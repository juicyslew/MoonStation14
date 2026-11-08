# Botany catalog items C — source-faithful art-only brief

## Exact scope and exclusions

Commission exactly 34 distinct primary item identities: `LemonSeeds`, `FoodLemon`, `LemoonSeeds`, `FoodLemoon`, `LimeSeeds`, `FoodLime`, `OrangeSeeds`, `FoodOrange`, `ExtradimensionalOrangeSeeds`, `FoodExtradimensionalOrange`, `GrapeSeeds`, `FoodGrape`, `WatermelonSeeds`, `FoodWatermelon`, `HolymelonSeeds`, `FoodHolymelon`, `BerrySeeds`, `FoodBerries`, `CherrySeeds`, `FoodCherry`, `LingzhiSeeds`, `FoodLingzhi`, `AmbrosiaVulgarisSeeds`, `FoodAmbrosiaVulgaris`, `AmbrosiaDeusSeeds`, `FoodAmbrosiaDeus`, `GalaxythistleSeeds`, `FoodGalaxythistle`, `GlasstleSeeds`, `FoodGlasstle`, `FlyAmanitaSeeds`, `FoodFlyAmanita`, `SpacemansTrumpetSeeds`, and `FoodSpacemansTrumpet`.

This request is art-only: no code, runtime registration, resource import, mechanics, audio, publication, delivery, visual acceptance, or owner acceptance. There are no secondary output-art identities in this batch. Slices, watermelon/holymelon pits, and headgear are distinct separate inventory/questions, not included or covered by dropping/claiming this batch. No requested item art depends on grown-plant art. Preserve the existing five-pair/tray package, A's 23 pairs/outputs, B's 20 identities, the ten-target item request, and the 65-family grown request unchanged. Do not infer vanilla reuse: prior local reuse inventory was NONE; exact-ID and canonical accepted-asset-index review is required before any later commission/import.

## Pinned source, exact visual binding, and inherited composition

Verified source checkout `C:/Users/William/Documents/SS14-dev/space-station-14`, revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Prototype YAML paths below are relative to `Resources/Prototypes/Entities/Objects`; RSI paths are relative to `Resources/Textures`. Each row's selected state is an explicit visual binding, not a claim to copy behavior.

| Exact ID | Prototype source; parent/composition and selected sprite | RSI state |
|---|---|---|
| LemonSeeds | `Specific/Hydroponics/seeds.yml`; `SeedBase`, family sprite layer | `Objects/Specific/Hydroponics/lemon.rsi:seed` |
| FoodLemon | `Consumable/Food/produce.yml`; `FoodProduceBase` → `FoodInjectableBase` → `FoodBase` → `EdibleBase` → `BaseItem`; inherited produce layer | `.../lemon.rsi:produce` |
| LemoonSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../lemoon.rsi:seed` |
| FoodLemoon | `produce.yml`; food chain above; inherited produce layer | `.../lemoon.rsi:produce` |
| LimeSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../lime.rsi:seed` |
| FoodLime | `produce.yml`; inherits `FoodLemon` (food chain above), overrides Sprite | `.../lime.rsi:produce` |
| OrangeSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../orange.rsi:seed` |
| FoodOrange | `produce.yml`; inherits `FoodLemon` (food chain above), overrides Sprite | `.../orange.rsi:produce` |
| ExtradimensionalOrangeSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../extradimensional_orange.rsi:seed` |
| FoodExtradimensionalOrange | `produce.yml` lines 781–784; direct parent `FoodProduceBase` (food chain above); inherited produce layer | `.../extradimensional_orange.rsi:produce` |
| GrapeSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../grape.rsi:seed` |
| FoodGrape | `produce.yml`; food chain above; inherited produce layer | `.../grape.rsi:produce` |
| WatermelonSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../watermelon.rsi:seed` |
| FoodWatermelon | `produce.yml`; food chain above; inherited produce layer | `.../watermelon.rsi:produce` |
| HolymelonSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../holymelon.rsi:seed` |
| FoodHolymelon | `produce.yml`; food chain above; inherited produce layer | `.../holymelon.rsi:produce` |
| BerrySeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../berries.rsi:seed` |
| FoodBerries | `produce.yml`; food chain above; inherited produce layer | `.../berries.rsi:produce` |
| CherrySeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../cherry.rsi:seed` |
| FoodCherry | `produce.yml`; food chain above; inherited produce layer | `.../cherry.rsi:produce` |
| LingzhiSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../lingzhi.rsi:seed` |
| FoodLingzhi | `produce.yml`; food chain above; inherited produce layer | `.../lingzhi.rsi:produce` |
| AmbrosiaVulgarisSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../ambrosia_vulgaris.rsi:seed` |
| FoodAmbrosiaVulgaris | `produce.yml`; food chain above; inherited produce layer | `.../ambrosia_vulgaris.rsi:produce` |
| AmbrosiaDeusSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../ambrosia_deus.rsi:seed` |
| FoodAmbrosiaDeus | `produce.yml`; food chain above; inherited produce layer | `.../ambrosia_deus.rsi:produce` |
| GalaxythistleSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../galaxythistle.rsi:seed` |
| FoodGalaxythistle | `produce.yml`; food chain above; inherited produce layer | `.../galaxythistle.rsi:produce` |
| GlasstleSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../glasstle.rsi:seed` |
| FoodGlasstle | `produce.yml`; `FoodProduceBase` → food chain above; preserve explicit refinable-item source role as art context only | `.../glasstle.rsi:produce` |
| FlyAmanitaSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../fly_amanita.rsi:seed` |
| FoodFlyAmanita | `produce.yml`; food chain above; inherited produce layer | `.../fly_amanita.rsi:produce` |
| SpacemansTrumpetSeeds | `seeds.yml`; `SeedBase`, family sprite layer | `.../spacemans_trumpet.rsi:seed` |
| FoodSpacemansTrumpet | `produce.yml`; food chain above; inherited produce layer | `.../spacemans_trumpet.rsi:produce` |

All 17 packets inherit `SeedBase`'s conditional hidden `Objects/Specific/Hydroponics/seeds.rsi:paper` layer. Show that paper only as its conditional layer; the selected packet family RSI/state replaces the packet sprite layer. Do **not** misstate the whole packet as a common-seeds RSI binding. Retain exact source hierarchy and independent layer treatment in artist previews and provenance.

The listed food composition is source inheritance, not permission or a claim to implement food behavior. In particular `FoodLime` and `FoodOrange` inherit `FoodLemon`, not a guessed generic produce parent. `FoodExtradimensionalOrange` is a direct `FoodProduceBase` child (produce.yml lines 781–784); it does **not** inherit lime/orange ancestry. Additional parent/source-role distinctions are art boundaries: ambrosia includes headwear-related art/identity; `FoodGlasstle` has weapon/refinable source context; trumpet has instrument context. These roles do not commission armor/headwear, weapon, refining, melee, audio, UI, instrument system, or other mechanics. Do not make inferred artwork claim them. Check every complete inherited prototype chain before final mapping delivery.

## Complete source image/state, animation, and layer handling

For every selected RSI, fetch/retain the complete source `meta.json` verbatim and provenance snapshots/hashes of every selected source PNG. Preserve frame count, ordering, direction and per-frame delays exactly; metadata is authoritative. Audit all actual frame PNGs, not merely the state label. The `produce` state for `FoodExtradimensionalOrange` is animated: source delays are 23 frames at 0.08 seconds each. `FoodHolymelon`'s `produce` is animated: 4 frames at 0.3 seconds each. Do not describe either as static or collapse them silently. Item art request does not include grown-family animation deliverables.

The existing native generated-item texture contract supports animated PNG strips with adjacent `.png.mcmeta`: `src/main/resources/assets/moonstation14/models/item/handheld_health_analyzer.json` binds `moonstation14:item/medical/analyzer/analyzer-animation` (with its existing animated texture), and `network_configurator.json` / `network_configurator_list.json` bind `moonstation14:item/network_configurator-strip` (with its existing `.mcmeta`). Reuse this established flat item pipeline; introduce no new framework, code, 3D model, or silent freeze. Preserve the source frame images in source order and document every adaptation in manifest/provenance.

For `FoodHolymelon`, native per-frame duration is exact: each of 4 source frames is 0.3 s = 6 Minecraft ticks, for a 24-tick (1.2 s) loop. For `FoodExtradimensionalOrange`, each source frame is 0.08 s = 1.6 ticks, which cannot be represented exactly per frame by integer-tick `.mcmeta` timing. Preserve all 23 frame images in order and construct the repeating native strip sequence by replaying source indices 0..22 five times (115 metadata entries). Assign each entry 1 or 2 ticks by rounding cumulative ideal source-frame boundaries `round(k * 1.6)` to integer tick boundaries and using adjacent-boundary differences as durations. The resulting cycle is exactly 184 ticks (9.2 s), matching five source cycles' aggregate duration (`23 * 0.08 * 5 = 9.2 s`); each boundary is within 0.4 tick of ideal, so each entry is positive 1/2 ticks and the cumulative drift never exceeds 0.5 tick. This is an explicit Minecraft cadence quantization adaptation, not exact individual-frame cadence: preserve source `.08` delays in provenance and state the per-boundary maximum error. Do not interpolate frames or add subtick timing. Require source-first animated gameplay-scale contact previews showing the complete 5-cycle/115-entry sequence for the extradimensional orange and the exact 4-frame holymelon loop; previews are not publication or visual acceptance. The conditional packet paper is a separate source layer/PNG with its own source license/copyright provenance; do not flatten it into packets.

## Verbatim source rights/provenance

All 17 family RSIs and shared `seeds.rsi` are CC-BY-SA-3.0. Include complete unmodified RSI `meta.json` snapshots, exact source selected frame PNG(s), shared conditional paper PNG, hashes and verbatim copyright strings in the future delivery/provenance. No relicensing.

* `lemon.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `lemoon.rsi`: “Taken and resprite from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github)”
* `lime.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `orange.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github), seed.png modified from https://github.com/space-wizards/space-station-14/pull/23210/files#diff-1ee24e9bb4023f490a3f87c861b65e459bd3fda10e688471cdf72b8a8aab381b by Chaoticaa (github), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `extradimensional_orange.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068 and https://github.com/tgstation/tgstation/commit/ead6d8d59753ef033efdfad17f337df268038ff3 and modified by slarticodefast, inhands by mubururu_ (github)”
* `grape.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/b459ea3fdee965bdc3e93e7983ad7fa610d05c12, Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands by mubururu_ (github), inhands modified by Prole0 (GitHub)”
* `watermelon.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/b459ea3fdee965bdc3e93e7983ad7fa610d05c12 and https://github.com/tgstation/tgstation/commit/ead6d8d59753ef033efdfad17f337df268038ff3, inhands by mubururu_ (github), slice.png modified from https://github.com/space-wizards/space-station-14/pull/14587/files#diff-3d6e5b157c429de7aa62396e8677d0a2fab073a2d6d6e796af4931cb8d979330 by Chaoticaa (GitHub), Growth stages, harvest, dead, and produce sprites Created by Chaoticaa (GitHub),”
* `holymelon.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/b459ea3fdee965bdc3e93e7983ad7fa610d05c12 and https://github.com/tgstation/tgstation/commit/ead6d8d59753ef033efdfad17f337df268038ff3 and modified by slarticodefast, inhands by mubururu_ (github), produce sprite modified by Chaoticaa (GitHub), Growth stages, harvest, and dead sprites created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `berries.rsi`: “Taken from tgstation: https://github.com/tgstation/tgstation/commit/696dfcc59c9e65e7bbe3923d1f7e880ea384783f, inhands by mubururu_ (github), Growth, harvest, and dead sprites created by Chaoticaa (GitHub)”
* `cherry.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068 and remade by RumiTiger, Growth stages, harvest, dead, and produce sprites created by Chaoticaa (GitHub)”
* `lingzhi.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a, inhands by mubururu_ (github)”
* `ambrosia_vulgaris.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a, inhands by mubururu_ (github)”
* `ambrosia_deus.rsi`: “Taken from https://github.com/BeeStation/BeeStation-Hornet/commit/bd870d649cc8ac9d8af2fbc046af4a79afb5ab28, inhands by mubururu_ (github)”
* `galaxythistle.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a, inhands by mubururu_ (github)”
* `glasstle.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a”
* `fly_amanita.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a, inhands by mubururu_ (github), produce created by Chaoticaa (GitHub), Growth stages, harvest, and dead modified by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `spacemans_trumpet.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a seed modified by potato1234_X (github) for ss14, inhands by mubururu_ (github)”
* `seeds.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13 at 1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, paper layer by Ian321”

## Art approach and eventual delivery contract

Start source-first with gameplay-scale contacts for ordinary packets (conditional paper layer visible as such) and whole produce; specifically review animated extradimensional orange and holymelon, flower/headwear-related ambrosia, weapon/refinable glasstle, and instrument-role trumpet. Contacts are visual source investigation, not output assets or mechanism requests. Preserve baseline five pairs/tray, A's 23, B's 20, target request's ten, and 65 grown-family scope; do not include secondary outputs here.

When separately authorized to deliver, use the established flat native Java 1.21.1 / NeoForge 21.1.224 item JSON/PNG + adjacent `.png.mcmeta` pipeline, editable source art, unique lowercase resources, explicit non-overwriting manifest, previews, exact case-sensitive ID→prototype chain→RSI/state/frame→resource mappings, all source snapshots/hashes, complete attribution, and read-only validator. Validate exactly 34 unique IDs, actual JSON overlap against each extant request and canonical accepted asset index, source/state/frame/delay correspondence (including the explicit integer-tick adaptation), licenses/copyright, unique safe destinations and every declared/provenance file. No import or queue/publication operation is authorized by this brief.
