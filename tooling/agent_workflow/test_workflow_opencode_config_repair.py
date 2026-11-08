"""Isolated regression coverage for receipt-owned generated OpenCode repair."""
import hashlib
import importlib.util
import json
import os
import subprocess
import sys
import unittest
import tempfile
from pathlib import Path
from unittest import mock


HERE = Path(__file__).resolve().parent
CLI_PATH = HERE.parent / "opencode/config.py"
SPEC = importlib.util.spec_from_file_location("opencode_config_repair_cli", CLI_PATH)
cli = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cli)
sys.path.insert(0, str(HERE))
import workflow_opencode_config as oc


class OpenCodeRepairTests(unittest.TestCase):
    def test_apply_repairs_only_drifted_generated_config(self):
        self._exercise_repair("wrong-token")

    def test_apply_normalizes_semantically_equivalent_format_drift(self):
        self._exercise_repair("format")

    def _exercise_repair(self, drift_kind):
        import workflow_coordinator_bindings as bindings
        import workflow_coordinator_package as package

        with tempfile.TemporaryDirectory() as td:
            base = Path(td); main = base / "MoonStation14"; main.mkdir()
            shared = base / "shared"; shared.mkdir()
            template_bytes = (CLI_PATH.parent / "shared-config.jsonc").read_bytes()
            template_path = main / "tooling/opencode/shared-config.jsonc"
            template_path.parent.mkdir(parents=True); template_path.write_bytes(template_bytes)
            variables = {"shared_root": shared.as_posix(), "workspace_parent": base.as_posix(),
                         "ss14_root": (Path.home() / "Documents/SS14-dev/space-station-14").as_posix()}
            names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
            roots = {}; rows = []; targets = []
            for index, name in enumerate(names):
                root = main if name == "main" else base / name
                if name != "main": root.mkdir()
                roots[name] = root
                (root / ".gitignore").write_text("/opencode.jsonc\n/.opencode/workflow/\n")
                (root / ".opencode/workflow").mkdir(parents=True)
                overlay = {"version": 1, "variables": variables, "config": {
                    "mcp": {"servers": {"workflow": {"url": f"https://fixture-{index}.invalid/{index}"}}},
                    "agents": {"implement": {"model": f"local-model-{index}"}}}}
                overlay_path = root / ".opencode/workflow/opencode-local.json"
                overlay_bytes = oc.serialize_config(overlay).encode(); overlay_path.write_bytes(overlay_bytes)
                generated = oc.serialize_config(oc.render_config(oc.load_template(template_bytes), overlay)).encode()
                (root / "opencode.jsonc").write_bytes(generated)
                subprocess.run(["git", "init", "-q", str(root)], check=True)
                subprocess.run(["git", "-C", str(root), "add", ".gitignore"], check=True)
                subprocess.run(["git", "-C", str(root), "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "fixture"], check=True)
                rows.extend([
                    {"target": name, "path": "opencode.jsonc", "new_sha256": hashlib.sha256(generated).hexdigest(),
                     "deleted": False, "config_scope": {"shared_template": {"path": str(template_path.resolve()), "sha256": hashlib.sha256(template_bytes).hexdigest()},
                     "local_overlay": {"path": ".opencode/workflow/opencode-local.json", "sha256": hashlib.sha256(overlay_bytes).hexdigest()}}},
                    {"target": name, "path": ".opencode/workflow/opencode-local.json", "new_sha256": hashlib.sha256(overlay_bytes).hexdigest(), "deleted": False}])
                targets.append({"target": name, "expected_config_sha256": hashlib.sha256(generated).hexdigest(),
                    "expected_local_sha256": hashlib.sha256(overlay_bytes).hexdigest(),
                    "expected_gitignore_sha256": hashlib.sha256((root / ".gitignore").read_bytes()).hexdigest(),
                    "expected_index_digest": bindings._index_digest(root)})
            receipt = base / "receipt.json"
            receipt.write_text(json.dumps({"files": rows, "source_hashes": {}, "shared_root": str(shared), "main_root": str(main)}))
            pointer = main / package.POINTER_RELATIVE; pointer.parent.mkdir(parents=True, exist_ok=True)
            pointer.write_text(json.dumps({"receipt": str(receipt)}))
            # Bootstrap current installation authority from the fixture receipt.
            installation = main / ".opencode/workflow/current-installation.json"
            installation.write_text(json.dumps({"receipt": str(receipt), "receipt_sha256": hashlib.sha256(receipt.read_bytes()).hexdigest(),
                "config": str(base / "service-config.json"), "health_file": str(base / "health.json"), "port": 59183}))
            generated01 = roots[names[1]] / "opencode.jsonc"
            canonical01 = json.loads(generated01.read_bytes())
            if drift_kind == "format":
                drift = (json.dumps(canonical01, indent=3, ensure_ascii=False) + "\n").encode()
                self.assertNotEqual(hashlib.sha256(drift).hexdigest(), rows[2]["new_sha256"])
                self.assertEqual(canonical01, json.loads(drift))
            else:
                drift = b'{"agents":{"implement":{"model":"MAIN-wrong-token"}}}\n'
            generated01.write_bytes(drift)
            self.assertEqual(cli._status(main, initialize=False)[0], "STALE")
            plan = cli._plan(main, initialize=False)
            self.assertFalse(plan["untrack_generated"])
            plan_path = Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode" / ("repair-plan-" + str(os.getpid()) + ".json")
            plan_path.write_text(json.dumps(plan)); self.addCleanup(lambda: plan_path.unlink(missing_ok=True))
            stage = Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode" / ("repair-stage-" + str(os.getpid()))
            self.addCleanup(lambda: __import__("shutil").rmtree(stage, ignore_errors=True))
            package.build(stage, receipt, binding_plan=plan_path)
            # Stage must remain inactive and must not create backups of generated config.
            self.assertEqual(drift, generated01.read_bytes())
            binding_manifest = json.loads((stage / "bindings/binding-manifest.json").read_text())
            staged_row = binding_manifest["files"][f"{names[1]}/opencode.jsonc"]
            self.assertEqual("local-model-1", json.loads((stage / staged_row["staged_path"]).read_text())["agents"]["implement"]["model"])
            self.assertFalse(list((stage / "bindings/backups").rglob("opencode.jsonc")))
            pointer_hash = hashlib.sha256(pointer.read_bytes()).hexdigest()
            new_receipt = Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode" / ("published-" + str(os.getpid()) + ".json")
            self.addCleanup(lambda: new_receipt.unlink(missing_ok=True))
            # Publication must recheck staged preimages; a late edit is refused
            # without leaving the temporary drifted source overwritten.
            generated01.write_bytes(b'{"late":"race"}\n')
            with self.assertRaises(ValueError):
                package.publish(stage, receipt, new_receipt, pointer_path=pointer,
                                expected_pointer_sha256=pointer_hash)
            generated01.write_bytes(drift)
            result = package.publish(stage, receipt, new_receipt, pointer_path=pointer,
                                     expected_pointer_sha256=pointer_hash)
            self.assertEqual("applied", result["status"])
            for index, name in enumerate(names):
                value = json.loads((roots[name] / "opencode.jsonc").read_text())
                self.assertEqual(f"local-model-{index}", value["agents"]["implement"]["model"])
                self.assertEqual(f"https://fixture-{index}.invalid/{index}", value["mcp"]["servers"]["workflow"]["url"])
            self.assertNotEqual(drift, generated01.read_bytes())
            config_actions = [action for action in result["actions"] if action.get("path") == "opencode.jsonc"]
            self.assertTrue(config_actions)
            self.assertTrue(all(action.get("backup_path") is None and action.get("backup_sha256") is None for action in config_actions))
            installation.write_text(json.dumps({"receipt": str(new_receipt), "receipt_sha256": hashlib.sha256(new_receipt.read_bytes()).hexdigest(),
                "config": str(base / "service-config.json"), "health_file": str(base / "health.json"), "port": 59183}))
            self.assertEqual(("CURRENT", []), cli._status(main, initialize=False))


if __name__ == "__main__":
    unittest.main()
