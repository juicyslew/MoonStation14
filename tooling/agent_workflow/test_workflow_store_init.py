import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from workflow_store import APPLICATION_ID, SCHEMA_VERSION, ProtocolError, StoreError, WorkflowStore, canonical_json, validate


class WorkflowStoreInitializationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name) / "store.sqlite"

    def tearDown(self):
        self.tmp.cleanup()

    def test_validation_requires_strict_integer_json_before_schema(self):
        base = {"contract_version": 1, "status": "ACCEPTED", "run_id": "run_x", "generation": 1, "next_action": "WAIT_FOR_RESULT"}
        validate(base, "submission_response")
        self.assertEqual(canonical_json({"unicode": "é☃"}), '{"unicode":"\\u00e9\\u2603"}')
        for bad in (dict(base, contract_version=1.0), dict(base, generation=1.0), {1: "x"}, (base,), {"x": float("nan")}, {"x": float("inf")}):
            with self.subTest(bad=bad), self.assertRaises(ProtocolError):
                validate(bad)
        with self.assertRaises(ProtocolError):
            canonical_json(10 ** 10000)

    def test_empty_init_reopen_and_connection_pragmas(self):
        store = WorkflowStore(self.path)
        db = store._connect()
        self.assertEqual(db.execute("PRAGMA journal_mode").fetchone()[0].lower(), "wal")
        self.assertEqual(db.execute("PRAGMA foreign_keys").fetchone()[0], 1)
        self.assertEqual(db.execute("PRAGMA synchronous").fetchone()[0], 2)
        db.close()
        WorkflowStore(self.path)

    def _snapshot(self):
        db = sqlite3.connect(self.path)
        try:
            return (self.path.read_bytes(), db.execute("PRAGMA application_id").fetchone()[0], db.execute("PRAGMA user_version").fetchone()[0],
                    tuple(db.execute("SELECT type,name,sql FROM sqlite_master ORDER BY type,name")))
        finally:
            db.close()

    def _reject_unchanged(self):
        before = self._snapshot()
        with self.assertRaises(StoreError):
            WorkflowStore(self.path)
        self.assertEqual(self._snapshot(), before)

    def test_foreign_and_unsupported_identifiers_reject_without_mutation(self):
        for app, version, table in ((77, 0, False), (APPLICATION_ID, 2, True), (0, 1, True), (APPLICATION_ID, 0, True)):
            with self.subTest(app=app, version=version):
                db = sqlite3.connect(self.path)
                db.execute(f"PRAGMA application_id={app}"); db.execute(f"PRAGMA user_version={version}")
                if table: db.execute("CREATE TABLE foreign_data(value TEXT)")
                db.commit(); db.close()
                self._reject_unchanged()
                self.path.unlink()

    def test_identified_v1_missing_table_or_column_fails_closed(self):
        for alteration in ("DROP TABLE events", "ALTER TABLE runs RENAME COLUMN run_json TO old_run_json"):
            with self.subTest(alteration=alteration):
                WorkflowStore(self.path)
                db = sqlite3.connect(self.path); db.execute(alteration); db.commit(); db.close()
                self._reject_unchanged()
                for suffix in ("", "-wal", "-shm"):
                    candidate = Path(str(self.path) + suffix)
                    if candidate.exists(): candidate.unlink()


if __name__ == "__main__":
    unittest.main()
