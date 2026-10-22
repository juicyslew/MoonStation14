import json
import tempfile
import unittest
from pathlib import Path

from workflow_store import WorkflowStore, StaleCommand, ProtocolError, IdempotencyConflict, SubmissionLaneBusy
from workflow_store_test_support import admission_records


class AttemptLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.store=WorkflowStore(Path(self.tmp.name)/"db.sqlite")
        req,run,self.task,event=admission_records("attempt", idempotency_key="attempts", deadline_ms=100)
        self.input=req["input_digest"]
        event["event_id"]="event_attempt_initial"
        self.store.submit(caller="local",request=req,run=run,first_task=self.task,outbox_event=event,logical_task_key="check")
        self.identity={"pid":123,"created_ms":42}; self.token="native-secret"
    def tearDown(self): self.tmp.cleanup()

    def dispatch_claim(self, task_id="task_attempt"):
        args=dict(task_id=task_id,run_id="run_attempt",evaluation_generation=1,assignment_generation=1,input_digest=self.input,process_identity=self.identity,claim_token=self.token)
        self.store.dispatch_task(task_id=task_id,event_id="event_dispatch_"+task_id,process_identity=self.identity,claim_token=self.token)
        self.store.claim_native_task(**args)
        return args

    def receipt(self, aid, taskid):
        art={"contract_version":1,"artifact_id":aid,"run_id":"run_attempt","producer_task_id":taskid,"type":"CHECK_RECEIPT","path":aid+".json","sha256":"c"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":taskid}
        self.store.register_artifact(art,input_digest=self.input)
        return aid

    def test_global_submission_lane_is_transactional_and_releases_only_terminal_attempts(self):
        def add_run(prefix, worktree):
            req,run,task,event=admission_records(prefix,idempotency_key=prefix,deadline_ms=100)
            req['sprint_id']='sprint-'+prefix
            req['author']={'session_id':'session-'+prefix,'worktree':worktree,'role':'author'}
            req['source_worktree']=worktree
            run['sprint_id']=req['sprint_id']; run['author']=req['author']
            self.store.submit(caller='caller-'+prefix,request=req,run=run,first_task=task,outbox_event=event,logical_task_key='check')
            return run['run_id']

        other=add_run('other-lane','agent-02')
        args=dict(run_id='run_attempt',generation=1,expected_sequence=0,phase='CHECKING',command_id='lane-owner-checking')
        admitted=self.store.transition_attempt(**args)
        self.assertEqual(self.store.transition_attempt(**args),admitted,'exact transition replay stays idempotent')
        self.store.assert_submission_lane('run_attempt',1)
        with self.assertRaises(SubmissionLaneBusy) as busy:
            self.store.transition_attempt(other,1,expected_sequence=0,phase='CHECKING',command_id='lane-other-checking')
        self.assertIsInstance(busy.exception,SubmissionLaneBusy)

        claim=self.dispatch_claim()
        ref=self.receipt('artifact_lane_pass','task_attempt')
        self.store.complete_native_task(**claim,outcome={'decision':'PASS','findings':[],'artifact_refs':[ref]})
        self.store.transition_attempt('run_attempt',1,expected_sequence=1,phase='REVIEWING')
        with self.assertRaises(SubmissionLaneBusy): self.store.transition_attempt(other,1,expected_sequence=0,phase='CHECKING')
        self.store.transition_attempt('run_attempt',1,expected_sequence=2,phase='STOPPING',closure='CANCELLED')
        with self.assertRaises(SubmissionLaneBusy): self.store.transition_attempt(other,1,expected_sequence=0,phase='CHECKING')
        self.store.transition_attempt('run_attempt',1,expected_sequence=3,phase='CLOSED',cleanup_state='NOT_REQUIRED')
        self.store.transition_attempt(other,1,expected_sequence=0,phase='CHECKING')
        self.assertEqual(self.store.get_run(other)['attempt']['phase'],'CHECKING')

        third=add_run('decided-lane','agent-03')
        incident={'contract_version':1,'incident_id':'incident_lane_error','run_id':other,'evaluation_generation':1,
            'code':'PROVIDER_PAUSED','message':'offline fixture','recovery_owner':'SYSTEM','state':'OPEN',
            'owner_action_required':False,'artifact_refs':[]}
        self.store.transition_attempt(other,1,expected_sequence=1,phase='STOPPING',proposed_result='ERROR',incident=incident)
        with self.assertRaises(SubmissionLaneBusy): self.store.transition_attempt(third,1,expected_sequence=0,phase='CHECKING')
        self.store.transition_attempt(other,1,expected_sequence=2,phase='DECIDED',terminal_result='ERROR',incident=incident,cleanup_state='NOT_REQUIRED')
        self.store.transition_attempt(third,1,expected_sequence=0,phase='CHECKING')
        self.assertEqual(self.store.get_run(third)['attempt']['phase'],'CHECKING')

        unresolved=add_run('unresolved-lane','agent-04')
        db=self.store._connect()
        try: task_id=db.execute('SELECT id FROM tasks WHERE run_id=?',(third,)).fetchone()[0]
        finally: db.close()
        self.store.dispatch_task(task_id=task_id,event_id='event_dispatch_'+task_id,process_identity=self.identity,claim_token=self.token)
        claim_args=dict(task_id=task_id,run_id=third,evaluation_generation=1,assignment_generation=1,input_digest=self.input,process_identity=self.identity,claim_token=self.token)
        self.store.claim_native_task(**claim_args)
        unresolved_incident={'contract_version':1,'incident_id':'incident_unresolved_lane','run_id':third,'evaluation_generation':1,
            'code':'CLEANUP_UNKNOWN','message':'unresolved contained fixture','recovery_owner':'SYSTEM','state':'OPEN',
            'owner_action_required':False,'artifact_refs':[]}
        self.store.transition_attempt(third,1,expected_sequence=1,phase='STOPPING',proposed_result='ERROR',incident=unresolved_incident)
        self.store.transition_attempt(third,1,expected_sequence=2,phase='DECIDED',terminal_result='ERROR',incident=unresolved_incident,cleanup_state='UNRESOLVED')
        with self.assertRaises(SubmissionLaneBusy): self.store.transition_attempt(unresolved,1,expected_sequence=0,phase='CHECKING')
        pending=self.store.get_task(task_id)
        cleanup_art={'contract_version':1,'artifact_id':'artifact_unresolved_lane_cleanup','run_id':third,
            'producer_task_id':task_id,'type':'LOG','path':'unresolved-cleanup.log','sha256':'e'*64,
            'size_bytes':1,'immutable':True,'producer_kind':'TASK','producer_id':task_id}
        self.store.register_artifact(cleanup_art,input_digest=pending['input_digest'])
        self.store.confirm_task_cancelled(task_id=task_id,run_id=third,evaluation_generation=1,
            assignment_generation=pending['assignment_generation'],input_digest=pending['input_digest'],
            cleanup_artifact_ref='artifact_unresolved_lane_cleanup')
        self.store.transition_attempt(unresolved,1,expected_sequence=0,phase='CHECKING')
        self.assertEqual(self.store.get_run(unresolved)['attempt']['phase'],'CHECKING')

    def test_held_or_unknown_resources_block_other_run_until_existing_release_proof(self):
        other_req,other_run,other_task,other_event=admission_records('resource-lane-other',idempotency_key='resource-lane-other',deadline_ms=100)
        other_req['sprint_id']='other-sprint';other_req['author']={'session_id':'other-session','worktree':'other-worktree','role':'author'};other_req['source_worktree']='other-worktree'
        other_run['sprint_id']=other_req['sprint_id'];other_run['author']=other_req['author']
        self.store.submit(caller='other-author',request=other_req,run=other_run,first_task=other_task,outbox_event=other_event,logical_task_key='check')
        self.dispatch_claim()
        resource=self.store.acquire_resource(resource_key='lane-held-resource',run_id='run_attempt',generation=1,
            task_id='task_attempt',assignment_generation=1,identity_proof=self.identity)
        self.store.mark_resource_unknown(resource['claim_id'],identity_proof=self.identity)
        with self.assertRaises(SubmissionLaneBusy): self.store.transition_attempt(other_run['run_id'],1,expected_sequence=0,phase='CHECKING')
        cleanup='artifact_lane_resource_cleanup'
        self.store.register_artifact({'contract_version':1,'artifact_id':cleanup,'run_id':'run_attempt',
            'producer_task_id':'task_attempt','type':'LOG','path':'lane-cleanup.log','sha256':'f'*64,
            'size_bytes':1,'immutable':True,'producer_kind':'TASK','producer_id':'task_attempt'},input_digest=self.input)
        self.store.release_resource('lane-held-resource',claim_id=resource['claim_id'],run_id='run_attempt',generation=1,
            task_id='task_attempt',assignment_generation=1,identity_proof=self.identity,cleanup_proof=cleanup)
        self.store.transition_attempt(other_run['run_id'],1,expected_sequence=0,phase='CHECKING')
        self.assertEqual(self.store.get_run(other_run['run_id'])['attempt']['phase'],'CHECKING')

    def test_checking_while_initial_check_queued_and_reviewing_requires_pass_receipt(self):
        self.store.transition_attempt("run_attempt",1,expected_sequence=0,phase="CHECKING")
        with self.assertRaises(StaleCommand): self.store.transition_attempt("run_attempt",1,expected_sequence=1,phase="REVIEWING")
        args=self.dispatch_claim()
        ref=self.receipt("artifact_attempt_receipt","task_attempt")
        self.store.complete_native_task(**args,outcome={"decision":"PASS","findings":[],"artifact_refs":[ref]})
        self.store.transition_attempt("run_attempt",1,expected_sequence=1,phase="REVIEWING")

    def test_stop_revokes_active_and_cancels_never_started_and_closes_admission(self):
        args=self.dispatch_claim()
        queued=dict(self.task,task_id="task_never")
        self.store.add_task(queued,logical_task_key="never")
        incident={"contract_version":1,"incident_id":"incident_stop_test","run_id":"run_attempt","evaluation_generation":1,"code":"STOP","message":"bounded test context","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}
        self.store.transition_attempt("run_attempt",1,expected_sequence=0,phase="STOPPING",proposed_result="ERROR",incident=incident)
        with self.assertRaises(StaleCommand): self.store.complete_native_task(**args,outcome={"decision":"PASS","findings":[],"artifact_refs":[]})
        with self.assertRaises(StaleCommand): self.store.add_task(dict(self.task,task_id="task_late"),logical_task_key="late")
        db=self.store._connect()
        try:
            states={r[0]:json.loads(r[1])["state"] for r in db.execute("SELECT id,task_json FROM tasks")}
            self.assertEqual(states["task_attempt"],"CANCEL_REQUESTED"); self.assertEqual(states["task_never"],"CANCELLED")
            self.assertIsNotNone(db.execute("SELECT proof_json FROM task_cancellations WHERE task_id='task_never'").fetchone())
            self.assertEqual(db.execute("SELECT count(*) FROM outbox WHERE event_id LIKE 'event_cancel_task_%'").fetchone()[0],1)
        finally: db.close()

    def test_registered_cleanup_proof_confirms_and_replays_exactly(self):
        args=self.dispatch_claim()
        incident={"contract_version":1,"incident_id":"incident_cancel_test","run_id":"run_attempt","evaluation_generation":1,"code":"STOP","message":"test","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}
        self.store.transition_attempt("run_attempt",1,expected_sequence=0,phase="STOPPING",proposed_result="ERROR",incident=incident)
        log={"contract_version":1,"artifact_id":"artifact_cancel_log","run_id":"run_attempt","producer_task_id":"task_attempt","type":"LOG","path":"cancel.log","sha256":"d"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":"task_attempt"}
        self.store.register_artifact(log,input_digest=self.input)
        kw={"task_id":"task_attempt","run_id":"run_attempt","evaluation_generation":1,"assignment_generation":1,"input_digest":self.input,"cleanup_artifact_ref":"artifact_cancel_log"}
        self.assertEqual(self.store.confirm_task_cancelled(**kw)["disposition"],"CONFIRMED")
        self.assertEqual(self.store.confirm_task_cancelled(**kw)["disposition"],"REPLAYED")
        with self.assertRaises(IdempotencyConflict): self.store.confirm_task_cancelled(**dict(kw,cleanup_artifact_ref="artifact_missing"))
        db=self.store._connect()
        try:
            task=json.loads(db.execute("SELECT task_json FROM tasks WHERE id='task_attempt'").fetchone()[0])
            proof=json.loads(db.execute("SELECT proof_json FROM task_cancellations WHERE task_id='task_attempt'").fetchone()[0])
            self.assertEqual(task["state"],"CANCELLED"); self.assertEqual(proof["artifact_ref"],"artifact_cancel_log")
        finally: db.close()

    def test_wrong_assignment_and_cross_task_proof_reject_without_mutation(self):
        self.dispatch_claim()
        incident={"contract_version":1,"incident_id":"incident_wrong_cancel","run_id":"run_attempt","evaluation_generation":1,"code":"STOP","message":"test","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}
        self.store.transition_attempt("run_attempt",1,expected_sequence=0,phase="STOPPING",proposed_result="ERROR",incident=incident)
        self.assertRaises(StaleCommand,self.store.confirm_task_cancelled,task_id="task_attempt",run_id="run_attempt",evaluation_generation=1,assignment_generation=2,input_digest=self.input,cleanup_artifact_ref="artifact_missing")
        self.receipt("artifact_other_task","task_other") if False else None
        with self.assertRaises(ProtocolError): self.store.confirm_task_cancelled(task_id="task_attempt",run_id="run_attempt",evaluation_generation=1,assignment_generation=1,input_digest=self.input,cleanup_artifact_ref="artifact_missing")

    def test_held_resource_blocks_cancellation_until_resource_api_releases_it(self):
        self.dispatch_claim()
        owned=self.store.acquire_resource(resource_key="owned-resource",run_id="run_attempt",generation=1,task_id="task_attempt",assignment_generation=1,identity_proof=self.identity)
        incident={"contract_version":1,"incident_id":"incident_held_cancel","run_id":"run_attempt","evaluation_generation":1,"code":"STOP","message":"test","recovery_owner":"SYSTEM","state":"OPEN","owner_action_required":False,"artifact_refs":[]}
        self.store.transition_attempt("run_attempt",1,expected_sequence=0,phase="STOPPING",proposed_result="ERROR",incident=incident)
        log={"contract_version":1,"artifact_id":"artifact_release_log","run_id":"run_attempt","producer_task_id":"task_attempt","type":"LOG","path":"release.log","sha256":"e"*64,"size_bytes":1,"immutable":True,"producer_kind":"TASK","producer_id":"task_attempt"}
        self.store.register_artifact(log,input_digest=self.input)
        with self.assertRaises(StaleCommand): self.store.confirm_task_cancelled(task_id="task_attempt",run_id="run_attempt",evaluation_generation=1,assignment_generation=1,input_digest=self.input,cleanup_artifact_ref="artifact_release_log")
        self.store.release_resource("owned-resource",claim_id=owned["claim_id"],run_id="run_attempt",generation=1,task_id="task_attempt",assignment_generation=1,identity_proof=self.identity,cleanup_proof="artifact_release_log")
        self.assertEqual(self.store.confirm_task_cancelled(task_id="task_attempt",run_id="run_attempt",evaluation_generation=1,assignment_generation=1,input_digest=self.input,cleanup_artifact_ref="artifact_release_log")["state"],"CANCELLED")


if __name__=="__main__": unittest.main()
