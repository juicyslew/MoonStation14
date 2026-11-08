#!/usr/bin/env python3
"""One-shot, bounded native GameTest hang evidence capture (not a pass/fail gate)."""
from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import time

LIMIT_SECONDS = 180
TARGET_STALL_SECONDS = 15
TARGET_BATCH_MARKER = "Running test batch 'atmosphere_wall_opening"
TAG = "atmos-wall-opening"
FIXTURE = "AtmosphereRoomGameTests.fullWallOpeningDrainsMoreThanOneBlockOpening"
TAG_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")


def load_runtime(repo: Path):
    shared = repo.parent / "MoonStation14-shared" / "tooling" / "agent_workflow" / "workflow_runtime.py"
    if not shared.is_file():
        raise RuntimeError(f"shared workflow runtime missing: {shared}")
    tooling_dir = str(shared.parent)
    if tooling_dir not in sys.path:
        sys.path.insert(0, tooling_dir)
    spec = importlib.util.spec_from_file_location("ms14_workflow_runtime", shared)
    runtime = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = runtime
    spec.loader.exec_module(runtime)
    return runtime


def owned_processes(root_pid: int) -> list[dict]:
    """Return process descendants by parent identity; never select by executable name."""
    script = ("$all=Get-CimInstance Win32_Process; $seen=@(" + str(root_pid) + "); $out=@(); "
              "do {$changed=$false; foreach($p in $all){if(($seen -contains [int]$p.ParentProcessId) -and "
              "(-not ($seen -contains [int]$p.ProcessId))){$seen+=([int]$p.ProcessId);$out+=($p | Select-Object ProcessId,ParentProcessId,CreationDate,CommandLine,Name,ExecutablePath);$changed=$true}}} while($changed); "
              "ConvertTo-Json -Compress -InputObject @($out)")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                            capture_output=True, text=True, timeout=10, check=True)
    values = json.loads(result.stdout or "[]")
    return values if isinstance(values, list) else [values]


def process_info(pid: int):
    script = f"Get-CimInstance Win32_Process -Filter 'ProcessId={pid}' | Select-Object ProcessId,ParentProcessId,CreationDate,CommandLine,Name,ExecutablePath | ConvertTo-Json -Compress"
    result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                            capture_output=True, text=True, timeout=10, check=True)
    return json.loads(result.stdout) if result.stdout.strip() else None


def observe_stage(text: str, target_started: float | None, elapsed: float, evidence):
    """Classify only the named fixture batch; startup delay is not target evidence."""
    completion = evidence.gametest_completion_state(text)
    if completion:
        return "completed_without_stall", target_started, completion
    if TARGET_BATCH_MARKER in text and target_started is None:
        target_started = elapsed
    if target_started is not None and elapsed - target_started >= TARGET_STALL_SECONDS:
        return "target_stalled", target_started, None
    return "waiting_for_target", target_started, None


def original_context_tags(script_path: Path) -> list[str]:
    source = script_path.with_name("native_hang_original_tags.json")
    try:
        tags = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"cannot load frozen original tag manifest: {exc}") from exc
    if (not isinstance(tags, list) or len(tags) != 42 or
            any(not isinstance(tag, str) or not TAG_PATTERN.fullmatch(tag) for tag in tags) or
            len(set(tags)) != len(tags)):
        raise RuntimeError("frozen original tag manifest must contain exactly 42 unique valid tag strings")
    return tags


def selected_tags(args, script_path: Path) -> list[str]:
    return original_context_tags(script_path) if getattr(args, "with_original_context", False) else [TAG]


def run(args):
    repo = Path(args.repo).resolve()
    if repo != Path.cwd().resolve() or not (repo / "gradlew.bat").is_file():
        raise RuntimeError("--repo must be the current source worktree containing gradlew.bat")
    if args.tag != TAG:
        raise RuntimeError(f"only the bounded diagnostic selector {TAG!r} is permitted")
    out = (repo / args.out).resolve()
    if not out.is_relative_to(repo / "build") or out.exists():
        raise RuntimeError("--out must be a fresh path inside this repository's build directory")
    out.mkdir(parents=True)
    log, dump = out / "gametest.log", out / "thread-dump.txt"
    runtime = load_runtime(repo)
    tags = selected_tags(args, Path(__file__))
    argv = runtime.execution_argv(repo, ["./gradlew", "runGameTestServer",
        f"-Pms14GameTestTags={','.join(tags)}", f"-Pms14GameTestDir={out / 'server'}",
        "--no-daemon", "--console=plain"])
    started = time.monotonic()
    report = {"kind": "diagnostic", "fixture": FIXTURE, "selector": TAG,
              "target_stall_seconds": TARGET_STALL_SECONDS, "limit_seconds": LIMIT_SECONDS,
              "context": "original-tags" if len(tags) > 1 else "isolated-fixture",
              "tag_count": len(tags), "captured": False, "return_code": None}
    proc = None
    try:
        with log.open("w", encoding="utf-8") as stream:
            proc = subprocess.Popen(argv, cwd=repo, stdout=stream, stderr=subprocess.STDOUT,
                                    creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0))
            report["worker_pid"] = proc.pid
            deadline = started + LIMIT_SECONDS
            target_started = None
            while time.monotonic() < deadline:
                rc = proc.poll()
                if rc is not None:
                    report["return_code"] = rc
                    break
                elapsed = time.monotonic() - started
                stage, target_started, completed = observe_stage(
                    log.read_text(encoding="utf-8", errors="replace"), target_started,
                    elapsed, runtime.validation_evidence)
                if target_started is not None:
                    report["target_started_elapsed_seconds"] = round(target_started, 1)
                if stage == "completed_without_stall":
                    report["status"] = stage
                    report["completed_game_tests"] = completed
                    report["return_code"] = proc.wait(timeout=max(1, min(10, deadline-time.monotonic())))
                    break
                if stage == "target_stalled" and not report["captured"]:
                    candidates = []
                    for info in owned_processes(proc.pid):
                        command = (info.get("CommandLine") or "").lower()
                        name = (info.get("Name") or "").lower()
                        if name not in ("java.exe", "javaw.exe"):
                            continue
                        if "gradledaemon" in command or "gradleworker" in command or "gradlewrapper" in command:
                            continue
                        if "gametestsERVERrunprogramargs.txt".lower() not in command and "devlaunch.main" not in command:
                            continue
                        candidates.append(info)
                    if not candidates:
                        raise RuntimeError("no identifiable owned game-server Java process; refusing unrelated capture")
                    info = candidates[-1]
                    pid = int(info["ProcessId"])
                    current = process_info(pid)
                    if not current or any(current.get(key) != info.get(key) for key in ("CreationDate", "CommandLine", "Name")):
                        raise RuntimeError("owned JVM identity changed before jcmd; refusing PID reuse/foreign capture")
                    executable = Path(current.get("ExecutablePath") or "")
                    java = executable.parent / "jcmd.exe"
                    if not java.is_file():
                        raise RuntimeError("jcmd.exe adjacent to identified owned JVM is unavailable")
                    with dump.open("w", encoding="utf-8") as target:
                        subprocess.run([str(java), str(pid), "Thread.print", "-l"], stdout=target,
                                       stderr=subprocess.STDOUT, timeout=max(1, min(20, deadline-time.monotonic()-5)), check=True)
                    if dump.stat().st_size < 100 or "Full thread dump" not in dump.read_text(errors="replace"):
                        raise RuntimeError("jcmd returned no verifiable full thread dump")
                    report.update(captured=True, elapsed_seconds=round(elapsed, 1), java_pid=pid,
                                  java_creation=info.get("CreationDate"), java_command=info.get("CommandLine"),
                                  capture_stage="target_batch_stall", capture_not_gameplay_pass=True)
                    runtime.stop_owned_tree(proc)
                    report["return_code"] = proc.wait(timeout=10)
                    break
                time.sleep(5)
            if proc.poll() is None:
                report["timed_out"] = True
                report["status"] = "target_not_reached" if target_started is None else "target_stall_not_captured"
                runtime.stop_owned_tree(proc)
                report["return_code"] = proc.wait(timeout=10)
        report["elapsed_seconds"] = round(time.monotonic() - started, 1)
        if report["captured"]:
            report["status"] = "captured-diagnostic"
        elif report.get("status") == "completed_without_stall":
            pass
        elif report.get("status") in ("target_not_reached", "target_stall_not_captured"):
            report["completed_game_tests"] = None
        else:
            text = log.read_text(encoding="utf-8", errors="replace")
            completed = runtime.validation_evidence.gametest_completion_state(text)
            report["status"] = "completed_without_stall" if completed else "completed_without_capture"
            report["completed_game_tests"] = completed
        if proc.poll() is None:
            raise RuntimeError("owned worker exit was not confirmed")
    except BaseException as exc:
        report["error"] = str(exc)
        if proc is not None and proc.poll() is None:
            try:
                runtime.stop_owned_tree(proc)
            except Exception as cleanup_error:
                report["cleanup_error"] = str(cleanup_error)
        raise
    finally:
        report["elapsed_seconds"] = round(time.monotonic() - started, 1)
        (out / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"diagnostic={report['status']} captured={report['captured']} worker_exit={report['return_code']} artifacts={out}")
    return 0 if report["captured"] or report["status"] == "completed_without_stall" else 2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=".")
    parser.add_argument("--out", default="build/teg-hang-repair/native-capture")
    parser.add_argument("--tag", default=TAG)
    parser.add_argument("--with-original-context", action="store_true",
                        help="replay the frozen exact original failed-run tag context")
    args = parser.parse_args()
    try:
        return run(args)
    except KeyboardInterrupt:
        print("diagnostic interrupted; worker cleanup attempted", file=sys.stderr)
        return 130
    except Exception as exc:
        print(f"diagnostic failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
