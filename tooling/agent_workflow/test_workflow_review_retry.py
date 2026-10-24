import tempfile
import unittest
from pathlib import Path

import workflow_review_retry
import workflow_store
from test_workflow_review_dependency import ReviewDependencyTests


class FakeAPI:
    def __init__(self, sid, session, prompt, *, patch_error=False, foreign=False, before_effect=False):
        self.sid, self.session, self.prompt = sid, session, prompt
        self.patch_error, self.foreign = patch_error, foreign
        self.before_effect=before_effect
        self.calls=[]
        self.history=[{'id':prompt['id'],'type':'user','time':{'created':__import__('time').time_ns()//1_000_000},
            'text':prompt['payload']['text'],'metadata':prompt['payload']['metadata']}]

    def __call__(self, server, method, path, body=None):
        self.calls.append((method,path,body))
        prefix='/api/session/'+self.sid
        if method=='GET' and path==prefix: return 200, {'data':self.session}
        if method=='GET' and path=='/api/session/active': return 200, {'data':{}}
        if method=='GET' and path==prefix+'/inbox':
            rows=[self.prompt,dict(self.prompt,id='foreign-msg',time={'created':9999999999999})] if self.foreign else [self.prompt]
            return 200, {'data':rows}
        if method=='GET' and path==prefix+'/message?limit=50&order=desc':
            return 200,{'data':sorted(self.history,key=lambda r:r['time']['created'],reverse=True),'cursor':{}}
        if method=='PATCH' and path==prefix+'/inbox/'+self.prompt['id']:
            if body != {'delivery':'steer'}: raise AssertionError('PATCH must only change delivery')
            if self.before_effect: raise OSError('failed before effect')
            if self.patch_error:
                self.prompt['delivery']='steer'
                raise OSError('reply lost after effect')
            return 204, None
        raise AssertionError(f'unexpected call {method} {path}')


class ConsumedPromptAPI:
    def __init__(self,sid,session,history,control):
        self.sid,self.session,self.history,self.control=sid,session,history,control; self.active=False; self.posts=0; self.calls=[]
    def __call__(self,server,method,path,body=None):
        self.calls.append((method,path,body)); prefix='/api/session/'+self.sid
        if method=='GET' and path==prefix: return 200,{'data':self.session}
        if method=='GET' and path=='/api/session/active': return 200,{'data':{self.sid:{'type':'running'}} if self.active else {}}
        if method=='GET' and path==prefix+'/inbox': return 200,{'data':[self.control]}
        if method=='GET' and path==prefix+'/message?limit=50&order=desc': return 200,{'data':self.history,'cursor':{}}
        if method=='POST' and path==prefix+'/interrupt?resume=true':
            self.posts+=1; self.active=True; return 200,{'interrupted':False}
        raise AssertionError(f'unexpected call {method} {path}')


class EmptyCheckpointAPI(ConsumedPromptAPI):
    def __init__(self,sid,session,history):
        super().__init__(sid,session,history,None); self.inbox=[]; self.control_posts=0; self.lose_reply=False
    def __call__(self,server,method,path,body=None):
        self.calls.append((method,path,body)); prefix='/api/session/'+self.sid
        if method=='GET' and path==prefix+'/message?limit=50&order=desc':
            return 200,{'data':sorted(self.history,key=lambda r:r.get('time',{}).get('created',-1),reverse=True),'cursor':{}}
        if method=='GET' and path==prefix: return 200,{'data':self.session}
        if method=='GET' and path=='/api/session/active': return 200,{'data':{}}
        if method=='GET' and path==prefix+'/inbox': return 200,{'data':list(self.inbox)}
        if method=='GET' and path==prefix+'/message?limit=50&order=desc': return 200,{'data':self.history,'cursor':{}}
        if method=='POST' and path==prefix+'/prompt':
            self.control_posts+=1
            record={'id':body['id'],'type':'user','time':{'created':9999999999999},'text':body['text'],'metadata':body['metadata']}
            self.inbox.append({'id':body['id'],'sessionID':self.sid,'type':'user','delivery':'steer','payload':{'text':body['text'],'metadata':body['metadata']}})
            self.history.append(record)
            if self.lose_reply:
                self.inbox.clear()
            if self.lose_reply: raise OSError('admitted but response lost')
            return 200,{'data':{'id':body['id'],'sessionID':self.sid,'type':'user','delivery':'steer','payload':{'text':body['text'],'metadata':body['metadata']}}}
        raise AssertionError(f'unexpected call {method} {path}')


class ReviewRetryTests(unittest.TestCase):
    def prepared(self, root):
        fixture=ReviewDependencyTests(); store,planner,task,server,envelope=fixture.prepared(root)
        assignment=planner.build_assignment(task['task_id']); event=store.get_event(envelope['event_id'])
        fixture.monitor if hasattr(fixture,'monitor') else None
        binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
        store.pause_dependency_task(task_id=task['task_id'],dependency_key=server+':provider.auth',failure_id='401',binding=binding,now_ms=event['created_ms']+20)
        mid='msg_'+__import__('hashlib').sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
        prompt={'id':mid,'type':'user','sessionID':task['assigned_session_id'],'delivery':'queue','payload':{'text':workflow_store.canonical_json(assignment['body']),
            'metadata':{'review_event_id':envelope['event_id'],'task_id':task['task_id'],'body_sha256':assignment['body_sha256']}}}
        role=task['logical_key'].removeprefix('review:'); reviewer=planner.reviewers[role]
        session={'id':task['assigned_session_id'],'agent':'workflow-reviewer-'+role,'directory':reviewer['worktree'],
            'metadata':{'ms14_review_task_id':task['task_id'],'ms14_review_run_id':task['run_id'],
                'ms14_review_evaluation_generation':task['evaluation_generation'],
                'ms14_review_assignment_generation':task['assignment_generation']}}
        return store,planner,task,server,prompt,session

    def test_queue_patch_once_resume_and_command_replay_without_api(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp)); fake=FakeAPI(task['assigned_session_id'],session,prompt)
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=fake,now_ms=lambda:100000,agents={'implementation':'workflow-reviewer-implementation'})
            first=service.retry(task['task_id'],'cmd1','trusted')
            self.assertEqual('RESUMED',first['status']); self.assertEqual(1,sum(c[0]=='PATCH' for c in fake.calls))
            calls=len(fake.calls); again=service.retry(task['task_id'],'cmd1','trusted')
            self.assertEqual(first,again); self.assertEqual(calls,len(fake.calls))
            self.assertEqual(task['assignment_generation'],store.get_task(task['task_id'])['assignment_generation'])

    def test_acknowledged_wake_after_stopping_is_durable_control_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp))
            fake=FakeAPI(task['assigned_session_id'],session,prompt)
            original=fake.__call__
            def request(host,method,path,body=None):
                if method=='PATCH':
                    current=store.get_run(task['run_id'])
                    store.transition_attempt(task['run_id'],task['evaluation_generation'],
                        expected_sequence=current['run']['sequence'],phase='STOPPING',closure='CANCELLED')
                return original(host,method,path,body)
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=request,
                now_ms=lambda:100000,agents={'implementation':'workflow-reviewer-implementation'})
            receipt=service.retry(task['task_id'],'cmd-stop-ack','trusted')
            self.assertEqual('STOPPING',receipt['status']); self.assertEqual('APPLIED',receipt['effect_status'])
            self.assertTrue(store.task_is_dependency_paused(task['task_id']))
            saved=store.get_review_retry(task_id=task['task_id'],caller='trusted',command_id='cmd-stop-ack')
            self.assertEqual(receipt,saved)
            calls=len(fake.calls)
            self.assertEqual(receipt,service.retry(task['task_id'],'cmd-stop-ack','trusted'))
            self.assertEqual(calls,len(fake.calls))

    def test_lost_patch_reply_is_durable_unknown_and_never_repeated(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp)); fake=FakeAPI(task['assigned_session_id'],session,prompt,patch_error=True)
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=fake,now_ms=lambda:100000,agents={'implementation':'workflow-reviewer-implementation'})
            self.assertEqual('UNKNOWN',service.retry(task['task_id'],'cmd2','trusted')['status'])
            calls=len(fake.calls); resolved=service.retry(task['task_id'],'cmd2','trusted')
            self.assertEqual('RESUMED',resolved['status'])
            self.assertEqual(1,sum(c[0]=='PATCH' for c in fake.calls)); self.assertFalse(store.task_is_dependency_paused(task['task_id']))

    def test_foreign_inbox_rejects_without_patch(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp)); fake=FakeAPI(task['assigned_session_id'],session,prompt,foreign=True)
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=fake,agents={'implementation':'workflow-reviewer-implementation'})
            self.assertNotEqual('RESUMED',service.retry(task['task_id'],'cmd3','trusted')['status'])
            self.assertFalse(any(c[0]=='PATCH' for c in fake.calls))

    def test_pre_effect_failure_blocks_other_command_wake(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp)); fake=FakeAPI(task['assigned_session_id'],session,prompt,before_effect=True)
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=fake,agents={'implementation':'workflow-reviewer-implementation'})
            self.assertEqual('UNKNOWN',service.retry(task['task_id'],'cmd4','trusted')['status'])
            count=sum(c[0]=='PATCH' for c in fake.calls)
            self.assertEqual('UNKNOWN',service.retry(task['task_id'],'cmd5','trusted')['status'])
            self.assertEqual(count,sum(c[0]=='PATCH' for c in fake.calls)); self.assertTrue(store.task_is_dependency_paused(task['task_id']))

    def test_consumed_original_with_owned_synthetic_control_uses_resume_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp))
            assignment=planner.build_assignment(task['task_id']); event=store.get_event(assignment['body']['envelope']['event_id'])
            original={'id':prompt['id'],'type':'user','time':{'created':event['created_ms']+2},
                'text':prompt['payload']['text'],'metadata':prompt['payload']['metadata']}
            control={'id':'synthetic-control','sessionID':task['assigned_session_id'],'time':{'created':event['created_ms']+4},
                'type':'synthetic','delivery':'steer','payload':{'text':'Plan reminder'}}
            api=ConsumedPromptAPI(task['assigned_session_id'],session,[original],control)
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,now_ms=lambda:100000,agents={'implementation':'workflow-reviewer-implementation'})
            result=service.retry(task['task_id'],'cmd-consumed','trusted')
            self.assertEqual('RESUMED',result['status'],result)
            self.assertEqual(1,api.posts); self.assertFalse(any(c[0]=='PATCH' for c in api.calls))
            calls=len(api.calls); self.assertEqual(result,service.retry(task['task_id'],'cmd-consumed','trusted'))
            self.assertEqual(calls,len(api.calls)); self.assertFalse(store.task_is_dependency_paused(task['task_id']))

    def test_foreign_or_predispatch_synthetic_control_does_not_wake(self):
        for foreign,old in ((True,False),(False,True)):
            with self.subTest(foreign=foreign,old=old),tempfile.TemporaryDirectory() as tmp:
                store,planner,task,server,prompt,session=self.prepared(Path(tmp))
                assignment=planner.build_assignment(task['task_id']); event=store.get_event(assignment['body']['envelope']['event_id'])
                original={'id':prompt['id'],'type':'user','time':{'created':event['created_ms']+2},'text':prompt['payload']['text'],'metadata':prompt['payload']['metadata']}
                control={'id':'bad-control','sessionID':'foreign' if foreign else task['assigned_session_id'],
                    'time':{'created':event['created_ms']-1 if old else event['created_ms']+4},'type':'synthetic','delivery':'steer','payload':{'text':'reminder'}}
                api=ConsumedPromptAPI(task['assigned_session_id'],session,[original],control)
                service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,agents={'implementation':'workflow-reviewer-implementation'})
                self.assertNotEqual('RESUMED',service.retry(task['task_id'],'cmd-bad','trusted')['status'])
                self.assertEqual(0,api.posts); self.assertTrue(store.task_is_dependency_paused(task['task_id']))

    def test_empty_history_uses_owned_control_and_receipt_retains_control_for_cleanup(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp))
            assignment=planner.build_assignment(task['task_id']); event=store.get_event(assignment['body']['envelope']['event_id'])
            original={'id':prompt['id'],'type':'user','time':{'created':event['created_ms']+1},
                'text':prompt['payload']['text'],'metadata':prompt['payload']['metadata']}
            api=EmptyCheckpointAPI(task['assigned_session_id'],session,[original])
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,now_ms=lambda:100000,agents={'implementation':'workflow-reviewer-implementation'})
            result=service.retry(task['task_id'],'cmd-empty','trusted')
            self.assertEqual('RESUMED',result['status'])
            self.assertEqual(1,api.control_posts); self.assertEqual(0,sum(c[0]=='PATCH' for c in api.calls))
            saved=store.list_review_retry_receipts(task['task_id'])
            self.assertEqual(1,len(saved)); control=saved[0]['controls_snapshot'][0]
            self.assertEqual('CONTROL_PROMPT',saved[0]['mode']); self.assertEqual(prompt['id'],control['body']['metadata']['original_prompt_id'])
            self.assertEqual(result,service.retry(task['task_id'],'cmd-empty','trusted'))
            self.assertEqual(1,api.control_posts)

    def test_empty_history_lost_reply_reconciles_saved_control_without_repost(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=self.prepared(Path(tmp))
            assignment=planner.build_assignment(task['task_id']); event=store.get_event(assignment['body']['envelope']['event_id'])
            original={'id':prompt['id'],'type':'user','time':{'created':event['created_ms']+1},'text':prompt['payload']['text'],'metadata':prompt['payload']['metadata']}
            api=EmptyCheckpointAPI(task['assigned_session_id'],session,[original]); api.lose_reply=True
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,now_ms=lambda:100000,agents={'implementation':'workflow-reviewer-implementation'})
            self.assertEqual('UNKNOWN',service.retry(task['task_id'],'cmd-empty-lost','trusted')['status'])
            self.assertEqual(1,api.control_posts); self.assertEqual(1,len(store.list_review_retry_receipts(task['task_id'])))
            api.lose_reply=False
            resolved=service.retry(task['task_id'],'cmd-empty-lost','trusted')
            self.assertEqual('RESUMED',resolved['status']); self.assertEqual(1,api.control_posts)


if __name__=='__main__': unittest.main()
