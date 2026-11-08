import tempfile
import unittest
from pathlib import Path

import workflow_review_retry
import workflow_store
import test_workflow_review_retry as _retry_fixture
EmptyCheckpointAPI = _retry_fixture.EmptyCheckpointAPI


class CursorAPI(EmptyCheckpointAPI):
    def __call__(self, server, method, path, body=None):
        if method == 'GET' and path.endswith('/message?limit=50&order=desc'):
            self.calls.append((method, path, body))
            records=sorted(self.history,key=lambda r:r.get('time',{}).get('created',-1),reverse=True)
            return 200, {'data': records, 'cursor': {}}
        return super().__call__(server, method, path, body)


class ReviewEffectObserverTests(unittest.TestCase):
    def test_second_owner_recovery_accepts_retained_first_control(self):
        with tempfile.TemporaryDirectory() as tmp:
            store,planner,task,server,prompt,session=_retry_fixture.ReviewRetryTests().prepared(Path(tmp))
            assignment=planner.build_assignment(task['task_id']); event=store.get_event(assignment['body']['envelope']['event_id'])
            original={'id':prompt['id'],'type':'user','time':{'created':event['created_ms']+1},
                'text':prompt['payload']['text'],'metadata':prompt['payload']['metadata']}
            api=CursorAPI(task['assigned_session_id'],session,[original]); api.lose_reply=True
            service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,
                now_ms=lambda:__import__('time').time_ns()//1_000_000,agents={'implementation':'workflow-reviewer-implementation'})
            generation=(task['assignment_generation'],task['evaluation_generation'],task['input_digest'],task['assigned_session_id'])
            self.assertEqual('UNKNOWN',service.retry(task['task_id'],'first-control','trusted')['status'])
            # Retain the exact first control as a completed prior receipt, without changing task state.
            store.complete_review_retry(task_id=task['task_id'],caller='trusted',command_id='first-control',
                response={'status':'STOPPING','task_id':task['task_id'],'command_id':'first-control',
                    'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],'effect_status':'APPLIED'})
            api.lose_reply=False
            api.inbox.clear()  # First acknowledged control was consumed; its canonical history remains.
            result=service.retry(task['task_id'],'second-control','trusted')
            self.assertEqual('RESUMED',result['status'],result)
            self.assertEqual(2,api.control_posts)
            after=store.get_task(task['task_id'])
            self.assertEqual(generation,(after['assignment_generation'],after['evaluation_generation'],after['input_digest'],after['assigned_session_id']))

    def test_lost_control_ack_observes_canceled_attempt_read_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            store, planner, task, server, prompt, session = _retry_fixture.ReviewRetryTests().prepared(Path(tmp))
            assignment = planner.build_assignment(task['task_id'])
            event = store.get_event(assignment['body']['envelope']['event_id'])
            original = {'id': prompt['id'], 'type': 'user', 'time': {'created': event['created_ms'] + 1},
                        'text': prompt['payload']['text'], 'metadata': prompt['payload']['metadata']}
            api = CursorAPI(task['assigned_session_id'], session, [original])
            api.lose_reply = True
            service = workflow_review_retry.ReviewRetryAdapter(planner, server=server, request=api,
                now_ms=lambda: __import__('time').time_ns()//1_000_000, agents={'implementation': 'workflow-reviewer-implementation'})
            self.assertEqual('UNKNOWN', service.retry(task['task_id'], 'observe-control', 'trusted')['status'])
            intent = store.get_review_retry(task_id=task['task_id'], caller='trusted', command_id='observe-control')
            # A canceled/stopping run may still require observation, but never recovery.
            current = store.get_run(task['run_id'])
            store.transition_attempt(task['run_id'], task['evaluation_generation'],
                expected_sequence=current['run']['sequence'], phase='STOPPING', closure='CANCELLED')
            before = store.get_task(task['task_id'])
            calls_before = len(api.calls)
            self.assertEqual('APPLIED', service.observe_effect(task['task_id'], intent))
            self.assertEqual(before, store.get_task(task['task_id']))
            self.assertFalse(any(c[0] in ('POST', 'PATCH', 'DELETE') for c in api.calls[calls_before:]))

            # The same identity proof remains eligible after conservative cleanup marking.
            resource=store.get_task_resources(task['task_id'])[0]
            store.mark_resource_unknown(resource['claim_id'],identity_proof=resource['identity_proof'])
            self.assertEqual('APPLIED',service.observe_effect(task['task_id'],intent))

    def test_old_users_allowed_but_modified_owned_user_is_unknown(self):
        for modified in (False, True):
            with self.subTest(modified=modified), tempfile.TemporaryDirectory() as tmp:
                store, planner, task, server, prompt, session = _retry_fixture.ReviewRetryTests().prepared(Path(tmp))
                assignment=planner.build_assignment(task['task_id']); event=store.get_event(assignment['body']['envelope']['event_id'])
                original={'id':prompt['id'],'type':'user','time':{'created':event['created_ms']+1},
                    'text':prompt['payload']['text'],'metadata':prompt['payload']['metadata']}
                api=CursorAPI(task['assigned_session_id'],session,[original])
                api.lose_reply=True
                service=workflow_review_retry.ReviewRetryAdapter(planner,server=server,request=api,
                    now_ms=lambda: __import__('time').time_ns()//1_000_000,agents={'implementation':'workflow-reviewer-implementation'})
                service.retry(task['task_id'],'observer-'+str(modified),'trusted')
                intent=store.get_review_retry(task_id=task['task_id'],caller='trusted',command_id='observer-'+str(modified))
                old={'id':'prior-task-user','type':'user','time':{'created':event['created_ms']-1},'text':'older prompt','metadata':{}}
                api.history.append(old)
                if modified:
                    api.history[0]['text']='tampered original'
                expected='UNKNOWN' if modified else 'APPLIED'
                self.assertEqual(expected,service.observe_effect(task['task_id'],intent))

    def test_absent_post_intent_effect_is_unknown(self):
        with tempfile.TemporaryDirectory() as tmp:
            store, planner, task, server, prompt, session = _retry_fixture.ReviewRetryTests().prepared(Path(tmp))
            api = CursorAPI(task['assigned_session_id'], session, [])
            service = workflow_review_retry.ReviewRetryAdapter(planner, server=server, request=api,
                agents={'implementation': 'workflow-reviewer-implementation'})
            self.assertEqual('UNKNOWN', service.observe_effect(task['task_id'], {}))


if __name__ == '__main__':
    unittest.main()
