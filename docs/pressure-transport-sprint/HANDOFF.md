# Pressure transport — closed

Owner accepted the live result as working well and requested sprint closure. Automated implementation acceptance is complete. The qualitative live acceptance is not a measured station-TPS or visible-front-speed benchmark.

## ARCHITECTURE

Production normal Monstermos uses `MonstermosPressureEqualization`: select regional pressure extrema, find six-face routes, and conservatively mix/forward composition and heat through intermediate cells. Endpoint transfer previews use actual gas Cp, energy and stored volume. Mixed-temperature routes use a bounded scalar solve; isothermal candidates use an exact endpoint solution validated by the same physical preview.

Discovery remains bounded to 2,000 nodes; coherent solve membership is the first 200 BFS nodes, including immutable endpoints. Immutable reservoirs are terminal in both membership and routing, never shortcuts through a finite region. Larger rooms use overlapping solves; omitted finite cells remain eligible. A 400-cell room is not necessarily two disjoint patches, nor one globally balanced operation.

Normal admission checks pressure gradients and face exchange, not the former mole-delta predicate. A committed incomplete patch retains one identity-only continuation for its seed, admitted before fresh work next cycle. It bypasses only the local-gradient check, never loaded ownership/topology safeguards. Same-cycle processed guards remain. Completion clears only the attempted identity; inactivity, exterior classification, chunk unload and level clear prune appropriate identities. Gas snapshots are freshly captured, not replayed across callbacks.

Production LINDA uses `processPressureAware`: archived composition-fraction counterdiffusion, actual donor-energy transport, and equal/opposite conduction using current physical capacities with the existing 0.4 coefficient. Density equalization would otherwise undo equal-pressure hot/dilute and cold/dense states.

Finite state publishes transactionally. Immutable species/energy exchanges, pressure diagnostics and operation counters publish only after successful transactions. Loaded-only ownership, grenade integration and no Minecraft fire blocks remain intact. Keep 15 Hz admissions/resumptions and the shared soft 3 ms stage budget; atomic work can overrun that deadline.

## IMPORTANT FILES

- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/core/MonstermosPressureEqualization.java`: pressure kernel, bounded work, cached pressures/indexed BFS.
- Same core package: `LindaGasSharing.java`, `GasMixture.java`, `GasType.java`, reference Monstermos and Space kernels.
- `.../atmos/world/AtmosphereService.java`: admission, continuations, transaction publication, boundary ledger, committed local reaction observations.
- Same world package: `AtmosphereRuntimeTelemetry.java`, `AtmosphereTransportDiagnosticCommand.java`.
- `src/test/java/.../atmos/core/MonstermosPressure{Equalization,Scale,RepeatedConservation}Test.java`; cycle, LINDA, reaction-commit and telemetry tests.
- `src/gametest/java/.../gametest/AtmospherePressureTransportGameTests.java`: equal-inventory thermal-pressure room and continuous producer.
- `DECISIONS.md`, `VALIDATION.md`, `LIVE-RETEST.md`; prior architecture index `../atmosphere-incident-sprint/HANDOFF.md`.

## SS14 CORRESPONDENCE

Pinned source: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Pressure-based normal transport is an explicitly authorized departure from SS14's mole-equalization target. Source-shaped Monstermos and LINDA remain numerical reference kernels, not competing production modes. Canonical chemistry, actual Cp values, float mixture semantics, 1,000 L cells and centralized 0.4 absolute inventory/energy conversion remain. Space/explosive kernel behavior was not changed by this pressure departure.

## DECISIONS / WHY

- Thermal pressure must move gas even when mole counts match. Runtime admission had retained the old mole threshold, preventing this and dropping low-inventory partial retries.
- Absolute convergence floor is 1e-6 kPa with unchanged relative tolerance: the original 0.1 kPa floor exceeded a 2-mol/124-cell vacuum room's entire equilibrium pressure.
- Count BFS work and actual preview path steps together against 8,000 search units; actual face transfers have a separate 8,000 bound. Preview counters are a subset, not additional search work.
- Conservation tests measure realized producer inputs and count float arithmetic/trace-cleanup opportunities. Signed and absolute incremental drift are tested; opposing errors cannot cancel into a pass. No energy correction is applied.
- Current-cycle committed whole-air events prove local chemistry; transported water or hot metadata alone do not. Existing bounded ignition history keeps distinct whole-air, direct-exposure and neighbor-spread origins.
- Finite ownership is independent of gas inventory. A vented cell can remain claimed vacuum. Entity drying retains its 20-tick cadence; its fixture measures helper ticks. Explosion gameplay is unchanged; actor fixtures isolate vanilla hurt knockback from custom impulse.

## KNOWN GAPS

Wind/entity pressure movement, momentum/CFD, expansion work and generalized explosive discharge into nonzero exterior atmosphere remain outside scope. Quantified station TPS, backlog and visible-front onset were not captured. The owner reported the live result working well.

The 200-node limit was discussed explicitly. A 400-node solve is a reasonable future benchmark candidate, not an approved/current change. Compare convergence, sustained-source fronts and solve-plus-commit costs before increasing it. The separate hotspot first-process correspondence (`CurrentCycle > UpdateCounter` upstream versus LINDA-processed membership locally) remains a parity question, not resolved here.

## VALIDATION

Final evidence: `build/pressure-transport-validation/final-gate-03/`; **1,928 JUnit tests**, zero failures/errors/skips, **76/76 selected GameTests**, default compilation, assembly and diff check passed. Independent reviews passed. Disposable pressure/audit worlds were removed; logs/XML retained.

Warm 200-node-input kernel: **1.345 ms median / 1.728 ms P95**. A 1,000-node input with 200 included costs **2.501 / 4.022 ms**. Equal-40-mol thermal-pressure service fixture reduced span **1,565.45 → 0.220 kPa before GROUPS**, with continued progress under producer writes. These are headless results, not TPS promises.

Rebuilt `build/libs/moonstation14-0.0.1.jar`, 3,923,863 bytes, 2026-10-05 02:08:35 UTC. SHA-256: `4C7460BDB7E60D48030015DFD7667E1596D2BB9BCB5A8107E088B247F9081118`.

## NEXT WORK

A subsequent, separately authorized reversible partial-hotspot spread experiment is now built. See `../hotspot-partial-spread-experiment/EXPERIMENT.md` for its narrow hotspot-only departure, focused validation and newer artifact. The pressure-sprint artifact and evidence above remain historical, not the latest JAR.

Return to entity fire-stack closeout, separately from pressure transport. Core ignition/extinguishing, wet-stack drying and damage integration already exist and FireStack GameTests passed in this gate. Orient from the reagent effect support matrix and `../combustion-reactions-sprint/plan.md`; verify the current implementation against any remaining requested entity-fire scope before calling that separate work closed. No commit or merge was performed by this sprint closeout.
