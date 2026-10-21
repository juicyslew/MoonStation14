import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

import workflow_coordinator_package as package


class PackageTests(unittest.TestCase):
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
            result=package.publish(stage,receipt,root/"published.json",pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual("applied",result["status"])
            for key in selected_keys:self.assertEqual((package.ROOT/key).read_bytes(),(shared/key).read_bytes())
            self.assertEqual(baseline_dirty,(shared/dirty_key).read_bytes())

    def test_explicit_inactive_manifest_and_stage_collision(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as directory:
            root = Path(directory)
            shared = root / "shared"
            shared.mkdir()
            receipt = root / "receipt.json"
            receipt.write_text(json.dumps({"files": [], "source_hashes": {}, "shared_root": str(shared)}), encoding="utf-8")
            stage = root / "stage"
            result = package.build(stage, receipt)
            self.assertEqual(len(package.SOURCES) + len(package.ASSETS) + len(package.ROOT_ASSETS) + len(json.loads((package.ROOT / "docs/development-workflow-redesign/package/package-docs.json").read_text())["files"]) + 1, len(result["files"]))
            names = {Path(key).name for key in result["files"]}
            self.assertIn("workflow_scope.py", names)
            self.assertIn("workflow_coordinator_http_mcp.py", names)
            self.assertNotIn("workflow_observer.py", names)
            self.assertNotIn("workflow.py", names)
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
