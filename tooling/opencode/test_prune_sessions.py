import importlib.util
import pathlib
import sqlite3
import sys
import tempfile
import unittest
import urllib.error
from unittest import mock

MODULE = pathlib.Path(__file__).with_name("prune_sessions.py")
sys.path.insert(0, str(MODULE.parent))
spec = importlib.util.spec_from_file_location("prune_sessions", MODULE)
prune = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prune)


class PrunePlanTests(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.executescript("CREATE TABLE session_pending(session_id); CREATE TABLE session_inbox(session_id);")

    def tearDown(self):
        self.db.close()

    def test_top_thirty_and_root_exclusion(self):
        root = "ses_root"
        rows = [(root, None, 1, 1, 1, None, None, None, "succeeded")]
        rows.extend((f"ses_{n}", root, 1, n, n, None, None, None, "succeeded") for n in range(40))
        by_id, _, trees = prune.descendants_by_root(rows, (root,))
        kept = prune.keep_ids(trees, by_id, 30)[root]
        self.assertEqual(len(kept), 30)
        self.assertNotIn(root, trees[root])
        self.assertIn("ses_39", kept)

    def test_pending_and_keeper_ancestor_are_preserved(self):
        root, parent = "root", "parent"
        rows = [(root, None, 1, 1, 1, None, None, None, "succeeded"),
                (parent, root, 1, 1, 1, None, None, None, "succeeded"),
                ("active-work", parent, 1, 1, 1, None, None, None, "succeeded")]
        self.db.execute("INSERT INTO session_pending VALUES (?)", ("active-work",))
        by_id, _, trees = prune.descendants_by_root(rows, (root,))
        top = prune.keep_ids(trees, by_id, 1)
        safety = {"active-work"} | set(top[root])
        self.assertIn(parent, prune.ancestors(safety & set(trees[root]), by_id))
        self.assertTrue(prune.pending(self.db, "active-work"))

    def test_missing_explicit_root_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "required root missing"):
            prune.descendants_by_root([], ("missing",))

    def test_only_matching_native_not_found_is_typed(self):
        import io
        error = lambda payload: urllib.error.HTTPError("url", 404, "missing", {}, io.BytesIO(payload))
        with self.assertRaises(prune.session_store.SessionNotFoundError):
            prune.session_store._raise_http_error(error(b'{"_tag":"SessionNotFoundError","sessionID":"ses_x"}'), "ses_x")
        for payload, sid in ((b"not json", "ses_x"),
                             (b'{"_tag":"Other","sessionID":"ses_x"}', "ses_x"),
                             (b'{"_tag":"SessionNotFoundError","sessionID":"ses_y"}', "ses_x")):
            with self.assertRaises(urllib.error.HTTPError):
                prune.session_store._raise_http_error(error(payload), sid)

    def test_cli_accounts_for_session_disappearing_before_info(self):
        self._run_vanished_candidate("before_info")

    def test_cli_accounts_for_typed_not_found_after_info(self):
        self._run_vanished_candidate("typed_404")

    def _run_vanished_candidate(self, race):
        with tempfile.TemporaryDirectory() as temp:
            repo = pathlib.Path(temp); dbpath = repo / "sessions.sqlite"
            w = sqlite3.connect(dbpath)
            w.executescript("CREATE TABLE session_v2(id,parent_id,time_created,time_updated,time_idle,time_viewed,time_compacting,time_suspended,idle_outcome); CREATE TABLE session_pending(session_id); CREATE TABLE session_inbox(session_id);")
            w.execute("INSERT INTO session_v2 VALUES ('ses_root',NULL,1,100,100,NULL,NULL,NULL,'succeeded')")
            w.execute("INSERT INTO session_v2 VALUES ('ses_gone','ses_root',1,1,1,NULL,NULL,NULL,'succeeded')")
            w.execute("INSERT INTO session_v2 VALUES ('ses_keep','ses_root',1,90,90,NULL,NULL,NULL,'succeeded')")
            w.commit(); w.close()
            workdb = repo / "workflow.sqlite"; w = sqlite3.connect(workdb)
            w.executescript("CREATE TABLE tasks(task_json); CREATE TABLE attempts(attempt_json); CREATE TABLE runs(run_json); CREATE TABLE resources(identity_proof); CREATE TABLE deliveries(recipient_json);"); w.commit(); w.close()
            config = repo / "config.json"; config.write_text(__import__("json").dumps({"database": str(workdb)}), encoding="utf-8")
            (repo / ".opencode/workflow").mkdir(parents=True)
            (repo / ".opencode/workflow/current-installation.json").write_text(__import__("json").dumps({"config": str(config)}), encoding="utf-8")
            tabs = repo / "tabs.json"; tabs.write_text('{"global":{"tabs":[]},"cwd":{}}', encoding="utf-8")
            service = repo / "service.json"; service.write_text('{"url":"http://invalid","password":"fixture"}', encoding="utf-8")
            class FakeAPI:
                def __init__(self, _): self.did_race = False
                def active(self):
                    if race == "before_info" and not self.did_race:
                        self.did_race = True; w = sqlite3.connect(dbpath); w.execute("DELETE FROM session_v2 WHERE id='ses_gone'"); w.commit(); w.close()
                    return set()
                def info(self, sid):
                    if sid == "ses_gone" and race == "typed_404":
                        w = sqlite3.connect(dbpath); w.execute("DELETE FROM session_v2 WHERE id=?", (sid,)); w.commit(); w.close()
                        raise prune.session_store.SessionNotFoundError(sid)
                    return {"id": sid, "parentID": "ses_root", "time": {"updated": 1}}
                def forms(self, _): return []
                def permissions(self, _): return []
                def remove(self, sid):
                    w = sqlite3.connect(dbpath)
                    self.assert_leaf(w, sid)
                    w.execute("DELETE FROM session_v2 WHERE id=?", (sid,)); w.commit(); w.close()
                @staticmethod
                def assert_leaf(w, sid):
                    if w.execute("SELECT 1 FROM session_v2 WHERE parent_id=?", (sid,)).fetchone(): raise AssertionError("non-leaf removal")
            argv = ["prune", "--root", "ses_root", "--keep", "1", "--repo", str(repo), "--db", str(dbpath), "--tabs", str(tabs), "--service", str(service), "--audit-dir", str(repo / "audit"), "--execute"]
            with mock.patch.object(prune.session_store, "API", FakeAPI), mock.patch("sys.argv", argv), mock.patch("builtins.print") as output:
                prune.main()
            report = __import__("json").loads(output.call_args.args[0])
            self.assertEqual(report["already_removed"], 1)
            self.assertEqual(report["deleted_candidates"], 0)
            check = sqlite3.connect(dbpath)
            self.assertEqual(check.execute("SELECT count(*) FROM session_v2 WHERE id='ses_root'").fetchone()[0], 1)
            self.assertEqual(check.execute("SELECT count(*) FROM session_v2 WHERE id='ses_keep'").fetchone()[0], 1)
            check.close()

    def test_plan_receives_cli_roots_and_keeps_exact_top_thirty(self):
        root = "ses_root"
        rows = [(root, None, 1, 1, 1, None, None, None, "succeeded")]
        rows.extend((f"ses_{n}", root, 1, n, n, None, None, None, "succeeded") for n in range(35))
        _, _, descendants, top, keepers, targets = prune.plan(rows, self.db, set(), set(), (root,), 30)
        self.assertEqual(len(descendants[root]), 35)
        self.assertEqual(len(top[root]), 30)
        self.assertEqual(len(targets), 5)
        self.assertIn(root, keepers)

    def test_top_level_root_required(self):
        rows = [("root", None, 1, 1, 1, None, None, None, "succeeded"),
                ("child", "root", 1, 1, 1, None, None, None, "succeeded")]
        with self.assertRaisesRegex(ValueError, "root must be a session-tree root"):
            prune.descendants_by_root(rows, ("child",))

    def test_live_guards_resolve_current_pointer_and_metadata(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = pathlib.Path(temp)
            (repo / ".opencode/workflow").mkdir(parents=True)
            workdb = repo / "workflow.sqlite"
            w = sqlite3.connect(workdb)
            w.executescript("CREATE TABLE tasks(task_json); CREATE TABLE attempts(attempt_json); CREATE TABLE runs(run_json); CREATE TABLE resources(identity_proof); CREATE TABLE deliveries(recipient_json);")
            w.execute("INSERT INTO tasks VALUES (?)", ('{"sessionID":"ses_work"}',))
            w.commit(); w.close()
            config = repo / "config.json"
            config.write_text(__import__("json").dumps({"database": str(workdb)}), encoding="utf-8")
            (repo / ".opencode/workflow/current-installation.json").write_text(__import__("json").dumps({"config": str(config)}), encoding="utf-8")
            tabs = repo / "tabs.json"
            tabs.write_text('{"global":{"tabs":[{"sessionID":"ses_tab"}]},"cwd":{}}', encoding="utf-8")
            self.assertEqual(prune.session_store.live_guards(repo, tabs), {"ses_tab", "ses_work"})
            tabs.write_text('{"global":{"tabs":[{"sessionID":"ses_new"}]},"cwd":{}}', encoding="utf-8")
            self.assertEqual(prune.session_store.live_guards(repo, tabs), {"ses_new", "ses_work"})

    def test_invalid_workflow_schema_fails_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            repo = pathlib.Path(temp)
            (repo / ".opencode/workflow").mkdir(parents=True)
            workdb = repo / "workflow.sqlite"
            sqlite3.connect(workdb).close()
            config = repo / "config.json"
            config.write_text(__import__("json").dumps({"database": str(workdb)}), encoding="utf-8")
            (repo / ".opencode/workflow/current-installation.json").write_text(__import__("json").dumps({"config": str(config)}), encoding="utf-8")
            tabs = repo / "tabs.json"
            tabs.write_text('{"global":{"tabs":[]},"cwd":{}}', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "schema incomplete"):
                prune.session_store.live_guards(repo, tabs)

    def test_cli_dry_run_executes_plan_without_remove(self):
        # CLI argument/root plumbing is exercised without a server or real deletion.
        with tempfile.TemporaryDirectory() as temp:
            repo = pathlib.Path(temp)
            dbpath = repo / "sessions.sqlite"
            w = sqlite3.connect(dbpath)
            w.executescript("CREATE TABLE session_v2(id,parent_id,time_created,time_updated,time_idle,time_viewed,time_compacting,time_suspended,idle_outcome); CREATE TABLE session_pending(session_id); CREATE TABLE session_inbox(session_id);")
            w.execute("INSERT INTO session_v2 VALUES ('ses_root',NULL,1,1,1,NULL,NULL,NULL,'succeeded')")
            for n in range(35):
                w.execute("INSERT INTO session_v2 VALUES (?,?,1,?, ?,NULL,NULL,NULL,'succeeded')", (f"ses_{n}", "ses_root", n + 10, n + 10))
            w.commit(); w.close()
            workdb = repo / "workflow.sqlite"
            w = sqlite3.connect(workdb)
            w.executescript("CREATE TABLE tasks(task_json); CREATE TABLE attempts(attempt_json); CREATE TABLE runs(run_json); CREATE TABLE resources(identity_proof); CREATE TABLE deliveries(recipient_json);")
            w.commit(); w.close()
            config = repo / "config.json"; config.write_text(__import__("json").dumps({"database": str(workdb)}), encoding="utf-8")
            (repo / ".opencode/workflow").mkdir(parents=True)
            (repo / ".opencode/workflow/current-installation.json").write_text(__import__("json").dumps({"config": str(config)}), encoding="utf-8")
            tabs = repo / "tabs.json"; tabs.write_text('{"global":{"tabs":[]},"cwd":{}}', encoding="utf-8")
            service = repo / "service.json"; service.write_text('{"url":"http://invalid","password":"fixture"}', encoding="utf-8")
            class FakeAPI:
                deleted = []
                def __init__(self, _service): self.dbpath = dbpath
                def active(self): return set()
                def info(self, sid): return {"id": sid, "parentID": "ses_root", "time": {"updated": 1}}
                def forms(self, _sid): return []
                def permissions(self, _sid): return []
                def remove(self, sid):
                    w = sqlite3.connect(self.dbpath)
                    if w.execute("SELECT 1 FROM session_v2 WHERE parent_id=?", (sid,)).fetchone():
                        raise AssertionError("API removal must only receive leaves")
                    w.execute("DELETE FROM session_v2 WHERE id=?", (sid,)); w.commit(); w.close()
                    self.deleted.append(sid)
            argv = ["prune", "--root", "ses_root", "--repo", str(repo), "--db", str(dbpath), "--tabs", str(tabs), "--service", str(service), "--audit-dir", str(repo / "audit")]
            with mock.patch.object(prune.session_store, "API", FakeAPI), mock.patch("sys.argv", argv), mock.patch("builtins.print") as output:
                prune.main()
            report = __import__("json").loads(output.call_args.args[0])
            self.assertEqual(report["roots"]["ses_root"]["before"], 35)
            self.assertEqual(report["planned_candidates"], 5)
            check = sqlite3.connect(dbpath)
            self.assertEqual(check.execute("SELECT count(*) FROM session_v2").fetchone()[0], 36)
            check.close()
            FakeAPI.deleted.clear()
            with mock.patch.object(prune.session_store, "API", FakeAPI), mock.patch("sys.argv", argv + ["--execute"]), mock.patch("builtins.print") as output:
                prune.main()
            report = __import__("json").loads(output.call_args.args[0])
            self.assertEqual(report["deleted_candidates"], 5)
            self.assertEqual(len(FakeAPI.deleted), 5)
            self.assertNotIn("ses_root", FakeAPI.deleted)
            check = sqlite3.connect(dbpath)
            self.assertEqual(check.execute("SELECT count(*) FROM session_v2").fetchone()[0], 31)
            check.close()


if __name__ == "__main__":
    unittest.main()
