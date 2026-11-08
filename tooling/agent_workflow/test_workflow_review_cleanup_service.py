"""Coordinator-thread lifecycle coverage for completed review cleanup."""
import json
import tempfile
import time
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_native_execution
import workflow_run_control
from test_workflow_native_execution import admit_native_fixture


class ReviewCleanupAPI:
    """Strict in-memory model of the prompt inbox and active-session API."""
    def __init__(self):
        self.session_id = None
        self.sessions = {}
        self.agent = None
        self.worktree = None
        self.inbox = []
        self.prompt_item = None
        self.history = []
        self.active = False
        self.interrupted = False
        self.posts = []
        self.calls = []
        self.fail_active_once = False
        self.active_override = None

    def __call__(self, server, method, path, body=None):
        self.calls.append((method,path))
        if method == 'GET' and path.startswith('/api/session/') and path != '/api/session/active' and path.count('/') == 3:
            sid=path.rsplit('/',1)[-1]
            if sid not in self.sessions: return 404, {'error':'not found'}
            return 200, {'data': self.sessions[sid]}
        if method == 'POST' and path == '/api/session':
            self.session_id=body['id']; self.agent=body['agent']
            self.worktree=body['location']['directory']
            self.sessions[self.session_id]={'id':self.session_id,'agent':self.agent,
                'directory':self.worktree,'metadata':body['metadata']}
            return 200, {'data': self.sessions[self.session_id]}
        if method == 'POST' and path == f'/api/session/{self.session_id}/prompt':
            self.posts.append(body)
            item = {'id': body['id'],'sessionID':self.session_id,'delivery':body['delivery'],
                    'type':'user','payload':{'text':body['text'],'metadata':body['metadata']},
                    'time':{'created':int(time.time()*1000)}}
            self.prompt_item = item
            self.active = True
            self.inbox.append(item)
            self.history.append(item)
            return 200, {'data': {'id': body['id'], 'sessionID': self.session_id,
                                  'type': 'user', 'delivery': body['delivery'],
                                  'payload': {'text': body['text'], 'metadata': body['metadata']}}}
        prefix = f'/api/session/{self.session_id}/inbox'
        if method == 'DELETE' and path.startswith(prefix + '/'):
            message_id = path[len(prefix) + 1:]
            self.inbox = [item for item in self.inbox if item['id'] != message_id]
            return 204, {}
        if method == 'POST' and path == f'/api/session/{self.session_id}/interrupt?resume=false':
            if self.prompt_item:
                # Model OpenCode requeueing the acknowledged prompt on interrupt.
                self.inbox.append(self.prompt_item)
                self.interrupted = True
            self.active = False
            return 200, {'interrupted': True}
        if method == 'GET' and path == prefix:
            return 200, {'data': list(self.inbox)}
        if method == 'GET' and path == '/api/session/active':
            if self.fail_active_once:
                self.fail_active_once=False
                return 503,{}
            if self.active_override is not None:
                return 200,{'data':self.active_override}
            data = {self.session_id: {'type': 'running'}} if self.prompt_item is not None and self.active else {}
            return 200, {'data': data}
        if method=='GET' and path.endswith('/message?limit=50&order=desc'):
            records=[{k:v for k,v in item.items() if k!='payload'} |
                     {'text':item['payload']['text'],'metadata':item['payload']['metadata']}
                     for item in self.history]
            return 200,{'data':sorted(records,key=lambda item:item.get('time',{}).get('created',0),reverse=True),'cursor':{}}
        raise AssertionError(f'unexpected fake API request: {method} {path}')


def wait_for(predicate, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(.02)
    raise AssertionError('bounded coordinator wait expired')


class ReviewCleanupServiceTests(unittest.TestCase):
    def test_cancel_verified_session_before_prompt_emission(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            from test_workflow_review import ReviewPlanningTests
            helper=ReviewPlanningTests()
            store,run_id,check_id=helper.fixture(root,roles=('implementation',))
            helper.run_check(root,store,run_id,check_id)
            planner=helper.coordinator(store,run_id)
            planner.artifact_dir=root/'review-artifacts'; planner.artifact_dir.mkdir(parents=True,exist_ok=True)
            task=planner.plan_reviews(run_id)[0]
            api=ReviewCleanupAPI(); api.fail_active_once=True
            import workflow_review_transport, workflow_review_cancellation
            server='http://127.0.0.1:4096'
            transport=workflow_review_transport.ReviewDispatchBackend(planner,server=server,
                agents={'implementation':'workflow-reviewer-implementation'},request=api)
            assignment=planner.build_assignment(task['task_id'])
            self.assertEqual('UNKNOWN',transport.dispatch(assignment['body']['envelope']['event_id'],
                {'task_id':task['task_id']}))
            task=store.get_task(task['task_id'])
            sid=task['assigned_session_id']
            self.assertEqual('DISPATCHING',task['state'])
            self.assertIn(sid,api.sessions)
            self.assertEqual([],api.posts)
            self.assertIsNone(transport.get_session_baseline(task,assignment))
            self.assertIsNotNone(transport._creation_artifact(task))
            self.assertIsNotNone(transport._creation_artifact(task,verified=True))

            run=store.get_run(run_id)
            store.transition_attempt(run_id,task['evaluation_generation'],expected_sequence=run['run']['sequence'],
                phase='STOPPING',closure='CANCELLED',caller='fixture-owner',command_id='cancel-before-prompt')
            cancel=store.find_task_cancel_event(task['task_id'])
            cancellation=workflow_review_cancellation.ReviewCancellation(store,planner,transport,
                server=server,request=api,sleep=lambda _:None)
            before_calls=len(api.calls)
            api.history.append({'id':'foreign-user','type':'user','time':{'created':1},'text':'foreign'})
            self.assertEqual('UNKNOWN',cancellation.cancel(cancel['event_id'],cancel['payload']))
            api.history.clear()
            api.active_override={sid:None}
            self.assertEqual('UNKNOWN',cancellation.cancel(cancel['event_id'],cancel['payload']))
            api.active_override=None
            original_metadata=dict(api.sessions[sid]['metadata'])
            api.sessions[sid]['metadata']['ms14_review_task_id']='foreign-task'
            self.assertEqual('UNKNOWN',cancellation.cancel(cancel['event_id'],cancel['payload']))
            api.sessions[sid]['metadata']=original_metadata
            verified=store.get_artifact(transport._creation_id(task)+'_verified')['artifact']
            verified_path=Path(verified['path']); verified_bytes=verified_path.read_bytes()
            try:
                verified_path.write_bytes(verified_bytes+b' ')
                self.assertEqual('UNKNOWN',cancellation.cancel(cancel['event_id'],cancel['payload']))
            finally:
                verified_path.write_bytes(verified_bytes)
            for aid in (transport._baseline_artifact_id(task),transport._receipt_artifact_id(task)):
                store.register_artifact({'contract_version':1,'artifact_id':aid,'run_id':task['run_id'],
                    'producer_task_id':task['task_id'],'type':'PROVENANCE','path':str(root/('missing-'+aid)),
                    'sha256':'0'*64,'size_bytes':1,'immutable':True,'producer_kind':'TASK','producer_id':task['task_id']},
                    input_digest=task['input_digest'])
                self.assertEqual('UNKNOWN',cancellation.cancel(cancel['event_id'],cancel['payload']))
                self.assertEqual('CANCEL_REQUESTED',store.get_task(task['task_id'])['state'])
                self.assertEqual('UNKNOWN',store.get_task_resources(task['task_id'])[0]['state'])
                db=store._connect()
                try: db.execute('DELETE FROM artifacts WHERE id=?',(aid,)); db.commit()
                finally: db.close()
            self.assertEqual('APPLIED',cancellation.cancel(cancel['event_id'],cancel['payload']))
            self.assertEqual('CANCELLED',store.get_task(task['task_id'])['state'])
            resource=store.get_task_resources(task['task_id'])[0]
            self.assertEqual('RELEASED',resource['state'])
            saved=json.loads(Path(store.get_artifact(resource['cleanup_proof'])['artifact']['path']).read_text())
            self.assertEqual('REVIEW_UNPROMPTED_SESSION_CLEANUP',saved['purpose'])
            self.assertTrue(saved['inactive_confirmed'] and saved['inbox_empty'] and saved['history_empty'])
            calls_after=len(api.calls)
            self.assertFalse(any(method in ('POST','DELETE') for method,path in api.calls[before_calls:]
                if path.endswith('/prompt') or '/inbox/' in path or path.endswith('/interrupt?resume=false')))
            self.assertEqual([],api.posts)
            self.assertEqual('APPLIED',cancellation.cancel(cancel['event_id'],cancel['payload']))
            self.assertEqual(calls_after,len(api.calls))
            replay_aid=transport._receipt_artifact_id(task)
            store.register_artifact({'contract_version':1,'artifact_id':replay_aid,'run_id':task['run_id'],
                'producer_task_id':task['task_id'],'type':'PROVENANCE','path':str(root/'missing-replay-receipt'),
                'sha256':'0'*64,'size_bytes':1,'immutable':True,'producer_kind':'TASK','producer_id':task['task_id']},
                input_digest=task['input_digest'])
            calls_after_corruption=len(api.calls)
            self.assertEqual('UNKNOWN',cancellation.cancel(cancel['event_id'],cancel['payload']))
            self.assertEqual(calls_after_corruption,len(api.calls))

    def test_coordinator_acknowledges_cancelled_task_and_attempt_closed_wakes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); role='implementation'; agent='workflow-reviewer-'+role
            case,store,accepted,check_id=admit_native_fixture(root,caller='cancel-author',
                key='cancel-review-case',review_roles=(role,),delay=.05)
            native=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'native-lock')
            author=workflow_coordinator.CoordinatorBinding('cancel-author',case.context)
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'author-token':author},
                artifact_dir=root/'artifacts',handlers={'TASK_READY':native},identity_verifier=lambda _:True,
                owner_bindings={'owner-token':workflow_coordinator.OwnerBinding('owner',str(case.repo))},
                reconcile_interval=.03)
            coordinator.identity_verifier.server='http://127.0.0.1:4096'
            review_bindings={role:{'role':'reviewer','worktree':str(case.repo)}}
            tokens={'review-token':workflow_coordinator.ReviewerBinding('reviewer',str(case.repo),role)}
            api=ReviewCleanupAPI()
            workflow_coordinator.configure_review_pipeline(coordinator,review_bindings,{role:agent},tokens,transport_request=api)
            coordinator.run_control=workflow_run_control.ServiceRunControl(store,coordinator.notify_committed)
            from starlette.testclient import TestClient
            try:
                with TestClient(coordinator.app()) as client:
                    wait_for(lambda:bool(api.posts) or coordinator.last_error)
                    self.assertTrue(api.posts,coordinator.last_error)
                    review=next(t for t in store.list_tasks(accepted['run_id'],1) if t['kind']=='REVIEW')
                    self.assertIn(review['assigned_session_id'],api.sessions)
                    self.assertEqual('workflow-reviewer-implementation',api.sessions[review['assigned_session_id']]['agent'])
                    self.assertEqual(review['task_id'],api.sessions[review['assigned_session_id']]['metadata']['ms14_review_task_id'])
                    wait_for(lambda:store.get_task(check_id)['state']=='COMPLETED')
                    saved=store.get_run(accepted['run_id'])
                    response=client.post('/v1/cancel',headers={'Authorization':'Bearer owner-token'},json={
                        'command_id':'cancel-review-run','run_id':accepted['run_id'],
                        'evaluation_generation':saved['attempt']['generation'],
                        'expected_sequence':saved['run']['sequence'],'reason':'CANCELLED'})
                    self.assertEqual(200,response.status_code,response.text)
                    wait_for(lambda:store.get_run(accepted['run_id'])['attempt']['phase']=='CLOSED')
                    self.assertEqual('CANCELLED',store.get_task(review['task_id'])['state'])
                    self.assertEqual('RELEASED',store.get_task_resources(review['task_id'])[0]['state'])
                    def wakes_acknowledged():
                        db=store._connect()
                        try:
                            states=db.execute("SELECT e.kind,o.state FROM events e JOIN outbox o ON o.event_id=e.id WHERE e.run_id=? AND e.kind IN ('TASK_CANCELLED','ATTEMPT_CLOSED')",(accepted['run_id'],)).fetchall()
                            return len(states)==2 and all(row['state']=='ACKNOWLEDGED' for row in states)
                        finally: db.close()
                    wait_for(wakes_acknowledged)
                    db=store._connect()
                    try:
                        wakes=db.execute("SELECT e.kind,o.state FROM events e JOIN outbox o ON o.event_id=e.id WHERE e.run_id=? AND e.kind IN ('TASK_CANCELLED','ATTEMPT_CLOSED')",(accepted['run_id'],)).fetchall()
                    finally: db.close()
                    self.assertEqual([('ATTEMPT_CLOSED','ACKNOWLEDGED'),('TASK_CANCELLED','ACKNOWLEDGED')],
                                     sorted((row[0],row[1]) for row in wakes))
                    self.assertEqual('1',(case.repo/'counter.txt').read_text())
            finally: coordinator.stop()

    def test_real_coordinator_cleans_completed_review_before_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            role, agent = 'implementation', 'workflow-reviewer-implementation'
            case, store, accepted, check_id = admit_native_fixture(
                root, caller='cleanup-author', key='cleanup-case', review_roles=(role,), delay=.05)
            native = workflow_native_execution.NativeExecutionBackend(
                store, root/'artifacts', root/'native-lock')
            author = workflow_coordinator.CoordinatorBinding('cleanup-author', case.context)
            coordinator = workflow_coordinator.ExplicitCoordinator(
                store, bindings={'author-token': author}, artifact_dir=root/'artifacts',
                handlers={'TASK_READY': native}, identity_verifier=lambda _: True,
                reconcile_interval=.03)
            coordinator.identity_verifier.server = 'http://127.0.0.1:4096'
            review_bindings = {role: {'role': 'reviewer',
                                      'worktree': str(case.repo)}}
            reviewer_tokens = {'review-token': workflow_coordinator.ReviewerBinding(
                'review-caller', str(case.repo), role)}
            api = ReviewCleanupAPI()
            workflow_coordinator.configure_review_pipeline(
                coordinator, review_bindings, {role: agent}, reviewer_tokens,
                transport_request=api)
            from starlette.testclient import TestClient
            try:
                with TestClient(coordinator.app()) as client:
                    wait_for(lambda: bool(api.posts) or coordinator.last_error)
                    self.assertTrue(api.posts,coordinator.last_error)
                    wait_for(lambda: store.get_task(check_id)['state'] == 'COMPLETED')
                    review = next(t for t in store.list_tasks(accepted['run_id'], 1)
                                  if t['kind'] == 'REVIEW')
                    session_id=review['assigned_session_id']
                    identity = {k: review[k] for k in ('task_id', 'run_id',
                        'evaluation_generation', 'assignment_generation', 'input_digest')}
                    headers = {'Authorization': 'Bearer review-token',
                               'X-MS14-Reviewer-Session': session_id}
                    claim = client.post('/v1/review/claim', headers=headers,
                        json={'contract_version': 1, 'command_id': 'claim-cleanup',
                              **identity})
                    self.assertTrue(claim.json()['accepted'], claim.json())
                    check_refs = [ref for ref in store.get_task(check_id)['result']['artifact_refs']
                                  if store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT']
                    result = client.post('/v1/review/complete', headers=headers, json={
                        'contract_version': 1, **identity, 'decision': 'PASS',
                        'findings': [{'criterion_id': 'criterion-1', 'category': 'IMPLEMENTATION',
                                      'message': 'Reviewed the native check receipt',
                                      'location': 'source tree', 'evidence_refs': check_refs}],
                        'inspected_refs': check_refs})
                    self.assertTrue(result.json()['accepted'], result.json())

                    wait_for(lambda: store.get_run(accepted['run_id'])['attempt']['phase'] == 'DECIDED')
                    self.assertEqual('PASS', store.get_run(accepted['run_id'])['attempt']['result'])
                    self.assertEqual('COMPLETED', store.get_task(review['task_id'])['state'])
                    resource = next(r for r in store.get_task_resources(review['task_id'])
                                    if r['resource_key'].startswith('review-session:'))
                    self.assertEqual('RELEASED', resource['state'])
                    self.assertTrue(api.interrupted)
                    self.assertEqual([], api.inbox)
                    self.assertEqual('1', (case.repo/'counter.txt').read_text())
            finally:
                coordinator.stop()


if __name__ == '__main__':
    unittest.main()
