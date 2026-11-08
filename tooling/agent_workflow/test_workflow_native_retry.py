"""Focused policy and linked-prefix checks for native retry."""
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import workflow_native_retry
import workflow_store
import workflow_task_artifacts
from test_workflow_native_execution import admit_native_fixture


class NativeRetryPolicyTests(unittest.TestCase):
    def test_fixed_policy_allows_only_transient_worker_errors(self):
        self.assertEqual(workflow_native_retry.MAX_RETRIES_PER_BOUNDARY, 1)
        self.assertEqual(workflow_native_retry.POLICY_ID, 'native-retry-v1')
        self.assertEqual(workflow_native_retry.RETRYABLE_ERRORS,
                         {'SPAWN_FAILED', 'TIMEOUT', 'NATIVE_WORKER_INTERRUPTED'})

    def test_missing_run_is_precisely_blocked_without_advancing_generation(self):
        class Store:
            def get_run(self, _run_id): return None
        with self.assertRaisesRegex(ValueError, 'run is missing'):
            workflow_native_retry.NativeRetry(Store(), '.').prepare('absent')

    def test_actual_admitted_check_error_links_generation_two_and_budget_blocks_three(self):
        for code, message in (('TIMEOUT', 'fixture timeout'),
                              ('NATIVE_EXECUTION_ERROR', workflow_native_retry.MANIFEST_SELECTION_ERROR)):
            with self.subTest(code=code):
                self._actual_admitted_error_links_generation_two(code, message)

    def test_actual_manifest_repair_then_launcher_repair_links_generation_three_once(self):
        self._actual_admitted_error_links_generation_two(
            'NATIVE_EXECUTION_ERROR', workflow_native_retry.MANIFEST_SELECTION_ERROR,
            followup_launcher=True)

    def _actual_admitted_error_links_generation_two(self, error_code, message, *, followup_launcher=False):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _, store, _, task_id = admit_native_fixture(root, caller='retry-real-'+error_code.lower(), key='retry-real-'+error_code.lower())
            task = store.get_task(task_id)
            run_id = task['run_id']
            store.transition_attempt(run_id, 1, expected_sequence=0, phase='CHECKING')
            identity = {'pid': 812, 'created_ms': 19}
            store.dispatch_task(task_id=task_id, event_id='event_retry_native',
                                process_identity=identity, claim_token='retry-token')
            store.claim_native_task(task_id=task_id, run_id=run_id, evaluation_generation=1,
                assignment_generation=1, input_digest=task['input_digest'],
                process_identity=identity, claim_token='retry-token')
            proof_ref = 'artifact_retry_error'
            proof_path = root / 'error.json'
            proof_path.write_bytes(b'{}')
            store.register_artifact({'contract_version': 1, 'artifact_id': proof_ref,
                'run_id': run_id, 'producer_task_id': task_id, 'type': 'PROVENANCE',
                'path': str(proof_path), 'sha256': hashlib.sha256(b'{}').hexdigest(),
                'size_bytes': 2, 'immutable': True, 'producer_kind': 'TASK', 'producer_id': task_id},
                input_digest=task['input_digest'])
            report = {'contract_version': 1, 'task_id': task_id, 'run_id': run_id,
                'evaluation_generation': 1, 'assignment_generation': 1,
                'input_digest': task['input_digest'], 'error_code': error_code,
                'message': message, 'artifact_refs': [proof_ref]}
            store.complete_native_task(task_id=task_id, run_id=run_id,
                evaluation_generation=1, assignment_generation=1, input_digest=task['input_digest'],
                process_identity=identity, claim_token='retry-token',
                outcome={'decision': 'ERROR', 'findings': [], 'artifact_refs': [proof_ref]},
                error_report=report)
            incident = {'contract_version': 1, 'incident_id': 'incident_retry_error',
                'run_id': run_id, 'evaluation_generation': 1, 'task_id': task_id,
                'code': error_code, 'message': message, 'recovery_owner': 'SYSTEM',
                'state': 'OPEN', 'owner_action_required': False, 'artifact_refs': [proof_ref]}
            store.record_incident(incident)
            store.transition_attempt(run_id, 1, expected_sequence=1, phase='STOPPING',
                proposed_result='ERROR', incident=incident)
            store.transition_attempt(run_id, 1, expected_sequence=2, phase='DECIDED',
                terminal_result='ERROR', cleanup_state='CONFIRMED', incident=incident)
            toolchain = {'fingerprint': 'fixture-current-toolchain'}
            with patch.object(workflow_native_retry.workflow_error_recovery, 'verified_prefix',
                              return_value={'records': [], 'source_links': [], 'toolchain': toolchain}):
                retry = workflow_native_retry.NativeRetry(store, root / 'artifacts')
                linked = retry.prepare(run_id)
                self.assertEqual(linked['generation'], 2)
                self.assertEqual(linked['task']['state'], 'QUEUED')
                self.assertEqual(linked['task']['dependency_ids'], [])
                self.assertEqual(linked['task']['deadline_ms'], task['deadline_ms'])
                self.assertEqual(retry.ensure_task(run_id, 2), linked['task'])
                self.assertEqual(len([t for t in store.list_tasks(run_id, 2) if t['kind'] == 'CHECK']), 1)
                self.assertEqual(store.get_task(task_id)['result']['decision'], 'ERROR')
                self.assertEqual(retry.prepare(run_id), linked)
                self.assertEqual(store.get_run(run_id)['run']['current_attempt'], 2)
                proof = store.get_artifact(linked['proof_ref'])['artifact']
                saved_proof = workflow_store.strict_loads(Path(proof['path']).read_text(encoding='utf-8'))
                if error_code == 'NATIVE_EXECUTION_ERROR':
                    self.assertEqual(saved_proof['repair_kind'], 'GENERATED_MANIFEST_SELECTION')
                if followup_launcher:
                    second = linked['task']
                    current = store.get_run(run_id)
                    store.transition_attempt(run_id, 2, expected_sequence=current['run']['sequence'],
                                             phase='CHECKING')
                    identity2 = {'pid': 814, 'created_ms': 21}
                    store.dispatch_task(task_id=second['task_id'], event_id='event_launcher_followup',
                                        process_identity=identity2, claim_token='launcher-token')
                    store.claim_native_task(task_id=second['task_id'], run_id=run_id,
                        evaluation_generation=2, assignment_generation=second['assignment_generation'],
                        input_digest=second['input_digest'], process_identity=identity2,
                        claim_token='launcher-token')
                    launcher_ref, _ = workflow_task_artifacts.register_bytes(
                        store, second, root / 'artifacts', 'PROVENANCE', b'{}')
                    launcher_message = workflow_native_retry.PACKAGE_LAUNCHER_ERROR
                    report2 = {'contract_version': 1, 'task_id': second['task_id'], 'run_id': run_id,
                        'evaluation_generation': 2, 'assignment_generation': second['assignment_generation'],
                        'input_digest': second['input_digest'], 'error_code': 'NATIVE_EXECUTION_ERROR',
                        'message': launcher_message, 'artifact_refs': [launcher_ref]}
                    store.complete_native_task(task_id=second['task_id'], run_id=run_id,
                        evaluation_generation=2, assignment_generation=second['assignment_generation'],
                        input_digest=second['input_digest'], process_identity=identity2,
                        claim_token='launcher-token',
                        outcome={'decision': 'ERROR', 'findings': [], 'artifact_refs': [launcher_ref]},
                        error_report=report2)
                    incident2 = {'contract_version': 1, 'incident_id': 'incident_retry_launcher_followup',
                        'run_id': run_id, 'evaluation_generation': 2, 'task_id': second['task_id'],
                        'code': 'NATIVE_EXECUTION_ERROR', 'message': launcher_message,
                        'recovery_owner': 'SYSTEM', 'state': 'OPEN', 'owner_action_required': False,
                        'artifact_refs': [launcher_ref]}
                    store.record_incident(incident2)
                    current = store.get_run(run_id)
                    store.transition_attempt(run_id, 2, expected_sequence=current['run']['sequence'],
                        phase='STOPPING', proposed_result='ERROR', incident=incident2)
                    current = store.get_run(run_id)
                    store.transition_attempt(run_id, 2, expected_sequence=current['run']['sequence'],
                        phase='DECIDED', terminal_result='ERROR', cleanup_state='CONFIRMED', incident=incident2)
                    third = retry.prepare(run_id)
                    self.assertEqual(third['generation'], 3)
                    third_proof = store.get_artifact(third['proof_ref'])['artifact']
                    third_metadata = workflow_store.strict_loads(
                        Path(third_proof['path']).read_text(encoding='utf-8'))
                    self.assertEqual(third_metadata['repair_kind'], 'WINDOWS_PACKAGE_LAUNCHER')
                    self.assertEqual(store.get_task(task_id)['result']['decision'], 'ERROR')
                    self.assertEqual(store.get_task(second['task_id'])['result']['decision'], 'ERROR')
                    self.assertEqual(retry.prepare(run_id), third)
                    self.assertEqual(store.get_run(run_id)['run']['current_attempt'], 3)
                    self.assertEqual(len([t for t in store.list_tasks(run_id, 3) if t['kind'] == 'CHECK']), 1)
                    return
                db = store._connect()
                try:
                    result = workflow_store.canonical_json({'decision': 'ERROR', 'findings': [], 'artifact_refs': []})
                    db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','COMPLETED','$.result',json(?)) WHERE id=?",
                               (result, linked['task']['task_id']))
                    db.execute("UPDATE attempts SET attempt_json=json_set(attempt_json,'$.phase','DECIDED','$.result','ERROR') WHERE run_id=? AND generation=2",
                               (run_id,))
                finally:
                    db.close()
                with self.assertRaisesRegex(ValueError, 'outside the automatic retry allowlist|budget'):
                    retry.prepare(run_id)
                self.assertEqual(store.get_run(run_id)['run']['current_attempt'], 2)
                self.assertEqual(store.get_task(linked['task']['task_id'])['result']['decision'], 'ERROR')
                # Simulate a corrupt second logical CHECK occupying the same saved
                # generation; the linked-slot helper must not silently select one.
                duplicate = dict(linked['task'], task_id='task_conflicting_retry_check')
                db = store._connect()
                try:
                    db.execute('INSERT INTO tasks(id,run_id,generation,logical_key,task_json) VALUES(?,?,?,?,?)',
                        (duplicate['task_id'], run_id, 2, 'check:conflict', workflow_store.canonical_json(duplicate)))
                finally:
                    db.close()
                with self.assertRaisesRegex(ValueError, 'conflicting CHECK slot'):
                    retry.ensure_task(run_id, 2)
            self.assertEqual(store.get_run(run_id)['run']['current_attempt'], 2)

    def test_reconcile_waits_for_proven_cleanup_then_starts_one_linked_generation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _, store, _, task_id = admit_native_fixture(root, caller='retry-cleanup-wait', key='retry-cleanup-wait')
            task = store.get_task(task_id); run_id = task['run_id']
            store.transition_attempt(run_id, 1, expected_sequence=0, phase='CHECKING')
            identity = {'pid': 813, 'created_ms': 20}
            store.dispatch_task(task_id=task_id, event_id='event_retry_cleanup_task',
                                process_identity=identity, claim_token='cleanup-token')
            store.claim_native_task(task_id=task_id, run_id=run_id, evaluation_generation=1,
                assignment_generation=1, input_digest=task['input_digest'],
                process_identity=identity, claim_token='cleanup-token')
            held = store.acquire_resource(resource_key='native_validation', run_id=run_id, generation=1,
                task_id=task_id, assignment_generation=1, identity_proof=identity)
            error_data = b'{}'; error_path = root / 'error.json'; error_path.write_bytes(error_data)
            error_ref = 'artifact_retry_cleanup_error'
            store.register_artifact({'contract_version': 1, 'artifact_id': error_ref, 'run_id': run_id,
                'producer_task_id': task_id, 'type': 'PROVENANCE', 'path': str(error_path),
                'sha256': hashlib.sha256(error_data).hexdigest(), 'size_bytes': len(error_data),
                'immutable': True, 'producer_kind': 'TASK', 'producer_id': task_id}, input_digest=task['input_digest'])
            report = {'contract_version': 1, 'task_id': task_id, 'run_id': run_id,
                'evaluation_generation': 1, 'assignment_generation': 1, 'input_digest': task['input_digest'],
                'error_code': 'TIMEOUT', 'message': 'fixture timeout', 'artifact_refs': [error_ref]}
            store.complete_native_task(task_id=task_id, run_id=run_id, evaluation_generation=1,
                assignment_generation=1, input_digest=task['input_digest'], process_identity=identity,
                claim_token='cleanup-token', outcome={'decision': 'ERROR', 'findings': [],
                'artifact_refs': [error_ref]}, error_report=report)
            incident = {'contract_version': 1, 'incident_id': 'incident_retry_cleanup_error',
                'run_id': run_id, 'evaluation_generation': 1, 'task_id': task_id, 'code': 'TIMEOUT',
                'message': 'fixture timeout', 'recovery_owner': 'SYSTEM', 'state': 'OPEN',
                'owner_action_required': False, 'artifact_refs': [error_ref]}
            store.record_incident(incident)
            store.transition_attempt(run_id, 1, expected_sequence=1, phase='STOPPING',
                                     proposed_result='ERROR', incident=incident)
            store.transition_attempt(run_id, 1, expected_sequence=2, phase='DECIDED',
                                     terminal_result='ERROR', cleanup_state='UNRESOLVED', incident=incident)
            retry = workflow_native_retry.NativeRetry(store, root / 'artifacts')
            waiting = retry.reconcile()
            self.assertEqual(waiting['waiting_cleanup'], [run_id])
            self.assertEqual(waiting['blocked'], [])
            self.assertIsNone(store.get_incident(retry._blocked_incident_id(run_id, 1)))
            self.assertEqual(store.get_run(run_id)['run']['current_attempt'], 1)
            cleanup_data = workflow_store.canonical_json({'no_live_owned_children': True,
                'fixture_cleanup': 'public release proof'}).encode()
            cleanup_ref, _ = workflow_task_artifacts.register_bytes(store, task, root / 'artifacts',
                'PROVENANCE', cleanup_data)
            store.release_resource('native_validation', claim_id=held['claim_id'], run_id=run_id,
                generation=1, task_id=task_id, assignment_generation=1,
                identity_proof=identity, cleanup_proof=cleanup_ref)
            toolchain = {'fingerprint': 'fixture-current-toolchain'}
            with patch.object(workflow_native_retry.workflow_error_recovery, 'verified_prefix',
                return_value={'records': [], 'source_links': [], 'toolchain': toolchain}):
                ready = retry.reconcile()
                self.assertEqual(ready['started'], [run_id])
                self.assertEqual(ready['blocked'], [])
                self.assertEqual(store.get_run(run_id)['run']['current_attempt'], 2)
                linked_tasks = [item for item in store.list_tasks(run_id, 2) if item['kind'] == 'CHECK']
                self.assertEqual(len(linked_tasks), 1)
                again = retry.reconcile()
                self.assertEqual(again['ensured'], [run_id])
                self.assertEqual(len([item for item in store.list_tasks(run_id, 2) if item['kind'] == 'CHECK']), 1)

    def test_linked_prefix_helper_rejects_tampered_proof(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'proof.json'
            data = b'{"kind":"NATIVE_RETRY_APPLICABILITY"}'
            path.write_bytes(data)
            class Store:
                def get_recovery_metadata(self, *_): return {'boundary': 'native:k', 'fresh_input_proof': 'proof'}
                def get_artifact(self, _):
                    return {'artifact': {'path': str(path), 'size_bytes': len(data),
                        'sha256': hashlib.sha256(b'tampered').hexdigest()}}
            with self.assertRaisesRegex(ValueError, 'integrity mismatch'):
                workflow_native_retry.load_recovery_prefix(Store(),
                    {'run_id': 'run', 'evaluation_generation': 2, 'logical_key': 'k'}, {}, {})

    def test_linked_prefix_helper_rejects_tampered_policy_and_policy_digest(self):
        request = {'input_digest': 'input', 'plan_digest': 'plan', 'policy_digest': 'policy'}
        bundle = {'source_manifest': {}, 'config_provenance': None}
        original = {'kind': 'NATIVE_RETRY_APPLICABILITY', 'policy_id': workflow_native_retry.POLICY_ID,
            'retry_limit': 1, 'run_id': 'run', 'source_task_id': 'old', 'logical_key': 'k',
            'original_generation': 1, 'input_digest': 'input', 'plan_digest': 'plan',
            'policy_digest': 'policy', 'source_manifest_digest': workflow_store.digest({}),
            'config_manifest_digest': workflow_store.digest(None), 'verified_prefix': [],
            'toolchain': {'fingerprint': 'x'}}
        data = workflow_store.canonical_json(original).encode()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'proof.json'; path.write_bytes(data)
            binding = {'artifact': {'path': str(path), 'size_bytes': len(data),
                'sha256': hashlib.sha256(data).hexdigest(), 'type': 'PROVENANCE',
                'producer_kind': 'TASK', 'producer_task_id': 'old', 'producer_id': 'old', 'run_id': 'run'},
                'input_digest': 'input'}
            class Store:
                def get_recovery_metadata(self, *_): return {'boundary': 'native:k', 'fresh_input_proof': 'proof'}
                def get_artifact(self, _): return binding
                def get_task(self, _): return {'task_id': 'old', 'run_id': 'run',
                    'evaluation_generation': 1, 'logical_key': 'k'}
            with patch.object(workflow_native_retry.workflow_error_recovery, 'verified_prefix') as verify:
                for field, bad in (('policy_id', 'wrong-policy'), ('retry_limit', 2),
                                   ('policy_digest', 'tampered-policy-digest')):
                    with self.subTest(field=field):
                        candidate = dict(original)
                        candidate[field] = bad
                        candidate_data = workflow_store.canonical_json(candidate).encode()
                        path.write_bytes(candidate_data)
                        binding['artifact'].update(size_bytes=len(candidate_data),
                            sha256=hashlib.sha256(candidate_data).hexdigest())
                        with self.assertRaisesRegex(ValueError, 'policy|does not match'):
                            workflow_native_retry.load_recovery_prefix(Store(),
                                {'run_id': 'run', 'evaluation_generation': 2, 'logical_key': 'k'}, request, bundle)
                verify.assert_not_called()


if __name__ == '__main__':
    unittest.main()
