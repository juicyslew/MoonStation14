import unittest, tempfile, time, hashlib, json
from types import SimpleNamespace
from pathlib import Path

import workflow_run_control
import workflow_store
import workflow_coordinator
import workflow_native_execution
from test_workflow_native_execution import admit_native_fixture


class RunControlTests(unittest.TestCase):
    def test_owner_cancellation_commits_closure_and_returns_truthful_waiting(self):
        class Store:
            def __init__(self):
                self.calls = []
            def get_run(self, run_id):
                return {"run": {"author": {"worktree": "owner-tree"}, "sequence": 7},
                        "attempt": {"generation": 2, "phase": "CHECKING"}}
            def transition_attempt(self, *args, **kwargs):
                self.calls.append((args, kwargs))
                return {"event_id": "transition-stopping", "phase": "STOPPING"}
            def list_tasks(self, *args):
                return [{"task_id": "child", "state": "CANCEL_REQUESTED"}]
            def get_task_resources(self, task_id): return []
        store = Store(); notified = []
        service = workflow_run_control.ServiceRunControl(store, lambda: notified.append(True))
        store.get_run = lambda run_id: {"run": {"author": {"worktree": "owner-tree"}, "sequence": 7},
                        "attempt": {"generation": 2, "phase": "CHECKING"}}
        store.list_tasks = lambda *a: []
        store.get_task_resources = lambda *a: []
        store.transition_attempt = lambda *a, **kw: {"event_id": "event_cancel", "phase": "STOPPING"}
        result = service.cancel({"contract_version":1,"run_id": "run_test", "evaluation_generation":2,"expected_sequence": 7,
                                 "command_id": "cancel-1", "actor":"OWNER", "reason": "CANCELLED"},
                                SimpleNamespace(worktree="owner-tree", caller="owner"))
        self.assertEqual(result["state"], "CONFIRMED")
        self.assertEqual(notified, [True])

    def test_rejects_untrusted_fields_and_wrong_owner_scope(self):
        class Store:
            def get_run(self, run_id):
                return {"run": {"author": {"worktree": "other"}}, "attempt": {"generation": 1}}
            def transition_attempt(self, *args, **kwargs): raise AssertionError("must not transition")
        service = workflow_run_control.ServiceRunControl(Store())
        cmd = {"contract_version":1,"run_id": "run_test", "evaluation_generation":1,"expected_sequence": 1, "command_id": "c", "actor":"OWNER", "reason": "CANCELLED"}
        with self.assertRaises(PermissionError): service.cancel(cmd, SimpleNamespace(worktree="owner", caller="o"))
        with self.assertRaises(workflow_store.ProtocolError):
            service.cancel(dict(cmd, extra="SYSTEM"), SimpleNamespace(worktree="other", caller="o"))


class RunControlCoordinatorTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)

    def tearDown(self): self.tmp.cleanup()

    def build(self, *, delay=30):
        timeline=self.root/'producer-timeline.jsonl'
        case,store,admission,taskid=admit_native_fixture(self.root,caller='author',key='fixture',timeline=timeline,delay=delay)
        backend=workflow_native_execution.NativeExecutionBackend(store,self.root/'artifacts',self.root/'lock')
        binding=workflow_coordinator.CoordinatorBinding('author',SimpleNamespace())
        identity=lambda context: True
        identity.server='http://127.0.0.1:1'
        owner=workflow_coordinator.OwnerBinding('owner',str(case.context.repo))
        co=workflow_coordinator.ExplicitCoordinator(store,bindings={'author-token':binding},artifact_dir=self.root/'artifacts',
            handlers={'TASK_READY':backend},identity_verifier=identity,owner_bindings={'owner-token':owner},reconcile_interval=.03)
        workflow_coordinator.configure_review_pipeline(co,{}, {}, {})
        co.run_control=workflow_run_control.ServiceRunControl(store,co.notify_committed)
        return case,store,taskid,timeline,co

    def command(self, store, run_id, command_id='owner-cancel'):
        saved=store.get_run(run_id)
        return {'command_id':command_id,'run_id':run_id,'evaluation_generation':saved['attempt']['generation'],
                'expected_sequence':saved['run']['sequence'],'reason':'CANCELLED'}

    def wait_closed(self, store, run_id):
        deadline=time.monotonic()+15
        while time.monotonic()<deadline:
            saved=store.get_run(run_id)
            if saved['attempt']['phase']=='CLOSED': return saved
            time.sleep(.025)
        self.fail('coordinator thread did not settle cancellation')

    def test_http_owner_cancellation_kills_real_native_worker_and_replays(self):
        from starlette.testclient import TestClient
        case,store,taskid,timeline,co=self.build()
        run_id=store.get_task(taskid)['run_id']
        try:
            with TestClient(co.app()) as client:
                deadline=time.monotonic()+10
                while time.monotonic()<deadline:
                    resources=store.get_task_resources(taskid)
                    if resources and resources[0]['state']=='HELD' and timeline.exists(): break
                    time.sleep(.025)
                self.assertTrue(resources and resources[0]['state']=='HELD')
                self.assertTrue(timeline.exists(),'actual native producer entered')
                command=self.command(store,run_id)
                response=client.post('/v1/cancel',headers={'Authorization':'Bearer owner-token'},json=command)
                self.assertEqual(response.status_code,200,response.text)
                saved=self.wait_closed(store,run_id)
                self.assertEqual(saved['attempt']['closure'],'CANCELLED'); self.assertIsNone(saved['attempt']['result'])
                self.assertEqual(store.get_task(taskid)['state'],'CANCELLED')
                self.assertEqual(store.get_task_resources(taskid)[0]['state'],'RELEASED')
                self.assertEqual(saved['attempt']['result'], None)
                self.assertEqual(len(timeline.read_text().splitlines()), 1)
                health=client.get('/v1/health',headers={'Authorization':'Bearer owner-token'})
                self.assertEqual(health.status_code,200)
                replay=client.post('/v1/cancel',headers={'Authorization':'Bearer owner-token'},json=command)
                self.assertEqual(replay.status_code,200,replay.text)
                self.assertEqual(replay.json()['receipt']['cancellation_id'],response.json()['receipt']['cancellation_id'])
                self.assertEqual((case.repo/'counter.txt').exists(),False)
                self.assertEqual(len(timeline.read_text().splitlines()), 1, 'exact cancellation retry must not relaunch producer')
        finally: co.stop()

    def test_queued_owner_cancellation_settles_from_coordinator_reconcile(self):
        from starlette.testclient import TestClient
        case,store,taskid,timeline,co=self.build(delay=30)
        run_id=store.get_task(taskid)['run_id']
        try:
            command=self.command(store,run_id)
            # The coordinator has not started; this request only persists STOPPING/outbox intent.
            client=TestClient(co.app())
            response=client.post('/v1/cancel',headers={'Authorization':'Bearer owner-token'},json=command)
            self.assertEqual(response.status_code,200,response.text)
            with client:
                saved=self.wait_closed(store,run_id)
                self.assertEqual(saved['attempt']['closure'],'CANCELLED'); self.assertIsNone(saved['attempt']['result'])
                self.assertEqual(store.get_task(taskid)['state'],'CANCELLED')
                self.assertFalse(timeline.exists())
                self.assertFalse((case.repo/'counter.txt').exists())
        finally: co.stop()

    def test_owner_cancel_retires_only_settled_error_head_and_replays_after_new_head(self):
        case,store,_,taskid=admit_native_fixture(self.root,caller='author',key='retire-error',delay=0)
        saved=store.get_run(store.get_task(taskid)['run_id']); run_id=saved['run']['run_id']
        db=store._connect()
        try:
            db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','COMPLETED','$.result',json('{}')) WHERE id=?",(taskid,))
            db.execute("UPDATE outbox SET state='ACKNOWLEDGED' WHERE event_id IN (SELECT id FROM events WHERE run_id=? AND generation=1)",(run_id,))
            attempt=saved['attempt']; attempt.update(phase='DECIDED',result='ERROR',cleanup_state='CONFIRMED',closure=None,proposed_result=None,proposed_closure=None)
            db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),run_id))
            db.commit()
        finally: db.close()
        binding=SimpleNamespace(worktree=str(case.repo),caller='owner')
        service=workflow_run_control.ServiceRunControl(store)
        cmd={'contract_version':1,'run_id':run_id,'evaluation_generation':1,'expected_sequence':saved['run']['sequence'],'command_id':'retire-1','actor':'OWNER','reason':'CANCELLED'}
        db=store._connect()
        try:
            history_before=(db.execute('SELECT attempt_json FROM attempts WHERE run_id=?',(run_id,)).fetchall(),
                db.execute('SELECT id,task_json,result_digest,result_json FROM tasks WHERE run_id=?',(run_id,)).fetchall(),
                db.execute('SELECT id,event_id,state,protocol_ack FROM deliveries WHERE event_id IN (SELECT id FROM events WHERE run_id=?)',(run_id,)).fetchall())
        finally: db.close()
        with self.assertRaises(workflow_store.StaleCommand):
            store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=cmd['expected_sequence']+1,caller='owner',command_id='stale',worktree=str(case.repo))
        result=service.cancel(cmd,binding)
        self.assertEqual(result['state'],'RETIRED_HEAD'); self.assertEqual(result['historical_result'],'ERROR')
        after=store.get_run(run_id)
        self.assertEqual(after['attempt'],attempt)
        self.assertEqual(store.get_task(taskid)['state'],'COMPLETED')
        db=store._connect()
        try:
            history_after=(db.execute('SELECT attempt_json FROM attempts WHERE run_id=?',(run_id,)).fetchall(),
                db.execute('SELECT id,task_json,result_digest,result_json FROM tasks WHERE run_id=?',(run_id,)).fetchall(),
                db.execute("SELECT id,event_id,state,protocol_ack FROM deliveries WHERE event_id IN (SELECT id FROM events WHERE run_id=? AND kind!='OWNER_ERROR_HEAD_RETIRED')",(run_id,)).fetchall())
            self.assertEqual(history_before,history_after)
        finally: db.close()
        replay=service.cancel(cmd,binding)
        self.assertEqual(replay['cancellation_id'],result['cancellation_id'])
        with self.assertRaises(workflow_store.IdempotencyConflict):
            service.cancel(dict(cmd,expected_sequence=cmd['expected_sequence']+1),binding)

    def test_different_session_author_can_submit_after_retirement_and_old_retry_replays(self):
        case,store,taskid,saved,run_id=self.error_case('new-author')
        binding=SimpleNamespace(worktree=str(case.repo),caller='owner')
        command={'contract_version':1,'run_id':run_id,'evaluation_generation':1,'expected_sequence':saved['run']['sequence'],'command_id':'retire-before-submit','actor':'OWNER','reason':'CANCELLED'}
        retired=workflow_run_control.ServiceRunControl(store).cancel(command,binding)
        self.assertEqual(retired['state'],'RETIRED_HEAD')
        new_case,_,_,new_task=admit_native_fixture(self.root/'new-author',caller='new-author',key='fresh-session',shared_store=store,author_session='fresh-session')
        self.assertEqual(new_case.repo,case.repo)
        self.assertNotEqual(new_case.author['session_id'],case.author['session_id'])
        self.assertEqual(store.get_task(new_task)['state'],'QUEUED')
        replay=workflow_run_control.ServiceRunControl(store).cancel(command,binding)
        self.assertEqual(replay['cancellation_id'],retired['cancellation_id'])

    def test_owner_cancel_retires_settled_fail_head_without_changing_history(self):
        case,store,taskid,saved,run_id=self.error_case('retire-fail')
        attempt=saved['attempt']; attempt.update(phase='DECIDED',result='FAIL',cleanup_state='CONFIRMED')
        db=store._connect()
        try:
            db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),run_id)); db.commit()
            before=(db.execute('SELECT attempt_json FROM attempts WHERE run_id=?',(run_id,)).fetchall(),
                db.execute('SELECT id,task_json,result_digest,result_json FROM tasks WHERE run_id=?',(run_id,)).fetchall(),
                db.execute('SELECT id,event_id,state,protocol_ack FROM deliveries WHERE event_id IN (SELECT id FROM events WHERE run_id=?)',(run_id,)).fetchall(),
                db.execute('SELECT id,artifact_json FROM artifacts WHERE run_id=?',(run_id,)).fetchall())
        finally: db.close()
        cmd={'contract_version':1,'run_id':run_id,'evaluation_generation':1,'expected_sequence':saved['run']['sequence'],'command_id':'retire-fail','actor':'OWNER','reason':'CANCELLED'}
        service=workflow_run_control.ServiceRunControl(store); binding=SimpleNamespace(worktree=str(case.repo),caller='owner')
        with self.assertRaises(workflow_store.StaleCommand):
            store.retire_settled_failed_head(run_id=run_id,generation=1,expected_sequence=cmd['expected_sequence'],caller='foreign',command_id='wrong-tree',worktree='foreign-tree')
        with self.assertRaises(workflow_store.StaleCommand):
            store.retire_settled_failed_head(run_id=run_id,generation=1,expected_sequence=cmd['expected_sequence']+1,caller='owner',command_id='stale-sequence',worktree=str(case.repo))
        receipt=service.cancel(cmd,binding)
        self.assertEqual(receipt['state'],'RETIRED_HEAD'); self.assertEqual(receipt['historical_result'],'FAIL')
        db=store._connect()
        try: self.assertIsNone(db.execute('SELECT 1 FROM feature_heads WHERE run_id=?',(run_id,)).fetchone())
        finally: db.close()
        replay=service.cancel(cmd,binding); self.assertEqual(replay['cancellation_id'],receipt['cancellation_id'])
        db=store._connect()
        try:
            after=(db.execute('SELECT attempt_json FROM attempts WHERE run_id=?',(run_id,)).fetchall(),
                db.execute('SELECT id,task_json,result_digest,result_json FROM tasks WHERE run_id=?',(run_id,)).fetchall(),
                db.execute("SELECT id,event_id,state,protocol_ack FROM deliveries WHERE event_id IN (SELECT id FROM events WHERE run_id=? AND kind!='OWNER_FAIL_HEAD_RETIRED')",(run_id,)).fetchall(),
                db.execute('SELECT id,artifact_json FROM artifacts WHERE run_id=?',(run_id,)).fetchall())
            self.assertEqual(before,after)
            event=db.execute("SELECT payload FROM events WHERE kind='OWNER_FAIL_HEAD_RETIRED' AND run_id=?",(run_id,)).fetchone()
            self.assertEqual(json.loads(event[0])['historical_result'],'FAIL')
        finally: db.close()

    def test_settled_error_retirement_rejects_unsettled_dispatch(self):
        case,store,_,taskid=admit_native_fixture(self.root,caller='author',key='retire-held',delay=0)
        saved=store.get_run(store.get_task(taskid)['run_id']); run_id=saved['run']['run_id']
        db=store._connect()
        try:
            db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','COMPLETED') WHERE id=?",(taskid,))
            attempt=saved['attempt']; attempt.update(phase='DECIDED',result='ERROR',cleanup_state='CONFIRMED')
            db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),run_id))
            db.commit()
        finally: db.close()
        with self.assertRaises(workflow_store.StaleCommand):
            store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=str(case.repo))

    def error_case(self,key):
        case,store,_,taskid=admit_native_fixture(self.root/key,caller='author',key=key,delay=0)
        saved=store.get_run(store.get_task(taskid)['run_id']); run_id=saved['run']['run_id']
        db=store._connect()
        try:
            db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','COMPLETED','$.result',json('{}')) WHERE id=?",(taskid,))
            db.execute("UPDATE outbox SET state='ACKNOWLEDGED' WHERE event_id IN (SELECT id FROM events WHERE run_id=?)",(run_id,))
            attempt=saved['attempt']; attempt.update(phase='DECIDED',result='ERROR',cleanup_state='CONFIRMED',closure=None,proposed_result=None,proposed_closure=None)
            db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),run_id)); db.commit()
        finally: db.close()
        return case,store,taskid,saved,run_id

    def test_retirement_rejects_all_held_unknown_resources_and_dirty_scope(self):
        for state in ('HELD','UNKNOWN','DIRTY_SCOPE'):
            with self.subTest(state=state):
                case,store,taskid,saved,run_id=self.error_case('blocked-'+state.lower())
                db=store._connect()
                try:
                    if state=='DIRTY_SCOPE':
                        db.execute("INSERT INTO native_scopes(task_id,assignment_generation,scope_name,worker_identity_json,creation_artifact_ref,cleanup_artifact_ref,state) VALUES(?,1,?,'{}','created',NULL,'ACTIVE')",(taskid,'scope-'+state))
                    else:
                        db.execute("INSERT INTO resources(claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof,request_digest) VALUES(?,?,?,?,?,1,'{}',?,NULL,'digest')",('claim-'+state,'resource-'+state,run_id,1,taskid,state))
                    db.commit()
                finally: db.close()
                with self.assertRaises(workflow_store.StaleCommand):
                    store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=str(case.repo))

    def test_historically_unresolved_error_retires_only_after_current_cleanup_proof(self):
        case,store,taskid,saved,run_id=self.error_case('historic-unresolved')
        original=saved['attempt']; original.update(phase='DECIDED',result='ERROR',cleanup_state='UNRESOLVED')
        db=store._connect()
        try:
            db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(original),run_id)); db.commit()
        finally: db.close()
        store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=str(case.repo))
        self.assertEqual(store.get_run(run_id)['attempt'],original)

    def test_historically_unresolved_error_remains_blocked_without_current_cleanup_proof(self):
        for state in ('HELD','UNKNOWN','DIRTY_SCOPE','UNSETTLED'):
            with self.subTest(state=state):
                case,store,taskid,saved,run_id=self.error_case('historic-unresolved-block-'+state.lower())
                attempt=saved['attempt']; attempt.update(phase='DECIDED',result='ERROR',cleanup_state='UNRESOLVED')
                db=store._connect()
                try:
                    db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),run_id))
                    if state=='UNSETTLED':
                        db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','RUNNING') WHERE id=?",(taskid,))
                    elif state=='DIRTY_SCOPE':
                        db.execute("INSERT INTO native_scopes(task_id,assignment_generation,scope_name,worker_identity_json,creation_artifact_ref,cleanup_artifact_ref,state) VALUES(?,1,?,'{}','created',NULL,'ACTIVE')",(taskid,'historic-'+state))
                    else:
                        db.execute("INSERT INTO resources(claim_id,resource_key,run_id,generation,task_id,assignment_generation,identity_proof,state,cleanup_proof,request_digest) VALUES(?,?,?,?,?,1,'{}',?,NULL,'digest')",('historic-'+state,'resource-'+state,run_id,1,taskid,state))
                    db.commit()
                finally: db.close()
                with self.assertRaises(workflow_store.StaleCommand):
                    store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=str(case.repo))

    def test_retirement_rejects_unproven_cancelled_review(self):
        case,store,taskid,saved,run_id=self.error_case('cancel-review')
        db=store._connect()
        try:
            task=store.get_task(taskid); task.update(kind='REVIEW',state='CANCELLED',task_id=taskid,run_id=run_id,evaluation_generation=1)
            db.execute('UPDATE tasks SET task_json=? WHERE id=?',(workflow_store.canonical_json(task),taskid)); db.commit()
        finally: db.close()
        with self.assertRaises(workflow_store.StaleCommand):
            store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=str(case.repo))

    def cancelled_review_case(self, name):
        case,store,taskid,saved,run_id=self.error_case(name)
        task=store.get_task(taskid)
        task.update(kind='REVIEW',state='CANCELLED',task_id=taskid,run_id=run_id,
                    evaluation_generation=1,assigned_session_id='session-exact')
        evidence={'purpose':'REVIEW_CLEANUP','task_id':taskid,'run_id':run_id,'generation':1,
            'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest'],
            'session_id':'session-exact','inactive_confirmed':True,'inbox_empty':True,'message_absent':True,
            'history_guard':{'current_identity_matches':True,'no_foreign_postbaseline_user':True,'session_id':'session-exact'}}
        raw=workflow_store.canonical_json(evidence).encode()
        path=self.root/(name+'-cleanup.json'); path.write_bytes(raw)
        ref='artifact_'+name+'_cleanup'
        artifact={'contract_version':1,'artifact_id':ref,'run_id':run_id,'producer_task_id':taskid,
            'type':'PROVENANCE','path':str(path),'sha256':hashlib.sha256(raw).hexdigest(),
            'size_bytes':len(raw),'immutable':True,'producer_kind':'TASK','producer_id':taskid}
        store.register_artifact(artifact,input_digest=task['input_digest'])
        expected={'task_id':taskid,'run_id':run_id,'generation':1,
            'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']}
        cancellation={'kind':'ADAPTER_CONFIRMED',**expected,'artifact_ref':ref,
                      'adapter_confirmation':dict(expected,artifact_ref=ref)}
        db=store._connect()
        try:
            db.execute('UPDATE tasks SET task_json=? WHERE id=?',(workflow_store.canonical_json(task),taskid))
            db.execute('INSERT INTO task_cancellations(task_id,proof_json,confirmed) VALUES(?,?,1)',(taskid,workflow_store.canonical_json(cancellation)))
            ready={'event_id':'ready_'+name,'kind':'TASK_READY','task_id':taskid}
            store._event(db,ready,run_id,1)
            db.execute("UPDATE outbox SET state='ACKNOWLEDGED' WHERE event_id=?",(ready['event_id'],))
            db.commit()
        finally: db.close()
        return case,store,taskid,saved,run_id,path,artifact

    def test_readonly_verifier_accepts_adapter_proof_without_mutating_store(self):
        case,store,taskid,saved,run_id,*_=self.cancelled_review_case('verify-cleanup-valid')
        db=store._connect()
        try:
            attempt=saved['attempt']; attempt.update(phase='DECIDED',result='ERROR',cleanup_state='UNRESOLVED')
            db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),run_id)); db.commit()
        finally: db.close()
        def snapshot():
            db=store._connect()
            try:
                return tuple(db.execute(q,(run_id,)).fetchall() for q in (
                    'SELECT * FROM feature_heads WHERE run_id=?',
                    'SELECT attempt_json FROM attempts WHERE run_id=?',
                    'SELECT * FROM outbox WHERE event_id IN (SELECT id FROM events WHERE run_id=?)'))
            finally: db.close()
        before=snapshot()
        expected={'run_id':run_id,'generation':1,'expected_sequence':saved['run']['sequence'],'worktree':str(case.repo)}
        self.assertEqual(store.verify_settled_error_head(**expected),expected)
        self.assertEqual(snapshot(),before)

    def test_readonly_verifier_rejects_tampered_review_proof(self):
        case,store,taskid,saved,run_id,path,artifact=self.cancelled_review_case('verify-cleanup-bad')
        path.write_text(path.read_text()+' ')
        with self.assertRaises(workflow_store.StaleCommand):
            store.verify_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],worktree=str(case.repo))

    def test_retirement_accepts_verified_review_cleanup_without_rewriting_error_history(self):
        case,store,taskid,saved,run_id,*_=self.cancelled_review_case('cleanup-valid')
        historical=dict(saved['attempt']); historical.update(phase='DECIDED',result='ERROR',cleanup_state='UNRESOLVED')
        db=store._connect()
        try:
            db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(historical),run_id)); db.commit()
        finally: db.close()
        store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=str(case.repo))
        self.assertEqual(store.get_run(run_id)['attempt'],historical)

    def test_retirement_rejects_tampered_or_unconfirmed_review_cleanup(self):
        for mutation in ('hash','missing','producer','input','task','session','history','inactive','array','null','string','number','unconfirmed','pending_cancel'):
            with self.subTest(mutation=mutation):
                case,store,taskid,saved,run_id,path,artifact=self.cancelled_review_case('cleanup-'+mutation)
                db=store._connect()
                try:
                    proofrow=db.execute('SELECT proof_json FROM task_cancellations WHERE task_id=?',(taskid,)).fetchone()
                    proof=json.loads(proofrow[0]); data=json.loads(path.read_text())
                    if mutation=='hash': path.write_text(path.read_text()+' ')
                    elif mutation=='missing': path.unlink()
                    elif mutation=='producer': artifact['producer_id']='foreign'; db.execute('UPDATE artifacts SET artifact_json=? WHERE id=?',(workflow_store.canonical_json({'artifact':artifact,'input_digest':store.get_task(taskid)['input_digest']}),artifact['artifact_id']))
                    elif mutation=='input': artifact['input_digest']='foreign'
                    elif mutation=='task': data['task_id']='foreign'; path.write_text(workflow_store.canonical_json(data))
                    elif mutation=='session': data['session_id']='foreign'; path.write_text(workflow_store.canonical_json(data))
                    elif mutation=='history': data['history_guard']['no_foreign_postbaseline_user']=False; path.write_text(workflow_store.canonical_json(data))
                    elif mutation=='inactive': data['inactive_confirmed']=False; path.write_text(workflow_store.canonical_json(data))
                    elif mutation in ('array','null','string','number'):
                        raw={'array':'[]','null':'null','string':'"not an object"','number':'17'}[mutation].encode()
                        path.write_bytes(raw)
                        artifact.update(size_bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
                        artrow=db.execute('SELECT artifact_json FROM artifacts WHERE id=?',(artifact['artifact_id'],)).fetchone()
                        info=json.loads(artrow[0]); info['artifact']=artifact
                        db.execute('UPDATE artifacts SET artifact_json=? WHERE id=?',(workflow_store.canonical_json(info),artifact['artifact_id']))
                    elif mutation=='unconfirmed': db.execute('UPDATE task_cancellations SET confirmed=0 WHERE task_id=?',(taskid,))
                    elif mutation=='pending_cancel':
                        db.execute("INSERT INTO events(id,run_id,generation,kind,payload,payload_digest,created_ms) VALUES('cancel_pending',?,1,'CANCEL_TASK','{}','x',0)",(run_id,))
                        db.execute("INSERT INTO outbox(event_id,state) VALUES('cancel_pending','PENDING')")
                    if mutation in ('input','task','session','history','inactive'):
                        path.write_text(workflow_store.canonical_json(data))
                    if mutation=='input':
                        artifact['input_digest']='foreign'; db.execute('UPDATE artifacts SET artifact_json=? WHERE id=?',(workflow_store.canonical_json({'artifact':artifact,'input_digest':'foreign'}),artifact['artifact_id']))
                    db.commit()
                finally: db.close()
                with self.assertRaises(workflow_store.StaleCommand):
                    store.retire_settled_error_head(run_id=run_id,generation=1,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=str(case.repo))
                self.assertEqual(store.get_run(run_id)['run']['run_id'],run_id)

    def test_retirement_rejects_wrong_head_worktree_generation_and_nonerror_verdicts(self):
        for mode in ('wrong-head','wrong-worktree','stale-generation','FAIL','PASS','unsettled'):
            with self.subTest(mode=mode):
                case,store,_,saved,run_id=self.error_case('reject-'+mode)
                db=store._connect()
                try:
                    if mode=='wrong-head': db.execute("DELETE FROM feature_heads WHERE run_id=?",(run_id,))
                    elif mode=='FAIL' or mode=='PASS':
                        attempt=saved['attempt']; attempt.update(phase='DECIDED',result=mode)
                        db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(attempt),run_id))
                    elif mode=='unsettled':
                        db.execute("UPDATE tasks SET task_json=json_set(task_json,'$.state','RUNNING') WHERE id=?",(store.list_tasks(run_id,1)[0]['task_id'],))
                    db.commit()
                finally: db.close()
                gen=2 if mode=='stale-generation' else 1
                worktree=str(case.repo/'wrong') if mode=='wrong-worktree' else str(case.repo)
                with self.assertRaises(workflow_store.StaleCommand):
                    store.retire_settled_error_head(run_id=run_id,generation=gen,expected_sequence=saved['run']['sequence'],caller='owner',command_id='retire',worktree=worktree)


if __name__ == "__main__": unittest.main()
