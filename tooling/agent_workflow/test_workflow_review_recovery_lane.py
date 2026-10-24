"""A busy submission lane rejects review recovery without partial writes."""
import tempfile
import unittest
from pathlib import Path

import deploy_workflow
import workflow_native_worker
import workflow_store
from test_workflow_native_execution import admit_native_fixture
from test_workflow_review import ReviewPlanningTests


class ReviewRecoveryLaneTests(unittest.TestCase):
    def test_busy_lane_recovery_rolls_back_then_retries_after_other_run_closes(self):
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

            _case, _same_store, _receipt, other_task = admit_native_fixture(
                root / 'other', caller='other-author', key='lane-other',
                shared_store=store)
            other = store.get_task(other_task)
            other_run = other['run_id']
            store.transition_attempt(other_run, 1, expected_sequence=0, phase='CHECKING')

            kwargs = dict(expected_sequence=store.get_run(run_id)['run']['sequence'],
                authorization={'kind': 'POLICY', 'id': 'test'},
                fresh_input_proof='artifact_generated_manifest',
                boundary='review:implementation',
                recovery_event={'event_id': 'event_lane_recovery'},
                reusable_artifact_refs=check_refs)
            before = deploy_workflow.logical_db_fingerprint(store.path)
            old_run = store.get_run(run_id)
            with self.assertRaises(workflow_store.SubmissionLaneBusy):
                store.recover_error(run_id, **kwargs)
            self.assertEqual(deploy_workflow.logical_db_fingerprint(store.path), before)
            unchanged = store.get_run(run_id)
            self.assertEqual(unchanged['run']['current_attempt'], old_run['run']['current_attempt'])
            self.assertEqual(unchanged['run']['sequence'], old_run['run']['sequence'])

            other_attempt = store.get_run(other_run)['attempt']
            store.transition_attempt(other_run, 1,
                expected_sequence=store.get_run(other_run)['run']['sequence'], phase='STOPPING',
                closure='CANCELLED')
            other_attempt = store.get_run(other_run)['attempt']
            store.transition_attempt(other_run, 1,
                expected_sequence=store.get_run(other_run)['run']['sequence'], phase='CLOSED',
                cleanup_state='CONFIRMED')

            kwargs['expected_sequence'] = store.get_run(run_id)['run']['sequence']
            recovered = store.recover_error(run_id, **kwargs)
            self.assertEqual(recovered['generation'], 2)
            self.assertEqual(store.recover_error(run_id, **kwargs), recovered)
            self.assertEqual(store.get_run(run_id)['attempt']['phase'], 'REVIEWING')
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
            store.close()


if __name__ == '__main__':
    unittest.main()
