"""End-to-end native retry coverage; worker is always a separate OS process."""
import tempfile
import time
import unittest
from pathlib import Path

import workflow_native_execution
import workflow_native_retry
import workflow_native_worker
import workflow_store
from test_workflow_native_execution import admit_native_fixture


class NativeRetryExecutionTests(unittest.TestCase):
    def test_review_error_with_reused_pass_checks_is_not_native_retry_target(self):
        from test_workflow_review import ReviewPlanningTests
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); helper=ReviewPlanningTests()
            store,run_id,task_id=helper.fixture(root)
            helper.run_check(root,store,run_id,task_id)
            run=store.get_run(run_id)['run']; refs=[ref for ref in store.get_task(task_id)['result']['artifact_refs']
                if store.get_artifact(ref)['artifact'].get('type')=='CHECK_RECEIPT']
            incident={'contract_version':1,'incident_id':'incident_review_error','run_id':run_id,
                'evaluation_generation':1,'code':'MISSING_REVIEW_RESULT','message':'review failed',
                'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,'artifact_refs':[]}
            store.transition_attempt(run_id,1,expected_sequence=run['sequence'],phase='STOPPING',proposed_result='ERROR',incident=incident)
            saved=store.get_run(run_id)['run']
            store.transition_attempt(run_id,1,expected_sequence=saved['sequence'],phase='DECIDED',terminal_result='ERROR',cleanup_state='CONFIRMED',incident=incident)
            fresh={'contract_version':1,'artifact_id':'artifact_fresh_retry_test','run_id':run_id,
                'producer_task_id':task_id,'type':'PROVENANCE','path':'fresh.json','sha256':'e'*64,
                'size_bytes':1,'immutable':True,'producer_kind':'TASK','producer_id':task_id}
            store.register_artifact(fresh,input_digest=run['input_digest'])
            current=store.get_run(run_id)['run']
            recovered=store.recover_error(run_id,expected_sequence=current['sequence'],authorization={'kind':'POLICY','id':'test'},
                fresh_input_proof=fresh['artifact_id'],boundary='review:implementation',
                recovery_event={'event_id':'event_review_retry_test'},reusable_artifact_refs=refs)
            current=store.get_run(run_id)['run']
            incident2=dict(incident,incident_id='incident_review_error_gen2',evaluation_generation=2)
            store.transition_attempt(run_id,2,expected_sequence=current['sequence'],phase='STOPPING',proposed_result='ERROR',incident=incident2)
            current=store.get_run(run_id)['run']
            store.transition_attempt(run_id,2,expected_sequence=current['sequence'],phase='DECIDED',terminal_result='ERROR',cleanup_state='CONFIRMED',incident=incident2)
            retry=workflow_native_retry.NativeRetry(store,root/'artifacts')
            before=store.get_incident(retry._blocked_incident_id(run_id,2))
            self.assertEqual({'started':[],'ensured':[],'waiting_cleanup':[],'blocked':[]},retry.reconcile())
            self.assertIsNone(before)
            self.assertIsNone(store.get_incident(retry._blocked_incident_id(run_id,2)))
            store.close()

    def test_retry_executes_frozen_two_check_plan_after_external_availability_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            external = root / 'external'
            external.mkdir()
            flag = external / 'available'
            first_count = external / 'first-count'
            second_count = external / 'second-count'
            checks = [
                {'argv': [__import__('sys').executable, 'first.py'], 'rationale': 'first build',
                 'covers': ['c1'], 'kind': 'build', 'artifacts': ['build/first.bin']},
                {'argv': [__import__('sys').executable, 'second.py'], 'rationale': 'second build',
                 'covers': ['c1'], 'kind': 'build', 'artifacts': ['build/second.bin']}]
            case, store, receipt, first_id = admit_native_fixture(
                root, caller='native-retry-service', key='native-retry-service',
                checks=checks,
                extra_files={'first.py': (
                    'from pathlib import Path\n'
                    f"c=Path({str(first_count)!r}); c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\n"
                    "p=Path('build/first.bin'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'first')\n"),
                    'second.py': (
                    'from pathlib import Path\n'
                    f"c=Path({str(second_count)!r}); c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\n"
                    f"if not Path({str(flag)!r}).exists(): raise SystemExit(127)\n"
                    "p=Path('build/second.bin'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'second')\n")})
            backend = workflow_native_execution.NativeExecutionBackend(store, root/'artifacts', root/'lock')
            proc = None
            try:
                _, admitted_bundle = workflow_native_worker.load_native_inputs(store, receipt['run_id'])
                original_plan = admitted_bundle['resolved_plan']
                backend.dispatch('retry-original', {'task_id': first_id})
                proc = backend.workers[first_id]
                proc.wait(timeout=15)
                task = store.get_task(first_id)
                self.assertEqual(task['state'], 'COMPLETED')
                self.assertEqual(task['result']['decision'], 'ERROR')
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    scope = store.get_native_scope(first_id)
                    if scope and scope['state'] == 'CLEANED': break
                    time.sleep(.025)
                self.assertEqual(scope['state'], 'CLEANED')
                current = store.get_run(receipt['run_id'])
                store.transition_attempt(receipt['run_id'], 1,
                    expected_sequence=current['run']['sequence'], phase='STOPPING',
                    proposed_result='ERROR', incident=store.get_task_incident(first_id))
                current = store.get_run(receipt['run_id'])
                store.transition_attempt(receipt['run_id'], 1,
                    expected_sequence=current['run']['sequence'], phase='DECIDED',
                    terminal_result='ERROR', cleanup_state='CONFIRMED',
                    incident=store.get_task_incident(first_id))
                self.assertEqual(first_count.read_text(), '1')
                self.assertEqual(second_count.read_text(), '1')
                flag.touch()
                linked = workflow_native_retry.NativeRetry(store, root/'artifacts').prepare(receipt['run_id'])
                second_id = linked['task']['task_id']
                self.assertEqual(linked['generation'], 2)
                self.assertEqual(linked['task']['state'], 'QUEUED')
                _, recovered_bundle = workflow_native_worker.load_native_inputs(store, receipt['run_id'])
                self.assertEqual(recovered_bundle['resolved_plan'], original_plan)
                backend.dispatch('retry-linked', {'task_id': second_id})
                proc = backend.workers[second_id]
                proc.wait(timeout=15)
                second = store.get_task(second_id)
                self.assertEqual(second['state'], 'COMPLETED')
                self.assertEqual(second['result']['decision'], 'PASS')
                self.assertEqual(store.get_task(first_id)['result']['decision'], 'ERROR')
                self.assertEqual(first_count.read_text(), '1')
                self.assertEqual(second_count.read_text(), '2')
                records = store.list_native_check_records(second_id)
                self.assertEqual(len(records), 1)
                self.assertEqual(records[0]['check_index'], 1)
                result_artifacts = [store.get_artifact(ref)['artifact']
                    for ref in second['result']['artifact_refs'] if store.get_artifact(ref)]
                check_receipt = next(a for a in result_artifacts if a.get('type') == 'CHECK_RECEIPT')
                full_receipt = __import__('json').loads(Path(check_receipt['path']).read_text())
                self.assertEqual(len(full_receipt['checks']), 2)
                self.assertEqual(full_receipt['reused_check_sources'][0]['source_task_id'], first_id)
                _, recovered_bundle = workflow_native_worker.load_native_inputs(store, receipt['run_id'])
                self.assertEqual(recovered_bundle['resolved_plan'], original_plan)
            finally:
                backend.close()
                if proc is not None and proc.poll() is None:
                    proc.wait(timeout=5)


if __name__ == '__main__':
    unittest.main()
