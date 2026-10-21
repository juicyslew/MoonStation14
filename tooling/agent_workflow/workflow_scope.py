"""Registration and resolution of immutable, per-feature author scopes."""
from __future__ import annotations

import copy
import hashlib
import json
from dataclasses import dataclass, replace
from pathlib import Path

from jsonschema import Draft202012Validator, ValidationError

import workflow_preflight as preflight
import workflow_store as store


class ScopeError(store.ProtocolError):
    def __init__(self, code, field, message, recovery=None):
        self.code = code
        self.field = str(field)[:256]
        self.message = str(message)[:500]
        self.recovery = str(recovery)[:500] if recovery else None
        super().__init__(f'{code} ({self.field}): {self.message}')


class ScopeInfrastructureError(RuntimeError):
    """Evidence infrastructure failed; do not persist it as an author FAIL."""


@dataclass(frozen=True)
class AuthorWorktreeContext:
    """Trusted identity and shared floors for one authenticated worktree call."""
    repo: str
    authenticated_author: dict
    floors: dict
    publication_receipt_path: str
    publication_receipt_sha256: str


def author_caller(slot, verified_session_id):
    """Canonical store namespace for one configured slot and verified session."""
    if not isinstance(slot, str) or not slot or not isinstance(verified_session_id, str) or not verified_session_id:
        raise ValueError('author slot and verified session are required')
    return slot + ':' + verified_session_id


def validate_worktree_floors(floors):
    """Parse the floor-only worktree configuration using existing floor semantics."""
    expected = {'required_packs', 'selectors', 'review_roles', 'owner_exceptions',
                'config_overrides', 'review_envelope_limit'}
    if not isinstance(floors, dict) or set(floors) != expected:
        raise ValueError('floors must contain exactly required_packs, selectors, review_roles, owner_exceptions, config_overrides, and review_envelope_limit')
    packs, roles = floors['required_packs'], floors['review_roles']
    if not isinstance(packs, list) or not packs or any(not isinstance(v, str) or not v for v in packs) or len(set(packs)) != len(packs):
        raise ValueError('required_packs must contain unique nonempty strings')
    import workflow_check_packs
    unknown_packs=set(packs)-set(workflow_check_packs._KINDS)
    if unknown_packs:
        raise ValueError('required_packs contains unknown check-pack IDs: '+', '.join(sorted(unknown_packs)))
    if not isinstance(roles, list) or any(not isinstance(v, str) or not v for v in roles) or len(set(roles)) != len(roles):
        raise ValueError('review_roles must contain unique nonempty strings')
    selectors = floors['selectors']
    if not isinstance(selectors, dict) or any(not isinstance(k, str) or not isinstance(v, list) or any(not isinstance(x, str) or not x for x in v) or len(set(v)) != len(v) for k, v in selectors.items()):
        raise ValueError('selectors must map strings to arrays of unique nonempty strings')
    if not isinstance(floors['owner_exceptions'], list) or not isinstance(floors['config_overrides'], list):
        raise ValueError('owner_exceptions and config_overrides have invalid types')
    limit = floors['review_envelope_limit']
    if type(limit) is not int or limit <= 0:
        raise ValueError('review_envelope_limit must be a positive integer')
    return {'required_packs': set(packs), 'selectors': {k: set(v) for k, v in selectors.items()},
            'roles': set(roles), 'exceptions': copy.deepcopy(floors['owner_exceptions']),
            'overrides': copy.deepcopy(floors['config_overrides']), 'review_limit': limit}


def worktree_author_context(*, repo, authenticated_author, floors, publication_receipt_path, publication_receipt_sha256):
    repo = str(Path(repo).resolve())
    if not isinstance(authenticated_author, dict) or authenticated_author.get('worktree') != repo:
        raise ValueError('authenticated author must be bound to the configured worktree')
    normalized = validate_worktree_floors(floors)
    return AuthorWorktreeContext(repo, copy.deepcopy(authenticated_author), normalized,
        str(Path(publication_receipt_path).resolve()), publication_receipt_sha256)


_SCOPE_RECOVERY = {
    'SCOPE_INPUT': 'Correct the named scope payload field(s) and register the corrected payload with a fresh registration key; use a fresh submission key if its bound inputs changed.',
    'POLICY_SCHEMA': 'Correct the named policy field to the version-1 policy contract, then register corrected inputs with a fresh registration key and fresh submission key.',
    'POLICY_BINDING': 'Use the authenticated author and exact worktree from current bound status; register corrected scope with fresh keys.',
    'FLOOR_COVERAGE': 'Add configured required check packs to policy.coverage; do not lower shared floors. Register corrected policy with fresh keys.',
    'FLOOR_SELECTORS': 'Add all configured required selectors to policy.selector_requirements; do not lower shared floors. Register corrected policy with fresh keys.',
    'FLOOR_REVIEW_ROLES': 'Retain every configured review role in policy.review_roles; do not weaken floors. Register corrected policy with fresh keys.',
    'FLOOR_OWNER_EXCEPTIONS': 'Use exactly the trusted owner_exceptions from bound status; do not add or remove exceptions. Register corrected scope with fresh keys.',
    'FLOOR_CONFIG_OVERRIDES': 'Use exactly trusted config_overrides from bound status; do not authorize new overrides. Register corrected scope with fresh keys.',
    'FLOOR_REVIEW_LIMIT': 'Set policy.review_envelope_limit_bytes at or below the configured limit; do not raise shared limits. Register corrected policy with fresh keys.',
    'ARTIFACT_SCHEMA': 'Correct artifact metadata fields to the artifact contract and register corrected evidence with fresh registration and submission keys.',
    'ARTIFACT_DUPLICATE': 'Give each artifact a unique artifact_id and correct evidence_refs, then register corrected evidence with fresh keys.',
    'ARTIFACT_PROVENANCE': 'Reference artifacts whose producer_id and submission_id match this exact submission key; register under fresh keys.',
    'ARTIFACT_MISSING': 'Restore or regenerate the referenced artifact at its declared path, verify its hash/size, and register corrected evidence with fresh keys.',
    'ARTIFACT_CONTRADICTION': 'Recompute sha256 and size_bytes from stable artifact bytes, then register corrected evidence with fresh keys.',
    'ARTIFACT_DRIFT': 'Regenerate changed evidence and register a new scope with fresh registration and submission keys.',
    'SCOPE_NOT_REGISTERED': 'Register the scope for this caller and submission key using current bound status before preparing submission.',
    'SCOPE_BINDING_MISMATCH': 'Refresh bound status and register a scope matching its authenticated worktree and trusted publication provenance; do not forge or bypass provenance.',
    'SCOPE_MISMATCH': 'Use the registered scope sprint_id and policy_digest, or register corrected policy with fresh keys.',
}


def _fail(code, field, message, recovery=None):
    repair = _SCOPE_RECOVERY.get(code, 'Correct the indicated scope input using current bound status and fresh registration/submission keys; do not weaken trusted floors or forge publication provenance.')
    raise ScopeError(code, field, message, recovery or repair)


def _floors(configured):
    """Extract only shared trust floors; feature-specific content is not global policy."""
    return {
        'required_packs': {value for values in configured['coverage'].values() for value in values},
        'selectors': {k: set(v) for k, v in configured['selector_requirements'].items()},
        'roles': set(configured['review_roles']),
        'exceptions': configured['owner_exceptions'],
        'overrides': configured['config_overrides'],
        'review_limit': configured['review_envelope_limit_bytes'],
    }


def register_author_scope(store_db, bound_context, caller, payload, idempotency_key, *, artifact_dir):
    """Validate, verify, and freeze author-supplied per-feature policy/evidence."""
    expected = {'contract_version', 'policy', 'artifacts', 'submission_idempotency_key'}
    if not isinstance(payload, dict) or set(payload) != expected:
        _fail('SCOPE_INPUT', 'payload', 'Expected contract_version, policy, artifacts, and submission_idempotency_key only.')
    if type(payload.get('contract_version')) is not int or payload['contract_version'] != 1:
        _fail('SCOPE_INPUT', 'contract_version', 'contract_version must be the integer 1 (booleans, floats, and strings are invalid).')
    try:
        payload_digest=store.digest(payload)
    except store.ProtocolError:
        _fail('SCOPE_INPUT', 'payload', 'Payload must be strict canonical JSON with string keys, finite values, and integer protocol numbers.')
    if not isinstance(caller, str) or not caller.strip() or not isinstance(idempotency_key, str) or not idempotency_key.strip():
        _fail('SCOPE_INPUT', 'caller/idempotency_key', 'Caller and registration key are required.')
    previous=store_db.get_author_scope_registration_receipt(caller=caller,command_key=idempotency_key,payload_digest=payload_digest)
    if previous is not None:return previous
    archive = Path(artifact_dir).resolve()
    repo = Path(bound_context.repo).resolve()
    if archive == repo or repo in archive.parents:
        raise ValueError('author evidence archive must be outside the author worktree')
    try:
        validator = Draft202012Validator({'$schema': preflight._PREFLIGHT_SCHEMA['$schema'],
            '$defs': preflight._PREFLIGHT_SCHEMA['$defs'], '$ref': '#/$defs/policy'})
        validator.validate(payload['policy'])
    except ValidationError as exc:
        field, repair = store.schema_error_details(exc, kind='policy')
        _fail('POLICY_SCHEMA', 'policy' + ('.' + field if field else ''),
              'Policy does not match the strict version-1 policy contract.',
              repair + ' Register corrected inputs with a fresh registration key and fresh submission key.')
    except Exception as exc:
        raise ScopeInfrastructureError('Policy validator failed unexpectedly; retain the registration key and check status after tooling recovery.') from exc
    policy = copy.deepcopy(payload['policy'])
    repo = str(repo)
    if policy['source_worktree'] != repo or policy['author'] != bound_context.authenticated_author or policy['author'].get('worktree') != repo:
        _fail('POLICY_BINDING', 'policy.author/source_worktree', 'Author and worktree must exactly match bound authenticated context.')
    floor = bound_context.floors if isinstance(bound_context, AuthorWorktreeContext) else _floors(bound_context.policy)
    declared_packs={value for values in policy['coverage'].values() for value in values}
    if not floor['required_packs'] <= declared_packs:
        _fail('FLOOR_COVERAGE', 'policy.coverage', 'Per-feature coverage weakens configured shared required-pack floor.')
    for kind, required in floor['selectors'].items():
        if not required <= set(policy['selector_requirements'].get(kind, [])):
            _fail('FLOOR_SELECTORS', f'policy.selector_requirements.{kind}', 'Per-feature selectors weaken configured shared floor.')
    if not floor['roles'] <= set(policy['review_roles']): _fail('FLOOR_REVIEW_ROLES', 'policy.review_roles', 'Required shared review roles cannot be removed.')
    if policy['owner_exceptions'] != floor['exceptions']: _fail('FLOOR_OWNER_EXCEPTIONS', 'policy.owner_exceptions', 'Owner exceptions must exactly match configured trusted list.')
    if policy['config_overrides'] != floor['overrides']: _fail('FLOOR_CONFIG_OVERRIDES', 'policy.config_overrides', 'Config overrides must exactly match configured trusted list.')
    if policy['review_envelope_limit_bytes'] > floor['review_limit']:
        _fail('FLOOR_REVIEW_LIMIT', 'policy.review_envelope_limit_bytes', 'Review envelope limit cannot exceed configured limit.')
    key = payload['submission_idempotency_key']
    if not isinstance(key, str) or not key.strip(): _fail('SCOPE_INPUT', 'submission_idempotency_key', 'Submission key must be non-empty.')
    submission = store_db.submission_id_for(caller,key)
    verified=[]
    if not isinstance(payload['artifacts'], list): _fail('ARTIFACT_SCHEMA', 'artifacts', 'Artifacts must be an array.')
    seen=set()
    for index, artifact in enumerate(payload['artifacts']):
        try: artifact=store.validate(artifact, 'artifact')
        except store.ProtocolError as exc:
            field=getattr(exc,'field',None) or ''
            repair=getattr(exc,'recovery',None) or 'Correct artifact metadata to match the artifact contract.'
            _fail('ARTIFACT_SCHEMA', f'artifacts.{index}' + ('.'+field if field else ''),
                  'Artifact metadata does not match the required artifact contract.',
                  repair + ' Register corrected evidence using a fresh registration key and fresh submission key.')
        if artifact['artifact_id'] in seen: _fail('ARTIFACT_DUPLICATE', 'artifacts', 'Artifact IDs must be unique.')
        seen.add(artifact['artifact_id'])
        if (artifact.get('producer_kind') != 'SUBMISSION' or artifact.get('run_id') is not None or
            artifact.get('producer_task_id') is not None or artifact.get('producer_id') != submission or artifact.get('submission_id') != submission):
            _fail('ARTIFACT_PROVENANCE', 'artifacts', 'Artifacts must be unscoped SUBMISSION artifacts for this exact submission key.')
        try: data=Path(artifact['path']).read_bytes()
        except FileNotFoundError as exc: _fail('ARTIFACT_MISSING', 'artifacts.path', str(exc))
        except OSError as exc: raise ScopeInfrastructureError(f'Cannot read artifact {artifact["artifact_id"]}: {exc}') from exc
        if hashlib.sha256(data).hexdigest()!=artifact['sha256'] or len(data)!=artifact['size_bytes']:
            _fail('ARTIFACT_CONTRADICTION', 'artifacts', f"Artifact bytes contradict metadata: {artifact['artifact_id']}")
        # Store only verified declared bytes, under their content-addressed name.
        try:
            from workflow_submission import _write_immutable
        except ModuleNotFoundError:
            from tooling.agent_workflow.workflow_submission import _write_immutable
        target=archive/submission/artifact['sha256']/Path(artifact['path']).name
        try: frozen_path=_write_immutable(target,data)
        except OSError as exc: raise ScopeInfrastructureError(f'Cannot archive artifact {artifact["artifact_id"]}: {exc}') from exc
        frozen_artifact=copy.deepcopy(artifact)
        frozen_artifact['path']=str(frozen_path)
        verified.append(frozen_artifact)
    frozen={'contract_version':1,'policy':policy,'artifacts':verified,'submission_idempotency_key':key,
            'submission_id':submission,'policy_digest':store.digest(policy),
            'binding':{'repo':repo,'authenticated_author':copy.deepcopy(bound_context.authenticated_author),
                       'publication_receipt_path':str(Path(bound_context.publication_receipt_path).resolve()),
                       'publication_receipt_sha256':bound_context.publication_receipt_sha256}}
    return store_db.register_author_scope(caller=caller,command_key=idempotency_key,payload_digest=payload_digest,scope=frozen)


def effective_context(store_db, bound_context, *, caller, submission_idempotency_key, sprint_id, policy_digest):
    scope=store_db.get_author_scope(caller=caller,submission_key=submission_idempotency_key)
    if scope is None: _fail('SCOPE_NOT_REGISTERED', 'submission_idempotency_key', 'No registered author scope for caller and submission key.')
    binding=scope['binding']
    if (binding['repo']!=str(Path(bound_context.repo).resolve()) or binding['authenticated_author']!=bound_context.authenticated_author or
        Path(binding['publication_receipt_path'])!=Path(bound_context.publication_receipt_path).resolve() or
        binding['publication_receipt_sha256']!=bound_context.publication_receipt_sha256):
        _fail('SCOPE_BINDING_MISMATCH','binding','Registered scope does not match current bound authority context.')
    if scope['policy']['sprint_id']!=sprint_id or scope['policy_digest']!=policy_digest:
        _fail('SCOPE_MISMATCH','sprint_id/policy_digest','Registered scope sprint or policy digest does not match submission.')
    for artifact in scope['artifacts']:
        try: data=Path(artifact['path']).read_bytes()
        except FileNotFoundError as exc: _fail('ARTIFACT_MISSING','artifacts.path',str(exc))
        except OSError as exc: raise ScopeInfrastructureError(f'Cannot reverify artifact {artifact["artifact_id"]}: {exc}') from exc
        if hashlib.sha256(data).hexdigest()!=artifact['sha256'] or len(data)!=artifact['size_bytes']:
            _fail('ARTIFACT_DRIFT','artifacts',f"Registered artifact changed: {artifact['artifact_id']}")
    if isinstance(bound_context, AuthorWorktreeContext):
        return preflight.PreflightContext(Path(bound_context.repo), copy.deepcopy(bound_context.authenticated_author),
            copy.deepcopy(scope['policy']), copy.deepcopy(scope['artifacts']),
            Path(bound_context.publication_receipt_path), bound_context.publication_receipt_sha256)
    return replace(bound_context, policy=copy.deepcopy(scope['policy']), artifacts=copy.deepcopy(scope['artifacts']))
