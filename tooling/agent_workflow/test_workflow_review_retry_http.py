import tempfile
import threading
import time
import unittest
from pathlib import Path

import workflow_coordinator
from test_workflow_native_execution import admit_native_fixture
from test_workflow_review_service import TestIdentity
from test_workflow_review_retry_service import RetryAPI
import test_workflow_review_retry as retry_fixture


class BlockingRetry:
    def __init__(self):
        self.entered = threading.Event()
        self.release = threading.Event()
        self.calls = 0

    def retry(self, *args):
        self.calls += 1
        self.entered.set()
        if not self.release.wait(10):
            raise TimeoutError('test retry barrier was not released')
        return {'status': 'PENDING'}


class ReviewRetryHttpTests(unittest.TestCase):
    def test_stopping_receipt_replays_before_stale_sequence_fence(self):
        from starlette.testclient import TestClient

        with tempfile.TemporaryDirectory() as tmp:
            store, planner, task, server, _, _ = retry_fixture.ReviewRetryTests().prepared(Path(tmp))
            binding = {key: task.get(key) for key in ('run_id', 'evaluation_generation', 'assignment_generation', 'input_digest', 'assigned_session_id')}
            resource = store.get_task_resources(task['task_id'])[0]
            proof = resource['identity_proof']
            command_id = 'stored-stop-receipt'
            caller = 'owner'
            store.begin_review_retry(task_id=task['task_id'], caller=caller, command_id=command_id,
                                     binding=binding, proof=proof, mode='PATCH', controls_snapshot=None)
            store.record_review_retry_effect(task_id=task['task_id'], caller=caller, command_id=command_id)
            receipt = {'status': 'STOPPING', 'task_id': task['task_id'], 'command_id': command_id,
                       'assignment_generation': task['assignment_generation'], 'input_digest': task['input_digest'],
                       'effect_status': 'APPLIED'}
            store.complete_review_retry(task_id=task['task_id'], caller=caller, command_id=command_id, response=receipt)
            retry = BlockingRetry()
            run = store.get_run(task['run_id'])['run']
            co = workflow_coordinator.ExplicitCoordinator(store, bindings={'author': workflow_coordinator.CoordinatorBinding('author', {})},
                artifact_dir=Path(tmp)/'artifacts', identity_verifier=lambda *args: True,
                owner_bindings={'owner-token': workflow_coordinator.OwnerBinding(caller, run['author']['worktree']),
                                'foreign-token': workflow_coordinator.OwnerBinding('foreign-owner', str(Path(tmp)/'foreign'))})
            co.review_retry = retry
            try:
                with TestClient(co.app()) as client:
                    body = {'task_id': task['task_id'], 'command_id': command_id, 'expected_sequence': -1}
                    response = client.post('/v1/recovery/retry-review', headers={'Authorization': 'Bearer owner-token'}, json=body)
                    self.assertEqual(200, response.status_code, response.text)
                    self.assertEqual(store.get_review_retry(task_id=task['task_id'], caller=caller, command_id=command_id), response.json())
                    self.assertEqual(0, retry.calls)
                    foreign = client.post('/v1/recovery/retry-review', headers={'Authorization': 'Bearer foreign-token'}, json=body)
                    self.assertEqual(403, foreign.status_code, foreign.text)
                    self.assertEqual(0, retry.calls)
            finally:
                co.stop()

    def test_blocking_retry_does_not_block_health_or_cancel(self):
        from starlette.testclient import TestClient
        import workflow_native_execution

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            case, store, accepted, _ = admit_native_fixture(root, caller='http-author', key='http-case')
            native = workflow_native_execution.NativeExecutionBackend(store, root/'artifacts', root/'native-lock')
            co = workflow_coordinator.ExplicitCoordinator(store,
                bindings={'author': workflow_coordinator.CoordinatorBinding('author', case.context)},
                artifact_dir=root/'artifacts', handlers={'TASK_READY': native}, identity_verifier=TestIdentity(),
                owner_bindings={'owner': workflow_coordinator.OwnerBinding('owner', str(case.repo))}, reconcile_interval=.03)
            import workflow_run_control
            co.run_control = workflow_run_control.ServiceRunControl(store, co.notify_committed)
            # Create an owner-addressable review task through the normal review pipeline.
            api = RetryAPI({})
            workflow_coordinator.configure_review_pipeline(co,
                {'implementation': {'role': 'reviewer', 'worktree': str(case.repo)}},
                {'implementation': 'workflow-reviewer-implementation'},
                {'review-token': workflow_coordinator.ReviewerBinding('reviewer', str(case.repo), 'implementation')},
                transport_request=api)
            blocked = BlockingRetry()
            co.review_retry = blocked
            try:
                with TestClient(co.app()) as client:
                    deadline = time.monotonic() + 8
                    while time.monotonic() < deadline:
                        tasks = [t for t in store.list_tasks(accepted['run_id'], 1) if t['kind'] == 'REVIEW']
                        if tasks and api.posts:
                            break
                        time.sleep(.02)
                    self.assertTrue(tasks, 'review task was not created')
                    task = tasks[0]
                    command = {'task_id': task['task_id'], 'command_id': 'http-block',
                               'expected_sequence': store.get_run(accepted['run_id'])['run']['sequence']}
                    outcome = {}
                    request = threading.Thread(target=lambda: outcome.setdefault('retry', client.post(
                        '/v1/recovery/retry-review', headers={'Authorization': 'Bearer owner'}, json=command)))
                    request.start()
                    self.assertTrue(blocked.entered.wait(3), 'retry did not enter worker thread')
                    self.assertEqual(200, client.get('/v1/health', headers={'Authorization': 'Bearer author'}).status_code)
                    cancel = client.post('/v1/cancel', headers={'Authorization': 'Bearer owner'},
                                         json={'run_id': accepted['run_id'], 'evaluation_generation': task['evaluation_generation'],
                                               'expected_sequence': command['expected_sequence'], 'reason': 'CANCELLED', 'command_id': 'http-cancel'})
                    self.assertEqual(200, cancel.status_code, cancel.text)
                    self.assertIn(cancel.json()['receipt']['state'], ('STOPPING', 'UNRESOLVED', 'CONFIRMED'))
                    self.assertTrue(cancel.json()['receipt']['transition'])
                    self.assertTrue(request.is_alive(), 'retry completed before barrier release')
                    blocked.release.set()
                    request.join(3)
                    self.assertFalse(request.is_alive())
            finally:
                blocked.release.set()
                co.stop()


if __name__ == '__main__':
    unittest.main()
