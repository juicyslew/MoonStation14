import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from jsonschema import Draft202012Validator

import workflow_coordinator_bindings as bindings
import workflow_coordinator_package as package


class DedicatedReviewerProfileTests(unittest.TestCase):
    ROLE_SOURCES = {
        "implementation": "review", "parity": "ss14-expert",
        "coverage": "review", "fidelity": "fidelity-reviewer",
    }

    def fixture(self, base: Path, roles=None):
        main=base/"MoonStation14"; shared=base/"MoonStation14-shared"; shared.mkdir()
        identities=roles or [(role,bindings.REVIEWER_AGENT_PREFIX+role) for role in self.ROLE_SOURCES]
        from test_workflow_coordinator_bindings import BindingPlanTests
        helper=BindingPlanTests()
        coordinator,coord_path,config,config_path,_,profile_path,target,receipt_rows=helper.setup_target(main,identities)
        for role, _ in identities:
            if role != "author": config["agents"].pop(bindings.REVIEWER_AGENT_PREFIX+role, None)
        for source in set(self.ROLE_SOURCES.values()):
            config["agents"][source] = {"model":{"model":f"preserve/{source}"},"description":f"Keep {source}","permissions":[]}
        config_path.write_text(json.dumps(config),encoding="utf-8")
        for row in receipt_rows:
            if row["path"] == "opencode.jsonc": row["new_sha256"] = hashlib.sha256(config_path.read_bytes()).hexdigest()
        Path(coordinator["artifact_dir"]).mkdir(parents=True,exist_ok=True)
        for row in receipt_rows: row["target"]="main"
        receipt={"main_root":str(main),"shared_root":str(shared),"files":receipt_rows,
                 "source_hashes":{},"_path":str(base/"previous.json"),"_sha256":"a"*64}
        receipt_path=Path(receipt["_path"]);receipt_path.write_text(json.dumps(receipt),encoding="utf-8")
        return main,shared,coordinator,coord_path,config,config_path,profile_path,target,receipt,receipt_path

    def declarations(self, base: Path, roles=None):
        roles=roles or list(self.ROLE_SOURCES)
        declarations=[]
        for role in roles:
            file_name,sha=bindings.REVIEWER_SOURCE_FILES[role]
            evidence=(bindings.ROOT/"build/agent-workflow/workflow-redesign-plan/wr12"/file_name).resolve()
            declarations.append({"role":role,"source_agent":self.ROLE_SOURCES[role],
                "output_root":str(base/"private-review-output"),"model":"openai/gpt-6-luna",
                "request":{"body":{"reasoningEffort":"high"}},
                "source_receipt":{"path":str(evidence),"sha256":sha}})
        return declarations

    def plan(self, base, target, declarations, filename="binding-plan.json", extra_targets=()):
        path=base/filename
        target_rows=[{"target":"main","coordinator_config":str(target["coord_path"]),"roles":target["target"]["roles"]},*extra_targets]
        path.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":target_rows,"reviewer_agents":declarations},separators=(",",":")),encoding="utf-8")
        return path

    def stage(self, base, target, plan_path):
        stage=base/"stage";stage.mkdir(exist_ok=True)
        result=bindings.stage_binding_plan(plan_path,stage,target["receipt"],main_root=target["main"],
            shared_root=target["shared"],adapter=target["shared"]/"tooling/agent_workflow/workflow_coordinator_mcp.py",
            python=target["shared"]/".venv-workflow-coordinator/Scripts/python.exe")
        return stage,result

    def target_tuple(self, values):
        main,shared,coordinator,coord_path,config,config_path,profile_path,target,receipt,receipt_path=values
        return {"main":main,"shared":shared,"coordinator":coordinator,"coord_path":coord_path,
                "config":config,"config_path":config_path,"profile_path":profile_path,
                "target":target,"receipt":receipt,"receipt_path":receipt_path}

    def test_stages_four_receipt_bound_role_profiles_and_preserves_unowned_config(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp); target=self.target_tuple(self.fixture(base)); before=target["config_path"].read_bytes(); profile_before=target["profile_path"].read_bytes()
            plan=self.plan(base,target,self.declarations(base));stage,result=self.stage(base,target,plan)
            entry=next(row for row in result["files"].values() if row["path"]=="opencode.jsonc")
            staged=json.loads((stage/entry["staged_path"]).read_bytes())
            schema_path=bindings.ROOT/"build/agent-workflow/phantom-wait-fix/opencode-schema.json"
            components=json.loads(schema_path.read_text(encoding="utf-8"))["components"]
            schema={"$schema":"https://json-schema.org/draft/2020-12/schema","components":components}
            agent_validator=Draft202012Validator({**schema,"$ref":"#/components/schemas/Config.AgentEncoded"})
            permission_validator=Draft202012Validator({**schema,"$ref":"#/components/schemas/Permission.Rule"})
            self.assertEqual(target["config"]["model"],staged["model"])
            self.assertEqual(target["config"]["mcp"]["timeout"],staged["mcp"]["timeout"])
            self.assertEqual(target["config"]["mcp"]["servers"]["custom"],staged["mcp"]["servers"]["custom"])
            self.assertEqual(before,target["config_path"].read_bytes())
            self.assertEqual(profile_before,target["profile_path"].read_bytes())
            self.assertEqual({"main/opencode.jsonc"},set(result["files"]))
            guidance=(bindings.ROOT/"build/agent-workflow/workflow-redesign-plan/wr12/reviewer-instructions.md").read_text(encoding="utf-8")
            for source in set(self.ROLE_SOURCES.values()):
                self.assertEqual(target["config"]["agents"][source],staged["agents"][source])
            self.assertEqual(set(target["config"]["agents"])|{bindings.REVIEWER_AGENT_PREFIX+r for r in self.ROLE_SOURCES},set(staged["agents"]))
            self.assertEqual(set(self.ROLE_SOURCES),set(result["reviewer_agents"]))
            self.assertEqual("http://127.0.0.1:49200",result["coordinator_endpoint"])
            self.assertEqual(target["coordinator"]["opencode_server"],"http://127.0.0.1:49100")
            for key, server in staged["mcp"]["servers"].items():
                if key.startswith("ms14_workflow_review_"):
                    self.assertEqual("http://127.0.0.1:49200",server["command"][server["command"].index("--endpoint")+1])
            for role,source in self.ROLE_SOURCES.items():
                name=bindings.REVIEWER_AGENT_PREFIX+role; agent=staged["agents"][name]
                agent_validator.validate(agent)
                for rule in agent["permissions"]: permission_validator.validate(rule)
                self.assertEqual("openai/gpt-6-luna",agent["model"])
                self.assertEqual({"body":{"reasoningEffort":"high"}},agent["request"])
                self.assertLessEqual(len(agent["system"].split()),130)
                self.assertIn("complete_review(result_path=...)",agent["system"])
                self.assertIn("report_error(error_path=...)",agent["system"])
                self.assertIn("Every tool call must include your assigned session_id",agent["system"])
                self.assertIn("same session_id",agent["system"])
                self.assertIn(bindings.REVIEWER_ROLE_GUIDANCE[role],agent["system"])
                self.assertIn(bindings.REVIEWER_ROLE_GUIDANCE[role],guidance)
                self.assertIn("Source is read-only",agent["system"])
                self.assertIn("Write only the assigned result/error JSON",agent["system"])
                output=(base/"private-review-output"/role).as_posix().rstrip("/")+"/**"
                permissions=agent["permissions"]
                self.assertIn({"action":"*","resource":"*","effect":"deny"},permissions)
                self.assertIn({"action":"shell","resource":"*","effect":"deny"},permissions)
                self.assertIn({"action":"edit","resource":"*","effect":"deny"},permissions)
                self.assertIn({"action":"edit","resource":output,"effect":"allow"},permissions)
                self.assertEqual([output],[p["resource"] for p in permissions if p["action"]=="edit" and p["effect"]=="allow"])
                self.assertIn({"action":"external_directory","resource":output,"effect":"allow"},permissions)
                self.assertFalse(any(p["effect"]=="allow" and p["action"] in ("write","apply_patch","create_file") for p in permissions))
                read_resources={p["resource"] for p in permissions if p["action"]=="read" and p["effect"]=="allow"}
                self.assertIn(target["main"].as_posix()+"/**",read_resources)
                self.assertIn((base/"artifacts").as_posix()+"/**",read_resources)
                external_resources={p["resource"] for p in permissions if p["action"]=="external_directory" and p["effect"]=="allow"}
                self.assertIn((base/"artifacts").as_posix()+"/**",external_resources)
                self.assertFalse(any(p["action"] in ("shell","subagent") and p["effect"]=="allow" for p in permissions))
                expected_tools={f"ms14_workflow_review_{role}_{tool}" for tool in bindings.REVIEW_TOOLS}
                self.assertEqual(expected_tools|{"ms14_workflow_*"},{p["action"] for p in permissions if p["action"].startswith("ms14_workflow")})
                self.assertIn("<task_id>.<assignment_generation>.result.json",agent["system"])
                self.assertIn(".error.json",agent["system"])
                self.assertEqual(source,result["reviewer_agents"][role]["source_agent"])
                self.assertEqual(bindings.REVIEWER_SOURCE_FILES[role][1],result["reviewer_agents"][role]["source_receipt"]["sha256"])
                if role=="parity":
                    ss14=(bindings.ROOT.parents[1]/"SS14-dev/space-station-14").as_posix()+"/**"
                    self.assertIn({"action":"external_directory","resource":ss14,"effect":"allow"},permissions)
                    self.assertIn(ss14,{p["resource"] for p in permissions if p["action"]=="read" and p["effect"]=="allow"})
            self.assertEqual(4,len([key for key in staged["mcp"]["servers"] if key.startswith("ms14_workflow_review_")]))
            self.assertFalse(any(p["action"].startswith("ms14_workflow") for p in staged["agents"]["review"]["permissions"]))
            self.assertFalse((base/"private-review-output").exists())
            old_receipt_bytes=target["receipt_path"].read_bytes()
            config_row=next(row for row in target["receipt"]["files"] if row["path"]=="opencode.jsonc")
            old_owned_hash=config_row["new_sha256"]
            old_config_scope=config_row.get("config_scope")
            # The fixture adopts publisher-produced bytes and their exact staged scope together.
            target["config_path"].write_bytes((stage/entry["staged_path"]).read_bytes())
            config_row["new_sha256"]=hashlib.sha256(target["config_path"].read_bytes()).hexdigest()
            config_row["config_scope"]=entry["config_scope"]
            target["receipt_path"].write_text(json.dumps(target["receipt"]),encoding="utf-8")
            repeat_stage=base/"repeat-stage";repeat_stage.mkdir()
            try:
                repeated=bindings.stage_binding_plan(plan,repeat_stage,target["receipt"],main_root=target["main"],shared_root=target["shared"],
                    adapter=target["shared"]/"tooling/agent_workflow/workflow_coordinator_mcp.py",python=target["shared"]/".venv-workflow-coordinator/Scripts/python.exe")
                repeated_entry=next(row for row in repeated["files"].values() if row["path"]=="opencode.jsonc")
                self.assertEqual(config_row["new_sha256"],repeated_entry["new_sha256"])
                self.assertEqual(set(self.ROLE_SOURCES),set(repeated["reviewer_agents"]))
            finally:
                target["config_path"].write_bytes(before);config_row["new_sha256"]=old_owned_hash
                if old_config_scope is None: config_row.pop("config_scope",None)
                else: config_row["config_scope"]=old_config_scope
                target["receipt_path"].write_bytes(old_receipt_bytes)
            pointer=target["main"]/package.POINTER_RELATIVE;pointer.parent.mkdir(parents=True,exist_ok=True)
            pointer.write_text(json.dumps({"bind_worktrees":False,"previous_receipt":str(base/"older.json"),
                "receipt":str(target["receipt_path"]),"scope":"fixture baseline","shell_policy_only":False,"custom":"preserve"}),encoding="utf-8")
            pointer_before=pointer.read_bytes();pointer_hash=hashlib.sha256(pointer_before).hexdigest()
            package_stage=base/"full-package-stage"
            package_manifest=package.build(package_stage,target["receipt_path"],plan)
            self.assertEqual("planned-explicit-targets",package_manifest["bindings"]["status"])
            applied=base/"package-applied.json"
            apply_record=package.publish(package_stage,target["receipt_path"],applied,pointer_path=pointer,expected_pointer_sha256=pointer_hash)
            applied_config=json.loads(target["config_path"].read_bytes())
            self.assertEqual(4,len([name for name in applied_config["agents"] if name.startswith(bindings.REVIEWER_AGENT_PREFIX)]))
            self.assertEqual(str(applied.resolve()),json.loads(pointer.read_bytes())["receipt"])
            rollback_path=base/"package-rollback.json"
            rolled=package.publish(package_stage,target["receipt_path"],rollback_path,pointer_path=pointer,
                expected_pointer_sha256=apply_record["publication_pointer"]["new_sha256"],rollback_receipt=applied)
            self.assertEqual("rolled-back",rolled["status"])
            self.assertEqual(before,target["config_path"].read_bytes());self.assertEqual(profile_before,target["profile_path"].read_bytes())
            self.assertEqual(pointer_before,pointer.read_bytes())

    def test_strict_declarations_reject_conflicts_author_drift_and_unsafe_roots_before_stage_writes(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp); target=self.target_tuple(self.fixture(base));decls=self.declarations(base)
            cases=[]
            extra=json.loads(json.dumps(decls));extra[0]["arbitrary_agent_config"]={};cases.append((extra,"only role"))
            author=json.loads(json.dumps(decls));author[0]["role"]="author";cases.append((author,"unknown or duplicate"))
            inside=json.loads(json.dumps(decls));inside[0]["output_root"]=str(target["main"]/"private");cases.append((inside,"outside every worktree"))
            wrong=json.loads(json.dumps(decls));wrong[0]["request"]["body"]["reasoningEffort"]="low";cases.append((wrong,"model/request snapshot"))
            wrong_model=json.loads(json.dumps(decls));wrong_model[0]["model"]="openai/other";cases.append((wrong_model,"model/request snapshot"))
            link=base/"linked-output";targetdir=base/"output-target";targetdir.mkdir()
            linked=subprocess.run(["cmd.exe","/c","mklink","/J",str(link),str(targetdir)],capture_output=True,text=True)
            if linked.returncode: self.skipTest("Windows output-root junction creation unavailable")
            reparse=json.loads(json.dumps(decls));reparse[0]["output_root"]=str(link/"private");cases.append((reparse,"reparse-point"))
            for index,(items,message) in enumerate(cases):
                with self.subTest(case=index):
                    plan=self.plan(base,target,items,filename=f"plan-{index}.json")
                    stage=base/f"stage-{index}";stage.mkdir()
                    with self.assertRaisesRegex(ValueError,message):
                        bindings.stage_binding_plan(plan,stage,target["receipt"],main_root=target["main"],shared_root=target["shared"],adapter=target["shared"]/"adapter.py",python=target["shared"]/"python.exe")
                    self.assertEqual([],list(stage.iterdir()))
            with self.subTest(case="undeclared-role"):
                target["target"]["roles"]=[r for r in target["target"]["roles"] if r["role"]!="implementation"]
                plan=self.plan(base,target,decls,filename="unassigned-role.json");stage=base/"unassigned-stage";stage.mkdir()
                with self.assertRaisesRegex(ValueError,"one explicit MAIN dedicated-agent role"):
                    bindings.stage_binding_plan(plan,stage,target["receipt"],main_root=target["main"],shared_root=target["shared"],adapter=target["shared"]/"adapter.py",python=target["shared"]/"python.exe")
                self.assertEqual([],list(stage.iterdir()))

    def test_reserved_existing_agent_conflict_and_unassigned_role_refuse(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);target=self.target_tuple(self.fixture(base));decls=self.declarations(base)
            config=json.loads(target["config_path"].read_text());config["agents"][bindings.REVIEWER_AGENT_PREFIX+"implementation"]={"description":"foreign"}
            target["config_path"].write_text(json.dumps(config),encoding="utf-8")
            row=next(x for x in target["receipt"]["files"] if x["path"]=="opencode.jsonc");row["new_sha256"]=hashlib.sha256(target["config_path"].read_bytes()).hexdigest()
            target["receipt_path"].write_text(json.dumps(target["receipt"]),encoding="utf-8")
            plan=self.plan(base,target,decls);stage=base/"foreign-stage";stage.mkdir()
            with self.assertRaisesRegex(ValueError,"conflicting/unowned reserved reviewer agent"):
                bindings.stage_binding_plan(plan,stage,target["receipt"],main_root=target["main"],shared_root=target["shared"],adapter=target["shared"]/"adapter.py",python=target["shared"]/"python.exe")
            self.assertEqual([],list(stage.iterdir()))

    def test_listener_endpoint_is_required_valid_and_distinct_from_api(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);target=self.target_tuple(self.fixture(base));declarations=self.declarations(base)
            for index, endpoint in enumerate((None,"https://127.0.0.1:49200","http://user@127.0.0.1:49200",
                                              "http://127.0.0.1:49200/path","http://127.0.0.1:49100",
                                              "http://localhost:49100/")):
                plan=self.plan(base,target,declarations,filename=f"bad-endpoint-{index}.json")
                data=json.loads(plan.read_text())
                if endpoint is None: data.pop("coordinator_endpoint")
                else: data["coordinator_endpoint"]=endpoint
                plan.write_text(json.dumps(data),encoding="utf-8")
                stage=base/f"endpoint-stage-{index}";stage.mkdir()
                with self.assertRaises(ValueError):
                    bindings.stage_binding_plan(plan,stage,target["receipt"],main_root=target["main"],shared_root=target["shared"],
                        adapter=target["shared"]/"adapter.py",python=target["shared"]/"python.exe")
                self.assertEqual([],list(stage.iterdir()))

    def test_profiles_receive_all_explicit_author_and_frozen_artifact_roots_only(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);target=self.target_tuple(self.fixture(base))
            author_root=base/"MoonStation14-agent-01"
            from test_workflow_coordinator_bindings import BindingPlanTests
            coordinator,coord_path,_,_,_,_,_,rows=BindingPlanTests().setup_target(author_root,[("author","orchestrate-ms14")])
            external_bundle=base/"agent01-frozen-bundles";external_bundle.mkdir()
            coordinator["artifact_dir"]=str(external_bundle);coord_path.write_text(json.dumps(coordinator),encoding="utf-8")
            for row in rows: row["target"]="MoonStation14-agent-01"
            target["receipt"]["files"].extend(rows)
            extra={"target":"MoonStation14-agent-01","coordinator_config":str(coord_path),
                   "roles":[{"role":"author","agent":"orchestrate-ms14","caller":"author-caller",
                     "session_id":"author-session","worktree":str(author_root),
                     "token_file":str(author_root/"author.token")} ]}
            plan=self.plan(base,target,self.declarations(base),extra_targets=[extra])
            stage,result=self.stage(base,target,plan)
            config_entry=next(entry for entry in result["files"].values() if entry["path"]=="opencode.jsonc" and entry["target"]=="main")
            staged=json.loads((stage/config_entry["staged_path"]).read_bytes())
            for role in self.ROLE_SOURCES:
                permissions=staged["agents"][bindings.REVIEWER_AGENT_PREFIX+role]["permissions"]
                for trusted in (target["main"],author_root,base/"artifacts",external_bundle):
                    pattern=trusted.as_posix()+"/**"
                    self.assertIn({"action":"read","resource":pattern,"effect":"allow"},permissions)
                    external={"action":"external_directory","resource":pattern,"effect":"allow"}
                    self.assertIn(external,permissions)
                edit_grants=[p["resource"] for p in permissions if p["action"]=="edit" and p["effect"]=="allow"]
                self.assertEqual([(base/"private-review-output"/role).as_posix()+"/**"],edit_grants)
                self.assertNotIn({"action":"edit","resource":author_root.as_posix()+"/**","effect":"allow"},permissions)
                self.assertNotIn({"action":"read","resource":(base/"untrusted").as_posix()+"/**","effect":"allow"},permissions)
                self.assertEqual(sorted(result["reviewer_agents"][role]["read_roots"],key=os.path.normcase),
                                 result["reviewer_agents"][role]["read_roots"])

    def test_untrusted_artifact_root_overlap_and_reparse_refuse_before_staging(self):
        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);target=self.target_tuple(self.fixture(base));plan=self.plan(base,target,self.declarations(base))
            nested=target["main"]/"build"/"artifacts";nested.mkdir(parents=True)
            target["coordinator"]["artifact_dir"]=str(nested)
            target["coord_path"].write_text(json.dumps(target["coordinator"]),encoding="utf-8")
            stage=base/"overlap-stage";stage.mkdir()
            with self.assertRaisesRegex(ValueError,"overlaps an explicit worktree"):
                bindings.stage_binding_plan(plan,stage,target["receipt"],main_root=target["main"],shared_root=target["shared"],adapter=base/"adapter.py",python=base/"python.exe")
            self.assertEqual([],list(stage.iterdir()))

        with tempfile.TemporaryDirectory(dir=Path(os.environ["LOCALAPPDATA"])/"Temp/opencode") as temp:
            base=Path(temp);target=self.target_tuple(self.fixture(base));plan=self.plan(base,target,self.declarations(base))
            junction=base/"artifact-link";actual=base/"actual-artifacts";actual.mkdir()
            linked=subprocess.run(["cmd.exe","/c","mklink","/J",str(junction),str(actual)],capture_output=True,text=True)
            if linked.returncode: self.skipTest("Windows artifact-root junction creation unavailable")
            target["coordinator"]["artifact_dir"]=str(junction)
            target["coord_path"].write_text(json.dumps(target["coordinator"]),encoding="utf-8")
            stage=base/"reparse-stage";stage.mkdir()
            with self.assertRaisesRegex(ValueError,"reparse-point"):
                bindings.stage_binding_plan(plan,stage,target["receipt"],main_root=target["main"],shared_root=target["shared"],adapter=base/"adapter.py",python=base/"python.exe")
            self.assertEqual([],list(stage.iterdir()))


if __name__ == "__main__":
    unittest.main()
