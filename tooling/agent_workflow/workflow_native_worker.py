"""Read-only resolution of the frozen inputs for an admitted native task."""
import hashlib
import json
import os
import argparse
import sys
import time
import subprocess
from pathlib import Path

try:
    import workflow_preflight
    import workflow_runtime
    import workflow_store
    import workflow_submission
    import gate_execution
    import workflow_regression
    import workflow_task_artifacts
    import workflow_toolchain
    import workflow_native_fixtures
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_preflight, workflow_runtime, workflow_store, workflow_submission, gate_execution, workflow_regression, workflow_task_artifacts, workflow_toolchain, workflow_native_fixtures


def load_native_inputs(store, run_id, *, verify_current_source=True):
    """Load integrity-checked frozen inputs, optionally requiring the live source snapshot."""
    run=store.get_run(run_id)
    if run and run['run'].get('run_kind')=='POST_ACCEPTANCE_REGRESSION':
        return _load_regression_inputs(store,run_id,run['run'])
    saved=store.get_run_submission(run_id)
    if saved is None or saved.get('response',{}).get('status')!='ACCEPTED':
        raise ValueError('run has no accepted submission')
    request=saved['request']
    verified_files={}
    for binding in saved['artifacts']:
        artifact=binding['artifact']
        path=Path(artifact['path'])
        data=path.read_bytes()
        if len(data)!=artifact['size_bytes'] or hashlib.sha256(data).hexdigest()!=artifact['sha256']:
            raise ValueError('frozen submission artifact integrity mismatch')
        verified_files[artifact.get('artifact_id')]=data
    artifact=resolve_generated_bundle(saved)
    data=verified_files[artifact['artifact_id']]
    bundle=workflow_store.strict_loads(data.decode('utf-8'))
    schema=workflow_preflight._PREFLIGHT_SCHEMA
    workflow_preflight.Draft202012Validator({'$schema':schema['$schema'],'$defs':schema['$defs'],'$ref':'#/$defs/bundle'}).validate(bundle)
    if (workflow_store.digest(bundle['source_manifest'])!=request['input_digest'] or
        workflow_store.digest(bundle['resolved_plan'])!=request['plan_digest'] or
        workflow_store.digest(bundle['review_policy'])!=request['policy_digest'] or
        bundle['request_digest']!=workflow_store.digest(request)):
        raise ValueError('frozen bundle digest does not match accepted request')
    manifest=resolve_generated_manifest(saved, bundle)
    manifest_bytes=Path(manifest['path']).read_bytes()
    if (len(manifest_bytes)!=manifest['size_bytes'] or hashlib.sha256(manifest_bytes).hexdigest()!=manifest['sha256'] or
        manifest_bytes!=workflow_store.canonical_json(bundle['source_manifest']).encode('utf-8')):
        raise ValueError('frozen source manifest integrity mismatch')
    if verify_current_source:
        live=workflow_preflight.capture_source(Path(request['source_worktree']))
        if live!=bundle['source_manifest']: raise ValueError('current source differs from admitted snapshot')
    return request,bundle


def resolve_generated_manifest(saved, bundle):
    """Resolve the generated binding, not an author attachment with a colliding name."""
    manifest_candidates=[]
    expected_manifest=workflow_store.canonical_json(bundle['source_manifest']).encode('utf-8')
    expected_manifest_hash=hashlib.sha256(expected_manifest).hexdigest()
    for binding in saved['artifacts']:
        candidate=binding['artifact']
        if (candidate['type']=='SOURCE_MANIFEST' and candidate.get('producer_kind')=='SUBMISSION' and
                candidate.get('producer_id')==saved['submission_id'] and candidate.get('submission_id')==saved['submission_id'] and
                candidate.get('artifact_id')==workflow_submission._generated_artifact_id(saved['submission_id'],expected_manifest_hash,'SOURCE_MANIFEST') and
                Path(candidate['path']).name=='source-manifest.json'):
            manifest_candidates.append(candidate)
    if len(manifest_candidates)!=1: raise ValueError('accepted generated source manifest is missing or ambiguous')
    return manifest_candidates[0]


def resolve_generated_bundle(saved):
    """Resolve the accepted submission's generated preflight bundle binding."""
    candidates=[]
    for binding in saved['artifacts']:
        artifact=binding['artifact']
        if (artifact.get('type')=='PROVENANCE' and artifact.get('producer_kind')=='SUBMISSION' and
                artifact.get('producer_id')==saved['submission_id'] and artifact.get('submission_id')==saved['submission_id'] and
                artifact.get('artifact_id')==workflow_submission._generated_artifact_id(saved['submission_id'],artifact['sha256'],artifact['type']) and
                Path(artifact['path']).name=='preflight-bundle.json'):
            candidates.append(artifact)
    if len(candidates)!=1:
        raise ValueError('accepted generated preflight bundle is missing or ambiguous')
    return candidates[0]


def _load_regression_inputs(store, run_id, run):
    saved=store.get_regression_for_run(run_id)
    if not saved: raise ValueError('regression run has no accepted owner inputs')
    inputs=saved['inputs']; acceptance=saved['acceptance']; job=saved['job']
    if (run.get('owner_acceptance_id')!=saved['acceptance_id'] or
        run.get('input_digest')!=acceptance['input_digest'] or
        inputs.get('source_input_digest')!=acceptance['input_digest'] or
        inputs.get('accepted_feature_run_id')!=acceptance['run_id']):
        raise ValueError('regression inputs differ from accepted source identity')
    root=Path(inputs['snapshot_root']).resolve()
    manifest=inputs['source_manifest']
    if workflow_store.digest(manifest)!=acceptance['input_digest']:
        raise ValueError('accepted source manifest digest mismatch')
    if run.get('plan_digest')!=inputs['plan_digest'] or run.get('policy_digest')!=inputs['policy_digest']:
        raise ValueError('regression plan or policy digest mismatch')
    workflow_regression.verify_snapshot_files(root,manifest)
    archive=store.get_artifact(inputs['source_snapshot_ref'])
    if not archive or archive.get('artifact',{}).get('type')!='PROVENANCE' or Path(archive.get('artifact',{}).get('path','')).name!='source-snapshot.zip':
        raise ValueError('accepted source archive is missing')
    a=archive['artifact']; data=Path(a['path']).read_bytes()
    if len(data)!=a['size_bytes'] or hashlib.sha256(data).hexdigest()!=a['sha256']:
        raise ValueError('accepted source archive integrity mismatch')
    request={'source_worktree':str(root),'input_digest':acceptance['input_digest'],
        'plan_digest':inputs['plan_digest'],'policy_digest':inputs['policy_digest']}
    bundle={'source_manifest':manifest,'resolved_plan':{'registry_version':1,'packs':[],
        'plan':inputs['plan'],'program_checks':['accepted_snapshot_provenance']},
        'config_provenance':None,'review_policy':{'review_roles':[]}}
    return request,bundle


def _verify_regression_files(root, manifest):
    workflow_regression.verify_snapshot_files(root,manifest)


def _preflight_fixture_bindings(plan, execution_plan, input_digest, *, repo=None,
                                regression_context=None, retry_bindings=None):
    """Reject invalid fixture attribution before any native check is launched."""
    retry_bindings=retry_bindings or {}
    for index, accepted_check in enumerate(plan['checks']):
        workflow_native_fixtures.accepted_binding(
            accepted_check,input_digest,index,regression_context=regression_context)
    expected=plan
    if retry_bindings:
        if any(binding.get('policy_id') != workflow_native_fixtures.POLICY_ID or
               binding.get('mode') not in ('FRESH_RETRY', 'FRESH_INITIAL') for binding in retry_bindings.values()):
            raise ValueError('native retry bindings contain an unsupported execution policy')
        expected=workflow_native_fixtures.adapt_plan(expected,retry_bindings.values())
    managed=(workflow_native_fixtures.package_execution_bindings(plan['checks'])+
             workflow_native_fixtures.managed_execution_bindings(plan['checks']))
    if managed:
        expected=workflow_native_fixtures.adapt_plan(expected,managed)
    if execution_plan != expected:
        raise ValueError('native execution plan differs from the authorized managed adaptation')


def native_process_identity(pid):
    """Translate gate_execution's exact OS identity into the WR01 millisecond field."""
    raw=gate_execution.process_identity(pid)
    if raw is None: return None
    created=raw['created']
    if isinstance(created,int):
        # Windows FILETIME ticks are since 1601; WR01 stores Unix epoch milliseconds.
        created_ms=(created-116444736000000000)//10000 if os.name=='nt' else created//10000
    else:
        # Linux /proc starttime is a boot-relative clock tick. Keep a stable integer
        # representation; process identity comparisons always use this same mapping.
        created_ms=int(created)
    return {'pid':int(raw['pid']),'created_ms':created_ms}


def native_identity_state(saved):
    if not isinstance(saved,dict): return 'unknown'
    try: current=native_process_identity(saved.get('pid'))
    except (OSError,PermissionError): return 'unknown'
    if current is None: return 'dead'
    return 'alive' if current==saved else 'dead'


def execute_native_task(store, *, task_id, process_identity, claim_token,
                        artifact_dir, lock_dir, notify_committed=None):
    task=store.get_task(task_id)
    if task is None: raise ValueError('unknown task')
    claim=store.get_native_claim(task_id)
    if claim is None or claim!={'process_identity':process_identity,'claim_token':claim_token}:
        raise ValueError('native dispatch identity/token mismatch')
    if task['state']=='COMPLETED': return task['result']
    if task['state']!='DISPATCHING': raise ValueError('native task is not dispatched')
    if native_process_identity(os.getpid())!=process_identity:
        raise ValueError('native process identity does not match the saved dispatch identity')
    store.claim_native_task(task_id=task_id,run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
        assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],
        process_identity=process_identity,claim_token=claim_token)
    lock=workflow_runtime.gate_lock(Path(lock_dir)); resource=None; children=[]; refs=[]
    root=Path(artifact_dir)/task_id/'logs'
    try:
        actual_worker=gate_execution.process_identity(os.getpid())
        root.mkdir(parents=True,exist_ok=True)
    except Exception:
        lock.close(); raise
    refs=[]
    completed_check_index=0
    check_toolchains=[]
    execution_plan=None
    runtime_fixture_bindings={}
    regression_fixture_context=None
    def freeze(path,digest,size,kind):
        data=Path(path).read_bytes()
        if hashlib.sha256(data).hexdigest()!=digest or len(data)!=size: raise ValueError('typed evidence changed before freezing')
        aid,frozen=workflow_task_artifacts.register_bytes(store,task,Path(artifact_dir),kind,data)
        return aid,str(frozen)
    def completed(record):
        nonlocal completed_check_index
        check_index=completed_check_index
        completed_check_index+=1
        original_log=record['log']
        ref,frozen_log=freeze(original_log,record['log_sha256'],Path(original_log).stat().st_size,'LOG')
        refs.append(ref); record['source_log']=original_log; record['log']=frozen_log
        normalized=[]
        for evidence in record['evidence']['artifacts']:
            suffix=Path(evidence['path']).suffix.lower()
            kind='BUILD' if suffix in ('.jar','.zip') else 'PROVENANCE'
            artifact_ref,frozen_path=freeze(evidence['path'],evidence['sha256'],evidence['size'],kind)
            refs.append(artifact_ref)
            normalized.append(dict(evidence,source_path=evidence['path'],path=frozen_path,artifact_ref=artifact_ref))
        record['evidence']['artifacts']=normalized
        accepted_plan=bundle['resolved_plan']['plan']
        execution_check=execution_plan['checks'][check_index]
        retry_binding=runtime_fixture_bindings.get(check_index)
        runtime_fixture=workflow_native_fixtures.execution_binding(
            accepted_plan['checks'][check_index],
            request['input_digest'],check_index,record,retry_binding,
            repo=repo,regression_context=regression_fixture_context)
        provenance={'kind':'NATIVE_CHECK_COMPLETED','task_id':task_id,'run_id':task['run_id'],
            'evaluation_generation':task['evaluation_generation'],'assignment_generation':task['assignment_generation'],
            'input_digest':task['input_digest'],'plan_digest':request['plan_digest'],'policy_digest':request['policy_digest'],
             'check_index':check_index,'check_digest':workflow_store.digest(accepted_plan['checks'][check_index]),
             'execution_check_digest':workflow_store.digest(execution_check),'runtime_fixture':runtime_fixture,
             'toolchain':check_toolchains[check_index],
            'record':{k:v for k,v in record.items() if k not in ('started','finished','startup_seconds')}}
        data=workflow_store.canonical_json(provenance).encode('utf-8')
        artifact_ref,_=workflow_task_artifacts.register_bytes(store,task,Path(artifact_dir),'PROVENANCE',data)
        refs.append(artifact_ref)
        store.record_native_check_completed(task_id,check_index,artifact_ref)
    try:
        resource=store.acquire_resource(resource_key='native_validation',run_id=task['run_id'],generation=task['evaluation_generation'],
            task_id=task_id,assignment_generation=task['assignment_generation'],identity_proof=process_identity)
        request,bundle=load_native_inputs(store,task['run_id'])
        saved_run=store.get_run(task['run_id'])
        if saved_run['run'].get('run_kind')=='POST_ACCEPTANCE_REGRESSION':
            accepted=store.get_regression_for_run(task['run_id'])
            if (not accepted or saved_run['run'].get('owner_acceptance_id')!=accepted.get('acceptance_id') or
                    accepted.get('inputs',{}).get('acceptance_id')!=accepted.get('acceptance_id') or
                    accepted.get('inputs',{}).get('source_input_digest')!=request.get('input_digest') or
                    accepted.get('acceptance',{}).get('input_digest')!=request.get('input_digest')):
                raise ValueError('saved regression fixture has no matching owner acceptance binding')
            regression_fixture_context={'run_kind':'POST_ACCEPTANCE_REGRESSION','run_id':task['run_id'],
                'acceptance_id':accepted['acceptance_id'],'input_digest':request['input_digest']}
        plan=bundle['resolved_plan']['plan']; repo=Path(request['source_worktree'])
        allowed={'accepted_snapshot_provenance'} if store.get_run(task['run_id'])['run'].get('run_kind')=='POST_ACCEPTANCE_REGRESSION' else {'managed_config_provenance'}
        if any(check not in allowed for check in bundle['resolved_plan']['program_checks']):
            raise RuntimeError('unsupported frozen program check')
        _verify_execution_inputs(request,bundle)
        completed_prefix=[]
        recovery_links=[]
        execution_plan=plan
        retry_fixture_bindings={}
        recovery_metadata=store.get_recovery_metadata(task['run_id'],task['evaluation_generation'])
        if recovery_metadata and recovery_metadata.get('boundary')=='native:'+task.get('logical_key',''):
            try:
                try: import workflow_native_retry
                except ModuleNotFoundError: from tooling.agent_workflow import workflow_native_retry
                prefix=workflow_native_retry.load_recovery_prefix(store,task,request,bundle)
            except Exception as exc:
                raise RuntimeError('NATIVE_RECOVERY_PROOF_INVALID: '+str(exc)) from exc
            completed_prefix=prefix['records']; recovery_links=prefix['source_links']
            runtime_fixture_bindings={binding['check_index']:binding for binding in prefix['runtime_fixtures']}
            retry_fixture_bindings=dict(runtime_fixture_bindings)
            execution_plan=workflow_native_fixtures.adapt_plan(plan,runtime_fixture_bindings.values())
            check_toolchains=list(prefix['toolchain'])
            refs.extend(link['artifact_ref'] for link in recovery_links)
            refs.extend(e.get('artifact_ref') for record in completed_prefix
                        for e in record.get('evidence',{}).get('artifacts',[]) if e.get('artifact_ref'))
            completed_check_index=len(completed_prefix)
        package_bindings=workflow_native_fixtures.package_execution_bindings(plan['checks'])
        unit_bindings=workflow_native_fixtures.managed_execution_bindings(plan['checks'])
        initial_bindings=[]
        if not recovery_metadata and regression_fixture_context is None:
            initial_bindings=workflow_native_fixtures.derive_initial_bindings(plan['checks'],
                input_digest=request['input_digest'],run_id=task['run_id'],
                generation=task['evaluation_generation'],task_id=task_id)
            workflow_native_fixtures.reserve_retry_bindings(repo,initial_bindings)
        all_bindings=package_bindings+unit_bindings+initial_bindings
        if all_bindings:
            execution_plan=workflow_native_fixtures.adapt_plan(execution_plan,all_bindings)
            runtime_fixture_bindings.update({binding['check_index']:binding for binding in all_bindings})
            if initial_bindings:
                retry_fixture_bindings.update({binding['check_index']:binding for binding in initial_bindings})
        # Validate every accepted fixture before the runtime can launch even the
        # first child. Completion-time provenance checks are too late for this.
        _preflight_fixture_bindings(plan,execution_plan,request['input_digest'],
            repo=repo,regression_context=regression_fixture_context,retry_bindings=retry_fixture_bindings)
        if not actual_worker: raise RuntimeError('worker OS identity unavailable')
        def started(proc,check,log):
            child={'proc':proc,'identity':None,'exited_before_registration':False,'exit_code':None}
            children.append(child)
            identity=gate_execution.process_identity(proc.pid)
            if not identity:
                # The exact Popen handle is authoritative if it already reaped this child;
                # an absent OS identity while it is still running remains unknown.
                exit_code=proc.poll()
                if exit_code is None: raise RuntimeError('child OS identity unavailable')
                child.update(exited_before_registration=True,exit_code=exit_code)
                return
            child['identity']=identity
            raw=workflow_store.canonical_json({'worker':actual_worker,'child':identity,'task_id':task_id}).encode()
            p=workflow_submission._write_immutable(Path(artifact_dir)/'immutable'/task_id/(hashlib.sha256(raw).hexdigest()+'-child.json'),raw)
            refs.append(freeze(p,hashlib.sha256(raw).hexdigest(),len(raw),'PROVENANCE')[0])
        def verify_check_inputs():
            index=completed_check_index
            if len(check_toolchains)<=index:
                check_toolchains.append(workflow_toolchain.capture(execution_plan['checks'][index],repo))
            _verify_execution_inputs(request,bundle)
        records=workflow_runtime.run_finite_checks(repo,execution_plan,root,completed_prefix=completed_prefix,
            verify_inputs=verify_check_inputs,record_completed=completed,child_started=started)
        passed=len(records)==len(plan['checks']) and all(r['exit_code']==0 and r['evidence']['status']=='PASS' for r in records)
        verified_nonzero=any(r['exit_code'] not in (0,124,127) for r in records)
        proven_failure=any(r['evidence']['status']=='FAIL' and
            (r['evidence']['counts'].get('failed',0) or r['evidence']['counts'].get('errors',0)) for r in records)
        if any(r['exit_code']==127 for r in records): decision='ERROR'; code='SPAWN_FAILED'
        elif any(r['exit_code']==124 for r in records): decision='ERROR'; code='TIMEOUT'
        elif verified_nonzero or proven_failure: decision='FAIL'; code='CHECK_FAILED'
        elif len(records)!=len(plan['checks']) or any(r['evidence']['status']=='UNVERIFIED' for r in records): decision='ERROR'; code='EVIDENCE_UNVERIFIED'
        elif not passed: decision='FAIL'; code='CHECK_FAILED'
        else: decision='PASS'; code=None
        if decision == 'PASS' and store.get_run(task['run_id'])['run'].get('run_kind') == 'POST_ACCEPTANCE_REGRESSION':
            full = all(r.get('evidence', {}).get('selection') == 'full' and
                isinstance(r.get('evidence', {}).get('counts', {}).get('selected'), int) and
                r['evidence']['counts']['selected'] > 0 and
                r['evidence']['counts'].get('selected') == r['evidence']['counts'].get('total') and
                not any(r['evidence']['counts'].get(k, 0) for k in ('failed', 'errors', 'skipped'))
                for r in records)
            if not full: decision='ERROR'; code='FULL_REGRESSION_EVIDENCE_REQUIRED'
    except Exception as exc:
        records=[]; decision='ERROR'; code='NATIVE_RECOVERY_PROOF_INVALID' if str(exc).startswith('NATIVE_RECOVERY_PROOF_INVALID:') else 'NATIVE_EXECUTION_ERROR'; message=str(exc)[:500]
    scope_proof=None; scope_error=None
    saved_scope=store.get_native_scope(task_id)
    if saved_scope is not None:
        try:
            try: import workflow_process_scope
            except ModuleNotFoundError: from tooling.agent_workflow import workflow_process_scope
            owned=workflow_process_scope.OwnedProcessScope.open_existing(saved_scope['scope_name'])
            if owned is None: raise RuntimeError('saved native Job Object cannot be opened')
            try:
                raw=gate_execution.process_identity(os.getpid())
                if raw!=saved_scope['worker_identity'] or not owned.contains(raw): raise RuntimeError('worker is not the exact saved Job Object member')
                end=time.monotonic()+5; snapshot=owned.snapshot()
                while snapshot['active_processes']!=1 and time.monotonic()<end:
                    time.sleep(.025); snapshot=owned.snapshot()
                if snapshot['active_processes']!=1: raise RuntimeError('native Job Object still has other active processes: '+workflow_store.canonical_json(snapshot))
                scope_proof={'scope_name':saved_scope['scope_name'],'worker_identity':raw,'active_processes':1,'snapshot':snapshot,'method':'OWNED_JOB_EXACT_WORKER_ONLY'}
            finally: owned.close()
        except Exception as exc: scope_error=str(exc)[:500]
        if scope_error: decision='ERROR'; code='CLEANUP_UNKNOWN'; message='Native process scope cleanup is unresolved: '+scope_error
    settled=scope_error is None and all(child['proc'].poll() is not None and
        (child['exited_before_registration'] or
         (child['identity'] is not None and gate_execution.identity_state(child['identity'])=='dead'))
        for child in children)
    cleanup_error=None
    try:
        if resource:
            if settled and (saved_scope is None or scope_proof is not None):
                proof_data=workflow_store.canonical_json({'resource_claim':resource['claim_id'],'worker_identity':actual_worker,
                    'children':[({'identity':child['identity'],'state':'dead'} if child['identity'] is not None else
                                 {'pid':child['proc'].pid,'state':'exited','exit_code':child['exit_code'],'proof':'owned_popen_exit'})
                                 for child in children],'no_live_owned_children':True,'native_scope':scope_proof}).encode()
                proof_path=workflow_submission._write_immutable(Path(artifact_dir)/'immutable'/task_id/(hashlib.sha256(proof_data).hexdigest()+'-cleanup.json'),proof_data)
                proof=freeze(proof_path,hashlib.sha256(proof_data).hexdigest(),len(proof_data),'PROVENANCE')[0]
                store.release_resource('native_validation',claim_id=resource['claim_id'],run_id=task['run_id'],generation=task['evaluation_generation'],
                    task_id=task_id,assignment_generation=task['assignment_generation'],identity_proof=process_identity,cleanup_proof=proof)
                refs.append(proof)
            else: store.mark_resource_unknown(resource['claim_id'],identity_proof=process_identity)
    except Exception as exc:
        cleanup_error=str(exc)[:300]
        try: store.mark_resource_unknown(resource['claim_id'],identity_proof=process_identity)
        except Exception: pass
    finally:
        lock.close()
    if cleanup_error:
        decision='ERROR'; code='RESOURCE_CLEANUP_FAILED'; message=cleanup_error
    if not settled:
        decision='ERROR'; code='CLEANUP_UNKNOWN'; message='Owned child cleanup could not be verified.'
    if decision=='FAIL':
        refs_log=refs[:]
        record=next((r for r in records if r['exit_code'] or r['evidence']['status']!='PASS'),records[-1])
        finding={'criterion_id':record['covers'][0],'category':'COVERAGE',
            'message':'; '.join(record['evidence']['findings']) or f"Check exited with {record['exit_code']}",
            'location':record['log'],'evidence_refs':refs_log,
            'required_change':'Resolve the evidenced validation failure and rerun the focused check.'}
        saved=store.complete_native_task(task_id=task_id,run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
            assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],process_identity=process_identity,
            claim_token=claim_token,outcome={'decision':'FAIL','findings':[finding],'artifact_refs':refs_log})
        result=store.get_task(task_id)['result']
        if notify_committed: notify_committed(result)
        return result
    if decision!='PASS':
        error_data=workflow_store.canonical_json({'task_id':task_id,'code':code,'message':locals().get('message','Native validation did not produce complete typed PASS evidence'),
            'checks':[{k:v for k,v in r.items() if k not in ('started','finished','startup_seconds')} for r in records]}).encode()
        ep=workflow_submission._write_immutable(Path(artifact_dir)/'immutable'/task_id/(hashlib.sha256(error_data).hexdigest()+'-error.json'),error_data)
        error_ref=freeze(ep,hashlib.sha256(error_data).hexdigest(),len(error_data),'PROVENANCE')[0]; refs.append(error_ref)
        report={'contract_version':1,'task_id':task_id,'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
            'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],'error_code':code,
            'message':locals().get('message','Native validation did not produce complete typed PASS evidence'),'artifact_refs':[error_ref]}
        incident_id='incident_native_'+hashlib.sha256((task_id+code).encode()).hexdigest()[:20]
        incident={'contract_version':1,'incident_id':incident_id,'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
            'task_id':task_id,'code':code,'message':report['message'],'recovery_owner':'SYSTEM','state':'OPEN',
            'owner_action_required':False,'artifact_refs':[error_ref]}
        store.record_incident(incident)
        result=store.complete_native_task(task_id=task_id,run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
            assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],process_identity=process_identity,
            claim_token=claim_token,outcome={'decision':'ERROR','findings':[],'artifact_refs':refs},error_report=report)
        saved=store.get_task(task_id)['result']
        if notify_committed: notify_committed(saved)
        return saved
    summary_records=[]
    for record in records:
        # The strict protocol JSON codec excludes floating point timing telemetry.
        summary_records.append({k:v for k,v in record.items() if k not in ('started','finished','startup_seconds')})
    summary={'task_id':task_id,'run_id':task['run_id'],'input_digest':task['input_digest'],
         'plan_digest':request['plan_digest'],'policy_digest':request['policy_digest'],'checks':summary_records}
    if recovery_links: summary['reused_check_sources']=recovery_links
    data=workflow_store.canonical_json(summary).encode('utf-8')
    path=workflow_submission._write_immutable(Path(artifact_dir)/'immutable'/task_id/(hashlib.sha256(data).hexdigest()+'-check-receipt.json'),data)
    receipt={'contract_version':1,'artifact_id':'artifact_'+task_id+'_receipt_'+hashlib.sha256(data).hexdigest()[:20],
        'run_id':task['run_id'],'producer_task_id':task_id,'type':'CHECK_RECEIPT','path':str(path),
        'sha256':hashlib.sha256(data).hexdigest(),'size_bytes':len(data),'immutable':True,
        'producer_kind':'TASK','producer_id':task_id}
    refs.append(store.register_artifact(receipt,input_digest=task['input_digest'])['artifact_id'])
    result=store.complete_native_task(task_id=task_id,run_id=task['run_id'],evaluation_generation=task['evaluation_generation'],
        assignment_generation=task['assignment_generation'],input_digest=task['input_digest'],process_identity=process_identity,
        claim_token=claim_token,outcome={'decision':'PASS','findings':[],'artifact_refs':refs})
    saved_result=store.get_task(task_id)['result']
    if notify_committed: notify_committed(saved_result)
    return saved_result


def _verify_current_source(repo,manifest):
    if workflow_preflight.capture_source(repo)!=manifest: raise RuntimeError('current source differs from admitted snapshot')


def _verify_execution_inputs(request,bundle):
    if bundle['resolved_plan']['program_checks']==['accepted_snapshot_provenance']:
        _verify_regression_files(Path(request['source_worktree']),bundle['source_manifest'])
        return
    repo=Path(request['source_worktree'])
    _verify_current_source(repo,bundle['source_manifest'])
    config=bundle['config_provenance']
    receipt=Path(config['receipt_path'])
    data=receipt.read_bytes()
    if hashlib.sha256(data).hexdigest()!=config['receipt_sha256']:
        raise RuntimeError('pinned publication receipt changed after admission')
    parsed=workflow_store.strict_loads(data.decode('utf-8'))
    rows=[row for row in parsed['files'] if row.get('target')==config['target'] and row.get('path')=='opencode.jsonc']
    if len(rows)!=1 or rows[0].get('deleted') is True:
        raise RuntimeError('pinned publication receipt has no unique non-deleted exact config row')
    path=repo/rows[0]['path']
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=config['actual_sha256']:
        raise RuntimeError('managed configuration changed after admission')


def main(argv=None):
    parser=argparse.ArgumentParser()
    sub=parser.add_subparsers(dest='command',required=True)
    run=sub.add_parser('run')
    for name in ('database','task-id','claim-token','artifact-dir','lock-dir','ready-path'): run.add_argument('--'+name,required=True)
    args=parser.parse_args(argv)
    def stage(name):
        print('native-worker-stage:'+name,flush=True)
    stage('cli-enter')
    store=workflow_store.WorkflowStore(args.database)
    identity=native_process_identity(os.getpid())
    stage('identity-ready')
    print('native-worker-identity:'+json.dumps(identity,sort_keys=True),flush=True)
    ready={'identity':identity,'token_digest':hashlib.sha256(args.claim_token.encode()).hexdigest()}
    ready_bytes=workflow_store.canonical_json(ready).encode('utf-8')
    ready_path=Path(args.ready_path)
    workflow_submission._write_immutable(ready_path,ready_bytes)
    stage('startup-ack')
    deadline=time.monotonic()+10
    while time.monotonic()<deadline:
        claim=store.get_native_claim(args.task_id)
        if claim and claim.get('process_identity')!=identity:
            print('native-worker-identity-mismatch:'+json.dumps({'saved':claim.get('process_identity'),'current':identity},sort_keys=True),flush=True)
        if claim and claim.get('process_identity')==identity and claim.get('claim_token')==args.claim_token:
            stage('claim-matched')
        if claim and claim.get('process_identity')==identity and claim.get('claim_token')==args.claim_token:
            task=store.get_task(args.task_id)
            if task and task['state']=='DISPATCHING':
                saved_scope=store.get_native_scope(args.task_id)
                if saved_scope is None:
                    print('native-worker-scope-missing:'+args.task_id,flush=True)
                    return 3
                try:
                    try: import workflow_process_scope
                    except ModuleNotFoundError: from tooling.agent_workflow import workflow_process_scope
                    raw=gate_execution.process_identity(os.getpid())
                    if raw!=saved_scope['worker_identity']:
                        print('native-worker-scope-identity-mismatch:'+args.task_id,flush=True); return 3
                    owned=workflow_process_scope.OwnedProcessScope.open_existing(saved_scope['scope_name'])
                    if owned is None or not owned.contains(raw):
                        if owned is not None: owned.close()
                        print('native-worker-scope-membership-failed:'+args.task_id,flush=True); return 3
                    owned.close()
                except Exception as exc:
                    print('native-worker-scope-verification-failed:'+str(exc),flush=True)
                    return 3
                stage('execute-enter')
                execute_native_task(store,task_id=args.task_id,process_identity=identity,claim_token=args.claim_token,
                    artifact_dir=args.artifact_dir,lock_dir=args.lock_dir)
                stage('execute-return')
                return 0
            if task and task['state']=='COMPLETED': return 0
        time.sleep(.05)
    return 2


if __name__=='__main__': raise SystemExit(main())
