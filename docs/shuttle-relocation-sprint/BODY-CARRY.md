# Owned human/corpse carry adapter

The pinned SS14 composition audit is verified at `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`MobHuman -> AppearanceHuman + BaseSpeciesMobOrganic`; that organic base composes
`MobBloodstream`, `MobRespirator`, `MobAtmosStandard`, `MobFlammable`, and
`BaseSpeciesMob`. Movement inherits `BaseMob` then `BaseControllable`, supplying
`MobMover`, `InputMover`, and `MovementSpeedModifier`; the kinematic controller
is gravity-affected (radius .35, density 50). The local human remains the
existing flattened root typed character definition; resolved `CharacterData` feeds
`CharacterMovementPolicy` in the existing motor (resolved grounded values accel 20,
walk 2.5, sprint 4.5, friction 20, minimum .005; local policy applies its 1.25
Minecraft speed adaptation and down-friction). This adapter adds no unsupported
prototype components or ship-carry fields and does not retrofit inheritance.
Upstream mover ordering is not claimed as parity.

`SableOwnedBodyMotion` is a narrow release-backed bridge reused by
`GroundedHarnessWorldStep` and `OwnedCorpsePhysics`. Sable 2.0.6 release tag
`ad8b4d377741fbd923f4d863b2cf9e462daff9a6` leaves the current move's
`CollisionInfo.inheritedMotion` in `EntityMovementExtension`; its `EntityMixin`
does not apply that displacement at the move tail for living entities. Its normal
`LivingEntityMixin.travel` return calls `EntityExtension.sable$vanillaCollide` and
then moves the entity. Owned human/corpse paths cancel `travel`, so this adapter
consumes that collision result once immediately after the ordinary native move.
`EntityMovementExtension` is marked `ApiStatus.Internal`; it is the release's only
exposed accessor for this collision result, and no public one-call carry API exists.
Use is deliberately limited to that pinned engine seam rather than replacing or
copying collision internals.

Side-contact diagnostic boundary (feedback-05 geometry-46): pinned
`SubLevelEntityCollision.collide` accumulates the request over substeps and places
`entityBoundsOBB` at `entityBoundsCenter + collisionMotion` before scanning shapes.
The selected-contact `firstCollisions.computeIfAbsent` callback precedes
`tryStepUp`, `collisionMotion.add(maxMTV)` and the following OBB position update.
`maxAABB` retains the exact winning voxel box. Fixture-only observation snapshots
that pre-response actor OBB, selected storage box and selection pose through
`NativeCollisionGeometry`; no borrowed SDK objects are retained. This proves the
snapshot's stage, not that every reported MTV is a safe correction.

Pinned `EntityMixin.sable$collideRedirect` then passes Sable's returned
`CollisionInfo.motion` to ordinary `this.collide(...)`. Final own displacement
can therefore differ on Y because static-world floor clipping follows sublevel
collision. WEST/SOUTH's horizontal evidence currently fails that all-axis match
before its first-plane projection check. NORTH reaches the projection check.
Do not remove the Y gate or admit arbitrary multi-plane corrections on that
observation alone; independent selected-box geometry and full accounting are
required. Generic `1e-6` movement bounds and position/velocity separation remain
unchanged. Native sources: pinned
`common/src/main/java/dev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision.java`
and `common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_sublevel_collision/EntityMixin.java`.

`EntityMixin` retains its previous collision-info reference between moves: it
assigns a newly allocated `CollisionInfo` only in the native collide redirect.
`SableOwnedBodyMotion` therefore snapshots the reference before `Entity.move` and
consumes inherited displacement only when that call leaves a distinct new object.
Moves that do not reach the collision redirect cannot replay an earlier carry.
The carrier delta is not estimated from pose/velocity and does not pass through
the voluntary movement collision result. That result remains bounded to the
requested native own-move acceptance; human posture, slip, and animation callbacks
use the motor's own resulting displacement, while returned authoritative human
state uses the body's actual post-carry world position.

The shared owned-move bridge now applies a one-shot map-velocity delta when fresh
native collision evidence proves a transition between live tracking frames. It
samples `LoadedFrameRayQuery.currentActorPoint` before/after the owner's single
native move, and uses Sable's explicit-frame `getVelocity` at captured storage
points (m/s); the delta is added after the motor, never to accepted own displacement
or external-flight impulse. Unknown/removed frames fail closed. Human jump departure
and actual moving-deck landing are covered by the tagged native transition gate. The
automatic typed-damage corpse owner also has departure and ordinary-world landing
runtime coverage. World-to-native boarding, rotating-frame angular point contribution,
and a direct independent A-to-B swap now have native coverage. Body angular-momentum
transfer and connected prediction remain open. Sable's usual
living hook's velocity, drag, bounce, and normal impact-damage effects are bypassed
by owned travel cancellation. The pinned SS14 `SharedGridTraversalSystem` changes
parent transforms with `SharedTransformSystem.SetParent` preserving world pose;
`SharedMoverController` handles mover velocity/friction and resets angular velocity.
The original transform/mover-only audit was incomplete: Robust physics receives
the parent-change callback and preserves map linear/angular velocity for eligible
bodies (`SharedPhysicsSystem.Velocities.cs:201-268`). See the correction in
`PARITY.md`. Measured translation departure/boarding, angular point contribution,
and A-to-B velocity transfer pass; this is not full frame-transition parity.
Our existing SS14-derived Minecraft motor remains authoritative for own
gravity, friction, and external-flight impulses; carried displacement is not
written to `CharacterMovementEnvironment.ZERO` (the external velocity impulse).
The original translation-only cases author decks at `(240,300,200)` and
`(280,300,200)`; later isolated fixtures use unique coordinates. These gates do not
establish SS14 collision damage/bounce parity, client prediction/camera continuity,
finite ship breathing, or arbitrary vertical/angular rigid-body behavior. The 3D
human movement basis remains a deliberate Minecraft adaptation.
Local resolved human composition and runtime consumer path remain the existing
`CharacterPrototypeTest` and `MovementEligibilityGameTests`; this adapter does not
change those data owners.

Pinned traversal evidence: `RobustToolbox/Robust.Shared/GameObjects/Systems/SharedGridTraversalSystem.cs`,
`RobustToolbox/Robust.Shared/GameObjects/Systems/SharedTransformSystem.Component.cs`,
and `Content.Shared/Movement/Systems/SharedMoverController.cs` at the SS14 revision
above. Input-relative rotation is handled separately in
`Content.Shared/Movement/Systems/SharedMoverController.Input.cs`; see `PARITY.md`.

### Remaining bounded motion gates

Use actual pre/post native tracking-frame identity, not side-contact identity, to
test boarding and grid swaps. SS14's eligible same-map callback preserves map
velocity; the native explicit-frame point-velocity helper is reused at the current
owner boundary after the own motor, not as carried displacement, walk/slip acceptance
or an external impulse.

Require translating and rotating support, walking/jumping off the deck, landing and
frame change for real bound Humans and damage-produced corpses, plus unchanged
world-floor counterparts. Establish whether the engine already transfers velocity
before adding a correction. Removed/unknown frames, 3D angular/facing behavior and
connected replay remain explicit questions; do not treat the two translation cases
as answers to them. The first nonrotating bound-Human departure/landing test now
selected and passed 1/1 (combined with prior body carry: 3/809 selected, all passed);
it measured 0.9712 m/s horizontal carrier contribution and exact owner result agreement
within 0.02 m/s. `test assemble` passed 3,158 JUnit tests. A subsequent full native
run selected all 809 and passed 801; eight existing first-join authority fixtures
failed with the shared “normal Survival first join did not enter lifecycle spectator
control” gate. Preserve that log for separate lifecycle triage; no such failure was
reported by the focused 3/809 run. The first-join handler now logs named fail-closed
phases while preserving disconnect behavior. A fresh full run after instrumentation
passed 809/809, so the eight prior failures did not reproduce; their root cause is
still unconfirmed rather than attributed to movement or declared fixed.

The automatic corpse transition gate uses exact-entity NeoForge `EntityTickEvent.Pre`
and `Post` observers around the real `LivingEntity` tick, whose medical-corpse TAIL
calls `OwnedCorpsePhysics.step`. It creates the corpse through typed 200-blunt damage,
then removes a bounded 3×3 owned deck footprint through normal plot block writes.
Fresh same-tick pre/post collision identity proves tracking-frame departure. Native
old-point velocity was `(1.792, ~0, 1.792)` m/s; the corpse own pre-velocity was zero
and owner post-velocity matched the old carrier contribution within 0.02 m/s. Two
subsequent airborne owner ticks had horizontal error 0.051 and 0.050 m/s (below the
0.1 tolerance for own motor drag), and the fixture passed with the same typed corpse,
Mind binding and hands owner through world-floor landing. Focused carry/transition
selection passed 4/810. No broad 810-case suite was run for this bounded runtime gate.

Correction to the airborne interpretation above: raw pre/post velocity deltas include
the corpse owner's ordinary 0.91 horizontal air retention and are not an inheritance
replay oracle. The corpse fixture now compares against that existing retention rule;
the initial departure still independently requires one full measured carrier impulse
within 0.02 m/s. A separate living-body jump fixture uses the independently captured
takeoff point velocity as its frozen airborne reference. These source corrections
have compiled and passed focused JVM tests; fresh native validation remains pending.

A separate real-bound-Human rotating-frame departure gate verifies that captured SDK
point velocity includes angular motion at the actor point. Measured native COM linear
velocity was `(0.491, -3.252, 0.491)` m/s, angular velocity `(0, 0.7857, 0)` rad/s,
and SDK point velocity `(0.222, -3.252, 1.729)` m/s: a 1.267 m/s horizontal
point-vs-COM contribution. The fresh tracked-frame-to-world jump committed the full
point velocity with zero measured horizontal error. This is not an actor-yaw or body
angular-momentum transfer claim. Focused carry/transition selection passed 5/811;
the consolidated full native run passed 811/811, and `test assemble` passed all 3,158
JUnit tests.

A world-to-native boarding gate was then added at a real bound Human's ordinary-
world start (0.15 blocks above the deck). Fresh collision info changed final tracking
from null to the live plot UUID, with actual deck support. SDK point velocity was
`(1.380, ~0, 1.380)` m/s; the corrected owner commit was its negative, retaining the
measured pre-step map-horizontal velocity with zero error. Focused carry/transition
selection passed 6/812. `test assemble` again passed 3,158 JUnit tests. The unfiltered
812-case run in the reused directory had 804 passes and eight existing normal-first-join
authority fixture failures. All eight now identify `phase=startup-state-not-enrollable`
with `snapshotState=DEFERRED`, `contextAvailable=true`, exact listed Survival players.
The existing first-join policy at `LifecycleFirstJoinHandler` intentionally admits
only `EMPTY`/`UNINITIALIZED` first accounts. The pre-run startup log showed
`DEFERRED profiles=8`: those fixtures reused a save containing eight prior offline
claims, so their random new UUIDs correctly failed enrollment. The earlier 809-case
failure log also began `DEFERRED profiles=8`; do not attribute other uninspected
intermittent runs to this cause. `LifecycleHandActorFixture.start` now checks the
precondition and reports state/profile count before creating or registering an actor.
It does not clear claims or alter production policy. Use a new disposable GameTest
directory for first-join cohorts. Preserve `build/shuttle-transition-final-full-native.log`.
With a new directory, startup was `UNINITIALIZED profiles=0`; a first 812-case full
run then had one serving-dose assertion (`4.5` units instead of `5`). The
eight `beaker`-tag fixtures passed in a separate fresh directory, and a second fresh
unfiltered 812-case run passed 812/812 with `UNINITIALIZED profiles=0`. Its log is
`build/shuttle-boarding-full-812-retry-native.log`; build/JUnit evidence is
`build/shuttle-boarding-fresh-build.log` (3,158 tests, 0 failures). No production
lifecycle policy or saved profiles were changed.

The next gate directly swapped a living bound Human from one independently authored
native frame to another across a 0.25-block gap. Fresh per-owner collision evidence
proved A UUID→B UUID (not world or a third frame), both frame poses moved about 1.20
blocks, and actual deck support plus body/Mind/hands identity remained intact. The
resolved-Human pure motor baseline was `(3.125, 0, 0)` m/s; measured old/new SDK
point velocities were approximately `(0, 0, 1.358)` and `(0, 0, -1.358)` m/s. The
shared correction committed `(3.125, 0, 2.716)` m/s, preserving the motor baseline
plus old-minus-new frame contribution with zero measured error. Focused carry/transition
selection passed 7/813; a new-save full run passed 813/813. `test assemble` passed
3,158 JUnit tests with no failures. Artifacts: `build/shuttle-grid-swap-native.log`,
`build/shuttle-grid-swap-full-813-native.log`, and `build/shuttle-grid-swap-build.log`.

Earlier translation-only native tests are in `SableOwnedBodyCarryGameTests`. Focused execution selected and
passed 2/2 of 795 tests: actual bound-human standing/walking during moving-deck
translation and actual typed-damage corpse translation. The first attempt exposed
that the human needed an ordinary bounded native motor settling interval to reach
deck contact; the retained failed log is `build/shuttle-body-native-first.log`, and
the corrected focused run is `build/shuttle-body-native.log`. These headless checks
alone did not establish connected client continuity, jumping/deck departure momentum,
or collision-damage parity. The later departure gate above supersedes that limit
only for the tested nonrotating Human jump/landing case.
