# Botany product worn-appearance commission

This bounded commission is imported after independent review and user authorization (“Authorize plants and wearables”). Its 20 files (10 mesh/PNG pairs) provide seven appearances; all hashes match and the 35-asset R2 freeze is unchanged. The request ID remains lineage, not an open blocker. SS14 source audit pinned at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Import does not constitute owner acceptance or visual/playback validation.

## Commissioned identities and source evidence

These are seven **upstream SS14 source IDs** (one visual each), bound to the existing `HEAD` wearable region; they are not local runtime item IDs. Local runtime items are `ambrosia_deus`, `ambrosia_vulgaris`, `lily`, `clothing_head_hat_watermelon`, `clothing_head_hat_holy_watermelon`, `trash_banana_peel`, and `trash_mimana_peel`. Bind through the existing canonical equipment item paths.

| Source item ID | Declared wearable source and worn state | Source |
|---|---|---|
| `FoodAmbrosiaDeus` | `Clothing` declares `slots: [HEAD]`, RSI `Objects/Specific/Hydroponics/ambrosia_deus.rsi`; inherited default `equipped-HELMET`, four-direction state | `Entities/Objects/Consumable/Food/produce.yml`; v1 RSI 32×32, `equipped-HELMET` directions 4 |
| `FoodAmbrosiaVulgaris` | `Clothing` declares `slots: [HEAD]`, RSI `Objects/Specific/Hydroponics/ambrosia_vulgaris.rsi`; inherited default `equipped-HELMET`, four directions | `Entities/Objects/Consumable/Food/produce.yml`; v1 RSI 32×32, `equipped-HELMET` directions 4 |
| `FoodLily` | Parent `FoodPoppy` declares `Clothing` with `slots: [HEAD]`; Lily overrides Sprite to `Objects/Specific/Hydroponics/lily.rsi`; inherited default `equipped-HELMET` | `Entities/Objects/Consumable/Food/produce.yml`; RSI metadata must be captured/hashed with delivery |
| `ClothingHeadHatWatermelon` | Parent `ClothingHeadBase` supplies `Clothing`/`HEAD`; item declares melon RSI `Clothing/Head/Hats/melon.rsi`; default `equipped-HELMET`, four directions | `Entities/Clothing/Head/hats.yml`; v1 RSI 32×32 |
| `ClothingHeadHatHolyWatermelon` | Parent `ClothingHeadBase` supplies `Clothing`/`HEAD`; item declares `Clothing/Head/Hats/holyhatmelon.rsi`; default `equipped-HELMET`, four directions with four authored frames at 0.5s each | `Entities/Clothing/Head/hats.yml`; v1 RSI 32×32 |
| `TrashBananaPeel` | Declares `Clothing` with `slots: [HEAD]`, `equippedPrefix: peel`; RSI `Objects/Specific/Hydroponics/banana.rsi`; worn state `peel-equipped-HELMET`, four directions | `Entities/Objects/Consumable/Food/produce.yml`; v1 RSI 32×32 |
| `TrashMimanaPeel` | Inherits `TrashBananaPeel` HEAD slot; overrides `Clothing` RSI to `mimana.rsi`, `equippedPrefix: peel`; state `peel-equipped-HELMET`, four directions | Same prototype file; v1 RSI 32×32 |

Audit exclusions: `CarvedPumpkin` is a `BaseItem` with Sprite/Item/Construction only, not Clothing; do not commission a worn appearance. It has carved source icon state, not worn geometry. The name `ClothingHeadBase` is an abstract parent, not a separate asset. `TrashBananaPeelExplosive` and baked-peel variants are not in this request. Ambrosia, lily, and peel source RSI item sprites are not themselves worn geometry; use the specific equipped state as visual evidence and create a distinct fitted model, not an icon extrusion. Lily qualifies through its actual `FoodPoppy` parent Clothing component, unlike CarvedPumpkin.

## Deliverables and boundary

For each commissioned identity provide source-first editable `.bbmodel` equipment master, native supported authored wearable mesh export and required texture dependencies, exact prototype-ID→resource binding with region `HEAD`, complete same-ID manifest/dependencies, source RSI snapshots and SHA-256 hashes, verbatim source license/copyright attribution, original-first gameplay-scale source/model previews, and independent review evidence. Preserve each equipped state’s directional/frame/cadence evidence; holy-watermelon worn animation is four frames at 0.5 seconds per frame. Do not invent body anatomy, special effects, physics, armor, or gameplay. No equipment/armor mechanics or runtime integration are requested.

Follow the accepted headwear workflow/contract: equipment-only geometry, no mannequin in exports; standard head-bone-local coordinates, +Y down model pixels with the parser’s single `/16` conversion, no repeated authoring pivot. Seven prototype `worn_model` references are implemented, HEAD-only. Holy-watermelon uses four ordered mesh frames at 0.5 seconds, reusing SingularityFrameTiming/world presentation time. Attribution metadata is complete at `assets/moonstation14/provenance/botany_imports`; independent pinned-source audit verified exact license/copyright fields. Independent code reviews pass; the consumer lookup/emission missing-frame fallback test is added and fixed, with focused final closure validation still running. GPU/native bake, client-camera/alignment and playback acceptance are not claimed.

Sources: `MODEL_HANDOFF.md` “Every purpose-made SS14 hat / headwear”; `assets/Objects/Clothing/Hats/collection/README.md`, `catalog.json`, and `binding_contract.json`; source prototype files/RSI metadata cited above. Existing wearable collection remains untouched.
