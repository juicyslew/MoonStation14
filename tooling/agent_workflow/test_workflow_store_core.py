import json
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, ProtocolError, StaleCommand
from workflow_store_test_support import admission_records


class CoreAdmissionClaimTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.store=WorkflowStore(Path(self.tmp.name)/"db.sqlite")
        self.req,self.run,self.task,self.event=admission_records("core", idempotency_key="core")
    def tearDown(self): self.tmp.cleanup()
    def admit(self): return self.store.submit(caller="author-caller",request=self.req,run=self.run,first_task=self.task,outbox_event=self.event,logical_task_key="initial")
    def test_accept_and_exact_idempotent_replay(self):
        self.assertEqual(self.admit()["status"],"ACCEPTED"); self.assertEqual(self.admit()["status"],"ACCEPTED")
    def test_admission_rejects_each_binding_mismatch_without_run(self):
        for field,value in (("plan_digest","0"*64),("policy_digest","0"*64),("sprint_id","wrong")):
            with self.subTest(field=field):
                run=dict(self.run,**{field:value})
                with self.assertRaises(ProtocolError): self.store.submit(caller="x",request=self.req,run=run,first_task=self.task,outbox_event=self.event,logical_task_key="k")
                self.assertIsNone(self.store.get_run("run_core"))
    def test_initial_task_and_internal_event_exact_shape(self):
        for changes in ({"kind":"REVIEW"},{"state":"DISPATCHING"},{"assigned_session_id":"fake"},{"assignment_generation":2},{"dependency_ids":["task_x"]}):
            with self.subTest(changes=changes):
                with self.assertRaises((ProtocolError,StaleCommand)): self.store.submit(caller="x",request=self.req,run=self.run,first_task=dict(self.task,**changes),outbox_event=self.event,logical_task_key="k")
        with self.assertRaises(ProtocolError): self.store.submit(caller="x",request=self.req,run=self.run,first_task=self.task,outbox_event=dict(self.event,recipient_binding={}),logical_task_key="k")
    def test_malformed_request_rejection_is_persisted_and_conflict_fenced(self):
        malformed={"idempotency_key":"bad","contract_version":1,"unknown":True}
        response={"contract_version":1,"status":"REJECTED","result":"FAIL","code":"INVALID","findings":[{"criterion_id":"input","category":"IMPLEMENTATION","message":"Malformed input","location":"request","evidence_refs":["request"],"required_change":"Correct the request fields."}],"next_action":"FIX_FINDINGS"}
        self.assertEqual(self.store.record_rejection(caller="c",idempotency_key="bad",raw_request=json.dumps(malformed),response=response),response)
        self.assertEqual(self.store.get_submission("c","bad"),response)
        with self.assertRaises(Exception): self.store.record_rejection(caller="c",idempotency_key="bad",raw_request=json.dumps(dict(malformed,unknown=False)),response=response)
    def test_check_dispatch_requires_program_identity_and_claim_only_native_path(self):
        self.admit()
        with self.assertRaises(ProtocolError): self.store.dispatch_task(task_id="task_core",event_id="event_native",assigned_session_id="model")
        identity={"pid":4,"created_ms":1}
        self.store.dispatch_task(task_id="task_core",event_id="event_native",process_identity=identity,claim_token="secret")
        with self.assertRaises(ProtocolError): self.store.claim_task(task_id="task_core",run_id="run_core",evaluation_generation=1,assignment_generation=1,input_digest=self.task["input_digest"],session_id="model")
        def native(**kw): return self.store.claim_native_task(task_id="task_core",run_id="run_core",evaluation_generation=1,assignment_generation=1,input_digest=self.task["input_digest"],**kw)
        with self.assertRaises(StaleCommand): native(process_identity={"pid":5,"created_ms":1},claim_token="secret")
        self.assertEqual(native(process_identity=identity,claim_token="secret")["disposition"],"ACCEPTED")
        self.assertEqual(native(process_identity=identity,claim_token="secret")["disposition"],"REPLAYED")
    def test_native_claim_checks_all_saved_bindings_before_exact_replay(self):
        identity={"pid":4,"created_ms":1}; self.admit(); self.store.dispatch_task(task_id="task_core",event_id="event_native",process_identity=identity,claim_token="token")
        kwargs=dict(task_id="task_core",run_id="run_core",evaluation_generation=1,assignment_generation=1,input_digest=self.task["input_digest"],process_identity=identity,claim_token="token")
        self.store.claim_native_task(**kwargs)
        for change in ({"run_id":"run_wrong"},{"evaluation_generation":2},{"assignment_generation":2},{"input_digest":"0"*64},{"claim_token":"wrong"},{"process_identity":{"pid":5,"created_ms":1}}):
            with self.subTest(change=change), self.assertRaises(StaleCommand): self.store.claim_native_task(**dict(kwargs,**change))
    def test_native_claim_cannot_be_made_before_saved_dispatch(self):
        self.admit()
        with self.assertRaises(StaleCommand): self.store.claim_native_task(task_id="task_core",run_id="run_core",evaluation_generation=1,assignment_generation=1,input_digest=self.task["input_digest"],process_identity={"pid":4,"created_ms":1},claim_token="token")
    def test_dispatch_rejects_unmet_dependency_atomically(self):
        self.admit()
        dep=dict(self.task,task_id="task_child",dependency_ids=["task_core"])
        # Create a second check with an existing dependency; dependency state is still QUEUED.
        self.store.add_task(dep,logical_task_key="dependent")
        with self.assertRaises(StaleCommand): self.store.dispatch_task(task_id="task_child",event_id="event_child",process_identity={"pid":4,"created_ms":1},claim_token="token")
        db=self.store._connect(); state=json.loads(db.execute("SELECT task_json FROM tasks WHERE id='task_child'").fetchone()[0])["state"]; n=db.execute("SELECT count(*) FROM events WHERE id='event_child'").fetchone()[0]; db.close()
        self.assertEqual((state,n),("QUEUED",0))
    def test_dispatch_is_current_phase_fenced(self):
        self.admit()
        incident={"contract_version":1,"incident_id":"incident_core_stop","run_id":"run_core","evaluation_generation":1,"code":"STOP","message":"test stop context","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}
        self.store.transition_attempt("run_core",1,expected_sequence=0,phase="STOPPING",proposed_result="ERROR",incident=incident)
        with self.assertRaises(StaleCommand): self.store.dispatch_task(task_id="task_core",event_id="event_stop",process_identity={"pid":4,"created_ms":1},claim_token="token")
    def test_review_claim_requires_saved_recipient_and_replays_only_exact_identity(self):
        self.admit()
        db=self.store._connect(); db.execute("UPDATE attempts SET attempt_json=json_set(attempt_json,'$.phase','REVIEWING') WHERE run_id='run_core' AND generation=1"); db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.kind','REVIEW') WHERE id='task_core'"); db.close()
        envelope={"contract_version":1,"event_id":"event_review","run_id":"run_core","evaluation_generation":1,"task_id":"task_core","assignment_generation":1,"input_digest":self.task["input_digest"],"recipient_binding":{"session_id":"review-session","worktree":self.req["source_worktree"],"role":"reviewer"},"artifact_refs":[],"deadline_ms":1,"required_next_action":"TASK_ASSIGNMENT"}
        self.store.dispatch_task(task_id="task_core",event_id="event_review",assigned_session_id="review-session",dispatch_envelope=envelope)
        args=dict(task_id="task_core",run_id="run_core",evaluation_generation=1,assignment_generation=1,input_digest=self.task["input_digest"])
        with self.assertRaises(StaleCommand): self.store.claim_task(**args,session_id="wrong")
        self.assertEqual(self.store.claim_task(**args,session_id="review-session")["disposition"],"ACCEPTED")
        self.assertEqual(self.store.claim_task(**args,session_id="review-session")["disposition"],"REPLAYED")
    def test_review_claim_requires_saved_dispatch_assignment_and_exact_replay(self):
        self.admit(); self.store.transition_attempt("run_core",1,expected_sequence=0,phase="CHECKING")
        # A review cannot be admitted before checks pass.
        review=dict(self.task,task_id="task_review",kind="REVIEW",assigned_session_id=None)
        with self.assertRaises(StaleCommand): self.store.add_task(review,logical_task_key="review")
        # A CHECK task cannot be claimed via the AI-review API regardless of caller session.
        self.store.dispatch_task(task_id="task_core",event_id="event_native",process_identity={"pid":4,"created_ms":1},claim_token="token")
        with self.assertRaises(ProtocolError): self.store.claim_task(task_id="task_core",run_id="run_core",evaluation_generation=1,assignment_generation=1,input_digest=self.task["input_digest"],session_id="pid")


if __name__=="__main__": unittest.main()
