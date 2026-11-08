# Author diagnostics: current use and limits

**Current state · 2026-10-19:** The opt-in author `status` diagnostics are deployed and healthy. Nine scoped MCP bindings have connected/refreshed; no global reload or agent restart was needed. The existing author’s genuine MAIN run is still **FAIL**, acknowledged by its author. Diagnostics expose the failure; they do not fix it. Intake remains open and the live board is available. This is not owner acceptance or sprint completion.

## Use

For input conflicts, safe retries, and workflow-capability blockers, see [Error recovery](ERROR-RECOVERY.md).

Ask the existing author to call its usual `ms14_workflow` `status` with its normal `session_id` and `run_id` (or `idempotency_key`), plus `include_diagnostics: true`:

```json
{"session_id":"<existing-session>","run_id":"<existing-run>","include_diagnostics":true}
```

No token paste, filesystem permission grant, new chat, or restart is required. Diagnostics are opt-in; ordinary polling remains unchanged. The real failing run returned `available` with a nonempty, bounded log excerpt. Raw log contents were not printed, and no denied file read was rerouted. The read-only call left the database fingerprint unchanged; the genuine FAIL and author acknowledgement were preserved.

## Limits and records

Author opt-in responses are capped at 64 KiB; normal/default and reviewer responses retain the 4 KiB cap. Each source log is capped at 16 MiB, at most four logs are included, each tail is limited to 8 KiB, and the diagnostic section is limited to 48 KiB total. Redaction is limited, not a guarantee that secrets can never appear; handle returned excerpts accordingly. Exact model-tool-picker visibility has not been visually reviewed.

Current installation receipt, hash, health-file, config, process/job details are recorded in ignored `.opencode/workflow/current-installation.json`. The durable publication pointer is `.opencode/workflow/publication.json`, outside the build tree. No growing deployment reports are being created.
