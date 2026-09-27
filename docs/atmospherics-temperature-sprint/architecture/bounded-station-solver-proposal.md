# Bounded Atmosphere Solver: Design and Acceptance Proposal

## Purpose and scope

This document describes the bounded equalization path now present in code and the validation still required. It is not a proposal for a prebuilt station or fixed envelope: rooms/buildings may be constructed anywhere, with no permanent station bounds. It is not a blind SS14 port, parity claim, or evidence of measured performance. See the [sprint instructions](../Instructions.md) and [M2 audit](../audits/m2-2026-09-26-sky-boundary-and-equalizer.md).

## Facts established by source review

> **Ownership update:** The heightmap-only description in this earlier solver proposal is superseded by the dynamic ownership implementation summarized here and in the [M3 audit](../audits/m3-dynamic-space-ownership.md). The [M2 audit](../audits/m2-2026-09-26-sky-boundary-and-equalizer.md) is an archived earlier baseline.

- Heightmap `MOTION_BLOCKING` first-free Y supplies only exterior seeds for loaded/passable cells. Unclaimed covered cells are classified by resumable six-face ownership search; a complete loaded enclosed proof is required for FINITE claims. Unloaded/unknown cells and the 131,072 candidate cap fail closed. The service budget is 768 ownership probes and 256 staged claim writes per due tick.
- Persisted schema-v2 finite claims take precedence over sky seeding and survive breached doors. Schema-v1 gas overrides are promoted to claims. Direct sky claim remains finite so stored gas is not instantly deleted; an adjacent open sky cell provides the sink. No permanent envelope or room column exists, and connected sky beneath an overhang can be exterior.
- Incomplete claim plans can leave a partial persisted claim set across crash/restart; committed claims survive plan cancellation. No whole-room demolition release policy exists. Custom geometry heuristic errors, no exact SS14 floor/map identity, and unresolved topology-edit gas displacement remain limitations.

- A block position represents a fixed 1 m³ gas cell. Gas and thermal energy exchange across six-face neighbors; pressure derives from gas and temperature.
- The service retains local neighbor species/heat processing and adds a pure bounded equalizer plus incremental finite-region discovery within the same server pipeline.
- `BoundedGasEqualizer` has an 800-cell maximum. It equalizes total moles to the patch average and transports donor composition and enthalpy; it is not a uniform mixture overwrite.
- `BoundedRegionDiscovery` has an 8,000-candidate limit. A patch/hard limit is not evidence that the whole connected region has been found or that it is exterior. Continuation seeds represent incomplete frontiers.
- Heightmap first-free Y is an exterior seed only. Covered unclaimed cells use the dynamic ownership search described above; claims take precedence, and local exchange to adjacent sky remains available. This is an approximate heuristic, not exact SS14 vacuum behavior.
- Direct exterior exports finite-cell species and energy into a transient per-level ledger. The ledger is not persisted. Unloaded chunks are unavailable/closed, not sinks.
- Scheduling includes priority mutation seeds with FIFO interleaving, an 8,192-position hot queue, coalesced per-chunk dirty retries, an ordered version-checked `AtmosphereChunkData.nextAfter` cursor, and per-column snapshots for exposure cleanup. Cursor/index behavior has focused unit coverage; priority overflow/recovery and long-region fairness are unit-tested only. Some work is bounded per tick; total memory, all overload cases, and real-world fairness are not acceptance-proven.
- Coordinator actually ran `.\gradlew.bat test --rerun-tasks --no-daemon` and `.\gradlew.bat build --no-daemon`; both reported **SUCCESS**. `compileGametestJava` executed during `test`, but GameTests were never executed in a server world (no game/server launch). No 800-cell cost profile, comprehensive transactionality test, or 20-player benchmark is evidenced.

## Design boundaries

There is no room object or permanent station envelope. Do not flood-fill an entire world or classify all cells connected through a door as exterior. Heightmap is only an exterior seed; covered unclaimed cells are resolved by six-face ownership search, and persistent finite claims override later sky connectivity. Breaching a door therefore does not instantly convert an entire claimed volume to exterior; finite cells drain through adjacent open sky. Roof/glass/no-skylight and complex topologies require real fixture validation. The heightmap seed heuristic may not identify intended mapped exterior correctly in all world layouts.

The bounded equalizer is Monstermos-inspired in its high-level use of finite patches, but it is not SS14 Monstermos and does not imply parity. Its bounded partial work must remain explicitly incomplete: an 800-cell patch or 8,000-candidate discovery limit cannot be interpreted as a complete room, or as proof that the unresolved region is space. Local exchange remains part of the same pipeline to preserve local gradients and process finite/exterior boundaries.

## Acceptance plan

### Gate A — World fixtures and boundary semantics

Execute server-world fixtures for direct exposure and covered cells, including roof/glass and no-skylight End cases; verify finite under-overhang exchange, immutable exposed ambient, and gradual door-to-space drainage. Verify normal and configured vacuum dimensions use their respective immutable exposed ambient. Check that partial discovery, unloaded chunks, and unknown boundaries are never reclassified as exterior. Document that heightmap classification is a deliberate approximation and obtain owner direction for mapped exterior policy.

### Gate B — Continuation and bounded work

Exercise finite connected regions larger than 800 cells and larger than the 8,000-candidate discovery limit. Verify patches make progress through continuation seeds and do not assert whole-region completion based on a partial result. Test mutation-priority scheduling alongside FIFO work, coalesced dirty chunk retry behavior, and `nextAfter` cursor restarts after version changes. Test unloaded chunk seams without force-loading. Establish memory/overload bounds and progress guarantees instead of inferring them from individual per-tick caps.

### Gate C — State conservation and boundary accounting

Measure closed finite-patch species and energy before/after equalization. Test topology changes (wall/roof/door placement/removal, exposure changes) separately because they can alter implicit ambient/exterior classification and may displace or reinterpret matter. Verify finite-to-exterior exports and the transient ledger's species/energy values; decide whether ledger state must persist, how it resets, and how it is surfaced. Distinguish intentional exterior export from topology-induced changes and ambient defaults.

Do not claim comprehensive transactionality. Measure preflight and write cost for a maximum 800-cell patch. Exercise service-thread mutations/failures and establish what happens if state changes or a write fails between validation and commit; add rollback only if a defined requirement calls for it.

### Gate D — Performance and decision

Profile discovery, patch preflight/commit, local processing, dirty retries, memory high-water marks and equalization latency in representative worlds. Include a region beyond 8,000 candidates and realistic loaded chunk seams. Benchmark a representative 20-player workload. No profile or 20-player result exists. Use results and correctness gates to tune or retain the design; do not derive performance claims from cell/edge arithmetic or a synthetic isolated patch.

## Explicit outstanding owner decisions

- What policy identifies intended exterior on mapped worlds where heightmap exposure is insufficient or overinclusive?
- How should topology changes materialize/displace ambient gas, and which conservation invariant is expected?
- Is the boundary ledger diagnostic/transient by design, or must it be persisted across restart? What reset/accounting behavior is desired?
- What mutation rollback semantics are required for patch preflight/write failures?
- What profile environment, acceptance workload, and performance target define successful 20-player operation?

Until these gates are executed, do not claim room-scale performance, exact SS14 vacuum behavior, comprehensive transactionality, 20-player readiness, or SS14 parity. Roof/direct exposure including no-skylight End behavior is supported by the heightmap code and code-level tests only; no real-world room tests have run. Test sources compiled are not executed server-world tests. After automated world tests pass, the owner-only manual checklist remains: decide mapped exterior policy; assess topology edit gas displacement; set ledger accounting/persistence semantics; confirm actual device/analyzer interactions; and perform owner smoke.
