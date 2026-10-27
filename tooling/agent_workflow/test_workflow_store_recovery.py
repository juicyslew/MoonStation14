import json
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, StoreError, ProtocolError
from workflow_store_test_support import admission_records


class RecoveryStoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.store=WorkflowStore(Path(self.tmp.name)/"recovery.sqlite")
        req,self.run,self.task,event=admission_records("recovery_test", idempotency_key="recovery-test")
        event["event_id"]="event_recovery_test_task"
        self.store.submit(caller="worker",request=req,run=self.run,first_task=self.task,outbox_event=event,logical_task_key="check:required")
        self.incident={"contract_version":1,"incident_id":"incident_recovery_test","run_id":self.run["run_id"],"evaluation_generation":1,"code":"ADAPTER_FAULT","message":"saved recovery fixture","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}

    def decide_error(self):
        self.store.transition_attempt(self.run["run_id"],1,expected_sequence=0,phase="STOPPING",proposed_result="ERROR",incident=self.incident)
        self.store.transition_attempt(self.run["run_id"],1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="CONFIRMED",incident=self.incident)

    def tearDown(self):
        self.store.close(); self.tmp.cleanup()

    def proof(self, artifact_id, *, input_digest=None, kind="PROVENANCE"):
        artifact={"contract_version":1,"artifact_id":artifact_id,"run_id":self.run["run_id"],"type":kind,"path":"evidence/provenance.json","sha256":"a"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":self.task["task_id"],"producer_task_id":self.task["task_id"]}
        # Insert external attestation reference directly: this test is about storage's
        # proof-reference scope, not an OS/source-byte verifier.
        db=self.store._connect()
        try:
            db.execute("INSERT INTO artifacts(id,run_id,artifact_json) VALUES(?,?,?)",(artifact_id,self.run["run_id"],json.dumps({"artifact":artifact,"input_digest":input_digest or self.run["input_digest"]},separators=(",",":"))))
        finally: db.close()
        return artifact_id

    def recover(self, proof="artifact_provenance"):
        return self.store.recover_error(self.run["run_id"],expected_sequence=2,authorization={"kind":"POLICY","id":"recovery-policy"},fresh_input_proof=proof,boundary="native adapter",recovery_event={"event_id":"event_recovery_started_test"})

    def test_registered_provenance_recovers_and_exact_retry_is_durable(self):
        self.decide_error()
        self.proof("artifact_provenance")
        first=self.recover()
        self.assertEqual(first["generation"],2)
        self.assertEqual(self.store.get_run(self.run["run_id"])["run"]["current_attempt"],2)
        self.assertEqual(self.store.get_run(self.run["run_id"])["attempt"]["phase"],"QUEUED")
        self.assertEqual(self.recover(),first)
        db=self.store._connect()
        try:
            event=json.loads(db.execute("SELECT payload FROM events WHERE id='event_recovery_started_test'").fetchone()[0])
            self.assertEqual(event["status_intent"],"RECOVERY_STARTED")
            self.assertEqual(db.execute("SELECT COUNT(*) FROM reused_checks").fetchone()[0],0)
        finally: db.close()

    def test_missing_wrong_input_and_wrong_type_proofs_reject_without_generation(self):
        self.decide_error()
        with self.assertRaises(ProtocolError): self.recover("artifact_missing")
        self.proof("artifact_wrong_input",input_digest="0"*64)
        with self.assertRaises(ProtocolError): self.recover("artifact_wrong_input")
        self.proof("artifact_receipt",kind="CHECK_RECEIPT")
        with self.assertRaises(ProtocolError): self.recover("artifact_receipt")
        self.assertEqual(self.store.get_run(self.run["run_id"])["run"]["current_attempt"],1)
        self.assertEqual(self.store.get_run(self.run["run_id"])["run"]["sequence"],2)

    def test_unsettled_old_child_blocks_recovery(self):
        self.decide_error()
        self.proof("artifact_provenance")
        db=self.store._connect()
        try: db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','RUNNING') WHERE id=?",(self.task["task_id"],))
        finally: db.close()
        with self.assertRaises(StoreError): self.recover()
        self.assertEqual(self.store.get_run(self.run["run_id"])["run"]["current_attempt"],1)

    def test_public_recovery_reuses_real_pass_receipt_and_finishes_review_without_reexecution(self):
        run_id=self.run["run_id"]; task_id=self.task["task_id"]; input_digest=self.run["input_digest"]
        self.store.transition_attempt(run_id,1,expected_sequence=0,phase="CHECKING")
        identity={"pid":812,"created_ms":19}
        self.store.dispatch_task(task_id=task_id,event_id="event_native_recovery",process_identity=identity,claim_token="native-token")
        self.store.claim_native_task(task_id=task_id,run_id=run_id,evaluation_generation=1,assignment_generation=1,input_digest=input_digest,process_identity=identity,claim_token="native-token")
        receipt={"contract_version":1,"artifact_id":"artifact_real_check_receipt","run_id":run_id,"producer_task_id":task_id,"type":"CHECK_RECEIPT","path":"checks/result.json","sha256":"c"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":task_id}
        self.store.register_artifact(receipt,input_digest=input_digest)
        native=self.store.complete_native_task(task_id=task_id,run_id=run_id,evaluation_generation=1,assignment_generation=1,input_digest=input_digest,process_identity=identity,claim_token="native-token",outcome={"decision":"PASS","findings":[],"artifact_refs":[receipt["artifact_id"]]})
        before=self.store.get_run(run_id)
        db=self.store._connect()
        try: oldhash=db.execute("SELECT result_digest FROM tasks WHERE id=?",(task_id,)).fetchone()[0]
        finally: db.close()
        self.store.transition_attempt(run_id,1,expected_sequence=1,phase="STOPPING",proposed_result="ERROR",incident=self.incident)
        self.store.transition_attempt(run_id,1,expected_sequence=2,phase="DECIDED",terminal_result="ERROR",cleanup_state="CONFIRMED",incident=self.incident)
        error=self.store.get_run(run_id)["attempt"]
        provenance={"contract_version":1,"artifact_id":"artifact_fresh_provenance","run_id":run_id,"producer_task_id":task_id,"type":"PROVENANCE","path":"evidence/fresh.json","sha256":"d"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":task_id}
        self.store.register_artifact(provenance,input_digest=input_digest)
        recovered=self.store.recover_error(run_id,expected_sequence=3,authorization={"kind":"POLICY","id":"recovery-policy"},fresh_input_proof=provenance["artifact_id"],boundary="native adapter",recovery_event={"event_id":"event_recovery_started_test"},reusable_artifact_refs=[receipt["artifact_id"]])
        self.assertEqual(recovered["generation"],2)
        self.assertEqual(self.store.get_run(run_id)["attempt"]["phase"],"REVIEWING")
        review={"contract_version":1,"task_id":"task_recovery_review","run_id":run_id,"evaluation_generation":2,"kind":"REVIEW","dependency_ids":[task_id],"input_digest":input_digest,"state":"QUEUED","assignment_generation":1,"assigned_session_id":None,"deadline_ms":1,"result":None}
        self.store.add_task(review,logical_task_key="review:feature")
        envelope={"contract_version":1,"event_id":"event_recovery_review","run_id":run_id,"evaluation_generation":2,"task_id":review["task_id"],"assignment_generation":1,"input_digest":input_digest,"recipient_binding":{"session_id":"review-session","worktree":self.run["author"]["worktree"],"role":"reviewer"},"artifact_refs":[receipt["artifact_id"]],"deadline_ms":1,"required_next_action":"TASK_ASSIGNMENT"}
        self.store.dispatch_task(task_id=review["task_id"],event_id=envelope["event_id"],assigned_session_id="review-session",dispatch_envelope=envelope)
        self.store.claim_task(task_id=review["task_id"],run_id=run_id,evaluation_generation=2,assignment_generation=1,input_digest=input_digest,session_id="review-session")
        result={"contract_version":1,"task_id":review["task_id"],"run_id":run_id,"evaluation_generation":2,"assignment_generation":1,"input_digest":input_digest,"decision":"PASS","findings":[],"owner_followups":[],"inspected_refs":[receipt["artifact_id"]]}
        self.store.complete_task(result,session_id="review-session")
        self.store.transition_attempt(run_id,2,expected_sequence=4,phase="STOPPING",proposed_result="PASS")
        self.store.transition_attempt(run_id,2,expected_sequence=5,phase="DECIDED",terminal_result="PASS",cleanup_state="CONFIRMED")
        final=self.store.get_run(run_id)
        self.assertEqual(final["attempt"]["result"],"PASS")
        self.assertEqual(final["run"]["current_attempt"],2)
        self.assertEqual(before["attempt"]["phase"],"CHECKING")
        self.assertEqual(error["result"],"ERROR")
        db=self.store._connect()
        try:
            self.assertEqual(db.execute("SELECT result_digest FROM tasks WHERE id=?",(task_id,)).fetchone()[0],oldhash)
            link=db.execute("SELECT source_task_id,receipt_refs_json FROM reused_checks WHERE run_id=? AND target_generation=2",(run_id,)).fetchone()
            self.assertEqual(link[0],task_id); self.assertEqual(json.loads(link[1]),[receipt["artifact_id"]])
            self.assertEqual(db.execute("SELECT COUNT(*) FROM native_claims").fetchone()[0],1)
            self.assertEqual(db.execute("SELECT COUNT(*) FROM tasks WHERE generation=2 AND json_extract(task_json,'$.kind')='CHECK'").fetchone()[0],0)
            self.assertEqual(db.execute("SELECT COUNT(*) FROM deliveries WHERE event_id IN (SELECT id FROM events WHERE run_id=? AND generation=2)",(run_id,)).fetchone()[0],2)
        finally: db.close()

    def test_missing_check_obligation_survives_two_error_recoveries(self):
        run_id=self.run["run_id"]; task_id=self.task["task_id"]; input_digest=self.run["input_digest"]
        task_b={"contract_version":1,"task_id":"task_recovery_missing_b","run_id":run_id,"evaluation_generation":1,"kind":"CHECK","dependency_ids":[],"input_digest":input_digest,"state":"QUEUED","assignment_generation":1,"assigned_session_id":None,"deadline_ms":1,"result":None}
        self.store.add_task(task_b,logical_task_key="check:missing-B")
        self.store.transition_attempt(run_id,1,expected_sequence=0,phase="CHECKING")
        identity={"pid":813,"created_ms":20}
        self.store.dispatch_task(task_id=task_id,event_id="event_obligation_a",process_identity=identity,claim_token="native-a")
        self.store.claim_native_task(task_id=task_id,run_id=run_id,evaluation_generation=1,assignment_generation=1,input_digest=input_digest,process_identity=identity,claim_token="native-a")
        receipt={"contract_version":1,"artifact_id":"artifact_obligation_a_receipt","run_id":run_id,"producer_task_id":task_id,"type":"CHECK_RECEIPT","path":"checks/a.json","sha256":"e"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":task_id}
        self.store.register_artifact(receipt,input_digest=input_digest)
        self.store.complete_native_task(task_id=task_id,run_id=run_id,evaluation_generation=1,assignment_generation=1,input_digest=input_digest,process_identity=identity,claim_token="native-a",outcome={"decision":"PASS","findings":[],"artifact_refs":[receipt["artifact_id"]]})
        self.store.transition_attempt(run_id,1,expected_sequence=1,phase="STOPPING",proposed_result="ERROR",incident=self.incident)
        self.store.transition_attempt(run_id,1,expected_sequence=2,phase="DECIDED",terminal_result="ERROR",cleanup_state="CONFIRMED",incident=self.incident)
        proof={"contract_version":1,"artifact_id":"artifact_obligation_proof","run_id":run_id,"producer_task_id":task_id,"type":"PROVENANCE","path":"evidence/current.json","sha256":"f"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":task_id}
        self.store.register_artifact(proof,input_digest=input_digest)
        gen2=self.store.recover_error(run_id,expected_sequence=3,authorization={"kind":"POLICY","id":"p"},fresh_input_proof=proof["artifact_id"],boundary="first fault",recovery_event={"event_id":"event_obligation_recover_2"},reusable_artifact_refs=[receipt["artifact_id"]])
        self.assertEqual(gen2["generation"],2)
        self.assertEqual(self.store.get_run(run_id)["attempt"]["phase"],"QUEUED")
        with self.assertRaises(StoreError): self.store.transition_attempt(run_id,2,expected_sequence=4,phase="STOPPING",proposed_result="PASS")
        incident2=dict(self.incident,incident_id="incident_recovery_gen2",evaluation_generation=2,message="second infrastructure fault")
        self.store.transition_attempt(run_id,2,expected_sequence=4,phase="STOPPING",proposed_result="ERROR",incident=incident2)
        self.store.transition_attempt(run_id,2,expected_sequence=5,phase="DECIDED",terminal_result="ERROR",cleanup_state="CONFIRMED",incident=incident2)
        gen3=self.store.recover_error(run_id,expected_sequence=6,authorization={"kind":"POLICY","id":"p"},fresh_input_proof=proof["artifact_id"],boundary="second fault",recovery_event={"event_id":"event_obligation_recover_3"},reusable_artifact_refs=[receipt["artifact_id"]])
        self.assertEqual(gen3["generation"],3)
        self.assertEqual(self.store.get_run(run_id)["attempt"]["phase"],"QUEUED")
        self.assertEqual(self.store.get_run(run_id)["run"]["current_attempt"],3)
        with self.assertRaises(StoreError): self.store.transition_attempt(run_id,3,expected_sequence=7,phase="STOPPING",proposed_result="PASS")
        review={"contract_version":1,"task_id":"task_obligation_review","run_id":run_id,"evaluation_generation":3,"kind":"REVIEW","dependency_ids":[task_id],"input_digest":input_digest,"state":"QUEUED","assignment_generation":1,"assigned_session_id":None,"deadline_ms":1,"result":None}
        with self.assertRaises(StoreError): self.store.add_task(review,logical_task_key="review:missing")
        db=self.store._connect()
        try:
            required={r[0] for r in db.execute("SELECT logical_check_key FROM recovery_check_obligations WHERE run_id=? AND generation=3",(run_id,))}
            self.assertEqual(required,{"check:required","check:missing-B"})
            self.assertEqual(db.execute("SELECT COUNT(*) FROM native_claims").fetchone()[0],1)
            self.assertEqual(db.execute("SELECT COUNT(*) FROM tasks WHERE generation IN (2,3) AND json_extract(task_json,'$.kind')='CHECK'").fetchone()[0],0)
            self.assertEqual(db.execute("SELECT COUNT(*) FROM tasks WHERE id=? AND result_digest IS NOT NULL",(task_id,)).fetchone()[0],1)
        finally: db.close()
if __name__=="__main__": unittest.main()
