# Workflow redesign: sequential execution tasks

Authoritative requirements: [DESIGN.md](DESIGN.md), including the latest owner decisions. This file is the execution checklist, not an additional integration queue or scheduler. The actual work items are queued to MAIN session `ses_f094b6b6effebY7ImHpWRaxSJG`.

## Execution rules

- Execute **one numbered task at a time**, in order. Do not start a successor until its predecessor is technically READY. If multiple instructions are visible, focus only on the earliest incomplete task.
- Read AGENTS.md and relevant handoffs. Delegate scoped implementation to `implement` and independent verification to `review`; MAIN owns decisions and acceptance. Workers return evidence to MAIN, not duplicate queue entries or owner handoffs.
- Reuse the existing finite executor, typed producers, cleanup, delivery/notification integration points, publication receipts and status webpage. Replace orchestration; do not add a competing live pipeline.
- Work offline from the live workflow until task 12. Use isolated fixtures/databases/sessions; do not change active jobs, author sessions, live configuration or installed services as incidental validation.
- Preserve existing uncommitted changes. Git HEAD predates accepted tooling work; never restore workflow files from HEAD. No commits, merges, pushes, owner acceptance or native reruns are implied by this plan.
- This is process tooling, not mod implementation: do not submit these tasks to the mod coding integration gate. Focused Python/process/adapter checks are appropriate. Do not run full Minecraft GameTests to test the workflow redesign.
- Full GameTests belong to the post-owner-acceptance path. Ordinary submissions are complete-feature, focused owner-readiness gates. No automatic re-triggering of stopped coding authors. Keep the moving-list status board and compact cards. Everything except AI inference stays local.
- Each task updates its status/evidence here and records a concise completion note. READY requires the task's acceptance checks and independent review; a passing unrelated suite is not proof. BLOCKED records a precise cause and retained next action; it does not authorize skipping ahead.
- A failed task remains the current work unit until corrected/reviewed. Do not consume later tasks as substitutes for finishing it. No routine owner check-in between successful tasks; ask only for a material unresolved choice or destructive action.
- Continue the already-requested backlog autonomously: after one unit is READY, start the next requested unit in order. Receiving a later unit does not replace the earliest unfinished unit, and ending a unit is not a reason to wait for its request to be resent.

## Ordered list

| ID | Work unit | Depends on | Status |
|---|---|---|---|
| WR01 | Baseline, reuse map and final protocol contracts | — | READY |
| WR02 | Transactional state, task identity and durable outbox | WR01 | READY |
| WR03 | Complete-feature preflight and focused check packs | WR02 | READY |
| WR04 | Local coordinator and immediate thin-MCP admission | WR03 | READY |
| WR05 | Existing executor/evidence integration and fail-fast checks | WR04 | READY |
| WR06 | Post-check AI review protocol and coded aggregation | WR05 | READY |
| WR07 | Result delivery, owner decisions and post-acceptance regression | WR06 | READY |
| WR08 | Graceful cancellation, outages and automatic safe recovery | WR07 | READY |
| WR09 | Readable moving-list Integration Status board | WR08 | READY |
| WR10 | Role instructions, packaging and scoped publication | WR09 | READY |
| WR11 | End-to-end fault tests, latency and final technical review | WR10 | READY |
| WR12 | Controlled cutover and live verification | WR11 | IN_PROGRESS |

### WR01 — Baseline, reuse map and final protocol contracts

Read the design, current development-workflow handoff and applicable existing contracts. Capture current publication/configuration ownership, active run identities, evidence locations and protected uncommitted source. Resolve the exact reusable symbols and boundaries. Verify OpenCode prompt/inbox/event/interrupt capabilities using read-only source/API inspection; volatile SSE is not durable delivery.

Specify concrete schemas and transition tables for submissions, runs/attempt generations, tasks, artifacts, review results, feedback acknowledgements, cancellation, incidents and owner acceptance/regression. Explicitly define ERROR recovery versus immutable old results, delivery incidents versus validation results, and the deferred stopped-author supervision boundary.

**Done:** a concise implementation/reuse map and unambiguous contract fixtures exist; independent read-only review finds no unresolved phase/identity/ownership ambiguity. No live mutation or runtime activation.

### WR02 — Transactional state, task identity and durable outbox

Implement the local SQLite authoritative state repository using the WR01 contracts: atomic transitions/outbox, uniqueness/idempotency, generation fencing, attempt history, resource ownership and durable delivery/incident records. Keep artifacts external and hash-bound. JSON/HTML are projections, never another authority. Add only the required database/library dependencies.

**Done:** isolated tests cover duplicate requests/results, stale generations, conflicting idempotency keys, crash boundaries around commit/outbox, immutable prior results and linked ERROR recovery. Review confirms no duplicate source of truth or competing scheduler.

### WR03 — Complete-feature preflight and focused check packs

Implement authoritative preflight and a reusable local check entry point. Validate complete-feature purpose, criterion completion, required artifacts/owner setup, identities, exact managed-config provenance, selectors/coverage and fully rendered review-envelope size. Add reusable typed check-pack resolution using existing producers. Reject ordinary pre-acceptance full GameTests unless an explicit owner exception is bound to the request.

**Done:** malformed/oversized/incomplete/checkpoint submissions fail before any run/worker/reviewer is admitted; valid requests freeze exact plans. Tests cover managed configuration, source/plan binding and the full-suite policy. No model needed for these checks.

### WR04 — Local coordinator and immediate thin-MCP admission

Turn the existing observer ownership point into the single local coordinator, backed by WR02 and WR03. Thin official-SDK MCP adapters call it; do not retain independently mutable workflow runtimes. Add direct post-commit dispatch, completion callbacks, volatile-event acceleration and durable reconnect reconciliation. Preserve program-owned submit/Git deadlines. Keep the new coordinator inactive against the live exchange.

**Done:** isolated SDK/process tests prove accepted submissions are durable, duplicate submits return one run, a lost reply is recoverable, and ready tasks dispatch without the old 30-second polling dependency. Initial dispatch timing is recorded. Local status/acceptance does not depend on AI-provider connectivity.

### WR05 — Existing executor/evidence integration and fail-fast checks

Adapt the existing finite execution owner, OS mutex, deadlines, process identities and typed receipts to coordinator tasks. Run affected check packs and packaging, stopping later work on a verified failure. Store concise evidence with exact artifacts/counts; do not reimplement native execution or typed evidence parsing. No AI reviewer starts until all required coded checks pass.

**Done:** Python/owned-process fixtures prove serialization, typed PASS/FAIL/ERROR, early stop, no reviewer on code failure, retry/idempotency without duplicate execution and reuse of completed evidence. No real full Minecraft suite is run for this task.

### WR06 — Post-check AI review protocol and coded aggregation

Implement coordinator-owned review assignments and scoped roles. Workers claim their exact task and submit typed PASS/FAIL findings or precise ERROR through the protocol. Inputs use current completed receipts, short summaries and artifact references. Eliminate lead-AI orchestration and untracked reviewer creation. Code aggregates required review results; pending tests are not an admissible review failure category.

**Done:** isolated adapter/session fixtures exercise claim versus API ACK, invalid result feedback, missing result, stale/duplicate results, disjoint reviewer identity and correct aggregation. The R33 pending-evidence/collected-rejection confusion cannot become an ambiguous handoff. Required runtime adapter semantics are explicitly tested or recorded as blocked, not assumed.

### WR07 — Result delivery, owner decisions and post-acceptance regression

Implement immutable result envelopes, idempotent author delivery and result acknowledgement. Keep findings/next action durable without automatically re-triggering authors who stop after receipt. Bind owner test/accept/request-changes/cancel actions to exact snapshots. Owner acceptance alone creates one separate code-only full-regression job; it does not reopen the focused gate or spawn another lead model.

**Done:** tests cover lost/duplicate delivery and ACK, no false consumption, no resubmission/rerun for delivery repair, no stopped-author nudges, one regression per acceptance and regression results separate from historical feature acceptance. A recoverable ERROR never tells the author to invent a new revision or unrelated work.

### WR08 — Graceful cancellation, outages and automatic safe recovery

Implement run-scoped cancellation, inbox cancellation/active-session interruption, task fencing and confirmed owned-process cleanup using existing helpers. Add supervised local restart/reconciliation, scoped resource holds and automatic release after verified cleanup. Handle complete AI-provider failure with bounded connection retries/circuit opening; preserve AI tasks without infinite inference attempts or held native locks. Recovery retains valid evidence and only resumes the affected boundary.

**Done:** real isolated process/API tests prove stopped children, no unrelated kills, recoverable provider failure/restart, no stale completion and a later safe run after cancellation/error without manual record/lock repair. Unknown cleanup is explicit and scoped; no unsafe bypass or permanent stale marker. Local status/cancel remains usable during provider failure. No coding-author restart feature is added.

### WR09 — Readable moving-list Integration Status board

Extend the existing local status webpage/projection. Preserve horizontal columns and moving cards: Building, Queued, Checking, Reviewing, Your testing, Accepted. Use short feature labels and compact default cards; long generated titles, findings, hashes and logs go in expanded details. Show next responsibility/action, concise faults/recovery, native queue/system health and separate focused/owner/regression states. Page disconnection must display saved-snapshot status, not fabricated live health.

**Done:** projection tests and browser/visual review show readable cards, correct phase movement independent of model TTLs, persistent error visibility, useful details/actions, no default wall of text and no AI calls to render status. A closed webpage does not stop the pipeline; an idle author is not automatically awakened or labeled a pipeline failure.

### WR10 — Role instructions, packaging and scoped publication

Update scoped author/reviewer contracts, README/administrative commands, dependency installation and receipt-based packaging for the completed architecture. Remove contradictory old lead-orchestration/full-suite-before-owner defaults. Preserve unrelated models, MCP servers, permissions and worktree customizations. Prepare publication/cutover and recovery instructions without activating them.

**Done:** installed-layout/permissions/schema/publisher dry-run tests agree with the actual tool contracts; receipt ownership and backed-up source hashes are verified. No new configuration conflicts, hidden broad rebinding, fallback shell handoffs or stopped-author supervision. All required artifacts are staged; the old live scheduler is still the sole live owner.

### WR11 — End-to-end fault tests, latency and final technical review

Run the complete relevant tooling/SDK/process suite and targeted real local OpenCode adapter tests in designated isolated fixtures. Exercise submission-to-feedback PASS/FAIL/ERROR and owner acceptance/regression with simulated/cheap producers, plus all design fault cases. Measure commit-to-dispatch/completion latency separately from preflight, resource queueing and model startup. Include full provider failure, cancellation refusal, restart, missed events/ACKs, oversize prompts and a subsequent healthy run.

**Done:** independent review accepts the frozen source/evidence against every design requirement, including cancellation cessation, no duplicate checks, concise board, full-suite policy and no author auto-retrigger. Failures are corrected before task 12. Retain useful failure evidence and remove only disposable validation artifacts. Python counts alone are not acceptance of untested adapter behavior.

### WR12 — Controlled cutover and live verification

Recheck the current live baseline; task 1's snapshot is not permission to overwrite later changes. Hold new intake, resolve/drain active work explicitly, preserve jobs/receipts/identities, and replace the old scheduler with the single verified coordinator. Publish only receipt-owned changes. Never disconnect required tools while dispatch remains active, run two schedulers, restore from Git HEAD or rerun old validation merely for adoption. Stop and report any genuinely destructive/unresolved live transition rather than improvising.

Verify actual worktree-bound tool discovery, one coordinator/runner ownership path, authoritative board state and initial feedback/acknowledgement using safe designated proof tasks—not manufactured gameplay submissions. Prove installation and real execution/receipt consumption separately from API ACK. Retain a scoped recovery/rollback record without replaying historical jobs.

**Done:** publication/hash/ownership and live adapter checks pass; no active source/evidence is lost or duplicated; no ordinary error/cancel strands future validation; the owner receives concise webpage/manual interaction steps and honest residual limits. Owner acceptance is still explicit and is not claimed by automation. No commits/merges/pushes are included.

## Execution evidence

Task notes, focused check counts and independent review references are added here as each unit completes. Queue admission alone is not execution or completion. The queue receipt is stored under `build/agent-workflow/workflow-redesign-plan/`.

### WR01 — READY

- Outputs: [baseline/reuse map](WR01_BASELINE.md), [protocol/transition contracts](CONTRACTS.md), `contracts/protocol.schema.json`, and indexed positive/negative fixtures.
- Captured Git/publication/config ownership, 54 protected source hashes, live identity/evidence indexes and exact OpenCode operation definitions. Six workflow MCP bindings connected; no active native job at capture. All 27 referenced receipts/current scoped reports matched recorded hashes. Preserved 27 pre-existing ownership drift rows; no incidental repair.
- Validation: Draft 2020-12 schema valid; **29/29 positive fixtures accepted and 29/29 negative fixtures rejected**, with full-root oneOf validation and 19 concrete contract kinds covered. Static fixtures do not prove cross-record/transactional/adapter behavior.
- Independent review `ses_ebb04830effexzK3Zfcfl7w0fl`: final PASS after correcting actionable owner feedback and operation-bound receipt next actions. No unresolved WR01 contract/phase/identity ambiguity reported.
- Evidence: `build/agent-workflow/workflow-redesign-plan/wr01/{baseline.json,baseline-summary.json,api-readonly-baseline.json,process-baseline.json,evidence-references.json,protection-check.json,contract-fixtures.log,acceptance.json}`. This is historical WR01 acceptance; WR02's authorized contract erratum is recorded below.
- No production source, installed configuration, service, live job/queue or publication change. No runtime activation, native validation, commit/merge/push, or owner acceptance. Live baseline must be refreshed before cutover.

### WR02 — READY

- MAIN accepts the inactive SQLite repository in `tooling/agent_workflow/workflow_store.py`; API and trust boundaries: [WR02_STORAGE.md](WR02_STORAGE.md). No live consumer, competing scheduler or automatic execution was added. WR03–WR06 remain queued; this completion does not execute them.
- Atomic admission/rejection, task results, terminal decisions, delivery/incident state and outbox intents; strict identity/idempotency/generation fences; retained attempt/resource history; registered artifact provenance; typed recipient/transport/protocol ACK separation. ERROR rejection incidents are durable without inventing admitted runs. Unknown resource ownership cannot expire or be stolen.
- Linked ERROR recovery preserves immutable old results and references original successful CHECK tasks/receipts without copied executions. Required CHECK obligations survive partial reuse and consecutive ERROR generations; missing work cannot disappear. Shared admission fixtures replace duplicated setup; superseded prototype tests were replaced by typed lifecycle and real subprocess commit-boundary tests, retaining unique parser/schema/logical-slot/concurrent-outbox assertions.
- Focused validation: **77 selected / 77 passed / 0 failed** repository tests, independently rerun; Python compilation and scoped diff whitespace checks passed. Full legacy tooling suites and Minecraft GameTests were not run for this isolated storage unit.
- Authorized unpublished-v1 contract erratum: STOPPING requires null committed result/closure and exactly one proposal. The original schema both required and forbade `result`; the four contract JSON files intentionally changed, with five positive and five negative fixtures added. **34/34 positive accepted, 34/34 negative rejected**; independent erratum review `ses_eba87042cffe3dY0Q2iYR646Dg` PASS. Original WR01 hashes remain historical evidence, not current hashes.
- Final whole-unit review `ses_eba752004ffe9U4L2Y4lWnMkvV` **PASS**, after fixing incident-seen transaction atomicity and adding rollback/retry tests for both seen and resolution. The reviewer's initial resolution-transaction concern was explicitly withdrawn: that method was already transactional. Earlier initial-candidate and recovery-obligation findings are superseded by corrected source and final review, not by overlapping focused counts.
- Preservation: 54 protected old sources, six configurations, 108 installed ownership rows and publication pointer/receipt remain unchanged. No live jobs, services, configuration, queue entries or installed assets were changed; no activation, mod coding gate, commits/merges/pushes or owner acceptance.
- Evidence: `build/agent-workflow/workflow-redesign-plan/wr02/{acceptance.json,final-review.json,final-review.log,incident-atomicity-tests.log,recovery-obligations-tests.log,reuse-complete-tests.log,contract-erratum-tests.log}`; baseline and earlier failure evidence retained there. Adapter/source-byte verification, actual process cessation, provider recovery, author consumption and owner acceptance remain later-unit boundaries, not guarantees established by these SQLite tests.

### WR03 — READY

- Outputs: `workflow_{check_packs,preflight,submission}.py`, new `contracts/preflight.schema.json`, shared real-Git fixtures/tests, and [API/trust-boundary notes](WR03_PREFLIGHT.md). New store APIs only retrieve exact receipts/admitted artifact references and derive submission IDs; no second lifecycle store or scheduler.
- The mandatory new admission facade verifies complete-feature declarations, agreed criteria/evidence/manual setup, real source/plan/policy digests, exact tailored publication provenance and whole rendered review inputs before admitting a run. Typed version-1 packs reuse existing plan validation/producer shapes. Trusted selector minimums cannot be replaced by unrelated request selectors; full GameTests need an exact trusted owner exception.
- Exact retries preserve original receipts, including after source changes. Cross-binding accepted replays are denied; generated artifact IDs include submission ownership. Immutable external bundles/evidence are retained; expected FAIL and system ERROR rejections create no tasks, with durable incidents/page intents for ERROR. Storage failure is reported without fabricating durability.
- Final independent review `ses_eb9ba6b19ffeqjuDIcO3nRC1pE` **PASS**: **128 selected / 128 passed / 0 failed** (51 WR03 and 77 store regression tests), plus **34/34 positive and 34/34 negative** WR01 static fixtures. The earlier required-selector review finding was fixed, not deferred. Focused checks include real disposable Git repos, CLI subprocesses, source/staging faults, config tailoring, malformed input, oversize payloads and retry/authority fences.
- Evidence: `build/agent-workflow/workflow-redesign-plan/wr03/{acceptance.json,final-review.json,final-review.log,baseline.json,artifact-replay-fence-tests.log,selector-binding-tests.log}`. Existing old runtime/configuration/installed publication remains untouched; WR01 contract files unchanged. No actual native validation, live activation, owner acceptance, gates or commits.
- Actual OpenCode authentication and coordinator/executor/transport integration remain later-unit boundaries; the local context file is explicitly a trusted administrative input, not proof of API authentication. WR04 follows automatically.

### WR04 — READY

- Outputs: `workflow_coordinator.py`, `workflow_coordinator_mcp.py`, separate pinned coordinator requirements and focused tests; [ownership/API notes](WR04_COORDINATOR.md). Explicit loopback service configuration, per-database OS/process guards, immediate post-commit wake, saved-outbox startup/reconciliation and a two-tool official-SDK author adapter. Only public outbox/event reads were added to the repository.
- Trusted configured caller/context and existing session/role lookup helpers fence admission; status remains local and labels source freshness unverified. Retry/lost-response paths retain one run/effect; uncertain dispatch is observed before reposting, with durable incident/page intent rather than a fabricated verdict. Missing backend capabilities retain pending work.
- Final independent review `ses_eb9a39d6dffexF0ZO53kWoPLeA` **PASS**: **149 selected / 149 passed / 0 failed / 0 skipped** (72 relevant workflow tests, 77 store tests). Includes actual official-SDK stdio against an owned loopback coordinator, real disposable Git/SQLite preflight, cross-process singleton denial/reuse, startup failure cleanup, strict HTTP requests, scope/retry fences and ambiguous-effect reconciliation.
- Evidence: `build/agent-workflow/workflow-redesign-plan/wr04/{acceptance.json,final-review.json,final-review.log,coordinator-sdk-tests.log,coordinator-startup-tests.log,coordinator-recovery-tests.log,latency-sample.json}`. Initial timing is one local fake-handler sample, not a p95/SLO or native queue/model-startup claim; post-admission-return timing is not instrumented.
- No native handler is configured by the WR04 starter; WR05 supplies the existing finite executor. Actual installed OpenCode authentication, live adapters, process cancellation and provider recovery remain later checks. Old live scheduler/configuration/publication remain untouched; no gates, native validation, owner acceptance or commits. WR05 follows automatically.

### WR05 — READY

- Outputs: shared `workflow_runtime.run_finite_checks`, SQL-bound `workflow_native_worker.py`, coordinator adapter `workflow_native_execution.py`, focused fixtures and [execution/ownership notes](WR05_EXECUTION.md). Legacy and new adapters call the same finite runner and existing typed parsers; no shadow candidate/job JSON authority. Small store additions are public reads only.
- Actual worker startup acknowledgement binds the interpreter identity before SQL dispatch (Windows venv launcher PID differs). Native claim/token/generation/input fences, OS serialization, SQL resource claims, owned child provenance, registered cleanup proofs and immutable external receipts/evidence are exercised. Evidence survives later package cleaning. Verified coded failures stop later checks; source/config/missing evidence/cleanup faults produce ERROR. No AI review is admitted by this unit.
- Final independent review `ses_eb9887ba3ffe3b7ylszkv13xxC` **PASS**: **207 selected / 207 passed / 0 failed / 0 errors / 0 skipped** on frozen source, including all prior store/workflow regressions, actual bounded Python workers, serialization, fail-fast, replay, typed producers, Windows batch launch and eight legacy e2e fixtures. Production launcher adaptation was proven with a fake wrapper, not actual Gradle.
- Evidence: `build/agent-workflow/workflow-redesign-plan/wr05/{acceptance.json,final-review.json,final-review.log,final-suite.json,final-suite.log,native-integration-tests.log,windows-wrapper-tests.log}`. Earlier failed identity and unisolated legacy-transport evidence is retained. The legacy fixture's unintended real GET failed HTTP 400; flat/package mock aliasing and fail-closed OpenCode subprocess guards were corrected before its passing isolated rerun. No live POST/native validation/publication was performed.
- Authorized local changes to old runtime are the shared-kernel extraction and fixed Windows wrapper resolution; installed runtime/configuration/publication remain unchanged. Child-launch crash-gap containment, unknown-resource recovery, cancellation/provider recovery and real installed adapter behavior remain WR08/WR11, not established here. No gates, owner acceptance or commits. WR06 follows automatically.

### WR06 — READY

- Outputs: `workflow_review{,_transport,_results,_pipeline}.py`, reviewer HTTP/official-SDK profile, actual factory integration and [review protocol notes](WR06_REVIEWS.md). Post-check roles, assignments and ready wakes are code-owned and policy-bound. Whole rendered inputs and current CHECK receipts are verified before prompt dispatch; API acknowledgement remains distinct from claim/result.
- Typed claims/results/errors are session/assignment/input/generation fenced. Invalid results return same-task `CORRECT_RESULT`; accepted ERROR commits task fault/incident/page intent atomically. Code aggregates accepted review results and delegates immutable terminal results/delivery intents to the existing store. Coded FAIL admits no reviewers; PASS-review rationale is excluded from FAIL findings.
- Final independent review `ses_eb951dcdcffe12ND0p0OnXRJV6` **PASS**: **237 distinct current tests passed, zero failures/errors/skips** — 236 unique full-suite tests on frozen production plus one independently verified, newly added deadline case; no overlapping rerun counts are added. All recorded production hashes were checked unchanged for the incremental case. Includes actual local official-SDK reviewer claim/result/status, two-role service PASS, code FAIL without reviewers, retries, invalid-result correction, factory/role guards and completion-wake reconciliation.
- Deadline evidence: pending before expiry; afterward `DECIDED/ERROR`, `UNRESOLVED` cleanup, a precise open `MISSING_REVIEW_RESULT` incident and `WAIT_FOR_RECOVERY`. The unanswered task remains `CANCEL_REQUESTED`, not falsely confirmed stopped. Reconciliation does not duplicate result/incident/tasks. Physical cessation/recovery remains WR08.
- Evidence: `build/agent-workflow/workflow-redesign-plan/wr06/{acceptance.json,final-review.json,final-review.log,full-suite.json,incremental-new-test.json,deadline-reconciliation-tests.log,claim-token-tests.log,review-factory-tests.log,review-service-tests.log,reviewer-sdk-tests.log}`. Independent review found option-like generated claim tokens broke worker CLI parsing; `--claim-token=<value>` and a real leading-dash worker regression fixed the cause, rather than treating startup failures as unrelated flakes. Earlier failures retained.
- Configured reviewer sessions are an explicit static pool; real OpenCode provisioning, role/MCP permissions, inbox/interrupt behavior remain unverified WR10–WR12 boundaries. No real API mutation, native Gradle/Minecraft, installation/publication, gates, owner acceptance or commits. WR07 follows automatically.

### WR07 — READY

- Continuing the existing backlog after WR06 READY. Reuse durable result/delivery/ACK records, author prompt identity conventions and existing owner-action/provenance boundaries. Result transport repair must never rerun validation or alter terminal verdicts. Owner acceptance creates a separate code-only regression obligation, not a reopened focused gate.
- Outputs: `workflow_result_delivery.py`, `workflow_owner.py`, `workflow_regression.py`, shared store delivery insertion and owner/regression authority, author ACK tool, scoped owner HTTP actions and deterministic admission-time source archives; [architecture and limits](WR07_DELIVERY.md). Feature-gate PASS, owner acceptance, regression verdict, API transport and author ACK remain distinct. Control feedback never fabricates a replacement feature FAIL.
- Actual fixtures prove coordinator-thread kindless terminal result → stable author prompt → ACK, and owner ACCEPT → unique regression admission → shared native execution from preserved source → separate regression finality. Real official-SDK profiles remain covered. Fake batch producers prove full/filtered counts without actual Gradle/Minecraft. Extra cache source, filtered exit-zero evidence, ambiguous/wrong-semantic source provenance and conflicting acceptance replay are rejected. Source mutation during archiving admits no run/task/resource.
- Final independent review `ses_eb8fef2bdffer9ARaQWL04q9la` **PASS**: **307 distinct current cases satisfied**, zero unresolved failures/errors/skips. The full run selected 307 unique cases after removing 18 duplicate IDs: 306 passed and one stale factory-handler assertion failed. The exact corrected selector then independently passed 1/1, with all 26 recorded production hashes unchanged; overlapping module reruns are not added to the count. The assertion now also proves the control handlers reuse the same author-delivery owner.
- Evidence: `build/agent-workflow/workflow-redesign-plan/wr07/{acceptance.json,final-review.json,final-review.log,full-suite.json,corrected-selector.json,corrected-selector.log,preservation-compile.json,feedback-loop-tests.log,regression-loop-tests.log,owner-provenance-guards.log,regression-input-guards.log,archive-mutation-tests.log}`. Original failures retained. Scoped compilation/diff and preservation checks passed: six configurations, 108 installed ownership rows, publication and four WR01 contract files unchanged.
- No stopped-author nudges, live prompt/session mutation, actual Minecraft/Gradle suite, installed runtime/publication change, coding gate, commit or real owner acceptance. Real adapter semantics and automatic cancellation/provider/UNKNOWN-resource recovery remain WR08/WR11 boundaries. WR08 follows automatically.

### WR08 — READY

- Continuing the existing backlog after WR07 READY. Reuse saved cancellation intents, process creation identities, child/transport provenance, resource cleanup proofs, task fencing and linked-generation recovery. Preserve terminal history/evidence; uncertain cleanup must remain observable and scoped until verified, without launching duplicate checks or restarting coding authors.
- Outputs and invariants: [WR08_RECOVERY.md](WR08_RECOVERY.md). Native/review containment and quiescence, complete-history ownership, local controls during outages, scoped UNKNOWN recovery, immutable proof replay and bounded prefix-preserving native retries reuse existing owners. No coding-author restart feature.
- Final evidence: **632 discovered / 503 selected / 129 excluded**; 502 passed, one test synchronization failure, zero errors/skips, 131 unchanged hashes. Independent amendment passed the corrected case **1/1**, verifying that only its test file changed; all production remained identical. Accepted evidence is 502 prior passes plus that focused amendment, **not** a single green 503-case run. Failed/invalid earlier evidence remains retained. Decision: `wr08/acceptance.json`; independent audit: `wr08/feedback-receipt-amendment-audit.{json,log}` under the evidence root.
- Final real SQLite/private OpenCode monitor→continuation→cancel fixture passed with exactly two fake-provider requests, zero replay API calls, retained original/control, empty/inactive session, RELEASED/CANCELLED/CLOSED and Job0. All 13 source hashes matched. Independent review accepted core quiescence and selective cleanup; controlled-listing healthy-scope exclusion and fake-producer/Minecraft limits are explicit.
- Preservation: six configs, 108 installed paths, four WR02-authorized contracts, receipt and publication pointer unchanged; 22 preexisting deletion-policy deviations untouched. Four closed private fixture roots archived/removed. No installation, cutover, mod coding gate, commit or owner acceptance. WR09 follows automatically.

### WR09 — READY

- Starting after WR08 technical READY. Extend `gate_progress.render` / `_PAGE`, not a second dashboard. SQLite owns redesigned lifecycle; legacy observations retain explicit provenance and cannot override authoritative phase. Preserve compact horizontal moving lists and expanded details.
- Architecture and current validation boundary: [WR09_BOARD.md](WR09_BOARD.md).
- Ordered scope: read-only consistent snapshot/projection and existing owner-action bindings; then extend the shared renderer with disconnected saved-snapshot behavior; then focused projection/action checks and isolated browser/visual review. No live board/configuration/publication changes before WR12.
- Delivered read-only authoritative projection, the opt-in shared compact board, revision-hint SSE/reconnect, and existing bound owner actions. Fixed foreign-owner decision admission before service artifacts/replay and transaction persistence; no new command authority or author restart.
- Final evidence: **177 discovered / 169 selected / 8 inherited duplicates excluded; 169 passed, no failures/errors/skips**, 138 unchanged hashes (`wr09-visual-suite.{json,log}`). Real client→ASGI/store ownership checks passed; flat/package legacy assertions retained. Final contained UTF-8 Edge proof passed with readable desktop/narrow/expanded/connected/stale/offline screenshots, counter 1 after browser close, Job0 and four matching final runtime hashes. Parent and independent visual review accepted it. Failed/intermediate evidence remains retained.
- Decision: `wr09/acceptance.json` under the evidence root. Three closed browser roots archived/removed with identity/CRC/member-byte proofs; six configs, 108 installed rows, four contracts, receipt/publication preserved. No live activation, mod gate, commit or owner acceptance. WR10 follows automatically.

### WR10 — READY

- Starting after WR09 READY. Update role/tool instructions and receipt-owned packaging against the implemented coordinator contracts, not the obsolete AI integration lead. Prepare inactive installation/cutover/recovery material only; the old observer stays the sole live owner until WR12.
- Reuse decisions are recorded in `wr10/decisions.json` under the evidence root: extend the existing publisher with stage-only mode; retain canonical `workflow_runtime.py`; use explicit role-bound reviewer server keys where sessions share a worktree; stage a separate coordinator dependency definition without touching the active environment. Documentation and packaging workers have disjoint file ownership.
- Delivered [inactive contracts and package guidance](WR10_PACKAGING.md), thirteen installed document targets from twelve assets, explicit manifest-only runtime layout and receipt-bound selected-role config/profile staging. Canonical runtime is not duplicated; legacy observer is omitted intentionally. Existing publisher defaults remain unchanged; unknown ownership/path conflicts fail closed.
- Final frozen validation: **42 discovered/selected, 42 passed, no failures/errors/skips; 168 unchanged source hashes**. Legacy publisher and copied-source-config private package fixtures both passed; zero target writes. Installed construction/catalog/schema, exact legacy permission ownership, profile byte preservation, backup corruption and root/live-target junction refusal are covered. Independent binding, document and package reviews passed.
- Final artifact independently audited: 67 files, 15 backups, 83 ZIP members; all member/source/receipt hashes and CRC verified. `final-runtime-package.zip` remains **runtime-only-incomplete**: actual bindings belong to WR12's fresh baseline, while complete binding staging is proven in private fixtures. Five dependency pins verified read-only; clean environment bootstrap is documented but unexecuted.
- Decision: `wr10/acceptance.json` under the evidence root; frozen report `wr10-frozen-suite.json`, archive proof `final-stage-proof.json`, cleanup `fixture-cleanup.json`. Six fixture groups and two stage roots archived/removed; six configs, 108 installed rows, four contracts, receipt/pointer and 22 preexisting deviations preserved. No activation, installation, live permissions change, coding gate, commit or owner acceptance. WR11 follows automatically.

### WR11 — READY

- Started after WR10 READY. First map existing WR04–WR10 evidence and reusable fault/latency fixtures against DESIGN acceptance, then close only actual coverage gaps. Run the complete relevant tooling suite and designated isolated real OpenCode tests serially; do not change live sessions/configuration or start a competing scheduler. No WR12 cutover is implied.
- Execution plan and pending requirement matrix: `wr11/{plan,acceptance-matrix}.json` under the evidence root. Existing store crash/outbox and core fail-fast tests are reused, not duplicated. Added one independently reviewed feedback restart/lost-ACK test; its inherited fixture passes are not new distinct WR11 cases. Full-suite isolation review found no required exclusions for live-service/Gradle safety.
- Initial latency fixture measured idle and deliberately blocked dispatcher behavior, **not native contention**; busy-case target missed and correct native-boundary measurements remain pending. Installed import probes passed but did not start OpenCode or execute tools; real-tool proof is still required. These partial artifacts do not establish WR11 readiness.
- Subsequent reviewed metrics: idle and fixture-held-mutex commit→handler p95 17.199/24.992 ms (30 samples each); terminal transition call→author fake POST p95 122.668 ms (30 samples, conservative bound including commit); authoritative preflight 3 samples, p95 241.250 ms. Actual backend spawn/request timing remains a separate prepared fixture; no real-inference timing is claimed.
- First complete tooling run: **964 discovered, 817 selected, 147 excluded; 781 passed, 33 failures, 3 errors, 0 skipped; 199 unchanged source hashes**. Failure evidence is retained in `wr11-full-suite-first.{json,log}`. All-import test isolation and real Windows cleanup failures require resolution; isolated affected selectors passing does not resolve the complete-run failures.
- Actual installed-adapter status tool executed and its result was consumed in private OpenCode; saved-run ACK proof is prepared, not yet validated. Refreshed existing real cancellation/recovery fixtures both passed with current-source bindings (6/13 paths respectively), queued deletion, local stream cessation, retry replay without added calls, healthy cheap native counter 1 and Job0. Remote provider computation cessation is not claimed.
- Found and fixed a native-capacity status gap: scoped boards could show capacity free when another worktree held it, and claimed mutex waiters vanished from queue accounting. Existing read-only snapshot/projection owners now expose only global occupancy counts and worktree-local capacity-pending counts; 27 focused tests passed, independent review pending. No scheduler/schema/phase mutation is introduced. WR11 remains IN_PROGRESS; WR12 has not started.
- Corrected capacity predicate and visible wait reason passed **37 focused tests and independent review**. Real saved ERROR feedback/ACK also passed independent review: CHECK counter 1, exact seven-field installed-SDK acknowledgement, ACKNOWLEDGED/WAIT_FOR_RECOVERY, immutable ERROR and verified native/OpenCode Job0. Reviewer claim/result proof and actual backend latency remain in progress.
- Legacy all-import failures were traced to fixture bare/package module identity, not established runtime defects. Eight fixture files now scope aliases and cached feedback mocks correctly; all 108 modules imported, **154 selected cases passed**, source review PASS. That retained focused log contains the final green run only. Original complete-run failures remain unchanged; final complete validation is still required.
- Two original real-process cleanup cases passed a diagnostic retry (three taskkill calls returned 0), but this does not retroactively solve the full-suite failures or prove cleanup of an earlier invalid diagnostic attempt. The invalid attempt lost its raw probe log and retains an explicitly unverified cleanup boundary; diagnostic output naming now prevents overwrite. Protected live state still passes interim preservation. See [WR11 validation index](WR11_VALIDATION.md) and machine evidence for limitations.
- Actual backend metrics subsequently passed review: 30 idle coordinator samples p95 **92.2714 ms**, 30 occupied direct-handler samples p95 **38.7876 ms**, post-preflight store-submit entry→real Popen entry; all 90 jobs passed counter 1 with cleanup. Native live-board revisions are now fixed using transactional hints in the existing outbox; **65 focused passes and independent review PASS**, including actual active-worker HELD/UNKNOWN/RELEASED revisions. Cross-process hint reconciliation remains ~1 second; this later lifecycle-only change does not invalidate the measured backend-request path. Final full-suite and remaining real reviewer/browser/current-package checks are still pending.
- Current milestone: the corrected final complete suite passed **822/822 selected (969 discovered, 147 duplicates excluded), no failures/errors/skips, 200 unchanged source hashes**. Staged-only native CHECK, actual reviewer claim/result/PASS/author ACK, updated browser/visual review and occupied-coordinator timings passed independent reviews. Successful disposable fixtures were archived/audited/removed; historical failed/unknown evidence remains retained. Coded-FAIL feedback runtime, final preservation and whole-criterion independent acceptance remain pending; WR12 is still inactive.
- Final decision: `wr11/acceptance.json` is **READY**. Actual coded CHECK FAIL→normal FAIL/FIX_FINDINGS→author SDK ACK passed independent review, with no review admitted. Whole-criterion review accepted all requirements subject only to its successful fixture's archival/cleanup; that exact root is now independently archived, verified and removed. Final preservation passed 11/11 anchors, 108/108 rows, unchanged pointer and 22 existing deviations. Historical lost diagnostic cleanup and failed isolated-review reservation remain explicitly unverified/retained, not retroactively repaired. Technical readiness is not owner acceptance or activation.

### WR12 — IN_PROGRESS

- Starting automatically after WR11 technical READY. First obtain a fresh read-only live baseline and identify exact intake/drain, scheduler, publication-receipt and role-binding owners. No historical snapshot authorizes overwrite; no activation or scheduler stop/start has occurred.
- Preserve all live/uncommitted work and evidence. Stage-only packaging is not an apply/cutover owner. Any necessary scoped cutover implementation must reuse existing admission, process identity/cleanup, publisher and coordinator owners; never launch two schedulers, invent live bindings, or replay historical validation.
- Offline receipt-owned publication, intake, scheduler exclusion, supervisor identity and API-activity units have focused validation and independent reviews. A 65/65 combined checkpoint predates subsequent follow-up source changes; overlapping unit counts are not summed. Current evidence and remaining assembly boundaries are indexed in `build/agent-workflow/workflow-redesign-plan/wr12/plan.json`.
- The user authorized four concise reviewer chats; all remain MAIN/idle with original agents/models. Actual implementation-profile output/source/sibling permission boundaries passed independent audit of retained runtime receipts. The raw harness remains BLOCKED by a path-key postassertion bug; a separate accepted capability audit preserves that erratum without replay. Final live tool binding remains incomplete. No live intake hold, scheduler retirement, installation/configuration change or cutover occurred.
- WR12 follow-up: the owner explicitly selected **coding-author scope registration** after discovering the startup-only per-feature context limitation. Add validated immutable scope/evidence registration and exact submit lookup while retaining server identity, publication provenance and shared check/review floors. No routine owner approval or integration AI; this is required before normal-flow cutover, not a retrospective claim about WR03 fixtures.
- The owner also explicitly chose **automatic coding-chat access**, retaining existing per-call worktree/primary-author checks instead of a fixed chat roster. The reviewed13-file candidate is now integrated into MAIN for validation only, with exact baseline backups and31 focused tests passing. No coding chats were created or resumed. The historical assembled checkpoint remains failed:945/947. Dynamic SDK flow reached both chats' submit/replay and cross-chat denial but errored on an incorrect expected-code assertion; corrected assertions await final assembled validation. The legacy report failure did not reproduce in2 diagnostic tests; its original cause remains unknown. No installation or live cutover authorization.
- Latest assembled checkpoint passed **968/968**, independently audited:123 modules,7 guards,228 stable sources and8109 children reaped (`wr12/assembled-968-evidence-audit.json`). Includes reviewed production removal/deadline/reappearance correction and scoped MCP status; earlier958 remains historical. Private project A/B compiled/catalog refresh and one scoped DELETE/two absent reads/catalog-process exit passed independent audit. Composed static assets, child gate, parent grant and strict plan contracts passed focused tests/review; source-only runtime driver is in preparation. No composed transition runtime, real binding staging, fresh protected-state preservation, live activation or owner acceptance is claimed.

The following deferral entries record prerequisite snapshots when the later requests arrived; the ordered table above is the current execution authority.

### WR07 — historical request deferral (now READY above)

- Predecessor check: WR06 is QUEUED, not READY; WR03 is the earliest unfinished unit. WR07 cannot start under the required sequence. WR02's storage-level delivery tests do not establish WR07 coordinator/transport/owner-decision acceptance.
- No WR07 implementation or validation was started. Retain this existing queued request; do not create duplicate tasks or skip WR03–WR06. Begin WR07 only after WR06 is READY.

### WR08 — historical request deferral (now READY above)

- Predecessor check: WR07 is QUEUED, not READY; WR03 remains the earliest unfinished unit. WR08 cannot start under the required sequence. WR02 storage tests do not establish actual process/session cessation, outage recovery or safe runtime restart.
- No WR08 implementation or validation was started. Retain the existing queued request without duplicate tasks or owner handoffs; begin only after WR07 is READY. Live jobs, services, configuration and evidence remain untouched.

### WR09 — historical request deferral (now READY above)

- Predecessor check: WR08 is QUEUED, not READY; WR03 remains the earliest unfinished unit. WR09 cannot start under the required sequence.
- No webpage/projection changes, browser review or WR09 validation were performed. Retain the existing queued request without duplicate tasks or owner handoffs; begin only after WR08 is READY. The existing board and live state remain untouched.

### WR10 — historical request deferral (now READY above)

- Predecessor check: WR09 is QUEUED, not READY; WR03 remains the earliest unfinished unit. WR10 cannot start under the required sequence.
- No role-contract, packaging, installation or publication changes or WR10 validation were performed. Retain the existing queued request without duplicate tasks or owner handoffs; begin only after WR09 is READY. Existing configuration, installed services, live jobs and evidence remain untouched.

### WR11 — historical request deferral (now IN_PROGRESS above)

- Predecessor check: WR10 is QUEUED, not READY; WR03 remains the earliest unfinished unit. WR11 cannot start under the required sequence.
- No WR11 suites, OpenCode adapter/process tests, latency measurements or final technical review were run. WR02's storage acceptance is not end-to-end acceptance. Retain the existing queued request without duplicate tasks or owner handoffs; begin only after WR10 is READY. Live jobs, configuration and evidence remain untouched.

### WR12 — historical request deferral (now IN_PROGRESS above)

- Historical predecessor check: WR11 was not READY at request receipt. The ordered table and current WR12 section above now control; receiving the cutover request did not waive predecessor acceptance.
- No live baseline/API probing, intake hold, draining, scheduler replacement, publication or proof jobs were performed. Retain the existing queued request without duplicate tasks or owner handoffs; begin only after WR11 is READY, with a fresh live baseline then. Existing live state and evidence remain untouched; no cutover or owner acceptance is claimed.
