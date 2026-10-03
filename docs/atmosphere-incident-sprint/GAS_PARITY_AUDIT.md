# Gas-simulation parity audit

## Scope and conclusion

Read-only comparison of the current Minecraft tree against SS14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. This covers room gas representation, transport, heat, boundaries, scheduling, groups, and chemistry; pressure effects and absent pipe systems are identified separately. No implementation changes or new test-server runs were made for this audit.

**A foundational parity pass is required before accepting further fire behavior as SS14-faithful.** Existing tests establish substantial local correctness and regression safety, but mostly test the local model rather than outputs obtained from SS14. This inventory identifies source-level differences; it is not an exhaustive differential proof of every reachable state.

## What already corresponds

- Both use ideal-gas pressure `nRT/V` and species-dependent heat capacity. Local pressure is converted to kPa from SI volume; upstream uses liters and kPa directly.
- The same nine gases and raw molar heat capacities are present: oxygen 20, nitrogen 30, CO₂ 30, plasma 200, tritium 10, water vapor 40, ammonia 20, N₂O 40, frezon 600 J/(mol·K).
- Normal Monstermos aims to redistribute **total moles**, not directly equalize pressure or species. This upstream limitation is not a port-specific omission of realistic pressure-driven thermal advection.
- LINDA species transfer uses live mole differences divided by the active receiver's open-neighbor count plus one. The absolute comparison threshold scales with standard cell inventory: upstream approximately 0.10393 mol for 2,500 L, local approximately 0.04157 mol for 1,000 L. Both also require the 0.001 relative comparison. The separate 5e-8 mol transfer cutoff must not be confused with the activation threshold. The >4 K temperature activation gate likewise corresponds after volume scaling.
- Normal finite equalization now permits subsequent LINDA work in the same due step. The recent blanket-deferral starvation defect was repaired; boundary-kernel exclusion remains.
- Plasma/tritium reaction extent formulas, products, and nominal heat quantities largely correspond. Tritium consumes **0.5 mol O₂ per mole burned**, not two. Both snapshot reaction temperature/energy gates at the start of an evaluator pass.

## Representation and numerical differences

| Area | SS14 | Current port | Significance |
|---|---|---|---|
| Cell geometry/volume | Grid tiles, normally 2,500 L | Six-face 3D Minecraft cells, 1,000 L | Necessary geometry adaptation; absolute inventory/energy thresholds must be converted consistently. |
| Mixture volume | Stored on each mixture; volume removal supported | Pressure callers supply volume; room simulation assumes 1 m³ | Detached hotspot fractions do not carry upstream's volume representation. |
| Storage | Mutable float arrays with immutable-map guards | Immutable Java objects and doubles | Valid architectural substitution, but not bitwise numerical equivalence. |
| Temperature limits | Setter clamps to 2.7–262,144 K | Any finite nonnegative temperature; empty vacuum factory uses 2.7 K | Different extreme-fire and cooling outcomes. |
| Tiny species | `RemoveRatio` clears residual and removed species below 5e-8 mol | Scaling/packet merging retain positive microscopic amounts | Trace tails can persist/propagate differently. |
| Thermal representation | Heat capacity and reaction heat normalized by `AtmosHeatScale`, default 8 | Unscaled heat capacity and joules | Usually equivalent when the factor cancels; absolute gates and external coupling require explicit conversion. Not automatically an eightfold heat error. |
| Merge tolerances | Weighted temperature update has 0.01 K / 0.0003 capacity gates | Solver merges explicitly sum energy and derive temperature | Differences near negligible temperature/capacity limits. |

Sources: local `atmos/core/GasMixture.java`, `GasType.java`; upstream `Content.Shared/Atmos/GasMixture.cs`, `Atmospherics.cs`, `SharedAtmosphereSystem.Gases.cs`, `CCVars.Atmos.cs`.

## Transport, groups, and boundaries

### LINDA heat sharing — material difference

Upstream archives each tile once per atmosphere cycle. Species flux uses live inventories, but carried heat and conduction use archived temperatures/capacities. Conduction also depends on heat-capacity-change and minimum-capacity conditions.

Local LINDA creates snapshots for each kernel invocation. Qualification uses those snapshots, but each pair transfers energy using its current live donor temperature, then conducts using live post-transfer temperatures/capacities:

`Q = 0.4 × (T1 − T2) × C1 × C2 / (C1 + C2)`.

It does not reproduce all upstream conduction eligibility conditions. Thus the shared coefficient 0.4 is **not** enough to establish equivalent multi-neighbor heat evolution. Local processing is deterministic, sequential, and source-count-bounded; upstream retains archives and processed-cycle identity across resumptions.

Sources: local `LindaGasSharing.java:57–79, 102–173`; upstream `AtmosphereSystem.LINDA.cs:117–126, 194–350`, `AtmosphereSystem.Gases.cs:245–277`.

### Monstermos — corresponding aim, different solve

Local uses donor-rooted BFS routing toward an 800-cell patch mean, with 8,000 search expansions and 8,000 edge-transfer ceilings. It can publish bounded partial progress and continue later. Upstream has 200/2,000 soft/hard discovery limits and different routing/progression. Equal target inventories do not establish equivalent packets, composition movement, activation, or wall-clock behavior.

Sources: local `MonstermosEqualization.java`, `AtmosphereService.java:1060–1258`; upstream `AtmosphereSystem.Monstermos.cs`.

### Excited groups — important room-mixing difference

An excited group is a connected collection of cells tracked while exchanging gas. Upstream can forcibly homogenize its gas after breakdown cooldown >4; it does **not** require the group already to be nearly uniform. Significant sharing can reset that cooldown, so averaging is not guaranteed after five wall-clock steps.

Local adds a requirement that every species be within 5% of standard-cell inventory and temperatures within 4 K before averaging. Otherwise it resets the breakdown cooldown. This can retain gradients that upstream group breakdown would remove. Local group cycles occur every 12 Minecraft ticks, not every completed upstream atmosphere update. Both ordinary group averages use heat-capacity-weighted temperature, not arithmetic temperature averaging. Upstream also has configurable space-all-consuming group behavior without the same local analogue.

Sources: local `ExcitedAtmosphereGroups.java:106–178, 214–242`, `AtmosphereService.java:1443–1457`; upstream `AtmosphereSystem.ExcitedGroup.cs:112–162`, `AtmosphereSystem.Processing.cs:350–386`.

### Exterior/vacuum transport — custom local mechanisms

- Upstream immutable, non-space map air participates in normal LINDA species/heat exchange. Pressure difference alone is not species advection.
- Local ambient patches instead use pressure-directed packets, a 48-iteration equilibrium bisection, half-relaxation, and a 500 kPa packet-pressure cap. Direct boundary exchange uses a separate 0.125 policy.
- Local vacuum transport requests 15% of initial moles plus transit inflow, retains 0.6 mol in transit cells, and clears small residues at 1e-6 mol. These are not literal upstream space-depressurization equations.
- Upstream `Space` is independent of its gas mixture. Its explosive branch passes a mole-derived quantity to `ReleaseGasTo` as **target pressure**; the callee converts pressure to moles. The shared 0.15/500 constants do not make local transport equivalent. Finite ambient can reject the upstream transfer. Do not claim upstream guarantees immediate high-temperature decompression.

Sources: local `MonstermosSpaceFlow.java`, `ImmutableAtmosphereBoundary.java`, `AtmosphereService.java:1526–1540`; upstream `AtmosphereSystem.Map.cs:37–75`, `AtmosphereSystem.LINDA.cs:215–320`, `AtmosphereSystem.Monstermos.cs:495–553`, `AtmosphereSystem.Gases.cs:139–164`.

## Minecraft world integration and scheduling

- Passability is a whole-cell approximation: open doors/trapdoors/gates are fully passable; other partial collision shapes are not directional permeability models.
- Ownership uses a heightmap plus loaded-connectivity proofs. Finite claims persist through temporary solidity. Unknown cells return presentation fallback but are not simulation gas. This is Minecraft-specific ownership machinery, not SS14 grid/map tile identity.
- Every non-vacuum dimension defaults to 101.325 kPa, 293.15 K breathable air. Configuration supports a vacuum-dimension list, not upstream map composition/temperature/independent `Space` profiles.
- Local runs nominally 10 atmosphere steps/s, admits ten LINDA sources and eight reaction evaluations per due step, and advances group work on its separate 12-tick clock. Discovery is resumable by count, but an admitted whole-patch solve is synchronous.
- Upstream defaults to 15 atmosphere steps/s with a 3 ms staged/resumable budget. Stages, shared archives, and cycle counters survive yields. Minecraft's 20 server ticks/s do **not** force the local 10 Hz or count-only design.
- Local stage sequence and independently ticking test devices/external writers are not an upstream complete grid processing cycle.

Sources: local `AtmosphereTopology.java`, `AtmosphereProfiles.java`, `AtmosphereService.java:59–79, 780–805`, `AtmosphereChunkData.java`; upstream `AtmosphereSystem.Processing.cs`, `MapAtmosphereComponent.cs`, `CCVars.Atmos.cs`.

## Chemistry and fire-dependent differences

- Absolute 0.01 mol reactant requirements, 0.0003 mol plasma/coolant cutoffs, and tritium's 143,000 J branch threshold remain unscaled. With 40% of upstream tile inventory at equal concentration, these select different boundary cases.
- Upstream reactions run in one priority-ordered pass on the same mixture. Local executes non-fire reactions on the whole mixture before a separate hotspot/fire pass. Equal-priority local ties are additionally sorted by prototype ID.
- Local ammonia oxidation and frezon production preserve thermal energy when composition changes, then recalculate temperature. Upstream changes composition while retaining temperature. This creates additional local cooling, especially when producing high-heat-capacity frezon. N₂O's current reactant/product capacities happen to balance, so the same representation choice need not create an observable temperature difference there.
- Local coolant rejects an operation that would produce negative energy; upstream consumes reactants and follows its own temperature/capacity/clamp path. The extra ammonia oxygen clamp is redundant for valid nonnegative mixtures; it is not an established behavioral mismatch.
- Hotspots add further deviations: 10% local seed, no neighbor ×25 exposure-volume behavior, additive rather than assigned growth, explicit source heat affordability, uniform-composition energy projection, and no equivalent upstream first-process flag. These should not be accepted merely because local ledgers and propagation fixtures pass.

Sources: local `GasReactionEvaluator.java`, `GasReactionStep.java`, `HotspotKernel.java`, `HotspotSpread.java`, bundled reaction JSON; upstream `AtmosphereSystem.Gases.cs:280–314`, `AtmosphereSystem.Hotspot.cs`, `Content.Server/Atmos/Reactions/`, `Resources/Prototypes/Atmospherics/reactions.yml`.

## Missing adjacent upstream systems

- Pressure-driven object movement/wind is absent locally. Existing barotrauma is not that system. Upstream pressure effects are separate from transporting the gas; they do not add a CFD-like advection rule.
- Solid/gas superconductive heat transfer is absent locally. It is **disabled by default upstream**, so its absence must not be used to explain default LINDA failures.
- No pipe-network/pump/scrubber transport was found in the local atmosphere subsystem. Current producers/sink/heater/cooler are test devices, not an SS14 device-network port. The 20 mol/s / 202.65 kPa cap belongs to the test producer, not every gas input. Upstream miner baseline is 400 mol/s with 300 kPa cap; flow-plus-cap semantics correspond, values do not.

## Required parity pass

1. Establish upstream-derived reference cases and a documented 1,000 L conversion contract: mixture operations, thresholds, reaction gates, and thermal quantities.
2. Align atmosphere-cycle/archive semantics, then LINDA species/heat sharing and group behavior. Test multi-neighbor order and opposing species gradients.
3. Align normal routing and explicit map/space boundary semantics. Any retained physical or safety deviation needs explicit documentation rather than a parity label.
4. Align cadence, resumable stages, queue/cooldown progression, and external writer/device ordering sufficiently to reproduce the same elapsed-time behavior.
5. Correct reaction thermal semantics, ordering, and threshold conversion; then implement faithful hotspot exposure/partition/growth/skip behavior on that foundation.
6. Retain conservation, loaded-cell, transaction, and budget tests as complementary safeguards. Run focused Minecraft room/door/breach/fire fixtures after numerical reference tests; live-station acceptance remains separate.

Current bisection oracles validate **local** boundary policy; legacy BFS comparisons validate **local** routing. The 1,821 JUnit / 73 focused GameTest gate is not an upstream differential parity gate.
