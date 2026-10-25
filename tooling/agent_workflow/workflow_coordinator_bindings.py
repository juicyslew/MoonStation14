"""Receipt-bound planning for explicit, inactive coordinator MCP bindings."""
from __future__ import annotations

from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import shutil
import subprocess
import urllib.parse

ROOT = Path(__file__).resolve().parents[2]
AUTHOR_TOOLS = ("submit", "status", "acknowledge_result", "register_scope", "scope_status")
REVIEW_TOOLS = ("claim_task", "complete_review", "report_error", "status")
REVIEW_ROLES = ("implementation", "parity", "coverage", "fidelity")
REVIEWER_AGENT_PREFIX = "workflow-reviewer-"
REVIEWER_SOURCE_AGENTS = {"implementation": "review", "parity": "ss14-expert",
                          "coverage": "review", "fidelity": "fidelity-reviewer"}
REVIEWER_SOURCE_FILES = {
    "implementation": ("reviewer-provisioning.json", "e61206f1a83e4e39c1d9bd073f1d125283cd5c93039ca63570825a12b7f69589"),
    "parity": ("reviewer-provisioning.json", "e61206f1a83e4e39c1d9bd073f1d125283cd5c93039ca63570825a12b7f69589"),
    "coverage": ("reviewer-provisioning-continuation.json", "99fa8e6d292c03e824756eb3adca3d08cc0bba92b85f2a857c61004b8a0f5fc9"),
    "fidelity": ("reviewer-provisioning-continuation.json", "99fa8e6d292c03e824756eb3adca3d08cc0bba92b85f2a857c61004b8a0f5fc9"),
}
REVIEWER_ROLE_GUIDANCE = {
    "implementation": "Check correctness, edge cases, architecture, and reuse of existing owners.",
    "parity": "Check source-backed behavior, resolved prototype composition, and relevant client/server/network parity.",
    "coverage": "Check whether acceptance criteria and changed behavior have relevant, adequate test evidence.",
    "fidelity": "For applicable bound visual work, compare submitted visuals to the specified reference and evidence.",
}


def _index_digest(root: Path) -> str:
    """Hash the semantic stage-0 index entries without mutating the index."""
    raw = subprocess.run(["git", "-C", str(root), "ls-files", "--stage", "-z"],
                         check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    entries = []
    for entry in raw.split(b"\0"):
        if entry:
            metadata, path = entry.split(b"\t", 1)
            entries.append(metadata + b"\t" + path)
    return hashlib.sha256(b"\0".join(sorted(entries)) + (b"\0" if entries else b"")).hexdigest()


def validate_shared_config_plan_shape(plan):
    """Validate exact public plan contract, reusable by package/deploy validators."""
    required_fields = {"version", "mode", "template", "initialize", "untrack_generated", "targets"}
    if (not isinstance(plan, dict) or set(plan) - (required_fields | {"refresh_reviewer_instructions"}) or
            not required_fields.issubset(plan)):
        raise ValueError("shared config plan has unknown or missing fields")
    if type(plan["version"]) is not int or plan["version"] != 1 or plan["mode"] != "shared-opencode-config":
        raise ValueError("shared config plan mode/version is invalid")
    if type(plan["initialize"]) is not bool or type(plan["untrack_generated"]) is not bool:
        raise ValueError("shared config flags must be booleans")
    if "refresh_reviewer_instructions" in plan and type(plan["refresh_reviewer_instructions"]) is not bool:
        raise ValueError("shared config reviewer-instruction flag must be boolean")
    template = plan["template"]
    if (not isinstance(template, dict) or set(template) != {"path", "sha256"} or
            not isinstance(template["path"], str) or not Path(template["path"]).is_absolute() or
            not isinstance(template["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", template["sha256"])):
        raise ValueError("shared config template must bind an absolute path and SHA-256")
    names = {"main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))}
    rows = plan["targets"]
    required = {"target", "expected_config_sha256", "expected_local_sha256", "expected_gitignore_sha256", "expected_index_digest"}
    if not isinstance(rows, list) or len(rows) != 5 or any(
            not isinstance(row, dict) or set(row) != required or row.get("target") not in names or
            any((row.get(k) is None if k != "expected_local_sha256" else False) or
                (row.get(k) is not None and (not isinstance(row[k], str) or not re.fullmatch(r"[0-9a-f]{64}", row[k])))
                for k in required - {"target"}) for row in rows) or {r["target"] for r in rows if isinstance(r, dict)} != names:
        raise ValueError("shared config plan must bind MAIN and agents 01..04 exactly once")
    return plan


def _refresh_reviewer_instructions(overlay: dict) -> dict:
    """Refresh existing managed reviewer instructions without provisioning agents."""
    result = deepcopy(overlay)
    config = result.get("config")
    if not isinstance(config, dict):
        return result
    if "agents" not in config:
        return result
    agents = config["agents"]
    roles = ("implementation", "parity", "coverage", "fidelity")
    if not isinstance(agents, dict):
        raise ValueError("reviewer instruction refresh requires agents to be an object")
    expected = {f"workflow-reviewer-{role}" for role in roles}
    present = expected.intersection(agents)
    if not present:
        return result
    if present != expected:
        raise ValueError("inconsistent partial managed reviewer profile; expected zero or all four managed reviewer agents")
    for role in roles:
        agent = agents[f"workflow-reviewer-{role}"]
        if not isinstance(agent, dict) or not isinstance(agent.get("system"), str):
            raise ValueError(f"managed reviewer system instruction is malformed: {role}")
        agent["system"] = _reviewer_system(role, "")
    return result


def stage_shared_config_plan(binding_plan: Path, stage: Path, receipt: dict, *, main_root: Path, shared_root: Path) -> dict:
    """Receipt-owned, read-only staging for the canonical shared OpenCode template."""
    raw = binding_plan.read_bytes()
    plan = validate_shared_config_plan_shape(json.loads(raw.decode("utf-8-sig")))
    main_root = Path(main_root)
    template_path = Path(plan["template"]["path"])
    expected_template = (main_root / "tooling/opencode/shared-config.jsonc").resolve()
    if (not main_root.is_absolute() or main_root.name != "MoonStation14" or not main_root.is_dir() or
            template_path != expected_template or template_path.is_symlink() or not template_path.is_file()):
        raise ValueError("template must be the canonical regular MAIN shared-config file")
    _assert_no_reparse(Path(main_root.anchor), template_path)
    template_bytes = template_path.read_bytes()
    if hashlib.sha256(template_bytes).hexdigest() != plan["template"]["sha256"]:
        raise ValueError("shared template hash changed")
    from workflow_opencode_config import (load_template, extract_local_overlay, render_config,
                                           preserve_existing_bytes, serialize_config, configs_equivalent)
    template = load_template(template_bytes)
    stage.mkdir(parents=True, exist_ok=True)
    variables = {"shared_root": shared_root.as_posix(), "workspace_parent": main_root.parent.as_posix(),
                 "ss14_root": (Path.home() / "Documents/SS14-dev/space-station-14").as_posix()}
    staged_files = {}
    for row in plan["targets"]:
        name = row["target"]
        root = main_root if name == "main" else main_root.parent / name
        if not root.is_dir(): raise ValueError(f"shared config target does not exist: {name}")
        _assert_no_reparse(Path(root.anchor), root)
        cfg = root / "opencode.jsonc"; overlay_path = root / ".opencode/workflow/opencode-local.json"; ignore_path = root / ".gitignore"
        for path in (cfg, overlay_path, ignore_path): _assert_no_reparse(root, path)
        cfg_bytes = cfg.read_bytes() if cfg.is_file() else b""; cfg_sha = hashlib.sha256(cfg_bytes).hexdigest()
        owners = [r for r in receipt.get("files", []) if r.get("target") == name and r.get("path") == "opencode.jsonc" and not r.get("deleted")]
        if (len(owners) != 1 or row["expected_config_sha256"] != (cfg_sha if cfg.is_file() else None) or
                (owners[0].get("new_sha256") != cfg_sha and
                 (plan["initialize"] or not owners[0].get("config_scope", {}).get("shared_template")))):
            raise ValueError(f"config is stale or not receipt-owned: {name}")
        local_exists = overlay_path.is_file()
        local_bytes = overlay_path.read_bytes() if local_exists else None
        overlay_output = local_bytes
        local_sha = hashlib.sha256(local_bytes).hexdigest() if local_exists else None
        if local_sha != row["expected_local_sha256"]: raise ValueError(f"local overlay hash mismatch: {name}")
        if plan["initialize"] and local_exists:
            raise ValueError(f"initialization requires all local overlays to be absent: {name}")
        if not plan["initialize"] and not local_exists:
            raise ValueError(f"non-initializing plan requires a current receipt-owned local overlay: {name}")
        if local_exists:
            local_owners = [r for r in receipt.get("files", []) if r.get("target") == name and r.get("path") == ".opencode/workflow/opencode-local.json" and not r.get("deleted")]
            if len(local_owners) != 1 or local_owners[0].get("new_sha256") != local_sha:
                raise ValueError(f"existing local overlay is unknown/unowned: {name}")
            import workflow_opencode_config as oc
            overlay = oc._overlay(local_bytes)
            if overlay["variables"] != variables: raise ValueError(f"local overlay variables differ: {name}")
            if plan.get("refresh_reviewer_instructions"):
                prior_overlay = overlay
                overlay = _refresh_reviewer_instructions(overlay)
                if overlay != prior_overlay:
                    overlay_output = serialize_config(overlay).encode()
        else:
            if row["expected_local_sha256"] is not None: raise ValueError("planned local overlay is absent")
            overlay = extract_local_overlay(template, cfg_bytes, variables)
        desired = render_config(template, overlay)
        try:
            equivalent = cfg.is_file() and configs_equivalent(__import__("workflow_opencode_config")._loads(cfg_bytes), desired)
        except (ValueError, UnicodeError):
            equivalent = False
        after = cfg_bytes if equivalent and cfg_sha == owners[0].get("new_sha256") else serialize_config(desired).encode()
        ignore_bytes = ignore_path.read_bytes() if ignore_path.exists() else b""
        ignore_sha = hashlib.sha256(ignore_bytes).hexdigest()
        if ignore_sha != row["expected_gitignore_sha256"]: raise ValueError(f"gitignore hash mismatch: {name}")
        ignore_after = ignore_bytes
        for pattern in ("/opencode.jsonc", "/.opencode/workflow/"):
            if pattern not in ignore_bytes.decode("utf-8-sig").splitlines():
                if ignore_after and not ignore_after.endswith((b"\n", b"\r")): ignore_after += b"\n"
                ignore_after += (pattern + "\n").encode()
        index = _index_digest(root)
        if index != row["expected_index_digest"]: raise ValueError(f"git index drift: {name}")
        effective_overlay_bytes = overlay_output if local_exists else serialize_config(overlay).encode()
        config_scope = {**owners[0].get("config_scope", {}), "shared_template": {"path": str(template_path), "sha256": plan["template"]["sha256"]}, "local_overlay": {"path": ".opencode/workflow/opencode-local.json", "sha256": hashlib.sha256(effective_overlay_bytes).hexdigest()}}
        for rel, before, output, receipt_sha, scope in (
            ("opencode.jsonc", cfg_bytes, after, owners[0]["new_sha256"], config_scope),
            (".opencode/workflow/opencode-local.json", local_bytes, effective_overlay_bytes, local_sha, None),
            (".gitignore", ignore_bytes, ignore_after, None, None)):
            if before == output and not (scope is not None and owners[0].get("config_scope") != scope): continue
            br = Path("bindings/backups") / name / rel; sr = Path("bindings") / name / rel
            for p in (stage / br, stage / sr): _assert_no_reparse(stage, p); p.parent.mkdir(parents=True, exist_ok=True)
            no_backup = rel == "opencode.jsonc" and not plan["initialize"]
            if before is not None and not no_backup: (stage / br).write_bytes(before)
            (stage / sr).write_bytes(output)
            new_hash=hashlib.sha256(output).hexdigest(); item={"target":name,"path":rel,"old_sha256":hashlib.sha256(before).hexdigest() if before is not None else None,"new_sha256":new_hash,"receipt_sha256":receipt_sha,"staged_path":sr.as_posix(),"staged_sha256":new_hash,"source":str(root/rel),"source_sha256":hashlib.sha256(before).hexdigest() if before is not None else None}
            if before is not None and not no_backup: item.update({"backup_path":br.as_posix(),"backup_sha256":item["old_sha256"]})
            if scope is not None: item["config_scope"]=scope
            staged_files[f"{name}/{rel}"]=item
    plan_rel="bindings/binding-plan.json"; (stage/plan_rel).parent.mkdir(parents=True,exist_ok=True); (stage/plan_rel).write_bytes(raw)
    result={"version":1,"mode":"shared-opencode-config","source_plan":str(binding_plan.resolve()),"source_plan_sha256":hashlib.sha256(raw).hexdigest(),"staged_plan":plan_rel,"staged_plan_sha256":hashlib.sha256(raw).hexdigest(),"receipt_path":receipt["_path"],"receipt_sha256":receipt["_sha256"],"targets":sorted(r["target"] for r in plan["targets"]),"files":staged_files,"metadata":{"index_transition":{"untrack_generated":plan["untrack_generated"],"expected_index_digests":{r["target"]:r["expected_index_digest"] for r in plan["targets"]}}}}
    m=stage/"bindings/binding-manifest.json";m.write_text(json.dumps(result,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    return result


def materialize_remote_authorization(entry: dict, *, endpoint: str, profile: str,
                                    token_file: str) -> dict:
    """Materialize only the publisher's canonical file-backed bearer reference in memory."""
    if profile not in ("author", "author-static", "reviewer"):
        raise ValueError("unknown canonical coordinator MCP profile")
    reference = "Bearer {file:" + token_file + "}"
    expected = {"type": "remote", "url": endpoint.rstrip("/") + "/mcp/" + profile,
                "oauth": False, "headers": {"Authorization": reference}, "disabled": False,
                "codemode": False, "protocol": "legacy",
                "timeout": {"startup": 15000, "catalog": 15000, "execution": 300000}}
    if entry != expected:
        raise ValueError("remote coordinator MCP entry is foreign or noncanonical")
    path = Path(token_file)
    if not path.is_absolute() or path.is_symlink() or not path.is_file():
        raise ValueError("canonical coordinator token file is missing or unsafe")
    _assert_no_reparse(Path(path.anchor), path)
    with path.open("rb") as stream:
        token = stream.read(16385)
    if len(token) > 16384:
        raise ValueError("coordinator token exceeds bounded size")
    try:
        value = token.decode("utf-8").strip()
    except UnicodeError as exc:
        raise ValueError("coordinator token is not UTF-8") from exc
    if not value or "\r" in value or "\n" in value:
        raise ValueError("coordinator token is empty or malformed")
    result = deepcopy(entry)
    result["headers"]["Authorization"] = "Bearer " + value
    return result


def _canonical_remote_entry_shape(entry: dict, profile: str) -> bool:
    """Validate receipt-owned canonical entry independent of its prior credential/endpoint."""
    if not isinstance(entry, dict) or set(entry) != {"type", "url", "oauth", "headers", "disabled", "codemode", "protocol", "timeout"}:
        return False
    match = re.fullmatch(r"(http://(?:127\.0\.0\.1|localhost):[1-9][0-9]*)/mcp/" + re.escape(profile), entry.get("url", ""))
    header = entry.get("headers")
    return bool(match and entry.get("type") == "remote" and entry.get("oauth") is False
        and isinstance(header, dict) and set(header) == {"Authorization"}
        and isinstance(header["Authorization"], str)
        and re.fullmatch(r"Bearer \{file:[^{}\r\n]+\}", header["Authorization"])
        and entry.get("disabled") is False and entry.get("codemode") is False
        and entry.get("protocol") == "legacy"
        and entry.get("timeout") == {"startup":15000,"catalog":15000,"execution":300000})
LEGACY_MANAGED_ACTIONS = {
    "ms14_workflow_*", "ms14_workflow_submit", "ms14_workflow_status",
    "ms14_workflow_validate", "ms14_workflow_review", "ms14_workflow_finish",
}
KNOWN_OLD_AGENTS = {"orchestrate-ms14", "implement", "investigate", "ss14-expert", "review",
                    "3d-modeler", "integration-lead", "fidelity-reviewer"}


def _expected_legacy_rules(agent: str, profile: str) -> set[tuple[str, str, str]]:
    if agent not in KNOWN_OLD_AGENTS or profile not in ("author", "lead"):
        return set()
    expected = {("ms14_workflow_*", "*", "deny")}
    if profile == "author" and agent == "orchestrate-ms14":
        expected.update({("ms14_workflow_submit", "*", "allow"), ("ms14_workflow_status", "*", "allow")})
    if profile == "lead" and agent == "integration-lead":
        expected.update((f"ms14_workflow_{tool}", "*", "allow") for tool in ("status", "validate", "review", "finish"))
    return expected


def _assert_no_reparse(root: Path, path: Path) -> None:
    if root.is_symlink() or (getattr(root.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
        raise ValueError(f"reparse-point root: {root}")
    for part in path.relative_to(root).parts:
        root = root / part
        if root.is_symlink() or (root.exists() and getattr(root.lstat(), "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)):
            raise ValueError(f"reparse-point target: {root}")


def _json_config(raw: bytes) -> dict:
    try:
        value = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("binding-plan target config must be strict JSON (JSONC/comments fail closed)") from exc
    if not isinstance(value, dict) or not isinstance(value.get("agents"), dict):
        raise ValueError("OpenCode config requires an agents object")
    return value


# Exact legacy shell-resource tuples emitted by the retired publisher.  These
# are deliberately resources (not substring tests against commands): OpenCode
# globs cannot tell a command inspection from command execution.
_LEGACY_SHELL_KEYWORDS = (
    "test", "build", "check", "runGameTestServer", "runServer", "runClient",
    "featureReadiness", "workflow.py", "workflow_entry.py", "workflow_runtime.py",
    "gate-validate", "workflow.py*gate-clear-blocker",
)


def _known_legacy_shell_denies() -> set[tuple[str, str, str]]:
    known = {("shell", resource, "deny") for resource in (
        "*gradlew*build*", "*gradlew*check*", "*gradlew*test*",
    )}
    for keyword in ("test", "build", "check"):
        for prefix in ("", ":", ":*:", "*:"):
            for suffix in ("", " *"):
                known.add(("shell", f"*gradle* {prefix}{keyword}{suffix}", "deny"))
    known.update(("shell", f"*{keyword}*", "deny") for keyword in _LEGACY_SHELL_KEYWORDS
                 if keyword not in ("test", "build", "check"))
    return known


KNOWN_LEGACY_SHELL_DENIES = frozenset(_known_legacy_shell_denies())


def retire_inspection_blocking_shell_rules(config: dict) -> dict:
    """Copy config and remove only exact legacy shell-deny tuples."""
    result = deepcopy(config)
    agents = result.get("agents")
    if not isinstance(agents, dict):
        return result
    for name, agent in agents.items():
        if name not in KNOWN_OLD_AGENTS:
            continue
        if not isinstance(agent, dict) or not isinstance(agent.get("permissions"), list):
            continue
        agent["permissions"] = [rule for rule in agent["permissions"]
            if not isinstance(rule, dict) or tuple(rule.get(k) for k in ("action", "resource", "effect"))
            not in KNOWN_LEGACY_SHELL_DENIES]
    return result


KNOWN_REVIEWER_SHELL_DENIES = frozenset({
    "*runGameTestServer*", "*runServer*", "*runClient*", "*featureReadiness*",
    "*gradlew*build*", "*gradlew*check*", "*gradlew*test*",
    "*workflow.py*gate-validate*", "*workflow.py*gate-clear-blocker*",
})


def retire_fidelity_shell_profile(text: str) -> tuple[str, list[str]]:
    """Remove only exact legacy shell-deny entries in bounded YAML bash frontmatter."""
    frontmatter=re.match(r"(?s)\A---\r?\n(.*?)\r?\n---(?:\r?\n|\Z)",text)
    if not frontmatter: raise ValueError("reviewer profile lacks bounded YAML frontmatter")
    body=frontmatter.group(1)
    permission=re.search(r"(?ms)^permission:\r?\n(.*?)(?=^[^ \t\r\n][^\r\n]*:|\Z)",body)
    if not permission: raise ValueError("reviewer profile lacks permission map")
    lines=permission.group(1).splitlines(keepends=True);in_bash=False;retained=[];removed=[]
    for line in lines:
        if re.fullmatch(r"  [^ \t\r\n][^\r\n]*:\r?\n?",line):
            in_bash=line.startswith("  bash:")
        item=re.fullmatch(r'    "([^"\r\n]+)": deny(\r?\n?)',line)
        if in_bash and item and item.group(1) in KNOWN_REVIEWER_SHELL_DENIES:
            removed.append(item.group(1));continue
        retained.append(line)
    if len(set(removed))!=len(removed): raise ValueError("duplicate known shell deny in reviewer profile")
    # Reuse the established bounded-frontmatter/unowned-byte projection so
    # only the explicit receipt-owned bash lines disappear.
    projected=_profile_unowned_view(text, [], receipt_owned_shell_denies=KNOWN_REVIEWER_SHELL_DENIES)
    return projected,removed


def _resolve(base: Path, value: str) -> Path:
    path = Path(value)
    return (base / path).resolve() if not path.is_absolute() else path.resolve()


def _normalise_roles(coordinator: dict, plan_roles: list, repo: Path, config_base: Path) -> list[dict]:
    if not isinstance(plan_roles, list) or not plan_roles:
        raise ValueError("target roles must be a nonempty explicit list")
    normalized = []
    seen = set()
    for item in plan_roles:
        if isinstance(item,dict) and item.get('role')=='author' and item.get('binding_mode')=='worktree':
            if set(item)!={'role','binding_mode','caller','token_file','context_file'} or not all(isinstance(item[k],str) and item[k].strip() for k in ('caller','token_file','context_file')):
                raise ValueError('worktree author target requires only role, binding_mode, caller, token_file, and context_file')
            candidates=[row for row in coordinator.get('author_worktrees',[]) if isinstance(row,dict) and row.get('caller')==item['caller']]
            if len(candidates)!=1: raise ValueError('worktree author target does not match exactly one trusted author slot')
            binding=candidates[0]
            if set(binding)!={'caller','token_file','context_file'} or _resolve(config_base,binding['token_file'])!=_resolve(config_base,item['token_file']) or _resolve(config_base,binding['context_file'])!=_resolve(config_base,item['context_file']):
                raise ValueError('worktree author target differs from trusted token/context slot')
            context_path=_resolve(config_base,binding['context_file'])
            if context_path.is_symlink() or not context_path.is_file(): raise ValueError('worktree author context is missing or linked')
            context=json.loads(context_path.read_text(encoding='utf-8'))
            if set(context)!={'repo','floors','publication_receipt_path','publication_receipt_sha256'} or _resolve(context_path.parent,context['repo'])!=repo.resolve():
                raise ValueError('worktree author context repository differs from target')
            try:
                import workflow_scope
            except ModuleNotFoundError:
                from tooling.agent_workflow import workflow_scope
            workflow_scope.validate_worktree_floors(context['floors'])
            receipt_path=_resolve(context_path.parent,context['publication_receipt_path'])
            if receipt_path.is_symlink() or not receipt_path.is_file() or not isinstance(context['publication_receipt_sha256'],str) or hashlib.sha256(receipt_path.read_bytes()).hexdigest()!=context['publication_receipt_sha256']:
                raise ValueError('worktree author publication receipt is missing or changed')
            normalized.append({'role':'author','binding_mode':'worktree','agent':'orchestrate-ms14','caller':item['caller'],
                'worktree':str(repo.resolve()),'token_file':str(_resolve(config_base,item['token_file'])),'context_file':str(context_path.resolve())})
            seen.add('author')
            continue
        if isinstance(item, dict) and item.get("role") in REVIEW_ROLES:
            required = {"role", "agent", "caller", "worktree", "token_file"}
            allowed_reviewer = required | {"profile_path"}
            if set(item) - allowed_reviewer or not required.issubset(item):
                raise ValueError("reviewer target requires role, dedicated agent, caller, worktree and token_file; static sessions are forbidden")
            role = item["role"]
            if not all(isinstance(item[k], str) and item[k].strip() for k in required - {"role"}):
                raise ValueError(f"empty target-role identity: {role}")
            if Path(item["worktree"]).resolve() != repo.resolve(): raise ValueError(f"target worktree mismatch: {role}")
            binding = coordinator.get("reviewers", {}).get(role)
            if (not isinstance(binding, dict) or set(binding) != {"worktree", "agent", "token_file", "caller"}
                    or binding.get("agent") != REVIEWER_AGENT_PREFIX + role
                    or any(binding.get(k) != item[k] for k in ("agent", "caller", "worktree"))
                    or _resolve(config_base, binding.get("token_file", "")) != _resolve(config_base, item["token_file"])):
                raise ValueError(f"reviewer target differs from trusted fresh-session configuration: {role}")
            normalized.append({**item, "worktree": str(repo.resolve()), "token_file": str(_resolve(config_base, item["token_file"]))})
            seen.add(role)
            continue
        allowed = {"role", "agent", "caller", "session_id", "worktree", "token_file", "profile_path"}
        if not isinstance(item, dict) or set(item) - allowed or not {"role", "agent", "caller", "session_id", "worktree", "token_file"}.issubset(item):
            raise ValueError("target role requires role, agent, caller, session_id, worktree and token_file")
        role = item["role"]
        if role not in ("author", *REVIEW_ROLES) or role in seen:
            raise ValueError(f"unknown or duplicate explicit role: {role}")
        seen.add(role)
        if not all(isinstance(item[k], str) and item[k].strip() for k in ("agent", "caller", "session_id", "worktree", "token_file")):
            raise ValueError(f"empty target-role identity: {role}")
        worktree = Path(item["worktree"]).resolve()
        if worktree != repo.resolve():
            raise ValueError(f"target worktree mismatch: {role}")
        if role == "author":
            candidates = [row for row in coordinator.get("bindings", []) if isinstance(row, dict) and row.get("caller") == item["caller"]]
            if len(candidates) != 1:
                raise ValueError("author target does not match exactly one trusted coordinator binding")
            binding = candidates[0]
            if set(binding) != {"caller", "token_file", "context_file"} or _resolve(config_base, binding["token_file"]) != _resolve(config_base, item["token_file"]):
                raise ValueError("author target token is not the configured binding token")
            context_path = _resolve(config_base, binding["context_file"])
            if context_path.is_symlink() or not context_path.is_file():
                raise ValueError("author trusted context is missing or linked")
            context = json.loads(context_path.read_text(encoding="utf-8"))
            author = context.get("authenticated_author", {})
            context_repo = _resolve(context_path.parent, context.get("repo", ""))
            if context_repo != repo.resolve() or author.get("session_id") != item["session_id"] or Path(author.get("worktree", "")).resolve() != repo.resolve() or author.get("role") != "author":
                raise ValueError("author session/worktree/role differs from the authenticated context")
        else:
            binding = coordinator.get("reviewers", {}).get(role)
            if not isinstance(binding, dict):
                raise ValueError(f"reviewer role is not configured: {role}")
            if any(binding.get(k) != item[k] for k in ("agent", "caller", "session_id", "worktree")) or _resolve(config_base, binding.get("token_file", "")) != _resolve(config_base, item["token_file"]):
                raise ValueError(f"reviewer target differs from trusted configuration: {role}")
        normalized.append({**item, "worktree": str(repo.resolve()), "token_file": str(_resolve(config_base, item["token_file"]))})
    return normalized


def _reviewer_output_root(value: str, worktrees: set[Path]) -> Path:
    if not isinstance(value, str) or not value.strip():
        raise ValueError("reviewer output_root must be an absolute private directory")
    raw = Path(value)
    if not raw.is_absolute() or ".." in raw.parts:
        raise ValueError("reviewer output_root must be an absolute normalized private directory")
    output = Path(os.path.abspath(raw))
    approved = (Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode").resolve()
    if output == approved or not output.is_relative_to(approved):
        raise ValueError("reviewer output_root must be a private OpenCode temp child")
    if any(output == tree or output.is_relative_to(tree) or tree.is_relative_to(output) for tree in worktrees):
        raise ValueError("reviewer output_root must be outside every worktree")
    if output.exists() and not output.is_dir():
        raise ValueError("reviewer output_root exists but is not a directory")
    _assert_no_reparse(Path(output.anchor), output)
    return output


def _validate_reviewer_declarations(declarations, target_rows, main_root: Path) -> dict:
    if not isinstance(declarations, list) or not declarations:
        raise ValueError("reviewer_agents must be a nonempty explicit list")
    worktrees = {main_root.resolve()}
    for row in target_rows:
        name = row.get("target")
        if name == "main": root = main_root
        elif isinstance(name, str) and re.fullmatch(r"MoonStation14-agent-0[1-5]", name): root = main_root.parent / name
        else: raise ValueError("unknown reviewer target worktree")
        worktrees.add(root.resolve())
    prepared = {}
    for item in declarations:
        expected_fields = {"role", "source_agent", "output_root", "model", "request", "source_receipt"}
        if not isinstance(item, dict) or set(item) != expected_fields:
            raise ValueError("reviewer declaration requires only role, source_agent, output_root, model, request and source_receipt")
        role = item["role"]
        if role not in REVIEW_ROLES or role in prepared:
            raise ValueError(f"unknown or duplicate dedicated reviewer role: {role}")
        source_agent = REVIEWER_SOURCE_AGENTS[role]
        if item["source_agent"] != source_agent:
            raise ValueError(f"reviewer model provenance source differs for {role}")
        expected_file, expected_sha = REVIEWER_SOURCE_FILES[role]
        expected_path = (ROOT / "build/agent-workflow/workflow-redesign-plan/wr12" / expected_file).resolve()
        source = item["source_receipt"]
        if not isinstance(source, dict) or set(source) != {"path", "sha256"} or Path(source["path"]).resolve() != expected_path or source["sha256"] != expected_sha:
            raise ValueError(f"reviewer model source must cite the pinned provisioning receipt for {role}")
        if hashlib.sha256(expected_path.read_bytes()).hexdigest() != expected_sha:
            raise ValueError(f"pinned reviewer model receipt changed for {role}")
        evidence = json.loads(expected_path.read_text(encoding="utf-8"))
        if role in ("implementation", "parity"):
            definition = evidence.get("verified_agent_gets", {}).get(source_agent, {})
            model_ok = definition.get("model") == "openai/gpt-6-luna" and definition.get("variant") == "high"
        else:
            definition = next((row for row in evidence.get("creates", []) if row.get("agent") == source_agent), {})
            model_ok = definition.get("agent_configured_model") == "openai/gpt-6-luna" and definition.get("agent_reasoning") == "high"
        if not model_ok or item["model"] != "openai/gpt-6-luna" or item["request"] != {"body": {"reasoningEffort": "high"}}:
            raise ValueError(f"reviewer model/request snapshot differs from verified source for {role}")
        output_root = _reviewer_output_root(item["output_root"], worktrees)
        prepared[role] = {"role": role, "source_agent": source_agent,
                          "agent": REVIEWER_AGENT_PREFIX + role,
                          "output_root": str(output_root), "model": item["model"],
                           "request": item["request"], "source_receipt": {"path": str(expected_path), "sha256": expected_sha}}
    return prepared


def _reviewer_system(role: str, output_dir: str) -> str:
    shared = ("Review only the assigned frozen task and supplied source/evidence. OWNER_READY means technical-ready for owner testing, not owner acceptance; owner acceptance is a separate later decision. Classify each blocking finding as TECHNICAL. Outstanding subjective/physical owner observations belong in owner_followups, never blocking findings. Concrete UI/render/input/audio defects and required machine-check coverage remain technical blockers even in those domains. For COVERAGE findings, include coverage_basis: MISSING_EXECUTION_EVIDENCE or INSUFFICIENT_CHECK_COVERAGE; the former also requires nonempty check_kind (for example gametest, junit, readiness, build). Treat trusted same-run, generation, and input-bound check receipts as current execution facts: never claim missing execution evidence when a current bound PASS receipt exists. Author objective/pre-submit notes may truthfully say checks are pending before the gate. A passing check can still have insufficient coverage only with a concrete untested branch or scope gap; PASS does not equal owner acceptance. Claim it, inspect your scope, submit "
              "evidence-backed PASS/FAIL through its role tool; report missing/bad inputs as ERROR. Source is read-only. "
              "Do not assume filesystem write tools, shell, or any generic read/edit tool exists. Do not edit, rerun "
              "checks, orchestrate, or spawn agents.")
    protocol = (f"{REVIEWER_ROLE_GUIDANCE[role]} Every tool call must include your assigned session_id. Claim with claim_task(task_id=<assigned task>, session_id=<assigned session>); task_id is required. "
                "Use status(task_id, input_ref='review_guide', input_offset=0) to read the complete canonical review guide; "
                "continue with each returned next_offset until eof. Use status(task_id, input_ref=<assigned artifact ID>, "
                "input_offset=0) to page each assigned artifact by its distinct ID, likewise continuing until eof. "
                "Use only artifact IDs and source references supplied by the assignment/status response; never invent paths or IDs. "
                 "Follow the guide's semantic result/error schemas and include task_id, decision/evidence or error fields. Omit contract_version, run_id, evaluation_generation, assignment_generation, and input_digest; the coordinator binds them after authorization. Submit the complete result inline with "
                 "complete_review(result=<semantic review result>, session_id=<assigned session>) or the complete typed "
                 "error inline with report_error(error=<semantic task error>, session_id=<assigned session>). "
                 "Every finding requires validation_stage=TECHNICAL. Put pending owner observations in owner_followups entries {criterion_id,message,manual_check,evidence_refs?}; these are retained for the owner log and do not block PASS. Never classify OWNER_TESTING as a blocking finding: the API rejects it and instructs correction. PASS requires no technical findings; FAIL requires actionable technical findings. Do not use result_path/error_path or rely on writing a file: inline submission is the required mode. "
                "If a legitimate assigned input is unsupported, report its actual typed read error only after attempting "
                "the SDK read; do not treat an invalid selector as infrastructure failure. "
                f"{output_dir} is an optional private output location only when an external client can write there; "
                "it is not required for review submission. Keep the assigned session_id on every SDK call.")
    return shared + " " + protocol


def _reviewer_permissions(repo: Path, role: str, output_dir: str, read_roots=()) -> list[dict]:
    rules = [{"action": "*", "resource": "*", "effect": "deny"},
             {"action": "shell", "resource": "*", "effect": "deny"},
             {"action": "edit", "resource": "*", "effect": "deny"}]
    roots = {repo.as_posix() + "/**"}
    ss14 = ROOT.parents[1] / "SS14-dev/space-station-14"
    if role == "parity" and not ss14.is_dir():
        raise ValueError("pinned SS14 reference root is unavailable for the parity reviewer")
    if role == "parity": roots.add(ss14.as_posix() + "/**")
    roots.update(Path(value).as_posix().rstrip("/") + "/**" for value in read_roots)
    roots = sorted(roots)
    # OpenCode matches glob/grep resource patterns against the query PATTERN, not its path.
    # Keep path scoping on file access actions; external_directory still guards roots.
    for action in ("read", "list"):
        rules.extend({"action": action, "resource": root, "effect": "allow"} for root in roots)
    for action in ("glob", "grep"):
        rules.append({"action": action, "resource": "*", "effect": "allow"})
    rules.append({"action": "edit", "resource": output_dir.replace("\\", "/").rstrip("/") + "/**", "effect": "allow"})
    external_roots = {Path(value).as_posix().rstrip("/") + "/**" for value in read_roots}
    if role == "parity": external_roots.add(ss14.as_posix() + "/**")
    for root in sorted(external_roots):
        rules.append({"action": "external_directory", "resource": root, "effect": "allow"})
    rules.append({"action": "external_directory", "resource": output_dir.replace("\\", "/").rstrip("/") + "/**", "effect": "allow"})
    return rules


def _reviewer_agent_definition(declaration: dict, repo: Path, read_roots=()) -> dict:
    output_dir = (Path(declaration["output_root"]) / declaration["role"]).as_posix()
    return {"description": f"Bounded {declaration['role']} reviewer; source read-only, result files only in assigned private output.",
            "model": declaration["model"], "request": deepcopy(declaration["request"]),
            "system": _reviewer_system(declaration["role"], output_dir),
            "permissions": _reviewer_permissions(repo, declaration["role"], output_dir, read_roots)}


def _trusted_reviewer_read_roots(target_rows, main_root: Path) -> list[str]:
    """Derive reviewer-readable worktrees and artifact dirs from explicit trusted targets."""
    targets = []
    for row in target_rows:
        name = row.get("target") if isinstance(row, dict) else None
        if name == "main": root = main_root
        elif isinstance(name, str) and re.fullmatch(r"MoonStation14-agent-0[1-5]", name): root = main_root.parent / name
        else: raise ValueError("unknown reviewer target worktree")
        root = Path(os.path.abspath(root))
        _assert_no_reparse(Path(root.anchor), root)
        targets.append((row, root))
    roots = set()
    artifacts = set()
    for row, root in targets:
        config_path = Path(row.get("coordinator_config", ""))
        if not config_path.is_absolute() or config_path.is_symlink() or not config_path.is_file():
            raise ValueError("coordinator_config must be an existing absolute regular file")
        _assert_no_reparse(Path(config_path.anchor), config_path)
        coordinator = json.loads(config_path.read_text(encoding="utf-8"))
        _normalise_roles(coordinator, row.get("roles"), root, config_path.parent)
        bindings = coordinator.get("bindings")
        if not isinstance(bindings, list): raise ValueError("trusted coordinator author bindings are malformed")
        for owner in bindings:
            if not isinstance(owner, dict) or set(owner) != {"caller", "token_file", "context_file"}:
                raise ValueError("trusted coordinator author binding is malformed")
            context_path = _resolve(config_path.parent, owner["context_file"])
            if context_path.is_symlink() or not context_path.is_file():
                raise ValueError("author trusted context is missing or linked")
            context = json.loads(context_path.read_text(encoding="utf-8"))
            author = context.get("authenticated_author", {})
            context_repo = _resolve(context_path.parent, context.get("repo", ""))
            if context_repo != root.resolve():
                continue
            synthetic = {"role":"author", "agent":"orchestrate-ms14", "caller":owner["caller"],
                         "session_id":author.get("session_id"), "worktree":author.get("worktree"),
                         "token_file":owner["token_file"]}
            validated = _normalise_roles(coordinator, [synthetic], root, config_path.parent)[0]
            author_root = Path(os.path.abspath(validated["worktree"]))
            _assert_no_reparse(Path(author_root.anchor), author_root)
            roots.add(str(author_root))
        dynamic=coordinator.get('author_worktrees',[])
        if not isinstance(dynamic,list): raise ValueError('trusted coordinator author_worktrees are malformed')
        for slot in dynamic:
            if not isinstance(slot,dict) or set(slot)!={'caller','token_file','context_file'}:
                raise ValueError('trusted coordinator worktree author slot is malformed')
            context_path=_resolve(config_path.parent,slot['context_file'])
            if context_path.is_symlink() or not context_path.is_file(): raise ValueError('worktree author context is missing or linked')
            context=json.loads(context_path.read_text(encoding='utf-8'))
            if set(context)!={'repo','floors','publication_receipt_path','publication_receipt_sha256'}: raise ValueError('worktree author context is malformed')
            try:
                import workflow_scope
            except ModuleNotFoundError:
                from tooling.agent_workflow import workflow_scope
            workflow_scope.validate_worktree_floors(context['floors'])
            receipt_path=_resolve(context_path.parent,context['publication_receipt_path'])
            if receipt_path.is_symlink() or not receipt_path.is_file() or hashlib.sha256(receipt_path.read_bytes()).hexdigest()!=context['publication_receipt_sha256']:
                raise ValueError('worktree author publication receipt is missing or changed')
            worktree=_resolve(context_path.parent,context['repo'])
            if worktree!=root.resolve(): continue
            _assert_no_reparse(Path(worktree.anchor),worktree)
            roots.add(str(worktree))
        configured_artifacts = coordinator.get("artifact_dir")
        if not isinstance(configured_artifacts, str) or not configured_artifacts.strip():
            raise ValueError("trusted coordinator artifact_dir is missing")
        artifact_root = Path(configured_artifacts)
        if not artifact_root.is_absolute(): artifact_root = config_path.parent / artifact_root
        artifact_root = Path(os.path.abspath(artifact_root))
        _assert_no_reparse(Path(artifact_root.anchor), artifact_root)
        if not artifact_root.is_dir():
            raise ValueError("trusted coordinator artifact_dir is not an existing directory")
        artifacts.add(str(artifact_root))
    all_worktrees = {root for _, root in targets}
    for artifact_value in artifacts:
        artifact_root = Path(artifact_value)
        if any(artifact_root == worktree or artifact_root.is_relative_to(worktree) or worktree.is_relative_to(artifact_root)
               for worktree in all_worktrees):
            raise ValueError("trusted coordinator artifact_dir overlaps an explicit worktree")
    roots.update(artifacts)
    normalized = {os.path.normcase(os.path.normpath(value)): os.path.normpath(value) for value in roots}
    return [normalized[key] for key in sorted(normalized)]


def _reviewer_workflow_rules(role: str) -> set[tuple[str, str, str]]:
    prefix=f"ms14_workflow_review_{role}"
    return {(f"{prefix}_{tool}", "*", "allow") for tool in REVIEW_TOOLS} | {("ms14_workflow_*", "*", "deny")}


def merge_bindings(config: dict, roles: list[dict], *, repo: str, endpoint: str,
                   python: str, adapter: str, receipt_owned_servers=None,
                   receipt_owned_rules=(), transport: str = "local") -> dict:
    """Merge only a target's explicitly assigned identities and exact tool grants."""
    result = deepcopy(config)
    if not isinstance(result.get("mcp", {}), dict) or not isinstance(result.get("agents"), dict):
        raise ValueError("malformed OpenCode project configuration")
    mcp = result.setdefault("mcp", {})
    servers = mcp.setdefault("servers", {})
    if not isinstance(servers, dict):
        raise ValueError("malformed mcp.servers")
    if not roles:
        raise ValueError("explicit target role set is empty")
    owned_servers = receipt_owned_servers or {}
    owned_rules = set(tuple(x) for x in receipt_owned_rules)
    tools_for = {}
    allowed_rules = set()
    for row in roles:
        prefix = "ms14_workflow" if row["role"] == "author" else f"ms14_workflow_review_{row['role']}"
        names = AUTHOR_TOOLS if row["role"] == "author" else REVIEW_TOOLS
        allowed_rules.update((f"{prefix}_{tool}", "*", "allow") for tool in names)
        allowed_rules.add(("ms14_workflow_*", "*", "deny"))
    for row in roles:
        role = row["role"]
        key = "ms14_workflow" if role == "author" else f"ms14_workflow_review_{role}"
        tools = AUTHOR_TOOLS if role == "author" else REVIEW_TOOLS
        if key in tools_for:
            raise ValueError(f"duplicate MCP server key: {key}")
        agent_name = row["agent"]
        if agent_name not in result["agents"]:
            raise ValueError(f"configured agent does not exist: {agent_name}")
        if transport not in ("local", "http"):
            raise ValueError("binding transport must be local or http")
        if transport == "http":
            profile = "author" if role == "author" and row.get("binding_mode") == "worktree" else (
                "author-static" if role == "author" else "reviewer")
            entry = {"type": "remote", "url": endpoint.rstrip("/") + "/mcp/" + profile,
                     "oauth": False, "headers": {"Authorization": "Bearer {file:" + row["token_file"] + "}"},
                     "disabled": False, "codemode": False, "protocol": "legacy",
                     "timeout": {"startup": 15000, "catalog": 15000, "execution": 300000}}
        else:
            command = [str(python), str(adapter), "--repo", str(Path(repo).resolve()), "--endpoint", endpoint,
                   "--token-file", row["token_file"], "--profile", "author" if role == "author" else "reviewer"]
            if role=='author' and row.get('binding_mode')=='worktree': command.extend(['--author-mode','worktree'])
            entry = {"type": "local", "command": command, "cwd": str(Path(repo).resolve()), "environment": {},
                     "disabled": False, "codemode": False, "protocol": "legacy",
                     "timeout": {"startup": 15000, "catalog": 15000, "execution": 300000}}
        old = servers.get(key)
        if old is not None and old != entry and owned_servers.get(key) != old:
            raise ValueError(f"conflicting/unowned MCP server entry: {key}")
        servers[key] = entry
        permissions = result["agents"][agent_name].setdefault("permissions", [])
        if not isinstance(permissions, list):
            raise ValueError(f"malformed agent permissions: {agent_name}")
        for permission in permissions:
            action = permission.get("action", "")
            if isinstance(action, str) and action.startswith("ms14_workflow"):
                identity = tuple(permission.get(k) for k in ("action", "resource", "effect"))
                if identity not in allowed_rules and identity not in owned_rules:
                    raise ValueError(f"unowned workflow permission conflict on {agent_name}: {action}")
                if identity in owned_rules and identity not in (
                    _expected_legacy_rules(agent_name, "author") | _expected_legacy_rules(agent_name, "lead")
                ) and identity not in allowed_rules and not any(identity in _reviewer_workflow_rules(assigned["role"])
                              for assigned in roles if assigned["agent"] == agent_name and assigned["role"] in REVIEW_ROLES):
                    raise ValueError(f"receipt-owned workflow permission is not a known publisher tuple on {agent_name}: {action}")
        # Drop only receipt-owned legacy tuples on selected agents, then establish the
        # existing publisher's deny-by-default prefix and exact per-tool allowances.
        result["agents"][agent_name]["permissions"] = [
            permission for permission in permissions
            if tuple(permission.get(k) for k in ("action", "resource", "effect")) not in owned_rules
        ]
        permissions = result["agents"][agent_name]["permissions"]
        deny = {"action": "ms14_workflow_*", "resource": "*", "effect": "deny"}
        if deny not in permissions:
            permissions.append(deny)
        for tool in tools:
            rule = {"action": f"{key}_{tool}", "resource": "*", "effect": "allow"}
            if rule not in permissions:
                permissions.append(rule)
        tools_for[key] = agent_name
    selected_agents = set(tools_for.values())
    return result


def merge_profile_tool_permissions(text: str, profile_roles: list[str] | str, *, receipt_owned_rules=()) -> str:
    if isinstance(profile_roles, str):
        profile_roles = [profile_roles]
    if not profile_roles or len(set(profile_roles)) != len(profile_roles) or set(profile_roles) - (set(REVIEW_ROLES) | {"author"}):
        raise ValueError("unknown/duplicate explicit profile role")
    allowed = [tool for role in profile_roles for tool in
               ([f"ms14_workflow_{t}" for t in AUTHOR_TOOLS] if role == "author" else [f"ms14_workflow_review_{role}_{t}" for t in REVIEW_TOOLS])]
    frontmatter = re.match(r"(?s)\A---\r?\n(.*?)\r?\n---(?:\r?\n|\Z)", text)
    if not frontmatter:
        raise ValueError("explicit workflow profile lacks bounded YAML frontmatter")
    body = frontmatter.group(1)
    permission = re.search(r"(?ms)^permission:\r?\n(.*?)(?=^[^ \t\r\n][^\r\n]*:|\Z)", body)
    if not permission:
        raise ValueError("explicit workflow profile lacks permission map")
    owned = set(receipt_owned_rules)
    kept = []
    for line in permission.group(1).splitlines(keepends=True):
        item = re.fullmatch(r"  (ms14_workflow[^:]*): (allow|deny)\r?\n?", line)
        if item:
            pair = (item.group(1), item.group(2))
            if pair not in owned and (item.group(1) not in allowed or item.group(2) != "allow"):
                raise ValueError(f"unowned workflow profile permission conflicts: {item.group(1)}")
            if pair in owned or pair[0] in allowed:
                continue
        kept.append(line)
    nl = "\r\n" if "\r\n" in text else "\n"
    insertion = "".join(f"  {tool}: allow{nl}" for tool in allowed)
    changed_body = body[:permission.start(1)] + insertion + "".join(kept) + body[permission.end(1):]
    changed = text[:frontmatter.start(1)] + changed_body + text[frontmatter.end(1):]
    if changed.endswith("\n") != text.endswith("\n"):
        changed = changed[:-1] if not text.endswith("\n") else changed + "\n"
    return changed


def _profile_unowned_view(text: str, profile_roles: list[str], receipt_owned_rules=(), *, receipt_owned_shell_denies=()) -> str:
    """Remove only exact owned legacy and selected new workflow lines for byte comparison."""
    owned = set(tuple(item) for item in receipt_owned_rules)
    selected = set()
    for role in profile_roles:
        selected.update(
            (name, "allow") for name in
            ([f"ms14_workflow_{tool}" for tool in AUTHOR_TOOLS] if role == "author" else
             [f"ms14_workflow_review_{role}_{tool}" for tool in REVIEW_TOOLS])
        )
    frontmatter = re.match(r"(?s)\A---\r?\n(.*?)\r?\n---(?:\r?\n|\Z)", text)
    if not frontmatter:
        raise ValueError("explicit workflow profile lacks bounded YAML frontmatter")
    permission = re.search(r"(?ms)^permission:\r?\n(.*?)(?=^[^ \t\r\n][^\r\n]*:|\Z)", frontmatter.group(1))
    if not permission:
        raise ValueError("explicit workflow profile lacks permission map")
    lines = permission.group(1).splitlines(keepends=True)
    retained = []
    in_bash=False
    shell_owned=set(receipt_owned_shell_denies)
    for line in lines:
        if re.fullmatch(r"  [^ \t\r\n][^\r\n]*:\r?\n?",line):
            in_bash=line.startswith("  bash:")
        item = re.fullmatch(r"  (ms14_workflow[^:]*): (allow|deny)\r?\n?", line)
        if item and ((item.group(1), item.group(2)) in owned or (item.group(1), item.group(2)) in selected):
            continue
        shell_item=re.fullmatch(r'    "([^"\r\n]+)": deny\r?\n?',line)
        if in_bash and shell_item and shell_item.group(1) in shell_owned:
            continue
        retained.append(line)
    changed_body = (frontmatter.group(1)[:permission.start(1)] + "".join(retained) +
                    frontmatter.group(1)[permission.end(1):])
    return text[:frontmatter.start(1)] + changed_body + text[frontmatter.end(1):]


def assert_profile_unowned_preserved(old: str, new: str, roles: list[str], receipt_owned_rules=()) -> None:
    if _profile_unowned_view(old, roles, receipt_owned_rules) != _profile_unowned_view(new, roles, receipt_owned_rules):
        raise ValueError("coordinator profile plan changed unowned profile bytes")


def plan_explicit_target(config: dict, coordinator: dict, target_spec: dict, profile_texts: dict, *,
                         config_base: str, profile_receipt_hashes: dict, repo: str, endpoint: str,
                         python: str, adapter: str, receipt_owned_servers=None, receipt_owned_rules=(),
                          profile_owned_rules=None, transport: str = "local"):
    root = Path(repo).resolve()
    if Path(target_spec["worktree"]).resolve() != root:
        raise ValueError("target worktree mismatch")
    roles = _normalise_roles(coordinator, target_spec["roles"], root, Path(config_base).resolve())
    merged_config = merge_bindings(config, roles, repo=str(root), endpoint=endpoint, python=python,
                                    adapter=adapter, receipt_owned_servers=receipt_owned_servers,
                                    receipt_owned_rules=receipt_owned_rules, transport=transport)
    updates: dict[str, list[str]] = {}
    for row in roles:
        profile_path = row.get("profile_path")
        if not profile_path:
            continue
        path = Path(profile_path)
        if path.is_absolute() or ".." in path.parts or path.as_posix() not in profile_texts:
            raise ValueError("profile path must be an explicit relative target")
        current_hash = hashlib.sha256(profile_texts[path.as_posix()]).hexdigest()
        if profile_receipt_hashes.get(path.as_posix()) != current_hash:
            raise ValueError(f"profile is not verified by publication receipt: {path.as_posix()}")
        updates.setdefault(path.as_posix(), []).append(row["role"])
    profiles = {}
    for path, scoped_roles in updates.items():
        old = profile_texts[path]
        bom = old.startswith(b"\xef\xbb\xbf")
        text = old.decode("utf-8-sig" if bom else "utf-8")
        new = merge_profile_tool_permissions(text, scoped_roles,
                                              receipt_owned_rules=(profile_owned_rules or {}).get(path, ())).encode("utf-8")
        new_text = new.decode("utf-8")
        assert_profile_unowned_preserved(text, new_text, scoped_roles,
                                         (profile_owned_rules or {}).get(path, ()))
        if bom:
            new = b"\xef\xbb\xbf" + new
        profiles[path] = {"old_sha256": hashlib.sha256(old).hexdigest(), "new_sha256": hashlib.sha256(new).hexdigest(),
                          "action": "up-to-date" if old == new else "replace", "old_bytes": old, "new_bytes": new}
    return merged_config, profiles


def _unowned_view(config: dict, roles: list[dict], receipt_owned_rules=(), managed_reviewer_agents=(), extra_owned_rules=()) -> dict:
    result = deepcopy(config)
    keys = {"ms14_workflow" if r["role"] == "author" else f"ms14_workflow_review_{r['role']}" for r in roles}
    for key in keys:
        result.get("mcp", {}).get("servers", {}).pop(key, None)
    managed = set(tuple(rule) for rule in receipt_owned_rules)
    managed.update(tuple(rule) for rule in extra_owned_rules)
    for role in roles:
        key = "ms14_workflow" if role["role"] == "author" else f"ms14_workflow_review_{role['role']}"
        for tool in (AUTHOR_TOOLS if role["role"] == "author" else REVIEW_TOOLS):
            managed.add((f"{key}_{tool}", "*", "allow"))
    managed.add(("ms14_workflow_*", "*", "deny"))
    agents = {r["agent"] for r in roles}
    if extra_owned_rules and "implement" in result.get("agents", {}):
        agents.add("implement")
    # These exact reserved names are removed only after the planner has either
    # proven them absent in the preimage or matched the receipt-owned definition.
    for name in managed_reviewer_agents:
        result.get("agents", {}).pop(name, None)
    for name in agents:
        if name in result.get("agents", {}):
            result["agents"][name]["permissions"] = [
                p for p in result["agents"][name].get("permissions", [])
                if tuple(p.get(k) for k in ("action", "resource", "effect")) not in managed
            ]
    return result


def _legacy_entry(entry: dict, repo: Path, shared_root: Path) -> bool:
    command = entry.get("command", [])
    try:
        profile = entry.get("command", [])[-1]
        if profile not in ("author", "lead"):
            return False
        expected = [str(shared_root/".venv-workflow-mcp/Scripts/python.exe"), str(shared_root/"tooling/agent_workflow/workflow_mcp.py"),
                    "--repo", str(repo.resolve()), "--profile", profile]
        norm = lambda x: str(x).replace("\\", "/").rstrip("/").casefold()
        return (set(entry) == {"type", "command", "cwd", "environment", "disabled", "codemode", "timeout", "protocol"}
                and entry.get("type") == "local" and len(command) == len(expected)
                and all(norm(a) == norm(b) for a,b in zip(command,expected))
                and norm(entry.get("cwd", "")) == norm(repo) and entry.get("environment") == {}
                and entry.get("disabled") in (True, False) and entry.get("codemode") is False and entry.get("protocol") == "legacy"
                and entry.get("timeout") == {"startup":15000,"catalog":15000,"execution":300000})
    except (ValueError, IndexError):
        return False


def _jsonc_text(raw: bytes) -> tuple[str, bool]:
    """Blank JSONC comments without shifting offsets; reject non-JSON constructs."""
    bom = raw.startswith(b"\xef\xbb\xbf")
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeError as exc:
        raise ValueError("retirement config is not UTF-8") from exc
    chars = list(text); i = 0; quoted = False; escaped = False
    while i < len(chars):
        if quoted:
            if escaped: escaped = False
            elif chars[i] == "\\": escaped = True
            elif chars[i] == '"': quoted = False
            i += 1; continue
        if chars[i] == '"': quoted = True; i += 1; continue
        if chars[i:i+2] == ["/", "/"]:
            end = i
            while end < len(chars) and chars[end] not in "\r\n": end += 1
            for n in range(i, end): chars[n] = " "
            i = end; continue
        if chars[i:i+2] == ["/", "*"]:
            end = i + 2
            while end + 1 < len(chars) and chars[end:end+2] != ["*", "/"]: end += 1
            if end + 1 >= len(chars): raise ValueError("unterminated JSONC comment")
            for n in range(i, end + 2):
                if chars[n] not in "\r\n": chars[n] = " "
            i = end + 2; continue
        i += 1
    if quoted: raise ValueError("unterminated JSON string in retirement config")
    # JSONC permits trailing commas. Blank only those delimiter commas so both
    # strict parsing and the byte offsets of every surrounding token survive.
    i = 0; quoted = False; escaped = False
    while i < len(chars):
        if quoted:
            if escaped: escaped = False
            elif chars[i] == "\\": escaped = True
            elif chars[i] == '"': quoted = False
            i += 1; continue
        if chars[i] == '"': quoted = True; i += 1; continue
        if chars[i] == ',':
            end = i + 1
            while end < len(chars) and chars[end].isspace(): end += 1
            if end < len(chars) and chars[end] in '}]': chars[i] = ' '
        i += 1
    return "".join(chars), bom, text


def _json_member_span(text: str, path: tuple[str, ...]) -> tuple[int, int]:
    """Locate a single JSON value by parsed object path, not a textual key search."""
    def ws(i):
        while i < len(text) and text[i].isspace(): i += 1
        return i
    def string(i):
        start = i; i += 1; escaped = False
        while i < len(text):
            if escaped: escaped = False
            elif text[i] == "\\": escaped = True
            elif text[i] == '"': return json.loads(text[start:i+1]), i+1
            i += 1
        raise ValueError("malformed JSON string")
    def value(i, depth=0):
        if depth > 100: raise ValueError("retirement config nesting is excessive")
        i = ws(i); start = i
        if i >= len(text): raise ValueError("truncated JSON")
        if text[i] == '{':
            i = ws(i+1); found = []
            if i < len(text) and text[i] == '}': return i+1, found
            while True:
                i = ws(i); key, i = string(i); i = ws(i)
                if i >= len(text) or text[i] != ':': raise ValueError("malformed JSON object")
                vstart = ws(i+1); vend, nested = value(vstart, depth+1)
                found.append((key, vstart, vend, nested)); i = ws(vend)
                if i < len(text) and text[i] == '}': return i+1, found
                if i >= len(text) or text[i] != ',': raise ValueError("malformed JSON object")
                i = ws(i+1)
        if text[i] == '[':
            i = ws(i+1)
            if i < len(text) and text[i] == ']': return i+1, []
            while True:
                i, _ = value(i, depth+1); i = ws(i)
                if i < len(text) and text[i] == ']': return i+1, []
                if i >= len(text) or text[i] != ',': raise ValueError("malformed JSON array")
                i = ws(i+1)
        if text[i] == '"': _, end = string(i); return end, []
        end = i
        while end < len(text) and text[end] not in ",]} \t\r\n": end += 1
        if end == i: raise ValueError("malformed JSON value")
        return end, []
    _, rows = value(0)
    selected = rows
    for key in path:
        matches = [row for row in selected if row[0] == key]
        if len(matches) != 1: raise ValueError("legacy MCP config path is absent or ambiguous")
        _, begin, end, nested = matches[0]
        selected = nested
    return begin, end


def _retired_config_bytes(raw: bytes, repo: Path, shared_root: Path) -> bytes:
    text, bom, original = _jsonc_text(raw)
    try: config = json.loads(text)
    except json.JSONDecodeError as exc: raise ValueError("retirement config must contain strict JSONC without trailing commas") from exc
    servers = config.get("mcp", {}).get("servers") if isinstance(config, dict) else None
    if not isinstance(servers, dict) or "ms14_workflow" not in servers:
        raise ValueError("receipt-owned legacy MCP entry is missing")
    entry = servers["ms14_workflow"]
    if not _legacy_entry(entry, repo, shared_root): raise ValueError("legacy MCP entry is malformed or not canonical")
    start, end = _json_member_span(text, ("mcp", "servers", "ms14_workflow", "disabled"))
    token = text[start:end]
    if token not in ("false", "true") or (token == "true") != entry["disabled"]:
        raise ValueError("legacy MCP disabled value is not the exact boolean token")
    if entry["disabled"] is True: return raw
    replaced = original[:start] + "true" + original[end:]
    output = replaced.encode("utf-8")
    return (b"\xef\xbb\xbf" + output) if bom else output


def stage_legacy_retirement_plan(binding_plan: Path, stage: Path, receipt: dict, *, main_root: Path, shared_root: Path) -> dict:
    """Stage only explicit, receipt-owned legacy MCP config disable actions."""
    raw_bytes = binding_plan.read_bytes(); raw = json.loads(raw_bytes.decode("utf-8-sig"))
    if not isinstance(raw, dict) or set(raw) != {"version", "targets"} or raw["version"] != 1 or not isinstance(raw["targets"], list) or not raw["targets"]:
        raise ValueError("retirement plan requires version 1 and explicit targets")
    allowed = {"main", *(f"MoonStation14-agent-0{i}" for i in range(1, 6))}
    if any(not isinstance(name, str) or name not in allowed for name in raw["targets"]) or len(set(raw["targets"])) != len(raw["targets"]):
        raise ValueError("retirement targets must be unique explicit MAIN/agent-01..05 names")
    if set(raw["targets"]) != allowed:
        raise ValueError("legacy retirement must explicitly cover MAIN and all five named agent worktrees")
    if (not main_root.is_absolute() or main_root.name != "MoonStation14" or not main_root.is_dir() or
            not shared_root.is_absolute() or not shared_root.is_dir()):
        raise ValueError("retirement receipt must bind existing MAIN and shared installation roots")
    _assert_no_reparse(Path(main_root.anchor), main_root); _assert_no_reparse(Path(shared_root.anchor), shared_root)
    staged_files = {}; parent = main_root.parent
    for name in raw["targets"]:
        root = main_root if name == "main" else parent / name
        if not root.is_dir(): raise ValueError(f"explicit retirement worktree does not exist: {name}")
        _assert_no_reparse(Path(root.anchor), root)
        config_path = root / "opencode.jsonc"; _assert_no_reparse(root, config_path)
        rows = [row for row in receipt.get("files", []) if row.get("target") == name and row.get("path") == "opencode.jsonc"]
        if len(rows) != 1 or rows[0].get("deleted") or not config_path.is_file() or config_path.is_symlink():
            raise ValueError(f"config does not have exactly one current receipt owner: {name}")
        before = config_path.read_bytes(); old_sha = hashlib.sha256(before).hexdigest()
        if rows[0].get("new_sha256") != old_sha: raise ValueError(f"receipt-owned config hash mismatch: {name}")
        after = _retired_config_bytes(before, root, shared_root)
        relative = "opencode.jsonc"; key = f"{name}/{relative}"
        backup_rel = Path("bindings/backups") / name / relative; staged_rel = Path("bindings") / name / relative
        backup = stage / backup_rel; staged = stage / staged_rel
        for path in (backup, staged):
            _assert_no_reparse(stage, path); path.parent.mkdir(parents=True, exist_ok=True); _assert_no_reparse(stage, path)
        new_sha = hashlib.sha256(after).hexdigest(); source_sha = hashlib.sha256(before).hexdigest()
        if backup.exists() and hashlib.sha256(backup.read_bytes()).hexdigest() != old_sha:
            raise ValueError(f"refusing to overwrite conflicting retirement backup: {key}")
        if staged.exists() and hashlib.sha256(staged.read_bytes()).hexdigest() != new_sha:
            raise ValueError(f"refusing to overwrite conflicting retirement stage member: {key}")
        if not backup.exists(): backup.write_bytes(before)
        if not staged.exists(): staged.write_bytes(after)
        staged_files[key] = {"target":name,"path":relative,"old_sha256":old_sha,"new_sha256":new_sha,
            "receipt_sha256":rows[0]["new_sha256"],"backup_path":backup_rel.as_posix(),"backup_sha256":old_sha,
            "staged_path":staged_rel.as_posix(),"staged_sha256":new_sha,"source":str(config_path),"source_sha256":source_sha,
            "config_scope":{"legacy_retirement":True,"server_key":"ms14_workflow"}}
    staged_rel = "bindings/binding-plan.json"; plan_copy = stage / staged_rel
    plan_copy.parent.mkdir(parents=True, exist_ok=True); _assert_no_reparse(stage, plan_copy)
    if plan_copy.exists() and hashlib.sha256(plan_copy.read_bytes()).hexdigest() != hashlib.sha256(raw_bytes).hexdigest():
        raise ValueError("refusing to overwrite conflicting staged retirement plan")
    if not plan_copy.exists(): shutil.copyfile(binding_plan, plan_copy)
    result = {"version":1,"mode":"legacy-retirement","source_plan":str(binding_plan.resolve()),
        "source_plan_sha256":hashlib.sha256(raw_bytes).hexdigest(),"staged_plan":staged_rel,
        "staged_plan_sha256":hashlib.sha256(raw_bytes).hexdigest(),"receipt_path":receipt["_path"],
        "receipt_sha256":receipt["_sha256"],"targets":sorted(raw["targets"]),"files":staged_files}
    manifest_path=stage/"bindings/binding-manifest.json"; _assert_no_reparse(stage,manifest_path)
    manifest_bytes=(json.dumps(result,indent=2,sort_keys=True)+"\n").encode("utf-8")
    if manifest_path.exists() and manifest_path.read_bytes()!=manifest_bytes:
        raise ValueError("refusing to overwrite conflicting retirement binding manifest")
    if not manifest_path.exists(): manifest_path.write_bytes(manifest_bytes)
    return result


def _strict_ordered_json(raw: bytes):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("duplicate JSON object key")
            result[key] = value
        return result
    try:
        return json.loads(raw.decode("utf-8-sig"), object_pairs_hook=pairs)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("formatting baseline/current config must be strict JSON") from exc


def stage_shell_policy_retirement_plan(binding_plan: Path, stage: Path, receipt: dict, *,
                                       main_root: Path, shared_root: Path) -> dict:
    """Stage the bounded five-worktree shell-policy retirement without applying it."""
    raw_bytes = binding_plan.read_bytes()
    try:
        plan = json.loads(raw_bytes.decode("utf-8-sig"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("shell policy plan must be strict JSON") from exc
    required = {"version", "mode", "targets"}
    if not isinstance(plan, dict) or set(plan) - (required | {"formatting_preimages","profiles"}) or not required <= set(plan):
        raise ValueError("shell policy plan has unknown or missing fields")
    if plan["version"] != 1 or plan["mode"] != "inspection-safe-shell" or not isinstance(plan["targets"], list):
        raise ValueError("shell policy plan mode/version is invalid")
    names = {"main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))}
    targets = plan["targets"]
    if len(targets) != 5 or any(not isinstance(row, dict) or set(row) != {"target", "expected_config_sha256"}
            or not isinstance(row["target"], str) or row["target"] not in names
            or not isinstance(row["expected_config_sha256"], str)
            or not re.fullmatch(r"[0-9a-f]{64}", row["expected_config_sha256"]) for row in targets) \
            or {row["target"] for row in targets} != names:
        raise ValueError("shell policy plan must name MAIN and agents 01..04 exactly once")
    if (not main_root.is_absolute() or main_root.name != "MoonStation14" or not main_root.is_dir()
            or not shared_root.is_absolute() or not shared_root.is_dir()):
        raise ValueError("shell policy plan requires existing absolute MAIN/shared roots")
    _assert_no_reparse(Path(main_root.anchor), main_root); _assert_no_reparse(Path(shared_root.anchor), shared_root)
    formatting = plan.get("formatting_preimages", {})
    if not isinstance(formatting, dict) or set(formatting) - {"main"}:
        raise ValueError("formatting_preimages may contain only MAIN")
    profile_rows=plan.get("profiles",[])
    profile_targets={f"MoonStation14-agent-0{i}" for i in range(1,4)}
    if "profiles" in plan and (not isinstance(profile_rows,list) or len(profile_rows)!=3 or
            any(not isinstance(r,dict) or set(r)!={"target","path","expected_sha256"} or
                r.get("target") not in profile_targets or r.get("path")!=".opencode/agents/fidelity-reviewer.md" or
                not isinstance(r.get("expected_sha256"),str) or not re.fullmatch(r"[0-9a-f]{64}",r["expected_sha256"]) for r in profile_rows) or
            {r["target"] for r in profile_rows if isinstance(r,dict)}!=profile_targets):
        raise ValueError("reviewer profiles must name agents 01..03 and the exact fidelity-reviewer path")
    stage_files = {}
    for row in targets:
        name = row["target"]
        root = main_root if name == "main" else main_root.parent / name
        if not root.is_dir():
            raise ValueError(f"shell policy target does not exist: {name}")
        _assert_no_reparse(Path(root.anchor), root)
        config_path = root / "opencode.jsonc"
        _assert_no_reparse(root, config_path)
        current = config_path.read_bytes()
        receipt_rows = [item for item in receipt.get("files", [])
                        if item.get("target") == name and item.get("path") == "opencode.jsonc"]
        if len(receipt_rows) != 1 or receipt_rows[0].get("deleted"):
            raise ValueError(f"config is not uniquely receipt-owned: {name}")
        receipt_sha = receipt_rows[0].get("new_sha256")
        current_sha = hashlib.sha256(current).hexdigest()
        if row["expected_config_sha256"] != current_sha:
            raise ValueError(f"expected current config hash mismatch: {name}")
        current_ordered = _strict_ordered_json(current)
        formatting_meta = None
        if current_sha != receipt_sha:
            spec = formatting.get(name)
            if name != "main" or not isinstance(spec, dict) or set(spec) != {"path", "sha256"}:
                raise ValueError(f"config hash differs from receipt without approved format preimage: {name}")
            baseline = Path(spec["path"])
            if not baseline.is_absolute() or baseline.is_symlink() or not baseline.is_file():
                raise ValueError("formatting preimage must be an existing absolute regular file")
            baseline_bytes = baseline.read_bytes()
            if hashlib.sha256(baseline_bytes).hexdigest() != spec["sha256"] or spec["sha256"] != receipt_sha:
                raise ValueError("formatting preimage does not match the prior receipt hash")
            if _strict_ordered_json(baseline_bytes) != current_ordered:
                raise ValueError("MAIN formatting drift changed ordered config semantics")
            formatting_meta = {"sha256": spec["sha256"]}
        config = _json_config(current)
        # Preserve publisher-owned scope so a later binding publication can
        # continue validating its existing coordinator ownership.
        previous_scope = receipt_rows[0].get("config_scope", {})
        if not isinstance(previous_scope, dict):
            raise ValueError("receipt config scope is malformed")
        changed = retire_inspection_blocking_shell_rules(config)
        after = (json.dumps(changed, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        after_config = json.loads(after.decode("utf-8"))
        # Scope explicitly covers only tuples removed by this policy and carries
        # forward the prior coordinator ownership record verbatim.
        removed = []
        for agent, old_def in config.get("agents", {}).items():
            new_def = after_config.get("agents", {}).get(agent, {})
            new_rules = new_def.get("permissions", []) if isinstance(new_def, dict) else []
            for rule in old_def.get("permissions", []) if isinstance(old_def, dict) else []:
                identity = tuple(rule.get(k) for k in ("action", "resource", "effect")) if isinstance(rule, dict) else ()
                if identity in KNOWN_LEGACY_SHELL_DENIES and rule not in new_rules:
                    removed.append({"agent": agent, "action": identity[0], "resource": identity[1], "effect": identity[2]})
        scope = {**previous_scope, "shell_policy_retirement": True, "retired_shell_rules": removed}
        rel = "opencode.jsonc"
        backup_rel = Path("bindings/backups") / name / rel
        staged_rel = Path("bindings") / name / rel
        backup, staged = stage / backup_rel, stage / staged_rel
        for path in (backup, staged):
            _assert_no_reparse(stage, path); path.parent.mkdir(parents=True, exist_ok=True); _assert_no_reparse(stage, path)
        backup.write_bytes(current); staged.write_bytes(after)
        digest = hashlib.sha256(after).hexdigest()
        item = {"target": name, "path": rel, "old_sha256": current_sha, "new_sha256": digest,
            "receipt_sha256": receipt_sha, "backup_path": backup_rel.as_posix(),
            "backup_sha256": current_sha, "staged_path": staged_rel.as_posix(),
            "staged_sha256": digest, "source": str(config_path), "source_sha256": current_sha,
            "config_scope": scope}
        if formatting_meta:
            item["formatting_preimage"] = formatting_meta
        stage_files[f"{name}/{rel}"] = item
    for profile in profile_rows:
        name=profile["target"];root=main_root.parent/name
        profile_path=root/profile["path"];_assert_no_reparse(root,profile_path)
        current=profile_path.read_bytes();current_sha=hashlib.sha256(current).hexdigest()
        receipt_rows=[r for r in receipt.get("files",[]) if r.get("target")==name and r.get("path")==profile["path"]]
        if len(receipt_rows)!=1 or receipt_rows[0].get("deleted") or receipt_rows[0].get("new_sha256")!=current_sha:
            raise ValueError(f"reviewer profile is not uniquely current receipt-owned: {name}")
        if profile["expected_sha256"]!=current_sha: raise ValueError(f"expected reviewer profile hash mismatch: {name}")
        updated,removed=retire_fidelity_shell_profile(current.decode("utf-8"))
        if not removed: raise ValueError(f"reviewer profile has no exact legacy shell denies: {name}")
        staged_bytes=updated.encode("utf-8")
        backup_rel=Path("bindings/backups")/name/profile["path"];staged_rel=Path("bindings")/name/profile["path"]
        backup,staged=stage/backup_rel,stage/staged_rel
        for p in (backup,staged): _assert_no_reparse(stage,p);p.parent.mkdir(parents=True,exist_ok=True);_assert_no_reparse(stage,p)
        backup.write_bytes(current);staged.write_bytes(staged_bytes);new_sha=hashlib.sha256(staged_bytes).hexdigest()
        previous_profile_scope=receipt_rows[0].get("profile_scope",{})
        if not isinstance(previous_profile_scope,dict): raise ValueError("receipt profile scope is malformed")
        stage_files[f"{name}/{profile['path']}"]={"target":name,"path":profile["path"],"old_sha256":current_sha,
            "new_sha256":new_sha,"receipt_sha256":current_sha,"backup_path":backup_rel.as_posix(),"backup_sha256":current_sha,
            "staged_path":staged_rel.as_posix(),"staged_sha256":new_sha,"source":str(profile_path),"source_sha256":current_sha,
            "profile_scope":{**previous_profile_scope,"shell_policy_retirement":True,"retired_shell_denies":removed}}
    plan_rel = "bindings/binding-plan.json"
    plan_copy = stage / plan_rel
    _assert_no_reparse(stage, plan_copy); plan_copy.parent.mkdir(parents=True, exist_ok=True)
    if plan_copy.exists() and plan_copy.read_bytes() != raw_bytes:
        raise ValueError("refusing to overwrite conflicting staged shell policy plan")
    if not plan_copy.exists(): plan_copy.write_bytes(raw_bytes)
    manifest = {"version": 1, "mode": "inspection-safe-shell", "source_plan": str(binding_plan.resolve()),
        "source_plan_sha256": hashlib.sha256(raw_bytes).hexdigest(), "staged_plan": plan_rel,
        "staged_plan_sha256": hashlib.sha256(raw_bytes).hexdigest(), "receipt_path": receipt["_path"],
        "receipt_sha256": receipt["_sha256"], "targets": sorted(names), "files": stage_files}
    manifest_path = stage / "bindings/binding-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def _known_receipt_profile_rules(relative: str, content: bytes) -> list[tuple[str, str]]:
    """Exact legacy coordinator entries emitted by the current lead-profile publisher."""
    if relative != ".opencode/agents/integration-lead.md":
        return []
    text = content.decode("utf-8-sig")
    expected = [(f"ms14_workflow_{tool}", "allow") for tool in ("status", "validate", "review", "finish")]
    for action, effect in expected:
        if not re.search(rf"(?m)^  {re.escape(action)}: {re.escape(effect)}\s*$", text):
            raise ValueError(f"receipt-owned lead profile is missing publisher rule {action}: {relative}")
    return expected


def _recover_receipt_config_scope(receipt: dict, row: dict) -> dict:
    """Recover coordinator ownership only from the hash-bound original staged manifest."""
    stage_value = receipt.get("stage")
    if not isinstance(stage_value, str) or not Path(stage_value).is_absolute():
        raise ValueError("receipt original stage path is malformed")
    stage = Path(stage_value)
    _assert_no_reparse(Path(stage.anchor), stage)
    manifest_path = stage / "manifest.json"
    _assert_no_reparse(Path(stage.anchor), manifest_path)
    if not manifest_path.is_file() or hashlib.sha256(manifest_path.read_bytes()).hexdigest() != receipt.get("manifest_sha256"):
        raise ValueError("receipt original stage manifest hash mismatch")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    bindings = manifest.get("bindings") if isinstance(manifest, dict) else None
    if (not isinstance(manifest, dict) or manifest.get("schema_version") != 1 or
            manifest.get("package") != "workflow-coordinator-inactive" or
            not isinstance(bindings, dict) or bindings.get("status") != "planned-explicit-targets"):
        raise ValueError("original stage is not a planned explicit-target coordinator package")
    binding = bindings.get("manifest")
    if not isinstance(binding, str):
        raise ValueError("original stage binding manifest reference is malformed")
    rel = Path(binding)
    if rel.is_absolute() or ".." in rel.parts or not rel.parts:
        raise ValueError("original stage binding manifest path is unsafe")
    binding_path = stage / rel
    _assert_no_reparse(Path(stage.anchor), binding_path)
    if not binding_path.is_file() or hashlib.sha256(binding_path.read_bytes()).hexdigest() != bindings.get("sha256"):
        raise ValueError("original stage binding manifest hash mismatch")
    data = json.loads(binding_path.read_text(encoding="utf-8"))
    key = f"{row.get('target')}/{row.get('path')}"
    item = data.get("files", {}).get(key) if isinstance(data, dict) else None
    if (not isinstance(item, dict) or item.get("target") != row.get("target") or item.get("path") != row.get("path")
            or row.get("path") != "opencode.jsonc" or item.get("new_sha256") != row.get("new_sha256")):
        raise ValueError("original staged coordinator config does not match receipt row")
    staged = item.get("staged_path")
    if not isinstance(staged, str): raise ValueError("original staged config path is malformed")
    staged_rel = Path(staged)
    if staged_rel.is_absolute() or ".." in staged_rel.parts or not staged_rel.parts:
        raise ValueError("original staged config path is unsafe")
    staged_path = stage / staged_rel
    _assert_no_reparse(Path(stage.anchor), staged_path)
    if not staged_path.is_file() or hashlib.sha256(staged_path.read_bytes()).hexdigest() != row.get("new_sha256"):
        raise ValueError("original staged coordinator config hash mismatch")
    scope = item.get("config_scope")
    if not isinstance(scope, dict) or not scope.get("server_keys") or not scope.get("agents"):
        raise ValueError("original staged coordinator scope is missing or malformed")
    return scope


def stage_binding_plan(binding_plan: Path, stage: Path, receipt: dict, *, main_root: Path, shared_root: Path, adapter: Path, python: Path) -> dict:
    """Resolve only named worktrees/roles; stage replacement and backup bytes without applying."""
    raw_bytes = binding_plan.read_bytes()
    raw = json.loads(raw_bytes.decode("utf-8-sig"))
    if (not isinstance(raw, dict) or set(raw) - {"version", "targets", "reviewer_agents", "coordinator_endpoint"}
            or not {"version", "targets", "coordinator_endpoint"}.issubset(raw) or raw["version"] != 1
            or not isinstance(raw["targets"], list) or not raw["targets"]):
        raise ValueError("binding plan must be version 1 with explicit targets")
    endpoint = raw["coordinator_endpoint"]
    if not isinstance(endpoint, str): raise ValueError("coordinator_endpoint must be a plain HTTP loopback base URL")
    try:
        from workflow_coordinator import _loopback_server
    except ModuleNotFoundError:
        from tooling.agent_workflow.workflow_coordinator import _loopback_server
    parsed = urllib.parse.urlsplit(endpoint)
    if parsed.scheme != "http" or not parsed.hostname or parsed.username or parsed.password or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
        raise ValueError("coordinator_endpoint must be a plain HTTP loopback base URL")
    try: parsed_port = parsed.port
    except ValueError as exc: raise ValueError("invalid coordinator_endpoint port") from exc
    if parsed_port == 0: raise ValueError("invalid coordinator_endpoint port")
    endpoint = _loopback_server(endpoint)
    parent = main_root.parent
    reviewer_declarations = {}
    if "reviewer_agents" in raw:
        reviewer_declarations = _validate_reviewer_declarations(raw["reviewer_agents"], raw["targets"], main_root)
        reviewer_read_roots = _trusted_reviewer_read_roots(raw["targets"], main_root)
        for role, declaration in reviewer_declarations.items():
            assigned = [(row, binding) for row in raw["targets"] if isinstance(row, dict)
                        for binding in (row.get("roles") if isinstance(row.get("roles"), list) else [])
                        if isinstance(binding, dict) and binding.get("role") == role]
            if len(assigned) != 1 or assigned[0][0].get("target") != "main" or assigned[0][1].get("agent") != declaration["agent"]:
                raise ValueError(f"dedicated reviewer {role} must match one explicit MAIN dedicated-agent role")
    targets = {}
    manifest_reviewer_agents = {}
    seen = set()
    for row in raw["targets"]:
        if (not isinstance(row, dict) or set(row) - {"target", "coordinator_config", "roles", "tool_output_read"}
                or not {"target", "coordinator_config", "roles"}.issubset(row)):
            raise ValueError("each binding target requires target, coordinator_config and roles")
        name = row["target"]
        tool_output_read = row.get("tool_output_read", False)
        if type(tool_output_read) is not bool:
            raise ValueError("tool_output_read must be a boolean opt-in")
        if tool_output_read and (name not in {f"MoonStation14-agent-0{i}" for i in range(1, 5)} or
                not isinstance(row.get("roles"), list) or
                not any(isinstance(role, dict) and role.get("role") == "author" and
                        (role.get("binding_mode") == "worktree" or role.get("agent") == "orchestrate-ms14")
                        for role in row["roles"])):
            raise ValueError("tool_output_read is limited to author bindings on agent-01..04")
        if name == "main":
            root = main_root
        elif re.fullmatch(r"MoonStation14-agent-0[1-5]", str(name)):
            root = parent / name
        else:
            raise ValueError(f"unknown explicit publication target: {name}")
        if name in seen:
            raise ValueError(f"duplicate binding target: {name}")
        seen.add(name)
        coord_path = Path(row["coordinator_config"])
        if not coord_path.is_absolute() or coord_path.is_symlink() or not coord_path.is_file():
            raise ValueError("coordinator_config must be an existing absolute regular file")
        _assert_no_reparse(Path(coord_path.anchor), coord_path)
        coord_hash = hashlib.sha256(coord_path.read_bytes()).hexdigest()
        coordinator = json.loads(coord_path.read_text(encoding="utf-8"))
        api = coordinator.get("opencode_server")
        if not isinstance(api, str): raise ValueError("target coordinator OpenCode API URL is missing")
        try: api = _loopback_server(api)
        except ValueError as exc: raise ValueError("target coordinator OpenCode API URL is invalid") from exc
        api_parts, listener_parts = urllib.parse.urlsplit(api), urllib.parse.urlsplit(endpoint)
        api_host = "127.0.0.1" if api_parts.hostname.lower() == "localhost" else api_parts.hostname.lower()
        listener_host = "127.0.0.1" if listener_parts.hostname.lower() == "localhost" else listener_parts.hostname.lower()
        api_port, listener_port = api_parts.port or (443 if api_parts.scheme == "https" else 80), listener_parts.port or 80
        if api_host == listener_host and api_port == listener_port:
            raise ValueError("coordinator_endpoint must differ from target opencode_server")
        _assert_no_reparse(main_root.parent, root)
        config_path = root / "opencode.jsonc"
        _assert_no_reparse(root, config_path)
        profile_texts = {}
        profile_hashes = {}
        profile_owned_rules = {}
        # Receipt validation happens before source profile bytes are read.
        config_bytes = config_path.read_bytes()
        target_rows = [x for x in receipt["files"] if x.get("target") == name and x.get("path") == "opencode.jsonc"]
        if len(target_rows) != 1 or target_rows[0].get("deleted") or hashlib.sha256(config_bytes).hexdigest() != target_rows[0].get("new_sha256"):
            raise ValueError(f"OpenCode config is absent, stale or not receipt-owned: {name}/opencode.jsonc")
        config = _json_config(config_bytes)
        config_preimage = deepcopy(config)
        existing_server = config.get("mcp", {}).get("servers", {}).get("ms14_workflow")
        legacy_profile = existing_server["command"][-1] if existing_server is not None and _legacy_entry(existing_server, root, shared_root) else None
        prior_scope = target_rows[0].get("config_scope")
        if "config_scope" in target_rows[0] and (not isinstance(prior_scope, dict) or not prior_scope):
            raise ValueError(f"receipt-owned OpenCode binding scope is malformed: {name}")
        if not isinstance(prior_scope, dict):
            prior_scope = {}
        has_selected_reviewer = any(
            row_role.get("role") in reviewer_declarations and
            row_role.get("agent") in config_preimage.get("agents", {})
            for row_role in row.get("roles", []) if isinstance(row_role, dict))
        if ("config_scope" not in target_rows[0] and legacy_profile is None and
                (existing_server is not None or has_selected_reviewer) and receipt.get("stage") and receipt.get("manifest_sha256")):
            try:
                prior_scope = _recover_receipt_config_scope(receipt, target_rows[0])
            except ValueError as exc:
                if not receipt.get("stage"):
                    raise ValueError(f"receipt-owned coordinator config lacks binding scope: {name}") from exc
                raise
        shared_template = prior_scope.get("shared_template")
        shared_overlay_meta = prior_scope.get("local_overlay")
        shared_overlay = None
        if shared_template is not None or shared_overlay_meta is not None:
            if (not isinstance(shared_template, dict) or set(shared_template) != {"path", "sha256"} or
                    not isinstance(shared_overlay_meta, dict) or set(shared_overlay_meta) != {"path", "sha256"}):
                raise ValueError(f"shared OpenCode ownership metadata is malformed: {name}")
            shared_path = Path(shared_template["path"])
            canonical = (main_root / "tooling/opencode/shared-config.jsonc").resolve()
            if shared_path != canonical or shared_path.is_symlink() or not shared_path.is_file():
                raise ValueError("shared config template path is not canonical MAIN")
            template_bytes = shared_path.read_bytes()
            if hashlib.sha256(template_bytes).hexdigest() != shared_template["sha256"]:
                raise ValueError("shared config template changed; apply shared config first")
            overlay_path = root / shared_overlay_meta["path"]
            if shared_overlay_meta["path"] != ".opencode/workflow/opencode-local.json":
                raise ValueError("shared local overlay path is not canonical")
            _assert_no_reparse(root, overlay_path)
            if not overlay_path.is_file() or overlay_path.is_symlink():
                raise ValueError("receipt-owned shared local overlay is missing")
            overlay_bytes = overlay_path.read_bytes(); overlay_sha = hashlib.sha256(overlay_bytes).hexdigest()
            if overlay_sha != shared_overlay_meta["sha256"]:
                raise ValueError("shared local overlay changed outside receipt")
            overlay_receipts = [x for x in receipt["files"] if x.get("target") == name and x.get("path") == shared_overlay_meta["path"] and not x.get("deleted")]
            if len(overlay_receipts) != 1 or overlay_receipts[0].get("new_sha256") != overlay_sha:
                raise ValueError("shared local overlay is not uniquely receipt-owned")
            import workflow_opencode_config as oc
            existing_overlay = oc._overlay(overlay_bytes)
            shared_overlay = existing_overlay
        prior_server_keys = prior_scope.get("server_keys", [])
        prior_agents = prior_scope.get("agents", [])
        if (not isinstance(prior_server_keys, list) or not all(isinstance(x, str) for x in prior_server_keys)
                or not isinstance(prior_agents, list) or not all(isinstance(x, str) for x in prior_agents)):
            raise ValueError(f"receipt-owned OpenCode binding scope is malformed: {name}")
        role_rows = _normalise_roles(coordinator, row["roles"], root, coord_path.parent)
        if tool_output_read and not any(role.get("role") == "author" and
                                        role.get("agent") == "orchestrate-ms14" for role in role_rows):
            raise ValueError("tool_output_read requires the normalized orchestrate-ms14 author role")
        source_role_rows = deepcopy(role_rows)
        target_reviewer_agents = {}
        for role_row in role_rows:
            declaration = reviewer_declarations.get(role_row["role"])
            if declaration is None:
                continue
            expected_agent = declaration["agent"]
            definition = _reviewer_agent_definition(declaration, root, reviewer_read_roots)
            existing_agent = config_preimage["agents"].get(expected_agent)
            if existing_agent is not None:
                prior = prior_scope.get("reviewer_agents", {}).get(declaration["role"])
                base = deepcopy(existing_agent)
                managed_permissions = _reviewer_workflow_rules(declaration["role"])
                base["permissions"] = [p for p in base.get("permissions", [])
                                        if tuple(p.get(k) for k in ("action", "resource", "effect")) not in managed_permissions]
                definition_hash = hashlib.sha256(json.dumps(base, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
                system_hash = hashlib.sha256(base.get("system", "").encode("utf-8")).hexdigest()
                source = declaration["source_receipt"]
                if (expected_agent not in prior_agents or "ms14_workflow_review_" + declaration["role"] not in prior_server_keys
                        or not isinstance(prior, dict) or prior.get("agent") != expected_agent
                        or prior.get("source_agent") != declaration["source_agent"]
                        or prior.get("source_receipt") != source or prior.get("model") != declaration["model"]
                        or prior.get("request") != declaration["request"]
                        or prior.get("system_sha256") != system_hash
                        or prior.get("definition_sha256") != definition_hash):
                    raise ValueError(f"conflicting/unowned reserved reviewer agent: {expected_agent}")
            config.setdefault("agents", {})[expected_agent] = definition
            target_reviewer_agents[expected_agent] = declaration
            if role_row["agent"] != expected_agent:
                raise ValueError(f"planned reviewer target does not name its reserved dedicated agent: {expected_agent}")
            role_row.pop("profile_path", None)
            output_dir = (Path(declaration["output_root"]) / declaration["role"]).as_posix()
            manifest_reviewer_agents[declaration["role"]] = {
                "agent": expected_agent, "source_agent": declaration["source_agent"],
                 "source_receipt": declaration["source_receipt"], "model": declaration["model"],
                "request": declaration["request"], "output_root": declaration["output_root"],
                "read_roots": reviewer_read_roots,
                "system_sha256": hashlib.sha256(definition["system"].encode("utf-8")).hexdigest(),
                "definition_sha256": hashlib.sha256(json.dumps(definition, sort_keys=True, separators=(",", ":")).encode()).hexdigest(),
            }
        if (existing_server is not None and legacy_profile is None and
                (not isinstance(prior_scope, dict) or not prior_server_keys or not prior_agents)):
            raise ValueError(f"receipt-owned coordinator config lacks binding scope: {name}")
        if any(row["agent"] in config_preimage.get("agents", {}) for row in role_rows if row["role"] in reviewer_declarations):
            if not isinstance(prior_scope, dict) or not prior_scope.get("reviewer_agents"):
                raise ValueError(f"receipt-owned coordinator config lacks reviewer scope: {name}")
        for binding in role_rows:
            profile = binding.get("profile_path") if isinstance(binding, dict) else None
            if not profile:
                continue
            relative = Path(profile)
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("profile_path must be a safe relative target")
            profile_path = root / relative
            _assert_no_reparse(root, profile_path)
            receipt_profile = [x for x in receipt["files"] if x.get("target") == name and x.get("path") == relative.as_posix()]
            if len(receipt_profile) != 1 or receipt_profile[0].get("deleted"):
                raise ValueError(f"profile is absent from publication receipt: {name}/{profile}")
            profile_bytes = profile_path.read_bytes()
            old_hash = hashlib.sha256(profile_bytes).hexdigest()
            if old_hash != receipt_profile[0].get("new_sha256"):
                raise ValueError(f"profile is stale against publication receipt: {name}/{profile}")
            profile_texts[relative.as_posix()] = profile_bytes
            profile_hashes[relative.as_posix()] = old_hash
            profile_owned_rules[relative.as_posix()] = (
                _known_receipt_profile_rules(relative.as_posix(), profile_bytes) if legacy_profile == "lead" else [])
        planner_roles = deepcopy(row["roles"])
        for planner_role in planner_roles:
            if planner_role.get("role") in reviewer_declarations:
                planner_role.pop("profile_path", None)
        role_spec = {"worktree": str(root.resolve()), "roles": planner_roles}
        # Only this exact, receipt-owned old server entry may be migrated.
        old_server = existing_server if any(x["role"] == "author" for x in role_rows) else None
        owned_servers = {}
        for selected in role_rows:
            selected_key = "ms14_workflow" if selected["role"] == "author" else f"ms14_workflow_review_{selected['role']}"
            prior_entry = config_preimage.get("mcp", {}).get("servers", {}).get(selected_key)
            if prior_entry is not None and selected_key in prior_server_keys:
                owned_servers[selected_key] = prior_entry
        if old_server is not None:
            old_command = old_server.get("command") if isinstance(old_server, dict) else None
            receipt_owned_remote = ("ms14_workflow" in prior_server_keys and "orchestrate-ms14" in prior_agents
                and _canonical_remote_entry_shape(old_server, "author"))
            valid_author_command_shape = (
                isinstance(old_command, list) and (
                    (len(old_command) == 10 and old_command[8:] == ["--profile", "author"]) or
                    (len(old_command) == 12 and old_command[8:] == ["--profile", "author", "--author-mode", "worktree"])))
            if legacy_profile not in ("author", "lead") and not receipt_owned_remote and (
                    "ms14_workflow" not in prior_server_keys or "orchestrate-ms14" not in prior_agents
                    or not isinstance(old_server, dict) or set(old_server) != {"type", "command", "cwd", "environment", "disabled", "codemode", "protocol", "timeout"}
                    or old_server.get("type") != "local" or old_server.get("disabled") is not False
                    or old_server.get("codemode") is not False or old_server.get("protocol") != "legacy"
                    or old_server.get("environment") != {} or not valid_author_command_shape
                    or old_server.get("cwd") != str(root.resolve())
                    or old_server.get("timeout") != {"startup": 15000, "catalog": 15000, "execution": 300000}):
                raise ValueError(f"conflicting/unowned legacy author MCP server at {name}")
            owned_servers["ms14_workflow"] = old_server
        selected_agent_roles = {}
        for identity in role_rows:
            selected_agent_roles.setdefault(identity["agent"], set()).add(identity["role"])
        owned_rules = []
        for agent, assigned_roles in selected_agent_roles.items():
            for permission in config["agents"][agent].get("permissions", []):
                action = permission.get("action", "")
                if not (isinstance(action, str) and action.startswith("ms14_workflow")):
                    continue
                identity = tuple(permission.get(k) for k in ("action", "resource", "effect"))
                if agent in target_reviewer_agents:
                    allowed = set().union(*(_reviewer_workflow_rules(role) for role in assigned_roles if role in REVIEW_ROLES))
                    if identity not in allowed:
                        raise ValueError(f"unowned workflow permission on dedicated reviewer {agent}: {identity}")
                    owned_rules.append(identity)
                    continue
                profiles = (legacy_profile,) if legacy_profile else ()
                selected_role = next((role for role in assigned_roles if role in ("author", *REVIEW_ROLES)), None)
                coordinator_rules = set()
                if agent in prior_agents and selected_role and (
                        ("ms14_workflow" if selected_role == "author" else f"ms14_workflow_review_{selected_role}") in prior_server_keys):
                    prefix = "ms14_workflow" if selected_role == "author" else f"ms14_workflow_review_{selected_role}"
                    tools = AUTHOR_TOOLS if selected_role == "author" else REVIEW_TOOLS
                    coordinator_rules = {(f"{prefix}_{tool}", "*", "allow") for tool in tools} | {("ms14_workflow_*", "*", "deny")}
                if identity not in coordinator_rules and (not profiles or not any(identity in _expected_legacy_rules(agent, profile) for profile in profiles)):
                    raise ValueError(f"selected agent has unowned/unsupported workflow permission: {agent} {identity}")
                owned_rules.append(identity)
        new_config, profile_plans = plan_explicit_target(config, coordinator, role_spec, profile_texts,
            config_base=str(coord_path.parent), profile_receipt_hashes=profile_hashes,
            repo=str(root), endpoint=endpoint, python=str(python), adapter=str(adapter),
            receipt_owned_servers=owned_servers, receipt_owned_rules=owned_rules,
            profile_owned_rules=profile_owned_rules,
            transport="http",
            )
        tool_output_rules = []
        tool_output_agents = []
        if tool_output_read:
            output_root = (Path.home() / ".local/share/opencode/tool-output").resolve()
            resource = output_root.as_posix().rstrip("/") + "/**"
            tool_output_agents = ["orchestrate-ms14", "implement"]
            for agent_name in tool_output_agents:
                agent = new_config["agents"].get(agent_name)
                if not isinstance(agent, dict) or not isinstance(agent.get("permissions"), list):
                    raise ValueError(f"tool_output_read requires an existing permissions list for {agent_name}")
                permissions = agent["permissions"]
                allowed = {"action": "external_directory", "resource": resource, "effect": "allow"}
                denied = {"action": "edit", "resource": resource, "effect": "deny"}
                permissions[:] = [p for p in permissions if p not in (allowed, denied)]
                permissions.extend((allowed, denied))
                tool_output_rules.extend(tuple(rule[k] for k in ("action", "resource", "effect"))
                                          for rule in (allowed, denied))
        managed_reviewer_names = set(target_reviewer_agents)
        if (_unowned_view(config_preimage, role_rows, owned_rules, managed_reviewer_names, tool_output_rules) !=
                _unowned_view(new_config, role_rows, owned_rules, managed_reviewer_names, tool_output_rules)):
            raise ValueError(f"unowned OpenCode config semantics changed for {name}")
        output = []
        config_new = (json.dumps(new_config, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
        scope = {**prior_scope, "server_keys": ["ms14_workflow" if r["role"] == "author" else f"ms14_workflow_review_{r['role']}" for r in role_rows],
                 "transport": "http",
                 "agents": sorted({r["agent"] for r in role_rows}),
                 "legacy_rules": [{"action":a,"resource":r,"effect":e} for a,r,e in owned_rules],
                 **({"tool_output_read": True, "tool_output_path": str(output_root), "tool_output_rules": [
                     {"action":a,"resource":r,"effect":e} for a,r,e in tool_output_rules],
                     "tool_output_agents": tool_output_agents} if tool_output_read else {}),
                  "reviewer_agents": {role: manifest_reviewer_agents[role] for role in (d["role"] for d in target_reviewer_agents.values())}}
        if shared_overlay is not None:
            from workflow_opencode_config import load_template, extract_local_overlay, render_config, configs_equivalent, serialize_config
            shared_overlay = extract_local_overlay(load_template(template_bytes), new_config, existing_overlay["variables"])
            rendered = render_config(load_template(template_bytes), shared_overlay)
            if not configs_equivalent(new_config, rendered):
                raise ValueError("binding update cannot roundtrip through shared local overlay")
            overlay_new_bytes = serialize_config(shared_overlay).encode("utf-8")
            scope["shared_template"] = shared_template
            scope["local_overlay"] = {"path": shared_overlay_meta["path"], "sha256": hashlib.sha256(overlay_new_bytes).hexdigest()}
        output.append(("opencode.jsonc", config_bytes, config_new, target_rows[0], scope))
        if shared_overlay is not None:
            output.append((shared_overlay_meta["path"], overlay_bytes, overlay_new_bytes, overlay_receipts[0], None))
        for relative, profile_plan in profile_plans.items():
            profile_row = next(x for x in receipt["files"] if x.get("target") == name and x.get("path") == relative)
            output.append((relative, profile_plan["old_bytes"], profile_plan["new_bytes"], profile_row, None))
        targets[name] = {"root": str(root), "coordinator_config": str(coord_path), "coordinator_config_sha256": coord_hash,
                         "files": output}
    staged_files = {}
    for name, target in targets.items():
        for relative, old_bytes, new_bytes, receipt_row, scope in target["files"]:
            backup_rel = Path("bindings/backups") / name / relative
            new_rel = Path("bindings") / name / relative
            backup_path = stage / backup_rel
            new_path = stage / new_rel
            for path in (backup_path, new_path):
                _assert_no_reparse(stage, path)
                path.parent.mkdir(parents=True, exist_ok=True)
                _assert_no_reparse(stage, path)
            backup_path.write_bytes(old_bytes)
            new_path.write_bytes(new_bytes)
            old_sha = hashlib.sha256(old_bytes).hexdigest()
            new_sha = hashlib.sha256(new_bytes).hexdigest()
            staged_files[f"{name}/{relative}"] = {"target": name, "path": relative, "old_sha256": old_sha, "new_sha256": new_sha,
                "receipt_sha256": receipt_row["new_sha256"], "backup_path": backup_rel.as_posix(),
                "backup_sha256": hashlib.sha256(old_bytes).hexdigest(), "staged_path": new_rel.as_posix(),
                "staged_sha256": new_sha, "source": str(target["coordinator_config"]), "source_sha256": target["coordinator_config_sha256"],
                "config_scope": scope}
    result = {"version": 1, "source_plan": str(binding_plan.resolve()), "source_plan_sha256": hashlib.sha256(raw_bytes).hexdigest(),
              "coordinator_endpoint": endpoint,
              "staged_plan": "bindings/binding-plan.json", "staged_plan_sha256": hashlib.sha256(raw_bytes).hexdigest(),
              "receipt_path": receipt["_path"], "receipt_sha256": receipt["_sha256"], "targets": sorted(targets), "files": staged_files}
    if reviewer_declarations:
        result["reviewer_agents"] = manifest_reviewer_agents
    binding_root = stage / "bindings"
    plan_copy = binding_root / "binding-plan.json"
    _assert_no_reparse(stage, plan_copy)
    plan_copy.parent.mkdir(parents=True, exist_ok=True)
    _assert_no_reparse(stage, plan_copy)
    if plan_copy.exists() and hashlib.sha256(plan_copy.read_bytes()).hexdigest() != hashlib.sha256(raw_bytes).hexdigest():
        raise ValueError("refusing to overwrite different staged binding plan")
    shutil.copyfile(binding_plan, plan_copy)
    manifest_path = binding_root / "binding-manifest.json"
    _assert_no_reparse(stage, manifest_path)
    manifest_path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return result


REVIEWER_SEARCH_AGENTS = ("workflow-reviewer-implementation", "workflow-reviewer-parity",
                          "workflow-reviewer-coverage", "workflow-reviewer-fidelity")


def transform_reviewer_search_permissions(config: dict) -> dict:
    """Widen only the known reviewers' glob/grep read rules, failing closed on custom rules."""
    result = deepcopy(config)
    agents = config.get("agents")
    if not isinstance(agents, dict):
        raise ValueError("reviewer search config has no agents map")
    for name in REVIEWER_SEARCH_AGENTS:
        original = agents.get(name)
        if not isinstance(original, dict) or not isinstance(original.get("permissions"), list):
            raise ValueError(f"reviewer search permissions missing: {name}")
        rules = original["permissions"]
        replaced = []
        read_roots = {r.get("resource") for r in rules if isinstance(r, dict) and r.get("action") in ("read", "list") and r.get("effect") == "allow"}
        if not read_roots or any(not isinstance(root, str) or not root.endswith("/**") for root in read_roots):
            raise ValueError(f"unrecognized reviewer read/list roots: {name}")
        for action in ("glob", "grep"):
            action_rules = [r for r in rules if isinstance(r, dict) and r.get("action") == action]
            scoped = [r for r in action_rules if r.get("effect") == "allow"]
            if not scoped or any(set(r) != {"action", "resource", "effect"} or not isinstance(r["resource"], str) or
                                 not r["resource"].endswith("/**") for r in scoped):
                raise ValueError(f"unrecognized reviewer {action} permission: {name}")
            roots = [r["resource"] for r in scoped]
            if (len(scoped) != len(action_rules) or len(set(roots)) != len(roots) or set(roots) != read_roots):
                raise ValueError(f"reviewer {action} roots do not exactly match read/list roots: {name}")
            replaced.extend(scoped)
        result["agents"][name]["permissions"] = [r for r in rules if r not in replaced] + [
            {"action": action, "resource": "*", "effect": "allow"} for action in ("glob", "grep")]
    return result


def stage_reviewer_search_permission_plan(binding_plan: Path, stage: Path, receipt: dict, *,
                                          main_root: Path, shared_root: Path) -> dict:
    """Receipt-owned staging for the four fixed reviewer search bindings; never applies changes."""
    raw = binding_plan.read_bytes()
    try:
        plan = json.loads(raw.decode("utf-8-sig"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("reviewer search plan must be strict JSON") from exc
    if (not isinstance(plan, dict) or set(plan) != {"version", "mode", "targets"} or
            plan.get("version") != 1 or plan.get("mode") != "reviewer-search-permissions" or
            not isinstance(plan.get("targets"), list) or len(plan["targets"]) != 1):
        raise ValueError("reviewer search plan has unknown fields or invalid mode")
    row = plan["targets"][0]
    if (not isinstance(row, dict) or set(row) != {"target", "expected_config_sha256"} or
            row.get("target") != "main" or not isinstance(row.get("expected_config_sha256"), str) or
            not re.fullmatch(r"[0-9a-f]{64}", row["expected_config_sha256"])):
        raise ValueError("reviewer search plan must target MAIN only with a config SHA-256")
    if not main_root.is_absolute() or not main_root.is_dir() or not shared_root.is_absolute():
        raise ValueError("reviewer search plan requires existing absolute roots")
    config_path = main_root / "opencode.jsonc"
    _assert_no_reparse(main_root, config_path)
    before = config_path.read_bytes(); old_hash = hashlib.sha256(before).hexdigest()
    if old_hash != row["expected_config_sha256"]:
        raise ValueError("expected current config hash mismatch: main")
    owned = [r for r in receipt.get("files", []) if r.get("target") == "main" and r.get("path") == "opencode.jsonc"]
    if len(owned) != 1 or owned[0].get("deleted") or owned[0].get("new_sha256") != old_hash:
        raise ValueError("MAIN config is not uniquely receipt-owned at expected hash")
    config = _strict_ordered_json(before)
    changed = transform_reviewer_search_permissions(config)
    after = (json.dumps(changed, ensure_ascii=False, indent=2) + "\n").encode()
    backup_rel = "bindings/backups/main/opencode.jsonc"; staged_rel = "bindings/main/opencode.jsonc"
    backup, staged = stage / backup_rel, stage / staged_rel
    for path in (backup, staged):
        _assert_no_reparse(stage, path); path.parent.mkdir(parents=True, exist_ok=True)
    backup.write_bytes(before); staged.write_bytes(after)
    new_hash = hashlib.sha256(after).hexdigest()
    files = {"main/opencode.jsonc": {"target":"main", "path":"opencode.jsonc", "old_sha256":old_hash,
        "new_sha256":new_hash, "receipt_sha256":old_hash, "backup_path":backup_rel,
        "backup_sha256":old_hash, "staged_path":staged_rel, "staged_sha256":new_hash,
        "source":str(config_path), "source_sha256":old_hash, "config_scope":{"reviewer_search_permissions":True}}}
    result = {"version":1,"mode":"reviewer-search-permissions","source_plan":str(binding_plan.resolve()),
        "source_plan_sha256":hashlib.sha256(raw).hexdigest(),"staged_plan":"bindings/binding-plan.json",
        "staged_plan_sha256":hashlib.sha256(raw).hexdigest(),"receipt_path":receipt["_path"],
        "receipt_sha256":receipt["_sha256"],"targets":["main"],"files":files}
    plan_copy = stage / result["staged_plan"]; plan_copy.parent.mkdir(parents=True, exist_ok=True); plan_copy.write_bytes(raw)
    manifest_path = stage / "bindings/binding-manifest.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(result, indent=2, sort_keys=True)+"\n", encoding="utf-8")
    return result
