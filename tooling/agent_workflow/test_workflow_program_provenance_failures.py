"""End-to-end native worker regressions for post-command provenance drift."""
import unittest
import os
from pathlib import Path
from unittest.mock import patch

import workflow_native_worker
import workflow_runtime
from test_workflow_native_execution import admit_native_fixture
import tempfile


class NativeProgramProvenanceFailureTests(unittest.TestCase):
    """Run real finite checks, then introduce drift before worker verification."""

    def _mutated_after_checks(self, key, mutate):
        original = workflow_runtime.run_finite_checks
        called = False
        completed_records = []

        def run_then_mutate(*args, **kwargs):
            nonlocal called
            records = original(*args, **kwargs)
            completed_records.extend(records)
            called = True
            mutate(args[0], case)
            return records

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            case, store, submission, task_id = admit_native_fixture(
                root, caller='provenance-'+key, key=key, delay=0,
                packs=[{'pack_id':'provenance','selectors':[],'covers':['c1']},
                       {'pack_id':'package','selectors':[],'covers':['c1']}],
                program_checks=['managed_config_provenance'])
            identity = workflow_native_worker.native_process_identity(os.getpid())
            store.dispatch_task(task_id=task_id, event_id='event_'+key,
                                process_identity=identity, claim_token='token-'+key)
            with patch.object(workflow_runtime, 'run_finite_checks', side_effect=run_then_mutate):
                result = workflow_native_worker.execute_native_task(
                    store, task_id=task_id, process_identity=identity,
                    claim_token='token-'+key, artifact_dir=root/'artifacts',
                    lock_dir=root/'lock')
            self.assertTrue(called, 'real finite checks did not run in worker process')
            self.assertEqual('ERROR', result['decision'], result)
            if key.endswith('package-deleted'):
                self.assertFalse((case.repo / 'build/output.zip').exists())
            self.assertFalse(any(store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT'
                                 for ref in result.get('artifact_refs', [])))
            self.assertEqual('RELEASED', store.get_resource_history('native_validation')[-1]['state'])
            self.assertEqual(1, len(completed_records))
            record = completed_records[0]
            self.assertEqual(0, record['exit_code'])
            self.assertEqual('PASS', record['evidence']['status'])
            build_ref = record['evidence']['artifacts'][0]['artifact_ref']
            self.assertEqual('BUILD', store.get_artifact(build_ref)['artifact']['type'])

    def test_tracked_source_mutation_after_last_command_rejects_result(self):
        self._mutated_after_checks('post-check-source', lambda repo, case: (repo / 'producer.py').write_text('changed'))

    def test_managed_config_mutation_after_last_command_rejects_result(self):
        self._mutated_after_checks('post-check-config', lambda repo, case: (repo / 'opencode.jsonc').write_text('{"changed":true}'))

    def test_publication_receipt_mutation_after_last_command_rejects_result(self):
        def mutate(repo, case):
            # The receipt lives beside the fixture repository in the preflight case.
            receipt = case.context.publication_receipt_path
            receipt.write_bytes(receipt.read_bytes() + b' ')
        self._mutated_after_checks('post-check-publication', mutate)

    def test_package_mutation_after_last_command_rejects_result(self):
        def mutate(repo, case):
            package = repo / 'build/output.zip'
            package.write_bytes(b'corrupted after check')
        self._mutated_after_checks('post-check-package', mutate)

    def test_package_deletion_after_last_command_rejects_result(self):
        def mutate(repo, case):
            (repo / 'build/output.zip').unlink()
        self._mutated_after_checks('post-check-package-deleted', mutate)


if __name__ == '__main__':
    unittest.main()
