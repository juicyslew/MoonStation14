"""Admission-root scheduler exclusion lifecycle tests."""
import tempfile
import unittest
from pathlib import Path
from unittest import mock
import subprocess
import sys
import time

import workflow_coordinator


class Store:
    def __init__(self, path): self.path = path
    def claim_outbox(self, **kwargs): return None
    def outbox_summary(self): return {}


class SchedulerGuardTests(unittest.TestCase):
    def make(self, root, name='db', recovery=None, handlers=None):
        return workflow_coordinator.ExplicitCoordinator(
            Store(Path(root) / name), bindings={'token': object()}, artifact_dir=root,
            identity_verifier=lambda _: True, admission_root=root,
            native_recovery=recovery, handlers=handlers)

    def test_same_thread_second_database_is_rejected_and_clean_stop_releases(self):
        with tempfile.TemporaryDirectory() as root:
            first = self.make(root, 'one.db'); second = self.make(root, 'two.db')
            first.start()
            try:
                with self.assertRaises(BlockingIOError): second.start()
                self.assertTrue(first.summary()['workflow_observer_guard']['owned'])
            finally: first.stop()
            second.start(); second.stop()

    def test_startup_failure_releases_exchange_and_database_guards(self):
        with tempfile.TemporaryDirectory() as root:
            recovery = mock.Mock(); recovery.reconcile.side_effect = RuntimeError('recovery')
            failed = self.make(root, recovery=recovery)
            with self.assertRaisesRegex(RuntimeError, 'recovery'): failed.start()
            healthy = self.make(root)
            healthy.start(); healthy.stop()

    def test_stop_failure_retains_exchange_guard_until_successful_cleanup(self):
        with tempfile.TemporaryDirectory() as root:
            handler = mock.Mock(); handler.close.side_effect = [RuntimeError('close'), None]
            first = self.make(root, handlers={'unused': handler}); first.start()
            with self.assertRaisesRegex(RuntimeError, 'close'): first.stop()
            second = self.make(root, 'other.db')
            with self.assertRaises(BlockingIOError): second.start()
            first.stop()
            second.start(); second.stop()

    def test_exchange_lock_acquired_before_recovery_and_rootless_behavior(self):
        with tempfile.TemporaryDirectory() as root:
            recovery = mock.Mock(); recovery.reconcile.return_value = {}
            coordinator = self.make(root, recovery=recovery)
            with mock.patch.object(workflow_coordinator.workflow_runtime, 'gate_lock', wraps=workflow_coordinator.workflow_runtime.gate_lock) as lock:
                coordinator.start()
                self.assertEqual(lock.call_args_list[0].args[0], Path(root) / 'workflow-observer')
                coordinator.stop()
            isolated = workflow_coordinator.ExplicitCoordinator(Store(Path(root)/'isolated.db'), bindings={'x':object()}, artifact_dir=root, identity_verifier=lambda _: True)
            isolated.start(); isolated.stop()

    def test_cross_process_observer_mutex_excludes_both_directions_and_releases(self):
        with tempfile.TemporaryDirectory() as root:
            queue = str(Path(root) / 'workflow-observer')
            holder_code = ("import sys; import workflow_runtime; "
                           "lock=workflow_runtime.gate_lock(__import__('pathlib').Path(sys.argv[1]), nonblocking=True); "
                           "print('LOCKED', flush=True); sys.stdin.readline(); lock.close()")
            holder = subprocess.Popen([sys.executable, '-u', '-c', holder_code, queue],
                                      stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                      stderr=subprocess.PIPE, text=True)
            try:
                self.assertEqual(holder.stdout.readline().strip(), 'LOCKED')
                coordinator = self.make(root)
                with self.assertRaises((BlockingIOError, TimeoutError)):
                    coordinator.start()
                self.assertIsNone(coordinator._thread)
            finally:
                if holder.poll() is None:
                    holder.stdin.write('\n'); holder.stdin.flush()
                    try: holder.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        holder.kill(); holder.wait(timeout=2)
                holder.stdout.close(); holder.stdin.close(); holder.stderr.close()

            coordinator = self.make(root); coordinator.start()
            try:
                reverse = subprocess.run([sys.executable, '-c',
                "import sys; import workflow_runtime; "
                "lock=workflow_runtime.gate_lock(__import__('pathlib').Path(sys.argv[1]), nonblocking=True); lock.close()",
                queue], capture_output=True, text=True, timeout=5)
                self.assertNotEqual(reverse.returncode, 0)
            finally: coordinator.stop()
            reverse = subprocess.run([sys.executable, '-c',
                "import sys; import workflow_runtime; "
                "lock=workflow_runtime.gate_lock(__import__('pathlib').Path(sys.argv[1]), nonblocking=True); lock.close()",
                queue], capture_output=True, text=True, timeout=5)
            self.assertEqual(reverse.returncode, 0, reverse.stderr)


if __name__ == '__main__': unittest.main()
