# Responsive finite-liquid feasibility

Design assessment only. No implementation, benchmark or runtime validation was performed. See `SS13-COMPARISON.md` for verified references; the architecture below is a proposed Minecraft adaptation, not a claim about delivered code or Yogstation's exact behavior.

Deferred work plan: [finite-liquids sprint](../finite-liquids-sprint/SPRINT.md). Do not start before shuttles are finished and the owner explicitly starts that sprint.

## Required behavior

One finite reagent-bearing system should cover puddles and deep pools. Adding substantial volume should expand coverage, fill reachable lower space and level within containment. Breaches should permit lateral/downward flooding; thinning changes presentation back to puddles without duplicating inventories. Small spills require precise, spatially local chemistry and ordinary flowing behavior. Large floods may approximate arrival timing and resulting damage, but must not become immobilized by small-spill transfer limits. Minecraft-water-like pacing is acceptable; the earlier two-or-three-update example is not a literal deadline.

The owner rejects uniform mixing across connected bodies. Terrain changes during ordinary 80-player play are expected, not exceptional. The requested analogy is local LINDA-like exchange plus Monstermos-like bulk transport, with liquid-specific physics.

Caching is not a prerequisite. Frequent door changes may make cache maintenance more expensive than rediscovery. Compare short-lived working snapshots and selective caching under topology churn before adopting persistent basin caches; do not assume more caching is automatically better.

## Assessment

Feasible, but a substantial subsystem rather than a small puddle enhancement. Efficient operation is plausible with a hybrid local-flow/bulk-transport architecture and event-driven hydraulic work. It is not established by profiling, and no unlimited-size latency guarantee is possible.

Existing `PuddleBlockEntity.slowTick` transfers between neighboring individual stores on a cell-local cadence. Preserve its local-composition behavior as the small-spill baseline. Merely increasing its cadence or applying a tiny fixed transfer allowance to deep pools would not establish responsive large-scale flow.

Yogstation demonstrates connected reagent groups, cached edges, height bands and lower-Z transfers, but also contains whole-group processing passes. Its exact scheduler cadence/budget was not established in this research. Borrow its representation ideas rather than assuming its processing strategy will meet Minecraft latency targets.

## Candidate architecture

1. **Cell-local quantities, derived depth.** Each cell retains its own authoritative volume and composition at the existing reagent precision. Puddle and pool appearances derive from depth; a connected hydraulic region is not one mixed reagent inventory. Existing consumers must address the same canonical contents through visual transitions.
2. **Two transport scales, one transfer authority.** Small spills use local cell-to-cell flow. Large imbalances use regional transport that carries actual source mixtures rather than homogenizing a region. Reactive cells/contact boundaries must not be bypassed by a bulk route. Identical-composition water can be a batching opportunity without treating unlike solutions as globally mixed.
3. **Dynamic topology and cached geometry.** Record relevant floors, barriers, capacities, spill elevations and outlets; invalidate affected regions on normal construction, destruction, door and explosion changes. Avoid a global invalidation that makes unrelated player edits restart every pool. A high sill must retain liquid until overtopped; not every air-connected room shares a water surface. Local valid flow should not depend on an indefinitely perfect global cache.
4. **Composition-aware bulk transport.** Significant pours, drains or breaches can move substantial volume through reachable space without a tiny per-cell throughput cap. Transport still needs defined reaction/contact points along the route. Approximating a large flood's progression does not authorize skipping the chemistry where it arrives.
5. **Bounded, coherent commits.** Coordinate local and bulk transport through ordered phases or shared staged state/source budgets. Neither may independently spend the same source volume. Revalidate affected topology before committing; reactions and explosions may invalidate subsequent routing. Quantity accounting remains precise even when large-scale flow timing is approximate.
6. **Discrete first-pass presentation.** No general mesh interpolation is required. Wet-cell creation/removal and depth bands may update discretely. Later interpolation of height in already-wet cells is an optional narrower enhancement, not a prerequisite or a way to hide slow flow.
7. **Sleep hydraulic equilibrium, schedule maintenance separately.** Stable cells need no continuous flow solving. Evaporation must follow an explicitly selected, source-verified SS14 or Yogstation policy; do not invent a depth cutoff for convenience. Actual volume/composition changes wake only affected work, and evaporation must not delete nonvolatile solutes. Cheap scheduled maintenance need not imply continuous hydraulic solving.

This is an approximate leveling/transfer model, not particle fluid dynamics. No velocity field, realistic turbulence, surface waves or density-based separation of different liquids is required for the stated first pass. Spatially different cell compositions are still preserved. Coarse depth steps and coarse geometry are potential scope cuts; chemical quantity precision is not.

Current code stores hundredths of reagent units: 2u is 200 cents. Generic transfer reacts its recipient; puddle flow reacts source/destination after its exchange. These boundaries matter when adapting transport. The potassium-water prototype's exact minimum/rate was not verified in this assessment; the owner's small-spill explosion example is a required future acceptance scenario, not a newly executed result.

## What actually costs time

- Discovering a large previously unknown receiving cavity and rebuilding connectivity after splits/merges. A basin-wide event requires work proportional to relevant geometry; caching cannot make every novel large breach constant-time.
- Minecraft world mutations, neighbor/topology notifications, packet fan-out and client mesh rebuilding. Cheap volume arithmetic does not make thousands of block-entity updates cheap.
- Reagent reactions and contact/heat consumers if naively evaluated over every member tile.
- Many simultaneously active floods, especially across large loaded regions.

Per-cell chemistry increases state/transport work compared with a well-mixed pool, but does not require recomputing every settled cell. Performance depends on active fronts, changed topology, chemical interactions and changed-cell presentation, not player count or total stored volume alone. A reactive boundary should not force an unrelated pure-water body into globally expensive processing.

Compact region/chunk changes and exposed-surface rendering are preferable candidates to copying the current full per-puddle update-tag path to every deep-water cell. Exact representation and shared-owner boundaries still need design. A separate BE cannot simply coexist with a puddle BE at the same position.

## Responsiveness requires explicit limits

Bound active work to supported event sizes and loaded geometry; do not force-load arbitrary terrain to find somewhere to spread. Retain volume at unresolved boundaries. A minimum retained film/spread threshold is also necessary: otherwise any positive volume could theoretically spread across an unlimited flat plane at arbitrarily small depth.

Distinguish a physical flow-rate rule from computation latency. A small outlet may intentionally drain more slowly than a demolished wall, but a wide opening should not remain slow merely because the implementation processes one neighbor layer per update.

Do not impose the earlier example as a two-update completion requirement. Measure whether flow has appropriate puddle/Minecraft-like pacing and whether high source volumes continue making progress instead of becoming trapped behind small fixed transfer caps. Supported-scale limits and bounded degradation are still necessary, but should not be implemented as an arbitrarily slow flood front.

## Existing reuse and remaining work

Reuse reagent units/transactions and puddle interaction consumers where responsibilities fit. `HeldLiquidTransferPlan` provides detached staging/react-then-commit patterns, not an existing multi-cell liquid transaction. The pressure-transport handoff records production LINDA local composition exchange and Monstermos routing actual composition through intermediate cells; its staging, discovery, conservation and invalidation discipline are relevant precedents. `BoundedRegionDiscovery` offers resumable discovery mechanics without world reads. Gas classifications, 800-cell patches, 8,000 candidate cap and 256-inspection-per-tick budget are not liquid rules or proven liquid budgets. Reuse the architecture where appropriate, not compressible-gas equations as liquid physics.

The targeted local audit found evaporation-speed data but did not establish a local evaporation processor. Upstream SS14 puddle evaporation must not be reported as already implemented locally without tracing the runtime consumer.

Remaining work includes canonical storage integration, basin topology and redistribution, source/sink policy, depth rendering/networking, persistence, explosion/door changes and movement/contact. Depth-aware swimming is a separate substantive integration with the custom movement authority; it is not provided automatically by pool rendering.

## Recommended next step

Authorize a bounded feasibility prototype before committing to the complete feature. First preserve small local mixtures and reaction behavior, including the owner's Water/Potassium case. Then measure a settled pool, a large pour into an empty cavity, a bottom breach into a lower room, a retaining sill between basins, reactive fronts, frequent terrain/door changes, partition/split changes and multiple simultaneous events. Include both cold discovery and valid cached geometry.

Record quantity conservation, maximum main-thread tick cost, time to first visible flow, time to the expected redistribution, changed-cell/network volume and client rendering cost. Algorithm-only tests are useful but cannot establish Minecraft world-update or client performance. Choose supported scales and latency targets from those measurements; do not promise throughput from this paper assessment.
