"""Guarded administrative deployment primitives for the workflow coordinator.

This command intentionally separates validation from effects.  It does not
manufacture workflow history or infer that an empty-looking UI is quiescent.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
import os
import subprocess
import sys
import time
import uuid
import tempfile
import urllib.request
import urllib.parse
from pathlib import Path

try:
    import workflow_coordinator_package as package
except ModuleNotFoundError:
    from tooling.agent_workflow import workflow_coordinator_package as package


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def finalize_shared_config_index_transition(roots, plan: dict, receipt: dict, stage: Path) -> None:
    """Remove only published generated configs from the five reviewed Git indexes."""
    import workflow_coordinator_bindings as bindings
    targets = {"main", *(f"MoonStation14-agent-0{i}" for i in range(1, 5))}
    if set(roots) != targets or {r.get("target") for r in plan.get("targets", [])} != targets:
        raise ValueError("shared config index transition must name all five roots")
    expected_digests = {r["target"]: r["expected_index_digest"] for r in plan["targets"]}
    receipt_rows = {(r.get("target"), r.get("path")): r for r in receipt.get("files", []) if not r.get("deleted")}
    prepared = []
    for name in sorted(targets):
        root = Path(roots[name]).resolve()
        config = root / "opencode.jsonc"
        config_row = receipt_rows.get((name, "opencode.jsonc"))
        overlay_row = receipt_rows.get((name, ".opencode/workflow/opencode-local.json"))
        ignore_row = receipt_rows.get((name, ".gitignore"))
        if (not config_row or not overlay_row or not ignore_row or
                config_row.get("new_sha256") != sha256(config.read_bytes()) or
                overlay_row.get("new_sha256") != sha256((root / ".opencode/workflow/opencode-local.json").read_bytes()) or
                ignore_row.get("new_sha256") != sha256((root / ".gitignore").read_bytes())):
            raise ValueError(f"published shared config materialization differs: {name}")
        ignored_lines = set((root / ".gitignore").read_text(encoding="utf-8").splitlines())
        if not {"/opencode.jsonc", "/.opencode/workflow/"}.issubset(ignored_lines):
            raise ValueError(f"required generated-config ignore rules missing: {name}")
        unmerged = subprocess.run(["git", "-C", str(root), "diff", "--name-only", "--diff-filter=U", "-z"],
                                  check=True, stdout=subprocess.PIPE).stdout
        if unmerged:
            raise ValueError(f"unmerged git index entries: {name}")
        merge_head = subprocess.run(["git", "-C", str(root), "rev-parse", "--git-path", "MERGE_HEAD"],
                                    check=True, stdout=subprocess.PIPE).stdout.decode().strip()
        merge_path = Path(merge_head)
        if not merge_path.is_absolute(): merge_path = root / merge_path
        if merge_path.exists():
            raise ValueError(f"repository merge is in progress: {name}")
        tracked = subprocess.run(["git", "-C", str(root), "ls-files", "--error-unmatch", "--", "opencode.jsonc"],
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if bindings._index_digest(root) != expected_digests[name]:
            raise ValueError(f"git index drift: {name}")
        if tracked.returncode != 0 and subprocess.run(["git", "-C", str(root), "check-ignore", "-q", "--", "opencode.jsonc"]).returncode != 0:
            raise ValueError(f"generated config is neither tracked nor ignored: {name}")
        index_path = Path(subprocess.run(["git", "-C", str(root), "rev-parse", "--git-path", "index"],
                                         check=True, stdout=subprocess.PIPE).stdout.decode().strip())
        if not index_path.is_absolute(): index_path = root / index_path
        prepared.append((name, root, index_path, tracked.returncode == 0))
    mutation_rows = []
    for name, root, index_path, tracked in prepared:
        if not tracked: continue
        backup = stage / "control/index-backups" / name
        backup.parent.mkdir(parents=True, exist_ok=True)
        backup.write_bytes(index_path.read_bytes())
        journal = stage / "control/index-transition" / (name + ".json")
        journal.parent.mkdir(parents=True, exist_ok=True)
        before = subprocess.run(["git", "-C", str(root), "ls-files", "--stage", "-z"], check=True, stdout=subprocess.PIPE).stdout
        package._atomic_write(journal, json.dumps({"target": name, "state": "before", "index_backup": str(backup), "index_sha256": sha256(index_path.read_bytes())}, sort_keys=True).encode(), exclusive=True)
        mutation_rows.append((name, root, before, journal, backup))
    for name, root, before, journal, backup in mutation_rows:
        config = root / "opencode.jsonc"
        original = config.read_bytes()
        config_row = receipt_rows[(name, "opencode.jsonc")]
        if sha256(original) != config_row["new_sha256"]:
            raise ValueError(f"published shared config changed immediately before index transition: {name}")
        overlay_row = receipt_rows[(name, ".opencode/workflow/opencode-local.json")]
        ignore_row = receipt_rows[(name, ".gitignore")]
        overlay = root / ".opencode/workflow/opencode-local.json"
        ignore_file = root / ".gitignore"
        if (sha256(overlay.read_bytes()) != overlay_row["new_sha256"] or
                sha256(ignore_file.read_bytes()) != ignore_row["new_sha256"] or
                not {"/opencode.jsonc", "/.opencode/workflow/"}.issubset(
                    set(ignore_file.read_text(encoding="utf-8").splitlines()))):
            raise ValueError(f"published overlay or ignore rules changed immediately before index transition: {name}")
        expected = expected_digests[name]
        import workflow_coordinator_bindings as bindings
        if bindings._index_digest(root) != expected:
            raise ValueError(f"git index drift immediately before transition: {name}")
        head = subprocess.run(["git", "-C", str(root), "rev-parse", "HEAD:opencode.jsonc"],
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        index_blob = subprocess.run(["git", "-C", str(root), "rev-parse", ":opencode.jsonc"],
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if head.returncode != 0 or index_blob.returncode != 0 or head.stdout.strip() != index_blob.stdout.strip():
            raise ValueError(f"staged config conflict prevents safe untrack: {name}")
        subprocess.run(["git", "-C", str(root), "rm", "--cached", "--", "opencode.jsonc"], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        after = subprocess.run(["git", "-C", str(root), "ls-files", "--stage", "-z"], check=True, stdout=subprocess.PIPE).stdout
        parse = lambda raw: sorted(x for x in raw.split(b"\0") if x and not x.split(b"\t", 1)[1] == b"opencode.jsonc")
        if parse(before) != parse(after) or config.read_bytes() != original:
            raise ValueError(f"index transition verification failed: {name}")
        ignored = subprocess.run(["git", "-C", str(root), "check-ignore", "-q", "--", "opencode.jsonc"]).returncode == 0
        if not ignored: raise ValueError(f"generated config is not ignored: {name}")
        package._atomic_write(journal, json.dumps({"target": name, "state": "after", "index_backup": str(backup), "index_sha256_after": sha256((root / subprocess.run(["git", "-C", str(root), "rev-parse", "--git-path", "index"], check=True, stdout=subprocess.PIPE).stdout.decode().strip()).read_bytes())}, sort_keys=True).encode())


def canonical_pointer_bytes(previous_pointer: bytes, receipt: Path, manifest_sha256: str) -> bytes:
    """Construct the publisher's pointer update in memory; never resurrect history."""
    value = json.loads(previous_pointer)
    value["previous_receipt"] = value["receipt"]
    value["receipt"] = str(receipt.resolve())
    value["scope"] = "coordinator package published; awaiting activation"
    value["coordinator_package"] = {"manifest_sha256": manifest_sha256,
                                    "status": "published-awaiting-activation"}
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def verify_pointer(path: Path, expected_hash: str, receipt: Path) -> None:
    """CAS guard; an existing pointer is accepted only when owned by this receipt."""
    if not path.is_file():
        raise ValueError("publication pointer missing; candidate bytes require explicit recovery review")
    data = path.read_bytes()
    if sha256(data) != expected_hash:
        raise ValueError("publication pointer hash differs from reviewed expectation")
    value = json.loads(data)
    if Path(value.get("receipt", "")).resolve() != receipt.resolve():
        raise ValueError("publication pointer does not name the supplied current receipt")


def logical_db_fingerprint(path: Path) -> str:
    """Hash canonical logical SQLite rows (not physical/WAL bytes) read-only."""
    return deployment_snapshot(path)["logical_fingerprint"]


_SNAPSHOT_TABLES = {"tasks", "attempts", "resources", "native_scopes", "outbox", "deliveries", "events"}
_JSON_COLUMNS = {"task_json", "attempt_json", "worker_identity_json", "recipient_json",
                 "transport_receipt", "protocol_ack"}


def deployment_snapshot(db_path: Path) -> dict:
    """Read canonical store rows from one read-only SQLite transaction."""
    uri = Path(db_path).resolve().as_uri() + "?mode=ro"
    db = sqlite3.connect(uri, uri=True, isolation_level=None)
    try:
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA query_only=ON")
        db.execute("BEGIN")
        tables = [r[0] for r in db.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
        if not _SNAPSHOT_TABLES.issubset(tables):
            raise ValueError("workflow store is missing required deployment tables")
        snapshot = {}
        canonical_rows = []
        for table in tables:
            quoted = '"' + table.replace('"', '""') + '"'
            columns = [r[1] for r in db.execute(f"PRAGMA table_info({quoted})")]
            rows = []
            for raw in db.execute(f"SELECT * FROM {quoted}"):
                row = dict(raw)
                for column in _JSON_COLUMNS.intersection(row):
                    if row[column] is not None:
                        try: row[column] = json.loads(row[column])
                        except (TypeError, json.JSONDecodeError): pass
                rows.append(row)
            rows.sort(key=lambda r: json.dumps(r, sort_keys=True, separators=(",", ":"), default=str))
            snapshot[table] = rows
            canonical_rows.append([table, columns, rows])
        encoded = json.dumps(canonical_rows, sort_keys=True, separators=(",", ":"),
                             ensure_ascii=False, default=str).encode("utf-8")
        snapshot["logical_fingerprint"] = sha256(encoded)
        return snapshot
    finally:
        db.close()


def assert_quiescent(snapshot: dict) -> None:
    """Fail closed on unsettled durable rows; permit untouched webpage deliveries only."""
    if not isinstance(snapshot, dict) or not _SNAPSHOT_TABLES.issubset(snapshot):
        raise ValueError("invalid workflow snapshot")
    for task in snapshot["tasks"]:
        if task.get("task_json", {}).get("state") not in ("COMPLETED", "CANCELLED"):
            raise ValueError("workflow task is not settled")
    for attempt in snapshot["attempts"]:
        value = attempt.get("attempt_json")
        if not isinstance(value, dict): raise ValueError("workflow attempt JSON is invalid")
        if value.get("phase") in ("DECIDED", "CLOSED"):
            continue
        raise ValueError("workflow attempt is active or unsettled")
    _assert_released_resources_scopes_deliveries(snapshot)
    cancellations = snapshot.get("task_cancellations", [])
    if any(row.get("state") != "ACKNOWLEDGED" for row in snapshot["outbox"]):
        try:
            import workflow_review_cancellation
            import workflow_native_cancellation
        except ModuleNotFoundError:
            from tooling.agent_workflow import workflow_review_cancellation
            from tooling.agent_workflow import workflow_native_cancellation
        def decoded(value):
            if isinstance(value, str):
                try: return json.loads(value)
                except json.JSONDecodeError: return None
            return value
        tasks = {row.get("id"): decoded(row.get("task_json")) for row in snapshot["tasks"]}
        events = {row.get("id"): row for row in snapshot["events"]}
        for row in snapshot["outbox"]:
            if row.get("state") == "ACKNOWLEDGED": continue
            event = events.get(row.get("event_id"))
            payload = decoded(event.get("payload")) if event else None
            if not isinstance(event, dict) or not isinstance(payload, dict):
                raise ValueError("workflow outbox contains pending delivery")
            public_event = dict(event, event_id=event.get("id"), payload=payload)
            task_id = payload.get("task_id")
            task_value = tasks.get(task_id)
            task = dict(task_value, task_id=task_id) if isinstance(task_value, dict) else None
            cancel_row = next((c for c in cancellations if c.get("task_id") == task_id), None)
            cancel = decoded(cancel_row.get("proof_json")) if cancel_row else None
            if isinstance(cancel, dict) and "proof" not in cancel:
                cancel = {"confirmed": cancel_row.get("confirmed"), "proof": cancel}
            resources = [r for r in snapshot["resources"] if r.get("task_id") == task_id]
            native_claim = next((c for c in snapshot.get("native_claims", []) if c.get("task_id") == task_id), None)
            native_scope = next((c for c in snapshot.get("native_scopes", []) if c.get("task_id") == task_id), None)
            settled = task is not None and event.get("generation") == task.get("evaluation_generation") and (
                (task.get("kind") == "REVIEW" and workflow_review_cancellation.cancelled_review_readiness_settled(
                    task, public_event, cancel, resources)) or
                (task.get("kind") == "CHECK" and workflow_native_cancellation.cancelled_native_readiness_settled(
                    task, public_event, cancel, resources, native_claim, native_scope)))
            if row.get("state") not in ("PENDING", "CLAIMED") or not settled:
                raise ValueError("workflow outbox contains pending delivery")


def _assert_released_resources_scopes_deliveries(snapshot: dict) -> None:
    for row in snapshot["resources"]:
        if row.get("state") != "RELEASED": raise ValueError("workflow resource is not released")
    for row in snapshot["native_scopes"]:
        if row.get("state") != "CLEANED": raise ValueError("native scope is not cleaned")
    for row in snapshot["deliveries"]:
        target, state = row.get("target_kind"), row.get("state")
        if target == "AUTHOR":
            if state != "ACKNOWLEDGED" or row.get("protocol_ack") is None:
                raise ValueError("author delivery is not acknowledged")
        elif target == "WEBPAGE" and state == "PENDING":
            untouched = (row.get("attempts") == 0 and row.get("claim_generation") == 0 and
                         all(row.get(k) is None for k in ("lease_token", "lease_until_ms", "transport_receipt", "protocol_ack")))
            event_id = row.get("event_id")
            outbox = [o for o in snapshot["outbox"] if o.get("event_id") == event_id]
            author_ack = any(d.get("event_id") == event_id and d.get("target_kind") == "AUTHOR" and
                             d.get("state") == "ACKNOWLEDGED" and d.get("protocol_ack") is not None
                             for d in snapshot["deliveries"])
            if not (untouched and len(outbox) == 1 and outbox[0].get("state") == "ACKNOWLEDGED" and author_ack):
                raise ValueError("webpage delivery is not an untouched passive projection")
        else:
                 raise ValueError("unknown or unsettled delivery target")


def assert_stalled_native_repair(snapshot: dict, run_id: str, *, identity_state=None) -> None:
    """Validate the one narrowly recoverable, pre-execution native failure.

    This deliberately does not relax ordinary deployment quiescence.  The only
    unsettled work admitted is one completed ERROR check and its linked queued
    native check; outbox deliveries must be their existing completion/readiness
    notifications.
    """
    if not isinstance(run_id, str) or not run_id:
        raise ValueError("repair run id is invalid")
    if identity_state is None:
        try: import workflow_native_worker
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_native_worker
        identity_state = workflow_native_worker.native_identity_state
    if not isinstance(snapshot, dict) or not _SNAPSHOT_TABLES.issubset(snapshot):
        raise ValueError("invalid workflow snapshot")
    try: import workflow_store
    except ModuleNotFoundError: from tooling.agent_workflow import workflow_store
    def decoded(value):
        if isinstance(value, str):
            try: return json.loads(value)
            except json.JSONDecodeError as exc: raise ValueError("invalid stored JSON") from exc
        return value
    rows = snapshot["tasks"]
    tasks = [(r, decoded(r.get("task_json"))) for r in rows]
    checks = [(r, t) for r, t in tasks if isinstance(t, dict) and t.get("run_id") == run_id and t.get("kind") == "CHECK"]
    targets = [(r, t) for r, t in checks if t.get("state") == "COMPLETED" and
               isinstance(t.get("result"), dict) and t["result"].get("decision") == "ERROR"]
    if len(targets) != 1:
        raise ValueError("repair target must be the sole completed ERROR check")
    target_row, target = targets[0]
    incident_rows = [decoded(i.get("incident_json")) for i in snapshot.get("incidents", [])]
    incident = next((i for i in incident_rows if isinstance(i, dict) and i.get("task_id") == target_row.get("id")), None)
    if not incident or any(incident.get(k) != v for k, v in {
            "run_id": run_id, "evaluation_generation": target.get("evaluation_generation"),
            "code": "NATIVE_EXECUTION_ERROR", "message": "frozen source manifest integrity mismatch"}.items()):
        raise ValueError("repair target incident does not match the approved failure")
    generation = target.get("evaluation_generation")
    run = next((decoded(r.get("run_json")) for r in snapshot.get("runs", []) if r.get("id") == run_id), None)
    if not isinstance(run, dict) or run.get("current_attempt") != generation:
        raise ValueError("failed CHECK is not for the current run generation")
    attempt = next((decoded(a.get("attempt_json")) for a in snapshot["attempts"]
                    if a.get("run_id") == run_id and a.get("generation") == generation), None)
    if not isinstance(attempt, dict) or attempt.get("phase") != "CHECKING":
        raise ValueError("target attempt is not in CHECKING phase")
    queued = []
    for row, task in tasks:
        if not isinstance(task, dict): raise ValueError("invalid stored task")
        if row is target_row: continue
        if task.get("state") in ("COMPLETED", "CANCELLED"): continue
        if task.get("state") == "QUEUED" and task.get("kind") == "CHECK":
            task_run = next((decoded(r.get("run_json")) for r in snapshot.get("runs", []) if r.get("id") == task.get("run_id")), None)
            if isinstance(task_run, dict) and task_run.get("current_attempt") == task.get("evaluation_generation"):
                queued.append((row, task)); continue
        raise ValueError("workflow contains unrelated active or unsettled task")
    for row in snapshot.get("native_claims", []):
        saved = decoded(row.get("process_identity"))
        if identity_state(saved) != "dead":
            raise ValueError("native claim process identity is not confirmed dead")
    if any(r.get("task_id") == target_row.get("id") for r in snapshot.get("native_check_records", [])):
        raise ValueError("target already has native check records")
    for row in snapshot.get("attempts", []):
        value = row.get("attempt_json")
        if not isinstance(value, dict): raise ValueError("workflow attempt JSON is invalid")
        if value.get("phase") not in ("DECIDED", "CLOSED", "QUEUED") and not (
                row.get("run_id") == run_id and row.get("generation") == generation and value.get("phase") == "CHECKING"):
            raise ValueError("workflow attempt is active or unsettled")
    _assert_released_resources_scopes_deliveries(snapshot)
    allowed = {target_row.get("id"): (target_row, target)}
    allowed.update({r.get("id"): (r, t) for r, t in queued})
    events = {e.get("id"): e for e in snapshot["events"]}
    seen_completion = False
    for row in snapshot["outbox"]:
        if row.get("state") == "ACKNOWLEDGED": continue
        event = events.get(row.get("event_id"))
        if not isinstance(event, dict): raise ValueError("pending outbox event is missing")
        payload = decoded(event.get("payload"))
        if not isinstance(payload, dict) or workflow_store.digest(payload) != event.get("payload_digest"):
            raise ValueError("pending outbox event payload digest is invalid")
        kind = event.get("kind")
        task_id = payload.get("task_id")
        if task_id not in allowed or kind not in ("TASK_COMPLETED", "TASK_READY"):
            raise ValueError("workflow outbox contains unrelated pending delivery")
        task_row, task_json = allowed[task_id]
        expected_kind = "TASK_COMPLETED" if task_id == target_row.get("id") else "TASK_READY"
        if (kind != expected_kind or payload.get("kind") != kind or payload.get("event_id") != event.get("id") or
                event.get("run_id") != task_json.get("run_id") or event.get("generation") != task_json.get("evaluation_generation") or
                payload.get("run_id") != task_json.get("run_id") or
                payload.get("evaluation_generation") != task_json.get("evaluation_generation")):
            raise ValueError("outbox event binding does not match task")
        for key in ("assignment_generation", "input_digest"):
            if payload.get(key) != task_json.get(key): raise ValueError("outbox task binding mismatch")
        if task_id == target_row.get("id"):
            if payload.get("result_digest") != target_row.get("result_digest"):
                raise ValueError("completion result digest does not match task")
            seen_completion = True
        else:
            qattempt = next((a for a in snapshot["attempts"] if a.get("run_id") == task_json.get("run_id") and
                             a.get("generation") == task_json.get("evaluation_generation")), None)
            if qattempt is None or decoded(qattempt.get("attempt_json")).get("phase") != "QUEUED":
                raise ValueError("queued CHECK attempt is not QUEUED")
        if row.get("state") not in ("PENDING", "CLAIMED"):
            raise ValueError("repair notification has an invalid state")
    if not seen_completion: raise ValueError("target completion event is missing")


def assert_stalled_repair_snapshot_stable(before: dict, after: dict) -> bool:
    """Permit only legacy outbox lease bookkeeping while a repair drains/stops."""
    if set(before) != set(after): raise ValueError("workflow snapshot tables changed")
    changed = False
    for table in before:
        if table == "logical_fingerprint": continue
        left, right = before[table], after[table]
        if table != "outbox":
            if left != right: raise ValueError("workflow state changed during stalled repair")
            continue
        if len(left) != len(right): raise ValueError("outbox rows changed during stalled repair")
        old = {r.get("event_id"): r for r in left}
        new = {r.get("event_id"): r for r in right}
        if old.keys() != new.keys(): raise ValueError("outbox events changed during stalled repair")
        for event_id, original in old.items():
            current = new[event_id]
            original_state = original.get("state")
            if original_state == "ACKNOWLEDGED":
                if current != original:
                    raise ValueError("acknowledged outbox event changed during stalled repair")
                continue
            if original_state not in ("PENDING", "CLAIMED"):
                raise ValueError("outbox event has an invalid original state")
            for key in set(original) | set(current):
                if key in {"state", "claim_generation", "lease_until_ms", "attempts"}:
                    if original.get(key) != current.get(key): changed = True
                elif original.get(key) != current.get(key):
                    raise ValueError("outbox payload or identity changed during stalled repair")
            if current.get("state") not in ("PENDING", "CLAIMED"):
                raise ValueError("outbox event was acknowledged during stalled repair")
    return changed


def assert_queued_review_repair(snapshot: dict, run_id: str, *, identity_state=None,
                                expected_server=None) -> None:
    """Fail-closed admission for the narrowly scoped pre-dispatch review outage."""
    if not isinstance(run_id, str) or not run_id or not isinstance(snapshot, dict):
        raise ValueError("queued review repair identity is invalid")
    try:
        import workflow_store
        from workflow_store import WorkflowStore
    except ModuleNotFoundError:
        from tooling.agent_workflow import workflow_store
        WorkflowStore = workflow_store.WorkflowStore
    required = ("runs", "dependency_circuits", "native_claims", "artifacts", "task_dependency_pauses", "task_cancellations")
    if any(key not in snapshot for key in required):
        raise ValueError("queued review repair proof is unavailable")
    decoded = lambda x: json.loads(x) if isinstance(x, str) else x
    runs = {}
    for row in snapshot["runs"]:
        value=decoded(row.get("run_json"))
        current=row.get("current_generation")
        if not isinstance(value,dict) or isinstance(current,bool) or not isinstance(current,int) or current < 1:
            raise ValueError("run generation proof is invalid")
        value=dict(value)
        value["current_attempt"]=current
        runs[row.get("id")]=value
    run = runs.get(run_id)
    if not isinstance(run, dict): raise ValueError("repair run is missing")
    generation = run.get("current_attempt")
    tasks = [(r, decoded(r.get("task_json"))) for r in snapshot["tasks"]]
    reviews = [(r,t) for r,t in tasks if isinstance(t,dict) and t.get("run_id")==run_id and t.get("evaluation_generation")==generation and t.get("kind")=="REVIEW"]
    current_attempts=[decoded(a.get("attempt_json")) for a in snapshot["attempts"] if a.get("run_id")==run_id and a.get("generation")==generation]
    if len(current_attempts)!=1 or current_attempts[0].get("phase")!="REVIEWING": raise ValueError("current attempt is not REVIEWING")
    if not reviews or any(t.get("state") != "QUEUED" for _,t in reviews): raise ValueError("active reviews are not exclusively queued")
    checks = [(r,t) for r,t in tasks if isinstance(t,dict) and t.get("run_id")==run_id and t.get("evaluation_generation")==generation and t.get("kind")=="CHECK"]
    if not checks or any(t.get("state")!="COMPLETED" or not isinstance(t.get("result"),dict) or t["result"].get("decision")!="PASS" for _,t in checks): raise ValueError("current CHECK is not completed PASS")
    for row, task in tasks:
        if not isinstance(task,dict): raise ValueError("invalid task")
        if task.get("state") in ("COMPLETED", "CANCELLED"): continue
        if task.get("run_id")==run_id and task.get("evaluation_generation")==generation and task.get("kind")=="REVIEW" and task.get("state")=="QUEUED": continue
        tr = runs.get(task.get("run_id"))
        if task.get("kind")=="CHECK" and task.get("state")=="QUEUED" and isinstance(tr,dict) and tr.get("current_attempt")==task.get("evaluation_generation"):
            if not any(a.get("run_id")==task.get("run_id") and a.get("generation")==task.get("evaluation_generation") and decoded(a.get("attempt_json")).get("phase")=="QUEUED" for a in snapshot["attempts"]): raise ValueError("queued CHECK attempt is not QUEUED")
            continue
        raise ValueError("workflow contains unrelated active task")
    for attempt in snapshot["attempts"]:
        val=decoded(attempt.get("attempt_json"))
        if val.get("phase") not in ("DECIDED","CLOSED","QUEUED") and not (attempt.get("run_id")==run_id and attempt.get("generation")==generation and val.get("phase")=="REVIEWING"):
            raise ValueError("workflow contains active attempt")
    _assert_released_resources_scopes_deliveries(snapshot)
    if snapshot.get("task_dependency_pauses"): raise ValueError("dependency pause is active")
    if any(c.get("task_id") in {r.get("id") for r,_ in reviews} for c in snapshot["task_cancellations"]): raise ValueError("review cancellation is active")
    if identity_state is None:
        try: import workflow_native_worker
        except ModuleNotFoundError: from tooling.agent_workflow import workflow_native_worker
        identity_state=workflow_native_worker.native_identity_state
    for claim in snapshot["native_claims"]:
        if identity_state(decoded(claim.get("process_identity"))) != "dead": raise ValueError("native claim is not confirmed dead")
    review_ids={r.get("id") for r,_ in reviews}
    for art in snapshot["artifacts"]:
        data=decoded(art.get("artifact_json"))
        detail=data.get("artifact",{}) if isinstance(data,dict) else {}
        if detail.get("producer_task_id",detail.get("producer_id")) in review_ids: raise ValueError("review artifact already exists")
    key = "LOCAL_API:" + expected_server if expected_server and not expected_server.startswith("LOCAL_API:") else expected_server
    circuits=snapshot["dependency_circuits"]
    matches=[c for c in circuits if c.get("dependency_key")==key] if key else circuits
    if len(matches)!=1 or matches[0].get("state") not in ("BACKOFF","OPEN"):
        raise ValueError("matching local API circuit is not in BACKOFF or OPEN")
    allowed={r.get("id"):(r,t) for r,t in tasks if isinstance(t,dict) and t.get("state")=="QUEUED" and
             ((t.get("kind")=="REVIEW" and t.get("run_id")==run_id and t.get("evaluation_generation")==generation) or
              (t.get("kind")=="CHECK" and isinstance(runs.get(t.get("run_id")),dict) and runs[t.get("run_id")].get("current_attempt")==t.get("evaluation_generation")))}
    events={e.get("id"):e for e in snapshot["events"]}
    for row in snapshot["outbox"]:
        if row.get("state")=="ACKNOWLEDGED": continue
        ev=events.get(row.get("event_id")); payload=decoded(ev.get("payload")) if ev else None
        if not isinstance(payload,dict) or workflow_store.digest(payload)!=ev.get("payload_digest") or ev.get("kind")!="TASK_READY" or payload.get("kind")!="TASK_READY" or payload.get("event_id")!=ev.get("id") or payload.get("task_id") not in allowed:
            raise ValueError("outbox contains unrelated pending event")
        task_row,task=allowed[payload.get("task_id")]
        if ev.get("run_id")!=task.get("run_id") or ev.get("generation")!=task.get("evaluation_generation"):
            raise ValueError("queued task event generation mismatch")
        if any(payload.get(k)!=task.get(k) for k in ("run_id","evaluation_generation","assignment_generation","input_digest")):
            raise ValueError("queued review outbox binding mismatch")


def assert_terminal_review_repair(snapshot: dict, run_id: str, db_path=None) -> None:
    """Admit only immutable terminal ERROR review state and its safe notifications."""
    if not isinstance(run_id, str) or not run_id:
        raise ValueError("terminal review repair identity is invalid")
    decoded = lambda value: json.loads(value) if isinstance(value, str) else value
    run_row = next((row for row in snapshot.get("runs", []) if row.get("id") == run_id), None)
    run = decoded(run_row.get("run_json")) if run_row else None
    generation = run_row.get("current_generation") if isinstance(run_row, dict) else None
    attempts = [decoded(row.get("attempt_json")) for row in snapshot.get("attempts", [])
                if row.get("run_id") == run_id and row.get("generation") == generation]
    if (not isinstance(run, dict) or run.get("run_kind") != "FEATURE_GATE" or
            len(attempts) != 1 or attempts[0].get("phase") != "DECIDED" or
            attempts[0].get("result") != "ERROR"):
        raise ValueError("terminal review repair requires current FEATURE_GATE ERROR")
    try:
        import workflow_store
    except ModuleNotFoundError:
        from tooling.agent_workflow import workflow_store
    if db_path is not None:
        import sys as _sys
        tool_dir = str(Path(__file__).resolve().parent)
        if tool_dir not in _sys.path: _sys.path.insert(0, tool_dir)
        import workflow_store
        store = workflow_store.WorkflowStore(db_path)
        store.verify_settled_error_head(run_id=run_id, generation=generation,
                                       expected_sequence=run_row.get("sequence"),
                                       worktree=run_row.get("worktree"))
    if any(task.get("task_json", {}).get("state") not in ("COMPLETED", "CANCELLED")
           for task in snapshot.get("tasks", [])):
        raise ValueError("workflow task is not settled")
    # Preserve only untouched author terminal deliveries for this run; every
    # other author delivery remains subject to ordinary strict quiescence.
    run_task_ids = {row.get("id") for row in snapshot["tasks"] if row.get("run_id") == run_id}
    terminal_event_ids = set()
    for event in snapshot["events"]:
        event_payload = decoded(event.get("payload"))
        if (event.get("run_id") == run_id and event.get("generation") == generation and
                event.get("kind") == "DISPATCH" and isinstance(event_payload, dict) and
                event_payload.get("run_id") == run_id and event_payload.get("evaluation_generation") == generation and
                event_payload.get("result", event_payload.get("decision")) == "ERROR" and
                workflow_store.digest(event_payload) == event.get("payload_digest")):
            terminal_event_ids.add(event.get("id"))
    filtered = dict(snapshot)
    filtered["deliveries"] = list(snapshot.get("deliveries", []))
    for event_id in terminal_event_ids:
        pair = [d for d in snapshot.get("deliveries", []) if d.get("event_id") == event_id]
        author = decoded(run.get("author"))
        expected_recipients = {"AUTHOR": author,
                               "WEBPAGE": {"session_id": None, "worktree": author.get("worktree"), "role": "owner"}}
        if len(pair) != 2 or {d.get("target_kind") for d in pair} != {"AUTHOR", "WEBPAGE"}:
            raise ValueError("terminal result delivery pair is incomplete")
        for delivery in pair:
            recipient = decoded(delivery.get("recipient_json"))
            if (delivery.get("state") != "PENDING" or delivery.get("attempts") != 0 or
                    delivery.get("claim_generation") != 0 or
                    any(delivery.get(k) is not None for k in ("lease_token", "lease_until_ms", "transport_receipt", "protocol_ack")) or
                    recipient != expected_recipients[delivery["target_kind"]] or
                    delivery.get("payload_digest") != next(e.get("payload_digest") for e in snapshot["events"] if e.get("id") == event_id)):
                raise ValueError("terminal result delivery is not an untouched exact recipient")
            filtered["deliveries"].remove(delivery)
    _assert_released_resources_scopes_deliveries(filtered)
    run_tasks = {row.get("id"): row for row in snapshot["tasks"] if row.get("run_id") == run_id}
    checks = [row for row in run_tasks.values() if decoded(row.get("task_json", {})).get("kind") == "CHECK"]
    if not checks or any(decoded(row.get("task_json", {})).get("result", {}).get("decision") != "PASS"
                         for row in checks):
        raise ValueError("terminal review repair CHECK proof is not all PASS")
    events = {row.get("id"): row for row in snapshot["events"]}
    for row in snapshot["outbox"]:
        if row.get("state") == "ACKNOWLEDGED":
            continue
        event = events.get(row.get("event_id"))
        payload = decoded(event.get("payload")) if event else None
        kind = event.get("kind") if event else None
        payload_kind = payload.get("kind") if isinstance(payload, dict) else None
        if (not event or event.get("run_id") != run_id or event.get("generation") != generation or
                not isinstance(payload, dict) or
                (kind, payload_kind) not in (("DISPATCH", None), ("TASK_CANCELLED", "TASK_CANCELLED"),
                                              ("ATTEMPT_STOPPING", "ATTEMPT_STOPPING"))):
            raise ValueError("terminal review repair contains unrelated pending notification")
        if workflow_store.digest(payload) != event.get("payload_digest"):
            raise ValueError("terminal notification payload digest is invalid")
        if payload_kind is None:
            try:
                workflow_store.validate(payload, "result_available")
            except (ValueError, TypeError) as exc:
                raise ValueError("terminal result notification is invalid") from exc
            if (event.get("id") not in terminal_event_ids or payload.get("event_id") != event.get("id") or
                    payload.get("result_id") != event.get("id") or payload.get("run_id") != run_id or
                    payload.get("evaluation_generation") != generation or payload.get("input_digest") != run.get("input_digest") or
                    payload.get("result") != "ERROR"):
                raise ValueError("terminal result notification is not bound to current run")
            semantic = {key: payload.get(key) for key in
                        ("run_id", "evaluation_generation", "input_digest", "result", "findings", "artifact_refs")}
            expected_digest = workflow_store.digest(semantic)
            if (payload.get("result_digest") != expected_digest or
                    payload.get("result_id") != "event_result_" + hashlib.sha256(expected_digest.encode()).hexdigest()[:24]):
                raise ValueError("terminal result semantic digest is invalid")
        elif payload_kind == "TASK_CANCELLED":
            task_id = payload.get("task_id")
            task = run_tasks.get(task_id)
            saved = decoded(task.get("task_json", {})) if task is not None else {}
            if (task is None or saved.get("state") not in ("COMPLETED", "CANCELLED") or
                    payload.get("run_id") != run_id or payload.get("evaluation_generation") != saved.get("evaluation_generation") or
                    payload.get("assignment_generation") != saved.get("assignment_generation") or
                    payload.get("input_digest") != saved.get("input_digest")):
                raise ValueError("terminal result notification is not bound to settled task")
        elif payload_kind in ("ATTEMPT_STOPPING", "ATTEMPT_CLOSED") and (
                payload.get("run_id") != run_id or payload.get("evaluation_generation") != generation):
            raise ValueError("terminal attempt notification binding is invalid")


def _pending_outbox_count(snapshot):
    return sum(row.get("state") in ("PENDING", "CLAIMED") for row in snapshot["outbox"])


def reviewed_sources(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or any(not isinstance(k, str) or not isinstance(v, str) or len(v) != 64 for k, v in value.items()):
        raise ValueError("reviewed source hashes must be a JSON path-to-SHA256 object")
    return value


def _parent_identity(pid, python_path):
    script = ("$ErrorActionPreference='Stop'; "
              f"Get-CimInstance Win32_Process -Filter \"ProcessId = {int(pid)}\" | "
              "Select-Object ProcessId,ParentProcessId | ConvertTo-Json -Compress")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                            capture_output=True, text=True, timeout=5, check=True)
    value = json.loads(result.stdout)
    if isinstance(value, list):
        if len(value) != 1: raise ValueError("ambiguous launcher identity")
        value = value[0]
    parent = value.get("ParentProcessId") if isinstance(value, dict) and value.get("ProcessId") == pid else None
    if isinstance(parent, bool) or not isinstance(parent, int) or parent < 1:
        raise ValueError("workflow interpreter launcher parent is unavailable")
    script = ("$ErrorActionPreference='Stop'; "
              f"Get-CimInstance Win32_Process -Filter \"ProcessId = {parent}\" | "
              "Select-Object ProcessId,ExecutablePath | ConvertTo-Json -Compress")
    result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
                            capture_output=True, text=True, timeout=5, check=True)
    launcher = json.loads(result.stdout)
    if isinstance(launcher, list):
        if len(launcher) != 1: raise ValueError("ambiguous launcher process")
        launcher = launcher[0]
    if (not isinstance(launcher, dict) or launcher.get("ProcessId") != parent or
            Path(launcher.get("ExecutablePath", "")).resolve() != Path(python_path).resolve()):
        raise ValueError("workflow launcher executable is not the pinned interpreter")
    try: import gate_execution
    except ModuleNotFoundError: from tooling.agent_workflow import gate_execution
    identity = gate_execution.process_identity(parent)
    if not identity: raise ValueError("workflow launcher process identity unavailable")
    return identity


def stop_host(health, python_path, *, backend=None):
    """Stop only the fresh, identity-verified supervisor and coordinator job."""
    if os.name != "nt" and backend is None: raise OSError("host stop is Windows-only")
    child = health["child"]
    identities = [health["supervisor"]["identity"], child["coordinator_identity"]]
    identities += [_parent_identity(i["pid"], python_path) for i in identities]
    if len({i["pid"] for i in identities}) != 4: raise ValueError("host process identities are not unique")
    if backend is None: backend = _WindowsStopBackend()
    handles = []
    job = None
    settled = False
    stop_started = False
    try:
        for identity in identities:
            handle = backend.open(identity["pid"])
            handles.append(handle)
            if backend.identity(identity["pid"], handle) != identity:
                raise ValueError("host process identity changed before stop")
        job = backend.open_job(child["scope_name"])
        if not backend.contains(job, identities[1]): raise ValueError("coordinator is not in its owned job")
        stop_started = True
        backend.terminate(handles[0])
        result = backend.terminate_job(job, 5)
        if result != "CONFIRMED": raise TimeoutError("coordinator job termination unresolved")
        for handle in handles:
            if not backend.wait(handle, 5000): raise TimeoutError("host process wait unresolved")
        settled = True
        return {"identities": identities, "job": child["scope_name"], "status": "STOPPED"}
    finally:
        if settled or not stop_started:
            if job is not None: backend.close_job(job)
            for handle in handles: backend.close(handle)


class _WindowsStopBackend:
    def __init__(self):
        import ctypes
        from ctypes import wintypes
        self.ctypes = ctypes
        self.k = ctypes.WinDLL("kernel32", use_last_error=True)
        self.k.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        self.k.OpenProcess.restype = wintypes.HANDLE
        self.k.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
        self.k.TerminateProcess.restype = wintypes.BOOL
        self.k.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        self.k.WaitForSingleObject.restype = wintypes.DWORD
        self.k.CloseHandle.argtypes = [wintypes.HANDLE]
        self.k.CloseHandle.restype = wintypes.BOOL
    def open(self, pid):
        h = self.k.OpenProcess(0x1000 | 0x00100000 | 0x0001, False, pid)
        if not h: raise OSError(self.ctypes.get_last_error(), "OpenProcess failed")
        return h
    def identity(self, pid, handle):
        try: import gate_execution
        except ModuleNotFoundError: from tooling.agent_workflow import gate_execution
        return gate_execution.process_identity(pid, handle=handle)
    def open_job(self, name):
        try: from workflow_process_scope import OwnedProcessScope
        except ModuleNotFoundError: from tooling.agent_workflow.workflow_process_scope import OwnedProcessScope
        job = OwnedProcessScope.open_existing(name)
        if job is None: raise ValueError("owned coordinator job is missing")
        return job
    def contains(self, job, identity): return job.contains(identity)
    def terminate(self, handle):
        if not self.k.TerminateProcess(handle, 1): raise OSError(self.ctypes.get_last_error(), "TerminateProcess failed")
    def terminate_job(self, job, timeout): return job.terminate_and_wait(timeout)["status"]
    def wait(self, handle, timeout): return self.k.WaitForSingleObject(handle, timeout) == 0
    def close(self, handle): self.k.CloseHandle(handle)
    def close_job(self, job): job.close()


def start_host(config, python_path, health_file, port, env):
    shared = Path(python_path).resolve().parents[2]
    supervisor = shared / "tooling/agent_workflow/workflow_supervisor.py"
    log = Path(config).resolve().parent / "current-service.log"
    nonce = str(uuid.uuid4())
    command = [str(Path(python_path)), str(supervisor), "serve", "--config", str(config),
               "--port", str(port), "--health-file", str(health_file), "--activation-nonce", nonce]
    flags = (getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0) | getattr(subprocess, "CREATE_NO_WINDOW", 0))
    with log.open("ab") as stream:
        process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=stream, stderr=stream,
                                   env=env, close_fds=True, creationflags=flags)
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if process.poll() is not None: raise RuntimeError("workflow supervisor exited during startup")
        try:
            health = json.loads(Path(health_file).read_text(encoding="utf-8"))
            child = health["child"]
            identity = child["coordinator_identity"]
            sup = health["supervisor"]
            if (health.get("state") == "running" and sup.get("activation_nonce") == nonce and
                    Path(sup.get("config_path", "")).resolve() == Path(config).resolve() and
                    sup.get("config_sha256") == sha256(Path(config).read_bytes())):
                try: import gate_execution
                except ModuleNotFoundError: from tooling.agent_workflow import gate_execution
                try: from workflow_process_scope import OwnedProcessScope
                except ModuleNotFoundError: from tooling.agent_workflow.workflow_process_scope import OwnedProcessScope
                scope = OwnedProcessScope.open_existing(child["scope_name"])
                if scope is None: raise ValueError("supervisor job is unavailable")
                try:
                    valid = (gate_execution.process_identity(identity["pid"]) == identity and
                             gate_execution.process_identity(sup["identity"]["pid"]) == sup["identity"] and
                             scope.snapshot()["active_processes"] == 1 and scope.contains(identity))
                finally: scope.close()
                if valid:
                    return {"process": process, "command": command, "activation_nonce": nonce,
                            "log": str(log), "health": health,
                            "identities": [sup["identity"], identity]}
        except (OSError, ValueError, KeyError, TypeError): pass
        time.sleep(.1)
    raise TimeoutError("workflow supervisor startup health was not verified")


class _HostProbeDrainPending(ValueError):
    """The owned Job still has transient members after the maintenance hold."""


def _validate_current_host(health, config_path, cfg_bytes, *, require_sole_member=True):
    sup = health.get("supervisor", {})
    if (health.get("state") != "running" or Path(sup.get("config_path", "")).resolve() != config_path or
            sup.get("config_sha256") != sha256(cfg_bytes)):
        raise ValueError("current supervisor health does not match configuration")
    try: import gate_execution
    except ModuleNotFoundError: from tooling.agent_workflow import gate_execution
    child = health.get("child") or {}
    identity = child.get("coordinator_identity")
    if (not isinstance(identity, dict) or gate_execution.process_identity(identity.get("pid")) != identity or
            gate_execution.process_identity((sup.get("identity") or {}).get("pid")) != sup.get("identity")):
        raise ValueError("current host process identity is not verified")
    try: from workflow_process_scope import OwnedProcessScope
    except ModuleNotFoundError: from tooling.agent_workflow.workflow_process_scope import OwnedProcessScope
    scope = OwnedProcessScope.open_existing(child.get("scope_name"))
    if scope is None: raise ValueError("current coordinator job is unavailable")
    try:
        count = scope.snapshot().get("active_processes")
        if not scope.contains(identity):
            raise ValueError("current coordinator is not a verified owned job member")
        if require_sole_member and count != 1:
            if isinstance(count, int) and not isinstance(count, bool) and count > 1:
                raise _HostProbeDrainPending("current coordinator is not the sole owned job member")
            raise ValueError("current coordinator is not the sole owned job member")
    finally: scope.close()


def _release_pre_stop_hold(admission_module, root, receipt, receipt_path, barrier, stop_started):
    if stop_started or barrier._hold is not None or barrier._locks or barrier._ownership_uncertain:
        return False
    admission_module.resume_hold(root, receipt)
    if admission_module.maintenance(root) is not None:
        raise RuntimeError("workflow intake did not reopen after owned hold release")
    Path(receipt_path).unlink(missing_ok=True)
    return True


def _reviewed_loopback_endpoint(value):
    parsed = urllib.parse.urlsplit(value) if isinstance(value, str) else None
    if (parsed is None or parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1") or
            parsed.username is not None or parsed.password is not None or parsed.path not in ("", "/") or
            parsed.query or parsed.fragment or parsed.port is None or not 1 <= parsed.port <= 65535):
        raise ValueError("reviewed OpenCode endpoint must be an explicit loopback HTTP URL")
    return value.rstrip("/")


def run(args):
    """Preflight or perform a bounded runtime-only package update."""
    import sys as _sys
    tool_dir = str(Path(__file__).resolve().parent)
    if tool_dir not in _sys.path: _sys.path.insert(0, tool_dir)
    import admission, workflow_cutover, workflow_cutover_transition
    config_path = args.config.resolve()
    cfg_bytes = config_path.read_bytes()
    cfg = json.loads(cfg_bytes)
    endpoint_override = getattr(args, "opencode_server", None)
    if endpoint_override is not None:
        endpoint_override = _reviewed_loopback_endpoint(endpoint_override)
        if "opencode_server" not in cfg:
            raise ValueError("configuration endpoint is unavailable")
        new_cfg = dict(cfg)
        new_cfg["opencode_server"] = endpoint_override
    else:
        new_cfg = cfg
    new_cfg_bytes = ((json.dumps(new_cfg, indent=2) + "\n").encode("utf-8")
                     if endpoint_override is not None else cfg_bytes)
    receipt_path = args.receipt.resolve()
    receipt_bytes = receipt_path.read_bytes()
    if sha256(receipt_bytes) != args.receipt_sha256: raise ValueError("receipt hash mismatch")
    receipt = json.loads(receipt_bytes)
    main_root = Path(receipt["main_root"]).resolve()
    shared_root = Path(receipt["shared_root"]).resolve()
    db_path = Path(cfg["database"])
    if not db_path.is_absolute(): db_path = config_path.parent / db_path
    repair_run_id = getattr(args, "repair_stalled_run", None)
    queued_review_run_id = getattr(args, "repair_queued_review", None)
    terminal_review_run_id = getattr(args, "repair_terminal_review", None)
    snapshot = deployment_snapshot(db_path)
    if sum(value is not None for value in (repair_run_id, queued_review_run_id, terminal_review_run_id)) > 1:
        raise ValueError("deployment repair modes are mutually exclusive")
    repair_mode = repair_run_id or queued_review_run_id or terminal_review_run_id
    if repair_mode is None: assert_quiescent(snapshot)
    elif repair_run_id: assert_stalled_native_repair(snapshot, repair_run_id)
    elif queued_review_run_id: assert_queued_review_repair(snapshot, queued_review_run_id, expected_server=cfg.get("opencode_server"))
    else: assert_terminal_review_repair(snapshot, terminal_review_run_id, db_path)
    health = json.loads(args.health_file.read_text(encoding="utf-8"))
    # Preflight is advisory about process count: ordinary coordinator API
    # probes may be children until the maintenance hold is applied.
    _validate_current_host(health, config_path, cfg_bytes, require_sole_member=False)
    if endpoint_override is not None:
        # The owned preimage is always the live, old configuration. The only
        # reviewed difference is the local API endpoint.
        if not isinstance(cfg.get("opencode_server"), str): raise ValueError("old endpoint is invalid")
    pointer = main_root / package.POINTER_RELATIVE
    expected = args.expected_pointer_sha256
    if pointer.is_file():
        verify_pointer(pointer, expected, receipt_path)
        pointer_bytes = pointer.read_bytes()
    else:
        stage_old = Path(receipt["stage"]) / "control/pointer.previous.json"
        backup_sha = receipt["publication_pointer"]["backup_sha256"]
        old_bytes = stage_old.read_bytes()
        if sha256(old_bytes) != backup_sha: raise ValueError("receipt pointer backup mismatch")
        pointer_bytes = canonical_pointer_bytes(old_bytes, receipt_path, receipt["manifest_sha256"])
        if sha256(pointer_bytes) != expected: raise ValueError("recovered pointer differs from reviewed hash")
    source_hashes = reviewed_sources(args.reviewed_source_hashes)
    binding_plan = getattr(args, "binding_plan", None)
    binding_plan_hash = getattr(args, "binding_plan_sha256", None)
    if (binding_plan is None) != (binding_plan_hash is None):
        raise ValueError("binding plan path and reviewed SHA-256 must be supplied together")
    if binding_plan is not None:
        if not binding_plan.is_file() or binding_plan.is_symlink() or sha256(binding_plan.read_bytes()) != binding_plan_hash:
            raise ValueError("binding plan differs from reviewed SHA-256")
        proposed = json.loads(binding_plan.read_text(encoding="utf-8"))
        allowed_targets = {f"MoonStation14-agent-0{i}" for i in range(1, 5)}
        targets = proposed.get("targets") if isinstance(proposed, dict) else None
        shell_mode = isinstance(proposed, dict) and proposed.get("mode") == "inspection-safe-shell"
        shared_config_mode = isinstance(proposed, dict) and proposed.get("mode") == "shared-opencode-config"
        reviewer_search_mode = (isinstance(proposed, dict) and
                                proposed.get("mode") == "reviewer-search-permissions")
        if shared_config_mode:
            import workflow_coordinator_bindings
            workflow_coordinator_bindings.validate_shared_config_plan_shape(proposed)
            if proposed["template"]["path"] != str((main_root / "tooling/opencode/shared-config.jsonc").resolve()):
                raise ValueError("shared config template must be the canonical MAIN template")
        if reviewer_search_mode:
            if (set(proposed) != {"version", "mode", "targets"} or
                    proposed.get("version") != 1 or not isinstance(targets, list) or len(targets) != 1 or
                    not isinstance(targets[0], dict) or
                    set(targets[0]) != {"target", "expected_config_sha256"} or
                    targets[0].get("target") != "main" or
                    not isinstance(targets[0].get("expected_config_sha256"), str) or
                    len(targets[0]["expected_config_sha256"]) != 64 or
                    any(c not in "0123456789abcdef" for c in targets[0]["expected_config_sha256"])):
                raise ValueError("reviewed reviewer-search binding plan must name MAIN exactly")
        if shell_mode:
            allowed_shell={"version","mode","targets","formatting_preimages","profiles"}
            targets=proposed.get("targets")
            expected_shell_targets={"main",*(f"MoonStation14-agent-0{i}" for i in range(1,5))}
            if (set(proposed)-allowed_shell or not {"version","mode","targets"}<=set(proposed) or
                    proposed.get("version")!=1 or not isinstance(targets,list) or len(targets)!=5 or
                    {r.get("target") for r in targets if isinstance(r,dict)}!=expected_shell_targets or
                    any(not isinstance(r,dict) or set(r)!={"target","expected_config_sha256"} or
                        not isinstance(r.get("expected_config_sha256"),str) or len(r["expected_config_sha256"])!=64 or
                        any(c not in "0123456789abcdef" for c in r["expected_config_sha256"]) for r in targets) or
                    ("formatting_preimages" in proposed and (not isinstance(proposed["formatting_preimages"],dict) or
                        set(proposed["formatting_preimages"])-{"main"} or
                        any(not isinstance(v,dict) or set(v)!={"path","sha256"} or not isinstance(v["path"],str) or
                            not isinstance(v["sha256"],str) or len(v["sha256"])!=64 or
                            any(c not in "0123456789abcdef" for c in v["sha256"]) for v in proposed["formatting_preimages"].values()))) or
                    ("profiles" in proposed and (not isinstance(proposed["profiles"],list) or len(proposed["profiles"])!=3 or
                        any(not isinstance(r,dict) or set(r)!={"target","path","expected_sha256"} or
                            not isinstance(r.get("target"),str) or not isinstance(r.get("expected_sha256"),str) or
                            len(r["expected_sha256"])!=64 or any(c not in "0123456789abcdef" for c in r["expected_sha256"]) for r in proposed["profiles"]) or
                        {r["target"] for r in proposed["profiles"] if isinstance(r,dict)}!={f"MoonStation14-agent-0{i}" for i in range(1,4)} or
                        any(r.get("path")!=".opencode/agents/fidelity-reviewer.md" for r in proposed["profiles"])))):
                raise ValueError("reviewed shell binding plan must name MAIN and agents 01..04 exactly")
        elif not reviewer_search_mode and not shared_config_mode and (not isinstance(proposed, dict) or set(proposed) != {"version", "coordinator_endpoint", "targets"} or
                proposed.get("version") != 1 or not isinstance(targets, list) or len(targets) != 4 or
                {row.get("target") for row in targets if isinstance(row, dict)} != allowed_targets or
                any(not isinstance(row, dict) or row.get("tool_output_read") is not True or
                    not isinstance(row.get("roles"), list) or len(row["roles"]) != 1 or
                    not isinstance(row["roles"][0], dict) or row["roles"][0].get("role") != "author" or
                    not ((set(row["roles"][0]) == {"role", "binding_mode", "caller", "token_file", "context_file"}
                          and row["roles"][0].get("binding_mode") == "worktree") or
                         (set(row["roles"][0]) == {"role", "agent", "caller", "session_id", "worktree", "token_file"}
                          and row["roles"][0].get("agent") == "orchestrate-ms14"))
                    for row in targets)):
            raise ValueError("reviewed binding plan must opt in only author roles on agents-01..04")
    for source, digest in source_hashes.items():
        path = (main_root / source).resolve()
        if not path.is_relative_to(main_root) or not path.is_file() or sha256(path.read_bytes()) != digest:
            raise ValueError("reviewed source does not match MAIN file")
    service_path = Path.home() / ".local/state/opencode/service.json"
    service = json.loads(service_path.read_text(encoding="utf-8"))
    password = service.get("password") if isinstance(service, dict) else None
    service_url = service.get("url") if isinstance(service, dict) else None
    expected_service_url = endpoint_override or cfg["opencode_server"]
    if service_url != expected_service_url: raise ValueError("local workflow service URL differs from reviewed configuration")
    if not isinstance(password, str) or not password: raise ValueError("workflow service credential unavailable")
    try:
        import gate_dispatch
        # OpenCode's CLI API helper reads the service's HTTP basic-auth
        # password from this environment variable. Keep it scoped to the probe.
        previous_password = os.environ.get("OPENCODE_SERVER_PASSWORD")
        os.environ["OPENCODE_SERVER_PASSWORD"] = password
        try: gate_dispatch._check_v2(expected_service_url)
        finally:
            if previous_password is None: os.environ.pop("OPENCODE_SERVER_PASSWORD", None)
            else: os.environ["OPENCODE_SERVER_PASSWORD"] = previous_password
    except Exception as exc:
        # The shared API client can include private process/transport detail.
        raise ValueError("authenticated OpenCode v2 API check failed") from exc
    if args.preflight:
        return {"status": "READY", "changed_sources": source_hashes,
                **({"opencode_server": endpoint_override} if endpoint_override else {})}

    if not pointer.exists(): package._atomic_write(pointer, pointer_bytes, exclusive=True)
    elif sha256(pointer.read_bytes()) != expected: raise ValueError("publication pointer changed before execution")
    temp_root = Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode"
    temp_root.mkdir(parents=True, exist_ok=True)
    stage = Path(tempfile.mkdtemp(prefix="workflow-update-", dir=temp_root))
    built = (package.build(stage, receipt_path, preserve_bindings=True, reviewed_source_hashes=source_hashes) if binding_plan is None else
             package.build(stage, receipt_path, binding_plan=binding_plan, preserve_bindings=False,
                           reviewed_source_hashes=source_hashes))
    changed = {row["source"]: row["source_sha256"] for row in built["files"].values()
               if row["old_target_sha256"] != row["source_sha256"]}
    if changed != source_hashes: raise ValueError("staged changed-source map differs from reviewed hashes")
    contexts = []
    for binding in cfg.get("author_worktrees", []):
        path = Path(binding["context_file"]).resolve()
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("publication_receipt_path") != str(receipt_path) or data.get("publication_receipt_sha256") != args.receipt_sha256:
            raise ValueError("author context is not bound to current receipt")
        contexts.append({"path": path, "sha256": sha256(path.read_bytes()), "context": data})
    env = dict(os.environ)
    env["OPENCODE_PASSWORD"] = password
    control_id = str(uuid.uuid4())
    hold = admission.begin_hold(cfg["admission_root"], owner="workflow-stalled-repair" if repair_run_id else "workflow-queued-review-repair" if queued_review_run_id else "workflow-runtime-update", control_id=control_id,
                                note="Brief workflow tooling update")
    hold_path = config_path.parent / "current-update-hold.json"
    barrier = workflow_cutover.DrainBarrier(cfg["admission_root"], hold)
    stop_started = False
    success = False
    phase = "barrier"
    failure_reason = "operator failure; private details withheld"
    config_backup = None
    config_replacement_state = "unchanged"
    try:
        if hold_path.exists(): raise ValueError("private current hold receipt already exists")
        package._atomic_write(hold_path, json.dumps(hold, sort_keys=True).encode(), exclusive=True)
        with barrier:
            if not isinstance(barrier.initial, dict) or barrier.initial.get("status") != "OBSERVED_ZERO":
                raise ValueError("workflow drain is busy")
            current = deployment_snapshot(db_path)
            if repair_mode is None:
                assert_quiescent(current)
                if _pending_outbox_count(snapshot):
                    prestop_changed = assert_stalled_repair_snapshot_stable(snapshot, current)
                elif current["logical_fingerprint"] != snapshot["logical_fingerprint"]: raise ValueError("workflow state changed during preflight")
            elif repair_run_id:
                assert_stalled_native_repair(current, repair_run_id)
                assert_stalled_repair_snapshot_stable(snapshot, current)
            elif queued_review_run_id:
                assert_queued_review_repair(current, queued_review_run_id, expected_server=cfg.get("opencode_server"))
                assert_stalled_repair_snapshot_stable(snapshot, current)
            else:
                assert_terminal_review_repair(current, terminal_review_run_id, db_path)
                if current["logical_fingerprint"] != snapshot["logical_fingerprint"]: raise ValueError("workflow state changed during preflight")
            if config_path.read_bytes() != cfg_bytes: raise ValueError("configuration changed during preflight")
            # Once the hold and drain are established, allow in-flight API
            # probes a bounded interval to exit. Never stop or replace unless
            # the original strict sole-member verification succeeds.
            deadline = time.monotonic() + 10
            while True:
                try:
                    _validate_current_host(health, config_path, cfg_bytes)
                    break
                except _HostProbeDrainPending:
                    if time.monotonic() >= deadline:
                        raise
                    time.sleep(min(.1, max(0, deadline - time.monotonic())))
            phase = "host_stop_started"
            stop_started = True
            stop_host(health, args.python)
            if endpoint_override is not None:
                if config_path.read_bytes() != cfg_bytes:
                    raise ValueError("configuration changed before endpoint replacement")
                config_backup = stage / "control/original-coordinator-config.json"
                config_backup.parent.mkdir(parents=True, exist_ok=True)
                package._atomic_write(config_backup, cfg_bytes, exclusive=True)
                config_replacement_state = "replacement_pending"
                package._atomic_write(config_path, new_cfg_bytes)
                config_replacement_state = "new_configured"
            stopped_snapshot = deployment_snapshot(db_path)
            if repair_mode is None:
                assert_quiescent(stopped_snapshot)
                if _pending_outbox_count(snapshot):
                    prestop_changed = assert_stalled_repair_snapshot_stable(snapshot, stopped_snapshot)
            elif repair_run_id:
                assert_stalled_native_repair(stopped_snapshot, repair_run_id)
                prestop_changed = assert_stalled_repair_snapshot_stable(snapshot, stopped_snapshot)
            elif queued_review_run_id:
                assert_queued_review_repair(stopped_snapshot, queued_review_run_id, expected_server=cfg.get("opencode_server"))
                prestop_changed = assert_stalled_repair_snapshot_stable(snapshot, stopped_snapshot)
            else:
                assert_terminal_review_repair(stopped_snapshot, terminal_review_run_id, db_path)
                if stopped_snapshot["logical_fingerprint"] != snapshot["logical_fingerprint"]: raise ValueError("workflow state changed during terminal review repair")
            phase = "publish"
            new_receipt = config_path.parent / ("current-installation-receipt-" + str(uuid.uuid4()) + ".json")
            published = package.publish(stage, receipt_path, new_receipt, pointer_path=pointer,
                                        expected_pointer_sha256=expected)
            class Journal:
                def record(self, *unused): pass
            workflow_cutover_transition._refresh_author_contexts(contexts, new_receipt, published, Journal())
            new_health = config_path.parent / "current-service-health.json"
            phase = "host_start"
            started = start_host(config_path, args.python, new_health, args.port, env)
            token_path = Path(cfg["author_worktrees"][0]["token_file"])
            token = token_path.read_text(encoding="utf-8").strip()
            endpoint = f"http://127.0.0.1:{args.port}"
            request = urllib.request.Request(endpoint + "/v1/health",
                                             headers={"Authorization": "Bearer " + token})
            class NoRedirect(urllib.request.HTTPRedirectHandler):
                def redirect_request(self, *unused): return None
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
            with opener.open(request, timeout=5) as response:
                if response.status != 200: raise ValueError("coordinator health HTTP check failed")
                api_health = json.loads(response.read(65536))
            if api_health.get("status") != "running": raise ValueError("coordinator API health is not running")
            if repair_mode is None:
                if (api_health.get("orchestration_paused_for_maintenance") is not True or
                        api_health.get("pending_events") != _pending_outbox_count(stopped_snapshot)): raise ValueError("coordinator API health is not paused and drained")
            elif (api_health.get("orchestration_paused_for_maintenance") is not True or
                  api_health.get("pending_events") != _pending_outbox_count(stopped_snapshot)):
                raise ValueError("repair coordinator is not paused at the stopped database baseline")
            after = deployment_snapshot(db_path)
            if repair_mode is None:
                assert_quiescent(after)
                if after["logical_fingerprint"] != stopped_snapshot["logical_fingerprint"]: raise ValueError("workflow state changed after paused coordinator startup")
            elif repair_run_id:
                assert_stalled_native_repair(after, repair_run_id)
                if after["logical_fingerprint"] != stopped_snapshot["logical_fingerprint"]:
                    raise ValueError("workflow state changed after paused coordinator startup")
            elif queued_review_run_id:
                assert_queued_review_repair(after, queued_review_run_id, expected_server=cfg.get("opencode_server"))
                if after["logical_fingerprint"] != stopped_snapshot["logical_fingerprint"]:
                    raise ValueError("workflow state changed after paused coordinator startup")
            else:
                assert_terminal_review_repair(after, terminal_review_run_id, db_path)
                if after["logical_fingerprint"] != stopped_snapshot["logical_fingerprint"]:
                    raise ValueError("workflow state changed after paused coordinator startup")
            if config_path.read_bytes() != new_cfg_bytes: raise ValueError("configuration changed during update")
            if binding_plan is not None and proposed.get("mode") == "shared-opencode-config" and proposed["untrack_generated"]:
                phase = "index_transition"
                import workflow_coordinator_bindings
                roots = {"main": main_root}
                roots.update({f"MoonStation14-agent-0{i}": main_root.parent / f"MoonStation14-agent-0{i}" for i in range(1, 5)})
                finalize_shared_config_index_transition(roots, proposed, json.loads(new_receipt.read_text(encoding="utf-8")), stage)
            phase = "barrier_release"
        if barrier._hold is not None or barrier._locks or barrier._ownership_uncertain:
            raise RuntimeError("drain barrier release was not confirmed")
        manifest = {"receipt": str(new_receipt), "receipt_sha256": sha256(new_receipt.read_bytes()),
                    "config": str(config_path), "health_file": str(new_health), "port": args.port,
                    "identities": started["identities"], "job": started["health"]["child"]["scope_name"],
                      "database_unchanged": after["logical_fingerprint"] == snapshot["logical_fingerprint"],
                     **({"orchestration_paused_during_publication": True} if repair_mode is None else {}),
                     **({"repair_run_id": repair_run_id,
                         "database_preserved_through_paused_upgrade": True,
                         "prestop_lease_bookkeeping_changed": prestop_changed} if repair_run_id is not None else {}),
                      **({"repair_queued_review_run_id": queued_review_run_id,
                          "database_preserved_through_paused_upgrade": True,
                          "prestop_lease_bookkeeping_changed": prestop_changed} if queued_review_run_id is not None else {}),
                      **({"database_preserved_through_paused_upgrade": True,
                          "prestop_lease_bookkeeping_changed": prestop_changed} if repair_mode is None and _pending_outbox_count(snapshot) else {})}
        package._atomic_write(main_root / ".opencode/workflow/current-installation.json",
                              (json.dumps(manifest, sort_keys=True, indent=2) + "\n").encode())
        admission.resume_hold(cfg["admission_root"], hold)
        if admission.maintenance(cfg["admission_root"]) is not None:
            raise RuntimeError("workflow intake did not reopen after owned hold release")
        hold_path.unlink(missing_ok=True)
        (main_root / ".opencode/workflow/current-failure.json").unlink(missing_ok=True)
        success = True
        return {"status": "DEPLOYED", "manifest": str(main_root / ".opencode/workflow/current-installation.json")}
    except Exception as exc:
        from workflow_task_artifacts import sanitize_text
        failure_reason = sanitize_text(str(exc), (password, token if 'token' in locals() else ""))[:512]
        raise
    finally:
        if not success:
            try: _release_pre_stop_hold(admission, cfg["admission_root"], hold, hold_path, barrier, stop_started)
            except Exception: pass
            from workflow_task_artifacts import sanitize_text
            failure = {"phase": phase, "error_type": "DeploymentFailure",
                       "safe_reason": failure_reason}
            if endpoint_override is not None:
                failure.update({"config_sha256_before": sha256(cfg_bytes),
                                "config_sha256_after": sha256(new_cfg_bytes),
                                "config_replacement_state": config_replacement_state,
                                "config_backup": str(config_backup) if config_backup else None,
                                "configured_new_endpoint_remains": config_replacement_state == "new_configured"})
            package._atomic_write(main_root / ".opencode/workflow/current-failure.json",
                                  (json.dumps(failure, sort_keys=True) + "\n").encode())


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--health-file", required=True, type=Path)
    parser.add_argument("--receipt", required=True, type=Path)
    parser.add_argument("--receipt-sha256", required=True)
    parser.add_argument("--expected-pointer-sha256", required=True)
    parser.add_argument("--binding-plan", type=Path)
    parser.add_argument("--binding-plan-sha256")
    parser.add_argument("--python", required=True, type=Path)
    parser.add_argument("--reviewed-source-hashes", required=True, type=Path)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--preflight", action="store_true")
    mode.add_argument("--execute", action="store_true")
    parser.add_argument("--port", type=int, default=59183)
    parser.add_argument("--opencode-server", help="reviewed endpoint-only loopback API relocation")
    repairs = parser.add_mutually_exclusive_group()
    repairs.add_argument("--repair-stalled-run", help="repair only the exact verified pre-execution native failure")
    repairs.add_argument("--repair-queued-review", help="repair only a verified pre-dispatch queued-review outage")
    repairs.add_argument("--repair-terminal-review-run", dest="repair_terminal_review",
                         help="repair only an immutable terminal review-notification outage")
    args = parser.parse_args(argv)
    try:
        if sha256(args.receipt.read_bytes()) != args.receipt_sha256:
            raise ValueError("publication receipt hash mismatch")
        for path in (args.config, args.health_file, args.receipt, args.python):
            if not path.is_file():
                raise ValueError("required private deployment input is unavailable")
        if not 1 <= args.port <= 65535:
            raise ValueError("invalid service port")
        result = run(args)
        print(json.dumps(result, sort_keys=True))
        return 0
    except Exception as exc:
        # Never echo exception text: private paths and credential-bearing errors stay private.
        print(f"deployment refused: {type(exc).__name__}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
