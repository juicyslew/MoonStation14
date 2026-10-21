"""Service-boundary integration tests for the configured review pipeline."""
import json
import tempfile
import time
import unittest
import uuid
from pathlib import Path

import workflow_coordinator
import workflow_native_execution
import workflow_store
from test_workflow_native_execution import admit_native_fixture
from unittest import mock


class FakeOpenCode:
    def __init__(self, sessions):
        self.sessions=sessions
        self.posts=[]
        self.inboxes={sid:[] for sid in sessions}
        self.history={sid:[] for sid in sessions}
        self.prompts={sid:None for sid in sessions}
        self.active={}
        self.calls=[]

    def __call__(self, server, method, path, body=None):
        self.calls.append((method,path))
        if method=='GET' and path.startswith('/api/session/') and path != '/api/session/active' and path.count('/')==3:
            sid=path.rsplit('/',1)[-1]
            if sid not in self.sessions: return 404,{'error':'not found'}
            return 200,{'data':self.sessions[sid]}
        if method=='POST' and path=='/api/session':
            sid=body['id']
            session={'id':sid,'agent':body['agent'],'directory':body['location']['directory'],
                     'metadata':body['metadata']}
            self.sessions[sid]=session
            self.inboxes[sid]=[]; self.history[sid]=[]; self.prompts[sid]=None
            return 200,{'data':session}
        if method=='POST' and path.endswith('/prompt'):
            self.posts.append(body)
            sid=path.split('/')[-2]
            item={'id':body['id'],'sessionID':sid,'type':'user','delivery':body['delivery'],
                  'payload':{'text':body['text'],'metadata':body['metadata']},
                  'time':{'created':int(time.time()*1000)}}
            self.inboxes[sid].append(item); self.history[sid].append(item)
            self.prompts[sid]=self.inboxes[sid][-1]
            self.active[sid]={'type':'running'}
            ack={'id':body['id'],'sessionID':sid,'type':'user','delivery':body['delivery'],
                 'payload':{'text':body['text'],'metadata':body['metadata']}}
            return 200,{'data':ack}
        if method=='GET' and path=='/api/session/active':
            return 200,{'data':{sid:active for sid,active in self.active.items()
                                if active and self.prompts.get(sid) is not None}}
        if method=='GET' and path.endswith('/message?limit=50&order=desc'):
            sid=path.split('/')[-2]
            return 200,{'data':sorted(self.history[sid],key=lambda m:m.get('time',{}).get('created',0),reverse=True),'cursor':{}}
        if method=='GET' and path.endswith('/inbox'):
            sid=path.split('/')[-2]
            return 200,{'data':list(self.inboxes[sid])}
        if method=='DELETE' and '/inbox/' in path:
            sid=path.split('/')[-3]; message_id=path.rsplit('/',1)[-1]
            self.inboxes[sid]=[m for m in self.inboxes[sid] if m['id']!=message_id]
            return 204,{}
        if method=='POST' and path.endswith('/interrupt?resume=false'):
            sid=path.split('/')[-2]
            # OpenCode may requeue the task after interruption.
            original=self.prompts[sid]
            if original: self.inboxes[sid].append(original)
            self.active[sid]=False
            return 200,{'interrupted':True}
        raise AssertionError(f'unexpected OpenCode route {method} {path}')


class TestIdentity:
    server='http://127.0.0.1:1'
    def __call__(self, _context): return True


class ReviewServiceTests(unittest.TestCase):
    def test_actual_factory_uses_one_review_pipeline_and_frozen_roles_without_provider_calls(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,_,_,_=admit_native_fixture(root,caller='factory-author',key='factory-case',review_roles=('parity','fidelity'))
            token=root/'author-token'; token.write_text('author-secret')
            context=root/'context.json'
            context.write_text(json.dumps({'repo':str(case.context.repo),'authenticated_author':case.context.authenticated_author,
                'policy':case.context.policy,'artifacts':[dict(case.artifact)],
                'publication_receipt_path':str(case.receipt),
                'publication_receipt_sha256':case.context.publication_receipt_sha256}))
            config={'database':str(root/'factory.sqlite'),'artifact_dir':str(root/'factory-artifacts'),
                'opencode_server':'http://localhost:4096','bindings':[{'token_file':str(token),'caller':'factory-author',
                'context_file':str(context)}],'reviewers':{}}
            for role in ('parity','fidelity'):
                reviewer_token=root/(role+'-token'); reviewer_token.write_text(role+'-secret')
                config['reviewers'][role]={'worktree':str(case.repo),
                    'agent':'workflow-reviewer-'+role,'token_file':str(reviewer_token),'caller':'caller-'+role}
            config_path=root/'config.json'; config_path.write_text(json.dumps(config))
            with mock.patch('gate_dispatch._request',side_effect=AssertionError('factory must not call OpenCode')) as request:
                coordinator=workflow_coordinator.load_coordinator(config_path)
            try:
                self.assertEqual({'TASK_READY','TASK_COMPLETED','TASK_CANCELLED','ATTEMPT_CLOSED','RECOVERY_STARTED','TASK_ASSIGNMENT',
                                  'REGRESSION_READY','RESULT_AVAILABLE','OWNER_FEEDBACK',
                                  'REGRESSION_DECIDED','CANCEL_TASK'},set(coordinator.handlers))
                self.assertIs(coordinator.handlers['OWNER_FEEDBACK'],coordinator.handlers['RESULT_AVAILABLE'])
                self.assertIs(coordinator.handlers['REGRESSION_DECIDED'],coordinator.handlers['RESULT_AVAILABLE'])
                self.assertEqual({'parity','fidelity'},set(coordinator.review_pipeline.planner.reviewers))
                self.assertEqual('http://127.0.0.1:4096',coordinator.identity_verifier.server)
                self.assertEqual(0,request.call_count)
            finally: coordinator.stop()
            config['reviewers']['integration']=dict(config['reviewers']['parity'])
            with self.assertRaisesRegex(ValueError,'known review roles'):
                config_path.write_text(json.dumps(config)); workflow_coordinator.load_coordinator(config_path)

    def run_case(self, root, fail):
        roles=('implementation','coverage')
        case,store,accepted,check_id=admit_native_fixture(root,caller='service-author',key='service-case',
                                                          review_roles=roles,fail_check=fail)
        native=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'native-lock')
        author=workflow_coordinator.CoordinatorBinding('service-author',case.context)
        coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'author-token':author},
            artifact_dir=root/'artifacts',handlers={'TASK_READY':native},identity_verifier=TestIdentity(),
            reconcile_interval=.05)
        reviewer_bindings={role:{'role':'reviewer',
                                   'worktree':str(case.repo)} for role in roles}
        tokens={role+'-token':workflow_coordinator.ReviewerBinding(role,str(case.repo),role)
                for role in roles}
        fake=FakeOpenCode({})
        workflow_coordinator.configure_review_pipeline(coordinator,reviewer_bindings,
            {role:'workflow-reviewer-'+role for role in roles},tokens,transport_request=fake)
        from starlette.testclient import TestClient
        try:
            with TestClient(coordinator.app()) as client:
                if fail:
                    wait_for(lambda:store.get_run(accepted['run_id'])['attempt']['phase']=='DECIDED')
                    self.assertEqual('FAIL',store.get_run(accepted['run_id'])['attempt']['result'])
                    self.assertEqual([], [t for t in store.list_tasks(accepted['run_id'],1) if t['kind']=='REVIEW'])
                    self.assertEqual([],fake.posts)
                    return
                wait_for(lambda:len(fake.posts)==2)
                created_ids=set(fake.sessions)
                self.assertEqual(2,len(created_ids))
                self.assertEqual(2,len({task['assigned_session_id'] for task in store.list_tasks(accepted['run_id'],1)
                                        if task['kind']=='REVIEW'}))
                self.assertEqual('REVIEWING',store.get_run(accepted['run_id'])['attempt']['phase'])
                self.assertEqual('1',(case.repo/'counter.txt').read_text())
                reviews=[t for t in store.list_tasks(accepted['run_id'],1) if t['kind']=='REVIEW']
                self.assertEqual(2,len(reviews))
                refs=[ref for ref in store.get_task(check_id)['result']['artifact_refs']
                      if store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT']
                self.assertTrue(refs)
                submitted=[]
                for task in reviews:
                    token='Bearer '+task['logical_key'].removeprefix('review:')+'-token'
                    headers={'Authorization':token,'X-MS14-Reviewer-Session':task['assigned_session_id']}
                    identity={k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')}
                    claim=client.post('/v1/review/claim',headers=headers,json={'contract_version':1,
                        'command_id':'claim-'+task['task_id'],**identity})
                    self.assertTrue(claim.json()['accepted'],claim.json())
                    body={'contract_version':1,**identity,
                        'decision':'PASS','findings':[{'criterion_id':'criterion-1','category':'IMPLEMENTATION',
                            'message':'Reviewed verified check receipt','location':'source tree','evidence_refs':refs}],
                        'inspected_refs':refs}
                    submitted.append((headers,body))
                    result=client.post('/v1/review/complete',headers=headers,json=body)
                    self.assertTrue(result.json()['accepted'],result.json())
                wait_for(lambda:store.get_run(accepted['run_id'])['attempt']['phase']=='DECIDED',timeout=5)
                self.assertEqual('PASS',store.get_run(accepted['run_id'])['attempt']['result'])
                db=store._connect()
                try:
                    completed=json.loads(db.execute("SELECT payload FROM events WHERE kind='TASK_COMPLETED' AND json_extract(payload,'$.task_id')=? ORDER BY created_ms LIMIT 1",(check_id,)).fetchone()[0])
                finally: db.close()
                adapter=coordinator.handlers['TASK_COMPLETED']
                self.assertEqual('ABSENT',adapter.observe(completed['event_id'],completed))
                # A reclaimed durable completion wake must rerun idempotent DB reconciliation.
                adapter.dispatch(completed['event_id'],completed)
                posts=len(fake.posts); tasks=len(store.list_tasks(accepted['run_id'],1))
                self.assertEqual('1',(case.repo/'counter.txt').read_text())
                self.assertEqual(posts,2); self.assertEqual(tasks,3)
                for headers,body in submitted:
                    replay=client.post('/v1/review/complete',headers=headers,json=body)
                    self.assertTrue(replay.json()['accepted'],replay.json())
                    self.assertEqual('REPLAYED',replay.json()['disposition'])
                self.assertEqual(2,len(fake.posts)); self.assertEqual(3,len(store.list_tasks(accepted['run_id'],1)))
        except Exception:
            failure_dir=Path(__file__).resolve().parents[2]/'build/agent-workflow/workflow-redesign-plan/wr12/attempts'/('review-service-failure-'+uuid.uuid4().hex)
            failure_dir.mkdir(parents=True,exist_ok=False)
            (failure_dir/'state.json').write_text(json.dumps({'run':store.get_run(accepted['run_id']),
                'tasks':store.list_tasks(accepted['run_id'],1),'outbox':store.outbox_summary(),
                'resources':{t['task_id']:store.get_task_resources(t['task_id']) for t in store.list_tasks(accepted['run_id'],1)},
                'incidents':{t['task_id']:store.get_task_incident(t['task_id']) for t in store.list_tasks(accepted['run_id'],1)},
                'coordinator_error':coordinator.last_error,'posts':fake.posts,'api_calls':fake.calls},default=str,indent=2))
            artifact=root/'artifacts'/check_id/'worker.log'
            if artifact.is_file(): (failure_dir/'worker.log').write_bytes(artifact.read_bytes())
            print('Retained review service failure evidence:',failure_dir)
            raise
        finally:
            coordinator.stop()

    def test_native_pass_then_two_real_review_api_results_decide_pass(self):
        with tempfile.TemporaryDirectory() as tmp: self.run_case(Path(tmp),False)

    def test_native_failure_decides_fail_without_reviewer_dispatch(self):
        with tempfile.TemporaryDirectory() as tmp: self.run_case(Path(tmp),True)


def wait_for(predicate, timeout=10):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if predicate(): return
        time.sleep(.02)
    raise AssertionError('bounded wait expired')


if __name__=='__main__': unittest.main()
