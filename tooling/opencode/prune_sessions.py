"""Keep the newest N descendants under explicit roots; dry-run unless --execute."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import time
import re
import session_store

def activity(row):
    values = [value for value in row[2:7] if isinstance(value, int)]
    return max(values) if values else None


def descendants_by_root(rows, roots):
    by_id = {row[0]: row for row in rows}
    children = {}
    for sid, parent, *_ in rows:
        if parent:
            children.setdefault(parent, set()).add(sid)
    result = {}
    for root in roots:
        if root not in by_id:
            raise ValueError(f"required root missing: {root}")
        if by_id[root][1] is not None:
            raise ValueError(f"root must be a session-tree root: {root}")
        found, stack = set(), list(children.get(root, ()))
        while stack:
            sid = stack.pop()
            if sid in found:
                continue
            found.add(sid)
            stack.extend(children.get(sid, ()))
        result[root] = found
    return by_id, children, result


def keep_ids(descendants, by_id, limit=30):
    return {
        root: set(sorted(ids, key=lambda sid: (activity(by_id[sid]) if activity(by_id[sid]) is not None else -1, sid), reverse=True)[:limit])
        for root, ids in descendants.items()
    }


def ancestors(ids, by_id):
    kept = set()
    for sid in ids:
        parent = by_id[sid][1]
        while parent is not None and parent in by_id:
            if parent in kept:
                break
            kept.add(parent)
            parent = by_id[parent][1]
    return kept


def terminal_safe(row):
    sid, _parent, created, updated, idle, viewed, compacting, suspended, outcome = row
    if not isinstance(created, int) or not isinstance(updated, int):
        return False
    if any(value is not None and not isinstance(value, int) for value in (idle, viewed, compacting)):
        return False
    return suspended is None and outcome in ("succeeded", "failed")


def newer_activity(snapshot_row, current_row):
    old = snapshot_row[2:7]
    current = current_row[2:7]
    return old != current and activity(current_row) is not None and (
        activity(snapshot_row) is None or activity(current_row) > activity(snapshot_row)
    )


def pending(db, sid):
    return any(db.execute(f"SELECT 1 FROM {table} WHERE session_id=? LIMIT 1", (sid,)).fetchone()
               for table in ("session_pending", "session_inbox"))


def plan(rows, db, guards, active, roots, limit):
    by_id, children, descendants = descendants_by_root(rows, roots)
    top30 = keep_ids(descendants, by_id, limit)
    owner_tree = set().union(*descendants.values())
    safety = set(guards) | set(roots) | (active & owner_tree)
    for sid in owner_tree:
        if not terminal_safe(by_id[sid]) or pending(db, sid):
            safety.add(sid)
    top_ids = set().union(*top30.values())
    safety |= ancestors((safety | top_ids) & owner_tree, by_id)
    keepers = top_ids | safety | set(roots)
    return by_id, children, descendants, top30, keepers, owner_tree - keepers


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", action="append", help="explicit protected root; repeat for each root")
    parser.add_argument("--roots-file", help="JSON array of explicit root IDs (alternative to --root)")
    parser.add_argument("--keep", type=int, default=30)
    home = pathlib.Path.home()
    parser.add_argument("--repo", default=str(pathlib.Path(__file__).resolve().parents[2]))
    parser.add_argument("--db", default=str(home / ".local/share/opencode/opencode.db"))
    parser.add_argument("--service", default=str(home / ".local/state/opencode/service.json"))
    parser.add_argument("--tabs", help="override current saved tabs path (normally discovered automatically)")
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--audit-dir", default=str(pathlib.Path(__file__).resolve().parents[2] / "build/opencode-session-cleanup"))
    args = parser.parse_args()
    if args.roots_file:
        if args.root:
            parser.error("use --root or --roots-file, not both")
        root_values = json.loads(pathlib.Path(args.roots_file).read_text(encoding="utf-8"))
        if not isinstance(root_values, list):
            parser.error("--roots-file must contain a JSON array")
    else:
        root_values = args.root or []
    roots = tuple(root_values)
    if not roots or any(not isinstance(r, str) or not re.fullmatch(r"ses_[A-Za-z0-9]+", r) for r in roots) or len(set(roots)) != len(roots):
        parser.error("explicit nonempty unique root IDs are required")
    if args.keep < 1:
        parser.error("--keep must be positive")
    db = session_store.open_db(args.db)
    api = None
    audit_path = None
    state = {"deleted": 0, "roots": {root: {"before": 0, "kept_top30": 0, "safety_extras": 0, "after": 0} for root in roots}}

    def log(event, **fields):
        if audit_path is not None:
            with open(audit_path, "a", encoding="utf-8") as stream:
                stream.write(json.dumps({"event": event, **fields}, separators=(",", ":")) + "\n")

    try:
        guards = session_store.live_guards(args.repo, args.tabs)
        rows = session_store.session_rows(db)
        api = session_store.API(json.loads(pathlib.Path(args.service).read_text(encoding="utf-8")))
        active = api.active()
        by_id, children, descendants, top30, keepers, targets = plan(rows, db, guards, active, roots, args.keep)
        for root, ids in descendants.items():
            state["roots"][root]["before"] = len(ids)
            state["roots"][root]["kept_top30"] = len(top30[root])
            state["roots"][root]["safety_extras"] = len((ids & keepers) - top30[root])
        audit_dir = pathlib.Path(args.audit_dir)
        audit_dir.mkdir(parents=True, exist_ok=True)
        stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        audit_path = audit_dir / f"trim-{stamp}.jsonl"
        log("started", mode="execute" if args.execute else "dry-run", roots=len(roots), target_count=len(targets),
            top30_counts={root: len(top30[root]) for root in roots})
        deleted = set()
        skipped = {}

        def skip(sid, reason):
            skipped.setdefault(sid, reason)

        while True:
            leaves = [sid for sid in targets if sid not in deleted and not (children.get(sid, set()) - deleted)]
            if not leaves:
                break
            progressed = False
            for sid in leaves:
                if sid in roots or sid in keepers:
                    skip(sid, "root_or_keeper")
                    continue
                # Refresh all guards and live state immediately before the final checks.
                fresh_guards = session_store.live_guards(args.repo, args.tabs)
                guards.update(fresh_guards)
                active = api.active()
                if sid in guards or sid in active:
                    skip(sid, "newly_protected_or_active")
                    keepers.add(sid)
                    continue
                info = api.info(sid)
                if info.get("id") != sid or info.get("parentID") != by_id[sid][1]:
                    raise RuntimeError(f"API/DB identity mismatch for {sid}")
                if api.forms(sid) or api.permissions(sid):
                    skip(sid, "unsettled_form_or_permission")
                    keepers.add(sid)
                    continue
                current = db.execute("SELECT id,parent_id,time_created,time_updated,time_idle,time_viewed,time_compacting,time_suspended,idle_outcome FROM session_v2 WHERE id=?", (sid,)).fetchone()
                db_children = {item[0] for item in db.execute("SELECT id FROM session_v2 WHERE parent_id=?", (sid,))}
                if current is None or session_store.has_unremoved_children(db_children, deleted, args.execute):
                    skip(sid, "missing_or_has_children")
                    keepers.add(sid)
                    continue
                current_row = tuple(current)
                if newer_activity(by_id[sid], current_row):
                    skip(sid, "newer_activity_than_snapshot")
                    keepers.add(sid)
                    continue
                if not terminal_safe(current_row) or pending(db, sid):
                    skip(sid, "newly_unsafe_state")
                    keepers.add(sid)
                    continue
                wire_time = info.get("time", {})
                if session_store.wire_millis(wire_time.get("updated")) > by_id[sid][3]:
                    skip(sid, "newer_api_activity")
                    keepers.add(sid)
                    continue
                # Recheck no child has appeared at the API deletion boundary. The
                # native route recursively removes descendants, so only zero-child
                # candidates may be sent to it.
                db_children = {item[0] for item in db.execute("SELECT id FROM session_v2 WHERE parent_id=?", (sid,))}
                if session_store.has_unremoved_children(db_children, deleted, args.execute):
                    skip(sid, "child_at_delete_boundary")
                    keepers.add(sid)
                    continue
                if args.execute:
                    log("delete_before", session_id=sid, root=next(root for root, tree in descendants.items() if sid in tree), parent_id=by_id[sid][1])
                    api.remove(sid)
                    log("delete_after", session_id=sid, root=next(root for root, tree in descendants.items() if sid in tree), parent_id=by_id[sid][1])
                    if db.execute("SELECT 1 FROM session_v2 WHERE id=?", (sid,)).fetchone():
                        raise RuntimeError(f"API reported success but row remains for {sid}")
                deleted.add(sid)
                state["deleted"] = len(deleted)
                parent = by_id[sid][1]
                if parent in children:
                    children[parent].discard(sid)
                progressed = True
            if not progressed:
                break

        for root, ids in descendants.items():
            state["roots"][root]["after"] = len(ids - deleted) if not args.execute else db.execute(
                "WITH RECURSIVE tree(id) AS (SELECT id FROM session_v2 WHERE parent_id=? UNION ALL SELECT s.id FROM session_v2 s JOIN tree t ON s.parent_id=t.id) SELECT COUNT(*) FROM tree", (root,)
            ).fetchone()[0]
        report = {"mode": "execute" if args.execute else "dry-run", "roots": state["roots"],
                  "deleted_candidates": len(deleted) if args.execute else 0,
                  "planned_candidates": len(deleted) if not args.execute else None,
                  "skipped_by_reason": {reason: list(skipped.values()).count(reason) for reason in set(skipped.values())}}
        log("completed", **report)
        print(json.dumps({**report, "audit": str(audit_path)}, indent=2))
    except Exception as exc:
        log("stopped", error_type=type(exc).__name__, partial_state=state)
        print(f"trim stopped safely: {type(exc).__name__}: {exc}; audit={audit_path}")
        raise
    finally:
        db.close()


if __name__ == "__main__":
    main()
