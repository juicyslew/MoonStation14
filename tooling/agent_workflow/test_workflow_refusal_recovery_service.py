"""Coordinator reconstruction must retain uncertain review cleanup until proven safe."""
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

import workflow_coordinator
import workflow_board
import test_workflow_review as review_fixtures
import test_workflow_review_cleanup_service as cleanup_fixtures


class RefusalRecoveryServiceTests(unittest.TestCase):
    def test_reconstructed_coordinator_keeps_refused_cleanup_unknown_and_visible(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            helper = review_fixtures.ReviewPlanningTests()
            store, run_id, check_id = helper.fixture(root)
            helper.run_check(root, store, run_id, check_id)
            role, agent = 'implementation', 'workflow-reviewer-implementation'

            class RefusingAPI(cleanup_fixtures.ReviewCleanupAPI):
                refuse_interrupt = True

                def __call__(self, server, method, path, body=None):
                    if self.refuse_interrupt and method == 'POST' and path.endswith('/interrupt?resume=false'):
                        return 503, {}
                    return super().__call__(server, method, path, body)

            api = RefusingAPI()
            server = 'http://127.0.0.1:4096'

            def build_coordinator():
                coordinator = SimpleNamespace(
                    handlers={'TASK_READY': object()}, identity_verifier=SimpleNamespace(server=server),
                    transport_health=None, artifact_dir=root / 'artifacts', store=store,
                    notify_committed=lambda: None, review_bindings={})
                workflow_coordinator.configure_review_pipeline(
                    coordinator, {role: {'role': 'reviewer',
                                         'worktree': str(root / 'repo')}},
                    {role: agent}, {role: {}}, transport_request=api)
                return coordinator

            first = build_coordinator()
            planner = first.review_pipeline.planner
            task = planner.plan_reviews(run_id)[0]
            assignment = planner.build_assignment(task['task_id'])
            self.assertEqual('APPLIED', first.review_pipeline.transport.dispatch(
                assignment['body']['envelope']['event_id'], {'task_id': task['task_id']}))
            task = store.get_task(task['task_id'])
            sid = task['assigned_session_id']
            claim = {'contract_version': 1, 'command_id': 'refusal-recovery-claim',
                     **{key: task[key] for key in ('task_id', 'run_id', 'evaluation_generation',
                                                   'assignment_generation', 'input_digest')},
                     'session_id': sid}
            self.assertTrue(first.review_results.claim(claim, session_id=sid)['accepted'])
            receipt = next(ref for ref in assignment['body']['envelope']['artifact_refs']
                           if store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT')
            error = {'contract_version': 1,
                     **{key: task[key] for key in ('task_id', 'run_id', 'evaluation_generation',
                                                   'assignment_generation', 'input_digest')},
                     'error_code': 'REVIEW_SERVICE_FAULT', 'message': 'failed', 'artifact_refs': [receipt]}
            saved = first.review_results.error(error, session_id=sid)
            first.review_pipeline.completed(store.get_event(saved['result_id'])['payload'])
            terminal = store.get_run(run_id)
            self.assertEqual(('DECIDED', 'ERROR'), (terminal['attempt']['phase'], terminal['attempt']['result']))
            resource = next(row for row in store.get_task_resources(task['task_id'])
                            if row['resource_key'].startswith('review-session:'))
            self.assertEqual('UNKNOWN', resource['state'])
            self.assertFalse(any(row['state'] == 'RELEASED' for row in store.get_task_resources(task['task_id'])))
            # Rebuild coordinator-owned services over the same durable store.
            # Refusal remains in effect, so startup reconciliation cannot claim cessation.
            recovered = build_coordinator()
            recovered.review_pipeline.reconcile_pending()
            resource = next(row for row in store.get_task_resources(task['task_id'])
                            if row['resource_key'].startswith('review-session:'))
            self.assertEqual('UNKNOWN', resource['state'])
            self.assertFalse(resource.get('cleanup_proof'))
            snapshot = store.readonly_snapshot(store.path, worktree=str(root / 'repo'))
            board = workflow_board.project(snapshot, worktree=str(root / 'repo'))
            self.assertTrue(any(fault.get('state') == 'OPEN' for fault in board['faults']))


if __name__ == '__main__':
    unittest.main()
