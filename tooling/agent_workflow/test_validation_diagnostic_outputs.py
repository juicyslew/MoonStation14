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


class DiagnosticOutputTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def run_check(self, check, before=None, start=None):
        start = time.time() - .1 if start is None else start
        log = self.repo / "unused.log"
        log.touch()
        return collect_evidence(check, self.repo, start, time.time() + .2, log,
                                before or {}, hash_file=sha, safe_path=safe)

    def test_uuid_fresh_binary_collected_deduplicated_and_hashed(self):
        old = self.repo / "diagnostics/old/result.jfr"
        old.parent.mkdir(parents=True)
        old.write_bytes(b"old")
        check = {"kind": "command", "exit_code": 0,
                 "artifact_globs": ["diagnostics/**/*.jfr", "diagnostics/**/*.jfr"]}
        before = snapshot_evidence(check, self.repo, hash_file=sha, safe_path=safe)
        start = time.time()
        fresh = self.repo / "diagnostics/run-uuid/result.jfr"
        fresh.parent.mkdir(parents=True)
        fresh.write_bytes(b"old")  # identical content, but a new observed file
        report = self.run_check(check, before, start)
        self.assertEqual(report["status"], "PASS", report["findings"])
        self.assertEqual(len(report["artifacts"]), 1)
        row = report["artifacts"][0]
        self.assertEqual(row["sha256"], sha(fresh))
        self.assertEqual(row["size"], 3)
        self.assertFalse(row["preexisting"])
        self.assertTrue(row["changed_this_run"])
        self.assertTrue(row["observed_fresh_this_run"])

    def test_no_fresh_glob_and_missing_exact_file_are_unverified(self):
        check = {"kind": "command", "exit_code": 0, "artifact_globs": ["none/**/*.json"]}
        self.assertEqual(self.run_check(check)["status"], "UNVERIFIED")
        self.assertEqual(self.run_check({"kind": "command", "exit_code": 0,
                                        "artifacts": ["missing.json"]})["status"], "UNVERIFIED")

    def test_unsafe_and_escaping_matches_refused(self):
        check = {"kind": "command", "exit_code": 0, "artifact_globs": ["../*.json"]}
        self.assertEqual(self.run_check(check)["status"], "UNVERIFIED")
        outside = Path(self.tmp.name).parent / (Path(self.tmp.name).name + "-outside.json")
        outside.write_text("x")
        try:
            (self.repo / "escape.json").symlink_to(outside)
            report = self.run_check({"kind": "command", "exit_code": 0,
                                     "artifact_globs": ["escape.json"]})
            self.assertEqual(report["status"], "UNVERIFIED")
            self.assertFalse(report["artifacts"])
        except (OSError, NotImplementedError):
            self.skipTest("symlink creation unavailable")

    def test_command_without_declared_artifacts_still_passes(self):
        self.assertEqual(self.run_check({"kind": "command", "exit_code": 0})["status"], "PASS")


if __name__ == "__main__":
    unittest.main()
