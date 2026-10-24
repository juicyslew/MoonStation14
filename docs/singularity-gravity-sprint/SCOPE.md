# Singularity gravity candidate scope

## Current r26 candidate-preparation boundary (15 October 2026)

r26 is a fresh readiness candidate, not a pass or approval. `warningBandTrajectoryMeasuresFourSecondNeutralDriftThenThreeSecondRunout` uses 80 neutral ticks followed by 60 outward-input ticks at stages 1–6: a **validation input schedule**, not gameplay timers or automatic escape. It uses the resolved human motor and shared production gravity kernel. The focused trajectory class passed 5/5 tests; modeled drift and outward progress do not establish connected-client feel.

`ObserverClient` uses the existing medical-scope policy to reconcile ghost credentials and construct the existing epoch-bound RETURN payload. Its focused policy class passed 5/5 tests, including composed snapshot-to-request freshness, identity, revocation and END cases. Camera and movement behavior is unchanged. `registeredLifecycleDeathGhostReviveAndReturn` uses a real registered player, production packet listener and lifecycle bridge—not a FakePlayer/backend substitute—to exercise confirmed death, ghost Ready, `/ms14 revive`, old-epoch rejection, RETURN, END and CHARACTER Ready on the original body. It waits for the normal server-Post-tick Begin delivery. The observer-tag diagnostic passed 10/10 selected of 931; this establishes the tested server path, not remote-client input, codec or handshake behavior.

The ordinary-core diagnostic also passed 15/15 selected of 930. Neither diagnostic is readiness approval. R25 was rejected before running its checks; all seven fresh r26 checks remain required. Connected-client R input, camera/runtime feel and owner acceptance remain unverified; general hand prediction and rear-facing wrap remain owner-deferred.

Retained unchanged work is provenance, not new feature scope or blanket approval. Existing owners and useful regression boundaries: power graph/runtime and harness lifecycle (`PowerGraphService`, `PowerRuntime`, `PowerGraphTest`, `PowerDeviceGameTests`, `ApcOverloadGameTests`, `FixtureChunkTicketsGameTests`); Chemistry/ChemMaster, ingestion, doors and ventilation sessions (`ChemMasterSessionService`, `ChemMasterOpenRequestGameTests`, `HandlessIngestionGameTests`, `AirlockConnectedGameTests`, `VentilationFixtureGameTests`); resolved prototype and item-footprint resources (`PrototypeRuntime`, `PrototypeRuntimeTest`, `ItemFootprintResourceTest`); and singuloosing/PA/beacon/containment blocks, assets and provenance (including `ParticleAcceleratorFiringGameTests`, `FieldContainmentRuntimeGameTests`, `SingularityBeaconProfileTest`, plus texture provenance metadata). Body lifecycle/travel ownership stays with `ActiveCharacterPolicy`, `RegisteredPlayerReadyDriver`, and `LivingEntityMindHarnessTravelMixin`, tested at the native player/core boundary. These are responsibility/test-owner pointers, not claims of individual authorship or approval.

Prior review groups 17+72+15 describe historical coverage, not the final candidate's current hashes. The r26 identity and review records bind current files and explicit follow-up reviews; stale documentation hashes in the r25 helper record are not reused as current review evidence.

All seven current lead-owned checks remain pending: JUnit, interaction readiness, focused native tests, blocker native tests, full untagged native suite, `cleanJar`, and `assemble`. No r25 result carries forward. Full current-snapshot evidence and review are required; no gate approval or owner acceptance is asserted.

This note records the scoped review boundary for the r16 resubmission. It is not an approval. The r15 exact snapshot (`ab9f69a2a6ce46ee8183217cf06eb723d8b68b578f66c722fd0899aeda8b5060`) passed all six planned checks, but its scoped review remains **needs_changes** pending reconciliation of current-snapshot evidence and review of the retained broad dirty scope.

## r12 → r13 delta

Both candidate snapshots use base `5c349cf5fdfb89780622d6010aa20c108fb7451c`. r12 had 99 dirty paths and r13 had 100; that one-path delta is the new `src/gametest/java/com/juicyslew/moonstation14/ms14/singularity/fixture/SingularityGravityFixtureGameTests.java`. The complete seven-path r13-only change set is:

- `src/gametest/java/com/juicyslew/moonstation14/ms14/singularity/fixture/SingularityGravityFixtureGameTests.java` — native exercise of the instant gravity fixture.
- `src/main/java/com/juicyslew/moonstation14/entities/SingularityEntity.java` — gravity behavior and player-core integration.
- `src/main/java/com/juicyslew/moonstation14/ms14/singularity/fixture/SingularityFixtureCommands.java` and `SingularityFixtureService.java` — command/service support for the gravity fixture.
- `src/test/java/com/juicyslew/moonstation14/ms14/singularity/fixture/SingularityFixturePowerValidationTest.java` — fixture-power validation relevant to the quick fixture, not a claim that unrelated power behavior changed or is approved.
- `docs/singularity-gravity-sprint/PLAN.md` and `OPERATOR.md` — feature plan and operator instructions.

The shared motor and player classification are already part of the gravity plan's implementation scope. The added fixture validation test is relevant because the instant fixture reuses power/fixture validation boundaries; it is not justification for broadening this work into unrelated power behavior.

## Retained dirty baseline and review

The r12/r13 comparison found the flagged hands, ingestion, airlock, ventilation, PowerRuntime/PowerGraphService, fixture chunk-ticket, and test-infrastructure hashes identical between those snapshots. These are retained dirty baseline, not newly introduced gravity changes; that provenance is not blanket approval. `docs/singuloosing-sprint/HANDOFF.md` records prior accepted singuloosing work, but does not approve every dirty file. Do not clean or rewrite unrelated files. Broad changed paths remain subject to current explanation and focused regression review. The r16 candidate delta from r15 is documentation/planning only; no runtime/source change is claimed.

PowerGraphService's stale-unload fix and its regression test are part of the retained baseline; the test exercises the actual consumer path, but a unit test alone is not native causal proof. Include appropriate current regression evidence rather than treating unchanged hashes or an earlier pass as sufficient.

## Evidence status

The bound r15 integration report and receipt record all six checks PASS: JUnit **3567/3567**, interaction readiness **62/62**, focused GameTest **28/930**, blocker GameTest **45/930**, unfiltered GameTest **930/930**, and assemble. This evidence is for r15's exact snapshot only. The initial scoped review necessarily preceded execution of the serialized checks; therefore absence of candidate-native logs at that review stage was expected, not a validation failure. After execution, the review needs to reconcile the generated logs/receipt and review current scope before a final gate decision. The r15 report remains `needs_changes`; logs do not silently change that verdict. Owner manual interaction remains NOT RUN and is owner acceptance/runtime evidence, not an author-approval prerequisite. Native fixture coverage is in `SingularityGravityFixtureGameTests` (stages 1–6, status, replacement, owned clear, unrelated preservation); the manual quick fixture remains useful for owner feel/acceptance.

Preserve all unrelated working files. This document is scope clarification only: it does not authorize source cleanup or edits, alter candidate records, or change the existing gate verdict.

## r14 check-plan scope note

`build/agent-workflow/singularity-gravity-sprint/gate-plan-r14.json` carries the same six checks and eight acceptance criteria, retaining the focused and blocker selectors. It assigns distinct fresh native-world directories to focused, blocker, and unfiltered full GameTest runs. Full GameTest is explicitly untagged and uses `--no-daemon`. Readiness remains interaction-only; general prediction readiness remains owner-deferred for the pre-existing empty `BodyHandClientPolicyTest`. These are planned checks, not results; no r14 validation is claimed here.

The r16 plan retains the same eight criteria and six checks/selectors, with untagged full GameTest, `--no-daemon`, and three distinct fresh native-world directories. These are planned reruns, not existing r16 results; the integration lead executes checks after source review and reconciles their resulting logs. Manual owner path remains gravity stages 1–6/status, normal-gravity approach/retreat and overhead lift without flight/immobilization/feed/freeze, repeat-stage ownership replacement, then owned-only clear; stage 6 is dangerous, stand back. Powered commissioning is optional. Startup topology failure remains unresolved; hand prediction/client feel and rear wrap are deferred; no GPU check (no shader changes). Preserve the retained dirty baseline and do not claim blanket approval.

## r20 fresh candidate boundary

r20 is a fresh code candidate combining resolved-prototype singularity gravity tuning with a separate revive-input task; it is not an artifact-only carry-forward. Current delta to review includes gravity fields/kernel, player cap/source query/client observer policy, revive input and authority/admission behavior, and related tests. Verify pinned upstream ancestry and resolved local defaults; specified profile base range is 2*(L+1), effective range 3*base, smooth ramp full at 2*base. Player core is min(0.35*horizon,0.35 blocks); NPC/terrain horizons remain unchanged and carriers are excluded. Prior user approval of adaptation does not establish parity or blanket approval of retained dirty paths.

The r20 plan requires seven fresh checks: JUnit; interaction selector only (general prediction remains owner-deferred/pre-existing gap); one focused native batch including observer and authority-support coverage; existing blocker native batch; full untagged native suite; cleanJar; then assemble. All commands use `--no-daemon --console=plain`. Native runs have distinct fresh directories `singularity-gravity-r20-{focused,blocker,full}-fresh-a` and current logs; no r16/r19 receipts carry forward. Criteria cover prototype defaults, warning/runout timings, player cap, all-level fixture placement and exact owned-UUID query, live level-4 core contact, revive body/credentials/stale watermark/shared server authority with camera/movement unchanged, regressions, and fresh JAR. Manual steps are in OPERATOR.md and remain NOT RUN. No current readiness result or owner acceptance is claimed.
