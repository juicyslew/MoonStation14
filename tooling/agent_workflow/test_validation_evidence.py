import hashlib
import tempfile
import time
import unittest
from pathlib import Path

from tooling.agent_workflow.validation_evidence import snapshot_evidence, collect_evidence


def safe(repo, value):
    path = (Path(repo) / value).resolve()
    if not path.is_relative_to(Path(repo).resolve()):
        raise ValueError("outside repository")
    return path


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def collect(self, check, log=""):
        log_path = self.repo / "check.log"
        log_path.write_text(log)
        start = time.time() - 0.2
        return collect_evidence(check, self.repo, start, time.time() + .1, log_path, {},
                                hash_file=sha, safe_path=safe)

    def test_fresh_junit_counts_and_skips_block(self):
        folder = self.repo / "build/test-results/test"
        folder.mkdir(parents=True)
        (folder / "TEST-a.xml").write_text('<testsuite tests="3" failures="0" errors="0" skipped="1"/>')
        result = self.collect({"kind": "junit"})
        self.assertEqual(result["counts"]["passed"], 2)
        self.assertEqual(result["status"], "FAIL")

    def test_failures_and_errors_use_real_junit_attribute_names(self):
        folder = self.repo / "build/test-results/test"
        folder.mkdir(parents=True)
        (folder / "TEST-a.xml").write_text('<testsuite tests="4" failures="1" errors="1" skipped="0"/>')
        result = self.collect({"kind": "junit"})
        self.assertEqual((result["counts"]["failed"], result["counts"]["errors"]), (1, 1))
        self.assertEqual(result["status"], "FAIL")

    def test_unchanged_old_junit_xml_blocks_successful_exit(self):
        folder = self.repo / "build/test-results/test"
        folder.mkdir(parents=True)
        report = folder / "TEST-a.xml"
        report.write_text('<testsuite tests="1"/>')
        before = snapshot_evidence({"kind": "junit"}, self.repo, hash_file=sha, safe_path=safe)
        result = collect_evidence({"kind": "junit", "exit_code": 0}, self.repo, time.time() - .1,
                                  time.time() + .1, self.repo / "unused.log", before, hash_file=sha, safe_path=safe)
        self.assertEqual(result["status"], "UNVERIFIED")

    def test_stale_or_absent_junit_does_not_pass(self):
        self.assertEqual(self.collect({"kind": "junit"})["status"], "UNVERIFIED")

    def test_game_test_requires_completion_marker(self):
        result = self.collect({"kind": "gametest"}, "GameTest tag preflight: 2/4 fixtures selected\n")
        self.assertEqual(result["selection"], "filtered")
        self.assertEqual(result["status"], "UNVERIFIED")

    def test_game_test_failure_marker_blocks_even_if_exit_zero(self):
        result = self.collect({"kind": "gametest"}, "2 tests failed\n")
        self.assertEqual(result["status"], "FAIL")

    def test_command_pass_is_completion_only_not_test_coverage(self):
        result = self.collect({"kind": "command", "exit_code": 0})
        self.assertEqual(result["status"], "PASS")
        self.assertIsNone(result["counts"]["tests"])

    def test_readiness_parses_current_producer_report_shape(self):
        folder = self.repo / "build/test-results/featureReadiness/xml"
        folder.mkdir(parents=True)
        (folder / "TEST-a.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
        report_dir = self.repo / "build/reports/feature-readiness"
        report_dir.mkdir(parents=True)
        started = time.time()
        (report_dir / "current-run-started.txt").write_text("2026-10-13T00:00:00Z\nselection=ui\ncatalog=ExampleTest\n")
        (report_dir / "readiness.md").write_text("""# Feature readiness run
- Selection: `ui`; catalog: `ExampleTest`
- Focused JUnit route: **PASS**; no Minecraft process required. Fresh XML suites: 1.
- Run identity: 2026-10-13T00:00:00Z; finished: now; elapsed: 1 ms wall-clock attempt.
- Current XML totals: 2 tests, 0 failures, 0 errors, 0 skipped. Checks are forced to execute; no cached/up-to-date result can count as pass.
| Category | Status | Selected evidence classes |
|---|---|---|
| ui | PASS | ExampleTest |
""")
        result = collect_evidence({"kind": "readiness", "selectors": ["ui"]}, self.repo,
                                  started - .1, time.time() + .1, self.repo / "unused.log", {}, hash_file=sha, safe_path=safe)
        self.assertEqual(result["status"], "PASS", result["findings"])
        wrong = collect_evidence({"kind": "readiness", "selectors": ["interaction"]}, self.repo,
                                 started - .1, time.time() + .1, self.repo / "unused.log", {}, hash_file=sha, safe_path=safe)
        self.assertEqual(wrong["status"], "UNVERIFIED")

    def test_explicit_build_artifact_is_hashed_and_missing_blocks(self):
        result = self.collect({"kind": "build", "artifacts": ["missing.jar"]})
        self.assertEqual(result["status"], "UNVERIFIED")
        self.assertFalse(result["artifacts"])

    def test_unchanged_build_output_is_truthfully_marked_preexisting(self):
        artifact = self.repo / "out.jar"
        artifact.write_bytes(b"fixture")
        check = {"kind": "build", "artifacts": ["out.jar"]}
        before = snapshot_evidence(check, self.repo, hash_file=sha, safe_path=safe)
        log = self.repo / "check.log"
        log.write_text("BUILD SUCCESSFUL")
        result = collect_evidence(check, self.repo, time.time() - .1, time.time() + .1,
                                  log, before, hash_file=sha, safe_path=safe)
        self.assertEqual(result["status"], "PASS")
        self.assertTrue(result["artifacts"][0]["preexisting"])
        self.assertFalse(result["artifacts"][0]["changed_this_run"])


if __name__ == "__main__":
    unittest.main()
