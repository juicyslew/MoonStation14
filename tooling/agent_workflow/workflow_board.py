"""Privacy-whitelisted, deterministic projection of saved workflow state."""
from __future__ import annotations

import hashlib
import json

try:
    import workflow_store
except ModuleNotFoundError:  # package import with only the workspace on sys.path
    from . import workflow_store


PHASES = ("Idle", "Building", "Queued", "Checking", "Reviewing", "Your testing", "Accepted")


def _short(value, limit=40):
    value = " ".join(str(value or "").split())
    return value if len(value) <= limit else value[:limit - 1].rstrip() + "…"


def _sections(record, name):
    value = record.get(name, [])
    return value if isinstance(value, list) else []


def _terminal_result_created_ms(record):
    terminal = record.get("terminal_result") or {}
    result_id = terminal.get("result_id") if isinstance(terminal, dict) else None
    generation = (record.get("run") or {}).get("current_attempt")
    if not result_id:
        return None
    compact_event=record.get("terminal_result_event")
    if isinstance(compact_event,dict) and compact_event.get("event_id")==result_id and compact_event.get("generation")==generation:
        payload=compact_event.get("payload") or {}
        value=compact_event.get("created_ms")
        if isinstance(payload,dict) and payload.get("result_id")==result_id:
            return value if isinstance(value,int) and not isinstance(value,bool) and value>=0 else None
    for event in _sections(record, "events"):
        if (isinstance(event, dict) and event.get("event_id") == result_id
                and event.get("generation") == generation
                and (event.get("payload") or {}).get("result_id") == result_id):
            value = event.get("created_ms")
            return value if isinstance(value, int) and not isinstance(value, bool) and value >= 0 else None
    return None


def _result(task):
    result = task.get("result") or {}
    return result if isinstance(result, dict) else {}


def _items(values, fields):
    return [{key: value[key] for key in fields if key in value and isinstance(value[key], (str, int, float, bool, type(None), list))}
            for value in values if isinstance(value, dict)]


def _safe_findings(values):
    out = []
    for value in values:
        if isinstance(value, str):
            out.append(value)
        elif isinstance(value, dict):
            finding={k:value[k] for k in ("code","category","severity","summary","message","criterion_id","path","location","required_change")
                     if k in value and isinstance(value[k],(str,int,float,bool,type(None)))}
            if isinstance(value.get("evidence_refs"),list):
                finding["evidence_refs"]=[ref for ref in value["evidence_refs"] if isinstance(ref,str)]
            out.append(finding)
    return out


def _decision_body(row):
    if not isinstance(row, dict):
        return {}
    value = row.get("decision")
    if isinstance(value,dict):
        body=dict(value)
        if row.get("decision_id") is not None: body.setdefault("decision_id",row["decision_id"])
        return body
    return row


def _artifact_items(values):
    rows = []
    for row in values:
        if not isinstance(row,dict):
            continue
        item = row.get("artifact") if isinstance(row.get("artifact"),dict) else row
        rows.append({k:item[k] for k in ("artifact_id","type","path","sha256","size_bytes","created_ms","label")
                     if k in item and isinstance(item[k],(str,int,float,bool,type(None)))})
    return rows


def _delivery_items(values):
    return [{"delivery_id":v.get("delivery_id"),"state":v.get("state"),"target_kind":v.get("target_kind"),
             "attempts":v.get("attempts"),"transport_ack":v.get("transport_receipt") is not None,
             "protocol_ack":v.get("protocol_ack") is not None,
             "next_actor":"Author" if v.get("state") == "TRANSPORT_ACCEPTED" else "Delivery transport" if v.get("state") == "PENDING" else None,
             "next_action":"Await author protocol acknowledgement" if v.get("state") == "TRANSPORT_ACCEPTED" else "Deliver saved result" if v.get("state") == "PENDING" else None}
            for v in values if isinstance(v,dict)]


def _regression_items(values):
    rows=[]
    for value in values:
        if not isinstance(value,dict):
            continue
        row={k:value[k] for k in ("regression_id","acceptance_id","created_ms","completed_ms")
             if k in value and isinstance(value[k],(str,int,float,bool,type(None)))}
        job=value.get("job")
        if isinstance(job,dict):
            row["job"]={k:job[k] for k in ("status","result") if k in job and isinstance(job[k],(str,int,float,bool,type(None)))}
        rows.append(row)
    return rows


def _record_faults(record, run_id, requested_worktree):
    """Parse persisted unresolved faults once for both cards and global notices."""
    opened, resolved = [], []
    for row in _sections(record,"incidents"):
        if not isinstance(row,dict):
            continue
        body=row.get("incident",row)
        fault={"id":body.get("incident_id"),"run_id":run_id,"kind":body.get("kind","incident"),
               "state":body.get("state","OPEN"),"seen":row.get("seen_ms") is not None,
               "summary":_short(body.get("summary") or body.get("message") or "Workflow incident",100)}
        for key in ("code","recovery_owner","owner_action_required","evaluation_generation","generation","task_id","next_retry_ms","next_automatic_retry_ms","retry_after_ms"):
            value=body.get(key)
            if isinstance(value,(str,int,float,bool,type(None))): fault[key]=value
        run=record.get("run") or {}
        author=run.get("author") if isinstance(run.get("author"),dict) else {}
        scope=record.get("worktree")
        bound=(row.get("resolved_ms") is None and isinstance(body.get("incident_id"),str) and bool(body.get("incident_id"))
               and body.get("run_id")==run_id and isinstance(scope,str) and bool(scope)
               and author.get("worktree")==scope and scope==requested_worktree)
        if bound and body.get("state") in ("OPEN","RECOVERING") and row.get("seen_ms") is None:
            fault["capabilities"]=["seen"]
            fault["bindings"]={"incident_id":body["incident_id"],"run_id":run_id,"worktree":scope}
        elif body.get("incident_id"):
            fault["capabilities"]=[]
        (resolved if row.get("resolved_ms") is not None else opened).append(fault)
    for resource in _sections(record,"resources"):
        if resource.get("state")=="UNKNOWN":
            native=resource.get("resource_key")=="native_validation"
            item={"id":resource.get("claim_id"),"run_id":run_id,"kind":"resource","state":"UNKNOWN",
                  "seen":False,"summary":"Native validation ownership unknown" if native else _short(resource.get("resource_key") or "Resource ownership unknown",100)}
            for key in ("generation","evaluation_generation","task_id"):
                if key in resource and isinstance(resource.get(key),(str,int,float,bool,type(None))): item[key]=resource[key]
            opened.append(item)
    for delivery in _sections(record,"deliveries"):
        state=str(delivery.get("state") or delivery.get("status") or "").upper()
        if state in ("ERROR","FAILED","REJECTED"):
            opened.append({"id":delivery.get("delivery_id"),"run_id":run_id,"kind":"delivery","state":state,
                           "seen":False,"summary":"Persisted result delivery error"})
    return opened, resolved


def _criteria(values):
    out=[]
    for c in values if isinstance(values,list) else []:
        if not isinstance(c,dict):
            continue
        row={k:c[k] for k in ("criterion_id","description","text","status") if k in c and isinstance(c[k],str)}
        refs=c.get("evidence_refs")
        if isinstance(refs,list): row["evidence_refs"]=[v for v in refs if isinstance(v,str)]
        out.append(row)
    return out


def _strings(values):
    return [v for v in values if isinstance(v,str)] if isinstance(values,list) else []


def _author_label(*values):
    for value in values:
        if isinstance(value,str):
            return value
        if isinstance(value,dict):
            for key in ("session_label","session_id","label"):
                if isinstance(value.get(key),str): return value[key]
    return None


def _phase(record):
    attempt = record.get("attempt") or {}
    run = record.get("run") or {}
    saved = str(attempt.get("phase") or "").upper()
    tasks = _current_tasks(record)
    # Only results attached to current-generation task records are considered.
    results = [_result(t) for t in tasks if isinstance(t, dict)]
    terminal = record.get("terminal_result") or {}
    focused = attempt.get("result") or terminal.get("result")
    if focused not in ("PASS", "FAIL", "ERROR"):
        focused = None
    generation = run.get("current_attempt", attempt.get("generation"))
    digest = run.get("input_digest")
    matching = []
    for wrapper in _sections(record, "owner_decisions"):
        if not isinstance(wrapper, dict):
            continue
        body = _decision_body(wrapper)
        if body.get("evaluation_generation", body.get("generation")) != generation:
            continue
        if body.get("input_digest") != digest:
            continue
        matching.append(body)
    latest = max(matching,key=lambda d:(d.get("expected_sequence",-1),d.get("generation",generation))) if matching else {}
    action = latest.get("action") or latest.get("decision")
    if isinstance(action, dict):
        action = action.get("action")
    accepted = any(
        a.get("decision_id") in {d.get("decision_id") for d in matching} and a.get("run_id") == run.get("run_id")
        and a.get("evaluation_generation", a.get("generation")) == generation
        and a.get("input_digest") == digest for a in _sections(record, "acceptances") if isinstance(a, dict))
    changed = action == "REQUEST_CHANGES"
    if saved == "CLOSED":
        return None, focused, saved
    if accepted:
        return "Accepted", focused, saved
    if saved == "DECIDED" and focused == "PASS" and action == "CANCEL":
        return None, focused, "OWNER_CLOSED"
    if changed or (saved == "DECIDED" and focused == "FAIL"):
        return "Building", focused, saved
    if saved == "DECIDED" and focused == "PASS":
        return "Your testing", focused, saved
    # A STOPPING attempt remains at the actual boundary of its active work.
    if saved == "STOPPING":
        kind = _boundary_kind(tasks)
        return ("Checking" if kind == "CHECK" else "Reviewing"), focused, saved
    if saved in ("QUEUED", "CHECKING", "REVIEWING"):
        return {"QUEUED":"Queued", "CHECKING":"Checking", "REVIEWING":"Reviewing"}[saved], focused, saved
    # Terminal ERROR and recovery boundaries use task kind, not whichever task
    # happens to be last in a timestamp-ordered activity list.
    if saved == "DECIDED" and focused == "ERROR":
        return ("Reviewing" if _boundary_kind(tasks) == "REVIEW" else "Checking"), focused, saved
    return "Queued", focused, saved


def _boundary_kind(tasks):
    """Choose actual unresolved/failed task boundary, preferring fault evidence."""
    failed = [t for t in tasks if isinstance(t,dict) and _result(t).get("decision") == "ERROR"]
    if failed:
        return str(failed[-1].get("kind", "")).upper()
    active = [t for t in tasks if isinstance(t, dict) and t.get("state") in
              ("RUNNING", "CLAIMED", "DISPATCHING", "CANCEL_REQUESTED", "FAILED")]
    chosen = active[-1] if active else next((t for t in reversed(tasks) if isinstance(t, dict)), {})
    return str(chosen.get("kind", "")).upper()


def _paused(row):
    value=row.get("paused_since_ms")
    return isinstance(value,(int,float)) and value > 0


def _native_capacity(raw, records):
    value=raw.get("native_capacity")
    if isinstance(value,dict) and all(type(value.get(key)) is int and value[key]>=0 for key in ("held","unknown")):
        return {"held":value["held"],"unknown":value["unknown"]}
    # Legacy snapshots/mock fixtures have no aggregate; visible resources are
    # the honest fallback and cannot assert global occupancy.
    return None


def _pending_native_task_ids(records, occupied):
    """Shared reservation predicate for queue totals and visible card reasons."""
    if not occupied:
        return set()
    pending=set()
    for record in records:
        run=record.get("run") or {}; generation=run.get("current_attempt")
        attempt=record.get("attempt") or {}
        if str(attempt.get("phase") or "").upper() in ("STOPPING","DECIDED","CLOSED"):
            continue
        owned={(r.get("task_id"),r.get("generation")) for r in _sections(record,"resources")
               if r.get("resource_key")=="native_validation"}
        paused_ids={p.get("task_id") for p in _sections(record,"dependency_pauses") if _paused(p)}
        for task in _current_tasks(record):
            if (task.get("kind")=="CHECK" and task.get("state") in ("CLAIMED","RUNNING")
                    and task.get("task_id") not in paused_ids
                    and (task.get("task_id"),task.get("evaluation_generation",generation)) not in owned):
                pending.add(task.get("task_id") or (run.get("run_id"),generation,"pending"))
    return pending


def _system_state(records, raw, worktree):
    claims, queued, paused = {}, set(), set()
    for record in records:
        run=record.get("run") or {}
        run_id=run.get("run_id")
        for resource in _sections(record,"resources"):
            state=resource.get("state")
            if resource.get("resource_key")!="native_validation" or state not in ("HELD","UNKNOWN"):
                continue
            key=resource.get("claim_id") or (run_id,resource.get("generation"),resource.get("task_id"),resource.get("resource_key"))
            if claims.get(key)!="UNKNOWN": claims[key]=state
        for task in _current_tasks(record):
            if task.get("kind")=="CHECK" and task.get("state")=="QUEUED":
                queued.add(task.get("task_id") or (run_id,task.get("evaluation_generation"),task.get("kind")))
        for pause in _sections(record,"dependency_pauses"):
            if _paused(pause) and "provider" in str(pause.get("dependency_key","")).lower():
                paused.add(pause.get("task_id") or (run_id,pause.get("dependency_key")))
    global_capacity=_native_capacity(raw,records)
    held=sum(state=="HELD" for state in claims.values()) if global_capacity is None else global_capacity["held"]
    unknown=sum(state=="UNKNOWN" for state in claims.values()) if global_capacity is None else global_capacity["unknown"]
    occupied=held+unknown
    pending=_pending_native_task_ids(records,occupied)
    queued_total=len(queued)+len(pending)
    return {"native_occupancy":{"held":held,"unknown":unknown,"state":"unknown" if unknown else ("occupied" if held else "free")},
            "queue":{"queued_tasks":queued_total,"queued_native_tasks":len(queued),
                     "pending_native_tasks":len(pending),"scope":"worktree",
                     "worktree":worktree,"state":"queued" if queued_total else "idle"},
            "ai_availability":"provider_paused" if paused else "unknown"}


def _unscoped_counts(raw):
    value=raw.get("unscoped_incidents")
    if not isinstance(value,dict): return None
    counts={key:value.get(key) for key in ("open","recovering","total")}
    if any(type(count) is not int or count < 0 for count in counts.values()): return None
    if counts["open"]+counts["recovering"] != counts["total"]: return None
    return counts


def _current_tasks(record):
    generation = (record.get("run") or {}).get("current_attempt", (record.get("attempt") or {}).get("generation"))
    return [t for t in _sections(record,"tasks") if isinstance(t,dict)
            and t.get("evaluation_generation",t.get("generation",generation)) == generation]


def _owner_faults(faults, generation, tasks):
    """Only explicit, unresolved owner requests belong in human action text."""
    current_ids={t.get("task_id") for t in tasks if t.get("task_id")}
    return [f for f in faults if f.get("owner_action_required") is True
            and f.get("state") in ("OPEN", "RECOVERING")
            and (f.get("evaluation_generation",f.get("generation"))==generation
                 or (f.get("evaluation_generation",f.get("generation")) is None and f.get("task_id") in current_ids))]


def _current_faults(faults, generation, tasks):
    ids={t.get("task_id") for t in tasks if t.get("task_id")}
    return [f for f in faults if f.get("evaluation_generation",f.get("generation"))==generation
            or (f.get("evaluation_generation",f.get("generation")) is None and f.get("task_id") in ids)]


def _summary(record, tasks, phase, focused, title):
    request=record.get("request") or {}
    goal=next((c.get("description") or c.get("text") for c in request.get("criteria",[])
               if isinstance(c,dict) and isinstance(c.get("description") or c.get("text"),str)),None)
    task_counts={}
    for task in tasks:
        kind=str(task.get("kind") or "").upper()
        if kind in ("CHECK","REVIEW"):
            task_counts.setdefault(kind, [0,0,0])
            state=task.get("state"); result=_result(task).get("decision")
            if result in ("PASS","FAIL"): task_counts[kind][0]+=1
            elif state in ("QUEUED","RUNNING","CLAIMED","DISPATCHING","CANCEL_REQUESTED"): task_counts[kind][1]+=1
            else: task_counts[kind][2]+=1
    progress=[]
    for kind,label in (("CHECK","checks"),("REVIEW","reviews")):
        if kind in task_counts:
            done,pending,other=task_counts[kind]
            if kind=="CHECK" and done:
                decisions=[_result(t).get("decision") for t in tasks if t.get("kind")=="CHECK"]
                if decisions and all(value=="PASS" for value in decisions):
                    progress.append("The automated check bundle passed.")
                elif "FAIL" in decisions:
                    progress.append("Automated validation failed; the coding agent needs to address the failed checks before resubmitting.")
                else:
                    progress.append("Automated check results are recorded.")
            elif kind=="REVIEW":
                total=done+pending+other
                progress.append(f"Reviews: {done} of {total} completed.")
    missing_review=any(f.get("code")=="MISSING_REVIEW_RESULT" and f.get("evaluation_generation",f.get("generation"))==(record.get("run") or {}).get("current_attempt") for f in _sections(record,"incidents") if isinstance(f,dict) for f in [f.get("incident",f)])
    blocker=("Reviews did not finish before the deadline; the coding agent needs to submit the work for validation again." if missing_review else
             "" if focused=="FAIL" and any(_result(t).get("decision")=="FAIL" for t in tasks if t.get("kind")=="CHECK") else
             "Validation stopped because of a validation-service problem." if focused=="ERROR" else
             "Validation found changes for the coding agent to address before another review." if focused=="FAIL" else
             "Reviews did not start before the validation deadline; the coding agent needs to submit the work for validation again." if phase=="Reviewing" and any(_result(t).get("decision")=="ERROR" for t in tasks) else
             "Work remains underway." if phase in ("Checking","Reviewing") else "")
    detail=" ".join(progress+[blocker]).strip()
    goal=_short(goal.strip(),180) if isinstance(goal,str) and goal.strip() else ""
    if goal and goal.casefold() in {str(title).casefold(),str((request.get("feature_label") or "")).casefold()}: goal=""
    paragraphs=([goal] if goal else [])+([detail] if detail else [])
    return paragraphs[:2]


def _status_text(phase, *, boundary, faults, pause, native_unknown, capacity_pending,
                 active, regression_state, focused, terminal_error=False):
    if terminal_error:
        return "Review stopped — service error" if phase == "Reviewing" else "Stopped — validation error"
    if boundary == "STOPPING": return "Stopping safely"
    if native_unknown: return "Native validation ownership is unknown"
    if pause: return "Waiting for a provider"
    if faults: return "Workflow needs attention"
    if phase == "Building": return "Changes needed after validation"
    if phase == "Queued": return "Waiting for validation"
    if phase == "Checking": return "Waiting for a validation slot" if capacity_pending else "Running checks"
    if phase == "Reviewing": return "Review in progress"
    if phase == "Your testing": return "Ready for your testing"
    if phase == "Accepted":
        if regression_state == "RUNNING": return "Follow-up checks running"
        if regression_state in ("FAIL", "ERROR"): return "Follow-up checks need attention"
        return "Accepted"
    return ""


def _dismiss_identity(run, acceptance, focused):
    identity={"run_id":run.get("run_id"), "generation":run.get("current_attempt"),
              "acceptance_id":acceptance.get("acceptance_id") if acceptance else None,
              "result_id":focused.get("result_id") if isinstance(focused,dict) else None,
              "result_digest":focused.get("result_digest") if isinstance(focused,dict) else None}
    return hashlib.sha256(json.dumps(identity,sort_keys=True,separators=(",", ":")).encode()).hexdigest()


def project(raw, *, worktree, connection=None, author_activity=None):
    """Build the public v1 board from a saved raw snapshot; unknown fields drop."""
    is_available = bool(raw.get("available"))
    columns = {p: [] for p in PHASES}
    faults, history = [], []
    system = {"native_occupancy": "unknown", "queue": "unknown", "ai_availability": "unknown"}
    captured = raw.get("captured_at_ms")
    conn = {"live": False, "mode": "saved", "health": "unknown", "saved_snapshot": "available" if is_available else "unavailable"}
    if connection and "health" in connection:
        conn["health"] = connection["health"]
    if is_available:
        all_records=list(raw.get("records",[]))+list(raw.get("history",[]))
        capacity=_native_capacity(raw,all_records)
        if capacity is None:
            visible_claims=[r for rec in all_records for r in _sections(rec,"resources")
                            if r.get("resource_key")=="native_validation" and r.get("state") in ("HELD","UNKNOWN")]
            occupied=bool(visible_claims)
        else:
            occupied=capacity["held"]+capacity["unknown"]>0
        pending_ids=_pending_native_task_ids(all_records,occupied)
        diagnostic_history_active=[]
        for item in raw.get("history",[]):
            if (item.get("run") or {}).get("run_kind")=="DIAGNOSTIC":
                attempt=item.get("attempt") or {}; result=attempt.get("result") or (item.get("terminal_result") or {}).get("result")
                if str(attempt.get("phase") or "").upper() not in ("DECIDED","CLOSED") and result not in ("PASS","FAIL","ERROR"):
                    diagnostic_history_active.append(item)
        diagnostic_history_ids={ (item.get("run") or {}).get("run_id") for item in diagnostic_history_active }
        for record in list(raw.get("records", []))+diagnostic_history_active:
            run, attempt = record.get("run") or {}, record.get("attempt") or {}
            if run.get("run_kind") == "DIAGNOSTIC":
                phase, focused, boundary = _phase(record)
                run_id = run.get("run_id")
                request = record.get("request") or {}
                label = _short("Diagnostic: " + str(request.get("feature_label") or request.get("title") or run.get("title") or "Workflow diagnostic"))
                terminal_diagnostic=(str(attempt.get("phase") or "").upper() in ("DECIDED","CLOSED") or focused in ("PASS","FAIL","ERROR"))
                if phase is None or terminal_diagnostic:
                    history.append({"id":run_id,"run_id":run_id,"label":label,"phase":"Diagnostic results",
                                    "details":{"title":request.get("title") or run.get("title"),"generation":run.get("current_attempt"),"focused_result":focused}})
                    continue
                phase = phase if phase in ("Queued", "Checking") else ("Checking" if boundary in ("CHECK", "STOPPING") else "Queued")
                tasks = _current_tasks(record)
                active = next((t for t in tasks if t.get("state") in ("RUNNING","CLAIMED","DISPATCHING","CANCEL_REQUESTED")), None)
                columns[phase].append({"id":run_id,"run_id":run_id,"generation":run.get("current_attempt"),
                    "sequence":run.get("sequence"),"input_digest":run.get("input_digest"),"label":label,"phase":phase,
                    "agent":_author_label(request.get("author"),run.get("agent")),"revision":None,
                    "step":str((active or {}).get("kind") or boundary or "DIAGNOSTIC"),"actor":"Diagnostic checks",
                    "capabilities":["cancel_active"] if active else [],"is_running":bool(active),"bindings":{"run_id":run_id,"generation":run.get("current_attempt")},
                    "next_action":"Complete diagnostic checks" if phase=="Checking" else "Wait for diagnostic checks",
                    "next_actor":"Diagnostic checks","badge":"Diagnostic","focused_result":focused,"owner_state":None,
                    "status_text":"Running diagnostic checks" if active else "Waiting for diagnostic checks","dismissible":False,
                    "regression_state":None,"details":{"title":request.get("title") or run.get("title"),"kind":"DIAGNOSTIC",
                    "tasks":[{"kind":t.get("kind"),"state":t.get("state"),"result":_result(t).get("decision")} for t in tasks]}})
                continue
            tasks = _current_tasks(record)
            record_open_faults, record_resolved_faults = _record_faults(record,run.get("run_id"),worktree)
            faults.extend(record_open_faults)
            history.extend({"id":f.get("id"),"run_id":f.get("run_id"),"phase":"Resolved incident","details":f} for f in record_resolved_faults)
            phase, focused, boundary = _phase(record)
            run_id = run.get("run_id")
            if phase is None:
                old_decisions=[_decision_body(d) for d in _sections(record,"owner_decisions")]
                old_decision=old_decisions[-1] if old_decisions else {}
                history.append({"id":run_id,"run_id":run_id,"label":_short(run.get("feature_id") or record.get("sprint")),
                                "phase":"Closed","details":{"title":run.get("title"),"generation":run.get("current_attempt"),"focused_result":focused,
                                "owner_decision":{k:old_decision[k] for k in ("decision_id","decision","evaluation_generation","input_digest","expected_sequence") if k in old_decision and isinstance(old_decision[k],(str,int,float,bool,type(None)))},
                                "incidents":_items(_sections(record,"incidents"),("seen_ms","resolved_ms")),"resources":_items(_sections(record,"resources"),("resource_key","state"))}})
                continue
            request = record.get("request") or {}
            title = request.get("feature_label") or request.get("title") or run.get("title") or "Feature validation"
            label = _short(request.get("feature_label") or request.get("title") or run.get("title") or "Feature validation")
            result_state = focused or "PENDING"
            owner_rows = []
            for row in _sections(record,"owner_decisions"):
                body=_decision_body(row)
                if body.get("evaluation_generation",body.get("generation"))==run.get("current_attempt") and body.get("input_digest")==run.get("input_digest"):
                    owner_rows.append(body)
            owner_rows.sort(key=lambda d:d.get("expected_sequence",-1))
            owner = str((owner_rows[-1].get("decision") if owner_rows else None) or
                        ("ACCEPT" if _phase(record)[0] == "Accepted" else "OPEN"))
            regressions = _sections(record,"regression_jobs")
            latest_regression = regressions[-1] if regressions else {}
            job = latest_regression.get("job") if isinstance(latest_regression.get("job"),dict) else {}
            if not regressions:
                regression_state="NOT_STARTED"
            elif job.get("status")=="DECIDED":
                regression_state=str(job.get("result") or "UNKNOWN")
            elif job.get("status") in ("QUEUED","RUNNING"):
                regression_state=job["status"]
            else:
                regression_state="UNKNOWN"
            current_tasks = [t for t in tasks if isinstance(t,dict)]
            active = next((t for t in current_tasks if t.get("state") in ("RUNNING","CLAIMED","DISPATCHING","CANCEL_REQUESTED")), None)
            terminal_error = boundary == "DECIDED" and focused == "ERROR"
            current_error = next((_result(t) for t in reversed(current_tasks)
                                  if _result(t).get("decision") == "ERROR"), {}) if terminal_error else {}
            error_cause = next((f for f in _safe_findings(current_error.get("findings", []))
                                if isinstance(f, str) or isinstance(f, dict)), None)
            if isinstance(error_cause, dict):
                error_cause = error_cause.get("summary") or error_cause.get("message") or error_cause.get("code")
            error_cause = _short(error_cause, 100) if error_cause else None
            kind = str((active or {}).get("kind") or _boundary_kind(current_tasks) or "").upper()
            owner_faults = _owner_faults(record_open_faults,run.get("current_attempt"),current_tasks)
            fault_records = _current_faults(record_open_faults,run.get("current_attempt"),current_tasks)
            findings = [f for t in current_tasks for f in _safe_findings(_result(t).get("findings",[]))]
            for decision in owner_rows:
                findings.extend(_safe_findings(decision.get("findings",[])))
            pauses = _sections(record,"dependency_pauses")
            pause = next((p for p in pauses if _paused(p) and "provider" in str(p.get("dependency_key","")).lower()),None)
            native_unknown=next((f for f in fault_records if f.get("kind")=="resource"),None)
            capacity_pending=(phase=="Checking" and boundary!="STOPPING" and not fault_records
                              and not pause and any(t.get("task_id") in pending_ids for t in current_tasks))
            badge = ("Validation error" if terminal_error else "Stopping" if boundary == "STOPPING" else
                     "Regression "+regression_state if phase=="Accepted" and regression_state in ("ERROR","FAIL") else
                     "UNKNOWN" if native_unknown else "Paused" if pause else
                     fault_records[0]["state"] if fault_records else None)
            acceptance = next((a for a in _sections(record,"acceptances") if isinstance(a,dict) and a.get("run_id")==run_id
                               and a.get("evaluation_generation",a.get("generation"))==run.get("current_attempt")
                               and a.get("input_digest")==run.get("input_digest")),None)
            terminal = record.get("terminal_result") or {}
            result_created_ms = _terminal_result_created_ms(record)
            current_pending=any(t.get("state") in ("QUEUED","RUNNING","CLAIMED","DISPATCHING","CANCEL_REQUESTED") for t in current_tasks)
            dismissible=(phase=="Accepted" and acceptance is not None and focused=="PASS" and not fault_records
                         and not current_pending and regression_state in ("NOT_STARTED","PASS"))
            capabilities = []
            if phase == "Your testing":
                capabilities = ["manual","accept","request_changes","owner_cancel"]
            if phase in ("Checking","Reviewing") and active:
                capabilities.append("cancel_active")
            if phase == "Reviewing" and pause and kind=="REVIEW":
                capabilities.append("retry_review")
            next_actor = {"Building":"Author","Queued":"Native capacity","Checking":"Native checks","Reviewing":"Reviewer","Your testing":"Owner","Accepted":"Regression"}[phase]
            author_label=_author_label(request.get("author"),run.get("agent"),run.get("author"))
            actual_revision=request.get("revision") or run.get("revision")
            revision=actual_revision if isinstance(actual_revision,(str,int,float)) else ("source:"+str(run.get("input_digest",""))[:12] if run.get("input_digest") else "generation:"+str(run.get("current_attempt")))
            card = {"id":run_id,"run_id":run_id,"generation":run.get("current_attempt"),"sequence":run.get("sequence"),
                    "input_digest":run.get("input_digest"),"label":label,"phase":phase,"agent":author_label,
                     "revision":revision,"step":"Native capacity pending" if capacity_pending else (kind or boundary),"actor":(active or {}).get("assignee") or next_actor,
                      "capabilities":capabilities,"is_running":bool(active),"bindings":{"run_id":run_id,"generation":run.get("current_attempt"),"input_digest":run.get("input_digest"),
                    "sequence":run.get("sequence"),"result_id":terminal.get("result_id"),"result_digest":terminal.get("result_digest"),
                    "review_task_id":pause.get("task_id") if pause and kind=="REVIEW" else None},
                      "next_action":("Repair the service or retry the existing run" if terminal_error else "Wait for native capacity" if capacity_pending else
                         {"Building":"Revise focused changes","Queued":"Wait for native capacity","Checking":"Complete focused checks","Reviewing":"Complete assigned review","Your testing":"Owner testing or decision","Accepted":"Monitor separate regression"}[phase]),
                     "next_actor":next_actor,"badge":_short(badge,40) if badge else None,"focused_result":focused,"owner_state":owner,
                     "status_text":_status_text(phase,boundary=boundary,faults=fault_records,pause=pause,native_unknown=native_unknown,
                          capacity_pending=capacity_pending,active=active,regression_state=regression_state,focused=focused,terminal_error=terminal_error),
                     "dismissible":dismissible,
                     **({"dismiss_key":_dismiss_identity(run,acceptance,terminal)} if dismissible else {}),
                      "regression_state":regression_state,"details":{"title":title,"summary":_summary(record,current_tasks,phase,focused,title),"owner_attention":[f["summary"] for f in owner_faults],"input_digest":run.get("input_digest"),"criteria":_criteria(request.get("criteria",[])),
                    "native_check_status":[{"state":t.get("state"),"result":_result(t).get("decision")} for t in current_tasks if t.get("kind")=="CHECK"],
                    "findings":findings,"tasks":[{"kind":t.get("kind"),"state":t.get("state"),"result":_result(t).get("decision")} for t in current_tasks],
                    "manual_steps":_strings(request.get("manual_steps",[])),"artifacts":_artifact_items(_sections(record,"artifacts")),
                    "deliveries":_delivery_items(_sections(record,"deliveries")),
                    "owner_decisions":[{k:v for k,v in row.items() if k in ("decision_id","decision","evaluation_generation","input_digest","created_ms")} for row in owner_rows],
                    "acceptances":_items(_sections(record,"acceptances"),("acceptance_id","decision_id","evaluation_generation","input_digest")),
                     "regression_jobs":_regression_items(regressions),
                      "events":_items(_sections(record,"events"),("kind","state","created_ms","summary")),"faults":fault_records,"boundary":boundary,
                      **({"current_error":error_cause} if error_cause else {})}}
            if phase in ("Your testing", "Accepted"):
                card["result_created_ms"] = result_created_ms
            columns[phase].append(card)
        for old in raw.get("history",[]):
            old_run = old.get("run",{})
            old_id = old_run.get("run_id")
            if old_run.get("run_kind") == "DIAGNOSTIC":
                if old_id in diagnostic_history_ids:
                    continue
                result = (old.get("terminal_result") or {}).get("result") or (old.get("attempt") or {}).get("result")
                history.append({"id":old_id,"run_id":old_id,"label":_short("Diagnostic: " + str(old_run.get("title") or old.get("sprint"))),
                                "phase":"Diagnostic results","details":{"title":old_run.get("title"),"generation":old_run.get("current_attempt"),"focused_result":result,"kind":"DIAGNOSTIC"}})
                continue
            old_faults, old_resolved = _record_faults(old,old_id,worktree)
            faults.extend(old_faults)
            history.extend({"id":f.get("id"),"run_id":f.get("run_id"),"phase":"Resolved incident","details":f} for f in old_resolved)
            history.append({"id":old_id,"run_id":old_id,"label":_short(old_run.get("feature_id") or old.get("sprint")),"phase":"Superseded",
                            "details":{"title":(old.get("request") or {}).get("title") or old_run.get("title"),"generation":old_run.get("current_attempt")}})
        system=_system_state(all_records,raw,worktree)
    # Unsubmitted coding activity is supplementary; any live submitted card
    # in that worktree remains the authoritative workflow representation.
    if isinstance(author_activity,list):
        active_author_phases={"Building","Queued","Checking","Reviewing"}
        for agent in author_activity:
            if not isinstance(agent,dict) or agent.get("worktree")!=worktree:
                continue
            if any(c.get("phase") in active_author_phases and c.get("details",{}).get("worktree",worktree)==worktree
                   for cards in columns.values() for c in cards):
                continue
            activity=agent.get("activity")
            phase="Idle" if activity in ("idle","unknown") else "Building"
            status="Activity unavailable" if activity=="unknown" else "Idle" if activity=="idle" else ("Waiting for your input" if activity=="waiting" else "Working")
            label=_short(agent.get("label") or "Coding agent")
            columns[phase].append({"id":"author-activity:"+str(agent.get("worktree")),"run_id":None,
                "generation":None,"sequence":None,"input_digest":None,"label":label,"phase":phase,
                "agent":label,"revision":None,"step":None,"actor":None,"capabilities":[],"bindings":{},
                "status_text":status,"dismissible":False,"next_action":None,"next_actor":None,"badge":None,
                "focused_result":None,"owner_state":None,"regression_state":None,
                "details":{"title":_short(agent.get("summary") or label,100),"session_id":agent.get("session_id") or "","activity_unknown":activity=="unknown"}})
    unscoped=_unscoped_counts(raw) if is_available else None
    deduped_faults=[]
    fault_keys=set()
    for fault in faults:
        key=(fault.get("kind"),fault.get("id")) if fault.get("id") else (fault.get("run_id"),fault.get("kind"),fault.get("state"),fault.get("summary"))
        if key not in fault_keys:
            fault_keys.add(key)
            deduped_faults.append(fault)
    result={"version":1,"available":is_available,"captured_at_ms":captured,"connection":conn,"worktree":worktree,
            "columns":columns,"history":history,"faults":deduped_faults,
            "current_owner_attention":[{"run_id":card.get("run_id"),"summary":summary}
                for cards in columns.values() for card in cards
                for summary in card.get("details",{}).get("owner_attention",[])],"system":system,
            **({"unavailable_reason":raw.get("reason","database_unavailable")} if not is_available else {})}
    if unscoped is not None:
        result["unscoped_incidents"]=unscoped
        if unscoped["total"]:
            result["faults"].append({"kind":"unscoped_admission","state":"UNKNOWN_SCOPE",
                "summary":f"{unscoped['total']} unresolved admission faults (unscoped)",
                "owner_action_required":False,"capabilities":[]})
    return result


def snapshot(database_path, worktree):
    return project(workflow_store.WorkflowStore.readonly_snapshot(database_path, worktree=worktree), worktree=worktree)
