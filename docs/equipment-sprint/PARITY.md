# Equipment prototype composition parity

Verified against pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`Resources/Prototypes/InventoryTemplates/human_inventory_template.yml` supplies
slot order, display labels, UI coordinates/groups, flags and dependencies;
`Content.Shared/Inventory/InventoryTemplatePrototype.cs` (`SlotDefinition`)
supplies defaults (`Default`, `PREVENTEQUIP`, strip time 4). Stripping is out of
scope and no inert stripping component is represented. Local inventory template
records preserve UI metadata and dependency/blacklist policy; strip time is omitted.
Slot flag and supported tag vocabularies are typed. `PREVENTEQUIP` is the
upstream omitted-flags default only; the human's concrete flags replace it.
The only presently represented equipment tag is `PetOnly` for the verified back
slot blacklist.

`BaseItem` → `Clothing` resolves the normal Item size, but abstract Clothing does
not compose a Clothing component. Concrete starter clothing therefore declares
its own slot flags. The simple local candidates are intentionally not concrete
SS14 ports: `grey_cap` is upright; `grey_jumpsuit` has no SuitSensor; `simple_coat`
is not a labcoat and has no armor behavior; `earpieces` are cosmetic;
`blank_id_card` has no access/ID behavior; `utility_case` is inert; and
`black_bandana` has no ingestion blocking. Per the owner's revised composition,
`simple_coat` does not carry AllowSuitStorage and cannot enable suit storage.
Qualifying outerwear remains supported through the optional presence-only local
component; upstream's component also supports a whitelist, which this starter
projection deliberately does not model. This does not imply that upstream suit
storage is EVA-only. No Radio, Access,
Armor, step-trigger immunity, or unsupported component is claimed. Shoes do not
grant blanket slip immunity.

`ClothingComponent.QuickEquip` defaults to true; the local typed Clothing
composition resolves `quick_equip` with that default. Z uses the shared in-hand
action route. Pinned `Content.Shared/Clothing/EntitySystems/ClothingSystem.cs`
lines 34–78 establishes template-order selection and occupied-slot replacement:
only an existing Clothing component with QuickEquip false blocks replacement on
that basis. Non-clothing pocket contents can be displaced too. Local immediate
starter transfers preserve whole displaced stacks and dependent contents; these
candidates do not introduce equip do-afters or item-specific equip sounds.

The nine Default-group slots occupy the compact bottom-left clothing panel;
the six hotbar slots remain alongside hands. The owner-requested opposite toggle
arrow is a deliberate presentation adaptation: pinned InventoryGui and
InventoryUIController use one static `Slots/toggle` texture upstream. The same
32×32 texture is mirrored by reversing its U coordinates on a positively wound
quad (rather than reflecting geometry, which can be culled by Minecraft's GUI
pipeline); no second upstream or authored icon asset is introduced. Clicking an
occupied clothing slot with an empty active hand unequips to that hand as a local
pointer adaptation, reusing the existing authoritative slot-action request; the
back/belt click-to-open behavior is unchanged.

`bag` and `belt` receive only size/slot appearance metadata here; their native
registry entries and accepted 7x4/8x2 grids remain the existing storage system's
responsibility and are not replaced by this catalog.

The optional local `EquipmentItem.worn_model` is a Minecraft rendering adaptation,
not an upstream SS14 prototype field or component. Eleven starter items reference
authored meshes; `blank_id_card` and `utility_case` intentionally have no mesh.
The references flow through resolved typed equipment prototypes into the live
render plan. Authored rendering currently supports WIDE bodies; missing meshes and
SLIM bodies deliberately use procedural geometry. No SS14 parity is claimed for
these assets.

Pocket admission falls back to the existing resolved `item_footprint` catalog only
when neither EquipmentItem nor MedicalItem composition supplies size. Physical
`item_size` is optional and independent from storage-grid `cells`; geometry never
implies physical size. Explicit Small mappings are present only for the verified
local `screwdriver`, `wirecutter`, `multitool`, `crowbar`, and
`atmosphere_analyzer` profiles. `airlock_screwdriver` inherits the local
`screwdriver` profile, so it inherits that mapping. Pinned SS14 evidence at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`Resources/Prototypes/Entities/Objects/base_item.yml` (`BaseItem` Item size
Small); `Objects/Tools/tools.yml` (`Screwdriver`, `Wirecutter`, `Multitool`);
`Objects/Tools/crowbars.yml` (`Crowbar` -> `BaseCrowbar` -> `BaseItem`, with
Small declared by `BaseCrowbar`); and `Objects/Specific/atmos.yml`
(`GasAnalyzer` -> `BaseItem`). Local `item_footprint/airlock_screwdriver.json`
inherits `screwdriver`. Other ordinary items without an explicit resolved
physical size are denied pocket admission; this is intentionally not a claim of
full SS14 item-catalog parity. Medical stack maxima remain MedicalItem-owned and
are intersected with native stack limits.
