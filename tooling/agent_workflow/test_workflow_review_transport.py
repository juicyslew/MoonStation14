import hashlib
import tempfile
import unittest
from pathlib import Path

import workflow_review
import workflow_review_transport
import workflow_review_pipeline
import workflow_coordinator
import json


class FakeTransport:
    def __init__(self, *, bad=None):
        self.bad = bad
        self.posts = []
        self.sessions = {}
        self.active = {}
        self.inboxes = {}
        self.create_posts=[]

    def __call__(self, server, method, path, body=None):
        if method == 'GET' and path.startswith('/api/session/'):
            if path == '/api/session/active':
                return 200, {'data': self.active}
            if path.endswith('/inbox'):
                sid=path.split('/')[-2]
                return 200, {'data':self.inboxes.get(sid,[])}
            sid = path.rsplit('/', 1)[-1]
            if sid not in self.sessions:
                return 404, {'error':'not found'}
            return 200, {'data': self.sessions[sid]}
        if method=='POST' and path=='/api/session':
            self.create_posts.append(body)
            sid=body['id']; self.sessions[sid]={'id':sid,'agent':body['agent'],
                'directory':body['location']['directory'],'metadata':body['metadata']}
            return 200,{'data':self.sessions[sid]}
        if method == 'POST' and path.endswith('/prompt'):
            self.posts.append(body)
            ack = {'id': body['id'], 'sessionID': path.split('/')[-2], 'type': 'user',
                   'delivery': body['delivery'], 'payload': {'text': body['text'], 'metadata': body['metadata']}}
            if self.bad == 'id': ack['id'] = 'wrong'
            elif self.bad == 'sessionID': ack['sessionID'] = 'wrong'
            elif self.bad == 'text': ack['payload']['text'] = 'wrong'
            return 200, {'data': ack}
        raise AssertionError(f'unexpected request {method} {path}')


class ReviewTransportTests(unittest.TestCase):
    def prepared(self, root):
        from test_workflow_review import ReviewPlanningTests
        fixture = ReviewPlanningTests()
        store, run_id, check_id = fixture.fixture(root, roles=('implementation',))
        fixture.run_check(root, store, run_id, check_id)
        planner = fixture.coordinator(store, run_id)
        planner.artifact_dir = root / 'review-artifacts'
        planner.artifact_dir.mkdir(parents=True,exist_ok=True)
        task = planner.plan_reviews(run_id)[0]
        binding = planner.reviewers['implementation']
        return fixture, store, planner, task, binding

    def backend(self, planner, binding, transport):
        return workflow_review_transport.ReviewDispatchBackend(planner, server='http://127.0.0.1:4096',
            agents={'implementation':'workflow-reviewer-implementation'}, request=transport)

    @staticmethod
    def set_task_state(store,task,state):
        saved=store.get_task(task['task_id']); saved['state']=state
        db=store._connect()
        try:db.execute('UPDATE tasks SET task_json=? WHERE id=?',
            (workflow_review_transport.workflow_store.canonical_json(saved),task['task_id'])); db.commit()
        finally:db.close()

    @staticmethod
    def reserve_review(store,planner,task,server='http://127.0.0.1:4096'):
        assignment=planner.build_assignment(task['task_id']); envelope=assignment['body']['envelope']
        sid=task['assigned_session_id']; message='msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
        text=workflow_review_transport.workflow_store.canonical_json(assignment['body'])
        store.dispatch_task(task_id=task['task_id'],event_id=envelope['event_id'],assigned_session_id=sid,
            dispatch_envelope=envelope,review_session_claim={'server':server,'session_id':sid,'message_id':message,
                'body_sha256':hashlib.sha256(text.encode()).hexdigest()})
        return assignment

    def test_registered_invalid_prompt_evidence_blocks_absence_and_replay(self):
        for kind in ('baseline','receipt'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
                transport=FakeTransport(); backend=self.backend(planner,binding,transport)
                self.reserve_review(store,planner,task)
                aid=(backend._baseline_artifact_id(task) if kind=='baseline' else backend._receipt_artifact_id(task))
                store.register_artifact({'contract_version':1,'artifact_id':aid,'run_id':task['run_id'],
                    'producer_task_id':task['task_id'],'type':'PROVENANCE','path':str(root/'missing-evidence'),
                    'sha256':'0'*64,'size_bytes':1,'immutable':True,'producer_kind':'TASK',
                    'producer_id':task['task_id']},input_digest=task['input_digest'])
                self.assertTrue(backend.has_registered_prompt_evidence(store.get_task(task['task_id'])))
                self.assertEqual('UNKNOWN',backend.observe('wake',{'task_id':task['task_id']}))
                self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
                self.assertEqual([],transport.create_posts); self.assertEqual([],transport.posts)
                resources=store.get_task_resources(task['task_id'])
                self.assertEqual(1,len(resources)); self.assertEqual('HELD',resources[0]['state'])

    def test_dispatched_without_bound_session_resource_cannot_create(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            self.set_task_state(store,task,'DISPATCHING')
            backend=self.backend(planner,binding,transport)
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual([],transport.create_posts); self.assertEqual([],transport.posts)
            self.assertEqual([],store.get_task_resources(task['task_id']))

    def test_cancellation_hook_after_intent_save_prevents_create_post(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            backend=self.backend(planner,binding,transport); save=backend._save_creation_artifact
            def save_then_cancel(task,record,verified=False):
                result=save(task,record,verified)
                if not verified:self.set_task_state(store,task,'CANCEL_REQUESTED')
                return result
            backend._save_creation_artifact=save_then_cancel
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual('CANCEL_REQUESTED',store.get_task(task['task_id'])['state'])
            self.assertEqual([],transport.create_posts); self.assertEqual([],transport.posts)
            self.assertEqual(1,len(store.get_task_resources(task['task_id'])))

    def test_cancellation_after_verified_create_get_prevents_prompt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            def create_then_cancel(server,method,path,body=None):
                result=transport(server,method,path,body)
                if method=='GET' and path.endswith(task['assigned_session_id']) and transport.create_posts:
                    self.set_task_state(store,task,'CANCEL_REQUESTED')
                return result
            backend=self.backend(planner,binding,create_then_cancel)
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual('CANCEL_REQUESTED',store.get_task(task['task_id'])['state'])
            self.assertEqual(1,len(transport.create_posts)); self.assertEqual([],transport.posts)
            self.assertEqual('REVIEW_SESSION_CREATED',backend._creation_artifact(store.get_task(task['task_id']),verified=True)['kind'])

    def test_cancellation_after_baseline_save_prevents_prompt_post(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            backend=self.backend(planner,binding,transport); save=backend._save_baseline
            def save_then_cancel(task,session_id,assignment):
                result=save(task,session_id,assignment)
                self.set_task_state(store,task,'CANCEL_REQUESTED')
                return result
            backend._save_baseline=save_then_cancel
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual('CANCEL_REQUESTED',store.get_task(task['task_id'])['state'])
            self.assertEqual(1,len(transport.create_posts)); self.assertEqual([],transport.posts)

    @staticmethod
    def _set_saved_task_state(store,task,state):
        saved=store.get_task(task['task_id']); saved['state']=state
        db=store._connect()
        try:db.execute('UPDATE tasks SET task_json=? WHERE id=?',
            (workflow_review_transport.workflow_store.canonical_json(saved),task['task_id'])); db.commit()
        finally:db.close()

    def test_cancel_before_reservation_performs_no_external_effect(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            self._set_saved_task_state(store,task,'CANCELLED')
            backend=self.backend(planner,binding,transport)
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual([],transport.create_posts); self.assertEqual([],transport.posts)
            self.assertEqual([],store.get_task_resources(task['task_id']))

    def test_cancel_after_reservation_before_intent_keeps_resource_without_post(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            backend=self.backend(planner,binding,transport)
            original=workflow_review_transport.workflow_review_effects.review_effect_guard
            def cancel_before_guard(*args,**kwargs):
                self._set_saved_task_state(store,task,'CANCEL_REQUESTED')
                return original(*args,**kwargs)
            workflow_review_transport.workflow_review_effects.review_effect_guard=cancel_before_guard
            try: self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            finally: workflow_review_transport.workflow_review_effects.review_effect_guard=original
            self.assertEqual('CANCEL_REQUESTED',store.get_task(task['task_id'])['state'])
            self.assertEqual([],transport.create_posts); self.assertEqual([],transport.posts)
            resources=store.get_task_resources(task['task_id']); self.assertEqual(1,len(resources))
            self.assertIn(resources[0]['state'],('HELD','UNKNOWN'))

    def test_cancel_during_ambiguous_create_keeps_reserved_resource(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            def commit_then_cancel(server,method,path,body=None):
                result=transport(server,method,path,body)
                if method=='POST' and path=='/api/session':
                    self._set_saved_task_state(store,task,'CANCEL_REQUESTED')
                    resources=store.get_task_resources(task['task_id'])
                    if resources:
                        db=store._connect()
                        try:db.execute("UPDATE resources SET state='UNKNOWN' WHERE claim_id=?",(resources[0]['claim_id'],)); db.commit()
                        finally:db.close()
                    raise TimeoutError('create acknowledgement lost during cancellation')
                return result
            backend=self.backend(planner,binding,commit_then_cancel)
            with self.assertRaises(Exception):backend.dispatch('wake',{'task_id':task['task_id']})
            self.assertEqual('CANCEL_REQUESTED',store.get_task(task['task_id'])['state'])
            self.assertEqual(1,len(transport.create_posts)); self.assertEqual([],transport.posts)
            self.assertEqual('UNKNOWN',store.get_task_resources(task['task_id'])[0]['state'])

    def test_registered_prompt_evidence_presence_includes_invalid_backing_artifact(self):
        for kind in ('baseline','receipt'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
                transport=FakeTransport(); backend=self.backend(planner,binding,transport)
                assignment=planner.build_assignment(task['task_id'])
                self.assertFalse(backend.has_registered_prompt_evidence(task))
                aid=(backend._baseline_artifact_id(task) if kind=='baseline'
                     else backend._receipt_artifact_id(task))
                store.register_artifact({'contract_version':1,'artifact_id':aid,'run_id':task['run_id'],
                    'producer_task_id':task['task_id'],'type':'PROVENANCE','path':str(root/'missing-artifact'),
                    'sha256':'0'*64,'size_bytes':1,'immutable':True,'producer_kind':'TASK',
                    'producer_id':task['task_id']},input_digest=task['input_digest'])
                self.assertTrue(backend.has_registered_prompt_evidence(task))
                if kind=='baseline':
                    self.assertIsNone(backend.get_session_baseline(task,assignment))
                else:
                    self.assertFalse(backend._receipt(task,assignment))

    def test_real_assignment_ack_registers_receipt_without_claim_and_replay_is_applied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
            transport=FakeTransport(); backend=self.backend(planner,binding,transport)
            wake={'kind':'TASK_READY','task_id':task['task_id']}
            self.assertEqual('ABSENT',backend.observe('wake',wake))
            self.assertEqual('APPLIED',backend.dispatch('wake',wake))
            self.assertEqual(1,len(transport.create_posts))
            self.assertEqual(task['assigned_session_id'],transport.create_posts[0]['id'])
            saved=store.get_task(task['task_id'])
            self.assertEqual('DISPATCHING',saved['state']); self.assertIsNone(saved['result'])
            assignment=planner.build_assignment(task['task_id'])
            self.assertEqual(assignment['body_sha256'],hashlib.sha256(transport.posts[0]['text'].encode()).hexdigest())
            self.assertIn('objective',transport.posts[0]['text'])
            self.assertEqual('APPLIED',backend.observe('wake',wake))
            self.assertEqual('APPLIED',backend.dispatch('wake',wake))
            self.assertEqual(1,len(transport.posts))

    def test_forged_ack_is_unknown_and_never_reposted(self):
        for forged_field in ('id','sessionID','text'):
            with self.subTest(field=forged_field), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
                transport=FakeTransport(bad=forged_field); backend=self.backend(planner,binding,transport)
                with self.assertRaises(Exception): backend.dispatch('wake',{'task_id':task['task_id']})
                self.assertEqual('UNKNOWN',backend.observe('wake',{'task_id':task['task_id']}))
                self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
                self.assertEqual(1,len(transport.posts)); self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])

    def test_wrong_agent_or_worktree_never_posts(self):
        for mismatch in ('agent','directory'):
            with self.subTest(mismatch=mismatch), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
                transport=FakeTransport(); setup=self.backend(planner,binding,transport)
                transport.sessions[task['assigned_session_id']]={
                    'id':task['assigned_session_id'],'agent':'wrong' if mismatch=='agent' else 'workflow-reviewer-implementation',
                    'directory':str(root/'wrong') if mismatch=='directory' else binding['worktree'],
                    'metadata':{'ms14_review_task_id':task['task_id'],'ms14_review_run_id':task['run_id'],
                        'ms14_review_evaluation_generation':task['evaluation_generation'],
                        'ms14_review_assignment_generation':task['assignment_generation']}}
                backend=workflow_review_transport.ReviewDispatchBackend(planner,server='http://127.0.0.1:4096',
                    agents={'implementation':'workflow-reviewer-implementation'},request=transport)
                self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
                self.assertEqual([],transport.posts); self.assertEqual('QUEUED',store.get_task(task['task_id'])['state'])

    def test_create_timeout_committed_session_reconciles_then_retries_prompt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            original=transport.__call__; first=[True]
            def timed(server,method,path,body=None):
                result=original(server,method,path,body)
                if method=='POST' and path=='/api/session' and first[0]: first[0]=False; raise TimeoutError('lost response')
                return result
            backend=self.backend(planner,binding,timed)
            with self.assertRaises(Exception): backend.dispatch('wake',{'task_id':task['task_id']})
            self.assertEqual('ABSENT',backend.observe('wake',{'task_id':task['task_id']}))
            self.assertEqual('APPLIED',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual(1,len(transport.create_posts)); self.assertEqual(1,len(transport.posts))
            verified=backend._creation_artifact(store.get_task(task['task_id']),verified=True)
            self.assertEqual('REVIEW_SESSION_CREATED',verified['kind'])

    def test_no_effect_timeout_keeps_intent_and_never_recreates(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            def timed(server,method,path,body=None):
                if method=='POST' and path=='/api/session': transport.create_posts.append(body); raise TimeoutError('no effect')
                return transport(server,method,path,body)
            backend=self.backend(planner,binding,timed)
            with self.assertRaises(Exception): backend.dispatch('wake',{'task_id':task['task_id']})
            self.assertEqual('UNKNOWN',backend.observe('wake',{'task_id':task['task_id']}))
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual(1,len(transport.create_posts)); self.assertEqual([],transport.posts)

    def test_created_session_without_verified_record_recovers_after_restart(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            backend=self.backend(planner,binding,transport); save=backend._save_creation_artifact; once=[True]
            def fail_once(task,record,verified=False):
                if verified and once[0]: once[0]=False; raise OSError('simulated crash')
                return save(task,record,verified)
            backend._save_creation_artifact=fail_once
            with self.assertRaises(Exception): backend.dispatch('wake',{'task_id':task['task_id']})
            backend=self.backend(planner,binding,transport)
            self.assertEqual('ABSENT',backend.observe('wake',{'task_id':task['task_id']}))
            self.assertEqual('APPLIED',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual(1,len(transport.create_posts)); self.assertEqual(1,len(transport.posts))

    def test_unowned_preexisting_session_is_not_adopted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            sid=task['assigned_session_id']; transport.sessions[sid]={'id':sid,'agent':'workflow-reviewer-implementation',
                'directory':binding['worktree'],'metadata':{}}
            backend=self.backend(planner,binding,transport)
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual([],transport.create_posts); self.assertEqual([],transport.posts)

    def test_registered_intent_tampering_fail_closes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
            backend=self.backend(planner,binding,transport); backend._save_creation_artifact(task,backend._creation_record(task,'workflow-reviewer-implementation',binding))
            artifact=store.get_artifact(backend._creation_id(task))['artifact']; Path(artifact['path']).write_text('{}')
            with self.assertRaises(workflow_review_transport.gate_dispatch.DispatchError) as raised:
                backend.dispatch('wake',{'task_id':task['task_id']})
            self.assertIsInstance(raised.exception.__cause__,workflow_review_transport.workflow_store.ProtocolError)
            self.assertEqual([],transport.create_posts); self.assertEqual([],transport.posts)

    def test_created_session_identity_and_each_owned_metadata_mismatch_rejected(self):
        fields=('agent','directory','task','run','evaluation','assignment')
        for mismatch in fields:
            with self.subTest(mismatch=mismatch), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); _,store,planner,task,binding=self.prepared(root); transport=FakeTransport()
                original=transport.__call__
                def create_then_tamper(server,method,path,body=None):
                    result=original(server,method,path,body)
                    if method=='POST' and path=='/api/session':
                        session=transport.sessions[body['id']]
                        if mismatch=='agent': session['agent']='other'
                        elif mismatch=='directory': session['directory']=str(root/'wrong')
                        else:
                            key={'task':'ms14_review_task_id','run':'ms14_review_run_id',
                                'evaluation':'ms14_review_evaluation_generation',
                                'assignment':'ms14_review_assignment_generation'}[mismatch]
                            session['metadata'][key]='tampered'
                    return result
                backend=self.backend(planner,binding,create_then_tamper)
                self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
                self.assertEqual(1,len(transport.create_posts)); self.assertEqual([],transport.posts)
                self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])

    def test_creation_body_matches_pinned_openapi_schema_and_dedicated_profile_agent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,_,planner,task,binding=self.prepared(root); transport=FakeTransport()
            backend=self.backend(planner,binding,transport)
            self.assertEqual('APPLIED',backend.dispatch('wake',{'task_id':task['task_id']}))
            body=transport.create_posts[0]
            spec_path=Path(__file__).resolve().parents[2]/'build/agent-workflow/submit-timeout-repair/opencode-openapi.json'
            spec=json.loads(spec_path.read_text(encoding='utf-8'))
            schema=spec['paths']['/api/session']['post']['requestBody']['content']['application/json']['schema']
            components=spec['components']['schemas']
            def validate(value,node):
                if '$ref' in node:
                    target=components[node['$ref'].rsplit('/',1)[-1]]
                    return validate(value,target)
                if 'anyOf' in node:return any(validate(value,choice) for choice in node['anyOf'])
                kind=node.get('type')
                if kind=='null':return value is None
                if kind=='string':return isinstance(value,str) and (not node.get('pattern') or __import__('re').search(node['pattern'],value))
                if kind=='integer':return isinstance(value,int) and not isinstance(value,bool)
                if kind=='object':
                    return isinstance(value,dict) and all(k in node.get('properties',{}) or node.get('additionalProperties',True) for k in value) and all(k not in node.get('required',[]) or k in value for k in node.get('properties',{})) and all(validate(v,node['properties'][k]) for k,v in value.items() if k in node.get('properties',{}))
                return True
            self.assertTrue(validate(body,schema))
            self.assertEqual(task['assigned_session_id'],body['id'])
            self.assertEqual('workflow-reviewer-implementation',body['agent'])
            self.assertEqual(binding['worktree'],body['location']['directory'])
            self.assertEqual({'ms14_review_task_id','ms14_review_run_id','ms14_review_evaluation_generation','ms14_review_assignment_generation'},set(body['metadata']))
            self.assertNotIn('model',body)

    def test_default_live_request_is_not_used_by_injected_transport(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,_,planner,task,binding=self.prepared(root)
            transport=FakeTransport(); backend=self.backend(planner,binding,transport)
            self.assertEqual('ABSENT',backend.observe('wake',{'task_id':task['task_id']}))
            self.assertEqual([],transport.posts)

    def test_active_or_preexisting_inbox_keeps_admitted_review_reserved(self):
        for occupied in ('active','inbox'):
            with self.subTest(occupied=occupied), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
                transport=FakeTransport(); backend=self.backend(planner,binding,transport)
                if occupied=='active': transport.active[task['assigned_session_id']]={'type':'running'}
                else: transport.inboxes[task['assigned_session_id']]=[{'id':'preexisting','type':'user'}]
                self.assertEqual('PENDING',backend.dispatch('wake',{'task_id':task['task_id']}))
                self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])
                self.assertEqual([],transport.posts)
                resources=store.get_task_resources(task['task_id'])
                self.assertEqual(1,len(resources)); self.assertIn(resources[0]['state'],('HELD','UNKNOWN'))

    def test_present_malformed_active_entry_is_unknown(self):
        for entry in (None,{}, {'type':'idle'}):
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
                transport=FakeTransport(); backend=self.backend(planner,binding,transport)
                transport.active[task['assigned_session_id']]=entry
                self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
                self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])
                self.assertEqual([],transport.posts)

    def test_coordinator_retries_known_busy_review_after_claim_lease_expires(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
            transport=FakeTransport(); backend=self.backend(planner,binding,transport)
            transport.active[task['assigned_session_id']]={'type':'running'}
            pipeline=workflow_review_pipeline.ReviewPipeline(store,planner,object(),backend,object())
            coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'token':object()},
                artifact_dir=root/'artifacts',handlers={'TASK_READY':pipeline},identity_verifier=lambda _:True)

            def claim_review():
                for _ in range(30):
                    claim=store.claim_outbox(lease_ms=1)
                    if claim is None: return None
                    if claim['payload'].get('task_id')==task['task_id']: return claim
                    store.acknowledge_outbox(claim['event_id'],claim['claim_generation'])
                return None

            claim=claim_review(); self.assertIsNotNone(claim)
            coordinator._deliver(claim)
            self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])
            self.assertEqual([],transport.posts)
            resources=store.get_task_resources(task['task_id'])
            self.assertEqual(1,len(resources))
            reservation=resources[0]
            assignment=planner.build_assignment(task['task_id'])
            expected_key='review-session:'+workflow_review_transport.workflow_store.digest(
                {'server':'http://127.0.0.1:4096','session_id':task['assigned_session_id']})
            self.assertEqual(('HELD',expected_key,task['task_id'],task['assigned_session_id']),
                (reservation['state'],reservation['resource_key'],reservation['task_id'],reservation['identity_proof']['session_id']))
            expected_message='msg_'+hashlib.sha256((task['task_id']+assignment['body_sha256']).encode()).hexdigest()[:40]
            self.assertEqual({'server':'http://127.0.0.1:4096','session_id':task['assigned_session_id'],
                'message_id':expected_message,'body_sha256':assignment['body_sha256'],'task_id':task['task_id'],
                'run_id':task['run_id'],'evaluation_generation':task['evaluation_generation'],
                'assignment_generation':task['assignment_generation'],'input_digest':task['input_digest']},reservation['identity_proof'])
            reservation_id=reservation['claim_id']; reserved_sid=task['assigned_session_id']
            self.assertEqual(1,len(transport.create_posts))
            self.assertIsNone(coordinator.last_error)
            db=store._connect()
            try: incidents=db.execute("SELECT COUNT(*) FROM incidents WHERE run_id=?",(task['run_id'],)).fetchone()[0]
            finally: db.close()
            self.assertEqual(0,incidents)

            transport.active.clear()
            import time
            time.sleep(.01)  # let the original public outbox lease expire
            retry=claim_review(); self.assertIsNotNone(retry)
            self.assertEqual(claim['event_id'],retry['event_id'])
            self.assertGreater(retry['claim_generation'],claim['claim_generation'])
            coordinator._deliver(retry)
            self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])
            retry_resources=store.get_task_resources(task['task_id'])
            self.assertEqual(1,len(retry_resources))
            self.assertEqual(reservation_id,retry_resources[0]['claim_id'])
            self.assertEqual(reserved_sid,store.get_task(task['task_id'])['assigned_session_id'])
            self.assertEqual(1,len(transport.create_posts))
            self.assertEqual(1,len(transport.posts))
            db=store._connect()
            try:
                acknowledged=db.execute("SELECT state,claim_generation FROM outbox WHERE event_id=?",
                    (retry['event_id'],)).fetchone()
            finally: db.close()
            self.assertEqual(('ACKNOWLEDGED',retry['claim_generation']),tuple(acknowledged))
            self.assertIsNone(coordinator.last_error)

    def test_idle_dispatch_persists_verified_session_baseline(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
            transport=FakeTransport(); backend=self.backend(planner,binding,transport)
            self.assertEqual('APPLIED',backend.dispatch('wake',{'task_id':task['task_id']}))
            baseline=backend.get_session_baseline(task,planner.build_assignment(task['task_id']))
            self.assertIsNotNone(baseline)
            self.assertEqual('http://127.0.0.1:4096',baseline['record']['server'])
            self.assertTrue(baseline['record']['current_session_inactive'])
            self.assertTrue(baseline['record']['inbox_empty'])
            self.assertEqual(1,len(transport.posts))

    def test_malformed_nested_inbox_shape_is_unknown_and_does_not_claim(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); _,store,planner,task,binding=self.prepared(root)
            transport=FakeTransport(); backend=self.backend(planner,binding,transport)
            original=transport.__call__
            def malformed(server,method,path,body=None):
                if method=='GET' and path.endswith('/inbox'):
                    return 200,{'data':{'inbox':[]}}
                return original(server,method,path,body)
            backend.request=malformed
            self.assertEqual('UNKNOWN',backend.dispatch('wake',{'task_id':task['task_id']}))
            self.assertEqual('DISPATCHING',store.get_task(task['task_id'])['state'])
            self.assertEqual([],transport.posts)


if __name__ == '__main__': unittest.main()
