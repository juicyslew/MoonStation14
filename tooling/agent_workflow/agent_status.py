"""Bounded, authenticated OpenCode worktree status snapshots."""
from __future__ import annotations
import json, time
from pathlib import Path
from urllib.parse import urlencode

SNAPSHOT = 'agent-status.json'
TTL = 30

def _dispatch(request):
    if request is not None: return request
    try: from . import gate_dispatch
    except ImportError: import gate_dispatch
    return gate_dispatch._request

def _api(request, path, deadline=None):
    timeout=12 if deadline is None else max(0.001, min(12, deadline-time.monotonic()))
    if deadline is not None and timeout <= 0.001: raise TimeoutError('observation budget expired')
    status, body = request(None, 'GET', path, timeout=timeout)
    if status < 200 or status >= 300: raise ValueError(f'OpenCode API status {status}')
    return body

def _data(body): return body.get('data') if isinstance(body, dict) else None

def observe_sessions(request, *, deadline=None):
    """Fetch and validate the bounded active-session/header snapshot."""
    errors=[]; sessions={}; complete=True
    try:
        active=_data(_api(request,'/api/session/active',deadline))
        if not isinstance(active,dict) or len(active)>64: raise ValueError('active session response invalid or exceeds 64 sessions')
    except Exception as exc:
        return {'active':None,'sessions':{},'errors':['active sessions unavailable: '+str(exc)[:160]],'complete':False}
    for sid,info in active.items():
        if not isinstance(info,dict) or info.get('type')!='running':
            complete=False; errors.append(f'active session {str(sid)[:80]} has invalid status entry'); continue
        try:
            h=_data(_api(request,'/api/session/'+str(sid),deadline))
            if not isinstance(h,dict) or str(h.get('id'))!=str(sid) or not _worktree((h.get('location') or {}).get('directory')): raise ValueError()
            sessions[sid]=h
        except Exception:
            complete=False; errors.append(f'active session header {str(sid)[:80]} unavailable: invalid session header')
    return {'active':active,'sessions':sessions,'errors':errors,'complete':complete}

def observe_coding_agents(worktrees, *, request=None, now=None, budget_seconds=5):
    """Observe only configured Agent 01–04 worktrees; never infer from filesystem roster."""
    now=time.time() if now is None else now; slots={}
    for value in worktrees or ():
        p=_worktree(value)
        if not p: continue
        name=Path(value['worktree'] if isinstance(value,dict) and value.get('worktree') else value['path'] if isinstance(value,dict) and value.get('path') else value).resolve().name
        if name in {f'MoonStation14-agent-{i:02d}' for i in range(1,5)}:
            slots[p]=(str(Path(p).resolve()),'Agent '+name[-2:])
    agents=[{'worktree':v[0],'label':v[1],'activity':'idle','summary':'No active work','session_id':''} for p,v in slots.items()]
    result={'checked_at':now,'complete':True,'agents':agents,'errors':[]}
    if not slots: return result
    request=_dispatch(request); deadline=time.monotonic()+max(0,budget_seconds)
    errors=[]
    def api(path): return _api(request,path,deadline)
    observed=observe_sessions(request,deadline=deadline)
    if not observed['complete']:
        for a in agents: a.update(activity='unknown',summary='No active work')
        return {**result,'complete':False,'errors':['active session observation incomplete']}
    sessions=observed['sessions']; roots={}
    for root,label in slots.values():
        try:
            response=api('/api/agent?'+urlencode({'location[directory]':root}))
            roles=_data(response)
            if not isinstance(response,dict) or _worktree((response.get('location') or {}).get('directory'))!=_worktree(root) or not isinstance(roles,list): raise ValueError()
            names=set()
            for role in roles:
                if not isinstance(role,dict) or not isinstance(role.get('name'),str) or role.get('mode') not in {'primary','all','subagent'}: raise ValueError()
                if role['mode']=='primary' and role['name'].startswith('orchestrate-'): names.add(role['name'])
            roots[_worktree(root)]=names
        except Exception: errors.append('agent roles unavailable')
    if errors:
        for a in agents: a['activity']='unknown'
        return {**result,'complete':False,'errors':list(dict.fromkeys(errors))}
    headers=dict(sessions); ancestry_cache={}
    def coding_root(sid):
        original=str(sid)
        if original in ancestry_cache: return ancestry_cache[original]
        cur=str(sid); seen=set()
        for _ in range(8):
            if cur in seen: raise ValueError('session ancestry cycle')
            seen.add(cur); h=headers.get(cur)
            if h is None:
                h=_data(api('/api/session/'+cur))
                if not isinstance(h,dict) or str(h.get('id'))!=cur or not _worktree((h.get('location') or {}).get('directory')): raise ValueError()
                headers[cur]=h
            loc=_worktree((h.get('location') or {}).get('directory'))
            if h.get('agent') in roots.get(loc,set()):
                answer=(loc,cur,h); ancestry_cache[original]=answer; return answer
            parent=h.get('parentID')
            if not parent:
                ancestry_cache[original]=None; return None
            cur=str(parent)
        raise ValueError('session ancestry depth exceeded')
    pending=set()
    try:
        for root,_ in slots.values():
            for endpoint in ('/api/permission/request','/api/form'):
                response=api(endpoint+'?'+urlencode({'location[directory]':root}))
                if not isinstance(response,dict) or _worktree((response.get('location') or {}).get('directory'))!=_worktree(root): raise ValueError()
                body=_data(response)
                if not isinstance(body,list): raise ValueError()
                for item in body:
                    if not isinstance(item,dict) or not isinstance(item.get('sessionID'),str): raise ValueError()
                    pending.add(item['sessionID'])
    except Exception:
        for a in agents: a['activity']='unknown'
        return {**result,'complete':False,'errors':['pending requests unavailable']}
    waiting_roots=set()
    try:
        for sid in pending:
            qualified=coding_root(sid)
            if qualified: waiting_roots.add(qualified[0])
    except Exception:
        for a in agents: a['activity']='unknown'
        return {**result,'complete':False,'errors':['pending session ancestry unavailable']}
    active_by_root={}
    try:
        for sid,h in sessions.items():
            qualified=coding_root(sid)
            if qualified:
                root,root_sid,root_header=qualified
                active_by_root.setdefault(root,(h,root_header,root_sid))
    except Exception:
        for a in agents: a['activity']='unknown'
        return {**result,'complete':False,'errors':['active session ancestry unavailable']}
    for a in agents:
        root=_worktree(a['worktree'])
        if root in waiting_roots:
            a.update(activity='waiting',summary='Waiting for input')
        elif root in active_by_root:
            active,root_header,root_sid=active_by_root[root]
            a.update(activity='working',summary=str(root_header.get('title') or active.get('title') or 'Active coding work')[:200],session_id=root_sid)
    return {**result,'errors':errors}

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

def _project(root, rows=None, attention=None, now=None, snapshot_override=None, include_idle_noncore=False):
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
        pending_intake=[]
        pending_by_id={}
        terminal={'ready_for_owner','needs_changes','blocked','diagnostic_complete','superseded','cancelled','rejected'}
        execution_requested=[]
        try:
            try: from . import gate_progress
            except ImportError: import gate_progress
            alive=gate_progress._pid_alive
        except Exception: alive=lambda _pid: False
        for r in rs:
            if r.get('id') not in head_ids: continue
            head=next(c for c in matching if c.get('id')==r.get('id'))
            outcome=head.get('gate_outcome') or {}
            if outcome.get('result') in terminal or outcome.get('status') in terminal: continue
            if r.get('state') not in {'queued','admitted'}: continue
            pid=r.get('runner_pid')
            live_proof=(isinstance(pid,int) and pid>0 and alive(pid)) or r.get('health',{}).get('state')=='active'
            if live_proof: live_queued.append(r)
            elif r.get('state')=='queued' and (head.get('gate_request') or {}).get('session_id'):
                # Legacy queued runner rows remain governed by their existing liveness proof.
                pending_intake.append(r)
        # Durable request admission can precede any progress row or lead session.
        # Require the request's own snapshot/origin and expected central worktree;
        # a stray historical session id is not admission evidence.
        for candidate in matching:
            req=candidate.get('gate_request') or {}
            origin=req.get('origin_session_id')
            try:
                expected_central=str((Path(candidate['worktree']['worktree']).resolve().parent/'MoonStation14-agent-05').resolve()).casefold()
                central=str(Path(req.get('central_worktree','')).resolve()).casefold()
            except (KeyError, OSError, TypeError, ValueError):
                continue
            bound=(req.get('snapshot_key')==candidate.get('snapshot_key') and isinstance(origin,str) and bool(origin.strip()) and central==expected_central)
            dispatching=req.get('dispatch_pending') is True and req.get('dispatch_state') not in {'blocked','failed'}
            admitted=req.get('prompt_admitted') is True and isinstance(req.get('session_id'),str) and bool(req.get('session_id').strip())
            outcome=candidate.get('gate_outcome') or {}
            job=None
            try:
                try: from . import gate_execution
                except ImportError: import gate_execution
                job=gate_execution.read(root,candidate.get('id'))
            except (ImportError,OSError,ValueError,TypeError): pass
            job_bound=bool(job and job.get('job_id')==candidate.get('id') and
                           job.get('candidate_id')==candidate.get('id') and
                           job.get('snapshot_key')==candidate.get('snapshot_key') and
                           isinstance(req.get('session_id'),str) and
                           job.get('lead_session_id')==req.get('session_id'))
            job_requested=job_bound and job.get('state') in {'requested','launching'}
            if job_requested:
                execution_requested.append(candidate)
            # A persisted blocked/terminal progress state supersedes prompt
            # admission unless a current durable execution job is outstanding.
            candidate_row=next((r for r in rs if r.get('id')==candidate.get('id')),None)
            row_terminal=bool(candidate_row and (
                str(candidate_row.get('phase') or '').casefold() in terminal or
                str(candidate_row.get('state') or '').casefold() in terminal or
                candidate_row.get('state') in {'OPERATIONAL BLOCKED','UNCONFIRMED / STALLED'}))
            if (bound and (dispatching or admitted) and not row_terminal and
                    (outcome.get('result') not in terminal and outcome.get('status') not in terminal)):
                pending_by_id[candidate.get('id')]=candidate
        active_gate=[]
        active_sessions=snapshot.get('active_sessions',{}) if fresh else {}
        for head in matching:
            req=head.get('gate_request') or {}; sid=req.get('session_id')
            session=active_sessions.get(sid) if sid else None
            outcome=head.get('gate_outcome') or {}
            if not session or outcome.get('result') in terminal or outcome.get('status') in terminal: continue
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
        waiting=live_queued or pending_intake or list(pending_by_id.values()) or execution_requested
        column='active_validation' if live or (active_gate and not live_queued and not execution_requested) else 'waiting_validation' if waiting else 'ready_manual' if manual else 'working'
        validation_stage='checks' if live else 'review' if active_gate and not live_queued and not execution_requested else None
        summary=''; sid=''; candidate_id=None; detail=''
        if manual and column=='ready_manual':
            m=manual[0]; candidate_id=m.get('candidate_id'); summary=m.get('title') or m.get('reason') or 'Manual testing'; detail=m.get('reason','')
        elif action or pending_by_id or execution_requested:
            r=action[0] if action else None
            candidate_id=r.get('id') if r else next(iter(pending_by_id or {c.get('id'):c for c in execution_requested}))
            pending_head=pending_by_id.get(candidate_id) or next((c for c in execution_requested if c.get('id')==candidate_id),None)
            summary=(pending_head.get('gate_plan') or {}).get('title') or pending_head.get('sprint_id') or 'Current candidate' if pending_head else r.get('detail') or r.get('state') or 'Validation work'
            detail=(r.get('detail') or '') if r else (pending_head.get('gate_request') or {}).get('reason') or ''
            if pending_head:
                bound_row=next((item for item in rs if item.get('id')==candidate_id),None)
                if bound_row:
                    health=bound_row.get('health') or {}
                    detail=bound_row.get('detail') or health.get('reason') or detail
                    if health.get('state')=='blocked' and 'validation blocked' not in detail.casefold():
                        detail='Validation blocked — '+detail
            if (not _useful_title(prior.get('summary')) or prior.get('summary')=='No current project') and head:
                summary=(head.get('gate_plan') or {}).get('title') or summary
        elif active_gate and not live_queued:
            candidate_id=active_gate[0].get('id'); summary=(active_gate[0].get('gate_plan') or {}).get('title') or active_gate[0].get('sprint_id') or 'Current candidate'; detail='Integration lead is reviewing this candidate'
        elif head:
            candidate_id=head.get('id'); summary=(head.get('gate_plan') or {}).get('title') or head.get('sprint_id') or 'Current project'; detail=(head.get('gate_request') or {}).get('reason') or ''
            waiting_row=next((r for r in [*waiting,*rs] if r.get('id')==candidate_id),None)
            if waiting_row:
                health=waiting_row.get('health') or {}
                detail=waiting_row.get('detail') or health.get('reason') or detail
                if health.get('state')=='blocked' and 'validation blocked' not in detail.casefold():
                    detail='Validation blocked — '+detail
            owner_review=head.get('owner_review') or {}
            if owner_review.get('state')=='awaiting_owner' and not (owner_review.get('readiness') or {}).get('eligible'):
                blocked=next((item for item in attention.get('agent_work',[]) if item.get('candidate_id')==candidate_id and item.get('reason')),None)
                if blocked: detail=blocked['reason']
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
        card=dict(slot,column=column,activity=activity if activity in {'working','idle','unknown','waiting'} else 'unknown',summary=str(summary)[:500],session_id=sid,checked_at=snapshot.get('checked_at'),candidate_id=candidate_id,detail=str(detail)[:500],validation_stage=validation_stage,findings=findings,manual_steps=manual_steps,fixture=fixture,report_url=report_url,log=log,command=command,check_index=check_index,total=total,started=started,runner_pid=runner_pid,additional_work=add)
        # Hide only confirmed-idle non-core worktree cards in the canonical
        # projection. Keep roster/snapshot metadata so active agents return.
        if include_idle_noncore or not (not slot['core'] and column=='working' and activity=='idle'):
            agents.append(card)
    return {'agents':agents,'checked_at':snapshot.get('checked_at'),'errors':snapshot.get('errors',[]) if fresh else ['activity snapshot stale or unavailable']}

def project(root, rows=None, attention=None, now=None):
    return _project(root,rows,attention,now)

def reconcile(root, workflow, request=None, now=None, rows=None, attention=None, notify=True):
    root=Path(root).resolve(); now=time.time() if now is None else now; errors=[]
    request=_dispatch(request)
    observed=observe_sessions(request); active=observed['active']; sessions=observed['sessions']; errors.extend(observed['errors']); header_ok=observed['complete']
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
    projected=_project(root,rows,attention,now,base,include_idle_noncore=True)
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
