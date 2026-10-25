import json
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, ProtocolError, StaleCommand, IdempotencyConflict
from workflow_store_test_support import admission_records


class TerminalFinalityTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.store=WorkflowStore(Path(self.tmp.name)/"db.sqlite")
        req,run,self.check,event=admission_records("finality", idempotency_key="finality", deadline_ms=10)
        self.input=req["input_digest"]
        event["event_id"]="event_finality_initial"
        self.store.submit(caller="local",request=req,run=run,first_task=self.check,outbox_event=event,logical_task_key="check")

    def tearDown(self): self.tmp.cleanup()

    def set_task(self, task, decision="PASS", findings=None, refs=None, state="COMPLETED"):
        if task["task_id"]!="task_finality": self.store.add_task(task,logical_task_key=task["task_id"])
        saved=dict(task,state=state,result=None if state!="COMPLETED" else {"decision":decision,"findings":findings or [],"artifact_refs":refs or []})
        db=self.store._connect()
        try: db.execute("UPDATE tasks SET task_json=?,result_digest=? WHERE id=?",(json.dumps(saved,sort_keys=True,separators=(",",":")),"a"*64 if state=="COMPLETED" else None,task["task_id"]))
        finally: db.close()

    def artifact(self, aid, task_id="task_finality", kind="LOG"):
        if kind=="CHECK_RECEIPT":
            db=self.store._connect()
            try: db.execute("INSERT OR IGNORE INTO native_claims(task_id,process_identity,claim_token) VALUES(?,?,?)",(task_id,'{"fixture":true}',"fixture"))
            finally: db.close()
        art={"contract_version":1,"artifact_id":aid,"run_id":"run_finality","producer_task_id":task_id,"type":kind,"path":aid+".txt","sha256":"f"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":task_id}
        self.store.register_artifact(art,input_digest=self.input)
        return aid

    def incident(self, suffix="fault", task_id=None):
        result={"contract_version":1,"incident_id":"incident_"+suffix,"run_id":"run_finality","evaluation_generation":1,"code":"TEST_FAULT","message":"bounded fixture fault","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}
        if task_id: result["task_id"]=task_id
        return result

    def stop(self, proposed="PASS", closure=None, incident=None):
        self.store.transition_attempt("run_finality",1,expected_sequence=0,phase="STOPPING",proposed_result=proposed,closure=closure,incident=incident)

    def test_pass_terminal_creates_result_and_both_delivery_intents(self):
        ref=self.artifact("artifact_final_check","task_finality","CHECK_RECEIPT")
        self.set_task(self.check,refs=[ref])
        self.stop("PASS")
        result=self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="PASS",cleanup_state="NOT_REQUIRED")
        self.assertEqual(result["attempt"]["result"],"PASS")
        db=self.store._connect()
        try:
            self.assertEqual(db.execute("SELECT count(*) FROM deliveries WHERE event_id=?",(result["event_id"],)).fetchone()[0],2)
            event=json.loads(db.execute("SELECT payload FROM events WHERE id=?",(result["event_id"],)).fetchone()[0])
            self.assertEqual(event["next_action"],"WAIT_FOR_OWNER_TEST")
        finally: db.close()

    def test_actionable_fail_terminal_and_exact_command_receipt_conflict_cas(self):
        evidence=self.artifact("artifact_failure_evidence")
        finding={"criterion_id":"C1","category":"IMPLEMENTATION","message":"broken","location":"unit check","evidence_refs":[evidence],"required_change":"correct behavior"}
        self.set_task(self.check,"FAIL",[finding],[evidence])
        self.stop("FAIL")
        kw=dict(expected_sequence=1,phase="DECIDED",terminal_result="FAIL",cleanup_state="NOT_REQUIRED",command_id="finish-fail")
        first=self.store.transition_attempt("run_finality",1,**kw)
        self.assertEqual(self.store.transition_attempt("run_finality",1,**kw),first)
        with self.assertRaises(IdempotencyConflict): self.store.transition_attempt("run_finality",1,**dict(kw,terminal_result="ERROR"))
        with self.assertRaises(StaleCommand): self.store.transition_attempt("run_finality",1,expected_sequence=0,phase="DECIDED",terminal_result="FAIL",cleanup_state="NOT_REQUIRED",command_id="new-stale")

    def test_clean_error_requires_scoped_incident_and_settled_children(self):
        self.set_task(self.check,"ERROR")
        incident=self.incident("clean_error","task_finality")
        self.stop("ERROR",incident=incident)
        result=self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="NOT_REQUIRED",incident=incident)
        self.assertEqual(result["attempt"]["result"],"ERROR")

    def test_unresolved_child_requires_error_and_cannot_close_cleanly(self):
        active=dict(self.check,task_id="task_active")
        self.store.add_task(active,logical_task_key="active")
        db=self.store._connect()
        try: db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','DISPATCHING') WHERE id='task_active'")
        finally: db.close()
        incident=self.incident("unresolved","task_active")
        self.stop("ERROR",incident=incident)
        with self.assertRaises(StaleCommand): self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="CONFIRMED",incident=incident)
        result=self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="UNRESOLVED",incident=incident)
        self.assertEqual(result["attempt"]["cleanup_state"],"UNRESOLVED")

    def test_cleanup_fault_promotes_pass_to_error_and_records_incident(self):
        ref=self.artifact("artifact_promote_receipt","task_finality","CHECK_RECEIPT")
        self.set_task(self.check,refs=[ref])
        self.stop("PASS")
        db=self.store._connect()
        try: db.execute("INSERT INTO resources(claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof,request_digest) VALUES('resource_fixture','fixture-resource','run_finality',1,'task_finality',1,'{}','UNKNOWN',NULL,'x')")
        finally: db.close()
        incident=self.incident("promoted")
        result=self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="UNRESOLVED",incident=incident)
        self.assertEqual(result["attempt"]["result"],"ERROR")
        self.assertIsNotNone(self.store.get_incident("incident_promoted"))

    def test_wrong_typed_event_rolls_back_and_closed_control_stays_null(self):
        self.set_task(self.check,"ERROR")
        incident=self.incident("wrong_event","task_finality")
        self.stop("ERROR",incident=incident)
        with self.assertRaises(ProtocolError): self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="NOT_REQUIRED",incident=incident,result_event={"contract_version":1})
        got=self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="NOT_REQUIRED",incident=incident)
        self.assertEqual(got["attempt"]["phase"],"DECIDED")

    def test_closed_control_has_no_result_verdict(self):
        self.store.transition_attempt("run_finality",1,expected_sequence=0,phase="STOPPING",closure="CANCELLED")
        result=self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="CLOSED",cleanup_state="NOT_REQUIRED")
        self.assertEqual(result["attempt"]["phase"],"CLOSED")
        self.assertIsNone(result["attempt"]["result"])
        self.assertEqual(result["attempt"]["closure"],"CANCELLED")

    def test_terminal_write_delivery_fault_rolls_everything_back_and_closure_has_no_verdict(self):
        self.set_task(self.check,"ERROR"); self.stop("ERROR")
        incident=self.incident("atomic_error","task_finality")
        helper=self.store._enqueue_delivery_tx
        self.store._enqueue_delivery_tx=lambda *args,**kwargs: (_ for _ in ()).throw(RuntimeError("fixture rollback"))
        try:
            with self.assertRaises(RuntimeError): self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="NOT_REQUIRED",incident=incident)
        finally: self.store._enqueue_delivery_tx=helper
        self.assertEqual(self.store.get_run("run_finality")["attempt"]["phase"],"STOPPING")
        self.assertIsNone(self.store.get_incident("incident_atomic_error"))
        self.assertEqual(self.store.transition_attempt("run_finality",1,expected_sequence=1,phase="DECIDED",terminal_result="ERROR",cleanup_state="NOT_REQUIRED",incident=incident)["attempt"]["phase"],"DECIDED")


if __name__=="__main__": unittest.main()
