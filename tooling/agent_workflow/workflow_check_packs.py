"""Resolve the finite, typed check-pack registry into an existing gate plan."""
from pathlib import Path
import re
import workflow_native_fixtures as native_fixtures

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
        _fail('unit selectors must be narrow Java class selectors only; remove method suffixes and select the enclosing class')
    if pid == 'readiness' and any(x not in _READINESS for x in selectors):
        _fail('readiness selectors must be allowed readiness categories')
    if pid == 'focused_gametest' and any(not _TAG.fullmatch(x) for x in selectors):
        _fail('focused GameTest selectors must be safe tags')


def _validate_unit_classes(repo, selectors, *, field='policy.selector_requirements.unit'):
    """Bind selectors to source class identities; XML evidence only proves classes."""
    recovery = 'Correct selectors in the named field; use only exact fully qualified top-level Java test class names from src/test/java. Method and nested-class selectors are unsupported. Register with fresh keys.'
    root = Path(repo) / 'src' / 'test' / 'java'
    identities = set()
    nested_names = set()
    unreadable = []
    for source in root.rglob('*.java') if root.is_dir() else ():
        try:
            text = source.read_text(encoding='utf-8')
        except (OSError, UnicodeError) as exc:
            unreadable.append(f'{source.relative_to(root)} ({type(exc).__name__})')
            continue
        clean = _java_code_only(text)
        package = re.search(r'^\s*package\s+([\w.]+)\s*;', clean, re.M)
        prefix = package.group(1) + '.' if package else ''
        depth = 0
        for match in re.finditer(r'[{}]|\b(?:class|interface|enum|record)\s+([A-Za-z_$][\w$]*)', clean):
            token = match.group(0)
            if token == '}': depth = max(0, depth - 1)
            elif token == '{': depth += 1
            elif depth == 0: identities.add(prefix + match.group(1))
            else: nested_names.add(prefix + match.group(1))
    if unreadable:
        raise ProtocolError('unable to verify Java unit selectors because source files could not be read: ' + ', '.join(unreadable[:10]) + (f' (+{len(unreadable)-10} more)' if len(unreadable)>10 else ''), field=field, recovery=recovery)
    bad = []
    for selector in sorted(selectors):
        if selector in identities:
            continue
        # A plausible method selector whose enclosing class exists is rejected explicitly.
        if any(selector.startswith(name + '.') for name in identities):
            bad.append(selector + ' (method selectors are unsupported; use the enclosing class selector)')
        elif selector in nested_names:
            bad.append(selector + ' (nested-class selectors are unsupported; use the enclosing top-level class selector)')
        elif any(name.startswith(selector + '.') for name in identities):
            bad.append(selector + ' (nested-class selectors are unsupported; use the enclosing top-level class selector)')
        else:
            bad.append(selector)
    if bad:
        suggestions = []
        for selector in sorted(selectors):
            basename = selector.rsplit('.', 1)[-1]
            suggestions.extend(sorted(name for name in identities if name.rsplit('.', 1)[-1] == basename))
        message = 'Unknown unit test class selectors: ' + ', '.join(bad[:10]) + (f' (+{len(bad)-10} more)' if len(bad)>10 else '')
        if suggestions:
            unique = list(dict.fromkeys(suggestions))
            recovery = 'Valid top-level class selector(s): ' + ', '.join(unique[:10]) + (f' (+{len(unique)-10} more)' if len(unique)>10 else '') + '. ' + recovery
        raise ProtocolError(message, field=field, recovery=recovery)


def _java_code_only(text):
    """Blank comments and literals while retaining line breaks and braces."""
    pattern = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
    return pattern.sub(lambda m: re.sub(r'[^\n]', ' ', m.group(0)), text)


def validate_policy_inputs(repo, policy, selected=None):
    """Validate shared package and required unit selector inputs for both entry paths."""
    covered = set()
    if isinstance(policy, dict) and isinstance(policy.get('coverage'), dict):
        for values in policy['coverage'].values():
            if isinstance(values, list): covered.update(x for x in values if isinstance(x, str))
    if 'package' in covered:
        artifacts = policy.get('package_artifacts')
        if not isinstance(artifacts, list) or not artifacts:
            raise ProtocolError('package coverage requires at least one built .jar or .zip archive', field='policy.package_artifacts', recovery='Declare a safe repo-relative built .jar or .zip distribution archive (typically under build/libs), then register with fresh keys.')
        for artifact in artifacts:
            if not isinstance(artifact, str) or not artifact:
                raise ProtocolError('package artifacts must be safe repo-relative archive paths', field='policy.package_artifacts', recovery='Declare a safe repo-relative .jar or .zip distribution archive, then register with fresh keys.')
            _repo_path(repo, artifact, 'policy.package_artifacts')
            if Path(artifact).suffix.lower() not in ('.jar', '.zip'):
                raise ProtocolError(f'package artifact {artifact!r} must identify a built .jar or .zip distribution output', field='policy.package_artifacts', recovery='Use a safe repo-relative .jar or .zip build output (typically build/libs), then register with fresh keys.')
    reqs = policy.get('selector_requirements', {}) if isinstance(policy, dict) else {}
    unit = reqs.get('unit', []) if isinstance(reqs, dict) else []
    if unit and selected is None:
        _validate_unit_classes(repo, set(unit), field='policy.selector_requirements.unit')


def _repo_path(repo, value, field):
    if not isinstance(value, str) or not value or Path(value).is_absolute() or '\\' in value or any(x in ('.', '..', '') for x in value.split('/')):
        _fail(f'{field} must be a safe repo-relative path')
    try:
        return safe(repo, value)
    except Exception as exc:
        _fail(f'{field} must remain inside the repository: {exc}')


def resolve_diagnostic_plan(request, *, repo, policy):
    """Resolve a narrow diagnostic plan against trusted criterion IDs."""
    if not isinstance(request, dict) or request.get('purpose') != 'DIAGNOSTIC' or request.get('implementation_complete') is not False:
        _fail('diagnostic plans require purpose DIAGNOSTIC and implementation_complete=false')
    if not isinstance(request.get('diagnostic_rationale'), str) or not request['diagnostic_rationale'].strip():
        _fail('diagnostic_rationale must be non-empty')
    plan = request.get('diagnostic_plan')
    if not isinstance(plan, dict) or plan.get('kind') != 'diagnostic' or plan.get('manual_steps') != []:
        _fail('diagnostic_plan must be a typed diagnostic plan with no manual steps')
    policy_ids = policy.get('criteria')
    if not isinstance(policy_ids, list) or not policy_ids:
        _fail('trusted policy criteria must be non-empty')
    criteria = request.get('criteria')
    if not isinstance(criteria, list) or not criteria or any(not isinstance(c, dict) or c.get('status') != 'DIAGNOSTIC' for c in criteria):
        _fail('diagnostic criteria must use DIAGNOSTIC status')
    ids = [c.get('criterion_id') for c in criteria]
    if len(ids) != len(set(ids)) or set(ids) != set(policy_ids):
        _fail('diagnostic criteria must exactly match trusted policy criteria')
    accept = plan.get('acceptance_criteria')
    if not isinstance(accept, list) or not accept or any(not isinstance(x, str) or not x.strip() for x in accept) or not set(accept).issubset(policy_ids):
        _fail('diagnostic plan criteria must be a non-empty subset of trusted criteria')
    if not isinstance(plan.get('checks'), list) or not plan['checks']:
        _fail('diagnostic plans require checks')
    for check in plan['checks']:
        if not isinstance(check, dict) or not isinstance(check.get('covers'), list) or not check['covers'] or not set(check['covers']).issubset(accept):
            _fail('diagnostic check coverage must be a non-empty subset of plan criteria')
    try:
        resolved = resolve_gate_plan(plan, Path(repo))
        for index, check in enumerate(resolved.get('checks', [])):
            if check.get('kind') == 'gametest':
                try:
                    native_fixtures.accepted_binding(check, request.get('input_digest', ''), index)
                except (ValueError, TypeError, AttributeError) as exc:
                    fixture = f"build/workflow-fixtures/{str(request.get('input_digest', ''))[:12]}"
                    raise ProtocolError('diagnostic GameTest fixture binding is invalid.',
                        field=f'diagnostic_plan.checks.{index}',
                        recovery=f'Require exactly -Pms14GameTestDir={fixture} and log {fixture}/logs/latest.log; recompute the plan digest, then prepare a fresh scope and submission key.') from exc
    except ProtocolError:
        raise
    except Exception as exc:
        _fail(f'diagnostic plan rejected: {exc}')
    return {'registry_version': REGISTRY_VERSION, 'packs': [], 'plan': resolved,
            'program_checks': ['managed_config_provenance']}


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
        if pid == 'unit':
            _validate_selectors(pid, selected[pid])
            _validate_unit_classes(repo, set(required_selectors), field='policy.selector_requirements.unit')
            extras = set(selected[pid]) - set(required_selectors)
            if extras:
                _validate_unit_classes(repo, extras, field='check_packs.unit.selectors')
        if not set(required_selectors).issubset(selected[pid]):
            _fail(f'{pid} request selectors must include trusted required selectors')
    if 'unit' in focused:
        validate_policy_inputs(repo, policy, selected)
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
    validate_policy_inputs(repo, policy, selected)
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
