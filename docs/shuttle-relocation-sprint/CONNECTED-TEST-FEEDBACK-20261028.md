# Two-client owner test — 2026-10-28

Owner observations, not established root causes. **Unfixed unless stated below.**
The owner creates a fresh world on every test: use the normal launch. Do not
introduce a custom run directory without a concrete technical necessity.

Current priority is the separate [angular recovery design](ANGULAR-RECOVERY-DESIGN.md).
The other defects remain queued; no implementation is authorized by this record.

## Reported defects (original numbering)

1. **Notable remote movement delay.** One client moves well before another sees
   it. Unacceptable. Distinguish transmission cadence, buffering/interpolation and
   rendering rather than assume a common cause for all subsequent problems.
2. **Lighting near outside walls.** Other players sometimes lose lighting near
   some ship walls, but not others. Investigate lighting sample/pose coordinates;
   being incorrectly sampled inside geometry is a hypothesis, not a diagnosis.
3. **Doorway correction/teleport.** The interacting client still sees minor
   forward corrections; the observing client did not see teleportation. Owner
   suspects the local client recognizes an open door late, blocks movement, then
   reconciles forward. Distinguish this from remote movement delay in issue 1.
4. **Stale jug throw aim.** Pick up a jug, look elsewhere, quickly throw it: release
   appears to use the earlier looking direction. Use the throw's aim, not stale
   pickup/input state; preserve accepted release/orientation rules.
5. **Jug placement correction while moving.** The apparent slide looks like a
   position update/correction, not intended physical motion. Investigate placement
   and reconciliation; do not mask it with friction tuning.
6. **Walking animation includes carrier motion.** Moving passengers animate from
   global speed. Standing aboard should not look like walking; consider motion
   relative to the supporting frame, including rotational point motion.
7. **Lighting during upward ship travel.** Other players sometimes lose lighting.
   Owner suspects momentary placement/sampling inside the floor. Potentially
   related to issue 2; keep the observation distinct until verified.
8. **Wrong third-person orientation on a spinning ship.** First-person direction
   is correct, but the rendered character faces incorrectly. Historic ship pose
   timing is an owner hypothesis; also verify rotation composition/application.
9. **Angular velocity vanishes on jumping.** Linear launch inheritance appears
   correct. Continued angular motion and bounded recovery are a new design scope,
   documented separately; do not conflate them with linear `ω × r` inheritance.
10. **Walking curves relative to a spinning cabin.** Unintended displacement seems
    spin-dependent. Distinguish a legitimate curved world trajectory from an
    incorrect path relative to the deck/view; do not dismiss the latter as physics.
11. **Status instructions do not match output.** Owner cannot find `bodyUp` or
    `tiltDegrees`, despite being told to inspect them. Verify actual command output
    and correct the command or documentation; do not make the owner hunt for them.

## Positive results to preserve

12. **Roll worked very well.**
13. **Slipping worked aboard the ship, including while spinning.**
14. **Landing worked, including a rolling crash landing, with nothing outstanding.**

These are owner observations and regression boundaries, not universal guarantees.
Do not retune those behaviours while addressing unrelated defects. Networking,
lighting, door prediction, cargo correction and animation may have different causes.

## Angular recovery follow-up after readiness PASS

`run_5c5fff9e73467026f7696883` passed technical readiness, not owner acceptance.
The owner reports that jumping inside the ship still kills angular velocity
relative to the ground, with behaviour nearly identical to the earlier build.
Treat issue 9 as unresolved. Passing native angular-state transfer assertions did
not establish visible end-to-end angular motion in the connected client.

The current manual setup cannot practically generate tilted/tumbling landings.
Skip that comparison for now; the seeded-orientation native landing case proves
the response to an imposed attitude, not naturally achievable tumbling in play.
Upright yaw-only landing remains an actionable manual check.

After the bounded-capture test (22:49:55–22:49:58), the owner reports that rotation
is now visibly attempted but still incorrect. They also report a **supported-motion
regression**: standing on the spinning floor causes sliding. Do not dismiss this
as expected carrier motion or change linear inheritance without identifying the
cause. The real manual cabin immediately obstructs a jump with its low ceiling;
give the manual test useful headroom separately from any physics correction.
The captured log is preserved under ignored
`build/agent-workflow/angular-recovery-20261028/owner-capture-224955/`.

### 2026-10-29 revised-build test

After readiness `run_1d3e2b8150ebe5e1c5d07776`, the owner reports:

1. Small jitter on takeoff and landing.
2. Releasing walking input produces about half a second of positional shifting
   before settling.
3. Hopping on a yawing ship appears to add forward speed and then slow it down.
4. They ask how Minecraft air steering and no-input momentum loss interact with
   moving/rotating ships; this is not a request to silently retune drag.
5. Walking begins in the expected direction but becomes sideways as the ship turns.

The owner performed the bounded capture and subsequently jumped out of the ship.
The log is preserved under ignored
`build/agent-workflow/angular-recovery-20261028/owner-capture-20261029-003732/`.
All 100 capture rows contain final-camera fields. Effective angular state briefly
reverts to the pre-jump orientation/zero spin when replaying pending takeoff, then
recovers with the next authoritative departure snapshot. Investigate original
per-input support evidence versus recapture from current native geometry.
Do not infer that this explains every reported motion defect.

Gravity overlap is a separate design discussion, not new implementation scope:
normal ship/station fields are expected to have equal Earth-strength gravity.
The owner wants smooth intentional transfers without unwanted switches merely
because fields overlap. A strongest-field-only rule does not resolve that need.

### Follow-up after replay/reference readiness

After `run_7b12412b5404a864a6abd1ae` passed readiness (not acceptance), the owner
confirms **walking direction is now correct**. Preserve that result. Remaining/new
observations:

1. Significant server strain while the ship moves against the ground.
2. Immediate forward offset in the yaw-spin direction on jumping, and backward
   offset on landing, despite correct walking.
3. Placed/tossed jugs shift in the spin direction, reverse and settle in stages.
4. A yaw-rate target of 10 does not appear to attain that spin.
5. Automatic angular comfort presentation must be configurable and **off by
   default**; its late activation is also reported. Medical critical/dead camera
   behavior must remain separate.

The preserved log is
`build/agent-workflow/angular-recovery-20261028/owner-feedback-20261029-022605/owner-latest.log`
(295,353 bytes; SHA-256
`1063f23844d40d913b58bc00dd8958108f81dccaa47b958fe4c251bcc419ffc8`).
It records rad/s targets up to 100 and native entity-collision samples with ten
sweep subdivisions. These are **not** the physics-system substeps: the collision
routine selects up to ten subdivisions from movement length, independently of
the six-substep physics baseline. The current run TOML reads six. Neither the
collision count nor the target proves the cause of strain; do not change owner
configuration or conflate the separate target trials.
No new bounded jump-camera or jug packet/render capture was requested or assumed.

### Follow-up after presentation readiness

After `run_5d37089d2c7181062bce4b5c`, the owner reports the spinning-ship jump
trajectory/jitter and jug-placement problem are **unchanged**. Do not label the
presentation corrections as resolving those physical symptoms. Additional facts:

- Boarding a ship moving away at 5 m/s appears to miss, then teleports the player
  aboard; boarding an approaching ship is easier. Horizontal motion also appears
  to lift the ship, complicating boarding.
- Looking straight up/down while moving can lock the camera.
- Walking while placing a jug makes it slide with the actor. On sufficiently fast
  spinning floors, placement can break the jug.
- The owner clarifies placement must start at rest relative to the supporting
  surface (matching its world point velocity), without inherited walking/throw
  motion or an artificial placement impact. This is distinct from an air drop or
  throw; do not make jugs globally unbreakable.
- The owner requests removal of the apparent angular-speed ceiling. Distinguish
  a true speed clamp from bounded acceleration acting against native damping;
  retain stock physics and do not silently disable universal drag.

Preserved log:
`build/agent-workflow/angular-recovery-20261028/owner-feedback-20261029-125411/owner-latest.log`
(698,669 bytes; SHA-256
`9ab279f61a895b9dc7202884d3877aff4c7b085bbcf852276f76f56a42cf9cfa`).
The existing two-input replay discrepancies are not, alone, measurements of a
teleport. No new bounded capture was assumed.

### 2026-10-30 receding-ship boarding blocker

After readiness `run_fa4357da3cf5360ce31f04fc`, the owner reports normal movement
until attempting to jump onto a ship moving away. Immediately afterward,
responsiveness and prediction deteriorated: pressed-key actions happened about
**one second late**. Receding-ship boarding also became worse, including at lower
speeds that previously worked. The owner stopped testing and will not test the
remaining items while this blocker persists.

Treat this as an unresolved connected-play regression/issue, not accepted by the
38 selected native passes. Those boarding fixtures exercise server-owned motion,
not the complete connected prediction/input-queue boundary. Establish the failure
timeline from the current owner log before attributing the delay to physics,
prediction barriers, or queued input. Do not retune normal movement, weaken
contact evidence, or ask for another broad manual run without a grounded fix.

Current normal-run logs are preserved in ignored `build/owner-boarding-feedback01/`
with source paths/timestamps and hashes in `provenance.json`. `latest.log` is
96,708 bytes, SHA-256
`22005430d6e259c2ce0e9ada001e70445c13365a3da18798810592cb3265cb6f`.
After the first jump input (sequence 815), reconciliation changes from eight
replayed inputs to repeated `HISTORY_LOST`/zero replay. The sampled client tick
cadence stays near 50 ms; the sent–ACK gap stays about nine and sampled server
queue depth stays nine. This supports investigating lost local prediction rather
than asserting a growing input queue or server stall. The initiating barrier is
not identified by the existing log.

Source inspection identifies a recovery defect: `GhostPredictionHistory.reconcile`
retains an incomplete pending suffix but erases its per-input evidence. Once ACKs
cover the missing prefix, that suffix can replay without required angular facts,
causing another authority barrier. Correcting suffix retention must preserve the
no-gap replay rule and all real barrier/ACK/eviction cleanup; it does not alone
establish the first boarding contact's cause or owner-visible resolution.

### Follow-up after replay-recovery readiness

After `run_8e6939e175751bc366a6815e`, the owner confirms the **persistent
prediction failure no longer occurs**. Preserve that improvement. Receding-ship
boarding initially still missed/corrected with client/server disagreement after
login, but worked flawlessly after a few attempts as the server calmed. This is
not proof that cold-start boarding is fixed or that load caused the corrections.

The main remaining jump problem also occurs **without yaw**, aboard a ship
moving horizontally at **2 m/s**:

- Takeoff appears to jut in the ship's direction, as if given extra velocity or
  a prediction correction.
- In the air that motion quickly diminishes; landing is farther back relative
  to the ship than the initial standing position.
- The owner suggests two possible causes: an initial launch/correction problem,
  and Minecraft's no-input horizontal air retention acting in the world frame
  rather than relative to the ship. These are hypotheses to investigate separately,
  not authorization to mask the launch problem by retuning drag.

Resolve this before proceeding to two-client testing. Two-client testing and M1
acceptance remain future steps; the sprint is not complete. Preserve current
controls, linear point-velocity inheritance, and the repaired replay recovery.

Additional wall-contact observation: the owner can jump and stick to a ship wall
moving toward them without holding movement toward it; a receding wall can hold
them while pressing toward it. Sticking also occurs with an **active zero target**
(`motion <id> 0 0 0 0 120`), but not after `motion_stop`. This distinguishes
controller-active/native-body state from requested ship speed. Investigate wall
contact, grounding and recovery separately from no-input air retention; a side
contact must not stand in for floor support. The cause is not established.

New normal-run snapshots are in ignored
`build/owner-horizontal-jump-feedback01/` (subsequent tail in `02`), with
provenance files. Use separate `dev-launcher-client.log` and
`dev-launcher-server.log`: `latest.log` interleaves both processes. The first
server snapshot is 9,182,975 bytes, SHA-256
`2d1080fb777e7d64b44dbf8f80300f61d8075ae194406a91a7a7cdb331788fc5`.
It contains 714 rejected harness steps for the controlled body. One active-zero
side-contact case requests zero Y but receives about +0.0000168 native Y;
both grounded flags are false and the bounded-motion validator rejects it.
Its horizontal certificate cannot authorize Y correction, and the position-proof
path is not evaluated. Only three of thirteen native correction rows are printed,
so this is not yet a complete frozen geometry proof or a demonstrated wall-stick fix.

Managed diagnostic `run_3c288c521ffe5e9c1236470d` selected **1/973**, with
**0 passed / 1 failed** (cleanup confirmed). The isolated active-zero, airborne
apex approach captured all thirteen side-contact rows, but did **not** reproduce
the rejection: measured Y was only `+1.8776e-7`, the existing pure-side proof
accepted it, and the body fell normally. This is a below-slop control, not a fix
or a disproof of the owner's wall sticking. Its X-facing wall normal had Y near
`1e-6`, versus the owner's Z-facing normal Y near `6.4e-5`. The captured native
pose has a substantially larger Z-face Y component; align the diagnostic contact
axis with that observed geometry before considering another native experiment.
Leave all production proof and motor limits unchanged. Native character collision
subdivision count (ten in this trace) is distinct from the required six native
rigid-body physics substeps.

Corrected-axis diagnostic `run_1e5a3b5484662b423512c3d6` selected **1/973**,
**1 passed / 0 failed**; result acknowledged and cleanup confirmed. This PASS
means the diagnostic reproduced and captured the rejection, **not** repaired
gameplay. Approaching the closed front steel wall along +Z captured all **16**
native rows with complete geometry. Request was `(0, 0, 0.15625)`, native own Y
`+9.8933943764e-6`, measured own Y `+9.8933943491e-6`; both grounded flags were
false, and no downward world-floor clipping occurred. The horizontal certificate
was present, but the position proof remained `NONE` / `NOT_EVALUATED`; the motor
rejected the unbounded Y. A later descending step entered `PURE_SIDE` proof and
failed `UNEXPLAINED_Y`, so opening the outer gate alone would not solve it.
Archive log: `artifact_task_1e5a3b5484662b423512c3d6_log_8ef4a15411eabce55751`,
SHA-256 `664b42d222f701d5ca7cc5e04d8a9b8bd0e283ae4766d1fb09777ccb24233a67`.
Use this complete trace for a frozen SAT/response regression before authorizing
any larger signed geometric correction. Preserve strict own-motion limits and
separate grounding; the initial takeoff jut and full connected wall-stick recovery
remain unresolved.

Candidate correction (native runtime verification pending): keep generic side
slop unchanged, but evaluate complete pure-side geometry even when its signed Y
remainder exceeds slop. `NativeSideContactProof` preserves requested own Y
exactly and requires that remainder to match the fully replayed side MTV sum.
Only its successful airborne result can use the new position-only
`ContactPositionCorrectionEvidence.validateAirborneSideGeometry` domain; that
domain rejects grounded use. Existing floor/ceiling proof and normal-finalization
ownership remain separate. The frozen sixteen-row regression yields bounded own
`(0, 0, 0.0010200082759098117)` and geometric correction
`(7.415801519528031e-7, 9.893394349091977e-6, 1.904587598744456e-13)`.
Focused JVM checks passed **91/91 across five suites**, with compile/tag preflight
**1/973** and independent review. The native fixture now uses
`shuttle-active-zero-wall-regression`: it must accept the witnessed correction
and demonstrate ordinary falling, rather than pass by detecting the old bug.
Those live assertions have not run yet; no new OWNER_READY or owner acceptance
is claimed. Preserved prediction responsiveness, inertia and air retention were
not changed by this correction.

Runtime follow-up `run_533e1ae25281cc85e5342965` reported **PASS, 1/973 selected**
(acknowledged; cleanup confirmed), but its bounded log contains a remaining
rejection. The first apex step now succeeds; the **second descending step** fails
`PURE_SIDE / CONTACT_EVIDENCE_REJECTED` after SAT, reconstruction and signed-Y
checks. The body eventually falls, but this is **not uninterrupted movement**:
the fixture incorrectly allowed an empty step result after the first success.
Fix the oracle to require every step and preserve the first rejected step's full
trace before considering the wall path repaired. Do not interpret this historical
PASS as satisfying that stronger criterion. The remaining fourteen-row sequence
is not fully printed: only its last failure row is available.
Log reference: `artifact_task_533e1ae25281cc85e5342965_log_d909d90d7f70b82b633c`,
SHA-256 `f0c3fbde4b46d754dd67153777d1d8c140586920bacbc3253bcc9439c063bcf5`.
Investigate ordered horizontal-projection certification separately from the now
accepted signed-Y proof; retain all generic slop and native evidence guards.

Strict-oracle capture `run_9ced34ee970fcb4f7f0a0744`: **FAIL, 0/1 passed of
1/973 selected**, acknowledged and cleanup confirmed. It now stops immediately
on the descending rejection and preserves all **14** actual rows. First-contact
projection removes almost all horizontal input; the next tilted side plane sees
a tiny horizontal prefix but substantial descending Y. Its full 3D approach can
be legitimate even though the public single-request horizontal certificate's
minimum-XZ guard rejects it. Verify that exact ordered projection in JVM replay
before changing the shared certificate; preserve the public single-request guard,
all approach/normal/lambda checks, Y preservation and prefix reconstruction.
Full trace reference: `artifact_task_9ced34ee970fcb4f7f0a0744_log_66d4a99c4f833967c216`,
SHA-256 `dd8af56a4adf5fe513c985a90d882a0e799d9ad97cb91359f5a38ac5e4f7194e`.

Ordered replay now certifies that captured sequence through the existing shared
plane-validation math, while the public single-request minimum-XZ guard remains
unchanged. The formerly rejected intermediate has full 3D approach dot
`-5.65357451473147e-7` and bounded lambda `4.70159193355357e-7`; requested own Y
is preserved. Candidate bounded movement is
`(0, -0.07840000104027735, -4.970660590131182e-6)`. Focused checks passed
**103/103 across seven suites**, compile/preflight **1/973**, and independent
review. Strict native every-step verification is still pending; do not substitute
eventual falling for zero rejected steps. No air retention or prediction changes
were made. Any straight-takeoff experiment must compare matching native pose
intervals and remain distinct from client/render behavior.

Grouped native run `run_d691d3cf4735ffc5beb04bc1`: **2/974 selected, 1 passed /
1 failed**, acknowledged and cleanup confirmed. The strict active-zero wall
regression passed all twelve owned steps and fell normally; no rejection was
masked by eventual progress. This verifies that controlled wall case, not full
connected owner acceptance.

The straight-takeoff diagnostic reached real moving-frame support but hit the
canonical room's low ceiling (local ceiling Y=3; standing feet Y=1, Human height
1.8), clipping the `0.42` jump request to about `0.20`. It is not a clean takeoff
sample. Measured carrier Z `0.09667968721487341` matched that sample's floor
interval Z `0.0966796875`, but the rejected head contact prevents conclusions
about normal departure/inherited velocity or the owner's visual jut. Reuse the
existing manual open jump bay and assert clearance before repeating this
experiment; do not change production ceiling admission to fix its setup.
Log: `artifact_task_d691d3cf4735ffc5beb04bc1_log_080a393fbfd2eda656e7`, SHA-256
`331e467a876f253d46b6f108e9294bcef32ce57a5f1eb58ee77a91570d932c96`.

Manual-bay takeoff run `run_bdc64127988ae52b5851809c`: **FAIL, 0/1 passed of
1/974 selected**, acknowledged and cleanup confirmed. It stopped before the
motion request or Human spawn: an extra pre-command SDK-at-rest requirement
timed out after forty ticks on the larger hull. It captured no takeoff data.
Use the existing admin-motion fixture's ordinary thirty-tick initialization,
then retain the directly relevant measured moving-frame and supported-relative-
rest premises at launch; do not relax the shared settling helper or native
physics to satisfy an unnecessary sleep prerequisite.

Stock Sable camera-position hooks remain unverified: read-only inspection of
the resolved external Gradle-cache directory was denied and stopped. The local
yaw transition alone does not establish a positional phase mismatch; ordinary
entity/floor interpolation might already align. Do not add camera smoothing or
a position bridge without evidence.

Clean manual-bay capture `run_652055100db5a05b20a4db0d` reported **FAIL,
1/974 selected** (acknowledged; cleanup confirmed), but emitted a genuine accepted
takeoff: own displacement `(0, 0.42, 0)`, supported before and airborne after,
one callback and ten complete native yaw samples. Carrier Z was
`0.0966796786424311`; matching floor-interval Z was `0.0966796800494194`
(post-own anchor: `0.0966796837747097`). Differences are only a few nanometres.
Post-move horizontal velocity equals the sampled point velocity once. No extra
authoritative forward takeoff displacement is demonstrated in this case.
The failing quality predicate sampled **last-substep** COM speed `1.58203125`
instead of the matching twenty-Hz interval rate, about `1.9335936 m/s`; correct
the diagnostic measurement, not speed thresholds, and do not rerun merely to
turn this historical verdict green. Log:
`artifact_task_652055100db5a05b20a4db0d_log_c5a6c5c59c070535a42d`, SHA-256
`9729715cb3be5aef148164b168b419c2338ecb12d135790d66abc5a97b855f9b`.

After explicit owner permission, the exact cached stock Sable JAR was inspected
read-only: SHA-256 `9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2`.
Its active camera mixin re-expresses old/current camera points through last/logical
frame poses and the render pose while tracking. This is positional, unlike the
port's yaw event. It is **not** itself evidence of a discontinuity: for pure
translation the formula equals world interpolation if render-pose endpoints
match. Endpoint/old-position timing still needs verification; no camera bridge
or smoothing change is justified yet.

## Fresh connected regression report — 2026-10-31

The owner completed `/ms14_connectedwall jump` and reports severe moving-ship
jump/boarding jitter, substantially worse than before: after jumping and landing
on the ship, the character was teleported back to an earlier part of the jump
and the sequence appeared to replay. Treat this as a rollback/prediction blocker,
not the small initial jut or normal airborne drag. The wall-hover symptom is no
longer reproducible. Preserve this run's separate client/server logs and compare
actual sequence/ACK/snapshot/movement records before changing anything else.
No further owner testing is requested until this regression is understood.

Capture limitation established in source: the client window started on
`cadenceTick`, but SNAPSHOT sampled it with world game time. The captured clocks
were approximately 315 ticks apart; this can expire a 16-tick window immediately.
The snapshot gate now uses cadence time while retaining the server timestamp in
the record. This is an evidence-quality correction, **not a rollback fix**.
Server recovery also does not restore a saved takeoff pose: it rebases or holds
the body's current, possibly partially moved native position. The rejection's
exact motor/clearance stage remains unknown because the old log prefix was
truncated. Keep the wall correction and do not infer a gameplay fix from these
source findings.

The nominal server lifecycle jump now has a full-flight regression:
`NaturalMovingJumpLifecycleGameTests`, using `LifecycleSingleWorldStepObserver`.
Diagnostic `run_2c403e5555314ef241fdae99` **passed 1/975 filtered native tests**:
14 actual movement attempts, takeoff, ascent/descent, matching ship-floor landing,
and three supported confirmation steps, without rejection or rebase. Cleanup and
result acknowledgement are confirmed. The fixture uses the real lifecycle owner
on an embedded transport and the production movement kernel; its test injection
bypasses the default diagnostic wrapper. It does **not** cover connected client
prediction/rendering or establish the reported ascending boarding rejection's
cause. The rollback blocker remains open. Supporting focused JVM evidence is
37 tests across eight classes, with no failures/errors/skips.

Fixture setup invariant: use the manual-cabin variant and its verified open bay,
not the machinery cabin at the same local coordinates. Register that exact frame
with the motion service and allow its existing 30-tick initialization before the
ordinary straight-motion request. Earlier diagnostics `run_bb67cb7e2172b7baa8425db1`
and `run_e5e3b985f5a4085e93e5234d` failed before takeoff on these setup defects;
neither is a gameplay reproduction.

Natural world-pad sprint boarding now reproduces the authoritative rejection:
`run_67d1963941d632717f5092da` **FAIL, 1/976 filtered**, acknowledged and cleanup
confirmed. On the third jump step, requested Y was `0.24813596707369667`, native
measured Y `0.27360569877350827`, and proof geometry-correction Y
`0.025469731699811593`. The native `ASCENDING_FLOOR_AIRBORNE` proof succeeded
(`reason=NONE`, complete 12-row trace/geometry), but `positionStage=REJECTED` left
no position-correction evidence. The motor therefore rejected the unbounded Y
and lifecycle rebased. This matches the owner logs' third-tick sprint-boarding
failure pattern; it does not establish the entire unpaired client rewind.
Source review traced rejection past the valid position-evidence constructor to
endpoint authorization. Its query classified the downward **support band**, so
legitimate floor support could veto an upward repair despite a clear body.
The reviewed candidate uses the existing bounded full-body query for ascending
contacts at both endpoints; negative transient contacts keep their support-band
query. Identity, both-endpoints-CLEAR, and conservative UNKNOWN denial remain.
Typed diagnostics and the boarding fixture now assert the actual query mode and
clearance outcomes. Focused checks passed 106 tests across nine classes.
`run_d288eb0c5c990415e31ae3ba` observed the reproduced third-step contact as
`BODY_VOLUME:CLEAR/changed:CLEAR:AUTHORIZED`, with accepted position evidence
and ascending-airborne grounding. Its **overall result remains FAIL, 1/976**:
the fixture then waited for three post-release *ascending* steps when only two
remained, never sent neutral, and timed out. The reviewed driver now counts
three actual post-contact entry steps, including descent.
`run_e40c16d528cf2be6cea70487` then **passed 1/976 filtered native tests**:
12 actual movement attempts, the same third-step repair authorized with both
body-clearance results CLEAR, actual neutral ACK, ship-floor landing, and three
supported confirmation steps without rejection or rebase. Result acknowledgement
and cleanup are confirmed. This validates the reproduced server-side boarding
fix, not the entire unpaired connected-client rewind. No gravity, air-drag,
camera, or generic collision-tolerance change is
part of this fix. The earlier boarding
run `run_cdf5d511c1623795b316c376` missed its pre-jump dispatch window and is not
a collision reproduction.

The subsequent ascending-air ownership fix reuses matching-frame tracking
release, refreshes the actor point, and requires WORLD ownership before authorizing
the transition. It does not add a second point-velocity impulse; ordinary jump
and negative transient behavior remain unchanged. `run_81111498840fb37cca4503e4`
**passed 1/976 filtered native tests** with the repaired boarding contact,
WORLD-to-WORLD frame-velocity delta zero, five subsequent contact-free airborne
steps whose actual displacement equaled measured own movement (extra carrier
zero), and clean ship-floor landing/support. Result acknowledgement and cleanup
are confirmed. Focused JVM evidence is 107 tests across ten classes, zero failures,
errors, or skips (`build/ascending-air-release02/`). This validates that scoped
transition, not every accelerating/rotating contact or the original connected
client's unpaired visual rewind. Initial-jut/reconciliation timing remains open.

Air retention is a separate design issue. Pinned SS14 has no ordinary 3D jump;
its relative-movement option rotates input, not proof of sustained ship-relative
airborne momentum. If adapting air controls, distinguish a **frozen inertial
launch velocity** from ongoing attachment to an accelerating/turning ship. Do
not add ship-following forces or mask the initial takeoff jut with drag changes.

OWNER_READY `run_95579a54103df683ca47b8c7` **failed**: 39/976 focused
native cases completed, 38 passed and one failed. The old ascending obstruction
fixture authored only a floor and treated its intersecting below-foot support
band as body obstruction. Its actual BODY_VOLUME query was CLEAR. The corrected
fixture retains this near-deck case as an admission control and adds a real
upper-body storage obstacle, exercising the shared live query and identity-bound
authorization for DENIED_CAPTURE/DENIED_FINAL, then CLEAR after removal. The new
case tests that query/authorization boundary, not rejection of a completed move
after editing the world. Compilation, 16 focused JVM tests, and metadata preflight
(9/977 for the two selected tags) passed. No production collision rule was weakened.

Replacement `run_1a08b3d77b43d9992f83e787` completed **40/977 selected native
tests, all 40 passed**, including the corrected obstruction controls and natural
lifecycle boarding. Its native log records ascending authorization at tick 195,
WORLD release with zero frame-velocity delta, five subsequent contact-free AIR
steps with zero extra carrier, and twelve attempts through neutral ACK and three
supported landing steps. The final workflow verdict nevertheless remains **FAIL**:
coverage review read the intake's pre-execution "pending" statement as absence of
runtime coverage. Independent source review confirmed the executed tag requires
these non-vacuous assertions. Fresh evidence bindings must distinguish preflight
metadata from the serialized runtime receipt and branch witnesses; no additional
physics change is indicated. Owner testing still waits for OWNER_READY PASS.

## Connected prediction fixture and two-client follow-up

Owner reports startup lag for roughly 20 seconds even without a shuttle, increased
server cost when walking against a stationary shuttle wall, initial boarding
prediction problems that later disappeared, and local jitter against a receding
ship wall. A second client sees the affected actor moving smoothly; separately,
remote pushed actors appear to penetrate the wall. These are distinct performance,
owner-prediction/presentation, and remote-presentation questions. No measured CPU
cause or common root cause is established.

One actual connected-fixture report was located:
`run/diagnostics/connected-prediction/epoch-2-1793570711934.json`, preserved with
normal-launch logs in `build/owner-connected-prediction-feedback03/`. Its result
is `PROTOCOL_PASS_VISUAL_UNVERIFIED`: 36 sent inputs, 35 snapshots, two jump/release
pairs, and 202 camera samples. The only actual replay is at the final snapshot.
At both takeoffs, applying authority moves the client forward in Z (about 0.1572
and 0.1341 blocks); at both landings it moves backward (about 0.1385 and 0.1242).
All four snapshots have **zero replay frames**. Thus replaying a retired jump is
not needed to explain these four measured discontinuities. Grounded samples carry
the ship frame; airborne samples omit the nullable `frameId` field and use WORLD
authority. The first parsing pass incorrectly treated the non-null frame-ID set
as applying to every sample; ordinary jump detachment is not implicated by that
claim. Immediately before the first takeoff, projected frame authority is about
0.1597 blocks behind server world XYZ. At takeoff WORLD authority removes this
offset; at landing frame projection reintroduces about 0.1376 blocks of offset.
The second flight has the same pattern. This establishes a discontinuity between
the applied FRAME and WORLD authority bases, consistent with different pose times;
the first report omits pose pairs/storage points needed to resolve their clocks.

Candidate correction: owned reconciliation now applies snapshot world XYZ for
both FRAME and WORLD snapshots. Frame identity/residency remain required; the
velocity basis is rebuilt at the applied world position, and the current SDK pose
pair still gates interval consumption. Frame-local snapshot coordinates remain
metadata, not a second position authority. No air reference, drag, smoothing,
native collision, or inheritance rule changed. The real-client fixture now checks
authority placement separately from replay-final displacement and records frame
storage/pose/timing data. Focused checks passed 56 tests across eight classes;
actual connected takeoff/landing and moving-wall contact remain to be rerun,
particularly because SDK collision geometry is still on its interpolated timeline.

Bulk default collision-rejection logging is also removed: server full traces
require explicit geometry observation, and client full traces require an active
capture. Ambient DEBUG is not consent to format them: the normal development
launcher itself uses DEBUG. Compact failure identity/reason warnings remain.
Expected pending movement no longer triggers a full framed-disagreement dump.
This removes known unnecessary formatting/output; startup and wall CPU costs
have not been profiled, so overall lag is not claimed fixed.

The test exercises neutral on-ship jumps, not boarding, wall pressure, startup
performance, or observer-client synchronization. Its protocol result is not a
visual smoothness assertion. No second completed report was found; an accidental
second command cannot be classified from the saved report alone. Automatic client
launch remains disabled.

## Common-world candidate regression

Owner reports worse standing jitter and direction-dependent deck offset, persistent
boarding instability, and larger irregular moving-wall corrections including
visible penetration after the common-world placement candidate. Do not treat its
diagnostic PASS as a successful gameplay fix. The later connected report
`epoch-2-1793580216089.json` still says `PROTOCOL_PASS_VISUAL_UNVERIFIED`, but records
about 0.405 blocks of worst before/final correction. Recorded frame-storage and
logical-pose data show roughly 0.10–0.20 blocks of server/client frame-position
disagreement in sampled grounded states. The authority-placement oracle's zero
error verifies our selected rule, not compatibility with collision geometry.

Current logs/reports are preserved in `build/owner-connected-prediction-feedback04/`.
See [PREDICTION-MODEL-REVIEW.md](PREDICTION-MODEL-REVIEW.md) for the visual pipeline,
the failed timing assumption, airborne-retention concern, pinned SS14 references,
and process correction. Implementation is paused for this explanation; no further
client launch, gameplay change, or owner retest is implied.

## Process

Finish design discussion before implementation. Reuse existing movement,
prediction, action, lighting and camera owners rather than create parallel paths.
No automatic client launch or changes to normal testing configuration are implied.
