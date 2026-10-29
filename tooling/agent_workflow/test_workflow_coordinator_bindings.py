import hashlib
import json
import copy
import tempfile
import unittest
import subprocess
from pathlib import Path

from jsonschema import Draft202012Validator

import workflow_coordinator_bindings as bindings


class BindingPlanTests(unittest.TestCase):
    def test_shared_config_plan_refresh_flag_is_optional_and_strictly_boolean(self):
        plan = {"version": 1, "mode": "shared-opencode-config", "template": {"path": "C:/template.jsonc", "sha256": "a" * 64},
                "initialize": False, "untrack_generated": False,
                "targets": [{"target": name, "expected_config_sha256": "b" * 64,
                             "expected_local_sha256": "c" * 64, "expected_gitignore_sha256": "d" * 64,
                             "expected_index_digest": "e" * 64}
                            for name in ("main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5)))]}
        self.assertFalse(bindings.validate_shared_config_plan_shape(plan).get("refresh_reviewer_instructions", False))
        enabled = {**plan, "refresh_reviewer_instructions": True}
        self.assertTrue(bindings.validate_shared_config_plan_shape(enabled)["refresh_reviewer_instructions"])
        for bad in (1, "true", None):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                bindings.validate_shared_config_plan_shape({**plan, "refresh_reviewer_instructions": bad})
        with self.assertRaises(ValueError):
            bindings.validate_shared_config_plan_shape({**plan, "refresh_everything": True})

    def test_reviewer_instruction_refresh_changes_only_four_existing_system_fields(self):
        agents = {f"workflow-reviewer-{role}": {"system": "old", "model": "private-model",
                  "permissions": [{"action": "edit", "resource": "private", "effect": "deny"}],
                  "request": {"reasoningEffort": "high"}} for role in bindings.REVIEW_ROLES}
        agents["fidelity-reviewer"] = {"system": "unmanaged"}
        original = {"version": 1, "variables": {"private": "value"}, "config": {"agents": agents,
                    "mcp": {"servers": {"bound": {"token": "unchanged"}}}}}
        refreshed = bindings._refresh_reviewer_instructions(original)
        expected = copy.deepcopy(original)
        for role in bindings.REVIEW_ROLES:
            expected["config"]["agents"][f"workflow-reviewer-{role}"]["system"] = bindings._reviewer_system(role, "")
        self.assertEqual(refreshed, expected)
        self.assertNotEqual(refreshed, original)
        for role in bindings.REVIEW_ROLES:
            with self.subTest(role=role):
                self.assertEqual(refreshed["config"]["agents"][f"workflow-reviewer-{role}"]["system"], bindings._reviewer_system(role, ""))
        no_managed = {"version": 1, "variables": {}, "config": {"agents": {"implement": {"model": "unchanged"}}}}
        self.assertEqual(bindings._refresh_reviewer_instructions(no_managed), no_managed)
        missing = copy.deepcopy(original)
        del missing["config"]["agents"]["workflow-reviewer-fidelity"]
        with self.assertRaises(ValueError):
            bindings._refresh_reviewer_instructions(missing)

    def test_reviewer_system_uses_supported_paged_inputs_and_inline_submission(self):
        for role in bindings.REVIEW_ROLES:
            prompt = bindings._reviewer_system(role, "C:/private/reviewer")
            self.assertIn("input_ref='review_guide', input_offset=0", prompt)
            self.assertIn("next_offset until eof", prompt)
            self.assertIn("input_ref=<assigned artifact ID>", prompt)
            self.assertIn("complete_review(result=<semantic review result>", prompt)
            self.assertIn("report_error(error=<semantic task error>", prompt)
            self.assertIn("task_id is required", prompt)
            self.assertIn("Omit contract_version, run_id, evaluation_generation, assignment_generation, and input_digest", prompt)
            self.assertIn("Do not use result_path/error_path", prompt)
            self.assertNotIn("must write", prompt)
            self.assertIn("Every tool call must include your assigned session_id", prompt)
            self.assertIn("MISSING_EXECUTION_EVIDENCE", prompt)
            self.assertIn("current bound PASS receipt exists", prompt)
            self.assertIn("concrete untested branch or scope gap", prompt)

    def test_author_binding_refreshes_owned_shared_overlay_and_future_template_keeps_entry(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp); main=base/'MoonStation14';main.mkdir();root=base/'MoonStation14-agent-01';shared=base/'shared';shared.mkdir()
            coordinator,coord_path,_,config_path,_,_,target,rows=self.setup_target(root,[("author","orchestrate-ms14")])
            template_path=main/'tooling/opencode/shared-config.jsonc';template_path.parent.mkdir(parents=True)
            source_template=(Path(__file__).resolve().parents[1]/'opencode/shared-config.jsonc').read_bytes();template_path.write_bytes(source_template)
            import workflow_opencode_config as oc
            variables={'shared_root':shared.as_posix(),'workspace_parent':main.parent.as_posix(),'ss14_root':(Path.home()/'Documents/SS14-dev/space-station-14').as_posix()}
            template=oc.load_template(source_template)
            overlay={'version':1,'variables':variables,'config':{'mcp':{'servers':{'local-credential':{'url':'token-ref'}}},'agents':{'orchestrate-ms14':{'model':'private-model'}}}}
            config_path.write_bytes(oc.serialize_config(oc.render_config(template,overlay)).encode())
            overlay_path=root/'.opencode/workflow/opencode-local.json';overlay_path.parent.mkdir(parents=True);overlay_bytes=oc.serialize_config(overlay).encode();overlay_path.write_bytes(overlay_bytes)
            config_row=next(x for x in rows if x['path']=='opencode.jsonc');config_row['new_sha256']=hashlib.sha256(config_path.read_bytes()).hexdigest()
            config_row['config_scope']={'server_keys':[],'agents':[],'shared_template':{'path':str(template_path),'sha256':hashlib.sha256(source_template).hexdigest()},'local_overlay':{'path':'.opencode/workflow/opencode-local.json','sha256':hashlib.sha256(overlay_bytes).hexdigest()},'other_owned':'keep'}
            rows.append({'target':root.name,'path':'.opencode/workflow/opencode-local.json','new_sha256':hashlib.sha256(overlay_bytes).hexdigest(),'deleted':False})
            receipt={'files':rows,'_path':str(base/'receipt.json'),'_sha256':'c'*64}
            role=target['roles'][0];role['token_file']=str(root/'author.token')
            plan=base/'plan.json';plan.write_text(json.dumps({'version':1,'coordinator_endpoint':'http://127.0.0.1:49200','targets':[{'target':root.name,'coordinator_config':str(coord_path),'roles':[role]}]}))
            stage=base/'stage';stage.mkdir();result=bindings.stage_binding_plan(plan,stage,receipt,main_root=main,shared_root=shared,adapter=shared/'adapter.py',python=shared/'python.exe')
            config_stage=result['files'][f'{root.name}/opencode.jsonc'];overlay_stage=result['files'][f'{root.name}/.opencode/workflow/opencode-local.json']
            self.assertEqual(config_stage['config_scope']['other_owned'],'keep')
            self.assertEqual(config_stage['config_scope']['shared_template'],config_row['config_scope']['shared_template'])
            staged_overlay=json.loads((stage/overlay_stage['staged_path']).read_bytes())
            staged_config=json.loads((stage/config_stage['staged_path']).read_bytes())
            self.assertEqual(staged_config['agents']['orchestrate-ms14']['model'],'private-model')
            self.assertEqual(staged_config['mcp']['servers']['local-credential']['url'],'token-ref')
            self.assertIn('ms14_workflow',staged_overlay['config']['mcp']['servers'])
            self.assertEqual(staged_overlay['config']['mcp']['servers']['ms14_workflow']['url'],'http://127.0.0.1:49200/mcp/author-static')
            revised=json.loads(source_template); revised['config']['system']=(revised['config'].get('system','')+' policy refresh')
            rendered=oc.render_config(revised,staged_overlay)
            self.assertIn('ms14_workflow',rendered['mcp']['servers'])
            self.assertEqual(rendered['agents']['orchestrate-ms14']['model'],'private-model')

    def test_shared_bound_normal_binding_rejects_overlay_tamper_and_pending_template(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);main=base/'MoonStation14';main.mkdir();root=base/'MoonStation14-agent-01';shared=base/'shared';shared.mkdir()
            coordinator,coord_path,_,config_path,_,_,target,rows=self.setup_target(root,[("author","orchestrate-ms14")])
            template_path=main/'tooling/opencode/shared-config.jsonc';template_path.parent.mkdir(parents=True)
            source=(Path(__file__).resolve().parents[1]/'opencode/shared-config.jsonc').read_bytes();template_path.write_bytes(source)
            import workflow_opencode_config as oc
            variables={'shared_root':shared.as_posix(),'workspace_parent':main.parent.as_posix(),'ss14_root':(Path.home()/'Documents/SS14-dev/space-station-14').as_posix()}
            overlay={'version':1,'variables':variables,'config':{}}
            config_path.write_bytes(oc.serialize_config(oc.render_config(oc.load_template(source),overlay)).encode())
            local=root/'.opencode/workflow/opencode-local.json';local.parent.mkdir(parents=True);data=oc.serialize_config(overlay).encode();local.write_bytes(data)
            row=next(x for x in rows if x['path']=='opencode.jsonc');row['new_sha256']=hashlib.sha256(config_path.read_bytes()).hexdigest();row['config_scope']={'server_keys':[],'agents':[],'shared_template':{'path':str(template_path),'sha256':hashlib.sha256(source).hexdigest()},'local_overlay':{'path':'.opencode/workflow/opencode-local.json','sha256':hashlib.sha256(data).hexdigest()}}
            rows.append({'target':root.name,'path':'.opencode/workflow/opencode-local.json','new_sha256':hashlib.sha256(data).hexdigest()})
            receipt={'files':rows,'_path':str(base/'receipt.json'),'_sha256':'d'*64};role=target['roles'][0]
            plan=base/'plan.json';plan.write_text(json.dumps({'version':1,'coordinator_endpoint':'http://127.0.0.1:49200','targets':[{'target':root.name,'coordinator_config':str(coord_path),'roles':[role]}]}))
            local.write_bytes(b'changed');next(x for x in rows if x['path']=='.opencode/workflow/opencode-local.json')['new_sha256']=hashlib.sha256(b'changed').hexdigest()
            with self.assertRaises(ValueError):
                bindings.stage_binding_plan(plan,base/'bad-overlay',receipt,main_root=main,shared_root=shared,adapter=shared/'adapter.py',python=shared/'python.exe')
            local.write_bytes(data);next(x for x in rows if x['path']=='.opencode/workflow/opencode-local.json')['new_sha256']=hashlib.sha256(data).hexdigest();template_path.write_bytes(source+b' ')
            with self.assertRaisesRegex(ValueError,'apply shared config first'):
                bindings.stage_binding_plan(plan,base/'pending-template',receipt,main_root=main,shared_root=shared,adapter=shared/'adapter.py',python=shared/'python.exe')

    def test_shared_config_stages_five_targets_then_receipt_owned_template_update(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); main=base/'MoonStation14'; main.mkdir(); shared=base/'shared'; shared.mkdir()
            template_path=main/'tooling/opencode/shared-config.jsonc'; template_path.parent.mkdir(parents=True)
            repo_template=(Path(__file__).resolve().parents[1]/'opencode/shared-config.jsonc').read_bytes()
            template_path.write_bytes(repo_template)
            template=json.loads(repo_template)
            roots={'main':main,**{f'MoonStation14-agent-0{i}':base/f'MoonStation14-agent-0{i}' for i in range(1,5)}}
            names=list(roots); rows=[]
            for name,root in roots.items():
                root.mkdir(exist_ok=True); (root/'.gitignore').write_bytes(b'# keep comment\ncustom\n')
                (root/'.opencode').mkdir(); (root/'.opencode/workflow').mkdir()
                subprocess.run(['git','init','-q',str(root)],check=True)
                import workflow_opencode_config as oc
                fixture_variables={'shared_root':shared.as_posix(),'workspace_parent':main.parent.as_posix(),'ss14_root':(Path.home()/'Documents/SS14-dev/space-station-14').as_posix()}
                agents={'implement':{'model':'private-model'}}
                if name == 'main':
                    agents.update({f'workflow-reviewer-{role}': {'system':'old reviewer instruction', 'model':'private-model', 'request':{'reasoningEffort':'high'}, 'permissions':[{'action':'edit','resource':'private','effect':'deny'}]} for role in bindings.REVIEW_ROLES})
                (root/'opencode.jsonc').write_bytes(oc.serialize_config(oc.render_config(template,{'version':1,'variables':fixture_variables,'config':{'mcp':{'servers':{'local':{'url':'private'}}},'agents':agents}})).encode())
                subprocess.run(['git','-C',str(root),'add','opencode.jsonc','.gitignore'],check=True)
                subprocess.run(['git','-C',str(root),'-c','user.name=test','-c','user.email=test@local','commit','-qm','fixture'],check=True)
                cfg=(root/'opencode.jsonc').read_bytes(); rows.append({'target':name,'path':'opencode.jsonc','new_sha256':hashlib.sha256(cfg).hexdigest(),'config_scope':{'prior':'retained'}})
            receipt_path=base/'receipt.json'; receipt={'files':rows,'_path':str(receipt_path),'_sha256':'f'*64}
            # The staging API accepts the verified receipt projection; fixture hashes bind all source bytes.
            rows_by={r['target']:r for r in rows}
            def plan_for(tpl, local_hashes=None):
                out=[]
                for name,root in roots.items():
                    cfg=(root/'opencode.jsonc').read_bytes(); ign=(root/'.gitignore').read_bytes()
                    out.append({'target':name,'expected_config_sha256':hashlib.sha256(cfg).hexdigest(),
                      'expected_local_sha256':(local_hashes or {}).get(name),
                      'expected_gitignore_sha256':hashlib.sha256(ign).hexdigest(),
                      'expected_index_digest':bindings._index_digest(root)})
                return {'version':1,'mode':'shared-opencode-config','template':{'path':str(template_path),'sha256':hashlib.sha256(tpl).hexdigest()},'initialize':True,'untrack_generated':False,'targets':out}
            planfile=base/'plan.json'; plan=plan_for(repo_template); planfile.write_text(json.dumps(plan))
            stage=base/'stage'; staged=bindings.stage_shared_config_plan(planfile,stage,receipt,main_root=main,shared_root=shared)
            self.assertEqual(len(staged['files']),15)
            self.assertEqual(set(staged['files']),{f'{n}/{p}' for n in names for p in ('opencode.jsonc','.opencode/workflow/opencode-local.json','.gitignore')})
            self.assertEqual(staged['metadata']['index_transition']['expected_index_digests'],{n:r['expected_index_digest'] for n,r in zip(names,plan['targets'])})
            for name in names:
                cfgrow=staged['files'][f'{name}/opencode.jsonc']
                self.assertEqual(cfgrow['old_sha256'],cfgrow['new_sha256'])
                self.assertEqual(cfgrow['config_scope']['prior'],'retained')
                self.assertEqual(cfgrow['config_scope']['local_overlay']['sha256'],staged['files'][f'{name}/.opencode/workflow/opencode-local.json']['new_sha256'])
            overlays={}
            for name,root in roots.items():
                original=rows_by[name]['new_sha256']; staged_cfg=(root/'opencode.jsonc').read_bytes()
                self.assertEqual(hashlib.sha256((root/'opencode.jsonc').read_bytes()).hexdigest(),original)
                self.assertIn(b'# keep comment\ncustom\n/opencode.jsonc\n/.opencode/workflow/\n',(stage/'bindings'/name/'.gitignore').read_bytes())
                local=(stage/'bindings'/name/'.opencode/workflow/opencode-local.json').read_bytes(); overlays[name]=local
                local_value=json.loads(local); self.assertEqual(local_value['variables']['shared_root'],shared.as_posix())
                self.assertEqual(local_value['config']['mcp']['servers']['local']['url'],'private')
            for name in names:
                cfg=(roots[name]/'opencode.jsonc').read_bytes()
                rows_by[name]['new_sha256']=hashlib.sha256(cfg).hexdigest()
                rows_by[name]['config_scope']=staged['files'][f'{name}/opencode.jsonc']['config_scope']
                (roots[name]/'.opencode/workflow/opencode-local.json').write_bytes(overlays[name])
                (roots[name]/'.gitignore').write_bytes((stage/'bindings'/name/'.gitignore').read_bytes())
                rows.append({'target':name,'path':'.opencode/workflow/opencode-local.json','new_sha256':hashlib.sha256(overlays[name]).hexdigest()})
            # Actual layout: MAIN has four managed reviewers; author overlays have none.
            refresh_plan=plan_for(repo_template,{n:hashlib.sha256(overlays[n]).hexdigest() for n in names})
            refresh_plan['initialize']=False; refresh_plan['refresh_reviewer_instructions']=True
            refresh_path=base/'reviewer-refresh-plan.json'; refresh_path.write_text(json.dumps(refresh_plan))
            refresh_stage=base/'reviewer-refresh-stage'
            refresh=bindings.stage_shared_config_plan(refresh_path,refresh_stage,receipt,main_root=main,shared_root=shared)
            self.assertEqual(set(refresh['files']), {'main/opencode.jsonc','main/.opencode/workflow/opencode-local.json'})
            refreshed_overlay=json.loads((refresh_stage/'bindings/main/.opencode/workflow/opencode-local.json').read_bytes())
            original_main_overlay=json.loads(overlays['main'])
            for role in bindings.REVIEW_ROLES:
                reviewer=f'workflow-reviewer-{role}'
                self.assertEqual(refreshed_overlay['config']['agents'][reviewer]['system'],bindings._reviewer_system(role,''))
                for key in ('model','request','permissions'):
                    self.assertEqual(refreshed_overlay['config']['agents'][reviewer][key],original_main_overlay['config']['agents'][reviewer][key])
            for name in names[1:]:
                self.assertEqual((refresh_stage/'bindings'/name/'.opencode/workflow/opencode-local.json').exists(),False)
            self.assertIsNone(refresh['files']['main/opencode.jsonc'].get('backup_path'))
            self.assertFalse((refresh_stage/'bindings/backups/main/opencode.jsonc').exists())
            # Model a finalized publication: both staged files now have truthful receipt ownership.
            for name in names:
                cfg=(roots[name]/'opencode.jsonc').read_bytes(); rows_by[name]['new_sha256']=hashlib.sha256(cfg).hexdigest()
                rows_by[name]['config_scope']=staged['files'][f'{name}/opencode.jsonc']['config_scope']
                (roots[name]/'.gitignore').write_bytes((stage/'bindings'/name/'.gitignore').read_bytes())
            changed=json.loads(repo_template); changed['config']['system']=(changed['config'].get('system','')+' policy-update')
            revised=json.dumps(changed,indent=2).encode(); template_path.write_bytes(revised)
            update_plan=plan_for(revised,{n:hashlib.sha256(overlays[n]).hexdigest() for n in names})
            update_plan['initialize']=False; planfile.write_text(json.dumps(update_plan))
            second=bindings.stage_shared_config_plan(planfile,base/'stage2',receipt,main_root=main,shared_root=shared)
            self.assertEqual(len(second['files']),5)
            for name in names:
                value=json.loads((base/'stage2/bindings'/name/'opencode.jsonc').read_bytes())
                self.assertIn('policy-update',value['system'])
                self.assertTrue(any(k=='mcp' for k in value))
                self.assertEqual(value['agents']['implement']['model'],'private-model')
                self.assertEqual(second['files'][f'{name}/opencode.jsonc']['config_scope']['prior'],'retained')
                self.assertEqual(bindings._index_digest(roots[name]),update_plan['targets'][names.index(name)]['expected_index_digest'])
                self.assertIsNone(second['files'][f'{name}/opencode.jsonc'].get('backup_path'))
                self.assertFalse((base/'stage2/bindings/backups'/name/'opencode.jsonc').exists())
            repeat=bindings.stage_shared_config_plan(planfile,base/'stage3',receipt,main_root=main,shared_root=shared)
            self.assertEqual({k:v['new_sha256'] for k,v in repeat['files'].items()}, {k:v['new_sha256'] for k,v in second['files'].items()})
            # Init must never treat a drifted receipt-owned output as an extraction source.
            import copy
            drift_init=copy.deepcopy(update_plan); drift_init['initialize']=True
            (roots['main']/'.opencode/workflow/opencode-local.json').unlink()
            p=base/'init-drift.json';p.write_text(json.dumps(drift_init))
            (roots['main']/'opencode.jsonc').write_text('{malformed generated output')
            with self.assertRaises(ValueError): bindings.stage_shared_config_plan(p,base/'init-drift-stage',receipt,main_root=main,shared_root=shared)
            # Restore the source preimage for subsequent focused rejection checks.
            (roots['main']/'opencode.jsonc').write_bytes((stage/'bindings/main/opencode.jsonc').read_bytes())
            (roots['main']/'.opencode/workflow/opencode-local.json').write_bytes(overlays['main'])

            def rejected(mutator):
                bad=copy.deepcopy(update_plan); mutator(bad)
                p=base/'bad-plan.json';p.write_text(json.dumps(bad))
                with self.assertRaises(ValueError): bindings.stage_shared_config_plan(p,base/('bad-'+str(len(list(base.glob('bad-stage*'))))),receipt,main_root=main,shared_root=shared)
            rejected(lambda p:p['targets'][0].update(expected_config_sha256='0'*64))
            rejected(lambda p:p['targets'][0].update(target='MoonStation14-agent-05'))
            rejected(lambda p:p['template'].update(path=str(base/'outside.jsonc')))
            (roots['main']/'.opencode/workflow/opencode-local.json').write_bytes(b'foreign')
            with self.assertRaises(ValueError): bindings.stage_shared_config_plan(planfile,base/'unknown-overlay',receipt,main_root=main,shared_root=shared)
            (roots['main']/'.opencode/workflow/opencode-local.json').write_bytes(overlays['main'])
            (roots['main']/'unrelated').write_text('index drift')
            subprocess.run(['git','-C',str(main),'add','unrelated'],check=True)
            with self.assertRaises(ValueError): bindings.stage_shared_config_plan(planfile,base/'index-drift',receipt,main_root=main,shared_root=shared)
            missing=copy.deepcopy(update_plan); missing['targets'][0]['expected_local_sha256']=None
            (roots['main']/'.opencode/workflow/opencode-local.json').unlink()
            p=base/'missing-local.json';p.write_text(json.dumps(missing))
            with self.assertRaises(ValueError): bindings.stage_shared_config_plan(p,base/'missing-local-stage',receipt,main_root=main,shared_root=shared)
            invalid=copy.deepcopy(update_plan); invalid['targets'][0]['expected_gitignore_sha256']=None
            with self.assertRaises(ValueError): bindings.validate_shared_config_plan_shape(invalid)

    def test_shared_config_plan_shape_requires_exact_five_targets(self):
        digest = "a" * 64
        plan = {"version": 1, "mode": "shared-opencode-config",
                "template": {"path": str(Path.cwd() / "tooling/opencode/shared-config.jsonc"), "sha256": digest},
                "initialize": True, "untrack_generated": True,
                "targets": [{"target": name, "expected_config_sha256": digest,
                             "expected_local_sha256": None, "expected_gitignore_sha256": digest,
                             "expected_index_digest": digest}
                            for name in ("main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5)))]}
        self.assertIs(bindings.validate_shared_config_plan_shape(plan), plan)
        with self.assertRaises(ValueError):
            bindings.validate_shared_config_plan_shape({**plan, "targets": plan["targets"] + [plan["targets"][0]]})

    def test_reviewer_search_plan_is_receipt_and_hash_bound_and_changes_only_search_rules(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); main=base/'MoonStation14'; main.mkdir(); shared=base/'shared'; shared.mkdir()
            root=str(main).replace('\\','/')+'/**'
            config={'model':{'model':'same'},'mcp':{'servers':{'x':{'command':['keep']}}},'system':'same',
                'agents':{}}
            for agent in bindings.REVIEWER_SEARCH_AGENTS:
                config['agents'][agent]={'permissions':[
                    {'action':a,'resource':root,'effect':'allow'} for a in ('read','list')]+[
                    {'action':a,'resource':root,'effect':'allow'} for a in ('glob','grep')]+[
                    {'action':'external_directory','resource':root,'effect':'allow'},
                    {'action':'edit','resource':'*','effect':'deny'}, {'action':'shell','resource':'*','effect':'deny'}]}
            raw=(json.dumps(config,indent=2)+'\n').encode(); path=main/'opencode.jsonc';path.write_bytes(raw)
            digest=hashlib.sha256(raw).hexdigest(); receipt={'files':[{'target':'main','path':'opencode.jsonc','new_sha256':digest,'deleted':False}],
                '_path':str(base/'receipt.json'),'_sha256':'a'*64}
            plan=base/'plan.json';plan.write_text(json.dumps({'version':1,'mode':'reviewer-search-permissions','targets':[{'target':'main','expected_config_sha256':digest}]}))
            stage=base/'stage';stage.mkdir()
            manifest=bindings.stage_reviewer_search_permission_plan(plan,stage,receipt,main_root=main,shared_root=shared)
            staged=json.loads((stage/manifest['files']['main/opencode.jsonc']['staged_path']).read_text())
            for agent in bindings.REVIEWER_SEARCH_AGENTS:
                before=config['agents'][agent]['permissions']; after=staged['agents'][agent]['permissions']
                self.assertEqual([{'action':a,'resource':'*','effect':'allow'} for a in ('glob','grep')],
                    [r for r in after if r['action'] in ('glob','grep')])
                self.assertEqual([r for r in before if r['action'] not in ('glob','grep')],
                    [r for r in after if r['action'] not in ('glob','grep')])
            self.assertEqual(config['model'],staged['model']);self.assertEqual(config['mcp'],staged['mcp']);self.assertEqual(config['system'],staged['system'])
            bad={'version':1,'mode':'reviewer-search-permissions','targets':[{'target':'MoonStation14-agent-01','expected_config_sha256':digest}]}
            plan.write_text(json.dumps(bad))
            with self.assertRaises(ValueError): bindings.stage_reviewer_search_permission_plan(plan,stage,receipt,main_root=main,shared_root=shared)
            bad['targets'][0]={'target':'main','expected_config_sha256':'0'*64};plan.write_text(json.dumps(bad))
            with self.assertRaises(ValueError): bindings.stage_reviewer_search_permission_plan(plan,stage,receipt,main_root=main,shared_root=shared)

    def test_reviewer_search_transform_refuses_unknown_search_and_mismatched_roots(self):
        for pattern in ('*', 'src/**', 'C:/elsewhere/**'):
            with self.subTest(pattern=pattern):
                config={'agents':{}}
                for name in bindings.REVIEWER_SEARCH_AGENTS:
                    config['agents'][name]={'permissions':[{'action':a,'resource':'C:/repo/**','effect':'allow'}
                        for a in ('read','list','glob','grep')]}
                config['agents'][bindings.REVIEWER_SEARCH_AGENTS[0]]['permissions'][2]['resource']=pattern
                with self.assertRaises(ValueError): bindings.transform_reviewer_search_permissions(config)

    def test_reviewer_search_transform_rejects_nonallow_glob_grep_rules(self):
        for effect in ('deny', 'ask'):
            with self.subTest(effect=effect):
                config={'agents':{}}
                for name in bindings.REVIEWER_SEARCH_AGENTS:
                    config['agents'][name]={'permissions':[{'action':a,'resource':'C:/repo/**','effect':'allow'}
                        for a in ('read','list','glob','grep')]}
                config['agents'][bindings.REVIEWER_SEARCH_AGENTS[0]]['permissions'].append(
                    {'action':'glob','resource':'*','effect':effect})
                with self.assertRaises(ValueError): bindings.transform_reviewer_search_permissions(config)

    def test_reviewer_pattern_permissions_do_not_misuse_path_resources(self):
        repo = Path("C:/review/main")
        external = Path("C:/review/artifacts")
        output = "C:/review/private/assigned"
        rules = bindings._reviewer_permissions(repo, "implementation", output, [str(external)])
        self.assertIn({"action":"glob", "resource":"*", "effect":"allow"}, rules)
        self.assertIn({"action":"grep", "resource":"*", "effect":"allow"}, rules)
        self.assertFalse(any(r["action"] in ("glob", "grep") and r["resource"] != "*" for r in rules))
        for action in ("read", "list"):
            self.assertIn({"action":action, "resource":repo.as_posix()+"/**", "effect":"allow"}, rules)
            self.assertIn({"action":action, "resource":external.as_posix()+"/**", "effect":"allow"}, rules)
            self.assertFalse(any(r["action"] == action and r["effect"] == "allow" and
                r["resource"] not in (repo.as_posix()+"/**", external.as_posix()+"/**") for r in rules))
        self.assertIn({"action":"external_directory", "resource":external.as_posix()+"/**", "effect":"allow"}, rules)
        self.assertIn({"action":"edit", "resource":"*", "effect":"deny"}, rules)
        self.assertIn({"action":"edit", "resource":output+"/**", "effect":"allow"}, rules)

    def test_fidelity_profile_retirement_removes_only_exact_bash_deny_lines(self):
        rules=[*sorted(bindings.KNOWN_REVIEWER_SHELL_DENIES),"*custom-build*","*role-specific*"]
        text="---\nname: fidelity-reviewer\nmodel: keep/model\npermission:\n  bash:\n"+"".join(f'    "{r}": {"deny" if r in bindings.KNOWN_REVIEWER_SHELL_DENIES or r=="*custom-build*" else "allow"}\n' for r in rules)+"  read: allow\ncolor: keep\n---\nBODY exact bytes\n"
        result,removed=bindings.retire_fidelity_shell_profile(text)
        self.assertEqual(set(bindings.KNOWN_REVIEWER_SHELL_DENIES),set(removed))
        self.assertEqual(text.splitlines(keepends=True)[0:4],result.splitlines(keepends=True)[0:4])
        self.assertIn('    "*custom-build*": deny\n',result)
        self.assertIn('    "*role-specific*": allow\n',result)
        self.assertTrue(result.endswith("  read: allow\ncolor: keep\n---\nBODY exact bytes\n"))

    def test_shell_policy_retirement_removes_only_exact_known_denies(self):
        rules = [
            {"action":"shell", "resource":"*gradlew*test*", "effect":"deny"},
            {"action":"shell", "resource":"*runServer*", "effect":"deny"},
            {"action":"shell", "resource":"*my-custom-build-command*", "effect":"deny"},
            {"action":"shell", "resource":"*gradle* test", "effect":"deny"},
            {"action":"read", "resource":"*workflow.py*", "effect":"deny"},
        ]
        source = {"agents":{"implement":{"description":"preserve", "permissions":rules},
            "custom-role":{"permissions":[{"action":"shell","resource":"*gradlew*test*","effect":"deny"}]}},
            "model":{"model":"preserve/model"}}
        result = bindings.retire_inspection_blocking_shell_rules(source)
        self.assertEqual(5, len(source["agents"]["implement"]["permissions"]))
        self.assertEqual([rules[2], rules[4]], result["agents"]["implement"]["permissions"])
        self.assertEqual(source['agents']['custom-role'],result['agents']['custom-role'])
        self.assertEqual(source["model"], result["model"])

    def test_real_main_and_agent01_to03_policy_corpus_has_only_publisher_variants_removed(self):
        roots=[bindings.ROOT,*(bindings.ROOT.parent/f'MoonStation14-agent-0{i}' for i in range(1,5))]
        legacy=bindings.KNOWN_LEGACY_SHELL_DENIES
        found=set()
        for root in roots:
            path=root/'opencode.jsonc'
            if not path.is_file():
                continue
            text,_,_=bindings._jsonc_text(path.read_bytes())
            config=bindings._json_config(text.encode())
            before=bindings.retire_inspection_blocking_shell_rules(config)
            for name in bindings.KNOWN_OLD_AGENTS:
                old=config.get('agents',{}).get(name,{})
                new=before.get('agents',{}).get(name,{})
                for rule in old.get('permissions',[]):
                    identity=tuple(rule.get(k) for k in ('action','resource','effect'))
                    if identity in legacy:
                        found.add(identity)
                        self.assertNotIn(rule,new.get('permissions',[]))
        # Owner worktrees may already contain the retired rules. When present,
        # still assert the exact known publisher corpus is recognized.
        if found:
            self.assertTrue({("shell",resource,"deny") for resource in
                ("*gradlew*build*","*gradlew*check*","*gradlew*test*")} <= found)

    def test_shell_policy_plan_stages_five_receipt_bound_targets_and_main_format_exception(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp); main=base/'MoonStation14'; shared=base/'shared'; shared.mkdir()
            names=['main',*(f'MoonStation14-agent-0{i}' for i in range(1,5))]
            rows=[]; targets=[]
            baseline=None
            for name in names:
                root=main if name=='main' else base/name
                root.mkdir()
                config={'model':{'model':'keep/me'},'mcp':{'servers':{'custom':{'command':['safe']}}},
                    'agents':{'implement':{'description':'preserve instructions','permissions':[
                        {'action':'shell','resource':'*gradlew*build*','effect':'deny'},
                        {'action':'shell','resource':'*rg*','effect':'deny'},
                        {'action':'shell','resource':'*runServer*','effect':'allow'}]}}}
                original=(json.dumps(config,indent=2)+'\n').encode()
                path=root/'opencode.jsonc'; path.write_bytes(original)
                receipt_hash=hashlib.sha256(original).hexdigest()
                if name=='main':
                    baseline=base/'main-baseline.json'; baseline.write_bytes(original)
                    pretty=(json.dumps(config,indent=2)+'\n').encode()
                    path.write_bytes(pretty)
                    expected=hashlib.sha256(pretty).hexdigest()
                    # Receipt is deliberately tied to the equivalent prior bytes.
                    receipt_hash=hashlib.sha256(baseline.read_bytes()).hexdigest()
                else: expected=receipt_hash
                rows.append({'target':name,'path':'opencode.jsonc','new_sha256':receipt_hash,'deleted':False,
                    'config_scope':{'server_keys':['ms14_workflow'],'agents':['orchestrate-ms14']}})
                targets.append({'target':name,'expected_config_sha256':expected})
            # Make MAIN a real formatting-only byte change while preserving ordered parsed content.
            main_path=main/'opencode.jsonc'; main_path.write_bytes(json.dumps(_strict:=json.loads(baseline.read_text()),separators=(',',':')).encode())
            targets[0]['expected_config_sha256']=hashlib.sha256(main_path.read_bytes()).hexdigest()
            receipt={'files':rows,'_path':str(base/'receipt.json'),'_sha256':'a'*64}
            plan={'version':1,'mode':'inspection-safe-shell','targets':targets,
                'formatting_preimages':{'main':{'path':str(baseline),'sha256':hashlib.sha256(baseline.read_bytes()).hexdigest()}}}
            plan_path=base/'plan.json'; plan_path.write_text(json.dumps(plan)); stage=base/'stage'; stage.mkdir()
            manifest=bindings.stage_shell_policy_retirement_plan(plan_path,stage,receipt,main_root=main,shared_root=shared)
            self.assertEqual(set(names),set(manifest['targets']))
            for name in names:
                item=manifest['files'][f'{name}/opencode.jsonc']
                staged=json.loads((stage/item['staged_path']).read_text())
                permissions=staged['agents']['implement']['permissions']
                self.assertEqual([{'action':'shell','resource':'*rg*','effect':'deny'},
                    {'action':'shell','resource':'*runServer*','effect':'allow'}],permissions)
                self.assertEqual('keep/me',staged['model']['model'])
                self.assertEqual('preserve instructions',staged['agents']['implement']['description'])
                self.assertIn('server_keys',item['config_scope'])
            # Changed parsed settings and an unreviewed hash drift must not be admitted.
            bad=main_path.read_bytes().replace(b'keep/me',b'changed')
            main_path.write_bytes(bad); targets[0]['expected_config_sha256']=hashlib.sha256(bad).hexdigest()
            plan_path.write_text(json.dumps(plan)); bad_stage=base/'bad-stage'; bad_stage.mkdir()
            with self.assertRaises(ValueError):
                bindings.stage_shell_policy_retirement_plan(plan_path,bad_stage,receipt,main_root=main,shared_root=shared)

    def test_shell_profile_plan_rejects_scope_path_and_hash_drift(self):
        with tempfile.TemporaryDirectory() as temp:
            base=Path(temp);main=base/"MoonStation14";shared=base/"shared";shared.mkdir()
            names=["main",*(f"MoonStation14-agent-0{i}" for i in range(1,5))];receipt_rows=[];targets=[]
            profile_bytes=b'---\nname: reviewer\npermission:\n  bash:\n    "*runServer*": deny\n---\nbody\n'
            profiles=[]
            for index,name in enumerate(names):
                root=main if name=="main" else base/name;root.mkdir()
                config=root/"opencode.jsonc";config.write_text(json.dumps({"agents":{}}));sha=hashlib.sha256(config.read_bytes()).hexdigest()
                receipt_rows.append({"target":name,"path":"opencode.jsonc","new_sha256":sha,"deleted":False})
                targets.append({"target":name,"expected_config_sha256":sha})
                if name in {f"MoonStation14-agent-0{i}" for i in range(1,4)}:
                    profile=root/".opencode/agents/fidelity-reviewer.md";profile.parent.mkdir(parents=True);profile.write_bytes(profile_bytes)
                    ph=hashlib.sha256(profile_bytes).hexdigest();path=".opencode/agents/fidelity-reviewer.md"
                    receipt_rows.append({"target":name,"path":path,"new_sha256":ph,"deleted":False})
                    profiles.append({"target":name,"path":path,"expected_sha256":ph})
            receipt={"files":receipt_rows,"_path":str(base/"receipt.json"),"_sha256":"a"*64}
            for index,bad_profile in enumerate((
                {**profiles[0],"target":"MoonStation14-agent-04"},
                {**profiles[0],"path":".opencode/agents/other.md"},
                {**profiles[0],"expected_sha256":"0"*64},
            )):
                proposed=[*profiles];proposed[0]=bad_profile
                plan=base/f"bad-{index}.json";plan.write_text(json.dumps({"version":1,"mode":"inspection-safe-shell","targets":targets,"profiles":proposed}))
                stage=base/f"stage-{index}";stage.mkdir()
                with self.subTest(profile=bad_profile),self.assertRaises(ValueError):
                    bindings.stage_shell_policy_retirement_plan(plan,stage,receipt,main_root=main,shared_root=shared)

    def test_recover_omitted_scope_from_hash_bound_stage(self):
        with tempfile.TemporaryDirectory() as temp:
            stage=Path(temp)/'stage';stage.mkdir(); target='main'; path='opencode.jsonc'
            config=b'{"fixture":true}\n'; digest=hashlib.sha256(config).hexdigest()
            scope={'server_keys':['ms14_workflow'],'agents':['orchestrate-ms14']}
            staged=stage/'bindings/main/opencode.jsonc';staged.parent.mkdir(parents=True);staged.write_bytes(config)
            binding={'files':{'main/opencode.jsonc':{'target':target,'path':path,'new_sha256':digest,'staged_path':'bindings/main/opencode.jsonc','config_scope':scope}}}
            bp=stage/'bindings/binding-manifest.json';bp.parent.mkdir(parents=True,exist_ok=True);bp.write_text(json.dumps(binding))
            manifest={'schema_version':1,'package':'workflow-coordinator-inactive','bindings':{'status':'planned-explicit-targets','manifest':'bindings/binding-manifest.json','sha256':hashlib.sha256(bp.read_bytes()).hexdigest()}}
            mp=stage/'manifest.json';mp.write_text(json.dumps(manifest))
            receipt={'stage':str(stage),'manifest_sha256':hashlib.sha256(mp.read_bytes()).hexdigest()}
            row={'target':target,'path':path,'new_sha256':digest}
            self.assertEqual(scope,bindings._recover_receipt_config_scope(receipt,row))

    def test_recover_scope_rejects_bad_hash_path_and_target_chain(self):
        with tempfile.TemporaryDirectory() as temp:
            stage=Path(temp)/'stage';stage.mkdir(); config=b'{}'; digest=hashlib.sha256(config).hexdigest()
            scope={'server_keys':['ms14_workflow'],'agents':['orchestrate-ms14']}
            staged=stage/'bindings/main/opencode.jsonc';staged.parent.mkdir(parents=True);staged.write_bytes(config)
            item={'target':'main','path':'opencode.jsonc','new_sha256':digest,'staged_path':'bindings/main/opencode.jsonc','config_scope':scope}
            bp=stage/'bindings/binding-manifest.json'; bp.write_text(json.dumps({'files':{'main/opencode.jsonc':item}}))
            bindings_meta={'status':'planned-explicit-targets','manifest':'bindings/binding-manifest.json','sha256':hashlib.sha256(bp.read_bytes()).hexdigest()}
            mp=stage/'manifest.json'; mp.write_text(json.dumps({'schema_version':1,'package':'workflow-coordinator-inactive','bindings':bindings_meta}))
            receipt={'stage':str(stage),'manifest_sha256':hashlib.sha256(mp.read_bytes()).hexdigest()}; row={'target':'main','path':'opencode.jsonc','new_sha256':digest}
            cases=[('manifest hash',None,None),('binding hash','hash',row),('unsafe binding path','path',row),('wrong target',None,{**row,'target':'MoonStation14-agent-01'})]
            for label,change,rr in cases:
                with self.subTest(label=label):
                    bindings_meta.update({'manifest':'bindings/binding-manifest.json','sha256':hashlib.sha256(bp.read_bytes()).hexdigest()})
                    if change=='hash': bindings_meta['sha256']='0'*64
                    elif change=='path': bindings_meta['manifest']='../escape.json'
                    mp.write_text(json.dumps({'schema_version':1,'package':'workflow-coordinator-inactive','bindings':bindings_meta}))
                    r={**receipt,'manifest_sha256':hashlib.sha256(mp.read_bytes()).hexdigest()}
                    if label=='manifest hash': r['manifest_sha256']='0'*64
                    with self.assertRaises(ValueError): bindings._recover_receipt_config_scope(r,rr or row)

    def test_legacy_retirement_patches_only_parsed_disabled_member(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);shared=root/'shared'
            entry={'type':'local','command':[str(shared/'.venv-workflow-mcp/Scripts/python.exe'),
                str(shared/'tooling/agent_workflow/workflow_mcp.py'),'--repo',str(root),'--profile','lead'],
                'cwd':str(root),'environment':{},'disabled':False,'codemode':False,'protocol':'legacy',
                'timeout':{'startup':15000,'catalog':15000,'execution':300000}}
            text='{\n // keep comment\n "mcp":{"servers":{"other":{"disabled":true},"ms14_workflow":'+json.dumps(entry)+',},},\n "agents":{},\n}'
            raw=text.encode();patched=bindings._retired_config_bytes(raw,root,shared)
            self.assertEqual(raw.replace(b'"disabled": false',b'"disabled": true',1),patched)
            self.assertEqual(patched,bindings._retired_config_bytes(patched,root,shared))
            broken=json.dumps({'mcp':{'servers':{'ms14_workflow':{**entry,'command':['foreign']}}},'agents':{}}).encode()
            with self.assertRaisesRegex(ValueError,'not canonical'):
                bindings._retired_config_bytes(broken,root,shared)
            # A second same-level owner name is ambiguous even if both values parse.
            duplicate=text.replace('"agents":{}', '"mcp":{"servers":{"ms14_workflow":'+json.dumps(entry)+'}}, "agents":{}')
            with self.assertRaisesRegex(ValueError,'ambiguous'):
                bindings._retired_config_bytes(duplicate.encode(),root,shared)

    def test_new_author_binding_plan_keeps_agent05_outside_four_explicit_targets(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__('os').environ['LOCALAPPDATA'])/'Temp/opencode') as temp:
            base=Path(temp);main=base/'MoonStation14';shared=base/'shared';shared.mkdir()
            roots=[main,*(base/f'MoonStation14-agent-0{i}' for i in range(1,6))]
            receipt_rows=[]; targets=[]; agent05_bytes=None
            for index,root in enumerate(roots):
                coordinator,coord_path,config,config_path,_profile,_profile_path,target,rows=self.setup_target(
                    root, [('author','orchestrate-ms14')])
                entry={'type':'local','command':[str(shared/'.venv-workflow-mcp/Scripts/python.exe'),
                    str(shared/'tooling/agent_workflow/workflow_mcp.py'),'--repo',str(root),'--profile','author'],
                    'cwd':str(root),'environment':{},'disabled':True,'codemode':False,'protocol':'legacy',
                    'timeout':{'startup':15000,'catalog':15000,'execution':300000}}
                config['mcp']['servers']['ms14_workflow']=entry
                config_path.write_text(json.dumps(config),encoding='utf-8')
                rows[0]['new_sha256']=hashlib.sha256(config_path.read_bytes()).hexdigest()
                for receipt_row in rows: receipt_row['target']='main' if index==0 else root.name
                receipt_rows.extend(rows)
                if index<5:
                    target['roles'][0]['profile_path']=None
                    targets.append({'target':'main' if index==0 else root.name,
                        'coordinator_config':str(coord_path),'roles':target['roles']})
                else:
                    agent05_bytes=config_path.read_bytes()
            receipt_path=base/'previous.json';receipt_path.write_text(json.dumps({'files':receipt_rows,
                'source_hashes':{},'main_root':str(main),'shared_root':str(shared)}),encoding='utf-8')
            receipt={'files':receipt_rows,'main_root':str(main),'shared_root':str(shared),
                '_path':str(receipt_path),'_sha256':hashlib.sha256(receipt_path.read_bytes()).hexdigest()}
            plan_path=base/'author-plan.json';plan_path.write_text(json.dumps({'version':1,
                'coordinator_endpoint':'http://127.0.0.1:49200','targets':targets}),encoding='utf-8')
            stage=base/'author-stage';stage.mkdir()
            result=bindings.stage_binding_plan(plan_path,stage,receipt,main_root=main,shared_root=shared,
                adapter=shared/'tooling/agent_workflow/workflow_coordinator_mcp.py',
                python=shared/'.venv-workflow-coordinator/Scripts/python.exe')
            self.assertEqual({'main',*(f'MoonStation14-agent-0{i}' for i in range(1,5))},set(result['targets']))
            self.assertEqual(set(f'{name}/opencode.jsonc' for name in result['targets']),
                {key for key,row in result['files'].items() if row['path']=='opencode.jsonc'})
            self.assertEqual(agent05_bytes,(roots[5]/'opencode.jsonc').read_bytes())

    def test_tool_output_opt_in_appends_narrow_read_and_edit_deny_rules(self):
        import os
        with tempfile.TemporaryDirectory(dir=Path(os.environ['LOCALAPPDATA'])/'Temp/opencode') as temp:
            base=Path(temp); root=base/'MoonStation14-agent-01'
            coordinator, coord_path, config, config_path, _, _, target, rows=self.setup_target(root,[('author','orchestrate-ms14')])
            config['agents']['orchestrate-ms14']['permissions']=[
                {'action':'external_directory','resource':'/**','effect':'deny'},
                {'action':'read','resource':str(root).replace('\\','/')+'/**','effect':'allow'}]
            config_path.write_text(json.dumps(config),encoding='utf-8');rows[0]['new_sha256']=hashlib.sha256(config_path.read_bytes()).hexdigest()
            shared=base/'shared';shared.mkdir(); receipt={'main_root':str(base/'MoonStation14'),'shared_root':str(shared),'files':rows,
                '_path':str(base/'receipt.json'),'_sha256':'a'*64}
            role=target['roles'][0];role['token_file']=str(root/'author.token')
            plan=base/'plan.json';plan.write_text(json.dumps({'version':1,'coordinator_endpoint':'http://127.0.0.1:49200',
                'targets':[{'target':root.name,'coordinator_config':str(coord_path),'roles':[role],'tool_output_read':True}]}))
            stage=base/'stage';stage.mkdir()
            result=bindings.stage_binding_plan(plan,stage,receipt,main_root=base/'MoonStation14',shared_root=shared,
                adapter=shared/'adapter.py',python=shared/'python.exe')
            planned=json.loads((stage/result['files'][f'{root.name}/opencode.jsonc']['staged_path']).read_text())
            rules=planned['agents']['orchestrate-ms14']['permissions']; root_path=(Path.home()/'.local/share/opencode/tool-output').resolve().as_posix()+'/**'
            self.assertIn({'action':'external_directory','resource':root_path,'effect':'allow'},rules)
            self.assertEqual({'action':'edit','resource':root_path,'effect':'deny'},rules[-1])
            implementation=planned['agents']['implement']['permissions']
            self.assertEqual({'action':'external_directory','resource':root_path,'effect':'allow'},implementation[-2])
            self.assertEqual({'action':'edit','resource':root_path,'effect':'deny'},implementation[-1])
            self.assertEqual(2,len([p for p in implementation if p.get('resource')==root_path]))
            self.assertTrue(result['files'][f'{root.name}/opencode.jsonc']['config_scope']['tool_output_read'])

    def setup_target(self, root: Path, roles):
        root.mkdir(parents=True, exist_ok=True)
        token = root / "author.token"; token.write_text("fixture-token", encoding="utf-8")
        context = root / "author-context.json"
        context.write_text(json.dumps({"repo": str(root), "authenticated_author": {"session_id": "author-session", "worktree": str(root), "role": "author"}}), encoding="utf-8")
        reviewer_config = {}
        for role, agent in roles:
            if role == "author": continue
            agent = bindings.REVIEWER_AGENT_PREFIX + role
            reviewer_config[role] = {"worktree": str(root), "agent": agent,
                                     "token_file": f"{role}.token", "caller": f"caller-{role}"}
        coordinator = {"database": str(root.parent / "state.sqlite"), "artifact_dir": str(root.parent / "artifacts"),
                       "opencode_server": "http://127.0.0.1:49100",
                       "bindings": [{"token_file": str(token), "caller": "author-caller", "context_file": str(context)}],
                       "reviewers": reviewer_config}
        coordinator_path = root.parent / (root.name + "-coordinator.json")
        coordinator_path.write_text(json.dumps(coordinator), encoding="utf-8")
        agents = {}
        for role, agent in roles:
            configured = agent if role == "author" else bindings.REVIEWER_AGENT_PREFIX + role
            agents[configured] = {"model": {"model": f"preserve/{configured}"}, "description": f"Keep {configured}", "permissions": []}
        agents["implement"] = {"description":"preserve implementation profile","permissions":[
            {"action":"external_directory","resource":"/**","effect":"deny"},
            {"action":"edit","resource":"/**","effect":"deny"}]}
        config = {"model": {"model": "unowned/model"}, "mcp": {"servers": {"custom": {"command": ["leave"]}}, "timeout": {"catalog": 123}}, "agents": agents}
        config_path = root / "opencode.jsonc"; config_path.write_text(json.dumps(config), encoding="utf-8")
        profile_path = root / ".opencode/agents/fidelity-reviewer.md"; profile_path.parent.mkdir(parents=True, exist_ok=True)
        profile = "---\nmodel: preserve/model\npermission:\n  \"*\": deny\n  custom: allow\n---\nKeep all prose.\n"
        profile_path.write_bytes(profile.encode("utf-8"))
        receipt_files = [{"target": root.name, "path": "opencode.jsonc", "new_sha256": hashlib.sha256(config_path.read_bytes()).hexdigest(), "deleted": False},
                         {"target": root.name, "path": ".opencode/agents/fidelity-reviewer.md", "new_sha256": hashlib.sha256(profile_path.read_bytes()).hexdigest(), "deleted": False}]
        target_roles = []
        for role, agent in roles:
            if role == "author":
                target_roles.append({"role": role, "agent": agent, "caller": "author-caller", "session_id": "author-session", "worktree": str(root), "token_file": str(token)})
            else:
                item = reviewer_config[role]
                target_roles.append({"role": role, **item, "profile_path": ".opencode/agents/fidelity-reviewer.md"})
        target = {"worktree": str(root), "roles": target_roles}
        return coordinator, coordinator_path, config, config_path, profile, profile_path, target, receipt_files

    def test_reviewer_binding_uses_dedicated_fresh_agent_without_static_session(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);coordinator,_,_,_,_,_,target,_=self.setup_target(root,[("implementation","old-generic")])
            row=target["roles"][0]
            self.assertEqual(bindings.REVIEWER_AGENT_PREFIX+"implementation",bindings._normalise_roles(coordinator,[row],root,root)[0]["agent"])
            with self.assertRaisesRegex(ValueError,"static sessions are forbidden"):
                bindings._normalise_roles(coordinator,[{**row,"session_id":"old-static"}],root,root)
            with self.assertRaisesRegex(ValueError,"trusted fresh-session configuration"):
                bindings._normalise_roles(coordinator,[{**row,"agent":"review"}],root,root)

    def test_subset_targets_author_only_reviewer_only_shared_and_distinct_worktrees(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            root = Path(temp) / "MoonStation14-agent-01"
            roles = [("author", "orchestrate-ms14"), ("implementation", "shared-reviewer"), ("parity", "shared-reviewer"), ("coverage", "coverage-reviewer"), ("fidelity", "fidelity-reviewer")]
            coordinator, coord_path, config, _, profile, _, _, _ = self.setup_target(root, roles)
            base = dict(config_base=str(coord_path.parent), profile_receipt_hashes={".opencode/agents/fidelity-reviewer.md": hashlib.sha256(profile.encode()).hexdigest()}, repo=str(root), endpoint=coordinator["opencode_server"], python="C:/python.exe", adapter="C:/adapter.py")
            # Author-only targets do not require or acquire reviewer role bindings.
            author_spec = {"worktree": str(root), "roles": [{"role": "author", "agent": "orchestrate-ms14", "caller": "author-caller", "session_id": "author-session", "worktree": str(root), "token_file": str(root / "author.token")}]}
            author_result, author_profiles = bindings.plan_explicit_target(config, coordinator, author_spec, {}, **{**base, "profile_receipt_hashes": {}})
            self.assertIn("ms14_workflow", author_result["mcp"]["servers"])
            self.assertFalse(any(k.startswith("ms14_workflow_review_") for k in author_result["mcp"]["servers"]))
            author_permissions = author_result["agents"]["orchestrate-ms14"]["permissions"]
            self.assertIn({"action":"ms14_workflow_*","resource":"*","effect":"deny"}, author_permissions)
            self.assertIn({"action":"ms14_workflow_submit","resource":"*","effect":"allow"}, author_permissions)
            self.assertIn({"action":"ms14_workflow_acknowledge_result","resource":"*","effect":"allow"}, author_permissions)
            self.assertFalse(any(p.get("action") == "tool" and str(p.get("resource","")).startswith("ms14_workflow") for p in author_permissions))
            self.assertEqual({}, author_profiles)
            # Reviewer-only target works without an author binding in this worktree.
            reviewer_only_cfg = dict(coordinator); reviewer_only_cfg["bindings"] = []
            reviewer_spec = {"worktree": str(root), "roles": [{"role": "coverage", "agent": "coverage-reviewer", **coordinator["reviewers"]["coverage"], "profile_path": ".opencode/agents/fidelity-reviewer.md"}]}
            reviewer_result, reviewer_profiles = bindings.plan_explicit_target(config, reviewer_only_cfg, reviewer_spec, {".opencode/agents/fidelity-reviewer.md": profile.encode()}, **base)
            self.assertNotIn("ms14_workflow", reviewer_result["mcp"]["servers"])
            self.assertIn("ms14_workflow_review_coverage", reviewer_result["mcp"]["servers"])
            self.assertIn("ms14_workflow_review_coverage_claim_task", reviewer_profiles[".opencode/agents/fidelity-reviewer.md"]["new_bytes"].decode())
            # Same-target bindings can share a configured agent/profile; only selected reviewer roles are granted.
            shared_spec = {"worktree": str(root), "roles": [author_spec["roles"][0], *reviewer_spec["roles"],
                {"role": "implementation", "agent": "shared-reviewer", **coordinator["reviewers"]["implementation"], "profile_path": ".opencode/agents/fidelity-reviewer.md"},
                {"role": "parity", "agent": "shared-reviewer", **coordinator["reviewers"]["parity"], "profile_path": ".opencode/agents/fidelity-reviewer.md"}]}
            shared_result, shared_profiles = bindings.plan_explicit_target(config, coordinator, shared_spec, {".opencode/agents/fidelity-reviewer.md": profile.encode()}, **base)
            self.assertEqual(5, len(shared_result["mcp"]["servers"])) # custom + author + three selected reviewers
            selected_permissions = shared_result["agents"][bindings.REVIEWER_AGENT_PREFIX+"implementation"]["permissions"]
            self.assertIn({"action":"ms14_workflow_*","resource":"*","effect":"deny"}, selected_permissions)
            self.assertIn({"action":"ms14_workflow_review_implementation_claim_task","resource":"*","effect":"allow"}, selected_permissions)
            self.assertFalse(any(p.get("action") == "tool" and str(p.get("resource","")).startswith("ms14_workflow") for p in selected_permissions))
            self.assertIn("ms14_workflow_review_parity_status", shared_profiles[".opencode/agents/fidelity-reviewer.md"]["new_bytes"].decode())
            self.assertNotIn("ms14_workflow_review_fidelity_", shared_profiles[".opencode/agents/fidelity-reviewer.md"]["new_bytes"].decode())
            # Another target gets only its own explicit role; previous target roles do not bleed across.
            root2 = Path(temp) / "MoonStation14-agent-02"
            coordinator2, coord_path2, config2, _, profile2, _, _, _ = self.setup_target(root2, [("fidelity", "fidelity-reviewer")])
            spec2 = {"worktree": str(root2), "roles": [{"role": "fidelity", "agent": "fidelity-reviewer", **coordinator2["reviewers"]["fidelity"], "profile_path": ".opencode/agents/fidelity-reviewer.md"}]}
            result2, profiles2 = bindings.plan_explicit_target(config2, coordinator2, spec2, {".opencode/agents/fidelity-reviewer.md": profile2.encode()}, config_base=str(coord_path2.parent), profile_receipt_hashes={".opencode/agents/fidelity-reviewer.md": hashlib.sha256(profile2.encode()).hexdigest()}, repo=str(root2), endpoint=coordinator2["opencode_server"], python="C:/python.exe", adapter="C:/adapter.py")
            self.assertEqual({"ms14_workflow_review_fidelity"}, {k for k in result2["mcp"]["servers"] if k.startswith("ms14_workflow_review_")})
            self.assertNotIn("ms14_workflow_review_coverage_", profiles2[".opencode/agents/fidelity-reviewer.md"]["new_bytes"].decode())

    def test_real_opencode_shapes_schema_and_profile_body_preservation(self):
        profile_path = Path(__file__).resolve().parents[2] / ".opencode/agents/fidelity-reviewer.md"
        source = profile_path.read_bytes(); text = source.decode("utf-8-sig")
        merged = bindings.merge_profile_tool_permissions(text, ["fidelity"])
        lines = "".join(f"  ms14_workflow_review_fidelity_{t}: allow\n" for t in bindings.REVIEW_TOOLS)
        self.assertEqual(text, merged.replace(lines, ""))
        # Minimal checked-in-test schema fixture; do not depend on generated build output.
        schemas = {"schemas": {
            "Mcp.LocalConfigEncoded": {"type":"object","required":["type","command","cwd","environment","disabled","codemode","protocol","timeout"]},
            "Permission.Rule": {"type":"object","required":["action","resource","effect"]},
        }}
        wrapped = {"$schema": "https://json-schema.org/draft/2020-12/schema", "components": schemas}
        server_validator = Draft202012Validator({**wrapped, "$ref": "#/components/schemas/Mcp.LocalConfigEncoded"})
        rule_validator = Draft202012Validator({**wrapped, "$ref": "#/components/schemas/Permission.Rule"})
        server_validator.validate({"type":"local","command":["python","adapter.py"],"cwd":"C:/repo","environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}})
        rule_validator.validate({"action":"ms14_workflow_review_fidelity_claim_task","resource":"*","effect":"allow"})
        rule_validator.validate({"action":"ms14_workflow_*","resource":"*","effect":"deny"})

        lead_path = Path(__file__).resolve().parents[2] / ".opencode/agents/integration-lead.md"
        lead = lead_path.read_text(encoding="utf-8")
        lead_owned = [(f"ms14_workflow_{name}", "allow") for name in ("status", "validate", "review", "finish")]
        migrated = bindings.merge_profile_tool_permissions(lead, ["fidelity"], receipt_owned_rules=lead_owned)
        self.assertNotIn("ms14_workflow_validate: allow", migrated)
        self.assertIn("ms14_workflow_review_fidelity_claim_task: allow", migrated)
        self.assertIn("Permanent, bounded integration/readiness coordinator", migrated)
        bindings.assert_profile_unowned_preserved(lead, migrated, ["fidelity"], lead_owned)
        self.assertEqual(migrated, bindings.merge_profile_tool_permissions(migrated, ["fidelity"]))
        mutated = migrated.replace("model: openai/gpt-6.1-sol", "model: unrelated/changed", 1)
        with self.assertRaisesRegex(ValueError, "unowned profile bytes"):
            bindings.assert_profile_unowned_preserved(lead, mutated, ["fidelity"], lead_owned)
        mutated_body = migrated.replace("Permanent, bounded integration/readiness coordinator", "altered prose")
        with self.assertRaisesRegex(ValueError, "unowned profile bytes"):
            bindings.assert_profile_unowned_preserved(lead, mutated_body, ["fidelity"], lead_owned)

    def test_unexpected_old_workflow_permission_fails_before_any_target_write(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            root=Path(temp)/"MoonStation14-agent-01"
            _, coordinator_path, config, config_path, _, _, target, rows=self.setup_target(root,[("author","orchestrate-ms14")])
            shared=Path(temp)/"MoonStation14-shared";shared.mkdir()
            config["mcp"]["servers"]["ms14_workflow"]={"type":"local","command":[str(shared/".venv-workflow-mcp/Scripts/python.exe"),str(shared/"tooling/agent_workflow/workflow_mcp.py"),"--repo",str(root),"--profile","author"],"cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            config["agents"]["orchestrate-ms14"]["permissions"].append({"action":"ms14_workflow_finish","resource":"*","effect":"allow"})
            config_path.write_text(json.dumps(config),encoding="utf-8");rows[0]["new_sha256"]=hashlib.sha256(config_path.read_bytes()).hexdigest()
            before=config_path.read_bytes();stage=Path(temp)/"stage";stage.mkdir()
            plan=Path(temp)/"plan.json";role=target["roles"][0]
            plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":root.name,"coordinator_config":str(coordinator_path),"roles":[role]}]}),encoding="utf-8")
            receipt={"main_root":str(root),"shared_root":str(shared),"files":rows,"_path":str(Path(temp)/"receipt.json"),"_sha256":"d"*64}
            with self.assertRaisesRegex(ValueError,"unowned/unsupported workflow permission"):
                bindings.stage_binding_plan(plan,stage,receipt,main_root=root,shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")
            self.assertEqual(before,config_path.read_bytes())
            self.assertFalse((root/".opencode/agents/fidelity-reviewer.md").read_bytes()==b"")

    def test_author_only_migrates_receipt_owned_legacy_entry_and_actual_permission_tuples(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            root = Path(temp) / "MoonStation14-agent-01"
            coordinator, coordinator_path, config, config_path, _, _, target, rows = self.setup_target(root, [("author", "orchestrate-ms14")])
            shared = Path(temp) / "MoonStation14-shared"; shared.mkdir()
            config["mcp"]["servers"]["ms14_workflow"] = {
                "type":"local", "command":[str(shared/".venv-workflow-mcp/Scripts/python.exe"),str(shared/"tooling/agent_workflow/workflow_mcp.py"),"--repo",str(root),"--profile","author"],
                "cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy",
                "timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            config_path.write_text(json.dumps(config), encoding="utf-8")
            rows[0]["new_sha256"] = hashlib.sha256(config_path.read_bytes()).hexdigest()
            receipt = {"main_root":str(root),"shared_root":str(shared),"files":rows,"_path":str(Path(temp)/"receipt.json"),"_sha256":"b"*64}
            role = target["roles"][0]
            role["token_file"] = str(root/"author.token")
            plan = Path(temp)/"author-only.json"
            plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":root.name,"coordinator_config":str(coordinator_path),"roles":[role]}]}),encoding="utf-8")
            stage=Path(temp)/"stage";stage.mkdir()
            result=bindings.stage_binding_plan(plan,stage,receipt,main_root=root,shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")
            planned=json.loads((stage/result["files"][f"{root.name}/opencode.jsonc"]["staged_path"]).read_text(encoding="utf-8"))
            self.assertEqual({"ms14_workflow","custom"},set(planned["mcp"]["servers"]))
            rules=planned["agents"]["orchestrate-ms14"]["permissions"]
            self.assertIn({"action":"ms14_workflow_*","resource":"*","effect":"deny"},rules)
            self.assertIn({"action":"ms14_workflow_acknowledge_result","resource":"*","effect":"allow"},rules)
            self.assertFalse(any(p.get("action")=="tool" for p in rules))

    def test_reviewer_only_central_target_preserves_legacy_lead_server_and_migrates_owned_lead_profile(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            base=Path(temp); main=base/"MoonStation14"; root=base/"MoonStation14-agent-05"
            coordinator, coordinator_path, config, config_path, _, _, target, rows = self.setup_target(root, [("fidelity","fidelity-reviewer")])
            shared=base/"MoonStation14-shared";shared.mkdir()
            old_lead={"type":"local","command":[str(shared/".venv-workflow-mcp/Scripts/python.exe"),str(shared/"tooling/agent_workflow/workflow_mcp.py"),"--repo",str(root),"--profile","lead"],
                      "cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            config["mcp"]["servers"]["ms14_workflow"]=old_lead
            config_path.write_text(json.dumps(config),encoding="utf-8")
            rows[0]["new_sha256"]=hashlib.sha256(config_path.read_bytes()).hexdigest()
            source_profile=Path(__file__).resolve().parents[2]/".opencode/agents/integration-lead.md"
            lead_bytes=source_profile.read_bytes(); lead_path=root/".opencode/agents/integration-lead.md"
            lead_path.parent.mkdir(parents=True,exist_ok=True);lead_path.write_bytes(lead_bytes)
            rows.append({"target":root.name,"path":".opencode/agents/integration-lead.md","new_sha256":hashlib.sha256(lead_bytes).hexdigest(),"deleted":False})
            target["roles"][0]["profile_path"]=".opencode/agents/integration-lead.md"
            plan=base/"reviewer-only.json";plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":root.name,"coordinator_config":str(coordinator_path),"roles":target["roles"]}]}),encoding="utf-8")
            stage=base/"stage";stage.mkdir()
            receipt={"main_root":str(main),"shared_root":str(shared),"files":rows,"_path":str(base/"receipt.json"),"_sha256":"c"*64}
            result=bindings.stage_binding_plan(plan,stage,receipt,main_root=main,shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")
            planned=json.loads((stage/result["files"][f"{root.name}/opencode.jsonc"]["staged_path"]).read_text(encoding="utf-8"))
            self.assertEqual(old_lead,planned["mcp"]["servers"]["ms14_workflow"]) # no author role: never replace the old lead entry
            self.assertIn("ms14_workflow_review_fidelity",planned["mcp"]["servers"])
            staged_lead=(stage/result["files"][f"{root.name}/.opencode/agents/integration-lead.md"]["staged_path"]).read_text(encoding="utf-8")
            self.assertNotIn("ms14_workflow_validate: allow",staged_lead)
            self.assertIn("ms14_workflow_review_fidelity_claim_task: allow",staged_lead)
            self.assertIn("Permanent, bounded integration/readiness coordinator",staged_lead)

    def test_receipt_owned_worktree_author_can_be_restaged(self):
        import os
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);main=base/"MoonStation14";main.mkdir()
            root=base/"MoonStation14-agent-01"
            coordinator,coord_path,config,config_path,_,profile_path,target,rows=self.setup_target(root,[("author","orchestrate-ms14")])
            shared=base/"MoonStation14-shared";shared.mkdir()
            legacy={"type":"local","command":[str(shared/".venv-workflow-mcp/Scripts/python.exe"),str(shared/"tooling/agent_workflow/workflow_mcp.py"),"--repo",str(root),"--profile","author"],"cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            config["mcp"]["servers"]["ms14_workflow"]=legacy;config_path.write_text(json.dumps(config),encoding="utf-8")
            rows[0]["new_sha256"]=hashlib.sha256(config_path.read_bytes()).hexdigest();rows[0]["target"]=root.name
            receipt_path=base/"receipt.json";receipt={"main_root":str(main),"shared_root":str(shared),"files":rows,"source_hashes":{},"_path":str(receipt_path)}
            receipt_path.write_text(json.dumps(receipt),encoding="utf-8")
            receipt["_sha256"]=hashlib.sha256(receipt_path.read_bytes()).hexdigest()
            floors={"required_packs":["package","provenance"],"selectors":{},"review_roles":[],"owner_exceptions":[],"config_overrides":[],"review_envelope_limit":100}
            context_path=base/"author-context.json"
            context={"repo":str(root),"floors":floors,"publication_receipt_path":str(receipt_path),"publication_receipt_sha256":hashlib.sha256(receipt_path.read_bytes()).hexdigest()}
            context_path.write_text(json.dumps(context),encoding="utf-8")
            token=root/"author.token";token.write_text("first")
            coordinator["bindings"]=[];coordinator["author_worktrees"]=[{"caller":"slot","token_file":str(token),"context_file":str(context_path)}]
            coord_path.write_text(json.dumps(coordinator),encoding="utf-8")
            role={"role":"author","binding_mode":"worktree","caller":"slot","token_file":str(token),"context_file":str(context_path)}
            def write_plan(path,endpoint,selected_role):
                path.write_text(json.dumps({"version":1,"coordinator_endpoint":endpoint,"targets":[{"target":root.name,"coordinator_config":str(coord_path),"roles":[selected_role]}]}),encoding="utf-8")
            plan1=base/"plan1.json";write_plan(plan1,"http://127.0.0.1:49200",role)
            stage1=base/"stage1";stage1.mkdir()
            result1=bindings.stage_binding_plan(plan1,stage1,receipt,main_root=main,shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")
            entry1=result1["files"][f"{root.name}/opencode.jsonc"]
            staged1=(stage1/entry1["staged_path"]).read_bytes();config_path.write_bytes(staged1)
            row=next(r for r in rows if r["path"]=="opencode.jsonc");row["new_sha256"]=hashlib.sha256(staged1).hexdigest();row["config_scope"]=entry1["config_scope"]
            receipt_path.write_text(json.dumps(receipt),encoding="utf-8")
            receipt["_sha256"]=hashlib.sha256(receipt_path.read_bytes()).hexdigest()
            context["publication_receipt_sha256"]=hashlib.sha256(receipt_path.read_bytes()).hexdigest();context_path.write_text(json.dumps(context),encoding="utf-8")
            token2=root/"author-next.token";token2.write_text("second")
            coordinator["author_worktrees"][0]["token_file"]=str(token2);coord_path.write_text(json.dumps(coordinator),encoding="utf-8")
            role2={**role,"token_file":str(token2)};plan2=base/"plan2.json";write_plan(plan2,"http://127.0.0.1:49201",role2)
            current=config_path.read_bytes();profile_before=profile_path.read_bytes()
            stage2=base/"stage2";stage2.mkdir()
            result2=bindings.stage_binding_plan(plan2,stage2,receipt,main_root=main,shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")
            staged2=json.loads((stage2/result2["files"][f"{root.name}/opencode.jsonc"]["staged_path"]).read_text(encoding="utf-8"))
            entry=staged2["mcp"]["servers"]["ms14_workflow"]
            self.assertEqual("http://127.0.0.1:49201/mcp/author",entry["url"])
            self.assertEqual("Bearer {file:"+str(token2)+"}",entry["headers"]["Authorization"])
            self.assertEqual(current,config_path.read_bytes());self.assertEqual(profile_before,profile_path.read_bytes())

    def test_receipt_owned_legacy_server_can_be_replaced_but_foreign_collision_fails(self):
        root = Path("C:/fixture/worktree").resolve()
        old = {"type":"local","command":["C:/shared/.venv-workflow-mcp/Scripts/python.exe", "C:/shared/tooling/agent_workflow/workflow_mcp.py", "--repo", str(root), "--profile", "author"],
               "cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
        row = {"role":"author","agent":"orchestrate-ms14","caller":"c","session_id":"s","worktree":str(root),"token_file":"C:/token"}
        cfg = {"mcp":{"servers":{"ms14_workflow":old}},"agents":{"orchestrate-ms14":{"permissions":[]}}}
        changed = bindings.merge_bindings(cfg,[row],repo=str(root),endpoint="http://127.0.0.1:49100",python="C:/python.exe",adapter="C:/adapter.py",receipt_owned_servers={"ms14_workflow":old})
        self.assertEqual("author",changed["mcp"]["servers"]["ms14_workflow"]["command"][-1])
        foreign = json.loads(json.dumps(cfg)); foreign["mcp"]["servers"]["ms14_workflow"]["command"][1] = "C:/user/custom.py"
        with self.assertRaisesRegex(ValueError,"conflicting/unowned"):
            bindings.merge_bindings(foreign,[row],repo=str(root),endpoint="http://127.0.0.1:49100",python="C:/python.exe",adapter="C:/adapter.py",receipt_owned_servers={"ms14_workflow":old})

    def test_dynamic_author_migrates_receipt_owned_legacy_lead_server_only_when_selected(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            base=Path(temp);root=base/"MoonStation14-agent-01"
            coordinator, coordinator_path, config, config_path, _, _, target, rows = self.setup_target(root, [("author","orchestrate-ms14")])
            shared=base/"MoonStation14-shared";shared.mkdir()
            old_lead={"type":"local","command":[str(shared/".venv-workflow-mcp/Scripts/python.exe"),str(shared/"tooling/agent_workflow/workflow_mcp.py"),"--repo",str(root),"--profile","lead"],
                      "cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            config["mcp"]["servers"]["ms14_workflow"]=old_lead
            # Real agent-05 shape: the lead's own selected agent has no inline workflow grants.
            config["agents"]["orchestrate-ms14"]["permissions"]=[]
            config_path.write_text(json.dumps(config),encoding="utf-8")
            rows[0]["new_sha256"]=hashlib.sha256(config_path.read_bytes()).hexdigest()
            receipt_path=base/"publication-receipt.json"
            receipt_path.write_text(json.dumps({"files":rows}),encoding="utf-8")
            token=root/"author.token"
            floors={"required_packs":["package","provenance"],"selectors":{},"review_roles":[],"owner_exceptions":[],"config_overrides":[],"review_envelope_limit":100}
            context_path=base/"dynamic-author-context.json"
            context_path.write_text(json.dumps({"repo":str(root),"floors":floors,"publication_receipt_path":str(receipt_path),"publication_receipt_sha256":hashlib.sha256(receipt_path.read_bytes()).hexdigest()}),encoding="utf-8")
            coordinator["bindings"]=[]
            coordinator["author_worktrees"]=[{"caller":"dynamic-slot","token_file":str(token),"context_file":str(context_path)}]
            coordinator_path.write_text(json.dumps(coordinator),encoding="utf-8")
            role={"role":"author","binding_mode":"worktree","caller":"dynamic-slot","token_file":str(token),"context_file":str(context_path)}
            plan=base/"plan.json";plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":root.name,"coordinator_config":str(coordinator_path),"roles":[role],"tool_output_read":True}]}),encoding="utf-8")
            stage=base/"stage";stage.mkdir()
            before=config_path.read_bytes();lead_profile=root/".opencode/agents/integration-lead.md"
            lead_profile.parent.mkdir(parents=True,exist_ok=True);lead_profile.write_bytes(b"locally edited, unselected lead profile\n")
            result=bindings.stage_binding_plan(plan,stage,{"main_root":str(base/"MoonStation14"),"shared_root":str(shared),"files":rows,"_path":str(receipt_path),"_sha256":"d"*64},main_root=base/"MoonStation14",shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")
            planned=json.loads((stage/result["files"][f"{root.name}/opencode.jsonc"]["staged_path"]).read_text(encoding="utf-8"))
            entry=planned["mcp"]["servers"]["ms14_workflow"]
            self.assertEqual("http://127.0.0.1:49200/mcp/author",entry["url"])
            self.assertEqual("Bearer {file:"+str(token)+"}",entry["headers"]["Authorization"])
            rules=planned["agents"]["orchestrate-ms14"]["permissions"]
            output_rule={"action":"external_directory","resource":(Path.home()/".local/share/opencode/tool-output").resolve().as_posix()+"/**","effect":"allow"}
            self.assertIn(output_rule,rules)
            self.assertEqual({"action":"edit","resource":output_rule["resource"],"effect":"deny"},rules[-1])
            self.assertTrue(result["files"][f"{root.name}/opencode.jsonc"]["config_scope"]["tool_output_read"])
            self.assertEqual(before,config_path.read_bytes())
            self.assertEqual(b"locally edited, unselected lead profile\n",lead_profile.read_bytes())

    def test_four_worktree_author_optins_stage_from_trusted_contexts(self):
        import os
        with tempfile.TemporaryDirectory(dir=Path(os.environ['LOCALAPPDATA'])/'Temp/opencode') as temp:
            base=Path(temp); main=base/'MoonStation14';main.mkdir();shared=base/'shared';shared.mkdir()
            roots=[base/f'MoonStation14-agent-0{i}' for i in range(1,5)]
            rows=[];targets=[]; coordinators=[]
            for index,root in enumerate(roots,1):
                _,coord_path,config,config_path,_,_,_,receipt_rows=self.setup_target(root,[('author','orchestrate-ms14')])
                for item in receipt_rows: item['target']=root.name
                rows.extend(receipt_rows)
                token=root/'author.token'; context=root/'author-context.json'
                floors={'required_packs':['package','provenance'],'selectors':{},'review_roles':[],
                    'owner_exceptions':[],'config_overrides':[],'review_envelope_limit':100}
                coordinator=json.loads(coord_path.read_text());coordinator['bindings']=[]
                coordinator['author_worktrees']=[{'caller':f'slot-{index}','token_file':str(token),'context_file':str(context)}]
                coord_path.write_text(json.dumps(coordinator));coordinators.append(coord_path)
                targets.append({'target':root.name,'coordinator_config':str(coord_path),'tool_output_read':True,
                    'roles':[{'role':'author','binding_mode':'worktree','caller':f'slot-{index}',
                    'token_file':str(token),'context_file':str(context)}]})
            receipt_path=base/'receipt.json';receipt={'files':rows,'main_root':str(main),'shared_root':str(shared),
                'source_hashes':{},'_path':str(receipt_path)}
            receipt_path.write_text(json.dumps(receipt));receipt['_sha256']=hashlib.sha256(receipt_path.read_bytes()).hexdigest()
            for index,root in enumerate(roots,1):
                context=root/'author-context.json'
                context.write_text(json.dumps({'repo':str(root),'floors':{'required_packs':['package','provenance'],
                    'selectors':{},'review_roles':[],'owner_exceptions':[],'config_overrides':[],
                    'review_envelope_limit':100},'publication_receipt_path':str(receipt_path),
                    'publication_receipt_sha256':receipt['_sha256']}))
            plan=base/'plan.json';plan.write_text(json.dumps({'version':1,'coordinator_endpoint':'http://127.0.0.1:49200','targets':targets}))
            stage=base/'stage';stage.mkdir()
            result=bindings.stage_binding_plan(plan,stage,receipt,main_root=main,shared_root=shared,
                adapter=shared/'adapter.py',python=shared/'python.exe')
            self.assertEqual({root.name for root in roots},set(result['targets']))
            scoped_rule_count=0
            for root in roots:
                config=json.loads((stage/result['files'][f'{root.name}/opencode.jsonc']['staged_path']).read_text())
                rules=config['agents']['orchestrate-ms14']['permissions']
                path=(Path.home()/'.local/share/opencode/tool-output').resolve().as_posix()+'/**'
                self.assertIn({'action':'external_directory','resource':path,'effect':'allow'},rules)
                self.assertEqual({'action':'edit','resource':path,'effect':'deny'},rules[-1])
                implementation=config['agents']['implement']['permissions']
                self.assertEqual({'action':'external_directory','resource':path,'effect':'allow'},implementation[-2])
                self.assertEqual({'action':'edit','resource':path,'effect':'deny'},implementation[-1])
                self.assertEqual(2,len([p for p in implementation if p.get('resource')==path]))
                scoped_rule_count += sum(p.get('resource')==path for agent in ('orchestrate-ms14','implement')
                                         for p in config['agents'][agent]['permissions'])
            self.assertEqual(16,scoped_rule_count)

    def test_binding_stage_records_receipt_backups_without_mutating_targets(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            root = Path(temp) / "MoonStation14-agent-01"
            roles = [("author", "orchestrate-ms14"), ("implementation", "shared-reviewer"), ("parity", "shared-reviewer")]
            coordinator, coordinator_path, _, config_path, _, profile_path, target_spec, rows = self.setup_target(root, roles)
            config_before = config_path.read_bytes(); profile_before = profile_path.read_bytes()
            shared = Path(temp) / "MoonStation14-shared"; shared.mkdir()
            receipt = {"main_root": str(root), "shared_root": str(shared), "files": rows,
                      "_path": str(Path(temp) / "receipt.json"), "_sha256": "a" * 64}
            plan_path = Path(temp) / "binding-plan.json"
            plan_path.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":root.name,"coordinator_config":str(coordinator_path),"roles":target_spec["roles"]}]}), encoding="utf-8")
            stage = Path(temp) / "stage"
            stage.mkdir()
            result = bindings.stage_binding_plan(plan_path, stage, receipt, main_root=root, shared_root=shared,
                adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py", python=shared/".venv-workflow-coordinator/Scripts/python.exe")
            self.assertEqual([root.name], result["targets"])
            self.assertEqual(config_before, config_path.read_bytes())
            self.assertEqual(profile_before, profile_path.read_bytes())
            self.assertIn(f"{root.name}/opencode.jsonc", result["files"])
            self.assertIn(f"{root.name}/.opencode/agents/fidelity-reviewer.md", result["files"])
            for entry in result["files"].values():
                backup = stage / entry["backup_path"]
                staged = stage / entry["staged_path"]
                self.assertEqual(entry["old_sha256"], hashlib.sha256(backup.read_bytes()).hexdigest())
                self.assertEqual(entry["new_sha256"], hashlib.sha256(staged.read_bytes()).hexdigest())

    def test_nonlegacy_coordinator_entry_requires_receipt_scope(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            base=Path(temp);root=base/"MoonStation14-agent-01"
            _,coord_path,config,config_path,_,_,target,rows=self.setup_target(root,[("author","orchestrate-ms14")])
            shared=base/"MoonStation14-shared";shared.mkdir()
            config["mcp"]["servers"]["ms14_workflow"]={"type":"local","command":["python","adapter.py"],"cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            config_path.write_text(json.dumps(config),encoding="utf-8")
            rows[0]["new_sha256"]=hashlib.sha256(config_path.read_bytes()).hexdigest()
            receipt={"main_root":str(root),"shared_root":str(shared),"files":rows,"_path":str(base/"receipt.json"),"_sha256":"e"*64}
            role=target["roles"][0];role["token_file"]=str(root/"author.token")
            plan=base/"plan.json";plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":root.name,"coordinator_config":str(coord_path),"roles":[role]}]}),encoding="utf-8")
            stage=base/"stage";stage.mkdir()
            with self.assertRaisesRegex(ValueError,"lacks binding scope"):
                bindings.stage_binding_plan(plan,stage,receipt,main_root=root,shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")

    def test_receipt_scope_must_name_selected_server_and_agent(self):
        with tempfile.TemporaryDirectory(dir=Path(__import__("os").environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            base=Path(temp);root=base/"MoonStation14-agent-01"
            _,coord_path,config,config_path,_,_,target,rows=self.setup_target(root,[("author","orchestrate-ms14")])
            shared=base/"MoonStation14-shared";shared.mkdir()
            config["mcp"]["servers"]["ms14_workflow"]={"type":"local","command":["python","adapter.py"],"cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            config_path.write_text(json.dumps(config),encoding="utf-8");rows[0]["new_sha256"]=hashlib.sha256(config_path.read_bytes()).hexdigest()
            rows[0]["config_scope"]={"server_keys":["ms14_workflow"],"agents":[]}
            receipt={"main_root":str(root),"shared_root":str(shared),"files":rows,"_path":str(base/"receipt.json"),"_sha256":"f"*64}
            role=target["roles"][0];role["token_file"]=str(root/"author.token")
            plan=base/"plan.json";plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":root.name,"coordinator_config":str(coord_path),"roles":[role]}]}),encoding="utf-8")
            stage=base/"stage";stage.mkdir()
            with self.assertRaisesRegex(ValueError,"lacks binding scope"):
                bindings.stage_binding_plan(plan,stage,receipt,main_root=root,shared_root=shared,adapter=shared/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=shared/".venv-workflow-coordinator/Scripts/python.exe")

    def test_remote_transport_preserves_nine_role_servers_permissions_and_file_secret(self):
        roles=[{"role":"author","binding_mode":"worktree","agent":"orchestrate-ms14","token_file":"C:/private/author.token"}]
        roles += [{"role":role,"agent":"workflow-reviewer-"+role,"token_file":f"C:/private/{role}.token"}
                  for role in bindings.REVIEW_ROLES]
        cfg={"mcp":{"servers":{}},"agents":{row["agent"]:{"permissions":[]} for row in roles}}
        result=bindings.merge_bindings(cfg,roles,repo="C:/repo",endpoint="http://127.0.0.1:49200",
            python="unused",adapter="unused",transport="http")
        self.assertEqual(5,len(result["mcp"]["servers"]))
        self.assertEqual("http://127.0.0.1:49200/mcp/author",result["mcp"]["servers"]["ms14_workflow"]["url"])
        for role in bindings.REVIEW_ROLES:
            key="ms14_workflow_review_"+role
            entry=result["mcp"]["servers"][key]
            self.assertEqual("http://127.0.0.1:49200/mcp/reviewer",entry["url"])
            self.assertEqual("Bearer {file:C:/private/"+role+".token}",entry["headers"]["Authorization"])
            self.assertIn({"action":key+"_claim_task","resource":"*","effect":"allow"},
                          result["agents"]["workflow-reviewer-"+role]["permissions"])
        for tool in bindings.AUTHOR_TOOLS:
            self.assertIn({"action":"ms14_workflow_"+tool,"resource":"*","effect":"allow"},
                          result["agents"]["orchestrate-ms14"]["permissions"])

    def test_remote_authorization_materialization_is_narrow_and_private(self):
        with tempfile.TemporaryDirectory() as temp:
            token=Path(temp)/"token";token.write_text("secret-fixture",encoding="utf-8")
            original={"type":"remote","url":"http://127.0.0.1:49200/mcp/author","oauth":False,
                "headers":{"Authorization":"Bearer {file:"+str(token)+"}"},"disabled":False,
                "codemode":False,"protocol":"legacy","timeout":{"startup":15000,"catalog":15000,"execution":300000}}
            runtime=bindings.materialize_remote_authorization(original,endpoint="http://127.0.0.1:49200",
                profile="author",token_file=str(token))
            self.assertEqual("Bearer secret-fixture",runtime["headers"]["Authorization"])
            self.assertIn("{file:",original["headers"]["Authorization"])
            self.assertNotIn("secret-fixture",json.dumps(original))
            for bad in ({**original,"url":"http://foreign/mcp/author"},
                        {**original,"headers":{"Authorization":original["headers"]["Authorization"],"X":"y"}},
                        {**original,"oauth":True}):
                with self.assertRaisesRegex(ValueError,"noncanonical"):
                    bindings.materialize_remote_authorization(bad,endpoint="http://127.0.0.1:49200",profile="author",token_file=str(token))
            with self.assertRaises(ValueError):
                bindings.materialize_remote_authorization(original,endpoint="http://127.0.0.1:49200",profile="author",token_file=str(Path(temp)/"wrong"))


if __name__ == "__main__":
    unittest.main()
