# Singularity gravity and revive

## STATUS

Closed with owner acceptance on 2026-10-21, with the pre-ghost revival bug explicitly deferred. The owner confirmed the pull feels correct, attracts jugs, and permits slingshot-like motion around the source. After reporting the remaining death-state bug, the owner requested removal of the unfinished workaround and sprint closure. This is not acceptance of that bug as fixed.

The unfinished immediate-ghost changes were removed. `ObserverSystem.java`, `LifecycleRegisteredPlayerGameTests.java`, and `PARITY.md` were restored byte-for-byte to the accepted r8 source manifest. A full comparison of that manifest found no missing files or hash mismatches before this handoff was added. No commit, merge, or installation is established here.

## ARCHITECTURE

- `SingularityEntity` consumes the resolved typed `SingularityGameplayProfile` from the runtime catalog; unavailable profiles fail closed.
- `SingularityGravityPulse` is the shared continuous client/server force calculation. Keep one gravity kernel rather than separate player and loose-item implementations.
- Matter consumption retains its independent cadence. Player lethal contact uses a separate small core; NPC/terrain consumption retains the general horizon.
- Gravity fixtures use real registered singularities, resolved starting energy and ordinary wander/drain. They neither feed nor lock the stage. Ownership tracks one source UUID per operator, so replacement/clear affects only that source.
- Revive/return uses existing observer credentials and canonical RETURN payloads, epoch checks, lifecycle ownership and Begin/Ready/END transport. The accepted fix covers return after ghosting, not the deferred pre-ghost case.

## IMPORTANT FILES

- `docs/singularity-gravity-sprint/{OPERATOR,PLAN,SCOPE,PARITY}.md`: setup, scope and pinned-source mapping.
- `src/main/java/com/juicyslew/moonstation14/entities/SingularityEntity.java`.
- `src/main/java/com/juicyslew/moonstation14/ms14/singularity/`: profile, shared force, consumption and fixture services.
- `src/main/resources/data/moonstation14/singularity_gameplay/singularity.json`.
- `src/main/java/com/juicyslew/moonstation14/ms14/medical_observer/client/{ObserverClient,ObserverMedicalScopePolicy}.java`.
- `src/gametest/java/com/juicyslew/moonstation14/ms14/player_body_control/lifecycle/server/LifecycleRegisteredPlayerGameTests.java`.
- `src/main/java/com/juicyslew/moonstation14/ms14/power/graph/{LoadedPowerGraph,PowerGraphService}.java`: bounded loaded-frontier reconciliation retained from validation fixes.

## SS14 CORRESPONDENCE

`PARITY.md` maps the verified checkout pin `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, root `Singularity` prototype (no parents), explicit components, omitted defaults and runtime-derived stage behavior to local typed profiles and live consumers.

Continuous force, multiplier/ramp, expanded range and player-only core are deliberate Minecraft adaptations, not upstream values. Base gravity range remains `2*(level+1)`; effective range is three times base, with full force at twice base. Player core radius is `min(0.35*horizon, 0.35 blocks)`.

SS14 normally ghosts on directional movement after death. Its Mind remains attached to the body before ghosting; returnable ghosting visits an observer while retaining the body link. Immediate ghosting was discussed as a workaround but is **not implemented**. A closer SS14 lifecycle model is future work.

## DECISIONS / WHY

- Preserve accepted lens, natural wandering/drain, finite PA feeding, beacon behavior and terrain consumption.
- Do not treat a dead body, observer presentation and control ownership as interchangeable concepts.
- The native validation world must be fresh when persisted lifecycle state prevents admission. Production startup intentionally rejects persisted `ACTIVE` profiles rather than repairing them silently. Random fixture account IDs do not bypass that world-level guard.
- Package evidence requires `cleanJar assemble`; an unchanged content hash alone does not prove or disprove a fresh build.

## KNOWN GAPS

- **Deferred owner-reported bug:** reviving while dead but before movement creates a ghost can leave the body alive without usable control. Do not claim this path fixed. Death-to-ghost still requires movement.
- General hand prediction and rear-facing lens wrapping remain deferred. No additional connected camera/movement preservation claim is established.
- Singularity-consumed-body revival is excluded.

## VALIDATION

Coordinator `run_cc7497dbc5195c659b2dde95`, generation 3: **PASS**, acknowledged. Frozen receipt records 131 passing selected unit tests, 62 interaction-readiness tests, 53/53 focused GameTests selected from 933, and successful `cleanJar assemble`. Coverage, implementation and parity reviews passed. This was not a full GameTest run. No separate post-owner-acceptance regression is established.

Ignored preparation/evidence index: `build/agent-workflow/singularity-gravity-sprint/coordinator-r8/`; durable admitted evidence belongs to the coordinator archive. Failed repeated-world fixture was preserved at `build/workflow-fixture-failures/bf196f8b7e8801449ac0ca8d`; do not mistake it for the passing fixture.

After removing unfinished workaround edits: manifest hash comparison passed; no new behavioral test run was claimed.

## NEXT WORK

Investigate the deferred revival path through `ObserverSystem.tryRecoverBoundCorpse`, `LifecycleMedicalObserverBridge.returnToBody`, `LifecycleServerContext.returnRevivedCorpse` and `PlayerLifecycleRegistry.returnRevivedCorpseDurably`. The registry already accepts exact inactive `DEAD_CLAIM` as well as ghost-return cases; a blanket claim that it requires GHOST is incorrect. The precise failing transition remains unproven. Add authenticated death/revive-without-movement coverage before changing ownership rules. Reuse the existing transition services; avoid a parallel spectator framework or weakening persistence/admission checks.
