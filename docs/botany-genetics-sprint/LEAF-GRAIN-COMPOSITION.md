# Grain and leaf composition

Source: verified SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Source audit, not local registration, processing support or art acceptance.

All six packets inherit SeedBase and declare the exact case-sensitive seed ID. Raw products inherit `ProduceBaseRuminant → ProduceBase → BaseItem`; rainbow leaves inherit ordinary cannabis leaves with overrides. They require special digestion, carry Ruminant, and expose actual named food solutions for grinding. They are not ordinary human food or automatically smokable.

| Seed / packet / product | Targets | Initial food / effective capacity | Static juice |
|---|---|---|---|
| oat / OatSeeds / OatBushel | none | Oats 10u / 10u | MilkOat 5u |
| sugarcane / SugarcaneSeeds / Sugarcane | papercane | Sugar 10u / 10u | none |
| teaPlant / TeaPlantSeeds / LeavesTea | none | Vitamin 2u / 2u | none |
| tobacco / TobaccoSeeds / LeavesTobacco | none | Nicotine 2u / 2u | none |
| cannabis / CannabisSeeds / LeavesCannabis | rainbowCannabis | THC 15u / 15u | none |
| rainbowCannabis / RainbowCannabisSeeds / LeavesCannabisRainbow | none | SpaceDrugs 3, Lipolicide 3, MindbreakerToxin 2, Happiness 2, Psicodine .6u / 10.6u | none |

Seed chemistry (min–max/divisor): oat Nutriment 1–20/20, Oats 5–20/20; sugarcane Sugar 4–5/5; tea Vitamin 1–5/20; tobacco Nicotine 1–10/10; cannabis THC 1–10/10. Rainbow has SpaceDrugs and Lipolicide 1–15/10, MindbreakerToxin 1–5/20, Happiness 1–5 with **omitted divisor**, Psicodine 0–5/33. Resolve the omitted chemical default before data emission; do not invent a divisor. Harvest replaces starting contents, retaining base capacity plus generated volume; static juice is separate.

Oat lifespan/maturation/production 25/6/3, yield 3, potency 5, six stages. Sugarcane Repeat 60/6/6, yield 3, potency 10, three stages. Tea Repeat 75/5/3, yield 2, potency 20, five stages. Tobacco Repeat 75/5/5, yield 2, potency 20, three stages. Both cannabis seeds Repeat 75/8/12, yield 2, potency 20, three stages. Resolve remaining inherited biology/defaults from SeedPrototype before runtime profiles.

## Actual processing outputs

The four raw leaf types have specific microwave recipes: RecipeDriedTeaLeaves, RecipeDriedTobacco, RecipeDriedCannabis and RecipeDriedCannabisRainbow. Each consumes one exact raw leaf, time 10, and produces a fixed dried prototype. This is not a general drying component or evidence that genetic chemistry is copied.

Dried tea remains ProduceBaseRuminant, with TeaPowder 5u. Dried tobacco/cannabis instead inherit BaseItem and use distinct stack identities, fixed Nicotine 2u / THC 12u food solutions. Dried rainbow overrides contents to SpaceDrugs 2.4, Lipolicide 2.4, MindbreakerToxin 1.6, Happiness 1.6, Psicodine .48u (8.48u contents, 8.5u declared capacity).

Separate construction graphs consume two dried tobacco/cannabis/rainbow leaves in a five-second action to create GroundTobacco/GroundCannabis/GroundCannabisRainbow. Ground products have fixed solutions and relevant Smokable tags. Further two-second graphs combine paper/filter with ground ingredients for cigarettes/joints/blunts. Raw leaves are not those smokeables. Complete microwave transactions, ground/smokeable output composition and smoking consumers remain downstream audits; do not claim genetic transmission through fixed-prototype recipes. Sugarcane itself has no Log output; papercane is its distinct mutation target with the separately audited Sharp material route.

Sources: seed YAML 56–80,405–452,941–1035; produce YAML 95–153; `Objects/Specific/Hydroponics/leaves.yml` 1–263; cooking `meal_recipes.yml` 2116–2149; crafting `Graphs/smokeables.yml` 1–112; shared grinder system 351–400. Source packet defaults in `Objects/Specific/Hydroponics/seeds.yml` 1–27.
