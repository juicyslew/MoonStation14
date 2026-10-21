import json
import os
import tempfile
import unittest
from pathlib import Path
import hashlib
import subprocess
import shutil
from unittest import mock

import workflow_coordinator_package as package
import workflow_coordinator_bindings as bindings
from test_workflow_coordinator_bindings import BindingPlanTests


class CoordinatorPublishTests(unittest.TestCase):
    def test_inspection_safe_shell_five_target_publish_and_tamper_guards(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp); main=base/"MoonStation14"; shared=base/"shared"; shared.mkdir()
            names=["main",*(f"MoonStation14-agent-0{i}" for i in range(1,5))]
            denied={"action":"shell","resource":"*workflow.py*","effect":"deny"}
            originals={};rows=[];target_rows=[];profile_rows=[]
            for name in names:
                root=main if name=="main" else base/name;root.mkdir()
                value={"model":{"model":"keep"},"mcp":{"servers":{"current":{"enabled":True}}},
                    "agents":{"implement":{"permissions":[denied,{"action":"read","resource":"**","effect":"allow"}]},
                        "author":{"permissions":[denied,{"action":"read","resource":"**","effect":"allow"}]}}}
                old=(json.dumps(value,indent=2)+"\n").encode()
                current=(json.dumps(value,indent=1)+"\n").encode() if name=="main" else old
                config=root/"opencode.jsonc";config.write_bytes(current);originals[name]=(old,current)
                old_hash=hashlib.sha256(old).hexdigest();current_hash=hashlib.sha256(current).hexdigest()
                rows.append({"target":name,"path":"opencode.jsonc","new_sha256":old_hash,"deleted":False,
                    "config_scope":{"coordinator":{"owned":True}}})
                target_rows.append({"target":name,"expected_config_sha256":current_hash})
                if name in {"MoonStation14-agent-01","MoonStation14-agent-02","MoonStation14-agent-03"}:
                    profile=root/".opencode/agents/fidelity-reviewer.md";profile.parent.mkdir(parents=True)
                    legacy='---\nname: fidelity-reviewer\nmodel: keep/model\npermission:\n  bash:\n'+''.join(f'    "{rule}": deny\n' for rule in sorted(bindings.KNOWN_REVIEWER_SHELL_DENIES))+'    "*custom-shell*": deny\n  read: allow\ncolor: keep\n---\nBody bytes remain\n'
                    profile.write_text(legacy);profile_hash=hashlib.sha256(profile.read_bytes()).hexdigest()
                    rows.append({"target":name,"path":".opencode/agents/fidelity-reviewer.md","new_sha256":profile_hash,"deleted":False,"profile_scope":{"publisher":{"kept":True}}})
                    profile_rows.append({"target":name,"path":".opencode/agents/fidelity-reviewer.md","expected_sha256":profile_hash})
            receipt=base/"previous.json";receipt.write_text(json.dumps({"main_root":str(main),"shared_root":str(shared),"files":rows,"source_hashes":{"prior":"b"*64}}))
            baseline=base/"main-prior.json";baseline.write_bytes(originals["main"][0])
            plan=base/"shell-plan.json";plan.write_text(json.dumps({"version":1,"mode":"inspection-safe-shell","targets":target_rows,"profiles":profile_rows,
                "formatting_preimages":{"main":{"path":str(baseline),"sha256":hashlib.sha256(baseline.read_bytes()).hexdigest()}}}))
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True);pointer.write_text(json.dumps({"receipt":str(receipt)}))
            stage=base/"stage";package.build(stage,receipt,plan)
            baseline_before=baseline.read_bytes();baseline.write_bytes(baseline_before+b" ")
            with self.assertRaisesRegex(ValueError,"formatting baseline hash"):
                package.publish(stage,receipt,base/"baseline-refused.json",pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            baseline.write_bytes(baseline_before)
            output=base/"published.json";result=package.publish(stage,receipt,output,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual("applied",result["status"])
            self.assertEqual(set(names),{a["target"] for a in result["actions"] if a["path"]=="opencode.jsonc"})
            self.assertEqual({"MoonStation14-agent-01","MoonStation14-agent-02","MoonStation14-agent-03"},
                {a["target"] for a in result["actions"] if a["path"]==".opencode/agents/fidelity-reviewer.md"})
            for name in names:
                current=main if name=="main" else base/name
                published=json.loads((current/"opencode.jsonc").read_text())['agents']
                self.assertNotIn(denied,published['implement']['permissions'])
                self.assertIn(denied,published['author']['permissions'])
                if name in {"MoonStation14-agent-01","MoonStation14-agent-02","MoonStation14-agent-03"}:
                    profile_text=(current/".opencode/agents/fidelity-reviewer.md").read_text()
                    self.assertNotIn('"*runServer*": deny',profile_text)
                    self.assertIn('"*custom-shell*": deny',profile_text)
                    self.assertIn("Body bytes remain",profile_text)

    def test_inspection_safe_shell_rejects_resealed_staged_semantic_tamper(self):
        # The semantic verifier must fail even when an attacker reseals staged hashes.
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);main=base/"MoonStation14";shared=base/"shared";shared.mkdir()
            names=["main",*(f"MoonStation14-agent-0{i}" for i in range(1,5))];rows=[];target_rows=[]
            for name in names:
                root=main if name=="main" else base/name;root.mkdir()
                raw=json.dumps({"agents":{"a":{"permissions":[{"action":"shell","resource":"*workflow.py*","effect":"deny"}]}}}).encode()
                (root/"opencode.jsonc").write_bytes(raw);sha=hashlib.sha256(raw).hexdigest()
                rows.append({"target":name,"path":"opencode.jsonc","new_sha256":sha,"deleted":False});target_rows.append({"target":name,"expected_config_sha256":sha})
            receipt=base/"previous.json";receipt.write_text(json.dumps({"main_root":str(main),"shared_root":str(shared),"files":rows,"source_hashes":{}}))
            plan=base/"plan.json";plan.write_text(json.dumps({"version":1,"mode":"inspection-safe-shell","targets":target_rows}))
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True);pointer.write_text(json.dumps({"receipt":str(receipt)}))
            stage=base/"stage";package.build(stage,receipt,plan);manifest_path=stage/"manifest.json";manifest=json.loads(manifest_path.read_text())
            bpath=stage/manifest["bindings"]["manifest"];binding=json.loads(bpath.read_text());item=binding["files"]["main/opencode.jsonc"]
            staged=stage/item["staged_path"];value=json.loads(staged.read_text());value["agents"]["a"]["permissions"].append({"action":"read","resource":"**","effect":"allow"})
            value["model"]={"model":"attacker-model"}
            staged.write_text(json.dumps(value,indent=2)+"\n");newhash=hashlib.sha256(staged.read_bytes()).hexdigest()
            item["new_sha256"]=item["staged_sha256"]=newhash;bpath.write_text(json.dumps(binding,indent=2,sort_keys=True)+"\n")
            manifest["bindings"]["sha256"]=hashlib.sha256(bpath.read_bytes()).hexdigest();manifest_path.write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n")
            with self.assertRaisesRegex(ValueError,"exact shell-policy retirement"):
                package.publish(stage,receipt,base/"refused.json",pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())

    def test_preserve_bindings_publishes_runtime_without_review_provisioning_receipts(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);main=base/"MoonStation14";shared=base/"shared";shared.mkdir()
            targets={"main":main,**{f"MoonStation14-agent-0{i}":base/f"MoonStation14-agent-0{i}" for i in range(1,6)}}
            rows=[];original={}
            for name,root in targets.items():
                root.mkdir(parents=True,exist_ok=True);config=root/"opencode.jsonc";config.write_text('{"mcp":{"servers":{"current":"keep"}}}')
                raw=config.read_bytes();original[name]=raw
                rows.append({"target":name,"path":"opencode.jsonc","new_sha256":hashlib.sha256(raw).hexdigest(),"deleted":False})
            receipt=base/"previous.json";receipt.write_text(json.dumps({"main_root":str(main),"shared_root":str(shared),"source_hashes":{},"files":rows}))
            receipt_before=receipt.read_bytes();pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True)
            pointer.write_text(json.dumps({"receipt":str(receipt)}))
            stage=base/"stage";manifest=package.build(stage,receipt,preserve_bindings=True)
            self.assertEqual("preserve-current",manifest["bindings"]["status"])
            stage2=base/"stage-changed";package.build(stage2,receipt,preserve_bindings=True)
            changed=targets["MoonStation14-agent-03"]/"opencode.jsonc";changed.write_text("user change")
            output2=base/"refused.json"
            with self.assertRaisesRegex(ValueError,"preserved receipt-owned config changed"):
                package.publish(stage2,receipt,output2,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertFalse(output2.exists());changed.write_bytes(original["MoonStation14-agent-03"])
            output=base/"applied.json";result=package.publish(stage,receipt,output,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual(71,len(result["actions"]))
            for name,root in targets.items(): self.assertEqual(original[name],(root/"opencode.jsonc").read_bytes())
            self.assertEqual(receipt_before,receipt.read_bytes())

    def test_repeat_publication_updates_runtime_from_current_receipt_preserving_bindings(self):
        from test_workflow_reviewer_profiles import DedicatedReviewerProfileTests
        helper=DedicatedReviewerProfileTests()
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp)
            roles=[("author","orchestrate-ms14"),*((role,bindings.REVIEWER_AGENT_PREFIX+role) for role in helper.ROLE_SOURCES)]
            target=helper.target_tuple(helper.fixture(base,roles))
            main,shared=target["main"],target["shared"]
            config_before=target["config_path"].read_bytes()
            runtime=shared/"tooling/agent_workflow/workflow_coordinator.py";runtime.parent.mkdir(parents=True);runtime.write_bytes(b"old receipt-owned runtime")
            target["receipt"]["files"].append({"target":"shared","path":"tooling/agent_workflow/workflow_coordinator.py","new_sha256":hashlib.sha256(runtime.read_bytes()).hexdigest(),"deleted":False})
            target["receipt"]["files"].append({"target":"main","path":package.LEGACY_POINTER_RELATIVE,"new_sha256":"legacy-pointer-owned","deleted":False})
            target["receipt_path"].write_text(json.dumps(target["receipt"]),encoding="utf-8")
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True,exist_ok=True)
            pointer.write_text(json.dumps({"receipt":str(target["receipt_path"]),"scope":"test"}),encoding="utf-8")
            stage1=base/"package-stage-first";package.build(stage1,target["receipt_path"],preserve_bindings=True)
            receipt2=base/"receipt-second.json"
            pub1=package.publish(stage1,target["receipt_path"],receipt2,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual(main/".opencode/workflow/publication.json",pointer)
            self.assertNotIn(package.LEGACY_POINTER_RELATIVE,[row.get("path") for row in pub1["files"]])
            self.assertEqual(config_before,target["config_path"].read_bytes())
            (main/"build/clean-simulation.txt").parent.mkdir(parents=True,exist_ok=True)
            (main/"build/clean-simulation.txt").write_text("simulated build artifact")
            shutil.rmtree(main/"build")
            self.assertTrue(pointer.is_file())
            stage2=base/"package-stage-second";package.build(stage2,receipt2,preserve_bindings=True)
            receipt3=base/"receipt-third.json"
            pub2=package.publish(stage2,receipt2,receipt3,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual(config_before,target["config_path"].read_bytes())
            receipt3_data=json.loads(receipt3.read_text())
            self.assertEqual(str(receipt2.resolve()),receipt3_data["previous_receipt"]["path"] if isinstance(receipt3_data["previous_receipt"],dict) else receipt3_data["previous_receipt"])
            self.assertEqual(str(receipt3.resolve()),json.loads(pointer.read_text())["receipt"])
    def test_published_binding_receipt_preserves_actual_config_scope(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            base=Path(temp);main=base/"MoonStation14";shared=base/"MoonStation14-shared";shared.mkdir()
            helper=BindingPlanTests();coordinator,coord_path,_,config_path,_,_,target,rows=helper.setup_target(main,[("author","orchestrate-ms14")])
            for row in rows: row["target"]="main"
            old=shared/"tooling/agent_workflow/workflow_coordinator.py";old.parent.mkdir(parents=True);old.write_bytes(b"old")
            rows.append({"target":"shared","path":"tooling/agent_workflow/workflow_coordinator.py","new_sha256":hashlib.sha256(old.read_bytes()).hexdigest(),"deleted":False})
            receipt=base/"previous.json";receipt.write_text(json.dumps({"main_root":str(main),"shared_root":str(shared),"source_hashes":{},"files":rows}),encoding="utf-8")
            role=target["roles"][0];role["token_file"]=str(main/"author.token")
            plan=base/"plan.json";plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":"main","coordinator_config":str(coord_path),"roles":[role]}]}),encoding="utf-8")
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True);pointer.write_text(json.dumps({"receipt":str(receipt)}),encoding="utf-8")
            stage=base/"stage";package.build(stage,receipt,plan)
            bmanifest=json.loads((stage/"bindings/binding-manifest.json").read_text());expected=bmanifest["files"]["main/opencode.jsonc"]["config_scope"]
            output=base/"applied.json";package.publish(stage,receipt,output,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            row=next(r for r in json.loads(output.read_text())["files"] if r.get("target")=="main" and r.get("path")=="opencode.jsonc")
            self.assertEqual(expected,row["config_scope"])
    def test_config_only_legacy_retirement_stages_and_publishes_explicit_six(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            base=Path(temp);main=base/"MoonStation14";shared=base/"shared";shared.mkdir()
            names=["main",*(f"MoonStation14-agent-0{i}" for i in range(1,6))]
            previous_files=[];original={}
            for name in names:
                root=main if name=="main" else base/name;root.mkdir()
                entry={"type":"local","command":[str(shared/".venv-workflow-mcp/Scripts/python.exe"),
                    str(shared/"tooling/agent_workflow/workflow_mcp.py"),"--repo",str(root),"--profile","author"],
                    "cwd":str(root),"environment":{},"disabled":False,"codemode":False,"protocol":"legacy",
                    "timeout":{"startup":15000,"catalog":15000,"execution":300000}}
                # Comments and unrelated raw bytes must survive the targeted boolean patch.
                text='{'+'\n  // preserve this comment\n  "model": { "model": "keep" },\n  "mcp": {"servers": {"ms14_workflow": '+json.dumps(entry)+', "other": {"disabled":false}}},\n  "agents": {}\n}'
                config=root/"opencode.jsonc";config.write_bytes(text.encode());original[name]=config.read_bytes()
                previous_files.append({"target":name,"path":"opencode.jsonc","new_sha256":hashlib.sha256(config.read_bytes()).hexdigest(),"deleted":False})
            # Prior publication state not selected by this operation is preserved verbatim.
            previous_files.append({"target":"shared","path":"unselected/profile.md","new_sha256":"a"*64,"deleted":False})
            receipt=base/"previous.json";receipt.write_text(json.dumps({"main_root":str(main),"shared_root":str(shared),
                "source_hashes":{"prior":"b"*64},"files":previous_files}),encoding="utf-8")
            for index,omitted in enumerate(names):
                partial=base/f"partial-{index}.json";partial.write_text(json.dumps({"version":1,
                    "targets":[name for name in names if name!=omitted]}),encoding="utf-8")
                with self.assertRaisesRegex(ValueError,"all five named agent worktrees"):
                    package.build(base/f"partial-stage-{index}",receipt,partial,legacy_retirement=True)
            duplicate=base/"duplicate.json";duplicate.write_text(json.dumps({"version":1,"targets":[*names,"main"]}),encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"unique explicit MAIN"):
                package.build(base/"duplicate-stage",receipt,duplicate,legacy_retirement=True)
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True);pointer.write_text(json.dumps({"receipt":str(receipt),"scope":"baseline","unrelated":7}))
            plan=base/"retirement.json";plan.write_text(json.dumps({"version":1,"targets":names}),encoding="utf-8")
            stage=base/"retirement-stage";manifest=package.build(stage,receipt,plan,legacy_retirement=True)
            self.assertEqual({},manifest["files"])
            self.assertEqual("planned-legacy-retirement",manifest["bindings"]["status"])
            binding=json.loads((stage/manifest["bindings"]["manifest"]).read_text())
            self.assertEqual(set(names),set(binding["targets"]));self.assertEqual(set(f"{n}/opencode.jsonc" for n in names),set(binding["files"]))
            self.assertEqual(sorted(names),binding["targets"])
            self.assertEqual({"version","targets"},set(json.loads(plan.read_text())))
            self.assertTrue(all(isinstance(name,str) for name in binding["targets"]))
            for name in names:
                item=binding["files"][f"{name}/opencode.jsonc"]
                staged=(stage/item["staged_path"]).read_bytes()
                self.assertEqual(original[name].replace(b'"disabled": false',b'"disabled": true'),staged)
                self.assertEqual(original[name],(stage/item["backup_path"]).read_bytes())
            applied=base/"retired.json"
            pointer_hash=hashlib.sha256(pointer.read_bytes()).hexdigest()
            (main/"opencode.jsonc").write_bytes(original["main"]+b" ")
            with self.assertRaisesRegex(ValueError,"binding source config changed"):
                package.publish(stage,receipt,base/"stale.json",pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            self.assertFalse((base/"stale.json").exists());self.assertEqual(pointer_hash,hashlib.sha256(pointer.read_bytes()).hexdigest())
            (main/"opencode.jsonc").write_bytes(original["main"])
            manifest_path=stage/"manifest.json";manifest_bytes=manifest_path.read_bytes()
            binding_path=stage/manifest["bindings"]["manifest"];binding_bytes=binding_path.read_bytes()
            plan_source=plan.read_bytes();plan_stage=(stage/binding["staged_plan"]).read_bytes()
            partial_data=json.loads(plan_source);partial_data["targets"].remove("MoonStation14-agent-05")
            partial_bytes=(json.dumps(partial_data)+"\n").encode();plan.write_bytes(partial_bytes)
            staged_plan=stage/binding["staged_plan"];staged_plan.write_bytes(partial_bytes)
            partial_binding=json.loads(binding_bytes);partial_binding["targets"].remove("MoonStation14-agent-05")
            partial_binding["files"].pop("MoonStation14-agent-05/opencode.jsonc")
            partial_binding["source_plan_sha256"]=hashlib.sha256(partial_bytes).hexdigest()
            partial_binding["staged_plan_sha256"]=hashlib.sha256(partial_bytes).hexdigest()
            new_binding_bytes=(json.dumps(partial_binding,indent=2,sort_keys=True)+"\n").encode();binding_path.write_bytes(new_binding_bytes)
            partial_manifest=json.loads(manifest_bytes);partial_manifest["bindings"]["sha256"]=hashlib.sha256(new_binding_bytes).hexdigest()
            partial_manifest["bindings"]["source_plan_sha256"]=hashlib.sha256(partial_bytes).hexdigest()
            manifest_path.write_text(json.dumps(partial_manifest,indent=2,sort_keys=True)+"\n",encoding="utf-8")
            before_pointer=pointer.read_bytes();before_configs={name:(main if name=="main" else base/name)/"opencode.jsonc" for name in names}
            before_configs={name:path.read_bytes() for name,path in before_configs.items()}
            with self.assertRaisesRegex(ValueError,"all six unique named targets"):
                package.publish(stage,receipt,base/"forged-partial.json",pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            self.assertFalse((base/"forged-partial.json").exists());self.assertEqual(before_pointer,pointer.read_bytes())
            for name,data in before_configs.items():
                self.assertEqual(data,((main if name=="main" else base/name)/"opencode.jsonc").read_bytes())
            manifest_path.write_bytes(manifest_bytes);binding_path.write_bytes(binding_bytes);plan.write_bytes(plan_source);staged_plan.write_bytes(plan_stage)
            result=package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest())
            self.assertEqual("applied",result["status"])
            self.assertEqual(set(names),{row["target"] for row in result["actions"] if row["path"]=="opencode.jsonc"})
            self.assertEqual(7,len(result["actions"]))
            self.assertEqual({"path":str(receipt.resolve()),"sha256":hashlib.sha256(receipt.read_bytes()).hexdigest()},result["previous_receipt"])
            self.assertEqual({"prior":"b"*64},result["source_hashes"])
            self.assertEqual(str(receipt.resolve()),json.loads(pointer.read_text())["previous_receipt"])
            preserved=next(row for row in result["files"] if row.get("path")=="unselected/profile.md")
            self.assertEqual(previous_files[-1],preserved)
            self.assertEqual("config-only-retirement",json.loads(pointer.read_text())["coordinator_package"]["status"])
            rollback=base/"retirement-rollback.json"
            package.publish(stage,receipt,rollback,pointer_path=pointer,
                expected_pointer_sha256=hashlib.sha256(pointer.read_bytes()).hexdigest(),rollback_receipt=applied)
            self.assertEqual("rolled-back",json.loads(rollback.read_text())["status"])
            for name in names:
                root=main if name=="main" else base/name
                self.assertEqual(original[name],(root/"opencode.jsonc").read_bytes())

    def test_reparse_ancestor_member_and_backup_fences(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            root=Path(temp);stage=root/"stage";outside=root/"outside";stage.mkdir();outside.mkdir()
            (outside/"member").write_bytes(b"protected")
            junction=stage/"redirect"
            result=subprocess.run(["cmd.exe","/c","mklink","/J",str(junction),str(outside)],capture_output=True,text=True)
            if result.returncode: self.skipTest("Windows junction creation unavailable")
            for fenced in (junction/"member",junction/"backup"):
                with self.assertRaisesRegex(ValueError,"reparse-point"): package.assert_no_reparse(stage,fenced)
            ancestor=root/"ancestor-link"
            result=subprocess.run(["cmd.exe","/c","mklink","/J",str(ancestor),str(root)],capture_output=True,text=True)
            if result.returncode: self.skipTest("Windows ancestor junction creation unavailable")
            with self.assertRaisesRegex(ValueError,"reparse-point"): package.assert_no_reparse(ancestor,ancestor/"stage/manifest.json")
            self.assertEqual(b"protected",(outside/"member").read_bytes())

    def test_real_explicit_binding_stage_apply_rollback(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            base=Path(temp); main=base/"MoonStation14"; shared=base/"MoonStation14-shared"; shared.mkdir()
            helper=BindingPlanTests(); coordinator, coord_path, _, config_path, _, profile_path, target, rows=helper.setup_target(main,[("author","orchestrate-ms14"),("fidelity","fidelity-reviewer")])
            binding_target_snapshot={config_path:config_path.read_bytes(),profile_path:profile_path.read_bytes()}
            for row in rows: row["target"]="main"
            old_runtime=shared/"tooling/agent_workflow/workflow_coordinator.py";old_runtime.parent.mkdir(parents=True);old_runtime.write_bytes(b"old receipt-owned runtime")
            history_helper=shared/"tooling/agent_workflow/workflow_cutover_history.py"
            self.assertFalse(history_helper.exists(),"fixture must model the pre-history package target as absent")
            scope_helper=shared/"tooling/agent_workflow/workflow_scope.py"
            self.assertFalse(scope_helper.exists(),"fixture must model the pre-scope package target as absent")
            rows.append({"target":"shared","path":"tooling/agent_workflow/workflow_coordinator.py","new_sha256":hashlib.sha256(old_runtime.read_bytes()).hexdigest(),"deleted":False})
            receipt=base/"previous.json"; previous={"main_root":str(main),"shared_root":str(shared),"source_hashes":{},"files":rows}
            receipt.write_text(json.dumps(previous),encoding="utf-8")
            shared_snapshot={p.relative_to(shared).as_posix():(p.read_bytes() if p.is_file() else None) for p in shared.rglob("*")}
            pointer=main/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True,exist_ok=True)
            pointer.write_text(json.dumps({"bind_worktrees":False,"previous_receipt":str(base/"older.json"),"receipt":str(receipt),"scope":"baseline","shell_policy_only":False,"unrelated":{"preserve":7}}),encoding="utf-8")
            pointer_hash=hashlib.sha256(pointer.read_bytes()).hexdigest()
            for role in target["roles"]:
                if role["role"]=="author": role["token_file"]=str(main/"author.token")
            binding_plan=base/"roles.json";binding_plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":[{"target":"main","coordinator_config":str(coord_path),"roles":target["roles"]}]}),encoding="utf-8")
            stage=base/"stage"; package.build(stage,receipt,binding_plan)
            applied=base/"applied.json"
            def refused_without_writes(message):
                with self.assertRaisesRegex(ValueError,message):
                    package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256=pointer_hash)
                self.assertFalse(applied.exists())
            manifest_path=stage/"manifest.json";manifest_bytes=manifest_path.read_bytes();manifest=json.loads(manifest_bytes)
            history_key="tooling/agent_workflow/workflow_cutover_history.py"
            history_row=manifest["files"].get(history_key)
            self.assertIsNotNone(history_row,"new runtime history helper must be explicitly staged")
            source_helper=package.ROOT/"tooling/agent_workflow/workflow_cutover_history.py"
            expected_history_sha=hashlib.sha256(source_helper.read_bytes()).hexdigest()
            self.assertEqual(history_key,history_row["source"])
            self.assertEqual(expected_history_sha,history_row["source_sha256"])
            self.assertEqual(expected_history_sha,history_row["sha256"])
            scope_key="tooling/agent_workflow/workflow_scope.py"
            scope_row=manifest["files"].get(scope_key)
            self.assertIsNotNone(scope_row,"author-scope runtime helper must be explicitly staged")
            expected_scope_sha=hashlib.sha256((package.ROOT/scope_key).read_bytes()).hexdigest()
            self.assertEqual(scope_key,scope_row["source"])
            self.assertEqual(expected_scope_sha,scope_row["source_sha256"])
            self.assertEqual(expected_scope_sha,scope_row["sha256"])
            package_backup=next(row for row in manifest["files"].values() if row["backup_path"])
            binding_manifest_path=stage/manifest["bindings"]["manifest"]
            binding_bytes=binding_manifest_path.read_bytes();binding_manifest=json.loads(binding_bytes)
            binding_backup=binding_manifest["files"]["main/opencode.jsonc"]
            def verify_reparse_backup(tree,relative,external_name,expected_sha):
                tree=stage/tree;outside=base/external_name
                shutil.move(str(tree),str(outside))
                result=subprocess.run(["cmd.exe","/c","mklink","/J",str(tree),str(outside)],capture_output=True,text=True)
                if result.returncode:
                    shutil.move(str(outside),str(tree));self.skipTest("Windows backup junction creation unavailable")
                try:
                    self.assertEqual(expected_sha,package.digest(stage/relative))
                    refused_without_writes("reparse-point")
                    self.assertEqual(pointer_hash,hashlib.sha256(pointer.read_bytes()).hexdigest())
                    self.assertEqual(b"old receipt-owned runtime",old_runtime.read_bytes())
                    self.assertEqual(shared_snapshot,{p.relative_to(shared).as_posix():(p.read_bytes() if p.is_file() else None) for p in shared.rglob("*")})
                    self.assertEqual(binding_target_snapshot,{path:path.read_bytes() for path in binding_target_snapshot})
                finally:
                    subprocess.run(["cmd.exe","/c","rmdir",str(tree)],capture_output=True,text=True,check=True)
                    shutil.move(str(outside),str(tree))
            with self.subTest(backup_owner="package"):
                verify_reparse_backup(Path(package_backup["backup_path"]).parts[0],package_backup["backup_path"],"outside-package-backups",package_backup["backup_sha256"])
            with self.subTest(backup_owner="binding"):
                verify_reparse_backup("bindings/backups",binding_backup["backup_path"],"outside-binding-backups",binding_backup["backup_sha256"])
            docrow=next(row for row in manifest["files"].values() if row["source"].startswith("docs/development-workflow-redesign/package/") and row["source"]!="docs/development-workflow-redesign/package/package-docs.json")
            staged_doc=stage/docrow["target"];docbytes=staged_doc.read_bytes();staged_doc.write_bytes(docbytes+b"tamper")
            refused_without_writes("staged member hash mismatch");staged_doc.write_bytes(docbytes)
            manifest["files"][docrow["target"]]["sha256"]="0"*64;manifest_path.write_text(json.dumps(manifest))
            refused_without_writes("staged member hash mismatch");manifest_path.write_bytes(manifest_bytes)
            binding_manifest_path.write_bytes(binding_bytes+b" ")
            refused_without_writes("binding manifest hash mismatch");binding_manifest_path.write_bytes(binding_bytes)
            staged_plan=stage/"bindings/binding-plan.json";planbytes=staged_plan.read_bytes();staged_plan.write_bytes(planbytes+b" ")
            refused_without_writes("binding plan staged hash mismatch");staged_plan.write_bytes(planbytes)
            binding_data=json.loads(binding_bytes);config_entry=binding_data["files"]["main/opencode.jsonc"]
            staged_config=stage/config_entry["staged_path"];staged_config_bytes=staged_config.read_bytes();staged_config.write_bytes(staged_config_bytes+b" ")
            refused_without_writes("binding staged bytes/backup mismatch");staged_config.write_bytes(staged_config_bytes)
            source_plan_bytes=binding_plan.read_bytes();binding_plan.write_bytes(source_plan_bytes+b" ")
            refused_without_writes("binding source plan changed");binding_plan.write_bytes(source_plan_bytes)
            coordinator_bytes=coord_path.read_bytes();coord_path.write_bytes(coordinator_bytes+b" ")
            refused_without_writes("binding source config changed");coord_path.write_bytes(coordinator_bytes)
            sharedrow=next(row for row in manifest["files"].values() if row["backup_path"])
            backup=stage/sharedrow["backup_path"];backupbytes=backup.read_bytes();backup.write_bytes(backupbytes+b"bad")
            refused_without_writes("package backup mismatch");backup.write_bytes(backupbytes)
            previous_bytes=receipt.read_bytes();receipt.write_bytes(previous_bytes+b" ")
            refused_without_writes("previous publication receipt changed");receipt.write_bytes(previous_bytes)
            applied.write_text("owned output collision")
            with self.assertRaisesRegex(ValueError,"output receipt already exists"):
                package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            applied.unlink()
            initial_shared=set(shared.rglob("*"))
            with self.assertRaisesRegex(ValueError,"expected value"):
                package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256="0"*64)
            foreign=json.loads(pointer.read_text());foreign["receipt"]=str(base/"foreign.json");pointer.write_text(json.dumps(foreign))
            foreign_hash=hashlib.sha256(pointer.read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError,"does not name exact previous receipt"):
                package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256=foreign_hash)
            pointer.write_text(json.dumps({"bind_worktrees":False,"previous_receipt":str(base/"older.json"),"receipt":str(receipt),"scope":"baseline","shell_policy_only":False,"unrelated":{"preserve":7}}),encoding="utf-8")
            with self.assertRaisesRegex(ValueError,"must be MAIN"):
                package.publish(stage,receipt,applied,pointer_path=main/"missing-pointer.json",expected_pointer_sha256=pointer_hash)
            self.assertEqual(initial_shared,set(shared.rglob("*")));self.assertFalse(applied.exists())
            create=next(row for row in json.loads((stage/"manifest.json").read_text())["files"].values() if row["old_target_sha256"] is None)
            conflict=shared/create["target"]; conflict.parent.mkdir(parents=True,exist_ok=True); conflict.write_bytes(b"user-owned conflict")
            with self.assertRaisesRegex(ValueError,"stale package preimage"): package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            self.assertEqual(b"user-owned conflict",conflict.read_bytes()); conflict.unlink()
            before=old_runtime.read_bytes();old_runtime.write_bytes(b"stale user edit")
            with self.assertRaisesRegex(ValueError,"stale package preimage"): package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            self.assertEqual(b"stale user edit",old_runtime.read_bytes());old_runtime.write_bytes(before)
            partial=base/"partial.json"; real_atomic=package._atomic_write; seen=0
            def fail_second_target(path,data,**kwargs):
                nonlocal seen
                if shared in path.parents:
                    seen+=1
                    if seen==2: raise OSError("fixture injected write error")
                return real_atomic(path,data,**kwargs)
            with mock.patch.object(package,"_atomic_write",side_effect=fail_second_target):
                with self.assertRaisesRegex(RuntimeError,"partial-hold"): package.publish(stage,receipt,partial,pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            journal=json.loads(partial.read_text());self.assertEqual("partial-hold",journal["status"]);self.assertEqual(1,len(journal["actions"]))
            first=journal["actions"][0]; first_path=shared/first["path"]
            if first["old_sha256"] is None: first_path.unlink()
            else: first_path.write_bytes((stage/first["backup_path"]).read_bytes())
            result=package.publish(stage,receipt,applied,pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            self.assertEqual("applied",result["status"])
            expected_shared_paths={row["receipt_path"] for row in manifest["files"].values()
                                   if row["receipt_target"]=="shared"}
            shared_actions={x["path"] for x in result["actions"] if x["target"]=="shared"}
            self.assertEqual(expected_shared_paths,shared_actions,"apply must match the staged package roster exactly")
            self.assertEqual(70,len(expected_shared_paths),"current roster includes workflow_coordinator_http_mcp.py alongside history and author-scope helpers")
            self.assertIn("tooling/agent_workflow/workflow_coordinator_http_mcp.py",expected_shared_paths)
            self.assertEqual(expected_history_sha,hashlib.sha256(history_helper.read_bytes()).hexdigest())
            self.assertEqual(3,len([x for x in result["actions"] if x["target"]=="main"]))
            self.assertTrue((shared/"docs/development-workflow/README.md").exists())
            published_pointer=json.loads(pointer.read_text());self.assertEqual(str(applied.resolve()),published_pointer["receipt"])
            self.assertEqual(str(receipt),published_pointer["previous_receipt"]);self.assertEqual({"preserve":7},published_pointer["unrelated"])
            rolled=base/"rollback.json";published=shared/"tooling/agent_workflow/workflow_coordinator.py";new_bytes=published.read_bytes();published.write_bytes(b"user edit after publish")
            published_pointer_bytes=pointer.read_bytes();pointer.write_bytes(b"foreign pointer mutation")
            reverse_before=published.read_bytes()
            with self.assertRaisesRegex(ValueError,"rollback publication pointer hash"):
                package.publish(stage,receipt,rolled,pointer_path=pointer,expected_pointer_sha256=json.loads(applied.read_text())["publication_pointer"]["new_sha256"],rollback_receipt=applied)
            self.assertEqual(reverse_before,published.read_bytes());pointer.write_bytes(published_pointer_bytes)
            with self.assertRaisesRegex(ValueError,"rollback target changed"): package.publish(stage,receipt,rolled,pointer_path=pointer,expected_pointer_sha256=json.loads(applied.read_text())["publication_pointer"]["new_sha256"],rollback_receipt=applied)
            self.assertFalse(rolled.exists());published.write_bytes(new_bytes)
            rollback=package.publish(stage,receipt,rolled,pointer_path=pointer,expected_pointer_sha256=json.loads(applied.read_text())["publication_pointer"]["new_sha256"],rollback_receipt=applied)
            self.assertEqual("rolled-back",rollback["status"]);self.assertEqual(b"old receipt-owned runtime",old_runtime.read_bytes())
            self.assertFalse(history_helper.exists(),"rollback must remove only the newly introduced history helper")
            self.assertFalse(scope_helper.exists(),"rollback must remove the newly introduced author-scope helper")
            self.assertEqual(str(receipt),json.loads(pointer.read_text())["receipt"])
            self.assertFalse((shared/"docs/development-workflow/README.md").exists())

    def test_incomplete_binding_package_refuses_apply_without_writes(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode") as temp:
            root = Path(temp); shared = root / "shared"; shared.mkdir()
            receipt = root / "previous.json"
            receipt.write_text(json.dumps({"files": [], "source_hashes": {}, "shared_root": str(shared)}), encoding="utf-8")
            stage = root / "stage"
            package.build(stage, receipt)
            before = list(shared.rglob("*"))
            with self.assertRaisesRegex(ValueError, "explicit complete role bindings"):
                package.publish(stage, receipt, root / "new-receipt.json", pointer_path=root/"build/agent-workflow/agent-status-board/publication.json", expected_pointer_sha256="0"*64)
            self.assertEqual(before, list(shared.rglob("*")))
            self.assertFalse((root / "new-receipt.json").exists())


if __name__ == "__main__":
    unittest.main()
