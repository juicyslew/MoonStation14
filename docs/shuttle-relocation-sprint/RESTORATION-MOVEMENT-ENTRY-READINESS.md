# Bounded milestone: moving-body reconstruction continuity

This is a correction to ordinary body/cargo reconstruction during flight, not
completion of M1 or a diagnostic-only checkpoint. Implementation is evaluated
against the following criteria; native success is established only by the new
coordinator's result, never by this document or an admission receipt.

| Criterion | Implementation/evidence to inspect |
|---|---|
| One-time native entry | `FrameAnchor`, `FrameAnchorState`, harness tick barrier, existing grounded/corpse consumers and pending-corpse dispatch. Exact live frame, loaded storage and physical residency; no initialization on rejected admission. Focused state/projection/compiled-consumer tests. |
| Body/cargo continuity | Three focused reconstruction cases: stationary cargo, stationary offline body/cargo, and flying offline body/cargo. Actual ordinary unload/quiet/reload, UUID/Mind/hands/held-item and complete jug payload checks, restored frame ownership and grounded ACTIVE reconnect. Both live-point observations are post-physics; `.12` remains unchanged. |
| Existing boundaries preserved | Cargo's current-pose initialization, strict current-pose gameplay queries, LIVE/UNLOADED serialization proof, ordinary movement and one-attempt budget remain authoritative. No solver change, recovery placement, velocity/gravity change or collision-tolerance relaxation. Existing state/capture/native-composition/geometry/physical-owner tests are regressions. |
| Owner-testable bounded fix | Existing development setup and the concise owner check in `RESTORATION-MOVEMENT-ENTRY-MANUAL.md`. Coordinator package/provenance checks use the managed configuration and a normal build, not installation or client automation. |

## Architecture and parity limits

`RESTORATION-MOVEMENT-ENTRY.md` gives the pinned Sable timing contract and local
consumer mapping. Reuse the shared anchor codec/barrier and native movement
owners; do not introduce a second carrier or save authority. The existing
typed character movement policy and resolved prototypes remain unchanged.
`BODY-CARRY.md` records the prior SS14 human composition audit at
`c9df5ef5d675b0d1d226828bddf6b78c28502d91`. This is a Minecraft/Sable initialization
correction, not a claim that SS14 uses the same tick ordering. No new assets,
prototype fields/components, keybinds, packets or prediction policy are added.
Existing Sable licensing/public-distribution limits are unchanged.

## Validation boundaries

Author focused JVM checks and tag preflight are not native execution. The new
coordinator must report the actual selected/total and passed/failed/unfinished
cases; expected selection is three cases, historically three of 895. The flight
fixture itself requires effective SDK two substeps. Its existing launch, saved
flight, real unload, quiet-state and full-payload premises remain mandatory.
The fixture's physical-step marker attests attempt admission, not native success.

### Observer-ordering correction (2026-10-20)

The prior coordinator result `run_6c12fb8c0bd92efe1f3ff73e` was a settled FAIL.
Its three-tag native selection completed 3/895 with 2 passed and 1 failed; it is
preserved as historical evidence and is not represented as an all-pass run. The
corrective wiring installs the existing idempotent `FrameAnchorCaptureService`
from the server `LevelEvent.Load` handler, paired with its existing unload close.
The `WORLD`/`UNRESOLVED` guards remain unchanged. The installed Sable constructor
container precedes the NeoForge level-load event in verified bytecode ordering.

After that correction, focused JVM validation covers 11 classes / 86 testcases,
including `compileGameTestJava`, with 86 passed and no failures, errors or skips.
A separate limited local cargo-only retest selected 1/895, completed 1/1 and
passed with effective SDK two substeps. It observed all three cargo anchors
`INITIALIZED` against the exact frame after ordinary unload, confirmed the old
entities were `UNLOADED_TO_CHUNK`, and preserved current identity/payload/owner.
This is local diagnostic evidence, not the coordinator's three-tag native gate;
no combined three-case native pass is claimed. The failed local diagnostic and
prior 85-test reports remain preserved. Package and managed-provenance checks
remain pending coordinator execution. This update does not establish M1
acceptance or expand the bounded proof.

The subsequent coordinator run `run_10a5929b25778845ef302141` was closed with
`CANCELLED`, `result=null`, and cleanup confirmed while native recovery was
blocked. This is a workflow cancellation, not a technical PASS/FAIL/ERROR and
does not replace or erase the prior settled FAIL. No native verdict is inferred
from the cancelled run. The owner has directed a fresh submission of the same
completed correction; it is an ordinary new feature submission, not a replacement
of the cancelled no-verdict run. The cancelled run's immutable history remains
preserved.

R47 remains a genuine flight failure. R48 remains interrupted without a technical
verdict; its frozen plan/identity are not reused. Historical artifacts remain in
place. The owner retired legacy in-flight jobs/holds at the 2026-10-20 cutover.

Connected client behavior, process restart, high-speed penetration, multi-ship
capacity, structural crash damage, and the remaining M1 combinations are outside
this bounded milestone's proof. M1 still requires broader readiness and explicit
owner acceptance; M2–M6 remain paused.
