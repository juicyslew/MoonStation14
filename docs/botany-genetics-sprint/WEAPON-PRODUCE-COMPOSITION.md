# Weapon-producing fruit and nettles

Verified source pin: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. These are separate runtime capability dependencies, not ordinary food registrations or delivered assets.

## Fruit outputs

Gatfruit seeds target fakeCapfruit then realCapfruit. Gatfruit, real capfruit and fake capfruit share lifespan 65, maturation/production 25, yield 1, potency 10, two stages, ideal light 6; Nutriment and Sulfur each min 1/max 5/divisor 20. Other seed fields require inherited defaults. Real/fake capfruit have no further targets.

| Seed / packet / product | Product parents | Full-eat trash and independent Knife Butcherable output |
|---|---|---|
| gatfruit / GatfruitSeeds / FoodGatfruit | FoodProduceBase + BaseSyndicateContraband | one WeaponRevolverPython |
| realCapfruit / RealCapfruitSeeds / FoodRealCapfruit | FoodProduceBase | one RevolverCapGun |
| fakeCapfruit / FakeCapfruitSeeds / FoodFakeCapfruit | FoodRealCapfruit, overriding seed and outputs | one RevolverCapGunFake |

FakeCapfruitSeeds inherits the real packet and overrides Seed identity; sharing capfruit RSI does not merge their identities. Gatfruit's packet additionally inherits syndicate contraband composition. Each fruit has initial food Nutriment 5u + Sulfur 5u, explicit capacity 10u; generated chemistry replaces those contents. FoodProduceBase supplies ordinary Edible, food grinding and potency appearance, not functional firearms. Python is a real magnum revolver; the other outputs are cap guns with distinct ammunition-provider composition.

Eating trash is emitted on full consumption. Butchering is a separate SharpComponent/Knife admission plus do-after, spawning the weapon and destroying the source. No output probability is authored. Full gun firing/ammunition behavior remains outside this audit; local gun absence must not be hidden by registering the fruit as plain food.

## Nettle products

Nettle seeds target deathNettle. Both use lifespan 25, maturation 8, production 6, yield 2, potency 20, five stages, ideal light 8/heat 298. Nettle water .60 and Histamine 1–25/divisor 4; death nettle water .70/nutrients .80, SulfuricAcid and FluorosulfuricAcid each 1–15/divisor 6. Death nettle has no further target.

- Nettle inherits ProduceBaseRuminant, requires special digestion, and has initial Histamine 3u. Its melee Heat 10 and MeleeChemicalInjector (3u from current food, no hardsuit piercing) are independent of grinding/genetic chemistry.
- DeathNettle inherits ProduceBase + BaseMajorContraband, **not** ProduceBaseRuminant. Initial food contains each acid 15u. Melee damage is Heat 8 + Caustic 8; injector transfers 5u and pierces armor. Do not invent special-digestion inheritance from ordinary nettle.
- Follow-up source trace now establishes DeathNettle's **DamageOnInteract**: empty-hand interaction damage Heat 4 + Caustic 4, two-second interaction timer, throw speed 3, 10% stun chance for 1.5 seconds. This is not passive damage merely from holding it. Earlier notes that this had not been established were audit limits, not proof of absence.
- The complete DeathNettle block also has Damageable(Inorganic), DamageOnHit self-Blunt 5, and destruction threshold 25, with `diona_salute.ogg` at −5 dB before destruction. Its authored hand-burn cue is `lightburn.ogg`, popup `powered-light-component-burn-hand`, and `ignoreResistances:false`. These are additional live consumers/ordered destruction dependencies, not established by chemical injection alone. Neither five successful hits nor a fixed lifetime should be promised without tracing resistance and hit admission.

Injection must consume current solution via the event consumer, not regenerate seed chemicals on each hit. Character combat/armor, empty-hand interaction and knockdown/throw semantics need real shared consumers before enabling these leaves. No local support is asserted by this source mapping.

## Local reuse and deferral boundary

Targeted runtime investigation found no functional firearm/ammunition owner or held-item melee admission/injection owner. Generic ingestion trash and Butcherable publication can emit items, but cannot supply working revolvers. Do not register inert weapon substitutes or omit their behavior behind ordinary fruit profiles.

`DamageSystem.applyHealthChange` / `observePost` are the shared typed-damage authority, not hit admission or equipment armor matching. `TimedInteractionSystem` supplies timing, not DeathNettle's burn/throw/stun behavior. `LooseItemEntity`'s impact self-damage reducer is a possible future reuse target for destruction, not a melee-hit producer. These dependencies justify deferring the special products until shared combat consumers exist; implementing a parallel botany-only combat framework would bypass their proper owners.

Sources: seeds YAML 1037–1093,1409–1488; packets YAML 517–526,713–730; produce YAML 231–327,1894–1984. Consumers: SharpSystem 40–139; SolutionInjectOnEventSystem 49–80; DamageOnInteractSystem 35–46,78–102. Weapon prototypes: revolvers YAML 126–149 and Fun/weapons YAML 77–136. Full reagent effects and firearm consumers remain separate dependencies.
