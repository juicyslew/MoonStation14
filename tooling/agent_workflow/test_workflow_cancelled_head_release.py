"""Regression coverage for releasing a settled owner-cancelled feature head."""
import tempfile
import unittest
from pathlib import Path

import workflow_run_control
import workflow_store
from test_workflow_native_execution import admit_native_fixture
import test_workflow_failed_run_replacement as replacement_fixtures


class CancelledHeadReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def _closed_cancel(self, key, legacy_head=True):
        case, store, _, task_id = admit_native_fixture(self.root / key, caller="author", key=key, delay=0)
        task = store.get_task(task_id)
        run_id = task["run_id"]
        run = store.get_run(run_id)
        event = store.claim_outbox(lease_ms=1000)
        self.assertIsNotNone(event)
        store.acknowledge_outbox(event["event_id"], event["claim_generation"])
        store.transition_attempt(run_id, 1, expected_sequence=run["run"]["sequence"],
            phase="STOPPING", closure="CANCELLED", command_id="fixture-stop-"+key)
        run = store.get_run(run_id)
        store.transition_attempt(run_id, 1, expected_sequence=run["run"]["sequence"],
            phase="CLOSED", cleanup_state="CONFIRMED", command_id="fixture-close-"+key)
        task_id = task["task_id"]
        # Restore the historical legacy-head condition after exercising the valid public closure.
        if legacy_head:
            db = store._connect()
            try:
                db.execute("INSERT INTO feature_heads(worktree,sprint,run_id) VALUES(?,?,?)",
                    (str(case.repo), "sprint", run_id))
                db.commit()
            finally:
                db.close()
        return case, store, task_id, run_id

    def test_closed_cancel_is_retired_and_fresh_submission_is_accepted_without_replacement(self):
        case, store, task_id, run_id = self._closed_cancel("cancelled-release")
        before = replacement_fixtures.FailedRunReplacementTests._row_counts(store)
        command = {"contract_version": 1, "run_id": run_id, "evaluation_generation": 1,
                   "expected_sequence": store.get_run(run_id)["run"]["sequence"],
                   "command_id": "retire-cancelled", "actor": "OWNER", "reason": "CANCELLED"}
        binding = type("Binding", (), {"worktree": str(case.repo), "caller": "owner"})()
        service = workflow_run_control.ServiceRunControl(store)
        result = service.cancel(command, binding)
        self.assertEqual("RETIRED_HEAD", result["state"])
        self.assertEqual("CANCELLED", result["historical_closure"])
        self.assertIsNone(result["historical_result"])
        self.assertEqual("CANCELLED", store.get_task(task_id)["state"])
        replay = service.cancel(command, binding)
        self.assertEqual(result["cancellation_id"], replay["cancellation_id"])

        # Reuse the established public submission fixture, with a new key and no replacement intent.
        new_case, same_store, _, new_task = admit_native_fixture(self.root / "cancelled-release",
            caller="author", key="fresh-after-cancel", shared_store=store, delay=0)
        self.assertIs(same_store, store)
        self.assertNotEqual(run_id, store.get_task(new_task)["run_id"])
        self.assertEqual("QUEUED", store.get_task(new_task)["state"])
        self.assertEqual(str(case.repo), str(new_case.repo))
        after = replacement_fixtures.FailedRunReplacementTests._row_counts(store)
        for table in ("runs", "attempts", "tasks", "artifacts", "events", "deliveries", "acks"):
            if table in before and table in after:
                self.assertGreaterEqual(after[table], before[table], table)

    def test_held_resource_blocks_cancelled_head_retirement(self):
        case, store, task_id, run_id = self._closed_cancel("cancelled-held")
        db = store._connect()
        try:
            db.execute("INSERT INTO resources(claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof,request_digest) VALUES(?,?,?,?,?,1,'{}','HELD',NULL,'digest')",
                       ("claim-held", "resource-held", run_id, 1, task_id))
            db.commit()
        finally:
            db.close()
        with self.assertRaises(workflow_store.StaleCommand):
            store.retire_settled_cancelled_head(run_id=run_id, generation=1,
                expected_sequence=store.get_run(run_id)["run"]["sequence"], caller="owner",
                command_id="blocked", worktree=str(case.repo))

    def test_valid_cancel_closure_releases_head_for_fresh_admission(self):
        case, store, _, run_id = self._closed_cancel("cancelled-auto-release", legacy_head=False)
        self.assertFalse(store.is_current_feature_head(run_id))
        new_case, same_store, _, task_id = admit_native_fixture(self.root / "cancelled-auto-release",
            caller="author", key="fresh-after-auto-release", shared_store=store, delay=0)
        self.assertIs(store, same_store)
        self.assertEqual(str(case.repo), str(new_case.repo))
        self.assertNotEqual(run_id, store.get_task(task_id)["run_id"])


if __name__ == "__main__":
    unittest.main()
