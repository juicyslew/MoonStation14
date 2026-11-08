import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
import asyncio
import json
import os
import subprocess
import sys
import threading
import time
from unittest import mock

from tooling.agent_workflow import workflow_mcp as mcp


def _main_runtime():
    source=Path(__file__).with_name("workflow_runtime.py")
    spec=__import__("importlib.util",fromlist=["spec_from_file_location"]).spec_from_file_location("workflow_mcp_main_runtime_fixture",source)
    module=__import__("importlib.util",fromlist=["module_from_spec"]).module_from_spec(spec)
    sys.modules[spec.name]=module; spec.loader.exec_module(module)
    return module


class Runtime:
    def queue(self, args): return Path(args.repo)/"exchange"
    def validid(self, value):
        if not isinstance(value,str) or not value: raise ValueError("bad id")
        return value
    def readjson(self, path): raise AssertionError("unexpected read")


class Dispatch:
    def _session(self, server, sid): return {"id":sid,"agent":"orchestrate-opus","directory":str(self.repo)}
    def _role(self, server, role, repo): return {"name":role,"mode":"primary"}


class MCPFacadeTests(unittest.TestCase):
    def facade(self, profile):
        with tempfile.TemporaryDirectory() as directory:
            runtime=Runtime(); runtime.queue=lambda args: Path(directory)/"exchange"
            dispatch=Dispatch(); dispatch.repo=Path(directory)
            yield mcp.WorkflowMCP(directory,profile,workflow=runtime,services=(dispatch,SimpleNamespace()))

    def test_profiles_publish_only_permitted_catalog(self):
        author=next(self.facade("author")); lead=next(self.facade("lead"))
        self.assertEqual({x["name"] for x in author.tools()},{"submit","status"})
        self.assertEqual({x["name"] for x in lead.tools()},{"status","validate","review","finish"})
        self.assertTrue(all(x["inputSchema"]["additionalProperties"] is False for x in author.tools()+lead.tools()))

    def test_schema_rejects_unknown_and_bad_fields(self):
        facade=next(self.facade("author"))
        for args in ({"candidate_id":"x","timeout":2},{"candidate_id":2}):
            with self.assertRaises(ValueError): facade.call("status",args)

    def test_bounded_response(self):
        self.assertLessEqual(len(mcp._response({"payload":"x"*5000}).encode()),mcp.MAX_RESPONSE)

    def test_capture_lock_wait_is_bounded_and_never_runs_queued_mutation(self):
        entered=threading.Event(); release=threading.Event(); called=[]
        def owner():
            with mcp._STDOUT_LOCK:
                entered.set(); release.wait(2)
        thread=threading.Thread(target=owner); thread.start(); self.assertTrue(entered.wait(1))
        try:
            with mock.patch.object(mcp,'CAPTURE_LOCK_SECONDS',.01):
                with self.assertRaises(mcp.CaptureBusyError): mcp._capture(lambda:called.append(True))
            self.assertEqual(called,[])
        finally:
            release.set(); thread.join(2)

    def test_submit_requires_deadline_runtime_before_author_api_or_mutation(self):
        runtime=Runtime(); runtime.cmd_submit=mock.Mock()
        with tempfile.TemporaryDirectory() as directory:
            dispatch=Dispatch(); dispatch.repo=Path(directory)
            identity=mock.Mock(side_effect=AssertionError('author API must not run without submit deadline support'))
            facade=mcp.WorkflowMCP(directory,'author',queue_root=Path(directory)/'exchange',workflow=runtime,
                identity=identity,services=(dispatch,SimpleNamespace()))
            with self.assertRaises(AttributeError):
                facade.call('submit',{'candidate_id':'x','sprint_id':'s','session_id':'sid','plan_path':'plan.json',
                    'build_identity':'fixture','unverified':'','purpose':'readiness'})
            identity.assert_not_called(); runtime.cmd_submit.assert_not_called()

    def test_submit_expired_deadline_does_not_create_or_admit_candidate(self):
        w=_main_runtime()
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp); repo=base/'author'; repo.mkdir()
            subprocess.run(['git','init','-q','--initial-branch=main',str(repo)],check=True)
            subprocess.run(['git','-C',str(repo),'config','user.email','fixture@example.invalid'],check=True)
            subprocess.run(['git','-C',str(repo),'config','user.name','Fixture'],check=True)
            (repo/'seed').write_text('seed'); (repo/'.gitignore').write_text('build/\n')
            subprocess.run(['git','-C',str(repo),'add','.'],check=True); subprocess.run(['git','-C',str(repo),'commit','-qm','base'],check=True)
            (repo/'fixture.txt').write_text('fixture')
            (repo/'plan.json').write_text(json.dumps({'title':'fixture','kind':'bugfix','acceptance_criteria':['works'],'fixture':'fixture.txt','manual_steps':['inspect'],'checks':[{'argv':['python','-c','pass'],'rationale':'fixture','covers':['works']}]}))
            exchange=base/'exchange'; exchange.mkdir(); (exchange/'candidates').mkdir()
            class API:
                def _session(self,server,sid): return {'id':sid,'agent':'orchestrate-test','directory':str(repo)}
                def _role(self,server,role,directory): return {'mode':'primary'}
            class Dispatch(API):
                def request_gate(self,*args,**kwargs): self.admitted=True
                admitted=False
            dispatch=Dispatch(); facade=mcp.WorkflowMCP(repo,'author',queue_root=exchange,workflow=w,identity=lambda sid:dispatch._session(None,sid),services=(dispatch,SimpleNamespace()))
            args={'candidate_id':'never-late','sprint_id':'fixture','session_id':'sid','plan_path':'plan.json','build_identity':'fixture','unverified':'','purpose':'readiness'}
            with mock.patch.object(mcp,'SUBMIT_DEADLINE_SECONDS',0):
                with self.assertRaises(w.OperationDeadlineExceeded): facade.call('submit',args)
            self.assertFalse((exchange/'candidates'/'never-late.json').exists())
            self.assertFalse(dispatch.admitted)

    def test_dispatch_input_and_transport_errors_are_distinguished(self):
        class DispatchError(ValueError): pass
        class DispatchInputError(DispatchError): pass
        errors=SimpleNamespace(DispatchError=DispatchError,DispatchInputError=DispatchInputError)
        input_result=mcp._error_payload(DispatchInputError("candidate invalid"),errors,SimpleNamespace())
        transport=mcp._error_payload(DispatchError("OpenCode API unavailable"),errors,SimpleNamespace())
        self.assertEqual((input_result["code"],input_result["retryable"]),("invalid_request",False))
        self.assertEqual((transport["code"],transport["retryable"]),("workflow_unavailable",True))
        self.assertIn("existing durable record",transport["next"])

    def test_deadline_and_git_cleanup_errors_are_actionable_and_distinct(self):
        workflow=_main_runtime()
        deadline=mcp._error_payload(workflow.OperationDeadlineExceeded('expired'),SimpleNamespace(),workflow)
        verified=mcp._error_payload(workflow.GitCommandTimeout(20,123,True),SimpleNamespace(),workflow)
        cleanup=mcp._error_payload(workflow.GitCommandTimeout(20,123,False),SimpleNamespace(),workflow)
        self.assertEqual(deadline['code'],'submit_deadline_exceeded')
        self.assertIn('same candidate ID',deadline['next'])
        self.assertEqual((verified['code'],verified['retryable']),('git_timeout',True))
        self.assertEqual((cleanup['code'],cleanup['retryable']),('git_cleanup_unverified',False))

    def test_submit_deadline_expiring_after_persist_blocks_gate_intent(self):
        w=_main_runtime()
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp); repo=base/'author'; repo.mkdir()
            subprocess.run(['git','init','-q','--initial-branch=main',str(repo)],check=True)
            subprocess.run(['git','-C',str(repo),'config','user.email','fixture@example.invalid'],check=True)
            subprocess.run(['git','-C',str(repo),'config','user.name','Fixture'],check=True)
            (repo/'seed').write_text('seed'); (repo/'.gitignore').write_text('build/\n')
            subprocess.run(['git','-C',str(repo),'add','.'],check=True); subprocess.run(['git','-C',str(repo),'commit','-qm','base'],check=True)
            (repo/'fixture.txt').write_text('fixture')
            (repo/'plan.json').write_text(json.dumps({'title':'fixture','kind':'bugfix','acceptance_criteria':['works'],'fixture':'fixture.txt','manual_steps':['inspect'],'checks':[{'argv':['python','-c','pass'],'rationale':'fixture','covers':['works']}]}))
            exchange=base/'exchange'; exchange.mkdir(); (exchange/'candidates').mkdir()
            class API:
                def _session(self,server,sid): return {'id':sid,'agent':'orchestrate-test','directory':str(repo)}
                def _role(self,server,role,directory): return {'mode':'primary'}
            class Dispatch(API):
                def request_gate(self,*args,**kwargs): self.admitted=True
                admitted=False
            dispatch=Dispatch(); facade=mcp.WorkflowMCP(repo,'author',queue_root=exchange,workflow=w,identity=lambda sid:dispatch._session(None,sid),services=(dispatch,SimpleNamespace()))
            args={'candidate_id':'expired-after-write','sprint_id':'fixture','session_id':'sid','plan_path':'plan.json','build_identity':'fixture','unverified':'','purpose':'readiness'}
            original=facade.workflow.cmd_submit
            def persist_then_expire(ns,root):
                original(ns,root)
                w._operation_deadline.set(time.monotonic()-1)
            facade.workflow.cmd_submit=persist_then_expire
            with self.assertRaises(w.OperationDeadlineExceeded): facade.call('submit',args)
            saved=json.loads((exchange/'candidates'/'expired-after-write.json').read_text())
            self.assertEqual(saved['id'],'expired-after-write')
            self.assertNotIn('gate_request',saved)
            self.assertFalse(dispatch.admitted)

    def test_worker_call_is_not_abandoned_on_caller_cancellation(self):
        import anyio
        completed=threading.Event()
        class Slow:
            def call(self,*args): time.sleep(.05); completed.set(); return {"ok":True}
        async def exercise():
            with anyio.move_on_after(.005) as scope:
                await mcp._thread_call(Slow(),"operation",{})
            self.assertTrue(scope.cancel_called)
            self.assertTrue(completed.is_set())
        anyio.run(exercise)

    def test_author_role_is_verified_using_worktree_api_binding(self):
        facade=next(self.facade("author"))
        self.assertEqual(facade._author("sid")["agent"],"orchestrate-opus")
        facade.dispatch._session=lambda *args:{"id":"sid","agent":"integration-lead","directory":str(facade.repo)}
        with self.assertRaises(PermissionError): facade._author("sid")

    def test_primary_agent_mode_required(self):
        facade=next(self.facade("author"))
        facade.dispatch._role=lambda *args:{"name":"orchestrate-opus","mode":"subagent"}
        with self.assertRaises(PermissionError): facade._author("sid")

    def test_lead_primary_capable_all_mode_allowed_but_subagent_denied(self):
        with tempfile.TemporaryDirectory() as temporary:
            parent=Path(temporary); author=parent/"author03"; lead=parent/"MoonStation14-agent-05"; author.mkdir(); lead.mkdir()
            candidate={"id":"role1","snapshot_key":"snap","worktree":{"worktree":str(author)},"gate_request":{"session_id":"lead-sid","prompt_admitted":True,"snapshot_key":"snap"}}
            class LeadAPI:
                mode="all"
                def _session(self,server,sid): return {"id":sid,"agent":"integration-lead","directory":str(lead)}
                def _role(self,server,role,directory): return {"name":role,"mode":self.mode}
            api=LeadAPI(); facade=mcp.WorkflowMCP(lead,"lead",queue_root=parent/"exchange",workflow=Runtime(),identity=lambda sid:api._session(None,sid),services=(api,SimpleNamespace()))
            facade._lead("lead-sid",candidate)
            api.mode="subagent"
            with self.assertRaises(PermissionError): facade._lead("lead-sid",candidate)

    def test_real_submit_request_retry_and_changed_source_binding(self):
        import subprocess
        from tooling.agent_workflow.workflow_mcp import WorkflowMCP
        w=_main_runtime()
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp); repo=base/"author01"; repo.mkdir()
            subprocess.run(["git","init","-q","--initial-branch=main",str(repo)],check=True)
            subprocess.run(["git","-C",str(repo),"config","user.email","test@example.invalid"],check=True)
            subprocess.run(["git","-C",str(repo),"config","user.name","Fixture"],check=True)
            (repo/"source.txt").write_text("base",encoding="utf-8")
            (repo/".gitignore").write_text("build/\n",encoding="utf-8")
            subprocess.run(["git","-C",str(repo),"add","source.txt",".gitignore"],check=True)
            subprocess.run(["git","-C",str(repo),"commit","-qm","base"],check=True)
            (repo/"fixture.txt").write_text("fixture",encoding="utf-8")
            (repo/"evidence.txt").write_text("evidence",encoding="utf-8")
            plan={"title":"MCP fixture","kind":"bugfix","acceptance_criteria":["source correct"],"fixture":"fixture.txt","manual_steps":["Check result"],"checks":[{"argv":["python","-c","pass"],"rationale":"cheap fixture check","covers":["source correct"]}]}
            (repo/"plan.json").write_text(json.dumps(plan),encoding="utf-8")
            root=base/"exchange"; root.mkdir()
            lead=base/"MoonStation14-agent-05"; lead.mkdir()
            class Dispatch:
                def _session(self,server,sid): return {"id":sid,"agent":"orchestrate-opus","directory":str(repo)}
                def _role(self,server,role,directory): return {"name":role,"mode":"primary"}
                def request_gate(self,args,exchange,*,workflow):
                    self.calls+=1; path=exchange/"candidates"/(args.id+".json"); c=workflow.readjson(path)
                    c["gate_request"]={"origin_session_id":args.origin_session,"session_id":"lead-sid","prompt_admitted":True,"snapshot_key":c["snapshot_key"]}; workflow.writejson(path,c)
                    return {"status":"requested","detail":"durable"}
                calls=0
            dispatch=Dispatch()
            execution=mcp._services()[1]
            facade=WorkflowMCP(repo,"author",queue_root=root,workflow=w,identity=lambda sid:dispatch._session(None,sid),services=(dispatch,execution))
            args={"candidate_id":"candidate1","sprint_id":"sprint1","session_id":"sid","plan_path":"plan.json","build_identity":"build","unverified":"","purpose":"readiness","evidence":["evidence.txt"]}
            first=facade.call("submit",args); self.assertEqual(first["state"],"requested")
            second=facade.call("submit",args); self.assertEqual(second["state"],"requested")
            self.assertEqual(dispatch.calls,2)
            invalid_cases=(
                ("badplan",dict(plan,unsupported=True)),
                ("badcheck",dict(plan,checks=[dict(plan["checks"][0],unsupported=True)])),
                ("badtimeout",dict(plan,checks=[dict(plan["checks"][0],timeout=1)])),
            )
            for identifier,bad_plan in invalid_cases:
                (repo/"plan.json").write_text(json.dumps(bad_plan),encoding="utf-8")
                with self.assertRaises(ValueError): facade.call("submit",dict(args,candidate_id=identifier))
                self.assertFalse((root/"candidates"/(identifier+".json")).exists())
            (repo/"plan.json").write_text(json.dumps(plan),encoding="utf-8")
            lead_facade=WorkflowMCP(lead,"lead",queue_root=root,workflow=w,identity=lambda sid:{"id":sid,"agent":"integration-lead","directory":str(lead)},services=(SimpleNamespace(_session=lambda *x:{},_role=lambda *x:{"name":"integration-lead","mode":"all"}),execution))
            # Existing report flow must reject a ready verdict without its bound passing receipt.
            with self.assertRaisesRegex(ValueError,"matching bound execution job is missing|fresh bound gate validation receipt required"):
                lead_facade.call("finish",{"candidate_id":"candidate1","session_id":"lead-sid","result":"ready_for_owner","findings":["Looks good"]})
            diagnostic=dict(plan,kind="diagnostic",manual_steps=[]); (repo/"diagnostic.json").write_text(json.dumps(diagnostic),encoding="utf-8")
            diag_args=dict(args,candidate_id="diagnostic1",plan_path="diagnostic.json",purpose="diagnostic",evidence=[])
            facade.call("submit",diag_args)
            diagnostic_candidate=w.readjson(root/"candidates"/"diagnostic1.json")
            self.assertEqual(diagnostic_candidate["status"],"diagnostic_request")
            self.assertEqual((diagnostic_candidate.get("gate_request") or {}).get("origin_session_id"),"sid")
            with self.assertRaisesRegex(ValueError,"diagnostic requests do not use integration-lead readiness reports"):
                lead_facade.call("finish",{"candidate_id":"diagnostic1","session_id":"lead-sid","result":"needs_changes","findings":["Fix required"]})
            changed_plan=dict(plan,title="Changed submitted plan"); (repo/"plan.json").write_text(json.dumps(changed_plan),encoding="utf-8")
            with self.assertRaises(ValueError): facade.call("submit",args)
            (repo/"plan.json").write_text(json.dumps(plan),encoding="utf-8")
            (repo/"evidence.txt").write_text("changed evidence",encoding="utf-8")
            with self.assertRaises(ValueError): facade.call("submit",args)
            (repo/"evidence.txt").write_text("evidence",encoding="utf-8")
            (repo/"source.txt").write_text("changed",encoding="utf-8")
            with self.assertRaises(ValueError): facade.call("submit",args)

    def test_submit_uses_only_flat_installed_workflow_module(self):
        import importlib.util
        import shutil
        from tooling.agent_workflow.workflow_mcp import WorkflowMCP
        source=Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory() as td:
            installed=Path(td)/"installed"; installed.mkdir()
            for filename in ("admission.py","validation_evidence.py"):
                shutil.copyfile(source/filename,installed/filename)
            shutil.copyfile(source/"workflow_runtime.py",installed/"workflow.py")
            shutil.copyfile(source/"workflow_mcp.py",installed/"workflow_mcp.py")
            self.assertFalse((installed/"workflow_runtime.py").exists())
            sys.path.insert(0,str(installed))
            try:
                spec=importlib.util.spec_from_file_location("flat_installed_workflow",installed/"workflow.py")
                workflow=importlib.util.module_from_spec(spec); sys.modules[spec.name]=workflow; spec.loader.exec_module(workflow)
                adapter_spec=importlib.util.spec_from_file_location("flat_installed_workflow_mcp",installed/"workflow_mcp.py")
                adapter=importlib.util.module_from_spec(adapter_spec); sys.modules[adapter_spec.name]=adapter; adapter_spec.loader.exec_module(adapter)
                repo=Path(td)/"author01"; repo.mkdir(); subprocess.run(["git","init","-q","--initial-branch=main",str(repo)],check=True)
                subprocess.run(["git","-C",str(repo),"config","user.email","test@example.invalid"],check=True); subprocess.run(["git","-C",str(repo),"config","user.name","Fixture"],check=True)
                (repo/"seed.txt").write_text("seed",encoding="utf-8"); (repo/".gitignore").write_text("build/\n",encoding="utf-8")
                subprocess.run(["git","-C",str(repo),"add","."],check=True); subprocess.run(["git","-C",str(repo),"commit","-qm","seed"],check=True)
                (repo/"fixture.txt").write_text("fixture",encoding="utf-8")
                plan={"title":"Flat install","kind":"bugfix","acceptance_criteria":["fixture binds"],"fixture":"fixture.txt","manual_steps":["Inspect"],"checks":[{"argv":["python","-c","pass"],"rationale":"fixture test","covers":["fixture binds"]}]}
                (repo/"plan.json").write_text(json.dumps(plan),encoding="utf-8"); exchange=Path(td)/"exchange"; exchange.mkdir()
                class Dispatch:
                    def _session(self,server,sid): return {"id":sid,"agent":"orchestrate-opus","directory":str(repo)}
                    def _role(self,server,role,directory): return {"name":role,"mode":"primary"}
                    def request_gate(self,args,root,*,workflow): self.requests+=1; return {"status":"requested","detail":"observer owns dispatch"}
                    requests=0
                dispatch=Dispatch(); facade=adapter.WorkflowMCP(repo,"author",queue_root=exchange,workflow=workflow,identity=lambda sid:dispatch._session(None,sid),services=(dispatch,SimpleNamespace()))
                result=facade.call("submit",{"candidate_id":"flat-candidate","sprint_id":"flat-sprint","session_id":"sid","plan_path":"plan.json","build_identity":"fixture","unverified":"","purpose":"readiness"})
                candidate=workflow.readjson(exchange/"candidates"/"flat-candidate.json")
                self.assertEqual(result["state"],"requested"); self.assertEqual(dispatch.requests,1)
                self.assertEqual(candidate["gate_plan"]["references"]["fixture.txt"],workflow.sha(repo/"fixture.txt"))
            finally:
                sys.path.remove(str(installed))
    def test_real_gate_execution_admission_reuses_one_job_and_canonical05_sibling(self):
        import subprocess
        from tooling.agent_workflow.workflow_mcp import WorkflowMCP
        w=_main_runtime(); execution=mcp._services()[1]
        with tempfile.TemporaryDirectory() as tmp:
            rootpath=Path(tmp); author=rootpath/"author01"; lead=rootpath/"MoonStation14-agent-05"
            author.mkdir(); lead.mkdir(); exchange=rootpath/"exchange"; (exchange/"candidates").mkdir(parents=True)
            plan={"checks":[{"argv":["python","-c","pass"]}]}; snapshot=w.snapshot_key(plan)
            candidate={"id":"validation1","snapshot_key":snapshot,"worktree":{"worktree":str(author)},"gate_plan":plan,"gate_request":{"session_id":"lead-sid","prompt_admitted":True,"snapshot_key":snapshot}}
            (exchange/"candidates"/"validation1.json").write_text(json.dumps(candidate),encoding="utf-8")
            class Dispatch:
                def _session(self,server,sid): return {"id":sid,"agent":"integration-lead","directory":str(lead)}
                def _role(self,server,role,directory): return {"name":role,"mode":self.mode}
                mode="all"
            dispatch=Dispatch(); facade=WorkflowMCP(lead,"lead",queue_root=exchange,workflow=w,identity=lambda sid:dispatch._session(None,sid),services=(dispatch,execution))
            request={"candidate_id":"validation1","session_id":"lead-sid"}
            with self.assertRaises(PermissionError): facade.call("validate",dict(request,session_id="wrong-sid"))
            first=facade.call("validate",request); second=facade.call("validate",request)
            self.assertEqual(first["job_id"],second["job_id"]); self.assertEqual(first["state"],"requested")
            self.assertEqual(execution.read(exchange,"validation1")["attempt"],0)
            dispatch.mode="subagent"
            with self.assertRaises(PermissionError): facade.call("validate",request)
            dispatch.mode="all"
            wrong=WorkflowMCP(rootpath/"fake05","lead",queue_root=exchange,workflow=w,identity=lambda sid:{"id":sid,"agent":"integration-lead","directory":str(rootpath/"fake05")},services=(dispatch,execution))
            (rootpath/"fake05").mkdir()
            with self.assertRaises(PermissionError): wrong.call("validate",request)

    def test_review_routes_existing_scoped_service(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); author=root/"author"; lead=root/"MoonStation14-agent-05"; author.mkdir(); lead.mkdir(); exchange=root/"exchange"; (exchange/"candidates").mkdir(parents=True)
            candidate={"id":"review1","snapshot_key":"snap","worktree":{"worktree":str(author)},"gate_request":{"session_id":"sid","prompt_admitted":True,"snapshot_key":"snap"}}
            (exchange/"candidates"/"review1.json").write_text(json.dumps(candidate),encoding="utf-8")
            dispatch=SimpleNamespace(_session=lambda server,sid:{"id":sid,"agent":"integration-lead","directory":str(lead)},_role=lambda *args:{"name":"integration-lead","mode":"all"})
            seen=[]
            def scoped(args,root,*,workflow): seen.append((args.id,args.reviewer,args.actor,args.repo)); return {"status":"requested","detail":"queued"}
            dispatch.scoped_review=scoped; facade=mcp.WorkflowMCP(lead,"lead",queue_root=exchange,workflow=SimpleNamespace(validid=lambda x:x,readjson=lambda p:json.loads(Path(p).read_text())),identity=lambda sid:dispatch._session(None,sid),services=(dispatch,SimpleNamespace()))
            # Bind identity against the actual durable lead request.
            facade._lead("sid",candidate)
            result=facade.call("review",{"candidate_id":"review1","session_id":"sid","reviewer":"review"})
            self.assertEqual(result["state"],"requested"); self.assertEqual(seen[0][2],"integration-lead")

    def test_real_sdk_stdio_protocol_private_fixture(self):
        try:
            from mcp import ClientSession
            from mcp.client.stdio import StdioServerParameters, stdio_client
        except ImportError:
            self.skipTest("pinned MCP SDK is not installed")
        with tempfile.TemporaryDirectory() as temporary:
            repo=Path(temporary)/"author"; repo.mkdir()
            subprocess.run(["git","init","-q",str(repo)],check=True)
            result=subprocess.run(["git","-C",str(repo),"rev-parse","--git-common-dir"],check=True,capture_output=True,text=True).stdout.strip()
            common=Path(result); common=(repo/common).resolve() if not common.is_absolute() else common
            root=common.parent.parent/"Asset Building Workspace"/"work"/"agent_exchange"
            (root/"candidates").mkdir(parents=True); (root/"gate-execution").mkdir()
            (root/"candidates"/"fixture.json").write_text(json.dumps({"id":"fixture","status":"candidate","worktree":{"worktree":str(repo)},"snapshot_key":"snap","gate_outcome":{},"gate_validation":{},"review":{}}),encoding="utf-8")
            (root/"gate-execution"/"fixture.json").write_text(json.dumps({"candidate_id":"fixture","snapshot_key":"snap","state":"interrupted","reason":"worker stopped; cleanup verified"}),encoding="utf-8")
            env=os.environ.copy(); env["PYTHONPATH"]=str(Path(__file__).resolve().parents[2])+os.pathsep+env.get("PYTHONPATH","")
            params=StdioServerParameters(command=sys.executable,args=[str(Path(__file__).with_name("workflow_mcp.py")),"--repo",str(repo),"--profile","author"],env=env)
            async def exchange():
                async with stdio_client(params) as (read,write):
                    async with ClientSession(read,write) as session:
                        await session.initialize()
                        catalog=await session.list_tools()
                        self.assertEqual({x.name for x in catalog.tools},{"submit","status"})
                        status=await session.call_tool("status",{"candidate_id":"fixture"})
                        result=json.loads(status.content[0].text)
                        self.assertTrue(result["ok"]); self.assertEqual(result["state"],"interrupted")
                        self.assertEqual(result["freshness"],"not_checked"); self.assertIn("do not retry",result["next"])
                        bad=await session.call_tool("status",{"candidate_id":"fixture","timeout":1})
                        self.assertTrue(bad.isError or (bad.content and not json.loads(bad.content[0].text)["ok"]))
            asyncio.run(exchange())

    def test_real_sdk_lead_server_uses_neutral05_sibling_exchange_without_git(self):
        try:
            from mcp import ClientSession
            from mcp.client.stdio import StdioServerParameters, stdio_client
        except ImportError:
            self.skipTest("pinned MCP SDK is not installed")
        with tempfile.TemporaryDirectory() as temporary:
            workspace=Path(temporary)/"neutral-parent"; workspace.mkdir()
            repo=workspace/"MoonStation14-agent-05"; repo.mkdir()
            author=workspace/"author01"; author.mkdir()
            self.assertFalse((repo/".git").exists())
            exchange=workspace/"Asset Building Workspace"/"work"/"agent_exchange"
            (exchange/"candidates").mkdir(parents=True)
            (exchange/"candidates"/"lead-fixture.json").write_text(json.dumps({
                "id":"lead-fixture","status":"candidate","snapshot_key":"neutral-snapshot",
                "worktree":{"worktree":str(author)},"gate_outcome":{},"gate_validation":{},"review":{},
                "gate_request":{"origin_session_id":"author-sid","snapshot_key":"neutral-snapshot"}}),encoding="utf-8")
            env=os.environ.copy(); env["PYTHONPATH"]=str(Path(__file__).resolve().parents[2])+os.pathsep+env.get("PYTHONPATH","")
            params=StdioServerParameters(command=sys.executable,args=[str(Path(__file__).with_name("workflow_mcp.py")),"--repo",str(repo),"--profile","lead"],env=env)
            async def exchange_tools():
                async with stdio_client(params) as (read,write):
                    async with ClientSession(read,write) as session:
                        await session.initialize()
                        catalog=await session.list_tools()
                        self.assertEqual({tool.name for tool in catalog.tools},{"status","validate","review","finish"})
                        status=await session.call_tool("status",{"candidate_id":"lead-fixture"})
                        value=json.loads(status.content[0].text)
                        self.assertTrue(value["ok"]); self.assertEqual(value["candidate_id"],"lead-fixture")
                        self.assertEqual(value["freshness"],"not_checked")
            asyncio.run(exchange_tools())


if __name__=="__main__": unittest.main()
