import json
import tempfile
import unittest
from pathlib import Path

from test_workflow_native_execution import admit_native_fixture
from workflow_store import IdempotencyConflict, ProtocolError, StaleCommand


class NativeCheckRecordStoreTests(unittest.TestCase):
    def test_record_survives_error_result_and_is_immutable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _, store, _, task_id = admit_native_fixture(root, caller="check-record", key="check-record")
            task = store.get_task(task_id)
            identity = {"pid": 321, "created_ms": 1}
            store.dispatch_task(task_id=task_id, event_id="event_check_record_dispatch",
                                process_identity=identity, claim_token="token")
            db = store._connect()
            try:
                saved = json.loads(db.execute("SELECT task_json FROM tasks WHERE id=?", (task_id,)).fetchone()[0])
                db.execute("UPDATE tasks SET task_json=? WHERE id=?", (json.dumps(dict(saved, state="CLAIMED")), task_id))
            finally:
                db.close()
            artifact = {"contract_version": 1, "artifact_id": "artifact_inner_check", "type": "PROVENANCE",
                        "producer_kind": "TASK", "producer_id": task_id, "producer_task_id": task_id,
                        "run_id": task["run_id"], "path": "inner.json", "sha256": "a" * 64,
                        "size_bytes": 1, "immutable": True}
            store.register_artifact(artifact)
            other = dict(artifact, artifact_id="artifact_inner_other", path="other.json")
            store.register_artifact(other)
            self.assertEqual(store.record_native_check_completed(task_id, 0, artifact["artifact_id"])["disposition"], "ACCEPTED")
            self.assertEqual(store.record_native_check_completed(task_id, 0, artifact["artifact_id"])["disposition"], "REPLAYED")
            with self.assertRaises(IdempotencyConflict):
                store.record_native_check_completed(task_id, 0, other["artifact_id"])
            with self.assertRaises(ProtocolError):
                store.record_native_check_completed(task_id, True, artifact["artifact_id"])
            error = {"contract_version": 1, "task_id": task_id, "run_id": task["run_id"],
                     "evaluation_generation": task["evaluation_generation"], "assignment_generation": task["assignment_generation"],
                     "input_digest": task["input_digest"], "error_code": "INNER_ERROR", "message": "later check failed",
                     "artifact_refs": []}
            store.complete_native_task(task_id=task_id, run_id=task["run_id"],
                evaluation_generation=task["evaluation_generation"], assignment_generation=task["assignment_generation"],
                input_digest=task["input_digest"], process_identity=identity, claim_token="token",
                outcome={"decision": "ERROR", "findings": [], "artifact_refs": []}, error_report=error)
            records = store.list_native_check_records(task_id)
            self.assertEqual(len(records), 1)
            self.assertEqual(records[0]["artifact_ref"], artifact["artifact_id"])
            with self.assertRaises(StaleCommand):
                store.record_native_check_completed(task_id, 1, artifact["artifact_id"])


if __name__ == "__main__":
    unittest.main()
