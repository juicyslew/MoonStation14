"""Focused review-native-log metadata boundary regression coverage."""
import copy
import shutil
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_store
class ReviewNativeLogsTests(unittest.TestCase):
    def setUp(self):
        from test_workflow_reviewer_api import ReviewerAPITests
        self.api_fixture = ReviewerAPITests
        ReviewerAPITests.setUp(self)

    def tearDown(self):
        self.api_fixture.tearDown(self)

    def test_native_log_binding_metadata_must_match_receipt_and_assignment(self):
        valid_log = self.planner.native_log_inputs(self.task['task_id'])[0]
        assignment_hash = self.planner.build_assignment(self.task['task_id'])['body_sha256']
        log_ref = valid_log['artifact_id']
        original_get = self.store.get_artifact
        original_item = original_get(log_ref)
        original_artifact = copy.deepcopy(original_item['artifact'])

        copied_path = self.root / 'same-bytes-different-path.log'
        shutil.copyfile(original_artifact['path'], copied_path)
        mutations = (
            ('producer_id', 'wrong-producer'),
            ('producer_task_id', 'wrong-task'),
            ('type', 'PROVENANCE'),
            ('producer_kind', 'SUBMISSION'),
            ('run_id', 'foreign-run'),
            ('input_digest', 'f' * 64),
            ('path', str(copied_path)),
            ('sha256', 'e' * 64),
        )
        for field, value in mutations:
            with self.subTest(field=field):
                def get_artifact(ref):
                    item = original_get(ref)
                    if ref == log_ref and item:
                        item = copy.deepcopy(item)
                        if field == 'input_digest':
                            item['input_digest'] = value
                        else:
                            item['artifact'][field] = value
                    return item

                with patch.object(self.store, 'get_artifact', side_effect=get_artifact):
                    with self.assertRaises(workflow_store.ProtocolError):
                        self.planner.native_log_inputs(self.task['task_id'])

        self.assertEqual(original_artifact, original_get(log_ref)['artifact'])
        self.assertEqual(assignment_hash,
                         self.planner.build_assignment(self.task['task_id'])['body_sha256'])
