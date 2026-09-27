# M2 Audit — Sky Boundary and Bounded Equalizer

> **Archived / earlier baseline.** This audit records the earlier heightmap-only ownership model and is retained as historical context, not the current ownership specification. Dynamic sky-connectivity search, persistent finite claims, and current ownership limits/risks are documented in the [M3 dynamic space ownership audit](m3-dynamic-space-ownership.md). Statements below describing heightmap classification as the complete exterior rule are superseded by M3.

**Date:** 2026-09-26

**Scope:** Documentation/source review of direct-sky exterior handling, bounded equalization, scheduling, and current acceptance evidence after the priority-overflow, dirty-cursor, and column-index changes. This is not a test report or full code audit.

## Summary

The atmosphere runtime now has a heightmap-based immutable direct-exterior rule, finite-cell exchange to exterior with a transient species/energy ledger, and an 800-cell bounded total-moles equalizer driven by discovery capped at 8,000 candidates. It runs alongside local neighbor species/heat processing in one service pipeline. Priority seeds, a capped hot queue, coalesced chunk retries, and version-safe `nextAfter` traversal are present.

The model has **no permanent station envelope or room ownership**; buildings can be created anywhere. A direct exterior cell is immutable ambient, while covered cells remain finite and may drain to adjacent exposed cells. A door opening does not instantly reclassify a whole room. This is a local heightmap heuristic and not exact SS14 vacuum behavior. Partial discoveries are not whole rooms or proof of exterior. Work has not passed server-world execution, long-region progress, transactionality, or scale acceptance.

The coordinator actually ran `.\gradlew.bat test --rerun-tasks --no-daemon` and `.\gradlew.bat build --no-daemon`; both reported **SUCCESS**. `compileGametestJava` executed during the test task, but GameTests were **never executed in a server** and there was no game/server launch. These command results are automated build/test evidence, not world-behavior verification. No SS14 parity, comprehensive transactionality, measured 800-write tick CPU cost, or 20-player performance result is claimed.

## Code reviewed

- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereService.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereTopology.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/BoundedRegionDiscovery.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereChunkData.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/core/BoundedGasEqualizer.java`
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/core/ImmutableAtmosphereBoundary.java`
- `Config.java` and `item/ModCreativeModeTabs.java` for default-off/ambient and creative-tab facts.

Review is based on source inspection; no runtime assertions below should be read as a measured outcome.

## Observed behavior and limits

### Ambient and direct exterior

- `enableAtmospherics` remains COMMON config default-off, sampled at server startup. `atmosphereVacuumDimensions` defaults empty; exposed ambient is breathable in all dimensions unless configured otherwise. Vacuum-dimension exposed ambient is zero gas with nominal 2.7 K. The same position-based exterior rule applies regardless of dimension skylight.
- `AtmosphereService.isExterior` obtains the chunk `MOTION_BLOCKING` heightmap first-free Y and classifies a loaded, passable cell with `y >= firstFreeY` as exterior. It does not inspect skylight or establish full geometric visibility to the sky. Code-level tests confirm this rule works independent of skylight, including no-skylight End semantics; no world was launched to validate it in a real End. A roof contributes to the heightmap; covered cells under finite overhangs remain finite. Directly exposed cells sample immutable ambient and reject ordinary writes/device changes.
- Cells beneath cover are finite. A finite cell adjacent to direct exterior can exchange locally through `ImmutableAtmosphereBoundary`; exported species and thermal energy are recorded in a transient per-server-level ledger (a space heat sink, not persisted). Exterior exposure itself is local, not propagated through room connectivity. Opening a door to space therefore does not immediately make every connected cell exterior. Ordinary-air exterior remains immutable breathable ambient, unless the dimension is configured as a vacuum dimension.
- This is not exact SS14 vacuum behavior. Under-overhang/cave/topology combinations, glass/roof interpretations, and mapped exterior intent need server fixtures and possibly an owner-selected policy. Partial block collision and face orientation remain coarse.

### Bounded equalization and pipeline

- `BoundedGasEqualizer` limits a patch to 800 mixtures. It targets average total moles, transporting donor mixture composition and enthalpy; it is not an all-species uniformization or state overwrite.
- `BoundedRegionDiscovery` limits candidate work to 8,000 and distinguishes finite, exterior, and unknown-unloaded probes. Ready patches can be consumed incrementally; hard-limit continuation frontiers are re-seeded. A partial patch is not treated as a complete room/component, and a candidate limit is not a space classification. No permanent station bounds, room/column ownership, or fixed region exists.
- The equalizer and local neighbor exchange are invoked by the same `AtmosphereService.tick` pipeline. Local processing continues to exchange species/heat and permits gradual finite-to-exterior loss. Device/topology mutation seeds receive priority, with FIFO fairness interleaving.
- Discovery and equalization source presence do not establish acceptable cost or progress. 800-cell aggregate preflight, list allocation, server writes, repeated discovery, and behavior under sustained mutation need measurement and tests.

### Work scheduling and ledger

- The hot queue has an 8,192-position capacity. Dirty work is coalesced per chunk; retries traverse attachment entries in order using `AtmosphereChunkData.nextAfter` and a mutation version. Exposure cleanup indexes/snapshots only the affected vertical column. Cursor and column-index behavior has focused unit coverage. Retry cursor work and lookup work have per-tick caps.
- Priority seeds have a bounded hot portion, coalesce overflow by chunk, and interleave with FIFO work. Priority overflow/recovery and long-region fairness have unit coverage only. These source-level mechanisms replace the old unbounded FIFO overflow description, but do not prove a global memory bound, fairness in actual worlds, or eventual progress through arbitrarily large connected regions. Per-chunk overflow aggregation can still grow with chunk cardinality; overload/recovery is not acceptance-proven.
- Boundary ledger accumulates species and energy exports in transient per-level state. It is not persisted. Cleanup of previously stored exposed overrides also adjusts the ledger. The accounting and persistence contract remains undecided and must be tested against intended semantics.

### Data and transaction semantics

- Closed isolated equalization is designed to redistribute conserved aggregate gas/enthalpy through pure mixtures. Species-composition thresholds and topology-driven gas displacement are incomplete/unverified. Topology changes can still reinterpret ambient cells, expose stored cells to immutable ambient cleanup, or change adjacency; conservation across edits is not established.
- `commitPatch` preflights mixtures and computes outputs before writes, but no comprehensive transactionality/rollback guarantee is established. Measure and test write failures or intervening mutations before describing commits as atomic. No concurrent rollback behavior has been verified.
- Unloaded chunks remain unavailable boundaries; code uses non-forcing loaded-chunk lookups. Seam resume and lifecycle behavior still require executed world fixtures.

## Acceptance status and follow-up gates

Milestones M0 through M5 remain open for acceptance (M3 code exists but acceptance is open). Required evidence:

1. **Server-world fixtures:** execute compiled `AtmosphereRoomGameTests`; add/execute roof, glass, no-skylight End, overhang, immutable exposed ambient, finite drainage, and door-to-space tests in both normal-air and configured-vacuum dimensions.
2. **Seams and progress:** execute unloaded seam/reload fixtures and demonstrate fair progress through connected finite regions larger than 8,000 candidates without treating partial discovery as whole-room or exterior completion.
3. **Cost:** measure 800-cell patch preflight and write cost, discovery budgets, dirty cursor retries, queue/retry memory, and latency.
4. **Failure/concurrency:** test mutations/failures during patch preparation and commit; define and verify rollback expectations before any transactionality claim.
5. **Policy and conservation:** obtain owner decision on mapped exterior identification; test topology edits and specify mass displacement/conservation behavior; decide whether boundary-ledger values persist, reset, or remain diagnostics only.
6. **Scale:** run representative profiling and at least a 20-player benchmark. No such results are available.

After automated world tests pass, the owner-only manual checklist is still required: confirm intended mapped exterior policy and roof/glass/overhang behavior; decide topology-edit gas displacement/conservation and boundary-ledger accounting/persistence; verify device/analyzer interactions and perform owner smoke. Automated compilation or unit tests do not substitute for this checklist.

## Explicit non-claims

- No exact or comprehensive SS14 vacuum behavior or SS14 solver parity.
- No assertion that every connected finite volume is fully discovered, or that an 800-cell patch is a whole room.
- No comprehensive transactionality/rollback guarantee.
- No proof of unloaded-seam correctness, infinite-region progress, total scheduler memory boundedness, or 20-player readiness.
- No server-world test execution: Gradle test/build both succeeded and GameTest sources compiled, but `AtmosphereRoomGameTests` never ran in a server because no game/server was launched.
- No real-world room tests, measured 800-cell/800-write tick CPU cost, verified species threshold, complete topology-edit gas displacement, or proven long-region fairness/priority-overflow recovery beyond unit tests.
