# Animal feature implementation and evidence

## Latest owner-ready revision validation

Feature run `run_aae9fadfb67612976ede7554` passed coded checks, including
38/38 native fixtures, readiness and packaging, but ended FAIL on review of
sustained-population and connected-takeover evidence. Cleanup is confirmed.
Human-operated Mixed 40 and Parrots 100 captures subsequently recorded all five
sustained phases. Owner found Mixed mostly smooth with a brief jumping stutter,
but reported view-dependent rubberbanding/slowing with Parrots. Capture PASS is
coverage, not performance acceptance. Kobolds and owner-plus-observer takeover
remain deferred until the owner returns; do not launch another human capture.

The input-cadence investigation established dropped same-tick inputs; the interim
FIFO then exposed backlog latency. It is now replaced by tick-associated state
updates and bounded logical-tick prediction (see `INPUT-TIMING.md`). The new
candidate has earlier focused JVM XML showing 411 tests across 55 suites without
failures/errors, plus successful compilation and independent client/server source
review. Diagnostic `run_28445f8a9c193569cb5a5bb0` passed the selected headless native,
explicit-class JUnit, and UI/interaction/prediction readiness checks; acknowledged,
cleanup CONFIRMED. This diagnostic does not replace the settled feature FAIL;
incident causality and connected behavior remain unverified. The earlier
`run_656a313543a7f68b0fc55ec5` passed 39/1,070 filtered native fixtures but covered
the interim FIFO, not this candidate. Existing human captures also predate the
new model and are not proof that Parrots rubberbanding is resolved. No further
human capture is authorized now.
See `OWNER-FEEDBACK.md` for the human observations and capture paths.

Earlier feature run `run_aace0e4d29035d7ed3b793bc` ended FAIL (acknowledged; cleanup
CONFIRMED). Unit and readiness checks passed. The filtered native selection ran
38/1,069 fixtures: 34 passed and four failed at shared server startup with
`GLOBAL_STARTUP_RECOVERY_REQUIRED` because the managed GameTest directory reused
a persistent `world/moonstation14-lifecycle` profile containing an ACTIVE session.
This is an isolated fixture-state failure, not evidence of a production recovery
guard defect. The guard remains unchanged. The preserved fixture and small
provenance record are under `build/agent-workflow/nonhostile-animals-newflow/failed-fixtures/`.
Each further feature revision must use a fresh managed directory and rerun its native
pack; prior native diagnostic PASS receipts are historical, not proof for that run.

The earlier diagnostic `run_8fb86e98fac3dc773be9ce4e` passed 38 selected fixtures,
but does not override this feature-run result or establish owner acceptance.

Historical retries: `run_8bc1cd4f7d7d724c05bc9a44` passed 33/38 and
`run_432123cdf15e55693b0adfbb` passed 37/38. Fixture corrections addressed role
composition assumptions, catalog isolation/ordering, invalid synthetic entries,
and flight-height setup. An earlier request-format ERROR ran no checks. The
later passing diagnostic superseded these failures for its selected scope only.

Owner feedback after feature gate `run_386c0ea62b5d916c24781d9b` reopens
development-only takeover movement, airborne idle continuation, grounded model
alignment, facing, species scales, gorilla collision, and monkey mesh overlap.
See `OWNER-FEEDBACK.md` for the complete findings. Prior PASS receipts below
predate these corrections; they are not validation of the current edits.
Current focused/native checks and connected owner-plus-observer behavior must
be reported separately. Bat menu/request failure remains unreproduced; the
established movement-authorization defect does not prove its cause.

## Previous route candidate boundary

Authorized native diagnostic `run_96c745b32a9d05fda7bdd1e4`, generation 1,
passed all 36 selected fixtures in 7.201 s; parent status confirms ACK and cleanup
CONFIRMED. This validates the corrected reachable-idle and flight fixtures as a
diagnostic, not feature acceptance. The subsequent stationary diagnostic is
summarized below; neither receipt establishes sustained gameplay performance.

## Latest stationary diagnostic — run_ccdb5bbe1b31bdffb76501bc

Mixed (40), Parrots (100), and Kobolds (100) stationary captures all completed
with cleanup confirmed. After 500 population ticks and rendering fraction 1, late
server p95 was 11.78/18.36/22.88 ms; client p95 was 13.45/11.47/16.88 ms. Late
server p95 was below the prior stationary capture in all scenarios, but Mixed and
Kobold late server p50 increased (5.73 vs 4.40; 13.23 vs 10.44 ms), and Parrot
p50 was similar. Early Parrot/Kobold p99 increased; late client p95 worsened for
Mixed/Kobolds and slightly improved for Parrots. Different geometry, randomness,
and warmup prevent causal attribution or a blanket efficiency claim. These short
stationary results do not cover movement/jumping, sustained operation, or owner
acceptance. Fresh analyzer evidence is pending.

The latest stationary Mixed attempt completed capture but failed cleanup on a
process that exited between identity verification and stop; scope cleanup was
confirmed and analysis did not run. The launcher has a reviewed narrow
ObjectNotFound/re-observation fix with cheap race coverage. Fresh Mixed/Parrots/
Kobolds profiles remain pending; no new performance conclusion is available.

## Latest stationary population diagnostic — run_9ef98b5c14b9fa35bb10257b

Diagnostic result **PASS**, generation 1, cleanup **CONFIRMED**; this does not
change overall feature review **FAIL** or claim current feature PASS/readiness.
The actual local manifests, summaries, capture-coverage records and owned-process
proofs are retained in the following directories under
`build/reports/nonhostile-animals/`. Each is a 500-population-tick stationary
capture with rendering fraction 1 and renderer-call median equal to population.
Server tick p50/p99 milliseconds are baseline → early → late; client frame p95 is
late milliseconds:

| Scenario | Actual count | Server p50/p99 (ms) baseline → early → late | Client p95 late (ms) |
| --- | ---: | --- | ---: |
| Mixed | 40 | 4.41/24.25 → 10.36/74.11 → 4.40/25.70 | 11.43 |
| Parrots | 100 | 2.92/16.00 → 14.44/113.49 → 10.87/47.70 | 11.94 |
| Kobolds | 100 | 3.24/17.65 → 17.66/127.31 → 10.44/52.95 | 14.98 |

Exact evidence folders: `population-profiles/mixed-053d9265923546118fbd9af001f2909b`,
`population-profiles/parrots-022798d8959445acb0fe5d038aa8b00f`,
`population-profiles/kobolds-b479a1ee3d8f41099f2239cb9879b5fe`; proof-gate logs
`artifact_task_9ef98b5c14b9fa35bb10257b_log_094340644cc38b1369e6`,
`artifact_task_9ef98b5c14b9fa35bb10257b_log_1ec242d5311173ab6bfb`, and
`artifact_task_9ef98b5c14b9fa35bb10257b_log_bbc30d176686098f779f` each exited 0.
NO_PATH collision work is a lead, not the sole established cause; no optimization
is claimed. No controlled before/after comparison exists (historical geometry
differs). No movement/jumping, sustained-minutes, unloaded, visual or owner
acceptance is demonstrated. The historical 28-native-test pass predates this
diagnostic extension and overall review remains FAIL, not replaced by this result.

## Bound run status and sustained diagnostic — run_816abfb809a06bc4c0f14738

Overall review is **FAIL**. The coded native result was historically 28/28
required tests passed, but those tests predate the current diagnostic capture code;
the new capture has not been natively revalidated. The focused current diagnostic
checks (8 protocol/fixture-settling tests, Java and GameTest compilation, analyzer
20/20, PowerShell protocol checks and integrated source review) pass, but establish
neither native capture success nor technical PASS.

The sustained opt-in population protocol records server JFR accepted grounded-jump
requests for the same player/body/epoch across five 1,200-server-tick phases. It
does not inject input, bypass normal input, teleport or alter AI. Accepted request
counts are not launch proof; ascent is separate. Horizontal body distance is not
camera-pixel evidence, and near/outside-glass-pen sampling does not establish
animal visibility. No sustained capture, overall lag resolution, performance
acceptance or owner acceptance is established. Coordinator/operator must schedule
Mixed, Parrots and Kobolds serially using `-PopulationScenario <Mixed|Parrots|Kobolds>
-PopulationSustained -WallTimeoutSeconds 900` with a real join and normal physical
keys/chat; see `MANUAL.md`.

## Historical native evidence — run_816abfb809a06bc4c0f14738

The bound native log
`artifact_task_816abfb809a06bc4c0f14738_log_7dc2da308188347683a4` (SHA-256
`6f69b0c2c90f5722a00c8b53fcfb4d81881e7a6e1b84c5d6cb4e9e87fd1a7cc1`, 217,648
bytes) records 28 required GameTests passed, completion in 6.448 seconds, and
Gradle `BUILD SUCCESSFUL` (exit 0). Current bound unit, readiness, and package
checks also passed; their log is
`artifact_task_816abfb809a06bc4c0f14738_log_e1d92596970f0991d872` (SHA-256
`b8bae849eeec79f65ce19a76dc8e6179d40060d0a6100d6e044f6b7275ed72b7`, exit 0).
This establishes a coded-check decision PASS, including current flight-push,
cruise and cleaning native checks. The selected/registered total denominator was
not reconfirmed; report only 28/28 required tests, not an inferred total.

The immutable overall run status is nevertheless FAIL: independent review is
REVIEWFAIL. Coded-check PASS does not override that decision or establish
technical readiness. The review's valid population-runtime concern remains
unverified: prolonged physical movement/jumping distance and time evidence for
mixed (40), parrots (100), and kobolds (100) was not executed. Population-runtime
coverage is therefore incomplete; owner readiness and owner acceptance are not
established.

## Historical diagnostic status — run_e0641a8c881e0e77912df0d0

Latest settled native run `run_e0641a8c881e0e77912df0d0` is FAIL: filtered
selection 28/1,063, 27 passed and one failed (flight push regression). The fixture
now waits up to 600 ticks for actual FLYING state, elevated active idle goal, and
horizontal speed >=0.02 before applying one perpendicular impulse; it captures
the original goal and allows up to 500 ticks for recovery, with a 1,200-tick
overall timeout. This replaces a fixed tick-28 precondition, which failed because
the bat was already 0.456 blocks from its goal before the push. Compile and
independent review passed; native rerun has not verified this correction. Cruise
and cleaning passed in the latest native run. Readiness, package, and owner
acceptance remain unverified.

This is a complete-feature validation candidate, not owner acceptance. The new
coordinator must run its coded checks and independent reviews before any technical
PASS. Historical receipts are supporting evidence, not current gate results.

| Criterion ID | Implemented scope and reuse | Evidence / remaining boundary |
| --- | --- | --- |
| animal-composition | Resolved inherited character prototypes, shared animal hosts, physiology, distinctive inventories/equipment and original creature identity. No proactively hostile variants. | `PROTOTYPES.md`, character/catalog/animal tests; pinned upstream ancestry and deliberate Minecraft adaptations are recorded there. |
| animal-traits | Shared udders/milking, eggs, absorbent cleaning, parrot memory/speech, defensive retaliation, portability and inherited hands. | Existing animal GameTests and composition tests. Kobold one-hand restrictions and legacy mouse/native pig behavior remain required. |
| flight-and-animation | Client flight projection/predicted transitions remain separate from server authority; NPC takeoff/cruise/landing and steering use shared movement owners. Native clips follow interpolated displacement with cached pose blending. | Flight/transition/animation JVM tests and `animals_npc_flight` native fixtures. Visual quality, physical input feel and post-push flight require current native/manual verification. No replacement artwork. |
| role-access-and-control | Bounded operator-only animal offers reuse trusted development admission through `/ghostroles`; ordinary roles preserve their eligibility. Catalog sync handles canonical omitted fields and inherited defaults. | Catalog/admission/wire tests, `ghost-roles` and `development-possession` fixtures. Current connected takeover/camera behavior is not established by pure tests. |
| population-runtime | Repeated exact support queries reuse immutable results within one route attempt, not across ticks/world changes. Animation state is weak-key/per-entity; collision policy and AI budgets are retained. | R34 native 4/4 route-equivalence/work-reduction/freshness tests; R35 live parrot support-cache hits about 91%. New gate reruns focused regressions. Overall stall resolution, long-duration growth, movement/jumping and mixed-client performance are not established by these measurements; owner load testing remains required. |
| delivery-and-regressions | Existing shared UI/interaction/prediction owners, prototype codecs and asset provenance are preserved. Runnable operator setup uses current `/ghostroles` entry paths. | `MANUAL.md`, attribution files, focused units/native tags, readiness categories, clean package and managed-config provenance checks. Owner acceptance and post-acceptance full GameTests remain separate. |

## Current evidence and limits

The bounded-flight corrective work is now implemented: NaN ground-height inputs
are rejected by the overload, failure diagnostics are length-bounded, and bat
steering uses inverse actual-motor movement through the shared production flight
command transform. The altitude fixture now samples explicit bat positions over
time, supporting the minimum randomized cruise-height target (0.75) rather than
asserting an invalid single-snapshot value above 1. The expanded XML fixture
compiles with two passes covering eight headings, 0/0.15/0.38 tick impulses and
bounded-command cases. These corrections received independent source review;
the altitude fixture was compiled but has not run natively. Current author
validation completed `./gradlew.bat test --rerun` for the existing 15 policy
classes plus `NpcFlightSteeringTest`, then `compileJava` and
`compileGameTestJava`; exact XML reports show 16 classes / 52 tests, zero failures,
errors or skips. The retained log is
`build/agent-workflow/nonhostile-animals-current-units.log`. GroundNaN overload
and bounded-failure-diagnostic changes passed review. Flight steering uses
motor-consistent inverse steering and the shared flight-command transform in live
behavior and test; the actual-motor test covers eight headings and 0/0.15/0.38-tick
impulses with finite bounds (two JUnit methods). The bat altitude fixture observes
100 ticks, with headroom and upward samples, and passed compile/review only.

The prior native summary (28 selected / 1,063 registered, 25 passed / three failed)
is historical, not a result for this candidate. The shared workflow-owner source
fix for log collision is observed: capture is `Path(logdir)/check.log`, separate
from GameTest's typed `check.log`, guarded against aliasing. Coordinator validation
is running in replacement run `run_770d11d0d8c72f3a46d8ce8e`; native validation is
pending, so this source fix is not yet native-verified and is not a current blocker.
Current native checks, owner manual acceptance and sustained-performance testing
remain pending.

The duplicate-host fixture correction now uses a hostless derived prototype and
revalidates the augmented catalog with the same production validator before
reload. Fresh author checks passed all 15 selected unit classes / 50 tests, with
zero failures, errors or skips, verified in the individual XML reports under
`build/test-results/test/`. Native focused GameTests, readiness checks and other
remaining gate checks are pending; this author result is not a workflow PASS.

On October 20, main/test/GameTest compilation passed; nine selected JVM classes
produced 35 tests with no failures/errors/skips. Analyzer tests passed 16/16;
PowerShell ancestry/proof and population protocol checks passed. Exact selectors
and current XML reports are included in the submission evidence bundle.

R35's 100-parrot recording retained matching Ready/control/first-intent evidence,
an explicit station-builder-disabled marker and median 100 animal renderer calls.
Failed routes still had about 19-ms median elapsed duration and substantial edge
collision work. Station-free geometry differs from R33, so timings do not prove a
controlled overall improvement. Mixed never launched in R35. Native rendering,
second-player visibility and sustained performance remain honest review/manual
boundaries, not assumed passes.

## New-flow restart

The owner stated on October 20 that all old in-flight work was cleared and directed
a new run. This authorizes fresh validation; it is not historical verification of
R35's missing process records. The launcher now verifies full PID/creation-time
ancestry, retains identity-specific cleanup proofs and blocks unknown observations.
No legacy run ID is represented as a new coordinator run or replacement FAIL.
The new flow has no diagnostic checkpoint submission: this candidate includes the
whole implemented animal feature. Any coded-check or review failure must be
addressed before owner testing. No old receipt establishes current readiness.
