"""Positive end-to-end coverage for native program provenance receipts."""
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

from test_workflow_native_execution import admit_native_fixture
import test_workflow_review as review_fixtures


class ProgramProvenanceSDKTests(unittest.TestCase):
    def test_native_producer_accepts_provenance_without_package_plan(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _case, store, submission, task_id = admit_native_fixture(
                root, caller='program-provenance-no-package', key='program-provenance-no-package', delay=0,
                packs=[{'pack_id': 'provenance', 'selectors': [], 'covers': ['c1']}],
                program_checks=['managed_config_provenance'],
                checks=[{'argv': [sys.executable, '-c', 'print(1)'],
                         'rationale': 'diagnostic unit-only fixture', 'covers': ['c1'], 'kind': 'command'}],
            )
            result = review_fixtures.ReviewPlanningTests().run_check(
                root, store, submission['run_id'], task_id)
            self.assertEqual(result['result']['decision'], 'PASS')

            binding = next(
                store.get_artifact(ref) for ref in result['result']['artifact_refs']
                if store.get_artifact(ref)['artifact'].get('type') == 'CHECK_RECEIPT'
            )
            receipt = json.loads(Path(binding['artifact']['path']).read_bytes())
            provenance = next(row for row in receipt['program_checks']
                              if row['kind'] == 'managed_config_provenance')
            self.assertEqual(provenance['covers'], ['c1'])
            self.assertEqual(provenance['evidence']['status'], 'PASS')
            self.assertEqual(provenance['evidence']['package_artifacts'], [])

    def test_native_producer_emits_managed_config_provenance_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _case, store, submission, task_id = admit_native_fixture(
                root, caller='program-provenance', key='program-provenance', delay=0,
                packs=[
                    {'pack_id': 'provenance', 'selectors': [], 'covers': ['c1']},
                    {'pack_id': 'package', 'selectors': [], 'covers': ['c1']},
                ],
                program_checks=['managed_config_provenance'],
            )
            result = review_fixtures.ReviewPlanningTests().run_check(root, store, submission['run_id'], task_id)
            self.assertEqual(result['result']['decision'], 'PASS')

            binding = next(
                store.get_artifact(ref) for ref in result['result']['artifact_refs']
                if store.get_artifact(ref)['artifact'].get('type') == 'CHECK_RECEIPT'
            )
            artifact = binding['artifact']
            receipt = json.loads(Path(artifact['path']).read_bytes())
            provenance = next(row for row in receipt['program_checks']
                              if row['kind'] == 'managed_config_provenance')
            self.assertEqual(provenance['covers'], ['c1'])
            self.assertEqual(provenance['evidence']['status'], 'PASS')
            self.assertEqual(receipt['input_digest'], store.get_run(submission['run_id'])['run']['input_digest'])
            package_artifacts = provenance['evidence']['package_artifacts']
            self.assertTrue(package_artifacts)
            proof = package_artifacts[0]
            archive_artifact = store.get_artifact(proof['artifact_ref'])['artifact']
            archive = Path(archive_artifact['path'])
            self.assertTrue(archive.is_file())
            self.assertEqual(hashlib.sha256(archive.read_bytes()).hexdigest(),
                             proof['sha256'])


if __name__ == '__main__':
    unittest.main()
