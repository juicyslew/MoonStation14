# M1 fix design — authoritative frame anchor during reconstruction

## Cause and scope

R41 proved that reconstructed jugs had no native tracking owner, resolved as
world actors, fell, then legitimately broke/spilled. Existing presentation
orientation and last-safe storage points are not current ownership authority.
The body serializer likewise has no frame anchor and R41 observed absent body
tracking, although its final reconnect was not reached. Restore entity transform
composition at initialization, not by changing collision/break rules or repairing
entities after gameplay. Prototype values/assets and Mind/inventory identities
are unchanged; this is an existing persistence bugfix, not new ship behavior.

## Shared owner

Introduce one small shared typed frame-anchor value/codec and initialization
helper for the two current callers: `LooseItemEntity` and
`PlayerCharacterHarnessEntity`. Reuse `projectActorPoint`, exact native Sable
ownership and existing loaded-only initialization probes. Do not copy the
resolver/codec into each entity, add another position solver or use render
orientation as physical membership. Serialization ownership and live gameplay
query readiness have different prerequisites (see R43 correction below).

At ordinary save, capture the **currently validated native actor frame UUID and
storage point** using the exact live owner or a proven ordinary-unload snapshot.
Save an explicit authoritative anchor compound. WORLD actors save no frame anchor;
legitimate departure therefore cannot leave a stale last-safe/render anchor.
Omit/remove the compound only for a **positively known WORLD** actor. A null
serialization ownership result is UNKNOWN, not WORLD: persist an explicit
unresolved state rather than silently erasing ownership. Present malformed/unresolved state
reloads as pending UNKNOWN; no fabricated point, nearby frame or historical
presentation fallback. Shared typed state must preserve these distinctions.
An entity still awaiting initialization preserves its original pending anchor
rather than dropping ownership during another save. No compatibility/migration
or restoration from older presentation-only fields is required.

On ordinary read, decode to pending initialization state without projecting or
moving the entity inside `readAdditionalSaveData`: base loading may still have
position work remaining. Missing anchor means ordinary WORLD behavior, not
proximity discovery. Invalid anchor is an explicit unresolved/invalid witness,
not permission to choose a frame or fabricate zero coordinates.

## Initialization, not recovery teleport

At each owned entity's server tick **before** its existing simulation/super tick,
resolve only the saved exact frame and resident storage point. Validate the
projected world point against existing border/residency boundaries. Then set its
initial world placement through that frame's current pose and restore the normal
native tracking association together, before any own motion/collision or carry
attempt. Use the existing pinned native interface, with compiled typed API checks;
no new engine/native/physics hook or extra `Entity.move` call. Preserve saved own
velocity, gravity, rotation, onGround decisions and normal one-carry behavior.
Never repeat initialization after ordinary gameplay simulation has begun.

If the exact frame/storage is not yet available, this entity has not completed
deserialization of its frame-relative transform. Keep its pending anchor and do
not begin ordinary motion or authority checks. Do not zero velocity, set
noPhysics/noGravity, force chunks or select a nearby frame. Emit a bounded
explicit initialization-wait witness and readiness state, not a silent ongoing
gameplay freeze. Shared actor queries return UNKNOWN for a pending anchor so
reconnect/interaction cannot mistake it for WORLD. Once resolved, initialization
clears pending state exactly once and ordinary simulation proceeds unchanged.
This dependency barrier exists only for reconstructed saved anchors, never for
ordinary resident-frame loss or ongoing native movement.

## Native correspondence and checks

Pinned Sable source exposes transient native tracking. Its internal tracking
setter assigns owner/UUID, not position. `EntityStickExtension`'s staged plot
projection runs at base `Entity.tick` return and can discard an unknown frame;
custom loose-item ticks do not run that base path. Do not rely on it accidentally
hydrating cargo or apply a second projection after shared initialization.
`PlayerCharacterHarnessEntity.tick` calls `super.tick`; hydrate before it, not
after body movement. No exact first-restored native velocity claim is required;
configured source retention0.9 remains separate from own motion and identities.

Focused tests cover anchor codec/current-owner capture policy, WORLD departure
clearing, pending-save preservation, missing/invalid/unknown versus ready state,
and single initialization/no post-simulation recovery. Native validation must use
the existing combined offline-body/three-jug fixture with its unweakened flight,
actual `UNLOADED_TO_CHUNK`/absence/non-FULL/quiet, UUID/Mind/hands/token/content,
settled/deck support and `.12` local-position oracles. Add explicit saved-anchor
and initialization witnesses rather than manually assigning fixture tracking.
Stationary reconstruction regressions are relevant because both entity serializers
change. Cheap checks and independent review precede a fresh lead-owned gate;
no author fixture preparation, R38–R41 reuse or automatic rerun.

Production/default remains2. M1 is not accepted; M2–M6 remain paused. Broad/native
and connected-client readiness remain separate future requirements.

## Original author candidate (before R42/R43 diagnostics)

`FrameAnchor`, `FrameAnchorState` and `FrameAnchorPending` now provide the shared
typed WORLD/FRAME/UNRESOLVED codec/state and one-time initialization owner. Cargo
and body delegate their serialization and pre-simulation initialization to it;
pending actor queries return UNKNOWN. Current authoritative capture is separate
from render orientation. The existing natural cargo observer includes the new
anchor compound; fixtures add initialization/read-anchor and retained-local-point
witnesses without setting tracking or positions themselves.

`compileGameTestJava` passed. Focused JVM checks: **32/32**, zero failures/errors/
skips (anchor8, watch7, payload5, reconnect history1, loaded-body resolver11).
Strict preparer Python14/14 passed; R42's target was confirmed absent and R38–R41
remain retained. Offline combined-tag preflight selects **3/895**. Independent
final source/reuse/plan review passed after correcting the UNKNOWN query guard
to use shared pending status, not nullable anchor value; unresolved and valid
pending states are both UNKNOWN. The fresh R42 diagnostic is still needed.
No native result or complete continuity fix is claimed.

## R43 correction — persistence is not gameplay readiness

R42 failed in fixture setup; see `FRAME-ANCHOR-R42-RESULT.md`. Corrected R43
reached real unload/reload but saved explicit UNRESOLVED anchors and never
initialized: see `FRAME-ANCHOR-R43-RESULT.md`. It does **not** identify which
live-query guard failed at save. `currentActorPoint` properly requires a live
registered owner and FULL storage for gameplay, but serialization of an exact
native frame transform need not require storage block residency.

The serialization-only correction has two proof paths:

- **LIVE:** exact native owner reference, level and UUID match the live container,
  owner not removed; derive storage coordinates from its authoritative pose and
  validate finite values within that owner's allocated plot. No chunk probing,
  loading, hull-bound membership inference or motion changes.
- **UNLOADED:** that same retained native owner must have an explicit ordinary
  `UNLOADED` lifecycle witness, with a copied pose and plot allocation from before
  removal. Copy `sub.logicalPose()`, matching the normal Sable serializer; do not
  use a potentially pushed level pose-provider override for this snapshot.
  Require exact owner identity, not merely a remembered UUID, and reject
  a different live instance even with the same UUID. Destructive `REMOVED`
  invalidates proof. No witness means UNKNOWN, never cached-render recovery.

Use the existing public SDK `SubLevelObserver.onSubLevelRemoved` lifecycle API,
not a new physics callback, engine patch or mixin. Install once per level from
the shared pre-simulation initializer, including fresh WORLD actors, before
teardown can occur. Keep snapshots weakly keyed by native owner; snapshot values
must not retain that owner/plot/level through captured references. Clear and
deactivate via the existing level-unload handler: the pinned SDK exposes
`addObserver` but no public removal method, so the container retains an inert
observer until its own disposal. Do not access its private observer collection.
This is immutable unload provenance,
not a per-tick position cache or another live movement authority.

Pinned public Sable source at `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`:
`SubLevelHoldingChunkMap.processUnload` saves holding data before container
removal; `SubLevelContainer.removeSubLevel` notifies observers before `onRemove`
and unregistering. `SubLevelSerializer.toData`/`serialize` use a copied
`logicalPose`; independent source review found the first implementation's
pose-provider fallback unsuitable for unload proof. Live capture keeps the
authoritative provider behavior used by gameplay queries.
`ServerSubLevelContainer.removeSubLevel` distinguishes
`REMOVED` (delete entities, queue deletion) from `UNLOADED`. Holding-entry
presence alone is **not** proof: queued deletion need not immediately erase an
old holding entry. Public source review and typed compilation do not prove
source/binary equivalence or actual Minecraft save-callback order.

Leave all gameplay queries, loaded-only hydration, pending UNKNOWN semantics,
own velocities, jug impacts, gravity, rotation and one-move/carry rules unchanged.
Native validation must identify the actual serialization proof branch for both
entity types and require full initialization/reconnect outcomes, not accept
merely surviving in pending state. No additional run is authorized by a config
change alone; fresh reviewed diagnostic submission is required.

The first implementation compiled but was incomplete: installation occurred
only during save, not before the shared initializer's fresh-WORLD return, and
new capture/lifecycle coverage was missing. Subsequent changes install before
the fresh-WORLD return, deactivate on close and route runtime writes through the
tested capture-status serializer. Bounded `[SableFrameAnchor] SAVE` witnesses
name the actual proof branch and anchor, rather than logging every autosave.
`OwnershipProofs`, within `FrameAnchorCaptureService`, now owns both actual
removal transitions and capture decisions; its copied snapshots allow focused
tests without constructing native server worlds. This is the runtime path, not
a parallel test implementation. The unused strong container reference was
removed so the weak level registry has no value-to-container-to-level cycle.

R44 author checks: `compileGameTestJava` passed; **46/46 focused JVM tests**,
zero skips/failures/errors (capture8, state10, fixture phase4, watch7, payload5,
history1, body resolver11). Capture tests use valid FRAME controls before
identity/level/UUID/removal rejection checks and verify snapshot copying,
finite/allocation checks, close/late removal and actual runtime delegation.
Independent source/architecture/plan and final corrected-test review passed.
Strict preparer Python **14/14** and scoped diff checks passed. The same three
native fixtures remain unchanged; their last selector evidence is3/895, not a
new native result. R44 is a fresh default-two diagnostic target, confirmed
absent before submission; R38–R43 remain retained. These author results establish
neither actual save-callback branch/order nor successful native hydration.

## Missing unload-proof correction

The later local diagnostic recorded three cargo saves as
`UNRESOLVED/unload-proof-missing` while readiness was otherwise true. The gap
was lifecycle timing: ordinary WORLD initialization correctly returns without
setting up frame ownership, while installing at save time is too late if Sable
has already removed the frame. `MoonStation14` now installs the same
idempotent `FrameAnchorCaptureService` at server `LevelEvent.Load`; its existing
`LevelEvent.Unload` handler still closes it. The service itself declines
installation until `SubLevelContainer.getContainer(level)` is available, and
repeated installation reuses its active per-level instance. This does not alter
WORLD guards, cargo ticks, proof rules, or native physics.

Installed-bytecode ordering was verified against Sable
`sable-fg9dTRz9.jar` (the workspace's pinned cached artifact) and
NeoForge `neoforge-21.1.228.jar`. Sable's
`dev.ryanhcode.sable.mixin.plot.container.ServerLevelMixin` constructor creates
the server `ServerSubLevelContainer`, invokes
`Sable.defaultSubLevelContainerInitializer`, and only then finishes the mixin
constructor. The initializer installs the physics/tracking observers and returns
the container. NeoForge's `MinecraftServer.createLevels` constructs each
`ServerLevel`, inserts it into the levels map, then posts `LevelEvent.Load`;
the overworld follows the same order before its load event. Thus Load runs after
Sable container construction/initialization and before normal server level use,
so the lifecycle installation's container-ready premise is supported by the
installed artifacts. Cheap verification used `javap -p -c` on these artifacts;
no native/GameTest server was run for this check.

## R45/R46 — tick admission before additional persistence state

R44 established cargo FRAME/unloaded serialization and initialization, but both
bodies saved WORLD and failed final reconnect. R45's bounded trace then observed
the exact registered body owner at the SDK callback and null at save two level
ticks later. The physical chunk was not entity-ticking at either observation;
there were no body-super traces/errors/truncation. Body world Y increased about
0.952m. See `BODY-UNLOAD-ORDER-R45-RESULT.md` for bindings and the distinct own
velocity/displacement units. Do not attribute this interval to base tick or call
the upward displacement a fall.

`OwnedLivingPhysics.tick` dispatches passive movement from all loaded entities
outside native entity ticking; `ExternalBodyFlight.tick` also dispatches physical
steps outside entity ticks. Add current-position `isPositionEntityTicking`
admission at those dispatchers before physical attempts or advanced/consumed
marking. Reuse the normal nonforcing level API; no new scheduler, staged actor
anchor or native-pointer repair. Keep active input and normal ticking-body
physics unchanged. This fixes a concrete admission defect, but the exact R45
clearing call remains unobserved until further evidence.

Do not introduce actor-to-frame unload staging unless normal scheduling still
cannot preserve ownership and its invalidation is separately justified. A cache
of former association is not automatically current authority after legitimate
WORLD departure. The existing shared exact-owner/unload-proof serializer and
loaded-only one-time initializer remain unchanged by the admission correction.

## R46 remaining composition gap

R46 passed both stationary cases. Flight retained its exact native owner, saved
FRAME/unloaded and initialized, but its unchanged worldY301.984777 was inverted
through a newer frame pose to localY128.047118 rather than near129. See
`BODY-TICK-ADMISSION-R46-RESULT.md`; do not copy R45's different world position.

Reuse the existing native movement completion's `ActorPoint` as actual completed
physical composition in the actor-owned state. This is not a last-safe cache:
fresh native physical results qualify even if a higher-level motor later rejects
its velocity, since the accepted physical position was not rolled back. Both
living and incapacitated body consumers already share `SableOwnedBodyMotion`.

A qualifying commit stores immutable world/storage points, frame UUID and exact
native owner/level identity. Fresh valid exact-owner results update it; no-fresh
results may only preserve an unchanged point/owner, never reinvert against a
newer pose. Unknown results invalidate; verified WORLD departure and entity read
clear old evidence. At save, exact actor world-position equality and exact live
owner or ordinary-UNLOADED proof are mandatory. Use the committed storage point,
not a newer-pose inverse; stale/mismatched evidence is UNKNOWN, not fallback.
Never use it for ongoing queries, motion, rendering or post-gameplay placement.

Keep default non-committed cargo capture and loaded-only initialization. Before
any new native run, complete state, shared native-completion wiring, reused
service qualification, bounded commit/save provenance, positive-control focused
tests and independent review. Prove a qualifying body commit exists before
unload; architecture review alone does not establish that. No new physics hook,
move attempt, actor unload-staging authority or pointer repair is authorized.

R47 author implementation now wires this state at the shared native completion
and consumes it through the existing serializer's shared owner qualifier.
Default cargo inversion remains unchanged; committed capture never reinverts
through a newer pose. Focused compile/JVM62/62 (nine XMLs, no skips/failures/
errors), exact three-tag offline preflight3/895, Python14/14 and independent
final source/test/control-flow/plan review passed. See the R46 result document
for per-class counts and corrected earlier preflight caveat. R47 still needs a
fresh native diagnostic proving a valid pre-unload commit and full strict flight
and stationary outcomes; no runtime continuity or readiness claim follows yet.

R47 subsequently completed3/895: both stationary cases passed; flight failed.
It proved a valid pre-unload movement commit, committed-unloaded serialization
at localY129 and faithful hydration onto the deck. First post-hydration motion
committed129.153900 with a rejected motor step; final current localY129.545472
still failed. The fixture's OFFLINE current-pose inverse baseline differs from
movement-time composition, so its1.498m distance is not a clean new-drift metric.
This does not erase the remaining discrepancy from saved129. See
`FRAME-COMPOSITION-R47-RESULT.md` for verified facts, comparison-basis caution,
receipt binding and the observational-only next investigation. No physics or
oracle change is justified by the current evidence.

R48 author work adds observational, opt-in post-hydration movement witnesses at
the same native completion seam: raw pre-own/post-own/final points, existing
MoveResult/contact/pose description, distinct inherited displacement versus
frame-velocity units, and explicit original fixture baseline. Existing per-level
diagnostic scope arms only living FRAME initialization; samples require successful
hydration, stop after64 plus one truncation marker and disable only diagnostics
on formatting failure. No new movement/query/ownership/initialization policy or
oracle change. Author compilation/JVM70/70 across nine verified XMLs, exact
offline preflight3/895, Python14/14 and independent source/test/plan review passed;
native coverage remains unverified. The R47 result document records an overwritten
four-test source incident, failed recovery and explicitly reconstructed current
guard coverage; do not claim byte-exact restoration of those historical tests.

R48 submission was subsequently operationally interrupted with **no technical
verdict**. Its separate child-ownership/cleanup quarantine remains unresolved;
the phrase "owned child cleanup verified" is not clearance. No bound logs or
target directory were present at inspection, so no native selection/outcome is
established. See `POST-RESTORE-MOTION-R48-OPERATIONAL-BLOCK.md` for exact binding
and restrictions. Continue offline investigation; no retry/replacement native
run until explicit verified clearance, then use a fresh candidate/plan/target.

Offline continuation reproduced the first R47 collision-box center as original
center plus `.4*(ownRequest+inherited)`, and the recorded ceiling overlap closely
matches its MTV. `RESTORED-BODY-COLLISION-PHASE.md` preserves exact numbers and
limits: only full own request plus correction reconstructs measured own motion;
the callback's partial motion is not the full request. Three new geometry
evidence tests reuse existing snapshots/JOML and pass within a focused9/9 class
run; no runtime change or live phase-correctness claim. The prior70 author XMLs
are preserved under `reports/r48-author-jvm70/`. Quarantine remains unresolved.

On2026-10-20 the owner explicitly retired all legacy in-flight work/holds and
directed new validation. This supersedes the historical quarantine restriction,
not the absence of an R48 verdict. New coordinator intake requires complete
owner-ready work; no unfinished diagnostic submission or logging-only milestone.
Complete the bounded body/cargo reconstruction correction first, with focused
checks; a genuine complete milestone may then use the new scope/request flow.
See the R48 block document's dated cutover disposition. M1 remains unaccepted;
M2–M6 remain paused.
