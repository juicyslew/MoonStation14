import tempfile
import threading
import time
import unittest
from pathlib import Path

import workflow_review_retry
import workflow_review_transport
import workflow_review_cancellation
from test_workflow_review_service import FakeOpenCode
class ReviewEffectRaceTests(unittest.TestCase):
    def test_stop_during_control_prompt_post_is_cancelled_after_ack(self):
        from test_workflow_review import ReviewPlanningTests
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); helper=ReviewPlanningTests()
            store,run_id,check_id=helper.fixture(root,roles=('implementation',))
            helper.run_check(root,store,run_id,check_id)
            planner=helper.coordinator(store,run_id)
            task=planner.plan_reviews(run_id)[0]
            sid=task['assigned_session_id']; role='implementation'; server='http://127.0.0.1:4096'
            entered=threading.Event(); release=threading.Event()
            class API(FakeOpenCode):
                def __init__(self): super().__init__({})
                def __call__(self,host,method,path,body=None):
                    result=super().__call__(host,method,path,body)
                    if method=='POST' and path.endswith('/prompt') and body.get('metadata',{}).get('kind')=='REVIEW_CONTINUATION':
                        entered.set()
                        if not release.wait(5):raise TimeoutError('barrier timeout')
                    return result
            api=API(); transport=workflow_review_transport.ReviewDispatchBackend(planner,server=server,agents={role:'workflow-reviewer-implementation'},request=api)
            self.assertEqual('APPLIED',transport.dispatch('review-ready',{'task_id':task['task_id']}))
            assignment=planner.build_assignment(task['task_id'])
            original=next(item for item in api.history[sid] if item.get('payload',{}).get('metadata',{}).get('task_id')==task['task_id'] and
                item.get('payload',{}).get('metadata',{}).get('review_event_id')==assignment['body']['envelope']['event_id'])
            metadata=original['payload']['metadata']
            self.assertEqual((assignment['body']['envelope']['event_id'],task['task_id'],assignment['body_sha256']),
                (metadata['review_event_id'],metadata['task_id'],metadata['body_sha256']))
            self.assertEqual(workflow_review_transport.workflow_store.canonical_json(assignment['body']),original['payload']['text'])
            api.inboxes[sid].clear(); api.active[sid]=False
            self.assertFalse(api.inboxes[sid]); self.assertFalse(api.active.get(sid))
            self.assertIn(original,api.history[sid])
            binding={k:task.get(k) for k in ('run_id','evaluation_generation','assignment_generation','input_digest','assigned_session_id')}
            store.pause_dependency_task(task_id=task['task_id'],dependency_key=server+':provider.auth',failure_id='401',binding=binding,now_ms=int(time.time()*1000))
            retry=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,now_ms=lambda:int(time.time()*1000),agents={role:'workflow-reviewer-implementation'})
            outcome={}
            def run_retry():
                try: outcome['receipt']=retry.retry(task['task_id'],'race-command','trusted')
                except BaseException as exc: outcome['error']=exc
            worker=threading.Thread(target=run_retry,daemon=True)
            canceller=workflow_review_cancellation.ReviewCancellation(store,planner,transport,server=server,request=api,sleep=lambda _:None)
            try:
                worker.start(); self.assertTrue(entered.wait(5),f'retry never reached control POST; outcome={outcome!r}')
                intent=store.get_review_retry(task_id=task['task_id'],caller='trusted',command_id='race-command')
                self.assertIsNotNone(intent); self.assertEqual(('INTENT','CONTROL_PROMPT'),(intent['status'],intent['mode']))
                self.assertTrue(any(r['state']=='HELD' for r in store.get_task_resources(task['task_id'])))
                current=store.get_run(run_id)
                store.transition_attempt(run_id,task['evaluation_generation'],expected_sequence=current['run']['sequence'],phase='STOPPING',closure='CANCELLED')
                event=store.find_task_cancel_event(task['task_id'])
                before=len(api.calls)
                self.assertEqual('PENDING',canceller.cancel(event['event_id'],event['payload']))
                self.assertFalse(any(c[0] in ('DELETE',) or (c[0]=='POST' and c[1].endswith('/interrupt?resume=false')) for c in api.calls[before:]))
                self.assertTrue(any(r['state']=='HELD' for r in store.get_task_resources(task['task_id'])))
            finally:
                release.set(); worker.join(5)
            self.assertFalse(worker.is_alive())
            self.assertNotIn('error',outcome,outcome.get('error'))
            self.assertEqual('STOPPING',outcome['receipt']['status']); self.assertEqual('APPLIED',outcome['receipt']['effect_status'])
            cancelled=canceller.cancel(event['event_id'],event['payload'])
            self.assertEqual('APPLIED',cancelled,{'incident':store.get_incident(run_id) if hasattr(store,'get_incident') else None,'phase':store.get_run(run_id)['run'].get('phase')})
            self.assertEqual('CANCELLED',store.get_task(task['task_id'])['state'])
            self.assertTrue(any(r['state']=='RELEASED' for r in store.get_task_resources(task['task_id'])))
            count=len(api.calls)
            self.assertEqual(outcome['receipt'],retry.retry(task['task_id'],'race-command','trusted'))
            self.assertEqual(count,len(api.calls))


if __name__=='__main__': unittest.main()
