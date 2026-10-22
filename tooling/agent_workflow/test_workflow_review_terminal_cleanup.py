"""Prove terminal cleanup retries through the production review cleanup owner."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_review
import workflow_review_pipeline
import workflow_review_results
import workflow_review_transport
import workflow_review_cancellation
import workflow_native_execution
import test_workflow_review as review_fixtures
import test_workflow_review_cleanup_service as cleanup_fixtures
from test_workflow_native_execution import admit_native_fixture


class TerminalReviewCleanupTests(unittest.TestCase):
    def test_terminal_error_cleanup_releases_global_lane_for_fresh_later_assignment(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            api_root = root / 'review-api'
            api_root.mkdir()
            store, run_id, check_id = review_fixtures.ReviewPlanningTests().fixture(root)
            helper = review_fixtures.ReviewPlanningTests()
            helper.run_check(root, store, run_id, check_id)
            role, agent = 'implementation', 'workflow-reviewer-implementation'
            class RetryableCleanupAPI(cleanup_fixtures.ReviewCleanupAPI):
                refuse_interrupt = True

                def __call__(self, server, method, path, body=None):
                    if self.refuse_interrupt and method == 'POST' and path.endswith('/interrupt?resume=false'):
                        return 503, {}
                    return super().__call__(server, method, path, body)

            api = RetryableCleanupAPI()
            server = 'http://127.0.0.1:4096'
            planner = workflow_review.ReviewCoordinator(store, reviewers={role:{
                'role':'reviewer','worktree':str(root/'repo')}}, artifact_dir=root/'artifacts')
            results = workflow_review_results.ReviewResults(store, planner)
            transport = workflow_review_transport.ReviewDispatchBackend(
                planner, server=server, agents={role:agent}, request=api)
            pipeline = workflow_review_pipeline.ReviewPipeline(store, planner, results, transport, object())
            pipeline.review_cancellation = workflow_review_cancellation.ReviewCancellation(
                store, planner, transport, server=server, request=api, sleep=lambda _:None)
            task = planner.plan_reviews(run_id)[0]
            assignment = planner.build_assignment(task['task_id'])
            self.assertEqual('APPLIED', transport.dispatch(assignment['body']['envelope']['event_id'],
                {'task_id':task['task_id']}))
            task = store.get_task(task['task_id'])
            sid=task['assigned_session_id']
            claim = {'contract_version':1,'command_id':'terminal-cleanup-claim',
                **{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                'session_id':sid}
            self.assertTrue(results.claim(claim, session_id=sid)['accepted'])
            receipt = next(ref for ref in assignment['body']['envelope']['artifact_refs']
                if store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT')
            error = {'contract_version':1,**{k:task[k] for k in ('task_id','run_id','evaluation_generation','assignment_generation','input_digest')},
                'error_code':'REVIEW_SERVICE_FAULT','message':'failed','artifact_refs':[receipt]}
            saved = results.error(error, session_id=sid)
            pipeline.completed(store.get_event(saved['result_id'])['payload'])
            terminal = store.get_run(run_id)
            self.assertEqual(('DECIDED','ERROR'), (terminal['attempt']['phase'],terminal['attempt']['result']))
            resource = next(r for r in store.get_task_resources(task['task_id']) if r['resource_key'].startswith('review-session:'))
            self.assertEqual('UNKNOWN', resource['state'])
            sequence, result_digest = terminal['run']['sequence'], store.get_terminal_result(run_id,1)

            # An unresolved terminal review claim must continue to block a
            # different caller/worktree before any native subprocess launches.
            root2 = root / 'healthy'
            case2, _, accepted2, check2 = admit_native_fixture(root2, caller='healthy', key='healthy',
                shared_store=store, review_roles=(role,), delay=.01)
            native2=workflow_native_execution.NativeExecutionBackend(store,root2/'artifacts',root2/'native-lock')
            try:
                with patch.object(workflow_native_execution.subprocess,'Popen') as popen:
                    self.assertEqual('PENDING',native2.dispatch('second-run-blocked',{'task_id':check2}))
                popen.assert_not_called()
            finally:
                native2.close()

            api.refuse_interrupt = False
            pipeline.reconcile_pending()
            resource = next(r for r in store.get_task_resources(task['task_id']) if r['resource_key'].startswith('review-session:'))
            self.assertEqual('RELEASED', resource['state'])
            self.assertTrue(resource.get('cleanup_proof'))
            self.assertEqual(sequence, store.get_run(run_id)['run']['sequence'])
            self.assertEqual(result_digest, store.get_terminal_result(run_id,1))

            helper.run_check(root2, store, accepted2['run_id'], check2)
            first_history=list(api.history)
            api2=cleanup_fixtures.ReviewCleanupAPI()
            planner2 = workflow_review.ReviewCoordinator(store, reviewers={role:{
                'role':'reviewer','worktree':str(case2.repo)}}, artifact_dir=root2/'artifacts')
            task2 = planner2.plan_reviews(accepted2['run_id'])[0]
            assignment2 = planner2.build_assignment(task2['task_id'])
            transport2 = workflow_review_transport.ReviewDispatchBackend(
                planner2, server=server, agents={role:agent}, request=api2)
            self.assertEqual('APPLIED', transport2.dispatch(assignment2['body']['envelope']['event_id'],
                {'task_id':task2['task_id']}))
            self.assertEqual('HELD', next(r for r in store.get_task_resources(task2['task_id'])
                if r['resource_key'].startswith('review-session:'))['state'])
            self.assertNotEqual(sid,task2['assigned_session_id'])
            self.assertEqual(first_history,api.history)
            self.assertEqual(1,len(api2.posts))
            self.assertEqual('1', (root/'repo'/'counter.txt').read_text())
            self.assertEqual('1', (case2.repo/'counter.txt').read_text())


if __name__ == '__main__':
    unittest.main()
