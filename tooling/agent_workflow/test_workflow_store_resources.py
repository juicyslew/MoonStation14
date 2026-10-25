import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, StaleCommand
from workflow_store_test_support import admission_records


class ResourceClaimTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.store = WorkflowStore(Path(self.tmp.name) / "db.sqlite")
        self.addCleanup(self.store.close)
        self.fixture = type("AdmissionFixture", (), {})()
        self.fixture.req, self.fixture.run, self.fixture.task, self.fixture.event = admission_records("core", idempotency_key="core")
        self.store.submit(caller="author-caller", request=self.fixture.req, run=self.fixture.run,
                          first_task=self.fixture.task, outbox_event=self.fixture.event, logical_task_key="initial")
        self.identity = {"pid": 123, "created_ms": 456}
        self.store.dispatch_task(task_id="task_core", event_id="event_resource",
                                 process_identity=self.identity, claim_token="native-token")
        self.store.claim_native_task(task_id="task_core", run_id="run_core", evaluation_generation=1,
                                     assignment_generation=1, input_digest=self.fixture.req["input_digest"],
                                     process_identity=self.identity, claim_token="native-token")

    def acquire(self):
        return self.store.acquire_resource(resource_key="global-key", run_id="run_core", generation=1,
                                           task_id="task_core", assignment_generation=1,
                                           identity_proof=self.identity)

    def test_acquire_replay_and_exclusive_held_claim(self):
        first = self.acquire()
        self.assertEqual(self.acquire(), {"disposition": "REPLAYED", "claim_id": first["claim_id"]})
        with self.assertRaises(Exception):
            self.store.acquire_resource(resource_key="global-key", run_id="run_core", generation=1,
                                        task_id="task_core", assignment_generation=2,
                                        identity_proof=self.identity)

    def test_unknown_claim_persists_across_reopen_and_fences_callback(self):
        claim = self.acquire()["claim_id"]
        self.store.mark_resource_unknown(claim, identity_proof=self.identity)
        path = self.store.path
        self.store.close()
        self.store = WorkflowStore(path)
        with self.assertRaises(Exception):
            self.store.acquire_resource(resource_key="global-key", run_id="run_core", generation=1,
                task_id="task_core", assignment_generation=1, identity_proof={"pid":124,"created_ms":456})
        with self.assertRaises(StaleCommand):
            self.store.mark_resource_unknown("not-a-claim", identity_proof=self.identity)

    def register_log(self, artifact_id, task_id="task_core"):
        self.store.register_artifact({"contract_version":1,"artifact_id":artifact_id,"run_id":"run_core",
            "type":"LOG","path":"build/log.txt","sha256":"a"*64,"size_bytes":1,"immutable":True,
            "producer_kind":"TASK","producer_id":task_id,"producer_task_id":task_id},
            input_digest=self.fixture.req["input_digest"])

    def test_release_proof_binding_history_and_reacquire_same_key(self):
        first=self.acquire()["claim_id"]
        self.register_log("artifact_cleanup")
        args=dict(claim_id=first,run_id="run_core",generation=1,task_id="task_core",assignment_generation=1,
                  identity_proof=self.identity,cleanup_proof="artifact_cleanup")
        self.assertEqual(self.store.release_resource("global-key",**args)["state"],"RELEASED")
        self.assertEqual(self.store.release_resource("global-key",**args)["disposition"],"REPLAYED")
        task=dict(self.fixture.task,task_id="task_second")
        self.store.add_task(task,logical_task_key="second")
        self.store.dispatch_task(task_id="task_second",event_id="event_second",process_identity=self.identity,claim_token="second-token")
        self.store.claim_native_task(task_id="task_second",run_id="run_core",evaluation_generation=1,
            assignment_generation=1,input_digest=self.fixture.req["input_digest"],process_identity=self.identity,claim_token="second-token")
        second=self.store.acquire_resource(resource_key="global-key",run_id="run_core",generation=1,
            task_id="task_second",assignment_generation=1,identity_proof=self.identity)
        self.assertNotEqual(first,second["claim_id"])
        with self.assertRaises(Exception): self.store.release_resource("global-key",**dict(args,claim_id=second["claim_id"],task_id="task_second"))
        self.assertEqual([r["state"] for r in self.store.get_resource_history("global-key")],["RELEASED","HELD"])
        with self.assertRaises(Exception):
            self.store.release_resource("global-key",claim_id=second["claim_id"],run_id="run_core",generation=1,
                task_id="task_second",assignment_generation=1,identity_proof=self.identity,cleanup_proof="artifact_cleanup")

    def test_wrong_process_identity_rejected(self):
        with self.assertRaises(StaleCommand):
            self.store.acquire_resource(resource_key="other",run_id="run_core",generation=1,
                task_id="task_core",assignment_generation=1,identity_proof={"pid":999,"created_ms":456})


if __name__ == "__main__":
    unittest.main()
