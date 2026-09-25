# M1: Pure Mind/Mob Harness registry implementation audit

**Status: isolated M1 model and tests delivered.** This audit records the implementation and its boundaries; it does not authorize M2 integration or claim runtime behavior. The sprint remains separately default-off. See the [sprint instructions](../../../instructions-player-body-control-sprint.md), [M0c architecture contract](m0c-mind-and-mob-harness-contract.md), and [historical M0b proof](m0b-spectator-ghost-harness-proof.md) (whose carrier-as-ghost premise is superseded).

## Delivered model

The pure Java package is `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/`, with its focused test in `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/BodyControlRegistryTest.java`.

- `MindId` is a UUID identity distinct from the authenticated session-owner UUID used by registry operations. `MobHarnessId` identifies a registered harness; `MobHarness` records its `GHOST` or `CHARACTER` kind.
- `BodyControlRegistry.createMind` atomically associates an owner with a new Mind already attached to a registered, eligible ghost harness. Invalid, ineligible, already-owned ghost, and duplicate-owner creation attempts are rejected without creating a Mind. There is no default vacant Mind creation path.
- The synchronized registry enforces one current harness per Mind and one Mind per owned harness. `transfer` atomically replaces the current harness after validating the target and owner/epoch; failed transfers preserve the existing binding. Detachment/recovery uses `release` followed by `attach`, with recovery from a detached state restricted to an eligible ghost.
- Authorization rechecks supplied target eligibility. A no-longer-eligible target is revoked fail-closed; unregistering the currently owned harness detaches its Mind and advances its epoch. Epochs come from a monotonically incremented counter shared by the registry, including across logout and later Mind creation.
- The code is deliberately an in-memory policy model. `BodyControlRegistry` documents that callers must authenticate sessions and provide live target validation; the registry itself does not authenticate packets or inspect world entities.

## Tests and validation record

`BodyControlRegistryTest` contains 11 JUnit tests covering initial ghost attachment and rejected creation, ghost/character transfer, exclusivity, wrong owners and targets, stale epochs, detach/recovery, eligibility revocation, unregister and logout cleanup, and epoch behavior. The focused command was reported successful with 11 tests:

```powershell
& ".\gradlew.bat" test --tests "com.juicyslew.moonstation14.ms14.player_body_control.*" --no-daemon
```

This focused command is unit-test evidence for the pure registry. Later, after the M2a ghost entity and concurrent atmospherics changes were present, a full JUnit run (500 tests) and full build both passed after a `ServerClassloadingTest` correction; see the [M2a validation record](m2a-ghost-mob-harness-foundation.md). Those project-wide results are not attributable to M1 alone and do not establish connected-client behavior, entity movement, or runtime control for this registry.

## Scope, limits, and M2 gate

There are no world references, client/server hooks, network protocol or packet, actual ghost entity, entity registration/rendering, movement implementation, or command in this delivery. No acceptance is claimed for runtime functionality or full SS14 parity. SS14's nullspace Mind entity is only a conceptual reference: this local `MindId` is an in-memory identifier, not a persisted entity or durable Mind record.

M2 is gated on delivering a genuine ghost Mob Harness first—not treating the spectator `ServerPlayer` carrier as the ghost—then proving ghost control before adding NPC possession. Separate exact owner approval of the required shared files is still necessary. At audit time, concurrent atmospherics work has modified these shared files, as shown by the working tree:

- `src/main/java/com/juicyslew/moonstation14/Config.java`
- `src/main/java/com/juicyslew/moonstation14/MoonStation14.java`
- `src/main/java/com/juicyslew/moonstation14/component/ModDataAttachments.java`

Do not modify or overwrite those overlapping changes; coordinate before any M2 shared-file work. Their presence is a stop/coordination constraint, not permission to edit them. The feature must remain default-off until separately approved and proven.

## Source-review note

One API nuance should not be hidden by the high-level lifecycle shorthand: `BodyControlRegistry.ReleaseReason` includes both `FAILURE` and `LIFECYCLE`, and `release` rejects `VOLUNTARY` but accepts either non-voluntary reason. The recovery path still requires a ghost. If the intended product contract is strictly failure-only detachment (excluding lifecycle release), that should be reconciled in a separately authorized model/test follow-up; this documentation does not change Java behavior.
