import json
import hashlib
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from types import SimpleNamespace
from unittest import mock

import workflow_supervisor as supervisor
import workflow_cutover_transition as transition


class SupervisorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.db = self.root / 'state.db'
        self.config = self.root / 'config.json'
        self.config.write_text(json.dumps({'database': str(self.db), 'artifact_dir': str(self.root/'artifacts'),
            'opencode_server': 'http://127.0.0.1:4096', 'bindings': [{'token_file': 'private-token',
            'caller': 'test', 'context_file': 'private-context'}]}), encoding='utf-8')

    def test_admission_root_config_is_accepted_without_starting_child(self):
        config = json.loads(self.config.read_text(encoding='utf-8'))
        config['admission_root'] = 'exchange'
        self.config.write_text(json.dumps(config), encoding='utf-8')
        obj = supervisor.WorkflowSupervisor(self.db, self.config, self.root/'health.json',
                                            command=['fixture-only'], supervisor_handshake=False)
        self.assertEqual(obj.database, self.db.resolve())
        self.assertEqual(obj.command, ['fixture-only'])
        config['unexpected'] = True
        self.config.write_text(json.dumps(config), encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'invalid coordinator config'):
            supervisor.WorkflowSupervisor(self.db, self.config, self.root/'health.json',
                                          command=['fixture-only'], supervisor_handshake=False)

    def test_health_self_identity_is_actual_process_and_separate_from_wrapper_child(self):
        import gate_execution
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',
            command=['fixture-only'],supervisor_handshake=False,activation_nonce='wr12-fixture-nonce')
        obj._self_identity=gate_execution.process_identity(os.getpid())
        obj._config_sha256=__import__('hashlib').sha256(self.config.read_bytes()).hexdigest()
        obj._runtime_sha256=__import__('hashlib').sha256(Path(supervisor.__file__).resolve().read_bytes()).hexdigest()
        obj._python_sha256=__import__('hashlib').sha256(Path(sys._base_executable).resolve().read_bytes()).hexdigest()
        obj._child=SimpleNamespace(pid=os.getpid()+100000)
        obj._scope_identity={'pid':321,'created':456}
        obj._publish()
        health=json.loads((self.root/'health.json').read_text())
        proof=health['supervisor']
        self.assertEqual(proof['identity'],gate_execution.process_identity(os.getpid()))
        self.assertEqual(proof['activation_nonce'],'wr12-fixture-nonce')
        self.assertEqual(proof['config_path'],str(self.config.resolve()))
        self.assertEqual(proof['config_sha256'],__import__('hashlib').sha256(self.config.read_bytes()).hexdigest())
        self.assertEqual(proof['runtime_entry_path'],str(Path(supervisor.__file__).resolve()))
        self.assertEqual(proof['python_path'],str(Path(sys._base_executable).resolve()))
        self.assertEqual(proof['python_sha256'],__import__('hashlib').sha256(Path(sys._base_executable).resolve().read_bytes()).hexdigest())
        self.assertEqual(health['child']['pid'],os.getpid()+100000)
        self.assertNotEqual(proof['identity'],health['child']['coordinator_identity'])
        self.assertEqual(health['child']['creation_identity'],supervisor._creation_identity(obj._child))

    def _handshake_health_fixture(self, identity):
        nonce='ack-health-fixture-nonce'
        runtime_path=Path(supervisor.__file__).resolve()
        python_path=Path(sys._base_executable).resolve()
        config_sha=hashlib.sha256(self.config.read_bytes()).hexdigest()
        runtime_sha=hashlib.sha256(runtime_path.read_bytes()).hexdigest()
        python_sha=hashlib.sha256(python_path.read_bytes()).hexdigest()
        token_path=self.root/'health.token';token_path.write_text('fixture-health-token',encoding='utf-8')
        class Child:
            pid=123456
            _handle=None
            def poll(self): return None
        scope=SimpleNamespace(name='Local\\fixture-coordinator-job')
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'handshake-health.json',
            command=['venv-launcher-fixture'],supervisor_handshake=True,
            activation_nonce=nonce,on_running=lambda _receipt:None)
        obj._child=Child();obj._scope=scope;obj._scope_identity=identity;obj._state='running'
        obj._self_identity={'pid':321,'created':654321}
        obj._config_sha256=config_sha;obj._runtime_sha256=runtime_sha;obj._python_sha256=python_sha
        obj._publish()
        plan={'activation_nonce':nonce,'port':8030,'expected_config_sha256':config_sha,
            'expected_runtime_entry_sha256':runtime_sha,'expected_python_sha256':python_sha}
        values={'plan':plan,'config_path':self.config,'runtime_path':runtime_path,
            'python_path':python_path,'health_file':obj.health_file,'health_token_path':token_path}
        receipt={'supervisor_identity':obj._self_identity,'coordinator_identity':identity,
            'scope_name':scope.name,'scope_member':True,'ready_go':True,'activation_nonce':nonce,
            'config_path':str(self.config.resolve()),'config_sha256':config_sha,
            'runtime_entry_path':str(runtime_path),'runtime_entry_sha256':runtime_sha,
            'python_path':str(python_path),'python_sha256':python_sha}
        return obj,values,receipt

    def test_handshake_publish_uses_verified_ack_identity_and_real_health_verifier(self):
        ack={'pid':234567,'created':987654321}
        obj,values,receipt=self._handshake_health_fixture(ack)
        health=json.loads(obj.health_file.read_text(encoding='utf-8'))
        self.assertEqual(health['state'],'running')
        self.assertEqual(health['child'],{'pid':ack['pid'],'creation_identity':ack,
            'coordinator_identity':ack,'scope_name':'Local\\fixture-coordinator-job'})
        self.assertNotEqual(health['child']['pid'],obj._child.pid)

        class Response:
            status=200
            def __enter__(self): return self
            def __exit__(self,*args): return False
            def read(self,_size):
                return json.dumps({'status':'running','pending_events':0,'last_error':None}).encode()
        class Opener:
            def open(self,_request,timeout):
                self.timeout=timeout
                return Response()
        with mock.patch.object(transition,'_query_listeners',
                return_value=[{'LocalPort':8030,'State':'Listen','OwningProcess':ack['pid']}]), \
             mock.patch.object(transition.gate_execution,'process_identity',return_value=ack), \
             mock.patch.object(transition.urllib.request,'build_opener',return_value=Opener()):
            verified=transition._health_proof(receipt,values)
        self.assertEqual(verified['status'],'VERIFIED')
        self.assertEqual(verified['coordinator_identity'],ack)

        health['child']['creation_identity']={'pid':obj._child.pid,'created':1}
        obj.health_file.write_text(json.dumps(health),encoding='utf-8')
        held=transition._health_proof(receipt,values)
        self.assertEqual(held['status'],'HELD')
        self.assertEqual(held['reason'],'supervisor health-file receipt or hash drift')

    def test_handshake_unknown_malformed_or_missing_scope_cannot_publish_running(self):
        valid={'pid':234567,'created':987654321}
        invalid=(None,{'pid':valid['pid'],'created':True},
            {'pid':valid['pid']}, {'pid':valid['pid'],'created':valid['created'],'extra':1})
        for identity in invalid:
            with self.subTest(identity=identity):
                obj,_,_=self._handshake_health_fixture(identity)
                health=json.loads(obj.health_file.read_text(encoding='utf-8'))
                self.assertEqual(health['state'],'paused')
                self.assertIsNone(health['child'])
                self.assertIn('identity/scope proof unavailable',health['fault'])
                self.assertFalse(supervisor.WorkflowSupervisor._valid_raw_identity(identity))
                with self.assertRaises(RuntimeError):
                    supervisor.WorkflowSupervisor._verified_ready_identity(identity,valid)
        missing,_,_=self._handshake_health_fixture(valid)
        missing._scope=None
        missing._state='running'
        missing._publish()
        health=json.loads(missing.health_file.read_text(encoding='utf-8'))
        self.assertEqual(health['state'],'paused')
        self.assertIsNone(health['child'])
        with self.assertRaisesRegex(RuntimeError,'identity unavailable or mismatched'):
            supervisor.WorkflowSupervisor._verified_ready_identity(valid,None)
        with self.assertRaisesRegex(RuntimeError,'identity unavailable or mismatched'):
            supervisor.WorkflowSupervisor._verified_ready_identity(valid,{'pid':valid['pid'],'created':1})

    def test_unknown_self_identity_pauses_before_any_child_launch(self):
        launched=[]
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=['fixture'],
            supervisor_handshake=False,popen=lambda *a,**k:launched.append(a),activation_nonce='required')
        with mock.patch('gate_execution.process_identity',return_value=None):
            obj.run()
        health=json.loads((self.root/'health.json').read_text())
        self.assertEqual(health['state'],'paused')
        self.assertEqual(health['supervisor']['identity_status'],'unknown')
        self.assertIsNone(health['supervisor']['identity'])
        self.assertEqual(health['supervisor']['activation_nonce'],'required')
        self.assertEqual(launched,[])

    def test_run_health_binds_supervisor_not_venv_launcher_pid(self):
        import gate_execution
        class Wrapper:
            pid=os.getpid()+100000
            def poll(self): return 0
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=['fixture'],
            supervisor_handshake=False,popen=lambda *a,**k:Wrapper(),activation_nonce='fixture-nonce',backoff=(0,0,0))
        snapshots=[]; publish=obj._publish
        def capture():
            publish(); snapshots.append(json.loads((self.root/'health.json').read_text())['supervisor'])
        obj._publish=capture
        obj.run()
        proof=snapshots[0]
        self.assertEqual(proof['identity'],gate_execution.process_identity(os.getpid()))
        self.assertEqual(proof['activation_nonce'],'fixture-nonce')
        self.assertEqual(proof['config_sha256'],__import__('hashlib').sha256(self.config.read_bytes()).hexdigest())
        self.assertEqual(proof['runtime_entry_sha256'],__import__('hashlib').sha256(Path(supervisor.__file__).resolve().read_bytes()).hexdigest())
        self.assertEqual(proof['python_path'],str(Path(sys._base_executable).resolve()))
        self.assertEqual(proof['python_sha256'],__import__('hashlib').sha256(Path(sys._base_executable).resolve().read_bytes()).hexdigest())

    def test_python_image_hash_failure_pauses_before_launch(self):
        launched=[]
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=['fixture'],
            supervisor_handshake=False,popen=lambda *a,**k:launched.append(a),activation_nonce='required')
        original=Path.read_bytes
        def fail_python(path):
            if path.resolve()==Path(sys._base_executable).resolve(): raise OSError('fixture image unavailable')
            return original(path)
        with mock.patch('gate_execution.process_identity',return_value={'pid':os.getpid(),'created':1}), \
             mock.patch.object(Path,'read_bytes',fail_python):
            obj.run()
        health=json.loads((self.root/'health.json').read_text())
        self.assertEqual(health['state'],'paused')
        self.assertEqual(health['supervisor']['identity_status'],'unknown')
        self.assertIsNone(health['supervisor']['python_sha256'])
        self.assertEqual(launched,[])

    def test_missing_base_interpreter_path_pauses_before_launch(self):
        launched=[]
        with mock.patch.object(sys,'_base_executable',None):
            obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=['fixture'],
                supervisor_handshake=False,popen=lambda *a,**k:launched.append(a))
            obj.run()
        health=json.loads((self.root/'health.json').read_text())
        self.assertEqual(health['state'],'paused')
        self.assertEqual(health['supervisor']['identity_status'],'unknown')
        self.assertIsNone(health['supervisor']['python_sha256'])
        self.assertEqual(launched,[])

    def test_reported_python_image_is_base_interpreter_not_venv_launcher(self):
        base_image=self.root/'base-python.exe'; launcher=self.root/'venv-launcher.exe'
        base_image.write_bytes(b'actual-base-interpreter-fixture')
        launcher.write_bytes(b'venv-launcher-fixture')
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=None,
            activation_nonce='base-image-fixture',popen=lambda *a,**k:None)
        self.assertEqual(obj._python_path,Path(sys._base_executable).resolve())
        self.assertEqual(obj.command[0],sys.executable)
        self.assertNotEqual(__import__('hashlib').sha256(base_image.read_bytes()).hexdigest(),
                            __import__('hashlib').sha256(launcher.read_bytes()).hexdigest())
        with mock.patch.object(sys,'_base_executable',str(base_image)), mock.patch.object(sys,'executable',str(launcher)):
            obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=None,
                activation_nonce='base-image-fixture',popen=lambda *a,**k:None)
            self.assertEqual(obj._python_path,base_image.resolve())
            self.assertEqual(obj.command[0],str(launcher))
            obj._self_identity={'pid':1,'created':2}; obj._config_sha256='config'; obj._runtime_sha256='runtime'
            obj._python_sha256=__import__('hashlib').sha256(base_image.read_bytes()).hexdigest()
            obj._publish()
            health=json.loads((self.root/'health.json').read_text())['supervisor']
            self.assertEqual(health['python_path'],str(base_image.resolve()))
            self.assertEqual(health['python_sha256'],__import__('hashlib').sha256(base_image.read_bytes()).hexdigest())
            self.assertEqual(obj.command[0],str(launcher))

    def _fake_running_supervisor(self, callback, *, scope_failure=False):
        import gate_execution
        child_identity={'pid':123456,'created':987}
        class Child:
            pid=123456
            returncode=None
            def poll(self): return self.returncode
            def wait(self,timeout=None):
                if self.returncode is None: self.returncode=0
                return self.returncode
        child=Child()
        class Scope:
            name='fixture-job'
            def __init__(self): self.attached=None; self.created=None
            def attach(self,identity): self.attached=identity
            def contains(self,identity): return identity==self.attached
            def snapshot(self): return {'active_processes':1}
            def terminate_and_wait(self,timeout):
                if scope_failure: raise RuntimeError('owned scope uncertain')
                child.returncode=0
                return {'status':'CONFIRMED','active_processes':0}
            def close(self): pass
        scope=Scope(); created=[]
        def create_scope(*args): created.append(scope); return scope
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',
            command=[sys.executable,'fixture-coordinator'],supervisor_handshake=True,
            popen=lambda *a,**k:child,activation_nonce='hook-nonce',on_running=callback,
            loss_reconciler=lambda:None)
        obj._await_ready=lambda proc,path,token:child_identity
        return obj,child,scope,create_scope,child_identity

    @unittest.skipUnless(os.name=='nt','Job Object handshake fixture requires Windows semantics')
    def test_running_hook_receives_ordered_same_thread_identity_receipt_once(self):
        seen=[]; run_thread=threading.get_ident()
        obj,child,scope,create_scope,child_identity=self._fake_running_supervisor(
            lambda receipt:(seen.append((receipt,threading.get_ident(),json.loads((self.root/'health.json').read_text())['state'])),obj.stop()))
        from unittest.mock import patch
        raw_self={'pid':os.getpid(),'created':456}
        with patch('workflow_process_scope.OwnedProcessScope.create',side_effect=create_scope), \
             patch('gate_execution.process_identity',side_effect=lambda pid:raw_self if pid==os.getpid() else child_identity):
            obj.run()
        receipt,callback_thread,health_state=seen[0]
        self.assertEqual(callback_thread,run_thread)
        self.assertEqual(health_state,'running')
        self.assertEqual(receipt['supervisor_identity'],raw_self)
        self.assertEqual(receipt['coordinator_identity'],child_identity)
        self.assertTrue(receipt['scope_member']); self.assertTrue(receipt['ready_go'])
        self.assertEqual(receipt['activation_nonce'],'hook-nonce')
        self.assertEqual(len(seen),1)

    @unittest.skipUnless(os.name=='nt','Job Object handshake fixture requires Windows semantics')
    def test_running_hook_failure_pauses_cleans_owned_child_without_restart(self):
        calls=[]
        def fail(_receipt): calls.append(threading.get_ident()); raise ValueError('proof rejected')
        obj,child,scope,create_scope,child_identity=self._fake_running_supervisor(fail)
        launches=[]; obj._popen=lambda *a,**k:launches.append(a) or child
        from unittest.mock import patch
        raw_self={'pid':os.getpid(),'created':456}
        with patch('workflow_process_scope.OwnedProcessScope.create',side_effect=create_scope), \
             patch('gate_execution.process_identity',side_effect=lambda pid:raw_self if pid==os.getpid() else child_identity):
            obj.run()
        health=json.loads((self.root/'health.json').read_text())
        self.assertEqual(health['state'],'paused')
        self.assertIn('CUTOVER_RUNNING_PROOF_FAILED',health['fault'])
        self.assertEqual(len(launches),1); self.assertEqual(len(calls),1)
        self.assertIsNone(obj._scope); self.assertIsNone(obj._lock)

    @unittest.skipUnless(os.name=='nt','Job Object handshake fixture requires Windows semantics')
    def test_callback_failure_with_uncertain_scope_retains_supervisor_guard(self):
        obj,child,scope,create_scope,child_identity=self._fake_running_supervisor(
            lambda receipt:(_ for _ in ()).throw(ValueError('proof rejected')),scope_failure=True)
        lock=mock.Mock()
        from unittest.mock import patch
        raw_self={'pid':os.getpid(),'created':456}
        with patch('workflow_process_scope.OwnedProcessScope.create',side_effect=create_scope), \
             patch('gate_execution.process_identity',side_effect=lambda pid:raw_self if pid==os.getpid() else child_identity), \
             patch.object(supervisor.workflow_runtime,'gate_lock',return_value=lock):
            obj.run()
        health=json.loads((self.root/'health.json').read_text())
        self.assertEqual(health['state'],'paused')
        self.assertIn('CUTOVER_RUNNING_PROOF_FAILED',health['fault'])
        self.assertIn('owned child cleanup unknown',health['fault'])
        lock.close.assert_not_called()

    @unittest.skipUnless(os.name=='nt','Job Object handshake fixture requires Windows semantics')
    def test_running_hook_is_not_reinvoked_after_coordinator_restart(self):
        import gate_execution
        class Child:
            def __init__(self,pid,code=None): self.pid=pid; self.returncode=code
            def poll(self): return self.returncode
            def wait(self,timeout=None):
                if self.returncode is None: self.returncode=0
                return self.returncode
        children=[Child(123456,17),Child(123457)]
        scopes=[]; launches=[]; calls=[]; obj_ref={}
        class Scope:
            name='restart-fixture'
            def __init__(self,child): self.child=child; self.attached=None
            def attach(self,ident): self.attached=ident
            def contains(self,ident): return ident==self.attached
            def snapshot(self): return {'active_processes':1}
            def terminate_and_wait(self,timeout): self.child.returncode=0; return {'status':'CONFIRMED'}
            def close(self): pass
        def spawn(*args,**kwargs):
            child=children[len(launches)]; launches.append(child)
            if len(launches)==2: obj_ref['obj'].stop()
            return child
        callback=lambda proof:calls.append(proof)
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=['fixture'],
            supervisor_handshake=True,popen=spawn,activation_nonce='restart-nonce',on_running=callback,
            backoff=(0,0,0),loss_reconciler=lambda:None)
        obj_ref['obj']=obj
        def create_scope(*args):
            scope=Scope(children[len(scopes)]); scopes.append(scope); return scope
        def ready(proc,path,token): return {'pid':proc.pid,'created':1000+proc.pid}
        obj._await_ready=ready
        identities={os.getpid():{'pid':os.getpid(),'created':1},123456:{'pid':123456,'created':124456},123457:{'pid':123457,'created':124457}}
        from unittest.mock import patch
        with patch('workflow_process_scope.OwnedProcessScope.create',side_effect=create_scope), \
             patch('gate_execution.process_identity',side_effect=lambda pid:identities.get(pid)):
            obj.run()
        self.assertEqual(len(launches),2)
        self.assertEqual(len(calls),1)
        self.assertEqual(calls[0]['coordinator_identity'],identities[123456])

    def launch_thread(self, child, cmd, backoff=(0, 0, 0)):
        obj = supervisor.WorkflowSupervisor(self.db, self.config, self.root/'health.json',
            command=cmd, backoff=backoff)
        thread = threading.Thread(target=obj.run)
        thread.start()
        self.addCleanup(lambda: (obj.stop(), thread.join(8)))
        return obj, thread

    def write_handshake_fixture(self, fixture, childinfo):
        fixture.write_text(f'''import hashlib, json, os, subprocess, sys, time
from pathlib import Path
sys.path.insert(0, {str(Path(__file__).resolve().parent)!r})
import gate_execution
a=sys.argv; p=Path(a[a.index('--supervisor-ready-path')+1]); t=next(x[len('--supervisor-token='):] for x in a if x.startswith('--supervisor-token='))
p.write_text(json.dumps({{'identity':gate_execution.process_identity(os.getpid()),'token_digest':hashlib.sha256(t.encode()).hexdigest()}}))
g=p.with_suffix(p.suffix+'.go'); end=time.time()+8
while time.time()<end:
    try:
        if json.loads(g.read_text())=={{'token':t}}: break
    except OSError: pass
    time.sleep(.02)
c=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
Path({str(childinfo)!r}).write_text(json.dumps([gate_execution.process_identity(os.getpid()),gate_execution.process_identity(c.pid)]))
time.sleep(60)
''',encoding='utf-8')
        return fixture

    def test_exit_then_healthy_recovers_and_only_one_child(self):
        marker = self.root/'count'
        script = "import pathlib,sys,time; p=pathlib.Path(sys.argv[1]); n=int(p.read_text())+1 if p.exists() else 1; p.write_text(str(n)); sys.exit(17) if n==1 else time.sleep(30)"
        obj, thread = self.launch_thread(None, [sys.executable, '-c', script, str(marker)])
        deadline = time.time()+5
        while time.time()<deadline:
            try: h=json.loads((self.root/'health.json').read_text()) if (self.root/'health.json').exists() else {}
            except OSError: h={}
            if h.get('state')=='running' and marker.exists() and marker.read_text()=='2': break
            time.sleep(.02)
        self.assertEqual(marker.read_text(), '2')
        self.assertEqual(h['last_exit'], 17)
        self.assertEqual(h['state'], 'running')
        obj.stop(); thread.join(8)
        self.assertFalse(thread.is_alive())
        self.assertEqual(h['child']['pid'] > 0, True)

    def test_three_failures_pause_without_more_launches(self):
        count=self.root/'count'
        script="import pathlib,sys; p=pathlib.Path(sys.argv[1]); p.write_text(str(int(p.read_text())+1) if p.exists() else '1'); sys.exit(9)"
        obj, thread=self.launch_thread(None,[sys.executable,'-c',script,str(count)])
        thread.join(8)
        self.assertFalse(thread.is_alive())
        health=json.loads((self.root/'health.json').read_text())
        self.assertEqual(count.read_text(),'3')
        self.assertEqual(health['state'],'paused')
        self.assertEqual(health['last_exit'],9)

    def test_stop_kills_only_owned_child_and_duplicate_guard_prevents_spawn(self):
        unrelated=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'])
        def clean_unrelated():
            if unrelated.poll() is None: unrelated.kill(); unrelated.wait(timeout=5)
        self.addCleanup(clean_unrelated)
        owned=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'])
        obj=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'health.json',command=[])
        obj._child=owned
        import workflow_runtime
        workflow_runtime.stop_owned_tree(owned,timeout=5)
        self.assertIsNotNone(owned.poll())
        self.assertIsNone(unrelated.poll())
        guard=workflow_runtime.gate_lock(self.db.with_suffix('.db.supervisor'),nonblocking=True)
        launched=[]
        contender=supervisor.WorkflowSupervisor(self.db,self.config,self.root/'duplicate.json',command=['unused'],popen=lambda *a,**k: launched.append(a))
        thread=threading.Thread(target=contender.run); thread.start(); thread.join(3)
        guard.close()
        self.assertEqual(launched,[])
        self.assertEqual(json.loads((self.root/'duplicate.json').read_text())['state'],'paused')

    def test_production_command_uses_explicit_port_and_trusted_script_override(self):
        script = self.root/'child.py'
        marker = self.root/'args'
        script.write_text(f"import pathlib,sys,time; pathlib.Path({str(marker)!r}).write_text(' '.join(sys.argv[1:])); time.sleep(30)", encoding='utf-8')
        obj = supervisor.WorkflowSupervisor(self.db, self.config, self.root/'health.json', port=43210,
            coordinator_script=script)
        # This test checks command construction; the child is not a coordinator handshake fixture.
        obj._handshake = False
        self.assertEqual(obj.command, [sys.executable, str(script), 'serve', '--config', str(self.config), '--port', '43210'])
        thread = threading.Thread(target=obj.run); thread.start(); thread.join(5)
        if thread.is_alive(): obj.stop(); thread.join(8)
        self.assertFalse(thread.is_alive())
        self.assertIn('--port 43210', marker.read_text(encoding='utf-8'))

    def test_coordinator_cli_accepts_dash_prefixed_supervisor_token(self):
        # Exercise the real coordinator argparse entry point: an option-looking
        # token must be consumed as the value, not parsed as another option.
        coordinator = Path(supervisor.__file__).with_name('workflow_coordinator.py')
        result = subprocess.run([sys.executable, str(coordinator), 'serve',
            '--supervisor-token=-option-looking-token'], capture_output=True, text=True, timeout=10)
        self.assertNotIn('argument --supervisor-token: expected one argument', result.stderr)
        self.assertIn('the following arguments are required: --config, --port', result.stderr)

    def test_database_mismatch_rejected_before_spawn(self):
        with self.assertRaisesRegex(ValueError, 'exactly match'):
            supervisor.WorkflowSupervisor(self.root/'other.db', self.config, self.root/'health.json')

    def test_launch_uses_platform_process_group_containment(self):
        calls = []
        class Exited:
            pid = 123
            def poll(self): return 0
        def launch(command, **kwargs):
            calls.append(kwargs)
            return Exited()
        obj = supervisor.WorkflowSupervisor(self.db, self.config, self.root/'health.json',
            command=['test'], popen=launch, backoff=(0, 0, 0))
        obj.run()
        self.assertEqual(len(calls), 3)
        if os.name == 'nt':
            self.assertTrue(all('creationflags' in c and 'start_new_session' not in c for c in calls))
        else:
            self.assertTrue(all(c['start_new_session'] for c in calls))
        self.assertTrue(all(c['close_fds'] for c in calls))

    @unittest.skipUnless(os.name == 'nt', 'Job Object containment is Windows-specific')
    def test_handshake_contains_actual_python_and_later_grandchild(self):
        ready = self.root/'ready.json'
        fixture = self.root/'coordinator_fixture.py'
        childinfo = self.root/'members.json'
        self.write_handshake_fixture(fixture, childinfo)
        unrelated = subprocess.Popen([sys.executable, '-c', 'import time;time.sleep(60)'])
        obj = supervisor.WorkflowSupervisor(self.db, self.config, self.root/'health.json',
            command=[sys.executable, str(fixture)], supervisor_handshake=True)
        thread = threading.Thread(target=obj.run); thread.start()
        try:
            deadline=time.time()+10
            while time.time()<deadline and not childinfo.exists(): time.sleep(.02)
            members=json.loads(childinfo.read_text())
            self.assertEqual(obj._scope_identity['pid'], members[0]['pid'])
            obj.stop(); thread.join(8)
            self.assertFalse(thread.is_alive())
            import gate_execution
            self.assertIsNone(gate_execution.process_identity(members[0]['pid']))
            self.assertIsNone(gate_execution.process_identity(members[1]['pid']))
            self.assertIsNone(unrelated.poll())
        finally:
            obj.stop(); thread.join(8)
            if unrelated.poll() is None: unrelated.kill(); unrelated.wait(timeout=5)

    @unittest.skipUnless(os.name == 'nt', 'Job Object containment is Windows-specific')
    def test_abrupt_supervisor_exit_closes_job_and_kills_coordinator_tree(self):
        import gate_execution
        fixture=self.write_handshake_fixture(self.root/'coordinator_fixture.py',self.root/'members.json')
        outcome=self.root/'creator-outcome.json'
        creator=self.root/'supervisor_creator.py'
        creator.write_text(f'''import json, os, sys, threading, time
from pathlib import Path
sys.path.insert(0, {str(Path(__file__).resolve().parent)!r})
import workflow_supervisor
root=Path({str(self.root)!r})
obj=workflow_supervisor.WorkflowSupervisor(root/'state.db',root/'config.json',root/'creator-health.json',command=[sys.executable,{str(fixture)!r}],supervisor_handshake=True)
thread=threading.Thread(target=obj.run); thread.start()
members=root/'members.json'; end=time.time()+10
while time.time()<end and not members.exists(): time.sleep(.02)
if not members.exists(): os._exit(4)
Path({str(outcome)!r}).write_text(members.read_text())
os._exit(17)
''',encoding='utf-8')
        unrelated=subprocess.Popen([sys.executable,'-c','import time;time.sleep(60)'])
        creator_proc=subprocess.Popen([sys.executable,str(creator)],cwd=str(Path(__file__).resolve().parent))
        try:
            self.assertEqual(creator_proc.wait(timeout=15),17)
            members=json.loads(outcome.read_text(encoding='utf-8'))
            self.assertTrue(all(isinstance(x,dict) and x.get('pid') for x in members))
            deadline=time.time()+8
            while time.time()<deadline and any(gate_execution.process_identity(x['pid']) for x in members): time.sleep(.025)
            self.assertIsNone(gate_execution.process_identity(members[0]['pid']))
            self.assertIsNone(gate_execution.process_identity(members[1]['pid']))
            self.assertIsNone(unrelated.poll())
        finally:
            if creator_proc.poll() is None: creator_proc.kill(); creator_proc.wait(timeout=5)
            if unrelated.poll() is None: unrelated.kill(); unrelated.wait(timeout=5)


if __name__ == '__main__': unittest.main()
