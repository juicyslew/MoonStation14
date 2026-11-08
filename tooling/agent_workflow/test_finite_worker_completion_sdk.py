"""Public-API regression for finite delegated-worker completion evidence.

Set MS14_FINITE_WORKER_SDK_CHILD and MS14_FINITE_WORKER_SDK_MARKER only when
the named fresh worker fixture was launched through its permitted shell tool.
"""
import json
import os
import unittest
from pathlib import Path

try:
    import gate_dispatch
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_dispatch


def validate(child_id, child_messages, parent_messages, marker):
    """Check real OpenCode message/tool records, not a synthetic success shape."""
    def txt(parts):
        return ''.join(p.get('text', '') for p in parts if isinstance(p, dict))

    def stamp(m, key):
        value = (m.get('time') or {}).get(key, 0) if isinstance(m.get('time'), dict) else 0
        return value if isinstance(value, (int, float)) and not isinstance(value, bool) else 0

    shells = []
    invocations = []
    finals = []
    for m in child_messages:
        if m.get('type') != 'assistant':
            continue
        for c in m.get('content', []):
            if c.get('type') == 'tool' and c.get('name') == 'shell':
                state = c.get('state') or {}
                inp = state.get('input') or {}
                started = stamp(c, 'created') or stamp(c, 'ran')
                completed = stamp(c, 'completed') or (state.get('time') or {}).get('completed', 0)
                if started > 0:
                    invocations.append((started, completed, inp, state))
                if (state.get('status') == 'completed' and inp.get('background') is not True
                        and isinstance(inp.get('timeout'), (int, float)) and not isinstance(inp.get('timeout'), bool)
                        and inp['timeout'] > 0 and started > 0 and completed >= started
                        and marker in txt(state.get('content', []))):
                    shells.append((started, completed))
        if stamp(m, 'completed') and m.get('finish') == 'stop':
            finals.append((stamp(m, 'completed'), txt(m.get('content', []))))
    if not shells or not finals:
        raise AssertionError('worker report does not follow a completed foreground finite shell check')
    latest_start = max((start for start, _, _, _ in invocations), default=0)
    shell_start, terminal_time = max(shells, key=lambda item: item[1])
    final_time, final_text = max(finals)
    if latest_start > shell_start or terminal_time < shell_start or final_time < terminal_time or 'COMPLETE' not in final_text or marker not in final_text:
        raise AssertionError('latest final worker report does not follow its completed foreground finite shell check')
    matched = [m for m in parent_messages
               if m.get('type') == 'synthetic'
               and m.get('metadata', {}).get('source') == 'subagent'
               and m.get('metadata', {}).get('childID') == child_id
                and stamp(m, 'created') >= final_time
               and marker in m.get('text', '') and 'COMPLETE' in m.get('text', '')]
    if not matched:
        raise AssertionError('no matching post-completion parent notification')


class FiniteWorkerCompletionTests(unittest.TestCase):
    def test_actual_public_session_history_for_opted_in_fixture(self):
        child_id = os.environ.get('MS14_FINITE_WORKER_SDK_CHILD')
        marker = os.environ.get('MS14_FINITE_WORKER_SDK_MARKER')
        if not child_id or not marker:
            self.skipTest('requires explicitly selected fresh permitted-role fixture')
        config = json.loads((Path.home() / '.local/state/opencode/service.json').read_text())
        base = config.get('url') or config.get('server')
        old_password = os.environ.get('OPENCODE_SERVER_PASSWORD')
        password = config.get('password')
        if password:
            os.environ['OPENCODE_SERVER_PASSWORD'] = password
        try:
            _, child = gate_dispatch._request(base, 'GET', f'/api/session/{child_id}', timeout=10, max_reply_bytes=1024 * 1024)
            child = child.get('data', child)
            parent_id = child.get('parentID') or child.get('parentId')
            self.assertTrue(parent_id, 'fixture session must expose its parent')
            _, cm = gate_dispatch._request(base, 'GET', f'/api/session/{child_id}/message?limit=30', timeout=10, max_reply_bytes=2 * 1024 * 1024)
            _, pm = gate_dispatch._request(base, 'GET', f'/api/session/{parent_id}/message?type=synthetic&order=desc&limit=30', timeout=10, max_reply_bytes=2 * 1024 * 1024)
        finally:
            if old_password is None:
                os.environ.pop('OPENCODE_SERVER_PASSWORD', None)
            else:
                os.environ['OPENCODE_SERVER_PASSWORD'] = old_password
        cm, pm = cm.get('data', cm), pm.get('data', pm)
        validate(child_id, cm, pm, marker)

    def test_foreground_completion_is_accepted(self):
        marker = 'FINITE_FIXTURE'
        shell = {'type': 'assistant', 'time': {'created': 10, 'completed': 20}, 'finish': 'tool-calls', 'content': [{'type': 'tool', 'name': 'shell', 'time': {'created': 11, 'ran': 12, 'completed': 20},
                 'state': {'input': {'background': False, 'timeout': 30000, 'command': 'echo '+marker}, 'status': 'completed', 'content': [{'type': 'text', 'text': marker}]}}]}
        final = {'type': 'assistant', 'time': {'created': 21, 'completed': 22}, 'finish': 'stop',
                  'content': [{'type': 'text', 'text': marker + ' COMPLETE'}]}
        notice = {'type': 'synthetic', 'time': {'created': 23},
                  'metadata': {'source': 'subagent', 'childID': 'child'}, 'text': marker + ' COMPLETE'}
        validate('child', [shell, final], [notice], marker)

    def test_rejects_background_shell(self): self._reject(shell_changes={'input': {'background': True, 'timeout': 30000}})
    def test_rejects_running_shell(self): self._reject(status='running')
    def test_rejects_parent_notice_before_final(self): self._reject(notice_time=21)
    def test_rejects_wrong_child(self): self._reject(child='other')
    def test_rejects_stale_completion_before_later_invocation(self): self._reject(later=True)
    def test_rejects_stale_completion_before_later_running_invocation(self): self._reject(later_running=True)
    def test_rejects_tool_calls_without_final_stop(self): self._reject(finish='tool-calls')
    def test_rejects_mismatched_marker(self):
        with self.assertRaises(AssertionError):
            validate('child', [self._messages()[0], self._messages()[1]], [self._messages()[2]], 'OTHER_MARKER')

    def _messages(self):
        marker = 'FINITE_FIXTURE'
        tool = {'type': 'tool', 'name': 'shell', 'time': {'created': 11, 'ran': 12, 'completed': 20},
                'state': {'input': {'background': False, 'timeout': 30000, 'command': 'echo '+marker}, 'status': 'completed', 'content': [{'type': 'text', 'text': marker}]}}
        return [{'type': 'assistant', 'time': {'created': 10, 'completed': 20}, 'finish': 'tool-calls', 'content': [tool]},
                {'type': 'assistant', 'time': {'created': 21, 'completed': 22}, 'finish': 'stop', 'content': [{'type': 'text', 'text': marker+' COMPLETE'}]},
                {'type': 'synthetic', 'time': {'created': 23}, 'metadata': {'source': 'subagent', 'childID': 'child'}, 'text': marker+' COMPLETE'}]

    def _reject(self, shell_changes=None, status='completed', notice_time=23, child='child', later=False, later_running=False, finish='stop'):
        marker = 'FINITE_FIXTURE'
        state = {'input': {'background': False, 'timeout': 30000, 'command': 'echo '+marker}, 'status': status,
                 'content': [{'type': 'text', 'text': marker}]}
        state.update(shell_changes or {})
        tool = {'type': 'tool', 'name': 'shell', 'time': {'created': 11, 'ran': 12, 'completed': 20}, 'state': state}
        assistant = {'type': 'assistant', 'time': {'created': 10, 'completed': 20}, 'finish': 'tool-calls', 'content': [tool]}
        final = {'type': 'assistant', 'time': {'created': 21, 'completed': 22}, 'finish': finish, 'content': [{'type': 'text', 'text': marker+' COMPLETE'}]}
        msgs = [assistant, final]
        if later:
            msgs.append({'type': 'assistant', 'time': {'created': 30, 'completed': 31}, 'finish': 'tool-calls', 'content': [dict(tool, time={'created': 30, 'ran': 30, 'completed': 32})]})
        if later_running:
            msgs.append({'type': 'assistant', 'time': {'created': 30}, 'finish': 'tool-calls', 'content': [{'type': 'tool', 'name': 'shell', 'time': {'created': 30, 'ran': 30}, 'state': {'status': 'running', 'input': {'background': False, 'timeout': 30000}, 'content': []}}]})
        notice = {'type': 'synthetic', 'time': {'created': notice_time}, 'metadata': {'source': 'subagent', 'childID': child}, 'text': marker+' COMPLETE'}
        with self.assertRaises(AssertionError): validate('child', msgs, [notice], marker)


if __name__ == '__main__':
    unittest.main()
