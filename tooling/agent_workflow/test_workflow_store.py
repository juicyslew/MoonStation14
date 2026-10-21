import tempfile
import threading
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, ProtocolError, IdempotencyConflict, canonical_json, strict_loads
from workflow_store_test_support import admit


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = WorkflowStore(Path(self.tmp.name) / "authority.sqlite")

    def tearDown(self):
        self.store.close()
        self.tmp.cleanup()

    def test_strict_json_and_canonical_digest(self):
        with self.assertRaises(ProtocolError):
            strict_loads('{"a":1,"a":2}')
        with self.assertRaises(ProtocolError):
            strict_loads('{"n":NaN}')
        self.assertEqual(canonical_json({"z": 1, "a": "é"}), '{"a":"\\u00e9","z":1}')

    def test_schema_rejects_unknown_and_bad_version(self):
        from workflow_store import validate
        with self.assertRaises(ProtocolError):
            validate({"contract_version": 99, "status": "ACCEPTED", "run_id": "run_x",
                      "generation": 1, "next_action": "WAIT_FOR_RESULT", "x": 1},
                     "submission_response")

    def test_logical_task_key_prevents_duplicate_slots_but_allows_distinct_work(self):
        _, _, task, _ = admit(self.store, "logical", logical_task_key="check:unit")
        duplicate = dict(task, task_id="task_another_id")
        with self.assertRaises(IdempotencyConflict):
            self.store.add_task(duplicate, logical_task_key="check:unit")
        other = dict(task, task_id="task_other")
        self.assertEqual(self.store.add_task(other, logical_task_key="check:other")["disposition"], "ACCEPTED")
        self.assertEqual(self.store.add_task(other, logical_task_key="check:other")["disposition"], "REPLAYED")

    def test_concurrent_outbox_claims_are_serialized_across_connections(self):
        admit(self.store, "concurrent")
        barrier = threading.Barrier(3)
        claimed, failures = [], []

        def worker():
            try:
                barrier.wait()
                claimed.append(WorkflowStore(self.store.path).claim_outbox(now_ms=10**15, lease_ms=60000))
            except Exception as exc:
                failures.append(exc)

        threads = [threading.Thread(target=worker) for _ in range(2)]
        for thread in threads:
            thread.start()
        barrier.wait()
        for thread in threads:
            thread.join()
        self.assertFalse(failures)
        self.assertEqual(len([item for item in claimed if item is not None]), 1)


if __name__ == "__main__":
    unittest.main()
