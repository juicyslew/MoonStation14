# Bounded Station Atmosphere Solver Proposal

## Purpose and scope

This is a proposal for evaluating room-scale atmosphere behavior for a **prebuilt moon base plus a small trade station**. Active gas cells are expected to be concentrated in a few buildings, while the Minecraft 3D volume represented by those buildings can still contain thousands or tens of thousands of cells. Exterior space should behave as vacuum only when the owner opts a dimension into vacuum through `atmosphereVacuumDimensions`; the current default is breathable ambient in every dimension.

This is not an implementation plan to blindly port SS14, a parity claim, or evidence that any solver has been benchmarked. First measure the existing implementation; then compare a bounded region prototype against it. The [sprint instructions](../Instructions.md) describe the current model and open gates.

## Facts established by the current implementation

- A block position represents a fixed 1 m³ gas cell. Gas and thermal energy exchange across six-face neighbors; pressure is derived from gas and temperature.
- Active processing runs every four server ticks and permits at most 128 pair edges and 512 conservatively charged inspections per processing tick. At 20 server TPS, the pair-edge ceiling is **640 pair edges/second**.
- An entirely open 20 × 10 × 20-cell room has `(19×10×20) + (20×9×20) + (20×10×19) = 11,200` undirected neighbor edges. At the ideal ceiling, a single pass would take at least `11,200 / 640 = 17.5 seconds`; actual progress can be slower due to queue ordering, inspections, and repeated work. This is an arithmetic estimate, not a measured benchmark.
- The current solver is local pairwise exchange, not room-wide equalization. Queue overflow memory is unbounded, airtight faces are approximate, and topology edits can create or destroy implicit ambient gas. Empty vacuum currently has zero heat capacity and is not a thermal sink.
- There is no measured 20-player result or current solver profile. No 20-player capacity, equalization latency, or performance claim is established.

## Recommendation

Keep the current per-cell gas and heat model as the correctness baseline. Do **not** replace it with a whole-room homogeneous simulation or adopt LINDA/Monstermos based on name or presumed performance. First instrument and characterize the actual workload. If results justify it, prototype bounded room-region discovery and a Monstermos-style fast equalization strategy **inside explicitly registered finite station bounds or otherwise loaded regions**. Never discover regions by flood-filling all Minecraft air or the unbounded world.

Room/region equalization should accelerate pressure redistribution, not erase local state indiscriminately. Retain per-cell state and processing where vents, leaks, fire, hotspots, or other local thermal/gas effects need gradients. A coarse homogeneous volume or chunk-section local cache is optional future work only if profiling demonstrates a meaningful benefit and tests show it preserves required behavior.

## Staged plan

### Stage 0 — M0 measurement before optimization

Instrument the current solver before changing its behavior. Capture, at minimum:

- Active gas-cell count, active positions, and active connected/building-region counts (where region attribution can be measured without changing semantics).
- Pair-edge queue depth, deferred overflow depth, deduplication size, enqueue/dequeue rates, and high-water marks; report overload and dropped/deferred work explicitly.
- Pair edges and inspections processed per tick, solver wall-clock time per tick and percentile/max cost, and age/latency of queued work. Count persistence/load-resume work separately.
- Work budget utilization against the configured 128 pair edges / 4 ticks and 512 inspections per processing tick.

Profile representative scenarios: a prebuilt sealed room, an expected breach/open-boundary case, a room crossing chunk boundaries, and a representative 20-player server workload. Use repeatable fixtures and state what is loaded and active. Include configurations with vacuum opt-in enabled and disabled where boundary behavior is under test. Do not infer a 20-player result from a synthetic single-room benchmark.

### Stage 1 — Correctness and semantics gates

Before optimizing, specify and test the behavior the prototype must preserve or deliberately change:

1. Correct airtight faces and topology, including partial block shapes and orientation, doors, and other expected station boundaries.
2. Gas displacement and conservation semantics on enclosure construction, opening, closing, and breach. Missing cells inheriting ambient must not silently imply conservation.
3. Startup configuration and persistence behavior, including opt-in vacuum dimensions and the effect of policy on saved worlds.
4. Exterior boundary semantics. In an opt-in vacuum dimension, a named exterior boundary may act as a synthetic gas sink. Distinguish that sink from an unloaded chunk, an unknown boundary, or an ordinary missing cell; do not treat every unavailable neighbor as vacuum.

Prioritize these semantics over faster equalization. Preserve the existing gate's restart-only configuration behavior and migration warning unless an owner-approved policy changes it.

### Stage 2 — Bounded-region prototype

Prototype finite region discovery only within owner-registered station/building bounds or a defined loaded-region scope. Bounds must be explicit and finite; never scan or flood-fill arbitrary world air. The prototype should:

- Identify connected passable cells across correct airtight faces, with a deliberate strategy for bounds, chunks, and dynamic topology.
- Treat explicitly identified exterior vacuum as a named synthetic sink. Track its gas removal separately so closed-system conservation can be checked while intentional loss to this sink is accounted for.
- Handle doors and breaches incrementally where feasible: door changes can split/merge regions, and breach changes can connect a bounded interior to its exterior sink. Do not assume these updates are already cheap or correct; measure them.
- Treat chunk unload as a closed/unknown boundary, not an exterior vacuum opening. Do not force-load chunks to complete discovery.
- Cap discovery/flood-fill work per tick, expose its queues and high-water marks to instrumentation, and define how incomplete work affects simulation and player-visible behavior.
- Keep per-cell gas/thermal values where local gradients or devices need them. Evaluate fast equalization within a bounded region as a prototype, not as an assumption that every cell is perfectly uniform every tick.

### Stage 3 — Validation and comparison

Run the same repeatable scenarios against a LINDA snapshot-based per-cell candidate and the bounded-region equalizer. The current implementation is a local pairwise solver, not LINDA. Record solver CPU time, queue/discovery work and memory, equalization latency, and behavior under doors, breaches, chunk seams/unloads, persistence, and 20-player load.

Add correctness tests for sealed topology split/merge: opening or closing an internal door must preserve total gas and thermal energy except for explicitly modeled sources/sinks. Verify that gas is lost **only** to the named exterior sink, and account quantitatively for that loss. Test conservation for fully sealed split/merge, cross-chunk topology, and restart/persistence. Confirm vacuum's present zero-gas/zero-heat-capacity ambient behavior separately; a vacuum gas sink does not imply a cold heat sink.

Compare qualitative behavior as well as timings against the relevant SS14 reference cases, documenting deviations. LINDA snapshot processing and Monstermos-style equalization are distinct strategies, not names for interchangeable algorithms. SS14 behavior can inform tests, but no parity claim follows from matching a solver label.

### Stage 4 — Decision gate

Adopt bounded regions only if measurements show the current solver misses an owner-approved performance or gameplay target and the prototype meets correctness gates at robust 20-player scale. Otherwise retain the simpler per-cell design and prioritize bounded queue memory and topology correctness. Publish the measured evidence and remaining tradeoffs before choosing coarse homogeneous volumes or chunk-section caching.

## Owner decisions required

- What finite bounds define the prebuilt station/base and any later registered buildings? Who registers and updates them?
- What breach sizes/frequencies and open exterior connections are representative?
- What equalization time is acceptable for small and large rooms, and what gameplay cases require local gradients to remain visible?
- Which server/profile environment and representative 20-player workload are the acceptance basis?
- What conservation/materialization policy applies when walls enclose cells that previously inherited ambient? Is gas lost through exterior vacuum acceptable, and how is it accounted for?

Until these decisions and measurements exist, do not claim room-scale performance, 20-player readiness, or SS14 parity. Handheld analyzer code is implemented and its automated unit tests passed; its server-world `useOn` behavior versus vanilla interactions and multiplayer behavior remain unverified. Keep its world verification open independently of this solver proposal.
