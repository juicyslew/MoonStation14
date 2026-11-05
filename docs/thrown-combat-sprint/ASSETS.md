# Basic armor asset import and runtime binding

Imported the owner-authorized frozen delivery for `thrown-combat-basic-armor-wearables-20261103-r1`, digest `8146d749484b087e1d929f7d5edc267430bc3f6bf92c205e6f553010bb29c55f` (snapshot `70a36c34de0afec848d5ebf633c1f76f580e7c22bcbc185dcff8e9f907fb55ec`). Owner receipt: `build/thrown-combat-sprint/armor-owner-import-receipt.json`; import plan: `build/thrown-combat-sprint/armor-authorized-plan.json`. Import was six new files, each planned with prior hash null. Approved source bytes remain unchanged.

## Runtime bindings and material

Resolved `basic_helmet` now binds `moonstation14:wearables/meshes/hats/helmet_basic.json`, and resolved `armor_vest` binds `moonstation14:wearables/meshes/outerclothing/armor_basic.json`; both are WIDE models. Existing prototype parents, armor values, slot/region, and inherited composition remain unchanged. The existing `AuthoredWearableMesh` parser honors the helmet mesh's explicit whole-model `entityTranslucent` route; `AuthoredWearableRenderer` dispatches it as translucent while normal wearable meshes remain cutout. Whole helmet is drawn once (not shell plus visor), using its authored white tint and PNG alpha without a second opacity multiplier. This follows `build/thrown-combat-sprint/armor-review/5-binding_contract.json`. GPU/NeoForge appearance, sorting and lighting remain unverified.

## Attribution / source pin

Source pin: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Helmet is a CC-BY-SA-3.0 adaptation of Hats R2 `Clothing/Head/Helmets/security.rsi`; copyright: Made by DieselMohawk for use in SS14. Vest is exact reuse of owner-accepted OuterClothing R3 artwork, source `Clothing/OuterClothing/Armor/security.rsi`, CC-BY-SA-3.0; original copyright metadata retained. License: https://creativecommons.org/licenses/by-sa/3.0/ . Per-file/source notes are retained in frozen delivery `build/thrown-combat-sprint/armor-review/7-ATTRIBUTION.md` and `6-per_file_provenance.json`.

Vest's original copyright notice: **Made by DieselMohawk for use in SS14.**
The helmet adaptation removes lower-front armor/cheek separators, trims the side
shell, adds a chinstrap and wrap visor, and reduces glass alpha from 163 to 48
while retaining RGB. The existing roof, brow and rear construction are retained.
These changes and the reconstructed meshes remain under CC-BY-SA-3.0. The owner
approved this delivered revision and separately authorized its exact-digest import.

## Imported runtime file SHA-256

| Runtime path (under `src/main/resources/assets/moonstation14/`) | SHA-256 |
|---|---|
| `textures/entity/equipment/hats/helmet_basic.png` | `2255621ed4c6714ff97cc1c8950dd3d1ea45adfa502dda2fcc8138ff7ea7f9f5` |
| `textures/entity/equipment/outerclothing/armor_basic.png` | `a9752c1cb210cd96b8363b7fd3ffcd9cca43dbd02be6d31aaaf3c8c3350bdadd` |
| `wearables/meshes/hats/helmet_basic.json` | `7736775ff9a9faee23fc107a3a793f7354ef3a9a147a8959469aeb4f9621876b` |
| `wearables/meshes/hats/helmet_basic_shell.json` | `0310a2056414cc6f7e1b911cf3abab5759497e5452bb87c577cdf66ff7e44be8` |
| `wearables/meshes/hats/helmet_basic_visor.json` | `afedf60c5b7b0756cf847d7aa4da558e75debf17905f78b3b70561e705ae91a0` |
| `wearables/meshes/outerclothing/armor_basic.json` | `dd0153d8ce94b886f6adefc2f196a3a7e785c86d42ed46a39cffcc4ec49c28c1` |
