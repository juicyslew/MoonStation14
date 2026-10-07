# Station-scale live follow-up

## Report and acceptance boundary

Live testing after the foundation gate reported prolonged provisional ownership, persistent producer-side pressure/composition plateaus, slow fire/heat propagation, and multi-minute drainage into breathable ambient. Movement sometimes coincided with visible gas-front advancement. The earlier 1,852 JUnit / 73 focused GameTest acceptance did not establish large-station throughput or connected-client behavior.

Keep 15 Hz admissions, source chemistry/transport constants, generator rates, loaded-only ownership, and atomic publication unchanged. Do not force faster behavior by raising diffusion or fire constants.

## Verified code findings

- **Premature Monstermos yielding:** the service ran one bounded discovery step per admitted callback, irrespective of remaining time. A pending job held the cycle before LINDA and HOTSPOTS. Upstream `AtmosphereSystem.Processing.cs:299–321` drains queued equalization work until its budget checkpoint. One stage per admission does not mean one discovery batch per admission.
- **Normal processed-membership mismatch:** the service marked its whole finite input patch processed, while the core selects the first 200 BFS gas nodes, including immutable endpoints. Omitted finite cells could therefore be excluded from further equalization in that cycle. Upstream stamps only included gas tiles; inactive seeds are stamped individually (`AtmosphereSystem.Monstermos.cs:55–58,102–135`).
- **Artificial patch barriers:** discovery treated cells processed by an earlier Monstermos solve in the same atmosphere cycle as blocked topology. Upstream increments its BFS queue identity per solve (`Monstermos.cs:61–66`) and guards only same-solve revisits in neighbor discovery (`:80–90`). Previously processed cells remain traversable for later seeds, allowing overlapping solves across 200-node patch seams. Retain the processed-seed guard; do not use it as a neighbor barrier.
- **Ownership is not waiting for gas edits:** sampling unknown cells and topology/lifecycle events schedule classification. Gas writes do not invalidate topology proofs. Ownership currently advances only in REVALIDATE, so long intervening stages also delay classification.
- **Local ownership bounds:** the former REVALIDATE invocation stopped at count limits even when time remained. It now drains bounded slices within the shared deadline, stopping on no progress or rejected proof. Searches at the 131,072-candidate cap still park as saturated; unloaded boundaries also park. Never turn an incomplete search into a finite claim to improve responsiveness.
- **Diagnostic gap:** last-stage timing cannot establish actual wall-clock admission rate, completed-cycle rate, processed-source throughput, or classification progress. Those must be measured separately.

## Still unproven

No live measurement yet establishes actual server TPS, completed-cycle duration, station component size, saturated ownership work, or whether stationary-client visualization misses updates. The scheduling and membership findings are real code defects, but their contribution to each reported symptom still requires a live retest.

Visual delivery is mutation-driven, with snapshots on chunk watch/resync and budgeted delta flushing every seven game ticks. It is not movement-only. Rendering selects nearby cached cells (48-block range, nearest 2,000 candidates), and snapshot backlog can delay visuals. No missed-update defect was established by the static trace; do not add periodic broadcasts to conceal simulation delays. Visual payloads do not contain pressure.

The adapter's 2,000 discovery-candidate bound counts walls/exterior probes as well as finite cells; upstream's hard solve bound counts discovered gas tiles. This is a separate bound-domain issue, not permission to raise constants without defining the adapter's safety and continuation behavior.

## Validation

The scheduling, membership, overlapping-discovery and ownership-budget repairs are implemented. Independent source/test review passed. Automated evidence:

- More than one bounded discovery slice can run in one MONSTERMOS admission when budget remains; expiration retains its cursor and prevents starting another stage.
- Only the normal solver's included finite membership is marked processed; omitted finite cells remain eligible. Space-flow membership retains its own rules.
- A later unprocessed seed can discover and transfer through an earlier solve's included cells, without rerunning those cells as processed seeds.
- Ownership can reuse the REVALIDATE budget without spinning on parked searches or failed witness proofs; partial proof never becomes a finite claim.
- Telemetry distinguishes wall-clock admissions, full cycles, stage age and actual transport/classification work. Reading it never schedules work.
- Fresh numerical and existing focused world gates remain green; default full fixture compilation is restored afterward.

`build/atmosphere-parity-validation/live-followup-gate-01/` contains **1,862 JUnit tests across 275 suites with zero failures/errors/skips**, and **all 73 required focused GameTests passed**. The ownership-tail fixture was enlarged to retain partial-claim coverage now that an admission can publish multiple slices. Default `compileGametestJava` was restored after selection, and `assemble` produced `build/libs/moonstation14-0.0.1.jar`.

`/ms14atmoscheck` now reports lifetime wall-clock callback/admission Hz separately from game-time rates, completed cycles, current stage/age, LINDA sources, equalization patch/cell visits, ownership probes/claims, active candidates and parked/rejected work. Counters are cumulative; compare their differences over time. Cell visits are not unique station coverage. Diagnostic reads do not schedule work.

Large-station/client retesting remains separate. Compare producer/front/far-end/breach diagnostics over time and distinguish authoritative cell values from visible-cloud thresholds.

### Minimal live retest after rebuilding

1. Reproduce the sealed-room producer/front/far-end setup. Run `/ms14atmoscheck x y z` in air at each location, then repeat after 10–20 seconds; retain the output.
2. Check a still-provisional air cell and repeat after the same interval. Capture ownership probes/claims, parked reasons and stage age alongside its status.
3. Open the ambient breach and repeat at its inside face. Compare actual pressure/moles, completed cycles and processed work—not just visible-cloud expansion. Record whether leaving/reentering still changes what is displayed.

## Second live retest: locality and cache churn

Pressure balance improved after the scheduling/overlap repairs. However, open-door classification flickered, and ignition/fire/water-vapor/extinction appeared as widely separated room-wide episodes, including a visible lag spike. Ambient ventilation remained slow. No diagnostic captures have yet established actual TPS or stage duration for this retest.

Two additional source defects were identified:

- **Quadratic LINDA preparation:** each local one-source batch copied the entire accumulating cycle archive into mutable states and copied the entire previously processed set. The caller already loops multiple batches within its deadline; the defect was global preparation per local batch, not another mandatory one-source-per-stage yield. Archive preparation now uses only referenced local cells/endpoints; processed checks borrow read-only membership plus the current batch's processed set. A regression rejects enumeration of either global input. Numerical order and once-cycle archive behavior remain unchanged.
- **Redundant exterior classification:** queued seeds covered by an already accepted exterior proof were not skipped when starting ownership searches. Redundant overlapping proofs could consume the unchanged 4,096-reference cache, evict prior proofs and sustain further search work. Search admission now skips already-classified loaded/passable exterior seeds as well as finite seeds. Invalidated, blocked or unloaded cases retain their appropriate discovery/retry behavior. No cache expansion or incomplete-proof acceptance was introduced.

Static independent review passed. Fresh consolidated validation in `build/atmosphere-parity-validation/linda-locality-gate/` passed **1,866 JUnit tests across 275 suites with zero failures/errors/skips** and **all 73 required focused GameTests**. Full fixture compilation was restored, `assemble` rebuilt the mod JAR, and disposable server worlds were removed after preserving logs/XML. This establishes the code corrections, not their live-station impact.

For the reported hot room, 45 mol at 5,000 K in a 1,000 L cell gives roughly 1,870 kPa. Normal Monstermos targets moles rather than pressure; the large excess pressure here is predominantly thermal. LINDA gas/heat sharing must therefore be measured, rather than assuming the pressure reading requires vacuum-style decompression. Upstream high-pressure movement acts on entities, not a missing general ambient advection solver; solid superconductivity is disabled by default. This distinction does not excuse the reported lag or long stage pauses.
