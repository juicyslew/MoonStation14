# Plant reagent metabolism scope

`PlantReagentMetabolism` consumes `ReagentData.plantMetabolism` from the caller's
resolved reagent catalog, including inherited metadata. Effects run in configured
order at fixed dose once per poll when a reagent has at least one unit; one unit
is drained from each reagent, including fractional/not-yet-effecting entries.
Quantities remain authoritative hundredths through `ReagentAttachment`.
Holder mutation level 25+ rejects the entire reagent poll before lookup, effects,
or drain; this admission is checked once at poll entry.

Supported effects: `PlantAdjustWater`, `PlantAdjustNutrition`,
`PlantAdjustHealth`, `PlantAdjustToxins`, `PlantAdjustWeeds`,
`PlantAdjustPests`, `PlantAdjustPotency`, and `RobustHarvest`. Other metadata
effects remain explicitly unsupported. Upstream pinned reference
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`PlantHolderSystem.UpdateReagents` applies effects then drains each reagent;
`AdjustWater` also reduces toxins by `4 * positive amount`.

Soil values buffer ordered changes and clamp after polling. Health changes remain
unclamped through the reagent batch, but death is checked immediately; later
effects cannot revive a plant. Endurance/zero clamping follows the batch. Potency
and robust-harvest changes update immutable per-instance genetics
without replacing unrelated profile fields or producing harvest output. Robust
harvest defaults are limit 50, increase 3, seedless threshold 30.

Source `SharedEntityEffectsSystem.TryApplyEffect` evaluates probability before
conditions from a predicted sample keyed by tick, target, and user, so a
same-tick target's effects share their probability sample independently of the
effect RNG stream. Minecraft adapts entity identity to server game tick,
dimension, and tray block address. This preserves same-holder/tick coherence but
does not claim bit-identical PRNG output.

The additional implemented care effects (`PlantCryoxadone`, `PlantAffectGrowth`,
`PlantDiethylamine`, `PlantPhalanximine`, `PlantRestoreSeeds`) and their current
catalog departures are recorded in
[`docs/botany-genetics-sprint/M5-CARE-COMPOSITION.md`](../botany-genetics-sprint/M5-CARE-COMPOSITION.md).
