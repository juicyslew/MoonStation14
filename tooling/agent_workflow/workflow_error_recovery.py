"""Read-only verification of an immutable successful native-check prefix."""
import hashlib
from pathlib import Path

try:
    import workflow_store
    import workflow_toolchain
    import workflow_native_fixtures
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_store, workflow_toolchain, workflow_native_fixtures


def _verify_file(path, sha256, size=None):
    path = Path(path)
    data = path.read_bytes()
    if (size is not None and len(data) != size) or hashlib.sha256(data).hexdigest() != sha256:
        raise ValueError('completed prefix artifact integrity mismatch')
    return data


def verified_prefix(store, task_id, request, bundle):
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
        expected_toolchain = workflow_toolchain.capture(expected_execution_check, Path(request['source_worktree']))
        if recorded_toolchain is None or recorded_toolchain != expected_toolchain:
            raise ValueError('completed prefix toolchain differs from current executable inputs')
        if (checks[expected_index].get('kind') == 'gametest' and
                provenance.get('execution_check_digest') != workflow_store.digest(expected_execution_check)):
            raise ValueError('completed prefix execution check differs from original accepted GameTest')
        if (record.get('exit_code') != 0 or record.get('evidence', {}).get('status') != 'PASS' or
                any(record.get(k) != expected_execution_check.get(k, default)
                    for k, default in (('argv', None), ('kind', 'command'), ('covers', None)))):
            break
        _verify_file(record.get('log'), record.get('log_sha256'))
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
