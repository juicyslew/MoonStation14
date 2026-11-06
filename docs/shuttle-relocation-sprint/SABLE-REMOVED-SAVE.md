# Removed-owner serialization — blocked full-suite gate

Central candidate `shuttle-feedback05-side-20261013-r2` passed **3,233/3,233 full
JUnit** and **33/868 filtered native integration, all 33 passed**. The unfiltered
run discovered 868 fixtures but crashed during autosave in `defaultBatch:4`
before aggregate completion. Final completed/pass/failure counts are unavailable;
Gradle exited 1. The central lead persisted `needs_changes`; this is not M1 or
full-suite acceptance.

The passing 33-case integration log also records **Exception stopping the server:
Body has been removed** after its completed aggregate, despite Gradle exit 0.
Thus 33/33 is gameplay-test evidence, not a clean save/shutdown result. Retain that
disposable world alongside the full-run failure artifacts. The narrower combined
run can provide the owner-identity diagnostic even if all its assertions pass.

Evidence: `build/shuttle-feedback-05-gate-r2-full.log` and
`build/gametest-shuttle-feedback-05-gate-r2-full/crash-reports/crash-2026-10-13_03.13.03-server.txt`.
Crash report UUID `d7c3a044-ebda-426a-b3dc-d89edd52e41f` identifies the report,
**not the failed sublevel**. Last logged character/carrier UUIDs are not proof of
the serialized owner.

## Verified native ordering

Pinned official Sable revision `ad8b4d377741fbd923f4d863b2cf9e462daff9a6`:

- `SubLevelHoldingChunkMap.saveAll` processes holding changes, snapshots live
  container seeds, traverses each loading dependency chain and serializes its
  nodes through `moveAndSaveSubLevel -> SubLevelSerializer.toData`.
- `SubLevelHelper.getLoadingDependencyChain` follows intersecting frames and
  block-entity actor dependencies without rejecting removed nodes. The save loop
  does not directly serialize the force-loaded ticket collection.
- `SubLevelSerializer.serialize` obtains a new `RigidBodyHandle` and reads
  velocity. `RapierPhysicsPipeline.assertBodyValid` throws the observed message
  when **the Java body object's `isRemoved()` is true**, before invoking JNI.
  This is not independent proof of native body-map invalidity. Handle `isValid()`
  checks that same flag; no cached-handle invalidation is involved.
- Invalid mass can mark a frame removed before normal container processing
  retires it. `processSubLevelRemovals` subsequently removes marked frames through
  observer callbacks, plot-slot/list/UUID cleanup and ordinary holding deletion.

Source roots: `common/src/main/java/dev/ryanhcode/sable/` (`api/SubLevelHelper.java`,
`api/sublevel/SubLevelContainer.java`, `sublevel/system/SubLevelPhysicsSystem.java`,
`sublevel/storage/holding/SubLevelHoldingChunkMap.java`,
`sublevel/storage/serialization/SubLevelSerializer.java`), and
`sable_rapier/src/main/java/dev/ryanhcode/sable/physics/impl/rapier/RapierPhysicsPipeline.java`.

## Local boundary and next proof

`SableFixturePlot.close` verifies exact UUID ownership before clearing its authored
blocks and uses normal container removal. It creates no Sable force-load ticket.
It can defer retirement if its owner is already marked removed, including after
block cleanup. That is a candidate lifecycle gap, not an established cause of
this crash. A stale-ticket release fix is not justified by current evidence.

The implemented diagnostic wraps serialization, preserves the exact original operation
and exception, and reports failed UUID, entry/failure removal flag, exact current
container ownership, plot owner and bounded dependency lookup metadata. It must
not skip saves, synthesize zero velocity, remove owners, recover physics, alter
SavedData or globally suppress exceptions. No lifecycle correction is accepted
until the actual failed owner and its retirement/dependency route are identified.

Paths: `mixin/compat/SableSerializationFailureDiagnosticMixin.java` and
`ms14/shuttle/sable/NativeSaveFailureDiagnostic.java`. Entry metadata reads are
guarded; full dependency context is built only on failure and limited to 16
lookups. The wrapper emits `[native sublevel serialization failure]`; fixture
DEBUG records `[native fixture owner allocated]`, `[native fixture owner close]`
and `[native fixture owner close result]` correlate the same UUID without a registry
or cleanup changes. Four focused tests and source/native-fixture compilation
passed (`build/shuttle-feedback-05-save-52-focused.log`); independent source review
passed. JVM tests exercise the actual guarded call seam and structural wrapper
binding, not runtime Mixin transformation. R3 exercised the Mixin and captured the
actual failure described below.

## R3 cause identified

Central r3 passed **3,237/3,237 full JUnit**, **33/868 filtered native integration,
all 33 passed**, and the raw unfiltered log reports **868/868 required passed**.
The full run exited 0 with no observed removed-owner save exception. Its workflow
selection remained unverified because the full registrar emitted a different
catalogue header, not the standard preflight consumed by the evidence reader.
This is a reporting boundary, not proof that the full aggregate was missing.

The combined run still failed saving after its aggregate. Its diagnostic identifies
`de267800-e49a-47f4-be11-e215eb5d1884`: allocated at line 1448; close entry at 1455
was `closed=false,isRemoved=false,registered=true`; close result at 1456 was
`isRemoved=true,registered=true`. Serialization at 1469 reports the **same UUID**,
removed and still the exact registered owner. The only reported dependency is that
same frame. Cleanup marked it removed, but `close` skipped normal retirement.
The server stopped before the next SDK removal-processing tick.

This establishes the local fixture retirement cause for that failure; it is not a
stale ticket or independent native handle-cache issue. The source correction uses
exact UUID ownership and normal container removal even after the removal flag is
set. It does not process unrelated owners or alter serialization. Existing
lifecycle assertions now require immediate UUID/list absence before another tick,
while retaining finite gas/heat archival, foreign-owner and late-close protections.
The central lead persisted `needs_changes`; clean combined shutdown is still
required. The earlier failed full-r2 owner remains unidentified individually.

The source correction and real-count catalogue-header change passed independent
review and native-fixture compilation. Fresh central r4 validation is pending;
do not treat source review as clean shutdown or lifecycle runtime proof.

R4 passed fresh **3,237/3,237 JUnit**, but combined native was **32/33 selected
of 868**. Save/shutdown was clean; the full native command was not run after the
combined assertion failure. The failing single-pipe lifecycle fixture incorrectly
expected the old physical key empty after a new incarnation reused that same cell.
The correction is identity-based: old UUID absent from the live key, replacement
UUID present when reused, unchanged old archive, and late-close safety. The
production synchronous retirement is not reverted or delayed. Replacement failure
cleanup reuses the existing plot tracker; no new lifecycle service is introduced.

The existing removed-heat-map tick guard addresses a different lifecycle seam;
see `SABLE-REMOVED-HEATMAP.md`. Gas archives and unrelated owners must remain
untouched by any eventual fixture cleanup correction. Failed artifacts are retained.
