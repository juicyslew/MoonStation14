# Foundation validation — candidates r1–r7

The serialized integration gate for `botany-genetics-foundation-20261014-r1` passed all four recorded checks, but the candidate verdict is **NEEDS_CHANGES**, not technical-ready. The gate identified insufficient composition mapping; submission metadata described the native fixture as not yet run, and the gate subsequently ran it successfully. The composition audit is now in progress; complete and document it, then submit a new r2 candidate/gate. These are historical r1 results, not evidence of a fresh r2 pass.

Evidence: `../Asset Building Workspace/work/agent_exchange/gate-reports/botany-genetics-foundation-20261014-r1.json` (SHA-256 `e112687eeae0d05e0d044cf80431097c5569342c7b31c526bb75dce3bcd51719`). Recorded gate checks:

- Fresh compile/test/GameTest compilation and jar build passed; candidate-bound jar SHA-256 `936886ea2458e6e3a342fa7cc86a9630268f39294ab5408c6b86cdde5eb4d1b7`.
- Focused JVM tests: 23/23 passed across five classes.
- Filtered `interaction,prediction` readiness: 101/101 passed.
- Filtered native GameTest tags `botany,botany-genetics`: 26 selected of 849 total, 26 passed. This includes authenticated altered-seed planting, natural growth, harvest, extraction, and replanting.

This does not establish full-suite status, GPU/visual review, all 71 catalog entries, pollen-alias coverage, mutation gameplay, composition parity, or owner acceptance. Those remain unverified or unimplemented as applicable. Gate success on these checks is not acceptance of the sprint.

## r2 preparation — post-r1 changes, not yet gated

Keep the r1 counts above historical; they do not cover these changes. Source review passed for the five-species foundation after a small `BaseItem` correction. The composition audit is now corrected for that foundation slice, independently of the still-incomplete full catalog audit.

- Added two explicit codec tests: persistent `GeneticValue` JVM coverage and registered item-stack/component network coverage. Both compile; their targeted execution is pending.
- Extractor profile is corrected to 1000 W using the already-existing shared power-runtime allocation. The registered extractor/altered-genetics extraction test now runs with the shared air-alarm power fixture at its known fresh 1000 W allocation. The main botany fixture's existing HV→MV→APC line serves the 1000 W extractor and 750 W grinder; status checks real allocation. The unsupplied foundation test fails closed. These sources compile, but the updated native tests have **not** run; r1 native evidence ran prior to this correction with 0 W and does not validate the 1000 W setup.

These are r2 preparation/source-review facts only. Do not report updated codec execution, updated native behavior, or a new gate pass until the new candidate is gated.

## Candidate r2 — gate result

The central gate for `botany-genetics-foundation-20261014-r2` passed build/jar production (bound jar SHA-256 `d0ee68d374f910e8db0ebc3422cbaa1b9eccdba0b7b2e050284506891ecb4afe`), focused JVM tests (28/28 across six classes), and filtered `interaction,prediction` readiness (101/101). Its filtered native GameTest run selected 27 of 850 fixtures and failed: the gate found no all-required-passed marker. The retained native log identifies the sole failing registered test as `SeedExtractorRegisteredPlayerGameTests.registeredPlayerExtractsAndPlantsFreshWheatSeedPacket`; it fails at assertion `normal produce pickup` before reaching extraction/replant. Thus 26 selected native fixtures passed and one failed. This is a powered registered-player pickup failure, not a demonstrated extractor 1000 W regression or a successful end-to-end native transmission.

The composition audit was not the r2 gate blocker. The pickup failure is under read-only diagnosis for r3; no production fix is yet claimed. Failed artifacts are retained. Candidate r2 is **NEEDS_CHANGES**, not technical-ready; owner acceptance remains outstanding. r1's separate historical native results and 0 W setup are unchanged and must not be conflated with this r2 run.

## Candidate r3 — gate result

The central gate for `botany-genetics-foundation-20261014-r3` passed build, 28 focused JVM tests, and filtered `interaction,prediction` readiness (101/101). Its filtered native GameTest run selected 27 of 850 fixtures; 26 passed and one failed before pickup because the normal target projection was null. The overall r3 result is **NEEDS_CHANGES**, not technically ready. These are r3 results; they do not revise r1 or r2's historical counts or failure details.

As a precondition correction for r4, `BodyItemTargetProbe` requires a loaded one-chunk halo, while `AirAlarmPowerFixture` currently uses a padding-0 footprint. A test-only `FixtureChunkTickets` halo correction is being implemented. This explains a fixture precondition mismatch but is not yet confirmed as the sole root cause; only r4 runtime results can establish whether it resolves the failure. No production fix or r4 pass is claimed.

## Candidate r4 — gate result

The r4 gate passed build, focused JVM tests (28/28), and filtered interaction/prediction readiness (101/101). Native GameTests selected 27 of 850; 26 passed and one failed with null normal item projection. The halo correction did not resolve the failure and is not established as its root cause. r4 remains **NEEDS_CHANGES**.

## r5 preparation — pre-submission review

The current r5 source changes compile and independently passed source review: explicit body/item validity checks, ray bounds, and missing-chunk/first-collider diagnostics; actor `readyAndQuery` admission now occurs after power readiness, immediately before positioning/interactions, rather than retaining an unticketed login-spawn body during the power wait. Diagnostics and the normal authority/probe/pickup path remain. The exact earlier failure cause is still unverified; central r5 validation is pending. Do not treat these changes as a confirmed fix or claim native results until r5 is gated.

## Candidates r5–r6 — gate results and confirmed fixture obstruction

Both central gates passed build, focused JVM tests (28/28), and filtered interaction/prediction readiness (101/101). Each native run selected 27 of 850 fixtures; 26 passed and one failed with null normal item projection. r5 and r6 remain **NEEDS_CHANGES**.

The r6 diagnostic establishes the immediate cause: first collider is a `minecraft:barrier` at local `(5,14,6)`, inside the owned template, distance `0.005` from body eye `(5.5,14.53,6.5)`. `power_receiver_tall_clear` is only 16×13×16, so the eye is above its cleared volume and intersects the GameTest roof. The probe correctly denies projection; this is a fixture-roof obstruction, not a production pickup bug. Earlier halo and actor-admission changes were precondition improvements but did not remove this obstruction.

## r7 preparation — fixture encoding and review

A dedicated `botany_extractor_power_clear.nbt` is now encoded as 16×20×16 with 5,120 explicit air cells and DataVersion 3465; the original template is unchanged. An initial malformed-NBT EOF was repaired. `compileGametestJava` passed, and focused `GameTestStructureTemplateEncodingTest` XML reports 1 test, 0 failures, 0 errors, 0 skipped (2026-10-14T10:18:09.811Z). Independent fixture source/encoding review passed.

The r6-proven failure was the old 13-high template's barrier roof inside the actor ray, above the cleared volume; the dedicated height-20 fixture includes that ray.

## Candidate r7 — foundation READY_FOR_OWNER

The central gate for `botany-genetics-foundation-20261014-r7` passed build (bound jar SHA-256 `d0ee68d374f910e8db0ebc3422cbaa1b9eccdba0b7b2e050284506891ecb4afe`), focused JVM tests 29/29, filtered `interaction,prediction` readiness 101/101, and filtered native GameTests 27/27 selected of 850 under `botany,botany-genetics`. The 16×20×16 dedicated fixture clears the roof-barrier ray obstruction. Native coverage verifies persistent and registered item-component network codecs plus authenticated altered-genetics harvest/extract/replant and actual 1000 W powered extractor behavior. Foundation status: **READY_FOR_OWNER** (historical r7 snapshot), not owner acceptance.

This gate is focused: it does not establish full-suite or GPU/visual validation, connected nonbaseline-gene end-to-end coverage, complete catalog/mutation/pollen coverage, or owner acceptance. Current worktree changes after r7 are not covered by this verdict. Practical manual actions: run `/ms14 botany_fixture`, then its status check for 1000 W extractor/750 W grinder allocation; perform normal plant→harvest→extract→replant interactions. The request for 65 missing plant families was submitted; no new delivery is claimed here.
