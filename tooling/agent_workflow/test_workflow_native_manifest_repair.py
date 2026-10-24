import tempfile
import unittest
from pathlib import Path

import workflow_native_retry


class ManifestSelectionRepairEligibilityTests(unittest.TestCase):
    def test_only_exact_preexecution_error_with_no_check_records_qualifies(self):
        class Store:
            records = []
            def list_native_check_records(self, task_id):
                return self.records

        store = Store()
        retry = workflow_native_retry.NativeRetry(store, Path(tempfile.gettempdir()))
        task = {'task_id':'task','evaluation_generation':1}
        incident = {'code':'NATIVE_EXECUTION_ERROR',
                    'message':workflow_native_retry.MANIFEST_SELECTION_ERROR}
        self.assertTrue(retry._is_manifest_selection_repair(task, incident))
        store.records = [{'check_index':0}]
        self.assertFalse(retry._is_manifest_selection_repair(task, incident))
        store.records = []
        self.assertFalse(retry._is_manifest_selection_repair(task,
            {'code':'NATIVE_EXECUTION_ERROR','message':'different failure'}))
        self.assertFalse(retry._is_manifest_selection_repair(
            dict(task, evaluation_generation=2), incident))


if __name__ == '__main__':
    unittest.main()
