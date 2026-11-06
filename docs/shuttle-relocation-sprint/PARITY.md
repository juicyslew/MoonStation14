# Shuttle composition / behavior baseline

## Verified reference and present scope

The upstream audit verified checkout revision
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. This document records researched
composition and consumers, **not implemented flight parity**. Current work proves
the engine and existing cabin owners before adding controller/thruster/port data.
The first cabin must use the existing resolved local airlock, power and machine
prototypes. No unsupported SS14-named component is introduced merely for YAML
resemblance. Sable physics/3D coordinate choices are explicit adaptations.

## Resolved upstream chains and relevant defaults

| Entity / chain | Relevant resolved behavior | Actual consumers / local mapping status |
| --- | --- | --- |
| `ComputerShuttle → BaseComputerShuttle → BaseComputer → BaseStructureComputer → BaseStructure` | Anchored/static structure; inherited 50 W idle / 500 W working power state and power-required UI; construction, wires and damage/destruction; shuttle UI/radar/destination-disk slot and `ShuttleConsole`. | Server `ShuttleConsoleSystem.TryPilot`, shared console system and `MoverController`. Local console/pilot components not yet designed or implemented. The console is not the craft owner. |
| `Thruster → BaseThruster → BaseStructureDynamic` | Thruster overrides to anchored/static; 1,500 W, linear thrust 100, exposed-space nozzle, powered/anchored enablement and exhaust heat damage. | `ThrusterSystem.CanEnable` and grid aggregate thrust registration. Existing local paid-power authority must be extended, not replaced with FE-only behavior. No thrust unit conversion yet established. |
| `ThrusterLarge → BaseThruster → BaseStructureDynamic` | 10,000 W; thrust 1,600. | Same system; larger craft/thruster rollout not part of the initial engine proof. |
| `Gyroscope → BaseThruster → BaseStructureDynamic` | Angular; no exposed-space requirement; thrust 2,000; `machinePartThrust: Manipulator`. | Same aggregate angular-thrust consumer. Not a ready-made Sable angular velocity. |
| `AirlockShuttle → AirlockRCDResistant → Airlock → BaseStructure` | Normal airlock access/wires/20 W/airtightness/door damage; RCD deconstruction disabled; adds `Docking`, device/signal control and soft circular docking fixture, radius 0.2, offset `(0,-0.5)`, alongside hard door fixture. | `DockingSystem` owns links/joints and associated door behavior. Reuse the existing local resolved airlock for the cabin; ordinary airlocks do not already implement docking. |

Full relevant parent/default/consumer mapping is required again when selecting the
supported live controller/thrust/port fields. This table is not a claim that every
inherited construction, radar, access, wiring or damage feature is supported.

## Gameplay consumers / invariants

- Pilot admission: `CanPilot` tag, powered anchored console and interaction
  permission. Shared handling blocks ordinary body movement while piloting.
  Pilot status is distinct from passenger membership.
- Upstream input: strafe left/right/up/down, rotate left/right and brake;
  tick/subtick input accumulation in `MoverController` consumes aggregate thrust
  and maximum-velocity state. This is 2D. Three-dimensional ascend/descend,
  engine mass/force scaling and angular behavior require explicit adaptations;
  upstream `max angular velocity = 4f` is not a verified Minecraft-unit setting.
- Normal impact: `ShuttleSystem.Impact` consumes velocity/inertia, mass, tile and
  structural damage logic; it is separate from FTL materialization hazards.
- FTL server configuration: startup 5.5 s, travel 20 s, arrival 5 s, cooldown
  60 s; arrivals-only cooldown 10 s. Limit 300 compares physics mass, not blocks.
  Hyperspace knockdown defaults to 5 s; exemptions/eligibility still need exact
  mapping before implementation. `CanFTL` also checks current phase,
  `PreventPilot`/`PreventFTL` and a cancellable attempt event.
- `NoFTL` lookup uses grid membership, not nearby AABB population. Persistent
  ship/sublevel membership, airborne riders and plot-retained/contained entities
  must be proved locally before FTL.
- SS14 FTL overlap uses expanded hard fixtures; the owner's wider 3D arrival
  shock/warning is a deliberate adaptation whose volume/cover/timing remain to
  be resolved at the FTL gate. It does not apply to normal landing collisions.
- Grid identity, station grouping, docking links and gravity targets are distinct.
  Physical contact with Moonrock does not make terrain part of a ship. This work
  does not globally change character gravity or introduce zero-G gameplay.

## Source index (paths relative to pinned SS14 checkout)

- `Resources/Prototypes/Entities/Structures/Machines/Computers/{computers.yml,base_structurecomputers.yml,frame.yml}`
- `Resources/Prototypes/Entities/Structures/{base_structure.yml,Shuttles/thrusters.yml}`
- `Resources/Prototypes/Entities/Structures/Doors/Airlocks/{shuttle.yml,base_structureairlocks.yml}`
- `Content.Server/Shuttles/Systems/{ShuttleConsoleSystem.cs,ThrusterSystem.cs,DockingSystem.cs,ShuttleSystem.Impact.cs,ShuttleSystem.FasterThanLight.cs}`
- `Content.Shared/Shuttles/Systems/SharedShuttleConsoleSystem.cs`
- `Content.Server/Physics/Controllers/MoverController.cs`
- `Content.Server/Shuttles/Components/{ThrusterComponent.cs,ShuttleComponent.cs}`
- `Content.Shared/{Input/ContentKeyFunctions.cs,CCVar/CCVars.Shuttle.cs}`

No shuttle assets imported yet. Preserve existing cabin assets/provenance. The
plan's hyperspace audio attribution must be verified against actual resources
before import; current engine integration does not require those sounds.

### Heavy-freight diagnostic profile

The Nov 6 audit reverified the pinned checkout and the full `BaseThruster → BaseStructureDynamic → BaseStructure` chain above. `ThrusterSystem` aggregates enabled, anchored, powered devices; `MoverController` uses fixed available thrust with body inverse mass/inertia. More mass therefore reduces acceleration for fixed thrust. `BaseStationShuttles` (`Resources/Prototypes/Entities/Stations/base.yml`) selects `Resources/Maps/Shuttles/cargo.yml`; its dynamic grid has `Physics` and `CargoShuttle`, not a cargo-specific handling-profile prototype.

The requested smooth freight test belongs to the existing `ShuttleFixtureMotionService`/`ShuttleMotionMath` diagnostic owner, not a new gameplay thruster or entity component. Reuse the resolved cabin's existing hull, airlock and machine composition. Low speed/acceleration and jerk limits are explicit Minecraft test parameters, not converted SS14 cargo values. Existing ideal gravity/damping compensation and mass-normalized feedback do not implement fixed powered-engine capacity or cargo-weight coupling. Native mass, inertia, gravity and collisions remain authoritative; do not substitute teleportation or velocity resets.

The manual bay sea lantern is explicitly owner-authorized vanilla Minecraft lighting. It introduces no SS14 light prototype, asset import or unsupported component; normal block emission and SDK light propagation must remain the runtime path.

## M1 owned-body composition and carry boundary

Pinned Human audit: `MobHuman` composes `AppearanceHuman` and
`BaseSpeciesMobOrganic`; organic composition includes `MobBloodstream`,
`MobRespirator`, `MobAtmosStandard`, `MobFlammable` and `BaseSpeciesMob`.
The movement foundation is `BaseMob → BaseControllable`: gravity-affected
physics, circle radius 0.35/density 50 with MobMask/MobLayer, overridden to
`KinematicController`; `InputMover`, `MobMover`, `MovementSpeedModifier` and
`MobCollision`. `MobMover` defaults include push strength 600, grab range 1 and
step-sound distances 2 running / 1.5 walking. Human adds no special ship-carry
component. Appearance, anatomy and organic behavior are existing owners, not a
reason to add unsupported YAML components to this adapter.

Local mapping is the **existing composed root** `moonstation14:human`, not a
new `HumanData` or an unverified `PlayerCharacterHarnessData` type. Its typed
`CharacterData` explicitly contains grounded `MovementSpeedModifier`: acceleration
20, walk 2.5, sprint 4.5, input/no-input friction 20 and minimum friction speed
0.005. `CharacterMovementPolicy` applies the existing 1.25 Minecraft speed
adaptation and down-state 0.4 acceleration/friction factor. This local flattened
composition is not a claim that the full SS14 Human inheritance is implemented.
Do not refactor unrelated character definitions for the carry adapter.

Wall-response research reverified SS14/Robust pins `c9df5ef...` / `3136118b...`.
`SharedMoverController.HandleMobMovement` applies input wish, friction and
acceleration; Robust's `SharedPhysicsSystem.Solver` resolves contact velocity and
position separately through manifold constraints. A wall contact is not proof of
grounding. Input-driven `SpriteMoveEvent` and transform-distance footsteps are
distinct upstream consumers. Local native MTV certificates and position-only
geometry corrections are Minecraft/Sable adaptations, not SS14 mover components
or guaranteed universal tangent-sliding behavior. Relevant sources:
`Content.Shared/Movement/Systems/SharedMoverController{,.Input}.cs` and
`RobustToolbox/Robust.Shared/Physics/Systems/SharedPhysicsSystem.Solver.cs`.

`ModCharacters.CHARACTER_TYPE` / `CharacterData.CODEC` /
`CharacterComponentMergeStrategy` resolve typed catalog data before
`CharacterIdentitySystem.resolveForActor`, `GroundedHarnessWorldStep` and
`OwnedCorpsePhysics` consume it. Existing `CharacterPrototypeTest` and
`MovementEligibilityGameTests` cover typed movement and live identity. Carry must
serve those current resolved consumers, not a hardcoded Human-only branch.

Preserve own motion/friction/status separately from accepted frame displacement.
Standing carry is not walking/slipping/crawling; step-off must not keep a stale
parent or snap back. Jump and 3D collision are Minecraft adaptations. Sable's
living Minecraft drag/bounce/damage hook is not automatically an SS14 requirement;
normal impact damage and departure momentum need explicit validation/policy.
M1 server carry proof is not connected prediction/camera or finite breathing proof.

Source additions: pinned `Resources/Prototypes/Body/{Species/human.yml,species_base.yml}`,
`Resources/Prototypes/Entities/Mobs/base.yml` and
`Content.Shared/Movement/Components/MobMoverComponent.cs`.

The follow-up system audit establishes containment-driven transform parenting:
`Robust.Shared/GameObjects/Systems/SharedGridTraversalSystem.cs:55-110` chooses
grid/map parent after movement; `SharedTransformSystem.Component.cs:772-793`
preserves world pose on reparenting without adding old-grid velocity.
`Content.Shared/Movement/Systems/SharedMoverController.cs:187-344,417-427` applies
friction/acceleration to the mover's own linear velocity and sets angular velocity
to zero. `SharedMoverController.Input.cs:182-233,258-299` maintains relative
input/camera rotation across parent changes, not velocity inheritance.

**Physics callback correction:** the transform/mover-only audit above was
incomplete. `Robust.Shared/GameObjects/Systems/SharedTransformSystem.cs:286` calls
physics on parent changes. `Robust.Shared/Physics/Systems/SharedPhysicsSystem.cs:
164-193` invokes `HandleParentChangeVelocity` for same-map bodies; its
`SharedPhysicsSystem.Velocities.cs:201-268` preserves map linear/angular velocity by
correcting the body's velocity for the old versus new parent contribution.
`GetMapVelocities:159-198` includes parent linear velocity and angular point
velocity. Guards cover state application, non-running/static/noncolliding bodies
and nullspace; the inspected guard does not exclude kinematic controllers.

Thus lack of velocity arithmetic inside `SetParent`/the mover does **not** prove
that boarding/departure lacks velocity transfer. The next motion unit must verify
this complete ordering for Humans/corpses and dynamic items, preserve own-motor
friction, and use native engine point-velocity data if adapting frame transitions.
This is distinct from blindly copying Sable's persistent living inherited velocity
and drag. Vertical jump/3D angular representation remain adaptations requiring
explicit proof. `SharedMobCollisionSystem.cs:150-168,195-220` remains a separate
mob contact/pushing consumer, not Sable bounce/damage. Complete locomotion parity
is not claimed.

## M1 frame-relative interaction

Pinned `Content.Shared/Interaction/SharedInteractionSystem.cs`
(`InteractionRange`, `InRangeUnobstructed`, `UserInteraction`) resolves current
map/world transforms and target rotation. Entity-to-entity checks prefer nearest
fixture points, then obstruction checks; different maps fail. Default range is
1.5 m and obstruction mask is `Impassable | InteractImpassable`. A nonpositive
range disables distance limiting, not obstruction. Server `InteractionSystem`
inherits the shared consumer.

A click retains target entity identity plus coordinates: entity targets use their
current transformed position for reach, while empty-space actions use coordinates.
Interaction/access permission remains separate from range/LOS. Therefore a moved
machine must be rechecked as that same device in its current frame, not a stale
world clicked point or a replacement device at the old position.

The query adapter preserves existing typed-component consumers and port physical
eye/loaded-only policies. Item MAX 5 / block MAX 6 are existing Minecraft defensive
ceilings, **not** SS14 range conversions. No new range/prototype flags are needed.
Human/device ancestry indexes remain examination and airlock parity documents;
target-specific verbs/menu continuation retain their current authoritative owners.
Shared interaction parity does not establish every verb or connected outline path.

### Hand target semantics (pinned audit)

At pinned SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, Human inherits the
`Hands` component through `BaseSpeciesAppearance` (`Resources/Prototypes/Body/
Species/human.yml`, `species_base.yml`). Resolved defaults are throw speed 11,
interaction range 8, and pickup cooldown 0.5 s. These values describe upstream
composition; local hand prototype data is independently resolved and does not
claim numeric parity. In `Content.Server/Hands/Systems/HandsSystem.cs`,
`ThrowHeldItem` receives captured pointer coordinates, not cached body facing.
Nominated pickup is validated through `Content.Shared/Interaction/
SharedInteractionSystem.cs` range/obstruction and shared hands/item pickup
consumers; gaze is not a pickup prerequisite. This is distinct from gaze-based
selection and UI targeting.

The local adaptation reuses resolved `HandsPrototypeComponent`, existing hand
request owners, `BodyItemTargetProbe` / `BodyInteractionAccess`, and
`LoadedFrameRayQuery.currentActorPoint`; loose-object physics remains consumed by
resolved `LooseObjectProfile` / `LooseItemEntity`. Captured frame targets retain
their exact UUID/storage point and fail closed when unknown or unloaded; world
coordinates stay world-fixed. Existing local throw-physics numbers are an
unchanged 3D adaptation, not SS14 defaults. No new component or prototype field
is introduced. Diagnostic run evidence and connected aim/prediction limits are
recorded in `connected-feedback-03.md`.

### Captured floor DROP correction

Reverified the same pinned checkout for ordinary DROP: `Content.Shared/Hands/EntitySystems/SharedHandsSystem.Interactions.cs:30–36,110–117` forwards pointer `EntityCoordinates` to `TryDrop`, rather than selecting a target from current server facing. `SharedHandsSystem.Drop.cs:75–89,123–169,193–215` applies drop eligibility/container handling and optional coordinate placement; `GetFinalDropCoordinates` limits distance by interaction range and clips obstruction. Throw remains a separate path. This establishes captured coordinate intent, not the port's exact placement rules.

The correction reuses `BodyHandRaySnapshot`, `CapturedHandTarget`, resolved hand/item components and existing transfer owners. Frame nominations retain UUID/storage coordinates and are reprojected at current authority time. UP-floor nomination, current eye/first-hit/frame-cell/point matching, 3.5-block reach, full physical-box clearance, denial rather than clipping an invalid nomination, and legacy plain DROP for determinate non-floor rays are deliberate existing/extended 3D adapter rules, **not** SS14 numeric or floor-ray parity. Unavailable ordinary-world camera capture fails closed. Already-contained clients must retain plain DROP without a world-ray requirement, using the existing authoritative containing-owner route; a nomination racing with newly entered containment still denies. No new prototype fields/components or assets are introduced; the Human/Hands and Jug composition mappings above/below remain unchanged.

## Cargo composition and remaining consumer audit

Pinned chemistry `Jug` composes `DrinkBaseMaterialStrongPlastic`, `DrinkBase` and
`DrinkVisualsAll`. StrongPlastic inherits Plastic; DrinkBase inherits BaseItem;
DrinkVisualsAll composes DrinkVisualsOpenable and DrinkVisualsFill. Material parents
do not override the BaseItem fixture. Relevant BaseItem defaults:
freely rotating dynamic body, fixture AABB ±0.25, density 20, ItemMask, restitution
0.3, friction 0.2 and gravity; high-speed impact damage (5 blunt), collision/land
sounds, pressure movement and collision wake are inherited. Jug is Normal item
size, **200-unit** capacity, six fill levels upstream. Source:
`Resources/Prototypes/Entities/Objects/{base_item.yml,Specific/Chemistry/chemical-containers.yml}`.

The established port jug is **150 units**, 0.5×0.75 bounds, restitution 0.3 and
0.5 blocks/tick break threshold. Capacity, 3D bounds and break threshold remain
existing Minecraft adaptations; cargo carry is not permission to change them.
Local resolved `jug → generic` loose profiles and `LooseItemEntity` remain the
physics/presentation consumers. Strong plastic is damageable/destructible: 20 damage
spills, optionally spawns plastic, then destroys; 200 damage is overkill destruction.
`DamageOnHighSpeedImpactSystem.HandleCollide` requires hard-fixture contact and a
damageable entity, reads the body's linear velocity, checks minimum speed and
cooldown, then applies scaled damage. Jug inherits the component's **20 m/s**
default, not the port's travel-speed break rule. Damage and material destruction
are separate consumers; pure carrier displacement is not a new impact criterion.

The concrete beaker is **`LargeBeaker → BaseBeaker → BaseItem`**, not
`BeakerLarge`. LargeBeaker has Normal size, **100-unit** capacity and six fill
levels. BaseBeaker supplies Glass damage modifier, 5-damage destruction/spill/shard,
DamageOnLand 10 blunt, DamageOtherOnHit 5 blunt and high-speed impact minimum 2 m/s /
5 blunt. Port beaker_large's 100-unit capacity matches, but unsupported damage
components are not silently implemented by the carry adapter. Sources include
chemistry.yml:1-99,291-323 and `Content.Shared/Damage/{Components/DamageOnHighSpeedImpactComponent.cs,
Systems/DamageOnHighSpeedImpactSystem.cs}`.

Robust map-velocity preservation above applies to eligible dynamic bodies; cargo
departure proof below is limited to horizontal translation and does not establish full
native collision/contact equivalence. Do not infer cargo boarding or rotating-frame
parity from Human translation tests or copy living drag into the nonliving owner.

### Settled loose cargo on a native frame

The audited SS14 `BaseItem` composition establishes gravity-affected dynamic loose
items and ordinary friction/contact consumers; it does not provide evidence for a
new frame-aware support component. The local adaptation retains resolved loose
profiles and keeps their existing gravity, friction, restitution, and contact
thresholds. Native own displacement comes from the loose-only `Entity.move`
observation before Sable's nonliving carry TAIL; no native collision solver is
duplicated and no living-body carry is applied to cargo.

For a settled tracked root, current support is revalidated through one `0.02`
downward native move per tick, requiring fresh clipped own displacement and the
same live tracked-frame UUID from `LoadedFrameRayQuery.currentActorPoint`. Sable's
normal TAIL carries the entity once; the support probe does not alter stored own
velocity. Lost or unresolved frame support wakes the root and returns, leaving the
next tick to normal gravity/collision. Ordinary-world settled objects retain the
existing 10-tick loaded-floor support check. This adapter changes support cadence
only for tracked settled cargo and retains existing profile defaults.
Runtime evidence and exact cargo/reagent ownership assertions are recorded in
`FRAMES-AND-OWNERS.md`.

### Loose cargo departure

The cargo path now reuses `SableFrameVelocity.pointDeltaPerSecond`, the same SDK
point-velocity calculation as Human/corpse movement, rather than estimating frame
motion or applying a second carry. Before/after endpoints must be valid current actor
points in the same level, and the exact move must refresh collision info and change
tracking identity. Same-frame/world-to-world motion yields zero; unknown or stale
tracked endpoints yield no correction. A registered filled-beaker automatic-tick
departure test removed its native support and observed one fresh frame-to-world
transition during a downward support probe. SDK old-point horizontal velocity was
`3.7011` m/s and cargo's post-tick velocity matched with zero measured horizontal
error; the following world tick had only the existing `0.995` airborne decay, no
replay. Three further automatic-owner proofs cover rotation, first world-to-frame
boarding, and a direct A-to-B native tracking transition. The rotating sample's SDK
point was `(-0.019, -1.637, 1.912)` m/s versus COM `(0.498, -1.637, 0.498)` m/s,
with measured angular Y `0.7964` rad/s and a `1.506` m/s horizontal point-vs-COM
contribution; departure committed the old point velocity once. Boarding applied the
signed correction for new-frame point velocity `(1.380, ~0, 1.380)` m/s. Independent
frames crossing directly A→B had old/new Z point velocities `+3.701/-3.701` m/s and
the cargo owner's Z velocity matched the `+7.402` m/s shared correction. Transition
errors were at most `2.3e-16` m/s for tested horizontal axes, and all cases preserved
the exact registered beaker root, ledger token, and 12.5-unit reagent state. These
are current server-side movement proofs, not cargo yaw/spin, vertical/contact
transition policy, persistence, client prediction, or network behavior.

### Measured first owned-body departure gate

`SableOwnedBodyMotion.move` now returns accepted own displacement separately from a
map-velocity delta. A fresh collision-info identity plus changed, current native
tracking frame is required; the shared human/corpse owners apply that delta only
after their motor result. Point velocity comes from Sable's explicit-frame helper at
the `LoadedFrameRayQuery.currentActorPoint` captured storage coordinate, in m/s.
Unknown or removed frames do not imply a zero-velocity world frame. Tagged real-bound-
Human jump/departure and actual moving-deck landing evidence passes. The automatic
typed-damage corpse medical tick was also observed through entity-tick pre/post events
while a bounded owned plot floor patch was removed. It departed on fresh collision info
and landed on the ordinary world floor; captured carrier velocity `(1.792, ~0, 1.792)`
m/s matched its post-owner horizontal velocity from zero own pre-velocity within 0.02
m/s. Follow-up airborne ticks showed only ~0.05 m/s own drag, not a repeated frame
delta. A separate bound-Human jump gate on a rotating frame measured native COM linear
velocity `(0.491, -3.252, 0.491)` m/s, angular velocity `(0, 0.7857, 0)` rad/s,
and SDK actor-point velocity `(0.222, -3.252, 1.729)` m/s. Its 1.267 m/s horizontal
point-vs-COM contribution was included in the departure commit with zero measured
error. A real bound-Human world-to-native boarding gate also passed: the fresh
world-null to exact-live-frame transition matched measured SDK point velocity `(1.380,
~0, 1.380)` m/s with zero horizontal map error. This establishes horizontal linear
point-velocity contribution on translated/rotating frames and boarding, not body
yaw/angular-momentum transfer or connected prediction/camera behavior. A direct
living-Human A-to-B swap used independently authored moving frames. Its resolved-Human
pure motor baseline was `(3.125, 0, 0)` m/s; old/new SDK point velocities were
approximately `(0, 0, 1.358)` and `(0, 0, -1.358)` m/s. The shared correction committed
`(3.125, 0, 2.716)` m/s with zero measured map error and fresh direct A-UUID→B-UUID
evidence. Focused carry/transition selection passed 7/813; `test assemble` passed
3,158 JUnit tests, and a fresh full run passed 813/813.

## C1 native-frame snapshot substrate

Character snapshot wire data now optionally carries the validated native frame
UUID and storage-space body point. Producers still include ordinary world XYZ
and velocity. `LoadedFrameRayQuery.currentActorPoint` provides common known-frame
admission: server actors retain the exact FULL chunk guard; client admission uses
the exact live sublevel's own `LevelPlot` known chunk (`toLocal(globalChunk)` then
`getChunk`, never `Level.getChunk`/forcing). Projection uses SDK `logicalPose`,
not render interpolation. The SDK known-chunk lookup is source/API-verified; no
connected-client residency proof is claimed at C1. Unknown server actor tracking suppresses the character
snapshot rather than emitting a false world-frame state. Ghost snapshots remain
frame-less. This metadata is not permission and no packet writes SDK tracking.

`NativeFrameInterval` snapshots both SDK `lastPose()` and `logicalPose()` by
copying through `Pose3d(Pose3dc)` and `bakeIntoMatrix`; it does not trust SDK pose
object equality or mutable SDK-owned pose instances. Its per-body, epoch+UUID
policy is ready for C2's one-time interval consumption, but live prediction,
carry, unknown-frame pause, and projection reuse have intentionally not been
enabled here. [C2 below records the implemented client current-pose boundary.]
SS14 `Transform.ParentID` plus `LocalPosition` source behavior is a reference for
spatial ownership, not an assertion that this client snapshot adapter implements
the full Transform system.

### C2 current-pose client carry/reconciliation boundary

For grounded controlled Humans only, `GhostControlClient` projects an accepted
frame snapshot's UUID+storage point through the client's known current logical
pose once, seeds the local velocity basis from that exact authoritative frame,
then replays pending own movement against current collision geometry. Replay
suppresses stock SDK `inheritedMotion`. A live client move may apply the fresh
`Entity.move` result's native `inheritedMotion` once per consumed `(epoch, frame,
lastPose, logicalPose)` interval through `sable$vanillaCollide`; the carrier is
never estimated from raw pose deltas and combined with own input. The shared
bridge records accepted own displacement before that carry is applied. Fresh
collision-info identity is still required
before accepting a changed native tracking UUID or computing old-point minus
new-point Sable velocity; the next basis is rebuilt at the body's current point,
not reused with stale storage coordinates. Thus a stale SDK A tracker at an
authoritative B snapshot does not substitute A velocity for B. Unavailable B
pauses local prediction and advances skipped inputs through existing bounded
history; only a later accepted snapshot resumes it. No client code writes SDK
tracking or transform parenting. Native identity that disagrees with a snapshot
basis cannot admit the live carry. Ghost, contained/non-grounded, externally
moved, and medical-barrier paths retain their existing owners.

This deliberately adapts *current* pose and current collision geometry; it is
not historical transform/physics rollback, so departure/rotation under latency
is not claimed SS14 prediction parity. The Human composition audited above remains
the existing resolved `human` CharacterData, with its existing speed and motor
policies; C2 adds no prototype fields, speeds, or components. Server movement
keeps default `SableOwnedBodyMotion` inherited-collision behavior, and nonliving
cargo keeps its separate native TAIL observer. Headless lifecycle/frame tests
prove server-authored frame data and existing bridge regressions, not connected
camera/network/client-chunk behavior. Connected manual testing remains required.

Native-contact fault recovery is a deliberate Minecraft adapter: the shared
owned-move boundary normalizes only `1e-6`-block native f32 residue while the
pure motor retains finite/bounded checks. A rejected committed Character move
settles that intent and rebases the same owner from a finite authoritative pose;
unknown pose/frame context holds movement until known rather than being encoded
as world data. A per-session snapshot rebase revision prevents replay of inputs
settled by that correction and clears the client stopped-prediction latch. These
are not claims of an SS14 zero-velocity recovery algorithm or historical rollback.
Older terminal failures left durable ACTIVE claims and in-memory recovery markers,
without a trustworthy movement-failure reason. Normal login now reclaims only an
independently proven unique loaded living body/account/profile/Mind with consistent
current-primary identity/generation and no competing authority. Expected-row CAS
parks the actual body pose before the existing OFFLINE reconnect path; ambiguous
claims remain fail-closed. See `RECONNECT.md`. This is a local recovery policy, not
a claim of SS14 crash/save reconciliation parity.

## Admin motion fixture (authorized 2026-10-25)

Pinned SS14 `ShuttleSystem.OnGridInit` adds shuttle state to runtime grids;
there is no shuttle entity-YAML parent chain for this fixture. See
`Content.Server/Shuttles/{Systems/ShuttleSystem,Components/ShuttleComponent}.cs`
and `Content.Server/Physics/Controllers/MoverController.cs` at the SS14 pin above.
Ordinary upstream piloting consumes machinery thrust and applies physical forces;
this admin diagnostic does not implement those gameplay systems or defaults.

The authorized local fixture reuses the existing native grid, fixture commands
and residency owner. Finite desired world velocity/yaw and a bounded timeout are
debug inputs, not prototype thrust fields. Following owner feedback, target-speed
and yaw caps are removed; acceleration/impulse actuation remains bounded. This
does not promise arbitrary targets are physically attainable or add altitude hold.
Sable release-source pin
`ad8b4d377741fbd923f4d863b2cf9e462daff9a6` exposes merged mass/local inertia through
`ServerSubLevel.getMassTracker`, world velocity reads, local impulses through
`RigidBodyHandle`, and a pre-physics callback per substep (seconds). Gravity
compensation and low-rate velocity feedback are deliberate 3D test adaptations.
Native collision solving remains authoritative: no per-step pose/velocity reset,
gravity/configuration override, or extra physics loop. Six substeps remain normal.
`ShuttleFixtureMotionService` owns only registered diagnostic requests and uses
`ShuttleMotionMath` for bounded local impulses. Stop and expiry release those
impulses, not the ship's velocity. Managed `run_19802318b353bf9612a46542`
passed 24 JVM checks and all 8 selected native cases (8/921) at six substeps:
real command-built grounded/lift/turn motion, controlled HUMAN and filled-jug
conservation, lifecycle/expiry and existing command/hand controls. This is bounded
server proof, not connected acceptance. The existing artifact/source
correspondence limitation remains unchanged.

Follow-up controller correction (validation pending): linear feedback now measures
transport velocity from consecutive **native pipeline COM positions** and the
previous SDK substep duration. Bootstrap uses SDK linear velocity; angular
feedback retains SDK angular velocity. This is a velocity measurement, not an
altitude target or pose/velocity reset, and is confined to the admin fixture.
Pinned Rapier `src/dynamics/solver/{island_solver,velocity_solver}.rs` at
`38e92f117590862481a53df6fc69a5d893e29186` subdivides one external step into solver
iterations. A single gravity-countering impulse before those iterations can
cancel final velocity while still advancing the integrated pose. No guessed
solver-iteration offset is used; actual transport is measured instead. Physical
impulse bounds, native mass/inertia, gravity and six SDK substeps are unchanged.
