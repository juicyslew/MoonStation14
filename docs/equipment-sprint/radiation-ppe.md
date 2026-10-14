# Radiation PPE

The registered `suit_rad` and `hood_rad` are separate items using the existing
equipment-item prototype family, its resolved inheritance, ItemFootprints,
slot-fit policy, inventory equipment slots, and canonical armor damage path.
The suit reduces radiation by coefficient `0.01`; the separately equipped hood
reduces it by `0.65`. The existing ordered worn-slot consumer multiplies these
modifiers in sequence and excludes pocket/suit-storage slots. Wearing the suit
does not create or equip a hood.

Pinned SS14 source: `space-station-14` revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

* `Resources/Prototypes/Entities/Clothing/OuterClothing/suits.yml`:
  `ClothingOuterSuitRad` inherits `[ClothingOuterBaseLarge,
  GeigerCounterClothing, AllowSuitStorageClothing]`. It sets Radiation armor
  coefficient `0.01` and declares toggleable `ClothingHeadHatHoodRad` in head.
* `Entities/Clothing/OuterClothing/base_clothingouter.yml`:
  `ClothingOuterBaseLarge` inherits `ClothingOuterBase`; that inherits
  `Clothing`. The large base contributes Item size Huge, outerClothing slot,
  clothing speed modifiers and held speed modifier. `ClothingOuterBase`
  contributes Clothing outerClothing slot and Sprite state. `base_clothing.yml`
  defines `Clothing` inheriting BaseItem with Item size Normal, Sprite, Tag,
  StaticPrice and FlavorProfile.
* `Entities/Clothing/Head/hoods.yml`: `ClothingHeadHatHoodRad` inherits
  `ClothingHeadBase`; `Head/base_clothinghead.yml` defines that base inheriting
  `Clothing`. The hood supplies Radiation coefficient `0.65`. `ClothingHeadBase`
  adds HEAD slot, Sprite, Item size Small, Edible, SolutionContainerManager,
  and tags.

Minecraft adaptation: the supported local profile maps Item, Clothing slot and
region, suit storage, generic Armor, physical footprint, and the optional
`worn_model` rendering adaptation. The accepted R3 suit mesh is bound in the
resolved local `suit_rad` profile as WIDE; the shared authored wearable reload
loader and renderer consume it via `EquipmentRenderPlan`. The renderer currently
gates authored mesh rendering to WIDE body shape; slim rendering remains on the
existing placeholder path. SS14's Geiger,
step-trigger protection, toggleable container, head identity/breath mask and
hide-layer components are not available through this composition. No toggle
is claimed: hood is its own explicit wearable item. The hood has no accepted dedicated model; its placeholder is
explicitly temporary and needs new model art. No armor is added to unrelated
cap/coat items.

The flat inventory placeholders are direct `icon.png` copies from the pinned
SS14 `Resources/Textures/Clothing/OuterClothing/Suits/rad.rsi` and
`Resources/Textures/Clothing/Head/Hoods/rad.rsi`. Their RSI metadata attributes
the suit icon to tgstation commit
`4f6190e2895e09116663ef282d3ce1d8b35c032e`, with Vox state by Flareguy and
tail sprites by Tiniest Shark; the hood icon to tgstation commit
`6665eec76c98a4f3f89bebcd10b34b47dcc0b8ae`, with Vox state by Flareguy and
Vulpkanin source from Paradise commit
`f0fa4e1fd809482fbc104a310aa34cebf7df157d` resprited for SS14 by MilonPL.
Both RSI metadata files specify CC-BY-SA-3.0. These are flat UI placeholders
only, not the accepted volumetric suit or hood models.

Asset provenance for the next suit-model binding stage (external read-only
workspace): `C:/Users/William/Documents/Minecraft Mod Dev Files/Asset Building
Workspace/assets/Objects/Clothing/OuterClothing/candidates/surface_mapping_r3/`.
Use `export/manifest.json`, `export/binding_contract.json`, `ATTRIBUTION.md`,
and `approval.json`; the accepted `ClothingOuterSuitRad` export is
`export/resourcepack/assets/moonstation14/wearables/meshes/outerclothing/suit_rad.json`
plus its manifest-linked texture. The export contract targets Java 1.21.1 /
NeoForge and `AuthoredWearableMesh/Renderer`, uses body shape WIDE, and specifies
bone-local model-pixel coordinates divided by 16 by the loader. Do not
regenerate, edit, or re-export the accepted asset. Its source attribution is
SS14 `Clothing/OuterClothing/Suits/rad.rsi`, CC-BY-SA-3.0, taken from tgstation
commit `4f6190e2895e09116663ef282d3ce1d8b35c032e`, with Vox state by Flareguy
for SS14 and tail sprites by Tiniest Shark. The asset package is accepted but
not installed; mesh binding is a separate next stage.
