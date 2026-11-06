# R45 — ownership lost while body chunk was not entity-ticking

**Filtered1/895 selected,1 completed,0 passed,1 failed,0 unfinished.** Compile
and fresh preparation passed; effectiveSDK2 was reached. Preserve R45 and all
earlier used/abandoned evidence. This is not readiness or M1 acceptance.

## Ordered original-body witness

Body `f88f682d-3c98-4540-9df2-91c19a982022`, frame
`3d9ffd95-1879-4103-8d1b-caa64de5bac5`:

| Observation | Level gameTime53, UNLOAD_CALLBACK | gameTime55, SAVE |
|---|---|---|
| Native owner | Exact callback reference | null |
| Registration/removal | Registered, not removed | No current owner |
| Physical entity-ticking | false | false |
| Physical chunk snapshot | FULL | absent |
| World Y | 302.93765642022856 | 303.8895360902377 |
| Own velocity Y (blocks/tick) | 0 | -0.0784 |
| Grounded | true | false |

Position changed **upward by0.951879670009m**, despite downward velocity at save.
Do not call this a0.95m downward displacement or equate instantaneous own
velocity with carrier/world displacement. There were **no BEFORE_BODY_SUPER or
AFTER_BODY_SUPER events**, and no TRACE_ERROR/TRUNCATED markers. Ownership was
present at the SDK callback and absent at save; the trace does not identify the
exact clearing call. Do not attribute this observed interval to stock base tick.

The native frame and all body/cargo UUIDs actually unloaded. Fixture stimulus27
measured rise0.984771728515625m and upward native speed about19.36m/s; OFFLINE28,
actual unload30, quiet through49, first observed reload50, failure100. These
fixture ticks are distinct from level gameTime above. Ordinary held payload
velocity about18.73m/s and first observed reload16.85m/s do not establish exact
pre-first-integration velocity conservation or physics while unloaded.

## Final verification boundary

The reconstructed body was alive, grounded, not removed, normal gravity/physics,
with the same UUID and exact current frame/native tracking. Binding, Mind,
ACTIVE profile, hands, held token and held stack checks logged true. However,
`restored=false` and local-point distance **1.498351334m** exceeded unchanged
**0.12m**. Do not claim full body continuity, health-value conservation, every
other unlogged conjunct, inventory loss or successful initialization.

All three cargo roots saved FRAME/unloaded anchors, initialized and reached the
strict cargo checks before reconnect. Natural observer: SAVE/READ/JOIN1 each,
50 post-JOIN samples,3 impact candidates and0 spills per jug;168 events,
coverage complete without overflow/capture loss. This is bounded cargo evidence,
not whole-fixture or long-term continuity.

## Smallest source-backed correction to test next

`MoonStation14.onServerTickPost` calls `OwnedLivingPhysics.tick` independently of
vanilla entity ticks. That dispatcher iterates all loaded entities and can call
`GroundedHarnessWorldStep.passiveStep` → `SableOwnedBodyMotion.move` even when a
physical chunk is not entity-ticking. `ExternalBodyFlight.tick` is another
out-of-band body movement dispatcher. No runtime entity-ticking admission helper
was found. The unguarded path is a concrete scheduling defect and a plausible
cause here; R45 does not prove which call cleared native tracking.

Use the existing nonforcing `isPositionEntityTicking` API as dispatch admission
before passive/out-of-band physical attempts. Preserve normal active/ticking
movement, one-attempt rules, own velocity/gravity/rotation and existing native
membership. Do not add staged actor anchors, recovery placement or historical
ownership fallback. Test actual guarded call paths, then a fresh default-two
three-case reconstruction diagnostic; do not reuse R45 or automatically rerun.

The narrow correction now adds current-position entity-ticking guards to
`OwnedLivingPhysics.tick` and `ExternalBodyFlight.tick`; no anchor or gameplay
physics rule changed. Author compilation and all eight focused JVM classes
passed **51/51**, zero skips/failures/errors. Offline combined-tag preflight
selects **3/895**. Strict preparer Python14/14 passed; fresh R46 remains absent
and unprepared. Independent final source/ASM/plan review passed. Its validation
caveat referenced the earlier partial32-test subset; the subsequent eight-class
XML evidence above supersedes that subset. Native continuity validation remains
separate and unverified.

Stopping and all-dimensions-saved markers appeared. No repeated writable-book/
lectern-save error, crash or diagnostic truncation/error was found. Missing
fresh-directory `server.properties` and three unknown `create:flywheel` startup
tag errors remain separate findings; this does not diagnose R26's save hang.

## Bound evidence

- Candidate `shuttle-feedback08-body-unload-order-20261016-r45`.
- Snapshot `b329e5c6297387c532249cc53f8281fae9c6ed26b6be72e32c2ce2f7de941c34`.
- Frozen plan `GATE-PLAN-08-BODY-UNLOAD-ORDER-R45.json`.
- Reports root `build/agent-workflow/shuttle-relocation-sprint/reports/`.
- Receipt `shuttle-feedback08-body-unload-order-20261016-r45-gate-validation.json`,
  SHA-256 `9ad0d9b33aa2162447d41414bc5effb5e1f078154da062b45cd126f9bfd02c00`.
- Raw `feedback08-body-unload-order-r45-native.log`,336,652 bytes,
  SHA-256 `291caa066b4bacaf4d7f34ef750bb769d4fe5a82b66031aab6c36961fcef63db`.
- Raw stimulus1266, OFFLINE1270, callback1278, save1279–1283, unload1285,
  quiet1289, first reload1292, final body facts1311, failure1312–1313,
  observer1316, stopping1325, saved1339. Independent audit verified hashes,
  counts and the observed callback-to-save ownership-loss boundary.
