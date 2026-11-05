"""Small, read-only/OpenCode API helpers shared by session maintenance tools."""
from __future__ import annotations

import datetime as dt
import json
import pathlib
import re
import re
import sqlite3
import urllib.parse
import urllib.request
import urllib.error


class SessionNotFoundError(Exception):
    """OpenCode's typed, identity-bearing session-not-found response."""


def _raise_http_error(error, sid):
    if error.code == 404:
        try:
            value = json.loads(error.read())
        except (ValueError, OSError):
            value = None
        if (isinstance(value, dict) and value.get("_tag") == "SessionNotFoundError"
                and value.get("sessionID") == sid):
            raise SessionNotFoundError(sid) from None
    raise error


def tab_session_ids(tabs):
    if not isinstance(tabs, dict) or not isinstance(tabs.get("global"), dict) or not isinstance(tabs.get("cwd"), dict):
        raise ValueError("unsupported tabs.json shape; expected global.tabs and cwd[path].tabs")
    lists = [tabs["global"].get("tabs")]
    if not isinstance(lists[0], list) or any(not isinstance(v, dict) or not isinstance(v.get("tabs"), list) for v in tabs["cwd"].values()):
        raise ValueError("unsupported tabs.json tab lists")
    lists.extend(v["tabs"] for v in tabs["cwd"].values())
    return set(re.findall(r"ses_[A-Za-z0-9]+", json.dumps(lists)))


def wire_millis(value):
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return int(value)
    if isinstance(value, str):
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("timezone required for ISO timestamp")
        return int(parsed.timestamp() * 1000)
    raise ValueError("unsupported wire timestamp")


def has_unremoved_children(db_child_ids, deleted_ids, execute):
    return bool(set(db_child_ids) if execute else set(db_child_ids) - set(deleted_ids))


def open_db(path):
    db = sqlite3.connect(pathlib.Path(path).resolve().as_uri() + "?mode=ro", uri=True)
    db.execute("PRAGMA query_only=ON")
    required = {"session_v2", "session_pending", "session_inbox"}
    tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    if not required <= tables:
        db.close()
        raise ValueError("unexpected database schema: required session tables absent")
    cols = {r[1] for r in db.execute("PRAGMA table_info(session_v2)")}
    if not {"id", "parent_id", "time_created", "time_updated", "time_idle", "time_viewed", "time_compacting", "time_suspended", "idle_outcome"} <= cols:
        db.close()
        raise ValueError("unexpected session_v2 schema")
    for table in ("session_pending", "session_inbox"):
        if "session_id" not in {r[1] for r in db.execute(f"PRAGMA table_info({table})")}:
            db.close()
            raise ValueError(f"unexpected {table} schema")
    return db


def session_rows(db):
    return db.execute("SELECT id,parent_id,time_created,time_updated,time_idle,time_viewed,time_compacting,time_suspended,idle_outcome FROM session_v2").fetchall()


def live_guards(repo_root, tabs_path=None):
    """Resolve saved tabs and session refs from the active installation metadata."""
    repo_root = pathlib.Path(repo_root).resolve()
    pointer = json.loads((repo_root / ".opencode/workflow/current-installation.json").read_text(encoding="utf-8"))
    config = json.loads(pathlib.Path(pointer["config"]).read_text(encoding="utf-8"))
    workflow_db = pathlib.Path(config["database"])
    tabs_path = pathlib.Path(tabs_path or pathlib.Path.home() / ".local/state/opencode/latest/tui/tabs.json")
    tabs = json.loads(tabs_path.read_text(encoding="utf-8"))
    saved_tabs = tab_session_ids(tabs)
    if not workflow_db.is_file():
        raise ValueError("current workflow database unavailable")
    db = sqlite3.connect(workflow_db.resolve().as_uri() + "?mode=ro", uri=True)
    try:
        db.execute("PRAGMA query_only=ON")
        needed = {"tasks", "attempts", "runs", "resources", "deliveries"}
        tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        if not needed <= tables:
            raise ValueError("current workflow database schema incomplete")
        selected = (("tasks", "task_json"), ("attempts", "attempt_json"), ("runs", "run_json"),
                    ("resources", "identity_proof"), ("deliveries", "recipient_json"))
        refs = _session_refs(config)
        for table, column in selected:
            columns = {r[1] for r in db.execute(f'PRAGMA table_info("{table}")')}
            if column not in columns:
                raise ValueError(f"current workflow table {table} missing {column}")
            for (raw,) in db.execute(f'SELECT "{column}" FROM "{table}"'):
                if raw is None:
                    continue
                value = json.loads(raw) if isinstance(raw, str) else raw
                refs.update(_session_refs(value))
    finally:
        db.close()
    return saved_tabs | refs


def _session_refs(value):
    found = set()
    if isinstance(value, dict):
        for key, child in value.items():
            normalized = re.sub(r"[^a-z]", "", key.lower()) if isinstance(key, str) else ""
            credential_key = any(part in normalized for part in ("credential", "password", "secret", "token", "auth"))
            if credential_key:
                continue
            if "session" in normalized and normalized.endswith("id") and isinstance(child, str) and re.fullmatch(r"ses_[A-Za-z0-9]+", child):
                found.add(child)
            else:
                found.update(_session_refs(child))
    elif isinstance(value, list):
        for child in value:
            found.update(_session_refs(child))
    return found


class API:
    def __init__(self, service):
        if not isinstance(service, dict) or not all(isinstance(service.get(k), str) and service[k] for k in ("url", "password")):
            raise ValueError("unsupported service.json shape")
        self.base, self.password = service["url"].rstrip("/"), service["password"]

    def get(self, path):
        import base64
        req = urllib.request.Request(self.base + path)
        req.add_header("Authorization", "Basic " + base64.b64encode(("opencode:" + self.password).encode()).decode())
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(req, timeout=10) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            parts = path.split("?")[0].split("/")
            _raise_http_error(error, urllib.parse.unquote(parts[3]) if len(parts) > 3 else "")

    def active(self):
        status, body = self.get("/api/session/active")
        data = json.loads(body)
        if status != 200 or not isinstance(data, dict) or not isinstance(data.get("data"), dict):
            raise ValueError("unexpected active endpoint response")
        return set(data["data"])

    def _list(self, sid, suffix=""):
        status, body = self.get("/api/session/" + urllib.parse.quote(sid, safe="") + suffix)
        data = json.loads(body)
        if status != 200 or not isinstance(data, dict) or not isinstance(data.get("data"), list):
            raise ValueError("unexpected session list response")
        return data["data"]

    def info(self, sid):
        status, body = self.get("/api/session/" + urllib.parse.quote(sid, safe=""))
        data = json.loads(body)
        if status != 200 or not isinstance(data, dict) or not isinstance(data.get("data"), dict):
            raise ValueError("unexpected session info response")
        return data["data"]

    def forms(self, sid): return self._list(sid, "/form")
    def permissions(self, sid): return self._list(sid, "/permission")

    def remove(self, sid):
        import base64
        req = urllib.request.Request(self.base + "/api/session/" + urllib.parse.quote(sid, safe=""), method="DELETE")
        req.add_header("Authorization", "Basic " + base64.b64encode(("opencode:" + self.password).encode()).decode())
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(req, timeout=20) as response:
                if response.status != 204:
                    raise RuntimeError(f"DELETE returned {response.status}, expected 204")
        except urllib.error.HTTPError as error:
            _raise_http_error(error, sid)
