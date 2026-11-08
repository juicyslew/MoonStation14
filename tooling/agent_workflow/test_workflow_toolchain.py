import tempfile
import unittest
from pathlib import Path

import workflow_toolchain


class ToolchainCaptureTests(unittest.TestCase):
    def test_capture_is_stable_and_binds_executable_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            executable = repo / 'python.exe'
            executable.write_bytes(b'python-one')
            check = {'argv': [str(executable)]}
            first = workflow_toolchain.capture(check, repo)
            self.assertEqual(first, workflow_toolchain.capture(check, repo))
            executable.write_bytes(b'python-two')
            self.assertNotEqual(first, workflow_toolchain.capture(check, repo))

    def test_missing_executable_is_deterministic(self):
        with tempfile.TemporaryDirectory() as directory:
            result = workflow_toolchain.capture({'argv': ['missing-workflow-tool']}, directory)
            self.assertEqual(result['executable']['sha256'], None)
            self.assertEqual(result['executable']['size_bytes'], None)


if __name__ == '__main__':
    unittest.main()
