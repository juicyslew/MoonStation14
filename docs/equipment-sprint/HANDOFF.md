# Equipment foundation handoff

## STATUS

Closed with owner approval on 2026-10-08. Changes remain uncommitted; closure
does not authorize staging, committing, or pushing.

Implemented the resolved human 15-slot inventory, representative native items,
Z quick-equip, clothing clicks and drag/drop, authoritative public appearance,
and eleven authored wide/Steve wearable meshes. First-person body/arm/item
viewmodels were explicitly removed from scope. Clothing and held items render
on the actual character in F5 and to observers.

## ARCHITECTURE

- `inventory_template` and `equipment_item` are typed, inherited prototype
  families. `HandCapability.resolveEquipmentSlots` resolves the character's
  template; live gameplay and HUD consume those declarations.
- `LiveHands` owns hands and equipment together. Backpack/belt grids remain
  item-owned; pockets are inventory slots, including supported whole medical
  stacks. Existing storage implementations and contents are preserved.
- `EquipmentFitPolicy` supplies shared fit/dependency rules.
  `BodyEquipmentTransfer` handles manual equip/unequip, quick replacement and
  equipment relocation. All attachment writes use `LiveHandsCommitGateway`.
- Generic intents include `EQUIP_SLOT`, `UNEQUIP_SLOT`, `QUICK_EQUIP`, and
  `MOVE_EQUIPMENT`. Body-grid transfers use `GRID_BODY_TO_EQUIPMENT` and
  `GRID_EQUIPMENT_TO_BODY`. World transfers extend `BodyWorldCrossAction` with
  `WORLD_TO_EQUIPMENT` and `EQUIPMENT_TO_WORLD`, retaining its existing conditional
  two-owner publication/compensation paths. No client-side action chains or
  temporary hand borrowing are needed for grid/equipment transfers.
- Source identities, revisions, authority epochs and grid origins are retained
  in requests. Shared dependent-slot removal and `commitCandidate`/`DropStage`
  conserve displaced items. `restoreContents` restores exact prior contents
  with a newer revision only after the caller proves ownership identities.
  These are staged, compensated transactions, not crash-durable atomic storage.
- Public `BodyAppearanceSnapshot` is separate from owner inventory proof.
  `ItemPresentation` sanitizes stacks; public appearance exposes no private grid
  contents, action credentials or pocket contents. Cache lifecycle handles
  tracking, entity incarnations, disconnects and world changes.
- `EquipmentRenderPlan` consumes that public projection and resolved clothing.
  Ordinary character layers render held items and worn clothing. Authored mesh
  resources are parsed on client reload into an immutable cache, registered
  explicitly through `MoonStation14Client`'s mod event bus.

## IMPORTANT FILES

Paths below are relative to `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/equipment/`: prototype data/merge/catalogs and `EquipmentFitPolicy`.
- `ms14/equipment/client/`: render plan, authored mesh parser/cache/emitter,
  clothing/item layers, procedural fallback, panel layout/renderer/state.
- `ms14/hands/live/`: `LiveHands`, equipment/body-grid transfer consumers,
  `LiveHandsCommitGateway`; world cross publication is in `hands/network/`.
- `ms14/hands/client/`: `BodyHandClient`, `HandEquipmentGesture`,
  `PopupActionPlan`, `StoragePopupScreen`, `HandBarLayout`.
- `ms14/hands/network/`: owner intents/snapshots and public appearance
  projection, publication and client cache.
- `ms14/player_body_control/lifecycle/character/client/PlayerCharacterHarnessRenderer.java`:
  retained F5/observer layers and local first-person body suppression.

Resources: `data/moonstation14/moonstation14/{inventory_template,equipment_item}/`;
`assets/moonstation14/wearables/meshes/`, `wearables/obj/`, and
`textures/entity/equipment/`.

Documentation indexes: [ARCHITECTURE](ARCHITECTURE.md), [PARITY](PARITY.md),
[ASSETS](ASSETS.md), [MODELLER](MODELLER.md), [MANUAL](MANUAL.md),
[VALIDATION](VALIDATION.md), [KNOWN-ISSUES](KNOWN-ISSUES.md), and
`authored-wearables/import-manifest.json` plus copied source contracts/credits.
The prior inventory baseline is `../hands-inventory-program/HANDOFF.md`.

## SS14 CORRESPONDENCE

Reference revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Human template slot flags/order/groups/dependencies and generic Clothing
composition were verified. Relevant sources include
`Resources/Prototypes/InventoryTemplates/human_inventory_template.yml`,
`Content.Shared/Inventory/InventorySystem.Equip.cs`,
`Content.Shared/Clothing/EntitySystems/ClothingSystem.cs`, and
`Content.Client/Clothing/ClientClothingSystem.cs`.

Z selects fitting slots in resolved template order. Incoming Clothing must
allow quick-equip; an old Clothing occupant with quick-equip false prevents
replacement on that basis. Non-clothing occupants can be displaced too.
Pockets/ID depend on jumpsuit; suit storage requires qualifying outer clothing
with `AllowSuitStorage`. Explicit physical size, not grid footprint geometry,
governs pocket admission; unaudited ordinary sizes are denied.

Starters are simple local counterparts, not complete SS14 concrete-item ports.
No radio, access/ID, suit sensors, armor, ingestion blocking or blanket shoe slip
immunity is implied. Authored 3D `worn_model` metadata is a Minecraft adaptation
of resolved worn visuals, not an upstream component.

## DECISIONS / WHY

- Nine clothing slots occupy the collapsible bottom-left panel. Clothing matches
  wearable hotbar tile size (normally 15px) with 1px gaps. Original 18px hands
  remain centered, with uniform 3px hotbar gaps. ID/back/belt are left of hands;
  pockets and then suit storage are right. All hit/drag paths use canonical
  rectangles; front managed windows win input.
- Clicking occupied clothing takes it into the empty active hand; a full active
  hand does nothing. This is an owner-requested UI adaptation. Dragging remains
  available. Backpack/belt clicks retain their storage-opening behavior.
- SS14 has one static `Slots/toggle.png`, not a separate hide icon. The local
  hide appearance reverses its UVs on positively wound geometry; it is a mirrored
  adaptation, not another upstream asset.
- `simple_coat` no longer composes suit-storage permission, by owner request.
  Neither it nor a jumpsuit enables suit storage. Generic qualifying outerwear
  remains supported; no coat-ID exception or EVA-only category was hardcoded.
- Existing asset-workspace exports were copied, not regenerated. Source:
  `C:\Users\William\Documents\Minecraft Mod Dev Files\Asset Building Workspace\assets\Objects\Clothing\Wearables`.
  Eleven authored items include clothing, bag and belt. Mesh/PNG/OBJ copies are
  hash-preserved; copied MTL texture paths were remapped and documented. Retain
  attribution and import manifests.
- Authored geometry is already bone-local: divide coordinates by 16 once during
  parsing, then apply the native bone pose once. Never reapply canonical pivots.
  Use painted PNGs with white tint and cutout/no-cull rendering. Render all
  exported bones once, including coat leg hems, without layering placeholders
  over authored meshes. Do not import the illustrative walking clip as gameplay.
- Authored fit is wide/Steve only. Slim uses whole-garment procedural fallback;
  blank ID and utility case also remain procedural. Inventory/held assets are
  independent and unchanged.
- No first-person replacement pass remains. The controlled CHARACTER uses a
  spectator carrier; vanilla does not draw its hands. F5/observer rendering stays
  independent of carrier inventory.

## KNOWN GAPS

Final authored fit, animation, UVs/lighting and the latest hide-icon/click pixels
were not validated by launching a client. Owner closure is not a claim of
automated graphical acceptance. Slim authored assets and other body sizes remain
future work. Stripping, functional ID/access and richer item gameplay are deferred.

Historical full native suites were not green. Pulling physics remains unresolved;
other power/atmosphere/physiology failures must not be presumed inherited or
equipment-caused merely from matching names. See the validation/failure records.

## VALIDATION

Latest serial command: `./gradlew --no-parallel compileJava compileTestJava
compileGameTestJava test jar` passed: **2,858 unit tests**, no failures.
Log: `build/validation-logs/equipment-toggle-click-coat.log`.
JAR: `build/libs/moonstation14-0.0.1.jar`; SHA-256
`D20EC7F1760B66E4102D94C8475BB426EE7AA5C2A37A1116F1A44A28970448A4`.
Focused coverage includes prototype resolution, mesh parsing, cache replacement,
production pose/vertex emission, shared dependencies and click-source proof.
Scoped source reviews passed.

Completed focused native run: 195/701 selected, 193 passed, two failed. The
world-equipment fixture subsequently passed 1/1 selected from 701 after correcting
fixture reach and rollback setup; round-trip and failed-publication conservation
are asserted. Pulling physics remained failed. Full runs before final repairs
were 678/698 and 676/700, not final full-suite-green evidence. Later client/prototype
corrections were compile/unit validated; native fixture updates were compiled,
not rerun. Failure artifacts/logs remain retained where useful.

## NEXT WORK

Use `MANUAL.md` for connected F5/observer and latest HUD checks. For future assets,
start with copied binding contracts and import hashes; preserve source exports
and attribution. Add explicit authored slim fit only when supplied and validated.
Keep stripping and access/airlock work separate; reuse the current inventory and
appearance services rather than adding competing ownership/render pipelines.
