# Special primary harvest products

Verified SS14 pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Source mapping only: no local mob/anomaly/explosive-structure completion, secondary output delivery or runtime acceptance.

## Killer tomato

`killerTomato → KillerTomatoSeeds → MobTomatoKiller`. Repeat, lifespan 25, maturation 15, production 6, yield 2, potency 10, two stages, water .60/nutrients .70, light 8/heat 298. Genetic chemistry: Blood 1–10/divisor 10; JuiceTomato 1–4/25.

The **complete** mob prototype is `Entities/Mobs/NPCs/miscellaneous.yml` 73–231. Parents are BaseSimpleMob, MobDamageable, MobPolymorphable, MobBloodstream, MobFlammable and MobCombat. It composes hostile faction/HTN, humanoid imprinting, bloodstream, combat, ghost role/takeover (.05 availability), large multi-handed item shape and living/dead visuals. Melee is Blunt/Piercing/Slash 3 each. Alive/dead thresholds are 0/35. Knife butchering gives two FoodMeatTomato; Blunt 40 destruction gives 1–2 meat, destruction, then gib sound. PassiveDamage configures Blunt .11 in Alive/Dead; its tick cadence was not traced.

The tail explicitly contains **Extractable bloodstream, PotencyVisuals, Appearance, Produce(seedId: killerTomato)**. An intermediate audit mistakenly stopped at line 202; its claim of absent Produce is retracted. GenerateProduct ensures Produce on every output, assigns the actual seed instance, runs ProduceGrown and writes potency Appearance. Produce's default generation solution is `food`, while killer tomato's grind target is `bloodstream`: **distinct solutions**. Do not claim seed chemicals populate bloodstream. Mob spawning/control/combat and separate solution channels cannot be represented faithfully by an ordinary food ItemStack.

## Other special products

| Seed / product | Initial food / capacity (u) | Static juice | Essential independent behavior |
|---|---|---|---|
| bloonion / FoodBloonion | Potassium 1, Phosphorus 1, Sugar 1, Allicin 4 / 7; canReact false | none | BaseStructureDynamic, **not Edible**; explicit small Item, Produce, potency visuals and grinding; threshold 1 spills, triggers explosion, then destruction |
| anomalyBerry / FoodAnomalyBerry | Nutriment 2, Vitamin 2, Artifexium 1 / 5 | none | FoodProduceBase; full-eat trash EffectAnomalyFloraBulb invokes a timed loot table that may produce mobs as well as items |
| holymelon / FoodHolymelon | Nutriment 10, Vitamin 5, Holywater 10 / 25 | Wine 20 | FoodProduceBase + ItemHeftyBase; impact/destruction/spill; five FoodHolymelonSlice; independent knife output ClothingHeadHatHolyWatermelon |
| bluePumpkin / FoodBluePumpkin | Ammonia 10, Chlorine 5, Vitamin 10 / 25 | JuiceBluePumpkin 10 | direct FoodProduceBase, not FoodPumpkin; Biological threshold 16 with spill, **no inherited orange-pumpkin butchery** |

Holy melon's prototype Produce seedId is watermelon fallback; actual holymelon instance genetics win. Its heavy-land/target-hit rules remain separate from self-impact. Bloonion's nonreacting solution, trigger and physics must not be flattened into ordinary edible onion. Anomaly bulb is not generic trash or a guaranteed harmless item. Source generated chemistry still replaces initial contents and extends retained capacity; static juice is separate.

Source seed YAML 620–650,1653–1675,1857–1884,1965–2028; produce YAML 2204–2295,2514–2613,2754–2865; anomaly effects `Effects/mobspawn.yml` 102–145. Mob parent indexes: `Mobs/base.yml` 65–119,152–165,258–273 and `NPCs/simplemob.yml` 1–32. Botany Seed.cs 150–190, Produce.cs 12–39 and ProduceComponent.cs 9–23 establish generation/channel behavior. Full ghost takeover, anomaly loot execution, passive timing and downstream output compositions remain separate consumer audits before claiming equivalent gameplay.
