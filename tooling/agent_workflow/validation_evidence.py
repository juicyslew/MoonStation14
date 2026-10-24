"""Evidence classifiers for serialized gate checks.

Check kinds: build (explicit ``artifacts``), junit (optional expected class
``selectors``), readiness (requested selector list), gametest, launch-smoke,
command. Launch smoke requires ``ready_patterns`` and may use explicit
``error_patterns``. These helpers only inspect producer outputs. Core must bind
command PASS to its own successful process result and classify known argv as a
typed check so generic commands cannot bypass specialized evidence.

Report schema: status PASS/FAIL/UNVERIFIED; counts has test/pass/fail/error/
skip and selected/total nullable integers; selection is full/filtered/unknown;
artifacts is exact path/hash/size; findings contains blockers. ``hash_file``
and ``safe_path`` callbacks reuse the workflow's canonical helpers.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def _stat(path):
    try:
        s = path.stat()
        return {"mtime_ns": s.st_mtime_ns, "size": s.st_size}
    except OSError:
        return None


def _glob_matches(repo, pattern, safe_path):
    """Return safe repo-local files matching a diagnostic glob, or raise."""
    candidate = Path(pattern)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise ValueError(f"unsafe artifact glob: {pattern}")
    matches = []
    for match in repo.glob(pattern):
        # Resolve every match through the shared path guard (also rejects symlinks
        # escaping the repository), not just the user-provided pattern.
        safe_match = Path(safe_path(repo, str(match.relative_to(repo))))
        if safe_match.is_file(): matches.append(safe_match)
    return sorted({str(p.resolve()): p for p in matches}.values(), key=lambda p: str(p))


def snapshot_evidence(check, repo, *, hash_file, safe_path):
    repo = Path(repo)
    kind = check.get("kind", "command")
    if kind == "junit": paths = [repo / "build/test-results/test"]
    elif kind == "readiness": paths = [repo / "build/reports/feature-readiness/readiness.md", repo / "build/reports/feature-readiness/current-run-started.txt", repo / "build/test-results/featureReadiness/xml"]
    elif kind == "build": paths = [safe_path(repo, p) for p in check.get("artifacts", [])]
    elif kind in ("gametest", "launch-smoke") and check.get("log"): paths = [safe_path(repo, check["log"])]
    elif kind == "command":
        paths = [safe_path(repo, p) for p in check.get("artifacts", [])]
        for pattern in check.get("artifact_globs", []):
            paths.extend(_glob_matches(repo, pattern, safe_path))
    else: paths = []
    result = {}
    for path in paths:
        path = Path(path)
        files = sorted(path.glob("*.xml")) if path.is_dir() else [path]
        for file in files:
            stamp = _stat(file)
            if stamp:
                stamp["sha256"] = hash_file(file)
                result[str(file.resolve())] = stamp
    return result


def _fresh(path, before, started, finished, hash_file):
    stamp = _stat(path)
    if not stamp or stamp["size"] <= 0: return False
    prior = before.get(str(path.resolve()))
    timestamp = stamp["mtime_ns"] / 1e9
    if timestamp < started - 1 or timestamp > finished + 1: return False
    if prior is None: return True
    same_stat = stamp == {k: prior.get(k) for k in ("mtime_ns", "size")}
    return not same_stat or hash_file(path) != prior.get("sha256")


def _xml_counts(paths):
    counts = dict(tests=0, passed=0, failed=0, errors=0, skipped=0)
    invalid = False
    for path in paths:
        try:
            root = ET.parse(path).getroot()
            values = {"tests": int(root.attrib.get("tests", 0)),
                      "failed": int(root.attrib.get("failures", 0)),
                      "errors": int(root.attrib.get("errors", 0)),
                      "skipped": int(root.attrib.get("skipped", 0))}
            if any(v < 0 for v in values.values()) or sum(values[k] for k in ("failed", "errors", "skipped")) > values["tests"]:
                invalid = True
            for key, value in values.items(): counts[key] += value
        except (ET.ParseError, OSError, ValueError):
            invalid = True
    counts["passed"] = max(0, counts["tests"] - counts["failed"] - counts["errors"] - counts["skipped"])
    return counts, invalid


def collect_evidence(check, repo, started, finished, log_path, before, *, hash_file, safe_path):
    repo, log_path = Path(repo), Path(log_path)
    kind = check.get("kind", "command")
    counts = dict(tests=None, passed=None, failed=None, errors=None, skipped=None, selected=None, total=None)
    selection, findings, artifacts = "unknown", [], []

    def add(path):
        path = Path(path)
        if path.is_file():
            key = str(path.resolve())
            digest = hash_file(path)
            prior = before.get(key)
            artifacts.append({"path": str(path), "sha256": digest, "size": path.stat().st_size,
                              "preexisting": prior is not None,
                              "changed_this_run": prior is None or prior.get("sha256") != digest,
                              "observed_fresh_this_run": _fresh(path, before, started, finished, hash_file)})

    if kind in ("junit", "readiness"):
        if kind == "junit":
            directory = repo / "build/test-results/test"
            all_paths = sorted(directory.glob("TEST-*.xml"))
            paths = [p for p in all_paths if _fresh(p, before, started, finished, hash_file)]
            if not all_paths: findings.append("JUnit XML reports missing or empty")
            elif len(paths) != len(all_paths): findings.append("one or more JUnit XML reports are stale")
        else:
            report = repo / "build/reports/feature-readiness/readiness.md"
            marker = repo / "build/reports/feature-readiness/current-run-started.txt"
            directory = repo / "build/test-results/featureReadiness/xml"
            selectors = check.get("selectors", [])
            expected = ",".join(selectors)
            if not _fresh(report, before, started, finished, hash_file): findings.append("readiness report missing or stale")
            else:
                text = report.read_text(encoding="utf-8", errors="replace")
                try:
                    marker_lines = marker.read_text(encoding="utf-8").splitlines()
                    marker_time = marker_lines[0]
                    marker_selection = next(x.split("=", 1)[1] for x in marker_lines if x.startswith("selection="))
                    selection_line = re.search(r"(?m)^- Selection: `([^`]*)`;", text)
                    run_line = re.search(r"(?m)^- Run identity: ([^;]+);", text)
                    if (not _fresh(marker, before, started, finished, hash_file) or marker_selection != expected or
                        not selection_line or selection_line.group(1).replace(", ", ",") != expected or
                        not run_line or run_line.group(1).strip() != marker_time):
                        findings.append("readiness selection/current-run marker mismatch")
                except (OSError, IndexError, StopIteration): findings.append("readiness current-run marker missing or malformed")
                if not re.search(r"(?m)^- Focused JUnit route: \*\*PASS\*\*", text): findings.append("readiness focused JUnit route is not PASS")
                rows = {name.lower(): state.upper() for name, state in re.findall(r"(?m)^\|\s*(ui|interaction|prediction|preview)\s*\|\s*(PASS|FAIL|UNVERIFIED)\s*\|", text)}
                if any(rows.get(s) is None for s in selectors): findings.append("selected readiness category missing")
                if any(rows.get(s) == "FAIL" for s in selectors): findings.append("selected readiness category reports FAIL")
                if any(rows.get(s) == "UNVERIFIED" for s in selectors): findings.append("selected readiness category is UNVERIFIED")
                totals = re.search(r"(?m)^- Current XML totals: (\d+) tests, (\d+) failures, (\d+) errors, (\d+) skipped\.", text)
                if not totals: findings.append("readiness current XML totals missing")
                elif any(int(v) for v in totals.groups()[1:]): findings.append("readiness producer reports failures, errors, or skips")
            all_paths = sorted(directory.glob("*.xml"))
            paths = [p for p in all_paths if _fresh(p, before, started, finished, hash_file)]
            if not all_paths: findings.append("readiness XML missing")
            elif len(paths) != len(all_paths): findings.append("one or more readiness XML reports are stale")
            selection = "filtered"
        counts_xml, invalid = _xml_counts(paths)
        counts.update(counts_xml)
        if invalid: findings.append("JUnit counts invalid or inconsistent")
        if counts["tests"] == 0: findings.append("no tests evidenced")
        if counts["failed"] or counts["errors"] or counts["skipped"]: findings.append("test failures, errors, or skips present")
        if kind == "junit":
            expected_classes = check.get("selectors", [])
            if "--tests" in check.get("argv", []) or expected_classes: selection = "filtered"
            found = set()
            for path in paths:
                try: found.add(ET.parse(path).getroot().attrib.get("name", ""))
                except (ET.ParseError, OSError): pass
            if expected_classes and any(not any(n == e or n.endswith("." + e) for n in found) for e in expected_classes):
                findings.append("expected selected JUnit classes missing from fresh XML")
            if selection == "unknown": selection = "full"
        for path in paths: add(path)
        if kind == "readiness" and counts["tests"]:
            report = (repo / "build/reports/feature-readiness/readiness.md").read_text(encoding="utf-8", errors="replace")
            match = re.search(r"(?m)^- Current XML totals: (\d+) tests, (\d+) failures, (\d+) errors, (\d+) skipped\.", report)
            if match and tuple(map(int, match.groups())) != (counts["tests"], counts["failed"], counts["errors"], counts["skipped"]):
                findings.append("readiness report totals do not match fresh XML")
    elif kind == "build":
        requested = check.get("artifacts", [])
        if not requested: findings.append("build requires explicit artifact paths")
        for item in requested:
            path = safe_path(repo, item)
            if not path.is_file(): findings.append(f"build artifact missing: {item}")
            else: add(path)
    elif kind in ("gametest", "launch-smoke"):
        if not log_path.is_file() or not _fresh(log_path, {}, started, finished, hash_file): findings.append("captured coordinator log missing or stale")
        else:
            add(log_path)
            producer = safe_path(repo, check["log"]) if check.get("log") else None
            producer_fresh = bool(producer and producer.is_file() and _fresh(producer, before, started, finished, hash_file))
            if producer_fresh: add(producer)
            if kind == "gametest":
                pre = re.compile(r"GameTest tag preflight:\s*(\d+)/(\d+) fixtures selected")
                complete = re.compile(r"All\s+(\d+) required tests passed", re.I)
                failed = re.compile(r"(?m)^\s*(\d+) (?:required )?tests? failed\b", re.I)
                saw_pre = saw_complete = False
                # Stream to avoid retaining large Gradle/server logs in memory.
                with log_path.open(encoding="utf-8", errors="replace") as stream:
                    for line in stream:
                        m = pre.search(line)
                        if m:
                            counts["selected"], counts["total"] = map(int, m.groups())
                            selection = "filtered" if counts["selected"] != counts["total"] else "full"
                            saw_pre = True
                        m = complete.search(line)
                        if m:
                            counts["tests"] = counts["passed"] = int(m.group(1)); saw_complete = True
                        m = failed.search(line)
                        if m and int(m.group(1)):
                            counts["failed"] = int(m.group(1))
                            findings.append("native GameTest failure summary present")
                if not saw_complete: findings.append("native GameTest all-required-passed marker absent")
                if not saw_pre: findings.append("native GameTest selection preflight absent")
                if counts["selected"] == 0: findings.append("native GameTest selected no fixtures")
            else:
                if not producer_fresh: findings.append("producer log missing or stale")
                text = producer.read_text(encoding="utf-8", errors="replace") if producer_fresh else ""
                patterns = check.get("ready_patterns", [])
                if not patterns or any(p not in text for p in patterns): findings.append("required launch readiness marker absent")
                if any(p in text for p in check.get("error_patterns", [])): findings.append("configured launch error marker present")
    elif kind == "command":
        selection = "unknown"
        requested = check.get("artifacts", [])
        for item in requested:
            try: path = Path(safe_path(repo, item))
            except (ValueError, OSError):
                findings.append(f"unsafe declared artifact: {item}"); continue
            if not path.is_file() or not _fresh(path, before, started, finished, hash_file):
                findings.append(f"declared artifact missing or stale: {item}")
            else: add(path)
        seen = set()
        for pattern in check.get("artifact_globs", []):
            try: matches = _glob_matches(repo, pattern, safe_path)
            except (ValueError, OSError):
                findings.append(f"unsafe artifact glob: {pattern}"); continue
            fresh = [p for p in matches if _fresh(p, before, started, finished, hash_file)]
            if not fresh: findings.append(f"artifact glob produced no fresh files: {pattern}")
            for path in fresh:
                key = str(path.resolve())
                if key not in seen:
                    add(path); seen.add(key)
    else: findings.append(f"unsupported evidence kind: {kind}")

    failed = counts["failed"] not in (None, 0) or counts["errors"] not in (None, 0) or counts["skipped"] not in (None, 0)
    status = "FAIL" if check.get("exit_code", 0) != 0 or failed or any("reports FAIL" in f or "failure summary" in f or "reports failures" in f for f in findings) else "UNVERIFIED" if findings else "PASS"
    if kind == "command": status = "FAIL" if check.get("exit_code") != 0 else "UNVERIFIED" if findings else "PASS"
    return {"status": status, "counts": counts, "selection": selection, "artifacts": artifacts, "findings": findings[:20]}
