# Eggy harvest and egg composition

Verified SS14 pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Source mapping only; no FoodEgg registration, art delivery or local runtime completion is established.

`eggy → EggySeeds → FoodEgg`: Repeat, lifespan 75, maturation 6, production 12, yield 2, potency 20, nutrients .50, light 9, heat 298; default six stages and remaining seed defaults. No mutation targets. Egg chemistry min 4/max 12/divisor 10.

`FoodEgg → FoodEggBase → [FoodInjectableBase, ItemHeftyBase]`, not FoodProduceBase. Egg/Meat tags, Tiny item, drawing/spiking, full-eat trash Eggshells; initial food Egg 6u and capacity 6u. Runtime GenerateProduct **ensures Produce**, assigns the actual seed and generates chemistry even though the egg prototype does not author that component. At potency 20, initial Egg 6u is replaced with generated Egg 6u and capacity becomes 12u. FoodEgg does not inherit PotencyVisuals; writing a potency Appearance datum is not proof of a scale consumer.

RandomSprite selects exactly one Base-layer state, `icon` or `white`, once on server MapInit, persists/replicates the selection and applies it client-side. No local selector exists yet; separate variants are not item identities or stacked layers.

DamageOnHighSpeedImpact min .1/Blunt 1 and damage threshold 1 feed ordered spill → one Eggshells spawn → desecration sound → destruction. This differs from tomato's destruction sequence. The inherited hefty land/other-hit effects are distinct triggers. AtmosExposed, Temperature, TemperatureDamage and InternalTemperature (thickness .011, area .04, conductivity .456) support thermal behavior. The egg construction graph's 473.15 K step applies Blunt 10; together with its threshold this establishes heat-breakage, **not** a generic bomb or hatching mechanic.

Eggshells is separately Tiny edible/trash: food Egg 1u/max 2u, Egg/Trash tags and SpaceGarbage. It must not be replaced with a generic discard or emitted on every partial bite. Complete temperature/construction runtime and cooking consumers remain separate support requirements.

Sources: seed YAML 916–938; packet YAML 331–339; `Consumable/Food/egg.yml` 3–70,100–116; construction `Graphs/food/egg.yml` 1–18; botany Seed.cs 150–182, Produce.cs 12–39; RandomSprite component/server/client systems cited in PARITY and the E brief. Historical asset-request hashes are unchanged by this clarification.
