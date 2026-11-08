# Pre-gate code review

## Current working candidate — tick-state model, selected diagnostics passed

Diagnostic `run_28445f8a9c193569cb5a5bb0` passed selected headless native, JUnit,
and UI/interaction/prediction readiness checks. Result acknowledged; cleanup
CONFIRMED. Its preceding native failure was corrected in fixture scheduling only,
preserving runtime tick guards and strict assertions. Connected behavior remains
unverified; this is not feature readiness or owner acceptance.

Tick-based input state, client clock/replay, server batch folding, and held/natural
flight snapshot seeds are wired. Current focused JVM XML reports 411 tests across
55 suites with no failures/errors; the earlier 525/54 count was not corroborated.
The log records main/GameTest compilation; independent client and server
reviews passed. The selected diagnostic above now covers this model, replacing the
interim message-per-step FIFO described in older receipts below.

After the interim FIFO retest, the owner reported larger occasional corrections
and circle-jump acceleration. The retest aborted idle coverage (owner confirmed
the phase instructions were not followed), so it is not a complete performance
measurement. Source analysis established FIFO backlog latency and a separate
gravity-air projected-acceleration growth path. The scalar voluntary-acceleration
correction passed 61 focused tests and independent source review; existing impulse
axes, grounded/space/natural-flight/external-flight behavior remain separate.
Failed-phase metric persistence passed six focused tests and source review.

See `INPUT-TIMING.md` for the implemented state/time separation and boundaries.
No new complete feature-readiness claim is made. The diagnostic below validated
the interim FIFO, not these subsequent changes or owner-visible resolution.

## Current input-cadence correction — headless diagnostic PASS

`run_656a313543a7f68b0fc55ec5` generation 1 passed; acknowledgement and cleanup
are confirmed. Filtered native tests: 39/1,070 discovered, all 39 passed in
8.193 s, including `body-input-cadence`. Selected JVM execution and
interaction/prediction readiness passed; local focused validation passed 52 tests
and compilation. Bound native log:
`artifact_task_656a313543a7f68b0fc55ec5_log_2b4e0dec19e5d8a576f7`.

`GhostIntentGate` now queues increasing sequences rather than dropping arrivals
merely sharing a server tick. Its shared 64-frame bound also governs prediction
history and all three controllers' admission budgets. Consumption is at most
once per authoritative tick, including an empty attempt. Epoch/reset, sequence
exhaustion, role admission, and applied-only acknowledgement remain intact.
Overflow stays bounded and may still settle skipped sequences; a backlog adds
latency. This is a Minecraft fixed-step adaptation, not literal Robust input
dispatch: pinned Robust `3136118b5338ef2d9580178caf5c723e65eb76e7` handles valid
commands on arrival and maintains held state separately (`Robust.Server/GameObjects/EntitySystems/InputSystem.cs`).

Owner-reported Parrots rubberbanding remains unverified after this fix. Existing
captures lack per-intent arrival/rejection/ACK timing and do not prove causality.
No further human capture is authorized until the owner returns. The older
receipts below predate this correction and are historical for the current source.

## Latest owner-ready revision validation — managed fixture startup failure

Feature run `run_aace0e4d29035d7ed3b793bc` ended FAIL (acknowledged; cleanup
CONFIRMED). Its native selection was 34/38 passed, 4 failed, from 1,069 discovered;
all four failures were `GLOBAL_STARTUP_RECOVERY_REQUIRED` after the managed test
directory reused a persisted `world/moonstation14-lifecycle` containing an ACTIVE
profile. Unit and readiness checks passed. This is environmental fixture-state
failure, not evidence the production startup guard is incorrect; production code
and recovery policy are unchanged. The old fixture was atomically preserved under
`build/agent-workflow/nonhostile-animals-newflow/failed-fixtures/` with a small
provenance record. The next owner-ready revision must use a fresh managed directory
and rerun native validation.

Earlier diagnostic `run_8fb86e98fac3dc773be9ce4e` and its log are historical only;
they are not attached as current evidence or a substitute for the new native run.
Visual review, connected observer/input behavior, audio playback, sustained play,
and owner acceptance remain unverified.

The owner reported defects after the passing feature gate
`run_386c0ea62b5d916c24781d9b`; the historical receipts below predate these edits.

- Trusted development offer origin now reaches the committed character session;
  operator-development movement does not require an ordinary GhostRole component.
  Ordinary role movement restrictions remain. The extended development-possession
  fixture checks real accepted intent, authoritative motion, emitted snapshot,
  and input release; the selected native fixture now passes.
- NPC idle flight retains cruise altitude across arrival/wait/replanning. Elevated
  idle routing reuses bounded route-attempt support resolution. Motor command yaw
  is separate from travel-facing yaw, avoiding a backwards-looking turn to brake.
- Requested appearance reductions reuse adapter scale in world and portable
  rendering. Gorilla model scale stays unchanged; collision is enlarged separately.
  Monkey diaper UV shells remain present with non-coplanar side faces.
- Ground contact is measured on animated foot geometry, or insect body geometry
  where feet do not exist. Hidden gore is not a valid contact anchor. Grounded
  alignment uses a shared post-animation correction; focused assertions cover
  corrected contact and unchanged airborne/non-alive poses. Breathing-pose contact
  residual is bounded to 0.06 model pixels; this is CPU geometry evidence, not a
  rendered visual acceptance check.
- Teleport revalidation uses the same exact-target catalog service as takeover,
  rather than the bounded menu snapshot. Native catalog-bound eligibility passes;
  it is not an authenticated connected teleport reproduction.

Independent source reviews do not substitute for execution. Connected menu/request
completion, prediction/input release, observer replication, visuals, sound playback,
and sustained performance remain unverified. See `OWNER-FEEDBACK.md` and `MANUAL.md`.

## Previous authorized native diagnostic — run_96c745b32a9d05fda7bdd1e4

Authorized diagnostic generation 1 passed all 36 selected fixtures in 7.201 s;
parent status confirms ACK and cleanup CONFIRMED. This exercises the corrected
reachable-idle and flight fixtures, but is diagnostic evidence only, not feature
acceptance.

Latest stationary population diagnostic `run_a7b5492ba71cbfa893d1fc13` failed
after the Mixed capture completed: cleanup saw PID 17384 disappear between
verified observation and `Stop-Process` (`Cannot find process identifier`). Scope
cleanup was confirmed; profile analysis did not run. The launcher now catches
only ObjectNotFound and re-observes the exact identity; GONE/REUSED is terminal
already-exited with audit retained, while matched/unknown/query-error/access-denied
remain failures. Cheap cleanup-race tests (7), production process tests (22),
ancestry/population-protocol tests, and independent review passed. No completed
profile analysis or performance conclusion is claimed; fresh three-scenario
captures are pending.

Historical validation incident: an unauthorized worker launch used `animals_npc_flight` and
`build/gametest-tags-fixture`; it selected two fixtures and reported two passes.
This is not authorized workflow evidence and is not counted as a current pass.
The parent reports the process shut down and read-only inspection found no live
Java fixture process; full process ancestry was not captured. Preserve the incident
for audit. It is excluded from workflow evidence; do not repeat the launch.

## Reachable idle-route candidate — native validation pending

`GroundNpcRoutePlanner` now samples up to 40 uniformly from discovered reachable
tiles (including start), uses per-attempt surface caching only, shared traversable
neighbors, and target A* under existing collision/budget limits. `NpcBehaviorSystem`
consumes the route without a second A*. Start-only valid goals retain normal
arrival/wait; off-center starts use a center waypoint. Independent source review
passed, as did compilation and focused `NpcTaskPlannerTest` /
`NpcRouteWorkDiagnosticTest`; no native run has exercised this candidate. Existing
stationary profiling is not evidence of route performance improvement. See
`PROTOTYPES.md` for the pinned SS14 mapping and deliberate geometry adaptation.

## Stationary population diagnostic — run_9ef98b5c14b9fa35bb10257b

Current diagnostic result is **PASS**, generation 1, cleanup **CONFIRMED**. This is
capture evidence only, not a feature/readiness PASS. Actual local summaries,
manifests, capture-coverage records and owned-process proofs are retained under
`build/reports/nonhostile-animals/population-profiles/` in the three directories
listed below. All report 500 population ticks and rendering fraction 1; sampled
render-call medians equal each actual population. The compact timing table is
server tick duration p50/p99 (ms), baseline → early → late, and client frame p95
(ms), late:

| Scenario (population) | Server p50/p99 baseline → early → late (ms) | Client p95 late (ms) |
| --- | --- | --- |
| Mixed (40) | 4.41/24.25 → 10.36/74.11 → 4.40/25.70 | 11.43 |
| Parrots (100) | 2.92/16.00 → 14.44/113.49 → 10.87/47.70 | 11.94 |
| Kobolds (100) | 3.24/17.65 → 17.66/127.31 → 10.44/52.95 | 14.98 |

Capture folders: `population-profiles/mixed-053d9265923546118fbd9af001f2909b`,
`population-profiles/parrots-022798d8959445acb0fe5d038aa8b00f`, and
`population-profiles/kobolds-b479a1ee3d8f41099f2239cb9879b5fe`. Bound proof-gate
logs are `artifact_task_9ef98b5c14b9fa35bb10257b_log_094340644cc38b1369e6`,
`artifact_task_9ef98b5c14b9fa35bb10257b_log_1ec242d5311173ab6bfb`, and
`artifact_task_9ef98b5c14b9fa35bb10257b_log_bbc30d176686098f779f` (each exit 0).
Route `NO_PATH` work remains a lead, not an established sole cause; no optimization
is claimed. These stationary captures do not establish movement/jumping, sustained
minutes, unloaded behavior, visual acceptance or owner acceptance. No controlled
before/after comparison exists; historical captures use different geometry. Feature
overall review remains **FAIL** (`run_816abfb809a06bc4c0f14738`); its historical
28-native-test pass predates the diagnostic extension and is not replaced here.

## Latest stationary population diagnostic — run_ccdb5bbe1b31bdffb76501bc

All three stationary captures completed and cleanup was confirmed. At 500
population ticks/rendering fraction 1, late server p95 was Mixed 11.78 ms, Parrots
18.36 ms, Kobolds 22.88 ms; late client p95 was 13.45, 11.47, and 16.88 ms,
respectively. Against prior run `run_9ef98b5c14b9fa35bb10257b`, late server p95
was lower in all cases, but Mixed/Kobold late p50 rose (5.73 vs 4.40; 13.23 vs
10.44 ms) and Parrot p50 was similar (10.67 vs 10.87 ms). Early p99 rose for
Parrots/Kobolds; client late p95 worsened Mixed/Kobolds and slightly improved
Parrots. These are short stationary captures with different geometry/randomness
and warmup; causality and blanket efficiency improvement are not established.
Movement/jumping, sustained operation, and owner acceptance remain unverified.
The prior cleanup-only launcher failure is historical; its narrow process-identity
race fix passed cheap tests and independent review, but is not itself profile
evidence.

## Bound run status and new sustained diagnostic boundary — run_816abfb809a06bc4c0f14738

The bound run's overall status is **FAIL** (independent review REVIEWFAIL). Its
historical coded native selection passed 28/28 required tests, but the diagnostic
capture implementation was changed afterward and has not been natively revalidated.
Do not describe the old 28/28 as validation of the new capture or as an overall
PASS.

The opt-in `newPopulationCaptureProtocol` / server JFR accepted-step observer is
implemented. It associates accepted grounded-jump requests with the same player,
body and epoch over five 1,200-server-tick phases. It does not bypass ordinary
input, inject controls, teleport, or change AI. Its counters establish accepted
grounded-jump requests, not launch proof; ascent is measured separately. Horizontal
distance is body distance, not camera pixels, and near/outside-glass-pen samples
are not evidence of seeing animals. Sustained mixed (40), parrot (100) and kobold
(100) captures have not run. Coordinator/operator must schedule them sequentially
with `-PopulationScenario Mixed`, `Parrots`, and `Kobolds`, each with
`-PopulationSustained -WallTimeoutSeconds 900`, and provide a real joined owner
using normal keys/chat for each phase. No capture, performance acceptance, or
overall lag resolution is claimed.

Focused checks after the diagnostic changes: four protocol plus four fixture-
settling tests passed (8 total); `compileJava` and `compileGameTestJava` passed;
analyzer tests passed 20/20; PowerShell protocol tests, including timeout flag
path, passed; integrated source review passed after completion-marker and JVM-flag
fixes. The timeout-flag check verifies source contract/initialization mapping, not
Gradle preparation. These checks do not substitute for the unrun native captures.

## Historical native diagnostic — run_816abfb809a06bc4c0f14738

The bound current native log is
`artifact_task_816abfb809a06bc4c0f14738_log_7dc2da308188347683a4`, SHA-256
`6f69b0c2c90f5722a00c8b53fcfb4d81881e7a6e1b84c5d6cb4e9e87fd1a7cc1`
(217,648 bytes). Its tail records `28 GAME TESTS COMPLETE IN 6.448s`, all 28
required tests passed, and Gradle `BUILD SUCCESSFUL` (exit 0). Thus the current
bound native result passes the selected 28 required tests, including flight push,
cruise and cleaning. The selection's registered-total denominator was not
reconfirmed here; do not infer one from earlier runs.

The same run's current unit/readiness/package checks are PASS. Its other bound log
is `artifact_task_816abfb809a06bc4c0f14738_log_e1d92596970f0991d872`, SHA-256
`b8bae849eeec79f65ce19a76dc8e6179d40060d0a6100d6e044f6b7275ed72b7`, exit 0.
The workflow's coded-check decision is PASS, but the immutable overall run remains
FAIL because independent review is REVIEWFAIL. Do not relabel the overall result
as PASS or treat coded-check success as technical readiness.

The review's valid population-runtime finding remains open: prolonged physical
movement/jumping distance and time evidence for mixed (40), parrots (100), and
kobolds (100) was not executed and remains unverified. Therefore population
runtime coverage is not complete and owner readiness is not established. Owner
acceptance also remains outstanding.

## Historical native diagnostic — run_e0641a8c881e0e77912df0d0

Settled FAIL; diagnostic evidence, not readiness approval. Filtered native
selection was 28/1,063 (zero untagged), 27 passed and one failed: flyer push
regression. Prior fixed-tick-28 setup failed because the bat was already 0.456
blocks from its goal before impulse. New setup waits up to 600 ticks for actual
FLYING state, elevated active idle goal and horizontal speed >=0.02, then applies
one perpendicular impulse, retaining the original goal and allowing 500 ticks
recovery within 1,200 ticks overall. Compile and independent review passed; no
native rerun verifies this correction. Cruise and cleaning passed in the latest
run. Readiness, package, and owner acceptance remain unverified.

## Historical workflow log evidence boundary

Parent source inspection verified a structural log collision: `workflow_check_packs.py`
uses `build/workflow-fixtures/<fixture>/logs/latest.log` for both `check.log` and
the GameTest `gameDir`; `workflow_runtime.py` opens that same path for writing,
emits the gate header and redirects Gradle output there while Minecraft writes its
own `latest.log`. The bound native artifact begins with Minecraft output (no gate
header) and later has a Minecraft end followed by mid-line console output. This
undermined reliable preflight evidence; without a complete scan it did not prove
which exact marker was overwritten. The missing all-pass outcome is accounted for
by the three actual native failures above. The shared workflow owner has since fixed
the collision: `workflow_runtime.py` captures to `Path(logdir)/check.log`, separate
from the GameTest's typed `check.log`, with an alias guard. Coordinator validation
is running under replacement run `run_770d11d0d8c72f3a46d8ce8e`; no native result
for that run is available yet. The collision is a source-level fix observed, with
native validation pending, not a current blocker. Ground dispatch has been corrected
and compiled.

## Current author candidate — native validation pending

The current author check completed `./gradlew.bat test --rerun` with the existing
15 policy classes plus `NpcFlightSteeringTest`, followed by `compileJava` and
`compileGameTestJava`. The exact XML reports show 16 classes / 52 tests, zero
failures, errors or skips; retained command log:
`build/agent-workflow/nonhostile-animals-current-units.log`.

Independent reviews passed the GroundNaN overload correction and bounded failure
diagnostics. Flight steering now uses motor-consistent inverse steering and the
shared flight-command transform in both live behavior and its test. The actual-motor
test covers eight headings with 0, 0.15 and 0.38-tick impulses and finite bounds
(two JUnit methods). The altitude fixture explicitly observes a bat over 100 ticks,
asserting the meaningful minimum randomized cruise height with headroom and upward
samples; it compiled and passed review but has not run natively.

The prior native selection (28/1,063; 25 pass, three fail) remains historical and
is not current-candidate evidence. Replacement-run native validation, the current
owner's manual acceptance and sustained-performance testing remain pending.

## New-flow run_328086154a1c9fca60621a6e — settled FAIL, fixture correction

The acknowledged run failed its bounded diagnostics because the flight-height
child fixture inherited `minecraft:bat` from `moonstation14:bat`, duplicating
the production host binding. The test-only child is now a hostless derived
prototype (`host_entity_types: []`), a supported representation, and the same
production cross-catalog validator runs against the augmented catalog before
reload as well as the original shipped catalog. It retains the bat parent,
child cruise-height override (2.25), and parent default (1.5). Fresh author
validation passed all 15 selected unit classes / 50 tests with zero failures,
errors or skips; individual reports are in `build/test-results/test/`. Native/
readiness checks remain pending; this is not a coordinator PASS.

## R35 diagnostic findings — not readiness

Receipt SHA-256: `e19e3b53b5eca97e98a3ba97fdb904e5c2f9365e1b5971646cc33c4768bd0d67`.
Parrot capture passed, retained at
`build/reports/nonhostile-animals/population-profiles/parrots-49eba0db6e224693823c99e9c86115b3`.
Mixed failed during Gradle preparation, before its native client/server capture:
the launcher reported 25 remaining owned-process PIDs. The receipt's
`tree_cleanup=not_needed` does not independently resolve that script-level finding.
Retained fixture: `build/connected-join-fixture/b7b9210382a94c639c5e85d9ce0bfac8`.
Process ownership is under read-only investigation; uncertain processes must not
be killed and further native work must not be requested until the boundary is
resolved. No mixed-animal population or runtime performance result exists here.

Parent read the retained preparation log: Gradle succeeded in 10 seconds and
emitted its manifest; the subsequent owned-child exit check failed before native
server/client launch. Parent's read-only WMI observation at
`2026-10-16T13:35:55.2936268Z` found none of the 25 reported PIDs live. This does
not reconstruct their historical identities or prove the original ownership
claim. The launcher verifies its root identity but currently walks child links
by parent PID alone, allowing a potential stale-parent/PID-reuse ownership error.
Creation-time ancestry checks and retained process-proof artifacts are being
implemented before any further native request; integration-owner cleanup
verification remains necessary. Do not weaken the live-child guard or stop
uncertain owner processes.

Parrot proof includes the explicit station-disabled marker, matching join/control
and accepted first intent, 100 animals and 500 population ticks. All 1,909
population frames recorded renderer calls, median 100. Server early/late p99
was 89.15/41.85 ms; client p99 was 32.70/23.00 ms. Failed routes remain costly:
early 102 `NO_PATH` attempts, median 19.09 ms, p95 31.27 ms. Approximately 91% of
their 241,981 support requests hit the per-attempt memo (220,094 hits, 21,887
computations); remaining work included 732,213 collision probes. Thus real
population reuse is established, not an overall lag fix. Timing is not a
controlled comparison to R33's station-bearing world. Long-run and physical
movement/jumping stalls remain unverified.

Process-safety correction is implemented and source-reviewed
(`ses_ebb108e17ffeE8Sc2T97ikgtUc`). Tracking now uses captured root identities and
walker-verified parent creation times; owned records and cleanup outcomes key by
PID plus normalized creation ticks, retaining distinct identities after PID reuse.
Ambiguous duplicate PIDs are excluded, malformed identities cannot authorize a
stop, and `UNKNOWN` live observations block cleanup verification. Ownership and
cleanup proofs are retained before cleanup and alongside population artifacts.
Synthetic ancestry/identity/proof tests, population protocol tests and PowerShell
AST checks passed. No Gradle/Minecraft processes were launched or stopped during
this correction. These checks do not reconstruct R35's missing historical process
proof or clear the operational boundary: integration-lead cleanup verification is
still required before another native request. No readiness submission is pending.

## R34 diagnostic findings — not readiness

Receipt SHA-256: `b2bf7d6756a362834855cc777ab380b73c9eee79654b3fca8fa0fe677a3c9b4b`.
Native route reuse fixtures passed **4/4**, filtered **4/1,063** registered tests;
this is not a full-suite pass. The bound native log is
`build/reports/nonhostile-animals/route-reuse-r34.log` (SHA-256
`e2f6205265b866fedab77b8acb9337a1339b078c6e7b44432297e8e0c9264115`).
It verifies shared cached/uncached route parity, reduced support/collision work
on the detour fixture, exhausted search and world-change freshness. Population
timing improvement has not been measured.

Parrot capture aborted before spawning: all 18 loaded candidates intersected
station floors/walls. Body was `(9.5,-60,-5.5)`, yaw 0. Mixed did not run. Retained
fixture: `build/connected-join-fixture/5ee9247be34242be80ef6165c67f9503`.
No further broadening of the nearby search is planned; deterministic isolated
diagnostic-map setup through existing map/spawn owners is under investigation.
No normal owner world, configuration, body/camera ownership flow or gameplay
collision policy may be weakened to obtain a capture.

Trace established that `StarterStationService` independently observes new-world
spawn initialization and builds the development station on later ticks. First-join
body staging instead inherits the carrier pose; it does not select a station spawn.
Thus flat world generation alone does not prevent station obstruction. The scoped
correction is a run-local, default-enabled station-builder option, explicitly
disabled only in population-server launches. No new body spawn selector, lifecycle
admission path, post-commit teleport or station-block clearing is needed.
Station-free baseline geometry/workload differs from R33; population timings
cannot independently prove a controlled before/after improvement.

`StarterStationService.register` now defaults to ordinary registration; only an
explicit run-local `ms14.starterStation.enabled=false` suppresses both station
listeners and emits `MS14_STARTER_STATION_DISABLED`. The isolated population
server receives this property; clients, ordinary connected joins and normal
development runs do not. No SavedData or existing station blocks are changed.
Main/test compilation passed and the actual registration consumer test passed
**1/1** (XML under `build/test-results/test/`), covering default/true/false and
unexpected property values. Protocol checks passed for server-only opt-in wiring.
The collector now requires the exact opt-out marker from the current server log
before population success; only verified proof writes `starterStationDisabled=true`
to the manifest. Positive/negative marker and success-gating protocol tests passed.
Independent source review passed (`ses_ebb1cfb4affehneU1oh641Z37L`), including the
population-only launch scope, registration defaults and current-log requirement.
`diagnostic-plan-r35.json` requests only parrot and mixed captures; actual
station-free native setup, render coverage and population timings remain unverified.

## R33 diagnostic findings — not readiness

Receipt SHA-256: `abc76c26791a3c7880126a99f524cffb95dcf03fd224832035018ff9fe06a8a6`.
Parrot and kobold capture commands passed. Their retained per-case artifacts are
`population-profiles/parrots-b2aecb4b4a9c4b5d90c2bf6705bab4b3` and
`population-profiles/kobolds-cc348786194a4756a1db56cbb258b76e` under
`build/reports/nonhostile-animals/`.

Mixed failed before spawning. The preserved primary reason is fixture headroom
obstructed at `(5,-60,10)` by `station_floor[tile_finish=steel]`, while the
committed body was grounded at `(-0.5,-60,7.5)`. This establishes a fixture
placement obstruction, not a mixed-animal runtime crash or measured lag. Retained
fixture: `build/connected-join-fixture/d25e0240e2c24dacb471f92d2923bd1c`.
Placement investigation and quantitative route-work analysis are pending; no
performance fix, native functional/visual pass or manual readiness is established.

R33 analysis confirms retained matching epoch/body, Ready, camera/control and
accepted first intent in both per-case join proofs. Population median renderer
calls were 98–99 parrots and 100 kobolds (calls, not unique-visibility proof).
Server p99 early/late was 163/52 ms for parrots and 136/15 ms for kobolds;
client p99 early/late was 40/26 ms and 36/15 ms respectively. These short stationary
captures do not reproduce sustained late-phase client degradation.

The parrot early window recorded 119 `NO_PATH` attempts, median 26.17 ms,
20,612 node expansions, 308,692 surface queries and 1,507,159 no-collision probes.
Successful routes were about 0.3 ms median. Failed-route search work is the
measured optimization target; elapsed event duration is not exclusive CPU time.
Source inspection identifies repeated same-cell support enumeration between
diagonal connectors and neighbor expansion. A route-local exact-input memo and
cached/uncached native equivalence/work-reduction fixtures are being implemented;
there is no accepted performance improvement yet. No cross-tick collision cache,
AI throttling or weakened collision policy is approved.

The memo is now implemented in `GroundNpcRoutePlanner.RouteAttempt`: exact cell
and `nearY` bits key immutable support lists, discarded after each attempt. Edge
collision probes remain fresh. Public support queries remain uncached. New
`surfaceCacheHits` / `surfaceComputations` JFR fields distinguish reuse from
requests; older recordings explicitly lack this evidence, not zero-cost work.
Native tag `animals_route_reuse` has four compiled fixtures: direct parity,
wall-detour parity plus real standability/collision work reduction, closed-wall
`SEARCH_EXHAUSTED` parity, and world-change freshness across attempts. These have
not run natively. Source review passed after correcting an initially reachable
"no-path" fixture (`ses_ebb282f57ffeel7y3G7zihZQ1j`).

Fixture selection is bounded to at most 18 preflighted camera-facing candidates
(13/17 blocks ahead, lateral 0/−4/+4, floor rise 0–2). It preserves station blocks,
never teleports the committed body, and aborts precisely if no safe candidate
exists. Fixture/cache source review passed (`ses_ebb2a60f2ffexmnNHG7GGsExI6`).
Compilation of main, test and GameTest sources passed; current XML confirms
**5/5** focused JVM tests (four settling/geometry, one route-measurement), no
skips/failures/errors. Analyzer tests passed **16/16**, with explicit legacy and
partial reuse-field coverage. Native equivalence and affected population
remeasurement remain necessary; `diagnostic-plan-r34.json` requests those checks,
not readiness, visual approval or owner acceptance.

## R32 diagnostic findings — not readiness

Receipt SHA-256: `1b2e2799390fa91c20eff9bb997fd15417edcc6d5156884ed06af5f4a6d4dd80`.
Parrot and kobold captures passed; mixed failed before spawning during fixture
preflight. The retained mixed server log reports unsafe floor/headroom or
obstruction; the exact block was not logged. The surfaced missing-recording
identity error was secondary, not evidence of a gameplay crash.

Valid captures are under `build/reports/nonhostile-animals/population-profiles/`:
`parrots-c0afcc2c17084b09bf932e74df1ad9cc` and
`kobolds-1188664c448c40b1bcb766c76f2ce190`. Original summaries remain unchanged;
`summary-phase-attributed.json` separates primary-thread samples and GC by JVM
and phase. GC cycle duration is not stop-the-world pause duration.

Parrot server early-population p99 was 208 ms (max 354 ms), late p99 64 ms;
timestamp-filtered stacks point to `GroundNpcRoutePlanner` support/edge collision
work, not an established sole cause. Kobold client population p99 was 16.2 ms
early / 13.5 ms late; its large baseline outlier predates the animals. Median
renderer calls were only 16 for kobolds and roughly 22 for parrots, not 100 unique
visible animals. Short stationary windows did not reproduce the owner’s prolonged
client stalls. A proposed route cache was removed without performance claims.

Corrections add opt-in route-work measurements, consecutive settling admission
and precise fixture preflight failures. The fixture is moved
ahead of the body to improve rendering coverage; changed camera-relative geometry
means future results are not a controlled before/after optimization comparison.
The launcher now preserves primary errors and retains per-case join proof and
server/client logs before deleting successful disposable fixtures. R32’s shared
join-proof path was overwritten between cases; deleted per-case logs cannot be
reconstructed, and raw JFR does not replace missing join evidence.

The planner is instrumented, not optimized: `ms14.NpcRouteWork` records actual
surface/standability/edge/probe counts and expansions for each route outcome.
Public helper queries outside routing remain unmeasured. No route cache, budget,
path ordering or collision-policy change remains. Source review passed
(`ses_ebb385feeffepN4u3CfEU0i0V4`). Fixture/launcher review passed after correcting
the consecutive-settling counter and its deadline test
(`ses_ebb3b1648ffeTearcDIViC2s3m`).

Current focused compilation and JVM checks passed: `FixtureSettlingTest` **3/3**,
`NpcRouteWorkDiagnosticTest` **1/1**, zero skipped/failures/errors, confirmed in
both XML files under `build/test-results/test/`. An earlier worker used a wrong
fixture package filter; that command did not execute the fixture tests and is
not evidence for them. The corrected run used both actual fully qualified names.
Python analyzer tests passed **14/14**; PowerShell protocol tests and AST/route-event
selector checks passed. Analyzer review passed
(`ses_ebb39468effeIQa92j1d8ylf4j`). GC and route durations are explicitly attributed
whole to their event-start phase, not clipped to phase bounds. Parent verified
actual R32 JFR JSON shapes, including `sampledThread` and offset timestamps.

`diagnostic-plan-r33.json` requests fresh serial population captures with retained
per-case join proof and measured rendering/route coverage. These cheap checks do
not establish native fixture success, population-stall improvement, visual quality,
manual readiness or owner acceptance.

## Post-R30 owner feedback — active corrections, not accepted

See `OWNER-FEEDBACK.md` for the acceptance blockers and current measurement scope.
The owner reported unanimated/ground-skimming bats, oscillating/orbiting steering,
lingering abrupt walk clips, missing bat `/ghostroles` access and population stalls.
Kobold one-hand and equipment restrictions were positively received and retained.

Corrections reuse `NpcBehaviorSystem`, `GroundedHarnessWorldStep`, `FlightSystem`,
`CharacterIdentitySystem.projectForActor`, native `AnimalModelAdapters`,
`OwnedHarnessWalkAnimation` cadence, and existing development-role admission.
Client flight prediction has explicit local transition APIs; authoritative writes
remain server-owned. Native rigs/assets are unchanged. The NPC cruise height is a
documented Minecraft adaptation, with codec/schema and catalog-wire regressions.

Independent source reviews passed the NPC controller, animation/prediction fixes,
and bounded server-derived development role offers. These are not native gameplay
or visual passes. All Java source sets compiled and 92 focused JVM tests passed
across 12 selected classes, with zero failures, errors or skips. The inherited
Flight wire fixture uses a unique host and verifies default omission as well as
custom-height production publication without weakening host uniqueness.

R31 is diagnostic-only: three isolated real-client population captures reuse the
existing authenticated join/owned-process lifecycle. Python and PowerShell tests
exercise actual event/phase/completion contracts. Server tick duration, client
frame intervals, actual renderer calls, GC and sampled thread stacks are evidence,
not a performance pass. No population lag cause or improvement is established yet.
The analyzer passed nine Python tests, and the actual completion protocol and
both-JVM configuration checks passed the PowerShell protocol test. Independent
diagnostic-path review passed (`ses_ebb5e4813ffez3NfJ4JNn1PmgK`). These cheap checks
do not establish a working native capture; R31 requests the first such evidence.

R31 completed with a capture failure, not readiness: receipt SHA-256
`f367884b19ffe9c2e19ef05211527ba15d751fbc4697f923ce9089764f3335ea`.
Only the parrot check ran. Retained server logs show baseline → 100 parrots →
complete, but server JFR export failed and the profile output directory is empty.
Kobold/mixed checks were not run. The failed fixture is retained at
`build/connected-join-fixture/9efc0f7e667244bda44075adcc4c8620`; the exact command
log is `nonhostile-animals-r31-logs/1.log` beside the bound receipt. Capture-error
diagnosis/recovery is required; these phase logs alone establish neither tick/frame
distributions nor a population-performance cause or pass.

A focused disposable plain-JVM test subsequently observed
`AttachNotSupportedException: Access is denied` for external `jcmd` attachment,
before either filename form could be validated. R31 discarded the original jcmd
output, so that run's exact underlying error remains unknown; path quoting alone
is not an established fix. The replacement captures recordings inside their own
JVM using `AnimalPopulationRecording`, with no external attach or permission
changes. Its standalone positive test produced a readable JFR (including its
fixture event) at a path containing spaces; missing named recording is rejected.
Server/client self-capture wiring compiles. Ready markers publish atomically only
after successful dumps; the collector validates role, scenario, owned PID,
filename, byte count and counts before offline export/analysis. The PowerShell
protocol test passed, and no external attach remains in the launcher. Default
join behavior and its eight-second hold are unchanged. Java capture review passed
(`ses_ebb4b59a6ffey2kuWQBUh7dno9`); a fresh native diagnostic is still required.

Independent read-only source reviews were completed after the R1 gate rejection.
These are code-level judgments, not substitutes for serialized validation or owner
acceptance. R2 confirmed build/jar and all 3,697 JVM tests pass after the R1
repairs. R2 still failed readiness verification: 155 checks passed, but UI's
expected class set was incomplete, so the full native suite remained unrun.
Fresh complete readiness and native evidence are still required.

R3 ran 3,700 JVM tests and exposed one cross-suite fixture leak: a locally
constructed PA workspace stayed registered after its policy test. Its owner test
now closes that local instance in `finally` with `closeLocal(false)`, pairing
registration/unregistration without sending a network packet. Registry ordering
assertions are unchanged. The plan also removes only the generated candidate jar
in a separate preparation check, so the following typed build can prove new output
even when test-only changes correctly yield identical reproducible jar bytes.
The cleanup and preparation-check delta passed independent source review by
`ses_ec03abee9ffejhHYGhB0l7i2QR`. Focused revalidation ran the PA policy and registry
classes in one executor: 86/86 selected tests passed, including all eight PA policy
and three registry tests. Full readiness/native execution remains pending.

R4 ran no protected checks: its generated review brief was 12,331 UTF-8 bytes,
exceeding the shared 12,288-byte cap. The plan now uses concise equivalent criteria
and coverage text. Offline reconstruction measured 9,740 bytes even retaining
R4's longer steps/boundaries; all five command arrays, check kinds, selectors,
artifact paths and native log path are identical. Scope and evidence are retained;
shared tooling and its limit are unchanged.

## Animal runtime — PASS

Reviewer session: `ses_ec051c81effecLCXC4IG2qAaKP`.

Inspected animal enrollment and resolved composition, NPC autonomy/retaliation,
shared flight movement and egg/udder/cleaning transactions. No code-level issue
was found. Relevant owners include `AnimalMob`, `NpcBehaviorSystem`,
`NpcRetaliationSystem`, `FlightSystem`, `CharacterMovementMotor`, `UdderSystem`,
`BodyLiquidInteraction`, egg-layer output and `AbsorbentCleaningService`.
Coverage assessment included prototype policy tests and production-path native
fixtures for flight, retaliation, milking, egg rollback and cleaning.

## Control and presentation integration — PASS

Reviewer session: `ses_ec05186adffeZTQomONCkBS80S`.

Inspected control/takeover, hand-client, custody/footprint and speech integration
diffs and their boundaries. No code-level issue was found. Relevant owners include
the lifecycle development-offer path, `BodyHandClientPolicy` and one-hand client
consumers, portable descriptor/custody paths, `ItemFootprints` and shared speech
admission/transform/audio consumers.

Earlier focused reviews also traced selected native model/hand-anchor rendering,
profile-aware storage geometry, thermal zero-threshold handling and all-host
speech inheritance; no unresolved source findings remain from those reviews.

## R1 test repairs — PASS

Reviewer session: `ses_ec04caaadffetB0ES7Xazbalws`.

Inspected physical resource/document root lookup, current active-hand routing
assertion, complete suicide fixture ancestry, the explicit animal-client package
classification, and shared accepted-asset hash checks. No unresolved source issue
was found. No checks were run by this reviewer.

| R1 failure | Correction |
| --- | --- |
| `EmoteResourcesTest` | Configured project root and current `docs/audio/third-party-emote-audio.md` location; all 34 audio hashes remain checked. |
| `RemoteSignallerHandUseRoutingTest` | Assert current shared `view.active()` selection, retaining the active-hand route requirement. |
| `SuicideCommandTest` | Include `supported_animal_core` in the fixture's resolved ancestry. |
| `ServerClassloadingTest` | Classify the native `mob/animals/client/` package as client-only; common-source checks remain intact. |
| Collector/canister provenance tests | Share the audited newline-equivalent text hash contract below; unchanged pins and binary checks. |

## Evidence limits

R2's missing UI suite was the empty `PointerOverlayRegistryTest` source. Its three
restored direct tests exercise the real registry and production dispatch/admission
helpers: registration/order, focus/capture, host/paint policy and reset. A resource
lock and per-test cleanup isolate shared registry state. Independent source review
by `ses_ec03abee9ffejhHYGhB0l7i2QR` passed. The readiness catalog and completeness
guard were not weakened; fresh readiness execution must still verify the suite.

The R1 asset-provenance failures were traced to LF checkout bytes versus accepted
CRLF text hashes, not changed art. The complete audit covered 40 canister entries
(6 raw matches, 34 newline-equivalent) and 36 collector references (18 raw,
18 newline-equivalent), with no residual content mismatch. The shared test guard
checks the original raw digest first, then known text formats (`.json`, `.mcmeta`,
`.obj`, `.mtl`) as newline-equivalent CRLF bytes against the same pin, including
derived JSON models. Only LF/CRLF representation is tolerated (including mixtures);
no other whitespace, JSON structure, terminal-newline or binary normalization is
added. Accepted manifests, provenance pins and asset contents remain unchanged.

Both reviewers relied on the reported pre-gate 218/218 focused JVM result and
inspected source; neither ran checks. Native fixtures were compiled but not run.
Connected control/network interoperability, actual event input and native visual
alignment remain unverified. The integration lead must independently judge the
fresh candidate and execute the complete plan; these source reviews do not turn
the failed R1 gate into a pass.

## R5 native startup correction

R5 produced a fresh jar, passed 3,700 JVM tests and 158 readiness tests, but native
startup failed before test execution on an unresolved bee bloodstream reference.
Animal data now uses existing registered `insectblood` and `copperblood` IDs rather
than underscore variants. No reagent behavior or permissive fallback was added.
The new shipped-catalog regression invokes `ModCharacters.validateReagentReferences`
and checks resolved animal bloodstream/metabolism dependencies against actual
reagent resources. Fresh validation remains required; selecting 1,056 native tests
is not evidence that any executed or passed.
Independent scoped source review (`ses_ec018abd8ffe1Pubo4jRKbBBVI`) passed the
canonical-ID repair and production-validator reuse. Main/test/GameTest compilation
passed; a fresh targeted JVM run passed 60/60 tests with no errors or skips across
`AnimalPrototypeTest`, `CharacterPrototypeTest`, `ReagentResourceSmokeTest`,
`ReagentComponentCatalogTest`, and `ReagentSchemaAuditTest`. No native fixture was
executed by the author; the complete gate plan remains required.

## R6 diagnostic preparation (not a completed repair)

R6 again passed the fresh build, 3,700 JVM tests and 158 readiness tests. Native
execution crashed on the cleaning fixture's uncaught `AssertionError`; its helper
now uses `GameTestAssertException`. Cleaning assertions retain exact chemistry
expectations and report starting chemistry, game time and persisted deadlines.
Equipment/pickup fixtures now assert shared admission/fit/geometry preconditions
before their transactions and report resolved inputs. Their root causes remain
unproven; diagnostic changes are not successful native evidence.

The saved native log starts with `DEFERRED profiles=1` before account fixtures.
Future native checks use a unique fresh game directory under the dedicated build
fixture root, retaining failures and preserving owner configuration. Independent
setup review (`ses_ebecf8956ffeO22UemqizQS0gA`) passed this replacement; the rejected
profiles-only reset is no longer used. Main/test/GameTest compilation passed and
13 focused policy/prototype tests passed without failures or skips. A protected
native diagnostic is still needed before these repairs can be declared complete.

## R7 diagnostic results and corrections in progress

`nonhostile-animals-r7-diagnostic` completed 17 animal-tagged native tests: nine
passed and eight failed. This was diagnostic-only, not readiness approval. Its
receipt is under `build/agent-workflow/nonhostile-animals-sprint/reports/`; the
bound log is `build/reports/nonhostile-animals/diagnostic-native.log`. Startup was
`UNINITIALIZED profiles=0`, avoiding the prior persisted-profile admission denial.

The pickup failure occurred after successful pickup/custody/appearance checks:
the fixture unboxed a nullable escape expectation. It now uses null-safe equality.
The cycle fixture observed correct chemistry but compared its next deadline to
callback time rather than the prior absolute deadline; the exact interval check
is corrected. Autonomous cleaning now checks the successful-use deadline and
reduced source contamination, rather than expecting absorbed blood to remain
after later purge cycles. These corrections still require native revalidation.
Flight death setup, egg laying, udder production/timed access and fixture lifecycle
admission remain under investigation; no fresh readiness candidate is claimed.

The external vanilla-damage observer now bounds only representability overflow
against the existing finite ledger, preserving ordinary overdamage and pending
typed-transaction mitigation. Regression tests cover extreme finite input,
existing mixed damage, ordinary overkill and rejection of NaN. Udder production
uses the resolved hunger boundary (live fixture coverage at 49/50); the pinned
source mapping is recorded in `PROTOTYPES.md`. Independent source review
`ses_ebea8a06fffe198Z0uCnLEAqyu` passed these changes. Stationary cow and nutrition
setup stabilize fixtures but do not prove milking admission is fixed. The next
animal-filtered request remains diagnostic-only, with richer lifecycle context
for the unresolved missing CHARACTER Begin.
Main/test/GameTest compilation passed after these corrections. Fresh focused JVM
validation passed 38/38 tests with no errors or skips (damage, animal prototypes,
cleaning composition, equipment fitting, portability and udder tests). This does
not validate native timing or lifecycle admission.

R8 diagnostic completed 17 tests: 12 passed, five failed. Flight, eggs, pickup and
basic udder tests no longer failed. Remaining failures were cleaning cycle/NPC
observation, cleaning command readiness, manual summon parsing and timed milking.
Direct source inspection caught the incomplete prior deadline edit; checks now
use `initialDeadline + cycleTicks` and `initialDeadline + 2 * cycleTicks`.
NPC task observation is bounded across ticks, not a single transient snapshot.
Cleaning command readiness waits two natural ticks for production's server-post-
tick Begin; it does not bypass admission. Missing-Begin diagnostics are bounded
to avoid Minecraft's 1,024-character failure book-page limit. Further output
reports command parse exceptions and milk source/recipient/combined volumes.
Those latter causes remain unresolved; R9 is diagnostic-only. Independent scoped
source review `ses_ebe9cda26ffebz5YEchzcB4vMq` passed the fixture corrections.
R9 preparation compiled all three source sets and reran the focused JVM selection:
38/38 passed, with no errors or skips. Native confirmation remains pending.

R9 diagnostic completed 17 tests: 15 passed, two failed. Lifecycle command,
autonomous cleaning and timed milking passed. The remaining failures were
fixture-only: absolute command coordinates used exponent notation unsupported
by Brigadier, and the second-cycle assertion read the immutable first-cycle
attachment snapshot. Command coordinates now use plain decimal formatting; the
second callback rereads current attachment state for deadline/cooldown checks.
All chemistry and exact cycle-interval assertions remain. These corrections
require fresh validation; no diagnostic result constitutes readiness approval.
Final scoped review (`ses_ebe990416ffeKTfutB2MFGfBjG`) passed both corrections.
Main/test/GameTest compilation and a fresh 38-test focused JVM run passed with no
failures, errors or skips. The next submission is a separate complete readiness
revision requiring all five planned checks, including full unfiltered GameTests;
earlier diagnostic receipts are not promoted or reused as readiness approval.

R10 built a fresh jar and passed 3,701 JVM tests and 158 readiness tests. Full
unfiltered native execution completed 1,056 tests with 11 failures. Several animal
fixtures exposed real vanilla distance despawning: unlike `MouseMob`, `AnimalMob`
lacked `requiresCustomPersistence()`. It now reuses that station-body lifetime
contract. The added animal regression uses unforced entities, a real far player
and `checkDespawn()`; it does not mask the bug with `setPersistenceRequired()`.
The full-suite milking failure observed a target outside the unchanged 2.5-block
reach; physical fixture confinement is being corrected, not production access.

Two blocking non-animal fixtures were examined narrowly. The airlock test waited
for OPEN but immediately asserted OPENING_PASSABLE; its wait now matches its
intended first-passable opening phase. PA outage timing remains unresolved;
bounded schedule/power history was added without changing its assertion or the
power runtime. Review `ses_ebe88720dffegRw7gnI3gccY4Y` passed the lifetime regression,
airlock correction and PA diagnostics. The next request remains diagnostic-only.
The milking fixture now uses a template-relative one-wide/two-long corridor and
bounded per-tick liveness/range assertions; review
`ses_ebe8834b1ffe6ekfcA4T2r6BCv` passed the geometry and retained cancellation/LOS/
conservation checks. Native confinement is not established by source review.
R11 diagnostic preparation compiled main/test/GameTest sources and passed the
fresh focused JVM selection, 38/38 with no failures, errors or skips.

R11 diagnostic completed 24 affected native fixtures: 23 passed, one failed.
The unforced animal despawn regression, PA firing fixtures and airlock crush-cycle
fixture passed in that selection; full-suite behavior still needs fresh evidence.
Direct inspection identified the remaining failure in the milking fixture monitor:
it called `BodyInteractionAccess.inRange` (which also checks LOS) during deliberate
wall occlusion. The monitor must use shared `rootWithinReach`; production requests
and completion retain the full reach/LOS checks and cancellation assertions.
That monitor correction passed independent source review
`ses_ebe82d55effe98jnn7vYhHgvAA`. Main/test/GameTest compilation and the fresh
38-test focused JVM selection passed with no failures, errors or skips. The next
readiness revision requires the entire five-check plan on its new snapshot;
R11's focused results are not promoted to full-suite or owner approval.

R12 ran the complete plan: fresh jar, 3,701 JVM tests and 158 readiness tests
passed. Full native execution completed 1,057 tests, with four failures (1,053
passed). Remaining cases are the operator bat possession target, PA outage charge
timing, an indexed MV receiver observation, and dead-body airlock crush capture.
These are under investigation; no owner readiness or completion is claimed.
The possession fixture now nominates its exact UUID and checks the offered entity
before commit, avoiding unrelated persistent bats in the shared test level.
The corpse airlock fixture's capture/sealed delays now begin inside the successful
paid-close callback, rather than racing an asynchronous power wait. Neither
change alters production selection or airlock rules. PA wave diagnostics now
observe transitions throughout the restored-wave wait; the indexed MV fixture
reports callback timing and receiver identity. Power causes remain unresolved, so
R13 will be diagnostic-only with focused existing selectors.
Scoped review confirmed the callback nesting and UUID selection. Its remaining
message-length concern was resolved by compacting the corpse-capture diagnostic;
parent source inspection verified it retains phase, collision/state flags,
victim/doorway bounds and timing without the full state record. No gameplay
assertion or production rule was relaxed.
R13 preparation compiled all source sets and passed 38/38 focused JVM tests with
no failures, errors or skips. A final GameTest compile confirmed current fixture
sources after the mechanical callback-closure repair. Native diagnosis remains
lead-owned and pending; neither power case is claimed fixed.

R13 diagnostic completed 15 tests: 13 passed, two failed. PA, indexed MV and
stationary airlock cases passed in this focused selection. Both failures were
the possession fixture's unsupported `uuid=` selector option. It now passes the
UUID literal directly to production's existing `EntityArgument.entity()` target;
offered entity-ID and original-UUID assertions remain. Focused power passes do not
explain or certify the earlier full-suite transients; that distinction is retained.
Source investigation established the relevant power fixture contract: cable edits
increment per-level topology generation and temporarily invalidate meters even
on otherwise unrelated networks. This intentional fail-closed behavior is
documented by the power handoff. A first restored eligible observation cannot
prove uninterrupted eligibility throughout the next charge interval. Fixture
corrections are being bounded to current identity-bound meter readiness and the
latest authoritative uninterrupted PA epoch; production power policy is unchanged.
The PA observer now retains the pre-restore particle baseline, validates captured
join epochs/ticks before inspecting subsequent eligibility, and bounds its wait
by the existing 1,000-tick test budget (20 ticks reserved for cleanup). No timeout
was increased. All restored particles must still follow their actual authoritative
epoch by exactly one resolved interval and the three-particle wave must share
one join tick/epoch. Indexed receiver readiness now requires the current receiver,
current known meter/delivery and matching callback readiness together. A direct
`EntityArgument` UUID-literal parser regression was added; fresh execution is pending.
Final source review `ses_ebe685ac2ffe5bCXe0EkpRLmnU` passed the corrected power
fixture semantics. Main/test/GameTest compilation and 39/39 fresh focused JVM
tests passed without errors or skips, including the actual Minecraft UUID parser
test (one executed). This is not native interoperability or full-suite evidence;
the next readiness revision requires all five checks on its fresh snapshot.

R14 completed the full plan: fresh jar, 3,702 JVM tests and 158 readiness tests
passed; full native execution completed 1,057 tests with four failures (1,053
passed). PA outage no longer failed. Remaining cases were bat role-adoption
commit, indexed MV receiver observation, natural reactor startup and a second
stationary critical airlock shock. The UUID parser succeeds; the bat failure is
after a correctly targeted offer, so selection syntax is no longer its cause.
These paths require further diagnosis before another readiness submission.

R15 preparation fixes the shared adoption preflight's unconditional GhostRole
requirement for trusted operator-development offers. Only server-stored development
origin with current operator permission can bypass that prototype check; ordinary
role admission and all identity/lease/lifecycle checks remain. Bat gains no fake
GhostRole component. Independent source review `ses_ebe58204cffezrVSnRtiqk3JOp`
passed this correction. Existing reconnect policy is unchanged.

Indexed MV fixture waits now reuse `PowerFixtureReadiness` so readiness and actual
assertions run in the same poll, not a tick apart; ten stable samples, current
identity, actual delivery/callback assertions and original deadlines remain.
Reactor startup adds bounded export topology/footprint/solve diagnostics without
changing generation rules. Source review `ses_ebe562410ffewBxOBfxTEvwKy1` passed.
The stationary-contact fixture now anchors setup and capture checks to observed
door phases. Its power sample is explicitly post-phase, not proof of power at the
solidification edge. Reactor export and second-shock causes remain unverified;
the next native run is diagnostic-only, with no owner/manual readiness.
Main/test/GameTest compilation passed. Fresh focused execution of
`LifecycleDevelopmentOfferAdmissionTest` passed 3/3 tests, with no failures,
errors or skips. Airlock fixture source review `ses_ebe55d9edffeix77ZkAnBaK3HA`
passed; native behavior remains pending the lead-owned diagnostic.

R15 diagnostic completed 11/11 selected native tests (1,057 total registered),
all required tests passed with zero failures. The bound receipt is
`nonhostile-animals-r15-diagnostic-gate-validation.json`; its log is
`build/reports/nonhostile-animals/diagnostic-native-r15.log`. This exercises bat
adoption, reactor generation, stationary airlock contact and indexed MV fixtures,
but does not reproduce or explain the earlier reactor export failure, prove
solidification-edge power, or establish full-suite stability. No diagnostic result
is promoted to readiness. R16 requires the unchanged complete five-check gate on
a fresh snapshot, including full native completion and exact counts.

R16 was rejected before execution (`checks: []`, `status: NOT_RUN`). Its cited
"fresh" native failure is actually the retained R14 log: current SHA-256
`52fb3ca7021cb9df24c5f125c4e9b99bfc57c8c04d2dbfcdf89c953ffaceeb03`
matches R14's bound validation receipt. The log predates R16; independent
provenance audit `ses_ebe51d228ffeWrVNK90BShMmYB` confirmed this. Thus R16 has
neither a fresh native failure nor a pass. R15's separate diagnostic log remains
only focused evidence. R17 requests the same complete five-check gate; retained
R14 failure evidence must not be relabeled as R17 execution. Full-suite stability
is unknown until that fresh run completes. No gameplay or assertion changes were
made in response to the provenance error.

R17 executed the complete plan: fresh jar, 3,703 JVM tests and 158 readiness
tests passed. Full native execution completed 1,057 tests: 1,056 passed, one
required failure, `bootstrapsanddeliversfixturepower`. The loaded fixture reached
its unchanged 600-tick deadline with control delivery known at 250 W and power
delivery known at 0 W. All four R14 failure paths passed this full run, but the
sprint is not technically ready. Bootstrap fixture/setup/runtime causality is
under investigation; no timeout increase or production power-policy change is
justified by the current evidence.
Source tracing confirms the PA's resolved standby demand is 500 W even while
disabled; the control demand is 250 W. Known zero delivery therefore is not an
idle-demand explanation. Planned cable/port adjacency is checked by the fixture,
but does not prove the live PA and supply share a component at timeout. R18 adds
failure-only live identity, demand, port, topology/footprint and allocation context
using existing shared meters and graph diagnostics. It does not add a supply
meter, infer wattage from device object representations, change the 600-tick
deadline, or alter allocation policy. The next native check is diagnostic-only.
Main/test/GameTest compilation and `git diff --check` passed. Independent source
review `ses_ebe456d1bffes4T7ZAITTHElHZ` passed the query-only diagnostics, retained
assertions/deadline and separation of source offers from measured allocations.
Full failure context is logged with an 8,000-character cap; the exception summary
is capped at 650 characters. No native execution is claimed by these checks.

R18 diagnostic completed its single selected bootstrap test (1/1 passed; 1,057
registered). The log `diagnostic-native-r18.log` has the all-required-passed marker;
no failure-only diagnostic fired. The R17 zero-delivery cause therefore remains
unexplained, not repaired or disproven. R19 submits the instrumented snapshot to
the unchanged full five-check gate so any recurrence carries current live context.
R18 is not full-suite evidence or owner readiness.

R19 completed the full plan: fresh build, 3,703 JVM tests and 158 readiness tests
passed. Native completion reports 1,057 tests, two required failures (1,055
passed): `queryissortedboundedanddoesnotpair` and
`leasedpigandvillagerkeepvanillawalkanimation`. Bootstrap did not fail this run;
its earlier zero-delivery cause remains unexplained. The query result includes
the expected two devices plus a vent from outside the fixture, still within its
16-block query neighborhood; sorting/read-only checks are not by themselves
evidence of a production query defect. The walk fixture reports nonzero idle
animation without speed/context. These two cases are under investigation; no
assertion or runtime behavior has yet been changed in response.

The query fixture now accepts legitimate shared-world neighbors instead of
assuming its two placed devices are the complete neighborhood. It still verifies
every returned candidate's spherical radius, unique identity/position, full-list
ordering and cap, own-device presence/order, outside exclusion, repeat equality
and unchanged links. Review initially rejected missing per-result radius/uniqueness
checks; after correction `ses_ebe3b2d6cffe05PW9JlOTX8sfL` passed the complete
contract. Production query behavior is unchanged.

Direct source inspection located the animation failure at the initial idle check
after acquisition, not the later decay assertion. The fixture allows natural NPC
ticks before leasing and provides no actual speed in its old failure message.
Initial-state diagnostics were added; residual pre-lease animation is plausible,
not established. Neither initial/final thresholds nor movement behavior changed.
The diagnostic now captures actual pre-lease speed/position/tick baselines and
current speed, displacement/velocity, grounding, ownership, no-AI and eligibility.
Narrow method tags `air-alarm-candidate-query` and `leased-walk-animation` select
only the affected checks. Final main/test/GameTest compilation and diff checks
passed after these edits; native R20 diagnosis remains lead-owned and pending.
Independent re-review `ses_ebe3a4192ffe9XLo9TgzNIDvY3` passed the baseline/eligibility
coverage and bounded initial diagnostic; it establishes no native root cause.

R20 diagnostic completed exactly two selected tests (1,057 registered); both
required tests passed, with completion markers in `diagnostic-native-r20.log`.
Initial idle animation did not fail, so its earlier cause remains unverified.
The query fixture correction and baseline diagnostics are retained. R21 requests
the unchanged full five-check gate on the new snapshot; R20 is not promoted to
full-suite stability, technical readiness or owner acceptance.

R21 passed fresh build, 3,703 JVM tests and 158 readiness tests. Actual full native
completion was 1,057 tests with two failures (1,055 passed), not only the bootstrap
case named in the review finding. Bootstrap diagnostics captured a live PA with
500 W demand and all eight enabled emitters, but every MV meter unknown while
control remained known at 250 W and topology reported ready. The second failure,
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`, observed 29,800 W
known APC output and 47 overload ticks without tripping before its initial
observation window expired. Both require source-level causality investigation;
neither evidence justifies weakening power/protection policy or extending native
test timeouts. No further readiness revision is submitted yet.
Bootstrap's ticket envelope covers the full layout; the broad 16-block footprint's
unrepresented empty neighboring chunks do not prove the relevant port is missing.
R22 replaces that broad context with exact port-node diagnostics from
`PowerGraphService.diagnostics`, retaining production fail-closed behavior.
APC source rules require 61 consecutive authoritative overload ticks; unknown,
stale or skipped observations reset continuity. The fixture's fixed 200-tick
initial window cannot guarantee that uninterrupted interval under shared-level
topology changes. Its native-trip wait is being bounded by the existing 1,200-tick
overall budget with the original 135-tick post-trip sequence reserved. Production
thresholds/continuity and manual reclose/cable-cut assertions remain unchanged.
Exact bootstrap node diagnostics passed independent source review
`ses_ebe2d22f6ffeUTMgqvzDp5QshH`. Main/test/GameTest compilation and diff checks
passed; compilation required only `Math.toIntExact(helper.getTick())` in the
fixture budget arithmetic. No native execution is claimed from these checks.
APC budget/counter/latch and post-trip checks passed independent source review
`ses_ebe2cb96effewf1wF72wTYn6Au`; R22 remains diagnostic-only.

R22 diagnostic completed two selected tests (1,057 registered), both required
tests passed; completion is recorded in `diagnostic-native-r22.log`. The native
APC sequence passed its revised budgeted wait and unchanged downstream checks.
Bootstrap did not reproduce, so its exact-node failure context did not fire and
its prior MV readiness cause remains unresolved. R23 requests all five unchanged
readiness checks on the instrumented snapshot; focused passes are not promoted
to full-suite stability or owner approval.

## Owner join failure after R23 — acceptance blocked

R23 passed the automated gate, but the owner's real-client join failed repeatedly.
`run/logs/latest.log` (2026-10-15 19:43–19:44) records client catalog publication
failing on character `cockroach`: `$.components[7].damage.types` required field
missing. The body then becomes tracked but client readiness reports
`CHARACTER_POLICY_UNAVAILABLE`; epochs 2, 4, 6 and 9 expire with `READY_TIMEOUT`.
No successful Ready commit occurs in that run. Inside-head camera/free movement
and disconnect are owner observations; catalog rejection and missing policy are
corroborated by the actual log. The earlier statement that `run/` had no logs was
incorrect and is not evidence of unavailable owner artifacts.

The server fixture directly invokes Ready using a socket-free peer; neither it nor
the readiness catalogs prove production client catalog assembly or join binding.
Repair must exercise the shipped catalog's actual server-to-client round-trip and
client-policy consumer, not bypass readiness or enlarge its timeout. Owner
acceptance is failed/pending; the R23 technical result is not a gameplay pass.
The cockroach source has `damage.types.piercing: 0`; canonical encoding produces
an empty specifier and omits the default empty `types` map. `DamageSpecifierData`
already decodes omitted types as empty, but the new `MeleeAttack` schema audit
required it. The correction aligns that audit with the codec; the damage object
remains required and malformed/null maps, unknown keys and invalid amounts remain
rejected. Independent source review `ses_ebd5f21c9ffec0COfnddxn4Z5O` passed this
contract correction. A focused component test passed; acceptance still requires
the shipped catalog's real sync round-trip regression and fresh gate evidence.
The new regression loads shipped character/reagent catalogs and uses production
resolution, chunking, payload wire encode/decode, assembly and client-manager
publication before checking human/cockroach movement policies and zero damage.
Independent source review `ses_ebd5c43b3ffeRiOMrGwiox1VJM` passed. These catalogs
fit one chunk in this test; separate protocol tests cover multi-chunk assembly.
This is not all registered prototype types or an authentic client join. Historical
experimental lifecycle config keys in old checklists are absent from current
source; current `LifecycleFirstJoinHandler` runs unconditionally on normal login.
The isolated connected diagnostic must not write obsolete flags or alter owner
configuration/world/profile data.
The designated cheap runner temporarily restored only the old required-`types`
audit and ran the new shipped-catalog sync regression: it failed on cockroach at
the exact owner-log path `$.components[7].damage.types`. The fix was restored
immediately. Fresh focused execution then passed 19/19 tests in
`PrototypeCatalogSyncTest` and `MeleeAttackComponentTest`, with zero failures,
errors or skips; main/test/GameTest compilation passed. Baseline failure evidence
is retained under `build/diagnostics/catalog-sync-prefixed/`. This demonstrates a
real regression rather than a synthetic successful Ready, but does not establish
connected join, camera or input success. An isolated real-client diagnostic is
being prepared for lead-owned execution before another readiness request.
The isolated real-client diagnostic reuses ModDev launch-script generation but
not the normal developer launcher (which deletes its selected `run/world`). Its
own fresh build fixture auto-connects using verified Minecraft Quick Play arguments,
requires matching client Ready and server committed epoch/body, rejects catalog
errors/disconnects during an eight-second wall-time hold, and verifies all owned
processes stopped before success. Preparation/native deadlines are separate;
normal run data is untouched. Process inspections fail closed and use exact UTC
microsecond identity matching. Final independent script review
`ses_ebd593e90ffeuXZeLCvnk5nQCR` passed after corrections. Cheap parser, identity,
argument-quoting and marker tests passed; they are not native execution evidence.
R24 is diagnostic-only; input, camera pixels and server-game-time duration are not
claimed by the script.
R24 diagnostic failed before native startup: Gradle 9 rejected eager inspection
of `runServer` dependencies from `projectsEvaluated` because the project-state
lock was not held (`connected_join.init.gradle:22`). The failed fixture is retained
under `build/connected-join-fixture/4534fe42c8754756ba9f830ca9f8c678`; its
`prepare.stderr.log` is the causal artifact. No client join or repaired gameplay
success is established by R24. The correction is limited to lazy task dependency
wiring and must pass a cheap task-graph dry run before another native request.
The corrected lazy dependency wiring passed that dry run, including classes,
resources, both run-preparation tasks and both launch scripts (all skipped, no
native execution). Independent delta review passed. R25 retries the isolated
authentic join diagnostic. Future readiness now explicitly includes this real
client check in addition to the original five checks; synthetic Ready evidence
alone cannot satisfy the join criterion.
R25 prepared successfully and started real server/client processes, but the
fresh client never attempted a connection within the bounded run. Server log
reached Done with no login; client log confirms the Quick Play address/username
and completed rendering/resource startup without any Connecting or harness Ready
record. Thus R25 is a diagnostic failure, not a repaired join or a reproduced
catalog error. Artifacts are retained under
`build/connected-join-fixture/1193e872fe6740d2a2ef310a558dc8f3` while the fresh-client
startup path is traced; increasing the deadline is not a justified workaround.
Minecraft source confirms fresh-client accessibility onboarding wraps the initial
screen continuation that starts Quick Play. The isolated fixture now writes only
its own `options.txt` with `onboardAccessibility:false`; owner options are untouched.
Parser/options-fixture checks and independent delta review passed. R26 retries
the same authentic join diagnostic; no connection success is claimed in advance.
R26 authentic join diagnostic passed. Its preserved proof
`build/reports/nonhostile-animals/connected-join-r24.log` (historical filename)
records client policy resolution, Ready sent, and server commit for epoch 2/body 2.
It also records client camera/medical ownership, eligible identity-resolved control
and the server accepting the first intent, followed by the connection hold and
verified process cleanup. The failure no longer occurs in that isolated run.
This is actual client/server evidence, not the socket-free server Ready fixture.

The checker now explicitly requires those camera/control/first-intent records for
the matching epoch/body, not just Ready/commit, and writes future proof to
`connected-join-proof.log`. Positive/negative predicate checks against R26 evidence,
parser/diff checks and independent delta review passed. No new native run is
claimed for the added assertions. New immutable `gate-plan-r27.json` retains full
sprint coverage and adds this authentic join check to the original five checks.
Physical keyboard/mouse movement, camera pixels, prediction feel and second-player
visibility remain owner checks; no sprint completion or acceptance is claimed.
R27 was operationally blocked by the scoped review's malformed identity/decision.
The same reviewer corrected its output; source review found the schema/sync change
consistent and requested fresh validation. The lead then rejected R27 with
`checks: []`, `status: NOT_RUN`, solely for lacking a gate receipt. This is not a
fresh code/test failure and must not be reported as executed validation. Authors
may run focused cheap checks, but the integration lead owns the full build/test,
readiness and native/client checks. R28 therefore requests execution of the same
six-check plan; it does not assert readiness before those checks or ask for
historical R26 evidence to substitute for a current gate. Gameplay source is
unchanged since R27. The workflow stage-order refusal must not be worked around
by fabricating a receipt, invoking protected checks as the author, or weakening
the plan.

R28 did execute the six-check plan: fresh jar, 3,706 JVM tests, 158 readiness
checks and the authentic client join/camera/control check passed. Full native
completion was 1,057 tests with one failure (1,056 passed), bootstrap power.
Its exact PA/emitter MV nodes were known, indexed and shared component 4, but all
received 0 W. The control's 250 W does not establish external source delivery.
The lead's missing post-receipt verdict is an operational issue, not missing
execution evidence; the bound receipt is `nonhostile-animals-r28-gate-validation.json`.
Archived logs are `r28-full-gametest.log` and `r28-connected-join-proof.log`.

R29 diagnostic instrumentation extends the existing `PowerRuntime` last-solve
diagnostic with actual source offers, substation HV/MV components and solved HV
input, APC input/total output, and solve tick/generation. It does not change
allocation policy. Ordinary power-device port diagnostics and a once-only healthy
bootstrap snapshot address gaps in previous observations. Limits and omitted or
truncated data are explicit. The new diagnostic plan preserves the original
unfiltered shared-level context because focused bootstrap checks did not reproduce
the full-run failure; it is not a readiness rerun or owner approval.

R29 author checks: all Java source sets compiled; `PowerLiveLoopTest` passed
30/30 with no failures, errors or skips. Independent review passed after correcting
success-log severity and binding diagnostic topology generation immediately before
the actual solve (not after visual projection). Subsequent compile checks passed.

R29 diagnostic completed: full native selection 1,057/1,057 passed. Receipt SHA-256
`f63d9f57414df93bd3259f019c8f4fa80c425f7f91557c2975800cc26e309a51`
matches the delivered receipt. The healthy fixture source offered 25,000 W on HV
component 11; its substation received 25,000 W and bridged to MV component 10.
The APC received 5,250 W; the PA received 500 W and all eight emitters 600 W each.
This does not explain or fix the intermittent R28 zero-allocation failure.

Post-run interpretation caught a diagnostic-only label error: `apcOutputWatts()`
is total APC output, not battery-exclusive contribution. R30 renames the displayed
field from `batteryOutput` to `actualOutput`; the R29 value must not be interpreted
as evidence of battery discharge. No power policy is changed. Fresh R30 readiness
uses the original coverage, a rerun-task jar build, and a separate native fixture;
runner lifecycle controls are not embedded in the new author plan. R29 remains
diagnostic evidence only; physical input, camera pixels and owner acceptance remain
outstanding.

R30 label-only correction compiled and passed `git diff --check`. Independent
plan review passed (`ses_ebc786ecaffey5Ro7MgbYV3O2W`): acceptance criteria, coverage
and manual steps are unchanged from R27. Five checks retain build, full JVM,
UI/interaction/prediction readiness, authentic isolated join and full native
execution. The redundant jar-deletion preparation command is replaced by the
existing rerun-task build, not by reduced coverage. The R30 fixture directory did
not exist at author submission preparation.
