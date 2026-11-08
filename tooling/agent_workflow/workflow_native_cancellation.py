"""Exact saved-event cancellation for owned native process scopes."""
import hashlib
import json
from pathlib import Path

try:
    import workflow_process_scope, workflow_task_artifacts, workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_process_scope, workflow_task_artifacts, workflow_store


def cancelled_native_readiness_settled(task, event, cancellation, resources, native_claim, native_scope):
    """Validate DB proof that a cancelled native readiness wake never started."""
    if not isinstance(task, dict) or task.get('kind') != 'CHECK' or task.get('state') != 'CANCELLED': return False
    payload = event.get('payload') if isinstance(event, dict) else None
    event_id = event.get('event_id', event.get('id')) if isinstance(event, dict) else None
    if (not isinstance(payload, dict) or event.get('kind') != 'TASK_READY' or
        payload.get('kind') != 'TASK_READY' or payload.get('event_id') != event_id or
        event.get('run_id') != task.get('run_id') or event.get('generation') != task.get('evaluation_generation') or
        any(payload.get(k) != task.get(v) for k,v in [('task_id','task_id'),('run_id','run_id'),
            ('evaluation_generation','evaluation_generation'),('assignment_generation','assignment_generation'),('input_digest','input_digest')]) or
        not isinstance(event.get('payload_digest'), str) or workflow_store.digest(payload) != event.get('payload_digest')): return False
    proof = cancellation.get('proof') if isinstance(cancellation, dict) else None
    expected = {'task_id':task.get('task_id'), 'run_id':task.get('run_id'),
        'generation':task.get('evaluation_generation'), 'assignment_generation':task.get('assignment_generation'),
        'input_digest':task.get('input_digest'), 'kind':'DB_NEVER_STARTED'}
    if (not isinstance(cancellation, dict) or type(cancellation.get('confirmed')) is not int or
        cancellation.get('confirmed') != 1 or not isinstance(proof, dict) or
        any(proof.get(k) != v for k,v in expected.items())): return False
    return (isinstance(resources, list) and not resources and native_claim is None and native_scope is None)


class NativeCancellation:
    def __init__(self, store, artifact_dir):
        self.store=store
        self.artifact_dir=Path(artifact_dir).resolve()

    def observe(self, eventid, payload):
        saved=self.store.get_event(eventid)
        if saved is None or saved['kind']!='CANCEL_TASK' or saved['payload']!=payload:
            return 'UNKNOWN'
        task=self.store.get_task(payload.get('task_id'))
        if task and task['state']=='CANCELLED': return 'APPLIED'
        scope=self.store.get_native_scope(payload.get('task_id')) if task else None
        if task and task['state']=='CANCEL_REQUESTED' and scope and scope['state'] in ('OWNED','CLEANED'): return 'ABSENT'
        return 'UNKNOWN'

    def cleanup_scope(self, taskid):
        task=self.store.get_task(taskid); scope=self.store.get_native_scope(taskid)
        if task is None or scope is None: raise workflow_store.StaleCommand('native scope ownership is unknown')
        if scope['state']=='CLEANED':
            ref=scope['cleanup_artifact_ref']; self._verify_artifact(task,scope,ref)
        else:
            self._verify_creation(task,scope)
            owned=workflow_process_scope.OwnedProcessScope.open_existing(scope['scope_name'])
            if owned is None:
                try: import workflow_native_worker
                except ModuleNotFoundError: from tooling.agent_workflow import workflow_native_worker
                if workflow_native_worker.native_identity_state(scope['worker_identity'])!='dead':
                    raise workflow_store.StaleCommand('saved native job is absent but root identity is not proven dead')
                method='CONFIRMED_KILL_ON_CLOSE_JOB_ABSENT'
                snapshot={'name':scope['scope_name'],'active_processes':0,'observed_open_error':2,
                          'root_identity':scope['worker_identity'],'creation_artifact_ref':scope['creation_artifact_ref']}
            else:
                try:
                    result=owned.terminate_and_wait(5)
                    if result['status']!='CONFIRMED' or result.get('active_processes')!=0:
                        raise workflow_store.StaleCommand('native job cleanup unresolved: '+str(result))
                    method='TERMINATE_JOB_AND_WAIT'; snapshot=result
                finally: owned.close()
            cleanup={'kind':'NATIVE_SCOPE_CLEANUP','scope_name':scope['scope_name'],'task_id':taskid,'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],'creation_artifact_ref':scope['creation_artifact_ref'],'worker_identity':scope['worker_identity'],'method':method,'active_processes':0,'snapshot':snapshot}
            ref,_=workflow_task_artifacts.register_bytes(self.store,task,self.artifact_dir,'PROVENANCE',workflow_store.canonical_json(cleanup).encode())
            try:
                self.store.record_native_scope_cleanup(task_id=taskid,assignment_generation=task['assignment_generation'],artifact_ref=ref)
            except workflow_store.IdempotencyConflict:
                # Another cleanup observer may have committed the immutable proof
                # after our initial OWNED read. Reuse it only after reloading the
                # exact assignment and validating its original proof bytes.
                current=self.store.get_native_scope(taskid)
                current_task=self.store.get_task(taskid)
                if (current is None or current_task is None or current['state']!='CLEANED' or
                        current['assignment_generation']!=task['assignment_generation'] or
                        current_task['assignment_generation']!=task['assignment_generation']):
                    raise
                ref=current['cleanup_artifact_ref']
                self._verify_artifact(current_task,current,ref)
        for resource in self.store.get_task_resources(taskid):
            if resource['state'] not in ('HELD','UNKNOWN'): continue
            data=workflow_store.canonical_json({'claim_id':resource['claim_id'],'identity_proof':resource['identity_proof'],'native_scope_cleanup_artifact_ref':ref,'method':'OWNED_NATIVE_SCOPE_ZERO_ACTIVE'}).encode()
            proof,_=workflow_task_artifacts.register_bytes(self.store,task,self.artifact_dir,'PROVENANCE',data)
            self.store.release_resource(resource['resource_key'],claim_id=resource['claim_id'],run_id=resource['run_id'],generation=resource['generation'],task_id=taskid,assignment_generation=resource['assignment_generation'],identity_proof=resource['identity_proof'],cleanup_proof=proof)
        return ref

    def _verify_creation(self,task,scope):
        binding=self.store.get_artifact(scope['creation_artifact_ref'])
        if not binding: raise workflow_store.StaleCommand('native scope creation proof missing')
        a=binding['artifact']; raw=Path(a['path']).read_bytes(); proof=workflow_store.strict_loads(raw.decode())
        expected={'scope_name':scope['scope_name'],'task_id':task['task_id'],'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],'worker_identity':scope['worker_identity']}
        if (len(raw)!=a['size_bytes'] or hashlib.sha256(raw).hexdigest()!=a['sha256'] or
                any(proof.get(k)!=v for k,v in expected.items()) or proof.get('kill_on_close') is not True or
                proof.get('non_inherited_handle') is not True):
            raise workflow_store.ProtocolError('native scope creation proof integrity/binding mismatch')
        return scope['creation_artifact_ref']

    def _verify_artifact(self,task,scope,ref):
        binding=self.store.get_artifact(ref)
        if not binding or binding['artifact'].get('type')!='PROVENANCE' or binding['artifact'].get('producer_task_id')!=task['task_id']: raise workflow_store.ProtocolError('saved native cleanup proof missing or unowned')
        art=binding['artifact']; raw=Path(art['path']).read_bytes(); proof=workflow_store.strict_loads(raw.decode())
        expected={'task_id':task['task_id'],'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],'creation_artifact_ref':scope['creation_artifact_ref'],'worker_identity':scope['worker_identity'],'scope_name':scope['scope_name']}
        if (len(raw)!=art['size_bytes'] or hashlib.sha256(raw).hexdigest()!=art['sha256'] or proof.get('active_processes')!=0 or
                any(proof.get(k)!=v for k,v in expected.items()) or proof.get('method') not in ('TERMINATE_JOB_AND_WAIT','CONFIRMED_KILL_ON_CLOSE_JOB_ABSENT')): raise workflow_store.ProtocolError('saved native cleanup proof integrity mismatch')

    def dispatch(self, eventid, payload):
        saved=self.store.get_event(eventid)
        if saved is None or saved['kind']!='CANCEL_TASK' or saved['payload']!=payload:
            raise workflow_store.StaleCommand('cancellation event is not the exact saved event')
        taskid=payload.get('task_id'); task=self.store.get_task(taskid)
        if task is None or task['state']!='CANCEL_REQUESTED':
            if task and task['state']=='CANCELLED': return self.observe(eventid,payload)
            raise workflow_store.StaleCommand('task has no pending cancellation')
        if any(payload.get(key)!=task.get(field) for key,field in [('run_id','run_id'),('evaluation_generation','evaluation_generation'),('assignment_generation','assignment_generation'),('input_digest','input_digest')]):
            raise workflow_store.StaleCommand('cancellation event task binding mismatch')
        scope=self.store.get_native_scope(taskid)
        if scope is None: raise workflow_store.StaleCommand('native process scope is unknown; cancellation held')
        ref=self.cleanup_scope(taskid)
        self.store.confirm_task_cancelled(task_id=taskid,run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],cleanup_artifact_ref=ref)
        return 'APPLIED'
