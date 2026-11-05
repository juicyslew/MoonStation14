"""Public reviewer MCP recovery from a failed pre-claim input preparation."""
import asyncio
import json
import unittest
from types import SimpleNamespace
from unittest.mock import patch


class ReviewerPreclaimErrorSDKTests(unittest.TestCase):
    def test_preclaim_input_failure_can_be_reported_immediately(self):
        async def exercise():
            import httpx
            import workflow_coordinator
            import workflow_review_transport
            import workflow_store
            import test_workflow_reviewer_api as api_fixture
            from mcp import ClientSession
            from mcp.client.streamable_http import streamablehttp_client
            from workflow_coordinator_http_mcp import PROFILE_PATHS

            fixture = api_fixture.ReviewerAPITests()
            fixture.setUp()
            try:
                task = fixture.task
                session = task['assigned_session_id']
                transport = workflow_review_transport.ReviewDispatchBackend(fixture.planner,
                    server='http://127.0.0.1:1234', agents={'implementation':'workflow-reviewer-implementation'},
                    request=lambda *args: fixture.fake_transport(*args))
                coordinator = workflow_coordinator.ExplicitCoordinator(fixture.store,
                    bindings={'author-token':object()}, review_bindings={'review-token':fixture.binding},
                    review_results=fixture.api, artifact_dir=fixture.root, identity_verifier=lambda _:True)
                coordinator.review_pipeline = SimpleNamespace(transport=transport)
                app = coordinator.app()
                def factory(headers=None, timeout=None, auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),
                        base_url='http://127.0.0.1:18444', headers=headers)

                with patch.object(fixture.planner, 'prepare_review_inputs',
                                  side_effect=workflow_store.ProtocolError('fixture unavailable')):
                    async with app.router.lifespan_context(app):
                        async with streamablehttp_client('http://127.0.0.1:18444'+PROFILE_PATHS['reviewer'],
                                {'Authorization':'Bearer review-token'}, httpx_client_factory=factory) as (r,w,_):
                            async with ClientSession(r,w) as client:
                                await client.initialize()
                                async def call(name, payload):
                                    return json.loads((await client.call_tool(name, payload)).content[0].text)
                                failed_claim = await call('claim_task', {'task_id':task['task_id'],'session_id':session})
                                claim_error=failed_claim.get('error',failed_claim)
                                self.assertEqual('REVIEW_INPUT_UNAVAILABLE', claim_error.get('code'), failed_claim)
                                self.assertEqual('RETRY_CLAIM', claim_error.get('next_action'), failed_claim)
                                self.assertEqual('DISPATCHING', fixture.store.get_task(task['task_id'])['state'])
                                error = {'task_id':task['task_id'],'error_code':'REVIEW_INPUT_UNAVAILABLE',
                                    'message':'Assigned review input could not be read.','artifact_refs':['review_guide']}
                                invalid = await call('report_error', {'error':error,'session_id':session})
                                self.assertFalse(invalid['accepted'])
                                self.assertEqual('INVALID_REVIEW_RESULT',invalid['code'])
                                self.assertEqual('artifact_refs[0]',invalid['field'])
                                self.assertIn('registered artifact ID',invalid['recovery'])
                                self.assertIn('artifact_refs=[]',invalid['recovery'])
                                self.assertEqual('DISPATCHING', fixture.store.get_task(task['task_id'])['state'])
                                corrected = {**error,'artifact_refs':[]}
                                accepted = await call('report_error', {'error':corrected,'session_id':session})
                                self.assertTrue(accepted['accepted'],accepted)
                                self.assertEqual('ACCEPTED',accepted['disposition'])
                                saved = fixture.store.get_task(task['task_id'])
                                self.assertEqual('COMPLETED',saved['state'])
                                self.assertEqual('ERROR',saved['result']['decision'])
                                replay = await call('report_error', {'error':corrected,'session_id':session})
                                self.assertTrue(replay['accepted'],replay)
                                self.assertEqual('REPLAYED',replay['disposition'])
            finally:
                fixture.tearDown()
        asyncio.run(exercise())


if __name__ == '__main__': unittest.main()
