"""Code-owned consumer for durable workflow task wakes and terminal finality."""
import time
import hashlib

try:
    import workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_store


class SafeDispatchError(RuntimeError):
    """Dispatch outcome was not proven; leave the durable wake unacknowledged."""


class ReviewPipeline:
    def __init__(self, store, planner, results, transport, native_backend, notify_committed=None):
        self.store, self.planner, self.results = store, planner, results
        self.transport, self.native_backend = transport, native_backend
        self.review_cancellation = None
        self.dependency_monitor = None
        self.notify_committed = notify_committed or (lambda event: None)

    def close(self):
        close=getattr(self.native_backend,'close',None)
        if callable(close): close()

    def observe(self, event_id, payload):
        task = self.store.get_task(payload.get('task_id')) if isinstance(payload, dict) else None
        if not task: return 'UNKNOWN'
        if task['kind'] == 'CHECK':
            return self.native_backend.observe(event_id, payload)
        return self.transport.observe(event_id, payload)

    def dispatch(self, event_id, payload):
        task = self.store.get_task(payload.get('task_id')) if isinstance(payload, dict) else None
        if not task: raise SafeDispatchError('wake does not identify a saved task')
        if task['kind'] == 'REVIEW' and task.get('state') == 'CANCELLED':
            if self.transport.observe(event_id, payload) == 'SETTLED': return 'SETTLED'
            raise SafeDispatchError('cancelled review readiness is not locally proven settled')
        backend = self.native_backend if task['kind'] == 'CHECK' else self.transport
        result = backend.dispatch(event_id, payload)
        if result == 'PENDING': return 'PENDING'
        if task['kind'] == 'CHECK' and result is None:
            result = backend.observe(event_id, payload)
        if result not in ('APPLIED','SETTLED'): raise SafeDispatchError('task dispatch is not proven applied')
        return result

    def completed(self, event, *, now_ms=None):
        """Reconcile only the exact saved task/result named by TASK_COMPLETED."""
        task = self.store.get_task(event.get('task_id'))
        persisted = self.store.get_event(event.get('event_id')) if isinstance(event, dict) else None
        if (not task or event.get('kind') != 'TASK_COMPLETED' or task['run_id'] != event.get('run_id') or
            task['evaluation_generation'] != event.get('evaluation_generation') or
            task['assignment_generation'] != event.get('assignment_generation') or
            task['input_digest'] != event.get('input_digest') or task['state'] != 'COMPLETED' or
            persisted is None or persisted['payload'] != event or
            task.get('result') is None or event.get('result_digest') != self.store.get_task_result_digest(event['task_id'])):
            return None
        return self._evaluate(task['run_id'], now_ms=now_ms)

    def reconcile_pending(self, *, now_ms=None):
        """Reconcile durable active attempt projections without creating new wakes."""
        if self.dependency_monitor is not None:
            for run_id in self.store.list_runs_by_phase(('REVIEWING',)):
                saved=self.store.get_run(run_id)
                if not saved: continue
                for task in self.store.list_tasks(run_id,saved['attempt']['generation']):
                    if task.get('kind')=='REVIEW' and task.get('state') in ('DISPATCHING','CLAIMED','RUNNING'):
                        self.dependency_monitor.poll(task['task_id'])
        for run_id in self.store.list_runs_by_phase(('CHECKING','REVIEWING','STOPPING')):
            saved=self.store.get_run(run_id)
            if saved and saved['attempt']['phase'] in ('REVIEWING','STOPPING'):
                self._evaluate(run_id, now_ms=now_ms)
        # ERROR is an intentionally terminal result even when cleanup could not
        # be proven at decision time. Retry only completed review-task cleanup;
        # never re-evaluate the immutable terminal result or touch active tasks.
        for run_id in self.store.list_runs_by_phase(('DECIDED','CLOSED')):
            saved=self.store.get_run(run_id)
            if not saved:
                continue
            attempt=saved['attempt']
            if not (attempt.get('phase')=='CLOSED' or
                    (attempt.get('phase')=='DECIDED' and attempt.get('result')=='ERROR')):
                continue
            for task in self.store.list_tasks(run_id):
                if task.get('kind')!='REVIEW' or task.get('state')!='COMPLETED':
                    continue
                resources=[r for r in self.store.get_task_resources(task['task_id'])
                           if r.get('resource_key','').startswith('review-session:') and
                           r.get('state') in ('HELD','UNKNOWN')]
                if resources and self.review_cancellation is not None:
                    self.review_cancellation.cleanup_completed(task['task_id'])

    def _evaluate(self, run_id, *, now_ms=None):
        now_ms = int(time.time()*1000) if now_ms is None else now_ms
        saved = self.store.get_run(run_id)
        attempt, run = saved['attempt'], saved['run']
        regression = run.get('run_kind') == 'POST_ACCEPTANCE_REGRESSION'
        diagnostic = run.get('run_kind') == 'DIAGNOSTIC'
        if attempt['phase'] == 'DECIDED': return {'decision':attempt['result'],'waiting_task_ids':[]}
        if attempt['phase'] == 'STOPPING':
            if attempt.get('proposed_closure') in ('CANCELLED', 'SUPERSEDED'):
                tasks = self.store.list_tasks(run_id, attempt['generation'])
                waiting = [t['task_id'] for t in tasks if t['state'] not in ('COMPLETED', 'CANCELLED')]
                held = [r for t in tasks for r in self.store.get_task_resources(t['task_id'])
                        if r['state'] in ('HELD', 'UNKNOWN')]
                if waiting or held:
                    return {'decision': None, 'waiting_task_ids': waiting,
                            'unresolved_resources': [r['resource_key'] for r in held]}
                terminal = self.store.transition_attempt(run_id, attempt['generation'],
                    expected_sequence=run['sequence'], phase='CLOSED', cleanup_state='CONFIRMED')
                self.notify_committed(terminal)
                return {'decision': None, 'closed': True, 'waiting_task_ids': []}
            proposal = attempt['proposed_result']
            aggregate = {'decision':proposal,'findings':[]}
        else:
            if regression or diagnostic:
                try:
                    import workflow_regression
                except ModuleNotFoundError:
                    from tooling.agent_workflow import workflow_regression
                aggregate = workflow_regression.aggregate_checks(self.store, run_id)
            else:
                self.planner.plan_reviews(run_id)
                aggregate = self.results.aggregate(run_id, now_ms=now_ms)
            if aggregate['decision'] is None: return aggregate
        proposal = aggregate['decision']
        incident = aggregate.get('incident')
        if proposal == 'ERROR' and incident is None:
            error = aggregate.get('error', {})
            task_id = error.get('task_id')
            incident = {'contract_version':1,
                'incident_id':'incident_'+hashlib.sha256(f"{run_id}:{attempt['generation']}:{task_id}:{error.get('code')}".encode()).hexdigest()[:24],
                'run_id':run_id,'evaluation_generation':attempt['generation'],'task_id':task_id,
                'code':error.get('code','WORKFLOW_ERROR'),'message':error.get('message','Required task failed'),
                'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,
                'artifact_refs':error.get('artifact_refs',[])}
        sequence = run['sequence']
        if attempt['phase'] != 'STOPPING':
            self.store.transition_attempt(run_id, attempt['generation'], expected_sequence=sequence,
                phase='STOPPING', proposed_result=proposal, incident=incident)
            saved = self.store.get_run(run_id); sequence = saved['run']['sequence']
        tasks = self.store.list_tasks(run_id, attempt['generation'])
        unresolved_review_resources=[]
        for task in tasks:
            if task.get('kind')!='REVIEW' or task.get('state')!='COMPLETED': continue
            resources=[r for r in self.store.get_task_resources(task['task_id'])
                       if r['resource_key'].startswith('review-session:')]
            if resources and any(r['state']!='RELEASED' for r in resources):
                cleanup=self.review_cancellation.cleanup_completed(task['task_id']) if self.review_cancellation else 'UNKNOWN'
                resources=[r for r in self.store.get_task_resources(task['task_id'])
                           if r['resource_key'].startswith('review-session:')]
                if cleanup!='APPLIED' or any(r['state']!='RELEASED' for r in resources):
                    unresolved_review_resources.append(task['task_id'])
        if unresolved_review_resources and proposal!='ERROR':
            return {'decision':None,'waiting_task_ids':unresolved_review_resources,
                    'unresolved_resources':unresolved_review_resources}
        unresolved = (any(t['state'] not in ('COMPLETED', 'CANCELLED') for t in tasks) or
                      any(r['state'] in ('HELD', 'UNKNOWN') for t in tasks
                          for r in self.store.get_task_resources(t['task_id'])))
        if unresolved and proposal != 'ERROR':
            return {'decision':None,'waiting_task_ids':[t['task_id'] for t in tasks if t['state'] not in ('COMPLETED','CANCELLED')]}
        terminal = self.store.transition_attempt(run_id, attempt['generation'], expected_sequence=sequence,
            phase='DECIDED', terminal_result=proposal, cleanup_state='UNRESOLVED' if unresolved else 'CONFIRMED', incident=incident)
        if diagnostic:
            saved_result = self.store.get_terminal_result(run_id, attempt['generation'])
            aggregate['next_action'] = saved_result.get('next_action') if saved_result else aggregate.get('next_action')
            aggregate['run_kind'] = 'DIAGNOSTIC'
        self.notify_committed(terminal)
        return aggregate
