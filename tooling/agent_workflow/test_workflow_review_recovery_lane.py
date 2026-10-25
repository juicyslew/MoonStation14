"""Review-only ERROR recovery does not contend for native CHECK admission."""
import tempfile
import time
import unittest
from pathlib import Path

import workflow_native_worker
import workflow_native_execution
import workflow_store
from test_workflow_native_execution import admit_native_fixture
from test_workflow_review import ReviewPlanningTests


class ReviewRecoveryLaneTests(unittest.TestCase):
    def test_review_only_recovery_coexists_with_other_native_check_owner(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            helper = ReviewPlanningTests()
            store, run_id, task_id = helper.fixture(root / 'first')
            helper.run_check(root / 'first', store, run_id, task_id)
            run = store.get_run(run_id)['run']
            digest = run['input_digest']
            incident = {
                'contract_version': 1, 'incident_id': 'incident_lane_recovery',
                'run_id': run_id, 'evaluation_generation': 1,
                'code': 'ADAPTER_FAULT', 'message': 'saved recovery fixture',
                'recovery_owner': 'SYSTEM', 'state': 'OPEN',
                'owner_action_required': False, 'artifact_refs': [],
            }
            seq = run['sequence']
            store.transition_attempt(run_id, 1, expected_sequence=seq,
                phase='STOPPING', proposed_result='ERROR', incident=incident)
            store.transition_attempt(run_id, 1, expected_sequence=seq + 1,
                phase='DECIDED', terminal_result='ERROR', cleanup_state='CONFIRMED',
                incident=incident)

            request, bundle = workflow_native_worker.load_native_inputs(store, run_id)
            manifest = workflow_native_worker.resolve_generated_manifest(
                store.get_run_submission(run_id), bundle)
            proof = {
                'contract_version': 1, 'artifact_id': 'artifact_generated_manifest',
                'run_id': run_id, 'producer_task_id': task_id, 'type': 'SOURCE_MANIFEST',
                'path': manifest['path'], 'sha256': manifest['sha256'],
                'size_bytes': manifest['size_bytes'], 'immutable': True,
                'producer_kind': 'TASK', 'producer_id': task_id,
            }
            store.register_artifact(proof, input_digest=digest)
            check_tasks = [t for t in store.list_tasks(run_id, 1) if t['kind'] == 'CHECK']
            check_refs = [ref for ref in check_tasks[0]['result']['artifact_refs']
                          if store.get_artifact(ref)['artifact'].get('type') == 'CHECK_RECEIPT']

            ready = root / 'other-ready'
            release = root / 'other-release'
            producer_code = (
                "from pathlib import Path\nimport time\n"
                f"ready=Path({str(ready)!r}); release=Path({str(release)!r})\n"
                "ready.write_text('ready',encoding='utf-8')\n"
                "deadline=time.monotonic()+30\n"
                "while not release.exists() and time.monotonic()<deadline: time.sleep(.01)\n"
                "if not release.exists(): raise SystemExit('test release gate timed out')\n"
                "p=Path('build/output.zip'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'pass')\n"
                "c=Path('counter.txt'); c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\n"
            )
            _case, _same_store, _receipt, other_task = admit_native_fixture(
                root / 'other', caller='other-author', key='lane-other',
                shared_store=store,producer_code=producer_code)
            other = store.get_task(other_task)
            other_run = other['run_id']
            backend=workflow_native_execution.NativeExecutionBackend(
                store,root/'other-runtime-artifacts',root/'other-lock')
            try:
                backend.dispatch('event_recovery_native_worker',{'task_id':other_task})
                deadline=time.monotonic()+15
                while time.monotonic()<deadline:
                    if ready.exists() and any(r['state']=='HELD' for r in store.get_task_resources(other_task)):
                        break
                    time.sleep(.01)
                self.assertTrue(ready.exists(),'native CHECK producer did not enter its deterministic gate')
                self.assertTrue(any(r['state']=='HELD' for r in store.get_task_resources(other_task)))
                self.assertIn(store.get_task(other_task)['state'],('CLAIMED','RUNNING'))

                kwargs = dict(expected_sequence=store.get_run(run_id)['run']['sequence'],
                    authorization={'kind': 'POLICY', 'id': 'test'},
                    fresh_input_proof='artifact_generated_manifest',
                    boundary='review:implementation',
                    recovery_event={'event_id': 'event_lane_recovery'},
                    reusable_artifact_refs=check_refs)
                recovered = store.recover_error(run_id, **kwargs)
                self.assertEqual(recovered['generation'], 2)
                self.assertEqual(store.recover_error(run_id, **kwargs), recovered)
                self.assertEqual(store.get_run(run_id)['attempt']['phase'], 'REVIEWING')
                store.assert_submission_lane(other_run,1)
                self.assertTrue(any(resource['state']=='HELD' for resource in store.get_task_resources(other_task)))
                _third_case,_same_store,_third_receipt,third_task=admit_native_fixture(
                    root/'third',caller='third-author',key='lane-third',shared_store=store)
                third_run=store.get_task(third_task)['run_id']
                with self.assertRaises(workflow_store.SubmissionLaneBusy):
                    store.transition_attempt(third_run,1,expected_sequence=0,phase='CHECKING')
                old_checks = [t for t in store.list_tasks(run_id, 1) if t['kind'] == 'CHECK']
                self.assertEqual(old_checks[0]['result']['decision'], 'PASS')
                self.assertEqual((root / 'first' / 'repo' / 'counter.txt').read_text(), '1')
                db = store._connect()
                try:
                    self.assertEqual(db.execute(
                        "SELECT COUNT(*) FROM tasks WHERE run_id=? AND generation=2 AND json_extract(task_json,'$.kind')='CHECK'",
                        (run_id,)).fetchone()[0], 0)
                finally:
                    db.close()
            finally:
                release.touch()
                deadline=time.monotonic()+15
                while time.monotonic()<deadline and store.get_task(other_task)['state'] not in ('COMPLETED','ERROR','CANCELLED'):
                    time.sleep(.025)
                backend.close()
                store.close()


if __name__ == '__main__':
    unittest.main()
