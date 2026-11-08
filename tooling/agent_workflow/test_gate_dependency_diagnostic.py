import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tooling.agent_workflow import gate_liveness as live
from tooling.agent_workflow import test_gate_liveness as fixtures
from tooling.agent_workflow import workflow_runtime as runtime


class GateDependencyDiagnosticTests(unittest.TestCase):
    def setUp(self):
        from tooling.agent_workflow import gate_dispatch, gate_execution
        aliases=patch.dict('sys.modules', {'gate_dispatch':gate_dispatch,
                                           'gate_execution':gate_execution,
                                           'gate_liveness':live})
        aliases.start(); self.addCleanup(aliases.stop)

    def test_persisted_diagnostic_terminal_delivers_without_mcp_or_lead_wake(self):
        with tempfile.TemporaryDirectory() as d:
            candidate, workflow, _ = fixtures.GateLivenessTests().setup_gate(d, outcome={'result': 'diagnostic_complete'})
            candidate['gate_plan'] = {'kind': 'diagnostic'}
            runtime.writejson(Path(d) / 'candidates' / 'candidate.json', candidate)
            with patch('tooling.agent_workflow.gate_dispatch.deliver_gate_completion',
                       return_value={'status': 'delivered'}) as deliver:
                result = live.reconcile(d, workflow=workflow,
                    request=lambda *a, **k: self.fail('terminal diagnostic must not wake the lead'), now=1000)
            deliver.assert_called_once()
            self.assertEqual(result['pending'], 0)
            self.assertEqual(live.read_health(d, 'candidate')['state'], 'finished')

    def test_completed_readiness_waits_offline_then_reuses_bound_review_prompt(self):
        with tempfile.TemporaryDirectory() as d:
            candidate, workflow, _, execution = fixtures.GateLivenessTests().completed_review_fixture(d)
            health = live.read_health(d, 'candidate')
            body = {'id': 'msg_cached-review', 'text': 'continue this review', 'delivery': 'steer',
                    'resume': True, 'metadata': {'workflow_gate_execution_review': True,
                    'candidate_id': 'candidate', 'snapshot_key': 'snap', 'job_id': 'candidate',
                    'receipt_sha256': runtime.sha(Path(d) / 'receipt.json')}}
            health['execution_review_delivery'] = {'body': body, 'job_id': 'candidate',
                'receipt_sha256': body['metadata']['receipt_sha256']}
            runtime.writejson(Path(d) / 'gate-health' / 'candidate.json', health)
            posts = []

            def offline(server, method, path, payload=None, timeout=15):
                if path.startswith('/api/mcp?'):
                    return 200, {'location': {'directory': candidate['gate_request']['central_worktree']},
                                 'data': [{'name': 'ms14_workflow', 'status': {'status': 'disabled'}}]}
                if method == 'POST': self.fail('offline readiness must not wake the lead')
                raise AssertionError((method, path))

            with patch.object(execution, 'reconcile', return_value={}):
                result = live.reconcile(d, workflow=workflow, request=offline, now=1000)
                saved = live.read_health(d, 'candidate')
                self.assertEqual(result['pending'], 1)
                self.assertEqual(saved['execution_review_delivery']['body'], body)
                self.assertEqual(saved['recovery_count'], 0)

                def connected(server, method, path, payload=None, timeout=15):
                    if method == 'POST':
                        posts.append((path, payload))
                        return 200, {'id': payload['id'], 'sessionID': 'lead',
                                     'payload': {'text': payload['text'], 'metadata': payload['metadata']}}
                    if path == '/api/session/active': return 200, {'data': {}}
                    if path.endswith('/inbox'): return 200, {'data': []}
                    if path.startswith('/api/mcp?'):
                        return 200, {'location': {'directory': candidate['gate_request']['central_worktree']},
                                     'data': [{'name': 'ms14_workflow', 'status': {'status': 'connected'}}]}
                    raise AssertionError((method, path))

                live.reconcile(d, workflow=workflow, request=connected, now=1001)
            self.assertEqual(len(posts), 1)
            self.assertEqual(posts[0][1], body)
            self.assertIn('/api/session/lead/prompt', posts[0][0])


if __name__ == '__main__':
    unittest.main()
