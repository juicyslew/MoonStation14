# M11 implemented kernels and integration audit

> **Historical M11 baseline — superseded for current cadence, device rates, Monstermos plume behavior, excited-group breakdown, and space-tail details by [M12](m12-cadence-plumes-opacity-and-thermal.md).** The four-tick cadence, 2 mol space minimum, instant group average, and unrestricted normal-route surplus descriptions below record the prior baseline, not current behavior. Keep this note for historical context; use M12 for current facts. Acceptance remains open.

**Status: CODE IMPLEMENTED / ACCEPTANCE OPEN.** This audit records code and focused test-source review plus coordinator-reported validation. It does not close M11 or any M0–M10 gate. The requested pure scenarios and focused tests passed according to the coordinator; `AtmosphereM11GameTests` compiled but have NOT been run in a server world. This docs-only update did not build, run tests, launch a game/server, or benchmark.

## Scope and integration

The M11 code is server-side and introduces no client, sky, or visual behavior. `AtmosphereService` continues to gate all simulation behind `enableAtmospherics`, which defaults false. Within an enabled service, the four-game-tick service cadence, active cell/edge/inspection caps, bounded region discovery and existing priority/FIFO/retry work paths still apply. Normal/exterior Monstermos uses a maximum 800-cell applied patch and up to 8,000 discovery candidates. Unloaded/unknown chunk boundaries are unavailable/closed and do not become vacuum or trigger force loads.

The integration preflights patch state and applies predicted writes transactionally with the existing rollback mechanism. Changed Monstermos patch cells are marked handled for that cycle and requeued; LINDA defers those cells so the same due service tick does not process a changed state twice. A group cycle due in that same service tick is postponed to the next available 12-game-tick window when Monstermos changed cells, avoiding double work on those cells at the cost of exact cadence parity. This documents implemented code, not a blanket guarantee of atomicity under every world mutation/failure condition.

## Kernel behavior recorded from source

### LINDA

`LindaGasSharing` snapshots cycle-start mixtures for comparison and uses sequential live moles for sharing. Active positions are sorted and neighbors follow fixed down/up/north/south/west/east order, making results reproducible but order-dependent. A candidate gas difference must be strictly greater than standard 1 m³ cell moles × 0.001 (~0.0416 mol) and strictly greater than 0.001 times the comparison sample's moles. The sample denominator and zero behavior follow the reviewed upstream convention; the relative comparison is potentially directional/asymmetric. Temperature-only eligibility requires enough gas and a strict archived difference greater than 4 K. Existing same-group pairs bypass the ordinary compare gate.

Per-gas live delta is divided by connected neighbors of the current cell plus one. Heat uses the 0.4 coefficient and live thermal state. Fixed ordering makes behavior reproducible but not order-independent. Species and energy are conserved across the pure closed pair path. This is the explicit selected sequential adaptation, not snapshot commit.

### Normal Monstermos-inspired routing

`MonstermosEqualization` works on a validated connected finite graph. It routes complete donor surplus to takers along deterministic directed connected paths until target patch average or a work limit is reached, carrying species proportions and thermal energy. There is no arbitrary 0.25 mol transfer cap. Closed-path molecule and energy conservation are fixture-checked. It is not an implementation of upstream's tile-level fast/slow equalization internals. Equal-pressure pure oxygen and nitrogen need not mix in this total-moles kernel: per-gas diffusion is LINDA's job; no instantaneous composition mixing is claimed.

Work uses the 800-cell patch cap and an 8,000-candidate discovery ceiling. Discovery/continuation remains owned by the existing bounded world scheduler. Partial/oversized/unavailable regions do not prove whole-room completion.

### Space flow and boundary accounting

`MonstermosSpaceFlow` finds exterior-adjacent finite seeds and routes inventory inward according to graph distance before exporting from boundary cells. Targets are escape ratio 0.15, minimum 2 mol, and a 500 kPa maximum pressure-wind cap approximation. The kernel has a 10 kPa release-pressure threshold, while low-pressure spacing decay remains allowed below it. The condition/cap mapping follows the requested SS14 comparison, but upstream release/cap code and units are inconsistent with this simplified Minecraft representation; this is approximate, not an exact `ReleaseGasTo` reproduction. No pressure wind, impulse, decompression entity effects, or tile ripping is implemented. Finite species/energy lost at the boundary is added exactly once to the existing transient boundary ledger after successful patch application. This ledger is not persisted; existing ledger limitations remain.

### Excited groups

`ExcitedAtmosphereGroups` tracks LINDA-qualified forming/merging transient groups with a cap of 800 tracked cells. Excited groups are enabled by default once atmosphere service is enabled (the overall service remains default-off). They are not simply extra per-tick LINDA time: same-group pairs can bypass the normal comparison gate, and group lifecycle/averaging advances separately once per 12-game-tick full-cycle approximation. A moved amount strictly greater than 10% standard cell moles (~4.16 mol) resets both cooldowns; strictly greater than 0.1% (~0.0416 mol) resets only the dismantle timer. Group averaging is triggered after stage >4 (fifth pass) and dismantling after stage >16 (seventeenth pass). Breakdown averages species and thermal energy with non-all-consuming space semantics; dismantling deactivates membership without averaging. Full group breakdown is kept atomic across its bounded work. Invalidated/unloaded tracked cells clear transient group state. Membership is transient and not persisted; it is rebuilt through subsequent activity.

The service advances groups once per 12 game ticks, roughly 0.8 seconds at 15 TPS, corresponding to three four-tick service due cycles. If Monstermos changed cells during a due group-cycle service call, that group cycle is postponed to the next available 12-tick window to avoid processing those changed cells again immediately. This is a `perFullCycle` approximation to SS14's staged process schedule, not a guarantee of exact upstream full-cycle timing.

## Test-source evidence and what it does not prove

- `AtmosphereM11ScenarioTest` has deterministic 4×4×3 fixtures for mixed-gas injection and connected transport; two-room equal-pressure pure O2/N2 LINDA mixing and fifth-pass group averaging; high-pressure donor/taker routing; one-hole-vacuum vs full-wall/multiple-opening exterior loss and ledger conservation; and fail-closed unknown/unloaded boundary behavior. It checks species/mole and thermal-energy conservation where the modeled boundary is closed or accounts for explicit exports. The coordinator reports the focused fixtures passed.
- Focused test sources include `LindaGasSharingTest`, `ExcitedAtmosphereGroupsTest`, `MonstermosEqualizationTest`, `MonstermosSpaceFlowTest`, `AtmospherePatchKernelSelectionTest`, and the M11 scenario test. `AtmosphereM11GameTests` and `AtmosphereRoomGameTests` were compiled but NOT RUN in a server world. Compilation and pure fixtures are not execution or proof of world behavior.
- Coordinator reports ` .\gradlew.bat test --rerun-tasks --no-daemon` and ` .\gradlew.bat build --no-daemon` both returned `BUILD SUCCESSFUL`; the test rerun executed `compileGametestJava`. This documentation-only update did not execute these commands. No game/server launch or 20-player benchmark occurred.

## Upstream comparison and deviations

Targeted evidence was reviewed at local SS14 checkout revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91` (`C:\Users\William\Documents\SS14-dev\space-station-14`): `AtmosphereSystem.LINDA.cs`, `AtmosphereSystem.Monstermos.cs`, `AtmosphereSystem.ExcitedGroup.cs`, `AtmosphereSystem.Processing.cs`, and `Content.Shared/Atmos/Atmospherics.cs`. This is focused source inspection, not comprehensive parity verification. In upstream LINDA, archived comparison and current live share are separate; normal Monstermos equalizes total moles while LINDA diffuses each gas species. Upstream uses 200 soft / 2,000 hard tiles, whereas this adaptation uses 800 / 8,000 based on Minecraft's four-high cell geometry. The excited-group thresholds originate upstream's 4 / 16 counters; local strict `>4` / `>16` behavior yields fifth / seventeenth counted pass.

Known non-parity / outstanding differences include:

- Minecraft uses six-neighbor 3D 1 m³ block cells vs SS14's 2D grid tiles (approximately 2.5 m³); budget scaling is an approximation.
- Normal Monstermos routing algorithm and per-path transfer schedule are different. The selected LINDA sequential live behavior is order-dependent; it is not snapshot deterministic in the mathematical sense.
- Space max-pressure cap and release pressure mapping are approximate because reviewed SS14 source paths have inconsistent pressure/units. Exact directional pressure wind is absent; no wind forces, tile ripping, decompression impulse, pressure-target effects, or explosive pressure-target mismatch behavior is implemented.
- Space routing's 800 applied-cell segment and 8,000 discovery ceiling cannot guarantee complete-region equalization/drainage; continuation and ownership limits remain. Linked/source pressure and temperature behavior is not a complete counterpart to upstream.
- Group cap 800 can decline formation or merges that exceed the cap. Work scheduling/lifecycle across unloaded members is transient and local, not upstream group persistence.
- The atmosphere service is four-game-tick cadence and group cycles are 12 ticks; upstream runs staged processing around 15 TPS, but stage equivalence is not guaranteed.
- Boundary ledger is transient and not persisted. The world topology/ownership and mapped exterior policies are not equivalent to SS14 map/space ownership.
- No measured 20-player benchmark or proof of ≤3 ms per tick for 800 writes exists. No lungs/respiration, gas composition physiology, pipes, fire, chemistry, or general entity pressure/wind response is added by M11.

## Acceptance still open

1. Coordinator reports the focused scenario and integrated test/build commands passed (see above); this validates pure fixtures and compilation, not world behavior.
2. Execute the compiled server-world GameTests and verify actual ownership, boundary ledger, scheduling/continuation, door/opening and chunk lifecycle interaction. A compile alone does not count.
3. Demonstrate progress/behavior beyond 800 cells and across regions exceeding 8,000 discovery candidates without false whole-room completion; exercise unloaded boundaries and restart state.
4. Obtain owner-only smoke/approval for behavior in a running world: equal-pressure O2/N2 rooms should show species diffusion without wind; a high-pressure/low-pressure case should show a directional plume; compare one-hole vacuum loss with a full wall; check all source/generator registrations; and confirm M11 leaves entity thermal behavior unaffected. Also determine whether the deviations (especially 2D/3D budgets, space pressure mapping and transient ledger) are acceptable.
5. Measure real server cost and representative scale, including the 20-player benchmark and the 3 ms per-tick objective. No such measurements are available.

Until these gates are met, retain **CODE IMPLEMENTED / ACCEPTANCE OPEN**; do not report M11 as accepted or the sprint as closed.
