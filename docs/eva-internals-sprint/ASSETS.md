# EVA / internals resources

Source reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Per-resource attribution files are authoritative for license and copyright; do not infer one license for the entire set.

## Inventory and worn gear

Native IDs: `eva_helmet`, `eva_suit`, `breathing_mask`, `oxygen_tank`, and `emergency_oxygen_tank`.

- SS14 inventory icons: `assets/moonstation14/textures/item/<id>.png`, with adjacent `.attribution.txt` records. Native item models are in `models/item/`.
- Worn meshes: `assets/moonstation14/wearables/meshes/{eva_helmet,eva_suit,breathing_mask,breathing_mask_lowered,oxygen_tank,emergency_oxygen_tank}.json`.
- Worn textures: matching names under `textures/entity/equipment/`, with adjacent attribution.

The six worn geometry/texture pairs are the owner-accepted, canonically imported source-faithful modeler family (not the former placeholder artwork). Request `eva-wearable-replacement-20261102-r1`, revision `eva-source-faithful-worn-family-r1`, snapshot `9fe64e31c9b933154d87efe61c7d20c6c3e04271a34a0465d66c5a97d0b8ac38`, delivery digest `0bb515a8f6e9e041566359d163b4f245110c8438b4f88e0fc9f689fe6d8cec7e`. The six mesh JSON and six worn PNG runtime files are hash-verified by the import receipt at `build/eva-internals/asset-import/post-import-sha256.json`; item/inventory icons and gameplay remain unchanged. Textures are source-faithful 3D atlas adaptations, not copied SS14 pixel data. Per-file source copyright and CC-BY-SA-3.0 provenance are in each adjacent texture attribution and delivery `source/manifest.json` / `ATTRIBUTION.md`.

The accepted source package, its eight editable authoring projects, and provenance remain in the separate canonical Asset Building Workspace at `assets/Objects/Clothing/EVA/candidates/source_family_r1/`; they are not duplicated in this repository's docs. The accepted art was reviewed using saved CPU renders; no Minecraft/NeoForge GPU installation or visual review is claimed. Native slot/layer, lighting, performance, and untested pose/species fit remain outstanding. The meshes use the existing authored wearable renderer; raised/lowered masks are alternative states selected through resolved breath-tool composition and public appearance state.

Source artwork includes `Clothing/Head/Helmets/eva.rsi`, `Clothing/OuterClothing/Suits/eva.rsi`, `Clothing/Mask/breath.rsi`, and the corresponding tank RSI resources identified in the item attribution files. Preserve their notices when changing derivatives.

## Actions and sound

Icons live in `textures/interface/actions/`: `internals_off`, `internals_on`, `internals_low`, `mask_off`, and `mask_on`. They are sourced from pinned SS14 resources with adjacent provenance. The mask-off blocked icon carries **CC-BY-NC-SA-3.0**; retain that non-commercial restriction.

Connect/disconnect audio is `sounds/internals.ogg`, registered through the existing sound owner. Its adjacent attribution records the source. Playback must follow accepted authoritative state changes, not duplicate client/server effects or loop continuously.

## Verification boundary

Resource parsing, bounds/UV checks and package provenance do not establish native GPU fit or audible playback. Inspect third-person gear, lowered-mask position, worn tanks and action states in the owner runtime after technical readiness. No model/texture backups or binaries belong in these docs.
