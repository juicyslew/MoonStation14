# Atmosphere hot-path audit

## Constraints

This audit predates the source-group scheduling correction in `GROUP-LIVENESS-FOLLOWUP.md`. Its pending-snapshot discussion describes the earlier implementation, not a requirement to retain artificial per-cell group progress.

Preserve SS14 equations, float behavior, priority/tie ordering, source/phase sequencing, 15 Hz admissions, the soft 3 ms budget, loaded-only ownership and transaction-success-only publication. These changes reduce preparation/allocation work, not gameplay rates. Live TPS/FPS and allocation profiles have not been supplied; structural improvements are not measured station speedups.

## Implemented neutral gains

| Path | Change | Correctness evidence |
|---|---|---|
| Client visible-cell selection | Select nearest K with a bounded worst-first heap, then sort retained cells. Avoid constructing wrappers for noncompetitive candidates. Retained storage is O(K), selection O(N log K), rather than materializing and sorting all in-range candidates. | Dense-cache full-sort reference comparison, including limits and existing distance/tritium/coordinate ordering. Radius, render cap and packet/cache behavior unchanged. |
| Reaction preparation | Reuse an immutable priority-ordered entry plan in a bounded catalog-identity cache. Replacement catalogs rebuild naturally; equal-priority insertion order remains intact. | Plan reuse, tied priorities, replacement/alternating catalogs and empty-catalog tests. Selection predicates and persistent Fire result remain per evaluation. |
| LINDA archived capacity reads | Reuse immutable archived mixtures instead of reconstructing equivalent mixtures for each capacity/comparison read. | Existing numerical transport/archived-capacity/immutable-reservoir tests; mutable live states still produce updated mixtures. No global archive/processed-set enumeration is reintroduced. |
| Excited-group resumptions | Borrow the input map read-only; retain detached pending snapshots. Compare unchanged mixture identity first and changed objects directly by temperature/species without constructing mole maps. Compute each group's minimum position once per sort, not once per comparator invocation. | Non-enumerating input and equivalent-object resumption tests, existing stale-snapshot/cooldown/boundary tests. Member iteration and averaging order unchanged; keys are fresh per sort, not stale cycle caches. |

Independent read-only review passed. Consolidated evidence is in `build/atmosphere-parity-validation/performance-audit-gate-01/`:

- **1,870 JUnit tests / 275 suites**, zero failures/errors/skips.
- **All 73 required focused GameTests passed on rerun.** Attempt 01 failed the previously intermittent `negativeStacksDryOnePerDueIntervalAndCleanUp` assertion; logs are retained in `attempt-01/`. Its cause is unresolved, not proven fixture-only or a production regression. No drying-cadence change or assertion waiver was made.
- Default fixture compilation and assembly succeeded; `build/libs/moonstation14-0.0.1.jar` rebuilt.
- Diff whitespace check passed; disposable successful server worlds removed after archiving logs.

## Checked and deliberately unchanged

- Group merges already use larger-wins/tie-to-other union-by-size, matching SS14. No merge rewrite was warranted.
- Ordinary gas writes mark chunks unsaved; they do not serialize the entire attachment to NBT on every cell update. Save-time serialization is a separate profiling target.
- Per-source neighbor work is local. LINDA already repeats source batches within its stage budget; a one-source batch is not a one-source-per-stage limit.
- Group pending snapshots, stale validation, atomic publication and detached discovery results are correctness safeguards. Do not simply delete them to lower allocation counts.
- Discovery snapshots and tracked-cell snapshot setup retain bounded/linear preparation costs. Further changes need allocation evidence and preservation of detached-result contracts.
- Dense fire can still be GPU/overdraw limited, and client/server network snapshots can backlog. No measured render/network bottleneck has yet justified altered visual cadence, fewer sprites, or periodic broadcasts.
- Geometry currently repeats per-cell relative-coordinate/pulse work across gas layers and does not frustum-cull the selected cells. These are profiling candidates, not established bottlenecks. Fire geometry is fixed-size with bounded visual intensity; there is no intensity-proportional unbounded quad loop.
- The 131,072 ownership-candidate saturation path and 4,096 exterior-witness/reference limits remain documented safety bounds, not weakened for speed.

## Next measurement

Compare `/ms14atmoscheck` counters at a fixed hot-room position over 10–20 seconds: wall-clock callbacks/admissions, completed cycles, stage age, processed LINDA sources and ownership probes. Separate client FPS from server progress. Profile the dominant stage or render path before adding caches, pooling or another scheduler redesign.
