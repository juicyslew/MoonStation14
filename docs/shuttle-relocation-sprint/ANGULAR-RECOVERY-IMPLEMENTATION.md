# Angular recovery — implementation notes

## Status and scope

**Current blocker (2026-10-31):** after the connected-jump instrumentation build,
the owner reports much worse moving-ship jumping/boarding: landing followed by a
teleport back to an earlier part of the jump and apparent replay. The wall-hover
symptom is no longer reproducible. Preserve the new capture and diagnose the
rollback/replay regression first; current connected prediction is not accepted
as repaired. The earlier improvement below is historical, not a current verdict.

After readiness `run_8e6939e175751bc366a6815e`, the owner confirms the persistent
prediction failure is gone and warmed-up receding boarding works. Preserve the
history-suffix fix. Remaining blockers: a forward jut and backward drift during
straight 2 m/s ship jumps, plus wall sticking with active motion (including zero
target) but not after stopping. Cold-start boarding corrections remain uncertain.
See `CONNECTED-TEST-FEEDBACK-20261028.md` and its current captures. Wall-contact
diagnosis precedes any air-reference change; no new physical fix or M1 acceptance
is claimed. Two-client testing remains deferred.

This adds player-body angular inheritance/recovery, not ship gravity generators,
new translation physics, an oriented rigid-body collision engine, or EVA equipment.
Existing linear point-velocity inheritance and native character collision remain
owned by their existing services. No assets or sounds were added.

## Verified upstream composition

Reference checkout verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Complete relevant entity ancestry (shared nodes listed once):

```text
MobHuman -> AppearanceHuman, BaseSpeciesMobOrganic
AppearanceHuman -> BaseSpeciesAppearance
BaseSpeciesAppearance -> InventoryBase, BaseSpeciesLayers
BaseSpeciesMobOrganic -> MobBloodstream, MobRespirator, MobAtmosStandard,
                         MobFlammable, BaseSpeciesMob
MobAtmosStandard -> MobAtmosExposed
BaseSpeciesMob -> BaseMob, MobCombat, MobDamageable, MobPolymorphable,
                  StripableInventoryBase, BaseSpeciesAppearance
BaseMob -> BaseControllable
StripableInventoryBase -> InventoryBase
```

The other listed nodes are roots. Sources: upstream
`Resources/Prototypes/Body/{Species/human,species_base,species_appearance}.yml`,
`Resources/Prototypes/Entities/Mobs/base.yml`, and
`Resources/Prototypes/InventoryTemplates/inventorybase.yml`.

`BaseControllable` contributes `GravityAffected`; `BaseMob` contributes the
kinematic controller, movers and movement-speed component. `BaseSpeciesMob`
contributes `StandingState` and `Crawler`. Component-code defaults include walk
2.5, sprint 4.5, acceleration 20; Crawler's default knockdown is 0.5 s with a 2 s
stand-up delay. Upstream `SharedStunSystem.TryKnockdown` checks standing capability
and attempt-event admission; durations supplied by callers are not a universal
Human stun duration. Reuse the port's existing composed stun/knockdown path.

`GravityGenerator -> BaseMachinePowered -> BaseMachine -> BaseStructure` (root).
Upstream `GravityGeneratorSystem`, `GravitySystem` and `SharedGravitySystem`
enable gravity by generator parent/grid/map membership, not a radius. This is
binary gravity/weightlessness, **not** a 3D orientation or angular-recovery model.
All new angular policy is an explicitly local 3D adaptation.

## Local policy and ownership

- Typed `AngularRecoveryComponent` is registered in the existing component codec,
  schema audit and resolved `CharacterData.angularRecoveryPolicy()` path. Human
  composes it; inherited local prototypes can override it. Absence opts out.
- Defaults: grace **0.65 s**, recovery **1 s**, bad-landing tilt **45°** or off-axis
  spin **>1 rad/s**, knockdown/stun **0.5 s**. Comfort return requires tilt at most
  **10°** and free spin at most **0.25 rad/s**. These are adaptation values.
- Existing jump physics remains unchanged: 0.05 s step, 8.4 jump velocity, 32
  gravity and 0.98 vertical drag. The grace check uses the actual discrete motor,
  not an invented upstream jump default.
- `AngularRecoveryModel` is immutable and JOML-backed. Its quaternion is a
  world-frame inertial basis; raw logical yaw/pitch compose afterward. It integrates
  a finite spin envelope and rights toward the supplied gravity-up target.
- `CharacterAngularRecoverySystem` samples native world angular velocity only
  from grounded, matching live frame/floor evidence before the owned move.
  Departure is a support transition, not proximity or gravity membership.
- `GroundedHarnessWorldStep` advances rotation within the existing physical-step
  ownership budget. Landing reuses `CharacterControlSystem.applyParalyze`, settles
  tilt/spin and preserves heading. It does not reset fall distance or replace damage.
- Current live gravity is world-down only. The pure model accepts nonvertical
  targets for the future gravity consumer; no live ship-field force is claimed.

## Replication, look and camera

`PlayerCharacterHarnessEntity` owns ephemeral authoritative synchronized state and
a separate local prediction override. Existing `GhostControlPayloads.Snapshot`
carries authoritative angular state; `GhostControlClient` seeds it before pending
input replay. `AngularRecoveryPrediction` samples supported client pose intervals
and reuses the same model. `AngularRecoveryLook` supplies world aim/movement heading.
No Sable custom-orientation API is injected into collision/query proof paths.

`AngularRecoveryPresentation` shares interpolated samples between the body renderer
and camera. Medical/prone/corpse presentation retains priority. Camera composition
is checked against actual Minecraft `Camera` rotation, including its negative-roll
convention, rather than inferred from forward direction alone.

Disposals and medical perspective policies share `PerspectiveOverridePolicy`.
Recovery extends the existing medical perspective owner, not another camera loop.
Automatic angular comfort presentation is a client preference,
`angularRecoveryComfortCamera` in `moonstation14-client.toml`, **off by default**.
`ClientConfig` is registered separately as a NeoForge CLIENT spec; the live medical
perspective owner reads it without disabling critical/dead medical views. Disabling
an active comfort episode uses the existing inverse-basis look and perspective
handoff. Opt-in activation still waits for `recoveryActive`; its timing was not
retuned with the default change.
Stable external angles follow mouse deltas without body spin; inverse-basis look
handoff avoids accumulated-yaw jumps on return. F5 can opt out for the episode.
Containment transfers the original perspective baseline and retains its lock rules.

## Validation

Core/prototype/shared-camera batch: **61/61 JVM checks passed**. Final integration
batch: **92/92 JVM checks across 15 classes**, zero failures/errors/skips; Java and
GameTest compilation passed (`integration/attempt-13`). This is a fresh selected
batch, not a full project suite. Camera tests exercise Minecraft's actual rotation
API; interpolation tests compare quaternion orientation independently of sign.

First managed diagnostic (`run_66ebea7122ca7385cef41357`): **9/963 selected,
7 passed / 2 failed**; acknowledgement and cleanup confirmed. All five existing
rotating jump/departure/ceiling regressions and both landing cases passed.

The remaining fixtures exposed setup/oracle problems: the long fall landed on the
authored world support pad; the jump compared an earlier spin sample with a later
queued takeoff while pad contact braked the hull. The corrected fixtures remove
only their verified authored pad and capture native angular velocity at the actual
completed airborne step. Snapshot checks exclude pre-jump grounded samples. These
are fixture-only changes, not relaxed recovery or physics assertions.

Corrected fixture compilation and metadata **2/963** passed
(`fixture-correction/attempt-02`); independent source review passed before the retry.

Second diagnostic (`run_26858b76926aa9a3b7bd0a31`): **2/963 selected,
1 passed / 1 failed**; acknowledgement and cleanup confirmed. The real unsupported
fall now passes finite zero-spin/upright recovery. The jump's native takeoff-spin
comparison passed, but the body landed before eight airborne samples: the authored
internal ceiling at local Y=3 leaves about 0.2 blocks above the standing head.
The jump-only fixture now verifies and opens that ceiling patch, retaining the
floor, outer roof and strict angular/sample assertions. Compilation, metadata
**1/963** and independent source review passed before the jump-only retry.

Third diagnostic (`run_ef7327f188da15e614429d89`): **1/963 selected, 1 passed**;
acknowledgement and cleanup confirmed. The native jump now passes takeoff-spin,
eight subsequent airborne grace samples and correlated snapshot assertions.
All **nine distinct selected native cases** have passing component evidence across
the three runs (7 + 1 + 1), not a single fresh nine-case run or a full-suite PASS.
Production code and recovery thresholds were unchanged during fixture corrections.
The normal feature-readiness gate subsequently passed
(`run_5c5fff9e73467026f7696883`). Owner testing then reported that in-cabin jumps
still lose angular velocity relative to the ground. Stored angular velocity and
snapshot assertions alone did not prove the connected visible orientation path.
The prior checks remain historical evidence, not proof that this report is fixed.

Follow-up diagnostics strengthen the jump oracle to check integrated world
orientation, the actual body's logical look and an advanced snapshot orientation,
not just stored angular velocity. The existing opt-in
`/ms14_connectedwall arm` capture now records effective/published angular state,
shared presentation orientation and the final rendered camera orientation within
its existing bounded capture. These are observability/test changes, not a fix.
Compilation and **24/24 focused JVM checks across seven classes** passed;
jump-only metadata is **1/963** (`owner-jump-diagnostic/cheap-03`). The strengthened
native oracle subsequently passed **1/963 selected, 1 passed**
(`run_22166b383d92bae4f55d4b0c`); acknowledgement and cleanup are confirmed.
This establishes integrated server orientation, logical look and advanced snapshot
orientation in that fixture, not the owner's connected view. A failing connected
jump capture is still needed; no production physics fix has been made.

### Captured landing handoff revision

The subsequent owner capture confirms effective client spin near **2.777 rad/s**
and advancing orientation during a short jump, followed by a real ceiling hit and
landing. It does not prove final camera orientation: a format/argument mismatch
dropped that observation. The logger mapping and camera-forward observation are
corrected and covered by a formatting regression test. Matched post-move frame
coordinates do not establish the cause of the owner's reported supported sliding;
that report remains unresolved, not dismissed as normal carrier motion.

The revised shared `AngularRecoveryModel.afterMove` advances the ending airborne
tick before landing assessment/settlement. Both authoritative and predicted moves
use it; grounded idle ticks retain the settled state unchanged. Completed live
landings seed `ClientSupportedHeading` from matching fresh native floor evidence,
so the next supported interval is not spent merely rebinding. Replay cannot seed
the live heading history. This fixes an identified logical-heading continuity gap;
it is not a claim of exact fractional contact timing or connected-client acceptance.

Manual fixture builds now add a same-frame **10×11 jumping bay with five blocks of
clear height**, beside the unchanged canonical cabin. The total manual footprint
is 20×7×13; the original sealed room, airlock, power and ventilation remain intact.
Automated canonical cabin geometry is unchanged. Full loaded/empty-footprint
preflight, native storage ownership and disposable cleanup cover the added bay.

Current focused validation: **114/114 JVM checks across 20 classes**, zero
failures/errors/skips; Java and GameTest compilation passed
(`landing-handoff/cheap-03`). Managed diagnostic
`run_ad83d2614db9f509af2dd9a1` selected **17/965**, with **15 passes / 2 failures**;
acknowledgement and cleanup are confirmed. All five angular cases (including
post-landing support stability), five shared movement controls and four admin-motion
controls passed. This is server-side evidence, not connected sliding acceptance.
The new supported-stability case compares matching post-tick frame coordinates
after a real ceiling jump/landing, not mismatched pre-/post-carry positions.

The two positive manual-command cases failed because their old template-sized
clear areas did not fit the wider bay; production correctly refused the build.
All three command cases now reuse distinct bounded, loaded, empty fixture scenes
within the ordinary operating radius. The negative case verifies its deliberately
occupied annex cell is the sole obstruction. Production geometry and preflight
remain unchanged. Compilation, metadata **3/965** and independent review passed
(`command-fixture-correction/attempt-01`). Diagnostic retry
`run_cdf1dae5aebc6e2951b1e157` then passed **3/965 selected, 3 passed**;
acknowledgement and cleanup are confirmed. All **17 distinct selected cases** now
have passing component evidence (14 unchanged cases plus the three command cases),
not a fresh 17-case aggregate or a full-suite PASS. Revised readiness is pending;
connected sliding and final-camera behaviour remain owner-testing questions.

An incidental unfiltered JVM invocation ran 3,655 checks and failed four. The
affected landing oracle was updated for the corrected ending-interval contract
and passes the focused batch. Three unrelated ASM wiring checks targeting older
profiling-wrapper locations remain failed; no full-suite PASS is claimed and no
unrelated runtime changes were made to silence them.
Raw receipts belong under ignored `build/agent-workflow/angular-recovery-20261028/`;
managed results remain in the coordinator archive, not copied into documentation.

### Recorded departure evidence and supported movement reference

The preserved 2026-10-29 capture contains 100 final-camera observations and 11
airborne samples. At client tick 8527, a pre-jump authority ACK (8518) caused replay
to restore the old orientation with zero spin; the departure ACK restored spin on
the next update. Replay had resampled present-day support instead of retaining the
original takeoff evidence.

`GhostPredictionHistory` now coalesces normal-contact and angular-support facts in
one bounded per-intent evidence map. Angular facts are recorded once after a live
physical move, including explicit zero/unsupported evidence. ACK, eviction, gaps,
barriers and reset share cleanup. Grounded angular replay uses the recorded fact
against the authority frame; required missing/mismatched evidence invokes the
existing authority barrier, not invented zero spin. Airborne replay advances the
authoritative angular state. Replay does not resample floor spin or mutate the
live sampling cache. Native collision remains current-pose, not historical rollback.

`Intent.supportedMovementReference` is an optional typed frame UUID/local movement
yaw. `SupportedMovementYaw` captures the world movement heading in the actual
support's world-up yaw basis and resolves it through the current matching support.
Raw yaw/pitch retain their look/aim meaning. `WorldUpTwist` supplies Minecraft's
sign convention. Client capture, prediction/replay, held lifecycle input and
`GroundedHarnessWorldStep` consume the same reference. Server admission reuses
the existing native floor attestation; airborne, world-floor and changed-frame
movement use the ordinary world-heading fallback. This fixes a source-level
capture-to-consumption basis mismatch; owner-visible sideways walking is not yet
verified fixed. Upstream's conditional `RelativeMovement` grid-wish rotation is the
analogue, not proof of full 3D jump parity (pinned `SharedMoverController.cs:623–635`).

Current evidence: **146/146 JVM tests across 23 classes**, zero failures/errors/skips,
plus Java/GameTest compilation and metadata **18/966** selected
(`replay-reference/cheap-06`). Independent source review passed. The new
`SupportedMovementReferenceGameTests` holds one captured reference through the
normal lifecycle input pump and checks native support, acknowledged movement,
deck-relative progress/cross-track, frame rotation and unchanged raw look.
Native diagnostic `run_ccc18562d361006b6b4399da` passed **18/966 selected,
18 passed / 0 failed** in one fresh batch, including the held-reference consumer
and angular/linear regression controls. Result acknowledgement and cleanup are
confirmed. This is selected server-side evidence, not readiness, full-suite proof
or connected camera/manual acceptance; previous native PASSes remain historical.

Air conditioning is unchanged: gravity-air motor velocity is multiplied by `.91F`
each airborne tick regardless of keys, before separate frame-velocity correction.
This is world-X/Z retention, not ship-relative air resistance. It can damp inherited
motion already present in motor velocity. The capture ends just after walking
resumes, so it does not establish a release-and-settle interval or its cause.

### Following readiness: presentation-boundary corrections

After `run_7b12412b5404a864a6abd1ae` passed, the owner verified walking direction,
but still reported jump/landing and jug offsets. These corrections address proven
presentation-phase gaps, not an established physical-position or performance cause:

- `ClientSupportedHeading` owns one completed live-move transition. The first
  airborne render interval retains the departing floor's frozen last/logical yaw
  residual instead of dropping it immediately. The landing interval suppresses
  the extra floor offset because angular presentation already interpolates the
  ending airborne interval. Epoch/body/tick guards and existing resets bound it;
  replay never writes it. Raw mouse look, subsequent floor carry and physical
  angular/linear state are unchanged. This is a camera-heading correction, not a
  new camera-position projection or body-render claim.
- Loose cargo now uses the frame **render pose** for both rendered position and
  orientation. Previous/current local anchors live in the existing
  `LooseClientPositionInterpolation` owner and advance at client-tick boundaries.
  First entry derives the previous local endpoint from the previous world point
  through render pose zero; correction snaps align both histories. The renderer
  and selected outline share `LooseItemEntity.renderPosition`. Native-tracked
  rendering is left to Sable to avoid double projection. Unknown/unloaded/world
  anchors fall back without exposing storage coordinates. `LoadedFrameRayQuery`
  reuses its exact-frame/residency checks for render projection/inverse; ordinary
  authoritative callers retain logical-pose behavior. Server cargo physics is
  unchanged.

Focused checks: **179/179 tests across 30 classes**, zero failures/errors/skips,
plus main/GameTest compilation (`owner-feedback-20261029/combined-cheap-05`).
Independent camera-transition, cargo-render and client-preference reviews passed.
No new native or connected result is claimed for these presentation changes;
the previous 18-case native diagnostic remains prior server-side evidence.
Client TOML reload, subjective camera continuity and placed/tossed jug appearance
remain manual checks. Ground-contact strain remains undiagnosed. Yaw commands
request rad/s targets through bounded acceleration, not instantaneous native spin.

### Physical corrections following the 2026-10-29 feedback

The owner reports that the presentation revision did **not** fix spinning jumps
or jug placement. Walking direction is owner-verified; retain the movement-only
support reference and raw look semantics. The following changes are not yet a
new readiness or connected-client result:

- Verified surface placement now records the target frame and the actual offset
  placement point, inside the existing full-box safety check. The hand transfer
  reuses `FrameAnchor.hydrate` for that exact target and launches with zero
  **support-relative** velocity. World-floor placement has zero world velocity.
  `HandReleaseKinematics.composePlacement` suppresses actor walking/carrier
  composition only for surface placement; air releases and throws retain their
  separate inheritance paths. Jug damage/material thresholds are unchanged.
- The native contact adapter admits an already-prepared, identity-bound ascending
  position proof even when a horizontal certificate also exists. Supported
  floor-side landing admits a known world origin or the same frame, never an
  unknown/foreign origin. Existing complete geometry, clearance and motor bounds
  remain required; geometry correction does not become voluntary velocity.
  Regression geometry comes from historical seq3936/15182, **not** an exact replay
  of the latest seq736 or 12:51:50 landing.
- Camera decomposition uses normalized double-precision YXZ matrix terms and an
  equivalent roll-zero representation at the poles. The oracle reconstructs the
  orientation through Minecraft's actual camera API, not forward direction alone.
- The apparent spin ceiling was damping equilibrium, not a discovered speed
  clamp. The fixture controller now compensates captured angular drag outside its
  bounded net-acceleration command. For step `h`, damping `d`, current spin `w`
  and bounded net acceleration `a`, the increment is
  `h*d*w + h*(1+h*d)*a`. Stock damping and the 0.25 rad/s² net ramp remain; stopping
  the controller removes compensation and leaves native coast-down.

Source pins: Sable `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`,
`sable_rapier/src/main/rust/rapier/src/lib.rs` and its Cargo manifest/lockfile;
Rapier `38e92f117590862481a53df6fc69a5d893e29186`,
`src/dynamics/{rigid_body_components.rs,solver/velocity_solver.rs}`. Damping is
applied after position integration. A previously recorded single-solver-interval
recurrence did not account for internal solver subdivisions; it is **not** an
established external-step transport equation. Verify the complete caller/timestep
chain before using it to dismiss or attribute a gravity/impulse transport bias.

New native fixture source covers walking jug placement on a fast-spinning floor,
air-release inheritance, a 5 rad/s yaw target/coast-down, the first horizontal
command's paired PRE/POST interval, and moving-away/approaching deck admission.
These are not native PASSes. Boarding variants are ordinary bound-body motor
consumers, not authenticated outside-footprint chase or exact ascending-contact
reproductions. Physical jump/jug behavior, horizontal lift and ground-contact
strain remain unresolved pending appropriate evidence. Raw receipts belong under
`build/agent-workflow/angular-recovery-20261028/owner-feedback-20261029-1254/`.

Fresh combined focused checks pass **263/263 tests across 35 executed classes**,
zero failures/errors/skips, with main and GameTest compilation (`build/lift03`).
The manifest uses the actual `NativeNormalContactPolicyTest` class, whose source
filename differs; the earlier 34-suite selection omitted it. Two stale wiring
assertions were corrected to select the five-argument movement implementation
rather than its four-argument forwarder, and to prohibit extra actor-point queries
in the diagnostic tail rather than imposing an obsolete whole-method count.
Earlier failing receipts remain failures, not replaced evidence.

Metadata preflight selects exactly **1/971** for `shuttle-horizontal-lift-cause`
(`build/lift-meta02`); this is not native execution. The proposed bounded
experiment requests X=5 m/s, Y=0 for the first 18 paired native steps, then returns
to the existing X=0.5 controls. It distinguishes startup/contact response from
later low-rate feedback; it does not establish achieved or steady 5 m/s behavior.

Diagnostic `run_0622fe9979f4fdd34e920e34` passed **1/971 selected, 1 passed**;
acknowledgement and cleanup are confirmed. PASS only establishes the selected
bounds. The first 18 paired steps rose **0.005706787 m** while the pre-controller
POST vertical velocity became slightly negative. Later airborne X=0.5, Y=0
windows rose **0.001923–0.001953 m/s**, within the existing 0.005 bound. This is
measurable residual lift, not zero drift or a resolution of the owner's report.
Review the internal solver subdivision/impulse integration before another run.
Managed log artifact: `artifact_task_0622fe9979f4fdd34e920e34_log_3df4f5a3f1d9e56a73e0`.

### Linear transport correction and grouped candidate

The pinned Rapier `solver/island_solver.rs` divides the outer step among solver
iterations (default **four**, from `integration_parameters.rs`), then calls
`writeback_bodies` with the **outer** timestep. This confirms the angular damping
interval. For a free body with fixed interval parameters, SDK velocity `v`,
impulse per mass `j`, gravity `g`, step `h`, damping `d` and `N` solver intervals:
`Tnext = v+j+((N+1)/(2N))*g*h`, while
`vnext = (v+j+g*h)/(1+h*d)`. Contacts, CCD and float pose quantization limit an
exact comparison with the recorded startup trace; do not fit an invented `N=8`.

The existing controller now retains COM transport `T` for bounded feedback `a`,
but uses the separately captured SDK velocity for linear drag feed-forward:
`j = h*(a-g+d*v)`. With consistent free-step parameters this gives
`Tnext-T = h*a`, preserving the **2 m/s² transport acceleration bound** without
hardcoding a gravity bias or solver count. Unlike angular feedback, linear `a`
is **not** multiplied by `1+h*d`. Both paths reuse the captured universal drag;
stopping still removes impulses and restores ordinary gravity/damping. Stock
Sable's misleadingly named force JNI calls also apply impulses, not integrated
forces; no new force framework or native override was introduced.

The native airborne criterion is now **0.0005 m/s per one-second COM window**,
which rejects the old observed ~0.002 m/s climb while allowing float-pose
quantization. Initial contact-scale motion remains separately bounded, not claimed
to be identically zero. A new first-world mixed-contact fixture requires the actual
`SUPPORTED_FLOOR_SIDE` proof through the ordinary motor, not just floor-only
boarding or a helper predicate.

Fresh grouped evidence: **266/266 tests across 36 executed classes**, zero
failures/errors/skips, main and GameTest compilation (`build/owner-final02`).
Metadata selects **38/972** affected native cases (`build/owner-final-meta02`),
not a native execution result. Independent implementation and parity source
reviews pass; owner acceptance and connected physical behavior remain unverified.
The jug fixture proves a completed rotating native pose interval **before** the
drop, not merely an angular-velocity field. It then releases walking input for
the eight-item-tick observation. Authored-pad removal is shared in
`MovingCabinAuthorityFixture`; the existing angular/movement fixtures delegate
to the same full loaded-stone preflight.
Managed isolated GameTest directories under canonical build output now reuse the
existing config-staging task's normal six-step default; explicit diagnostic step
counts remain supported, and external/owner profiles are not implicitly staged.
Cheap configuration proofs are in `build/native-config-proof01`.

Readiness `run_e33eeb652bb5a8ecfd1bbd33` **failed: 38/972 selected, 31 passed,
7 failed**; acknowledgement and cleanup are confirmed. The 5-rad/s case reached
SDK same-phase rates **5.00379–5.00381**, passed bounded ramp and coast-down; the
strict airborne zero-Y case measured **zero COM rise in all three one-second
windows**. These are passing components, not an aggregate readiness PASS.

The failed rows identify fixture issues: three scenes had loaded chunks still
awaiting entity-ticking promotion at the 40-tick startup deadline; the jug wait
chased the continuously advancing input sequence despite actual walking; the
world AIR oracle omitted its intentional release velocity; the mixed-landing
setup treated an unchanged floor block as a failed write and skipped its side;
the descent fixture's pad braked away its spin premise. The jug placement and
mixed-contact assertions were not reached. Preserve those coverage gaps until a
corrected complete gate executes them. Native task log:
`artifact_task_e33eeb652bb5a8ecfd1bbd33_log_1bae271372d851561464`.

The next candidate corrects these fixture premises without changing production
physics or weakening gameplay assertions: AIR arithmetic includes placement
intent; walking waits for a fixed submitted sequence; both geometry writes run
and loaded collision end states are checked. Spin-descent receives one initial
upward fixture velocity to clear pad braking, retaining measured angular and
point-motion requirements at contact. Scene startup gets a bounded **120-tick**
allowance through the existing readiness service; loaded/entity-ticking/empty
conditions remain mandatory. Fast-spin jug release and first carry intervals
still require fresh native execution before readiness can pass.

After these fixture corrections, fresh main/GameTest compilation and the exact
36-class JVM selection passed **266/266**, zero failures/errors/skips
(`build/owner-final04`). The unchanged 12-tag metadata union selects **38/972**
(`build/owner-final-meta03`). These compile/selection receipts do not establish
that the corrected native fixtures pass.

Replacement `run_f2bea599d4471474e4f0f493` **failed: 38/972 selected, 34 passed,
4 failed**; acknowledgement and cleanup are confirmed. First-world mixed-contact
landing now passes. Remaining failures occurred before jug release, during the
spin-descent motion premise, and in receding-deck/rotating-jump setup. Source
inspection found the latter two fixtures shared **the same world origin and
support pad** `(1160,300,640)`, so concurrent authoring/cleanup could interfere;
additional waiting cannot isolate them. The spin setup had applied its upward
premise to the Human rather than the frame. The jug fixture probed immediately
after enqueuing view input, without waiting for that view sequence's ACK. These
causal setup corrections must retain all physical and surface-safety assertions;
they do not establish a successful release or native readiness. Managed log:
`artifact_task_f2bea599d4471474e4f0f493_log_4c6b9256f7b72ab15d6c`,
SHA-256 `c6939efd207367b1da1ed76ca24dbef9403af02813aaac9e13d9264333689802`.

The follow-up isolates the rotating-jump origin at `(1480,300,2200)`, waits for
the specific authenticated view ACK before jug probing, and rechecks walking,
grounding and fast native spin at release. Spin-descent now gives the **frame**
one initial 4 m/s upward command, not the Human; ordinary body gravity and all
contact-time angular/point-motion assertions remain. This is bounded fixture
setup, not a production controller, repeated velocity write, or runtime PASS.

Fresh checks after this follow-up: **266/266 tests in 36 executed JVM suites**,
zero failures/errors/skips, main/GameTest compilation (`build/owner-final07`);
metadata **38/972** selected (`build/owner-final-meta05`). Independent source
review passes with strict physical assertions retained. Native execution and
owner verification remain outstanding; these are not full-suite results.

`run_a585af7945173feea82c210e` **failed: 38/972 selected, 37 passed, 1 failed**;
acknowledgement and cleanup are confirmed. All boarding and rotating-jump cases
passed. The remaining jug case reached authenticated surface release and its
first native move, then rejected a capture whose before/after points identify
the same target frame but whose `frameVelocityDeltaPerSecond` is null. Establish
that field's runtime contract before changing the oracle; no eight-interval
placement-stability PASS is established. Managed task log:
`artifact_task_a585af7945173feea82c210e_log_1765dcd20f464a0afc78`, SHA-256
`81dd2a6965b47332a2446c6a8d6a18cea7397e9fb258804a3fe22d5daa3bebf0`.

`LooseItemEntity.finishNativeMoveCapture` computes the velocity delta only for
**different** known before/after frame IDs; null is the ordinary same-frame
contract. The fixture now requires null alongside exact known target-frame
points and coherent finite captured displacement, rather than manufacturing a
zero transition delta. The existing AIR frame-entry case remains the non-null
transition control. All eight drift/survival/conservation observations remain
required; this oracle correction alone does not prove placement stability.

After the oracle correction: main/GameTest compilation and **266/266** focused
tests across **36** suites passed (`build/owner-final09`, XML under
`test-results/`); the same tag union selects **38/972**
(`build/owner-final-meta06`). Independent source review found no assertion
weakening. The eight native placement observations still await execution.

Readiness `run_fa4357da3cf5360ce31f04fc` subsequently passed **38/972 selected,
38 passed**, packaging/provenance and reviews; acknowledgement and cleanup are
confirmed. The owner then reported the connected boarding/input-response blocker
at the top of this document. Native passes did not establish connected acceptance.

### Connected replay recovery correction

`GhostPredictionHistory.reconcile` now retains captured angular/normal evidence
for retained pending suffix inputs on `HISTORY_LOST`. Replay remains forbidden
while the prefix is incomplete. ACK, capacity eviction, intentional skip, real
authority/failure barriers and reset still discard their corresponding evidence.
No ACK is invented and missing/mismatched angular facts remain unusable; no
current geometry is substituted for a historical fact.

The client reuses the existing bounded cadence logger to identify an unusable
angular replay fact **before** barrier cleanup: missing evidence versus supported
frame mismatch, sequence, ACK, pending count and frame identities. It does not
change movement, the server queue, support proofs, angular policy or controls.

Chronological tests use the real history and angular replay selector with an
eight-input ACK lag, continued forward/jump/release inputs, explicit world-zero
and matching supported facts. They require no replay through a gap, retained facts
and no secondary barrier when the prefix settles, plus a stable replay window as
new input continues. **32 focused tests** and main/GameTest compilation pass
(`build/boarding-replay02`); independent source review passes. This covers the
identified recovery defect, not the unlogged initiating barrier or physical
boarding. Any next owner check is limited to boarding/responsiveness; the broader
manual checklist remains paused.

Final focused regression: **267/267 tests across 36 executed suites**, zero
failures/errors/skips, with main/GameTest compilation
(`build/boarding-final03`). Metadata retains the exact **38/972** selection
(`build/boarding-final-meta01`). These are not a new native execution result.
The chronological recovery test and production consumer passed independent
review; the candidate is suitable for the serialized gate and, only after PASS,
one focused boarding/responsiveness check. No physical-contact fix is claimed.
