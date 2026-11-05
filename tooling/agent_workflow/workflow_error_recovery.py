"""Read-only verification of an immutable successful native-check prefix."""
import hashlib
from pathlib import Path

try:
    import workflow_store
    import workflow_toolchain
    import workflow_native_fixtures
    import workflow_task_artifacts
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_store, workflow_toolchain, workflow_native_fixtures, workflow_task_artifacts


def _verify_file(path, sha256, size=None):
    path = Path(path)
    data = path.read_bytes()
    if (size is not None and len(data) != size) or hashlib.sha256(data).hexdigest() != sha256:
        raise ValueError('completed prefix artifact integrity mismatch')
    return data


def verify_registered_native_log(store, *, ref, task_id, run_id, input_digest, log_path, sha256):
    """Verify immutable native LOG registration and bytes against producer identity."""
    binding = store.get_artifact(ref)
    if binding is None:
        raise ValueError('native log artifact is not registered')
    art = binding['artifact']
    if (art.get('type') != 'LOG' or art.get('immutable') is not True or
            art.get('producer_kind') != 'TASK' or art.get('producer_id') != task_id or
            art.get('producer_task_id') != task_id or art.get('run_id') != run_id or
            binding.get('input_digest') != input_digest or art.get('sha256') != sha256):
        raise ValueError('native log artifact producer or input binding mismatch')
    if Path(art['path']).resolve() != Path(log_path).resolve():
        raise ValueError('native log artifact path mismatch')
    _verify_file(art['path'], art['sha256'], art['size_bytes'])
    return art


def resolve_registered_native_log(store, *, task_id, run_id, input_digest, log_path, sha256):
    """Resolve canonical producer LOG ID and verify its immutable registration."""
    ref = workflow_task_artifacts.canonical_artifact_id(task_id, 'LOG', sha256)
    return ref, verify_registered_native_log(store, ref=ref, task_id=task_id,
        run_id=run_id, input_digest=input_digest, log_path=log_path, sha256=sha256)


def verified_prefix(store, task_id, request, bundle, *, evidence_only=False):
    """Return contiguous PASS records, retaining their original provenance links.

    This function performs no writes and deliberately does not revalidate live
    source; callers must first use the accepted-input applicability guard.
    """
    task = store.get_task(task_id)
    if (task is None or task.get('kind') != 'CHECK' or task.get('state') != 'COMPLETED' or
            task.get('result', {}).get('decision') != 'ERROR'):
        raise ValueError('prefix source must be a completed ERROR CHECK task')
    plan = bundle.get('resolved_plan', {}).get('plan', {})
    checks = plan.get('checks')
    if not isinstance(checks, list):
        raise ValueError('accepted full plan is missing checks')
    if (request.get('input_digest') != task.get('input_digest') or
            request.get('plan_digest') != workflow_store.digest(bundle.get('resolved_plan')) or
            request.get('policy_digest') != task.get('policy_digest', request.get('policy_digest'))):
        raise ValueError('accepted request does not match failed task inputs')
    run = store.get_run(task['run_id'])
    if (run is None or run['run'].get('input_digest') != task['input_digest'] or
            run['run'].get('plan_digest') != request['plan_digest'] or
            run['run'].get('policy_digest') != request['policy_digest']):
        raise ValueError('failed task differs from accepted run digests')

    output = []
    links = []
    toolchains = []
    runtime_fixtures = []
    for expected_index, link in enumerate(store.list_native_check_records(task_id)):
        if link.get('check_index') != expected_index:
            break
        binding = store.get_artifact(link['artifact_ref'])
        if binding is None:
            raise ValueError('completed prefix provenance link is missing')
        art = binding['artifact']
        if (art.get('type') != 'PROVENANCE' or art.get('producer_kind') != 'TASK' or
                art.get('producer_task_id') != task_id or art.get('producer_id') != task_id or
                art.get('run_id') != task['run_id'] or binding.get('input_digest') != task['input_digest']):
            raise ValueError('completed prefix provenance is foreign')
        data = _verify_file(art['path'], art['sha256'], art['size_bytes'])
        provenance = workflow_store.strict_loads(data.decode('utf-8'))
        if (provenance.get('kind') != 'NATIVE_CHECK_COMPLETED' or
                provenance.get('task_id') != task_id or provenance.get('run_id') != task['run_id'] or
                provenance.get('evaluation_generation') != task['evaluation_generation'] or
                provenance.get('assignment_generation') != task['assignment_generation'] or
                provenance.get('input_digest') != task['input_digest'] or
                provenance.get('plan_digest') != request['plan_digest'] or
                provenance.get('policy_digest') != request['policy_digest'] or
                provenance.get('check_index') != expected_index or
                provenance.get('check_digest') != workflow_store.digest(checks[expected_index])):
            raise ValueError('completed prefix provenance does not match accepted plan/task')
        record = provenance.get('record')
        if not isinstance(record, dict):
            raise ValueError('completed prefix record is malformed')
        # Only successful records are eligible for reuse. A failed suffix is
        # authenticated by its provenance envelope above, not re-executed.
        if record.get('exit_code') != 0 or not isinstance(record.get('evidence'), dict) or record['evidence'].get('status') != 'PASS':
            break
        if not workflow_native_fixtures.verify_original_execution(
                checks[expected_index], request['input_digest'], expected_index, record,
                provenance.get('runtime_fixture'), request['source_worktree'], run_id=task['run_id'],
                generation=task['evaluation_generation'], task_id=task_id):
            raise ValueError('completed prefix runtime fixture attribution differs from original accepted check')
        expected_execution_check = checks[expected_index]
        runtime = provenance.get('runtime_fixture')
        if isinstance(runtime, dict) and runtime.get('mode') == 'FRESH_INITIAL':
            binding = workflow_native_fixtures.derive_initial_binding(checks[expected_index],
                input_digest=request['input_digest'], run_id=task['run_id'],
                generation=task['evaluation_generation'], task_id=task_id, check_index=expected_index)
            expected_execution_check = workflow_native_fixtures.adapt_plan({'checks':[checks[expected_index]]},
                [dict(binding, check_index=0, check_digest=workflow_store.digest(checks[expected_index]))])['checks'][0]
        recorded_toolchain = provenance.get('toolchain')
        if not evidence_only:
            expected_toolchain = workflow_toolchain.capture(expected_execution_check, Path(request['source_worktree']))
            if recorded_toolchain is None or recorded_toolchain != expected_toolchain:
                raise ValueError('completed prefix toolchain differs from current executable inputs')
        elif not isinstance(recorded_toolchain, dict):
            raise ValueError('completed prefix has no recorded toolchain fingerprint')
        if (checks[expected_index].get('kind') == 'gametest' and
                provenance.get('execution_check_digest') != workflow_store.digest(expected_execution_check)):
            raise ValueError('completed prefix execution check differs from original accepted GameTest')
        if (any(record.get(k) != expected_execution_check.get(k, default)
                    for k, default in (('argv', None), ('kind', 'command'), ('covers', None)))):
            break
        resolve_registered_native_log(store, task_id=task_id, run_id=task['run_id'], input_digest=task['input_digest'],
            log_path=record.get('log'), sha256=record.get('log_sha256'))
        artifacts = record.get('evidence', {}).get('artifacts', [])
        for evidence in artifacts:
            ref = evidence.get('artifact_ref')
            registered = store.get_artifact(ref) if ref else None
            if registered is None:
                raise ValueError('completed prefix evidence artifact is unregistered')
            ea = registered['artifact']
            if (ea.get('producer_kind') != 'TASK' or ea.get('producer_task_id') != task_id or
                    registered.get('input_digest') != task['input_digest']):
                raise ValueError('completed prefix evidence artifact is foreign')
            _verify_file(evidence['path'], evidence['sha256'], evidence['size'])
            _verify_file(ea['path'], ea['sha256'], ea['size_bytes'])
            if evidence['sha256'] != ea['sha256'] or evidence['size'] != ea['size_bytes']:
                raise ValueError('completed prefix evidence artifact binding mismatch')
        output.append(record)
        toolchains.append(recorded_toolchain)
        if isinstance(runtime, dict) and runtime.get('mode') == 'FRESH_INITIAL':
            runtime_fixtures.append(binding)
        links.append({'check_index': expected_index, 'artifact_ref': link['artifact_ref'],
                      'source_task_id': task_id})
    return {'records': output, 'source_links': links, 'toolchain': toolchains,
            'runtime_fixtures': runtime_fixtures}
