# WR06: code-owned review protocol

WR06 technically READY after independent review: 237 distinct current tests passed (236 frozen-production full-suite checks plus one independently verified deadline case). Inactive development source; no reviewer session or live prompt was created for this work, and owner acceptance is not implied. Owned local SDK/HTTP fixtures use fake OpenCode transport.

## Owners and flow

- `workflow_review.ReviewCoordinator` reads the admitted, frozen review policy and current accepted CHECK receipts. It creates deterministic `review:<role>` slots only after every coded obligation passes. Each new task and its ready wake commit together. Session bindings must be distinct from the author and each other.
- `workflow_review_transport.ReviewDispatchBackend` uses the shared WR03 renderer, verifies current receipt bytes and remeasures the **whole** objective/instruction/criteria/envelope body. Assignment is persisted before the stable prompt POST. A matching API acknowledgement records immutable task-produced transport provenance; it does not claim or complete a review. Unknown post state is not permission to repost.
- `workflow_review_results.ReviewResults` validates typed claims, PASS/FAIL review reports and precise task errors. Invalid categories/fields return `CORRECT_RESULT` for the same task without creating another task or changing its verdict. ERROR commits the fenced task result, incident and page intent atomically through `WorkflowStore.report_task_error`.
- `workflow_review_pipeline.ReviewPipeline` routes CHECK and REVIEW wakes, consumes exact saved completion events, admits post-check work and aggregates accepted results. Existing store transitions own STOPPING/DECIDED and derive immutable results and delivery intents. No lead model dispatches work or infers lifecycle state from prose.

Native dispatch now enters CHECKING before execution. Required roles are the frozen `implementation`, `parity`, `coverage` and/or `fidelity` selection; configuration cannot replace agreed coverage. A coded FAIL creates no reviewer assignments. Pending evidence is not an admissible review-failure category. PASS-review rationale is excluded from FAIL findings.

Completion reconciliation is code-only and idempotent. An event's existence is **not** proof that its effect was applied; reclaimed completion wakes are evaluated again from saved tasks/results. Review prompt effects use their own acknowledgement/protocol evidence instead.

## Configuration and tools

The explicit coordinator configuration may add `reviewers`, mapping role to `session_id`, `worktree`, `agent`, `token_file` and stable `caller`. Author/reviewer tokens, callers and session identities are disjoint. Both production loading and fixture configuration use `configure_review_pipeline`; no second assembly path owns reviews. Missing required role capability is visible as a pipeline fault, never owner-ready success.

This unit uses **preconfigured reviewer sessions**, not an assumed session-creation API. Dispatch verifies the configured session/agent/worktree through the transport adapter. Actual OpenCode provisioning, role/MCP permissions, prompt/inbox semantics and physical interruption remain explicitly unverified until isolated real-adapter checks in WR10–WR12. A configured session is not a claim that an AI worker has consumed its prompt.

The official SDK adapter has separate author/reviewer profiles. Reviewer tools are `claim_task`, `complete_review`, `report_error` and `status`. Prepared result/error files use the same bounded local-file transport pattern as submission. Server-side bearer binding supplies session identity and fences role/worktree/assignment; a request cannot appoint another session. Reviewer status exposes saved task and bounded receipt metadata, not source freshness, live health or file contents.

HTTP worker routes are `/v1/review/claim`, `/v1/review/complete`, `/v1/review/error` and `/v1/review/status?task_id=...`. Author submit/status behavior remains separate. Successful callbacks signal the coordinator after commit. Initial feedback does not depend on restarting stopped coding authors; no such supervision was added.

## Evidence and remaining boundaries

Focused fixtures include real native producer PASS receipts, strict fake OpenCode acknowledgements, actual official-SDK stdio reviewer claim/result calls, invalid-result correction, replay/session fences, no-result aggregation and service-level coded PASS → two reviews → DECIDED/PASS. Verified coded FAIL reaches DECIDED/FAIL without prompt dispatch. Factory tests cover the actual configured assembly path, not only injected helpers.

Startup failure and fast-exit child-registration regressions exposed distinct boundaries. An exited pre-claim launcher must not be killed by stale PID; no native command could have started before the SQL handshake. An already-exited exact `Popen` handle can prove that child's cessation; unknown live children remain held. Earlier failed checks are retained rather than counted as passing evidence.

Timeout/error decisions do not establish physical reviewer cessation. UNKNOWN ownership, cancellation refusal, provider interruption/recovery and completing cleanup remain WR08/WR11 work. Delivery intents are durable here; author acknowledgement, owner decisions and post-acceptance regression remain WR07. No real Gradle/Minecraft suite, live API mutation, installation, publication, owner acceptance or commit is implied.
