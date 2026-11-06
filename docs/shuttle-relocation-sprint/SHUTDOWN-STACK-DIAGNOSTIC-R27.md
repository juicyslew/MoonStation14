# R27: bounded test-only shutdown stacks

R26 completed both gameplay cases but hung after `Saving worlds`; see
`R26-SHUTDOWN-DIAGNOSTIC.md`. No captured stack identifies its cause. R25 and
R26 fixtures, plans, logs and receipts remain preserved.

## Change

Extend the existing GameTest-only `GameTestProgressObserver`; do not add another
watchdog/service or change production behavior. Only
`LifecycleOfflineShuttlePersistenceGameTests` opts in. The observer accepts only
its exact observed GameTest server and starts one daemon on its matching
`ServerStoppingEvent`. A matching `ServerStoppedEvent` releases the latch.

If shutdown has not completed, the daemon collects at most two read-only
`ThreadMXBean` snapshots after two fixed15-second waits. It logs the stopping
thread first, up to128 threads and64 frames each, lock/owner details and explicit
thread truncation. It then exits. It reads no world/chunk state, blocks no server
thread, changes no worker deadline or termination policy, and cannot keep the
JVM alive. Normal shutdown cancels capture; cancellation alone is not save proof.

Focused `compileGameTestJava` and independent source review passed. Native
capture and normal save/stop are not yet established. No gameplay fix is claimed.

## Fresh matched check

Plan `GATE-PLAN-08-SHUTDOWN-STACK-R27.json`: same two tags/cases as R26, expected
filtered2/893, default native substeps2. Maintain the matched two-case context
rather than changing isolation while investigating the shutdown failure.
Fresh fixture `build/gametest-shuttle-feedback-08-shutdown-stack-r27`; fresh log
`build/agent-workflow/shuttle-relocation-sprint/reports/feedback08-shutdown-stack-r27-native.log`.

Inspect `[GameTest shutdown stack]` ARM/STOPPING/SAMPLE_BEGIN/THREAD/FRAME/
SAMPLE_END or normal-stop cancellation alongside actual gameplay counts and
save/exit markers. A timeout with both gameplay passes remains a failed run.
A clean run without a hang is non-reproduction, not proof that a cause was fixed.
Missing stack evidence is unknown, never a license to infer a deadlock or Sable
defect. Normal saves/stop and process exit must be established separately.

No Sable internals, new mixins/private access, physics/gravity/rotation/cap/
recovery-teleport, runtime config or owner-process/world changes. No flight,
process restart, client/Creative coverage, owner-loss fix, readiness or M1
acceptance. Author compile evidence:
`build/shuttle-feedback-08-author-shutdown-stacks-r27.log`.
