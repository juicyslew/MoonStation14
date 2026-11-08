"""Public author MCP/HTTP protocol coverage for diagnostic submissions."""
import asyncio
import json
import tempfile
import unittest
from pathlib import Path

import httpx
import workflow_coordinator
import workflow_store
import workflow_preflight
import workflow_native_execution
import workflow_review
import workflow_review_pipeline
import workflow_review_results
import workflow_result_delivery
from workflow_check_packs import resolve_diagnostic_plan
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client
from diagnostic_test_support import make_diagnostic_case
from preflight_test_support import prepare_dynamic_author_submission


class DiagnosticHTTPTests(unittest.TestCase):
    def test_author_http_registration_submission_replay_and_status_identity(self):
        async def scenario():
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                case = make_diagnostic_case(root)
                base = case.policy
                floors = {
                    'required_packs': sorted({p for packs in base['coverage'].values() for p in packs}),
                    'selectors': base['selector_requirements'], 'review_roles': base['review_roles'],
                    'owner_exceptions': base['owner_exceptions'], 'config_overrides': base['config_overrides'],
                    'review_envelope_limit': base['review_envelope_limit_bytes'],
                }
                token = 'diagnostic-author-token'
                sessions = {'author-session': {'id': 'author-session',
                    'location': {'directory': str(case.repo)}, 'agent': 'orchestrate-test'},
                    'other-session': {'id': 'other-session',
                    'location': {'directory': str(case.repo)}, 'agent': 'orchestrate-test'}}
                slot = workflow_coordinator.AuthorWorktreeBinding('slot', token, 'context',
                    str(case.repo), floors, str(case.receipt), case.context.publication_receipt_sha256)
                verifier = workflow_coordinator.OpenCodeIdentityVerifier('local',
                    session_lookup=lambda _server, sid: sessions.get(sid), role_lookup=lambda *_: {'mode': 'primary'})
                store = workflow_store.WorkflowStore(root / 'state.sqlite')
                coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={},
                    author_worktrees={token: slot}, artifact_dir=root / 'artifacts', handlers={},
                    identity_verifier=verifier, reconcile_interval=30, admission_root=root / 'admission')
                app = coordinator.app()

                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                        base_url='http://127.0.0.1:18443', headers={**(headers or {}),
                        'Origin': 'http://127.0.0.1:18443'})

                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18443/mcp/author',
                            {'Authorization': 'Bearer ' + token}, httpx_client_factory=factory) as (r, w, _):
                        async with ClientSession(r, w) as client:
                            await client.initialize()
                            catalog = {tool.name for tool in (await client.list_tools()).tools}
                            self.assertTrue({'register_scope', 'submit', 'status', 'acknowledge_result'} <= catalog)
                            context = json.loads((await client.call_tool('status',
                                {'session_id': 'author-session'})).content[0].text)['author_context']
                            scope, feature_request = prepare_dynamic_author_submission(context,
                                base_policy=base, repo=case.repo, submit_key=case.key,
                                criterion='c1', artifact_id='artifact_diagnostic_source',
                                artifact_path=root / 'source.evidence', request_path=root / 'temporary-request.json')
                            request = feature_request
                            request.update(purpose='DIAGNOSTIC', implementation_complete=False,
                                criteria=[{**criterion, 'status': 'DIAGNOSTIC'} for criterion in request['criteria']],
                                manual_steps=[], check_packs=[],
                                diagnostic_rationale=case.request['diagnostic_rationale'],
                                diagnostic_plan=case.request['diagnostic_plan'])
                            request['plan_digest'] = workflow_store.digest(resolve_diagnostic_plan(
                                request, repo=case.repo, policy=scope['policy']))
                            scope_path = root / 'scope.json'
                            scope_path.write_text(json.dumps(scope), encoding='utf-8')
                            registered = json.loads((await client.call_tool('register_scope', {
                                'scope_path': str(scope_path), 'idempotency_key': 'diag-registration',
                                'session_id': 'author-session'})).content[0].text)
                            self.assertEqual(registered['response']['status'], 'REGISTERED', registered)
                            request_path = root / 'diagnostic-request.json'
                            request_path.write_text(json.dumps(request), encoding='utf-8')
                            args = {'request_path': str(request_path), 'idempotency_key': case.key,
                                'session_id': 'author-session'}
                            accepted = json.loads((await client.call_tool('submit', args)).content[0].text)
                            replay = json.loads((await client.call_tool('submit', args)).content[0].text)
                            self.assertEqual(accepted['response'], replay['response'])
                            run_id = accepted['response']['run_id']
                            saved = store.get_run(run_id)['run']
                            self.assertEqual(saved['run_kind'], 'DIAGNOSTIC')
                            db = store._connect()
                            try:
                                self.assertIsNone(db.execute('SELECT run_id FROM feature_heads WHERE worktree=? AND sprint=?',
                                    (request['source_worktree'], saved['sprint_id'])).fetchone())
                            finally:
                                db.close()
                            denied = json.loads((await client.call_tool('status', {
                                'run_id': run_id, 'session_id': 'other-session'})).content[0].text)
                            self.assertNotEqual(denied.get('run', {}).get('run_id'), run_id)
                            status = json.loads((await client.call_tool('status', {
                                'run_id': run_id, 'session_id': 'author-session'})).content[0].text)
                            self.assertIn(run_id, json.dumps(status))

                            # Run the declared real diagnostic CHECK producer to completion, then use
                            # the production evaluator, persisted result event, delivery adapter, and
                            # public MCP ACK endpoint (no fabricated PASS/result payload).
                            task = next(task for task in store.list_tasks(run_id, 1) if task['kind'] == 'CHECK')
                            native = workflow_native_execution.NativeExecutionBackend(
                                store, root / 'native-artifacts', root / 'native-lock')
                            try:
                                native.dispatch('diagnostic-http-' + task['task_id'], {'task_id': task['task_id']})
                                native.workers[task['task_id']].wait(timeout=30)
                            finally:
                                native.close()
                            planner = workflow_review.ReviewCoordinator(store, reviewers={}, artifact_dir=root / 'reviews')
                            pipeline = workflow_review_pipeline.ReviewPipeline(store, planner,
                                workflow_review_results.ReviewResults(store, planner), object(), object())
                            delivered_events = []
                            pipeline.notify_committed = delivered_events.append
                            terminal = pipeline._evaluate(run_id)
                            self.assertEqual((terminal['run_kind'], terminal['next_action']),
                                ('DIAGNOSTIC', 'REVIEW_DIAGNOSTIC_RESULTS'))
                            terminal_row = store.get_run(run_id)['attempt']
                            self.assertEqual(terminal['run_kind'], 'DIAGNOSTIC')
                            self.assertEqual(terminal['next_action'], 'REVIEW_DIAGNOSTIC_RESULTS')
                            # Terminalization writes the immutable event/outbox row transactionally.
                            db = store._connect()
                            try:
                                event_row = db.execute('SELECT payload FROM events WHERE run_id=? ORDER BY rowid DESC LIMIT 1',
                                    (run_id,)).fetchone()
                            finally:
                                db.close()
                            event = json.loads(event_row[0])
                            delivery_backend = workflow_result_delivery.AuthorResultDelivery(store,
                                server='http://127.0.0.1:4096', request=lambda *_: None)
                            delivery_id = delivery_backend._delivery_id(event['event_id'], event)
                            store.enqueue_delivery(event=event, delivery_id=delivery_id,
                                recipient=saved['author'], target_kind='AUTHOR')
                            def transport(_server, method, path, body=None):
                                if method == 'GET':
                                    return 200, {'id': 'author-session', 'directory': str(case.repo), 'agent': 'orchestrate-test'}
                                return 200, {'id': body['id'], 'sessionID': 'author-session', 'type': 'user',
                                    'delivery': body['delivery'], 'payload': {'text': body['text'], 'metadata': body['metadata']}}
                            delivery = workflow_result_delivery.AuthorResultDelivery(store,
                                server='http://127.0.0.1:4096', request=transport)
                            self.assertEqual(delivery.dispatch(event['event_id'], event), 'TRANSPORT_ACCEPTED')
                            receipt = store.get_delivery(delivery_id)
                            self.assertIsNotNone(receipt['transport_receipt'])
                            ack = {key: event[key] for key in ('contract_version', 'event_id', 'run_id',
                                'evaluation_generation', 'result_id', 'result_digest')}
                            ack['command_id'] = 'diagnostic-author-ack-' + event['event_id']
                            ack['session_id'] = 'author-session'
                            acknowledged = json.loads((await client.call_tool('acknowledge_result', ack)).content[0].text)
                            self.assertTrue(acknowledged.get('accepted'), acknowledged)
                            saved_delivery = store.get_delivery(delivery_id)
                            self.assertEqual(saved_delivery['state'], 'ACKNOWLEDGED')
                            self.assertIsNotNone(saved_delivery['protocol_ack'])

        asyncio.run(scenario())


if __name__ == '__main__':
    unittest.main()
