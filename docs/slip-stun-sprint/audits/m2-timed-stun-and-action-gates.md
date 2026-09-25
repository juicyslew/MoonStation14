# M2 Timed Stun and Action-Gates Report

**Review date:** 2026-09-24  
**Milestone result:** The bounded timed status policy and immobility projection are implemented and covered by server GameTests. Player and villager execution hooks exist, but important end-to-end behaviors remain unverified. This is not full SS14 knockdown/action coverage or completion of the slip/stun sprint.  
**Evidence:** Reviewed implementation and the existing `build/gametest-run/logs/latest.log`; no tests/build were run for this documentation-only report.

## Implemented behavior

- The typed `StatusEffectBehavior` enum includes `STUN_ACTION_BLOCK`, serialized as `stun_action_block`; `statuseffectstunned.json` declares it alongside `marker`. `CharacterControlSystem` is the shared server policy: `applyStun` rejects nonpositive durations, non-server entities, unresolved/ineligible character profiles and entities without the status trait. Eligible bound humans receive the existing `statuseffectstunned` through `StatusEffectSystem` using finite ticks. The seconds overload uses the status system's seconds-to-ticks conversion. Mob application also stops its navigation immediately.
- `isStunned` resolves the bound profile and inspects the active status snapshot against the current status catalog for any active definition carrying `STUN_ACTION_BLOCK`; `canAct` negates that result. This is policy driven, not a hard-coded entity-type/status-name check. Missing/unbound/dangling identity stays inert/fails closed. Identity resolution's unresolved-catalog diagnostic is warn-once per catalog/key (bounded cache).
- `LivingEntityStunImmobileMixin` ORs active stun into the vanilla `isImmobile` return value. Its GameTest-facing invoker lets the test check this projection, but `isImmobile` alone is not proof of every action route being blocked.
- Player action interceptions are in `ServerPlayerStunActionMixin` on `ServerGamePacketListenerImpl`. Nine handlers are gated: `handlePlayerInput`, `handleMovePlayer`, `handleUseItemOn`, `handleUseItem`, `handleInteract`, `handlePlayerAction`, `handlePlayerCommand`, `handleContainerClick`, and `handleSetCreativeModeSlot`. Each injection is after `PacketUtils.ensureRunningOnSameThread`, so the policy lookup and cancellation occur on the server thread. Denied movement gets vanilla connection position correction; denied container/creative-slot clicks synchronize menu state. `ServerPlayerStunPickupMixin` additionally cancels server-player item-entity pickup while `canAct` is false.
- `VillagerStunBrainMixin` cancels `Villager.customServerAiStep` at HEAD while stunned. Navigation is stopped when `applyStun` runs. These are the implemented hooks; they do not establish that all villager Brain/navigation work is paused in a real moving AI scenario.

## GameTest evidence and limits

`CharacterControlGameTests.boundHumanPlayerAndVillagerShareTimedStunPolicy` uses a `FakePlayer` (a `ServerPlayer` test fixture, not an authenticated connected player) and a spawned real Minecraft Villager. It verifies both are bound/eligible for the shared human policy, accept a three-tick finite stun, report active stun and denied `canAct`, and project as immobile. It checks both recover `canAct` and immobility after expiry and that the expired status entry is removed. An unbound pig stays inert/mobile without status or identity attachment materialization; a dangling profile also refuses status storage.

The existing latest GameTest log reports 91 tests discovered, `91 GAME TESTS COMPLETE`, and `All 91 required tests passed` (2026-09-24 01:05:06). It includes this control test. The run also contains expected diagnostic/error-path test logging (including unresolved identity warning and a deliberately missing status-effect test); all required tests passed. Identity warning behavior is now warn-once for unresolved catalog/key pairs. The log establishes the overall run count, not packet-level or AI navigation behavior.

M2 tests verify shared policy, status lifetime/expiry, and immobility only. They **do not** verify the effect of intercepted inbound player packets on a real connected connection, nor demonstrate a real player's clicks/actions being rejected over that connection. Two attempted packet GameTest fixtures were abandoned: a bare channel was null and crashed, while an `EmbeddedChannel` marked active still could not establish an unstunned dirt-placement baseline. The fixture was removed; this is a test-fixture limitation, not evidence that the packet gate passes or fails end-to-end.

Likewise, no GameTest demonstrates actual Villager Brain navigation pause or absence of AI motion. An attempted AI fixture's `moveTo` returned false, so it supplied no AI motion proof. The mixin and navigation stop are implemented, but the villager behavioral outcome remains an evidence gap.

## Physical scope and milestone decision

Full prone/crawl is **not implemented**. Minecraft pose changes such as `Pose.SWIMMING` alone would not faithfully represent SS14 physical knockdown/crawling, and no physical prone/crawl behavior is claimed. The current status/action policy and vanilla immobility projection are bounded functionality, not full SS14 physical or action parity.

M3 may proceed with this bounded M2 result and explicit risk: existing hooks still need connected-player packet-effect validation and real villager AI/navigation validation before claiming those execution effects. This report does not waive root `instructions.md` requirements for later sprint acceptance. M5 acceptance remains contingent on owner-run manual player action smoke and any further tests needed to close or explicitly bound these gaps; M5 must not describe M2 as full physical knockdown or proven network/AI enforcement.

## Owner-only manual smoke (not performed)

On a controlled development server with a real connected player, first confirm an ordinary unstunned action works (for example, place dirt on a target block). Have test/admin tooling apply a short stun to that bound player using the server-side `CharacterControlSystem.applyStun(player, durationTicks)` API (or an equivalent controlled harness); there is no player-facing stun command yet, so do not assume a command exists. During the active interval, try the same placement plus representative use/interact/inventory click and movement actions; confirm they are denied/corrected and pickup is blocked. After the finite duration expires, repeat an action and confirm normal behavior returns. Record server log and observations. This manual test is the owner's responsibility and was not performed here; a successful smoke does not replace automated packet-handler and AI tests.

## Validation and remaining work

- Existing evidence inspected: `build/gametest-run/logs/latest.log`, 91/91 required GameTests passed.
- No build or tests were run as part of this documentation-only task; no game was launched.
- Still needed for stronger M2 evidence: a viable real-connection/packet GameTest proving at least representative inbound action cancellation and recovery, and an AI fixture that can demonstrate Villager navigation/motion pause and resume. Keep physical prone/crawl separately scoped and reported.
