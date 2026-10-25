# Workflow error recovery

Workflow errors distinguish correctable input, immutable-record conflicts, uncertain outcomes, and service/capability failures. Responses include a cause, an affected field where known, a `next_action`, and concrete `recovery` guidance. Preflight FAIL receipts instead use their existing findings and `required_change` fields.

## Agent rules

- **Identical replay:** retain the original key and exact payload.
- **Corrected input:** preserve old receipts/evidence. Prepare fresh scope/submission keys and fresh artifact IDs for changed evidence or producers; update evidence references and provenance together.
- **Uncertain submission:** call `status` with the original submission key before another write. Uncertain registration uses `scope_status` with the original registration key.
- **Uncertain acknowledgement:** run status is not an ACK receipt. Replay only the exact original acknowledgement, including its command ID; never manufacture an ACK.
- **Stale review:** inspect only the assigned task and current assignment; do not overwrite completed results or claim another session's work.
- **Service/authentication fault:** retain identifiers and follow the stated service-recovery boundary. Do not change permissions, tokens, trusted floors, or owner decisions.

Local file/JSON/argument failures are distinguished from requests that might have committed. Normal responses remain bounded to 4 KiB; opted-in author diagnostics retain their 64 KiB limit. Oversize responses retain recovery guidance rather than suggesting nonexistent tool parameters.

## Explicit failed-run replacement

For a corrected same-feature submission, inspect the original accepted submission receipt/request and its run/result (including `replaces_run_id` on any replacement request). A new request may set `replaces_run_id` only to the exact current head that is a settled `FEATURE_GATE` `DECIDED/FAIL` authored by the same author. Use fresh scope/key/evidence as usual. Missing intent returns `SUBMISSION_REPLACEMENT_REQUIRED`; an ineligible or stale target returns `REPLACEMENT_TARGET_INVALID`; unsettled work returns `FEATURE_HEAD_UNSETTLED`. Do not change sprint IDs, reuse a prior key for changed intent, or retry against a replacement winner automatically. Historical `SUBMISSION_REPLACEMENT_UNAVAILABLE` receipts remain immutable; prepare a new explicit request rather than rewriting/replaying them with changed content. ERROR recovery remains linked-generation recovery, not author replacement.

Put the field inside the submission JSON, not in the MCP tool arguments. Existing preflight, scope, evidence provenance, and authenticated-session checks still apply. Replacement atomically rechecks the head, terminal tasks, released resources, and cleaned native scopes; it does not alter the prior run, result, evidence, or ACK. Missing/invalid intent is a correctable FAIL; unsettled cleanup remains ERROR.

Current installation details live in ignored `.opencode/workflow/current-installation.json`.

Deployment status · 2026-10-19: explicit failed-run replacement is deployed. Validation passed 302 focused tests across 25 modules plus one staged-runtime smoke test, with independent source review. Nine OpenCode bindings remain connected; all nine official SDK catalogs and the actual retained rejection's updated recovery guidance were verified. Database rows, genuine FAIL/ACK, token files, configuration/model/permission definitions, and non-publication context fields were preserved. Intake is open; no author restart or global reload occurred.

No production replacement was submitted and validation has not been restarted. This capability does not fix the original Gradle `clean` file-lock failure; its historical lock owner remains unverified. Historical incidents and rejected receipts were not closed or rewritten, and no new PASS/review/ACK cycle or owner acceptance is claimed.
