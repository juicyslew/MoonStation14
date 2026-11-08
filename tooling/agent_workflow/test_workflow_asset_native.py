"""Public-CLI regression coverage for complete native OBJ/MTL asset groups."""
import json
import unittest
from pathlib import Path

import test_workflow as workflow_tests


class NativeAssetWorkflowTests(unittest.TestCase):
    """Compose the shared fixture without inheriting/rerunning its test cases."""

    def setUp(self):
        self.fixture = workflow_tests.WorkflowTests()
        self.fixture.setUp()
        self.__dict__.update(self.fixture.__dict__)

    def tearDown(self):
        self.fixture.tearDown()

    def __getattr__(self, name):
        # Reuse fixture helpers (CLI, report/review flow) without inheriting its
        # test methods and accidentally composing the entire parent test suite.
        fixture = self.__dict__.get('fixture')
        if fixture is not None and hasattr(fixture, name):
            return getattr(fixture, name)
        raise AttributeError(name)

    def test_obj_mtl_atlas_group_survives_review_and_owner_accepted_import(self):
        runtime = 'src/main/resources/assets/moonstation14/'
        files = {
            'harvest.json': (runtime + 'models/block/botany/plants/blue_pumpkin/harvest.json',
                '{"loader":"neoforge:obj","model":"moonstation14:models/block/botany/plants/blue_pumpkin/harvest.obj",'
                '"textures":{"atlas":"moonstation14:block/botany/plants/blue_pumpkin/harvest",'
                '"particle":"moonstation14:block/botany/plants/blue_pumpkin/harvest"}}'),
            'harvest.obj': (runtime + 'models/block/botany/plants/blue_pumpkin/harvest.obj',
                'mtllib harvest.mtl\nusemtl atlas\nv 0 0 0\n'),
            'harvest.mtl': (runtime + 'models/block/botany/plants/blue_pumpkin/harvest.mtl',
                'newmtl atlas\nmap_Kd #atlas\n'),
            'harvest.png': (runtime + 'textures/block/botany/plants/blue_pumpkin/harvest.png', None),
        }
        for name, (_, text) in files.items():
            if text is not None:
                (self.assets / name).write_text(text)
        (self.assets / 'harvest.png').write_bytes(workflow_tests.PNG)
        # Capture the platform's actual saved bytes: text-mode writes may use
        # CRLF on Windows, and delivery must preserve those bytes unchanged.
        expected_bytes = {name: (workflow_tests.PNG if text is None else
                                 (self.assets / name).read_bytes())
                          for name, (_, text) in files.items()}
        manifest = self.assets / 'export' / 'native-manifest.json'
        manifest.write_text(json.dumps({'files': [
            {'file': name, 'destination': destination} for name, (destination, _) in files.items()
        ]}))
        self.cli('request', '--from-json', self.request, '--context-root', self.repo)
        self.cli('claim', 'test-1', '--agent', 'author')
        self.cli('deliver', 'test-1', '--manifest', manifest, '--source-root', self.assets,
                 '--revision', 'native-r1', '--provenance', 'origin.txt', '--editable', 'harvest.json',
                 '--review-images', 'model.png')
        delivery = json.loads((self.root / 'work/agent_exchange/requests/test-1/request.json').read_text())['delivery']
        # Provenance and the author-side manifest are mutable after capture. An
        # independent reviewer still evaluates the exact frozen delivery.
        (self.assets / 'origin.txt').write_text('metadata changed before review')
        manifest.write_text(manifest.read_text() + ' ')
        self.ready(delivery)
        owner = self.root / 'owner.json'
        owner.write_text(json.dumps({'request_id': 'test-1', 'decision': 'accepted_as_is_closed',
                                     'delivery_digest': delivery['digest']}))
        target = self.root / 'target'
        plan = self.root / 'native-plan.json'
        self.cli('import-assets', 'test-1', '--target', target, '--owner-receipt', owner, '--plan-out', plan)
        self.assertFalse(target.exists())
        # Current source and metadata may change after capture; applying the frozen plan
        # must install precisely the reviewed bytes, including native MTL/OBJ sidecars.
        (self.assets / 'origin.txt').write_text('later provenance')
        manifest.write_text(manifest.read_text() + ' ')
        (self.assets / 'harvest.obj').write_text('UNAPPROVED')
        for name, (destination, text) in files.items():
            delivered = self.root / 'work/agent_exchange/requests/test-1/delivery/files' / destination
            self.assertEqual(delivered.read_bytes(), expected_bytes[name], name)
        self.cli('import-assets', 'test-1', '--target', target, '--owner-receipt', owner,
                 '--plan', plan, '--apply')
        for name, (destination, text) in files.items():
            self.assertEqual((target / destination).read_bytes(), expected_bytes[name], name)

    def test_incomplete_or_unresolved_native_groups_are_rejected_before_delivery(self):
        for case in ('missing-wrapper', 'wrong-model-ref', 'missing-png', 'unresolved-slot'):
            with self.subTest(case=case):
                # Each case uses an independent fixture/queue to prove refusal happens
                # before the CLI captures any delivery payload.
                f = workflow_tests.WorkflowTests(); f.setUp()
                try:
                    runtime = 'src/main/resources/assets/moonstation14/'
                    sources = {'harvest.json': '{"loader":"neoforge:obj","model":"moonstation14:models/block/botany/plants/blue_pumpkin/harvest.obj","textures":{"atlas":"moonstation14:block/botany/plants/blue_pumpkin/harvest"}}',
                                'harvest.obj': 'mtllib harvest.mtl\nusemtl atlas\nv 0 0 0\n',
                                'harvest.mtl': 'newmtl atlas\nmap_Kd #atlas\n',
                                'harvest.png': ''}
                    if case == 'wrong-model-ref':
                        sources['harvest.json'] = sources['harvest.json'].replace('harvest.obj', 'other.obj')
                    if case == 'unresolved-slot':
                        sources['harvest.mtl'] = 'newmtl atlas\nmap_Kd #missing\n'
                    for name, text in sources.items():
                        if case == 'missing-wrapper' and name == 'harvest.json': continue
                        if case == 'missing-png' and name == 'harvest.png': continue
                        path = f.assets / name
                        if name == 'harvest.png': path.write_bytes(workflow_tests.PNG)
                        else: path.write_text(text)
                    entries = [{'file': n, 'destination': runtime + ('models/block/botany/plants/blue_pumpkin/' + n if n.endswith(('.json','.obj','.mtl')) else 'textures/block/botany/plants/blue_pumpkin/harvest.png')} for n in sources if not (case == 'missing-wrapper' and n == 'harvest.json') and not (case == 'missing-png' and n == 'harvest.png')]
                    manifest=f.assets/'export'/'native.json'; manifest.write_text(json.dumps({'files':entries}))
                    f.cli('request','--from-json',f.request,'--context-root',f.repo)
                    f.cli('claim','test-1','--agent','author')
                    result = f.cli('deliver','test-1','--manifest',manifest,'--source-root',f.assets,'--revision','r1','--provenance','origin.txt','--editable','harvest.json','--review-images','model.png',ok=False)
                    reasons = {'missing-wrapper': 'requires matching wrapper JSON', 'wrong-model-ref': 'must resolve to delivered OBJ', 'missing-png': 'missing OBJ wrapper texture dependency', 'unresolved-slot': 'unresolved OBJ material alias'}
                    self.assertIn(reasons[case], result.stderr)
                    self.assertFalse((f.root/'work/agent_exchange/requests/test-1/delivery').exists())
                finally:
                    f.tearDown()

    def test_closed_art_acceptance_without_import_authorization_is_rejected(self):
        self.cli('request', '--from-json', self.request, '--context-root', self.repo)
        self.cli('claim', 'test-1', '--agent', 'author')
        (self.assets / 'asset.png').write_bytes(workflow_tests.PNG)
        manifest = self.assets / 'export' / 'asset.json'
        manifest.write_text(json.dumps({'files': [{'file': 'asset.png', 'destination': 'textures/test/asset.png'}]}))
        self.cli('deliver', 'test-1', '--manifest', manifest, '--source-root', self.assets,
                 '--revision', 'r1', '--provenance', 'origin.txt', '--editable', 'asset.png',
                 '--review-images', 'model.png')
        delivery = json.loads((self.root / 'work/agent_exchange/requests/test-1/request.json').read_text())['delivery']
        owner = self.root / 'owner.json'
        owner.write_text(json.dumps({'decision': 'accepted_as_is_closed', 'request_id': 'test-1',
                                     'delivery_digest': delivery['digest'], 'import_authorized': False}))
        target = self.root / 'target'
        plan = self.root / 'plan.json'
        args = ('import-assets', 'test-1', '--target', target, '--owner-receipt', owner)
        dry = self.cli(*args, ok=False)
        self.assertIn('explicitly denies queue import authorization', dry.stderr)
        self.assertFalse(target.exists())
        self.assertFalse(plan.exists())
        applied = self.cli(*args, '--plan', plan, '--apply', ok=False)
        self.assertIn('explicitly denies queue import authorization', applied.stderr)
        self.assertFalse(target.exists())
        self.assertFalse((self.root / 'work/agent_exchange/requests/test-1/import_receipt.json').exists())
