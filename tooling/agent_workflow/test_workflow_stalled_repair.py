import unittest
import hashlib
import json
from pathlib import Path
from unittest import mock

import deploy_workflow as deploy
import admission
from workflow_coordinator import ExplicitCoordinator

def canonical(value): return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
def digest(value): return hashlib.sha256(canonical(value).encode()).hexdigest()


def fixture():
    failed = {"id": "failed", "run_id": "run1", "evaluation_generation": 2, "assignment_generation": 1,
              "input_digest": "in-f", "result_digest": "out-f",
              "task_json": {"run_id": "run1", "kind": "CHECK", "state": "COMPLETED", "evaluation_generation": 2,
                            "assignment_generation": 1, "input_digest": "in-f",
                            "result": {"decision": "ERROR"}}}
    ready = {"id": "next", "run_id": "run2", "evaluation_generation": 1, "assignment_generation": 1,
             "input_digest": "in-n", "result_digest": None,
             "task_json": {"run_id": "run2", "kind": "CHECK", "state": "QUEUED", "evaluation_generation": 1,
                           "assignment_generation": 1, "input_digest": "in-n"}}
    def event(eid, kind, task, run, gen):
        payload = {"kind": kind, "event_id": eid, "task_id": task["id"], "run_id": run,
                   "evaluation_generation": gen, "assignment_generation": task["assignment_generation"],
                   "input_digest": task["input_digest"]}
        if kind == "TASK_COMPLETED": payload["result_digest"] = task["result_digest"]
        return {"id": eid, "run_id": run, "generation": gen, "kind": kind, "payload": canonical(payload),
                "payload_digest": digest(payload)}
    return {"tasks": [failed, ready], "runs": [{"id": "run1", "run_json": '{"current_attempt":2}'},
                                                 {"id": "run2", "run_json": '{"current_attempt":1}'}],
            "attempts": [{"run_id": "run1", "generation": 2, "attempt_json": {"phase": "CHECKING"}},
                         {"run_id": "run2", "generation": 1, "attempt_json": {"phase": "QUEUED"}}],
            "resources": [{"state": "RELEASED"}], "native_scopes": [{"state": "CLEANED"}],
            "native_claims": [{"process_identity": '{"pid":7,"created_ms":100}'}],
            "native_check_records": [{"task_id": "historic-check"}],
            "incidents": [{"incident_json": canonical({"task_id": "failed", "run_id": "run1",
                "evaluation_generation": 2, "code": "NATIVE_EXECUTION_ERROR", "message": "frozen source manifest integrity mismatch"})}],
            "events": [event("e1", "TASK_COMPLETED", failed, "run1", 2), event("e2", "TASK_READY", ready, "run2", 1)],
            "outbox": [{"event_id": "e1", "state": "CLAIMED"}, {"event_id": "e2", "state": "CLAIMED"}],
            "deliveries": []}


class StalledRepairPredicateTests(unittest.TestCase):
    def test_actual_maintenance_hold_pauses_only_repair_and_fails_closed(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            receipt = admission.begin_hold(tmp, owner='workflow-stalled-repair', control_id='repair-1', note='repair')
            held = admission.maintenance(tmp)
            self.assertEqual(held['owner'], 'workflow-stalled-repair')
            self.assertNotIn('token', held)
            self.assertNotIn('_operation_token', held)
            coordinator = object.__new__(ExplicitCoordinator)
            coordinator.admission_root = tmp
            self.assertTrue(coordinator._maintenance_paused())
            admission.resume_hold(tmp, receipt)
            self.assertFalse(coordinator._maintenance_paused())

            receipt = admission.begin_hold(tmp, owner='workflow-update', control_id='update-1', note='update')
            self.assertFalse(coordinator._maintenance_paused())
            admission.resume_hold(tmp, receipt)
            receipt = admission.begin_hold(tmp, owner='workflow-runtime-update', control_id='update-2', note='update')
            self.assertTrue(coordinator._maintenance_paused())
            admission.resume_hold(tmp, receipt)
            self.assertFalse(coordinator._maintenance_paused())
            for malformed in ('{"owner":"workflow-stalled-repair"}', '{"owner": ""}', '{'):
                (Path(tmp) / 'gate-maintenance.json').write_text(malformed, encoding='utf-8')
                self.assertTrue(coordinator._maintenance_paused())

    def check(self, snap):
        with mock.patch.dict("sys.modules", {"workflow_store": type("StoreDigest", (), {"digest": staticmethod(digest)})()}):
            deploy.assert_stalled_native_repair(snap, "run1", identity_state=lambda _: "dead")

    def test_runtime_shaped_case_and_default_quiescence(self):
        value = fixture()
        self.check(value)
        with self.assertRaises(ValueError): deploy.assert_quiescent(value)

    def test_rejects_wrong_incident_unsettled_task_and_phase(self):
        for change in (lambda s: s["incidents"][0].update(incident_json='{}'),
                       lambda s: s["tasks"][1]["task_json"].update(state="RUNNING"),
                       lambda s: s["attempts"][0]["attempt_json"].update(phase="REVIEWING")):
            value = fixture(); change(value)
            with self.assertRaises(ValueError): self.check(value)

    def test_rejects_live_unknown_identity_and_unbound_outbox(self):
        value = fixture()
        for state in ("alive", "unknown"):
            with mock.patch.dict("sys.modules", {"workflow_store": type("StoreDigest", (), {"digest": staticmethod(digest)})()}):
                with self.assertRaises(ValueError): deploy.assert_stalled_native_repair(value, "run1", identity_state=lambda _, s=state: s)
        value = fixture(); value["events"][1]["payload_digest"] = "bad"
        with self.assertRaises(ValueError): self.check(value)

    def test_snapshot_stability_allows_only_outbox_lease_bookkeeping(self):
        before = fixture()
        after = json.loads(json.dumps(before))
        after["outbox"][0].update(state="PENDING", claim_generation=3, lease_until_ms=0, attempts=4)
        self.assertTrue(deploy.assert_stalled_repair_snapshot_stable(before, after))

        mutations = (
            lambda s: s["tasks"][0]["task_json"]["result"].update(decision="PASS"),
            lambda s: s["events"][0].update(payload_digest="changed"),
            lambda s: s["outbox"].append(dict(s["outbox"][0])),
            lambda s: s["native_claims"][0].update(process_identity="other"),
        )
        for mutate in mutations:
            changed = json.loads(json.dumps(before)); mutate(changed)
            with self.subTest(mutation=mutate), self.assertRaises(ValueError):
                deploy.assert_stalled_repair_snapshot_stable(before, changed)

    def test_snapshot_stability_preserves_historical_acknowledgements(self):
        before = fixture()
        historic = {"event_id": "historic", "state": "ACKNOWLEDGED", "claim_generation": 2,
                    "lease_until_ms": 123, "attempts": 5, "payload_digest": "historic-digest"}
        before["outbox"].append(historic)

        after = json.loads(json.dumps(before))
        after["outbox"][0].update(state="PENDING", claim_generation=3, lease_until_ms=0, attempts=4)
        self.assertTrue(deploy.assert_stalled_repair_snapshot_stable(before, after))

        unchanged = json.loads(json.dumps(before))
        self.assertFalse(deploy.assert_stalled_repair_snapshot_stable(before, unchanged))

        for key, value in (("attempts", 6), ("state", "PENDING")):
            changed = json.loads(json.dumps(before))
            changed["outbox"][-1][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                deploy.assert_stalled_repair_snapshot_stable(before, changed)

        acknowledged_pending = json.loads(json.dumps(before))
        acknowledged_pending["outbox"][0]["state"] = "ACKNOWLEDGED"
        with self.assertRaises(ValueError):
            deploy.assert_stalled_repair_snapshot_stable(before, acknowledged_pending)


if __name__ == "__main__": unittest.main()
