"""Fixed-policy, linked retry for narrowly recoverable native CHECK errors."""
import hashlib
from pathlib import Path

try:
    import workflow_error_recovery, workflow_native_fixtures, workflow_native_worker, workflow_store, workflow_task_artifacts
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_error_recovery, workflow_native_fixtures, workflow_native_worker, workflow_store, workflow_task_artifacts


POLICY_ID = 'native-retry-v1'
MAX_RETRIES_PER_BOUNDARY = 1
RETRYABLE_ERRORS = frozenset(('SPAWN_FAILED', 'TIMEOUT', 'NATIVE_WORKER_INTERRUPTED'))
MANIFEST_SELECTION_ERROR = 'frozen source manifest integrity mismatch'
PACKAGE_LAUNCHER_ERROR = 'legacy package executed argv differs from authorized non-destructive build'
STALE_JUNIT_FINDINGS = frozenset(('one or more JUnit XML reports are stale', 'no tests evidenced',
    'expected selected JUnit classes missing from fresh XML'))
KNOWN_REPAIRS = frozenset(('GENERATED_MANIFEST_SELECTION', 'WINDOWS_PACKAGE_LAUNCHER',
                           'STALE_INCREMENTAL_JUNIT'))
REPLAY_STRATEGY = 'REPLAY_ACCEPTED_FOCUSED_PLAN'


def _recovery_prefix(store, source_task, request, bundle, *, reuse_strategy=None, repair_kind=None):
    if reuse_strategy is None:
        return workflow_error_recovery.verified_prefix(store, source_task['task_id'], request, bundle)
    if (reuse_strategy != REPLAY_STRATEGY or source_task.get('evaluation_generation', 0) <= 1 or
            repair_kind not in KNOWN_REPAIRS):
        raise ValueError('retry proof has an unsupported prefix reuse strategy')
    return {'records': [], 'source_links': [], 'toolchain': []}


class NativeRetry:
    def __init__(self, store, artifact_dir):
        self.store = store
        self.artifact_dir = Path(artifact_dir)

    def reconcile(self):
        """Advance eligible current ERROR attempts and repair linked CHECK gaps."""
        summary = {'started': [], 'ensured': [], 'waiting_cleanup': [], 'blocked': []}
        for run_id in self.store.list_runs_by_phase(('DECIDED', 'QUEUED', 'CHECKING')):
            saved = self.store.get_run(run_id)
            if saved is None:
                continue
            run, attempt = saved['run'], saved['attempt']
            generation = run['current_attempt']
            if generation > 1:
                metadata = self.store.get_recovery_metadata(run_id, generation)
                if (metadata and metadata.get('boundary', '').startswith('native:') and
                        attempt.get('phase') in ('QUEUED', 'CHECKING')):
                    try:
                        self.ensure_task(run_id, generation)
                        summary['ensured'].append(run_id)
                    except Exception as exc:
                        self._record_blocked(run, generation, None, exc)
                        summary['blocked'].append(run_id)
                    continue
                if attempt.get('phase') != 'DECIDED' or attempt.get('result') != 'ERROR':
                    continue
            if attempt.get('result') != 'ERROR':
                continue
            if run.get('run_kind') != 'FEATURE_GATE':
                task = next((item for item in self.store.list_tasks(run_id, generation)
                             if item.get('kind') == 'CHECK' and not item.get('reused') and
                             item.get('state') == 'COMPLETED' and
                             item.get('result', {}).get('decision') == 'ERROR'), None)
                if task is None:
                    continue
                self._record_blocked(run, generation, task,
                                     ValueError('automatic native recovery does not support this run kind'))
                summary['blocked'].append(run_id)
                continue
            tasks = self.store.list_tasks(run_id, generation)
            # An ERROR attempt is not necessarily a native CHECK failure: review
            # admission/results can fail after all retained/effective checks PASS.
            # Only a completed ERROR CHECK created in this generation is eligible
            # for native retry policy; reused PASS checks are never retry targets.
            failed_checks = [item for item in tasks if item.get('kind') == 'CHECK' and
                not item.get('reused') and item.get('evaluation_generation') == generation and
                item.get('state') == 'COMPLETED' and item.get('result', {}).get('decision') == 'ERROR']
            if not failed_checks:
                continue
            if not self._cleanup_ready(run_id, tasks):
                summary['waiting_cleanup'].append(run_id)
                continue
            incident_id = self._blocked_incident_id(run_id, generation)
            try:
                existing_block = self.store.get_incident(incident_id)
                if existing_block and not self._cleanup_blocker(existing_block):
                    task = failed_checks[0]
                    incident = self.store.get_task_incident(task['task_id']) if task else None
                    repair = self._known_repair_kind(task, incident) if task else None
                    if repair is None or (generation > 1 and repair in self._used_repairs(run_id, generation)):
                        summary['blocked'].append(run_id)
                        continue
                outcome = self.prepare(run_id)
                summary['started'].append(run_id)
            except Exception as exc:
                task = failed_checks[0]
                self._record_blocked(run, generation, task, exc)
                summary['blocked'].append(run_id)
        return summary

    def _cleanup_ready(self, run_id, tasks):
        if any(task.get('state') not in ('COMPLETED', 'CANCELLED') for task in tasks):
            return False
        all_run_tasks = self.store.list_tasks(run_id)
        if any(resource.get('state') in ('HELD', 'UNKNOWN') for task in all_run_tasks
               for resource in self.store.get_task_resources(task['task_id'])):
            return False
        return not any(scope.get('task', {}).get('run_id') == run_id and scope.get('state') != 'CLEANED'
                       for scope in self.store.list_native_scopes())

    @staticmethod
    def _cleanup_blocker(saved):
        message = str(saved.get('incident', {}).get('message', '')).lower()
        return any(token in message for token in ('unsettled', 'scope', 'cleanup', 'held',
            'unknown resource', 'resource ownership', 'child task'))

    @staticmethod
    def _blocked_incident_id(run_id, generation):
        return 'incident_native_recovery_blocked_' + hashlib.sha256(
            (run_id + ':' + str(generation)).encode()).hexdigest()[:24]

    def _record_blocked(self, run, generation, task, exc):
        run_id = run['run_id']
        incident_id = self._blocked_incident_id(run_id, generation)
        if self.store.get_incident(incident_id):
            return
        reason = f'{type(exc).__name__}: {exc}'[:500]
        provenance = {'kind': 'NATIVE_RECOVERY_BLOCKED', 'run_id': run_id,
            'evaluation_generation': generation, 'reason': reason,
            'next_action': 'Keep this ERROR terminal; resolve the recorded applicability boundary before any owner-authorized recovery.'}
        data = workflow_store.canonical_json(provenance).encode('utf-8')
        ref = None
        if task is not None:
            ref, _ = workflow_task_artifacts.register_bytes(self.store, task, self.artifact_dir,
                                                            'PROVENANCE', data)
        incident = {'contract_version': 1, 'incident_id': incident_id, 'run_id': run_id,
            'evaluation_generation': generation, 'task_id': task.get('task_id') if task else None,
            'code': 'NATIVE_RECOVERY_BLOCKED', 'message': reason, 'recovery_owner': 'SYSTEM',
            'state': 'OPEN', 'owner_action_required': False, 'artifact_refs': [ref] if ref else []}
        self.store.record_incident(incident)

    def prepare(self, run_id):
        saved = self.store.get_run(run_id)
        if saved is None:
            raise ValueError('retry blocked: run is missing')
        run, attempt = saved['run'], saved['attempt']
        old_generation = run['current_attempt']
        # A retry command is deterministic from its proof, so a caller replay after
        # the generation advance returns the linked generation rather than retrying it.
        if old_generation > 1:
            linked = self.store.get_recovery_metadata(run_id, old_generation)
            if (linked and linked.get('boundary', '').startswith('native:') and
                    (attempt.get('phase') in ('QUEUED', 'CHECKING', 'REVIEWING') or
                     attempt.get('result') != 'ERROR')):
                proof = self.store.get_artifact(linked['fresh_input_proof'])
                if proof:
                    value = workflow_store.strict_loads(Path(proof['artifact']['path']).read_text(encoding='utf-8'))
                    if (value.get('kind') == 'NATIVE_RETRY_APPLICABILITY' and
                            value.get('policy_id') == POLICY_ID and
                            value.get('original_generation') == old_generation - 1):
                        task = self.ensure_task(run_id, old_generation)
                        return {'generation': old_generation, 'task': task,
                                'proof_ref': linked['fresh_input_proof'],
                                'source_task_id': value['source_task_id'], 'boundary': linked['boundary']}
            if attempt.get('phase') != 'DECIDED' or attempt.get('result') != 'ERROR':
                raise ValueError('retry blocked: linked native retry is already active')
        if run.get('run_kind') != 'FEATURE_GATE' or attempt.get('phase') != 'DECIDED' or attempt.get('result') != 'ERROR':
            raise ValueError('retry blocked: only a decided ERROR feature run is supported')
        tasks = self.store.list_tasks(run_id, old_generation)
        checks = [task for task in tasks if task.get('kind') == 'CHECK']
        if len(checks) != 1:
            raise ValueError('retry blocked: exactly one native CHECK is required')
        failed = checks[0]
        if failed.get('state') != 'COMPLETED' or failed.get('result', {}).get('decision') != 'ERROR':
            raise ValueError('retry blocked: the sole CHECK is not a completed ERROR')
        if any(task.get('state') not in ('COMPLETED', 'CANCELLED') for task in tasks):
            raise ValueError('retry blocked: attempt has unsettled tasks')
        for scope in self.store.list_native_scopes():
            if scope.get('task', {}).get('run_id') == run_id and scope.get('state') != 'CLEANED':
                raise ValueError('retry blocked: native process scope is not CLEANED')
        if any(resource.get('state') in ('HELD', 'UNKNOWN') for task in tasks
               for resource in self.store.get_task_resources(task['task_id'])):
            raise ValueError('retry blocked: native resource is held or unknown')
        incident = self.store.get_task_incident(failed['task_id'])
        repair_kind = self._known_repair_kind(failed, incident)
        transient = bool(incident and incident.get('code') in RETRYABLE_ERRORS)
        if not incident or (not transient and repair_kind is None):
            raise ValueError('retry blocked: native error is outside the automatic retry allowlist')
        if old_generation > 1:
            if repair_kind is None or repair_kind in self._used_repairs(run_id, old_generation):
                raise ValueError('retry blocked: automatic retry budget for this run is exhausted')
        elif not transient and repair_kind is None:
            raise ValueError('retry blocked: automatic retry budget for this run is exhausted')
        request, bundle = workflow_native_worker.load_native_inputs(self.store, run_id)
        workflow_native_worker._verify_execution_inputs(request, bundle)
        replay_focused_plan = old_generation > 1 and repair_kind in KNOWN_REPAIRS
        if replay_focused_plan:
            # A later linked retry may have records executed under a prior retry's
            # fixture bindings. Do not reinterpret those records as reusable PASSes;
            # replay the accepted focused plan with no prefix instead.
            prefix = _recovery_prefix(self.store, failed, request, bundle,
                reuse_strategy=REPLAY_STRATEGY, repair_kind=repair_kind)
        else:
            prefix = _recovery_prefix(self.store, failed, request, bundle)
            if 'toolchain' not in prefix:
                raise ValueError('retry blocked: verified prefix has no current toolchain fingerprint')
        generation = old_generation + 1
        retry_task_id = self._retry_task_id(run_id, generation, failed['logical_key'])
        runtime_fixtures = workflow_native_fixtures.derive_retry_bindings(
            bundle['resolved_plan']['plan']['checks'], input_digest=request['input_digest'],
            run_id=run_id, generation=generation, task_id=retry_task_id,
            prefix_count=len(prefix['records']))
        metadata = {'kind': 'NATIVE_RETRY_APPLICABILITY', 'policy_id': POLICY_ID,
            'retry_limit': MAX_RETRIES_PER_BOUNDARY, 'run_id': run_id,
            'source_task_id': failed['task_id'], 'logical_key': failed['logical_key'],
            'original_generation': old_generation, 'input_digest': request['input_digest'],
            'plan_digest': request['plan_digest'],
            'policy_digest': request['policy_digest'],
            'source_manifest_digest': workflow_store.digest(bundle['source_manifest']),
            'config_manifest_digest': workflow_store.digest(bundle.get('config_provenance')),
            'verified_prefix': prefix['source_links'], 'toolchain': prefix['toolchain'],
            'runtime_fixture_policy_id': workflow_native_fixtures.POLICY_ID,
            'runtime_fixtures': runtime_fixtures}
        if replay_focused_plan:
            metadata['reuse_strategy'] = REPLAY_STRATEGY
        if repair_kind:
            metadata['repair_kind'] = repair_kind
        data = workflow_store.canonical_json(metadata).encode('utf-8')
        proof_ref, _ = workflow_task_artifacts.register_bytes(self.store, failed, self.artifact_dir, 'PROVENANCE', data)
        event_id = 'event_native_retry_' + hashlib.sha256((run_id + ':' + str(old_generation) + ':' + proof_ref).encode()).hexdigest()[:24]
        boundary = 'native:' + failed['logical_key']
        response = self.store.recover_error(run_id, expected_sequence=run['sequence'],
            authorization={'kind': 'POLICY', 'id': POLICY_ID}, fresh_input_proof=proof_ref,
            boundary=boundary, recovery_event={'event_id': event_id}, reusable_artifact_refs=[],
            command_id='native-retry:' + str(old_generation) + ':' + proof_ref, caller='SYSTEM')
        new_task = self.ensure_task(run_id, response['generation'])
        return {'generation': response['generation'], 'task': new_task,
                'proof_ref': proof_ref, 'source_task_id': failed['task_id'], 'boundary': boundary}

    def _known_repair_kind(self, task, incident):
        """Recognize only the verified pre-execution artifact-selection defect."""
        if incident is None:
            return None
        if self._is_manifest_selection_repair(task, incident):
            return 'GENERATED_MANIFEST_SELECTION'
        if incident.get('code') == 'NATIVE_EXECUTION_ERROR' and incident.get('message') == PACKAGE_LAUNCHER_ERROR:
            return 'WINDOWS_PACKAGE_LAUNCHER'
        if incident.get('code') == 'EVIDENCE_UNVERIFIED' and self._stale_junit_record(task):
            return 'STALE_INCREMENTAL_JUNIT'
        return None

    def _stale_junit_record(self, task):
        links = self.store.list_native_check_records(task['task_id'])
        if len(links) != 1 or links[0].get('check_index') != 0:
            return False
        saved = self.store.get_artifact(links[0]['artifact_ref'])
        if not saved:
            return False
        artifact = saved['artifact']
        path = Path(artifact['path'])
        data = workflow_error_recovery._verify_file(path, artifact['sha256'], artifact['size_bytes'])
        payload = workflow_store.strict_loads(data.decode('utf-8'))
        record = payload.get('record', {})
        counts = record.get('evidence', {}).get('counts', {})
        task_id = task['task_id']
        if (payload.get('kind') != 'NATIVE_CHECK_COMPLETED' or payload.get('task_id') != task_id or
                payload.get('run_id') != task.get('run_id') or
                payload.get('evaluation_generation') != task.get('evaluation_generation') or
                payload.get('assignment_generation') != task.get('assignment_generation') or
                payload.get('input_digest') != task.get('input_digest') or payload.get('check_index') != 0):
            return False
        log = workflow_error_recovery._verify_file(record.get('log'), record.get('log_sha256')).decode('utf-8', errors='replace')
        return (record.get('kind') == 'junit' and record.get('exit_code') == 0 and
                record.get('evidence', {}).get('status') == 'UNVERIFIED' and
                set(record.get('evidence', {}).get('findings', [])) == STALE_JUNIT_FINDINGS and
                all(counts.get(key) == 0 for key in ('tests', 'failed', 'errors', 'skipped')) and
                '> Task :test UP-TO-DATE' in log and 'BUILD SUCCESSFUL' in log)

    def _used_repairs(self, run_id, generation):
        used = set()
        for prior in range(2, generation + 1):
            metadata = self.store.get_recovery_metadata(run_id, prior)
            if (metadata is None or not metadata.get('boundary', '').startswith('native:')):
                raise ValueError('retry blocked: prior native recovery metadata is missing')
            proof = self.store.get_artifact(metadata.get('fresh_input_proof'))
            if proof is None:
                raise ValueError('retry blocked: prior recovery proof is missing')
            artifact = proof['artifact']; data = Path(artifact['path']).read_bytes()
            if len(data) != artifact['size_bytes'] or hashlib.sha256(data).hexdigest() != artifact['sha256']:
                raise ValueError('retry blocked: prior recovery proof integrity mismatch')
            value = workflow_store.strict_loads(data.decode('utf-8'))
            if (value.get('kind') != 'NATIVE_RETRY_APPLICABILITY' or value.get('policy_id') != POLICY_ID or
                    value.get('run_id') != run_id or value.get('original_generation') != prior - 1):
                raise ValueError('retry blocked: prior recovery proof lineage mismatch')
            if value.get('repair_kind') in KNOWN_REPAIRS:
                used.add(value['repair_kind'])
        return used

    def _is_manifest_selection_repair(self, task, incident):
        return (incident is not None and task.get('evaluation_generation') == 1 and
                incident.get('code') == 'NATIVE_EXECUTION_ERROR' and
                incident.get('message') == MANIFEST_SELECTION_ERROR and
                not self.store.list_native_check_records(task['task_id']))

    def ensure_task(self, run_id, generation):
        """Idempotently materialize the fresh CHECK required by a linked retry."""
        metadata = self.store.get_recovery_metadata(run_id, generation)
        if metadata is None or not metadata.get('boundary', '').startswith('native:'):
            raise ValueError('retry CHECK blocked: linked native recovery metadata is missing')
        proof = self.store.get_artifact(metadata['fresh_input_proof'])
        if proof is None:
            raise ValueError('retry CHECK blocked: applicability proof is missing')
        artifact = proof['artifact']
        data = Path(artifact['path']).read_bytes()
        if len(data) != artifact['size_bytes'] or hashlib.sha256(data).hexdigest() != artifact['sha256']:
            raise ValueError('retry CHECK blocked: applicability proof integrity mismatch')
        value = workflow_store.strict_loads(data.decode('utf-8'))
        saved_run = self.store.get_run(run_id)
        if saved_run is None:
            raise ValueError('retry CHECK blocked: linked run is missing')
        if (value.get('kind') != 'NATIVE_RETRY_APPLICABILITY' or value.get('policy_id') != POLICY_ID or
                value.get('retry_limit') != MAX_RETRIES_PER_BOUNDARY or value.get('run_id') != run_id or
                value.get('original_generation') != generation - 1 or
                value.get('input_digest') != saved_run['run'].get('input_digest') or
                value.get('plan_digest') != saved_run['run'].get('plan_digest') or
                value.get('policy_digest') != saved_run['run'].get('policy_digest') or
                value.get('runtime_fixture_policy_id') != workflow_native_fixtures.POLICY_ID):
            raise ValueError('retry CHECK blocked: applicability proof is not bound to this generation')
        source = self.store.get_task(value.get('source_task_id'))
        if (source is None or source.get('run_id') != run_id or
                source.get('evaluation_generation') != generation - 1 or
                source.get('logical_key') != value.get('logical_key')):
            raise ValueError('retry CHECK blocked: original CHECK binding is missing')
        if (value.get('retry_limit') != MAX_RETRIES_PER_BOUNDARY or
                value.get('plan_digest') is None or value.get('policy_digest') is None):
            raise ValueError('retry CHECK blocked: applicability policy or accepted digests are missing')
        if (artifact.get('type') != 'PROVENANCE' or artifact.get('producer_kind') != 'TASK' or
                artifact.get('producer_task_id') != source['task_id'] or artifact.get('producer_id') != source['task_id'] or
                artifact.get('run_id') != run_id or proof.get('input_digest') != source['input_digest']):
            raise ValueError('retry CHECK blocked: applicability proof has a foreign task binding')
        existing = [task for task in self.store.list_tasks(run_id, generation) if task.get('kind') == 'CHECK']
        if any(task.get('logical_key') != value['logical_key'] for task in existing):
            raise ValueError('retry CHECK blocked: generation has a conflicting CHECK slot')
        if len(existing) > 1:
            raise ValueError('retry CHECK blocked: duplicate linked CHECK assignments')
        if existing:
            return existing[0]
        request, bundle = workflow_native_worker.load_native_inputs(self.store, run_id)
        workflow_native_worker._verify_execution_inputs(request, bundle)
        if value.get('reuse_strategy') is not None:
            prefix = _recovery_prefix(self.store, source, request, bundle,
                reuse_strategy=value.get('reuse_strategy'), repair_kind=value.get('repair_kind'))
            if value.get('verified_prefix') != [] or value.get('toolchain') != []:
                raise ValueError('retry CHECK blocked: full-plan replay proof claims a reusable prefix')
        else:
            prefix = _recovery_prefix(self.store, source, request, bundle)
            if (prefix['source_links'] != value.get('verified_prefix') or
                    prefix.get('toolchain') != value.get('toolchain')):
                raise ValueError('retry CHECK blocked: original completed prefix no longer matches proof')
        runtime_fixtures = workflow_native_fixtures.validate_retry_bindings(
            bundle['resolved_plan']['plan']['checks'], value.get('runtime_fixtures'),
            input_digest=request['input_digest'], run_id=run_id, generation=generation,
            task_id=self._retry_task_id(run_id, generation, value['logical_key']),
            prefix_count=len(prefix['records']))
        if value.get('runtime_fixture_policy_id') != workflow_native_fixtures.POLICY_ID:
            raise ValueError('retry CHECK blocked: managed runtime fixture policy mismatch')
        workflow_native_fixtures.reserve_retry_bindings(request['source_worktree'], runtime_fixtures)
        task_id = self._retry_task_id(run_id, generation, value['logical_key'])
        check = {'contract_version': 1, 'task_id': task_id, 'run_id': run_id,
            'evaluation_generation': generation, 'kind': 'CHECK', 'dependency_ids': [],
            'input_digest': source['input_digest'], 'state': 'QUEUED', 'assignment_generation': 1,
            'assigned_session_id': None, 'deadline_ms': source['deadline_ms'], 'result': None}
        ready_event = workflow_store._internal_task_event('event_ready_' + task_id.removeprefix('task_'), check)
        self.store.add_task(check, logical_task_key=value['logical_key'], ready_event=ready_event)
        return self.store.get_task(task_id)

    @staticmethod
    def _retry_task_id(run_id, generation, logical_key):
        return 'task_native_retry_' + hashlib.sha256(
            (run_id + ':' + str(generation) + ':' + logical_key).encode()).hexdigest()[:24]


def load_recovery_prefix(store, new_task, request, bundle):
    """Validate linked retry proof and original completed-prefix provenance."""
    metadata = store.get_recovery_metadata(new_task['run_id'], new_task['evaluation_generation'])
    if metadata is None or metadata.get('boundary') != 'native:' + new_task.get('logical_key', ''):
        raise ValueError('retry recovery metadata is missing or has the wrong boundary')
    proof = store.get_artifact(metadata['fresh_input_proof'])
    if proof is None:
        raise ValueError('retry applicability proof is missing')
    artifact = proof['artifact']
    data = Path(artifact['path']).read_bytes()
    if len(data) != artifact['size_bytes'] or hashlib.sha256(data).hexdigest() != artifact['sha256']:
        raise ValueError('retry applicability proof integrity mismatch')
    value = workflow_store.strict_loads(data.decode('utf-8'))
    old_task_id = value.get('source_task_id')
    old_task = store.get_task(old_task_id)
    if (value.get('kind') != 'NATIVE_RETRY_APPLICABILITY' or value.get('run_id') != new_task['run_id'] or
            value.get('original_generation') != new_task['evaluation_generation'] - 1 or
            value.get('logical_key') != new_task.get('logical_key') or old_task is None or
            old_task.get('evaluation_generation') != value.get('original_generation') or
            value.get('input_digest') != request.get('input_digest') or
            value.get('plan_digest') != request.get('plan_digest') or
            value.get('policy_digest') != request.get('policy_digest') or
            value.get('source_manifest_digest') != workflow_store.digest(bundle['source_manifest']) or
            value.get('config_manifest_digest') != workflow_store.digest(bundle.get('config_provenance'))):
        raise ValueError('retry applicability proof does not match linked task and current inputs')
    if (value.get('policy_id') != POLICY_ID or value.get('retry_limit') != MAX_RETRIES_PER_BOUNDARY or
            value.get('plan_digest') != request.get('plan_digest') or
            value.get('policy_digest') != request.get('policy_digest')):
        raise ValueError('retry applicability policy or accepted digests mismatch')
    if value.get('runtime_fixture_policy_id') != workflow_native_fixtures.POLICY_ID:
        raise ValueError('retry runtime fixture policy mismatch')
    if (artifact.get('type') != 'PROVENANCE' or artifact.get('producer_kind') != 'TASK' or
            artifact.get('producer_task_id') != old_task_id or artifact.get('producer_id') != old_task_id or
            artifact.get('run_id') != new_task['run_id'] or proof.get('input_digest') != value.get('input_digest')):
        raise ValueError('retry applicability proof has a foreign task binding')
    if value.get('reuse_strategy') is not None:
        prefix = _recovery_prefix(store, old_task, request, bundle,
            reuse_strategy=value.get('reuse_strategy'), repair_kind=value.get('repair_kind'))
        if value.get('verified_prefix') != [] or value.get('toolchain') != []:
            raise ValueError('retry proof claims reusable prefix despite full-plan replay')
    else:
        prefix = _recovery_prefix(store, old_task, request, bundle)
        if prefix['source_links'] != value.get('verified_prefix'):
            raise ValueError('retry completed-prefix links differ from applicability proof')
        if prefix.get('toolchain') != value.get('toolchain'):
            raise ValueError('retry toolchain fingerprint differs from applicability proof')
    runtime_fixtures = workflow_native_fixtures.validate_retry_bindings(
        bundle['resolved_plan']['plan']['checks'], value.get('runtime_fixtures'),
        input_digest=request['input_digest'], run_id=new_task['run_id'],
        generation=new_task['evaluation_generation'], task_id=new_task['task_id'],
        prefix_count=len(prefix['records']))
    return dict(prefix, runtime_fixtures=runtime_fixtures)
