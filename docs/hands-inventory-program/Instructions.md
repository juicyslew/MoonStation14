# Body-Owned Hands and Inventory Program — Planning Instructions

**Status: plan only.** This document and its three sprint plans define a bounded order of work; they do not report implementation, approve a concrete architecture, or authorize changes outside an explicitly owner-approved sprint. The owner authorized this planning umbrella only. Recommend fidelity to SS14 behavior, with every deliberate exception documented and reviewed.

## Boundary and ordering

The controlled body owns only its hands (including active-hand selection) and wearable equipment slots. Storage is owned by the actual storage item—such as a bag, belt, or garment with pockets—and grid contents are not intrinsic body inventory. The eventual transfer ledger describes where items are (including hands, equipment, a storage item, world, or an explicitly supported vanilla carrier), not an additional body-owned storage container. It is not a generic UI framework, a character lifecycle rewrite, an item/prototype import project, or a replacement for unrelated device menus.

Work in this strict dependency order:

1. **[Hands sprint](../hands-sprint/Instructions.md):** settle a body/lease-authoritative hand contract and a single-owner item ledger. No grid or wearables dependency.
2. **[Grid storage sprint](../grid-storage-sprint/Instructions.md):** pure, deterministic grid/footprint/admission core; consume the hands contract but do not build a UI.
3. **[Wearables sprint](../wearables-sprint/Instructions.md):** named slots and belt/bag storage integration, then plan end-to-end transfers across all holders.

Menu transport/UI and connected multiplayer acceptance are **separately gated**. Do not infer permission to begin the deferred device-UI proposal, or to add a new generic menu framework, from these plans. A UI/connected stage requires a separate owner decision and must use its own acceptance criteria.

## Current evidence and non-goals

- Local `src/main/java/com/juicyslew/moonstation14/ms14/MS14Provider.java` and `MS14Bridges.java` are data attachment/component access and integration abstractions. They are not an inventory, item-location registry, or transfer transaction system. Do not claim they already provide inventory semantics.
- `src/main/java/com/juicyslew/moonstation14/component/codec/json/CharacterData.java` and its strict audit/codec path currently define character policy data; its own comments leave hands unmodeled. `src/main/java/com/juicyslew/moonstation14/ms14/character/CharacterControlSystem.java` and `src/main/java/com/juicyslew/moonstation14/mixin/ServerPlayerStunActionMixin.java` are relevant action/eligibility seams, not an inventory authority.
- `docs/player-body-control-sprint/closure.md` says the hotbar stays hidden pending genuine body-owned hands and that full gameplay/action/lifecycle acceptance remains unfulfilled. `docs/player-character-lifecycle-sprint/closure.md` records an unaccepted lifecycle handoff. Neither is evidence that inventory exists or that connected acceptance passed.
- The hands/inventory sprint remains **incomplete**: current capability and authority seams are planning/foundation work, not an accepted inventory implementation. No live item transfer has been implemented or accepted.
- `docs/device-ui-platform-sprint/proposal.md` is a deferred, separately owned UI-first proposal. This program must not edit or supersede it.
- Platform for any later implementation is Java 21, Minecraft 1.21.1, NeoForge 21.1.224 (`gradle.properties`). There are no notable assets expected or requested; do not add or import assets as part of these plans.

## Cross-sprint invariants (required design and test contracts)

1. **Authority:** server validates the authenticated actor, current controlled body, current body-control lease/epoch, permissions, holder identity, range/context where relevant, and current revision before any mutation. Never trust client-supplied body, slot, item, footprint, orientation, or destination as authority. Per owner decision, either lifecycle- or experimental-authority CHARACTER bodies may be eligible when their bound prototype declares the required capability; do not infer capability from entity type.
2. **One location, conserved identity:** every live item has exactly one authoritative owner/location among hand, equipment slot, a cell in an item-owned storage container, world/entity, or explicitly supported vanilla carrier. Define how ItemStack copies, splits, merges, creative actions, death drops, cloning, save/load, and stale references cannot duplicate or lose items. A vanilla carrier bridge is not a second independent owner. “Inventory of a body” means only a location/transfer ledger for items accessible to or associated with that body; it does not make grid storage body-owned. Vanilla inventory is used when the player is actually in Creative. Outside Creative, an eligible body uses SS14-style hands, equipment, and item-owned storage: no intrinsic body storage and no vanilla Survival inventory. These authorities are mutually exclusive, never simultaneous.
3. **Atomic movement:** validate source and destination first, commit as one logical operation, or roll back. Failed moves preserve the original item and state. State the limits of atomicity across Minecraft inventory/world persistence boundaries; do not claim global conservation without tests covering the boundary.
4. **Bounded admission:** explicit item-size/footprint limits, cell counts, storage nesting depth, stack quantities, request rate, and work per request. Reject cycles, self-containment, invalid masks, out-of-bounds/overlap, and unsupported oversized items. No recursive unbounded search.
5. **Lifecycle:** define save/load and recovery for held/equipped/contained items, and behavior on stun, ghosting, lease transfer, disconnect/reconnect, death, body loss, and drop/cleanup. No vanilla hotbar leakage while vanilla inventory is disabled for eligible bodies.
6. **Stale-state defense:** server-side revisions/session identity; reject stale or replayed operations without mutation and return/queue an authoritative resync. UI details remain deferred, but the data protocol cannot assume a future client is trustworthy.
7. **Bounded scale:** target 20 concurrent players. Keep per-tick work constant or explicitly bounded; inventory validation and transfer should be event-driven, not a full-world scan or per-tick attachment rewrite. Measure later implementation at representative 20-player inventory loads.

## Parallel ownership and hard boundaries

Each child sprint owns only its listed model/core/integration. The hands sprint owns the shared location/transfer contract; grid and wearables must consume it, not create alternate item owners. Existing lifecycle/body-control, status/action, assets, device UI, and other sprint instruction documents remain owned elsewhere and must not be modified by this program. If an integration requires their changes, stop and request owner coordination. No child is accepted by documentation or unit tests alone where its gate requires server integration.

## Resolved policy and remaining decisions for the owner

Resolved for this plan:

- approved mode policy: when the player is actually in Creative, they use vanilla inventory; when not Creative and controlling an eligible body, they use SS14-style hands, equipment, and item-owned storage only. A committed current harness uses a Spectator carrier, so Creative and committed body control are mutually exclusive; do not imply same-session Creative body control. Existing Creative vanilla items are account-owned: preserve/quarantine them separately while controlling an eligible non-Creative body, and restore those exact items only on actual return to Creative, including after body death and disconnect/reconnect. Never copy them into SS14 hands, equipment, or item containers; never drop them on character death or lose them. On a mode switch, invalidate/end active body authority safely before enabling the other mode. SS14 hands, equipment, and item-owned storage remain body-owned on Creative entry; never silently transfer them to vanilla inventory. Audit all slots/hotbar and menu/action/packet routes. If safe no-loss parking/restoration or authority handoff is mechanically unsafe, stop for owner review rather than weakening this decision. This is an approved policy, not a claim of implementation feasibility;
- bodies own hands and wearable equipment slots only. Bags, belts, and garments own their storage; usable garment pockets require the appropriate garment to be worn. Define a server-authoritative removal policy for a garment with contents: preferably contents move with the garment when safe, otherwise reject removal while nonempty. Never implicitly spill contents or transfer them to a body pocket;
- SS14's human template has `pocket1`/`pocket2` dependent on `jumpsuit` and `suitstorage` dependent on `outerClothing` plus `AllowSuitStorage`; named `belt` and `back` slots are equipment slots. This is a dependent-slot/access model and does not establish that pocket contents are physically stored in the jumpsuit. The planned garment-backed container/ownership model is a deliberate design requirement to validate, not an upstream implementation claim.
- owner-selected body policy: any currently controlled CHARACTER body admitted by either lifecycle authority or the experimental harness may be eligible, only when its bound character prototype declares hand capability. The human prototype declares hands; pig omits them and gains none implicitly. Capability is prototype data, not inferred from carrier/entity type. H5 supplies a read-only lifecycle-body snapshot accessor; H6 provides unified lifecycle/experimental actor resolution and revalidation. These seams do not implement inventory actions, commits, or transfers;
- whether SS14 irregular storage/container cell masks and orientation are in initial scope (recommended: implement a bounded mask-capable core, but defer irregular prototypes unless approved);
- the engineering design for a durable quarantine journal, its persistence owner, and crash recovery/reconciliation with playerdata/clone state; this remains unresolved and blocks activation (the account-ownership, preserve-and-restore policy above is settled);
- which storage nesting, dimensions, item footprints, stack limits, and drop/death policies are supported;
- whether the future UI/connected stage is approved and which authenticated two-client acceptance is required.

Record remaining decisions and any exceptions before coding; unresolved safety or ownership questions are stop conditions. Do not reopen the resolved Creative/non-Creative inventory policy without owner review; engineering feasibility remains a gate, not a reason to make the decision tentative. In particular, the durable quarantine journal and crash-recovery design must be implemented and validated before activation; this plan does not claim that mechanism exists.

The current implementation and verification boundary is recorded in [the current sprint handoff](current-handoff.md). The owner-directed follow-up after this hands/inventory sprint is complete is recorded in [the deferred ghost playability handoff](future-ghost-playability-handoff.md). It is not part of this sprint and does not change the separate ownership of existing lifecycle or ghost/body-control documentation.

## Program gates and validation

Each child document defines its milestone pass/fail gates and test matrix. Sequence is a hard gate: hands contract accepted before grid core; grid core accepted before wearable storage integration; only then propose end-to-end transfers. Automated unit/GameTests and dedicated-server tests are implementation-stage requirements, but **do not run or claim them for this docs-only task**. Connected acceptance is a later, separately gated stage and cannot be inferred from `FakePlayer` coverage.

For future implementation (not run here), required baseline commands include `gradlew.bat test --no-daemon`, `gradlew.bat compileGametestJava --no-daemon`, and targeted dedicated-server GameTests for the changed hand/grid/wearable/transfer contracts. Add connected two-client acceptance only after separate UI/connected authorization. For this plan-only change, validate with `git diff --check` and `Test-Path` checks for all four instruction files; no gameplay/manual testing is requested.

## References and parity status

These are targeted upstream SS14 source paths to inspect against a pinned upstream revision during the relevant implementation audit; they are not local files and this plan does not claim that every proposed feature exactly matches upstream:

- Hands: `Content.Shared/Hands/Components/HandsComponent.cs`; `Content.Shared/Hands/EntitySystems/SharedHandsSystem.Whitelist.cs`.
- Inventory/equipment: `Content.Shared/Inventory/InventoryComponent.cs`; `Content.Shared/Inventory/InventoryTemplatePrototype.cs`; `Content.Shared/Inventory/InventorySystem.Equip.cs`.
- Storage/items/sizes: `Content.Shared/Storage/StorageComponent.cs`; `Content.Shared/Storage/EntitySystems/SharedStorageSystem.cs`; `Content.Shared/Item/ItemComponent.cs`; `Content.Shared/Item/ItemSizePrototype.cs`; `Resources/Prototypes/item_size.yml`; `Resources/Prototypes/Entities/Clothing/Back/backpacks.yml`; `Resources/Prototypes/InventoryTemplates/human_inventory_template.yml`.

Parity baseline to investigate: hands and whitelist rules; declared named inventory slots/templates and equip constraints; storage admission/size behavior. Planned enhancements or environment-specific decisions include body/lease-bound authority integrated with this project's controller model, explicit single-location conservation across Minecraft carriers and persistence, transactional rollback, revisions/stale request rejection, strict limits against cycles/nesting/abuse, and potentially generalized irregular masks/orientation. Do not describe these as upstream parity unless source behavior is verified; document divergences with an owner-approved rationale.
