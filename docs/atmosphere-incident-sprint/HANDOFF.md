# Atmosphere transport, breach performance, and fractional fire

## STATUS
Implemented and accepted for the automated scope below; live-station acceptance remains open. All changes are uncommitted. Preserve the earlier explosion, chat, and lung work. This is the atmosphere follow-up, not completion of grenade chain reactions.

## ARCHITECTURE
- `AtmosphereService` owns loaded-only finite claims, discovery, atomic solver commits, LINDA scheduling, and bounded reaction queues. Atmosphere runs every two Minecraft ticks; reaction work admits eight cells and LINDA ten sources per due step.
- Normal Monstermos balances total moles through actual face-connected routes; LINDA subsequently shares species and heat. Normal finite patches must remain eligible for LINDA in the same due cycle. Blanket deferral starved sharing under continuous mole changes. Ambient/vacuum boundary kernels retain handled-cell exclusion and boundary admission guards; do not remove those guards.
- Normal routing reuses lazy donor-rooted BFS trees; deterministic routes and 8,000 search/transfer ceilings remain.
- Accepted exterior proofs wake loaded finite boundary neighbors; rejection does not. This restores venting work consumed while an opening was UNKNOWN.
- Ambient exchange retains pressure-directed packets, 48 scalar bisection iterations, relaxation/caps, and signed boundary accounting.
- Fractional fire stores volume fraction and hot temperature; energy remains in the mixture. Composition merges uniformly; hot temperature is projected from active energy and updated heat capacity. Spread-source debits reconcile metadata before publication.
- Successful non-reaction gas writes reconcile queued hotspots against the new mixture; atomic patches reconcile only after commit. Raw reaction writes retain their own publication path. Failed transactions must not publish reconciled metadata.
- Cancelled partial finite-claim plans restart from uncommitted suffix candidates, not an already-claimed seed. Candidates require fresh proof. Retry checks, stale-seed removal, and search share the 384-check budget; claims retain a separate 128-position cap. Unloaded candidates use ordinary discovery/chunk parking; capacity failures are charged and deferred without blocking loaded tails. Pending diagnostics include retained retries.
- Face heat is `0.25 × recipient heat capacity × (0.85 × source temperature − recipient bulk temperature)`. Unaffordable offers are rejected, not credited freely; a very hot, low-inventory source can therefore fail to heat a dense neighbor despite its pressure.

## IMPORTANT FILES
- `GAS_PARITY_AUDIT.md`: source-comparison inventory and required foundational parity pass. Read before further gas/fire feature work; existing automated acceptance is local regression acceptance, not general SS14 parity.
- `src/main/java/com/juicyslew/moonstation14/ms14/atmos/world/AtmosphereService.java`: orchestration, source reconciliation, diagnostics.
- `.../atmos/world/AtmosphereTransportDiagnosticCommand.java`: operator-only `/ms14atmoscheck <pos>`; loaded-only cell and six-face readings, stage timings, queue/ownership counts, boundary ledger.
- `.../atmos/core/MonstermosEqualization.java`, `MonstermosSpaceFlow.java`: routing and ambient numerical optimizations.
- `.../atmos/reaction/HotspotKernel.java`, `GasReactionStep.java`, `HotspotSpread.java`: partition, eligibility, quarter-volume energy-backed offers.
- `.../atmos/world/ReactionWorkQueue.java`: stored cold-bulk hotspot admission and deferred recipient scheduling.
- `src/gametest/java/com/juicyslew/moonstation14/gametest/AtmosphereLoopRoomGameTests.java`: sealed nonconvex loop, capped producer and shutoff observations.
- `AtmosphereReactionGameTests.java`, `AtmosphereRoomGameTests.java`: fractional and legacy fire fixtures, door/breach regressions.
- `AtmosphereAmbientBreachGameTests.java`, `AtmosphereFractionalChainGameTests.java`: proof-completion vent/reseal and sustained three-cell fire regressions.
- `AtmosphereHotFrontGameTests.java`, `AtmosphereHotspotMutationGameTests.java`, `AtmosphereCraterOwnershipGameTests.java`, `AtmosphereOwnershipTailRetryGameTests.java`: continuous-write thermal progress, actual queued metadata, fresh blast/creative floor comparison, and interrupted claim recovery.
- `src/test/java/.../atmos/`: routing, boundary, hotspot, transaction, ownership scheduler, and retry tests.
- `build.gradle`: `-Pms14GameTestClasses=ClassA,ClassB` selects fixtures and clears stale output. Omit it to restore full compilation.
- `docs/explosion-effects-sprint/plan.md`: earlier explosion validation and withdrawn LINDA experiment.

## SS14 CORRESPONDENCE
Reference: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, local checkout `C:\Users\William\Documents\SS14-dev\space-station-14`.
- SS14 Monstermos follows open adjacency and total-mole imbalance, not room convexity or pressure alone. Soft/hard limits are 200/2,000 tiles. Processing defaults to 15 steps/second with a 3 ms resumable processing budget; local whole-patch synchronous processing is not equivalent.
- Space explosive depressurization is distinct from ordinary gas-bearing neighbors. The owner's default Overworld exterior is approximately 101 kPa air, NOT vacuum.
- Upstream `tile.Space` is independent of the map mixture. Its explosive branch passes a mole-derived target pressure to `ReleaseGasTo`; finite ambient can reject it. Neither the flag nor high pressure guarantees immediate decompression. Do not add pressure-driven thermal advection silently.
- SS14 bypass requires incoming fraction strictly above 0.95; spread requires viable fire and post-reaction bulk temperature above 423.15 K. Offers use quarter-volume at 0.85 source temperature; upstream exposure multiplies volume by 25. Local energy-backed offers, uniform-composition projection, and additive growth approximate upstream timing. Upstream assigns reacted extent × growth rate rather than adding it.
- Human inhalation is 0.5 L every two seconds from the eye cell. Human inhaled tritium causes one typed radiation damage per inhaled mole with no minimum gate. CO₂ has a prototype-backed per-inhale minimum.
- Local LINDA comparison uses approximately 0.04157 mol species difference or >4 K temperature difference. Tiny CO₂ alone need not immediately trigger neighbor sharing; the toxicity threshold is unrelated.

## DECISIONS / WHY
- Do not increase generator output or diffusion constants to hide scheduling failures. The local test producer remains 20 mol/s capped at 202.65 kPa and 293.15 K.
- Keep grenade ignition, loaded-only safety, conservative species/energy accounting, server authority, no recursive same-pass receiver burn, and no vanilla fire blocks.
- One-second fire-stack damage is confirmed, not an issue requiring adjustment.
- A 2,000 kPa cell at 200,000 K contains only about 1.2 mol in 1 m³; high pressure alone does not establish a large inventory or expected mole-gradient direction.

## KNOWN GAPS
- Live outlet/far mixing, fire timing, breach venting, and movement correction require post-fix station snapshots; fixture success does not establish live resolution.
- Processing still bounds counts rather than pausing a whole solve at an elapsed-time checkpoint. The new timings are evidence for deciding whether resumable discovery/solve/commit needs further work; they are not a 3 ms SS14 processing budget.
- Pre-follow-up loop shutoff: near/far 103.832589 kPa/42.6 mol, but tritium 5.8446 versus 5.85e-20 mol. This predates the sharing fix; pressure convergence alone is not species parity.
- Follow-up repaired thermal/species starvation and cancelled claim-tail retry defects; automated acceptance is green. Breathing swaps O₂ for equal CO₂ moles; body heating could explain roughly 106 kPa locally, but the live source has not been measured.
- Radiation remains unresolved: eye-cell tritium 2.3e-5 mol predicts 5.75e-9 damage/s, not reported 2e-6/s (which needs approximately 0.008 mol). Take same-cell, timed snapshots; damage totals alone are not provenance.
- Existing grenade damage-triggered chains, exposure admission overflow/burst liveness, and connected-client chat acceptance are outside this incident and remain open.

## VALIDATION
- Final follow-up gate: **272 suites, 1,821 JUnit tests, zero failures/errors; 73/73 required GameTests**, 18 classes, 10.45 seconds in-game, normal shutdown. Default GameTest compilation restored (150 class files). This is not whole-namespace or connected-client acceptance.
- Continuous 0.1 mol withdrawal previously left adjacent/far nitrogen at 349 K for 40 due steps; now approximately **16,220/16,109 K**, with withdrawal energy/species ledgers conserved and zero boundary exports. Actual hotspot-mutation, fresh crater/creative comparison, and interrupted claim-tail recovery fixtures passed. The tail fixture changes an already-claimed seed to avoid independent new seeds masking retry failure.
- Ambient proof wakeup: **112.331 mol exported; 442.600→399.941 kPa**; resealing stopped exports. Before the fix, proven exterior with queue zero exported nothing after 40 passes.
- Fractional propagation, grenade ignition, door/boundary guards, and routing/bisection references remain covered.
- Minecraft `runAfterDelay` is relative; `runAtTickTime` is absolute. Fractional fixtures use `GameTestSequence` polling of actual due ticks.
- Whole-namespace attempts remain unaccepted: lifecycle/power failures and timeout amid legacy puddle logging. Negative-stack cleanup passed the focused gate, not the full run.
- Original evidence: `build/atmosphere-incident-validation/`. Disposable worlds were removed; shared Gradle/JDT services were untouched.
- Follow-up evidence: `build/atmosphere-followup-validation/` retains final gates and earlier failed reproductions. Three disposable follow-up worlds were removed after archiving; no process kills or shared-service changes.

## NEXT WORK
0. Establish SS14-derived numerical references and correct foundational sharing, group, boundary, scheduling, and chemistry differences documented in `GAS_PARITY_AUDIT.md` before accepting further fire parity.
1. Collect `/ms14atmoscheck <x> <y> <z>` near/adjacent/far with producer on/off and breach open/resealed. Compare kernels, exports, queues, timings, movement, and venting.
2. Compare timed `/ms14damagecheck` with exact eye-cell tritium over two-second inhales; record eye-cell temperature/moles/CO₂ for breathing pressure.
3. Diagnose remaining delays from measurements, preserving stage/boundary guards and rates. Exact hotspot timing, whole-namespace, and connected-client acceptance remain open.
