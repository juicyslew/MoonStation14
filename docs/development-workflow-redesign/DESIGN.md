# Deterministic validation and explicit review protocol

Status: **design only**. Nothing described here has been installed or runtime-tested. This replaces orchestration inside the current workflow; it is not a second pipeline alongside it.

## 1. Decision

Use a **program-owned validation pipeline with narrowly scoped AI review jobs**. Eliminate the integration-lead AI as a dispatcher, test runner, callback interpreter, and finalization coordinator. Retain independent AI judgment for implementation, SS14 parity/composition, coverage, and applicable visual fidelity.

One coordinator owns admission, task dependencies, execution, review assignment, cancellation, results and delivery. Agents never hand responsibility to one another through prose. Natural-language findings explain decisions; they cannot change workflow state.

The author finishes a sprint, submits once, and remains responsible for that sprint. While validation runs, its next action is explicitly `WAIT_FOR_RESULT`. There is no instruction to find unrelated work. Integration retains responsibility until an actionable result is durably delivered.

### Submission means the entire feature is finished

Integration is a **final owner-readiness gate**, not a development loop or milestone service. The submission question is: **"If this passes, can the owner test the complete requested feature immediately, with no implementation work still planned?"** If not, keep developing; do not submit.

- Finish the whole agreed sprint/feature scope: live wiring, applicable assets/provenance, SS14 composition/parity, networking/prediction boundaries, tests and runnable owner setup. Honest unverified manual behavior may remain for owner testing; missing implementation may not. Only explicit owner-approved deferrals change the agreed scope.
- Use cheap, focused local compilation/unit checks during implementation. Local tests are not integration submissions and should not be discouraged. The shared integration runner is not a way to discover whether an unfinished increment builds or whether an uninvestigated fix might work.
- Submission is explicit and final-readiness-only. File edits, commits, intermediate milestones, local tests and agent turn completion never automatically trigger integration validation.
- Require a concise owner-ready declaration and criterion-to-evidence completion matrix. Every agreed implementation criterion must be complete, with evidence references and a usable manual test path. Preflight rejects declared TODOs, missing required completion entries, unavailable setup and intermediate/checkpoint submission purposes before creating a run. This is necessary admission evidence, not a claim that code can prove arbitrary feature completeness; independent review still verifies it.
- A FAIL starts a correction phase: inspect all available findings, investigate the cause, complete all required fixes and run appropriate focused local checks. Resubmit only when the entire feature is owner-ready again—not after each finding, speculative edit or individual test repair. Fail-fast execution can still reveal a later genuine defect on the next attempt; do not hide it to meet a revision target.
- An ERROR or delivery problem is recovered on the existing run as specified below; it is never a reason for the author to manufacture another revision or rerun a full suite.

There is no arbitrary revision quota and no weaker validation to reduce counts. Record integration attempts, actual changed inputs, failure categories and expensive-run counts per sprint to identify premature submissions or workflow-caused repetition. The goal is fewer unnecessary full gates, not fewer useful local tests or concealed failures.

## 2. Outcomes and responsibility

Every completed evaluation has exactly one result:

| Result | Meaning | Required feedback | Responsible next actor |
|---|---|---|---|
| PASS | All required coded checks and independent reviews passed for the frozen input | Concise evidence summary and owner test instructions | Owner for manual acceptance |
| FAIL | A submission, check or review identified a specific unmet requirement | Criterion, concrete finding, evidence reference, correction required | Author |
| ERROR | The validation system could not reach a trustworthy result | Exact fault code, failed operation, retained evidence, recovery owner | Automatic recovery policy first; owner only for an explicit unresolved decision/fault |

PASS means ready for owner testing, never owner acceptance. Pending is not a result. Runtime phases such as queued, checking, reviewing, stopping and delivering are necessary operational states, not additional verdicts. A user withdrawal is a recorded control operation, not a fabricated test result; canceled child tasks have cancellation dispositions, not PASS or a spurious ERROR.

Examples: compiler diagnostics/test assertions/reviewed code defects are FAIL. Missing required artifacts, contradictory protocol state, an unavailable execution service beyond its retry allowance, or a reviewer exiting without submitting a result are ERROR. Expected cancellation after another task fails is not an ERROR. A configured execution ceiling is an ERROR unless the check explicitly tests a performance limit as an acceptance criterion.

No bare `blocked`, `not ready`, `try another revision`, or `go do other work` responses. An ERROR must not masquerade as a request to fix gameplay code.

### Evaluation attempts and ERROR recovery

A run identifies immutable submitted inputs. Each **evaluation attempt** has a monotonically increasing generation and exactly one immutable terminal result: PASS, FAIL or ERROR. Its active phases are `QUEUED → CHECKING → REVIEWING → STOPPING → DECIDED`; phases with no applicable work may be skipped. `STOPPING` closes child admission and settles cleanup. Cleanup failure produces ERROR and continued tracked cleanup supervision, not a claim that all children stopped. Result delivery/acknowledgement is tracked separately from evaluation phase.

PASS and FAIL attempts cannot be reopened. Corrected source/plan means a new submission. ERROR attempts cannot be reopened either: authorized recovery creates a **new evaluation generation on the same run**, explicitly linked to the prior ERROR. Authorization normally comes from a recorded, bounded automatic recovery policy; a genuinely ambiguous/destructive action requires an explicit owner decision. Recovery retains the same candidate and immutable source, and references still-valid successful check receipts instead of rerunning them. Prior task/results/events remain immutable; new or resumed work gets generation-bound task identities. Uncertain native work must be settled before any recovery execution.

The transaction creating a recovery generation advances the run's single `current_attempt` pointer and emits a `RECOVERY_STARTED` event to author and the status webpage. New result envelopes identify that generation and the prior ERROR they replace. Late results/acknowledgements can settle historical delivery records but cannot change the current attempt. No automatic resubmission, new candidate, or silent alteration of a previous verdict occurs. Recovery records its authorizing policy/owner decision, proven fresh inputs and the precise failed boundary; resolved errors retain their incident history. Normal recovery does not wait for the owner to acknowledge the notification.

A lost delivery after a valid PASS/FAIL is only a **delivery incident**, not an ERROR evaluation or a new evaluation generation. Retry that exact result event until acknowledged; never rerun validation to redeliver a result.

## 3. Technology and reuse

Keep Python and the official MCP SDK. Reuse the existing finite executor, shared execution mutex, fixed deadlines, process-identity/owned-tree cleanup, typed evidence collectors, prototype/readiness checks, receipt hashing, publication ownership verification and owner-notification integration points.

Replace the observer's scattered mutable JSON lifecycle decisions with **one local coordinator and one SQLite database**, using transactions, foreign keys, uniqueness constraints, WAL and durable commits. SQLite is adequate for this single-machine workload; no Redis, RabbitMQ, Temporal, cloud service or distributed consensus is warranted. This is a replacement for the current queue/state owner, not an additional competing owner.

The existing per-workspace stdio MCP adapters become thin clients of that coordinator through a loopback HTTP API (standard Python ASGI server and typed request validation). They do not cache separate mutable lifecycle runtimes. Contracts use versioned JSON Schema, validated in code; Pydantic is a suitable implementation. Current exchange JSON/HTML can remain read-only projections and artifact storage, never another authority.

The existing observer service becomes this event-driven coordinator. Keep its one-instance ownership guard and the existing execution backend. Do not launch a second validator or LSP/backend. Use the OS process supervisor to restart the coordinator; use a separate lightweight health observer in the owner-facing integration to detect coordinator loss. That observer cannot run jobs or decide verdicts.

**Everything is local except AI inference.** The database, coordinator, build/test processes, MCP/OpenCode adapters, artifacts and Integration Status webpage run on this computer. Loopback HTTP/SSE is local inter-process communication, not a remote service or internet dependency. Reading status, retaining progress, executing coded checks and performing cleanup do not require the AI provider or an open browser. If the local OpenCode process is restarting, retain deliveries locally and resume them when that process returns. If the AI provider fails, park only AI-dependent tasks; do not strand the local pipeline or reject otherwise valid input as a gameplay failure.

OpenCode's local API snapshot exposes SSE `/api/event`, queued inbox management, and session interruption. SSE is explicitly **volatile**: disconnected events can be missed. It is a latency optimization, not durable delivery. Reconcile saved task/delivery IDs on reconnect/startup and use bounded code-only deadline checks as a safety net. Never infer completion from SSE alone.

## 4. Admission before integration work

Provide one submit operation backed by a reusable local preflight command. The submission handler always repeats authoritative preflight; agents cannot bypass it by skipping the command. Reuse the existing monotonic submit budget, bounded Git execution and cleanup. Do not promise instant full-worktree hashing.

Preflight checks, without waking any reviewer:

- Schema, required fields, criterion IDs, artifact existence, selectors and check-pack coverage.
- Exact author/worktree/source identity and source freshness.
- Explicit review policy and required evidence; no missing manual setup or unfinished feature claimed complete.
- Final-owner-readiness purpose and completed implementation-criterion matrix; reject checkpoints or remaining declared implementation work.
- The **fully rendered** bounded review envelope for every required role, not merely the author's free-text brief. Return the offending field/size/limit before admission. Detailed files, plans and logs are artifact references, not duplicated prompt text.
- Workflow-managed configuration provenance against exact worktree-specific publication hashes. A match is a coded PASS; an unrecognized gameplay-author change requires an actionable submission FAIL or an explicit authorized tooling-change provenance record. Unknown/corrupt publication evidence is ERROR, not a guessed authorization decision.
- Local coordinator/backend capabilities. If the coordinator cannot durably accept a submission, return a concise ERROR and retain a local incident through the existing client/health surface. Temporary AI-provider unavailability does not prevent admission or coded checks: it is an explicit recoverable AI wait, shown on the webpage, not a lost request or a gameplay FAIL.

Rejected preflight creates no admitted validation task, lead, reviewer or native job. It does persist a submission-response/error incident so a lost tool reply is recoverable by submission ID.

The accepted transaction freezes the resolved plan, review policy and input manifest, creates the run and its first executable task, and commits its dispatch event. Only then return `ACCEPTED` with run ID, phase, queue reason/position if relevant and `WAIT_FOR_RESULT`. This is an admission acknowledgement, not validation PASS.

Duplicate submit requests with the same idempotency key and identical input return the existing response/run. A reused key with different input is rejected explicitly. A client timeout is never permission to invent a replacement candidate.

### Author-owned scope registration (WR12 owner decision)

The bound coding author registers the agreed feature scope through a validated local operation before submitting. This must not require an integration AI, routine owner approval, or restarting/reconfiguring the coordinator for each feature. Python freezes the per-feature policy and evidence metadata and returns an idempotent receipt. New features and corrected-source submissions can register fresh evidence; they cannot overwrite an earlier registration or frozen run.

The server retains author/worktree identity, publication provenance and shared minimum check/review requirements. Registration cannot create owner-only full-suite exceptions or authorize configuration drift. Submission selects the exact caller-bound registration using its submission key, sprint and policy digest, then repeats existing authoritative source, artifact, plan and provenance checks. Unknown registration outcomes are reconciled by receipt lookup, never a replacement key or blind rerun. This replaces the initial startup-only per-feature context limitation; the original fixed-context fixtures do not establish this normal author flow.

**Automatic coding-chat access (explicit owner decision):** retain the old per-call worktree/agent identity checks rather than requiring a preselected coding-chat roster. Configure worktree-level floors and credentials; verify each supplied session against its actual worktree and configured primary author role using the existing identity verifier. Derive separate caller identities for chats sharing a worktree. Never mutate a global binding to the last caller or create, restart or resume author chats. The existing author `status` tool may expose bounded, non-secret preparation context after verification; it must not expose another chat's receipts. Reviewer sessions are bound to individual review assignments, not permanent role chats. This normal author flow is required before activation; the initial static-author fixtures do not establish it.

## 5. Pipeline order and reusable check packs

```text
preflight
  → cheap coded validation
  → affected JVM/readiness/focused native checks
  → fresh packaging for owner testing
  → independent AI review task(s)
  → coded aggregate decision
  → verified cleanup and durable result delivery
```

No AI reviewer is started before **all required coded checks pass**. Thus there can be no review rejecting a historical 'tests pending' summary while the real checks are still running. Inputs to reviews contain only the current completed, verified receipt.

**Execution capacity (explicit owner decision):** only one submission executes globally across coding authors and worktrees, including post-acceptance regression. Other accepted submissions remain durably queued. Checks and independent reviewer roles inside that submission may run in parallel where useful; this is not a one-reviewer-at-a-time restriction. Preserve cheap rejection before expensive checks. The existing transactional attempt lifecycle admits execution and retains the lane through checking, review, stopping and provider pauses. Terminal ERROR may retain unresolved cleanup; its active resources/tasks still block another submission. Release execution capacity after proven cleanup and decision/closure, without waiting for manual owner acceptance. Native process-resource protections remain separate.

**Fresh review context (explicit owner decision):** reuse four dedicated role profiles, not four permanent sessions. Reserve a deterministic session identity for each independent task/evaluation/assignment generation and create it with the dedicated role agent only after the coded-check gate passes. A reserved ID is not proof of session creation. Register immutable creation intent in SQLite before the external create request; reconcile uncertain outcomes through exact-ID GET and verified task, role, worktree and ownership metadata. Never blindly recreate after an uncertain request. Same-task transport/provider retries retain that session; a new independent assignment gets a fresh context. Retain completed chats as evidence. Coding-author creation, replacement and restart remain excluded.

Reserve the existing task-bound review-session resource transactionally **before** any session-create request. Creation, prompt emission and cancellation share the existing session effect guard; recheck current assignment eligibility before each external effect. An ambiguous create retains its resource and cannot become a database-only “never started” cancellation. A verified but unprompted session may be settled only with strict owned identity, inactive status, empty inbox and complete empty history evidence. Record verified creation facts even if cancellation now prevents prompting. A valid prompt receipt is applied; otherwise **registered** baseline/receipt evidence, including corrupt or missing backing files, forbids replay or a no-effect cleanup claim. SQLite registration absence and artifact validation failure are different facts.

Reviewer tools carry the assigned session identity separately from their strict protocol body; the coordinator verifies it against the current task and actual session. Role credentials alone do not select a permanent chat. Binding plans name an explicit coordinator HTTP listener separately from the OpenCode API URL; MCP adapters must target the former. Historical generic-agent/model receipts establish provenance, not the identity of a newly created dedicated reviewer session.

Use a versioned registry of project-local **check packs**: affected unit tests, affected readiness selectors, focused tagged GameTests, fresh clean/build/package, manifest/provenance and configuration-ownership verification. Authors specify acceptance criteria, required packs/selectors and justified exceptional checks; code resolves the exact command/evidence plan and checks policy coverage. Preserve author ownership of check intent; the AI cannot edit the frozen plan. Do not assume filename heuristics prove semantic coverage.

### Full GameTests are post-acceptance, not the feature gate

The ordinary owner-readiness gate validates the feature's core behavior and directly affected integration boundaries. It does **not** run the full GameTest suite. Neither an author nor a reviewer may silently add a full suite as a pre-acceptance requirement; an exception requires the owner's explicit request. Missing focused coverage is addressed by meaningful targeted tests, not by routinely substituting a full suite.

After an explicit owner acceptance, code queues a separate, code-only **post-acceptance regression job** for the accepted snapshot. The webpage separately shows `Feature: accepted` and `Regression: queued/running/PASS/FAIL/ERROR`. Prior feature-gate results and owner acceptance are not rewritten by this later result. A regression FAIL delivers concrete corrective work to the same author; it does not pretend the feature gate is still pending or automatically invalidate the historical acceptance. Behavior-changing corrections may require another focused owner check.

Post-acceptance regression is idempotently keyed to the acceptance and source snapshot; repeated acceptance events do not rerun it. It uses the existing native execution slot and fail-fast evidence machinery, without spawning an integration-lead or repeating completed feature checks. Preserve an executable accepted source snapshot before later edits, or reserve that worktree until the regression job finishes; never run it against silently changed sources. Any needed new correction submission remains a complete-feature focused gate, not another pre-owner full suite.

This is the proposed replacement workflow policy. Implementation must update the applicable workflow instructions/check-pack defaults together; existing full-suite instructions must not remain as a conflicting authority.

Keep the current typed producers and sequential fail-fast runner. After a validated failure, do not start later checks. Stop an active check through existing bounded owned-process cleanup when cancellation is required. Never infer PASS from exit zero or invent missing counts. Preserve filtered/full and selected/total counts. Do not let an AI rerun Gradle, reparse huge logs repeatedly, or restate standard check commands.

Run each check at most once per run/attempt. Repeated submit, review delivery and result delivery reuse the same task and receipt. There is no speculative cross-candidate result cache; evidence reuse requires exact input/plan/toolchain binding. A reviewer retry cannot rerun native checks. Resuming an uncertain native execution requires verified process/cleanup state and explicit authorized recovery, not an automatic duplicate attempt.

## 6. Minimal authoritative records

- `runs`: run ID, author/sprint, immutable input/plan/policy digests, phase, result, sequence number, current responsible actor.
- `attempts`: evaluation generation, parent run, prior ERROR/recovery authorization if applicable, phase and immutable terminal result. Run phase/result are projections of its single current attempt.
- `tasks`: task ID, parent run, kind, dependencies, assigned session/process identity, lease generation, deadline, state, input/result digests and cancellation state.
- `artifacts`: immutable references, hashes, producer task and evidence type. Large logs stay outside SQLite.
- `deliveries`: event ID, exact payload hash, target, delivery attempts, transport receipt, protocol acknowledgement and deadline.
- `outbox`: committed events awaiting dispatch. Result/state updates and their events are written in the **same transaction**.
- `incidents`: ERROR code/context, affected run/tasks, owner/author alert delivery and acknowledgement, recovery action/audit trail.
- `sprints`: agreed scope/criteria, current author session, pending finding IDs, result acknowledgement and next responsible actor/action. Store this outside the source snapshot so workflow records do not stale the candidate. Automatic author restart/checkpoint supervision is deferred.

Every externally visible handoff has a stable event ID. Unique constraints prevent a second logical job, reviewer assignment, result or acknowledgement for the same identity. Use conditional transitions/sequence numbers and task lease generations; late or duplicate messages cannot update a canceled, superseded or completed task.

No activity TTL or model 'working/idle' flag changes who owns the run. Those observations inform timeouts; they cannot manufacture a verdict or erase responsibility.

## 7. Communication protocol

The coordinator sends a small typed envelope containing:

```text
protocol_version, event_id, run_id, evaluation_generation, task_id, input_digest,
task_kind, assignment_generation, recipient_binding,
artifact_refs, deadline, allowed_result_schema, required_next_action
```

A normal worker receives a concise objective and a bounded evidence summary plus references. It does not receive whole serialized check plans, hundreds of inline file entries or raw suite logs. References remain available for substantive source/parity inspection; concision must not remove evidence access.

Review tools expose only the task contract:

- `claim_task(task_id, generation, input_digest)` — durable acknowledgement that the assigned worker actually received this exact task. Transport acceptance alone does not count.
- `complete_review(task_id, generation, decision, findings, inspected_refs)` — `decision` is PASS or FAIL. FAIL requires criterion/location/evidence/correction. PASS requires review rationale/evidence, not merely 'tests green'.
- `report_error(task_id, generation, code, evidence_refs)` — a concrete system/input fault, not 'pending'.

The coordinator validates identity, generation, schema, artifact binding and admissible transition before committing a result. It responds with the accepted result ID/digest. Identical retries return that receipt without repeating effects; changed retries are rejected. Invalid arguments return exact field diagnostics without mutating state. A worker that cannot correct a call or exits without a valid result causes ERROR and direct escalation; prose is never scraped into a verdict.

Review finding categories are implementation, parity/composition, coverage and applicable fidelity—not orchestration states. `tests_pending` is not an admissible review failure code. A worker claiming a required artifact is missing must use `report_error` with that artifact ID; code checks whether it exists and returns its verified reference or records the actual missing-input fault. This prevents replaying the known pending-evidence failure as a valid protocol result. It cannot prove every natural-language code finding is semantically correct; independent judgment remains fallible.

The coordinator, not a lead model, aggregates results. Any valid FAIL stops the review group; all required PASS results permit PASS. Missing results never imply PASS. Additional required work discovered by a reviewer is a concrete coverage/criterion FAIL, not permission to create untracked child agents.

Authors receive the equally explicit `result_available` envelope and acknowledge it through `ack_result(event_id, result_digest)`. Code records the author's sprint next action: fix the listed findings, await owner acceptance, or await automatic recovery. Acknowledgement proves receipt/transfer, not that corrections or manual testing are completed. It does not require a follow-up prose acknowledgement. Section 12 specifies author feedback; automatically re-triggering a stopped coding agent is deferred.

## 8. Reliable delivery and honest guarantees

Use a **transactional outbox + at-least-once delivery + idempotent consumers**. Do not claim exactly-once network delivery. The enforceable property is one logical state transition/effect despite duplicate delivery, with every incomplete handoff durably visible.

Dispatch immediately after commit using an in-process wake signal; task/worker completion posts directly to the coordinator. SSE handles external session changes. Code reconciles durable IDs after disconnects and monitors deadlines independently of model turns. A lost wake signal cannot lose the saved task.

Track three distinct facts: transport accepted, worker/author protocol acknowledged, task result accepted. None substitutes for another. A model returning to idle without `complete_review` is an unfinished assigned task, not an actionable author failure. Expiry of a **worker-task delivery, claim or execution deadline during evaluation** produces a precise ERROR for that attempt; do not send vague repeated 'please finish' prompts or count an ACK as successful review. Delivery of an already decided result is different: its deadline creates a delivery incident only, never changes the verdict and never creates a new evaluation generation.

Author busy/queued delivery is not a failed validation result. Keep the result and its pending acknowledgement visible; execute the existing queued delivery when appropriate. On an idle author, target result acknowledgement within 10 seconds. A pending result beyond the configured delivery deadline raises a delivery incident on the webpage without overwriting the validation result or rerunning work. Default escalation deadline: 60 seconds for an idle available recipient. If the local OpenCode process or AI provider is unavailable, preserve work and automatically resume delivery/execution after recovery; do not require the owner to be online or the webpage to be open.

Guarantees under a functioning local disk/runtime: accepted work is durable; no silent handoff completion; duplicate messages cannot duplicate logical work; missing acknowledgement/result reaches a durable ERROR/incident with alerts; terminal attempt results cannot be reopened. ERROR recovery uses an explicitly linked new generation, never mutation of an old result. After abrupt process loss, recover metadata and reconcile identities before dispatch. Unknown cleanup fails closed.

No design can guarantee a model obeys, a disk never fails, or an unattended owner reads an alert. Owner presence and internet connectivity are not requirements for the local workflow. The guarantee is **acknowledged progress, automatic recoverable waiting, or a visible owned fault**, not magical delivery or guaranteed correct AI judgment. A supervisor detects coordinator loss; disk/service failure must be independently visible on the local status surface. Establish those paths with actual fault tests before calling the workflow reliable.

## 9. Immediate dispatch and latency budget

Remove the current normal 30-second gate polling dependency. There is no lead model to wake at submission. Admit/queue with a direct coordinator call, wake the executor immediately, and dispatch AI tasks only when their prerequisites have committed.

Targets, measured separately on this machine:

- Acceptance transaction to scheduler dispatch: p95 below 100 ms.
- Ready task to backend request, when capacity is available: p95 below 1 second.
- Completion commit to result/review notification: p95 below 1 second.

Report preflight duration, execution queue delay, backend request delay and model startup separately. Native serialization, large source hashing, provider latency and a busy author cannot honestly be called instant. Queue delays show their actual reason; no unexplained observer lag. A small code-only watchdog/reconnect reconciliation is allowed, not model status polling or recurring agent wakeups.

## 10. Structured cancellation: no hanging reviewers

The coordinator owns every review session, queued prompt and native process as children of its run. AI workers cannot create reviewers or issue independent validation commands.

On FAIL, ERROR, withdrawal or supersession:

1. Atomically close admission to new child tasks and invalidate outstanding task generations.
2. Cancel queued OpenCode inbox items; interrupt active sessions through the verified adapter. Revoke their completion authority immediately.
3. Stop owned native descendants through existing identity-checked bounded cleanup; never kill unrelated session/server trees.
4. Confirm each child is stopped or already completed; retain artifacts and cancellation receipts.
5. Only then finish cleanup/delivery. If a child cannot be stopped, report ERROR to author and owner with its identity; keep cleanup supervision and quarantine the affected resource. Do not claim clean completion or leave untracked token-burning work.

A coordinator crash triggers reconciliation of registered children before accepting new work; a worker disconnected from its lease must not continue indefinitely. Session interruption/queued cancellation and actual cessation are deployment acceptance tests, not assumptions based on HTTP success. If the OpenCode adapter cannot enforce or observe cancellation, this design is not technically accepted; prompting a child to stop is not an adequate substitute.

Do not depend on the model voluntarily noticing an expired lease. An OS-supervised restart/cleanup handler reuses the cancellation adapter and saved child registry to interrupt orphaned review sessions when coordinator ownership expires. It may cancel bound work and emit health incidents, but may not admit jobs or decide verdicts; there is still one scheduler. Restart reconciliation and this handler share cancellation fencing. The target for detecting coordinator loss and initiating orphan cancellation is 10 seconds. If the host or OpenCode service itself is unreachable, retain the incident and reconcile before new work; immediate remote cessation cannot honestly be guaranteed during that outage.

The integration-lead-stops/leaves-reviewer-running case disappears: there is no AI parent controlling lifecycle. Any remaining AI review-group failure is handled by the same coordinator-owned cancellation scope.

### Leave the machine and author usable

Cancellation is a normal control path, not a permanent poisoned-run flag. Transaction-scoped locks and OS-owned mutexes are released on every normal/exceptional exit; startup reconciles leftovers against actual process/session identity. Old marker files alone must never block future validation. Automatically clear a resource hold once verified cleanup proves the owned work has stopped, with its proof retained.

Use disposable per-run fixtures and existing worktree/artifact isolation. Scope quarantine to the affected resource, not the author account or all future work. Coding and safe independent checks remain available while a native slot is being recovered. An unresolved live native process must not be bypassed merely to make a new run appear possible; the cleanup supervisor keeps retrying safe identity-bound termination/reconciliation. An OS-level refusal or genuinely unprovable ownership is an explicit exceptional `Needs you`, not an ordinary cancellation outcome.

For provider failures, preserve the assigned task, source/evidence bindings and session checkpoint. Use a local circuit breaker and bounded backoff (initial target: 5/15/60 seconds, capped at two minutes) to re-establish availability without generating model turns. On recovery, resume the same bound session/task context with a generation-fenced continuation; do not restart completed checks or reread the entire sprint by default. Repeated authentication/configuration errors pause that dependency and show the exact repair needed rather than hammering the provider or switching models without authorization.

A provider that remains completely unavailable leaves AI work in `PAUSED — AI unavailable`, not an infinite model-retry loop. Exhaust a bounded connection-retry burst, then open the circuit; any automatic health probes must be non-generating and supported by the provider adapter. If none is available, wait for an explicit retry or an authoritative availability event rather than sending speculative inference requests. Preserve pending work across local restarts. The webpage and safe cancellation remain usable, coded checks can continue independently, and the affected task cannot hold a local execution mutex while waiting for AI. On recovery, resume pipeline-owned review work and deliver still-pending feedback; restarting a coding agent that already stopped after receiving feedback remains out of scope.

Recover automatically through recorded policies for transport retries, local service restart, missing acknowledgements and confirmed stopped-child cleanup. A genuinely incomplete native check may be retried only after verified cessation/cleanup, under an explicit bounded recovery policy and fresh fixture; successful checks are reused. Never blindly replay an uncertain native job. A repeated protocol violation or contradictory source/evidence remains a diagnosed ERROR, with the affected task retained and unrelated future work still usable.

## 11. Integration Status webpage is the owner's primary interface

Extend the existing `integration-status.html`/`gate_progress.py` surface rather than create another dashboard. Serve it locally from coordinator projections; it never decides state from model text, activity TTLs or stale-cache guesses. It displays progress without calling AI models. Update from local events, with a cheap reconnect/snapshot fallback. The webpage may be closed without interrupting any workflow.

Keep the existing **horizontal board of vertical lists**, with cards moving through authoritative workflow phases. Do not replace it with a dense table. Proposed columns: `Building → Queued → Checking → Reviewing → Your testing → Accepted`. A corrective FAIL returns its sprint to Building with a short `Fixing N findings` label. A paused/erroring task remains in its actual phase with a visible badge and next action; it must not jump to Building just because a model became idle. Post-acceptance regression appears on the Accepted card as a separate status. Canceled/completed history can be collapsed below the active board.

Concise default layout:

- **System strip:** local coordinator, AI availability/retrying, native runner occupied/free and queued count. Clearly say `Disconnected — last saved snapshot at …` if the page cannot reach the coordinator; never render an old snapshot as live health.
- **Needs you:** owner test/acceptance, a concrete scope decision, or an unrecoverable fault. Ordinary automatic recovery is visible elsewhere and does not require acknowledgement to proceed.
- **One compact card per active sprint:** short feature label (aim for 40 characters; never use the full generated sprint title as the heading), agent/revision, current step/progress, and responsible actor/next action. Default to four short lines plus one optional concise fault badge. Use ellipsis with the full value available in details; never obscure the required next action. Model running/idle is secondary information, not a workflow column decision.
- **Expandable detail:** failed criteria, actual versus expected test result, selected/total counts, concise error cause/recovery step, retained feedback/next action, receipt/report/log links, and a short event timeline. Distinguish `sent`, `acknowledged` and `result accepted`; do not show one generic delivered checkmark or claim corrective work occurred merely because feedback was received.
- **Actions:** open build/manual steps, accept the exact feature snapshot, request changes, cancel, and an explicitly safe recovery action only when needed. No generic reset/delete-lock/retry-everything button. Owner feedback is persisted and forwarded through the same author protocol, not an untracked chat.

Example default card (in Checking):

```text
Singularity gravity
Agent 01 · r33
3/5 checks · Focused GameTests
System running · 42s
```

Example Your testing card: short title/agent, `Focused checks + reviews PASS`, `You: test feature`. Full titles, long findings, raw paths/hashes, logs, detailed timing and the post-acceptance policy explanation live in the expanded detail view, not repeated on every card. Exceptions show a short readable cause such as `AI unavailable — paused` or `2 findings to fix`, with details one click away. Needs-you counts and important fault indicators remain visible without opening cards.

For every ERROR, commit one incident, a bound author notification, and a persistent visible webpage entry with code/cause, recovery owner, next automatic retry and whether owner action is required. New/unseen faults have a clear badge; an automatically resolved fault moves to concise history with its outcome. Acknowledging a fault marks it seen, not fixed. Direct notification to the owner's current OpenCode session may supplement the webpage but is not required for operation; desktop notification is optional. Retain resolved/unresolved error history across refresh/restart. The page's local health indicator and OS supervision expose coordinator loss even when the coordinator cannot publish a new incident.

Example:

```text
ERROR REVIEW_RESULT_MISSING — run R33, task parity-1
Reviewer ended without a protocol result. Checks remain PASS and are retained.
Recovery: resume the saved review through the configured recovery policy.
Author action: WAIT_FOR_RECOVERY. Do not change source or resubmit.
Incident E42; author delivery acknowledged; automatic recovery in progress.
```

Expected input rejection is concise FAIL feedback, not an owner emergency. A malformed generated review envelope or contradictory system state is ERROR to both author and webpage. Infrastructure recovery preserves the same run and completed evidence; gameplay changes require a new snapshot. Resolution never blindly reruns checks.

## 12. Author feedback; stopped-agent re-triggering deferred

Persist the sprint's agreed scope/criteria, source/worktree identity, pending feedback/finding IDs, result acknowledgement and next action. Deliver feedback to the existing bound author session through the normal durable prompt/inbox adapter. Preserve the same event/body for delivery retries, and do not confuse API ACK with protocol acknowledgement. Do not interrupt unrelated sessions or quietly assign the sprint to another model.

Default feedback routing is code, not interpretation:

| Event | Durable author action |
|---|---|
| Preflight FAIL | Correct the specified submission/readiness defects; do not create a run |
| Check/review FAIL or owner requests changes | Receive and acknowledge all current findings; investigate/fix them, run focused local checks, and resubmit only when the whole feature is ready again |
| Focused gate PASS | Present the ready build/manual steps on the webpage and wait for owner feedback; do not resubmit or invent another sprint |
| Recoverable ERROR | Retain the sprint and wait on the named recovering task; automatic recovery supplies the next event |
| Owner acceptance | Record acceptance and schedule the separate post-acceptance regression job; do not rerun the feature gate |
| Post-acceptance regression FAIL | Resume concrete regression correction, preserving the accepted-feature history |
| Owner cancels sprint | Stop owned child work, retain the current work/feedback records and release the assignment cleanly |

Normal initial feedback delivery may schedule the existing author session through `resume:true`; that is delivery of new work, not supervision of a stopped author. If an author subsequently stops after receiving feedback, retain the findings/next action and show `Feedback received · author idle` as secondary detail. Do **not** automatically restart it, send nudges, reconstruct its session, impose progress-checkpoint tools, or create a replacement coding agent. Those capabilities are explicitly deferred, not promises of this implementation. Model idleness does not falsely complete the sprint, change the validation verdict or poison future pipeline work.

A gate PASS leaves the feature waiting for owner testing. Owner acceptance transitions it to accepted with the linked post-acceptance regression responsibility still tracked until settled (or waived). The page retains actionable feedback and the bound session link if manual continuation is needed. A stopped author must not be misreported as an integration system ERROR merely because it is idle; unresolved delivery/protocol faults remain separately diagnosed.

Author-role instructions still specify complete-feature-only submission, concise result acknowledgement, concrete corrective feedback, no unrestricted native validation runs and no interpreting a system ERROR as a gameplay defect. Human input is reserved for material scope/behavior decisions or actual faults; there is no generic 'go do other work' instruction. Future automatic author supervision would require a separate design/acceptance scope.

## 13. R32/R33 under this design

- R32 oversized brief: rendered-envelope preflight returns field/limit diagnostics before admission. No integration model or reviewer is started.
- R33 pending-test review rejection: impossible ordering; reviewers start only after the exact completed receipt is verified, and are not asked to re-decide check completion.
- R33 collected `needs_changes` misunderstood as pending: a typed FAIL with concrete findings, not a status string. No lead has to infer what to do. A report citing missing artifacts that are present is a diagnosed evidence/protocol inconsistency for the recovery policy; request a human decision only if it cannot be reconciled safely, rather than inventing a gameplay correction.
- R33 config question: exact worktree hash/provenance verification is a scripted check before review. Agent 01's current config hash `c80e427531dc3b9c9de9911c4eaaad15463c87c4327c6f00c06fad7325421d12` matches its managed publication receipt. MAIN has a different tailored config; its hash is not a substitute. A Git diff against an older base alone cannot establish unauthorized changes.
- Lead idles after explaining a refusal: no heuristic 'stopped after one continuation' handoff. The exact failed task/result acceptance and responsibility remain recorded; system faults go directly to the owner and author.

## 14. Acceptance and sequencing

Implement later as coherent units: (1) contracts/preflight/focused check packs and full-suite policy, (2) transactional coordinator/outbox and thin adapters, (3) typed reviews and author feedback delivery, cancellation/recovery and the existing list-board webpage, (4) end-to-end fault tests and latency measurement, (5) controlled cutover. Automatic stopped-author supervision is excluded. This document is not authorization to implement.

Reuse existing executor/evidence tests and add fault-injection tests for every boundary: lost submit reply; duplicate submit; crash before/after outbox commit; lost SSE and reconnect; duplicate/out-of-order delivery; API ACK without claim; reviewer exit without result; stale-generation result; failed check preventing all AI startup; contradictory input evidence; first review failure canceling siblings; coordinator loss; cancellation refusal; lost author acknowledgement; browser closed; local OpenCode restart; provider unavailable then recovered; exact managed-config provenance; oversize fully rendered envelopes; and a complete focused PASS to manual owner acceptance and separate regression.

Acceptance requires actual OpenCode process/API tests for queued cancellation, active interruption, structured acknowledgements and observed cessation; acknowledged initial author feedback delivery; persistent readable webpage incidents; and measured dispatch latency under idle and occupied native-runner conditions. Unit counts alone are insufficient. Prove native execution is not duplicated after retry/restart, that a stopped review leaves no running child, and that a canceled/erroring run does not prevent a later safe validation run without manual lock/queue repair.

Admission tests must also reject incomplete/checkpoint submissions without creating jobs or reviewers; prove ordinary file edits and local tests do not auto-submit; and prove protocol/transport recovery does not create a new candidate or repeat a full validation run.

Prove the ordinary pre-acceptance plan cannot select full GameTests without an explicit owner exception; a single owner acceptance queues exactly one snapshot-bound regression job. Prove FAIL feedback is durably delivered to the correct author, PASS waits for actual owner input without model polling, and an author idling after feedback is not automatically re-triggered. Prove total provider failure safely pauses AI work without unbounded inference attempts or a held local execution slot; local status/cancel remain usable. The board must show correct responsibilities even when models are idle, keep full sprint titles/details out of compact cards, and preserve readable next actions.

At cutover, stop new intake, resolve/drain existing runs explicitly, preserve historical receipts/archives, stop the old scheduler, install/start the single replacement coordinator and verify acknowledgements before reopening intake. Never run both authoritative pipelines, rewrite owner configuration blindly, or rerun historical validation merely to migrate tooling. Detailed migration machinery is unnecessary unless a concrete active run requires it.

## 15. Evidence and unresolved implementation checks

Orientation/reuse: `docs/development-workflow/HANDOFF.md`; `tooling/agent_workflow/{workflow_runtime,workflow_mcp,gate_execution,gate_dispatch,gate_liveness,workflow_observer,validation_evidence,owner_attention,owner_feedback,publish_workflow}.py` (publisher is `.ps1`). Incident evidence: `build/agent-workflow/r33-explanation/` and `r31-pending-verdict-incident/`.

Parent verified the local OpenAPI snapshot at `build/agent-workflow/submit-timeout-repair/opencode-openapi.json`: `/api/event` explicitly describes volatile SSE; `/api/session/{sessionID}/inbox/{inboxID}` exposes cancel/update; `/api/session/{sessionID}/interrupt` exists. These establish available API surfaces, **not** successful end-to-end cancellation or durable consumption. Exact adapter semantics, owner-client integration and restart supervision remain implementation acceptance work. No runtime assets, services, configuration or live queue records were changed for this design.
