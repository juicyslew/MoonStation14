import json
import tempfile
import unittest
from unittest import mock
from pathlib import Path

from tooling.agent_workflow import admission


class IntakeControlTests(unittest.TestCase):
    def test_exclusive_hold_and_owned_resume(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            receipt = admission.begin_hold(root, owner='owner', control_id='c1', note='paused', drain_candidate_ids=['drain'])
            self.assertEqual(admission.maintenance(root, 'x')['note'], 'paused')
            with self.assertRaises(FileExistsError):
                admission.begin_hold(root, owner='owner', control_id='c2', note='again')
            with self.assertRaises(ValueError):
                admission.resume_hold(root, dict(receipt, token='foreign'))
            admission.resume_hold(root, receipt)
            self.assertIsNone(admission.maintenance(root))

    def test_live_submit_consumer_is_held_before_identity_and_store_effects(self):
        import json
        from tooling.agent_workflow import workflow_coordinator, workflow_store
        from tooling.agent_workflow.preflight_test_support import make_preflight_case
        from starlette.testclient import TestClient
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); case = make_preflight_case(root, caller='caller', key='held')
            store = workflow_store.WorkflowStore(root / 'state.sqlite')
            holdroot = root / 'exchange'; holdroot.mkdir()
            (holdroot / 'gate-maintenance.json').write_text('{')
            calls = []
            binding = workflow_coordinator.CoordinatorBinding('caller', case.context)
            coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={'secret': binding}, artifact_dir=root / 'artifacts',
                identity_verifier=lambda ctx: calls.append('identity') or True, admission_root=holdroot)
            with TestClient(coordinator.app()) as client:
                response = client.post('/v1/submit', headers={'Authorization':'Bearer secret'}, json={
                    'idempotency_key':'held', 'request':dict(case.request, admitted=True, allowed_candidate_ids=['held'])})
                self.assertEqual(response.status_code, 503)
                self.assertEqual(response.json()['error']['code'], 'INTAKE_HELD')
                self.assertEqual(calls, [])
                self.assertIsNone(store.get_submission('caller', 'held'))
                self.assertEqual(client.get('/v1/status', headers={'Authorization':'Bearer secret'}).status_code, 200)

    def test_config_loader_binds_explicit_admission_root(self):
        import json
        from unittest.mock import patch
        from tooling.agent_workflow import admission
        from tooling.agent_workflow import workflow_coordinator
        from tooling.agent_workflow.preflight_test_support import make_preflight_case
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); case = make_preflight_case(root, caller='cfg-author', key='cfg')
            context = root / 'context.json'
            context.write_text(json.dumps({'repo':str(case.repo),'authenticated_author':case.context.authenticated_author,
                'policy':case.context.policy,'artifacts':case.context.artifacts,
                'publication_receipt_path':str(case.context.publication_receipt_path),
                'publication_receipt_sha256':case.context.publication_receipt_sha256}))
            (root/'author.token').write_text('author-token')
            exchange = root/'explicit-exchange'; exchange.mkdir()
            (exchange/'gate-maintenance.json').write_text(json.dumps({'owner':'test','note':'config hold'}))
            config = root/'coordinator.json'
            config.write_text(json.dumps({'database':'db.sqlite','artifact_dir':'artifacts','opencode_server':'http://127.0.0.1:4096',
                'admission_root':str(exchange),'bindings':[{'token_file':'author.token','caller':'author','context_file':'context.json'}]}))
            with patch.object(workflow_coordinator, 'configure_review_pipeline'):
                coordinator = workflow_coordinator.load_coordinator(config)
            try:
                self.assertEqual(coordinator.admission_root, exchange)
                self.assertEqual(admission.maintenance(coordinator.admission_root)['note'], 'config hold')
            finally:
                coordinator.store.close()


class HeldIntakeExistingRouteTests(__import__('tooling.agent_workflow.test_workflow_store_delivery', fromlist=['DeliveryEnqueueTests']).DeliveryEnqueueTests):
    def test_status_and_valid_author_ack_remain_available_under_hold(self):
        import hashlib
        from starlette.testclient import TestClient
        from tooling.agent_workflow import workflow_coordinator
        from tooling.agent_workflow import admission
        from tooling.agent_workflow.workflow_store import canonical_json
        holdroot = Path(self.tmp.name) / 'exchange'; holdroot.mkdir()
        (holdroot/'gate-maintenance.json').write_text(json.dumps({'owner':'test','note':'held'}))
        delivery_id = 'delivery_' + hashlib.sha256((self.event['event_id'] + 'AUTHOR' + canonical_json(self.run['author'])).encode()).hexdigest()[:24]
        self.enqueue(delivery_id, 'AUTHOR', self.run['author'])
        context = type('Context', (), {'authenticated_author':self.run['author'], 'repo':'worktree'})()
        coordinator = workflow_coordinator.ExplicitCoordinator(self.store,
            bindings={'token':workflow_coordinator.CoordinatorBinding('author-caller',context)}, artifact_dir=self.tmp.name,
            identity_verifier=lambda _:self.fail('ACK must not query identity'), admission_root=holdroot)
        original = admission.maintenance; checked=[]
        def observed(root, *args, **kwargs):
            checked.append(root); return original(root, *args, **kwargs)
        from unittest.mock import patch
        ack={'contract_version':1,'event_id':self.event['event_id'],'run_id':self.event['run_id'],
            'evaluation_generation':self.event['evaluation_generation'],'result_id':self.event['result_id'],
            'result_digest':self.event['result_digest'],'command_id':'held-ack'}
        with patch.object(admission, 'maintenance', side_effect=observed):
            with TestClient(coordinator.app()) as client:
                headers={'Authorization':'Bearer token'}
                self.assertEqual(client.get('/v1/status',headers=headers).status_code,200)
                response=client.post('/v1/acknowledge-result',headers=headers,json=ack)
                self.assertEqual(response.status_code,200,response.text)
        self.assertEqual(checked, [])

    def test_existing_manual_and_malformed_holds_are_never_replaced(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); path = root / 'gate-maintenance.json'
            for data in (json.dumps({'owner': 'manual', 'note': 'keep'}), '{'):
                path.write_text(data)
                before = path.read_bytes()
                with self.assertRaises(FileExistsError):
                    admission.begin_hold(root, owner='worker', control_id='c1', note='x')
                self.assertEqual(path.read_bytes(), before)

    def test_resume_rejects_changed_hold(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            receipt = admission.begin_hold(root, owner='owner', control_id='c1', note='paused')
            (root / 'gate-maintenance.json').write_text(json.dumps({'owner': 'foreign'}))
            with self.assertRaises(ValueError): admission.resume_hold(root, receipt)

    @unittest.skipUnless(__import__('os').name == 'nt', 'Windows file-sharing guarantee')
    def test_resume_refuses_concurrent_replacement_handle(self):
        import ctypes
        from ctypes import wintypes
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); receipt = admission.begin_hold(root, owner='o', control_id='c', note='n')
            path = root / 'gate-maintenance.json'; before = path.read_bytes()
            kernel = ctypes.WinDLL('kernel32', use_last_error=True)
            kernel.CreateFileW.restype = wintypes.HANDLE
            kernel.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, wintypes.LPVOID,
                                           wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
            handle = kernel.CreateFileW(str(path), 0x40000000 | 0x00010000, 0x1, None, 3, 0x00200000, None)
            self.assertNotEqual(handle, wintypes.HANDLE(-1).value)
            try:
                with self.assertRaises(OSError): admission.resume_hold(root, receipt)
            finally:
                kernel.CloseHandle(handle)
            self.assertEqual(path.read_bytes(), before)
            self.assertEqual(admission.maintenance(root)['status'], 'held')

    def test_root_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); target = root / 'target'; target.mkdir(); link = root / 'link'
            try: link.symlink_to(target, target_is_directory=True)
            except OSError: self.skipTest('symlink creation unavailable')
            with self.assertRaises(ValueError): admission.begin_hold(link, owner='o', control_id='c', note='n')

    def test_write_failure_leaves_fail_closed_partial_hold(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with mock.patch('os.fsync', side_effect=OSError('disk failure')):
                with self.assertRaisesRegex(OSError, 'partial hold remains'):
                    admission.begin_hold(root, owner='o', control_id='c', note='n')
            self.assertTrue((root / 'gate-maintenance.json').exists())
            self.assertEqual(admission.maintenance(root)['status'], 'held')

    def test_cli_hold_resume_uses_private_receipt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); receipt = root / 'private.json'
            self.assertEqual(admission.main(['hold', '--root', str(root), '--owner', 'o', '--control-id', 'c', '--note', 'n', '--receipt', str(receipt)]), 0)
            self.assertEqual(admission.maintenance(root)['status'], 'held')
            self.assertEqual(admission.main(['resume', '--root', str(root), '--receipt', str(receipt)]), 0)
            self.assertIsNone(admission.maintenance(root))


if __name__ == '__main__':
    unittest.main()
