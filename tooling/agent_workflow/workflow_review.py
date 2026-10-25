"""Durable post-check review planning (no transport or session creation)."""
import hashlib
import json
import codecs
import time
from pathlib import Path

try:
    import workflow_native_worker, workflow_preflight, workflow_store, workflow_task_artifacts, workflow_review_results, workflow_runtime
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_native_worker, workflow_preflight, workflow_store, workflow_task_artifacts, workflow_review_results, workflow_runtime

MAX_REVIEW_SOURCE_BYTES = 8 * 1024 * 1024
MAX_REVIEW_INPUT_PAGE_BYTES = 12 * 1024
REVIEWER_BOUND_FIELDS = ('contract_version', 'run_id', 'evaluation_generation', 'assignment_generation', 'input_digest')


def semantic_reviewer_payload(payload):
    """Project a canonical reviewer callback onto its public semantic fields."""
    return {key: value for key, value in payload.items() if key not in REVIEWER_BOUND_FIELDS}


class SourceAccessError(Exception):
    def __init__(self, code, status, message):
        super().__init__(message); self.code=code; self.status=status


class ReviewCoordinator:
    def __init__(self, store, *, reviewers, artifact_dir):
        self.store = store
        self.reviewers = reviewers
        self.artifact_dir = Path(artifact_dir)
        self._review_guide_cache = {}

    def _inputs(self, run_id):
        return workflow_native_worker.load_native_inputs(self.store, run_id, verify_current_source=False)

    def source_access(self, task_id, *, source_query=None, source_path=None, source_line=1):
        task=self.store.get_task(task_id)
        if task is None or task.get('kind')!='REVIEW': raise SourceAccessError('SOURCE_UNAVAILABLE',404,'Review task unavailable.')
        if (source_query is None)==(source_path is None): raise SourceAccessError('INVALID_QUERY',400,'Choose exactly one source query or path.')
        request,bundle=self._inputs(task['run_id'])
        if request.get('input_digest')!=task.get('input_digest'): raise SourceAccessError('SOURCE_STALE',409,'Review source digest mismatch.')
        rows=[r for r in bundle['source_manifest'].get('files',[]) if r.get('state')=='FILE']
        if source_query is not None:
            if not isinstance(source_query,str) or not source_query or len(source_query)>500: raise SourceAccessError('INVALID_QUERY',400,'Source query must be nonempty and at most 500 characters.')
            matches=[r for r in rows if source_query.casefold() in r.get('path','').casefold()]
            return {'input_digest':task['input_digest'],'matches':[{'path':r['path'],'sha256':r['sha256'],'size_bytes':r['size_bytes']} for r in matches[:40]],'total_matches':len(matches),'truncated':len(matches)>40}
        if not isinstance(source_path,str) or not source_path or '\\' in source_path or Path(source_path).is_absolute() or '..' in Path(source_path).parts:
            raise SourceAccessError('INVALID_QUERY',400,'Source path must be a canonical repository-relative path.')
        row=next((r for r in rows if r.get('path')==source_path),None)
        if row is None: raise SourceAccessError('INVALID_QUERY',400,'Path is not in the frozen manifest; use source_query to select an exact admitted relative path and retry.')
        if isinstance(row.get('size_bytes'),bool) or not isinstance(row.get('size_bytes'),int) or row['size_bytes']<0:
            raise SourceAccessError('SOURCE_UNAVAILABLE',409,'Frozen source manifest has invalid file size metadata.')
        if row['size_bytes']>MAX_REVIEW_SOURCE_BYTES:
            raise SourceAccessError('SOURCE_UNAVAILABLE',413,'Source text access is limited to files at most 8 MiB; inspect an assigned permitted artifact/source file or report unavailable access.')
        if isinstance(source_line,bool) or not isinstance(source_line,int) or source_line<1: raise SourceAccessError('INVALID_QUERY',400,'Source line must be positive.')
        try:
            path=workflow_runtime.safe(request['source_worktree'],source_path)
            with path.open('rb') as stream: raw=stream.read(MAX_REVIEW_SOURCE_BYTES+1)
        except (OSError,ValueError,KeyError) as exc: raise SourceAccessError('SOURCE_UNAVAILABLE',409,'Bound source file cannot be safely read.') from exc
        if len(raw)!=row['size_bytes'] or hashlib.sha256(raw).hexdigest()!=row['sha256']:
            raise SourceAccessError('SOURCE_STALE',409,'Bound source file differs from its admitted hash.')
        try: lines=raw.decode('utf-8','strict').splitlines()
        except UnicodeDecodeError as exc: raise SourceAccessError('SOURCE_UNAVAILABLE',415,'Bound source file is not UTF-8 text.') from exc
        start=source_line; selected=[]; used=0
        for line in lines[start-1:start+79]:
            size=len(line.encode('utf-8'))+(1 if selected else 0)
            if size>12*1024-used:
                if not selected: raise SourceAccessError('SOURCE_UNAVAILABLE',413,'A source line exceeds the page byte limit.')
                break
            selected.append(line); used+=size
        end=start+len(selected)
        return {'input_digest':task['input_digest'],'path':source_path,'sha256':row['sha256'],'size_bytes':row['size_bytes'],'content':'\n'.join(selected),'start_line':start,'next_line':end if end<=len(lines) else None,'eof':end>len(lines)}

    def input_access(self, task_id, input_ref, input_offset=0):
        """Read a page of the exact guide or artifact set frozen into this review assignment."""
        task=self.store.get_task(task_id)
        if task is None or task.get('kind')!='REVIEW': raise SourceAccessError('SOURCE_UNAVAILABLE',404,'Review task unavailable.')
        if not isinstance(input_offset,int) or isinstance(input_offset,bool) or input_offset<0:
            raise SourceAccessError('INVALID_QUERY',400,'Input offset must be a nonnegative byte offset.')
        request,bundle=self._inputs(task['run_id'])
        if request.get('input_digest')!=task.get('input_digest'):
            raise SourceAccessError('SOURCE_STALE',409,'Review input digest mismatch.')
        assignment=self._build_assignment(task,request,bundle)
        return self._input_access(task,input_ref,input_offset,request,bundle,assignment)

    def _input_access(self,task,input_ref,input_offset,request,bundle,assignment):
        refs=assignment['body'].get('envelope',{}).get('artifact_refs',[])
        if input_ref=='review_guide':
            try: artifact=self._render_review_guide(task,request,bundle,assignment)
            except Exception as exc: raise SourceAccessError('SOURCE_UNAVAILABLE',409,'Review guide is unavailable or failed integrity verification.') from exc
            artifact_id=artifact['artifact_id']; art=artifact
        else:
            if not isinstance(input_ref,str) or input_ref not in refs:
                raise SourceAccessError('INVALID_QUERY',400,'Input reference must be review_guide or an exact assigned artifact ID.')
            binding=self.store.get_artifact(input_ref)
            if binding is None: raise SourceAccessError('SOURCE_UNAVAILABLE',409,'Assigned artifact is unavailable.')
            art=binding['artifact']; artifact_id=input_ref
        try:
            size=art['size_bytes']; expected=art['sha256']; path=Path(art['path'])
            if isinstance(size,bool) or not isinstance(size,int) or size<0 or not isinstance(expected,str): raise ValueError()
            if input_offset>size: raise SourceAccessError('INVALID_QUERY',400,'Input offset exceeds artifact size.')
            if size>64*1024*1024: raise SourceAccessError('SOURCE_UNAVAILABLE',413,'Assigned artifact exceeds bounded verification limit.')
            if path.stat().st_size!=size: raise SourceAccessError('SOURCE_STALE',409,'Assigned artifact size changed.')
            end=min(size,input_offset+MAX_REVIEW_INPUT_PAGE_BYTES)
            digest=hashlib.sha256(); decoder=codecs.getincrementaldecoder('utf-8')('strict'); text_valid=True
            captured=bytearray(); position=0
            with path.open('rb') as stream:
                while True:
                    block=stream.read(1024*1024)
                    if not block: break
                    digest.update(block)
                    if text_valid:
                        try: decoder.decode(block,final=False)
                        except UnicodeDecodeError: text_valid=False
                    block_end=position+len(block)
                    left=max(input_offset,position); right=min(end+4,block_end)
                    if right>left: captured.extend(block[left-position:right-position])
                    position=block_end
                if position!=size or path.stat().st_size!=size or digest.hexdigest()!=expected:
                    raise SourceAccessError('SOURCE_STALE',409,'Assigned artifact failed size or SHA-256 verification.')
                if text_valid:
                    try: decoder.decode(b'',final=True)
                    except UnicodeDecodeError: text_valid=False
        except SourceAccessError: raise
        except Exception as exc: raise SourceAccessError('SOURCE_UNAVAILABLE',409,'Assigned artifact cannot be safely read.') from exc
        page=bytes(captured[:end-input_offset])
        if text_valid:
            if input_offset and page and (page[0]&0xC0)==0x80:
                raise SourceAccessError('INVALID_QUERY',400,'Input offset is not a UTF-8 boundary; retry at a returned page offset.')
            while page:
                try:
                    content=page.decode('utf-8','strict'); break
                except UnicodeDecodeError as exc:
                    if exc.end < len(page): raise SourceAccessError('SOURCE_UNAVAILABLE',409,'Assigned UTF-8 page failed decoding.') from exc
                    page=page[:-1]; end-=1
            else: content=''
            encoding='utf-8'
        else:
            encoding='base64'
            import base64
            content=base64.b64encode(page).decode('ascii')
        return {'task_id':task['task_id'],'run_id':task['run_id'],'input_digest':task['input_digest'],
            'assignment_body_sha256':assignment['body_sha256'],'artifact_id':artifact_id,'type':art.get('type'),
            'sha256':expected,'size_bytes':size,'encoding':encoding,'content':content,
            'offset':input_offset,'next_offset':end if end<size else None,'eof':end==size}

    def prepare_review_inputs(self,task_id):
        """Verify claim inputs using one fresh frozen-source context, before claim commit."""
        task=self.store.get_task(task_id)
        if task is None or task.get('kind')!='REVIEW': raise workflow_store.ProtocolError('unknown review task')
        request,bundle=workflow_native_worker.load_native_inputs(self.store,task['run_id'])
        if request.get('input_digest')!=task.get('input_digest'):
            raise workflow_store.ProtocolError('review task input digest differs from admitted source')
        assignment=self._build_assignment(task,request,bundle)
        guide=self._render_review_guide(task,request,bundle,assignment)
        refs=assignment['body'].get('envelope',{}).get('artifact_refs',[])
        self._input_access(task,'review_guide',0,request,bundle,assignment)
        for ref in refs:
            self._input_access(task,ref,0,request,bundle,assignment)
        return guide

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
        request, bundle = workflow_native_worker.load_native_inputs(self.store, run_id)
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
        return self._build_assignment(task,request,bundle)

    def _build_assignment(self,task,request,bundle):
        task_id=task['task_id']
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
        request,bundle=self._inputs(task['run_id'])
        assignment=self._build_assignment(task,request,bundle)
        return self._render_review_guide(task,request,bundle,assignment)

    def _render_review_guide(self,task,request,bundle,assignment):
        task_id=task['task_id']
        cache_key=(task_id,assignment['body_sha256'])
        cached=self._review_guide_cache.get(cache_key)
        if cached is not None:
            # This is locator metadata only: input_access still reads and verifies
            # the immutable bytes on every page access.
            return dict(cached)
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
            meta = {'artifact_id': ref, 'type': art['type'], 'sha256': art['sha256'], 'size_bytes': len(raw),
                    'producer_kind': art.get('producer_kind'), 'producer_id': art.get('producer_id'),
                    'producer_task_id': art.get('producer_task_id')}
            expected_producer = native_receipt_refs.get(ref)
            if art['type'] == 'CHECK_RECEIPT' and expected_producer is not None:
                if (art.get('producer_kind') != 'TASK' or art.get('producer_id') != expected_producer or
                        art.get('producer_task_id') != expected_producer or art.get('run_id') != task['run_id']):
                    raise workflow_store.ProtocolError(f'CHECK_RECEIPT producer binding failed: {ref}')
                try:
                    receipt=json.loads(raw)
                    check_summaries=[]
                    for check in receipt.get('checks',[]) if isinstance(receipt,dict) else []:
                        if not isinstance(check,dict): continue
                        evidence=check.get('evidence',{})
                        check_summaries.append({key:check[key] for key in ('kind','covers','exit_code') if key in check} |
                            ({'status':evidence['status']} if isinstance(evidence,dict) and 'status' in evidence else {}))
                    receipts.append({'metadata': meta, 'content_embedded':False,
                        'read_example': {'input_ref':ref,'input_offset':0}, 'checks': check_summaries})
                except (ValueError, UnicodeDecodeError) as exc:
                    raise workflow_store.ProtocolError(f'CHECK_RECEIPT is not readable JSON: {ref}') from exc
            elif art['type'] == 'SOURCE_MANIFEST':
                sources.append({'metadata': meta, 'content_embedded': False,
                                'read_example': {'input_ref':ref,'input_offset':0}})
            else:
                sources.append({'metadata': meta, 'content_embedded': False,
                                'read_example': {'input_ref': ref, 'input_offset': 0}})
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
        # Public reviewer result/error objects contain semantic fields only;
        # these exact protocol definitions remain the source of truth and are
        # projected rather than independently re-specified.
        for name in ('review_result','task_error'):
            defs[name]=json.loads(json.dumps(defs[name]))
            defs[name]['required']=[field for field in defs[name]['required'] if field not in REVIEWER_BOUND_FIELDS]
            for field in REVIEWER_BOUND_FIELDS: defs[name]['properties'].pop(field,None)
        guide = {'task_id': task_id, 'run_id': task['run_id'], 'evaluation_generation': task['evaluation_generation'],
            'assignment_generation': task['assignment_generation'], 'input_digest': task['input_digest'],
            'source_worktree': request['source_worktree'], 'assignment_body_sha256': assignment['body_sha256'],
            'review_scope': assignment['body']['review'],
            'assignment_artifact_refs': refs,
            'instruction': 'Claim with claim_task(task_id, session_id); task_id is required. OWNER_READY means technically ready for owner testing, not owner acceptance; owner acceptance is a separate later decision. Every blocking finding must declare validation_stage=TECHNICAL. Pending subjective/physical owner observations belong in owner_followups with criterion_id, message, manual_check, and optional evidence_refs. Never classify pending owner observation as a technical finding; concrete UI/render/input/audio defects and required machine-check coverage remain technical blockers. PASS has no blocking findings; FAIL requires actionable technical findings. Followups are retained for the owner testing log. A review is complete only when the SDK accepts complete_review(result=<semantic review result>, session_id=<assigned session>) or report_error(error=<semantic task error>, session_id=<assigned session>). Include task_id and semantic evidence/decision fields, but omit contract_version, run_id, evaluation_generation, assignment_generation, and input_digest; the coordinator binds those after authorization. final chat prose or stopping without an accepted receipt does not submit a result. For an actual infrastructure error call report_error. inspected_refs must cite original assignment refs; guide/source pages are not refs. FAIL findings require required_change. Read this guide and each required assigned artifact through status(task_id, session_id, input_ref="review_guide", input_offset=0), then use the exact artifact_id in assignment_artifact_refs as input_ref and returned next_offset for paging. Pages are integrity-verified and bound to this assignment. For source code use status(task_id, session_id, source_query="literal substring"), then source_path exactly as returned and source_line for paging. Never read relative MAIN-checkout files as substitutes for source_worktree. If access is unavailable or stale, report factual task_error. session_id is a separate tool argument, not a field in either JSON object.',
            'source_access': {'limits': {'maximum_file_bytes':MAX_REVIEW_SOURCE_BYTES,'maximum_lines_per_page':80,'maximum_utf8_bytes_per_page':12*1024},
                'input_example': {'tool':'status','task_id':'<assigned task_id>','session_id':'<assigned session_id>','input_ref':'review_guide','input_offset':0},
                'query_example': {'tool':'status','task_id':'<assigned task_id>','session_id':'<assigned session_id>','source_query':'<filename from your scoped criterion>'},
                'page_example': {'tool':'status','task_id':'<assigned task_id>','session_id':'<assigned session_id>','source_path':'<exact relative path returned by source_query>','source_line':1},
                'paging': 'Use returned next_line until eof is true. source_path must come from this task source_query; replace placeholders with this assignment identifiers and paths. Text access supports UTF-8 files up to 8 MiB. Larger/binary files are unsupported here; use only permitted assigned source/artifact tools where available, otherwise report the actual source access error. Never substitute MAIN-checkout content.'},
            'review_contract': {'$schema': canonical['$schema'],
                '$ref': '#/$defs/review_result', '$defs': defs},
            'error_report_template': semantic_reviewer_payload(self.error_report_template(task_id, error_code='REPLACE_WITH_FACTUAL_ERROR_CODE', message='Replace with a factual description of the error.')),
            'result_example': {'tool':'complete_review','arguments':{'result':{'task_id':task_id,'decision':'PASS',
                'findings':[], 'owner_followups':[], 'inspected_refs':[refs[0]]},
                'session_id':'<assigned session_id>'}},
            'error_example': {'tool':'report_error','arguments':{'error':semantic_reviewer_payload(
                self.error_report_template(task_id,error_code='REPLACE_WITH_FACTUAL_ERROR_CODE',
                    message='Replace with a factual description of the error.')),'session_id':'<assigned session_id>'}},
            'allowed_categories': sorted(workflow_review_results._CATEGORIES), 'evidence': sources + receipts}
        data = (json.dumps(guide, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf-8')
        artifact_id, path = workflow_task_artifacts.register_bytes(self.store, task, self.artifact_dir, 'PROVENANCE', data)
        metadata={'artifact_id': artifact_id, 'sha256': hashlib.sha256(data).hexdigest(), 'size_bytes': len(data),
                'path': str(path), 'type': 'PROVENANCE'}
        self._review_guide_cache[cache_key]=metadata
        return dict(metadata)

    def error_report_template(self, task_id, *, error_code, message):
        task = self.store.get_task(task_id)
        if task is None:
            raise workflow_store.ProtocolError('unknown task')
        report = {'contract_version': 1, 'task_id': task['task_id'], 'run_id': task['run_id'],
                  'evaluation_generation': task['evaluation_generation'], 'assignment_generation': task['assignment_generation'],
                  'input_digest': task['input_digest'], 'error_code': error_code, 'message': message, 'artifact_refs': []}
        workflow_store.validate(report, 'task_error')
        return report
