# OpenCode session pruning

Use only `tooling/opencode/prune_sessions.py` for future retention runs. It keeps
the 30 most recently active descendants per explicitly supplied root, protects
live tabs/bindings/workflow references from the required protection inventory,
and removes only verified terminal leaves through OpenCode's API. Roots and
their required ancestors are never removed. There is no age cutoff.

Select roots from current session metadata (for example, current saved tabs and
the session's top-level conversations), not IDs from earlier cleanups. Roots
must be top-level session rows. The tool obtains current tabs and all session
references found in the current installation's workflow task, attempt, run,
resource, and delivery metadata itself. It rereads these guards before each
candidate. Missing pointer/config/database/tabs or unexpected schema is a stop.

Run from the repository root, first without `--execute`:

```powershell
python tooling/opencode/prune_sessions.py --root <root-id> --root <another-root-id>
```

Review candidates/skips and the audit output; rerun with the same explicit roots
and `--execute` only after review. `--keep` defaults to 30; `--roots-file`
accepts a JSON string array instead of repeated `--root`. Database, service,
tabs, and repository paths are autodiscovered for the current Windows user;
`--repo`, `--db`, `--service`, and `--tabs` are available for deliberate
overrides. Never delete a root, edit/delete database rows directly,
export/read transcripts, vacuum the database, or schedule this procedure
automatically. API deletion is recursive, so the tool checks children and
sends only leaf candidates.
