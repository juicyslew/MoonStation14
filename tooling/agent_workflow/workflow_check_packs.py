"""Resolve the finite, typed check-pack registry into an existing gate plan."""
from pathlib import Path
import re

from workflow_store import ProtocolError, canonical_json
from workflow_runtime import resolve_gate_plan, validate_gate_plan, safe

REGISTRY_VERSION = 1
_KINDS = {
    'unit': ('junit', 'unit', 'test'),
    'readiness': ('readiness', 'readiness', 'featureReadiness'),
    'focused_gametest': ('gametest', 'gametest', 'runGameTestServer'),
    'full_gametest': ('gametest', 'gametest', 'runGameTestServer'),
    'package': ('build', 'package', 'build'),
    'provenance': ('program', 'provenance', 'managed_config_provenance'),
}
_JAVA_SELECTOR = re.compile(r'^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*$')
_TAG = re.compile(r'^[A-Za-z0-9_.:-]+$')
_READINESS = {'ui', 'interaction', 'prediction', 'preview'}


def _fail(message):
    raise ProtocolError(message)


def _strings(value, field, *, nonempty=True):
    if not isinstance(value, list) or (nonempty and not value) or any(not isinstance(x, str) or not x.strip() for x in value):
        _fail(f'{field} must be a ' + ('non-empty' if nonempty else 'possibly empty') + ' list of non-empty strings')
    return value


def _validate_selectors(pid, selectors):
    if len(set(selectors)) != len(selectors):
        _fail(f'{pid} selectors must be unique')
    if pid == 'unit' and any(not _JAVA_SELECTOR.fullmatch(x) for x in selectors):
        _fail('unit selectors must be narrow Java class or method selectors')
    if pid == 'readiness' and any(x not in _READINESS for x in selectors):
        _fail('readiness selectors must be allowed readiness categories')
    if pid == 'focused_gametest' and any(not _TAG.fullmatch(x) for x in selectors):
        _fail('focused GameTest selectors must be safe tags')


def _repo_path(repo, value, field):
    if not isinstance(value, str) or not value or Path(value).is_absolute() or '\\' in value or any(x in ('.', '..', '') for x in value.split('/')):
        _fail(f'{field} must be a safe repo-relative path')
    try:
        return safe(repo, value)
    except Exception as exc:
        _fail(f'{field} must remain inside the repository: {exc}')


def resolve_check_packs(request, *, repo, policy):
    """Return deterministic frozen gate plan; does not execute checks or write files."""
    if not isinstance(request, dict) or not isinstance(policy, dict):
        _fail('request and policy must be objects')
    try:
        canonical_json(request)
        canonical_json(policy)
    except (TypeError, ValueError) as exc:
        _fail(f'request and policy must contain JSON-compatible values: {exc}')
    if request.get('purpose') != 'OWNER_READY' or request.get('implementation_complete') is not True:
        _fail('check packs require a complete OWNER_READY submission')
    label = request.get('feature_label')
    if not isinstance(label, str) or not label.strip() or len(label) > 40:
        _fail('feature_label must be non-empty and at most 40 characters')
    rows = request.get('criteria')
    if not isinstance(rows, list) or not rows:
        _fail('criteria must be a non-empty list')
    ids = []
    for row in rows:
        if not isinstance(row, dict) or set(row) != {'criterion_id', 'status', 'evidence_refs'} or row['status'] != 'COMPLETE':
            _fail('each criterion must be a COMPLETE submission criterion')
        cid = row.get('criterion_id')
        if not isinstance(cid, str) or not cid.strip() or cid in ids:
            _fail('criterion IDs must be unique non-empty strings')
        ids.append(cid)
    manual = _strings(request.get('manual_steps'), 'manual_steps')
    criteriapolicy = policy.get('criteria')
    if not isinstance(criteriapolicy, list) or any(not isinstance(x, str) or not x for x in criteriapolicy) or len(set(criteriapolicy)) != len(criteriapolicy) or set(criteriapolicy) != set(ids):
        _fail('policy.criteria must exactly match request criterion IDs')
    coverage = policy.get('coverage')
    if not isinstance(coverage, dict) or set(coverage) != set(ids):
        _fail('policy.coverage must have exactly one entry per request criterion')
    requirements = policy.get('selector_requirements', {})
    if not isinstance(requirements, dict):
        _fail('policy.selector_requirements must be an object')
    packs = request.get('check_packs')
    if not isinstance(packs, list):
        _fail('check_packs must be a list')
    selected = {}
    for pack in packs:
        if not isinstance(pack, dict) or set(pack) != {'pack_id', 'kind', 'selectors'}:
            _fail('each check pack requires exactly pack_id, kind, selectors')
        pid, kind = pack['pack_id'], pack['kind']
        if not isinstance(pid, str) or pid not in _KINDS or pid in selected or not isinstance(kind, str) or kind != pid:
            _fail(f'unknown, duplicate, or mismatched check pack: {pid!r}')
        selectors = _strings(pack['selectors'], f'{pid}.selectors', nonempty=pid not in ('package', 'provenance', 'full_gametest'))
        selected[pid] = selectors
    if not selected:
        _fail('at least one check pack is required')
    focused = set(selected) & {'unit', 'readiness', 'focused_gametest'}
    if set(requirements) != focused:
        _fail('selector_requirements must exactly cover selected focused packs')
    for pid in focused:
        required_selectors = _strings(requirements[pid], f'selector_requirements.{pid}')
        _validate_selectors(pid, required_selectors)
        if not set(required_selectors).issubset(selected[pid]):
            _fail(f'{pid} request selectors must include trusted required selectors')
    if {'full_gametest', 'focused_gametest'}.issubset(selected):
        _fail('select focused or full GameTests, not both')
    for cid in ids:
        mapped = _strings(coverage[cid], f'coverage.{cid}')
        if len(set(mapped)) != len(mapped) or any(not isinstance(pid, str) or pid not in _KINDS for pid in mapped):
            _fail(f'coverage.{cid} contains duplicate or unknown pack IDs')
        if not set(mapped).issubset(selected):
            _fail(f'criterion {cid} requires selected packs: {sorted(set(mapped)-set(selected))}')
    owner_exception = request.get('owner_exception_id')
    if 'full_gametest' in selected:
        exceptions = policy.get('owner_exceptions')
        if not isinstance(exceptions, list):
            _fail('full GameTests require an exact trusted owner exception')
        match = [e for e in exceptions if isinstance(e, dict) and e.get('id') == owner_exception]
        expected = {'id': owner_exception, 'sprint_id': request.get('sprint_id'), 'source_worktree': request.get('source_worktree'), 'input_digest': request.get('input_digest'), 'allow': 'full_gametest'}
        if not owner_exception or len(match) != 1 or set(match[0]) != set(expected) or match[0] != expected:
            _fail('full GameTests require an exact owner exception matching sprint, worktree and input digest')
    fixture = policy.get('fixture')
    if not isinstance(fixture, str) or not _repo_path(repo, fixture, 'policy.fixture').is_file():
        _fail('policy.fixture must be an existing repo-relative file')
    artifacts = policy.get('package_artifacts')
    if not isinstance(artifacts, list) or any(not isinstance(x, str) or not x for x in artifacts):
        _fail('policy.package_artifacts must be a list of repo-relative paths')
    if len(set(artifacts)) != len(artifacts):
        _fail('policy.package_artifacts must not contain duplicates')
    for artifact in artifacts:
        _repo_path(repo, artifact, 'package artifact')
    if 'package' in selected and not artifacts:
        _fail('package pack requires non-empty package_artifacts')
    if 'package' not in selected or 'provenance' not in selected:
        _fail('OWNER_READY feature plans require package and provenance packs')
    checks = []
    covers = {pid: [cid for cid in ids if pid in coverage[cid]] for pid in selected}
    ordered = [x for x in ('provenance', 'unit', 'readiness', 'focused_gametest', 'full_gametest', 'package') if x in selected]
    for pid in ordered:
        selectors = selected[pid]
        if pid == 'provenance':
            if selectors:
                _fail('package and provenance packs require empty selectors')
            continue
        if pid == 'unit':
            _validate_selectors(pid, selectors)
            argv = ['./gradlew', 'test', '--rerun', *sum((['--tests', x] for x in selectors), []), '--no-daemon']
            check = {'argv': argv, 'rationale': 'Run selected unit tests.', 'covers': covers[pid], 'kind': 'junit', 'selectors': selectors}
        elif pid == 'readiness':
            _validate_selectors(pid, selectors)
            check = {'argv': ['./gradlew', 'featureReadiness', '-Pms14Readiness='+','.join(selectors), '--no-daemon'], 'rationale': 'Run selected readiness categories.', 'covers': covers[pid], 'kind': 'readiness', 'selectors': selectors}
        elif pid in ('focused_gametest', 'full_gametest'):
            if pid == 'full_gametest' and selectors:
                _fail('full_gametest requires empty selectors')
            if pid == 'focused_gametest': _validate_selectors(pid, selectors)
            fixture_id = str(request.get('input_digest', ''))[:12]
            argv = ['./gradlew', 'runGameTestServer']
            if pid == 'focused_gametest': argv.append('-Pms14GameTestTags='+','.join(selectors))
            argv.append(f'-Pms14GameTestDir=build/workflow-fixtures/{fixture_id}')
            argv.append('--no-daemon')
            check = {'argv': argv, 'rationale': 'Run focused tagged GameTests.' if pid == 'focused_gametest' else 'Run explicitly owner-authorized full GameTests.', 'covers': covers[pid], 'kind': 'gametest', 'selectors': selectors, 'log': f'build/workflow-fixtures/{fixture_id}/logs/latest.log'}
        else:
            if selectors:
                _fail('package and provenance packs require empty selectors')
            check = {'argv': ['./gradlew', 'cleanJar', 'assemble', '--no-daemon'], 'rationale': 'Produce a fresh package for owner testing.', 'covers': covers[pid], 'kind': 'build', 'artifacts': artifacts}
        checks.append(check)
    plan = {'title': label, 'kind': 'feature', 'acceptance_criteria': ids, 'fixture': fixture, 'manual_steps': manual, 'checks': checks, 'references': {}}
    try:
        resolved = resolve_gate_plan(plan, Path(repo))
        validate_gate_plan(resolved, Path(repo))
    except Exception as exc:
        _fail(f'check-pack plan rejected: {exc}')
    return {'registry_version': REGISTRY_VERSION, 'packs': [{'pack_id': pid, 'kind': pid, 'selectors': selected[pid], 'covers': covers[pid]} for pid in ordered], 'plan': resolved, 'program_checks': ['managed_config_provenance']}
