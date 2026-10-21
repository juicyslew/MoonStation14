"""Read-only OpenCode preflight for serialized legacy workflow cutover."""
import urllib.parse
import re
from pathlib import Path

try:
    from tooling.agent_workflow import gate_dispatch, workflow_review_history
except ImportError:
    import gate_dispatch
    import workflow_review_history


WORKFLOW_PREFIXES = ('ms14_workflow_', 'ms14_workflow_review_')
TERMINAL_TOOL_STATES = {'completed', 'error'}
ACTIVE_TOOL_STATES = {'pending', 'running', 'scheduled'}
HISTORY_REPLY_BYTES = 16 * 1024 * 1024


def _norm(path):
    return str(Path(path).expanduser().resolve()).casefold()


def _body_data(body):
    return body.get('data', body) if isinstance(body, dict) else body if isinstance(body, list) else None


def _held(code, evidence=()):
    return {'status': 'HELD', 'reason_codes': [code], 'evidence': list(evidence)}


def _cutover_request(server, method, path, body=None, timeout=30):
    """Use the bounded large-reply allowance only for generated history-list GETs."""
    parsed = urllib.parse.urlsplit(path)
    history_path = re.fullmatch(r'/api/session/[^/]+/message', parsed.path)
    try:
        query = urllib.parse.parse_qs(parsed.query, keep_blank_values=True, strict_parsing=True)
        limit = query.get('limit', [])
        valid_query = (len(limit) == 1 and limit[0].isdigit() and 1 <= int(limit[0]) <= 50 and
                       ((set(query) == {'limit', 'order'} and query.get('order') == ['desc']) or
                        (set(query) == {'limit', 'cursor'} and len(query['cursor']) == 1 and bool(query['cursor'][0]))))
    except (ValueError, KeyError):
        valid_query = False
    if method == 'GET' and history_path and valid_query and not parsed.fragment:
        return gate_dispatch._request(server, method, path, body, timeout, max_reply_bytes=HISTORY_REPLY_BYTES)
    return gate_dispatch._request(server, method, path, body, timeout)


def probe_cutover(bindings, lower_bound_ms, request=None, server=None, max_pages=1000):
    """Check known author/lead session identities and legacy workflow API activity.

    Bindings are explicit records: {session_id, agent, worktree}. History is
    bounded to 1,000 pages of 50 records (50,000 records). The probe is
    GET-only; CLEAR means no API-visible workflow tool call remains in flight,
    not proof about OS processes or activity outside these sessions.
    """
    if request is None:
        request = _cutover_request
    if not isinstance(bindings, (list, tuple)) or not bindings:
        return _held('invalid_bindings')
    if isinstance(lower_bound_ms, bool) or not isinstance(lower_bound_ms, int):
        return _held('invalid_freshness_cursor')
    try:
        active_status, active_body = request(server, 'GET', '/api/session/active')
        active_rows = _body_data(active_body)
        if active_status != 200:
            return _held('active_sessions_unavailable')
        if not isinstance(active_rows, dict):
            return _held('active_sessions_malformed')
        active_ids = set()
        for sid, row in active_rows.items():
            if not isinstance(sid, str) or not re.match(r'^ses.+', sid) or row != {'type': 'running'}:
                return _held('active_sessions_malformed')
            active_ids.add(sid)
    except Exception:
        return _held('active_sessions_unavailable')
    evidence, seen = [], set()
    for binding in bindings:
        if not isinstance(binding, dict):
            return _held('invalid_binding')
        sid, expected_agent, expected_worktree = (binding.get(k) for k in ('session_id', 'agent', 'worktree'))
        if not all(isinstance(v, str) and v.strip() for v in (sid, expected_agent, expected_worktree)) or sid in seen:
            return _held('invalid_binding')
        seen.add(sid)
        escaped = urllib.parse.quote(sid, safe='')
        try:
            status, body = request(server, 'GET', '/api/session/' + escaped)
            session = _body_data(body)
            if status != 200 or not isinstance(session, dict):
                return _held('session_unavailable', [{'session_id': sid}])
            location = session.get('location')
            actual_dir = location.get('directory') if isinstance(location, dict) else None
            if session.get('id') != sid or session.get('agent') != expected_agent or not isinstance(actual_dir, str):
                return _held('session_identity_mismatch', [{'session_id': sid}])
            if _norm(actual_dir) != _norm(expected_worktree):
                return _held('session_worktree_mismatch', [{'session_id': sid, 'directory_match': False}])
            active = sid in active_ids
            evidence.append({'session_id': sid, 'agent': session['agent'], 'worktree_match': True,
                             'active_map_running': active})
            status, inbox_body = request(server, 'GET', '/api/session/' + escaped + '/inbox')
            rows = _body_data(inbox_body)
            if status != 200 or not isinstance(rows, list):
                return _held('inbox_unavailable', evidence)
            queued = []
            for row in rows:
                if not isinstance(row, dict):
                    return _held('inbox_malformed', evidence)
                if row.get('type') == 'synthetic' and row.get('delivery') == 'queue':
                    queued.append({'id': row.get('id'), 'created_ms': (row.get('time') or {}).get('created')})
            if queued:
                return _held('queued_workflow_input', evidence + [{'session_id': sid, 'queued': queued}])
            # An idle active-map result is not proof that an old workflow call
            # completed. Check all bounded history for every known binding.
            records = workflow_review_history.read_since(request, server, sid, 0,
                                                         max_pages=max_pages, page_size=50, full_scan=True)
            for message in records:
                if not isinstance(message, dict):
                    return _held('message_history_malformed', evidence)
                message_type = message.get('type')
                if message_type in ('user', 'system', 'idle', 'synthetic', 'compaction',
                                    'model-switched', 'agent-switched'):
                    continue
                if message_type != 'assistant':
                    return _held('message_history_malformed', evidence)
                parts = message.get('content')
                if not isinstance(parts, list):
                    return _held('message_history_malformed', evidence)
                for part in parts:
                    if not isinstance(part, dict):
                        return _held('message_history_malformed', evidence)
                    if part.get('type') != 'tool':
                        continue
                    name, state = part.get('name'), part.get('state')
                    if not isinstance(name, str) or not name.strip():
                        return _held('message_history_malformed', evidence)
                    if not name.startswith(WORKFLOW_PREFIXES):
                        continue
                    tool_status = state.get('status') if isinstance(state, dict) else None
                    fact = {'session_id': sid, 'message_id': message.get('id'), 'tool': name,
                            'tool_status': tool_status, 'created_ms': (message.get('time') or {}).get('created')}
                    if tool_status not in TERMINAL_TOOL_STATES:
                        return _held('workflow_tool_inflight' if tool_status in ACTIVE_TOOL_STATES else 'workflow_tool_state_unknown', evidence + [fact])
        except workflow_review_history.HistoryUnavailable:
            return _held('message_history_incomplete', evidence)
        except Exception:
            return _held('api_or_history_unavailable', evidence)
    return {'status': 'OBSERVED_CLEAR', 'reason_codes': [], 'evidence': evidence}
