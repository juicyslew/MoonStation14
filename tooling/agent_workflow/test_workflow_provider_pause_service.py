import json
import tempfile
import time
import unittest
from pathlib import Path

import workflow_coordinator
import workflow_native_execution
import workflow_run_control
import workflow_store
from test_workflow_native_execution import admit_native_fixture
from test_workflow_review_service import FakeOpenCode, TestIdentity


class OutageOpenCode(FakeOpenCode):
    def __init__(self, sessions):
        super().__init__(sessions)
        self.error_enabled=False
        self.get_history=0

    def __call__(self, server, method, path, body=None):
        if method=='GET' and '/message?' in path:
            self.get_history+=1
            if self.error_enabled:
                sid=path.split('/')[3]
                prompt=self.prompts[sid]
                payload=prompt['payload']
                assignment_text=payload['text']; metadata=payload['metadata']
                error={'id':'provider-error','type':'assistant','time':{'created':int(time.time()*1000)},
                    'error':{'type':'provider.auth','status':401,'message':'invalid API key'}}
                original={'id':prompt['id'],'type':'user','time':{'created':prompt['time']['created']},
                    'text':assignment_text,'metadata':metadata}
                return 200,{'data':[error,original], 'cursor':{}}
            return 200,{'data':[],'cursor':{}}
        if self.error_enabled and method=='POST' and path.endswith('/prompt'):
            raise AssertionError('unexpected inference prompt after outage')
        return super().__call__(server,method,path,body)


class ProviderPauseServiceTests(unittest.TestCase):
    def test_provider_pause_local_health_cancel_closes_without_verdict(self):
        from starlette.testclient import TestClient
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,accepted,check_id=admit_native_fixture(root,caller='provider-author',key='provider-pause',review_roles=('implementation',))
            native=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'native-lock')
            co=workflow_coordinator.ExplicitCoordinator(store,bindings={'author':workflow_coordinator.CoordinatorBinding('author',case.context)},
                artifact_dir=root/'artifacts',handlers={'TASK_READY':native},identity_verifier=TestIdentity(),
                owner_bindings={'owner':workflow_coordinator.OwnerBinding('owner',str(case.repo))},reconcile_interval=.03)
            role='implementation'; agent='workflow-reviewer-'+role
            reviewer={role:{'role':'reviewer','worktree':str(case.repo)}}
            token={'review-token':workflow_coordinator.ReviewerBinding('reviewer',str(case.repo),role)}
            api=OutageOpenCode({})
            workflow_coordinator.configure_review_pipeline(co,reviewer,{role:agent},token,transport_request=api)
            co.run_control=workflow_run_control.ServiceRunControl(store,co.notify_committed)
            try:
                with TestClient(co.app()) as client:
                    end=time.monotonic()+10
                    tasks=[]
                    while time.monotonic()<end:
                        tasks=[t for t in store.list_tasks(accepted['run_id'],1) if t['kind']=='REVIEW']
                        # DISPATCHING is persisted before the transport can POST;
                        # wait for the observable delivery rather than racing it.
                        if tasks and tasks[0]['state']=='DISPATCHING' and api.posts: break
                        time.sleep(.02)
                    self.assertTrue(tasks and tasks[0]['state']=='DISPATCHING' and api.posts,{'run':store.get_run(accepted['run_id']),'tasks':store.list_tasks(accepted['run_id'],1),
                        'outbox':store.outbox_summary(),'coordinator_error':co.last_error,'api_calls':api.calls,'api_posts':api.posts})
                    sid=tasks[0]['assigned_session_id']
                    self.assertIn(sid,api.sessions)
                    self.assertEqual('workflow-reviewer-implementation',api.sessions[sid]['agent'])
                    self.assertEqual(tasks[0]['task_id'],api.sessions[sid]['metadata']['ms14_review_task_id'])
                    api.error_enabled=True
                    end=time.monotonic()+5
                    while time.monotonic()<end and not store.task_is_dependency_paused(tasks[0]['task_id']): time.sleep(.02)
                    task=store.get_task(tasks[0]['task_id'])
                    self.assertTrue(store.task_is_dependency_paused(task['task_id']))
                    self.assertEqual('DISPATCHING',task['state'])
                    self.assertIsNone(store.get_run(accepted['run_id'])['attempt']['result'])
                    self.assertEqual('RELEASED',store.get_task_resources(check_id)[0]['state'])
                    n=api.get_history
                    self.assertEqual(200,client.get('/v1/health',headers={'Authorization':'Bearer owner'}).status_code)
                    self.assertEqual(n,api.get_history)
                    saved=store.get_run(accepted['run_id'])
                    command={'command_id':'cancel-outage','run_id':accepted['run_id'],'evaluation_generation':1,
                        'expected_sequence':saved['run']['sequence'],'reason':'CANCELLED'}
                    self.assertEqual(200,client.post('/v1/cancel',headers={'Authorization':'Bearer owner'},json=command).status_code)
                    end=time.monotonic()+5
                    while time.monotonic()<end and store.get_run(accepted['run_id'])['attempt']['phase']!='CLOSED': time.sleep(.02)
                    final=store.get_run(accepted['run_id'])
                    self.assertEqual('CLOSED',final['attempt']['phase'])
                    self.assertIsNone(final['attempt']['result'])
                    self.assertEqual('CANCELLED',store.get_task(task['task_id'])['state'])
                    self.assertEqual('RELEASED',store.get_task_resources(task['task_id'])[0]['state'])
            finally:
                co.stop()


if __name__=='__main__': unittest.main()
