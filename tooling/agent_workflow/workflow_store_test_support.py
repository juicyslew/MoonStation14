"""Small shared WR01-positive admission fixture builder for store tests."""
import json
from pathlib import Path


def admission_records(prefix, *, idempotency_key=None, deadline_ms=1):
    fixture = Path(__file__).parents[2] / "docs/development-workflow-redesign/contracts/fixtures/positive.json"
    request = json.loads(fixture.read_text(encoding="utf-8"))[0]
    request["idempotency_key"] = idempotency_key or prefix
    run_id, task_id, event_id = f"run_{prefix}", f"task_{prefix}", f"event_{prefix}"
    run = {"contract_version": 1, "run_id": run_id, "sprint_id": request["sprint_id"],
           "run_kind": "FEATURE_GATE", "input_digest": request["input_digest"],
           "plan_digest": request["plan_digest"], "policy_digest": request["policy_digest"],
           "author": request["author"], "current_attempt": 1, "sequence": 0}
    task = {"contract_version": 1, "task_id": task_id, "run_id": run_id,
            "evaluation_generation": 1, "kind": "CHECK", "dependency_ids": [],
            "input_digest": request["input_digest"], "state": "QUEUED",
            "assignment_generation": 1, "assigned_session_id": None,
            "deadline_ms": deadline_ms, "result": None}
    event = {"contract_version": 1, "event_id": event_id, "kind": "TASK_READY",
             "run_id": run_id, "evaluation_generation": 1, "task_id": task_id,
             "assignment_generation": 1, "input_digest": request["input_digest"]}
    return request, run, task, event


def admit(store, prefix, *, idempotency_key=None, caller="author-caller", logical_task_key="initial", deadline_ms=1):
    request, run, task, event = admission_records(prefix, idempotency_key=idempotency_key, deadline_ms=deadline_ms)
    store.submit(caller=caller, request=request, run=run, first_task=task,
                 outbox_event=event, logical_task_key=logical_task_key)
    return request, run, task, event
