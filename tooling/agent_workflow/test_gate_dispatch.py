import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from tooling.agent_workflow import gate_dispatch as d


class WF:
    calls = []
    validid = staticmethod(lambda value: value)
    git_common = None
    git = staticmethod(lambda repo, *args: str(WF.git_common).encode())
    sha = staticmethod(lambda path: hashlib.sha256(Path(path).read_bytes()).hexdigest())
    safe = staticmethod(lambda root, value: (Path(root) / value).resolve())
    evidence_paths = staticmethod(lambda paths, base: [{'png_dimensions': [1, 1]} for _ in paths])
    gate_lock = staticmethod(lambda path: SimpleNamespace(close=lambda: None))

    @staticmethod
    def readjson(path): return json.loads(Path(path).read_text())
    @staticmethod
    def writejson(path, obj):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        Path(path).write_text(json.dumps(obj))
    @staticmethod
    def verify_candidate(c): return c
    @staticmethod
    def verify_gate(c): WF.calls.append('verify_gate'); return c
    @staticmethod
    def verify_publication(c, root): WF.calls.append('verify_publication')


class GateDispatchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=Path.home() / 'AppData/Local/Temp/opencode') if (Path.home() / 'AppData/Local/Temp/opencode').is_dir() else tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.author = self.base / 'MoonStation14-agent-01'
        self.central = self.base / 'MoonStation14-agent-05'
        self.author.mkdir(); self.central.mkdir()
        WF.git_common = self.base / '.git'
        self.root = self.base / 'work' / 'agent_exchange'; (self.root / 'candidates').mkdir(parents=True)
        self.c = {'id': 'candidate-1', 'status': 'candidate', 'snapshot_key': 'snap',
                  'worktree': {'worktree': str(self.author)}, 'base_ref': 'main',
                  'gate_plan': {'acceptance_criteria': ['works'], 'fixture': 'test.py', 'checks': []},
                  'evidence': [], 'build_identity': 'unit', 'steps': 'run it', 'agent': 'author'}
        self.path = self.root / 'candidates/candidate-1.json'; WF.writejson(self.path, self.c)
        WF.calls = []
        self.metadata = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap',
                         'origin_session_id': 'author-session', 'author_worktree': str(self.author), 'workflow_gate': True}

    def args(self, **kwargs):
        values = {'id': 'candidate-1', 'central_worktree': str(self.central), 'origin_session': 'author-session',
                  'server': 'http://opencode.test', 'wait_timeout': 2}
        values.update(kwargs)
        return SimpleNamespace(**values)

    def gate_api(self, *, outcome=None, session_agent='integration-lead', directory=None, timeout_on_wait=False):
        state = {'created': 0, 'prompts': 0, 'waits': 0}
        directory = directory or str(self.central)
        def call(server, method, path, body=None, timeout=30):
            if path == '/api/info': return 200, {'version': '2.0.18'}
            if path.startswith('/api/agent/'):
                return 200, {'location': {'directory': str(self.central)}, 'data': {'name': 'integration-lead'}}
            if path == '/api/session' and method == 'POST':
                state['created'] += 1
                return 200, {'data': {'id': 'gate-sid', 'agent': session_agent, 'directory': directory, 'metadata': self.metadata}}
            if path == '/api/session/gate-sid' and method == 'GET':
                return 200, {'data': {'id': 'gate-sid', 'agent': session_agent, 'directory': directory, 'metadata': self.metadata}}
            if path.endswith('/prompt'):
                state['prompts'] += 1
                self.assertEqual(body['delivery'], 'queue'); self.assertIn('acceptance_criteria', body['text'])
                self.assertIn('candidate_record', body['text']); self.assertIn('--asset-workspace', body['text'])
                return 200, {'data': []}
            if path.endswith('/wait'):
                state['waits'] += 1
                if timeout_on_wait and state['waits'] == 1: raise d.DispatchError('simulated bounded wait timeout')
                if outcome:
                    current = WF.readjson(self.path)
                    current['review'] = {'result': outcome, 'report_sha256': 'report-hash'}
                    current['gate_outcome'] = {'result': outcome, 'report_sha256': 'report-hash', 'findings': ['fix this']}
                    WF.writejson(self.path, current)
                return 204, None
            raise AssertionError((method, path))
        return state, call

    def test_ready_requires_fresh_core_publication_and_reuses_receipt(self):
        state, api = self.gate_api(outcome='ready_for_owner')
        with patch.object(d, '_request', side_effect=api):
            first = d.request_gate(self.args(), self.root, workflow=WF)
            second = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(first['status'], 'ready_for_owner', first)
        self.assertEqual(second['status'], 'ready_for_owner')
        self.assertEqual((state['created'], state['prompts'], state['waits']), (1, 1, 1))
        self.assertIn('verify_gate', WF.calls); self.assertIn('verify_publication', WF.calls)

    def test_rejection_is_report_bound_and_idempotent(self):
        state, api = self.gate_api(outcome='needs_changes')
        with patch.object(d, '_request', side_effect=api):
            result = d.request_gate(self.args(), self.root, workflow=WF)
            again = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'needs_changes', result); self.assertEqual(again['detail'], ['fix this'])
        self.assertEqual((state['created'], state['prompts']), (1, 1))

    def test_wait_timeout_preserves_session_without_duplicate_prompt(self):
        state, api = self.gate_api(timeout_on_wait=True)
        with patch.object(d, '_request', side_effect=api):
            first = d.request_gate(self.args(), self.root, workflow=WF)
            second = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(first['session_id'], 'gate-sid'); self.assertEqual(first['status'], 'blocked')
        self.assertEqual(second['session_id'], 'gate-sid')
        self.assertEqual((state['created'], state['prompts']), (1, 1))

    def test_wrong_session_and_missing_persisted_outcome_block(self):
        state, api = self.gate_api(session_agent='review')
        with patch.object(d, '_request', side_effect=api):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'blocked'); self.assertIn('unexpected role', result['detail'])
        state, api = self.gate_api()
        with patch.object(d, '_request', side_effect=api):
            result = d.request_gate(self.args(), self.root, workflow=WF)
        self.assertEqual(result['status'], 'blocked', result); self.assertIn('without a persisted gate verdict', result['detail'])

    def test_wrong_central_root_rejected_before_service_use(self):
        args = self.args(central_worktree=str(self.base / 'not-central'))
        def tree(repo, wf): return self.base if Path(repo).resolve() in (self.author, self.central) else Path(repo).resolve()
        with patch.object(d, '_root', side_effect=tree), patch.object(d, '_request') as api:
            result = d.request_gate(args, self.root, workflow=WF)
        self.assertEqual(result['status'], 'blocked'); api.assert_not_called()

    def test_scoped_visual_review_uses_candidate_root_and_copies_bound_image(self):
        image = self.author / 'evidence.png'
        image.write_bytes(b'\x89PNG\r\n\x1a\n' + b'\0' * 16)
        self.c['visual_review_required'] = True
        self.c['evidence'] = [{'path': str(image), 'sha256': WF.sha(image)}]
        WF.writejson(self.path, self.c)
        report = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap', 'reviewer': 'fidelity-reviewer',
                  'decision': 'ready_for_owner', 'findings': ['matches'],
                  'viewed_images': [{'path': str(image), 'sha256': WF.sha(image)}]}
        state = {'created': 0, 'body': None, 'location': None}
        def api(server, method, path, body=None, timeout=30):
            if path == '/api/info': return 200, {'version': '2.0.18'}
            if path.startswith('/api/agent/'):
                self.assertIn('location%5Bdirectory%5D=', path)
                return 200, {'location': {'directory': str(self.author)}, 'data': {'name': 'fidelity-reviewer'}}
            if path == '/api/session' and method == 'POST':
                state['created'] += 1; state['location'] = body['location']['directory']
                return 200, {'data': {'id': 'review-sid', 'agent': 'fidelity-reviewer', 'directory': str(self.author)}}
            if path == '/api/session/review-sid' and method == 'GET':
                return 200, {'data': {'id': 'review-sid', 'agent': 'fidelity-reviewer', 'directory': str(self.author)}}
            if path.endswith('/prompt'):
                state['body'] = body
                self.assertIn('acceptance_criteria', body['text']); return 200, {'data': []}
            if path.endswith('/wait'): return 204, None
            if path.endswith('/message?type=assistant&order=desc&limit=1'):
                return 200, {'data': [{'agent': 'fidelity-reviewer', 'finish': 'stop',
                    'content': [{'type': 'text', 'text': json.dumps(report)}]}]}
            raise AssertionError((method, path))
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_root', return_value=self.base):
            result = d.scoped_review(SimpleNamespace(id='candidate-1', reviewer='fidelity-reviewer',
                actor='integration-lead', server='http://opencode.test', wait_timeout=2), self.root, workflow=WF)
        self.assertEqual(state['location'], str(self.author)); self.assertEqual(result['status'], 'reported')
        self.assertIn("reviewer field MUST be exactly 'fidelity-reviewer'", state['body']['text'])
        brief = json.loads(state['body']['text'].split('\n', 1)[1])
        schema = brief['report_schema']
        self.assertEqual(schema['reviewer'], 'fidelity-reviewer')
        self.assertEqual(schema['decision']['enum'], ['needs_changes', 'ready_for_owner'])
        self.assertEqual(schema['findings']['minItems'], 1)
        self.assertEqual(schema['findings']['maxItems'], 100)
        self.assertEqual(brief['implementation_steps'], 'run it')
        self.assertIn('gate_validation', brief)
        saved = WF.readjson(result['report'])
        binding = saved['original_images'][0]
        self.assertEqual(binding['original_sha256'], WF.sha(image))
        self.assertEqual(WF.sha(Path(result['report']).parent / binding['path']), WF.sha(image))
        image.write_bytes(b'changed')
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_root', return_value=self.base):
            with self.assertRaises(d.DispatchError):
                d.scoped_review(SimpleNamespace(id='candidate-1', reviewer='fidelity-reviewer',
                    actor='integration-lead', server='http://opencode.test', wait_timeout=2), self.root, workflow=WF)

    def test_scoped_review_selects_only_completed_final_json(self):
        report = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap', 'reviewer': 'review',
                  'decision': 'needs_changes', 'findings': ['fix'], 'viewed_images': []}
        def api(server, method, path, body=None, timeout=30):
            if path == '/api/info': return 200, {'version': '2.0.18'}
            if path.startswith('/api/agent/'):
                return 200, {'location': {'directory': str(self.author)}, 'data': {'name': 'review'}}
            if path == '/api/session' and method == 'POST':
                self.assertEqual(body['location']['directory'], str(self.author))
                return 200, {'data': {'id': 'sid', 'agent': 'review', 'directory': str(self.author)}}
            if path == '/api/session/sid' and method == 'GET':
                return 200, {'data': {'id': 'sid', 'agent': 'review', 'directory': str(self.author)}}
            if path.endswith('/prompt'): return 200, {'data': []}
            if path.endswith('/wait'): return 204, None
            if path.endswith('/message?type=assistant&order=desc&limit=1'):
                return 200, {'data': [{'agent': 'review', 'finish': None, 'content': [{'type': 'text', 'text': json.dumps(report)}]},
                                        {'agent': 'review', 'finish': 'stop', 'content': [{'type': 'text', 'text': json.dumps(report)}]}]}
            raise AssertionError(path)
        with patch.object(d, '_request', side_effect=api), patch.object(d, '_root', return_value=self.base):
            result = d.scoped_review(SimpleNamespace(id='candidate-1', reviewer='review', actor='integration-lead',
                server='http://opencode.test', wait_timeout=2), self.root, workflow=WF)
        self.assertEqual(WF.readjson(result['report'])['decision'], 'needs_changes')

    def test_authenticated_default_cli_uses_existing_background_service(self):
        response = SimpleNamespace(returncode=0, stdout='{"version":"2.0.18","finding":"review → fix — preserve UTF-8"}', stderr='')
        with patch.object(d.subprocess, 'run', return_value=response) as run:
            status, info = d._request(None, 'GET', '/api/info')
        argv = run.call_args.args[0]
        self.assertEqual((status, info['version']), (200, '2.0.18'))
        self.assertEqual(info['finding'], 'review → fix — preserve UTF-8')
        self.assertEqual(argv, ['opencode', 'api', 'GET', '/api/info'])
        self.assertEqual(run.call_args.kwargs['encoding'], 'utf-8')
        self.assertNotIn('--server', argv); self.assertNotIn('--standalone', argv)

    def test_explicit_server_and_json_payload_use_authenticated_cli(self):
        response = SimpleNamespace(returncode=0, stdout='{"data":{"id":"s"}}', stderr='')
        payload = {'agent': 'integration-lead', 'location': {'directory': 'C:/central'}}
        with patch.object(d.subprocess, 'run', return_value=response) as run:
            _, result = d._request('http://127.0.0.1:49374', 'POST', '/api/session', payload)
        argv = run.call_args.args[0]
        self.assertEqual(argv[:5], ['opencode', 'api', '--server', 'http://127.0.0.1:49374', 'POST'])
        self.assertEqual(json.loads(argv[-1]), payload)
        self.assertEqual(result['data']['id'], 's')
        self.assertEqual(run.call_args.kwargs['encoding'], 'utf-8')

    def test_invalid_utf8_cli_response_is_a_dispatch_error(self):
        with patch.object(d.subprocess, 'run', side_effect=UnicodeDecodeError('utf-8', b'\xff', 0, 1, 'invalid')):
            with self.assertRaisesRegex(d.DispatchError, 'invalid'):
                d._request(None, 'GET', '/api/info')

    def test_cli_errors_invalid_json_and_oversized_responses_block(self):
        with patch.object(d.subprocess, 'run', return_value=SimpleNamespace(returncode=1, stdout='', stderr='401 Unauthorized')):
            with self.assertRaisesRegex(d.DispatchError, '401 Unauthorized'):
                d._request(None, 'GET', '/api/info')
        with patch.object(d.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout='not json', stderr='')):
            with self.assertRaisesRegex(d.DispatchError, 'invalid JSON'):
                d._request(None, 'GET', '/api/info')
        with patch.object(d.subprocess, 'run', return_value=SimpleNamespace(returncode=0, stdout='x' * (d.MAX_REPLY + 1), stderr='')):
            with self.assertRaisesRegex(d.DispatchError, 'exceeds limit'):
                d._request(None, 'GET', '/api/info')

    def test_review_contract_rejects_provider_name_pass_and_empty_findings(self):
        candidate = {'id': 'candidate-1', 'snapshot_key': 'snap'}
        valid = {'candidate_id': 'candidate-1', 'snapshot_key': 'snap', 'reviewer': 'review',
                 'decision': 'ready_for_owner', 'findings': ['Reviewed current source and validation evidence.'],
                 'viewed_images': []}
        self.assertIs(d._validate_review_report(valid, candidate, 'review'), valid)
        for changes in ({'reviewer': 'GPT-6 Luna'}, {'decision': 'PASS'}, {'findings': []},
                        {'findings': ['']}, {'findings': [' \t\n ']}):
            invalid = dict(valid); invalid.update(changes)
            with self.assertRaises(d.DispatchError):
                d._validate_review_report(invalid, candidate, 'review')


if __name__ == '__main__': unittest.main()
