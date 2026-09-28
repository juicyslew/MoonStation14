# Device UI platform/session synchronization spike — proposal

**Status — 2026-09-27 (historical ordering):** proposal only; this is a deferred UI-first proposal, not the current next-sprint recommendation. The newer roadmap and [player/session–character lifecycle proposal](../player-character-lifecycle-sprint/proposal.md) prioritize lifecycle first and defer this UI platform spike to the next major technical investigation. This document requests owner review; it does **not** authorize implementation, change active sprint ownership, or add project-wide instructions. Atmospherics remains outside this proposal.

## Recommendation

**Historical recommendation (superseded):** this proposal previously recommended a **generic device UI/menu/session synchronization technical spike** as the next new sprint. If separately authorized as the next major technical investigation, do not begin a full ChemMaster or build a general-purpose UI framework. Use a harmless test device and normal authenticated Minecraft `ServerPlayer` menu transport to establish whether a reusable, server-authoritative device screen can safely open, exchange versioned requests/snapshots, and close across ordinary multiplayer lifecycle events.

This is the hardest uncertain foundation for a family of future tools: the repository has no local Screen/Menu infrastructure for device interactions, and menu ownership, lifecycle, request validation, client/server classloading, and stale-state behavior need proof before individual tool UIs multiply assumptions. A small real-transport spike can expose those risks without first committing to chemistry or gameplay semantics. It is **not** proof of body-owned action authorization: the authenticated player transport is intentionally distinct from active MobHarness action authorization, and this spike must not add or imply such a capability.

Player/session-character-body lifecycle is the current higher-priority work and should precede this deferred spike; see [the bounded Player Body Control closure](../player-body-control-sprint/closure.md) and the [current lifecycle proposal](../player-character-lifecycle-sprint/proposal.md). This UI proposal remains viable later, before ChemMaster or gameplay action menus, but its former “UI first, lifecycle after” order is historical and no longer current. Nothing here claims lifecycle is solved or approved for implementation.

## Motivation and source evidence

The target class of future features includes dedicated SS14-style, server-synchronized and selectively predicted device UIs, such as ChemMaster4000 buffer mixing and reactions. SS14 is a design reference only; no code or assets are to be copied.

Pinned reference revision: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- [`Resources/Prototypes/Entities/Structures/Machines/chem_master.yml`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Resources/Prototypes/Entities/Structures/Machines/chem_master.yml) defines prototype `ChemMaster`, its `UserInterface` component/key, input/output slots, and buffer. This shows the device is more than a standalone screen.
- [`Content.Shared/Chemistry/SharedChemMaster.cs`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Shared/Chemistry/SharedChemMaster.cs) provides shared interaction intents and state DTOs.
- [`Content.Server/Chemistry/EntitySystems/ChemMasterSystem.cs`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Server/Chemistry/EntitySystems/ChemMasterSystem.cs) owns server authority.
- [`Content.Client/Chemistry/UI/ChemMasterBoundUserInterface.cs`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Client/Chemistry/UI/ChemMasterBoundUserInterface.cs) and [`ChemMasterWindow.xaml`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Client/Chemistry/UI/ChemMasterWindow.xaml) / [`Content.Client/Chemistry/UI/ChemMasterWindow.xaml.cs`](https://github.com/space-wizards/space-station-14/blob/c9df5ef5d675b0d1d226828bddf6b78c28502d91/Content.Client/Chemistry/UI/ChemMasterWindow.xaml.cs) provide client presentation and binding.

These references motivate separating device state/authority from the presentation and menu session. They are not local APIs or implementation specifications. A simple counter/selection spike cannot validate chemistry, and **prediction of reagent state is not presumed safe**.

## Bounded stages

Each stage is conditional on owner authorization and passing its preceding stop gate. Stage 0 is investigation; it may conclude that the proposed APIs or scope need revision without any implementation.

### Stage 0 — verify the local platform contract

Inspect the pinned project NeoForge/Minecraft versions and verify the applicable `MenuType`, `MenuProvider`, server/client Screen registration, open-menu handshake, and menu/session lifecycle APIs against those exact versions. Document how the server binds an opening to an authenticated `ServerPlayer` and device, how protocol/menu mismatches are rejected, and how replayed or late requests are handled. Check client-only class isolation so ordinary dedicated-server startup never loads Screen/UI classes. Do not infer behavior from a different NeoForge version or start a framework before this audit.

**Gate:** written local API/lifecycle map with a feasible, version-appropriate test path and no server-side client classloading. If the handshake, mismatch behavior, or classloading boundary cannot be established, stop and return for owner review.

### Stage 1 — one harmless server-owned test device

Only after Stage 0 passes, implement one simple test device with a real Screen and normal server menu transport. Its only mutable value is a harmless server-owned counter or selection. Exchange bounded requests and a **versioned authoritative snapshot**; associate every request with the authenticated player and currently open menu/session identity. Never accept arbitrary client state, a client-supplied position as authority, or client-selected device identity detached from the server-opened menu. Keep mutable state authoritative on the server.

The test device and ordinary player menu session demonstrate transport and UI availability only. Do not describe this as proof of possessed-body access, active MobHarness controls, or actor/action authorization.

### Stage 2 — server validation and lifecycle

Validate each request on the server against the current authenticated player, open menu/session, device identity, interaction range, dimension, loaded device, and permissions. Safely invalidate/close the session when the device is removed or unloaded, the menu closes, the player disconnects, or the player changes/reopens context. Mismatched protocol or menu identity, invalid permissions, stale/late session requests, and invalid device context must reject without mutating state. Never load a device merely to satisfy a client request.

### Stage 3 — multiplayer and stale-state proof

Exercise two connected clients opening the same device. Verify monotonic server revisions, authoritative resynchronization, and rejection or safe handling of stale requests; one client must not overwrite newer state with an old snapshot. Under artificial latency if practical, optionally reconcile a **presentation-only** tab/selection choice. Such a choice may be predicted only as local UI presentation and must be replaced by server truth; no provisional chemical contents, reactions, output, items, or gameplay effects are permitted. Prediction remains optional and must not block a sound authoritative non-predicted spike.

### Stage 4 — conditional minimal extraction

Do not build a broad device framework up front. Extract only tiny reusable validation/state primitives if a **second distinct device or concrete second use** demonstrates that the same seam is needed. Otherwise keep the spike narrowly scoped and report what should be reused later. No speculative abstraction, generic action/menu catalog, or gameplay-action API is in scope.

## Validation and acceptance

Acceptance requires all of the following, with results recorded for owner review:

1. Dedicated-server GameTests prove the test device's server-owned state, revision progression, and rejection of malformed, stale, unauthorized, wrong-session, wrong-device, out-of-range, wrong-dimension, unloaded, and removed-device requests without unintended mutation. Use the project's real menu/session seams where the test harness permits; a helper-only unit test is not sufficient evidence of end-to-end transport.
2. A connected two-client owner smoke test uses normal authenticated `ServerPlayer` sessions and real Screens. It records open, valid update, close, stale request/revision handling, dimension/context change, device removal/unload as applicable, and disconnect/reopen cleanup. Confirm each client converges on authoritative state and a stale client cannot roll it back. A FakePlayer-only or other fake-player-only result is **not** acceptance.
3. Dedicated-server startup and tests demonstrate no server classloading of client Screen/UI classes; the normal transport player can access the test UI without any claim that their possessed body has device/action permission.
4. No pending Mind/legacy gates regress, and the spike neither enables nor retires any pending gates. Do not change unrelated body-control, movement, Mind, or legacy behavior to make this UI proof pass.
5. The result explicitly reports open API/version questions, untested cases, and whether a second use justified any extraction. A failed gate is a valid bounded outcome; it does not authorize scope expansion.

## Explicit non-goals and stop gates

- No full ChemMaster4000, reagents, mixing, reaction simulation, buffer chemistry, input/output item slots, hands, output/ejection behavior, or item transfer.
- No prediction of chemistry or other authoritative device state. Presentation-only selection/tab reconciliation is optional and never authority.
- No gameplay action menus, actor-capability system, body-owned actions/inventory, MobHarness action authorization, or claim that session transport means possessed-body access.
- No account/profile/character/body lifecycle implementation, round/lobby work, atmospherics work, broad UI framework, speculative shared framework, or changes to pending Mind/legacy gates.
- No edits to atmospherics; its active work belongs to its current owner and is not a next-sprint candidate here.
- No server trust in client-supplied state or position; no loading devices on demand from client requests; no client-side authority.

Stop for owner review if Stage 0 cannot establish the pinned-platform API contract, the connected lifecycle cannot be observed, any acceptance requires weakening server authority, the test depends only on fake players, client UI classes leak into dedicated-server loading, or unrelated Mind/legacy gates would need to change. Stage completion is evidence for a future scope decision, not automatic authorization for the next stage or ChemMaster implementation.

## Relationship to roadmap

This proposal's former UI-first order is historical and has been superseded by the newer owner-priority update in [the roadmap](../next-systems-roadmap.md): lifecycle first, UI platform spike deferred to the next major technical investigation. Retain this proposal as a potential later investigation before ChemMaster or gameplay action menus; it does not authorize implementation or override the lifecycle proposal.
