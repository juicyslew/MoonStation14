# Equipment architecture

## Composition and mutation

`ms14/equipment/` defines resolved `inventory_template` and `equipment_item`
prototype families. The character's `EquipmentSlots` component can bind a template;
`HandCapability.resolveEquipmentSlots` supplies its ordered runtime slots.
The human template contains all 15 standard slots. Item composition separates
size, clothing flags/appearance, and optional suit-storage permission.

`EquipmentFitPolicy` performs admission against the resolved template and item
composition. `BodyEquipmentTransfer` is the shared transfer consumer;
`LiveHands` stages immutable ownership changes, including dependent-slot removal.
`LiveHandsCommitGateway` remains the only gameplay attachment commit seam.
Dependent drops reuse `BodyHandItemTransfer`'s root-placement/spawn path.
Backpack/belt grids remain item-owned; pockets are body inventory slots, not new
garment-owned containers.

Owner `BodyHandStateSnapshot` includes the template ID and declared equipment.
Generic `EQUIP_SLOT`/`UNEQUIP_SLOT` intents encode the slot in `sourceHostId`.
Legacy back/belt intents delegate to the shared equipment consumer.

Z uses `QUICK_EQUIP`: the server selects the first fitting template slot,
respecting resolved `Clothing.quick_equip` and returning displaced clothing to
the freed hand. `MOVE_EQUIPMENT` relocates worn contents between compatible empty
slots without borrowing a hand. Body-grid transfers use
`GRID_BODY_TO_EQUIPMENT` / `GRID_EQUIPMENT_TO_BODY`; world-grid transfers extend
the existing `BodyWorldCrossAction` with `WORLD_TO_EQUIPMENT` /
`EQUIPMENT_TO_WORLD` rather than creating another world ownership service.
All drag requests retain settled source identities and revisions.

Dependency removal is shared among manual unequip, quick replacement, and drag
transfers. `BodyEquipmentTransfer.commitCandidate` and `DropStage` reuse the root
drop and compensation path. World transfers retain the existing source-first,
conditionally published two-owner transaction. Pure `LiveHands.restoreContents`
restores exact prior contents with a monotonically newer revision only after the
caller proves the staged body and world identities; it does not publish or grant
authority itself.

## HUD

`EquipmentPanelLayout`, `EquipmentPanelState`, and `EquipmentPanelRenderer`
integrate through `BodyHandClient` and `StoragePopupScreen`, not a second workspace
manager. The nine Default clothing slots retain their SS14 bottom-left
composition and share the wearable hotbar cell size selected by `HandBarLayout`.
The 18px hands remain centered with 3px spacing; all hotbar gaps are 3px. ID,
back and belt remain in left-side order; pockets are immediately right of the
hands and suit storage follows both pockets at the far right. Collapse hides clothing only. Storage gestures retain
existing host/grid services and use canonical slot rectangles on either surface.

## Public presentation

`BodyHandNetworking` registers the `moonstation14:body_appearance` play-to-client
payload. `BodyAppearanceSnapshot` is a public, presentation-only projection; item
stacks use the shared `hands.network.ItemPresentation` sanitizer. It contains no
owner proof, tokens, action sequence, reservations, or private grid contents.

Renderer consumers should query
`BodyHandNetworking.clientCache().get(dimension, entityUuid, entityId)` and treat
the returned `BodyAppearanceSnapshot` as display-only. The cache rejects stale
revisions and conflicting entity-id incarnations, and removes matching entries
on client entity leave, level unload, dimension change, and disconnect. It is
separate from the authoritative owner `BodyHandStateSnapshot` cache.

The projection is consumed by the character's ordinary entity layers only. The
controlled body's own world pass remains suppressed in its first-person camera;
no replacement first-person body, arm, or held-item pass is installed. The
existing GhostControl CHARACTER binding requires the carrier player to be a
spectator, whose vanilla `ItemInHandRenderer.renderHandsWithItems` path does not
draw carrier hands; ordinary non-spectator player hand rendering is unaffected.

The server sends snapshots on entity tracking and after compatible committed
gateway writes, to trackers and the exact current body controller. Gateway batch
publication is deferred to the final state; owner proof is published separately.

## Authored worn models

Starter garments use authored meshes under `assets/moonstation14/wearables/meshes/`
and textures under `assets/moonstation14/textures/entity/equipment/`. Resolved
typed equipment data carries an optional worn-model resource and supported body
shape through the shared render plan. `AuthoredWearableMeshes` parses resources on
client reload and atomically replaces an immutable cache. The renderer emits
bone-local geometry on vanilla player bones with white tint, authored UVs, and
cutout/no-cull rendering. Positions are converted from model pixels to block units
once during parsing. An authored garment replaces, rather than overlays, its
procedural placeholder; unsupported shape or missing mesh uses that fallback.
Reload listener registration is wired explicitly on `MoonStation14Client`'s mod
event bus, so resource-backed geometry does not depend on automatic subscriber
bus inference. This local worn-model field is a Minecraft adaptation, not an SS14
component.
