"""Thin stdio MCP adapter for an explicitly configured local coordinator."""
from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request

from workflow_mcp import _response, _thread_call, schema
import workflow_store

MAX_REQUEST = 1024 * 1024
MAX_HTTP_RESPONSE = 1024 * 1024
SCHEMAS = {
    "submit": schema({"request_path": {"type": "string", "description":"Path to the prepared strict JSON request file. To replace your settled current FEATURE PASS or FAIL after feedback/new source work, put replaces_run_id in the request JSON; DIAGNOSTIC submissions never replace a run."}, "idempotency_key": {"type": "string", "description":"Registered submission key; exact retries reuse this key. A new feature revision uses a fresh key."}}, ("request_path", "idempotency_key")),
    "register_scope": schema({"scope_path":{"type":"string","description":"Path to the prepared strict JSON scope file."},"idempotency_key":{"type":"string","description":"Fresh scope-registration key; recovery checks reuse this key."}},("scope_path","idempotency_key")),
    "scope_status": schema({"idempotency_key":{"type":"string"}},("idempotency_key",)),
    "status": schema({"run_id": {"type": "string"}, "idempotency_key": {"type": "string"}, "include_diagnostics":{"type":"boolean","default":False}, "log_artifact_id":{"type":"string","description":"An artifact ID returned by your diagnostics."}, "log_offset":{"type":"integer","minimum":0,"description":"Byte offset for paging the selected log; use its next_offset."}}),
    "acknowledge_result": schema({"contract_version":{"type":"integer","const":1},"command_id":{"type":"string"},"event_id":{"type":"string"},"run_id":{"type":"string"},"evaluation_generation":{"type":"integer"},"result_id":{"type":"string"},"result_digest":{"type":"string"}}, ("contract_version","command_id","event_id","run_id","evaluation_generation","result_id","result_digest")),
}
REVIEW_SCHEMAS = {
    "claim_task": schema({"task_id":{"type":"string"},"session_id":{"type":"string","minLength":1,"maxLength":2000,"description":"Current assigned reviewer chat session ID."}}, ("task_id","session_id")),
    "complete_review": schema({"result_path":{"type":"string","description":"Prepared strict JSON review result file (alternative to inline result)."},"result":{"type":"object","description":"Semantic review result with task_id, decision, findings, inspected_refs; binding metadata is coordinator-filled."},"session_id":{"type":"string","minLength":1,"maxLength":2000,"description":"Current assigned reviewer chat session ID."}}, ("session_id",)),
    "report_error": schema({"error_path":{"type":"string","description":"Prepared strict JSON task_error file (alternative to inline error)."},"error":{"type":"object","description":"Semantic task error with task_id, error_code, message, artifact_refs; binding metadata is coordinator-filled."},"session_id":{"type":"string","minLength":1,"maxLength":2000,"description":"Current assigned reviewer chat session ID."}}, ("session_id",)),
    "status": schema({"task_id":{"type":"string"},"session_id":{"type":"string","minLength":1,"maxLength":2000,"description":"Current assigned reviewer chat session ID."}, "input_ref":{"type":"string","description":"review_guide or an exact assigned artifact_id; page authorized reviewer input."},"input_offset":{"type":"integer","minimum":0,"default":0,"description":"Byte offset for input_ref paging; use next_offset."}, "source_query":{"type":"string","minLength":1,"maxLength":500,"description":"Optional literal filename/path substring search in bound source."}, "source_path":{"type":"string","minLength":1,"maxLength":2000,"description":"Optional exact repository-relative source file path."}, "source_line":{"type":"integer","minimum":1,"default":1,"description":"Starting line for source_path (one-based)."}}, ("task_id","session_id")),
}
REVIEW_DESCRIPTIONS = {"claim_task":"Claim the bound review assignment with task_id and session_id; coordinator derives assignment bindings.","complete_review":"Submit semantic review_result fields inline as result or through prepared result_path; coordinator fills task/run/generation/digest bindings.","report_error":"Report semantic task_error fields inline as error or through prepared error_path; coordinator fills task/run/generation/digest bindings.","status":"Inspect a bound review task. Read the complete reviewer guide with input_ref=review_guide and page via input_offset/next_offset; read assigned evidence using its exact artifact_id. Frozen source is separately accessed using source_query/source_path. No arbitrary filesystem access is provided."}
DESCRIPTIONS = {"submit": "Submit a prepared request using the same submission key used to register its feature scope. After manual feedback or new source work, the same author may replace their settled current FEATURE PASS or FAIL with a fresh complete OWNER_READY revision; include replaces_run_id in request JSON (not as a tool argument), with fresh scope/submission keys, evidence, and producer IDs. PASS means ready for owner manual testing, not accepted; it does not require owner approval to continue. DIAGNOSTIC submissions never replace a run. Check status after uncertain writes before using new keys. DIAGNOSTIC submissions may set purpose=DIAGNOSTIC and implementation_complete=false to record task-relevant typed checks at any development stage, without a prior failure or feature-complete claim. They use the same serialized lane, authorization, and evidence rules; DIAGNOSTIC PASS is not feature readiness or OWNER_READY.", "register_scope":"Register and freeze policy and evidence before submit; use the submission key inside the scope payload for the later submit call. For DIAGNOSTIC work, purpose=DIAGNOSTIC and implementation_complete=false allow task-relevant typed checks at any development stage, without prior failure or feature-complete requirement. The same serialized lane, authorization, and evidence rules apply; diagnostic PASS is not feature readiness or OWNER_READY. A new feature revision needs fresh scope/submission keys and evidence.", "scope_status":"Recover the receipt for a scope registration using its registration key; after an uncertain response, check status instead of reposting with a new key.", "status": "Inspect an existing submission or run. To retrieve your own bounded validation log excerpts, set include_diagnostics=true with run_id or idempotency_key.", "acknowledge_result":"Acknowledge receipt of a bound workflow result."}

WORKTREE_SCHEMAS = {name: schema({**spec['properties'], 'session_id': {'type': 'string'}},
    tuple(spec['required']) + ('session_id',)) for name, spec in SCHEMAS.items()}


class CoordinatorClient:
    @staticmethod
    def local_failure(exc, args):
        if isinstance(exc, ClientInputError):
            code, message, field, recovery = exc.code, str(exc), exc.field, exc.recovery
        else:
            code, message, field, recovery = "INVALID_TOOL_ARGUMENTS", "Correct the tool arguments and permitted input file.", None, "Check required arguments, types, and file path; local preparation failed before sending."
        safe_args=args if isinstance(args,dict) else {}
        result={"ok":False,"code":code,"message":message,"next_action":"CORRECT_INPUT","recovery":recovery,"idempotency_key":safe_args.get("idempotency_key")}
        if field: result["field"]=field
        return result
    @staticmethod
    def encode_response(name, args, result):
        args=args if isinstance(args,dict) else {}
        diagnostic_status = (name == 'status' and args.get('include_diagnostics') is True
                             and (args.get('run_id') is not None or args.get('idempotency_key') is not None))
        reviewer_source_status = (name == 'status' and args.get('task_id') is not None
                                   and any(args.get(key) is not None for key in ('source_query','source_path','input_ref')))
        limit=65536 if diagnostic_status or reviewer_source_status else 4096
        encoded=_response(result,max_bytes=limit)
        try:
            initial=json.loads(encoded)
            overflow=(initial.get('code')=='response_too_large' and 'next_action' not in initial)
        except (ValueError,AttributeError): overflow=False
        if not overflow: return encoded
        key=args.get('idempotency_key'); run=args.get('run_id'); task=args.get('task_id')
        selector=(f"run_id {run}" if run else f"idempotency_key {key}" if key else f"task_id {task}" if task else "the same assignment")
        if name=='submit':
            fallback={"ok":False,"code":"response_too_large","message":"Submission response exceeded the output limit; verify its receipt before retrying.","next_action":"CHECK_SUBMISSION_STATUS","recovery":"Check status with this same idempotency key before retrying.","idempotency_key":key}
        elif name=='register_scope':
            fallback={"ok":False,"code":"response_too_large","message":"Scope registration response exceeded the output limit; recover its receipt before retrying.","next_action":"CHECK_SCOPE_STATUS","recovery":"Check scope status with this same registration key before retrying.","idempotency_key":key}
        elif name=='acknowledge_result':
            fallback={"ok":False,"code":"response_too_large","message":"Acknowledgement response exceeded the output limit; verify the same event and result.","next_action":"RETRY_SAME_ACKNOWLEDGEMENT","recovery":"Inspect status for the original run, then retry only the identical acknowledgement payload.","event_id":args.get('event_id'),"result_id":args.get('result_id'),"run_id":args.get('run_id'),"command_id":args.get('command_id')}
        elif name in ('claim_task','complete_review','report_error'):
            fallback={"ok":False,"code":"response_too_large","message":"Review response exceeded the output limit; check the frozen task assignment.","next_action":"CHECK_TASK_STATUS","recovery":"Check status for this same assigned task before retrying.","task_id":task}
        elif name=='status' and args.get('include_diagnostics') is True:
            fallback={"ok":False,"code":"response_too_large","message":"Diagnostic output exceeded the limit; retry bounded status without diagnostics.","next_action":"RETRY_STATUS","recovery":f"Check {selector} with include_diagnostics=false."}
        elif name=='status' and args.get('task_id'):
            fallback={"ok":False,"code":"response_too_large","message":"Reviewer status exceeded the output limit; report the oversized status response.","next_action":"REPORT_ERROR","task_id":task,"recovery":f"Use report_error for the assigned task {task}; do not reset the assignment."}
        else:
            fallback={"ok":False,"code":"response_too_large","message":"Status response exceeded the output limit; wait for recovery and retain the same selector.","next_action":"WAIT_FOR_RECOVERY","run_id":run,"idempotency_key":key,"recovery":f"Retain {selector}; do not register or submit with a new key."}
        bounded=_response(fallback)
        try:
            encoded_fallback=json.loads(bounded)
            did_overflow=(encoded_fallback.get('code')=='response_too_large'
                          and 'next_action' not in encoded_fallback)
        except (ValueError,AttributeError): did_overflow=False
        if did_overflow:
            # Preserve actionable semantics; omit rather than truncate opaque IDs.
            fallback={key:value for key,value in fallback.items()
                      if key not in ('idempotency_key','event_id','result_id','run_id','task_id','command_id')}
            fallback['message']='Response exceeded the output limit; use the original call identifiers.'
            fallback['recovery']='Recover or retry using the exact original call IDs/key; do not substitute a truncated identifier.'
            bounded=_response(fallback)
        return bounded

    def __init__(self, endpoint, token, repo, *, transport=None, timeout=240, profile='author', author_mode='static'):
        parsed = urllib.parse.urlsplit(endpoint)
        if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"} or parsed.username or parsed.password or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
            raise ValueError("endpoint must be plain HTTP loopback without path or credentials")
        if not isinstance(token, str) or not token.strip(): raise ValueError("configured token is required")
        self.endpoint = endpoint.rstrip("/"); self.token = token; self.repo = Path(repo).resolve()
        self.transport = transport or self._http
        self.timeout = timeout
        if profile not in ('author','reviewer'): raise ValueError('profile must be author or reviewer')
        self.profile = profile
        if author_mode not in ('static', 'worktree'): raise ValueError('invalid author mode')
        self.author_mode = author_mode

    @staticmethod
    def _http(url, data, headers, timeout):
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs): return None
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect)
        request = urllib.request.Request(url, data=data, headers=headers, method="POST" if data is not None else "GET")
        try:
            with opener.open(request, timeout=timeout) as response:
                payload = response.read(MAX_HTTP_RESPONSE + 1)
                return response.status, payload
        except urllib.error.HTTPError as exc:
            return exc.code, exc.read(MAX_HTTP_RESPONSE + 1)

    def tools(self):
        catalog = REVIEW_SCHEMAS if self.profile == 'reviewer' else WORKTREE_SCHEMAS if self.author_mode == 'worktree' else SCHEMAS
        descriptions = REVIEW_DESCRIPTIONS if self.profile == 'reviewer' else DESCRIPTIONS
        return [{"name": n, "description": descriptions[n], "inputSchema": catalog[n]} for n in catalog]

    def _read_json_file(self, field, value, *, missing_recovery=None):
        path=Path(value)
        if not path.is_absolute(): path=self.repo/path
        try:
            with path.open('rb') as stream: raw=stream.read(MAX_REQUEST+1)
        except FileNotFoundError as exc:
            raise ClientInputError('INPUT_FILE_NOT_FOUND',field,missing_recovery or 'Choose an existing permitted file path.') from exc
        except PermissionError as exc:
            raise ClientInputError('INPUT_FILE_UNREADABLE',field,'Choose a file you can read.') from exc
        except OSError as exc:
            raise ClientInputError('INPUT_FILE_UNREADABLE',field,'Choose an existing file you can read.') from exc
        if len(raw)>MAX_REQUEST:
            raise ClientInputError('PAYLOAD_TOO_LARGE',field,'Use a JSON file no larger than 1 MiB.')
        try:
            text=raw.decode('utf-8',errors='strict')
            parsed=workflow_store.strict_loads(text)
        except (UnicodeDecodeError,workflow_store.ProtocolError,ValueError,TypeError) as exc:
            raise ClientInputError('INVALID_JSON',field,'Correct the file to contain strict UTF-8 JSON.') from exc
        if not isinstance(parsed,dict):
            raise ClientInputError('INVALID_JSON',field,'The JSON root must be an object.')
        return parsed,text

    def prepare_call(self, name, args):
        catalog = REVIEW_SCHEMAS if self.profile == 'reviewer' else WORKTREE_SCHEMAS if self.author_mode == 'worktree' else SCHEMAS
        if name not in catalog: raise ClientInputError('UNKNOWN_TOOL','name','Use one of the available tools: '+', '.join(catalog)+'.')
        spec = catalog[name]
        if not isinstance(args,dict): raise ClientInputError('INVALID_TOOL_ARGUMENTS','arguments','Provide tool arguments as an object.')
        extra=set(args)-set(spec['properties'])
        if extra: raise ClientInputError('INVALID_TOOL_ARGUMENTS',sorted(extra)[0],'Remove unexpected argument(s); allowed: '+', '.join(spec['properties'])+'.')
        missing=set(spec['required'])-set(args)
        if missing: raise ClientInputError('INVALID_TOOL_ARGUMENTS',sorted(missing)[0],'Provide required argument(s): '+', '.join(sorted(missing))+'.')
        identity = args.get('session_id') if (self.profile == 'author' and self.author_mode == 'worktree') or self.profile == 'reviewer' else None
        protocol_args = {key: value for key, value in args.items() if key != 'session_id'}
        if ((self.profile == 'reviewer' or self.profile == 'author' and self.author_mode == 'worktree')
                and (not isinstance(identity,str) or not identity or len(identity)>2000)):
            raise ClientInputError('INVALID_TOOL_ARGUMENTS','session_id','Provide the current chat session ID as a nonempty string of at most 2000 characters.')
        for key,v in protocol_args.items():
            if self.profile == 'reviewer' and name == 'claim_task' and key == 'command_id' and v == '':
                continue
            if self.profile == 'reviewer' and key in ('result','error'):
                if not isinstance(v,dict): raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Provide the canonical payload as an object.')
                continue
            if key == 'source_line':
                if isinstance(v,bool) or not isinstance(v,int) or v<1: raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Provide a positive one-based line number.')
                continue
            if key == 'input_offset':
                if isinstance(v,bool) or not isinstance(v,int) or v<0: raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Provide a nonnegative byte offset.')
                continue
            if key == 'include_diagnostics':
                if not isinstance(v,bool): raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Set include_diagnostics to true or false.')
                continue
            if key == 'log_offset':
                if isinstance(v,bool) or not isinstance(v,int) or v<0 or v>16*1024*1024: raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Provide a nonnegative byte offset within the log limit.')
                continue
            if key in ('contract_version','evaluation_generation','assignment_generation'):
                if isinstance(v,bool) or not isinstance(v,int): raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Provide an integer value.')
                if key=='contract_version' and v!=1: raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Use contract_version 1.')
                if key!='contract_version' and v<=0: raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Provide a positive integer.')
            elif not isinstance(v,str) or not v or len(v)>2000: raise ClientInputError('INVALID_TOOL_ARGUMENTS',key,'Provide a nonempty string no longer than 2000 characters.')
        if self.profile == 'reviewer':
            if name == 'status':
                if args.get('input_ref') is not None and (args.get('source_query') is not None or args.get('source_path') is not None):
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','input_ref','Use input_ref or source_query/source_path, not both.')
                if args.get('input_offset') is not None and args.get('input_ref') is None:
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','input_offset','input_offset is only valid with input_ref.')
                if args.get('input_offset') is not None and (isinstance(args['input_offset'],bool) or not isinstance(args['input_offset'],int) or args['input_offset']<0):
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','input_offset','Provide a nonnegative byte offset.')
                if args.get('source_query') is not None and args.get('source_path') is not None:
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','source_query','Use source_query or source_path, not both.')
                if args.get('source_line') is not None and args.get('source_path') is None:
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','source_line','source_line is only valid with source_path.')
                query=args.get('source_query')
                if query is not None and (not isinstance(query,str) or not query or len(query)>500):
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','source_query','Provide a nonempty literal query of at most 500 characters.')
                path=args.get('source_path')
                if path is not None and (not isinstance(path,str) or not path or len(path)>2000):
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','source_path','Provide a nonempty repository-relative file path.')
                line=args.get('source_line',1)
                if isinstance(line,bool) or not isinstance(line,int) or line<1:
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS','source_line','Provide a positive one-based line number.')
            if name in ('complete_review','report_error'):
                inline='result' if name=='complete_review' else 'error'
                key='result_path' if name=='complete_review' else 'error_path'
                if (inline in args)==(key in args):
                    raise ClientInputError('INVALID_TOOL_ARGUMENTS',inline,'Provide exactly one of '+inline+' or '+key+'.')
                if inline in args:
                    parsed=args[inline]
                    try:
                        check=dict(parsed)
                        # Binding metadata is coordinator-owned for public inline calls.
                        for field,value in {'contract_version':1,'run_id':'run_placeholder','evaluation_generation':1,'assignment_generation':1,'input_digest':'0'*64}.items():
                            check.setdefault(field,value)
                        workflow_store.validate(check,'review_result' if name=='complete_review' else 'task_error')
                        text=workflow_store.canonical_json(parsed)
                    except (workflow_store.ProtocolError,ValueError,TypeError) as exc:
                        raise ClientInputError('INVALID_TOOL_ARGUMENTS',inline,str(exc)) from exc
                    if len(text.encode('utf-8'))>MAX_REQUEST:
                        raise ClientInputError('PAYLOAD_TOO_LARGE',inline,'Use a canonical JSON payload no larger than 1 MiB.')
                    # Inline and file modes send the same canonical protocol body;
                    # reviewer identity is carried only by the session header.
                    data=text.encode('utf-8')
                else:
                    parsed,text=self._read_json_file(key,args[key], missing_recovery=(
                        'Finish writing the review result and confirm this exact file is readable, then retry complete_review with the same result_path and session_id. Do not change the result or assignment.'
                        if name=='complete_review' else None))
                    data=text.encode('utf-8')
                route={'complete_review':'complete','report_error':'error'}[name]
                url=self.endpoint+'/v1/review/'+route
            elif name=='claim_task':
                data=json.dumps({'task_id':protocol_args['task_id']}).encode('utf-8'); url=self.endpoint+'/v1/review/claim'
            else:
                query=urllib.parse.urlencode(protocol_args); data=None; url=self.endpoint+'/v1/review/status?'+query
        elif name == "acknowledge_result":
            data=json.dumps(protocol_args).encode("utf-8"); url=self.endpoint+"/v1/acknowledge-result"
        elif name == 'register_scope':
            scope,_=self._read_json_file('scope_path',protocol_args['scope_path'])
            data=json.dumps({'idempotency_key':protocol_args['idempotency_key'],'scope':scope},separators=(',',':')).encode()
            url=self.endpoint+'/v1/scope/register'
        elif name == 'scope_status':
            url=self.endpoint+'/v1/scope/status?'+urllib.parse.urlencode({'idempotency_key':protocol_args['idempotency_key']}); data=None
        elif name == "status" and not protocol_args and self.author_mode != 'worktree': raise ClientInputError('INVALID_TOOL_ARGUMENTS','run_id','Provide a run_id or idempotency_key selector.')
        elif name == "submit":
            _,request_text=self._read_json_file('request_path',protocol_args['request_path'])
            request = {"idempotency_key": protocol_args["idempotency_key"], "request": request_text}
            url = self.endpoint + "/v1/submit"; data = json.dumps(request).encode()
        else:
            query = urllib.parse.urlencode({k: str(v).lower() if isinstance(v,bool) else v for k,v in protocol_args.items()})
            url = self.endpoint + "/v1/status?" + query; data = None
        headers = {"Authorization": "Bearer " + self.token, "Content-Type": "application/json"}
        if identity is not None: headers['X-MS14-Reviewer-Session' if self.profile == 'reviewer' else 'X-MS14-Author-Session'] = identity
        return name, args, url, data, headers

    @staticmethod
    def interpret_response(name, args, status, body):
        try:
            if len(body) > MAX_HTTP_RESPONSE: raise ValueError("response too large")
            result = json.loads(body.decode("utf-8"))
        except (ValueError, UnicodeDecodeError, TypeError):
            failure=CoordinatorClient.transport_failure(name,args,None)
            failure['code']='invalid_coordinator_response'
            failure['message']='Coordinator response was malformed or exceeded its limit; outcome may be uncertain.'
            failure['http_status']=status
            return failure
        if not isinstance(result,dict):
            failure=CoordinatorClient.transport_failure(name,args,None)
            failure['code']='invalid_coordinator_response'
            failure['message']='Coordinator returned an invalid response; outcome may be uncertain.'
            failure['http_status']=status
            return failure
        if status >= 400 or isinstance(result,dict) and result.get('ok') is False or isinstance(result,dict) and result.get('accepted') is False:
            error=result.get("error",{})
            if not isinstance(error,dict):
                failure=CoordinatorClient.transport_failure(name,args,None)
                failure['code']='invalid_coordinator_response'; failure['message']='Coordinator returned a malformed error response; outcome may be uncertain.'
                failure['http_status']=status
                return failure
            if not error: error=result
            code=error.get('code'); message=error.get('message'); action=error.get('next_action')
            safe={"ok":False,"code":code[:120] if isinstance(code,str) else 'coordinator_error',"message":message[:500] if isinstance(message,str) else 'Coordinator request failed',"next_action":action[:120] if isinstance(action,str) else 'WAIT_FOR_RECOVERY',"idempotency_key":args.get("idempotency_key")}
            for key in ('event_id','result_id','run_id','task_id','command_id'):
                if isinstance(args.get(key),str): safe[key]=args[key]
            if status < 400 and result.get('accepted') is False: safe['accepted']=False
            for key in ("field","recovery"):
                if isinstance(error.get(key),str): safe[key]=error[key][:500]
            return safe
        if name == "submit" and (not isinstance(result.get("response"), dict) or not isinstance(result["response"].get("status"), str)):
            failure=CoordinatorClient.transport_failure(name,args,None)
            failure.update({"code":"invalid_coordinator_response","message":"Coordinator returned no valid submission receipt; verify status using this same key."})
            return failure
        return result

    @staticmethod
    def transport_failure(name, args, exc):
        args=args if isinstance(args,dict) else {}
        def contextual(result):
            for key in ('event_id','result_id','run_id','task_id','command_id','idempotency_key'):
                if isinstance(args.get(key),str): result.setdefault(key,args[key])
            return result
        if name == 'submit':
            return contextual({"ok":False,"code":"submission_outcome_uncertain","message":"Coordinator response unavailable; check status using this same idempotency key before retrying.","next_action":"CHECK_SUBMISSION_STATUS","idempotency_key":args.get('idempotency_key')})
        if name == 'register_scope':
            return contextual({"ok":False,"code":"scope_registration_outcome_uncertain","message":"Coordinator response unavailable; recover using scope_status with this same registration key.","next_action":"CHECK_SCOPE_STATUS","idempotency_key":args.get('idempotency_key')})
        if name == 'acknowledge_result':
            return contextual({"ok":False,"code":"acknowledgement_outcome_uncertain","message":"Inspect status for the original run, then retry only the identical acknowledgement payload.","next_action":"RETRY_SAME_ACKNOWLEDGEMENT","event_id":args.get('event_id'),"result_id":args.get('result_id'),"run_id":args.get('run_id'),"command_id":args.get('command_id')})
        if name in ('claim_task','complete_review','report_error'):
            return contextual({"ok":False,"code":"review_outcome_uncertain","message":"Check status for the same assigned task before retrying.","next_action":"CHECK_TASK_STATUS","task_id":args.get('task_id')})
        return contextual({"ok":False,"code":"coordinator_unavailable","message":"Coordinator response unavailable; retry this read with the same selector.","next_action":"RETRY_STATUS","run_id":args.get('run_id'),"idempotency_key":args.get("idempotency_key"),"task_id":args.get('task_id')})

    def call(self, name, args):
        try: name, args, url, data, headers = self.prepare_call(name, args)
        except ClientInputError as exc:
            return self.local_failure(exc,args)
        except (ValueError, KeyError, OSError) as exc:
            return self.local_failure(exc,args)
        try:
            status, body = self.transport(url, data, headers, self.timeout)
            return self.interpret_response(name, args, status, body)
        except Exception as exc:
            return self.transport_failure(name,args,exc)


class ClientInputError(ValueError):
    def __init__(self, code, field, recovery):
        self.code,self.field,self.recovery=code,field,recovery
        super().__init__(recovery)


def main(argv=None):
    parser = argparse.ArgumentParser(); parser.add_argument("--repo", required=True); parser.add_argument("--endpoint", required=True); parser.add_argument("--token-file", required=True); parser.add_argument('--profile',choices=('author','reviewer'),default='author'); parser.add_argument('--author-mode',choices=('static','worktree'),default='static')
    args = parser.parse_args(argv); facade = CoordinatorClient(args.endpoint, Path(args.token_file).read_text(encoding="utf-8").strip(), args.repo, profile=args.profile, author_mode=args.author_mode)
    from mcp.server import Server
    from mcp.server.stdio import stdio_server
    from mcp.types import Tool, TextContent
    server = Server("moonstation14-coordinator")
    @server.list_tools()
    async def list_tools(): return [Tool(name=t["name"], description=t["description"], inputSchema=t["inputSchema"]) for t in facade.tools()]
    @server.call_tool()
    async def call_tool(name, arguments):
        result = await _thread_call(facade, name, arguments or {})
        return [TextContent(type="text", text=facade.encode_response(name, arguments or {}, result))]
    async def run():
        async with stdio_server() as (r, w): await server.run(r, w, server.create_initialization_options())
    asyncio.run(run())


if __name__ == "__main__": main()
