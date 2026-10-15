"""Candidate-bound informational progress sidecars and static integration board."""
from __future__ import annotations

import argparse
import json
import os
import time
import uuid
from contextlib import contextmanager
from pathlib import Path


def _replace(source, target):
    deadline = time.monotonic() + 5
    while True:
        try:
            os.replace(source, target)
            return
        except PermissionError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(.01)


def _atomic_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        _replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


@contextmanager
def _render_lock(exchange):
    """Bounded cross-process advisory lock serializes snapshot and replacement."""
    path = exchange / '.integration-status.lock'
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = path.open('a+b')
    deadline, acquired = time.monotonic() + 5, False
    try:
        while not acquired:
            try:
                if os.name == 'nt':
                    import msvcrt
                    handle.seek(0)
                    if handle.read(1) == b'':
                        handle.seek(0); handle.write(b'0'); handle.flush()
                    handle.seek(0)
                    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl
                    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                acquired = True
            except (OSError, BlockingIOError):
                if time.monotonic() >= deadline:
                    raise TimeoutError('timed out acquiring integration board render lock')
                time.sleep(.02)
        yield
    finally:
        if acquired:
            if os.name == 'nt':
                import msvcrt
                handle.seek(0); msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
        handle.close()


def update(root, candidate, phase, **fields):
    """Atomically persist informational progress bound to candidate id+snapshot."""
    root = Path(root)
    ident = candidate['id']
    previous = _read(root / 'progress' / f'{ident}.json') or {}
    old_phase = previous.get('phase')
    same_phase = old_phase == str(phase)
    started_at = previous.get('stage_started_at') if same_phase else time.time()
    def value(key, default=None):
        return fields[key] if key in fields else previous.get(key, default) if same_phase else default
    record = {'candidate_id': ident, 'snapshot_key': candidate['snapshot_key'], 'phase': str(phase),
              'check_index': value('check_index'), 'total': value('total'),
              'argv': value('argv'), 'log': value('log'),
              'runner_pid': value('runner_pid'), 'child_pid': value('child_pid'),
              'started': value('started'), 'detail': value('detail', ''),
              'stage_started_at': started_at, 'updated_at': time.time()}
    _atomic_json(root / 'progress' / f'{ident}.json', record)
    render(root)
    return record


def heartbeat(root, candidate, phase, **fields):
    return update(root, candidate, phase, **fields)


def _read(path):
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
        return value if isinstance(value, dict) else None
    except (OSError, ValueError):
        return None


def _file_url(root, value):
    if not isinstance(value, str) or not value:
        return ''
    path = Path(value)
    if not path.is_absolute():
        path = root / path
    return path.resolve().as_uri()


def _pid_alive(pid):
    """Delegate PID authority to gate liveness; never duplicate platform probes."""
    try:
        if __package__:
            from . import gate_liveness
        else:
            import gate_liveness
        return gate_liveness._pid_alive(pid)
    except (ImportError, OSError, ValueError, TypeError):
        return False


def _is_running_validation(candidate, progress):
    """Only a bound, live validation check—not dispatch or model review—is active."""
    if candidate.get('gate_outcome', {}).get('result') in {'ready_for_owner', 'needs_changes'}:
        return False
    return (progress.get('candidate_id') == candidate.get('id') and
            progress.get('snapshot_key') == candidate.get('snapshot_key') and
            str(progress.get('phase', '')).lower() == 'running' and
            isinstance(progress.get('runner_pid'), int) and progress.get('runner_pid') > 0 and
            isinstance(progress.get('check_index'), int) and progress.get('check_index') > 0 and
            isinstance(progress.get('total'), int) and progress.get('total') >= progress.get('check_index') and
            isinstance(progress.get('argv'), list) and bool(progress.get('argv')) and
            all(isinstance(arg, str) for arg in progress.get('argv')) and
            isinstance(progress.get('log'), str) and bool(progress.get('log')) and
            _pid_alive(progress.get('runner_pid')))


def _rows(exchange):
    found = []
    current_heads = None
    try:
        if __package__:
            from . import owner_attention
        else:
            import owner_attention
        heads = owner_attention.current_work(exchange)
        current_heads = {str(item['head'].get('id')) for item in heads if item.get('head')}
    except (ImportError, OSError, ValueError, TypeError, KeyError):
        pass
    directory = exchange / 'candidates'
    if not directory.is_dir():
        return []
    for path in directory.glob('*.json'):
        c = _read(path)
        if not c or not c.get('id'):
            continue
        p = _read(exchange / 'progress' / (str(c['id']) + '.json')) or {}
        if p.get('candidate_id') != c.get('id') or p.get('snapshot_key') != c.get('snapshot_key'):
            p = {}
        # An actual running test remains visible even when a later head exists;
        # never hide or stop a real worker based on dashboard ordering.
        validation_active = _is_running_validation(c, p)
        # Reuse owner queue head selection for history. Preserve ambiguous
        # legacy records as unconfirmed, and retain genuine running checks.
        if (current_heads is not None and int(c.get('revision_sequence', 0) or 0) > 0
                and str(c['id']) not in current_heads and not validation_active):
            continue
        outcome = c.get('gate_outcome') or {}
        verdict = outcome.get('result')
        request = c.get('gate_request') or {}
        health = _read(exchange / 'gate-health' / (str(c['id']) + '.json')) or {}
        if (health.get('candidate_id') != c.get('id') or health.get('snapshot_key') != c.get('snapshot_key')
                or not request.get('session_id') or health.get('session_id') != request.get('session_id')):
            health = {}
        phase = str(p.get('phase') or '')
        phase_lower = phase.lower()
        if validation_active:
            state = 'VALIDATION RUNNING'
        elif verdict == 'needs_changes':
            state = 'needs_changes'
        elif verdict == 'ready_for_owner':
            state = 'ready_for_owner'
        elif verdict == 'blocked' or outcome.get('status') == 'blocked' or phase_lower == 'blocked':
            state = 'blocked'
        elif verdict == 'pending' or outcome.get('status') == 'pending' or phase_lower == 'pending':
            state = 'pending'
        else:
            state = ('running' if phase_lower == 'running' else 'admitted' if phase_lower == 'admitted'
                     else 'queued' if phase_lower == 'queued'
                     else 'awaiting_review' if phase_lower in ('awaiting_review', 'review')
                     else 'awaiting activity')
        author = (c.get('worktree') or {}).get('worktree', '')
        updated = p.get('updated_at')
        try:
            mtime = path.stat().st_mtime
        except OSError:
            mtime = 0
        report = c.get('review') or {}
        now = time.time()
        health_state = health.get('state')
        checked = health.get('checked_at')
        poll_fresh = isinstance(checked, (int, float)) and 0 <= now - checked <= 90
        last_activity = health.get('last_activity')
        activity_fresh = isinstance(last_activity, (int, float)) and 0 <= now - last_activity <= 90
        runner_proof = (health_state == 'active' and poll_fresh and
                        health.get('reason') == 'bound gate runner is alive')
        active_proof = health_state == 'active' and poll_fresh and (activity_fresh or runner_proof)
        stale_reference = last_activity if isinstance(last_activity, (int, float)) else p.get('stage_started_at', updated)
        overdue = isinstance(stale_reference, (int, float)) and now - stale_reference > 90
        if not verdict and not validation_active and phase_lower not in {'queued', 'admitted'} and health_state == 'recovering':
            state = 'Same lead being resumed'
        elif not verdict and not validation_active and phase_lower not in {'queued', 'admitted'} and health_state == 'blocked':
            state = 'OPERATIONAL BLOCKED'
        elif not verdict and not validation_active and health_state == 'superseded':
            state = 'SUPERSEDED — fresh revision required'
        elif not verdict and not validation_active and health_state == 'finished':
            state = 'GATE SESSION FINISHED — verdict unconfirmed'
        elif not verdict and not validation_active and phase_lower not in {'queued', 'admitted'} and health_state in {'observing', 'api_unavailable'}:
            state = ('OPERATIONAL STATUS UNAVAILABLE — UNCONFIRMED' if health_state == 'api_unavailable'
                     else 'UNCONFIRMED — no current execution proof')
        elif not verdict and not validation_active and phase_lower == 'running':
            state = 'UNCONFIRMED / STALLED'
        elif not verdict and not validation_active and phase_lower not in {'queued', 'admitted'} and not active_proof and overdue:
            state = 'UNCONFIRMED / STALLED'
        findings = (outcome.get('findings') or (report.get('report') or {}).get('findings') or
                    report.get('findings') or [])
        if not isinstance(findings, list):
            findings = [str(findings)]
        found.append({
            'id': c['id'], 'snapshot': str(c.get('snapshot_key', ''))[:12], 'author': author,
            'sprint_id': c.get('sprint_id'),
            'state': state, 'phase': phase, 'stage_started_at': p.get('stage_started_at'), 'detail': (p.get('detail', '') +
                ((' — ' + str(health.get('reason'))) if health.get('reason') else '') +
                ((' — Next: ' + str(health.get('next_action') or ('integration workflow' if health.get('state') in {'blocked', 'api_unavailable'} else ''))) if health.get('next_action') or health.get('state') in {'blocked', 'api_unavailable'} else '')), 'updated': updated,
            'check_index': p.get('check_index'), 'total': p.get('total'), 'argv': p.get('argv'),
            'started': p.get('started'), 'runner_pid': p.get('runner_pid'), 'child_pid': p.get('child_pid'),
            'validation_active': validation_active, 'findings': [str(item) for item in findings if str(item).strip()],
            'log': _file_url(exchange, p.get('log')),
            'report': _file_url(exchange, outcome.get('report_path') or report.get('report_path') or (c.get('gate_validation') or {}).get('path')),
            'session': request.get('session_id', ''), 'verdict': verdict or '', 'health': health,
            'delivery': (c.get('author_completion') or {}).get('status', 'not recorded'),
            'owner_decision': c.get('owner_decision') or '',
            'sort_time': updated or c.get('submitted_at') or c.get('created_at') or mtime,
            'active': validation_active,
        })
    rows = found
    rows.sort(key=lambda row: (not row['validation_active'], -row['sort_time']))
    return rows


def _project_agents(exchange, rows, attention, now):
    """Use the shared canonical-worktree projection; never infer agent identity here."""
    try:
        if __package__:
            from . import agent_status
        else:
            import agent_status
        value = agent_status.project(exchange, rows=rows, attention=attention, now=now)
        if not isinstance(value, dict) or not isinstance(value.get('agents'), list):
            raise ValueError('agent status projection returned an invalid shape')
        return {'agents': value['agents'], 'checked_at': value.get('checked_at'),
                'errors': value.get('errors') if isinstance(value.get('errors'), list) else []}
    except (ImportError, OSError, ValueError, TypeError, KeyError) as exc:
        return {'agents': [], 'checked_at': None, 'errors': [f'Agent activity unavailable: {exc}']}


def render(exchange, output=None):
    exchange = Path(exchange).resolve()
    board = Path(output).resolve() if output else exchange / 'integration-status.html'
    exchange.mkdir(parents=True, exist_ok=True)
    with _render_lock(exchange):
        attention = {'awaiting_owner': [], 'agent_work': [], 'unconfirmed': [], 'completed': []}
        observer = {'status': 'unavailable', 'updated_at': None, 'errors': ['Owner queue unavailable']}
        try:
            if __package__:
                from . import owner_attention
            else:
                import owner_attention
            attention = owner_attention.collect(exchange, refresh=False)
            for group in ('awaiting_owner', 'agent_work', 'unconfirmed', 'completed'):
                for item in attention.get(group, []):
                    if isinstance(item, dict):
                        item['report_url'] = _file_url(exchange, item.get('report_path'))
            observer = _read(exchange / 'owner-feedback-observer.json') or observer
        except (ImportError, OSError, ValueError, TypeError) as exc:
            observer['errors'] = [f'Owner queue unavailable: {exc}']
        now = time.time()
        rows = _rows(exchange)
        agent_status = _project_agents(exchange, rows, attention, now)
        payload = json.dumps({'generated': now, 'agents': agent_status['agents'],
                              'agent_status': agent_status,
                              'observer': observer}, ensure_ascii=False)
        payload = payload.replace('&', '\\u0026').replace('<', '\\u003c').replace('>', '\\u003e')
        page = _PAGE.replace('__DATA__', payload)
        board.parent.mkdir(parents=True, exist_ok=True)
        temp = board.with_name(board.name + '.' + uuid.uuid4().hex + '.tmp')
        try:
            temp.write_text(page, encoding='utf-8')
            _replace(temp, board)
        finally:
            temp.unlink(missing_ok=True)
    return board


def _view_model(candidate, now):
    age = max(0, now - candidate['updated']) if isinstance(candidate.get('updated'), (int, float)) else None
    terminal = candidate.get('verdict') in {'ready_for_owner', 'needs_changes'}
    health = candidate.get('health') or {}
    health_state = health.get('state')
    checked = health.get('checked_at')
    poll_fresh = isinstance(checked, (int, float)) and 0 <= now - checked <= 90
    last_activity = health.get('last_activity')
    activity_fresh = isinstance(last_activity, (int, float)) and 0 <= now - last_activity <= 90
    runner_proof = (health_state == 'active' and poll_fresh and
                    health.get('reason') == 'bound gate runner is alive')
    fresh = health_state == 'active' and poll_fresh and (activity_fresh or runner_proof)
    reference = last_activity if isinstance(last_activity, (int, float)) else candidate.get('stage_started_at', candidate.get('updated'))
    stale = (not terminal and not fresh and
             (health_state in {'observing', 'api_unavailable'} or
              isinstance(reference, (int, float)) and now - reference > 90))
    started = candidate.get('started')
    stop = candidate.get('updated') if terminal else now
    elapsed = max(0, int(stop - started)) if isinstance(started, (int, float)) else None
    title = (candidate.get('verdict') if terminal else
             'OPERATIONAL BLOCKED' if health_state == 'blocked' else
             'Same lead being resumed' if health_state == 'recovering' else
             'OPERATIONAL STATUS UNAVAILABLE — UNCONFIRMED' if health_state == 'api_unavailable' else
             'UNCONFIRMED — no current execution proof' if health_state == 'observing' else
             'UNCONFIRMED / STALLED' if stale else
             'RUNNING' if candidate.get('state') == 'running' else candidate.get('state', ''))
    return {'age': age, 'stale': stale, 'fresh': fresh, 'elapsed': elapsed, 'title': title,
            'operational_reason': health.get('reason', ''), 'recovery_count': health.get('recovery_count'),
            'next_action': health.get('next_action', '')}





_PAGE = r'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<meta http-equiv="refresh" content="3"><title>Integration status</title>
<style>
body{font:16px system-ui;max-width:1700px;margin:1.5rem auto;padding:0 1rem;background:#101820;color:#edf2f4}
h1{color:#70d6ff}.columns{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.8rem;align-items:start}
.column{background:#15232c;border:1px solid #38505e;border-radius:8px;padding:.7rem;min-width:0}
article{background:#1d2b34;padding:.85rem;margin:.7rem 0;border-radius:8px;overflow-wrap:anywhere}
.stale{color:#ffbd69}.core-alert{color:#ff967c;background:#38251f;padding:.35rem;border-radius:4px}
.muted{color:#aab7bf}pre,code{white-space:pre-wrap;overflow-wrap:anywhere}a{color:#70d6ff}
h2{font-size:1.05rem}h3{font-size:1rem}.agent-card h3{margin-top:0}
@media(max-width:1150px){.columns{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:650px){.columns{grid-template-columns:1fr}body{margin:1rem auto}}
</style></head><body>
<h1>Integration status</h1><div id="health-banner" role="status"></div>
<div class="columns">
<section class="column" id="working-column"><h2>Working <span id="working-count"></span></h2><div id="working-cards"></div></section>
<section class="column" id="waiting-column"><h2>Waiting on Validation <span id="waiting-count"></span></h2><div id="waiting-cards"></div></section>
<section class="column" id="active-column"><h2>Active Validation <span id="active-count"></span></h2><div id="active-cards"></div></section>
<section class="column" id="ready-column"><h2>Ready for Manual testing <span id="ready-count"></span></h2><div id="ready-cards"></div></section>
</div><p class="muted">Owner response and technical validation are separate. This dashboard is read-only.</p>
<script>
const data=__DATA__;
function elem(tag,text,cls){const e=document.createElement(tag);e.textContent=String(text??'');if(cls)e.className=cls;return e}
function link(parent,label,url){if(!url)return;const a=document.createElement('a');a.href=url;a.textContent=label;a.target='_blank';a.rel='noopener';parent.append(a)}
function age(t,now){return typeof t==='number'?Math.max(0,Math.floor(now-t))+'s':'time unknown'}
function list(parent,values){if(!Array.isArray(values)||!values.length)return;const ul=document.createElement('ul');for(const v of values)ul.append(elem('li',v));parent.append(ul)}
function agentCard(a,now){const card=document.createElement('article');card.className='agent-card';
card.append(elem('h3',(a.label||'Agent')+' — '+(a.summary||a.activity||'Activity unknown')));
const state=a.column==='active_validation'?'Active validation':a.column==='waiting_validation'?'Waiting on validation':a.column==='ready_manual'?'Ready for manual testing':
 a.activity==='idle'?(a.core?'Core agent idle — needs work':'Idle'):
 a.activity==='unknown'?'Activity unknown':a.activity==='waiting'?'Waiting on validation':a.activity==='working'?'Working':'Activity unknown';
const idleAlert=a.core&&a.column==='working'&&a.activity==='idle';
card.append(elem('p',state,idleAlert?'core-alert':a.activity==='unknown'?'stale':'muted'));
if(idleAlert){const alert=elem('p','Core slot needs attention.','core-alert');alert.setAttribute('role','alert');card.append(alert)}
if(a.worktree)card.append(elem('p','Worktree: '+a.worktree,'muted'));
if(a.detail)card.append(elem('p',a.detail));
if(a.candidate_id)card.append(elem('p','Current candidate: '+a.candidate_id));
if(a.session_id)card.append(elem('p','Session: '+a.session_id));
if(a.column==='active_validation'){
 const hasCheck=Number.isFinite(a.check_index)&&a.check_index>0&&Number.isFinite(a.total)&&a.total>0&&Number.isFinite(a.runner_pid)&&a.runner_pid>0;
 if(hasCheck){card.append(elem('p','Check '+a.check_index+'/'+a.total+' · elapsed '+age(a.started,now)+' · live PID '+a.runner_pid));
  if(a.command)card.append(elem('code',a.command));if(a.log)link(card,'Open current check log',a.log);
 } else card.append(elem('p','Integration lead reviewing candidate'));
} else if(a.check_index&&a.total)card.append(elem('p','Current check '+a.check_index+'/'+a.total));
if(a.findings&&a.findings.length){card.append(elem('h4','Required corrections'));list(card,a.findings)}
if(a.column==='ready_manual'){if(a.fixture)card.append(elem('p','Fixture: '+a.fixture));list(card,a.manual_steps)}
if(a.additional_work&&a.additional_work.length){card.append(elem('h4','Other current work'));const ul=document.createElement('ul');for(const item of a.additional_work)ul.append(elem('li',(item.title||'Work')+' — '+(item.state||'status unknown')));card.append(ul)}
if(a.report_url){const details=document.createElement('details');details.append(elem('summary','Technical report'));link(details,'Open report',a.report_url);card.append(details)}
return card}
function render(now){const agents=Array.isArray(data.agents)?data.agents:[],observer=data.observer||{},status=data.agent_status||{};
const banner=document.getElementById('health-banner'),errors=[...(status.errors||[]),...(observer.errors||[])];
const checked=status.checked_at,activityFresh=typeof checked==='number'&&now-checked>=0&&now-checked<=90;
const observerFresh=['watching','idle','ok'].includes(observer.status)&&typeof observer.updated_at==='number'&&now-observer.updated_at>=0&&now-observer.updated_at<=45;
const snapshotFresh=activityFresh&&observerFresh&&!errors.length;
banner.className=errors.length||!snapshotFresh?'stale':'';
banner.textContent=errors.length?'Activity or owner observation has errors; status may be unknown.':!activityFresh?'Agent activity snapshot is stale or unknown.':!observerFresh?'Owner-response observation is stale or unknown.':'Agent activity and owner observation snapshots are current.';
if(errors.length){const ul=document.createElement('ul');for(const error of errors)ul.append(elem('li',error));banner.append(ul)}
const columns=[['working','working'],['waiting_validation','waiting'],['active_validation','active'],['ready_manual','ready']];
for(const [key,domKey] of columns){const selected=agents.filter(a=>a.column===key),host=document.getElementById(domKey+'-cards');
 const count=document.getElementById(domKey+'-count');
 count.textContent='('+selected.length+' agents)';host.replaceChildren();for(const agent of selected)host.append(agentCard(agent,now));
 if(!selected.length)host.append(elem('p',snapshotFresh?'No agents in this state.':'Agent status unknown; do not infer an empty queue.','muted'));
}
}
render(Date.now()/1000);
</script></body></html>'''


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exchange', type=Path, required=True)
    args = parser.parse_args(argv)
    print(render(args.exchange))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
