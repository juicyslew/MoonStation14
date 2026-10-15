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
        try:
            from . import gate_liveness
        except ImportError:
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
        try:
            from . import owner_attention
        except ImportError:
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


def render(exchange, output=None):
    exchange = Path(exchange).resolve()
    board = Path(output).resolve() if output else exchange / 'integration-status.html'
    exchange.mkdir(parents=True, exist_ok=True)
    with _render_lock(exchange):
        attention = {'awaiting_owner': [], 'agent_work': [], 'unconfirmed': [], 'completed': []}
        observer = {'status': 'unavailable', 'updated_at': None, 'errors': ['Owner queue unavailable']}
        try:
            try:
                from . import owner_attention
            except ImportError:
                import owner_attention
            attention = owner_attention.collect(exchange, refresh=False)
            for group in ('awaiting_owner', 'agent_work', 'unconfirmed', 'completed'):
                for item in attention.get(group, []):
                    if isinstance(item, dict):
                        item['report_url'] = _file_url(exchange, item.get('report_path'))
            observer = _read(exchange / 'owner-feedback-observer.json') or observer
        except (ImportError, OSError, ValueError, TypeError) as exc:
            observer['errors'] = [f'Owner queue unavailable: {exc}']
        payload = json.dumps({'generated': time.time(), 'candidates': _rows(exchange), 'attention': attention, 'observer': observer}, ensure_ascii=False)
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


_PAGE = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta http-equiv="refresh" content="3"><meta name="viewport" content="width=device-width"><title>Needs your attention — Integration status</title><style>body{font:16px system-ui;max-width:1100px;margin:2rem auto;padding:0 1rem;background:#101820;color:#edf2f4}h1{color:#70d6ff}article{background:#1d2b34;padding:1rem;margin:1rem 0;border-radius:8px}.stale{color:#ffbd69}.muted{color:#aab7bf}pre{white-space:pre-wrap;overflow-wrap:anywhere}a{color:#70d6ff}</style></head><body><h1>Needs your attention <span id="count"></span></h1><p id="health"></p><main id="owner-queue"></main><p class="muted">Give feedback to the agent in the normal conversation; the card clears automatically when your response is observed. A technical pass is not owner acceptance.</p><section><h2>Needs reconciliation / status unknown <span id="unknown-count"></span></h2><main id="unknown"></main></section><details><summary>With agents / validation</summary><main id="agents"></main></details><details><summary>Integration telemetry</summary><p class="muted">Progress is informational; only a recorded candidate verdict is authoritative.</p><main id="items"></main></details><script>const data=__DATA__;function elem(tag,text,cls){const e=document.createElement(tag);e.textContent=String(text??'');if(cls)e.className=cls;return e}function link(parent,label,url){if(!url)return;const a=document.createElement('a');a.href=url;a.textContent=label;a.target='_blank';a.rel='noopener';parent.append(a)}function age(t){return typeof t==='number'?Math.max(0,Math.floor(Date.now()/1000-t))+'s ago':'time unknown'}function shortPath(x){const w=x.worktree||{},v=typeof w==='string'?w:(w.worktree||w.path||'');return v.split(/[\\/]/).filter(Boolean).slice(-2).join('/')||'worktree unknown'}function renderItem(x,kind){const c=document.createElement('article'),title=x.title||x.sprint_id||x.id||'Work';c.append(elem('h2',title+' — '+kind));c.append(elem('p',shortPath(x)+' · waiting '+age(x.requested_at||x.submitted_at),'muted'));if(x.manual_steps?.length){const ol=document.createElement('ol');for(const step of x.manual_steps)ol.append(elem('li',step));c.append(ol)}if(x.fixture)c.append(elem('p','Fixture: '+x.fixture));if(x.build_identity)c.append(elem('p','Build: '+x.build_identity));if(x.unverified)c.append(elem('p','Unverified: '+x.unverified));const sid=x.origin_session_id||x.session_id||'';if(sid){const row=elem('p','Conversation ID: '+sid+' ');const copy=elem('button','Copy ID');copy.type='button';copy.onclick=()=>navigator.clipboard?.writeText(sid);row.append(copy);c.append(row)}link(c,'Review report',x.report_url||x.report);return c}function renderAttention(now){const a=data.attention||{},o=data.observer||{},pending=a.awaiting_owner||[],uncertain=a.unconfirmed||[],healthy=['watching','idle','ok'].includes(o.status)&&typeof o.updated_at==='number'&&now-o.updated_at<45,unknown=[...uncertain,...(!healthy?[{reason:'Observer stale or unavailable; current queue is not verified'}]:[])];document.getElementById('count').textContent='('+pending.length+(unknown.length?' confirmed; '+unknown.length+' unknown':'')+')';const h=document.getElementById('health');h.textContent=healthy?'Queue observation '+age(o.updated_at):'Owner queue not verified — last observation '+age(o.updated_at)+((o.errors||[]).length?': '+o.errors.join('; '):'');h.className=healthy?'':'stale';const q=document.getElementById('owner-queue');q.replaceChildren();if(!pending.length&&!unknown.length)q.append(elem('p','Nothing is currently confirmed to need your response.'));for(const x of pending)q.append(renderItem(x,x.kind||'Manual testing'));const u=document.getElementById('unknown');u.replaceChildren();document.getElementById('unknown-count').textContent='('+unknown.length+')';for(const x of unknown)u.append(renderItem(x,'Not confirmed — do not act on stale evidence'));const w=document.getElementById('agents');w.replaceChildren();for(const x of a.agent_work||[])w.append(renderItem(x,x.kind||'Agent triage / validation'))}function update(){const now=Date.now()/1000;renderAttention(now);const host=document.getElementById('items');host.replaceChildren();for(const c of data.candidates){const card=document.createElement('article');const ageSeconds=typeof c.updated==='number'?Math.max(0,now-c.updated):null,stale=!c.verdict&&c.phase==='running'&&ageSeconds!==null&&ageSeconds>30;card.append(elem('h2',c.id+' — '+(stale?'STALE — last reported running':c.state),stale?'stale':''));card.append(elem('p',c.detail||c.phase||'Awaiting activity'));if(ageSeconds!==null)card.append(elem('p','Last heartbeat: '+Math.floor(ageSeconds)+'s ago'+(stale?' — UNCONFIRMED':''),stale?'stale':''));if(c.check_index&&c.total)card.append(elem('p','Check '+c.check_index+'/'+c.total));if(c.log)link(card,'Open local log',c.log);if(c.report)link(card,'Open report',c.report);host.append(card)}if(!data.candidates.length)host.append(elem('p','No candidates in the shared exchange.'))}update();</script></body></html>

'''


_PAGE = r'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<meta http-equiv="refresh" content="3"><title>Integration status</title>
<style>
body{font:16px system-ui;max-width:1500px;margin:1.5rem auto;padding:0 1rem;background:#101820;color:#edf2f4}
h1{color:#70d6ff}.columns{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1rem;align-items:start}
.column{background:#15232c;border:1px solid #38505e;border-radius:8px;padding:.8rem;min-width:0}
article{background:#1d2b34;padding:1rem;margin:.8rem 0;border-radius:8px;overflow-wrap:anywhere}
.stale{color:#ffbd69}.muted{color:#aab7bf}pre,code{white-space:pre-wrap;overflow-wrap:anywhere}
a{color:#70d6ff}h2{font-size:1.15rem}h3{font-size:1rem}button{font:inherit}
@media(max-width:850px){.columns{grid-template-columns:1fr}body{margin:1rem auto}}
</style></head><body>
<h1>Integration status</h1><p id="observer-health" role="status"></p>
<div class="columns">
<section class="column" id="validation-column"><h2>Validation actively running <span id="validation-count"></span></h2><div id="validation-cards"></div></section>
<section class="column" id="owner-column"><h2>Ready for owner <span id="owner-count"></span></h2><div id="owner-cards"></div><div id="owner-empty"></div></section>
<section class="column" id="other-column"><h2>Other states</h2><div id="other-cards"></div><details id="history"><summary>Completed history</summary><div id="history-cards"></div></details></section>
</div>
<p class="muted">Owner review and technical verdicts are separate. Operational telemetry is informational and never accepts work.</p>
<script>
const data=__DATA__;
function elem(tag,text,cls){const e=document.createElement(tag);e.textContent=String(text??'');if(cls)e.className=cls;return e}
function link(parent,label,url){if(!url)return;const a=document.createElement('a');a.href=url;a.textContent=label;a.target='_blank';a.rel='noopener';parent.append(a)}
function age(t,now){return typeof t==='number'?Math.max(0,Math.floor(now-t))+'s':'time unknown'}
function shortPath(x){const w=x.worktree||{},v=typeof w==='string'?w:(w.worktree||w.path||'');return v.split(/[\\/]/).filter(Boolean).slice(-2).join('/')||'worktree unknown'}
function renderSteps(parent,steps){if(!Array.isArray(steps)||!steps.length)return;const ol=document.createElement('ol');for(const step of steps)ol.append(elem('li',step));parent.append(ol)}
function renderOwner(x,now){const c=document.createElement('article');c.append(elem('h3',(x.title||x.sprint_id||x.id||'Work')+' — manual review'));if(x.candidate_id)c.append(elem('p','Candidate: '+x.candidate_id));c.append(elem('p',shortPath(x)+' · waiting '+age(x.requested_at||x.submitted_at,now),'muted'));if(x.reason)c.append(elem('p',x.reason));if(x.fixture)c.append(elem('p','Fixture: '+x.fixture));if(x.build_identity)c.append(elem('p','Build: '+x.build_identity));if(x.unverified)c.append(elem('p','Unverified: '+x.unverified));renderSteps(c,x.manual_steps);link(c,'Review report',x.report_url||x.report);return c}
function renderCandidate(x,now){const c=document.createElement('article');c.append(elem('h3',x.id+' — '+x.state));if(x.detail)c.append(elem('p',x.detail));c.append(elem('p',shortPath({worktree:x.author})+' · sprint '+(x.sprint_id||'unknown')));
if(x.validation_active){c.append(elem('p','Check '+x.check_index+'/'+x.total+' · elapsed '+age(x.started,now)+' · live PID '+x.runner_pid));c.append(elem('code',Array.isArray(x.argv)?x.argv.join(' '):''));link(c,'Open current check log',x.log)}
else if(x.check_index&&x.total)c.append(elem('p','Last check '+x.check_index+'/'+x.total));
if(x.findings&&x.findings.length){const h=elem('h4','Required corrections');c.append(h);renderSteps(c,x.findings)}
if(x.log&&!x.validation_active)link(c,'Open log',x.log);link(c,'Open report',x.report);return c}
function renderOtherItem(x,kind,now){const c=document.createElement('article');c.append(elem('h3',(x.title||x.sprint_id||x.id||'Work')+' — '+kind));c.append(elem('p',shortPath(x)+' · '+(x.reason||x.detail||'')));if(x.candidate_ids&&x.candidate_ids.length)c.append(elem('p','Candidate IDs requiring reconciliation: '+x.candidate_ids.join(', ')));if(x.fixture)c.append(elem('p','Fixture: '+x.fixture));if(x.build_identity)c.append(elem('p','Build: '+x.build_identity));renderSteps(c,x.manual_steps);link(c,'Review report',x.report_url||x.report);return c}
function render(now){const a=data.attention||{},observer=data.observer||{},ready=a.awaiting_owner||[],agent=a.agent_work||[],unknown=a.unconfirmed||[],completed=a.completed||[];
const health=document.getElementById('observer-health'),healthy=['watching','idle','ok'].includes(observer.status)&&typeof observer.updated_at==='number'&&now-observer.updated_at<45;
health.textContent=healthy?'Owner-response observation is current.':'Owner-response queue is not verified — unknown is not empty.';health.className=healthy?'':'stale';if((observer.errors||[]).length){const detail=document.createElement('div');detail.append(elem('strong','Observer health errors ('+observer.errors.length+')'));const ul=document.createElement('ul');for(const error of observer.errors)ul.append(elem('li',error));detail.append(ul);health.append(detail)}
const readyIds=new Set(ready.map(x=>String(x.candidate_id||x.id||''))),agentById=new Map(agent.filter(x=>x.candidate_id||x.id).map(x=>[String(x.candidate_id||x.id),x]));
const unknownIds=new Set();for(const x of unknown){if(x.candidate_id||x.id)unknownIds.add(String(x.candidate_id||x.id));for(const id of x.candidate_ids||[])unknownIds.add(String(id))}
const completedIds=new Set(completed.map(x=>String(x.candidate_id||x.id||''))),candidateRows=data.candidates||[],active=candidateRows.filter(x=>x.validation_active),activeIds=new Set(active.map(x=>String(x.id)));
document.getElementById('validation-count').textContent='('+active.length+')';const vh=document.getElementById('validation-cards');vh.replaceChildren();for(const x of active)vh.append(renderCandidate(x,now));if(!active.length)vh.append(elem('p','No verified test process is actively running.'));
document.getElementById('owner-count').textContent='('+ready.length+')';const oh=document.getElementById('owner-cards');oh.replaceChildren();for(const x of ready)oh.append(renderOwner(x,now));const empty=document.getElementById('owner-empty');empty.replaceChildren();if(!ready.length)empty.append(elem('p',healthy?'No current owner response is confirmed.':'Owner queue status is unknown; do not infer an empty queue.','muted'));
const other=document.getElementById('other-cards');other.replaceChildren();const emitted=new Set([...readyIds,...activeIds,...completedIds]);
for(const x of candidateRows){const id=String(x.id);if(emitted.has(id)||unknownIds.has(id))continue;const work=agentById.get(id);if(work&&x.state!=='needs_changes'){emitted.add(id);other.append(renderOtherItem(work,work.reason||'Agent work',now));continue}if(readyIds.has(id))continue;emitted.add(id);other.append(renderCandidate(x,now))}
for(const x of agent){const id=String(x.candidate_id||x.id||'');if(id&&emitted.has(id))continue;if(id)emitted.add(id);other.append(renderOtherItem(x,x.reason||'Agent work',now))}
for(const x of unknown){other.append(renderOtherItem(x,'Unconfirmed — reconcile evidence',now))}
if(!other.childNodes.length)other.append(elem('p','No other candidate state is currently recorded.','muted'));
const history=document.getElementById('history-cards');history.replaceChildren();for(const x of completed)history.append(renderOtherItem(x,x.reason||'Recorded owner history',now));document.getElementById('history').hidden=!completed.length;
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
