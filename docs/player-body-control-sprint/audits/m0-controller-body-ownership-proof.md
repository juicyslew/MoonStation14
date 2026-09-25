# M0: Controller/body ownership proof and decision gate

> **SUPERSEDED — historical source evidence only.** The former “old player body” premise, recommendations, command forms, and pending decisions below do not describe the current design. The owner corrected the premise: the authenticated `ServerPlayer` is only a spectator/ghost/session carrier, with no dedicated playable body; configured `LivingEntity` actors are character harnesses. Use the current [M0b spectator/ghost harness proof](m0b-spectator-ghost-harness-proof.md) and [root sprint instructions](../../../instructions-player-body-control-sprint.md). This historical note is retained to preserve its original evidence; do not interpret its body-disposition discussion as current.

**Status: investigation complete; stop before implementation.** This note records the source evidence and the smallest plausible design boundary. It does not authorize code, command, protocol, or shared-system changes. A camera change is not body control.

## Source evidence

### Reference model (conceptual only)

Pinned SS14 source separates the authenticated user from the controlled body: `Content.Shared/Mind/SharedMindSystem.cs:119-139,507-527` maps `NetUserId` to a `Mind`; `Content.Server/Mind/MindSystem.cs:219-272` transfers a mind by updating its old/new body and the session's attached entity; `Content.Shared/Mind/Components/MindContainerComponent.cs:6-23` describes the body-side mind container. This demonstrates a conceptual ownership model, **not** a Minecraft or NeoForge API available to this mod.

### Minecraft and mod paths

- In the pinned generated Minecraft 1.21.1 NeoForge source archive `.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_030c49e34b61d0ceaabf5a9185565bb391167493_output.jar`, `ServerPlayer.java:1734-1754` implements `setCamera(target)` by teleporting the `ServerPlayer` to the target coordinates and sending a camera packet. That method has no spectator guard in the inspected path. It changes view and moves the player; it does not transfer movement control to the target. Therefore camera-only fails the control requirement, and using `setCamera` while claiming the original body stays physically stationary is unsafe.
- The same pinned source's `ServerGamePacketListenerImpl.java:872-974` processes vanilla movement on `this.player`; `LocalPlayer.java:235-305,687-745` samples/updates the local player. These paths do not establish a supported alternate-body control adapter.
- `ms14/character/CharacterIdentitySystem.java:34-72` binds the prototype/HUMAN key to a `ServerPlayer` or Villager body; this is not authenticated-session ownership.
- `movement/client/MovementClientController.java:201-267` sends intent sampled from `LocalPlayer`. `movement/server/MovementServerController.java:47-98,140-202,210-249,391-410` tracks sessions and resolves collision for `ServerPlayer`. `movement/protocol/MovementPayloads.java:78-96` has no body target. That experiment remains default-off; it is not an NPC possession path.
- `VillagerStunBrainMixin.java` pauses Villager AI while stunned; no inspected adapter arbitrates AI wishes against player input. Existing player interactions, inventory, and status are player-centric. No command is registered yet.

The inspected paths do not prove a supported server-side API for reassigning a vanilla player connection to an NPC, nor a client-view workaround that avoids `setCamera`'s player teleport while providing usable Villager view. These are unresolved, blocking probes—not assumptions to fill in during implementation.

## Alternatives and recommendation

**A. Camera-only — reject.** Does not route keyboard input to the Villager, and the inspected `setCamera` implementation also teleports the ServerPlayer. It cannot satisfy body-control acceptance.

**B. Swap/reassign the vanilla player — reject as unproven and unsafe.** No inspected API establishes connection ownership transfer, preservation of ordinary player movement/prediction, or safe return/lifecycle handling. Do not claim that changing the camera or an entity reference performs this transfer.

**C. Dedicated authenticated owner-to-NPC binding plus independent input relay — smallest plausible design, still gated.** It would require new shared client/server input and body-routing code, server-authoritative bounded/validated input and collision for the selected Villager, explicit AI pause/arbitration and resume, independent view handling, an actual server acknowledgement of the active body, and deterministic lifecycle cleanup. The current player-specific movement path cannot simply be relabeled. This is broader than the approved M0 silo and requires a precise owner-approved shared-path list before any such work.

If approved, the narrow initial semantics proposed for owner decision are: keep the old player body stationary but still subject to normal physics and damage; deny player interactions/actions and inventory access while controlling the Villager; clear control and return to self on target or player death; leave other lifecycle cases fail-closed until explicitly specified. A view solution avoiding `setCamera`'s teleport is not yet established. Do not implement or promise these semantics as verified behavior.

## Exact gate and owner decisions

The only proposed command forms are `/ms14 bodycontrol <villager>` and `/ms14 bodycontrol self`; permission level 2 is a candidate, not approved policy. **Neither command exists or is authorized.** Do not implement one unless the owner approves a genuine-input-routing design, its shared-file boundary, and the decisions below. A command response alone is not proof of control.

Before any implementation, the owner must decide:

1. Whether to pursue alternative C at all, and approve the exact shared client/server, input, movement/collision, lifecycle, command-registration, and view paths required; otherwise stop as not safely feasible within this scope.
2. Whether the proposed old-player-body disposition is acceptable, including physics, damage/status, interactions/actions, inventory/equipment, and ownership while control is active.
3. The permission policy (permission 2 or stricter), initial interaction scope, and safe behavior for death, disconnect, dimension change, teleport, target removal/invalidity, permission loss, and concurrent/repeated requests.
4. An independently verified client-view strategy that does not rely on the inspected player-teleporting `setCamera` behavior, plus the server acknowledgement and authenticated-owner smoke evidence required to demonstrate real body input.
5. Whether an independent default-off feature flag is needed, and how any approved integration remains compatible with the existing default-off movement experiment.

After approval only, a proposed sequence is isolated pure binding tests first, followed by a separately approved gated relay/command and an authenticated owner smoke. Do not start that work before approval. If source investigation cannot prove a safe client-view workaround, stop rather than promise one. This is M0 evidence and a decision request, not a declaration that the implementation is safe or feasible.
