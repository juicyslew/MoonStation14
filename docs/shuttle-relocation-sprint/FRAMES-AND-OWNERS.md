# Ship frames and current owners

## Feedback-04 source findings (acceptance pending)

Pinned Sable `SubLevelEntityCollision.collide` samples the own-motion request
(`min(10,max(1,(int)(length / .015625)))`), not the frame sweep. An idle
support probe can therefore get one sample while a hull travels nearly a metre.
The scoped `TrackedCollisionSubsteps` adaptation increases this count using the
actual SDK intersecting candidates and copied last-to-logical actor-point motion.
It never lowers stock sampling or exceeds ten, and excludes current-geometry
replay/default STOCK paths. Its only live caller is the owned `Mob` bridge;
Sable's subsequent local-Player override is not in that domain.

`CurrentFrameCollisionScope` now has observational, bounded ordered selected-MTV
and applied-response traces. The first plane per frame is not proof of the final
compound response. Trace completeness is explicit; truncation cannot become an
acceptance certificate. Native movement/carry and generic motor bounds are not
changed by tracing. Native fixture execution verifies complete traces and the
narrow floor-geometry channel below; connected acceptance remains open.

The complete negative-X fixture trace explains a positive floor correction during
an idle downward probe. `FloorContactCorrectionEvidence` is a pure movement value;
its native provider lives in `SableOwnedBodyMotion`, not the motor. The provider
checks fresh opted-in movement, exact live before/after frame, immutable selected
block/state through existing loaded getters, complete finite upward-only response
and matching measured pre-carry/native motion. It admits zero bounded voluntary Y
plus upward-dominant positional floor correction, including oblique XYZ; velocity
never includes that offset. Each selected MTV's normalized Y chooses the exact
pinned SDK redirect/original-vector branch, and the complete reconstructed expected
sum must match native motion within one global residual. Side/step/ceiling/mixed-frame
or unexplained response is refused. This narrow domain is not a compound-wall
solver certificate. Motion-6 passes both furnished Human strong impulses.
Reconnect-15 passes an actual post-Ready movement interval with normal per-tick
neutral intents, the same body/Mind/items and real outbound frame snapshots.
Bounded collision displacement is reused for server/client own animation and slip;
raw positional correction remains only in actual geometry/position diagnostics.

Strong-cargo height is not a fixed local-feet Y invariant on a tilted deck: the
upright native box requires clearance over its footprint. The test reuses actual
`LooseItemEntity.NativeMoveCapture` plus a copied pre-cutoff native OBB/frame pose,
matches the exact fresh collision-info identity, and derives vertices with SDK
math in private scratch. It checks loaded solid deck cells, `.02` unexplained
horizontal drift, real floor/tracking and both registered filled roots. Certified
floor geometry is accumulated from measured pre-carry displacement only after
complete ordered native trace admission, every selected row's loaded authored-deck
state proof, and own/native motion consistency. Raw tilted-foot displacement is
still recorded; subtracting raw carrier motion is not permitted. Physical root
comparison distinguishes the stock squared-`1e-8` carrier cutoff from vanilla
squared-`1e-7` tiny own-position-write suppression using measured pre-carry own
motion; neither is permission to add an extra physical move or widen motor bounds.
An exhausted fourth SDK correction can leave a finite plane remainder after its
world-up redirect. The height test derives that remainder independently from the
last selected/applied row and actual copied-pose deck-up; observed remainder must
match at the unchanged `1e-6` plus coordinate ULP budget. No gameplay bound or SDK
iteration count changes. Cargo-22 passes 1/859 selected; independent source review
finds no concrete fault. Full-31 also passes all 859 cases, including this fixture.
The stock diagnostic scope reuses the existing trace but
never enables character yaw, geometry replay, micro-carry retention or sampling;
capture is default-off and enabled only for the two fixture roots.

For future exterior-face diagnosis, the existing tracked-character yaw wrapper
in `SableOwnedCollisionPoseMixin` samples before native OBB orientation is updated
each substep. `observedSdkYawSource` is first-only and `selectedOwnedYawPose` is
last-only; neither proves per-substep identity switches. Extend the existing
bounded exact-entity scope if a prolonged test reproduces the fault. Distinguish
in-flight tracker UUID at that callback from the selected candidate frame UUID in
the collision trace; never infer a new grounding/yaw policy from aggregate flags.

Upstream pins were independently verified: `MobHuman` inherits
`AppearanceHuman + BaseSpeciesMobOrganic -> BaseSpeciesMob -> BaseMob ->
BaseControllable`, resolving a gravity-affected `KinematicController`, circular
fixture radius `.35`, density `50`, MobMask/MobLayer and InputMover/MobMover/speed
modifiers. Robust position constraints can correct penetration independently of
mover velocity. Sources: SS14 `Resources/Prototypes/Entities/Mobs/base.yml:10-58`,
species definitions, `SharedMoverController.cs:187-344`; pinned Robust
`SharedPhysicsSystem.Solver.cs:129-160,729-779` and `Island.cs:818-884`.
This supports separating intent from collision correction, not blanket trust of
SDK displacement or a claim of identical Minecraft/SS14 solver ordering.

Loose client cargo skips vanilla ItemEntity physics. Sable's nonliving packet
branch projects plot coordinates to a world snapshot but does not cache a local
point for continuous projection; stationary plot-local cargo need not receive a
new positional packet on every moving-frame tick despite update interval one.
The packet-anchored presentation fix retains exact packet frame identity/local
coordinates and reuses `LoadedFrameRayQuery.projectActorPoint` each client tick.
It neither simulates item physics nor forces native tracking. Unknown storage
packets retain the last anchor; unknown/unloaded frame projection freezes safely.
Ordinary-world packets clear it. The shared pinned-SDK post-merge plugin supplies
metadata before Sable converts packet coordinates, without another packet decoder
or cancelling the SDK handler. Actual ModLauncher target loading and merged export
in `build/client-shuttle-cargo-proof-29/` verify the callback precedes `sable$lerp`'s
coordinate conversion. Live packet delivery, connected cargo presentation and
camera continuity remain unverified. Initial-spawn anchor/tracking admission is not
proven by the move/teleport hook; newly dropped cargo requires connected coverage.
The owner prohibits further client launches.

## Baseline at `5c09e42`

The current possessed-human movement owner is **`GroundedHarnessWorldStep`**.
The historical default-disabled experimental movement handoff does not describe
this live path. Native `Entity.move` remains the collision seam. Owned living
bodies/corpses cancel vanilla `LivingEntity.travel` in
`LivingEntityMindHarnessTravelMixin`; Sable living carry at normal travel return
cannot be assumed to execute there. Carry must be integrated once through the
current owner, with voluntary movement kept distinct from inherited motion.

## Frame contract

- Body physics, camera and reach operate in world space.
- Hull blocks, block entities, finite gas, cables and pipe records use stable plot
  storage coordinates. Same-dimension flight changes pose, not every cell key.
- Sable owns plot/pose/physics identity. Do not create a competing grid backend.
- Use one narrow engine-backed query boundary for current world-to-plot samples,
  projected loaded ray hits and frame identity. Return enough hit identity for
  server revalidation; keep loaded-only policy. Exact APIs must be demonstrated
  against the required release, not invented from the wiki.
- Initial authored creation places cells directly in their plot and initializes
  current owners there. This proves neither import of existing dimension SavedData
  nor splitting/dimensional transfer. Those remain separate lifecycle gates.

## Existing services to extend

| Owner / seam | Relevant paths and obligation |
| --- | --- |
| Body movement / snapshots | `player_body_control/character/GroundedHarnessWorldStep`; `mixin/LivingEntityMindHarnessTravelMixin`; `player_body_control/client/GhostControlClient`. Preserve status/posture/slip/pull/animation callbacks, identity/epoch and prediction/correction. The spectator carrier is not another physical passenger. |
| Loaded targeting | `hands/live/BodyItemTargetProbe.LoadedGetter` uses `getChunkNow`; unrestricted vanilla clipping is not an acceptable replacement. Project at the shared boundary; preserve obstruction, exact root identity and reach revalidation. |
| Loose cargo | `entities/LooseItemEntity.tick` uses native movement with settled early returns, loaded-sweep guards and contact/friction; `hasFloorSupport` is a separate direct-block query. Make support/carry frame-aware without treating carried motion as impact or disabling settled optimization. |
| Atmosphere / exposure | `atmos/world/` and `atmos/exposure/ExposureAirRouting`; `BarotraumaSystem` currently samples world eye block positions. Both reads and finite inhale/exhale/thermal writes must reach the correct owned plot cell. Claimed vacuum/unknown handling remains unchanged. |
| Power / pipes | Existing graph/runtime, plot chunk access, pipe SavedData/lane IDs and paid grants. Verify native plot chunk hooks and loaded lookup before changing solvers; stable storage does not automatically prove attachments/indexing. |
| Hands / inventory / actions | Existing authority/request/access, storage-token and timed-action services. Preserve identities and use frame-correct access; no copied inventory transfer or new possession handshake. |
| Containment | Existing `BodyContainmentService` and actual holder air/position/camera owners. An ordinary moving cabin is not disposal containment. |

Paths above are under `src/main/java/com/juicyslew/moonstation14/`, with subsystem
paths under `ms14/` unless otherwise stated. Consult `Instructions.md` §4 and
their handoffs before extending a consumer.

## First proof sequence

1. Exact engine APIs and real plot-authored cabin ticking.
2. Shared loaded frame queries; body carry/snapshots and query revalidation.
3. Settled cargo/corpse and hatch/deck transitions with real ownership.
4. Live finite atmosphere/power/pipe state and persistence/edit/split policies.

Current source research is not runtime compatibility or connected acceptance.
Do not claim atmosphere integration merely because an eye sample is transformed.

## Release-backed M1 API findings

Audit basis: Sable 2.0.6 tag `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`,
not the newer planning pin. `EntityMixin` delegates collision to
`SubLevelEntityCollision.collide`; its result includes inherited displacement.
Non-living entities apply that displacement at the native move tail, while
living entities use `LivingEntityMixin.travel` return and associated inherited
velocity/drag bookkeeping. Our owned human/corpse travel cancellation bypasses
that normal living path. No public one-call carry helper was found in the audited
paths. Measure actual native displacement first; do not blindly add ship velocity
again or copy the engine's collision/friction implementation. `SubLevelHelper`
velocity is documented in m/s; inherited displacement is not that velocity.

Two existing native-move consumers require the same policy: human
`GroundedHarnessWorldStep` and corpse `OwnedCorpsePhysics` (driven by
`LivingEntityMedicalCorpseMixin`). Coordinate their existing tick guards with
`ExternalBodyFlight`. World-space snapshots remain appropriate; no new possession
protocol is indicated by this research.

`BlockGetterMixin.clip` projects only `Level` getters. It does not project our
custom loaded getter, and no public loaded-only projected-ray callback was found
in the audited release. A normal `Level.clip` is not established safe as a
loaded-only replacement. Use the engine pose and loaded candidate lookup through
one narrow shared boundary, with ordinary vanilla loaded traversal and nearest
world obstruction retained. A hit needs storage position/local face, world hit
point and frame identity for server revalidation. `BodyInteractionAccess.inRange`
also uses `level.clip`; `BodyItemTargetProbe.withinInteractionReach` currently
assumes an ordinary world-space eye/target. Both are shared integration points,
not reasons to duplicate reach/LOS checks per machine.

Implementation must verify relevant API visibility/internal annotations and
document any smallest necessary pinned-engine boundary. These findings are
planning evidence, not yet a body-carry or loaded-ray runtime proof.

## Owned carry follow-up (release source)

The audited living hook is explicitly at `travel` RETURN. It reads the current
native move's `CollisionInfo.inheritedMotion`, resolves that displacement through
`EntityExtension.sable$vanillaCollide`, applies accepted movement via `setPos`, and
updates `LivingEntityMovementExtension.sable$getInheritedVelocity()` (m/t). It also
has separate collision damage/bounce and inherited-velocity drag behavior. Simply
moving the body does not prove those additional effects.

Use collision data already produced by native `Entity.move`, not a second
ship-velocity estimator. A shared owned-move adapter can serve the existing human
and corpse consumers; keep its consumption associated with that exact move and
preserve the current SS14 motor/friction ownership. The current movement kernel
expects accepted **own** displacement; returning carried movement as voluntary
collision acceptance can exceed its bounds or pollute slip/posture/walk animation.
World-space state/snapshots must nevertheless include the body's actual final
position. Verify the collision-info lifetime and departure momentum in native
tests before implementing this policy; do not copy the engine's full living hook.

Minecraft positions/current movement snapshots stay double-valued; matching
Sable's f32 internals is not itself an optimization requirement. The owner's
near-base/5,000-radius operating assumptions and separate distant/FTL dimensions
are recorded in `EXECUTION.md`.

## Loaded projected query reuse decision

Release-source follow-up confirms that the non-`Level` branch of
`BlockGetterMixin.clip` invokes its private original clipping implementation.
Therefore a shared **loaded-only non-Level getter** can call ordinary `.clip`
for both terrain and inverse-projected storage rays. Do not copy Sable's private
traversal/shape/fluid implementation or replace the getter with unrestricted
`Level.clip`. Extract/extend `BodyItemTargetProbe.LoadedGetter` for the actual
current callers, retaining chunk ceilings, shape-query margins and unknown reads.

`ServerSubLevel.getLevel()` is the parent server level. Inverse pose projection
produces global storage coordinates; read admitted plot chunks through parent
`getChunkNow(globalX, globalZ)`, without adding the plot centre again.
`LevelPlot.getChunk` instead accepts local chunk coordinates via `toLocal`; do not
introduce a competing storage lookup when the existing global getter suffices.
Use the engine's intersecting loaded-sublevel query and pose provider rather than
own broadphase/transform math. Candidate storage reads must remain within their
own admitted plot and already loaded chunks.

The query must distinguish known hit, known miss and unknown. A ship hit retains
storage block/face, **world-space hit location** and engine UUID/frame identity;
compare all terrain/ship hits by world-ray distance. Unknown potentially
intersecting storage refuses the query rather than becoming a miss. Existing
body authority, reach, item ordering and exact target identity remain their current
owners, with frame revalidation before mutation. This is an implementation plan,
not yet a loaded-query runtime proof. Required cases include rotated storage hit,
nearer terrain obstruction, missing projected chunk and stale engine identity.

## Cargo and frame-transition follow-up

Sable already applies nonliving inherited movement at `Entity.move` TAIL. The
loose owner must not apply a second living-body carry adapter. Its total resulting
world displacement is not accepted own movement: `CollisionInfo.motion` precedes
ordinary-world clipping, and `inheritedMotion` itself is collision-clipped before
application. Subtracting raw inherited motion or rerunning collision is therefore
not a correct recovery of own acceptance. A loose-only observation now captures the
accepted entity position at the native move boundary before nonliving carry; it does
not rerun the solver or add a second carry adapter. The ordinary loose tick and
pull-correction consumer use this own displacement for contact classification.
Static settled 10-tick support checks remain unchanged; tracked settled cargo still
requires current native support/carry rather than stale world-floor reads.

Runtime proof on the pinned Sable 2.0.6 release: the Sable movement mixin is priority
1100; this observer must use priority 1000 because a trial at 1200 ran after the
carry callback. At 1000, its TAIL snapshot for a real registered beaker captured
requested/accepted own X `0.15` while the post-move total was `0.24277` (fresh
collision-info reference, exact same owner tick). A normal owner tick with zero
requested travel captured a newer known-zero own displacement while total world
displacement remained nonzero. A registered filled jug's clear-deck own X `0.75`
likewise remained live with exact ownership/reagent state despite `0.84277` total
world X displacement. Those initial tests establish the callback boundary and impact
classification; the support follow-up below separately proves current support and
settled-frame cadence, not velocity transfer. The observer captures at TAIL when reached and idempotently at RETURN for
early exits; each move clears prior capture, and callers reject missing/stale
sequence results rather than treating them as zero. `CollisionInfo.verticalCollisionBelow`
and tracking field names alone remain insufficient proof of support for a zero move.

The follow-up support gate now reuses that own-move observation and
`LoadedFrameRayQuery.currentActorPoint`'s exact live plot/loaded-storage checks.
An ordinary downward owner move can settle only with fresh clipped own-Y and a
validated tracked frame. A settled tracked root issues one collision-resolved
`0.02` downward support probe each owner tick; native nonliving TAIL remains the
sole carrier. Lost/unknown support wakes the root without a second move that tick.
Ordinary-world settled roots keep the existing 10-tick support cadence. Runtime
coverage used real registered filled jug/beaker roots: both settled on the same
native deck, retained zero own velocity and stable deck-relative coordinates over
six native-motion ticks while moving in world space, then the jug accepted `-0.02`
after its owned 2×2 floor removal and `-0.04` on the next gravity tick; its tracking
returned to world while the beaker remained supported. This does not implement
saved frame ownership, or a new cargo profile policy.

Cargo departure now shares the body owners' `SableFrameVelocity.pointDeltaPerSecond`
calculation. The loose capture records a loaded current actor point before move and
resolves the final point after Sable's nonliving carry TAIL; only a fresh collision
reference and a real tracking-UUID change permit the SDK point-velocity difference
to be committed. The value remains m/s until applied once at `0.05` blocks per tick.
Unknown/removed/unloaded tracked endpoints produce no correction; a valid world point
contributes zero. Cached Sable 2.0.6 bytecode (`EntityMixin.sable$moveInject`) only
copies collision flags and invokes the wrapped `setPos`; it does not mutate entity
velocity. Its `ItemEntityMixin` contains the item physics tick gate, which loose cargo
does not invoke because its owner tick replaces `ItemEntity.tick`.

The fresh registered-beaker departure run (`shuttle-cargo-transition`) removed its
owned support after actual native settling. On sequence 3, the normal `-0.02` support
move freshly changed tracking from the live frame UUID to world; SDK point velocity
was `(3.7011353, 0.0000148, 0.0000038)` m/s and the shared delta matched it. The same
tick changed cargo velocity from zero to that value with zero horizontal error; the
next fresh world tick showed horizontal `0.995` decay and did not replay the frame
delta. Root, token and filled-beaker contents remained intact. This is translation
departure proof, not rotating cargo, boarding, frame-to-frame cargo transfer,
vertical parity, persistence, or a claim about camera/network prediction.

Pinned Robust physics preserves map velocity across eligible same-map parent
changes, including kinematic controllers and dynamic bodies; the initial mover-only
audit missed this callback. See the corrected `PARITY.md`. The translation adapter
does not imply full-body rigid rotation, and copying Sable's living drag is not an
equivalent loose-cargo policy.

The follow-up cargo frame-basis matrix reused that shared SDK-point calculation and
normal registered filled-beaker roots. Rotating-frame departure measured COM
`(0.498, -1.637, 0.498)` m/s, angular Y `0.7964` rad/s, and old actor-point
`(-0.019, -1.637, 1.912)` m/s; the point-vs-COM horizontal contribution was
`1.506` m/s. Fresh frame→world departure matched the point velocity with
`2.2e-16` m/s horizontal error; its next world tick showed only the existing `0.995`
horizontal damping. First world→frame boarding measured the new point
`(1.380, ~0, 1.380)` m/s and applied its signed negative correction once; direct
independent-frame A→B crossing measured old/new Z point speeds `+3.701/-3.701` m/s
and applied `+7.402` m/s to own Z, with zero measured basis error. All were fresh
single native collision moves with exact final tracking UUIDs and preserved cargo
ownership/reagents. These are horizontal point-velocity proofs, not cargo yaw/spin,
vertical transition policy, persistence, or client/network prediction.

Release bytecode establishes the tracking producer: `sable$collideRedirect`
captures prior tracking/velocity into fresh collision info, sets entity tracking
when the selected frame has below-support collision, clears a null choice for
nonplayers, then performs ordinary-world collision. Additional vertical clipping
can clear entity tracking again; removed tracking frames are also cleared. Thus
final entity tracking, not merely `info.trackingSubLevel` or first side contact, is
the transition candidate. This producer runs inside normal collision even though
owned living travel RETURN is bypassed; move TAIL itself does not add a living
velocity correction. Require fresh per-move evidence before interpreting a null
final tracking result as departure; no-collision or removal paths are not equivalent
to a proved supported-frame → map transition.

Exact release-bytecode reuse target: public
`Sable.HELPER.getVelocity(Level, SubLevelAccess, Vector3dc, Vector3d)` computes
server native linear velocity plus angular point velocity, in m/s. **Its point
argument is global storage-space**, not world space: it applies the frame pose
before taking the offset from native COM. Inverse-project a world point once before
calling this explicit-frame overload. Its client path derives velocity from
last/logical pose times 20. Do not hand-code another point-velocity estimator or
assume `RigidBodyHandle` exposes a point-velocity method (the inspected release
does not). This establishes API/units, not transfer runtime acceptance.

## Connected prediction timing (unaccepted)

`GhostControlClient.predictCharacter` wraps native `body.move` in the own motor;
it does not overwrite the actual entity position with the motor result. Its current
animation measures total before/after entity displacement. The server bridge is
therefore a reuse candidate, but animation must continue to exclude carrier motion.
Snapshot reconciliation sets/lerps authoritative world pose, then replays pending
intents inline; several moves can execute in the same client tick.

Release-bytecode inspection confirms `ClientSubLevel.tick` copies logical to last
pose, advances the snapshot interpolator and updates logical pose once per tick.
`renderPose(float)` separately interpolates last→logical; the container advances
shared interpolation before ticking sublevels. No per-entity carry-consumption
record was found in those paths. Fresh collision-info identity is consequently not
proof of distinct platform displacement across replayed moves in one pose interval.

Do not naively enable repeated carry in replay, mutate global engine pose state or
create a second camera owner. Connected body/camera evidence must establish timing,
drift/correction and replay semantics before acceptance. Preserve world-double
snapshots, own velocity, session/epoch/ack and medical/no-input authority boundaries.

## Finite atmosphere consumer index (M2 prerequisite only)

Actual breathing is `LungSystem.tickIfDue`, not the no-op
`RespirationExposure.exposeTwoSeconds`. Non-contained lungs strictly sample the
physical eye cell and commit via `AtmosphereService.exchangeBreath`; barotrauma and
`BodyTemperatureSystem` sample that eye cell too, with temperature energy committed
through atmosphere ownership. Disposal containment already routes through
`ExposureAirRouting` and must retain precedence.

`AtmosphereService.sample` is the physical loaded/passable read: unknown aborts,
exterior is ambient, finite claims use owned gas. A finite claimed vacuum is not
ambient. Presentation `readAtmosphere` can be provisional and is not an exposure
source. These APIs currently receive BlockPos without ship-frame resolution.

M2 must resolve the physical point once through the engine-backed loaded frame
boundary, retain/revalidate its exact finite owner for exchange/energy commits and
reuse the existing solver. Do not duplicate transformations in lungs, barotrauma
and temperature, model ship air separately, or seed world air from moving storage.
Existing finite-room endpoint tests are starting points. M0 storage gas conservation
and current body translation do not establish living inhalation/exhalation,
temperature, breach leakage or persistence.
# Living-body physical ownership (feedback 05)

Bound custom characters retain physical existence without fresh intent, while
parked in Creative or OFFLINE. `OwnedLivingPhysics` dispatches only bound custom
characters through the existing `GroundedHarnessWorldStep`; it is not another
motor. Ordinary NPC leases retain their existing control/native owners.

The per-body transient marker (`MindControlledMob` / `MobMindHarnessOwnerMixin`)
keys one physical attempt to exact level identity and server tick count, not world
game time. It is consumed before native movement, including a later rejection or
partial failure. A duplicate common-step call returns current authoritative state,
not a second move or an ownership-ending rejection. Externally advanced motion
consumes that budget before settled-flight tracking is cancelled.

`OwnedCorpsePhysics` participates in that same marker. A still-alive Critical
body can otherwise move in the medical entity-tick callback and again in active
player control. Medical native attempts, including partial failures, consume the
same budget; skipping corpse-eligible bodies in fallback alone is insufficient.

Ordering matters: Sable container ticking is at `ServerLevel.tick` HEAD; level
tick/post precedes connection/player ticks. Fresh player input moves first.
`MoonStation14.onServerTickPost` at HIGHEST then dispatches unconsumed fallback
physics and explicitly publishes routine snapshots afterward. The original
level-post fallback was wrong: it could starve active input. Fixture observations
use bounded LOWEST-priority server-post samples after physical simulation.

Vanilla travel cancellation and its tether hook share the physical-owner predicate.
Custom movers receive tether effects through their existing kernel, never twice.
The snapshot publisher must reach `sendCharacterSnapshot` even for an unknown
pose: that producer owns unsafe-pose suspension and packet suppression. A
pre-publication safe-pose filter bypasses the hold/rebase transition and is forbidden.
Begin/Ready and immediate recovery boundaries remain distinct.

# Current native-frame snapshot substrate (C1)

Character snapshots now optionally carry the exact native frame UUID and the
authoritative body's storage-space point, alongside ordinary world position and
velocity. Producers use `LoadedFrameRayQuery.currentActorPoint`; unknown tracking
suppresses that snapshot rather than mislabeling it world-frame. Ghost snapshots
remain frame-less. This metadata is descriptive only and never sets SDK tracking.

`LoadedFrameRayQuery.projectActorPoint(Level, UUID, Vec3)` admits the exact live
Sable sublevel, its own plot, and a known `LevelPlot.getChunk(plot.toLocal(global))`
on clients before projecting through `logicalPose`; on servers the existing
`getChunkNow` plus FULL-residency guard is retained. It does not force chunks,
scan frames, or use render interpolation. Native pose interval identity is an
explicit per-controlled-body substrate: immutable SDK-baked matrix values for
both `lastPose()` and `logicalPose()`, scoped by body epoch and UUID. It is not
used as a render/game-time key.

## C2 bounded client adaptation

`GhostControlClient` now interprets frame UUID/storage only for grounded CHARACTER
snapshots, projects current `logicalPose` once before replay, and seeds an explicit
velocity basis from that snapshot point. The client's existing `SableOwnedBodyMotion`
bridge reuses native `Entity.move` and fresh collision-info identity; replay
suppresses stock SDK inherited motion while retaining own collision prediction.
The bridge measures accepted own displacement immediately after `Entity.move`,
then optionally applies that same move's fresh `CollisionInfo.inheritedMotion`
through `sable$vanillaCollide`. It never combines a projected carrier estimate
with the own request or subtracts raw carrier motion after collision. A live
predicted tick may admit that native carry once per captured current-pose interval;
a fresh collision transition updates the local velocity basis to its actual final frame.
Unavailable framed snapshots pause owned movement and skip outgoing intents in
the existing bounded history; a later accepted snapshot can resume. No packet
writes native tracking or parent identity.

This is current-pose alignment plus current-geometry replay, not historical
rollback of shuttle transforms or collision geometry at server ticks. Departure
under latency, rotation/camera behavior, and real client known-chunk residency
remain connected-manual gates. Server-owned Human/corpse and nonliving cargo
movement retain their existing owners and default bridge behavior.

The pinned-SDK `SableOwnedCollisionPoseMixin` adaptation scopes only opted-in
owned client moves and substitutes the exact frame's current `logicalPose()` for
the `lastPose()` read inside `SubLevelEntityCollision.collide()`. The scope is
nested-safe and exact-entity; it never mutates shared SDK poses. Stock server,
corpse, cargo, untracked/world movement, and a newly admitted live SDK interval
continue through the SDK's native interpolation and carry. Replay and a live
already-consumed interval use current geometry, so declining the outer inherited
carry alone is not mistaken for consuming the SDK's internal collision interval.

Separately, Sable 2.0.6's `entities_stick_sublevels.EntityMixin` caches remote
plot packet coordinates and projects them at `Entity.tick()` return, then writes
SDK tracking identity; its `LivingEntityMixin` carries custom lerp and rotation
state through `sable$setupLerp`/`sable$applyLerp`. For the exact committed,
camera-owned CHARACTER Human only, the client mixin stops the cached tick
projection and its corresponding remote tracking writes, hides the plot cache from
rendering, and stops the SDK/vanilla remote lerp handoff. The stale cache can be
discarded without activating its null-cache tracking-clear branch. SDK collision-derived tracking,
authoritative snapshots, and genuine teleport authority barriers remain intact.
Observers, other remote entities, server movement, and medical sessions that do
not own the tracker retain SDK behavior. This remote-interpolation fix is
distinct from current-pose collision geometry; the remaining connected trace is
needed to establish whether both contributed to the observed anchor flicker.

Do not add a second owned-body render projection when hiding the packet cache.
Pinned `entity/entity_rendering/LevelRendererMixin.renderEntityOnSubLevel` already
handles natively tracked world-space bodies independently of `sable$getPlotPosition`:
it inverse-projects old/current body positions through last/logical poses,
interpolates locally, then projects once through `renderPose(partialTick)`.
Orientation uses the separate containment/riding helper, not that packet cache.
The similarly named `entities_stick_sublevels/EntityRenderDispatcherMixin` is
debug hitbox code. This establishes source reuse, not moving-client acceptance.

Pinned upstream source: Sable 2.0.6 `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`,
`common/src/main/java/dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/EntityMixin.java`,
`LivingEntityMixin.java`, and `SubLevelEntityCollision.java`.

Real native boundary GameTests exercise clipping against an ordinary-world stone
wall with the Human nearly tangent to its face: this fixture measured SDK inherited
X `0.0780`, actual clipped carry `0.0500`, and accepted own X `0`. Three replay
moves at one unchanged pose interval observed fresh SDK carrier metadata each time
but summed only own movement (`0.03` total). An authority projection to another
frame is checked against native live identity without writing Sable tracking state.
These headless tests do not prove connected-client networking, camera, or client
chunk-residency behavior.

## Owner-input contact and correction recovery

The pure character motor keeps strict bounded-displacement checks by default.
The shared native owned-move adapter normalizes signed/overrun residuals up to
`1e-6` blocks; this is a local adapter allowance, not a verified SDK skin constant.
Companion entity SAT uses doubles. Actual entity coordinates and native carry
remain unchanged by residual normalization. Larger unexplained wrong-sign,
non-finite, or materially over-budget movement is not normalized. If a committed
live CHARACTER step still rejects after its one native move, the exact session
settles that input, zeros unsafe motor velocity, and sends a higher movement
rebase revision when the current body/frame point is valid. The client treats a
new revision as authority-only, flushes old replay history, and clears the
prediction-stopped latch. A client-only failure also resumes through a no-replay
barrier when fresh known authority acknowledges its failed input, even without a
server revision change. Unsafe/unknown pose context holds movement and waits
for a known point without projecting it as world coordinates. A healthy
uncommitted Begin/Ready handshake is retransmitted on a bounded interval instead
of disconnecting solely for slow body/catalog readiness.

This is a bounded local collision/reconciliation recovery adaptation, not an
SS14 zero-velocity algorithm or historical physics rollback. Earlier terminal
failures left durable ACTIVE rows and in-memory recovery markers. Normal login
uses independent loaded-body identity/ownership proof, expected-row CAS to OFFLINE,
and existing reconnect services for these abandoned claims, including safe fresh
registry hydration. No historical fault reason is inferred and conflicting or
unavailable evidence remains fail-closed; see `RECONNECT.md`.

### Character contact evidence and SDK pose reads (runtime acceptance pending)

`HorizontalContactEvidence` admits full or partial horizontal clipping along one
real native side-contact normal, including legitimate cross-axis movement, and preserves
tangential own velocity. It requires the exact request/result pair and the existing
`1e-6` residual limit. `SableOwnedBodyMotion` builds it only for opted-in
`TRACKED_CHARACTER` moves with fresh native motion matching measured pre-carry
displacement and a live frame. Both character adapters consume the same
`MoveResult.toCollisionResult` conversion. Default STOCK/corpse results have no
certificate; vertical bounds, step rules and generic resolver checks are unchanged.
Unsupported multi-contact corrections remain rejected rather than inferred.
The certificate retains all three components of the captured unit normal: physical
wall tilt is not numerical residue. Its clip budget uses the full requested-motion
dot product, while only X/Z response is authorized. Horizontal normal energy must
dominate vertical energy to exclude floor-like/ill-conditioned projections; this
is a conservative local side-plane domain, not a claimed SDK angle threshold.
Velocity projection preserves Y and gravity. Neither the residual allowance nor
the motor's independent vertical bounds is widened.

The existing exact-entity `CurrentFrameCollisionScope` also isolates opted-in body
hitbox yaw from Sable's reused per-level scratch pose. It reads the live in-flight
`CollisionInfo.trackingSubLevel` (published entity tracking can still lag), using
that frame's endpoint last/logical interpolation, or logical pose for consumed
current-geometry moves. It never writes native tracking or shared poses.
For changed stock intervals only, it preserves the exact finite nonzero native
carrier delta below Sable's `1e-4` cutoff; the existing bridge applies it once after
own-motion measurement. CURRENT geometry and default SDK paths retain the cutoff.
The relevant pinned `collide` source sites are 143–145/175/200–208 (scratch yaw)
and 442–447/454 (discarded carrier and temporary-position restoration).

Dual-frame scratch-yaw and naturally settled micro-carry fixtures are source-ready.
Native A→B transition coverage is still missing. The observed `+1.166e-6` X wall
response remains rejected by the static-plane certificate: its unexplained excess
is about `1.352e-6`. No wider tolerance or guessed normal authorizes it. These
source adaptations are not proof that the connected wall/snap fault is resolved,
nor a claim of equivalence to the unverified pinned Robust contact solver.

This is bounded current-pose adaptation, not historical grid rollback or
historical collision replay. Real lifecycle GameTests check an ordinary-world
Human snapshot has no frame metadata and a moving-cabin Human snapshot carries
the actual frame UUID/storage point; they do not fabricate producer snapshots.
World XYZ remains ordinary authority/render data.
SS14 source references are pinned in `PARITY.md`: Robust
`SharedGridTraversalSystem.cs:55-110` assigns parent identity, while
`SharedTransformSystem.Component.cs:772-793` preserves world pose on reparenting.
Transform parent identity plus local position are a useful conceptual reference,
but this adaptation currently supplies frame-relative snapshot data;
it does not claim full SS14 transform parenting.

## Controlled native strike architecture (acceptance pending)

`SharedNativeSelectedContactJournal` records the exact selected SAT
`CollisionInfo` on both sides. Pose intervals use side-neutral `levelGameTime`,
with `serverTick` retained for fixture compatibility. `NativeNormalContactPolicy.Target`
is an immutable absolute m/s normal prescription, making repeat application
idempotent rather than additive. The hull selector floors/skips compatible
planes, refuses unknown/overflow/corner cases, and checks world endpoints both
before and after. `SableOwnedBodyMotion.MoveResult` separates caller-requested
preclip and `.05` incoming-velocity intent from measured accepted movement.

`NativeNormalContactFinalizer` runs post-motor and post-frame-correction for
`GroundedHarnessWorldStep`, `OwnedCorpsePhysics`, and `GhostControlClient`;
admission requires exactly one native move and verified world endpoints.
`GhostPredictionHistory` retains pending sequence/target through replay and
clears on ack, skip, barrier, capacity, or lost history. Live target capture is
client-only; replay suppresses new native targets and reuses the stored normal,
not an additive delta. Existing authoritative server body velocity remains the
snapshot source; no packet was added. This is current-pose/current-geometry
replay, not historical hull rollback. Connected latency/fidelity remains
unverified. Human rest-0 upstream mapping is already in `PARITY.md`; no new
prototype fields/components are introduced.

Latest validation `ab7d45b0f3e0650e328e5926`: PASS all 13 across four managed
checks (owned translation 1/908, headless union 10/908, hand 1, wall 1),
cleanup confirmed; 36 focused XML checks and compilation passed. Owned-rotation
fixture is being implemented, not validated; corpse-specific runtime is not
validated by translation evidence. Camera, connected wall, extreme spin, and
persistence remain open. Not OWNER_READY or M1 acceptance. The loose-only
`moveInFlight` diagnostic is not an owned/mob eligibility signal.
