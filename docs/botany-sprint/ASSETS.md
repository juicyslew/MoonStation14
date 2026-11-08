# Botany source sprite bundle

Imported from SS14 checkout `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (`Resources/Textures`); packaged source files and each RSI's upstream `meta.json` are retained byte-for-byte under `src/main/resources/assets/moonstation14/textures/ss14/botany/`. This is attributed source material, not a claim of local renderer integration or visual acceptance. Do not store runtime resources only in the repository-root `assets/` directory: Gradle packages from `src/main/resources`.

## Plant state keys

| visualKey | Imported RSI | Declared stages | empty | harvestable | dead |
|---|---|---:|---|---|---|
| wheat | `Objects/Specific/Hydroponics/wheat.rsi` | 6 | shared tray empty (no plant sprite) | `harvest` | `dead` |
| tomato | `Objects/Specific/Hydroponics/tomato.rsi` | 6 | shared tray empty | `harvest` | `dead` |
| potato | `Objects/Specific/Hydroponics/potato.rsi` | 4 | shared tray empty | `harvest` | `dead` |
| poppy | `Objects/Specific/Hydroponics/poppy.rsi` | 3 | shared tray empty | `harvest` | `dead` |
| aloe | `Objects/Specific/Hydroponics/aloe.rsi` | 5 | shared tray empty | `harvest` | `dead` |
| meatwheat | `Objects/Specific/Hydroponics/meatwheat.rsi` | 6 | shared tray empty | `harvest` | `dead` |

Stage logical keys `stage-1` through the declared stage count map to identically named RSI states. `SeedProfile.visualKey` is metadata only; no models, geometry, collision, growth behavior, or per-item switches are defined here. The packet base and paper layer are `Objects/Specific/Hydroponics/seeds.rsi` states `seed` and `paper`; combine with the species seed states using the existing bitmap image transformer when item IDs/runtime wiring are ready. Shared tray sprite family `Structures/Hydroponics/containers.rsi` includes `hydrotray`, `hydrotray2`, and `hydrotray3`; upstream leaf appearance determines which is used. Hoe, spade, grinder, and seed-extractor RSI families were also copied with their untouched metadata.

## Attribution and license

All copied families declare `CC-BY-SA-3.0` in their original metadata. Preserve those per-file metadata notices. Exact source/copyright records are in each RSI's `meta.json`: plant families cite vgstation commits (`1dbcf389b0ec6b2c51b002df5fef8dd1519f8068`) or tgstation (`40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a`) and name individual contributors; seed packet art cites vgstation and Ian321; tools cite Aurorastation commit `3160508c1a9f367be0ab054cceaf2e36c0b66250`; tray containers cite tgstation `c6e3401f2e7e1e55c57060cdf956a98ef1fefc24`; grinder cites tgstation `59f2a4e10e5ba36033c9734ddebfbbdc6157472d`; extractor cites tgstation at `c6e3401f2e7e1e55c57060cdf956a98ef1fefc24` and modified-by notice. Refer to metadata for complete individual attributions.

This bundle is source art only. No existing production consumer was verified, no preview or renderer integration has been done, and modeler delivery and independent visual review remain pending. Do not treat sprite states as geometry or infer hitboxes/origins/attachments.

## Meatwheat single-family approved source import

Source checkout `C:/Users/William/Documents/SS14-dev/space-station-14`, pinned at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Imported source frames and unchanged metadata: `src/main/resources/assets/moonstation14/textures/ss14/botany/Objects/Specific/Hydroponics/meatwheat.rsi/`; stage/runtime PNGs are exact copies at `textures/block/botany/plants/meatwheat/`, and source-state item copies at `textures/item/botany/`. State resources map one-to-one; the two-layer Minecraft crossed-plane model is a presentation adaptation of SS14's 2D upright sprite, not commissioned 3D plant geometry. Model elements cross at canonical local center `(8,*,8)`; root at soil height is represented by block-local Y=0. Item mappings: `meatwheat_seeds` uses generic packet seed, meatwheat seed, blank paper label layers in that order; `meatwheat_bushel` uses source `produce`. Attribution/license is verbatim in the retained source `meta.json`: CC-BY-SA-3.0, “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068 and https://github.com/tgstation/tgstation/commit/ead6d8d59753ef033efdfad17f337df268038ff3 and modified by slarticodefast, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce sprites created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”. Seed packet-layer metadata remains at the corresponding retained `seeds.rsi/meta.json`. Exact source hashes are recorded in the parent-requested ignored preview manifest `build/asset-preview/meatwheat/manifest.json`. Imported resources and mapping are not GPU/owner visual acceptance.

## Planned flat item models

Vanilla generated item models are provided for the five produce IDs, five seed packet IDs, and hoe/spade IDs under `src/main/resources/assets/moonstation14/models/item/`. Their small PNG source copies under `textures/item/botany/` are unchanged SS14 RSI frames; source attribution/license remains in the corresponding imported RSI `meta.json`. Produce selects each plant's `produce` state; tools select `icon`. Packet models layer generic packet `seed`, species `seed`, then base `paper` label. The label is the unmodified blank `paper` state (no per-seed label text is fabricated); runtime advanced labels remain outside this resource-only change. Texture file copies are SHA-256 identical to the referenced imported frames. No item registrations, profile IDs, growth scaling, blocks, machine item models, or renderer integration are changed. The models are 2D item icons, not plant meshes or final visual acceptance.

## Delivered botany model candidates (resource-only)

The candidate delivery is installed at the exact paths in `collection/export/manifest.json` from the BasicBotany source workspace. It supplies one hydroponics tray and 34 static plant-state models (wheat 6, tomato 6, potato 4, poppy 3, aloe 5; each also harvest/dead) plus their native textures. `src/main/resources/assets/moonstation14/botany/plant-models.json` is the existing runtime manifest and now maps those 34 states to the delivered models; no second renderer or model registry was introduced. Empty remains tray-only. Plant roots are already block-local at `(8,5,8)`; the renderer must not translate them again. The delivery remains a candidate pending owner GPU/visual review, not production acceptance or proof of runtime rendering. Provenance, source manifest, attribution and applicable licenses are retained under `docs/botany-sprint/model-delivery/provenance/`; editable `.bbmodel` sources are retained under `docs/botany-sprint/model-source/`.

### Tray front correction R2 (tray/status resources only)

The saved `front_status_slots_r2` candidate is installed as a correction to the tray only: it replaces the tray JSON/PNG and adds five independent optional status surface model/PNG/`.mcmeta` resources. Original collection plant models, textures, and their state map are deliberately preserved byte-for-byte. Editable tray and five surface `.bbmodel` sources are in `docs/botany-sprint/model-source/`; attribution and source/license records remain in `docs/botany-sprint/model-delivery/provenance/`. The complete slot binding, coordinates, native overlay appearances/crops, and timing contract is `docs/botany-sprint/model-delivery/indicator-bindings.json` and the renderer notes are in `RENDER-CONTRACT.md`. This is source/assets installation only: no overlay renderer/controller, status interpretation, or alert logic is included. User/GPU review and runtime rendering remain pending.

## Seed extractor block fallback

The temporary cube fallback model `models/block/seed_extractor.json` uses runtime texture `block/botany/seed_extractor.png`, derived from the first 32×32 frame (top-left crop at 0,0) of the 96×96 `seedextractor.png` sheet in the retained `Structures/Machines/seed_extractor.rsi` source bundle. The source `meta.json` remains unchanged and records CC-BY-SA-3.0, the tgstation source commit `c6e3401f2e7e1e55c57060cdf956a98ef1fefc24`, and the modification notice. This copy is a temporary native fallback texture, not commissioned geometry or visual acceptance.
