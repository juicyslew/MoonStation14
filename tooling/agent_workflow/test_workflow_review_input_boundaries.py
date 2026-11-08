import hashlib
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_native_execution
import workflow_native_worker
import workflow_preflight
import workflow_review
import workflow_review_results
import workflow_store
import workflow_task_artifacts
import test_workflow_native_execution as native_fixtures
import test_workflow_review as review_fixtures


class ReviewerInputBoundaryTests(unittest.TestCase):
    def test_large_unicode_assigned_evidence_pages_through_authorized_status(self):
        import test_workflow_native_execution as native_fixture
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            original=native_fixture.make_preflight_case
            text=('Reviewer input boundary: αβγ🙂\n' * 900)
            def unicode_case(*args,**kwargs):
                kwargs['evidence']=text.encode('utf-8')
                case=original(*args,**kwargs)
                for index in range(11):
                    data=f'assigned supplemental evidence {index}'.encode()
                    path=root/'author'/f'supplement-{index}.txt'; path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(data)
                    aid=f'artifact_supplement_{index}'
                    case.context.artifacts.append({'contract_version':1,'artifact_id':aid,'run_id':None,
                        'producer_task_id':None,'type':'PROVENANCE','path':str(path),
                        'sha256':hashlib.sha256(data).hexdigest(),'size_bytes':len(data),'immutable':True,
                        'producer_kind':'SUBMISSION','producer_id':case.submission_id,'submission_id':case.submission_id})
                    case.request['criteria'][0]['evidence_refs'].append(aid)
                return case
            with patch.object(native_fixture,'make_preflight_case',side_effect=unicode_case):
                case,store,accepted,check_id=native_fixtures.admit_native_fixture(
                    root/'author',caller='boundary-author',key='boundary-large',review_roles=('implementation',))
            native=workflow_native_execution.NativeExecutionBackend(store,root/'author/artifacts',root/'author/lock')
            try:
                native.dispatch('boundary-check',{'task_id':check_id})
                native.workers[check_id].wait(timeout=30)
            finally: native.close()
            planner=workflow_review.ReviewCoordinator(store,
                reviewers={'implementation':{'role':'reviewer','worktree':str(case.repo)}},artifact_dir=root/'review-artifacts')
            task=planner.plan_reviews(accepted['run_id'])[0]
            assignment=planner.build_assignment(task['task_id'])['body']['envelope']
            self.assertGreaterEqual(len(assignment['artifact_refs']),12)
            evidence=next(ref for ref in assignment['artifact_refs'] if ref==case.artifact_id)
            with patch.object(planner,'_inputs',wraps=planner._inputs) as load_inputs:
                first=planner.input_access(task['task_id'],evidence,0)
                self.assertEqual(1,load_inputs.call_count)
            self.assertGreater(first['size_bytes'],12*1024)
            self.assertEqual('utf-8',first['encoding'])
            chunks=[first['content'].encode('utf-8')]; page=first
            while not page['eof']:
                page=planner.input_access(task['task_id'],evidence,page['next_offset'])
                chunks.append(page['content'].encode('utf-8'))
            raw=b''.join(chunks)
            self.assertEqual(len(raw),first['size_bytes'])
            self.assertEqual(first['sha256'],hashlib.sha256(raw).hexdigest())
            self.assertEqual(text,raw.decode('utf-8'))
            # Offset inside a multibyte scalar is correctable, not a failed task.
            continuation=next(index for index in range(1,len(raw)) if raw[index]&0xC0==0x80)
            with self.assertRaises(workflow_review.SourceAccessError) as error:
                planner.input_access(task['task_id'],evidence,continuation)
            self.assertEqual('INVALID_QUERY',error.exception.code)

            guide=planner.review_guide(task['task_id'])
            with patch.object(workflow_native_worker,'load_native_inputs',wraps=workflow_native_worker.load_native_inputs) as load_inputs:
                prepared=planner.prepare_review_inputs(task['task_id'])
                self.assertEqual(1,load_inputs.call_count)
                self.assertNotIn('verify_current_source',load_inputs.call_args.kwargs)
            self.assertEqual(guide,prepared)
            guide_page=planner.input_access(task['task_id'],'review_guide',0)
            with patch.object(workflow_task_artifacts,'register_bytes',wraps=workflow_task_artifacts.register_bytes) as register:
                while not guide_page['eof']:
                    guide_page=planner.input_access(task['task_id'],'review_guide',guide_page['next_offset'])
                self.assertEqual(0,register.call_count)
            guide_path=Path(guide['path']); original_guide=guide_path.read_bytes()
            try:
                guide_path.write_bytes(original_guide+b'tampered')
                with self.assertRaises(workflow_review.SourceAccessError) as stale:
                    planner.input_access(task['task_id'],'review_guide',0)
                self.assertEqual('SOURCE_STALE',stale.exception.code)
            finally: guide_path.write_bytes(original_guide)

            transport=SimpleNamespace(server='http://127.0.0.1:9',request=Mock(return_value=(200,{'data':{'id':task['assigned_session_id']}})),
                session_matches_assignment=Mock(return_value=True))
            binding=workflow_coordinator.ReviewerBinding('reviewer',str(case.repo),'implementation')
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'author-token':object()},review_bindings={'review-token':binding},
                review_results=workflow_review_results.ReviewResults(store,planner),artifact_dir=root/'artifacts',
                identity_verifier=lambda _:True)
            coordinator.review_pipeline=SimpleNamespace(transport=transport)
            try:
                with TestClient(coordinator.app()) as client:
                    response=client.get('/v1/review/status',headers={'Authorization':'Bearer review-token',
                        'X-MS14-Reviewer-Session':task['assigned_session_id']},params={
                            'task_id':task['task_id'],'input_ref':evidence,'input_offset':0})
                    denied=client.get('/v1/review/status',headers={'Authorization':'Bearer review-token',
                        'X-MS14-Reviewer-Session':'foreign-session'},params={
                            'task_id':task['task_id'],'input_ref':evidence,'input_offset':0})
                self.assertEqual(200,response.status_code,response.text)
                self.assertEqual(evidence,response.json()['input_access']['artifact_id'])
                self.assertEqual(403,denied.status_code)
                self.assertEqual(task['state'],store.get_task(task['task_id'])['state'])
            finally: coordinator.stop()
            # Immutable assigned artifact reads survive unrelated live source changes.
            fresh=planner.input_access(task['task_id'],evidence,0)
            source_row=next(row for row in workflow_native_worker.load_native_inputs(store,accepted['run_id'])[1]
                ['source_manifest']['files'] if row.get('state')=='FILE')
            changed_path=case.repo/source_row['path']; original_source=changed_path.read_bytes()
            try:
                changed_path.write_bytes(original_source+b'\nchanged after first page\n')
                intact=planner.input_access(task['task_id'],evidence,fresh['next_offset'] or 0)
                self.assertEqual(evidence,intact['artifact_id'])
                with self.assertRaises(workflow_review.SourceAccessError) as stale:
                    planner.source_access(task['task_id'],source_path=source_row['path'])
                self.assertEqual('SOURCE_STALE',stale.exception.code)
            finally: changed_path.write_bytes(original_source)

    def test_recovery_generation_two_reads_original_pass_receipt_without_new_check(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); helper=review_fixtures.ReviewPlanningTests()
            store,run_id,check_id=helper.fixture(root/'feature')
            helper.run_check(root/'feature',store,run_id,check_id)
            run=store.get_run(run_id)['run']; digest=run['input_digest']
            check=store.get_task(check_id)
            receipt_ref=next(ref for ref in check['result']['artifact_refs']
                if store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT')
            original_receipt=store.get_artifact(receipt_ref)['artifact']
            incident={'contract_version':1,'incident_id':'incident_input_boundary_recovery',
                'run_id':run_id,'evaluation_generation':1,'code':'ADAPTER_FAULT',
                'message':'review fixture recovery','recovery_owner':'SYSTEM','state':'OPEN',
                'owner_action_required':False,'artifact_refs':[]}
            sequence=run['sequence']
            store.transition_attempt(run_id,1,expected_sequence=sequence,phase='STOPPING',
                proposed_result='ERROR',incident=incident)
            store.transition_attempt(run_id,1,expected_sequence=sequence+1,phase='DECIDED',
                terminal_result='ERROR',cleanup_state='CONFIRMED',incident=incident)
            request,bundle=workflow_native_worker.load_native_inputs(store,run_id)
            manifest=workflow_native_worker.resolve_generated_manifest(store.get_run_submission(run_id),bundle)
            store.register_artifact({'contract_version':1,'artifact_id':'artifact_input_boundary_proof',
                'run_id':run_id,'producer_task_id':check_id,'type':'SOURCE_MANIFEST','path':manifest['path'],
                'sha256':manifest['sha256'],'size_bytes':manifest['size_bytes'],'immutable':True,
                'producer_kind':'TASK','producer_id':check_id},input_digest=digest)
            recovered=store.recover_error(run_id,expected_sequence=store.get_run(run_id)['run']['sequence'],
                authorization={'kind':'POLICY','id':'input-boundary-test'},
                fresh_input_proof='artifact_input_boundary_proof',boundary='review:implementation',
                recovery_event={'event_id':'event_input_boundary_recovery'},
                reusable_artifact_refs=[receipt_ref])
            self.assertEqual(2,recovered['generation'])
            db=store._connect()
            try:
                self.assertEqual(0,db.execute("SELECT COUNT(*) FROM tasks WHERE run_id=? AND generation=2 AND json_extract(task_json,'$.kind')='CHECK'",(run_id,)).fetchone()[0])
            finally: db.close()
            planner=helper.coordinator(store,run_id)
            planner.artifact_dir=root/'review-artifacts'; planner.artifact_dir.mkdir()
            review_task=planner.plan_reviews(run_id)[0]
            assignment=planner.build_assignment(review_task['task_id'])['body']['envelope']
            self.assertIn(receipt_ref,assignment['artifact_refs'])
            guide=planner.review_guide(review_task['task_id'])
            guide_page=planner.input_access(review_task['task_id'],'review_guide',0)
            self.assertEqual(guide['artifact_id'],guide_page['artifact_id'])
            receipt=planner.input_access(review_task['task_id'],receipt_ref,0)
            self.assertEqual(original_receipt['sha256'],receipt['sha256'])
            self.assertEqual(original_receipt['size_bytes'],receipt['size_bytes'])
            self.assertEqual(Path(original_receipt['path']).read_bytes(),receipt['content'].encode('utf-8'))
            guide_data=Path(guide['path']).read_bytes()
            self.assertEqual(guide['sha256'],hashlib.sha256(guide_data).hexdigest())
            import json
            evidence=next(item for item in json.loads(guide_data)['evidence']
                if item['metadata']['artifact_id']==receipt_ref)
            self.assertEqual(check_id,evidence['metadata']['producer_task_id'])
            self.assertEqual(check_id,evidence['metadata']['producer_id'])


if __name__=='__main__': unittest.main()
