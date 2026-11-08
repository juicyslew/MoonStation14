"""Small read-only reconciler for ordinary owner replies in review sessions."""
import argparse
import importlib.util
import json
import os
import subprocess
import sys
import time
import urllib.parse
from pathlib import Path

try:
    import owner_attention
except ModuleNotFoundError:
    from tooling.agent_workflow import owner_attention


def _load_dispatch():
    for module_name in ('gate_dispatch', 'tooling.agent_workflow.gate_dispatch'):
        try: return __import__(module_name, fromlist=['_request'])
        except ModuleNotFoundError: pass
    base = Path(__file__).resolve().parent
    shared = base.parents[2] / 'MoonStation14-shared' / 'tooling' / 'agent_workflow' / 'gate_dispatch.py'
    local = base / 'gate_dispatch.py'
    target = shared if shared.is_file() else local
    if not target.is_file(): raise ImportError('shared gate_dispatch runtime unavailable')
    spec = importlib.util.spec_from_file_location('_owner_feedback_gate_dispatch', target)
    module = importlib.util.module_from_spec(spec); sys.modules[spec.name] = module; spec.loader.exec_module(module)
    return module


w = owner_attention._load_workflow()
gate_dispatch = _load_dispatch()
MAX_REPLY = getattr(gate_dispatch, 'MAX_REPLY', 64 * 1024)

INTERVAL = 10
IDLE_GRACE = 60
MAX_PAGES = 8
PAGE_SIZE = 20
AUTOMATION_FLAGS = {'workflow_gate_completion', 'review_fixture', 'automation', 'system', 'reminder', 'tool_result', 'manual_recovery', 'scoped_review'}


def _api(request, method, path):
    status, body = request(None, method, path, timeout=12)
    if status != 200 or not isinstance(body, dict):
        raise ValueError(f'OpenCode {method} response unavailable')
    data = body.get('data')
    if not isinstance(data, list): raise ValueError('OpenCode message response is not a list')
    return data


def _session(request, sid):
    status, body = request(None, 'GET', '/api/session/' + urllib.parse.quote(sid, safe=''), timeout=12)
    data = body.get('data', body) if isinstance(body, dict) else None
    if status != 200 or not isinstance(data, dict): raise ValueError('OpenCode session lookup unavailable')
    return data


def _normalize_user_message(row, *, inbox=False):
    if not isinstance(row, dict) or row.get('type') != 'user' or not isinstance(row.get('id'), str): return None
    stamp = row.get('time', {}).get('created') if isinstance(row.get('time'), dict) else None
    if inbox:
        payload = row.get('payload') if isinstance(row.get('payload'), dict) else {}
        text, metadata = payload.get('text', ''), payload.get('metadata') or {}
    else:
        text, metadata = row.get('text', ''), row.get('metadata') or {}
    return {'type': 'user', 'id': row['id'], 'text': text, 'metadata': metadata, 'created': stamp}


def _message_by_id(request, sid, message_id):
    escaped_sid = urllib.parse.quote(sid, safe='')
    escaped_message = urllib.parse.quote(message_id, safe='')
    status, body = request(None, 'GET', f'/api/session/{escaped_sid}/message/{escaped_message}', timeout=12)
    data = body.get('data', body) if isinstance(body, dict) else None
    if status != 200 or not isinstance(data, dict): raise ValueError('OpenCode owner notice lookup unavailable')
    message = _normalize_user_message(data)
    return [message] if message else []


def _messages(request, sid, earliest=None, *, max_pages=MAX_PAGES, require_boundary=True):
    escaped = urllib.parse.quote(sid, safe='')
    inbox = _api(request, 'GET', f'/api/session/{escaped}/inbox')
    seen = {}
    for row in inbox:
        message = _normalize_user_message(row, inbox=True)
        if message: seen[message['id']] = message
    cursor = None
    for _ in range(max_pages):
        query = {'type': 'user', 'limit': PAGE_SIZE}
        if cursor: query['cursor'] = cursor
        else: query['order'] = 'desc'
        path = f'/api/session/{escaped}/message?' + urllib.parse.urlencode(query)
        status, body = request(None, 'GET', path, timeout=12)
        if status != 200 or not isinstance(body, dict) or not isinstance(body.get('data'), list):
            raise ValueError('OpenCode projected messages unavailable')
        rows = body['data']
        for row in rows:
            message = _normalize_user_message(row)
            if message: seen.setdefault(message['id'], message)
        stamps = [r.get('time', {}).get('created') for r in rows if isinstance(r, dict) and isinstance(r.get('time'), dict)]
        if earliest is not None and stamps and max(stamps) / 1000 <= earliest: break
        paging = body.get('cursor') or {}
        cursor = paging.get('next') if isinstance(paging, dict) else None
        if not cursor: break
    else:
        if cursor and require_boundary: raise ValueError('owner feedback history exceeded bounded pagination before request boundary')
    return list(seen.values())


def _eligible(message):
    metadata = message.get('metadata') or {}
    if not isinstance(metadata, dict): return False
    # Metadata-bearing messages are excluded unless future schema explicitly identifies safe owner UI metadata.
    if metadata: return False
    return isinstance(message.get('text'), str) and bool(message['text'].strip())


def discover_legacy_request(candidate, messages):
    """Return a candidate-bound durable ready notice, never infer time from file metadata."""
    receipt = candidate.get('author_completion') or {}
    gate = candidate.get('gate_request') or {}
    if candidate.get('gate_outcome', {}).get('result') != 'ready_for_owner' or not gate.get('origin_session_id'):
        return None
    found = []
    for m in messages:
        md = m.get('metadata') or {}
        exact = (md.get('candidate_id') == candidate.get('id') and md.get('snapshot_key') == candidate.get('snapshot_key') and
                 md.get('result') == 'ready_for_owner' and isinstance(m.get('created'), (int, float)))
        completion = (md.get('workflow_gate_completion') is True and m.get('id') == receipt.get('id') and
                      receipt.get('status') == 'delivered' and receipt.get('result') == 'ready_for_owner' and
                      receipt.get('origin_session_id') == gate.get('origin_session_id'))
        recovery = md.get('manual_recovery') is True
        if exact and (completion or recovery): found.append(m)
    if not found: return None
    notice = max(found, key=lambda m: m['created'])
    return {'candidate_id': candidate['id'], 'snapshot_key': candidate['snapshot_key'],
            'origin_session_id': gate['origin_session_id'], 'requested_at': notice['created'] / 1000,
            'announcement_message_id': notice['id'],
            'verified_by': 'owner_feedback_observer', 'is_current': True}


def reconcile(root, workflow=None, request=gate_dispatch._request, *, now=None, refresh=False, render=True):
    """Reconcile currently awaiting owner requests. No session creation or model-output reads."""
    root = Path(root); workflow = workflow or w; now = time.time() if now is None else now
    stats = {'sessions': 0, 'acknowledged': 0, 'ignored': 0, 'ambiguous': 0, 'errors': []}
    current_work_fn = getattr(owner_attention, 'current_work', None)
    if current_work_fn is None:
        if not (root / 'candidates').exists() or not any((root / 'candidates').glob('*.json')):
            current_work = []
        else:
            raise ImportError('owner_attention.current_work is required for safe head selection')
    else:
        current_work = current_work_fn(root, workflow=workflow)
    try: prior = workflow.readjson(root / 'owner-feedback-observer.json')
    except (ValueError, OSError): prior = {}
    attempted_legacy = prior.get('legacy_attempted', {}) if isinstance(prior.get('legacy_attempted', {}), dict) else {}
    candidates, legacy_candidates = [], []
    for item in current_work:
        head = item.get('head')
        if isinstance(head, dict):
            review = head.get('owner_review') or {}
            state = review.get('state')
            if head.get('owner_decision') or head.get('gate_outcome', {}).get('result') == 'needs_changes' or state in ('feedback_received', 'completed'):
                continue
            if state == 'awaiting_owner': candidates.append(head)
            elif head.get('gate_outcome', {}).get('result') == 'ready_for_owner' and state in (None, 'unconfirmed'):
                legacy_candidates.append(head)
        elif head is None:
            legacy_candidates.extend(item.get('legacy_candidates') or [])
    legacy_candidates = [c for c in legacy_candidates if not c.get('owner_decision') and
        c.get('gate_outcome', {}).get('result') == 'ready_for_owner' and
        (c.get('owner_review') or {}).get('state') not in ('feedback_received', 'completed') and
        attempted_legacy.get(c.get('id')) != c.get('snapshot_key')]
    candidates.extend(legacy_candidates)
    by_session = {}
    for c in candidates:
        sid = (c.get('owner_review') or {}).get('origin_session_id') or (c.get('gate_request') or {}).get('origin_session_id')
        if sid: by_session.setdefault(sid, []).append(c)
    for c in legacy_candidates: attempted_legacy[c['id']] = c.get('snapshot_key')
    stats['pending_owner'] = sum(1 for c in candidates
        if (c.get('owner_review') or {}).get('state') == 'awaiting_owner')
    if len(attempted_legacy) > 256:
        attempted_legacy = dict(list(attempted_legacy.items())[-256:])
    for sid, group in by_session.items():
        try:
            session = _session(request, sid)
            location = session.get('location') or {}
            actual_root = str(Path(location.get('directory', '')).resolve()).casefold()
            actual_agent = session.get('agent')
            if not isinstance(actual_agent, str) or not actual_agent.strip() or actual_agent == 'integration-lead':
                stats['errors'].append({'session_id': sid, 'error': 'origin session is not an eligible author session'}); continue
            valid = []
            for c in group:
                if actual_root != str(Path(c['worktree']['worktree']).resolve()).casefold():
                    stats['errors'].append({'session_id': sid, 'error': f"{c['id']}: origin session worktree mismatch"}); continue
                valid.append(c)
            if not valid: continue
            stats['sessions'] += 1
            # Lifecycle current_work has already selected every current head from all queue records.
            legacy_proofs = {}
            legacy_without_exact_receipt = []
            for c in valid:
                if (c.get('owner_review') or {}).get('state') == 'awaiting_owner': continue
                receipt = c.get('author_completion') or {}
                gate = c.get('gate_request') or {}
                exact_receipt = (receipt.get('status') == 'delivered' and receipt.get('result') == 'ready_for_owner' and
                    receipt.get('origin_session_id') == sid and gate.get('origin_session_id') == sid and
                    isinstance(receipt.get('id'), str) and bool(receipt['id']))
                notice_rows = _message_by_id(request, sid, receipt['id']) if exact_receipt else []
                key = (str(Path(c['worktree']['worktree']).resolve()).casefold(), c.get('sprint_id'))
                request_info = discover_legacy_request(c, notice_rows)
                if request_info: legacy_proofs.setdefault(key, []).append((c, request_info))
                else: legacy_without_exact_receipt.append(c)
            if legacy_without_exact_receipt:
                # Manual recovery has no saved message ID: inspect only the recent bounded window.
                recent = _messages(request, sid, max_pages=1, require_boundary=False)
                for c in legacy_without_exact_receipt:
                    key = (str(Path(c['worktree']['worktree']).resolve()).casefold(), c.get('sprint_id'))
                    request_info = discover_legacy_request(c, recent)
                    if request_info: legacy_proofs.setdefault(key, []).append((c, request_info))
            for key, proofs in legacy_proofs.items():
                if len(proofs) > 1:
                    newest = max(proof['requested_at'] for _, proof in proofs)
                    selected = [(c, proof) for c, proof in proofs if proof['requested_at'] == newest]
                    if len(selected) != 1:
                        stats['errors'].append({'session_id': sid, 'error': 'legacy owner request head ambiguous; announcement timestamps tie'})
                        continue
                    proofs = selected
                for c, request_info in proofs:
                    try:
                        owner_attention.register_request(root, c, requested_at=request_info['requested_at'],
                            origin_session_id=sid, workflow=workflow, legacy_head=request_info)
                    except ValueError:
                        # Strict source verifier rejected stale evidence; lifecycle queue owns its unconfirmed status.
                        pass
                    except Exception as exc:
                        stats['errors'].append({'session_id': sid, 'error': f"{c['id']}: legacy registration failed: {str(exc)[:180]}"})
            # Refresh group after possible request hydration so feedback is matched against current requests.
            refreshed = []
            for c in valid:
                try:
                    latest = workflow.readjson(Path(root) / 'candidates' / (c['id'] + '.json'))
                    if (latest.get('owner_review') or {}).get('state') == 'awaiting_owner': refreshed.append(latest)
                except (ValueError, OSError): pass
            if not refreshed: continue
            valid = refreshed
            earliest = min(c['owner_review'].get('requested_at', 0) for c in valid)
            messages = _messages(request, sid, earliest)
            for m in messages:
                if not _eligible(m) or not isinstance(m.get('created'), (int, float)): continue
                created = m['created'] / 1000
                evidence = {'type': 'user', 'id': m['id'], 'text': m.get('text', ''),
                            'metadata': m.get('metadata') or {}, 'time': created, 'session_id': sid}
                result = owner_attention.acknowledge_feedback(root, sid, m['id'], created,
                    workflow=workflow, evidence=evidence)
                stats['acknowledged' if result.get('status') == 'acknowledged' else 'ambiguous' if result.get('status') == 'ambiguous' else 'ignored'] += 1
        except Exception as exc:
            stats['errors'].append({'session_id': sid, 'error': str(exc)[:300]})
            for c in group:
                if (c.get('owner_review') or {}).get('state') != 'awaiting_owner':
                    attempted_legacy.pop(c.get('id'), None)
    try:
        groups = owner_attention.collect(root, workflow=workflow, refresh=refresh)
        stats['queue'] = {key: len(value) for key, value in groups.items()}
    except Exception as exc:
        stats['errors'].append({'session_id': '', 'error': f'owner queue refresh failed: {str(exc)[:250]}'})
    def save_health():
        health = {k: v for k, v in stats.items() if k != 'errors'}
        health.update(updated_at=now, last_checked=now, status='error' if stats['errors'] else 'ok',
                      errors=[e['error'] for e in stats['errors']], pid=os.getpid(), legacy_attempted=attempted_legacy)
        workflow.writejson(root / 'owner-feedback-observer.json', health)
    try: save_health()
    except Exception: stats['errors'].append({'session_id': '', 'error': 'observer health write failed'})
    if render:
      try:
        try: import gate_progress
        except ModuleNotFoundError: from tooling.agent_workflow import gate_progress
        gate_progress.render(root)
      except Exception as exc:
        stats['errors'].append({'session_id': '', 'error': f'board render failed: {str(exc)[:200]}'})
        try: save_health()
        except Exception: pass
    return stats


def ensure_started(root, workflow_path=None):
    """Compatibility entry point; the shared observer owns the only process."""
    workflow_observer = _load_observer()
    return workflow_observer.ensure_started(root)


def watch(root, request=gate_dispatch._request, *, interval=INTERVAL, idle_grace=IDLE_GRACE, clock=time.monotonic, sleep=time.sleep):
    workflow_observer = _load_observer()
    return workflow_observer.watch(root, request=request, clock=clock, sleep=sleep)


def _load_observer():
    if __package__:
        from importlib import import_module
        return import_module('.workflow_observer', __package__)
    import workflow_observer
    return workflow_observer


def main(argv=None):
    p = argparse.ArgumentParser(); p.add_argument('--exchange', type=Path, required=True); mode = p.add_mutually_exclusive_group(required=True); mode.add_argument('--once', action='store_true'); mode.add_argument('--watch', action='store_true'); mode.add_argument('--ensure-started', action='store_true')
    a = p.parse_args(argv)
    try:
        if a.ensure_started:
            proc = ensure_started(a.exchange)
            print(json.dumps({'status': 'started' if proc else 'unavailable', 'pid': getattr(proc, 'pid', None)}))
            return 0 if proc else 1
        if a.once:
            stats = reconcile(a.exchange, refresh=True)
            print(json.dumps(stats)); return 1 if stats['errors'] else 0
        return watch(a.exchange)
    except (BlockingIOError, TimeoutError): return 0


if __name__ == '__main__': raise SystemExit(main())
