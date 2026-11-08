import hashlib
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

import workflow_board
from workflow_store import WorkflowStore


class BoardSnapshotTests(unittest.TestCase):
    def test_old_owner_incident_does_not_surface_but_current_explicit_action_does(self):
        raw={"available":True,"records":[{"worktree":"/safe","request":{"feature_label":"Gravity"},
            "run":{"run_id":"r","current_attempt":4,"input_digest":"d","title":"Gravity"},
            "attempt":{"phase":"REVIEWING","generation":4},"tasks":[{"task_id":"review4","kind":"REVIEW","state":"QUEUED","evaluation_generation":4}],
            "incidents":[{"incident":{"incident_id":"old","run_id":"r","evaluation_generation":2,"state":"OPEN","owner_action_required":True,"summary":"old action"}},
                          {"incident":{"incident_id":"current","run_id":"r","evaluation_generation":4,"state":"OPEN","owner_action_required":True,"recovery_owner":"OWNER","summary":"Please review the saved result"}}]}]}
        projected=workflow_board.project(raw,worktree="/safe")
        card=projected["columns"]["Reviewing"][0]
        self.assertEqual(card["details"]["owner_attention"],["Please review the saved result"])
        self.assertEqual(len(projected["current_owner_attention"]),1)
        self.assertEqual(projected["current_owner_attention"][0]["summary"],"Please review the saved result")
        self.assertEqual(card["status_text"],"Workflow needs attention")
        self.assertIn("Reviews: 0 of 1 completed."," ".join(card["details"]["summary"]))

    def test_terminal_review_timeout_and_failed_check_have_responsible_actor(self):
        base={"worktree":"/safe","request":{"feature_label":"Gravity"},"run":{"run_id":"r","current_attempt":3,"title":"Gravity"},
              "attempt":{"phase":"DECIDED","generation":3,"result":"ERROR"},
              "tasks":[{"task_id":"check","kind":"CHECK","state":"COMPLETED","evaluation_generation":3,"result":{"decision":"PASS"}},
                       *[{"task_id":f"rev{i}","kind":"REVIEW","state":"CANCELLED","evaluation_generation":3} for i in range(3)]],
              "incidents":[{"incident":{"incident_id":"timeout","run_id":"r","evaluation_generation":3,"code":"MISSING_REVIEW_RESULT","state":"OPEN"}}]}
        card=workflow_board.project({"available":True,"records":[base]},worktree="/safe")["columns"]["Reviewing"][0]
        summary=" ".join(card["details"]["summary"])
        self.assertIn("Reviews: 0 of 3 completed.",summary)
        self.assertIn("deadline",summary)
        self.assertIn("coding agent needs to submit",summary)
        self.assertLessEqual(len(summary),500)

        base["attempt"]={"phase":"DECIDED","generation":3,"result":"FAIL"}
        base["tasks"][0]["result"]={"decision":"FAIL"}
        base["incidents"]=[]
        card=workflow_board.project({"available":True,"records":[base]},worktree="/safe")["columns"]["Building"][0]
        summary=" ".join(card["details"]["summary"])
        self.assertIn("coding agent needs to address the failed checks",summary)
        self.assertNotIn("1 check results recorded",summary)
        self.assertEqual(summary.count("coding agent"),1)
        self.assertEqual(summary.count("resubmitting"),1)

    def test_accepted_dismissal_ignores_old_faults_but_not_current_blockers(self):
        from test_workflow_board_projection import record
        accepted={"decision_id":"d","run_id":"r","generation":2,"input_digest":"digest",
                  "decision":{"decision":"ACCEPT","run_id":"r","evaluation_generation":2,"input_digest":"digest"}}
        acceptance={"acceptance_id":"a","decision_id":"d","run_id":"r","evaluation_generation":2,"input_digest":"digest"}
        item=record("DECIDED","PASS",decisions=(accepted,),extra={"acceptances":[acceptance],
            "incidents":[{"incident":{"incident_id":"old","run_id":"r","evaluation_generation":1,"state":"OPEN","owner_action_required":True,"summary":"Old action"}},
                         {"incident":{"incident_id":"unbound","run_id":"r","state":"OPEN","owner_action_required":True,"summary":"Unbound action"}}]})
        card=workflow_board.project({"available":True,"records":[item]},worktree="/w")["columns"]["Accepted"][0]
        self.assertTrue(card["dismissible"])
        self.assertEqual(card["details"]["owner_attention"],[])
        item["incidents"].append({"incident":{"incident_id":"current","run_id":"r","evaluation_generation":2,"state":"OPEN","owner_action_required":True,"summary":"Current action"}})
        card=workflow_board.project({"available":True,"records":[item]},worktree="/w")["columns"]["Accepted"][0]
        self.assertFalse(card["dismissible"])
        item["incidents"]=[]
        item["resources"]=[{"resource_key":"native_validation","state":"UNKNOWN","generation":2}]
        card=workflow_board.project({"available":True,"records":[item]},worktree="/w")["columns"]["Accepted"][0]
        self.assertFalse(card["dismissible"])

    def test_missing_store_is_not_created_or_healthy(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "missing" / "state.sqlite"
            result = workflow_board.snapshot(path, "C:/work")
            self.assertFalse(result["available"])
            self.assertFalse(path.exists())

    def test_snapshot_is_readonly_and_projection_whitelists(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "state.sqlite"
            WorkflowStore(path)
            before = hashlib.sha256(path.read_bytes()).hexdigest()
            raw = {"available": True, "records": [{"worktree":"/safe", "sprint":"S1",
                    "run":{"run_id":"r1", "sprint_id":"S1", "current_attempt":1, "sequence":3,
                           "input_digest":"abc", "title":"A useful feature", "bearer_token":"SECRET"},
                    "attempt":{"phase":"CHECKING", "criteria":["criterion"]},
                    "tasks":[{"task_id":"t1", "kind":"CHECK", "state":"COMPLETED",
                              "result":{"decision":"PASS", "claim_token":"SECRET"},
                              "prompt":"SECRET"}]}]}
            raw["records"][0]["incidents"] = [{"incident":{"incident_id":"i1", "summary":"held"},
                                                "seen_ms":1, "resolved_ms":None}]
            raw["records"][0]["resources"] = [{"resource_key":"native_validation", "state":"UNKNOWN"}]
            projected = workflow_board.project(raw, worktree="/safe")
            encoded = json.dumps(projected)
            self.assertNotIn("SECRET", encoded)
            card = projected["columns"]["Checking"][0]
            self.assertIsNone(card["focused_result"])
            self.assertEqual(card["details"]["native_check_status"][0]["result"], "PASS")
            self.assertTrue(card["details"]["summary"])
            self.assertEqual(card["details"]["owner_attention"], [])
            self.assertEqual([fault["state"] for fault in projected["faults"]], ["OPEN", "UNKNOWN"])
            self.assertTrue(projected["faults"][0]["seen"])
            uri = path.resolve().as_uri() + "?mode=ro"
            connection = sqlite3.connect(uri, uri=True)
            try:
                connection.execute("PRAGMA query_only=ON")
                with self.assertRaises(sqlite3.OperationalError):
                    connection.execute("CREATE TABLE forbidden(x)")
            finally:
                connection.close()
            self.assertEqual(before, hashlib.sha256(path.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
