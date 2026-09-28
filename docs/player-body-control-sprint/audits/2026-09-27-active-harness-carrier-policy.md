# 2026-09-27 active harness carrier policy and connected report

**Status: bounded active-carrier policy is implemented; owner reports a connected ghost/body/return outcome. This is not full lifecycle acceptance.** This audit records the exact character-policy boundary and the limited owner-reported connected outcome. It does not supply logs, protocol/epoch capture, performance measurements, a specific target-removal mechanism, or evidence of complete lifecycle behavior. See the [sprint instructions](../Instructions.md), [possessed-character HUD source handoff](2026-09-27-character-hud-source-handoff.md), [committed CHARACTER loss recovery audit](2026-09-27-committed-character-loss-recovery.md), and [operator possession handoff audit](2026-09-27-operator-possession-handoff.md).

## Bounded owner-reported connected outcome

In response to the coordinator's request to test ghost → Villager → fresh ghost, ghost → Pig → fresh ghost, and losing a possessed body, the owner replied: **“This is done. I did this”.** This is recorded as a bounded owner-reported connected outcome for those requested scenarios. No session logs, performance or epoch/protocol capture, details of how the possessed body was removed/lost, or other corroborating artifacts were supplied. Do not infer exact transfer mechanics or generalize this report into full lifecycle acceptance, negative-case coverage, or proof of every cleanup path.

## Implemented carrier and character-policy boundary

- `GhostMobHarnessControl.isExperimentalCarrier(ServerPlayer)` identifies an exact connected session with an experimental harness control, including when its active harness is a ghost and when the session has not yet committed a harness. It is not a test for all players or a character-body identity conversion.
- `GhostMobHarnessControl.activeHarness` is a read-only view of the committed registry binding, matched against Mind ID, epoch, harness kind, and entity. `activeCharacterBody` returns a body only for a committed `CHARACTER` harness. `ActiveCharacterPolicy` exposes this routing boundary.
- `ActiveCharacterPolicy.resolveActor` returns no character profile for an active carrier. The legacy `CharacterIdentitySystem.resolve` / enrollment path and persisted `HUMAN` identity are unchanged. **Do not remove legacy `minecraft:player` HUMAN identity** until a future custom-created character Mob Harness replaces it.
- Server-side stun/knockdown handling in `CharacterControlSystem`, plus `SlipSystem` and `ReactiveTouchSystem`, use the active-character boundary. A Mob body continues to use its own status and slip behavior; carrier filtering does not retarget a world contact to a distant active Mob.
- Hunger and thirst type eligibility excludes the carrier. Carrier start/end reconciliation rebuilds existing nutrition projections and derived activity without overwriting scalar values. The food and peaceful-mode mixins preserve the existing vanilla food/peaceful suppression behavior, and existing attachments are preserved.
- `TickHooks.runDueActivities` skips these six carrier-body activities: status, alerts, reagents, fire, hunger, and thirst. This does not change the carrier's ordinary body tick.
- Thermal/respiratory exposure and data remain concurrently owned and unchanged. This work does not claim that all body systems are separated.

## Explicitly unmodeled or untested

Direct generic status, reagent, effect, and damage/health paths on a carrier; player attack/interactions/role inventory; thermal behavior; and full body UI are unmodeled or untested here. The bounded HUD source implementation and pending connected retest are documented in the [HUD handoff](2026-09-27-character-hud-source-handoff.md); it does not supply carrier actions, a hotbar, or full UI. Do not auto-retarget a carrier's world contact to its possibly distant controlled Mob. The legacy route and host mapping remain retained; this policy is not authorization to remove them.

## Validation record and limits

The worker reported the focused owner/player-body-control/character/hunger/thirst JUnit checks and `compileGametestJava` passed. The first dedicated `gametest-active-body-routing` run completed 132 GameTests with four failures: three concurrent atmosphere failures and the flaky `boundhumanplayerandvillagersharetimedstunpolicy` assertion checking a three-tick expiry at the same boundary. A narrow TEST-only adjustment waited four ticks and split diagnostic assertions. The latest retest log, `build/gametest-active-body-routing-retest/logs/latest.log`, records 132 GameTests and exactly two required atmosphere failures: `coveredexteriorsealingandbreachrespectownership` and `fullwallopeningdrainsmorethanoneblockopening`. This is **not green** and is not an authenticated carrier-identity GameTest. No test result here upgrades the bounded owner report into automated or complete lifecycle acceptance.

## Follow-up boundary

Follow up with narrow owner-connected carrier/legacy recovery validation, retaining evidence limits above. Broad actions/inventory and the SS14 YAML importer/parity require a separate sprint. Do not assume the full player-body-control sprint is closed.

Implementation references: `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/server/GhostMobHarnessControl.java`, `ActiveCharacterPolicy.java`, `CarrierActivityPolicy.java`, `src/main/java/com/juicyslew/moonstation14/eventhooks/TickHooks.java`, and the character, slip, hunger, thirst, and food/peaceful mixin systems.
