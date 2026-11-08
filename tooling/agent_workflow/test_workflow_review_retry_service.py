import tempfile
import time
import unittest
import json
import uuid
from pathlib import Path

import workflow_coordinator
import workflow_native_execution
from test_workflow_native_execution import admit_native_fixture
from test_workflow_review_service import FakeOpenCode, TestIdentity


class RetryAPI(FakeOpenCode):
    def __init__(self,sessions):
        super().__init__(sessions); self.fail=False; self.patch_count=0
    def __call__(self,server,method,path,body=None):
        if method=='GET' and '/message?' in path and self.fail:
            sid=path.split('/')[3]; prompt=self.prompts[sid]
            error={'id':'401','type':'assistant','time':{'created':int(time.time()*1000)},'error':{'type':'provider.auth','status':401,'message':'credential rejected'}}
            payload=prompt.get('payload',{})
            promptmsg={'id':prompt['id'],'type':'user','sessionID':sid,'delivery':'queue','time':{'created':int(time.time()*1000)-1},'payload':{'text':payload.get('text'),'metadata':payload.get('metadata')}}
            return 200,{'data':[error,promptmsg],'cursor':{}}
        if method=='GET' and path.endswith('/inbox'):
            sid=path.split('/')[-2]; prompt=self.prompts[sid]
            if prompt is None: return 200,{'data':[]}
            return 200,{'data':[prompt]}
        if method=='PATCH' and '/inbox/' in path:
            self.patch_count+=1; sid=path.split('/')[-3]; self.prompts[sid]['delivery']='steer'; self.active[sid]=True
            return 204,None
        if method=='DELETE' and '/inbox/' in path:
            sid=path.split('/')[-3]; self.prompts[sid]=None
            return 204,{}
        return super().__call__(server,method,path,body)


class ReviewRetryServiceTests(unittest.TestCase):
    def test_owner_retry_unpauses_same_task_then_sdk_pass_decides_attempt(self):
        from starlette.testclient import TestClient
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,accepted,check=admit_native_fixture(root,caller='retry-author',key='retry-case',review_roles=('implementation',))
            native=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'native-lock')
            co=workflow_coordinator.ExplicitCoordinator(store,bindings={'author':workflow_coordinator.CoordinatorBinding('author',case.context)},
                artifact_dir=root/'artifacts',handlers={'TASK_READY':native},identity_verifier=TestIdentity(),
                owner_bindings={'owner':workflow_coordinator.OwnerBinding('owner',str(case.repo)),
                                'other-owner':workflow_coordinator.OwnerBinding('other-owner',str(root/'other'))},reconcile_interval=.03)
            role='implementation'; reviewers={role:{'role':'reviewer','worktree':str(case.repo)}}
            api=RetryAPI({})
            workflow_coordinator.configure_review_pipeline(co,reviewers,{role:'workflow-reviewer-'+role},
                {'review-token':workflow_coordinator.ReviewerBinding('reviewer',str(case.repo),role)},transport_request=api)
            try:
                with TestClient(co.app()) as client:
                    end=time.monotonic()+8
                    while time.monotonic()<end:
                        reviews=[t for t in store.list_tasks(accepted['run_id'],1) if t['kind']=='REVIEW']
                        if reviews and api.posts: break
                        time.sleep(.02)
                    task=reviews[0]; sid=task['assigned_session_id']; api.fail=True; api.active[sid]=False
                    end=time.monotonic()+4
                    while time.monotonic()<end and not store.task_is_dependency_paused(task['task_id']): time.sleep(.02)
                    self.assertTrue(store.task_is_dependency_paused(task['task_id']), {'task':store.get_task(task['task_id']),'calls':api.calls,'error':co.last_error})
                    api.fail=False
                    command={'task_id':task['task_id'],'command_id':'owner-retry-1','expected_sequence':store.get_run(accepted['run_id'])['run']['sequence']}
                    headers={'Authorization':'Bearer owner'}
                    self.assertEqual(403,client.post('/v1/recovery/retry-review',headers={'Authorization':'Bearer review-token'},json=command).status_code)
                    self.assertEqual(403,client.post('/v1/recovery/retry-review',headers={'Authorization':'Bearer other-owner'},json=command).status_code)
                    stale=dict(command,expected_sequence=command['expected_sequence']+1)
                    self.assertEqual(409,client.post('/v1/recovery/retry-review',headers=headers,json=stale).status_code)
                    result=client.post('/v1/recovery/retry-review',headers=headers,json=command)
                    self.assertEqual(200,result.status_code,result.text); self.assertEqual('RESUMED',result.json()['status'])
                    self.assertEqual(1,api.patch_count); self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])
                    self.assertEqual('1',(case.repo/'counter.txt').read_text())
                    self.assertEqual(task['assigned_session_id'],store.get_task(task['task_id'])['assigned_session_id'])
                    replay=client.post('/v1/recovery/retry-review',headers=headers,json=command)
                    self.assertEqual(200,replay.status_code); self.assertEqual(1,api.patch_count)
                    identity={k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')}
                    reviewer_headers={'Authorization':'Bearer review-token','X-MS14-Reviewer-Session':sid}
                    claimed=client.post('/v1/review/claim',headers=reviewer_headers,json={'contract_version':1,'command_id':'claim-after-retry',**identity})
                    self.assertTrue(claimed.json()['accepted'],claimed.text)
                    receipt=[r for r in store.get_task(task['task_id'])['result']['artifact_refs'] if store.get_artifact(r)['artifact']['type']=='CHECK_RECEIPT'] if store.get_task(task['task_id']).get('result') else []
                    checkrefs=[r for r in store.get_task(check)['result']['artifact_refs'] if store.get_artifact(r)['artifact']['type']=='CHECK_RECEIPT']
                    api.active[sid]=False
                    completed=client.post('/v1/review/complete',headers=reviewer_headers,json={'contract_version':1,**identity,'decision':'PASS','findings':[],'owner_followups':[],'inspected_refs':checkrefs})
                    self.assertTrue(completed.json()['accepted'],completed.text)
                    end=time.monotonic()+5
                    while time.monotonic()<end and store.get_run(accepted['run_id'])['attempt']['phase']!='DECIDED': time.sleep(.02)
                    if store.get_run(accepted['run_id'])['attempt']['phase']!='DECIDED':
                        diagnostic={'run':store.get_run(accepted['run_id']),'task':store.get_task(task['task_id']),
                            'task_incident':store.get_task_incident(task['task_id']),'resources':store.get_task_resources(task['task_id']),
                            'retry_receipts':store.list_review_retry_receipts(task['task_id']),'outbox':store.outbox_summary(),
                            'coordinator_error':co.last_error,'calls':api.calls,'prompts':api.posts}
                        artifact=Path(__file__).resolve().parents[2]/'build/agent-workflow/workflow-redesign-plan/wr12/attempts'/('review-service-retry-failure-'+uuid.uuid4().hex)
                        artifact.mkdir(parents=True,exist_ok=False)
                        (artifact/'diagnostic.json').write_text(json.dumps(diagnostic,default=str,indent=2))
                        print('Retained review retry failure evidence:',artifact)
                    self.assertEqual('PASS',store.get_run(accepted['run_id'])['attempt']['result'],
                        {'run':store.get_run(accepted['run_id']),'task':store.get_task(task['task_id']),
                         'resources':store.get_task_resources(task['task_id']),'calls':api.calls,'error':co.last_error})
            finally: co.stop()


if __name__=='__main__': unittest.main()
