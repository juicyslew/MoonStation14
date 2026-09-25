# 2026-09-24 connected collision regression audit

**Status: cause investigated and bounded-resolution handling fixed; authenticated connected retest is still required.** On 2026-09-24 at 15:57:56, an authenticated client connected to a survival world and began a movement epoch with Begin/Commit. Movement then triggered `IllegalArgumentException: collision resolution must be finite and bounded by requested displacement` from `CharacterMovementMotor.ensureBoundedResolution:94`, through `MovementServerController.onAfterVanillaRestore:139`, and the client disconnected with “Internal server error.” This was a real connected-session failure; the later checks below do not erase it or prove the corrected path works for a connected player.

## Cause and bounds of the finding

The verified cause was the strict post-`Entity.move` collision-result check: it treated a resolved displacement outside the requested displacement envelope as invalid and threw. The observed stack trace establishes where that exception occurred, but **does not identify which axis actually exceeded its requested bound**.

The pinned Minecraft 1.21.1 `Entity.collide` grounded step path can return upward displacement greater than a positive requested Y displacement, up to `maxUpStep`. At large coordinates, subtracting positions to recover displacement can additionally produce an ULP-scale rounding overshoot. Those are verified mechanisms that explain why the strict check can reject a legitimate collision result; the trace does not prove which mechanism, or which axis, caused this particular observed exception.

## Bounded fix and fail-closed behavior

The new bounded-resolution handling:

- permits a grounded step's upward result up to `max(maxUpStep, positive requested Y)`;
- retains finite-value checks and uses a small, coordinate-derived rounding allowance with a cap, rather than allowing an unbounded displacement;
- tolerates small blocked-axis velocity rounding; and
- continues to reject gross out-of-envelope results, including fake teleports.

Server and client callers now catch an out-of-envelope `IllegalArgumentException` and fail closed instead of letting it escape as an uncaught tick error. The server uses a controlled disconnect. This is not a second movement attempt: the path does not move the entity twice, and it does not grant the client position authority.

## Regression fixture and validation

A new real-Villager slab GameTest exercises the relevant grounded-step case. It requests approximately `(0.175, 0.34, 0)` and observes approximately `(0.175, 0.5, 0)`, with the resulting Y at the slab top. This is dedicated-server GameTest evidence for the collision case, **not** a retest of the authenticated connected session.

Latest coordinator-reported checks after the fix:

- `& ".\gradlew.bat" test --rerun-tasks --no-daemon` — `BUILD SUCCESSFUL` (30s).
- `& ".\gradlew.bat" build --no-daemon` — `BUILD SUCCESSFUL` (5s).
- `& ".\gradlew.bat" runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run` — `BUILD SUCCESSFUL` (20s).
- `build/gametest-run/logs/latest.log`: 110 tests started at `2026-09-24 16:23:53` (line 35); 110 complete at `16:23:56` (line 200); all 110 required tests passed (line 201).
- Python parsed 603 JSON files; `git diff --check` passed with CRLF warnings.

There was one intermittent failure earlier in validation: the existing FireStack GameTest `negativestacksdryoneperdueintervalandcleanup` failed at approximately 16:16, then passed in each of the next two runs. No FireStack code or test was changed. Treat this as a fixture timing/flakiness risk, not as a movement regression or as proof the test is deterministic.

## Required follow-up

The owner must repeat the smoke with a **matching rebuilt client and server** and a **backed-up test world**, following the [experimental connected-smoke checklist](experimental-connected-smoke.md). Specifically repeat Begin/Commit and ordinary survival movement, and capture both client/server logs. Record whether the slab/step case and ordinary movement complete without an uncaught tick error or unexpected disconnect. Until that authenticated retest is reported, connected movement after the fix remains unverified and acceptance gates remain open.
