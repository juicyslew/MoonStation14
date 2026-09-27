# M3 Audit — Dynamic Space and Finite Ownership

**Date:** 2026-09-27
**Scope:** Source review of dynamic sky/exterior ownership, persistence, claim scheduling, the gas solver, and atmosphere diagnostic readouts/device rules. This documents implementation and risks; it is not a server-world test report or acceptance decision.

## Summary

The heightmap is now an **exterior seed only** for unclaimed sky-visible cells. A covered unclaimed cell is resolved with resumable six-face `AtmosphereOwnershipSearch`: connectivity to an open sky seed yields exterior; only a complete, loaded, fully enclosed proof yields persistent FINITE claims. Unknown unloaded boundaries and saturation fail closed. Ownership remains local topology state, not a permanent station envelope, room identity, or room column.

Persistent finite claims override the heightmap, including after a door breach. Schema-v1 stored gas overrides are promoted to finite claims when decoded into schema v2. Direct sky claim stays finite to avoid deleting gas instantly; adjacent open sky is still an exterior exchange sink. Under-overhang cells that connect to sky are exterior, not automatically finite.

This audit establishes source behavior only. Roof/door service GameTests **COMPILED but were NOT EXECUTED**; there was no manual game/server launch. No manual owner smoke, full SS14 parity, acceptance, or 20-player benchmark is claimed.

## Diagnostic readings, door cells, and device rates

Strict `AtmosphereService.sample` is deliberately fail-closed: a loaded passable cell with UNKNOWN ownership has no strict sample. Writes and simulation continue to rely on strict sampling/finite ownership, so a diagnostic fallback cannot cause gas mutation or device operation in an unproven cell; device changes are blocked until ownership is proved. The separate `readAtmosphere` presentation path returns an `AtmosphereReading` marked PROVISIONAL for a loaded passable UNKNOWN cell, using stored gas when present or dimension ambient otherwise. Any displayed numeric PROVISIONAL/FALLBACK value is not authoritative physical state: it does not claim ownership or write a fallback into chunk data. Blocked, unloaded, out-of-bounds, and disabled positions have no diagnostic reading.

A solid closed door's full-collision block position is not passable and therefore has no gas-cell reading. Analyzer targeting on an impassable clicked block tries the clicked-face adjacent cell; if that side is not a loaded passable gas cell, the user receives an explicit no-gas-cell-on-clicked-side response instead of a fabricated door atmosphere. This target policy is not a scan through a door. A future full-tile airlock model remains possible but is not current behavior.

The four devices remain gated and tick at one-second (20 game tick) cadence: producer up to 20 mol/s with partial dosing against the 202.65 kPa cap; proportional sink up to 20 mol/s; heater +40,000 J/s; cooler up to -40,000 J/s with a 2.7 K lower bound. The producer may therefore apply a partial dose near the pressure cap. These are source/rule behaviors, not manually verified world effects.

## Code reviewed

- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereService.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereOwnershipSearch.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/OwnershipClaimPlan.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereChunkData.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereTopology.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/core/ImmutableAtmosphereBoundary.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/core/GasMixture.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereReading.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/device/AtmosphereDeviceRules.java`
- `src/main/java/com/juicyslew/moonstation14/block/custom/AtmosphereTestDeviceBlock.java`
- `src/main/java/com/juicyslew/moonstation14/item/custom/AtmosphereAnalyzerItem.java`
- Focused ownership, activation, exterior, scheduler, claim-plan, and chunk-data tests were source-inspected; no test command was run for this documentation task.

## Ownership and persistence behavior

1. A loaded, passable cell at/above the chunk's `MOTION_BLOCKING` first-free Y is an open-sky seed. Heightmap does not inspect skylight; a roof by itself is not a finite ownership proof.
2. An unclaimed covered position seeds `AtmosphereOwnershipSearch`, which explores passable neighbors across six faces. Finding a sky seed produces EXTERIOR; blocked cells and already FINITE claims are barriers. Only exhaustion with no sky seed and no unresolved unloaded boundary produces FINITE.
3. `UNKNOWN_UNLOADED` boundaries park the search instead of certifying a finite component. A candidate-cap result is SATURATED and likewise fails closed. Ownership candidate cap is 131,072.
4. The ownership service probes at most 768 candidates and commits at most 256 finite claims per due tick. Claim commits are staged, not a single whole-component persistent transaction.
5. Chunk schema v2 stores finite claims separately from gas overrides. Claims apply to ambient-valued cells, persist across restart, and take precedence over sky exposure. Schema-v1 overrides are deliberately migrated as finite claims, including old overrides that would now be sky-exposed, to avoid silently deleting legacy gas.
6. A breach does not revoke claims: claimed cells remain finite and drain through open adjacency. Direct-sky claimed cells are also kept finite so their stored gas is not immediately erased; open adjacent sky behaves as sink. The boundary ledger is transient and not persisted.

There is no fixed station envelope, room column, or exact SS14 floor/map identity. A connected sky path can pass beneath an overhang. Heightmap seeding may misclassify custom geometry; a large finite station may remain UNKNOWN if it touches unloaded chunks, or SATURATED if the search cap is reached.

## Gas processing is a separate stage

`enableAtmospherics` is the one common atmosphere enable gate and defaults off; ownership discovery and gas processing are separate internal stages, not independently configured switches. When enabled, bounded Monstermos-inspired gas patches process up to 800 cells per patch with an 8,000-candidate discovery limit, alongside local six-neighbor species/heat exchange. Those limits and ownership budgets do not prove room-scale solver progress, fairness, or acceptable performance. Per-chunk work is coalesced; priority seeds and bounded retry/work paths remain subject to their own overload and memory risks.

## Explicit limitations and risks

- A proof cannot cross unloaded/unknown cells or exceed the search cap. Bounded UNKNOWN/SATURATED searches release their active job so other room seeds can proceed; the affected region remains unknown and is retried on chunk-availability or topology change. Large stations can therefore remain unresolved, and the candidate cap remains a risk.
- Heightmap seeding is a heuristic and may misclassify custom geometry. There is no exact SS14 floor/map identity or exact SS14 vacuum behavior.
- Previously claimed cells stay FINITE after breaching. Demolishing an entire room may leave cells claimed FINITE indefinitely until an explicit release policy exists.
- Direct-sky claim deliberately preserves finite gas rather than instantly removing it; legacy schema-v1 outside stored gas is preserved as finite intentionally.
- Gas displacement/conservation on topology edits remains unresolved. The transient boundary ledger has no persistence/accounting contract.
- Claim plans are incremental. A crash/restart can persist only part of a proven component. Committed claims survive plan cancellation; the uncommitted remainder must be rediscovered. This partial-restart policy is a risk, not atomic ownership commit.
- Roof/door service GameTests COMPILED but NOT EXECUTED. No manual launch, owner smoke, 20-player benchmark, or full parity/acceptance result exists.

## Coordinator validation evidence and acceptance follow-up

After the 20x device rules and provisional readout changes, the coordinator ran `.\gradlew.bat test --rerun-tasks --no-daemon` (**BUILD SUCCESSFUL**) and `.\gradlew.bat build --no-daemon` (**BUILD SUCCESSFUL**; up-to-date). `compileGametestJava` executed during the test command, but GameTests were not run in a server world. These results are build/test-task evidence only, not world behavior or acceptance. No test command was run for this documentation-only update.

M0–M5 acceptance remains OPEN. The World GameTests compiled but were not executed; no game/server launch or owner manual run occurred. In addition to world behavior, 20-player performance (20p) is unverified, as are unloaded seams, large connected-room progress/ownership limits, topology displacement, and restart/persistence behavior. Execute server-world roof/cover/glass, under-overhang connectivity, door breach, unloaded seam/reload, and restart/persistence fixtures; validate behavior in normal-air and configured-vacuum dimensions. Exercise UNKNOWN and saturation recovery, partial claim-plan restart, demolition/release policy, and topology-edit gas displacement. Measure ownership memory/progress separately from gas-processing cost and run representative scale/performance tests.

Keep the owner-only manual smoke/checklist outstanding: confirm numeric and PROVISIONAL analyzer/device readouts for loaded unresolved passable cells; verify analyzer targeting on a closed door reports adjacent passable air only (or explicit no-gas-cell, never door atmosphere); check the four devices at their 20x rates, including partial producer dose and cooler floor; decide intended mapped exterior policy; confirm roof/overhang and door-to-space semantics; decide topology-edit displacement and ledger accounting/persistence; verify actual interactions; and perform owner smoke. Do not claim a manual run. Compilation is not execution, and neither source review nor focused unit coverage is acceptance or SS14 parity. A possible future SS14-inspired model—map tiles begin as space and defined grid/floor tiles become non-space—may be evaluated against search/history-based ownership; it is not a selected or implemented alternative, and no new block assets or runtime tile parity are claimed.
