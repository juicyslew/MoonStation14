import hashlib
import tempfile
import unittest
from pathlib import Path

import workflow_review_control
import workflow_review_dependency
import workflow_store
import test_workflow_review_dependency as dependency_fixtures


class ReviewRepeatOutageTests(unittest.TestCase):
    def test_resumed_continuation_survives_repeat_auth_outage(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            store, planner, task, server, envelope = dependency_fixtures.ReviewDependencyTests().prepared(root)
            dispatch_ms = store.get_event(envelope['event_id'])['created_ms']
            assignment = planner.build_assignment(task['task_id'])
            original = dependency_fixtures.ReviewDependencyTests().owned_prompt(store, planner, task, envelope)
            path = '/api/session/' + task['assigned_session_id'] + '/message?limit=50&order=desc'
            error = {'id': 'auth-first', 'time': {'created': dispatch_ms + 1},
                     'error': {'type': 'provider.auth', 'status': 401, 'message': 'invalid API key'}}
            fake = dependency_fixtures.ReviewDependencyTests().history(path, [error, original])
            monitor = workflow_review_dependency.ReviewDependencyMonitor(
                planner, server=server, request=fake, now_ms=lambda: dispatch_ms + 10)
            self.assertEqual('PAUSED', monitor.poll(task['task_id']))

            resource = next(r for r in store.get_task_resources(task['task_id']) if r['state'] == 'HELD')
            proof = resource['identity_proof']
            binding = {k: task.get(k) for k in ('run_id', 'evaluation_generation', 'assignment_generation',
                                                  'input_digest', 'assigned_session_id')}
            command = 'repeat-outage-control'
            body = workflow_review_control.continuation_body(task, assignment, command)
            control = {'id': body['id'], 'session_id': task['assigned_session_id'],
                       'body_sha256': hashlib.sha256(workflow_store.canonical_json(body).encode()).hexdigest(),
                       'text': body['text'], 'metadata': body['metadata'], 'body': body}
            store.begin_review_retry(task_id=task['task_id'], caller='trusted', command_id=command,
                                     binding=binding, proof=proof, mode='CONTROL_PROMPT', controls_snapshot=[control])
            store.complete_review_retry(task_id=task['task_id'], caller='trusted', command_id=command,
                                        response={'status': 'RESUMED', 'task_id': task['task_id'],
                                                  'command_id': command,
                                                  'assignment_generation': task['assignment_generation'],
                                                  'input_digest': task['input_digest']})
            self.assertEqual('RESUMED', store.resume_dependency_task(
                task_id=task['task_id'], dependency_key=server + ':provider.auth', binding=binding,
                now_ms=dispatch_ms + 20)['disposition'])
            receipts = store.list_review_retry_receipts(task['task_id'])
            continuation = workflow_review_control.continuation_bodies(receipts, task, assignment, server)[0]
            continued = {'id': continuation['id'], 'type': 'user', 'time': {'created': dispatch_ms + 21},
                         'text': continuation['text'], 'metadata': continuation['metadata']}
            repeat_error = {'id': 'auth-second', 'time': {'created': dispatch_ms + 30},
                            'error': {'type': 'provider.auth', 'status': 401, 'message': 'still unavailable'}}
            fake.response = {'data': [repeat_error, dict(continued, text='tampered') , original], 'cursor': {}}
            monitor.now_ms = lambda: dispatch_ms + 40
            self.assertEqual('UNKNOWN', monitor.poll(task['task_id']))
            self.assertFalse(store.task_is_dependency_paused(task['task_id']))

            fake.response = {'data': [repeat_error, continued, original], 'cursor': {}}
            self.assertEqual('PAUSED', monitor.poll(task['task_id']))
            current = store.get_task(task['task_id'])
            self.assertEqual(task['task_id'], current['task_id'])
            self.assertEqual(task['assigned_session_id'], current['assigned_session_id'])
            self.assertEqual(task['assignment_generation'], current['assignment_generation'])
            pause = store.get_task_dependency_pause(task['task_id'])
            self.assertEqual(server + ':provider.auth', pause['dependency_key'])
            self.assertEqual(dispatch_ms + 40, pause['paused_since_ms'])
            self.assertEqual(10, store.task_paused_duration_ms(task['task_id'], dispatch_ms + 40))
            self.assertIsNone(current['result'])

    def test_continuation_receipt_rejects_changed_input_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            store, planner, task, server, envelope = dependency_fixtures.ReviewDependencyTests().prepared(Path(tmp))
            dispatch_ms = store.get_event(envelope['event_id'])['created_ms']
            assignment = planner.build_assignment(task['task_id'])
            original = dependency_fixtures.ReviewDependencyTests().owned_prompt(store, planner, task, envelope)
            path = '/api/session/' + task['assigned_session_id'] + '/message?limit=50&order=desc'
            fake = dependency_fixtures.ReviewDependencyTests().history(path, [
                {'id': 'auth-for-proof', 'time': {'created': dispatch_ms + 1},
                 'error': {'type': 'provider.auth', 'status': 401, 'message': 'unavailable'}}, original])
            monitor = workflow_review_dependency.ReviewDependencyMonitor(
                planner, server=server, request=fake, now_ms=lambda: dispatch_ms + 10)
            self.assertEqual('PAUSED', monitor.poll(task['task_id']))
            resource = next(r for r in store.get_task_resources(task['task_id']) if r['state'] == 'HELD')
            binding = {k: task.get(k) for k in ('run_id', 'evaluation_generation', 'assignment_generation',
                                                  'input_digest', 'assigned_session_id')}
            command = 'changed-digest-control'
            body = workflow_review_control.continuation_body(task, assignment, command)
            control = {'id': body['id'], 'session_id': task['assigned_session_id'],
                       'body_sha256': hashlib.sha256(workflow_store.canonical_json(body).encode()).hexdigest(),
                       'text': body['text'], 'metadata': body['metadata'], 'body': body}
            store.begin_review_retry(task_id=task['task_id'], caller='trusted', command_id=command,
                                     binding=binding, proof=resource['identity_proof'], mode='CONTROL_PROMPT',
                                     controls_snapshot=[control])
            store.complete_review_retry(task_id=task['task_id'], caller='trusted', command_id=command,
                                        response={'status': 'RESUMED', 'task_id': task['task_id'],
                                                  'command_id': command,
                                                  'assignment_generation': task['assignment_generation'],
                                                  'input_digest': task['input_digest']})
            changed = dict(task, input_digest='changed-input')
            with self.assertRaises(ValueError):
                workflow_review_control.continuation_bodies(
                    store.list_review_retry_receipts(task['task_id']), changed, assignment, server)


if __name__ == '__main__':
    unittest.main()
