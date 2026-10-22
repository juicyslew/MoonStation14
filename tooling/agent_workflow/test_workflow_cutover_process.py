import hashlib
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import uuid
import unittest
from pathlib import Path
from unittest.mock import patch

try:
    import workflow_cutover_process as process
    import workflow_cutover, admission, gate_execution
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_cutover_process as process
    from tooling.agent_workflow import workflow_cutover, admission, gate_execution


class Barrier:
    def __init__(self, statuses, jobs=None, root=None):
        self.statuses = iter(statuses); self.calls = 0; self.jobs=jobs or []; self.root=root
        self._owner_thread=threading.get_ident(); self._locks=[object()]; self._hold=(object(),)
    def recheck(self): self.calls += 1; return {'status': next(self.statuses),'jobs':self.jobs}


class FakeKernel:
    def __init__(self, *, wait=True, close=True, exit_code=0):
        self.wait_ok, self.close_ok, self.code = wait, close, exit_code
        self.opened = []; self.terminated = []; self.waited = []; self.closed = []
    def open_process(self, pid): self.opened.append(pid); return 44
    def terminate(self, handle): self.terminated.append(handle); return True
    def wait(self, handle, timeout): self.waited.append((handle, timeout)); return self.wait_ok
    def exit_code(self, handle): return self.code
    def close(self, handle): self.closed.append(handle); return self.close_ok


def _fixture_interpreter_info():
    path=Path(sys._base_executable).resolve()
    if not path.is_file(): raise AssertionError('sys._base_executable must be an actual interpreter file')
    return {'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}


def _write_fixture_evidence(prefix, report, fixture_root):
    prefix=Path(prefix)
    payload=dict(report,fixture_root=str(fixture_root),fixture_root_preserved=Path(fixture_root).is_dir())
    prefix.with_suffix('.json').write_text(json.dumps(payload,indent=2,sort_keys=True)+'\n',encoding='utf8')
    prefix.with_suffix('.log').write_text(
        f"status={payload.get('status')} children_reaped={payload.get('children_reaped')} "
        f"root_preserved={payload['fixture_root_preserved']}\n",encoding='utf8')
    return payload


class WorkflowCutoverProcessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.shared = self.base / 'shared'; self.shared.mkdir()
        self.exchange = self.base / 'exchange'; self.exchange.mkdir()
        self.localappdata=self.base/'local'; self.approved=self.localappdata/'Temp'/'opencode'; self.approved.mkdir(parents=True)
        self.source = self.shared / process.OBSERVER_RELATIVE
        self.source.parent.mkdir(parents=True)
        self.source.write_text('# fixture observer source\n', encoding='utf8')
        self.receipt = self.approved / 'publication.json'
        self.source_hash = hashlib.sha256(self.source.read_bytes()).hexdigest()
        self.receipt_data = {'shared_root':str(self.shared), 'files':[{'target':'shared',
            'path':process.OBSERVER_RELATIVE.as_posix(),'new_sha256':self.source_hash,'deleted':False}]}
        self.receipt.write_text(json.dumps(self.receipt_data), encoding='utf8')
        self.receipt_hash = hashlib.sha256(self.receipt.read_bytes()).hexdigest()
        self.cmd = f'python.exe "{self.source}" --exchange "{self.exchange}" --watch'
        self.plan = {'exchange_root':str(self.exchange),'shared_root':str(self.shared),
            'publication_receipt':str(self.receipt),'expected_publication_receipt_sha256':self.receipt_hash,
            'pid':123,'created':456,'executable_path':'python.exe','command_line':self.cmd,
            'source_sha256':self.source_hash,'observed_at_ms':1000,
            'inventory':None}
        self.plan['inventory'] = self.inventory()
        self.api = FakeKernel()
        self.cim = {'ProcessId':123,'ExecutablePath':'python.exe','CommandLine':self.cmd}

    def tearDown(self): self.tmp.cleanup()

    def test_real_fixture_interpreter_is_verified_base_executable(self):
        info=_fixture_interpreter_info()
        self.assertTrue(Path(info['path']).is_file())
        self.assertEqual(info['path'],str(Path(sys._base_executable).resolve()))
        self.assertEqual(info['sha256'],hashlib.sha256(Path(info['path']).read_bytes()).hexdigest())

    def test_fixture_failure_evidence_preserves_root_and_failure_record(self):
        fixture=self.base/'preserved-fixture'; fixture.mkdir()
        prefix=self.base/'observer-stop-real-test'
        payload=_write_fixture_evidence(prefix,{'status':'FAIL','failure':'intentional pure fixture'},fixture)
        self.assertEqual(json.loads(prefix.with_suffix('.json').read_text())['failure'],'intentional pure fixture')
        self.assertTrue(payload['fixture_root_preserved']); self.assertTrue(fixture.is_dir())

    def inventory(self, **changes):
        value={'version':1,'exchange_root':str(self.exchange),'observer_identity':{'pid':123,'created':456},
               'captured_at_ms':1000,'known_owned_states':[],'eligible_active':[],'unknown':[],
               'checked_scope':'fixture-owned-process-inventory','scope_owner':'fixture', 'source_digest':''}
        value.update(changes)
        value['source_digest']=''
        value['source_digest']=hashlib.sha256(json.dumps({k:v for k,v in value.items() if k!='source_digest'},
            sort_keys=True,separators=(',',':')).encode()).hexdigest()
        return value

    def run_stop(self, barrier=None, *, identity=None, cim=None, api=None, plan=None, **kwargs):
        with patch.dict(os.environ, {'LOCALAPPDATA':str(self.localappdata)}), \
             patch.object(process.os, 'name', 'nt'), \
             patch.object(process.gate_execution, 'process_identity', return_value=identity or {'pid':123,'created':456}):
            barrier=barrier or Barrier(['OBSERVED_ZERO','OBSERVED_ZERO'],root=self.exchange)
            barrier.root=self.exchange
            return process.stop_observer(self.plan if plan is None else plan, barrier=barrier,
                query_cim=lambda pid: cim or self.cim, win_api=api or self.api,
                argv_parser=lambda cmd: ['python.exe',str(self.source),'--exchange',str(self.exchange),'--watch'],
                clock_ms=lambda:1001, **kwargs)

    def test_valid_receipt_stops_only_same_open_handle(self):
        result = self.run_stop()
        self.assertEqual(result['status'], 'STOPPED')
        self.assertEqual((self.api.opened,self.api.terminated,self.api.waited,self.api.closed),
                         ([123],[44],[(44,process.WAIT_TIMEOUT_MS)],[44]))

    def test_reused_pid_creation_identity_never_terminated(self):
        result = self.run_stop(identity={'pid':123,'created':999})
        self.assertEqual(result['status'],'ERROR'); self.assertEqual(self.api.terminated,[])
        self.assertEqual(self.api.closed,[44])

    def test_wrong_command_exchange_script_or_watch_never_terminated(self):
        variants = [self.cmd.replace(str(self.exchange),str(self.shared)),
                    self.cmd.replace(str(self.source),'other.py'), self.cmd.replace('--watch','--once')]
        for command in variants:
            with self.subTest(command=command):
                self.api=FakeKernel(); cim=dict(self.cim,CommandLine=command)
                self.assertEqual(self.run_stop(cim=cim)['status'],'ERROR')
                self.assertEqual(self.api.terminated,[])

    def test_stale_receipt_or_source_refuses_before_open(self):
        for mutate in ('receipt','source'):
            self.api=FakeKernel()
            if mutate=='receipt': self.plan['expected_publication_receipt_sha256']='0'*64
            else: self.plan['source_sha256']='0'*64
            self.assertEqual(self.run_stop()['status'],'ERROR')
            self.assertEqual(self.api.opened,[])

    def test_missing_or_unknown_inventory_and_stale_plan_refuse(self):
        cases = [({},{}), ({**self.plan,'inventory':self.inventory(unknown=['unresolved'])},{}),
                 ({**self.plan,'inventory':self.inventory(captured_at_ms=900)}, {'max_plan_age_ms':50})]
        for supplied, options in cases:
            self.api=FakeKernel()
            self.assertEqual(self.run_stop(plan=supplied, **options)['status'],'ERROR')
            self.assertEqual(self.api.opened,[])

    def test_barrier_change_pretermination_closes_handle_without_kill(self):
        self.api=FakeKernel(); barrier=Barrier(['OBSERVED_ZERO','HELD'])
        result=self.run_stop(barrier)
        self.assertEqual(result['status'],'HELD'); self.assertEqual(self.api.terminated,[])
        self.assertEqual(self.api.closed,[44])

    def test_wait_timeout_active_exit_and_close_failure_are_errors(self):
        for api in (FakeKernel(wait=False),FakeKernel(exit_code=259),FakeKernel(close=False)):
            with self.subTest(api=api):
                self.api=api
                self.assertEqual(self.run_stop()['status'],'ERROR')
        self.assertEqual(api.closed,[44])

    def test_late_held_close_failure_preserves_both_facts(self):
        self.api=FakeKernel(close=False)
        result=self.run_stop(Barrier(['OBSERVED_ZERO','HELD']))
        self.assertEqual(result['status'],'ERROR')
        self.assertEqual(result['primary_status'],'HELD')
        self.assertEqual(result['cleanup'],'CloseHandle failed')

    def test_wrong_publication_owner_shape_refuses(self):
        self.receipt_data['files'][0]['target']='main'
        self.receipt.write_text(json.dumps(self.receipt_data),encoding='utf8')
        self.plan['expected_publication_receipt_sha256']=hashlib.sha256(self.receipt.read_bytes()).hexdigest()
        self.api=FakeKernel()
        self.assertEqual(self.run_stop()['status'],'ERROR'); self.assertEqual(self.api.opened,[])

    def test_deleted_missing_duplicate_rows_refuse_before_open(self):
        variants=[]
        for row in (dict(self.receipt_data['files'][0],deleted=True),
                    {k:v for k,v in self.receipt_data['files'][0].items() if k!='deleted'}):
            variants.append([row])
        variants.append([self.receipt_data['files'][0],self.receipt_data['files'][0]])
        for rows in variants:
            self.receipt_data['files']=rows
            self.receipt.write_text(json.dumps(self.receipt_data),encoding='utf8')
            self.plan['expected_publication_receipt_sha256']=hashlib.sha256(self.receipt.read_bytes()).hexdigest()
            self.api=FakeKernel()
            self.assertEqual(self.run_stop()['status'],'ERROR')
            self.assertEqual(self.api.opened,[])

    def test_receipt_inside_shared_or_outside_approved_temp_refuses_before_open(self):
        original=self.receipt
        for destination in (self.shared/'publication.json', self.base/'publication.json'):
            destination.write_text(json.dumps(self.receipt_data),encoding='utf8')
            self.plan['publication_receipt']=str(destination)
            self.plan['expected_publication_receipt_sha256']=hashlib.sha256(destination.read_bytes()).hexdigest()
            self.api=FakeKernel()
            self.assertEqual(self.run_stop()['status'],'ERROR')
            self.assertEqual(self.api.opened,[])
        self.plan['publication_receipt']=str(original)
        self.plan['expected_publication_receipt_sha256']=hashlib.sha256(original.read_bytes()).hexdigest()

    def test_exchange_root_and_numeric_bounds_are_bound_before_open(self):
        self.api=FakeKernel()
        other=self.base/'other-exchange'; other.mkdir()
        result=self.run_stop(plan={**self.plan,'exchange_root':str(other)})
        self.assertEqual(result['status'],'ERROR'); self.assertEqual(self.api.opened,[])
        for options in ({'timeout_ms':0},{'timeout_ms':True},{'timeout_ms':float('inf')},
                        {'max_plan_age_ms':-1},{'max_plan_age_ms':float('inf')}):
            self.api=FakeKernel()
            self.assertEqual(self.run_stop(**options)['status'],'ERROR')
            self.assertEqual(self.api.opened,[])

    def test_historical_no_child_disclosure_is_not_claimed_as_dead(self):
        jobs=[{'job_id':'legacy','sha256':'d'*64,'limitation':'historical_no_child_recorded_not_cleanup_proof'}]
        row={'job_id':'legacy','sha256':'d'*64,'state':'interrupted','identity_state':'historical_unrecorded'}
        plan={**self.plan,'inventory':self.inventory(known_owned_states=[row])}
        self.api=FakeKernel()
        self.assertEqual(self.run_stop(Barrier(['OBSERVED_ZERO','OBSERVED_ZERO'],jobs),plan=plan)['status'],'STOPPED')

    def test_inventory_digest_tamper_and_barrier_coverage_mismatch_refuse(self):
        changed=self.inventory(scope_owner='different')
        changed['scope_owner']='tampered'
        self.api=FakeKernel()
        self.assertEqual(self.run_stop(plan={**self.plan,'inventory':changed})['status'],'ERROR')
        self.assertEqual(self.api.opened,[])
        row={'job_id':'job1','sha256':'b'*64,'state':'completed','identity_state':'dead'}
        inv=self.inventory(known_owned_states=[row])
        self.api=FakeKernel()
        result=self.run_stop(plan={**self.plan,'inventory':inv})
        self.assertEqual(result['status'],'ERROR'); self.assertEqual(self.api.opened,[])

    def test_inventory_known_state_must_cover_current_barrier_record_hash(self):
        jobs=[{'job_id':'job1','sha256':'b'*64}]
        row={'job_id':'job1','sha256':'b'*64,'state':'completed','identity_state':'dead'}
        plan={**self.plan,'inventory':self.inventory(known_owned_states=[row])}
        self.api=FakeKernel()
        self.assertEqual(self.run_stop(Barrier(['OBSERVED_ZERO','OBSERVED_ZERO'],jobs),plan=plan)['status'],'STOPPED')
        row['sha256']='c'*64
        plan={**self.plan,'inventory':self.inventory(known_owned_states=[row])}
        self.api=FakeKernel()
        self.assertEqual(self.run_stop(Barrier(['OBSERVED_ZERO'],jobs),plan=plan)['status'],'ERROR')


@unittest.skipUnless(os.name == 'nt' and os.environ.get('MS14_RUN_REAL_OBSERVER_STOP_FIXTURE') == '1',
                     'opt-in owned-process integration fixture; requires parent GO')
class RealObserverStopTests(unittest.TestCase):
    """Windows-only: two bounded fixture-owned processes, never a live observer."""
    def test_only_owned_observer_is_stopped(self):
        children=[]; report={'status':'STARTING','children_reaped':False}
        approved_temp=Path(os.environ.get('LOCALAPPDATA',''))/'Temp'/'opencode'
        self.assertTrue(approved_temp.is_dir(), 'approved opencode temp directory is required')
        root=Path(tempfile.mkdtemp(prefix='observer-stop-fixture-',dir=approved_temp))
        evidence_dir=Path(__file__).resolve().parents[2]/'build'/'agent-workflow'/'workflow-redesign-plan'/'wr12'
        evidence_dir.mkdir(parents=True,exist_ok=True)
        evidence=evidence_dir/f'observer-stop-real-{uuid.uuid4().hex}'
        base_python=Path(sys._base_executable).resolve()
        report.update(fixture_root=str(root),evidence_json=str(evidence.with_suffix('.json')),
                      evidence_log=str(evidence.with_suffix('.log')),base_interpreter=str(base_python))
        try:
            interpreter=_fixture_interpreter_info(); base_python=Path(interpreter['path'])
            report.update(base_interpreter=str(base_python),base_interpreter_sha256=interpreter['sha256'])
            exchange=root/'exchange'; shared=root/'shared'; exchange.mkdir(); shared.mkdir()
            source=shared/process.OBSERVER_RELATIVE; source.parent.mkdir(parents=True)
            ready=root/'target-ready.json'; neighbor_ready=root/'neighbor-ready.txt'
            source.write_text("import json,os,time\nfrom pathlib import Path\nPath(os.environ['FIXTURE_READY']).write_text(json.dumps({'pid':os.getpid()}))\ntime.sleep(18)\n",encoding='utf8')
            target_cmd=[str(base_python),str(source),'--exchange',str(exchange),'--watch']
            neighbor_code="import os,time; from pathlib import Path; Path(os.environ['FIXTURE_READY']).write_text(str(os.getpid())); time.sleep(18)"
            neighbor_cmd=[str(base_python),'-c',neighbor_code]
            report.update(target_command=target_cmd,neighbor_command=neighbor_cmd,
                          source_sha256=hashlib.sha256(source.read_bytes()).hexdigest())
            target=subprocess.Popen(target_cmd,env=dict(os.environ,FIXTURE_READY=str(ready)),
                stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); children.append(target)
            neighbor=subprocess.Popen(neighbor_cmd,env=dict(os.environ,FIXTURE_READY=str(neighbor_ready)),
                stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); children.append(neighbor)
            deadline=time.monotonic()+8
            while time.monotonic()<deadline and (not ready.exists() or not neighbor_ready.exists()): time.sleep(.05)
            self.assertTrue(ready.exists()); self.assertTrue(neighbor_ready.exists())
            target_pid=json.loads(ready.read_text())['pid']; neighbor_pid=int(neighbor_ready.read_text())
            report.update(target_ready_pid=target_pid,target_popen_pid=target.pid,neighbor_ready_pid=neighbor_pid,
                          neighbor_popen_pid=neighbor.pid)
            self.assertEqual(target_pid,target.pid,'ready PID must be the actual base-interpreter process')
            self.assertEqual(neighbor_pid,neighbor.pid)
            target_identity=gate_execution.process_identity(target_pid)
            neighbor_identity=gate_execution.process_identity(neighbor_pid)
            report.update(target_creation_identity=target_identity,neighbor_creation_identity=neighbor_identity)
            self.assertTrue(target_identity); self.assertTrue(neighbor_identity)
            receipt=root/'publication.json'
            source_hash=report['source_sha256']
            receipt.write_text(json.dumps({'shared_root':str(shared),'files':[{'target':'shared','path':process.OBSERVER_RELATIVE.as_posix(),'deleted':False,'new_sha256':source_hash}]}),encoding='utf8')
            report.update(receipt_path=str(receipt),receipt_sha256=hashlib.sha256(receipt.read_bytes()).hexdigest())
            hold=admission.begin_hold(exchange,owner='fixture',control_id='owned-stop',note='private integration fixture')
            with workflow_cutover.DrainBarrier(exchange,hold) as barrier:
                snapshot=barrier.recheck(); self.assertEqual(snapshot['status'],'OBSERVED_ZERO')
                cim=process._query_cim(target_pid); report['target_command_line_cim']=cim.get('CommandLine')
                now=int(time.time()*1000)
                inventory={'version':1,'exchange_root':str(exchange.absolute()),
                    'observer_identity':target_identity,'captured_at_ms':now,'known_owned_states':[],
                    'eligible_active':[],'unknown':[],'checked_scope':'two explicitly owned private fixture processes',
                    'scope_owner':'this test'}
                inventory['source_digest']=hashlib.sha256(json.dumps(inventory,sort_keys=True,separators=(',',':')).encode()).hexdigest()
                plan={'exchange_root':str(exchange.absolute()),'shared_root':str(shared.absolute()),
                    'publication_receipt':str(receipt),'expected_publication_receipt_sha256':report['receipt_sha256'],
                    'pid':target_pid,'created':target_identity['created'],'executable_path':cim['ExecutablePath'],
                    'command_line':cim['CommandLine'],'source_sha256':source_hash,'observed_at_ms':now,'inventory':inventory}
                result=process.stop_observer(plan,barrier=barrier)
                report['stop_result']=result
                self.assertEqual(result['status'],'STOPPED')
                target.wait(timeout=3)
            report['target_confirmed_absent']=gate_execution.identity_state(target_identity)=='dead'
            report['neighbor_alive_after_stop']=gate_execution.identity_state(neighbor_identity)=='alive'
            self.assertTrue(report['target_confirmed_absent'])
            self.assertTrue(report['neighbor_alive_after_stop'])
            report['status']='PASS'
        except BaseException as exc:
            report.update(status='FAIL',failure=f'{type(exc).__name__}: {exc}')
            raise
        finally:
            cleanup=[]
            for child in children:
                try:
                    if child.poll() is None:
                        child.terminate()
                        try: child.wait(timeout=3)
                        except subprocess.TimeoutExpired: child.kill(); child.wait(timeout=3)
                    else: child.wait(timeout=3)
                    cleanup.append({'pid':child.pid,'reaped':child.poll() is not None})
                except BaseException as exc:
                    cleanup.append({'pid':child.pid,'reaped':False,'error':str(exc)[:200]})
            report['cleanup']=cleanup
            report['children_reaped']=len(cleanup)==len(children) and all(x['reaped'] for x in cleanup)
            _write_fixture_evidence(evidence,report,root)
            self.assertTrue(report['children_reaped'],'fixture-owned child cleanup was not confirmed')


if __name__ == '__main__': unittest.main()
