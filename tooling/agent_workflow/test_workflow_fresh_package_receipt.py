import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

import workflow_native_execution
from test_workflow_native_execution import admit_native_fixture


class FreshPackageReceiptTests(unittest.TestCase):
    def test_native_receipt_observes_recreated_identical_package_as_fresh(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            package = b'reproducible jar'
            historical = root / 'historical.jar'
            historical.write_bytes(package)
            historical_hash = hashlib.sha256(historical.read_bytes()).hexdigest()
            producer = (
                "from pathlib import Path\n"
                "p=Path('build/output.zip'); p.unlink(); p.write_bytes(b'reproducible jar')\n"
            )
            case, store, submission, task_id = admit_native_fixture(
                root, caller='fresh-package-receipt', key='fresh-package-receipt', delay=0,
                extra_files={'build/output.zip': package.decode('ascii')}, producer_code=producer,
            )
            backend = workflow_native_execution.NativeExecutionBackend(store, root / 'runtime-artifacts', root / 'lock')
            proc = None
            try:
                backend.dispatch('event-fresh-package', {'task_id': task_id})
                proc = backend.workers[task_id]
                proc.wait(timeout=30)
                task = store.get_task(task_id)
                self.assertEqual(task['state'], 'COMPLETED')
                self.assertEqual(task['result']['decision'], 'PASS')
                receipt_binding = next(
                    store.get_artifact(ref) for ref in task['result']['artifact_refs']
                    if store.get_artifact(ref)['artifact'].get('type') == 'CHECK_RECEIPT'
                )
                receipt = json.loads(Path(receipt_binding['artifact']['path']).read_text(encoding='utf-8'))
                rows = [row for check in receipt['checks'] for row in check['evidence'].get('artifacts', [])]
                package_row = next((row for row in rows if row.get('source_path', '').replace('\\', '/').endswith('/build/output.zip')), None)
                self.assertIsNotNone(package_row, rows)
                self.assertTrue(package_row['preexisting'])
                self.assertFalse(package_row['changed_this_run'])
                self.assertTrue(package_row['observed_fresh_this_run'])
                self.assertNotIn('produced_this_run', package_row)
                self.assertEqual(package_row['sha256'], historical_hash)
                self.assertEqual(hashlib.sha256(historical.read_bytes()).hexdigest(), historical_hash)
                self.assertEqual(historical.read_bytes(), package)
            finally:
                backend.close()
                if proc is not None and proc.poll() is None:
                    proc.wait(timeout=3)


if __name__ == '__main__':
    unittest.main()
