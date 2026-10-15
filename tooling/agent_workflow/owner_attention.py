"""Separate owner-response responsibility from technical verdict and owner disposition."""
import sys
import time
from pathlib import Path
import importlib.util
import subprocess

def _load_workflow(workflow=None):
    if workflow is not None:
        return workflow
    for name in ('workflow_runtime', '_moonstation14_owner_attention_runtime', 'moonstation14_shared_workflow'):
        module = sys.modules.get(name)
        if module is not None and hasattr(module, 'verify_gate'):
            return module
    base = Path(__file__).resolve().parent
    for filename in ('workflow_runtime.py', 'workflow.py'):
        path = base / filename
        if path.is_file():
            module_name = '_moonstation14_owner_attention_runtime'
            cached = sys.modules.get(module_name)
            if cached is not None and hasattr(cached, 'verify_gate'):
                return cached
            spec = importlib.util.spec_from_file_location(module_name, path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[spec.name] = module
            spec.loader.exec_module(module)
            if hasattr(module, 'verify_gate'):
                return module
    raise ImportError('workflow runtime is unavailable')

w = _load_workflow()


def _candidate(root, ident, workflow=None):
    runtime = _load_workflow(workflow)
    path = Path(root) / 'candidates' / f'{w.validid(ident)}.json'
    return path, runtime.readjson(path)


def register_request(root, candidate, requested_at=None, origin_session_id=None, *, workflow=None, after_feedback_message_id=None, legacy_head=None):
    """Record one fresh publication as awaiting owner; never infer from old ready records."""
    return _register_request(root, candidate, requested_at=requested_at, origin_session_id=origin_session_id, workflow=workflow, after_feedback_message_id=after_feedback_message_id, legacy_head=legacy_head)


def _register_request(root, candidate, *, requested_at, origin_session_id, workflow, after_feedback_message_id, legacy_head):
    runtime = _load_workflow(workflow)
    path, _ = _candidate(root, candidate['id'], runtime)
    current = runtime.readjson(path)
    runtime.verify_gate(current)
    runtime.verify_publication(current, root)
    if current.get('gate_outcome', {}).get('result') != 'ready_for_owner':
        raise ValueError('ready_for_owner verdict required')
    if current.get('owner_decision'):
        raise ValueError('candidate already has owner disposition')
    lock = runtime.sprint_lock(root, candidate['worktree']['worktree'], candidate['sprint_id'])
    try:
        current = runtime.readjson(path)
        if current.get('snapshot_key') != candidate.get('snapshot_key'):
            raise ValueError('candidate changed before owner request registration')
        runtime.verify_gate(current)
        runtime.verify_publication(current, root)
        if current.get('gate_outcome', {}).get('result') != 'ready_for_owner' or current.get('owner_decision'):
            raise ValueError('candidate is not eligible for owner attention')
        review = current.get('owner_review', {})
        if review.get('candidate_id') == current['id'] and review.get('snapshot_key') == current['snapshot_key']:
            if review.get('state') == 'feedback_received' and after_feedback_message_id and review.get('feedback', {}).get('message_id') == after_feedback_message_id:
                generation = int(review.get('request_generation', 1)) + 1
                checked_at = time.time()
                current['owner_review'] = {**review, 'state': 'awaiting_owner', 'requested_at': time.time() if requested_at is None else requested_at,
                    'origin_session_id': origin_session_id, 'request_generation': generation,
                    'readiness': _readiness(True, checked_at, current, None), 'readiness_checked_at': checked_at}
                runtime.writejson(path, current)
                return current['owner_review']
            if not (legacy_head and review.get('state') == 'unconfirmed' and _valid_legacy_proof(legacy_head, current, origin_session_id, requested_at)):
                return review
        if not current.get('revision_sequence'):
            if not _valid_legacy_proof(legacy_head, current, origin_session_id, requested_at):
                raise ValueError('legacy candidate requires explicit observer-bound head proof')
        checked_at = time.time()
        current['owner_review'] = {
            'state': 'awaiting_owner' if origin_session_id else 'unconfirmed',
            'reason': None if origin_session_id else 'gate request has no bound origin session',
            'candidate_id': current['id'], 'snapshot_key': current['snapshot_key'],
            'requested_at': time.time() if requested_at is None else requested_at, 'origin_session_id': origin_session_id,
            'readiness': _readiness(True, checked_at, current, None),
            'request_generation': 1,
        }
        if legacy_head:
            current['owner_review']['legacy_head'] = dict(legacy_head)
        current['owner_review']['readiness_checked_at'] = checked_at
        runtime.writejson(path, current)
        return current['owner_review']
    finally:
        lock.close()


def _valid_legacy_proof(proof, candidate, origin_session_id, requested_at):
    return (isinstance(proof, dict) and proof.get('verified_by') == 'owner_feedback_observer' and
            proof.get('candidate_id') == candidate.get('id') and proof.get('snapshot_key') == candidate.get('snapshot_key') and
            proof.get('origin_session_id') == origin_session_id and proof.get('requested_at') == requested_at and
            isinstance(proof.get('announcement_message_id'), str) and bool(proof['announcement_message_id']))


def current_work(root, workflow=None):
    """Canonical worktree/sprint head selection shared by dashboard and observer consumers."""
    runtime = _load_workflow(workflow)
    grouped = {}
    for path in (Path(root) / 'candidates').glob('*.json') if (Path(root) / 'candidates').exists() else []:
        try:
            candidate = runtime.readjson(path)
            worktree = str(Path(candidate['worktree']['worktree']).resolve()).casefold()
            key = (worktree, candidate.get('sprint_id'))
            grouped.setdefault(key, []).append(candidate)
        except (ValueError, OSError, KeyError, TypeError):
            continue
    result = []
    for (worktree, sprint_id), candidates in sorted(grouped.items(), key=lambda row: (row[0][0], str(row[0][1]))):
        sequenced = [c for c in candidates if int(c.get('revision_sequence', 0) or 0) > 0]
        legacy = [c for c in candidates if int(c.get('revision_sequence', 0) or 0) <= 0]
        head = None
        if sequenced:
            highest = max(int(c['revision_sequence']) for c in sequenced)
            selected = [c for c in sequenced if int(c['revision_sequence']) == highest]
            if len(selected) == 1: head = selected[0]
            else: legacy = selected
        elif legacy:
            marked = [c for c in legacy if (c.get('owner_review', {}).get('legacy_head', {}).get('verified_by') == 'owner_feedback_observer' and
                c['owner_review']['legacy_head'].get('candidate_id') == c.get('id') and
                c['owner_review']['legacy_head'].get('snapshot_key') == c.get('snapshot_key'))]
            if len(marked) == 1: head = marked[0]
        result.append({'worktree': worktree, 'sprint_id': sprint_id, 'head': head,
                       'legacy_candidates': legacy if head is None else []})
    return result


def collect(root, *, workflow=None, refresh=False):
    """Report bounded current candidate responsibility; legacy ready rows stay unconfirmed."""
    root = Path(root)
    workflow = _load_workflow(workflow)
    groups = {'awaiting_owner': [], 'agent_work': [], 'unconfirmed': [], 'completed': []}
    for item in current_work(root, workflow):
        c = item['head']
        if c is None:
            legacy = item['legacy_candidates']
            if legacy:
                groups['unconfirmed'].append({'worktree': item['worktree'], 'sprint_id': item['sprint_id'],
                    'candidate_ids': [x['id'] for x in legacy], 'reason': 'legacy candidate ordering is unknown; reconcile explicit head'})
            continue
        if c.get('owner_decision'):
            if c['owner_decision'].get('decision') == 'accepted': groups['completed'].append(_item(c, 'owner accepted'))
            else: groups['agent_work'].append(_item(c, 'owner disposition recorded; agent responsibility is not pending owner response'))
            continue
        if c.get('gate_outcome', {}).get('result') == 'needs_changes':
            groups['agent_work'].append(_item(c, 'agent is responsible'))
            continue
        review = c.get('owner_review', {})
        if review.get('state') == 'awaiting_owner':
            reason = f"awaiting owner response; readiness last verified at {review.get('readiness_checked_at', 'unknown')} (refresh required for current evidence)"
            if refresh:
                try:
                    workflow.verify_gate(c); workflow.verify_publication(c, root)
                    if c.get('gate_outcome', {}).get('result') != 'ready_for_owner' or review.get('candidate_id') != c['id'] or review.get('snapshot_key') != c.get('snapshot_key'):
                        raise ValueError('owner request identity/verdict mismatch')
                except (ValueError, OSError, KeyError, TypeError, subprocess.CalledProcessError):
                    _save_readiness(root, c, review, False, workflow, 'readiness evidence stale or owner request identity invalid; revalidation required')
                    groups['agent_work'].append(_item(c, 'readiness evidence stale or owner request identity invalid; revalidation required'))
                    continue
                else:
                    _save_readiness(root, c, review, True, workflow, None)
                    current = workflow.readjson(root/'candidates'/f"{c['id']}.json")
                    latest_review = current.get('owner_review', {})
                    if current.get('snapshot_key') != c.get('snapshot_key') or latest_review.get('state') != 'awaiting_owner':
                        continue
            elif (c.get('gate_outcome', {}).get('result') != 'ready_for_owner' or review.get('candidate_id') != c['id'] or review.get('snapshot_key') != c.get('snapshot_key')):
                groups['agent_work'].append(_item(c, 'owner request identity/verdict invalid; revalidation required')); continue
            else:
                cached = review.get('readiness', {})
                if not _cache_matches(c, cached):
                    groups['unconfirmed'].append(_item(c, 'readiness eligibility cache missing or mismatched; refresh required')); continue
                if time.time() - cached.get('checked_at', 0) > 45:
                    groups['unconfirmed'].append(_item(c, 'readiness eligibility cache expired; refresh required')); continue
                if not cached.get('eligible'):
                    groups['agent_work'].append(_item(c, cached.get('reason') or 'revalidation required')); continue
            groups['awaiting_owner'].append(_item(c, reason))
        elif review.get('state') == 'feedback_received':
            groups['agent_work'].append(_item(c, 'owner responded; agent triage required'))
        elif c.get('gate_outcome', {}).get('result') == 'ready_for_owner':
            groups['unconfirmed'].append(_item(c, 'legacy ready request needs reconciliation'))
        else:
            groups['agent_work'].append(_item(c, 'author / integration work; no owner response requested'))
    return groups


def _item(c, reason):
    plan = c.get('gate_plan') or {}
    request = c.get('owner_review') or {}
    return {key: c.get(key) for key in ('id', 'snapshot_key', 'sprint_id', 'worktree', 'submitted_at', 'revision_sequence', 'build_identity', 'unverified')} | {
        'title': plan.get('title'), 'fixture': plan.get('fixture'), 'manual_steps': plan.get('manual_steps', []),
        'agent': c.get('agent'), 'candidate_id': c.get('id'), 'owner_review': request,
        'report_path': c.get('review', {}).get('report_path') or c.get('gate_validation', {}).get('path'),
        'kind': 'Manual testing', 'readiness_checked_at': (request.get('readiness') or {}).get('checked_at'),
        'origin_session_id': request.get('origin_session_id') or c.get('gate_request', {}).get('origin_session_id'), 'requested_at': request.get('requested_at'), 'reason': reason,
    }


def _readiness(eligible, checked_at, candidate, reason):
    review = candidate.get('review', {})
    validation = candidate.get('gate_validation', {})
    return {'checked_at': checked_at, 'eligible': eligible, 'reason': reason,
            'snapshot_key': candidate.get('snapshot_key'), 'review_sha256': review.get('report_sha256'),
            'validation_sha256': validation.get('sha256')}


def _cache_matches(candidate, readiness):
    expected = _readiness(readiness.get('eligible'), readiness.get('checked_at'), candidate, readiness.get('reason'))
    return all(readiness.get(key) == expected[key] for key in ('snapshot_key', 'review_sha256', 'validation_sha256'))


def _save_readiness(root, candidate, review, eligible, workflow, reason):
    runtime = workflow
    lock = runtime.sprint_lock(root, candidate['worktree']['worktree'], candidate['sprint_id'])
    try:
        path, current = _candidate(root, candidate['id'], runtime)
        saved = current.get('owner_review', {})
        if current.get('snapshot_key') != candidate.get('snapshot_key') or saved.get('candidate_id') != candidate.get('id') or saved.get('snapshot_key') != candidate.get('snapshot_key'):
            return
        saved['readiness'] = _readiness(eligible, time.time(), current, reason)
        saved['readiness_checked_at'] = saved['readiness']['checked_at']
        current['owner_review'] = saved
        runtime.writejson(path, current)
    finally: lock.close()


def acknowledge_feedback(root, session_id, message_id, created_at, *, workflow=None, evidence=None):
    """Acknowledge observed user feedback, without deciding acceptance or interpreting its text."""
    runtime = _load_workflow(workflow)
    def has_synthetic_flag(value):
        if isinstance(value, dict):
            if any(value.get(k) for k in ('workflow_gate_completion','review_fixture','automation','system','reminder','tool_result')): return True
            return any(has_synthetic_flag(v) for v in value.values())
        if isinstance(value, list): return any(has_synthetic_flag(v) for v in value)
        return False
    if (not isinstance(evidence, dict) or evidence.get('type') != 'user' or
        not isinstance(evidence.get('id'), str) or not evidence['id'] or
        not isinstance(evidence.get('text'), str) or not evidence['text'].strip() or
        not isinstance(created_at, (int, float)) or isinstance(created_at, bool) or
        not isinstance(message_id, str) or not message_id or evidence.get('id') != message_id or
        evidence.get('session_id') != session_id or evidence.get('time') != created_at or
        has_synthetic_flag(evidence.get('metadata', {}))):
        return {'status': 'ignored', 'reason': 'not an eligible observed user message'}
    root = Path(root)
    matches = []
    for work in current_work(root, runtime):
        c = work['head']
        if c is None: continue
        try:
            r = c.get('owner_review', {})
            if r.get('state') == 'awaiting_owner' and r.get('candidate_id') == c.get('id') and r.get('snapshot_key') == c.get('snapshot_key') and r.get('origin_session_id') == session_id and created_at > r.get('requested_at', float('inf')):
                if evidence.get('candidate_id') and evidence['candidate_id'] != c['id']: continue
                matches.append((root / 'candidates' / f"{c['id']}.json", c))
        except (ValueError, OSError): continue
    if len(matches) != 1:
        return {'status': 'ambiguous' if matches else 'ignored', 'reason': 'multiple requests in conversation' if matches else 'no matching post-request owner prompt'}
    path, c = matches[0]
    lock = runtime.sprint_lock(root, c['worktree']['worktree'], c['sprint_id'])
    try:
        current = runtime.readjson(path); r = current.get('owner_review', {})
        if current.get('snapshot_key') != c.get('snapshot_key') or r.get('state') != 'awaiting_owner' or r.get('origin_session_id') != session_id:
            return {'status': 'ignored', 'reason': 'request changed'}
        if created_at <= r.get('requested_at', float('inf')): return {'status': 'ignored', 'reason': 'message predates request'}
        current['owner_review'] = {**r, 'state': 'feedback_received', 'feedback': {'message_id': str(message_id)[:160], 'at': created_at, 'session_id': session_id}}
        runtime.writejson(path, current)
        return {'status': 'acknowledged', 'candidate_id': c['id'], 'responsibility': 'agent triage required'}
    finally: lock.close()


def ensure_feedback_observer(root):
    """Best-effort handoff to the sibling observer; unavailable observer is explicit health state."""
    try:
        try: import owner_feedback
        except ModuleNotFoundError: from tooling.agent_workflow import owner_feedback
        return owner_feedback.ensure_started(root)
    except (ImportError, OSError, ValueError, RuntimeError) as exc:
        return {'status': 'unavailable', 'reason': str(exc)[:300]}
