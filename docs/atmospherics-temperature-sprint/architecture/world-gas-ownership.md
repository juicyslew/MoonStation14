# World Gas and Temperature Ownership

## Status and ownership

The experimental world-cell runtime is server-authoritative, but acceptance is incomplete. `enableAtmospherics` is COMMON config, sampled at `ServerAboutToStartEvent`, and defaults to `false`; restart is required. The service is inert while disabled, although device block-entity tickers perform a cheap enabled check. The coordinator actually ran `.\gradlew.bat test --rerun-tasks --no-daemon` and `.\gradlew.bat build --no-daemon`; both reported **SUCCESS**. `compileGametestJava` ran during the test task, but GameTests were never executed in a server world (no game/server launch). **M0–M5 acceptance remains open.** No SS14 parity, comprehensive transactional guarantee, owner smoke, or 20-player result is established. This subsystem does not own entity lungs, respiration, oxygen saturation, or body temperature; `Oxygenate` and `AdjustTemperature` remain unsupported.

| Concern | Current owner | Boundary / limitation |
| --- | --- | --- |
| Enable and dimension ambient policy | `Config` → server-about-to-start handler → `AtmosphereService.configureAtServerStart` | Default-off and restart-only. Empty vacuum-dimension list means directly exposed ambient is breathable everywhere. |
| Cell gas and thermal state | `AtmosphereService` + sparse `AtmosphereChunkData` attachment | Missing override derives ambient unless position is classified direct exterior. No room object, aggregate, or station envelope. |
| Direct exterior seed | `AtmosphereService.isExterior` + `MOTION_BLOCKING` heightmap | Loaded, passable cells at/above first-free Y seed exterior; heightmap alone does not own covered cells or prove enclosure. |
| Dynamic ownership | `AtmosphereService.ownership` + `AtmosphereOwnershipSearch` | Resumable six-face sky search; only complete loaded enclosure proof yields a persistent finite claim. Unknown/saturated searches fail closed. |
| Passability / adjacency | `AtmosphereTopology` | Six-face block adjacency; full collision and closed doors/trapdoors/gates seal, open ones pass. Partial shapes pass; no face/orientation airtightness. |
| Active processing | `AtmosphereEventHooks` → `AtmosphereService` | One pipeline: bounded total-moles patches plus local species/heat exchange. 800 cells/patch, 8,000 discovery candidates, with continuation seeds. Not an SS14 solver port. |
| Scheduling and persistence retries | `AtmosphereService.WorkQueue`, priority seeds, dirty chunk cursors | 8,192-position hot queue, coalesced chunk retry records, ordered `nextAfter` cursor with version checks, and per-column snapshots for exposure changes. Cursor/index behavior has focused unit coverage; priority overflow/recovery and long-region fairness are unit-tested only. Total overload behavior and world-scale memory/progress need further tests. |
| Finite claims | `AtmosphereChunkData` schema v2 | Claims persist independently of gas overrides; legacy schema-v1 overrides become claims. Claims override heightmap exposure after breaches. |
| Exterior export | `AtmosphereService` transient `BoundaryLedger` | Species and energy exported to adjacent open sky are recorded in per-level memory; not persisted. Unloaded chunks are closed/unknown, never exterior sinks. |
| Public world query/mutation | `AtmosphereService.INSTANCE` | Strict `sample` returns empty for UNKNOWN ownership; writes and simulation use that fail-closed path. Direct exterior cells reject mutation. |
| Diagnostic presentation read | `AtmosphereService.readAtmosphere` → `AtmosphereReading` | Loaded, passable UNKNOWN cell returns a numeric PROVISIONAL snapshot (stored gas if available, else dimension ambient). Presentation-only; it does not claim ownership, materialize state, or authorize simulation/writes. Unloaded, blocked, out-of-bounds, or disabled targets have no reading. |
| Device blocks and analyzer | `ModBlocks` / `ModBlockEntities` / `ModItems` | Four placeholder blocks plus handheld analyzer; registration places five atmosphere items in custom Moonstation creative tabs. Device readout samples its front cell; analyzer uses the clicked passable cell, or for an impassable clicked block targets the clicked-face adjacent cell. Server-world acceptance remains open. |
| Entity, client HUD/synchronization | No owner in this subsystem | No body effects, atmosphere HUD, or synchronized client state. |

Chunk load work is deferred until chunks are `FULL`, with at most 16 non-forced lookup attempts per tick. Dirty retry traversal is bounded per tick and uses the attachment's ordered `nextAfter` cursor; chunk mutations increment a version so the cursor can complete and restart a subsequent pass rather than lose inserts behind its cursor. Per-column snapshots limit exposure cleanup to one column. These mechanisms have source review and unit coverage, not executed lifecycle GameTests; unloaded seams and sustained mutations remain unverified in a world.

## Cell model, ambient, and exterior ownership

### Current dynamic ownership implementation (supersedes the earlier heightmap-only description below)

The `MOTION_BLOCKING` heightmap is only an exterior seed: loaded, passable cells at or above first-free Y seed sky exposure; it is not a roof/enclosure test and does not inspect skylight. An unclaimed covered cell is initially UNKNOWN and enters resumable six-face `AtmosphereOwnershipSearch`. Encountering a sky seed makes the open cells exterior; only exhausting the search with all probes loaded and no sky seed proves FINITE. Existing finite claims and blocked cells are search barriers. Unknown unloaded probes and the 131,072-candidate cap fail closed as UNKNOWN/SATURATED, never as finite. Service budgets are 768 ownership probes and 256 staged claims per due tick.

Schema-v2 chunk data persists finite ownership claims separately from gas overrides; schema-v1 overrides are promoted to FINITE claims so legacy stored gas is not silently deleted. A finite claim takes precedence over the heightmap and survives a breached door. Direct sky claim also remains finite, avoiding immediate gas deletion, while its adjacent open sky face provides an exterior sink. There is no permanent envelope, room column, or fixed station; a sky-connected cell beneath an overhang can be exterior.

Claim plans commit incrementally. A crash can leave a partial persisted claim set; committed claims survive canceled/restarted in-memory plans while the remainder is searched again. Whole-room demolition has no explicit claim-release policy, so formerly enclosed cells can remain FINITE. Heightmap geometry can misclassify custom layouts, and there is no exact SS14 floor/map identity. Topology-edit gas displacement is unresolved. See the [M3 audit](../audits/m3-dynamic-space-ownership.md); the [M2 audit](../audits/m2-2026-09-26-sky-boundary-and-equalizer.md) is an archived earlier baseline.

Each block position is a fixed 1 m³ gas cell. Pressure derives from total moles and temperature via the ideal-gas relation; gas-specific molar heat capacities define thermal energy. Default ambient is 21% oxygen / 79% nitrogen at 293.15 K and 101.325 kPa. A dimension listed in `atmosphereVacuumDimensions` instead uses zero-gas nominal 2.7 K ambient for directly exposed cells. Empty/default means directly exposed ambient is breathable in all dimensions, including the End/custom dimensions. The gate and dimension list are sampled at server startup.

There is no permanent station envelope, fixed station bounds, room identity, aggregate, or auto-fill. A building/room can be constructed anywhere. Missing overrides normally inherit ambient; explicit changes are individual finite-cell overrides. Whether cells contain previously ambient matter is not resolved by adding walls, removing walls, or changing exposure. Do not claim closed-system conservation on topology edits.

The heightmap rule is only an exterior seed heuristic. See the current dynamic ownership implementation above: covered unclaimed cells require a resumable six-face connectivity search; they are not inherently finite simply because a roof is above them. This replaces the prior heightmap-only description. A finite claim overrides a breached opening and gas drains by local neighbor exchange to adjacent sky. Under-overhang cells connected to sky can be exterior. Skylight, geometry, and custom map intent are not fully modeled; this remains approximate and is not exact SS14 vacuum behavior.

Export through an adjacent open exterior face records species and thermal energy in a transient per-level ledger. Ledger values are runtime-only; persistence, reset, sign/accounting presentation, and restart semantics need an owner decision. A missing/unloaded chunk is not an exterior boundary and is not force-loaded to continue work. A direct-sky claim remains finite to avoid immediately deleting gas.

## Devices

Four registered placeholder blocks are an air producer, proportional gas sink, heater, and cooler; the handheld analyzer is the fifth atmosphere item distributed across custom creative tabs. Device assets use placeholder vanilla textures. Devices are server-only, gated, and run at a one-second (20 game tick) cadence. The producer adds up to 20 mol/s breathable mix and computes a partial dose when needed to remain at or below 202.65 kPa; the sink removes up to 20 mol/s proportionally; heater adds 40,000 J/s; cooler removes at most 40,000 J/s and floors at 2.7 K. The direct-exterior cells are immutable, so devices targeting them do not mutate atmosphere. Empty-hand device readouts and analyzer sample behavior are implemented, but server-world interactions and owner smoke remain unverified.

### Diagnostic readouts and door targeting

Strict `sample` is not weakened for diagnostic UI. It returns no sample while a loaded passable cell's ownership is UNKNOWN, and all writes and simulated device rules continue through this fail-closed physical-state path until finite/exterior ownership is proven. `readAtmosphere` is a separate presentation-only read: for a loaded, passable UNKNOWN cell it returns an `AtmosphereReading` with PROVISIONAL status and a numeric fallback, preferring saved gas at the position and otherwise using dimension ambient. This does not claim finite ownership or create saved gas. The formatter identifies the provisional status so the number is not mistaken for authoritative state.

A full-collision closed door is impassable and its block position is not a gas cell. The analyzer first attempts the clicked position, then, when that target has no reading, the adjacent position in the clicked face direction. Thus a closed-door interaction can show a provisional/known reading for adjacent passable air, but cannot fabricate atmosphere for the door itself or scan through it. If the target side is also blocked, unloaded, out of bounds, disabled, or otherwise has no gas cell, show the explicit “No gas cell on the clicked side” response. Future full-tile airlocks may be modeled, but no gas-bearing door-block-cell behavior is claimed now.

## Processing, solver, and topology

One server atmosphere pipeline includes two bounded strategies:

1. **Bounded patch equalization:** discovery probes loaded/passable finite cells connected over six faces, treating direct exterior as a boundary and unloaded chunks as unknown. A complete or ready finite patch contains at most 800 cells and equalizes total moles to the patch average while moving donor composition and enthalpy. Discovery has an 8,000-candidate hard limit. Hitting a patch/discovery limit is not completion of the connected region and does not prove “whole room” or “space”; unclassified frontiers are scheduled as continuation seeds. This is Monstermos-inspired in concept, not the SS14 algorithm or parity.
2. **Local neighbor exchange:** pairwise gas/species and heat processing runs alongside patches, preserving local gradients and enabling gradual exchange between a finite cell and an adjacent immutable exterior. It is an approximation with neighbor ordering effects, not a room-wide guarantee.

Mutation/topology changes create priority seeds with FIFO interleaving; the scheduler also has an 8,192-position hot queue and coalesced per-chunk dirty retry work. Ordered attachment traversal uses `AtmosphereChunkData.nextAfter` with a version-checked cursor, and exposure cleanup snapshots only the affected column. Cursor/index behavior has focused unit coverage; priority overflow/recovery and long-region fairness are unit-tested only. Per-tick work is capped, but these mechanisms do not prove a total bound on scheduler memory, real-world fairness, or progress under arbitrary sustained load. No measured 800-write tick CPU cost is available. Preflight/write cost for 800-cell patches and rollback/concurrency behavior are unverified; do not claim comprehensive transactionality.

Full collision blocks and closed doors, trapdoors, and gates are treated as sealed; open variants pass. Partial collision shapes are treated as passable. Airtightness is not face/orientation-aware and fluid interactions are undefined. Topology edits can change which cells are considered direct exterior and can create/remove/reinterpret implicit ambient matter. Unloaded/inaccessible chunks are closed exchange boundaries, and lookups use `create=false`. Seam behavior and mass displacement/conservation have not passed executed server tests.

## SS14 reference comparison (no parity claim)

SS14 map/grid atmosphere has finite tile mixtures, implicit map/space defaults, and its own processing strategies, including LINDA and Monstermos. One possible future parity model is that map tiles start as space and defined grid/floor tiles become non-space; this is an alternative to the current search/history-based ownership approach for evaluation, not an implemented behavior or decision. This project has sparse fixed-1-m³ Minecraft cells, a configurable dimension ambient, a heightmap direct-sky heuristic, local exchange, and bounded total-moles patches inspired by one broad solver concept. It does not implement exact SS14 space ownership, room aggregation, or SS14 Monstermos. No new block assets or runtime tile-parity behavior are claimed. The topology, partial discovery, and semantics differ; do not claim that vacuum behavior is “exactly SS14.”

## Ordered acceptance milestones

1. **M0 — default-off safety proof (OPEN):** prove default false leaves service reads/writes/processing inactive; confirm restart semantics and saved-world migration warning.
2. **M1 — server-world GameTests (OPEN):** execute compiled `AtmosphereRoomGameTests`; test actual query, persistence, lifecycle, disabled-world and chunk-seam behavior. Compilation alone is not acceptance.
3. **M2 — topology/persistence/conservation (OPEN):** test roof/glass/no-skylight End, overhangs, door-to-space drainage, partial shapes, topology edits, unloaded seams, continuation, exterior policy, and ledger accounting/persistence. Matter conservation on topology changes remains unresolved.
4. **M3 — devices/API (CODE IMPLEMENTED; ACCEPTANCE OPEN):** verify four device blocks, analyzer, five custom creative-tab entries, server effects, interactions, and owner smoke.
5. **M4 — scale/performance (OPEN):** measure 800-cell preflight/write cost, >8,000 candidate progress, retry/queue behavior, and at least 20-player performance. No results exist.
6. **M5 — parity matrix / further systems (OPEN):** compare with evidence and document deviations. No full SS14 parity is claimed.

## Required follow-up gates

- Execute server-world fixtures for roof/glass/no-skylight End, under-overhang exchange, and door opening to exterior.
- Verify unloaded seam behavior and sustained continuation through connected finite regions exceeding 8,000 discovery candidates.
- Measure 800-cell preflight and write costs; exercise concurrent mutation/failure and specify rollback expectations before making any transactionality claim.
- Obtain owner decisions for mapped exterior policy, topology-edit mass displacement/conservation, and transient boundary ledger persistence/accounting.
- Profile and benchmark with 20 players. No server-world fixture execution or 20-player result is currently evidenced.
# Verified exterior opening pressure policy

An airtight finite patch continues to use normal graph equalization. A connected
patch with a verified exterior opening uses bounded graph flow instead: vacuum
dimensions keep the existing space-flow decay; breathable dimensions transfer
donor-composition, donor-enthalpy packets toward lower pressure over real face
edges, including inward flow from the read-only ambient reservoir. Exterior gas
is sampled during preflight and is never persisted. Mixed vacuum and non-vacuum
exterior face snapshots fail closed. Invalid topology or unknown/unloaded faces
cannot become openings; a failed patch transaction rolls back finite writes and
does not publish its signed boundary ledger.

For a breathable exterior, the ambient graph route has no bulk transfer at
equal total pressure and currently no cross-boundary species diffusion at
equal total pressure, even when the finite gas and ambient have different
compositions. Equal-pressure composition mixing is intentionally deferred
future scope, not SS14 parity. The vacuum space-flow behavior is likewise not
claimed to match SS14 exactly.

The fallback local `exchangeWithExterior` remains available when the graph job
is unavailable. It exchanges only the immediately adjacent finite cell (and
uses the legacy local thermal/space behavior), so its short-term gradient and
heat behavior, including equal-pressure mixing, may differ from the room-scale
graph pass. Both paths leave the exterior immutable. Graph routing is bounded
to 800 applied finite cells; an
oversized/incomplete graph must not publish partial states or ledger changes.
