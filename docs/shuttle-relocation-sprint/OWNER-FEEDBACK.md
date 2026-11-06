# Connected contact/examination regression

## Latest connected feedback — 2026-10-28

The two-client owner test exposed additional defects; prior technical readiness
is not owner acceptance. See [the numbered test record](CONNECTED-TEST-FEEDBACK-20261028.md)
and [the angular-recovery design](ANGULAR-RECOVERY-DESIGN.md). Angular recovery is
the current discussion priority; the other defects remain queued. The owner uses
the normal launch and creates a fresh world each time; custom run directories are
not justified without a concrete technical necessity.

## Current owner direction — stock Sable

Use the unmodified pinned Sable release for active implementation, validation and
owner launch. Patched native candidates and their results below are historical,
not proof of the stock candidate. Handling may lag mass/COM changes across a
physics boundary; exact first-PRE inertia response is not an acceptance criterion.
Unexpected passenger/camera discontinuities, lost support and prediction
snapbacks remain unacceptable. Continuous edits must not indefinitely suspend
control. Natural uncontrolled tumbling is valid; retain controlled yaw stress at
0.5/1/3 rad/s without adding mandatory upright stabilization.

The stock adaptation and new coverage are in progress: large asymmetric block
batches in one game frame, substantial section loss, impact-like abrupt motion,
and prediction/reconciliation through the resulting frame changes. No automatic
client launch or owner-world/configuration changes are authorized. Headless
prediction-consumer evidence must not be described as rendered/connected proof.

Stock adapter work currently moves mass-dependent fixture actuation/braking to
Sable's completed-step POST callback, removes custom gyro feed-forward while
retaining bounded angular-rate feedback, and resets transport sampling when the
COM/pivot basis changes. First stock compilation passed; regression work remains
in progress. Prediction tests exposed roundoff-only COM rebakes being treated as
new carry intervals by exact matrix comparison. The local interval-admission fix
must preserve exact diagnostic fingerprints, prevent duplicate carry, retain a
fixed consumed baseline, and handle large-pivot cancellation without masking real
motion. This is an adapter issue, not a new Sable patch.

Stock compile and the selected JVM batch passed **72/72**, with no failures,
errors or skips (`stock-controls/cheap-validation/attempt-14` under the ignored
owner-feedback evidence directory). Dependency insight resolved
`maven.modrinth:sable:fg9dTRz9` without a native-candidate override. Coverage
includes the operand-scaled cancellation regression, real pending-input history
payloads, frame-authority admission, controller math and common classloading.
Independent source reviews passed for the controller and interval numeric policy.

Native source coverage includes a 384-block single-callback ballast change,
actual deck-support loss, native bridge-removal splitting with a passenger, and
occupied terrain impact using the existing physical impact runner. Fixture-only
timing/tracking corrections after that batch still require compile and native
validation. None is yet reported as stock native PASS or full connected prediction
validation. In particular, merged mass must be observed after normal physics
refresh, and projecting authority already consumes its supplied carry interval.

The first managed stock native diagnostic, R53
(`run_550aa0ba482700b1722006fc`), completed **21/959: 17 passed, 4 failed**;
cleanup and result acknowledgement are confirmed. Automatic splitting with
natural passenger transfer passed, as did mass-response, angular response,
braking, ordinary admin motion, rolled support, airborne side-impact boundaries,
and yaw targets 0.5/1 rad/s. Preserve these component results.

The two bulk cases failed their initial machinery-bay standing precondition,
before either edit callback. Their new callsites used an obstructed standing
point; use the existing proven clear-floor placement rather than loosening the
shared fixture. Impact stopped during ascent at an endpoint residual of about
`1.006e-7`, before contact; its numerical oracle needs the existing native adapter
accounting/projection precision, not a gameplay-tolerance increase. Controlled
3-rad/s yaw lost clearance after about 30 seconds (`omega≈(-.028,2.589,-.105)`,
tilt≈.227 rad). Uniform angular-budget saturation deprioritized off-axis damping.
The proposed correction allocates the same .25 acceleration budget to off-axis
rate damping first, then yaw tracking; gain, world-Y target, full-tensor conversion,
stress values, clearance oracle and absence of an upright target remain unchanged.
These corrections require fresh focused validation, not a native fork or a full
suite rerun. Full connected prediction/render/camera acceptance remains open.

R54 candidate: the allocation correction is implemented; both bulk cases use the
passing admin-passenger fixture's clear `(2.5,1,2.5)` local placement. The impact
oracle reuses `NativeCollisionSlop.MAX_RESIDUAL` and includes the intermediate
storage-coordinate ULP scale; contact coverage accepts the immediately preceding
native descent/deceleration at actual terrain contact, not ascent-only support.
Source review passed. Focused compile, **13/13 motion-math checks**, and metadata
preflight **6/959** passed (ignored evidence under
`stock-controls/r54/cheap-validation/relocated-root-evidence/attempt-03`).
The 60 unaffected JVM checks from R53 remain valid; this is composite evidence,
not a fresh 73-test batch. R54 native results are pending. Retry only bulk, impact
and all three yaw cases; the allocation change justifies repeating the lower yaw
targets, not all earlier native successes.

R54 (`run_3d396087a0dac919c47a07ff`) completed **6/959: 2 passed, 4 failed**;
cleanup and acknowledgement are confirmed. The unchanged 0.5/1 yaw cases passed
again. Off-axis priority delayed the 3-rad/s failure to sample 947 (~47 s), but did
not resolve it (`omega≈(-.071,2.666,.028)`, tilt≈.238 rad). Do not tune gains,
increase the .25 budget, level the hull or relax clearance to hide this result.
R55 adds a bounded paired PRE/HIGHEST–POST/HIGHEST–POST/LOWEST observation to the
existing 3-rad/s case, measuring native free response and intended versus actual
controller impulses before choosing another control change.

Both bulk cases now reach their callbacks. The support-removal witness incorrectly
expected stone where the authored hull floor is `STEEL_WALL_BLOCK`; the intact
case's geometry-only `1e-6` check was below stock COM-rebase f32 projection drift
(~.00076 at world X≈4000), while the Human's world point stayed unchanged. Use the
resolved floor and the already-established 16-float-ULP geometry bound; retain
strict immediate Human continuity and post-physics support checks. Impact still
stopped during ascent at ~`1.035e-6`; keep its current bound and instrument native
own motion, ordered response sum, carrier, bridge endpoint and final motor
endpoint rather than inflating precision allowances again.

R55 source corrections and bounded probes compile; metadata selects **4/959**
(`stock-controls/r55/cheap-validation/attempt-07`). Only the two bulk cases,
occupied impact and tagged 3-rad/s diagnostic are selected. Production code and
the 73-check composite JVM evidence are unchanged. This measurement diagnostic
may still fail known assertions; its results are not OWNER_READY.

R55 (`run_86f5783bd9bc584f1d853170`) completed **4/959: 0 passed, 4 failed**;
cleanup/acknowledgement are confirmed. Its measurements establish:
- The applied POST angular impulse matches the requested full-tensor world
  correction to native float precision. At 45 s the free angular acceleration was
  about `(-.0527,-.2449,.1197)` while the proportional correction was
  `(.0699,.2390,-.0224)`. The remaining drift is not an impulse-coordinate/cache
  mismatch. R56 retains one actual post-actuation world-omega sample and rejects
  the next completed interval's **measured off-axis** response within the existing
  .25 budget. It adds neither an upright target nor a gyro formula, integrator,
  yaw-drag compensation or native fork. The sample survives COM rebasing and is
  cleared at request/stop/brake/owner lifecycle boundaries.
- Impact's raw native own clip was `1.021e-6`, but vanilla applied zero own motion;
  native inherited carry matched actual carry exactly and bridge/final endpoints
  matched. `Entity.move` writes position only when the collision-vector squared
  length exceeds `1e-7`. The corrected independent oracle applies that verified
  vanilla branch after trace certification, before carrier projection; it does
  not adopt measured own motion or increase the residual bound.
- Bulk support loss reached world authority, then hit a fixture null-ID comparison.
  The intact case was still resting on the pad and lacked a real physical interval.
  R56 uses null-safe world-frame comparisons and bounded physical lift/horizontal
  flight before the intact-floor edits, not a weaker movement witness or teleport.

R56 source review and cheap checks passed: **15/15 motion-math tests**, **6/959**
metadata selection (`stock-controls/r56/cheap-validation/attempt-03`), with the
60 unaffected checks retained as composite evidence. Native results remain open;
repeat all three yaw cases because the actual control path changed.

R56 (`run_4f7783bc3e3d4203533923ed`) completed **6/959: 3 passed, 3 failed**;
cleanup and acknowledgement are confirmed. Support removal and 0.5/1 yaw passed.
The 3-rad/s case completed **all 1,001 physical samples** within unchanged bounds:
steady rate `[2.731729,2.749305]`, cross-axis rates below `9e-8`, maximum tilt
`6.81e-6` rad and minimum bottom `302.01456`. Its terminal FAIL occurred during
normal teardown: the temporary observer treated plot removal before delayed
success as a failure. Remove that now-unneeded observer, not the physical checks;
a clean terminal retry remains required.

Impact reached actual contact. Its residual now isolates a carrier-order oracle
error: Sable computes inherited carry from the **pre-own** anchor, then vanilla
own displacement and that carrier are applied separately. The independent
endpoint is `T(before) + vanillaAppliedOwn`, not `T(before + own)`. Do not widen
the collision allowance. The bulk round trip completed both real 384-cell edits
in flight, but its aggregate final comparison still needs boundary-aligned
accounting. R57 adds full-precision native metadata and actual outbound snapshot
world/storage points; the old assertion remains until that discrepancy is known.

R57 fixture-only source review/compile/metadata passed **3/959**
(`stock-controls/r57/cheap-validation/attempt-01`): intact bulk round trip,
occupied impact and 3-rad/s yaw. Preserve the passed support-loss/lower-yaw cases;
production control and composite 75-check evidence are unchanged.

R57 (`run_15d7003ac3c316df93c91ee0`) completed **3/959: 1 passed,
2 failed**; acknowledgement and cleanup are confirmed. The unchanged 3-rad/s
physical case now has a clean terminal PASS. Preserve it alongside R56's
support-loss/lower-yaw and R53's split/component successes.

Impact's remaining negative-own residual coincides with the existing certified
idle-probe suppression: the endpoint equals the independently projected carrier
reference, not that reference plus the suppressed downward probe. R58 checks
eligibility using the existing negative-probe, clearance and endpoint-support
services against the independently projected candidate; reported certification
and suppression are consistency witnesses, not the endpoint oracle.

Bulk's final discrepancy is `0.00417958` versus the unchanged `0.00390625`
geometry allowance. Actual outbound snapshot local coordinates match their
corresponding actor points; a simple timing explanation is not established.
Capture removal's instantaneous fixed-point geometry/body displacement, as
already done for addition, before attributing the delayed discrepancy to carry
or rebasing. The aggregate assertion remains unchanged. Next native selection
contains only intact bulk and occupied impact; this remains diagnostic work,
not OWNER_READY or connected/rendered camera proof.

R58 focused compile and metadata preflight passed **2/959**
(`stock-controls/r58/cheap-validation/attempt-04`); independent source reviews
passed. Immediate removal guard failures are retained but reported after the
delayed accounting, preserving both observations without relaxing either guard.
Only intact bulk and occupied impact are selected. Production and the composite
75-check JVM evidence are unchanged. Stock per-block COM-update bytecode has not
been reverified; repeated-rebase accumulation remains a hypothesis, not a claimed
cause. Native R58 results are pending submission.

R58 (`run_bf1a518ccd947dbe94074d09`) completed **2/959: 1 passed,
1 failed**; acknowledgement and cleanup are confirmed. Occupied physical impact
and its headless prediction-consumer checks passed; preserve that result.
Removal immediately leaves the passenger's world position **exactly unchanged**,
but moves both fixed geometry projections by `0.004180007576` (existing allowance
`0.00390625`). Two ticks later the residual is `0.004180006649`: the discrepancy
is already present at edit time, not growing passenger drift during subsequent
carry. Same-frame native support remains fresh. Establish the stock rebasing
mechanism before changing either the adapter or the geometry invariant; do not
attribute it to snapshot timing or merely widen the allowance.

Official-JAR inspection confirms ordinary block changes refresh merged mass
outside physics, so a 384-cell callback can perform 384 COM rebases. The path is
`SubLevelPhysicsSystem.handleBlockChange → updateMassDataFromBlockChange →
MergedMassTracker.uploadData`: read native pose, add rotated COM delta, teleport,
then replace the rotation point. `ServerLevelPlot`'s previously cited lines
628/652 concern loading, not this edit path. The prepared pinned Rust source
casts teleport position/quaternion to Rapier `Real`; this is source evidence,
not disassembly proof of the official DLL.

R59 keeps stock mass behavior. Its fixture work replaces the single-rebase
geometry assumption with independent sequential quantization accounting, retaining
the existing residual budget and strict passenger-world check. Expected later
carry starts from the held pre-edit world anchor rebased into the post-edit
coordinate basis, never from the observed post-edit passenger endpoint. The
numeric model must itself pass focused tests and live stock readback checks;
the observed millimetric geometry drift is not exact geometry continuity.

R59 source review, compile, **6/6 new quantization-model JVM tests**, and
metadata selection **1/959** passed (`stock-controls/r59/cheap-validation/attempt-05`).
The evidence is **81 composite checks**: 75 unchanged checks plus six fresh ones,
not a fresh 81-test run. Fixture-only helper `BulkMassQuantizationOracle` advances
from each batch's initial native pose and per-cell COM deltas, never observed
post-edit positions. Live native readbacks must match the predicted f32 sequence;
fixed-point model residuals retain the existing 16-float-ULP budget. Addition and
removal have separate seeds across the intervening flight. Strict immediate
passenger-world continuity and final carrier/support assertions remain.
Only intact bulk is selected for the next managed diagnostic; impact, splitting,
support loss and yaw passes are preserved. Production behavior is unchanged.

R59 (`run_f2201b7da4de4eebb92548ef`) completed **1/959: 1 passed,
0 failed**; acknowledgement and cleanup are confirmed. Both 384-cell callbacks
left the passenger world point exactly unchanged; all per-cell native readback
checks passed. Addition's fixed-point model residuals were exactly zero. The
roughly `0.00417960` removal geometry drift is consistent with the independently
checked stock rebase sequence, not subsequent passenger drift; the rebased
held-world-anchor carry/support check passed. Preserve this result with R58's
occupied-impact PASS and the earlier split/support-loss/yaw successes.
Selected stock diagnostics are now settled, but this is not feature readiness,
connected/rendered camera proof or owner acceptance. The complete OWNER_READY
gate and connected manual checks remain separate outstanding steps.

Readiness audit found no remaining scoped implementation blocker. The current
owned feature head was verified as `run_84714a21d56e08450dc585f1` (settled PASS,
older source), not a failed diagnostic; it needs a fresh complete OWNER_READY
replacement for this stock revision. R25's recorded FAIL is diagnostic history.
The normal package/provenance gate and implementation/parity/coverage reviews
remain required. `SHIP-FIXTURE.md` now gates the owner's isolated connected
session on that new readiness PASS, not merely on the settled diagnostics.
No automatic client launch, owner-world/configuration change or full-suite run
is authorized by this checkpoint.

Do not use `r54/cheap-validation/attempt-01` as native readiness evidence: an
unintended out-of-lane server invocation used two substeps and a dirty disposable
fixture. It terminated and selected only `build/gametest-tags-fixture`, not an
owner-world/config path. Subsequent approved cheap checks launched no server;
further native execution belongs exclusively to the serialized workflow lane.

## Connected stress feedback — 2026-10-26

**2026-10-27 airlock parity correction:** pinned SS14's shared collision handler
predicts bump-opening client-side; the prior inference that missing access
prediction data required server-only opening was wrong. The access reader is
networked, and the `predicted` flag selects audio routing, not server-only state
execution. The port's server-only bump path is therefore a concrete parity gap.
Client prediction is being wired to the existing resolved Door defaults and
`AirlockState` transitions, with authoritative reconciliation and unchanged
server permissions/safety. See `docs/airlocks-sprint/PARITY.md`; validation is
pending. Bounded door-contact tracing remains diagnostic, not a behavior fix.

Client bump prediction is now wired through the committed CHARACTER's actual
move/contact path. It reuses loaded world/native contact ordering and controller
identity checks, resolved `standard_airlock -> base_airlock` values, and
`AirlockState.open/tick`; held contact does not restart the first-half deadline.
An active-controller index advances only pending overlays on client game ticks.
Effective component passability drives both halves' dynamic collision shapes,
without writing chunk OPEN bits. Authoritative identity/revision admission covers
the complete view/victim payload; stale packets cannot restore crush exemptions.
Server power, electrification, contact memo and occupied-close authority remain.
Existing server-routed SS14 audio is retained to avoid duplicate playback.

Production/GameTest compilation and **57 focused JVM tests** pass; independent
component/collision and contact/ownership reviews pass. Real connected movement
and rendering remain unverified. The new physical roll/brake probe uses actual
owned-movement observations and an independently admitted loaded floor trace;
its support/stop runtime check and affected native airlock controls are pending.

R46 `run_0f7f5d0ab278c862ab9d046a` **failed**, acknowledged and cleanup confirmed:
**16/953 selected**, server aborted during the first test with no completed native
results. Common `AirlockBumpSystem` referenced fully qualified client-only
`ClientLevel`; dedicated-server class verification rejected it before gameplay
checks could complete. Replace that type test with common `Level.isClientSide`
and extend the source classloading guard beyond imports/class literals to catch
qualified type uses. This failure provides no rolled-support or airlock-behavior
verdict and does not invalidate the unchanged R45 jump proof.
The corrected common-side check and qualified-type scanner pass review. The
stronger scanner also exposed three client-clock reads in shared examination
code; callers now pass the render partial tick explicitly, with logical `1.0`
defaults retained for server queries. No query geometry or ownership changed.
Production/GameTest compilation and **74 focused JVM tests** pass, zero failures,
errors or skips. No client was launched.

R47 `run_94c71d77be384d541fe1f503` remains **ERROR** (`GAMETEST_TEARDOWN_TIMEOUT`),
acknowledged with cleanup confirmed. All **16/953 selected cases completed:
15 passed, 1 failed** before teardown timed out. Preserve the 15 native airlock
admission/safety successes as component evidence; this is not connected-client
validation or feature readiness.

The physical roll/brake case failed its first idle support move after braking at
approximately **42.5 degrees** tilt. The artificial `(0,-0.001,0)` support probe
produced approximately `(-0.000498,-0.000457,0)` own displacement, consistent with
downhill tangent projection against the oblique floor normal. Fresh geometry and
native/measured motion matched, but strict motor bounds rejected the response.
The idle-floor proof currently excludes negative measured Y. Diagnose artificial
probe motion separately from real geometry separation; do not merely widen bounds
or admit accumulating downhill drift. Reuse existing SAT/contact/loaded-box proof
owners. Connected airlock presentation and camera smoothness remain unverified.
The roll fixture now independently bounds consecutive and cumulative post-brake
deck-tangent drift from actual body positions in the inverse logical frame pose;
source review passes, but compilation/runtime validation is pending. Reporting
zero bounded movement alone is insufficient: any suppression must physically
correct the endpoint, preserve actual applied carry once, and independently prove
the no-own endpoint is clear and supported. Existing foot-band clearance does not
establish full-body clearance; extending that loaded-geometry owner is the next
prerequisite. Candidate clearance now extends the existing foot-band owner's
shared loaded-geometry traversal; a separate same-floor query matches captured
floor cuboids to current loaded shapes and checks candidate top contact using
the existing SAT service. World clearance conservatively uses the oriented
envelope (possible false obstruction, not permission to pass an obstruction).
The guarded suppression candidate is now wired to the actual applied carrier
delta, retaining raw native measurements and recording the physical cancellation
separately. Production/GameTest compilation and final source/reuse review pass.
The focused JVM batch passed **126/126 across 12 classes**; the additional
negative-idle SAT proof test subsequently passed **1/1**, without modifying its
fixture math. The roll fixture now requires a nonzero physical-suppression sample
and bounds cumulative post-brake deck-tangent drift to `.002` (the old `.006`
budget could miss seven samples of the reproduced drift). It preserves the
independent physical-position oracle and verifies suppression cancels raw own
motion without turning it into position-correction evidence. Final fixture
compilation passes.

R48 `run_950fb3645975d96cdcdb2e5a` **FAIL**, acknowledged with cleanup confirmed:
**18/953 selected, 17 passed, 1 failed**, all completed with clean server shutdown.
The physical roll/brake case passes the strengthened no-drift and nonvacuous
suppression oracles. At post-brake tick 8, measured deck-tangent drift is zero,
four samples have physically suppressed the artificial response, and raw native
own motion remains separately reported with the opposite physical cancellation.
Actual carrier movement remains present. All unchanged **0.5/1/3 rad/s yaw stress**,
jump/departure, ceiling/wall, strong-impulse and airlock-safety controls pass.
Preserve these component successes rather than rerunning the whole union solely
because the managed result is FAIL.

The sole failure is `ordinaryWorldFloorCollisionAndOverheadClearance` at its new
`upper-body obstruction remains separated from foot band` assertion. Diagnose
the authored fixture geometry versus the query before changing either; the other
live candidate-volume/native obstruction and unknown-world controls pass. Native
log artifact: `artifact_task_950fb3645975d96cdcdb2e5a_log_18df94db789e52c72b19`
(SHA-256 `46b126f8453bf9e5111573cc782945e7f59833e295bae714660771988d5c47c3`).
Connected-client camera/prediction validation and complete owner readiness remain
unverified; this diagnostic is not M1 acceptance.

Source/log diagnosis establishes a fixture error: the added block at Y301–302
overlaps the foot band when measured feet Y is `301.00000115344545`, so `CLEAR`
was an invalid expectation. The fixture also leaves its Y302–303 block present
before expecting full-body clearance. Reuse that higher block alone, explicitly
prove it misses the foot band but intersects the body, then remove that same
block. Production clearance rules and tolerances must not change for this fix.
That fixture-only correction now compiles and passes independent source review.
The new `shuttle-world-candidate-clearance` method tag isolates its rerun without
repeating the 17 passing R48 cases. Its native result is recorded below.

R49 `run_d65bf86e84bce8c1b34ce8db` **PASS**, acknowledged with cleanup confirmed:
**1/953 selected, 1 passed**, clean server shutdown. The corrected world-clearance
case now verifies foot-band clearance, full-body obstruction and clearance after
removing that same block. Together with the retained 17 R48 successes, every case
in that focused boundary selection has passing component evidence; R48's original
FAIL remains unchanged and this is not a fresh 18-case union PASS. No production
code changed between these native runs. Log artifact:
`artifact_task_d65bf86e84bce8c1b34ce8db_log_1a0d640f58b5e44ada34`
(SHA-256 `70d7a9f746cde7ad680344c1f0919cdcc452f142cc4c5b02570518620c669600`).
Connected airlock movement/rendering and rolled camera smoothness remain
unexecuted; focused native PASS is not complete OWNER_READY or owner acceptance.
Final scoped source assessment found no additional concrete camera/client-airlock
implementation gap. The remaining evidence requires an owner-controlled connected
session: check smooth supported camera through roll/brake, bump-opening visuals
and passage through both airlock halves after login, and denied/unsafe contact
remaining server-authoritative. Client catalog/login ordering remains runtime
unverified. No automatic client launch or full-suite submission is authorized by
these diagnostic results alone.

Owner clarification: yaw-only flight is the primary cross-axis regression check;
intentional roll is secondary tilted-support/camera coverage, not proof of the
backend yaw fix. Previous manual roll/brake instructions were unusable because
only translation/world-Y yaw controls were exposed. The owner authorized adding
fixture roll/pitch and an explicit physical brake before another manual session.
The admin controls are implemented: `roll` uses body-local +Z, `pitch` body-local
+X (angular-velocity deltas in rad/s); legacy `yaw` remains world-Y. `brake`
releases feedback and queues one full-tensor counter-impulse at the next physics
PRE boundary, without resetting pose or attitude. Gravity/collisions continue.
`motion_stop` remains release-only. Status adds body-up and tilt diagnostics.
Production/GameTest compilation, **23 focused JVM tests across four classes**,
and independent source/reuse review pass. Actual-dispatcher native controls and
the existing admin-motion boundary checks remain pending. No client launch or
owner-world/configuration changes are implied; the isolated owner launch recipe
must select the verified native candidate and confirm six effective substeps.
The isolated recipe now reuses `ms14ClientRunDir` and `stageSableServerConfig`
with the same fresh build-directory instance, then explicitly selects R40.
NeoForge 21.1.228/FML loading was checked: a world's SERVER config overrides the
instance config; otherwise the staged instance config is used. Use a fresh world
and confirm six steps via status. `SHIP-FIXTURE.md` contains the combined owner
sequence, prioritizing yaw-only flight before intentional tilt. No client was
launched; command-native execution remains pending.

R50 `run_1f6852cd6859f48489046ba0` **FAIL**, acknowledged with cleanup confirmed:
**6/955 selected, 5 passed, 1 failed**, clean shutdown. The actual-dispatcher
physical brake PRE check passes, along with all four existing admin-motion
controls (including passenger/jug carry). The angular-command case failed before
command execution: its new world scene reused exactly `(1960,300,1660)` from
`ShuttleAdminCommandMotionGameTests`, so the concurrently authored support pad
prevented the required empty-scene predicate. Chunk readiness was already true;
do not widen timeouts or weaken scene checks. Separate the new scene and retain
the five successes. Strengthen the new angular/brake oracles with actual physical
pre-tilt so local axes and attitude preservation cannot pass vacuously on an
upright ship. Production command/service code remains unchanged for this fixture
correction; manual controls are not yet presented as validated end to end.
The angular fixture now uses a separate region `(2700,300,2500)`. Both new
control cases require meaningful physical pre-tilt before testing local-axis
deltas or braking; no direct pose reset is used. Final GameTest compilation and
independent fixture review pass. The focused retry covers those two strengthened
manual-control cases only, preserving the four existing admin-motion successes.

R51 `run_b0414c49d7b3a8c185dbd469` **FAIL**, acknowledged with cleanup confirmed:
**2/955 selected, 1 passed, 1 failed**, clean shutdown. The strengthened brake
case passes with meaningful physical tilt and same-PRE velocity cancellation/
attitude preservation. The angular case fails its pre-oracle rotation premise,
not a measured angular-delta assertion. Its fixture seeds rotation while still
on the support pad and omitted the lift used by the passing brake setup. Add the
existing physical lift before seeding rotation, retain the same orientation/delta
bounds, and record actual pose/velocity at the precondition. Isolate only that
angular case for the next diagnostic; preserve the tilted-brake success.
The corrected angular setup now applies the physical lift before all rotation
seeds, requires over 2 m of native COM rise and the unchanged rotated-axis
precondition, and records bounded native pose/velocity/axis diagnostics. Fixture
review and final compilation pass. Its angular-only tag is method-scoped (not
class-inherited), so the next selector must resolve to one case.

R52 `run_41fb2f2e3857c2c47d50a41c` **PASS**, acknowledged with cleanup confirmed:
**1/955 selected, 1 passed**, clean shutdown. The angular fixture physically rises
over 6 m and reaches a clearly nonidentity native orientation before verifying
body-local roll/pitch deltas and legacy world-Y yaw through the actual dispatcher.
Combined with R51's tilted-brake PASS and R50's four existing admin-motion
successes, the six scoped control cases have passing component evidence across
runs, not a fresh six-case union PASS. No production command/service code changed
during these fixture corrections. Log artifact:
`artifact_task_41fb2f2e3857c2c47d50a41c_log_b2bce7ac3179068ea4be`
(SHA-256 `8e6d03fef2c44d0755813a6847eb2f1cb1db6c38962879d6ac6aa40c8a511e65`).
The owner can now perform the combined targeted session in `SHIP-FIXTURE.md` with
real exposed roll/pitch/brake controls. No automatic client launch, full-suite
run, feature OWNER_READY declaration, or owner acceptance follows from this
diagnostic PASS.

Current follow-up: defer the full-suite request until the complete candidate is
owner-ready. R43's focused native PASS is diagnostic evidence, not readiness.
Investigate jump behavior from the first accepted motor launch, rather than
starting flight checks only after native tracking changes. Door authority stays
server-owned: upstream solidity changes at the partial-open transition, not at
initial bump. New bounded door-ordering observations and camera lifecycle tests
do not themselves establish a connected fix. No automatic client launches.

New focused jump fixture `boundHumanRotatingJumpLaunchesBallistically` (tag
`shuttle-rotating-jump-regression`) uses the actual grounded motor jump, checks
launch-point velocity immediately, and compares eight subsequent physical moves
against the resolved motor's free-flight baseline. It does not wait for native
tracking identity to clear before checking flight. Existing departure/landing
controls remain unchanged. Independent source review passes; native execution
is pending. Camera helper coverage includes rolled stopping and support rebasing;
it is not a connected camera-event test. Optional airlock-order observations are
default-off and bounded to 128 records/200 ticks per controller; authority and
partial-open solidity timing are unchanged. Door/input correlation remains open.

R44 `run_6c131581825dcdae35f7859f` **failed**, acknowledged and cleanup confirmed:
**2/952 selected, 1 passed, 1 failed**. The retained rotating departure control
passes. The new first-launch case establishes delayed jump release: a fresh
accepted +0.42-block jump retains the native tracking frame and applies native
carrier displacement, but body velocity remains `(0, 6.664, 0)` m/s instead of
motor velocity plus measured SDK launch-point velocity
`(-0.1501962, -0.00000176, 3.0075081)` m/s (3.0112562 m/s residual).
Correct at the motor-attested accepted-jump/native-move boundary, using the
existing native tracking and frame-velocity transition services on both client
and server. Preserve rejected/head-blocked jumps, standing carry and ordinary
positive-Y impulses; do not weaken the first-launch or ballistic-flight oracle.
The correction candidate carries the motor's actual jump-initiation decision
through the existing collision resolver. Native acceptance and fresh, matching
live-frame evidence still gate release. It keeps the launch interval's inherited
displacement, clears only that native tracking frame, and reuses the existing
one-shot frame point-velocity correction. This is a Minecraft jump adaptation,
not a claim that SS14's 2D mover implements this jump lifecycle. Validation is
pending; there is no client launch or default dependency change.
Both production and GameTest compilation pass. The focused JVM batch ran 59
tests with one new test-oracle failure: a legacy four-argument resolver call
explicitly supplied an idle-probe flag, but its test incorrectly expected that
flag to be discarded. Production correctly preserves it. Correcting only that
test, the motor class passes all 55 tests; independent review passes the motor
attestation and native release integration. Native revalidation is still pending.

R45 `run_4dd9b6077836d0ed156a1ebc` **passed**, acknowledged and cleanup confirmed:
**5/952 selected, 5 passed**, candidate R40 and six substeps unchanged. Accepted
jump launch now inherits the measured SDK point velocity immediately (velocity
residual 4.44e-16 m/s). The subsequent eight neutral airborne steps pass physical
displacement/velocity checks with no renewed native inherited displacement.
Retained rotating departure and all three selected ceiling-side/ceiling-only
controls pass. This resolves the reproduced delayed jump-release defect, not
connected camera smoothness or airlock prediction; M1 remains unaccepted.

Owner tested yaw targets **0.5, 1, and 3 rad/s**. These are stress rates, not
slow-rotation acceptance substitutes. Zero-yaw movement, posture recovery,
moving-puddle slipping, Creative/body return, and movement after landing worked.
Remaining reports: held-forward airlock crossing blocks locally then corrects
forward; throws can begin inside hull walls unlike ordinary world walls; prone
F5/observer models do not turn with the hull; onboard first-person yaw jitters
while the offboard hull looks smooth; jumping briefly follows the same floor
tile before transitioning to inertial flight; high yaw produces unintended roll;
rolled support alternates insufficient/excess view carry, and stopping can snap
view nearly 90 degrees. These remain blockers, not explained by target rate alone.

Verified latest evidence: `build/evidence/owner-feedback-20261026/20261026T182738Z/`.
Six source copies were stable and hash-matched; manifest SHA-256
`64306eb946cad6f550592baefb2942706a3ccc2a6c2255a535b5eb3b9a26dd21`.
Session begins 10:58:36, player joins 10:59:42. Ship
`fd4b9265-d593-4fde-8c2e-a6bea6f0d989`, body
`07fc4cc4-7acb-4069-9a06-b7ec163ddc88`. Full server log has 85 unique rejection
payloads; client has 86 prediction-stop entries and 25 framed discrepancies.
Rates are logged targets, not measured achieved angular velocities. No CPU
profile or event-level cause for all visual symptoms is established.

Source investigation confirms launch-volume clearance currently checks ordinary
world shapes but omits native-frame shapes. Door bump activation is server-only;
the precise prediction/block-update ordering defect remains unestablished.
Logical-tick heading versus interpolated rendering is a rotation investigation
target, not yet a demonstrated cause.

Current candidates: throw launch clearance now probes the already-constructed,
detached item's physical box against world and canonical native-frame shapes
before committing hands. Its authenticated native regression establishes clear
world geometry, complete/known frame geometry and a real wall intersection,
then checks denial preserves token/revision/stack and creates no loose entity.
Compilation and independent review pass; native execution is pending.

Supported heading now consumes incremental world-up quaternion twist rather
than projecting the frame's absolute local +Z, which is singular near vertical.
A client-only camera listener adds the render-pose offset relative to the last
consumed logical pose, without advancing logical carry or modifying input yaw.
It shares support/identity guards and preserves a world-upright camera. Source
review, production/GameTest compilation and seven heading JVM tests pass,
including 0.5/1/3 rad/s wrap/rolled cases and repeated read-only render offsets.
Connected smoothness, prone model facing, jump release, doors and capsizing
remain unverified/unfixed by these candidates.

Test-fixture note: published JOML 1.10.5 `Quaterniond.rotationX` puts its cosine
in Z rather than W in this artifact. Cache bytes match Maven Central (SHA-1
`22566d58af70ad3d72308bab63b8339906deb649`); no cache repair/version change was
made. Heading pitch fixtures use canonical component quaternions and verify
their transformed basis, rather than silently testing an incorrect pitch.

Shared prone-facing carry now updates the synchronized world-facing value and
therefore its render/interaction consumers, including stationary down bodies.
It reuses the existing server posture tick and a transient per-attachment frame
basis. Support loss freezes facing; get-up/re-prone clears and reseeds the basis;
accepted own crawling keeps precedence. Shared world-up twist math also serves
client heading. Lifecycle/source review passes. The grouped production/GameTest
compile and eight focused JVM classes pass **85 tests**, zero failures/errors/
skips; retained XML is under
`build/agent-workflow/owner-feedback-20261026/r23-unit-evidence/`.

Three new native yaw diagnostics target **0.5, 1, and 3 rad/s**. Each naturally
settles upright before a bounded physical lift, verifies hull clearance above
the actual pad surface, then records native angular velocity, tilt and clearance
through 280 ramp ticks plus 101 steady ticks. Requests last 1200 ticks; steady
rate bounds are diagnostic (max 0.05 rad/s or 10%), not gameplay tolerances.
Gross capsizing is a failure, not prevented by a lock. Source review and final
GameTest compilation pass. The launch-wall, prone-facing and stress fixtures
remain runtime-unverified until the next managed diagnostic. Airlock prediction,
jump-release timing and exact causes of unintended roll remain open.

R23 diagnostic `run_f7d18ba3abf053f408621312` failed; acknowledged, cleanup
confirmed. All **198 JVM tests** and **37/39 native cases** passed, including
prone-facing transport/own-crawl precedence and all 34 retained controls. The
upright airborne stress cases measured 0.47847 rad/s for target 0.5 and 0.95694
for target 1, with maximum tilt about 1.4e-6 rad over their sampled windows.
These do not establish stability under contacts or connected camera smoothness.

Two fixture corrections follow actual findings. The launch-wall case's jug box
missed the native wall by about 0.00233 blocks, failing its independent overlap
premise before the throw request. It now uses a real stick's resolved generic
0.65-wide profile for a robust overlap without moving the body inside the wall;
world-clear/frame-complete/intersection and hand-preservation oracles remain.
The 3 rad/s case measured 1.9896–2.2753 during seconds 14–19, still accelerating.
Those samples fit the bounded 0.25-acceleration/0.09-drag response; they do not
show an axis defect. Its unchanged rate band begins at 2.7 rad/s, reached by
that model around 39.6 seconds. Only this case's ramp is now 900 ticks, followed
by 101 steady samples (seconds 45–50); the 1200-tick request, target, rate band,
and capsize/clearance checks remain unchanged. Compilation and independent
fixture review pass. Managed rerun remains pending; no production physics
tuning, target cap, or attitude lock was added.

R24 diagnostic `run_da95d888f45fd70a0a65daee` failed; acknowledged, cleanup
confirmed. All **198 JVM tests** and **38/39 native cases** passed, including
authenticated hull-wall launch rejection. The longer 3 rad/s case exposed real
cross-axis growth: sample 686 (34.3 s) had yaw 2.6398 rad/s, X/Z rates about
-0.1005/-0.03462 rad/s, and tilt 0.22047 rad (12.6 degrees). Hull bottom was
300.9713 over pad top 300: still airborne when the 1 m reserve-margin check
stopped the case. This is not proof of contact-induced roll or owner camera cause.

Pinned Rapier source now confirms gyroscopic forces are enabled, merged local
inertia is installed as native mass properties, and local torque impulses are
rotated to world coordinates. The fixture rate controller omitted the native
gyroscopic compensation term. Its candidate correction computes local
`Omega cross (I Omega)` and prioritizes compensation within the unchanged
0.25 acceleration-equivalent budget, scaling rate feedback into the remaining
budget. It targets angular velocity, never an upright orientation. Grossly
nonsymmetric inertia is rejected while roundoff-scale asymmetry is tolerated.
Production/GameTest compilation, nine focused math tests, and independent
review passed, but R25 `run_b3c739ca45d1c70862091ecc` **failed**: 201 JVM
tests passed, 38/39 native cases passed. The 3 rad/s case failed earlier at
sample 322 (16.1 s), tilt 0.21317 rad and bottom 300.9891 above pad top 300.
Lower-target maximum tilt also regressed: 0.00972 rad at 0.5 and 0.11403 rad
at 1, versus R24's near-zero tilt. The candidate is not an accepted correction.
No target/rate-band relaxation or leveler was added.

R26 `run_3cce928e2efd04e607c2c380` **passed**, acknowledged, cleanup confirmed:
**6/947 selected native cases**, six substeps, no JVM rerun. Two new
`NativeAngularResponseGameTests` cases verify upright/compound-tilted instantaneous
local impulses against merged inertia and current native orientation on all
three axes, then restore through opposite physical impulses; four admin-motion
controls also pass. This validates that measured impulse contract, not the
gyro controller, long-run stability, connected visuals, or M1 readiness.

Pinned Sable `ad8b4d377741fbd923f4d863b2cf9e462daff9a6` serializes the full
local inertia correctly and gives its sublevel collider zero density. Parry
0.26.0 resolves principal inertia plus its local frame; native world inverse
inertia includes that frame. Teleport immediately refreshes world mass
properties; impulse calls wake the body and velocity reads are synchronous.
However, independent source review of pinned Rapier fork
`38e92f117590862481a53df6fc69a5d893e29186`,
`src/dynamics/rigid_body.rs::angvel_with_gyroscopic_forces`, confirms that its
gyro update uses principal values with body rotation only, omitting the
principal-frame rotation. This is a source-level defect candidate, not proven
causation for the tested hull. Next isolate multi-step free gyro behavior with
measured nonaligned inertia; do not tune compensation against an assumed
standard Euler update or treat R26 as replacing the failed stress tests.

R27 `run_49cee36129d6e8d3aba31ffe` **failed**, acknowledged, cleanup confirmed:
**8/949 selected native cases: 6 passed, 2 failed**, six substeps.
Both gyro diagnostics stopped before body creation: their requested empty scene
included Y=369, outside Minecraft's build-height range. Tickets were loaded and
entity-ticking; `SableFixtureScene.emptyAndLoaded` correctly rejected the
out-of-range cells. The retained impulse calibration/admin controls passed.
No native gyro classification was obtained. Correct fixture placement and add
a build-height regression check; do not relax empty-scene or physics guards.

R28 `run_977a32fe6dc35c5a47031a65` **failed**, acknowledged, cleanup confirmed.
The corrected build-height placement passed setup, but the first gyro observer
rejected its measured tensor at the X/Y-equality premise. Its analytic 2x2
eigenvalue formula already supports unequal diagonals; that extra equality is
not required. More importantly, the observer's catch called throwing
`helper.fail`, escaping the physics event and crashing the server. Eight cases
were selected from 949, but there was no terminal test-count summary and no
gyro classification. Remove the unnecessary equality premise without widening
physics thresholds, retain XZ/YZ/SPD guards, and report callback failures through
the normal GameTest scheduler rather than throwing into the physics event bus.

R29 `run_166189854d81c259217495f8` **passed**, acknowledged, cleanup confirmed:
**8/949 selected native cases, all 8 passed**, six substeps. The two
controller-free gyro fixtures both classified `BACKEND_PRINCIPAL_FRAME_OMISSION`.
Across twelve measured native intervals, the fixed diagonal-in-body-axes model
fit upright/tilted angular velocity with RMS error **7.88e-7 / 5.44e-7 rad/s**;
the frame-correct full-tensor model erred **0.019108 / 0.014311 rad/s**. The
measured tensor has Ixy approximately 0.6667 and principal values approximately
1.6667, 3, 3.6667; minimum model separations were 0.00204 / 0.01035 rad/s.
This confirms the native gyro-frame defect on discriminating free-flight bodies,
not its full causal contribution to the cabin stress failure. Four focused JVM
model/guard/failure-dispatch tests also passed before this run; none were rerun
by R29. R25's production compensation remains failed and unchanged. Investigate
the smallest native-backend repair path before further controller tuning; retain
the unchanged 0.5/1/3 rad/s stress tests and intentional-roll requirements.

Owner subsequently authorized the narrow native repair. The candidate at
`tooling/native-physics/patches/rapier-principal-frame.patch` composes body
rotation with the local principal-inertia frame for both gyro transforms,
preserving momentum normalization and all remaining integration behavior.
Source pin/license/hash metadata is in `tooling/native-physics/rapier-source.json`.
Clean-base patch application and whitespace checks pass; Rust regressions are
not yet compiled or executed, and no replacement native artifact is installed.
The official Sable native bundle has no supported external-DLL override, so
rebuilding/repackaging and explicit dependency provenance remain necessary.
Existing MSVC/Windows SDK are available; Rust is absent. Keep any forthcoming
bootstrap isolated and native builds serialized; no global toolchain changes.

Owner requires live block placement/removal during free and controlled rotation.
Source audit confirms the existing tracker recomputes mass, COM and full inertia,
but revealed a separate timing defect: Sable posts new properties before PRE,
while Rapier's additional-mass setter queues effective-property recomputation
until the following physics step. PRE impulses can therefore combine new tracker
inertia with old native effective inertia. The minimal candidate at
`tooling/native-physics/patches/sable-effective-mass-refresh.patch` explicitly
recomputes effective properties inside the existing native setter; it introduces
no second cache or angular-velocity reset. Source/license/hash metadata is in
`tooling/native-physics/sable-source.json`; compilation is still pending.

`NativeMassChangeResponseGameTests` now covers ballast addition/removal in free
and controller-active rotation. Both edit callbacks require observed angular
motion, and the first eligible PRE must match the new inertia immediately—no
post-edit retry can hide the stale-property window. The instantaneous probe is
shared with existing angular calibration. Compilation, independent review and
six focused JVM tests pass; replacement-backend runtime coverage remains pending.

R30 `run_36670fcbd8b2e4b36c8cbff5` **failed**, acknowledged, cleanup confirmed.
The first managed Rust-regression request stopped in Git source preparation
before Rust bootstrap or Cargo execution; no Rust tests ran. Native command
argument forwarding/exit capture and diagnostic routing were corrected in
`tooling/native-physics/build-candidate.ps1`. Actual preparation now exits 0
with pinned source/patch hashes verified; five summary-parser and two command
fixtures pass, and independent review passes. Subsequent intake should collect
the always-created whole-driver log and report, rather than require a Cargo log
before Cargo starts. Rust compilation and both native fixes remain unvalidated.

R31 `run_31f173d444964d826d3b0644` **failed**, acknowledged, cleanup confirmed.
Pinned Rust nightly installed successfully under project-local build homes;
the version guard then stopped before tests because the unit workspace selected
Parry 0.26.1 instead of production 0.26.0. The driver now plans only a precise
Parry downgrade, validates the selected dependency/checksum and unchanged other
packages, then runs locked tests. It reuses the installed local nightly and
disables local rustup auto-self-update. Five lock-policy fixtures pass alongside
existing driver checks and independent review. No Rust regression has run yet;
no replacement DLL or game dependency is installed.

R32 `run_1378bc345385723901d3bf3e` **failed**, acknowledged, cleanup confirmed.
The precise Parry downgrade succeeded, but a PowerShell array-enumeration bug
counted that intentional change as unrelated lockfile drift. The comparison now
operates on individual package records, retains strict non-Parry drift checks,
and has positive reuse and multi-package regression fixtures. Independent review
passes; the existing unit lock now selects the correct Parry 0.26.0/checksum and
is reused. Compilation and Rust tests still have not run; no DLL was produced.

R33 `run_23c69addcfd7c4bf0f9d9623` failed before Cargo startup because nested
Windows CMD quoting was invalid. Cleanup is confirmed. The driver now imports
VS environment variables into Cargo's child process and launches Cargo directly;
actual spaced-path/batch and VS2019 environment smoke checks and independent
review pass. Native compilation remains pending.

R34 `run_6b8e38d876dd30bc9c4f186e` failed in preparation: an optional raw-command
parameter defaulted to an empty string and overrode ordinary process arguments.
Cleanup is confirmed. Raw mode is now explicit-only; final source preparation,
generic argv (including spaced paths/quotes/empty arguments), raw CMD/VS setup
smokes and independent review pass. No Rust compilation or tests ran in R34.

R35 `run_1ac50452da1504f2dc4814a3` failed during Rust test compilation; cleanup
is confirmed. The new test module referenced an unimported assertion macro.
Fixtures now use vector-norm assertions and explicitly recompute their initial
mass properties before exercising gyro behavior; the production transform fix
is unchanged. Source/manifest hashes now consistently use UTF-8/LF, and actual
preparation exits 0. Rust regression execution remains pending; no DLL exists.

R36 `run_c07b56de3be39966d5bc8eb2` is immutable **ERROR**: cleanup could not be
verified at decision time; subsequent status confirms cleanup. Its captured
command exited **0**, and both Rust regressions executed successfully (**2 passed,
0 failed, 20 filtered**). Preserve and reuse that source/hash-bound evidence;
do not relabel the managed result PASS or rerun completed tests just for a new
verdict. Next build the Windows native candidate with both source patches and
the production Sable lockfile. No replacement DLL or runtime repair exists yet.

R37 `run_1374dc21b7ca32e55e7c820a` failed before DLL compilation because a
PowerShell metadata merge duplicated its status key; cleanup is confirmed.
Clone-and-overlay transitions now preserve source metadata and pass five
control-flow fixtures, actual DLL source preparation, and independent review.
Both patches and the 172-record production lock are prepared; no DLL was built.

R38 `run_de1961af5b29f09e62719798` is immutable **ERROR** for cleanup
verification; subsequent status confirms cleanup. The actual command exited
**0**: both patches compiled into the Windows x64 release DLL in 84 seconds,
and AMD64 PE validation passed. DLL SHA-256:
`7b5c57b88d29bc7ce8ef8d6e75947db7fd6cd40c419ffd7d9d5ea7f8c1ee6c15`.
Reuse its hash-bound build receipt instead of rebuilding for a managed PASS.
Packaging must preserve all other native and Java payloads. No game dependency
has been replaced; corrected gyro behavior, first-PRE mass edits, intentional
roll and unchanged 0.5/1/3 rad/s stress validation remain pending.

The gyro fixtures now succeed only for `FRAME_CORRECT`; reproducing the known
wrong model is no longer a passing repair regression. Compilation and seven
focused gyro/mass JVM tests pass. Receipt-bound packaging source and payload
preservation checks are reviewed; real packaging is not yet run. Gradle's
explicit `-Pms14SableNativeCandidate=<jar>` opt-in checks paired provenance and
replaces, rather than duplicates, the SDK dependency. Default builds still use
the official artifact; no owner run configuration or world was changed.

R39 `run_6cfe3af83414f300bbbb95f4` failed after writing a candidate JAR because
verification expected compact JSON, but PowerShell had reformatted provenance.
Cleanup is confirmed; that candidate is unverified. The verifier now uses
build-only pinned Gson semantic JSON parsing and checks all nested/bundle hashes
against actual bytes. Twenty-three synthetic checks, the real PowerShell JSON
roundtrip and source review pass. Repackage into a fresh R40 output directory;
reuse the DLL and Rust evidence, without rebuilding either.

R40 `run_2b8fe82626948c5ad2fbe635` **passed** packaging and separate verification;
cleanup is confirmed. Candidate:
`build/native-physics/artifacts/r40/sable-fg9dTRz9-native-fixed-win-x64.jar`,
SHA-256 `4807c4b92edbf6b7037c7cd171dcb4a0295b7369a64332c63375598fe9898c2b`.
All other native and Java payloads are preserved; default dependency unchanged.

R41 `run_960527c93aa9fc1e7b2993af` **failed**, cleanup confirmed: **13/951 selected,
11 passed, 2 mass-edit cases failed**, six substeps, explicit R40 candidate.
Both gyro cases report `FRAME_CORRECT`; all unchanged 0.5/1/3 rad/s stress cases
pass. At target 3, steady yaw is 2.7022–2.7160 rad/s, maximum tilt 0.003254 rad
(0.19 degrees), minimum hull bottom 302.0206 over pad top 300, 1001 samples.
Mass-edit addition/removal callbacks occur, but terminal failure messages were
not logged, so neither a stale-native-property defect nor tracker roundoff is
established. Removal currently requires bit-identical tensors, which incremental
floating-point tracking does not guarantee. First capture full tracker deltas
and per-axis native-response diagnostics without changing either oracle.

R42 `run_3ba004e149081633901e35d0` **failed**, cleanup confirmed: **4/951 selected,
2 controls passed, 2 mass-edit cases failed**. Diagnostics establish that every
addition/removal first-PRE native probe passes in both free and controlled spin
(maximum response error approximately 1.29e-7 rad/s; unchanged limit 1e-5).
The sole removal failure is bit-identical tracker tensor restoration: maximum
entry drift 6.2088e-9, with mass and COM exactly restored. Replace only that
invalid test equality with a fixture/source-arithmetic-derived bound; retain
the independent native-response and first-PRE requirements unchanged. No backend
or DLL change is indicated by this failure.

The tracker round-trip oracle now uses the fixture's weighted-COM and
parallel-axis operation error bound (about 2.35e-6 for the captured inputs),
not a gameplay epsilon. Addition must remain materially outside that bound;
removal must return within it, and the unchanged native response probe must
independently pass on the first PRE. Three focused model tests, compilation and
independent review pass. Native revalidation remains pending; DLL unchanged.

### Roll robustness and future leveling

Owner requires both correction of unintended yaw-induced roll and correct
operation under intentional roll/pitch. A rotated wall or ceiling becoming a
physical supporting surface must not break movement or view/model continuity.
Do not hide these failures with a mandatory upright lock or reduced yaw cap.
An optional ship-leveling machine, analogous in role to a gravity generator, is
a future feature idea: enabled constrains attitude to level/yaw-only operation;
disabled permits roll/pitch. It is not implemented or part of the present
bugfix scope; its design/prototypes and SS14 naming need separate work.

## Latest connected feedback — 2026-10-25

### New manual report after stock-interval diagnostic PASS

Owner testing after `run_c16e415fc1a7040aa23056e3` reports:
- First boarding stalls outside, then snaps inside as the door opens; later
  entries do not reproduce the same spike.
- Cargo retains its cabin-local position under the reported “15 yaw,” but
  presentation jitters severely. Frozen evidence confirms the author's supplied
  `motion ID 0 1 0 15` command actually selected yaw **15 rad/s** with the default
  **120-second** duration. The instruction and subsequent claim that `15` meant
  duration were author errors. Intended lift syntax is `motion ID 0 1 0 0 15`;
  no rerun is requested.
- After tabbing out, the shuttle was sideways and the body stood on its wall.
  Cause and ordering remain unknown. The owner also entered Creative, left the
  shuttle frame, and returned to the controlled character.
- After righting the shuttle and repeating turning, character yaw outpaced hull
  yaw, producing relative rotation and worse jitter.
- Jitter persisted after jumping onto world ground, even idle, with new apparent
  input-to-motion delay.

Preserve the latest connected logs before relaunch. Investigation separates
first-entry cost, fixture angular dynamics, and control/frame/prediction state
across Creative/body return. No cause, CPU attribution, or fix is established.
Further stress testing is paused; the bounded native PASS is not M1 acceptance.

Latest session evidence is frozen with source metadata and SHA-256 manifest at
`build/evidence/owner-feedback-20261025/20261026T053025Z/` (five source files,
including command history). Local session window: 22:11–22:29 on Oct 25. Ship
`34b47ed7-f233-4738-8961-ce79754ec4ab`, body
`37ce66f1-1185-4d38-a6bf-2add37265203`. One accepted controller start records
target `(0,1,0)`, yaw `15`, duration `2400` ticks; target is not achieved-rate
evidence. Initial counts include 43 framed discrepancy warnings, five movement
rebases, and 25 rejected harness steps. These do not attribute each visible
failure or measure CPU cost. High yaw and later control/frame transitions need
separate event-level analysis; the first-entry spike cannot be dismissed.

Full launcher logs refine that initial summary: accepted starts occurred at
22:14:51 (expired 22:16:51) and 22:17:23 (admin stop 22:18:15), both with the
same yaw-15/120-second request. The earliest framed discrepancy and collision
replay rejection occurred at 22:11:44, before either command. Client launcher
logs contain 45 discrepancy rows across epochs 2/4 and repeated stop entries
through 22:29:37; repeated entries are not distinct stop incidents. Later rows
combine null native tracking with frame-storage metadata; the diagnostic field
semantics and authority state require verification before calling that stale
frame state. No post-start measured ship angular velocity or CPU profile exists.

Command ambiguity correction: motion now requires explicit seconds and echoes
world velocity, world-Y yaw rate, duration, and physical-control limitations.
Finite target velocities/yaw remain uncapped. The actual-dispatch fixture checks
missing-duration syntax cannot replace an active request/deadline and that the
intended zero-yaw 15-second lift records 300 ticks. Production/GameTest
compilation and independent review pass; native dispatch is pending validation.
`SHIP-FIXTURE.md` examples now include explicit durations. This correction does
not claim to fix connected prediction, persistent jitter, or first-entry cost.

Command diagnostic `run_4844ae2b54cc741ff49ee1a8` passed **169 JVM tests and all
32/938 selected native cases at six substeps**; acknowledged, cleanup confirmed.
Missing-duration dispatch rejection and active-request/deadline preservation now
have native evidence. Movement corrections remain separate and unfinished.

Next bounded corrections target (1) supported side-first contact with a separate
verified floor witness, retaining native grounding authority and all full-proof
gates, and (2) the separate SDK living inherited-velocity input during exact
tracked-character owned calls. The SDK field's normal travel-return update/drag
path is bypassed by owned travel; a controlled seeded-field regression will test
this second motion channel without claiming its unlogged value caused the exact
historical rejection. Generic stock callers, native inherited position carry,
and motor-owned release velocity must remain unchanged.

Both candidates now have source and focused regressions. Supported admission
attests the actual first contact plus a same-frame geometric upward floor
witness; it retains the prior grounding guards and full supported proof. Its
independent review passes. The velocity adapter zeros only the separate SDK
velocity-motion argument for the exact active tracked-character scope, without
mutating the SDK field or native inherited position carry. A controlled native
test compares seeded-field raw SDK and owned calls. Both independent reviews
pass. The completed combined batch passed production/GameTest compilation and
all **60 JVM tests across seven selected classes** (zero failures/errors/skips),
confirmed from its XML reports. An incidental supported-test trace-constructor
argument mismatch was corrected without changing assertions. The parent missed
the completion notification; this was a stale status report, not an active
compile wait. Neither movement correction has managed native evidence yet;
R21 is being assembled for that next stage without repeating the completed
seven-class batch.

R21 diagnostic `run_5cb7951bd6574f177c977554` failed; acknowledged, cleanup
confirmed. All 172 JVM tests passed; 32/34 native cases passed. The new grounded
side-first case passed. An existing floor-edge airborne control regressed because
the broadened supported branch intercepted its transient case. The velocity
control failed before seeding: setup `moveTo` had not refreshed native tracking.

Corrections retain supported side-first admission but give an identity-bound
negative transient candidate with known CLEAR pre-carry feet precedence; its
existing post-carry clearance and rollback remain mandatory. The velocity
fixture now performs a genuine unseeded native departure move and verifies fresh
null tracking plus WORLD identity before its decisive seeded comparisons.
Neither failing oracle was relaxed. Independent review passes; a terminal
foreground batch passed production/GameTest compilation and **61 tests across
seven JVM classes**, with zero failures/errors/skips. R22 native validation is
the next stage; neither correction has a managed PASS yet. Finite worker checks
must return terminal evidence, not detach background shells and report a wait.

R22 diagnostic `run_2daaa6413327c434ba7e34b1` passed **173 JVM tests and all
34/940 selected native cases at six substeps**; acknowledged, cleanup confirmed.
Grounded side-first admission and the retained airborne floor-edge control both
pass. The seeded SDK velocity control now establishes actual WORLD ownership,
retains raw unscoped SDK behavior, and confirms the exact tracked-character
owned call avoids that second motion input without mutating its backing field.
This is native mechanism evidence, not a recovered value from the owner's
historical session. First-entry CPU cost, connected visual/yaw continuity after
Creative/body return, and owner-observed persistent jitter still require
connected evidence. No full replacement OWNER_READY or owner acceptance exists.

Replacement readiness `run_84714a21d56e08450dc585f1` passed and was
acknowledged; subsequent owner testing found further blockers. **M1 is not
accepted. Further retesting is paused pending corrections and a fresh gate.**

- Wall contact/interactions increase client profiler cost; first boarding caused
  client/server spikes; intermittent idle/tabbed-out client lag remains unexplained.
- Entry/exit sometimes produces prediction teleports.
- Slip recovery aboard placed the controlled body through the floor onto terrain.
- Fixture target `.5 0 0 0` produced slow upward drift.
- Jug flight jitters during simultaneous translation/yaw; multi-tick inertial
  motion and packet-anchor presentation require separate verification.
- Remove arbitrary fixture target velocity/yaw caps, retaining finite validation
  and bounded physical actuation. Passenger flinging/sliding (#7b) is excluded.
- A puddle eventually stopped slipping; volume/speed eligibility and episode
  lifecycle must be distinguished before calling this a regression.

Frozen actual owner logs: `build/evidence/owner-feedback-20261025/122304/`
(four stable source copies and hash metadata). They establish 18 server bounded
motor rebases from 11:55:53–12:16:31 for body
`8ec232bb-5d23-4382-abb9-cf4881185080` and shuttle
`0fb05421-6f59-44d1-b337-ee121d2b1539`; four precede first logged motion.
Client prediction stops/discrepancies overlap but do not prove an event-level
cause for every visible teleport. No profiler capture establishes CPU attribution.
Early posture/slip log matches are startup messages, not gameplay evidence.

Source inspection confirms duplicate render-stage pile queries: `pileFrame` and
`pileEpoch` did not prevent refresh. Correcting that redundancy is not proof that
first-entry or idle spikes are resolved. Posture, zero-vertical-target drift and
unsupported rotating-frame jug regressions are being added; compilation or a
release-instant assertion alone is not runtime proof.

Focused diagnostic `run_84b0687f48feb17d4dfe5154` failed: fresh 20 JVM tests
passed; 3/8 selected native cases passed (8/926 selected), at six substeps.
Cleanup is confirmed and the result acknowledged. Native posture recovery
reproduced feet falling from Y≈301 to Y=300 and losing the cabin frame; the
low-ceiling rejection also failed. The real-slip case failed before slipping,
at its movement setup assertion. The jug oracle hit the real roof on its first
tick, so it did not establish free-flight behavior. The command test retained
an obsolete capped-target refusal assertion. Lifecycle, passenger/cargo and
bounded zero-Y drift checks passed; the drift window does not exclude a slower
persistent climb. Corrections and another focused diagnostic are required.

Rerun `run_aa8b96a2acad038f082d458e` also failed: fresh 29 JVM tests passed;
5/8 native cases passed at six substeps. Ceiling rejection and uncapped command
coverage now pass. The floor control could not start get-up; frame clearance is
being narrowed to newly occupied growth space rather than existing resting-floor
contact, without increasing tolerances. The real puddle case admitted slip and
recovered automatically before its manual-request assertion; it now observes
production get-up provenance and the first standing anchor. The jug case never
threw: one-shot grounded yaw decayed below its threshold, and its translation
baseline incorrectly projected the distant storage origin. It now uses sustained
physical fixture control and the cabin's actual storage anchor. These changes
remain pending a fresh diagnostic; neither floor recovery nor jug flight is
claimed proven. Cleanup is confirmed and the failed result acknowledged.

`run_3afdf7dc99894dc55c756df3`: fresh 30 JVM tests and **7/8 native cases**
passed at six substeps. All three posture cases now pass, including real water
slip, observed production automatic get-up and sustained floor anchoring.
This is server-side proof, not connected acceptance. The sole failure was the
jug fixture's pre-flight clearance conversion: it compared centre-relative
coordinates with absolute storage coordinates. The fixture now uses the SDK's
full inverse pose transform. Free-flight proof remains pending. Cleanup is
confirmed and the result acknowledged.

Follow-up diagnostics add engine profiler sections `moonstation14_pile`,
`moonstation14_frame_ray` and `moonstation14_body_step`, plus bounded existing-trace
prefix/vertical witnesses for unsupported-mixed rejections. These expose future
profiling/correlation evidence; they do not establish a measured performance
cause or change movement admission. Numeric fixture drift windows are also being
recorded without relaxing the existing bounds or changing controller physics.

`run_8ca7f142e4710b658c031683` passed fresh **31 JVM tests and all 8/926
selected native cases**, six substeps; result acknowledged and cleanup confirmed.
This includes real slip/get-up anchoring, ceiling rejection, uncapped commands,
and four unsupported inertial jug-flight intervals under sustained translation/yaw.
It does not prove connected jug presentation or resolve prediction teleports.

The numeric drift receipts expose a remaining problem despite the loose test
passing: consecutive one-second airborne zero-Y windows rose approximately
0.14752, 0.05261 and **0.04102 blocks**, while endpoint native Y velocities
decayed to 0.03044, 0.003703 and **0.000451 m/s**. The final pose displacement is
not explained by endpoint velocity alone; native integration/actuation/pose
sampling is under investigation. No altitude lock or movement-feel change is
authorized by this result.

For the owner prediction rejection at seq 11309, the 13-row trace passes native
geometry/order/reconstruction and fails the final first-plane contact certificate:
ordered own response adds Z≈2.312813e-6, exceeding that certificate's unchanged
1e-6 tangent residual bound. Diagnostic row index 12 is the last trace index,
not a failed geometry row. A typed ordered-response certificate is being developed;
the separate seq 3725 mixed native-floor/side domain remains unresolved.

Next correction candidates measure fixture transport velocity from successive
native COM poses (not an altitude error) and add microstep-aware ordered contact
certificates only after full native geometry/order/reconstruction validation.
Legacy single-plane evidence remains the first path; residual bounds are not
increased. A compact 13-row seq11309 unit fixture exercises the exact recorded
geometry and response math, with a neutral full-cube block-state stand-in; it is
not a reconstruction of the owner's world terrain. Both candidates still require
fresh focused checks and independent review before any readiness claim.

`run_5b39d1c92c102d938a0aa1a5` passed fresh **124 JVM tests and all
20/926 selected native cases at six substeps**; result acknowledged and cleanup
confirmed. This includes the corrected frozen seq11309 certificate regression
and existing wall, ceiling, airborne, posture, water and jug-flight controls.
Airborne zero-Y one-second transport windows measured **0.00192261,
0.00195313 and 0.00192261 m/s**, each below the unchanged 0.005 m/s steady
diagnostic bound. The 18 PRE samples distinguish actual COM transport from
SDK instantaneous Vy (approximately -0.0431 m/s); neither value is an altitude
lock or a promise of zero drift. Connected presentation and performance remain
unverified. Seq3725 remains rejected pending a separate transient-floor/side
proof and trustworthy final-support classification; its observed world clip
is identity, but earlier native floor contact does not prove final grounding.

Seq3725 follow-up candidate (not yet managed-validated or wired into movement):
the compact 16-row fixture and shared frozen-trace loader have passed three
focused tests, including the unchanged seq11309 regression. A distinct transient
proof separates replayed downward own movement from signed geometric Y residue;
its position evidence cannot match as grounded. The new read-only final-feet
query uses the existing 0.001-block support band, loaded block access, complete
frame candidate reporting and shared SAT. It distinguishes clear, obstructed and
unknown coverage; obstruction is not itself a floor classification. Unsupported
frame scale is unknown, never clear. World/frame clearance and no-force-load
GameTest oracles are prepared but native execution is pending. The transient
policy still requires fresh focused validation and exact raw world-clip identity.
Live integration must independently bind the clip call, require proven final
clearance, and preserve shared client/server prediction; no server-only grounding
override is delivered by this candidate.

`run_ae38074d3d648a0a55eeb49d` diagnostic **failed**: 128 JVM tests passed;
21/23 selected native cases passed at six substeps. The two new clearance cases
failed their initial clear-band assertions; the unloaded-world UNKNOWN/no-load
case and the previous 20 native controls passed. Result acknowledged and cleanup
confirmed. Both failing tests start aboard an authored cabin floor, so their
claimed empty-band premises require correction/verification before attributing
failure to the query. No live grounding integration or readiness is established.

Corrected-fixture diagnostic `run_bb8286c352dd101e047fde02` passed fresh
**128 JVM tests and all 23/929 selected native cases at six substeps**; result
acknowledged and cleanup confirmed. The three clearance cases now exercise
known world/frame bands, obstruction, read-only body state and unloaded-world
UNKNOWN without force-loading. This validates the selected server helper and
pure-proof checks, not live transient grounding. Next integration must use a
shared client/server loaded query and an explicit physical endpoint pose, never
client render interpolation, before admitting the transient airborne branch.

Live-integration candidate (fresh validation pending): clearance now shares
client/server loaded access and copies the logical physics endpoint pose rather
than render interpolation. Server FULL-status admission is retained. The shared
move adapter prepares transient proof and clearance after own movement but before
inherited carry, without reordering existing movement. If carry changes position
or bounds, it requires another clear query before committing the new airborne
decision. Both queries use the same bounded 16-chunk/one-chunk-margin policy;
unknown or obstructed results do not authorize the override. Caller-policy and
endpoint-pose unit tests plus two owned-frame edge/support GameTests are prepared.
Their live branch behavior and connected-client presentation remain unverified.

`run_8fa51f4f9af7e1cbc15d65d0` diagnostic **failed**: 133 JVM tests passed;
23/25 native cases passed at six substeps. Both new transient cases stopped
inside fixture setup (`clear bounded native test volume`, local cell 1,1,1),
before their native movement/proof assertions. The previous 23 controls passed.
Result acknowledged and cleanup confirmed. Idempotent block-authoring semantics
must be respected when clearing already-air cabin cells; no live transient
branch result or connected-client proof is established by this run.

Corrected-fixture diagnostic `run_61c1c7fd157551531eff4787` passed fresh
**133 JVM tests and all 25/931 selected native cases at six substeps**; result
acknowledged and cleanup confirmed. The owned-frame floor-edge case now reaches
the accepted transient proof and airborne decision; the final-floor support
control rejects that override and stays grounded. This is bounded server-side
integration evidence, not a complete reproduction of every owner rebase, a
connected-client presentation/performance result, readiness, or owner acceptance.

An ignored, focused JVM audit replayed all 18 frozen owner records through the
current five typed proof entrypoints: 286 rows, 285 with geometry and one genuine
null geometry row (seq8663). The seq3725/11309 anchors passed. Five events have a
passing mathematical domain; this is conditional on captured-state assumptions,
not final-world coverage or live caller admission. Eight further floor/ceiling
compound cases fail contact certification (13168, 15182, 15473, 15720, 15829,
16016, 16131, 28051); intermediate vertical-clipping bookkeeping is being checked.
Four complete traces need separate vertical-domain analysis (3936, 11446, 18339,
28494). Missing geometry at seq8663 must not be fabricated or treated as air.
Audit result SHA-256: `19f65ea5d0a259311e47dfa11962022683053ea8ef7615fd57cdb6b5f1d9a2b9`.

The refreshed six-domain focused JVM audit passes its anchors and finds a
mathematical domain for **16/18 events** after compound bookkeeping fixes and
the new pure ceiling-only proof. Seq3936 remains outside these domains;
seq8663 still has genuinely missing geometry. Conditional floor support and
neutral known-state fixture assumptions are not live caller admission or final
world coverage. Ceiling-only proof is not yet live-wired. Fresh result SHA-256:
`803535f0ceef9478e6047f691263aa6f4f267ec0a7c9596eccf44d2ea42e525f`.

Oriented clearance now consumes final-substep native yaw and the canonical
Sable shape provider through bounded loaded reads. Source review and compile
checks pass; fresh focused checks pass **141 JVM tests**. The compiled eleven-tag
native union is **26/932**, retaining previous controls and adding the 45-degree
corner/empty/unknown-yaw oracle. Native invoker linkage and these new runtime
results remain unverified pending a fresh managed diagnostic.

Managed diagnostic `run_95ba9cf61a74bf3f419a50ae` **failed** after passing
141 JVM tests and 23/26 selected native cases (932 total, six substeps).
Acknowledged; cleanup confirmed. The new oriented corner/empty/unknown-yaw
oracle passed, exercising native shape-invoker linkage. The transient floor-edge
airborne case and final-floor support control also passed. Two older clearance
controls threw a null-scope exception during their new stock-SDK setup probe;
the admin lifecycle fixture timed out at its entity-ticking scene prerequisite
(loaded chunks, not entity-ticking after 40 ticks). Corrections are pending;
this failed selection is not readiness or acceptance.

The null-scope dispatch is now guarded, and clearance fixtures obtain yaw from
the actual tracked move before query snapshots or floor removal. Their ordinary
world-floor oracle uses authored Y=300 rather than a float-sensitive feet-cell
offset. Compile checks and independent source review pass. The lifecycle fixture
retains entity-ticking/empty-scene prerequisites but allows 80 startup ticks;
its timeout increases by the same 40 ticks to 260, leaving physics windows and
assertions unchanged. A fresh managed rerun is still required for these changes.

Corrected diagnostic `run_2a6b60f34f8a47cfeec40112` passed **141 JVM tests and
all 26/932 selected native cases at six substeps**; acknowledged, cleanup
confirmed. This validates the corrected stock/tracked setup and readiness wait,
oriented corner/empty/unknown-yaw query, native shape invoker, and retained
movement/posture/puddle/jug/fixture controls within that selection. Ceiling-only
proof remains unwired; seq3936, missing seq8663 geometry, connected presentation,
and client performance evidence remain open. No OWNER_READY or acceptance claim.

Subsequent source now wires `CEILING_ONLY_POSITION` through exact-current-call
world-clip admission and the existing ceiling position-evidence consumer. Seven
focused JVM checks and independent source review pass. Positive requests with a
downward native ceiling result remain eligible, including seq11446's separate
ordinary-world floor clip. Proof replay runs once; this route does not override
grounding or native normal-impact velocity authority. Native caller fixtures
are being added; the preceding managed PASS does not cover this newer wiring.

For seq3936, transforming the selected storage cube with its captured pose gives
floor-top center Y `-59.00000225516382`; the captured candidate body bottom is
`-59.02347772311641`. The positive native MTV therefore corresponds to a real
late-step overlap, not a missing geometry record. The OBB center already includes
the current microstep movement; do not add `collisionMotion` again. Initial
clearance/grounding and a suitable ascending-floor proof remain unresolved;
this calculation does not authorize a new movement domain.

Ceiling-caller diagnostic `run_06a6a0b6dd8f2886aa90f63c` failed after passing
142 JVM checks and all 26 retained native controls (27/933 selected, six
substeps); acknowledged and cleanup confirmed. The new fixture passed its
descending-ceiling admission and ordinary upward-clipping stages, then reached
`CEILING_ONLY_POSITION` for the positive-request/world-floor case but rejected
its context. Native Y was `-0.0980005335640245`, measured Y `-0.00200000000000955`;
native Z was `-8.78282436744265e-8`, measured Z zero. Raw clip components were not
printed, so near-zero vanilla axis snapping remains an inference. The fixture
now uses nonzero X/Z motion, as in seq11446, while retaining strict raw clip
X/Z identity and unchanged production proof bounds. The fixture-only adjustment
compiles and passes independent source review; its managed rerun is pending.

Diagnostic `run_544eb013a75c12c92a099f77` passed **142 JVM tests and all 27/933
selected native cases at six substeps**; acknowledged, cleanup confirmed. The
live ceiling-only path now passes descent and positive-request/world-floor-clip
fixtures while retaining the prior controls. This does not resolve the earlier
zero-X/Z case. Inspection of this build's Minecraft 1.21.1/NeoForge 21.1.228
mapped bytecode confirms a per-axis `<1e-7` zeroing path in nonempty voxel-shape
collision; attribution of the earlier call still needs its raw clip witness.
No proof bounds were relaxed; M1 and connected performance/presentation remain
unaccepted and unverified respectively.

Three additional focused JVM checks now exercise the actual mapped shape APIs:
strict per-axis `1e-7` boundaries, empty-shape/list behavior, and floor-Y clipping
followed by tiny-axis normalization. A test-only fourth ceiling-fixture stage
will record the sealed raw clip and verify either the current strict rejection
or an unchanged-horizontal accepted call. No production admission rule changes
are included; whether a natural native call exhibits normalization remains to
be observed by the managed diagnostic.

Diagnostic `run_8e1422741f3d6418e0b0578a` passed **145 JVM tests and all 27/933
selected native cases at six substeps**; acknowledged, cleanup confirmed. Its
zero-axis probe observed a complete, exact-call-bound downward world-floor clip:
input `(8.215491872565689e-7, -0.09800053115683706, -9.558543531255253e-8)`;
output `(8.215491872565689e-7, -0.0020000000000095497, 0)`.
`normalizationObserved=true`; the unchanged strict caller rejected with
`INVALID_CONTEXT` / `NOT_EVALUATED`, no correction, and no grounding override.
The raw witness plus actual-API checks establish the narrow normalization case;
production handling is still pending. This build exposes canonical
`Shapes.EPSILON=1e-7`, which should be reused rather than adding another threshold.

Subsequent source recognizes that exact vanilla axis-to-zero pattern only in
the ceiling-only downward-world-floor context. It reuses `Shapes.EPSILON`, keeps
the combined horizontal residual within the existing `1e-6` budget, and rejects
attenuation, sign reversal, threshold-or-larger zeroing, invalid values, and
non-floor contexts. Other proof domains and grounding/velocity authority are
unchanged. Compile checks, 32 focused JVM tests, and independent source/boundary
review pass. The fourth native fixture now requires actual position-proof
acceptance rather than the prior diagnostic rejection; its rerun is pending.

Diagnostic `run_a430a3572ef5306add7ef383` passed **146 JVM tests and all 27/933
selected native cases at six substeps**; acknowledged, cleanup confirmed. The
raw zero-axis probe again observed normalization and now reached `reason=NONE`,
`positionStage=ACCEPTED`, with unchanged grounding. The strict normalization
handling is therefore live-tested within this ceiling/floor domain.

Remaining capture gap: default tracked-character scopes omit geometry for an
exact positive-Y floor MTV, while transient/ascending proofs need that snapshot.
Controlled HUMAN server steps use tracked-character policy with observation off;
other mob stock-SDK paths are separate. Seq8663's missing row has selected MTV
`(0, 0.41339804586007034, 0)`, consistent with the omission policy, but its lost
geometry cannot be reconstructed. Capture-policy correction and ascending-floor
proof work are next; no wider readiness claim is made.

Subsequent source captures every selected native row in an exact tracked-character
scope, including pure upward-floor MTVs, with observation disabled. Wrong-entity,
stock-SDK, forced-observation, and bounded-completeness behavior are preserved.
A 36-test focused joint batch passes after correcting a test overload; capture
source review passes. The new exact-axis callback fixture uses explicit current
construction geometry (not a stock-interval claim) and remains runtime-unverified.

The new pure ascending-floor proof reuses SAT, response replay, floor redirect
checks, and horizontal certificates while preserving positive own Y exactly.
Seq3936's frozen 11-row regression, floor-before-side prefix, and explicitly
synthetic floor-only tests pass; independent source/test review passes. Its
position evidence cannot certify grounded support. Live ascending caller and
clearance integration are not yet implemented.

The refreshed seven-domain JVM audit passes all anchors and finds a mathematical
domain for **17/18** frozen events. Seq8663 still rejects with
`GEOMETRY_REPLAY_FAILURE` on its genuinely null row. The audit covers 286 rows
(285 with geometry); neutral known-state assumptions and conditional support
are not owner-world coverage or live admission. Report SHA-256:
`67f6360c542daa480658fefd4e15999ac7e9a1650688a61be74102018903e33e`.

Diagnostic `run_859861db0ca81b8c3cb635b5` passed **165 JVM tests and all 28/934
selected native cases at six substeps**; acknowledged, cleanup confirmed. The
new exact-axis native callback test confirms positive-floor geometry is present
with observation disabled in its explicit current-geometry fixture. Existing
observation-disabled transient consumers and prior controls also pass. Pure
ascending evidence remains unwired; this is not live ascending-movement proof
or M1 readiness.

Live ascending-floor admission is now wired through the existing transient
candidate, pre-carry clearance, changed-endpoint requery, and rollback flow.
Positive moves permit a known WORLD or same-frame pre-point, require same-frame
post-own tracking, and attest the first selected MTV and native contact flag.
Negative transient eligibility and legacy branch priorities remain unchanged.
Production review passes; the focused four-class JVM batch passes 38 tests.
The ordered-row regression also passes through the live matcher, confirming a
malformed first row cannot be rescued by a later valid row; GameTest sources
compile after the fixture corrections.

Three native controls cover side-first admission, floor-first admission, and a
valid ascending proof rejected by obstructed final feet. Their initially invalid
layouts were corrected before submission. Corrected geometry and oracle review
passes; runtime ordering and clearance remain unverified. Fixtures explicitly
use current-frame geometry with observation disabled, not stock-interval replay.
At six substeps, the authored side/floor overlaps are approximately .06/.02;
the blocked endpoint's feet are approximately 1.004167 above the floor origin,
so its downward .02 foot band still intersects the floor. These are source-based
expectations, not native-run evidence. M1 remains unaccepted; connected
presentation and measured performance gaps are still open.

Diagnostic `run_0a3f8f4db573315260320f3f` failed; acknowledged and cleanup
confirmed. All 168 JVM tests and 28 prior native controls passed. The three new
ascending cases failed before their movement actions: one support-pad scene
was not clear/loaded and two frames failed bounded natural settling. All three
used the same world-space fixture origin, making concurrent scene contention
the leading explanation. Separate origins are being assigned without changing
relative collision geometry, proof guards, or settling tolerances. This run
does not establish either success or failure of live ascending admission.

The three ascending fixtures now use separate world origins at X=6000, 6200,
and 6400 (Y=300, Z=6000). Their 200-block spacing exceeds the scene footprints;
relative movement geometry and assertions are unchanged. GameTest compilation
and independent isolation review pass. Native setup and admission still require
a managed rerun.

Diagnostic `run_ebd30f55ac9b04ba96715611` failed; acknowledged, cleanup confirmed.
All 168 JVM tests and 29/31 native cases passed, including obstructed ascending
rejection and all prior controls. Both admission actions ran. Side-first had a
valid independent ascending proof and CLEAR feet, but the live coarse candidate
filter incorrectly required every row to be vertical. It must permit optional
side rows while retaining a required positive floor and the full proof gates.
Floor-first already reported accepted ascending position and airborne grounding
with exact bounded own Y=.12; its fixture incorrectly compared total accepted
Y=.142857143 (including geometry) against requested own Y. These two narrow
corrections and regression coverage are underway; side-first live admission
still needs a managed rerun.

The coarse ascending filter now permits side rows while requiring an
upward-dominant floor; null and incompatible vertical rows still reject. A new
live-filter regression covers both contact orders, floor-only, and rejection
controls. The native fixture now compares requested Y with bounded own Y, not
total displacement. Production and GameTest compilation plus the four-class
focused JVM batch pass **39 tests**. Native admission remains pending rerun.

Diagnostic `run_72c6764b17be5bba88eee385` passed **169 JVM tests and all 31/937
selected native cases at six substeps**; acknowledged, cleanup confirmed.
The live side-first and floor-first ascending admissions now pass with exact
bounded own Y, positive geometry-only correction, and CLEAR final feet. The
valid-proof/OBSTRUCTED-foot rejection and all retained controls also pass.
These explicit current-frame fixture results do not establish connected-client
presentation, owner-world coverage, measured performance, or full M1 readiness.

A separate side-first stock-interval ascending control now reuses the same
native fixture and proof oracles without rewriting frame pose/history. It uses
the live logical orientation for placement and request conversion, explicitly
selects STOCK_NATIVE_INTERVAL, and leaves observation disabled. GameTest
compilation and independent review pass; native ordering/admission are not yet
verified. This is server-owned movement coverage, not connected prediction.

Diagnostic `run_c16e415fc1a7040aa23056e3` passed **169 JVM tests and all 32/938
selected native cases at six substeps**; acknowledged, cleanup confirmed. The
new unmodified stock-interval side-first control passes native ordering, full
proof, clearance, and live ascending admission. Connected prediction/render
continuity and measured first-entry/idle CPU costs remain unverified; there is
no complete replacement OWNER_READY revision or owner acceptance.

Water eligibility clarification: resolved `Water → BaseDrink` inherits
`requiredSlipSpeed=3.5` at the SS14 pin and locally. Equality is admitted
(`speed < threshold` rejects); volume must be strictly greater than 15 units.
Continuous contact remains episode-latched after recovery until an observed
exit/re-entry. No passive evaporation consumer was established, so elapsed time
alone is not evidence of puddle shrinkage. These rules do not identify the cause
of the owner's particular #9 incident.

## Current cabin blockers — 2026-10-24

**M1 acceptance is pending. Owner retesting resumes only after the replacement
readiness gate passes.**
On 2026-10-25 the owner authorized resuming the minimal admin-only flight fixture
needed for reliable moving/turning checks. This excludes piloting gameplay,
automatic client launches and changes to the owner's world/configuration.
The bounded physical-impulse controller is implemented. Latest
`run_19802318b353bf9612a46542` passes fresh 24 JVM checks and all eight selected
native cases (8/921), including grounded continuous motion, lift/turn,
controlled passenger/cargo preservation and shutdown/expiry at six substeps.
Cleanup is confirmed. The replacement readiness gate is the next step; connected
owner verification and M1 acceptance remain pending. Initial native run
`run_82abaad6ae3b69cb6c95a415` passed 7/8, including controlled passenger/cargo
flight and shutdown/expiry. The command-motion fixture used a mutable starting
pose, invalidating its travel measurement; copied baselines and its expected
permission-refusal handling were corrected before the successful rerun; no
controller, gravity or collision rule was relaxed.
The fixture cargo grant
now supplies 20 water units (capacity-checked), enough to exceed the unchanged
15-unit puddle threshold; running above the unchanged slip-speed threshold is
still required. This is not a new retest request.
The manual procedures below are historical, not a current retest request.

Owner reports incorrect stationary-cabin throw origin/trajectory, denied loose-jug
pickup, water not slipping aboard, prediction trouble apparently after puddles,
and facing remaining world-fixed while the supported hull yaws. Preserve accepted
air slowdown, release/orientation rules, and successful airborne pig pulling;
do not resume movement-feel tuning.

The 02:01–02:11 session contains 23 client prediction stops and seven server
movement rejections, including failures before the jug grant. It does not record
enough hand/slip events to establish a puddle cause or a throw/pickup cause.
The first server rejection includes a verified ceiling-plus-wall correction:
negative roof MTV `-0.02202879558996` accounts for the excess downward displacement.
The new ceiling proof separates that geometric correction from bounded own motion
without granting floor support or increasing collision tolerances.

Current candidate also shares exact frame-aware puddle contact geometry between
slip/friction and carries supported client yaw before publishing world-yaw intent.
`run_2965a7bfe0466479260320cb` passed 152 JVM cases and ten selected native
cases at six substeps: canonical stationary-cabin throw/pickup, two water cases,
two ceiling cases, mixed-contact replay, and four exterior-wall controls. These
are not connected runtime verdicts. Camera/action-aim equivalence and remaining
prediction signatures are still unverified. The hand-input correction now captures
render-camera throw targets, resolves exact frame coordinates at server handling,
and validates nominated pickups without gaze. It preserves committed empty-hand
host transitions. `run_e414acb294350d98db5f9c42` passes fresh 340 JVM
checks and all seven selected native hand/query cases (7/918) at six substeps;
cleanup is confirmed. This is not connected-client verification. These diagnostic
runs do not themselves establish OWNER_READY or M1 acceptance; follow the
replacement readiness verdict before testing.

## Bounded connected-wall follow-up — 2026-10-23

Owner reports recording is mostly fine, but intermittent outside-armed-window
teleports to previous locations remain. Latest `logs/latest.log` entry at
19:27:02.746 stopped replay at seq 3097 / ack 3096 on combined steel shuttle
wall (normal `(-1,0,0)`) plus ordinary world floor. Request was
`(.23849,-.44483,-.14908)`; measured movement `(-2.4e-6,-.121297,-.149077)`,
horizontal certificate null, end ground Y `-60`. Prediction failure clears
unacknowledged history and stops until snapshot, consistent with snapback; the
exact visible authority correction was not logged and no matching server
`WORLD_STEP_REJECTED` was found. Cause of the visible teleport is therefore not
fully established.

The verified root bug was server-only pre-move AABB capture in
`SableOwnedBodyMotion`, preventing client `existingWorldClipObservation.matchesCall`
from admitting the existing world-floor plus side-proof combination. Fix captures
bounds on both sides and reuses the existing bounded exact-call/unchanged-XZ
side-geometry proof; it does not change global tolerances, strict bounds,
friction, air slowdown, or camera behavior. Focused evidence
`run9c6aefe3375dd96a5997ad62`: JUnit 33 pass (scope 11, side-proof 18,
prediction-failure 4), native mixed-contact 1/912 and server-wall 1/912 in
separate fresh worlds, six-substep cleanup confirmed. Native headless server
adapter is not live-client certification. Connected retest remains required;
no OWNER_READY/M1 acceptance.

Manual next check: restart with updated client, stand still on a spawned
stationary shuttle, and jump against an outside wall for 20–30 seconds without
nudge or yaw; note the clock time on any snap. Optional existing 100-tick
diagnostic arm may miss sporadic events; replay-stop errors remain logged.

## Connected M1 feedback — 2026-10-23

The owner authorizes corrections following the readiness PASS
`run_bf5bd154dfb7ae6d6d1d709f`; M1 is **not accepted**.

- Jumping against a newly spawned shuttle wall stutters and appears to clip
  against hull blocks. Apparent improvement after repeated jumps was not a fix.
- Aboard throws appear to omit passenger/carrier velocity; ordinary jug placement
  while moving no longer reliably places the jug on the shuttle.
- Jug spills do not remain aboard. Selected 3D policy: puddles remain tile-bound
  while supported, including on a tilted original floor. This does not confine
  liquid to one grid: open-edge overflow must resolve a world-space receiving
  surface, including an offset/rotated other grid, and conserve contents.
- A translating hull pushes the controlled body, with jitter, but does not appear
  to push loose items. At `yaw 20`, exterior contacts drag items/mobs around the
  hull. Distinguish supported carry from impact momentum; a strike should permit
  separation, while repeated contact remains geometry-dependent.
- Positive controls to preserve: pulling a pig aboard an airborne ship works.
  Released-object orientation follows the source grid at release; objects outside
  retain their world pose independently of subsequent ship rotation. An incoming
  object's initial world orientation is retained, then supported objects follow
  later ship rotation. Do not replace this with universal orientation snapping.

The previous grounded `nudge`/`yaw` owner procedure is not reliable proof of
continuous movement: these add velocity once and do not overcome ongoing ground
friction. Do not prescribe repeated nudges as a sustained-motion controller.
No further movement-feel tuning is requested; contact/ownership defects remain
in scope. Automated client launches and changes to owner worlds/configuration
remain prohibited.

### Bounded automated follow-up (not connected acceptance)

- `run_0f673f053e97e798106824cf`: 5/7 selected cases passed. All four jug
  impact cases and offset/yawed cross-grid puddle overflow passed. Exterior
  impact cases did not yet establish their intended contact premises.
- `run_1bde91f03949d33667e628e5`: separate fresh-world sustained wall-jump
  and carrier-release checks each passed. The latter verified exact initial
  own + rotational/linear carrier-point + release velocity, then destination
  frame correction once. The server wall trajectory does not resolve the
  reported connected clipping/jitter.
- `run_7979f0e2051dbe7ea295deb5`: both exterior cases captured actual solver
  side contacts and failed outgoing-momentum assertions. Translation corrected
  position by 0.112020169 m while outgoing velocity remained zero; rotating
  contact likewise produced zero outgoing velocity. This establishes missing
  momentum response, not a reason to relax the fixtures to positional pushing.

These are diagnostic results, not a replacement readiness PASS or M1 acceptance.

## Movement status — 2026-10-23

Owner reports movement is mostly good and natural air slowdown is present, and
directs that feel tuning stop. This is an accepted movement-feel checkpoint,
not acceptance of the shuttle sprint or M1. Separate observation: falling off
the station appears to show ground contact, then harm roughly a moment later.
Cause is **unverified**; investigate separately without movement-feel changes.

## Observed failure

### Latest connected retest — feedback 05, 2026-10-12

**M1 remains rejected.** The owner sees no change in wall sliding across the last
two check-ins. Passing headless exterior cases are not a reproduction or a fix.
The next investigation must use actual connected input/pose/collision evidence,
not another unchanged green fixture as acceptance.

Positive connected results: an actively controlled character stays aboard at a
20-speed nudge, and the jug retains its cabin-local spot during motion. These
observations improve active carry/presentation evidence, not all passenger states.

New failures and questions:
- Logout/rejoin while the ship is falling places the character through the room
  roof into the maintenance area. Determine actual disconnected-body motion and
  Ready-time pose continuity before assuming a saved-coordinate rebase fault.
- Entering Creative leaves the living character floating at its old world position
  while the hull moves. Physical collision/carry must not depend on controlling
  input, connection, or the current possessor; returning control must attach to the
  same physically continued body, not teleport it back onto the craft.
- A moving airlock closes on the character. Verify pinned SS14's default safety,
  occupied/closure/reopen/crush consumers, then audit local frame-space occupancy.
  Do not assume either universal closure refusal or deliberate crushing.
- Maintenance shows SMES/substation/APC without apparent connections, an air alarm
  but no visible vent/scrubber, and a lone apparently unconnected pipe. Audit actual
  authored lane/device/power records and presentation separately; do not call a
  finite-gas token proof a functional ventilation installation.

The old moving reconnect fixture's post-Ready neutral-intent pump is normal client
protocol, but it cannot prove disconnected/uncontrolled physical motion. Required
regressions include falling-cabin OFFLINE and Creative/observer intervals with no
controlling intents, then same-body/Mind/items return and roof/deck continuity.
No further client launches are authorized. Runtime artifact/log identity for this
retest is being checked read-only; do not attribute the owner's report to a wrong
build without evidence.

Source audit for this retest:
- `GroundedHarnessWorldStep` is the shared living-body movement kernel. Normal
  lifecycle control previously invoked it only for a fresh intent; detached and
  offline bodies had no corresponding owner while vanilla travel was suppressed.
  The new post-player server-tick fallback consumes that same kernel, not a second motor.
  Its physical-attempt marker is body-local, keyed by exact level and **server
  tick**, and is consumed before the native call even if the call later rejects.
  Snapshot publication follows fallback physics, including routinely held owners.
  These changes require validation;
  they do not yet establish Creative/offline/falling acceptance.
- The pinned SS14 `Airlock -> BaseStructure` audit was verified at
  `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Default safety checks occupancy both
  at close admission and at partial close: `SharedDoorSystem.CanClose:429-447` and
  `OnPartialClose:468-491`, plus `SharedAirlockSystem:38-55`. A safe entrant during
  closing causes reopening **before** the door becomes solid, without crushing.
  Disabled safety permits the configured crush path (15 Blunt, 2.4 s paralysis,
  2 s reopen delay). There is no shuttle-speed exception. The earlier research
  summary claiming a default-safe partial-close crush was incorrect.
- Local safety, partial-close and crush queries previously used the stored block
  box. `AirlockDoorwayActors` now projects the potential closed volume using a
  copied current frame pose, existing eight-corner bounds, and SDK OBB narrowphase
  with private scratch. Safe unknown geometry refuses closing; no victims are
  invented. Live open-state collision geometry is deliberately not the potential
  closed volume.
- Power is actually authored: HV source `(4,4,3)`, substation `(4,4,5)`, APC
  `(4,4,7)` and concealed cable hosts/ceiling extensions. Appearance alone does not
  prove disconnection. No vent or scrubber is authored, however; alarm `(5,4,11)`
  and pipe `(6,1,8)` are not a ventilation installation. The pipe holds only 2 mol
  of finite gas. Existing ventilation prototype/placement/ownership/link services
  are the reuse targets; this audit does not establish sustained cabin ventilation.

Validation checkpoint: foundation-1 compiled, but full JUnit had **3 failures of
3,221 tests**, all in the new doorway geometry cases. Filtered server validation
selected **5 tests; 0 passed, 5 failed**. Failures included an open-door test using
empty live collision geometry, overlapping authored scene origins, and strong
impulse regressions. Its logs/world are retained. Corrections and additional
Creative/falling fixtures are pending new validation; do not replace these failures
with the previous full-31 pass. Wall yaw samples are observational only, independently
source-reviewed; no connected wall root cause or fix has been established.

Foundation-2 compiled: **3,220/3,221 JUnit passed**; the remaining rotated-door
case had chosen a point inside the rotated volume rather than a broadphase-only
corner. Filtered server validation selected **6/863; 4 passed, 2 failed**. The
strong X/Y carry cases, moving reconnect and occupied moving-door close refusal
passed. New passive/Creative cases failed their premises: callback marker clock
and actual player operator permission respectively. These were corrected without
bypassing body physics or the lifecycle command.

Foundation-3 completed **7 selected/863; 6 passed, 1 failed** (falling reconnect).
Its guard recorded a real physical step on the exact frame, with room-floor Y
`origin + .999` below the fixture's literal `+1` check. Room containment now uses
the existing strong-cabin `+.8..+3` range, with fresh SDK below-floor contact
witnessed separately. Shutdown exceeded the command timeout; a later process
audit found no corresponding fixture Java/Gradle process. Its world is retained.

**Phase correction before acceptance:** dependency bytecode proves level tick/post
precedes connection/player ticking; the initial player-list loop merely suspends
flushing. The earlier level-post fallback could consume active input's physical
budget. Fallback now runs at `ServerTickEvent.Post` HIGHEST after actual player
input. Routine snapshots, including held owners, follow it in the same owner.
SDK container ticking occurs at `ServerLevel.tick` HEAD. Evidence:
`build/shuttle-feedback-05-server-tick-bytecode.txt:2403-2530,5113-5118` and
`build/shuttle-feedback-05-sable-level-tick.txt:400-458`. Fixtures sample completed
physics through a bounded LOWEST-priority observer, not an assumed GameTest clock
offset. Native travel's tether hook shares its physical-owner predicate, preventing
a second impulse for detached/corpse movers.

Foundation-4: **3,221/3,221 JUnit passed**; **8 selected/864, 4 passed, 4 failed**.
Nonzero input/real acknowledgements, strong X/Y carry and moving-door safety passed.
Passive, Creative and both reconnect cases failed. Fixtures now await actual fresh
onboard native-floor contact before pulsing, count 30 future physical ticks rather
than 30 inclusive samples spanning 29 ticks, and read Ready poses after physics.
Creative return expects the inert Spectator carrier, not vanilla Survival. The
moving reconnect waits a physical offline interval before its displacement check.
Observer failures now log their exact guard without aborting the event bus.
Foundation-5: **3,221/3,221 JUnit passed; 7/8 selected passed**. Only the older
moving-reconnect proof failed: its 4 m/s pulse had physically stopped before the
post-Ready movement window. The fixture now uses one 20 m/s pulse and the existing
64-block support/ticking corridor, retaining all offline and post-Ready checks.

Foundation-6: **3,221/3,221 JUnit and 8/8 selected of 864 passed**, with successful
shutdown. This covers real active input/acknowledgement, 30 input-free ticks,
Creative plus normal return, 30 falling OFFLINE ticks plus normal rejoin, moving
reconnect, strong X/Y carry and occupied moving-door refusal. No connected wall
fix or camera acceptance follows from those results.

Full-7 completed **856/864; 8 failed**. The new ordinary-NPC fallback was redundant
with existing control/native owners and was removed; fallback is now restricted
to bound custom characters. An externally advanced body consumes its marker before
settled flight tracking is cancelled. Legacy fixture phase/setup repairs preserve
the same assertions: one physical step per tick, real input for owned bump contact,
supported current-pose snapshot recovery, stock carrier clipping against a wall
placed before the pulse, and same-callback yaw pollution/control on a fresh tick.
The cases are indexed by `shuttle-owner-regression`; the class-level recovery tag
also selects two existing recovery checks. Regression-8 selected **18/864; 15
passed, 3 failed**. Remaining guards were the leased-animation initial baseline,
known-pose snapshot recovery, and canonical inside hatch input. Repairs establish
the baseline through a real zero-wish step, validate a resumed snapshot at its
published current pose over supported geometry, and use forward input while
looking toward the hatch (the prior wish walked backward). The hatch observer now
latches exact frame/root native contact and completes after physical simulation.
Regression-10 passed **16/18**, retaining two failures. Its unknown-pose case
exposed a real producer regression: the publisher's safe-pose prefilter skipped
the existing producer's hold/rebase transition. That redundant filter was removed;
unknown poses now reach the sole producer and enter hold without emitting packets.
Animation decay now counts actual native steps, not a duplicate budget-skipped call.

Regression-12 passed **18/18**, with **3,221/3,221 JUnit**. Full-13 completed
**861/864; 3 failed** (human/pig profiles, linked powered button, same-frame pry
cancellation). The profile fixture had a real phase-loop bug and its target tag
was missing: five walk ticks reset the counter and restarted walking indefinitely,
never reaching sprint. It now runs five walk then eight sprint ticks. Button
readiness checks fresh delivered prototype watts, not cached availability. Pry
observations latch all physical/power/operation data at one completed server-post
phase; the `.3` motion threshold remains unchanged. Regression-14 and full-15 if
green are pending. These three target methods now have explicit regression tags.
No new packaged build or owner retest is claimed.

Regression-14 selected **21/864; 19 completed/passed, 2 unstarted**, with no
aggregate before timeout. The pry proof met the unchanged `.3` threshold with
known-zero power. Its POST-phase test completion stalled native batch progression:
`GameTestInfo.tick` skips already-done tests, and only completion inside its own
tick notifies listeners. Physical observations remain server-post; terminal test
success/failure now dispatches through the existing native GameTest delay helper.
Evidence: `build/shuttle-feedback-05-test-info-bytecode.txt:174-225`; helper
`fail(String)` only throws, per `...test-helper-bytecode.txt:2433-2439`.

Further source review found Critical, still-alive controlled bodies could step
twice: medical `OwnedCorpsePhysics` did not consume the shared marker before the
later active grounded step. It now guards/marks the same physical budget. A new
typed-Critical fixture checks duplicate suppression and next-tick gravity under
loaded entity ticks. Regression-16 and full-17 if green are pending; no result is
inferred from the incomplete run. No corresponding fixture process remained after
the timeout audit; other worktrees' processes were untouched.

Regression-18 passed **22/22 selected of 865**, with **3,221/3,221 JUnit**. Full-19
completed **864/865**; only `poweredAlarmQueuedSyncReceivesVentState` failed. Its
observation showed ready endpoints later, but did not prove vent readiness when
the one queued Sync was issued. The fixture now waits for actual alarm/vent power
and connected live endpoints before requesting, retaining queued disable/reply/
re-enable assertions. The Sable removed-body shutdown issue remains separate from
test aggregates (regression-18 exited successfully despite logging it).

The cabin installation now has authored vent/scrubber placement, finite separate
Primary supply / Secondary return and explicit alarm links in source; runtime
validation is pending. See `CABIN-VENTILATION.md`. A second moving-door safety test
is being added for an entrant after closing starts but before solid partial closure.
Neither source change establishes owner-connected acceptance or fixes wall sliding.

Integration-20: **3,221/3,221 JUnit; 26 selected/867, 17 passed, 9 failed**. Seven
canonical builds failed concealed supply admission before body/gas assertions.
The exact failing node/stage was not logged, so no eligibility or gas-owner policy
change is justified yet. Bounded setup diagnostics are being added. The late-entry
door actor left the moving volume: its fixture subclass suppresses automatic travel,
but the new test had armed it only once. Its real travel is now scheduled once per
server tick during the probe. Alarm readiness incorrectly required gas-work metering
for an unplumbed monitor endpoint; native Sync does not require that meter. Readiness
now follows the existing native endpoint contract plus fresh 100 W alarm power.
Full-21 was **not run**. Failed worlds/logs are retained; no JAR/retest is requested.

Integration-22: **3,221/3,221 JUnit; 26/867 selected, 18 passed, 8 failed**. Late
moving-door safety now passed. Seven builds failed at `(5,3,3)`, `owner_create`:
new valid sealed records met active gas-owner UUIDs left at recycled plot storage.
Steel eligibility, full collision, enclosure and current host generations were
valid. Teardown now reuses existing proven-AIR retirement, preserving nonempty
species/heat in inactive UUID archives before storage reuse; a new full-cabin
archive/rebuild case covers separate lanes and unrelated-owner preservation.

Queued alarm Sync still failed. A proposed raw-address explanation was disproved
for this runtime's retained AddressStore, and that production edit was reverted.
Recipient power is checked at reply delivery, not just readiness. The fixture now
has a dedicated batch and bounded power/topology history; interference remains a
hypothesis pending evidence, not a claimed protocol fault. Integration-24/full-25
if green are pending. Full-23 was not run. Clean foundation-6 and regression-12
disposable worlds were removed; their logs remain, as do failed/save-error worlds.

Integration-24: **3,221/3,221 JUnit; 28 selected/868, 26 passed, 2 failed**.
All earlier cabin-admission failures and native queued alarm Sync passed. The live
gas case lost an active upper Primary owner during observation; bounded diagnostics
now distinguish unloaded storage, block/BE change, dormant/archive state and UUID
mismatch, with exact current-frame context. No runtime cause or workaround is
claimed yet. The lifecycle regression incorrectly required the SDK allocator to
immediately reuse the just-freed slot; no such contract is established. Its renamed
case retains exact old-owner retirement/archive, unrelated-owner and fresh-build
assertions, recording natural reuse when it occurs. Narrow ventilation-26, then
integration-27 and full-28 only if green, are pending. Full-25 was not run.

The subsequent narrow runs isolated fixture proof issues rather than establishing
a production gas defect. Removed-frame storage in ventilation-26 retained matching
active gas UUIDs; the scene now covers the pulse's travel window with ordinary air
clearance and strict whole-hull/ticking checks. Ventilation-38 exposed reversed
gain/loss measurement signs. Ventilation-39 then observed three paid, substantial
positive-X transfer intervals, but its redundant completion predicate used a
whole-cabin rounding maximum as a minimum flow threshold. Flow is now certified by
the unchanged quantitative per-interval predicates; conservation/UUID checks run
on every sample and at the full deadline. Ventilation-42/integration-43/full-44
only if green are pending. No timing workaround, gas reseed, physics flag or
collision-tolerance change was made. Connected wall sliding remains uncorrected.

Completed gates: **3,221/3,221 JUnit; ventilation-42 4/4 selected of 868;
integration-43 28/28 selected of 868; full-44 864/868**. Full-44 failed WEST,
SOUTH and NORTH exterior-wall movement plus strong-impulse settled cargo. This is
not a green full suite or evidence of an unrelated baseline. Cargo's tick-57 root
was past the existing support pad; its new ordinary support corridor is pending
validation. Ordered wall traces must be correlated with actual selected collision
planes; a differing last SDK-yaw callback can be foreign candidate data and is not
itself a cause. New lifecycle fixture cleanup now guards initial foreign-owner
admission and removes only its exact test owner through normal hooks; pending
runtime validation. No JAR was built, no private retest is requested, and no
connected/visual or broader M1 acceptance is inferred from the focused passes.

Remainder-45: **3,221/3,221 JUnit; 9 selected/868, 6 passed, 3 failed**.
The supported strong-cargo corridor passed without changing `.02` residual limits
or native carry. All but the newly tightened lifecycle setup passed ventilation;
that lifecycle case required a loaded foreign cell without first acquiring its
fixture ticket. It now waits for real entity-ticking admission while retaining
loaded/AIR/no-owner checks. WEST/SOUTH wall cases still failed; EAST/NORTH passed
in this isolated selection, which does not erase full-44's NORTH failure.

Complete wall traces identify the rejection boundary: WEST/NORTH native clips
slightly beyond the requested normal component, while SOUTH adds an outward
corner correction on a different plane. The single-first-plane certificate cannot
authenticate these responses. Its `1e-6` bound remains unchanged. Exact native
actor OBB, chosen voxel bounds and selection-pose snapshots are being added as
fixture-opt-in diagnostics, not as movement authority. No correction is justified
merely by a supplied MTV or a last-yaw callback.

Geometry-46 completed **3,221/3,221 JUnit; filtered 9/868, 6 passed / 3 failed**.
Ventilation/lifecycle **4/4**, strong cargo **1/1**, EAST **1/1** passed;
WEST/NORTH/SOUTH still rejected. Lifecycle now has real foreign-cell ticket
readiness and its normal identity-checked cleanup passed. Every failed wall
snapshot reports geometry observation/completeness enabled; the shared immutable
value captures the actual actor OBB, selected voxel storage bounds and exact
selection transform. Observation is enabled only by the exterior fixture factory.
Default runtime observation remains disabled; no movement correction is admitted.

Rejection detail: WEST/SOUTH return no horizontal certificate at the earlier
native/final **Y** comparison, before projection validation. The first-plane
overshoot/corner algebra remains an additional counterfactual rejection. NORTH
passes native/final matching and reaches the projection bound. Recorded native Y
versus final floor-level Y suggests another collision stage; literal stage order
and independent selected-box overlap still need verification. The extracted
geometry value also needs its missing finite-quaternion guard restored; current
finite captures do not justify trusting invalid future diagnostic data.
Evidence: `build/shuttle-feedback-05-geometry-46.log` and its derived
`build/shuttle-feedback-05-geometry-46-wall-traces.txt`. Gradle exited 1/native
task 3 for required test failures; no removed-body save exception was found here.

The side correction now has a live shared bridge consumer, not just diagnostic
JSON. It witnesses the exact captured boxes with SDK SAT, verifies complete native
input/response budgets, and separates bounded own XZ from position-only excess.
Side contacts are not passenger support identity. Supported-floor semantics,
generic movement bounds and first-plane certificate limits remain unchanged.
Finite snapshot guard and aggregate-vector findings are source-corrected.
Serial side-50 passed **84/84 filtered unit tests** across eight classes, with
source/native-fixture compilation and independent source review passing. Fresh
central native/full validation is still required; this is not a connected fix or
M1 acceptance. `WALL-RESPONSE.md` and `GATE-PLAN.json` index the exact proof/gates.

SDK normal-source audit: `FirstCollisionInfo.globalDirection` is a normalized
copy of its supplied MTV, not a cardinal-face conversion. See
`build/shuttle-feedback-05-collision-kernel-bytecode.txt:1682-1710`. It records the
first contact for that frame, not every later selected plane. Rotated walls and
in-flight parenting changes remain hypotheses requiring actual trace evidence.

### Latest connected retest — feedback 04, 2026-10-12

Evidence is retained in `build/shuttle-owner-feedback-04`: mixed client/server
logs, lifecycle snapshots, server-list snapshots and process/listener observations.
The logs contain **127 server grounded-world-step rejections**; they are not
client-only. Body `09965e19-fd8f-47b7-af55-d823ad1e68ea` and native frame
`b0e0bcc5-401a-42d8-b425-3ef17186fc99` identify this session.

The owner reports unchanged exterior behavior: fast airborne sliding on two faces,
head bumps on the other two. A `-20` X velocity pulse largely carries the character
but produces a later brief backward jut. A `+20` Y pulse initially carries the
character, then the character falls through the floor. At these speeds the hull can
also pass through the character. Interior bump-opening is inconsistent during
motion and after settling. An interior loose jug trails the moving hull, updates
occasionally and reaches its intended position after the hull stops. Stationary
logout/rejoin inside now succeeds; moving-cabin reconnect is still unverified.

The first logged wall rejection has requested `(0.219956,-0.001,0.175274)` but
own response `(0.065987,0,0.196703)` against first-recorded normal `(-1,0,0)`;
the extra tangent displacement is material, not physical micro-tilt. Other records
show vertical overruns and a small downward probe yielding about `+0.2499` Y.
During upward flight, a downward own request `-0.468425` yields `-0.664083`,
separately from native carry `+0.374058`. These establish server failures without
identifying every SDK substep or the initiating nudge tick. No tolerance increase,
forced grounding or arbitrary nudge cap is justified.

At the 04:32:13 underside record, world Y changes approximately `-0.290025`;
subtracting the already measured own displacement yields actual applied carrier Y
`+0.374058`, matching the raw SDK carrier to rounding. Ordinary-world clipping of
the carrier does not explain that specific record. It is not proof about earlier
unlogged intervals; no incapacitation/health transition is established by these logs.

The SDK first-contact map retains the first plane per frame, whereas subsequent
selected collisions have their own current normal/classification. The airlock
capture mistakenly used the first-map classification to admit later selections;
that pure capture fix is under validation. Pinned SS14 `SharedDoorSystem.cs`
handles bump-opening from `StartCollideEvent`, not a required final post-solver
overlap. Any revised witness must still prove a real selected door collision,
world/frame occlusion ordering and live controller identity; nominees/proximity
alone cannot open a door.

Source-supported cargo presentation mismatch: Sable's nonliving packet path
turns plot-local coordinates into a world snapshot without retaining plot cache,
while the custom loose-item client tick skips vanilla physics/carry. Local cargo
coordinates can stay constant while the ship moves, so packet interval one alone
does not supply continuous hull-relative presentation. The fix retains exact raw
packet frame/local point and reuses current-pose loaded projection each client
tick. Unknown-storage packets pause, unloaded frames freeze, world packets clear
the anchor; no client physics or native tracking is introduced. The SDK renderer's
world-to-local-to-render-pose adjustment is not a second logical-pose projection.
Independent source review passes; actual client transformation and connected
visual acceptance remain pending. This does not prove server cargo continuity:
the first strong cargo fixture retains tracking but fails its local-distance
assertion, now with first-failure drift/pose diagnostics.

The duplicate Multiplayer rows are a separate, unconfirmed UI/config observation.
The captured project server list has one saved entry, not two; no settings were
edited. This does not explain or dismiss the owner's observed duplicate rows.

### Previous connected retest — feedback 03, 2026-10-12, build 12

Owner logs are preserved at `build/shuttle-owner-feedback-03/{latest,debug}.log`
(copied before further launches). Interior walking is comfortable; touching the
powered hatch opens it. Examination works outside and through the window/open
hatch. Creative item placement followed by return to the same character works
inside. Translational nudges on all three axes preserve standing character and
interior jug placement; repeated nudges increase speed. A moving hull pushes the
Human, but does not push an exterior loose jug. These are owner observations,
not complete M1 or rotation/network acceptance.

Remaining blockers: side-dependent fast airborne exterior-wall gliding/head
contact, frequent grounded-world-step rejection warnings, occasional brief upward
ceiling clipping, and reconnect disconnect after leaving inside the cabin. Logs
show movement recovery retaining ownership before logout, then generic reconnect
refusals; the exact failing admission predicate was not logged. Do not infer that
the body is corrupt. Startup lag settled during play. A moving airlock edge shows
a world-pattern visual artifact (separate renderer issue, not yet diagnosed).

The owner rejects unsafe-character admission as a reason to lock players out or
require an administrator. Preserve exact character ownership while separating
safe server admission from successful body attachment; do not grant ambiguous
body authority or spawn a replacement silently. Short writable diagnostic ship
IDs and removal of the arbitrary 2 m/s nudge limit are requested.

Targeted exterior-cargo inspection finds a concrete missing observation:
`LooseItemEntity.tick` returns for settled **untracked** items until the next
10-tick support check, and still returns without `Entity.move` when ordinary
terrain support remains. Tracked settled cargo instead makes a native support
probe every tick. This explains why existing aboard-cargo carry proofs do not
cover a hull approaching a ground-settled exterior jug. A release-pinned SDK
proactive-wake audit and actual moving-hull/jug fixture are still required before
choosing a bounded wake adaptation; no cargo fix is yet claimed.

Reconnect source/runtime diagnosis: `LifecycleExistingBodyReconnect.inertPolicy`
previously required `setGameMode(SPECTATOR)` to return true even when the carrier
was already Spectator. Vanilla returns false for that no-change operation. The
waiting same-body native retry reached this exact admission failure after valid
body proof. The idempotent fix checks actual Spectator mode plus the unchanged
inventory gate; it does not loosen body identity proof. This is a concrete
reconnect defect, but the historical owner login lacked phase diagnostics, so it
is not a proven explanation of both owner refusals. Safe connected waiting now
has a standalone non-operator `/ms14_return` command (the shared `/ms14` root may
require operator permission).

The four exterior regression cases initially failed their own setup/measurement:
settled hull Y was used to place terrain feet, then a world-space height was
compared with the storage-space roof. Retained native tick traces establish real
jumps and fresh airborne side contacts on all faces, not reproduction of the
owner's fast glide. Correcting fixture coordinates is not a gameplay wall fix.

With the full canonical furnished cabin, the latest four-face run finally
reproduces a west-wall rejection at the jump tick: requested X `+0.110485`, Y
`+0.42`, accepted own X `-2.157e-6`; selected fresh native side-plane normal is
approximately `(-1, -3.781e-6, -1.633e-6)`. The existing certificate discarded
the real Y tilt using a numerical-residue threshold. Upward movement against
this physical tilted plane explains an X correction that a flattened normal
cannot explain. The validated repair retains the full captured normal
and unchanged `1e-6` residual bound; it grants horizontal response evidence only,
not vertical authority. This establishes one concrete furnished-wall rejection,
not the cause of every owner glide, head bump, or ceiling clip.

Evidence before that wall repair: `build/shuttle-feedback-03-native-5.log`,
**14/854 selected, 13 passed, 1 failed**. All **8 reconnect** and **2 command**
fixtures pass; **3/4 furnished exterior faces** pass. Same-body waiting retry
completes the ordinary Begin/Ready handshake with the original Mind/body/hand
roots and a new generation. These are filtered checks, not full certification.

After repair, `build/shuttle-feedback-03-native-6.log` passes **14/854 selected,
14 passed, 0 failed**, including all four furnished exterior faces. Build/assembly
and **3,196 JUnit tests** pass. The full native run completes **854 cases, 845 passed,
9 failed** (`build/shuttle-feedback-03-full.log`); native airlock contact/bump,
occlusion, rotation, recontact/memo and seam prerequisites remain unresolved.
Full-suite certification and connected wall/reconnect acceptance are still open.

### Earlier connected retest — stationary cabin rejected

The owner can now collide with the cabin without the earlier disconnect and
examine its walls. However, wall outlines have incorrect placement/size; jumping
against vertical walls produces false support/head contacts; airlock bump-opening
fails although looked-at E interaction succeeds. Entering the **stationary** cabin
causes rapid (roughly 5–10 Hz) alternation between the entry anchor and attempted
movement. Jumping changes the anchor. These observations reject standing/walking
continuity even before any ship motion; the previous headless pass is not connected
acceptance. Retest logs are preserved at
`build/shuttle-owner-feedback-02/{latest,debug}.log` from `run/logs` (18:15 launch).

The logs establish repeated server movement rejection/rebase and client prediction
stops. Native displacement sometimes exceeds the request substantially: a downward
0.001-block probe produces roughly +0.500 Y, and another produces -0.5625 Z with
no requested horizontal movement. Smaller tangential overruns also recur. These
are not all numerical residue. Exact contact geometry and the reported entry-anchor
snap target remain unproven; do not simply widen the motor's tolerance.

Two source-level defects are established independently: framed outlines submit
roughly 20-million-block storage vertices to float rendering before pivot cancellation;
client replay suppresses outer inherited motion but still invokes native collision's
last-to-logical pose interpolation. Fixes normalize outline vertices before float
conversion and scope already-consumed prediction moves to current collision geometry.
Neither source finding alone certifies the connected movement fix. The first native
wall probe had zero rejected moves in 120 steps and did not reproduce this retest.

The pinned SDK also has a separate **remote-entity** cached plot-position writer:
`common/src/main/java/dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/EntityMixin.java`
reprojects `sable$plotPosition` at `Entity.tick` return, and its null-cache client
branch clears tracking for non-local-player Mobs. The controlled Human is a Mob,
not Minecraft's local Player. Existing relative tracker cancellation runs before
SDK packet interpolation, but teleport handling can seed this cache. This is a
concrete competing position writer and a strong anchor-snap candidate; runtime
packet/cache correlation remains unverified. The similarly named collision-package
`EntityMixin` does not own this cache. Do not infer absence from that other file or
clear the cache alone while leaving the null-cache tracking-clear branch active.

The owned-client shim now has a narrow post-merge guard for the SDK handler identified
by its `@MixinMerged` source-mixin metadata and `$sable$updateSubLevelPosition` suffix.
It discards the stale cache and returns before either the cached `setPos` projection
or null-branch tracking clear, hides the cache through its getter, and suppresses
the SDK living lerp setup only for the committed camera-owned CHARACTER Human.
Stock `applyLerp` still reaches its null-cache branch to clear both custom lerp
counters before returning without transform writes. Sable's tracked-body renderer
projects current body coordinates independently of that cache; no second render
projection is added.
This is separate from the current-collision-pose fix above. Source evidence makes
the cache writer a real snap candidate, not a confirmed explanation for each
observed anchor transition; only a connected retest can correlate cache packets
with the remaining snap target.

The previous 9/833 focused run's lone native failure was a fixture authority setup
bug: it advanced the native frame, updated `ClientFramePredictionState`, but left
the Human at its old world position instead of projecting the saved storage point
through the current double-precision pose. The corrected fixture projects and
places that authority point once, then checks three actual native floor-contact
replay moves. It is not a gameplay guard change. The older 120-tick stationary
baseline observed no rejection and is not evidence of reproducing the connected fault.

The canonical exterior-hatch fixture reproduced a separate bump defect:
`build/shuttle-feedback-02-exterior-native.log` selected one fixture, which failed
with a fully paid closed hatch and eligible Human at exterior ground height.
Final body-versus-door SAT contact was present, but the selected native blocker
was `steel_wall_block` at the raised floor edge (normal approximately -Z), so the
airlock-only contact list was empty. The adjacent hull-only control did not open
the hatch. Coincident door nomination must therefore start from the real native
blocking plane, not only a dominant door MTV; final shape/root checks remain required.

Build attempts that stop before `:test` runs are not JUnit results. In particular,
the retained 3,178-test XML was stale for later compile/bootstrap failures. The
common bootstrap failure traced to a plain move-access interface under the
Mixin-owned package; that interface now belongs in `ms14/doors`. Client hooks
still require connected acceptance. The isolated client smoke exported actual
transformed `Entity` and `LivingEntity` classes; independent bytecode review
confirmed the single SDK-handler ownership guard, guarded cache getter, setup
cancellation and stock null-cache custom-counter cleanup. The run timed out during
resource/shader loading, so menu readiness and client native-collision class
transformation remain unverified. Evidence: `build/shuttle-feedback-02-client-smoke-2/`
and `build/shuttle-feedback-02-client-smoke-2.log`.

The 108-tick cabin probe now reproduces an owner-like rejection while sliding
airborne along steel: requested X `-0.0168216`, native X `+1.166e-6`, contact
normal approximately `(1, 6.58e-8, 9.13e-7)`. Its displacement exceeds the exact
static-plane clipping projection by about `1.352e-6`; the unchanged `1e-6`
allowance must still reject that unexplained remainder. A typed horizontal
projection path preserves legitimate full/partial oblique clipping and tangent
velocity but does not authorize this sample or large idle-axis corrections.

Two additional SDK boundaries are verified in `SubLevelEntityCollision.collide`:
body hitbox yaw reads the per-level reused `sink.subLevelPose` before initializing
it for the current collision (lines 143–145, 175, 200–208), and the solver discards
raw inherited movement below `1e-4` after using the temporary projected body for
geometry (442–447, 454). Character-only scoped adaptations read the in-flight
native tracking frame for yaw and retain real changed-interval micro-carry once.
Default stock/corpse/cargo and consumed-current-geometry paths remain excluded.
Dual-frame and micro-carry regression sources are ready; runtime proof and whether
these explain the wall rejection remain pending. No wider slop or estimated carry
is introduced.

Priority: trace native tracking/position ownership and snapshot/replay anchor
updates first; investigate contact/support and existing bump-opening dispatch;
correct the actual outline vertex transform. Reuse existing native movement,
collision, door and examination services. Do not mask this with recurring
teleports, reassembly, relaxed displacement guards or proximity opening.

### Earlier contact disconnect

The first connected translation attempt reached normal Human movement and cabin
construction, but examination failed and touching the hull disconnected the owner.
Preserved logs: `build/shuttle-owner-feedback-01/{latest,debug}.log`.

The rejected step requested Z `-0.2572313738174518` but native collision resolution
accepted Z `+2.1101951119817386e-7`. The pure motor's direction bound rejected that
residue. `WORLD_STEP_REJECTED` then took a terminal lifecycle path. The logs show a
disconnect, not a JVM crash. An earlier healthy first-join handshake also timed out.

## Recovery contract

- Normalize only narrowly bounded native numerical residue at the shared native
  own-movement boundary. Preserve actual body positions and own/carrier separation;
  retain the pure motor's finite/displacement/step bounds.
- A rejected or throwing step may already have moved the body. Do not execute it
  again, blindly roll it back, replace the character, or retire its Mind/hand roots.
  A safe finite result becomes the correction authority, with rejected velocity
  cleared and a no-replay prediction barrier. Unknown/unsafe pose data is held,
  not serialized into a snapshot.
- Client-only prediction failure must also resume from fresh acknowledged known
  authority, even when the server did not increment its rebase revision.
- A healthy pending Begin/Ready handshake retains the same body/session and retries
  delivery rather than disconnecting solely because loading exceeded five seconds.

Old terminal failures left durable `ACTIVE` rows, not a trustworthy saved movement
failure reason. Recovery must not invent that reason. Reclaim requires independently
proven exact loaded living body/account/profile/Mind identity, a consistent unique
current-primary row/generation, and no competing authority. Use expected-row CAS
and existing OFFLINE reconnect services; absent, conflicting or ambiguous evidence
remains fail-closed. This is bounded exact-body reclamation, not general crash recovery.

## Examination and acceptance

Ordinary examination previously treated projected plot blocks as world cells.
Frame-qualified identity, current server map-space eligibility, known-only client
selection and render-pose outlines are tracked in `EXAMINATION.md`; abandoned-body
reclamation is tracked in `RECONNECT.md`.

Prior-revision coordinated validation (before this retest's fixes):
**3,177 JUnit, zero failures/errors/skips;
26/831 focused and 831/831 full GameTests passed**. Evidence:
`build/shuttle-feedback-final-build.log`,
`build/shuttle-feedback-final-native-10.log`, and
`build/shuttle-feedback-final-full-native.log`.

Coverage includes real session post-move rejection/error, held unknown-context
snapshot suppression/resume, next valid movement and same-body clean reconnect;
125-tick Ready delivery; normal-login reclamation of a production terminal claim;
rotated frame selection/visibility; and active/removed-owner native heat-map ticks.
Fresh startup reservation/CAS/restoration is unit/service-path evidence, not a
connected restart test. Client prediction barriers are tested through shared runtime
policies; headless fixtures cannot certify actual client networking or rendering.

Useful fixture entry points: `LifecycleMovementRecoveryGameTests`,
`LifecycleAbandonedActiveReconnectGameTests` (lifecycle/server),
`LoadedFrameRayQueryGameTests` and `SableRemovedHeatMapTickGameTests` (shuttle/sable).
Client-only resume uses `CharacterMovementRebasePolicy` and existing bounded
prediction history. Reconnect extends the existing resolver, registry/current-primary
CAS and normal staging services; examination extends the shared frame-ray context
and existing visibility/shape policy, rather than a second native clipping kernel.

Connected acceptance remains open: use `MANUAL.md` with the rebuilt artifact.
Rotation/latency/second-client continuity, exposure sampling, persistence/performance,
docking and dimensional transfer remain separate gates. The outside-envelope
allocated-but-unknown chunk crop does not yet have a dedicated regression fixture.

### Native selected-contact response follow-up — 2026-10-23

The shared `NativeSelectedContactJournal` / `Access` is attached to exact
`CollisionInfo` and is the sole selected SAT geometry source. `LooseItemEntity`
adapts immutable snapshots and diagnostic observers only. Native mob and loose
response share `NativeNormalContactPolicy`. The mob adapter is generic-world-only,
priority 900 after Sable native travel at 1000, and excludes actual owners rather
than all interface implementors. Restitution 0 is native vanilla adaptation, not
a delivered SS14 pig prototype. Nonparallel contacts are refused; overflow and
freshness boundaries fail closed.

Evidence remains bounded and separate: `run1d7d6344` PASS (pig translation 1/905;
item/spill union 7/905), with pig 1.7099 m/s committed and 0.3116 m later outward
world travel tracked, and union item 1.8474 m / 0.39616 m with interior stable.
`run9a513a139` is **OVERALL FAIL**: pig strike 2/907 passed, verifying rotation's
~0.718 m/s committed angular contribution; supported setup failed before pig spawn
on a falling frame. Separate `run9a483e65479fe6d73f45304` PASS (supported 1/907):
actual native frame impulse, exact-deck/on-ground, local delta <1 and frame
displacement >.02 after 35 physical ticks. Floor/coordinate setup mistakes are
fixture-premise defects, not production regressions; existing world pad top 300
was reused and frame support queried in storage coordinates. Controlled-body
physics/prediction, connected-wall fidelity, extreme spin and persistence remain
open. No acceptance/readiness is claimed.

### Airborne bound-human side impact follow-up — 2026-10-23

`run4ff914aa3bac153cea87d2e4` passed **2/911** for
`translatingHullSideImpactSeparatesAirborneBoundHuman` and
`rotatingHullSideImpactSeparatesAirborneBoundHuman`. Both use a real jump with no
horizontal input, target the post-motor normal, and require complete fresh side
contact, a free own step, outgoing normal velocity `>.05` plus positive own
outward displacement, total displacement `>.02`, and exact-OBB SAT positive
clearance. Committed normal speeds were `1.70987797 m/s` and `.71851073 m/s`.

Grounded oracle `runb99d4eb3a574824d396b415d` passed **2 actual cases only**;
an initially prepared 4-case claim was wrong because tags were misassigned, now
fixed by bytecode preflight reporting actual names. Its oracle checks contact
stop, world ownership, first-normal response, outward displacement and no
material penetration. SAT residuals `6.23e-8` (translation) and `9.79e-8`
(rotation) are within native `1e-6`: exact touching, not a free gap or burial.
The shared tag selects 4/911 and the dedicated airborne tag 2/911; all four
native cases passed across two runs, not one four-case run.

No production friction/air-slowdown tuning or external-flight lifetime was
added. `moveInFlight` remains a loose-item-only test signal, not generic/owned
eligibility; rotated exits use the actual oriented shape rather than global AABB.
This does not close connected wall, camera, latency, extreme-spin or persistence
gaps; no OWNER_READY/M1 acceptance, sprint completion or handoff is claimed.
