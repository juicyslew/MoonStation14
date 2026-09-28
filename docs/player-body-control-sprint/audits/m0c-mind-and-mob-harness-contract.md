# M0c: Mind and Mob Harness contract

**Status: architecture/source record only.** The owner explicitly selected a distinct authenticated control-session **Mind** and a **Mob Harness** for every controllable entity, including the ghost. The earlier M0b premise of treating the Minecraft `ServerPlayer` spectator carrier as the ghost and allowing the Mind to be vacant by default is superseded. This audit authorizes only the isolated M1 pure model/tests identified below; it does not authorize shared integration, a command, or claim that a complete SS14 architecture has been implemented. See the [current sprint instructions](../Instructions.md) and the [superseded M0b audit](m0b-spectator-ghost-harness-proof.md).

## Pinned SS14 evidence

The following references were verified against pinned SS14 source at commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91` and its RobustToolbox checkout. They support the conceptual separation and control-transfer contract; they are not Minecraft APIs or instructions to port SS14 wholesale.

- `RobustToolbox/Robust.Shared/Player/CommonSession.cs:11-18`: a session has a user ID and nullable `AttachedEntity`. Session identity and currently attached control entity are distinct concepts.
- `Content.Shared/Mind/MindComponent.cs:9-24,54-81`: Mind is a separate entity in nullspace, with owned and visiting entity concepts. A Mind is not identical to the authenticated user/session.
- `Content.Server/Mind/MindSystem.cs:170-273`: `TransferTo` clears old body ownership/container state and attaches the Mind to a new body; for a connected session it updates session `AttachedEntity`. This is transfer/replacement, not a design requirement that a normal ghost Mind have no body.
- `Content.Server/Ghost/GhostSystem.cs:448-497`: ghosting can use `Visit` versus `TransferTo` depending on return path. `Visit` and ownership transfer are not interchangeable. In the selected local contract, a controllable ghost is itself the active harness; do not infer that Minecraft must copy each SS14 ghosting branch.
- `Resources/Prototypes/Entities/Mobs/Player/observer.yml:1-82,110-118`: the observer is an actual `MobObserver` entity with input, physics, and movement-related components. It is not an absent entity or camera-only state.
- `Content.Server/GameTicking/GameTicker.Spawning.cs:334-365`: role/body spawning creates a Mind and transfers it to the spawned mob, illustrating body creation followed by Mind attachment.
- `Content.Shared/Movement/Systems/SharedMoverController.Input.cs:579-603`: movement input targets `session.AttachedEntity`, demonstrating native attached-entity routing in SS14. Minecraft packets do not inherit this routing.
- `Content.Shared/Mind/SharedMindSystem.cs:619-647`: `MakeSentient` ensures a mind container and required input/movement components when allowed. `Content.Server/Mind/MindSystem.cs:339-360` shows admin `controlmob` making a target sentient and transferring the Mind; it is evidence of a distinct control path, not permission or an equivalent command here.

SS14 architecture is the reference model, not a claim that every target there is a Minecraft `LivingEntity`, nor a claim that every local entity is eligible. In this feature, configured Mob Harness eligibility is defined by the local supported set, including supported configured `LivingEntity` characters and the living, controllable ghost harness.

## Minecraft-specific seam and contract

Minecraft's authenticated connection still requires its `ServerPlayer` transport entity. In the pinned merged Minecraft sources, `ServerGamePacketListenerImpl.java:872-1001` handles vanilla movement against `this.player`, while `LocalPlayer.java:266-315,700-702` sends movement for the controlled local player/camera path; neither automatically targets a separate harness. `ServerPlayer.java:1734-1754` camera targeting is view/position behavior, not input ownership. Keep the carrier as a guarded, invisible spectator/session entity with no character status; do not label it the ghost or give it the controlled body's identity/actions. Its actual visibility still requires connected proof. Spawn a separate, server-owned, non-`ServerPlayer` ghost entity. It is independently movable and viewable and must be registered/rendered via the approved mod bus. Do not copy upstream SS14 audio assets.

Create a Mind on initial opt-in join (or first opt-in session) and create/attach its ghost Mob Harness. Bind Mind ID separately from the authenticated session owner ID. Route client input only after authenticating the session against that Mind; the server resolves the Mind's active harness, never a client-selected entity. The default active harness is the ghost, not null. Transfer to a supported configured NPC replaces the Mind's active harness. Returning to ghost creates or reattaches a ghost harness and activates it. Temporary detach is reserved for lifecycle/failure handling and must have explicit fail-closed recovery; it is not the ordinary ghost mode.

Represent harness kind explicitly (at least `GHOST` and `CHARACTER`) and enforce one active harness per Mind and one Mind per harness. Initial control is movement/look only. The first NPC fixture is a configured human Villager; eligibility must remain a server-defined supported configured set rather than a Villager-only predicate. A view/camera transition without authenticated input and authoritative collision movement is not control.

## Milestones and hard stop

### M1 — pure model/tests (owner-authorized, isolated)

Permitted only in the new `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/**` and matching test package. Define distinct Mind ID and authenticated owner ID, ghost/character `MobHarnessKind`, exclusive active-harness registry, transfer/replacement, controlled temporary detach/recovery, and deterministic cleanup. Test exclusivity/conflicts, wrong owner, transfer, invalid or removed harness, detached-state rules, and cleanup. No live movement, network, command, config, prototypes, entity registration, lifecycle hooks, or shared integration at M1.

### M2 — ghost harness and connected movement (not authorized yet)

Requires a later explicit, exact shared-file approval. Register/spawn/render the separate ghost entity; route owner-authenticated client input to its active harness; and prove connected, server-authoritative collision movement for the ghost first. Only after that connected proof may the configured NPC harness and genuine NPC movement be added. A future command proposal may use `/ms14 bodycontrol ...`; no command exists or is authorized now. Camera-only movement, a fake player, or a command that only changes view does not pass.

### M3 — connected visibility/transitions

Connected owner proof after implementation must establish carrier non-exposure as a player avatar, intended ghost and harness visibility/view, actual movement, transfer/return, and lifecycle cleanup. Do not claim this proof from source inspection or unit tests.

### Shared-file wishlist and stop due to concurrent work

Potential M2 integration areas to present for a future exact review—not authorization—are narrowly scoped input protocol and server motor hooks, approved mod-bus ghost entity/render registration and its local assets/prototype, Mind/harness lifecycle integration, a new default-off opt-in gate, and command registration only after ghost movement proof. The exact source files and hooks remain to be determined and approved. Stop before M2: `Config.java`, `MoonStation14.java`, and `component/ModDataAttachments.java` are in concurrent atmospherics work, along with other shared files. Do not edit those overlaps or any atmosphere files; coordinate before proposing shared-file changes. Current isolated M1 authorization does not imply M2 approval.

## Limitations

No Java implementation, entity registration, input routing, command, or connected proof is delivered by this audit. The transport-carrier visibility behavior, mod-bus registration/render seams, server-thread/lifecycle policy, supported harness set, and exact shared integration points still require source inspection and later owner review. Keep the feature default-off; do not claim full SS14 parity.
