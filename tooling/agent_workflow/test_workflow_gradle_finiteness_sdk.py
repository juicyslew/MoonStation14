"""Public SDK regression for finite Gradle argv normalization."""
import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import workflow_coordinator
import workflow_runtime
import workflow_store
import workflow_preflight
import workflow_native_worker
from preflight_test_support import make_preflight_case
from test_workflow_coordinator_mcp import _stdio_coordinator_fixture
from preflight_test_support import prepare_dynamic_author_submission


class GradleFinitenessSDKTests(unittest.TestCase):
    def test_submitted_gradle_check_is_normalized_at_native_runner_boundary(self):
        from mcp import ClientSession
        from mcp.client.stdio import stdio_client

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            case = make_preflight_case(root, caller='gradle-finite-sdk', key='gradle-finite-sdk')
            plan = {'registry_version': 1, 'packs': [], 'plan': {
                'title': 'Gradle finiteness', 'kind': 'feature', 'acceptance_criteria': ['c1'],
                'fixture': 'manual.md', 'manual_steps': ['Run the check.'], 'references': {},
                'checks': [{'argv': ['gradle', 'check'], 'rationale': 'finite argv regression',
                            'covers': ['c1'], 'kind': 'command'}]}, 'program_checks': []}
            floors={'required_packs':sorted({p for packs in case.policy['coverage'].values() for p in packs}),
                'selectors':case.policy['selector_requirements'],'review_roles':case.policy['review_roles'],
                'owner_exceptions':case.policy['owner_exceptions'],'config_overrides':case.policy['config_overrides'],
                'review_envelope_limit':case.policy['review_envelope_limit_bytes']}
            token='fixture-token'
            slot=workflow_coordinator.AuthorWorktreeBinding('slot',token,'context',str(case.repo),floors,
                str(case.receipt),case.context.publication_receipt_sha256)
            sessions={'gradle-author':{'id':'gradle-author','location':{'directory':str(case.repo)},'agent':'orchestrate-ms14'}}
            verifier=workflow_coordinator.OpenCodeIdentityVerifier('loopback',session_lookup=lambda server,sid:sessions.get(sid),
                role_lookup=lambda server,agent,directory:{'mode':'primary'})
            store = workflow_store.WorkflowStore(root / 'state.sqlite')
            coordinator = workflow_coordinator.ExplicitCoordinator(
                store, bindings={}, author_worktrees={token:slot},
                artifact_dir=root / 'artifacts', handlers={}, identity_verifier=verifier,
                reconcile_interval=30)
            request_file = root / 'request.json'
            scope_file = root / 'scope.json'

            async def scenario(params):
                async with asyncio.timeout(30):
                    async with stdio_client(params) as (reader, writer):
                        async with ClientSession(reader, writer) as session:
                            await session.initialize()
                            catalog = await session.list_tools()
                            names = {tool.name for tool in catalog.tools}
                            self.assertEqual(names, {'submit', 'status', 'acknowledge_result', 'register_scope', 'scope_status'})
                            for tool in catalog.tools: self.assertIn('session_id',tool.inputSchema['required'],tool.name)
                            sid='gradle-author'
                            status = json.loads((await session.call_tool('status', {'session_id':sid})).content[0].text)
                            context=status['author_context']
                            scope,request=prepare_dynamic_author_submission(context,base_policy=case.policy,repo=case.repo,
                                submit_key=case.key,criterion='c1',artifact_id='artifact_gradle',
                                artifact_path=root/'evidence',request_path=request_file)
                            request['plan_digest']=workflow_store.digest(plan)
                            request_file.write_text(json.dumps(request),encoding='utf-8')
                            scope_file.write_text(json.dumps(scope),encoding='utf-8')
                            registered = json.loads((await session.call_tool('register_scope', {
                                'scope_path': str(scope_file), 'idempotency_key': 'scope-registration','session_id':sid
                            })).content[0].text)
                            self.assertEqual(registered['response']['status'], 'REGISTERED')
                            accepted = json.loads((await session.call_tool('submit', {
                                'request_path': str(request_file), 'idempotency_key': case.key,'session_id':sid
                            })).content[0].text)
                            self.assertIn('response', accepted, json.dumps(accepted))
                            self.assertEqual(accepted['response']['status'], 'ACCEPTED', json.dumps(accepted))
                            status = json.loads((await session.call_tool('status', {
                                'idempotency_key': case.key,'session_id':sid
                            })).content[0].text)
                            self.assertEqual(status['run']['run_id'], accepted['response']['run_id'])
                            return accepted['response']['run_id']

            with _stdio_coordinator_fixture(coordinator, root, case.repo, token, author_mode=True) as params:
                with patch.object(workflow_preflight, 'resolve_check_packs', return_value=plan):
                    run_id = asyncio.run(scenario(params))

            db = store._connect()
            try:
                task_row = db.execute('select id from tasks where run_id=?', (run_id,)).fetchone()
            finally:
                db.close()
            self.assertIsNotNone(task_row)
            task = store.get_task(task_row[0])
            request, bundle = workflow_native_worker.load_native_inputs(store, run_id)
            frozen = bundle['resolved_plan']
            self.assertEqual(workflow_store.digest(frozen), request['plan_digest'])

            class Completed:
                pid = 123456
                returncode = 0
                def communicate(self, timeout=None): return b'', b''
                def wait(self, timeout=None): return 0
                def poll(self): return 0

            for argv, expected_reject in ((['gradle', 'check'], False), (['gradle', 'check', '--daemon'], True)):
                with self.subTest(argv=argv), tempfile.TemporaryDirectory() as check_tmp:
                    calls = []
                    def contained_popen(command, *args, **kwargs):
                        calls.append(list(command))
                        return Completed()

                    plan_for_check = json.loads(json.dumps(frozen['plan']))
                    plan_for_check['checks'][0]['argv'] = argv
                    with patch.object(workflow_runtime.subprocess, 'Popen', side_effect=contained_popen):
                        records = workflow_runtime.run_finite_checks(case.repo, plan_for_check, Path(check_tmp) / 'logs')
                    if expected_reject:
                        self.assertEqual(calls, [])
                        self.assertEqual(records[0]['exit_code'], 127)
                        self.assertNotIn('executed_argv', records[0])
                    else:
                        self.assertEqual(calls, [['gradle', 'check', '--no-daemon']])
                        self.assertEqual(['gradle', 'check'], records[0]['argv'])
                        self.assertEqual(['gradle', 'check', '--no-daemon'], records[0]['executed_argv'])
            self.assertEqual('QUEUED', task['state'])


if __name__ == '__main__':
    unittest.main()
