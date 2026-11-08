"""Managed GameTest logging execution and packaging contract."""
import hashlib
from pathlib import Path
import tempfile
import unittest
import os
import subprocess
import sys

import workflow_coordinator_package
import workflow_runtime
import workflow_toolchain


class ValidationLoggingTests(unittest.TestCase):
    def test_package_import_context_can_capture_gradle_argv(self):
        root = Path(__file__).resolve().parents[2]
        env = dict(os.environ)
        env['PYTHONPATH'] = str(root)
        code = "from tooling.agent_workflow import workflow_toolchain; print(workflow_toolchain.capture({'argv':['gradle','help']}, '.')['executable']['path'])"
        result = subprocess.run([sys.executable, '-c', code], cwd=root, env=env, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_policy_metadata_tracks_actual_info_and_debug_argv(self):
        check = {'argv': ['./gradlew', 'runGameTestServer']}
        info_argv = workflow_runtime.execution_argv('.', check['argv'])
        self.assertIn('-Pms14ValidationDebug=false', info_argv)
        self.assertEqual('INFO', workflow_toolchain.capture(check, '.') ['validation_logging']['mode'])

        debug = {'argv': ['./gradlew', 'runGameTestServer', '-Pms14ValidationDebug=true']}
        evidence = workflow_toolchain.capture(debug, '.')
        logging = evidence['validation_logging']
        self.assertEqual('DEBUG', logging['mode'])
        self.assertEqual('ms14-gametest-logging-v1', logging['policy_id'])
        asset = Path(logging['init_script']['path'])
        self.assertEqual(hashlib.sha256(asset.read_bytes()).hexdigest(), logging['init_script']['sha256'])
        self.assertIn('tooling/agent_workflow/validation-logging.gradle', workflow_coordinator_package.ROOT_ASSETS)

    def test_invalid_and_duplicate_debug_properties_fail_closed(self):
        for value in ('yes', 'TRUE'):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, 'true or false'):
                workflow_runtime.execution_argv('.', ['gradle', 'runGameTestServer', f'-Pms14ValidationDebug={value}'])
        with self.assertRaisesRegex(ValueError, 'once'):
            workflow_runtime.execution_argv('.', ['gradle', 'runGameTestServer', '-Pms14ValidationDebug=true', '-Pms14ValidationDebug=false'])

    def test_non_gradle_game_test_name_is_not_adapted(self):
        argv = ['python', 'runGameTestServer']
        self.assertEqual(argv, workflow_runtime.execution_argv('.', argv))


if __name__ == '__main__':
    unittest.main()
