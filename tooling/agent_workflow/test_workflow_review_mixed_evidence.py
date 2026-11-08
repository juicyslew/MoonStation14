import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import test_workflow_native_execution as native_fixtures
import test_workflow_review as review_fixtures
import workflow_review


class MixedReviewEvidenceTests(unittest.TestCase):
    def test_submission_xml_receipt_remains_source_beside_real_native_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            make_case = native_fixtures.make_preflight_case
            xml = b'<testsuite tests="1"><testcase name="historical"/></testsuite>'
            def case_with_xml(*args, **kwargs):
                case = make_case(*args, **kwargs)
                path = case.evidence
                path.write_bytes(xml)
                art = case.artifact
                art.update(type='CHECK_RECEIPT', path=str(path), size_bytes=len(xml),
                           sha256=hashlib.sha256(xml).hexdigest())
                return case
            with patch.object(native_fixtures, 'make_preflight_case', side_effect=case_with_xml):
                _case, store, admitted, check_id = native_fixtures.admit_native_fixture(
                    root, caller='mixed-evidence', key='mixed-evidence', review_roles=('implementation',))
            helper = review_fixtures.ReviewPlanningTests()
            helper.run_check(root, store, admitted['run_id'], check_id)
            planner = helper.coordinator(store, admitted['run_id'])
            planner.artifact_dir = root / 'guides'; planner.artifact_dir.mkdir()
            task = planner.plan_reviews(admitted['run_id'])[0]
            check = store.get_task(check_id)
            native_ref = next(ref for ref in check['result']['artifact_refs']
                              if store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT')
            assignment = planner.build_assignment(task['task_id'])
            guide = json.loads(Path(planner.review_guide(task['task_id'])['path']).read_bytes())
            self.assertEqual(assignment, planner.build_assignment(task['task_id']))
            native = next(e for e in guide['evidence'] if e['metadata']['artifact_id'] == native_ref)
            historical = next(e for e in guide['evidence'] if e['metadata']['producer_kind'] == 'SUBMISSION')
            self.assertEqual(json.loads(Path(store.get_artifact(native_ref)['artifact']['path']).read_bytes()), native['checks'])
            self.assertEqual(xml.decode(), historical['content'])
            for entry in (native, historical):
                art = store.get_artifact(entry['metadata']['artifact_id'])['artifact']
                raw = Path(art['path']).read_bytes()
                self.assertEqual((art['sha256'], len(raw)), (entry['metadata']['sha256'], entry['metadata']['size_bytes']))
            self.assertEqual('TASK', native['metadata']['producer_kind'])
            self.assertEqual(check_id, native['metadata']['producer_task_id'])
            self.assertIn(historical['metadata']['artifact_id'], guide['assignment_artifact_refs'])


if __name__ == '__main__': unittest.main()
