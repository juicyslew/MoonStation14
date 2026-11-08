# Botany catalog items B — art-only asset request

## Scope and boundaries

Prepare art for exactly these 20 identities: `EggplantSeeds`, `FoodEggplant`, `OnionSeeds`, `FoodOnion`, `OnionRedSeeds`, `FoodOnionRed`, `ChanterelleSeeds`, `FoodMushroom`, `RiceSeeds`, `RiceBushel`, `SoybeanSeeds`, `FoodSoybeans`, `KoibeanSeeds`, `FoodKoibean`, `ChiliSeeds`, `FoodChiliPepper`, `ChillySeeds`, `FoodChillyPepper`, `FoodOnionSlice`, and `FoodOnionRedSlice`. This is source-faithful, art-only scope; no runtime registration, code, resource import, acceptance, or gameplay implementation is authorized.

This is separate from the 65-family grown request, the existing ten-target swab/item request, and the canonical BasicBotany five-pair/tray package. Preserve all three unchanged. No grown-plant asset is an art dependency. The local audit found no matching item identity or native item binding for these 20 in project resources and no match in the existing target-item list. `CATALOG-REFERENCES.json` records the species/source plant RSIs only; a species entry is not an item asset. No request is made to copy a differently named species asset as an item identity.

Keep packet, whole-produce, ruminant output, and sliced-produce identities distinct. RiceBushel, FoodSoybeans, and FoodKoibean use the ruminant produce composition; do not suggest ordinary human-food behavior. Onion products yield five slices, but this request commissions only the two slice identities as art: it does not implement slicing, digestion, or any other mechanics. No audio.

## Pinned source and verified selection map

Source checkout `C:/Users/William/Documents/SS14-dev/space-station-14`, revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (verified). Prototype paths below are relative to `Resources/Prototypes/Entities/Objects/`; RSI paths relative to `Resources/Textures/`. All source-selected states are static single-frame states with no `delays` entry; preserve that honestly and do not create animation.

| Exact ID | Prototype source and visual inheritance | Selected RSI state |
|---|---|---|
| EggplantSeeds | `Specific/Hydroponics/seeds.yml`; `SeedBase` (inherits `BaseItem`, `BasePaperLabelable`); family sprite overrides seed layer | `Objects/Specific/Hydroponics/eggplant.rsi:seed` |
| FoodEggplant | `Consumable/Food/produce.yml`; `FoodProduceBase` (food base chain); produce state inherited | `.../eggplant.rsi:produce` |
| OnionSeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../onion.rsi:seed` |
| FoodOnion | `produce.yml`; `FoodProduceBase`; produce state inherited | `.../onion.rsi:produce` |
| OnionRedSeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../onion_red.rsi:seed` |
| FoodOnionRed | `produce.yml`; `FoodProduceBase`; produce state inherited | `.../onion_red.rsi:produce` |
| ChanterelleSeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../chanterelle.rsi:seed` |
| FoodMushroom | `produce.yml`; `FoodProduceBase`; produce state inherited | `.../chanterelle.rsi:produce` |
| RiceSeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../rice.rsi:seed` |
| RiceBushel | `produce.yml`; `ProduceBaseRuminant` → `ProduceBase` → `BaseItem`; produce state inherited | `.../rice.rsi:produce` |
| SoybeanSeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../soybeans.rsi:seed` |
| FoodSoybeans | `produce.yml`; `ProduceBaseRuminant` → `ProduceBase` → `BaseItem`; produce state inherited | `.../soybeans.rsi:produce` |
| KoibeanSeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../koibean.rsi:seed` |
| FoodKoibean | `produce.yml`; `ProduceBaseRuminant` → `ProduceBase` → `BaseItem`; produce state inherited | `.../koibean.rsi:produce` |
| ChiliSeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../chili.rsi:seed` |
| FoodChiliPepper | `produce.yml`; `FoodProduceBase`; produce state inherited | `.../chili.rsi:produce` |
| ChillySeeds | `seeds.yml`; `SeedBase`; family sprite overrides seed layer | `.../chilly.rsi:seed` |
| FoodChillyPepper | `produce.yml`; `FoodProduceBase`; produce state inherited | `.../chilly.rsi:produce` |
| FoodOnionSlice | `produce.yml`; `ProduceSliceBase` → `FoodInjectableBase`; inherited state `slice`, explicit onion sprite | `.../onion.rsi:slice` |
| FoodOnionRedSlice | `produce.yml`; `ProduceSliceBase` → `FoodInjectableBase`; inherited state `slice`, explicit red-onion sprite | `.../onion_red.rsi:slice` |

The nine family RSIs are eggplant, onion, onion_red, chanterelle, rice, soybeans, koibean, chili, chilly; all packets also inherit the conditional hidden `seeds.rsi:paper` label layer. Preserve it as a separate conditional layer, not flattened packet artwork. Source selected states are static; source metadata/frame count is authoritative. The sources show separate onion/red onion slice entities explicitly using `slice`; do not substitute their whole-produce `produce` state. See the pinned seed/product blocks plus `SeedBase`, `FoodProduceBase`, `ProduceBaseRuminant`, and `ProduceSliceBase` definitions. The food ancestry establishes source composition only, not a request to reproduce mechanics.

## License and verbatim source copyright

All ten RSI families including shared `seeds.rsi` are CC-BY-SA-3.0. Retain each complete source `meta.json`, source PNG/state used, and the exact copyright text below in provenance; do not relicense derivatives.

* `eggplant.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github), produce modified from https://github.com/space-wizards/space-station-14/commit/484eb0bba44d3e014873e7d7026a99d7ba56f7b9#diff-5b9a7a38ef618b5f482f5baf4bb8db27dc87f9af7602d67cae9133e624aa3f91 by Chaoticaa (GitHub), Growth, harvest, and dead created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `onion.rsi`: “Taken from tgstation at https://github.com/tgstation/tgstation/commit/9b0c2e771e9d2442dd7d21c019b970c1299b5cf4, inhands by mubururu_ (github), slice modified from https://github.com/space-wizards/space-station-14/pull/3436/files#diff-65d1e265b787136da82b8e8b56ede11fdb879c31f9d14b4236d33a9a89d6a815 by Chaoticaa (GitHub), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `onion_red.rsi`: “Taken from tgstation at https://github.com/tgstation/tgstation/commit/9b0c2e771e9d2442dd7d21c019b970c1299b5cf4, inhands by mubururu_ (github), seed modified from https://github.com/space-wizards/space-station-14/pull/23210/files#diff-6b89a02e399bc4dd9c50c16ad274a353a92c8a0f462083fab0c1e5045d957ad2 by Chaoticaa (GitHub), slice modified from https://github.com/space-wizards/space-station-14/pull/3436/files#diff-65d1e265b787136da82b8e8b56ede11fdb879c31f9d14b4236d33a9a89d6a815 by Chaoticaa (GitHub), Growth, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `chanterelle.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github)”
* `rice.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/b459ea3fdee965bdc3e93e7983ad7fa610d05c12, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `soybeans.rsi`: “Taken from https://github.com/BeeStation/BeeStation-Hornet/commit/bd870d649cc8ac9d8af2fbc046af4a79afb5ab28, produce modified from https://github.com/space-wizards/space-station-14/pull/13989/files#diff-a55f533faecbd8741098e25f259fa528683197ccc0e99a77f0f592a3ae2faa03 by Chaoticaa (GitHub), Growth, harvest, and dead created by Chaoticaa (GitHub)”
* `koibean.rsi`: “Taken from https://github.com/tgstation/tgstation/commit/696dfcc59c9e65e7bbe3923d1f7e880ea384783f, Growth, harvest, and dead created by Chaoticaa (GitHub)”
* `chili.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce sprites created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `chilly.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13/commit/b459ea3fdee965bdc3e93e7983ad7fa610d05c12, inhands by mubururu_ (github), Growth stages, harvest, dead, and produce sprites created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `seeds.rsi`: “Taken from https://github.com/vgstation-coders/vgstation13 at 1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, paper layer by Ian321”

## Commission and delivery

Begin source-first with gameplay-scale original-first contacts for the seed packet including conditional paper, each visually distinct whole produce family, both ruminant outputs, and the two onion slice outputs. Resolve source-faithfulness and scale before batch authoring. Reuse the existing generated-item native Java 1.21.1 / NeoForge 21.1.224 item-model/PNG pipeline and applicable flat profiles. No Blockbench 3D plant models.

Deliver editable art, native item JSON/PNG, exact ID→prototype→state→resource mapping, flat non-overwriting destination manifest, source snapshot/hashes, attribution, practical previews, and a read-only validator. Keep stable lowercase runtime resource names and exact upstream ID case in metadata. Validate all 20 identities, source states, licenses, unique safe destinations, declared files, and provenance references. Delivery/request is not asset delivery, independent visual review, owner acceptance, import, or runtime/mechanics completion. No import without separate authorization.
