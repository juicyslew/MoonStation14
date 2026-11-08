"""Entry point for checking and applying the shared OpenCode configuration."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace


def _main_root() -> Path:
    root = Path(__file__).resolve().parents[2]
    if root.name != "MoonStation14":
        raise ValueError("run this command from the MAIN MoonStation14 checkout")
    return root


def _pinned_python(main: Path) -> Path:
    shared = main.parent / "MoonStation14-shared"
    python = shared / ".venv-workflow-coordinator/Scripts/python.exe"
    if not python.is_file():
        raise ValueError("pinned shared workflow Python is missing")
    if Path(sys.prefix).resolve() != python.parent.parent.resolve():
        env = dict(os.environ)
        env["PYTHONPATH"] = str(main / "tooling/agent_workflow")
        result = subprocess.run([str(python), str(Path(__file__).resolve()), *sys.argv[1:]], env=env)
        raise SystemExit(result.returncode)
    return python


def _plan(main: Path, initialize: bool, refresh_reviewer_instructions: bool = False) -> dict:
    import workflow_coordinator_bindings as bindings
    from workflow_coordinator_bindings import _index_digest

    template = main / "tooling/opencode/shared-config.jsonc"
    rows = []
    for target in ("main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))):
        root = main if target == "main" else main.parent / target
        cfg = root / "opencode.jsonc"
        overlay = root / ".opencode/workflow/opencode-local.json"
        gitignore = root / ".gitignore"
        digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
        rows.append({"target": target, "expected_config_sha256": digest(cfg),
                     "expected_local_sha256": digest(overlay),
                     "expected_gitignore_sha256": digest(gitignore),
                     "expected_index_digest": _index_digest(root)})
    tracked = any(subprocess.run(["git", "-C", str(main if r["target"] == "main" else main.parent / r["target"]),
                                  "ls-files", "--error-unmatch", "opencode.jsonc"], stdout=subprocess.DEVNULL,
                                 stderr=subprocess.DEVNULL).returncode == 0 for r in rows)
    result = {"version": 1, "mode": "shared-opencode-config",
              "template": {"path": str(template.resolve()), "sha256": hashlib.sha256(template.read_bytes()).hexdigest()},
              "initialize": initialize, "untrack_generated": tracked, "targets": rows}
    if refresh_reviewer_instructions:
        result["refresh_reviewer_instructions"] = True
    return bindings.validate_shared_config_plan_shape(result)


def _status(main: Path, initialize: bool):
    import workflow_opencode_config as oc
    import workflow_coordinator_package as package
    from workflow_coordinator_bindings import _index_digest
    pointer = json.loads((main / package.POINTER_RELATIVE).read_text(encoding="utf-8"))
    receipt_path = Path(pointer["receipt"]).resolve()
    receipt_bytes = receipt_path.read_bytes()
    installation = _installation(main)
    if (Path(installation["receipt"]).resolve() != receipt_path or
            hashlib.sha256(receipt_bytes).hexdigest() != installation["receipt_sha256"]):
        raise ValueError("publication pointer does not match current installation receipt")
    receipt = json.loads(receipt_bytes)
    template_bytes = (main / "tooling/opencode/shared-config.jsonc").read_bytes()
    template = oc.load_template(template_bytes)
    changed = set()
    for target in ("main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))):
        root = main if target == "main" else main.parent / target
        cfg = root / "opencode.jsonc"
        overlay_path = root / ".opencode/workflow/opencode-local.json"
        cfg_bytes = cfg.read_bytes() if cfg.is_file() else b""
        owners = [r for r in receipt.get("files", []) if r.get("target") == target and
                  r.get("path") == "opencode.jsonc" and not r.get("deleted")]
        if len(owners) != 1:
            raise ValueError(f"configuration is not receipt-owned: {target}")
        exists = overlay_path.is_file()
        if initialize and exists:
            raise ValueError(f"already initialized: {target}")
        if not initialize and not exists:
            return "NOT_INITIALIZED", target
        if exists:
            overlay_bytes = overlay_path.read_bytes()
            local_owner = [r for r in receipt.get("files", []) if r.get("target") == target and
                           r.get("path") == ".opencode/workflow/opencode-local.json" and not r.get("deleted")]
            if len(local_owner) != 1 or hashlib.sha256(overlay_bytes).hexdigest() != local_owner[0].get("new_sha256"):
                raise ValueError(f"local overlay is not receipt-owned: {target}")
            overlay = oc._overlay(overlay_bytes)
            try:
                equivalent = oc.configs_equivalent(oc._loads(cfg_bytes), oc.render_config(template, overlay))
            except (ValueError, UnicodeError):
                equivalent = False
            if not equivalent:
                changed.add(target)
            if hashlib.sha256(cfg_bytes).hexdigest() != owners[0].get("new_sha256"):
                changed.add(target)
            provenance = owners[0].get("config_scope", {}).get("shared_template", {})
            if provenance.get("path") != str((main / "tooling/opencode/shared-config.jsonc").resolve()) or provenance.get("sha256") != hashlib.sha256(template_bytes).hexdigest():
                changed.add(target)
        ignore = root / ".gitignore"
        ignore_bytes = ignore.read_bytes() if ignore.is_file() else b""
        if b"/.opencode/workflow/" not in ignore_bytes.splitlines(): changed.add(target)
        ignored = subprocess.run(["git", "-C", str(root), "check-ignore", "-q", ".opencode/workflow/opencode-local.json"],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if exists and ignored.returncode != 0: changed.add(target)
        tracked = subprocess.run(["git", "-C", str(root), "ls-files", "--error-unmatch", ".opencode/workflow/opencode-local.json"],
                                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if exists and tracked.returncode == 0: changed.add(target)
        cfg_ignored = subprocess.run(["git", "-C", str(root), "check-ignore", "-q", "opencode.jsonc"],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        cfg_tracked = subprocess.run(["git", "-C", str(root), "ls-files", "--error-unmatch", "opencode.jsonc"],
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if cfg_ignored.returncode != 0 or cfg_tracked.returncode == 0:
            changed.add(target)
        _index_digest(root)  # read-only index integrity input for the reviewed plan
    return ("CURRENT" if not changed else "STALE"), sorted(changed)


def _installation(main: Path) -> dict:
    path = main / ".opencode/workflow/current-installation.json"
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or not all(k in value for k in ("config", "health_file", "port", "receipt", "receipt_sha256")):
        raise ValueError("current installation authority is incomplete")
    return value


def _delegate(main: Path, plan: dict, python: Path):
    import deploy_workflow
    import workflow_coordinator_package as package
    pointer_path = main / package.POINTER_RELATIVE
    pointer_bytes = pointer_path.read_bytes()
    pointer = json.loads(pointer_bytes)
    receipt_path = Path(pointer["receipt"]).resolve()
    installation = _installation(main)
    if Path(installation["receipt"]).resolve() != receipt_path:
        raise ValueError("publication pointer does not match current installation")
    receipt_bytes = receipt_path.read_bytes()
    if hashlib.sha256(receipt_bytes).hexdigest() != installation["receipt_sha256"]:
        raise ValueError("current installation receipt hash mismatch")
    receipt = json.loads(receipt_bytes)
    health, config = Path(installation["health_file"]), Path(installation["config"])
    if not health.is_file() or not config.is_file(): raise ValueError("receipt deployment inputs unavailable")
    temp_root = Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode"
    temp_root.mkdir(parents=True, exist_ok=True)
    plan_bytes = json.dumps(plan, sort_keys=True).encode("utf-8")
    persistent = temp_root / ("shared-opencode-config-" + hashlib.sha256(plan_bytes).hexdigest())
    persistent.mkdir(parents=True, exist_ok=True)
    plan_path, hashes_path = persistent / "binding-plan.json", persistent / "reviewed-source-hashes.json"
    plan_path.write_bytes(plan_bytes)
    if hashlib.sha256(plan_path.read_bytes()).hexdigest() != hashlib.sha256(plan_bytes).hexdigest():
        raise ValueError("private plan persistence verification failed")
    rel = "tooling/opencode/shared-config.jsonc"
    current_hash = hashlib.sha256((main / rel).read_bytes()).hexdigest()
    installed = receipt.get("source_hashes", {}).get(rel)
    hashes_path.write_text(json.dumps({rel: current_hash} if installed != current_hash else {}), encoding="utf-8")
    args = SimpleNamespace(config=config, health_file=health, receipt=receipt_path,
        receipt_sha256=hashlib.sha256(receipt_bytes).hexdigest(),
        expected_pointer_sha256=hashlib.sha256(pointer_bytes).hexdigest(),
        binding_plan=plan_path, binding_plan_sha256=hashlib.sha256(plan_path.read_bytes()).hexdigest(),
        python=python, reviewed_source_hashes=hashes_path,
        preflight=True, execute=False, port=installation["port"], opencode_server=None,
        repair_stalled_run=None, repair_queued_review=None, repair_terminal_review=None)
    preflight = deploy_workflow.run(args)
    if preflight.get("status") != "READY": raise ValueError("deployment preflight refused")
    args.preflight, args.execute = False, True
    return deploy_workflow.run(args)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("check", "init", "apply"))
    parser.add_argument("--refresh-reviewer-instructions", action="store_true",
                        help="refresh only existing dedicated reviewer system instructions in receipt-owned overlays")
    args = parser.parse_args(argv)
    try:
        if args.refresh_reviewer_instructions and args.command != "apply":
            raise ValueError("--refresh-reviewer-instructions is valid only with apply")
        root = _main_root()
        python = _pinned_python(root)
        workflow_dir = str(root / "tooling/agent_workflow")
        if workflow_dir not in sys.path:
            sys.path.insert(0, workflow_dir)
        plan = _plan(root, args.command == "init", args.refresh_reviewer_instructions)
        if args.command == "check":
            status, detail = _status(root, initialize=False)
            print(json.dumps({"status": status, "detail": detail}, sort_keys=True))
            return 0
        status, detail = _status(root, initialize=args.command == "init")
        if args.command == "apply" and status == "NOT_INITIALIZED":
            raise ValueError(f"{detail} is not initialized; run init after provisioning local bindings")
        if args.command == "apply" and status == "CURRENT" and not args.refresh_reviewer_instructions:
            print(json.dumps({"status": "UP_TO_DATE"}, sort_keys=True))
            return 0
        print(json.dumps(_delegate(root, plan, python), sort_keys=True))
    except Exception as exc:
        print(f"OpenCode config refused: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
