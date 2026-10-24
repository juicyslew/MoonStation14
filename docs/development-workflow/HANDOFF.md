# STATUS

Installed state is determined by `build/agent-workflow/agent-status-board/publication.json`, not this document. Workflow tooling changes do not imply gameplay readiness, commits or merges. Start with [README.md](README.md); current finality repair evidence is in `build/agent-workflow/r31-pending-verdict-incident/`.

# ARCHITECTURE

MAIN `tooling/agent_workflow/` is the development source. The receipt-based publisher installs one runtime in sibling `MoonStation14-shared`; worktrees retain cwd-preserving shims and scoped configuration. Publication maps `workflow_runtime.py` to shared `workflow.py`, and `README.runtime.md` to shared `README.md`. The existing exchange remains sibling `Asset Building Workspace/work/agent_exchange`. Do not create another queue, validator, backend, or scheduler.

The local stdio MCP server is transport-only, using official `mcp==1.26.0` in shared `.venv-workflow-mcp`. Author profiles expose submit/status; central 05 exposes status/validate/review/finish. Neutral 05 is not a Git worktree: resolve its exchange through the existing explicit sibling asset-workspace route, not Git discovery. Submit freezes the author-owned plan and requests integration. The observer automatically admits eligible current, fresh, program-admitted requests through the existing execution owner; AI does not decide whether checks start. Validate remains idempotent for the same job. Review/finish reuse existing scoped-review and finalization owners. Status is recorded metadata, not freshness or approval. Normal agents use MCP; CLI is administrative/debug only.

Startup fixes worktree/profile. Check claimed session IDs against authenticated OpenCode and durable candidate/lead binding. Tools expose no runtime/deadline/retry/cleanup controls. Preserve other MCP servers, models and permissions; this is cooperative tooling, not a hostile-client sandbox.

OpenCode 2.0.18 requires **`mcp.servers.ms14_workflow`**, not flat `mcp.ms14_workflow`. Validate `Config.InfoEncoded.mcp` and actual discovery. Lead mode `all` is primary-capable; subagent-only roles are not.

One `workflow_observer.py` handles dispatch, finite-worker launch, collection and delivery. Public tools persist intent and return promptly. The worker owns the shared OS mutex and verifies source/plan freshness before checks. Ordinary ceilings remain 3,600 seconds; launch smoke is 120. Let GameTest batches finish before skipping later checks. Typed producer evidence, not exit zero alone, determines results. Both final readiness verdicts require a matching completed execution job, verified receipt and collected requested scoped reports. `readiness_finalization_guard` runs before report/outcome writes and in direct finish. Pending finish returns `validation_pending`, never `needs_changes`; wait for the observer without polling/resubmitting. Failed checks with passing scoped code reviews may legitimately yield rejection, never readiness. Post-finalization registration/delivery runs outside metadata locks and retries through existing idempotent owners.

Candidate/plan/lead-bound claims and locks prevent duplicate execution. PID plus OS creation/exit identity distinguishes alive/dead/unknown. Failed spawns terminalize; unknown startup gets 30 seconds, then interrupts/quarantines. Use existing owned-tree cleanup. Root-PID absence or agent confirmation cannot clear unknown descendants without program-owned proof.

Resume the same lead for readiness review, including failed-check rejection; diagnostics never imply readiness. Do not starve scoped collection. Transport retries reuse IDs/bodies; ACK is not execution/consumption. Board `column` is workflow responsibility, separate from raw `activity`: bound admitted waits need no PID, obsolete terminal progress cannot renew admission, and real bound outstanding jobs retain precedence over an old verdict. Completed jobs do not wait; active checks need execution proof. Evidence: `build/agent-workflow/validation-wait-status-fix/`.

Scoped continuations have separate saved delivery/observation state per reviewer/report hash; one review's ACK cannot satisfy another. Only the freshly reread matching scoped record is cleared through the existing dynamic-write owner. Author/model idle while waiting is normal; completion callbacks, not periodic model wakes, continue work.

# IMPORTANT FILES

- `workflow_mcp.py`, `requirements-workflow-mcp.txt`, `install_workflow_mcp.ps1`: bounded adapter, pinned SDK, isolated dependency setup.
- `workflow_runtime.py`: candidate lifecycle, shared `resolve_gate_plan`, typed receipts, reports/finalization.
- `gate_execution.py`, `gate_dispatch.py`, `gate_liveness.py`, `workflow_observer.py`: execution ownership and existing-service dispatch/supervision.
- `gate_progress.py`, `agent_status.py`, `owner_attention.py`, `owner_feedback.py`: board, owner responsibility, observed continuation.
- `publish_workflow.ps1`: staged/hash/receipt publication; `-WorkflowMcpOnly` selectively manages MCP configuration without full rebinding. Preserve merged ownership receipts and backups.
- [CLI.md](CLI.md), `roles/`, `.opencode/agents/`: schemas, administrative commands, and scoped role contracts.
- Asset transport remains separate: `source-fidelity.md`, request/review templates, and the asset workspace's package/manifest/provenance registry remain authoritative.

# SS14 CORRESPONDENCE

No gameplay parity implementation changed. Existing SS14 prototype ancestry/defaults/runtime consumers, shared-owner reuse, prediction/readiness, asset fidelity/provenance, and owner acceptance requirements remain in force. MCP does not replace independent judgment or gameplay tests.

# DECISIONS / WHY

Authors edit plans before submission. Tools freeze resolved contents and maintain execution/health/results, not the author file. A changed submitted plan requires a fresh plan/candidate revision. The lead inspects/rejects rather than editing author work. Process/prose-only changes do not enter the mod coding gate.

Keep MCP catalogs and responses compact (4 KiB maximum reply), submit/request combined, and completion event-driven. Do not model-poll progress, wake idle agents periodically, or create speculative reviewers. Preserve immutable saved prompt bodies and reuse existing sessions/receipts.

Publication merges exact owned entries/rules only; preserve custom models, servers and role prose. Unknown conflicts refuse. Verify planned hashes before writing. Completion retries reuse saved bodies, including after formatter upgrades.

Merge receipt rows by exact target/path across publication modes, including MAIN ownership. Missing managed rows can be reconciled without rewriting unchanged config.

# KNOWN GAPS

Submit timeout: Windows MCP Git hangs caused surviving timed-out submissions. Preserve quiet, stdin-isolated 20-second Git bounds, owned-tree cleanup, the 240-second submit budget, bounded capture/mutex waits and pre-write deadline checks. Never invent alternate candidate IDs after timeout. Exact Windows causality remains unproven. Evidence: `build/agent-workflow/submit-timeout-repair/`.

Lead outage: disconnecting MCP while the observer dispatched stranded 01 R30/02 R29. Their original leads were recovered; reconnection/ACK alone was insufficient. Worktree-bound MCP guards defer lead work without spending attempts; native/quarantine/block priority stays authoritative. Keep MCP connected during ordinary observer reloads. Diagnostic outcome/delivery does not require lead tools. Evidence: `build/agent-workflow/lead-activation-incident/`.

R31 premature `needs_changes/NOT_RUN` suppressed continuation after checks finished. Guards now prohibit that transition. `gate_liveness.recover_premature_outcome` is explicit, hash/session/snapshot/freshness-bound repair, not migration: archive old report/candidate/health, retire its completion observation, preserve job/receipt/scopes/lead, and resume matching partial writes idempotently. Never rerun checks to recover delivery. Consult the current incident `WORK_STATE.md` for actual activation/recovery. Separate dashboard cache expiry remains outside this repair.

Git HEAD predates important uncommitted workflow changes. Never recover these files from HEAD; compare the current source and published receipt first. A mistaken source restoration was caught before MCP publication; the accepted repair stayed installed. Shared evidence folders must not be recursively cleaned.

Legacy 03 R25 lacks a receipt/aggregate. Preserve its used fixture/log and interrupted state; root absence did not prove descendant cleanup. Its author answered the prior notice. Do not rerun it.

OS notification visibility/click-through is manually unverified. Fixtures do not establish Minecraft correctness, owner acceptance or universal crash recovery. Session counters do not establish measured token savings.

`roslyn_ss14` remains failed/untouched; launch no replacement backend. Configuration affects whole-worktree snapshots: serialize migrations, preserve historical receipts and never force native reruns for tooling adoption.

# VALIDATION

Earlier execution/MCP evidence includes 322 Python tests, eight real-process cases, SDK stdio/layout/idempotency checks, 6/6 actual connections and 11/11 permissions. See `build/agent-workflow/durable-validation-execution/` and `MCP_ACCEPTANCE.md`; transport fixtures do not prove native gameplay.

Dependency guard and waiting-column follow-up: independent final review passed 246 affected Python tests, including all eight real-process cases; `git diff --check` passed. Service probes in fixtures use actual worktree-bound MCP response shapes. These checks do not establish Minecraft behavior or universal outage recovery.

An actual author03 `ms14_workflow_status` call completed through OpenCode after the saved release notice. It correctly returned historical R26 completion with unchecked/stale current-source freshness; no candidate or native check was created. Execution evidence is separate from the API ACK record.

R26 diagnostic hit its deadline, retained filtered 2/893 evidence (2 passed; failed count unavailable), recorded FAIL/124, cleaned its tree and delivered the receipt. This proves execution/delivery, not readiness; no fixture was rerun for setup.

# NEXT WORK

Use MCP and observer-delivered results; never submit merely to test setup. Preserve existing author/session/message identities. Investigate token savings only with comparable evidence. Resolve dashboard cache expiry separately; do not replace workflow fixes with label latches.
