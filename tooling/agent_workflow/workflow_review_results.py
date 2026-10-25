"""Typed review callbacks and deterministic, decision-only aggregation."""
import time

try:
    import workflow_store
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_store


_CATEGORIES = {"IMPLEMENTATION", "PARITY_COMPOSITION", "COVERAGE", "FIDELITY"}


class ReviewResults:
    def __init__(self, store, planner):
        self.store, self.planner = store, planner

    def _invalid(self, payload, field, message, recovery=None):
        return {"accepted":False,"code":"INVALID_REVIEW_RESULT","field":field,"message":message,
                "task_id":payload.get("task_id") if isinstance(payload,dict) else None,"next_action":"CORRECT_RESULT",
                "recovery":recovery or 'Correct the indicated field to match the version-1 callback contract, then retry this result.'}

    def _store_failure(self, payload, exc):
        task_id=payload.get('task_id') if isinstance(payload,dict) else None
        if isinstance(exc, workflow_store.StaleCommand):
            return {"accepted":False,"code":"STALE_REVIEW_TASK","field":"task_id",
                    "message":"This callback does not match the current task assignment.","task_id":task_id,
                    "next_action":"REFRESH_TASK_STATUS","recovery":"Refresh task status and claim/submit only the current assignment generation; do not modify the old task."}
        if isinstance(exc, workflow_store.IdempotencyConflict):
            return {"accepted":False,"code":"REVIEW_RESULT_CONFLICT","field":"task_id",
                    "message":"An immutable task result conflicts with this callback.","task_id":task_id,
                    "next_action":"CHECK_TASK_STATUS","recovery":"Check current task status; only replay the identical result under the same task and assignment. Do not replace an immutable result."}
        if isinstance(exc, workflow_store.ProtocolError):
            return {"accepted":False,"code":"REVIEW_TASK_STATE","field":getattr(exc,'field',None) or 'task_id',
                    "message":"Task state or assignment does not accept this callback.","task_id":task_id,
                    "next_action":"REFRESH_TASK_STATUS","recovery":"Refresh task status and use only the current authorized assignment; do not spoof another session or generation."}
        if isinstance(exc, workflow_store.StoreError):
            return {"accepted":False,"code":"REVIEW_SYSTEM_ERROR","field":"task_id",
                    "message":"Review storage is unavailable; callback outcome may be uncertain.","task_id":task_id,
                    "next_action":"WAIT_FOR_RECOVERY","recovery":"Retain the exact callback and identifiers; after recovery retry it unchanged to resolve the uncertain write."}
        raise exc

    def _validate(self, payload, schema):
        try:
            workflow_store.validate(payload, schema)
        except workflow_store.ProtocolError as exc:
            field=getattr(exc,'field',None) or 'payload'
            recovery=getattr(exc,'recovery',None)
            return self._invalid(payload,field,'Result does not match the required callback contract.',recovery)
        if schema == "review_result":
            for finding in payload["findings"]:
                if finding.get("category") not in _CATEGORIES:
                    return self._invalid(payload,"findings.category","Use one allowed category: IMPLEMENTATION, PARITY_COMPOSITION, COVERAGE, or FIDELITY.")
                if payload["decision"] == "FAIL" and not all(finding.get(k) for k in ("required_change","location","evidence_refs")):
                    return self._invalid(payload,"findings","FAIL requires a concrete change, location and evidence reference")
        return None

    def claim(self, payload, *, session_id):
        bad=self._validate(payload,"task_claim")
        if bad: return bad
        if payload["session_id"] != session_id:
            return self._invalid(payload,"session_id","Claim session must match the bound caller")
        try:
            disposition=self.store.claim_task(task_id=payload["task_id"],run_id=payload["run_id"],evaluation_generation=payload["evaluation_generation"],assignment_generation=payload["assignment_generation"],input_digest=payload["input_digest"],session_id=session_id)
            return {"accepted":True,**disposition}
        except workflow_store.StoreError as exc: return self._store_failure(payload,exc)

    def complete(self, payload, *, session_id):
        bad=self._validate(payload,"review_result")
        if bad: return bad
        try: return {"accepted":True,**self.store.complete_task(payload,session_id=session_id)}
        except workflow_store.StoreError as exc: return self._store_failure(payload,exc)

    def error(self, payload, *, session_id):
        bad=self._validate(payload,"task_error")
        if bad: return bad
        try: return {"accepted":True,**self.store.report_task_error(payload,session_id=session_id)}
        except workflow_store.StoreError as exc: return self._store_failure(payload,exc)

    def aggregate(self, run_id, now_ms=None):
        now_ms=int(time.time()*1000) if now_ms is None else now_ms
        plan=self.planner.plan_reviews(run_id)
        saved=self.store.get_run(run_id)
        if saved is None: raise workflow_store.ProtocolError("unknown run")
        attempt=saved["attempt"]
        tasks=self.store.list_tasks(run_id,attempt["generation"])
        checks=[t for t in tasks if t["kind"]=="CHECK"]
        reviews=[t for t in tasks if t["kind"]=="REVIEW"]
        if not reviews and plan: reviews=list(plan)
        if any((t.get("result") or {}).get("decision")=="ERROR" for t in checks+reviews):
            failed=next(t for t in checks+reviews if (t.get("result") or {}).get("decision")=="ERROR")
            incident=self.store.get_task_incident(failed['task_id'])
            error=({"code":incident['code'],"message":incident['message'],"task_id":failed['task_id'],"artifact_refs":incident['artifact_refs']}
                   if incident else {"code":"TASK_ERROR","message":"A required task reported an infrastructure error","task_id":failed['task_id']})
            return {"decision":"ERROR","waiting_task_ids":[],"findings":[],"error":error,"incident":incident}
        if any((t.get("result") or {}).get("decision")=="FAIL" for t in checks):
            failed=[t for t in checks if (t.get("result") or {}).get("decision")=="FAIL"]
            return {"decision":"FAIL","waiting_task_ids":[],"findings":[f for t in failed for f in t["result"].get("findings",[])]}
        waiting=[t["task_id"] for t in checks if t["state"]!="COMPLETED" or (t.get("result") or {}).get("decision")!="PASS"]
        if waiting: return {"decision":None,"waiting_task_ids":waiting,"findings":[]}
        waiting=[t["task_id"] for t in reviews if t["state"]!="COMPLETED"]
        if waiting:
            try:
                import workflow_dependency
            except ModuleNotFoundError:
                from tooling.agent_workflow import workflow_dependency
            deadline=min((t["deadline_ms"] for t in reviews if t["task_id"] in waiting),default=None)
            waiting_tasks=[t for t in reviews if t["task_id"] in waiting]
            paused_tasks=[t for t in reviews if t["task_id"] in waiting]
            due=deadline is not None and any(workflow_dependency.task_deadline_due(
                t, now_ms, paused_ms=self.store.task_paused_duration_ms(t["task_id"], now_ms),
                paused=self.store.task_is_dependency_paused(t["task_id"])) for t in paused_tasks)
            if due:
                task=next(t for t in reviews if t['task_id'] in waiting)
                return {"decision":"ERROR","waiting_task_ids":waiting,"findings":[],"error":{"code":"MISSING_REVIEW_RESULT","message":"review assignment deadline elapsed without an accepted result","task_id":task['task_id'],"session_id":task['assigned_session_id'],"artifact_refs":[]}}
            return {"decision":None,"waiting_task_ids":waiting,"findings":[]}
        failures=[f for t in reviews if (t.get("result") or {}).get("decision")=="FAIL" for f in t["result"].get("findings",[])]
        return {"decision":"FAIL" if failures else "PASS","waiting_task_ids":[],"findings":failures}
