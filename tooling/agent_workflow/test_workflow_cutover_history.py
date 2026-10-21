import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest

import workflow_cutover_history as history
import workflow_coordinator_package as package


class CutoverHistoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.old_localappdata = os.environ.get('LOCALAPPDATA')
        os.environ['LOCALAPPDATA'] = str(self.root)
        self.private = self.root / 'Temp' / 'opencode'
        self.private.mkdir(parents=True)
        self.exchange = self.root / 'exchange'
        self.exchange.mkdir()
        self.html = b'<h1>Legacy board</h1>\n'
        (self.exchange / history.BOARD).write_bytes(self.html)
        self.receipt = self.root / 'prior-receipt.json'
        self.receipt.write_text('{"status":"applied"}', encoding='utf-8')
        self.receipt_sha = hashlib.sha256(self.receipt.read_bytes()).hexdigest()

    def tearDown(self):
        if self.old_localappdata is None:
            os.environ.pop('LOCALAPPDATA', None)
        else:
            os.environ['LOCALAPPDATA'] = self.old_localappdata
        self.temp.cleanup()

    def capture(self, **kw):
        output = self.private / ('wr12-legacy-history-' + str(kw.pop('name', 'one')))
        return history.capture(self.exchange, output, self.receipt, self.receipt_sha, 'op-test')

    def test_capture_exact_bytes_projection_and_existing_bytes_preserved(self):
        before = {p.name: p.read_bytes() for p in self.exchange.iterdir()}
        result = self.capture()
        projected = history.read_projection(self.exchange)
        index = json.loads((self.exchange / history.INDEX).read_text(encoding='utf-8'))
        manifest = json.loads(Path(index['manifest']).read_text(encoding='utf-8'))
        saved = Path(manifest['board']['path'])
        self.assertEqual(saved.read_bytes(), self.html)
        self.assertEqual(manifest['board']['sha256'], hashlib.sha256(self.html).hexdigest())
        self.assertEqual(manifest['previous_receipt']['sha256'], self.receipt_sha)
        self.assertEqual(projected['sha256'], manifest['board']['sha256'])
        self.assertEqual({k: (self.exchange / k).read_bytes() for k in before}, before)

    def test_absent_index_is_noop_and_conflicting_index_refuses(self):
        self.assertIsNone(history.read_projection(self.exchange))
        index = self.exchange / history.INDEX
        index.write_bytes(b'foreign')
        with self.assertRaises(ValueError):
            self.capture()
        self.assertEqual(index.read_bytes(), b'foreign')

    def test_bad_receipt_does_not_publish_index(self):
        with self.assertRaises(ValueError):
            history.capture(self.exchange, self.private / 'wr12-legacy-history-bad', self.receipt, '0' * 64, 'op')
        self.assertFalse((self.exchange / history.INDEX).exists())

    def test_tampered_board_reports_unavailable(self):
        self.capture()
        index = json.loads((self.exchange / history.INDEX).read_text())
        manifest = json.loads(Path(index['manifest']).read_text())
        Path(manifest['board']['path']).write_bytes(b'tampered')
        projected = history.read_projection(self.exchange)
        self.assertFalse(projected['available'])
        self.assertNotIn('tampered', str(projected))

    def test_installed_source_allowlist_contains_helper(self):
        self.assertIn('workflow_cutover_history.py', package.SOURCES)


if __name__ == '__main__':
    unittest.main()
