# Intentional departures from SS14

This records accepted design choices intended to remain—not unfinished ports, temporary workarounds or known parity bugs. SS14 parity remains the default outside these choices. Implementation details and tuning may evolve without changing the underlying decision.

Source comparisons currently reference SS14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

## Pressure-driven normal gas equalization

**SS14:** normal Monstermos primarily equalizes gas inventory between tiles.

**Our design:** normal Monstermos routes gas between regional pressure extrema. Transfers account for composition, temperature, actual gas heat capacities and cell volume. Intermediate cells mix and forward gas and thermal energy; the solver does not simply average room temperatures. Normal work admission likewise uses pressure rather than the source mole-difference threshold.

**Why:** similar mole counts can conceal enormous pressure differences. Hot gas should drive bulk movement into surrounding cool gas, particularly during grenade gas release and combustion.

This replaces normal transport behavior, not the separate Space/explosive-flow kernel or reaction chemistry.

References: [pressure decisions](pressure-transport-sprint/DECISIONS.md), [handoff](pressure-transport-sprint/HANDOFF.md).

## Pressure-compatible composition diffusion and thermal exchange

**SS14 reference behavior:** LINDA includes absolute-inventory sharing and archived-capacity thermal calculations.

**Our design:** production LINDA counterdiffuses composition fractions rather than restoring equal inventory. Transferred species carry energy at the donor's temperature and actual heat capacity. Conduction uses current physical capacities with equal-and-opposite energy changes; the existing conduction coefficient is retained.

**Why:** cool dense gas and hot dilute gas can legitimately have equal pressure. Absolute-inventory equalization would undo that balance. Physical energy accounting also avoids treating the reference kernel's archived-capacity energy drift as intended production behavior.

Source-shaped kernels remain numerical comparison implementations, not alternative production modes. Float rounding and source trace cleanup still have explicit numerical allowances.

Reference: [pressure decisions](pressure-transport-sprint/DECISIONS.md).

## Earlier neighbor exposure from partial hotspots

**SS14:** neighbor exposure is restricted to full-tile/bypassing hotspots with sufficiently hot bulk tile gas.

**Our design:** an already-first-processed partial hotspot may expose adjacent cells after a positive local combustion pass. Its local flame temperature determines eligibility and exposure temperature. Full-cell hotspots retain bulk-temperature behavior.

**Why:** a flame occupying part of a cubic metre should be able to affect adjacent combustible gas before almost the entire source cell is burning or its bulk gas has heated sufficiently. The owner live-tested the change and reported faster, satisfactory spread.

This does **not** change tritium chemistry, oxygen requirements, reaction heat or chemical ignition thresholds. Existing neighbor exposure strength, temperature attenuation, receiver processing and deferred processing remain. The 95% threshold still selects partial-versus-whole-air processing; it is no longer a universal prerequisite for neighbor exposure. Water vapor remains a combustion product, not an ignition trigger.

Reference: [partial-hotspot change and validation](hotspot-partial-spread-experiment/EXPERIMENT.md).

## Three-dimensional atmosphere geometry and cell units

**SS14:** atmosphere topology is based on two-dimensional map/grid tiles, with a 2,500 L reference cell.

**Our design:** Minecraft gas occupies three-dimensional, six-face-connected cells, normally 1,000 L per block. Enclosure and exterior ownership use Minecraft's loaded voxel topology rather than SS14 floor identity. Unloaded areas are unresolved boundaries, not assumed vacuum, and simulation does not force-load them.

**Why:** these are adaptations to Minecraft's physical geometry and chunk lifecycle, not placeholders for a future 2D simulation.

Reference-cell absolute inventory and energy quantities use the centralized **0.4 conversion**. Temperature, pressure, ratios and per-mole heat capacities are not scaled. This preserves concentrations and canonical chemical properties rather than arbitrarily weakening reactions.

References: [mixture/unit contract](atmosphere-parity-sprint/UNIT-AND-MIXTURE-CONTRACT.md), [geometry decisions](atmosphere-parity-sprint/DECISIONS.md).

## Inclusion rule

Missing systems, deferred parity work, unresolved bugs and unapproved proposals are not accepted departures. This document does not make current solve-size tuning permanent, endorse unresolved hotspot first-process differences, or classify unimplemented atmosphere features as intentional omissions. Add another entry only when its design difference is explicitly chosen and intended to remain.
