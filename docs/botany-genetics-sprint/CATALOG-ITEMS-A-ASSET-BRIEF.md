# Botany catalog items A — art-only asset request

## Scope and boundaries

Prepare art for exactly these 23 upstream identities: `OatSeeds`, `OatBushel`, `BananaSeeds`, `FoodBanana`, `MimanaSeeds`, `FoodMimana`, `CarrotSeeds`, `FoodCarrot`, `CabbageSeeds`, `FoodCabbage`, `GarlicSeeds`, `FoodGarlic`, `AppleSeeds`, `FoodApple`, `GoldenAppleSeeds`, `FoodGoldenApple`, `CornSeeds`, `FoodCorn`, `PineappleSeeds`, `FoodPineapple`, `TrashBananaPeel`, `TrashMimanaPeel`, `FoodCornTrash`. This is a source-faithful art-only request; no runtime registration, code, resource import, acceptance, or gameplay implementation is authorized.

This is neither an expansion of the 65 grown-family request nor a duplicate of the existing 10-target item request or canonical BasicBotany five-pair tray package. Preserve those packages unchanged. There is no art dependency on grown-plant assets. Local reuse audit found no registered/native asset for these identities; the existing 65-family request excludes packets, produce, and trash, and the 10-target request has no overlap.

Art coverage is not mechanics completion. Preserve the identity and visual distinction of OatBushel (ruminant product), banana/mimana toxin or peel behavior and their peel outputs, and corn trash/current consumer dependencies. Do not flatten these to ordinary generic food or imply any mechanic is implemented. No audio.

## Pinned source and verified selection mapping

Source checkout `C:/Users/William/Documents/SS14-dev/space-station-14`, revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (revision verified). Prototype files below are relative to `Resources/Prototypes/Entities/Objects/`; RSI paths relative to `Resources/Textures/`. Each family uses `Objects/Specific/Hydroponics/{family}.rsi`.

| Exact item ID | Entity source/parent relationship | Selected visual state |
|---|---|---|
| OatSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `oat.rsi:seed` |
| OatBushel | `Consumable/Food/produce.yml`, `ProduceBaseRuminant` | `oat.rsi:produce` |
| BananaSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `banana.rsi:seed` |
| FoodBanana | `Consumable/Food/produce.yml`, `FoodProduceBase` | `banana.rsi:produce` |
| MimanaSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `mimana.rsi:seed` |
| FoodMimana | `Consumable/Food/produce.yml`, `FoodProduceBase` | `mimana.rsi:produce` |
| CarrotSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `carrot.rsi:seed` |
| FoodCarrot | `Consumable/Food/produce.yml`, `FoodProduceBase` | `carrot.rsi:produce` |
| CabbageSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `cabbage.rsi:seed` |
| FoodCabbage | `Consumable/Food/produce.yml`, `FoodProduceBase` | `cabbage.rsi:produce` |
| GarlicSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `garlic.rsi:seed` |
| FoodGarlic | `Consumable/Food/produce.yml`, `FoodProduceBase` | `garlic.rsi:produce` |
| AppleSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `apple.rsi:seed` |
| FoodApple | `Consumable/Food/produce.yml`, `FoodProduceBase` | `apple.rsi:produce` |
| GoldenAppleSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `golden_apple.rsi:seed` |
| FoodGoldenApple | `Consumable/Food/produce.yml`, `FoodProduceBase` | `golden_apple.rsi:produce` |
| CornSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `corn.rsi:seed` |
| FoodCorn | `Consumable/Food/produce.yml`, `FoodProduceBase` | `corn.rsi:produce` |
| PineappleSeeds | `Specific/Hydroponics/seeds.yml`, `SeedBase` | `pineapple.rsi:seed` |
| FoodPineapple | `Consumable/Food/produce.yml`, `FoodProduceBase` | `pineapple.rsi:produce` |
| TrashBananaPeel | `Consumable/Food/produce.yml`, `BaseItem` | `banana.rsi:peel` |
| TrashMimanaPeel | `Consumable/Food/produce.yml`, inherits `TrashBananaPeel`, sprite override | `mimana.rsi:peel` |
| FoodCornTrash | `Consumable/Food/produce.yml`, `BaseItem` | `corn.rsi:cob` |

These are separate identities and require separate item bindings even when they share one RSI. Parent composition matters: seed entities inherit both seed and conditional hidden paper-label layers from `SeedBase`, while the family-specific sprite layer overrides the seed art. Preserve `seeds.rsi:paper` as the label layer; do not guess/flatten its per-layer override. Bushel uses the ruminant parent. Produce selected state is inherited from `ProduceBase`/`FoodProduceBase`. Peels and cob select their explicit states. Selected states are static single-frame, with no frame delays; some in-hand direction states are explicit but do not manufacture animation. Banana/mimana primed animated states are not requested.

Direct source evidence: seeds and product/peel/cob entity blocks in `Specific/Hydroponics/seeds.yml` and `Consumable/Food/produce.yml`; `SeedBase`/paper layer in seed base entity definitions; shared produce base definitions in produce entity definitions. `CATALOG-REFERENCES.md` is catalog context only and does not establish composition. Retain exact upstream IDs/case in metadata and use stable lowercase runtime bindings.

## Source licenses and verbatim copyright

Preserve complete source `meta.json` files and the actual source PNG/state in provenance. All eleven RSI licenses are CC-BY-SA-3.0; do not relicense derived art. Verbatim copyright strings:

* `oat.rsi`: “Taken from vgstation and modified by Swept at https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `banana.rsi`: “Taken from tgstation at https://github.com/tgstation/tgstation/commit/6be7633abca9f1a51cab1020500cf0776ce78e5c, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub), On-head sprites by FlipBrooke,.”
* `mimana.rsi`: “Taken from tgstation at https://github.com/tgstation/tgstation/commit/6be7633abca9f1a51cab1020500cf0776ce78e5c, inhands by mubururu_ (github). Mimana peel on-head sprite by FlipBrooke, based on the mimana item sprite.”
* `carrot.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github)”
* `cabbage.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/b459ea3fdee965bdc3e93e7983ad7fa610d05c12#diff-3fdb6bdffec70eb17e6315b5cc5447b4cc73d39f583f077f064ad70f286138fa, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `garlic.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/9f2e55e83cb93e7124d06c705d1942dfec63c93a, produce created by Chaoticaa (GitHub)”
* `apple.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce sprites created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `golden_apple.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce sprites created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `corn.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, Growth, dead, harvest, and produce created by Chaoticaa (GitHub)”
* `pineapple.rsi`: “Taken from tgstation at https://github.com/tgstation/tgstation/commit/9b0c2e771e9d2442dd7d21c019b970c1299b5cf4, inhands by mubururu_ (github), slice.png modified from https://github.com/space-wizards/space-station-14/pull/31012/commits/f840c12356368798ab2dbe7de8232ed8181d29d6#diff-2ca7537b4a0f25517344be1d3a98d6cc33a3176c0af385bc5882b96d77afc64d by Chaoticaa (GitHub), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `seeds.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13 at 1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, paper layer by Ian321”

## Commission/deliverables

Begin source-first with gameplay-scale contacts representing packet (show conditional paper layer), ruminant bushel, produce, peel, and cob. Reuse the existing generated-item native Java 1.21.1 / NeoForge 21.1.224 item-model/PNG pipeline and flat item profiles; no Blockbench 3D plant models. Deliver editable art, native item JSON/PNG, exact ID→prototype→state→resource mapping, non-overwriting destination manifest, source snapshot/hashes, attribution, practical previews, and read-only validator. Validate all 23 identity mappings, licenses, unique safe destinations, declared files, and provenance refs. Art delivery is not visual or owner acceptance or runtime/mechanics completion; no import without separate authorization. Preserve the accepted BasicBotany five-pair/tray unchanged.
