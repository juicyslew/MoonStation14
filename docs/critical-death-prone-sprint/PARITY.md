# Medical state and posture correspondence

Reference: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, verified in
`C:/Users/William/Documents/SS14-dev/space-station-14`.

## Composition

Human resolves through `MobHuman → AppearanceHuman + BaseSpeciesMobOrganic →
BaseSpeciesMob`, with `BaseMob` and `MobDamageable`. Pig resolves through
`MobPig → SimpleMobBase + StripableInventoryBase → SimpleSpaceMobBase →
BaseSimpleMob → BaseMob + MobDamageable + MobPolymorphable`.
See `Resources/Prototypes/Entities/Mobs/base.yml` and species prototype files.
Both inherit Alive=0, Critical=100, Dead=200; Human has Crawler, Pig does not.
The local existing human/pig character definitions flatten these relevant
components; local prototype inheritance remains supported and tested. This is
not a reproduction of unsupported SS14 component families or its entire tree.

`MobState` allowed states and `MobThresholds` are independent components.
A state-only character remains valid; threshold updates require both resolved
components. Highest reached threshold is selected before allowed-state checking;
a disallowed desired state is rejected, not replaced with a lower threshold.
`AllowRevives=false` locks ordinary healing in Dead, not explicit revival.
Threshold dictionaries are not universally human-shaped: mouse uses 10/20 and
some mobs have no Critical threshold.

Pig was rechecked at the pinned revision: `animals.yml:3592–3661` (`MobPig`) has
no threshold override; `simplemob.yml` and `Body/Animals/animal.yml` introduce no
replacement thresholds in its parents. It therefore has Critical at 100 and Dead
at 200. Its Critical and Dead visuals both select `Base: dead`, which is not a
two-state health policy. Local pig thresholds and its current prone appearance
remain unchanged. `MobStatePolicyTest` checks the real pig definition and a
test-only inherited pig with Alive/Dead-only states and death at 60 through the
same resolved policy/reducer consumed by `MobStateSystem`.

## Minecraft health bridge

### Ghost handoff correction

Corpse admission uses the body's resolved `GhostOnMove` policy. Movement and
incorporeality belong to the separate resolved `observer` prototype, inherited
from `observer_base`, **not Human**. `ObserverSystem.requestGhost` passes its
already resolved `ObserverMovementComponent` through `BoundObserverAuthority`
to the lifecycle bridge. The bridge rejects missing movement before staging;
it must not look for this component on the corpse or add it to Human as a repair.
The fresh owner trace proved that former lookup rejected every ghost request
before staging. `CharacterPrototypeTest` checks actual bundled composition and
inherited/custom observer rates; `LifecycleObserverMovementGameTests` checks
the correct ownership in a server fixture (compiled, not executed).
Ghost Begin carries the passed movement rates; client readiness and movement
do not depend on observer-snapshot arrival or corpse projection. The staged
generation is newer than the corpse generation; existing monotonic epoch guards
remain intact.
Owner subsequently confirmed successful ghost entry and movement after the
Observer policy-source repair.

`LocalCarrierRenderPolicy` additionally suppresses only the owner's distinct
native carrier model during CORPSE/GHOST observation. It does not hide the
represented corpse, ghost sprite, other players, or ordinary spectators; remote
carrier visibility continues to use server tracking policy.
Owner subsequently confirmed the dev head is gone; this rendering path is not
part of the current ghost-entry repair.

Owner-requested visual adaptation: the ghost sprite is a 1.15-block billboard
square centered on `entity.getEyeHeight()`. World-space eye translation precedes
billboard rotation and scale; local vertices are centered, preserving the anchor
at any camera pitch/yaw. This does not change physical bounds, camera height,
movement, or sprite assets. Visual placement still awaits owner confirmation.

Typed damage remains authoritative. Opted-in characters project their resolved
Dead threshold to native maximum health at the existing 5 typed damage / native
health ratio: Human 200 maps to 40 native maximum, with 100 Critical at 20 health.
This avoids native death at the start of Critical without changing SS14 thresholds
or damage rates. Capture a pre-existing native deficit before changing maximum
health. Persist/synchronize medical state independently of native health.

Native death still runs the existing confirmation boundary. Policy-backed corpses
are retained; explicit revival resets native death flags and preserves entity UUID.
Removed entities are not resurrected or reinserted. Ordinary threshold healing
does not revive a default Dead body. These are Minecraft lifecycle adaptations.

## Standing and crawler consumer audit

- `Content.Shared/Stunnable/CrawlerComponent.cs:14–39`: `.5s` knockdown, `2s`
  base stand time, `.4` speed, `5` knockdown refresh damage, `1` crawler friction.
- `SharedStunSystem.Knockdown.cs:277–317,582–595`: get-up requires expired
  knockdown and movement eligibility; no Crawler means immediate stand. Get-up
  damage interruption threshold is `5`, duplicate cancellation is enabled, and
  interaction eligibility is not required. Movement interruption is disabled.
- `DoAfterArgs.cs:79–143` and `SharedDoAfterSystem.Update.cs:218–230,261–274`:
  `NeedHand=false`; although the consumer sets `BreakOnHandChange=true`, that
  check is gated on NeedHand and therefore ineffective here. Do not introduce
  hand-switch or movement cancellation merely because ingestion supports it.
- `SharedHandsSystem.EventListeners.cs:17–44`: crawl speed multiplies by
  `freeHands / totalHands`; stand time multiplies by
  `totalHands / (freeHands + totalHands)` if any hand is empty. Thus two empty
  human hands produce a one-second get-up from the two-second base; one empty
  hand produces 1⅓ seconds, no empty hands retain two seconds. A pull reservation
  counts as occupied locally, corresponding to the upstream virtual hand item.
- `StandingStateComponent.cs:14–28` and `StandingStateSystem.cs`: down friction
  `.4` is separate from crawler friction. SS14 changes collision layers; lowered
  Minecraft collision and eye height for one-block crawling are owner-approved
  adaptations. Blocked standing must retain conscious prone posture.

## Pulling and presentation

`BaseMob` supplies Pullable; Puller needs an empty hand by default. Critical/Dead
stop the actor's pull, but targets are pullable irrespective of medical state.
`Content.Shared/Movement/Pulling/Systems/PullingSystem.cs` creates a distance
joint with initial distance + `.15`, zero stiffness, and no connected collision;
puller walk/sprint modifiers are `.95`. The existing local server constraint and
hand reservation are Minecraft substitutions, not native shared joint prediction.
The approved input correction is Ctrl + left-click (`TryPullObject`), matching
`Resources/keybinds.yml:226–230` and the shared interaction pointer binding.
Implementation and connected-client acceptance of this correction remain pending.

## Approved face-up / face-down representation

### Idle prone rendering geometry

Minecraft 1.21.1 humanoid/player limbs, jacket and pants are reset after animation
when down and not actively crawled by their owner. Prone head/hat rotations remain
neutral during crawling too. Thus attack/swim pose remnants and head-look tracking do not imply
consciousness; the camera remains independently controllable. Externally dragged
bodies and voluntary fake-death/incapacitated bodies use the same idle policy.
The existing synchronized own-crawling flag is the sole animation exception.

The renderer's outer `scale(-1,-1,1)` and model origin translation are retained.
Solid torso/head geometry reaches 2/4 pixels from the model center; outerwear and
headwear reach 2.25/4.5 pixels and may penetrate the floor by 0.25/0.5px. The shared
pivot uses 2/16 model units; a pose-local head offset of 2 pixels grounds the solid
head in either orientation. Its sign
reverses for face-up roll. Head support applies during crawling too; limb
neutralization is idle-only, and the offset resets when standing. Renderer model
scale (native PlayerRenderer 0.9375, harness 1) and the outer entity scale are
accounted for in full-transform contact tests. This is a 3D Minecraft presentation
adaptation, not upstream skeletal physiology. Physical dimensions, eyes, and
gameplay positions remain unchanged. Source index: Minecraft 1.21.1
`HumanoidModel`/`PlayerModel` cube definitions; local consumers are
`LivingEntityProneRenderMixin`, `PlayerCharacterHarnessRenderer`, and
`ProneRenderBounds`.

The prone transform centers the native humanoid mesh, not half the standing
collision height. Both the harness renderer override and fallback mixin use the
shared root transform. Native rendering applies entity scale before setup
rotations, then the outer flip, renderer model scale, and origin translation
(-1.501). Setup-local offsets must not multiply entity scale again. Independent
tests compare the production PoseStack against this actual operation order, with
player/harness model scales 0.9375/1, entity scales, quarter turns, both faces, and
solid torso/head contact. Bounds use the same native mesh basis; physical collision
and eyes are unchanged. Earlier shared-math-only tests missed the harness override
and do not establish actual mesh centering. Connected rendering remains a manual check.

SS14 standing/knockdown is two-dimensional; these positions are an owner-approved
Minecraft representation, not additional upstream physiology components.
Voluntary prone and knockdown start face down. Standing entry into Critical/Dead
defaults face up, but medical transitions preserve an already-down body's face
orientation, including external rolls before Critical becomes Dead. These latest
owner rules supersede the earlier forced face-up transitions. A conscious person can roll
face up to play dead; another person can turn a body over. Authorized self-crawl
input turns the body face down without an extra delay or speed penalty. Stopping
does not turn it back; external dragging never changes its lying position.
Orientation belongs to synchronized posture state, independent of medical state.
Normal prone/get-up controls must remain available separately from rolling.
The local roll control is rebindable K; Shift plus the roll control targets another
downed body. This control has no claimed upstream default-key counterpart.

Local Minecraft presentation adaptation: resolved typed PostureData persists and
syncs `movement_facing_yaw` independently of Minecraft look/body yaw. Voluntary
downing snapshots current body yaw; involuntary downing snapshots it only when
entering down posture, and subsequent external movement cannot change it. The
authoritative grounded harness updates it from collision-accepted owner movement
in world coordinates (yaw = `atan2(-x,z)`); stationary/blocked input and external
drag preserve it. Prone renderers and gameplay interaction bounds consume this
stored yaw. SS14 posture is two-dimensional and has no corresponding facing field;
this is a deliberate Minecraft adaptation, not upstream parity.

## Approved debug revival and damage feedback

The requested operator debug revival targets the caller's exact linked body,
including the retained corpse while ghosted. It clears authoritative typed damage
and invokes explicit revival without replacing the body or forcing a ghost to
return. This is a local debug adaptation: upstream `RejuvenateSystem` raises an
event for component-specific cleanup, rather than directly setting Alive
(`Content.Shared/Administration/Systems/RejuvenateSystem.cs:5–13`). It is not a
medical item or general treatment feature. Implementation acceptance is pending.

Generic damage must not create a movement impulse. Suppressing Minecraft's
automatic hurt knockback must leave explicitly requested impulses intact.
The local `damage_feedback` prototype family separates red flash, camera shake,
action interruption, and hurt sound. Hurt sound is opt-in and defaults off,
including physical profiles; gasp/death/weapon audio remains separate.
Profiles resolve inheritance through the normal server
catalog and synchronize to the client. Semantic damage causes select these
profiles; reagent HealthChange is quiet regardless of damage type. Quiet chemical
damage not interrupting timed actions is an owner-approved departure from the
previous generic positive-damage interruption rule. Explicit source impulses
remain separate from damage feedback.
The requested health display uses typed damage and resolved medical thresholds,
not the native compatibility maximum of 40. Human Critical remains 100 damage and
Dead remains 200; displaying health out of 100 must not rescale these thresholds
or present Critical as death.

## Respiratory gasps

Pinned `Content.Server/Body/Systems/RespiratorSystem.cs:80–125` subtracts saturation
on due updates, skips breathing while incapacitated, and attempts `Gasp` when
saturation is strictly below the suffocation threshold and the eight-second
cooldown has elapsed. Dead mobs are skipped. This is not an immediate
Critical-entry sound or a periodic deathgasp. The live local lung consumer uses
resolved respirator interval, threshold, gasp interval, and sound fields.

Human's upstream Unsexed voice maps to MaleHuman; the port currently supports that
default rather than inferring voice from skin. Ordinary male/female gasp sound
collections were copied from `Resources/Audio/Effects/Gasp`, retaining its
CC-BY-SA-3.0 tgstation attribution. Voice pitch variation is ±0.125. Playback is
server-authored at the body's position, without a chat emote. Minecraft sound
audibility is a spatial-audio substitution for upstream PVS playback; full voice
selection and the broader emote system are not implemented here.

Owner-approved presentation departures: body-centered tightening Critical fog,
clear Critical fog on death, third-person entry with unrestricted F5, and fresh
post-death movement input for ghosting (upstream accepts held input). Ghosted
revival offers manual return rather than forced rebinding. No active-capability
Last Words/Succumb or strip UI in this sprint; Critical speech is blocked.

### Connected correction invariants

While lifecycle memory is actively GHOST, its controlled body ID is the ghost.
The durable DEAD_CLAIM still identifies the retained corpse. These are distinct
identities; validity must check each against its own role, while preserving the
observer epoch versus backend authority-generation distinction. The owner's log
showed GHOST immediately followed by END because the active identity check
incorrectly expected the corpse.

The subsequent owner log showed GHOST surviving, but the staged ghost could
still be denied to its owner: classification consulted the staging map while
visibility did not. Visibility now authorizes the exact staged owner before
world-add, when that owner is still CORPSE; other living viewers remain excluded.
After commit, active GHOST-session rules continue authorization. Client body-pull
nomination uses read-only client prototype projection; server actor resolution
requires ServerLevel and must not filter client candidates.

Supported medically incapacitated bodies suppress native travel. Their common
entity-tick tail advances zero-input gravity/collision using resolved movement data
and shared grounded constants for Critical and Dead, independently of mind/input
ownership. Death/controller teardown clears ownership; this must not stop physical
motion. Command simulation does not run a second step. Unmanaged bodies retain
ordinary travel; observer
ghosts are excluded. Movement ownership/NoAI, corpse UUID, and worn inventory stay
intact. Dead characters gain no owner-input movement. This falling behavior is a
Minecraft vertical-world adaptation, not an upstream vertical-physics claim.

Body interactions use separate gameplay bounds covering the lying model, derived
from standing dimensions and shared pose geometry. The physical crawling box is
not the visible model's full extent; render-only culling margins are not gameplay
reach. Client nomination and server validation share eye-ray, reach, and block
occlusion rules. Turning another downed body may target retained Dead bodies;
only the actor must satisfy consciousness/action/free-hand requirements.

Pinned `PullingSystem.cs:563–577` creates a distance joint between body centers,
with initial length equal to their separation, maximum length plus 0.15 m,
minimum zero, zero stiffness, and connected-body collision disabled. The port's
replacement 3D spring-damper is not a native physics joint or mass solver.
Ordinary lag is not a release condition. Explicit release, invalid
authority/entity/world/hand reservation, incapacitation, and conscious movement
resistance remain distinct termination rules. Shared movement consumes ephemeral
synchronized rope state; this is a supported local runtime component, not an
unsupported upstream YAML component.

Upstream explicitly describes no constraint effect inside the slack interval and
infinite stiffness at the limits: zero joint stiffness does not mean no limit
correction. A progressive spring would be a deliberate Minecraft adaptation, not
the pinned SS14 rule. Vertical hauling/jumps have no direct 2D upstream counterpart.
Owner has chosen a compliant, progressive 3D spring-damper adaptation. Dedicated
body tossing and artificial-gravity controls remain out of scope. The superseded
horizontal limiter incorrectly scales
Y when clipping X/Z, and horizontal-only catch-up cannot haul upward. These are
known connected movement defects, not accepted rope behavior.

The replacement uses `Puller.tether` settings resolved with the existing typed
component (including partial nested inheritance): stiffness 60, progressive
stiffness 40, damping 6, maximum acceleration 160. These are Minecraft tuning
values, not upstream values. `TetherSpring` samples 3D foot-position separation
and relative velocity in blocks/second, with no tension inside initial 3D distance
plus resolved `slackAllowance` (now 1.5 blocks, previously hardcoded 0.15).
This larger allowance is an owner-requested Minecraft adaptation to permit an
ordinary full vertical jump without early tether restraint; it is not an SS14
default. Closing motion reduces tension; acceleration is bounded for integration.
Endpoints have normalized equal response, not a mass/density rigid-body solver.

Both standing controllers erase previous horizontal velocity through ground
friction before applying input and tether impulse. Damping 10 produced a sustained
two-endpoint oscillation that the earlier fixed-anchor test missed. A simultaneous
two-grounded-motor regression measures target velocity spread 4.5 with the former
tuning and approximately 9.24e-14 with damping 6. Spring stiffness, force cap, and
slack are unchanged; this is a Minecraft integration correction, not SS14 tuning.

Pinned inheritance audit: `MobHuman` inherits `AppearanceHuman` and
`BaseSpeciesMobOrganic`; the latter resolves through `BaseSpeciesMob` to `BaseMob`
and `BaseControllable`. `BaseMob` supplies `Pullable`, and `BaseSpeciesMob` supplies
`Puller`, without overrides. `PullerComponent.cs:30–45` supplies hands-required and
0.95 pulling walk/sprint modifiers. `PullingSystem.cs:306–318` releases a pulled
target on move input only when `ActionBlockerSystem.CanMove` permits it. The port's
typed nested tether settings are supported Minecraft-only additions, not upstream
components. Source prototypes: `Body/Species/human.yml`, `Body/species_base.yml`,
and `Entities/Mobs/base.yml` at the pinned revision above.

`BodyPullService` publishes incident endpoint snapshots to both bodies; incoming
and outgoing links coexist, and stopping one must preserve the other. Normal
movement consumes `TetherMotion`: owned server movement, client prediction,
retained-corpse movement, native living travel, and loose-item ticks. Owned kernels
apply the constraint impulse after friction/input/jump and before collision;
otherwise standing Human friction would erase the reaction. Legacy external
impulse ordering is unchanged. There is no endpoint clipping or target snapping.
Numerical fixtures and connected smoothness checks are acceptance requirements,
not claims implied by the implementation.

Dragged medical-camera presentation retains the body's previous/current eye
samples on `MedicalCameraAnchorEntity`, rather than resetting both positions to
the current sample every tick. Minecraft's native partial-tick camera interpolation
can then follow body movement smoothly. The anchor synchronizes its own rotation
history independently; following a body does not copy its look direction.
`MedicalCameraFollowPolicy` supplies the live sample pair and pure interpolation
tests. This is client presentation only, not body motion or observer authority.

Ghost view uses `ObserverGhostLook` to project the observer's own yaw/pitch onto
all native current/previous head, body, and entity rotations. Native living cameras
read head yaw; updating entity yaw alone caused the mob-style heading deadband.
Camera look remains independent of the spectator carrier, and movement intent
uses that same camera yaw.

Medical ghost movement now reuses `LifecycleGhostSessionControl` and the existing
`GhostControlClient` Begin/Ready/Commit prediction/history/stream machinery. The
observer session still owns corpse identity, visibility, and revival/return policy,
but must not run its legacy movement loop for a prepared lifecycle-owned ghost.
Observer look feeds the predictor rather than the spectator carrier's old yaw.
Resolved observer movement rates are captured for both client and server motors;
vertical flight uses the sprint rate as a Minecraft adaptation. Authenticated
connected handoff still requires owner testing.

Incoming rope roles distinguish an externally dragged target from a puller.
Targets use authoritative native interpolation, not historical input replay with
current tether endpoint data; their movement input still reaches the server to
release a conscious pull. Outgoing-only pullers retain prediction. Entry/release
barriers and suspended sequence advancement preserve acknowledgement progress.

Distinct native spectator carriers are excluded from other viewers' tracking
while observed as Corpse/Ghost. The existing observer session identifies them;
there is no global spectator-hide rule or new prototype component. Tracking is
refreshed after session changes through `ServerChunkCache.move`, whose native
`ChunkMap.move` refreshes the carrier's tracked entity even without movement.

Controlled living-body physics advances with a zero voluntary wish when no fresh
input packet arrives. Incapacitated bodies instead use the sole passive entity-tick
consumer. Client prediction relinquishes tracker ownership and history replay
during incapacity; authority barriers guard entry and recovery. Stable medical/HUD
body identity is separate from prediction eligibility.

Observer camera yaw may accumulate beyond ±180 degrees. Payload and server intent
construction accept every finite yaw and canonicalize it before use; visual look
remains free. Packet/server movement bounds and non-finite rejection remain independent. The
prior raw-yaw sender crashed in the owner's first GHOST client tick. Factory tests
cover multiple rotations, but are not connected movement/look acceptance.

SS14's hand multiplier for knocked-down get-up time is
`totalHands / (emptyHands + totalHands)` when any hand is empty; no empty hands
leaves the base duration. With two hands this is 1/2, 2/3, or 1 times the base
duration (`SharedHandsSystem.EventListeners.cs:20–31`).

Temporary client debug presentation: while the observed body has synchronized
Critical state, the HUD adds typed-damage/death-threshold progress using that
species' resolved prototype thresholds. It is read-only diagnostics, not medical
authority or a new capability. Critical fog adapts Minecraft's opaque fog color
to black through `ViewportEvent.ComputeFogColor` (the terrain/sky fog color path)
and narrows the fog shader near/far planes through `ViewportEvent.RenderFog`.
The project's custom Overworld sky renderer skips its procedural stars and
sun/moon discs for this state, leaving its black sky cube. Terrain/entity
fragments use the ordinary fog shader's near/far opacity. This is a Minecraft
1.21.1 shader/fog adaptation, not an exact radial cutoff; cloud-stage coverage
remains subject to owner visual validation.

## Acceptance status

This is a verified source map, not a claim that all consumers are implemented.
See SPRINT.md for current fixture results and pending acceptance evidence.
See `SPRINT.md` for exact counts and unresolved regression results.
# Integration correspondence — 2026-10-05

The current runtime adds typed `Pullable`, `Puller`, `GhostOnMove`,
`ObserverMovement` and `Incorporeal` policies. Human Puller comes from
`Resources/Prototypes/Body/species_base.yml` (`BaseSpeciesMob`); Pig inherits
`BaseMob` through `BaseSimpleMob` and has Pullable, but no Puller/Hands/Crawler.
Puller currently supports the hand-requiring subset; resolved walk/sprint
modifiers default to .95 and are consumed by live movement.

Observer composition is represented by local observer_base/observer inheritance,
not by copying unsupported BaseMob physiology. Verified MobObserver movement is
walk 8/sprint 12, gravity ignored. Minecraft's existing noPhysics ghost harness
is broader than SS14's GhostImpassable collision layer: this remains a deliberate
adaptation, not collision-layer parity. The authored SS14 ghost sheet/metadata and
attribution live under `assets/moonstation14/textures/entity/ghost/`.

The lifecycle medical-observer bridge and ghost-mob harness control use normal
required flags ON. Native-player observer remains optional. UUID-based native death
retains the durable DEAD_CLAIM row in existing stager state; same-UUID ghosting
returns via `returnRevivedCorpse` CAS and standard `beginReadyCommit`. CORPSE-only
revival restores control without a ghost. Operator-bound harness control transfers
the same mind, holds the body lease, then renews the unchanged-body-epoch binding
before standard Ready commit on CORPSE-only revival. Explicit revival clears
harness-confirmed-death before mob-state events.

Camera snapshots where owner differs from body follow the exact UUID's
LivingEntity/body eye position; END restores the exact body ID before standard
controller return. FrameworkNativeRenderMixin updates the exact observed living
body. These backend facts are not authenticated lifecycle/Ready transport proof.
Source visibility staging precedes world-add for owner and ghost observer; no
alive-viewer leak is established by source inspection, not connected proof. Any
observer session blocks healthy-carrier actions, raw movement, and client
interaction prediction; C/F5 and fresh ghost-direction/R semantics remain separate.
Legacy offline login still uses the old direct-ghost-session path, and legacy R
does not use medical observer; new corpse-first login is not established.

Fog approximates a body-centered awareness radius with bounded third-person
camera-offset compensation using Minecraft distance-fog planes; it is not an
exact radial body-centered shader. The radius interpolates from 16 blocks at the
resolved Critical threshold to 1.5 blocks at the resolved Dead threshold. These
are Minecraft presentation values, not SS14 prototype fields. The far-plane
camera offset remains capped and can make third-person visibility extend beyond
the body-centered radius depending on camera position. Both planes tighten
together, preserving stricter environment/eye fog. Critical contribution clears
on Dead and while ghosting. Connected visual verification remains outstanding.

Crawler damage threshold consumes the upstream knockdown-refresh rule:
`SharedStunSystem.Knockdown.cs:496-503,195-202`. It refreshes an existing down
deadline to at least now + default duration without enabling voluntary auto-stand.
Get-up DoAfter's damage threshold is independently 5, not that component field.

Native Minecraft main/offhand slots remain separate owners from LiveHands.
Availability is the union by resolved left/right ID (main-arm aware); medical
drops use exact source-slot extraction and loaded loose-item publication with
unadmitted-only compensation. No stacks are copied into typed hand roots.
These are Minecraft adaptations. FakePlayer notification and trusted-backend
fixtures do not prove authenticated lifecycle/Ready transport or connected native
hurt/packet/camera behavior. Focused new fixtures are 12/14 passing (retry running);
readiness remains PENDING. See SPRINT.md and MANUAL.md.
