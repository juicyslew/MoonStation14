"""Fail-closed recognition and bounded use of known native defects."""
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

import workflow_native_retry
import workflow_store


class StaleJUnitRecognitionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.task = {'task_id': 'task-old', 'run_id': 'run-old', 'evaluation_generation': 1,
                     'assignment_generation': 1, 'input_digest': 'input'}
        self.record = {'kind': 'junit', 'exit_code': 0,
            'evidence': {'status': 'UNVERIFIED', 'findings': sorted(workflow_native_retry.STALE_JUNIT_FINDINGS),
                         'counts': {'tests': 0, 'failed': 0, 'errors': 0, 'skipped': 0}},
            'log': str(self.root / 'gradle.log'), 'log_sha256': ''}
        log = b'> Task :test UP-TO-DATE\nBUILD SUCCESSFUL\n'
        (self.root / 'gradle.log').write_bytes(log)
        self.record['log_sha256'] = hashlib.sha256(log).hexdigest()
        data = workflow_store.canonical_json({'kind': 'NATIVE_CHECK_COMPLETED', 'task_id': 'task-old',
            'run_id': 'run-old', 'evaluation_generation': 1, 'assignment_generation': 1,
            'input_digest': 'input', 'check_index': 0, 'record': self.record}).encode()
        self.artifact_path = self.root / 'record.json'
        self.artifact_path.write_bytes(data)
        self.link = {'check_index': 0, 'artifact_ref': 'record'}
        self.artifact = {'path': str(self.artifact_path), 'size_bytes': len(data),
                         'sha256': hashlib.sha256(data).hexdigest()}
        class Store:
            def list_native_check_records(inner, task_id): return [self.link]
            def get_artifact(inner, ref): return {'artifact': self.artifact} if ref == 'record' else None
        self.retry = workflow_native_retry.NativeRetry(Store(), self.root)

    def test_recognizes_exact_stale_incremental_junit_proof(self):
        self.assertTrue(self.retry._stale_junit_record(self.task))
        incident = {'code': 'EVIDENCE_UNVERIFIED'}
        self.assertEqual(self.retry._known_repair_kind(self.task, incident), 'STALE_INCREMENTAL_JUNIT')

    def test_rejects_log_hash_mismatch_and_nonzero_or_nonstale_reports(self):
        (self.root / 'gradle.log').write_text('> Task :compile UP-TO-DATE\nBUILD SUCCESSFUL\n')
        with self.assertRaisesRegex(ValueError, 'integrity mismatch'):
            self.retry._stale_junit_record(self.task)
        log = b'> Task :test UP-TO-DATE\nBUILD SUCCESSFUL\n'
        (self.root / 'gradle.log').write_bytes(log)
        self.record['log_sha256'] = hashlib.sha256(log).hexdigest()
        self.record['evidence']['counts']['tests'] = 1
        self._rewrite_record()
        self.assertFalse(self.retry._stale_junit_record(self.task))
        self.record['evidence']['counts']['tests'] = 0
        self.record['evidence']['findings'] = ['expected selected JUnit classes missing from fresh XML']
        self._rewrite_record()
        self.assertFalse(self.retry._stale_junit_record(self.task))
        self.assertIsNone(self.retry._known_repair_kind(self.task, {'code': 'EVIDENCE_UNVERIFIED'}))

    def _rewrite_record(self):
        payload = {'kind': 'NATIVE_CHECK_COMPLETED', 'task_id': 'task-old', 'run_id': 'run-old',
            'evaluation_generation': 1, 'assignment_generation': 1, 'input_digest': 'input',
            'check_index': 0, 'record': self.record}
        data = workflow_store.canonical_json(payload).encode()
        self.artifact_path.write_bytes(data)
        self.artifact.update(size_bytes=len(data), sha256=hashlib.sha256(data).hexdigest())

    def test_later_known_repair_replay_requires_explicit_supported_proof(self):
        task = {'task_id': 'old', 'evaluation_generation': 2}
        empty = {'records': [], 'source_links': [], 'toolchain': []}
        self.assertEqual(workflow_native_retry._recovery_prefix(None, task, {}, {},
            reuse_strategy=workflow_native_retry.REPLAY_STRATEGY,
            repair_kind='WINDOWS_PACKAGE_LAUNCHER'), empty)
        for strategy, kind, source in (
                ('UNRECOGNIZED', 'WINDOWS_PACKAGE_LAUNCHER', task),
                (workflow_native_retry.REPLAY_STRATEGY, 'UNKNOWN', task),
                (workflow_native_retry.REPLAY_STRATEGY, 'WINDOWS_PACKAGE_LAUNCHER',
                 {'task_id': 'first-generation', 'evaluation_generation': 1})):
            with self.subTest(strategy=strategy, kind=kind, source=source):
                with self.assertRaisesRegex(ValueError, 'unsupported prefix reuse strategy'):
                    workflow_native_retry._recovery_prefix(None, source, {}, {},
                        reuse_strategy=strategy, repair_kind=kind)


class ReconcileKnownRepairTests(unittest.TestCase):
    def test_decided_later_generation_can_recover_despite_prior_blocker(self):
        run_id = 'run-later'
        run = {'run_id': run_id, 'current_attempt': 2, 'run_kind': 'FEATURE_GATE'}
        attempt = {'phase': 'DECIDED', 'result': 'ERROR'}
        task = {'task_id': 'task-later', 'kind': 'CHECK', 'evaluation_generation': 2,
                'state': 'COMPLETED', 'result': {'decision': 'ERROR'}}
        store = Mock()
        store.list_runs_by_phase.return_value = [run_id]
        store.get_run.return_value = {'run': run, 'attempt': attempt}
        store.get_recovery_metadata.return_value = {'boundary': 'native:check'}
        store.list_tasks.return_value = [task]
        store.get_incident.return_value = {'incident': {'message': 'outside allowlist'}}
        store.get_task_incident.return_value = {'code': 'NATIVE_EXECUTION_ERROR',
            'message': workflow_native_retry.PACKAGE_LAUNCHER_ERROR}
        retry = workflow_native_retry.NativeRetry(store, '.')
        retry._cleanup_ready = Mock(return_value=True)
        retry._known_repair_kind = Mock(return_value='WINDOWS_PACKAGE_LAUNCHER')
        retry._used_repairs = Mock(return_value=set())
        retry.prepare = Mock(return_value={'generation': 3})
        summary = retry.reconcile()
        self.assertEqual(summary['started'], [run_id])
        retry.prepare.assert_called_once_with(run_id)

    def test_nonknown_error_with_prior_blocker_remains_blocked(self):
        run_id = 'run-generic'
        store = Mock()
        store.list_runs_by_phase.return_value = [run_id]
        store.get_run.return_value = {'run': {'run_id': run_id, 'current_attempt': 2,
            'run_kind': 'FEATURE_GATE'}, 'attempt': {'phase': 'DECIDED', 'result': 'ERROR'}}
        store.get_recovery_metadata.return_value = None
        store.list_tasks.return_value = [{'task_id': 'task-generic', 'kind': 'CHECK',
            'evaluation_generation': 2, 'state': 'COMPLETED', 'result': {'decision': 'ERROR'}}]
        store.get_incident.return_value = {'incident': {'message': 'outside allowlist'}}
        store.get_task_incident.return_value = {'code': 'NATIVE_EXECUTION_ERROR', 'message': 'unrecognized'}
        retry = workflow_native_retry.NativeRetry(store, '.')
        retry._cleanup_ready = Mock(return_value=True)
        summary = retry.reconcile()
        self.assertEqual(summary['blocked'], [run_id])
        self.assertEqual(summary['started'], [])

    def test_repair_proof_read_failure_blocks_only_current_run(self):
        run_id = 'run-corrupt-proof'
        store = Mock()
        store.list_runs_by_phase.return_value = [run_id]
        store.get_run.return_value = {'run': {'run_id': run_id, 'current_attempt': 2,
            'run_kind': 'FEATURE_GATE'}, 'attempt': {'phase': 'DECIDED', 'result': 'ERROR'}}
        store.get_recovery_metadata.return_value = None
        store.list_tasks.return_value = [{'task_id': 'task-corrupt-proof', 'kind': 'CHECK',
            'evaluation_generation': 2, 'state': 'COMPLETED', 'result': {'decision': 'ERROR'}}]
        store.get_incident.return_value = {'incident': {'message': 'outside allowlist'}}
        store.get_task_incident.return_value = {'code': 'EVIDENCE_UNVERIFIED', 'message': 'evidence'}
        retry = workflow_native_retry.NativeRetry(store, '.')
        retry._cleanup_ready = Mock(return_value=True)
        retry._known_repair_kind = Mock(side_effect=ValueError('native CHECK evidence integrity mismatch'))
        summary = retry.reconcile()
        self.assertEqual(summary['blocked'], [run_id])
        self.assertEqual(summary['started'], [])


if __name__ == '__main__':
    unittest.main()
