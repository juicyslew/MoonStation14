import tempfile
import unittest
from pathlib import Path
import os
import sys
import time
import hashlib
from unittest.mock import patch

import workflow_native_worker
import workflow_submission
import workflow_store
import workflow_preflight
import workflow_runtime
import workflow_error_recovery
import workflow_native_fixtures
from preflight_test_support import make_preflight_case


class NativeInputLoaderTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.root=Path(self.tmp.name)
        self.case=make_preflight_case(self.root,repo_name='repo',caller='loader-caller',key='loader-key')
        self.store=workflow_store.WorkflowStore(self.root/'workflow.sqlite')
        result=workflow_submission.submit_submission(self.store,caller=self.case.caller,idempotency_key=self.case.key,
            request=self.case.request,context=self.case.context,artifact_dir=self.root/'artifacts')
        self.assertEqual(result['status'],'ACCEPTED')
        self.run_id=result['run_id']

    def tearDown(self): self.tmp.cleanup()

    def test_resolves_actual_accepted_bundle_and_live_source(self):
        request,bundle=workflow_native_worker.load_native_inputs(self.store,self.run_id)
        self.assertEqual(request,self.case.request)
        self.assertEqual(bundle['input_digest'],request['input_digest'])
        self.assertEqual(bundle['request_digest'],workflow_store.digest(request))

    def test_tampered_frozen_bundle_fails_integrity(self):
        saved=self.store.get_run_submission(self.run_id)
        path=next(Path(b['artifact']['path']) for b in saved['artifacts'] if Path(b['artifact']['path']).name=='preflight-bundle.json')
        path.write_bytes(path.read_bytes()+b' ')
        with self.assertRaisesRegex(ValueError,'integrity'):
            workflow_native_worker.load_native_inputs(self.store,self.run_id)

    def test_changed_live_source_fails_applicability(self):
        (self.case.repo/'dirty.txt').write_text('changed after admission')
        with self.assertRaisesRegex(ValueError,'current source'):
            workflow_native_worker.load_native_inputs(self.store,self.run_id)

    def test_readonly_frozen_load_skips_only_current_tree_capture(self):
        (self.case.repo/'unrelated.txt').write_text('changed')
        with patch.object(workflow_preflight,'capture_source',side_effect=AssertionError('live capture called')):
            request,bundle=workflow_native_worker.load_native_inputs(
                self.store,self.run_id,verify_current_source=False)
        self.assertEqual(self.case.request['input_digest'],request['input_digest'])
        self.assertEqual(request['input_digest'],workflow_store.digest(bundle['source_manifest']))
        saved=self.store.get_run_submission(self.run_id)
        bundle_artifact=workflow_native_worker.resolve_generated_bundle(saved)
        path=Path(bundle_artifact['path'])
        path.write_bytes(path.read_bytes()+b' ')
        with self.assertRaisesRegex(ValueError,'integrity'):
            workflow_native_worker.load_native_inputs(self.store,self.run_id,verify_current_source=False)

    def test_native_execution_commits_typed_pass_and_replay_does_not_rerun(self):
        self.store=workflow_store.WorkflowStore(self.root/'worker.sqlite')
        self.case.key='worker-key'; self.case.request['idempotency_key']='worker-key'
        self.case.submission_id='submit_'+__import__('hashlib').sha256((self.case.caller+'\0submit\0worker-key').encode()).hexdigest()[:24]
        self.case.artifact['submission_id']=self.case.submission_id; self.case.artifact['producer_id']=self.case.submission_id
        producer=self.case.repo/'producer.py'; counter=self.case.repo/'counter.txt'; output='build/output.zip'
        producer.write_text("from pathlib import Path\np=Path('build/output.zip'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'fresh-package')\nc=Path('counter.txt'); c.write_text(str(int(c.read_text())+1) if c.exists() else '1')\n")
        (self.case.repo/'.gitignore').write_text('counter.txt\nbuild/\n')
        # Recompute admission input only after the deterministic test fixture exists.
        self.case.request['input_digest']=workflow_store.digest(workflow_preflight.capture_source(self.case.repo))
        inner={'title':'worker fixture','kind':'feature','acceptance_criteria':['c1'],'fixture':'manual.md',
              'manual_steps':['Run the manual scenario.'],'references':{},'checks':[{'argv':[sys.executable,str(producer)],
              'rationale':'Produce test package','covers':['c1'],'kind':'build','artifacts':[output]}]}
        plan={'registry_version':1,'packs':[],'plan':inner,'program_checks':[]}
        self.case.request['plan_digest']=workflow_store.digest(plan)
        identity=workflow_native_worker.native_process_identity(os.getpid())
        # A test-only trusted resolver fixture; runtime executes the frozen argv normally.
        with patch.object(workflow_preflight,'resolve_check_packs',return_value=plan):
            result=workflow_submission.submit_submission(self.store,caller=self.case.caller,idempotency_key=self.case.key,
                request=self.case.request,context=self.case.context,artifact_dir=self.root/'artifacts')
        self.assertEqual(result['status'],'ACCEPTED')
        # Resolve the actual saved task via the run's SQL-owned task row.
        db=self.store._connect()
        try: task_id=db.execute('select id from tasks where run_id=?',(result['run_id'],)).fetchone()[0]
        finally: db.close()
        self.store.dispatch_task(task_id=task_id,event_id='event_worker_dispatch',process_identity=identity,claim_token='worker-token')
        kwargs=dict(task_id=task_id,process_identity=identity,claim_token='worker-token',artifact_dir=self.root/'artifacts',lock_dir=self.root/'lock')
        first=workflow_native_worker.execute_native_task(self.store,**kwargs)
        self.assertEqual(first['decision'],'PASS')
        self.assertEqual(counter.read_text(),'1')
        second=workflow_native_worker.execute_native_task(self.store,**kwargs)
        self.assertEqual(second,first)
        self.assertEqual(counter.read_text(),'1')
        refs=first['artifact_refs']
        self.assertTrue(any(self.store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT' for ref in refs))

    def test_native_execution_preflights_unit_and_package_adaptations(self):
        junit="pass\n"
        checks=[{'argv':['./gradlew','test','--tests','FixtureTest','--no-daemon'],'rationale':'unit','covers':['c1'],'kind':'junit','selectors':['FixtureTest']},
                {'argv':['./gradlew','clean','build','--no-daemon'],'rationale':'package','covers':['c1'],'kind':'build','artifacts':['build/libs/out.jar']}]
        seen=[]
        original=workflow_native_worker._preflight_fixture_bindings
        def preflight(*args,**kwargs):
            original(*args,**kwargs); seen.append((args[1],kwargs.get('retry_bindings')))
        def stop_before_children(*_args,**_kwargs): raise RuntimeError('stop after preflight test boundary')
        with patch.object(workflow_native_worker,'_preflight_fixture_bindings',side_effect=preflight), \
             patch.object(workflow_runtime,'run_finite_checks',side_effect=stop_before_children):
            store,result,root=self.run_case('unit-package-preflight',junit,checks=checks)
        self.assertEqual(1,len(seen)); self.assertEqual({},seen[0][1])
        adapted=seen[0][0]['checks']
        self.assertEqual(['./gradlew','test','--rerun','--tests','FixtureTest','--no-daemon'],adapted[0]['argv'])
        self.assertEqual(['./gradlew','build','--no-daemon'],adapted[1]['argv'])

    def test_native_unit_callback_keeps_accepted_and_fresh_argv_provenance(self):
        checks=[{'argv':['./gradlew','test','--tests','FixtureTest','--no-daemon'],'rationale':'unit','covers':['c1'],'kind':'junit','selectors':['FixtureTest']},
                {'argv':['./gradlew','clean','build','--no-daemon'],'rationale':'package','covers':['c1'],'kind':'build','artifacts':['build/libs/out.jar']}]
        saved=[]
        def complete_first(repo,plan,logdir,**kwargs):
            kwargs['verify_inputs']()
            check=plan['checks'][0]; logdir=Path(logdir); logdir.mkdir(parents=True,exist_ok=True)
            log=logdir/'fake-unit.log'; log.write_text('GATE COMMAND EXIT: 0\n',encoding='utf-8')
            record={'argv':check['argv'],'executed_argv':workflow_runtime.execution_argv(repo,check['argv']),
                'kind':check['kind'],'rationale':check['rationale'],'covers':check['covers'],'exit_code':0,
                'log':str(log),'log_sha256':hashlib.sha256(log.read_bytes()).hexdigest(),
                'evidence':{'status':'PASS','counts':{},'artifacts':[]}}
            kwargs['record_completed'](record)
            raise RuntimeError('intentional stop after unit provenance callback')
        with patch.object(workflow_runtime,'run_finite_checks',side_effect=complete_first), \
             patch.object(workflow_native_worker.workflow_toolchain,'capture',return_value={'fixture_toolchain':'unit-callback'}):
            store,result,root=self.run_case('unit-callback-provenance','pass\n',checks=checks)
        for ref in result.get('artifact_refs',[]):
            artifact=store.get_artifact(ref)['artifact']
            if artifact['type']=='PROVENANCE':
                parsed=workflow_store.strict_loads(Path(artifact['path']).read_text(encoding='utf-8'))
                if parsed.get('kind')=='NATIVE_CHECK_COMPLETED': saved.append(parsed)
        self.assertEqual(1,len(saved))
        runtime=saved[0]['runtime_fixture']
        self.assertEqual('managed-unit-fresh-evidence-v1',runtime['policy_id'])
        self.assertEqual(checks[0]['argv'],runtime['accepted_argv'])
        self.assertEqual(['./gradlew','test','--rerun','--tests','FixtureTest','--no-daemon'],runtime['execution_argv'])

    def test_fast_exited_child_without_identity_registration_uses_handle_exit_proof(self):
        import gate_execution
        original_runner=workflow_runtime.run_finite_checks
        def completed_before_registration(repo,plan,logdir,**kwargs):
            callback=kwargs['child_started']
            def exited_callback(proc,check,log):
                proc.wait(timeout=10)
                with patch.object(gate_execution,'process_identity',return_value=None):
                    callback(proc,check,log)
            kwargs['child_started']=exited_callback
            return original_runner(repo,plan,logdir,**kwargs)
        with patch.object(workflow_runtime,'run_finite_checks',side_effect=completed_before_registration):
            store,result,root=self.run_case('fast-child-exit','pass\n')
        self.assertEqual(result['decision'],'PASS',result)
        self.assertEqual(store.get_resource_history('native_validation')[-1]['state'],'RELEASED')
        proofs=[]
        for ref in result['artifact_refs']:
            artifact=store.get_artifact(ref)['artifact']
            if artifact['type']!='PROVENANCE': continue
            parsed=workflow_store.strict_loads(Path(artifact['path']).read_text(encoding='utf-8'))
            if isinstance(parsed,dict) and parsed.get('no_live_owned_children') is True: proofs.append(parsed)
        self.assertEqual(len(proofs),1)
        self.assertTrue(any(child.get('proof')=='owned_popen_exit' and child['exit_code']==0
                            for child in proofs[0]['children']))

    def run_case(self, key, script, *, kind='command', artifact=None, changed=False, checks=None,
                 checks_factory=None, after_digest=None, ignored_config=False, mutate_config_after=False, extra_receipt_rows=()):
        root=self.root/key; root.mkdir()
        case=make_preflight_case(root,repo_name='repo',caller='worker-'+key,key=key)
        (case.repo/'producer.py').write_text(script)
        (case.repo/'.gitignore').write_text('build/\n')
        if ignored_config:
            import subprocess
            (case.repo/'.gitignore').write_text('build/\nopencode.jsonc\n')
            subprocess.run(['git','rm','--cached','-q','opencode.jsonc'],cwd=case.repo,check=True)
            subprocess.run(['git','add','.gitignore'],cwd=case.repo,check=True)
            subprocess.run(['git','commit','-qm','ignore managed config fixture'],cwd=case.repo,check=True)
        if extra_receipt_rows:
            receipt=workflow_store.strict_loads(case.context.publication_receipt_path.read_text())
            receipt['files'].extend(extra_receipt_rows)
            data=workflow_store.canonical_json(receipt).encode()
            case.context.publication_receipt_path.write_bytes(data)
            object.__setattr__(case.context,'publication_receipt_sha256',__import__('hashlib').sha256(data).hexdigest())
        store=workflow_store.WorkflowStore(root/'db.sqlite')
        checks=checks or [{'argv':[sys.executable,'producer.py'],'rationale':'fixture','covers':['c1'],'kind':kind,
            **({'artifacts':[artifact]} if artifact else {})}]
        inner={'title':'worker fixture','kind':'feature','acceptance_criteria':['c1'],'fixture':'manual.md','manual_steps':['Run.'],
            'references':{},'checks':checks}
        plan={'registry_version':1,'packs':[],'plan':inner,'program_checks':[]}
        case.request['input_digest']=workflow_store.digest(workflow_preflight.capture_source(case.repo))
        if after_digest: after_digest(case,case.request['input_digest'])
        if checks_factory:
            checks=checks_factory(case,case.request['input_digest'])
            inner['checks']=checks
        case.request['plan_digest']=workflow_store.digest(plan)
        with patch.object(workflow_preflight,'resolve_check_packs',return_value=plan):
            receipt=workflow_submission.submit_submission(store,caller=case.caller,idempotency_key=key,request=case.request,
                context=case.context,artifact_dir=root/'artifacts')
        task_id=store.get_task('task_'+workflow_store.digest({'caller':case.caller,'key':key,'request_digest':workflow_store.digest(case.request)})[:24])
        db=store._connect()
        try: task_id=db.execute('select id from tasks where run_id=?',(receipt['run_id'],)).fetchone()[0]
        finally: db.close()
        import gate_execution
        identity=workflow_native_worker.native_process_identity(os.getpid())
        store.dispatch_task(task_id=task_id,event_id='event_'+key,process_identity=identity,claim_token='token-'+key)
        if changed: (case.repo/'dirty.txt').write_text('drifted')
        if mutate_config_after: (case.repo/'opencode.jsonc').write_text('{"changed":true}')
        result=workflow_native_worker.execute_native_task(store,task_id=task_id,process_identity=identity,claim_token='token-'+key,
            artifact_dir=root/'artifacts',lock_dir=root/'lock')
        return store,result,root

    def test_verified_nonzero_check_commits_actionable_fail_and_releases_resource(self):
        store,result,root=self.run_case('verified-fail',"import sys; sys.exit(2)\n")
        self.assertEqual(result['decision'],'FAIL'); self.assertTrue(result['findings'][0]['required_change'])
        self.assertEqual(store.get_resource_history('native_validation')[-1]['state'],'RELEASED')

    def test_missing_fresh_junit_output_is_error_not_gameplay_fail(self):
        store,result,root=self.run_case('missing-junit',"pass\n",kind='junit')
        self.assertEqual(result['decision'],'ERROR')
        self.assertEqual(store.get_resource_history('native_validation')[-1]['state'],'RELEASED')

    def test_changed_source_commits_error_without_launching_child(self):
        store,result,root=self.run_case('drift-error',"from pathlib import Path; Path('ran').write_text('yes')\n",changed=True)
        self.assertEqual(result['decision'],'ERROR'); self.assertFalse((root/'repo'/'ran').exists())
        self.assertEqual(store.get_resource_history('native_validation')[-1]['state'],'RELEASED')

    def test_fresh_junit_failure_is_fail_fast_and_does_not_launch_package(self):
        script="from pathlib import Path; p=Path('build/test-results/test/TEST-fixture.xml'); p.parent.mkdir(parents=True,exist_ok=True); p.write_text('<testsuite name=\"FixtureTest\" tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\"><testcase name=\"x\"><failure/></testcase></testsuite>')\n"
        marker='build/package-ran.txt'
        checks=[{'argv':[sys.executable,'producer.py'],'rationale':'fixture unit test','covers':['c1'],'kind':'junit','selectors':['FixtureTest']},
                {'argv':[sys.executable,'-c',f"from pathlib import Path; Path('{marker}').write_text('ran')"],
                 'rationale':'sentinel package','covers':['c1'],'kind':'build','artifacts':[marker]}]
        store,result,root=self.run_case('junit-failfast',script,checks=checks)
        self.assertEqual(result['decision'],'FAIL')
        self.assertFalse((root/'repo'/marker).exists())
        self.assertFalse(any(t.get('kind')=='REVIEW' for t in [store.get_task(result.get('task_id',''))] if t))

    def test_resource_release_fault_is_error_and_os_lock_is_released(self):
        with patch.object(workflow_store.WorkflowStore,'release_resource',side_effect=RuntimeError('injected release fault')):
            store,result,root=self.run_case('release-fault','pass\n')
        self.assertEqual(result['decision'],'ERROR')
        self.assertEqual(store.get_resource_history('native_validation')[-1]['state'],'UNKNOWN')
        handle=workflow_runtime.gate_lock(root/'lock',nonblocking=True); handle.close()

    def test_unit_proof_is_externalized_before_package_clean_deletes_xml(self):
        script="from pathlib import Path; p=Path('build/test-results/test/TEST-fixture.xml'); p.parent.mkdir(parents=True,exist_ok=True); p.write_text('<testsuite name=\"FixtureTest\" tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase name=\"x\"/></testsuite>')\n"
        package="import shutil; from pathlib import Path; shutil.rmtree('build/test-results/test'); p=Path('build/output.zip'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'fresh package')"
        checks=[{'argv':[sys.executable,'producer.py'],'rationale':'fixture unit','covers':['c1'],'kind':'junit','selectors':['FixtureTest']},
                {'argv':[sys.executable,'-c',package],'rationale':'fixture clean package','covers':['c1'],'kind':'build','artifacts':['build/output.zip']}]
        store,result,root=self.run_case('clean-retains-unit-proof',script,checks=checks)
        self.assertEqual(result['decision'],'PASS')
        receipt=next(Path(store.get_artifact(ref)['artifact']['path']).read_bytes() for ref in result['artifact_refs']
                     if store.get_artifact(ref)['artifact']['type']=='CHECK_RECEIPT')
        parsed=workflow_store.strict_loads(receipt.decode())
        junit=parsed['checks'][0]['evidence']['artifacts'][0]
        self.assertTrue(Path(junit['path']).is_file())
        self.assertEqual(__import__('hashlib').sha256(Path(junit['path']).read_bytes()).hexdigest(),junit['sha256'])
        self.assertEqual(parsed['checks'][0]['evidence']['counts']['tests'],1)

    def test_initial_gametest_fixture_worker_provenance_is_reusable_as_verified_prefix(self):
        accepted_holder={}
        def after_digest(case,digest):
            old=case.repo/'build'/'workflow-fixtures'/digest[:12]/'lifecycle-profile.json'
            old.parent.mkdir(parents=True,exist_ok=True); old.write_text('{"old":"keep"}',encoding='utf-8')
            accepted_holder['path']=old; accepted_holder['contents']=old.read_bytes()
        def make_checks(case,digest):
            fixture='build/workflow-fixtures/'+digest[:12]
            game_script=("import pathlib,sys\n" 
                "arg=next(x.split('=',1)[1] for x in sys.argv if x.startswith('-Pms14GameTestDir='))\n"
                "p=pathlib.Path(arg); assert not p.exists() or not any(p.iterdir())\n"
                "p.mkdir(parents=True,exist_ok=True); (p/'logs').mkdir(exist_ok=True)\n"
                "(p/'logs/latest.log').write_text('GameTest tag preflight: 1/1 fixtures selected\\nAll 1 required tests passed\\n')\n"
                "print('GameTest tag preflight: 1/1 fixtures selected\\nAll 1 required tests passed')\n"
                "pathlib.Path('build/actual-fixture.txt').write_text(arg)\n")
            missing=case.repo/'build/missing.jar'
            return [
                {'argv':[sys.executable,'-c',"from pathlib import Path; p=Path('build/test-results/test/TEST-Fixture.xml'); p.parent.mkdir(parents=True,exist_ok=True); p.write_text('<testsuite name=\\\"FixtureTest\\\" tests=\\\"1\\\" failures=\\\"0\\\" errors=\\\"0\\\" skipped=\\\"0\\\"><testcase name=\\\"pass\\\"/></testsuite>')"],
                 'rationale':'fresh unit producer','covers':['c1'],'kind':'junit','selectors':['FixtureTest']},
                {'argv':[sys.executable,'-c',game_script,'runGameTestServer','-Pms14GameTestDir='+fixture],
                 'rationale':'managed native GameTest producer','covers':['c1'],'kind':'gametest','log':fixture+'/logs/latest.log'},
                {'argv':[sys.executable,'-c','pass'],'rationale':'missing package artifact creates terminal error',
                 'covers':['c1'],'kind':'build','artifacts':['build/missing.jar']}]
        store,result,root=self.run_case('initial-gametest-prefix','pass\n',checks_factory=make_checks,after_digest=after_digest)
        self.assertEqual('ERROR',result['decision'])
        self.assertEqual(b'{"old":"keep"}',accepted_holder['path'].read_bytes())
        self.assertNotIn('lifecycle-profile.json', (root/'repo'/'build'/'actual-fixture.txt').read_text())
        db=store._connect()
        try: task_id=db.execute('select id from tasks order by rowid desc limit 1').fetchone()[0]
        finally: db.close()
        task=store.get_task(task_id)
        self.assertEqual('ERROR',result['decision'],result)
        self.assertGreaterEqual(len(store.list_native_check_records(task_id)),2,
            [Path(store.get_artifact(ref)['artifact']['path']).read_text(errors='replace')[:700]
             for ref in result.get('artifact_refs',[]) if store.get_artifact(ref)['artifact']['type']=='PROVENANCE'])
        request,bundle=workflow_native_worker.load_native_inputs(store,task['run_id'])
        # Confirm execution ownership fields can be independently re-derived from the SQL task.
        saved_links=store.list_native_check_records(task_id)
        game_art=store.get_artifact(saved_links[1]['artifact_ref'])['artifact']
        game_prov=workflow_store.strict_loads(Path(game_art['path']).read_text(encoding='utf-8'))
        self.assertEqual('FRESH_INITIAL',game_prov['runtime_fixture']['mode'])
        self.assertEqual(task_id,game_prov['task_id'])
        self.assertEqual('PASS',game_prov['record']['evidence']['status'])
        prefix=workflow_error_recovery.verified_prefix(store,task_id,request,bundle)
        self.assertEqual([0,1],[link['check_index'] for link in prefix['source_links']],
            store.list_native_check_records(task_id))
        self.assertEqual('PASS',prefix['records'][1]['evidence']['status'])
        self.assertEqual('FRESH_INITIAL',next(
            workflow_store.strict_loads(Path(store.get_artifact(link['artifact_ref'])['artifact']['path']).read_text())['runtime_fixture']['mode']
            for link in store.list_native_check_records(task_id) if link['check_index']==1))
        # Exercise the runner boundary used by retries: the verified initial argv
        # must be present in the adapted plan so its completed record is reusable.
        retry_task='task_retry_component'
        suffix=workflow_native_fixtures.derive_retry_bindings(bundle['resolved_plan']['plan']['checks'],
            input_digest=request['input_digest'],run_id=task['run_id'],generation=2,
            task_id=retry_task,prefix_count=len(prefix['records']))
        adapted=workflow_native_fixtures.adapt_plan(bundle['resolved_plan']['plan'],
            prefix['runtime_fixtures']+suffix)
        self.assertEqual(prefix['records'][1]['argv'],adapted['checks'][1]['argv'])
        adapted['checks'][2]['argv']=[sys.executable,'-c',
            "from pathlib import Path; p=Path('build/missing.jar'); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(b'now available')"]
        resumed=workflow_runtime.run_finite_checks(root/'repo',adapted,root/'retry-logs',
            completed_prefix=prefix['records'])
        self.assertEqual(3,len(resumed)); self.assertEqual(0,resumed[-1]['exit_code'])
        self.assertEqual('FRESH_INITIAL',prefix['runtime_fixtures'][0]['mode'])

    def test_ignored_managed_config_change_is_execution_error(self):
        store,result,root=self.run_case('ignored-config','from pathlib import Path; Path("ran").write_text("yes")\n',
            ignored_config=True,mutate_config_after=True)
        self.assertEqual(result['decision'],'ERROR')
        self.assertFalse((root/'repo'/'ran').exists())

    def test_same_target_managed_files_do_not_confuse_config_selection(self):
        extras=[{'target':'main','path':f'source-{n}.json','new_sha256':'0'*64} for n in range(4)]
        _,result,_=self.run_case('multi-receipt-files','pass\n',extra_receipt_rows=extras)
        self.assertEqual(result['decision'],'PASS')

    def test_duplicate_exact_config_receipt_row_is_rejected(self):
        store,result,root=self.run_case('duplicate-config-row','pass\n')
        db=store._connect()
        try: run_id=db.execute('select run_id from tasks').fetchone()[0]
        finally: db.close()
        request,bundle=workflow_native_worker.load_native_inputs(store,run_id)
        cp=Path(bundle['config_provenance']['receipt_path'])
        receipt=workflow_store.strict_loads(cp.read_text())
        row=next(r for r in receipt['files'] if r.get('path')=='opencode.jsonc')
        receipt['files'].append(dict(row)); data=workflow_store.canonical_json(receipt).encode(); cp.write_bytes(data)
        bundle['config_provenance']['receipt_sha256']=__import__('hashlib').sha256(data).hexdigest()
        with self.assertRaisesRegex(RuntimeError,'unique non-deleted exact config row'):
            workflow_native_worker._verify_execution_inputs(request,bundle)

    def test_nonzero_compiler_exit_is_fail_fast_without_later_pack(self):
        marker='build/later-package-ran.txt'
        checks=[{'argv':[sys.executable,'-c','import sys; sys.exit(3)'],'rationale':'compile failure','covers':['c1'],'kind':'command'},
                {'argv':[sys.executable,'-c',f"from pathlib import Path; Path('{marker}').write_text('ran')"],
                 'rationale':'later package sentinel','covers':['c1'],'kind':'build','artifacts':[marker]}]
        _,result,root=self.run_case('compiler-failfast','pass\n',checks=checks)
        self.assertEqual(result['decision'],'FAIL')
        self.assertFalse((root/'repo'/marker).exists())


if __name__=='__main__': unittest.main()
