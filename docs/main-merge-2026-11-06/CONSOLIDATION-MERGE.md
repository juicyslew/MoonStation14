# Guns / TEG consolidation merge

## Scope and baselines

Owner-requested conflict resolution for transfer to the final refactor worktree. **Not sprint completion, feature acceptance, SS14 ECS approval, or resumption of Guns/TEG development.** No commit, push, client launch, native suite, or feature-readiness submission is part of this merge-only task.

- Branch/worktree: `agent/03`, `MoonStation14-agent-03`.
- HEAD: `69f055a5970394e59c6f1bab3b271597d29706df` (includes the earlier main merge and client presentation repair).
- Incoming: `d8fab70a7923d15f4a1ae69226f7a47ecd977051` (Guns plus paused TEG/main integration).
- Base: `b606b6bddbdcce0ff32bb43fe726bdf45f890635`.
- Original conflicts: 11 files, across hands/targeting, rendering, alarms/PipeNet, fixture leases, and server classloading coverage.

## Resolution boundaries

- **Hands:** One canonical drop factory retains M1 diagnostics/captured frame-world targeting and incoming captured containment destinations. Preserve required THROW capture, optional DROP capture, gun world-target versus dual-source control admission, and nullable empty-hand proofs. No second ownership commit or parallel item factory.
- **Queries:** Ordinary look direction, captured coordinates, forced directions, and generic gun rays reuse `LoadedFrameRayQuery`. Gun-only opacity filtering applies through caller-specific shape context for both world and frame geometry. Retain loaded-only/unknown failure, build-height endpoint clipping, throw caps and generic ray reach semantics; do not reinterpret a direction as a world coordinate.
- **Rendering:** Apply incoming model-rest transforms before the existing final-pose observation hook. Retain jug cutout material, produce tint/scale, gun-model assets and muzzle capture. Combine full visibility restoration/owned gun pass with M1 supported-body heading. Gun-aware prone posture must receive physical aim yaw, then apply the presentation heading offset once—not twice.
- **Atmosphere:** Retain incoming nonmodal alarm workspaces and PipeNet gas authority/topology invalidation. Preserve alarm frame identity, body authority and frame-aware reach/LOS through existing shared query owners. Do not restore the obsolete modal opening path or start the paused hotspot-scheduler proposal.
- **PipeNet API bridges:** Shuttle status uses the existing node-share inspection projection. Cabin totals capture one immutable shared-network image and count each network once; fixture lifecycle readers retain pre-teardown gas views and prove retirement through absence from live ownership plus archived identity/payload. Do not restore gas ownership on node metadata merely to satisfy old callers.
- **Fixtures/tests:** Preserve bounded M1 ticket diagnostics alongside the incoming shared wake-anchor and idempotent lease close. Combine classloading exclusions instead of dropping either side's coverage.

## Preserved movement baseline

Current working-tree blobs were checked against HEAD for 12 protected files: `GhostControlClient`, `OwnedPredictionApplicationSchedule`, `GhostPredictionHistory`, `ClientOwnedSimulationClock`, `ClientFrameTimelineStore`, `PackedLightAccountingScope`, `CharacterMovementMotor`, `GroundedHarnessMotor`, `GroundedHarnessPhysics`, `SlipSystem`, `LifecycleRegisteredPlayerGameTests`, and `BoundedGameTestWait`. All matched HEAD exactly at that check. Incoming older/base versions were not substituted for the repaired code.

The earlier bounded presentation gate `run_55736cd76061c3941792bd97` passed and cleanup is confirmed; it is **not validation of this combined merge**. GPU/manual appearance and broader merge correctness remain separate.

## Validation / transfer

All three compile tasks passed. The focused JVM batch ran **31 classes / 201 tests: 200 passed, one failed, zero errors/skips**. Independent conflict-resolution review passed with the inherited failure retained. No native/GPU, full-suite, new readiness, or combined feature acceptance is claimed. The owner performs the merge commit and transfer.

All 11 original conflicts are resolved and the reviewed resolutions/API bridges are staged; no merge commit was made. Incoming whitespace warnings in root source copies and several unchanged Guns/TEG test files were retained rather than pulled into merge-only cleanup.

The retained failure is `PipeGasTransactionTest.r17WasteMixturesReproduceOrRuleOutSecondVentPlanRejection`: at scale `.005`, 600 K and 1 s, a strict no-excess comparison sees reference total `5.407004937296733E-5` versus actual `5.407005301094614E-5` mol. The test and directly relevant gas kernels/config/prototype inputs match incoming `d8fab70a` exactly; this was not introduced by conflict resolution. Float storage explains the quantization scale but does not authorize a tolerance change. It remains a paused TEG investigation, not a passing check or a reason to retune the operating plan. Local merge-check evidence is under ignored `build/guns-teg-merge-check/`.

Start the refactor from `docs/guns-sprint/CONSOLIDATION-PAUSE.md` and `docs/teg-sprint/HANDOFF.md`: both explicitly record unresolved ECS architecture concerns and feature limitations. Preserve behavior, assets/attribution, invariants, and failure history without treating the current aggregate profiles/static owners as architectural precedents.
