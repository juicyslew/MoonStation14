# Water terrain feasibility and SS14 parity

Research only; no implementation authorized or performed. Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Upstream paths below are relative to that checkout. Findings describe this revision, not every SS14 fork or later version.

**Subsequent owner requirement:** a static surface alone is not acceptable for the 3D experience. If water is implemented, it needs swimmable depth, containment, flow into newly opened cavities and leveling, potentially thinning into puddles. The bounded static-surface recommendation below therefore does not satisfy the current brief; retain it as the original SS14 comparison, not an approved design. The owner explicitly permits a deliberate departure from SS14 for these physical requirements. See [SS13-COMPARISON.md](SS13-COMPARISON.md) for static-water, finite-fluid and Z-level transfer references. No implementation decision has been made.

## Water terrain and puddles are different systems

`FloorWaterEntity` in `Resources/Prototypes/Entities/Tiles/water.yml:2-65` is an anchored entity with no parent. It is placed over an underlying grid tile, including in Box, Fland, Oasis and other maps. Procedural biome templates also place it over grass/dirt (`Resources/Prototypes/Procedural/biome_templates.yml:157-175`). No deep/shallow water family was identified.

Its composition supplies:

- A fixed `Tiles/Planet/water.rsi` shoreline sprite, not reagent-derived appearance.
- A `pool` solution initially containing 9,999,999u Water, with matching capacity and `DrainableSolution` capability.
- Contact walk/sprint multipliers of 0.5 and water footstep sounds.
- A non-hard static fixture and a step-triggered `Extinguish` entity effect. The trigger requires no minimum speed and uses intersection ratio 0.1; Catwalk-tagged entities are blacklisted.
- No `Puddle`, `RefillableSolution`, `MixableSolution`, `DrawableSolution`, `Edible`, `Damageable`, `Destructible`, `Flammable`, or `RequiresTile` component.

This is not a general chemically configurable lake. Replacing the stored reagent in a hypothetical derived prototype would not automatically change the fixed artwork or extinguishing behavior. Normal gameplay does not expose arbitrary mixing into this source.

By contrast, `Puddle` is finite, mixture-aware, drawable/mixable and spreadable. Its base solution capacity is 1,000u and overflow threshold defaults to 50u. It spreads excess among eligible neighboring cells and supports reagent-dependent evaporation. Water has evaporation speed 0.3; non-evaporating mixture contents can remain after water evaporates. Empty puddles are deleted. See `Resources/Prototypes/Entities/Effects/puddle.yml`, `Content.Shared/Fluids/Components/PuddleComponent.cs`, `Content.Server/Fluids/EntitySystems/PuddleSystem.cs`, and `Content.Shared/Fluids/SharedPuddleSystem.Evaporation.cs`.

## Collection and depletion

Compatible held containers can fill from the terrain source through the ordinary drainable-to-refillable interaction. Transfer is bounded by requested amount, remaining source volume and receiver capacity. A bucket holds 250u and transfers 100u per configured operation; source/receiver drain/refill delays default to zero. This is reagent transfer, not scooping a cubic metre of terrain.

The reservoir is technically exhaustible but intended to be practically unlimited: the prototype comment explicitly says infinity would be preferable. At 100u per bucket transfer, one full tile reservoir allows 99,999 complete transfers and one partial transfer. Depleting it does not remove the fixed sprite, movement slowdown or other terrain components.

There is a source discrepancy: `SolutionRegeneration` generates 100u Water per default one-second cycle into a solution named `tank`, but this entity only declares `pool`. Solution lookup does not create missing solutions; regeneration skips when lookup fails. Consequently this prototype does not successfully replenish its drainable pool. Treat that as observed behavior, not an automatically corrected design requirement.

Ordinary pouring into terrain requires a missing refillable capability. Syringe drawing requires drawable capability; direct drinking requires edible capability. Neither is configured. Generic solution APIs could modify the store, but that is not an existing player interaction.

References: `Content.Shared/Chemistry/EntitySystems/{SolutionTransferSystem,SharedSolutionContainerSystem,SharedSolutionContainerSystem.Capabilities,SolutionRegenerationSystem}.cs`; `Content.Shared/Chemistry/Components/SolutionTransferComponent.cs`; `Resources/Prototypes/Entities/Objects/{Consumable/Drinks/drinks_base,Tools/bucket}.yml`.

## Other systems

| System | Verified behavior |
| --- | --- |
| Movement | Contact slowdown, not a depth-based swimming/buoyancy simulation. No swimming or drowning implementation was found in the bounded pinned-source investigation. |
| Burning entities | An eligible step contact applies the Extinguish effect, adjusting fire stacks by -1.5 at default scale. Step triggers latch contacts; do not apply this every Minecraft tick merely because a body overlaps the surface. |
| Atmospheric fire | Separate from terrain contact: spilled Water's tile reaction can cool/extinguish an atmospheric hotspot. Do not confuse this chemical reaction with the terrain's stepping effect. |
| Atmosphere | Water terrain supplies no airtight component or separate liquid-volume atmosphere model. Underlying map/tile atmosphere remains relevant; this is not a hull seal or displaced-gas simulation. |
| Explosions | Ordinary entity damage requires Damageable, which water lacks. Floor-tile damage replaces the underlying tile, not the water entity. Tile-change deletion requires RequiresTile, also absent. Thus ordinary explosion floor damage does not destroy the water entity. Entire-grid/entity deletion is a separate operation. |
| Heat/freezing/electricity | No terrain-water thermodynamic, phase-change or conductive-pool model was established. Do not infer one from the Water reagent's chemistry. |
| Fishing | Not found in the pinned upstream investigation. Fishing code was identified in the Goobstation fork under `Content.Shared/_Goobstation/Fishing/Systems`; that does not make it part of this parity baseline. Swimming provenance in other forks/revisions remains unverified. |

Key consumers: `Content.Shared/StepTrigger/Systems/StepTriggerSystem.cs:88-150`; `Content.Server/Tiles/TileEntityEffectSystem.cs`; `Content.Shared/EntityEffects/Effects/Atmos/ExtinguishEntityEffectSystem.cs`; `Content.Server/Chemistry/TileReactions/ExtinguishTileReaction.cs`; `Content.Server/Explosion/EntitySystems/ExplosionSystem.Processing.cs:438-576`; `Content.Server/Tiles/RequiresTileSystem.cs:24-39`.

## Minecraft adaptation and existing reuse

A parity-oriented implementation would be a custom shallow/static surface, not vanilla FluidState water. It would need resolved surface composition, fixed artwork/footsteps, finite source storage and ordinary container-transfer admission, contact extinguishing and authoritative 0.5 walk/sprint modifiers. Explosion handling must preserve the surface independently of the supporting floor. Gas occupancy, source lifetime and floor replacement need explicit adaptations rather than copying puddle-block behavior blindly.

Existing reusable owners include `LiquidInteractionCapability` and reagent transfer units/stores, `FireStackSystem.applyExtinguish`, and step-contact geometry/latching. `PuddleBlockEntity` already supports finite mixtures and spreading; `ReagentSystem.handleSpill` serves spill placement/merging. Local puddles use a raised overlay block and a distinct contact/slip path, so they should not become the water-terrain owner.

New boundaries are surface-specific contact admission, effective walk/sprint speed integration, terrain source interaction/presentation and explosion/topology lifecycle. The custom grounded motor currently rejects vanilla water; a custom shallow surface might avoid requiring swimming, but its admission and gas-volume effects still require verification before implementation. The 9,999,999u source fits current integer-cent storage: 999,999,900 cents.

## Recommendation

Keep arbitrary large reagent spills in the existing finite-puddle model. Do not build conserved 3D liquid flow, hydrostatic pressure, swimming or fishing merely to reproduce pinned SS14 water; those are separate extensions.

Defer terrain water unless maps or gameplay need its static slow/extinguish/collection surface. If desired later, a bounded static-surface adaptation is moderate work; a volumetric fluid simulation would be high-cost and not required for parity. Decide explicitly whether to preserve the near-infinite reservoir and observed regeneration mismatch, rather than silently inventing a different economy or claiming exact parity after changing them.
