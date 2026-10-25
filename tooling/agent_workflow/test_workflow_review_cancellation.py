import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import workflow_review_cancellation as adapter_module
import workflow_review_control
import workflow_review_transport
import workflow_store
from workflow_store_test_support import admission_records
from test_workflow_review_retry import ReviewRetryTests


class Store:
    def __init__(self, root, state='CANCEL_REQUESTED'):
        self.root=Path(root); self.path=str(self.root/'fixture-store.sqlite'); self.task={'task_id':'task-x','run_id':'run-x','evaluation_generation':1,
          'assignment_generation':1,'input_digest':'d'*64,'kind':'REVIEW','state':state,
          'assigned_session_id':'reviewer-sid','logical_key':'review:implementation'}
        self.author={'session_id':'author-sid'}; self.resource=None; self.artifacts={}; self.confirmed=0
        self.cancel={'event_id':'cancel-x','payload':{k:self.task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')}}
    def get_task(self, tid): return self.task if tid==self.task['task_id'] else None
    def get_run(self, rid): return {'run':{'author':self.author}}
    def find_task_cancel_event(self, tid): return self.cancel
    def get_task_resources(self, tid): return [self.resource] if self.resource else []
    def list_review_retry_receipts(self, tid): return getattr(self,'retry_receipts',[])
    def register_artifact(self, a, input_digest): self.artifacts[a['artifact_id']]={'artifact':a,'input_digest':input_digest}
    def get_artifact(self, aid): return self.artifacts.get(aid)
    def mark_resource_unknown(self, claim_id, *, identity_proof): self.resource['state']='UNKNOWN'
    def release_resource(self, *args, **kw):
        self.resource['state']='RELEASED'; self.resource['cleanup_proof']=kw['cleanup_proof']
    def confirm_task_cancelled(self, **kw): self.confirmed+=1; self.task['state']='CANCELLED'
    def record_incident(self, incident): self.incident=incident


class Planner:
    def __init__(self, assignment, root):
        self.reviewers={'implementation':{'role':'reviewer','worktree':'C:/review-worktree'}}
        self.assignment=assignment; self.artifact_dir=Path(root)
    def build_assignment(self, _): return self.assignment


class Harness:
    sid='reviewer-sid'; mid='msg_'+hashlib.sha256(('task-x'+'b'*64).encode()).hexdigest()[:40]
    body={'envelope':{'event_id':'review-event'}}
    assignment={'body':body,'body_sha256':'b'*64}
    def __init__(self, root, state='CANCEL_REQUESTED', mode='queued'):
        self.store=Store(root,state); self.planner=Planner(self.assignment,root); self.mode=mode; self.calls=[]; self.inbox=[self.message()]; self.active=mode=='active'
        self.session={'id':self.sid,'agent':'reviewer-agent','directory':'C:/review-worktree'}; self.history=[]
        proof={'server':'http://127.0.0.1:4096','session_id':self.sid,'message_id':self.mid,'body_sha256':'b'*64,
          'task_id':'task-x','run_id':'run-x','evaluation_generation':1,'assignment_generation':1,'input_digest':'d'*64}
        self.store.resource={'claim_id':'claim-x','resource_key':'review-session:'+hashlib.sha256(b'key').hexdigest(),
          'run_id':'run-x','generation':1,'task_id':'task-x','assignment_generation':1,'identity_proof':proof,'state':'HELD','cleanup_proof':None}
        # Receipt is absent, so exact inbox observation establishes admission.
        self.transport=type('Transport',(),{'_receipt':lambda *_:False,
          '_baseline_artifact_id':lambda _,task:workflow_review_transport.ReviewDispatchBackend._baseline_artifact_id(task),
          '_receipt_artifact_id':lambda _,task:workflow_review_transport.ReviewDispatchBackend._receipt_artifact_id(task),
          'has_registered_prompt_evidence':lambda _,task:any(self.store.get_artifact(aid) is not None for aid in (
            workflow_review_transport.ReviewDispatchBackend._baseline_artifact_id(task),
            workflow_review_transport.ReviewDispatchBackend._receipt_artifact_id(task))),
          '_creation_artifact':lambda _,task,verified=False: {'kind':'REVIEW_SESSION_CREATE_INTENT'} if mode=='intent404' and not verified else None,
          'get_session_baseline':lambda *_:None if mode=='uncreated' else {'artifact_ref':'artifact-baseline','record':{'observed_ms':1}},
          'session_matches_assignment':lambda _,task,assignment,reply:(reply.get('data',reply).get('id')==task['assigned_session_id'] and reply.get('data',reply).get('agent')=='reviewer-agent' and reply.get('data',reply).get('directory')=='C:/review-worktree')})()
        self.adapter=adapter_module.ReviewCancellation(self.store,self.planner,self.transport,server='http://127.0.0.1:4096',request=self.request,sleep=lambda _:None)
    def message(self): return {'id':self.mid,'sessionID':self.sid,'type':'user','delivery':'queue',
      'payload':{'text':workflow_store.canonical_json(self.body),
       'metadata':{'review_event_id':'review-event','task_id':'task-x','body_sha256':'b'*64}}}
    def synthetic(self,mid,text,created): return {'id':mid,'sessionID':self.sid,'type':'synthetic','delivery':'steer','time':{'created':created},'text':text}
    def add_retry_control(self, *, receipt_status='RESUMED'):
        command='retry-command'; cid='msg_'+hashlib.sha256(b'retry-control').hexdigest()[:40]
        cid='msg_'+hashlib.sha256(f"task-x\0{1}\0{1}\0{command}".encode()).hexdigest()[:40]
        text='Continue existing bound review task task-x from this session checkpoint. Do not rerun checks or create sessions. Claim/complete the existing assignment or report its precise blocker.'
        metadata={'kind':'REVIEW_CONTINUATION','task_id':'task-x','run_id':'run-x','evaluation_generation':1,
          'assignment_generation':1,'input_digest':'d'*64,'original_prompt_id':self.mid,
          'assignment_body_sha256':'b'*64,'command_id':command}
        body={'id':cid,'text':text,'delivery':'steer','resume':True,'metadata':metadata}
        control={'id':cid,'session_id':self.sid,'body_sha256':hashlib.sha256(workflow_store.canonical_json(body).encode()).hexdigest(),
          'text':text,'metadata':metadata,'body':body}
        receipt={'task_id':'task-x','command_id':command,'status':receipt_status,
          'binding':{k:self.store.task[k] for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')},
          'proof':self.store.resource['identity_proof'],'mode':'CONTROL_PROMPT','controls_snapshot':[control]}
        if receipt_status=='RESUMED': receipt['final_receipt']={'status':'RESUMED','task_id':'task-x','command_id':command,'assignment_generation':1,'input_digest':'d'*64}
        self.store.retry_receipts=[receipt]
        self.inbox.append({'id':cid,'sessionID':self.sid,'type':'user','delivery':'steer',
          'payload':{'text':text,'metadata':metadata}})
    def request(self, server, method, path, body=None):
        self.calls.append((method,path))
        if method=='GET' and path=='/api/session/'+self.sid:
            if self.mode in ('uncreated','intent404'): return 404,{'error':'not found'}
            return 200,{'data':self.session}
        if method=='GET' and path=='/api/session/'+self.sid+'/message?limit=50&order=desc':
            return 200,{'data':sorted(self.history,key=lambda m:m.get('time',{}).get('created',0),reverse=True),'cursor':{}}
        if method=='GET' and path.endswith('/inbox'): return 200,{'data':list(self.inbox)}
        if method=='DELETE':
            deleted=path.rsplit('/',1)[-1]
            self.inbox=[m for m in self.inbox if m['id']!=deleted]
            return 204,{}
        if method=='POST' and path.endswith('/interrupt?resume=false'):
            if self.mode=='refuse': return 503,{}
            # OpenCode can requeue the active prompt at interrupt.
            if self.mode=='active': self.inbox=[self.message(),self.synthetic('task-steer','owned control',100)]
            self.active=False
            return 200,{'interrupted':True}
        if method=='GET' and path=='/api/session/active':
            if self.mode=='null-active': return 200,{'data':{self.sid:None}}
            return 200,{'data':{self.sid:{'type':'running'}} if self.active else {}}
        raise AssertionError((method,path))
    def payload(self): return self.store.cancel['payload']


class ReviewCancellationTests(unittest.TestCase):
    def test_planner_role_bindings_are_instance_isolated(self):
        first=Planner(Harness.assignment,'first')
        second=Planner(Harness.assignment,'second')
        first.reviewers['implementation']['role']='author'
        self.assertEqual('author',first.reviewers['implementation']['role'])
        self.assertEqual('reviewer',second.reviewers['implementation']['role'])
        self.assertIsNot(first.reviewers['implementation'],second.reviewers['implementation'])

    def test_real_store_finds_persisted_cancellation_command(self):
        with tempfile.TemporaryDirectory() as root:
            store=workflow_store.WorkflowStore(Path(root)/'workflow.sqlite')
            request,run,task,event=admission_records('cancel-store',idempotency_key='cancel-store')
            store.submit(caller='local',request=request,run=run,first_task=task,outbox_event=event,logical_task_key='check')
            store.dispatch_task(task_id=task['task_id'],event_id='event_dispatch_cancel_store',
                process_identity={'pid':123,'created_ms':456},claim_token='test-token')
            store.transition_attempt(run['run_id'],1,expected_sequence=0,phase='STOPPING',proposed_result='ERROR',
              incident={'contract_version':1,'incident_id':'incident_cancel_store','run_id':run['run_id'],
                'evaluation_generation':1,'code':'STOP','message':'test stop','recovery_owner':'SYSTEM','state':'OPEN',
                'owner_action_required':False,'artifact_refs':[]})
            saved=store.find_task_cancel_event(task['task_id'])
            self.assertEqual('event_cancel_task_'+hashlib.sha256((run['run_id']+'\0'+'1'+'\0'+task['task_id']+'\0'+'1').encode()).hexdigest()[:24],saved['event_id'])
            self.assertEqual(task['task_id'],saved['payload']['task_id'])
    def test_real_store_control_prompt_receipt_is_accepted_for_cleanup(self):
        with tempfile.TemporaryDirectory() as root:
            store,planner,task,server,prompt,session=ReviewRetryTests().prepared(Path(root))
            assignment=planner.build_assignment(task['task_id'])
            resource=next(r for r in store.get_task_resources(task['task_id']) if r['state']=='HELD')
            proof=resource['identity_proof']; command='cleanup-control'
            binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            body=workflow_review_control.continuation_body(task,assignment,command)
            control={'id':body['id'],'session_id':task['assigned_session_id'],
              'body_sha256':hashlib.sha256(workflow_store.canonical_json(body).encode()).hexdigest(),
              'text':body['text'],'metadata':body['metadata'],'body':body}
            store.begin_review_retry(task_id=task['task_id'],caller='trusted',command_id=command,
                binding=binding,proof=proof,mode='CONTROL_PROMPT',controls_snapshot=[control])
            final={'status':'RESUMED','task_id':task['task_id'],'command_id':command,
                'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
            store.complete_review_retry(task_id=task['task_id'],caller='trusted',command_id=command,response=final)
            receipts=store.list_review_retry_receipts(task['task_id'])
            self.assertEqual('RESUMED',receipts[0]['status'])
            self.assertEqual(final,receipts[0]['final_receipt'])
            adapter=adapter_module.ReviewCancellation(store,planner,object(),server=server,request=lambda *a:(200,{}))
            controls,context=adapter._retry_controls(task,assignment,task['assigned_session_id'],proof,
                {'record':{'observed_ms':1}})
            self.assertEqual([body['id']],context['control_ids'])
            self.assertEqual(body,controls[0]['body'])
    def test_queued_prompt_cancel_drains_and_confirms(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root)
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual('CANCELLED',h.store.task['state']); self.assertEqual(1,h.store.confirmed)
            self.assertEqual('RELEASED',h.store.resource['state']); self.assertEqual([],h.inbox)
    def test_active_interrupt_requeue_is_deleted_twice_and_replay_uses_proof(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,mode='active')
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            deletes=[p for m,p in h.calls if m=='DELETE']
            self.assertEqual(3,len(deletes)); count=len(h.calls)
            self.assertTrue(any(p.endswith('/inbox/task-steer') for p in deletes))
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual(count,len(h.calls))
    def test_released_before_confirm_replay_confirms_without_api(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root)
            original=h.store.confirm_task_cancelled
            h.store.confirm_task_cancelled=lambda **kw: (_ for _ in ()).throw(RuntimeError('crash window'))
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual('RELEASED',h.store.resource['state']); count=len(h.calls)
            h.store.confirm_task_cancelled=original
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual(count,len(h.calls)); self.assertEqual('CANCELLED',h.store.task['state'])
    def test_refusal_and_mismatched_reviewer_role_hold_unknown_without_confirmation(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,mode='refuse')
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual('UNKNOWN',h.store.resource['state']); self.assertEqual(0,h.store.confirmed)
            self.assertEqual('SYSTEM',h.store.incident['recovery_owner'])
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root); h.planner.reviewers['implementation']['role']='author'
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual([],h.calls); self.assertEqual(0,h.store.confirmed)

    def test_reserved_without_creation_intent_is_released_and_replayable(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,mode='uncreated')
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual('CANCELLED',h.store.task['state'])
            self.assertEqual('RELEASED',h.store.resource['state'])
            proof=h.store.get_artifact(h.store.resource['cleanup_proof'])
            saved=json.loads(Path(proof['artifact']['path']).read_text())
            self.assertEqual('REVIEW_NO_EXTERNAL_EFFECT_CLEANUP',saved['purpose'])
            self.assertTrue(saved['intent_absent'] and saved['verified_creation_absent'] and
                            saved['prompt_receipt_absent'] and saved['session_not_found'])
            call_count=len(h.calls)
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual(call_count,len(h.calls))
            self.assertFalse(any(method in ('POST','DELETE') for method,_ in h.calls))
            aid=h.transport._receipt_artifact_id(h.store.task)
            h.store.register_artifact({'artifact_id':aid,'type':'PROVENANCE','path':str(Path(root)/'invalid-receipt'),
                'run_id':'run-x','producer_task_id':'task-x','producer_kind':'TASK','producer_id':'task-x',
                'immutable':True,'sha256':'0'*64,'size_bytes':1},input_digest='d'*64)
            calls=len(h.calls)
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual(calls,len(h.calls))

    def test_registered_but_unreadable_prompt_artifacts_block_zeroeffect_cleanup(self):
        with tempfile.TemporaryDirectory() as root:
            for kind in ('baseline','receipt'):
                with self.subTest(kind=kind):
                    h=Harness(root,mode='uncreated')
                    aid=(h.transport._baseline_artifact_id(h.store.task) if kind=='baseline'
                         else h.transport._receipt_artifact_id(h.store.task))
                    h.store.register_artifact({'artifact_id':aid,'type':'PROVENANCE','path':str(Path(root)/('missing-'+kind)),
                        'run_id':'run-x','producer_task_id':'task-x','producer_kind':'TASK','producer_id':'task-x',
                        'immutable':True,'sha256':'0'*64,'size_bytes':1},input_digest='d'*64)
                    self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
                    self.assertEqual('CANCEL_REQUESTED',h.store.task['state'])
                    self.assertEqual('UNKNOWN',h.store.resource['state'])
                    self.assertEqual(0,h.store.confirmed)

    def test_creation_intent_with_missing_session_stays_unknown_and_unconfirmed(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,mode='intent404')
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual('CANCEL_REQUESTED',h.store.task['state'])
            self.assertEqual('UNKNOWN',h.store.resource['state'])
            self.assertEqual(0,h.store.confirmed)
            self.assertEqual([('GET','/api/session/'+h.sid)],h.calls)
    def test_foreign_or_prebaseline_control_prevents_interrupt_and_deletion(self):
        for item in ({'id':'foreign','sessionID':'reviewer-sid','type':'user','delivery':'queue','time':{'created':100},'text':'foreign'},
                     {'id':'old','sessionID':'reviewer-sid','type':'synthetic','delivery':'steer','time':{'created':0},'text':'old'}):
            with self.subTest(item=item), tempfile.TemporaryDirectory() as root:
                h=Harness(root); h.inbox.append(item)
                self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
                self.assertFalse(any(m=='POST' and p.endswith('/interrupt?resume=false') for m,p in h.calls))
                self.assertFalse(any(m=='DELETE' for m,_ in h.calls)); self.assertEqual('UNKNOWN',h.store.resource['state'])
    def test_active_map_present_null_session_is_not_proof_of_inactivity(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,mode='null-active')
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual('UNKNOWN',h.store.resource['state']); self.assertEqual(0,h.store.confirmed)
    def test_saved_review_continuation_control_and_synthetic_are_drained(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,mode='active'); h.add_retry_control()
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            deleted=[p.rsplit('/',1)[-1] for m,p in h.calls if m=='DELETE']
            self.assertIn(h.mid,deleted); self.assertIn(h.store.retry_receipts[0]['controls_snapshot'][0]['id'],deleted)
            self.assertIn('task-steer',deleted); self.assertEqual([],h.inbox)
            self.assertEqual(1,sum(m=='POST' and p.endswith('/interrupt?resume=false') for m,p in h.calls))
    def test_completed_resumed_receipt_retains_control_ownership(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,state='COMPLETED'); h.add_retry_control(receipt_status='RESUMED')
            h.store.task['result']={'decision':'PASS'}
            self.assertEqual('APPLIED',h.adapter.cleanup_completed('task-x'))
            self.assertIn(h.store.retry_receipts[0]['controls_snapshot'][0]['id'],[p.rsplit('/',1)[-1] for m,p in h.calls if m=='DELETE'])
            self.assertEqual({'decision':'PASS'},h.store.task['result']); self.assertEqual(0,h.store.confirmed)
    def test_mismatched_saved_control_payload_holds_without_interrupt(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root); h.add_retry_control()
            h.inbox[-1]['payload']['text']='tampered inbox text'
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertFalse(any(m=='POST' and p.endswith('/interrupt?resume=false') for m,p in h.calls))
            self.assertEqual('UNKNOWN',h.store.resource['state'])
    def test_consumed_foreign_user_in_history_prevents_session_interrupt(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root); h.inbox=[]; h.active=True
            h.history=[{'id':'foreign-user','type':'user','time':{'created':10},'text':'another task','metadata':{}}]
            self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
            self.assertFalse(any(m=='DELETE' or (m=='POST' and p.endswith('/interrupt?resume=false')) for m,p in h.calls))
            self.assertEqual('UNKNOWN',h.store.resource['state'])
    def test_cancel_discards_noncanonical_history_but_completed_cleanup_preserves_fence(self):
        for state, expected in [('CANCEL_REQUESTED','APPLIED'),('COMPLETED','UNKNOWN')]:
            with self.subTest(state=state), tempfile.TemporaryDirectory() as root:
                h=Harness(root,state=state); h.history=[{'id':'foreign-user','type':'user','time':{'created':10},'text':'parent notice'}]
                result=h.adapter.cancel('cancel-x',h.payload()) if state=='CANCEL_REQUESTED' else h.adapter.cleanup_completed('task-x')
                self.assertEqual(expected,result)
                interrupted=any(m=='POST' and p.endswith('/interrupt?resume=false') for m,p in h.calls)
                self.assertEqual(state=='CANCEL_REQUESTED',interrupted)
    def test_cancel_discards_pending_noncanonical_notice_in_owned_session(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root); h.inbox.append({'id':'notice','sessionID':h.sid,'type':'user','time':{'created':2},
                'workflow_service_delivery_repair':True,'text':'parent notice','metadata':{'workflow_service_delivery_repair':True,
                'task_id':'task-x','run_id':'run-x','session_id':h.sid}})
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
            self.assertEqual('CANCELLED',h.store.task['state']); self.assertEqual('RELEASED',h.store.resource['state'])
            self.assertFalse(h.inbox)
    def test_session_identity_change_prevents_deletion_and_interrupt(self):
        for field,value in [('agent','wrong-agent'),('directory','C:/foreign-worktree')]:
            with self.subTest(field=field),tempfile.TemporaryDirectory() as root:
                h=Harness(root); h.session[field]=value
                self.assertEqual('UNKNOWN',h.adapter.cancel('cancel-x',h.payload()))
                self.assertFalse(any(m=='DELETE' or (m=='POST' and p.endswith('/interrupt?resume=false')) for m,p in h.calls))
    def test_valid_original_and_control_history_records_allow_drain(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root); h.add_retry_control()
            control=h.store.retry_receipts[0]['controls_snapshot'][0]
            h.history=[{'id':h.mid,'sessionID':h.sid,'type':'user','delivery':'queue','time':{'created':2},
                'payload':{'text':workflow_store.canonical_json(h.body),'metadata':h.message()['payload']['metadata']}},
                {'id':control['id'],'sessionID':h.sid,'type':'user','delivery':'steer','time':{'created':3},
                'payload':{'text':control['text'],'metadata':control['metadata']}}]
            self.assertEqual('APPLIED',h.adapter.cancel('cancel-x',h.payload()))
    def test_completed_cleanup_drains_without_changing_result_or_confirmation(self):
        with tempfile.TemporaryDirectory() as root:
            h=Harness(root,state='COMPLETED'); h.store.task['result']={'decision':'PASS'}
            self.assertEqual('APPLIED',h.adapter.cleanup_completed('task-x'))
            self.assertEqual({'decision':'PASS'},h.store.task['result']); self.assertEqual(0,h.store.confirmed)


if __name__=='__main__': unittest.main()
