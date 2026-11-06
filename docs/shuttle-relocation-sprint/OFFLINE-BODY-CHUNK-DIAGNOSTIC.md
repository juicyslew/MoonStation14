# Offline body and cargo: ordinary chunk persistence

## Scope and status

R25 adds `LifecycleOfflineShuttlePersistenceGameTests` and a narrow optional
offline-body witness to the existing cargo phase. Both compile and passed
independent source review; native validation is pending. Expected filtered2/893:
`shuttle-offline-body-chunk-persistence,shuttle-cargo-chunk-persistence`.
The cargo-only regression is required because its shared phase source changed;
R24's valid original receipt is retained, not rerun against its old snapshot.

This is stationary, same-process logout/unload/reload/reconnect. No flight,
process restart, client, Creative-body, owner-loss fix or M1 readiness claim.
No production code, visibility, physics, prototypes, settings or Sable internals
change. Default substeps2 remains mandatory for this diagnostic.

## Proof boundary

Reuse `MovingCabinAuthorityFixture` for the resolved furnished cabin and ordinary
enrolled body, `FixtureHeldTool` for a held glass bottle, and
`LifecycleHandActorFixture.reconnect` for normal PlayerList/Begin/Ready admission.
The lifecycle test stays in its existing server package to inspect saved
OFFLINE/ACTIVE profiles without exposing production-private APIs.

Reuse the complete cargo-only phase from `CARGO-CHUNK-DIAGNOSTIC.md`; do not copy
its snapshots, nonloading checks, ownership adoption or cargo assertions. The
optional witness supplies ordinary disconnect/identity checks, the original
body reference, nonloading diagnostics and final normal reconnect verification.

Required evidence:

- Player removed normally, registry inactive/OFFLINE, saved OFFLINE profile has
  original body UUID/Mind; body alive/on-ground with exact cabin tracking and
  account/profile/Mind binding before lease release.
- Actual old body **and** cargo `UNLOADED_TO_CHUNK`, UUID lookup absence, world
  chunks below FULL and old ship removed/absent throughout the quiet checks.
  Neither mere ticket release nor UUID absence alone establishes this premise.
- After explicit reload, cargo/ship pass the shared conservation checks. The
  old body stays removed, but a new same-UUID body may already be loaded before
  reconnect; absence is required **before**, not after, explicit reload.
- Ordinary reconnect offers a distinct reconstructed body with original body
  UUID, Mind, hands, account/profile binding, held token and complete stack;
  saved ACTIVE profile and live authority agree. The living, physical body is
  naturally grounded on the exact replacement ship/frame. No replacement spawn,
  rebind, profile write, ledger repair or recovery teleport is used by the test.

Existing exact-owner plot/cargo and actor cleanup remains authoritative. Unknown
replacement ownership is not adopted/deleted. Resident/partial/destructive or
reacquisition outcomes remain failures/inconclusive premises as appropriate,
never expected-failure passes. Save/shutdown exceptions invalidate clean-stop
claims even if aggregate tests pass.

Plan `GATE-PLAN-08-OFFLINE-UNLOAD-R25.json`; author compile evidence:
`build/shuttle-feedback-08-author-offline-hooks-r25.log` and
`build/shuttle-feedback-08-author-offline-unload-r25.log`.
