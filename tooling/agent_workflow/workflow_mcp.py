"""Bounded stdio MCP facade over the installed shared workflow owners."""
from __future__ import annotations

import argparse
import contextlib
import importlib
import io
import json
import os
from pathlib import Path
import sys
import threading

MAX_RESPONSE = 4096
_STDOUT_LOCK=threading.RLock()
SUBMIT_DEADLINE_SECONDS=240
CAPTURE_LOCK_SECONDS=5

class CaptureBusyError(RuntimeError):
    """Another bounded workflow command owns process stdout capture."""


def schema(properties, required=()):
    return {"type":"object","properties":properties,"required":list(required),"additionalProperties":False}


TEXT={"type":"string"}
SUBMIT_SCHEMA=schema({
 "candidate_id":TEXT,"sprint_id":TEXT,"session_id":TEXT,"plan_path":TEXT,"build_identity":TEXT,
 "unverified":TEXT,"base_ref":{"type":"string","default":"main"},
 "purpose":{"type":"string","enum":["readiness","diagnostic"]},
 "evidence":{"type":"array","items":TEXT},"visual_review_required":{"type":"boolean"}},
 ("candidate_id","sprint_id","session_id","plan_path","build_identity","unverified","purpose"))
STATUS_SCHEMA=schema({"candidate_id":TEXT,"detail":{"type":"string","enum":["summary","findings"],"default":"summary"}},("candidate_id",))
VALIDATE_SCHEMA=schema({"candidate_id":TEXT,"session_id":TEXT},("candidate_id","session_id"))
REVIEW_SCHEMA=schema({"candidate_id":TEXT,"session_id":TEXT,"reviewer":{"type":"string","enum":["review","fidelity-reviewer"]}},("candidate_id","session_id","reviewer"))
FINISH_SCHEMA=schema({"candidate_id":TEXT,"session_id":TEXT,"result":{"type":"string","enum":["ready_for_owner","needs_changes"]},"findings":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"string","minLength":1,"maxLength":1000}},"scoped_review":{"type":"string","enum":["review","fidelity-reviewer"]}},("candidate_id","session_id","result","findings"))
SCHEMAS={"submit":SUBMIT_SCHEMA,"status":STATUS_SCHEMA,"validate":VALIDATE_SCHEMA,"review":REVIEW_SCHEMA,"finish":FINISH_SCHEMA}
DESCRIPTIONS={
 "submit":"Freeze and submit an author candidate and automatically request its gate.",
 "status":"Recover or explicitly inspect bounded candidate status; completion is delivered by the observer, not polling.",
 "validate":"Admit the existing serialized validation job for its bound integration lead.",
 "review":"Request a scoped review through the existing integration-lead observer.",
 "finish":"Record the independent lead verdict using existing receipt and finalization guards.",
}


def _load_runtime():
    shared=Path(__file__).resolve().parents[3]/"MoonStation14-shared"
    package=shared/"tooling"/"agent_workflow"
    if (package/"workflow.py").is_file():
        sys.path[:0]=[str(shared),str(package)]
    try:
        from owner_attention import _load_workflow
    except ModuleNotFoundError:
        from tooling.agent_workflow.owner_attention import _load_workflow
    return _load_workflow()


def _services():
    # workflow module lives in the workflow's sibling component directory.
    names=("gate_dispatch","gate_execution")
    loaded=[]
    for name in names:
        try: loaded.append(importlib.import_module(name))
        except ModuleNotFoundError: loaded.append(importlib.import_module("tooling.agent_workflow."+name))
    return loaded


def _capture(fn,*args,**kwargs):
    # Existing command/service prints must never contaminate stdio JSON-RPC.
    if not _STDOUT_LOCK.acquire(timeout=CAPTURE_LOCK_SECONDS):
        raise CaptureBusyError(f'workflow output is busy; waited {CAPTURE_LOCK_SECONDS}s. Retry this same candidate ID after checking status')
    try:
        with contextlib.redirect_stdout(io.StringIO()): return fn(*args,**kwargs)
    finally:
        _STDOUT_LOCK.release()


def _clean(value):
    text=str(value)
    return text[:240]


def _error_payload(exc, dispatch, workflow):
    deadline_type=getattr(workflow,"OperationDeadlineExceeded",())
    if (deadline_type and isinstance(exc,deadline_type)) or isinstance(exc,CaptureBusyError):
        return {"ok":False,"code":"submit_deadline_exceeded" if deadline_type and isinstance(exc,deadline_type) else "workflow_busy",
            "retryable":True,"message":_clean(exc),"next":"Inspect status and retry only with the same candidate ID; do not create an alternate candidate"}
    git_timeout=getattr(workflow,"GitCommandTimeout",())
    if git_timeout and isinstance(exc,git_timeout):
        blocked=not exc.cleanup_verified
        return {"ok":False,"code":"git_cleanup_unverified" if blocked else "git_timeout","retryable":not blocked,
            "message":_clean(exc),"next":"Inspect status; retry only the same candidate ID" if not blocked else "Do not retry or create another candidate until process-tree cleanup is verified"}
    if isinstance(exc,PermissionError): return {"ok":False,"code":"forbidden","message":_clean(exc),"next":"Use the authorized role and bound candidate"}
    input_error=getattr(dispatch,"DispatchInputError",())
    dispatch_error=getattr(dispatch,"DispatchError",())
    if input_error and isinstance(exc,input_error): return {"ok":False,"code":"invalid_request","retryable":False,"message":_clean(exc),"next":"Correct the bound candidate or submitted inputs"}
    if dispatch_error and isinstance(exc,dispatch_error): return {"ok":False,"code":"workflow_unavailable","retryable":True,"message":_clean(exc),"next":"Inspect status and the existing durable record; do not submit an alternate candidate"}
    exit_type=getattr(workflow,"WorkflowExit",())
    if exit_type and isinstance(exc,exit_type): return {"ok":False,"code":"workflow_blocked","message":_clean(exc),"next":"Use status to inspect the durable workflow record"}
    pending_type=getattr(workflow,"GateFinalizationPending",())
    if pending_type and isinstance(exc,pending_type):
        return {"ok":False,"code":"validation_pending","retryable":False,"message":_clean(exc),
                "next":"Stop and wait for existing observer completion; do not poll, resubmit, or create another job"}
    if isinstance(exc,(ValueError,KeyError,TypeError)): return {"ok":False,"code":"invalid_request","message":_clean(exc),"next":"Correct the candidate binding or tool arguments"}
    return {"ok":False,"code":"workflow_error","message":_clean(exc),"next":"Inspect the existing workflow record; do not retry destructive work blindly"}


async def _thread_call(facade,name,args):
    import anyio
    return await anyio.to_thread.run_sync(facade.call,name,args,abandon_on_cancel=False)


class WorkflowMCP:
    def __init__(self, repo, profile, *, queue_root=None, workflow=None, identity=None, services=None):
        self.repo=Path(repo).resolve()
        if profile not in ("author","lead"): raise ValueError("profile must be author or lead")
        self.profile=profile; self.workflow=workflow or _load_runtime(); self.identity=identity
        asset_workspace=str(self.repo.parent/"Asset Building Workspace") if profile=="lead" else None
        self.queue_root=Path(queue_root).resolve() if queue_root else self.workflow.queue(type("Args",(),{"repo":str(self.repo),"asset_workspace":asset_workspace})())
        self.dispatch,self.execution=services or _services()

    def tools(self):
        names=("submit","status") if self.profile=="author" else ("status","validate","review","finish")
        return [{"name":n,"description":DESCRIPTIONS[n],"inputSchema":SCHEMAS[n]} for n in names]

    @staticmethod
    def _validate(name,args):
        s=SCHEMAS[name]
        if not isinstance(args,dict) or set(args)-set(s["properties"]): raise ValueError("unknown tool arguments")
        missing=set(s["required"])-set(args)
        if missing: raise ValueError("missing required tool arguments")
        for key,value in args.items():
            spec=s["properties"][key]; typ=spec["type"]
            good=(isinstance(value,str) if typ=="string" else isinstance(value,bool) if typ=="boolean" else isinstance(value,list) if typ=="array" else False)
            if not good: raise ValueError("invalid argument type")
            if typ=="string" and (len(value)>4000 or len(value)<spec.get("minLength",0) or ("enum" in spec and value not in spec["enum"])): raise ValueError("invalid argument value")
            if typ=="array" and (len(value)>spec.get("maxItems",100) or len(value)<spec.get("minItems",0) or any(not isinstance(x,str) or not x.strip() or len(x)>spec["items"].get("maxLength",1000) for x in value)): raise ValueError("invalid bounded array")
        return args

    def _api_session(self,sid):
        if self.identity: return self.identity(sid)
        return self.dispatch._session(None,sid)

    def _author(self,sid):
        info=self._api_session(sid)
        if not isinstance(info,dict) or info.get("id",sid)!=sid or Path(info.get("directory",info.get("location",{}).get("directory",""))).resolve()!=self.repo:
            raise PermissionError("session is not bound to this author worktree")
        agent=info.get("agent")
        if not isinstance(agent,str) or not agent.startswith("orchestrate-"):
            raise PermissionError("session does not use an approved author agent")
        role=self.dispatch._role(None,agent,self.repo)
        if not isinstance(role,dict) or role.get("mode")!="primary":
            raise PermissionError("session agent is not configured as a primary author")
        return info

    def _lead(self,sid,candidate):
        gate=candidate.get("gate_request") or {}
        author=Path(candidate.get("worktree",{}).get("worktree","")).resolve()
        if (self.repo!=author.parent/"MoonStation14-agent-05" or gate.get("session_id")!=sid or not gate.get("prompt_admitted") or gate.get("snapshot_key")!=candidate.get("snapshot_key")):
            raise PermissionError("session is not the admitted lead bound to this candidate")
        info=self._api_session(sid)
        if not isinstance(info,dict) or info.get("id",sid)!=sid or Path(info.get("directory",info.get("location",{}).get("directory",""))).resolve()!=self.repo or info.get("agent")!="integration-lead":
            raise PermissionError("session is not integration-lead in the canonical worktree")
        role=self.dispatch._role(None,"integration-lead",self.repo)
        if not isinstance(role,dict) or role.get("mode") not in ("primary","all"): raise PermissionError("integration-lead is not configured as a primary-capable role")

    def _candidate(self,ident):
        path=self.queue_root/"candidates"/(self.workflow.validid(ident)+".json")
        if not path.is_file(): raise ValueError("unknown candidate")
        c=self.workflow.readjson(path)
        if c.get("id")!=ident: raise ValueError("candidate identity mismatch")
        return path,c

    def call(self,name,args):
        if name not in {t["name"] for t in self.tools()}: raise PermissionError("tool unavailable for configured profile")
        a=self._validate(name,args); w=self.workflow
        if name=="status":
            _,c=self._candidate(a["candidate_id"])
            if self.profile=="author":
                if Path(c.get("worktree",{}).get("worktree","")).resolve()!=self.repo: raise PermissionError("candidate belongs to another author worktree")
            else:
                gate=c.get("gate_request") or {}
                if (self.repo!=Path(c.get("worktree",{}).get("worktree","")).resolve().parent/"MoonStation14-agent-05" or
                    not gate.get("origin_session_id") or gate.get("snapshot_key")!=c.get("snapshot_key")):
                    raise PermissionError("lead status is restricted to bound submitted author candidates")
            result=c.get("gate_outcome") or {}; review=c.get("review") or {}; validation=c.get("gate_validation") or {}
            gate=c.get("gate_request") or {}; snapshot=c.get("snapshot_key")
            phase={}; health={}
            for label,path in (("progress",self.queue_root/"progress"/(c["id"]+".json")),("health",self.queue_root/"gate-health"/(c["id"]+".json"))):
                if path.is_file():
                    item=w.readjson(path)
                    if item.get("candidate_id")==c["id"] and item.get("snapshot_key")==snapshot:
                        if label=="progress": phase=item
                        elif item.get("session_id")==gate.get("session_id"): health=item
            job=self.execution.read(self.queue_root,c["id"]) or {}
            if job and (job.get("candidate_id")!=c["id"] or job.get("snapshot_key")!=snapshot): job={}
            state=result.get("result") or health.get("state") or job.get("state") or phase.get("phase") or c.get("status")
            reason=health.get("reason") or job.get("reason") or phase.get("detail") or ""
            next_action=health.get("next_action") or ("Inspect the interrupted bound job; do not retry validation automatically" if job.get("state")=="interrupted" else "Continue using the existing workflow observer")
            out={"ok":True,"candidate_id":c["id"],"state":state,"phase":phase.get("phase"),"job_state":job.get("state"),"reason":str(reason)[:300],"next":next_action,"freshness":"not_checked","receipt":validation.get("path"),"report":result.get("report_path") or review.get("report_path")}
            if a.get("detail")=="findings":
                findings=result.get("findings") or []
                bounded=[]; used=0
                for finding in findings:
                    item=str(finding)[:1200-used]
                    if not item: break
                    bounded.append(item); used+=len(item)
                    if used>=1200: break
                out["findings"]=bounded
            return out
        if name=="submit": return self._submit(a)
        _,c=self._candidate(a["candidate_id"]); self._lead(a["session_id"],c)
        if name=="validate":
            job=self.execution.request(self.queue_root,a["candidate_id"],workflow=w)
            state=job.get("state")
            return {"ok":state not in ("blocked","interrupted"),"candidate_id":a["candidate_id"],"state":state,"job_id":job.get("job_id"),"next":"Inspect existing job state; do not automatically retry" if state in ("blocked","interrupted") else "Existing observer owns execution"}
        if name=="review":
            result=self.dispatch.scoped_review(argparse_namespace(id=a["candidate_id"],reviewer=a["reviewer"],actor="integration-lead",repo=str(self.repo)),self.queue_root,workflow=w)
            state=result.get("status")
            return {"ok":state!="blocked","candidate_id":a["candidate_id"],"state":state,"reviewer":a["reviewer"],"next":str(result.get("detail",""))[:240]}
        if name=="finish":
            obj=argparse_namespace(id=a["candidate_id"],actor="integration-lead",result=a["result"],finding=a["findings"],scoped_review=a.get("scoped_review"),finish=True)
            _capture(w.cmd_gate_report,obj,self.queue_root)
            return {"ok":True,"candidate_id":a["candidate_id"],"state":a["result"]}

    def _submit(self,a):
        with self.workflow.operation_deadline(SUBMIT_DEADLINE_SECONDS):
            return self._submit_bounded(a)

    def _submit_bounded(self,a):
        actor=self._author(a["session_id"]); plan_path=wpath=self.workflow.safe(self.repo,a["plan_path"])
        if not plan_path.is_file(): raise ValueError("plan file is missing")
        plan=self.workflow.resolve_gate_plan(self.workflow.readjson(plan_path),self.repo)
        diagnostic=a["purpose"]=="diagnostic"
        if diagnostic!=(plan.get("kind")=="diagnostic"): raise ValueError("purpose does not match plan kind")
        if a.get("visual_review_required") and diagnostic: raise ValueError("diagnostic cannot require visual review")
        evidence=[]
        for path in a.get("evidence",[]):
            resolved=self.workflow.safe(self.repo,path)
            if not resolved.exists(): raise ValueError("evidence path is missing")
            evidence.append(path)
        candidate_id=self.workflow.validid(a["candidate_id"]); cpath=self.queue_root/"candidates"/(candidate_id+".json")
        if cpath.exists():
            c=self.workflow.readjson(cpath)
            same=(Path(c.get("worktree",{}).get("worktree","")).resolve()==self.repo and
                  (c.get("gate_request") or {}).get("origin_session_id") in (None,a["session_id"]) and
                  c.get("gate_plan")==plan and c.get("sprint_id")==a["sprint_id"] and
                  c.get("build_identity")==a["build_identity"] and c.get("unverified")==a["unverified"] and
                  c.get("agent")==actor.get("agent") and c.get("base_ref")==a.get("base_ref","main") and
                  c.get("evidence")==self.workflow.evidence_paths(evidence,str(self.repo)) and
                  bool(c.get("visual_review_required"))==bool(a.get("visual_review_required",False)))
            if same:
                base,committed,dirty=self.workflow.git_changes(self.repo,c.get("base_ref",a.get("base_ref","main")))
                same=(base==c.get("base_sha") and committed==c.get("committed_changes") and dirty==c.get("dirty_changes"))
            if not same: raise ValueError("candidate ID is bound to different frozen submission; use a fresh revision")
            cpath,c=self._candidate(candidate_id)
        else:
            steps=("diagnostic only; no owner/manual readiness" if diagnostic else "\n".join(plan.get("manual_steps",[])))
            ns=argparse_namespace(repo=str(self.repo),agent=actor["agent"],id=candidate_id,sprint=a["sprint_id"],base_ref=a.get("base_ref","main"),evidence=evidence,build_identity=a["build_identity"],steps=steps,unverified=a["unverified"],gate_plan=str(plan_path),diagnostic_only=diagnostic,visual_review_required=bool(a.get("visual_review_required",False)))
            _capture(self.workflow.cmd_submit,ns,self.queue_root)
            self.workflow.check_operation_deadline()
            cpath,c=self._candidate(candidate_id)
        self.workflow.check_operation_deadline()
        result=self.dispatch.request_gate(argparse_namespace(id=candidate_id,origin_session=a["session_id"]),self.queue_root,workflow=self.workflow)
        state=result.get("status")
        return {"ok":state not in ("blocked","error"),"candidate_id":candidate_id,"state":state,"detail":str(result.get("detail",""))[:240],"next":"Inspect status for the durable request; do not create another candidate" if state in ("blocked","error") else "Existing observer owns gate dispatch"}


def argparse_namespace(**kwargs):
    from types import SimpleNamespace
    return SimpleNamespace(**kwargs)


def _response(value, max_bytes=MAX_RESPONSE):
    raw=json.dumps(value,separators=(",",":"),ensure_ascii=False)
    if len(raw.encode("utf-8"))>max_bytes:
        raw=json.dumps({"ok":False,"code":"response_too_large","message":"Bounded response limit exceeded","next":"Request summary status"},separators=(",",":"))
    return raw


def main(argv=None):
    parser=argparse.ArgumentParser(); parser.add_argument("--repo",required=True); parser.add_argument("--profile",choices=("author","lead"),required=True)
    args=parser.parse_args(argv)
    from mcp.server import Server
    from mcp.server.stdio import stdio_server
    from mcp.types import Tool,TextContent
    server=Server("moonstation14-workflow"); facade=WorkflowMCP(args.repo,args.profile)
    @server.list_tools()
    async def list_tools(): return [Tool(name=x["name"],description=x["description"],inputSchema=x["inputSchema"]) for x in facade.tools()]
    @server.call_tool()
    async def call_tool(name,arguments):
        try: result=await _thread_call(facade,name,arguments or {})
        except Exception as exc: result=_error_payload(exc,facade.dispatch,facade.workflow)
        return [TextContent(type="text",text=_response(result))]
    import asyncio
    async def run():
        async with stdio_server() as (r,w): await server.run(r,w,server.create_initialization_options())
    asyncio.run(run())


if __name__=="__main__": main()
