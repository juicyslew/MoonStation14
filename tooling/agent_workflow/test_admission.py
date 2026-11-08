import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
import subprocess
import contextlib
import io
from types import SimpleNamespace

from tooling.agent_workflow import admission


class AdmissionTests(unittest.TestCase):
    def test_hold_allowlist_and_resume(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            hold = root / 'gate-maintenance.json'
            self.assertIsNone(admission.maintenance(root, 'c1'))
            hold.write_text(json.dumps({'owner': 'owner', 'note': 'draining', 'allowed_candidate_ids': ['verify']}))
            self.assertEqual(admission.maintenance(root, 'c1')['status'], 'held')
            self.assertIsNone(admission.maintenance(root, 'verify'))
            self.assertIsNone(admission.maintenance(root, 'c1', admitted=True))
            hold.write_text('{')
            self.assertIn('malformed', admission.maintenance(root, 'c1')['note'])
            hold.unlink()
            self.assertIsNone(admission.maintenance(root, 'c1'))

    def test_guarded_legacy_submit_holds_before_legacy_validation(self):
        repo = Path(__file__).parents[2]
        staged = repo / 'build/reports/neutral-cutover/guarded-installed/workflow.py'
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'work/agent_exchange'
            root.mkdir(parents=True)
            (root / 'gate-maintenance.json').write_text(json.dumps({'owner': 'parent', 'note': 'drain', 'allowed_candidate_ids': []}))
            env = dict(os.environ, PYTHONPATH=str(repo / 'tooling/agent_workflow'))
            result = subprocess.run([sys.executable, str(staged), '--asset-workspace', tmp, 'submit', 'candidate-1', '--agent', 'author', '--build-identity', 'test', '--steps', 'test'], cwd=repo, env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('"status": "held"', result.stdout)
            self.assertFalse((root / 'candidates').exists())

    def test_prepared_submit_hold_precedes_sprint_and_git_work(self):
        from tooling.agent_workflow import workflow_runtime
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'gate-maintenance.json').write_text(json.dumps({'owner': 'parent', 'note': 'paused', 'allowed_candidate_ids': []}))
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                workflow_runtime.cmd_submit(SimpleNamespace(id='candidate-1'), root)
            self.assertIn('"status": "held"', out.getvalue())
            self.assertFalse((root / 'candidates').exists())


if __name__ == '__main__':
    unittest.main()
