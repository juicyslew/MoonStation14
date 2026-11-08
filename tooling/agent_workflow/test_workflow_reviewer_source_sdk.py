import json
import hashlib
import tempfile
import unittest
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch
from urllib.parse import parse_qs, urlsplit

from workflow_coordinator_mcp import CoordinatorClient


class ReviewerSourceSDKTests(unittest.TestCase):
    def test_source_selectors_are_forwarded_and_validated(self):
        with tempfile.TemporaryDirectory() as directory:
            calls=[]
            def transport(*args):
                calls.append(args)
                return 200,b'{"ok":true}'
            client=CoordinatorClient('http://127.0.0.1:9000','token',Path(directory),transport=transport,profile='reviewer')
            args={'task_id':'task','session_id':'session','source_query':'Foo bar'}
            client.call('status',args)
            self.assertEqual(parse_qs(urlsplit(calls[-1][0]).query),{'task_id':['task'],'source_query':['Foo bar']})
            _,_,url,_,headers=client.prepare_call('status',{'task_id':'task','session_id':'session','source_path':'src/Foo.java','source_line':7})
            self.assertEqual(parse_qs(urlsplit(url).query),{'task_id':['task'],'source_path':['src/Foo.java'],'source_line':['7']})
            self.assertEqual(headers['X-MS14-Reviewer-Session'],'session')
            for bad,field in [({'source_query':'x','source_path':'a'},'source_query'),
                              ({'source_line':2},'source_line'),({'source_line':True,'source_path':'a'},'source_line')]:
                result=client.call('status',{'task_id':'task','session_id':'session',**bad})
                self.assertEqual((result['code'],result['field']),('INVALID_TOOL_ARGUMENTS',field))

    def test_source_status_has_larger_but_bounded_response_budget(self):
        args={'task_id':'task','source_path':'src/file','source_line':1}
        result={'source_access':{'page':{'content':'x'*12000}}}
        encoded=CoordinatorClient.encode_response('status',args,result)
        self.assertIn('source_access',encoded)
        self.assertLessEqual(len(encoded.encode()),65536)
        self.assertIn('response_too_large',CoordinatorClient.encode_response('status',args,{'content':'x'*70000}))
        self.assertIn('response_too_large',CoordinatorClient.encode_response('status',{'task_id':'task'},result))

    def test_official_http_sdk_reads_real_bound_source_page(self):
        import httpx
        import workflow_coordinator
        import workflow_review_transport
        import test_workflow_native_execution as native
        import test_workflow_reviewer_api as api_fixture
        from mcp import ClientSession
        from mcp.client.streamable_http import streamablehttp_client

        filename='unit_tool.py'
        source=''.join(f'# source line {index:04d} π '+('payload-'*14)+'\n' for index in range(1,501))
        original_case=native.make_preflight_case
        def case_with_source(*args,**kwargs):
            case=original_case(*args,**kwargs)
            (case.repo/filename).write_text(source,encoding='utf-8')
            return case

        async def exercise():
            fixture=api_fixture.ReviewerAPITests()
            original_coordinator=api_fixture.review_fixtures.ReviewPlanningTests.coordinator
            def coordinator_with_reviewer_main(helper,store,run_id,*args,**kwargs):
                planner=original_coordinator(helper,store,run_id,*args,**kwargs)
                reviewer_main=fixture.root/'reviewer-MAIN'; reviewer_main.mkdir(exist_ok=True)
                (reviewer_main/filename).write_text('REVIEWER COPY MUST NOT LEAK\n',encoding='utf-8')
                planner.reviewers['implementation']['worktree']=str(reviewer_main)
                return planner
            with patch.object(native,'make_preflight_case',side_effect=case_with_source), patch.object(api_fixture.review_fixtures.ReviewPlanningTests,'coordinator',coordinator_with_reviewer_main):
                fixture.setUp()
            try:
                reviewer_main=fixture.root/'reviewer-MAIN'; reviewer_main.mkdir(exist_ok=True)
                (reviewer_main/filename).write_text('REVIEWER COPY MUST NOT LEAK\n',encoding='utf-8')
                transport=workflow_review_transport.ReviewDispatchBackend(fixture.planner,server='http://127.0.0.1:1234',
                    agents={'implementation':'workflow-reviewer-implementation'},request=lambda *args:fixture.fake_transport(*args))
                binding=workflow_coordinator.ReviewerBinding('reviewer',str(reviewer_main),'implementation')
                coordinator=workflow_coordinator.ExplicitCoordinator(fixture.store,bindings={'author-token':object()},review_bindings={'review-token':binding},
                    review_results=fixture.api,artifact_dir=fixture.root,identity_verifier=lambda _:True)
                coordinator.review_pipeline=SimpleNamespace(transport=transport)
                app=coordinator.app()
                from workflow_coordinator_http_mcp import PROFILE_PATHS
                def factory(headers=None,timeout=None,auth=None):
                    return httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://127.0.0.1:18444',headers=headers)
                async with app.router.lifespan_context(app):
                    async with streamablehttp_client('http://127.0.0.1:18444'+PROFILE_PATHS['reviewer'],{'Authorization':'Bearer review-token'},httpx_client_factory=factory) as (r,w,_):
                        async with ClientSession(r,w) as client:
                            await client.initialize()
                            tools=(await client.list_tools()).tools
                            self.assertEqual({tool.name for tool in tools},{'claim_task','complete_review','report_error','status'})
                            self.assertEqual([tool.name for tool in tools].count('status'),1)
                            task=fixture.task; session=task['assigned_session_id']
                            query=json.loads((await client.call_tool('status',{'task_id':task['task_id'],'session_id':session,'source_query':filename})).content[0].text)
                            self.assertIn(filename,[row['path'] for row in query['source_access']['matches']])
                            page=json.loads((await client.call_tool('status',{'task_id':task['task_id'],'session_id':session,'source_path':filename,'source_line':1})).content[0].text)
                            source_page=page['source_access']
                            self.assertGreater(len(source_page['content'].encode()),4096)
                            self.assertLessEqual(len(json.dumps(page).encode()),65536)
                            import workflow_native_worker
                            request,_=workflow_native_worker.load_native_inputs(fixture.store,task['run_id'])
                            admitted_bytes=(Path(request['source_worktree'])/filename).read_bytes()
                            self.assertEqual(source_page['sha256'],hashlib.sha256(admitted_bytes).hexdigest())
                            self.assertEqual(source_page['content'],source[:len(source_page['content'])])
                            self.assertEqual(source_page['next_line'],source_page['start_line']+len(source_page['content'].splitlines()))
                            self.assertNotIn('REVIEWER COPY MUST NOT LEAK',source_page['content'])
                            self.assertEqual(source_page['input_digest'],fixture.task['input_digest'])
                            denied=json.loads((await client.call_tool('status',{'task_id':task['task_id'],'session_id':'foreign','source_path':filename})).content[0].text)
                            self.assertEqual(denied['code'],'FORBIDDEN')
                            missing=json.loads((await client.call_tool('status',{'task_id':'unknown','session_id':session,'source_path':filename})).content[0].text)
                            self.assertEqual(missing['code'],'NOT_FOUND')
                            for path in ('../outside','C:/absolute'):
                                rejected=json.loads((await client.call_tool('status',{'task_id':task['task_id'],'session_id':session,'source_path':path})).content[0].text)
                                self.assertFalse(rejected.get('ok',True))
                                self.assertNotIn(source[:50],json.dumps(rejected))
            finally:
                fixture.tearDown()
        import asyncio
        asyncio.run(exercise())


if __name__=='__main__': unittest.main()
