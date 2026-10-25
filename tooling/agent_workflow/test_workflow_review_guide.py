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
    def test_canonical_review_result_coverage_basis_and_check_kind(self):
        finding = {'criterion_id':'criterion-1','category':'COVERAGE','evidence_refs':['evidence-1'],
            'location':'checks','message':'Review coverage','validation_stage':'TECHNICAL','required_change':'Add coverage'}
        result = {'task_id':'task_1','run_id':'run_1','evaluation_generation':1,
            'assignment_generation':1,'input_digest':'a'*64,'contract_version':1,
            'decision':'FAIL','inspected_refs':['evidence-1'],'findings':[finding],'owner_followups':[]}

        def valid(candidate):
            workflow_store.validate(candidate, 'review_result')

        missing_basis = json.loads(json.dumps(result))
        missing_basis['findings'][0]['check_kind'] = 'gametest'
        with self.assertRaises(workflow_store.ProtocolError):
            valid(missing_basis)

        missing_kind = json.loads(json.dumps(result))
        missing_kind['findings'][0]['coverage_basis'] = 'MISSING_EXECUTION_EVIDENCE'
        with self.assertRaises(workflow_store.ProtocolError):
            valid(missing_kind)

        structured = json.loads(json.dumps(result))
        structured['findings'][0].update(coverage_basis='MISSING_EXECUTION_EVIDENCE', check_kind='gametest')
        valid(structured)
        insufficient = json.loads(json.dumps(result))
        insufficient['findings'][0]['coverage_basis'] = 'INSUFFICIENT_CHECK_COVERAGE'
        valid(insufficient)

        noncoverage = json.loads(json.dumps(result))
        noncoverage['findings'][0]['category'] = 'IMPLEMENTATION'
        valid(noncoverage)
        native = json.loads(json.dumps(result))
        native['findings'][0].update(category='IMPLEMENTATION', coverage_basis='MISSING_EXECUTION_EVIDENCE', check_kind='gametest')
        valid(native)

        # The generic protocol finding schema has no review-only conditional fields.
        generic = workflow_store._SCHEMA['$defs']['finding']
        self.assertNotIn('coverage_basis', generic['properties'])
        self.assertNotIn('check_kind', generic['properties'])
        generic_finding = {'criterion_id':'criterion-1','category':'COVERAGE','message':'Generic coverage',
            'location':'checks','evidence_refs':['evidence-1']}
        from jsonschema import Draft202012Validator
        Draft202012Validator({'$ref':'#/$defs/finding','$defs':workflow_store._SCHEMA['$defs']}).validate(generic_finding)

    def test_input_pages_reassemble_exact_guide_and_reject_unbound_refs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); helper=fixtures.ReviewPlanningTests(); store,run,check=helper.fixture(root)
            helper.run_check(root,store,run,check); planner=helper.coordinator(store,run)
            planner.artifact_dir=root/'guide-artifacts'; planner.artifact_dir.mkdir()
            task=planner.plan_reviews(run)[0]; planner.build_assignment(task['task_id'])
            first=planner.input_access(task['task_id'],'review_guide',0)
            self.assertGreater(first['size_bytes'],4096)
            self.assertEqual('utf-8',first['encoding'])
            pages=[first['content'].encode()]; page=first
            while not page['eof']:
                page=planner.input_access(task['task_id'],'review_guide',page['next_offset'])
                pages.append(page['content'].encode())
            assembled=b''.join(pages)
            self.assertEqual(first['size_bytes'],len(assembled))
            self.assertEqual(first['sha256'],hashlib.sha256(assembled).hexdigest())
            with self.assertRaises(workflow_review.SourceAccessError) as caught:
                planner.input_access(task['task_id'],'foreign-run-artifact',0)
            self.assertEqual('INVALID_QUERY',caught.exception.code)
            with self.assertRaises(workflow_review.SourceAccessError) as caught:
                planner.input_access(task['task_id'],'review_guide',first['size_bytes']+1)
            self.assertEqual('INVALID_QUERY',caught.exception.code)

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
            self.assertLessEqual(len(data),32*1024)
            view = json.loads(data)
            self.assertIn(receipt_ref, view['assignment_artifact_refs'])
            self.assertEqual(receipt_ref, view['evidence'][-1]['read_example']['input_ref'])
            self.assertFalse(view['evidence'][-1]['content_embedded'])
            self.assertTrue(view['evidence'][-1]['checks'])
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
            finding = contract['$defs']['review_finding']
            self.assertIn('inspected_refs', schema['required'])
            self.assertTrue({'category', 'evidence_refs', 'location', 'message'} <= set(finding['required']))
            self.assertIn('required_change', finding['properties'])
            self.assertNotIn('coverage_basis', finding['required'])
            self.assertEqual(len(finding['allOf']),2)
            for decision, required_change in [('PASS', False), ('FAIL', True)]:
                sample = {'task_id': task['task_id'], 'decision': decision, 'inspected_refs': [receipt_ref],
                    'findings': ([{'criterion_id':'criterion-1','category':'IMPLEMENTATION','evidence_refs':[receipt_ref],
                        'location':'source file','message':'Checked evidence','validation_stage':'TECHNICAL', **({'required_change':'Fix defect'} if required_change else {})}] if required_change else []),
                    'owner_followups':[]}
                from jsonschema import Draft202012Validator
                Draft202012Validator(contract).validate(sample)
                canonical={**sample,'contract_version':1,'run_id':run_id,
                    'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
                    'input_digest':task['input_digest']}
                workflow_store.validate(canonical, 'review_result')
            self.assertIn('the coordinator binds those after authorization',view['instruction'])
            self.assertIn('owner_followups',view['instruction'])
            self.assertNotIn('run_id',schema['properties'])
            error_schema=contract['$defs']['task_error']
            self.assertEqual(set(error_schema['required']),set(view['error_report_template']))
            self.assertNotIn('run_id',view['error_report_template'])
            self.assertEqual(set(schema['required']),set(view['result_example']['arguments']['result']))
            self.assertNotIn('run_id',view['result_example']['arguments']['result'])
            from jsonschema import Draft202012Validator
            Draft202012Validator(contract).validate(view['result_example']['arguments']['result'])
            Draft202012Validator({'$ref':'#/$defs/task_error','$defs':contract['$defs']}).validate(view['error_example']['arguments']['error'])

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
            with patch.object(planner,'_build_assignment',return_value=altered):
                guide=planner.review_guide(task['task_id'])
            self.assertTrue(any(x['metadata']['artifact_id']==ref for x in json.loads(Path(guide['path']).read_bytes())['evidence']))
            self.assertEqual(invalid,Path(path).read_bytes())


if __name__ == '__main__': unittest.main()
