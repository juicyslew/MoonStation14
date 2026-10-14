# Plant reagent metabolism scope

`PlantReagentMetabolism` consumes `ReagentData.plantMetabolism` from the caller's
resolved reagent catalog; it does not dispatch on reagent IDs. Metadata is
inherited as part of the resolved prototype (for example, a reagent inheriting
`BaseDrink` receives its resolved water effect). Each effect is a fixed dose
applied once per poll when that reagent has at least 1 unit. One unit is then
removed from every reagent, including fractional and not-yet-effecting entries.
Quantities remain authoritative hundredths through `ReagentAttachment`.

The currently supported effect slots are `PlantAdjustWater` and
`PlantAdjustNutrition`. All other metadata effect types are reported explicitly
as unsupported; consumers must not claim those effects are implemented. The
world target adapter owns plant-side behavior, including the upstream water
adjustment's positive-water toxin dilution (`4 * amount`). Upstream reference
is pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`: `PlantHolderSystem.UpdateReagents`
applies each reagent's effects then removes one reagent unit;
`PlantHolderSystem.AdjustWater` adds water and dilutes toxins for positive
amounts. Effect-system adapters call `AdjustWater` / `AdjustNutrient` with the
configured effect amount. Mutation, pests, and harvest-special effects remain
outside this consumer's supported scope.
