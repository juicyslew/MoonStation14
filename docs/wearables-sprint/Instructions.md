# Wearables Sprint — Instructions

**Status: future plan only; not implementation approval.** This sprint follows the accepted hands and pure grid-storage contracts. It covers named equipment slots and wearable-held storage (notably belts and bags) and specifies eventual end-to-end hand/equip/store transfers. It does not authorize UI implementation or connected acceptance.

## Boundary, ownership, and upstream parity

Own named equipment slot definitions/templates, equip/unequip constraints, and adapters making approved wearable items provide storage through the shared grid core. The body owns the equipment slots, not general inventory or storage: a bag, belt, or garment owns its own container and contents. Do not duplicate the item-location ledger, hands API, storage geometry or transaction semantics. Do not modify the lifecycle/body-control owner's work, other sprint instruction docs, assets, or deferred device-UI proposal. No notable assets expected.

Prerequisites: owner-accepted [body-owned hands contract](../hands-sprint/Instructions.md) and [grid core/storage adapter](../grid-storage-sprint/Instructions.md), with unresolved risks closed. Local `MS14Provider`/`MS14Bridges` are only attachment/component seams. The local `CharacterData` codec currently does not model hands; character slot policy changes are not implied by this plan. Platform is Java 21, Minecraft 1.21.1, NeoForge 21.1.224 (`gradle.properties`).

Inspect pinned SS14 paths `Content.Shared/Inventory/InventoryComponent.cs`, `Content.Shared/Inventory/InventoryTemplatePrototype.cs`, `Content.Shared/Inventory/InventorySystem.Equip.cs`, `Resources/Prototypes/InventoryTemplates/human_inventory_template.yml`, and `Resources/Prototypes/Entities/Clothing/Back/backpacks.yml`, plus storage/item references in the grid plan. Verify exact named slots/templates, equip rules, and which back items supply storage; distinguish that verified parity from planned lease authority, Minecraft carrier bridging and atomic cross-holder conservation. The verified human template uses `pocket1`/`pocket2` dependent on `jumpsuit` and `suitstorage` dependent on `outerClothing` plus `AllowSuitStorage`; `belt` and `back` are named equipment slots. This is a dependency/access model, not evidence that pocket contents physically live in the jumpsuit. Here, usable pockets require the appropriate worn garment and the planned pocket container is owned by that garment.

## Contract

- Slots are server-declared, named, unique identifiers with explicit capacity and equip rules. Template selection is capability/body-policy-based, not guessed from entity type. Owner must decide whether any possessed mob body gets slots; no implied universal human template.
- Every item remains in exactly one location across hand, named equipment slot, a cell in an item-owned grid, world, or an owner-approved vanilla carrier. Wearing a belt/bag/garment activates access to its storage but does not copy, mirror, or make the body own contents. Pockets are unavailable unless the appropriate garment is worn. Removing a garment with nonempty pockets should move the contents with that garment when safe; otherwise reject removal while nonempty. Never implicitly spill contents or move them to a body-owned pocket. Dropping/transferring a bag or garment preserves contents as contents of that item under the same single-owner rule.
- Equip/unequip validates actor, current body/lease, action status, slot capability, item whitelist/size, occupancy, source and destination revisions, and relevant context on the server. Rejected requests do not clear a hand or slot. A failed cross-container transaction rolls back.
- Specify occupied-slot swap/replace policy, slot-conflict policy, allowed clothing layer/category rules, restrictions when contained items exceed new context, and behavior if a wearer dies, ghosts, disconnects, changes lease, or loses the body. Never silently delete or spill contents. Stun prevents prohibited actions using the existing action policy; do not rewrite it here.
- Storage access is through the accepted grid API and bounded by its max nesting/capacity. Reject self/ancestor insertion and cycles. If storage within storage is not approved, explicitly reject rather than partially support it.
- Apply the resolved umbrella policy: actual Creative uses vanilla inventory; outside Creative, an eligible body uses only hands, equipment, and item-owned storage, with no intrinsic body grid or vanilla Survival inventory. Unaffected bodies retain vanilla behavior. The committed harness requires a Spectator carrier, so Creative and committed body control are mutually exclusive. Audit every vanilla slot/hotbar, menu, packet/action route, and mode transition; determine no-loss handling for pre-existing vanilla contents rather than assuming they are empty or transitions harmless. Keep vanilla contents parked separately and restore those exact contents on return to Creative; never merge/copy them into body holdings. Body-owned holdings remain body-owned on Creative entry. The policy is settled, not conditional on feasibility: if safe parking/restoration, authority invalidation, or handoff cannot be established, stop for engineering/owner review rather than weakening the policy. No implementation safety is claimed here.

## Milestones and gates

### W0 — parity and integration design

Audit the upstream paths above and inventory all local Minecraft inventory/carrier routes that could create or move player items, including container, creative, death/drop, clone/respawn and save paths. Trace Creative entry/exit and Survival↔Creative transitions through vanilla slots/hotbar, inventory menus and creative packets/actions. Inventory pre-existing contents; do not assume slots are empty or transitions harmless. Coordinate with but do not edit other owners. Apply the settled vanilla mode boundary and obtain remaining decisions for mob eligibility, slots/template policy, belts/bags, stacking, nesting and death/drop. **Pass:** accepted matrix maps each upstream behavior to parity, approved exception, or out-of-scope, and proves a safe no-loss isolation/handoff for pre-existing contents and transitions under the mutually exclusive Creative/Spectator-harness modes; **Fail/stop:** unaccounted carrier, unsafe or unproven handoff, or unresolved owner boundary. The policy itself is not awaiting an owner decision.

### W1 — named slot model

Define bounded slot identifiers/template, equip eligibility and server-owned occupancy state. Integrate with canonical location/transaction API. Validate saved data against current template, and define safe recovery for unknown/removed slots without item loss. **Pass:** tests for empty/occupied/incompatible slots, wrong body/lease, stale requests, save/load and failed move all preserve one owner. **Fail:** slot identity depends on client input or invalid saved state destroys items.

### W2 — wearable storage integration

Connect approved belt/bag/garment types to the accepted grid kernel, item footprint and admission policy. Opening/access is not UI scope; server access checks and storage ownership are. Pockets are usable only while the required garment is worn. Define garment/bag equip, removal while nonempty (contents move with the item if safe, otherwise reject removal), transfer, invalid/deleted prototype, dimension change and nested-storage behavior. **Pass:** item and contents remain uniquely owned on equip, unequip, drop, save/load and rollback; pocket access is gated by worn garment; bounded limits hold. **Fail:** contents are copied to wearer, spill implicitly, become inaccessible without a recovery policy, or are duplicated between wearable and grid owner.

### W3 — end-to-end transfer contract and automation

Specify and test operation sequences: world → hand → named slot; hand → belt/bag/garment pocket cell; storage → hand; hand ↔ slot; garment pocket access denied without required worn garment; garment/bag removal with stored item (contents move with item safely or removal rejects); failed/stale transfer; death/drop path. Each is a server transaction with revisions, validated body lease and single-owner conservation. **Pass:** targeted dedicated-server GameTests prove each approved path, rejection/rollback and persistence; 20-player bounds are measured/reviewed. This gate is automated system acceptance only, not connected-player/UI acceptance.

### W4 — separately gated UI and connected acceptance

No automatic progression. Return to owner for separate authorization covering UI/menu/session transport and authenticated two-client test design. Test stale menu/session, wrong body, disconnect/reopen, body/lease transition, dimension/context change, item movement by competing actor, and authoritative resync. Do not claim menu access means body action permission. A fake-player-only test cannot pass connected acceptance.

## Test matrix (implementation stage)

| Area | Minimum cases |
|---|---|
| Slots | template per approved body capability; valid/invalid equip; occupied slot; conflict/swap rule; unknown slot on load |
| Authority | wrong actor/body, changed lease, stun/action denial, stale/replayed request, unauthorized storage access |
| Wearable storage | allowed belt/bag/garment grid; admission/footprint/rotation follows grid policy; no usable pockets without required worn garment; garment/bag removal with nonempty storage moves contents safely or rejects removal; capacity and nesting limits |
| Conservation/lifecycle | all W3 transfers; failed commit rollback; stack split/merge; death/drop, body loss, disconnect/reconnect, save/load and vanilla/Creative carrier transitions without Survival bypass or item loss/duplication |
| Load/security | malformed client fields, duplicate requests, oversized payloads, rate limits, max legal nested/holder state and representative 20-player cost |

For future implementation run `gradlew.bat test --no-daemon`, `gradlew.bat compileGametestJava --no-daemon`, and targeted dedicated-server GameTests. Connected UI acceptance is separately gated and requires real authenticated clients. No manual gameplay test is part of this docs-only plan.

## Risks and open decisions

Slot templates may diverge from available body capabilities; carrier integration can fork ownership; garment/bag deletion or death behavior can orphan nested items; safe parking/restoration and mode-transition isolation may be unsafe or infeasible under platform constraints; saved templates may change between versions. Pre-existing vanilla contents and every transition path require audit and tests, not assumptions. The Creative/non-Creative policy is decided; stop for engineering/owner review if safe mechanics cannot satisfy it, rather than treating the policy as conditional. Keep storage item-owned and pockets garment-gated. Recommend SS14 fidelity where feasible and document every exception; no upstream parity is certified by this plan.

## References

Targeted upstream references: `Content.Shared/Inventory/InventoryComponent.cs`; `Content.Shared/Inventory/InventoryTemplatePrototype.cs`; `Content.Shared/Inventory/InventorySystem.Equip.cs`; `Content.Shared/Storage/StorageComponent.cs`; `Content.Shared/Storage/EntitySystems/SharedStorageSystem.cs`; `Content.Shared/Item/ItemComponent.cs`; `Content.Shared/Item/ItemSizePrototype.cs`; `Resources/Prototypes/item_size.yml`; `Resources/Prototypes/InventoryTemplates/human_inventory_template.yml`; `Resources/Prototypes/Entities/Clothing/Back/backpacks.yml`. Review exact source at a pinned revision. Program boundaries and parity caveats are in the [umbrella instructions](../hands-inventory-program/Instructions.md).
