"""Cancellation recovery when a continuation POST was admitted but its reply was lost."""
import tempfile
import time
import unittest
from pathlib import Path

import workflow_review_retry
import workflow_review_transport
import workflow_review_cancellation
from test_workflow_review_service import FakeOpenCode


class UncertainCancellationTests(unittest.TestCase):
    def _fixture(self, *, admit, mode='CONTROL_PROMPT'):
        from test_workflow_review import ReviewPlanningTests
        tmp=tempfile.TemporaryDirectory(); root=Path(tmp.name); helper=ReviewPlanningTests()
        store,run_id,check_id=helper.fixture(root,roles=('implementation',))
        helper.run_check(root,store,run_id,check_id); planner=helper.coordinator(store,run_id)
        task=planner.plan_reviews(run_id)[0]; sid=task['assigned_session_id']; role='implementation'; server='http://127.0.0.1:4096'
        class API(FakeOpenCode):
            def __init__(self): super().__init__({})
            def __call__(self,host,method,path,body=None):
                if method=='POST' and path.endswith('/prompt') and body.get('metadata',{}).get('kind')=='REVIEW_CONTINUATION':
                    if admit:
                        super().__call__(host,method,path,body)
                    else:
                        self.calls.append((method,path)); self.posts.append(body)
                    raise OSError('lost control prompt reply')
                if method=='POST' and path.endswith('/interrupt?resume=true'):
                    self.calls.append((method,path)); raise OSError('lost resume interrupt reply')
                return super().__call__(host,method,path,body)
        api=API(); transport=workflow_review_transport.ReviewDispatchBackend(planner,server=server,
            agents={role:'workflow-reviewer-implementation'},request=api)
        self.assertEqual('APPLIED',transport.dispatch('review-ready',{'task_id':task['task_id']}))
        assignment=planner.build_assignment(task['task_id'])
        original=next(item for item in api.history[sid] if item.get('payload',{}).get('metadata',{}).get('task_id')==task['task_id'] and
            item.get('payload',{}).get('metadata',{}).get('review_event_id')==assignment['body']['envelope']['event_id'])
        self.assertEqual(workflow_review_transport.workflow_store.canonical_json(assignment['body']),original['payload']['text'])
        api.inboxes[sid].clear(); api.active[sid]=False
        self.assertFalse(api.inboxes[sid]); self.assertFalse(api.active.get(sid))
        self.assertIn(original,api.history[sid])
        if mode=='INTERRUPT_RESUME':
            api.inboxes.setdefault(sid,[]).append({'id':'synthetic-task-wake','sessionID':sid,'type':'synthetic','delivery':'steer',
                'payload':{'text':'continue existing task'},'time':{'created':int(time.time()*1000)}})
        binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
        store.pause_dependency_task(task_id=task['task_id'],dependency_key=server+':provider.auth',failure_id='401',binding=binding,now_ms=int(time.time()*1000))
        retry=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,now_ms=lambda:int(time.time()*1000),agents={role:'workflow-reviewer-implementation'})
        result=retry.retry(task['task_id'],'uncertain-command','trusted')
        self.assertEqual('UNKNOWN',result['status'])
        intent=store.get_review_retry(task_id=task['task_id'],caller='trusted',command_id='uncertain-command')
        self.assertIsNotNone(intent); self.assertEqual('INTENT',intent['status'])
        self.assertEqual('INTERRUPT_RESUME' if mode=='INTERRUPT_RESUME' else 'CONTROL_PROMPT',intent['mode'])
        self.assertTrue(any(r['state']=='HELD' for r in store.get_task_resources(task['task_id'])))
        if mode=='INTERRUPT_RESUME':
            self.assertTrue(any(method=='POST' and path.endswith('/interrupt?resume=true') for method,path in api.calls))
        else:
            self.assertTrue(any(post.get('metadata',{}).get('kind')=='REVIEW_CONTINUATION' for post in api.posts))
        current=store.get_run(run_id)
        store.transition_attempt(run_id,task['evaluation_generation'],expected_sequence=current['run']['sequence'],phase='STOPPING',closure='CANCELLED')
        event=store.find_task_cancel_event(task['task_id'])
        cancel=workflow_review_cancellation.ReviewCancellation(store,planner,transport,server=server,request=api,sleep=lambda _:None)
        return tmp,store,task,retry,cancel,event,api,result

    def test_admitted_control_is_observed_and_cancelled(self):
        tmp,store,task,retry,cancel,event,api,result=self._fixture(admit=True)
        try:
            self.assertEqual('APPLIED',cancel.cancel(event['event_id'],event['payload']))
            receipt=store.get_review_retry(task_id=task['task_id'],caller='trusted',command_id='uncertain-command')
            self.assertEqual('APPLIED',receipt['effect_status']); self.assertEqual('STOPPING',receipt['status'])
            self.assertEqual('CANCELLED',store.get_task(task['task_id'])['state'])
            self.assertTrue(any(r['state']=='RELEASED' for r in store.get_task_resources(task['task_id'])))
            self.assertEqual(2,len(api.posts),'only original prompt and uncertain retry prompt may be posted')
            count=len(api.calls); self.assertEqual(receipt,retry.retry(task['task_id'],'uncertain-command','trusted')); self.assertEqual(count,len(api.calls))
        finally: tmp.cleanup()

    def test_absent_control_keeps_ambiguous_intent_unknown(self):
        tmp,store,task,retry,cancel,event,api,result=self._fixture(admit=False)
        try:
            intent=store.get_review_retry(task_id=task['task_id'],caller='trusted',command_id='uncertain-command')
            self.assertIsNotNone(intent); self.assertEqual(('INTENT','CONTROL_PROMPT'),(intent['status'],intent['mode']))
            attempted=api.posts[-1]
            self.assertEqual('REVIEW_CONTINUATION',attempted.get('metadata',{}).get('kind'))
            history=api.history[task['assigned_session_id']]
            original=next(item for item in history if item.get('payload',{}).get('metadata',{}).get('task_id')==task['task_id'])
            self.assertNotEqual(original['id'],attempted['id'])
            self.assertIn(original,history)
            self.assertFalse(any(item.get('id')==attempted['id'] for item in history))
            before=len(api.calls); self.assertEqual('UNKNOWN',cancel.cancel(event['event_id'],event['payload']))
            self.assertFalse(any(m in ('DELETE','POST') and (m=='DELETE' or p.endswith('/interrupt?resume=false')) for m,p in api.calls[before:]))
            self.assertFalse(any(r['state']=='RELEASED' for r in store.get_task_resources(task['task_id'])))
        finally: tmp.cleanup()

    def test_unproven_interrupt_resume_intent_keeps_resource_held(self):
        tmp,store,task,retry,cancel,event,api,result=self._fixture(admit=False,mode='INTERRUPT_RESUME')
        try:
            self.assertEqual('INTERRUPT_RESUME',store.get_review_retry(task_id=task['task_id'],caller='trusted',command_id='uncertain-command')['mode'])
            before=len(api.calls)
            self.assertEqual('UNKNOWN',cancel.cancel(event['event_id'],event['payload']))
            followup=api.calls[before:]
            self.assertFalse(any(method=='DELETE' or (method=='POST' and path.endswith('/interrupt?resume=false'))
                                 for method,path in followup))
            self.assertFalse(any(r['state']=='RELEASED' for r in store.get_task_resources(task['task_id'])))
        finally: tmp.cleanup()


if __name__=='__main__': unittest.main()
