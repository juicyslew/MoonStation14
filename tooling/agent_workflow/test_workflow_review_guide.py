import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_review
import workflow_store
import workflow_task_artifacts
import test_workflow_review as fixtures


class ReviewGuideTests(unittest.TestCase):
    def test_guide_preserves_assignment_and_complete_verified_receipt_idempotently(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            helper = fixtures.ReviewPlanningTests()
            store, run_id, check_id = helper.fixture(root)
            helper.run_check(root, store, run_id, check_id)
            planner = helper.coordinator(store, run_id)
            planner.artifact_dir = root / 'guide-artifacts'
            planner.artifact_dir.mkdir()
            task = planner.plan_reviews(run_id)[0]
            assignment_before = planner.build_assignment(task['task_id'])
            check = store.get_task(check_id)
            receipt_ref = next(r for r in check['result']['artifact_refs']
                               if store.get_artifact(r)['artifact']['type'] == 'CHECK_RECEIPT')
            artifact = store.get_artifact(receipt_ref)['artifact']
            original = Path(artifact['path']).read_bytes()
            receipt = json.loads(original)
            records = receipt if isinstance(receipt, list) else receipt.get('checks', receipt.get('records', []))
            if records:
                self.assertTrue(any('gametest' in json.dumps(x).lower() or 'build' in json.dumps(x).lower() for x in records))
            guide1 = planner.review_guide(task['task_id'])
            guide2 = planner.review_guide(task['task_id'])
            self.assertEqual((guide1['artifact_id'], guide1['path']), (guide2['artifact_id'], guide2['path']))
            self.assertEqual(assignment_before, planner.build_assignment(task['task_id']))
            data = Path(guide1['path']).read_bytes()
            self.assertEqual(guide1['sha256'], hashlib.sha256(data).hexdigest())
            lines = data.decode().splitlines()
            self.assertTrue(all(len(line) < 2000 for line in lines))
            view = json.loads(data)
            self.assertIn(receipt_ref, view['assignment_artifact_refs'])
            self.assertEqual(receipt, view['evidence'][-1]['checks'])
            self.assertEqual(artifact['sha256'], view['evidence'][-1]['metadata']['sha256'])
            self.assertEqual(original, Path(artifact['path']).read_bytes())
            contract = view['review_contract']
            reachable = {'review_result'}
            def visit(node):
                if isinstance(node, dict):
                    ref=node.get('$ref')
                    if isinstance(ref,str) and ref.startswith('#/$defs/'):
                        name=ref[len('#/$defs/'):]
                        if name not in reachable:
                            reachable.add(name); visit(workflow_store._SCHEMA['$defs'][name])
                    for child in node.values(): visit(child)
                elif isinstance(node,list):
                    for child in node: visit(child)
            visit(workflow_store._SCHEMA['$defs']['review_result'])
            reachable.add('task_error'); reachable.add('refs')
            self.assertEqual(reachable,set(contract['$defs']))
            self.assertNotIn('submission', contract['$defs'])
            schema = contract['$defs']['review_result']
            finding = contract['$defs']['finding']
            self.assertIn('inspected_refs', schema['required'])
            self.assertTrue({'category', 'evidence_refs', 'location', 'message'} <= set(finding['required']))
            self.assertIn('required_change', finding['properties'])
            for decision, required_change in [('PASS', False), ('FAIL', True)]:
                sample = {'contract_version': 1, 'task_id': task['task_id'], 'run_id': run_id,
                    'evaluation_generation': task['evaluation_generation'], 'assignment_generation': task['assignment_generation'],
                    'input_digest': task['input_digest'], 'decision': decision, 'inspected_refs': [receipt_ref],
                    'findings': [{'criterion_id':'criterion-1','category':'IMPLEMENTATION','evidence_refs':[receipt_ref],
                        'location':'source file','message':'Checked evidence', **({'required_change':'Fix defect'} if required_change else {})}]}
                from jsonschema import Draft202012Validator
                Draft202012Validator(contract).validate(sample)
                workflow_store.validate(sample, 'review_result')

    def test_tampered_source_is_rejected_without_rewrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); helper=fixtures.ReviewPlanningTests(); store,run,check=helper.fixture(root)
            helper.run_check(root,store,run,check); planner=helper.coordinator(store,run)
            planner.artifact_dir=root/'guide-artifacts'; planner.artifact_dir.mkdir()
            task=planner.plan_reviews(run)[0]
            ref=next(r for r in store.get_task(check)['result']['artifact_refs'] if store.get_artifact(r)['artifact']['type']=='CHECK_RECEIPT')
            art=store.get_artifact(ref)['artifact']; path=Path(art['path']); path.write_bytes(b'changed')
            with self.assertRaises(workflow_store.ProtocolError): planner.review_guide(task['task_id'])
            self.assertEqual(b'changed',path.read_bytes())

    def test_registered_malformed_check_receipt_is_not_silently_downgraded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); helper=fixtures.ReviewPlanningTests(); store,run,check=helper.fixture(root)
            helper.run_check(root,store,run,check); planner=helper.coordinator(store,run)
            planner.artifact_dir=root/'guide-artifacts'; planner.artifact_dir.mkdir()
            task=planner.plan_reviews(run)[0]
            original=planner.build_assignment(task['task_id'])
            invalid=b'{not json'
            from workflow_task_artifacts import register_bytes
            ref,path=register_bytes(store,store.get_task(check),root/'guide-artifacts','CHECK_RECEIPT',invalid)
            altered=dict(original); altered['body']=dict(original['body']); envelope=dict(altered['body']['envelope'])
            envelope['artifact_refs']=list(envelope['artifact_refs'])+[ref]; altered['body']['envelope']=envelope
            with patch.object(planner,'build_assignment',return_value=altered):
                guide=planner.review_guide(task['task_id'])
            self.assertTrue(any(x['metadata']['artifact_id']==ref for x in json.loads(Path(guide['path']).read_bytes())['evidence']))
            self.assertEqual(invalid,Path(path).read_bytes())


if __name__ == '__main__': unittest.main()
