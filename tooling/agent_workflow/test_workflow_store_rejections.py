import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, IdempotencyConflict, ProtocolError


class RejectionStorageTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "store.sqlite"
        self.store = WorkflowStore(self.path)
        fixture = Path(__file__).parents[2] / "docs/development-workflow-redesign/contracts/fixtures/positive.json"
        self.request = json.loads(fixture.read_text(encoding="utf-8"))[0]
        self.request["idempotency_key"] = "reject-key"
        self.response = {"contract_version": 1, "status": "REJECTED", "result": "ERROR",
                         "code": "INVALID", "incident_ref": "incident_reject",
                         "next_action": "WAIT_FOR_RECOVERY"}
        self.incident = {"contract_version": 1, "incident_id": "incident_reject", "code": "INVALID",
                         "message": "Request cannot be processed", "recovery_owner": "SYSTEM",
                         "state": "OPEN", "owner_action_required": False, "artifact_refs": []}
        self.run = {"contract_version": 1, "run_id": "run_should_not_exist", "sprint_id": self.request["sprint_id"],
                    "run_kind": "FEATURE_GATE", "input_digest": self.request["input_digest"],
                    "plan_digest": self.request["plan_digest"], "policy_digest": self.request["policy_digest"],
                    "author": self.request["author"], "current_attempt": 1, "sequence": 0}
        self.task = {"contract_version": 1, "task_id": "task_should_not_exist", "run_id": self.run["run_id"],
                     "evaluation_generation": 1, "kind": "CHECK", "dependency_ids": [],
                     "input_digest": self.request["input_digest"], "state": "QUEUED", "assignment_generation": 1,
                     "assigned_session_id": None, "deadline_ms": 1, "result": None}
        self.event = {"contract_version": 1, "event_id": "event_should_not_exist", "kind": "TASK_READY",
                      "run_id": self.run["run_id"], "evaluation_generation": 1, "task_id": self.task["task_id"],
                      "assignment_generation": 1, "input_digest": self.request["input_digest"]}

    def tearDown(self):
        self.store.close()
        self.tmp.cleanup()

    def counts(self):
        db = sqlite3.connect(self.path)
        try:
            return {table: db.execute(f"select count(*) from {table}").fetchone()[0]
                    for table in ("submissions", "runs", "tasks", "incidents", "events", "outbox")}
        finally:
            db.close()

    def test_error_rejection_persists_bound_incident_atomically_and_replays(self):
        first = self.store.submit(caller="author", request=self.request, run=self.run, first_task=self.task,
                                  outbox_event=self.event, rejected_response=self.response, incident=self.incident)
        reopened = WorkflowStore(self.path)
        self.assertEqual(reopened.get_submission("author", "reject-key"), first)
        self.assertEqual(reopened.get_incident("incident_reject")["incident"]["incident_id"], "incident_reject")
        self.assertEqual(reopened.submit(caller="author", request=self.request, run=self.run, first_task=self.task,
                                         outbox_event=self.event, rejected_response=dict(self.response, code="OTHER"),
                                         incident=self.incident), first)
        self.assertEqual(self.counts(), {"submissions": 1, "runs": 0, "tasks": 0, "incidents": 1, "events": 1, "outbox": 1})
        with self.assertRaises(IdempotencyConflict):
            reopened.submit(caller="author", request=dict(self.request, feature_label="changed"), run=self.run,
                            first_task=self.task, outbox_event=self.event, rejected_response=self.response, incident=self.incident)

    def test_error_missing_or_mismatched_incident_rolls_back(self):
        with self.assertRaises(ProtocolError):
            self.store.submit(caller="a", request=self.request, run=self.run, first_task=self.task,
                              outbox_event=self.event, rejected_response=self.response)
        wrong = dict(self.incident, incident_id="incident_wrong")
        with self.assertRaises(ProtocolError):
            self.store.record_rejection(caller="b", idempotency_key="malformed", raw_request={"idempotency_key": "malformed"},
                                        response=self.response, incident=wrong)
        self.assertEqual(self.counts(), {"submissions": 0, "runs": 0, "tasks": 0, "incidents": 0, "events": 0, "outbox": 0})

    def test_expected_fail_and_namespaced_malformed_rejections_need_no_incident(self):
        response = {"contract_version": 1, "status": "REJECTED", "result": "FAIL", "code": "INVALID",
                    "findings": [{"criterion_id": "input", "category": "IMPLEMENTATION", "message": "Bad request",
                                  "location": "request", "evidence_refs": ["request"], "required_change": "Fix input."}],
                    "next_action": "FIX_FINDINGS"}
        for caller in ("one", "two"):
            self.store.record_rejection(caller=caller, idempotency_key="same", raw_request={"idempotency_key": "same", "bad": True}, response=response)
        self.assertEqual(self.counts(), {"submissions": 2, "runs": 0, "tasks": 0, "incidents": 0, "events": 0, "outbox": 0})


if __name__ == "__main__":
    unittest.main()
