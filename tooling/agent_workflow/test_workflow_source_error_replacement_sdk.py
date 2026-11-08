"""Public author SDK coverage for replacing a settled source-drift ERROR."""
import asyncio
import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

import httpx
import workflow_coordinator
import workflow_store
import workflow_review
import workflow_review_results
import workflow_review_pipeline
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client
from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission


class SourceErrorReplacementSDKTests(unittest.TestCase):
    def test_public_author_sdk_can_replace_settled_source_drift_error(self):
        async def scenario():
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                case = make_preflight_case(root)
                base = case.policy
                floors = {'required_packs': sorted({p for packs in base['coverage'].values() for p in packs}),
                    'selectors': base['selector_requirements'], 'review_roles': base['review_roles'],
                    'owner_exceptions': base['owner_exceptions'], 'config_overrides': base['config_overrides'],
                    'review_envelope_limit': base['review_envelope_limit_bytes']}
                token = 'source-error-author'
                sessions = {sid: {'id': sid, 'location': {'directory': str(case.repo)}, 'agent': 'orchestrate-test'}
                    for sid in ('source-author', 'other-author')}
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
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                        base_url='http://127.0.0.1:18443', headers={**(headers or {}), 'Origin': 'http://127.0.0.1:18443'})

                with patch.object(coordinator, 'start', lambda: None):
                  async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18443/mcp/author',
                            {'Authorization': 'Bearer ' + token}, httpx_client_factory=factory) as (r, w, _):
                        async with ClientSession(r, w) as client:
                            await client.initialize()
                            context = json.loads((await client.call_tool('status',
                                {'session_id': 'source-author'})).content[0].text)['author_context']
                            scope, request = prepare_dynamic_author_submission(context, base_policy=base,
                                repo=case.repo, submit_key='source-original', criterion='c1',
                                artifact_id='artifact_source_original', artifact_path=root / 'original.evidence',
                                request_path=root / 'original-request.json')
                            scope_path = root / 'original-scope.json'
                            scope_path.write_text(json.dumps(scope), encoding='utf-8')
                            registered = json.loads((await client.call_tool('register_scope', {'scope_path': str(scope_path),
                                'idempotency_key': 'source-original-scope', 'session_id': 'source-author'})).content[0].text)
                            self.assertEqual(registered['response']['status'], 'REGISTERED', registered)
                            request_path = root / 'original-request.json'
                            request_path.write_text(json.dumps(request), encoding='utf-8')
                            args = {'request_path': str(request_path), 'idempotency_key': 'source-original', 'session_id': 'source-author'}
                            accepted = json.loads((await client.call_tool('submit', args)).content[0].text)
                            replay = json.loads((await client.call_tool('submit', args)).content[0].text)
                            self.assertEqual(accepted['response'], replay['response'])
                            old_id = accepted['response']['run_id']

                            # The native worker loads the frozen input bundle only after acquiring its
                            # native resources. This real source mutation therefore exercises its drift error.
                            import workflow_native_execution
                            import workflow_native_worker
                            task = next(t for t in store.list_tasks(old_id, 1) if t['kind'] == 'CHECK')
                            (case.repo / 'tracked.txt').write_text('changed after admission', encoding='utf-8')
                            backend = workflow_native_execution.NativeExecutionBackend(store, root / 'native-artifacts', root / 'native-lock')
                            try:
                                backend.dispatch('source-drift-' + task['task_id'], {'task_id': task['task_id']})
                                backend.workers[task['task_id']].wait(timeout=30)
                            finally:
                                backend.close()
                            result = store.get_task(task['task_id'])
                            reports = []
                            for ref in result['result']['artifact_refs']:
                                artifact = store.get_artifact(ref)['artifact']
                                try:
                                    reports.append(json.loads(Path(artifact['path']).read_text(encoding='utf-8')))
                                except (OSError, ValueError):
                                    continue
                            self.assertTrue(any(report.get('code') == 'SOURCE_SNAPSHOT_CHANGED' for report in reports), reports)
                            self.assertEqual('COMPLETED', result['state'])
                            result_digest = store.get_task_result_digest(task['task_id'])
                            self.assertIsNotNone(result_digest)
                            completion_id = store._task_result_event_id(task['task_id'], result_digest)
                            completion = store.get_event(completion_id)
                            self.assertEqual('TASK_COMPLETED', completion['kind'])
                            native_scope = store.get_native_scope(task['task_id'])
                            self.assertEqual(native_scope['state'], 'CLEANED')
                            self.assertTrue(native_scope.get('cleanup_artifact_ref'))
                            resources = store.get_task_resources(task['task_id'])
                            self.assertTrue(resources)
                            self.assertTrue(all(resource['state'] == 'RELEASED' for resource in resources))

                            # Settle every readiness event for this fixture task through the
                            # coordinator's normal delivery path; terminal settlement checks all
                            # durable TASK_READY dispatches, not only the admission event.
                            coordinator.handlers['TASK_READY'] = backend
                            db = store._connect()
                            try:
                                ready_ids = [r[0] for r in db.execute(
                                    "SELECT id FROM events WHERE run_id=? AND kind='TASK_READY' AND json_extract(payload,'$.task_id')=? ORDER BY created_ms",
                                    (old_id, task['task_id'])).fetchall()]
                            finally:
                                db.close()
                            ready_events = [store.get_event(event_id) for event_id in ready_ids]
                            self.assertTrue(ready_events)
                            pending = {event['event_id'] for event in ready_events}
                            for _ in range(len(ready_ids) + 8):
                                if not pending:
                                    break
                                ready = store.claim_outbox()
                                self.assertIsNotNone(ready)
                                if ready['event_id'] not in pending:
                                    coordinator._deliver(ready)
                                    continue
                                self.assertEqual('TASK_READY', ready['payload']['kind'])
                                self.assertEqual(task['task_id'], ready['payload']['task_id'])
                                coordinator._deliver(ready)
                                self.assertEqual('APPLIED', backend.observe(ready['event_id'], ready['payload']))
                                pending.remove(ready['event_id'])
                            self.assertFalse(pending, pending)
                            planner = workflow_review.ReviewCoordinator(store, reviewers={}, artifact_dir=root / 'review-artifacts')
                            results = workflow_review_results.ReviewResults(store, planner)
                            pipeline = workflow_review_pipeline.ReviewPipeline(store, planner, results, object(), backend)
                            pipeline.completed(completion['payload'])
                            settled = store.get_run(old_id)
                            self.assertEqual('CONFIRMED', settled['attempt']['cleanup_state'])
                            self.assertEqual('DECIDED', settled['attempt']['phase'])
                            self.assertEqual('ERROR', settled['attempt']['result'])

                            status = json.loads((await client.call_tool('status',
                                {'run_id': old_id, 'session_id': 'source-author'})).content[0].text)
                            self.assertEqual(status['continuation']['next_action'], 'PREPARE_REVISED_SUBMISSION')
                            self.assertEqual(status['continuation']['replaces_run_id'], old_id)
                            denied = json.loads((await client.call_tool('status',
                                {'run_id': old_id, 'session_id': 'other-author'})).content[0].text)
                            self.assertEqual(denied['code'], 'FORBIDDEN')

                            (case.repo / 'tracked.txt').write_text('corrected source', encoding='utf-8')
                            fresh_scope, fresh = prepare_dynamic_author_submission(context, base_policy=base,
                                repo=case.repo, submit_key='source-replacement', criterion='c1',
                                artifact_id='artifact_source_replacement', artifact_path=root / 'replacement.evidence',
                                request_path=root / 'replacement-request.json')
                            fresh['replaces_run_id'] = old_id
                            self.assertNotEqual(fresh['input_digest'], request['input_digest'])
                            fresh_scope_path = root / 'replacement-scope.json'
                            fresh_scope_path.write_text(json.dumps(fresh_scope), encoding='utf-8')
                            reg = json.loads((await client.call_tool('register_scope', {'scope_path': str(fresh_scope_path),
                                'idempotency_key': 'source-replacement-scope', 'session_id': 'source-author'})).content[0].text)
                            self.assertEqual(reg['response']['status'], 'REGISTERED', reg)
                            fresh_path = root / 'replacement-request.json'
                            fresh_path.write_text(json.dumps(fresh), encoding='utf-8')
                            replacement_args = {'request_path': str(fresh_path), 'idempotency_key': 'source-replacement', 'session_id': 'source-author'}
                            replacement = json.loads((await client.call_tool('submit', replacement_args)).content[0].text)
                            self.assertEqual(replacement['response']['status'], 'ACCEPTED', replacement)
                            new_id = replacement['response']['run_id']
                            self.assertNotEqual(new_id, old_id)
                            self.assertTrue(any(t['kind'] == 'CHECK' and t['state'] == 'QUEUED' for t in store.list_tasks(new_id, 1)))
                            again = json.loads((await client.call_tool('submit', replacement_args)).content[0].text)
                            self.assertEqual(replacement['response'], again['response'])
                            old_after = store.get_run(old_id)
                            self.assertEqual(old_after['attempt'], settled['attempt'])
                            old_status = json.loads((await client.call_tool('status',
                                {'run_id': old_id, 'session_id': 'source-author'})).content[0].text)
                            self.assertEqual(old_status['continuation']['next_action'], 'HISTORY_ONLY')

        asyncio.run(scenario())


if __name__ == '__main__':
    unittest.main()
