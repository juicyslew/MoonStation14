"""Read-only board snapshot collection and owner-scoped route coverage."""
import hashlib
import json
import sqlite3
import tempfile
import unittest
import uuid
from unittest import mock
from pathlib import Path

from starlette.testclient import TestClient

import workflow_coordinator
import workflow_native_execution
import workflow_store
import workflow_board
from test_workflow_native_execution import admit_native_fixture


class WorkflowBoardSnapshotTests(unittest.TestCase):
    def _accepted(self, root, key='board-snapshot'):
        from test_workflow_regression import NativeRegressionTests
        bat='@echo off\necho GameTest tag preflight: 1/1 fixtures selected\necho All 1 required tests passed\nexit /b 0\n'
        return NativeRegressionTests()._prepared(root,bat=bat,key=key)

    def test_full_raw_domain_shape_includes_result_owner_acceptance_and_regression_link(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,feature,accepted,admitted,_frozen,backend=self._accepted(root)
            try:
                raw=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                self.assertTrue(raw['available'],raw)
                self.assertIsInstance(raw['captured_at_ms'],int)
                self.assertEqual({'available','captured_at_ms','records','history','unscoped_incidents','native_capacity'},set(raw))
                self.assertEqual({'held':0,'unknown':0},raw['native_capacity'])
                self.assertEqual(1,len(raw['records']))
                record=raw['records'][0]
                self.assertEqual({'worktree','sprint','run','attempt','tasks','request','owner_decisions',
                    'terminal_result','acceptances','regression_jobs','incidents','resources','dependency_pauses',
                    'deliveries','events','artifacts'},set(record))
                self.assertEqual(feature['run_id'],record['run']['run_id'])
                self.assertEqual(store.get_run(feature['run_id'])['run']['sequence'],record['run']['sequence'])
                self.assertEqual(store.get_terminal_result(feature['run_id'],1),record['terminal_result'])
                self.assertEqual(case.request['input_digest'],record['request']['input_digest'])
                completed=[task for task in record['tasks'] if task['task_id']]
                self.assertEqual('PASS',completed[0]['result']['decision'])
                self.assertEqual({'decision','response'},set(record['owner_decisions'][0]))
                self.assertEqual('ACCEPT',record['owner_decisions'][0]['decision']['decision'])
                self.assertEqual(accepted['acceptance_id'],record['acceptances'][0]['acceptance_id'])
                self.assertEqual(accepted['regression_id'],record['regression_jobs'][0]['regression_id'])
                self.assertTrue(record['events']); self.assertTrue(record['artifacts'])
                self.assertTrue(all(isinstance(record[name],list) for name in (
                    'owner_decisions','acceptances','regression_jobs','incidents','resources',
                    'dependency_pauses','deliveries','events','artifacts')))
                self.assertTrue(any(row['run']['run_id']==admitted['run_id'] for row in raw['history']) or
                    any(job['regression_id']==accepted['regression_id'] for job in record['regression_jobs']))
                sample=Path(__file__).resolve().parents[2]/'build/agent-workflow/workflow-redesign-plan/wr12/attempts'/f'board-snapshot-sample-{uuid.uuid4()}'
                sample.mkdir(parents=True,exist_ok=False)
                sample=sample/'board-raw-sample.json'
                sample.write_text(workflow_store.canonical_json(raw)+'\n',encoding='utf-8')
                print(f'board raw sample retained at {sample}')
            finally:
                backend.close()

    def test_feature_head_replacement_uses_persisted_head_not_latest_run_guess(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,feature,_taskid=admit_native_fixture(root,caller='board-head',key='board-head')
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'native-lock')
            backend.dispatch('board-head-finish',{'task_id':_taskid})
            backend.workers[_taskid].wait(timeout=20)
            backend.close()
            sequence=store.get_run(feature['run_id'])['run']['sequence']
            store.transition_attempt(feature['run_id'],1,expected_sequence=sequence,phase='STOPPING',proposed_result='PASS')
            store.transition_attempt(feature['run_id'],1,expected_sequence=sequence+1,phase='DECIDED',terminal_result='PASS',cleanup_state='NOT_REQUIRED')
            request=dict(case.request,idempotency_key='board-head-revision')
            run_id='run_'+hashlib.sha256(b'board-head-revision').hexdigest()[:24]
            task_id='task_'+hashlib.sha256(b'board-head-revision').hexdigest()[:24]
            run={'contract_version':1,'run_id':run_id,'sprint_id':request['sprint_id'],'run_kind':'FEATURE_GATE',
                'input_digest':request['input_digest'],'plan_digest':request['plan_digest'],'policy_digest':request['policy_digest'],
                'author':request['author'],'current_attempt':1,'sequence':0}
            task={'contract_version':1,'task_id':task_id,'run_id':run_id,'evaluation_generation':1,'kind':'CHECK',
                'dependency_ids':[],'input_digest':request['input_digest'],'state':'QUEUED','assignment_generation':1,
                'assigned_session_id':None,'deadline_ms':9999999999999,'result':None}
            event={'contract_version':1,'event_id':'event_'+task_id[5:],'kind':'TASK_READY','run_id':run_id,
                'evaluation_generation':1,'task_id':task_id,'assignment_generation':1,'input_digest':request['input_digest']}
            newer=store.submit(caller=case.caller,request=request,run=run,first_task=task,outbox_event=event,
                logical_task_key='preflight:code_checks',replace_head_run_id=feature['run_id'])
            raw=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
            new_id=newer['run_id']
            self.assertEqual([new_id],[row['run']['run_id'] for row in raw['records']],newer)
            self.assertIn(feature['run_id'],[row['run']['run_id'] for row in raw['history']])

    def test_readonly_snapshot_does_not_create_missing_paths_or_change_live_wal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); missing=root/'missing'/'state.sqlite'
            result=workflow_store.WorkflowStore.readonly_snapshot(missing)
            self.assertFalse(result['available']); self.assertEqual('database_missing',result['reason'])
            self.assertFalse(missing.parent.exists())
            store=workflow_store.WorkflowStore(root/'state.sqlite')
            case,_,_,taskid=admit_native_fixture(root/'admission',caller='board-wal',key='board-wal',shared_store=store)
            task=store.get_task(taskid); run=store.get_run(task['run_id'])
            reader=sqlite3.connect(store.path)
            try:
                reader.execute('BEGIN')
                reader.execute('SELECT count(*) FROM runs').fetchone()
                store.transition_attempt(task['run_id'],task['evaluation_generation'],
                    expected_sequence=run['run']['sequence'],phase='CHECKING')
                wal=Path(store.path+'-wal')
                self.assertTrue(wal.exists(),'held reader keeps a committed uncheckpointed WAL visible')
                before=wal.read_bytes()
                database_before=hashlib.sha256(Path(store.path).read_bytes()).hexdigest()
                raw=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                self.assertTrue(raw['available'],raw)
                self.assertEqual(store.get_run(task['run_id'])['run']['sequence'],raw['records'][0]['run']['sequence'])
                self.assertEqual(before,wal.read_bytes())
                self.assertEqual(database_before,hashlib.sha256(Path(store.path).read_bytes()).hexdigest())
                reader.rollback()
            finally:
                reader.close()

    def test_unscoped_incident_snapshot_is_count_only_and_scope_independent(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'state.sqlite')
            request={'idempotency_key':'unscoped-rejection','source_worktree':str(root/'foreign-bait'),
                'prompt':'PRIVATE_RAW_PROMPT','config_path':str(root/'secret-config'),'access_token':'PRIVATE_BEARER'}
            response={'contract_version':1,'status':'REJECTED','result':'ERROR','code':'INVALID_REQUEST',
                'incident_ref':'incident_unscoped_error','next_action':'WAIT_FOR_RECOVERY'}
            error={'contract_version':1,'incident_id':'incident_unscoped_error','code':'INVALID_REQUEST',
                'message':'PRIVATE_INCIDENT_MESSAGE','recovery_owner':'SYSTEM','state':'OPEN',
                'owner_action_required':False,'artifact_refs':[]}
            store.record_rejection(caller='untrusted-caller',idempotency_key=request['idempotency_key'],
                raw_request=request,response=response,incident=error)
            store.record_incident({'contract_version':1,'incident_id':'incident_unscoped_recovering',
                'code':'RECOVERY','message':'private recovering detail','recovery_owner':'SYSTEM',
                'state':'RECOVERING','owner_action_required':False,'artifact_refs':[]})
            store.record_incident({'contract_version':1,'incident_id':'incident_unscoped_resolved',
                'code':'RESOLVED','message':'not an unresolved notice','recovery_owner':'SYSTEM',
                'state':'RESOLVED','owner_action_required':False,'artifact_refs':[]})

            raw=workflow_store.WorkflowStore.readonly_snapshot(store.path)
            other_scope=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(root/'another-owner'))
            self.assertTrue(raw['available'],raw)
            self.assertEqual({'open':1,'recovering':1,'total':2},raw['unscoped_incidents'])
            self.assertEqual(raw['unscoped_incidents'],other_scope['unscoped_incidents'])
            self.assertEqual([],raw['records'])
            self.assertEqual([],raw['history'])
            raw_text=json.dumps(raw)
            for secret in ('PRIVATE_RAW_PROMPT','PRIVATE_INCIDENT_MESSAGE','foreign-bait','secret-config',
                           'PRIVATE_BEARER','untrusted-caller','incident_unscoped_error'):
                self.assertNotIn(secret,raw_text)

            unavailable=workflow_store.WorkflowStore.readonly_snapshot(root/'does-not-exist.sqlite')
            self.assertFalse(unavailable['available'])
            self.assertNotIn('unscoped_incidents',unavailable)

    def test_incompatible_database_is_unavailable_without_initializing_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'incompatible.sqlite'
            db=sqlite3.connect(path); db.execute('create table not_workflow(value text)'); db.commit(); db.close()
            before=hashlib.sha256(path.read_bytes()).hexdigest()
            result=workflow_store.WorkflowStore.readonly_snapshot(path)
            self.assertFalse(result['available'])
            self.assertEqual(before,hashlib.sha256(path.read_bytes()).hexdigest())

    def test_acknowledged_delivery_and_unknown_resource_are_snapshot_records(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,feature,accepted,_admitted,_frozen,backend=self._accepted(root,'board-timeline')
            try:
                terminal=store.get_terminal_result(feature['run_id'],1)
                event=store.get_event(terminal['event_id'])['payload']
                recipient=case.request['author']
                delivery_id='delivery_'+hashlib.sha256((event['event_id']+'AUTHOR'+
                    workflow_store.canonical_json(recipient)).encode()).hexdigest()[:24]
                claim=store.claim_delivery(delivery_id=delivery_id)
                receipt={'message_id':delivery_id,'event_id':claim['event_id'],'recipient':claim['recipient'],
                    'payload_digest':claim['payload_digest'],'accepted':True}
                store.record_delivery_transport(delivery_id,claim_generation=claim['claim_generation'],
                    lease_token=claim['lease_token'],receipt=receipt)
                ack={'contract_version':1,'event_id':claim['event_id'],'run_id':feature['run_id'],
                    'evaluation_generation':1,'result_id':terminal['result_id'],'result_digest':terminal['result_digest'],
                    'author_session_id':recipient['session_id'],'command_id':'board-ack-command'}
                store.acknowledge_delivery(ack,authenticated_session=recipient['session_id'])
                raw=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                record=raw['records'][0]
                row=next(d for d in record['deliveries'] if d['delivery_id']==delivery_id)
                self.assertEqual('ACKNOWLEDGED',row['state'])
                self.assertEqual(ack,row['protocol_ack'])
                self.assertEqual([],record['dependency_pauses'])
                self.assertEqual(accepted['acceptance_id'],record['acceptances'][0]['acceptance_id'])
            finally:
                backend.close()

    def test_unknown_resource_and_open_incident_are_never_dropped(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,_run,task_id=admit_native_fixture(root,caller='board-unknown',key='board-unknown',delay=30)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None
            try:
                backend.dispatch('board-unknown-running',{'task_id':task_id}); proc=backend.workers[task_id]
                import time
                deadline=time.monotonic()+10
                while time.monotonic()<deadline:
                    resources=store.get_task_resources(task_id)
                    if resources and resources[0]['state']=='HELD': break
                    time.sleep(.025)
                self.assertTrue(resources and resources[0]['state']=='HELD')
                store.mark_resource_unknown(resources[0]['claim_id'],identity_proof=resources[0]['identity_proof'])
                incident={'contract_version':1,'incident_id':'incident_board_unknown','run_id':_run['run_id'],
                    'evaluation_generation':1,'task_id':task_id,'code':'BOARD_UNKNOWN','message':'Uncertain native ownership',
                    'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,'artifact_refs':[]}
                store.record_incident(incident)
                raw=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                record=raw['records'][0]
                self.assertTrue(any(row['state']=='UNKNOWN' for row in record['resources']))
                self.assertTrue(any(row['incident']['incident_id']=='incident_board_unknown' for row in record['incidents']))
            finally:
                backend.close()
                if proc is not None: proc.wait(timeout=8)

    def test_dependency_pause_uses_saved_review_pause_record_shape(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,receipt,check_id=admit_native_fixture(root,caller='board-dependency',key='board-dependency',
                review_roles=['implementation'],delay=.05)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            try:
                backend.dispatch('board-dependency-check',{'task_id':check_id})
                backend.workers[check_id].wait(timeout=20)
                saved=store.get_run(receipt['run_id']); seq=saved['run']['sequence']
                if saved['attempt']['phase']!='CHECKING':
                    store.transition_attempt(receipt['run_id'],1,expected_sequence=seq,phase='CHECKING')
                    seq=store.get_run(receipt['run_id'])['run']['sequence']
                store.transition_attempt(receipt['run_id'],1,expected_sequence=seq,phase='REVIEWING')
                task_id='task_board_dependency_review'
                task={'contract_version':1,'task_id':task_id,'run_id':receipt['run_id'],
                    'evaluation_generation':1,'kind':'REVIEW','dependency_ids':[check_id],
                    'input_digest':case.request['input_digest'],'state':'QUEUED','assignment_generation':1,
                    'assigned_session_id':None,'deadline_ms':9999999999999,'result':None}
                store.add_task(task,logical_task_key='review:implementation')
                binding={key:task.get(key) for key in ('run_id','evaluation_generation','assignment_generation',
                    'input_digest','assigned_session_id')}
                store.pause_dependency_task(task_id=task_id,dependency_key='board-local-api',
                    failure_id='board-dependency-failure',binding=binding,now_ms=123456)
                raw=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                pause=raw['records'][0]['dependency_pauses'][0]
                self.assertEqual({'task_id','dependency_key','paused_since_ms','active','last_resumed_ms',
                    'accumulated_paused_ms','context'},set(pause))
                self.assertEqual(task_id,pause['task_id']); self.assertTrue(pause['active'])
            finally:
                backend.close()

    def test_owner_routes_are_worktree_scoped_and_seen_does_not_resolve(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,feature,accepted,_admitted,_frozen,backend=self._accepted(root,'board-route')
            try:
                incident={'contract_version':1,'incident_id':'incident_board_scope','run_id':feature['run_id'],
                    'evaluation_generation':1,'code':'BOARD_TEST','message':'Board visibility fixture',
                    'recovery_owner':'OWNER','state':'OPEN','owner_action_required':True,'artifact_refs':[]}
                store.record_incident(incident)
                coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'unused':object()},
                    artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                    owner_bindings={'board-token':workflow_coordinator.OwnerBinding('board-owner',str(case.repo.resolve()))})
                client=TestClient(coordinator.app())
                self.assertEqual(403,client.get('/v1/board/snapshot').status_code)
                denied=client.get('/v1/board/snapshot',headers={'Authorization':'Bearer board-token'},params={'worktree':str(case.repo)})
                self.assertEqual(400,denied.status_code)
                author_token=client.get('/v1/board/snapshot',headers={'Authorization':'Bearer author-token'})
                self.assertEqual(403,author_token.status_code)
                response=client.get('/v1/board/snapshot',headers={'Authorization':'Bearer board-token'})
                self.assertEqual(200,response.status_code)
                self.assertEqual(str(case.repo.resolve()),response.json()['worktree'])
                self.assertIn('coordinator',response.json())
                for forbidden in ('authorization','bearer','claim_token','prompt_body','config_body'):
                    self.assertNotIn(forbidden,json.dumps(response.json()).lower())
                foreign=workflow_coordinator.ExplicitCoordinator(store,bindings={'unused':object()},
                    artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                    owner_bindings={'foreign-token':workflow_coordinator.OwnerBinding('foreign-owner',str(root/'elsewhere'))})
                foreign_response=TestClient(foreign.app()).get('/v1/board/snapshot',headers={'Authorization':'Bearer foreign-token'})
                self.assertEqual([],foreign_response.json()['columns']['Building']+
                    foreign_response.json()['columns']['Queued']+
                    foreign_response.json()['columns']['Checking']+
                    foreign_response.json()['columns']['Reviewing']+
                    foreign_response.json()['columns']['Your testing']+
                    foreign_response.json()['columns']['Accepted'])
                seen=client.post('/v1/board/incidents/incident_board_scope/seen',headers={'Authorization':'Bearer board-token'})
                self.assertEqual(200,seen.status_code)
                persisted=store.get_incident('incident_board_scope')
                self.assertIsNotNone(persisted['seen_ms']); self.assertIsNone(persisted['resolved_ms'])
                foreign_seen=TestClient(foreign.app()).post('/v1/board/incidents/incident_board_scope/seen',
                    headers={'Authorization':'Bearer foreign-token'})
                self.assertEqual(403,foreign_seen.status_code)
                self.assertIsNone(store.get_incident('incident_board_scope')['resolved_ms'])
            finally:
                backend.close()

    def test_unscoped_artifacts_are_decoded_once_per_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,*_rest=self._accepted(root,key='artifact-once')
            backend=_rest[-1]
            try:
                db=sqlite3.connect(store.path)
                try:
                    artifact_json=[row[0] for row in db.execute(
                        'SELECT artifact_json FROM artifacts WHERE run_id IS NULL ORDER BY id')]
                finally:
                    db.close()
                original=json.loads
                decoded=[]
                def loads(value,*args,**kwargs):
                    if value in artifact_json:
                        decoded.append(value)
                    return original(value,*args,**kwargs)
                statements=[]
                real_connect=sqlite3.connect
                def traced_connect(*args,**kwargs):
                    connection=real_connect(*args,**kwargs)
                    connection.set_trace_callback(statements.append)
                    return connection
                with mock.patch.object(workflow_store.sqlite3,'connect',side_effect=traced_connect), \
                        mock.patch.object(workflow_store.json,'loads',side_effect=loads):
                    raw=workflow_store.WorkflowStore.readonly_snapshot(store.path)
                self.assertTrue(raw['available'],raw)
                self.assertEqual(len(artifact_json),len(decoded))
                self.assertTrue(any(r['artifacts'] for r in raw['records']+raw['history']))
                for table in ('artifacts','events','deliveries'):
                    matching=[sql for sql in statements if sql.lstrip().upper().startswith('SELECT') and
                        f'FROM {table.upper()} ' in sql.upper()]
                    self.assertEqual(2 if table=='artifacts' else 1,len(matching),(table,matching))
            finally:
                backend.close()

    def test_board_snapshot_projects_submissions_and_skips_audit_payload_tables(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,feature,_accepted,_admitted,_frozen,backend=self._accepted(root,'board-light-read')
            try:
                statements=[]
                real_connect=sqlite3.connect
                def traced_connect(*args,**kwargs):
                    connection=real_connect(*args,**kwargs)
                    connection.set_trace_callback(statements.append)
                    return connection
                with mock.patch.object(workflow_store.sqlite3,'connect',side_effect=traced_connect):
                    light=workflow_store.WorkflowStore.readonly_board_snapshot(store.path,worktree=str(case.repo.resolve()))
                self.assertTrue(light['available'],light)
                self.assertEqual(feature['run_id'],light['records'][0]['run']['run_id'])
                request=light['records'][0]['request']
                self.assertEqual(case.request['author'],request['author'])
                self.assertEqual(case.request['manual_steps'],request['manual_steps'])
                full_initial=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                projected_live=workflow_board.project(light,worktree=str(case.repo.resolve()))
                projected_audit=workflow_board.project(full_initial,worktree=str(case.repo.resolve()))
                self.assertEqual(projected_audit['columns']['Your testing'][0]['agent'],
                    projected_live['columns']['Your testing'][0]['agent'])
                terminal=light['records'][0]['terminal_result']
                db=sqlite3.connect(store.path)
                try:
                    db.execute("UPDATE events SET created_ms=123456 WHERE id=?",(terminal['result_id'],)); db.commit()
                finally: db.close()
                light=workflow_store.WorkflowStore.readonly_board_snapshot(store.path,worktree=str(case.repo.resolve()))
                full=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                projected_light=workflow_board.project(light,worktree=str(case.repo.resolve()))
                projected_full=workflow_board.project(full,worktree=str(case.repo.resolve()))
                card_light=projected_light['columns']['Your testing'][0]
                card_full=projected_full['columns']['Your testing'][0]
                self.assertEqual(123456,card_light['result_created_ms'])
                self.assertEqual(card_full['result_created_ms'],card_light['result_created_ms'])
                selects=[s.upper() for s in statements if s.lstrip().upper().startswith('SELECT')]
                for table in ('ARTIFACTS','EVENTS','DELIVERIES'):
                    self.assertFalse(any('FROM '+table+' ' in s for s in selects),table)
                submission_selects=[s for s in selects if 'FROM SUBMISSIONS' in s]
                self.assertTrue(submission_selects)
                self.assertTrue(all('SELECT ID,REQUEST_JSON' not in s and ',REQUEST_JSON,RESPONSE_JSON' not in s for s in submission_selects),submission_selects)
                self.assertTrue(all('JSON_EXTRACT(REQUEST_JSON' in s for s in submission_selects),submission_selects)
            finally:
                backend.close()

    def test_full_reader_batches_unscoped_artifacts_once_across_run_chunks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,*rest=self._accepted(root,'artifact-chunks'); backend=rest[-1]
            try:
                db=sqlite3.connect(store.path)
                try:
                    run_json,attempt_json=db.execute("SELECT r.run_json,a.attempt_json FROM runs r JOIN attempts a ON a.run_id=r.id WHERE a.generation=r.current_generation LIMIT 1").fetchone()
                    template=json.loads(run_json); attempt=json.loads(attempt_json)
                    for i in range(501):
                        rid=f'run_history_chunk_{i:04d}'
                        run=dict(template,run_id=rid)
                        db.execute("INSERT INTO runs(id,sprint,worktree,run_json,current_generation,sequence) VALUES(?,?,?,?,1,0)",
                            (rid,'chunk-test',str(root),json.dumps(run)))
                        db.execute("INSERT INTO attempts(run_id,generation,attempt_json) VALUES(?,1,?)",(rid,json.dumps(attempt)))
                    db.commit()
                    artifact={'contract_version':1,'artifact':{'artifact_id':'artifact_unscoped_once','submission_id':'sub_not_selected'},'classification':'PUBLIC'}
                    db.execute("INSERT INTO artifacts(id,run_id,artifact_json) VALUES(?,NULL,?)",('artifact_unscoped_once',json.dumps(artifact))); db.commit()
                finally: db.close()
                decoded=[]; statements=[]; original=json.loads; real_connect=sqlite3.connect
                def loads(value,*args,**kwargs):
                    if value==json.dumps(artifact): decoded.append(value)
                    return original(value,*args,**kwargs)
                def traced_connect(*args,**kwargs):
                    connection=real_connect(*args,**kwargs); connection.set_trace_callback(statements.append); return connection
                with mock.patch.object(workflow_store.sqlite3,'connect',side_effect=traced_connect), \
                        mock.patch.object(workflow_store.json,'loads',side_effect=loads):
                    raw=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(root))
                self.assertTrue(raw['available'],raw)
                self.assertEqual(1,len(decoded))
                artifact_queries=[s for s in statements if s.lstrip().upper().startswith('SELECT') and 'FROM ARTIFACTS ' in s.upper()]
                self.assertEqual(3,len(artifact_queries),artifact_queries) # two scoped chunks, one unscoped query
                copies=[a for r in raw['records']+raw['history'] for a in r['artifacts'] if a.get('artifact',{}).get('artifact_id')=='artifact_unscoped_once']
                self.assertEqual(0,len(copies))  # no selected submission owns this row
            finally: backend.close()

    def test_live_board_route_uses_light_reader_not_audit_reader(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,*rest=self._accepted(root,'board-route-light')
            backend=rest[-1]
            try:
                coordinator=workflow_coordinator.ExplicitCoordinator(store,bindings={'unused':object()},
                    artifact_dir=root/'artifacts',identity_verifier=lambda *_:True,
                    owner_bindings={'board-token':workflow_coordinator.OwnerBinding('board-owner',str(case.repo.resolve()))})
                with mock.patch.object(workflow_store.WorkflowStore,'readonly_snapshot',side_effect=AssertionError('audit snapshot called')):
                    response=TestClient(coordinator.app()).get('/v1/board/snapshot',headers={'Authorization':'Bearer board-token'})
                self.assertEqual(200,response.status_code,response.text)
            finally:
                backend.close()

    def test_historical_board_read_does_not_decode_owner_or_regression_payloads(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,feature,*rest=self._accepted(root,'history-summary-boundary'); backend=rest[-1]
            try:
                db=sqlite3.connect(store.path)
                try:
                    db.execute('DELETE FROM feature_heads WHERE run_id=?',(feature['run_id'],))
                    db.execute("UPDATE owner_decisions SET decision_json=json_set(decision_json,'$.findings','HISTORICAL_PRIVATE_FINDINGS') WHERE run_id=?",(feature['run_id'],))
                    db.execute("UPDATE regression_jobs SET inputs_json=json_object('private_payload','HISTORICAL_PRIVATE_REGRESSION_INPUTS')")
                    db.commit()
                    audit=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                    self.assertIn('HISTORICAL_PRIVATE_FINDINGS',json.dumps(audit['history']))
                    self.assertIn('HISTORICAL_PRIVATE_REGRESSION_INPUTS',json.dumps(audit['history']))
                finally: db.close()
                original=json.loads
                def guarded_loads(value,*args,**kwargs):
                    self.assertNotIn('HISTORICAL_PRIVATE_FINDINGS',value)
                    self.assertNotIn('HISTORICAL_PRIVATE_REGRESSION_INPUTS',value)
                    return original(value,*args,**kwargs)
                with mock.patch.object(workflow_store.json,'loads',side_effect=guarded_loads):
                    light=workflow_store.WorkflowStore.readonly_board_snapshot(store.path,worktree=str(case.repo.resolve()))
                self.assertTrue(light['available'],light)
                self.assertEqual(feature['run_id'],light['history'][0]['run']['run_id'])
                self.assertEqual([],light['history'][0]['owner_decisions'])
                self.assertEqual([],light['history'][0]['regression_jobs'])
            finally: backend.close()

    def test_historical_incident_projection_preserves_missing_null_and_json_types(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,feature,*rest=self._accepted(root,'incident-type-boundary'); backend=rest[-1]
            try:
                db=sqlite3.connect(store.path)
                try:
                    db.execute('DELETE FROM feature_heads WHERE run_id=?',(feature['run_id'],))
                    base={'contract_version':1,'incident_id':'incident_missing_kind','run_id':feature['run_id'],
                        'state':'OPEN','message':'Missing kind','owner_action_required':False,'evaluation_generation':1}
                    explicit=dict(base,incident_id='incident_explicit_null',kind=None,message='Explicit null')
                    for incident in (base,explicit):
                        db.execute("INSERT INTO incidents(id,run_id,generation,incident_json) VALUES(?,?,1,?)",
                            (incident['incident_id'],feature['run_id'],json.dumps(incident)))
                    db.commit()
                finally: db.close()
                full=workflow_store.WorkflowStore.readonly_snapshot(store.path,worktree=str(case.repo.resolve()))
                compact=workflow_store.WorkflowStore.readonly_board_snapshot(store.path,worktree=str(case.repo.resolve()))
                full_record=full['history'][0]; compact_record=compact['history'][0]
                full_faults=workflow_board._record_faults(full_record,feature['run_id'],str(case.repo.resolve()))
                compact_faults=workflow_board._record_faults(compact_record,feature['run_id'],str(case.repo.resolve()))
                self.assertEqual(full_faults,compact_faults)
                by_id={fault['id']:fault for fault in compact_faults[0]}
                self.assertEqual('incident',by_id['incident_missing_kind']['kind'])
                self.assertIsNone(by_id['incident_explicit_null']['kind'])
                self.assertIs(False,by_id['incident_missing_kind']['owner_action_required'])
                self.assertIs(type(1),type(by_id['incident_missing_kind']['evaluation_generation']))
            finally: backend.close()


if __name__=='__main__': unittest.main()
