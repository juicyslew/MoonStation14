# Seed-reference and scream validation handoff

Candidate: `botany-seed-reference-scream-20261015-r1`  
Sprint: `botany-genetics-sprint`  
Base: `70b3bace62ee1e3b879e7556530a33e0f3653b9b`

## Gate contract

Use `seed-reference-scream-gate-plan.json` as the executable gate plan. Its JVM selector is the exact M5 care baseline (20 unique classes) plus `PlantSeedReferenceTest`, `GeneticCarriersTest`, `PlantSeedReferenceLifecycleTest`, and `PlantScreamMutationTest`: 24 unique classes. `PlantMutationCycleTest` and `PlantMutationTableTest` already belong to the baseline; do not append a second copy or repeat the earlier erroneous 19-class count. The expected fresh XML discovery is 129 tests, with zero failures, errors, or skips. The lead must verify actual fresh XML, not infer counts from the selector.

Require serialized one-worker build and JUnit invocations as specified in the plan. A prior focused run reported 24 classes / 129 tests / zero failures, errors, or skips, and compile later passed, but native-auth/auto-harvest edits since that evidence require a fresh compile and all gate evidence. Earlier concurrent-output disruption was followed by a normal compile pass; neither substitutes for the fresh gate. Preserve owner configuration and all existing artifacts.

Native selection retains the nine prior tags and adds `botany-scream` and `botany-seed-reference`. Use only the new designated world directory `build/gametest-botany-seed-reference-scream-r1` and log `build/reports/botany-genetics/seed-reference-scream-native-r1.log`; never delete or overwrite prior runs. Require fresh discovery before reporting selected/total. Historical interaction/prediction evidence of 101 tests is not current gate evidence.

## Behavior and evidence boundaries

The acceptance contract includes all criteria in the JSON plan. In particular, ensure batch validity/admission precedes COW detachment and all effects; invalid batches must not cause identity side effects. This is an explicit Minecraft batch-adaptation ordering requirement, not a claim that SS14 has that batch API. Prove the no-COW invalid-batch case and the admitted sub-unit solution/level-25 boundary.

Native fixture map: `PlantCareGameTests` covers two ordinary poll cases; `BotanyHarvestAuthenticatedGameTests` covers authenticated unique-carrier and M6 manual harvest; `PlantAutoHarvestGameTests` covers reference sharing and three scream cases. `PlantSeedReferenceGameTests` has three helper component/codec cases only, not proof of the ordinary consumer path. Verify actual source method/test names during gate execution rather than assuming the shorthand labels here are Java method identifiers.

Manual presentation is bounded: existing `/ms14 botany_fixture` is partial, has no complete every-mutation UI, and does not establish an enabled-scream station. Do not prescribe an invented owner interaction or claim a manual scenario for scream. Pollen swab art/request remains open; capture/cross gameplay is not implemented, so do not call this a complete pollen-item/cross showcase. No repeated gas test is requested. Full sprint owner review and acceptance remain outstanding.

## Verified design/source record

`POLLEN-IDENTITY-DESIGN.md` documents the architecture decision: immutable genetics values, opaque identity-based shared reference, resolved-reference-first reads, COW, source-sharing boundaries, and detached persistence/client decoding. COW design has approval; code-independent review remains pending. Required SS14 pin is `c9df5ef5d675b0d1d226828bddf6b78c28502d91`; the parent reports verifying the pin and M6 worker verified all eleven reused clip hashes. `M6-SCREAM-COMPOSITION.md` records source mapping, typed resolved scream default, toggle/copy behavior, server tray audio adaptation, and explicit sampling-path gap. Do not elevate these records to fresh gate evidence.

Current limits remain: pollen swab assets/request open; capture/cross absent; Sentient, Slippery, Kudzu, and Lignification unsupported; only five species currently available versus the broader catalog, with 65 grown assets open and 10 item requests open. No claim of full native/connected visual review, full suite, or owner acceptance is supported by this handoff.
