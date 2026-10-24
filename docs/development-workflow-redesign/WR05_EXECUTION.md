# WR05: finite execution and SQL-bound evidence

WR05 technically READY after independent review: 207/207 selected tests, zero failures/errors/skips. Inactive development sources only; nothing has been installed or run against live jobs, and owner acceptance is not implied. Validation uses cheap owned Python/batch producers, not Minecraft or real Gradle.

## Shared execution boundary

`workflow_runtime.run_finite_checks` is the shared finite loop used by both the legacy candidate adapter and `workflow_native_worker`. It reuses existing typed evidence producers, process launch/deadline/stop helpers and launch-smoke monitoring. Candidate verification/progress/blockers/receipts remain in the legacy wrapper. The SQL worker never fabricates a legacy candidate or uses job JSON as authority.

Callbacks verify current inputs, register owned children and externalize completed evidence before the next check. Callback failure stops a live owned child; uncertain cleanup is not reported as success. On Windows, the fixed `./gradlew` executable is adapted to the repository's existing `gradlew.bat`; planned argv remains unchanged and `executed_argv` records the real launcher.

## Native ownership

`NativeExecutionBackend` handles CHECK wakes without running long checks on the coordinator thread. It starts the fixed worker CLI, obtains a bounded token-bound startup identity acknowledgement, then persists dispatch to SQLite before the worker can claim or start checks. This handshake is necessary because Windows venv launchers and their interpreter have different PIDs. The acknowledgement file is transport evidence only, never task/result/recovery authority.

The saved native claim fences worker PID/creation identity, token, task, assignment, evaluation generation and input. A duplicate wake observes saved state: completed/live work is APPLIED; missing/unknown/dead execution without a result is UNKNOWN, not permission to rerun. Owned completion waiters notify the coordinator after worker exit; durable outbox reconciliation remains the fallback. The starter now wires this backend explicitly; imports/config loading still do not start it.

Workers use the existing OS gate mutex plus SQL `native_validation` resource ownership. Child identities are captured through `gate_execution` and recorded as task-produced provenance. Registered cleanup proofs release the exact claim only after observed child cessation; uncertainty retains UNKNOWN ownership. Coordinator shutdown closes owned backends before releasing its ownership guard.

## Inputs, evidence and outcomes

`load_native_inputs` reads the accepted submission and registered frozen bundle/manifest through repository APIs, verifies actual artifact bytes and semantic source/plan/policy digests, and checks live source applicability. Before every check, current source, pinned receipt and exact worktree configuration bytes must still match. Receipt selection uses the exact target **and** `opencode.jsonc`, not an arbitrary file owned by the same target.

The existing `validation_evidence` producer remains the only parser for fresh JUnit/readiness/GameTest/build output and selected/total counts. The completed-check callback copies logs and producer artifacts to immutable external paths, registers task provenance and replaces receipt paths with those copies before a later `clean build` can delete originals. Typed counts are retained, not reinterpreted by AI.

- PASS requires every planned typed check and program provenance check. A task-produced CHECK_RECEIPT binds input, plan and policy and references immutable evidence.
- A verified nonzero compiler/test exit or assertion failure is actionable FAIL and stops later packs. It cannot start review.
- Missing/unverified producer output, source/config drift, spawn/timeout and uncertain cleanup are ERROR with a bound task error and durable incident. They do not ask the author to invent another revision for infrastructure repair.

Exact completed-task replay returns saved evidence without rerunning commands. Only public task/native-claim/accepted-submission reads were added to the store; transactional result/resource authority is unchanged. Review creation and aggregation remain WR06.

## Validation and remaining boundaries

Tests exercise real disposable Git/SQLite admission, actual bounded worker subprocesses, typed PASS/FAIL/ERROR, early stop, live-input drift, callback cleanup, package-clean evidence retention, duplicate observation and two-worker non-overlapping serialization with released resource proofs. Fake registry plans are injected only in tests; production has no arbitrary command override. Legacy receipt regressions use fail-closed mocked OpenCode transport.

An earlier legacy regression accidentally reached a real **GET** for a fixture session and failed HTTP 400. This was not counted as a passing check; the module's flat/package import alias and transport isolation were fixed before its eight tests passed. Failure evidence is retained. No live POST, gameplay validation or publication was authorized.

Worker death in the child-launch/registration gap, cancellation/outage recovery and resolving UNKNOWN resources remain WR08/WR11 work. These tests do not establish crash-safe OS containment or real installed OpenCode semantics. In particular, a safe hold is not a claim that ordinary runtime recovery is already complete. Linux creation-identity representation and real Gradle/Minecraft execution are not established by Windows fixture tests. Broader live verification remains WR12.
