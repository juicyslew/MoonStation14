# Finite liquids: continuous puddles, pools and flooding

## Status and prerequisite

**DEFERRED — planning only. Do not begin implementation, prototyping, asset work or validation.**

Start only after the shuttles task is finished and the owner explicitly starts this sprint. Read the completed shuttle handoff before finalizing liquid ownership, coordinates and topology integration. This document is a future work plan, not implementation authorization.

## Goal

Extend the existing finite-reagent puddle experience into one continuous system supporting thin spills, wading-depth liquid and swimmable pools. Sufficient liquid poured at one location should fill reachable space, spread, level and descend into lower openings. Draining or spreading a pool thinner should return it to puddle presentation without creating, deleting or duplicating its reagent contents.

Preserve precise, spatially local chemistry at small scales. Larger floods may approximate motion and reaction timing, but must carry actual source mixtures and produce the appropriate chemical interactions where they arrive.

This deliberately goes beyond pinned SS14's static water terrain. Yogstation supplies useful liquid-group/elevation precedents, not a solver to copy wholesale.

## Requirements and constraints

- **No global homogenization:** each cell retains its own composition. Connecting liquid bodies does not mix an entire pool instantly. Mixing occurs through actual transfers and existing reaction rules.
- **One quantity authority:** puddle/pool is derived presentation and behavior, not two competing inventories. Chemical precision remains independent of visual depth quantization.
- **Hybrid transport:** preserve useful local cell-to-cell flow for small spills; add volume-aware regional transport for large imbalances. Bulk routing must not bypass reactive intermediate cells or spend volume already moved by local flow.
- **Responsive, not instantaneous:** target sensible existing-puddle/Minecraft-water-like pacing. There is no literal two-or-three-update deadline. Large volumes must not become trapped behind tiny fixed transfer allowances or stalled rediscovery.
- **Changing terrain is normal:** doors, construction, breaches and explosions must be ordinary supported events, including concurrent activity on an 80-player server.
- **Caching is optional and measured:** begin with the smallest useful working snapshots/dirty-region state. Compare recomputation with selective caching under frequent changes. Do not require a persistent whole-station basin cache or assume geometry stays stable.
- **Evaporation follows verified source behavior:** choose and document a coherent SS14 or Yogstation policy after tracing the actual consumer. A depth-based exemption is acceptable only if supported by the selected source behavior or separately approved as a departure. Do not invent a cutoff merely to make sleeping easier.
- **Discrete first-pass visuals are acceptable:** no general flood-surface interpolation is required.
- Do not force-load arbitrary chunks. Unresolved boundaries, drains, void exits and storage limits need explicit policies; they must not silently discard or duplicate liquid.

## Ordered work units

### 1. Resolve parity, reuse and integration boundaries

Read the research indexes below, then verify relevant upstream compositions/defaults and actual consuming systems before designing prototype fields. Pin the selected Yogstation source as well as the project's SS14 revision. Resolve evaporation cadence, eligible reagents, depth/volume dependence and removal semantics; current local evaporation metadata is not proof of a live evaporation consumer.

Inspect completed shuttle architecture, current puddle consumers and atmosphere transport owners. Establish supported geometry/openings, reagent-units-to-cell-capacity mapping, loaded-boundary behavior and required wading/swimming controls. Shuttle-carried liquids must use the completed shuttle ownership model; acceleration-driven sloshing is not a first-pass requirement.

Define representative small-spill, flooding and concurrent-topology scenarios before optimizing. Document deliberate adaptations and any remaining material owner decisions.

### 2. Establish canonical cell contents and precise transfers

Reuse existing reagent units, storage, reactions and transaction services. Integrate current puddle filling, collection, ingestion, cleaning and contact consumers with the same canonical liquid state used by deeper cells. Do not add a second chemistry implementation.

Stage transfers with source budgets and defined reaction points; preserve component quantities and rounding residues at the supported reagent precision. Coordinate local and bulk phases so neither acts on stale contents or moves the same quantity twice. Region metadata may describe connectivity/capacity, but must not own a globally blended mixture.

Add focused source/default/inheritance and live-consumer coverage, including the owner's small Water/Potassium spill case. Resolve any existing reaction-rule discrepancy explicitly rather than silently changing chemistry balance.

### 3. Implement local depth-aware flow and visual continuity

Extend/reuse the current local-flow responsibilities for lateral flow, support, gravity and downward spills. Derive puddle/pool appearance from liquid depth while retaining exact contents. Cover cell filling/emptying, larger pours, cliffs, collection and drainage.

Use supported prototypes/composition for capacities and behavior. Prefer SS14 assets/sounds where practical; retain provenance. Do not present new artwork as approved or delivered before its normal integration/review.

### 4. Add bulk transport and prove the performance direction early

Use the gas architecture's local-exchange/regional-transport pattern where responsibilities fit, not its compressible-gas equations. Route composition-bearing transfers using gravity, surface height, openings and available capacity. Respect retaining sills, separate downstream basins and reactive fronts.

Wake affected work on relevant topology changes. Unrelated edits must not invalidate every pool. A stale regional job must not freeze all otherwise valid local flow. Evaluate short-lived discovery and selective caching against frequent door changes before choosing a persistent cache design.

At this stage, measure representative floods and changing geometry before completing expensive presentation/movement integration. If the architecture cannot meet pacing and server-budget goals, revisit it rather than hiding slow transport behind animation.

### 5. Integrate world lifecycle, networking and presentation

Connect door/floor/wall/explosion changes and chunk lifecycle to the liquid owner. Preserve quantities through supported save/unload/reload and shuttle lifecycle paths without introducing disproportionate crash-recovery machinery.

Batch changed-cell/chunk information where appropriate; do not blindly replicate full reagent NBT for every deep-water cell every update. Render exposed surfaces rather than unnecessary internal faces. Keep initial wet-cell/depth changes discrete; optional interpolation inside already-wet cells is follow-up work only.

### 6. Integrate contact, wading/swimming and maintenance

Reuse existing fire-stack, contact, status and movement owners. Establish authoritative depth-driven wading/swimming with corresponding prediction/reconciliation; do not secretly fall back to vanilla movement or revive the current unsupported-water failure path.

Apply reagent/contact effects at appropriate local boundaries. Implement the source-verified evaporation policy separately from hydraulic scheduling so settled liquids can avoid unnecessary flow work. Removing volatile contents must leave nonvolatile components correctly accounted for.

### 7. Grouped readiness and owner testing

Run focused cheap checks during development and use the shared serialized workflow for expensive integration, native, readiness and packaging work. Select actual affected classes/categories/tags; do not automatically select every readiness category. Preserve applicable broad-change/full-suite and integration requirements.

Provide a short manual checklist and an honest performance/coverage summary. Automated readiness is not owner acceptance. Write the sprint handoff only after the owner closes the sprint.

## Acceptance and performance evidence

- Small precise mixtures react locally; unrelated connected liquid is not diluted or reacted globally.
- Per-component quantities remain accounted for through local/bulk transfers and puddle/pool presentation changes; explicit reaction/evaporation/source/sink effects explain changes.
- A large pour expands into reachable capacity, levels and continues flowing without a small fixed per-cell bottleneck.
- Wall/bottom breaches, cliffs, retaining sills, doors and partitions produce appropriate redistribution without crossing closed barriers or stale topology.
- Reactive fronts are exercised during bulk flooding, including topology changes caused by reactions/explosions.
- Frequent local edits and several simultaneous flows make progress without whole-world rebuilds. Settled areas avoid unnecessary hydraulic work.
- Loaded boundaries, collection, cleaning, supported persistence and shuttle lifecycle preserve ownership and quantities.
- Depth affects appropriate wading/swimming/contact behavior through the real authorized-player route; small puddles remain usable by existing chemistry interactions.
- Evaporation matches the documented selected source, including cadence, eligibility and residual mixtures.
- Measure worst/p95 main-thread cost, first-flow and redistribution latency, active/visited/changed cells, invalidation cost, network traffic and client rendering. Compare cold discovery, valid cached geometry and frequent door/topology churn. Include multiplayer-representative concurrent activity, not just one empty-room flood.

Choose supported scale and pacing targets from measurements. No unprofiled throughput figure or arbitrary-size latency guarantee is claimed by this plan.

## Reuse and references

- Research: `docs/water-terrain-research/{FINDINGS,SS13-COMPARISON,FEASIBILITY}.md`.
- Existing handoffs: pressure transport, reagent/status, timed interactions/ingestion, slip/stun, explosions, automatic atmosphere/firelocks, and the future completed shuttle handoff.
- Existing owners: `PuddleBlockEntity`, `PuddleBlock`, `ReagentSystem`, `ReagentUnits`, `HeldLiquidTransferPlan`, `LiquidInteractionCapability`, `TimedIngestionAccess`, `FireStackSystem`, and the current contact/movement authorities.
- Discovery/invalidation references: `BoundedRegionDiscovery`, atmosphere topology/event hooks and pressure-transport staging. Extract or extend shared mechanics only where current responsibilities justify it; do not create a competing topology or transaction framework by copying substantial logic.
- SS14 baseline: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Investigated Yogstation liquid source: `ae2886be95c234202212a3090ee7637173ea2c1d`.

## Out of scope for the initial pass

Full hydrodynamics, realistic turbulence/waves, density-stratified layers, acceleration-driven shuttle sloshing, fishing/ecosystems, vanilla infinite-source-block compatibility, unrelated chemistry rebalance and generic other-mod compatibility. Spatially different reagent mixtures are **not** excluded: preserving them is required.
