import sys
import unittest
from pathlib import Path

import workflow_native_worker
import workflow_store
import workflow_error_recovery
from test_workflow_native_check_records import NativeCheckRecordTests


class VerifiedPrefixTests(NativeCheckRecordTests):
    def _failed_two_check_task(self):
        producer = "from pathlib import Path; p=Path('build/output.zip'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'first')"
        checks = [
            {'argv': [sys.executable, '-c', producer], 'rationale': 'produce', 'covers': ['c1'],
             'kind': 'build', 'artifacts': ['build/output.zip']},
            {'argv': [sys.executable, '-c', 'raise SystemExit(127)'], 'rationale': 'fail',
             'covers': ['c1'], 'kind': 'command'},
        ]
        store, result, _ = self.run_case('prefix-fixture', 'pass\n', checks=checks)
        self.assertEqual(result['decision'], 'ERROR')
        task_id = store.list_tasks(self.run_id)[0]['task_id'] if False else None
        db = store._connect()
        try: task_id = db.execute('select id from tasks order by rowid desc').fetchone()[0]
        finally: db.close()
        request, bundle = workflow_native_worker.load_native_inputs(store, store.get_task(task_id)['run_id'])
        return store, task_id, request, bundle

    def test_returns_verified_prefix_and_original_producer_link(self):
        store, task_id, request, bundle = self._failed_two_check_task()
        result = workflow_error_recovery.verified_prefix(store, task_id, request, bundle)
        self.assertEqual(len(result['records']), 1)
        self.assertEqual(result['records'][0]['exit_code'], 0)
        self.assertEqual(result['source_links'][0]['artifact_ref'], store.list_native_check_records(task_id)[0]['artifact_ref'])

    def test_tampered_frozen_log_is_rejected(self):
        store, task_id, request, bundle = self._failed_two_check_task()
        record_ref = store.list_native_check_records(task_id)[0]['artifact_ref']
        payload = workflow_store.strict_loads(Path(store.get_artifact(record_ref)['artifact']['path']).read_text())
        Path(payload['record']['log']).write_text('tampered')
        with self.assertRaisesRegex(ValueError, 'integrity'):
            workflow_error_recovery.verified_prefix(store, task_id, request, bundle)

    def test_foreign_plan_is_rejected(self):
        store, task_id, request, bundle = self._failed_two_check_task()
        bundle['resolved_plan']['plan']['checks'][0]['rationale'] = 'different plan'
        with self.assertRaisesRegex(ValueError, 'accepted request|provenance'):
            workflow_error_recovery.verified_prefix(store, task_id, request, bundle)
