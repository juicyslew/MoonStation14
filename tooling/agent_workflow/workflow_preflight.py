"""Inactive, read-only authoritative preflight for workflow submissions."""
from __future__ import annotations

import copy
import hashlib
import json
import re
import stat
from dataclasses import dataclass
from pathlib import Path

import workflow_store as store
import workflow_runtime as runtime
from workflow_check_packs import resolve_check_packs, resolve_diagnostic_plan
from jsonschema import Draft202012Validator

_PREFLIGHT_SCHEMA_PATH = Path(__file__).parents[2] / 'docs' / 'development-workflow-redesign' / 'contracts' / 'preflight.schema.json'
_PREFLIGHT_SCHEMA = json.loads(_PREFLIGHT_SCHEMA_PATH.read_text(encoding='utf-8'))
Draft202012Validator.check_schema(_PREFLIGHT_SCHEMA)


@dataclass(frozen=True)
class PreflightContext:
    repo: Path
    authenticated_author: dict
    policy: dict
    artifacts: list
    publication_receipt_path: Path
    publication_receipt_sha256: str


class PreflightFailure(Exception):
    def __init__(self, verdict, code, field, message, required_change):
        self.verdict, self.code, self.field = verdict, code, field
        self.message, self.required_change = message, required_change
        super().__init__(message)


_CORRECTABLE_GUIDANCE = {
        'CALLER_REQUIRED': 'Use the authenticated caller binding supplied by the workflow tool.',
        'REQUEST_SCHEMA': 'Correct the named request field to match the submission_request contract, then prepare a fresh scope and submission key.',
        'AUTHOR_WORKTREE_MISMATCH': 'Use the authenticated author and exact bound worktree; register a fresh scope for corrected inputs.',
        'INCOMPLETE': 'Finish implementation and submit with implementation_complete=true and purpose OWNER_READY.',
        'MANUAL_INCOMPLETE': 'Replace TODO/FIXME placeholders in manual_steps with executable verification instructions.',
        'FIXTURE_MISSING': 'Add or correct the runnable fixture at the policy-declared path in the bound worktree.',
        'CRITERIA_MISMATCH': 'Use exactly the registered criteria IDs once each; request a new scope if criteria need correction.',
        'SOURCE_STALE': 'Refresh the source digest from the current worktree, register corrected evidence in a fresh scope, and submit with a fresh key.',
         'CHECK_PACK_PLAN': 'Correct check-pack selectors/evidence to satisfy trusted coverage floors; do not lower the floors. Register a fresh scope and submit with a fresh key.',
         'DIAGNOSTIC_CHECK_PLAN': 'Correct diagnostic check argv/log fixture binding to the required input-digest path, recompute the plan digest, then prepare a fresh scope and submission key.',
        'PLAN_DIGEST': 'Recompute the plan digest from the resolved trusted check plan and submit corrected inputs using a fresh scope and key.',
        'CONFIG_CHANGED': 'Do not revert unrelated owner configuration or add an exception. Use existing trusted provenance, or wait for tooling to repair publication provenance.',
        'ARTIFACT_MISSING': 'Provide the referenced readable artifact and register its corrected metadata in a fresh scope; submit with a fresh key.',
        'ARTIFACT_CONTRADICTION': 'Regenerate artifact metadata from the exact artifact bytes and register it in a fresh scope; submit with a fresh key.',
        'ARTIFACT_PROVENANCE': 'Register evidence produced by this exact submission key with valid SUBMISSION provenance; use a fresh scope and key.',
        'ARTIFACT_REQUIRED': 'Add each referenced evidence artifact to the scope and register it before submitting with a fresh key.',
        'EVIDENCE_DUPLICATE': 'Remove duplicate evidence references within each criterion, then register a fresh scope and submit with a fresh key.',
        'SOURCE_DRIFT': 'Stop source edits, recapture the current source and digest, then register fresh evidence and submit with a fresh key.',
        'ARTIFACT_DRIFT': 'Regenerate and re-register the changed evidence from stable bytes, then submit with a fresh scope and key.',
        'SOURCE_PATH_ESCAPE': 'Remove the escaping tracked path or symlink and repair the tracked source safely; recapture source, register a fresh scope, and submit with a fresh key. Do not bypass path checks.',
        'SOURCE_UNSUPPORTED_FILE': 'Replace the symlink or special file with a regular tracked file, recapture source and evidence, then register a fresh scope and key.',
        'SOURCE_RACE': 'Stop concurrent edits and regenerate the source manifest/digest from a stable worktree; register a fresh scope and key.',
        'SOURCE_READ': 'Repair or remove the unreadable tracked source file, recapture source, register a fresh scope, and submit with a fresh key.',
        'POLICY_DIGEST': 'Refresh the request digest from the currently registered policy; if policy inputs changed, register a fresh scope and submission key.',
    'ARTIFACT_SCHEMA': 'Correct the provided artifact metadata to the artifact contract, re-register it in a fresh scope, and submit with a fresh key.',
        'ARTIFACT_DUPLICATE': 'Remove duplicate artifact IDs, correct the evidence references, and register the corrected artifacts in a fresh scope with a fresh submission key.',
        'REVIEW_POLICY': 'Correct review roles, nonempty summaries, and envelope limit to satisfy trusted policy floors; register a fresh scope and key.',
        'REVIEW_ENVELOPE_OVERSIZE': 'Reduce the named role objective/context to the stated rendered-byte limit; keep trusted limits unchanged and submit with a fresh scope and key.',
}
_CORRECTABLE_CODES = frozenset(_CORRECTABLE_GUIDANCE)


def _error(code, field, message, verdict=None, change=None):
    # Explicit verdicts are authoritative; only the default path is classified.
    if verdict is None:
        verdict = 'FAIL' if code in _CORRECTABLE_GUIDANCE else 'ERROR'
    if change is None:
        change = _CORRECTABLE_GUIDANCE.get(code, 'Wait for tooling recovery; retain this submission key and retry the identical request only when receipt status is uncertain.')
    raise PreflightFailure(verdict, code, field, message, change)


def _hash_file(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        while True:
            runtime.check_operation_deadline()
            chunk=stream.read(1024*1024)
            if not chunk: break
            h.update(chunk)
    return h.hexdigest()


def capture_source(repo):
    repo = Path(repo).resolve()
    runtime.check_operation_deadline()
    head = runtime.git(repo, 'rev-parse', 'HEAD').decode('utf-8').strip()
    names = runtime.git(repo, 'ls-files', '-z', '--cached', '--others', '--exclude-standard').decode('utf-8').split('\0')
    rows = []
    for name in sorted(set(x for x in names if x)):
        runtime.check_operation_deadline()
        p = (repo / name).resolve(strict=False)
        if not p.is_relative_to(repo):
            _error('SOURCE_PATH_ESCAPE', 'source', f'Git path escapes worktree: {name}')
        raw = repo / name
        if not raw.exists() and not raw.is_symlink():
            rows.append({'path': name.replace('\\', '/'), 'state': 'DELETED'}); continue
        try:
            mode = raw.lstat().st_mode
            if stat.S_ISLNK(mode) or not stat.S_ISREG(mode):
                _error('SOURCE_UNSUPPORTED_FILE', 'source', f'Unsupported symlink or special path: {name}')
            before = raw.stat()
            file_hash = _hash_file(raw)
            after = raw.stat()
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                _error('SOURCE_RACE', 'source', f'File changed during capture: {name}')
            rows.append({'path': name.replace('\\', '/'), 'state': 'FILE', 'sha256': file_hash, 'size_bytes': after.st_size})
        except OSError as exc:
            _error('SOURCE_READ', 'source', f'Cannot inspect {name}: {exc}', 'FAIL', 'Repair or remove the unreadable tracked source file, recapture the source digest, then register a fresh scope and submission key.')
    return {'contract_version': 1, 'worktree': str(repo), 'head': head, 'files': rows}


def render_review_input(request, bundle_metadata, role, objective):
    envelope = {'contract_version':1,'event_id':bundle_metadata['event_id'],'run_id':bundle_metadata['run_id'],
                'evaluation_generation':bundle_metadata['evaluation_generation'],'task_id':bundle_metadata['task_id'],
                'assignment_generation':bundle_metadata['assignment_generation'],'input_digest':request['input_digest'],
                'recipient_binding':{'session_id':bundle_metadata['session_id'],'worktree':bundle_metadata['worktree'],'role':'reviewer'},
                'artifact_refs':bundle_metadata['artifact_refs'],'deadline_ms':bundle_metadata['deadline_ms'],
                'required_next_action':'TASK_ASSIGNMENT'}
    return {'envelope':store.validate(envelope,'dispatch_envelope'),
            'review':{'role':role,'objective':objective,'feature_label':request['feature_label'],
                      'manual_steps':request['manual_steps'],'criteria':request['criteria']},
            'instructions':'Claim this exact task, inspect the bound source and references, then complete_review with PASS or concrete FAIL findings; use report_error for system/input faults. Never run native checks or report pending evidence.'}


def preflight(request, *, caller, context):
    try:
        with runtime.operation_deadline(240):
            if not isinstance(caller, str) or not caller.strip(): _error('CALLER_REQUIRED', 'caller', 'Caller binding is required.', 'FAIL')
            try: store.validate(request, 'submission_request')
            except store.ProtocolError as exc:
                field=getattr(exc,'field',None) or 'request'
                detail=getattr(exc,'recovery',None)
                _error('REQUEST_SCHEMA', field, 'Submission request does not match the required contract.', 'FAIL',
                       (detail + ' Prepare a fresh scope and submission key.') if detail else _CORRECTABLE_GUIDANCE['REQUEST_SCHEMA'])
            req = copy.deepcopy(request)
            repo = Path(context.repo).resolve()
            if req['author'] != context.authenticated_author or req['source_worktree'] != str(repo) or req['author']['worktree'] != str(repo):
                _error('AUTHOR_WORKTREE_MISMATCH', 'author', 'Author binding and source must match authenticated worktree.', 'FAIL')
            policy = copy.deepcopy(context.policy)
            try:
                store.canonical_json(policy)
                Draft202012Validator({'$schema':_PREFLIGHT_SCHEMA['$schema'],'$defs':_PREFLIGHT_SCHEMA['$defs'],'$ref':'#/$defs/policy'}).validate(policy)
            except Exception:
                _error('POLICY_SCHEMA','policy','Trusted policy is not strict version-1 preflight JSON.')
            # Validate the authoritative policy's minimum shape without trusting request fields.
            required = {'contract_version','sprint_id','source_worktree','author','criteria','coverage','selector_requirements','fixture','package_artifacts','owner_exceptions','review_roles','review_summaries','review_envelope_limit_bytes','config_overrides'}
            if set(policy) != required or policy.get('contract_version') != 1:
                _error('POLICY_SCHEMA', 'policy', 'Trusted policy has unknown, missing, or unsupported fields.')
            if policy['sprint_id'] != req['sprint_id'] or policy['source_worktree'] != str(repo) or policy['author'] != context.authenticated_author:
                _error('POLICY_BINDING', 'policy', 'Trusted policy does not bind this request.')
            diagnostic = req['purpose'] == 'DIAGNOSTIC'
            if (not diagnostic and (req['implementation_complete'] is not True or req['purpose'] != 'OWNER_READY')) or (diagnostic and req['implementation_complete'] is not False):
                _error('INCOMPLETE', 'implementation_complete', 'Submission purpose/completion state is invalid.', 'FAIL')
            if not diagnostic and re.search(r'\b(TODO|FIXME)\b', '\n'.join(req['manual_steps']), re.I):
                _error('MANUAL_INCOMPLETE', 'manual_steps', 'Manual steps contain an explicit TODO/FIXME.', 'FAIL')
            fixture = (repo / policy['fixture']).resolve()
            if not fixture.is_relative_to(repo) or not fixture.is_file(): _error('FIXTURE_MISSING', 'policy.fixture', 'Runnable manual fixture is missing.', 'FAIL')
            if len({x['criterion_id'] for x in req['criteria']}) != len(req['criteria']) or set(x['criterion_id'] for x in req['criteria']) != set(policy['criteria']):
                _error('CRITERIA_MISMATCH', 'criteria', 'Submitted criteria must exactly match trusted agreed criteria.', 'FAIL')
            manifest = capture_source(repo)
            if store.digest(manifest) != req['input_digest']:
                _error('SOURCE_STALE', 'input_digest', 'Submitted source digest does not match current worktree.', 'FAIL')
            if store.digest(policy) != req['policy_digest']:
                _error('POLICY_DIGEST', 'policy_digest', 'Trusted policy digest does not match request.', 'FAIL')
            try: resolved = resolve_diagnostic_plan(req, repo=repo, policy=policy) if diagnostic else resolve_check_packs(req, repo=repo, policy=policy)
            except store.ProtocolError as exc:
                if diagnostic:
                    _error('DIAGNOSTIC_CHECK_PLAN', getattr(exc,'field',None) or 'diagnostic_plan',
                           'Diagnostic plan could not be resolved against its contract.', 'FAIL',
                           getattr(exc,'recovery',None) or 'Correct the typed diagnostic plan and recompute its plan digest; prepare a fresh scope and submission key.')
                _error('CHECK_PACK_PLAN',getattr(exc,'field',None) or 'check_packs',
                       'Check-pack plan could not be resolved against trusted coverage.', 'FAIL',
                       _CORRECTABLE_GUIDANCE['CHECK_PACK_PLAN'])
            if store.digest(resolved) != req['plan_digest']:
                _error('PLAN_DIGEST', 'plan_digest', 'Resolved plan digest does not match request.', 'FAIL')
            receipt_path = Path(context.publication_receipt_path).resolve()
            receipt_bytes = receipt_path.read_bytes()
            actual_receipt_hash = hashlib.sha256(receipt_bytes).hexdigest()
            if actual_receipt_hash != context.publication_receipt_sha256: _error('RECEIPT_HASH', 'publication_receipt', 'Pinned publication receipt hash mismatch.')
            try: receipt = store.strict_loads(receipt_bytes.decode('utf-8'))
            except Exception as exc: _error('RECEIPT_CORRUPT', 'publication_receipt', str(exc))
            main = Path(receipt['main_root'])
            matches = []
            for f in receipt['files']:
                t = f.get('target')
                root = main if t == 'main' else main.parent / t if isinstance(t, str) and re.fullmatch(r'MoonStation14-agent-[A-Za-z0-9_-]+', t) else None
                if root is not None and root.resolve() == repo and f.get('path') == 'opencode.jsonc': matches.append(f)
            if len(matches) != 1 or matches[0].get('deleted') is True: _error('CONFIG_RECEIPT_MISSING', 'publication_receipt.files', 'No unique non-deleted exact worktree config receipt row.')
            target = matches[0]['target']
            config = repo / 'opencode.jsonc'; config_hash = hashlib.sha256(config.read_bytes()).hexdigest()
            expected = matches[0].get('new_sha256')
            if not isinstance(expected,str) or not re.fullmatch(r'[a-f0-9]{64}',expected): _error('CONFIG_RECEIPT_CORRUPT','publication_receipt.files','Exact config receipt hash is malformed.')
            overrides = [x for x in policy['config_overrides'] if x.get('worktree')==str(repo) and x.get('path')=='opencode.jsonc' and x.get('sha256')==config_hash and isinstance(x.get('authority_ref'),str) and x['authority_ref']]
            if len(overrides)>1: _error('CONFIG_OVERRIDE_AMBIGUOUS','policy.config_overrides','More than one exact config override is authorized.')
            override = overrides[0] if overrides else None
            if config_hash != expected and override is None: _error('CONFIG_CHANGED', 'opencode.jsonc', 'Config differs from published worktree hash.', 'FAIL')
            artifacts = []
            refs = [r for c in req['criteria'] for r in c['evidence_refs']]
            for c in req['criteria']:
                if len(set(c['evidence_refs'])) != len(c['evidence_refs']): _error('EVIDENCE_DUPLICATE','criteria.evidence_refs','Evidence refs must be unique within each criterion.','FAIL')
            refs=sorted(set(refs))
            expected_submission = 'submit_'+hashlib.sha256((caller+'\0submit\0'+req['idempotency_key']).encode()).hexdigest()[:24]
            if len({a.get('artifact_id') for a in context.artifacts}) != len(context.artifacts): _error('ARTIFACT_DUPLICATE','artifacts','Artifact IDs must be unique.')
            for a in context.artifacts:
                try: store.validate(a, 'artifact')
                except store.ProtocolError as exc:
                    field=getattr(exc,'field',None) or 'artifacts'
                    detail=getattr(exc,'recovery',None)
                    _error('ARTIFACT_SCHEMA',field,'Provided artifact metadata does not match the artifact contract.',
                           'FAIL',(detail + ' Re-register corrected metadata in a fresh scope and submit with a fresh key.')
                           if detail else _CORRECTABLE_GUIDANCE['ARTIFACT_SCHEMA'])
                p = Path(a['path']).resolve()
                try: b = p.read_bytes()
                except OSError as exc: _error('ARTIFACT_MISSING','artifacts.path',f"Cannot read provided artifact {a['artifact_id']}: {exc}")
                if hashlib.sha256(b).hexdigest() != a['sha256'] or len(b) != a['size_bytes']: _error('ARTIFACT_CONTRADICTION','artifacts',f"Artifact metadata contradicts bytes: {a['artifact_id']}")
                if a.get('producer_kind') != 'SUBMISSION' or a.get('run_id') is not None or a.get('producer_task_id') is not None or a.get('producer_id') != expected_submission or a.get('submission_id') != expected_submission: _error('ARTIFACT_PROVENANCE','artifacts','Only this submission’s exact SUBMISSION producer evidence is accepted.')
                artifacts.append(copy.deepcopy(a))
            if set(refs) - {a['artifact_id'] for a in artifacts}: _error('ARTIFACT_REQUIRED','criteria.evidence_refs','Required evidence artifact is missing.','FAIL')
            roles = policy['review_roles']; summaries = policy['review_summaries']; limit = policy['review_envelope_limit_bytes']
            if not isinstance(roles,list) or len(set(roles)) != len(roles) or not roles or set(roles)-{'implementation','parity','coverage','fidelity'} or not isinstance(summaries,dict) or set(summaries) != set(roles) or any(not isinstance(summaries[r],str) or not summaries[r].strip() for r in roles) or not isinstance(limit,int) or isinstance(limit,bool) or not 1024 <= limit <= 16000:
                _error('REVIEW_POLICY','review_policy','Review roles/summaries/limit are invalid.')
            meta = {'event_id':'event_'+'x'*250,'run_id':'run_'+'x'*252,'task_id':'task_'+'x'*251,
                    'evaluation_generation':2147483647,'assignment_generation':2147483647,'session_id':'session_'+'x'*248,
                    'worktree':str(repo),'artifact_refs':refs+['artifact_'+'x'*247],
                    'deadline_ms':9999999999999}
            review_inputs=[]
            for role in ([] if diagnostic else roles):
                body=render_review_input(req,meta,role,summaries[role]); size=len(store.canonical_json(body).encode('utf-8'))
                if size > limit: _error('REVIEW_ENVELOPE_OVERSIZE',f'review_inputs.{role}',f'{role} rendered envelope is {size} bytes; limit is {limit}.','FAIL',f'Reduce {role} objective or submission context to at most {limit} rendered bytes.')
                review_inputs.append({'role':role,'body':body,'size_bytes':size,'body_sha256':hashlib.sha256(store.canonical_json(body).encode('utf-8')).hexdigest()})
            final_manifest=capture_source(repo)
            if final_manifest != manifest: _error('SOURCE_DRIFT','source_manifest','Worktree changed during preflight.')
            if hashlib.sha256(receipt_path.read_bytes()).hexdigest()!=actual_receipt_hash: _error('RECEIPT_DRIFT','publication_receipt','Publication receipt changed during preflight.')
            for a in artifacts:
                b=Path(a['path']).read_bytes()
                if hashlib.sha256(b).hexdigest()!=a['sha256'] or len(b)!=a['size_bytes']:_error('ARTIFACT_DRIFT','artifacts',f"Artifact changed during preflight: {a['artifact_id']}")
            bundle={'contract_version':1,'request_digest':store.digest(req),'submission_id':expected_submission,
                    'source_manifest':manifest,'resolved_plan':resolved,'review_policy':policy,'verified_artifacts':artifacts,
                    'review_inputs':review_inputs,'completed_receipt_ref':None,
                    'config_provenance':{'receipt_path':str(receipt_path),'receipt_sha256':actual_receipt_hash,'target':target,'actual_sha256':config_hash,'expected_sha256':expected,'override_authority':override and override['authority_ref']},
                    'input_digest':req['input_digest'],'plan_digest':req['plan_digest'],'policy_digest':req['policy_digest']}
            try:
                store.canonical_json(bundle)
                Draft202012Validator({'$schema':_PREFLIGHT_SCHEMA['$schema'],'$defs':_PREFLIGHT_SCHEMA['$defs'],'$ref':'#/$defs/bundle'}).validate(bundle)
                for artifact in artifacts: store.validate(artifact,'artifact')
            except Exception: _error('BUNDLE_CONTRACT','bundle','Frozen preflight bundle failed strict contract validation.')
            return copy.deepcopy(bundle)
    except PreflightFailure: raise
    except Exception:
        _error('PREFLIGHT_ERROR','preflight','Preflight stopped because an unexpected system error occurred; inspect the receipt state before retrying.')
