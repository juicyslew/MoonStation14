"""A diagnostic request remains diagnostic through real native check execution."""
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

import workflow_native_execution
import workflow_preflight
import workflow_store
import workflow_submission
import workflow_review_pipeline
import workflow_review
import workflow_review_results
from diagnostic_test_support import make_diagnostic_case
from workflow_check_packs import resolve_diagnostic_plan


class DiagnosticEndToEndTests(unittest.TestCase):
    def test_submitted_diagnostic_runs_real_check_and_freezes_declared_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            capture_script = (
                "import json,uuid\nfrom pathlib import Path\n"
                "p=Path('build/diag-captures')/str(uuid.uuid4());p.mkdir(parents=True)\n"
                "(p/'summary.json').write_text(json.dumps({'message':'capture completed'}))\n"
                "(p/'client.jfr').write_bytes(b'diagnostic-capture')\n"
            )
            check = {'argv':[sys.executable, '-c', capture_script],
                'rationale':'Capture bounded diagnostic evidence.', 'covers':['c1'], 'kind':'command',
                'artifact_globs':['build/diag-captures/*/summary.json','build/diag-captures/*/client.jfr']}
            case = make_diagnostic_case(root, caller='author', key='diag-e2e', checks=[check])
            (case.repo/'.gitignore').write_text('build/\n', encoding='utf-8')
            # Hash only after all fixture/source edits; resolve the same public plan used in preflight.
            case.request['input_digest'] = workflow_store.digest(workflow_preflight.capture_source(case.repo))
            case.request['plan_digest'] = workflow_store.digest(resolve_diagnostic_plan(
                case.request, repo=case.repo, policy=case.policy))
            bundle = workflow_preflight.preflight(case.request, caller=case.caller, context=case.context)
            self.assertEqual(bundle['review_inputs'], [])
            store = workflow_store.WorkflowStore(root/'workflow.sqlite')
            accepted = workflow_submission.submit_submission(store, caller=case.caller,
                idempotency_key=case.key, request=case.request, context=case.context,
                artifact_dir=root/'artifacts')
            run_id = accepted['run_id']
            task = next(task for task in store.list_tasks(run_id, 1) if task['kind']=='CHECK')
            task_id = task['task_id']
            backend = workflow_native_execution.NativeExecutionBackend(store, root/'native-artifacts', root/'lock')
            try:
                backend.dispatch('diag-e2e-'+task_id, {'task_id':task_id})
                backend.workers[task_id].wait(timeout=30)
            finally:
                backend.close()
            saved = store.get_task(task_id)
            self.assertEqual(saved['state'], 'COMPLETED')
            self.assertEqual(saved['result']['decision'], 'PASS')
            refs = saved['result']['artifact_refs']
            self.assertTrue(refs)
            artifacts = [store.get_artifact(ref)['artifact'] for ref in refs]
            self.assertTrue(any(a.get('type') == 'PROVENANCE' and a.get('immutable') is True for a in artifacts))
            receipt_artifact = next(a for a in artifacts if a['type'] == 'CHECK_RECEIPT')
            receipt_bytes = Path(receipt_artifact['path']).read_bytes()
            self.assertEqual(hashlib.sha256(receipt_bytes).hexdigest(), receipt_artifact['sha256'])
            receipt = json.loads(receipt_bytes)
            record = receipt['checks'][0]
            found = {Path(row['source_path']).name: row for row in record['evidence']['artifacts']}
            self.assertEqual(set(found), {'summary.json', 'client.jfr'})
            for row in found.values():
                frozen = Path(row['path']).read_bytes()
                self.assertEqual(row['sha256'], hashlib.sha256(frozen).hexdigest())
                self.assertEqual(len(frozen), row['size'])
                self.assertEqual(row['artifact_ref'], next(ref for ref in refs if ref == row['artifact_ref']))
                self.assertTrue(store.get_artifact(row['artifact_ref'])['artifact']['immutable'])
            run = store.get_run(run_id)
            self.assertEqual(run['run']['run_kind'], 'DIAGNOSTIC')
            planner = workflow_review.ReviewCoordinator(store, reviewers={}, artifact_dir=root/'reviews')
            pipeline = workflow_review_pipeline.ReviewPipeline(store, planner,
                workflow_review_results.ReviewResults(store, planner), object(), object())
            terminal_payloads = []
            pipeline.notify_committed = terminal_payloads.append
            terminal = pipeline._evaluate(run_id)
            self.assertEqual(terminal['decision'], 'PASS')
            self.assertEqual(terminal['run_kind'], 'DIAGNOSTIC')
            self.assertEqual(terminal['next_action'], 'REVIEW_DIAGNOSTIC_RESULTS')
            saved_run = store.get_run(run_id)
            self.assertEqual(saved_run['attempt']['phase'], 'DECIDED')
            self.assertEqual(saved_run['attempt']['result'], 'PASS')
            result_event = store.get_event(terminal_payloads[-1]['event_id'])
            result_payload = result_event['payload']
            self.assertEqual(result_payload['run_kind'], 'DIAGNOSTIC')
            self.assertEqual(result_payload['result'], 'PASS')
            self.assertEqual(result_payload['next_action'], 'REVIEW_DIAGNOSTIC_RESULTS')
            self.assertEqual(planner.plan_reviews(run_id), [])
            self.assertEqual(store.list_tasks(run_id, 1), [saved])
            resources = store.get_task_resources(task_id)
            self.assertTrue(resources)
            self.assertTrue(all(resource['state'] == 'RELEASED' for resource in resources))
            scope = store.get_native_scope(task_id)
            self.assertTrue(scope is None or scope['state'] == 'CLEANED')


if __name__ == '__main__':
    unittest.main()
