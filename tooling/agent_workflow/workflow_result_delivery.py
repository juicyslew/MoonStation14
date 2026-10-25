"""Inactive adapter for durable result feedback to the bound coding author."""
from __future__ import annotations

import hashlib
import json
import urllib.parse

try:
    import gate_dispatch
    import workflow_coordinator
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_dispatch, workflow_coordinator


class AuthorResultDelivery:
    def __init__(self, store, *, server, request=None):
        self.store = store
        self.server = workflow_coordinator._loopback_server(server)
        self.request = request or gate_dispatch._request

    def observe(self, event_id, payload):
        if self._is_projection_hint_applied(event_id, payload):
            return "APPLIED"
        delivery_id = self._delivery_id(event_id, payload)
        delivery = self.store.get_delivery(delivery_id) if delivery_id else None
        if delivery is None or delivery["event_id"] != event_id or delivery["payload"] != payload:
            return "UNKNOWN"
        if delivery["target_kind"] != "AUTHOR":
            return "UNKNOWN"
        if delivery["protocol_ack"] is not None or delivery["state"] == "ACKNOWLEDGED":
            return "APPLIED"
        if delivery["transport_receipt"] is not None:
            return "APPLIED"
        # A prior lease may have reached the API; never blindly duplicate it.
        return "UNKNOWN" if delivery["attempts"] else "ABSENT"

    def dispatch(self, event_id, payload):
        if self._is_projection_hint_applied(event_id, payload):
            return "PROJECTION_ONLY"
        if payload.get("kind") == "REGRESSION_DECIDED" and payload.get("result") == "PASS":
            raise ValueError("regression PASS projection differs from saved finality")
        delivery_id = self._delivery_id(event_id, payload)
        if not isinstance(delivery_id, str):
            raise ValueError("result wake has no delivery_id")
        saved = self.store.get_delivery(delivery_id)
        if (saved is None or saved["event_id"] != event_id or saved["payload"] != payload
                or saved["target_kind"] != "AUTHOR"):
            raise ValueError("result delivery binding differs from immutable saved event")
        if saved["state"] == "ACKNOWLEDGED":
            return "APPLIED"
        if saved["transport_receipt"] is not None:
            return "APPLIED"
        claim = self.store.claim_delivery(delivery_id=delivery_id)
        if claim is None:
            return "PENDING"
        if claim["target_kind"] != "AUTHOR" or claim["recipient"].get("role") != "author":
            raise ValueError("delivery is not bound to an author")
        sid = claim["recipient"].get("session_id")
        try:
            status, session = self.request(self.server, "GET", "/api/session/" + urllib.parse.quote(sid))
            if status < 200 or status >= 300:
                raise RuntimeError("bound author session lookup returned HTTP " + str(status))
            session = session.get("data", session) if isinstance(session, dict) else None
            recipient = claim["recipient"]
            directory = session.get("directory", session.get("location", {}).get("directory")) if isinstance(session, dict) else None
            if (not isinstance(session, dict) or session.get("id") != sid or
                    not isinstance(directory, str) or not directory.strip() or
                    directory != recipient.get("worktree") or
                    not isinstance(session.get("agent"), str) or not session["agent"].strip() or
                    session["agent"] == "integration-lead"):
                raise RuntimeError("bound author session unavailable")
        except Exception as exc:
            self._incident(claim, "RESULT_DELIVERY_SESSION_UNAVAILABLE", str(exc))
            return "WAIT_FOR_RECOVERY"

        # Deterministic message identity and text: retries cannot invent new feedback.
        text = _feedback_text(payload)
        message_id = "msg_" + hashlib.sha256((delivery_id + "\0" + claim["payload_digest"]).encode()).hexdigest()[:32]
        metadata = {k: payload[k] for k in ("event_id", "run_id", "evaluation_generation", "input_digest", "result_id", "result_digest")}
        metadata.update({"result_available": True, "body_digest": hashlib.sha256(text.encode("utf-8")).hexdigest()})
        body = {"id": message_id, "text": text, "delivery": "queue", "resume": True, "metadata": metadata}
        try:
            response = self.request(self.server, "POST", "/api/session/" + urllib.parse.quote(sid) + "/prompt", body)
            if response[0] < 200 or response[0] >= 300:
                raise RuntimeError("author prompt returned HTTP " + str(response[0]))
            gate_dispatch._acknowledged_prompt(response[1], sid, body, metadata)
            receipt = {"message_id": delivery_id, "event_id": event_id, "recipient": claim["recipient"],
                       "payload_digest": claim["payload_digest"], "accepted": True}
            self.store.record_delivery_transport(delivery_id, claim_generation=claim["claim_generation"],
                                                lease_token=claim["lease_token"], receipt=receipt)
            return "TRANSPORT_ACCEPTED"
        except Exception as exc:
            self._incident(claim, "RESULT_DELIVERY_UNKNOWN", str(exc))
            return "UNKNOWN"

    def _is_projection_hint_applied(self, event_id, payload):
        saved = self.store.get_event(event_id)
        if not saved or saved["payload"] != payload:
            return False
        if payload.get("kind") == "REGRESSION_DECIDED" and payload.get("result") == "PASS":
            job = self.store.get_regression_job(payload.get("regression_id"))
            run_id = payload.get("run_id")
            run_result = self.store.get_run(run_id) if isinstance(run_id, str) else None
            run = (run_result or {}).get("run", {})
            attempt = (run_result or {}).get("attempt", {})
            return bool(job and job.get("result") == "PASS" and job.get("status") == "DECIDED"
                        and job.get("run_id") == run_id
                        and job.get("acceptance_id") == payload.get("acceptance_id")
                        and job.get("source_input_digest") == payload.get("input_digest")
                        and run.get("current_attempt") == payload.get("evaluation_generation")
                        and attempt.get("phase") == "DECIDED" and attempt.get("result") == "PASS")
        if payload.get("kind") != "OWNER_FEEDBACK" or payload.get("next_action") != "REGRESSION_QUEUED":
            return False
        run_id = payload.get("run_id")
        decisions = self.store.get_owner_decisions(run_id) if isinstance(run_id, str) else []
        return any(d.get("decision_id") == payload.get("decision_id") and d.get("acceptance_id")
                   and (acceptance := self.store.get_acceptance(d["acceptance_id"]))
                   and acceptance.get("decision_id") == payload.get("decision_id")
                   and acceptance.get("run_id") == run_id
                   and acceptance.get("evaluation_generation") == payload.get("evaluation_generation")
                   and acceptance.get("input_digest") == payload.get("input_digest")
                   for d in decisions)
    def _delivery_id(self, event_id, payload):
        if isinstance(payload, dict) and isinstance(payload.get("delivery_id"), str):
            return payload["delivery_id"]
        run_id = payload.get("run_id") if isinstance(payload, dict) else None
        run_result = self.store.get_run(run_id) if isinstance(run_id, str) else None
        run = (run_result or {}).get("run", {})
        author = run.get("author")
        if not isinstance(author, dict):
            return None
        recipient = {"session_id": author["session_id"], "worktree": author["worktree"], "role": "author"}
        try:
            import workflow_store
        except ModuleNotFoundError:
            from tooling.agent_workflow import workflow_store
        return "delivery_" + hashlib.sha256((event_id + "AUTHOR" + workflow_store.canonical_json(recipient)).encode()).hexdigest()[:24]

    def _incident(self, claim, code, evidence):
        event = self.store.get_event(claim["event_id"])
        payload = event["payload"] if event else {}
        incident_id = "incident_" + hashlib.sha256((claim["delivery_id"] + "\0" + code).encode()).hexdigest()[:24]
        incident = {"contract_version": 1, "incident_id": incident_id, "code": code,
                    "message": ("Author result delivery requires recovery: " + evidence)[:1000],
                    "recovery_owner": "SYSTEM", "state": "OPEN", "owner_action_required": False,
                    "artifact_refs": [], "run_id": payload.get("run_id"),
                    "evaluation_generation": payload.get("evaluation_generation")}
        self.store.record_delivery_incident(incident_id=incident_id, delivery_id=claim["delivery_id"], incident=incident)


def _feedback_text(payload):
    if payload.get("control_kind") == "OWNER_DECISION":
        lines=["Owner requested changes to the accepted feature review snapshot.",
               f"Owner decision event {payload['event_id']} (run {payload['run_id']}, generation {payload['evaluation_generation']})."]
        lines.extend("Required change: "+json.dumps(item,sort_keys=True,separators=(",",":"),ensure_ascii=False) for item in payload.get("findings",[]))
        lines.append("Next action: "+payload["next_action"]+".")
    elif payload.get("control_kind") == "POST_ACCEPTANCE_REGRESSION":
        lines=[f"Post-acceptance regression {payload['result']}; feature acceptance remains unchanged.",
               "Findings: "+json.dumps(payload.get("findings",[]),sort_keys=True,separators=(",",":"),ensure_ascii=False),
               "Next action: "+payload["next_action"]+"."]
    else:
        return _feature_feedback_text(payload)
    ack = {"contract_version":1,"command_id":"ack_"+hashlib.sha256(payload["event_id"].encode()).hexdigest()[:24],
           "event_id":payload["event_id"],"run_id":payload["run_id"],
           "evaluation_generation":payload["evaluation_generation"],"result_id":payload["result_id"],
           "result_digest":payload["result_digest"]}
    lines.append("Acknowledge using acknowledge_result: "+json.dumps(ack,sort_keys=True,separators=(",",":")))
    return "\n".join(lines)


def _feature_feedback_text(payload):
    if payload.get('run_kind') == 'DIAGNOSTIC':
        lines = [f"Diagnostic result: {payload['result']} (run {payload['run_id']}, generation {payload['evaluation_generation']}).",
                 "This reports only selected diagnostic checks; it is not feature validation, manual testing, or acceptance."]
        if payload.get('result') == 'ERROR':
            lines.append("Diagnostic error; inspect workflow status and logs for details. If the request or configuration needs correction, correct it and register a fresh diagnostic scope/submission after cleanup settles; no owner approval is required. This does not complete or replace the feature.")
        elif payload.get('result') == 'FAIL':
            lines.append("Diagnostic checks failed; inspect the coded check findings and workflow status/logs.")
        for finding in payload.get('findings', []):
            lines.append("Finding: " + json.dumps(finding, sort_keys=True, separators=(",", ":"), ensure_ascii=False))
        ack = {"contract_version": 1, "command_id": "ack_" + hashlib.sha256(payload["event_id"].encode()).hexdigest()[:24],
               "event_id": payload["event_id"], "run_id": payload["run_id"],
               "evaluation_generation": payload["evaluation_generation"], "result_id": payload["result_id"],
               "result_digest": payload["result_digest"]}
        lines.append("Next action: " + payload["next_action"] + ". Acknowledge using acknowledge_result with arguments: " +
                     json.dumps(ack, sort_keys=True, separators=(",", ":")))
        return "\n".join(lines)
    findings = payload.get("findings", [])
    lines = [f"Workflow result: {payload['result']} (run {payload['run_id']}, generation {payload['evaluation_generation']})."]
    if payload.get("result") == "ERROR":
        error_code = payload.get("error_code")
        error_message = payload.get("error_message")
        if error_code or error_message:
            lines.append(f"Error ({error_code or 'unknown'}): {error_message or 'diagnostic unavailable'}")
        else:
            lines.append("Error diagnostic unavailable; check workflow status for details.")
        incident_ref = payload.get("incident_ref")
        if incident_ref:
            lines.append("Recovery incident: " + str(incident_ref))
    for finding in findings:
        lines.append("Finding: " + json.dumps(finding, sort_keys=True, separators=(",", ":"), ensure_ascii=False))
    for followup in payload.get("owner_followups", []):
        lines.append("Owner testing follow-up (does not block technical readiness): " +
                     json.dumps(followup, sort_keys=True, separators=(",", ":"), ensure_ascii=False))
    if payload.get("result") == "PASS":
        lines.append("PASS means ready for owner testing; it is not owner acceptance.")
    ack = {"contract_version": 1, "command_id": "ack_" + hashlib.sha256(payload["event_id"].encode()).hexdigest()[:24],
           "event_id": payload["event_id"], "run_id": payload["run_id"],
           "evaluation_generation": payload["evaluation_generation"], "result_id": payload["result_id"],
           "result_digest": payload["result_digest"]}
    lines.append("Next action: " + payload["next_action"] + ". Acknowledge using the workflow SDK tool acknowledge_result with arguments: " +
                 json.dumps(ack, sort_keys=True, separators=(",", ":")))
    return "\n".join(lines)
