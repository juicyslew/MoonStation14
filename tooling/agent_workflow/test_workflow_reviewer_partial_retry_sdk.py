"""Public reviewer SDK sees immutable successful evidence across native retry."""
import asyncio
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace


class ReviewerPartialRetrySDKTests(unittest.TestCase):
    def test_native_prefix_retry_logs_are_claimable_and_pageable_in_reviewer_sdk(self):
        import workflow_coordinator
        import workflow_review_pipeline
        import workflow_review_results
        import workflow_review_transport
        import workflow_native_retry
        from test_workflow_native_execution import admit_native_fixture
        from test_workflow_review import ReviewPlanningTests
        from test_workflow_review_transport import FakeTransport

        async def exercise():
            with tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                producer = "from pathlib import Path\nimport zipfile\np=Path('build/output.zip');p.parent.mkdir(parents=True,exist_ok=True)\nwith zipfile.ZipFile(p,'w') as z:z.writestr('x','native')\nc=Path('build/prefix-count.txt');c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\nprint('X'*15000)\nprint('PREFIX_NATIVE_PASS')\n"
                retry_command = "from pathlib import Path\np=Path('build/retry-once.flag')\nif not p.exists():p.write_text('retry');raise SystemExit(127)\nprint('RETRY_NATIVE_PASS')\n"
                checks = [
                    {'argv': [__import__('sys').executable, '-c', producer], 'rationale': 'native prefix producer',
                     'kind': 'build', 'covers': ['c1'], 'artifacts': ['build/output.zip']},
                    {'argv': [__import__('sys').executable, '-c', retry_command], 'rationale': 'retryable command',
                     'kind': 'command', 'covers': ['c1']},
                ]
                _, store, receipt, original_id = admit_native_fixture(root, caller='reviewer-partial-retry',
                    key='reviewer-partial-retry', checks=checks,
                    packs=[{'pack_id':'provenance','selectors':[],'covers':['c1']},
                           {'pack_id':'package','selectors':[],'covers':['c1']}],
                    program_checks=['managed_config_provenance'])
                run_id = receipt['run_id']
                helper = ReviewPlanningTests()
                original = helper.run_check(root, store, run_id, original_id)
                self.assertEqual('ERROR', original['result']['decision'])
                prefix_records = store.list_native_check_records(original_id)
                self.assertTrue(prefix_records)
                self.assertEqual(0, prefix_records[0]['check_index'])
                self.assertEqual('ERROR', original['result']['decision'])
                self.assertEqual('1', (Path(store.get_run(run_id)['run']['author']['worktree']) / 'build/prefix-count.txt').read_text())
                completion_digest = store.get_task_result_digest(original_id)
                completion = store.get_event(store._task_result_event_id(original_id, completion_digest))
                self.assertEqual('TASK_COMPLETED', completion['kind'])
                scope = store.get_native_scope(original_id)
                self.assertEqual('CLEANED', scope['state'])
                resources = store.get_task_resources(original_id)
                self.assertTrue(resources)
                self.assertTrue(all(row['state'] == 'RELEASED' for row in resources))

                old_planner = helper.coordinator(store, run_id)
                old_results = workflow_review_results.ReviewResults(store, old_planner)
                workflow_review_pipeline.ReviewPipeline(store, old_planner, old_results, object(), object()).completed(completion['payload'])
                settled = store.get_run(run_id)
                self.assertEqual(('DECIDED', 'ERROR', 'CONFIRMED'),
                    (settled['attempt']['phase'], settled['attempt']['result'], settled['attempt']['cleanup_state']))

                linked = workflow_native_retry.NativeRetry(store, root/'artifacts').prepare(run_id)
                self.assertEqual(2, linked['generation'])
                retried = helper.run_check(root, store, run_id, linked['task']['task_id'])
                self.assertEqual('PASS', retried['result']['decision'])
                retry_artifact = next(store.get_artifact(ref)['artifact'] for ref in retried['result']['artifact_refs']
                    if store.get_artifact(ref) and store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT')
                retry_receipt = json.loads(Path(retry_artifact['path']).read_text(encoding='utf-8'))
                provenance_check = next(row for row in retry_receipt['program_checks']
                    if row.get('kind') == 'managed_config_provenance')
                self.assertEqual('PASS', provenance_check['evidence']['status'], provenance_check)
                self.assertTrue(provenance_check['evidence']['package_artifacts'], provenance_check)
                self.assertEqual('1', (Path(store.get_run(run_id)['run']['author']['worktree']) / 'build/prefix-count.txt').read_text())
                self.assertEqual('ERROR', store.get_task(original_id)['result']['decision'])
                import workflow_task_artifacts
                unlinked_log, _ = workflow_task_artifacts.register_bytes(store, store.get_task(original_id),
                    root/'artifacts', 'LOG', b'unlinked historical log')

                planner = helper.coordinator(store, run_id)
                planner.artifact_dir = root/'review-artifacts'
                task = planner.plan_reviews(run_id)[0]
                results = workflow_review_results.ReviewResults(store, planner)
                fake = FakeTransport()
                transport = workflow_review_transport.ReviewDispatchBackend(planner,
                    server='http://127.0.0.1:1234', agents={'implementation':'workflow-reviewer-implementation'}, request=fake)
                self.assertEqual('APPLIED', transport.dispatch('fixture-dispatch', {'task_id': task['task_id']}))
                binding = workflow_coordinator.ReviewerBinding('reviewer', planner.reviewers['implementation']['worktree'], 'implementation')
                coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={'author-token': object()},
                    review_bindings={'review-token': binding}, review_results=results,
                    artifact_dir=root, identity_verifier=lambda _: True)
                coordinator.review_pipeline = SimpleNamespace(transport=transport)
                app = coordinator.app()
                import httpx
                from mcp import ClientSession
                from mcp.client.streamable_http import streamablehttp_client
                from workflow_coordinator_http_mcp import PROFILE_PATHS

                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                        base_url='http://127.0.0.1:18444', headers=headers)

                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444'+PROFILE_PATHS['reviewer'],
                            {'Authorization':'Bearer review-token'}, httpx_client_factory=factory) as (reader, writer, _):
                        async with ClientSession(reader, writer) as client:
                            await client.initialize()
                            session = task['assigned_session_id']
                            async def body(call): return json.loads(call.content[0].text)
                            claim = await body(await client.call_tool('claim_task', {'task_id':task['task_id'],'session_id':session}))
                            self.assertTrue(claim.get('accepted'), claim)
                            async def status(**kwargs):
                                return await body(await client.call_tool('status', {'task_id':task['task_id'],'session_id':session,**kwargs}))
                            guide_data = bytearray(); offset = 0
                            while True:
                                page = (await status(input_ref='review_guide',input_offset=offset))['input_access']
                                guide_data.extend(page['content'].encode())
                                if page['eof']: break
                                offset = page['next_offset']
                            guide = json.loads(guide_data)
                            provenance_ref = next(ref for ref in guide['assignment_artifact_refs']
                                if store.get_artifact(ref)['artifact']['type'] == 'CHECK_RECEIPT')
                            receipt_pages = bytearray(); offset = 0
                            while True:
                                page = (await status(input_ref=provenance_ref,input_offset=offset))['input_access']
                                receipt_pages.extend(page['content'].encode())
                                if page['eof']: break
                                offset = page['next_offset']
                            sdk_receipt = json.loads(receipt_pages)
                            guide_provenance = next(row for row in sdk_receipt['program_checks']
                                if row.get('kind') == 'managed_config_provenance')
                            self.assertEqual('PASS', guide_provenance['evidence']['status'], guide_provenance)
                            self.assertTrue(guide_provenance['evidence']['package_artifacts'], guide_provenance)
                            logs = guide['current_validation']['native_logs']
                            self.assertGreaterEqual(len(logs), 2, logs)
                            old_log = next(row for row in logs if row['producer_task_id'] == original_id)
                            new_log = next(row for row in logs if row['producer_task_id'] == linked['task']['task_id'])
                            assignment_hash = guide['assignment_body_sha256']
                            for log, marker in ((old_log, 'PREFIX_NATIVE_PASS'), (new_log, 'RETRY_NATIVE_PASS')):
                                data = bytearray(); offset = 0
                                while True:
                                    page = (await status(input_ref=log['artifact_id'],input_offset=offset))['input_access']
                                    self.assertEqual(assignment_hash, page['assignment_body_sha256'])
                                    if log is old_log and offset == 0:
                                        self.assertFalse(page['eof'])
                                        self.assertNotIn(marker, page['content'])
                                    data.extend(page['content'].encode())
                                    if page['eof']: break
                                    offset = page['next_offset']
                                self.assertEqual(log['sha256'], hashlib.sha256(data).hexdigest())
                                self.assertIn(marker, data.decode())
                            rejected = await status(input_ref=unlinked_log)
                            self.assertFalse(rejected.get('ok', True))
                            self.assertEqual('INVALID_QUERY', rejected.get('code'))
                            self.assertEqual(assignment_hash, planner.build_assignment(task['task_id'])['body_sha256'])
                            receipt_ref = provenance_ref
                            result = {'task_id':task['task_id'], 'decision':'PASS', 'findings':[],
                                'owner_followups':[], 'inspected_refs':[old_log['artifact_id'],new_log['artifact_id'],receipt_ref]}
                            accepted = await body(await client.call_tool('complete_review', {'result':result,'session_id':session}))
                            self.assertTrue(accepted.get('accepted'), accepted)
                            replay = await body(await client.call_tool('complete_review', {'result':result,'session_id':session}))
                            self.assertTrue(replay.get('accepted'), replay)
                            self.assertEqual('ERROR', store.get_task(original_id)['result']['decision'])
        asyncio.run(exercise())


if __name__ == '__main__':
    unittest.main()
