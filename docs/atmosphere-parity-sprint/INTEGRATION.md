# Worker integration notes

Temporary coordination index; consolidate durable decisions into final documentation.

Current acceptance evidence is indexed in `VALIDATION.md`; older assignment/status notes below are historical coordination, not a current green-gate claim.

- Mixture/constants: `ses_efdbdc4c0ffeH3iaf9ifaZtC3B`, float state with compatible numerical APIs, central conversion utility, mixture operation contract.
- Chemistry evaluator/prototypes: `ses_efdbd10abffeAO07g57jseHcY8`; no hotspot/step edits yet.
- Monstermos core normal/space: `ses_efdbc6bcaffeBIdKymhY4ow5Pc`; no service edits.
- Service/stages: `ses_efdbb5c59ffev7oHHrQp1l5kg4`; owns service integration and scheduler.
- Profiles/config: `ses_efdbb024dffeFSlh40mm7udqwI`; independent startup-configured space dimension list versus vacuum gas profile dimension list. Service wiring belongs service integrator. No new generalized profile/prototype family without actual need.
- Upstream transport reference research: `ses_efdbd5d1dffeoXS14PjIPO5OEF`.
- LINDA/immutable boundary: `ses_efdb9f386ffewcV75ywoWl05uu`, completed standalone API/equations; validation pending combined gate.
- Groups: `ses_efdb8020fffe6VQBPB7DVaRF2f`, completed merge/divide breakdown changes; validation pending combined gate.
- Hotspot/step/queue: `ses_efdb3e89fffeN0z86ZEFaLIMD6`, implementing volume-metadata lifecycle.

## Architectural boundaries

Existing public API adapters can remain where they preserve consumers without perpetuating old runtime physics. Ordinary immutable ambient participates as a bounded one-layer endpoint in normal Monstermos averaging and as an immutable LINDA neighbor; it is never processed by pressure-bisection or expanded as an infinite graph. Classification must expose independent Space identity rather than infer it from gas moles.

Service cadence should resume unfinished work every server processing opportunity. Successful external writes must not repeatedly cancel/restart the entire cycle and recreate continuous-writer starvation. Retain cycle archives and schedule subsequent activation; rebuild stale topology work selectively. Transactions remain success-only publication.

Mole-derived upstream spacing pressure targets must be reconstructed from equivalent 2,500 L reference inventory to preserve pressure behavior at 1,000 L. Float cleanup and temperature normalization are allowed explicit upstream sources/sinks, not conservation failures to eliminate.

## Critical source clarification

Pinned `AtmosphereSystem.LINDA.cs:102–103` calls the shared whole-air reaction pass, including on hotspot tiles. Pinned `AtmosphereSystem.Hotspot.cs:258–275` **does not react whole air again on bypass**: it uses the prior whole-air Fire reaction result. Only the non-bypass extracted portion receives another shared reaction pass. This supersedes an incorrect upstream research report claiming bypass called React again.

GasMixture now provides float-backed state, explicit volume, and `splitRatio` with cleaned remaining/removed mixtures. Transport must use both results for source cleanup. Chemistry JSON retains canonical 0.01 requirements; evaluation converts them once. `GasReactionEvaluator.evaluate(catalog, mixture)` is the shared ordered pass.

Standalone Monstermos completion: normal production entry is `equalizeDiscovered`, hard discovery constant `MAX_DISCOVERED_CELLS` (2,000), soft inclusion 200. The old equalizer and ambient-bisection APIs are deprecated compatibility adapters, not acceptable production dispatch. Space service calls must pass the new 2,000-cell bound rather than the old 800. Independent review requested finalization dependency resolution and stable direction traversal; worker is addressing those before acceptance.

Reaction APIs now available: `GasReactionStep.evaluateWholeAir(catalog, gas)` and `processHotspot(catalog, gas, HotspotState, priorWholeFireExtent)`. Queue state is the standalone explicit-volume `HotspotState`. Service must migrate all state/diagnostic/exposure consumers and retain the whole-air result through the hotspot stage. Legacy derived fractions are display adapters, never a cap on stored volume.

Direct source clarification: `Share` assigns `tileReceiver.LastShare = absMovedMoles` **per edge**, and `LastShareCheck` runs immediately after that edge. Summing movement over multiple neighbors can incorrectly reset breakdown cooldown. LINDA worker is adding per-edge absolute movement to the result; service must reset groups from each edge's magnitude, not an aggregate neighbor sum. Opposing species deltas use the sum of absolute deltas within that single edge, not absolute net moles.

## Fire reaction-result state

Direct pinned-source verification: each invoked fire effect resets the single Fire result slot and assigns its own result; the last invoked effect wins, including a zero result that emits no successful event. React does not reset results globally. If no fire effect is invoked, prior Fire result persists. Fresh removed portions start with zero. Plasma result is burn rate × (1 + oxygen burn rate); tritium result is burned fuel. Summing successful event extents is incorrect.

Evaluator worker is exposing `Result.fireReactionResultMoles()` plus prior-result input. Step exposes `evaluateWholeAir(catalog, gas, priorFireResult)`. Service must retain this per-cell transient component across cycles and only publish its update after a successful whole-air operation. Partial kernel consumes the new portion result; bypass consumes the cached whole-air result without another evaluation.

Normal Monstermos graph parity includes distinct loaded, classified exterior cells directly adjacent to the discovered finite patch as immutable participants in its first-200 average. This is a one-cell boundary adapter: it does not expand discovery through the exterior reservoir. Space-flow remains a separate kernel. Writes and topology invalidation are finite-only; normal kernel's reported actual boundary packets update the boundary ledger.

## Latest completed interfaces

- LINDA overload accepts `alreadyProcessedThisCycle`; `Result.processedCells()` contains only sources processed in that invocation. Commit success adds them to level cycle state. This prevents repeated edges across batch boundaries.
- Groups extended `advanceCycle` accepts immutable and Space position sets, default all-consuming false, and exposes pending-breakdown status plus membership queries. Include reservoir members in averaging denominator, write only finite replacements.
- Mixture `mergedWith(giver, giverNormalizedCapacity)` supports empty immutable virtual-Cp giver weighting; ordinary overload retains normal capacity behavior.
- HotspotKernel legacy nested-state and fraction APIs are removed. Explicit `process`/`expose`/metadata reconciliation remain. Non-bypass starts a fresh zero Fire result; bypass takes prior whole result.
- HotspotSpread is now a metadata-only offer planner. `plan(sourcePosition, sourceGas, sourceState, List<Neighbor>)`; neighbor includes existing explicit hotspot state. Offers are keyed by position and carry exposed temperature/input volume.
- Initial compile log: `build/atmosphere-parity-compile-initial.log` (main errors were service/cycle integration only at that snapshot). No final validation acceptance yet.

## Normal ambient participation correction

Pinned Monstermos.cs:72–118 includes non-Space immutable map tiles in BFS and the averaging count. Immutability is enforced during actual Remove/Merge, not discovery. Earlier descriptions emphasizing immutable LINDA sharing were incomplete, not evidence that normal Monstermos excludes those endpoints.

Core worker is adding `equalizeDiscovered(allCells, adjacency, seed, immutableCells)` with actual boundary packet accounting. Service will supply unique one-layer loaded exterior endpoints adjoining finite cells; those endpoints do not cause an unbounded exterior-world traversal. A finite 20-mol cell adjacent to an immutable 40-mol endpoint averages toward 30 while the endpoint stays at 40; finite 60 adjacent to immutable 40 exports toward 50. This remains ordinary Monstermos, not generalized ambient explosive decompression.

Space-only refinement worker `ses_efd811e60ffeaLFd9ivFTjaJOK` owns `MonstermosSpaceFlow.java`/tests now; normal immutable-endpoint worker owns only `MonstermosEqualization.java`/tests. Space refinement adds an explicit trigger-seed overload so initial BFS order matches the source, and counts finite plus unique Space endpoints within the total 2,000-tile bound. Service should pass the current job seed to that overload when available.

## Integrated runtime invariants

- Monstermos and LINDA share activation, not ownership of a destructive seed queue. Monstermos admission leaves active positions available to LINDA; inactive mole gradients do not imply inactive species or temperature gradients.
- Normal and Space Monstermos bookkeeping are separate from LINDA's processed identity. Space progression also leaves finite cells and their immutable exterior faces eligible for later LINDA sharing.
- Unknown loaded passable source seeds request ownership before returning from preflight. Automatic topology/activation work must not depend on a human or fixture calling `sample` first. Solids and unloaded chunks do not acquire fictitious finite gas.
- All substantial stages use a shared soft elapsed deadline with coherent-operation checkpoints. Group cycle identity/counters and hotspot source positions persist across resumptions. Atomic proof validation or patch publication may overrun the soft deadline.
- HOTSPOTS snapshots positions, not just queue length; newly spread receivers remain for a later pass. Scheduled hotspot calls never synthesize a whole-air reaction missing from the current LINDA pass.
- A newly valid hotspot must leave the current pass even when its position was previously admitted as generic reactive work. Defer it without removing other original sources.
- Newly created hotspot metadata carries current-cycle LINDA provenance. Already-processed recipients must not incur an invented extra first-process delay; unprocessed recipients still skip their first hotspot pass. This does not alter current HOTSPOTS snapshot eligibility.
- Entity fire exposure follows committed valid hotspot processing, not reaction-event presence, and uses post-hotspot temperature. First-process skips/fizzles emit no tile-fire exposure.
- Proportional removals/breath samples consume both cleaned split outputs for finite air, packet only for immutable air. Empty exhaled lung state is no exhalation; nonempty exhaled gas uses the common Merge operation.
- Realized immutable LINDA and group exchange deltas publish only after successful transactions. LINDA uses pre-chemistry shared mixtures; pending group work emits no boundary delta until its atomic completion.
- Monstermos and ownership may drain multiple bounded slices within one stage's shared soft 3 ms budget. The 384-probe/128-claim limits are per slice, not aggregate callback caps. Normal processed membership comes from the solver's included gas nodes; processed seeds are skipped, but previously processed neighbors remain traversable for overlapping solves.
- `/ms14atmoscheck <pos>` exposes read-only cumulative wall/game-time throughput, completed cycles, current stage/age, source/patch visits and ownership progress/parking. Use counter differences for live diagnosis; nominal 15 Hz admissions do not establish station-wide 0.6-second updates.
- LINDA resolves archived state only for its local input/adjacency endpoints; it never enumerates the entire cycle archive or prior processed set for a one-source batch. Prior processed membership is borrowed read-only.
- Ownership search admission skips already-classified finite/exterior seeds; exterior skipping requires loaded/passable recognized classification. Invalidated proofs and unresolved loading retain discovery/retry behavior.
