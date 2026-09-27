# 2026-09-27 committed character loss recovery and sprint scope

**Status: bounded recovery is implemented; connected lifecycle acceptance remains open.** This records the current committed-CHARACTER loss recovery behavior, its validation boundary, and the scope decision to keep unrelated systems in separate milestones. It does not claim a universally guaranteed one-Mind active-play lifecycle. See the [sprint instructions](../../../instructions-player-body-control-sprint.md), [operator possession handoff audit](2026-09-27-operator-possession-handoff.md), and [active harness carrier policy audit](2026-09-27-active-harness-carrier-policy.md) for the bounded owner-reported carrier scenarios and policy boundary.

## Bounded recovery behavior

- If the committed CHARACTER becomes ineligible, dies, or is removed while its owner is still the same connected, alive spectator in the same dimension, recovery transfers the **same registry Mind** to a newly spawned ghost harness through the ownership epoch and Begin / Ready / Commit handoff.
- Recovery handles both registry-authorized detachment and a transfer where the Mind remains attached. The old character body remains in the world. Its lease is restored, logged, or quarantined as appropriate; pending ghost/offer state is cleaned up.
- This bounded path does not apply to logout, clone, dimension change, game-mode change, or server stop. Failures still end the debug session. Therefore, exactly one Mind-bound harness during active play is not universally guaranteed by this recovery path.
- No connected death scenario or negative-case recovery proof has been supplied. The behavior is implementation status, not connected-play acceptance.

## Camera behavior and validation

The third-party Spectator-camera `/ms14 mindghost stop` cleanup now restores the camera to the player even when the camera was not owned by this harness. Separately, the owner confirmed that both possessed-Mob WALK animation and mouse turning work well on their connected setup. This corrects the earlier operator handoff audit's statement that a post-fix visual retest was still pending; it is bounded owner feedback, not broader Villager/Pig, multi-client, lifecycle, or possession acceptance.

The worker reported these focused checks passed:

- `test --tests com.juicyslew.moonstation14.ms14.player_body_control.*`
- `compileGametestJava`

Pure registry tests cover attached and detached transfer paths; they do not exercise a live connected player. The earlier coordinator attempt ran `& '.\gradlew.bat' runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-body-loss-recovery` and **failed at `:compileJava` before any GameTests ran**. At that time, a concurrent atmos change at `src/main/java/com/juicyslew/moonstation14/ms14/atmos/core/MonstermosSpaceFlow.java:25` had a comparator method-reference type-inference error. The owning atmos work later resolved that blocker; this sprint did not touch that file. The coordinator then reran the exact command `& '.\gradlew.bat' runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-body-loss-recovery`, which completed **132 GameTests, not green**: exactly two required concurrent atmos failures, `coveredexteriorsealingandbreachrespectownership` and `fullwallopeningdrainsmorethanoneblockopening`. The evidence is `build/gametest-body-loss-recovery/logs/latest.log` lines 288–291. No possession tests are listed as failing, but this does not establish that a live controller-death test exists or ran. This remains dedicated-server GameTest evidence, not connected-player acceptance.

## Current sprint boundary and remaining gates

Death/ineligibility recovery and camera cleanup are well-defined relevant work in this sprint. Detailed inventory, hands, equipment, speech/action contracts, role/profile/lobby systems, and the SS14 prototype importer require separately scoped milestones, consistent with the [next-systems roadmap](../../next-systems-roadmap.md).

Carrier identity separation and retirement of the old default-off player controller remain ordered player-body-control sprint gates. They are not safe to patch before connected lifecycle evidence and supported mode/teleport proof exist. Preserve these gates without expanding the current bounded recovery work into those larger changes.
