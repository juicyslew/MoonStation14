import unittest
from unittest.mock import patch

import workflow_cutover_api as cutover


SID = 'ses-author'
LEAD = 'ses-lead'
ROOT = r'C:\repo'


def message(parts, stamp=1500):
    return {'id': 'msg-' + str(stamp), 'time': {'created': stamp}, 'type': 'assistant', 'content': parts}


def non_assistant(kind, stamp=1500):
    if kind == 'idle':
        return {'id': 'msg-' + str(stamp), 'time': {'created': stamp}, 'type': kind, 'outcome': 'idle'}
    if kind == 'compaction':
        return {'id': 'msg-' + str(stamp), 'type': kind, 'time': {'created': stamp},
                'cost': 0, 'model': 'model', 'providerState': {}, 'reason': 'compact',
                'recent': False, 'status': 'completed', 'summary': 'compacted', 'tokens': {}}
    if kind == 'model-switched':
        return {'id': 'msg-' + str(stamp), 'type': kind, 'time': {'created': stamp},
                'model': 'model', 'previous': 'previous'}
    if kind == 'agent-switched':
        return {'id': 'msg-' + str(stamp), 'type': kind, 'time': {'created': stamp},
                'agent': 'agent', 'previous': 'previous'}
    return {'id': 'msg-' + str(stamp), 'time': {'created': stamp}, 'type': kind,
            'metadata': {}, 'text': 'ordinary message'}


class ApiFixture:
    def __init__(self, messages=(), inbox=None, session_override=None, fail=None, active=True,
                 active_status=200, active_body=None):
        self.messages = list(messages)
        self.inbox = [] if inbox is None else inbox
        self.session_override = session_override
        self.fail = fail
        self.active = active
        self.active_status = active_status
        self.active_body = active_body
        self.calls = []

    def request(self, server, method, path):
        self.calls.append((method, path))
        if self.fail == path:
            raise OSError('fixture failure')
        if path == '/api/session/active':
            return self.active_status, self.active_body if self.active_body is not None else {
                'data': {SID: {'type': 'running'}} if self.active else {}}
        if path == '/api/session/' + SID:
            body = self.session_override or {'id': SID, 'agent': 'orchestrate-ms14', 'location': {'directory': ROOT}, 'time': {'created': 1, 'updated': 2}}
            return 200, body
        if path == '/api/session/' + LEAD:
            return 200, {'id': LEAD, 'agent': 'integration-lead', 'location': {'directory': ROOT + '-lead'}, 'time': {'created': 1, 'updated': 2}}
        if path.endswith('/inbox'):
            return 200, self.inbox
        if '/message?' in path:
            # One complete history page followed by its verified end marker.
            return 200, {'data': list(reversed(self.messages)), 'cursor': {'next': None}}
        raise AssertionError(path)


class CutoverApiTests(unittest.TestCase):
    def probe(self, fixture, bindings=None):
        return cutover.probe_cutover(bindings or [{'session_id': SID, 'agent': 'orchestrate-ms14', 'worktree': ROOT}],
                                     1000, request=fixture.request)

    def test_completed_legacy_workflow_is_clear(self):
        f = ApiFixture([message([{'type': 'tool', 'name': 'ms14_workflow_validate', 'state': {'status': 'completed'}}])])
        self.assertEqual('OBSERVED_CLEAR', self.probe(f)['status'])

    def test_active_map_shape_and_metadata_are_not_workflow_completion_proof(self):
        f = ApiFixture(active=True)
        result = self.probe(f)
        self.assertEqual('OBSERVED_CLEAR', result['status'])
        self.assertTrue(result['evidence'][0]['active_map_running'])
        f = ApiFixture(active_body={'data': {'ses-unrelated': {'type': 'running'}}})
        result = self.probe(f)
        self.assertEqual('OBSERVED_CLEAR', result['status'])
        self.assertFalse(result['evidence'][0]['active_map_running'])
        self.assertEqual('OBSERVED_CLEAR', self.probe(ApiFixture(active=False))['status'])

    def test_active_map_malformed_or_unavailable_fails_closed(self):
        malformed = [
            [], {'data': []}, {'data': None}, {'data': {'bad-id': {'type': 'running'}}},
            {'data': {'ses-x': {'type': 'running', 'extra': 1}}},
            {'data': {'ses-x': {'type': 'unknown'}}}, {'data': {'ses-x': {'type': True}}},
            {'data': {'ses-x': None}}, {'data': {'ses-x': True}},
        ]
        for body in malformed:
            with self.subTest(body=body):
                self.assertEqual('active_sessions_malformed', self.probe(ApiFixture(active_body=body))['reason_codes'][0])
        self.assertEqual('active_sessions_unavailable', self.probe(ApiFixture(active_status=401))['reason_codes'][0])
        f = ApiFixture(); f.fail = '/api/session/active'
        self.assertEqual('active_sessions_unavailable', self.probe(f)['reason_codes'][0])

    def test_running_or_unknown_legacy_workflow_held(self):
        for state, reason in [('running', 'workflow_tool_inflight'), ('mystery', 'workflow_tool_state_unknown')]:
            f = ApiFixture([message([{'type': 'tool', 'name': 'ms14_workflow_validate', 'state': {'status': state}}])])
            result = self.probe(f)
            self.assertEqual(('HELD', [reason]), (result['status'], result['reason_codes']))

    def test_active_author_ordinary_tool_and_text_are_not_workflow(self):
        f = ApiFixture([message([{'type': 'tool', 'name': 'patch', 'state': {'status': 'running'}},
                                 {'type': 'tool', 'name': 'shell', 'state': {'status': 'pending'}},
                                 {'type': 'text', 'text': 'ordinary chat'}])])
        self.assertEqual('OBSERVED_CLEAR', self.probe(f)['status'])

    def test_old_inflight_call_not_dropped_by_hold_cursor(self):
        f = ApiFixture([message([{'type': 'tool', 'name': 'ms14_workflow_submit', 'state': {'status': 'running'}}], 900)])
        self.assertEqual('workflow_tool_inflight', self.probe(f)['reason_codes'][0])

    def test_typed_nonassistant_history_is_ignored_but_assistant_tool_is_scanned(self):
        records = [message([{'type': 'tool', 'name': 'ms14_workflow_submit', 'state': {'status': 'running'}}], 900),
                   non_assistant('idle', 1500), non_assistant('system', 1600), non_assistant('user', 1700),
                   non_assistant('synthetic', 1800), non_assistant('compaction', 1900),
                   non_assistant('model-switched', 2000), non_assistant('agent-switched', 2100)]
        self.assertEqual('workflow_tool_inflight', self.probe(ApiFixture(records))['reason_codes'][0])
        clear = [non_assistant(kind) for kind in ('user', 'system', 'idle', 'synthetic', 'compaction',
                                                  'model-switched', 'agent-switched')]
        clear += [message([{'type': 'tool', 'name': 'ms14_workflow_submit', 'state': {'status': status}}])
                  for status in ('completed', 'error')]
        self.assertEqual('OBSERVED_CLEAR', self.probe(ApiFixture(clear))['status'])

    def test_malformed_assistant_content_and_unknown_message_type_hold(self):
        for record in ({'id': 'bad', 'time': {'created': 1500}, 'type': 'assistant'},
                       {'id': 'bad', 'time': {'created': 1500}, 'type': 'assistant', 'content': [None]},
                       {'id': 'bad', 'time': {'created': 1500}, 'type': 'future'}):
            with self.subTest(record=record):
                self.assertEqual('message_history_malformed', self.probe(ApiFixture([record]))['reason_codes'][0])

    def test_binding_and_inbox_fail_closed(self):
        f = ApiFixture(session_override={'id': SID, 'agent': 'wrong', 'location': {'directory': ROOT}})
        self.assertEqual('session_identity_mismatch', self.probe(f)['reason_codes'][0])
        f = ApiFixture(inbox=[{'type': 'synthetic', 'delivery': 'queue', 'id': 'q1', 'time': {'created': 1200}}])
        self.assertEqual('queued_workflow_input', self.probe(f)['reason_codes'][0])

    def test_failures_and_malformed_history_hold(self):
        f = ApiFixture(); f.fail = '/api/session/' + SID + '/inbox'
        self.assertEqual('HELD', self.probe(f)['status'])
        f = ApiFixture([{'id': 'bad', 'type': 'assistant', 'time': {'created': 1500}}])
        self.assertEqual('message_history_malformed', self.probe(f)['reason_codes'][0])
        malformed_tool_parts = ({'type': 'tool'}, {'type': 'tool', 'name': None},
                                {'type': 'tool', 'name': 7}, {'type': 'tool', 'name': ''},
                                {'type': 'tool', 'name': '   '})
        for part in malformed_tool_parts:
            with self.subTest(part=part):
                result = self.probe(ApiFixture([message([part])]))
                self.assertEqual(('HELD', ['message_history_malformed']),
                                 (result['status'], result['reason_codes']))

    def test_complete_pagination_scans_to_verified_boundary(self):
        base = ApiFixture()
        pages = 0
        def paged(server, method, path):
            nonlocal pages
            if path == '/api/session/active':
                return 200, {'data': {}}
            if '/message?' in path:
                pages += 1
                if pages == 1:
                    return 200, {'data': [message([], 1200)], 'cursor': {'next': 'next-page'}}
                return 200, {'data': [message([], 900)], 'cursor': {'next': None}}
            return base.request(server, method, path)
        self.assertEqual('OBSERVED_CLEAR', cutover.probe_cutover(
            [{'session_id': SID, 'agent': 'orchestrate-ms14', 'worktree': ROOT}], 1000,
            request=paged)['status'])
        self.assertEqual(2, pages)

    def test_inactive_old_call_on_later_page_is_held_and_page_exhaustion_blocks(self):
        call = message([{'type': 'tool', 'name': 'ms14_workflow_submit', 'state': {'status': 'running'}}], 900)
        base = ApiFixture(active=False)
        pages = 0
        def paged(server, method, path):
            nonlocal pages
            if path == '/api/session/active': return 200, {'data': {}}
            if '/message?' in path:
                pages += 1
                if pages == 1:
                    return 200, {'data': [message([], 1500)], 'cursor': {'next': 'later'}}
                return 200, {'data': [call], 'cursor': {'next': None}}
            return base.request(server, method, path)
        binding = [{'session_id': SID, 'agent': 'orchestrate-ms14', 'worktree': ROOT}]
        self.assertEqual('workflow_tool_inflight', cutover.probe_cutover(binding, 1000, request=paged)['reason_codes'][0])
        pages = 0
        result = cutover.probe_cutover(binding, 1000, request=paged, max_pages=1)
        self.assertEqual('message_history_incomplete', result['reason_codes'][0])

    def test_cutover_uses_bounded_pages_and_finds_old_pending_tool(self):
        base = ApiFixture(active=False)
        pages = 0
        old = message([{'type': 'tool', 'name': 'ms14_workflow_submit', 'state': {'status': 'pending'}}], 900)
        def paged(server, method, path):
            nonlocal pages
            if path == '/api/session/active': return 200, {'data': {}}
            if '/message?' in path:
                self.assertIn('limit=50', path)
                pages += 1
                if pages == 1:
                    return 200, {'data': [message([], 1500)], 'cursor': {'next': 'older'}}
                return 200, {'data': [old], 'cursor': {'next': None}}
            return base.request(server, method, path)
        result = cutover.probe_cutover(
            [{'session_id': SID, 'agent': 'orchestrate-ms14', 'worktree': ROOT}], 1000, request=paged)
        self.assertEqual('workflow_tool_inflight', result['reason_codes'][0])
        self.assertEqual(2, pages)

    def test_cutover_requests_exhaustive_history_scan(self):
        reader = cutover.workflow_review_history.read_since
        with patch.object(cutover.workflow_review_history, 'read_since', wraps=reader) as read_since:
            result = self.probe(ApiFixture())
        self.assertEqual('OBSERVED_CLEAR', result['status'])
        self.assertEqual(0, read_since.call_args.args[3])
        self.assertEqual({'max_pages': 1000, 'page_size': 50, 'full_scan': True},
                         read_since.call_args.kwargs)

    def test_get_only_and_inputs_not_mutated(self):
        rows = [message([{'type': 'tool', 'name': 'shell', 'state': {'status': 'completed'}}])]
        before = repr(rows)
        f = ApiFixture(rows)
        self.assertEqual('OBSERVED_CLEAR', self.probe(f)['status'])
        self.assertEqual(before, repr(rows))
        self.assertTrue(all(method == 'GET' for method, _ in f.calls))

    def test_default_request_uses_large_cap_only_for_history_get(self):
        calls = []
        def request(server, method, path, body=None, timeout=30, **kwargs):
            calls.append((method, path, kwargs))
            if path == '/api/session/active': return 200, {'data': {}}
            if path == '/api/session/' + SID:
                return 200, {'id': SID, 'agent': 'orchestrate-ms14', 'location': {'directory': ROOT}}
            if path.endswith('/inbox'): return 200, []
            if '/message?' in path: return 200, {'data': [], 'cursor': {'next': None}}
            raise AssertionError(path)
        with patch.object(cutover.gate_dispatch, '_request', side_effect=request):
            result = cutover.probe_cutover(
                [{'session_id': SID, 'agent': 'orchestrate-ms14', 'worktree': ROOT}], 1000)
        self.assertEqual('OBSERVED_CLEAR', result['status'])
        history_calls = [entry for entry in calls if '/message?' in entry[1]]
        other_calls = [entry for entry in calls if '/message?' not in entry[1]]
        self.assertEqual(1, len(history_calls))
        self.assertIn('limit=50', history_calls[0][1])
        self.assertEqual({'max_reply_bytes': cutover.HISTORY_REPLY_BYTES}, history_calls[0][2])
        self.assertTrue(all(not kwargs for _, _, kwargs in other_calls))
        self.assertTrue(all(method == 'GET' for method, _, _ in calls))

    def test_history_large_cap_adapter_rejects_nonhistory_routes(self):
        calls = []
        def request(server, method, path, body=None, timeout=30, **kwargs):
            calls.append((method, path, kwargs))
            return 204, None
        paths = ('/api/session/sid/message?limit=10&order=desc',
                 '/api/session/sid/message?limit=10&cursor=abc',
                 '/api/session/sid/inbox', '/api/session/sid/message-extra?limit=10&order=desc',
                 '/api/session/sid/message?limit=10&order=asc')
        with patch.object(cutover.gate_dispatch, '_request', side_effect=request):
            for path in paths:
                cutover._cutover_request('server', 'GET', path)
            cutover._cutover_request('server', 'POST', paths[0])
        self.assertEqual([{'max_reply_bytes': cutover.HISTORY_REPLY_BYTES}, {'max_reply_bytes': cutover.HISTORY_REPLY_BYTES},
                          {}, {}, {}, {}], [kwargs for _, _, kwargs in calls])


if __name__ == '__main__':
    unittest.main()
