import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import workflow_coordinator_package as package


class PackageTests(unittest.TestCase):
    def test_opencode_config_helper_is_packaged_and_imports_from_installed_tree(self):
        import hashlib
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as directory:
            root=Path(directory); shared=root/"shared"; shared.mkdir()
            receipt=root/"receipt.json"; receipt.write_text(json.dumps({"files":[],"source_hashes":{},"shared_root":str(shared)}))
            stage=root/"stage"; manifest=package.build(stage,receipt)
            key="tooling/agent_workflow/workflow_opencode_config.py"
            self.assertIn(key,manifest["files"])
            staged=stage/manifest["files"][key]["target"]
            self.assertEqual(hashlib.sha256((package.ROOT/key).read_bytes()).hexdigest(),hashlib.sha256(staged.read_bytes()).hexdigest())
            installed=shared/key; installed.parent.mkdir(parents=True,exist_ok=True); installed.write_bytes(staged.read_bytes())
            script=("import sys; sys.path.insert(0, r'"+str(shared/"tooling/agent_workflow")+
                    "'); import workflow_opencode_config as m; print(m.__file__)")
            result=subprocess.run([sys.executable,"-I","-c",script],cwd=root,capture_output=True,text=True,check=True)
            self.assertEqual(installed.resolve(),Path(result.stdout.strip()).resolve())

    def test_shared_config_plan_builds_and_publishes_receipt_owned_rows(self):
        import hashlib
        import workflow_coordinator_bindings as bindings
        import workflow_opencode_config as oc
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as directory:
            base=Path(directory); main=base/"MoonStation14"; main.mkdir(); shared=base/"shared"; shared.mkdir()
            template=(package.ROOT/"tooling/opencode/shared-config.jsonc").read_bytes()
            template_path=main/"tooling/opencode/shared-config.jsonc"; template_path.parent.mkdir(parents=True); template_path.write_bytes(template)
            variables={"shared_root":shared.as_posix(),"workspace_parent":main.parent.as_posix(),"ss14_root":(Path.home()/"Documents/SS14-dev/space-station-14").as_posix()}
            names=["main",*(f"MoonStation14-agent-0{i}" for i in range(1,5))]; roots={"main":main}; rows=[]; targets=[]
            local_config={"version":1,"variables":variables,"config":{"mcp":{"servers":{"private":{"url":"keep"}}},"agents":{"implement":{"model":"private-model"}}}}
            for name in names:
                root=main if name=="main" else main.parent/name
                if name!="main": root.mkdir()
                roots[name]=root; (root/".gitignore").write_bytes(b"# retain\ncustom\n")
                (root/".opencode/workflow").mkdir(parents=True,exist_ok=True)
                config=oc.serialize_config(oc.render_config(oc.load_template(template),local_config)).encode()
                (root/"opencode.jsonc").write_bytes(config)
                subprocess.run(["git","init","-q",str(root)],check=True)
                subprocess.run(["git","-C",str(root),"add","opencode.jsonc",".gitignore"],check=True)
                subprocess.run(["git","-C",str(root),"-c","user.name=test","-c","user.email=test@local","commit","-qm","fixture"],check=True)
                ch=hashlib.sha256(config).hexdigest(); rows.append({"target":name,"path":"opencode.jsonc","new_sha256":ch,"deleted":False,"config_scope":{"preserved":"yes"}})
                targets.append({"target":name,"expected_config_sha256":ch,"expected_local_sha256":None,
                    "expected_gitignore_sha256":hashlib.sha256((root/".gitignore").read_bytes()).hexdigest(),"expected_index_digest":bindings._index_digest(root)})
            receipt=base/"previous.json"; receipt.write_text(json.dumps({"files":rows,"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            plan={"version":1,"mode":"shared-opencode-config","template":{"path":str(template_path),"sha256":hashlib.sha256(template).hexdigest()},"initialize":True,"untrack_generated":False,"targets":targets}
            planpath=base/"plan.json"; planpath.write_text(json.dumps(plan))
            stage=base/"stage"; built=package.build(stage,receipt,binding_plan=planpath)
            pointer=main/package.POINTER_RELATIVE; pointer.parent.mkdir(parents=True,exist_ok=True); pointer.write_text(json.dumps({"receipt":str(receipt)}))
            result=package.publish(stage,receipt,base/"published.json",pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual("applied",result["status"])
            for name in names:
                configrow=next(r for r in result["files"] if r["target"]==name and r["path"]=="opencode.jsonc")
                overlayrow=next(r for r in result["files"] if r["target"]==name and r["path"]==".opencode/workflow/opencode-local.json")
                self.assertEqual(configrow["config_scope"]["local_overlay"]["sha256"],overlayrow["new_sha256"])
                self.assertEqual("private-model",json.loads((roots[name]/"opencode.jsonc").read_text())["agents"]["implement"]["model"])
                self.assertTrue((roots[name]/".gitignore").read_text().endswith("/.opencode/workflow/\n"))

    def test_preserve_bindings_rejects_drift_in_config_scoped_overlay(self):
        import hashlib
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as directory:
            root=Path(directory); shared=root/"shared"; shared.mkdir(); main=root/"MoonStation14"; main.mkdir()
            cfg=main/"opencode.jsonc"; cfg.write_bytes(b'{"agents":{}}')
            overlay=main/".opencode/workflow/opencode-local.json"; overlay.parent.mkdir(parents=True); overlay.write_bytes(b'{"version":1}\n')
            ch=hashlib.sha256(cfg.read_bytes()).hexdigest(); oh=hashlib.sha256(overlay.read_bytes()).hexdigest()
            receipt=root/"receipt.json"; receipt.write_text(json.dumps({"files":[
                {"target":"main","path":"opencode.jsonc","new_sha256":ch,"deleted":False,
                 "config_scope":{"local_overlay":{"path":".opencode/workflow/opencode-local.json","sha256":oh}}},
                {"target":"main","path":".opencode/workflow/opencode-local.json","new_sha256":oh,"deleted":False}],
                "source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            overlay.write_bytes(b'{"version":2}\n')
            with self.assertRaisesRegex(ValueError,"receipt-owned local overlay changed"):
                package.build(root/"stage",receipt,preserve_bindings=True)

    def test_reviewer_search_plan_build_validate_and_publish_receipt_owned_main(self):
        import hashlib
        import workflow_coordinator_bindings as bindings
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as directory:
            root=Path(directory);shared=root/'shared';shared.mkdir();main=root/'MoonStation14';main.mkdir();receipt=root/'receipt.json'
            config={'model':{'model':'keep'},'mcp':{'servers':{'custom':{'command':['keep']}}},'system':'keep','agents':{}}
            readroot=str(main).replace('\\','/')+'/**'
            for agent in bindings.REVIEWER_SEARCH_AGENTS:
                config['agents'][agent]={'permissions':[
                    {'action':a,'resource':readroot,'effect':'allow'} for a in ('read','list','glob','grep')]+
                    [{'action':'external_directory','resource':readroot,'effect':'allow'},
                     {'action':'edit','resource':'*','effect':'deny'}, {'action':'shell','resource':'*','effect':'deny'}]}
            configpath=main/'opencode.jsonc';configpath.write_text(json.dumps(config,indent=2)+'\n')
            # Bootstrap the complete installed receipt baseline, then stage the narrow reviewed source.
            receipt.write_text(json.dumps({'files':[{'target':'main','path':'opencode.jsonc','new_sha256':hashlib.sha256(configpath.read_bytes()).hexdigest(),'deleted':False}],
                'source_hashes':{},'shared_root':str(shared),'main_root':str(main)}))
            bootstrap=root/'bootstrap';boot=package.build(bootstrap,receipt)
            rows=[]
            for key,item in boot['files'].items():
                dest=shared/key;dest.parent.mkdir(parents=True,exist_ok=True);data=(bootstrap/item['target']).read_bytes();dest.write_bytes(data)
                rows.append({'target':'shared','path':key,'new_sha256':hashlib.sha256(data).hexdigest(),'deleted':False})
            confighash=hashlib.sha256(configpath.read_bytes()).hexdigest()
            rows.append({'target':'main','path':'opencode.jsonc','new_sha256':confighash,'deleted':False})
            receipt.write_text(json.dumps({'files':rows,'source_hashes':{},'shared_root':str(shared),'main_root':str(main)}))
            generator='tooling/agent_workflow/workflow_coordinator_bindings.py'
            selected={generator:hashlib.sha256((package.ROOT/generator).read_bytes()).hexdigest()}
            plan=root/'reviewer-search-plan.json';plan.write_text(json.dumps({'version':1,'mode':'reviewer-search-permissions',
                'targets':[{'target':'main','expected_config_sha256':confighash}]}))
            stage=root/'candidate-stage'
            manifest=package.build(stage,receipt,binding_plan=plan,reviewed_source_hashes=selected)
            self.assertEqual('planned-reviewer-search-permissions',manifest['bindings']['status'])
            stagedbinding=json.loads((stage/manifest['bindings']['manifest']).read_text())
            stagedpath=stage/stagedbinding['files']['main/opencode.jsonc']['staged_path']
            result=json.loads(stagedpath.read_text())
            for agent in bindings.REVIEWER_SEARCH_AGENTS:
                before=config['agents'][agent]['permissions'];after=result['agents'][agent]['permissions']
                self.assertEqual([{'action':a,'resource':'*','effect':'allow'} for a in ('glob','grep')],
                    [r for r in after if r['action'] in ('glob','grep')])
                self.assertEqual([r for r in before if r['action'] not in ('glob','grep')],
                    [r for r in after if r['action'] not in ('glob','grep')])
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True,exist_ok=True)
            pointer.write_text(json.dumps({'receipt':str(receipt)}));before_pointer=hashlib.sha256(pointer.read_bytes()).hexdigest()
            published=package.publish(stage,receipt,root/'published-receipt.json',pointer_path=pointer,expected_pointer_sha256=before_pointer)
            self.assertEqual('applied',published['status'])
            for key in selected:self.assertEqual((package.ROOT/key).read_bytes(),(shared/key).read_bytes())
            live=json.loads(configpath.read_text())
            self.assertEqual(result['agents'][bindings.REVIEWER_SEARCH_AGENTS[0]]['permissions'],live['agents'][bindings.REVIEWER_SEARCH_AGENTS[0]]['permissions'])

    def test_reviewed_selection_uses_installed_receipt_baseline_for_unselected_sources(self):
        import hashlib
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as directory:
            root=Path(directory);shared=root/"shared";shared.mkdir();main=root/"MoonStation14";main.mkdir();receipt=root/"receipt.json"
            main_config=main/"opencode.jsonc";main_config.write_text('{"agents":{}}')
            receipt.write_text(json.dumps({"files":[{"target":"main","path":"opencode.jsonc","new_sha256":hashlib.sha256(main_config.read_bytes()).hexdigest(),"deleted":False}],"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            bootstrap=root/"bootstrap";bootstrap_manifest=package.build(bootstrap,receipt)
            dirty_key="tooling/agent_workflow/workflow_result_delivery.py"
            selected_keys={f"tooling/agent_workflow/{n}" for n in package.SOURCES[:4]}
            rows=[];baseline_dirty=b"installed receipt-verified baseline, not dirty checkout\n"
            for key,item in bootstrap_manifest["files"].items():
                dest=shared/key;dest.parent.mkdir(parents=True,exist_ok=True)
                data=(baseline_dirty if key==dirty_key else (b"old installed runtime\n" if key in selected_keys else (bootstrap/item["target"]).read_bytes()))
                dest.write_bytes(data);rows.append({"target":"shared","path":key,"new_sha256":hashlib.sha256(data).hexdigest(),"deleted":False})
            rows.append({"target":"main","path":"opencode.jsonc","new_sha256":hashlib.sha256(main_config.read_bytes()).hexdigest(),"deleted":False})
            receipt.write_text(json.dumps({"files":rows,"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            selected={key:hashlib.sha256((package.ROOT/key).read_bytes()).hexdigest() for key in selected_keys}
            stage=root/"selected-stage";manifest=package.build(stage,receipt,preserve_bindings=True,reviewed_source_hashes=selected)
            self.assertEqual(baseline_dirty,(stage/dirty_key).read_bytes())
            self.assertEqual(baseline_dirty,(shared/dirty_key).read_bytes())
            for key in selected_keys:self.assertEqual((package.ROOT/key).read_bytes(),(stage/key).read_bytes())
            with self.assertRaisesRegex(ValueError,"reviewed source hash mismatch"):
                package.build(root/"wrong-hash",receipt,reviewed_source_hashes={next(iter(selected_keys)):"0"*64})
            unowned=json.loads(receipt.read_text());unowned["files"]=[r for r in unowned["files"] if r["path"]!=dirty_key]
            receipt.write_text(json.dumps(unowned))
            with self.assertRaisesRegex(ValueError,"lacks receipt-verified installed baseline"):
                package.build(root/"unowned-preserve",receipt,reviewed_source_hashes=selected)
            receipt.write_text(json.dumps({"files":rows,"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True);pointer.write_text(json.dumps({"receipt":str(receipt)}))
            durable=Path(os.environ["LOCALAPPDATA"])/"MoonStation14/workflow"
            durable.mkdir(parents=True,exist_ok=True)
            with tempfile.TemporaryDirectory(prefix="package-publish-fixture-",dir=durable) as durable_fixture:
                output=Path(durable_fixture)/"published.json"
                result=package.publish(stage,receipt,output,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
                self.assertTrue(output.is_file())
                self.assertEqual(output.resolve(),Path(json.loads(pointer.read_text())["receipt"]).resolve())
            self.assertEqual("applied",result["status"])
            for key in selected_keys:self.assertEqual((package.ROOT/key).read_bytes(),(shared/key).read_bytes())
            self.assertEqual(baseline_dirty,(shared/dirty_key).read_bytes())

    def test_reviewed_runtime_alias_and_cli_are_published_and_installed_runtime_runs(self):
        import hashlib
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as directory:
            root=Path(directory);shared=root/"shared";shared.mkdir();main=root/"MoonStation14";main.mkdir();receipt=root/"receipt.json"
            config=main/"opencode.jsonc";config.write_text('{"agents":{}}')
            receipt.write_text(json.dumps({"files":[{"target":"main","path":"opencode.jsonc","new_sha256":hashlib.sha256(config.read_bytes()).hexdigest(),"deleted":False}],"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            bootstrap=root/"bootstrap";boot=package.build(bootstrap,receipt);rows=[]
            for key,item in boot["files"].items():
                dest=shared/key;dest.parent.mkdir(parents=True,exist_ok=True);data=(bootstrap/item["target"]).read_bytes();dest.write_bytes(data)
                rows.append({"target":"shared","path":key,"new_sha256":hashlib.sha256(data).hexdigest(),"deleted":False})
            rows.append({"target":"main","path":"opencode.jsonc","new_sha256":hashlib.sha256(config.read_bytes()).hexdigest(),"deleted":False})
            receipt.write_text(json.dumps({"files":rows,"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            runtime="tooling/agent_workflow/workflow_runtime.py"; cli="docs/development-workflow-redesign/package/CLI.md"
            selected={key:hashlib.sha256((package.ROOT/key).read_bytes()).hexdigest() for key in (runtime,cli)}
            stage=root/"stage";manifest=package.build(stage,receipt,preserve_bindings=True,reviewed_source_hashes=selected)
            alias="tooling/agent_workflow/workflow.py"
            self.assertIn(alias,manifest["files"])
            self.assertEqual([alias], list(package.SOURCE_ALIASES[runtime]))
            self.assertNotIn("tooling/agent_workflow/shared_workflow_runtime.py", manifest["files"])
            canonical=(package.ROOT/runtime).read_bytes()
            self.assertEqual(canonical,(stage/alias).read_bytes())
            self.assertEqual(canonical,(stage/runtime).read_bytes())
            installed_cli="docs/development-workflow/CLI.md"
            self.assertIn(installed_cli,manifest["files"])
            self.assertEqual((package.ROOT/cli).read_bytes(),(stage/manifest["files"][installed_cli]["target"]).read_bytes())
            self.assertEqual(runtime,manifest["files"][alias]["source"])
            self.assertEqual(cli,manifest["files"][installed_cli]["source"])
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True,exist_ok=True);pointer.write_text(json.dumps({"receipt":str(receipt)}))
            published=package.publish(stage,receipt,root/"published.json",pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual("applied",published["status"])
            self.assertEqual(canonical,(shared/alias).read_bytes())
            self.assertEqual((package.ROOT/cli).read_bytes(),(shared/installed_cli).read_bytes())
            result=subprocess.run([sys.executable,str(shared/alias),"--help"],capture_output=True,text=True,timeout=20)
            self.assertEqual(0,result.returncode,result.stderr)
            self.assertIn("usage",result.stdout.lower())
            (shared/alias).write_bytes(b"unowned alias modification\n")
            with self.assertRaisesRegex(ValueError,"refusing unowned/conflicting shared target"):
                package.build(root/"reject-drift",receipt)

    def test_scoped_update_omits_new_unselected_catalog_member(self):
        import hashlib
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as directory:
            root=Path(directory);shared=root/"shared";shared.mkdir();receipt=root/"receipt.json"
            main=root/"main";main.mkdir();config=main/"opencode.jsonc";config.write_text("{}")
            receipt.write_text(json.dumps({"files":[{"target":"main","path":"opencode.jsonc","new_sha256":__import__("hashlib").sha256(config.read_bytes()).hexdigest(),"deleted":False}],"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            bootstrap=root/"bootstrap";bootstrap_manifest=package.build(bootstrap,receipt)
            rows=[]
            for key,item in bootstrap_manifest["files"].items():
                live=shared/key;live.parent.mkdir(parents=True,exist_ok=True)
                data=(bootstrap/item["target"]).read_bytes();live.write_bytes(data)
                rows.append({"target":"shared","path":key,"new_sha256":hashlib.sha256(data).hexdigest(),"deleted":False})
            rows.append({"target":"main","path":"opencode.jsonc","new_sha256":hashlib.sha256(config.read_bytes()).hexdigest(),"deleted":False})
            receipt.write_text(json.dumps({"files":rows,"source_hashes":{},"shared_root":str(shared),"main_root":str(main)}))
            member="tooling/agent_workflow/new_unreviewed_catalog_member.py"
            with __import__("unittest.mock",fromlist=["patch"]).patch.object(package,"SOURCES",(*package.SOURCES,"new_unreviewed_catalog_member.py")):
                manifest=package.build(root/"stage",receipt,reviewed_source_hashes={"tooling/agent_workflow/workflow_coordinator.py":hashlib.sha256((package.ROOT/"tooling/agent_workflow/workflow_coordinator.py").read_bytes()).hexdigest()})
            self.assertNotIn(member,manifest["files"])
            self.assertFalse((root/"stage"/member).exists())

    def test_explicit_inactive_manifest_and_stage_collision(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as directory:
            root = Path(directory)
            shared = root / "shared"
            shared.mkdir()
            receipt = root / "receipt.json"
            receipt.write_text(json.dumps({"files": [], "source_hashes": {}, "shared_root": str(shared)}), encoding="utf-8")
            stage = root / "stage"
            result = package.build(stage, receipt)
            self.assertEqual(len(package.SOURCES) + len(package.ASSETS) + len(package.ROOT_ASSETS) + sum(len(v) for v in package.SOURCE_ALIASES.values()) + len(json.loads((package.ROOT / "docs/development-workflow-redesign/package/package-docs.json").read_text())["files"]) + 1, len(result["files"]))
            names = {Path(key).name for key in result["files"]}
            self.assertIn("workflow_scope.py", names)
            self.assertIn("workflow_coordinator_http_mcp.py", names)
            self.assertNotIn("workflow_observer.py", names)
            self.assertEqual(1, sum(name == "workflow.py" for name in names))
            self.assertFalse(any(name.startswith("test_") for name in names))
            self.assertEqual("workflow-coordinator-inactive", json.loads((stage / "manifest.json").read_text())["package"])
            target = stage / "tooling/agent_workflow/workflow_coordinator.py"
            target.write_text("unowned", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "refusing to overwrite"):
                package.build(stage, receipt)

    def test_shared_replacement_backup_is_receipt_bound_and_creates_have_none(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as directory:
            root=Path(directory);shared=root/"shared";shared.mkdir()
            relative="tooling/agent_workflow/workflow_coordinator.py"
            live=shared/relative;live.parent.mkdir(parents=True);old=b"receipt-owned old runtime bytes\n";live.write_bytes(old)
            old_hash=__import__("hashlib").sha256(old).hexdigest()
            receipt=root/"receipt.json";receipt.write_text(json.dumps({"files":[{"target":"shared","path":relative,"new_sha256":old_hash,"deleted":False}],"source_hashes":{},"shared_root":str(shared)}),encoding="utf-8")
            stage=root/"stage";manifest=package.build(stage,receipt)
            replacement=manifest["files"][relative]
            self.assertEqual(old_hash,replacement["old_target_sha256"])
            self.assertEqual(replacement["old_target_sha256"],replacement["backup_sha256"])
            backup=stage/replacement["backup_path"]
            self.assertEqual(old,backup.read_bytes())
            self.assertEqual(old_hash,__import__("hashlib").sha256(backup.read_bytes()).hexdigest())
            create=next(item for item in manifest["files"].values() if item["old_target_sha256"] is None)
            self.assertIsNone(create["backup_path"]);self.assertIsNone(create["backup_sha256"])

    def test_rejects_unapproved_stage(self):
        with tempfile.TemporaryDirectory() as directory:
            shared = Path(directory) / "shared"
            shared.mkdir()
            receipt = Path(directory) / "receipt.json"
            receipt.write_text(json.dumps({"files": [], "source_hashes": {}, "shared_root": str(shared)}), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "private OpenCode temp"):
                package.build(Path(directory) / "stage", receipt)

    def test_source_traversal_and_stage_junction_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "unsafe package documentation source"):
            package.safe_relative("../../private/secrets", "package documentation source")
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as directory:
            root = Path(directory); stage = root / "stage"; outside = root / "outside"
            stage.mkdir(); outside.mkdir(); (outside / "foreign.bin").write_bytes(b"protected")
            link = stage / "redirect"
            result = subprocess.run(["cmd.exe", "/c", "mklink", "/J", str(link), str(outside)], capture_output=True, text=True)
            if result.returncode:
                self.skipTest("Windows junction creation is unavailable in this fixture environment")
            with self.assertRaisesRegex(ValueError, "reparse-point"):
                package.assert_no_reparse(stage, link / "foreign.bin")
            self.assertEqual(b"protected", (outside / "foreign.bin").read_bytes())


if __name__ == "__main__":
    unittest.main()
