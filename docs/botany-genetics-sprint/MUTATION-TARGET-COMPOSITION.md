# First authentic mutation targets

Source audit: pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, revision verified by the source specialist. This records required composition, not installed species, accepted art or runtime completion.

## Identity and continuing graph

| Existing source | Target seed | Packet | Primary product | Further target |
|---|---|---|---|---|
| wheat | meatwheat | MeatwheatSeeds | MeatwheatBushel | none |
| tomato | blueTomato | BlueTomatoSeeds | FoodBlueTomato | none |
| tomato | bloodTomato | BloodTomatoSeeds | FoodBloodTomato | killerTomato |
| poppy | lily | LilySeeds | FoodLily | spacemansTrumpet |

The last two outgoing edges cannot silently disappear when these targets are introduced. Killer tomato is a mob output, not an ordinary item. The current five installed species have no authentic mutual mutation edge; synthetic test targets do not fill that gap.

## Product boundaries

- **MeatwheatBushel:** `ProduceBaseRuminant → ProduceBase → BaseItem`; inherits restricted Edible admission, Produce, potency appearance and food-solution grinding. Its initial solution is 10u UncookedAnimalProteins. `SpawnItemsOnUse` produces one separate `FoodMeatWheat` with the source slime-squish cue. Bushel, secondary food and seed packet are distinct identities; do not turn the bushel into ordinary human food or substitute an eating result for its use action.
- **Blue/blood tomato:** inherit `FoodTomato → [FoodProduceBase, ItemHeftyBase]`, with ordinary Edible admission, food grinding, injection/refill, and tomato damage/destruction/spill behavior. Blue starts with Nutriment 5u, SpaceLube 15u, Vitamin 4u; blood starts with Blood 10u, Vitamin 4u. Separate juice outputs are SpaceLube 10u and Blood 10u. Neither is an additional grind recipe. Blue's replacement Destructible configuration does **not** remove inherited `DamageOnHighSpeedImpact` (minimum speed .1, Blunt 1). ItemHeftyBase separately supplies DamageOnLand and DamageOtherOnHit.
- **Lily:** `FoodPoppy → FoodProduceBase → FoodInjectableBase → FoodBase → EdibleBase → BaseItem`; ordinary edible/grindable produce, inheriting poppy's HEAD clothing slot with quick equip disabled. Initial solution is Nutriment 2u + Bicaridine 20u; its Flower tag and food-sequence output remain separate composition. No invented inherited `Item` component is required.
- All packets inherit `SeedBase → [BaseItem, BasePaperLabelable]`, then specify their own Seed identity and crop RSI. Packet art does not define produce behavior.

Harvest chemistry replaces prototype starting contents. `BotanySystem.ProduceGrown` uses the actual inherited seed genetics, clears the solution, and generates each chemical from its configured minimum, potency divisor and maximum; it does not add the item's initial contents again. Existing local `ProduceFactory`, `ProduceSourceComposition`, genetic carriers and ingestion/grinder owners remain reuse targets.

## Tomato impact is cumulative self-damage

The pinned `DamageOnHighSpeedImpactSystem` handles StartCollide only when both fixtures are hard and the item is Damageable. It measures the item's own full body speed, not relative/contact-normal speed. At speed at least .1, after the default two-second cooldown, configured Blunt 1 is scaled by `.5 × speed / .1`: the minimum-speed hit is .5 damage, not an automatic destruction. Biological damage accumulates; total damage at least 1 triggers ordered sound, spill of the current `food` solution, then destruction. `SpillBehavior` drains/splits that solution to prevent double-spilling. Hefty land/on-other-hit damage remains separate. The impact system also supplies hit sound/flash and stun handling; its defaults must be audited before claiming a complete local consumer.

The inherited `DamageOnLand` applies Blunt 1 to the item itself when a LandEvent is raised; the reviewed producer is timed thrown-item landing, not ordinary resting/dropped-item contact. `DamageOtherOnHit` instead applies Blunt 1 (times the universal thrown-damage modifier) to the hit target, requiring the thrown-hit path; it does not damage every collision partner. These three triggers must not be collapsed. Consumers: server `DamageOnLandSystem.cs` 14–23; shared `ThrownItemSystem.cs` 62–71,118–169; server `DamageOtherOnHitSystem.cs` 28–52.

Biological supports Brute/Burn/Toxin/Airloss/Genetic damage groups, including Blunt. Tomato has no authored damage modifier set or initial damage. Destruction's `desecration` collection contains three distinct `desecration-01/02/03.ogg` clips (`Resources/Prototypes/SoundCollections/desceration.yml`); it is not the collision hit sound. **Unresolved source detail:** tomato omits `soundHit`, while the impact component marks that field required without a concrete default; do not invent an effective sound or claim this omission resolved. Observable item stun eligibility is also unverified; no tomato-specific stun/status composition is authored.

Source owners: `Content.Shared/Damage/Components/DamageOnHighSpeedImpactComponent.cs` 14–39; `.../Systems/DamageOnHighSpeedImpactSystem.cs` 25–57; `Content.Server/Destructible/DestructibleSystem.cs` 51–109,152–165; `Content.Shared/Destructible/Triggers/DamageTrigger.cs` 21–24; server `SpillBehavior.cs` 17–36 and `DoActsBehavior.cs` 20–30. Minecraft's gravity, collision events and speed units require an explicit adaptation; do not substitute the existing jug's one-threshold break flag or “any collision bursts tomato.” Local authoritative loose-item contact and puddle/spill owners are reuse candidates, not proof that generic impact damage/destruction is implemented.

## Source indexes and implementation boundary

- `Resources/Prototypes/Hydroponics/seeds.yml`: meatwheat 2–53, tomato family 517–617, poppy/lily 1159–1238. Blood's continuing edge is 588–598; lily's is 1213–1222.
- `Resources/Prototypes/Entities/Objects/Consumable/Food/produce.yml`: bases 4–44, meatwheat 71–94, tomatoes 896–1057, flowers 1569–1635. Tomato impact 928–934; blue inheritance 958–961 and Destructible replacement 987–999.
- `.../Food/food_base.yml` 4–40; `.../Food/ingredients.yml` 68–79; `.../Specific/Hydroponics/seeds.yml` packet base and leaves.
- Consumers: `Content.Server/Botany/Systems/BotanySystem.Produce.cs` 12–39; `Content.Server/Storage/EntitySystems/SpawnItemsOnUseSystem.cs` 64 onward; `Content.Server/Botany/SeedPrototype.cs` for omitted seed defaults.

Before emitting production profiles, resolve every omitted value from these sources and test local inheritance/runtime consumption. Outstanding composition includes authentic product registration/art, generic bushel use output, tomato impact/spill, juice processing, lily clothing behavior, and the continuing graph's distinct products. Unsupported consumers must be explicit; these leaves must not be flattened into ordinary food to make harvest succeed. No asset delivery/import or owner acceptance is established here.
