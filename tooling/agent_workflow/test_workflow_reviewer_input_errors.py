"""Reviewer SDK error reporting uses canonical inline payloads."""
import asyncio
import json
import unittest
from pathlib import Path
from types import SimpleNamespace


class ReviewerInlineErrorSDKTests(unittest.TestCase):
    def test_valid_inline_error_report_is_accepted_and_saved(self):
        import httpx
        import workflow_coordinator
        import workflow_review_transport
        import test_workflow_reviewer_api as api_fixture
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        from workflow_coordinator_http_mcp import PROFILE_PATHS

        async def exercise():
            fixture = api_fixture.ReviewerAPITests(); fixture.setUp()
            try:
                task=fixture.task; session=task['assigned_session_id']
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
                            claim=json.loads((await client.call_tool('claim_task',{'task_id':task['task_id'],'session_id':session})).content[0].text)
                            self.assertTrue(claim.get('accepted'),claim)
                            guide_meta=fixture.planner.review_guide(task['task_id'])
                            guide=json.loads(Path(guide_meta['path']).read_text(encoding='utf-8'))
                            error=dict(guide['error_report_template'],error_code='FIXTURE_TRANSPORT_FAILURE',
                                message='Assigned evidence endpoint returned a factual fixture failure.')
                            schema=guide['review_contract']['$defs']['task_error']
                            self.assertEqual(set(schema['required']),set(error))
                            self.assertNotIn('run_id',error)
                            malformed=dict(error); malformed.pop('message',None)
                            bad=await client.call_tool('report_error',{'error':malformed,'session_id':session})
                            bad_body=json.loads(bad.content[0].text)
                            self.assertFalse(bad_body.get('ok',True),bad_body)
                            self.assertEqual('INVALID_TOOL_ARGUMENTS',bad_body.get('code'))
                            self.assertEqual('CORRECT_INPUT',bad_body.get('next_action'))
                            self.assertEqual('CLAIMED',fixture.store.get_task(task['task_id'])['state'])
                            both=await client.call_tool('report_error',{'error':error,'error_path':'never-read.json','session_id':session})
                            self.assertEqual('INVALID_TOOL_ARGUMENTS',json.loads(both.content[0].text).get('code'))
                            neither=await client.call_tool('report_error',{'session_id':session})
                            self.assertEqual('INVALID_TOOL_ARGUMENTS',json.loads(neither.content[0].text).get('code'))
                            result=await client.call_tool('report_error',{'error':error,'session_id':session})
                            response=json.loads(result.content[0].text)
                            self.assertTrue(response.get('accepted'),response)
                            saved=fixture.store.get_task(task['task_id'])
                            self.assertEqual('COMPLETED',saved['state'])
                            self.assertEqual('ERROR',saved['result']['decision'])
                            incident=fixture.store.get_task_incident(task['task_id'])
                            self.assertEqual(error['error_code'],incident['code'])
                            self.assertEqual(error['message'],incident['message'])
                            self.assertEqual(error['artifact_refs'],incident['artifact_refs'])
            finally:
                fixture.tearDown()
        asyncio.run(exercise())

    def test_status_authorizes_before_input_access_and_rejects_foreign_guide(self):
        import test_workflow_reviewer_api as api_fixture
        from unittest.mock import patch
        fixture=api_fixture.ReviewerAPITests(); fixture.setUp()
        try:
            other=api_fixture.ReviewerAPITests(); other.setUp()
            try:
                claimed=fixture.claim(); self.assertTrue(claimed.json().get('accepted'))
                with patch.object(fixture.planner,'input_access',wraps=fixture.planner.input_access) as access:
                    wrong_session=fixture.client.get('/v1/review/status',headers=fixture.headers(session='wrong-session'),
                        params={'task_id':fixture.task['task_id'],'input_ref':'review_guide'})
                    self.assertNotEqual(200,wrong_session.status_code); access.assert_not_called()
                    wrong_role=fixture.client.get('/v1/review/status',headers=fixture.headers('author-token'),
                        params={'task_id':fixture.task['task_id'],'input_ref':'review_guide'})
                    self.assertEqual(403,wrong_role.status_code); access.assert_not_called()
                foreign=other.planner.input_access(other.task['task_id'],'review_guide',0)
                self.assertTrue(foreign)
                denied=fixture.client.get('/v1/review/status',headers=fixture.headers(),params={
                    'task_id':fixture.task['task_id'],'input_ref':foreign['artifact_id']})
                self.assertEqual('INVALID_QUERY',denied.json()['error']['code'])
                self.assertEqual('CLAIMED',fixture.store.get_task(fixture.task['task_id'])['state'])
                valid=fixture.client.get('/v1/review/status',headers=fixture.headers(),params={
                    'task_id':fixture.task['task_id'],'input_ref':'review_guide'})
                self.assertEqual(200,valid.status_code,valid.text); self.assertIn('input_access',valid.json())
            finally: other.tearDown()
        finally: fixture.tearDown()

    def test_malformed_input_offsets_are_correctable_without_terminal_error(self):
        import test_workflow_reviewer_api as api_fixture
        fixture=api_fixture.ReviewerAPITests(); fixture.setUp()
        try:
            self.assertTrue(fixture.claim().json().get('accepted'))
            base={'task_id':fixture.task['task_id']}
            malformed=[{'input_ref':'review_guide','input_offset':'-1'},
                       {'input_ref':'review_guide','input_offset':'true'}, {'input_offset':'0'}]
            for selector in malformed:
                response=fixture.client.get('/v1/review/status',headers=fixture.headers(),params={**base,**selector})
                self.assertEqual(400,response.status_code,response.text)
                self.assertEqual('INVALID_QUERY',response.json()['error']['code'])
            bad=fixture.client.get('/v1/review/status',headers=fixture.headers(),params={**base,'input_ref':'not-registered'})
            self.assertEqual('INVALID_QUERY',bad.json()['error']['code'])
            self.assertEqual('CLAIMED',fixture.store.get_task(fixture.task['task_id'])['state'])
            valid=fixture.client.get('/v1/review/status',headers=fixture.headers(),params={**base,'input_ref':'review_guide','input_offset':'0'})
            self.assertEqual(200,valid.status_code,valid.text)
        finally: fixture.tearDown()

    def test_invalid_error_payload_does_not_terminalize_claim(self):
        import test_workflow_reviewer_api as api_fixture
        fixture=api_fixture.ReviewerAPITests(); fixture.setUp()
        try:
            self.assertTrue(fixture.claim().json().get('accepted'))
            error=fixture.planner.error_report_template(fixture.task['task_id'],error_code='FIXTURE_FAILURE',message='Factual fixture failure.')
            # Canonical payload is validated before a task can receive an ERROR verdict.
            malformed=dict(error); malformed.pop('message',None)
            response=fixture.client.post('/v1/review/error',headers=fixture.headers(),json=malformed)
            self.assertEqual(200,response.status_code,response.text)
            self.assertFalse(response.json().get('accepted'),response.json())
            self.assertEqual('CLAIMED',fixture.store.get_task(fixture.task['task_id'])['state'])
        finally: fixture.tearDown()


if __name__ == '__main__': unittest.main()
