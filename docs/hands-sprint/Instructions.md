# Body-Owned Hands Sprint — Instructions

**Status: future plan only; not implementation approval.** This is the first dependent sprint in the [inventory program](../hands-inventory-program/Instructions.md). Its only deliverable is a reviewed contract and bounded server-side hands foundation. It does not implement grids, named wearable slots, a gameplay menu, or claim body-control lifecycle acceptance.

## Boundary and owner

The hands sprint owns the domain contract for hands belonging to the controlled body, active-hand selection, held-item identity/location, and a single-owner transfer API that later grid and wearable work must use. The body does not own general inventory or storage; grids belong to actual storage items. Coordinate with the body-control/lifecycle owner before touching lease or lifecycle code. Leave their docs/code and the deferred device-UI proposal untouched. No assets are expected.

Inspect local evidence first: `src/main/java/com/juicyslew/moonstation14/ms14/MS14Provider.java`, `MS14Bridges.java` (attachment/component data seam only); `src/main/java/com/juicyslew/moonstation14/component/codec/json/CharacterData.java` (currently no hands policy); `src/main/java/com/juicyslew/moonstation14/ms14/character/CharacterControlSystem.java`; `src/main/java/com/juicyslew/moonstation14/mixin/ServerPlayerStunActionMixin.java`; `docs/player-body-control-sprint/closure.md` (hotbar hidden pending body-owned hands); and `docs/player-character-lifecycle-sprint/closure.md` (lifecycle not accepted). Local evidence is not an inventory implementation.

## Milestones and gates

### H0 — authority and parity audit (no runtime edits until approved)

- Trace how the authenticated player resolves to the controlled body and current lease/epoch. Specify which server code is authoritative and what becomes invalid on lease/body changes. Audit stun/action gates, disconnect, ghost, respawn/death, body loss and reconnect boundaries without reopening their sprints. Apply the approved mode policy: actual Creative uses vanilla inventory; outside Creative an eligible body uses SS14-style hands/equipment/item-owned storage, with no intrinsic body storage or vanilla Survival inventory. Current committed harness authority requires a Spectator carrier, so Creative and committed body control are mutually exclusive. A mode switch must invalidate/end active authority safely before enabling the other mode; do not design same-session Creative body control.
- Compare targeted upstream `Content.Shared/Hands/Components/HandsComponent.cs` and `Content.Shared/Hands/EntitySystems/SharedHandsSystem.Whitelist.cs` at a pinned revision. Record actual upstream hand-count/whitelist/selection semantics separately from proposed lease validation and Minecraft-specific conservation.
- Apply the approved mode policy and define the handoff: pre-existing Creative vanilla items are account-owned and must be preserved/quarantined separately before eligible non-Creative body authority is enabled, then those exact items are restored only on actual return to Creative, including after body death and disconnect/reconnect. Never copy them into SS14 hands, equipment, or item containers; never drop them on character death or lose them. SS14 hands, equipment, and item-owned storage stay body-owned on Creative entry; never silently transfer them into vanilla inventory. Audit inventory menus and Creative packet/action paths. The policy is fixed, but the durable quarantine journal/crash-recovery design remains open and blocks activation until implemented and validated. If safe parking/restoration or authority invalidation/end cannot be established under NeoForge/Minecraft constraints, stop for owner review. Never allow vanilla Survival ownership or Creative actions to bypass body authority. **Pass:** written owner-reviewed contract; **fail/stop:** unsafe mode transition, ambiguous body authority, competing item owners, or required edits outside this sprint's boundary.

### H1 — body-owned hand state and validation

- Model a bounded hand set with stable hand identifiers, active hand, and at most one held item per hand. Enforce active-hand membership and explicit empty-hand semantics server-side. Do not assume all bodies share human hand capability; capability/eligibility policy is owner-approved.
- All requests resolve actor and body server-side and verify current lease, alive/usable state, action permission (including stun), request/session/revision, and operation-specific constraints before mutation. Reject unauthorized, stale, malformed, duplicate/replayed and mismatched-body requests without partial change.
- Establish canonical item location and a mutation API with validate/commit/rollback behavior. Define stack split/merge and immutable/copy handling so cached or copied ItemStacks cannot be inserted in two locations. Bound count, request rate, and per-request work. A hand's state must save/load and synchronize without making the player's vanilla hotbar an alternate owner during eligible non-Creative body control. Vanilla inventory remains the authority in actual Creative; the audit must verify separate no-loss parking/restoration of pre-existing vanilla contents and safe mode handoff. The committed Spectator-carrier harness and Creative mode are mutually exclusive. The decided mode policy is not a feasibility claim; unsafe mechanics are a stop gate.
- Do not show or use a hotbar as if it were these hands. UI presentation waits for the separate UI gate.
- **Pass:** pure tests and server-side integration tests prove one-item-one-location for hand/world, eligible-body vanilla disablement, any approved Creative boundary, lease rejection, save/load, transaction rollback, and lifecycle cleanup; other bodies retain existing behavior. Exercise Creative entry/exit, survival↔creative transitions, hotbar/vanilla slots, inventory-menu and creative packet/action routes for no loss, duplication, or Survival authority bypass. **Fail:** any stale request mutates, item duplicates/disappears on a rejected/failed move, or a vanilla path silently creates a second owner.

### H2 — hand contract handoff

- Publish the stable hand/active-item query and atomic transfer contract for grid/wearables consumers. Include limits, revision semantics, lifecycle notifications and failure outcomes. Avoid a second inventory API in later sprints.
- **Pass:** owner accepts contract; focused tests cover both hands (where supported), active hand switch, held item, invalid lease, stale/replay, rollback, drop, death/body-loss policy, save/load and 20-player bounded-cost design. **Fail:** unresolved lifecycle/carrier policy or an implicit UI dependency. Grid sprint remains blocked until pass.

## Required implementation-stage test matrix (future work)

| Area | Cases / pass condition |
|---|---|
| Authority | wrong actor/body, expired or replaced lease, non-capable body, stunned actor and invalid state reject with no mutation |
| Hands | active selection, empty hand, occupied hand, swap/transfer rules, invalid hand identifiers |
| Conservation/carrier boundary | hand ↔ world; eligible-body vanilla inventory disablement; decided Creative/non-Creative boundary and transitions across vanilla slots, hotbar, inventory-menu and creative packet/action paths; pre-existing account-owned Creative contents quarantined separately and restored exactly only on actual Creative return, without copying into SS14 holdings; split, merge, copied stack reference, failed destination and injected commit failure preserve one owner and quantities, with no vanilla bypass during eligible non-Creative body control |
| Persistence/lifecycle | save/load, disconnect/reconnect, ghost/lease change, body death/drop and body removal follow the recorded policy without loss/duplication; quarantined vanilla items remain account-owned through body death and reconnect and are restored only on actual Creative return, never dropped on character death |
| Abuse/scale | malformed and replayed requests, size/count/rate bounds; no per-tick full scan; measure representative 20-player workload |

Use unit tests for pure rules plus targeted dedicated-server GameTests for registered authority/lifecycle seams. Run future `gradlew.bat test --no-daemon`, `gradlew.bat compileGametestJava --no-daemon`, and targeted GameTests after implementation. Fake players do not establish connected acceptance. UI and authenticated two-client acceptance are separately gated; do not add menu code here.

## Risks and open decisions

Risks: vanilla hotbar/container/creative actions and mode transitions may bypass isolation; safe no-loss parking/restoration or authority handoff may not be possible with platform constraints. Durable quarantine journaling and crash recovery across playerdata, reconnect, death, and clone state are unresolved and block activation; do not claim an implementation. The approved Creative/non-Creative policy is fixed, including preserving account-owned Creative contents through body death and reconnect, but unsafe mechanics are a stop condition, not permission to weaken it. Minecraft ItemStack copy/split semantics can break conservation; controller leases can change mid-request; save timing can orphan items; possessed mobs may not have a meaningful hand capability. Return to owner before coding if any needs broader inventory/lifecycle changes. Remaining questions and upstream parity deltas are tracked in the umbrella.

## References

Upstream targets: `Content.Shared/Hands/Components/HandsComponent.cs`; `Content.Shared/Hands/EntitySystems/SharedHandsSystem.Whitelist.cs`. Inspect source and tests at a pinned revision before asserting exact parity. Body/lease checks and cross-carrier transactional guarantees are planned local requirements/enhancements, not claimed upstream behavior.
