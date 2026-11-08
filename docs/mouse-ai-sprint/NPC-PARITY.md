# NPC behavior composition and parity

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
`MobMouse` inherits `SimpleMobBase` and `BaseMobAnimal`; `SimpleMobBase` inherits
`SimpleSpaceMobBase`, which inherits `BaseSimpleMob`, `MobAtmosExposed`,
`MobCombat`, `MobBloodstream`, `MobFlammable`, and `BaseMobAnimal`. `BaseSimpleMob`
inherits `BaseMob`, `MobDamageable`, and `MobPolymorphable`; `BaseMob` inherits
`BaseControllable`. `SimpleMobBase` contributes animal hunger/thirst thresholds;
`MobMouse` contributes the `HTN` root task. `BaseMobAnimal` additionally
composes Body and animal organs, which are not fabricated locally.

The expanded mouse task order is FOOD (only at Peckish), then DRINK (only at
Parched), then IDLE. Both nutrition branches search visible edible sources in
vision 10; idle uses range 7 and waits 2–7 seconds. The `NearbyDrink` query uses
`DrinkValueCon`, which rejects `BadDrinkComponent` targets before evaluating
hydration. Upstream base puddles compose `BadDrink`, so the mouse NPC must not
select them. The local NPC consumer selects eligible typed world-item drinks
only; it does not add AI puddle drinking. Player-controlled puddle ingestion is
unchanged. Item target selection is gated by the local typed ingestion profile
and admission path; unsupported generic SS14 entities are not fabricated.
Resolved mouse physiology keeps the existing local thresholds (hunger
Peckish 15, thirst Parched 10) rather than copying unrelated upstream values.

`supported_npc` is a local abstract composition adapter inheriting
`supported_animal` and supplying an IDLE-only default; it is not a fabricated
upstream component. Mouse inherits that adapter and resolves FOOD, DRINK, IDLE
in order. `NpcBehaviorComponent` defaults to vision 10, meal range 1, idle
range 7, nine-tick replanning and idle wait 40–140 ticks.

Runtime uses typed local reagent effects to compute positive hunger satiation
and hydration value (food nonzero; drinks over 1u), then the verified utility
curve's normalized target-distance term and alive-mob rejection. `GroundNpcRoutePlanner`
is a bounded loaded-only surface A* adaptation: 3D one-block steps, at most
one-block drops, explicit fluid/pit avoidance, and live `AirlockPassagePolicy`
checks. The shared grounded motor alone executes the route; vanilla navigation
is never ticked. These Minecraft path/drop rules are adaptations, not SS14
parity claims.

The route planner is custom rather than wrapping vanilla `PathNavigation`:
vanilla node evaluation is tied to its navigator's mob/profile and chunk/node
semantics, while this consumer needs resolved mouse/airlock collision policy,
loaded-only footprint checks, fractional collision-top support (slabs/stairs),
and a small bounded search whose waypoint steps are driven by the shared motor.
This avoids a second movement authority and a navigator with mismatched
collision assumptions.

Upstream source files: `Content.Server/NPC/Systems/NPCUtilitySystem.cs`
(`DrinkValueCon` rejects `BadDrinkComponent`),
`Resources/Prototypes/Entities/Effects/puddle.yml` (base puddle `BadDrink`),
`Resources/Prototypes/NPCs/utility_queries.yml` (`NearbyDrink`),
`Resources/Prototypes/Entities/Mobs/NPCs/animals.yml`
(`MobMouse`), `NPCs/simplemob.yml` (`BaseSimpleMob`, `SimpleSpaceMobBase`,
`SimpleMobBase`), `Resources/Prototypes/Entities/Mobs/base.yml` (`BaseMob`,
`BaseControllable`, `MobDamageable`, `MobPolymorphable`),
`Resources/Prototypes/Body/Animals/animal.yml` (`BaseMobAnimal`), and
`Resources/Prototypes/NPCs/{mob,nutrition,idle,utility_queries}.yml`. Local
composition is `supported_npc.json` → `supported_animal.json` →
`supported_simple_mob.json`; only the mouse host opts into the generic consumer.
