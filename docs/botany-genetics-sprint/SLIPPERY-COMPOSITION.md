# Slippery mutation: source mapping

Research only; no persistent mutation-record or produce-contact implementation is established. Reference checkout verified at SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Seed record and produce application

`Resources/Prototypes/Hydroponics/randomMutations.yml:16–20` defines Slippery with base odds `0.036`, `appliesToPlant: false`, and `Slipify`. `Content.Shared/Random/RandomPlantMutation.cs:37–53` defaults `AppliesToProduce`, `AppliesToPlant`, and `Persists` to true. Thus Slippery is a persistent seed mutation record applied to produce, not an effect on the growing plant.

`MutationSystem.CheckRandomMutations` independently rolls entries in configured order using `min(BaseOdds × severity, 1)`. Application to the plant and recording are separate decisions; recording deduplicates by mutation name. Clone retains records. Crossing clones recipient B, independently selects records from each side at 50%, unions, and deduplicates by name (`Content.Server/Botany/Systems/MutationSystem.cs:29–105`; `SeedPrototype.cs:260–315`).

`BotanySystem.ProduceGrown` applies recorded effects gated by `AppliesToProduce` **before** replacing produce solution contents with seed-derived chemicals (`BotanySystem.Produce.cs:12–39`). Preserve this separation: registration of a descriptor is not record persistence or a gameplay consumer.

## Resolved baseline produce composition

These are the actual five local baseline species, not oats or sugarcane:

| Seed | Source product | Parent chain |
|---|---|---|
| wheat | WheatBushel | ProduceBaseRuminant → ProduceBase → BaseItem |
| tomato | FoodTomato | FoodProduceBase → FoodInjectableBase → FoodBase → EdibleBase → BaseItem; additional ItemHeftyBase |
| potato | FoodPotato | FoodProduceBase → FoodInjectableBase → FoodBase → EdibleBase → BaseItem |
| poppy | FoodPoppy | Same food chain |
| aloe | FoodAloe | Same food chain |

`ItemHeftyBase` has no parent or fixture override; it adds damage-on-land/hit, each Blunt 1. The intermediate food parents do not override fixtures, nor do the five concrete products. All inherit BaseItem's hard `fix1` AABB `[-0.25,-0.25,0.25,0.25]`, density 20, ItemMask, restitution `.3`, friction `.2`, dynamic physics and non-fixed rotation.

Sources: `Entities/Objects/base_item.yml:3–41`; `Consumable/Food/food_base.yml:4–40`; `ingredients.yml:68–79`; `produce.yml:4–44,48–69,862–921,1540–1594`, all under `Resources/Prototypes`.

## Slipify and contact defaults

`Content.Shared/EntityEffects/Effects/SlipifyEntityEffectSystem.cs:19–36` ensures Slippery and StepTrigger, assigns slip data, and dirties Slippery. It copies the first hard fixture shape into a non-hard `slips` fixture, density 1, SlipLayer; then ensures CollisionWake and disables wake. Without a hard shape, the components still exist but fixture creation/wake changes do not occur.

`SlipperyEffectEntry` defaults: stun `.5 s`, knockdown `1.5 s`, auto-stand true, launch multiplier `1.5`, required speed `3.5`, super-slippery false, slip friction `.5`. Slippery also defaults friction-status duration `.5 s`, stamina damage 25, and `AffectsSliding=false` (`Content.Shared/Slippery/SlipperyComponent.cs:14–97`).

StepTrigger defaults: active true, intersection ratio `.3`, required speed `3.5`, ignore-weightless false, StepOn false. Admission requires appropriate hard-fixture contact, intersection, speed, and an uncancelled attempt. Contact tracking suppresses repeats until separation (`StepTriggerComponent.cs:24–59`; `StepTriggerSystem.cs:88–190`).

`SlipperySystem.cs:42–155` excludes contained sources and ineligible stun targets, respects NoSlip including feet inventory, and skips already-knocked-down targets unless super-slippery. It multiplies existing velocity rather than inventing a knockback direction. For a newly knocked-down target it drops hands, applies stun/friction/stamina/audio; it then force-applies knockdown. Sliding requires `AffectsSliding && requiresContact`, so do not infer a new sliding requirement from default Slipify.

## Local integration boundary

Reuse `SlipSystem` and its authoritative status/eligibility/response owners where equivalent; current puddle-specific contact admission and `SlidingFrictionSystem` feet queries are not a produce-contact implementation. `LooseItemEntity` has server ticking and stack persistence, but no compatible produce step-trigger path. Audit target eligibility and movement-owned-body authority before extending contact admission.

`ProduceFactory` already persists immutable `GENETIC_VALUE` and `PRODUCE_INSTANCE`. The missing work is seed mutation-record persistence, record-driven harvest composition, and authoritative loose-produce contact feeding shared slip behavior. Do not substitute an inert slippery boolean, arbitrary Minecraft knockback, or chemistry regeneration on later genetics reads. Native movement/contact, repeat suppression, immunity, transfer, and reload coverage remain required; no visual or owner acceptance is established.

### Capability decision

Keep Slippery explicitly unsupported in the current botany implementation rather than enabling a partial response. Inspection of `SlipSystem.admitContact` found additional dependencies: it currently applies stun, velocity scaling, sliding, and knockdown, but not hand drop, stamina damage, or the complete friction-status/auto-stand contract. Default Slipify must **not** set sliding. `SlipAttempt`/`SlipEvent` and separation latches also currently assume puddle sources; `ReactiveTouchSystem` consumes that puddle-specific event.

Existing per-hand transactional drop operations are reusable, but there is no established slip-facing drop-all operation or local stamina resource/consumer. Do not introduce an unrelated stamina framework merely to mark a botany descriptor supported. A future complete Slipify unit must resolve these dependencies, extend the existing slip response owner for both real source types, and preserve accepted player/movement-owned-body authority. Puddle behavior is not silently changed by this deferral.

The pinned active mutation table has **27 entries**, with Slippery the **only persistent** entry (the other 26 explicitly set `persists: false`). This means missing mutation-history behavior is specifically relevant to this deferred produce capability; nonpersistent stat/chemical/gas/harvest effects must not be reapplied to produce. Source: `randomMutations.yml:10–186`, `RandomPlantMutation.cs:37–53`, and `BotanySystem.Produce.cs:12–39`.

## Banana and mimana peel consumers

### Equipment NoSlip dependency

The bounded equipment representation uses optional `no_slip` as presence of the empty upstream
`NoSlipComponent` marker (not a configurable boolean). The pinned shoes chain is
`ClothingShoesGaloshes -> ClothingShoesBase -> Clothing -> BaseItem`; `ClothingShoesBase`
declares FEET, and galoshes adds NoSlip (`Resources/Prototypes/Entities/Clothing/Shoes/specific.yml:107–121`,
`base_clothingshoes.yml:1–27`). Upstream `SlipperySystem.cs:38–49,66–69,98–110,162–175`
relays attempt events to worn FEET slots only. Local slip admission therefore checks the resolved
item profile only in actual LiveHands equipment slots whose declared flags include FEET; it does
not provide an item-contact path or treat held/head/pocket items as protection. Existing character
NoSlip behavior and slip response/latch order remain intact.

`TrashBananaPeel` directly inherits BaseItem; `TrashMimanaPeel` inherits that peel, overriding art/head-clothing appearance and slip sound (`Effects/slip.ogg`, −100dB), not adding an instrument. Both use dynamic physics/disabled collision wake, matching AABB bounds `[-.2,-.2,.2,.2]` for a non-hard SlipLayer fixture and hard ItemMask fixture (density30). StepTrigger overrides intersection ratio to.2; Slipify's usual .3 ratio is not the peel's value. Slippery response defaults remain those above, including AffectsSliding=false. Source: `produce.yml:420–480,507–525`.

They independently contain food4u/PulpedBananaPeel4, special-digestion Edible and food grinding, HEAD clothing with quickEquip=false, but no Seed/Produce. Each fruit separately declares full-eat trash and Knife butchering to its matching peel; chemistry-only profiles or ordinary trash sprites do not satisfy contact/head-clothing behavior.

Oct20 local verification reconfirmed the deferral against code, not only this audit: use accepted player or post-motor movement-owned body paths in SlipSystem, not proposed destinations. Keep puddle-specific ReactiveTouch events isolated from new item contacts. The existing transactional drop reuse target is `BodyHandItemTransfer.forceDropOwned`; no drop-all/stamina owner was found. A future bounded extension must reuse slip eligibility/status/response and existing hand publication, with source-specific contact/rearm, rather than a second banana-specific response or a speculative generic contact/stamina framework. Native fixture/body-authority/collision correspondence remains unverified.

### Current implementation boundary (2026-10-20)

The historical audit/deferral above predates the active item-slip implementation and is not a statement that item contact remains wholly absent. Typed step-trigger/response/composition data, resolved loose-object profile composition, and the shared accepted-player/post-motor item contact consumer are now implemented; see `CURRENT.md` for the checkpoint and canonical validation evidence. This does **not** implement persistent mutation-record capture/acquisition or playable seed/peel bindings. Native fixtures are compiled but unrun; collision/packet/motor/client/NoSlip admission and broader exclusions remain unverified. Packaged slip audio is not gameplay-wired. The original source mapping and its outstanding parity boundaries remain applicable.
