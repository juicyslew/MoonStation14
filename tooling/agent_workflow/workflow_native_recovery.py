"""Explicit restart reconciliation for saved native process scopes.

Call only after the coordinator's exclusive ownership guard has been acquired.
"""
import hashlib

try:
    import workflow_native_cancellation, workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_native_cancellation, workflow_store


class NativeRecovery:
    def __init__(self, store, artifact_dir):
        self.store=store
        self.cancellation=workflow_native_cancellation.NativeCancellation(store, artifact_dir)
        self._startup_unknown=set()

    def reconcile(self, unresolved_only=False):
        summary={'cleaned':[],'errors':[],'unknown':[],'cleanup_artifact_refs':[]}
        saved_rows=self.store.list_native_scopes()
        if unresolved_only:
            present={row['task_id'] for row in saved_rows}
            for taskid in sorted(self._startup_unknown-present):
                scope=self.store.get_native_scope(taskid); task=self.store.get_task(taskid)
                if scope is not None and task is not None:
                    saved_rows.append(dict(scope,task=task))
        for saved in saved_rows:
            task=saved['task']
            resources=self.store.get_task_resources(saved['task_id'])
            unresolved_resources=any(row['state'] in ('HELD','UNKNOWN') for row in resources)
            unresolved_scope=saved.get('state')!='CLEANED'
            settled_or_cancelled=task['state'] in ('COMPLETED','CANCELLED','CANCEL_REQUESTED')
            if unresolved_only and not (
                    saved['task_id'] in self._startup_unknown or
                    (settled_or_cancelled and (unresolved_scope or unresolved_resources))):
                continue
            try:
                ref=self.cancellation.cleanup_scope(saved['task_id'])
                summary['cleaned'].append(saved['task_id'])
                summary['cleanup_artifact_refs'].append(ref)
                self._startup_unknown.discard(saved['task_id'])
                incident_ids=('incident_native_recovery_'+saved['task_id'],
                    'incident_scope_'+hashlib.sha256((saved['task_id']+'NATIVE_SCOPE_CLEANUP_UNKNOWN').encode()).hexdigest()[:20])
                for incident_id in incident_ids:
                    incident=self.store.get_incident(incident_id)
                    if incident and incident.get('resolved_ms') is None:
                        self.store.resolve_incident(incident_id,proof_ref=ref)
                if task['state']=='CANCEL_REQUESTED':
                    rows=self.store.find_task_cancel_event(saved['task_id'])
                    if rows is None: raise workflow_store.StaleCommand('exact saved cancellation event is missing')
                    self.cancellation.dispatch(rows['event_id'],rows['payload'])
                elif task['state'] in ('DISPATCHING','CLAIMED','RUNNING'):
                    report={'contract_version':1,'task_id':task['task_id'],'run_id':task['run_id'],
                        'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
                        'input_digest':task['input_digest'],'error_code':'NATIVE_WORKER_INTERRUPTED',
                        'message':'Native worker was interrupted; its owned process scope was confirmed cleaned during restart reconciliation.',
                        'artifact_refs':[ref]}
                    self.store.report_native_interruption(report,cleanup_artifact_ref=ref)
                    summary['errors'].append(saved['task_id'])
            except Exception as exc:
                summary['unknown'].append(saved['task_id'])
                self._startup_unknown.add(saved['task_id'])
                incident={'contract_version':1,'incident_id':'incident_native_recovery_'+saved['task_id'],
                    'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],'task_id':saved['task_id'],
                    'code':'NATIVE_SCOPE_RECOVERY_UNKNOWN','message':f'Native scope recovery for {saved["scope_name"]} remains unresolved: {type(exc).__name__}: {exc}',
                    'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,
                    'artifact_refs':[saved['creation_artifact_ref']]}
                if self.store.get_incident(incident['incident_id']) is None:
                    self.store.record_incident(incident)
        return summary
