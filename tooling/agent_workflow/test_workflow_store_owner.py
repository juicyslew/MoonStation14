"""Focused schema and read API coverage for durable owner decision records."""
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, StaleCommand, IdempotencyConflict
import test_workflow_store_finality as finality


class OwnerRecordSchemaTests(unittest.TestCase):
    def test_owner_tables_are_created_in_store_authority(self):
        with tempfile.TemporaryDirectory() as directory:
            store = WorkflowStore(Path(directory) / "owner.sqlite")
            db = store._connect()
            try:
                names = {row[0] for row in db.execute(
                    "SELECT name FROM sqlite_master WHERE type='table'")}
                self.assertTrue({"owner_decisions", "acceptances", "regression_jobs"}.issubset(names))
            finally:
                db.close()


class OwnerAcceptanceTests(finality.TerminalFinalityTests):
    def acceptance_setup(self):
        receipt = self.artifact("artifact_owner_receipt", kind="CHECK_RECEIPT")
        source = self.artifact("artifact_owner_source", kind="SOURCE_MANIFEST")
        build = self.artifact("artifact_owner_build", kind="BUILD")
        self.set_task(self.check, refs=[receipt, source, build])
        self.stop("PASS")
        terminal = self.store.transition_attempt("run_finality", 1, expected_sequence=1,
            phase="DECIDED", terminal_result="PASS", cleanup_state="NOT_REQUIRED")
        return source, build, terminal["event_id"]

    def owner_command(self, result_id, result_digest, *, action="ACCEPT", session="owner-session"):
        worktree=self.store.get_run("run_finality")["run"]["author"]["worktree"]
        command = {"contract_version": 1, "command_id": "owner-" + action.lower(),
            "run_id": "run_finality", "evaluation_generation": 1, "input_digest": self.input,
            "owner": {"session_id": session, "worktree": worktree, "role": "owner"},
            "kind": "LOCAL_OWNER", "decision": action, "findings": ([{"criterion_id":"C1", "category":"IMPLEMENTATION", "message":"change required", "location":"fixture", "evidence_refs":["artifact_owner_source"], "required_change":"fix it"}] if action == "REQUEST_CHANGES" else []), "expected_sequence": 2}
        if action == "ACCEPT": command["acceptance_id"] = "accept_finality"
        return command

    def perform_accept(self, source, build, event_id, *, shown_digest=None, action="ACCEPT", session="owner-session"):
        event = self.store.get_event(event_id)["payload"]
        decision = self.owner_command(event["result_id"], event["result_digest"], action=action, session=session)
        return self.store.record_owner_decision(decision, authenticated_owner=decision["owner"],
            displayed_result_id=event["result_id"], displayed_result_digest=shown_digest or event["result_digest"],
            source_artifact_ref=source, build_artifact_refs=[build],
            regression_inputs={"manifest_ref": source, "plan_digest": "a" * 64} if action == "ACCEPT" else None)

    def test_acceptance_and_exact_replay_create_one_job_and_wake(self):
        source, build, event_id = self.acceptance_setup()
        first = self.perform_accept(source, build, event_id)
        second = self.perform_accept(source, build, event_id)
        self.assertEqual(first, second)
        db = self.store._connect()
        try:
            self.assertEqual(1, db.execute("SELECT count(*) FROM acceptances").fetchone()[0])
            self.assertEqual(1, db.execute("SELECT count(*) FROM regression_jobs").fetchone()[0])
            self.assertEqual(1, db.execute("SELECT count(*) FROM events WHERE kind='REGRESSION_READY'").fetchone()[0])
        finally:
            db.close()

    def test_wrong_displayed_result_digest_is_denied_without_acceptance(self):
        source, build, event_id = self.acceptance_setup()
        with self.assertRaises(StaleCommand):
            self.perform_accept(source, build, event_id, shown_digest="0" * 64)
        self.assertIsNone(self.store.get_acceptance("accept_finality"))
        self.assertIsNone(self.store.get_regression_job("task_regression_finality"))

    def test_null_session_owner_exact_acceptance_replay(self):
        source, build, event_id = self.acceptance_setup()
        first = self.perform_accept(source, build, event_id, session=None)
        second = self.perform_accept(source, build, event_id, session=None)
        self.assertEqual(first, second)

    def test_new_command_same_acceptance_must_match_semantics(self):
        source, build, event_id = self.acceptance_setup()
        first = self.perform_accept(source, build, event_id)
        event = self.store.get_event(event_id)["payload"]
        changed = self.owner_command(event["result_id"], event["result_digest"])
        changed["command_id"] = "owner-new-command"
        changed["owner"] = dict(changed["owner"], session_id="different-owner-session")
        with self.assertRaises(IdempotencyConflict):
            self.store.record_owner_decision(changed, authenticated_owner=changed["owner"],
                displayed_result_id=event["result_id"], displayed_result_digest=event["result_digest"],
                source_artifact_ref=source, build_artifact_refs=[build],
                regression_inputs={"manifest_ref": source, "plan_digest": "a" * 64})
        benign = self.owner_command(event["result_id"], event["result_digest"])
        benign["command_id"] = "owner-benign-retry"
        self.assertEqual(first, self.store.record_owner_decision(benign, authenticated_owner=benign["owner"],
            displayed_result_id=event["result_id"], displayed_result_digest=event["result_digest"],
            source_artifact_ref=source, build_artifact_refs=[build],
            regression_inputs={"manifest_ref": source, "plan_digest": "a" * 64, "source_input_digest": self.input, "source_artifact_ref": source}))
        self.assertEqual(1, len(self.store.get_owner_decisions("run_finality")))

    def test_job_insert_fault_rolls_back_decision_acceptance_and_wake_then_retries(self):
        source, build, event_id = self.acceptance_setup()
        db = self.store._connect()
        try:
            db.execute("CREATE TRIGGER fail_owner_job BEFORE INSERT ON regression_jobs BEGIN SELECT RAISE(ABORT,'injected job failure'); END")
        finally:
            db.close()
        with self.assertRaises(sqlite3.IntegrityError):
            self.perform_accept(source, build, event_id)
        self.assertEqual([], self.store.get_owner_decisions("run_finality"))
        self.assertIsNone(self.store.get_acceptance("accept_finality"))
        db = self.store._connect()
        try:
            self.assertEqual(0, db.execute("SELECT count(*) FROM events WHERE kind='REGRESSION_READY'").fetchone()[0])
            db.execute("DROP TRIGGER fail_owner_job")
        finally:
            db.close()
        result = self.perform_accept(source, build, event_id)
        self.assertEqual("accept_finality", result["acceptance_id"])

    def test_request_changes_and_cancel_are_feedback_not_verdicts_or_jobs(self):
        for action in ("REQUEST_CHANGES", "CANCEL"):
            with self.subTest(action=action):
                source, build, event_id = self.acceptance_setup()
                response = self.perform_accept(source, build, event_id, action=action)
                self.assertEqual(action, response["decision"])
                self.assertEqual("PASS", self.store.get_run("run_finality")["attempt"]["result"])
                self.assertIsNone(self.store.get_acceptance("accept_finality"))
                db = self.store._connect()
                try:
                    payload = next(json.loads(row[0]) for row in db.execute("SELECT payload FROM events WHERE kind='OWNER_FEEDBACK'")
                                   if json.loads(row[0])["decision_id"] == response["decision_id"])
                    self.assertEqual("FIX_FINDINGS" if action == "REQUEST_CHANGES" else "CLOSED", payload["next_action"])
                    self.assertEqual(0, db.execute("SELECT count(*) FROM regression_jobs").fetchone()[0])
                finally:
                    db.close()

    def test_read_apis_return_empty_or_missing_for_unknown_records(self):
        with tempfile.TemporaryDirectory() as directory:
            store = WorkflowStore(Path(directory) / "owner.sqlite")
            self.assertEqual([], store.get_owner_decisions("run_missing"))
            self.assertIsNone(store.get_acceptance("accept_missing"))
            self.assertIsNone(store.get_regression_job("task_missing"))
            self.assertIsNone(store.get_regression_inputs("task_missing"))


if __name__ == "__main__":
    unittest.main()
