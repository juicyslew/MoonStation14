import json
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from tooling.agent_workflow import gate_liveness as live
from tooling.agent_workflow import workflow_runtime as runtime


class GateLivenessTests(unittest.TestCase):
    def setup_gate(self, d, *, outcome=None, prompt_id=None):
        repo = str((Path(d) / 'author-repo').resolve())
        central = str((Path(d) / 'neutral-05').resolve())
        c = {'id': 'candidate', 'snapshot_key': 'snap', 'sprint_id': 's', 'revision_sequence': 1,
             'worktree': {'worktree': repo}, 'gate_request': {'session_id': 'lead', 'snapshot_key': 'snap',
             'prompt_admitted': True, 'prompt_id': prompt_id, 'central_worktree': central}, 'gate_outcome': outcome}
        runtime.writejson(Path(d) / 'candidates' / 'candidate.json', c)
        live.register(d, c, lead_session_id='lead', workflow=runtime)
        wf = SimpleNamespace(readjson=runtime.readjson, verify_gate=lambda c: c)
        def req(server, method, path, body=None, timeout=15):
            if path == '/api/session/lead':
                return 200, {'data': {'id': 'lead', 'agent': 'integration-lead', 'directory': central,
                    'metadata': {'candidate_id': 'candidate', 'snapshot_key': 'snap', 'author_worktree': repo,
                                 'workflow_gate': True}, 'status': 'idle'}}
            if path.endswith('/inbox'): return 200, {'data': []}
            if path.endswith('/permission'): return 200, {'data': []}
            if '/message?' in path: return 200, {'data': []}
            raise AssertionError((method, path, body))
        return c, wf, req

    def test_registration_binding_is_idempotent_and_preserves_state(self):
        with tempfile.TemporaryDirectory() as d:
            c = {'id': 'candidate', 'snapshot_key': 'snap'}
            row = live.register(d, c, lead_session_id='lead', workflow=runtime)
            row.update(state='recovering', recovery_count=1)
            runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', row)
            again = live.register(d, c, lead_session_id='lead', workflow=runtime)
            self.assertEqual((again['state'], again['recovery_count']), ('recovering', 1))
            self.assertEqual(live.read_health(d, 'candidate'), again)

    def test_permission_wait_then_one_nested_ack_recovery_same_session(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, base = self.setup_gate(d)
            calls = []
            def req(server, method, path, body=None, timeout=15):
                calls.append((method, path, body))
                if path.endswith('/permission'): return 200, {'data': [{'id': 'perm'}]}
                if method == 'POST':
                    return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': 'term-1', 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            result = live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            self.assertEqual(result['pending'], 1)
            self.assertFalse(any(x[0] == 'POST' for x in calls))
            def no_permission(server, method, path, body=None, timeout=15):
                if path.endswith('/permission'): return 200, {'data': []}
                return req(server, method, path, body, timeout)
            result = live.reconcile(d, workflow=wf, request=no_permission, pid_alive=lambda _: False)
            post = [x for x in calls if x[0] == 'POST']
            self.assertEqual(len(post), 1)
            self.assertEqual(post[0][1], '/api/session/lead/prompt')
            self.assertTrue(post[0][2]['resume'])
            self.assertIn(str(Path(d).parents[1].resolve()), post[0][2]['text'])
            self.assertIn('gate-preflight candidate', post[0][2]['text'])
            self.assertIn('neutral-05', post[0][2]['text'])
            health = live.read_health(d, 'candidate')
            self.assertEqual(health['recovery_count'], 1)
            self.assertEqual(health['session_id'], 'lead')

    def test_progress_runner_pid_alive_does_not_recover(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, req = self.setup_gate(d)
            self.assertNotEqual(c['worktree']['worktree'], c['gate_request']['central_worktree'])
            runtime.writejson(Path(d) / 'progress' / 'candidate.json', {'candidate_id': 'candidate', 'snapshot_key': 'snap', 'phase': 'queued', 'runner_pid': 77})
            result = live.reconcile(d, workflow=wf, request=req, pid_alive=lambda pid: pid == 77)
            self.assertEqual(result['active'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 0)

    def test_original_queued_item_is_patched_only_once_across_ticks(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d, prompt_id='original')
            item = {'id': 'original', 'delivery': 'queue', 'payload': {'text': 'review', 'metadata': {'candidate_id': 'candidate', 'snapshot_key': 'snap'}}}
            calls = []
            def req(server, method, path, body=None, timeout=15):
                if path.endswith('/inbox'): return 200, {'data': [item]}
                if method == 'PATCH': calls.append((method, path, body)); return 204, None
                return base(server, method, path, body, timeout)
            for tick in (1, 2, 3): live.reconcile(d, workflow=wf, request=req, now=tick, pid_alive=lambda _: False)
            self.assertEqual(len(calls), 1)
            self.assertEqual(calls[0], ('PATCH', '/api/session/lead/inbox/original', {'delivery': 'steer'}))
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 1)

    def test_ambiguous_recovery_retries_after_freshness_and_pid_guards(self):
        for stale, alive in ((True, False), (False, True), (True, True)):
            with self.subTest(stale=stale, alive=alive), tempfile.TemporaryDirectory() as d:
                _, wf, base = self.setup_gate(d)
                health = live.read_health(d, 'candidate')
                body = {'id': 'same-message', 'text': 'exact', 'metadata': {'candidate_id': 'candidate'}, 'delivery': 'steer'}
                health.update(recovery_count=1, state='api_unavailable', pending_recovery={'body': body})
                runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', health)
                runtime.writejson(Path(d) / 'progress' / 'candidate.json', {'candidate_id': 'candidate', 'snapshot_key': 'snap', 'phase': 'running', 'runner_pid': 99})
                if stale: wf.verify_gate = lambda c: (_ for _ in ()).throw(ValueError('changed'))
                calls = []
                def req(server, method, path, body=None, timeout=15):
                    if method == 'POST': calls.append(body); return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                    return base(server, method, path, body, timeout)
                with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}):
                    live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: alive)
                self.assertFalse(calls)
                self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 1)

    def test_tool_calls_without_real_session_idle_timestamp_is_unconfirmed(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [{'id': 'tools', 'finish': 'tool-calls', 'time': {'completed': 100}}]}
                return base(server, method, path, body, timeout)
            result = live.reconcile(d, workflow=wf, request=req, now=1000, pid_alive=lambda _: False)
            self.assertEqual(result['pending'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'observing')

    def test_unchanged_nonterminal_evidence_across_hours_is_unconfirmed(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'stream', 'finish': None, 'time': {'created': 100, 'streamed': 101},
                   'content': [{'type': 'tool', 'state': {'status': 'running', 'input': {'step': 'working'}}}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            first = live.read_health(d, 'candidate'); stamp = first['last_activity']
            self.assertEqual(first['state'], 'observing')
            live.reconcile(d, workflow=wf, request=req, now=4600)
            second = live.read_health(d, 'candidate')
            self.assertEqual(second['state'], 'observing')
            self.assertEqual(second['last_activity'], stamp)
            self.assertGreater(second['checked_at'], first['checked_at'])

    def test_streaming_evidence_advance_is_recorded_as_activity(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'stream', 'finish': None, 'time': {'created': 100, 'streamed': 101},
                   'content': [{'type': 'tool', 'state': {'status': 'running', 'input': 'a'}}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            row['time']['streamed'] = 105; row['content'][0]['state'] = {'status': 'completed', 'output': 'ab'}
            result = live.reconcile(d, workflow=wf, request=req, now=1001)
            health = live.read_health(d, 'candidate')
            self.assertEqual(result['active'], 1)
            self.assertEqual(health['last_activity'], 105)
            self.assertEqual(health['state'], 'active')

    def test_actual_opencode_epoch_millisecond_time_is_normalized_to_seconds(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            created = 1_792_039_025_543
            row = {'id': 'assistant-tool', 'finish': None, 'time': {'created': created},
                   'content': [{'type': 'tool', 'state': {'status': 'running'}, 'time': {'created': created, 'ran': created}}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1_792_039_026)
            first = live.read_health(d, 'candidate')
            self.assertAlmostEqual(first['last_activity'], created / 1000)
            self.assertLess(first['last_activity'], 2_000_000_000)
            live.reconcile(d, workflow=wf, request=req, now=1_792_039_030)
            same = live.read_health(d, 'candidate')
            self.assertEqual(same['last_activity'], first['last_activity'])
            row['content'][0]['state']['status'] = 'completed'
            row['content'][0]['time']['completed'] = created + 5_000
            result = live.reconcile(d, workflow=wf, request=req, now=1_792_039_031)
            advanced = live.read_health(d, 'candidate')
            self.assertEqual(result['active'], 1)
            self.assertAlmostEqual(advanced['last_activity'], (created + 5_000) / 1000)
            self.assertEqual(advanced['state'], 'active')

    def test_untimestamped_content_change_is_observed_not_activity(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'assistant', 'finish': None, 'content': [{'type': 'text', 'text': 'a'}]}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=100)
            first = live.read_health(d, 'candidate')
            self.assertIsNone(first['last_activity'])
            row['content'][0]['text'] = 'ab'
            result = live.reconcile(d, workflow=wf, request=req, now=200)
            second = live.read_health(d, 'candidate')
            self.assertEqual(result['active'], 0)
            self.assertEqual(second['state'], 'observing')
            self.assertIsNone(second['last_activity'])
            self.assertEqual(second['observed_change_at'], 200.0)

    def test_scheduled_provider_retry_is_unconfirmed_across_hours(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            row = {'id': 'retry', 'finish': None, 'retry': {'attempt': 1, 'at': 10_000_000, 'error': 'temporary'}, 'time': {'created': 100}}
            def req(server, method, path, body=None, timeout=15):
                if '/message?' in path: return 200, {'data': [row]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, now=1000)
            one = live.read_health(d, 'candidate')
            live.reconcile(d, workflow=wf, request=req, now=2000)
            two = live.read_health(d, 'candidate')
            self.assertEqual((one['state'], two['state']), ('observing', 'observing'))
            self.assertEqual(two['last_activity'], one.get('last_activity'))
            self.assertEqual(two['retry_at'], 10_000)

    def test_stale_blocks_without_recovery_and_notice_boundary_is_mocked(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, req = self.setup_gate(d)
            wf.verify_gate = lambda c: (_ for _ in ()).throw(ValueError('stale source'))
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}) as notice:
                res = live.reconcile(d, workflow=wf, request=lambda *a, **k: (200, {'data': [{'id': 't', 'finish': 'stop'}]}) if '/message?' in a[2] else req(*a, **k), pid_alive=lambda _: False)
            self.assertEqual(res['blocked'], 1)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'blocked')
            notice.assert_called_once()

    def test_original_prompt_uses_patch_of_same_inbox_item(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, base = self.setup_gate(d, prompt_id='original')
            inbox_item = {'id': 'original', 'delivery': 'queue', 'payload': {'text': 'review', 'metadata': {'candidate_id': 'candidate', 'snapshot_key': 'snap'}}}
            calls = []
            def req(server, method, path, body=None, timeout=15):
                calls.append((method, path, body))
                if path.endswith('/inbox'): return 200, {'data': [inbox_item]}
                if method == 'PATCH': return 204, None
                return base(server, method, path, body, timeout)
            res = live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            self.assertEqual(res['recovered'], 1)
            self.assertIn(('PATCH', '/api/session/lead/inbox/original', {'delivery': 'steer'}), calls)
            self.assertFalse(any(x[0] == 'POST' for x in calls))
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 1)

    def test_wrong_nested_ack_retries_same_id_and_body(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            calls = []
            def req(server, method, path, body=None, timeout=15):
                if method == 'POST':
                    calls.append(body)
                    if len(calls) == 1: return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': {'wrong': True}}}}
                    return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': 'term-1', 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            self.assertEqual(len(calls), 2)
            self.assertEqual(calls[0], calls[1])

    def test_same_terminal_after_recovery_is_unconfirmed_not_blocked(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def req(server, method, path, body=None, timeout=15):
                if method == 'POST': return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': 'term-1', 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            result = live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            self.assertEqual(result['blocked'], 0)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'observing')

    def test_different_terminal_after_recovery_blocks_once(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            terminal = ['term-1']
            def req(server, method, path, body=None, timeout=15):
                if method == 'POST': return 200, {'data': {'id': body['id'], 'sessionID': 'lead', 'payload': {'text': body['text'], 'metadata': body['metadata']}}}
                if '/message?' in path: return 200, {'data': [{'id': terminal[0], 'finish': 'stop'}]}
                return base(server, method, path, body, timeout)
            live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            terminal[0] = 'term-2'
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', return_value={'status': 'delivered'}) as notice:
                self.assertEqual(live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)['blocked'], 1)
                live.reconcile(d, workflow=wf, request=req, pid_alive=lambda _: False)
            notice.assert_called_once()

    def test_failed_author_notice_retries_until_delivered_then_stops(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, _ = self.setup_gate(d)
            health = live.read_health(d, 'candidate')
            health.update(state='blocked', reason='stopped', author_notice={'status': 'failed', 'id': 'notice-id', 'body': {'text': 'same'}})
            runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', health)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_block_notice', side_effect=[{'status': 'failed', 'id': 'notice-id'}, {'status': 'delivered', 'id': 'notice-id'}]) as deliver:
                live.reconcile(d, workflow=wf, request=lambda *a, **k: self.fail('blocked state must not query lead API'))
                live.reconcile(d, workflow=wf, request=lambda *a, **k: self.fail('blocked state must not query lead API'))
                live.reconcile(d, workflow=wf, request=lambda *a, **k: self.fail('delivered notice must not retry'))
            self.assertEqual(deliver.call_count, 2)

    def test_finished_outcome_wins_without_api(self):
        with tempfile.TemporaryDirectory() as d:
            self.setup_gate(d, outcome={'result': 'ready_for_owner'})
            result = live.reconcile(d, workflow=runtime, request=lambda *a, **k: self.fail('unexpected API'))
            self.assertEqual(result['active'] + result['pending'] + result['blocked'], 0)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'finished')

    def test_role_and_location_mismatch_fail_closed(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def req(server, method, path, body=None, timeout=15):
                status, response = base(server, method, path, body, timeout)
                if path == '/api/session/lead': response['data']['directory'] = str(Path(d) / 'wrong')
                return status, response
            result = live.reconcile(d, workflow=wf, request=req)
            self.assertTrue(result['errors'])
            self.assertEqual(live.read_health(d, 'candidate')['recovery_count'], 0)

    def test_author_worktree_location_is_not_accepted_for_neutral_lead(self):
        with tempfile.TemporaryDirectory() as d:
            c, wf, base = self.setup_gate(d)
            def author_location(server, method, path, body=None, timeout=15):
                status, response = base(server, method, path, body, timeout)
                if path == '/api/session/lead': response['data']['directory'] = c['worktree']['worktree']
                return status, response
            result = live.reconcile(d, workflow=wf, request=author_location)
            self.assertTrue(result['errors'])
            self.assertIn('central-worktree', result['errors'][0])

    def test_role_mismatch_and_api_error_do_not_create_verdict(self):
        with tempfile.TemporaryDirectory() as d:
            _, wf, base = self.setup_gate(d)
            def wrong_role(server, method, path, body=None, timeout=15):
                status, response = base(server, method, path, body, timeout)
                if path == '/api/session/lead': response['data']['agent'] = 'author'
                return status, response
            failed = live.reconcile(d, workflow=wf, request=wrong_role)
            self.assertTrue(failed['errors'])
            def broken(*args, **kwargs): raise OSError('offline')
            result = live.reconcile(d, workflow=wf, request=broken)
            self.assertEqual(result['pending'], 1)
            health = live.read_health(d, 'candidate')
            self.assertEqual(health['recovery_count'], 0)
            self.assertIsNone(runtime.readjson(Path(d) / 'candidates' / 'candidate.json')['gate_outcome'])

    @unittest.skipUnless(os.name == 'nt', 'Windows process handle validation')
    def test_windows_pid_probe_accepts_current_process(self):
        self.assertTrue(live._pid_alive(os.getpid()))


if __name__ == '__main__':
    unittest.main()
