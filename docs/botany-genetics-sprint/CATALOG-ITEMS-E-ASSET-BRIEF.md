# Botany catalog items E — art-only asset request

## Scope and boundaries

Commission source-faithful art for exactly the 34 primary item identities listed in `CATALOG-ITEMS-E-REQUEST.json`. This completes primary-item request planning only: it is not asset delivery, review, acceptance, import, runtime implementation, or complete processing-output coverage. This batch is separate from the 65 grown-family request, the existing 10-target request, and published catalog items A (23) and B (20), all of which remain unchanged; C (34) and D (16) are independently authored and are not dependencies. No overlap with the 10/A/B requests is intended. Preserve canonical BasicBotany five-pair/tray assets.

Explicitly excluded secondary outputs include pit, beans, `CarvedPumpkin`, egg shells, guns, planks/sheets, and anomaly loot. Do not claim complete processing coverage. `MobTomatoKiller` is an actual mob, excluded because it lacks item consumers; do not substitute/package a flat item for it. `FoodWorldPeas` is a distinct product identity but uses `PeaSeeds` already; do not invent a second packet identity. Fake and real capfruit share the source RSI family but retain distinct packet/product bindings. No audio, mod code, registration, resource import, dependency/tool/mechanic, or fabricated Blockbench 3D plant model is authorized.

Art does not implement or claim upstream gameplay behavior. In particular, preserve source distinctions: `FoodBloonion` is `BaseStructureDynamic` with its own solution/explosion behavior, not ordinary edible produce; `FoodEgg` independently derives from `FoodEggBase` and `ItemHeftyBase`, not `FoodProduceBase`; logs use `ProduceBase`; ordinary nettle and death nettle have different compositions (death nettle derives from `BaseMajorContraband`); gatfruit is contraband; fake capfruit inherits real capfruit while overriding outputs; anomaly berry trash has a separate random-mob-spawn consumer. These details are identity/art context only.

## Pinned source and source-first mapping

Pinned checkout is `C:/Users/William/Documents/SS14-dev/space-station-14`, revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (verified). Prototype paths below are relative to `Resources/Prototypes/Entities/Objects/`; RSI paths are relative to `Resources/Textures/`. `CATALOG-REFERENCES.md` is catalog context, not composition evidence. Final packet and product bindings, inherited layers, selected PNG state metadata, licenses, and verbatim copyright are recorded below from the pinned prototypes and actual RSI files. Parent reuse audit reports no matches among these 34 IDs in current Java/resources/canonical indexes. Preserve exact upstream identity case in provenance; do not infer a resource or attribution from a family name. Minecraft's vanilla `FoodEgg` is not SS14 source art.

| Exact identity | Source prototype family and verified visual state |
|---|---|---|
| `AnomalyBerrySeeds`, `FoodAnomalyBerry` | seeds / produce; `anomaly_berry.rsi:seed` / `:produce` |
| `BloonionSeeds`, `FoodBloonion` | seeds / structure-derived item product; `bloonion.rsi:seed` / `:produce` |
| `PumpkinSeeds`, `FoodPumpkin` | seeds / produce; `pumpkin.rsi:seed` / `:produce` |
| `BluePumpkinSeeds`, `FoodBluePumpkin` | seeds / produce; `blue_pumpkin.rsi:seed` / `:produce` |
| `BungoSeeds`, `FoodBungo` | seeds / produce; `bungo.rsi:seed` / `:produce` |
| `CocoaSeeds`, `FoodCocoaPod` | seeds / produce; `cocoa.rsi:seed` / `:produce` |
| `NettleSeeds`, `Nettle` | seeds / ruminant product; `nettle.rsi:seed` / `:produce` |
| `DeathNettleSeeds`, `DeathNettle` | seeds / contraband product; `death_nettle.rsi:seed` / `:produce` |
| `EggySeeds`, `FoodEgg` | seeds / independent egg-base product; `eggy.rsi:seed` / `egg.rsi:icon` + `:white` |
| `GatfruitSeeds`, `FoodGatfruit` | seeds / contraband product; `gatfruit.rsi:seed` / `:produce` |
| `FakeCapfruitSeeds`, `FoodFakeCapfruit` | inherited real-capfruit art with distinct ID; `capfruit.rsi:seed` / `:produce` |
| `RealCapfruitSeeds`, `FoodRealCapfruit` | seeds / product; `capfruit.rsi:seed` / `:produce` |
| `PeaSeeds`, `FoodPeaPod` | seeds / produce; `pea.rsi:seed` / `:produce` |
| `LaughinPeaSeeds`, `FoodLaughinPeaPod` | seeds / produce; `laughin_pea.rsi:seed` / `:produce` |
| `FoodWorldPeas` | distinct product, existing packet identity `PeaSeeds`; `world_pea.rsi:produce` |
| `KillerTomatoSeeds` | seeds only; `tomatokiller.rsi:seed`, not a mob model |
| `TowercapSeeds`, `Log` | seeds / log product; `towercap.rsi:seed` / `:produce` |
| `SteelcapSeeds`, `SteelLog` | seeds / log product; `steelcap.rsi:seed` / `:produce` |

### Verified visual-source audit findings

#### Completed packet-only source audit (17 identities)

This table is the exact packet binding audit against `Resources/Prototypes/Entities/Objects/Specific/Hydroponics/seeds.yml` and each RSI's actual `meta.json` at the pinned revision. All 17 leaf entities use `SeedBase` directly except `GatfruitSeeds` (`[SeedBase, BaseSyndicateContraband]`) and `FakeCapfruitSeeds` (inherits `RealCapfruitSeeds`). All but `FakeCapfruitSeeds` set a family Sprite resource in the leaf; FakeCapfruit inherits the RealCapfruit packet Sprite resource. `SeedBase` provides the visible `seed` layer and a separate initially-hidden `paper` layer from `Objects/Specific/Hydroponics/seeds.rsi`; its visualizer reveals the paper layer when the paper-label appearance indicates a label. The `seed` state is a single static frame with no explicit directions or delays in every row below; the source `seed.png` exists for every row. RSI path and selected state are exact; all source paths below are relative to `Resources/Textures/`.

| Exact packet ID | Source parent; leaf Sprite binding | Selected state; verified PNG | RSI license |
|---|---|---|---|
| `AnomalyBerrySeeds` | `SeedBase`; `Objects/Specific/Hydroponics/anomaly_berry.rsi` | `seed`; `Objects/Specific/Hydroponics/anomaly_berry.rsi/seed.png` | CC-BY-SA-3.0 |
| `BloonionSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/bloonion.rsi` | `seed`; `Objects/Specific/Hydroponics/bloonion.rsi/seed.png` | CC0-1.0 |
| `PumpkinSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/pumpkin.rsi` | `seed`; `Objects/Specific/Hydroponics/pumpkin.rsi/seed.png` | CC-BY-SA-3.0 |
| `BluePumpkinSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/blue_pumpkin.rsi` | `seed`; `Objects/Specific/Hydroponics/blue_pumpkin.rsi/seed.png` | CC-BY-SA-3.0 |
| `BungoSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/bungo.rsi` | `seed`; `Objects/Specific/Hydroponics/bungo.rsi/seed.png` | CC-BY-SA-3.0 |
| `CocoaSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/cocoa.rsi` | `seed`; `Objects/Specific/Hydroponics/cocoa.rsi/seed.png` | CC-BY-SA-3.0 |
| `NettleSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/nettle.rsi` | `seed`; `Objects/Specific/Hydroponics/nettle.rsi/seed.png` | CC-BY-SA-3.0 |
| `DeathNettleSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/death_nettle.rsi` | `seed`; `Objects/Specific/Hydroponics/death_nettle.rsi/seed.png` | CC-BY-SA-3.0 |
| `EggySeeds` | `SeedBase`; `Objects/Specific/Hydroponics/eggy.rsi` | `seed`; `Objects/Specific/Hydroponics/eggy.rsi/seed.png` | CC-BY-SA-3.0 |
| `GatfruitSeeds` | `[SeedBase, BaseSyndicateContraband]`; `Objects/Specific/Hydroponics/gatfruit.rsi` | `seed`; `Objects/Specific/Hydroponics/gatfruit.rsi/seed.png` | CC-BY-SA-3.0 |
| `FakeCapfruitSeeds` | inherits `RealCapfruitSeeds`; inherited `Objects/Specific/Hydroponics/capfruit.rsi` | inherited `seed`; `Objects/Specific/Hydroponics/capfruit.rsi/seed.png` | CC-BY-SA-3.0 |
| `RealCapfruitSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/capfruit.rsi` | `seed`; `Objects/Specific/Hydroponics/capfruit.rsi/seed.png` | CC-BY-SA-3.0 |
| `PeaSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/pea.rsi` | `seed`; `Objects/Specific/Hydroponics/pea.rsi/seed.png` | CC-BY-SA-3.0 |
| `LaughinPeaSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/laughin_pea.rsi` | `seed`; `Objects/Specific/Hydroponics/laughin_pea.rsi/seed.png` | CC-BY-SA-3.0 |
| `KillerTomatoSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/tomatokiller.rsi` | `seed`; `Objects/Specific/Hydroponics/tomatokiller.rsi/seed.png` | CC-BY-SA-3.0 |
| `TowercapSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/towercap.rsi` | `seed`; `Objects/Specific/Hydroponics/towercap.rsi/seed.png` | CC-BY-SA-3.0 |
| `SteelcapSeeds` | `SeedBase`; `Objects/Specific/Hydroponics/steelcap.rsi` | `seed`; `Objects/Specific/Hydroponics/steelcap.rsi/seed.png` | CC-BY-SA-3.0 |

#### Verbatim source metadata for the packet RSI set

Full exact `copyright` field from each distinct packet source RSI `meta.json` (all source files also retain metadata fields such as version, size, and complete state declarations). Shared `seeds.rsi` is one conditional label layer used by all packet identities, not 17 additional packet graphics. `capfruit.rsi` is intentionally recorded once for both capfruit packet IDs.

* `anomaly_berry.rsi` (CC-BY-SA-3.0): “Created by TheShuEd for Space Station 14, inhands by mubururu_ (github)”
* `bloonion.rsi` (CC0-1.0): “Made by Thinbug for space station 14 :33”
* `pumpkin.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/tgstation/tgstation/blob/5d507cfbad6f73d1beaba66d93f31f893adb3a84/icons/obj/hydroponics/harvest.dmi, carved sprites by ps3moira, inhands by mubururu_ (github), Growth stages, dead, and harvest sprites created by Chaoticaa (GitHub)”
* `blue_pumpkin.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/tgstation/tgstation/commit/5d507cfbad6f73d1beaba66d93f31f893adb3a84, modified by potato1234_x (github), inhands by mubururu_ (github), Growth, harvest, and dead created by Chaoticaa (GitHub)”
* `bungo.rsi` (CC-BY-SA-3.0): “Taken from tgstation: https://github.com/tgstation/tgstation/commit/b3399ffe39f9ace645123c98323c4832412cc641”
* `cocoa.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/vgstation-coders/vgstation13. produce-beans drawn by FillerVK, inhands by mubururu_ (github)”
* `nettle.rsi` and `death_nettle.rsi` (each CC-BY-SA-3.0): “Taken from tgstation at https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a”
* `eggy.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, seed.png modified from https://github.com/space-wizards/space-station-14/pull/23210/files#diff-13896c37cf761a5782bb95bbc433bb738dd59ed89276af17dccd606adb759ee5 by Chaoticaa (GitHub), Growth stages, dead, and harvest sprites created by Chaoticaa (GitHub)”
* `gatfruit.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a, inhands by mubururu_ (github)”
* `capfruit.rsi` (CC-BY-SA-3.0; used for both capfruit packet bindings): “Taken from https://github.com/tgstation/tgstation/commit/40d89d11ea4a5cb81d61dc1018b46f4e7d32c62a, redrawn by Ubaser, inhands by mubururu_ (github).”
* `pea.rsi` (CC-BY-SA-3.0): “Taken from tgstation at commit https://github.com/tgstation/tgstation/commit/a5e6db8f99b436b643bd3b76fa131ac074dfd856, inhands by mubururu_ (github), Growth, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `laughin_pea.rsi` (CC-BY-SA-3.0): “Taken from tgstation at commit https://github.com/tgstation/tgstation/commit/a5e6db8f99b436b643bd3b76fa131ac074dfd856 seed modified by potato1234_x (github) for ss14, inhands by mubururu_ (github), Growth, harvest, dead, and produce created by Chaoticaa (GitHub), inhands modified by Prole0 (GitHub)”
* `tomatokiller.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/vgstation-coders/vgstation13 at 1dbcf389b0ec6b2c51b002df5fef8dd1519f8068”
* `towercap.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/vgstation-coders/vgstation13/commit/1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, inhands by mubururu_ (github)”
* `steelcap.rsi` (CC-BY-SA-3.0): “Taken from https://github.com/tgstation/tgstation/commit/dccc1e60a3c151e2b7fac26da7b3087757f1e116, inhands by mubururu_ (github)”
* `seeds.rsi` conditional label layer (CC-BY-SA-3.0): “Taken from https://github.com/vgstation-coders/vgstation13 at 1dbcf389b0ec6b2c51b002df5fef8dd1519f8068, paper layer by Ian321”

This completes source verification for the **17 packet identities**. Product mappings are separately verified below.

The pinned checkout revision was re-verified. The selected ordinary family states are `seed` and `produce` (inherited state for standard produce); all are present in their listed RSI metadata as one-frame static states. `Nettle` uses `nettle.rsi:produce`; `DeathNettle` uses `death_nettle.rsi:produce`; `FoodWorldPeas` uses `world_pea.rsi:produce`; `Log` and `SteelLog` use `towercap.rsi:produce` and `steelcap.rsi:produce`. The corresponding packets use each family RSI's `seed`, including `KillerTomatoSeeds` from `tomatokiller.rsi:seed`. `FoodFakeCapfruit` inherits the `FoodRealCapfruit` sprite (therefore `capfruit.rsi:produce`), while remaining a separate exact item identity; both packet entities use `capfruit.rsi:seed`. `FoodBloonion` explicitly selects `bloonion.rsi:produce`, with `drawdepth: Items`, `noRot: true`, and an explicit matching Item sprite/held prefix; its `BaseStructureDynamic` ancestry does not remove that item sprite binding.

`FoodEgg` is `FoodEggBase` → `FoodEgg`, with `FoodInjectableBase` and `ItemHeftyBase` on the base. The base Sprite/Item resource is `Objects/Consumable/Food/egg.rsi`, Item size Tiny; the leaf replaces Sprite layers with `icon` mapped to `enum.DamageStateVisualLayers.Base`. Its leaf `RandomSprite.available` defines that Base layer with **both** `icon` and `white` variants. They are two state choices for the same visual layer, not two simultaneously stacked layers. Source PNGs are `Resources/Textures/Objects/Consumable/Food/egg.rsi/icon.png` and `white.png`; the source RSI metadata declares both as static single-frame states, no directions beyond the default single direction, and no delays. Source prototype does not specify a preferred variant: RandomSprite selects among both. The art commission must export both variants as distinct native JSON/PNG bindings and include source-first contacts showing both. Do not freeze to `icon`, discard `white`, or claim selection parity. An existing per-stack random item-variant/display binding is not established by the current generated-item contract; its runtime choice remains an explicit integration question, with no new renderer/framework authorized as part of art commissioning.

#### Completed product-only source audit (17 identities)

Prototype paths are relative to `Resources/Prototypes/Entities/Objects/`; RSI and PNG paths below are relative to `Resources/Textures/`. Every selected state was found in its exact RSI `meta.json`; absent `directions` means RSI default single direction, absent `delays` means one static frame. All selected PNG files below exist. For shared family RSIs, the complete verbatim copyright is recorded once in the packet provenance section.

| Exact product ID | Leaf prototype parent; visual source resolution | Selected RSI state; verified PNG | License |
|---|---|---|---|
| `FoodAnomalyBerry` | `produce.yml`; `FoodProduceBase`; leaf RSI and explicit state | `Objects/Specific/Hydroponics/anomaly_berry.rsi:produce`; `Objects/Specific/Hydroponics/anomaly_berry.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodBloonion` | `produce.yml`; `BaseStructureDynamic`; leaf explicit Sprite/Item RSI and state; Items drawdepth, noRot | `Objects/Specific/Hydroponics/bloonion.rsi:produce`; `Objects/Specific/Hydroponics/bloonion.rsi/produce.png` | CC0-1.0 |
| `FoodPumpkin` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/pumpkin.rsi:produce`; `Objects/Specific/Hydroponics/pumpkin.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodBluePumpkin` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/blue_pumpkin.rsi:produce`; `Objects/Specific/Hydroponics/blue_pumpkin.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodBungo` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/bungo.rsi:produce`; `Objects/Specific/Hydroponics/bungo.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodCocoaPod` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/cocoa.rsi:produce`; `Objects/Specific/Hydroponics/cocoa.rsi/produce.png` | CC-BY-SA-3.0 |
| `Nettle` | `produce.yml`; `ProduceBaseRuminant`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/nettle.rsi:produce`; `Objects/Specific/Hydroponics/nettle.rsi/produce.png` | CC-BY-SA-3.0 |
| `DeathNettle` | `produce.yml`; `[ProduceBase, BaseMajorContraband]`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/death_nettle.rsi:produce`; `Objects/Specific/Hydroponics/death_nettle.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodEgg` | `egg.yml`; `FoodEggBase` → `[FoodInjectableBase, ItemHeftyBase]`; Base layer, RandomSprite choices | `Objects/Consumable/Food/egg.rsi:icon` and `:white`; `Objects/Consumable/Food/egg.rsi/icon.png` and `white.png` | CC-BY-SA-3.0 |
| `FoodGatfruit` | `produce.yml`; `[FoodProduceBase, BaseSyndicateContraband]`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/gatfruit.rsi:produce`; `Objects/Specific/Hydroponics/gatfruit.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodFakeCapfruit` | `produce.yml`; inherits `FoodRealCapfruit` Sprite and state | `Objects/Specific/Hydroponics/capfruit.rsi:produce`; `Objects/Specific/Hydroponics/capfruit.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodRealCapfruit` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/capfruit.rsi:produce`; `Objects/Specific/Hydroponics/capfruit.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodPeaPod` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/pea.rsi:produce`; `Objects/Specific/Hydroponics/pea.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodLaughinPeaPod` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/laughin_pea.rsi:produce`; `Objects/Specific/Hydroponics/laughin_pea.rsi/produce.png` | CC-BY-SA-3.0 |
| `FoodWorldPeas` | `produce.yml`; `FoodProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/world_pea.rsi:produce`; `Objects/Specific/Hydroponics/world_pea.rsi/produce.png` | CC-BY-SA-3.0 |
| `Log` | `produce.yml`; `ProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/towercap.rsi:produce`; `Objects/Specific/Hydroponics/towercap.rsi/produce.png` | CC-BY-SA-3.0 |
| `SteelLog` | `produce.yml`; `ProduceBase`; leaf RSI, inherited state | `Objects/Specific/Hydroponics/steelcap.rsi:produce`; `Objects/Specific/Hydroponics/steelcap.rsi/produce.png` | CC-BY-SA-3.0 |

Exact `FoodEgg` `egg.rsi` copyright (CC-BY-SA-3.0): “Taken from cev-eris at https://github.com/discordia-space/CEV-Eris/raw/9c980cb9bc84d07b1c210c5447798af525185f80/icons/obj/food.dmi”. Additional selected product family `world_pea.rsi` (CC-BY-SA-3.0): “Taken from tgstation at commit https://github.com/tgstation/tgstation/commit/ead6d8d59753ef033efdfad17f337df268038ff3 and modified by slarticodefast, inhands by mubururu_ (github)”. All other product RSI copyrights/licenses are shared with packet families and transcribed verbatim above. All selected product states are static/no-delay; only FoodEgg has multiple selected source variants, both to be delivered and shown. Runtime random choice is unresolved; do not omit either variant.

The selected product state audit confirms one static frame/no delays for every product RSI state except that FoodEgg selects two separate static states, `icon` and `white`, on one randomized Base visual layer. Metadata/license/copyright for each reused family RSI and for FoodEgg/world_pea is documented above; retain complete actual source `meta.json` and source PNGs. The selected art audit does not cover nonselected animated `pumpkin.rsi:lantern` (five declared delay entries at 0.2s each), or any unrelated growth/in-hand states.

Do not infer handheld geometry for bloonion from its entity identity. Request only source-native item rendering and preserve identity/draw bounds; any unsupported world-structure geometry is a runtime-support boundary, not a fabricated 3D model. The exact towercap/steelcap product bindings were verified above.

## Commission and deliverables

Start with source-first, gameplay-scale contacts for packet, log, bloonion, egg variants, nettle, capfruit shared-art/identity mapping, and an ordinary pod before batching. If source poses are anisotropic and current flat-item rendering cannot represent them faithfully, demonstrate that in contacts for review before production. Reuse the existing Java 1.21.1 / NeoForge 21.1.224 native generated-item JSON/PNG pipeline and applicable flat profiles; do not add renderer/animation code.

Deliver editable artwork, native item JSON/PNG, exact ID→prototype ancestry→selected state/layers/frames→resource mapping, non-overwriting flat destination manifest, complete source snapshots and hashes, exact metadata/attribution/licenses, practical previews, and a read-only validator. Keep SS14 identity case exact in metadata and stable lowercase runtime resource names. Dependencies remain empty unless a real source-art dependency is demonstrated; do not add grown-state dependencies. The existing native item pipeline supports animated PNG strips plus adjacent `.png.mcmeta`; if a selected state is found animated, reuse that established pipeline and the existing C/D cadence adaptation methodology, preserving source frames/order/timing with explicit quantization rather than freezing frames or adding a framework. For FoodEgg, export and show both `icon` and `white` source variants; do not claim runtime random choice absent an established per-item random display binding. Validator should check all 34 unique identities, actual source states/variants, metadata and license provenance, safe unique destinations, declared files, context, and A/B/10-target non-overlap. This brief authorizes art-only preparation/delivery workflow; it does not authorize runtime/code changes or asset import.
