# Operational frame residency

Pinned upstream mapping (SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
verified by the existing parity audit): `MapManager.GridCollection.CreateGridEntity`
builds a runtime grid with `Transform` and `MapGrid` (16-tile chunks, tile size 1,
splitting enabled). `ShuttleSystem.GridInitializeEvent` enables shuttle behavior
and adds implicit roof where appropriate; it enables an existing physics body as
dynamic, in-air, and non-fixed-rotation. Physics component defaults are static,
not-awake, sleep-allowed. PVS visibility does not pause grid simulation. These
are runtime grid properties, not a canonical grid YAML prototype parent chain.
The pinned-checkout audit verifies these references: `RobustToolbox/Robust.Shared/Map/MapManager.GridCollection.cs`
(`GridCollection.CreateGridEntity`), `Content.Server/Shuttles/Systems/ShuttleSystem.cs`
(`OnGridInit`), and `RobustToolbox/Robust.Shared/Physics/PhysicsComponent.cs`
(defaults). These are runtime grid properties, not a canonical grid YAML parent chain.

Local mapping remains Sable's registered `ServerSubLevel` (plot/container,
logical pose and Sable physics body), not a fabricated SS14 prototype or a second
chunk-parent implementation. Every ordinary registered server sublevel is
operational by default. `ShuttleSimulationResidency` installs a distinct stable
`moonstation14:operational_frame` force-load ticket per exact frame owner. Sable's
force-loaded list is its existing simulation admission path; it uses predicted
world bounds plus margin and entity-ticking chunk status. Tickets persist in
Sable's ticket saved data. This is the deliberate Minecraft adaptation needed
because Sable otherwise unloads remote plots; it is not upstream grid parity.

The ticket codec is registered during mod construction before world/sublevel
ticket deserialization. Level load installs the existing capture observer first,
then the residency observer, and scans already-registered frames after observer
registration. Player logout never releases a frame. Explicit `release(owner)` is
the retirement/test seam; it is identity-based and a released owner is not
re-admitted on later callbacks. Server level stop only closes in-memory observation
and leaves persisted ticket metadata intact for save/restart. No per-grid cost or
population capacity claim is made; force-loading has real ticking/chunk costs and
 must be measured for actual craft counts.

## Operational-residency evidence (2026-10-21)

Focused service/lease phase-1 evidence: 21 checks passed. The local native evidence
`build/gametest-operational-residency-sdk6-local-20261021-e5/native.log` is one
selected GameTest: 1 completed, 1 passed, 0 failed; effective SDK substeps were
6. The passing case confirms production force-load remains after fixture lease
close, launch 50, detach 52, offline 53–131 (78 observed ticks), reconnect at
134, and post-reconnect checks through 194 (60 observed ticks); body damage and
cargo identity/full payload were preserved. Source proof/hash manifest is
alongside the native evidence. Earlier a1/d4 attempts were separate harness
failures and must not be aggregated with e5 or represented as passed evidence.
These results do not verify broad M1:
stutter/movement is pending owner logs and hull integrity remains separately
unverified. No owner acceptance or feature readiness is implied.

## M1 continuity diagnostics (2026-10-23)

Two isolated fresh checks ran at effective SDK substeps 6. The resident-landing
reconnect fixture preserved the same body/Mind, hands-held tool, and three jug
water identities/ledger across landing and reconnect. Measured detach was tick
52, offline at 53, reconnect at 134, and post-reconnect checks through 194:
about 78 offline ticks plus 60 post-reconnect ticks. The 400-tick wait was only
a deadline, not observed offline duration. Closing the scene lease left the
production shuttle simulation-residency ticket normally operational; it did
not force-unload the frame. The native-contact fixture confirmed contact
consumption follows a genuine later physical tick, and both paths invoked the
normal `GroundedHarnessWorldStep` callbacks with the same binding and actual
six-substep configuration.

The separate 40 m/s hull observation remains limited to transient overlap
recovery described in `impact-substeps-default.md`. These diagnostics add
identity/contact evidence only; connected camera, movement, ray/action and hatch
crossing remain unverified, and no M1 acceptance is implied.

## Focused author validation (2026-10-21)

`compileGameTestJava` and focused JVM selection of these 14 classes succeeded:
`FrameAnchorStateTest` (23), `FrameAnchorCaptureServiceTest` (10),
`FrameAnchorLifecycleTraceTest` (7), `SableOwnedBodyMotionCompositionWiringTest` (2),
`CargoPersistenceFixturePhaseTest` (5), `LoosePersistenceWatchTest` (7),
`OfflineFlightPayloadTest` (5), `NativeFrameIntervalTest` (1),
`LoadedBodyResolverTest` (11), `NativeCollisionGeometryTest` (9),
`FrameAnchorMovementEntryTest` (6), `ShuttleSimulationResidencyTest` (4),
`ShuttleFixtureSubstepsStatusWiringTest` (1), and
`LifecycleShuttleCargoReconnectGameTestsTest` (1). Total: 92 tests, 0 failures,
errors, or skips. Captured XML reports and command log are in
`build/operational-residency-readiness-20261021/worker-14-043100/`. No native/full
build or full suite was run in this validation.

SS14 source references (relative to the pinned checkout):
`RobustToolbox/Robust.Shared/Map/MapManager.GridCollection.cs`
(`GridCollection.CreateGridEntity`), `Content.Server/Shuttles/Systems/ShuttleSystem.cs`
(`OnGridInit`), and `RobustToolbox/Robust.Shared/Physics/PhysicsComponent.cs` (defaults).
