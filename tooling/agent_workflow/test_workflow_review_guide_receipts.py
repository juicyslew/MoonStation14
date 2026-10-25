import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import workflow_review
import workflow_task_artifacts
import test_workflow_review as fixtures


class ReviewGuideReceiptTests(unittest.TestCase):
    def test_guide_summarizes_receipt_and_keeps_full_original_accessible(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            helper = fixtures.ReviewPlanningTests()
            store, run_id, check_id = helper.fixture(root)
            check = store.get_task(check_id)
            identity = {'pid': 812, 'created_ms': 19}
            store.transition_attempt(run_id, 1, expected_sequence=0, phase='CHECKING')
            store.dispatch_task(task_id=check_id, event_id='event_receipt_test',
                                process_identity=identity, claim_token='receipt-test-token')
            store.claim_native_task(task_id=check_id, run_id=run_id,
                                    evaluation_generation=check['evaluation_generation'],
                                    assignment_generation=check['assignment_generation'],
                                    input_digest=check['input_digest'], process_identity=identity,
                                    claim_token='receipt-test-token')

            receipt = {'checks': [
                {'kind': 'junit', 'tests': ['unit_case_%03d' % i for i in range(250)],
                 'evidence': {'status': 'PASS'}},
                {'kind': 'gametest', 'evidence': {'status': 'PASS', 'counts':
                 {'selected': 2, 'total': 893, 'passed': 2}}},
                {'kind': 'build', 'evidence': {'status': 'PASS', 'artifact': 'build/output.jar'}},
            ]}
            raw = json.dumps(receipt, separators=(',', ':'), sort_keys=True).encode()
            self.assertGreater(len(raw), 2000)
            offsets = {kind: raw.index(('"kind":"%s"' % kind).encode()) for kind in ('gametest', 'build')}
            self.assertTrue(all(offset > 2000 for offset in offsets.values()))
            ref, path = workflow_task_artifacts.register_bytes(store, check, root / 'artifacts', 'CHECK_RECEIPT', raw)
            store.complete_native_task(task_id=check_id, run_id=run_id,
                evaluation_generation=check['evaluation_generation'],
                assignment_generation=check['assignment_generation'], input_digest=check['input_digest'],
                process_identity=identity, claim_token='receipt-test-token',
                outcome={'decision': 'PASS', 'findings': [], 'artifact_refs': [ref]})

            planner = helper.coordinator(store, run_id)
            planner.artifact_dir = root / 'review-guides'
            task = planner.plan_reviews(run_id)[0]
            assignment = planner.build_assignment(task['task_id'])
            guide = planner.review_guide(task['task_id'])
            data = Path(guide['path']).read_bytes()
            view = json.loads(data)
            self.assertEqual(assignment, planner.build_assignment(task['task_id']))
            self.assertIn(ref, view['assignment_artifact_refs'])
            evidence = next(item for item in view['evidence'] if item['metadata']['artifact_id'] == ref)
            self.assertEqual([{'kind':'junit','status':'PASS'},
                {'kind':'gametest','status':'PASS'}, {'kind':'build','status':'PASS'}], evidence['checks'])
            self.assertEqual({'input_ref':ref,'input_offset':0}, evidence['read_example'])
            self.assertFalse(evidence['content_embedded'])
            self.assertEqual(raw, Path(path).read_bytes())
            self.assertEqual(hashlib.sha256(data).hexdigest(), guide['sha256'])
            self.assertEqual(hashlib.sha256(raw).hexdigest(), evidence['metadata']['sha256'])
            self.assertLess(len(data), 32 * 1024)


if __name__ == '__main__':
    unittest.main()
