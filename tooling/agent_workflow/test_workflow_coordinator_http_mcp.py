import asyncio
import json
import tempfile
import unittest
from pathlib import Path

import httpx
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from workflow_coordinator_http_mcp import CoordinatorHTTPMCP, PROFILE_PATHS


class HTTPMCPTests(unittest.TestCase):
    def test_http_mcp_local_file_errors_and_tool_schemas_are_actionable(self):
        from types import SimpleNamespace
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        with tempfile.TemporaryDirectory() as tmp:
            repo=Path(tmp); bridge=CoordinatorHTTPMCP(SimpleNamespace(author_worktrees={'a':SimpleNamespace(repo=str(repo))},bindings={},review_bindings={}),Starlette())
            async def outer(request): return await bridge.request('author',request)
            app=Starlette(routes=[Route('/mcp/author',outer,methods=['POST','GET','DELETE'])])
            def factory(headers=None,timeout=None,auth=None): return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1',headers=headers)
            async def run():
                async with bridge.managers['author'].run():
                    async with streamablehttp_client('http://127.0.0.1/mcp/author',{'Authorization':'Bearer a'},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as session:
                            await session.initialize(); tools=(await session.list_tools()).tools
                            submit=next(t for t in tools if t.name=='submit')
                            self.assertTrue(submit.description); self.assertIn('request_path',submit.inputSchema['required'])
                            response=await session.call_tool('submit',{'request_path':'missing.json','idempotency_key':'same','session_id':'chat'})
                            self.assertFalse(response.isError, response.content[0].text)
                            result=json.loads(response.content[0].text)
                            self.assertEqual((result['code'],result['field'],result['idempotency_key']),('INPUT_FILE_NOT_FOUND','request_path','same'))
            asyncio.run(run())

    def test_direct_backend_rejections_retain_safe_fields(self):
        from workflow_coordinator_mcp import CoordinatorClient
        result=CoordinatorClient.interpret_response('acknowledge_result',{'event_id':'e','result_id':'r','run_id':'run','command_id':'cmd'},400,
            b'{"accepted":false,"code":"STALE_VIEW","message":"refresh","next_action":"CHECK_RESULT_STATUS","field":"result_digest","recovery":"use current receipt","debug":"secret"}')
        self.assertEqual((result['code'],result['field'],result['recovery'],result['event_id'],result['result_id'],result['run_id'],result['command_id']),('STALE_VIEW','result_digest','use current receipt','e','r','run','cmd'))
        self.assertNotIn('debug',result)

    def test_fixed_catalogs_credentials_and_reused_rest_route_in_process(self):
        from types import SimpleNamespace
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client

        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp)
            author = SimpleNamespace(repo=str(repo))
            reviewer = SimpleNamespace(worktree=str(repo))
            coordinator = SimpleNamespace(author_worktrees={"author-token": author}, bindings={},
                review_bindings={"review-token": reviewer})
            seen=[]
            async def rest(request):
                seen.append((request.url.path, request.url.query, request.headers.get("authorization"), request.headers.get("x-ms14-author-session")))
                return JSONResponse({"run":None,"attempt":None,"submission":None,"freshness":"not_checked","coordinator":{},"diagnostics":{"logs":[{"text":"compiler failure: " + "x" * 8192}]}})
            rest_app=Starlette(routes=[Route("/v1/status",rest)])
            bridge=CoordinatorHTTPMCP(coordinator,rest_app)
            async def outer(request: Request):
                path=request.url.path
                profile=next(k for k,v in PROFILE_PATHS.items() if v==path)
                return await bridge.request(profile,request)
            app=Starlette(routes=[Route(p,outer,methods=["GET","POST","DELETE"]) for p in PROFILE_PATHS.values()])
            bridge.rest_app=rest_app

            def factory(headers=None, timeout=None, auth=None):
                return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url="http://127.0.0.1",headers=headers)
            async def scenario():
                async with bridge.managers["author"].run(), bridge.managers["reviewer"].run():
                    async with streamablehttp_client("http://127.0.0.1/mcp/author",{"Authorization":"Bearer author-token"},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            tools=await client.list_tools()
                            self.assertEqual({x.name for x in tools.tools},{"submit","register_scope","scope_status","status","acknowledge_result"})
                            status_tool=next(x for x in tools.tools if x.name=='status')
                            self.assertEqual(status_tool.inputSchema['properties']['include_diagnostics']['type'],'boolean')
                            self.assertEqual(status_tool.inputSchema['properties']['log_offset']['type'],'integer')
                            result=await client.call_tool("status",{"session_id":"session-A","run_id":"run-fixture","include_diagnostics":True,"log_artifact_id":"artifact-1","log_offset":4096})
                            decoded=json.loads(result.content[0].text)
                            diagnostic_text=decoded['diagnostics']['logs'][0]['text']
                            self.assertIn('compiler failure:',diagnostic_text)
                            self.assertGreater(len(diagnostic_text),4096)
                            self.assertLessEqual(len(result.content[0].text.encode('utf-8')),64*1024)
                            self.assertNotIn('response_too_large',result.content[0].text)
                    async with streamablehttp_client("http://127.0.0.1/mcp/reviewer",{"Authorization":"Bearer review-token"},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            tools=await client.list_tools()
                            self.assertEqual({x.name for x in tools.tools},{"claim_task","complete_review","report_error","status"})
                    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url="http://127.0.0.1:18443") as raw:
                        wrong=await raw.post("/mcp/author",headers={"Authorization":"Bearer review-token","Origin":"http://127.0.0.1:18443"},json={})
                        self.assertEqual(wrong.status_code,403)
                        wrong_profile=await raw.post("/mcp/reviewer",headers={"Authorization":"Bearer author-token","Origin":"http://127.0.0.1:18443"},json={})
                        self.assertEqual(wrong_profile.status_code,403)
                        evil=await raw.post("/mcp/author",headers={"Authorization":"Bearer author-token","Origin":"http://evil.invalid"},json={})
                        self.assertGreaterEqual(evil.status_code,400)
                        get=await raw.get("/mcp/author",headers={"Authorization":"Bearer author-token","Origin":"http://127.0.0.1:18443"})
                        self.assertEqual(get.status_code,406)
                self.assertEqual(seen,[('/v1/status','run_id=run-fixture&include_diagnostics=true&log_artifact_id=artifact-1&log_offset=4096','Bearer author-token','session-A')])
            asyncio.run(scenario())

    def test_real_coordinator_author_sessions_scope_replay_hold_and_isolation(self):
        from types import SimpleNamespace
        import workflow_coordinator
        import workflow_store
        from mcp import ClientSession
        from mcp.client.streamable_http import streamable_http_client, streamablehttp_client
        from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission

        async def run_test():
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); case=make_preflight_case(root)
                base=case.policy
                floors={'required_packs':sorted({p for packs in base['coverage'].values() for p in packs}),
                    'selectors':base['selector_requirements'],'review_roles':base['review_roles'],
                    'owner_exceptions':base['owner_exceptions'],'config_overrides':base['config_overrides'],
                    'review_envelope_limit':base['review_envelope_limit_bytes']}
                token='author-secret'; sessions={sid:{'id':sid,'location':{'directory':str(case.repo)},'agent':'orchestrate-test'} for sid in ('author-A','author-B')}
                slot=workflow_coordinator.AuthorWorktreeBinding('slot',token,'context',str(case.repo),floors,str(case.receipt),case.context.publication_receipt_sha256)
                verifier=workflow_coordinator.OpenCodeIdentityVerifier('local',session_lookup=lambda _,sid:sessions.get(sid),role_lookup=lambda *_:{'mode':'primary'})
                store=workflow_store.WorkflowStore(root/'state.sqlite')
                coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={token:slot},artifact_dir=root/'artifacts',handlers={},identity_verifier=verifier,reconcile_interval=30,admission_root=root/'admission')
                app=coordinator.app()
                def factory(headers=None,timeout=None,auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1:18443',headers={**(headers or {}),'Origin':'http://127.0.0.1:18443'})
                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18443/mcp/author',{'Authorization':'Bearer '+token},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            status=[]; runs=[]
                            for index,sid in enumerate(sessions):
                                ctx=json.loads((await client.call_tool('status',{'session_id':sid})).content[0].text)['author_context']
                                status.append(ctx['authenticated_author']['session_id'])
                                key='submit-'+sid
                                scope,request=prepare_dynamic_author_submission(ctx,base_policy=base,repo=case.repo,submit_key=key,criterion='criterion-'+sid,artifact_id='artifact_'+sid,artifact_path=root/(sid+'.evidence'),request_path=root/(sid+'.json'))
                                scopefile=root/(sid+'-scope.json'); scopefile.write_text(json.dumps(scope),encoding='utf-8')
                                one=json.loads((await client.call_tool('register_scope',{'scope_path':str(scopefile),'idempotency_key':'registration-'+sid,'session_id':sid})).content[0].text)
                                self.assertEqual(one['response']['status'],'REGISTERED',one)
                                args={'request_path':str(root/(sid+'.json')),'idempotency_key':key,'session_id':sid}
                                receipt=json.loads((await client.call_tool('submit',args)).content[0].text)
                                replay=json.loads((await client.call_tool('submit',args)).content[0].text)
                                self.assertEqual(receipt['response'],replay['response'])
                                runs.append(receipt['response']['run_id'])
                            self.assertEqual(status,['author-A','author-B'])
                            denied=json.loads((await client.call_tool('status',{'run_id':runs[1],'session_id':'author-A'})).content[0].text)
                            self.assertEqual(denied['code'],'FORBIDDEN')
                            (root/'admission').mkdir()
                            (root/'admission'/'gate-maintenance.json').write_text(json.dumps({'owner':'fixture','allowed_candidate_ids':[]}),encoding='utf-8')
                            db=store._connect(); before=db.execute('SELECT count(*) FROM runs').fetchone()[0]; db.close()
                            hold=json.loads((await client.call_tool('submit',{'request_path':str(root/'author-A.json'),'idempotency_key':'held-new','session_id':'author-A'})).content[0].text)
                            self.assertEqual(hold['code'],'INTAKE_HELD',hold)
                            db=store._connect(); after=db.execute('SELECT count(*) FROM runs').fetchone()[0]; db.close()
                            self.assertEqual(after,before)
                self.assertEqual(len(set(runs)),2)
        asyncio.run(run_test())

    def test_real_reviewer_route_rejects_wrong_session_and_task(self):
        import workflow_coordinator
        import workflow_review_transport
        from types import SimpleNamespace
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client
        import test_workflow_reviewer_api as fixture_module

        async def run_test():
            fixture=fixture_module.ReviewerAPITests(); fixture.setUp()
            try:
                transport=workflow_review_transport.ReviewDispatchBackend(fixture.planner,server='http://127.0.0.1:1234',
                    agents={'implementation':'workflow-reviewer-implementation'},request=lambda *args:fixture.fake_transport(*args))
                binding=workflow_coordinator.ReviewerBinding('reviewer',fixture.worktree,'implementation')
                coordinator=workflow_coordinator.ExplicitCoordinator(fixture.store,bindings={'author-token':object()},review_bindings={'review-token':binding},
                    review_results=fixture.api,review_pipeline=SimpleNamespace(transport=transport),artifact_dir=fixture.root,identity_verifier=lambda _:True)
                app=coordinator.app()
                def factory(headers=None,timeout=None,auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1:18444',headers=headers)
                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444/mcp/reviewer',{'Authorization':'Bearer review-token'},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            task=fixture.task
                            wrong_session=json.loads((await client.call_tool('status',{'task_id':task['task_id'],'session_id':'not-assigned'})).content[0].text)
                            self.assertEqual(wrong_session['code'],'FORBIDDEN')
                            wrong_task=json.loads((await client.call_tool('status',{'task_id':'not-a-task','session_id':task['assigned_session_id']})).content[0].text)
                            self.assertEqual(wrong_task['code'],'NOT_FOUND')
                            self.assertEqual('DISPATCHING',fixture.store.get_task(task['task_id'])['state'])
            finally:
                fixture.tearDown()
        asyncio.run(run_test())


if __name__ == "__main__": unittest.main()
