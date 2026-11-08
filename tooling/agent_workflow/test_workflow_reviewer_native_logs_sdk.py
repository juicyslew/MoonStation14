"""Reviewer SDK access to receipt-bound native command logs."""
import asyncio
import json
import unittest
from unittest.mock import patch


class ReviewerNativeLogsSDKTests(unittest.TestCase):
    def test_native_log_is_discoverable_pageable_and_required_for_coverage_finding(self):
        import test_workflow_native_execution as native
        import test_workflow_reviewer_api as api_fixture

        async def exercise():
            fixture = api_fixture.ReviewerAPITests()
            original = native.admit_native_fixture

            producer_code = 'from pathlib import Path\np=Path("build/output.zip"); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b"pass")\nprint("x"*15000)\nprint("MS14_EVA_FIXTURE_COMMAND_PASS finite=true entered=true roofLights=true kitOwned=true")\n'
            def command_fixture(*args, **kwargs):
                kwargs['producer_code'] = producer_code
                return original(*args, **kwargs)

            with patch.object(api_fixture.review_fixtures, 'admit_native_fixture', command_fixture):
                fixture.setUp()
            try:
                import workflow_coordinator, workflow_review_transport
                from types import SimpleNamespace
                import httpx
                from mcp import ClientSession
                from mcp.client.streamable_http import streamablehttp_client
                from workflow_coordinator_http_mcp import PROFILE_PATHS

                transport = workflow_review_transport.ReviewDispatchBackend(fixture.planner,
                    server='http://127.0.0.1:1234', agents={'implementation':'workflow-reviewer-implementation'},
                    request=lambda *args: fixture.fake_transport(*args))
                coordinator = workflow_coordinator.ExplicitCoordinator(fixture.store, bindings={'author-token':object()},
                    review_bindings={'review-token':fixture.binding}, review_results=fixture.api,
                    artifact_dir=fixture.root, identity_verifier=lambda _:True)
                coordinator.review_pipeline = SimpleNamespace(transport=transport)
                app = coordinator.app()
                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url='http://127.0.0.1:18444', headers=headers)
                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444'+PROFILE_PATHS['reviewer'],
                            {'Authorization':'Bearer review-token'}, httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            task=fixture.task; session=task['assigned_session_id']
                            async def body(call): return json.loads(call.content[0].text)
                            self.assertTrue((await body(await client.call_tool('claim_task', {'task_id':task['task_id'],'session_id':session})))['accepted'])
                            async def status(**kw): return await body(await client.call_tool('status', {'task_id':task['task_id'],'session_id':session,**kw}))
                            saved_hash=fixture.planner.build_assignment(task['task_id'])['body_sha256']
                            guide_bytes=bytearray(); offset=0
                            while True:
                                page=(await status(input_ref='review_guide',input_offset=offset))['input_access']
                                if offset == 0: self.assertIn('native_logs',page['content'])
                                guide_bytes.extend(page['content'].encode())
                                if page['eof']: break
                                offset=page['next_offset']
                            guide=json.loads(guide_bytes)
                            self.assertEqual(guide['assignment_body_sha256'], saved_hash)
                            logs=guide['current_validation']['native_logs']
                            log=logs[0]; self.assertEqual(log['kind'], 'build')
                            criterion=guide['review_scope']['criteria'][0]['criterion_id']
                            finding={'criterion_id':criterion,'category':'COVERAGE','message':'Concrete branch is not covered.',
                                'validation_stage':'TECHNICAL','location':'branch','evidence_refs':[log['parent_receipt_ref']],
                                'required_change':'Add branch check.','coverage_basis':'INSUFFICIENT_CHECK_COVERAGE'}
                            rejected_result={'task_id':task['task_id'],'decision':'FAIL','findings':[finding],
                                'owner_followups':[],'inspected_refs':[log['parent_receipt_ref']]}
                            rejected=await body(await client.call_tool('complete_review',{'result':rejected_result,'session_id':session}))
                            self.assertFalse(rejected.get('accepted'),rejected)
                            foreign=await status(input_ref='foreign-native-log')
                            self.assertFalse(foreign.get('ok',True))
                            artifact=fixture.store.get_artifact(log['artifact_id'])['artifact']
                            from pathlib import Path
                            raw=Path(artifact['path']).read_bytes()
                            try:
                                Path(artifact['path']).write_bytes(raw+b' tamper')
                                stale=await status(input_ref=log['artifact_id'])
                                self.assertFalse(stale.get('ok',True))
                                # Shared provenance rejects tampered bytes before paging.
                                self.assertEqual(stale.get('code'),'SOURCE_UNAVAILABLE')
                                self.assertNotIn('input_access',stale)
                            finally: Path(artifact['path']).write_bytes(raw)
                            first=await status(input_ref=log['artifact_id'],input_offset=0)
                            page=first['input_access']; self.assertFalse(page['eof']); self.assertNotIn('MS14_EVA_FIXTURE_COMMAND_PASS',page['content'])
                            rest=await status(input_ref=log['artifact_id'],input_offset=page['next_offset'])
                            self.assertIn('MS14_EVA_FIXTURE_COMMAND_PASS',rest['input_access']['content'])
                            self.assertEqual(fixture.planner.build_assignment(task['task_id'])['body_sha256'],saved_hash)
                            self.assertEqual(log['parent_receipt_ref'],rest['input_access']['parent_receipt_ref'])
                            result={'task_id':task['task_id'],'decision':'PASS','findings':[],
                                'owner_followups':[],'inspected_refs':[log['artifact_id'],log['parent_receipt_ref']]}
                            reply=await body(await client.call_tool('complete_review',{'result':result,'session_id':session}))
                            self.assertTrue(reply.get('accepted'),reply)
                            replay=await body(await client.call_tool('complete_review',{'result':result,'session_id':session}))
                            self.assertTrue(replay.get('accepted'),replay)
            finally:
                fixture.tearDown()
        asyncio.run(exercise())


if __name__ == '__main__': unittest.main()
