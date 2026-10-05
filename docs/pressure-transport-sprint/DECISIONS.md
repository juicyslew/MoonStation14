# Pressure-driven transport

## Approved scope

The user explicitly approved an approximate, CPU-bounded pressure-driven Monstermos using actual per-gas heat capacities. The objective is region-wide bulk movement driven by thermal or inventory pressure, not full CFD. Gameplay pacing will be evaluated after the physical movement prototype works. This intentionally departs from SS14's normal mole-equalization target.

## Architecture

- `MonstermosPressureEqualization` is a pure normal-transport kernel. World ownership, loaded-only connectivity, snapshots, transactional publication and visualization remain in `AtmosphereService`.
- Keep normal discovery bounded to 2,000 nodes and solve membership to the first 200 BFS nodes, including immutable endpoints. Large connected rooms use overlapping solves; omitted nodes are not walls. Prove large-area convergence rather than infer it from a two-cell test.
- Select pressure extrema across the included region and route gas through its six-face graph. A route carries gas through intermediate cells, mixing and forwarding actual composition and thermal energy. This is a regional bulk solver, not a replacement name for local LINDA diffusion.
- Scalar route previews estimate endpoint pressure after advective mixing using actual moles, heat capacity, energy and volume. Bounded root calculations determine transfer amounts; a universal Cp or frozen-temperature global pressure target is not used. Heterogeneous Cp can make final pressure fall outside the original min/max interval.
- Count real discovery/search, scalar-preview path steps and actual face transfers. Bounds must include preview work; no precompute-then-dummy-progress delay or repeated across-callback gas snapshot replay.
- Immutable normal endpoints stay unchanged. They may be route endpoints, not transit nodes. Record realized imported/exported species and packet energy, publishing ledger/diagnostics only after finite transaction success.
- Membership discovery also stops at reservoir endpoints. Otherwise a reservoir shortcut can clip a real finite bridge out of the first-200 cohort, leaving pressure extrema unreachable despite a connected original finite region. The U-corridor regression covers this case; omitted finite cells remain eligible for overlapping solves.
- Keep the existing Space/explosive kernel and classification policy separate. Wind/entity movement, momentum fields, adiabatic compression/expansion work and generalized explosive decompression into nonzero exterior atmosphere remain out of scope.

## Necessary LINDA change

The old absolute-inventory exchange can undo equal-pressure states with different temperatures/densities. Production pressure transport therefore uses `LindaGasSharing.processPressureAware`:

- Exchange archived **composition fractions**, not density alone; cap all signed species deltas together against live availability so per-cell mole totals remain unchanged apart from float rounding.
- Carry each transferred species' energy at the current donor temperature and actual molar heat capacity.
- Conduct heat with current physical capacities and equal/opposite energy changes, retaining the existing 0.4 coefficient. Empty immutable heat baths retain the existing virtual-capacity adapter; do not materialize it as gas or finite heat capacity.
- Borrow local archives/processed membership; preserve once-per-edge/cycle processing. Handle live gas introduced after an empty archive without division by zero.

Old source-shaped Monstermos and LINDA entry points remain numerical reference implementations for comparison tests, not an additional competing production solver. Their known archived-capacity raw-energy drift is not the new pressure-mode conservation oracle.

## Numerical and acceptance rules

Use the 1,000 L stored cell volume and centralized 0.4 reference inventory conversion correctly. Preserve actual gas Cp values (tritium 10, oxygen 20, nitrogen 30, water 40, plasma 200, frezon 600 J/mol K); no gas-specific pressure shortcut. Pressure computation itself is cheap; route planning, mixing and world publication need measurement.

Require species and raw thermal-energy accounting with explicitly derived float/operation tolerances, not arbitrary energy correction or loose percentage assertions. Demonstrate equal-mole hot/cold movement, isothermal compatibility, unequal volume, mixed-Cp/vacuum/reservoir behavior, deterministic budgets, connected-area routing, coupled LINDA stability and large-area convergence. Retain the 15 Hz admission cadence and soft 3 ms shared stage budget; measure coherent-operation overruns.

The first integration gate exposed a low-pressure numerical cutoff: 2 mol in a 124-cell vacuum hallway has a uniform pressure of about 0.039 kPa, below the initial 0.1 kPa absolute solver tolerance. That cutoff could declare success with empty distant cells; composition counterdiffusion cannot fill a zero-inventory receiver. Use a much smaller numerical absolute floor (1e-6 kPa) alongside the unchanged relative tolerance and hard work bounds. This is a convergence correction, not an increase to diffusion/fire rates or a return to density equalization in LINDA. Keep the existing far-cell propagation assertion.

Runtime admission must also use pressure, not the old mole-difference gate. Otherwise equal-inventory hot/cold gradients never start, and low-inventory partial solves lose their retries. Normal neighbor admission uses stored-volume pressures and the solver's numerical tolerance, with loaded ownership and face exchange checked first. Immutable Space-exterior activation retains its separate source-shaped policy. Preserve one identity-only continuation for a committed incomplete normal patch: next-cycle admission resumes its global residual even when local neighbor gradients are below tolerance. Do not save/replay old gas snapshots. Same-cycle processed-seed guards still apply; successful completion, terminal inactivity, unload and clear retire the appropriate identities.

Continuations precede fresh work to avoid starvation under continuous producers. Completion retires only its attempted identity, not identities merely included in an overlapping patch: different BFS cohorts need their own fresh completion check. Gas inventory remains independent of finite ownership; a vented cell can contain vacuum while its claim and strict sample remain valid.

Isothermal routes can use the exact endpoint mass solution, validated with the same actual-Cp scalar preview before publication. Mixed-temperature routes retain their bounded coupled solve. This reduces work rather than raising physical rates or computational budgets.

Long-run audits measure realized producer input before transport and use cumulative **committed** pressure-face, LINDA-pair and averaged-member counts. Float-error allowances derive from counted arithmetic and separate sub-cutoff cleanup opportunities. Audit incremental stage deltas and their absolute sums as well as final signed drift; opposing errors must not cancel to produce a false pass. These diagnostic counters do not alter simulated energy or inventory.

Implementation passed automated acceptance: 1,928 JUnit tests, 76 selected GameTests, default compilation and assembly; independent reviews passed. Evidence and rebuilt artifact: `VALIDATION.md`. Automated correctness and headless costs do not constitute live gameplay acceptance.

The owner subsequently reported the live result working well and requested closure. Sprint closed with qualitative live acceptance; durable architecture, remaining measurement limits and next work are indexed in `HANDOFF.md`. The discussed 400-node limit remains a future benchmark candidate, not a change made in this sprint.
