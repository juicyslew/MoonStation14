# M5B typed reagent care effects

Audited upstream revision: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Implemented resolved effects `PlantCryoxadone`, `PlantAffectGrowth`,
`PlantDiethylamine`, `PlantPhalanximine`, and `PlantRestoreSeeds` through the
existing `PlantReagentMetabolism` → tray `SoilTarget` path. Their descriptor
parameters and defaults are codec-backed and inherited by resolved reagent
prototypes. All five authored local seed profiles inherit `seed_profile:defaults`;
none configures immutability. Local profiles therefore remain mutable, matching
the actual current catalog without fabricating an unused immutable property.

Pinned behavior comes from `PlantHolderSystem.UpdateReagents` (threshold,
ordered effects, one-unit drain), `AffectGrowth`, the five corresponding
`Plant*EntityEffectSystem` implementations, and
`SharedEntityEffectsSystem.TryApplyEffect` (scale before probability before
conditions/effect). Probability uses the prior same-holder/tick adapter and is
separate from effect RNG. Cryoxadone's mature deviation draws `[7,10)` only
above maturation and casts after `max`; immature deviation truncates
`maturation / growthStages` and draws nothing. The forced update is consumed by
the existing evaluator cycle path. Growth retains source integer truncation and
its zero-yield/ready branches. Diethylamine makes two sequential independent
10% effect-RNG rolls. Phalanximine compares exact hundredth-backed dose against
`MinScale` before probability. These genetics mutations copy the immutable
instance profile while preserving packet/product, chemicals, gas genes, and
mutation targets.

`UpdateReagents` admits the entire reagent batch only when the solution has
volume and holder mutation level is below 25. The local metabolism adapter
checks this once at poll entry; a rejected poll resolves nothing, runs no effects,
and drains nothing (including fractional reagent). An admitted batch completes
even if an earlier configured effect raises mutation level to 25; normal cycle
mutation consumption then governs a later poll.

Health adjustments preserve ordered overshoot within one reagent batch:
`PlantAdjustHealth` applies amount and immediately invokes `CheckHealth` (death
only at `Health <= 0`), while `CheckLevelSanity` clamps health to endurance
after the full `UpdateReagents` loop. The tray adapter therefore defers the upper
and lower clamp to `SoilTarget.finish`, but marks death immediately so later
care effects are guarded. Cryoxadone's ForceUpdate bypasses only the evaluator's
cycle-time check; known-atmosphere gating and the ordinary cycle owner remain in
place, and the force is consumed once.

Minecraft's immutable genetic profile is value data, not a biological trait;
`ensureUniqueSeed` is the local mutable plant-instance boundary. Restore-seed
feedback reuses the existing shared popup message/actionbar owner after the
authoritative tray poll publishes a real transition. Minecraft actionbar and
chunk-tracking audience are presentation departures from SS14 PVS small-popup
delivery, not world-anchored text or visual parity. `PlantRemoveKudzu`
remains explicitly unsupported: local plants have no kudzu world entity or
corresponding seed flag to clear, and no unused placeholder state was added.
