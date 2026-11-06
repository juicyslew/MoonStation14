# Intended cargo-orientation R11 current-source attribution — NOT A READINESS RECEIPT

Intended candidate: `shuttle-feedback08-cargo-orientation-20261015-r11`. Current HEAD `5c09e426d055d18932e1896b865912d2716a8278`; immutable base `620face76b8c1374019ce074380a451d698f05e1`. This packet is not a workflow candidate or pass.

Current dirty paths from shared `git_changes(repo, base)` helper: 337 total, 337 source-manifest rows after exactly three output exclusions. Digest: `c9f251f41779690794b10c8ab87f87f11127468496a5532fb1616b485dc90f8e`. Committed base-to-HEAD rows: 615 (digest `9f70a18f2ee922ed75f014bd7e19c39299e5b009f1793bc77b505556888ce075`); HEAD remains the recorded historical HEAD.

Explicit category counts: `{"R10-GameTestServer-mixin-launch-only-config": 1, "R11-cargo-terminal-observation-fix": 1, "R7-test-progress-tooling": 6, "author-evidence-and-plan": 26, "feedback08-cargo-orientation-setup": 20, "feedback08-shared-fixture-extensions": 3, "inherited-R10-extracted-source-or-jar-artifact": 11, "inherited-R10-workspace-or-prior-changes": 249, "preserved-prior-diagnostic-bytecode-artifact": 2, "prior-feedback08-documentation": 2, "prior-feedback08-hull-cost-reconnect-diagnostics": 16}`. Live mismatches: 0. Unclassified changed source paths: 0. Scoped patch `docs/shuttle-relocation-sprint/scope-r11/changed-source-vs-head.patch`: 47 paths, 355999 bytes. Retained code matches cargo R10 by status+hash: 46/46; mismatches: 0. Removed paths since cargo R10: 0. Full source hashes/statuses and nested historical proof are in `snapshot.json`.

R11 source change is limited to `LifecycleShuttleCargoReconnectGameTests.java`: a `CargoJournal` terminal-scheduled latch defers failure through `safeDelay`/existing `succeedAfterObservation`; existing observations, assertions, and 160-offline/60-post observations are preserved. Other current code/build/mixin/probe paths match cargo R10 by status+hash. Matching proves source identity, not correctness.

R10 historical result: full JVM 3314 passed; focused native 6/889 passed; full 889 native timed out after 749 admitted/finished, two hull failures, and 140 unadmitted. Last cargo result was DONE_FALLBACK_PASS rather than ordinary native PASS; cause remains unproven until a batch advances. These results are not R11 readiness. The bootstrap had already been removed by R10; no new R10-to-R11 removed prior source is reported.

The generated SUMMARY, snapshot JSON and patch are the only three exact self-reference exclusions. Generator, R11 plan, older plans, prior packets and other current dirty files remain in the manifest for normal submission binding. The ignored R1 cargo terminal-phase author log is not source/gate evidence in the shared non-ignored dirty-path manifest. Do not edit outputs after R11 submission.

R9 lead clarification (session `ses_ec02df7efffehdPu2u2qwUSX6x`, message `msg_13fd5fb63001foydukTDeOVbPn`): exact wall-R10 dirty preimage line delta is not required; current full manifest and HEAD patch are adequate attribution. This is not a new approval. R10 passes do not carry forward to R11.

R11 remains uncompiled and unrun by design. The lead runs the unchanged R11 plan: full JVM, focused 7/889 (including reconnect ordinary-listener PASS, not DONE_FALLBACK), then full 889 with admission/aggregate checked; genuine 40/80 hull failures remain failures. The full-run root cause remains a hypothesis pending observed batch progress. No logs are fabricated here.

## Current scoped source list

| Category | Current change | Cargo R10 status+hash | Path |
|---|---|---|---|
| R10-GameTestServer-mixin-launch-only-config | current dirty | same | `build.gradle` |
| R7-test-progress-tooling | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressObserver.java` |
| R7-test-progress-tooling | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressState.java` |
| R7-test-progress-tooling | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/mixin/GameTestTickerProgressMixin.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/examine/CargoExamineGeometryGameTests.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/CargoPickupGeometryGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistory.java` |
| R11-cargo-terminal-observation-fix | current dirty | differs | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleShuttleCargoReconnectGameTests.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/command/ShuttleCargoSetupGameTests.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoActorDropOrientationGameTests.java` |
| R7-test-progress-tooling | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/GameTestProgressGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectation.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceState.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometry.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactProbe.java` |
| feedback08-shared-fixture-extensions | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingCabinAuthorityFixture.java` |
| feedback08-shared-fixture-extensions | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureScene.java` |
| feedback08-shared-fixture-extensions | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorder.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleServerCostGameTests.java` |
| R7-test-progress-tooling | current dirty | same | `src/gametest/resources/moonstation14.gametest.mixins.json` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/entities/LooseFrameOrientation.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/eventhooks/ModEventHooks.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineTargetResolver.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineWorldSelector.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/examine/client/ExamineClient.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/hands/client/SelectedLooseOutlineRenderer.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyHandItemTransfer.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyItemTargetProbe.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyLooseThrow.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/NativeHeldItemTransfer.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometry.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureCommands.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/test/java/com/juicyslew/moonstation14/entities/LooseFrameOrientationTest.java` |
| R7-test-progress-tooling | current dirty | same | `src/test/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressStateTest.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryExtentTest.java` |
| feedback08-cargo-orientation-setup | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometryTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistoryTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectationTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceStateTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullStateMismatchEvidenceTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometryTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorderTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `tooling/shuttle/prepare_impact_substeps_comparison.py` |
| prior-feedback08-hull-cost-reconnect-diagnostics | current dirty | same | `tooling/shuttle/test_prepare_impact_substeps_comparison.py` |

## Removed prior cargo R10 paths

| Path | R10 prior status/hash | Current manifest | Filesystem | HEAD preimage |
|---|---|---|---|---|

## Complete current dirty-path index

| Category | Git status | Wall R10 | R6 | R7 | R8 | R9 | Cargo R10 | Live match | Path |
|---|---|---|---|---|---|---|---|---:|---|
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `.gitignore` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `.opencode/agents/fidelity-reviewer.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `.opencode/agents/integration-lead.md` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `AGENTS.md` |
| inherited-R10-extracted-source-or-jar-artifact | `??` | same | same | same | same | same | same | yes | `META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.6.jar` |
| R10-GameTestServer-mixin-launch-only-config | ` M` | other | other | other | other | other | same | yes | `build.gradle` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `capturebuild/shuttle-feedback-r14-author-chemfixture.log` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `docs/airlocks-sprint/BUMP.md` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `docs/airlocks-sprint/PARITY.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/development-workflow/README.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-grid-feasibility-sprint/SPRINT.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/BASELINE.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/BODY-CARRY.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/BUMP-FEEDBACK.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/CABIN-VENTILATION.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/CARGO-ORIENTATION.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/CRITICAL-CONTACT-VALIDATION-06.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/ENGINE-PROOF.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/EXAMINATION.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/EXECUTION.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/FRAMES-AND-OWNERS.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-06.json` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-07-FIX.json` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-07-REPRO.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R1.json` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | absent | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R10.json` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | absent | absent | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R11.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R5.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R6.json` |
| author-evidence-and-plan | `??` | absent | absent | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R7.json` |
| author-evidence-and-plan | `??` | absent | absent | absent | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R8.json` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R9.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-RECONNECT-R1.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-COST-COMPARISON-R2.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-COST-COMPARISON.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-IMPACT-REPRO.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-IMPACT-SUBSTEPS-COMPARISON.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-SUBSTEPS10-REGRESSION-R2.json` |
| author-evidence-and-plan | `??` | absent | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-SUBSTEPS10-REGRESSION.json` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/GATE-PLAN.json` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/HANDOFF.md` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/Instructions.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/MANUAL.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/NATIVE-PRECISION.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/OUTLINE-FEEDBACK.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK-06.md` |
| prior-feedback08-documentation | `??` | other | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK-07.md` |
| author-evidence-and-plan | `??` | absent | other | same | same | same | same | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK-08.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/PARITY.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/POWER-SIGNAL-PHASE-06.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/QUERY-ACCESS.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/RECONNECT-LANDING-06.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/RECONNECT.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/SABLE-BLOCK-UPDATES.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/SABLE-INVENTORY.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/SABLE-REMOVED-HEATMAP.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/SABLE-REMOVED-SAVE.md` |
| prior-feedback08-documentation | `??` | other | other | same | same | same | same | yes | `docs/shuttle-relocation-sprint/SHIP-FIXTURE.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/WALL-RESPONSE-06.md` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/WALL-RESPONSE.md` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `docs/shuttle-relocation-sprint/licensing-review.md` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | absent | same | yes | `docs/shuttle-relocation-sprint/scope-r10/SUMMARY.md` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | absent | same | yes | `docs/shuttle-relocation-sprint/scope-r10/changed-source-vs-head.patch` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | absent | same | yes | `docs/shuttle-relocation-sprint/scope-r10/snapshot.json` |
| author-evidence-and-plan | `??` | absent | absent | absent | same | same | same | yes | `docs/shuttle-relocation-sprint/scope-r8/SUMMARY.md` |
| author-evidence-and-plan | `??` | absent | absent | absent | same | same | same | yes | `docs/shuttle-relocation-sprint/scope-r8/changed-source-vs-head.patch` |
| author-evidence-and-plan | `??` | absent | absent | absent | same | same | same | yes | `docs/shuttle-relocation-sprint/scope-r8/snapshot.json` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | same | same | yes | `docs/shuttle-relocation-sprint/scope-r9/SUMMARY.md` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | same | same | yes | `docs/shuttle-relocation-sprint/scope-r9/changed-source-vs-head.patch` |
| author-evidence-and-plan | `??` | absent | absent | absent | absent | same | same | yes | `docs/shuttle-relocation-sprint/scope-r9/snapshot.json` |
| author-evidence-and-plan | `??` | absent | absent | absent | other | other | other | yes | `docs/shuttle-relocation-sprint/tools/scope_snapshot.py` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `gradle.properties` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/renderer/block/model/BakedQuad.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/renderer/block/model/BlockElement.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/renderer/block/model/BlockElementFace.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/renderer/block/model/BlockModel.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/renderer/block/model/FaceBakery.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/renderer/texture/SpriteContents.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/renderer/texture/TextureAtlasSprite.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/client/resources/model/BlockModelRotation.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/minecraft/server/packs/resources/ResourceMetadata.java` |
| inherited-R10-extracted-source-or-jar-artifact | ` D` | same | same | same | same | same | same | yes | `net/neoforged/neoforge/client/model/ExtraFaceData.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `opencode.jsonc` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `shuttle-feedback-05-save-51-focused.log` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/HumanHarnessMovementGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/HumanHarnessTravelOwnershipGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/LooseObjectEffectsGameTestEntry.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/LooseObjectEffectsGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/atmos/AirAlarmCandidatesGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/power/runtime/ApcOverloadGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/power/runtime/PowerAppearanceGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/power/ui/ApcMenuGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/BoundHumanFixture.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/BoundedGameTestWait.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/FixtureChunkTickets.java` |
| R7-test-progress-tooling | `??` | absent | absent | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressObserver.java` |
| R7-test-progress-tooling | `??` | absent | absent | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressState.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestTagRegistrar.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/PowerFixtureReadiness.java` |
| R7-test-progress-tooling | `??` | absent | absent | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/mixin/GameTestTickerProgressMixin.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/atmos/piping/alarm/ui/AirAlarmBodyReachGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/chemistry/chemmaster/interaction/ChemMasterWorldDumpGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceAlarmWorkflowGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceButtonAirlockWorkflowGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/disposal/transport/DisposalSpatialTransportGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/doors/AirlockConnectedGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/doors/AirlockCoreGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/doors/AirlockToolAccessGameTests.java` |
| feedback08-cargo-orientation-setup | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/examine/CargoExamineGeometryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/ServerCharacterAuthorityFixture.java` |
| feedback08-cargo-orientation-setup | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/CargoPickupGeometryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/FixtureHeldTool.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/HeldTimedIngestionGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/network/LifecycleHandActorFixture.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/mobs/OwnedCorpsePhysicsGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/character/MovementEligibilityGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistory.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleAbandonedActiveReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleActiveShuttleInputGameTests.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCreativeReconnectPolicyGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleFallingShuttleReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleMovementRecoveryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleMovingShuttleReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecyclePairedWorldStepObserver.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecyclePassiveShuttleBodyGameTests.java` |
| R11-cargo-terminal-observation-fix | `??` | absent | other | other | other | other | other | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleShuttleCargoReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/CabinGasInventorySnapshot.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleCabinFixtureGameTests.java` |
| feedback08-cargo-orientation-setup | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/command/ShuttleCargoSetupGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/command/ShuttleFixtureCommandGameTests.java` |
| feedback08-cargo-orientation-setup | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoActorDropOrientationGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoOrientationGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ExteriorWallSustainedJumpGameTests.java` |
| R7-test-progress-tooling | `??` | absent | absent | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/GameTestProgressGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectation.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceState.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometry.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactProbe.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/LoadedFrameRayQueryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAirlockPanelGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAirlockPryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAirlockWireGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAlarmMenuGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingApcMenuGameTests.java` |
| feedback08-shared-fixture-extensions | `??` | other | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingCabinAuthorityFixture.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingFrameRayDiagnostic.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeAirlockContactGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureLifecycleGameTests.java` |
| feedback08-shared-fixture-extensions | `??` | other | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureScene.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOperatingEnvelopeGameTests.java` |
| feedback08-shared-fixture-extensions | `??` | other | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SablePlotBlockEntityGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SablePrecisionPairGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableRemovedHeatMapTickGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorder.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleCabinVentilationGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleServerCostGameTests.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/compat/SableNestedBlockChangeGameTests.java` |
| R7-test-progress-tooling | `??` | absent | absent | same | same | same | same | yes | `src/gametest/resources/moonstation14.gametest.mixins.json` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/MoonStation14.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/MoonStation14Client.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/entities/LooseClientPositionInterpolation.java` |
| feedback08-cargo-orientation-setup | `??` | other | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/entities/LooseFrameOrientation.java` |
| feedback08-cargo-orientation-setup | ` M` | other | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java` |
| feedback08-cargo-orientation-setup | ` M` | absent | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/eventhooks/ModEventHooks.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/EntityAirlockBumpMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/LivingEntityAirlockBumpTravelMixin.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/LivingEntityMindHarnessTravelMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/LooseNativeMoveObserverMixin.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/MobMindHarnessOwnerMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/LooseCargoPacketFrameMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/SableOwnedRemoteFrameEntityMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/SableOwnedRemoteFrameLivingMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/SableRemotePlotProjectionPlugin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/LooseNativePreCutoffMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableCollisionInfoAirlockContactsMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableLevelChunkBlockChangeContextMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableNativeAirlockContactCaptureMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableOwnedCollisionPoseMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableOwnedNativeCollisionTraceMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableRemovedHeatMapTickMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableSerializationFailureDiagnosticMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableTrackedCollisionSubstepsMixin.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/VanillaWorldCollisionObservationMixin.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/LoadedPipeGasOwner.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/alarm/ui/AirAlarmMenu.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/alarm/ui/AirAlarmMenuService.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/fixture/FixturePipeAdmission.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/fixture/VentilationFixtureService.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceEndpoint.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceNetworkLifecycle.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceNetworkTransport.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockBlockEntity.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockBumpMoveAccess.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockBumpSystem.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockDoorwayActors.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockPryService.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockToolInteraction.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockToolLineOfSight.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/NativeAirlockContact.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/NativeAirlockContactAccess.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/ui/AirlockWireMenuService.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineTarget.java` |
| feedback08-cargo-orientation-setup | ` M` | other | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineTargetResolver.java` |
| feedback08-cargo-orientation-setup | ` M` | other | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineWorldSelector.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/FrameBoundsTransform.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/FrameExamineGeometry.java` |
| feedback08-cargo-orientation-setup | ` M` | other | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/client/ExamineClient.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/client/ExamineSelector.java` |
| feedback08-cargo-orientation-setup | ` M` | absent | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/client/SelectedLooseOutlineRenderer.java` |
| feedback08-cargo-orientation-setup | ` M` | absent | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyHandItemTransfer.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyInteractionAccess.java` |
| feedback08-cargo-orientation-setup | ` M` | other | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyItemTargetProbe.java` |
| feedback08-cargo-orientation-setup | ` M` | absent | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyLooseThrow.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/LoadedBlockGetter.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/LoadedFrameRayQuery.java` |
| feedback08-cargo-orientation-setup | ` M` | absent | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/NativeHeldItemTransfer.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyBlockUseTargetProbe.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandActionNetworking.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandRequestService.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandStateService.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyWorldItemActions.java` |
| feedback08-cargo-orientation-setup | `??` | absent | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometry.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseItemRenderer.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/CharacterMovementMotor.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/ContactPositionCorrectionEvidence.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/HorizontalContactEvidence.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/MovementCollisionResolver.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/NativeCollisionSlop.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/BodyControlRegistry.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessWorldStep.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/MindControlledMob.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/OwnedCorpsePhysics.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/OwnedLivingPhysics.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/CharacterMovementRebasePolicy.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/CharacterPredictionFailurePolicy.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/ClientFramePredictionState.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/PlayerLifecycleRegistry.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/persistence/LoadedBodyResolver.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/persistence/MinecraftLoadedBodyAdapter.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControl.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleDevelopmentMode.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleExistingBodyReconnect.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleFirstJoinHandler.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleServerContext.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleStartupRuntime.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterMovementHoldPolicy.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterMovementSafePose.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterWorldStepRecoveryPolicy.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlNetworking.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlPayloads.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/prediction/GhostPredictionHistory.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/server/GhostMobHarnessControl.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/graph/PowerGraphService.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/runtime/PowerRuntime.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/ui/ApcMenu.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/ui/ApcMenuService.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleCabinFixture.java` |
| feedback08-cargo-orientation-setup | `??` | other | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureCommands.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureShipIds.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CurrentFrameCollisionScope.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeCollisionGeometry.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeFrameInterval.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSaveFailureDiagnostic.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideCollisionWitness.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideContactProof.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/OwnedNativeMoveDiagnostic.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixturePlot.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFrameVelocity.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyMotion.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedStepProbe.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/TrackedCollisionSubsteps.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/java/com/juicyslew/moonstation14/ms14/timed_interaction/TimedInteractionSystem.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/resources/moonstation14.mixins.json` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/main/templates/META-INF/neoforge.mods.toml` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/entities/LooseClientPositionInterpolationTest.java` |
| feedback08-cargo-orientation-setup | `??` | other | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/entities/LooseFrameOrientationTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/entities/NativePreCutoffTest.java` |
| R7-test-progress-tooling | `??` | absent | absent | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressStateTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/gametest/support/PowerFixtureReadinessTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/LooseCargoPostMergeMixinTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/NativeAirlockContactCaptureStructureTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/SableOwnedRemoteFrameMixinTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/SableOwnedStepProbeMixinStructureTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/SableSerializationFailureDiagnosticTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/VanillaWorldCollisionObservationMixinTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/doors/AirlockDoorwayActorsGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/examine/ExamineCellTraversalTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/examine/ExamineResponseContractTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/examine/FrameExamineGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClientPolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandStatePolicyTest.java` |
| feedback08-cargo-orientation-setup | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryExtentTest.java` |
| feedback08-cargo-orientation-setup | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/movement/CharacterMovementMotorTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/movement/HorizontalContactEvidenceTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/movement/NativeCollisionSlopTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/client/CharacterMovementRebasePolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/client/ClientFramePredictionStateTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/PlayerLifecycleRegistryTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/persistence/LoadedBodyResolverTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistoryTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControlTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleExistingBodyReconnectTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleFirstJoinHandlerTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleStartupRuntimeTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterMovementHoldPolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterWorldStepRecoveryPolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlPayloadsTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/prediction/GhostPredictionHistoryTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/power/runtime/PowerDeliveryTickOrderingTest.java` |
| inherited-R10-workspace-or-prior-changes | ` M` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/prototype/ServerClassloadingTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureShipIdsTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoFloorResidualMathTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CurrentFrameCollisionScopeTraceTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ExteriorWallJumpIntentFactoryTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ExteriorWallTangentOracleTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectationTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceStateTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullStateMismatchEvidenceTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeCollisionGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeFrameIntervalTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideCollisionWitnessTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideContactProofTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/OwnedStepProbePositionTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorderTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/TrackedCollisionSubstepsTest.java` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `tooling/agent_workflow/.gitignore` |
| inherited-R10-workspace-or-prior-changes | `??` | same | same | same | same | same | same | yes | `tooling/agent_workflow/workflow.py` |
| preserved-prior-diagnostic-bytecode-artifact | `??` | absent | same | same | same | same | same | yes | `tooling/shuttle/__pycache__/prepare_impact_substeps_comparison.cpython-313.pyc` |
| preserved-prior-diagnostic-bytecode-artifact | `??` | absent | same | same | same | same | same | yes | `tooling/shuttle/__pycache__/test_prepare_impact_substeps_comparison.cpython-313.pyc` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `tooling/shuttle/prepare_impact_substeps_comparison.py` |
| prior-feedback08-hull-cost-reconnect-diagnostics | `??` | absent | same | same | same | same | same | yes | `tooling/shuttle/test_prepare_impact_substeps_comparison.py` |
