"""Inactive authoritative admission facade over preflight and WorkflowStore."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import tempfile
import time
import stat
import zipfile
from pathlib import Path

import workflow_preflight
import workflow_store
import workflow_runtime


def _strict_request(raw):
    if isinstance(raw, dict):
        workflow_store.canonical_json(raw)
        return raw
    if isinstance(raw, str):
        return workflow_store.strict_loads(raw)
    raise workflow_store.ProtocolError("request must be a JSON object or JSON string")


def _failure_response(verdict, message, *, code="PREFLIGHT_REJECTED", field="request", evidence=None, required_change=None):
    def bounded(value):
        return ' '.join(str(value).split())[:2000] or 'No diagnostic message is available.'
    if verdict == 'ERROR':
        return {"contract_version":1,"status":"REJECTED","result":"ERROR","next_action":"WAIT_FOR_RECOVERY",
                "code":code,"message":bounded(message),
                "required_change":bounded(required_change or "Wait for tooling recovery, then retry the identical request with the same submission key."),
                "incident_ref":evidence['incident_id']}
    finding = {"category":"COVERAGE", "criterion_id":"preflight", "location":field,
               "message":message,"required_change":required_change or message,
               "evidence_refs":evidence}
    return {"contract_version":1,"status":"REJECTED","result":verdict,
            "findings":[finding],"next_action":"FIX_FINDINGS",
            "code":code}


def _write_immutable(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix='.workflow-evidence-', dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        try:
            os.link(temporary,path)
        except FileExistsError:
            if path.read_bytes()!=data: raise RuntimeError(f"immutable evidence path collision: {path}")
    finally:
        try: os.unlink(temporary)
        except FileNotFoundError: pass
    return path


def _save_diagnostic(store, artifact_dir, caller, key, request, fault):
    subid=store.submission_id_for(caller,key)
    data=workflow_store.canonical_json(fault).encode()
    sha=hashlib.sha256(data).hexdigest(); path=_write_immutable(Path(artifact_dir)/subid/sha/'preflight-diagnostic.json',data)
    artifact={'contract_version':1,'artifact_id':_generated_artifact_id(subid,sha,'PROVENANCE'),'run_id':None,'producer_task_id':None,
        'type':'PROVENANCE','path':str(path),'sha256':sha,'size_bytes':len(data),'immutable':True,
        'producer_kind':'SUBMISSION','producer_id':subid,'submission_id':subid}
    input_digest=request.get('input_digest') if isinstance(request,dict) else None
    if not isinstance(input_digest,str) or len(input_digest)!=64 or any(c not in '0123456789abcdef' for c in input_digest): input_digest='0'*64
    store.register_artifact(artifact,input_digest=input_digest)
    return [artifact['artifact_id']]


def _generated_artifact_id(submission_id, content_sha256, artifact_type):
    identity=workflow_store.digest({'submission_id':submission_id,'content_sha256':content_sha256,'type':artifact_type})
    return 'artifact_preflight_'+identity[:32]


def _freeze_source_snapshot(repo, manifest, base, submission_id):
    """Create and immutably retain a deterministic archive of manifest FILE rows."""
    parent=Path(base)/submission_id
    parent.mkdir(parents=True,exist_ok=True)
    fd, temporary=tempfile.mkstemp(prefix='.source-snapshot-',suffix='.zip',dir=parent)
    os.close(fd)
    try:
        with zipfile.ZipFile(temporary,'w',compression=zipfile.ZIP_STORED) as archive:
            for row in sorted(manifest['files'],key=lambda item:item['path']):
                workflow_runtime.check_operation_deadline()
                if row['state']!='FILE': continue
                name=row['path']
                if name.startswith('/') or '\\' in name or any(part in ('','..','.') for part in name.split('/')):
                    raise RuntimeError(f'unsafe source manifest path: {name}')
                source=Path(repo)/name
                resolved=source.resolve(strict=True)
                if not resolved.is_relative_to(Path(repo)) or stat.S_ISLNK(source.lstat().st_mode) or not stat.S_ISREG(source.lstat().st_mode):
                    raise RuntimeError(f'unsafe source file: {name}')
                info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0)); info.compress_type=zipfile.ZIP_STORED
                mode=source.stat().st_mode
                info.external_attr=((stat.S_IFREG | (mode & 0o777)) << 16)
                digest=hashlib.sha256(); size=0
                with source.open('rb') as input_stream, archive.open(info,'w') as output_stream:
                    while True:
                        workflow_runtime.check_operation_deadline()
                        chunk=input_stream.read(1024*1024)
                        if not chunk: break
                        digest.update(chunk); size+=len(chunk); output_stream.write(chunk)
                if digest.hexdigest()!=row['sha256'] or size!=row['size_bytes']:
                    raise RuntimeError(f'source changed while snapshotting: {name}')
        digest=hashlib.sha256(); size=0
        with open(temporary,'rb') as stream:
            while True:
                workflow_runtime.check_operation_deadline(); chunk=stream.read(1024*1024)
                if not chunk: break
                digest.update(chunk); size+=len(chunk)
        sha=digest.hexdigest(); target=parent/sha/'source-snapshot.zip'; target.parent.mkdir(parents=True,exist_ok=True)
        try: os.link(temporary,target)
        except FileExistsError:
            check=hashlib.sha256()
            with target.open('rb') as stream:
                for chunk in iter(lambda:stream.read(1024*1024),b''): check.update(chunk)
            if check.hexdigest()!=sha: raise RuntimeError(f'immutable evidence path collision: {target}')
        return {'contract_version':1,'artifact_id':_generated_artifact_id(submission_id,sha,'PROVENANCE'),
            'run_id':None,'producer_task_id':None,'type':'PROVENANCE','path':str(target),'sha256':sha,
            'size_bytes':size,'immutable':True,'producer_kind':'SUBMISSION','producer_id':submission_id,'submission_id':submission_id}
    finally:
        try: os.unlink(temporary)
        except FileNotFoundError: pass


class AdmissionUnavailable(RuntimeError):
    pass


def submit_submission(store, *, caller, idempotency_key, request, context, artifact_dir, replace_head_run_id=None,
                      context_resolver=None, admission_guard=None, identity_guard=None):
    """Strictly verify, freeze and durably admit; caller/context are trusted bindings."""
    if not isinstance(caller,str) or not caller.strip(): raise workflow_store.ProtocolError("caller binding is required")
    if not isinstance(idempotency_key,str) or not idempotency_key.strip(): raise workflow_store.ProtocolError("explicit idempotency key is required")
    base=Path(artifact_dir).resolve(); repo=Path(context.repo).resolve()
    if base == repo or repo in base.parents:
        raise workflow_store.ProtocolError("artifact_dir must be outside repository")
    req=None; parse_error=None
    try:
        req=_strict_request(request)
        if not isinstance(req,dict): raise workflow_store.ProtocolError('request must be a JSON object')
        if req.get('idempotency_key') != idempotency_key:
            receipt_request={'idempotency_key':idempotency_key,'bound_request':req}
        else: receipt_request=req
        req_digest=workflow_store.digest(receipt_request)
    except Exception as exc:
        parse_error=exc
        req=None
        if not isinstance(idempotency_key,str) or not idempotency_key.strip(): raise workflow_store.ProtocolError(f'cannot persist malformed request without explicit key: {exc}')
        receipt_request={'idempotency_key':idempotency_key,'malformed_request':request if isinstance(request,str) else repr(request)}
        req_digest=workflow_store.digest(receipt_request)
    # Validate the trusted binding itself before any accepted-receipt lookup.
    try:
        workflow_store.validate(context.authenticated_author,'binding')
        context_repo=Path(context.repo).resolve()
        if Path(context.authenticated_author['worktree']).resolve()!=context_repo:
            raise workflow_store.ProtocolError('trusted author binding does not match configured worktree')
    except Exception as exc:
        raise workflow_store.ProtocolError(f'invalid trusted caller context: {exc}') from exc
    trusted_author_ok=(isinstance(req,dict) and req.get('author')==context.authenticated_author and
                       req.get('source_worktree')==str(context_repo))
    def lookup_existing_receipt():
        try:
            return store.lookup_submission_receipt(caller=caller,idempotency_key=idempotency_key,request_digest=req_digest)
        except workflow_store.IdempotencyConflict:
            if identity_guard is not None: identity_guard()
            raise
    if trusted_author_ok:
        saved=lookup_existing_receipt()
        if saved is not None:
            if identity_guard is not None: identity_guard()
            if saved.get('status')=='ACCEPTED' and not trusted_author_ok:
                raise workflow_store.ProtocolError('accepted receipt is not available outside its trusted author/worktree binding')
            return saved
    else:
        # Still bind retries/conflicts before making diagnostic artifacts or records.
        saved=lookup_existing_receipt()
        if saved is not None:
            if identity_guard is not None: identity_guard()
            if saved.get('status')=='ACCEPTED':
                raise workflow_store.ProtocolError('accepted receipt is not available outside its trusted author/worktree binding')
            return saved
    # Replays above remain authoritative even if new intake is currently held.
    if admission_guard is not None:
        admission_guard()
    if identity_guard is not None: identity_guard()
    if req is None or receipt_request is not req or req.get('idempotency_key') != idempotency_key:
        error=('Request idempotency key must match the explicit key.' if req is not None else 'Malformed request: '+str(parse_error))
        field='idempotency_key' if req is not None else 'request'
        fault={'code':'MALFORMED_REQUEST' if req is None else 'IDEMPOTENCY_KEY_MISMATCH','field':field,'message':error,'required_change':error,'request_digest':req_digest}
        return _durable_rejection(store,artifact_dir,caller,idempotency_key,receipt_request,req_digest,'FAIL',fault,allow_accepted=False)
    if not trusted_author_ok:
        error='Request author does not match trusted authenticated author.'
        fault={'code':'AUTHOR_MISMATCH','field':'author','message':error,'required_change':error,'request_digest':req_digest}
        return _durable_rejection(store,artifact_dir,caller,idempotency_key,req,req_digest,'FAIL',fault,allow_accepted=False)
    try:
        with workflow_runtime.operation_deadline(240):
            if context_resolver is not None:
                context = context_resolver(req)
            return _submit_verified(store,caller,idempotency_key,req,req_digest,context,base,replace_head_run_id)
    except workflow_store.IdempotencyConflict:
        raise
    except workflow_preflight.PreflightFailure as exc:
        fault={'code':exc.code,'field':exc.field,'message':exc.message,'required_change':exc.required_change,'request_digest':req_digest}
        return _durable_rejection(store,artifact_dir,caller,idempotency_key,req,req_digest,exc.verdict,fault,allow_accepted=True)
    except workflow_store.SubmissionHeadConflict as exc:
        if exc.reason == workflow_store.SubmissionHeadConflict.ACTIVE_OR_UNSETTLED:
            code='FEATURE_HEAD_UNSETTLED'
            message='The existing feature head is active or cleanup remains unsettled.'
            verdict='ERROR'
        elif exc.reason == workflow_store.SubmissionHeadConflict.INVALID_TARGET:
            code='REPLACEMENT_TARGET_INVALID'
            message='The requested replacement target is not eligible.'
            verdict='FAIL'
        elif req.get('replaces_run_id') is None:
            code='SUBMISSION_REPLACEMENT_REQUIRED'
            message='A current feature head exists; explicitly identify your own settled PASS or FAIL feature run to replace it with a fresh revision.'
            verdict='FAIL'
        else:
            code='FEATURE_HEAD_UNSETTLED'
            message='The existing feature head is active or cleanup remains unsettled.'
            verdict='ERROR'
        field='replaces_run_id' if code in ('SUBMISSION_REPLACEMENT_REQUIRED','REPLACEMENT_TARGET_INVALID') else 'feature_head'
        required=('Choose the exact current settled PASS or FAIL run you authored; submit a fresh full request with replaces_run_id, new scope, key, and evidence.' if code=='SUBMISSION_REPLACEMENT_REQUIRED' else
            'Use the exact eligible current settled PASS or FAIL run only; do not change sprint or infer another run identifier.' if code=='REPLACEMENT_TARGET_INVALID' else
            'Wait for the original run and cleanup to settle, then retry with a fresh request key and current evidence.')
        fault={'code':code,'field':field,'message':message,
            'required_change':required,
            'request_digest':req_digest}
        return _durable_rejection(store,artifact_dir,caller,idempotency_key,req,req_digest,verdict,fault,allow_accepted=True)
    except Exception as exc:
        code=getattr(exc,'code','ADMISSION_SYSTEM_ERROR'); field=getattr(exc,'field','admission')
        fault={'code':code,'field':field,'message':str(exc),'required_change':'Wait for tooling recovery; retain this submission key and retry the identical request only when receipt status is uncertain.','request_digest':req_digest}
        return _durable_rejection(store,artifact_dir,caller,idempotency_key,req,req_digest,'ERROR',fault,allow_accepted=True)


def _durable_rejection(store, artifact_dir, caller, key, receipt_request, request_digest, verdict, fault, *, allow_accepted):
    refs=[]
    try:
        refs=_save_diagnostic(store,artifact_dir,caller,key,receipt_request,fault)
    except Exception as exc:
        fault=dict(fault,message=f"Admission diagnostic could not be staged ({type(exc).__name__}); no admission occurred.")
    if verdict=='FAIL' and not refs: verdict='ERROR'
    code=fault['code']; field=fault['field']; message=fault['message']; required=fault['required_change']
    if verdict=='ERROR':
        incident_id='incident_'+workflow_store.digest({'caller':caller,'key':key,'request_digest':request_digest,'code':code})[:24]
        incident={'contract_version':1,'incident_id':incident_id,'code':code,'message':message,
                  'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,'artifact_refs':[]}
        response=_failure_response('ERROR',message,code=code,evidence={'incident_id':incident_id})
        try:
            return store.record_rejection(caller=caller,idempotency_key=key,raw_request=receipt_request,response=response,incident=incident)
        except workflow_store.IdempotencyConflict:
            saved=store.lookup_submission_receipt(caller=caller,idempotency_key=key,request_digest=request_digest)
            if saved is not None:
                if saved.get('status')=='ACCEPTED' and not allow_accepted:
                    raise workflow_store.ProtocolError('accepted receipt is not available outside its trusted author/worktree binding')
                return saved
            raise
        except Exception as exc:
            raise AdmissionUnavailable(f"not accepted; durable receipt unavailable; inspect storage and retry the same key after recovery ({exc})") from exc
    response=_failure_response('FAIL',message,code=code,field=field,evidence=refs,required_change=required)
    try:
        return store.record_rejection(caller=caller,idempotency_key=key,raw_request=receipt_request,response=response)
    except workflow_store.IdempotencyConflict:
        saved=store.lookup_submission_receipt(caller=caller,idempotency_key=key,request_digest=request_digest)
        if saved is not None:
            if saved.get('status')=='ACCEPTED' and not allow_accepted:
                raise workflow_store.ProtocolError('accepted receipt is not available outside its trusted author/worktree binding')
            return saved
        raise
    except Exception as exc:
        raise AdmissionUnavailable(f"not accepted; durable receipt unavailable; inspect storage and retry the same key after recovery ({exc})") from exc


def _submit_verified(store,caller,idempotency_key,req,req_digest,context,base,replace_head_run_id):
    repo=Path(context.repo).resolve()
    bundle=workflow_preflight.preflight(req,caller=caller,context=context)
    # Diagnostic admission is an explicit, validated request purpose; never infer
    # it from a caller-supplied run kind or from a feature-head conflict.
    run_kind='DIAGNOSTIC' if req.get('purpose')=='DIAGNOSTIC' else 'FEATURE_GATE'
    subid=bundle['submission_id']; metadata=[]
    for artifact in bundle['verified_artifacts']:
        workflow_runtime.check_operation_deadline()
        source=Path(artifact['path']); data=source.read_bytes()
        if hashlib.sha256(data).hexdigest()!=artifact['sha256'] or len(data)!=artifact['size_bytes']:
            raise workflow_preflight.PreflightFailure('FAIL','ARTIFACT_DRIFT','artifacts',
                f"Proof changed before staging: {artifact['artifact_id']}",
                'Regenerate and re-register the changed evidence from stable bytes, then submit with a fresh scope and key.')
        frozen=_write_immutable(base/subid/artifact['sha256']/source.name,data)
        artifact['path']=str(frozen); metadata.append(artifact)
    files={'source-manifest.json':workflow_store.canonical_json(bundle['source_manifest']).encode(),
           'preflight-bundle.json':workflow_store.canonical_json(bundle).encode()}
    for name,data in files.items():
        workflow_runtime.check_operation_deadline()
        digest=hashlib.sha256(data).hexdigest(); path=_write_immutable(base/subid/digest/name,data)
        typ='SOURCE_MANIFEST' if name.startswith('source') else 'PROVENANCE'
        metadata.append({'contract_version':1,'artifact_id':_generated_artifact_id(subid,digest,typ),'run_id':None,
            'producer_task_id':None,'type':typ,'path':str(path),'sha256':digest,'size_bytes':len(data),'immutable':True,
            'producer_kind':'SUBMISSION','producer_id':subid,'submission_id':subid})
    metadata.append(_freeze_source_snapshot(repo,bundle['source_manifest'],base,subid))
    workflow_runtime.check_operation_deadline()
    receipt_hash=hashlib.sha256(Path(context.publication_receipt_path).read_bytes()).hexdigest()
    if (workflow_preflight.capture_source(repo)!=bundle['source_manifest'] or
        receipt_hash!=bundle['config_provenance']['receipt_sha256'] or
        any(hashlib.sha256(Path(a['path']).read_bytes()).hexdigest()!=a['sha256'] for a in metadata)):
        # Receipt provenance is trusted service state; only author-owned
        # worktree/evidence drift is repairable as a fresh-input rejection.
        if receipt_hash!=bundle['config_provenance']['receipt_sha256']:
            raise RuntimeError('trusted publication receipt changed before admission')
        raise workflow_preflight.PreflightFailure('FAIL','SOURCE_DRIFT','source_manifest',
            'Worktree or staged author evidence changed before admission.',
            'Stop edits and regenerate the source manifest/digest and evidence from stable inputs; register a fresh scope and submit with a fresh key.')
    for artifact in metadata:
        workflow_runtime.check_operation_deadline(); store.register_artifact(artifact,input_digest=req['input_digest'])
    workflow_runtime.check_operation_deadline()
    seed=hashlib.sha256((caller+'\0'+idempotency_key+'\0'+req_digest).encode()).hexdigest()
    runid='run_'+seed[:24]; taskid='task_'+seed[:24]; eventid='event_'+seed[:24]
    run={'contract_version':1,'run_id':runid,'sprint_id':req['sprint_id'],'run_kind':run_kind,
         'input_digest':req['input_digest'],'plan_digest':req['plan_digest'],'policy_digest':req['policy_digest'],
         'author':req['author'],'current_attempt':1,'sequence':0}
    task={'contract_version':1,'task_id':taskid,'run_id':runid,'evaluation_generation':1,'kind':'CHECK',
          'dependency_ids':[],'input_digest':req['input_digest'],'state':'QUEUED','assignment_generation':1,
          'assigned_session_id':None,'deadline_ms':int(time.time()*1000)+3600000,'result':None}
    event={'contract_version':1,'event_id':eventid,'kind':'TASK_READY','run_id':runid,
           'evaluation_generation':1,'task_id':taskid,'assignment_generation':1,'input_digest':req['input_digest']}
    return store.submit(caller=caller,request=req,run=run,first_task=task,outbox_event=event,
        logical_task_key='preflight:code_checks',replace_head_run_id=replace_head_run_id)


def _context(raw):
    required={'repo','authenticated_author','policy','artifacts','publication_receipt_path','publication_receipt_sha256'}
    if not isinstance(raw,dict) or set(raw)!=required: raise ValueError('context fields must be exactly: '+', '.join(sorted(required)))
    return workflow_preflight.PreflightContext(Path(raw['repo']),raw['authenticated_author'],raw['policy'],raw['artifacts'],Path(raw['publication_receipt_path']),raw['publication_receipt_sha256'])


def main(argv=None):
    parser=argparse.ArgumentParser(); sub=parser.add_subparsers(dest='command',required=True)
    for command in ('check','submit'):
        p=sub.add_parser(command); p.add_argument('--request',required=True); p.add_argument('--context',required=True); p.add_argument('--caller',required=True); p.add_argument('--key',required=True)
        if command=='submit': p.add_argument('--database',required=True); p.add_argument('--artifact-dir',required=True)
    args=parser.parse_args(argv)
    try:
        request=Path(args.request).read_text(encoding='utf-8'); context=_context(workflow_store.strict_loads(Path(args.context).read_text(encoding='utf-8')))
        if args.command=='check':
            parsed=_strict_request(request)
            if parsed.get('idempotency_key')!=args.key: raise workflow_store.ProtocolError('request idempotency_key does not match --key')
            try:
                bundle=workflow_preflight.preflight(parsed,caller=args.caller,context=context)
                print(json.dumps({'ready_for_admission':True,'bundle':bundle},sort_keys=True))
            except workflow_preflight.PreflightFailure as exc:
                print(json.dumps({'ready_for_admission':False,'diagnostic':{'verdict':exc.verdict,'code':exc.code,'field':exc.field,'message':exc.message,'required_change':exc.required_change}},sort_keys=True))
            return 0
        response=submit_submission(workflow_store.WorkflowStore(args.database),caller=args.caller,idempotency_key=args.key,request=request,context=context,artifact_dir=args.artifact_dir)
        bundle_path=None
        if response['status']=='ACCEPTED':
            for item in workflow_store.WorkflowStore(args.database).submission_artifacts(caller=args.caller,idempotency_key=args.key):
                artifact=item['artifact']
                if artifact['type']=='PROVENANCE' and Path(artifact['path']).name=='preflight-bundle.json': bundle_path=artifact['path']
        print(json.dumps({'response':response,'bundle_path':bundle_path},sort_keys=True)); return 0
    except Exception as exc:
        print(json.dumps({'error':str(exc)},sort_keys=True)); return 2


if __name__=='__main__': raise SystemExit(main())
