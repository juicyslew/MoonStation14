import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

STAGING = Path(__file__).resolve().parent
sys.path.insert(0, str(STAGING))
import workflow_runtime as workflow


class MetadataListTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / 'asset' / 'work' / 'agent_exchange'
        self.requests = self.root / 'requests'
        self.requests.mkdir(parents=True)

    def tearDown(self): self.temp.cleanup()

    def add(self, rid, **values):
        folder = self.requests / rid
        folder.mkdir()
        data = {'id': rid, 'status': 'open', 'title': rid, 'scope': 'scope', 'context_snapshot': []}
        data.update(values)
        (folder / 'request.json').write_text(json.dumps(data), encoding='utf8')
        return folder

    def run_list(self, *args):
        output = io.StringIO()
        with mock.patch.object(sys, 'argv', ['workflow_runtime.py', '--asset-workspace', str(self.root.parents[1]), 'list', *args]), contextlib.redirect_stdout(output):
            code = workflow.main()
        return code, json.loads(output.getvalue())

    def test_no_create(self):
        missing = Path(self.temp.name) / 'absent'
        with mock.patch.object(sys, 'argv', ['workflow_runtime.py', '--asset-workspace', str(missing), 'list', '--json']), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(workflow.main(), 0)
        self.assertFalse(missing.exists())

    def test_metadata_only_large_queue_and_no_integrity_helpers(self):
        for i in range(250): self.add(f'req-{i:03d}', context_snapshot=[{'path': f'p{i}'}], delivery={'source_root':'missing-root','files':[{'source':'missing-file'}]}, review={'report_path':'missing-review'})
        with mock.patch.object(workflow, 'verify_request', side_effect=AssertionError), mock.patch.object(workflow, 'verify_delivery', side_effect=AssertionError), mock.patch.object(workflow, 'current_review', side_effect=AssertionError), mock.patch.object(workflow, 'sha', side_effect=AssertionError):
            code, data = self.run_list('--json')
        self.assertEqual(code, 0); self.assertEqual(len(data['requests']), 250)
        self.assertEqual(data['mode'], 'metadata-discovery'); self.assertFalse(data['integrity_verified'])

    def test_open_filter_and_sorted(self):
        self.add('zeta'); self.add('alpha'); self.add('closed', status='claimed')
        _, data = self.run_list('--open', '--json')
        self.assertEqual([r['request_id'] for r in data['requests']], ['alpha', 'zeta'])

    def test_claim_reservation_and_bad_claim_skip_open_and_unknown_claimant(self):
        claimed = self.add('claimed'); (claimed/'claim.json').write_text('{broken')
        _, all_data = self.run_list('--json')
        found = next(r for r in all_data['requests'] if r['request_id']=='claimed')
        self.assertEqual(found['claimant'], 'unknown')
        _, open_data = self.run_list('--open', '--json')
        self.assertNotIn('claimed', [r['request_id'] for r in open_data['requests']])

    def test_invalid_metadata_visible(self):
        self.add('wrong', id='other')
        _, data = self.run_list('--json')
        self.assertEqual(data['requests'][0]['status'], 'error')
        self.assertIn('identity mismatch', data['requests'][0]['error'])

    def test_large_valid_request_metadata_is_discovered(self):
        folder = self.add('huge', delivery={'files':[{'path': f'asset/{i}', 'metadata': 'x'*100} for i in range(300)]})
        _, data = self.run_list('--json')
        self.assertEqual(data['requests'][0]['request_id'], 'huge')
        self.assertEqual(data['requests'][0]['claimant'], None)

    def test_invalid_context_shape_reported_without_crash_and_fields_sanitized(self):
        self.add('bad-context', context_snapshot={'path':'not-list'}, title={'private':'object'}, scope=['not-string'])
        _, data = self.run_list('--json')
        self.assertEqual(data['requests'][0]['status'], 'error')
        self.assertIn('context_snapshot', data['requests'][0]['error'])

    def test_legacy_context_paths(self):
        self.add('legacy', context=['guide.md', 'images/view.png'], context_snapshot=None)
        _, data = self.run_list('--json')
        self.assertEqual(data['requests'][0]['context_paths'], ['guide.md', 'images/view.png'])

    def test_claim_still_verifies_snapshot(self):
        self.add('selected')
        with mock.patch.object(sys, 'argv', ['workflow_runtime.py', '--asset-workspace', str(self.root.parents[1]), 'claim', 'selected', '--agent', 'tester']), contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(workflow.main(), 2)
        self.assertIn('request snapshot key mismatch', err.getvalue())
        self.assertFalse((self.requests / 'selected' / 'claim.json').exists())


if __name__ == '__main__': unittest.main()
