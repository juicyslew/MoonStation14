# Additional food and legume compositions

Source-only primary-product audit at verified SS14 pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Not local registration, visual acceptance or complete downstream recipe/reagent support. Exact packet/product identities are indexed in CATALOG-REFERENCES.json; resolve remaining seed defaults before emitting runtime data.

## Medicinal plants, mushrooms and peppers

All ten products inherit FoodProduceBase and ordinary food/injection/grind/potency-appearance composition, not special digestion. No static juice or slicing/butchering output is authored on these leaves.

| Seed / product | Initial food / capacity (u) | Additional composition |
|---|---|---|
| chanterelle / FoodMushroom | Nutriment 5 / 5 | ordinary produce |
| lingzhi / FoodLingzhi | Ultravasculine 20 + Epinephrine 20 / 40 | ordinary produce |
| flyAmanita / FoodFlyAmanita | Amatoxin 10 + Nutriment 5 / 15 | BadFood |
| ambrosiaVulgaris / FoodAmbrosiaVulgaris | Bicaridine 5 + Kelotane 5 + Nutriment 2 + Vitamin 2 / 14 | HEAD clothing, Ambrosia tag; target ambrosiaDeus |
| ambrosiaDeus / FoodAmbrosiaDeus | Omnizine 3 + SpaceDrugs 5 + Nutriment 2 / 10 | HEAD clothing, Ambrosia tag |
| galaxythistle / FoodGalaxythistle | Stellibinin 25 / 25 | target glasstle |
| glasstle / FoodGlasstle | Razorium 15 + Desoxyephedrine 10 / 25 | inorganic damage, melee/refinement described below |
| spacemansTrumpet / FoodSpacemansTrumpet | Nutriment 1 + PolypyryliumOligomers 3 / 4 | Instrument program 56, Flower tag |
| chili / FoodChiliPepper | Nutriment 4 + CapsaicinOil 10 + Vitamin 4 / 18 | Repeat; target chilly |
| chilly / FoodChillyPepper | Nutriment 4 + FrostOil 10 + Vitamin 4 / 18 | Repeat |

Other targets are empty. Source seed chemistry differs from starting contents: trumpet generates Nutriment 1–5/divisor 50 and oligomers 1–15/5, for example. Do not substitute initial solution for genetics. CapsaicinOil/FrostOil names alone do not establish direct item temperature behavior; actual reagent consumers are separate.

Glasstle has Damageable(Inorganic), threshold 10 spawning one ShardGlass, DamageOnHit self-Blunt 10, melee Slash 15 and ToolRefinable producing SheetGlass1 (Welding quality, two seconds, three fuel). These are independent of grinding. Source instrument use, clothing, BadFood and refinement each need real consumers; ordinary food registration does not implement them.

Source indexes: seed YAML 892–923,1095–1155,1241–1378,1383–1406,1544–1564; produce YAML 1338–1350,1640–2037; source SeedPrototype/ProduceGrown and prior PARITY refinement/instrument notes. Downstream instruments/refinement and reagent physiology are not fully audited here.

## Ordinary fruit and vegetables

These eight are ordinary FoodProduceBase descendants, with no mutation targets. Packet ancestry is shared SeedBase. All grind current food; static juices are independent fixed outputs.

| Seed / product | Initial food contents / max (u) | Static juice and other outputs |
|---|---|---|
| pineapple / FoodPineapple | Nutriment 10, Vitamin 2, Water 4 / 16 | JuicePineapple 10; five FoodPineappleSlice via utensil-Knife slicing |
| garlic / FoodGarlic | Nutriment 10, Vitamin 4, Allicin 4 / 18 | no juice/secondary output established |
| corn / FoodCorn | Cornmeal 15, Nutriment 5 / 20 | Cornoil 3 + Enzyme 2; FoodCornTrash on full eating or independent knife butchery |
| grape / FoodGrape | Nutriment 5, Vitamin 4 / 9 | JuiceGrape 10 |
| cocoa / FoodCocoaPod | Nutriment 2, Vitamin 1, CocoaPowder 1 / 14 | use-in-hand SpawnItemsOnUse creates FoodCocoaBeans, consumes pod when uses exhausted |
| berries / FoodBerries | Nutriment 5, Vitamin 4 / 11 | JuiceBerry 10; not an anomaly effect |
| bungo / FoodBungo | Nutriment 10, Enzyme 10 / 20 | JuiceBungo 10; FoodBungoPit on full eating or knife butchery |
| cherry / FoodCherry | Nutriment 3, Vitamin 3 / 8 | JuiceCherry 5; TrashCherryPit on full eating or knife butchery |

Bungo pit is separately FoodInjectableBase, food max 7u with Nutriment 2 + Bungotoxin 5, BadFood, SpaceGarbage, grinding and Seed(bungo). Cherry pit is separately FoodInjectableBase, Tiny, food max 1u with Toxin 1, BadFood, SpaceGarbage, grinding and Seed(cherry). Their seed identity does not prove inheritance of the eaten fruit's instance genetics. Investigate actual trash-spawn/seed initialization before implementing that boundary.

Slicing uses Utensil Knife and splits current solution into discrete slice entities before source deletion. Knife butchery instead uses SharpComponent plus Butcherable and its do-after. Cocoa use-spawn is neither path. Full food consumption, not each bite, emits Edible trash. New outputs must not be replaced with generic items or native stack counts.

Sources: seed YAML 347–376,707–734,797–822,1596–1619,1681–1759,1938–1962; produce YAML 617–647,824–860,1167–1268,1358–1403,2307–2445,2682–2752. SliceableFoodSystem 35–103; SharpSystem 40–139; SpawnItemsOnUseSystem 24–29,64–100. Cocoa-bean downstream recipes and complete secondary behaviors remain separate dependencies.

## Rice, soy and peas

RiceBushel, FoodSoybeans and FoodKoibean inherit ProduceBaseRuminant and require special digestion. FoodPeaPod, FoodLaughinPeaPod and FoodWorldPeas inherit ordinary FoodProduceBase; laughin pea's Ruminant tag does not itself impose special digestion.

| Seed / packet / product | Targets | Initial food / capacity (u) | Static juice |
|---|---|---|---|
| rice / RiceSeeds / RiceBushel | none | Rice 10 / 10 | none |
| soybeans / SoybeanSeeds / FoodSoybeans | koibean | Nutriment 4 / 4 | MilkSoy 5 |
| koibean / KoibeanSeeds / FoodKoibean | none | Nutriment 5 + CarpoToxin 2 / 7 | CarpoToxin 2 |
| pea / PeaSeeds / FoodPeaPod | laughinPea | Nutriment 3 + Vitamin 2 / 5 | none |
| laughinPea / LaughinPeaSeeds / FoodLaughinPeaPod | worldPea | Nutriment 3 + Sugar 5 + Laughter 5 / 13 | LaughinSyrup 5 |
| worldPea / **PeaSeeds** / FoodWorldPeas | none | Happiness 3 + Nutriment 3 + Pax 2 / 8 | none |

Only the three pea seeds repeat harvest in this group. No knife/use-spawn/trash output is authored on these primary leaves. WorldPeaSeeds exists as an entity, but the worldPea seed definition binds PeaSeeds; carried instance genetics must override ordinary pea fallback identity. Restricted ingestion remains independent of grinding/juicing.

Sources: seed YAML 167–200,1490–1595,1764–1826; packet YAML 539–577,651–671; produce YAML 155–189,1986–2029,2069–2099,2447–2511. Server botany Seed.cs 42–78,150–190 and Produce.cs 12–38 establish instance precedence and content replacement/capacity behavior. Initial solutions are not genetic formulas; source seed chemical maps must be resolved separately.
