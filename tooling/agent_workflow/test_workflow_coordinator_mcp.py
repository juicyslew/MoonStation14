import json
import asyncio
import os
import socket
import subprocess
import sys
import threading
import time
import tempfile
import unittest
from contextlib import contextmanager
from pathlib import Path

from workflow_coordinator_mcp import CoordinatorClient


@contextmanager
def _stdio_coordinator_fixture(coordinator, root, repo, token, *, author_mode=False):
    import uvicorn
    from mcp.client.stdio import StdioServerParameters
    sock=socket.socket(); sock.bind(('127.0.0.1',0)); sock.listen()
    endpoint=f'http://127.0.0.1:{sock.getsockname()[1]}'
    server=uvicorn.Server(uvicorn.Config(coordinator.app(),log_level='error',lifespan='on'))
    thread=threading.Thread(target=server.run,kwargs={'sockets':[sock]},daemon=True); thread.start()
    token_file=root/'token'; token_file.write_text(token,encoding='utf-8')
    script=Path(__file__).with_name('workflow_coordinator_mcp.py').resolve()
    env=os.environ.copy(); env['PYTHONPATH']=str(script.parent)+os.pathsep+env.get('PYTHONPATH','')
    env['PYTHONDONTWRITEBYTECODE']='1'
    args=[str(script),'--repo',str(repo),'--endpoint',endpoint,'--token-file',str(token_file)]
    if author_mode: args += ['--author-mode','worktree']
    try:
        deadline=time.monotonic()+5
        while not server.started and time.monotonic()<deadline: time.sleep(.01)
        if not server.started: raise AssertionError('coordinator fixture failed to start')
        yield StdioServerParameters(command=sys.executable,args=args,env=env)
    finally:
        server.should_exit=True; thread.join(timeout=5); sock.close()
        if thread.is_alive(): raise AssertionError('owned coordinator fixture did not stop')


class CoordinatorMCPTests(unittest.TestCase):
    def test_response_budget_only_expands_for_author_diagnostics(self):
        from workflow_mcp import _response
        large = {'diagnostics': 'x' * 8000}
        self.assertIn('response_too_large', _response(large))
        self.assertIn('diagnostics', CoordinatorClient.encode_response('status', {'run_id':'r','include_diagnostics':True}, large))
        self.assertIn('response_too_large', CoordinatorClient.encode_response('status', {'run_id':'r'}, large))
        self.assertIn('response_too_large', CoordinatorClient.encode_response('status', {'run_id':'r','include_diagnostics':True}, {'diagnostics':'x'*70000}))
        self.assertIn('response_too_large', CoordinatorClient.encode_response('status', {'run_id':'r','include_diagnostics':True}, large) if False else CoordinatorClient.encode_response('status', {'task_id':'t'}, large))

    def test_overflow_fallbacks_retain_operation_recovery_context(self):
        huge={'payload':'x'*8000}; cases=[('submit',{'idempotency_key':'submit-k'},'CHECK_SUBMISSION_STATUS','submit-k'),('register_scope',{'idempotency_key':'scope-k'},'CHECK_SCOPE_STATUS','scope-k'),('acknowledge_result',{'event_id':'event-1','result_id':'result-1','run_id':'run-1','command_id':'cmd'},'RETRY_SAME_ACKNOWLEDGEMENT','event-1'),('complete_review',{'task_id':'task-1'},'CHECK_TASK_STATUS','task-1'),('status',{'task_id':'task-1'},'REPORT_ERROR','task-1'),('status',{'run_id':'run-1','idempotency_key':'key-1','include_diagnostics':True},'RETRY_STATUS','run-1'),('status',{'run_id':'run-1'},'WAIT_FOR_RECOVERY','run-1')]
        for name,args,action,retained in cases:
            payload={'payload':'x'*70000} if args.get('include_diagnostics') else huge
            encoded=CoordinatorClient.encode_response(name,args,payload); result=json.loads(encoded)
            self.assertLessEqual(len(encoded.encode()),4096)
            self.assertEqual((result['code'],result['next_action']),('response_too_large',action))
            self.assertIn(retained,str(result)); self.assertTrue(result.get('recovery'))

    def test_invalid_success_receipt_and_nonobject_responses_are_uncertain(self):
        result=CoordinatorClient.interpret_response('submit',{'idempotency_key':'same'},200,b'{"response":{}}')
        self.assertEqual((result['code'],result['next_action'],result['idempotency_key']),('invalid_coordinator_response','CHECK_SUBMISSION_STATUS','same'))
        for body in (b'[]',b'{"error":"bad"}'):
            result=CoordinatorClient.interpret_response('submit',{'idempotency_key':'same'},502,body)
            self.assertEqual(result['code'],'invalid_coordinator_response'); self.assertNotIn('bad',json.dumps(result))

    def test_long_original_ids_never_evict_overflow_recovery_guidance(self):
        identifier='🚀'*2000
        cases=[('acknowledge_result',{'event_id':identifier,'result_id':identifier,'run_id':identifier,'command_id':identifier},'RETRY_SAME_ACKNOWLEDGEMENT'),
               ('submit',{'idempotency_key':identifier},'CHECK_SUBMISSION_STATUS'),
               ('status',{'run_id':identifier,'idempotency_key':identifier,'include_diagnostics':True},'RETRY_STATUS')]
        for name,args,action in cases:
            encoded=CoordinatorClient.encode_response(name,args,{'payload':'x'*80000})
            self.assertLessEqual(len(encoded.encode('utf-8')),4096)
            result=json.loads(encoded)
            self.assertEqual((result['code'],result['next_action']),('response_too_large',action))
            self.assertIn('recovery',result); self.assertIn('original',result['recovery'])
            self.assertNotIn(identifier,encoded)

    def test_backend_response_too_large_error_with_action_is_not_rewritten(self):
        backend={'ok':False,'code':'response_too_large','message':'service bound','next_action':'CHECK_SUBMISSION_STATUS','recovery':'check this key','idempotency_key':'valid-key'}
        encoded=CoordinatorClient.encode_response('submit',{'idempotency_key':'valid-key'},backend)
        result=json.loads(encoded)
        self.assertEqual(result,backend)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)
        (self.repo / "request.json").write_text('{"title":"prepared"}', encoding="utf-8")
        self.calls = []
        def transport(url, data, headers, timeout):
            self.calls.append((url, data, headers, timeout))
            return 200, b'{"response":{"status":"ACCEPTED","run_id":"run-fixture"}}'
        self.client = CoordinatorClient("http://127.0.0.1:9000", "configured", self.repo, transport=transport)

    def tearDown(self): self.temp.cleanup()

    def test_loopback_only(self):
        for endpoint in ("https://localhost", "http://example.org", "http://localhost/path", "http://user@localhost"):
            with self.assertRaises(ValueError): CoordinatorClient(endpoint, "x", self.repo)

    def test_catalog_and_unknown_args(self):
        self.assertEqual({x["name"] for x in self.client.tools()}, {"submit", "status", "acknowledge_result", "register_scope", "scope_status"})
        reviewer=CoordinatorClient("http://127.0.0.1:9000","reviewer",self.repo,transport=self.client.transport,profile='reviewer')
        self.assertFalse({'register_scope','scope_status'} & {x['name'] for x in reviewer.tools()})
        self.assertEqual(self.client.call("submit", {"request_path":"request.json", "idempotency_key":"k", "extra":1})['code'],'INVALID_TOOL_ARGUMENTS')

    def test_reviewer_calls_strip_session_from_protocol_and_preserve_files(self):
        reviewer=CoordinatorClient('http://127.0.0.1:9000','configured',self.repo,transport=self.client.transport,profile='reviewer')
        result_file=self.repo/'result.json'; result_file.write_bytes(b'{"review":"unchanged"}')
        error_file=self.repo/'error.json'; error_file.write_bytes(b'{"error":"unchanged"}')
        calls=[('claim_task',{'contract_version':1,'command_id':'c','task_id':'t','run_id':'r','evaluation_generation':1,'assignment_generation':1,'input_digest':'d','session_id':'sid'}),
          ('complete_review',{'result_path':str(result_file),'session_id':'sid'}),
          ('report_error',{'error_path':str(error_file),'session_id':'sid'}),('status',{'task_id':'t','session_id':'sid'})]
        original=(result_file.read_bytes(),error_file.read_bytes())
        for name,args in calls:
            reviewer.call(name,args)
            url,data,headers,_=self.calls[-1]
            self.assertEqual(headers['X-MS14-Reviewer-Session'],'sid')
            self.assertNotIn('session_id',url)
            if name=='status':
                self.assertNotIn('session_id',url)
            elif name in ('claim_task','complete_review','report_error'):
                self.assertNotIn('session_id',json.loads(data) if name=='claim_task' else data.decode())
        self.assertEqual(original,(result_file.read_bytes(),error_file.read_bytes()))
        for sid in (None,True,'','x'*2001):
            args=dict(calls[0][1]); args['session_id']=sid
            result=reviewer.call('claim_task',args)
            self.assertEqual((result['code'],result['field']),('INVALID_TOOL_ARGUMENTS','session_id'))
        self.assertEqual(len(self.calls),4)

    def test_missing_review_result_is_local_and_same_call_succeeds_after_file_is_ready(self):
        reviewer=CoordinatorClient('http://127.0.0.1:9000','configured',self.repo,transport=self.client.transport,profile='reviewer')
        result_file=self.repo/'canonical-result.json'
        args={'result_path':str(result_file),'session_id':'assigned-session'}
        missing=reviewer.call('complete_review',args)
        self.assertEqual((missing['code'],missing['field']),('INPUT_FILE_NOT_FOUND','result_path'))
        self.assertIn('same result_path and session_id',missing['recovery'])
        self.assertEqual(self.calls,[])

        payload=b'{"verdict":"PASS","evidence_refs":["receipt"]}'
        result_file.write_bytes(payload)
        self.assertEqual(result_file.read_bytes(),payload)
        accepted=reviewer.call('complete_review',args)
        self.assertEqual(accepted['response']['status'],'ACCEPTED')
        self.assertEqual(len(self.calls),1)
        url,data,headers,_=self.calls[0]
        self.assertEqual(url,'http://127.0.0.1:9000/v1/review/complete')
        self.assertEqual(data,payload)
        self.assertEqual(headers['X-MS14-Reviewer-Session'],'assigned-session')

    def test_submit_posts_exact_envelope(self):
        result = self.client.call("submit", {"request_path":"request.json", "idempotency_key":"same"})
        self.assertEqual(result["response"]["status"], "ACCEPTED")
        payload = json.loads(self.calls[0][1])
        self.assertEqual(payload, {"idempotency_key":"same", "request":'{"title":"prepared"}'})
        self.assertEqual(self.calls[0][2]["Authorization"], "Bearer configured")

    def test_ack_tool_posts_feedback_ack_fields_and_accepts_integer_generation(self):
        args={"contract_version":1,"command_id":"ack-command","event_id":"event-x","run_id":"run-x",
              "evaluation_generation":2,"result_id":"result-x","result_digest":"d"*64}
        result=self.client.call("acknowledge_result",args)
        self.assertEqual(result["response"]["status"],"ACCEPTED")
        self.assertEqual(self.calls[-1][0],"http://127.0.0.1:9000/v1/acknowledge-result")
        self.assertEqual(json.loads(self.calls[-1][1]),args)
        self.assertNotIn("author_session_id",json.loads(self.calls[-1][1]))

    def test_status_without_selector_has_no_network_effect(self):
        result=self.client.call("status", {})
        self.assertEqual((result['code'],result['field']),('INVALID_TOOL_ARGUMENTS','run_id'))
        self.assertEqual(self.calls, [])

    def test_status_diagnostics_boolean_is_strict_and_serialized(self):
        self.client.call('status',{'run_id':'run-1','include_diagnostics':True})
        self.assertIn('include_diagnostics=true',self.calls[-1][0])
        for value in ('true',1,None):
            with self.assertRaises(ValueError): self.client.prepare_call('status',{'run_id':'run-1','include_diagnostics':value})
        with self.assertRaises(ValueError): self.client.prepare_call('status',{'run_id':True})

    def test_unavailable_preserves_key_without_retry(self):
        def failed(*args): raise OSError("offline")
        self.client.transport = failed
        result = self.client.call("submit", {"request_path":"request.json", "idempotency_key":"same"})
        self.assertEqual(result["idempotency_key"], "same")
        self.assertEqual(result["next_action"], "CHECK_SUBMISSION_STATUS")

    def test_oversized_request_rejected_before_network(self):
        (self.repo / "big").write_bytes(b"x" * (1024 * 1024 + 1))
        self.assertEqual(self.client.call("submit", {"request_path":"big", "idempotency_key":"k"})['code'],'PAYLOAD_TOO_LARGE')
        self.assertEqual(self.calls, [])

    def test_scope_tools_read_strict_payload_and_use_separate_registration_key(self):
        (self.repo/'scope.json').write_text('{"contract_version":1,"policy":{},"artifacts":[],"submission_idempotency_key":"submit-key"}',encoding='utf-8')
        result=self.client.call('register_scope',{'scope_path':'scope.json','idempotency_key':'registration-key'})
        self.assertEqual(result['response']['status'],'ACCEPTED')
        self.assertEqual(self.calls[-1][0],'http://127.0.0.1:9000/v1/scope/register')
        self.assertEqual(json.loads(self.calls[-1][1]),{'idempotency_key':'registration-key','scope':{'contract_version':1,'policy':{},'artifacts':[],'submission_idempotency_key':'submit-key'}})
        self.client.call('scope_status',{'idempotency_key':'registration-key'})
        self.assertEqual(self.calls[-1][0],'http://127.0.0.1:9000/v1/scope/status?idempotency_key=registration-key')

    def test_bad_scope_json_and_uncertain_registration_are_safe(self):
        (self.repo/'duplicate.json').write_text('{"x":1,"x":2}',encoding='utf-8')
        (self.repo/'scope.json').write_text('{"contract_version":1}',encoding='utf-8')
        self.assertEqual(self.client.call('register_scope',{'scope_path':'duplicate.json','idempotency_key':'same'})['code'],'INVALID_JSON')
        self.assertEqual(self.calls,[])
        self.client.transport=lambda *args: (_ for _ in ()).throw(OSError('offline'))
        result=self.client.call('register_scope',{'scope_path':'scope.json','idempotency_key':'same'})
        self.assertEqual(result['next_action'],'CHECK_SCOPE_STATUS')
        self.assertEqual(result['idempotency_key'],'same')

    def test_typed_backend_fields_and_malformed_body_recovery(self):
        result=CoordinatorClient.interpret_response('submit',{'idempotency_key':'k'},400,b'{"error":{"code":"CONFLICT","message":"safe","next_action":"EDIT","field":"request_path","recovery":"fix input","secret":"hidden"}}')
        self.assertEqual({k:result[k] for k in ('code','message','next_action','field','recovery','idempotency_key')},{'code':'CONFLICT','message':'safe','next_action':'EDIT','field':'request_path','recovery':'fix input','idempotency_key':'k'})
        self.assertNotIn('secret',result)
        for name,args,action in [('submit',{'idempotency_key':'k'},'CHECK_SUBMISSION_STATUS'),('register_scope',{'idempotency_key':'s'},'CHECK_SCOPE_STATUS'),('claim_task',{'task_id':'t'},'CHECK_TASK_STATUS'),('complete_review',{'task_id':'t'},'CHECK_TASK_STATUS'),('report_error',{'task_id':'t'},'CHECK_TASK_STATUS'),('acknowledge_result',{'event_id':'e','result_id':'r','run_id':'r','command_id':'c'},'RETRY_SAME_ACKNOWLEDGEMENT'),('status',{'run_id':'r'},'RETRY_STATUS')]:
            self.assertEqual(CoordinatorClient.transport_failure(name,args,None)['next_action'],action)
        malformed=CoordinatorClient.interpret_response('submit',{'idempotency_key':'same'},502,b'<html>do not echo')
        self.assertEqual(malformed['next_action'],'CHECK_SUBMISSION_STATUS'); self.assertNotIn('html',json.dumps(malformed))

    def test_local_prepare_errors_are_not_sent_and_retain_retry_context(self):
        for payload,code in [(b'\xff','INVALID_JSON'),(b'{"a":1,"a":2}','INVALID_JSON'),(b'[]','INVALID_JSON')]:
            (self.repo/'bad.json').write_bytes(payload)
            result=self.client.call('submit',{'request_path':'bad.json','idempotency_key':'same'})
            self.assertEqual((result['code'],result['field'],result['idempotency_key']),(code,'request_path','same'))
        self.assertEqual(self.calls,[])
        missing=self.client.call('submit',{'request_path':'absent.json','idempotency_key':'same'})
        self.assertEqual(missing['code'],'INPUT_FILE_NOT_FOUND')
        self.assertEqual(self.calls,[])

    def test_direct_arguments_report_concrete_correctable_fields(self):
        cases=[('submit',{'request_path':'request.json' },'idempotency_key'),
            ('submit',{'request_path':'request.json','idempotency_key':'k','oops':1},'oops'),
            ('status',{'run_id':'r','include_diagnostics':'yes'},'include_diagnostics'),
            ('acknowledge_result',{'contract_version':1,'command_id':'c','event_id':'e','run_id':'r','evaluation_generation':-1,'result_id':'x','result_digest':'d'},'evaluation_generation')]
        for tool,args,field in cases:
            result=self.client.call(tool,args)
            self.assertEqual(result['code'],'INVALID_TOOL_ARGUMENTS'); self.assertEqual(result['field'],field)
            self.assertTrue(result.get('recovery')); self.assertEqual(self.calls,[])
        unknown=self.client.call('foreign_tool',{})
        self.assertEqual(unknown['code'],'UNKNOWN_TOOL')
        self.assertIn('submit',unknown['message']); self.assertNotIn('foreign_tool',unknown['message'])

    def test_shared_file_reader_rejects_strict_json_errors_for_all_input_tools(self):
        reviewer=CoordinatorClient('http://127.0.0.1','configured',self.repo,transport=self.client.transport,profile='reviewer')
        bad_cases=[(b'{"x":1,"x":2}','duplicate'),(b'{"x":NaN}','nonfinite'),(b'[]','root')]
        for raw,label in bad_cases:
            (self.repo/'invalid.json').write_bytes(raw)
            cases=[(self.client,'submit',{'request_path':'invalid.json','idempotency_key':'k'},'request_path'),
                   (self.client,'register_scope',{'scope_path':'invalid.json','idempotency_key':'k'},'scope_path'),
                   (reviewer,'complete_review',{'result_path':'invalid.json','session_id':'chat'},'result_path')]
            for client,name,args,field in cases:
                result=client.call(name,args)
                self.assertEqual((result['code'],result['field']),('INVALID_JSON',field),label)
                self.assertNotIn('NaN',json.dumps(result)); self.assertEqual(self.calls,[])
        (self.repo/'directory').mkdir()
        for path,code in [('missing','INPUT_FILE_NOT_FOUND'),('directory','INPUT_FILE_UNREADABLE')]:
            result=self.client.call('submit',{'request_path':path,'idempotency_key':'k'})
            self.assertEqual((result['code'],result['field']),(code,'request_path'))
        sentinel=b'PRIVATE_SENTINEL'
        (self.repo/'invalid.json').write_bytes(b'{"x":"'+sentinel+b'\xff"}')
        result=self.client.call('submit',{'request_path':'invalid.json','idempotency_key':'k'})
        self.assertEqual(result['code'],'INVALID_JSON'); self.assertNotIn(sentinel.decode(),json.dumps(result))

    def test_official_sdk_stdio_real_coordinator_happy_flow(self):
        from mcp import ClientSession
        from mcp.client.stdio import StdioServerParameters, stdio_client
        import workflow_coordinator
        import workflow_scope
        import workflow_store
        from preflight_test_support import make_preflight_case

        root = self.repo / "fixture"
        root.mkdir()
        case = make_preflight_case(root)
        store = workflow_store.WorkflowStore(root / "state.sqlite")
        effects = set()
        class Handler:
            def dispatch(inner, event_id, payload): effects.add(event_id)
            def observe(inner, event_id, payload): return "APPLIED" if event_id in effects else "ABSENT"
        binding = workflow_coordinator.CoordinatorBinding(case.caller, case.context)
        coordinator = workflow_coordinator.ExplicitCoordinator(store, bindings={"secret": binding}, artifact_dir=root / "artifacts", handlers={"TASK_READY": Handler()}, identity_verifier=lambda context: True, reconcile_interval=30)
        request_file = root / "prepared-request.json"; request_file.write_text(json.dumps(case.request), encoding="utf-8")
        scope_file = root / "prepared-scope.json"
        scope_file.write_text(json.dumps({'contract_version':1,'policy':case.policy,'artifacts':[case.artifact],
                                          'submission_idempotency_key':case.key}),encoding='utf-8')
        async def scenario():
            async with asyncio.timeout(30):
                async with stdio_client(params) as (reader, writer):
                    async with ClientSession(reader, writer) as session:
                        await session.initialize()
                        catalog = await session.list_tools()
                        self.assertEqual({tool.name for tool in catalog.tools}, {"submit", "status", "acknowledge_result", "register_scope", "scope_status"})
                        registered=json.loads((await session.call_tool('register_scope',{'scope_path':str(scope_file),'idempotency_key':'scope-registration'})).content[0].text)
                        self.assertEqual(registered['response']['status'],'REGISTERED')
                        status=json.loads((await session.call_tool('scope_status',{'idempotency_key':'scope-registration'})).content[0].text)
                        self.assertEqual(status['response'],registered['response'])
                        args = {"request_path": str(request_file), "idempotency_key": case.key}
                        one = json.loads((await session.call_tool("submit", args)).content[0].text)
                        two = json.loads((await session.call_tool("submit", args)).content[0].text)
                        self.assertEqual(one["response"], two["response"])
                        self.assertEqual(one["response"]["status"], "ACCEPTED")
                        state = json.loads((await session.call_tool("status", {"idempotency_key": case.key})).content[0].text)
                        self.assertEqual(state["run"]["run_id"], one["response"]["run_id"])
                        self.assertEqual(state["attempt"]["phase"], "QUEUED")
                        self.assertIsNone(state["attempt"]["result"])
        with _stdio_coordinator_fixture(coordinator,root,case.repo,'secret') as params:
            asyncio.run(scenario())
            self.assertEqual(store.get_submission(case.caller, case.key)["status"], "ACCEPTED")
            self.assertLessEqual(len(effects), 1)
            self.assertEqual(len(list((root / "artifacts").glob("**/manifest*.json"))), 0)  # no fabricated result artifacts

    def test_official_sdk_stdio_worktree_author_sessions(self):
        """Exercise worktree-mode SDK identity over stdio and the real loopback app."""
        import workflow_coordinator
        import workflow_scope
        import workflow_store
        from mcp import ClientSession
        from mcp.client.stdio import StdioServerParameters, stdio_client
        from preflight_test_support import make_preflight_case, prepare_dynamic_author_submission

        root=self.repo/'dynamic-fixture'; root.mkdir()
        case=make_preflight_case(root)
        base=case.policy
        floors={'required_packs':sorted({p for packs in base['coverage'].values() for p in packs}),
            'selectors':base['selector_requirements'],'review_roles':base['review_roles'],
            'owner_exceptions':base['owner_exceptions'],'config_overrides':base['config_overrides'],
            'review_envelope_limit':base['review_envelope_limit_bytes']}
        token='fixture-token'
        slot=workflow_coordinator.AuthorWorktreeBinding('slot',token,'context',str(case.repo),floors,
            str(case.receipt),case.context.publication_receipt_sha256)
        sessions={sid:{'id':sid,'location':{'directory':str(case.repo)},'agent':'orchestrate-ms14'} for sid in ('chat-a','chat-b')}
        verifier=workflow_coordinator.OpenCodeIdentityVerifier('loopback',session_lookup=lambda server,sid:sessions.get(sid),
            role_lookup=lambda server,agent,directory:{'mode':'primary'})
        self.assertFalse(verifier(workflow_scope.worktree_author_context(repo=case.repo,authenticated_author={'session_id':'legacy-shape','worktree':str(case.repo),'role':'author'},floors=floors,publication_receipt_path=str(case.receipt),publication_receipt_sha256=case.context.publication_receipt_sha256)))
        store=workflow_store.WorkflowStore(root/'state.sqlite')
        coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={},author_worktrees={token:slot},
            artifact_dir=root/'artifacts',handlers={},identity_verifier=verifier,reconcile_interval=30)
        async def scenario():
            async with asyncio.timeout(30):
                async with stdio_client(params) as (reader,writer):
                    async with ClientSession(reader,writer) as session:
                        await session.initialize()
                        catalog=(await session.list_tools()).tools
                        names={tool.name for tool in catalog}
                        self.assertEqual(names,{'submit','status','acknowledge_result','register_scope','scope_status'})
                        for tool in catalog: self.assertIn('session_id',tool.inputSchema['required'],tool.name)
                        runs=[]; submit_key='shared-submit-key'
                        for sid in sessions:
                            result=json.loads((await session.call_tool('status',{'session_id':sid})).content[0].text)
                            self.assertIsNone(result['run'])
                            context=result['author_context']; self.assertEqual(context['authenticated_author']['session_id'],sid)
                            criterion='criterion-'+sid; artifact_id='artifact_'+sid
                            scope,request=prepare_dynamic_author_submission(context,base_policy=base,repo=case.repo,
                                submit_key=submit_key,criterion=criterion,artifact_id=artifact_id,
                                artifact_path=root/(sid+'.evidence'),request_path=root/(sid+'-request.json'))
                            scope_file=root/(sid+'-scope.json'); scope_file.write_text(json.dumps(scope),encoding='utf-8')
                            reg_args={'scope_path':str(scope_file),'idempotency_key':'shared-registration-key','session_id':sid}
                            registered=json.loads((await session.call_tool('register_scope',reg_args)).content[0].text)
                            self.assertEqual(registered['response']['status'],'REGISTERED',json.dumps(registered)[:3000])
                            scope_state=json.loads((await session.call_tool('scope_status',{'idempotency_key':'shared-registration-key','session_id':sid})).content[0].text)
                            self.assertEqual(scope_state['response'],registered['response'],json.dumps(scope_state)[:3000])
                            args={'request_path':str(root/(sid+'-request.json')),'idempotency_key':submit_key,'session_id':sid}
                            one=json.loads((await session.call_tool('submit',args)).content[0].text)
                            two=json.loads((await session.call_tool('submit',args)).content[0].text)
                            self.assertEqual(one['response'],two['response'],json.dumps({'first':one,'retry':two})[:3000])
                            self.assertEqual(one['response']['status'],'ACCEPTED',json.dumps(one)[:3000])
                            runs.append(one['response']['run_id'])
                            own=json.loads((await session.call_tool('status',{'run_id':runs[-1],'session_id':sid})).content[0].text)
                            self.assertEqual(own['attempt']['phase'],'QUEUED')
                        self.assertEqual(len(set(runs)),2)
                        foreign=await session.call_tool('status',{'run_id':runs[1],'session_id':'chat-a'})
                        foreign_result=json.loads(foreign.content[0].text)
                        self.assertFalse(foreign_result['ok'],json.dumps(foreign_result)[:3000])
                        self.assertEqual(foreign_result['code'],'FORBIDDEN')
                        unknown=await session.call_tool('status',{'session_id':'unknown-session'})
                        unknown_result=json.loads(unknown.content[0].text)
                        self.assertFalse(unknown_result['ok'],json.dumps(unknown_result)[:3000])
                        self.assertEqual(unknown_result['code'],'IDENTITY_UNAVAILABLE')
                        missing=await session.call_tool('status',{})
                        self.assertTrue(missing.isError)
        with _stdio_coordinator_fixture(coordinator,root,case.repo,token,author_mode=True) as params:
            asyncio.run(scenario())


if __name__ == "__main__": unittest.main()
