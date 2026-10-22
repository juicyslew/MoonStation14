import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import types
import unittest
from unittest import mock


CLI_PATH = Path(__file__).resolve().parents[1] / "opencode/config.py"
SPEC = importlib.util.spec_from_file_location("opencode_config_cli", CLI_PATH)
cli = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cli)


class OpenCodeConfigCliTests(unittest.TestCase):
    def test_pinned_python_relaunch_preserves_argv_environment_and_exit_code(self):
        main = Path(r"C:\Users\William\Documents\Minecraft Mod Dev Files\MoonStation14")
        shared_python = main.parent / "MoonStation14-shared/.venv-workflow-coordinator/Scripts/python.exe"
        script = main / "tooling/opencode/config.py"
        original_argv = ["config.py", "check", "value with spaces"]
        with mock.patch.object(cli, "__file__", str(script)), \
             mock.patch.object(cli.sys, "prefix", r"C:\Other Python"), \
             mock.patch.object(cli.sys, "argv", original_argv), \
             mock.patch.dict(os.environ, {"KEEP_ME": "yes"}, clear=True), \
             mock.patch.object(cli.subprocess, "run", return_value=subprocess.CompletedProcess([], 7)) as run:
            with self.assertRaises(SystemExit) as exit_error:
                cli._pinned_python(main)
        self.assertEqual(exit_error.exception.code, 7)
        args, kwargs = run.call_args
        self.assertEqual(args[0], [str(shared_python), str(script.resolve()), "check", "value with spaces"])
        self.assertEqual(kwargs["env"], {"KEEP_ME": "yes", "PYTHONPATH": str(main / "tooling/agent_workflow")})

    def test_pinned_python_does_not_relaunch_when_already_pinned(self):
        main = Path(r"C:\Users\William\Documents\Minecraft Mod Dev Files\MoonStation14")
        shared_python = main.parent / "MoonStation14-shared/.venv-workflow-coordinator/Scripts/python.exe"
        with mock.patch.object(cli.sys, "prefix", str(shared_python.parent.parent)), \
             mock.patch.object(cli.subprocess, "run") as run:
            self.assertEqual(cli._pinned_python(main), shared_python)
        run.assert_not_called()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.main = self.base / "MoonStation14"
        self.main.mkdir()
        (self.main / "tooling/opencode").mkdir(parents=True)
        template_source = CLI_PATH.parent / "shared-config.jsonc"
        (self.main / "tooling/opencode/shared-config.jsonc").write_bytes(template_source.read_bytes())
        import sys
        sys.path.insert(0, str(CLI_PATH.parents[1] / "agent_workflow"))
        self.addCleanup(lambda: sys.path.remove(str(CLI_PATH.parents[1] / "agent_workflow")))
        import workflow_opencode_config as oc
        self.oc = oc
        self.template_bytes = template_source.read_bytes()
        self.template = oc.load_template(self.template_bytes)
        self.variables = {"shared_root": (self.base / "shared").as_posix(),
                          "workspace_parent": self.base.as_posix(),
                          "ss14_root": (self.base / "ss14").as_posix()}
        self.roots = [self.main]
        for i in range(1, 5):
            root = self.base / f"MoonStation14-agent-0{i}"
            root.mkdir()
            self.roots.append(root)
        self.files = []
        for root in self.roots:
            (root / ".opencode/workflow").mkdir(parents=True)
            (root / ".gitignore").write_text("/opencode.jsonc\n/.opencode/workflow/\n")
            config = self.oc.render_config(self.template, {"version": 1, "variables": self.variables, "config": {}})
            (root / "opencode.jsonc").write_text(self.oc.serialize_config(config))
            subprocess.run(["git", "-C", str(root), "init", "-q"], check=True)
            self.files.append({"target": "main" if root == self.main else root.name,
                               "path": "opencode.jsonc", "new_sha256": hashlib.sha256((root / "opencode.jsonc").read_bytes()).hexdigest(),
                               "config_scope": {"shared_template": {"path": str((self.main / "tooling/opencode/shared-config.jsonc").resolve()),
                                                                     "sha256": hashlib.sha256(self.template_bytes).hexdigest()}},
                               "deleted": False})
        self.receipt = self.base / "receipt.json"
        self._write_receipt()
        self._write_pointer()

    def _write_receipt(self):
        self.receipt.write_text(json.dumps({"files": self.files, "source_hashes": {}}))

    def _write_pointer(self):
        (self.main / ".opencode/workflow").mkdir(parents=True, exist_ok=True)
        (self.main / ".opencode/workflow/publication.json").write_text(json.dumps({"receipt": str(self.receipt)}))
        inst = {"receipt": str(self.receipt), "receipt_sha256": hashlib.sha256(self.receipt.read_bytes()).hexdigest(),
                "config": str(self.base / "service-config.json"), "health_file": str(self.base / "health.json"), "port": 59183}
        (self.main / ".opencode/workflow/current-installation.json").write_text(json.dumps(inst))

    def test_status_recognizes_init_and_detects_tracked_root_config(self):
        self.assertEqual(cli._status(self.main, initialize=True)[0], "CURRENT")
        root = self.main
        subprocess.run(["git", "-C", str(root), "add", "-f", "opencode.jsonc"], check=True)
        self.assertIn("main", cli._status(self.main, initialize=True)[1])

    def test_status_detects_overlay_tracked_or_not_ignored(self):
        root = self.roots[0]
        overlay = {"version": 1, "variables": self.variables, "config": {}}
        for target_root in self.roots:
            path = target_root / ".opencode/workflow/opencode-local.json"
            path.write_text(self.oc.serialize_config(overlay))
            target = "main" if target_root == self.main else target_root.name
            self.files.append({"target": target, "path": ".opencode/workflow/opencode-local.json",
                               "new_sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "deleted": False})
        self._write_receipt(); self._write_pointer()
        subprocess.run(["git", "-C", str(root), "add", "-f", ".opencode/workflow/opencode-local.json"], check=True)
        status, changed = cli._status(self.main, initialize=False)
        self.assertEqual(status, "STALE")
        self.assertIn("main", changed)

    def test_delegate_uses_installation_authority_and_keeps_review_inputs(self):
        (self.base / "service-config.json").write_text("{}")
        (self.base / "health.json").write_text("{}")
        self._write_pointer()
        calls = []
        module = types.SimpleNamespace(run=lambda args: (calls.append(args) or {"status": "READY"}) if args.preflight else
                                       (calls.append(args) or {"status": "DONE"}))
        old = os.environ.get("LOCALAPPDATA")
        os.environ["LOCALAPPDATA"] = str(self.base)
        self.addCleanup(lambda: os.environ.__setitem__("LOCALAPPDATA", old) if old is not None else os.environ.pop("LOCALAPPDATA", None))
        with mock.patch.dict("sys.modules", {"deploy_workflow": module}):
            result = cli._delegate(self.main, {"test": "plan"}, self.base / "python.exe")
        self.assertEqual(result["status"], "DONE")
        self.assertEqual([c.port for c in calls], [59183, 59183])
        self.assertEqual(calls[0].config, self.base / "service-config.json")
        self.assertEqual(calls[0].health_file, self.base / "health.json")
        self.assertTrue(calls[0].binding_plan.is_file())
        reviewed = json.loads(calls[0].reviewed_source_hashes.read_text())
        self.assertEqual(set(reviewed), {"tooling/opencode/shared-config.jsonc"})
        self.assertEqual(reviewed["tooling/opencode/shared-config.jsonc"], hashlib.sha256(self.template_bytes).hexdigest())
        self.assertTrue(calls[0].binding_plan.parent.exists())
        self.assertEqual(calls[0].binding_plan.parent.parent.name, "opencode")
        self.assertEqual(calls[0].python, self.base / "python.exe")

    def test_current_apply_is_noop_without_owner_invocation(self):
        with mock.patch.object(cli, "_main_root", return_value=self.main), \
             mock.patch.object(cli, "_pinned_python", return_value=self.base / "python.exe"), \
             mock.patch.object(cli, "_plan", return_value={}), \
             mock.patch.object(cli, "_status", return_value=("CURRENT", [])), \
             mock.patch.object(cli, "_delegate") as delegate:
            self.assertEqual(cli.main(["apply"]), 0)
            delegate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
