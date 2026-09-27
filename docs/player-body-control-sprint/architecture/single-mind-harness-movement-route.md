# Single Mind / Mob Harness movement route

**Status: owner architectural decision; target architecture, not an implementation or acceptance claim.** There will never be a normal active-play situation in which a player is not a Mind controlling a Mob Harness. The sole exception is lobby, and only while a server-owned lobby state says the session is in lobby. A Mind without an active harness is not a transient ghost/body state. This decision rejects a permanent second movement or prediction path for the Minecraft player entity. See the [sprint instructions](../../../instructions-player-body-control-sprint.md) for current sprint status and migration gates.

## Decision and current compliance

An authenticated user/session resolves to one Mind. During active play, that Mind controls exactly one currently owned, server-eligible Mob Harness: either a ghost or a configured character. It does not control the Minecraft `ServerPlayer`. The Mind has one epoch/body ID and one server-authoritative input, snapshot, and pending-replay contract across every controlled harness. Each harness can have its own explicit physics policy, but movement calculation and reconciliation are not forked into permanent ghost and player paths.

The lobby exception is deliberately narrow. A session/Mind may have no harness only while a server-owned lobby status is active. Do not represent it as a ghost without an entity, infer it from a missing body, or invent lobby/login lifecycle now. No current lobby state is modeled; introduce one only with an actual server-owned lobby/round lifecycle. An SS14 lobby may lack a Mind/entity, which is not a reason to claim or fabricate that lifecycle here.

**The current implementation is not compliant with this target and is not accepted.** `BodyControlRegistry` currently creates a Mind only with an eligible registered ghost; it has no lobby state. `GhostMobHarnessControl` is an operator-command ghost experiment, with only one authenticated-client experiment; the carrier `ServerPlayer` still has a HUMAN attachment. It does not yet separate authenticated carrier identity from character identity or establish active-play body lifecycle. Do not claim NPC transfer, on-join control, lobby behavior, carrier identity separation, or connected-owner acceptance.

In parallel, the old `MovementServerController` / `MovementClientController` path independently controls `ServerPlayer` / `LocalPlayer` for grounded Survival/Adventure under `experimentalVerticalSliceMovement`. It has a mode handoff and bounded ground-only arithmetic correction, not full replay. Its gate is currently default-off. Ghost control instead uses `GhostMovementMotor` around the same `CharacterMovementMotor`, a ghost-entity protocol, 64-input full replay, and a separate prediction path. Sharing the arithmetic motor does not make these two ownership/protocol/prediction paths one architecture. The legacy path is transitional evidence and must eventually be retired, not generalized as a second game path.

## One movement contract, per-body policy

The common route is authenticated session → Mind → its one active harness. Input is resolved against the server-owned Mind binding; clients never choose a target or send a position claim. The server remains authoritative for movement outcomes and external effects. Snapshots identify the Mind epoch and body ID, and acknowledgement/replay applies to that same authoritative contract when a Mind changes harness. A handoff must prevent stale input or snapshots for the previous body from controlling or correcting the next one.

Ghost and character movement share the existing `CharacterMovementMotor`; differences belong in named, explicit per-harness physics policy rather than a second controller or prediction system. Ghost `noGravity`/`noPhysics` is an interim ghost policy, not collision parity. A configured HUMAN character retains its pinned walk/sprint speeds and friction values. Character resolution uses server `Entity.move` collision and pauses AI while controlled. Those policy details still need verification at their implementation gates; this document does not claim they exist already.

Actions, inventory, status, and slip belong to the active body, not the transport carrier. The Minecraft `ServerPlayer` is retained only as needed for spectator connection/camera transport; it has no character policy, status/health, or action authority. Preserve vanilla transport during transitions, but fail closed for unsupported harness modes (including creative, swimming, or passenger) until they are modeled. Do not fall back to moving `ServerPlayer` as a permanent gameplay path.

## Shared-route comparison

- **Ghost harness:** interim `noGravity`/noclip flight behavior.
- **Human character harness:** grounded movement with server collision, friction, sprint, and knockdown policy. This remains target behavior; no character harness is claimed as implemented.
- **Lobby exception:** a Mind may have no harness only while a server-owned round/lobby state says the session is in lobby; no such state is currently modeled.

## Ordered, gated migration

Small steps are required. Passing an earlier gate does not accept this architecture as implemented or authorize skipping later checks.

0. **Retest the connected ghost owner.** Complete the owner-connected ghost retest against the new prediction and mixin path. Record failures as observations and stop on crash/injection failure. Until this succeeds, the ghost path is not proven and no migration can rely on its connected behavior.
1. **Generalize the ghost controller, not the old player controller.** Make the Mind binding/body ID the actor target for input, protocol, and prediction. Generalize the ghost controller/protocol/replay contract to harness ownership; do not create a parallel character controller. Prove epoch/body handoffs and stale-frame rejection.
2. **Add one real character harness.** Configure a real Villager as a HUMAN character harness on that same Mind input/reconciliation route. Use server `Entity.move` collision resolution and pause its AI while controlled. Verify connected-owner switches ghost ↔ character without a second prediction path or authority gap.
3. **Move gameplay identity to the active body.** Assign status, slip, friction/knockdown, role inventory, and action ownership to the active body; remove character identity/authority from the carrier. Preserve vanilla transport during transitions. Add lobby-without-harness only if/when a server-owned round/lobby status is introduced; do not infer it now.
4. **Retire the transitional route only after replacement proof.** Remove `MovementServerController`, `MovementClientController`, their protocol/networking, mixins, bootstrap/configuration (`Config.experimentalVerticalSliceMovement`), and their tests only after the replacement passes connected lifecycle, mode-switch, and teleport checks and a search finds no residual references. Do not delete prematurely: ghost connected prediction, exclusive owner mode handoffs, and the character adapter have not been proven. Retain the pure motor/character prototypes and sprint historical evidence. Keep the legacy default-off gate until retirement is complete.

The completion bar for retirement includes connected owner tests through lifecycle and supported mode transitions, teleport/reconciliation, and proof that no residual references keep the old route alive. Unsupported modes remain fail-closed; transport continuity does not imply gameplay support.
