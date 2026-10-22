"""Durable post-check review planning (no transport or session creation)."""
import hashlib
import json
import time
from pathlib import Path

try:
    import workflow_native_worker, workflow_preflight, workflow_store, workflow_task_artifacts, workflow_review_results
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_native_worker, workflow_preflight, workflow_store, workflow_task_artifacts, workflow_review_results


class ReviewCoordinator:
    def __init__(self, store, *, reviewers, artifact_dir):
        self.store = store
        self.reviewers = reviewers
        self.artifact_dir = Path(artifact_dir)

    def _inputs(self, run_id):
        return workflow_native_worker.load_native_inputs(self.store, run_id)

    def plan_reviews(self, run_id):
        saved = self.store.get_run(run_id)
        if saved is None:
            raise workflow_store.ProtocolError('unknown run')
        run, attempt = saved['run'], saved['attempt']
        if run.get('run_kind') == 'DIAGNOSTIC':
            return []
        if attempt['phase'] == 'QUEUED':
            return []
        if attempt['phase'] not in ('CHECKING', 'REVIEWING'):
            if attempt['phase'] in ('DECIDED', 'STOPPING', 'CLOSED'):
                return []
            raise workflow_store.ProtocolError('attempt is not ready for review planning')
        tasks = self.store.list_tasks(run_id, attempt['generation'])
        initial_checks = [t for t in tasks if t['kind'] == 'CHECK']
        if any(t['state'] == 'COMPLETED' and (t.get('result') or {}).get('decision') in ('FAIL','ERROR') for t in initial_checks):
            return []
        if not self.store.effective_checks_complete(run_id, attempt['generation']):
            return []
        effective = [t for t in tasks if t['kind'] == 'CHECK']
        for check in effective:
            if check['state'] != 'COMPLETED' or check.get('result', {}).get('decision') != 'PASS':
                return []
        request, bundle = self._inputs(run_id)
        roles = bundle['review_policy']['review_roles']
        bindings = self.reviewers
        selected = {}
        for role in roles:
            binding = bindings.get(role)
            if (not isinstance(binding, dict) or binding.get('role') != 'reviewer' or
                    not isinstance(binding.get('worktree'), str) or not binding['worktree'].strip()):
                raise workflow_store.ProtocolError(f'missing or invalid trusted reviewer binding for role {role}')
            selected[role] = binding
        if attempt['phase'] == 'REVIEWING' and attempt.get('result') is not None:
            return []
        deps, receipts = [], []
        for check in effective:
            deps.append(check['task_id'])
            for ref in check.get('result', {}).get('artifact_refs', []):
                binding = self.store.get_artifact(ref)
                if binding is None or binding['artifact']['type'] != 'CHECK_RECEIPT':
                    continue
                artifact = binding['artifact']
                data = Path(artifact['path']).read_bytes()
                if len(data) != artifact['size_bytes'] or hashlib.sha256(data).hexdigest() != artifact['sha256']:
                    raise workflow_store.ProtocolError(f'CHECK_RECEIPT bytes failed verification: {ref}')
                receipts.append(ref)
        if not receipts:
            raise workflow_store.ProtocolError('passing checks have no verified CHECK_RECEIPT')
        if attempt['phase'] == 'CHECKING':
            self.store.transition_attempt(run_id, attempt['generation'], expected_sequence=run['sequence'], phase='REVIEWING')
            saved = self.store.get_run(run_id)
            run, attempt = saved['run'], saved['attempt']
        result = []
        for role in roles:
            binding = selected[role]
            task_id = 'task_review_' + hashlib.sha256(f"{run_id}:{attempt['generation']}:{role}".encode()).hexdigest()[:24]
            existing = self.store.get_task(task_id)
            if existing:
                expected_session = self._reserved_session(task_id, existing['evaluation_generation'], existing['assignment_generation'])
                if existing.get('assigned_session_id') != expected_session:
                    raise workflow_store.ProtocolError('saved reviewer session does not match assignment')
                result.append(existing)
                continue
            session_id = self._reserved_session(task_id, attempt['generation'], 1)
            task = {'contract_version':1,'task_id':task_id,'run_id':run_id,'evaluation_generation':attempt['generation'],
                    'kind':'REVIEW','dependency_ids':sorted(set(deps)),'input_digest':run['input_digest'],'state':'QUEUED',
                    'assignment_generation':1,'assigned_session_id':session_id,'deadline_ms':int(time.time()*1000)+workflow_store.REVIEW_CLAIM_WINDOW_MS,'result':None}
            ready_id = 'event_ready_' + hashlib.sha256(task_id.encode()).hexdigest()[:24]
            ready = {'contract_version':1,'event_id':ready_id,'kind':'TASK_READY','run_id':run_id,
                     'evaluation_generation':attempt['generation'],'task_id':task_id,
                     'assignment_generation':1,'input_digest':run['input_digest']}
            self.store.add_task(task, logical_task_key='review:'+role, ready_event=ready)
            result.append(task)
        return result

    @staticmethod
    def _reserved_session(task_id, evaluation_generation, assignment_generation):
        digest = hashlib.sha256(f'{task_id}:{evaluation_generation}:{assignment_generation}'.encode()).hexdigest()[:32]
        return 'ses_' + digest

    def build_assignment(self, task_id):
        task = self.store.get_task(task_id)
        if task is None or task['kind'] != 'REVIEW':
            raise workflow_store.ProtocolError('unknown review task')
        request, bundle = self._inputs(task['run_id'])
        role = task['logical_key'].removeprefix('review:')
        binding = self.reviewers.get(role)
        expected_session = self._reserved_session(task_id, task['evaluation_generation'], task['assignment_generation'])
        if task.get('assigned_session_id') != expected_session:
            raise workflow_store.ProtocolError('saved reviewer session does not match assignment')
        if not binding or binding.get('role') != 'reviewer' or not isinstance(binding.get('worktree'), str) or not binding['worktree'].strip():
            raise workflow_store.ProtocolError('reviewer binding no longer matches saved assignment')
        checks = self.store.list_tasks(task['run_id'], task['evaluation_generation'])
        refs = sorted(set(a['artifact_id'] for a in bundle['verified_artifacts']))
        for check in checks:
            if check['kind'] != 'CHECK':
                continue
            for ref in check.get('result', {}).get('artifact_refs', []):
                info = self.store.get_artifact(ref)
                if info and info['artifact']['type'] == 'CHECK_RECEIPT':
                    art = info['artifact']; data = Path(art['path']).read_bytes()
                    if len(data) != art['size_bytes'] or hashlib.sha256(data).hexdigest() != art['sha256']:
                        raise workflow_store.ProtocolError(f'CHECK_RECEIPT bytes failed verification: {ref}')
                    refs.append(ref)
        event_id = 'event_review_' + hashlib.sha256(task_id.encode()).hexdigest()[:24]
        body = workflow_preflight.render_review_input(request, {
            'event_id':event_id,'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
            'task_id':task_id,'assignment_generation':task['assignment_generation'],'session_id':task['assigned_session_id'],
            'worktree':binding['worktree'],'artifact_refs':sorted(set(refs)),'deadline_ms':task['deadline_ms']},
            role,bundle['review_policy']['review_summaries'][role])
        raw = workflow_store.canonical_json(body).encode('utf-8')
        if len(raw) > bundle['review_policy']['review_envelope_limit_bytes']:
            raise workflow_store.ProtocolError('fully rendered review envelope exceeds trusted policy limit')
        return {'body':body,'body_sha256':hashlib.sha256(raw).hexdigest(),'size_bytes':len(raw)}

    def review_guide(self, task_id):
        """Materialize a readable, hash-bound reviewer guide without changing assignment inputs."""
        task = self.store.get_task(task_id)
        if task is None or task.get('kind') != 'REVIEW':
            raise workflow_store.ProtocolError('unknown review task')
        assignment = self.build_assignment(task_id)
        request, _ = self._inputs(task['run_id'])
        refs = assignment['body'].get('envelope', {}).get('artifact_refs', [])
        # Only CHECK_RECEIPTs bound to an accepted CHECK task result are native
        # receipts. Submission attachments with the same label remain sources.
        checks = self.store.list_tasks(task['run_id'], task['evaluation_generation'])
        native_receipt_refs = {}
        for check in checks:
            if check.get('kind') == 'CHECK' and check.get('state') == 'COMPLETED' and check.get('result', {}).get('decision') == 'PASS':
                for ref in check.get('result', {}).get('artifact_refs', []):
                    native_receipt_refs[ref] = check['task_id']
        sources = []
        receipts = []
        for ref in refs:
            item = self.store.get_artifact(ref)
            if not item:
                raise workflow_store.ProtocolError(f'missing review artifact: {ref}')
            art = item['artifact']
            try:
                raw = Path(art['path']).read_bytes()
            except OSError as exc:
                raise workflow_store.ProtocolError(
                    f"review artifact read failed ({ref}, {type(exc).__name__})") from exc
            if len(raw) != art['size_bytes'] or hashlib.sha256(raw).hexdigest() != art['sha256']:
                raise workflow_store.ProtocolError(f'review artifact bytes failed verification: {ref}')
            meta = {'artifact_id': ref, 'sha256': art['sha256'], 'size_bytes': len(raw), 'source_pointer': art['path'],
                    'producer_kind': art.get('producer_kind'), 'producer_id': art.get('producer_id'),
                    'producer_task_id': art.get('producer_task_id')}
            expected_producer = native_receipt_refs.get(ref)
            if art['type'] == 'CHECK_RECEIPT' and expected_producer is not None:
                if (art.get('producer_kind') != 'TASK' or art.get('producer_id') != expected_producer or
                        art.get('producer_task_id') != expected_producer or art.get('run_id') != task['run_id']):
                    raise workflow_store.ProtocolError(f'CHECK_RECEIPT producer binding failed: {ref}')
                try:
                    receipts.append({'metadata': meta, 'checks': json.loads(raw)})
                except (ValueError, UnicodeDecodeError) as exc:
                    raise workflow_store.ProtocolError(f'CHECK_RECEIPT is not readable JSON: {ref}') from exc
            elif art['type'] == 'SOURCE_MANIFEST':
                sources.append({'metadata': meta, 'verified_locator': art['path'], 'content_embedded': False})
            else:
                try: sources.append({'metadata': meta, 'content': json.loads(raw)})
                except (ValueError, UnicodeDecodeError):
                    try: sources.append({'metadata': meta, 'content': raw.decode('utf-8')})
                    except UnicodeDecodeError: sources.append({'metadata': meta, 'verified_locator': art['path']})
        canonical = workflow_store._SCHEMA
        reachable = {'review_result'}
        def visit(value):
            if isinstance(value, dict):
                ref = value.get('$ref')
                if isinstance(ref, str) and ref.startswith('#/$defs/'):
                    name = ref[len('#/$defs/'):]
                    if name not in reachable:
                        reachable.add(name)
                        visit(canonical['$defs'][name])
                for child in value.values(): visit(child)
            elif isinstance(value, list):
                for child in value: visit(child)
        visit(canonical['$defs']['review_result'])
        reachable.add('task_error')
        visit(canonical['$defs']['task_error'])
        defs = {name: canonical['$defs'][name] for name in sorted(reachable)}
        guide = {'task_id': task_id, 'run_id': task['run_id'], 'evaluation_generation': task['evaluation_generation'],
            'assignment_generation': task['assignment_generation'], 'input_digest': task['input_digest'],
            'source_worktree': request['source_worktree'], 'assignment_body_sha256': assignment['body_sha256'],
            'review_scope': assignment['body']['review'],
            'assignment_artifact_refs': refs,
            'instruction': 'Return the canonical review_result JSON. inspected_refs must cite original assignment refs. FAIL findings require required_change. For error reporting use task_error; do not include session_id in its JSON (session is a separate tool argument).',
            'review_contract': {'$schema': canonical['$schema'],
                '$ref': '#/$defs/review_result', '$defs': defs},
            'error_report_template': self.error_report_template(task_id, error_code='REPLACE_WITH_FACTUAL_ERROR_CODE', message='Replace with a factual description of the error.'),
            'allowed_categories': sorted(workflow_review_results._CATEGORIES), 'evidence': sources + receipts}
        data = (json.dumps(guide, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
        artifact_id, path = workflow_task_artifacts.register_bytes(self.store, task, self.artifact_dir, 'PROVENANCE', data)
        return {'artifact_id': artifact_id, 'sha256': hashlib.sha256(data).hexdigest(), 'size_bytes': len(data),
                'path': str(path), 'type': 'PROVENANCE'}

    def error_report_template(self, task_id, *, error_code, message):
        task = self.store.get_task(task_id)
        if task is None:
            raise workflow_store.ProtocolError('unknown task')
        report = {'contract_version': 1, 'task_id': task['task_id'], 'run_id': task['run_id'],
                  'evaluation_generation': task['evaluation_generation'], 'assignment_generation': task['assignment_generation'],
                  'input_digest': task['input_digest'], 'error_code': error_code, 'message': message, 'artifact_refs': []}
        workflow_store.validate(report, 'task_error')
        return report
