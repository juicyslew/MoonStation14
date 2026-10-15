"""Bounded, authenticated OpenCode worktree status snapshots."""
from __future__ import annotations
import json, time
from pathlib import Path

SNAPSHOT = 'agent-status.json'
TTL = 30

def _dispatch(request):
    if request is not None: return request
    try: from . import gate_dispatch
    except ImportError: import gate_dispatch
    return gate_dispatch._request

def _api(request, path):
    status, body = request(None, 'GET', path, timeout=12)
    if status < 200 or status >= 300: raise ValueError(f'OpenCode API status {status}')
    return body

def _data(body): return body.get('data') if isinstance(body, dict) else None

def _session_time(session):
    value=session.get('time')
    if isinstance(value,dict): value=value.get('updated') or value.get('created')
    return value if isinstance(value,(int,float)) else 0

def _useful_title(title):
    if not isinstance(title,str): return False
    folded=title.strip().casefold()
    return not (folded in {'','no current project','unknown project','agent worktree'} or (folded.startswith('agent ') and 'worktree' in folded))

def _worktree(value):
    if isinstance(value, dict): value = value.get('worktree') or value.get('path')
    if not isinstance(value, str) or not value: return None
    try: return str(Path(value).resolve()).casefold()
    except (OSError, ValueError): return None

def roster(root, candidates=()):
    root=Path(root).resolve(); family=root.parents[2] if len(root.parents)>2 else root.parent
    paths=[family/'MoonStation14']+[family/f'MoonStation14-agent-{i:02d}' for i in range(1,5)]
    integration_lead=str((family/'MoonStation14-agent-05').resolve()).casefold()
    paths += [Path(p) for c in candidates if (p:=_worktree(c.get('worktree'))) and p!=integration_lead]
    seen=set(); result=[]
    for p in paths:
        if not p.is_dir(): continue
        canonical=str(p.resolve()).casefold()
        if canonical in seen: continue
        seen.add(canonical); name=p.name
        core=name.startswith('MoonStation14-agent-') and name[-2:].isdigit() and 1<=int(name[-2:])<=4
        label='Agent '+name[-2:] if core else 'Integration lead' if name=='MoonStation14-agent-05' else 'Main consultant' if name=='MoonStation14' else name
        result.append({'id':canonical,'label':label,'worktree':str(p.resolve()),'core':core})
    return result

def _attention_for(attention, path):
    return [a for a in attention.get('awaiting_owner',[]) if _worktree(a.get('worktree'))==path]

def _project(root, rows=None, attention=None, now=None, snapshot_override=None):
    root=Path(root).resolve(); now=time.time() if now is None else now
    if snapshot_override is not None: snapshot=snapshot_override
    else:
        try: snapshot=json.loads((root/SNAPSHOT).read_text(encoding='utf-8'))
        except (OSError,ValueError): snapshot={}
    fresh=isinstance(snapshot.get('checked_at'),(int,float)) and 0<=now-snapshot['checked_at']<=TTL
    try:
        if rows is None:
            try: from . import gate_progress
            except ImportError: import gate_progress
            rows=gate_progress._rows(root)
        if attention is None:
            try: from . import owner_attention
            except ImportError: import owner_attention
            attention=owner_attention.collect(root, refresh=False)
    except Exception as exc: return {'agents':[], 'checked_at':snapshot.get('checked_at'), 'errors':[str(exc)[:250]]}
    attention=attention or {}; old={a.get('id'):a for a in snapshot.get('agents',[]) if isinstance(a,dict)}
    # Heads, not historical candidate files, define the current work/backlog.
    try:
        try: from . import owner_attention
        except ImportError: import owner_attention
        heads=owner_attention.current_work(root)
    except Exception: heads=[]
    candidates=[g['head'] for g in heads if g.get('head')]
    legacy_by_path={}
    for group in heads:
        if group.get('head'): continue
        for c in group.get('legacy_candidates',[]):
            p=_worktree(c.get('worktree'))
            if p: legacy_by_path.setdefault(p,[]).append(c)
    agents=[]
    roster_candidates=candidates+[c for values in legacy_by_path.values() for c in values]
    for slot in roster(root,roster_candidates):
        path=slot['id']; matching=[c for c in candidates if _worktree(c.get('worktree'))==path]
        prior=old.get(path,{})
        rs=[r for r in rows if _worktree(r.get('author'))==path]
        legacy=legacy_by_path.get(path,[])
        # A physical live check remains visible even when the head changed.
        live=[r for r in rs if r.get('validation_active')]
        head_ids={c.get('id') for c in matching}
        live_queued=[]
        try:
            try: from . import gate_progress
            except ImportError: import gate_progress
            alive=gate_progress._pid_alive
        except Exception: alive=lambda _pid: False
        for r in rs:
            if r.get('id') not in head_ids or r.get('state') not in {'queued','admitted'}: continue
            head=next(c for c in matching if c.get('id')==r.get('id'))
            if head.get('gate_outcome',{}).get('result') in {'ready_for_owner','needs_changes','blocked'}: continue
            pid=r.get('runner_pid')
            if (isinstance(pid,int) and pid>0 and alive(pid)) or r.get('health',{}).get('state')=='active': live_queued.append(r)
        active_gate=[]
        active_sessions=snapshot.get('active_sessions',{}) if fresh else {}
        for head in matching:
            req=head.get('gate_request') or {}; sid=req.get('session_id')
            session=active_sessions.get(sid) if sid else None
            if not session or head.get('gate_outcome',{}).get('result') in {'ready_for_owner','needs_changes','blocked'}: continue
            try:
                try: from . import gate_dispatch
                except ImportError: import gate_dispatch
                if gate_dispatch._metadata(head,session): active_gate.append(head)
            except Exception: pass
        queued=[r for r in rs if r.get('validation_active')] + live_queued
        manual=sorted(_attention_for(attention,path),key=lambda a:a.get('requested_at') or 0,reverse=True) if fresh else []
        activity=old.get(path,{}).get('activity','unknown') if fresh else 'unknown'
        action=sorted(live or queued,key=lambda r:r.get('sort_time') or 0,reverse=True)
        def recency(c):
            request=c.get('owner_review') or {}; gate=c.get('gate_request') or {}
            return max((v for v in (request.get('requested_at'),gate.get('requested_at'),c.get('submitted_at'),c.get('created_at')) if isinstance(v,(int,float))),default=0)
        head=max(matching,key=recency) if matching else None
        latest_legacy_row=None; legacy_candidate=None
        if not head and not action and not manual and legacy:
            by_id={c.get('id'):c for c in legacy}
            known=[r for r in rs if r.get('id') in by_id]
            if known:
                latest_legacy_row=max(known,key=lambda r:r.get('updated') or r.get('sort_time') or 0)
                legacy_candidate=by_id.get(latest_legacy_row.get('id'))
        column='active_validation' if live or (active_gate and not live_queued) else 'waiting_validation' if live_queued else 'ready_manual' if manual else 'working'
        validation_stage='checks' if live else 'review' if active_gate and not live_queued else None
        summary=''; sid=''; candidate_id=None; detail=''
        if manual and column=='ready_manual':
            m=manual[0]; candidate_id=m.get('candidate_id'); summary=m.get('title') or m.get('reason') or 'Manual testing'; detail=m.get('reason','')
        elif action:
            r=action[0]; candidate_id=r.get('id'); summary=r.get('detail') or r.get('state') or 'Validation work'; detail=r.get('detail') or ''
            if (not _useful_title(prior.get('summary')) or prior.get('summary')=='No current project') and head:
                summary=(head.get('gate_plan') or {}).get('title') or summary
        elif active_gate and not live_queued:
            candidate_id=active_gate[0].get('id'); summary=(active_gate[0].get('gate_plan') or {}).get('title') or active_gate[0].get('sprint_id') or 'Current candidate'; detail='Integration lead is reviewing this candidate'
        elif head:
            candidate_id=head.get('id'); summary=(head.get('gate_plan') or {}).get('title') or head.get('sprint_id') or 'Current project'; detail=(head.get('gate_request') or {}).get('reason') or ''
        elif legacy_candidate:
            candidate_id=legacy_candidate.get('id'); summary=(legacy_candidate.get('gate_plan') or {}).get('title') or legacy_candidate.get('sprint_id') or latest_legacy_row.get('state') or 'Latest known work'
            detail='Latest known validation result; current snapshot/head unconfirmed'
        else: summary='No current project'
        findings=[]; manual_steps=[]; fixture=''; report_url=''; log=''; check_index=None; total=None; started=None; runner_pid=None
        if action:
            chosen=action[0]
            findings=chosen.get('findings') or []; log=chosen.get('log') or ''; report_url=chosen.get('report') or ''
            check_index=chosen.get('check_index'); total=chosen.get('total'); started=chosen.get('started'); runner_pid=chosen.get('runner_pid')
        elif latest_legacy_row:
            findings=latest_legacy_row.get('findings') or []
        if head and head.get('gate_outcome',{}).get('result')=='needs_changes':
            findings=head.get('gate_outcome',{}).get('findings') or findings
        if manual:
            m=manual[0]; manual_steps=m.get('manual_steps') or []; fixture=m.get('fixture') or ''; report_url=m.get('report_url') or ''
        add=[{'title':(c.get('gate_plan') or {}).get('title') or c.get('sprint_id'), 'state':next((r.get('state') for r in rs if r.get('id')==c.get('id')),'registered')} for c in matching if c.get('id')!=candidate_id]
        if prior.get('session_id'):
            sid=prior['session_id']
            if activity=='working' and _useful_title(prior.get('summary')) and prior.get('summary')!='No current project': summary=prior['summary']
        command=' '.join(action[0].get('argv') or []) if action and isinstance(action[0].get('argv'),list) else ''
        agents.append(dict(slot,column=column,activity=activity if activity in {'working','idle','unknown','waiting'} else 'unknown',summary=str(summary)[:500],session_id=sid,checked_at=snapshot.get('checked_at'),candidate_id=candidate_id,detail=str(detail)[:500],validation_stage=validation_stage,findings=findings,manual_steps=manual_steps,fixture=fixture,report_url=report_url,log=log,command=command,check_index=check_index,total=total,started=started,runner_pid=runner_pid,additional_work=add))
    return {'agents':agents,'checked_at':snapshot.get('checked_at'),'errors':snapshot.get('errors',[]) if fresh else ['activity snapshot stale or unavailable']}

def project(root, rows=None, attention=None, now=None):
    return _project(root,rows,attention,now)

def reconcile(root, workflow, request=None, now=None, rows=None, attention=None, notify=True):
    root=Path(root).resolve(); now=time.time() if now is None else now; errors=[]
    request=_dispatch(request)
    try:
        active=_data(_api(request,'/api/session/active'))
        if not isinstance(active,dict) or len(active)>64: raise ValueError('active session response invalid or exceeds 64 sessions')
    except Exception as exc: active=None; errors.append('active sessions unavailable: '+str(exc)[:180])
    sessions={}; header_ok=active is not None
    for sid,info in list((active or {}).items()):
        if not isinstance(info,dict) or info.get('type') not in {'running'}:
            header_ok=False; errors.append(f'active session {str(sid)[:80]} has invalid status entry'); continue
        try:
            h=_data(_api(request,'/api/session/'+str(sid)))
            if not isinstance(h,dict) or str(h.get('id'))!=str(sid) or not _worktree((h.get('location') or {}).get('directory')): raise ValueError('invalid session header')
            sessions[sid]=h
        except Exception as exc:
            header_ok=False; errors.append(f'active session header {str(sid)[:80]} unavailable: {str(exc)[:120]}')
    old={}
    try: old=workflow.readjson(root/SNAPSHOT)
    except Exception: pass
    old_episodes=old.get('idle_episodes',{}) if isinstance(old,dict) else {}
    activity={}
    for sid,h in sessions.items():
        p=_worktree((h.get('location') or {}).get('directory'))
        previous=activity.get(p)
        if previous is None or (_session_time(h),str(h.get('id') or sid)) > (_session_time(previous),str(previous.get('id') or '')):
            activity[p]=h
    # Current candidate heads and progress are supplied by established owners.
    if rows is None:
        try:
            try: from . import gate_progress
            except ImportError: import gate_progress
            rows=gate_progress._rows(root)
        except Exception as exc: rows=[]; errors.append(str(exc)[:200])
    if attention is None:
        try:
            try: from . import owner_attention
            except ImportError: import owner_attention
            attention=owner_attention.collect(root,workflow=workflow,refresh=False)
        except Exception as exc: attention={}; errors.append(str(exc)[:200])
    # Activity is authoritative only if complete map and every running header resolved.
    prior={a.get('id'):a for a in old.get('agents',[]) if isinstance(a,dict)}
    active_sessions={sid:h for sid,h in sessions.items()} if header_ok else {}
    base={'checked_at':now,'agents':[],'errors':errors,'idle_episodes':old_episodes,'active_sessions':active_sessions}
    # Project against this observation in memory; publish exactly one snapshot.
    for slot in roster(root,[]):
        h=activity.get(slot['id']); prior_card=prior.get(slot['id'],{})
        title=(h or {}).get('title')
        base['agents'].append(dict(slot,activity=('working' if h else 'idle') if header_ok else 'unknown',summary=(title if _useful_title(title) else prior_card.get('summary')) or 'No current project',session_id=(h or {}).get('id',''),column='working',candidate_id=None,detail='',findings=[],manual_steps=[],additional_work=[]))
    projected=_project(root,rows,attention,now,base)
    agents=[]
    for card in projected['agents']:
        saved=next((a for a in base['agents'] if a['id']==card['id']),None)
        if saved:
            card['activity']=saved['activity']; card['session_id']=saved['session_id']
            if saved['session_id'] and _useful_title(saved.get('summary')): card['summary']=saved['summary']
            elif not card.get('candidate_id'): card['summary']=saved['summary']
        agents.append(card)
    episodes=dict(old_episodes)
    if header_ok and not errors:
        new_idle=[]
        for card in agents:
            ident=card['id']; active_work=card['activity']=='working' or card['column']!='working'
            if not card['core']:
                episodes.pop(ident,None); continue
            if active_work: episodes.pop(ident,None); continue
            episode=episodes.get(ident) or {'since':now,'notified':False}
            if notify and now-episode['since']>=10 and not episode['notified']:
                new_idle.append((card,episode))
            episodes[ident]=episode
        if new_idle:
            message='; '.join(card['label']+': '+(card.get('summary') or 'idle')[:140] for card,episode in new_idle)
            try:
                workflow.native_notify(root/'integration-status.html','Core coding agents idle',message)
                for card,episode in new_idle: episode['notified']=True
            except Exception as exc: errors.append('idle notification failed: '+str(exc)[:160])
    snapshot={'version':1,'checked_at':now,'agents':agents,'errors':errors,'idle_episodes':episodes,'active_sessions':active_sessions}
    workflow.writejson(root/SNAPSHOT,snapshot)
    return {'monitored':len(agents),'core_monitored':sum(a['core'] for a in agents),'idle':sum(a['core'] and a['activity']=='idle' and a['column']=='working' for a in agents),'errors':errors}
