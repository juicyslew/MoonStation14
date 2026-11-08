import base64, importlib.util, json, os, shutil, subprocess, sys, tempfile, unittest, time, io, contextlib, re
from argparse import Namespace
from pathlib import Path
from unittest import mock

SCRIPT=Path(__file__).with_name('workflow_runtime.py').resolve()
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
    def test_git_helper_runs_real_windows_git_silently_and_bounds_hangs(self):
        # Exercise the exact helper used by submit/status against the real Git binary.
        self.assertEqual(wf.git(self.repo,'rev-parse','HEAD').decode().strip(),self.base)
        real_popen=wf.subprocess.Popen
        seen=[]
        def launch(*args,**kwargs):
            seen.append(kwargs)
            return real_popen(*args,**kwargs)
        with mock.patch.object(wf.subprocess,'Popen',side_effect=launch):
            self.assertEqual(wf.git(self.repo,'branch','--show-current').decode().strip(),'main')
        self.assertIs(seen[0]['stdin'],subprocess.DEVNULL)
        self.assertTrue(seen[0]['creationflags'] & getattr(subprocess,'CREATE_NO_WINDOW',0))

    def test_git_helper_accepts_relative_nested_repository_path(self):
        relative=Path(os.path.relpath(self.repo,Path.cwd()))
        self.assertFalse(relative.is_absolute())
        self.assertEqual(wf.git(relative,'rev-parse','HEAD').decode().strip(),self.base)

    def test_git_timeout_stops_owned_process_before_returning(self):
        child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(30)'],stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,
            creationflags=(getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0)|getattr(subprocess,'CREATE_NO_WINDOW',0)) if os.name=='nt' else 0,
            start_new_session=os.name!='nt')
        proc=mock.Mock(pid=child.pid,wait=child.wait,args=['git'])
        proc.communicate.side_effect=subprocess.TimeoutExpired(['git'],0)
        with mock.patch.object(wf.subprocess,'Popen',return_value=proc),mock.patch.object(wf,'GIT_TIMEOUT_SECONDS',0), \
             mock.patch.object(wf,'stop_owned_tree',side_effect=lambda p: (child.kill(),child.wait())) as stop:
            with self.assertRaisesRegex(RuntimeError,'process tree was stopped'):
                wf.git(self.repo,'status')
        stop.assert_called_once_with(proc)
        self.assertIsNotNone(child.poll())

    def test_git_timeout_cleanup_failure_is_typed_unverified(self):
        proc=mock.Mock(pid=4321,args=['git'])
        proc.communicate.side_effect=subprocess.TimeoutExpired(['git'],0)
        with mock.patch.object(wf.subprocess,'Popen',return_value=proc),mock.patch.object(wf,'GIT_TIMEOUT_SECONDS',0), \
             mock.patch.object(wf,'stop_owned_tree',side_effect=wf.GateTreeBlocked(4321,'taskkill failed')):
            with self.assertRaises(wf.GitCommandTimeout) as caught: wf.git(self.repo,'status')
        self.assertFalse(caught.exception.cleanup_verified)
        self.assertEqual(caught.exception.pid,4321)
    def git(self,*args): return subprocess.run(['git','-C',str(self.repo),*args],check=True,capture_output=True).stdout
    def cli(self,*args,ok=True,repo=None,override=True):
        cmd=[sys.executable,str(SCRIPT)]
        if override: cmd+=['--asset-workspace',str(self.root)]
        args=list(map(str,args))
        if args and args[0]=='submit' and '--sprint' not in args: args+=['--sprint','fixture-sprint']
        cmd+=['--repo',str(repo or self.repo),*args]
        x=subprocess.run(cmd,capture_output=True,text=True)
        def diagnostic():
            return (f'command={cmd!r}\nreturncode={x.returncode}\n'
                    f'stdout={(x.stdout or "")[:2000]!r}\nstderr={(x.stderr or "")[:2000]!r}')
        if ok and x.returncode: self.fail('CLI command failed\n'+diagnostic())
        if not ok and x.returncode==0: self.fail('expected refusal\n'+diagnostic())
        return x
    def run_checks(self,ident,*,timeout=3600):
        """Call the private fixed-policy runner for synchronous unit fixtures."""
        return wf._run_gate_validation(Namespace(id=ident,actor='integration-lead'),self.root/'work/agent_exchange',timeout=timeout)
    def bind_completed_execution(self,ident):
        """Build the real admitted job shape and bind its completed receipt."""
        from tooling.agent_workflow import gate_execution
        exchange=self.root/'work/agent_exchange'; path=exchange/'candidates'/f'{ident}.json'
        candidate=json.loads(path.read_text())
        candidate['gate_request']={'prompt_admitted':True,'snapshot_key':candidate['snapshot_key'],
            'session_id':'fixture-lead','origin_session_id':'fixture-author'}
        wf.writejson(path,candidate)
        job=gate_execution.request(exchange,ident,workflow=wf)
        receipt=Path(candidate['gate_validation']['path'])
        job.update(state='completed',receipt={'path':str(receipt),'sha256':wf.sha(receipt)})
        gate_execution._write(gate_execution._path(exchange,ident),job)
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

    def test_import_uses_only_accepted_immutable_delivery_after_source_metadata_changes(self):
        d=self.create_claim_delivery(); self.ready(d)
        folder=self.root/'work/agent_exchange/requests/test-1'
        delivered=folder/'delivery/files/assets/moon/models/model.json'
        approved=delivered.read_bytes()
        receipt=self.root/'owner.json'
        receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted_as_is_closed','delivery_digest':d['digest']}))
        # Historical candidate artwork and navigation metadata can change after closure;
        # neither is an import source and neither invalidates the frozen delivery.
        (self.assets/'origin.txt').write_text('changed post-delivery provenance metadata')
        self.manifest.write_text(self.manifest.read_text()+' ')
        (self.assets/'model.json').write_text('new unapproved artwork')
        target=self.root/'target'; plan=self.root/'plan.json'
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan)
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply')
        self.assertEqual((target/'assets/moon/models/model.json').read_bytes(),approved)
        self.assertNotEqual((target/'assets/moon/models/model.json').read_text(),(self.assets/'model.json').read_text())

        # Boundaries remain strict: wrong identity/digest, pending acceptance and
        # tampering with the actual immutable delivered bytes all refuse import.
        bad=self.root/'bad.json'; bad.write_text(json.dumps({'request_id':'other','decision':'accepted_as_is_closed','delivery_digest':d['digest']}))
        self.cli('import-assets','test-1','--target',self.root/'bad-target','--owner-receipt',bad,ok=False)
        pending=self.root/'pending.json'; pending.write_text(json.dumps({'request_id':'test-1','decision':'pending','delivery_digest':d['digest']}))
        self.cli('import-assets','test-1','--target',self.root/'pending-target','--owner-receipt',pending,ok=False)
        delivered.write_bytes(b'tampered')
        self.cli('import-assets','test-1','--target',self.root/'tampered-target','--owner-receipt',receipt,ok=False)

    def test_native_obj_mtl_copy_requires_dependency_closure_and_canonical_destination(self):
        (self.assets/'model.obj').write_text('mtllib model.mtl\nv 0 0 0\n')
        (self.assets/'model.mtl').write_text('newmtl material\n')
        manifest=self.assets/'obj-manifest.json'
        manifest.write_text(json.dumps({'files':[{'file':'model.obj','destination':'assets/moon/models/model.obj'}, {'file':'model.mtl','destination':'assets/moon/models/model.mtl'}]}))
        self.cli('plan-manifest','--manifest',manifest,'--source-root',self.assets)
        manifest.write_text(json.dumps({'files':[{'file':'model.obj','destination':'assets/moon/models/model.obj'}]}))
        self.cli('plan-manifest','--manifest',manifest,'--source-root',self.assets,ok=False)

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

    def test_obj_mtl_texture_delivery_imports_frozen_closed_group_end_to_end(self):
        # This fixture exercises delivery and import only; model.json is declared editable
        # metadata and is not evidence of native geometry runtime integration.
        (self.assets/'model.obj').write_text('mtllib model.mtl\nv 0 0 0\n')
        (self.assets/'model.mtl').write_text('newmtl material\nmap_Kd model.png\n')
        self.manifest.write_text(json.dumps({'files':[
            {'file':'model.json','destination':'assets/moon/models/model.json'},
            {'file':'model.obj','destination':'assets/moon/models/model.obj'},
            {'file':'model.mtl','destination':'assets/moon/models/model.mtl'},
            {'file':'model.png','destination':'assets/moon/models/model.png'}]}))
        d=self.create_claim_delivery(); report=self.ready(d)
        receipt=self.root/'owner.json'
        receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted_as_is_closed','delivery_digest':d['digest']}))
        folder=self.root/'work/agent_exchange/requests/test-1'
        frozen={name:(folder/'delivery/files/assets/moon/models'/name).read_bytes()
                for name in ('model.obj','model.mtl','model.png')}
        (self.assets/'origin.txt').write_text('changed origin')
        self.manifest.write_text(self.manifest.read_text()+' ')
        (self.assets/'model.obj').write_text('new unapproved obj')
        target=self.root/'target'; plan=self.root/'plan.json'
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan-out',plan)
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply')
        for name,content in frozen.items():
            self.assertEqual((target/'assets/moon/models'/name).read_bytes(),content)
        self.assertNotEqual((target/'assets/moon/models/model.obj').read_text(),'new unapproved obj')

    def test_obj_dependency_closure_rejects_missing_mtl_texture_and_unsafe_remap(self):
        (self.assets/'model.obj').write_text('mtllib model.mtl\nv 0 0 0\n')
        (self.assets/'model.mtl').write_text('newmtl material\nmap_Kd model.png\n')
        manifest=self.assets/'obj-manifest.json'
        rows=[{'file':'model.obj','destination':'assets/moon/models/model.obj'},
              {'file':'model.mtl','destination':'assets/moon/models/model.mtl'},
              {'file':'model.png','destination':'assets/moon/models/model.png'}]
        for i,case in enumerate((rows[:1],rows[:2],
                                  [dict(rows[0]),dict(rows[1],destination='assets/moon/models/materials/model.mtl'),dict(rows[2],destination='assets/moon/textures/model.png')])):
            manifest.write_text(json.dumps({'files':case}))
            self.cli('plan-manifest','--manifest',manifest,'--source-root',self.assets,ok=False)
        (self.assets/'model.mtl').write_text('newmtl material\nmap_Kd ../escape.png\n')
        manifest.write_text(json.dumps({'files':rows}))
        self.cli('plan-manifest','--manifest',manifest,'--source-root',self.assets,ok=False)

    def test_import_preview_survives_changed_candidate_source_but_uses_frozen_copy(self):
        d=self.create_claim_delivery(); self.ready(d)
        receipt=self.root/'owner.json'; receipt.write_text(json.dumps({'request_id':'test-1','decision':'accepted','delivery_digest':d['digest']}))
        (self.assets/'model.json').write_text('updated source')
        self.cli('import-assets','test-1','--target',self.root/'target','--owner-receipt',receipt)
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
        self.cli('import-assets','test-1','--target',target,'--owner-receipt',receipt,'--plan',plan,'--apply')

        # Distinct new queue entry validates changed current manifest vs captured revision.
        request2=self.root/'request2.json'; request2.write_text(json.dumps({'id':'test-2','brief':'new sample','context':[]}))
        self.cli('request','--from-json',request2,'--context-root',self.repo); self.cli('claim','test-2','--agent','author')
        self.assets.joinpath('origin.txt').write_text('owned source + provenance')
        self.cli('deliver','test-2','--manifest',self.manifest,'--source-root',self.assets,'--revision','r1','--provenance','origin.txt','--editable','model.json','--review-images','model.png')
        # Edit original manifest after delivery; a previously valid source export is now stale.
        self.manifest.write_text(self.manifest.read_text()+' ')
        self.cli('import-assets','test-2','--target',target)

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
        status=self.cli('status','--verify').stdout; self.assertIn('code candidate-1: STALE/BLOCKED',status)

    def test_gate_plan_validation_and_runner_records_exact_check(self):
        plan={'title':'Gate fixture','kind':'bugfix','acceptance_criteria':['exit recorded'],'fixture':'seed.txt','manual_steps':['Inspect fixture'],'checks':[{'argv':[sys.executable,'-c','print("gate-ok")'],'rationale':'focused fixture check','covers':['exit recorded']}]}
        planfile=self.root/'gate.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','gate-1','--agent','author','--base-ref',self.base,'--build-identity','python fixture','--steps','manual fixture','--unverified','game runtime not tested','--gate-plan',planfile)
        self.cli('gate-preflight','gate-1'); self.run_checks('gate-1')
        report=json.loads((self.repo/'build/agent-workflow/fixture-sprint/reports/gate-1-gate-validation.json').read_text())
        self.assertTrue(report['passed']); self.assertEqual(report['checks'][0]['exit_code'],0)
        self.assertIn('gate-ok',(self.root/'work/agent_exchange'/report['checks'][0]['log']).read_text())

    def test_gate_stops_after_zero_exit_without_typed_build_evidence(self):
        plan={'title':'Fail-fast fixture','kind':'bugfix','acceptance_criteria':['missing artifact blocks'],'fixture':'seed.txt','manual_steps':['Inspect fixture'],'checks':[
            {'kind':'build','argv':[sys.executable,'-c','print("exit zero")'],'rationale':'artifact evidence required','covers':['missing artifact blocks'],'artifacts':['missing.bin']},
            {'argv':[sys.executable,'-c','raise SystemExit("must not run")'],'rationale':'expensive followup','covers':['missing artifact blocks']}]}
        planfile=self.root/'fail-fast.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','fail-fast-1','--agent','author','--base-ref',self.base,'--build-identity','python fixture','--steps','manual fixture','--unverified','runtime not tested','--gate-plan',planfile)
        with self.assertRaisesRegex(ValueError,'gate checks failed'): self.run_checks('fail-fast-1')
        report=json.loads((self.repo/'build/agent-workflow/fixture-sprint/reports/fail-fast-1-gate-validation.json').read_text())
        self.assertFalse(report['passed']); self.assertEqual(len(report['checks']),1)
        self.assertEqual(report['checks'][0]['evidence']['status'],'UNVERIFIED')

    def test_diagnostic_request_runs_existing_gate_without_readiness_promotion(self):
        plan={'title':'Agent02 native diagnostic','kind':'diagnostic','acceptance_criteria':['Collect native failure counts and logs.'],'fixture':'seed.txt','manual_steps':[],
              'checks':[{'argv':[sys.executable,'-c','print("diagnostic evidence")'],'rationale':'Collect the native diagnostic evidence','covers':['Collect native failure counts and logs.']}]}
        planfile=self.root/'diagnostic.json'; planfile.write_text(json.dumps(plan))
        args=['submit','diag-1','--agent','author','--base-ref',self.base,'--build-identity','diagnostic only','--steps','diagnostic only; no owner/manual readiness','--gate-plan',planfile,'--diagnostic-only']
        self.cli(*args)
        root=self.root/'work/agent_exchange'; candidate=root/'candidates/diag-1.json'
        saved=json.loads(candidate.read_text()); self.assertEqual(saved['status'],'diagnostic_request'); self.assertEqual(wf.is_diagnostic(saved),True)
        delivered=[]
        with mock.patch.object(wf,'_send_gate_completion',side_effect=lambda a,r,phase:delivered.append(phase)):
            self.run_checks('diag-1',timeout=10)
            receipt_path=wf.readjson(candidate)['gate_validation']['path']; receipt_hash=wf.sha(Path(receipt_path))
            original_popen=wf.subprocess.Popen; launched=[]
            def no_duplicate(command,*args,**kwargs):
                if command and command[0]!= 'git': launched.append(command)
                return original_popen(command,*args,**kwargs)
            with mock.patch.object(wf.subprocess,'Popen',side_effect=no_duplicate):
                self.run_checks('diag-1',timeout=10)
            self.assertEqual(launched,[])
        saved=json.loads(candidate.read_text()); self.assertEqual(saved['gate_outcome']['result'],'diagnostic_complete')
        self.assertEqual(delivered,[])
        receipt,g=wf.verify_gate_receipt(saved,require_pass=False); self.assertTrue(g['passed'])
        self.assertEqual(g['purpose'],'diagnostic'); self.assertEqual(wf.sha(receipt),receipt_hash)
        with self.assertRaisesRegex(ValueError,'not a passing readiness'):
            wf.verify_gate_receipt(saved,require_pass=True)
        with self.assertRaisesRegex(ValueError,'cannot be published'):
            wf.verify_publication(saved,root)
        with self.assertRaisesRegex(ValueError,'cannot enter gate finish'):
            wf.cmd_gate_finish(Namespace(id='diag-1',result='ready_for_owner',report='unused'),root)
        with self.assertRaisesRegex(ValueError,'cannot receive candidate readiness review'):
            wf.cmd_candidate_review(Namespace(id='diag-1'),root)
        with self.assertRaisesRegex(ValueError,'cannot receive owner disposition'):
            wf.cmd_candidate_decision(Namespace(id='diag-1',owner_receipt='unused'),root)
        self.assertFalse((root/'manual-tests.md').exists())

    def test_diagnostic_submission_requires_explicit_kind_and_flag(self):
        plan={'title':'Diagnostic mismatch','kind':'bugfix','acceptance_criteria':['x'],'fixture':'seed.txt','manual_steps':['Check manually'],'checks':[{'argv':[sys.executable,'-c','pass'],'rationale':'x','covers':['x']}]}
        planfile=self.root/'mismatch.json'; planfile.write_text(json.dumps(plan))
        base=['submit','diag-mismatch','--agent','author','--base-ref',self.base,'--build-identity','test','--steps','manual','--gate-plan',planfile]
        self.cli(*base,'--diagnostic-only',ok=False)
        plan['kind']='diagnostic'; plan['manual_steps']=[]; planfile.write_text(json.dumps(plan))
        self.cli(*base,ok=False)

    def test_gate_stops_after_typed_fail_with_zero_exit(self):
        plan={'title':'Typed fail fixture','kind':'bugfix','acceptance_criteria':['typed fail stops'],'fixture':'seed.txt','manual_steps':['Inspect fixture'],'checks':[
            {'kind':'build','argv':[sys.executable,'-c','print("exit zero")'],'rationale':'typed producer failure','covers':['typed fail stops'],'artifacts':['missing.bin']},
            {'argv':[sys.executable,'-c','raise SystemExit("must not run")'],'rationale':'expensive followup','covers':['typed fail stops']}]}
        planfile=self.root/'typed-fail.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','typed-fail-1','--agent','author','--base-ref',self.base,'--build-identity','python fixture','--steps','manual fixture','--unverified','runtime not tested','--gate-plan',planfile)
        root=self.root/'work/agent_exchange'
        with mock.patch.object(wf.validation_evidence,'collect_evidence',return_value={'status':'FAIL','counts':{},'selection':'unknown','artifacts':[],'findings':['typed failure']}):
            with self.assertRaisesRegex(ValueError,'gate checks failed'):
                self.run_checks('typed-fail-1',timeout=10)
        report=json.loads((self.repo/'build/agent-workflow/fixture-sprint/reports/typed-fail-1-gate-validation.json').read_text())
        self.assertFalse(report['passed']); self.assertEqual(len(report['checks']),1)
        self.assertEqual(report['checks'][0]['exit_code'],0); self.assertEqual(report['checks'][0]['evidence']['status'],'FAIL')
        candidate_path=root/'candidates'/'typed-fail-1.json'; candidate=json.loads(candidate_path.read_text())
        candidate['gate_request']={'prompt_admitted':True,'snapshot_key':candidate['snapshot_key'],'session_id':'fixture-lead'}
        wf.writejson(candidate_path,candidate)
        from tooling.agent_workflow import gate_execution
        job=gate_execution.request(root,'typed-fail-1',workflow=wf); receipt=Path(candidate['gate_validation']['path'])
        job.update(state='completed',receipt={'path':str(receipt),'sha256':wf.sha(receipt)})
        gate_execution._write(gate_execution._path(root,'typed-fail-1'),job)
        from io import StringIO
        output=StringIO()
        with mock.patch('tooling.agent_workflow.gate_dispatch.deliver_gate_completion',return_value={'status':'unavailable'}), contextlib.redirect_stdout(output):
            wf.cmd_gate_report(Namespace(id='typed-fail-1',actor='integration-lead',result='needs_changes',finding=['Independent review rejects insufficient coverage.'],scoped_review=None,finish=True),root)
        saved=json.loads(Path(output.getvalue().splitlines()[0]).read_text())
        self.assertEqual(saved['findings'],['Independent review rejects insufficient coverage.'])
        self.assertEqual(saved['evidence_summary']['status'],'FAILED_OR_PARTIAL')
        self.assertEqual(saved['evidence_summary']['checks'][0]['status'],'FAIL')

    def test_gametest_batch_finishes_before_failure_evidence_stops_later_checks(self):
        plan={'title':'GameTest batch fixture','kind':'bugfix','acceptance_criteria':['batch fully collected'],'fixture':'seed.txt','manual_steps':['Inspect fixture'],'checks':[
            {'kind':'gametest','argv':['fake-gradle','runGameTestServer'],'log':'build/gametest.log','rationale':'complete fake batch','covers':['batch fully collected']},
            {'argv':[sys.executable,'-c','raise SystemExit("must not run")'],'rationale':'later check','covers':['batch fully collected']}]}
        planfile=self.root/'gametest-batch.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','gametest-batch','--agent','author','--base-ref',self.base,'--build-identity','fake process','--steps','fixture','--unverified','native engine not launched','--gate-plan',planfile)
        class FakeProcess:
            pid=45678
            calls=0
            def wait(self,timeout=None):
                self.calls+=1
                if self.calls==1: raise subprocess.TimeoutExpired('fake-gradle',timeout)
                return 0
        process=FakeProcess(); stop=mock.Mock()
        root=self.root/'work/agent_exchange'; report_dir=self.repo/'build/agent-workflow/fixture-sprint/reports'
        def launch(*args,**kwargs):
            self.assertEqual(args[0],plan['checks'][0]['argv'])
            kwargs['stdout'].write('GameTest early failure: fixture A\nGameTest later result: fixture B complete\n1 test failed\n')
            return process
        def evidence(check,repo,started,finished,log,before,**kwargs):
            text=log.read_text(); self.assertIn('GameTest later result: fixture B complete',text)
            self.assertIn('1 test failed',text)
            return {'status':'FAIL','counts':{'failed':1},'selection':'filtered','artifacts':[],'findings':['native GameTest failure summary present']}
        with mock.patch.object(wf,'verify_gate',return_value=(self.repo,plan)), mock.patch.object(wf,'author_report_dir',return_value=report_dir), \
             mock.patch.object(wf.subprocess,'Popen',side_effect=launch), mock.patch.object(wf,'stop_owned_tree',stop), \
             mock.patch.object(wf.validation_evidence,'collect_evidence',side_effect=evidence):
            with self.assertRaisesRegex(ValueError,'gate checks failed'):
                self.run_checks('gametest-batch',timeout=20)
        saved=json.loads((report_dir/'gametest-batch-gate-validation.json').read_text())
        self.assertFalse(saved['passed']); self.assertEqual(len(saved['checks']),1)
        self.assertEqual(saved['checks'][0]['evidence']['counts']['failed'],1)
        self.assertEqual(process.calls,2); stop.assert_not_called()

    def test_gate_to_manual_log_and_explicit_owner_disposition(self):
        plan={'title':'Full gate cycle','kind':'bugfix','acceptance_criteria':['unit passes'],'fixture':'seed.txt','manual_steps':['Check behavior','Confirm expected result'],'checks':[{'argv':[sys.executable,'-c','print("ok")'],'rationale':'focused fake check','covers':['unit passes']}]}
        planfile=self.root/'cycle.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','cycle-1','--agent','author','--base-ref',self.base,'--build-identity','fixture build','--steps','manual','--unverified','runtime not exercised','--gate-plan',planfile)
        cpath=self.root/'work/agent_exchange/candidates/cycle-1.json'; c=json.loads(cpath.read_text())
        c['gate_request']={'origin_session_id':'owner-session'}; wf.writejson(cpath,c)
        review=self.assets/'cycle-review.json'; data={'candidate_id':'cycle-1','snapshot_key':c['snapshot_key'],'reviewer':'lead','decision':'ready_for_owner','findings':['manual owner check remains'],'viewed_images':[]}; review.write_text(json.dumps(data))
        self.cli('candidate-review','cycle-1','--reviewer','lead','--report',review,'--result','ready_for_owner')
        self.run_checks('cycle-1')
        self.bind_completed_execution('cycle-1')
        c=json.loads(cpath.read_text()); review_data=json.loads(review.read_text())
        review_data['evidence_summary']=wf._gate_evidence_summary(c,self.root/'work/agent_exchange','ready_for_owner')
        review.write_text(json.dumps(review_data)); c['review']['report_sha256']=wf.sha(review); wf.writejson(cpath,c)
        import gate_dispatch
        order=[]
        queue_root=self.root/'work'/'agent_exchange'; lock_paths=[]; original_lock=wf.gate_lock
        def cheap_only_lock(path,*args,**kwargs):
            resolved=Path(path).resolve(); lock_paths.append(resolved)
            if resolved==queue_root.resolve(): raise AssertionError('manual publication must not wait on expensive gate-validation mutex')
            return original_lock(path,*args,**kwargs)
        with mock.patch.object(wf,'gate_lock',side_effect=cheap_only_lock), mock.patch.object(gate_dispatch,'deliver_gate_completion',side_effect=lambda *args,**kwargs: order.append('author') or {'status':'unavailable'}), mock.patch.object(wf,'_ensure_owner_feedback',return_value={'status':'started'}), mock.patch.object(wf,'native_notify',side_effect=lambda *args: order.append('balloon') or 'delivered_to_os; user visibility is not guaranteed') as notify:
            wf.cmd_gate_finish(Namespace(id='cycle-1',result='ready_for_owner',report=str(review)),self.root/'work/agent_exchange')
            wf.cmd_gate_finish(Namespace(id='cycle-1',result='ready_for_owner',report=str(review)),self.root/'work/agent_exchange')
        self.assertTrue(any(p==queue_root.resolve()/'manual-publication' for p in lock_paths))
        notify.assert_called_once()
        self.assertEqual(order[:2],['author','balloon'])
        index=(self.root/'work/agent_exchange/manual-tests.md').read_text()
        self.assertIn('cycle-1 (open)',index); self.assertIn('fixture build',index); self.assertIn(str(self.repo),index)
        self.assertIsNone(json.loads(cpath.read_text()).get('owner_decision'))
        receipt=self.root/'cycle-owner.json'; receipt.write_text(json.dumps({'candidate_id':'cycle-1','snapshot_key':c['snapshot_key'],'decision':'accepted','owner_reference':'explicit fixture owner','note':'record only'}))
        self.cli('candidate-decision','cycle-1','--owner-receipt',receipt)
        self.assertIn('cycle-1 (owner_accepted)',(self.root/'work/agent_exchange/manual-tests.md').read_text())

    def test_native_notification_hides_terminal_window(self):
        manual=self.root/'space and ünicode'/'owner manual tests.md'
        with mock.patch.object(wf.os,'name','nt'),mock.patch.object(wf.subprocess,'Popen') as launch:
            result=wf.native_notify(manual,"O'Brien fixture",'specific candidate body')
        argv=launch.call_args.args[0]; encoded=argv[-1]
        import base64
        script=base64.b64decode(encoded).decode('utf-16le')
        self.assertIn('-EncodedCommand',argv); self.assertIn('BalloonTipClicked',script)
        self.assertIn('[System.Windows.Forms.Application]::Run($form)',script)
        self.assertIn('$timer.Interval=120000',script); self.assertIn('ProcessStartInfo',script)
        self.assertIn('$form.ShowInTaskbar=$false',script); self.assertIn('$form.Hide()',script)
        expected=(manual.parent/'integration-status.html').resolve().as_uri()
        self.assertIn(__import__('base64').b64encode(expected.encode()).decode(),script)
        self.assertNotIn('owner%20manual%20tests.md',script)
        self.assertIn(__import__('base64').b64encode("O'Brien fixture".encode()).decode(),script)
        self.assertIn(__import__('base64').b64encode('specific candidate body'.encode()).decode(),script)
        self.assertIn('$psi.FileName=$targetUri',script)
        self.assertEqual(launch.call_args.kwargs['creationflags'],getattr(subprocess,'CREATE_NO_WINDOW',0))
        self.assertEqual(launch.call_args.kwargs['stdin'],subprocess.DEVNULL)
        self.assertEqual(launch.call_args.kwargs['stdout'],subprocess.DEVNULL)
        self.assertEqual(launch.call_args.kwargs['stderr'],subprocess.DEVNULL)
        launch.return_value.wait.assert_not_called()
        self.assertIn('listener_launched',result)

    def test_manual_notification_titles_are_specific_and_candidate_bound(self):
        generic={'id':'gas-mutation','sprint_id':'botany','revision_sequence':3,'worktree':{'worktree':str(self.root/'MoonStation14-agent-04')},
                 'gate_plan':{'title':'Visual testing fixture'}}
        title=wf.manual_notification_title(generic)
        self.assertIn('Botany',title); self.assertIn('agent-04',title); self.assertIn('ready',title)
        specific=dict(generic,id='gas-mutation-2',gate_plan={'title':'Botany · gas mutation'})
        self.assertIn('Botany · gas mutation',wf.manual_notification_title(specific))
        self.assertNotEqual(wf.manual_notification_title(generic),wf.manual_notification_title(specific))

    def test_manual_notification_requires_current_unanswered_owner_handoff(self):
        root=self.root/'work'/'agent_exchange'; (root/'candidates').mkdir(parents=True)
        candidate={'id':'notify-1','snapshot_key':'snap','sprint_id':'notify-sprint','revision_sequence':2,
            'worktree':{'worktree':str(self.repo)},'base_ref':self.base,'gate_plan':{'title':'Visual testing fixture','manual_steps':['Check target mutation']},
            'gate_outcome':{'result':'ready_for_owner','manual_entry':'notify-1','notification':'pending'},
            'review':{'report_path':str(self.assets/'independent-review.json'),'report_sha256':'report-hash'},'owner_review':{'state':'awaiting_owner','candidate_id':'notify-1',
                'snapshot_key':'snap','origin_session_id':'owner-session'}}
        path=root/'candidates/notify-1.json'; wf.writejson(path,candidate)
        with mock.patch.object(wf,'verify_gate',return_value=(self.repo,candidate['gate_plan'])), \
             mock.patch.object(wf,'verify_publication'), mock.patch.object(wf,'native_notify',return_value='listener_launched; OS visibility and click are not verified') as notify:
            result=wf.send_manual_notification(path,root)
        self.assertIn('listener_launched',result); notify.assert_called_once()
        receipt=wf.readjson(path)['gate_outcome']['notification_receipt']
        self.assertEqual(receipt['candidate_id'],'notify-1'); self.assertEqual(receipt['snapshot_key'],'snap')
        self.assertEqual(receipt['report_sha256'],'report-hash'); self.assertIn('repo',receipt['title'])
        self.assertIn('notify-1',receipt['message']); self.assertEqual(receipt['status'],'listener_launched')
        self.assertIn('Check target mutation',receipt['message'])
        self.assertIn('snap',receipt['message']); self.assertIn('review report-h',receipt['message'])
        self.assertIn(str(self.assets/'independent-review.json'),receipt['report_path'])
        self.assertIn('integration-status.html',receipt['target_url'])
        self.assertIn('review '+'report-hash'[:8],receipt['message'])
        self.assertEqual(notify.call_args.args[2],receipt['message'])
        notify.reset_mock(); changed=wf.readjson(path); changed['owner_review']['state']='feedback_received'; wf.writejson(path,changed)
        with mock.patch.object(wf,'verify_gate',return_value=(self.repo,candidate['gate_plan'])):
            with self.assertRaisesRegex(ValueError,'current owner-review handoff'): wf.send_manual_notification(path,root)
        notify.assert_not_called()

    def test_manual_notification_retry_reuses_exact_persisted_body(self):
        root=self.root/'work'/'agent_exchange'; folder=root/'candidates'; folder.mkdir(parents=True)
        c={'id':'notify-retry','snapshot_key':'retry-snapshot','sprint_id':'notify-retry','revision_sequence':1,
            'worktree':{'worktree':str(self.repo)},'base_ref':self.base,
            'gate_plan':{'title':'Botany gas mutation','manual_steps':['Inspect atmosphere status','Confirm valve response']},
            'gate_outcome':{'result':'ready_for_owner','manual_entry':'notify-retry','notification':'pending'},
            'review':{'report_path':str(self.assets/'review.json'),'report_sha256':'abc123def456'},
            'owner_review':{'state':'awaiting_owner','candidate_id':'notify-retry','snapshot_key':'retry-snapshot','origin_session_id':'owner'}}
        path=folder/'notify-retry.json'; wf.writejson(path,c); sent=[]
        with mock.patch.object(wf,'verify_gate',return_value=(self.repo,c['gate_plan'])),mock.patch.object(wf,'verify_publication'), \
             mock.patch.object(wf,'native_notify',side_effect=lambda *args: sent.append(args) or 'listener_launched; OS visibility and click are not verified'):
            wf.send_manual_notification(path,root)
            saved=wf.readjson(path); original_body=saved['gate_outcome']['notification_receipt']['message']
            saved['gate_plan']['manual_steps']=['Changed later, must not alter retry payload']; wf.writejson(path,saved)
            # A failed prior OS attempt may retry, but the same bound candidate must reuse its persisted text.
            saved['gate_outcome']['notification']='failed: transient'; saved['gate_outcome']['notification_receipt']['status']='failed'; wf.writejson(path,saved)
            wf.send_manual_notification(path,root)
        self.assertEqual(sent[0][2],original_body); self.assertEqual(sent[1][2],original_body)
        self.assertIn('Inspect atmosphere status',original_body); self.assertNotIn('Changed later',original_body)

    def test_long_notification_keeps_visible_candidate_snapshot_step_and_review_hash(self):
        root=self.root/'work'/'agent_exchange'; folder=root/'candidates'; folder.mkdir(parents=True)
        ident='candidate-'+'x'*70; snapshot='a'*64; report_path=str(self.assets/('review-'+('long-path-'*15)+'.json'))
        steps=['Verify the chemical release valve closes after the gas mutation is applied.','Check the recorded console status.']
        c={'id':ident,'snapshot_key':snapshot,'sprint_id':'botany','revision_sequence':4,
           'worktree':{'worktree':str(self.root/'MoonStation14-agent-09')},'base_ref':self.base,
           'gate_plan':{'title':'Botany · chemical gas mutation','manual_steps':steps},
           'gate_outcome':{'result':'ready_for_owner','manual_entry':ident,'notification':'pending'},
           'review':{'report_path':report_path,'report_sha256':'b'*64},
           'owner_review':{'state':'awaiting_owner','candidate_id':ident,'snapshot_key':snapshot,'origin_session_id':'owner'}}
        path=folder/(ident+'.json'); wf.writejson(path,c); sent=[]
        with mock.patch.object(wf,'verify_gate',return_value=(Path(c['worktree']['worktree']),c['gate_plan'])), \
             mock.patch.object(wf,'verify_publication'),mock.patch.object(wf,'native_notify',side_effect=lambda *args: sent.append(args) or 'listener_launched; OS visibility and click are not verified'):
            wf.send_manual_notification(path,root)
        title,message=sent[0][1:3]
        self.assertLessEqual(len(message),255); self.assertIn(ident,message); self.assertIn(snapshot[:8],message)
        self.assertIn('b'*8,message); self.assertIn('Verify the chemical release valve closes',message)
        receipt=wf.readjson(path)['gate_outcome']['notification_receipt']
        self.assertEqual(receipt['report_path'],report_path); self.assertEqual(receipt['manual_steps'],steps)
        self.assertIn('Botany',title); self.assertIn('agent-09',title)
        with mock.patch.object(wf.os,'name','nt'),mock.patch.object(wf.subprocess,'Popen') as launch:
            wf.native_notify(root/'manual-tests.md',title,message)
        script=__import__('base64').b64decode(launch.call_args.args[0][-1]).decode('utf-16le')
        encoded_text=re.search(r"\$n\.BalloonTipText=\[System\.Text\.Encoding\]::UTF8\.GetString\(\[System\.Convert\]::FromBase64String\('([^']+)'\)\)",script).group(1)
        captured=__import__('base64').b64decode(encoded_text).decode('utf-8')
        self.assertEqual(captured,message); self.assertLessEqual(len(captured),255)

    def test_manual_notification_refuses_stale_or_superseded_candidate(self):
        root=self.root/'work'/'agent_exchange'; folder=root/'candidates'; folder.mkdir(parents=True)
        base={'snapshot_key':'snap','sprint_id':'notify-sprint','revision_sequence':2,'worktree':{'worktree':str(self.repo)},
            'gate_plan':{'title':'Botany mutation'},'gate_outcome':{'result':'ready_for_owner','notification':'pending'},
            'review':{'report_sha256':'hash'},'owner_review':{'state':'awaiting_owner','candidate_id':'notify-old','snapshot_key':'snap','origin_session_id':'owner'}}
        old=dict(base,id='notify-old'); path=folder/'notify-old.json'; wf.writejson(path,old)
        newer=dict(base,id='notify-new',revision_sequence=3); wf.writejson(folder/'notify-new.json',newer)
        with mock.patch.object(wf,'verify_gate',return_value=(self.repo,base['gate_plan'])),mock.patch.object(wf,'verify_publication'), \
             mock.patch.object(wf,'native_notify') as notify:
            with self.assertRaisesRegex(ValueError,'no longer the current'):
                wf.send_manual_notification(path,root)
            notify.assert_not_called()
        old['id']='notify-stale'; old['owner_review'].update(candidate_id='notify-stale',snapshot_key='snap')
        old['gate_outcome']['notification']='pending'; stale=folder/'notify-stale.json'; wf.writejson(stale,old)
        with mock.patch.object(wf,'verify_gate',side_effect=ValueError('candidate source stale')),mock.patch.object(wf,'native_notify') as notify:
            with self.assertRaisesRegex(ValueError,'candidate source stale'): wf.send_manual_notification(stale,root)
            notify.assert_not_called()

    def test_status_defaults_to_metadata_and_candidate_selector_verifies_only_one_record(self):
        root=self.root/'work'/'agent_exchange'; folder=root/'candidates'; folder.mkdir(parents=True)
        report=self.assets/'saved-needs-report.json'; report.write_text('saved report bytes')
        for ident in ('fast-a','fast-b','fast-c'):
            digest=wf.sha(report)
            c={'id':ident,'status':'candidate','snapshot_key':'snap-'+ident,'review':{'result':'needs_changes',
                'report_path':str(report),'report_sha256':digest},'gate_outcome':{'result':'needs_changes',
                'findings':['First exact actionable finding.','Second exact actionable finding.'],'report_sha256':digest}}
            wf.writejson(folder/f'{ident}.json',c)
        output=io.StringIO()
        with mock.patch.object(wf,'verify_candidate',side_effect=AssertionError('default status must not verify history')),contextlib.redirect_stdout(output):
            wf.cmd_status(Namespace(candidate=None,verify=False),root)
        self.assertEqual(output.getvalue().count('freshness not checked'),3)
        calls=[]; output=io.StringIO()
        with mock.patch.object(wf,'verify_candidate',side_effect=lambda c: calls.append(c['id']) or c),contextlib.redirect_stdout(output):
            wf.cmd_status(Namespace(candidate='fast-b',verify=True),root)
        self.assertEqual(calls,['fast-b'])
        self.assertIn('First exact actionable finding.',output.getvalue())
        self.assertIn('Second exact actionable finding.',output.getvalue())
        self.assertIn(str(report),output.getvalue())

    def test_status_marks_historical_ready_stale_instead_of_current_ready(self):
        root=self.root/'work'/'agent_exchange'; folder=root/'candidates'; folder.mkdir(parents=True)
        c={'id':'stale-ready','status':'candidate','snapshot_key':'snap','gate_outcome':{'result':'ready_for_owner'},
           'review':{'result':'ready_for_owner','report_path':'/missing/report','report_sha256':'hash'}}
        wf.writejson(folder/'stale-ready.json',c); output=io.StringIO()
        with mock.patch.object(wf,'verify_candidate',side_effect=ValueError('author source changed')),contextlib.redirect_stdout(output):
            wf.cmd_status(Namespace(candidate='stale-ready',verify=True),root)
        self.assertIn('STALE/BLOCKED',output.getvalue()); self.assertIn('recorded ready_for_owner',output.getvalue())
        self.assertNotIn('ready_for_owner (verified current)',output.getvalue())

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
        with self.assertRaisesRegex(ValueError,'gate checks failed'):
            self.run_checks('timeout-1',timeout=1)
        report=json.loads((self.repo/'build/agent-workflow/fixture-sprint/reports/timeout-1-gate-validation.json').read_text())
        self.assertEqual(report['checks'][0]['exit_code'],124)
        import time; time.sleep(2.2); self.assertFalse(marker.exists())
        lock=wf.gate_lock(self.root/'work/agent_exchange'); lock.close()

    def test_unverified_tree_cleanup_quarantines_gate_until_explicit_lead_confirmation(self):
        plan={'title':'Quarantine fixture','kind':'bugfix','acceptance_criteria':['bounded cleanup'],'fixture':'seed.txt','manual_steps':['Inspect blocker'],'checks':[{'argv':[sys.executable,'-c','pass'],'rationale':'bounded process stop','covers':['bounded cleanup']}]}
        planfile=self.root/'blocked.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','blocked-1','--agent','author','--base-ref',self.base,'--build-identity','process fixture','--steps','cleanup','--unverified','not runtime','--gate-plan',planfile)
        child=mock.Mock(pid=54321,wait=mock.Mock(side_effect=lambda *args,**kwargs: (_ for _ in ()).throw(subprocess.TimeoutExpired('child',1))))
        root=self.root/'work/agent_exchange'
        report_folder=self.repo/'build/agent-workflow/fixture-sprint/reports'
        with mock.patch.object(wf,'verify_gate',return_value=(self.repo,plan)), mock.patch.object(wf,'author_report_dir',return_value=report_folder), mock.patch.object(wf.subprocess,'Popen',return_value=child), mock.patch.object(wf,'stop_owned_tree',side_effect=wf.GateTreeBlocked(54321,'taskkill timeout/failure')):
            with self.assertRaisesRegex(ValueError,'gate checks failed'):
                self.run_checks('blocked-1',timeout=1)
        blocker=json.loads((root/'gate-blocker.json').read_text())
        self.assertEqual(blocker['pid'],54321)
        self.assertEqual(json.loads((self.repo/'build/agent-workflow/fixture-sprint/reports/blocked-1-gate-validation.json').read_text())['checks'][0]['tree_cleanup'],'unverified')
        with self.assertRaisesRegex(ValueError,'gate quarantined'):
            self.run_checks('blocked-1',timeout=1)
        with self.assertRaisesRegex(ValueError,'PID does not match'):
            wf.cmd_gate_clear_blocker(Namespace(actor='integration-lead',pid=12,confirmed_stopped=True),root)
        with mock.patch.object(wf,'process_presence',return_value='unknown'):
            with self.assertRaisesRegex(ValueError,'unknown|cleanup proof|interrupted execution'):
                wf.cmd_gate_clear_blocker(Namespace(actor='integration-lead',pid=54321,confirmed_stopped=True),root)
        with mock.patch.object(wf,'process_presence',return_value='absent'):
            with self.assertRaisesRegex(ValueError,'legacy blocker|cleanup proof'):
                wf.cmd_gate_clear_blocker(Namespace(actor='integration-lead',pid=54321,confirmed_stopped=True),root)
        self.assertTrue((root/'gate-blocker.json').exists())

    def test_taskkill_failure_is_bounded_and_never_claims_tree_stopped(self):
        proc=mock.Mock(pid=999,wait=mock.Mock())
        failed=subprocess.CompletedProcess(['taskkill'],1)
        with mock.patch.object(wf.subprocess,'run',return_value=failed) as run:
            with self.assertRaises(wf.GateTreeBlocked): wf.stop_owned_tree(proc,timeout=2)
        run.assert_called_once(); self.assertEqual(run.call_args.kwargs['timeout'],2); proc.wait.assert_not_called()
        if os.name=='nt': self.assertEqual(run.call_args.kwargs['creationflags'],getattr(subprocess,'CREATE_NO_WINDOW',0))

    def test_original_visual_binding_tracks_source_and_dispatch_status_exit_codes(self):
        source=self.repo/'evidence.png'; candidate={'worktree':{'worktree':str(self.repo)},'evidence':[{'path':str(source),'sha256':wf.sha(source)}]}
        record={'path':'evidence.png','sha256':wf.sha(source)}
        self.assertEqual(wf.verify_original_images([record],candidate)[0]['path'],str(source.resolve()))
        reports=self.root/'reports'; reports.mkdir(); (reports/'image.png').write_bytes(source.read_bytes())
        copied={'path':'image.png','sha256':wf.sha(source),'original_path':str(source),'original_sha256':wf.sha(source)}
        self.assertEqual(wf.verify_original_images([copied],candidate,reports)[0]['sha256'],wf.sha(source))
        source.write_bytes(PNG+b'stale')
        with self.assertRaisesRegex(ValueError,'stale'): wf.verify_original_images([copied],candidate,reports)

        import gate_dispatch
        # Gate intake intentionally rejects unknown IDs before consulting the observer.
        # Seed a real candidate so this fixture exercises the status-to-exit-code path.
        self.cli('submit','code-1','--agent','author','--base-ref',self.base,'--build-identity','exit-code fixture','--steps','focused test','--unverified','No Minecraft runtime exercised')
        args=Namespace(id='code-1',central_worktree=None,origin_session=None,server=None,wait_timeout=10)
        output=io.StringIO()
        with mock.patch.object(gate_dispatch,'request_gate',return_value={'status':'needs_changes','candidate_id':'code-1','session_id':'s','detail':['fix']}) as call, contextlib.redirect_stdout(output):
            with self.assertRaises(wf.WorkflowExit) as exc: wf.cmd_gate_request(args,self.root/'work'/'agent_exchange')
        self.assertEqual(exc.exception.code,3); call.assert_called_once()
        self.assertIn('needs_changes',output.getvalue())

    def test_launch_smoke_matches_real_marker_controls_owned_tree_and_hashes_startup_scope(self):
        marker=self.root/'smoke-child-marker'
        child="import time,pathlib; time.sleep(1.5); pathlib.Path(r'%s').write_text('alive')"%marker
        command="import subprocess,sys,time; print('HTTP READY: port=8080',flush=True); subprocess.Popen([sys.executable,'-c',%r]); time.sleep(20)"%child
        check={'kind':'launch-smoke','argv':[sys.executable,'-u','-c',command],'rationale':'bounded synthetic startup probe','covers':['startup marker'],'log':'build/gate-smoke.log','ready_patterns':['HTTP READY: port=\\d+'],'error_patterns':['FATAL: startup']}
        plan={'title':'Synthetic launch smoke','kind':'bugfix','acceptance_criteria':['startup marker'],'fixture':'seed.txt','manual_steps':['Review startup-only evidence'],'checks':[check]}
        planfile=self.root/'smoke.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit','smoke-1','--agent','author','--base-ref',self.base,'--build-identity','python fixture only','--steps','not Minecraft','--unverified','No GPU, real client input, or gameplay consumer was tested','--gate-plan',planfile)
        self.run_checks('smoke-1')
        report=json.loads((self.repo/'build/agent-workflow/fixture-sprint/reports/smoke-1-gate-validation.json').read_text())
        item=report['checks'][0]
        self.assertTrue(report['passed']); self.assertTrue(item['controlled_stop']); self.assertEqual(item['evidence']['status'],'PASS')
        self.assertIn('HTTP READY: port=8080',(self.repo/'build/gate-smoke.log').read_text())
        time.sleep(1.8); self.assertFalse(marker.exists())

    def test_launch_smoke_exit_zero_without_marker_is_not_readiness(self):
        check={'kind':'launch-smoke','argv':[sys.executable,'-c','print("startup incomplete")'],'rationale':'no readiness marker fixture','covers':['startup marker'],'log':'build/missing-smoke.log','ready_patterns':['HTTP READY']}
        plan={'title':'Missing marker','kind':'bugfix','acceptance_criteria':['startup marker'],'fixture':'seed.txt','manual_steps':['Inspect failure'],'checks':[check]}
        path=self.root/'missing-smoke.json'; path.write_text(json.dumps(plan))
        self.cli('submit','smoke-missing','--agent','author','--base-ref',self.base,'--build-identity','fixture','--steps','check missing marker','--unverified','runtime not tested','--gate-plan',path)
        with self.assertRaisesRegex(ValueError,'gate checks failed'): self.run_checks('smoke-missing')
        report=json.loads((self.repo/'build/agent-workflow/fixture-sprint/reports/smoke-missing-gate-validation.json').read_text())
        self.assertFalse(report['passed']); self.assertEqual(report['checks'][0]['exit_code'],1)
        self.assertEqual(report['checks'][0]['evidence']['status'],'FAIL')

    def test_nonvisual_candidate_review_no_fake_image_and_needs_changes(self):
        self.cli('submit','code-1','--agent','author','--base-ref',self.base,'--build-identity','stdlib test','--steps','python -m unittest','--unverified','Minecraft not run')
        saved=json.loads((self.root/'work/agent_exchange/candidates/code-1.json').read_text())
        report=self.assets/'nonvisual.json'; report.write_text(json.dumps({'candidate_id':'code-1','snapshot_key':saved['snapshot_key'],'reviewer':'reviewer','decision':'ready_for_owner','findings':['CLI behavior covered'],'viewed_images':[]}))
        self.cli('candidate-review','code-1','--reviewer','reviewer','--report',report,'--result','ready_for_owner')
        self.assertIn('code code-1: ready_for_owner',self.cli('status').stdout)
        stable=report.read_text(); report.write_text(stable+' ')
        self.assertIn('code code-1: STALE/BLOCKED',self.cli('status','--verify').stdout)
        report.write_text(stable)
        self.assertIn('code code-1: ready_for_owner',self.cli('status').stdout)
        owner=self.root/'owner-decision.json'; ownerdata={'candidate_id':'code-1','snapshot_key':saved['snapshot_key'],'decision':'accepted','owner_reference':'William explicitly accepted this candidate','note':'Owner acceptance only; not a test result.'}; owner.write_text(json.dumps(ownerdata))
        self.cli('candidate-decision','code-1','--owner-receipt',owner)
        self.assertIn('code code-1: owner_accepted',self.cli('status').stdout)
        owner_bytes=owner.read_bytes(); owner.write_text(owner.read_text()+' ')
        self.assertIn('code code-1: STALE/BLOCKED (owner_accepted recorded)',self.cli('status','--verify').stdout)
        owner.write_bytes(owner_bytes)

        self.cli('submit','code-evidence','--agent','author','--base-ref',self.base,'--build-identity','stdlib test','--steps','not run','--unverified','runtime','--evidence','evidence.png')
        evidence_record=json.loads((self.root/'work/agent_exchange/candidates/code-evidence.json').read_text())
        evidence_report=self.assets/'evidence-review.json'; evidence_report.write_text(json.dumps({'candidate_id':'code-evidence','snapshot_key':evidence_record['snapshot_key'],'reviewer':'reviewer','decision':'ready_for_owner','findings':['evidence checked'],'viewed_images':[]}))
        self.cli('candidate-review','code-evidence','--reviewer','reviewer','--report',evidence_report,'--result','ready_for_owner')
        self.assertIn('code code-evidence: ready_for_owner',self.cli('status').stdout)
        (self.repo/'evidence.png').write_bytes(PNG+b'changed')
        self.assertIn('code code-evidence: STALE/BLOCKED',self.cli('status','--verify').stdout)
        self.assertIn('code code-1: STALE/BLOCKED (owner_accepted recorded)',self.cli('status','--verify').stdout)

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

    def test_integration_lead_report_uses_author_folder_and_preserves_candidate_freshness(self):
        self.cli('submit','lead-report','--agent','author','--sprint','sprint-a','--base-ref',self.base,
                 '--build-identity','python fixture','--steps','not run','--unverified','runtime not tested')
        record=json.loads((self.root/'work/agent_exchange/candidates/lead-report.json').read_text())
        snapshot=record['snapshot_key']; folder=self.repo/'build/agent-workflow/sprint-a/reports'
        self.cli('gate-report','lead-report','--actor','author','--result','needs_changes','--finding','Not allowed.',ok=False)
        self.cli('gate-report','lead-report','--actor','integration-lead','--result','ready_for_owner','--finding',' ',ok=False)
        self.cli('gate-report','lead-report','--actor','integration-lead','--result','needs_changes','--finding','Fix the fixture.')
        report=folder/'lead-report-integration-lead-review.json'
        self.assertTrue(report.is_file())
        self.assertEqual(json.loads(report.read_text())['reviewer'],'integration-lead')
        after=json.loads((self.root/'work/agent_exchange/candidates/lead-report.json').read_text())
        self.assertEqual(after['snapshot_key'],snapshot)
        self.assertEqual(after['review']['report_path'],str(report.resolve()))
        self.assertEqual(wf.verify_candidate(after)['snapshot_key'],snapshot)

    def test_cli_refusal_diagnostics_include_command_status_and_streams(self):
        failed=subprocess.CompletedProcess(['python','workflow_runtime.py','gate-report'],7,'fixture stdout','typed fixture error')
        succeeded=subprocess.CompletedProcess(['python','workflow_runtime.py','gate-report'],0,'unexpected success','')
        with mock.patch.object(subprocess,'run',side_effect=[failed,succeeded]):
            with self.assertRaises(AssertionError) as caught: self.cli('gate-report','fixture')
            for expected in ('command=', 'gate-report', 'returncode=7', 'fixture stdout', 'typed fixture error'):
                self.assertIn(expected,str(caught.exception))
            with self.assertRaises(AssertionError) as caught: self.cli('gate-report','fixture',ok=False)
            for expected in ('expected refusal', 'returncode=0', 'unexpected success'):
                self.assertIn(expected,str(caught.exception))

    def test_non_git_central_durable_intake_defers_stale_check_to_observer(self):
        self.cli('submit','stale-author','--agent','author','--sprint','sprint-a','--base-ref',self.base,
                 '--build-identity','fixture','--steps','not run','--unverified','runtime not tested')
        (self.repo/'seed.txt').write_text('author changed after submission')
        from tooling.agent_workflow import gate_dispatch
        args=Namespace(id='stale-author',central_worktree=str(self.root/'MoonStation14-agent-05'),
                       origin_session='session',server='http://unused',wait_timeout=1)
        with mock.patch.object(wf,'gate_lock',return_value=mock.Mock(close=mock.Mock())), \
             mock.patch.object(gate_dispatch,'_request',side_effect=AssertionError('API must not be called')), \
             mock.patch.object(gate_dispatch,'_register_and_start_observer'):
            result=gate_dispatch.request_gate(args,self.root/'work/agent_exchange',workflow=wf)
        self.assertEqual(result['status'],'requested')
        saved=json.loads((self.root/'work/agent_exchange/candidates/stale-author.json').read_text())
        self.assertEqual(saved['gate_request']['origin_session_id'],'session')

    def test_stale_candidate_diagnostic_names_changed_dirty_path(self):
        self.cli('submit','stale-path','--agent','author','--sprint','sprint-a','--base-ref',self.base,
                 '--build-identity','fixture','--steps','not run','--unverified','runtime not tested')
        candidate=json.loads((self.root/'work/agent_exchange/candidates/stale-path.json').read_text())
        (self.repo/'seed.txt').write_text('changed after submission')
        with self.assertRaisesRegex(ValueError,r'candidate stale.*seed\.txt.*new candidate revision'):
            wf.verify_candidate(candidate)

    def _visual_candidate_with_scoped_receipt(self,ident='visual-lead'):
        plan={'title':'Visual coding fixture','kind':'feature','acceptance_criteria':['review image binding'],
              'fixture':'seed.txt','manual_steps':['Inspect report'],'checks':[{'argv':[sys.executable,'-c','print("gate-ok")'],
              'rationale':'bounded stdlib fixture','covers':['review image binding']}]}
        planfile=self.root/f'{ident}-plan.json'; planfile.write_text(json.dumps(plan))
        self.cli('submit',ident,'--agent','author','--sprint','visual-sprint','--base-ref',self.base,
                 '--build-identity','python fixture','--steps','inspect scoped PNG','--unverified','Minecraft not run',
                 '--visual-review-required','--evidence','evidence.png','--gate-plan',planfile)
        candidate_path=self.root/'work/agent_exchange/candidates'/f'{ident}.json'; candidate=json.loads(candidate_path.read_text())
        folder=self.repo/'build/agent-workflow/visual-sprint/reports'; copied=folder/ident/'fidelity-reviewer/images/1.png'
        copied.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(self.repo/'evidence.png',copied)
        relative=copied.relative_to(folder).as_posix(); digest=wf.sha(copied)
        scoped={'candidate_id':ident,'snapshot_key':candidate['snapshot_key'],'reviewer':'fidelity-reviewer',
                'decision':'ready_for_owner','findings':['Scoped reviewer examined the candidate image.'],
                'viewed_images':[{'path':relative,'sha256':digest}],
                'original_images':[{'path':relative,'sha256':digest,'original_path':str((self.repo/'evidence.png').resolve()),
                                    'original_sha256':digest,'png_dimensions':[1,1]}]}
        scoped_path=folder/f'{ident}-fidelity-reviewer.json'; scoped_path.write_text(json.dumps(scoped))
        candidate['scoped_reviews']={'fidelity-reviewer':{'session_id':'mock-review','snapshot_key':candidate['snapshot_key'],
            'prompt_admitted':True,'report_path':str(scoped_path),'report_sha256':wf.sha(scoped_path),
            'original_images':scoped['original_images']}}
        wf.writejson(candidate_path,candidate)
        return candidate_path,folder,scoped_path,copied

    def test_visual_gate_report_binds_scoped_review_then_finishes_without_api(self):
        path,folder,scoped_path,scoped_copy=self._visual_candidate_with_scoped_receipt()
        self.run_checks('visual-lead')
        self.bind_completed_execution('visual-lead')
        out=self.cli('gate-report','visual-lead','--actor','integration-lead','--result','ready_for_owner',
                     '--finding','Scoped fidelity reviewer verified the candidate image and source binding.',
                     '--scoped-review','fidelity-reviewer')
        report_path=Path(out.stdout.strip()); report=json.loads(report_path.read_text())
        self.assertTrue(report['scoped_review']['integration_lead_personally_viewed'] is False)
        self.assertEqual(report['scoped_review']['inspection_by'],'fidelity-reviewer')
        self.assertTrue(report['original_images']); self.assertTrue(report['viewed_images'])
        report['evidence_summary']=wf._gate_evidence_summary(json.loads(path.read_text()),self.root/'work/agent_exchange','ready_for_owner')
        report_path.write_text(json.dumps(report))
        c=json.loads(path.read_text()); c['review']['report_sha256']=wf.sha(report_path); wf.writejson(path,c)
        c=json.loads(path.read_text()); self.assertEqual(c['review']['result'],'ready_for_owner')
        self.assertEqual(wf.verify_candidate(c)['review']['result'],'ready_for_owner')
        c['gate_request'].update(origin_session_id='visual-owner-session'); wf.writejson(path,c)
        from tooling.agent_workflow import gate_dispatch
        with mock.patch.dict('sys.modules', {'gate_dispatch':gate_dispatch}), \
             mock.patch.object(wf,'_ensure_owner_feedback',return_value={'status':'started'}), \
             mock.patch.object(wf,'native_notify',return_value='listener_launched; OS visibility and click are not verified'), \
             mock.patch.object(gate_dispatch,'deliver_gate_completion',return_value={'status':'unavailable'}):
            wf.cmd_gate_finish(Namespace(id='visual-lead',result='ready_for_owner',report=str(report_path)),self.root/'work/agent_exchange')
        closed=json.loads(path.read_text()); before=path.read_bytes(); report_before=report_path.read_bytes()
        lead_image=folder/'reviewed-images'/('visual-lead-1-'+wf.sha(scoped_copy)[:16]+'.png'); image_before=lead_image.read_bytes()
        # Published decisions are immutable before any writer-side report replacement.
        rejected=self.cli('gate-report','visual-lead','--actor','integration-lead','--result','needs_changes','--finding','late change',ok=False)
        self.assertIn('immutable',rejected.stderr)
        self.assertEqual(path.read_bytes(),before); self.assertEqual(report_path.read_bytes(),report_before)
        self.assertEqual(lead_image.read_bytes(),image_before)

    def test_gate_report_finish_publishes_bound_summary_and_is_idempotent(self):
        path,folder,_,_=self._visual_candidate_with_scoped_receipt('finish-report')
        self.run_checks('finish-report')
        self.bind_completed_execution('finish-report')
        c=json.loads(path.read_text()); c['gate_request'].update(origin_session_id='finish-owner'); wf.writejson(path,c)
        from tooling.agent_workflow import gate_dispatch
        with mock.patch.dict('sys.modules', {'gate_dispatch':gate_dispatch}), \
             mock.patch.object(wf,'_ensure_owner_feedback',return_value={'status':'started'}) as owner, \
             mock.patch.object(wf,'native_notify',return_value='listener_launched; OS visibility unverified') as notify, \
             mock.patch.object(gate_dispatch,'deliver_gate_completion',return_value={'status':'unavailable'}) as completion:
            from io import StringIO
            output=StringIO()
            with contextlib.redirect_stdout(output):
                wf.cmd_gate_report(Namespace(id='finish-report',actor='integration-lead',result='ready_for_owner',finding=['Independent review confirms coverage.'],scoped_review='fidelity-reviewer',finish=True),self.root/'work/agent_exchange')
            report_path=Path(output.getvalue().splitlines()[0]); report=json.loads(report_path.read_text())
            self.assertEqual(report['evidence_summary']['status'],'PASS')
            self.assertEqual(report['evidence_summary']['checks'][0]['status'],'PASS')
            self.assertEqual(json.loads(path.read_text())['gate_outcome']['result'],'ready_for_owner')
            report_bytes=report_path.read_bytes(); candidate=json.loads(path.read_text())
            outcome=candidate['gate_outcome']; owner_state=candidate.get('owner_request_generation')
            manual=(self.root/'work/agent_exchange'/'manual-tests.md').read_bytes()
            with contextlib.redirect_stdout(io.StringIO()):
                wf.cmd_gate_report(Namespace(id='finish-report',actor='integration-lead',result='ready_for_owner',finding=['Independent review confirms coverage.'],scoped_review='fidelity-reviewer',finish=True),self.root/'work/agent_exchange')
            candidate=json.loads(path.read_text())
            self.assertEqual(report_path.read_bytes(),report_bytes)
            self.assertEqual(candidate['gate_outcome'],outcome)
            self.assertEqual(candidate.get('owner_request_generation'),owner_state)
            self.assertEqual((self.root/'work/agent_exchange'/'manual-tests.md').read_bytes(),manual)
        self.assertLessEqual(owner.call_count,2)
        self.assertLessEqual(notify.call_count,1)
        self.assertLessEqual(completion.call_count,2)

    def test_gate_report_finish_rejects_not_run_without_writing_any_review_state(self):
        ident='review-reject'; path,_,_,_=self._visual_candidate_with_scoped_receipt(ident)
        from tooling.agent_workflow import gate_dispatch
        before=path.read_bytes(); folder=self.repo/'build/agent-workflow/visual-sprint/reports'
        target=folder/f'{ident}-integration-lead-review.json'
        with self.assertRaisesRegex(ValueError,'validation job|NOT_RUN|receipt'):
            wf.cmd_gate_report(Namespace(id=ident,actor='integration-lead',result='needs_changes',finding=['Coverage is incomplete despite no checks.'],scoped_review=None,finish=True),self.root/'work/agent_exchange')
        self.assertEqual(path.read_bytes(),before)
        self.assertFalse(target.exists())
        saved=json.loads(path.read_text())
        self.assertNotIn('review',saved); self.assertNotIn('gate_outcome',saved)

    def test_gate_report_finish_ready_blocks_missing_or_misbound_receipt(self):
        ident='finish-no-receipt'; path,_,_,_=self._visual_candidate_with_scoped_receipt(ident)
        self.run_checks(ident); self.bind_completed_execution(ident)
        args=Namespace(id=ident,actor='integration-lead',result='ready_for_owner',finding=['Independent review finding.'],scoped_review='fidelity-reviewer',finish=True)
        root=self.root/'work/agent_exchange'
        c=json.loads(path.read_text()); c.pop('gate_validation'); wf.writejson(path,c)
        with self.assertRaisesRegex(ValueError,'receipt'):
            wf.cmd_gate_report(args,root)
        c=json.loads(path.read_text()); c['gate_validation']={'path':str(self.repo/'wrong-receipt.json'),'sha256':'bad'}; wf.writejson(path,c)
        with self.assertRaisesRegex(ValueError,'receipt|candidate-owned'):
            wf.cmd_gate_report(args,root)

    def test_visual_failed_gate_report_retry_preserves_bound_report_and_images(self):
        path,folder,scoped_path,scoped_copy=self._visual_candidate_with_scoped_receipt('visual-retry')
        self.cli('gate-report','visual-retry','--actor','integration-lead','--result','ready_for_owner',
                 '--finding','Scoped reviewer verified the image.','--scoped-review','fidelity-reviewer')
        target=folder/'visual-retry-integration-lead-review.json'
        candidate_before=path.read_bytes(); report_before=target.read_bytes()
        scoped_report_before=scoped_path.read_bytes()
        lead_image=folder/'reviewed-images'/('visual-retry-1-'+wf.sha(scoped_copy)[:16]+'.png'); image_before=lead_image.read_bytes()
        # A reviewer identity mismatch and changed copied evidence both fail before touching prior bindings.
        self.cli('gate-report','visual-retry','--actor','integration-lead','--result','ready_for_owner',
                 '--finding','wrong reviewer','--scoped-review','review',ok=False)
        scoped_bytes=scoped_copy.read_bytes(); scoped_copy.write_bytes(b'tampered copy')
        self.cli('gate-report','visual-retry','--actor','integration-lead','--result','ready_for_owner',
                 '--finding','stale evidence','--scoped-review','fidelity-reviewer',ok=False)
        self.assertEqual(path.read_bytes(),candidate_before); self.assertEqual(target.read_bytes(),report_before)
        self.assertEqual(lead_image.read_bytes(),image_before)
        scoped_copy.write_bytes(scoped_bytes)
        scoped_report=json.loads(scoped_path.read_text()); scoped_report['candidate_id']='different-candidate'
        scoped_path.write_text(json.dumps(scoped_report))
        identity_candidate=json.loads(candidate_before); identity_candidate['scoped_reviews']['fidelity-reviewer']['report_sha256']=wf.sha(scoped_path)
        wf.writejson(path,identity_candidate)
        self.cli('gate-report','visual-retry','--actor','integration-lead','--result','ready_for_owner',
                 '--finding','identity mismatch','--scoped-review','fidelity-reviewer',ok=False)
        self.assertEqual(target.read_bytes(),report_before)
        self.assertEqual(lead_image.read_bytes(),image_before)
        scoped_path.write_bytes(scoped_report_before)
        path.write_bytes(candidate_before)
        scoped_path.write_bytes(scoped_report_before+b' ')
        # Receipt hash mismatch remains a rejection until the durable binding is current again.
        self.cli('gate-report','visual-retry','--actor','integration-lead','--result','ready_for_owner',
                 '--finding','stale receipt hash','--scoped-review','fidelity-reviewer',ok=False)
        self.assertEqual(path.read_bytes(),candidate_before); self.assertEqual(target.read_bytes(),report_before)
        scoped_path.write_bytes(scoped_report_before)
        source=self.repo/'evidence.png'; source_bytes=source.read_bytes(); source.write_bytes(b'tampered original')
        self.cli('gate-report','visual-retry','--actor','integration-lead','--result','ready_for_owner',
                 '--finding','stale source image','--scoped-review','fidelity-reviewer',ok=False)
        source.write_bytes(source_bytes)
        self.cli('gate-report','visual-retry','--actor','integration-lead','--result','needs_changes',
                 '--finding','decision differs from scoped review','--scoped-review','fidelity-reviewer',ok=False)
        blank=self.cli('gate-report','visual-retry','--actor','integration-lead','--result','ready_for_owner',
                       '--finding',' ', '--scoped-review','fidelity-reviewer',ok=False)
        self.assertNotEqual(blank.returncode,0)
        self.assertEqual(path.read_bytes(),candidate_before); self.assertEqual(target.read_bytes(),report_before)
        self.assertEqual(lead_image.read_bytes(),image_before)

    def test_gate_report_rejects_owner_disposition_without_writing(self):
        self.cli('submit','lead-owner','--agent','author','--sprint','sprint-owner','--base-ref',self.base,
                 '--build-identity','fixture','--steps','not run','--unverified','runtime not tested')
        path=self.root/'work/agent_exchange/candidates/lead-owner.json'; c=json.loads(path.read_text())
        c['owner_decision']={'decision':'deferred'}; wf.writejson(path,c); before=path.read_bytes()
        folder=self.repo/'build/agent-workflow/sprint-owner/reports'
        self.cli('gate-report','lead-owner','--actor','integration-lead','--result','needs_changes',
                 '--finding','late',ok=False)
        self.assertEqual(path.read_bytes(),before); self.assertFalse(folder.exists())

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
        self.assertIn('code unreviewed: STALE/BLOCKED (owner_deferred recorded)',self.cli('status','--verify').stdout)


if __name__=='__main__': unittest.main()
