"""Bounded operational recovery for admitted coding gates (never a verdict authority)."""
import hashlib
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


def register(root, candidate, *, lead_session_id, workflow=None):
    if not lead_session_id: raise ValueError('lead session id required')
    ident = candidate['id']; target = _path(root, ident)
    lock = _lock(root, ident, workflow)
    try:
        if target.is_file():
            old = json.loads(target.read_text(encoding='utf-8'))
            if (old.get('candidate_id'), old.get('snapshot_key'), old.get('session_id')) != (ident, candidate['snapshot_key'], lead_session_id):
                raise ValueError('existing liveness registration is bound to another candidate/session')
            return old
        record = {'candidate_id': ident, 'snapshot_key': candidate['snapshot_key'], 'session_id': lead_session_id,
                  'state': 'observing', 'reason': None, 'checked_at': None, 'registered_at': time.time(),
                  'recovery_count': 0, 'last_activity': None, 'last_activity_signature': None,
                  'observed_change_at': None}
        _write(target, record); return record
    finally: lock.close()


def read_health(root, ident):
    path = _path(root, ident)
    return json.loads(path.read_text(encoding='utf-8')) if path.is_file() else None


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
    if not folder.is_dir(): return result
    for hp in sorted(folder.glob('*.json')):
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
            outcome = candidate.get('gate_outcome') or {}
            if outcome.get('result') in ('needs_changes', 'ready_for_owner'):
                health.update(state='finished', checked_at=now, reason='candidate has persisted gate outcome'); _write(hp, health); continue
            if health.get('state') in ('finished', 'blocked', 'superseded'):
                if health.get('state') == 'blocked':
                    _notice(root, candidate, health, workflow, request); result['blocked'] += 1
                continue
            gate = candidate.get('gate_request') or {}; sid = health.get('session_id')
            if gate.get('session_id') != sid or gate.get('snapshot_key') != health['snapshot_key'] or gate.get('prompt_admitted') is not True: raise ValueError('registered lead/request identity mismatch')
            try:
                try: import gate_dispatch
                except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
                _, sessraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid))
                _, inboxraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid) + '/inbox')
                _, messagesraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid) + '/message?type=assistant&order=desc&limit=10')
                _, permissionsraw = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid) + '/permission')
            except Exception as exc:
                health.update(state='api_unavailable', checked_at=now, reason=str(exc)[:500]); _write(hp, health); result['pending'] += 1; continue
            session = _data(sessraw); inbox = _data(inboxraw) or []; rows = _data(messagesraw) or []; permissions = _data(permissionsraw) or []
            if not isinstance(inbox, list): inbox = inbox.get('items', [])
            if not isinstance(rows, list): rows = rows.get('items', [])
            if not isinstance(permissions, list): permissions = permissions.get('items', [])
            if session.get('agent') != 'integration-lead' or not gate_dispatch._metadata(candidate, session): raise ValueError('lead session metadata/role mismatch')
            location = session.get('directory') or (session.get('location') or {}).get('directory')
            central = gate.get('central_worktree')
            if not central or not location or Path(location).resolve() != Path(central).resolve(): raise ValueError('lead session central-worktree location mismatch')
            if permissions:
                health.update(state='permission_wait', checked_at=now, reason='lead has a pending permission request'); _write(hp, health); result['pending'] += 1; continue
            try:
                import owner_attention
            except ModuleNotFoundError: from tooling.agent_workflow import owner_attention
            heads = owner_attention.current_work(root, workflow=workflow)
            if any(x.get('worktree', '').casefold() == str(Path(candidate['worktree']['worktree']).resolve()).casefold() and
                   x.get('sprint_id') == candidate.get('sprint_id') and x.get('head') is not None and
                   x['head'].get('id') != ident for x in heads):
                health.update(state='superseded', checked_at=now, reason='a newer owner-attention head supersedes this candidate'); _write(hp, health); continue
            bound = [m for m in rows if (m.get('metadata') or {}).get('candidate_id') == ident]
            latest = bound[0] if bound else (rows[0] if rows else {})
            activity_advanced = _message_activity(rows, health, now)
            progress_path = root / 'progress' / (ident + '.json')
            progress = json.loads(progress_path.read_text(encoding='utf-8')) if progress_path.is_file() else {}
            live_runner = (progress.get('candidate_id') == ident and progress.get('snapshot_key') == health['snapshot_key'] and
                           progress.get('phase') in ('queued', 'running') and pid_alive(progress.get('runner_pid')))
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
            if live_runner:
                health.update(state='active', checked_at=now, reason='bound gate runner is alive'); _write(hp, health); result['active'] += 1; continue
            prompt_id = gate.get('prompt_id')
            original = next((m for m in inbox if prompt_id and m.get('id') == prompt_id), None)
            if original and original.get('delivery') in ('queue', 'steer'):
                if health.get('recovery_count', 0) >= 1:
                    health.update(state='observing', checked_at=now, reason='Unconfirmed: original inbox item remains after its bounded steer'); _write(hp, health); result['pending'] += 1; continue
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
                if activity_advanced:
                    health.update(state='active', checked_at=now, reason='fresh assistant/tool activity observed'); _write(hp, health); result['active'] += 1
                else:
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
                text = (f'Continue this same gate review from the saved stage; do not rerun checks with valid receipts or create reviewer sessions. Read JSON with the permitted file-read tool and list files with glob. From the neutral 05 directory `{central}`, run exactly one permitted Python workflow CLI command per shell call; for example: `python tooling/agent_workflow/workflow.py --asset-workspace "{asset_workspace}" gate-preflight {ident}`. Do not use Get-Content/Get-ChildItem or compound shell commands. Reuse valid existing receipts and read the existing report rather than rerunning tests. Persist the candidate-bound verdict or state the precise blocker.')
                body = {'id': mid, 'text': text, 'delivery': 'steer', 'resume': True,
                        'metadata': {'workflow_gate_recovery': True, 'candidate_id': ident, 'snapshot_key': health['snapshot_key']}}
                health.update(recovery_count=1, pending_recovery={'body': body}, last_terminal_id=terminal)
                _write(hp, health)
                _persist_post(root, hp, health, request, sid, body, now, result, recovery=True)
            elif health.get('recovery_count', 0) >= 1 and terminal == health.get('last_terminal_id'):
                health.update(state='observing', checked_at=now, reason='Unconfirmed: recovery acknowledged; model execution has not been observed'); _write(hp, health); result['pending'] += 1
            else:
                health.update(state='blocked', checked_at=now, last_terminal_id=terminal, reason='lead stopped after bounded recovery; author action required'); _write(hp, health)
                _notice(root, candidate, health, workflow, request); result['blocked'] += 1
        except Exception as exc:
            result['errors'].append(str(exc)[:500])
            if health and health.get('state') not in ('finished', 'blocked', 'superseded'): result['pending'] += 1
        finally:
            if lock is not None: lock.close()
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
        if recovery: health.update(state='recovering', pending_recovery=None, recovery_receipt=payload)
        else: health.update(state='active', pending_initial_steer=None, initial_steer_id=body['id'])
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
                      checked_at=now, reason='Unconfirmed: inbox steer acknowledged; model execution not yet observed')
        _write(hp, health); result['recovered'] += 1
    except Exception as exc:
        health.update(state='api_unavailable', checked_at=now, reason='initial inbox steer uncertain: ' + str(exc)[:350]); _write(hp, health); result['pending'] += 1


def _notice(root, candidate, health, workflow, request):
    receipt = health.get('author_notice')
    if isinstance(receipt, dict) and receipt.get('status') == 'delivered': return
    try:
        try: import gate_dispatch
        except ModuleNotFoundError: from tooling.agent_workflow import gate_dispatch
        receipt = gate_dispatch.deliver_gate_block_notice(root, candidate, health, workflow=workflow, request=request)
        health['author_notice'] = receipt; _write(_path(root, candidate['id']), health)
    except Exception as exc:
        health['notice_error'] = str(exc)[:400]; _write(_path(root, candidate['id']), health)
