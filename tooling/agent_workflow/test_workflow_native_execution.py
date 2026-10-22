import unittest
import tempfile, os, sys, time, shutil, json, hashlib
import uuid
from pathlib import Path
from unittest.mock import patch
import workflow_native_execution
import workflow_native_worker, workflow_submission, workflow_store, workflow_preflight, gate_execution
from preflight_test_support import make_preflight_case


def admit_native_fixture(root, *, caller, key, shared_store=None, timeline=None, delay=.2, review_roles=None, fail_check=False, extra_files=None, producer_code=None, checks=None, author_session=None):
    root=Path(root); case=make_preflight_case(root,repo_name='repo',caller=caller,key=key)
    if author_session is not None:
        case.author['session_id']=author_session
        case.request['author']=case.author; case.policy['author']=case.author
        case.request['policy_digest']=workflow_store.digest(case.policy)
        case.context=workflow_preflight.PreflightContext(case.context.repo,case.author,case.policy,
            case.context.artifacts,case.context.publication_receipt_path,case.context.publication_receipt_sha256)
    if review_roles is not None:
        case.policy['review_roles']=list(review_roles)
        case.policy['review_summaries']={role:f'Assess {role} against agreed evidence.' for role in review_roles}
        case.context=workflow_preflight.PreflightContext(case.context.repo,case.context.authenticated_author,case.policy,
            case.context.artifacts,case.context.publication_receipt_path,case.context.publication_receipt_sha256)
        case.request['policy_digest']=workflow_store.digest(case.policy)
    unique_evidence='artifact_evidence_'+workflow_store.digest({'caller':caller,'key':key})[:12]
    case.artifact['artifact_id']=unique_evidence; case.artifact_id=unique_evidence
    for evidence in case.request['criteria'][0]['evidence_refs']:
        if evidence=='artifact_evidence': case.request['criteria'][0]['evidence_refs'][0]=unique_evidence
    producer=case.repo/'producer.py'; output='build/output.zip'
    timeline_path=str(Path(timeline).resolve()) if timeline else ''
    producer.write_text("import json,time\nfrom pathlib import Path\nt=%r\n\ndef mark(phase):\n if t:\n  with open(t,'a',encoding='utf8') as f: f.write(json.dumps({'run':%r,'phase':phase,'time_ns':time.time_ns()})+'\\n')\nmark('start')\ntime.sleep(%r)\np=Path(%r);p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'pass')\nc=Path('counter.txt');c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\nmark('finish')\n" % (timeline_path,key,delay,output))
    if producer_code is not None: producer.write_text(producer_code)
    if fail_check:
        producer.write_text(producer.read_text()+"raise SystemExit(1)\n")
    (case.repo/'.gitignore').write_text('counter.txt\nbuild/\n')
    for rel, content in (extra_files or {}).items():
        dest=case.repo/rel; dest.parent.mkdir(parents=True,exist_ok=True); dest.write_text(content)
    plan={'registry_version':1,'packs':[],'plan':{'title':'native fixture','kind':'feature','acceptance_criteria':['c1'],'fixture':'manual.md','manual_steps':['Run.'],'references':{},'checks':checks if checks is not None else [{'argv':[sys.executable,str(producer)],'rationale':'fixture','covers':['c1'],'kind':'build','artifacts':[output]}]},'program_checks':[]}
    case.request['input_digest']=workflow_store.digest(workflow_preflight.capture_source(case.repo)); case.request['plan_digest']=workflow_store.digest(plan)
    store=shared_store or workflow_store.WorkflowStore(root/'db.sqlite')
    with patch.object(workflow_preflight,'resolve_check_packs',return_value=plan):
        receipt=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=case.key,request=case.request,context=case.context,artifact_dir=root/'artifacts')
    db=store._connect()
    try: task_id=db.execute('select id from tasks where run_id=?',(receipt['run_id'],)).fetchone()[0]
    finally: db.close()
    return case,store,receipt,task_id


def settle_native_error_fixture(store, task_id):
    """Finalize a completed ERROR task only after its saved native cleanup proof."""
    task=store.get_task(task_id)
    if not task or task.get('state')!='COMPLETED' or (task.get('result') or {}).get('decision')!='ERROR':
        raise AssertionError('fixture settlement requires the saved task to be COMPLETED with ERROR')
    resources=store.get_task_resources(task_id)
    if not resources or any(resource.get('state')!='RELEASED' for resource in resources):
        raise AssertionError('fixture settlement requires every actual task resource to be RELEASED')
    scope=store.get_native_scope(task_id)
    if not scope or scope.get('state')!='CLEANED' or not scope.get('cleanup_artifact_ref'):
        raise AssertionError('fixture settlement requires a saved CLEANED native scope')
    binding=store.get_artifact(scope['cleanup_artifact_ref'])
    if not binding:
        raise AssertionError('fixture settlement requires the registered native cleanup artifact')
    import workflow_native_cancellation
    workflow_native_cancellation.NativeCancellation(store,Path(binding['artifact']['path']).parent)._verify_artifact(
        task,scope,scope['cleanup_artifact_ref'])
    for resource in resources:
        cleanup=store.get_artifact(resource.get('cleanup_proof'))
        if not cleanup:
            raise AssertionError('fixture settlement requires each RELEASED resource cleanup artifact')
        artifact=cleanup['artifact']; raw=Path(artifact['path']).read_bytes()
        proof=workflow_store.strict_loads(raw.decode('utf-8'))
        if (artifact.get('type')!='PROVENANCE' or artifact.get('immutable') is not True or
                artifact.get('producer_task_id')!=task_id or artifact.get('run_id')!=task['run_id'] or
                artifact.get('producer_kind')!='TASK' or artifact.get('producer_id')!=task_id or
                cleanup.get('input_digest')!=task['input_digest'] or
                artifact.get('size_bytes')!=len(raw) or artifact.get('sha256')!=hashlib.sha256(raw).hexdigest() or
                proof!={'claim_id':resource['claim_id'],'identity_proof':resource['identity_proof'],
                    'native_scope_cleanup_artifact_ref':scope['cleanup_artifact_ref'],
                    'method':'OWNED_NATIVE_SCOPE_ZERO_ACTIVE'}):
            raise AssertionError('fixture settlement requires bound immutable resource cleanup proof')
    run_id=task['run_id']; generation=task['evaluation_generation']
    incident={'contract_version':1,'incident_id':'incident_fixture_settle_'+task_id,
        'run_id':run_id,'evaluation_generation':generation,'task_id':task_id,
        'code':'NATIVE_TASK_ERROR_SETTLED','message':'Completed native task reported ERROR after verified scope cleanup',
        'recovery_owner':'SYSTEM','state':'OPEN','owner_action_required':False,
        'artifact_refs':task['result']['artifact_refs']}
    current=store.get_run(run_id)
    if not current or current['attempt']['phase']!='CHECKING':
        raise AssertionError('fixture settlement requires the current attempt to remain CHECKING')
    store.transition_attempt(run_id,generation,expected_sequence=current['run']['sequence'],
        phase='STOPPING',proposed_result='ERROR',incident=incident,
        command_id='fixture-error-stopping:'+task_id)
    current=store.get_run(run_id)
    store.transition_attempt(run_id,generation,expected_sequence=current['run']['sequence'],
        phase='DECIDED',terminal_result='ERROR',cleanup_state='CONFIRMED',incident=incident,
        command_id='fixture-error-decided:'+task_id)
    settled=store.get_run(run_id)
    if settled['attempt']['phase']!='DECIDED' or settled['attempt']['result']!='ERROR':
        raise AssertionError('fixture settlement did not preserve the terminal ERROR verdict')
    return settled


class NativeExecutionBackendTests(unittest.TestCase):
    def test_competing_authors_serialize_checking_before_one_worker_spawn(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'shared.sqlite')
            submissions=[]; authors=[]
            for index in range(2):
                case,_,receipt,task_id=admit_native_fixture(root/f'author{index}',caller=f'author-{index}',key=f'lane-{index}',shared_store=store,author_session=f'session-author-{index}')
                submissions.append((receipt['run_id'],task_id))
                authors.append(case.author)
            self.assertNotEqual(authors[0]['session_id'],authors[1]['session_id'])
            self.assertNotEqual(authors[0]['worktree'],authors[1]['worktree'])
            backends=[workflow_native_execution.NativeExecutionBackend(store,root/'runtime-artifacts',root/'lock') for _ in submissions]
            start=__import__('threading').Barrier(2); popen_calls=[]; outcomes=[]; lock=__import__('threading').Lock()
            class LaunchAttempted(Exception): pass
            def fake_popen(*args,**kwargs):
                with lock: popen_calls.append(args[0])
                raise LaunchAttempted('contained fake launch boundary')
            def dispatch(index):
                start.wait()
                try:
                    value=backends[index].dispatch('event-lane-'+str(index),{'task_id':submissions[index][1]})
                    outcomes.append((index,'return',value))
                except LaunchAttempted:
                    outcomes.append((index,'launch_attempted',None))
                except Exception as exc:
                    outcomes.append((index,'error',type(exc).__name__))
            threads=[__import__('threading').Thread(target=dispatch,args=(i,)) for i in range(2)]
            try:
                with patch.object(workflow_native_execution.subprocess,'Popen',side_effect=fake_popen):
                    for thread in threads: thread.start()
                    for thread in threads: thread.join(5)
                self.assertTrue(all(not thread.is_alive() for thread in threads))
                self.assertEqual(len(popen_calls),1)
                self.assertEqual(sum(row[1]=='launch_attempted' for row in outcomes),1)
                self.assertEqual(sum(row[1]=='return' and row[2]=='PENDING' for row in outcomes),1)
                phases=[store.get_run(run_id)['attempt']['phase'] for run_id,_ in submissions]
                self.assertEqual(phases.count('CHECKING'),1); self.assertEqual(phases.count('QUEUED'),1)
                pending_index=next(i for i,kind,value in outcomes if kind=='return' and value=='PENDING')
                pending_task=submissions[pending_index][1]
                self.assertFalse((root/'runtime-artifacts'/pending_task/'worker.log').exists())
                self.assertIsNone(store.get_native_claim(pending_task))
            finally:
                for backend in backends: backend.close()

    def test_same_current_run_can_admit_additional_check_tasks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            case,store,receipt,first=admit_native_fixture(root,caller='same-run-author',key='same-run-lane')
            db=store._connect()
            try: second=json.loads(db.execute('SELECT task_json FROM tasks WHERE id=?',(first,)).fetchone()[0])
            finally: db.close()
            second['task_id']='task_same_run_additional'
            store.add_task(second,logical_task_key='additional-check')
            db=store._connect()
            try: tasks=[row[0] for row in db.execute('SELECT id FROM tasks WHERE run_id=? ORDER BY id',(receipt['run_id'],))]
            finally: db.close()
            self.assertEqual(len(tasks),2)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            calls=[]
            class LaunchAttempted(Exception): pass
            def fake_popen(*args,**kwargs): calls.append(args[0]); raise LaunchAttempted()
            try:
                with patch.object(workflow_native_execution.subprocess,'Popen',side_effect=fake_popen):
                    for task_id in tasks:
                        with self.assertRaises(LaunchAttempted): backend.dispatch('event-'+task_id,{'task_id':task_id})
                self.assertEqual(len(calls),2)
                self.assertEqual(store.get_run(receipt['run_id'])['attempt']['phase'],'CHECKING')
            finally: backend.close()

    def test_existing_checking_attempt_revalidates_lane_before_spawn(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'shared.sqlite'); rows=[]
            for index in range(2):
                _case,_store,receipt,task_id=admit_native_fixture(root/f'legacy{index}',caller=f'legacy-{index}',key=f'legacy-lane-{index}',shared_store=store)
                rows.append((receipt['run_id'],task_id))
            first=store.get_run(rows[0][0]);store.transition_attempt(rows[0][0],1,expected_sequence=first['run']['sequence'],phase='CHECKING')
            # Model an already persisted CHECKING attempt from before the global
            # lane guard existed. It must not bypass the backend's prelaunch check.
            db=store._connect()
            try:
                saved=json.loads(db.execute('SELECT attempt_json FROM attempts WHERE run_id=? AND generation=1',(rows[1][0],)).fetchone()[0]);saved['phase']='CHECKING'
                db.execute('UPDATE attempts SET attempt_json=? WHERE run_id=? AND generation=1',(workflow_store.canonical_json(saved),rows[1][0]));db.commit()
            finally: db.close()
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            try:
                with patch.object(workflow_native_execution.subprocess,'Popen') as popen:
                    self.assertEqual(backend.dispatch('legacy-wake',{'task_id':rows[1][1]}),'PENDING')
                popen.assert_not_called()
                self.assertFalse((root/'artifacts'/rows[1][1]/'worker.log').exists())
            finally: backend.close()

    def test_claim_token_starting_with_dash_is_passed_as_option_value(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,task_id=admit_native_fixture(root,caller='dash-token-caller',key='dash-token-key',delay=.1)
            token='-leading-token-value'
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None
            try:
                with patch.object(workflow_native_execution.secrets,'token_urlsafe',return_value=token):
                    backend.dispatch('event-dash-token',{'task_id':task_id})
                proc=backend.workers[task_id]; proc.wait(timeout=20)
                self.assertEqual(store.get_task(task_id)['state'],'COMPLETED')
                self.assertEqual(store.get_task(task_id)['result']['decision'],'PASS')
                self.assertEqual((case.repo/'counter.txt').read_text(),'1')
                self.assertEqual(store.get_native_claim(task_id)['claim_token'],token)
                self.assertIsNone(gate_execution.process_identity(proc.pid),'owned worker PID should be dead after completion')
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)

    def test_exited_launcher_failure_is_reported_without_tree_kill(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            class Store:
                path=root/'db.sqlite'
                def get_task(self, task_id): return {'state':'QUEUED','run_id':'run','evaluation_generation':1}
                def get_native_claim(self, task_id): return None
                def get_run(self, run_id): return {'attempt':{'phase':'CHECKING'}}
                def assert_submission_lane(self, run_id, generation): return None
            backend=workflow_native_execution.NativeExecutionBackend(Store(),root/'artifacts',root/'lock')
            class Exited:
                pid=4321
                def poll(self): return 128
            proc=Exited()
            def launch(*args,**kwargs):
                log=kwargs['stdout']; log.write(b'startup diagnostic\n'); log.flush()
                return proc
            with patch.object(workflow_native_execution.subprocess,'Popen',side_effect=launch), \
                 patch.object(workflow_native_execution.workflow_runtime,'stop_owned_tree') as stop:
                with self.assertRaisesRegex(RuntimeError,'startup failed exit 128') as error:
                    backend.dispatch('event',{'task_id':'task'})
            stop.assert_not_called()
            self.assertIn('startup diagnostic',str(error.exception))

    def test_missing_task_is_absent(self):
        class Store:
            def get_task(self, task_id): return None
        backend=workflow_native_execution.NativeExecutionBackend(Store(),'.','.')
        self.assertEqual('ABSENT',backend.observe('event',{'task_id':'missing'}))

    def test_authoritative_admission_runs_worker_and_observes_completion(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case=make_preflight_case(root,repo_name='repo',caller='native-backend',key='native-backend')
            producer=case.repo/'producer.py'; counter=case.repo/'counter.txt'
            producer.write_text("from pathlib import Path\nimport time\ntime.sleep(.2)\np=Path('build/output.zip');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b'pass')\nc=Path('counter.txt');c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\n")
            (case.repo/'.gitignore').write_text('counter.txt\nbuild/\n')
            plan={'registry_version':1,'packs':[],'plan':{'title':'native fixture','kind':'feature','acceptance_criteria':['c1'],'fixture':'manual.md','manual_steps':['Run.'],'references':{},'checks':[{'argv':[sys.executable,str(producer)],'rationale':'fixture','covers':['c1'],'kind':'build','artifacts':['build/output.zip']}]},'program_checks':[]}
            case.request['input_digest']=workflow_store.digest(workflow_preflight.capture_source(case.repo)); case.request['plan_digest']=workflow_store.digest(plan)
            store=workflow_store.WorkflowStore(root/'db.sqlite')
            with patch.object(workflow_preflight,'resolve_check_packs',return_value=plan):
                receipt=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=case.key,request=case.request,context=case.context,artifact_dir=root/'artifacts')
            db=store._connect()
            try: task_id=db.execute('select id from tasks where run_id=?',(receipt['run_id'],)).fetchone()[0]
            finally: db.close()
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None
            try:
                backend.dispatch('event',{'task_id':task_id})
                proc=backend.workers[task_id]; deadline=time.monotonic()+20
                scope_identity=backend.scope_identities[task_id]
                self.assertEqual(scope_identity['pid'],backend.store.get_native_claim(task_id)['process_identity']['pid'])
                self.assertIn('created',scope_identity)
                self.assertNotIn('created_ms',scope_identity)
                self.assertNotEqual(scope_identity['pid'],proc.pid,'scope must contain acknowledged interpreter, not launcher')
                self.assertIn(scope_identity,backend.scopes[task_id].snapshot()['identities'])
                scope_row=store.get_native_scope(task_id)
                self.assertEqual(scope_row['worker_identity'],scope_identity)
                self.assertEqual(scope_row['worker_identity']['pid'],store.get_native_claim(task_id)['process_identity']['pid'])
                proof_artifact=store.get_artifact(scope_row['creation_artifact_ref'])['artifact']
                proof_bytes=Path(proof_artifact['path']).read_bytes()
                self.assertEqual(len(proof_bytes),proof_artifact['size_bytes'])
                self.assertEqual(hashlib.sha256(proof_bytes).hexdigest(),proof_artifact['sha256'])
                proof=json.loads(proof_bytes)
                self.assertEqual({k:proof[k] for k in ('scope_name','task_id','run_id','evaluation_generation','assignment_generation','input_digest','worker_identity')},
                    {'scope_name':scope_row['scope_name'],'task_id':task_id,'run_id':receipt['run_id'],
                     'evaluation_generation':store.get_task(task_id)['evaluation_generation'],'assignment_generation':store.get_task(task_id)['assignment_generation'],
                     'input_digest':store.get_task(task_id)['input_digest'],'worker_identity':scope_identity})
                self.assertTrue(proof['kill_on_close']); self.assertTrue(proof['non_inherited_handle'])
                while time.monotonic()<deadline and proc.poll() is None: time.sleep(.025)
                if proc.poll() is None:
                    preserve=Path(__file__).resolve().parents[2]/'build/agent-workflow/workflow-redesign-plan/wr12/attempts'/f'native-dispatch-failure-{uuid.uuid4()}'
                    preserve.mkdir(parents=True,exist_ok=False)
                    shutil.copy2(root/'artifacts'/task_id/'worker.log',preserve/'worker.log')
                    diagnostic={'task':store.get_task(task_id),'claim':store.get_native_claim(task_id),'resource_history':store.get_resource_history('native_validation'),
                        'parent_identity':workflow_native_worker.native_process_identity(os.getpid()),'child_identity':workflow_native_worker.native_process_identity(proc.pid),'child_pid':proc.pid,'child_poll':proc.poll()}
                    (preserve/'state.json').write_text(json.dumps(diagnostic,default=str,indent=2))
                    print('native dispatch failure diagnostics retained at '+str(preserve))
                    self.fail('worker did not exit before 20-second test deadline; evidence preserved at '+str(preserve))
                task=store.get_task(task_id)
                if task['state']!='COMPLETED':
                    preserve=Path(__file__).resolve().parents[2]/'build/agent-workflow/workflow-redesign-plan/wr12/attempts'/f'native-dispatch-failure-{uuid.uuid4()}'
                    preserve.mkdir(parents=True,exist_ok=False)
                    shutil.copy2(root/'artifacts'/task_id/'worker.log',preserve/'worker.log')
                    (preserve/'state.json').write_text(json.dumps({'task':task,'claim':store.get_native_claim(task_id),'resource_history':store.get_resource_history('native_validation'),'parent_identity':workflow_native_worker.native_process_identity(os.getpid()),'child_identity':workflow_native_worker.native_process_identity(proc.pid),'child_pid':proc.pid,'child_poll':proc.poll()},default=str,indent=2))
                    print('native dispatch failure diagnostics retained at '+str(preserve))
                self.assertEqual(task['state'],'COMPLETED'); self.assertEqual(task['result']['decision'],'PASS')
                self.assertIsNone(gate_execution.process_identity(proc.pid),'owned worker PID should be dead after result commit')
                self.assertEqual(counter.read_text(),'1')
                self.assertEqual(backend.observe('event',{'task_id':task_id}),'APPLIED')
                backend.dispatch('event',{'task_id':task_id})
                self.assertEqual(counter.read_text(),'1')
            finally:
                backend.close()
                if proc is not None: proc.wait(timeout=2)

    def test_worker_cli_rejects_saved_claim_without_scope(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,task_id=admit_native_fixture(root,caller='scope-guard',key='scope-guard',delay=.1)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None; original=store.dispatch_task
            def drop_scope_for_test(**kwargs):
                kwargs.pop('native_scope')
                return original(**kwargs)
            try:
                with patch.object(store,'dispatch_task',side_effect=drop_scope_for_test):
                    backend.dispatch('event-scope-guard',{'task_id':task_id})
                proc=backend.workers[task_id]; proc.wait(timeout=20)
                self.assertNotEqual(proc.returncode,0)
                self.assertIsNone(store.get_native_scope(task_id))
                self.assertEqual(store.get_task(task_id)['state'],'DISPATCHING')
                self.assertEqual(store.get_resource_history('native_validation'),[])
                self.assertFalse((case.repo/'counter.txt').exists())
                self.assertIn('native-worker-scope-missing',(root/'artifacts'/task_id/'worker.log').read_text())
            finally:
                backend.close()
                if proc is not None and proc.poll() is None: proc.wait(timeout=3)

    def test_independent_workers_serialize_shared_native_resource(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); store=workflow_store.WorkflowStore(root/'shared.sqlite'); timeline=root/'timeline.jsonl'
            rows=[]; workers=[]; backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'shared-lock')
            try:
                case,_,receipt,first_task=admit_native_fixture(root/'case',caller='mutex-author',key='mutex-run',shared_store=store,timeline=timeline,delay=.25)
                db=store._connect()
                try: second_task_data=json.loads(db.execute('SELECT task_json FROM tasks WHERE id=?',(first_task,)).fetchone()[0])
                finally: db.close()
                second_task='task_mutex_duplicate_check'
                second_task_data['task_id']=second_task
                # Synthetic duplicate-producer batch: exercise physical resource
                # serialization within one active submission. This does not
                # claim that a resolved production check plan creates parallel
                # duplicate producer tasks.
                store.add_task(second_task_data,logical_task_key='synthetic-duplicate-producer-check')
                cases=[(case,first_task),(case,second_task)]
                for case,task_id in cases: backend.dispatch('event-'+task_id,{'task_id':task_id})
                workers=list(backend.workers.values())
                self.assertEqual(len(workers),2,'both same-run check workers should be launched before waiting')
                deadline=time.monotonic()+20
                while time.monotonic()<deadline and any(p.poll() is None for p in workers): time.sleep(.025)
                self.assertTrue(all(p.poll() is not None for p in workers),'native worker deadline')
                for case,task_id in cases:
                    task=store.get_task(task_id); self.assertEqual(task['state'],'COMPLETED'); self.assertEqual(task['result']['decision'],'PASS')
                self.assertEqual((case.repo/'counter.txt').read_text(),'2')
                rows=[json.loads(line) for line in timeline.read_text().splitlines()]
                self.assertEqual([row['phase'] for row in rows],['start','finish','start','finish'])
                history=store.get_resource_history('native_validation')
                self.assertEqual(len(history),2); self.assertTrue(all(item['state']=='RELEASED' for item in history))
            finally:
                backend.close()
                for proc in workers: proc.wait(timeout=3)

    def test_fresh_backend_observes_inflight_worker_without_relaunch(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,task_id=admit_native_fixture(root,caller='observe-caller',key='observe-key',delay=1)
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            fresh=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock')
            proc=None
            try:
                backend.dispatch('event-observe',{'task_id':task_id}); proc=backend.workers[task_id]
                self.assertEqual(fresh.observe('event-observe',{'task_id':task_id}),'APPLIED')
                self.assertEqual(fresh.workers,{})
                deadline=time.monotonic()+20
                while time.monotonic()<deadline and proc.poll() is None: time.sleep(.025)
                self.assertIsNotNone(proc.poll()); self.assertEqual(store.get_task(task_id)['result']['decision'],'PASS')
                self.assertEqual(fresh.observe('event-observe',{'task_id':task_id}),'APPLIED')
                backend.dispatch('event-observe',{'task_id':task_id})
                self.assertEqual((case.repo/'counter.txt').read_text(),'1')
            finally:
                backend.close(); fresh.close()
                if proc is not None: proc.wait(timeout=3)

    def test_completion_wakes_coordinator_callback_after_native_result_commit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); case,store,receipt,task_id=admit_native_fixture(root,caller='notify-caller',key='notify-key',delay=.4)
            notified=[]; event=__import__('threading').Event()
            def notify(): notified.append(store.get_task(task_id)['state']); event.set()
            backend=workflow_native_execution.NativeExecutionBackend(store,root/'artifacts',root/'lock',notify)
            try:
                backend.dispatch('event-notify',{'task_id':task_id})
                proc=backend.workers[task_id]
                self.assertIsNone(proc.poll(),'worker should still be active during its check')
                self.assertTrue(event.wait(2),'dispatch commit did not notify coordinator')
                event.clear()
                self.assertTrue(event.wait(10),'worker completion did not notify coordinator')
                proc.wait(timeout=2)
                self.assertIn('COMPLETED',notified)
                self.assertEqual(store.get_task(task_id)['result']['decision'],'PASS')
            finally: backend.close()


if __name__=='__main__': unittest.main()
