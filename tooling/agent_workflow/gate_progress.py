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
        # Keep the last concrete check evidence when moving to a terminal
        # operational state; it must not look like a normal completion erased it.
        retain = same_phase or str(phase).lower() in {'blocked', 'interrupted'}
        return fields[key] if key in fields else previous.get(key, default) if retain else default
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


def _execution_worker_alive(exchange, candidate):
    """A durable job is active only with a bound claimed worker PID proof."""
    try:
        if __package__:
            from . import gate_execution
        else:
            import gate_execution
        job = gate_execution.read(exchange, candidate.get('id'))
        return bool(job and job.get('job_id') == candidate.get('id') and
                    job.get('candidate_id') == candidate.get('id') and
                    job.get('snapshot_key') == candidate.get('snapshot_key') and
                    job.get('state') == 'running' and isinstance(job.get('claim'), str) and
                    len(job.get('claim')) >= 32 and isinstance(job.get('attempt'), int) and
                    job.get('attempt') > 0 and _pid_alive(job.get('worker_pid')))
    except (ImportError, OSError, ValueError, TypeError):
        return False


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
        validation_active = _is_running_validation(c, p) or _execution_worker_alive(exchange, c)
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


def render(exchange, output=None, workflow_snapshot=None):
    exchange = Path(exchange).resolve()
    board = Path(output).resolve() if output else exchange / 'integration-status.html'
    if workflow_snapshot is not None:
        page = workflow_page(workflow_snapshot)
        board.parent.mkdir(parents=True, exist_ok=True)
        board.write_text(page, encoding='utf-8')
        return board
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


def workflow_page(snapshot):
    """Render a saved WR09 snapshot through the existing page and DOM helpers."""
    if not isinstance(snapshot, dict) or snapshot.get('version') != 1:
        raise ValueError('workflow snapshot version 1 is required')
    payload = json.dumps({'workflow': snapshot}, ensure_ascii=False)
    payload = payload.replace('&', '\\u0026').replace('<', '\\u003c').replace('>', '\\u003e')
    return _PAGE.replace('__DATA__', payload).replace('<meta http-equiv="refresh" content="3">', '')


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
body{font:16px system-ui;width:100%;box-sizing:border-box;margin:1rem auto;padding:0 .75rem;background:#101820;color:#edf2f4}
h1{color:#70d6ff}.columns{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:.8rem;align-items:start}
.column{background:#15232c;border:1px solid #38505e;border-radius:8px;padding:.7rem;min-width:0}
article{background:#1d2b34;padding:.85rem;margin:.7rem 0;border-radius:8px;overflow-wrap:anywhere}
.stale{color:#ffbd69}.core-alert{color:#ff967c;background:#38251f;padding:.35rem;border-radius:4px}
.muted{color:#aab7bf}pre,code{white-space:pre-wrap;overflow-wrap:anywhere}a{color:#70d6ff}
h2{font-size:1.05rem}h3{font-size:1rem}.agent-card h3{margin-top:0}
@media(max-width:1150px){.columns{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:650px){.columns{grid-template-columns:1fr}body{margin:1rem auto}}
 .workflow-columns{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:.55rem;align-items:start}.workflow-columns>.column{min-width:0;padding:.45rem}.workflow-card{min-width:0;padding:.55rem}.workflow-card h3{overflow-wrap:break-word;word-break:normal;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.workflow-card button{max-width:100%;white-space:normal}.workflow-status{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.workflow-title{overflow-wrap:anywhere}.workflow-card h3,.workflow-card p{margin:.2rem 0}.workflow-needs{border:1px solid #ffbd69;padding:.5rem}.workflow-error{color:#ff967c;background:#38251f;border:1px solid #ff967c;padding:.25rem;overflow-wrap:anywhere}
 @media(max-width:1200px){.workflow-columns{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:800px){.workflow-columns{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:480px){.workflow-columns{grid-template-columns:1fr}}
</style></head><body>
<h1>Integration status</h1><div id="health-banner" role="status"></div>
<section id="workflow-owner"><button id="owner-refresh" type="button">Refresh snapshot</button><button id="owner-review" type="button" hidden>Review updated snapshot</button><span id="owner-transport">Connecting…</span><div id="workflow-pending"></div></section>
<div id="needs-you" class="workflow-needs" hidden></div>

<div class="columns">
<section class="column" id="working-column"><h2>Working <span id="working-count"></span></h2><div id="working-cards"></div></section>
<section class="column" id="waiting-column"><h2>Waiting on Validation <span id="waiting-count"></span></h2><div id="waiting-cards"></div></section>
<section class="column" id="active-column"><h2>Active Validation <span id="active-count"></span></h2><div id="active-cards"></div></section>
<section class="column" id="ready-column"><h2>Ready for Manual testing <span id="ready-count"></span></h2><div id="ready-cards"></div></section>
</div><p id="workflow-footer" class="muted">Owner response and technical validation are separate. This dashboard is read-only.</p>
<script>
const data=__DATA__;
function elem(tag,text,cls){const e=document.createElement(tag);e.textContent=String(text??'');if(cls)e.className=cls;return e}
const workflowDismissStorageKey='moonstation.integration.workflow-dismissals.v1';
const dismissedWorkflowCards=new Set();
try{const saved=JSON.parse(localStorage.getItem(workflowDismissStorageKey)||'[]');if(Array.isArray(saved))for(const key of saved)if(typeof key==='string'&&key)dismissedWorkflowCards.add(key)}catch(_){}
 function link(parent,label,url){if(!url)return;const a=document.createElement('a');a.href=url;a.textContent=label;a.target='_blank';a.rel='noopener';parent.append(a)}
 function age(t,now){return typeof t==='number'?Math.max(0,Math.floor(now-t))+'s':'time unknown'}
 function list(parent,values){if(!Array.isArray(values)||!values.length)return;const ul=document.createElement('ul');for(const v of values)ul.append(elem('li',v));parent.append(ul)}
 function renderWorkflowDetails(d,c){const text=v=>typeof v==='string'?v.trim():typeof v==='number'?String(v):'', details=c.details&&typeof c.details==='object'?c.details:{};
   // Status is already visible above the disclosure; Summary adds concise context only.
    const fallback=details.current_error?'Validation stopped because of a validation-service problem.':details.title;
    const summaries=Array.isArray(details.summary)?details.summary:(fallback?[fallback]:[]);
    for(const value of summaries.slice(0,2)){const summary=text(value).replace(/\s+/g,' ').trim().slice(0,240);if(summary&&summary!==text(c.label)&&summary!==text(c.status_text))d.append(elem('p',summary))}
    const findings=Array.isArray(details.owner_attention)?details.owner_attention:[],seen=new Set(),messages=[];
   for(const f of findings){const msg=typeof f==='string'?text(f):text(f?.message)||text(f?.summary)||text(f?.reason),change=f&&typeof f==='object'?text(f.required_change):'',location=f&&typeof f==='object'?text(f.location):'',rendered=[msg,change?'Required change: '+change:'',location?'File: '+location:''].filter(Boolean).join(' — ');if(rendered&&!seen.has(rendered)){seen.add(rendered);messages.push(rendered)}}
    if(messages.length){const h=elem('h4','Needs attention'),ul=document.createElement('ul');for(const m of messages)ul.append(elem('li',m));d.append(h,ul)}
  if(!d.childNodes.length)d.append(elem('p','No coding work is currently active.'));
 }
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
let workflowConnected=false;
let workflowStale=false;
let workflowNeedsReview=false;
const workflowAmbiguous=new Map();
const workflowUi=new Map();
 function stageElapsed(ms){if(!Number.isSafeInteger(ms)||ms<0)return 'In time unknown';const seconds=Math.floor(Math.max(0,Date.now()-ms)/1000),hours=Math.floor(seconds/3600),minutes=Math.floor(seconds%3600/60),rest=seconds%60;return 'In '+(hours?hours+'h '+minutes+'m':minutes?minutes+'m '+rest+'s':rest+'s')}
const actionLabels={manual:'Manual steps',cancel_active:'Cancel active',retry_review:'Retry review',seen:'Mark seen',retry_local_api:'Retry local API'};
 const boardTransport={token:null,controller:null,snapshotController:null,snapshotGeneration:null,snapshotToken:null,generation:0,reconnectTimer:null,fallbackTimer:null,retryIndex:0,revision:null,workflowRevision:null,snapshotPromise:null,revisionQueued:false,revisionQueuedNeedsReview:false,streamLive:false,snapshotConnected:false,snapshotLoading:false,snapshotError:'',unloaded:false};
function commandId(){return 'cmd_'+crypto.randomUUID()}
function buildActiveCancel(card,command_id){const b=card.bindings||{};return{command_id,run_id:b.run_id,evaluation_generation:b.generation,expected_sequence:b.sequence,reason:'CANCELLED'}}
function buildReviewRetry(card,command_id){const b=card.bindings||{};return{command_id,task_id:b.review_task_id,expected_sequence:b.sequence}}
  function renderWorkflow(w){
  document.querySelector('.workflow-columns')?.querySelectorAll?.('.workflow-card').forEach(card=>{const key=card.dataset?.cardId;if(key)workflowUi.set(key,{open:!!card.querySelector('details')?.open,binding:card.dataset.binding})});
 document.querySelector('meta[http-equiv="refresh"]')?.remove();document.getElementById('workflow-owner').hidden=false;updateWorkflowFooter();const cols=document.querySelector('.columns');cols.className='columns workflow-columns';cols.replaceChildren();const names=['Idle','Building','Queued','Checking','Reviewing','Passed'];
    const banner=document.getElementById('health-banner'),stamp=w.captured_at_ms?new Date(w.captured_at_ms).toLocaleString():'unknown time';banner.className=boardTransport.streamLive&&!boardTransport.snapshotError?'':'stale';banner.textContent=boardTransport.streamLive?'Transport connected — AI health unknown.':boardTransport.snapshotLoading?'Loading initial workflow snapshot; connecting to event stream…':boardTransport.snapshotError?'Snapshot unavailable: '+boardTransport.snapshotError:'Disconnected — last saved snapshot at '+stamp+'; live health unknown.';
    if(boardTransport.snapshotLoading)banner.append(elem('p','Refreshing workflow snapshot; showing last captured '+stamp+'.','stale'));
    if(boardTransport.snapshotError)banner.append(elem('p','Snapshot refresh failed ('+boardTransport.snapshotError+'); displayed data remains from '+stamp+'.','stale'));
   if(boardTransport.streamLive&&(workflowNeedsReview||workflowAmbiguous.size))banner.append(elem('p',workflowAmbiguous.size?'Connected; command outcome requires review.':'Connected; updated snapshot requires review before actions.','stale'));
   const occupancy=(w.system||{}).native_occupancy||{},queue=(w.system||{}).queue||{};const nativeText=typeof occupancy==='object'?'Native slots: '+(occupancy.held??'unknown')+' held, '+(occupancy.unknown??'unknown')+' unknown ('+(occupancy.state||'unknown')+')':'Native slots: '+occupancy;const queueText=typeof queue==='object'?'Queue: '+(queue.queued_tasks??'unknown')+' queued ('+(queue.state||'unknown')+')':'Queue: '+queue;banner.append(elem('p',nativeText+' · '+queueText+' · AI: '+((w.system||{}).ai_availability||'unknown'),'muted'));
   const faultCount=Array.isArray(w.current_owner_attention)?w.current_owner_attention.length:0;const needs=document.getElementById('needs-you');needs.hidden=faultCount===0;needs.textContent='Needs attention: '+faultCount+' workflow fault'+(faultCount===1?'':'s');
        names.forEach(name=>{const sec=document.createElement('section');sec.className='column';sec.append(elem('h2',name));const host=document.createElement('div');sec.append(host);cols.append(sec);const cards=name==='Passed'?[...((w.columns||{})['Your testing']||[]),...((w.columns||{}).Accepted||[])]:((w.columns||{})[name]||[]);if(name==='Passed')cards.sort((a,b)=>{const at=Number.isSafeInteger(a.result_created_ms)&&a.result_created_ms>=0?a.result_created_ms:null,bt=Number.isSafeInteger(b.result_created_ms)&&b.result_created_ms>=0?b.result_created_ms:null;if(at===null&&bt!==null)return 1;if(bt===null&&at!==null)return -1;if(at!==null&&bt!==null&&at!==bt)return bt-at;return String(a.id||'').localeCompare(String(b.id||''))});for(const c of cards){if(name==='Passed'&&c.dismissible===true&&typeof c.dismiss_key==='string'&&c.dismiss_key&&dismissedWorkflowCards.has(c.dismiss_key))continue;const card=document.createElement('article');card.className='workflow-card';const title=elem('h3',c.label||'Untitled');title.className='workflow-title';title.title=c.label||'Untitled';title.setAttribute('aria-label',c.label||'Untitled');card.append(title);if(c.badge==='Validation error')card.append(elem('p','Validation error','workflow-error'));const result=String(c.focused_result||'').toUpperCase(),regression=String(c.regression_state||'').toUpperCase();const status=name==='Passed'?(result==='PASS'?'Validation passed':result==='FAIL'?'Validation failed':result==='ERROR'?'Validation error':'Validation result: '+(result||'unknown'))+(regression==='FAIL'||regression==='ERROR'?' · Regression '+regression:''):(c.status_text||(c.fault?'Needs attention':''));if(status){const line=elem('p',status,'workflow-status');line.title=status;card.append(line)}if(['Queued','Checking','Reviewing'].includes(c.phase)){const timer=elem('p',stageElapsed(c.stage_started_ms),'workflow-stage-time');timer.dataset.stageStarted=String(c.stage_started_ms??'');card.append(timer)}if(c.badge==='Validation error'&&c.details?.current_error)card.append(elem('p',c.details.current_error,'workflow-error'));
    const d=document.createElement('details');d.open=c.details_open===true;d.append(elem('summary','Summary'));renderWorkflowDetails(d,c);card.dataset.cardId=c.id;card.dataset.binding=JSON.stringify(c.bindings||{});card.append(d);renderCardActions(card,c,name);const ui=workflowUi.get(c.id);if(ui)d.open=ui.open;host.append(card)}});
  document.getElementById('owner-refresh').onclick=()=>refreshWorkflow();const review=document.getElementById('owner-review');review.hidden=!workflowNeedsReview;review.textContent=workflowAmbiguous.size?'I reviewed the snapshot and observed the pending outcome':'Review updated snapshot';review.onclick=()=>{if(workflowAmbiguous.size){for(const key of workflowAmbiguous.keys())pendingOwnerCommands.delete(key);workflowAmbiguous.clear()}workflowNeedsReview=false;renderWorkflow(data.workflow);updateTransportState()};renderPendingCommands();
}
const pendingOwnerCommands=new Map();
  function renderCardActions(host,c,column){for(const name of c.capabilities||[]){if(!['cancel_active','retry_review','seen','retry_local_api','manual'].includes(name))continue;const disabled=!workflowConnected||workflowNeedsReview||workflowAmbiguous.size>0;const b=elem('button',actionLabels[name]||name);b.type='button';b.dataset.action=name;b.disabled=disabled;b.onclick=()=>name==='manual'?editorForManual(host,c):ownerAction(host,c,name);host.append(b)}if(column==='Passed'&&c.dismissible===true&&typeof c.dismiss_key==='string'&&c.dismiss_key){const b=elem('button','Dismiss');b.type='button';b.dataset.action='dismiss';b.disabled=!workflowConnected||workflowNeedsReview||workflowAmbiguous.size>0;b.title=b.disabled?'Resolve pending command and review current source first':'';b.onclick=()=>{if(b.disabled)return;dismissedWorkflowCards.add(c.dismiss_key);try{localStorage.setItem(workflowDismissStorageKey,JSON.stringify([...dismissedWorkflowCards]))}catch(_){}renderWorkflow(data.workflow)};host.append(b)}}
 function editorForManual(host,c){let d=host.querySelector('.workflow-manual');if(!d){d=document.createElement('details');d.className='workflow-manual';d.append(elem('summary','Manual steps'));list(d,c.details?.manual_steps);host.append(d)}d.open=true}
 function renderPendingCommands(){const host=document.getElementById('workflow-pending');host.replaceChildren();for(const [key,item] of workflowAmbiguous){const b=item.card.bindings||{};host.append(elem('p','Ambiguous command '+item.saved.body.command_id+' · run '+(b.run_id||'none')+' · generation '+(b.generation??'none')+' · sequence '+(b.sequence??'none')+' · result '+(b.result_id||'none')+' · digest '+(b.input_digest||'none')+'. Exact original payload retained.','stale'));const replay=elem('button','Replay exact command');replay.type='button';replay.disabled=!boardTransport.snapshotConnected;replay.onclick=()=>ownerAction(host,item.card,item.name,true);host.append(replay)}if(workflowAmbiguous.size){const observe=elem('button','Observe latest snapshot');observe.type='button';observe.onclick=()=>refreshWorkflow();host.append(observe)}}
 async function ownerAction(host,c,name,replay=false){const token='local-owner',pendingKey=c.id+':'+name;let saved=pendingOwnerCommands.get(pendingKey);if(replay){if(!boardTransport.snapshotConnected||!saved||!workflowAmbiguous.has(pendingKey)){host.append(elem('p','Reconnect and observe the saved snapshot before replaying this original command.','stale'));return}}else if(!workflowConnected){host.append(elem('p','Connect to the owner event stream before actions.','muted'));return}
  if(!saved){const b=c.bindings||{},id=commandId();let url,body;
   if(name==='cancel_active'){url='/v1/cancel';body=buildActiveCancel(c,id)}
  else if(name==='retry_review'){url='/v1/recovery/retry-review';body=buildReviewRetry(c,id)}
  else if(name==='retry_local_api'){const state=String(c.state||c.status||c.incident_state||'').toUpperCase();if(!['OPEN','BACKOFF'].includes(state)){host.append(elem('p','Local API retry is available only for an explicit OPEN or BACKOFF incident.','stale'));return}url='/v1/recovery/retry-local-api';body={command_id:id}}
  else if(name==='seen'){url='/v1/board/incidents/'+encodeURIComponent(c.bindings?.incident_id||'')+'/seen';body={command_id:id}}
  else if(name==='manual'){host.append(elem('p','Manual steps are recorded in the read-only details.'));return}else return;
  saved={url,body};pendingOwnerCommands.set(pendingKey,saved)}
  try{const r=await fetch(saved.url,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json'},body:JSON.stringify(saved.body),redirect:'error'});if(r.status===409){workflowConnected=false;workflowStale=true;workflowAmbiguous.delete(pendingKey);pendingOwnerCommands.delete(pendingKey);lockWorkflowActions();host.append(elem('p','Snapshot is stale. Refresh, then review the new source before choosing an action.','stale'));return}if(r.status===401||r.status===403){workflowConnected=false;lockWorkflowActions();document.getElementById('owner-transport').textContent='Owner authentication failed; refresh owner access before retrying';host.append(elem('p','Owner authentication failed. Refresh the owner view, then retry explicitly.','stale'));return}if(!r.ok&&r.status!==202)throw new Error('HTTP '+r.status);const response=await r.json().catch(()=>({}));const pending=r.status===202||response.status==='PENDING';host.append(elem('p',pending?'Pending; not resumed.':(response.status||'Action recorded')));if(!pending){pendingOwnerCommands.delete(pendingKey);workflowAmbiguous.delete(pendingKey)}renderPendingCommands()}catch(_){workflowConnected=false;workflowAmbiguous.set(pendingKey,{card:c,name,saved});lockWorkflowActions();host.append(elem('p','Reply ambiguous; retry exact command or explicitly observe current snapshot.','stale'));renderPendingCommands()}}
function lockWorkflowActions(){document.querySelectorAll('.workflow-card button').forEach(x=>x.disabled=true)}
function updateWorkflowFooter(){const footer=document.getElementById('workflow-footer');footer.textContent=workflowConnected?'Validation results are read-only. Manual testing and acceptance are handled with the coding agent.':'Validation results are read-only. Manual testing and acceptance are handled with the coding agent.'}
function sameBindings(left,right){return JSON.stringify(left||{})===JSON.stringify(right||{})}
function workflowBindingCards(workflow){const result={};for(const [column,cards] of Object.entries(workflow?.columns||{})){result[column]=(cards||[]).filter(card=>!(card.run_id===null&&Object.keys(card.bindings||{}).length===0&&String(card.id||'').startsWith('author-activity:'))).map(card=>({id:card.id,bindings:card.bindings||{}})).sort((a,b)=>String(a.id).localeCompare(String(b.id)))}return result}
function sameWorkflowBindings(left,right){return JSON.stringify(workflowBindingCards(left))===JSON.stringify(workflowBindingCards(right))}
 function updateTransportState(){workflowConnected=!!boardTransport.streamLive&&!workflowNeedsReview&&!workflowAmbiguous.size&&boardTransport.snapshotConnected;const label=document.getElementById('owner-transport');label.textContent=boardTransport.streamLive?'Board event stream connected; AI health unknown':!boardTransport.snapshotConnected&&!data.workflow?'Loading snapshot and connecting…':'Disconnected; saved snapshot retained';updateWorkflowFooter();if(!workflowConnected)lockWorkflowActions();if(data.workflow)renderWorkflow(data.workflow)}
 function retainDisconnected(){boardTransport.streamLive=false;boardTransport.snapshotConnected=false;workflowConnected=false;updateWorkflowFooter();lockWorkflowActions();document.getElementById('owner-transport').textContent=data.workflow?'Disconnected; saved snapshot retained':'Connecting…';if(data.workflow){const t=data.workflow.captured_at_ms?new Date(data.workflow.captured_at_ms).toLocaleString():'unknown time';const banner=document.getElementById('health-banner');banner.textContent='Disconnected — last saved snapshot at '+t+'; live health unknown.';banner.className='stale'}else if(!boardTransport.snapshotError)document.getElementById('health-banner').textContent='Loading initial workflow snapshot; connecting to event stream…'}
function clearBoardTimers(){if(boardTransport.reconnectTimer){clearTimeout(boardTransport.reconnectTimer);boardTransport.reconnectTimer=null}if(boardTransport.fallbackTimer){clearTimeout(boardTransport.fallbackTimer);boardTransport.fallbackTimer=null}}
function stopBoardTransport(){boardTransport.generation++;clearBoardTimers();if(boardTransport.controller){boardTransport.controller.abort();boardTransport.controller=null}if(boardTransport.snapshotController){boardTransport.snapshotController.abort();boardTransport.snapshotController=null}boardTransport.streamLive=false;boardTransport.snapshotConnected=false;workflowConnected=false;}
function transportAllowed(){return typeof location!=='undefined'&&(location.protocol==='http:'||location.protocol==='https:')&&location.origin&&location.origin!=='null'}
  async function fetchOwnerSnapshot(token,generation,{revisionWake=false}={}){if(boardTransport.snapshotPromise&&boardTransport.snapshotGeneration===generation&&boardTransport.snapshotToken===token)return boardTransport.snapshotPromise;boardTransport.snapshotLoading=true;boardTransport.snapshotError='';if(data.workflow)renderWorkflow(data.workflow);const controller=new AbortController();boardTransport.snapshotController=controller;const request=(async()=>{let r=await fetch('/v1/board/snapshot',{credentials:'same-origin',headers:{},redirect:'error',cache:'no-store',signal:controller.signal});if(r.status===401||r.status===403){const renewed=await fetch('/integration-status',{credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal});if(!renewed.ok)throw new Error('local owner configuration unavailable');r=await fetch('/v1/board/snapshot',{credentials:'same-origin',headers:{},redirect:'error',cache:'no-store',signal:controller.signal})}if(!r.ok||r.status<200||r.status>=300)throw new Error('HTTP '+r.status);const next=await r.json();if(generation!==boardTransport.generation||token!==boardTransport.token)return null;if(!next||next.version!==1||next.available!==true)throw new Error('unavailable or incompatible snapshot');const old=data.workflow;const changed=old&&!sameWorkflowBindings(old,next);if(changed||revisionWake||workflowStale||workflowAmbiguous.size){workflowNeedsReview=true;workflowConnected=false;lockWorkflowActions()}data.workflow=next;boardTransport.snapshotConnected=true;boardTransport.snapshotError='';renderWorkflow(next);updateTransportState();return next})();boardTransport.snapshotPromise=request;boardTransport.snapshotGeneration=generation;boardTransport.snapshotToken=token;try{return await request}catch(error){if(generation===boardTransport.generation&&token===boardTransport.token){boardTransport.snapshotError=String(error?.message||error);boardTransport.snapshotConnected=false;workflowConnected=false;lockWorkflowActions();if(data.workflow)renderWorkflow(data.workflow);else{const banner=document.getElementById('health-banner');banner.className='stale';banner.textContent='Initial snapshot unavailable: '+boardTransport.snapshotError+'; retrying.'}}throw error}finally{if(boardTransport.snapshotPromise===request){boardTransport.snapshotPromise=null;boardTransport.snapshotController=null;boardTransport.snapshotGeneration=null;boardTransport.snapshotToken=null;boardTransport.snapshotLoading=false;if(data.workflow)renderWorkflow(data.workflow)}}}
function scheduleFallback(generation,token){if(boardTransport.fallbackTimer||boardTransport.unloaded)return;boardTransport.fallbackTimer=setTimeout(async()=>{boardTransport.fallbackTimer=null;if(generation!==boardTransport.generation||boardTransport.streamLive)return;try{await fetchOwnerSnapshot(token,generation,{revisionWake:true})}catch(_){}if(generation===boardTransport.generation&&!boardTransport.streamLive)scheduleFallback(generation,token)},30000)}
function scheduleReconnect(generation,token){if(boardTransport.reconnectTimer||boardTransport.unloaded)return;const delays=[1000,2000,5000,10000,20000,30000],delay=delays[Math.min(boardTransport.retryIndex++,delays.length-1)];boardTransport.reconnectTimer=setTimeout(async()=>{boardTransport.reconnectTimer=null;if(generation!==boardTransport.generation||boardTransport.unloaded)return;try{await fetchOwnerSnapshot(token,generation,{revisionWake:true});if(generation===boardTransport.generation)await openBoardStream(token,generation)}catch(_){retainDisconnected();scheduleReconnect(generation,token);scheduleFallback(generation,token)}},delay);scheduleFallback(generation,token)}
function parseBoardRevision(frame,withMetadata=false){let event='',dataLines=[];for(const line of frame.split(/\r?\n/)){if(!line||line.startsWith(':'))continue;const i=line.indexOf(':'),field=i<0?line:line.slice(0,i),value=i<0?'':line.slice(i+1).replace(/^ /,'');if(field==='event')event=value;else if(field==='data')dataLines.push(value)}if(event!=='board-revision')return null;try{const message=JSON.parse(dataLines.join('\n'));if(!Number.isSafeInteger(message.revision)||message.revision<0)return null;const workflowRevision=Number.isSafeInteger(message.workflow_revision)&&message.workflow_revision>=0?message.workflow_revision:undefined;return withMetadata?{revision:message.revision,workflowRevision}:message.revision}catch(_){return null}}
  function onBoardRevision(revision,token,generation,coalesced=false,workflowRevision){if(generation!==boardTransport.generation||token!==boardTransport.token||revision===boardTransport.revision&&!coalesced)return;const initial=boardTransport.revision===null;const needsReview=!initial&&(workflowRevision===undefined||boardTransport.workflowRevision===null||workflowRevision!==boardTransport.workflowRevision);boardTransport.revision=revision;if(workflowRevision!==undefined)boardTransport.workflowRevision=workflowRevision;if(needsReview){workflowNeedsReview=true;workflowConnected=false;lockWorkflowActions()}if(boardTransport.snapshotPromise){boardTransport.revisionQueued=true;boardTransport.revisionQueuedNeedsReview=boardTransport.revisionQueuedNeedsReview||needsReview;return}fetchOwnerSnapshot(token,generation,{revisionWake:needsReview}).catch(()=>{}).finally(()=>{if(boardTransport.revisionQueued&&generation===boardTransport.generation){boardTransport.revisionQueued=false;const latest=boardTransport.revision,latestWorkflow=boardTransport.workflowRevision,queuedReview=boardTransport.revisionQueuedNeedsReview;boardTransport.revisionQueuedNeedsReview=false;queueMicrotask(()=>onBoardRevision(latest,token,generation,true,queuedReview?undefined:latestWorkflow))}})}
async function consumeBoardStream(reader,token,generation){const decoder=new TextDecoder();let buffer='';try{while(generation===boardTransport.generation&&!boardTransport.unloaded){const part=await reader.read();if(part.done)break;buffer+=decoder.decode(part.value,{stream:true});if(buffer.length>16384)throw new Error('SSE frame limit exceeded');let split;while((split=buffer.search(/\r?\n\r?\n/))>=0){const frame=buffer.slice(0,split);const delimiter=buffer.slice(split).match(/^\r?\n\r?\n/)[0];buffer=buffer.slice(split+delimiter.length);const rev=parseBoardRevision(frame,true);if(rev!==null)onBoardRevision(rev.revision,token,generation,false,rev.workflowRevision)}}if(generation===boardTransport.generation)throw new Error('event stream ended')}catch(_){if(generation!==boardTransport.generation||boardTransport.unloaded)return;retainDisconnected();scheduleReconnect(generation,token)}}
 async function openBoardStream(token,generation){if(generation!==boardTransport.generation||boardTransport.unloaded)return;const controller=new AbortController();boardTransport.controller=controller;try{const r=await fetch('/v1/board/events',{credentials:'same-origin',headers:{Accept:'text/event-stream'},redirect:'error',cache:'no-store',signal:controller.signal});if(!r.ok||r.status<200||r.status>=300||!r.body?.getReader)throw new Error('event stream unavailable');if(generation!==boardTransport.generation)return;boardTransport.streamLive=true;boardTransport.retryIndex=0;if(boardTransport.fallbackTimer){clearTimeout(boardTransport.fallbackTimer);boardTransport.fallbackTimer=null}updateTransportState();void consumeBoardStream(r.body.getReader(),token,generation)}catch(_){if(generation!==boardTransport.generation||boardTransport.unloaded)return;retainDisconnected();scheduleReconnect(generation,token)}}
 async function fetchRevisionSnapshot(token,generation){try{await fetchOwnerSnapshot(token,generation,{revisionWake:true})}catch(_){}}
  async function connectWorkflow(){if(!transportAllowed()){retainDisconnected();document.getElementById('owner-transport').textContent='Owner connection requires same-origin local HTTP';return}const token='local-owner';stopBoardTransport();boardTransport.token=token;boardTransport.unloaded=false;const generation=boardTransport.generation;try{await fetchOwnerSnapshot(token,generation);if(generation!==boardTransport.generation)return;await openBoardStream(token,generation)}catch(_){if(generation===boardTransport.generation)scheduleReconnect(generation,token)}}
  async function refreshWorkflow(){const token='local-owner';if(!token||!transportAllowed()){retainDisconnected();return}if(boardTransport.token!==token){stopBoardTransport();boardTransport.token=token}const generation=boardTransport.generation;try{await fetchOwnerSnapshot(token,generation)}catch(_){}}
connectWorkflow();
window.addEventListener('pagehide',()=>{boardTransport.unloaded=true;stopBoardTransport()},{once:true});
if(data.workflow)renderWorkflow(data.workflow);else render(Date.now()/1000);
 setInterval(()=>{document.querySelectorAll('.workflow-stage-time').forEach(node=>{const ms=Number(node.dataset.stageStarted);node.textContent=node.dataset.stageStarted!==''?stageElapsed(ms):'In time unknown'})},1000);
</script></body></html>'''


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exchange', type=Path, required=True)
    args = parser.parse_args(argv)
    print(render(args.exchange))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
