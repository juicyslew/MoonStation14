# Shuttle port — M1 accepted

## STATUS

**Owner accepted M1 on 2026-11-06.** This supersedes older planning-only status and “M1 unaccepted” notes in supporting documents. No merge, commit, push, or M2 implementation has been performed or authorized by this handoff.

Accepted readiness gate: `run_8467a1ef5e6c1e0bee84c8af`, generation 1, PASS, acknowledged, cleanup confirmed. Its code snapshot input digest is `c5218c5ab206b047741723bd50d4be2942353cc35c0c19687e71d0b23498c3df`. This documentation update follows acceptance; do not claim its changed documentation bytes were in that snapshot.

Worktree is `MoonStation14-agent-03`, branch `agent/03`, base HEAD `a5e72a15`. Acceptance-time inventory reported 537 dirty status entries, including untracked files, and no existing unmerged entries. Work is still uncommitted; inventory individual paths before staging or integration.

## ARCHITECTURE

- **Coordinates and authority:** Persistent native Sable sublevels own ship motion. Structures/machines retain storage coordinates; character/cargo physical state and interaction reach remain world-space. Existing lifecycle and hand authorities remain the owners; the spectator carrier is not a passenger. Character input FIFO/ACK/replay and one physical character move per world tick are preserved.
- **Ground/air boundaries:** Native collision/contact witnesses admit support and response. Frozen XYZ airborne launch references inherit point velocity `v + ω × r` once; airborne actors are not continuously pulled after the ship. Gravity remains world-down, damage ordinary, and descending-floor response is finalized after frame rebasing without wiping tangential velocity.
- **Presentation:** One owned simulation interval supplies two immutable audiences in `ClientFrameTimelineStore`: actor readers get only their original primary pairs; scene readers get frozen known-frame pairs for the same interval. Do not leak scene-wide endpoints into camera/body inversion. Accepted authority restores invalidate only obsolete departure/landing yaw bridges, not live heading carry. Preserve existing body render-origin writes.
- **Lighting/cargo:** Packed-light computation skips only the erroneous initial rounded-world-cell frame accounting; the SDK precise accounting then runs once. Cargo presentation, light probe, outline and culling use their existing shared geometry/presentation owners.
- **DROP:** The shared rendered camera ray nominates UP surfaces in world or frame-storage coordinates. Server reprojects at current authority time and checks reach (3.5 blocks), first hit, frame/cell/point identity and full physical clearance before atomic ownership commit. Invalid present nominations retain the hand item. Unavailable ordinary capture fails closed; determinate non-floor rays retain plain DROP. Already-contained clients retain the original containing-owner route without a world ray. Authorized carrier exclusion never exempts unrelated occupants.
- **Diagnostic flight:** The existing fixture service applies native impulses using actual mass/inertia and existing ideal gravity/drag compensation. Freight feedback limits are 0.35 m/s² and 0.02 rad/s²; jerk limits are 0.12 m/s³ and 0.01 rad/s³. Applied-command history is recorded after actuation in every mode and retained across active takeovers/restarts. Stop/expiry/64 m horizontal or 8 m vertical excursion disengage feedback, not momentum or gravity. No obstacles or artificial bump impulses are supplied.

## IMPORTANT FILES

Java paths below are relative to `src/main/java/com/juicyslew/moonstation14/`:

- `ms14/player_body_control/{client/GhostControlClient,client/ClientSupportedHeading,client/ClientFramePredictionState,client/ClientOwnedSimulationClock,character/GroundedHarnessWorldStep,prediction/GhostPredictionHistory}.java`: prediction, input sequencing, support heading and reconciliation.
- `ms14/shuttle/sable/{ClientFrameTimelineStore,OwnedFramePresentationContext,OwnedFrameSimulationScope,SableOwnedBodyMotion,NativeNormalContactFinalizer,FrameAnchor}.java`: temporal frame, contact and reconstruction boundaries.
- `entities/{LooseItemEntity,LooseClientPositionInterpolation}.java`, `ms14/loose_objects/`, and `ms14/hands/{client/BodyHandRaySnapshot,client/BodyHandClient,live/BodyItemTargetProbe,live/BodyHandItemTransfer,network/CapturedHandTarget,network/BodyHandActionRequest,network/BodyHandRequestService}.java`: cargo, shared queries, targeting and ownership.
- `ms14/shuttle/sable/PackedLightAccountingScope.java`; `mixin/client/{SableEntityRendererLightingMixin,SableRemotePlotProjectionPlugin,SableOwnedFramePresentationMixin,SableOwnedSubLevelPoseGetterMixin}.java`: high-risk SDK bridges. Keep lighting helpers outside the reserved mixin package.
- `ms14/shuttle/fixture/{ShuttleFixtureCommands,ShuttleFixtureMotionService,ShuttleMotionMath,ShuttleFreightFlightProfile,ShuttleFreightFlightBounds,ShuttleManualFixture,ShuttleCabinFixture}.java`: diagnostic commands/controller and authored layouts.
- Tests: `ShuttleFreightFlightGameTests`, `ShuttleBayLightGameTests`, `CapturedFrameDropPlacementGameTests`, `DescendingFrameLandingGameTests`; `LifecycleSingleWorldStepObserver` and its JVM test freeze completed-move evidence rather than later mutable SDK scratch.
- Documentation: `Instructions.md` / `EXECUTION.md` for milestone scope, `PARITY.md`, `QUERY-ACCESS.md`, `CARGO-ORIENTATION.md`, `SHARED-FRAME-CORRECTIONS.md`, `MANUAL.md`, `SHIP-FIXTURE.md`; other subsystem handoffs remain indexes for M2 owners.

## SS14 CORRESPONDENCE

Pinned upstream: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; full relevant parent/default/consumer mappings are indexed in `PARITY.md`. Preserve resolved Human/Hands, Jug, airlock and machine consumers rather than hardcoded alternatives.

SS14 DROP forwards captured pointer `EntityCoordinates`; the floor ray, physical-box clearance and denial rules are explicit 3D adaptations. The cargo shuttle is map-authored, not a cargo-flight-profile prototype. SS14 fixed available thrust responds to mass/inertia; this mass-normalized admin flight profile does **not** implement powered thruster hardware or cargo-weight coupling. The sea lantern is explicitly owner-authorized Minecraft lighting, not an SS14 asset/prototype implementation.

## DECISIONS / WHY

- Keep stock Sable 2.0.6 release `fg9dTRz9`, six substeps and near-base roughly 5,000-block operating assumptions. No native fork, copied world geometry, smoothing, camera-offset workaround, forced grounding, or collision-tolerance relaxation.
- Manual bay is 20×7×13 with five clear interior height blocks. Its ceiling sea lantern occupies local `(14,6,6)`; loaded-only `bay_light` retrofit verifies plot ownership before reads, idempotent success or mutation.
- Native diagnostics are optional correction evidence, not universal grounding certificates. Direct fresh native below-contact may have `Branch.NONE`; ordinary idle contact may have `PositionStage.NOT_EVALUATED`. Preserve strict primary contact/frame/completed-step checks and applicable negative correction rejection.

## KNOWN GAPS

The owner accepts a residual cursor-placement offset. Do not silently reopen it during integration. Dedicated Critical/Dead-drift and connected two-client results were not separately established; owner acceptance does not manufacture those measurements. SS14 powered piloting/thrusters, M2 simulation-owner integration, later performance/capacity gates and actual dimension/FTL work remain separate. Public Sable distribution/license clearance remains unresolved; see `licensing-review.md`.

## VALIDATION

Final gate passed 716 JVM tests across 100 selected classes, **123/1006 filtered native cases**, build/JAR and provenance checks. One named native case executes a **30-second**, restarted HUMAN/JUG freight profile (600 game ticks, 3,600 native callbacks), not a 30-case matrix. It passed takeover/restart, vertical COM reversal, grounded HUMAN, cargo integrity/floor and stop checks. Bay retrofit/light propagation also passed. A compiled-tag preflight is only discovery, never runtime execution.

A missing compiled bay-test class despite Gradle `UP-TO-DATE` was repaired by MAIN via forced GameTest compilation; the exact initial cause remains unknown. MAIN also repaired its misleading validator error classification. Report recurrence; inspect actual bytecode catalogue before scheduling native work. Historical failures/results remain immutable.

## NEXT WORK

Integrate only after merge target/authority is explicit. Preserve all required untracked sources/resources; do not blanket choose ours/theirs, stage everything indiscriminately, or treat old subsystem handoffs as current source truth. Highest-risk overlaps are body input/prediction, native-move bridges, cargo/query/hand networking, mixin registration, Gradle/test configuration and fixture controller logic. Run the required post-acceptance and post-merge regression through the serialized integration owner; no full-suite post-acceptance result is claimed here. M2 starts only when assigned.
