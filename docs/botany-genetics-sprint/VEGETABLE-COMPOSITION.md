# Vegetable product composition

Source audit: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, revision verified. This is product/processing evidence, not installed catalog data, secondary-output completion or asset acceptance. Resolve each seed's remaining lifecycle/biology defaults before runtime profile emission.

## Identity and solution boundaries

| Seed / packet / product | Ordered mutation targets | Initial food contents / capacity (u) | Static JUICE |
|---|---|---|---|
| carrots / CarrotSeeds / FoodCarrot | none | JuiceCarrot 5, Vitamin 4, Oculine 3 / 12 | JuiceCarrot 10 + Oculine 2 |
| cabbage / CabbageSeeds / FoodCabbage | none | Nutriment 10, Vitamin 4 / 14 | none |
| eggplant / EggplantSeeds / FoodEggplant | eggy | Nutriment 10, Vitamin 4 / 14 | none |
| onion / OnionSeeds / FoodOnion | onionred, bloonion | Nutriment 10, Vitamin 4, Allicin 4 / 18 | none |
| onionred / OnionRedSeeds / FoodOnionRed | none | Nutriment 10, Vitamin 4, Allicin 4 / 18 | none |
| pumpkin / PumpkinSeeds / FoodPumpkin | bluePumpkin | PumpkinFlesh 20, Vitamin 5 / 25 | none |
| watermelon / WatermelonSeeds / FoodWatermelon | holymelon | Nutriment 10, Vitamin 5, Water 10 / 25 | JuiceWatermelon 20 |

These inherit ordinary `FoodProduceBase → FoodInjectableBase → FoodBase → EdibleBase → BaseItem`, including ordinary Edible admission, injection/refill, Produce, Appearance/PotencyVisuals and named `food` grinding. Watermelon additionally inherits ItemHeftyBase. Packets retain shared SeedBase ancestry; packet identity/art is not product behavior.

Seed-generated chemistry is independent of these starting contents and static juices. Min–Max/divisor: carrots JuiceCarrot 1–5/20, Oculine 2–6/20, Vitamin 1–4/25; cabbage/eggplant Nutriment 1–10/10, Vitamin 1–4/25; both onions add Allicin 1–4/25 to that pair; pumpkin PumpkinFlesh 1–20/5, Vitamin 1–5/20; watermelon Nutriment 1–10/10, Water 1–10/10, Vitamin 1–5/20. Harvest clears initial contents, generates from actual seed genetics, and adds generated volume to retained base capacity. Never add prototype initial reagents again or infer juice from food contents.

## Distinct processing consumers

- **Onion slicing:** both leaves configure SliceableFood, count 5, respective FoodOnionSlice/FoodOnionRedSlice output. Admission requires a held UtensilComponent with Knife utensil type, not merely Sharp. Defaults: one-second do-after and chop sound. Upstream declares `.5` SpawnOffset but does not consume it; completion spawns separate outputs, applies random horizontal throw velocity to slices outside containers, divides current solution contents, and deletes source. Native stack count is not a substitute.
- **Pumpkin:** Small item, Biological damage, destruction threshold 16 spilling `food`, and Knife Butcherable outputs one CarvedPumpkin plus one PumpkinSeeds. SharpSystem admission requires SharpComponent, correct butcher type, reach and a do-after (default eight seconds times tool modifier, interrupted by movement/damage). This is not slicing's utensil-knife rule.
- **Watermelon:** Small item; inherited hefty self-land and thrown-target damage remain distinct. Biological damage, high-speed self-impact min .1/Blunt 1, destruction threshold 1 and food spill; SliceableFood produces five FoodWatermelonSlice entities. Independently, Knife Butcherable produces one ClothingHeadHatWatermelon. These are separate actions and output graphs. Tomato's generic block-impact implementation does not establish thrown-target or every source fixture-contact behavior.
- **Carrot/cabbage/eggplant:** no slice/butcher component established on these leaves. Carrot has its explicit multi-reagent juice. Eggy's separate FoodEgg composition is not FoodEggplant behavior.

Slice targets, CarvedPumpkin and watermelon hat still require resolved downstream composition before enabling their outputs. Do not register all seven as ordinary food and silently drop their processing dependencies.

## Source indexes

- `Resources/Prototypes/Hydroponics/seeds.yml`: 138–165,653–705,825–889,1622–1650,1829–1854.
- `Entities/Objects/Consumable/Food/produce.yml`: 543–615,1059–1086,1270–1330,2102–2160,2514–2560; common bases 4–45; `food_base.yml` 4–40.
- `Content.Server/Nutrition/Components/SliceableFoodComponent.cs` 10–36 and `SliceableFoodSystem.cs` 40–102,109–168.
- `Content.Shared/Nutrition/Components/ButcherableComponent.cs` 10–33; `Content.Server/Kitchen/EntitySystems/SharpSystem.cs` 46–145; shared `EntitySpawnEntry.cs` 26–67,101–125 for default one entity.
- Shared grinder system 351–400; server botany Produce/Seed systems and existing `docs/botany-sprint/PROCESSING-COMPOSITION.md` for chemistry/capacity semantics.
