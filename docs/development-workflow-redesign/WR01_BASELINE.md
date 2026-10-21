# WR01: baseline and implementation/reuse map

WR01 scope is read-only investigation plus static protocol specifications/fixtures. No live mutation, service restart, native validation, publication or author wake is permitted. Recheck the live baseline at WR12; this snapshot is not permission to overwrite later changes.

## Captured baseline

Machine-specific evidence: `build/agent-workflow/workflow-redesign-plan/wr01/`.

- `baseline.json`: Git HEAD/status/worktrees; SHA-256 and size for 54 protected MAIN workflow, role and contract files; design-input hashes; publication pointer/receipt hash; all 108 receipt ownership rows with actual hashes; six configuration hashes; canonical current heads; 25 execution jobs; evidence/health/progress/recovery indexes; observer/status records; extracted OpenAPI contracts.
- `baseline-summary.json`: ownership drift, head selection and execution-state summary.
- `api-readonly-baseline.json`: GET-only worktree-bound MCP discovery and current active-session response. Six workflow MCP bindings were connected. API discovery is not task execution proof.
- `process-baseline.json`: read-only OS identities/creation times/command lines for existing workflow observer, MCP and execution processes.
- `evidence-references.json`: 27 referenced execution receipts/current scoped reports independently hashed; all 27 matched their recorded hashes at capture.

Publication pointer remains `build/agent-workflow/agent-status-board/publication.json`, pointing to receipt `workflow-publish-ec6b60635a594a058878cbc5e8b71f70/publish-receipt.json`. This task does not change it. Of 108 ownership rows, 81 matched and 27 had pre-existing drift/missing legacy entries, consistent with the preserved prior incident baseline. Do not repair, delete or rebind these rows incidentally. All six worktree configurations are individually hashed; MAIN's config is not a substitute for an author's tailored config.

At capture there were **no requested/launching/running native execution jobs**. Five canonical sequenced heads were recorded:

| Candidate | State at capture / durable identity |
|---|---|
| `singularity-gravity-20261016-r33` | No verdict; snapshot `8ec9e19e6e4b6e0fed78698e60662ff7df499ea4fd7c90d092495975dc8457fb`; lead `ses_ebb6f03b2ffe0yxvjS6uHx9Klm`; author `ses_f1696632cffe2FzFf1gwEOEYEM`. Both scoped reports retained; actual record hashes in baseline. |
| `nonhostile-animals-r35` | Diagnostic complete; lead `ses_ebb1abe92ffeHBZxm2CpXRExaT`. |
| `shuttle-feedback08-substeps6-focused-20261016-r34` | Diagnostic complete; lead `ses_ebb1ee3e3ffegPgi01KrjYEAZ7`. |
| `owner-attention-dashboard-r2` | Historical head without a verdict; lead `ses_ec1a84afeffecnEPZii9gFimPt`. Not authorization to revive it. |
| `botany-seed-reference-scream-20261015-r1` | Historical head without a verdict; lead `ses_ec0dfc6d0ffeemQrYFe7ew4H0C`. Not authorization to revive it. |

Legacy ambiguous groups are preserved as ambiguous using the current pure `owner_attention.current_work` selector, not guessed from filename/date. A selected head is not necessarily active execution. Live records may legitimately progress after capture; source/evidence ownership must be distinguished from an immutable global snapshot.

The sole observed exchange watcher was PID 3676, creation time recorded in `process-baseline.json`; do not use the PID alone as later stop authority. Installed CLI reported `opencode v2.0.18`, matching the handoff's configuration-shape context.

Git HEAD predates accepted uncommitted tooling. Protect current bytes and publication receipts; never restore from HEAD. The new redesign directory is intentionally outside the protected-runtime set because WR01 edits its static specifications. No mod/gameplay work is part of WR01.

## Exact reuse and replacement boundaries

| Responsibility | Existing path / symbols | Decision / future unit |
|---|---|---|
| Canonical hash, source/plan resolution | `workflow_runtime.py`: `snapshot_key`, `resolve_gate_plan`, `_cmd_submit_locked`; `workflow_mcp.py`: bounded submit owner | Extend existing normalization/validation rather than duplicate it. WR03 wraps source/provenance and owner-readiness admission. Explicit schema inputs/canonical encoding are in CONTRACTS.md. |
| Execution and admission | `gate_execution.py`: `request`, `request_admitted_observer`, `_request`, `reconcile`, `_reconcile_locked`, `run_worker` | WR05 reuses finite execution and binding; SQLite replaces JSON launch/admission authority. Do not run both schedulers or synchronize two lifecycle authorities. |
| Typed producers / exact receipts | `validation_evidence.py`: `snapshot_evidence`, `collect_evidence`; `workflow_runtime.py`: `verify_gate_receipt`, `_run_gate_validation`, `_gate_evidence_summary` | Reuse evidence parsing, counts, log/artifact hashing and fail-fast behavior; bind results to task/attempt IDs. No new parallel Gradle runner or regex evidence implementation. |
| Time bounds | `workflow_runtime.py`: `GIT_TIMEOUT_SECONDS=20`, `operation_deadline`, `_run_gate_validation(timeout=3600, smoke_timeout=120)`; bounded submit is 240 seconds; `gate_execution.STARTUP_GRACE_SECONDS=30` | Preserve program ownership and monotonic budgets. These are code limits, not measured latency claims. |
| Process ownership / cleanup | `gate_execution.py`: `process_identity`, `identity_state`, worker child registration/cleanup; `workflow_runtime.py`: `stop_owned_tree` | Extend existing identity-bound stop/proof paths for task cancellation; unknown is not dead. Resource holds release only on verified proof. WR08 owns adapter/session cessation validation. |
| Current orchestration | `workflow_observer.py`: `watch`, `_gate_reconcile`; `gate_liveness.py`: `reconcile` | Replace the periodic mutable-JSON orchestration at cutover with the single event-driven coordinator. Preserve reusable process/delivery helpers, not competing phase heuristics. |
| Durable transport bodies / bindings | `gate_dispatch.py`: `_request`, `_acknowledged_prompt`, `deliver_gate_completion`; `gate_liveness.py`: `_persist_post`, `_persist_steer` | Reuse exact session/worktree verification and immutable message-ID/body discipline. New outbox becomes authority; old JSON receipts become history/projections. API ACK remains distinct from protocol ACK. |
| Current finality | `workflow_runtime.py`: `readiness_finalization_guard`, `cmd_gate_finish` | Preserve verified input/evidence requirements as coded new transition guards. Remove lead-model orchestration; do not invoke legacy report writers alongside the new store. |
| Board and human actions | `gate_progress.py`: `render`, `_rows`; `owner_attention.py`: `current_work`, `collect`, `acknowledge_feedback`; `owner_feedback.py`: `reconcile` | Extend existing board/readable cards and reuse human-feedback binding. Replace activity/cache-derived responsibility with committed projections. Stopped-author re-triggering remains excluded. |
| Deployment / configuration provenance | `publish_workflow.ps1`: receipt/hash planning, scoped merges and publication; `install_workflow_mcp.ps1`; current worktree-preserving entry shims | Extend installed layouts, dependencies and role contracts in WR10. Verify exact tailored hashes; preserve unrelated MCP, model/permission customization and historical receipt rows. Activate only WR12. |

Paths above are under `tooling/agent_workflow/` unless noted. MAIN publishes `workflow_runtime.py` as shared `workflow.py`, and `README.runtime.md` as shared `README.md`. Never import an unrelated flat module by accident in installed-layout tests.

**Lock boundary:** reuse existing `sprint_lock`/`gate_lock` and execution OS mutex where they protect current domain operations, but never hold a SQLite write transaction across API calls, process waits or these blocking locks. Reserve a task/lease transactionally, release the transaction, perform the external effect, then commit its bound observation. Outbox/claim reconciliation resolves interrupted effects; a database commit cannot atomically execute an OpenCode request or start a process.

## OpenCode: verified interface, not guessed behavior

Parent inspected the local snapshot `build/agent-workflow/submit-timeout-repair/opencode-openapi.json`; exact extracted operation definitions and the snapshot SHA-256 are retained in baseline.json. Snapshot is OpenAPI 3.1.0 with an experimental API surface, not an assurance of future compatibility.

| Interface | Verified description / critical limit |
|---|---|
| `POST /api/session/{sessionID}/prompt` | Durably admits one input; schedules execution unless `resume:false`. Supports caller-supplied message ID/metadata and `queue`/`steer`. Returned inbox record is transport admission, not consumption/result. |
| `GET /api/session/{sessionID}/inbox` | Lists durable not-yet-delivered work ordered by enqueue sequence. Absence does not prove execution. |
| `DELETE /api/session/{sessionID}/inbox/{inboxID}` | Cancels only a not-yet-delivered item; unavailable items are a no-op. A successful DELETE cannot prove an already-running task stopped. |
| `PATCH /api/session/{sessionID}/inbox/{inboxID}` | Changes pending delivery mode; steering wakes execution. This is not a task-result API. |
| `POST /api/session/{sessionID}/interrupt` | Interrupts active execution owned by that OpenCode process; returns interrupted true/false. `resume:true` resumes pending steering/control items while queued prompts remain parked. Observe actual cessation separately. |
| `GET /api/event` | Native/plugin SSE is explicitly volatile: slow consumers fail the stream and disconnected events are missed. Reconcile durable IDs on reconnect; do not use stream delivery as an acknowledgement. |
| Session lookup / active sessions | Current role, location and execution observation can be inspected. Neither proves protocol result acceptance. |

`claim_task`, `complete_review`, `report_error` and `ack_result` are new proposed application contracts, **not existing OpenCode capabilities**. Provider non-generating health probes, exact cancellation races/cessation, crash recovery and stopped-child proof remain WR06/WR08/WR11 implementation acceptance work. WR01 does not send test prompts, interrupt sessions, cancel queued work or wake models to validate these claims.

## WR01 outputs / next boundary

- `CONTRACTS.md`: record meanings, transition tables, authority/idempotency and semantic guards.
- `contracts/protocol.schema.json` and `contracts/fixtures/`: machine-readable static shapes with positive/negative fixture index; cross-record constraints remain explicit future transactional tests.
- WR02 implements these contracts in an isolated store. WR01 does not implement the coordinator, migration, executor adapters, new MCP tools or a production runtime validator.
