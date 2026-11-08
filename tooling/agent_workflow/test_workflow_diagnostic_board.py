import unittest

import workflow_board
import tempfile
from pathlib import Path
import workflow_store
import workflow_submission
from diagnostic_test_support import make_diagnostic_case


def diagnostic(phase, result=None):
    return {"worktree":"/w","sprint":"s",
            "run":{"run_id":"diag-1","run_kind":"DIAGNOSTIC","current_attempt":1,"sequence":2,"title":"Audit"},
            "attempt":{"generation":1,"phase":phase,"result":result},
            "request":{"title":"Audit"},"tasks":[{"kind":"CHECK","state":"RUNNING"}],
            "owner_decisions":[{"decision":"ACCEPT"}],"acceptances":[{"acceptance_id":"bad"}],
            "incidents":[],"resources":[],"deliveries":[],"artifacts":[],"events":[]}


class WorkflowDiagnosticBoardTests(unittest.TestCase):
    def test_active_diagnostic_uses_existing_check_column_without_owner_actions(self):
        board=workflow_board.project({"available":True,"records":[diagnostic("CHECKING")],"history":[]},worktree="/w")
        card=board["columns"]["Checking"][0]
        self.assertEqual(card["badge"],"Diagnostic")
        self.assertEqual(card["details"]["kind"],"DIAGNOSTIC")
        self.assertEqual(card["capabilities"],["cancel_active"])
        self.assertNotIn("Your testing",[card["phase"] for cards in board["columns"].values() for card in cards])

    def test_nonhead_diagnostic_history_is_not_superseded(self):
        for result in ("PASS","FAIL","ERROR"):
            old=diagnostic("DECIDED",result)
            board=workflow_board.project({"available":True,"records":[],"history":[old]},worktree="/w")
            self.assertEqual(board["history"][0]["phase"],"Diagnostic results")
            self.assertEqual(board["history"][0]["details"]["focused_result"],result)

    def test_active_history_diagnostic_is_projected_once(self):
        old=diagnostic("CHECKING")
        board=workflow_board.project({"available":True,"records":[],"history":[old]},worktree="/w")
        self.assertEqual(len(board["columns"]["Checking"]),1)
        self.assertFalse(board["history"])

    def test_readonly_store_projects_headless_active_diagnostic(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case=make_diagnostic_case(root)
            store=workflow_store.WorkflowStore(root/"db.sqlite")
            response=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=case.key,
                request=case.request,context=case.context,artifact_dir=root/"artifacts")
            raw=workflow_store.WorkflowStore.readonly_snapshot(root/"db.sqlite",worktree=case.request["source_worktree"])
            self.assertFalse(raw["records"])
            self.assertEqual(len(raw["history"]),1)
            board=workflow_board.project(raw,worktree=case.request["source_worktree"])
            cards=[card for column in ("Queued","Checking") for card in board["columns"][column]]
            self.assertEqual([card["run_id"] for card in cards],[response["run_id"]])


if __name__ == "__main__":
    unittest.main()
