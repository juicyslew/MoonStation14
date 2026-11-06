# R7 scoped attribution packet — NOT A READINESS RECEIPT

Candidate shuttle-feedback08-cargo-orientation-20261015-r7 snapshot `a6a8f675b4326749b809315e14199ab20c0a47437c3e7237bea32a0480d83db3`; R7 remains **needs_changes**. Planned full-JVM, focused-native, and full-native checks are **NOT_RUN**. Frozen manifest: **324 paths**.

R10/R6/R7 candidate-record file SHA-256: `{"r10": "6a8e60b88ecaf65aa22a6a0cb46691089ff2f2c29e499895e6dfd60f8af7a934", "r6": "dbe6a3c29a1fd18cfb3ab1e68957145757c32b6061c464d44bca9547ce1e956b", "r7": "6c42cc24c9a3d6063215cf6a32cc3c4f90bc0c2aefb196c02fe7b470326fd64f"}`. Pinned base `620face76b8c1374019ce074380a451d698f05e1`; observed HEAD `5c09e426d055d18932e1896b865912d2716a8278` (current match: True).

All R7 paths are hash-bound and live checked; mismatches: 0. Phase counts: `{"R6-manifest-entry-differs": 2, "absent-R6-manifest; HEAD-preimage-proof": 8, "inherited-R10-identical-status-and-bytes": 260, "same-R6-hash-and-status; R10-differs-or-absent": 54}`. Categories and all 324 path proofs are in `snapshot.json`. Focused patch: `changed-source-vs-head.patch` (48 scoped paths, 356084 bytes). R10 preimages are unavailable, so this HEAD-relative view may include lines inherited from R10; identical-status-and-hash files are explicitly marked inherited and excluded from this patch. No correctness claim follows from byte identity.

R6/R10 matching means only identical recorded hash AND status, not first introduction. Differences are reported against R6 first when an R6 entry exists; no first-introduced chronology is inferred. Candidate HEADs are checked before comparison; no cross-HEAD equivalence is inferred. Root `net/...` extracted sources and `META-INF` jar artifacts matching R10 are labeled inherited artifacts, not newly claimed gameplay changes.

The lead clarified for session `ses_ec03d58e3ffetz6U2hO0DJz6VG` (message `msg_13fc5cc51001MTGkOiPBXI5meA`): on a fresh revision with attributable scope and adequate plan, the integration lead may run declared exact protected checks; readiness depends on actual results and independent review. This is not a receipt or pass; R6 cannot establish R7 readiness and known 40/80 remain failures.

## Scoped change index

| Attribution | Phase | Path |
|---|---|---|
| prior-feedback08-test-support | same-R6-hash-and-status; R10-differs-or-absent | `build.gradle` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressMixinBootstrap.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressObserver.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressState.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/mixin/GameTestTickerProgressMixin.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/examine/CargoExamineGeometryGameTests.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/CargoPickupGeometryGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistory.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleShuttleCargoReconnectGameTests.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/command/ShuttleCargoSetupGameTests.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoActorDropOrientationGameTests.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/GameTestProgressGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectation.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceState.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometry.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactProbe.java` |
| feedback08-shared-fixture-extensions | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingCabinAuthorityFixture.java` |
| feedback08-shared-fixture-extensions | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureScene.java` |
| feedback08-shared-fixture-extensions | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorder.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleServerCostGameTests.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | `src/gametest/resources/moonstation14.gametest.mixins.json` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/entities/LooseFrameOrientation.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/eventhooks/ModEventHooks.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineTargetResolver.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineWorldSelector.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/examine/client/ExamineClient.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/hands/client/SelectedLooseOutlineRenderer.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyHandItemTransfer.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyItemTargetProbe.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyLooseThrow.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/NativeHeldItemTransfer.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometry.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureCommands.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/entities/LooseFrameOrientationTest.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | `src/test/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressStateTest.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryExtentTest.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometryTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistoryTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectationTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceStateTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullStateMismatchEvidenceTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometryTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorderTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `tooling/shuttle/prepare_impact_substeps_comparison.py` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | `tooling/shuttle/test_prepare_impact_substeps_comparison.py` |

## Full R7 path accounting

| Category | Phase | R10 hash/status match | R6 hash/status match | Live hash | Path |
|---|---|---:|---:|---:|---|
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `.gitignore` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `.opencode/agents/fidelity-reviewer.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `.opencode/agents/integration-lead.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `AGENTS.md` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-2.0.6.jar` |
| prior-feedback08-test-support | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `build.gradle` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `capturebuild/shuttle-feedback-r14-author-chemfixture.log` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/airlocks-sprint/BUMP.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/airlocks-sprint/PARITY.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/development-workflow/README.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-grid-feasibility-sprint/SPRINT.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/BASELINE.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/BODY-CARRY.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/BUMP-FEEDBACK.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/CABIN-VENTILATION.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/CARGO-ORIENTATION.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/CRITICAL-CONTACT-VALIDATION-06.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/ENGINE-PROOF.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/EXAMINATION.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/EXECUTION.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/FRAMES-AND-OWNERS.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-06.json` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-07-FIX.json` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-07-REPRO.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R1.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R5.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R6.json` |
| feedback08-documentation-or-workspace | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-ORIENTATION-R7.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-CARGO-RECONNECT-R1.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-COST-COMPARISON-R2.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-COST-COMPARISON.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-IMPACT-REPRO.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-IMPACT-SUBSTEPS-COMPARISON.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-SUBSTEPS10-REGRESSION-R2.json` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN-08-SUBSTEPS10-REGRESSION.json` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/GATE-PLAN.json` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/HANDOFF.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/Instructions.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/MANUAL.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/NATIVE-PRECISION.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/OUTLINE-FEEDBACK.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK-06.md` |
| feedback08-documentation-or-workspace | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK-07.md` |
| feedback08-documentation-or-workspace | R6-manifest-entry-differs | no/absent | no/absent | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK-08.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/OWNER-FEEDBACK.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/PARITY.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/POWER-SIGNAL-PHASE-06.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/QUERY-ACCESS.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/RECONNECT-LANDING-06.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/RECONNECT.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/SABLE-BLOCK-UPDATES.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/SABLE-INVENTORY.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/SABLE-REMOVED-HEATMAP.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/SABLE-REMOVED-SAVE.md` |
| feedback08-documentation-or-workspace | R6-manifest-entry-differs | no/absent | no/absent | yes | `docs/shuttle-relocation-sprint/SHIP-FIXTURE.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/WALL-RESPONSE-06.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/WALL-RESPONSE.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `docs/shuttle-relocation-sprint/licensing-review.md` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `gradle.properties` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/renderer/block/model/BakedQuad.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/renderer/block/model/BlockElement.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/renderer/block/model/BlockElementFace.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/renderer/block/model/BlockModel.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/renderer/block/model/FaceBakery.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/renderer/texture/SpriteContents.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/renderer/texture/TextureAtlasSprite.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/client/resources/model/BlockModelRotation.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/minecraft/server/packs/resources/ResourceMetadata.java` |
| inherited-R10-extracted-source-or-jar-artifact | inherited-R10-identical-status-and-bytes | yes | yes | yes | `net/neoforged/neoforge/client/model/ExtraFaceData.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `opencode.jsonc` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `shuttle-feedback-05-save-51-focused.log` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/HumanHarnessMovementGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/HumanHarnessTravelOwnershipGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/LooseObjectEffectsGameTestEntry.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/LooseObjectEffectsGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/atmos/AirAlarmCandidatesGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/power/runtime/ApcOverloadGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/power/runtime/PowerAppearanceGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/power/ui/ApcMenuGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/BoundHumanFixture.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/BoundedGameTestWait.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/FixtureChunkTickets.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressMixinBootstrap.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressObserver.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressState.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/GameTestTagRegistrar.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/PowerFixtureReadiness.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `src/gametest/java/com/juicyslew/moonstation14/gametest/support/mixin/GameTestTickerProgressMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/atmos/piping/alarm/ui/AirAlarmBodyReachGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/chemistry/chemmaster/interaction/ChemMasterWorldDumpGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceAlarmWorkflowGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceButtonAirlockWorkflowGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/disposal/transport/DisposalSpatialTransportGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/doors/AirlockConnectedGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/doors/AirlockCoreGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/doors/AirlockToolAccessGameTests.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/examine/CargoExamineGeometryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/ServerCharacterAuthorityFixture.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/CargoPickupGeometryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/FixtureHeldTool.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live/HeldTimedIngestionGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/hands/network/LifecycleHandActorFixture.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/mobs/OwnedCorpsePhysicsGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/character/MovementEligibilityGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistory.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleAbandonedActiveReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleActiveShuttleInputGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCreativeReconnectPolicyGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleFallingShuttleReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleMovementRecoveryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleMovingShuttleReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecyclePairedWorldStepObserver.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecyclePassiveShuttleBodyGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleShuttleCargoReconnectGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/CabinGasInventorySnapshot.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleCabinFixtureGameTests.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/command/ShuttleCargoSetupGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/command/ShuttleFixtureCommandGameTests.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoActorDropOrientationGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoOrientationGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ExteriorWallSustainedJumpGameTests.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/GameTestProgressGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectation.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceState.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometry.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainImpactProbe.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/LoadedFrameRayQueryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAirlockPanelGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAirlockPryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAirlockWireGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingAlarmMenuGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingApcMenuGameTests.java` |
| feedback08-shared-fixture-extensions | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingCabinAuthorityFixture.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/MovingFrameRayDiagnostic.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeAirlockContactGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureLifecycleGameTests.java` |
| feedback08-shared-fixture-extensions | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureScene.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOperatingEnvelopeGameTests.java` |
| feedback08-shared-fixture-extensions | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SablePlotBlockEntityGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SablePrecisionPairGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableRemovedHeatMapTickGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorder.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleCabinVentilationGameTests.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ShuttleServerCostGameTests.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/gametest/java/com/juicyslew/moonstation14/ms14/shuttle/sable/compat/SableNestedBlockChangeGameTests.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `src/gametest/resources/moonstation14.gametest.mixins.json` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/MoonStation14.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/MoonStation14Client.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/entities/LooseClientPositionInterpolation.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/entities/LooseFrameOrientation.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/eventhooks/ModEventHooks.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/EntityAirlockBumpMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/LivingEntityAirlockBumpTravelMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/LivingEntityMindHarnessTravelMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/LooseNativeMoveObserverMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/MobMindHarnessOwnerMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/LooseCargoPacketFrameMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/SableOwnedRemoteFrameEntityMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/SableOwnedRemoteFrameLivingMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/client/SableRemotePlotProjectionPlugin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/LooseNativePreCutoffMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableCollisionInfoAirlockContactsMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableLevelChunkBlockChangeContextMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableNativeAirlockContactCaptureMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableOwnedCollisionPoseMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableOwnedNativeCollisionTraceMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableRemovedHeatMapTickMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableSerializationFailureDiagnosticMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/SableTrackedCollisionSubstepsMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/mixin/compat/VanillaWorldCollisionObservationMixin.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/LoadedPipeGasOwner.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/alarm/ui/AirAlarmMenu.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/alarm/ui/AirAlarmMenuService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/fixture/FixturePipeAdmission.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/atmos/piping/fixture/VentilationFixtureService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceEndpoint.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceNetworkLifecycle.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/devicenet/DeviceNetworkTransport.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockBlockEntity.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockBumpMoveAccess.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockBumpSystem.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockDoorwayActors.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockPryService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockToolInteraction.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/AirlockToolLineOfSight.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/NativeAirlockContact.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/NativeAirlockContactAccess.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/doors/ui/AirlockWireMenuService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineTarget.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineTargetResolver.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/ExamineWorldSelector.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/FrameBoundsTransform.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/FrameExamineGeometry.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/client/ExamineClient.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/examine/client/ExamineSelector.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/client/SelectedLooseOutlineRenderer.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyHandItemTransfer.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyInteractionAccess.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyItemTargetProbe.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/BodyLooseThrow.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/LoadedBlockGetter.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/LoadedFrameRayQuery.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/live/NativeHeldItemTransfer.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyBlockUseTargetProbe.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandActionNetworking.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandRequestService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandStateService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyWorldItemActions.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometry.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseItemRenderer.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/CharacterMovementMotor.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/ContactPositionCorrectionEvidence.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/HorizontalContactEvidence.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/MovementCollisionResolver.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/movement/NativeCollisionSlop.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/BodyControlRegistry.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessWorldStep.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/MindControlledMob.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/OwnedCorpsePhysics.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/OwnedLivingPhysics.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/CharacterMovementRebasePolicy.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/CharacterPredictionFailurePolicy.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/ClientFramePredictionState.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/PlayerLifecycleRegistry.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/persistence/LoadedBodyResolver.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/persistence/MinecraftLoadedBodyAdapter.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControl.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleDevelopmentMode.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleExistingBodyReconnect.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleFirstJoinHandler.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleServerContext.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleStartupRuntime.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterMovementHoldPolicy.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterMovementSafePose.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterWorldStepRecoveryPolicy.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlNetworking.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlPayloads.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/prediction/GhostPredictionHistory.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/server/GhostMobHarnessControl.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/graph/PowerGraphService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/runtime/PowerRuntime.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/ui/ApcMenu.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/power/ui/ApcMenuService.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleCabinFixture.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureCommands.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureShipIds.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CurrentFrameCollisionScope.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeCollisionGeometry.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeFrameInterval.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSaveFailureDiagnostic.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideCollisionWitness.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideContactProof.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/OwnedNativeMoveDiagnostic.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixturePlot.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableFrameVelocity.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyMotion.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedStepProbe.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/TrackedCollisionSubsteps.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/java/com/juicyslew/moonstation14/ms14/timed_interaction/TimedInteractionSystem.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/resources/moonstation14.mixins.json` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/main/templates/META-INF/neoforge.mods.toml` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/entities/LooseClientPositionInterpolationTest.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/entities/LooseFrameOrientationTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/entities/NativePreCutoffTest.java` |
| R7-test-progress-tooling | absent-R6-manifest; HEAD-preimage-proof | no/absent | no/absent | yes | `src/test/java/com/juicyslew/moonstation14/gametest/support/GameTestProgressStateTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/gametest/support/PowerFixtureReadinessTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/LooseCargoPostMergeMixinTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/NativeAirlockContactCaptureStructureTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/SableOwnedRemoteFrameMixinTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/SableOwnedStepProbeMixinStructureTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/SableSerializationFailureDiagnosticTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/mixinvalidation/VanillaWorldCollisionObservationMixinTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/doors/AirlockDoorwayActorsGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/examine/ExamineCellTraversalTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/examine/ExamineResponseContractTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/examine/FrameExamineGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClientPolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandStatePolicyTest.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryExtentTest.java` |
| feedback08-cargo-orientation/setup | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/loose_objects/LooseItemQueryGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/movement/CharacterMovementMotorTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/movement/HorizontalContactEvidenceTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/movement/NativeCollisionSlopTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/client/CharacterMovementRebasePolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/client/ClientFramePredictionStateTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/PlayerLifecycleRegistryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/persistence/LoadedBodyResolverTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/CargoReconnectWitnessHistoryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleCharacterSessionControlTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleExistingBodyReconnectTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleFirstJoinHandlerTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleStartupRuntimeTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterMovementHoldPolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/CharacterWorldStepRecoveryPolicyTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/network/GhostControlPayloadsTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/player_body_control/prediction/GhostPredictionHistoryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/power/runtime/PowerDeliveryTickOrderingTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/prototype/ServerClassloadingTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureShipIdsTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CargoFloorResidualMathTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/CurrentFrameCollisionScopeTraceTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ExteriorWallJumpIntentFactoryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ExteriorWallTangentOracleTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactSubstepsExpectationTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullImpactTraceStateTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullStateMismatchEvidenceTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/HullTerrainGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeCollisionGeometryTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeFrameIntervalTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideCollisionWitnessTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/NativeSideContactProofTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/OwnedStepProbePositionTest.java` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/ServerTickCostRecorderTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `src/test/java/com/juicyslew/moonstation14/ms14/shuttle/sable/TrackedCollisionSubstepsTest.java` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `tooling/agent_workflow/.gitignore` |
| inherited-R10-workspace-or-prior-changes | inherited-R10-identical-status-and-bytes | yes | yes | yes | `tooling/agent_workflow/workflow.py` |
| preserved-prior-diagnostic-bytecode-artifact | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `tooling/shuttle/__pycache__/prepare_impact_substeps_comparison.cpython-313.pyc` |
| preserved-prior-diagnostic-bytecode-artifact | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `tooling/shuttle/__pycache__/test_prepare_impact_substeps_comparison.cpython-313.pyc` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `tooling/shuttle/prepare_impact_substeps_comparison.py` |
| prior-feedback08-hull-cost-reconnect-diagnostics | same-R6-hash-and-status; R10-differs-or-absent | no/absent | yes | yes | `tooling/shuttle/test_prepare_impact_substeps_comparison.py` |
