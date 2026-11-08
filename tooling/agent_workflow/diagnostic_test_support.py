"""Shared lightweight construction of a real, hash-bound diagnostic request."""
import copy
import sys

from preflight_test_support import make_preflight_case
from workflow_check_packs import resolve_diagnostic_plan
from workflow_store import digest


def make_diagnostic_case(root, *, caller='author', key='diagnostic', checks=None):
    """Return the standard real-Git preflight case configured for DIAGNOSTIC."""
    case = make_preflight_case(root, caller=caller, key=key)
    request = copy.deepcopy(case.request)
    request.update(
        purpose='DIAGNOSTIC', implementation_complete=False,
        criteria=[{**criterion, 'status': 'DIAGNOSTIC'} for criterion in request['criteria']],
        manual_steps=[], check_packs=[],
        diagnostic_rationale='Investigate the observed issue.',
        diagnostic_plan={
            'title':'Investigate issue', 'kind':'diagnostic',
            'acceptance_criteria':['c1'], 'fixture':'manual.md',
            'manual_steps':[], 'references':{},
            'checks':checks if checks is not None else [{
                'argv':[sys.executable, '-c', 'print("diagnostic")'],
                'rationale':'Run a bounded diagnostic.', 'covers':['c1'], 'kind':'command'}],
        })
    request['plan_digest'] = digest(resolve_diagnostic_plan(request, repo=case.repo, policy=case.policy))
    case.request = request
    return case
