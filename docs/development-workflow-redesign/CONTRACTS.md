# Workflow protocol v1 — WR01 contracts

Specification/static fixtures only. No live API, SQLite store or pipeline is implemented here. Read [DESIGN.md](DESIGN.md) for owner policy and [WR01_BASELINE.md](WR01_BASELINE.md) for exact reuse/baseline evidence. JSON shapes are in `contracts/protocol.schema.json`; relational/transactional guards below are equally mandatory and cannot be established by JSON Schema alone.

## 1. Identity, encoding and authority

- `contract_version` is integer 1. Reject unknown fields/versions; return field-specific protocol diagnostics without mutation. Do not silently infer renamed/missing fields.
- IDs are opaque, stable and prefix-qualified (`submit_`, `run_`, `task_`, `artifact_`, `event_`, `delivery_`, `incident_`, `accept_`, and schema-defined control/result IDs). Session IDs are actual OpenCode session IDs; verify worktree/role against the existing service, not the model's prose.
- SHA-256 values are lowercase 64-character hexadecimal. Timestamps are nonnegative UTC epoch milliseconds. Generation/assignment numbers start at 1; committed sequence numbers are monotonic nonnegative integers. Use monotonic process clocks for execution budgets, never wall-clock differences as stop authority.
- Canonical JSON for a digest is exactly the current Python `snapshot_key` encoding: `json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True, allow_nan=False).encode('utf-8')`, then SHA-256. Preserve array order and Unicode code points (no normalization); reject duplicate input keys and nonfinite numbers before hashing; do not nondeterministically rewrite path case. Protocol numeric fields are integers. Reuse `snapshot_key` only after WR02 adds/verifies strict input rejection and these encoding tests; do not replace existing file/producer byte hashes with a subtly different algorithm.
- Admission re-computes submitted source/plan/policy digests and freezes a manifest of relevant resolved paths/bytes. Store transport IDs separately from immutable semantic request payloads. No model-declared digest establishes source truth by itself.
- Idempotency scope is operation + caller binding + key. Save request digest and exact response in the same transaction as the effect/outbox event. Exact retry returns the saved receipt; different content under the same key is rejected. Also enforce one active feature evaluation per sprint/source/plan/policy and one regression per owner acceptance, regardless of caller retry keys.
- Each worktree/sprint has one current feature head. Replacing it atomically records supersession and closes old child admission; any needed cleanup stays tracked. A new queued evaluation cannot execute against an affected resource until that cleanup settles. Different input digests are not permission for competing current heads or two writers to the same worktree.
- Every mutating command is authorized by role and bound target; a review worker cannot submit executor results, accept the feature, start checks, modify its plan or create reviewers. Producer callbacks use the existing program-owned claim identity, not an AI assertion.

## 2. Records and minimum semantic guards

Schema definitions cover submission/response, run, attempt, task, artifact, review result, claim/receipt, task ERROR, result event/feedback ACK, cancellation/receipt, incident, owner decision, regression job and delivery. Exact properties/types are machine-readable; required cross-record rules are:

| Record | Meaning / guards |
|---|---|
| Submission | Purpose OWNER_READY, implementation complete, short label, agreed criterion completion/evidence, runnable manual steps, typed check packs and exact author/source identities. Criterion IDs must cover the agreed scope; owner-approved deferral changes that scope, not a hidden partial completion. |
| Submission response | ACCEPTED only after run/initial task/dispatch intent commit; otherwise rejection FAIL/ERROR with concrete diagnostics and no admitted run. Same response recoverable after a lost reply. |
| Run | Immutable input, plan and policy; single current evaluation generation. FEATURE_GATE versus POST_ACCEPTANCE_REGRESSION are separate kinds. A regression must reference a valid matching acceptance. |
| Attempt | Current phase, null or immutable terminal result, optional control closure, cleanup state and recovery authorization. ERROR recovery creates a higher generation linked to the old ERROR, not a mutation of it. |
| Task | Run/generation, exact dependencies, kind, assignment generation, deadline and assigned process/session. A lease/claim grants completion authority only for that assignment. A check task and a review task have different authorized producers. |
| Artifact | Immutable path/hash/size/type and producer association. Source/provenance can be produced by a submission before a run/task exists; do not manufacture a worker task to hash inputs. Task-produced artifacts require their actual task/run binding, and owner evidence uses owner provenance. Verify files/receipt internals and source/plan/task binding before use; schema-valid hashes alone prove nothing. |
| Review result | PASS/FAIL only with criterion/category/location/evidence and rationale; FAIL requires a concrete correction. Tests pending is not an admissible category. Missing verified input is task ERROR, not a gameplay FAIL. All required coded checks must already have passed. |
| Task ERROR | Precise code/message and evidence, bound to the same assignment. Retain original defect/check findings if cleanup or protocol faults later determine overall ERROR. |
| Result event / ACK | Stable result ID/digest and exact run/generation; action follows PASS→WAIT_FOR_OWNER_TEST, FAIL→FIX_FINDINGS, ERROR→WAIT_FOR_RECOVERY. ACK proves receipt of that result only, not implementation progress or owner approval. |
| Cancellation | Generation/expected-sequence-bound control request. Retain reason and affected children. Request ACK, API interruption ACK and confirmed cessation are separate facts. |
| Incident | Durable fault/recovery record, SYSTEM or OWNER responsibility, explicit action requirement. Page acknowledgement means seen, not resolved. Routine recovery does not wait for owner acknowledgement. |
| Owner decision | Exact displayed feature snapshot/result and expected sequence, authenticated local owner action. ACCEPT needs a new acceptance ID; REQUEST_CHANGES needs concrete feedback. No model can manufacture owner acceptance. |
| Regression | Unique acceptance/snapshot-bound code-only job. Its result never overwrites feature-gate result or historical acceptance. Cannot silently use modified worktree contents. |
| Delivery | Exact event/payload digest and recipient binding, transport acceptance and protocol ACK separately. Changed bytes require a new event, never reuse of an existing ID with a modified body. |

Do not generate criterion, evidence, role or acceptance validity from filenames, model activity or state-cache age. Recheck source applicability before publishing PASS, accepting an owner decision, or starting post-acceptance work. A historical PASS remains immutable if source changes; its applicability to current work is a separate fact.

## 3. Submission transitions

| From | Event / guard | Effect |
|---|---|---|
| No admitted run | Valid preflight; keys/digests/criteria/config/rendered envelopes match | Atomically save ACCEPTED response, frozen run generation 1, initial task and outbox dispatch. |
| No admitted run | Invalid/incomplete/checkpoint/oversized request | Save FAIL response with specific correction; no reviewer/native job/run created. |
| No admitted run | System cannot safely verify/persist request | ERROR and local incident; no false ACCEPTED. Local client/health path handles coordinator unavailability. |
| Any | Exact idempotent retry | Return original response; no second job. |
| Any | Same key, changed payload/binding | Reject conflict; original request/response unchanged. |

Temporary AI-provider failure does not reject admission or local coded checks. Record an AI dependency pause when needed. Ordinary FEATURE_GATE plans cannot contain full GameTests without a matching explicit owner exception; POST_ACCEPTANCE_REGRESSION requires matching owner acceptance.

## 4. Evaluation transitions and arbitration

`result` is null until DECIDED, then one immutable PASS/FAIL/ERROR. Withdrawal/supersession is a **control closure**, not a fourth verdict. CLOSED has null result plus CANCELLED/SUPERSEDED closure; DECIDED has a non-null result and no closure. Add CLOSED to the conceptual design's phase list for these previously described control operations.

| From | Event / guard | To / transaction |
|---|---|---|
| QUEUED | Native slot claim and current input verified | CHECKING; reserve one executable task. |
| CHECKING | Required code tasks all PASS, receipts verified, packaging available | REVIEWING; create only required review tasks and their dispatch outbox entries. |
| CHECKING / REVIEWING | First validated FAIL or terminal task ERROR | STOPPING; save proposed outcome/findings, invalidate unfinished child admission, queue cancellation. |
| REVIEWING | Every required review PASS; no missing tasks | STOPPING with proposed PASS; verify source and settle cleanup. |
| Any active phase | Owner withdrawal / newer approved source supersedes | STOPPING with proposed control closure; no new child admission. |
| STOPPING | All required children stopped/completed, evidence/current-source guards pass | DECIDED with proposed result, or CLOSED with requested control closure; commit result/control event and delivery intent atomically. |
| STOPPING | Cleanup cannot be confirmed / contradictory evidence | DECIDED ERROR plus supervised scoped resource hold and visible incident. Do not claim clean cancellation or abandoned cleanup. |
| DECIDED ERROR | Fresh same input, resolved boundary and recorded recovery policy/owner authorization | Create generation N+1 on same run, link prior ERROR and reusable verified receipts; atomically advance current pointer and emit RECOVERY_STARTED. Old result stays unchanged. |
| DECIDED PASS/FAIL or CLOSED | Late task callback, duplicate event or request to reopen | Duplicate same content returns saved receipt where applicable; otherwise reject as stale/closed. No new work. |

Pause is orthogonal: retain actual phase/task plus a reason, dependency and retry policy. Provider outage never becomes PASS/FAIL, and no native mutex is held while waiting for AI. Exhausted permitted retries produce an ERROR attempt or retained provider pause according to the recorded fault policy, not an invisible unbounded retry loop.

Transactions serialize races. The first accepted decision trigger closes child admission; later observations are retained as evidence, not competing finalizations. A cleanup/safety fault may promote a **proposed** PASS/FAIL/control closure to ERROR before DECIDED; it never rewrites an already committed verdict. Repeated cancellation shares its receipt. Result accepted immediately before cancellation remains historical evidence; an outstanding task result after assignment revocation cannot satisfy the run.

If source changes during evaluation, stop and supersede its applicability; do not publish PASS for changed input. A new input requires a new submission. Do not automatically create that submission or infer owner permission to narrow scope.

## 5. Task transitions

| From | Event / guard | To |
|---|---|---|
| QUEUED | Dependencies met, correct attempt current, no stop/pause preventing dispatch | DISPATCHING; reserve assignment and stable outbound identity. |
| DISPATCHING | OpenCode/worker transport admission | Still DISPATCHING until actual protocol claim; save transport receipt. |
| DISPATCHING | Exact assigned worker claims generation/input | CLAIMED; save idempotent claim receipt. |
| CLAIMED | Program execution starts / review proceeds | RUNNING; no model heartbeat prose required. |
| CLAIMED / RUNNING | Valid producer result with current assignment and evidence | COMPLETED with immutable PASS/FAIL/ERROR result; emit dependency/aggregate event. |
| Any unfinished state | Run stop or task cancellation | CANCEL_REQUESTED; revoke completion authority, cancel queued work/interrupt active work. |
| CANCEL_REQUESTED | Actual identity-bound cessation or proven never-started | CANCELLED with no fabricated result; save proof. |
| Any | Duplicate accepted claim/result with same identity/content | Return saved receipt; do not repeat effects. |
| Any | Mismatched source/session/generation or changed result | Reject without mutation and record appropriate protocol diagnostic/incident. |

Schema validation is only structural; a generation value of 2 is not inherently invalid. Whether it is stale is a transactional test against the saved assignment. Deadlines require known execution/transport state; an HTTP timeout is not proof that dispatch failed. Reconcile saved ID and session/inbox facts before any repeated dispatch. Creating an OpenCode session and submitting a prompt are separate external effects: persist/reconcile each identity before continuing, never blindly create another reviewer after an uncertain response.

The model-facing `task_claim` binds an assigned OpenCode review session. Native/code workers instead use the existing program-owned worker claim and process identity, recorded through the same task transition owner; do not invent an AI session or expose native-result authority through reviewer MCP tools.

## 6. Delivery, feedback and recovery

| State / event | Rule |
|---|---|
| PENDING | Exact event/body and target are committed before transport. |
| TRANSPORT_ACCEPTED | OpenCode response matches message/session/delivery/body metadata. This is not consumption. |
| ACKNOWLEDGED | `ack_result` names exact event, run/generation, result ID/digest and bound author. Old ACKs may close only old deliveries. |
| Retry / disconnect | Reuse same event/message ID and body; reconcile before repost. Code-only deadlines/reconnect checks, no model-poll loop. |
| Result delivery timeout | Durable delivery incident and readable page status; do not change PASS/FAIL/ERROR or rerun validation. |
| Owner page closed / provider unavailable | Local records remain authoritative; page can reconnect and pending delivery can resume. No requirement for owner presence. |
| Author idle after acknowledged feedback | Retain findings/next action and truthful secondary activity. Do not restart, nudge, reconstruct or replace the author. |

Preflight FAIL returns directly to the submitting caller; admitted evaluation results use durable author delivery. Owner-requested changes are durable control feedback with their own event/binding, not a fabricated replacement test FAIL. PASS→manual test and manual feedback→author are explicit actions; no natural-language parsing of arbitrary chat establishes acceptance.

Acknowledgement responses must not accidentally instruct the author to stop corrective work. The command receipt's `next_action` is selected by the accepted operation and result: review claim→PERFORM_REVIEW; completed review/task error→STOP for that review worker; author result ACK→the result's FIX_FINDINGS/WAIT_FOR_OWNER_TEST/WAIT_FOR_RECOVERY action; owner ACCEPT→VIEW_REGRESSION; owner REQUEST_CHANGES→WAIT_FOR_AUTHOR. These are operation-bound guards, not model-selected advice. A schema enum alone cannot prove the receipt matches the operation that produced it.

## 7. Owner acceptance and post-acceptance regression

ACCEPT is permitted only for the exact current applicable FEATURE_GATE PASS and displayed snapshot. In one transaction save the owner decision/acceptance and exactly one regression reservation/dispatch intent for that acceptance. Duplicate ACCEPT returns the existing acceptance/job. Conflicting or stale decisions reject without editing historical acceptance.

The code-only regression uses accepted immutable inputs and existing serialized execution/evidence owners. If an executable accepted snapshot cannot be retained, keep its worktree reserved until that job settles; do not run against changed input. Its PASS/FAIL/ERROR is independent of feature-gate PASS and owner acceptance. A concrete regression FAIL is delivered to the author; no automatic author re-triggering after receipt is added.

## 8. Cancellation and incident closure

Cancellation receipt REQUESTED/STOPPING is not CONFIRMED. CONFIRMED requires proofs for every owned child and resource. UNRESOLVED retains the exact identities, supervisory action and scoped hold; it does not require blocking unrelated coding/JVM tasks. Resolve holds automatically only after verified cleanup, and never on a stale marker or PID absence alone. Reuse existing creation-time identities and cleanup helpers.

Incident OPEN→RECOVERING→RESOLVED tracks automatic policy/owner action. Owner 'seen' is independent. Faults in the coordinator itself must be visible through local health/saved snapshots even when it cannot write an incident. ERROR recovery does not require routine owner approval; a genuine unsafe ambiguity does. Full provider failure opens a circuit after bounded transport attempts; non-generating health probes only if actually supported, otherwise wait for a real availability event or explicit retry. Retain task state, do not send inference probes indefinitely or switch models silently.

## 9. Static versus runtime acceptance

The fixture index separates structural positive/negative schema cases from semantic scenarios requiring a transactional store or actual adapter. WR01 can establish valid JSON/schema shapes, expected structural rejection and coherent transition specifications. It cannot establish durable commits, API cessation, provider recovery, actual author acknowledgement or latency. WR02 onward must test those invariants; WR11 requires real isolated API/process proof, not static fixtures relabeled as integration evidence.
