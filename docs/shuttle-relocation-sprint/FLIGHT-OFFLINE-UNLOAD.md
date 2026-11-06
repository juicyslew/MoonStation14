# M1: flight at ordinary offline chunk unload

## Next bounded question

R24/R26/R27 establish stationary ordinary cargo/body reconstruction; R36's
moving/falling reconnect cases do not establish actual chunk unload during
flight. Reuse the existing combined lifecycle/cargo fixture to test a living
body, exact Mind/hands/held bottle and its three existing jug roots through
**nonzero native flight at serialization**, actual absence, reconstruction and
normal reconnect. A launch followed by landing before unload is not that proof.

Use the unchanged **default two substeps**, not implicit six-step adoption. One
ordinary upward20m/s launch after existing cargo readiness is sufficient as an
initial stimulus. Prior successful stationary unloads confirmed absence one tick
after release (R24 ticks21→22; R26 27→28; R27 26→27), but the fixture permits380
ticks and provides no deadline guarantee. Require a nonzero saved velocity in
the ordinary holding payload after actual frame absence; fail explicitly if it
is omitted/zero or the flight-at-unload premise cannot be established.

## Reuse and preserved behavior

- `MovingCabinAuthorityFixture`: canonical powered cabin, real lifecycle actor,
  body/Mind/hands authority, neutral-intent pump and ordinary native movement.
- `LifecycleOfflineShuttlePersistenceGameTests.OfflineWitness`: normal disconnect,
  persisted OFFLINE identity, actual old-body `UNLOADED_TO_CHUNK`, ordinary
  reconnect, changed Java object but same body UUID/Mind/hands/token/stack,
  ACTIVE profile and current frame tracking.
- `SableCargoChunkPersistenceGameTests`: create/register the same three filled
  jug roots; reuse snapshots, token/world-ledger checks, exact stack contents,
  passive absence, non-FULL physical chunks, quiet interval, reload lease,
  adopted frame identity and terminal cleanup.

The launch belongs **after the existing three-sample stationary cargo readiness
and before logout/release**. The flight variant must not re-enter the stationary
wait after launch. Existing stationary methods/default branches, exact two-step
guard, support/grounded requirements, three stable samples, `.12` local cargo
position bound and all identity/content assertions remain unchanged. Keep those
same assertions in the flight variant where they describe contact with the
ship's deck rather than terrain: a flying cabin can still support an on-ground
body and locally settled cargo. Do not remove guards merely because it is a
flight test; any necessary new policy requires a concrete physical explanation.

Log measured native upward movement/velocity before disconnect and explicit
root/token/water amounts. After actual absence read/copy only the relevant
ordinary holding data (saved UUID/pose/linear and angular velocity); do not
manually serialize/restore the ship or substitute the last pre-logout pose for
the actual saved sample. Existing fixture lease close is not unload proof:
retain frame absence, body/cargo UUID absence and `UNLOADED_TO_CHUNK`, non-FULL
physical chunks and the quiet interval before reload.

## SDK facts and limits

Public Sable source pinned to `ad8b4d377741fbd923f4d863b2cf9e462daff9a6` shows
`SubLevelHoldingChunkMap.processUnload` calling `SubLevelSerializer.toData` before
native-frame removal, skipping force-loaded frames. Its verified public-source
read chain is `SubLevelContainer.getContainer(ServerLevel)` →
`ServerSubLevelContainer.getHoldingChunkMap()` →
`getHoldingSubLevel(uuid).data().fullTag()`; confirm typed API availability
by normal fixture compilation/runtime use, not reflection or new access hooks.
Fixture compilation has now confirmed this typed chain and the SDK decoder
signatures against the installed dependency; actual holding-payload behavior
still needs the native diagnostic.
Installed-artifact bytecode inspection was unavailable: the exact Gradle cache
coordinate lookup was denied, and the older external artifact path was already
denied. **Do not retry/bypass either denial.** Ordinary SDK-consuming compilation
does not establish binary/source equivalence or serializer operation ordering.

Reuse public `dev.ryanhcode.sable.util.SableNBTUtils.readPose3d` and
`readVector3d` after explicit required-tag/type checks. Source writes UUID under
`uuid`, pose under compound `pose` (nested `position`, `orientation`,
`rotation_point`), and optional vector compounds `linear_velocity` and
`angular_velocity` with double x/y/z fields. Do not create a parallel NBT parser
or serializer. The source files are under `common/src/main/java/dev/ryanhcode/sable/`
at the above pin: `api/sublevel/{SubLevelContainer,ServerSubLevelContainer}.java`,
`sublevel/storage/{HoldingSubLevel,holding/SubLevelHoldingChunkMap}.java`,
`sublevel/storage/serialization/{SubLevelData,SubLevelSerializer}.java` and
`util/SableNBTUtils.java`.

Pinned source omits sufficiently small velocities and applies configured
`VELOCITY_RETAINED_ON_LOAD` (default0.9) on restore. This is not a promise of exact
velocity conservation or continued physics while the frame is absent. The exact
installed pre-first-integration observation seam is unverified. The first
diagnostic must report saved payload and first observed reload state separately;
do not assert their equality, infer a timestep adjustment or relax a tolerance.
It proves flight-at-save identity/content continuity if successful, not exact
first-restored velocity, process restart, connected presentation or the cause of
owner cargo loss.

## R39 result

R39 completed **1/895 selected,0 passed,1 failed,0 unfinished**. Flight-at-save
was established: actual frame/body/three-jug unload and absence at tick29, with
ordinary same-UUID holding payload upward velocity about18.73m/s. Ordinary
reload reacquired the native frame at49 and a body object was later present;
all three cargo UUID lookups remained absent at timeout248. Final reconnect
identity/content assertions were not reached. See `FLIGHT-OFFLINE-R39-RESULT.md`
for bound evidence and the failure-book save error; this is not successful cargo
continuity, an owner-loss diagnosis or approval. Preserve R39; no automatic rerun.

## Fixture revision boundary

The fixture-only implementation and focused payload checks compile; current tag
preflight selects1/895. The worker prematurely prepared R38's minimal isolated
`build/gametest-shuttle-feedback-08-flight-offline-unload-r38/config/sable-server.toml`
despite author instructions not to prepare native targets. No server/native run
or workflow submission occurred. **R38 is abandoned and retained intact**; do not
delete/reuse it to make preparation appear fresh. Its unsubmitted draft plan is
historical intent, not an admitted gate. The replacement is the never-used R39
directory and `GATE-PLAN-08-FLIGHT-OFFLINE-R39.json`, after independent review.

R38 was created by the author's preparer command with `--substeps 2`; the retained
32-byte config SHA-256 is
`b4af3f96bfb3ef7e637a623d46b514e22cffca68fb7d945ffb03d2963410dcb6`.
No native run occurred. R39 was confirmed absent before submission. Final author
validation: `compileGameTestJava` passed; focused `OfflineFlightPayloadTest`
**5/5 passed**, zero failures/errors/skips, including compiled tag selection1/895;
strict preparer Python14/14 passed. Independent source/reuse and R39 plan review
passed. Payload reads use SDK decoders, require typed finite pose/velocity fields,
and take defensive bounded snapshots; no global catalogue-total unit assertion
remains. These are author/source checks, not live serialization proof.

No production/engine/native/hook changes, velocity cap/freeze, artificial gravity,
recovery teleport, owner-state edits or used-fixture rerun is authorized. R38 is
not runnable under this plan; R39 is now used and must not be reused. Any further
native diagnosis requires an adequately justified fresh plan/candidate and
never-used fixture after correction/review. M1 remains unaccepted; M2–M6 remain
paused.
