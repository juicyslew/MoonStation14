"""Regression coverage for durable workflow credential references in shared config plans."""
import copy
import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import workflow_coordinator_bindings as bindings
import workflow_opencode_config as oc


class DurableWorkflowCredentialTests(unittest.TestCase):
    def test_shared_config_refreshes_only_canonical_authorization_references(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp)
            main = base / "MoonStation14"
            shared = base / "shared"
            shared.mkdir()
            template_path = main / "tooling/opencode/shared-config.jsonc"
            template_path.parent.mkdir(parents=True)
            template_bytes = (Path(__file__).resolve().parents[1] / "opencode/shared-config.jsonc").read_bytes()
            template_path.write_bytes(template_bytes)
            roots = {"main": main, **{f"MoonStation14-agent-0{i}": base / f"MoonStation14-agent-0{i}" for i in range(1, 5)}}
            variables = {"shared_root": shared.as_posix(), "workspace_parent": main.parent.as_posix(),
                         "ss14_root": (Path.home() / "Documents/SS14-dev/space-station-14").as_posix()}
            config_path = base / "coordinator.json"
            # The coordinator API and MCP listener deliberately use different ports.
            coordinator = {"opencode_server": "http://127.0.0.1:49200", "author_worktrees": [], "reviewers": {}}
            mcp_endpoint = "http://127.0.0.1:59183"
            receipt_rows = []
            overlays = {}
            config_bytes_by_target = {}
            targets = []
            for name, root in roots.items():
                root.mkdir(exist_ok=True)
                subprocess.run(["git", "init", "-q", str(root)], check=True)
                (root / ".gitignore").write_bytes(b"# fixture\n")
                (root / ".opencode/workflow").mkdir(parents=True)
                token = root / "author.token"
                token.write_text("fixture-secret", encoding="utf-8")
                context_path = base / f"{name}-context.json"
                context = {"repo": str(root), "floors": [], "publication_receipt_path": str(base / "publication.json"),
                           "publication_receipt_sha256": "a" * 64}
                context_path.write_text(json.dumps(context), encoding="utf-8")
                coordinator["author_worktrees"].append({"caller": f"caller-{name}", "token_file": str(token),
                                                          "context_file": str(context_path)})
                if name == "main":
                    for role in ("implementation", "parity", "coverage", "fidelity"):
                        reviewer_token = root / f"{role}.token"
                        reviewer_token.write_text(f"{role}-secret", encoding="utf-8")
                        coordinator["reviewers"][role] = {"worktree": str(root),
                            "agent": f"workflow-reviewer-{role}", "token_file": str(reviewer_token),
                            "caller": f"reviewer-{role}"}
                entry = {"type": "remote", "url": mcp_endpoint + "/mcp/author",
                         "oauth": False, "headers": {"Authorization": "Bearer {file:old-token}"}, "disabled": False,
                         "codemode": False, "protocol": "legacy",
                         "timeout": {"startup": 15000, "catalog": 15000, "execution": 300000}}
                servers = {"ms14_workflow": entry}
                agent_profiles = {}
                if name == "main":
                    for role in ("implementation", "parity", "coverage", "fidelity"):
                        servers[f"ms14_workflow_review_{role}"] = {**entry, "url": mcp_endpoint + "/mcp/reviewer",
                            "headers": {"Authorization": "Bearer {file:old-token}"}}
                        agent_profiles[f"workflow-reviewer-{role}"] = {"description": "preserve reviewer"}
                overlay = {"version": 1, "variables": variables, "config": {"mcp": {"servers": {**servers,
                              "unrelated": {"command": ["keep"], "headers": {"Authorization": "keep"}}}},
                              "model": {"model": "private-model"}, "system": "private-system",
                              "agents": {"implement": {"permissions": [{"action": "read", "resource": "*", "effect": "deny"}]}, **agent_profiles}}}
                overlay_bytes = oc.serialize_config(overlay).encode()
                overlays[name] = overlay
                overlay_path = root / ".opencode/workflow/opencode-local.json"
                overlay_path.write_bytes(overlay_bytes)
                rendered = oc.render_config(oc.load_template(template_bytes), overlay)
                config_bytes = oc.serialize_config(rendered).encode()
                config_bytes_by_target[name] = config_bytes
                (root / "opencode.jsonc").write_bytes(config_bytes)
                subprocess.run(["git", "-C", str(root), "add", "opencode.jsonc", ".gitignore"], check=True)
                subprocess.run(["git", "-C", str(root), "-c", "user.name=test", "-c", "user.email=test@local",
                                "commit", "-qm", "fixture"], check=True)
                receipt_rows.extend([
                    {"target": name, "path": "opencode.jsonc", "new_sha256": hashlib.sha256(config_bytes).hexdigest(),
                     "deleted": False, "config_scope": {"shared_template": {"path": str(template_path),
                     "sha256": hashlib.sha256(template_bytes).hexdigest()}, "local_overlay": {"path": ".opencode/workflow/opencode-local.json",
                     "sha256": hashlib.sha256(overlay_bytes).hexdigest()}}},
                    {"target": name, "path": ".opencode/workflow/opencode-local.json",
                     "new_sha256": hashlib.sha256(overlay_bytes).hexdigest(), "deleted": False}])
                targets.append({"target": name, "expected_config_sha256": hashlib.sha256(config_bytes).hexdigest(),
                                "expected_local_sha256": hashlib.sha256(overlay_bytes).hexdigest(),
                                "expected_gitignore_sha256": hashlib.sha256((root / ".gitignore").read_bytes()).hexdigest(),
                                "expected_index_digest": bindings._index_digest(root)})
            config_bytes = json.dumps(coordinator).encode()
            original_coordinator_bytes = config_bytes
            original_context_bytes = {name: (base / f"{name}-context.json").read_bytes() for name in roots}
            config_path.write_bytes(config_bytes)
            credentials = {"config_path": str(config_path), "sha256": hashlib.sha256(config_bytes).hexdigest(),
                           "coordinator_endpoint": mcp_endpoint}
            receipt = {"files": receipt_rows, "_path": str(base / "receipt.json"), "_sha256": "f" * 64}
            plan = {"version": 1, "mode": "shared-opencode-config",
                    "template": {"path": str(template_path), "sha256": hashlib.sha256(template_bytes).hexdigest()},
                    "initialize": False, "untrack_generated": False, "workflow_credentials": credentials,
                     "targets": targets}
            self.assertIs(bindings.validate_shared_config_plan_shape(plan), plan)
            baseline_plan = copy.deepcopy(plan)
            baseline_plan.pop("workflow_credentials")
            baseline_plan_path = base / "baseline-plan.json"
            baseline_plan_path.write_text(json.dumps(baseline_plan), encoding="utf-8")
            baseline_stage = base / "baseline-stage"
            baseline_result = bindings.stage_shared_config_plan(
                baseline_plan_path, baseline_stage, receipt, main_root=main, shared_root=shared)
            for name, root in roots.items():
                for relative in ("opencode.jsonc", ".opencode/workflow/opencode-local.json"):
                    with self.subTest(target=name, path=relative, baseline=True):
                        row = baseline_result["files"].get(name + "/" + relative)
                        effective_bytes = ((baseline_stage / "bindings" / name / row["staged_path"]).read_bytes()
                                           if row else (root / relative).read_bytes())
                        self.assertEqual((root / relative).read_bytes(), effective_bytes)

            # Model a user editing the generated raw config after publication. Keep
            # the overlay untouched: the credential refresh must preserve this raw
            # preference rather than re-rendering the older overlay over it.
            drift_name = "MoonStation14-agent-02"
            drift_root = roots[drift_name]
            drift_config = json.loads((drift_root / "opencode.jsonc").read_bytes())
            drift_config["model"] = {"model": "actual-local-model"}
            drift_bytes = oc.serialize_config(drift_config).encode()
            (drift_root / "opencode.jsonc").write_bytes(drift_bytes)
            config_bytes_by_target[drift_name] = drift_bytes
            drift_hash = hashlib.sha256(drift_bytes).hexdigest()
            next(row for row in receipt_rows if row["target"] == drift_name and row["path"] == "opencode.jsonc")["new_sha256"] = drift_hash
            next(target for target in targets if target["target"] == drift_name)["expected_config_sha256"] = drift_hash

            plan_path = base / "plan.json"
            plan_path.write_text(json.dumps(plan), encoding="utf-8")
            stage = base / "stage"
            result = bindings.stage_shared_config_plan(plan_path, stage, receipt, main_root=main, shared_root=shared)
            self.assertEqual(15, len(result["files"]))
            staged_raw = json.loads((stage / "bindings" / drift_name / "opencode.jsonc").read_bytes())
            self.assertEqual({"model": "actual-local-model"}, staged_raw["model"])
            expected_raw = copy.deepcopy(drift_config)
            raw_servers = drift_config["mcp"]["servers"]
            staged_servers = staged_raw["mcp"]["servers"]
            self.assertEqual(set(raw_servers), set(staged_servers))
            for server_name, server in raw_servers.items():
                expected_raw["mcp"]["servers"][server_name]["headers"]["Authorization"] = staged_servers[server_name]["headers"]["Authorization"]
            self.assertEqual(expected_raw, staged_raw)
            for name, root in roots.items():
                with self.subTest(target=name):
                    overlay = json.loads((stage / "bindings" / name / ".opencode/workflow/opencode-local.json").read_bytes())
                    prior = overlays[name]
                    self.assertEqual(prior["config"]["mcp"]["servers"]["ms14_workflow"]["url"],
                                     overlay["config"]["mcp"]["servers"]["ms14_workflow"]["url"])
                    self.assertEqual("Bearer {file:" + coordinator["author_worktrees"][list(roots).index(name)]["token_file"] + "}",
                                     overlay["config"]["mcp"]["servers"]["ms14_workflow"]["headers"]["Authorization"])
                    self.assertEqual(prior["config"]["mcp"]["servers"]["unrelated"], overlay["config"]["mcp"]["servers"]["unrelated"])
                    for key in ("model", "system", "agents"):
                        self.assertEqual(prior["config"][key], overlay["config"][key])
                    self.assertEqual({"path": credentials["config_path"], "sha256": credentials["sha256"]},
                                     result["files"][f"{name}/opencode.jsonc"]["config_scope"]["workflow_credentials"])
                    self.assertEqual(config_bytes_by_target[name], (root / "opencode.jsonc").read_bytes())

            main_overlay = json.loads((stage / "bindings" / "main" / ".opencode/workflow/opencode-local.json").read_bytes())
            drift_overlay = json.loads((stage / "bindings" / drift_name / ".opencode/workflow/opencode-local.json").read_bytes())
            self.assertEqual(overlays[drift_name]["config"]["model"], drift_overlay["config"]["model"])
            for role, reviewer in coordinator["reviewers"].items():
                reference = "Bearer {file:" + reviewer["token_file"] + "}"
                self.assertEqual(reference, main_overlay["config"]["mcp"]["servers"][f"ms14_workflow_review_{role}"]["headers"]["Authorization"])
            for label, mutate in (("hash", lambda p: p["workflow_credentials"].update(sha256="0" * 64)),
                                  ("missing token", lambda _: None),
                                  ("foreign author", lambda _: json.loads((base / "MoonStation14-agent-01-context.json").read_text()).update(repo=str(base / "foreign")))):
                bad = copy.deepcopy(plan)
                mutate(bad)
                config_path.write_bytes(original_coordinator_bytes)
                for name, context_bytes in original_context_bytes.items():
                    (base / f"{name}-context.json").write_bytes(context_bytes)
                if label == "missing token":
                    invalid_coordinator = copy.deepcopy(coordinator)
                    invalid_coordinator["author_worktrees"][0]["token_file"] = str(base / "missing.token")
                    invalid_bytes = json.dumps(invalid_coordinator).encode()
                    config_path.write_bytes(invalid_bytes)
                    bad["workflow_credentials"]["sha256"] = hashlib.sha256(invalid_bytes).hexdigest()
                if label == "foreign author":
                    context = base / "MoonStation14-agent-01-context.json"
                    context.write_text(json.dumps({"repo": str(base / "foreign"), "floors": [],
                        "publication_receipt_path": str(base / "publication.json"), "publication_receipt_sha256": "a" * 64}))
                path = base / f"{label.replace(' ', '-')}.json"
                path.write_text(json.dumps(bad), encoding="utf-8")
                expected = {"hash": "hash changed", "missing token": "token.*missing",
                            "foreign author": "existing workflow profiles do not match configured target identities"}[label]
                with self.subTest(rejection=label), self.assertRaisesRegex(ValueError, expected):
                    bindings.stage_shared_config_plan(path, base / f"stage-{label.replace(' ', '-')}", receipt,
                                                      main_root=main, shared_root=shared)

    def test_absent_optional_credentials_keeps_existing_overlay_bytes(self):
        digest = "a" * 64
        plan = {"version": 1, "mode": "shared-opencode-config", "template": {"path": str(Path.cwd() / "template"), "sha256": digest},
                "initialize": False, "untrack_generated": False,
                "targets": [{"target": n, "expected_config_sha256": digest, "expected_local_sha256": digest,
                             "expected_gitignore_sha256": digest, "expected_index_digest": digest}
                            for n in ("main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5)))]}
        self.assertIs(bindings.validate_shared_config_plan_shape(plan), plan)
