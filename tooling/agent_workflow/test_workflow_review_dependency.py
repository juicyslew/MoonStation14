import tempfile
import unittest
import urllib.parse
from pathlib import Path

import workflow_review_dependency
import workflow_store
from test_workflow_review import ReviewPlanningTests


class StrictHistory:
    def __init__(self, response, inbox=None):
        self.response = response
        self.inbox=inbox
        self.calls = []

    def __call__(self, server, method, path, body=None):
        self.calls.append((server, method, path, body))
        inbox_path=urllib.parse.urlsplit(self.expected_path).path.rsplit('/',1)[0]+'/inbox'
        if method=='GET' and body is None and path==inbox_path:
            return 200,{'data':self.inbox or []}
        if method != 'GET' or body is not None or path != self.expected_path:
            raise AssertionError(f'unexpected API call: {method} {path}')
        return 200, self.response


class ReviewDependencyTests(unittest.TestCase):
    def prepared(self, root):
        helper = ReviewPlanningTests()
        store, run_id, check_id = helper.fixture(root, roles=('implementation',))
        helper.run_check(root, store, run_id, check_id)
        planner = helper.coordinator(store, run_id)
        planner.artifact_dir = Path(root) / 'review-artifacts'
        planner.artifact_dir.mkdir(parents=True, exist_ok=True)
        task = planner.plan_reviews(run_id)[0]
        task = store.get_task(task['task_id'])
        assignment = planner.build_assignment(task['task_id'])
        envelope = assignment['body']['envelope']
        server = 'http://127.0.0.1:9'
        import test_workflow_review_transport
        import workflow_review_transport
        transport = test_workflow_review_transport.FakeTransport()
        role = task['logical_key'].removeprefix('review:')
        backend = workflow_review_transport.ReviewDispatchBackend(
            planner, server=server, agents={role:'workflow-reviewer-'+role}, request=transport)
        self.assertEqual('APPLIED', backend.dispatch('fixture-review-dispatch', {'task_id':task['task_id']}))
        return store, planner, store.get_task(task['task_id']), server, envelope

    @staticmethod
    def history(path, messages):
        fake = StrictHistory({'data':messages, 'cursor':{}})
        fake.expected_path = path
        return fake

    @staticmethod
    def owned_prompt(store, planner, task, envelope):
        import hashlib
        assignment=planner.build_assignment(task['task_id'])
        mid='msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
        return {'id':mid,'type':'user','time':{'created':store.get_event(envelope['event_id'])['created_ms']},
            'text':workflow_store.canonical_json(assignment['body']),'metadata':{
                'review_event_id':envelope['event_id'],'task_id':task['task_id'],'body_sha256':assignment['body_sha256']}}

    def test_current_auth_error_pauses_once_and_persists_without_further_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            store, planner, task, server, envelope = self.prepared(root)
            dispatch_ms = store.get_event(envelope['event_id'])['created_ms']
            path = '/api/session/' + task['assigned_session_id'] + '/message?limit=50&order=desc'
            fake = self.history(path, [{'id':'message-auth-1','time':{'created':dispatch_ms+1},
                'error':{'type':'provider.auth','status':401,'message':'invalid API key'}}, self.owned_prompt(store,planner,task,envelope)])
            monitor = workflow_review_dependency.ReviewDependencyMonitor(planner, server=server, request=fake,
                now_ms=lambda:dispatch_ms+20)
            self.assertEqual('PAUSED', monitor.poll(task['task_id']))
            self.assertEqual(2, len(fake.calls))
            pause = store.get_task_dependency_pause(task['task_id'])
            self.assertEqual(server + ':provider.auth', pause['dependency_key'])
            self.assertEqual(dispatch_ms+20, pause['paused_since_ms'])
            self.assertIsNone(store.get_task(task['task_id'])['result'])
            db = store._connect()
            try:
                proof = db.execute("SELECT COUNT(*) FROM artifacts WHERE run_id=? AND json_extract(artifact_json,'$.artifact.producer_id')=? AND json_extract(artifact_json,'$.artifact.type')='PROVENANCE'",(task['run_id'],task['task_id'])).fetchone()[0]
            finally:
                db.close()
            self.assertGreaterEqual(proof, 1)
            reopened = workflow_store.WorkflowStore(store.path)
            monitor.store = reopened
            self.assertEqual('PAUSED', monitor.poll(task['task_id']))
            self.assertEqual(2, len(fake.calls))

    def test_pre_dispatch_error_and_wrong_resource_binding_are_ignored(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            store, planner, task, server, envelope = self.prepared(root)
            dispatch_ms = store.get_event(envelope['event_id'])['created_ms']
            path = '/api/session/' + task['assigned_session_id'] + '/message?limit=50&order=desc'
            owned=self.owned_prompt(store,planner,task,envelope)
            old = self.history(path, [owned, {'id':'old-error','time':{'created':dispatch_ms-1},
                'error':{'type':'provider.auth','status':401,'message':'old failure'}}])
            monitor = workflow_review_dependency.ReviewDependencyMonitor(planner, server=server, request=old)
            self.assertEqual('CLEAR', monitor.poll(task['task_id']))
            self.assertIsNone(store.get_task_dependency_pause(task['task_id']))
            resources = store.get_task_resources(task['task_id'])
            proof = resources[0]['identity_proof']
            proof['server'] = 'http://127.0.0.1:10'
            db = store._connect()
            try:
                db.execute('UPDATE resources SET identity_proof=? WHERE claim_id=?',
                    (workflow_store.canonical_json(proof), resources[0]['claim_id']))
                db.commit()
            finally:
                db.close()
            invalid = self.history(path, [owned, {'id':'current-error','time':{'created':dispatch_ms+1},
                'error':{'type':'provider.auth','status':401,'message':'invalid'}}])
            monitor.request = invalid
            self.assertEqual('UNKNOWN', monitor.poll(task['task_id']))
            self.assertEqual([], invalid.calls)
            self.assertIsNone(store.get_task_dependency_pause(task['task_id']))

    def test_compaction_error_pauses_from_exact_queued_original_in_inbox(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            store,planner,task,server,envelope=self.prepared(root)
            dispatch_ms=store.get_event(envelope['event_id'])['created_ms']
            path='/api/session/'+task['assigned_session_id']+'/message?limit=50&order=desc'
            prompt=self.owned_prompt(store,planner,task,envelope)
            error={'id':'compact-auth','type':'assistant','time':{'created':dispatch_ms+5},
                'error':{'type':'provider.auth','status':401,'message':'unauthorized'}}
            fake=self.history(path,[error]); fake.inbox=[prompt]
            monitor=workflow_review_dependency.ReviewDependencyMonitor(planner,server=server,request=fake,now_ms=lambda:dispatch_ms+10)
            self.assertEqual('PAUSED',monitor.poll(task['task_id']))
            self.assertEqual(3,len(fake.calls))
            self.assertTrue(store.task_is_dependency_paused(task['task_id']))

    def test_older_page_original_proves_prompt_and_current_error_pauses(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,planner,task,server,envelope=self.prepared(root)
            dispatch=store.get_event(envelope['event_id'])['created_ms']
            path='/api/session/'+task['assigned_session_id']+'/message?limit=50&order=desc'
            class Pages:
                calls=[]
                def __call__(self,server,method,path,body=None):
                    self.calls.append(path)
                    if len(self.calls)==1: return 200,{'data':[{'id':'err','time':{'created':dispatch+2},'error':{'type':'provider.auth','status':401,'message':'bad'}}],'cursor':{'next':'older'}}
                    return 200,{'data':[self_prompt],'cursor':{}}
            self_prompt=self.owned_prompt(store,planner,task,envelope); fake=Pages()
            monitor=workflow_review_dependency.ReviewDependencyMonitor(planner,server=server,request=fake,now_ms=lambda:dispatch+4)
            self.assertEqual('PAUSED',monitor.poll(task['task_id']))
            self.assertEqual(3,len(fake.calls)); self.assertNotIn('order=',fake.calls[1])

    def test_foreign_user_on_older_page_blocks_pause(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,planner,task,server,envelope=self.prepared(root)
            dispatch=store.get_event(envelope['event_id'])['created_ms']
            class Pages:
                calls=[]
                def __call__(self,server,method,path,body=None):
                    self.calls.append(path)
                    if len(self.calls)==1: return 200,{'data':[{'id':'err','time':{'created':dispatch+2},'error':{'type':'provider.auth','status':401,'message':'bad'}}],'cursor':{'next':'older'}}
                    return 200,{'data':[{'id':'foreign','type':'user','time':{'created':dispatch+1}}],'cursor':{}}
            fake=Pages(); monitor=workflow_review_dependency.ReviewDependencyMonitor(planner,server=server,request=fake)
            self.assertEqual('UNKNOWN',monitor.poll(task['task_id']))
            self.assertIsNone(store.get_task_dependency_pause(task['task_id']))

    def test_malformed_paging_is_unknown_without_pause(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store,planner,task,server,envelope=self.prepared(root)
            fake=self.history('/api/session/'+task['assigned_session_id']+'/message?limit=50&order=desc',[])
            fake.response={'data':[],'cursor':None}
            monitor=workflow_review_dependency.ReviewDependencyMonitor(planner,server=server,request=fake)
            self.assertEqual('UNKNOWN',monitor.poll(task['task_id']))
            self.assertIsNone(store.get_task_dependency_pause(task['task_id']))


if __name__ == '__main__':
    unittest.main()
