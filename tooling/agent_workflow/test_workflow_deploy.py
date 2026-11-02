import json
import sqlite3
import tempfile
import unittest
import hashlib
import os
import subprocess
from unittest import mock
from contextlib import ExitStack
from pathlib import Path

try:
    import deploy_workflow as deploy
except ModuleNotFoundError:
    from tooling.agent_workflow import deploy_workflow as deploy


class DeployWorkflowTests(unittest.TestCase):
    def test_shared_config_index_transition_five_isolated_git_roots(self):
        import workflow_coordinator_bindings as bindings
        names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); roots = {}; targets = []; receipt = {"files": []}
            for name in names:
                root = base / name; root.mkdir(); roots[name] = root
                def git(*args):
                    return subprocess.run(["git", "-C", str(root), *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
                git("init", "-q"); git("config", "user.email", "test@example.invalid"); git("config", "user.name", "Fixture")
                (root / "opencode.jsonc").write_bytes(b'{"generated":true}\n')
                (root / ".gitignore").write_text("/opencode.jsonc\n/.opencode/workflow/\n")
                (root / ".opencode/workflow").mkdir(parents=True); (root / ".opencode/workflow/opencode-local.json").write_text("{}\n")
                (root / "other.txt").write_text("base\n"); git("add", "."); git("commit", "-qm", "base")
                (root / "other.txt").write_text("staged\n"); git("add", "other.txt"); (root / "other.txt").write_text("working\n")
                targets.append({"target": name, "expected_index_digest": bindings._index_digest(root)})
                for path in ("opencode.jsonc", ".opencode/workflow/opencode-local.json", ".gitignore"):
                    data = (root / path).read_bytes()
                    receipt["files"].append({"target": name, "path": path, "new_sha256": hashlib.sha256(data).hexdigest()})
            stage = base / "stage"; stage.mkdir()
            deploy.finalize_shared_config_index_transition(roots, {"targets": targets}, receipt, stage)
            for root in roots.values():
                git = lambda *args: subprocess.run(["git", "-C", str(root), *args], check=True, stdout=subprocess.PIPE).stdout
                self.assertFalse(git("ls-files", "--", "opencode.jsonc").strip())
                self.assertTrue((root / "opencode.jsonc").is_file())
                self.assertIn(b"opencode.jsonc", git("check-ignore", "-v", "--", "opencode.jsonc"))
                self.assertEqual(b"staged\n", git("show", ":other.txt"))
                self.assertEqual("working\n", (root / "other.txt").read_text())

    def test_shared_config_index_transition_rejects_digest_drift_before_any_mutation(self):
        import workflow_coordinator_bindings as bindings
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
            roots = {}; targets = []
            for name in names:
                repo = root / name; repo.mkdir(); roots[name] = repo
                subprocess.run(["git", "-C", str(repo), "init", "-q"], check=True)
                (repo / "opencode.jsonc").write_text("{}\n")
                subprocess.run(["git", "-C", str(repo), "add", "opencode.jsonc"], check=True)
                targets.append({"target": name, "expected_index_digest": bindings._index_digest(repo)})
            original = bindings._index_digest(roots["main"])
            targets[0]["expected_index_digest"] = "0" * 64
            with self.assertRaises(ValueError):
                deploy.finalize_shared_config_index_transition(roots, {"targets": targets}, {"files": []}, root / "stage")
            self.assertEqual(original, bindings._index_digest(roots["main"]))

    def test_shared_config_prechecks_all_roots_before_first_index_mutation(self):
        import workflow_coordinator_bindings as bindings
        names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); roots = {}; targets = []; receipt = {"files": []}
            for name in names:
                root = base / name; root.mkdir(); roots[name] = root
                def git(*args): return subprocess.run(["git", "-C", str(root), *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
                git("init", "-q"); git("config", "user.email", "test@example.invalid"); git("config", "user.name", "Fixture")
                (root / "opencode.jsonc").write_text("{}\n"); (root / ".gitignore").write_text("/opencode.jsonc\n/.opencode/workflow/\n")
                (root / ".opencode/workflow").mkdir(parents=True); (root / ".opencode/workflow/opencode-local.json").write_text("{}\n")
                git("add", "."); git("commit", "-qm", "base")
                targets.append({"target": name, "expected_index_digest": bindings._index_digest(root)})
                for path in ("opencode.jsonc", ".opencode/workflow/opencode-local.json", ".gitignore"):
                    receipt["files"].append({"target": name, "path": path, "new_sha256": hashlib.sha256((root / path).read_bytes()).hexdigest()})
            initial = {n: bindings._index_digest(r) for n, r in roots.items()}
            # Late target drift must be detected before any earlier target is removed.
            (roots[names[-1]] / "other").write_text("drift")
            subprocess.run(["git", "-C", str(roots[names[-1]]), "add", "other"], check=True)
            with self.assertRaisesRegex(ValueError, "index drift"):
                deploy.finalize_shared_config_index_transition(roots, {"targets": targets}, receipt, base / "stage")
            for name in names[:-1]: self.assertEqual(initial[name], bindings._index_digest(roots[name]))

    def test_shared_config_changed_materialization_rejects_before_index_mutation(self):
        # A receipt-bound file edit is rejected before any index operation.
        import workflow_coordinator_bindings as bindings
        names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
        with tempfile.TemporaryDirectory() as td:
            base = Path(td); roots = {}; targets = []; receipt = {"files": []}
            for name in names:
                root = base / name; root.mkdir(); roots[name] = root
                subprocess.run(["git", "-C", str(root), "init", "-q"], check=True)
                (root / "opencode.jsonc").write_text("{}\n"); (root / ".gitignore").write_text("/opencode.jsonc\n/.opencode/workflow/\n")
                (root / ".opencode/workflow").mkdir(parents=True); (root / ".opencode/workflow/opencode-local.json").write_text("{}\n")
                subprocess.run(["git", "-C", str(root), "add", "."], check=True)
                targets.append({"target": name, "expected_index_digest": bindings._index_digest(root)})
                for path in ("opencode.jsonc", ".opencode/workflow/opencode-local.json", ".gitignore"):
                    receipt["files"].append({"target": name, "path": path, "new_sha256": hashlib.sha256((root / path).read_bytes()).hexdigest()})
            before = {n: bindings._index_digest(r) for n, r in roots.items()}
            (roots[names[-1]] / "opencode.jsonc").write_text("changed\n")
            with self.assertRaisesRegex(ValueError, "materialization differs"):
                deploy.finalize_shared_config_index_transition(roots, {"targets": targets}, receipt, base / "stage")
            self.assertEqual(before, {n: bindings._index_digest(r) for n, r in roots.items()})

    def test_shared_config_index_conflict_prechecks_staged_and_unmerged_entries(self):
        import workflow_coordinator_bindings as bindings
        names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
        for conflict in ("staged", "unmerged"):
            with self.subTest(conflict=conflict), tempfile.TemporaryDirectory() as td:
                base = Path(td); roots = {}; targets = []; receipt = {"files": []}
                for name in names:
                    root = base / name; root.mkdir(); roots[name] = root
                    def git(*args): return subprocess.run(["git", "-C", str(root), *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
                    git("init", "-q"); git("config", "user.email", "test@example.invalid"); git("config", "user.name", "Fixture")
                    (root / "opencode.jsonc").write_text("{}\n"); (root / ".gitignore").write_text("/opencode.jsonc\n/.opencode/workflow/\n")
                    (root / ".opencode/workflow").mkdir(parents=True); (root / ".opencode/workflow/opencode-local.json").write_text("{}\n")
                    git("add", "."); git("add", "-f", "opencode.jsonc"); git("commit", "-qm", "base")
                    targets.append({"target": name, "expected_index_digest": bindings._index_digest(root)})
                    for path in ("opencode.jsonc", ".opencode/workflow/opencode-local.json", ".gitignore"):
                        receipt["files"].append({"target": name, "path": path, "new_sha256": hashlib.sha256((root / path).read_bytes()).hexdigest()})
                bad = roots[names[-1]]
                if conflict == "staged":
                    (bad / "opencode.jsonc").write_text('{"staged":true}\n'); subprocess.run(["git", "-C", str(bad), "add", "-f", "opencode.jsonc"], check=True)
                    targets[-1]["expected_index_digest"] = bindings._index_digest(bad)
                else:
                    blob = subprocess.run(["git", "-C", str(bad), "hash-object", "-w", "--stdin"], input=b"conflict", check=True, stdout=subprocess.PIPE).stdout.strip().decode()
                    info = f"100644 {blob} 1\topencode.jsonc\n100644 {blob} 2\topencode.jsonc\n100644 {blob} 3\topencode.jsonc\n"
                    subprocess.run(["git", "-C", str(bad), "update-index", "--index-info"], input=info.encode(), check=True)
                    targets[-1]["expected_index_digest"] = bindings._index_digest(bad)
                before = {n: bindings._index_digest(r) for n, r in roots.items()}
                with self.assertRaises(ValueError):
                    deploy.finalize_shared_config_index_transition(roots, {"targets": targets}, receipt, base / "stage")
                self.assertEqual(before, {n: bindings._index_digest(r) for n, r in roots.items()})

    def test_shared_config_rechecks_overlay_and_ignore_immediately_before_removal(self):
        import workflow_coordinator_bindings as bindings
        names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
        for changed in ("overlay", "ignore"):
            with self.subTest(changed=changed), tempfile.TemporaryDirectory() as td:
                base = Path(td); roots = {}; targets = []; receipt = {"files": []}
                for name in names:
                    root = base / name; root.mkdir(); roots[name] = root
                    def git(*args): return subprocess.run(["git", "-C", str(root), *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
                    git("init", "-q"); git("config", "user.email", "test@example.invalid"); git("config", "user.name", "Fixture")
                    (root / "opencode.jsonc").write_text("{}\n"); (root / ".gitignore").write_text("/opencode.jsonc\n/.opencode/workflow/\n")
                    (root / ".opencode/workflow").mkdir(parents=True); (root / ".opencode/workflow/opencode-local.json").write_text("{}\n")
                    git("add", "."); git("add", "-f", "opencode.jsonc"); git("commit", "-qm", "base")
                    targets.append({"target": name, "expected_index_digest": bindings._index_digest(root)})
                    for path in ("opencode.jsonc", ".opencode/workflow/opencode-local.json", ".gitignore"):
                        receipt["files"].append({"target": name, "path": path, "new_sha256": hashlib.sha256((root / path).read_bytes()).hexdigest()})
                first = "MoonStation14-agent-01"
                before = {n: bindings._index_digest(r) for n, r in roots.items()}
                original_writer = deploy.package._atomic_write
                mutated = False
                def journal_then_mutate(path, data, *args, **kwargs):
                    nonlocal mutated
                    result = original_writer(path, data, *args, **kwargs)
                    if not mutated:
                        mutated = True
                        member = ".opencode/workflow/opencode-local.json" if changed == "overlay" else ".gitignore"
                        with (roots[first] / member).open("a", encoding="utf-8") as stream: stream.write("# concurrent change\n")
                    return result
                with mock.patch.object(deploy.package, "_atomic_write", side_effect=journal_then_mutate):
                    with self.assertRaisesRegex(ValueError, "overlay or ignore rules changed"):
                        deploy.finalize_shared_config_index_transition(roots, {"targets": targets}, receipt, base / "stage")
                self.assertTrue(mutated)
                self.assertEqual(before, {n: bindings._index_digest(r) for n, r in roots.items()})

    def test_shared_config_finalizer_failure_keeps_deployer_hold(self):
        with tempfile.TemporaryDirectory() as td:
            calls = []
            args, stack = self.run_fixture(td, calls=calls)
            names = ["main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))]
            plan = {"version": 1, "mode": "shared-opencode-config", "template": {
                "path": str(Path(td) / "main/tooling/opencode/shared-config.jsonc"), "sha256": "a" * 64},
                "initialize": True, "untrack_generated": True,
                "targets": [{"target": n, "expected_config_sha256": "a" * 64,
                    "expected_local_sha256": None, "expected_gitignore_sha256": "b" * 64,
                    "expected_index_digest": "c" * 64} for n in names]}
            plan_path = Path(td) / "shared-plan.json"; plan_path.write_text(json.dumps(plan))
            args.binding_plan = plan_path; args.binding_plan_sha256 = deploy.sha256(plan_path.read_bytes())
            with stack, mock.patch.object(deploy, "finalize_shared_config_index_transition", side_effect=ValueError("fixture failure")), \
                    mock.patch("admission.resume_hold") as resume:
                with self.assertRaisesRegex(ValueError, "fixture failure"):
                    deploy.run(args)
            resume.assert_not_called()
            failure = json.loads((Path(td) / "main/.opencode/workflow/current-failure.json").read_text())
            self.assertEqual("index_transition", failure["phase"])

    def test_reviewed_endpoint_is_strict_loopback_http(self):
        self.assertEqual("http://127.0.0.1:45278", deploy._reviewed_loopback_endpoint("http://127.0.0.1:45278"))
        for value in ("https://127.0.0.1:45278", "http://example.com:45278", "http://127.0.0.1",
                      "http://user:pass@127.0.0.1:45278", "http://127.0.0.1:45278/path"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                deploy._reviewed_loopback_endpoint(value)

    def store(self, root):
        try:
            from workflow_store import WorkflowStore
        except ModuleNotFoundError:
            from tooling.agent_workflow.workflow_store import WorkflowStore
        db = Path(root) / "workflow.sqlite"
        WorkflowStore(db)
        return db

    def run_fixture(self, root, *, atomic=None, stop=None, barrier_exit=None, calls=None, api_health=None):
        from types import SimpleNamespace
        root = Path(root); db = self.store(root); main = root / "main"; main.mkdir()
        private = root / "private"; private.mkdir(); service_root = root / "home/.local/state/opencode"
        service_root.mkdir(parents=True); (service_root / "service.json").write_text(json.dumps({"url": "http://127.0.0.1:49374", "password": "secret"}))
        token = private / "token"; token.write_text("opaque")
        context = private / "context.json"
        receipt = private / "receipt.json"
        receipt.write_text(json.dumps({"main_root": str(main), "shared_root": str(root / "shared"),
            "stage": str(private), "manifest_sha256": "a" * 64, "publication_pointer": {"backup_sha256": ""}}))
        receipt_sha = deploy.sha256(receipt.read_bytes())
        pointer = main / deploy.package.POINTER_RELATIVE; pointer.parent.mkdir(parents=True)
        pointer.write_text(json.dumps({"receipt": str(receipt)}))
        pointer_sha = deploy.sha256(pointer.read_bytes())
        config = private / "config.json"
        config.write_text(json.dumps({"database": str(db), "admission_root": str(root / "admission"),
            "opencode_server": "http://127.0.0.1:49374", "bindings": [],
            "author_worktrees": [{"token_file": str(token), "context_file": str(context)}]}))
        context.write_text(json.dumps({"publication_receipt_path": str(receipt.resolve()), "publication_receipt_sha256": receipt_sha}))
        health_file = private / "health.json"; health_file.write_text("{}")
        hashes = private / "hashes.json"; hashes.write_text("{}")
        args = SimpleNamespace(config=config, health_file=health_file, receipt=receipt,
            receipt_sha256=receipt_sha, expected_pointer_sha256=pointer_sha,
            reviewed_source_hashes=hashes, python=root / "python.exe", port=59183,
            preflight=False, execute=True)
        class Admission:
            def begin_hold(self, *a, **kw): return {"path": "private", "token": "secret", "sha256": "a", "owner": kw.get("owner", "x"), "control_id": "x"}
            def resume_hold(self, *a): calls.append("resume") if calls is not None else None
            def maintenance(self, *a): return None
        class Barrier:
            def __init__(self, *a): self.initial = {"status": "OBSERVED_ZERO"}; self._hold = None; self._locks = []; self._ownership_uncertain = False
            def __enter__(self): self._hold = object(); self._locks = [object()]; return self
            def __exit__(self, *a):
                if calls is not None: calls.append("barrier_exit")
                self._hold = None; self._locks = []
                if barrier_exit: self._ownership_uncertain = True; raise RuntimeError("cleanup uncertain")
        class Response:
            status = 200
            def __enter__(self): return self
            def __exit__(self, *a): pass
            def read(self, n): return json.dumps(api_health or {"status":"running","pending_events":0,"orchestration_paused_for_maintenance":True}).encode()
        stack = ExitStack()
        stack.enter_context(mock.patch.dict("os.environ", {"LOCALAPPDATA": str(root / "localappdata")}))
        modules = __import__("sys").modules
        import sys
        sys.path.insert(0, str(Path(deploy.__file__).resolve().parent))
        import admission, workflow_cutover, workflow_cutover_transition, gate_execution
        import gate_dispatch
        modules.update(admission=admission, workflow_cutover=workflow_cutover,
                       workflow_cutover_transition=workflow_cutover_transition, gate_execution=gate_execution,
                       gate_dispatch=gate_dispatch)
        stack.enter_context(mock.patch.object(gate_dispatch, "_check_v2"))
        admission = modules["admission"]
        cutover = modules["workflow_cutover"]
        transition = modules["workflow_cutover_transition"]
        stack.enter_context(mock.patch.object(Path, "home", return_value=root / "home"))
        stack.enter_context(mock.patch.object(admission, "begin_hold", side_effect=Admission().begin_hold))
        stack.enter_context(mock.patch.object(admission, "resume_hold", side_effect=Admission().resume_hold))
        stack.enter_context(mock.patch.object(admission, "maintenance", return_value=None))
        stack.enter_context(mock.patch.object(cutover, "DrainBarrier", Barrier))
        stack.enter_context(mock.patch.object(deploy, "_validate_current_host"))
        stack.enter_context(mock.patch.object(deploy, "deployment_snapshot", wraps=deploy.deployment_snapshot))
        stack.enter_context(mock.patch.object(deploy.package, "build", return_value={"files": {}}))
        def publish(*a, **kw):
            if calls is not None: calls.append("publish")
            out = a[2]; out.write_text('{"status":"applied"}')
            return {"status": "applied"}
        stack.enter_context(mock.patch.object(deploy.package, "publish", side_effect=publish))
        stack.enter_context(mock.patch.object(transition, "_refresh_author_contexts", side_effect=lambda *a: calls.append("refresh") if calls is not None else None))
        stack.enter_context(mock.patch.object(deploy, "stop_host", side_effect=stop or (lambda *a: calls.append("stop") if calls is not None else None)))
        started_health = {"child": {"scope_name": "job"}}
        stack.enter_context(mock.patch.object(deploy, "start_host", side_effect=lambda *a: (calls.append("start") if calls is not None else None) or {"identities": [], "health": started_health}))
        stack.enter_context(mock.patch.object(deploy.urllib.request, "build_opener", return_value=SimpleNamespace(open=lambda *a, **k: Response())))
        if atomic is not None: stack.enter_context(mock.patch.object(deploy.package, "_atomic_write", side_effect=atomic))
        return args, stack

    def test_pointer_bytes_match_publisher_canonical_format(self):
        with tempfile.TemporaryDirectory() as root:
            receipt = Path(root) / "receipt.json"
            old = json.dumps({"receipt": "old", "scope": "old"}).encode()
            result = deploy.canonical_pointer_bytes(old, receipt, "a" * 64)
            value = json.loads(result)
            self.assertEqual(value["previous_receipt"], "old")
            self.assertEqual(value["receipt"], str(receipt.resolve()))
            self.assertEqual(result, (json.dumps(value, indent=2, sort_keys=True) + "\n").encode())

    def test_pointer_hash_mismatch_refuses(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / "pointer.json"
            receipt = Path(root) / "receipt.json"
            path.write_text(json.dumps({"receipt": str(receipt)}))
            with self.assertRaisesRegex(ValueError, "hash"):
                deploy.verify_pointer(path, "0" * 64, receipt)

    def test_readonly_snapshot_uses_real_store_schema_and_idle_is_quiescent(self):
        with tempfile.TemporaryDirectory() as root:
            snapshot = deploy.deployment_snapshot(self.store(root))
            self.assertIn("attempts", snapshot)
            deploy.assert_quiescent(snapshot)

    def test_queued_review_repair_requires_complete_proof_snapshot(self):
        snapshot = self.queued_review_snapshot()
        deploy.assert_queued_review_repair(snapshot, "run", expected_server="http://127.0.0.1:49374",
                                           identity_state=lambda _: "dead")

    def queued_review_snapshot(self):
        try: import workflow_store
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_store
        snapshot = {key: [] for key in deploy._SNAPSHOT_TABLES}
        snapshot.update({key: [] for key in ("runs", "dependency_circuits", "native_claims", "artifacts",
                                               "task_dependency_pauses", "task_cancellations")})
        snapshot["runs"] = [{"id":"run", "run_json":{"current_attempt":1}, "current_generation":2},
                            {"id":"other", "run_json":{"current_attempt":1}, "current_generation":1}]
        snapshot["attempts"] = [{"run_id":"run", "generation":2, "attempt_json":{"phase":"REVIEWING"}},
                                {"run_id":"other", "generation":1, "attempt_json":{"phase":"QUEUED"}}]
        snapshot["tasks"] = [
            {"id":"check-pass", "task_json":{"run_id":"run","evaluation_generation":2,"kind":"CHECK","state":"COMPLETED","result":{"decision":"PASS"}}},
            {"id":"review-1", "task_json":{"run_id":"run","evaluation_generation":2,"kind":"REVIEW","state":"QUEUED","assignment_generation":1,"input_digest":"a"}},
            {"id":"review-2", "task_json":{"run_id":"run","evaluation_generation":2,"kind":"REVIEW","state":"QUEUED","assignment_generation":1,"input_digest":"b"}},
            {"id":"review-3", "task_json":{"run_id":"run","evaluation_generation":2,"kind":"REVIEW","state":"QUEUED","assignment_generation":1,"input_digest":"c"}},
            {"id":"check-next", "task_json":{"run_id":"other","evaluation_generation":1,"kind":"CHECK","state":"QUEUED","assignment_generation":1,"input_digest":"d"}},
        ]
        snapshot["attempts"].append({"run_id":"other","generation":1,"attempt_json":{"phase":"QUEUED"}})
        snapshot["resources"]=[{"state":"RELEASED"}]
        snapshot["native_scopes"]=[{"state":"CLEANED"}]
        snapshot["native_claims"]=[{"process_identity":{"pid":1}}]
        snapshot["dependency_circuits"]=[{"dependency_key":"LOCAL_API:http://127.0.0.1:49374","state":"BACKOFF"}]
        payload={"task_id":"check-next","kind":"TASK_READY","event_id":"event-check","run_id":"other",
                 "evaluation_generation":1,"assignment_generation":1,"input_digest":"d"}
        snapshot["events"]=[{"id":"event-check","kind":"TASK_READY","run_id":"other","generation":1,
                              "payload":payload,"payload_digest":workflow_store.digest(payload)}]
        snapshot["outbox"]=[{"event_id":"event-check","state":"PENDING"}]
        return snapshot

    def test_queued_review_repair_rejects_unsafe_proof_mutations(self):
        mutations = (
            lambda s: s["tasks"][1]["task_json"].update(state="CLAIMED"),
            lambda s: s["resources"][0].update(state="HELD"),
            lambda s: s["dependency_circuits"][0].update(state="CLOSED"),
            lambda s: s["attempts"][0]["attempt_json"].update(phase="CHECKING"),
            lambda s: s["runs"][0].update(current_generation=3),
            lambda s: s["artifacts"].append({"artifact_json":{"artifact":{"producer_task_id":"review-1"}}}),
            lambda s: s["deliveries"].append({"target_kind":"AUTHOR","state":"PENDING","protocol_ack":None}),
            lambda s: s["native_claims"][0].update(process_identity={"pid":2}),
            lambda s: s["events"][0].update(payload_digest="0"*64),
            lambda s: s["events"][0]["payload"].update(input_digest="wrong"),
        )
        for mutate in mutations:
            snapshot=self.queued_review_snapshot(); mutate(snapshot)
            with self.subTest(mutation=mutate), self.assertRaises(ValueError):
                deploy.assert_queued_review_repair(snapshot,"run",expected_server="http://127.0.0.1:49374",
                                                   identity_state=lambda identity: "dead" if identity.get("pid")==1 else "alive")

    def test_queued_review_repair_rejects_incomplete_snapshot(self):
        snapshot = {key: [] for key in deploy._SNAPSHOT_TABLES}
        with self.assertRaisesRegex(ValueError, "proof is unavailable"):
            deploy.assert_queued_review_repair(snapshot, "run")

    def test_quiescence_rejects_unsettled_rows(self):
        with tempfile.TemporaryDirectory() as root:
            snapshot = deploy.deployment_snapshot(self.store(root))
        for key, row in (("tasks", {"task_json": {"state": "QUEUED"}}),
                         ("attempts", {"attempt_json": {"phase": "CHECKING"}}),
                         ("outbox", {"state": "PENDING"}),
                         ("deliveries", {"target_kind": "AUTHOR", "state": "PENDING", "protocol_ack": None}),
                         ("native_scopes", {"state": "OWNED"}), ("resources", {"state": "HELD"})):
            fixture = dict(snapshot)
            fixture[key] = [row]
            with self.subTest(key=key), self.assertRaises(ValueError):
                deploy.assert_quiescent(fixture)

    def cancelled_ready_snapshot(self):
        try: import workflow_store
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_store
        snapshot = {key: [] for key in deploy._SNAPSHOT_TABLES}
        snapshot["task_cancellations"] = []
        for i in range(3):
            task_id, event_id = f"review-{i}", f"ready-{i}"
            task = {"task_id":task_id,"run_id":"run","evaluation_generation":4,
                    "assignment_generation":2,"input_digest":f"digest-{i}","kind":"REVIEW","state":"CANCELLED"}
            payload = {"event_id":event_id,"task_id":task_id,"run_id":"run","kind":"TASK_READY",
                       "evaluation_generation":4,"assignment_generation":2,"input_digest":f"digest-{i}"}
            snapshot["tasks"].append({"id":task_id,"task_json":task})
            snapshot["events"].append({"id":event_id,"run_id":"run","generation":4,"kind":"TASK_READY",
                "payload":json.dumps(payload),"payload_digest":workflow_store.digest(payload)})
            snapshot["outbox"].append({"event_id":event_id,"state":"PENDING"})
            snapshot["task_cancellations"].append({"task_id":task_id,"confirmed":1,"proof_json":json.dumps({
                "task_id":task_id,"run_id":"run","generation":4,"assignment_generation":2,
                "input_digest":f"digest-{i}","kind":"DB_NEVER_STARTED"})})
            snapshot["resources"].append({"task_id":task_id,"state":"RELEASED"})
        return snapshot

    def test_quiescence_accepts_only_verified_cancelled_review_readiness(self):
        snapshot = self.cancelled_ready_snapshot()
        deploy.assert_quiescent(snapshot)
        mutations = (
            lambda s: s["events"][0].update(payload_digest="0"*64),
            lambda s: s["events"][0].update(payload=json.dumps(dict(json.loads(s["events"][0]["payload"]),input_digest="wrong"))),
            lambda s: s["events"][0].update(generation=5),
            lambda s: s["task_cancellations"][0].update(confirmed=0),
            lambda s: s["task_cancellations"][0].update(proof_json='{}'),
            lambda s: s["task_cancellations"][0].update(proof_json=json.dumps({"kind":"OTHER"})),
            lambda s: s["tasks"][0]["task_json"].update(state="QUEUED"),
            lambda s: s["resources"][0].update(state="HELD"),
            lambda s: s["outbox"].append({"event_id":"unrelated","state":"PENDING"}),
            lambda s: s["deliveries"].append({"target_kind":"AUTHOR","state":"PENDING","protocol_ack":None}),
        )
        for mutate in mutations:
            bad = self.cancelled_ready_snapshot()
            mutate(bad)
            with self.subTest(mutation=mutate), self.assertRaises(ValueError): deploy.assert_quiescent(bad)

    def test_fingerprint_tracks_row_content_not_just_count(self):
        with tempfile.TemporaryDirectory() as root:
            db = self.store(root)
            conn = sqlite3.connect(db)
            conn.execute("INSERT INTO dependency_circuits(dependency_key,generation,state,details_json) VALUES('x',1,'CLOSED','{}')")
            conn.commit(); conn.close()
            one = deploy.deployment_snapshot(db)["logical_fingerprint"]
            self.assertEqual(one, deploy.logical_db_fingerprint(db))
            conn = sqlite3.connect(db); conn.execute("UPDATE dependency_circuits SET state='OPEN'"); conn.commit(); conn.close()
            self.assertNotEqual(one, deploy.deployment_snapshot(db)["logical_fingerprint"])

    def test_webpage_delivery_only_allows_untouched_projection_same_acked_event(self):
        snapshot = {key: [] for key in deploy._SNAPSHOT_TABLES}
        event = {"id": "e", "run_id": "r", "generation": 1}
        snapshot["events"] = [event]
        snapshot["outbox"] = [{"event_id": "e", "state": "ACKNOWLEDGED"}]
        snapshot["attempts"] = [{"run_id": "r", "generation": 1,
                                 "attempt_json": {"phase": "DECIDED"}}]
        author = {"event_id": "e", "target_kind": "AUTHOR", "state": "ACKNOWLEDGED", "protocol_ack": {"ok": True}}
        page = {"event_id": "e", "target_kind": "WEBPAGE", "state": "PENDING", "attempts": 0,
                "claim_generation": 0, "lease_token": None, "lease_until_ms": None,
                "transport_receipt": None, "protocol_ack": None}
        snapshot["deliveries"] = [author, page]
        deploy.assert_quiescent(snapshot)
        for mutation in (lambda d: d.update(state="CLAIMED"), lambda d: d.update(attempts=1),
                         lambda d: d.update(lease_token="claimed"), lambda d: d.update(event_id="other")):
            dirty = dict(snapshot)
            dirty["deliveries"] = [author, dict(page)]
            mutation(dirty["deliveries"][1])
            with self.assertRaises(ValueError): deploy.assert_quiescent(dirty)
        dirty = dict(snapshot); dirty["attempts"] = [{"attempt_json": {"phase": "WEBPAGEPENDING"}}]
        with self.assertRaises(ValueError): deploy.assert_quiescent(dirty)

    def test_quiescence_allows_exact_transport_accepted_terminal_pair_without_mutation(self):
        try: import workflow_store
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_store
        payload = {"run_id":"r", "evaluation_generation":1, "result":"FAIL"}
        event = {"id":"e", "run_id":"r", "generation":1, "kind":"DISPATCH",
                 "payload":json.dumps(payload), "payload_digest":workflow_store.digest(payload)}
        ar = {"session_id":"s","worktree":"w","role":"author"}
        author = {"id":"d", "event_id":"e", "target_kind":"AUTHOR", "state":"TRANSPORT_ACCEPTED",
                  "transport_receipt":{"accepted":True,"event_id":"e","message_id":"d","payload_digest":event["payload_digest"],"recipient":ar}, "protocol_ack":None, "lease_token":"historical",
                  "lease_until_ms":1, "recipient_json":ar,
                  "attempts":1, "claim_generation":1, "payload_digest":event["payload_digest"]}
        page = {"event_id":"e", "target_kind":"WEBPAGE", "state":"PENDING", "attempts":0,
                "claim_generation":0, "lease_token":None, "lease_until_ms":None,
                "transport_receipt":None, "protocol_ack":None,
                "recipient_json":{"session_id":None,"worktree":"w","role":"owner"},
                "payload_digest":event["payload_digest"]}
        snapshot = {key: [] for key in deploy._SNAPSHOT_TABLES}
        snapshot.update(events=[event], outbox=[{"event_id":"e","state":"ACKNOWLEDGED"}],
                        runs=[{"id":"r","run_json":{"author":{"session_id":"s","worktree":"w","role":"author"}}}],
                        attempts=[{"run_id":"r","generation":1,"attempt_json":{"phase":"DECIDED"}}],
                        deliveries=[author,page])
        before = json.dumps(snapshot, sort_keys=True)
        deploy.assert_quiescent(snapshot)
        self.assertEqual(json.dumps(snapshot, sort_keys=True), before)
        for index, mutate in enumerate((lambda a: a.update(state="CLAIMED"), lambda a: a.update(lease_until_ms=9999999999999),
                       lambda a: a.update(recipient_json={"session_id":"foreign"}),
                       lambda p: p.update(payload_digest="bad"))):
            bad = json.loads(before)
            mutate(bad["deliveries"][1])
            with self.subTest(index=index), self.assertRaises(ValueError): deploy.assert_quiescent(bad)

    def test_stop_verifies_all_identities_then_stops_supervisor_before_job(self):
        identities = [{"pid": i, "created": i * 10} for i in range(1, 5)]
        health = {"supervisor": {"identity": identities[0]},
                  "child": {"coordinator_identity": identities[1], "scope_name": "job"}}
        class Backend:
            def __init__(self): self.calls = []
            def open(self, pid): self.calls.append(("open", pid)); return pid
            def identity(self, pid, handle): return identities[pid - 1]
            def open_job(self, name): self.calls.append(("job", name)); return name
            def contains(self, job, identity): return True
            def terminate(self, handle): self.calls.append(("terminate-supervisor", handle))
            def terminate_job(self, job, timeout): self.calls.append(("terminate-job", job)); return "CONFIRMED"
            def wait(self, handle, timeout): self.calls.append(("wait", handle)); return True
            def close(self, handle): pass
            def close_job(self, job): pass
        backend = Backend()
        with mock.patch.object(deploy, "_parent_identity", side_effect=[identities[2], identities[3]]):
            result = deploy.stop_host(health, Path("python.exe"), backend=backend)
        self.assertEqual(result["status"], "STOPPED")
        self.assertLess(backend.calls.index(("terminate-supervisor", 1)), backend.calls.index(("terminate-job", "job")))

    def test_stop_identity_mismatch_does_not_kill(self):
        identity = {"pid": 1, "created": 10}
        health = {"supervisor": {"identity": identity}, "child": {"coordinator_identity": {"pid": 2, "created": 20}, "scope_name": "job"}}
        class Backend:
            def open(self, pid): return pid
            def identity(self, pid, handle): return {"pid": pid, "created": 99}
            def terminate(self, handle): self.fail("must not terminate")
            def close(self, handle): pass
        with mock.patch.object(deploy, "_parent_identity", side_effect=[{"pid": 3, "created": 30}, {"pid": 4, "created": 40}]), self.assertRaisesRegex(ValueError, "identity"):
            deploy.stop_host(health, Path("python.exe"), backend=Backend())

    def test_parent_identity_queries_launcher_path_and_uses_native_raw_identity(self):
        rows = [json.dumps({"ProcessId": 11, "ParentProcessId": 22}),
                json.dumps({"ProcessId": 22, "ExecutablePath": "C:\\venv\\Scripts\\python.exe"})]
        class Result:
            def __init__(self, text): self.stdout = text
        with mock.patch.object(deploy.subprocess, "run", side_effect=[Result(rows[0]), Result(rows[1])]), \
             mock.patch("gate_execution.process_identity", return_value={"pid": 22, "created": 220}) as identity:
            got = deploy._parent_identity(11, Path("C:/venv/Scripts/python.exe"))
        self.assertEqual(got, {"pid": 22, "created": 220})
        identity.assert_called_once_with(22)

    def test_old_health_config_mismatch_refuses_before_identity_checks(self):
        config = Path("C:/private/coordinator.json")
        health = {"state": "running", "supervisor": {"config_path": str(config),
                  "config_sha256": "wrong", "identity": {"pid": 1, "created": 1}},
                  "child": {"coordinator_identity": {"pid": 2, "created": 2}, "scope_name": "job"}}
        with self.assertRaisesRegex(ValueError, "does not match"):
            deploy._validate_current_host(health, config, b"config")

    def test_host_validation_allows_transient_count_only_when_explicitly_deferred(self):
        config = Path("C:/private/coordinator.json")
        identity = {"pid": 2, "created": 20}
        health = {"state": "running", "supervisor": {"identity": {"pid": 1, "created": 10},
                  "config_path": str(config), "config_sha256": deploy.sha256(b"config")},
                  "child": {"coordinator_identity": identity, "scope_name": "job"}}
        class Scope:
            def snapshot(self): return {"active_processes": 3}
            def contains(self, value): return value == identity
            def close(self): pass
        def process(pid): return {"pid": pid, "created": pid * 10}
        with mock.patch("gate_execution.process_identity", side_effect=process), \
             mock.patch("workflow_process_scope.OwnedProcessScope.open_existing", return_value=Scope()):
            deploy._validate_current_host(health, config, b"config", require_sole_member=False)
            with self.assertRaisesRegex(ValueError, "sole owned job member"):
                deploy._validate_current_host(health, config, b"config")

    def test_execute_waits_for_owned_probe_count_to_settle_before_stop(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            args, stack = self.run_fixture(root, calls=calls)
            with stack:
                checks = []
                def validate(*unused, **kw):
                    checks.append(kw.get("require_sole_member", True))
                    if len(checks) == 2:
                        raise deploy._HostProbeDrainPending("transient probe")
                with mock.patch.object(deploy, "_validate_current_host", side_effect=validate), \
                     mock.patch.object(deploy.time, "sleep"):
                    deploy.run(args)
                self.assertEqual(checks, [False, True, True])
                self.assertLess(calls.index("stop"), calls.index("publish"))

    def test_execute_persistent_owned_members_refuses_stop_and_releases_hold(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            args, stack = self.run_fixture(root, calls=calls)
            with stack:
                def validate(*unused, **kw):
                    if kw.get("require_sole_member", True):
                        raise deploy._HostProbeDrainPending("persistent probe")
                with mock.patch.object(deploy, "_validate_current_host", side_effect=validate), \
                     mock.patch.object(deploy.time, "monotonic", side_effect=[0, 11]):
                    with self.assertRaises(deploy._HostProbeDrainPending):
                        deploy.run(args)
                self.assertNotIn("stop", calls)
                self.assertIn("resume", calls)

    def test_execute_identity_failure_is_not_deferred(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            args, stack = self.run_fixture(root, calls=calls)
            with stack:
                def validate(*unused, **kw):
                    if kw.get("require_sole_member", True):
                        raise ValueError("current host process identity is not verified")
                with mock.patch.object(deploy, "_validate_current_host", side_effect=validate), \
                     mock.patch.object(deploy.time, "sleep") as sleep:
                    with self.assertRaisesRegex(ValueError, "identity is not verified"):
                        deploy.run(args)
                sleep.assert_not_called()
                self.assertNotIn("stop", calls)

    def test_hold_release_requires_barrier_cleanup_and_reopens_intake(self):
        class Admission:
            def __init__(self): self.open = False
            def resume_hold(self, root, receipt): self.open = True
            def maintenance(self, root): return None if self.open else {"status": "held"}
        class Barrier:
            _hold = None
            _locks = []
            _ownership_uncertain = False
        admission = Admission()
        with tempfile.TemporaryDirectory() as root:
            receipt = Path(root) / "hold.json"
            receipt.write_text("owned")
            self.assertTrue(deploy._release_pre_stop_hold(admission, root, {}, receipt, Barrier(), False))
            self.assertTrue(admission.open)
            self.assertFalse(receipt.exists())
            admission.open = False
            receipt.write_text("owned")
            self.assertFalse(deploy._release_pre_stop_hold(admission, root, {}, receipt, Barrier(), True))
            self.assertFalse(admission.open)
            self.assertTrue(receipt.exists())

    def test_hold_receipt_persistence_failure_resumes_before_any_stop(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            with stack:
                calls = []
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.side_effect = lambda *a: calls.append("resume")
                original = deploy.package._atomic_write
                def fail_receipt(path, data, **kw):
                    if Path(path).name == "current-update-hold.json": raise OSError("fixture persist fault")
                    return original(path, data, **kw)
                with mock.patch.object(deploy.package, "_atomic_write", side_effect=fail_receipt), \
                     mock.patch.object(deploy, "stop_host") as stop, self.assertRaises(OSError):
                    deploy.run(args)
                stop.assert_not_called()
                admission.resume_hold.assert_called_once()

    def test_stop_failure_retains_hold_and_records_host_stop_phase(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            args, stack = self.run_fixture(root, calls=calls, stop=RuntimeError("stop fault"))
            with stack:
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.side_effect = mock.Mock()
                with mock.patch.object(deploy, "stop_host", side_effect=RuntimeError("stop fault")), self.assertRaises(RuntimeError):
                    deploy.run(args)
                admission.resume_hold.assert_not_called()
                failure = json.loads((Path(root) / "main/.opencode/workflow/current-failure.json").read_text())
                self.assertEqual(failure["phase"], "host_stop_started")

    def test_barrier_exit_failure_retains_hold_after_successful_start(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            args, stack = self.run_fixture(root, calls=calls, barrier_exit=True)
            with stack:
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.side_effect = mock.Mock()
                with self.assertRaisesRegex(RuntimeError, "cleanup uncertain"):
                    deploy.run(args)
                self.assertIn("start", calls)
                admission.resume_hold.assert_not_called()

    def test_successful_runner_orders_refresh_start_barrier_manifest_then_resume(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            args, stack = self.run_fixture(root, calls=calls)
            with stack:
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.side_effect = lambda *a: calls.append("resume")
                original = deploy.package._atomic_write
                def record_manifest(path, data, **kw):
                    if Path(path).name == "current-installation.json": calls.append("manifest")
                    return original(path, data, **kw)
                with mock.patch.object(deploy.package, "_atomic_write", side_effect=record_manifest):
                    result = deploy.run(args)
                self.assertEqual(result["status"], "DEPLOYED")
                self.assertLess(calls.index("refresh"), calls.index("start"))
                self.assertLess(calls.index("start"), calls.index("barrier_exit"))
                self.assertLess(calls.index("barrier_exit"), calls.index("manifest"))
                self.assertLess(calls.index("manifest"), calls.index("resume"))

    def test_reviewed_opt_in_binding_plan_is_passed_to_package_builder(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            targets=[{"target":f"MoonStation14-agent-0{i}","coordinator_config":f"C:/fixture/coord-{i}.json",
                      "tool_output_read":True,"roles":[{"role":"author","binding_mode":"worktree",
                      "caller":f"author-{i}","token_file":f"C:/fixture/token-{i}",
                      "context_file":f"C:/fixture/context-{i}.json"}]} for i in range(1,5)]
            plan=Path(root)/"approved-plan.json"; plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":targets}))
            args.binding_plan=plan; args.binding_plan_sha256=deploy.sha256(plan.read_bytes())
            with stack, mock.patch.object(deploy.package,"build",return_value={"files":{}}) as build:
                deploy.run(args)
            build.assert_called_once_with(mock.ANY,mock.ANY,binding_plan=plan,preserve_bindings=False,reviewed_source_hashes={})

    def test_shell_plan_execute_preserves_pointer_cas_hash_through_publish(self):
        with tempfile.TemporaryDirectory() as root:
            args,stack=self.run_fixture(root)
            names=["main",*(f"MoonStation14-agent-0{i}" for i in range(1,5))]
            plan=Path(root)/"shell-plan.json"
            plan.write_text(json.dumps({"version":1,"mode":"inspection-safe-shell",
                "targets":[{"target":name,"expected_config_sha256":f"{i+1:064x}"} for i,name in enumerate(names)]}))
            args.binding_plan=plan;args.binding_plan_sha256=deploy.sha256(plan.read_bytes())
            expected_pointer_hash=args.expected_pointer_sha256
            published={}
            def publish(*a,**kw):
                published.update(kw);Path(a[2]).write_text('{"status":"applied"}');return {"status":"applied"}
            with stack,mock.patch.object(deploy.package,"build",return_value={"files":{}}) as build,mock.patch.object(deploy.package,"publish",side_effect=publish):
                result=deploy.run(args)
            self.assertEqual("DEPLOYED",result["status"])
            self.assertEqual(expected_pointer_hash,published["expected_pointer_sha256"])
            build.assert_called_once_with(mock.ANY,mock.ANY,binding_plan=plan,preserve_bindings=False,reviewed_source_hashes={})

    def test_reviewer_search_permissions_plan_is_limited_to_main(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            plan = Path(root) / "reviewer-search-plan.json"
            valid = {"version": 1, "mode": "reviewer-search-permissions",
                     "targets": [{"target": "main", "expected_config_sha256": "a" * 64}]}
            plan.write_text(json.dumps(valid))
            args.binding_plan = plan
            args.binding_plan_sha256 = deploy.sha256(plan.read_bytes())
            with stack, mock.patch.object(deploy.package, "build", return_value={"files": {}}) as build:
                deploy.run(args)
            build.assert_called_once_with(mock.ANY, mock.ANY, binding_plan=plan,
                                          preserve_bindings=False, reviewed_source_hashes={})

            for target in (
                    {"target": "other", "expected_config_sha256": "a" * 64},
                    {"target": "main", "expected_config_sha256": "A" * 64},
                    {"target": "main", "expected_config_sha256": "a" * 63},
                    {"target": "main", "expected_config_sha256": "a" * 64, "path": "custom"}):
                with self.subTest(target=target), stack:
                    plan.write_text(json.dumps({**valid, "targets": [target]}))
                    args.binding_plan_sha256 = deploy.sha256(plan.read_bytes())
                    with mock.patch("admission.begin_hold") as begin_hold, \
                            mock.patch.object(deploy, "stop_host") as stop:
                        with self.assertRaises(ValueError):
                            deploy.run(args)
                    begin_hold.assert_not_called()
                    stop.assert_not_called()

    def test_terminal_review_repair_requires_scoped_terminal_error(self):
        import workflow_store
        saved_task = {"kind": "CHECK", "state": "COMPLETED", "evaluation_generation": 2,
                      "assignment_generation": 1, "input_digest": "input", "result": {"decision": "PASS"}}
        semantic = {"run_id": "run_test", "evaluation_generation": 2, "input_digest": "a" * 64,
                    "result": "ERROR", "findings": [], "artifact_refs": []}
        result_digest = workflow_store.digest(semantic)
        result_id = "event_result_" + __import__("hashlib").sha256(result_digest.encode()).hexdigest()[:24]
        payload = {"contract_version": 1, "event_id": result_id, **semantic, "result_id": result_id,
                   "result_digest": result_digest, "next_action": "WAIT_FOR_RECOVERY",
                   "error_code": "NATIVE_EXECUTION_ERROR", "error_message": "failure", "incident_ref": "incident_x"}
        result_digest = workflow_store.digest(semantic)
        result_id = "event_result_" + __import__("hashlib").sha256(result_digest.encode()).hexdigest()[:24]
        payload.update(event_id=result_id, result_id=result_id, result_digest=result_digest)
        saved_task.update(evaluation_generation=2, input_digest="a" * 64)
        task = {"id": "check", "run_id": "run_test", "result_digest": "result", "task_json": saved_task}
        author = {"session_id": "author", "worktree": "tree", "role": "author"}
        deliveries = [{"event_id": result_id, "target_kind": "AUTHOR", "recipient_json": author,
                       "state": "PENDING", "attempts": 0, "claim_generation": 0,
                       "lease_token": None, "lease_until_ms": None, "transport_receipt": None,
                       "protocol_ack": None, "payload_digest": workflow_store.digest(payload)},
                      {"event_id": result_id, "target_kind": "WEBPAGE",
                       "recipient_json": {"session_id": None, "worktree": "tree", "role": "owner"},
                       "state": "PENDING", "attempts": 0, "claim_generation": 0,
                       "lease_token": None, "lease_until_ms": None, "transport_receipt": None,
                       "protocol_ack": None, "payload_digest": workflow_store.digest(payload)}]
        snap = {"runs": [{"id": "run_test", "current_generation": 2, "sequence": 7,
                          "worktree": "tree", "run_json": {"run_kind": "FEATURE_GATE", "input_digest": "a" * 64,
                          "author": author}}],
                "attempts": [{"run_id": "run_test", "generation": 2,
                              "attempt_json": {"phase": "DECIDED", "result": "ERROR"}}],
                "tasks": [task], "resources": [], "native_scopes": [], "deliveries": deliveries,
                "events": [{"id": result_id, "run_id": "run_test", "generation": 2, "kind": "DISPATCH",
                            "payload": payload, "payload_digest": workflow_store.digest(payload)}],
                "outbox": [{"event_id": result_id, "state": "PENDING"}]}
        deploy.assert_terminal_review_repair(snap, "run_test")
        stopping = {"contract_version": 1, "event_id": "stopping", "kind": "ATTEMPT_STOPPING",
                    "run_id": "run_test", "evaluation_generation": 2}
        snap["events"].append({"id": "stopping", "run_id": "run_test", "generation": 2,
                               "kind": "ATTEMPT_STOPPING", "payload": stopping,
                               "payload_digest": workflow_store.digest(stopping)})
        snap["outbox"].append({"event_id": "stopping", "state": "PENDING"})
        deploy.assert_terminal_review_repair(snap, "run_test")
        for change in (lambda s: s["outbox"].append({"event_id": "foreign", "state": "PENDING"}),
                       lambda s: (s["events"].append({"id": "foreign", "run_id": "other", "kind": "TASK_READY",
                           "payload": {"kind": "TASK_READY"}}),
                           s["outbox"].append({"event_id": "foreign", "state": "PENDING"})),
                       lambda s: s["resources"].append({"state": "ACTIVE"}),
                       lambda s: s["tasks"][0]["task_json"].update(state="RUNNING")):
            bad = json.loads(json.dumps(snap))
            change(bad)
            with self.assertRaises(ValueError):
                deploy.assert_terminal_review_repair(bad, "run_test")
        bad = json.loads(json.dumps(snap))
        bad["events"][0]["payload"]["evaluation_generation"] = 99
        with self.assertRaises(ValueError):
            deploy.assert_terminal_review_repair(bad, "run_test")
        bad = json.loads(json.dumps(snap))
        bad["tasks"][0]["task_json"]["kind"] = "REVIEW"
        bad["tasks"][0]["task_json"]["state"] = "CANCELLED"
        with self.assertRaises(ValueError):
            deploy.assert_terminal_review_repair(bad, "run_test")

    def test_terminal_review_cli_flag_reaches_run_argument(self):
        with tempfile.TemporaryDirectory() as root:
            paths = [Path(root) / name for name in ("config", "health", "receipt", "python", "hashes")]
            for path in paths: path.write_text("{}")
            with mock.patch.object(deploy, "run", return_value={"status": "READY"}) as run:
                status = deploy.main(["--config", str(paths[0]), "--health-file", str(paths[1]),
                    "--receipt", str(paths[2]), "--receipt-sha256", deploy.sha256(paths[2].read_bytes()),
                    "--expected-pointer-sha256", "0" * 64, "--python", str(paths[3]),
                    "--reviewed-source-hashes", str(paths[4]), "--preflight",
                    "--repair-terminal-review-run", "run_example"])
            self.assertEqual(status, 0)
            self.assertEqual(run.call_args.args[0].repair_terminal_review, "run_example")
    def test_preflight_accepts_exact_four_dynamic_worktree_author_targets(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            args.preflight=True; args.execute=False
            targets=[{"target":f"MoonStation14-agent-0{i}","coordinator_config":f"C:/fixture/coord-{i}.json",
                      "tool_output_read":True,"roles":[{"role":"author","binding_mode":"worktree",
                      "caller":f"slot-{i}","token_file":f"C:/fixture/token-{i}",
                      "context_file":f"C:/fixture/context-{i}.json"}]} for i in range(1,5)]
            plan=Path(root)/"dynamic-plan.json"
            plan.write_text(json.dumps({"version":1,"coordinator_endpoint":"http://127.0.0.1:49200","targets":targets}))
            args.binding_plan=plan; args.binding_plan_sha256=deploy.sha256(plan.read_bytes())
            with stack, mock.patch.object(deploy.package,"build") as build:
                result=deploy.run(args)
            self.assertEqual("READY",result["status"])
            build.assert_not_called()

    def test_preflight_accepts_only_exact_five_target_shell_retirement_plan(self):
        with tempfile.TemporaryDirectory() as root:
            args,stack=self.run_fixture(root);args.preflight=True;args.execute=False
            targets=[{"target":name,"expected_config_sha256":f"{i+1:064x}"} for i,name in enumerate(["main",*(f"MoonStation14-agent-0{i}" for i in range(1,5))])]
            profiles=[{"target":f"MoonStation14-agent-0{i}","path":".opencode/agents/fidelity-reviewer.md","expected_sha256":f"{i+10:064x}"} for i in range(1,4)]
            plan=Path(root)/"shell-plan.json"
            def write(data):
                plan.write_text(json.dumps(data));args.binding_plan=plan;args.binding_plan_sha256=deploy.sha256(plan.read_bytes())
            valid={"version":1,"mode":"inspection-safe-shell","targets":targets,"profiles":profiles}
            write(valid)
            with stack,mock.patch.object(deploy.package,"build") as build:
                self.assertEqual("READY",deploy.run(args)["status"])
                build.assert_not_called()
            for bad in (
                {**valid,"targets":[*targets[:-1],{**targets[-1],"expected_config_sha256":"z"*64}]},
                {**valid,"targets":[*targets,{"target":"MoonStation14-agent-05","expected_config_sha256":"0"*64}]},
                {**valid,"profiles":[*profiles[:-1],{**profiles[-1],"target":"MoonStation14-agent-04"}]},
                {**valid,"profiles":[*profiles[:-1],{**profiles[-1],"path":".opencode/agents/other.md"}]},
                {**valid,"mode":"arbitrary"},
            ):
                with self.subTest(plan=bad),stack:
                    write(bad)
                    with self.assertRaises(ValueError): deploy.run(args)

    def test_endpoint_only_execute_preserves_other_configuration_and_starts_new_preimage(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            args.opencode_server = "http://127.0.0.1:45278"
            Path(root, "home/.local/state/opencode/service.json").write_text(json.dumps({"url": args.opencode_server, "password": "secret"}))
            captures=[]
            with stack, mock.patch.object(deploy, "start_host", side_effect=lambda config,*a: captures.append(json.loads(Path(config).read_text())) or {"identities":[],"health":{"child":{"scope_name":"job"}}}):
                deploy.run(args)
            config=json.loads(args.config.read_text())
            self.assertEqual(args.opencode_server, config["opencode_server"])
            self.assertEqual(str(Path(root)/"workflow.sqlite"), config["database"])
            self.assertEqual([], config["bindings"])
            self.assertEqual(config, captures[0])

    def test_endpoint_probe_failure_precedes_hold_stop_and_config_write(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            args.opencode_server="http://127.0.0.1:45278"
            Path(root,"home/.local/state/opencode/service.json").write_text(json.dumps({"url":args.opencode_server,"password":"secret"}))
            original=args.config.read_bytes()
            with stack, mock.patch("gate_dispatch._check_v2", side_effect=RuntimeError("private detail")), \
                 mock.patch.object(deploy, "stop_host") as stop:
                with self.assertRaisesRegex(ValueError,"v2 API check failed"): deploy.run(args)
            self.assertEqual(original,args.config.read_bytes())
            stop.assert_not_called()

    def test_endpoint_start_failure_retains_backup_hashes_and_new_config_state(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack=self.run_fixture(root)
            args.opencode_server="http://127.0.0.1:45278"
            Path(root,"home/.local/state/opencode/service.json").write_text(json.dumps({"url":args.opencode_server,"password":"secret"}))
            original=args.config.read_bytes()
            with stack, mock.patch.object(deploy,"start_host",side_effect=RuntimeError("start failed")), self.assertRaises(RuntimeError):
                deploy.run(args)
            failure=json.loads((Path(root)/"main/.opencode/workflow/current-failure.json").read_text())
            self.assertEqual("new_configured",failure["config_replacement_state"])
            self.assertTrue(failure["configured_new_endpoint_remains"])
            backup=Path(failure["config_backup"])
            self.assertEqual(original,backup.read_bytes())
            self.assertEqual(deploy.sha256(original),failure["config_sha256_before"])
            self.assertNotEqual(original,args.config.read_bytes())

    def test_endpoint_post_stop_config_drift_does_not_overwrite_unknown_preimage(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack=self.run_fixture(root)
            args.opencode_server="http://127.0.0.1:45278"
            Path(root,"home/.local/state/opencode/service.json").write_text(json.dumps({"url":args.opencode_server,"password":"secret"}))
            unknown=b'{"operator":"changed"}'
            def stop(*unused): args.config.write_bytes(unknown)
            with stack, mock.patch.object(deploy,"stop_host",side_effect=stop), self.assertRaisesRegex(ValueError,"before endpoint replacement"):
                deploy.run(args)
            self.assertEqual(unknown,args.config.read_bytes())

    def test_binding_plan_hash_refusal_precedes_hold_or_publication(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            args.binding_plan=Path(root)/"plan.json"; args.binding_plan.write_text("{}")
            args.binding_plan_sha256="0"*64
            calls=[]
            with stack, mock.patch.object(deploy.package,"build") as build, mock.patch.object(deploy,"stop_host") as stop:
                with self.assertRaisesRegex(ValueError,"reviewed SHA-256"):
                    deploy.run(args)
            build.assert_not_called(); stop.assert_not_called()

    def test_stalled_repair_success_preserves_paused_pending_baseline(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            health = {"status": "running", "pending_events": 2,
                      "orchestration_paused_for_maintenance": True}
            args, stack = self.run_fixture(root, calls=calls, api_health=health)
            args.repair_stalled_run = "repair-run"
            with stack:
                admission = __import__("sys").modules["admission"]
                admission.begin_hold.side_effect = lambda *a, **kw: calls.append(("hold", kw["owner"])) or {"owner": kw["owner"]}
                admission.resume_hold.side_effect = lambda *a: calls.append("resume")
                with mock.patch.object(deploy, "assert_stalled_native_repair") as predicate, \
                     mock.patch.object(deploy, "_pending_outbox_count", return_value=2):
                    result = deploy.run(args)
                self.assertEqual(result["status"], "DEPLOYED")
                self.assertEqual(predicate.call_count, 4)
                self.assertIn(("hold", "workflow-stalled-repair"), calls)
                self.assertEqual(calls.count("resume"), 1)
                manifest = json.loads((Path(root) / "main/.opencode/workflow/current-installation.json").read_text())
                self.assertEqual(manifest["repair_run_id"], "repair-run")
                self.assertTrue(manifest["database_preserved_through_paused_upgrade"])
                self.assertLess(calls.index("start"), calls.index("barrier_exit"))
                self.assertLess(calls.index("barrier_exit"), calls.index("resume"))
                self.assertLess(calls.index("resume"), calls.index("manifest") if "manifest" in calls else len(calls))

    def test_stalled_repair_refuses_unpaused_or_wrong_pending_health(self):
        for health in ({"status": "running", "pending_events": 2,
                        "orchestration_paused_for_maintenance": False},
                       {"status": "running", "pending_events": 0,
                        "orchestration_paused_for_maintenance": True}):
            with self.subTest(health=health), tempfile.TemporaryDirectory() as root:
                calls = []
                args, stack = self.run_fixture(root, calls=calls, api_health=health)
                args.repair_stalled_run = "repair-run"
                with stack:
                    admission = __import__("sys").modules["admission"]
                    with mock.patch.object(deploy, "assert_stalled_native_repair"), \
                         mock.patch.object(deploy, "_pending_outbox_count", return_value=2), \
                         self.assertRaisesRegex(ValueError, "not paused"):
                        deploy.run(args)
                    admission.resume_hold.assert_not_called()
                    self.assertTrue((Path(root) / "private/current-update-hold.json").exists())
                    failure = json.loads((Path(root) / "main/.opencode/workflow/current-failure.json").read_text())
                    self.assertEqual(failure["phase"], "host_start")
                    self.assertNotIn("current-installation.json", [p.name for p in (Path(root) / "main/.opencode/workflow").glob("current-installation.json")])

    def test_stalled_repair_refuses_changed_post_start_fingerprint_and_keeps_hold(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root, api_health={"status": "running", "pending_events": 2,
                "orchestration_paused_for_maintenance": True})
            args.repair_stalled_run = "repair-run"
            with stack:
                snapshots = [dict(logical_fingerprint=value, outbox=[]) for value in ("same", "same", "stopped", "changed")]
                with mock.patch.object(deploy, "deployment_snapshot", side_effect=snapshots), \
                     mock.patch.object(deploy, "assert_stalled_native_repair"), \
                     mock.patch.object(deploy, "_pending_outbox_count", return_value=2), \
                     self.assertRaisesRegex(ValueError, "changed after paused"):
                    deploy.run(args)
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.assert_not_called()
                self.assertTrue((Path(root) / "private/current-update-hold.json").exists())

    def test_queued_review_repair_success_preserves_paused_pending_baseline(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            health = {"status": "running", "pending_events": 4,
                      "orchestration_paused_for_maintenance": True}
            args, stack = self.run_fixture(root, calls=calls, api_health=health)
            args.repair_queued_review = "run"
            with stack:
                admission = __import__("sys").modules["admission"]
                admission.begin_hold.side_effect = lambda *a, **kw: calls.append(("hold", kw["owner"])) or {"owner": kw["owner"]}
                admission.resume_hold.side_effect = lambda *a: calls.append("resume")
                original = deploy.package._atomic_write
                def record_manifest(path, data, **kw):
                    if Path(path).name == "current-installation.json": calls.append("manifest")
                    return original(path, data, **kw)
                # Exercise the real queued-review proof predicate on its positive fixture,
                # while the runner's repeated snapshots share that proven state.
                proof = self.queued_review_snapshot()
                proof["logical_fingerprint"] = "stable"
                with mock.patch.object(deploy, "deployment_snapshot", return_value=proof), \
                     mock.patch.object(deploy, "assert_stalled_repair_snapshot_stable", return_value=True), \
                     mock.patch.object(deploy, "_pending_outbox_count", return_value=4), \
                     mock.patch.object(deploy.package, "_atomic_write", side_effect=record_manifest):
                    result = deploy.run(args)
                self.assertEqual(result["status"], "DEPLOYED")
                self.assertIn(("hold", "workflow-queued-review-repair"), calls)
                self.assertLess(calls.index("start"), calls.index("barrier_exit"))
                self.assertLess(calls.index("barrier_exit"), calls.index("manifest"))
                self.assertLess(calls.index("manifest"), calls.index("resume"))
                manifest = json.loads((Path(root) / "main/.opencode/workflow/current-installation.json").read_text())
                self.assertEqual(manifest["repair_queued_review_run_id"], "run")
                self.assertTrue(manifest["database_preserved_through_paused_upgrade"])
                self.assertTrue(manifest["prestop_lease_bookkeeping_changed"])
                self.assertTrue(manifest["database_unchanged"])
                self.assertEqual(json.loads((Path(root) / "private/config.json").read_text())["bindings"], [])

    def test_queued_review_repair_refuses_unpaused_or_wrong_pending_health(self):
        for health in ({"status": "running", "pending_events": 4,
                        "orchestration_paused_for_maintenance": False},
                       {"status": "running", "pending_events": 3,
                        "orchestration_paused_for_maintenance": True}):
            with self.subTest(health=health), tempfile.TemporaryDirectory() as root:
                calls = []
                args, stack = self.run_fixture(root, calls=calls, api_health=health)
                args.repair_queued_review = "run"
                with stack:
                    admission = __import__("sys").modules["admission"]
                    with mock.patch.object(deploy, "deployment_snapshot", return_value=self.queued_review_snapshot()), \
                         mock.patch.object(deploy, "_pending_outbox_count", return_value=4), \
                         self.assertRaises(ValueError):
                        deploy.run(args)
                    admission.resume_hold.assert_not_called()

    def test_queued_review_repair_refuses_changed_post_start_fingerprint_and_keeps_hold(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root, api_health={"status": "running", "pending_events": 4,
                "orchestration_paused_for_maintenance": True})
            args.repair_queued_review = "run"
            with stack:
                snapshots = [dict(logical_fingerprint=value, outbox=[]) for value in ("same", "same", "stopped", "changed")]
                with mock.patch.object(deploy, "deployment_snapshot", side_effect=snapshots), \
                     mock.patch.object(deploy, "assert_queued_review_repair"), \
                     mock.patch.object(deploy, "_pending_outbox_count", return_value=4), \
                     self.assertRaisesRegex(ValueError, "changed after paused"):
                    deploy.run(args)
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.assert_not_called()
                self.assertTrue((Path(root) / "private/current-update-hold.json").exists())

    def test_normal_update_still_rejects_active_queued_work(self):
        with tempfile.TemporaryDirectory() as root:
            args, stack = self.run_fixture(root)
            with stack, mock.patch.object(deploy, "deployment_snapshot", return_value=self.queued_review_snapshot()), \
                 self.assertRaises(ValueError):
                deploy.run(args)

    def normal_cancelled_snapshot(self):
        snapshot = self.cancelled_ready_snapshot()
        snapshot["logical_fingerprint"] = "settled"
        return snapshot

    def test_normal_update_preserves_cancelled_pending_baseline(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            baseline = self.normal_cancelled_snapshot()
            current = json.loads(json.dumps(baseline))
            stopped = json.loads(json.dumps(baseline))
            stopped["logical_fingerprint"] = "stopped"
            stopped["outbox"][0].update(state="CLAIMED", claim_generation=1, lease_until_ms=123)
            after = json.loads(json.dumps(stopped))
            args, stack = self.run_fixture(root, calls=calls, api_health={"status": "running", "pending_events": 3,
                "orchestration_paused_for_maintenance": True})
            with stack:
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.side_effect = lambda *a: calls.append("resume")
                original = deploy.package._atomic_write
                def record_manifest(path, data, **kw):
                    if Path(path).name == "current-installation.json": calls.append("manifest")
                    return original(path, data, **kw)
                with mock.patch.object(deploy, "deployment_snapshot", side_effect=[baseline, current, stopped, after]), \
                     mock.patch.object(deploy.package, "_atomic_write", side_effect=record_manifest):
                    result = deploy.run(args)
                self.assertEqual(result["status"], "DEPLOYED")
                self.assertLess(calls.index("start"), calls.index("barrier_exit"))
                self.assertLess(calls.index("barrier_exit"), calls.index("manifest"))
                self.assertLess(calls.index("manifest"), calls.index("resume"))
                manifest = json.loads((Path(root) / "main/.opencode/workflow/current-installation.json").read_text())
                self.assertTrue(manifest["prestop_lease_bookkeeping_changed"])
                self.assertTrue(manifest["database_preserved_through_paused_upgrade"])
                self.assertFalse(manifest["database_unchanged"])

    def test_normal_pending_baseline_rejects_unpaused_or_wrong_count_health(self):
        for health in ({"status": "running", "pending_events": 3,
                        "orchestration_paused_for_maintenance": False},
                       {"status": "running", "pending_events": 2,
                        "orchestration_paused_for_maintenance": True}):
            with self.subTest(health=health), tempfile.TemporaryDirectory() as root:
                calls = []
                snapshots = [self.normal_cancelled_snapshot() for _ in range(3)]
                args, stack = self.run_fixture(root, calls=calls, api_health=health)
                with stack:
                    admission = __import__("sys").modules["admission"]
                    admission.resume_hold.side_effect = lambda *a: calls.append("resume")
                    with mock.patch.object(deploy, "deployment_snapshot", side_effect=snapshots), \
                         mock.patch.object(deploy.package, "_atomic_write", wraps=deploy.package._atomic_write), \
                         self.assertRaises(ValueError):
                        deploy.run(args)
                    self.assertNotIn("resume", calls)

    def test_normal_pending_baseline_rejects_post_start_database_change(self):
        with tempfile.TemporaryDirectory() as root:
            calls = []
            baseline = self.normal_cancelled_snapshot()
            snapshots = [json.loads(json.dumps(baseline)) for _ in range(4)]
            snapshots[2]["logical_fingerprint"] = "stopped"
            snapshots[3]["logical_fingerprint"] = "changed"
            args, stack = self.run_fixture(root, calls=calls, api_health={"status": "running", "pending_events": 3,
                "orchestration_paused_for_maintenance": True})
            with stack:
                admission = __import__("sys").modules["admission"]
                admission.resume_hold.side_effect = lambda *a: calls.append("resume")
                with mock.patch.object(deploy, "deployment_snapshot", side_effect=snapshots), \
                     self.assertRaisesRegex(ValueError, "changed after paused"):
                    deploy.run(args)
                self.assertNotIn("resume", calls)

    def test_start_uses_explicit_supervisor_command_and_real_nested_health_shape(self):
        with tempfile.TemporaryDirectory() as root:
            config = Path(root) / "private config.json"
            config.write_text("{}")
            python = Path(root) / "shared" / ".venv" / "Scripts" / "python.exe"
            health = Path(root) / "health.json"
            class Proc:
                pid = 42
                def poll(self): return None
            identity = {"pid": 42, "created": 420}
            def launch(*args, **kwargs):
                nonce = args[0][args[0].index("--activation-nonce") + 1]
                health.write_text(json.dumps({"state": "running", "supervisor": {"identity": {"pid": 41, "created": 410},
                    "activation_nonce": nonce, "config_path": str(config.resolve()),
                    "config_sha256": deploy.sha256(config.read_bytes())}, "child": {
                    "coordinator_identity": identity, "scope_name": "scope"}}))
                return Proc()
            class Scope:
                def snapshot(self): return {"active_processes": 1}
                def contains(self, value): return value == identity
                def close(self): pass
            with mock.patch.object(deploy.subprocess, "Popen", side_effect=launch) as popen, \
                 mock.patch.object(deploy, "time") as clock:
                clock.monotonic.side_effect = [0, 0]
                clock.sleep.return_value = None
                # Imported lazily by start_host.
                with mock.patch("gate_execution.process_identity", side_effect=lambda pid: identity if pid == 42 else {"pid": pid, "created": pid * 10}), \
                     mock.patch("workflow_process_scope.OwnedProcessScope.open_existing", return_value=Scope()):
                    result = deploy.start_host(config, python, health, 59183, {"OPENCODE_PASSWORD": "secret"})
            args = popen.call_args.args[0]
            self.assertEqual(args[0], str(python))
            self.assertEqual(args[args.index("--config") + 1], str(config))
            self.assertEqual(result["health"]["supervisor"]["config_path"], str(config.resolve()))


if __name__ == "__main__":
    unittest.main()
