import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

from tooling.agent_workflow import workflow_service


class WorkflowServiceTests(unittest.TestCase):
    def test_installation_requires_receipt_hash(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            receipt = root / 'receipt.json'
            receipt.write_text(json.dumps({'shared_root': str(root)}), encoding='utf-8')
            pointer = root / '.opencode/workflow/current-installation.json'
            pointer.parent.mkdir(parents=True)
            pointer.write_text(json.dumps({'receipt': str(receipt), 'receipt_sha256': '0' * 64}), encoding='utf-8')
            with self.assertRaisesRegex(ValueError, 'receipt hash mismatch'):
                workflow_service.installation(root)

    def test_installation_accepts_verified_receipt(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            shared = root / 'shared'
            (shared / 'tooling/agent_workflow').mkdir(parents=True)
            (shared / 'tooling/agent_workflow/deploy_workflow.py').touch()
            receipt = root / 'receipt.json'
            data = json.dumps({'shared_root': str(shared)}).encode()
            receipt.write_bytes(data)
            pointer = root / '.opencode/workflow/current-installation.json'
            pointer.parent.mkdir(parents=True)
            pointer.write_text(json.dumps({'receipt': str(receipt), 'receipt_sha256': hashlib.sha256(data).hexdigest()}), encoding='utf-8')
            record, decoded, resolved = workflow_service.installation(root)
            self.assertEqual(record['receipt_sha256'], hashlib.sha256(data).hexdigest())
            self.assertEqual(decoded['shared_root'], str(shared))
            self.assertEqual(resolved, shared.resolve())

    def test_healthy_service_is_not_started_again(self):
        start = Mock()
        result, started = workflow_service.ensure_running(lambda: ('health', 'snapshot'), start)
        self.assertEqual(result, ('health', 'snapshot'))
        self.assertFalse(started)
        start.assert_not_called()

    def test_stopped_service_starts_once_then_verifies(self):
        start = Mock()
        check = Mock(side_effect=[OSError('not available'), ('health', 'snapshot')])
        result, started = workflow_service.ensure_running(check, start)
        self.assertEqual(result, ('health', 'snapshot'))
        self.assertTrue(started)
        start.assert_called_once_with()
        self.assertEqual(check.call_count, 2)

    def test_live_unhealthy_identity_prevents_duplicate_start(self):
        start = Mock()
        with self.assertRaisesRegex(ValueError, 'still alive'):
            workflow_service.ensure_running(lambda: (_ for _ in ()).throw(OSError()), start,
                                            recorded_process_alive=True)
        start.assert_not_called()

    def test_occupied_port_prevents_duplicate_start(self):
        start = Mock()
        with self.assertRaisesRegex(ValueError, 'port is occupied'):
            workflow_service.ensure_running(lambda: (_ for _ in ()).throw(OSError()), start,
                                            port_occupied=True)
        start.assert_not_called()

    def test_status_mode_never_starts(self):
        start = Mock()
        with self.assertRaisesRegex(ValueError, 'not verified'):
            workflow_service.ensure_running(lambda: (_ for _ in ()).throw(OSError()), start,
                                            inspect_only=True)
        start.assert_not_called()

    def test_maintenance_health_is_reported_without_mutation(self):
        start = Mock()
        snapshot = {'orchestration_paused_for_maintenance': True}
        result, started = workflow_service.ensure_running(lambda: ('running', snapshot), start)
        self.assertTrue(result[1]['orchestration_paused_for_maintenance'])
        self.assertFalse(started)
        start.assert_not_called()


if __name__ == '__main__':
    unittest.main()
