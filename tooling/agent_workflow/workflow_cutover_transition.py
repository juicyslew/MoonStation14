"""One-shot, receipt-bound WR12 transition assembly; never an auto-recovery path."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

try:
    import admission, gate_dispatch, gate_execution, workflow_coordinator
    import workflow_cutover, workflow_cutover_api, workflow_cutover_history
    import workflow_cutover_inventory, workflow_cutover_process, workflow_coordinator_package
    import workflow_coordinator_bindings, workflow_supervisor
except ModuleNotFoundError:
    from tooling.agent_workflow import admission, gate_dispatch, gate_execution, workflow_coordinator
    from tooling.agent_workflow import workflow_cutover, workflow_cutover_api, workflow_cutover_history
    from tooling.agent_workflow import workflow_cutover_inventory, workflow_cutover_process
    from tooling.agent_workflow import workflow_coordinator_package, workflow_coordinator_bindings, workflow_supervisor


class TransitionHeld(RuntimeError):
    """The operation stopped without claiming a completed cutover."""


class _McpRosterHeld(TransitionHeld):
    def __init__(self, message, http_status=None):
        super().__init__(message)
        self.http_status = http_status


_FIELDS = {
    "version", "plan_sha256", "operation_id", "operation_root", "main_root", "exchange_root", "shared_root",
    "hold_receipt_path", "bindings", "lower_bound_ms", "api_server", "legacy_mcp",
    "previous_receipt_path", "expected_receipt_sha256", "expected_observer_identity",
    "history_output_root", "stage", "publication_receipt_path", "pointer_path", "expected_pointer_sha256",
    "config_path", "health_file", "health_token_file", "port", "activation_nonce",
    "expected_config_sha256", "expected_runtime_entry_sha256", "expected_python_sha256",
}
_HASH_FIELDS = ("plan_sha256", "expected_receipt_sha256", "expected_pointer_sha256",
                "expected_config_sha256", "expected_runtime_entry_sha256", "expected_python_sha256")


def _sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _plan_digest(plan: dict) -> str:
    body = {key: value for key, value in plan.items() if key != "plan_sha256"}
    return _sha_bytes(json.dumps(body, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8"))


def _safe_existing_file(value, label):
    if not isinstance(value, str) or not value.strip(): raise ValueError(f"{label} must be an absolute file path")
    path = Path(value)
    if not path.is_absolute() or not path.is_file() or path.is_symlink(): raise ValueError(f"{label} is missing or not a regular file")
    workflow_coordinator_package.assert_no_reparse(Path(path.anchor), path)
    return path


def _safe_existing_dir(value, label):
    if not isinstance(value, str) or not value.strip(): raise ValueError(f"{label} must be an absolute directory")
    path = Path(value)
    if not path.is_absolute() or not path.is_dir() or path.is_symlink(): raise ValueError(f"{label} is missing or not a directory")
    workflow_coordinator_package.assert_no_reparse(Path(path.anchor), path)
    return path


def _private_output(value, prefix, *, must_exist):
    if not isinstance(value, str) or not value.strip(): raise ValueError("private output path is required")
    path = Path(os.path.abspath(value))
    base = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
    if path == base or base not in path.parents or not path.name.startswith(prefix):
        raise ValueError("transition output must be a named private OpenCode temp child")
    workflow_coordinator_package.assert_no_reparse(base, path)
    if must_exist and not path.is_dir(): raise ValueError("private output parent/root is unavailable")
    if not must_exist and path.exists(): raise ValueError("private output path already exists")
    return path


def _known_worktrees(main_root: Path):
    return {main_root, *(main_root.parent / f"MoonStation14-agent-0{i}" for i in range(1, 6))}


def _validate_plan(plan):
    """Resolve and verify every authority-bearing file before journal or effects."""
    if not isinstance(plan, dict) or set(plan) != _FIELDS or plan.get("version") != 1:
        raise ValueError("transition plan fields/version are incomplete or unexpected")
    if not isinstance(plan["plan_sha256"], str) or _plan_digest(plan) != plan["plan_sha256"]:
        raise ValueError("transition plan digest mismatch")
    for key in _HASH_FIELDS:
        value = plan[key]
        if not isinstance(value, str) or len(value) != 64 or any(c not in "0123456789abcdef" for c in value):
            raise ValueError(f"{key} must be a lowercase SHA-256")
    for key in ("operation_id", "activation_nonce"):
        if not isinstance(plan[key], str) or not plan[key].strip(): raise ValueError(f"{key} must be nonempty")
    if type(plan["lower_bound_ms"]) is not int or plan["lower_bound_ms"] < 0: raise ValueError("invalid API freshness cursor")
    if type(plan["port"]) is not int or not 1 <= plan["port"] <= 65535: raise ValueError("invalid coordinator port")

    main = _safe_existing_dir(plan["main_root"], "main_root")
    exchange = _safe_existing_dir(plan["exchange_root"], "exchange_root")
    shared = _safe_existing_dir(plan["shared_root"], "shared_root")
    expected_pointer = main / workflow_coordinator_package.POINTER_RELATIVE
    pointer = _safe_existing_file(plan["pointer_path"], "publication pointer")
    if os.path.normcase(str(pointer)) != os.path.normcase(str(expected_pointer)):
        raise ValueError("publication pointer is not the authoritative MAIN pointer")
    if _sha_bytes(pointer.read_bytes()) != plan["expected_pointer_sha256"]:
        raise ValueError("publication pointer changed from expected hash")

    operation_root = _private_output(plan["operation_root"], "wr12-transition-", must_exist=False)
    history_root = _private_output(plan["history_output_root"], "wr12-legacy-history-", must_exist=False)
    receipt_out = _private_output(plan["publication_receipt_path"], "wr12-publication-", must_exist=False)
    if plan["operation_id"] in (operation_root.name, history_root.name):
        raise ValueError("operation identity must be distinct from output directory names")
    stage = _safe_existing_dir(plan["stage"], "package stage")
    coordinator_key = "tooling/agent_workflow/workflow_coordinator.py"
    manifest_path = stage / "manifest.json"
    workflow_coordinator_package.assert_no_reparse(stage, manifest_path)
    manifest_bytes = manifest_path.read_bytes()
    staged_manifest = json.loads(manifest_bytes.decode("utf-8"))
    package_files = staged_manifest.get("files") if isinstance(staged_manifest, dict) else None
    coordinator_row = package_files.get(coordinator_key) if isinstance(package_files, dict) else None
    if (not isinstance(coordinator_row, dict) or coordinator_row.get("source") != coordinator_key or
            coordinator_row.get("target") != coordinator_key or coordinator_row.get("receipt_target") != "shared" or
            coordinator_row.get("receipt_path") != coordinator_key):
        raise ValueError("staged package manifest does not receipt-bind the canonical shared coordinator entry")
    for field in ("sha256", "source_sha256"):
        digest = coordinator_row.get(field)
        if not isinstance(digest, str) or len(digest) != 64 or any(char not in "0123456789abcdef" for char in digest):
            raise ValueError(f"staged coordinator manifest {field} is not a lowercase SHA-256")
    staged_coordinator = stage / coordinator_key
    workflow_coordinator_package.assert_no_reparse(stage, staged_coordinator)
    if not staged_coordinator.is_file() or _sha_bytes(staged_coordinator.read_bytes()) != coordinator_row["sha256"]:
        raise ValueError("staged coordinator entry does not match package manifest hash")
    source_root = _safe_existing_dir(staged_manifest.get("source_root"), "package source root")
    if source_root.resolve() != Path(workflow_coordinator_package.ROOT).resolve():
        raise ValueError("package source root is not the local coordinator package owner root")
    coordinator_source = source_root / coordinator_key
    workflow_coordinator_package.assert_no_reparse(source_root, coordinator_source)
    if (not coordinator_source.is_file() or coordinator_source.is_symlink() or
            _sha_bytes(coordinator_source.read_bytes()) != coordinator_row["source_sha256"]):
        raise ValueError("package coordinator source differs from manifest source hash")

    previous = _safe_existing_file(plan["previous_receipt_path"], "previous publication receipt")
    if _sha_bytes(previous.read_bytes()) != plan["expected_receipt_sha256"]:
        raise ValueError("previous publication receipt changed from expected hash")
    previous_data = json.loads(previous.read_text(encoding="utf-8"))
    if staged_manifest.get("previous_receipt") != {"path":str(previous.resolve()),"sha256":plan["expected_receipt_sha256"]}:
        raise ValueError("staged coordinator manifest is not bound to the exact previous publication receipt")
    if (Path(previous_data.get("main_root", "")).resolve() != main.resolve() or
            Path(previous_data.get("shared_root", "")).resolve() != shared.resolve()):
        raise ValueError("previous receipt roots differ from explicit transition roots")
    if Path(plan["pointer_path"]).resolve() != expected_pointer.resolve(): raise ValueError("pointer path mismatch")
    pointer_data = json.loads(pointer.read_text(encoding="utf-8"))
    if Path(pointer_data.get("receipt", "")).resolve() != previous.resolve():
        raise ValueError("authoritative pointer does not name the exact previous receipt")
    source, _ = workflow_cutover_process._expected_from_receipt(previous, shared, plan["expected_receipt_sha256"])
    if not source.is_file(): raise ValueError("receipt-owned observer source is unavailable")

    hold_path = _safe_existing_file(plan["hold_receipt_path"], "caller-owned hold receipt")
    approved = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
    if hold_path == approved or approved not in hold_path.parents:
        raise ValueError("caller-owned hold receipt must be in private OpenCode temp")
    hold_receipt = json.loads(hold_path.read_text(encoding="utf-8"))
    if not isinstance(hold_receipt, dict) or not all(isinstance(hold_receipt.get(k), str) and hold_receipt[k]
            for k in ("path", "token", "sha256", "owner", "control_id")):
        raise ValueError("caller-owned intake hold receipt is malformed")
    if Path(hold_receipt["path"]).resolve() != (exchange / "gate-maintenance.json").resolve():
        raise ValueError("hold receipt does not name the caller-owned exchange intake hold")

    if not isinstance(plan["expected_observer_identity"], dict) or set(plan["expected_observer_identity"]) != {"pid", "created"}:
        raise ValueError("fresh expected observer identity is required")
    identity = plan["expected_observer_identity"]
    if type(identity["pid"]) is not int or identity["pid"] <= 0 or type(identity["created"]) is not int:
        raise ValueError("fresh expected observer identity is malformed")

    api_server = workflow_coordinator._loopback_server(plan["api_server"])
    if api_server != plan["api_server"].rstrip("/"): raise ValueError("API server must be the explicit normalized loopback endpoint")
    if not isinstance(plan["bindings"], list) or not plan["bindings"]: raise ValueError("explicit session bindings are required")
    session_ids = set(); worktrees = _known_worktrees(main)
    for binding in plan["bindings"]:
        if not isinstance(binding, dict) or set(binding) != {"session_id", "agent", "worktree"}:
            raise ValueError("API-probe binding must contain only session_id, agent, and worktree")
        if any(not isinstance(binding[k], str) or not binding[k].strip() for k in binding):
            raise ValueError("API-probe binding fields must be nonempty strings")
        if binding["session_id"] in session_ids: raise ValueError("duplicate session id in API-probe bindings")
        session_ids.add(binding["session_id"])
        bound_worktree = _safe_existing_dir(binding["worktree"], "session binding worktree")
        if bound_worktree.resolve() not in {root.resolve() for root in worktrees}:
            raise ValueError("session binding worktree is outside the explicit known workspace set")

    mcp = plan["legacy_mcp"]
    if not isinstance(mcp, list) or not mcp: raise ValueError("explicit known-owned legacy MCP servers are required")
    seen_mcp = set(); known = {root.resolve() for root in worktrees}
    for row in mcp:
        if not isinstance(row, dict) or set(row) != {"name", "worktree", "config_sha256", "config_path"}:
            raise ValueError("legacy MCP row shape is invalid")
        if row["name"] != "ms14_workflow": raise ValueError("only the exact owned legacy workflow MCP may be disconnected")
        worktree = _safe_existing_dir(row["worktree"], "legacy MCP worktree")
        if worktree.resolve() not in known: raise ValueError("legacy MCP worktree is not an explicit known workspace")
        if worktree.resolve() in seen_mcp: raise ValueError("duplicate legacy MCP worktree")
        seen_mcp.add(worktree.resolve())
        config_path = _safe_existing_file(row["config_path"], "legacy MCP config")
        if config_path.resolve() != (worktree / "opencode.jsonc").resolve(): raise ValueError("legacy MCP config is not its worktree config")
        if row["config_sha256"] != _sha_bytes(config_path.read_bytes()): raise ValueError("legacy MCP config hash mismatch")
        target_name = "main" if worktree.resolve() == main.resolve() else worktree.name
        owned_rows = [entry for entry in previous_data.get("files", []) if isinstance(entry, dict) and
                      entry.get("target") == target_name and entry.get("path") == "opencode.jsonc"]
        if (len(owned_rows) != 1 or owned_rows[0].get("deleted") is not False or
                owned_rows[0].get("new_sha256") != row["config_sha256"]):
            raise ValueError("legacy MCP config hash is not owned by the previous publication receipt")
        raw_config = config_path.read_bytes()
        config_text, _bom, _original = workflow_coordinator_bindings._jsonc_text(raw_config)
        config = json.loads(config_text)
        if not isinstance(config, dict) or not isinstance(config.get("mcp", {}).get("servers"), dict) or "ms14_workflow" not in config["mcp"]["servers"]:
            raise ValueError("receipt-bound config does not contain the owned legacy MCP server")
        legacy_entry = config["mcp"]["servers"]["ms14_workflow"]
        if (not workflow_coordinator_bindings._legacy_entry(legacy_entry, worktree, shared) or
                legacy_entry.get("disabled") is not True):
            raise ValueError("legacy MCP must be the exact receipt-owned, persistently disabled entry")
        if workflow_coordinator_bindings._retired_config_bytes(raw_config, worktree, shared) != raw_config:
            raise ValueError("legacy MCP disabled config is not an unambiguous true token")
    if seen_mcp != known:
        raise ValueError("legacy retirement requires exactly the six known worktree configs")
    bound_roots = {Path(row["worktree"]).resolve() for row in plan["bindings"]}
    if not known.issubset(bound_roots):
        raise ValueError("every one of the six legacy MCP locations requires a freshly bound session")

    config_path = _safe_existing_file(plan["config_path"], "supervisor config")
    if _sha_bytes(config_path.read_bytes()) != plan["expected_config_sha256"]: raise ValueError("supervisor config hash drift")
    database = workflow_supervisor.WorkflowSupervisor._config_database(config_path)
    config = json.loads(config_path.read_text(encoding="utf-8"))
    admission_root = config.get("admission_root") if isinstance(config, dict) else None
    if not isinstance(admission_root, str) or not admission_root.strip():
        raise ValueError("production supervisor config must bind the cutover admission root")
    admission_path = Path(admission_root)
    if not admission_path.is_absolute() or admission_path.resolve() != exchange.resolve():
        raise ValueError("supervisor admission root must exactly match the cutover exchange root")
    bindings = config.get("bindings") if isinstance(config, dict) else None
    if not isinstance(bindings, list): raise ValueError("supervisor config has no author token bindings")
    author_worktrees = config.get("author_worktrees", [])
    if not isinstance(author_worktrees, list) or (not bindings and not author_worktrees):
        raise ValueError("supervisor config has no valid static or worktree author bindings")
    configured_tokens = set()
    token_values = set()
    callers = set()
    configured_repos = set()
    author_contexts = []
    context_paths = set()
    artifacts_path = Path(config["artifact_dir"])
    if not artifacts_path.is_absolute(): artifacts_path = config_path.parent / artifacts_path
    artifacts_path = artifacts_path.resolve()
    for index, row in enumerate([*bindings, *author_worktrees]):
        if (not isinstance(row, dict) or set(row) != {"token_file", "caller", "context_file"} or
                any(not isinstance(row.get(key), str) or not row[key].strip()
                    for key in ("token_file", "caller", "context_file"))):
            raise ValueError("configured author binding must contain only token_file, caller, and context_file")
        if row["caller"] in callers: raise ValueError("configured author caller names must be unique")
        callers.add(row["caller"])
        token_path = Path(row["token_file"])
        context_path = Path(row["context_file"])
        if not token_path.is_absolute(): token_path = config_path.parent / token_path
        if not context_path.is_absolute(): context_path = config_path.parent / context_path
        token_path = Path(os.path.abspath(token_path))
        context_path = Path(os.path.abspath(context_path))
        _safe_existing_file(str(token_path), "configured author token")
        _safe_existing_file(str(context_path), "configured author context")
        context_raw_bytes = context_path.read_bytes()
        context_raw = workflow_coordinator.workflow_store.strict_loads(context_raw_bytes.decode("utf-8"))
        if index < len(bindings):
            if not isinstance(context_raw, dict): raise ValueError("static author context must be an object")
            for field in ("repo", "publication_receipt_path"):
                context_raw[field] = str(workflow_coordinator.resolve_context_path(context_raw[field], context_path.parent))
            if isinstance(context_raw.get("artifacts"), list):
                for artifact in context_raw["artifacts"]:
                    if isinstance(artifact, dict) and isinstance(artifact.get("path"), str):
                        artifact["path"] = str(workflow_coordinator.resolve_context_path(artifact["path"], context_path.parent))
            static_context = workflow_coordinator.workflow_submission._context(context_raw)
            repo = Path(static_context.repo).resolve()
        else:
            if not isinstance(context_raw, dict) or set(context_raw) != {
                    "repo", "floors", "publication_receipt_path", "publication_receipt_sha256"}:
                raise ValueError("worktree author context does not match the coordinator floor-only contract")
            repo = workflow_coordinator.resolve_context_path(context_raw["repo"], context_path.parent)
            workflow_coordinator.workflow_scope.validate_worktree_floors(context_raw["floors"])
            receipt_path = workflow_coordinator.resolve_context_path(context_raw["publication_receipt_path"], context_path.parent)
            if (receipt_path.resolve() != previous.resolve() or context_raw["publication_receipt_sha256"] != plan["expected_receipt_sha256"] or
                    not receipt_path.is_file() or
                    _sha_bytes(receipt_path.read_bytes()) != context_raw["publication_receipt_sha256"]):
                raise ValueError("worktree author publication receipt is not bound to the previous receipt")
            approved = Path(os.environ["LOCALAPPDATA"]) / "Temp" / "opencode"
            if (not context_path.is_absolute() or context_path == approved or approved not in context_path.parents or
                    any(context_path == worktree or worktree in context_path.parents for worktree in worktrees)):
                raise ValueError("worktree author context must be an explicit private OpenCode temp file outside worktrees")
            workflow_coordinator_package.assert_no_reparse(approved, context_path)
            resolved_context = context_path.resolve()
            if resolved_context in context_paths:
                raise ValueError("worktree author context paths must be unique")
            context_paths.add(resolved_context)
            author_contexts.append({"path":context_path, "sha256":_sha_bytes(context_raw_bytes),
                                    "context":context_raw, "repo":repo})
        database_path = database.resolve()
        if (database_path == repo or repo in database_path.parents or
                artifacts_path == repo or repo in artifacts_path.parents):
            raise ValueError("coordinator database and artifacts must remain external to configured author worktrees")
        configured_repos.add(repo)
        token = token_path.read_text(encoding="utf-8").strip()
        if not token or token in token_values:
            raise ValueError("configured author token values must be nonempty and unique")
        token_values.add(token)
        configured_tokens.add(token_path.resolve())
    health_token_path = _safe_existing_file(plan["health_token_file"], "coordinator health token")
    if health_token_path.resolve() not in configured_tokens: raise ValueError("health token file is not an existing configured author binding")
    if not health_token_path.read_text(encoding="utf-8").strip(): raise ValueError("coordinator health token is empty")
    if database.exists():
        _safe_existing_file(str(database), "supervisor database")
    else:
        _safe_existing_dir(str(database.parent), "supervisor database parent")
        workflow_coordinator_package.assert_no_reparse(Path(database.anchor), database)

    runtime_path = Path(workflow_supervisor.__file__).resolve()
    base_executable = getattr(sys, "_base_executable", None)
    if not isinstance(base_executable, str) or not base_executable.strip():
        raise ValueError("supervisor base interpreter path is unavailable")
    python_path = Path(base_executable).resolve()
    if _sha_bytes(runtime_path.read_bytes()) != plan["expected_runtime_entry_sha256"]:
        raise ValueError("supervisor runtime source hash drift")
    if _sha_bytes(python_path.read_bytes()) != plan["expected_python_sha256"]:
        raise ValueError("supervisor interpreter hash drift")
    health_file = Path(plan["health_file"])
    if not health_file.is_absolute(): raise ValueError("health file must be absolute")
    workflow_coordinator_package.assert_no_reparse(Path(health_file.anchor), health_file)
    coordinator_path = shared / coordinator_key
    workflow_coordinator_package.assert_no_reparse(shared, coordinator_path)

    # Verify all declared output parents and authority paths before the journal is created.
    if not receipt_out.parent.is_dir(): raise ValueError("publication receipt output parent is unavailable")
    if not history_root.parent.is_dir(): raise ValueError("history output parent is unavailable")
    if operation_root.parent.resolve() != history_root.parent.resolve(): raise ValueError("private operation outputs must share the approved parent")
    return {"plan":plan, "main_root":main, "exchange_root":exchange, "shared_root":shared,
            "operation_root":operation_root, "history_output_root":history_root,
            "previous_receipt":previous, "publication_receipt":receipt_out,
            "pointer_path":pointer, "hold_receipt":hold_receipt, "hold_receipt_file":hold_path,
            "stage":stage, "config_path":config_path,
            "database":database, "health_file":health_file, "health_token_path":health_token_path,
            "runtime_path":runtime_path, "python_path":python_path, "source":source,
            "stage_manifest_path":manifest_path, "stage_manifest_sha256":_sha_bytes(manifest_bytes),
            "coordinator_path":coordinator_path, "coordinator_sha256":coordinator_row["sha256"],
             "coordinator_source_sha256":coordinator_row["source_sha256"],
             "coordinator_stage_path":staged_coordinator, "author_contexts":author_contexts}


def _refresh_author_contexts(contexts, receipt_path, published, journal):
    if not contexts:
        return
    if not isinstance(published, dict) or published.get("status") != "applied":
        raise TransitionHeld("publication owner did not return an applied receipt")
    workflow_coordinator_package.assert_no_reparse(Path(receipt_path.anchor), receipt_path)
    if not receipt_path.is_file() or receipt_path.is_symlink():
        raise TransitionHeld("applied publication did not leave its receipt file")
    receipt_bytes = receipt_path.read_bytes()
    receipt_sha = _sha_bytes(receipt_bytes)
    try:
        receipt_document = json.loads(receipt_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise TransitionHeld("published receipt file is not valid JSON") from exc
    if receipt_document != published:
        raise TransitionHeld("published receipt file differs from the publication owner's result")

    for binding in contexts:
        path = binding["path"]
        workflow_coordinator_package.assert_no_reparse(Path(path.anchor), path)
        if not path.is_file() or path.is_symlink() or _sha_bytes(path.read_bytes()) != binding["sha256"]:
            raise TransitionHeld("worktree author context changed after preflight")
    rows = []
    for binding in contexts:
        path = binding["path"]
        workflow_coordinator_package.assert_no_reparse(Path(path.anchor), path)
        if _sha_bytes(path.read_bytes()) != binding["sha256"]:
            raise TransitionHeld("worktree author context changed before refresh")
        context = dict(binding["context"])
        context["publication_receipt_path"] = str(receipt_path.resolve())
        context["publication_receipt_sha256"] = receipt_sha
        payload = (json.dumps(context, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
        workflow_coordinator_package._atomic_write(path, payload)
        rows.append({"path":str(path), "before_sha256":binding["sha256"], "after_sha256":_sha_bytes(payload)})
    journal.record("author_context_refresh", "REFRESHED", {"count":len(rows), "receipt_sha256":receipt_sha,
                   "contexts":rows})


class _Journal:
    def __init__(self, root, plan_sha256, admin_identity, activation_nonce, python_path, python_sha256):
        self.root = Path(root)
        self.root.mkdir(parents=False, exist_ok=False)
        self.path = self.root / "transition.json"
        self.data = {"version":1, "plan_sha256":plan_sha256, "admin_identity":dict(admin_identity),
                     "admin_identity_observed_at_ms":int(time.time()*1000), "activation_nonce":activation_nonce,
                     "python_path":str(python_path), "python_sha256":python_sha256,
                     "status":"STARTED", "phases":[], "receipts":[]}
        self._write()

    def _write(self):
        payload = (json.dumps(self.data, sort_keys=True, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
        workflow_coordinator_package._atomic_write(self.path, payload)

    def record(self, phase, status, receipt=None):
        row = {"phase":phase, "status":status, "at_ms":int(time.time()*1000)}
        if receipt is not None:
            json.dumps(receipt)
            safe = {"phase":phase, "value":receipt}
            self.data["receipts"].append(safe)
            row["receipt_sha256"] = _sha_bytes(json.dumps(receipt, sort_keys=True, separators=(",", ":")).encode())
        self.data["phases"].append(row)
        self.data["status"] = status
        self._write()


def _status_value(row):
    status = row.get("status")
    if not isinstance(status, dict) or not isinstance(status.get("status"), str): return None
    value = status["status"]
    return value if value in {"connected", "pending", "disabled", "failed", "needs_auth"} else None


def _mcp_roster(server, worktree, *, timeout=30):
    query = urllib.parse.urlencode({"location[directory]": str(worktree)})
    status, body = gate_dispatch._request(server, "GET", "/api/mcp?" + query, timeout=timeout)
    if status != 200 or not isinstance(body, dict) or not isinstance(body.get("location"), dict) or not isinstance(body.get("data"), list):
        raise _McpRosterHeld("location-scoped MCP roster is unavailable or malformed",
                             status if type(status) is int else None)
    directory = body["location"].get("directory")
    if (not isinstance(directory, str) or not directory.strip() or
            os.path.normcase(os.path.abspath(directory)) != os.path.normcase(os.path.abspath(worktree))):
        raise _McpRosterHeld("MCP roster resolved a different worktree", 200)
    rows = body["data"]
    if any(not isinstance(row, dict) or not isinstance(row.get("name"), str) or not row["name"].strip() or
           _status_value(row) is None for row in rows):
        raise _McpRosterHeld("MCP roster contains malformed or unknown server state", 200)
    return rows


def _assert_barrier_observed(barrier, where):
    held = barrier.recheck()
    if not isinstance(held, dict) or held.get("status") != "OBSERVED_ZERO":
        raise TransitionHeld(f"maintained intake barrier changed {where}")
    return held


def _legacy_absence_proof(plan, row, journal, barrier, phase):
    try:
        before_barrier = _assert_barrier_observed(barrier, f"before {phase} MCP absence check")
    except TransitionHeld:
        journal.record(f"mcp_absence_{phase}", "HELD", {"name":row["name"],"worktree":row["worktree"],
                      "barrier_before":"not_observed"})
        raise
    try:
        config_path = Path(row["config_path"])
        if not config_path.is_file() or _sha_bytes(config_path.read_bytes()) != row["config_sha256"]:
            raise TransitionHeld("persistently disabled legacy MCP config changed")
        main_root = Path(plan["main_root"]); target_name = "main" if Path(row["worktree"]).resolve() == main_root.resolve() else Path(row["worktree"]).name
        previous = json.loads(Path(plan["previous_receipt_path"]).read_text(encoding="utf-8"))
        owned = [item for item in previous.get("files", []) if item.get("target") == target_name and item.get("path") == "opencode.jsonc"]
        if len(owned) != 1 or owned[0].get("deleted") is not False or owned[0].get("new_sha256") != row["config_sha256"]:
            raise TransitionHeld("persistently disabled config lost exact publication-receipt ownership")
        raw_config = config_path.read_bytes()
        text, _bom, _original = workflow_coordinator_bindings._jsonc_text(raw_config)
        config = json.loads(text); entry = config.get("mcp", {}).get("servers", {}).get("ms14_workflow")
        if not workflow_coordinator_bindings._legacy_entry(entry, Path(row["worktree"]), Path(plan["shared_root"])) or entry.get("disabled") is not True:
            raise TransitionHeld("persistently disabled legacy MCP config is malformed or enabled")
        if workflow_coordinator_bindings._retired_config_bytes(raw_config, Path(row["worktree"]), Path(plan["shared_root"])) != raw_config:
            raise TransitionHeld("persistently disabled legacy MCP config token is ambiguous")
        roster = _mcp_roster(plan["api_server"], row["worktree"])
    except Exception as exc:
        journal.record(f"mcp_absence_{phase}", "HELD", {"name":row["name"],"worktree":row["worktree"],
                      "http_status":getattr(exc,"http_status",None),"error_type":type(exc).__name__})
        raise
    matches = [item for item in roster if item["name"] == row["name"]]
    evidence = {"name": row["name"], "worktree": row["worktree"],
                "config_sha256": row["config_sha256"], "state": "absent" if not matches else "present"}
    if len(matches) > 1 or (matches and _status_value(matches[0]) != "disabled"):
        journal.record(f"mcp_absence_{phase}", "HELD", evidence)
        raise TransitionHeld(f"owned legacy MCP is not uniquely disabled/absent before {phase}")
    try:
        after_barrier = _assert_barrier_observed(barrier, f"after {phase} MCP absence check")
    except TransitionHeld:
        journal.record(f"mcp_absence_{phase}", "HELD", {**evidence,"barrier_before":"OBSERVED_ZERO",
                      "barrier_after":"not_observed"})
        raise
    journal.record(f"mcp_absence_{phase}", "PROVED_INACTIVE", {**evidence,
                  "persisted_config_state":"disabled",
                  "barrier_before":before_barrier["status"],"barrier_after":after_barrier["status"]})


def _remove_legacy(plan, journal, barrier):
    for row in plan["legacy_mcp"]:
        try:
            before = _mcp_roster(plan["api_server"], row["worktree"])
        except Exception as exc:
            journal.record("mcp_removal_precondition", "HELD", {"name":row["name"],"worktree":row["worktree"],
                          "http_status":getattr(exc,"http_status",None),"error_type":type(exc).__name__})
            raise
        matches = [item for item in before if item["name"] == row["name"]]
        if len(matches) > 1:
            journal.record("mcp_removal_precondition", "HELD", {"name":row["name"],"worktree":row["worktree"],
                          "matching_rows":len(matches)})
            raise TransitionHeld("pre-removal roster contains duplicate owned MCP rows")
        old_state = _status_value(matches[0]) if matches else "absent"
        if old_state not in ("connected", "disabled", "absent"):
            journal.record("mcp_removal_precondition", "HELD", {"name":row["name"],"worktree":row["worktree"],
                          "state":old_state})
            raise TransitionHeld("owned MCP state is unknown or not safely disconnectable")
        journal.record("mcp_removal_precondition", "OBSERVED", {"name":row["name"],"worktree":row["worktree"],
                      "state":old_state,"http_status":200})
        journal.record("mcp_removal_intent", "DISCONNECTING" if old_state == "connected" else "VERIFYING_INACTIVE", {"name":row["name"],"worktree":row["worktree"],
                      "config_sha256":row["config_sha256"],"before":old_state})
        _assert_barrier_observed(barrier, "before legacy MCP removal")
        config_path = Path(row["config_path"])
        if not config_path.is_file() or _sha_bytes(config_path.read_bytes()) != row["config_sha256"]:
            journal.record("mcp_removal_precondition", "HELD", {"name":row["name"],"worktree":row["worktree"],
                          "reason":"config_hash_changed_before_effect"})
            raise TransitionHeld("owned legacy MCP config changed immediately before removal")
        deadline = time.monotonic() + 12.0
        path = "/api/experimental/mcp/" + urllib.parse.quote(row["name"], safe="") + "/disconnect?" + urllib.parse.urlencode({"location[directory]":row["worktree"]})
        delete_budget = deadline - time.monotonic()
        if delete_budget <= 0:
            journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],
                          "reason":"deadline_expired_before_delete"})
            raise TransitionHeld("legacy MCP removal deadline expired before DELETE")
        delete_result = None
        if old_state == "connected":
            try:
                response = gate_dispatch._request(plan["api_server"], "POST", path,
                                                  timeout=min(7.0, delete_budget))
                delete_result = {"http_status":response[0] if isinstance(response, tuple) and response else None,
                                 "result_state":"204" if isinstance(response, tuple) and len(response)==2 and response[0]==204 and response[1] is None else "non_204"}
            except Exception as exc:
                delete_result = {"result_state":"transport_error", "error_type":type(exc).__name__}
        else:
            delete_result = {"result_state":"not_required", "before":old_state}
        journal.record("mcp_removal_http", "OBSERVED", {"name":row["name"],"worktree":row["worktree"],**delete_result})
        absent_count = 0
        while True:
            remaining_time = deadline - time.monotonic()
            if remaining_time <= 0:
                journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],
                              "consecutive_absent":absent_count,"deadline_expired":True})
                raise TransitionHeld("legacy MCP removal absence was not proved within 12 seconds")
            _assert_barrier_observed(barrier, "during legacy MCP removal reconciliation")
            remaining_time = deadline - time.monotonic()
            if remaining_time <= 0:
                journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],
                              "consecutive_absent":absent_count,"reason":"deadline_expired_after_barrier"})
                raise TransitionHeld("legacy MCP removal deadline expired before reconciliation GET")
            try:
                after = _mcp_roster(plan["api_server"], row["worktree"], timeout=min(7.0, remaining_time))
            except Exception as exc:
                journal.record("mcp_removal_reconcile", "HELD", {"http_status":getattr(exc,"http_status",None),
                              "error_type":type(exc).__name__})
                raise
            remaining_rows = [item for item in after if item["name"] == row["name"]]
            if len(remaining_rows) > 1:
                state = "duplicate"
            elif not remaining_rows:
                state = "absent"
            else:
                state = _status_value(remaining_rows[0])
            late = time.monotonic() >= deadline
            journal.record("mcp_removal_reconcile", "LATE_NOT_ACCEPTED" if late else "OBSERVED",
                           {"name":row["name"],"worktree":row["worktree"],"http_status":200,
                            "state":state,"matching_rows":len(remaining_rows),"consecutive_absent_before":absent_count})
            if late:
                journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],
                              "state":state,"consecutive_absent":absent_count,"reason":"late_roster_response"})
                raise TransitionHeld("late MCP roster response cannot prove timely removal")
            if state in {"absent", "disabled"}:
                absent_count += 1
                if absent_count == 2:
                    _assert_barrier_observed(barrier, "after legacy MCP removal proof")
                    if time.monotonic() >= deadline:
                        journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],
                                      "state":"absent","consecutive_absent":2,"reason":"deadline_expired_after_barrier"})
                        raise TransitionHeld("legacy MCP removal deadline expired after final barrier check")
                    journal.record("mcp_removal_result", "PROVED_INACTIVE", {"name":row["name"],"worktree":row["worktree"],
                                  "consecutive_observations":2,"state":state,"delete_result":delete_result})
                    if time.monotonic() >= deadline:
                        journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],
                                      "state":"absent","consecutive_absent":2,"reason":"deadline_expired_during_result_journal"})
                        raise TransitionHeld("legacy MCP removal result journal completed after deadline")
                    break
            else:
                if state in {"failed", "needs_auth"}:
                    journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],"state":state})
                    raise TransitionHeld("legacy MCP entered terminal failed/needs_auth state")
                if state not in {"pending", "connected"}:
                    journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],"state":state})
                    raise TransitionHeld("legacy MCP reconciliation roster is ambiguous")
                absent_count = 0
            if time.monotonic() >= deadline:
                journal.record("mcp_removal_result", "HELD", {"name":row["name"],"worktree":row["worktree"],
                              "state":state,"consecutive_absent":absent_count,"reason":"deadline_expired_after_observation_journal"})
                raise TransitionHeld("legacy MCP removal observation journal completed after deadline")
            remaining_time = deadline-time.monotonic()
            if remaining_time > 0:
                time.sleep(min(0.2, remaining_time))


def _query_listeners(port):
    if type(port) is not int or not 1 <= port <= 65535: raise ValueError("invalid coordinator listener port")
    script = ("$ErrorActionPreference='Stop'; Get-NetTCPConnection -LocalPort " + str(port) +
              " -State Listen | Select-Object LocalPort,@{Name='State';Expression={$_.State.ToString()}},OwningProcess | ConvertTo-Json -Compress")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                            capture_output=True, text=True, timeout=5, check=True)
    data = json.loads(result.stdout) if result.stdout.strip() else []
    if isinstance(data, dict): data = [data]
    if not isinstance(data, list) or any(not isinstance(row, dict) for row in data):
        raise ValueError("listener query returned malformed rows")
    for row in data:
        if type(row.get("LocalPort")) is not int or row.get("State") != "Listen" or type(row.get("OwningProcess")) is not int:
            raise ValueError("listener query row schema is invalid")
    return data


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _health_proof(receipt, values, *, timeout=10.0):
    """Verify supervisor self-receipt, exact child listener identity, and real /v1/health schema."""
    plan = values["plan"]
    supervisor_id = receipt.get("supervisor_identity")
    child_id = receipt.get("coordinator_identity")
    if (not isinstance(supervisor_id, dict) or set(supervisor_id) != {"pid", "created"} or
            type(supervisor_id.get("pid")) is not int or type(supervisor_id.get("created")) is not int):
        return {"status":"HELD","reason":"supervisor raw process identity is unavailable"}
    if (not isinstance(child_id, dict) or set(child_id) != {"pid", "created"} or
            type(child_id.get("pid")) is not int or type(child_id.get("created")) is not int):
        return {"status":"HELD","reason":"supervisor raw child identity is unavailable"}
    if (receipt.get("scope_member") is not True or receipt.get("ready_go") is not True or
            not isinstance(receipt.get("scope_name"), str) or not receipt["scope_name"] or
            receipt.get("activation_nonce") != plan["activation_nonce"]):
        return {"status":"HELD","reason":"supervisor readiness scope or nonce is unverified"}
    if (Path(receipt.get("config_path", "")).resolve() != values["config_path"].resolve() or
            receipt.get("config_sha256") != plan["expected_config_sha256"] or
            Path(receipt.get("runtime_entry_path", "")).resolve() != values["runtime_path"] or
            receipt.get("runtime_entry_sha256") != plan["expected_runtime_entry_sha256"] or
            Path(receipt.get("python_path", "")).resolve() != values["python_path"] or
            receipt.get("python_sha256") != plan["expected_python_sha256"]):
        return {"status":"HELD","reason":"supervisor source/config/interpreter receipt differs from the plan"}
    deadline = time.monotonic() + timeout
    token = values["health_token_path"].read_text(encoding="utf-8").strip()
    if not token: return {"status":"HELD","reason":"configured health token is empty"}
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), _NoRedirect())
    last_observation = {"stage":"startup", "code":"awaiting_health_observation"}
    while time.monotonic() < deadline:
        stage = "supervisor_inputs"
        try:
            # Recheck exact file bytes after process launch, not only the startup preflight.
            if (_sha_bytes(values["config_path"].read_bytes()) != plan["expected_config_sha256"] or
                    _sha_bytes(values["runtime_path"].read_bytes()) != plan["expected_runtime_entry_sha256"] or
                    _sha_bytes(values["python_path"].read_bytes()) != plan["expected_python_sha256"]):
                return {"status":"HELD","reason":"supervisor input file hash changed during startup"}
            stage = "health_file"
            health_file = json.loads(values["health_file"].read_text(encoding="utf-8"))
            supervisor = health_file.get("supervisor") if isinstance(health_file, dict) else None
            child = health_file.get("child") if isinstance(health_file, dict) else None
            if (not isinstance(health_file, dict) or health_file.get("state") != "running" or
                    not isinstance(supervisor, dict) or supervisor.get("identity_status") != "verified" or
                    supervisor.get("identity") != supervisor_id or supervisor.get("activation_nonce") != plan["activation_nonce"] or
                    Path(supervisor.get("config_path", "")).resolve() != values["config_path"].resolve() or
                    supervisor.get("config_sha256") != plan["expected_config_sha256"] or
                    Path(supervisor.get("runtime_entry_path", "")).resolve() != values["runtime_path"] or
                    supervisor.get("runtime_entry_sha256") != plan["expected_runtime_entry_sha256"] or
                    Path(supervisor.get("python_path", "")).resolve() != values["python_path"] or
                    supervisor.get("python_sha256") != plan["expected_python_sha256"] or
                    not isinstance(child, dict) or child.get("coordinator_identity") != child_id or
                    child.get("pid") != child_id["pid"] or child.get("creation_identity") != child_id or
                    child.get("scope_name") != receipt["scope_name"]):
                return {"status":"HELD","reason":"supervisor health-file receipt or hash drift"}
            stage = "listener_query"
            try:
                listeners = _query_listeners(plan["port"])
            except (OSError, ValueError, json.JSONDecodeError, subprocess.SubprocessError) as exc:
                last_observation = {"stage":stage, "code":"query_error",
                                    "exception_type":type(exc).__name__}
                time.sleep(.1)
                continue
            matching = [row for row in listeners if row.get("LocalPort") == plan["port"] and row.get("State") == "Listen"]
            if not matching:
                last_observation = {"stage":stage, "code":"no_listener"}
                time.sleep(.1)
                continue
            if len(matching) != 1 or matching[0].get("OwningProcess") != child_id["pid"]:
                return {"status":"HELD","reason":"coordinator listener is ambiguous or owned by another PID"}
            if gate_execution.process_identity(child_id["pid"]) != child_id:
                return {"status":"HELD","reason":"coordinator listener PID creation identity mismatch"}
            stage = "health_http"
            request = urllib.request.Request("http://127.0.0.1:" + str(plan["port"]) + "/v1/health",
                                             headers={"Authorization":"Bearer " + token, "Accept":"application/json"}, method="GET")
            try:
                with opener.open(request, timeout=1) as response:
                    if response.status != 200: return {"status":"HELD","reason":"coordinator health HTTP status is not 200"}
                    raw = response.read(65537)
            except urllib.error.HTTPError as exc:
                if 300 <= exc.code < 400: return {"status":"HELD","reason":"coordinator health endpoint redirected"}
                last_observation = {"stage":stage, "code":"http_error", "http_status":exc.code}
                time.sleep(.1)
                continue
            if len(raw) > 65536: return {"status":"HELD","reason":"coordinator health body exceeds limit"}
            stage = "health_body"
            body = json.loads(raw.decode("utf-8"))
            if (not isinstance(body, dict) or body.get("status") != "running" or
                    type(body.get("pending_events")) is not int or body["pending_events"] < 0 or
                    body.get("last_error") is not None):
                return {"status":"HELD","reason":"coordinator health summary schema/state is not healthy"}
            return {"status":"VERIFIED","supervisor_identity":supervisor_id,
                    "coordinator_identity":child_id,"listener_pid":child_id["pid"],
                    "health_status":body["status"],"pending_events":body["pending_events"]}
        except FileNotFoundError:
            last_observation = {"stage":stage, "code":"not_found", "exception_type":"FileNotFoundError"}
            time.sleep(.1)
        except (OSError, ValueError, json.JSONDecodeError, subprocess.SubprocessError) as exc:
            code = "connection_error" if stage == "health_http" else "observation_error"
            last_observation = {"stage":stage, "code":code, "exception_type":type(exc).__name__}
            time.sleep(.1)
    return {"status":"HELD","reason":"bounded authenticated listener/health proof timed out",
            "last_observation":last_observation}


def run_transition(plan):
    """Execute once against explicit existing authority; never resumes or rolls back."""
    values = _validate_plan(plan)
    try:
        admin_identity = gate_execution.process_identity(os.getpid())
    except Exception as exc:
        raise TransitionHeld("current administrator process identity could not be verified") from exc
    if (not isinstance(admin_identity, dict) or set(admin_identity) != {"pid", "created"} or
            type(admin_identity.get("pid")) is not int or admin_identity["pid"] <= 0 or
            type(admin_identity.get("created")) is not int or admin_identity["created"] <= 0 or
            admin_identity["pid"] != os.getpid()):
        raise TransitionHeld("current administrator process has no canonical raw OS creation identity")
    journal = _Journal(values["operation_root"], plan["plan_sha256"], admin_identity,
                       plan["activation_nonce"], values["python_path"], plan["expected_python_sha256"])
    barrier = None
    barrier_entered = False
    barrier_released = False
    callback_success = False

    def release_barrier():
        nonlocal barrier_released
        if barrier_entered and not barrier_released:
            # Public owner-thread exit closes only this verified handle/locks; the durable hold file remains.
            try:
                barrier.__exit__(None, None, None)
            except BaseException as exc:
                try: journal.record("barrier_release_failure", "HELD", {"error":type(exc).__name__,"detail":str(exc)[:200]})
                except BaseException: pass
                raise
            else:
                barrier_released = True

    try:
        journal.record("preflight", "VALIDATED", {"plan_sha256":plan["plan_sha256"],
                      "previous_receipt_sha256":plan["expected_receipt_sha256"],
                      "pointer_sha256":plan["expected_pointer_sha256"]})
        barrier = workflow_cutover.DrainBarrier(values["exchange_root"], values["hold_receipt"])
        barrier.__enter__()
        barrier_entered = True
        snapshot = barrier.recheck()
        if not isinstance(snapshot, dict) or snapshot.get("status") != "OBSERVED_ZERO":
            raise TransitionHeld("maintained drain barrier is not clear")
        journal.record("drain_barrier", "CLEAR", snapshot)

        api = workflow_cutover_api.probe_cutover(plan["bindings"], plan["lower_bound_ms"], server=plan["api_server"])
        journal.record("api_probe_before", api.get("status", "HELD") if isinstance(api, dict) else "HELD", api if isinstance(api, dict) else {"malformed":True})
        if not isinstance(api, dict) or api.get("status") != "OBSERVED_CLEAR":
            raise TransitionHeld("pre-disconnect workflow API probe is not clear")
        _remove_legacy(plan, journal, barrier)
        api = workflow_cutover_api.probe_cutover(plan["bindings"], plan["lower_bound_ms"], server=plan["api_server"])
        journal.record("api_probe_after", api.get("status", "HELD") if isinstance(api, dict) else "HELD", api if isinstance(api, dict) else {"malformed":True})
        if not isinstance(api, dict) or api.get("status") != "OBSERVED_CLEAR":
            raise TransitionHeld("post-disconnect workflow API probe is not clear")
        snapshot = barrier.recheck()
        if not isinstance(snapshot, dict) or snapshot.get("status") != "OBSERVED_ZERO":
            raise TransitionHeld("maintained barrier changed before process inventory")

        for legacy_row in plan["legacy_mcp"]:
            _legacy_absence_proof(plan, legacy_row, journal, barrier, "observer_stop")
        for legacy_row in plan["legacy_mcp"]:
            _legacy_absence_proof(plan, legacy_row, journal, barrier, "observer_stop_effect")

        # Collect the age-bounded process receipt only after all potentially slow MCP checks.
        inventory = workflow_cutover_inventory.collect(
            barrier, values["previous_receipt"], plan["expected_receipt_sha256"],
            plan["expected_observer_identity"], values["shared_root"])
        journal.record("process_inventory", inventory.get("status", "HELD") if isinstance(inventory, dict) else "HELD",
                       inventory if isinstance(inventory, dict) else {"malformed":True})
        if not isinstance(inventory, dict) or inventory.get("status") != "OBSERVED_CLEAR" or not isinstance(inventory.get("observer_plan"), dict):
            raise TransitionHeld("fresh receipt-bound process inventory is not clear")
        fresh_observer_plan = inventory["observer_plan"]

        journal.record("observer_stop_intent", "STOPPING", {"pid":fresh_observer_plan.get("pid"),
                      "created":fresh_observer_plan.get("created"), "source_sha256":fresh_observer_plan.get("source_sha256")})
        stopped = workflow_cutover_process.stop_observer(fresh_observer_plan, barrier=barrier)
        journal.record("observer_stop_result", stopped.get("status", "HELD") if isinstance(stopped, dict) else "HELD",
                       stopped if isinstance(stopped, dict) else {"malformed":True})
        if not isinstance(stopped, dict) or stopped.get("status") != "STOPPED":
            raise TransitionHeld("freshly inventoried observer was not proven stopped")

        journal.record("history_capture_intent", "CAPTURING", {"operation_id":plan["operation_id"]})
        history = workflow_cutover_history.capture(
            values["exchange_root"], values["history_output_root"], values["previous_receipt"],
            plan["expected_receipt_sha256"], plan["operation_id"])
        if (not isinstance(history, dict) or not isinstance(history.get("manifest"), str) or
                not isinstance(history.get("manifest_sha256"), str) or len(history["manifest_sha256"]) != 64):
            raise TransitionHeld("legacy-board capture did not return its verified index receipt")
        journal.record("history_capture", "CAPTURED", history)

        for legacy_row in plan["legacy_mcp"]:
            _legacy_absence_proof(plan, legacy_row, journal, barrier, "publication")
        journal.record("publication_intent", "PUBLISHING", {"stage":str(plan["stage"]),
                      "receipt_output":str(values["publication_receipt"]), "pointer_sha256":plan["expected_pointer_sha256"]})
        if (_sha_bytes(values["stage_manifest_path"].read_bytes()) != values["stage_manifest_sha256"] or
                _sha_bytes(values["coordinator_stage_path"].read_bytes()) != values["coordinator_sha256"]):
            journal.record("staged_coordinator_integrity", "HELD", {"reason":"package manifest or staged coordinator changed before publication"})
            raise TransitionHeld("staged coordinator package changed after preflight")
        for legacy_row in plan["legacy_mcp"]:
            _legacy_absence_proof(plan, legacy_row, journal, barrier, "publication_effect")
        try:
            published = workflow_coordinator_package.publish(
                values["stage"], values["previous_receipt"], values["publication_receipt"],
                pointer_path=values["pointer_path"], expected_pointer_sha256=plan["expected_pointer_sha256"])
        except Exception as exc:
            receipt = None
            try: receipt = json.loads(str(exc))
            except (TypeError, json.JSONDecodeError): pass
            journal.record("publication_partial", "HELD", receipt if isinstance(receipt, dict) else
                           {"error":type(exc).__name__,"detail":str(exc)[:200]})
            raise TransitionHeld("publication failed or became partial; reconcile from the journal") from exc
        if not isinstance(published, dict):
            journal.record("publication_result", "HELD", {"malformed":True})
            raise TransitionHeld("receipt-owned publication returned a malformed receipt")
        journal.record("publication_result", published.get("status", "HELD"), published)
        if not isinstance(published, dict) or published.get("status") != "applied":
            raise TransitionHeld("receipt-owned package publication did not return applied")

        _refresh_author_contexts(values["author_contexts"], values["publication_receipt"], published, journal)

        try:
            workflow_coordinator_package.assert_no_reparse(values["shared_root"], values["coordinator_path"])
            if (not values["coordinator_path"].is_file() or
                    _sha_bytes(values["coordinator_path"].read_bytes()) != values["coordinator_sha256"]):
                raise ValueError("published shared coordinator entry differs from its staged manifest hash")
        except Exception as exc:
            journal.record("published_coordinator_integrity", "HELD", {"path":str(values["coordinator_path"]),
                          "expected_sha256":values["coordinator_sha256"],
                          "error":type(exc).__name__,"detail":str(exc)[:200]})
            raise TransitionHeld("published coordinator entry failed post-publication integrity verification") from exc
        journal.record("published_coordinator_integrity", "VERIFIED", {"path":str(values["coordinator_path"]),
                      "source_sha256":values["coordinator_source_sha256"],
                      "staged_sha256":values["coordinator_sha256"],"published_sha256":_sha_bytes(values["coordinator_path"].read_bytes())})

        journal.record("supervisor_start_intent", "STARTING", {"config_sha256":plan["expected_config_sha256"],
                      "runtime_entry_sha256":plan["expected_runtime_entry_sha256"],
                      "coordinator_entry_path":str(values["coordinator_path"]),
                      "coordinator_entry_sha256":values["coordinator_sha256"],
                      "python_sha256":plan["expected_python_sha256"], "activation_nonce":plan["activation_nonce"]})

        def on_running(receipt):
            nonlocal callback_success
            if isinstance(receipt, dict): journal.record("supervisor_running_receipt", "RUNNING", receipt)
            if threading.get_ident() != barrier._owner_thread:
                raise TransitionHeld("supervisor activation callback left barrier owner thread")
            if (not isinstance(receipt, dict) or receipt.get("supervisor_identity") != admin_identity or
                    gate_execution.process_identity(admin_identity["pid"]) != admin_identity):
                raise TransitionHeld("supervisor running receipt is not bound to the journaled administrator process identity")
            proof = _health_proof(receipt, values)
            journal.record("activation_health", proof.get("status", "HELD") if isinstance(proof, dict) else "HELD",
                           proof if isinstance(proof, dict) else {"malformed":True})
            if not isinstance(proof, dict) or proof.get("status") != "VERIFIED":
                raise TransitionHeld("actual coordinator listener/health proof is not verified")
            final_barrier = barrier.recheck()
            if not isinstance(final_barrier, dict) or final_barrier.get("status") != "OBSERVED_ZERO":
                raise TransitionHeld("maintained barrier changed during coordinator activation")
            release_barrier()
            journal.record("activation_proof", "AWAITING_TOOL_PROOF", {"supervisor":receipt,
                          "listener_health":proof,"barrier_recheck":final_barrier,
                          "admin_identity":admin_identity})
            callback_success = True

        supervisor = workflow_supervisor.WorkflowSupervisor(
            values["database"], values["config_path"], values["health_file"], port=plan["port"],
            coordinator_script=values["coordinator_path"],
            activation_nonce=plan["activation_nonce"], on_running=on_running)
        supervisor.run()
        if not callback_success or journal.data["status"] != "AWAITING_TOOL_PROOF":
            raise TransitionHeld("supervisor returned without verified activation proof")
        return journal.data
    except BaseException as exc:
        try: journal.record("transition_error", "HELD", {"error":type(exc).__name__, "detail":str(exc)[:240]})
        except BaseException: pass
        raise
    finally:
        if barrier_entered and not barrier_released:
            try:
                release_barrier()
                journal.record("barrier_cleanup", "RELEASED", {"retry_after_failure":True})
            except BaseException as cleanup:
                try: journal.record("barrier_cleanup", "HELD", {"error":type(cleanup).__name__,"detail":str(cleanup)[:200],
                                   "verified_release":False})
                except BaseException: pass
