"""Bounded execution adaptations for trusted managed native checks.

This does not reset arbitrary command effects or copy the source tree. It adapts
the typed GameTestDir property owned by the managed check-pack producer and the
exact legacy clean/build package command in already-admitted in-flight plans.
"""
import copy
import hashlib
import re
from pathlib import Path

try:
    import workflow_runtime, workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_runtime, workflow_store


POLICY_ID = 'managed-gametest-runtime-v1'
PACKAGE_POLICY_ID = 'managed-package-no-clean-v1'
UNIT_POLICY_ID = 'managed-unit-fresh-evidence-v1'
_LEGACY_PACKAGE_ARGV = ['./gradlew', 'clean', 'build', '--no-daemon']
_MANAGED = re.compile(r'^build/workflow-fixtures/([0-9a-f]{12})$')
_RETRY = re.compile(r'^build/workflow-fixtures/native-retry/[0-9a-f]{32}$')
_ACCEPTANCE = re.compile(r'^accept_[A-Za-z0-9_-]{1,128}$')


def accepted_binding(check, input_digest, check_index, *, regression_context=None):
    """Validate and describe the immutable producer fixture for a managed GameTest."""
    if check.get('kind') == 'build' and check.get('argv') == _LEGACY_PACKAGE_ARGV and check.get('artifacts'):
        return {'policy_id': PACKAGE_POLICY_ID, 'mode': 'ACCEPTED_PACKAGE', 'check_index': check_index,
            'check_digest': workflow_store.digest(check), 'accepted_argv': list(check['argv']),
            'execution_argv': ['./gradlew', 'build', '--no-daemon']}
    if check.get('kind') == 'junit' and isinstance(check.get('argv'),list) and check['argv'][:2]==['./gradlew','test']:
        argv=check.get('argv'); selectors=check.get('selectors')
        valid_selector=lambda x: isinstance(x,str) and re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)?',x)
        if (not isinstance(argv,list) or not isinstance(selectors,list) or not selectors or
            any(not valid_selector(x) for x in selectors) or
            argv not in ( ['./gradlew','test',*sum((['--tests',x] for x in selectors),[]),'--no-daemon'], ['./gradlew','test','--rerun',*sum((['--tests',x] for x in selectors),[]),'--no-daemon'] )):
            raise ValueError('managed unit check is not the exact trusted selected-test command')
        fresh='--rerun' in argv
        if fresh:
            return None
        return {'policy_id':UNIT_POLICY_ID,'mode':'ACCEPTED_UNIT','check_index':check_index,
            'check_digest':workflow_store.digest(check),'accepted_argv':list(argv),
            'execution_argv':['./gradlew','test','--rerun',*sum((['--tests',x] for x in selectors),[]),'--no-daemon']}
    if check.get('kind') != 'gametest':
        return None
    argv = check.get('argv')
    if not isinstance(argv, list):
        raise ValueError('managed GameTest argv is malformed')
    args = [arg for arg in argv if isinstance(arg, str) and arg.startswith('-Pms14GameTestDir=')]
    if len(args) != 1:
        raise ValueError('unsafe or unmanaged GameTest fixture configuration blocks native retry')
    fixture = args[0].split('=', 1)[1]
    if regression_context is not None:
        acceptance_id = regression_context.get('acceptance_id')
        expected = 'build/workflow-regression/' + str(acceptance_id)
        if (not _ACCEPTANCE.fullmatch(acceptance_id or '') or
                regression_context.get('run_kind') != 'POST_ACCEPTANCE_REGRESSION' or
                not regression_context.get('run_id') or
                regression_context.get('input_digest') != input_digest or fixture != expected or
                check.get('log') != expected + '/logs/latest.log'):
            raise ValueError('regression GameTest fixture does not match the saved owner acceptance')
        return {'policy_id': POLICY_ID, 'mode': 'ACCEPTED_REGRESSION', 'check_index': check_index,
            'check_digest': workflow_store.digest(check), 'run_id': regression_context['run_id'],
            'acceptance_id': acceptance_id, 'fixture_dir': expected, 'log': check['log']}
    match = _MANAGED.fullmatch(fixture)
    if (match is None or match.group(1) != input_digest[:12] or
            check.get('log') != fixture + '/logs/latest.log'):
        raise ValueError('unsafe or unmanaged GameTest fixture configuration blocks native retry')
    return {'policy_id': POLICY_ID, 'mode': 'ACCEPTED_ORIGINAL', 'check_index': check_index,
        'check_digest': workflow_store.digest(check), 'fixture_dir': match.group(0), 'log': check['log']}


def derive_retry_bindings(checks, *, input_digest, run_id, generation, task_id, prefix_count):
    """Derive fresh unique GameTest fixture paths only for the uncompleted suffix."""
    bindings = []
    for index, check in enumerate(checks):
        if check.get('kind') != 'gametest' or index < prefix_count:
            continue
        original = accepted_binding(check, input_digest, index)
        name = hashlib.sha256(f'{run_id}:{generation}:{task_id}:{index}'.encode()).hexdigest()[:32]
        fixture_dir = 'build/workflow-fixtures/native-retry/' + name
        argv = list(check['argv'])
        at = next(i for i, arg in enumerate(argv) if arg.startswith('-Pms14GameTestDir='))
        argv[at] = '-Pms14GameTestDir=' + fixture_dir
        bindings.append({'policy_id': POLICY_ID, 'mode': 'FRESH_RETRY', 'check_index': index,
            'check_digest': workflow_store.digest(check), 'task_id': task_id,
            'generation': generation, 'accepted_fixture_dir': original['fixture_dir'],
            'fixture_dir': fixture_dir, 'log': fixture_dir + '/logs/latest.log', 'argv': argv})
    return bindings


def validate_retry_bindings(checks, bindings, *, input_digest, run_id, generation, task_id, prefix_count):
    expected = derive_retry_bindings(checks, input_digest=input_digest, run_id=run_id,
        generation=generation, task_id=task_id, prefix_count=prefix_count)
    if bindings != expected:
        raise ValueError('retry GameTest runtime fixture derivation differs from applicability proof')
    return expected


def reserve_retry_bindings(repo, bindings):
    """Create only fresh ignored managed directories; never clear or reuse dirty paths."""
    repo = Path(repo).resolve()
    for binding in bindings:
        rel = binding.get('fixture_dir')
        if not isinstance(rel, str) or not _RETRY.fullmatch(rel):
            raise ValueError('retry GameTest fixture path is outside the managed runtime namespace')
        if workflow_runtime.git_status(repo, 'check-ignore', '-q', rel, quiet=True) != 0:
            raise ValueError('retry GameTest fixture parent is not ignored by the worktree')
        path = workflow_runtime.safe(repo, rel)
        cursor = repo
        for part in Path(rel).parts:
            cursor = cursor / part
            if cursor.is_symlink():
                raise ValueError('retry GameTest fixture path contains a symlink; refusing to write through it')
        claim = {'policy_id': binding['policy_id'], 'task_id': binding['task_id'],
            'generation': binding['generation'], 'check_index': binding['check_index'],
            'check_digest': binding['check_digest'], 'fixture_dir': rel}
        claim_path = path.parent / '.claims' / (path.name + '.json')
        expected = workflow_store.canonical_json(claim).encode('utf-8')
        if claim_path.parent.is_symlink() or claim_path.is_symlink():
            raise ValueError('retry GameTest fixture ownership claim path contains a symlink')
        claim_path.parent.mkdir(parents=True, exist_ok=True)
        if claim_path.exists():
            if not claim_path.is_file() or claim_path.read_bytes() != expected:
                raise ValueError('retry GameTest fixture ownership claim differs; refusing to reuse it')
        else:
            if path.exists():
                raise ValueError('retry GameTest fixture directory already exists without its ownership claim')
            try:
                with claim_path.open('xb') as stream:
                    stream.write(expected); stream.flush()
            except FileExistsError:
                if not claim_path.is_file() or claim_path.read_bytes() != expected:
                    raise ValueError('retry GameTest fixture ownership claim raced with another producer')
        path.mkdir(parents=True, exist_ok=True)
        if any(path.iterdir()):
            raise ValueError('retry GameTest fixture is not initially empty; refusing to clear or overwrite it')


def adapt_plan(plan, bindings):
    """Return a plan with only proven retry suffix GameTest argv/log adapted."""
    result = copy.deepcopy(plan)
    by_index = {binding['check_index']: binding for binding in bindings}
    for index, binding in by_index.items():
        if index >= len(result.get('checks', [])):
            raise ValueError('retry fixture check index is outside the accepted plan')
        check = result['checks'][index]
        if workflow_store.digest(check) != binding['check_digest']:
            raise ValueError('retry fixture binding does not match the accepted GameTest check')
        if binding.get('policy_id') == PACKAGE_POLICY_ID:
            if check.get('kind') != 'build' or check.get('argv') != _LEGACY_PACKAGE_ARGV or not check.get('artifacts'):
                raise ValueError('package execution binding does not match the exact accepted legacy package command')
            check['argv'] = list(binding['execution_argv'])
        elif binding.get('policy_id') == UNIT_POLICY_ID:
            accepted=accepted_binding(check,'',index)
            if binding != accepted:
                raise ValueError('unit execution binding does not match exact accepted selected-test command')
            check['argv']=list(binding['execution_argv'])
        else:
            if check.get('kind') != 'gametest':
                raise ValueError('retry fixture binding does not match the accepted GameTest check')
            check['argv'] = list(binding['argv'])
            check['log'] = binding['log']
    return result


def package_execution_bindings(checks):
    """Return only the narrowly authorized legacy clean/build adaptation bindings."""
    return [accepted_binding(check, '', index) for index, check in enumerate(checks)
            if check.get('kind') == 'build' and check.get('argv') == _LEGACY_PACKAGE_ARGV
            and check.get('artifacts')]

def managed_execution_bindings(checks):
    return [binding for index,check in enumerate(checks)
            if check.get('kind')=='junit' and check.get('selectors')
            for binding in [accepted_binding(check,'',index)] if binding is not None]


def execution_binding(check, input_digest, check_index, record, retry_binding=None, *, repo=None, regression_context=None):
    """Record accepted-original or fresh-retry fixture attribution for provenance."""
    if check.get('kind')=='junit' and check.get('selectors'):
        result=accepted_binding(check,input_digest,check_index)
        if result is None:return None
        planned=list(record.get('argv',[])); actual=list(record.get('executed_argv',planned))
        expected=result['execution_argv']
        if planned != expected or repo is None or actual != workflow_runtime.execution_argv(repo,expected):
            raise ValueError('managed unit executed argv differs from authorized fresh selected-test execution')
        result['actual_execution_argv']=actual
        return result
    if (check.get('kind') == 'build' and check.get('artifacts') and
            (check.get('argv') == _LEGACY_PACKAGE_ARGV or retry_binding and retry_binding.get('policy_id') == PACKAGE_POLICY_ID)):
        result = dict(retry_binding) if retry_binding else accepted_binding(check, input_digest, check_index)
        planned = list(record.get('argv', []))
        actual = list(record.get('executed_argv', planned))
        if planned != result['execution_argv'] or repo is None or actual != workflow_runtime.execution_argv(repo, result['execution_argv']):
            raise ValueError('legacy package executed argv differs from authorized non-destructive build')
        result['actual_execution_argv'] = actual
        return result
    if check.get('kind') != 'gametest': return None
    if retry_binding is None:
        result = accepted_binding(check, input_digest, check_index, regression_context=regression_context)
    else:
        result = dict(retry_binding)
    result['execution_argv'] = list(record.get('executed_argv', record['argv']))
    return result


def verify_original_execution(check, input_digest, check_index, record, provenance, repo,
                              *, regression_context=None):
    """Keep a completed prefix bound to its original accepted shared fixture."""
    expected = accepted_binding(check, input_digest, check_index, regression_context=regression_context)
    if expected is None:return provenance is None
    if expected is not None and expected.get('policy_id')==UNIT_POLICY_ID:
        planned=list(record.get('argv',[])); actual=list(record.get('executed_argv',planned))
        if planned==expected['accepted_argv'] and actual==workflow_runtime.execution_argv(repo,planned):
            return provenance is None
        expected['actual_execution_argv']=workflow_runtime.execution_argv(repo,expected['execution_argv'])
        return provenance==expected and planned==expected['execution_argv'] and actual==expected['actual_execution_argv']
    if expected is None:
        return provenance is None
    if expected.get('policy_id') == PACKAGE_POLICY_ID:
        planned=list(record.get('argv', []))
        actual=list(record.get('executed_argv', planned))
        expected_actual=workflow_runtime.execution_argv(repo,expected['execution_argv'])
        expected['actual_execution_argv']=expected_actual
        return (provenance == expected and planned == expected['execution_argv'] and actual == expected_actual)
    expected['execution_argv'] = list(record.get('executed_argv', record.get('argv', [])))
    source_log = record.get('source_log')
    expected_log = workflow_runtime.safe(repo, expected['log'])
    return provenance == expected and source_log is not None and Path(source_log).resolve() == expected_log
