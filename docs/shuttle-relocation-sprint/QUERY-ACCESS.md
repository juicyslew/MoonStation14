# Loaded frame-ray query boundary

`LoadedBlockGetter` is the single bounded `BlockGetter` for current body item and
block-use server queries. Both body consumers use `getChunkNow`; missing/unbounded
reads fail closed. The getter intentionally remains a non-`Level` object and invokes
normal `BlockGetter.clip(context)`. In pinned Sable 2.0.6
`ad8b4d377741fbd923f4d863b2cf9e462daff9a6`, `BlockGetterMixin.clip` routes that
branch to its private original vanilla traversal. No traversal/shape/fluid kernel
is copied and no unrestricted `Level.clip` is used.

`LoadedFrameRayQuery` applies the same clip to terrain and inverse-projected
storage-space endpoints. Candidate frames come only from
`Sable.HELPER.getAllIntersecting(Level, BoundingBox3dc)`; pose comes from
`LevelPoseProviderExtension.sable$getPose` when available, otherwise the live
logical pose. Inverse transform outputs are global storage positions. Reads use
the parent `ServerLevel.getChunkNow(globalX, globalZ)` only after all intersecting
chunks pass the candidate's own plot ownership test. There is no plot-center
offset, force load, custom broadphase or duplicate transform kernel.

Results retain a typed unknown outcome separately from miss/hit. A frame hit
records storage block/local face, actual world ray point and exact engine UUID;
the UUID/plot ownership is checked again by `revalidate` before the block-use
probe accepts that nomination. World and frame hits are compared by squared
world-ray distance, so nearer ordinary terrain wins. Item target ordering,
authority, reach ceilings (item 5, block 6 Minecraft blocks) and existing
one-chunk item-visible/block-use bounds versus 0.125-block throw-aim margins are
unchanged. Dynamic block
outline contexts continue to pass the real `ServerLevel` to shape callbacks;
arbitrary mod callbacks may independently read/load the world and are not
sandboxed by this getter.

SS14 source correspondence: pinned `SharedInteractionSystem.InteractionRange`,
`InRangeUnobstructed`, and `UserInteraction` use transformed current positions,
fixture-nearest entity checks, map identity and obstruction separately from
action permission. This adapter adds no range/prototype fields and preserves the
existing local MAX 5/MAX 6 defensive ceilings rather than treating them as the
SS14 1.5 m default. `currentTargetPoint` uses `SubLevelContainer.getPlot(ChunkPos)`
to identify the storage plot owner, then the exact live sublevel pose; ordinary
world positions remain world-space block centers. It requires an already loaded
block and preserves the frame UUID. `BodyBlockUseTargetProbe` measures its existing
eye-based six-block policy to that transformed point and clips LOS through the
same loaded query. `BodyInteractionAccess` uses the shared query with no added
terrain chunk halo while retaining root-to-root reach and eye-to-eye collider
semantics. Entity-target menu continuation/device identity and client
outline/prediction remain follow-up integrations, not claimed by these tests.

Focused runtime proof: `LoadedFrameRayQueryGameTests` uses the existing Sable
fixture allocator/scene and an actual bound human; it checks a rotated frame hit,
normal block-use and item-aim margins against minimally allocated storage, nearer
terrain priority, and stale UUID rejection. Its missing-storage case admits two
authored storage regions separated by a never-allocated chunk inside the frame's
reserved plot bounding volume. The engine bounding-box ray broadphase admits that
live frame; lazy projected reads encounter the owned-but-unloaded gap, return
UNKNOWN, and leave `getChunkNow` null. Positions outside the frame's occupied query
domain or outside its own plot are known empty; an absent chunk still required
inside the intersected owned domain is unknown. No storage halo is force-loaded.
The test expands the reserved plot bounds only after native body registration and
placement finalization, so it doesn't perturb Sable's initial native mass/pivot.
Focused native and full-suite evidence is recorded in `EXECUTION.md`.

The body-target fixture verifies transformed reach/LOS, refusal after frame removal,
clear ordinary-world body-pair LOS, and refusal through an authored projected steel
hull. A separate real bound-human test applies one bounded native velocity change
and observes the same storage block/frame UUID at a changed current world point;
the existing reach/LOS consumers admit against that updated point. Body-to-body
checks retain root-distance and eye-to-eye collider policy and use this bounded
frame-aware clip with zero added chunk halo. Current focused native evidence is
14/800 passed and the full current native suite is 800/800 passed; unit/build
evidence is in `build/shuttle-access-final-build.log`. Connected client interaction,
menus, and arbitrary dynamic-shape callback sandboxing are not established.

Lifecycle-backed hand authority fixtures additionally require a fresh disposable
GameTest save: `LifecycleHandActorFixture.start` checks the immutable startup snapshot
is `EMPTY` or `UNINITIALIZED` before creating/registering an actor. A reused save with
offline lifecycle claims is correctly `DEFERRED` and cannot exercise first-account
enrollment with new random fixture UUIDs. The fixture refuses that setup without
clearing or rewriting owner lifecycle data; use a new `ms14GameTestDir` for such runs.

## Minimum-cabin machine menu continuation

### APC UI continuation parity correction

An APC menu open still requires the actual initial body-eye ray admission through
`BodyBlockUseTargetProbe.probe` for the nominated block and face, exact lifecycle
authority, active empty issued hand, and exact loaded APC block entity. Once open,
continuation is not aim-locked to that original face: `ApcMenuService.validBodyContext`
rechecks exact actor/menu authority, `canAct`, containment policy, FULL loaded chunk,
bound APC block entity/kind, and the captured expected frame UUID, then applies
current root-to-current-device range (`PowerDeviceRules.canToggle`) and current
aim-independent unobstructed LOS (`BodyBlockUseTargetProbe.lineOfSight`). The
vanilla player/carrier path remains unchanged. Alarm continuation likewise retains
its aim-independent current mapped point, power, link, authority, and LOS checks.

This follows pinned SS14 commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`:
`Robust.Shared/GameObjects/Systems/SharedUserInterfaceSystem.cs` lines 1163-1204
and 1234-1261 close active UIs using mapped range events, not head/cursor/clicked
face; `Content.Shared/Interaction/SharedInteractionSystem.cs` lines 1442-1449
routes the check through `UiRangeCheck` lines 193-205 and
`InRangeAndAccessible` lines 1287-1304 (current mapped positions, unobstructed
range, accessibility). Initial menu activation remains a separate aimed interaction.
The earlier local continuation requirement to keep probing the original APC face
was an incorrect parity restriction and is removed. This is not a shuttle-specific
exemption; the same current-point policy applies to world and frame body menus.
The existing local eye-based six-block query ceiling remains an adaptation guard;
it is not substituted for APC root range or SS14's unit conversion. No new
prototype fields/defaults are introduced. Initial body open uses the existing
issued-hand and authority route; subsequent UI action checks retain their current
service validation and breaker policy.

Existing focused APC/alarm reach and menu fixtures remain relevant regression
coverage. Moving-frame body-authorized service-action evidence is recorded in the
current task's GameTest logs; it is explicitly a headless server mock actor, not
connected-client UI proof. Client rendering/prediction and airlock tool access are
outside this slice.

### Airlock tool continuation and timed actor coordinates

Airlock panel/pry sessions retain the initially admitted target frame UUID and
revalidate it with the exact controller/door state and shared
`AirlockToolLineOfSight`; that service uses `LoadedFrameRayQuery`'s current
loaded target point and bounded OUTLINE clip rather than unrestricted world clip.
For a native frame in free fall, diagnosis found the expected Sable frame in the
engine broadphase and the full inverse-projected `LoadedBlockGetter` OUTLINE clip
hit the lower airlock, while the frame clip returned `MISS`/no frame hit. The ray
began inside `sub.boundingBox()` but its target and the leaf's actual clipped
surface lay below that tight native collision AABB; `rayExit` shortened the
frame-space segment before it reached the known loaded door. The shared query
now clips a nominated frame against the union of its native bounds and its own
plot bounds projected with the pinned Sable companion `BoundingBox3d.transform`
through the same current Sable pose. Candidate discovery
still comes only from `Sable.HELPER.getAllIntersecting`; the plot envelope does
not enumerate other frames, widen loaded storage reads, force-load chunks, or
turn UNKNOWN into MISS. Regression coverage completes the registered 100-tick
free-fall pry at a 124-block world displacement with the same frame ID, stable
storage coordinate, healthy normal HUMAN, known 0 W, and no LOS rejection.
This does not establish connected-client behavior or general unbounded free-fall
world interactions.

The captured failing sample was especially discriminating: the door frame was
already among the intersecting candidates, `LoadedBlockGetter` clipped the full
inverse-projected ray to the lower root block, but the world result was `MISS`
and the frame result null. Native bounds began at world Y 227.0909 while the
lower-root center was Y 226.7908 and the known storage hit projected to Y 226.9617;
the actor eye was Y 227.8208 (eye-to-root distance squared 10.0609). Current
provider pose equaled logical pose, both door halves had the same frame UUID,
chunks were entity-ticking, health was 20, and storage remained loaded. Thus this
was the nominated frame's narrow native AABB clipping a real storage hit, not a
missed broadphase candidate, unknown storage, range, or authority failure.

The generic timed-interaction runtime captures the actor's native Sable tracking
sublevel and inverse-projected storage coordinate. Movement cancellation compares
local displacement while the exact tracking UUID is unchanged, so rigid frame
translation/rotation does not count as voluntary movement. On a frame transition,
the captured coordinate is projected through the still-live old pose and compared
in world space; missing/removed frames fail closed. This is a Minecraft native
tracking adaptation, not Robust Transform parenting. The 0.3-block default and
explicit caller thresholds are unchanged. Pinned SS14 `DoAfterSystem` captures
`user.Transform.Coordinates`; `EntityCoordinates.TryDelta` uses local positions
for a shared parent and map coordinates otherwise. No blanket moving-grid
cancellation exception is introduced. Connected-client interaction remains
unverified; headless service execution is not client UI/authentication proof.

Current native airlock proof covers three live HUMAN routes in the authored
powered Sable cabin: screwdriver panel DoAfter completion while the frame carries
the body, crowbar pry completion and own-walk cancellation (grounded native-walk
corridor only), and registered wire-menu open/query/CUT while frame motion
continues. The wire test captures the actual open/session and initial opaque rows,
submits normal wire QUERY and CUT payloads, and verifies row mutation under the
same window/session plus current frame UUID and unchanged body/tool ownership. It
then refuses continuation of that captured session after normal same-storage-root
door replacement with a different block-entity object and UUID. The wire menu
replacement case is directly tested; nearer-frame-wall session refusal remains
covered by the existing geometry admission fixture, not a second wire-session
continuation assertion. The panel-open wire setup is an explicit fixture
state mutation; opening the panel through the admitted timed route is separately
covered by the screwdriver test. These are headless mock-connection/server tests,
not connected-client screen, render, prediction, or production account/network
proof. Generic 0.3 movement threshold, same-frame storage-point policy, and
airlock continuation validation are verified here; parity for every generic
world-walk, held-tool-change, and DoAfter target-retarget variant is not claimed.
