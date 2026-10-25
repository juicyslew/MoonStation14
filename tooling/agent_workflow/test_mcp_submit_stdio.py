"""End-to-end Windows stdio submit regression using the pinned MCP SDK."""
import asyncio
import json
import os
from pathlib import Path
import subprocess
import unittest



REPO_ROOT = Path(__file__).resolve().parents[2]
SDK_PYTHON = REPO_ROOT.parent / "MoonStation14-shared" / ".venv-workflow-mcp" / "Scripts" / "python.exe"


class StdioSubmitTests(unittest.TestCase):
    def test_real_git_snapshot_submit_and_retry_over_official_stdio_transport(self):
        if os.name != "nt":
            self.skipTest("regression targets Windows child-process transport")
        if not SDK_PYTHON.is_file():
            self.skipTest("pinned workflow MCP interpreter unavailable")
        probe = subprocess.run(
            [str(SDK_PYTHON), "-c", "import mcp, anyio"],
            capture_output=True, timeout=10,
        )
        if probe.returncode:
            self.skipTest("pinned MCP SDK dependencies unavailable")

        from mcp import ClientSession
        from mcp.client.stdio import StdioServerParameters, stdio_client

        source = Path(__file__).resolve().parent
        import tempfile
        approved_temp = Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Temp" / "opencode"
        approved_temp.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=approved_temp) as temporary:
            workspace = Path(temporary)
            repo = workspace / "MoonStation14" / "author01"
            root = workspace / "MoonStation14" / "Asset Building Workspace" / "work" / "agent_exchange"
            repo.mkdir(parents=True)
            for args in (
                ["git", "init", "-q", "--initial-branch=main", str(repo)],
                ["git", "-C", str(repo), "config", "user.email", "test@example.invalid"],
                ["git", "-C", str(repo), "config", "user.name", "Fixture"],
            ):
                subprocess.run(args, check=True, timeout=15, capture_output=True)
            (repo / ".gitignore").write_text("build/\n", encoding="utf-8")
            (repo / "tracked.txt").write_text("tracked source\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(repo), "add", "."], check=True, timeout=15, capture_output=True)
            subprocess.run(["git", "-C", str(repo), "commit", "-qm", "fixture base"], check=True, timeout=15, capture_output=True)
            # Include tracked changes and untracked content in the real full-worktree snapshot.
            (repo / "tracked.txt").write_text("dirty source\n", encoding="utf-8")
            (repo / "new-source.txt").write_text("untracked source\n", encoding="utf-8")
            (repo / "plan.json").write_text(json.dumps({
                "title": "stdio submit regression", "kind": "bugfix",
                "acceptance_criteria": ["snapshot includes source"],
                "fixture": "new-source.txt", "manual_steps": ["Inspect snapshot"],
                "checks": [{"argv": ["python", "-c", "pass"], "rationale": "fixture", "covers": ["snapshot includes source"]}],
            }), encoding="utf-8")

            # Replace only identity/role/dispatch integration; adapter, runtime, Git, and
            # snapshot/submission remain the production implementations.
            wrapper = Path(temporary) / "stdio_fixture.py"
            pid_file = Path(temporary) / "stdio_fixture.pid"
            wrapper.write_text('''\
import importlib.util, json, sys
from pathlib import Path
from types import SimpleNamespace
from tooling.agent_workflow import workflow_mcp as m
pid_file=Path(sys.argv[sys.argv.index("--pid-file")+1])
pid_file.write_text(str(__import__("os").getpid()),encoding="ascii")
runtime_path=Path(sys.argv[sys.argv.index("--runtime")+1])
spec=importlib.util.spec_from_file_location("stdio_source_workflow_runtime",runtime_path)
runtime=importlib.util.module_from_spec(spec); sys.modules[spec.name]=runtime; spec.loader.exec_module(runtime)
for flag in ("--runtime", "--pid-file"):
    index=sys.argv.index(flag); del sys.argv[index:index+2]
m._load_runtime=lambda:runtime
class Dispatch:
    def _session(self, server, sid): return {"id":sid,"agent":"orchestrate-opus","directory":str(repo)}
    def _role(self, server, role, directory): return {"name":role,"mode":"primary"}
    def request_gate(self, args, exchange, *, workflow):
        self.calls+=1
        path=Path(exchange)/"candidates"/(args.id+".json")
        candidate=workflow.readjson(path)
        if not candidate.get("gate_request"):
            self.intents+=1
            (Path(repo).parent/"dispatch-intents.txt").write_text(str(self.intents),encoding="ascii")
            candidate["gate_request"]={"origin_session_id":args.origin_session,"session_id":"lead-fixture","prompt_admitted":True,"snapshot_key":candidate["snapshot_key"]}
            workflow.writejson(path,candidate)
        return {"status":"requested","detail":"fixture dispatch"}
    calls=0
    intents=0
repo=Path(sys.argv[sys.argv.index("--repo")+1]).resolve()
dispatch=Dispatch()
original_execution=m._services()[1]
m._services=lambda:(dispatch,original_execution)
m._load_runtime=lambda:runtime
print("source-runtime-loaded:"+str(runtime.__file__),file=sys.stderr,flush=True)
m.main()
''', encoding="utf-8")
            env = os.environ.copy()
            env["PYTHONPATH"] = str(source.parents[1]) + os.pathsep + env.get("PYTHONPATH", "")
            params = StdioServerParameters(
                command=str(SDK_PYTHON), args=[str(wrapper), "--repo", str(repo), "--profile", "author",
                                               "--runtime", str(source / "workflow_runtime.py"),
                                               "--pid-file", str(pid_file)],
                env=env,
            )

            progress = []
            async def exercise():
                async with asyncio.timeout(240):
                    async with stdio_client(params) as (reader, writer):
                        async with ClientSession(reader, writer) as session:
                            await session.initialize()
                            progress.append("initialized")
                            catalog = await session.list_tools()
                            self.assertEqual({tool.name for tool in catalog.tools}, {"submit", "status"})
                            args = {"candidate_id":"stdio-candidate", "sprint_id":"stdio-sprint",
                                    "session_id":"author-session", "plan_path":"plan.json",
                                    "build_identity":"fixture", "unverified":"", "purpose":"readiness"}
                            # Exercise several Git invocations through this real child transport.
                            first = await session.call_tool("submit", args)
                            progress.append("first-submit-returned")
                            second = await session.call_tool("submit", args)
                            progress.append("retry-returned")
                            for reply in (first, second):
                                value = json.loads(reply.content[0].text)
                                self.assertTrue(value["ok"], value)
                                self.assertEqual(value["state"], "requested")
                            candidate_path = root / "candidates" / "stdio-candidate.json"
                            candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
                            self.assertEqual(candidate["gate_request"]["session_id"], "lead-fixture")
                            changed_paths = {item["path"] for item in candidate["dirty_changes"]}
                            self.assertIn("tracked.txt", changed_paths)
                            self.assertIn("new-source.txt", changed_paths)
                            self.assertEqual(len(list((root / "candidates").glob("stdio-candidate.json"))), 1)
                            self.assertEqual(candidate["gate_request"]["origin_session_id"], "author-session")
                            self.assertEqual((repo.parent/"dispatch-intents.txt").read_text(encoding="ascii"), "1")
                            status = await session.call_tool("status", {"candidate_id":"stdio-candidate"})
                            status_value = json.loads(status.content[0].text)
                            self.assertTrue(status_value["ok"], status_value)
                            self.assertEqual(status_value["candidate_id"], "stdio-candidate")

            try:
                asyncio.run(exercise())
            except BaseException as exc:
                self.fail(f"stdio fixture failed after {progress!r}: {type(exc).__name__}: {exc}")
            finally:
                # python.exe may be a launcher which detaches the actual interpreter;
                # terminate only the PID written by this fixture wrapper and its tree.
                if pid_file.exists():
                    fixture_pid = pid_file.read_text(encoding="ascii").strip()
                    cleanup = subprocess.run(
                        ["taskkill", "/PID", fixture_pid, "/T", "/F"],
                        capture_output=True, timeout=10,
                    )
                    if cleanup.returncode not in (0, 128):
                        self.fail(f"could not verify fixture process-tree cleanup for PID {fixture_pid}")


if __name__ == "__main__":
    unittest.main()
