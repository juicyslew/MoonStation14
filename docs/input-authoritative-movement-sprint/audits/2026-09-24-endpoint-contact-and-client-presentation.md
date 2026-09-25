# 2026-09-24 endpoint contact and client-presentation audit

**Status:** documentation of the current experimental implementation and the latest reported automated validation. It is not a measured performance benchmark or acceptance of the movement slice. The owner connected with an earlier build in a first diagnostic session, but has not retested the latest support-probe build. The two dated sessions and their limits are recorded in the [two-session owner log and grounded-support regression addendum](2026-09-24-two-session-owner-log-and-grounded-support.md). The owner still needs to test lube steering/turning, perceived speed, HUD status, animation, jitter/reconciliation, and mode/teleport behavior on matching client/server builds.

## Player slip contact and bounded work

Player slip-source contact is checked from the authoritative, collision-resolved **endpoint AABB**. The current check visits at most 16 block cells overlapped by that box (normally about 2×2×3); it does not sample a 64-cell swept path or take 0.125-block path samples. This intentionally bounded algorithm may miss a source crossed entirely between the previous and final positions, especially for an extremely fast endpoint-crossing movement. The owner explicitly accepts that limitation for now. This is not evidence that fast-crossing slips are caught.

Contact state is organized in a per-level weak registry of actors keyed by UUID. Each actor's source latch is bounded to 32 source positions; an emergency level-wide cap of 8192 entries is a memory-safety ceiling, not a routine scan budget. A player tick does not copy the entire level map or scan all actors: reconciliation concerns that actor's own contacts. Its source latch clears when the actual source-shape AABB is exited. Player/entity leave cleanup removes its registry state. This describes work bounds and ownership, not measured CPU time or allocation behavior.

The slippery source data is restored without a SpaceLube-only gate: source eligibility remains based on the existing slippery-source rules. A focused GameTest uses 20 units of soap and no lube dependency, and checks that a near-edge contact latch remains until the source-shape AABB is actually exited. These are automated endpoint/latch checks, not proof of connected traversal. The intentionally accepted fast-crossing limitation means they do not establish a swept-path guarantee.

## Turning, lube factor, speed, and jitter: evidence versus hypotheses

Pinned SS14 `SharedMoverController.Accelerate` uses velocity projected onto the wished direction. Its documented “snaking” behavior means a large direction reversal while moving quickly can produce an abrupt response; this is not Minecraft vanilla turning behavior. As an illustration only, at sprint wish speed 4.5 blocks/s and a 1.5 launch multiplier, launch speed is about 6.75 blocks/s. Against a 1.8 blocks/s knockdown wish, the projection threshold is `acos(1.8 / 6.75)`, about 74.5 degrees. That source-derived illustration does **not** identify the owner's particular turning report or prove parity with every local contact transition.

While qualifying feet contact is active, the local SpaceLube movement factor is 0.05. With acceleration 20, that gives effective acceleration 1 rather than 20; when the factor becomes neutral, the large difference could feel like an abrupt turning change. A large abrupt turn may also coincide with feet leaving/entering the qualifying source AABB or Sliding state changing. These are plausible explanations to investigate, not a confirmed cause. Record surface factor/contact status alongside inputs and turns in a connected session; no exact cause is established here.

The motor maps one Minecraft block to one SS14 tile. Pinned walk/sprint values are 2.5/4.5 blocks per second, slower than vanilla Minecraft by design. Do not infer a speed bug or a feel pass solely from those constants. For visible body orientation, the latest client-only render wrapper temporarily sets `yBodyRotO`/`yBodyRot` from the quantized world-space movement wish, and restores both in `try/finally`. This is visual-only: head/gameplay yaw and server packets remain untouched. It has not been checked in a connected render smoke.

The latest experimental client phase runs the custom motor in `LivingEntity.travel` at the input tick, caches/sends that frame once from the tick hook, and calls vanilla `calculateEntityAnimation` after an accepted move rather than manually calculating animation late. This is intended to align movement and presentation timing; it does not prove tiny gait jitter is fixed. Grounded correction preserves bounded unacknowledged displacement arithmetically rather than fully replaying pending inputs. Jitter may relate to projected corrections, duplicate/missing ACK history, or other client/server divergence, but none is measured as the cause. A short video and matching client logs are needed; the client-only bounded correction summary is about five seconds and intentionally contains no coordinates.

The right-side HUD now presents `Stunned` from the synced action-block state, `Slipped` from the synced Sliding marker, and `Knocked Down` from the existing synced status, after Thirsty/Peckish. These entries may overlap intentionally because the states have different lifetimes. No new server attachment, status owner, or source JSON was added for the HUD; English localization keys exist. The owner has not checked the overlay or its synchronization in game.

The latest server-only diagnostic adds one `[movement server] surface` summary about every five seconds per movement session. It reports sliding-lube and neutral tick counts, minimum/maximum movement factor, ticks where pre-friction velocity was blocked in the projected wish direction, lube-to-neutral contact transitions, and maximum horizontal speed. It does not log raw coordinates. The client correction summary is likewise about every five seconds and reports projected/hard corrections and duplicate ACKs, without coordinates. These are observation aids, not evidence of connected behavior or its cause; correlate the summaries with HUD state, inputs, and video in the owner retest.

## Puddle amount caution

Nine 200-unit jugs distributed over an existing puddle capacity of 1000 units and 50-unit overflow do not establish a fixed amount in every resulting puddle. The number, placement, and amount of puddles depend on tick flow and distribution. Do not claim that each puddle has 50 units without measuring its actual contents. The separate connected three-puddle test and numeric traversal bound remain open.

## Latest reported validation and open evidence

The coordinator reported the following latest checks on 2026-09-24:

- `& ".\gradlew.bat" test --rerun-tasks --no-daemon` — `BUILD SUCCESSFUL`, 32s.
- `& ".\gradlew.bat" build --no-daemon` — `BUILD SUCCESSFUL`, 5s.
- `& ".\gradlew.bat" runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run` — `BUILD SUCCESSFUL`; latest log line 35 reports 114 GameTests started at 2026-09-24 22:03:46.589, and lines 244-245 report 114 complete and required passed at 22:03:49.585.
- 603 JSON files parsed; `git diff --check` passed with LF/CRLF warnings.

These are coordinator-reported results, not reruns in this documentation task and not connected-client proof. The owner has not retested these latest code changes with matching client/server builds. No 20- or 50-player 20Hz workload benchmark has been performed: the registry/latch limits are complexity and memory bounds only, and CPU/allocations remain unmeasured. Full connected mode/teleport validation and the three-puddle acceptance scenario remain open. The [connected smoke checklist](experimental-connected-smoke.md) includes the owner retest; previous dated reports remain historical evidence and are not rewritten here.
