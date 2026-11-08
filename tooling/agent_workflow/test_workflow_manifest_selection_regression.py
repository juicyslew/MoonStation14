"""Regression coverage for filename-colliding author and generated manifests."""
import hashlib
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

import workflow_native_worker
import workflow_store
import workflow_submission
import workflow_preflight
import workflow_review
import workflow_native_retry
from preflight_test_support import make_preflight_case


class ManifestSelectionRegressionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.case = make_preflight_case(self.root, caller='manifest-collision', key='collision')
        # A legitimate author attachment may have the same basename as the generated
        # canonical manifest; it remains an independently integrity-checked artifact.
        authored = self.root / 'source-manifest.json'
        authored.write_bytes(self.case.evidence.read_bytes())
        artifact = dict(self.case.artifact, path=str(authored), sha256=hashlib.sha256(authored.read_bytes()).hexdigest(),
                        size_bytes=authored.stat().st_size)
        self.case.artifact = artifact
        self.case.context = replace(self.case.context, artifacts=[artifact])
        self.store = workflow_store.WorkflowStore(self.root / 'workflow.sqlite')
        result = workflow_submission.submit_submission(self.store, caller=self.case.caller,
            idempotency_key=self.case.key, request=self.case.request, context=self.case.context,
            artifact_dir=self.root / 'artifacts')
        self.assertEqual(result['status'], 'ACCEPTED')
        self.run_id = result['run_id']

    def tearDown(self):
        self.tmp.cleanup()

    def test_generated_manifest_selected_despite_same_basename_author_attachment(self):
        saved = self.store.get_run_submission(self.run_id)
        colliding = [b['artifact'] for b in saved['artifacts']
                     if Path(b['artifact']['path']).name == 'source-manifest.json']
        self.assertEqual(len(colliding), 2)
        author = next(a for a in colliding if a['artifact_id'] == 'artifact_evidence')
        generated = next(a for a in colliding if a['artifact_id'] != author['artifact_id'])
        self.assertNotEqual(author['sha256'], generated['sha256'])
        _, bundle = workflow_native_worker.load_native_inputs(self.store, self.run_id)
        self.assertEqual(bundle['source_manifest'], workflow_preflight.capture_source(self.case.repo))

    def test_authored_colliding_manifest_tamper_is_still_rejected(self):
        saved = self.store.get_run_submission(self.run_id)
        author = next(b['artifact'] for b in saved['artifacts'] if b['artifact']['artifact_id'] == 'artifact_evidence')
        Path(author['path']).write_bytes(b'tampered author attachment')
        with self.assertRaisesRegex(ValueError, 'integrity'):
            workflow_native_worker.load_native_inputs(self.store, self.run_id)

    def test_generated_manifest_tamper_is_rejected(self):
        saved = self.store.get_run_submission(self.run_id)
        generated = next(b['artifact'] for b in saved['artifacts']
                         if b['artifact']['type'] == 'SOURCE_MANIFEST' and b['artifact']['artifact_id'] != 'artifact_evidence')
        path = Path(generated['path'])
        path.write_bytes(path.read_bytes() + b' ')
        with self.assertRaisesRegex(ValueError, 'integrity'):
            workflow_native_worker.load_native_inputs(self.store, self.run_id)

    def test_review_planning_never_loads_inputs_after_completed_error(self):
        class Store:
            def get_run(self, _):
                return {'run': {'sequence': 4}, 'attempt': {'phase': 'CHECKING', 'generation': 1}}
            def list_tasks(self, *_):
                return [{'kind': 'CHECK', 'state': 'COMPLETED', 'result': {'decision': 'ERROR'}}]
            def effective_checks_complete(self, *_):
                return True

        coordinator = workflow_review.ReviewCoordinator(Store(), reviewers={}, artifact_dir=self.root)
        with patch.object(coordinator, '_inputs', side_effect=ValueError('manifest integrity')) as inputs:
            self.assertEqual(coordinator.plan_reviews('run'), [])
            inputs.assert_not_called()

    def test_review_planning_requires_verified_inputs_for_pass(self):
        class Store:
            def get_run(self, _):
                return {'run': {'sequence': 4}, 'attempt': {'phase': 'CHECKING', 'generation': 1}}
            def list_tasks(self, *_):
                return [{'kind': 'CHECK', 'task_id': 'check', 'state': 'COMPLETED',
                         'result': {'decision': 'PASS', 'artifact_refs': []}}]
            def effective_checks_complete(self, *_):
                return True

        coordinator = workflow_review.ReviewCoordinator(Store(), reviewers={}, artifact_dir=self.root)
        with patch.object(coordinator, '_inputs', side_effect=ValueError('manifest integrity')) as inputs:
            with self.assertRaisesRegex(ValueError, 'manifest integrity'):
                coordinator.plan_reviews('run')
            inputs.assert_called_once_with('run')

    def test_manifest_repair_classifier_requires_exact_known_incident(self):
        task = {'task_id': self.store.list_tasks(self.run_id)[0]['task_id'],
                'evaluation_generation': 1}
        incident = {'code': 'NATIVE_EXECUTION_ERROR', 'message': workflow_native_retry.MANIFEST_SELECTION_ERROR}
        retry = workflow_native_retry.NativeRetry(self.store, self.root / 'retry-artifacts')
        self.assertTrue(retry._is_manifest_selection_repair(task, incident))
        self.assertFalse(retry._is_manifest_selection_repair(task, dict(incident, message='different failure')))


if __name__ == '__main__':
    unittest.main()
