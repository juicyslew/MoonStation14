"""Focused source-snapshot ERROR replacement predicate and transaction checks."""
import json
import tempfile
import threading
import unittest
from pathlib import Path

import workflow_store


def _helpers():
    from test_workflow_failed_run_replacement import FailedRunReplacementTests
    return FailedRunReplacementTests


class SourceErrorReplacementTests(unittest.TestCase):
    def _fixture(self, root):
        store = workflow_store.WorkflowStore(root / "workflow.sqlite")
        request = _helpers()._request()
        _helpers()._submit(store, request, "one")
        task = store.list_tasks(run_id="run_one")[0]
        identity = {"pid": 1, "worker": "native-fixture"}
        body = {"binding": {"decision": "ERROR", "run_id": "run_one", "task_id": task["task_id"], "evaluation_generation": 1,
                    "assignment_generation": 1, "input_digest": request["input_digest"]},
                "process_identity": identity, "claim_token": "claim-token",
                "error_report": {"task_id": task["task_id"], "run_id": "run_one", "evaluation_generation": 1,
                    "assignment_generation": 1, "input_digest": request["input_digest"],
                    "error_code": "SOURCE_SNAPSHOT_CHANGED"}}
        db = store._connect()
        try:
            attempt = {"contract_version": 1, "run_id": "run_one", "generation": 1, "phase": "DECIDED",
                "result": "ERROR", "closure": None, "proposed_result": None, "cleanup_state": "CONFIRMED",
                "pause_reason": None, "proposed_closure": None}
            db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one' AND generation=1",
                       (workflow_store.canonical_json(attempt),))
            task_doc = workflow_store.strict_loads(db.execute("SELECT task_json FROM tasks WHERE id=?", (task["task_id"],)).fetchone()[0])
            task_doc.update(state="COMPLETED", process_identity=identity,
                            result={"decision": "ERROR", "findings": [], "artifact_refs": []})
            db.execute("UPDATE tasks SET task_json=?,result_json=? WHERE id=?",
                       (workflow_store.canonical_json(task_doc), workflow_store.canonical_json(body), task["task_id"]))
            db.execute("INSERT INTO native_claims(task_id,process_identity,claim_token) VALUES(?,?,?)",
                       (task["task_id"], workflow_store.canonical_json(identity), "claim-token"))
            db.execute("UPDATE outbox SET state='ACKNOWLEDGED' WHERE event_id='event_one'")
            db.commit()
        finally:
            db.close()
        return store, request, task["task_id"]

    def test_current_source_snapshot_error_is_replaceable_and_submission_is_atomic(self):
        with tempfile.TemporaryDirectory() as tmp:
            store, request, _ = self._fixture(Path(tmp))
            author = request["author"]
            self.assertTrue(store.can_replace_source_snapshot_error(run_id="run_one", author=author, worktree="tree"))
            replacement = dict(request, idempotency_key="fresh", replaces_run_id="run_one", input_digest="d" * 64)
            accepted = _helpers()._submit(store, replacement, "two")
            self.assertEqual("ACCEPTED", accepted["status"])
            self.assertEqual("run_two", accepted["run_id"])
            db = store._connect()
            try: self.assertEqual("run_two", db.execute("SELECT run_id FROM feature_heads WHERE worktree='tree' AND sprint='sprint'").fetchone()[0])
            finally: db.close()
            replay = store.submit(caller="author", request=replacement, run={}, first_task={}, outbox_event={}, logical_task_key="ignored")
            self.assertEqual(accepted, replay)

    def test_source_error_predicate_rejects_invalid_provenance_and_authority(self):
        mutations = ("unchanged_source", "generic_error", "bad_report", "report_generation_mismatch", "binding_task_mismatch", "foreign_author", "foreign_worktree",
            "wrong_sprint", "nonhead", "diagnostic", "closed", "unconfirmed", "held", "uncleaned_native",
            "review_unsettled", "pending_dispatch", "report_digest_mismatch", "missing_claim", "active_attempt",
            "unknown_resource", "cleanup_not_required", "expired_head", "noncurrent_head")
        for mutation in mutations:
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as tmp:
                store, request, task_id = self._fixture(Path(tmp))
                db = store._connect()
                try:
                    if mutation == "generic_error":
                        body = workflow_store.strict_loads(db.execute("SELECT result_json FROM tasks WHERE id=?", (task_id,)).fetchone()[0]); body["error_report"]["error_code"] = "NATIVE_EXECUTION_ERROR"
                        db.execute("UPDATE tasks SET result_json=? WHERE id=?", (workflow_store.canonical_json(body), task_id))
                    elif mutation == "bad_report":
                        body = workflow_store.strict_loads(db.execute("SELECT result_json FROM tasks WHERE id=?", (task_id,)).fetchone()[0]); body["error_report"]["error_code"] = "FROZEN_BUNDLE_CORRUPTION"
                        db.execute("UPDATE tasks SET result_json=? WHERE id=?", (workflow_store.canonical_json(body), task_id))
                    elif mutation in ("report_digest_mismatch", "report_generation_mismatch", "binding_task_mismatch"):
                        body = workflow_store.strict_loads(db.execute("SELECT result_json FROM tasks WHERE id=?", (task_id,)).fetchone()[0])
                        if mutation == "report_digest_mismatch": body["error_report"]["input_digest"] = "f" * 64
                        elif mutation == "report_generation_mismatch": body["error_report"]["evaluation_generation"] = 99
                        else: body["binding"]["task_id"] = "wrong-task"
                        db.execute("UPDATE tasks SET result_json=? WHERE id=?", (workflow_store.canonical_json(body), task_id))
                    elif mutation == "missing_claim": db.execute("DELETE FROM native_claims WHERE task_id=?", (task_id,))
                    elif mutation == "pending_dispatch": db.execute("UPDATE outbox SET state='PENDING' WHERE event_id='event_one'")
                    elif mutation == "held": db.execute("INSERT INTO resources VALUES(?,?,?,?,?,?,?,?,?,?)", ("r", "resource", "run_one", 1, task_id, 1, "identity", "HELD", None, "d"*64))
                    elif mutation == "uncleaned_native": db.execute("INSERT INTO native_scopes(task_id,assignment_generation,scope_name,worker_identity_json,creation_artifact_ref,state) VALUES(?,?,?,?,?,?)", (task_id, 1, "scope", "{}", "artifact", "OWNED"))
                    elif mutation == "unconfirmed":
                        attempt = workflow_store.strict_loads(db.execute("SELECT attempt_json FROM attempts WHERE run_id='run_one'").fetchone()[0]); attempt["cleanup_state"] = "UNRESOLVED"
                        db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one'", (workflow_store.canonical_json(attempt),))
                    elif mutation == "closed":
                        attempt = workflow_store.strict_loads(db.execute("SELECT attempt_json FROM attempts WHERE run_id='run_one'").fetchone()[0]); attempt.update(phase="CLOSED", result=None, closure="CANCELLED")
                        db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one'", (workflow_store.canonical_json(attempt),))
                    elif mutation == "diagnostic":
                        run = workflow_store.strict_loads(db.execute("SELECT run_json FROM runs WHERE id='run_one'").fetchone()[0]); run["run_kind"] = "DIAGNOSTIC"
                        db.execute("UPDATE runs SET run_json=? WHERE id='run_one'", (workflow_store.canonical_json(run),))
                    elif mutation == "nonhead": db.execute("DELETE FROM feature_heads WHERE worktree='tree' AND sprint='sprint'")
                    elif mutation == "review_unsettled":
                        review = workflow_store.strict_loads(db.execute("SELECT task_json FROM tasks WHERE id=?", (task_id,)).fetchone()[0])
                        review.update(task_id="review-active", kind="REVIEW", state="RUNNING")
                        db.execute("INSERT INTO tasks(id,run_id,generation,logical_key,task_json) VALUES(?,?,?,?,?)", ("review-active", "run_one", 1, "review-active", workflow_store.canonical_json(review)))
                    elif mutation == "active_attempt":
                        attempt = workflow_store.strict_loads(db.execute("SELECT attempt_json FROM attempts WHERE run_id='run_one'").fetchone()[0]); attempt["phase"] = "CHECKING"
                        db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one'", (workflow_store.canonical_json(attempt),))
                    elif mutation in ("unknown_resource", "cleanup_not_required"):
                        if mutation == "unknown_resource": db.execute("INSERT INTO resources VALUES(?,?,?,?,?,?,?,?,?,?)", ("r", "resource", "run_one", 1, task_id, 1, "identity", "UNKNOWN", None, "d"*64))
                        else:
                            attempt = workflow_store.strict_loads(db.execute("SELECT attempt_json FROM attempts WHERE run_id='run_one'").fetchone()[0]); attempt["cleanup_state"] = "NOT_REQUIRED"
                            db.execute("UPDATE attempts SET attempt_json=? WHERE run_id='run_one'", (workflow_store.canonical_json(attempt),))
                    elif mutation in ("expired_head", "noncurrent_head"):
                        if mutation == "expired_head": db.execute("DELETE FROM feature_heads WHERE worktree='tree' AND sprint='sprint'")
                        else:
                            run = workflow_store.strict_loads(db.execute("SELECT run_json FROM runs WHERE id='run_one'").fetchone()[0])
                            run["run_id"] = "other-run"
                            db.execute("INSERT INTO runs(id,sprint,worktree,run_json,current_generation,sequence) VALUES(?,?,?,?,?,?)",
                                ("other-run", "sprint", "tree", workflow_store.canonical_json(run), 1, 0))
                            db.execute("INSERT INTO attempts(run_id,generation,attempt_json) SELECT 'other-run',generation,attempt_json FROM attempts WHERE run_id='run_one'")
                            db.execute("UPDATE feature_heads SET run_id='other-run' WHERE worktree='tree' AND sprint='sprint'")
                    db.commit()
                finally: db.close()
                if mutation == "unchanged_source":
                    with self.assertRaises(workflow_store.SubmissionHeadConflict):
                        _helpers()._submit(store, dict(request, idempotency_key="same", replaces_run_id="run_one"), "two")
                author = dict(request["author"])
                worktree = "tree"
                if mutation == "foreign_author": author["session_id"] = "other"
                if mutation == "foreign_worktree": worktree = "other"
                if mutation not in ("unchanged_source", "wrong_sprint", "binding_task_mismatch"):
                    self.assertFalse(store.can_replace_source_snapshot_error(run_id="run_one", author=author, worktree=worktree))
                if mutation != "unchanged_source":
                    bad_request = dict(request, idempotency_key="bad-" + mutation, replaces_run_id="run_one")
                    if mutation == "wrong_sprint": bad_request["sprint_id"] = "different-sprint"
                    if mutation == "foreign_author": bad_request["author"] = author
                    if mutation == "foreign_worktree": bad_request["source_worktree"] = "other"
                    with self.assertRaises(workflow_store.SubmissionHeadConflict):
                        _helpers()._submit(store, bad_request, "two")

    def test_competing_fresh_replacements_accept_exactly_one(self):
        with tempfile.TemporaryDirectory() as tmp:
            store, request, _ = self._fixture(Path(tmp))
            barrier = threading.Barrier(2)
            outcomes = []
            def compete(key):
                barrier.wait()
                try:
                    outcomes.append(_helpers()._submit(store, dict(request, idempotency_key=key, replaces_run_id="run_one", input_digest="e"*64), key)["status"])
                except workflow_store.SubmissionHeadConflict as exc:
                    outcomes.append(exc.reason)
            workers = [threading.Thread(target=compete, args=(key,)) for key in ("fresh-a", "fresh-b")]
            for worker in workers: worker.start()
            for worker in workers: worker.join(timeout=15)
            self.assertTrue(all(not worker.is_alive() for worker in workers))
            self.assertEqual(1, outcomes.count("ACCEPTED"))
            self.assertEqual(1, outcomes.count(workflow_store.SubmissionHeadConflict.INVALID_TARGET))


if __name__ == "__main__":
    unittest.main()
