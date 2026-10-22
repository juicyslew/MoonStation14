"""Admission and native-check aggregation for accepted-feature regressions."""

import hashlib
import os
from pathlib import Path


def verify_snapshot_files(root, manifest):
    """Require accepted snapshot contents to exactly match manifested FILE rows."""
    root = Path(root).resolve()
    expected = {row['path'] for row in manifest.get('files', []) if row.get('state') == 'FILE'}
    actual = set()
    for base, dirs, names in os.walk(root, followlinks=False):
        base = Path(base)
        for name in list(dirs) + names:
            path = base / name
            if path.is_symlink(): raise RuntimeError('accepted snapshot contains a symlink')
        for name in names: actual.add((base / name).relative_to(root).as_posix())
    if actual != expected: raise RuntimeError('accepted snapshot file set differs from frozen manifest')
    for row in manifest.get('files', []):
        if row.get('state') == 'FILE':
            path = root.joinpath(*Path(row['path']).parts); data = path.read_bytes()
            if len(data) != row['size_bytes'] or hashlib.sha256(data).hexdigest() != row['sha256']:
                raise RuntimeError('accepted source file differs from frozen manifest')


class RegressionAdmission:
    def __init__(self, store, notify=None):
        self.store = store
        self.notify = notify or (lambda event: None)

    def observe(self, event_id, payload):
        if not isinstance(payload, dict) or payload.get('kind') != 'REGRESSION_READY':
            return 'UNKNOWN'
        job = self.store.get_regression_job(payload.get('regression_id'))
        if job is None or job.get('acceptance_id') != payload.get('acceptance_id'):
            return 'UNKNOWN'
        admitted = self.store.get_regression_for_run(job['run_id'])
        if admitted:
            return 'APPLIED' if (admitted['regression_id'] == job['regression_id'] and
                admitted['acceptance_id'] == job['acceptance_id'] and
                admitted['job'].get('run_id') == job.get('run_id') and
                admitted['job'].get('task_id') == job.get('task_id') and
                admitted['inputs'].get('source_input_digest') == job.get('source_input_digest')) else 'UNKNOWN'
        return 'ABSENT' if job.get('status') == 'QUEUED' else 'UNKNOWN'

    def dispatch(self, event_id, payload):
        if not isinstance(payload, dict) or payload.get('kind') != 'REGRESSION_READY':
            raise RuntimeError('invalid regression-ready wake')
        job = self.store.get_regression_job(payload.get('regression_id'))
        if job is None or any(job.get(key) != payload.get(key) for key in ('acceptance_id','regression_id','run_id')):
            raise RuntimeError('regression-ready wake does not match saved job')
        if self.observe(event_id, payload) == 'APPLIED':
            return 'APPLIED'
        admitted = self.store.admit_regression_job(job['regression_id'])
        self.notify()
        return 'APPLIED'


def aggregate_regression(store, run_id):
    saved = store.get_run(run_id)
    if saved is None:
        raise ValueError('unknown regression run')
    if saved['run'].get('run_kind') != 'POST_ACCEPTANCE_REGRESSION':
        raise ValueError('run is not a post-acceptance regression')
    tasks = store.list_tasks(run_id, saved['attempt']['generation'])
    checks = [task for task in tasks if task['kind'] == 'CHECK']
    if not checks:
        return {'decision': None, 'waiting_task_ids': [], 'findings': []}
    errors = [t for t in checks if (t.get('result') or {}).get('decision') == 'ERROR']
    if errors:
        task = errors[0]
        result = task.get('result') or {}
        incident = store.get_task_incident(task['task_id'])
        error = result.get('error') or incident or result
        return {'decision': 'ERROR', 'waiting_task_ids': [], 'findings': [],
            'error': {'code': error.get('code', 'NATIVE_CHECK_ERROR'),
                'message': error.get('message', 'Native regression check failed'),
                'task_id': task['task_id'], 'artifact_refs': error.get('artifact_refs', result.get('artifact_refs', []))},
            'incident': incident}
    failures = [t for t in checks if (t.get('result') or {}).get('decision') == 'FAIL']
    if failures:
        return {'decision': 'FAIL', 'waiting_task_ids': [],
            'findings': [f for t in failures for f in (t.get('result') or {}).get('findings', [])]}
    waiting = [t['task_id'] for t in checks if t['state'] != 'COMPLETED' or (t.get('result') or {}).get('decision') != 'PASS']
    if waiting:
        return {'decision': None, 'waiting_task_ids': waiting, 'findings': []}
    return {'decision': 'PASS', 'waiting_task_ids': [], 'findings': []}
