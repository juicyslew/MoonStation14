import tempfile
import unittest
from pathlib import Path

import workflow_store
from test_workflow_native_execution import admit_native_fixture


class NativeInterruptionTests(unittest.TestCase):
    def test_cleaned_native_interruption_commits_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _, store, _, task_id = admit_native_fixture(root, caller="interrupt-ok", key="interrupt-ok")
            task = store.get_task(task_id)
            creation = {"contract_version": 1, "artifact_id": "artifact_scope_create", "type": "PROVENANCE",
                        "producer_kind": "TASK", "producer_id": task_id, "producer_task_id": task_id,
                        "run_id": task["run_id"], "path": "scope-create.json", "sha256": "a" * 64, "size_bytes": 1, "immutable":True}
            cleanup = dict(creation, artifact_id="artifact_scope_cleanup", path="scope-cleanup.json")
            store.register_artifact(creation)
            store.dispatch_task(task_id=task_id, event_id="event_interrupt_dispatch", process_identity={"pid": 123, "created_ms": 1},
                                claim_token="token", native_scope={"scope_name":"scope-interrupt", "worker_identity":{"pid":123,"created":1}, "creation_artifact_ref":creation["artifact_id"]})
            store.register_artifact(cleanup)
            store.record_native_scope_cleanup(task_id=task_id, assignment_generation=task["assignment_generation"], artifact_ref=cleanup["artifact_id"])
            report = {"contract_version":1,"task_id":task_id,"run_id":task["run_id"],"evaluation_generation":task["evaluation_generation"],
                      "assignment_generation":task["assignment_generation"],"input_digest":task["input_digest"],"error_code":"NATIVE_INTERRUPTED",
                      "message":"worker ended unexpectedly","artifact_refs":[]}
            first = store.report_native_interruption(report, cleanup_artifact_ref=cleanup["artifact_id"])
            self.assertEqual(store.get_task(task_id)["result"]["decision"], "ERROR")
            self.assertEqual(store.get_task(task_id)["state"], "COMPLETED")
            self.assertEqual(store.get_incident(first["incident_id"])["incident"]["code"], "NATIVE_INTERRUPTED")
            db = store._connect()
            try:
                before = db.execute("SELECT COUNT(*) FROM events WHERE kind='TASK_COMPLETED'").fetchone()[0]
            finally: db.close()
            self.assertEqual(before, 1)
            self.assertEqual(store.report_native_interruption(report, cleanup_artifact_ref=cleanup["artifact_id"])["disposition"], "REPLAYED")
            with self.assertRaises(workflow_store.IdempotencyConflict):
                store.report_native_interruption(dict(report, message="changed"), cleanup_artifact_ref=cleanup["artifact_id"])
            db = store._connect()
            try: self.assertEqual(db.execute("SELECT COUNT(*) FROM events WHERE kind='TASK_COMPLETED'").fetchone()[0], before)
            finally: db.close()

    def test_interruption_requires_saved_cleanup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _, store, _, task_id = admit_native_fixture(root, caller="interrupt", key="interrupt")
            task = store.get_task(task_id)
            report = {"contract_version": 1, "task_id": task_id, "run_id": task["run_id"],
                      "evaluation_generation": task["evaluation_generation"],
                      "assignment_generation": task["assignment_generation"],
                      "input_digest": task["input_digest"], "error_code": "NATIVE_INTERRUPTED",
                      "message": "worker ended unexpectedly", "artifact_refs": []}
            with self.assertRaises(workflow_store.StaleCommand):
                store.report_native_interruption(report, cleanup_artifact_ref="artifact_cleanup")
