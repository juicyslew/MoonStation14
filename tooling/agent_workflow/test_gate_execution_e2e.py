"""End-to-end gate execution tests using real finite OS processes.

The check commands are Python fixtures, not game/runtime checks.  The workflow
fixture is intentionally reused from test_workflow rather than duplicating its
Git/candidate construction rules.
"""
import json
import shutil
import subprocess
import sys
import time
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

try:
    import test_workflow as workflow_fixtures
    import gate_execution
except ModuleNotFoundError:
    from tooling.agent_workflow import test_workflow as workflow_fixtures
    from tooling.agent_workflow import gate_execution

wf = workflow_fixtures.wf


_GATE_JOB_STATES=('requested','admitted','launching','running','completed','blocked','interrupted')


def _known_gate_job_state(job):
    if not isinstance(job,dict):
        return None
    state=job.get('state')
    return state if state in _GATE_JOB_STATES else None


def _read_progress_record(path,reader=None):
    try:
        progress=(reader or wf.readjson)(path)
        return (progress,True) if isinstance(progress,dict) else (None,False)
    except Exception:
        return None,False


def _valid_gate_process_identity(identity):
    return (isinstance(identity,dict) and type(identity.get('pid')) is int and
            identity.get('pid')>0 and identity.get('created') is not None)


def _valid_optional_gate_identity_pair(pid,identity):
    if pid is None and identity is None:
        return True
    return (type(pid) is int and pid>0 and _valid_gate_process_identity(identity) and
            identity.get('pid')==pid)


def _current_test_failed_or_unverified(testcase):
    """Consult durable result records, not _Outcome.success which tearDown resets."""
    try:
        result=testcase._outcome.result
        if result is None:
            return True
        for records in (result.failures,result.errors):
            if not isinstance(records,(list,tuple)):
                return True
            for record in records:
                if not isinstance(record,(list,tuple)) or not record:
                    return True
                recorded_test=record[0]
                if recorded_test is testcase or getattr(recorded_test,'test_case',None) is testcase:
                    return True
                if (not isinstance(recorded_test,unittest.TestCase) and
                        not isinstance(getattr(recorded_test,'test_case',None),unittest.TestCase)):
                    return True
        return False
    except Exception:
        return True


class FailureRetentionPredicateTests(unittest.TestCase):
    def test_result_records_survive_teardown_success_reset_and_are_test_scoped(self):
        def run_probe(body,result=None):
            observations={}
            class Probe(unittest.TestCase):
                def runTest(self): body(self)
                def tearDown(self):
                    observations['success']=self._outcome.success
                    observations['retain']=_current_test_failed_or_unverified(self)
            result=result or unittest.TestResult()
            Probe().run(result)
            return result,observations

        failed_result,failed=run_probe(lambda case:case.fail('method failure'))
        self.assertTrue(failed['success'],'regression requires teardown to reset transient success')
        self.assertTrue(failed['retain']); self.assertEqual(len(failed_result.failures),1)
        error_result,error=run_probe(lambda _case:(_ for _ in ()).throw(RuntimeError('method error')))
        self.assertTrue(error['retain']); self.assertEqual(len(error_result.errors),1)
        def subtest_failure(case):
            with case.subTest(part='nested'):
                case.fail('subtest failure')
        subtest_result,subtest=run_probe(subtest_failure)
        self.assertTrue(subtest['retain']); self.assertEqual(len(subtest_result.failures),1)
        success_result,success=run_probe(lambda _case:None)
        self.assertFalse(success['retain']); self.assertEqual((success_result.failures,success_result.errors),([],[]))
        other_result,_=run_probe(lambda case:case.fail('different test failure'))
        _same_result,current=run_probe(lambda _case:None,other_result)
        self.assertFalse(current['retain'],'unrelated test failure must not retain this successful fixture')

    def test_teardown_retention_rejects_unknown_or_unreadable_job_records(self):
        self.assertEqual(_known_gate_job_state({'state':'requested'}),'requested')
        self.assertEqual(_known_gate_job_state({'state':'admitted'}),'admitted')
        self.assertEqual(_known_gate_job_state({'state':'completed'}),'completed')
        self.assertIsNone(_known_gate_job_state({'state':'mystery'}))
        self.assertIsNone(_known_gate_job_state(None))
        self.assertIsNone(_known_gate_job_state({'state':[]}))
        for waiting in ([{'state':'running'},{'state':'requested'}],
                        [{'state':'completed'},{'state':'requested'}],
                        [{'state':'running'},{'state':'admitted'}]):
            self.assertFalse(any(_known_gate_job_state(job) is None for job in waiting),waiting)
        self.assertEqual(_read_progress_record('fixture',lambda _path:{'child_pid':123}),({'child_pid':123},True))
        self.assertEqual(_read_progress_record('fixture',lambda _path:[]),(None,False))
        self.assertEqual(_read_progress_record('fixture',lambda _path:(_ for _ in ()).throw(OSError('unreadable'))),(None,False))
        self.assertTrue(_valid_gate_process_identity({'pid':123,'created':456}))
        for malformed in (None,[],{'pid':'123','created':456},{'pid':0,'created':456},{'pid':123}):
            self.assertFalse(_valid_gate_process_identity(malformed))

    def test_optional_identity_pair_rejects_dangling_and_malformed_worker_or_child_records(self):
        valid_identity={'pid':123,'created':456}
        for state in ('requested','admitted','interrupted','completed'):
            worker_record={'state':state,'worker_pid':123,'worker_identity':None}
            child_record={'state':state,'owned_child':{'pid':123,'identity':None}}
            self.assertEqual(_known_gate_job_state(worker_record),state)
            self.assertEqual(_known_gate_job_state(child_record),state)
            self.assertFalse(_valid_optional_gate_identity_pair(worker_record['worker_pid'],worker_record['worker_identity']),state)
            self.assertFalse(_valid_optional_gate_identity_pair(child_record['owned_child']['pid'],child_record['owned_child']['identity']),state)
        self.assertFalse(_valid_optional_gate_identity_pair(None,valid_identity))
        self.assertFalse(_valid_optional_gate_identity_pair(124,valid_identity))
        for pid in (True,False,-1,0):
            self.assertFalse(_valid_optional_gate_identity_pair(pid,{'pid':pid,'created':456}))
        self.assertTrue(_valid_optional_gate_identity_pair(None,None))
        self.assertTrue(_valid_optional_gate_identity_pair(123,valid_identity))


class GateExecutionEndToEndTests(unittest.TestCase):
    def setUp(self):
        self.fixture = workflow_fixtures.WorkflowTests(methodName='runTest')
        self.fixture.setUp()
        self.root = self.fixture.root / 'work' / 'agent_exchange'
        # These tests use finite Python/Git fixtures only. Fail closed if a
        # regression tries to invoke the developer's live OpenCode service.
        self._real_run = subprocess.run
        self._real_popen = subprocess.Popen
        def guarded_run(command, *args, **kwargs):
            if self._is_opencode(command):
                raise AssertionError('Live OpenCode transport forbidden in isolated tests')
            return self._real_run(command, *args, **kwargs)
        def guarded_popen(command, *args, **kwargs):
            if self._is_opencode(command):
                raise AssertionError('Live OpenCode transport forbidden in isolated tests')
            return self._real_popen(command, *args, **kwargs)
        self._run_guard = patch.object(subprocess, 'run', side_effect=guarded_run)
        self._popen_guard = patch.object(subprocess, 'Popen', side_effect=guarded_popen)
        self._run_guard.start()
        self._popen_guard.start()

    @staticmethod
    def _is_opencode(command):
        if isinstance(command, (str, bytes)):
            import shlex
            parts = shlex.split(command)
        else:
            parts = command
        return bool(parts) and Path(str(parts[0])).name.lower() in ('opencode', 'opencode.exe')

    def _retain_fixture_artifacts(self):
        # TemporaryDirectory otherwise deletes the preserved failure root when
        # its finalizer runs; detach only this test fixture's exact finalizer.
        self.fixture.tmp._finalizer.detach()

    def tearDown(self):
        # Gate workers are children of the test runner, not returned Popen handles.
        # Only stop PIDs recorded in this private fixture's own execution/progress files.
        records_read=True
        for path in (self.root/'gate-execution').glob('*.json') if (self.root/'gate-execution').is_dir() else ():
            try: job=gate_execution.read(self.root,path.stem)
            except Exception:
                records_read=False
                continue
            if not isinstance(job,dict):
                records_read=False
                continue
            state=_known_gate_job_state(job)
            child_record=job.get('owned_child')
            if child_record is not None and not isinstance(child_record,dict):
                records_read=False
                child={}
            else:
                child=child_record or {}
            child_saved_pid=child.get('pid'); child_identity=child.get('identity')
            worker_pid=job.get('worker_pid'); worker_identity=job.get('worker_identity')
            if not _valid_optional_gate_identity_pair(worker_pid,worker_identity): records_read=False
            if not _valid_optional_gate_identity_pair(child_saved_pid,child_identity): records_read=False
            if state is None:
                records_read=False
                continue
            if state not in ('launching','running'): continue
            progress_path=self.root/'progress'/(path.stem+'.json')
            progress,progress_read=_read_progress_record(progress_path)
            if not progress_read:
                records_read=False
                progress={}
            child_pid=child_saved_pid
            progress_child_pid=progress.get('child_pid')
            if progress_child_pid is not None and not _valid_optional_gate_identity_pair(progress_child_pid,child_identity):
                records_read=False
                progress_child_pid=None
            if child_pid is not None and progress_child_pid is not None and child_pid!=progress_child_pid:
                records_read=False
            child_pid=child_pid if child_pid is not None else progress_child_pid
            if not _valid_optional_gate_identity_pair(child_pid,child_identity): records_read=False
            owned=((child_pid,child_identity,False),(worker_pid,worker_identity,True))
            for pid,identity,required in owned:
                if pid is None and identity is None:
                    if required: records_read=False
                    continue
                if type(pid) is not int or not _valid_gate_process_identity(identity) or identity.get('pid')!=pid:
                    records_read=False
                    continue
                try: identity_state=gate_execution.identity_state(identity)
                except Exception:
                    records_read=False
                    continue
                if identity_state not in ('alive','dead'):
                    records_read=False
                    continue
                if identity_state!='alive': continue
                try:
                    if sys.platform=='win32': subprocess.run(['taskkill','/PID',str(pid),'/T','/F'],capture_output=True,timeout=5)
                    else:
                        import os,signal
                        os.kill(pid,signal.SIGKILL)
                except (OSError,subprocess.SubprocessError): pass
        for path in (self.root/'gate-execution').glob('*.json') if (self.root/'gate-execution').is_dir() else ():
            try: job=gate_execution.read(self.root,path.stem)
            except Exception:
                records_read=False
                continue
            if not isinstance(job,dict):
                records_read=False
                continue
            state=_known_gate_job_state(job)
            child_record=job.get('owned_child')
            if child_record is not None and not isinstance(child_record,dict):
                records_read=False
                child=None
            else:
                child=child_record or {}
            child_pid=child.get('pid') if child is not None else None
            child_identity=child.get('identity') if child is not None else None
            worker_pid=job.get('worker_pid'); identity=job.get('worker_identity')
            if not _valid_optional_gate_identity_pair(worker_pid,identity): records_read=False
            if not _valid_optional_gate_identity_pair(child_pid,child_identity): records_read=False
            if state is None:
                records_read=False
                continue
            if state in ('launching','running') and identity is None:
                records_read=False
            deadline=time.monotonic()+3
            for owned_identity in (identity,child_identity):
                if owned_identity is None: continue
                if not _valid_gate_process_identity(owned_identity):
                    records_read=False
                    continue
                try: identity_state=gate_execution.identity_state(owned_identity)
                except Exception:
                    records_read=False
                    continue
                if identity_state not in ('alive','dead'):
                    records_read=False
                    continue
                while identity_state=='alive' and time.monotonic()<deadline:
                    time.sleep(.05)
                    try: identity_state=gate_execution.identity_state(owned_identity)
                    except Exception:
                        records_read=False
                        break
                    if identity_state not in ('alive','dead'):
                        records_read=False
                        break
                if identity_state=='alive':
                    records_read=False
        self._run_guard.stop()
        self._popen_guard.stop()
        if not _current_test_failed_or_unverified(self) and records_read:
            self.fixture.tearDown()
        else:
            self._retain_fixture_artifacts()
            print('PRESERVED E2E FAILURE ARTIFACTS: '+str(self.fixture.root))

    def test_retention_detaches_only_fixture_temporary_directory_finalizer(self):
        root=self.fixture.root
        finalizer=self.fixture.tmp._finalizer
        self._retain_fixture_artifacts()
        self.assertFalse(finalizer.alive)
        self.assertTrue(root.exists())

    def test_read_retries_transient_windows_sharing_error_without_reporting_absence(self):
        record={'candidate_id':'e2e-read-retry','state':'admitted','worker_pid':None}
        gate_execution._write(gate_execution._path(self.root,'e2e-read-retry'),record)
        real_read_text=Path.read_text
        attempts=[]
        def transient(path, *args, **kwargs):
            if path.name=='e2e-read-retry.json' and not attempts:
                attempts.append(True)
                raise PermissionError('injected sharing violation')
            return real_read_text(path,*args,**kwargs)
        with patch.object(Path,'read_text',transient):
            job=gate_execution.read(self.root,'e2e-read-retry')
        self.assertEqual(len(attempts),1)
        self.assertIsNotNone(job)
        self.assertEqual(job['state'],'admitted')

    def admit(self, ident, script):
        plan = {'title':'OS process fixture','kind':'bugfix','acceptance_criteria':['fixture marker exists'],
                'fixture':'seed.txt','manual_steps':['Inspect fixture'],'checks':[
                    {'argv':[sys.executable,'-c',script],'rationale':'finite Python OS process','covers':['fixture marker exists']}]}
        planfile = self.fixture.root / (ident + '.json')
        planfile.write_text(json.dumps(plan))
        self.fixture.cli('submit',ident,'--agent','author','--base-ref',self.fixture.base,
                         '--build-identity','Python process fixture','--steps','fixture only',
                         '--unverified','No Minecraft execution','--gate-plan',planfile)
        candidate_path = self.root / 'candidates' / (ident + '.json')
        candidate = json.loads(candidate_path.read_text())
        candidate['gate_request'] = {'prompt_admitted':True,'snapshot_key':candidate['snapshot_key'],
                                     'session_id':'fixture-lead','origin_session_id':'fixture-author'}
        wf.writejson(candidate_path,candidate)
        return candidate

    def produce_real_receipt(self, ident, script):
        self.admit(ident,script)
        gate_execution.request(self.root,ident,workflow=wf)
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            job=gate_execution.read(self.root,ident)
            if job and job['state'] in ('completed','blocked','interrupted'): break
            gate_execution.reconcile(self.root,workflow=wf); time.sleep(.05)
        self.assertEqual(job['state'],'completed',job)
        receipt=Path(job['receipt']['path'])
        self.assertEqual(wf.sha(receipt),job['receipt']['sha256'])
        return job,receipt

    def wait_completed(self, ident, timeout=20):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            job=gate_execution.read(self.root,ident)
            if job and job['state'] in ('completed','blocked','interrupted'):
                identity=job.get('worker_identity'); deadline=time.monotonic()+3
                while identity and time.monotonic()<deadline and gate_execution.identity_state(identity)=='alive': time.sleep(.05)
                return job
            # This is the same independent observer hook that owns launches.
            gate_execution.reconcile(self.root,workflow=wf)
            time.sleep(.05)
        self.fail('execution did not reach terminal state; fixture root: '+str(self.fixture.root))

    def test_admission_survives_caller_exit_and_real_worker_runs_whole_batch(self):
        markers=[self.fixture.root/'marker-one',self.fixture.root/'marker-two']
        # Deliberately expensive source preflight happens in the real worker, not admission.
        (self.fixture.repo/'seed.txt').write_text('seed\n' * 300000)
        self.fixture.git('add','seed.txt'); self.fixture.git('commit','-qm','large preflight fixture')
        self.fixture.base=self.fixture.git('rev-parse','HEAD').decode().strip()
        body='import pathlib,time; time.sleep(4); pathlib.Path('+repr(str(markers[0]))+').write_text("one"); pathlib.Path('+repr(str(markers[1]))+').write_text("two")'
        self.admit('e2e-long',body)
        caller_marker=self.fixture.root/'caller-admitted'
        cli=Path(__file__).with_name('workflow_runtime.py').resolve()
        caller_code=('import pathlib,subprocess,sys,time; cmd=[sys.executable,'+repr(str(cli))+',"--asset-workspace",'
                     +repr(str(self.fixture.root))+',"--repo",'+repr(str(self.fixture.repo))+',"gate-validate","e2e-long","--actor","integration-lead"]; '
                     'subprocess.run(cmd,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); pathlib.Path('+repr(str(caller_marker))+').write_text("admitted"); time.sleep(30)')
        caller=subprocess.Popen([sys.executable,'-c',caller_code],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        deadline=time.monotonic()+10
        while time.monotonic()<deadline and not caller_marker.exists(): time.sleep(.025)
        if not caller_marker.exists():
            stdout,stderr=caller.communicate(timeout=2)
            self.fail('public CLI caller did not reach admission: '+stdout+'\n'+stderr+'; fixture root: '+str(self.fixture.root))
        self.assertEqual(gate_execution.read(self.root,'e2e-long')['state'],'requested')
        # Model the interrupted outer tool call: terminate only this fixture-owned wrapper.
        caller.kill(); caller.wait(timeout=5); caller.communicate()
        self.assertIsNotNone(caller.returncode)
        # A separate observer, with no caller ancestry, starts the durable claim.
        observer=subprocess.Popen([sys.executable,'-c',
            'import sys; from pathlib import Path; sys.path.insert(0,sys.argv[1]); import gate_execution,workflow_runtime; gate_execution.reconcile(sys.argv[2],workflow=workflow_runtime)',
            str(Path(__file__).parent),str(self.root)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        self.assertEqual(observer.wait(timeout=4),0)
        done=self.wait_completed('e2e-long')
        self.assertEqual(done['state'],'completed',done)
        self.assertTrue(all(p.is_file() for p in markers))
        self.assertEqual(done['origin_session_id'],'fixture-author')
        self.assertEqual(done['receipt']['sha256'],wf.sha(Path(done['receipt']['path'])))

    def test_concurrent_observers_launch_one_real_check(self):
        marker=self.fixture.root/'once'
        script='import pathlib,time; time.sleep(.5); pathlib.Path('+repr(str(marker))+').open("a").write("x")'
        self.admit('e2e-once',script)
        gate_execution.request(self.root,'e2e-once',workflow=wf)
        code='import sys; from pathlib import Path; sys.path.insert(0,sys.argv[1]); import gate_execution,workflow_runtime; gate_execution.reconcile(sys.argv[2],workflow=workflow_runtime)'
        observers=[subprocess.Popen([sys.executable,'-c',code,str(Path(__file__).parent),str(self.root)],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True) for _ in range(2)]
        for p in observers:
            status=p.wait(timeout=5)
            stdout,stderr=p.communicate()
            self.assertEqual(status,0,stdout+'\n'+stderr)
        self.assertEqual(self.wait_completed('e2e-once')['state'],'completed')
        self.assertEqual(marker.read_text(),'x')

    def test_concurrent_admission_and_observer_restart_do_not_repeat_worker(self):
        marker=self.fixture.root/'restart-once'
        script='import pathlib,time; time.sleep(1); pathlib.Path('+repr(str(marker))+').open("a").write("x")'
        self.admit('e2e-restart',script)
        import concurrent.futures
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            admissions=list(pool.map(lambda _:gate_execution.request(self.root,'e2e-restart',workflow=wf),range(8)))
        self.assertEqual(len({x['claim'] for x in admissions}),1)
        gate_execution.reconcile(self.root,workflow=wf)
        before=gate_execution.read(self.root,'e2e-restart')
        # Restarting an observer while the persisted worker identity is live is a no-op.
        gate_execution.reconcile(self.root,workflow=wf)
        self.assertEqual(gate_execution.read(self.root,'e2e-restart')['attempt'],before['attempt'])
        self.assertEqual(self.wait_completed('e2e-restart')['state'],'completed')
        self.assertEqual(marker.read_text(),'x')

    def test_shared_execution_mutex_serializes_distinct_real_jobs(self):
        markers=[self.fixture.root/'serial-a',self.fixture.root/'serial-b']
        identifiers=('e2e-serial-a','e2e-serial-b')
        setup_error=None
        try:
            for ident,marker in zip(identifiers,markers):
                self.admit(ident,'import pathlib,time; time.sleep(.6); pathlib.Path('+repr(str(marker))+').write_text("done")')
                gate_execution.request(self.root,ident,workflow=wf)
            gate_execution.reconcile(self.root,workflow=wf)
        except Exception as exc:
            setup_error=f'{type(exc).__name__}: {exc}'
        def retain_serial_failure(reason,jobs,read_errors=None):
            retained=(Path(__file__).resolve().parents[2]/'build/agent-workflow/workflow-redesign-plan/wr12/attempts'/
                f'serial-mutex-diagnostic-{uuid.uuid4()}')
            retained.mkdir(parents=True,exist_ok=False)
            captured=[]; copied=[]; copy_errors=[]
            for ident,job in zip(identifiers,jobs):
                entry={'case_id':ident,'state':job.get('state','UNKNOWN') if isinstance(job,dict) else 'UNKNOWN',
                    'read_result':job,'read_error':(read_errors or {}).get(ident)}
                captured.append(entry)
                sources=[(f'{ident}.job.json',self.root/'gate-execution'/(ident+'.json')),
                    (f'{ident}.progress.json',self.root/'progress'/(ident+'.json'))]
                progress=None
                try:
                    raw_progress=wf.readjson(self.root/'progress'/(ident+'.json'))
                    if isinstance(raw_progress,dict): progress=raw_progress
                except Exception as exc:
                    entry['progress_read_error']=f'{type(exc).__name__}: {exc}'
                for key,label in (('log','worker.log'),('receipt','receipt.json')):
                    value=(progress.get(key,progress.get('log_path')) if key=='log' and progress else
                        (job.get('log_path') if key=='log' and isinstance(job,dict) else
                         job.get(key) if isinstance(job,dict) else None))
                    if isinstance(value,dict): value=value.get('path')
                    if isinstance(value,str): sources.append((f'{ident}.{label}',Path(value)))
                for name,source in sources:
                    try:
                        resolved=source.resolve(strict=True)
                        resolved.relative_to(self.fixture.root.resolve())
                        if resolved.is_file():
                            shutil.copy2(resolved,retained/name); copied.append(str(resolved))
                    except FileNotFoundError:
                        continue
                    except Exception as exc:
                        copy_errors.append({'path':str(source),'error':f'{type(exc).__name__}: {exc}'})
            summary={'case_id':self.id(),'source_sha256':wf.sha(Path(__file__)),
                'fixture_path':str(self.fixture.root),'reason':reason,'jobs':captured,
                'copied_owned_files':copied,'copy_errors':copy_errors}
            (retained/'summary.json').write_text(json.dumps(summary,default=str,indent=2),encoding='utf-8')
            print('SERIAL MUTEX DIAGNOSTICS RETAINED: '+str(retained))
            return retained

        deadline=time.monotonic()+20
        jobs=[]; read_errors={}; failure=None
        if setup_error is not None:
            failure='serial setup/reconcile failed: '+setup_error
        while time.monotonic()<deadline:
            if failure is not None: break
            jobs=[]; read_errors={}
            for ident in identifiers:
                try:
                    job=gate_execution.read(self.root,ident)
                    if not isinstance(job,dict):
                        read_errors[ident]='job record missing or unreadable'
                    jobs.append(job)
                except Exception as exc:
                    jobs.append(None); read_errors[ident]=f'{type(exc).__name__}: {exc}'
            if read_errors:
                failure='serial job records missing/unreadable: '+repr(read_errors); break
            active=[j for j in jobs if j['state'] in ('launching','running')]
            if len(active)>1:
                failure='shared mutex overlap: '+repr([j.get('candidate_id',j.get('request_id')) for j in active]); break
            blocked=[j for j in jobs if j.get('state') in ('blocked','interrupted')]
            if blocked:
                failure='serial job reached blocked/interrupted: '+repr([(j.get('candidate_id'),j.get('state'),j.get('reason')) for j in blocked]); break
            unknown=[j for j in jobs if _known_gate_job_state(j) is None]
            if unknown:
                failure='serial job has unknown state: '+repr(unknown); break
            if all(j['state']=='completed' for j in jobs): break
            try:
                gate_execution.reconcile(self.root,workflow=wf)
            except Exception as exc:
                failure=f'{type(exc).__name__}: {exc}'; break
            time.sleep(.05)
        if failure is None and not (jobs and all(isinstance(j,dict) and j.get('state')=='completed' for j in jobs)):
            failure='serial jobs did not both complete before the original 20-second deadline'
        if failure is None and not all(marker.is_file() for marker in markers):
            failure='both job records completed but one or more completion markers are missing'
        if failure is not None:
            if len(jobs)!=len(identifiers):
                jobs=[]; read_errors={}
                for ident in identifiers:
                    try:
                        job=gate_execution.read(self.root,ident)
                        if not isinstance(job,dict): read_errors[ident]='job record missing or unreadable'
                        jobs.append(job)
                    except Exception as exc:
                        jobs.append(None); read_errors[ident]=f'{type(exc).__name__}: {exc}'
            retained=retain_serial_failure(failure,jobs,read_errors)
            self.fail(f'{failure}; serial diagnostics retained at {retained}; fixture root: {self.fixture.root}')
        self.assertTrue(all(x.is_file() for x in markers))

    def test_killed_owned_worker_is_interrupted_and_never_automatically_rerun(self):
        started=self.fixture.root/'started'; grandpid=self.fixture.root/'grandpid'; late=self.fixture.root/'late-marker'
        grandchild_code='import pathlib,time; time.sleep(3); pathlib.Path('+repr(str(late))+').write_text("late")'
        script=('import pathlib,subprocess,sys,time; p=subprocess.Popen([sys.executable,"-c",'+repr(grandchild_code)+']); '
                'pathlib.Path('+repr(str(grandpid))+').write_text(str(p.pid)); pathlib.Path('+repr(str(started))+').write_text("started"); time.sleep(25)')
        self.admit('e2e-killed',script); gate_execution.request(self.root,'e2e-killed',workflow=wf)
        gate_execution.reconcile(self.root,workflow=wf)
        deadline=time.monotonic()+10
        job=gate_execution.read(self.root,'e2e-killed')
        while time.monotonic()<deadline and (not started.exists() or not grandpid.exists() or not job.get('owned_child')):
            time.sleep(.05); job=gate_execution.read(self.root,'e2e-killed')
        self.assertTrue(started.exists(),'worker check never started; '+str(self.fixture.root))
        worker_pid=job['worker_pid']; child=job['owned_child']; child_pid=child['pid']; grandchild_pid=int(grandpid.read_text())
        # Kill only the fixture worker root. Leave check + grandchild for observer-owned cleanup.
        if sys.platform=='win32': subprocess.run(['taskkill','/PID',str(worker_pid),'/F'],capture_output=True,timeout=5)
        else:
            import os,signal
            os.kill(worker_pid,signal.SIGKILL)
        deadline=time.monotonic()+5
        while time.monotonic()<deadline and gate_execution.identity_state(job['worker_identity'])=='alive': time.sleep(.05)
        gate_execution.reconcile(self.root,workflow=wf)
        interrupted=gate_execution.read(self.root,'e2e-killed')
        self.assertEqual(interrupted['state'],'interrupted',interrupted)
        self.assertEqual(interrupted['cleanup']['status'],'terminated_and_absent',interrupted)
        self.assertEqual(gate_execution.process_presence(child_pid),'absent')
        self.assertEqual(gate_execution.process_presence(grandchild_pid),'absent')
        progress=wf.readjson(self.root/'progress'/'e2e-killed.json')
        self.assertEqual(progress['phase'],'interrupted',progress)
        self.assertFalse(late.exists(),'orphan grandchild wrote its delayed marker')
        attempt=interrupted['attempt']; gate_execution.reconcile(self.root,workflow=wf)
        self.assertEqual(gate_execution.read(self.root,'e2e-killed')['state'],'interrupted')
        self.assertEqual(gate_execution.read(self.root,'e2e-killed')['attempt'],attempt)
        self.assertEqual(gate_execution.identity_state(interrupted.get('worker_identity')),'dead')
        # A fresh job is permitted only after observer verified the prior owned tree clear.
        self.admit('e2e-after-cleanup','import pathlib; pathlib.Path('+repr(str(self.fixture.root/'fresh'))+').write_text("ok")')
        gate_execution.request(self.root,'e2e-after-cleanup',workflow=wf)
        done=self.wait_completed('e2e-after-cleanup')
        self.assertEqual(done['state'],'completed',done)

    def test_published_flat_workflow_layout_worker_imports_and_completes_receipt(self):
        """Exercise the publisher's SourceMap target, not source-tree imports."""
        candidate=self.admit('e2e-published','import pathlib; pathlib.Path('+repr(str(self.fixture.root/'published-marker'))+').write_text("flat")')
        package=Path(__file__).parent
        published=self.fixture.root/'published'/'tooling'/'agent_workflow'
        published.mkdir(parents=True)
        # Minimal actual SharedSources subset required by gate_execution's worker loader/runtime.
        for name in ('gate_execution.py','owner_attention.py','validation_evidence.py','gate_progress.py'):
            (published/name).write_bytes((package/name).read_bytes())
        (published/'workflow.py').write_bytes((package/'workflow_runtime.py').read_bytes())
        self.assertTrue((published/'workflow.py').is_file())
        self.assertFalse((published/'workflow_runtime.py').exists())
        gate_execution.request(self.root,'e2e-published',workflow=wf)
        job=gate_execution.read(self.root,'e2e-published')
        job.update(state='launching',attempt=1); gate_execution._write(gate_execution._path(self.root,'e2e-published'),job)
        proc=subprocess.Popen([sys.executable,str(published/'gate_execution.py'),'--worker',str(self.root),
                               'e2e-published',job['claim'],'1'],cwd=str(self.fixture.root),
                              stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
        try:
            stdout,stderr=proc.communicate(timeout=30)
            self.assertEqual(proc.returncode,0,stdout+'\n'+stderr)
        finally:
            if proc.poll() is None: proc.kill(); proc.wait(timeout=5)
        completed=gate_execution.read(self.root,'e2e-published')
        self.assertEqual(completed['state'],'completed',completed)
        receipt=Path(completed['receipt']['path'])
        self.assertEqual(wf.sha(receipt),completed['receipt']['sha256'])
        self.assertTrue((self.fixture.root/'published-marker').is_file())

    def test_real_readiness_receipt_posts_once_to_same_lead_ack_is_not_consumption(self):
        _,receipt=self.produce_real_receipt('e2e-review-route','import sys; print("fixture pass")')
        from tooling.agent_workflow import gate_liveness
        candidate=wf.readjson(self.root/'candidates'/'e2e-review-route.json')
        central_worktree=str((self.fixture.root/'neutral-05').resolve())
        candidate['gate_request']['central_worktree']=central_worktree
        wf.writejson(self.root/'candidates'/'e2e-review-route.json',candidate)
        gate_liveness.register(self.root,candidate,lead_session_id='fixture-lead',workflow=wf)
        posted=[]; active={'value':{}}; active_probes=[]
        def request(server,method,path,body=None,timeout=15):
            if method=='GET' and path.startswith('/api/mcp?'):
                return 200,{'location':{'directory':central_worktree},
                    'data':[{'name':'ms14_workflow','status':{'status':'connected'}}]}
            if path=='/api/session/active': active_probes.append(dict(active['value'])); return 200,{'data':active['value']}
            if method=='POST' and path=='/api/session/fixture-lead/prompt':
                posted.append(body)
                return 200,{'data':{'id':body['id'],'sessionID':'fixture-lead','payload':{'text':body['text'],'metadata':body['metadata']}}}
            raise AssertionError((method,path,body))
        first=gate_liveness.reconcile(self.root,workflow=wf,request=request)
        self.assertEqual(first['pending'],1,first)
        health=gate_liveness.read_health(self.root,'e2e-review-route')
        self.assertTrue(health['execution_review_delivery']['acknowledged'])
        self.assertIsNone(health['execution_review_delivery'].get('execution_observed_at'))
        # Repeated observation retains the exact admitted item/body, no duplicate POST/work.
        gate_liveness.reconcile(self.root,workflow=wf,request=request)
        self.assertEqual(len(posted),1)
        self.assertEqual(health['execution_review_delivery']['body'],posted[0])
        active['value']={'fixture-lead':{'type':'running'}}
        gate_liveness.reconcile(self.root,workflow=wf,request=request)
        observed=gate_liveness.read_health(self.root,'e2e-review-route')['execution_review_delivery']
        self.assertEqual(observed['state'],'execution_observed')
        self.assertIn('active',observed['evidence'])
        self.assertIn('not implied',observed['evidence'])
        self.assertEqual(gate_liveness.read_health(self.root,'e2e-review-route')['session_id'],'fixture-lead')
        self.assertIn({'fixture-lead':{'type':'running'}},active_probes)
        self.assertEqual(len(posted),1)
        self.assertTrue(receipt.is_file())

    def test_diagnostic_failed_receipt_completion_routes_only_to_exact_author(self):
        # Real worker emits a typed failed diagnostic receipt; only transport is stubbed.
        plan={'title':'Diagnostic transport fixture','kind':'diagnostic','acceptance_criteria':['fixture failure recorded'],
              'fixture':'seed.txt','manual_steps':[],'checks':[{'argv':[sys.executable,'-c','raise SystemExit(3)'],
              'rationale':'intentional diagnostic failure','covers':['fixture failure recorded']}]}
        planfile=self.fixture.root/'e2e-diagnostic.json'; planfile.write_text(json.dumps(plan))
        self.fixture.cli('submit','e2e-diagnostic','--agent','author','--base-ref',self.fixture.base,
                         '--build-identity','Python diagnostic fixture','--steps','diagnostic only; no owner/manual readiness',
                         '--unverified','No runtime execution','--diagnostic-only','--gate-plan',planfile)
        cp=self.root/'candidates'/'e2e-diagnostic.json'; candidate=wf.readjson(cp)
        candidate['gate_request']={'prompt_admitted':True,'snapshot_key':candidate['snapshot_key'],
                                   'session_id':'fixture-lead','origin_session_id':'fixture-author'}
        wf.writejson(cp,candidate)
        gate_execution.request(self.root,'e2e-diagnostic',workflow=wf)
        job=self.wait_completed('e2e-diagnostic')
        receipt=Path(job['receipt']['path']); self.assertEqual(job['state'],'completed')
        ref={'path':str(receipt),'sha256':wf.sha(receipt)}
        candidate=wf.readjson(cp); candidate['gate_validation']=ref
        candidate['gate_outcome']={'result':'diagnostic_complete','purpose':'diagnostic','validation':str(receipt),
                                   'validation_sha256':ref['sha256']}
        wf.writejson(cp,candidate)
        from tooling.agent_workflow import gate_liveness,gate_dispatch
        gate_liveness.register(self.root,candidate,lead_session_id='fixture-lead',workflow=wf)
        requests=[]
        def transport(server,method,path,body=None,timeout=15):
            requests.append((method,path,body))
            if method=='GET' and path=='/api/session/fixture-author':
                return 200,{'data':{'id':'fixture-author','agent':'author','directory':candidate['worktree']['worktree']}}
            if method=='POST' and path=='/api/session/fixture-author/prompt':
                return 200,{'data':{'id':body['id'],'sessionID':'fixture-author','type':'user','delivery':'steer',
                                    'payload':{'text':body['text'],'metadata':body['metadata']}}}
            if path=='/api/session/active': return 200,{'data':{}}
            raise AssertionError((method,path,body))
        with patch.dict(sys.modules, {'gate_dispatch': gate_dispatch}), \
             patch.object(gate_dispatch,'_server',return_value=None),patch.object(gate_dispatch,'_check_v2'),patch.object(gate_dispatch,'_request',side_effect=transport):
            result=gate_liveness.reconcile(self.root,workflow=wf,request=transport)
            self.assertEqual(result['errors'],[],result)
            self.assertEqual(result['active']+result['pending']+result['blocked'],0,result)
        sent=[row for row in requests if row[0]=='POST']
        self.assertEqual(len(sent),1)
        self.assertEqual(sent[0][1],'/api/session/fixture-author/prompt')
        body=sent[0][2]
        self.assertEqual(body['metadata']['candidate_id'],'e2e-diagnostic')
        self.assertEqual(body['metadata']['result'],'diagnostic_complete')
        saved=wf.readjson(cp)['author_completion']
        self.assertEqual(saved['status'],'delivered'); self.assertEqual(saved['id'],body['id'])


if __name__ == '__main__': unittest.main()
