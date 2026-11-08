import json
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, ProtocolError, IdempotencyConflict, StaleCommand
from workflow_store_test_support import admission_records


class ArtifactRegistryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = WorkflowStore(Path(self.tmp.name) / "store.sqlite")

    def tearDown(self):
        self.tmp.cleanup()

    def test_artifact_metadata_replay_and_immutable_conflict(self):
        req=json.loads((Path(__file__).parents[2] / "docs/development-workflow-redesign/contracts/fixtures/positive.json").read_text(encoding="utf-8"))[0]
        sid=self.store.submission_id_for("preflight-caller","preflight-key")
        artifact = {"contract_version": 1, "artifact_id": "artifact_input", "run_id": None,
                    "submission_id": sid, "type": "SOURCE_MANIFEST", "path": "manifest.json",
                    "sha256": "a" * 64, "size_bytes": 12, "immutable": True,
                    "producer_kind": "SUBMISSION", "producer_id": sid, "producer_task_id": None}
        self.store.register_artifact(artifact, input_digest=req["input_digest"])
        self.assertEqual(self.store.register_artifact(artifact, input_digest=req["input_digest"])["disposition"], "REPLAYED")
        with self.assertRaises(IdempotencyConflict):
            self.store.register_artifact(dict(artifact, path="changed"), input_digest=req["input_digest"])

    def _submit_with_input(self, caller, key, run_id, worktree=None):
        req,run,task,event=admission_records(run_id[4:], idempotency_key=key)
        if worktree: req["source_worktree"]=worktree
        run["run_id"]=run_id
        self.store.submit(caller=caller,request=req,run=run,first_task=task,outbox_event=event,logical_task_key="initial")
        return req

    def test_submission_artifact_requires_actual_accepted_receipt_run_and_input(self):
        req=json.loads((Path(__file__).parents[2] / "docs/development-workflow-redesign/contracts/fixtures/positive.json").read_text(encoding="utf-8"))[0]
        req["idempotency_key"]="legit-key"; req["source_worktree"]="/work/legit"
        sid=self.store.submission_id_for("legit-caller","legit-key")
        artifact={"contract_version":1,"artifact_id":"artifact_legit_source","run_id":None,"submission_id":sid,"type":"SOURCE_MANIFEST","path":"source.json","sha256":"d"*64,"size_bytes":2,"immutable":True,"producer_kind":"SUBMISSION","producer_id":sid,"producer_task_id":None}
        # Public preflight flow registers immutable metadata before admission.
        self.store.register_artifact(artifact,input_digest=req["input_digest"])
        self._submit_with_input("legit-caller","legit-key","run_legit","/work/legit")
        self._submit_with_input("other-caller","other-key","run_other","/work/other")
        db=self.store._connect()
        try:
            self.store._require_artifacts(db,[artifact["artifact_id"]],"run_legit",input_digest=req["input_digest"])
            with self.assertRaises(ProtocolError): self.store._require_artifacts(db,[artifact["artifact_id"]],"run_other",input_digest=req["input_digest"])
            unknown=dict(artifact,artifact_id="artifact_unknown",submission_id="submit_not_saved",producer_id="submit_not_saved")
            self.store.register_artifact(unknown,input_digest=req["input_digest"])
            with self.assertRaises(ProtocolError): self.store._require_artifacts(db,[unknown["artifact_id"]],"run_legit",input_digest=req["input_digest"])
        finally: db.close()

    def test_task_artifact_input_is_inferred_and_mismatch_rejected(self):
        # The native fixture uses a saved producer task/claim and verifies exact input binding.
        native=NativeCompletionTests()
        native.setUp()
        try:
            artifact={"contract_version":1,"artifact_id":"artifact_task_input","run_id":"run_native","producer_task_id":"task_native","type":"LOG","path":"log.txt","sha256":"e"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":"task_native"}
            with self.assertRaises(ProtocolError): native.store.register_artifact(artifact,input_digest="0"*64)
            native.store.register_artifact(artifact)
            self.assertEqual(native.store.get_artifact("artifact_task_input")["input_digest"],native.input_digest)
        finally: native.tearDown()

    def test_unknown_reference_rejected(self):
        db = self.store._connect()
        try:
            with self.assertRaises(ProtocolError):
                self.store._require_artifacts(db, ["artifact_missing"], "run_missing")
        finally:
            db.close()


class NativeCompletionTests(ArtifactRegistryTests):
    def setUp(self):
        super().setUp()
        req,run,self.task,event = admission_records("native", idempotency_key="native-result", deadline_ms=100)
        self.input_digest = req["input_digest"]
        event["event_id"]="event_native_start"
        self.store.submit(caller="local",request=req,run=run,first_task=self.task,outbox_event=event,logical_task_key="check")
        self.identity = {"pid":123,"created_ms":42}
        self.store.dispatch_task(task_id="task_native",event_id="event_native_dispatch",process_identity=self.identity,claim_token="native-secret")
        args = dict(task_id="task_native",run_id="run_native",evaluation_generation=1,assignment_generation=1,input_digest=self.input_digest,process_identity=self.identity,claim_token="native-secret")
        self.store.claim_native_task(**args)
        self.args = args
        self.outcome = {"decision":"PASS","findings":[],"artifact_refs":[]}

    def _complete(self, **kwargs):
        args = dict(self.args, outcome=self.outcome)
        args.update(kwargs)
        return self.store.complete_native_task(**args)

    def test_native_pass_result_replay_and_changed_body_are_immutable(self):
        receipt={"contract_version":1,"artifact_id":"artifact_native_receipt","run_id":"run_native","producer_task_id":"task_native","type":"CHECK_RECEIPT","path":"checks/result.json","sha256":"c"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":"task_native"}
        self.store.register_artifact(receipt,input_digest=self.input_digest)
        self.outcome={"decision":"PASS","findings":[],"artifact_refs":["artifact_native_receipt"]}
        first = self._complete()
        self.assertEqual(first["disposition"], "ACCEPTED")
        self.assertEqual(self._complete()["disposition"], "REPLAYED")
        with self.assertRaises(IdempotencyConflict):
            finding={"criterion_id":"different","category":"IMPLEMENTATION","message":"different","location":"test","evidence_refs":["artifact_changed"],"required_change":"Use the changed result."}
            self._complete(outcome={"decision":"FAIL","findings":[finding],"artifact_refs":[]})
        db=self.store._connect()
        try:
            self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id LIKE 'event_task_completed_%'").fetchone()[0],1)
        finally: db.close()

    def test_native_fail_requires_actionable_findings(self):
        with self.assertRaises(ProtocolError):
            self._complete(outcome={"decision":"FAIL","findings":[],"artifact_refs":[]})
        vague={"criterion_id":"criterion","category":"IMPLEMENTATION","message":"broken","location":"check","evidence_refs":["artifact_missing"],"required_change":"  "}
        with self.assertRaises(ProtocolError):
            self._complete(outcome={"decision":"FAIL","findings":[vague],"artifact_refs":[]})
        finding={"criterion_id":"criterion","category":"IMPLEMENTATION","message":"check rejects invalid input","location":"validator","evidence_refs":["artifact_fail_evidence"],"required_change":"Reject the invalid input before task completion."}
        evidence={"contract_version":1,"artifact_id":"artifact_fail_evidence","run_id":"run_native","producer_task_id":"task_native","type":"LOG","path":"failure.log","sha256":"f"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":"task_native"}
        self.store.register_artifact(evidence,input_digest=self.input_digest)
        accepted=self._complete(outcome={"decision":"FAIL","findings":[finding],"artifact_refs":[]})
        self.assertEqual(accepted["disposition"],"ACCEPTED")

    def test_wrong_claim_bindings_and_late_new_result_reject(self):
        for change in ({"claim_token":"wrong"},{"process_identity":{"pid":124,"created_ms":42}},{"input_digest":"0"*64},{"evaluation_generation":2}):
            with self.subTest(change=change), self.assertRaises(StaleCommand): self._complete(**change)
        incident={"contract_version":1,"incident_id":"incident_native_stop","run_id":"run_native","evaluation_generation":1,"code":"STOP","message":"test stop context","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}
        self.store.transition_attempt("run_native",1,expected_sequence=0,phase="STOPPING",proposed_result="ERROR",incident=incident)
        with self.assertRaises(StaleCommand): self._complete()

    def test_native_error_requires_exact_bound_task_error_and_replays_exactly(self):
        outcome={"decision":"ERROR","findings":[],"artifact_refs":[]}
        with self.assertRaises(ProtocolError): self._complete(outcome={"decision":"ERROR","findings":[{"criterion_id":"fault","category":"FIDELITY","message":"fault","location":"check","evidence_refs":["artifact_noise"]}],"artifact_refs":[]})
        report={"contract_version":1,"task_id":"task_native","run_id":"run_native","evaluation_generation":1,"assignment_generation":1,"input_digest":self.input_digest,"error_code":"TOOL_FAILURE","message":"check process failed","artifact_refs":[]}
        bad=dict(report,run_id="run_other")
        with self.assertRaises(ProtocolError): self._complete(outcome=outcome,error_report=bad)
        accepted=self._complete(outcome=outcome,error_report=report)
        self.assertEqual(accepted["disposition"],"ACCEPTED")
        self.assertEqual(self._complete(outcome=outcome,error_report=report)["disposition"],"REPLAYED")
        with self.assertRaises(IdempotencyConflict): self._complete(outcome=outcome,error_report=dict(report,message="changed report"))

    def test_native_result_cannot_complete_review_task(self):
        db=self.store._connect()
        try:
            db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.kind','REVIEW') WHERE id='task_native'")
        finally: db.close()
        with self.assertRaises(ProtocolError): self._complete()


if __name__ == "__main__":
    unittest.main()
