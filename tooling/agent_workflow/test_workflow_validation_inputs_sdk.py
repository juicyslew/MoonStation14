"""Public author SDK regressions for early validation-input rejection."""
import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import httpx
import workflow_coordinator
import workflow_store
import workflow_check_packs
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client
from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission


class ValidationInputsSDKTests(unittest.TestCase):
    def test_public_scope_rejects_wrong_unit_fqn_then_accepts_corrected_class(self):
        async def scenario():
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                case = make_preflight_case(root)
                java_test = case.repo / 'src/test/java/org/example/FeatureTest.java'
                java_test.parent.mkdir(parents=True)
                java_test.write_text('package org.example; class FeatureTest {}', encoding='utf-8')
                base = case.policy
                floors = {'required_packs': sorted({p for packs in base['coverage'].values() for p in packs}),
                    'selectors': base['selector_requirements'], 'review_roles': base['review_roles'],
                    'owner_exceptions': base['owner_exceptions'], 'config_overrides': base['config_overrides'],
                    'review_envelope_limit': base['review_envelope_limit_bytes']}
                token = 'unit-selector-author'
                sessions = {'unit-author': {'id': 'unit-author', 'location': {'directory': str(case.repo)}, 'agent': 'orchestrate-test'}}
                slot = workflow_coordinator.AuthorWorktreeBinding('slot', token, 'context', str(case.repo), floors,
                    str(case.receipt), case.context.publication_receipt_sha256)
                verifier = workflow_coordinator.OpenCodeIdentityVerifier('local',
                    session_lookup=lambda _server, sid: sessions.get(sid), role_lookup=lambda *_: {'mode': 'primary'})
                store = workflow_store.WorkflowStore(root / 'state.sqlite')
                coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={}, author_worktrees={token: slot},
                    artifact_dir=root / 'artifacts', handlers={}, identity_verifier=verifier,
                    reconcile_interval=30, admission_root=root / 'admission')
                app = coordinator.app()

                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://127.0.0.1:18443',
                        headers={**(headers or {}), 'Origin': 'http://127.0.0.1:18443'})

                with patch.object(coordinator, 'start', lambda: None):
                    async with app.router.lifespan_context(app):
                        async with streamablehttp_client('http://127.0.0.1:18443/mcp/author',
                                {'Authorization': 'Bearer ' + token}, httpx_client_factory=factory) as (r, w, _):
                            async with ClientSession(r, w) as client:
                                await client.initialize()
                                context = json.loads((await client.call_tool('status', {'session_id': 'unit-author'})).content[0].text)['author_context']
                                def add_unit(scope, request, selector):
                                    scope['policy']['coverage']['c1'] = [*scope['policy']['coverage']['c1'], 'unit']
                                    scope['policy']['selector_requirements'] = {**scope['policy']['selector_requirements'], 'unit': [selector]}
                                    request['check_packs'].append({'pack_id': 'unit', 'kind': 'unit', 'selectors': ['org.example.FeatureTest']})
                                    request['policy_digest'] = workflow_store.digest(scope['policy'])
                                    request['plan_digest'] = workflow_store.digest(workflow_check_packs.resolve_check_packs(
                                        request, repo=case.repo, policy=scope['policy']))

                                bad_scope, bad_request = prepare_dynamic_author_submission(context, base_policy=base,
                                    repo=case.repo, submit_key='unit-bad', criterion='c1', artifact_id='artifact_unit_bad',
                                    artifact_path=root / 'unit-bad.evidence', request_path=root / 'unit-bad-request.json')
                                bad_scope['policy']['coverage']['c1'] = [*bad_scope['policy']['coverage']['c1'], 'unit']
                                bad_scope['policy']['selector_requirements'] = {'unit': ['org.wrong.FeatureTest']}
                                bad_request['check_packs'].append({'pack_id': 'unit', 'kind': 'unit', 'selectors': ['org.example.FeatureTest']})
                                bad_request['policy_digest'] = workflow_store.digest(bad_scope['policy'])
                                bad_path = root / 'unit-bad-scope.json'
                                bad_path.write_text(json.dumps(bad_scope), encoding='utf-8')
                                rejected = json.loads((await client.call_tool('register_scope', {'scope_path': str(bad_path),
                                    'idempotency_key': 'unit-bad-scope', 'session_id': 'unit-author'})).content[0].text)
                                response = rejected.get('response', {})
                                self.assertEqual(response.get('status'), 'REJECTED', rejected)
                                self.assertEqual(response.get('result'), 'FAIL', rejected)
                                self.assertEqual(response.get('field'), 'policy.selector_requirements.unit', rejected)
                                self.assertIn('org.wrong.FeatureTest', response.get('message', ''))
                                self.assertTrue('org.example.FeatureTest' in response.get('message', '') or
                                    'org.example.FeatureTest' in response.get('recovery', ''), response)
                                self.assertTrue(response.get('recovery'), rejected)
                                self.assertEqual(response.get('code'), 'POLICY_INPUT', rejected)
                                db = store._connect()
                                try:
                                    self.assertEqual(db.execute('SELECT COUNT(*) FROM runs').fetchone()[0], 0)
                                    self.assertEqual(db.execute('SELECT COUNT(*) FROM tasks').fetchone()[0], 0)
                                finally:
                                    db.close()

                                good_scope, good_request = prepare_dynamic_author_submission(context, base_policy=base,
                                    repo=case.repo, submit_key='unit-good', criterion='c1', artifact_id='artifact_unit_good',
                                    artifact_path=root / 'unit-good.evidence', request_path=root / 'unit-good-request.json')
                                add_unit(good_scope, good_request, 'org.example.FeatureTest')
                                good_scope_path = root / 'unit-good-scope.json'
                                good_scope_path.write_text(json.dumps(good_scope), encoding='utf-8')
                                registered = json.loads((await client.call_tool('register_scope', {'scope_path': str(good_scope_path),
                                    'idempotency_key': 'unit-good-scope', 'session_id': 'unit-author'})).content[0].text)
                                self.assertEqual(registered.get('response', {}).get('status'), 'REGISTERED', registered)
                                malformed_scope, malformed_request = prepare_dynamic_author_submission(context, base_policy=base,
                                    repo=case.repo, submit_key='unit-malformed-submit', criterion='c1', artifact_id='artifact_unit_malformed',
                                    artifact_path=root / 'unit-malformed.evidence', request_path=root / 'unit-malformed-request.json')
                                add_unit(malformed_scope, malformed_request, 'org.example.FeatureTest')
                                malformed_scope_path = root / 'unit-malformed-scope.json'
                                malformed_scope_path.write_text(json.dumps(malformed_scope), encoding='utf-8')
                                malformed_registration = json.loads((await client.call_tool('register_scope', {'scope_path': str(malformed_scope_path),
                                    'idempotency_key': 'unit-malformed-scope', 'session_id': 'unit-author'})).content[0].text)
                                self.assertEqual(malformed_registration.get('response', {}).get('status'), 'REGISTERED', malformed_registration)
                                malformed_request = dict(malformed_request)
                                malformed_request['check_packs'] = [dict(pack, selectors=['org.example.FeatureTest', 'org.wrong.FeatureTest'])
                                    if pack['pack_id'] == 'unit' else pack for pack in malformed_request['check_packs']]
                                malformed_request['idempotency_key'] = 'unit-malformed-submit'
                                malformed_path = root / 'unit-malformed-request.json'
                                malformed_path.write_text(json.dumps(malformed_request), encoding='utf-8')
                                malformed = json.loads((await client.call_tool('submit', {'request_path': str(malformed_path),
                                    'idempotency_key': 'unit-malformed-submit', 'session_id': 'unit-author'})).content[0].text)
                                malformed_response = malformed.get('response', {})
                                self.assertEqual(malformed_response.get('status'), 'REJECTED', malformed)
                                self.assertEqual(malformed_response.get('result'), 'FAIL', malformed)
                                public_findings = json.dumps(malformed_response.get('findings', []))
                                self.assertIn('org.wrong.FeatureTest', public_findings, malformed)
                                self.assertIn('org.example.FeatureTest', public_findings, malformed)
                                self.assertNotIn('ERROR', malformed_response.get('result', ''))
                                request_path = root / 'unit-good-request.json'
                                request_path.write_text(json.dumps(good_request), encoding='utf-8')
                                accepted = json.loads((await client.call_tool('submit', {'request_path': str(request_path),
                                    'idempotency_key': 'unit-good', 'session_id': 'unit-author'})).content[0].text)
                                self.assertEqual(accepted.get('response', {}).get('status'), 'ACCEPTED', accepted)
                                run_id = accepted['response']['run_id']
                                self.assertTrue(any(task['kind'] == 'CHECK' and task['state'] == 'QUEUED'
                                    for task in store.list_tasks(run_id, 1)))
                                db = store._connect()
                                try:
                                    self.assertEqual(db.execute('SELECT COUNT(*) FROM runs').fetchone()[0], 1)
                                    self.assertEqual(db.execute('SELECT COUNT(*) FROM tasks').fetchone()[0], 1)
                                finally:
                                    db.close()

        asyncio.run(scenario())

    def test_package_scope_rejects_non_archive_outputs_without_admission(self):
        async def scenario():
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                case = make_preflight_case(root)
                base = case.policy
                floors = {'required_packs': sorted({p for packs in base['coverage'].values() for p in packs}),
                    'selectors': base['selector_requirements'], 'review_roles': base['review_roles'],
                    'owner_exceptions': base['owner_exceptions'], 'config_overrides': base['config_overrides'],
                    'review_envelope_limit': base['review_envelope_limit_bytes']}
                token = 'validation-input-author'
                sessions = {'validation-author': {'id': 'validation-author', 'location': {'directory': str(case.repo)}, 'agent': 'orchestrate-test'}}
                slot = workflow_coordinator.AuthorWorktreeBinding('slot', token, 'context', str(case.repo), floors,
                    str(case.receipt), case.context.publication_receipt_sha256)
                verifier = workflow_coordinator.OpenCodeIdentityVerifier('local',
                    session_lookup=lambda _server, sid: sessions.get(sid), role_lookup=lambda *_: {'mode': 'primary'})
                store = workflow_store.WorkflowStore(root / 'state.sqlite')
                coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={}, author_worktrees={token: slot},
                    artifact_dir=root / 'artifacts', handlers={}, identity_verifier=verifier,
                    reconcile_interval=30, admission_root=root / 'admission')
                app = coordinator.app()

                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://127.0.0.1:18443',
                        headers={**(headers or {}), 'Origin': 'http://127.0.0.1:18443'})

                with patch.object(coordinator, 'start', lambda: None):
                    async with app.router.lifespan_context(app):
                        async with streamablehttp_client('http://127.0.0.1:18443/mcp/author',
                                {'Authorization': 'Bearer ' + token}, httpx_client_factory=factory) as (r, w, _):
                            async with ClientSession(r, w) as client:
                                await client.initialize()
                                context = json.loads((await client.call_tool('status', {'session_id': 'validation-author'})).content[0].text)['author_context']
                                scope, _ = prepare_dynamic_author_submission(context, base_policy=base, repo=case.repo,
                                    submit_key='bad-package', criterion='c1', artifact_id='artifact_bad_package_evidence',
                                    artifact_path=root / 'bad-package.evidence', request_path=root / 'bad-package-request.json')
                                scope['policy']['package_artifacts'] = ['build/assets/texture.png']
                                scope_path = root / 'bad-package-scope.json'
                                scope_path.write_text(json.dumps(scope), encoding='utf-8')
                                result = json.loads((await client.call_tool('register_scope', {'scope_path': str(scope_path),
                                    'idempotency_key': 'bad-package-scope', 'session_id': 'validation-author'})).content[0].text)
                                response = result.get('response', {})
                                self.assertEqual(response.get('status'), 'REJECTED', result)
                                self.assertEqual(response.get('result'), 'FAIL', result)
                                self.assertIn('package_artifacts', response.get('field', ''))
                                self.assertIn('texture.png', response.get('message', ''))
                                self.assertTrue('.jar' in response.get('recovery', '') or '.zip' in response.get('recovery', ''), response)
                                self.assertTrue(response.get('recovery'), result)
                                self.assertNotEqual(response.get('code'), 'SCOPE_REGISTRATION_UNAVAILABLE', result)
                                db = store._connect()
                                try:
                                    self.assertEqual(db.execute('SELECT COUNT(*) FROM runs').fetchone()[0], 0)
                                    self.assertEqual(db.execute('SELECT COUNT(*) FROM tasks').fetchone()[0], 0)
                                    self.assertEqual(db.execute('SELECT COUNT(*) FROM events').fetchone()[0], 0)
                                finally:
                                    db.close()

        asyncio.run(scenario())


if __name__ == '__main__':
    unittest.main()
