import json
import tempfile
import unittest
import os
import sys
import subprocess
import time
from pathlib import Path
from unittest.mock import patch

try:
    import gate_execution
    import workflow_runtime as wf
except ModuleNotFoundError:
    from tooling.agent_workflow import gate_execution, workflow_runtime as wf


class GateExecutionTests(unittest.TestCase):
    def setUp(self):
        tempbase=Path.home()/'AppData'/'Local'/'Temp'/'opencode'; tempbase.mkdir(parents=True,exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=tempbase,prefix='gate-execution-unit-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root/'candidates').mkdir()
        candidate = {'id':'candidate1','snapshot_key':'s1','gate_plan':{'checks':[]},
                     'gate_request':{'prompt_admitted':True,'snapshot_key':'s1','session_id':'lead1','origin_session_id':'author1'}}
        (self.root/'candidates'/'candidate1.json').write_text(json.dumps(candidate))

    def test_admission_is_quick_idempotent_and_does_not_verify_or_spawn(self):
        with patch.object(wf, 'verify_gate', side_effect=AssertionError), patch.object(gate_execution.subprocess, 'Popen', side_effect=AssertionError):
            job = gate_execution.request(self.root, 'candidate1', workflow=wf)
            self.assertEqual(job, gate_execution.request(self.root, 'candidate1', workflow=wf))
        self.assertEqual(job['state'], 'requested')
        self.assertEqual(job['origin_session_id'], 'author1')

    def test_final_candidate_rejects_new_job_but_returns_existing_job_unchanged(self):
        candidate=json.loads((self.root/'candidates/candidate1.json').read_text())
        candidate['gate_outcome']={'result':'ready_for_owner'}
        (self.root/'candidates/candidate1.json').write_text(json.dumps(candidate))
        with self.assertRaisesRegex(ValueError,'gate finalized'):
            gate_execution.request(self.root,'candidate1',workflow=wf)
        self.assertFalse((self.root/'gate-execution/candidate1.json').exists())
        candidate.pop('gate_outcome'); (self.root/'candidates/candidate1.json').write_text(json.dumps(candidate))
        job=gate_execution.request(self.root,'candidate1',workflow=wf)
        job.update(state='completed',attempt=3,claim='unchanged')
        gate_execution._write(gate_execution._path(self.root,'candidate1'),job)
        candidate['gate_outcome']={'result':'ready_for_owner'}
        (self.root/'candidates/candidate1.json').write_text(json.dumps(candidate))
        self.assertEqual(gate_execution.request(self.root,'candidate1',workflow=wf),job)

    def test_owner_decision_alone_blocks_new_job_admission(self):
        candidate=json.loads((self.root/'candidates/candidate1.json').read_text())
        candidate['owner_decision']={'decision':'accept'}
        (self.root/'candidates/candidate1.json').write_text(json.dumps(candidate))
        with self.assertRaisesRegex(ValueError,'gate finalized'):
            gate_execution.request(self.root,'candidate1',workflow=wf)
        self.assertFalse((self.root/'gate-execution/candidate1.json').exists())

    def test_unsafe_timeout_cli_is_rejected(self):
        with patch('sys.argv', ['workflow', 'gate-validate', 'candidate1', '--actor', 'integration-lead', '--timeout', '1']):
            with self.assertRaises(SystemExit): wf.main()

    def test_procedural_gate_controls_are_not_public_arguments(self):
        for argv in (['workflow','gate-request','x','--wait-timeout','1'],
                     ['workflow','gate-request','x','--server','http://example'],
                     ['workflow','gate-scope-review','x','--reviewer','review','--actor','integration-lead','--server','http://example']):
            with patch('sys.argv',argv), self.assertRaises(SystemExit): wf.main()

    def test_claimed_worker_must_match_job(self):
        job = gate_execution.request(self.root, 'candidate1', workflow=wf)
        with self.assertRaises(ValueError): gate_execution.run_worker(self.root,'candidate1','wrong',1,workflow=wf)
        self.assertEqual(gate_execution.read(self.root,'candidate1')['claim'], job['claim'])

    def test_public_command_only_returns_safe_admission_fields(self):
        root=self.root/'work'/'agent_exchange'; (root/'candidates').mkdir(parents=True)
        c=json.loads((self.root/'candidates'/'candidate1.json').read_text()); (root/'candidates'/'candidate1.json').write_text(json.dumps(c))
        with patch.object(gate_execution.subprocess,'Popen',side_effect=AssertionError), patch('sys.argv',['workflow','--asset-workspace',str(self.root),'gate-validate','candidate1','--actor','integration-lead']):
            from io import StringIO
            import contextlib
            out=StringIO()
            with contextlib.redirect_stdout(out): wf.main()
        payload=json.loads(out.getvalue())
        self.assertEqual(set(payload),{'state','job_id','candidate_id','snapshot_key','reason'})

    def test_process_identity_uses_pid_and_creation(self):
        identity=gate_execution.process_identity(os.getpid())
        self.assertEqual(identity['pid'],os.getpid())
        self.assertEqual(gate_execution.identity_state(identity),'alive')
        self.assertEqual(gate_execution.identity_state({'pid':99999999,'created':'wrong'}),'dead')

    def test_permission_denied_identity_fallback_only_proves_clear_pid_reuse(self):
        saved={'pid':42,'created':132000000000000000}
        def cim(row):
            return subprocess.CompletedProcess([],0,json.dumps(row), '')
        with patch.object(gate_execution.os,'name','nt'), patch.object(gate_execution,'process_identity',side_effect=PermissionError()), \
             patch.object(gate_execution.subprocess,'run',return_value=cim({'ProcessId':42,'Created':saved['created']+20000})) as run:
            self.assertEqual(gate_execution.identity_state(saved),'dead')
            self.assertEqual(run.call_args.kwargs['timeout'],5)
            for delta in (0,9999,10000,-10000):
                run.return_value=cim({'ProcessId':42,'Created':saved['created']+delta})
                self.assertEqual(gate_execution.identity_state(saved),'unknown')
            for row in (None, [], [{'ProcessId':42,'Created':saved['created']+20000}, {'ProcessId':42,'Created':saved['created']+30000}],
                        {'ProcessId':43,'Created':saved['created']+20000},
                        {'ProcessId':42,'Created':'bad'}, {'ProcessId':42,'Created':True},
                        {'ProcessId':42,'Created':0}):
                run.return_value=cim(row) if row is not None else subprocess.CompletedProcess([],0,'', '')
                self.assertEqual(gate_execution.identity_state(saved),'unknown')
            run.side_effect=subprocess.TimeoutExpired('powershell',5)
            self.assertEqual(gate_execution.identity_state(saved),'unknown')
            run.side_effect=OSError('query failed')
            self.assertEqual(gate_execution.identity_state(saved),'unknown')

    def test_permission_fallback_rejects_invalid_saved_identity_and_never_revives(self):
        with patch.object(gate_execution.os,'name','nt'), patch.object(gate_execution,'process_identity',side_effect=PermissionError()), \
             patch.object(gate_execution.subprocess,'run') as run:
            for identity in ({'pid':True,'created':12},{'pid':0,'created':12},{'pid':42,'created':True},
                             {'pid':42,'created':0},{'pid':42,'created':'12'}):
                self.assertEqual(gate_execution.identity_state(identity),'unknown')
            run.assert_not_called()

    def test_prior_boot_proves_saved_identity_dead_when_cim_has_no_pid_row(self):
        saved={'pid':42,'created':132000000000000000}
        missing=subprocess.CompletedProcess([],0,'','')
        boot=subprocess.CompletedProcess([],0,json.dumps({'Boot':saved['created']+20_000_000}),'')
        with patch.object(gate_execution.os,'name','nt'), patch.object(gate_execution,'process_identity',side_effect=PermissionError()), \
             patch.object(gate_execution.subprocess,'run',side_effect=[missing,boot]):
            self.assertEqual(gate_execution.identity_state(saved),'dead')

    def test_prior_boot_fallback_is_conservative_and_only_used_after_native_failure(self):
        saved={'pid':42,'created':132000000000000000}
        missing=subprocess.CompletedProcess([],0,'','')
        for bootrow in ({'Boot':saved['created']}, {'Boot':saved['created']-20_000_000}, {'Boot':True}, {'Boot':'bad'}, None):
            with self.subTest(bootrow=bootrow), patch.object(gate_execution.os,'name','nt'), \
                 patch.object(gate_execution,'process_identity',side_effect=PermissionError()), \
                 patch.object(gate_execution.subprocess,'run',side_effect=[missing,subprocess.CompletedProcess([],0,json.dumps(bootrow) if bootrow is not None else '[]','')]):
                self.assertEqual(gate_execution.identity_state(saved),'unknown')
        current={'pid':42,'created':saved['created']+20_000_000}
        with patch.object(gate_execution.os,'name','nt'), patch.object(gate_execution,'process_identity',return_value=current), \
             patch.object(gate_execution.subprocess,'run',side_effect=AssertionError('boot queried despite native success')):
            self.assertEqual(gate_execution.identity_state(current),'alive')
        with patch.object(gate_execution,'process_identity',side_effect=PermissionError()), patch.object(gate_execution.os,'name','posix'), \
             patch.object(gate_execution.subprocess,'run') as run:
            self.assertEqual(gate_execution.identity_state({'pid':42,'created':12}),'unknown')
            run.assert_not_called()

    def test_current_gate_plan_descriptive_metadata_is_typed_not_execution_control(self):
        fixture=self.root/'fixture.txt'; fixture.write_text('data')
        dependency=self.root/'dependency.txt'; dependency.write_text('reference')
        plan={'title':'R26 shape','kind':'docs','acceptance_criteria':['criteria'],'fixture':'fixture.txt',
              'manual_steps':['review'],'checks':[],'no_checks_rationale':'documentation-only',
              'unverified':'Minecraft runtime remains unverified.',
              'deferred_boundaries':['Boundary '+str(i) for i in range(5)],'references':{'dependency.txt':'untrusted-input-hash'}}
        self.assertEqual(wf.validate_gate_plan(plan,self.root),plan)
        resolved=wf.resolve_gate_plan(plan,self.root)
        self.assertEqual(resolved['references'],{'fixture.txt':wf.sha(fixture),'dependency.txt':wf.sha(dependency)})
        malformed=dict(plan,unverified=['not text'])
        with self.assertRaisesRegex(ValueError,'unverified metadata'): wf.validate_gate_plan(malformed,self.root)
        malformed=dict(plan,deferred_boundaries='not a list')
        with self.assertRaisesRegex(ValueError,'deferred_boundaries'): wf.validate_gate_plan(malformed,self.root)
        forbidden=dict(plan,timeout=1)
        with self.assertRaisesRegex(ValueError,'unsupported gate plan keys'): wf.validate_gate_plan(forbidden,self.root)

    def test_popen_failure_is_durable_blocked_and_never_retried(self):
        root=self.root/'exchange'; (root/'candidates').mkdir(parents=True)
        candidate={'id':'candidate1','snapshot_key':'s1','gate_plan':{'checks':[]},'gate_request':{'prompt_admitted':True,'snapshot_key':'s1','session_id':'lead'}}
        (root/'candidates'/'candidate1.json').write_text(json.dumps(candidate))
        job=gate_execution.request(root,'candidate1',workflow=wf)
        with patch.object(gate_execution.subprocess,'Popen',side_effect=OSError('fixture spawn refused')) as spawn:
            result=gate_execution.reconcile(root,workflow=wf,now=500)
            self.assertEqual(result['blocked'],1)
            saved=gate_execution.read(root,'candidate1')
            self.assertEqual(saved['state'],'blocked'); self.assertIn('spawn refused',saved['error'])
            gate_execution.reconcile(root,workflow=wf,now=501)
            spawn.assert_called_once()
        self.assertEqual(gate_execution.read(root,'candidate1')['attempt'],1)

    def test_identityless_startup_grace_and_unknown_quarantine(self):
        root=self.root/'exchange'; (root/'candidates').mkdir(parents=True)
        c={'id':'candidate1','snapshot_key':'s1'}
        (root/'candidates'/'candidate1.json').write_text(json.dumps(c))
        folder=root/'gate-execution'; folder.mkdir()
        job={'job_id':'candidate1','candidate_id':'candidate1','snapshot_key':'s1','plan_digest':'p','claim':'x','attempt':1,
             'state':'launching','launch_started_at':100,'lead_session_id':'lead'}
        (folder/'candidate1.json').write_text(json.dumps(job))
        gate_execution.reconcile(root,workflow=wf,now=100+gate_execution.STARTUP_GRACE_SECONDS-1)
        self.assertEqual(gate_execution.read(root,'candidate1')['state'],'launching')
        gate_execution.reconcile(root,workflow=wf,now=100+gate_execution.STARTUP_GRACE_SECONDS)
        saved=gate_execution.read(root,'candidate1')
        self.assertEqual(saved['state'],'interrupted'); self.assertIn('identity remains unknown',saved['reason'])
        self.assertTrue(wf.blocker_path(root).is_file())

    def test_live_handshake_wins_startup_grace(self):
        root=self.root/'exchange'; (root/'candidates').mkdir(parents=True)
        (root/'candidates'/'candidate1.json').write_text(json.dumps({'id':'candidate1','snapshot_key':'s1'}))
        folder=root/'gate-execution'; folder.mkdir()
        job={'job_id':'candidate1','candidate_id':'candidate1','snapshot_key':'s1','plan_digest':'p','claim':'x','attempt':1,
             'state':'running','launch_started_at':100,'worker_identity':{'pid':10,'created':'born'}}
        (folder/'candidate1.json').write_text(json.dumps(job))
        with patch.object(gate_execution,'identity_state',return_value='alive'):
            gate_execution.reconcile(root,workflow=wf,now=100+gate_execution.STARTUP_GRACE_SECONDS+5)
        self.assertEqual(gate_execution.read(root,'candidate1')['state'],'running')

    def test_exact_owned_child_is_terminated_and_absence_verified(self):
        class Workflow:
            @staticmethod
            def stop_owned_tree(proc,timeout=5): wf.stop_owned_tree(proc,timeout=timeout)
        child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(20)'],
            creationflags=(getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0)|getattr(subprocess,'CREATE_NO_WINDOW',0)) if os.name=='nt' else 0,
            start_new_session=os.name!='nt')
        identity=gate_execution.process_identity(child.pid)
        self.assertIsNotNone(identity)
        record={'candidate_id':'candidate1','claim':'unitclaim','pid':child.pid,'identity':identity}
        result=gate_execution._cleanup_interrupted_child(Workflow,record)
        self.assertEqual(result['status'],'terminated_and_absent',result)
        child.wait(timeout=5)

    def _write_cleanup_proof(self,child,*,terminate):
        identity=gate_execution.process_identity(child.pid)
        job={'job_id':'candidate1','candidate_id':'candidate1','snapshot_key':'s1','plan_digest':'p',
             'claim':'unitclaim','state':'interrupted','worker_pid':99999999,
             'worker_identity':{'pid':99999999,'created':'old'},
             'owned_child':{'candidate_id':'candidate1','snapshot_key':'s1','claim':'unitclaim','check_index':1,'pid':child.pid,'identity':identity}}
        if terminate:
            cleanup=gate_execution._cleanup_interrupted_child(type('W',(),{'stop_owned_tree':staticmethod(lambda proc,timeout=5:wf.stop_owned_tree(proc,timeout=timeout))}),job['owned_child'])
            job['cleanup']=cleanup
        else:
            job['cleanup']={'status':'terminated_and_absent','candidate_id':'candidate1','snapshot_key':'s1','claim':'unitclaim',
                            'identity':identity,'method':'workflow.stop_owned_tree','tree_stop_returned':True}
        (self.root/'gate-execution').mkdir(exist_ok=True)
        (self.root/'gate-execution'/'candidate1.json').write_text(json.dumps(job))
        wf.writejson(self.root/'candidates'/'candidate1.json',{'id':'candidate1','snapshot_key':'s1'})
        wf.writejson(wf.blocker_path(self.root),{'candidate_id':'candidate1','pid':child.pid,'reason':'fixture'})

    def test_absent_root_with_live_descendant_cannot_clear_quarantine(self):
        child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(20)'],
            creationflags=(getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0)|getattr(subprocess,'CREATE_NO_WINDOW',0)) if os.name=='nt' else 0,
            start_new_session=os.name!='nt')
        self.addCleanup(lambda: child.poll() is None and wf.stop_owned_tree(child))
        self._write_cleanup_proof(child,terminate=False)
        # Blocker root is absent, but the candidate-bound tree identity remains alive.
        record=wf.readjson(wf.blocker_path(self.root)); record['pid']=99999999; wf.writejson(wf.blocker_path(self.root),record)
        with patch.object(wf,'process_presence',return_value='absent'):
            with self.assertRaisesRegex(ValueError,'alive or unknown'):
                wf.cmd_gate_clear_blocker(type('Args',(),{'actor':'integration-lead','confirmed_stopped':True,'pid':99999999})(),self.root)
        self.assertTrue(wf.blocker_path(self.root).is_file())

    def test_legacy_pid_only_blocker_refuses_and_verified_tree_proof_clears(self):
        wf.writejson(wf.blocker_path(self.root),{'pid':99999999,'reason':'legacy'})
        with patch.object(wf,'process_presence',return_value='absent'):
            with self.assertRaisesRegex(ValueError,'candidate-bound'):
                wf.cmd_gate_clear_blocker(type('Args',(),{'actor':'integration-lead','confirmed_stopped':True,'pid':99999999})(),self.root)
        child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(20)'],
            creationflags=(getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0)|getattr(subprocess,'CREATE_NO_WINDOW',0)) if os.name=='nt' else 0,
            start_new_session=os.name!='nt')
        self._write_cleanup_proof(child,terminate=True)
        blocker=wf.readjson(wf.blocker_path(self.root)); blocker['pid']=child.pid; wf.writejson(wf.blocker_path(self.root),blocker)
        child.wait(timeout=5)
        with patch.object(wf,'process_presence',return_value='absent'):
            wf.cmd_gate_clear_blocker(type('Args',(),{'actor':'integration-lead','confirmed_stopped':True,'pid':child.pid})(),self.root)
        self.assertFalse(wf.blocker_path(self.root).exists())


if __name__ == '__main__': unittest.main()
