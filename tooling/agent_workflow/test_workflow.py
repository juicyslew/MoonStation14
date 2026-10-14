import base64, importlib.util, json, os, shutil, subprocess, sys, tempfile, unittest, time, io, contextlib
from argparse import Namespace
from pathlib import Path
from unittest import mock

SCRIPT=Path(__file__).with_name('workflow.py').resolve()
spec=importlib.util.spec_from_file_location('agent_workflow',SCRIPT); wf=importlib.util.module_from_spec(spec); spec.loader.exec_module(wf)
PNG=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4n8AAAAASUVORK5CYII=')

class WorkflowTests(unittest.TestCase):
    def setUp(self):
        tempbase=Path.home()/'AppData'/'Local'/'Temp'/'opencode'; tempbase.mkdir(parents=True,exist_ok=True)
        self.tmp=tempfile.TemporaryDirectory(dir=tempbase,prefix='agent-workflow-')
        self.root=Path(self.tmp.name); self.repo=self.root/'repo'; self.repo.mkdir()
        self.git('init','-q'); self.git('config','user.email','fixture@example.invalid'); self.git('config','user.name','fixture')
        (self.repo/'seed.txt').write_text('seed'); (self.repo/'evidence.png').write_bytes(PNG); (self.repo/'.gitignore').write_text('build/\n'); self.git('add','.'); self.git('commit','-qm','seed'); self.git('branch','-M','main'); self.base=self.git('rev-parse','HEAD').decode().strip()
        self.assets=self.root/'assets'; self.assets.mkdir()
        (self.assets/'model.json').write_text('{"model":true}')
        (self.assets/'model.png').write_bytes(PNG)
        (self.assets/'extra.json').write_text('{"dependency":true}')
        (self.assets/'origin.txt').write_text('owned source + provenance')
        self.manifest=self.assets/'export'/'manifest.json'; self.manifest.parent.mkdir()
        self.manifest.write_text(json.dumps({'files':[{'file':'model.json','destination':'assets/moon/models/model.json'},{'file':'model.png','destination':'assets/moon/textures/model.png'},{'file':'extra.json','destination':'assets/moon/data/extra.json'}]}))
        self.request=self.root/'request.json'
        self.request.write_text(json.dumps({'id':'test-1','title':'fixture','brief':'bounded asset','context':['notes/brief.md']}))
        (self.repo/'notes').mkdir(); (self.repo/'notes'/'brief.md').write_text('brief')
        self.image=self.assets/'model.png'

    def tearDown(self): self.tmp.cleanup()
    def git(self,*args): return subprocess.run(['git','-C',str(self.repo),*args],check=True,capture_output=True).stdout
    def cli(self,*args,ok=True,repo=None,override=True):
        cmd=[sys.executable,str(SCRIPT)]
        if override: cmd+=['--asset-workspace',str(self.root)]
        cmd+=['--repo',str(repo or self.repo),*map(str,args)]
        x=subprocess.run(cmd,capture_output=True,text=True)
        if ok and x.returncode: self.fail(x.stderr)
        if not ok and x.returncode==0: self.fail('expected refusal')
        return x
    def create_claim_delivery(self,identifier='test-1'):
        self.cli('request','--from-json',self.request,'--context-root',self.repo)
        self.cli('claim',identifier,'--agent','author')
        self.cli('claim',identifier,'--agent','second-agent',ok=False)
        self.cli('deliver',identifier,'--manifest',self.manifest,'--source-root',self.assets,'--revision','r1','--provenance','origin.txt','--editable','model.json','--review-images','model.png')
        return json.loads((self.root/'work/agent_exchange/requests'/identifier/'request.json').read_text())['delivery']
    def report(self,identifier,digest,reviewer='reviewer'):
        saved=json.loads((self.root/'work/agent_exchange/requests'/identifier/'request.json').read_text())
        p=self.assets/'report.json'; p.write_text(json.dumps({'request_id':identifier,'snapshot_key':saved['snapshot_key'],'delivery_digest':digest,'reviewer':reviewer,'decision':'ready_for_owner','findings':['shape and source checked'],'viewed_images':[{'path':'model.png','sha256':wf.sha(self.image)}]})); return p
    def ready(self,delivery,identifier='test-1'):
        report=self.report(identifier,delivery['digest'])
        self.cli('review',identifier,'--reviewer','reviewer','--result','ready_for_owner','--report',report)
        return report

    def test_default_queue_root_main_and_linked_worktree_readonly_status(self):
        home=self.root/'layout'; main=home/'MoonStation14'; main.mkdir(parents=True)
        subprocess.run(['git','init','-q',str(main)],check=True); subprocess.run(['git','-C',str(main),'config','user.email','fixture@example.invalid'],check=True); subprocess.run(['git','-C',str(main),'config','user.name','fixture'],check=True)
        (main/'seed').write_text('x'); subprocess.run(['git','-C',str(main),'add','.'],check=True); subprocess.run(['git','-C',str(main),'commit','-qm','seed'],check=True)
        linked=home/'MoonStation14-linked'; subprocess.run(['git','-C',str(main),'worktree','add','-q','-b','linked',str(linked)],check=True)
        workspace=home/'Asset Building Workspace'; workspace.mkdir(); (workspace/'marker').write_text('existing sibling workspace')
        expected=workspace/'work'/'agent_exchange'; self.assertFalse(expected.exists())
        for repo in (main,linked):
            self.assertEqual(wf.queue(type('A',(),{'asset_workspace':None,'repo':str(repo)})()),expected)
            out=self.cli('status',repo=repo,override=False)
            self.assertIn('No agent exchange',out.stdout); self.assertFalse(expected.exists())

    def test_safe_id_and_all_context_validation_before_directory_creation(self):
        bad=self.root/'bad.json'; bad.write_text(json.dumps({'id':'../escape','brief':'x','context':[]}))
        self.cli('request','--from-json',bad,'--context-root',self.repo,ok=False)
        self.assertFalse((self.root/'work/agent_exchange/requests').exists())
        self.request.write_text(json.dumps({'id':'retry-1','brief':'x','context':['notes/brief.md','missing']}))
        self.cli('request','--from-json',self.request,'--context-root',self.repo,ok=False)
        self.assertFalse((self.root/'work/agent_exchange/requests/retry-1').exists())
        self.request.write_text(json.dumps({'id':'retry-1','brief':'x','context':['notes/brief.md']}))
        self.cli('request','--from-json',self.request,'--context-root',self.repo)
        saved=self.root/'work/agent_exchange/requests/retry-1/context/notes/brief.md'
        self.assertTrue(saved.is_file())
        self.cli('submit','../escape','--agent','author','--build-identity','test','--steps','blocked','--base-ref','main',ok=False)
        self.cli('candidate-review','../escape','--reviewer','x','--report','x','--result','needs_changes',ok=False)

    def test_duplicate_request_id_refused(self):
        self.cli('request','--from-json',self.request,'--context-root',self.repo)
        self.cli('request','--from-json',self.request,'--context-root',self.repo,ok=False)

    def test_complete_deliver_review_receipt_plan_and_stale_target_protection(self):
        d=self.create_claim_delivery()
        folder=self.root/'work/agent_exchange/requests/test-1'
        self.assertEqual(len(d['files']),3)
        # Complete group is immutable, no cherry-pick/re-delivery.
        self.cli('deliver','test-1','--manifest',self.manifest,'--source-root',self.assets,'--revision','r2','--provenance','origin.txt','--editable','model.json','--review-images','model.png',ok=False)
        target=self.root/'target'; plan=self.root/'plan.json'; plan2=self.root/'plan2.json'
        # Human can preview a pending/unreviewed model before approval.
        self.cli('import-assets','test-1','--target',target,'--plan-out',plan)
        self.assertFalse(target.exists())
        (target/'assets/moon/models').mkdir(parents=True); shutil.copyfile(self.assets/'model.json',target/'assets/moon/models/model.json')
        self.cli('import-assets','test-1','--target',target)
        (target/'assets/moon/models/model.json').unlink()
        self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan2)
        (target/'assets/moon/models/model.json').parent.mkdir(parents=True,exist_ok=True)
        (target/'assets/moon/models/model.json').write_text('manual edit')
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply',ok=False)
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan2,'--allow-overwrite',ok=False)
        plan2=self.root/'plan-overwrite.json'
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan2,'--allow-overwrite')
        (target/'assets/moon/models/model.json').write_text('changed after plan')
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan2,'--apply','--allow-overwrite',ok=False)
        # owner acceptance for an older delivery cannot accept changed delivery digest.
        wrong=self.root/'wrong.json'; wrong.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':'0'*64}))
        self.cli('import-assets','test-1','--target',self.root/'other','--owner-receipt',wrong,'--apply',ok=False)

    def test_owner_receipt_hash_binds_apply_plan(self):
        d=self.create_claim_delivery(); self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        target=self.root/'target'; preview=self.root/'preview.json'; postaccept=self.root/'postaccept.json'
        self.cli('import-assets','test-1','--target',target,'--plan-out',preview)
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',postaccept)
        receipt.write_text(receipt.read_text()+' ')
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',postaccept,'--apply',ok=False)
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',preview,'--apply',ok=False)
        fresh=self.root/'fresh.json'; self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',fresh)
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',fresh,'--apply')

    def test_successful_asset_apply_and_unrelated_file_preservation(self):
        d=self.create_claim_delivery(); self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        target=self.root/'target'; target.mkdir(); sentinel=target/'manual.txt'; sentinel.write_text('preserve')
        (target/'assets/moon/models').mkdir(parents=True); shutil.copyfile(self.assets/'model.json',target/'assets/moon/models/model.json')
        plan=self.root/'apply-plan.json'; self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan)
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply')
        self.assertEqual((target/'assets/moon/models/model.json').read_bytes(),(self.assets/'model.json').read_bytes())
        self.assertEqual((target/'assets/moon/textures/model.png').read_bytes(),PNG)
        self.assertEqual((target/'assets/moon/data/extra.json').read_bytes(),(self.assets/'extra.json').read_bytes())
        self.assertEqual(sentinel.read_text(),'preserve')
        rec=json.loads((self.root/'work/agent_exchange/requests/test-1/import_receipt.json').read_text())
        self.assertEqual(rec['result'],'complete'); self.assertEqual(rec['paths'][0]['status'],'unchanged')
        self.assertEqual(json.loads((self.root/'work/agent_exchange/requests/test-1/request.json').read_text())['status'],'imported')

    def test_destination_directory_and_parent_file_block_planning_and_apply_preflight(self):
        d=self.create_claim_delivery(); self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        destinations=['assets/moon/models/model.json','assets/moon/textures/model.png','assets/moon/models/model.json']
        kinds=['directory','directory','parent-file']

        def obstruct(target,destination,kind):
            dest=target/destination
            if kind=='directory': dest.parent.mkdir(parents=True,exist_ok=True); dest.mkdir()
            else: dest.parent.parent.mkdir(parents=True,exist_ok=True); dest.parent.write_text('blocking file')

        # Read-only manifest planning checks every path, including a later row,
        # before reporting any candidate copy actions.
        for i,(dest,kind) in enumerate(zip(destinations,kinds)):
            target=self.root/f'planning-{i}'; obstruct(target,dest,kind)
            self.cli('plan-manifest','--manifest',self.manifest,'--source-root',self.assets,'--target',target,ok=False)

        # A clean plan does not license a path that became a directory/file later.
        for i,(dest,kind) in enumerate(zip(destinations,kinds)):
            target=self.root/f'apply-{i}'; plan=self.root/f'apply-plan-{i}.json'
            self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan)
            obstruct(target,dest,kind)
            self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply',ok=False)
            self.assertFalse((target/'assets/moon/textures/model.png').is_file())
            self.assertFalse((target/'assets/moon/data/extra.json').exists())
            self.assertFalse((self.root/'work/agent_exchange/requests/test-1/import_attempt.json').exists())

    def test_partial_os_copy_failure_receipt_truthfully_records_paths(self):
        d=self.create_claim_delivery(); self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        target=self.root/'target'; plan=self.root/'plan.json'; self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan)
        original=shutil.copyfile
        def flaky(src,dst,*args,**kwargs):
            if Path(dst).as_posix().endswith('textures/model.png'): raise OSError('fixture disk failure')
            return original(src,dst,*args,**kwargs)
        ns=Namespace(id='test-1',apply=True,owner_receipt=str(receipt),target=str(target),plan=str(plan),plan_out=None,allow_overwrite=False)
        with mock.patch.object(wf.shutil,'copyfile',side_effect=flaky):
            with self.assertRaisesRegex(ValueError,'no rollback'):
                wf.cmd_import(ns,self.root/'work/agent_exchange')
        folder=self.root/'work/agent_exchange/requests/test-1'
        attempt=json.loads((folder/'import_attempt.json').read_text())
        self.assertEqual([x['status'] for x in attempt['paths']],['copied','failed','not_attempted'])
        self.assertFalse((folder/'import_receipt.json').exists())
        self.assertEqual(json.loads((folder/'request.json').read_text())['status'],'import_failed')
        self.assertTrue((target/'assets/moon/models/model.json').is_file())

    def test_manifest_rejects_partial_null_duplicate_traversal_missing_and_nested(self):
        cases=[{'files':[{'file':'model.json'}]}, {'files':[{'file':'model.json','destination':'x'},{'file':'model.png','destination':'X'}]}, {'files':[{'file':'model.json','destination':'x'},{'file':'model.json','destination':'y'}]}, {'files':[{'file':'model.json','destination':'../x'}]}, {'files':[{'file':'../oops','destination':'x'}]}, {'files':[{'file':'missing','destination':'x'}]}, {'files':[{'file':None,'destination':'x'}]}, {'items':[{'source':'model.json','destination':None}]}, {'files':[{'file':'model.json','destination':'x','status':'MTL remap required'}]}]
        for i,m in enumerate(cases):
            p=self.assets/f'bad{i}.json'; p.write_text(json.dumps(m))
            self.cli('plan-manifest','--manifest',p,'--source-root',self.assets,ok=False)
        # no partial/cherry-pick option exists
        self.assertNotIn('--files',[x for x in self.cli('--help').stdout.split()])

    def test_source_manifest_source_copy_and_metadata_staleness_require_new_revision(self):
        d=self.create_claim_delivery(); self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        (self.assets/'model.json').write_text('updated source')
        self.cli('import-assets','test-1','--target',self.root/'target','--owner-receipt',receipt,ok=False)
        self.assertTrue((self.root/'work/agent_exchange/requests/test-1/delivery/source_manifest.json').exists())

    def test_manifest_metadata_copy_and_review_images_all_rechecked(self):
        d=self.create_claim_delivery(); report=self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        target=self.root/'target'; plan=self.root/'plan.json'
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan)
        self.image.write_bytes(PNG+b'changed')
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply',ok=False)
        self.image.write_bytes(PNG)
        self.cli('review','test-1','--reviewer','reviewer','--result','ready_for_owner','--report',report)
        copied=self.root/'work/agent_exchange/requests/test-1/delivery/files/assets/moon/models/model.json'
        copied.write_text('changed handoff')
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply',ok=False)
        copied.write_text((self.assets/'model.json').read_text())
        self.assets.joinpath('origin.txt').write_text('updated provenance')
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply',ok=False)

        # Distinct new queue entry validates changed current manifest vs captured revision.
        request2=self.root/'request2.json'; request2.write_text(json.dumps({'id':'test-2','brief':'new sample','context':[]}))
        self.cli('request','--from-json',request2,'--context-root',self.repo); self.cli('claim','test-2','--agent','author')
        self.assets.joinpath('origin.txt').write_text('owned source + provenance')
        self.cli('deliver','test-2','--manifest',self.manifest,'--source-root',self.assets,'--revision','r1','--provenance','origin.txt','--editable','model.json','--review-images','model.png')
        # Edit original manifest after delivery; a previously valid source export is now stale.
        self.manifest.write_text(self.manifest.read_text()+' ')
        self.cli('import-assets','test-2','--target',target,ok=False)

    def test_review_report_and_real_png_requirements_and_independent_reviewer(self):
        d=self.create_claim_delivery()
        report=self.report('test-1',d['digest'])
        self.cli('review','test-1','--reviewer','author','--result','ready_for_owner','--report',report,ok=False)
        saved=json.loads((self.root/'work/agent_exchange/requests/test-1/request.json').read_text())
        bad=self.assets/'bad.png'; bad.write_bytes(b'fake PNG'); report.write_text(json.dumps({'request_id':'test-1','snapshot_key':saved['snapshot_key'],'delivery_digest':d['digest'],'reviewer':'reviewer','decision':'ready_for_owner','findings':['looks good'],'viewed_images':[{'path':'bad.png','sha256':wf.sha(bad)}]}))
        self.cli('review','test-1','--reviewer','reviewer','--result','ready_for_owner','--report',report,ok=False)
        report.write_text(json.dumps({'request_id':'test-1','snapshot_key':saved['snapshot_key'],'delivery_digest':'stale','reviewer':'reviewer','decision':'ready_for_owner','findings':['x'],'viewed_images':[{'path':'model.png','sha256':wf.sha(self.image)}]}))
        self.cli('review','test-1','--reviewer','reviewer','--result','ready_for_owner','--report',report,ok=False)

    def test_candidate_status_binary_paths_deletions_and_staleness(self):
        # Committed changes from baseline plus dirty/untracked/deleted paths with spaces.
        (self.repo/'committed path.txt').write_text('committed'); self.git('add','.'); self.git('commit','-qm','candidate commit')
        (self.repo/'dirty name.txt').write_text('dirty'); (self.repo/'untracked x.bin').write_bytes(b'abc')
        self.git('mv','seed.txt','renamed seed.txt')
        self.cli('submit','candidate-1','--agent','author','--base-ref',self.base,'--build-identity','python test fixture','--steps','not run; fixture','--unverified','Minecraft not run','--evidence','evidence.png')
        saved=json.loads((self.root/'work/agent_exchange/candidates/candidate-1.json').read_text())
        self.assertTrue(saved['committed_changes']); self.assertEqual(len(saved['dirty_changes']),3)
        report=self.assets/'candidate-review.json'; report.write_text(json.dumps({'candidate_id':'candidate-1','snapshot_key':saved['snapshot_key'],'reviewer':'reviewer','decision':'ready_for_owner','findings':['files captured'],'viewed_images':[{'path':'model.png','sha256':wf.sha(self.image)}]}))
        self.cli('candidate-review','candidate-1','--reviewer','author','--report',report,'--result','ready_for_owner',ok=False)
        self.cli('candidate-review','candidate-1','--reviewer','reviewer','--report',report,'--result','ready_for_owner')
        (self.repo/'dirty name.txt').write_text('changed after submit')
        self.cli('candidate-review','candidate-1','--reviewer','reviewer','--report',report,'--result','ready_for_owner',ok=False)
        status=self.cli('status').stdout; self.assertIn('code candidate-1: STALE/BLOCKED',status)

    def test_gate_plan_validation_and_runner_records_exact_check(self):
        plan={'title':'Gate fixture','kind':'bugfix','acceptance_criteria':['exit recorded'],'fixture':'seed.txt','manual_steps':['Inspect fixture'],'checks':[{'argv':[sys.executable,'-c','print("gate-ok")'],'rationale':'focused fixture check','covers':['exit recorded']}]}
        planfile=self.root/'gate.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','gate-1','--agent','author','--base-ref',self.base,'--build-identity','python fixture','--steps','manual fixture','--unverified','game runtime not tested','--gate-plan',planfile)
        self.cli('gate-preflight','gate-1'); self.cli('gate-validate','gate-1','--actor','integration-lead')
        report=json.loads((self.root/'work/agent_exchange/gate-reports/gate-1.json').read_text())
        self.assertTrue(report['passed']); self.assertEqual(report['checks'][0]['exit_code'],0)
        self.assertIn('gate-ok',(self.root/'work/agent_exchange'/report['checks'][0]['log']).read_text())

    def test_gate_to_manual_log_and_explicit_owner_disposition(self):
        plan={'title':'Full gate cycle','kind':'bugfix','acceptance_criteria':['unit passes'],'fixture':'seed.txt','manual_steps':['Check behavior','Confirm expected result'],'checks':[{'argv':[sys.executable,'-c','print("ok")'],'rationale':'focused fake check','covers':['unit passes']}]}
        planfile=self.root/'cycle.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','cycle-1','--agent','author','--base-ref',self.base,'--build-identity','fixture build','--steps','manual','--unverified','runtime not exercised','--gate-plan',planfile)
        cpath=self.root/'work/agent_exchange/candidates/cycle-1.json'; c=json.loads(cpath.read_text())
        review=self.assets/'cycle-review.json'; data={'candidate_id':'cycle-1','snapshot_key':c['snapshot_key'],'reviewer':'lead','decision':'ready_for_owner','findings':['manual owner check remains'],'viewed_images':[]}; review.write_text(json.dumps(data))
        self.cli('candidate-review','cycle-1','--reviewer','lead','--report',review,'--result','ready_for_owner')
        self.cli('gate-validate','cycle-1','--actor','integration-lead')
        with mock.patch.object(wf,'native_notify',return_value='delivered_to_os; user visibility is not guaranteed') as notify:
            wf.cmd_gate_finish(Namespace(id='cycle-1',result='ready_for_owner',report=str(review)),self.root/'work/agent_exchange')
            self.cli('gate-finish','cycle-1','--result','ready_for_owner','--report',review)
        notify.assert_called_once()
        index=(self.root/'work/agent_exchange/manual-tests.md').read_text()
        self.assertIn('cycle-1 (open)',index); self.assertIn('fixture build',index); self.assertIn(str(self.repo),index)
        self.assertIsNone(json.loads(cpath.read_text()).get('owner_decision'))
        receipt=self.root/'cycle-owner.json'; receipt.write_text(json.dumps({'candidate_id':'cycle-1','snapshot_key':c['snapshot_key'],'decision':'accepted','owner_reference':'explicit fixture owner','note':'record only'}))
        self.cli('candidate-decision','cycle-1','--owner-receipt',receipt)
        self.assertIn('cycle-1 (owner_accepted)',(self.root/'work/agent_exchange/manual-tests.md').read_text())

    def test_shared_gate_mutex_serializes_processes(self):
        queue=self.root/'shared-exchange'; script=("import importlib.util,time,pathlib; "+
            f"s=importlib.util.spec_from_file_location('wf',r'{SCRIPT}'); w=importlib.util.module_from_spec(s); s.loader.exec_module(w); "+
            f"l=w.gate_lock(pathlib.Path(r'{queue}')); time.sleep(.45); l.close()")
        start=time.monotonic()
        a=subprocess.Popen([sys.executable,'-c',script]); b=subprocess.Popen([sys.executable,'-c',script])
        self.assertEqual(a.wait(timeout=10),0); self.assertEqual(b.wait(timeout=10),0)
        self.assertGreater(time.monotonic()-start,.8)

    def test_timeout_kills_only_command_process_tree_and_releases_gate(self):
        marker=self.root/'late-marker'
        child="import time,pathlib; time.sleep(2); pathlib.Path(r'%s').write_text('late')"%marker
        script="import subprocess,sys; subprocess.Popen([sys.executable,'-c',%r]); import time; time.sleep(20)"%child
        plan={'title':'Timeout fixture','kind':'bugfix','acceptance_criteria':['bounded timeout'],'fixture':'seed.txt','manual_steps':['Inspect result'],'checks':[{'argv':[sys.executable,'-c',script],'rationale':'timeout process-tree fixture','covers':['bounded timeout']}]}
        planfile=self.root/'timeout.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','timeout-1','--agent','author','--base-ref',self.base,'--build-identity','process fixture','--steps','timeout','--unverified','runtime not tested','--gate-plan',planfile)
        self.cli('gate-validate','timeout-1','--actor','integration-lead','--timeout','1',ok=False)
        report=json.loads((self.root/'work/agent_exchange/gate-reports/timeout-1.json').read_text())
        self.assertEqual(report['checks'][0]['exit_code'],124)
        import time; time.sleep(2.2); self.assertFalse(marker.exists())
        lock=wf.gate_lock(self.root/'work/agent_exchange'); lock.close()

    def test_unverified_tree_cleanup_quarantines_gate_until_explicit_lead_confirmation(self):
        plan={'title':'Quarantine fixture','kind':'bugfix','acceptance_criteria':['bounded cleanup'],'fixture':'seed.txt','manual_steps':['Inspect blocker'],'checks':[{'argv':[sys.executable,'-c','pass'],'rationale':'bounded process stop','covers':['bounded cleanup']}]}
        planfile=self.root/'blocked.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','blocked-1','--agent','author','--base-ref',self.base,'--build-identity','process fixture','--steps','cleanup','--unverified','not runtime','--gate-plan',planfile)
        child=mock.Mock(pid=54321,wait=mock.Mock(side_effect=[subprocess.TimeoutExpired('child',1),subprocess.TimeoutExpired('child',1)]))
        root=self.root/'work/agent_exchange'
        with mock.patch.object(wf,'verify_gate',return_value=(self.repo,plan)), mock.patch.object(wf.subprocess,'Popen',return_value=child), mock.patch.object(wf,'stop_owned_tree',side_effect=wf.GateTreeBlocked(54321,'taskkill timeout/failure')):
            with self.assertRaisesRegex(ValueError,'gate checks failed'):
                wf.cmd_gate_validate(Namespace(id='blocked-1',actor='integration-lead',timeout=1),root)
        blocker=json.loads((root/'gate-blocker.json').read_text())
        self.assertEqual(blocker['pid'],54321)
        self.assertEqual(json.loads((root/'gate-reports/blocked-1.json').read_text())['checks'][0]['tree_cleanup'],'unverified')
        with self.assertRaisesRegex(ValueError,'gate quarantined'):
            wf.cmd_gate_validate(Namespace(id='blocked-1',actor='integration-lead',timeout=1),root)
        with self.assertRaisesRegex(ValueError,'PID does not match'):
            wf.cmd_gate_clear_blocker(Namespace(actor='integration-lead',pid=12,confirmed_stopped=True),root)
        wf.cmd_gate_clear_blocker(Namespace(actor='integration-lead',pid=54321,confirmed_stopped=True),root)
        self.assertFalse((root/'gate-blocker.json').exists())

    def test_taskkill_failure_is_bounded_and_never_claims_tree_stopped(self):
        proc=mock.Mock(pid=999,wait=mock.Mock())
        failed=subprocess.CompletedProcess(['taskkill'],1)
        with mock.patch.object(wf.subprocess,'run',return_value=failed) as run:
            with self.assertRaises(wf.GateTreeBlocked): wf.stop_owned_tree(proc,timeout=2)
        run.assert_called_once(); self.assertEqual(run.call_args.kwargs['timeout'],2); proc.wait.assert_not_called()

    def test_original_visual_binding_tracks_source_and_dispatch_status_exit_codes(self):
        source=self.repo/'evidence.png'; candidate={'worktree':{'worktree':str(self.repo)},'evidence':[{'path':str(source),'sha256':wf.sha(source)}]}
        record={'path':'evidence.png','sha256':wf.sha(source)}
        self.assertEqual(wf.verify_original_images([record],candidate)[0]['path'],str(source.resolve()))
        reports=self.root/'reports'; reports.mkdir(); (reports/'image.png').write_bytes(source.read_bytes())
        copied={'path':'image.png','sha256':wf.sha(source),'original_path':str(source),'original_sha256':wf.sha(source)}
        self.assertEqual(wf.verify_original_images([copied],candidate,reports)[0]['sha256'],wf.sha(source))
        source.write_bytes(PNG+b'stale')
        with self.assertRaisesRegex(ValueError,'stale'): wf.verify_original_images([copied],candidate,reports)

        from tooling.agent_workflow import gate_dispatch
        args=Namespace(id='code-1',central_worktree=None,origin_session=None,server=None,wait_timeout=10)
        output=io.StringIO()
        with mock.patch.object(gate_dispatch,'request_gate',return_value={'status':'needs_changes','candidate_id':'code-1','session_id':'s','detail':['fix']}) as call, contextlib.redirect_stdout(output):
            with self.assertRaises(wf.WorkflowExit) as exc: wf.cmd_gate_request(args,self.root/'queue')
        self.assertEqual(exc.exception.code,3); call.assert_called_once()
        self.assertIn('needs_changes',output.getvalue())

    def test_launch_smoke_matches_real_marker_controls_owned_tree_and_hashes_startup_scope(self):
        marker=self.root/'smoke-child-marker'
        child="import time,pathlib; time.sleep(1.5); pathlib.Path(r'%s').write_text('alive')"%marker
        command="import subprocess,sys,time; print('HTTP READY: port=8080',flush=True); subprocess.Popen([sys.executable,'-c',%r]); time.sleep(20)"%child
        check={'kind':'launch-smoke','argv':[sys.executable,'-u','-c',command],'rationale':'bounded synthetic startup probe','covers':['startup marker'],'log':'build/gate-smoke.log','ready_patterns':['HTTP READY: port=\\d+'],'error_patterns':['FATAL: startup'],'timeout':10}
        plan={'title':'Synthetic launch smoke','kind':'bugfix','acceptance_criteria':['startup marker'],'fixture':'seed.txt','manual_steps':['Review startup-only evidence'],'checks':[check]}
        planfile=self.root/'smoke.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','smoke-1','--agent','author','--base-ref',self.base,'--build-identity','python fixture only','--steps','not Minecraft','--unverified','No GPU, real client input, or gameplay consumer was tested','--gate-plan',planfile)
        self.cli('gate-validate','smoke-1','--actor','integration-lead','--timeout','10')
        report=json.loads((self.root/'work/agent_exchange/gate-reports/smoke-1.json').read_text())
        item=report['checks'][0]
        self.assertTrue(report['passed']); self.assertTrue(item['controlled_stop']); self.assertEqual(item['evidence']['status'],'PASS')
        self.assertIn('HTTP READY: port=8080',(self.repo/'build/gate-smoke.log').read_text())
        time.sleep(1.8); self.assertFalse(marker.exists())

    def test_launch_smoke_exit_zero_without_marker_is_not_readiness(self):
        check={'kind':'launch-smoke','argv':[sys.executable,'-c','print("startup incomplete")'],'rationale':'no readiness marker fixture','covers':['startup marker'],'log':'build/missing-smoke.log','ready_patterns':['HTTP READY'],'timeout':5}
        plan={'title':'Missing marker','kind':'bugfix','acceptance_criteria':['startup marker'],'fixture':'seed.txt','manual_steps':['Inspect failure'],'checks':[check]}
        path=self.root/'missing-smoke.json'; path.write_text(json.dumps(plan))
        self.cli('submit','smoke-missing','--agent','author','--base-ref',self.base,'--build-identity','fixture','--steps','check missing marker','--unverified','runtime not tested','--gate-plan',path)
        self.cli('gate-validate','smoke-missing','--actor','integration-lead',ok=False)
        report=json.loads((self.root/'work/agent_exchange/gate-reports/smoke-missing.json').read_text())
        self.assertFalse(report['passed']); self.assertEqual(report['checks'][0]['exit_code'],1)
        self.assertEqual(report['checks'][0]['evidence']['status'],'FAIL')

    def test_nonvisual_candidate_review_no_fake_image_and_needs_changes(self):
        self.cli('submit','code-1','--agent','author','--base-ref',self.base,'--build-identity','stdlib test','--steps','python -m unittest','--unverified','Minecraft not run')
        saved=json.loads((self.root/'work/agent_exchange/candidates/code-1.json').read_text())
        report=self.assets/'nonvisual.json'; report.write_text(json.dumps({'candidate_id':'code-1','snapshot_key':saved['snapshot_key'],'reviewer':'reviewer','decision':'ready_for_owner','findings':['CLI behavior covered'],'viewed_images':[]}))
        self.cli('candidate-review','code-1','--reviewer','reviewer','--report',report,'--result','ready_for_owner')
        self.assertIn('code code-1: ready_for_owner',self.cli('status').stdout)
        stable=report.read_text(); report.write_text(stable+' ')
        self.assertIn('code code-1: STALE/BLOCKED',self.cli('status').stdout)
        report.write_text(stable)
        self.assertIn('code code-1: ready_for_owner',self.cli('status').stdout)
        owner=self.root/'owner-decision.json'; ownerdata={'candidate_id':'code-1','snapshot_key':saved['snapshot_key'],'decision':'accepted','owner_reference':'William explicitly accepted this candidate','note':'Owner acceptance only; not a test result.'}; owner.write_text(json.dumps(ownerdata))
        self.cli('candidate-decision','code-1','--owner-receipt',owner)
        self.assertIn('code code-1: owner_accepted',self.cli('status').stdout)
        owner_bytes=owner.read_bytes(); owner.write_text(owner.read_text()+' ')
        self.assertIn('code code-1: STALE/BLOCKED (owner_accepted recorded)',self.cli('status').stdout)
        owner.write_bytes(owner_bytes)

        self.cli('submit','code-evidence','--agent','author','--base-ref',self.base,'--build-identity','stdlib test','--steps','not run','--unverified','runtime','--evidence','evidence.png')
        evidence_record=json.loads((self.root/'work/agent_exchange/candidates/code-evidence.json').read_text())
        evidence_report=self.assets/'evidence-review.json'; evidence_report.write_text(json.dumps({'candidate_id':'code-evidence','snapshot_key':evidence_record['snapshot_key'],'reviewer':'reviewer','decision':'ready_for_owner','findings':['evidence checked'],'viewed_images':[]}))
        self.cli('candidate-review','code-evidence','--reviewer','reviewer','--report',evidence_report,'--result','ready_for_owner')
        self.assertIn('code code-evidence: ready_for_owner',self.cli('status').stdout)
        (self.repo/'evidence.png').write_bytes(PNG+b'changed')
        self.assertIn('code code-evidence: STALE/BLOCKED',self.cli('status').stdout)
        self.assertIn('code code-1: STALE/BLOCKED (owner_accepted recorded)',self.cli('status').stdout)

        self.cli('submit','code-needs-changes','--agent','author','--base-ref',self.base,'--build-identity','stdlib test','--steps','not run','--unverified','runtime')
        needs=json.loads((self.root/'work/agent_exchange/candidates/code-needs-changes.json').read_text())
        needs_report=self.assets/'needs-changes.json'; needs_report.write_text(json.dumps({'candidate_id':'code-needs-changes','snapshot_key':needs['snapshot_key'],'reviewer':'reviewer','decision':'needs_changes','findings':['attach diagnostic artifacts'],'viewed_images':[]}))
        self.cli('candidate-review','code-needs-changes','--reviewer','reviewer','--report',needs_report,'--result','needs_changes')
        self.assertIn('code code-needs-changes: needs_changes',self.cli('status').stdout)
        needs_owner=self.root/'needs-owner.json'; needs_owner.write_text(json.dumps({'candidate_id':'code-needs-changes','snapshot_key':needs['snapshot_key'],'decision':'needs_changes','owner_reference':'William requested revisions','note':'No image requested for this code-only item.'}))
        self.cli('candidate-decision','code-needs-changes','--owner-receipt',needs_owner)
        self.assertIn('code code-needs-changes: owner_needs_changes',self.cli('status').stdout)

        visual=self.root/'visual'; visual.mkdir(); visual_repo=visual/'repo'
        self.git('worktree','add','-q','-b','visual',str(visual_repo))
        self.cli('submit','visual-1','--agent','author','--base-ref',self.base,'--build-identity','visual fixture','--steps','inspect PNG','--unverified','runtime','--visual-review-required',repo=visual_repo)
        visual_record=json.loads((self.root/'work/agent_exchange/candidates/visual-1.json').read_text())
        vr=self.assets/'visual.json'; vr.write_text(json.dumps({'candidate_id':'visual-1','snapshot_key':visual_record['snapshot_key'],'reviewer':'reviewer','decision':'ready_for_owner','findings':['no image yet'],'viewed_images':[]}))
        self.cli('candidate-review','visual-1','--reviewer','reviewer','--report',vr,'--result','ready_for_owner',ok=False)
        vr.write_text(json.dumps({'candidate_id':'visual-1','snapshot_key':visual_record['snapshot_key'],'reviewer':'reviewer','decision':'needs_changes','findings':['please attach source view'],'viewed_images':[]}))
        self.cli('candidate-review','visual-1','--reviewer','reviewer','--report',vr,'--result','needs_changes',repo=visual_repo)
        self.assertIn('code visual-1: needs_changes',self.cli('status').stdout)

    def test_owner_decision_without_ready_review_refuses_acceptance_but_allows_defer(self):
        self.cli('submit','unreviewed','--agent','author','--base-ref',self.base,'--build-identity','stdlib test','--steps','not run','--unverified','visual review not performed')
        c=json.loads((self.root/'work/agent_exchange/candidates/unreviewed.json').read_text())
        receipt=self.root/'unreviewed-owner.json'; data={'candidate_id':'unreviewed','snapshot_key':c['snapshot_key'],'decision':'accepted','owner_reference':'explicit owner instruction','note':'Do not accept without review'}; receipt.write_text(json.dumps(data))
        self.cli('candidate-decision','unreviewed','--owner-receipt',receipt,ok=False)
        data.update(decision='deferred',note='Wait for independent review'); receipt.write_text(json.dumps(data))
        self.cli('candidate-decision','unreviewed','--owner-receipt',receipt)
        self.assertIn('code unreviewed: owner_deferred',self.cli('status').stdout)
        data['snapshot_key']='0'*64; receipt.write_text(json.dumps(data))
        self.cli('candidate-decision','unreviewed','--owner-receipt',receipt,ok=False)
        self.assertIn('code unreviewed: STALE/BLOCKED (owner_deferred recorded)',self.cli('status').stdout)


if __name__=='__main__': unittest.main()
