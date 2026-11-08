"""Public MCP reviewer loop: no reviewer-side filesystem or shell access."""
import asyncio
import hashlib
import json
import codecs
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


class ReviewerInputSDKTests(unittest.TestCase):
    def test_public_mcp_claim_read_assigned_inputs_and_submit_inline(self):
        self._exercise_public_mcp_claim_read_assigned_inputs_and_submit_inline()

    def test_public_mcp_provenance_claim_read_and_submit(self):
        self._exercise_public_mcp_claim_read_assigned_inputs_and_submit_inline(require_program=True)

    def _exercise_public_mcp_claim_read_assigned_inputs_and_submit_inline(self, require_program=False):
        import httpx
        import workflow_coordinator
        import workflow_review_transport
        import workflow_native_worker
        import test_workflow_native_execution as native
        import test_workflow_reviewer_api as api_fixture
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        from workflow_coordinator_http_mcp import PROFILE_PATHS

        async def exercise():
            fixture=api_fixture.ReviewerAPITests()
            if require_program:
                original=native.admit_native_fixture
                def provenance_fixture(*args, **kwargs):
                    kwargs['packs']=[{'pack_id':'provenance','selectors':[],'covers':['c1']},
                                     {'pack_id':'package','selectors':[],'covers':['c1']}]
                    kwargs['program_checks']=['managed_config_provenance']
                    return original(*args, **kwargs)
                with patch.object(api_fixture.review_fixtures, 'admit_native_fixture', provenance_fixture):
                    fixture.setUp()
            else:
                fixture.setUp()
            try:
                task=fixture.task; session=task['assigned_session_id']
                self.assertNotEqual('CLAIMED',fixture.store.get_task(task['task_id'])['state'])
                transport=workflow_review_transport.ReviewDispatchBackend(fixture.planner,server='http://127.0.0.1:1234',
                    agents={'implementation':'workflow-reviewer-implementation'},request=lambda *args:fixture.fake_transport(*args))
                coordinator=workflow_coordinator.ExplicitCoordinator(fixture.store,bindings={'author-token':object()},
                    review_bindings={'review-token':fixture.binding},review_results=fixture.api,artifact_dir=fixture.root,
                    identity_verifier=lambda _:True)
                coordinator.review_pipeline=SimpleNamespace(transport=transport); app=coordinator.app()
                def factory(headers=None,timeout=None,auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1:18444',headers=headers)
                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444'+PROFILE_PATHS['reviewer'],
                            {'Authorization':'Bearer review-token'},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            self.assertEqual({t.name for t in (await client.list_tools()).tools},
                                {'claim_task','complete_review','report_error','status'})
                            status=lambda **kw: client.call_tool('status',{'task_id':task['task_id'],'session_id':session,**kw})
                            async def body(call): return json.loads(call.content[0].text)
                            claim=json.loads((await client.call_tool('claim_task',{'task_id':task['task_id'],'session_id':session})).content[0].text)
                            self.assertTrue(claim.get('accepted'),claim)
                            self.assertEqual('CLAIMED',fixture.store.get_task(task['task_id'])['state'])
                            guide_bytes=bytearray(); offset=0; digest=None
                            while True:
                                page=await body(await status(input_ref='review_guide',input_offset=offset))
                                self.assertIn('input_access',page,page)
                                access=page['input_access']; digest=access['sha256']
                                self.assertEqual(access['encoding'],'utf-8')
                                self.assertEqual(access['offset'],offset)
                                if offset == 0:
                                    self.assertLessEqual(len(access['content'].encode()), 12 * 1024)
                                    self.assertEqual(access.get('content_kind'), 'review_guide')
                                    self.assertEqual(access.get('type'), 'PROVENANCE')
                                    self.assertIn('"document_type":"review_guide"', access['content'])
                                    self.assertIn('"semantic_identity":"This canonical review guide', access['content'])
                                guide_bytes.extend(access['content'].encode())
                                if access['eof']: break
                                self.assertEqual(access['next_offset'],len(guide_bytes)); offset=access['next_offset']
                            self.assertGreater(len(guide_bytes),4096)
                            guide=json.loads(guide_bytes)
                            self.assertEqual(guide['document_type'], 'review_guide')
                            self.assertEqual(guide['task_id'], task['task_id'])
                            self.assertTrue(guide['current_validation']['checks'])
                            self.assertIn('final chat prose or stopping without an accepted receipt does not submit a result',guide['instruction'])
                            self.assertEqual(hashlib.sha256(guide_bytes).hexdigest(),digest)
                            contract=guide['review_contract']; self.assertEqual(contract['$ref'],'#/$defs/review_result')
                            self.assertIn('review_result',contract['$defs'])
                            receipt=next(ref for ref in guide['assignment_artifact_refs'] if fixture.store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT')
                            art=fixture.store.get_artifact(receipt)['artifact']; raw=Path(art['path']).read_bytes()
                            self.assertIn(receipt,guide['assignment_artifact_refs'])
                            # Correctable bad ref, then usable exact ID on same assignment.
                            rejected=await body(await status(input_ref='foreign-artifact'))
                            self.assertFalse(rejected.get('ok',True))
                            self.assertEqual(rejected.get('code'),'INVALID_QUERY')
                            self.assertEqual('CLAIMED',fixture.store.get_task(task['task_id'])['state'])
                            data=bytearray(); offset=0
                            while True:
                                page=await body(await status(input_ref=receipt,input_offset=offset))
                                access=page['input_access']; data.extend(access['content'].encode())
                                self.assertEqual(access['sha256'],hashlib.sha256(raw).hexdigest())
                                if access['eof']: break
                                offset=access['next_offset']
                            self.assertEqual(bytes(data),raw)
                            if require_program:
                                decoded=json.loads(data)
                                program=next(row for row in decoded['program_checks'] if row.get('kind') == 'managed_config_provenance')
                                evidence=program['evidence']
                                self.assertEqual(evidence['status'],'PASS',program)
                                self.assertEqual(program['covers'],['c1'])
                                self.assertEqual(evidence['covers'],['c1'])
                                self.assertTrue(evidence['package_artifacts'],program)
                                for proof in evidence['package_artifacts']:
                                    self.assertEqual(proof['source_input_digest'],decoded['input_digest'],proof)
                                    archived=fixture.store.get_artifact(proof['artifact_ref'])
                                    self.assertIsNotNone(archived,proof)
                                    archive_bytes=Path(archived['artifact']['path']).read_bytes()
                                    self.assertEqual(proof['sha256'],hashlib.sha256(archive_bytes).hexdigest(),proof)
                            request,_=workflow_native_worker.load_native_inputs(fixture.store,task['run_id'])
                            self.assertTrue(request)
                            source=await body(await status(source_query='.py'))
                            self.assertIn('source_access',source)
                            source_path=source['source_access']['matches'][0]['path']
                            source_page=await body(await status(source_path=source_path,source_line=1))
                            access=source_page['source_access']
                            source_raw=Path(request['source_worktree'],source_path).read_bytes()
                            self.assertEqual(access['sha256'],hashlib.sha256(source_raw).hexdigest())
                            self.assertEqual(source_raw.decode().replace('\r\n','\n')[:len(access['content'])],access['content'])
                            scope=guide['review_scope']; criterion=scope['criteria'][0]
                            result={'task_id':guide['task_id'],
                                'decision':'PASS','findings':[],'owner_followups':[], 'inspected_refs':[receipt]}
                            import jsonschema
                            jsonschema.Draft202012Validator(contract).validate(result)
                            completed=await client.call_tool('complete_review',{'result':result,'session_id':session})
                            response=json.loads(completed.content[0].text)
                            self.assertTrue(response.get('accepted'),response)
                            saved=fixture.store.get_task(task['task_id'])
                            self.assertEqual(saved['state'],'COMPLETED')
                            self.assertEqual(saved['result']['decision'],'PASS')
            finally: fixture.tearDown()
        asyncio.run(exercise())


if __name__=='__main__': unittest.main()
