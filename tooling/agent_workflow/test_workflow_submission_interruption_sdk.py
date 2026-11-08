import asyncio
from contextlib import closing
import json
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

import httpx


class SubmissionInterruptionSDKTests(unittest.TestCase):
    def test_interrupted_submit_recovers_by_public_status(self):
        import workflow_coordinator
        import workflow_coordinator_http_mcp
        import workflow_store
        import workflow_submission
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission

        async def scenario():
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                case = make_preflight_case(root)
                policy = case.policy
                floors = {
                    'required_packs': sorted({p for packs in policy['coverage'].values() for p in packs}),
                    'selectors': policy['selector_requirements'], 'review_roles': policy['review_roles'],
                    'owner_exceptions': policy['owner_exceptions'], 'config_overrides': policy['config_overrides'],
                    'review_envelope_limit': policy['review_envelope_limit_bytes'],
                }
                token = 'author-secret'
                sessions = {'author-A': {'id': 'author-A', 'location': {'directory': str(case.repo)}, 'agent': 'orchestrate-test'},
                            'author-B': {'id': 'author-B', 'location': {'directory': str(case.repo)}, 'agent': 'orchestrate-test'}}
                slot = workflow_coordinator.AuthorWorktreeBinding('slot', token, 'context', str(case.repo), floors, str(case.receipt), case.context.publication_receipt_sha256)
                verifier = workflow_coordinator.OpenCodeIdentityVerifier('local', session_lookup=lambda _, sid: sessions.get(sid), role_lookup=lambda *_: {'mode': 'primary'})
                coordinator = workflow_coordinator.ExplicitCoordinator(workflow_store.WorkflowStore(root / 'state.sqlite'), bindings={},
                    author_worktrees={token: slot}, artifact_dir=root / 'artifacts', handlers={}, identity_verifier=verifier,
                    reconcile_interval=30, admission_root=root / 'admission')
                app = coordinator.app()

                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://127.0.0.1:18443',
                        headers={**(headers or {}), 'Origin': 'http://127.0.0.1:18443'})

                entered, release, worker_done = threading.Event(), threading.Event(), threading.Event()
                original_freeze = workflow_submission._freeze_source_snapshot
                original_submit = workflow_submission.submit_submission

                def held(*args, **kwargs):
                    entered.set()
                    if not release.wait(30): raise TimeoutError('freeze release was not signalled')
                    return original_freeze(*args, **kwargs)

                def tracked_submit(*args, **kwargs):
                    try:
                        return original_submit(*args, **kwargs)
                    finally:
                        worker_done.set()

                with patch.object(workflow_submission, '_freeze_source_snapshot', held), patch.object(workflow_submission, 'submit_submission', tracked_submit):
                  async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18443/mcp/author', {'Authorization': 'Bearer ' + token}, httpx_client_factory=factory) as (r, w, _):
                        async with ClientSession(r, w) as client:
                            await client.initialize()
                            ctx = json.loads((await client.call_tool('status', {'session_id': 'author-A'})).content[0].text)['author_context']
                            key = 'interrupted-submit'
                            scope, _ = prepare_dynamic_author_submission(ctx, base_policy=policy, repo=case.repo, submit_key=key,
                                criterion='criterion-A', artifact_id='artifact_A', artifact_path=root/'evidence', request_path=root/'request.json')
                            scopefile = root/'scope.json'; scopefile.write_text(json.dumps(scope), encoding='utf-8')
                            registered = json.loads((await client.call_tool('register_scope', {'scope_path': str(scopefile), 'idempotency_key': 'registration-A', 'session_id': 'author-A'})).content[0].text)
                            self.assertEqual(registered['response']['status'], 'REGISTERED', registered)
                            args = {'request_path': str(root/'request.json'), 'idempotency_key': key, 'session_id': 'author-A'}
                            submit = asyncio.create_task(client.call_tool('submit', args))
                            try:
                                reached = await asyncio.to_thread(entered.wait, 30)
                                self.assertTrue(reached, 'submit never reached source freeze')
                                submit.cancel()
                                with self.assertRaises(asyncio.CancelledError):
                                    await asyncio.wait_for(submit, 30)
                                interrupted = json.loads((await client.call_tool('status', {'session_id': 'author-A', 'idempotency_key': key})).content[0].text)
                                self.assertIsNone(interrupted['submission'], interrupted)
                                self.assertEqual(interrupted['next_action'], 'CHECK_SUBMISSION_STATUS', interrupted)
                            finally:
                                release.set()
                                self.assertTrue(await asyncio.to_thread(worker_done.wait, 30), 'submission worker did not finish')
                            status = json.loads((await client.call_tool('status', {'session_id': 'author-A', 'idempotency_key': key})).content[0].text)
                            self.assertEqual(status['submission']['status'], 'ACCEPTED', status)
                            run_id = status['submission']['run_id']
                            self.assertEqual(status['run']['run_id'], run_id, status)
                            self.assertEqual(status['attempt']['phase'], 'QUEUED', status)
                            self.assertIsNone(status['attempt'].get('result'), status)
                            foreign = json.loads((await client.call_tool('status', {'session_id': 'author-B', 'idempotency_key': key})).content[0].text)
                            self.assertIsNone(foreign['submission'], foreign)
                            with closing(coordinator.store._connect()) as db:
                                self.assertEqual(db.execute('SELECT COUNT(*) FROM submissions').fetchone()[0], 1)
                                self.assertEqual(db.execute('SELECT COUNT(*) FROM runs').fetchone()[0], 1)
                                self.assertEqual(db.execute('SELECT COUNT(*) FROM tasks').fetchone()[0], 1)

        asyncio.run(scenario())
