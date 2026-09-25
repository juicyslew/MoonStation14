# 2026-09-24 two-session owner log and grounded-support regression addendum

**Scope:** bounded owner-run connected-session evidence plus a source-supported regression hypothesis. These observations are not an acceptance result, do not establish every tick's movement cause, and do not include a connected retest of the latest support-probe build. The first session used an earlier build; the second session has no recorded custom ownership evidence. See the [connected smoke checklist](experimental-connected-smoke.md) for a properly gated retest.

## Session 1 — dedicated server, earlier build

The dedicated-server logs are `run/logs/2026-09-24-2.log.gz` or `debug-1.log.gz`. Recorded event sequence: server startup at 22:24:46, player join at 22:28:53, custom Begin/Commit at 22:29:26, first admitted launch at 22:29:34.208, and end/disconnect at 22:30:56 (2026-09-24 local log time).

The aggregated diagnostics for this session report:

- 17 five-second surface summaries covering 1,701 ticks; movement factor range 0.05–1.0, `slidingLube` 1,334 ticks, `slidingNeutral` 0 ticks, `lubeToNeutral` 8 transitions, `preFrictionWishBlocked` 272 ticks, and maximum horizontal speed 6.7509 blocks/s.
- Eight admitted launch lines. The first reported speed change is 4.5001 to 6.7501 at 22:29:34.208; repeated lines are approximately 4.5 to 6.75.
- 1,699 client snapshots. The client summary independently counted 848 server-grounded flags and 848 client-grounded flags; these are independent totals and **do not mean the flags coincided on the same snapshots**. It reports projected 0 and hardSnaps 1,699, but only 14 nonzero position corrections, with maximum correction about 0.227 blocks.

These are aggregate summaries without per-tick input, position, contact, and paired-ground-flag detail. They establish recorded session counts and reported outcomes only; they cannot prove a precise input-to-speed cause, the exact tick-by-tick grounded transition, or that a player-visible jitter report was caused or fixed by these corrections. In particular, “hardSnaps” is a summary counter, not evidence that all 1,699 snapshots moved the player by a nonzero amount.

## Session 2 — client-hosted, no recorded custom ownership evidence

The client-hosted logs are `run/logs/debug.log` and `latest.log`. They record client boot at 22:34:04, server startup around 22:34:08, player join at 22:36:10, and disconnect at 22:37:04. There are **no** custom Begin/Commit, surface-summary, or admitted-launch lines for this session. Therefore, this is not evidence of a custom-motor regression and must not be combined with the first session as if both exercised the custom motor.

At inspection, `experimentalVerticalSliceMovement=true` was present, while the then-current `run/server.properties` had `force-gamemode=true` and `gamemode=creative`. Custom eligibility is survival/adventure, so creative is a plausible explanation for the missing ownership transition. This is not historical proof of the effective mode during that session: current configuration does not establish the session's actual mode or exact launched build. Confirm the matching client/server launch and build, effective per-session mode, and matching Begin/Commit in debug logs before inferring custom ownership or treating missing custom diagnostics as a motor failure.

## Grounded support-probe regression hypothesis

Source inspection supports a plausible explanation for grounded lube-factor changes and associated jitter in the first session, but does not prove it was the per-tick cause in that run. `CharacterMovementMotor` uses the surface factor for grounded acceleration; when an entity previously grounded with zero vertical velocity requests no downward displacement, Minecraft `Entity.move` may not refresh `onGround`. A lost grounded flag can skip the 0.05 lube factor for subsequent air ticks and use neutral acceleration instead (20 times the lube-scaled acceleration), potentially producing a factor/steering discontinuity and correction jitter. This is a source-supported mechanism, not a demonstrated reconstruction of the session.

The current motor requests a 0.001-block downward support probe only when the previous state is grounded, effective jump is not requested, vertical velocity is exactly zero, and gravity is positive. A real floor collision can then retain support; an unsupported probe can fall normally. Existing automated coverage includes floor, edge, jump, slab, and a 16-step turning REAL Villager GameTest at a 0.05 surface factor and 0.4 voluntary speed factor; the GameTest result is 116/116. This is automated/source evidence, not connected validation. The coordinator subsequently reran `& ".\gradlew.bat" test --rerun-tasks --no-daemon` (`BUILD SUCCESSFUL`, 37s) and `& ".\gradlew.bat" build --no-daemon` (`BUILD SUCCESSFUL`, 6s). These verified checks still are not connected validation.

`ServerPlayerStunActionMixin` also routes a stunned player's vanilla move-packet correction through `player.connection.teleport(...)` when the server startup gate is off or custom movement does not own the player; during enabled committed custom ownership it lets the custom packet boundary handle movement without a competing correction. This code path is relevant context for non-custom stun correction only. There is no session evidence that it caused either session's stop or disconnect, and it must not be presented as the cause.

## Required matching retest

For a meaningful latest-build test, launch matching client and server builds, verify opt-in at server startup, and connect an authenticated player in survival or adventure. If `force-gamemode=true` with creative configured, change the connected player to survival before testing. Confirm and preserve matching server/client debug logs showing Begin/Commit (and applicable handoff messages) before interpreting surface, launch, or correction diagnostics. Record effective mode and exact build for that session. Then collect per-session contact/factor, movement, correction, HUD, and video observations. The earlier dedicated session is useful bounded evidence; neither it nor the non-custom second session replaces this latest support-probe retest.
