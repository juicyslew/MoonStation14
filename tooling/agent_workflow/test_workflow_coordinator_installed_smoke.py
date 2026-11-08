"""Read-only import, construction, and catalog smoke test against staged runtime bytes."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import workflow_coordinator_package as package


class InstalledCoordinatorSmokeTests(unittest.TestCase):
    def test_staged_import_construction_and_both_catalogs_without_starting(self):
        temp_root = Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode"
        with tempfile.TemporaryDirectory(dir=temp_root) as directory:
            base = Path(directory)
            shared = base / "shared"
            shared.mkdir()
            receipt_path = base / "receipt.json"
            receipt_path.write_text(json.dumps({"files": [], "source_hashes": {}, "shared_root": str(shared)}), encoding="utf-8")
            staged = base / "staged-root"
            manifest = package.build(staged, receipt_path)
            runtime_source = package.ROOT / "tooling/agent_workflow/workflow_runtime.py"
            runtime_source_hash = package.digest(runtime_source)
            self.assertEqual(runtime_source_hash, manifest["files"]["tooling/agent_workflow/workflow_runtime.py"]["source_sha256"])
            repo = base / "private-worktree"
            repo.mkdir()
            (repo / "author.token").write_text("private-author-token", encoding="utf-8")
            (repo / "reviewer.token").write_text("private-review-token", encoding="utf-8")
            publication_receipt = base / "publication.json"
            publication_receipt.write_text("{}", encoding="utf-8")
            context = {
                "repo": str(repo), "authenticated_author": {"session_id": "author-session", "worktree": str(repo), "role": "author"},
                "policy": {}, "artifacts": [], "publication_receipt_path": str(publication_receipt),
                "publication_receipt_sha256": "a" * 64,
            }
            (repo / "author-context.json").write_text(json.dumps(context), encoding="utf-8")
            config = {
                "database": str(base / "private.sqlite"), "artifact_dir": str(base / "artifacts"),
                "opencode_server": "http://127.0.0.1:49100",
                "bindings": [{"token_file": str(repo / "author.token"), "caller": "author", "context_file": str(repo / "author-context.json")}],
                "reviewers": {"fidelity": {"worktree": str(repo), "agent": "workflow-reviewer-fidelity", "token_file": str(repo / "reviewer.token"), "caller": "reviewer"}},
            }
            config_path = base / "coordinator.json"
            config_path.write_text(json.dumps(config), encoding="utf-8")
            script = r'''
import hashlib, json, sys
from pathlib import Path
root = Path(sys.argv[1]); sys.path.insert(0, str(root / "tooling/agent_workflow"))
import workflow_coordinator, workflow_coordinator_mcp, workflow_runtime
import workflow_supervisor, workflow_store, workflow_preflight, workflow_native_worker
assert Path(workflow_runtime.__file__).resolve() == (root / "tooling/agent_workflow/workflow_runtime.py").resolve()
assert "workflow_observer" not in sys.modules
coordinator = workflow_coordinator.load_coordinator(sys.argv[2])
assert coordinator.store is not None and coordinator._thread is None
assert set(tool["name"] for tool in workflow_coordinator_mcp.CoordinatorClient("http://127.0.0.1:49100", "token", ".", profile="author").tools()) == {"submit", "status", "acknowledge_result", "register_scope", "scope_status"}
assert {x["name"] for x in workflow_coordinator_mcp.CoordinatorClient("http://127.0.0.1:49100", "token", ".", profile="reviewer").tools()} == {"claim_task", "complete_review", "report_error", "status"}
assert "workflow_observer" not in sys.modules
coordinator.store.close()
print("staged runtime constructed; canonical imports and both SDK catalogs verified; no observer/API start")
'''
            result = subprocess.run([sys.executable, "-I", "-c", script, str(staged), str(config_path)],
                                    cwd=base, capture_output=True, text=True, timeout=60)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn("both SDK catalogs verified", result.stdout)
            self.assertEqual(runtime_source_hash, package.digest(runtime_source), "canonical runtime source changed during staged smoke")
            self.assertEqual(runtime_source_hash, package.digest(staged / "tooling/agent_workflow/workflow_runtime.py"))


if __name__ == "__main__":
    unittest.main()
