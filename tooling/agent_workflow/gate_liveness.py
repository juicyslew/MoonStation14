"""Bounded operational recovery for admitted coding gates (never a verdict authority)."""
import hashlib
import base64
import json
import os
import re
import tempfile
import time
import urllib.parse
from pathlib import Path


def _path(root, ident):
    if not isinstance(ident, str) or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,127}', ident):
        raise ValueError('candidate id is not portable')
    return Path(root) / 'gate-health' / (ident + '.json')


def _write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=path.name + '.', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as stream:
            json.dump(value, stream, sort_keys=True, indent=2); stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        try: os.unlink(name)
        except FileNotFoundError: pass


def _lock(root, ident, workflow):
    # Use a per-registration namespace, disjoint from the candidate queue lock.
    return workflow.gate_lock(Path(root) / 'gate-health' / ident) if workflow and hasattr(workflow, 'gate_lock') else _NullLock()


class _NullLock:
    def close(self): pass


def register(root, candidate, *, lead_session_id=None, workflow=None):
    ident = candidate['id']; target = _path(root, ident)
    lock = _lock(root, ident, workflow)
    try:
        if target.is_file():
            old = json.loads(target.read_text(encoding='utf-8'))
            if old.get('candidate_id') != ident or old.get('snapshot_key') != candidate['snapshot_key']:
                raise ValueError('existing liveness registration is bound to another candidate/session')
            if lead_session_id:
                if old.get('session_id') not in (None, lead_session_id):
                    raise ValueError('existing liveness registration is bound to another lead session')
                if old.get('state') in ('finished', 'superseded'):
                    return old
                old.update(session_id=lead_session_id)
                gate = candidate.get('gate_request') or {}
                if gate.get('prompt_admitted') is True and gate.get('session_id') == lead_session_id:
                    old.update(state='observing', reason=None)
                _write(target, old)
            return old
        gate = candidate.get('gate_request') or {}
        record = {'candidate_id': ident, 'snapshot_key': candidate['snapshot_key'], 'session_id': lead_session_id or gate.get('session_id'),
                  'origin_session_id': gate.get('origin_session_id'),
                  'state': 'observing', 'reason': None, 'checked_at': None, 'registered_at': time.time(),
                  'recovery_count': 0, 'last_activity': None, 'last_activity_signature': None,
                  'observed_change_at': None}
        if not record['session_id']:
            record.update(state='observing', reason=gate.get('operational_error') or 'gate admission is in progress')
        _write(target, record); return record
    finally: lock.close()


def read_health(root, ident):
    path = _path(root, ident)
    return json.loads(path.read_text(encoding='utf-8')) if path.is_file() else None


def recover_premature_outcome(root, ident, *, expected_snapshot, expected_lead_session_id,
                              expected_report_sha256, expected_receipt_sha256, workflow):
    """Explicit, narrowly bound repair of a premature NOT_RUN needs_changes record."""
    root = Path(root).resolve(); ident = workflow.validid(ident)
    cp = root / 'candidates' / (ident + '.json')
    initial = workflow.readjson(cp)
    sprint_lock = workflow.sprint_lock(root, initial['worktree']['worktree'], initial['sprint_id'])
    try:
        candidate_lock = workflow.gate_lock(root / 'candidates' / ident)
        try:
            candidate = workflow.readjson(cp)
            marker = candidate.get('gate_recovery') or {}
            archive_path = root / 'gate-recovery' / f'{ident}-{expected_report_sha256}.json'
            if marker:
                if (marker.get('snapshot_key'), marker.get('lead_session_id'), marker.get('report_sha256'), marker.get('receipt_sha256')) != (expected_snapshot, expected_lead_session_id, expected_report_sha256, expected_receipt_sha256):
                    raise ValueError('candidate already has a different recovery binding')
                if not archive_path.is_file() or workflow.sha(archive_path) != marker.get('archive_sha256'):
                    raise ValueError('saved recovery archive is missing or changed')
                return {'status': 'already_recovered', 'archive_path': str(archive_path), 'candidate_id': ident}
            if candidate.get('snapshot_key') != expected_snapshot: raise ValueError('candidate snapshot mismatch')
            gate = candidate.get('gate_request') or {}
            if gate.get('prompt_admitted') is not True or gate.get('dispatch_state') != 'admitted' or gate.get('snapshot_key') != expected_snapshot or gate.get('session_id') != expected_lead_session_id:
                raise ValueError('admitted request/lead binding mismatch')
            outcome = candidate.get('gate_outcome') or {}; review = candidate.get('review') or {}
            if outcome.get('result') != 'needs_changes' or outcome.get('report_sha256') != expected_report_sha256 or review.get('report_sha256') != expected_report_sha256:
                raise ValueError('premature needs_changes outcome/report binding mismatch')
            if candidate.get('owner_decision'): raise ValueError('owner disposition exists')
            workflow.verify_gate(candidate)
            report_path = Path(review.get('report_path', ''))
            if not report_path.is_file() or workflow.sha(report_path) != expected_report_sha256: raise ValueError('review report missing or hash mismatch')
            report_bytes = report_path.read_bytes(); report = workflow.read_report(report_path)
            if report.get('candidate_id') != ident or report.get('snapshot_key') != expected_snapshot or report.get('reviewer') != 'integration-lead' or report.get('decision') != 'needs_changes': raise ValueError('review report identity/decision mismatch')
            health_path = _path(root, ident)
            health = read_health(root, ident) or {}
            existing_archive = None
            if archive_path.is_file():
                try:
                    existing_archive = json.loads(archive_path.read_text(encoding='utf-8'))
                except Exception as exc:
                    raise ValueError('conflicting recovery archive already exists') from exc
                recovery_id = hashlib.sha256((ident+'\0'+expected_snapshot+'\0'+expected_report_sha256+'\0'+expected_receipt_sha256).encode()).hexdigest()
                if (existing_archive.get('format') != 'premature-gate-outcome-recovery-v1' or
                    existing_archive.get('recovery_id') != recovery_id or existing_archive.get('candidate_id') != ident or
                    existing_archive.get('snapshot_key') != expected_snapshot or existing_archive.get('lead_session_id') != expected_lead_session_id or
                    existing_archive.get('old_report_sha256') != expected_report_sha256 or existing_archive.get('receipt_sha256') != expected_receipt_sha256 or
                    existing_archive.get('candidate_before') != candidate):
                    raise ValueError('conflicting recovery archive already exists')
                archived_health = existing_archive.get('health_before') or {}
                if (archived_health.get('candidate_id') != ident or archived_health.get('snapshot_key') != expected_snapshot or
                    archived_health.get('session_id') != expected_lead_session_id):
                    raise ValueError('conflicting recovery archive health binding')
                archived_report = existing_archive.get('report') or {}
                if (archived_report.get('sha256') != expected_report_sha256 or archived_report.get('path') != str(report_path) or
                    base64.b64decode(archived_report.get('bytes_base64', ''), validate=True) != report_bytes):
                    raise ValueError('conflicting recovery archive report binding')
                health = archived_health
            if health.get('candidate_id') != ident or health.get('snapshot_key') != expected_snapshot or health.get('session_id') != expected_lead_session_id:
                raise ValueError('liveness health is not bound to the expected lead session')
            evidence = report.get('evidence_summary') or {}
            if evidence.get('status') != 'NOT_RUN' or report.get('check_evidence') or report.get('checks'):
                raise ValueError('report is not the expected premature NOT_RUN review')
            try: from tooling.agent_workflow import gate_execution as execution
            except ModuleNotFoundError: import gate_execution as execution
            job = execution.read(root, ident)
            if not job or job.get('state') != 'completed' or job.get('candidate_id') != ident or job.get('snapshot_key') != expected_snapshot or job.get('lead_session_id') != expected_lead_session_id or job.get('plan_digest') != workflow.snapshot_key(candidate.get('gate_plan')):
                raise ValueError('matching completed execution job required')
            receipt_ref = job.get('receipt') or {}
            if receipt_ref.get('sha256') != expected_receipt_sha256: raise ValueError('execution receipt hash mismatch')
            receipt_path, _ = workflow.verify_gate_receipt(candidate, require_pass=False)
            if workflow.sha(receipt_path) != expected_receipt_sha256 or str(receipt_path) != str(Path(receipt_ref.get('path', '')).resolve()): raise ValueError('verified receipt binding mismatch')
            if existing_archive is not None:
                saved_receipt = existing_archive.get('verified_receipt') or {}
                if (saved_receipt.get('sha256') != expected_receipt_sha256 or
                    saved_receipt.get('path') != str(receipt_path) or saved_receipt.get('job_id') != job.get('job_id')):
                    raise ValueError('conflicting recovery archive receipt binding')
            recovery_id = hashlib.sha256((ident+'\0'+expected_snapshot+'\0'+expected_report_sha256+'\0'+expected_receipt_sha256).encode()).hexdigest()
            archive = {'format': 'premature-gate-outcome-recovery-v1', 'recovery_id': recovery_id,
                'candidate_id': ident, 'snapshot_key': expected_snapshot, 'lead_session_id': expected_lead_session_id,
                'old_report_sha256': expected_report_sha256, 'receipt_sha256': expected_receipt_sha256,
                'candidate_before': candidate, 'health_before': health,
                'report': {'path': str(report_path), 'sha256': expected_report_sha256, 'bytes_base64': base64.b64encode(report_bytes).decode('ascii')},
                'verified_receipt': {'path': str(receipt_path), 'sha256': expected_receipt_sha256, 'job_id': job.get('job_id')}}
            if existing_archive is None:
                _write(archive_path, archive)
            archive_sha = workflow.sha(archive_path)
            # Retire only delivery observation tied to the superseded completion.
            # Archive already contains the exact prior health record above.
            current_health = read_health(root, ident) or {}
            # Work from current health so unrelated fields and counters survive a retry.
            health = current_health
            if (health.get('candidate_id') != ident or health.get('snapshot_key') != expected_snapshot or
                health.get('session_id') != expected_lead_session_id):
                raise ValueError('liveness health is not bound to the expected lead session')
            health.pop('author_completion_delivery', None)
            for key in ('completion_delivery_pending', 'completion_retry_error', 'completion_retry_at'):
                health.pop(key, None)
            if health.get('state') == 'finished':
                health.update(state='observing', reason='premature terminal outcome archived; continuing existing receipt-bound review')
            _write(health_path, health)
            candidate.pop('gate_outcome', None); candidate.pop('review', None); candidate.pop('author_completion', None)
            candidate['gate_recovery'] = {'recovery_id': recovery_id, 'archive_path': str(archive_path), 'archive_sha256': archive_sha,
                'snapshot_key': expected_snapshot, 'lead_session_id': expected_lead_session_id,
                'report_sha256': expected_report_sha256, 'receipt_sha256': expected_receipt_sha256}
            workflow.writejson(cp, candidate)
            return {'status': 'recovered', 'archive_path': str(archive_path), 'candidate_id': ident}
        finally: candidate_lock.close()
    finally: sprint_lock.close()


def _request(server, method, path, body=None, timeout=15):
    try: import gate_dispatch
    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
    return gate_dispatch._request(server, method, path, body, timeout)


def _data(obj): return obj.get('data', obj) if isinstance(obj, dict) else obj


def _seconds(value):
    """OpenCode message/tool timestamps are epoch ms; retain already-second values."""
    if not isinstance(value, (int, float)): return None
    value = float(value)
    return value / 1000 if abs(value) >= 100_000_000_000 else value


def _message_activity(rows, health, now):
    """Return a stable assistant/tool evidence signature and whether it advanced."""
    material = []
    stamps = []
    for row in rows:
        if not isinstance(row, dict): continue
        times = row.get('time') or {}
        for key in ('created', 'streamed', 'completed'):
            value = times.get(key)
            stamp = _seconds(value)
            if stamp is not None: stamps.append(stamp)
        completed_ms = times.get('completedms')
        stamp = _seconds(completed_ms)
        if stamp is not None: stamps.append(stamp)
        content = row.get('content') or []
        for item in content:
            if not isinstance(item, dict): continue
            tool_times = item.get('time') or {}
            for key in ('created', 'ran', 'completed'):
                stamp = _seconds(tool_times.get(key))
                if stamp is not None: stamps.append(stamp)
        material.append({'id': row.get('id'), 'time': times, 'content': content, 'parts': row.get('parts')})
    signature = hashlib.sha256(json.dumps(material, sort_keys=True, separators=(',', ':'), default=str).encode()).hexdigest()
    changed = bool(health.get('last_activity_signature') and signature != health['last_activity_signature'])
    timestamp = max(stamps) if stamps else None
    if timestamp is not None and health.get('last_activity') is None:
        health['last_activity'] = timestamp
    if changed and timestamp is None:
        # Observation time is not execution time: keep it separate and never
        # promote un-timestamped content changes to confirmed activity.
        health['observed_change_at'] = float(now)
    advanced = changed and timestamp is not None and timestamp > (health.get('last_activity') or 0)
    if advanced:
        health['last_activity'] = timestamp
    health['last_activity_signature'] = signature
    return advanced


def _pid_alive(pid):
    if not isinstance(pid, int) or pid <= 0: return False
    if os.name == 'nt':
        import ctypes
        k = ctypes.WinDLL('kernel32', use_last_error=True)
        handle = ctypes.c_void_p
        k.OpenProcess.argtypes = [ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
        k.OpenProcess.restype = handle
        k.GetExitCodeProcess.argtypes = [handle, ctypes.POINTER(ctypes.c_ulong)]
        k.GetExitCodeProcess.restype = ctypes.c_int
        k.CloseHandle.argtypes = [handle]; k.CloseHandle.restype = ctypes.c_int
        h = k.OpenProcess(0x1000, False, pid)
        if not h: return False
        code = ctypes.c_ulong(); ok = k.GetExitCodeProcess(h, ctypes.byref(code)); k.CloseHandle(h)
        return bool(ok and code.value == 259)
    try: os.kill(pid, 0); return True
    except (OSError, ValueError): return False


def reconcile(root, *, workflow, request=_request, now=None, pid_alive=_pid_alive):
    root = Path(root); now = time.time() if now is None else now
    result = {'pending': 0, 'active': 0, 'recovered': 0, 'blocked': 0, 'errors': []}
    folder = root / 'gate-health'
    folder.mkdir(parents=True, exist_ok=True)
    # The durable execution worker is authoritative over lead-session liveness.
    # Reconcile it before current-head selection so stale/blocked health cannot
    # suppress a live worker. Its own records remain bound to their candidate.
    execution = None
    try:
        try: import gate_execution
        except ModuleNotFoundError: from tooling.agent_workflow import gate_execution
        execution = gate_execution
        execution.reconcile(root, workflow=workflow if hasattr(workflow, 'gate_lock') else __import__('tooling.agent_workflow.workflow_runtime', fromlist=['*']), now=now)
    except Exception as exc:
        result['errors'].append('gate execution reconciliation: ' + str(exc)[:300])
    active_jobs = {}
    queued_jobs = {}
    unknown_jobs = {}
    completed_jobs = {}
    interrupted_jobs = {}
    if execution is not None:
        jobs_dir = root / 'gate-execution'
        if jobs_dir.is_dir():
            for job_path in jobs_dir.glob('*.json'):
                try:
                    job = execution.read(root, job_path.stem)
                    if job and job.get('state') == 'requested':
                        queued_jobs[job.get('candidate_id')] = job
                    elif job and job.get('state') in ('launching','running'):
                        identity_state='unknown'
                        try: identity_state=execution.identity_state(job.get('worker_identity'))
                        except Exception: pass
                        if job.get('state')=='running' and identity_state=='alive': active_jobs[job.get('candidate_id')]=job
                        elif identity_state=='unknown': unknown_jobs[job.get('candidate_id')]=job
                        else: queued_jobs[job.get('candidate_id')]=job
                    elif job and job.get('state') == 'completed':
                        completed_jobs[job.get('candidate_id')] = job
                    elif job and job.get('state') in ('interrupted', 'blocked'):
                        interrupted_jobs[job.get('candidate_id')] = job
                except Exception as exc:
                    result['errors'].append('gate execution record unreadable: ' + str(exc)[:200])
    try:
        try: import owner_attention
        except ModuleNotFoundError: from tooling.agent_workflow import owner_attention
        current_heads = owner_attention.current_work(root, workflow=workflow)
    except Exception as exc:
        return {'pending': 1, 'active': 0, 'recovered': 0, 'blocked': 0, 'errors': ['current-head selection unavailable: ' + str(exc)[:300]]}
    heads = {(x.get('worktree', '').casefold(), x.get('sprint_id')): (x.get('head') or {}).get('id') for x in current_heads}
    active_response = {'loaded': False, 'value': None, 'error': None}

    def active_snapshot():
        if not active_response['loaded']:
            active_response['loaded'] = True
            try:
                status, active_response['value'] = request(None, 'GET', '/api/session/active')
                if status != 200: raise OSError(f'active session endpoint returned HTTP {status}')
            except Exception as exc: active_response['error'] = exc
        if active_response['error']: raise active_response['error']
        return active_response['value']
    health_paths = {p.stem: p for p in folder.glob('*.json')}
    # Bootstrap only candidates with an explicit outstanding request; old records
    # without a request are historical and must never be resurrected.
    for cp in (root / 'candidates').glob('*.json'):
        try:
            c = workflow.readjson(cp)
            current = heads.get((str(Path(c['worktree']['worktree']).resolve()).casefold(), c.get('sprint_id')))
            if c.get('gate_request') and current == c.get('id') and cp.stem not in health_paths:
                register(root, c, workflow=workflow)
                health_paths[cp.stem] = _path(root, cp.stem)
        except Exception: pass
    for hp in sorted(health_paths.values()):
        health = None
        lock = None
        try:
            ident = hp.stem; health = read_health(root, ident)
            lock = _lock(root, ident, workflow)
            if hasattr(lock, 'acquire') and not lock.acquire(blocking=False):
                result['pending'] += 1; continue
            # Re-read only after taking the health-specific lock.
            health = read_health(root, ident)
            if not health or health.get('candidate_id') != ident: raise ValueError('health filename/binding mismatch')
            cp = root / 'candidates' / (ident + '.json'); candidate = workflow.readjson(cp)
            if candidate.get('id') != ident or candidate.get('snapshot_key') != health.get('snapshot_key'): raise ValueError('candidate/snapshot binding mismatch')
            current = heads.get((str(Path(candidate['worktree']['worktree']).resolve()).casefold(), candidate.get('sprint_id')))
            execution_job = active_jobs.get(ident)
            queued_job = queued_jobs.get(ident)
            unknown_job = unknown_jobs.get(ident)
            interrupted_job = interrupted_jobs.get(ident)
            completed_job = completed_jobs.get(ident)
            valid_completed_receipt = False
            if completed_job:
                try:
                    ref=completed_job.get('receipt') or {}; receipt_path=Path(ref.get('path',''))
                    valid_completed_receipt=bool(receipt_path.is_file() and workflow.sha(receipt_path)==ref.get('sha256'))
                except Exception: pass
            if current != ident and not execution_job and not queued_job and not unknown_job and not valid_completed_receipt:
                if health.get('state') not in ('finished', 'superseded'):
                    health.update(state='superseded', checked_at=now, reason='candidate is no longer the canonical current-work head'); _write(hp, health)
                continue
            outcome = candidate.get('gate_outcome') or {}
            if execution_job:
                health.update(state='active', checked_at=now, reason='claimed gate worker identity is alive')
                _write(hp, health); result['active'] += 1; continue
            if queued_job:
                health.update(state='queued',checked_at=now,reason='gate execution request is queued behind the serialized worker slot')
                _write(hp,health); result['pending']+=1; continue
            if unknown_job:
                health.update(state='api_unavailable',checked_at=now,reason='gate worker identity is unknown; execution is quarantined pending reconciliation')
                _write(hp,health); result['pending']+=1; continue
            gate=candidate.get('gate_request') or {}
            if gate.get('dispatch_pending') is True:
                try:
                    try: import gate_dispatch
                    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
                    dispatched=gate_dispatch._dispatch_gate(type('Args',(),{'id':ident,'central_worktree':gate.get('central_worktree'),
                        'origin_session':gate.get('origin_session_id'),'actor':'integration-lead'})(),root,workflow=workflow)
                    fresh=workflow.readjson(cp); latest_gate=fresh.get('gate_request') or {}
                    if latest_gate.get('dispatch_state')=='blocked' and latest_gate.get('dispatch_pending') is False:
                        health.update(state='blocked',checked_at=now,reason=str(latest_gate.get('dispatch_error') or latest_gate.get('operational_error') or 'gate request input is blocked')[:500])
                        _write(hp,health); _notice(root,candidate,health,workflow,request); result['blocked']+=1; continue
                    elif latest_gate.get('prompt_admitted') is True:
                        health.update(state='observing',session_id=latest_gate.get('session_id'),checked_at=now,
                                      reason='integration-lead prompt admitted; awaiting execution evidence')
                        _write(hp,health)
                    result['pending'] += 1
                except Exception as exc:
                    result['errors'].append(f'{ident}: pending gate dispatch: {str(exc)[:300]}')
                    result['pending'] += 1
                continue
            diagnostic = (candidate.get('gate_plan') or {}).get('kind') == 'diagnostic'
            terminal_results = ('needs_changes', 'ready_for_owner') + (('diagnostic_complete',) if diagnostic else ())
            if outcome.get('result') in terminal_results:
                review = candidate.get('owner_review') or {}
                # A persisted verdict is terminal only when its required evidence
                # is actually complete. Never deliver a premature NOT_RUN verdict.
                report = candidate.get('gate_report') or {}
                report_evidence = report.get('evidence_summary') or {}
                scoped_outstanding = any(isinstance(r, dict) and r.get('snapshot_key') == health.get('snapshot_key') and
                    (r.get('dispatch_pending') or r.get('collection_pending')) for r in (candidate.get('scoped_reviews') or {}).values())
                inconsistent = bool((completed_job and report_evidence.get('status') == 'NOT_RUN') or scoped_outstanding)
                if inconsistent:
                    health.update(state='blocked', checked_at=now,
                        reason='inconsistent terminal readiness record has outstanding bound validation/review evidence; administrative recovery required')
                    _write(hp, health); result['blocked'] += 1; continue
                if outcome.get('result') == 'ready_for_owner' and (candidate.get('owner_decision') or review.get('state') != 'awaiting_owner'):
                    health.update(state='finished', checked_at=now, reason='owner responsibility already ended; completion is not retried'); _write(hp, health); continue
                try:
                    workflow.verify_gate(candidate)
                    if outcome.get('result') == 'ready_for_owner': workflow.verify_publication(candidate, root)
                except Exception as exc:
                    health.update(state='finished', checked_at=now, reason='terminal result no longer verifies against current source/publication: ' + str(exc)[:350]); _write(hp, health); continue
                health.update(state='finished', checked_at=now, reason='candidate has persisted gate outcome'); _write(hp, health)
                completion = candidate.get('author_completion') or {}
                if completion.get('status') != 'delivered' and now >= (health.get('completion_retry_at') or 0):
                    try:
                        try: import gate_dispatch
                        except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
                        delivery = gate_dispatch.deliver_gate_completion(type('Args', (), {'id': ident, 'server': None})(), root, workflow=workflow)
                        if not isinstance(delivery, dict) or delivery.get('status') != 'delivered':
                            reason = delivery.get('reason') if isinstance(delivery, dict) else None
                            _completion_failure(hp, health, ident, reason or f"completion delivery returned {delivery.get('status') if isinstance(delivery, dict) else 'invalid response'}", now)
                        else:
                            _completion_success(hp, health)
                    except Exception as exc:
                        _completion_failure(hp, health, ident, str(exc), now)
                if completion.get('status') == 'delivered':
                    error = _observe_author_delivery(root, candidate, health, completion, 'author_completion_delivery', request, now)
                    if error:
                        result['errors'].append(error); result['blocked'] += 1
                continue
            # Dependency downtime defers recovery and lead continuations without
            # waking a model or consuming bounded retries. Durable native jobs,
            # interruption handling, and terminal author receipts are handled above.
            progress_path = root / 'progress' / (ident + '.json')
            progress = json.loads(progress_path.read_text(encoding='utf-8')) if progress_path.is_file() else {}
            if progress and (progress.get('candidate_id') != ident or progress.get('snapshot_key') != health['snapshot_key']):
                health.update(state='blocked', checked_at=now, reason='progress candidate/snapshot binding mismatch; manual reconciliation required'); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
            # Durable interruption and already-recorded admission blocks are
            # operational classifications, not lead continuations to defer.
            preserve_operational = bool(interrupted_job or gate.get('operational_error') or
                gate.get('operational_state') == 'blocked' or gate.get('creation_unknown') or
                progress.get('phase') == 'blocked')
            sid = health.get('session_id')
            if sid and gate.get('session_id') == sid and gate.get('snapshot_key') == health['snapshot_key'] and gate.get('prompt_admitted') is True:
                live_runner = (progress.get('candidate_id') == ident and progress.get('snapshot_key') == health['snapshot_key'] and
                               progress.get('phase') in ('queued', 'running') and pid_alive(progress.get('runner_pid')))
                if live_runner:
                    health.update(state='active', checked_at=now, reason='bound gate runner is alive'); _write(hp, health)
                    result['active'] += 1; continue
            if preserve_operational:
                service_ready = True
            else:
                try:
                    try: import gate_dispatch
                    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
                    service_ready = bool(gate.get('central_worktree') and
                        gate_dispatch._workflow_service_ready(None, gate.get('central_worktree'), request=request))
                except Exception:
                    service_ready = False
            if not service_ready:
                # A terminal operational block is durable; dependency recovery
                # must not erase it and later resume work that was rejected.
                if health.get('state') in ('blocked', 'superseded', 'finished'):
                    result['blocked' if health.get('state') == 'blocked' else 'pending'] += 1
                    continue
                reason='Waiting for the existing ms14_workflow MCP connection in the bound central worktree.'
                health.update(state='waiting_dependency', checked_at=now, reason=reason)
                _write(hp, health)
                result['pending'] += 1
                continue
            scoped_records=candidate.get('scoped_reviews') or {}
            pending_scoped=[(reviewer,record) for reviewer,record in scoped_records.items()
                if record.get('snapshot_key')==health.get('snapshot_key') and (record.get('dispatch_pending') or record.get('collection_pending'))]
            if pending_scoped:
                try:
                    try: import gate_dispatch
                    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
                    reviewer,record=pending_scoped[0]
                    observed=gate_dispatch._dispatch_scoped_review(type('Args',(),{'id':ident,'reviewer':reviewer,
                        'actor':'integration-lead','central_worktree':gate.get('central_worktree'),
                        'repo':gate.get('central_worktree')})(),root,workflow=workflow)
                    if observed.get('status')=='reported':
                        candidate=workflow.readjson(cp); scoped_records=candidate.get('scoped_reviews') or {}
                        record=scoped_records.get(reviewer) or record
                        record['lead_continuation_pending']=True
                        gate_dispatch._write_dynamic(root,ident,workflow,'scoped_reviews',scoped_records)
                        candidate=workflow.readjson(cp)
                    else:
                        result['pending']+=1; continue
                except Exception as exc:
                    try: from tooling.agent_workflow.gate_dispatch import DispatchInputError
                    except ImportError: from gate_dispatch import DispatchInputError
                    if isinstance(exc,DispatchInputError):
                        _block_scoped_input(root,ident,candidate,reviewer,health,workflow,request,exc,now,result)
                    else:
                        result['errors'].append(f'{ident}: scoped review dispatch/collection: {str(exc)[:300]}')
                        result['pending']+=1
                    continue
            has_scoped_stage=any(record.get('lead_continuation_pending') for record in scoped_records.values())
            scoped_for_job=bool(completed_job and any(record.get('execution_job_id')==completed_job.get('job_id') and
                record.get('receipt_sha256')==(completed_job.get('receipt') or {}).get('sha256') and record.get('report_sha256')
                for record in scoped_records.values()))
            if completed_job and not (candidate.get('gate_plan') or {}).get('kind') == 'diagnostic' and not has_scoped_stage and not scoped_for_job:
                # A completed receipt is evidence for the named lead to finish
                # review, never an independent verdict or an invitation to rerun.
                try:
                    if not execution or not execution.read(root, ident): raise ValueError('execution job unavailable')
                    receipt = completed_job.get('receipt') or {}
                    receipt_path = Path(receipt.get('path', ''))
                    if not receipt_path.is_file() or workflow.sha(receipt_path) != receipt.get('sha256'):
                        raise ValueError('completed validation receipt is missing or hash-mismatched')
                    _, evidence = workflow.verify_gate_receipt(candidate, require_pass=False)
                    checks = evidence.get('checks') or []
                    failed = any(x.get('exit_code') != 0 or x.get('evidence', {}).get('status') != 'PASS' for x in checks)
                    key = 'execution_review_delivery'
                    delivery = health.get(key) or {}
                    sid = completed_job.get('lead_session_id')
                    if sid != health.get('session_id') or sid != (candidate.get('gate_request') or {}).get('session_id'):
                        raise ValueError('completed job is not bound to the registered lead')
                    body = delivery.get('body')
                    if not body:
                        mid = 'msg_' + hashlib.sha256((ident+'\0'+completed_job['job_id']+'\0'+receipt['sha256']+'\0review').encode()).hexdigest()[:40]
                        text = (f"Gate validation job {completed_job['job_id']} completed for {ident}. Receipt {receipt['sha256']} is bound to this candidate. "
                                + ('Checks failed; inspect the saved typed findings and finish the existing independent review as needs_changes. Do not rerun checks or create reviewers.' if failed else
                                   'Checks passed; continue this same lead review and persist the independent verdict. Do not rerun checks or create reviewers.'))
                        body = {'id': mid, 'text': text, 'delivery': 'steer', 'resume': True,
                                'metadata': {'workflow_gate_execution_review': True, 'candidate_id': ident,
                                             'snapshot_key': health['snapshot_key'], 'job_id': completed_job['job_id'],
                                             'receipt_sha256': receipt['sha256']}}
                        delivery.update(body=body, job_id=completed_job['job_id'], receipt_sha256=receipt['sha256'])
                        health[key] = delivery; _write(hp, health)
                    if not delivery.get('acknowledged'):
                        _persist_post(root, hp, health, request, sid, body, now, result, recovery=False)
                        if health.get('last_post_receipt'):
                            delivery.update(acknowledged=True,acknowledged_at=now)
                    if delivery.get('acknowledged') and not delivery.get('execution_observed_at'):
                        observed_error=_observe_author_delivery(root,candidate,health,
                            {'id':body['id'],'origin_session_id':sid,'status':'delivered'},key,request,now)
                        delivery=health.get(key) or delivery
                        if observed_error:
                            result['errors'].append(observed_error)
                            if delivery.get('state')=='delivery_blocked':
                                health.update(state='blocked',checked_at=now,reason='completed receipt was delivered but lead execution was not observed: '+str(observed_error)[:250])
                                _write(hp,health); _notice(root,candidate,health,workflow,request); result['blocked']+=1; continue
                    if delivery.get('execution_observed_at'):
                        _,active_raw=request(None,'GET','/api/session/active')
                        active=_data(active_raw)
                        if isinstance(active,dict) and sid in active:
                            result['pending']+=1; continue
                        if delivery.get('review_resume_count',0)>=1:
                            health.update(state='blocked',checked_at=now,reason='named lead stopped after one receipt-bound review-only continuation without a verdict')
                            _write(hp,health); _notice(root,candidate,health,workflow,request); result['blocked']+=1; continue
                        resume_body=delivery.get('review_resume_body')
                        if not resume_body:
                            mid='msg_'+hashlib.sha256((ident+'\0'+completed_job['job_id']+'\0'+receipt['sha256']+'\0review-resume').encode()).hexdigest()[:40]
                            resume_body={'id':mid,'text':f'Resume only the independent verdict for candidate {ident} from saved receipt {receipt["sha256"]}. Do not rerun checks, restart fixtures, or create reviewers. Persist the existing review outcome or report the precise blocker.',
                                'delivery':'steer','resume':True,'metadata':{'workflow_gate_review_resume':True,'candidate_id':ident,
                                    'snapshot_key':health['snapshot_key'],'job_id':completed_job['job_id'],'receipt_sha256':receipt['sha256']}}
                            delivery['review_resume_body']=resume_body; health[key]=delivery; _write(hp,health)
                        status,response=request(None,'POST','/api/session/'+urllib.parse.quote(sid)+'/prompt',resume_body)
                        data=_data(response); payload=data.get('payload') if isinstance(data,dict) else None
                        if status!=200 or not isinstance(data,dict) or data.get('id')!=resume_body['id'] or data.get('sessionID')!=sid or not isinstance(payload,dict) or payload.get('text')!=resume_body['text'] or payload.get('metadata')!=resume_body['metadata']:
                            raise ValueError('review-only continuation acknowledgement mismatch')
                        delivery.update(review_resume_count=1,review_resume_acknowledged=True)
                        health[key]=delivery; _write(hp,health)
                    result['pending'] += 1
                    continue
                except Exception as exc:
                    health.update(state='blocked', checked_at=now, reason='completed gate receipt unavailable/invalid: '+str(exc)[:350]); _write(hp,health)
                    result['blocked'] += 1; continue
            if health.get('state') in ('finished', 'blocked', 'superseded') and not interrupted_job:
                if health.get('state') == 'blocked':
                    if health.get('scoped_review_error_delivery'):
                        _scoped_review_error_delivery(root,candidate,health,request,now)
                    _notice(root, candidate, health, workflow, request); result['blocked'] += 1
                continue
            gate = candidate.get('gate_request') or {}; sid = health.get('session_id')
            # Program-owned admission queues the frozen original plan without
            # asking the lead to run validation. Existing execution records are
            # idempotent; blocked/interrupted/obsolete requests are never revived.
            new_style = (gate.get('prompt_admitted') is True and gate.get('dispatch_state') == 'admitted' and
                         isinstance(gate.get('requested_at'), (int, float)) and gate.get('snapshot_key') == candidate.get('snapshot_key') and
                         gate.get('session_id') == sid)
            if new_style and not (execution_job or queued_job or unknown_job or completed_job or interrupted_job) and not outcome.get('result'):
                progress_path = root / 'progress' / (ident + '.json')
                progress = json.loads(progress_path.read_text(encoding='utf-8')) if progress_path.is_file() else {}
                if health.get('state') not in ('blocked', 'interrupted', 'superseded', 'finished') and progress.get('phase') not in ('blocked', 'interrupted', 'obsolete') and not gate.get('operational_error') and gate.get('operational_state') != 'blocked':
                    try:
                        workflow.verify_gate(candidate)
                        if not execution: raise ValueError('gate execution owner unavailable')
                        job = execution.request_admitted_observer(root, ident, workflow=workflow)
                        health.update(state='queued', execution_job_id=job.get('job_id'), checked_at=now,
                                      reason='program-admitted frozen validation plan queued')
                        _write(hp, health); result['pending'] += 1; continue
                    except Exception as exc:
                        result['errors'].append(f'{ident}: admitted validation queue: {str(exc)[:300]}')
                        result['pending'] += 1; continue
            if interrupted_job:
                reason = str(interrupted_job.get('reason') or interrupted_job.get('error') or 'worker interrupted; cleanup status unknown')
                cleanup = interrupted_job.get('cleanup_status')
                suffix = ('; worker cleanup verified clear; continue with a fresh candidate/fixture if needed' if cleanup in ('clear','verified_clear')
                          else '; child ownership/cleanup is quarantined pending explicit verified clearance')
                health.update(state='blocked', checked_at=now, reason='gate execution '+interrupted_job['state']+': '+reason+suffix,
                              execution_job_id=interrupted_job.get('job_id'))
                _write(hp,health)
                try:
                    import gate_progress
                    prior = json.loads((root/'progress'/(ident+'.json')).read_text(encoding='utf-8'))
                    gate_progress.update(root,candidate,'interrupted',detail=health['reason'],
                                         check_index=prior.get('check_index'),total=prior.get('total'),argv=prior.get('argv'),
                                         log=prior.get('log'),runner_pid=prior.get('runner_pid'),child_pid=prior.get('child_pid'),
                                         started=prior.get('started'))
                except Exception:
                    # Preserve diagnostic fields even if board rendering is
                    # unavailable; the sidecar phase is authoritative here.
                    try:
                        progress_path=root/'progress'/(ident+'.json')
                        progress_record=json.loads(progress_path.read_text(encoding='utf-8'))
                        progress_record.update(phase='interrupted',detail=health['reason'],updated_at=now)
                        _write(progress_path,progress_record)
                    except Exception: pass
                _notice(root,candidate,health,workflow,request); result['blocked'] += 1; continue
            scoped=[(reviewer,record) for reviewer,record in (candidate.get('scoped_reviews') or {}).items()
                    if record.get('snapshot_key')==health.get('snapshot_key') and
                    (record.get('dispatch_pending') or record.get('collection_pending') or record.get('lead_continuation_pending'))]
            if scoped:
                try:
                    try: import gate_dispatch
                    except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
                    reviewer,review_record=scoped[0]
                    observed=gate_dispatch._dispatch_scoped_review(type('Args',(),{'id':ident,'reviewer':reviewer,
                        'actor':'integration-lead','central_worktree':gate.get('central_worktree'),
                        'repo':gate.get('central_worktree')})(),root,workflow=workflow)
                    if observed.get('status')=='reported':
                        report_sha=review_record.get('report_sha256') or ''
                        event_suffix=hashlib.sha256((reviewer+'\0'+report_sha).encode()).hexdigest()
                        key='scoped_review_continuation_'+event_suffix
                        delivery=health.get(key) or {}
                        if not delivery.get('acknowledged'):
                            body=delivery.get('body')
                            if not body:
                                mid='msg_'+hashlib.sha256((ident+'\0'+reviewer+'\0'+report_sha+'\0lead-review').encode()).hexdigest()[:40]
                                body={'id':mid,'text':f'Scoped {reviewer} report is saved for candidate {ident}, hash {report_sha}. Continue this same independent lead review and finish the verdict; do not create another reviewer or rerun validation.',
                                      'delivery':'steer','resume':True,'metadata':{'workflow_gate_scoped_review':True,'candidate_id':ident,
                                      'snapshot_key':health['snapshot_key'],'reviewer':reviewer,'report_sha256':report_sha}}
                                delivery.update(body=body,report_sha256=report_sha,reviewer=reviewer)
                                health[key]=delivery; _write(hp,health)
                            status,response=request(None,'POST','/api/session/'+urllib.parse.quote(sid)+'/prompt',body)
                            data=_data(response); payload=data.get('payload') if isinstance(data,dict) else None
                            if status!=200 or not isinstance(data,dict) or data.get('id')!=body['id'] or data.get('sessionID')!=sid or not isinstance(payload,dict) or payload.get('text')!=body['text'] or payload.get('metadata')!=body['metadata']:
                                raise ValueError('scoped review continuation acknowledgement mismatch')
                            delivery.update(acknowledged=True,acknowledged_at=now,body=body)
                            health[key]=delivery; _write(hp,health)
                        receipt={'id':delivery.get('body',{}).get('id'),'origin_session_id':sid,'status':'delivered'}
                        observe_error=_observe_author_delivery(root,candidate,health,receipt,key,request,now)
                        if observe_error:
                            result['errors'].append(observe_error)
                        fresh=workflow.readjson(cp); fresh_scoped=fresh.get('scoped_reviews') or {}
                        current_record=fresh_scoped.get(reviewer)
                        if current_record and current_record.get('report_sha256')==report_sha:
                            current_record['lead_continuation_pending']=False
                            gate_dispatch._write_dynamic(root,ident,workflow,'scoped_reviews',fresh_scoped)
                    result['pending']+=1
                except Exception as exc:
                    message=str(exc)[:300]
                    try: from tooling.agent_workflow.gate_dispatch import DispatchInputError
                    except ImportError: from gate_dispatch import DispatchInputError
                    if isinstance(exc,DispatchInputError):
                        _block_scoped_input(root,ident,candidate,reviewer,health,workflow,request,exc,now,result)
                    else:
                        result['errors'].append(f'{ident}: scoped review dispatch/collection: {message}'); result['pending']+=1
                continue
            if gate.get('operational_error') or gate.get('operational_state') == 'blocked':
                health.update(state='blocked', checked_at=now, reason=str(gate.get('operational_error') or 'gate admission blocked')[:500]); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
            progress_path = root / 'progress' / (ident + '.json')
            progress = json.loads(progress_path.read_text(encoding='utf-8')) if progress_path.is_file() else {}
            if progress and (progress.get('candidate_id') != ident or progress.get('snapshot_key') != health['snapshot_key']):
                health.update(state='blocked', checked_at=now, reason='progress candidate/snapshot binding mismatch; manual reconciliation required'); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
            admission_expired = now - health.setdefault('admission_started_at', health.get('registered_at') or now) >= 60
            if not sid:
                gate = candidate.get('gate_request') or {}
                if gate.get('creation_unknown') or admission_expired or progress.get('phase') == 'blocked':
                    detail = progress.get('reason') or progress.get('detail') or gate.get('operational_error')
                    health.update(state='blocked', checked_at=now, reason=str(detail or 'admission/session identity unknown; manual reconciliation required')[:500]); _write(hp, health)
                    _notice(root, candidate, health, workflow, request); result['blocked'] += 1
                else:
                    health.update(state='admitting', checked_at=now, reason='lead session creation/admission is in progress'); _write(hp, health)
                    result['pending'] += 1
                continue
            if gate.get('session_id') != sid or gate.get('snapshot_key') != health['snapshot_key']:
                raise ValueError('registered lead/request identity mismatch')
            if gate.get('prompt_admitted') is not True:
                if progress.get('phase') == 'blocked' or gate.get('creation_unknown') or gate.get('operational_error') or admission_expired:
                    detail = progress.get('reason') or progress.get('detail') or gate.get('operational_error') or 'gate prompt was never admitted within the admission grace; manual reconciliation required'
                    try: workflow.verify_gate(candidate)
                    except Exception as exc: detail = 'source-stale: ' + str(exc)[:350] + '; a fresh revision is required'
                    health.update(state='blocked', checked_at=now, reason=str(detail)[:500]); _write(hp, health)
                    _notice(root, candidate, health, workflow, request); result['blocked'] += 1
                else:
                    health.update(state='admitting', checked_at=now, reason='bound lead request is awaiting prompt admission'); _write(hp, health)
                    result['pending'] += 1
                continue
            live_runner = (progress.get('candidate_id') == ident and progress.get('snapshot_key') == health['snapshot_key'] and
                           progress.get('phase') in ('queued', 'running') and pid_alive(progress.get('runner_pid')))
            if live_runner:
                health.update(state='active', checked_at=now, reason='bound gate runner is alive'); _write(hp, health); result['active'] += 1; continue
            try:
                try: import gate_dispatch
                except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
                _, sessraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid))
                activeraw = active_snapshot()
                _, inboxraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid) + '/inbox')
                _, messagesraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid) + '/message?type=assistant&order=desc&limit=10')
                _, permissionsraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid) + '/permission')
            except Exception as exc:
                health.update(state='api_unavailable', checked_at=now, reason=str(exc)[:500]); _write(hp, health); result['pending'] += 1; continue
            session = _data(sessraw); inbox = _data(inboxraw) or []; rows = _data(messagesraw) or []; permissions = _data(permissionsraw) or []
            active_data = _data(activeraw)
            if not isinstance(active_data, dict) or len(active_data) > 64 or any(not isinstance(v, dict) or v.get('type') != 'running' for v in active_data.values()):
                health.update(state='api_unavailable', checked_at=now, reason='active session response malformed; execution state unknown'); _write(hp, health); result['pending'] += 1; continue
            if not isinstance(inbox, list): inbox = inbox.get('items', [])
            if not isinstance(rows, list): rows = rows.get('items', [])
            if not isinstance(permissions, list): permissions = permissions.get('items', [])
            if session.get('agent') != 'integration-lead' or not gate_dispatch._metadata(candidate, session):
                health.update(state='blocked', checked_at=now, reason='lead session metadata/role mismatch; manual reconciliation required'); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
            location = session.get('directory') or (session.get('location') or {}).get('directory')
            central = gate.get('central_worktree')
            if not central or not location or Path(location).resolve() != Path(central).resolve():
                health.update(state='blocked', checked_at=now, reason='lead session central-worktree location mismatch; manual reconciliation required'); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
            if permissions:
                health.update(state='blocked', checked_at=now, reason='required permission approval is pending; author action required'); _write(hp, health); _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
            bound = [m for m in rows if (m.get('metadata') or {}).get('candidate_id') == ident]
            latest = bound[0] if bound else (rows[0] if rows else {})
            activity_advanced = _message_activity(rows, health, now)  # stale messages are observational only; /active/runner proves execution.
            # Retrying an uncertain delivery is still an action: verify source and
            # runner state first, while retaining the exact cached request body.
            pending_recovery = health.get('pending_recovery')
            pending_initial = health.get('pending_initial_steer')
            if (isinstance(pending_recovery, dict) and isinstance(pending_recovery.get('body'), dict)) or (isinstance(pending_initial, dict) and isinstance(pending_initial.get('body'), dict)):
                try: workflow.verify_gate(candidate)
                except Exception as exc:
                    health.update(state='blocked', checked_at=now, reason='source-stale: ' + str(exc)[:400]); _write(hp, health)
                    _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
                if live_runner:
                    health.update(state='active', checked_at=now, reason='bound gate runner is alive; ambiguous delivery deferred'); _write(hp, health); result['active'] += 1; continue
                if pending_recovery:
                    _persist_post(root, hp, health, request, sid, pending_recovery['body'], now, result, recovery=True)
                else:
                    _persist_steer(root, hp, health, request, sid, pending_initial['prompt_id'], pending_initial['body'], now, result)
                continue
            active_ids = {str(key) for key in active_data}
            active_bound = sid in active_ids
            for active_id in active_ids:
                if active_id == sid:
                    active_bound = True; break
                child_status, child_raw = request(None, 'GET', '/api/session/' + urllib.parse.quote(active_id))
                if child_status != 200: raise ValueError('active child session lookup unavailable; execution state unknown')
                child = _data(child_raw)
                if not isinstance(child, dict) or str(child.get('id')) != active_id:
                    raise ValueError('active child session identity mismatch; execution state unknown')
                parent = child.get('parentID') or child.get('parent_id')
                seen = set()
                while parent and str(parent) not in seen and len(seen) < 64:
                    if str(parent) == sid: active_bound = True; break
                    seen.add(str(parent))
                    parent_status, parent_raw = request(None, 'GET', '/api/session/' + urllib.parse.quote(str(parent)))
                    if parent_status != 200: raise ValueError('active parent session lookup unavailable; execution state unknown')
                    parent_session = _data(parent_raw)
                    if not isinstance(parent_session, dict) or str(parent_session.get('id')) != str(parent):
                        raise ValueError('active parent session identity mismatch; execution state unknown')
                    parent = parent_session.get('parentID') or parent_session.get('parent_id')
                if active_bound: break
            if active_bound:
                health.update(state='active', checked_at=now, reason='bound lead or child session is actively executing'); _write(hp, health); result['active'] += 1; continue
            prompt_id = gate.get('prompt_id')
            original = next((m for m in inbox if prompt_id and m.get('id') == prompt_id), None)
            if original and original.get('delivery') in ('queue', 'steer'):
                if health.get('recovery_count', 0) >= 1:
                    steer_ack_at = health.setdefault('initial_steer_at', now)
                    if now - steer_ack_at >= 60:
                        health.update(state='blocked', checked_at=now, reason='original inbox item remains after bounded steer; author action required'); _write(hp, health); _notice(root, candidate, health, workflow, request); result['blocked'] += 1
                    else:
                        health.update(state='observing', checked_at=now, reason='Unconfirmed: original inbox item remains after its bounded steer'); _write(hp, health); result['pending'] += 1
                    continue
                try: workflow.verify_gate(candidate)
                except Exception as exc:
                    health.update(state='blocked', checked_at=now, reason='source-stale: ' + str(exc)[:400]); _write(hp, health)
                    _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
                opayload = original.get('payload') or original
                if (opayload.get('metadata') or {}).get('candidate_id') != ident or (opayload.get('metadata') or {}).get('snapshot_key') != health['snapshot_key']:
                    raise ValueError('original inbox prompt metadata mismatch')
                body = {'delivery': 'steer'}
                health.update(recovery_count=max(1, health.get('recovery_count', 0)), pending_initial_steer={'body': body, 'prompt_id': prompt_id}); _write(hp, health)
                _persist_steer(root, hp, health, request, sid, prompt_id, body, now, result); continue
            finish = latest.get('finish')
            retry = latest.get('retry') or {}
            if isinstance(retry.get('at'), (int, float)) and retry['at'] > now * 1000:
                health.update(state='observing', checked_at=now, retry_at=retry['at'] / 1000, reason='Unconfirmed: provider retry is scheduled'); _write(hp, health); result['pending'] += 1; continue
            session_idle = (session.get('time') or {}).get('idle')
            completed = (latest.get('time') or {}).get('completed')
            terminal_finish = finish in ('stop', 'error', 'length', 'content-filter') or (
                finish == 'tool-calls' and isinstance(session_idle, (int, float)) and
                isinstance(completed, (int, float)) and session_idle >= completed)
            if not terminal_finish:
                idle_since = health.setdefault('idle_since', now)
                age = now - (health.get('recovery_ack_at') or idle_since)
                if age >= 60:
                    health.update(state='blocked', checked_at=now, reason='lead idle without execution evidence after admission grace; author action required'); _write(hp, health); _notice(root, candidate, health, workflow, request); result['blocked'] += 1
                else:
                    if health.get('recovery_count', 0) and health.get('recovery_ack_at') is None:
                        health['recovery_ack_at'] = now
                    health.update(state='observing', checked_at=now, reason='Unconfirmed: no current execution evidence'); _write(hp, health); result['pending'] += 1
                continue
            recovery_messages = [m for m in inbox if ((m.get('metadata') or m.get('payload', {}).get('metadata') or {}).get('workflow_gate_recovery'))]
            try: workflow.verify_gate(candidate)
            except Exception as exc:
                health.update(state='blocked', checked_at=now, reason='source-stale: ' + str(exc)[:400]); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1; continue
            terminal = latest.get('id')
            if health.get('recovery_count', 0) < 1:
                mid = 'msg_' + hashlib.sha256(f"{ident}\0{health['snapshot_key']}\0{sid}\0recovery".encode()).hexdigest()[:32]
                central = gate['central_worktree']
                asset_workspace = str(root.parents[1].resolve())
                text = (f'Continue this same gate review from the saved stage; do not rerun checks with valid receipts or create reviewer sessions. Reuse valid receipts and read the existing report. Use ms14_workflow_status only for explicit inspection/recovery; if no valid receipt exists, continue required checks, then finish this candidate using ms14_workflow_finish with the same lead session ID, candidate ID, result and findings. State the precise blocker when unable to proceed.')
                body = {'id': mid, 'text': text, 'delivery': 'steer', 'resume': True,
                        'metadata': {'workflow_gate_recovery': True, 'candidate_id': ident, 'snapshot_key': health['snapshot_key']}}
                health.update(recovery_count=1, pending_recovery={'body': body}, last_terminal_id=terminal)
                _write(hp, health)
                _persist_post(root, hp, health, request, sid, body, now, result, recovery=True)
            elif health.get('recovery_count', 0) >= 1 and terminal == health.get('last_terminal_id'):
                if now - (health.get('recovery_ack_at') or health.get('checked_at') or now) >= 60:
                    health.update(state='blocked', checked_at=now, reason='recovery acknowledged but no model execution followed; author action required'); _write(hp, health); _notice(root, candidate, health, workflow, request); result['blocked'] += 1
                else:
                    health.setdefault('recovery_ack_at', now)
                    health.update(state='observing', checked_at=now, reason='Unconfirmed: recovery acknowledged; model execution has not been observed'); _write(hp, health); result['pending'] += 1
            else:
                observed_after_recovery = (health.get('recovery_ack_at') is not None and
                    health.get('last_activity') is not None and health['last_activity'] > health['recovery_ack_at'])
                reason = ('lead execution was observed after recovery, then stopped without a verdict; author action required'
                          if observed_after_recovery else 'lead stopped after bounded recovery; author action required')
                health.update(state='blocked', checked_at=now, last_terminal_id=terminal, reason=reason); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1
        except Exception as exc:
            result['errors'].append(str(exc)[:500])
            if health and health.get('state') not in ('finished', 'blocked', 'superseded'): result['pending'] += 1
        finally:
            if lock is not None: lock.close()
    # Surface persisted notification obligations even when retry is in backoff.
    for ident, hp in health_paths.items():
        try: pending_health = read_health(root, ident) or {}
        except Exception: continue
        completion_error = pending_health.get('completion_retry_error')
        if pending_health.get('completion_delivery_pending') and completion_error:
            result['errors'].append(f'{ident}: author completion delivery pending: {completion_error}')
            result['pending'] += 1
        for field in ('notice_error', 'notice_delivery_error'):
            if pending_health.get(field):
                result['errors'].append(f'{ident}: author blocked notice {field}: {pending_health[field]}')
    return result


def _persist_post(root, hp, health, request, sid, body, now, result, recovery):
    if recovery:
        health.update(state='recovering', pending_recovery={'body': body}, checked_at=now); _write(hp, health)
    else:
        health.update(state='steering', pending_initial_steer={'body': body}, checked_at=now); _write(hp, health)
    try:
        _, response = request(None, 'POST', '/api/session/' + urllib.parse.quote(sid) + '/prompt', body)
        data = _data(response); payload = data.get('payload') if isinstance(data, dict) else None
        if not isinstance(data, dict) or data.get('id') != body['id'] or data.get('sessionID') != sid or not isinstance(payload, dict) or payload.get('text') != body['text'] or payload.get('metadata') != body['metadata']:
            raise ValueError('prompt API response payload identity mismatch')
        if recovery: health.update(state='recovering', pending_recovery=None, recovery_receipt=payload, recovery_ack_at=now)
        else: health.update(state='active', pending_initial_steer=None, initial_steer_id=body['id'], last_post_receipt=payload)
        health['checked_at'] = now; _write(hp, health); result['recovered'] += 1
    except Exception as exc:
        health.update(state='api_unavailable', checked_at=now, reason=('recovery' if recovery else 'initial steer') + ' POST uncertain: ' + str(exc)[:350]); _write(hp, health); result['pending'] += 1


def _persist_steer(root, hp, health, request, sid, prompt_id, body, now, result):
    health.update(state='steering', pending_initial_steer={'body': body, 'prompt_id': prompt_id}, checked_at=now); _write(hp, health)
    try:
        status, response = request(None, 'PATCH', '/api/session/' + urllib.parse.quote(sid) + '/inbox/' + urllib.parse.quote(prompt_id), body)
        if status != 204:
            data = _data(response); payload = data.get('payload', data) if isinstance(data, dict) else None
            if not isinstance(payload, dict) or payload.get('id') != prompt_id or payload.get('delivery') != 'steer':
                raise ValueError('inbox steer acknowledgement mismatch')
        health.update(state='observing', pending_initial_steer=None, initial_steer_id=prompt_id,
                      checked_at=now, initial_steer_at=now, reason='Unconfirmed: inbox steer acknowledged; model execution not yet observed')
        _write(hp, health); result['recovered'] += 1
    except Exception as exc:
        health.update(state='api_unavailable', checked_at=now, reason='initial inbox steer uncertain: ' + str(exc)[:350]); _write(hp, health); result['pending'] += 1


def _block_scoped_input(root,ident,candidate,reviewer,health,workflow,request,exc,now,result):
    detail='scoped review input/identity is blocked: '+str(exc)[:300]
    try:
        try: import gate_dispatch
        except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
        gate_dispatch._write_bound_candidate(root,ident,workflow,candidate,
            lambda latest: _block_scoped_record(latest,reviewer,health['snapshot_key'],detail))
    except Exception as persist_exc:
        result['errors'].append(f'{ident}: scoped review blocker persistence: {str(persist_exc)[:200]}')
        result['pending']+=1; return
    health.update(state='blocked',checked_at=now,reason=detail,
                  scoped_review_error_delivery={'reviewer':reviewer,'reason':detail})
    _write(_path(root,ident),health); _scoped_review_error_delivery(root,candidate,health,request,now)
    try:
        import gate_progress
        gate_progress.update(root,candidate,'blocked',detail=detail)
    except Exception: pass
    _notice(root,candidate,health,workflow,request); result['blocked']+=1


def _block_scoped_record(candidate, reviewer, snapshot, reason):
    record=(candidate.get('scoped_reviews') or {}).get(reviewer)
    if not record or record.get('snapshot_key')!=snapshot:
        raise ValueError('scoped review record binding changed')
    record.update(dispatch_pending=False,collection_pending=False,lead_continuation_pending=False,
                  operational_state='blocked',operational_error=str(reason)[:1000])
    candidate['scoped_reviews'][reviewer]=record


def _scoped_review_error_delivery(root,candidate,health,request,now):
    """Deliver one deterministic operational finding to the exact lead session."""
    key='scoped_review_error_delivery'; delivery=health.get(key) or {}
    if delivery.get('acknowledged'):
        error=_observe_author_delivery(root,candidate,health,
            {'id':delivery.get('body',{}).get('id'),'origin_session_id':health.get('session_id'),'status':'delivered'},
            key,request,now)
        return error
    body=delivery.get('body')
    if not body:
        reviewer=delivery.get('reviewer','review'); reason=delivery.get('reason','scoped review operationally blocked')
        mid='msg_'+hashlib.sha256((candidate['id']+'\0'+candidate['snapshot_key']+'\0'+reviewer+'\0'+reason).encode()).hexdigest()[:40]
        body={'id':mid,'text':f'Scoped {reviewer} review for {candidate["id"]} is operationally blocked: {reason}. No independent verdict was produced. Do not create another reviewer for this bound operation.',
              'delivery':'steer','resume':True,'metadata':{'workflow_gate_scoped_review_error':True,
              'candidate_id':candidate['id'],'snapshot_key':candidate['snapshot_key'],'reviewer':reviewer}}
        delivery['body']=body; health[key]=delivery; _write(_path(root,candidate['id']),health)
    sid=health.get('session_id')
    if not sid: return 'scoped review error cannot be delivered without the bound lead session'
    try:
        status,response=request(None,'POST','/api/session/'+urllib.parse.quote(sid)+'/prompt',body)
        data=_data(response); payload=data.get('payload') if isinstance(data,dict) else None
        if status!=200 or not isinstance(data,dict) or data.get('id')!=body['id'] or data.get('sessionID')!=sid or not isinstance(payload,dict) or payload.get('text')!=body['text'] or payload.get('metadata')!=body['metadata']:
            raise ValueError('scoped review operational notice acknowledgement mismatch')
        delivery.update(acknowledged=True,acknowledged_at=now,body=body); health[key]=delivery
        _write(_path(root,candidate['id']),health)
        return _observe_author_delivery(root,candidate,health,
            {'id':body['id'],'origin_session_id':sid,'status':'delivered'},key,request,now)
    except Exception as exc:
        delivery.update(error=str(exc)[:300],body=body); health[key]=delivery
        _write(_path(root,candidate['id']),health)
        return str(exc)[:300]


def _notice(root, candidate, health, workflow, request):
    now = time.time()
    receipt = health.get('author_notice')
    if isinstance(receipt, dict) and receipt.get('status') == 'delivered':
        error = _observe_author_delivery(root, candidate, health, receipt, 'author_notice_delivery', request, now)
        if error:
            health['notice_delivery_error'] = error
        else:
            health.pop('notice_delivery_error', None)
        _write(_path(root, candidate['id']), health)
        return
    if now < (health.get('notice_retry_at') or 0): return
    try:
        try: import gate_dispatch
        except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
        receipt = gate_dispatch.deliver_gate_block_notice(root, candidate, health, workflow=workflow, request=request)
        health['author_notice'] = receipt; _write(_path(root, candidate['id']), health)
        if isinstance(receipt, dict) and receipt.get('status') == 'delivered':
            health.pop('notice_error', None); health.pop('notice_retry_at', None)
            error = _observe_author_delivery(root, candidate, health, receipt, 'author_notice_delivery', request, time.time())
            if error: health['notice_delivery_error'] = error
            else: health.pop('notice_delivery_error', None)
            _write(_path(root, candidate['id']), health)
        else:
            status = receipt.get('status') if isinstance(receipt, dict) else 'invalid response'
            reason = receipt.get('reason') or receipt.get('error') if isinstance(receipt, dict) else None
            health.update(notice_error=str(reason or f'blocked notice delivery returned {status}')[:400], notice_retry_at=time.time() + 30)
            _write(_path(root, candidate['id']), health)
    except Exception as exc:
        health.update(notice_error=str(exc)[:400], notice_retry_at=time.time() + 30); _write(_path(root, candidate['id']), health)


def _completion_failure(path, health, ident, reason, now):
    health.update(state='finished', completion_delivery_pending=True,
                  completion_retry_error=str(reason)[:350], completion_retry_at=now + 30)
    _write(path, health)


def _completion_success(path, health):
    health.pop('completion_retry_error', None)
    health.pop('completion_retry_at', None)
    health['completion_delivery_pending'] = False
    _write(path, health)


def _observe_author_delivery(root, candidate, health, receipt, key, request, now):
    """Observe/wake only the exact acknowledged inbox item, steering at most once."""
    sid, msg_id = receipt.get('origin_session_id'), receipt.get('id')
    progress = health.get(key) or {}
    if not sid or not msg_id or progress.get('execution_observed_at'):
        return None
    try:
        _, active_raw = request(None, 'GET', '/api/session/active')
        active = _data(active_raw)
        if not isinstance(active, dict) or len(active) > 64 or any(not isinstance(v, dict) for v in active.values()):
            raise ValueError('active session response malformed; author execution unknown')
        if sid in active:
            progress.update(state='execution_observed', execution_observed_at=now,
                            evidence='bound delivery target session active; message consumption is not implied')
            health[key] = progress; _write(_path(root, candidate['id']), health); return None
        _, inbox_raw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid) + '/inbox')
        inbox = _data(inbox_raw)
        if isinstance(inbox, dict): inbox = inbox.get('items', [])
        if not isinstance(inbox, list): raise ValueError('author inbox response malformed')
        item = next((m for m in inbox if isinstance(m, dict) and m.get('id') == msg_id), None)
        if not item:
            admitted_at = progress.setdefault('admitted_at', now)
            progress.update(state='admitted', evidence='completion API acknowledged; exact item no longer pending; consumption unknown')
            if now - admitted_at >= 60:
                progress.update(state='delivery_blocked', error='acknowledged author item is no longer pending but execution was not observed; action required', checked_at=now)
                health[key] = progress; _write(_path(root, candidate['id']), health)
                return progress['error']
        elif not progress.get('wake_attempted'):
            status, _ = request(None, 'PATCH', '/api/session/' + urllib.parse.quote(sid) + '/inbox/' + urllib.parse.quote(msg_id), {'delivery': 'steer'})
            if status != 204:
                data = _data(_)
                payload = data.get('payload', data) if isinstance(data, dict) else None
                if not isinstance(payload, dict) or payload.get('id') != msg_id or payload.get('delivery') != 'steer':
                    raise ValueError('exact author inbox steer acknowledgement mismatch')
            progress.update(state='steered', wake_attempted=True, wake_at=now,
                            evidence='exact acknowledged completion inbox item steered; consumption not implied')
        elif now - (progress.get('wake_at') or now) >= 60:
            progress.update(state='delivery_blocked', error='author session stayed idle after the one bounded steer; action required', checked_at=now)
            health[key] = progress; _write(_path(root, candidate['id']), health)
            return progress['error']
        health[key] = progress; _write(_path(root, candidate['id']), health)
        return None
    except Exception as exc:
        progress.update(state='unknown', error=str(exc)[:350], checked_at=now)
        health[key] = progress; _write(_path(root, candidate['id']), health)
        return f'{key}: {str(exc)[:300]}'
