"""Build the inactive WR10 coordinator package from an explicit allowlist."""
from __future__ import annotations

import argparse
from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import time
import tempfile


ROOT = Path(__file__).resolve().parents[2]
SOURCES = (
    "admission.py", "agent_status.py", "gate_dispatch.py", "gate_execution.py",
    "gate_liveness.py", "gate_progress.py", "owner_attention.py", "owner_feedback.py",
    "transport_health.py", "validation_evidence.py", "workflow_board.py",
    "workflow_check_packs.py", "workflow_coordinator.py", "workflow_coordinator_mcp.py", "workflow_coordinator_http_mcp.py",
    "workflow_dependency.py", "workflow_error_recovery.py", "workflow_mcp.py",
    "workflow_coordinator_bindings.py", "workflow_opencode_config.py",
    "workflow_native_cancellation.py", "workflow_native_execution.py",
    "workflow_native_fixtures.py", "workflow_native_recovery.py", "workflow_native_retry.py",
    "workflow_native_worker.py", "workflow_owner.py", "workflow_preflight.py",
    "workflow_process_scope.py", "workflow_regression.py", "workflow_result_delivery.py",
    "workflow_review_cancellation.py", "workflow_review_control.py",
    "workflow_review_dependency.py", "workflow_review_effects.py", "workflow_review_history.py",
    "workflow_review_pipeline.py", "workflow_review_quiescence.py", "workflow_review_results.py",
    "workflow_review_retry.py", "workflow_review_transport.py", "workflow_review.py",
    "workflow_run_control.py", "workflow_runtime.py", "workflow_store.py",
    "workflow_submission.py", "workflow_scope.py", "workflow_task_artifacts.py", "workflow_toolchain.py",
    "workflow_transport_health.py", "workflow_supervisor.py", "workflow_cutover_history.py",
)
ASSETS = ("contracts/preflight.schema.json", "contracts/protocol.schema.json")
ROOT_ASSETS = (
    "tooling/agent_workflow/validation-logging.gradle",
    "tooling/agent_workflow/validation-junit-selectors.gradle",
    "tooling/agent_workflow/requirements-workflow-coordinator.txt",
    "tooling/agent_workflow/install_workflow_coordinator.ps1",
    "tooling/agent_workflow/workflow_coordinator_package.py",
    "tooling/opencode/shared-config.jsonc",
)
# These installed legacy entry points are receipt-owned aliases of the one
# reviewed runtime source; they intentionally contain no independent logic.
SOURCE_ALIASES = {
    "tooling/agent_workflow/workflow_runtime.py": (
        "tooling/agent_workflow/workflow.py",
    ),
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def safe_relative(value: str, label: str) -> Path:
    path = Path(value)
    if path.is_absolute() or ".." in path.parts or not path.parts:
        raise ValueError(f"unsafe {label} path: {value}")
    return path


def assert_no_reparse(root: Path, path: Path) -> None:
    root = Path(os.path.abspath(root)); path = Path(os.path.abspath(path))
    if root not in path.parents and path != root:
        raise ValueError(f"path escapes fenced root: {path}")
    for ancestor in reversed((root, *root.parents)):
        if ancestor.exists() and (ancestor.is_symlink() or getattr(ancestor.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
            raise ValueError(f"refusing reparse-point ancestor: {ancestor}")
    if root.is_symlink() or (getattr(root.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
        raise ValueError(f"refusing reparse-point root: {root}")
    relative = path.relative_to(root)
    current = root
    for part in relative.parts:
        current = current / part
        if current.is_symlink() or (current.exists() and getattr(current.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
            raise ValueError(f"refusing reparse-point path: {current}")


def _hash(path: Path) -> str | None:
    return digest(path) if path.is_file() else None


def _atomic_write(path: Path, data: bytes, *, exclusive: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if exclusive:
        with path.open("xb") as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        return
    fd, name = tempfile.mkstemp(prefix="." + path.name + ".", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name): os.unlink(name)


POINTER_RELATIVE = ".opencode/workflow/publication.json"
LEGACY_POINTER_RELATIVE = "build/agent-workflow/agent-status-board/publication.json"


def _pointer_path(main_root: Path, supplied: Path) -> Path:
    expected=main_root/Path(POINTER_RELATIVE)
    if Path(os.path.abspath(supplied)) != Path(os.path.abspath(expected)):
        raise ValueError("publication pointer must be MAIN .opencode/workflow/publication.json")
    assert_no_reparse(main_root,expected)
    if not expected.is_file(): raise ValueError("coordinator publication pointer is missing")
    return expected


def publish(stage: Path, previous_receipt: Path, receipt_output: Path, *, pointer_path: Path, expected_pointer_sha256: str, rollback_receipt: Path | None = None) -> dict:
    """Apply a verified package or reverse only the exact actions of its receipt.

    This is deliberately driven by staged bytes and receipt ownership, never a
    renderer or a current source checkout. Callers must retain the returned
    receipt as the publication evidence; failures include confirmed writes.
    """
    stage = Path(os.path.abspath(stage)); manifest_path = stage / "manifest.json"
    receipt_output = Path(os.path.abspath(receipt_output))
    temp_root=(Path(os.environ["LOCALAPPDATA"])/"Temp/opencode").resolve()
    workflow_root=(Path(os.environ["LOCALAPPDATA"])/"MoonStation14/workflow").resolve()
    if not (receipt_output.is_relative_to(temp_root) or receipt_output.is_relative_to(workflow_root)):
        raise ValueError("output receipt must remain in private OpenCode temp or durable workflow state")
    if not receipt_output.parent.is_dir(): raise ValueError("output receipt parent must already exist")
    assert_no_reparse(receipt_output.parent, receipt_output)
    if receipt_output.exists(): raise ValueError("output receipt already exists; refusing overwrite")
    if rollback_receipt is not None:
        old = json.loads(rollback_receipt.read_text(encoding="utf-8"))
        if old.get("status") != "applied" or Path(old.get("stage", "")).resolve() != stage.resolve():
            raise ValueError("rollback receipt is not an applied receipt for this stage")
        old_manifest = stage / "manifest.json"
        if digest(old_manifest) != old.get("manifest_sha256"): raise ValueError("rollback package manifest changed")
        old_manifest_data=json.loads(old_manifest.read_text(encoding="utf-8"))
        prev = json.loads(previous_receipt.read_text(encoding="utf-8"))
        previous_hash=hashlib.sha256(previous_receipt.read_bytes()).hexdigest()
        if old.get("previous_receipt") != {"path":str(previous_receipt.resolve()),"sha256":previous_hash}:
            raise ValueError("rollback receipt previous-receipt lineage does not match")
        pointer=_pointer_path(Path(prev["main_root"]),pointer_path)
        pointer_bytes=pointer.read_bytes()
        pointer_action=next((a for a in old.get("actions",[]) if a.get("control")=="publication-pointer"),None)
        if pointer_action is None or expected_pointer_sha256 != pointer_action["new_sha256"] or hashlib.sha256(pointer_bytes).hexdigest()!=expected_pointer_sha256:
            raise ValueError("rollback publication pointer hash is not the applied coordinator receipt")
        pointer_data=json.loads(pointer_bytes)
        if Path(pointer_data.get("receipt","")).resolve()!=rollback_receipt.resolve():
            raise ValueError("rollback publication pointer does not name the owned applied receipt")
        roots = {"shared": Path(prev["shared_root"]), "main": Path(prev["main_root"])}
        if old_manifest_data.get("bindings",{}).get("status")=="planned-legacy-retirement":
            binding_path=stage/safe_relative(old_manifest_data["bindings"]["manifest"],"rollback binding manifest")
            if digest(binding_path)!=old_manifest_data["bindings"]["sha256"]: raise ValueError("rollback binding manifest changed")
            binding_data=json.loads(binding_path.read_text(encoding="utf-8"))
            plan_path=stage/safe_relative(binding_data["staged_plan"],"rollback staged plan")
            source_plan=Path(binding_data["source_plan"])
            if (binding_data.get("mode")!="legacy-retirement" or digest(plan_path)!=binding_data.get("staged_plan_sha256") or
                    digest(plan_path)!=binding_data.get("source_plan_sha256") or not source_plan.is_file() or
                    digest(source_plan)!=binding_data.get("source_plan_sha256")):
                raise ValueError("rollback retirement plan changed")
            rollback_targets=json.loads(plan_path.read_text(encoding="utf-8")).get("targets",[])
            allowed={"main",*(f"MoonStation14-agent-0{i}" for i in range(1,6))}
            if (not isinstance(rollback_targets,list) or any(not isinstance(name,str) or name not in allowed for name in rollback_targets)
                    or len(set(rollback_targets))!=len(rollback_targets) or set(rollback_targets)!=allowed):
                raise ValueError("rollback retirement targets must cover all six explicit worktrees")
        else:
            rollback_targets=json.loads((stage / "bindings/binding-plan.json").read_text(encoding="utf-8"))["targets"]
        for row in rollback_targets:
            name=row if isinstance(row,str) else row.get("target")
            if name != "main": roots[Path(name).name]=Path(prev["main_root"]).parent/name
        reverse=[]
        for action in old.get("actions", []):
            root=roots.get(action["target"])
            if root is None: raise ValueError("rollback target is outside bound roots")
            path=root/safe_relative(action["path"],"rollback target"); assert_no_reparse(root,path)
            if _hash(path) != action["new_sha256"]: raise ValueError(f"rollback target changed: {action['path']}")
            backup=None
            if action["old_sha256"] is not None:
                backup=stage/safe_relative(action["backup_path"],"rollback backup")
                assert_no_reparse(stage,backup)
                if digest(backup) != action["old_sha256"]: raise ValueError(f"rollback backup changed: {action['path']}")
            reverse.append((path,action,backup if action["old_sha256"] is not None else None))
        journal={"schema_version":1,"status":"rollback-partial-hold","rollback_of":str(rollback_receipt.resolve()),"stage":str(stage),"publication_pointer":{"path":str(pointer),"old_sha256":pointer_action["new_sha256"],"new_sha256":pointer_action["old_sha256"]},"actions":[]}
        _atomic_write(receipt_output,json.dumps(journal,indent=2,sort_keys=True).encode()+b"\n",exclusive=True)
        ordered_reverse=[item for item in reversed(reverse) if item[1].get("control")!="publication-pointer"]
        ordered_reverse += [item for item in reverse if item[1].get("control")=="publication-pointer"]
        for path,action,backup in ordered_reverse:
            try:
                if _hash(path) != action["new_sha256"]: raise ValueError(f"rollback target changed during rollback: {action['path']}")
                if backup is None: path.unlink()
                else: _atomic_write(path,backup.read_bytes())
                journal["actions"].append(action)
                _atomic_write(receipt_output,json.dumps(journal,indent=2,sort_keys=True).encode()+b"\n")
            except Exception as exc:
                journal["error"]=str(exc); _atomic_write(receipt_output,json.dumps(journal,indent=2,sort_keys=True).encode()+b"\n"); raise RuntimeError(json.dumps(journal)) from exc
        journal["status"]="rolled-back"; _atomic_write(receipt_output,json.dumps(journal,indent=2,sort_keys=True).encode()+b"\n")
        return journal
    assert_no_reparse(stage, manifest_path)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    binding_status = manifest.get("bindings", {}).get("status")
    retirement = binding_status == "planned-legacy-retirement"
    preserve_current = binding_status == "preserve-current"
    shared_config = binding_status == "planned-shared-opencode-config"
    shell_retirement = binding_status == "planned-inspection-safe-shell"
    reviewer_search = binding_status == "planned-reviewer-search-permissions"
    if binding_status != "planned-explicit-targets" and not retirement and not preserve_current and not shell_retirement and not reviewer_search and not shared_config:
        raise ValueError("coordinator package requires explicit complete role bindings")
    if retirement and (manifest.get("package") != "workflow-legacy-retirement" or manifest.get("files") != {}):
        raise ValueError("legacy retirement must be a config-only package with zero shared files")
    previous_bytes = previous_receipt.read_bytes()
    previous_hash = hashlib.sha256(previous_bytes).hexdigest()
    if manifest.get("previous_receipt") != {"path": str(previous_receipt.resolve()), "sha256": previous_hash}:
        raise ValueError("previous publication receipt changed since staging")
    previous = json.loads(previous_bytes)
    shared = Path(previous["shared_root"])
    pointer=_pointer_path(Path(previous["main_root"]),pointer_path)
    pointer_bytes=pointer.read_bytes(); pointer_hash=hashlib.sha256(pointer_bytes).hexdigest()
    if pointer_hash != expected_pointer_sha256: raise ValueError("publication pointer SHA does not match explicit expected value")
    pointer_data=json.loads(pointer_bytes)
    if Path(pointer_data.get("receipt","")).resolve()!=previous_receipt.resolve():
        raise ValueError("publication pointer does not name exact previous receipt")
    if not preserve_current:
        binding_path = stage / safe_relative(manifest["bindings"]["manifest"], "binding manifest")
        if digest(binding_path) != manifest["bindings"]["sha256"]:
            raise ValueError("binding manifest hash mismatch")
        bindings = json.loads(binding_path.read_text(encoding="utf-8"))
        if bindings.get("receipt_sha256") != previous_hash:
            raise ValueError("binding plan does not bind previous receipt")
        plan = stage / safe_relative(bindings["staged_plan"], "staged binding plan")
        if digest(plan) != bindings["staged_plan_sha256"] or digest(plan) != bindings["source_plan_sha256"]:
            raise ValueError("binding plan staged hash mismatch")
        source_plan=Path(bindings["source_plan"])
        if not source_plan.is_file() or digest(source_plan)!=bindings["source_plan_sha256"]:
            raise ValueError("binding source plan changed since staging")
    actions = []
    roots = {"shared": shared, "main": Path(previous["main_root"])}
    plan_data = {} if preserve_current else json.loads(plan.read_text(encoding="utf-8"))
    if preserve_current:
        config_rows = [row for row in previous["files"] if row.get("path")=="opencode.jsonc" and row.get("target") in {"main", *(f"MoonStation14-agent-0{i}" for i in range(1,6))}]
        expected = {row["target"]:row.get("new_sha256") for row in config_rows if not row.get("deleted")}
        if len(expected)!=len(config_rows) or not expected or manifest["bindings"].get("configs") != expected:
            raise ValueError("preserved configs do not match exact receipt ownership")
        for name,sha in expected.items():
            root=Path(previous["main_root"]) if name=="main" else Path(previous["main_root"]).parent/name
            config=root/"opencode.jsonc"; assert_no_reparse(root,config)
            if _hash(config)!=sha: raise ValueError(f"preserved receipt-owned config changed: {name}/opencode.jsonc")
            config_row=next(r for r in config_rows if r.get("target")==name)
            scope=config_row.get("config_scope",{})
            overlay_scope=scope.get("local_overlay") if isinstance(scope,dict) else None
            if overlay_scope is not None:
                if (not isinstance(overlay_scope,dict) or set(overlay_scope)!={"path","sha256"} or
                        overlay_scope["path"]!=".opencode/workflow/opencode-local.json"):
                    raise ValueError(f"preserved local overlay scope is malformed: {name}")
                overlay=root/overlay_scope["path"]; assert_no_reparse(root,overlay)
                overlay_rows=[r for r in previous["files"] if r.get("target")==name and r.get("path")==overlay_scope["path"] and not r.get("deleted")]
                if len(overlay_rows)!=1 or overlay_rows[0].get("new_sha256")!=overlay_scope["sha256"] or _hash(overlay)!=overlay_scope["sha256"]:
                    raise ValueError(f"preserved receipt-owned local overlay changed: {name}")
        target_names=list(expected)
    elif shell_retirement:
        if (bindings.get("mode") != "inspection-safe-shell" or plan_data.get("mode") != "inspection-safe-shell" or
                set(plan_data)-{"version","mode","targets","formatting_preimages","profiles"} or plan_data.get("version")!=1):
            raise ValueError("inspection-safe-shell binding plan shape is invalid")
        allowed_targets={"main", *(f"MoonStation14-agent-0{i}" for i in range(1,5))}
        target_rows=plan_data.get("targets")
        profile_rows=plan_data.get("profiles",[]);profile_targets={f"MoonStation14-agent-0{i}" for i in range(1,4)}
        if "profiles" in plan_data and (not isinstance(profile_rows,list) or len(profile_rows)!=3 or
                any(not isinstance(r,dict) or set(r)!={"target","path","expected_sha256"} or
                    r.get("target") not in profile_targets or r.get("path")!=".opencode/agents/fidelity-reviewer.md" or
                    not isinstance(r.get("expected_sha256"),str) or len(r["expected_sha256"])!=64 for r in profile_rows) or
                {r["target"] for r in profile_rows if isinstance(r,dict)}!=profile_targets):
            raise ValueError("inspection-safe-shell reviewer profile scope is invalid")
        expected_files={f"{n}/opencode.jsonc" for n in allowed_targets}|{f"{r['target']}/{r['path']}" for r in profile_rows}
        if (not isinstance(plan_data.get("targets"),list) or len(plan_data["targets"])!=5 or
                any(not isinstance(r,dict) or set(r)!={"target","expected_config_sha256"} or
                    not isinstance(r.get("expected_config_sha256"),str) or len(r["expected_config_sha256"])!=64 for r in target_rows) or
                {r["target"] for r in target_rows}!=allowed_targets or
                sorted(allowed_targets)!=bindings.get("targets") or
                set(bindings.get("files",{}))!=expected_files):
            raise ValueError("inspection-safe-shell must bind exactly MAIN and agents 01..04")
        plan_by_target={r["target"]:r for r in target_rows}
        profile_by_target={r["target"]:r for r in profile_rows}
    elif retirement:
        if bindings.get("mode") != "legacy-retirement" or set(plan_data) != {"version", "targets"} or plan_data.get("version") != 1:
            raise ValueError("legacy retirement binding plan shape is invalid")
        allowed_targets={"main", *(f"MoonStation14-agent-0{i}" for i in range(1,6))}
        names=plan_data["targets"]
        if (not isinstance(names,list) or any(not isinstance(name,str) or name not in allowed_targets for name in names)
                or len(set(names))!=len(names) or set(names)!=allowed_targets or sorted(names) != bindings.get("targets")):
            raise ValueError("retirement must explicitly bind all six unique named targets")
        if not isinstance(bindings.get("files"),dict) or set(bindings["files"]) != {f"{name}/opencode.jsonc" for name in names}:
            raise ValueError("retirement binding files must contain only each explicit target config")
    elif reviewer_search:
        from workflow_coordinator_bindings import REVIEWER_SEARCH_AGENTS, _strict_ordered_json, transform_reviewer_search_permissions
        expected_key="main/opencode.jsonc"
        if (bindings.get("mode")!="reviewer-search-permissions" or set(plan_data)!={"version","mode","targets"} or
                plan_data.get("version")!=1 or plan_data.get("mode")!="reviewer-search-permissions" or
                not isinstance(plan_data.get("targets"),list) or len(plan_data["targets"])!=1 or
                plan_data["targets"][0].get("target")!="main" or set(bindings.get("files",{}))!={expected_key}):
            raise ValueError("reviewer search plan must bind MAIN only")
    elif shared_config:
        from workflow_coordinator_bindings import validate_shared_config_plan_shape
        if set(bindings)!={"version","mode","source_plan","source_plan_sha256","staged_plan","staged_plan_sha256","receipt_path","receipt_sha256","targets","files","metadata"}:
            raise ValueError("shared config binding manifest has unknown fields")
        allowed_shared_files = {f"{n}/{p}" for n in ("main", *(f"MoonStation14-agent-0{i}" for i in range(1,5))) for p in ("opencode.jsonc", ".opencode/workflow/opencode-local.json", ".gitignore")}
        if bindings.get("mode") != "shared-opencode-config" or not isinstance(bindings.get("files"), dict) or not set(bindings["files"]).issubset(allowed_shared_files):
            raise ValueError("shared config plan contains unknown targets or files")
        validate_shared_config_plan_shape(plan_data)
        if bindings.get("targets")!=sorted(r["target"] for r in plan_data["targets"]):
            raise ValueError("shared config binding target list differs from reviewed plan")
        transition=bindings.get("metadata",{}).get("index_transition",{})
        if transition.get("untrack_generated") is not plan_data["untrack_generated"] or transition.get("expected_index_digests")!={r["target"]:r["expected_index_digest"] for r in plan_data["targets"]}:
            raise ValueError("shared config index transition metadata differs from reviewed plan")
        # Re-derive every output against current source preimages immediately
        # before applying. This binds template, config, overlay, ignore and index.
        import tempfile
        with tempfile.TemporaryDirectory(prefix="shared-config-verify-") as verify_dir:
            from workflow_coordinator_bindings import stage_shared_config_plan
            previous["_path"]=str(previous_receipt.resolve()); previous["_sha256"]=previous_hash
            fresh=stage_shared_config_plan(source_plan,Path(verify_dir),previous,
                main_root=Path(previous["main_root"]),shared_root=shared)
            if set(fresh["files"])!=set(bindings["files"]): raise ValueError("shared config staged file set changed")
            for key, expected in fresh["files"].items():
                supplied=bindings["files"][key]
                if supplied != expected:
                    raise ValueError(f"shared config staged transformation changed: {key}")
                for field in ("staged_path","backup_path"):
                    rel=supplied.get(field); exp=expected.get(field)
                    if rel is None or exp is None:
                        if rel!=exp: raise ValueError(f"shared config stage ownership changed: {key}")
                    elif digest(stage/safe_relative(rel,"shared config staged member")) != digest(Path(verify_dir)/safe_relative(exp,"verified shared config member")):
                        raise ValueError(f"shared config staged bytes differ from rederived output: {key}")
        allowed_members={"bindings/binding-plan.json","bindings/binding-manifest.json"}
        for item in bindings["files"].values():
            allowed_members.add(safe_relative(item["staged_path"],"shared config staged member").as_posix())
            if item.get("backup_path") is not None:
                allowed_members.add(safe_relative(item["backup_path"],"shared config backup").as_posix())
        actual_members={p.relative_to(stage).as_posix() for p in (stage/"bindings").rglob("*") if p.is_file()}
        if actual_members!=allowed_members:
            raise ValueError("shared config stage contains unknown or missing binding members")
    elif not preserve_current and bindings.get("mode") == "legacy-retirement":
        raise ValueError("retirement binding manifest requires a config-only package")
    if preserve_current:
        pass
    elif shell_retirement:
        target_names=["main", *(f"MoonStation14-agent-0{i}" for i in range(1,5))]
    elif reviewer_search:
        target_names=["main"]
    elif shared_config:
        target_names=["main", *(f"MoonStation14-agent-0{i}" for i in range(1,5))]
    elif retirement:
        target_names = plan_data["targets"]
    else:
        target_names = [r["target"] for r in plan_data["targets"]]
    roots.update({Path(name).name: Path(previous["main_root"]).parent / name
                  for name in target_names if name != "main"})
    for key, row in manifest["files"].items():
        rel = safe_relative(row["target"], "package target")
        # target is the actual staged member path; source is provenance and may
        # intentionally differ for package documentation remapping.
        src = stage / safe_relative(row["target"], "package staged member")
        assert_no_reparse(stage, src)
        if digest(src) != row["sha256"]: raise ValueError(f"staged member hash mismatch: {key}")
        target = shared / rel
        assert_no_reparse(shared, target)
        if row["receipt_target"] != "shared" or row["receipt_path"] != key: raise ValueError("package target is not receipt-bound")
        owned=[x for x in previous["files"] if x.get("target")=="shared" and x.get("path")==key]
        if row["old_target_sha256"] is None:
            if owned and not owned[0].get("deleted"): raise ValueError(f"create target unexpectedly receipt-owned: {key}")
        elif len(owned)!=1 or owned[0].get("deleted") or owned[0].get("new_sha256")!=row["old_target_sha256"]:
            raise ValueError(f"replacement target not owned by previous receipt: {key}")
        old = _hash(target)
        if old != row["old_target_sha256"]: raise ValueError(f"stale package preimage: {key}")
        backup = None
        if old is not None:
            backup = stage / safe_relative(row["backup_path"], "package backup")
            assert_no_reparse(stage, backup)
            if digest(backup) != row["backup_sha256"] or row["backup_sha256"] != old: raise ValueError(f"package backup mismatch: {key}")
        actions.append({"target":"shared","path":key,"old_sha256":old,"new_sha256":digest(src),"backup_path":row["backup_path"],"backup_sha256":row["backup_sha256"],"staged_path":row["target"]})
    for key, row in ({} if preserve_current else bindings["files"]).items():
        rel = safe_relative(row["path"], "binding target")
        if row["target"] not in roots: raise ValueError("binding target outside explicit receipt roots")
        root = roots[row["target"]]; target = root / rel; src = stage / safe_relative(row["staged_path"], "staged binding")
        assert_no_reparse(root, target); assert_no_reparse(stage, src)
        backup = stage / safe_relative(row["backup_path"], "binding backup") if row.get("backup_path") is not None else None
        if backup is not None: assert_no_reparse(stage, backup)
        source=Path(row["source"])
        if row.get("source_sha256") is None:
            if source.exists(): raise ValueError(f"binding source appeared after staging: {key}")
        elif not source.is_file() or digest(source)!=row["source_sha256"]:
            raise ValueError(f"binding source config changed: {key}")
        generated_config_candidate = (shared_config and not plan_data["initialize"] and row["path"] == "opencode.jsonc"
                                      and row.get("config_scope", {}).get("shared_template") is not None)
        if digest(src) != row["new_sha256"] or (backup is not None and (digest(backup) != row["backup_sha256"] or row["backup_sha256"] != row["old_sha256"])) or (backup is None and (row.get("backup_sha256") is not None or (row.get("old_sha256") is not None and not generated_config_candidate))):
            raise ValueError(f"binding staged bytes/backup mismatch: {key}")
        receipt_rows = [x for x in previous["files"] if x.get("target")==row["target"] and x.get("path")==row["path"]]
        formatted_main = shell_retirement and row.get("target")=="main" and row.get("formatting_preimage",{}).get("sha256")==row.get("receipt_sha256")
        new_overlay = shared_config and row["path"] == ".opencode/workflow/opencode-local.json" and row["old_sha256"] is None and row["receipt_sha256"] is None
        reviewed_ignore = shared_config and row["path"] == ".gitignore" and row.get("receipt_sha256") is None
        generated_config = (shared_config and not plan_data["initialize"] and row["path"] == "opencode.jsonc"
                            and row["target"] in {"main", *(f"MoonStation14-agent-0{i}" for i in range(1,5))}
                            and row.get("config_scope", {}).get("shared_template") is not None
                            and row.get("backup_path") is None and row.get("backup_sha256") is None
                            and row.get("old_sha256") == next(r["expected_config_sha256"] for r in plan_data["targets"] if r["target"] == row["target"]))
        if (not new_overlay and not reviewed_ignore and (len(receipt_rows)!=1 or receipt_rows[0].get("deleted") or receipt_rows[0].get("new_sha256") != row["receipt_sha256"] or (row["old_sha256"] != row["receipt_sha256"] and not formatted_main and not generated_config))):
            raise ValueError(f"binding target not owned by previous receipt: {key}")
        if backup is None and row.get("old_sha256") is not None and not generated_config:
            raise ValueError(f"binding backup is required: {key}")
        if shell_retirement and row.get("path")==".opencode/agents/fidelity-reviewer.md":
            from workflow_coordinator_bindings import retire_fidelity_shell_profile
            profile=profile_by_target.get(row["target"])
            if profile is None or row.get("old_sha256")!=profile["expected_sha256"]:
                raise ValueError(f"reviewed profile hash mismatch: {key}")
            baseline=backup.read_bytes()
            if hashlib.sha256(baseline).hexdigest()!=row["receipt_sha256"] or row["old_sha256"]!=row["receipt_sha256"]:
                raise ValueError(f"reviewer profile preimage is not receipt-owned: {key}")
            if source.resolve()!=target.resolve() or digest(source)!=row["source_sha256"]:
                raise ValueError(f"reviewer profile source changed: {key}")
            expected_text,removed=retire_fidelity_shell_profile(baseline.decode("utf-8"))
            staged_text=(stage/safe_relative(row["staged_path"],"staged binding")).read_bytes().decode("utf-8")
            if not removed or staged_text!=expected_text:
                raise ValueError(f"staged reviewer profile differs from exact shell-deny retirement: {key}")
            receipt_rows=[x for x in previous["files"] if x.get("target")==row["target"] and x.get("path")==row["path"]]
            old_scope=receipt_rows[0].get("profile_scope",{}) if len(receipt_rows)==1 else None
            if not isinstance(old_scope,dict) or row.get("profile_scope")!={**old_scope,"shell_policy_retirement":True,"retired_shell_denies":removed}:
                raise ValueError(f"reviewer profile ownership scope changed: {key}")
        elif shell_retirement:
            from workflow_coordinator_bindings import _json_config, _strict_ordered_json, retire_inspection_blocking_shell_rules, KNOWN_LEGACY_SHELL_DENIES
            name=row["target"]
            if row.get("path")!="opencode.jsonc" or row.get("old_sha256")!=plan_by_target[name]["expected_config_sha256"]:
                raise ValueError(f"reviewed expected config hash mismatch: {key}")
            if _hash(target)!=row["old_sha256"]: raise ValueError(f"binding preimage mismatch: {key}")
            current_bytes=target.read_bytes(); baseline_bytes=current_bytes
            if row["old_sha256"] != row["receipt_sha256"]:
                fmt=plan_data.get("formatting_preimages",{}).get("main") if name=="main" else None
                if not isinstance(fmt,dict) or set(fmt)!={"path","sha256"} or fmt["sha256"]!=row["receipt_sha256"]:
                    raise ValueError("formatting baseline does not match receipt-owned MAIN config")
                baseline=Path(fmt["path"])
                if not baseline.is_absolute(): raise ValueError("formatting baseline must be absolute")
                assert_no_reparse(Path(baseline.anchor),baseline)
                if not baseline.is_file() or digest(baseline)!=row["receipt_sha256"]:
                    raise ValueError("formatting baseline hash differs from previous receipt")
                baseline_bytes=baseline.read_bytes()
                if _strict_ordered_json(baseline_bytes)!=_strict_ordered_json(current_bytes):
                    raise ValueError("MAIN formatting drift changed ordered config semantics")
            prior_scope=receipt_rows[0].get("config_scope",{})
            if not isinstance(prior_scope,dict): raise ValueError("receipt config scope is malformed")
            prior=_json_config(baseline_bytes)
            expected=retire_inspection_blocking_shell_rules(prior)
            staged=_json_config(src.read_bytes())
            if staged!=expected: raise ValueError("staged config differs from exact shell-policy retirement")
            removed=[]
            for agent,definition in prior.get("agents",{}).items():
                new_def=expected.get("agents",{}).get(agent,{})
                new_rules=new_def.get("permissions",[]) if isinstance(new_def,dict) else []
                for rule in definition.get("permissions",[]) if isinstance(definition,dict) else []:
                    identity=tuple(rule.get(k) for k in ("action","resource","effect")) if isinstance(rule,dict) else ()
                    if identity in KNOWN_LEGACY_SHELL_DENIES and rule not in new_rules:
                        removed.append({"agent":agent,"action":identity[0],"resource":identity[1],"effect":identity[2]})
            expected_scope={**prior_scope,"shell_policy_retirement":True,"retired_shell_rules":removed}
            if row.get("config_scope")!=expected_scope: raise ValueError("shell retirement config scope changed")
        elif shared_config:
            if row["path"] == ".gitignore":
                if row["old_sha256"] is not None and _hash(target) != row["old_sha256"]: raise ValueError(f"binding preimage mismatch: {key}")
                if row["old_sha256"] is None and target.exists(): raise ValueError(f"unowned gitignore: {key}")
            elif _hash(target) != row["old_sha256"]: raise ValueError(f"binding preimage mismatch: {key}")
        elif _hash(target) != row["old_sha256"]: raise ValueError(f"binding preimage mismatch: {key}")
        action={"target":row["target"],"path":row["path"],"old_sha256":row["old_sha256"],"new_sha256":row["new_sha256"],"backup_path":row.get("backup_path"),"backup_sha256":row.get("backup_sha256"),"staged_path":row["staged_path"]}
        if row["path"] == "opencode.jsonc" and isinstance(row.get("config_scope"), dict):
            action["config_scope"] = deepcopy(row["config_scope"])
        if row["path"] == ".opencode/agents/fidelity-reviewer.md" and isinstance(row.get("profile_scope"),dict):
            action["profile_scope"]=deepcopy(row["profile_scope"])
        actions.append(action)
    pointer_backup=stage/"control/pointer.previous.json"
    assert_no_reparse(stage,pointer_backup)
    pointer_backup.parent.mkdir(parents=True,exist_ok=True)
    if pointer_backup.exists() and digest(pointer_backup)!=pointer_hash: raise ValueError("staged pointer backup conflicts")
    if not pointer_backup.exists(): pointer_backup.write_bytes(pointer_bytes)
    pointer_value=dict(pointer_data)
    pointer_value["previous_receipt"]=pointer_data["receipt"]
    pointer_value["receipt"]=str(receipt_output.resolve())
    pointer_value["scope"]=("legacy MCP retirement published; no coordinator activation" if retirement else "coordinator package published; awaiting activation")
    pointer_value["coordinator_package"]={"manifest_sha256":digest(manifest_path),
        "status":"config-only-retirement" if retirement else "published-awaiting-activation"}
    pointer_new_bytes=(json.dumps(pointer_value,indent=2,sort_keys=True)+"\n").encode("utf-8")
    pointer_action={"target":"main","path":POINTER_RELATIVE,"old_sha256":pointer_hash,"new_sha256":hashlib.sha256(pointer_new_bytes).hexdigest(),"backup_path":pointer_backup.relative_to(stage).as_posix(),"backup_sha256":pointer_hash,"staged_path":None,"control":"publication-pointer","previous_receipt":pointer_data["receipt"],"new_receipt":str(receipt_output.resolve())}
    confirmed=[]
    source_hashes=dict(previous.get("source_hashes",{}))
    for row in manifest["files"].values(): source_hashes[row["source"]]=row["source_sha256"]
    current_files=[row for row in previous["files"] if not (row.get("target")=="main" and row.get("path")==LEGACY_POINTER_RELATIVE)]
    record={"schema_version":1,"status":"applying-hold","created_utc":time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime()),"main_root":previous.get("main_root"),"shared_root":str(shared),"source_hashes":source_hashes,"stage":str(stage),"previous_receipt":{"path":str(previous_receipt.resolve()),"sha256":previous_hash},"publication_pointer":{"path":str(pointer),"old_sha256":pointer_hash,"new_sha256":pointer_action["new_sha256"],"backup_path":pointer_action["backup_path"],"backup_sha256":pointer_hash},"manifest_sha256":digest(manifest_path),"files":current_files,"actions":confirmed}
    _atomic_write(receipt_output,json.dumps(record,indent=2,sort_keys=True).encode()+b"\n",exclusive=True)
    try:
        if hashlib.sha256(pointer.read_bytes()).hexdigest()!=pointer_hash or Path(json.loads(pointer.read_text(encoding="utf-8")).get("receipt","")).resolve()!=previous_receipt.resolve():
            raise ValueError("publication pointer changed immediately before package writes")
        for a in actions:
            root=roots.get(a["target"],shared); path=root/safe_relative(a["path"],"publication target")
            assert_no_reparse(root,path); path.parent.mkdir(parents=True,exist_ok=True)
            data=(stage/safe_relative(a["staged_path"],"publication staged member")).read_bytes()
            if hashlib.sha256(data).hexdigest()!=a["new_sha256"]: raise ValueError(f"staged member changed immediately before write: {a['path']}")
            assert_no_reparse(root,path)
            if _hash(path)!=a["old_sha256"]: raise ValueError(f"preimage changed immediately before write: {a['path']}")
            _atomic_write(path,data); confirmed.append(a)
            record["files"]=[row for row in record["files"] if not (row.get("target")==a["target"] and row.get("path")==a["path"])]
            new_row={"target":a["target"],"path":a["path"],"old_sha256":a["old_sha256"],"new_sha256":a["new_sha256"],"deleted":False,"backup_path":a["backup_path"],"backup_sha256":a["backup_sha256"]}
            if "config_scope" in a: new_row["config_scope"] = deepcopy(a["config_scope"])
            if "profile_scope" in a: new_row["profile_scope"] = deepcopy(a["profile_scope"])
            record["files"].append(new_row)
            _atomic_write(receipt_output,json.dumps(record,indent=2,sort_keys=True).encode()+b"\n")
        if hashlib.sha256(pointer.read_bytes()).hexdigest()!=pointer_hash: raise ValueError("publication pointer changed before update")
        assert_no_reparse(Path(previous["main_root"]),pointer)
        _atomic_write(pointer,pointer_new_bytes)
        confirmed.append(pointer_action)
        record["files"]=[row for row in record["files"] if not (row.get("target")=="main" and row.get("path")==POINTER_RELATIVE)]
        record["files"].append({"target":"main","path":POINTER_RELATIVE,"old_sha256":pointer_hash,"new_sha256":pointer_action["new_sha256"],"deleted":False,"backup_path":pointer_action["backup_path"],"backup_sha256":pointer_hash})
        record["actions"]=confirmed
        _atomic_write(receipt_output,json.dumps(record,indent=2,sort_keys=True).encode()+b"\n")
    except Exception as exc:
        record["status"]="partial-hold";record["error"]=str(exc);_atomic_write(receipt_output,json.dumps(record,indent=2,sort_keys=True).encode()+b"\n")
        raise RuntimeError(json.dumps(record)) from exc
    record["status"]="applied";record["actions"]=confirmed;_atomic_write(receipt_output,json.dumps(record,indent=2,sort_keys=True).encode()+b"\n"); return record


def build(stage: Path, receipt_path: Path, binding_plan: Path | None = None, *, legacy_retirement: bool = False, preserve_bindings: bool = False, reviewed_source_hashes: dict | None = None) -> dict:
    approved = (ROOT / "build/agent-workflow/workflow-redesign-plan/wr10/stage").resolve()
    temp = (Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode").resolve()
    requested_stage = Path(os.path.abspath(stage))
    if requested_stage != approved and temp not in requested_stage.parents:
        raise ValueError("package stage must be the WR10 build stage or private OpenCode temp")
    stage_root = ROOT if requested_stage == approved else temp
    assert_no_reparse(stage_root, requested_stage)
    stage = requested_stage
    if not stage.exists():
        stage.mkdir(parents=True)
    assert_no_reparse(stage_root, stage)
    if stage == ROOT or stage in ROOT.parents:
        raise ValueError("refusing package stage at repository root/ancestor")
    if receipt_path.is_symlink() or (receipt_path.exists() and getattr(receipt_path.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
        raise ValueError("previous receipt cannot be a reparse point")
    receipt_path = receipt_path.resolve(strict=True)
    receipt_bytes = receipt_path.read_bytes()
    receipt = json.loads(receipt_bytes)
    if preserve_bindings and (binding_plan is not None or legacy_retirement):
        raise ValueError("preserve_bindings cannot be combined with a binding plan or retirement")
    if not isinstance(receipt.get("files"), list) or not isinstance(receipt.get("source_hashes"), dict):
        raise ValueError("previous publication receipt has an invalid shape")
    selected = reviewed_source_hashes
    if selected is not None and (not isinstance(selected,dict) or any(not isinstance(k,str) or not isinstance(v,str) or len(v)!=64 or any(c not in "0123456789abcdef" for c in v) for k,v in selected.items())):
        raise ValueError("reviewed source hashes must be a source-to-SHA256 map")
    files = {}
    shared_root = Path(receipt.get("shared_root") or "")
    if not shared_root.is_absolute():
        raise ValueError("previous receipt does not bind an absolute shared installation root")

    if legacy_retirement:
        if binding_plan is None: raise ValueError("legacy retirement requires an explicit named target plan")
        if binding_plan.is_symlink() or not binding_plan.is_file() or temp not in binding_plan.resolve().parents:
            raise ValueError("retirement plan must be an existing regular private OpenCode temp file")
        receipt["_path"] = str(receipt_path); receipt["_sha256"] = hashlib.sha256(receipt_bytes).hexdigest()
        from workflow_coordinator_bindings import stage_legacy_retirement_plan
        staged = stage_legacy_retirement_plan(binding_plan, stage, receipt,
            main_root=Path(receipt.get("main_root", "")), shared_root=shared_root)
        manifest = {"schema_version":1,"package":"workflow-legacy-retirement","source_root":str(ROOT),
            "previous_receipt":{"path":str(receipt_path),"sha256":hashlib.sha256(receipt_bytes).hexdigest()},
            "files":{},"bindings":{"status":"planned-legacy-retirement","manifest":"bindings/binding-manifest.json",
                "sha256":digest(stage/"bindings/binding-manifest.json"),"source_plan":str(binding_plan.resolve()),
                "source_plan_sha256":staged["source_plan_sha256"]}}
        manifest_path=stage/"manifest.json"; assert_no_reparse(stage,manifest_path)
        payload=(json.dumps(manifest,indent=2,sort_keys=True)+"\n").encode()
        if manifest_path.exists() and manifest_path.read_bytes()!=payload: raise ValueError("refusing to overwrite an unowned/stale package manifest")
        manifest_path.write_bytes(payload); return manifest

    def bind_destination(key: str, source: Path, staged_target: Path) -> dict:
        destination = Path(key)
        if destination.is_absolute() or ".." in destination.parts:
            raise ValueError(f"unsafe package destination: {key}")
        live = shared_root.joinpath(*destination.parts)
        assert_no_reparse(shared_root, live)
        matches = [row for row in receipt["files"] if row.get("target") == "shared" and row.get("path") == key]
        if len(matches) > 1:
            raise ValueError(f"duplicate receipt ownership row for shared/{key}")
        row = matches[0] if matches else None
        old_hash = None
        backup_path = None
        backup_sha256 = None
        if live.exists():
            if not live.is_file() or live.is_symlink() or row is None or row.get("deleted") or row.get("new_sha256") != digest(live):
                raise ValueError(f"refusing unowned/conflicting shared target: {key}")
            old_hash = digest(live)
            backup = stage / "backups/shared" / destination
            assert_no_reparse(stage, backup)
            backup.parent.mkdir(parents=True, exist_ok=True)
            assert_no_reparse(stage, backup)
            if backup.exists() and digest(backup) != old_hash:
                raise ValueError(f"refusing conflicting staged backup: {backup}")
            shutil.copyfile(live, backup)
            backup_path = backup.relative_to(stage).as_posix()
            backup_sha256 = digest(backup)
        elif row is not None and not row.get("deleted"):
            raise ValueError(f"receipt-owned target is missing; refusing stale plan: {key}")
        return {"source_sha256": digest(source), "old_target_sha256": old_hash,
                "backup_path": backup_path, "backup_sha256": backup_sha256,
                "ownership": "receipt-replace" if old_hash else "create",
                "receipt_target": "shared", "receipt_path": key}

    used_sources=set()
    def package_source(source_key: str, destination_key: str, root_source: Path) -> Path | None:
        """Use reviewed checkout bytes only for explicitly selected sources."""
        used_sources.add(source_key)
        if selected is None:
            return root_source
        if source_key in selected:
            assert_no_reparse(ROOT, root_source)
            if not root_source.is_file() or digest(root_source)!=selected[source_key]:
                raise ValueError(f"reviewed source hash mismatch: {source_key}")
            return root_source
        dest=Path(destination_key)
        live=shared_root.joinpath(*dest.parts);assert_no_reparse(shared_root,live)
        owned=[r for r in receipt["files"] if r.get("target")=="shared" and r.get("path")==destination_key]
        if not owned and not live.exists():
            return None
        if len(owned)!=1 or owned[0].get("deleted") or not live.is_file() or live.is_symlink() or digest(live)!=owned[0].get("new_sha256"):
            raise ValueError(f"unselected package member lacks receipt-verified installed baseline: {destination_key}")
        return live

    for name in SOURCES:
        key = f"tooling/agent_workflow/{name}"
        source = package_source(key,key,ROOT / key)
        if source is None: continue
        if selected is None or key in selected: assert_no_reparse(ROOT, ROOT / key)
        if not source.is_file():
            raise ValueError(f"missing or linked source: {source}")
        target = stage / "tooling/agent_workflow" / name
        assert_no_reparse(stage, target)
        target.parent.mkdir(parents=True, exist_ok=True)
        assert_no_reparse(stage, target)
        if target.exists() and digest(target) != digest(source):
            raise ValueError(f"refusing to overwrite unowned/stale stage target: {target}")
        shutil.copyfile(source, target)
        files[key] = {"source": key, "target": key, "sha256": digest(target), **bind_destination(key, source, target)}
    for source_key, destinations in SOURCE_ALIASES.items():
        for key in destinations:
            source = package_source(source_key, key, ROOT / source_key)
            if source is None:
                continue
            if selected is None or source_key in selected:
                assert_no_reparse(ROOT, ROOT / source_key)
            else:
                assert_no_reparse(shared_root, source)
            if not source.is_file() or source.is_symlink():
                raise ValueError(f"missing or linked source: {source}")
            target = stage / key
            assert_no_reparse(stage, target)
            target.parent.mkdir(parents=True, exist_ok=True)
            assert_no_reparse(stage, target)
            if target.exists() and digest(target) != digest(source):
                raise ValueError(f"refusing to overwrite unowned/stale stage target: {target}")
            shutil.copyfile(source, target)
            files[key] = {"source": source_key, "target": key, "sha256": digest(target), **bind_destination(key, source, target)}
    for name in ASSETS:
        key = f"docs/development-workflow-redesign/{name}"
        source = package_source(key,key,ROOT / key)
        if source is None: continue
        if selected is None or key in selected: assert_no_reparse(ROOT, ROOT / key)
        target = stage / "docs/development-workflow-redesign" / name
        assert_no_reparse(stage, target)
        target.parent.mkdir(parents=True, exist_ok=True)
        assert_no_reparse(stage, target)
        if target.exists() and digest(target) != digest(source):
            raise ValueError(f"refusing to overwrite unowned/stale stage target: {target}")
        shutil.copyfile(source, target)
        files[key] = {"source": key, "target": key, "sha256": digest(target), **bind_destination(key, source, target)}
    for relative in ROOT_ASSETS:
        source = package_source(relative,relative,ROOT / relative)
        if source is None: continue
        if selected is None or relative in selected: assert_no_reparse(ROOT, ROOT / relative)
        target = stage / relative
        assert_no_reparse(stage, target)
        target.parent.mkdir(parents=True, exist_ok=True)
        assert_no_reparse(stage, target)
        if target.exists() and digest(target) != digest(source):
            raise ValueError(f"refusing to overwrite unowned/stale stage target: {target}")
        shutil.copyfile(source, target)
        files[relative] = {"source": relative, "target": relative, "sha256": digest(target), **bind_destination(relative, source, target)}
    docs_index_root = ROOT / "docs/development-workflow-redesign/package/package-docs.json"
    docs_index_path = package_source("docs/development-workflow-redesign/package/package-docs.json",
        "docs/development-workflow-redesign/package/package-docs.json",docs_index_root)
    if docs_index_path is None:
        raise ValueError("package documentation index is not available")
    if selected is None or "docs/development-workflow-redesign/package/package-docs.json" in selected:
        assert_no_reparse(ROOT, docs_index_path)
    else:
        assert_no_reparse(shared_root, docs_index_path)
    docs_index = json.loads(docs_index_path.read_text(encoding="utf-8"))
    if docs_index.get("version") != 1 or not isinstance(docs_index.get("files"), list):
        raise ValueError("unsupported package documentation manifest")
    for row in docs_index["files"]:
        source_relative = safe_relative(row["source"], "package documentation source")
        relative_target = safe_relative(row["target"], "package documentation target")
        source = package_source(row["source"],relative_target.as_posix(),ROOT / source_relative)
        if source is None: continue
        if selected is None or row["source"] in selected: assert_no_reparse(ROOT, ROOT / source_relative)
        if not source.is_file() or source.is_symlink():
            raise ValueError(f"unsafe/missing package documentation member: {row}")
        target = stage / relative_target
        assert_no_reparse(stage, target)
        target.parent.mkdir(parents=True, exist_ok=True)
        assert_no_reparse(stage, target)
        if target.exists() and digest(target) != digest(source):
            raise ValueError(f"refusing to overwrite unowned/stale stage target: {target}")
        shutil.copyfile(source, target)
        key = relative_target.as_posix()
        if key in files:
            raise ValueError(f"duplicate package destination: {key}")
        files[key] = {"source": row["source"], "target": key, "sha256": digest(target), **bind_destination(key, source, target)}
    index_target = stage / "docs/development-workflow-redesign/package/package-docs.json"
    assert_no_reparse(stage, index_target)
    if index_target.exists() and index_target.read_bytes() != docs_index_path.read_bytes():
        raise ValueError("refusing to overwrite unowned/stale documentation index")
    index_target.parent.mkdir(parents=True, exist_ok=True)
    assert_no_reparse(stage, index_target)
    shutil.copyfile(docs_index_path, index_target)
    index_key = "docs/development-workflow-redesign/package/package-docs.json"
    files[index_key] = {"source": index_key, "target": index_key, "sha256": digest(index_target), **bind_destination(index_key, docs_index_path, index_target)}
    if selected is not None and set(selected)-used_sources:
        raise ValueError(f"reviewed source selection contains unknown package members: {sorted(set(selected)-used_sources)}")
    manifest = {"schema_version": 1, "package": "workflow-coordinator-inactive", "source_root": str(ROOT), "previous_receipt": {"path": str(receipt_path), "sha256": hashlib.sha256(receipt_bytes).hexdigest()}, "files": files}
    if preserve_bindings:
        main_root=Path(receipt.get("main_root", ""))
        if not main_root.is_absolute(): raise ValueError("previous receipt does not bind an absolute MAIN root")
        config_rows=[row for row in receipt["files"] if row.get("path")=="opencode.jsonc" and row.get("target") in {"main",*(f"MoonStation14-agent-0{i}" for i in range(1,6))}]
        configs={row["target"]:row.get("new_sha256") for row in config_rows if not row.get("deleted")}
        if len(configs)!=len(config_rows) or not configs: raise ValueError("receipt lacks unique owned current configuration rows")
        for name,sha in configs.items():
            root=main_root if name=="main" else main_root.parent/name
            config=root/"opencode.jsonc"; assert_no_reparse(root,config)
            if _hash(config)!=sha: raise ValueError(f"receipt-owned config changed: {name}/opencode.jsonc")
            config_row=next(row for row in config_rows if row.get("target")==name)
            scope=config_row.get("config_scope",{})
            overlay_scope=scope.get("local_overlay") if isinstance(scope,dict) else None
            if overlay_scope is not None:
                if not isinstance(overlay_scope,dict) or overlay_scope.get("path")!=".opencode/workflow/opencode-local.json" or not isinstance(overlay_scope.get("sha256"),str):
                    raise ValueError(f"receipt local overlay scope is malformed: {name}")
                overlay=root/overlay_scope["path"]; assert_no_reparse(root,overlay)
                overlay_rows=[r for r in receipt["files"] if r.get("target")==name and r.get("path")==overlay_scope["path"] and not r.get("deleted")]
                if len(overlay_rows)!=1 or overlay_rows[0].get("new_sha256")!=overlay_scope["sha256"] or _hash(overlay)!=overlay_scope["sha256"]:
                    raise ValueError(f"receipt-owned local overlay changed: {name}")
        manifest["bindings"]={"status":"preserve-current","configs":configs}
    elif binding_plan is not None:
        from workflow_coordinator_bindings import stage_binding_plan
        if binding_plan.is_symlink() or not binding_plan.is_file() or temp not in binding_plan.resolve().parents:
            raise ValueError("binding plan must be an existing regular private OpenCode temp file")
        receipt["_path"] = str(receipt_path)
        receipt["_sha256"] = hashlib.sha256(receipt_bytes).hexdigest()
        try: plan_mode=json.loads(binding_plan.read_text(encoding="utf-8")).get("mode")
        except (UnicodeError, json.JSONDecodeError): plan_mode=None
        if plan_mode == "inspection-safe-shell":
            from workflow_coordinator_bindings import stage_shell_policy_retirement_plan
            binding_manifest=stage_shell_policy_retirement_plan(binding_plan,stage,receipt,
                main_root=Path(receipt.get("main_root", "")),shared_root=shared_root)
            manifest["bindings"]={"status":"planned-inspection-safe-shell","manifest":"bindings/binding-manifest.json",
                "sha256":digest(stage/"bindings/binding-manifest.json"),"source_plan":str(binding_plan.resolve()),
                "source_plan_sha256":binding_manifest["source_plan_sha256"]}
        elif plan_mode == "reviewer-search-permissions":
            from workflow_coordinator_bindings import stage_reviewer_search_permission_plan
            binding_manifest=stage_reviewer_search_permission_plan(binding_plan,stage,receipt,
                main_root=Path(receipt.get("main_root", "")),shared_root=shared_root)
            manifest["bindings"]={"status":"planned-reviewer-search-permissions","manifest":"bindings/binding-manifest.json",
                "sha256":digest(stage/"bindings/binding-manifest.json"),"source_plan":str(binding_plan.resolve()),
                "source_plan_sha256":binding_manifest["source_plan_sha256"]}
        elif plan_mode == "shared-opencode-config":
            from workflow_coordinator_bindings import stage_shared_config_plan
            binding_manifest=stage_shared_config_plan(binding_plan,stage,receipt,
                main_root=Path(receipt.get("main_root", "")),shared_root=shared_root)
            manifest["bindings"]={"status":"planned-shared-opencode-config","manifest":"bindings/binding-manifest.json",
                "sha256":digest(stage/"bindings/binding-manifest.json"),"source_plan":str(binding_plan.resolve()),
                "source_plan_sha256":binding_manifest["source_plan_sha256"]}
        else:
            binding_manifest = stage_binding_plan(binding_plan, stage, receipt,
                main_root=Path(receipt.get("main_root", "")), shared_root=shared_root,
                adapter=shared_root / "tooling/agent_workflow/workflow_coordinator_mcp.py",
                python=shared_root / ".venv-workflow-coordinator/Scripts/python.exe")
            manifest["bindings"] = {"status": "planned-explicit-targets", "manifest": "bindings/binding-manifest.json",
                                    "sha256": digest(stage / "bindings/binding-manifest.json"),
                                    "source_plan": str(binding_plan.resolve()),
                                    "source_plan_sha256": binding_manifest["source_plan_sha256"]}
    else:
        manifest["bindings"] = {"status": "runtime-only-incomplete", "reason": "no explicit target binding plan supplied"}
    manifest_path = stage / "manifest.json"
    assert_no_reparse(stage, manifest_path)
    content = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    if manifest_path.exists() and manifest_path.read_bytes() != content:
        raise ValueError("refusing to overwrite an unowned/stale package manifest")
    manifest_path.write_bytes(content)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage", required=True, type=Path)
    parser.add_argument("--previous-receipt", required=True, type=Path)
    parser.add_argument("--binding-plan", type=Path)
    parser.add_argument("--legacy-retirement", action="store_true")
    parser.add_argument("--apply-package", action="store_true")
    parser.add_argument("--rollback-receipt", type=Path)
    parser.add_argument("--output-receipt", type=Path)
    parser.add_argument("--publication-pointer", type=Path)
    parser.add_argument("--expected-pointer-sha256", required=False)
    args = parser.parse_args()
    if args.apply_package or args.rollback_receipt:
        if not args.output_receipt: parser.error("apply/rollback requires --output-receipt")
        if not args.publication_pointer or not args.expected_pointer_sha256: parser.error("apply/rollback requires publication pointer path and expected SHA-256")
        result = publish(args.stage, args.previous_receipt, args.output_receipt, pointer_path=args.publication_pointer,
                         expected_pointer_sha256=args.expected_pointer_sha256, rollback_receipt=args.rollback_receipt)
        print(f"coordinator package {result['status']}: {len(result.get('actions', []))} actions")
    else:
        result = build(args.stage, args.previous_receipt, args.binding_plan, legacy_retirement=args.legacy_retirement)
        print(f"staged {len(result['files'])} files; inactive, no installation performed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
