# Sprint 10d continuation: grid storage and actual hand UI

**Status: G0–G4 implementation complete; G5 connected acceptance not performed.** The user retired the custom one-cell pouch on Oct 1, 2026. There is no registered pouch item or playable pouch UI/client action; internally named `PouchContents` / `pouch_contents` remains only for historical/legacy bag and belt contents. Pouch is excluded from any popup quota. Pouch art was not imported because there is no corresponding standalone SS14 item. Earlier pouch descriptions in this continuation are historical and superseded on this point. No live connected acceptance, old-save pouch migration, or new test result is asserted here. The owner-closed 10c/10d slice and this separately named continuation otherwise remain as recorded in [the current handoff](current-handoff.md) and [the original 10d plan](sprint-10d-equipment-ui.md). This status records implementation scope and existing automated evidence, not connected acceptance, exhaustive packet/transfer proof, crash durability, or full SS14 parity.

## Audited SS14 baseline

Audit target: `C:\Users\William\Documents\SS14-dev\space-station-14`, revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- `Resources/Prototypes/Entities/Clothing/Back/backpacks.yml` declares the ordinary `ClothingBackpack` storage grid as `0,0,6,3` (7 by 4 cells) and `maxItemSize: Huge`.
- `Resources/Prototypes/Entities/Clothing/Belt/base_clothingbelt.yml` declares the storage base grid `0,0,7,1` (8 by 2 cells) and `maxItemSize: Normal`. These are prototype-specific shapes, not universal bag/belt dimensions.
- `Resources/Prototypes/item_size.yml` gives the default item masks Tiny `0,0,0,0` (1x1), Small `0,0,0,1` (1x2), Normal `0,0,1,1` (2x2), Large `0,0,3,1` (4x2), Huge `0,0,3,3` (4x4), and Ginormous `0,0,5,5` (6x6). These are default shapes; per-item shapes may differ.
- `Content.Shared/Storage/EntitySystems/SharedStorageSystem.cs` validates maximum size using the size prototype and rejects a stored item whose storage's maximum size is greater than or equal to the destination's maximum size. Thus nesting is constrained by *relative maximum item size*, not a blanket prohibition. Grid occupancy/admission also checks location and shape. The current mod's blanket pouch/bag/belt exclusion is its own existing policy, not evidence of SS14's rule.
- Upstream's storage shapes are coordinate masks and item placement includes rotation; the cited item-size defaults are masks, not merely scalar width/height labels. This proposal intentionally limits the initial implementation to regular rectangular footprints and four cardinal orientations.

These are audit observations at the pinned revision, not a promise to copy all SS14 rules. The ordinary bag and belt dimensions above are relevant evidence for the proposed slice only.

## Owner decisions and remaining details

1. **Storage grids:** ordinary bag 7x4; belt 8x2, matching the audited ordinary prototypes. Existing item-owned storage ownership stays; no body-owned grid or new carrier/menu authority.
2. **Item footprints:** regular and irregular shapes are approved for the initial implementation, with unknown/non-mapped vanilla items defaulting to 1x1. This is an explicit deviation from SS14's default size-based behavior. Exact item-to-shape mapping is unresolved; pickaxe is a reference-audit item. Do not infer shape silently from unrelated vanilla size/components or claim mapping parity.
3. **Rotation/masks:** support 0/90/180/270 degree rotations. Bound each container shape/mask and validate nonempty/in-bounds/nonoverlapping placements. Four orientations can be redundant for symmetric shapes; canonical placement should avoid duplicate states.
4. **Nesting:** no nested storage in this first slice, as an explicit deliberately narrower rule than SS14's relative-size rule. Reject cycles/self-containment regardless.
5. **Limits and stacks:** bag 7x4, belt 8x2; at most 64 permitted cells and 32 distinct entries. These are independent of per-item maximum stack count. SS14's observed 5/10/30/50/100 values are audited observations only; this mod currently uses native Minecraft maximum stack sizes. No arbitrary byte-size bound is implied.
6. **Legacy reader (implementation detail):** the implementation includes a read-only legacy bag/belt virtual adapter and writes grid state on successful authorized writes. Filled legacy-save preservation or migration is not an owner acceptance condition, manual-test prerequisite, or blocker. Earlier “pouch remains one-cell” statements are historical and superseded; there is no current playable pouch item or UI.
7. **Authority and UI:** server-revisioned, authenticated transfers with actor/body/lease revalidation; client receives a bounded snapshot and sends intents only. Actual hand/grid UI is approved through snapshot-only presentation; no vanilla container menu or client authority.

The choices above are approved in principle. Precise shape mapping and data/rollback evidence are stop conditions for any mutation; connected acceptance is not authorized by this approval.

## Ordered milestones and gates

### G0 — Owner decisions (design only)

Owner approved the direction and continuation without milestone pauses. Initial mappings and data/rollback behavior are implemented within the explicit limits below; unknown items remain 1x1 and unsafe/ambiguous states fail closed. This does not establish complete Minecraft item mapping or crash-safe durable transactions.

### G1 — Pure deterministic grid kernel

Implemented: bounded `GridGeometry` (8x8 maximum / 64 cells, 32 entries), regular and irregular masks, cardinal rotation, including the irregular T pickaxe and cardinal pickaxe footprints, placement/bounds/overlap validation and deterministic operations. Earlier one-cell pouch notes are historical; the custom pouch is retired. Focused kernel tests cover geometry and rejection behavior. This is not a claim that every possible malformed or adversarial input has exhaustive coverage.

### G2 — Item-owned codec and legacy migration

Implemented: registered `GRID_CONTENTS` value codec/state on the storage item, read-only legacy bag/belt virtual adapter, and grid writes as part of successful writes. Unknown item footprints default to 1x1; there is no current pouch item. SS14's per-type 5/10/30/50/100 stack maxima are audited observations only; this mod uses native Minecraft maximum stack sizes. Codec decode lists are bounded to 32 entries before decoding. A byte budget remains optional hardening; no crash-safe transaction across player/world save boundaries is claimed.

### G3 — Authenticated server transfers

Implemented worn bag/belt grid transfers using existing hand/item authority, exact tokens and revision checks, with authenticated network intents. World pickup/drop preserve grid contents; read-side duplicate-token state is rejected. The authenticated request service has no connected evidence; the honest evidence is the GameTest seam. Tests cover implemented paths but do not exhaust every packet, rollback, substitution, or ambiguous-recovery scenario. Stop on `RECOVERY_REQUIRED` or unclear ownership; no global conservation or sudden-crash claim.

### G4 — Snapshot and actual hand UI (separate gate)

Implemented client-only I-screen hand panels and grid overlay, selection/rotation/store/take controls, and authoritative summaries/snapshots. Client sends intents rather than authoritative placements. There is no drag-and-drop of real slots. Automated tests do not constitute a connected grid UI test or two-client acceptance; client-only presentation is not connected movement acceptance.

### G5 — Connected acceptance (separate gate)

Not performed. Owner acceptance will use a fresh world and nonpersistent server, with updates made while the server is down; no old filled saves need verification. Manual checklist: bag 7x4, belt 8x2; place and take a rotated pickaxe; place two items; the former one-cell pouch check is historical and not applicable (the custom pouch is retired); confirm items are available in Creative. Drop/relog are optional behavior checks, not prerequisites. Stop immediately on `RECOVERY_REQUIRED` or ambiguous state. Authenticated request service has no connected evidence; GameTest is the honest test seam. Legacy migration hardening, crash durability, and 20-player tests are outside this slice.

## Boundaries retained

This approval does not edit/reopen the accepted one-cell work, approve full SS14 parity, import upstream item prototypes, add garment pockets, GUI drag-and-drop, nested bags, generic menu framework, or claim storage durability or 20-player scale. The owner will test in a fresh world on a nonpersistent server; old filled-save compatibility is not required. Legacy migration hardening, crash durability, and 20-player tests are outside this slice.
