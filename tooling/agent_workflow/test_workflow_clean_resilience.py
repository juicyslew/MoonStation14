"""Gradle-clean resilience for admitted workflow evidence and consumers."""
import hashlib
import shutil
import tempfile
import unittest
from pathlib import Path

import workflow_native_worker
import workflow_review
import workflow_store
import workflow_scope
from preflight_test_support import make_preflight_case
import workflow_submission


class CleanResilienceTests(unittest.TestCase):
    def test_clean_after_admission_preserves_frozen_inputs_and_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            case = make_preflight_case(root, repo_name='author', caller='clean-author', key='clean-key')
            # Simulate an ordinary author-provided source manifest in ignored build output.
            (case.repo / '.gitignore').write_text('build/\n', encoding='utf-8')
            source = case.repo / 'build/agent-workflow/evidence/source-manifest.json'
            source.parent.mkdir(parents=True)
            source.write_bytes(case.evidence.read_bytes())
            case.artifact.update(path=str(source), sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                                 size_bytes=source.stat().st_size)
            import workflow_preflight
            case.request['input_digest'] = workflow_store.digest(workflow_preflight.capture_source(case.repo))
            store = workflow_store.WorkflowStore(root / 'history.sqlite')
            artifact_dir = root / 'external-artifacts'
            scope = {'contract_version': 1, 'policy': case.policy, 'artifacts': [case.artifact],
                     'submission_idempotency_key': case.key}
            registration = workflow_scope.register_author_scope(
                store, case.context, case.caller, scope, 'clean-registration', artifact_dir=artifact_dir)
            admitted_context = workflow_scope.effective_context(
                store, case.context, caller=case.caller, submission_idempotency_key=case.key,
                sprint_id=case.request['sprint_id'], policy_digest=registration['policy_digest'])
            self.assertEqual(case.request['input_digest'], workflow_store.digest(
                workflow_preflight.capture_source(admitted_context.repo)))
            case.context = admitted_context
            accepted = workflow_submission.submit_submission(
                store, caller=case.caller, idempotency_key=case.key, request=case.request,
                context=case.context, artifact_dir=artifact_dir)
            self.assertEqual('ACCEPTED', accepted['status'])
            run_id = accepted['run_id']
            frozen = store.get_run_submission(run_id)
            snapshots = []
            for row in frozen['artifacts']:
                art = row['artifact']
                data = Path(art['path']).read_bytes()
                snapshots.append((art['path'], art['sha256'], art['size_bytes'], data))
            self.assertTrue(all(Path(path).is_relative_to(artifact_dir) for path, *_ in snapshots))
            shutil.rmtree(case.repo / 'build')  # fixture-only analogue of gradlew clean
            after_clean_context = workflow_scope.effective_context(
                store, case.context, caller=case.caller, submission_idempotency_key=case.key,
                sprint_id=case.request['sprint_id'], policy_digest=registration['policy_digest'])
            self.assertEqual(admitted_context.policy, after_clean_context.policy)
            self.assertEqual(admitted_context.artifacts, after_clean_context.artifacts)
            request, bundle = workflow_native_worker.load_native_inputs(store, run_id)
            self.assertEqual(case.request, request)
            self.assertEqual(case.request['input_digest'], bundle['input_digest'])
            self.assertEqual((request, bundle), workflow_review.ReviewCoordinator(
                store, reviewers={}, artifact_dir=artifact_dir)._inputs(run_id))
            self.assertEqual(accepted, workflow_submission.submit_submission(
                store, caller=case.caller, idempotency_key=case.key, request=case.request,
                context=case.context, artifact_dir=artifact_dir))
            self.assertEqual(run_id, store.get_run(run_id)['run']['run_id'])
            for path, digest, size, original in snapshots:
                data = Path(path).read_bytes()
                self.assertEqual(original, data)
                self.assertEqual((digest, size), (hashlib.sha256(data).hexdigest(), len(data)))


if __name__ == '__main__':
    unittest.main()
