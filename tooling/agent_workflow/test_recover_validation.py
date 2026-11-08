import io
import json
import unittest
import urllib.error
from contextlib import redirect_stdout
from io import StringIO

import recover_validation as recovery


class Response:
    def __init__(self, value): self.value = value
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def __init_subclass__(cls, **kwargs): pass
    def read(self): return json.dumps(self.value).encode()


class Opener:
    def __init__(self, snapshot, failure=None): self.snapshot, self.failure, self.calls = snapshot, failure, []
    def open(self, req, timeout=None):
        self.calls.append(req)
        if self.failure and req.full_url.endswith('/v1/cancel'): raise urllib.error.HTTPError(req.full_url, 409, 'conflict', {}, io.BytesIO(json.dumps({'error': self.failure}).encode()))
        if req.full_url.endswith('/integration-status'):
            class Html:
                def __enter__(self): return self
                def __exit__(self, *args): pass
                def read(self): return b'<html>owner ui</html>'
            return Html()
        if '/v1/owner/snapshot?' in req.full_url: return Response(self.snapshot)
        return Response({"accepted": True, "receipt": {"state": "RETIRED_HEAD", "historical_result": "ERROR"}})


def snapshot(state='ERROR', diagnostics=True):
    result = {'decision': state, 'findings': ['bad']} if diagnostics else {'decision': state}
    if diagnostics: result.update(error_code='CHECK_FAILED', error_message='check failed')
    return {'run': {'run_id': 'r'}, 'attempt': {'phase': 'DECIDED', 'cleanup_state': 'CLEAN'}, 'result': result, 'generation': 7, 'expected_sequence': 12}


class RecoveryTests(unittest.TestCase):
    def test_default_is_read_only_and_reports(self):
        client = Opener(snapshot())
        out = StringIO()
        with redirect_stdout(out): recovery.run('http://127.0.0.1:1234/', 'r', opener=client)
        self.assertEqual(len(client.calls), 2)
        self.assertTrue(client.calls[1].full_url.endswith('run_id=r'))
        self.assertIn('historicalcleanup=CLEAN', out.getvalue())

    def test_exact_retirement_payload_and_origin(self):
        client = Opener(snapshot())
        out = StringIO()
        with redirect_stdout(out): result = recovery.run('http://localhost:1234', 'r', True, 'stable-id', client)
        req = client.calls[-1]
        self.assertEqual(req.get_header('Origin'), 'http://localhost:1234')
        self.assertEqual(json.loads(req.data), {'contract_version': 1, 'run_id': 'r', 'evaluation_generation': 7, 'expected_sequence': 12, 'command_id': 'stable-id', 'actor': 'OWNER', 'reason': 'CANCELLED'})
        self.assertEqual(result['receipt']['historical_result'], 'ERROR')
        self.assertIn('Recovery: RETIRED_HEAD', out.getvalue())

    def test_fail_refused_and_missing_diagnostics_reported(self):
        with self.assertRaisesRegex(RuntimeError, 'FAIL'):
            recovery.run('http://127.0.0.1:1234', 'r', True, 'id', Opener(snapshot('FAIL')))
        with self.assertRaisesRegex(ValueError, 'command-id'):
            recovery.run('http://127.0.0.1:1234', 'r', True, opener=Opener(snapshot()))
        recovery.run('http://127.0.0.1:1234', 'r', opener=Opener(snapshot(diagnostics=False)))

    def test_conflict_cause_and_endpoint_validation(self):
        with self.assertRaisesRegex(RuntimeError, 'STALE.*sequence changed'):
            recovery.run('http://127.0.0.1:1234', 'r', True, 'id', Opener(snapshot(), {'code':'STALE','message':'sequence changed'}))
        for endpoint in ('https://127.0.0.1:12', 'http://example.com:12', 'http://127.0.0.1', 'http://u:p@127.0.0.1:12', 'http://127.0.0.1:12/x', 'http://127.0.0.1:12/?x=1'):
            with self.assertRaises(ValueError): recovery.run(endpoint, 'r', opener=Opener(snapshot()))


if __name__ == '__main__': unittest.main()
