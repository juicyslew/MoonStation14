# M0 stationary fixture baseline

## Environment

- Worktree branch `agent/03`, baseline HEAD `5c09e426d055d18932e1896b865912d2716a8278`.
- Target verified from current repository configuration: Minecraft 1.21.1 / NeoForge 21.1.224; Java 21.
- Sable was not installed or enabled for the dependency-free baseline. This does not assert a lack of owner authorization for subsequent work; Sable's project-specific licensing clearance remains unresolved per `licensing-review.md`.

## Current baseline

- `./gradlew test compileGametestJava assemble` — passed; accepted current JUnit count is 3,158 tests, 0 failures, 0 errors, 0 skipped; build log: `build/shuttle-baseline-build.log`.
- Unfiltered `./gradlew runGameTestServer -Pms14GameTestDir=build/gametest-shuttle-fixture` — 783 selected / 783 total, 783 passed, 0 failed, 0 incomplete; log: `build/shuttle-baseline-native.log` (lead verified native total against result XML).
- No baseline GameTest failures/incompletes occurred. Logs do contain expected error-level diagnostics from negative/legacy-state fixtures (`InvalidReagentGameTests`, `StatusEffectGameTests`, `StomachGameTests`, puddle legacy-capacity checks, and a bounded neighbor-update warning); these passed their assertions. The isolated server also logs missing `server.properties` on startup, then starts normally. This is recorded rather than conflated with test-result failures.

## Existing fixture reuse verified

- `DisposalFixtureCommands.build` authors a bounded steel enclosure, finite initialized gas and actual HV/MV/APC network with finite stored energy. Its 259-node disposal tour is intentionally not a suitable shuttle-cabin dependency.
- `AirAlarmPowerFixture.place` constructs a smaller actual APC/cable/powered-device fixture; `PowerFixtureReadiness.await` and `AtmospherePrecisionBudget` are existing readiness and accounting helpers.
- Existing real-body tests use `BodyHandBootstrap.ensure`; existing character/corpse and filled-storage patterns are in the body-control, corpse-physics, and hands fixtures. `FixtureChunkTickets` and `BoundedGameTestWait` are the established loaded-chunk/wait seams.

This baseline establishes only a clean pre-edit comparison. It does not claim Sable compatibility, ship integration, movement, seamlessness, or performance.

## Stationary authored cabin fixture

`ShuttleCabinFixture` now provides a bounded server-side authoring routine for a real steel hull, glass window, two-cell resolved airlock, separated finite non-default gas room, APC/cable-fed live alarm machine, and an actual pipe-gas lane with stable UUID/species/temperature. Its APC output routes over the ceiling to a real extension cable within the airlock's inherited reach; tests check paid 100 W machine and 20 W hatch meters. It requires an already-loaded, in-bounds, empty footprint and performs no chunk loading or ticketing. Tagged `shuttle` GameTests use a registered `LooseItemEntity` and owner-ledger token for a settled large beaker with a valid 12.5-unit water mixture (`ReagentUnits.fromFloat(12.5f)`, 1,250 cents). The test resolves capacity through the existing typed `IReagentTrait` item path and asserts the fill is within that capacity (currently the large-beaker prototype resolves to 100 units); it then checks exact entity/token/stack/content persistence, room gas, pipe identity/state, bound human/Mind/hands, and damage-produced corpse. They intentionally make no flight or seamlessness claim.

Reused existing owners/helpers: `PowerFixtureReadiness`, `FixtureChunkTickets`, `BoundedGameTestWait`, `AtmosphereService` trusted finite-room proof, `LoadedPipeGasOwner` lane admission/snapshot, `CableStorage`, `PowerDeviceFixturePlacement`, `PowerRuntime` meter observations, `LooseItemEntity.create`/`settled`, `LevelItemLocationLedgers`, and existing body/lung/hands/damage binding paths. The APC/cable topology follows existing `AirAlarmPowerFixture` and airlock live-power fixtures, while this standalone builder authors it at cabin coordinates. The small trusted-fixture gas initializer is added to `LoadedPipeGasOwner` so the fixture does not mutate SavedData or copy pipe ownership logic.

## Corrected stationary fixture validation

- The integration attempt found the cargo's initial position was captured while the actual loose item was still settling. The assertion now captures its position and contents only after native settling and paid-power readiness, then verifies stability across subsequent native ticks; initial gravity displacement is accepted.
- A later in-template-bounds guard found local origin `(0,8,0)` was outside both the cabin footprint and the 16x10x16 `empty` template. Both stationary fixtures now use `empty` at local `(2,1,2)` and verify all cabin corners before acquiring footprint tickets.
- The filled large beaker uses `ReagentUnits.fromFloat(12.5f)` (1,250 cents), not raw `12,500` cents. The fixture resolves the large-beaker capacity through its existing `IReagentTrait` and asserts the actual fill does not exceed it.
- `./gradlew test compileGametestJava assemble` — passed; JUnit XML: 3,158 tests, 0 failures, 0 errors, 0 skipped. Current log: `build/shuttle-final-fixture-build.log`.
- Filtered `./gradlew runGameTestServer "-Pms14GameTestTags=shuttle-stationary" -Pms14GameTestDir=build/gametest-shuttle-final-stationary-bounds-fixture` — 2 selected / 791 discovered, 2 passed, 0 failed, 0 incomplete. Log: `build/shuttle-final-stationary-native.log`.
- The Sable-enabled startup still emits nonfatal `Unknown block: create:flywheel` physics-tag diagnostics. Tests use no Sable API and do not establish flight, plot, or engine compatibility.

## Superseded Sable-enabled full-suite evidence

- Earlier Sable full-suite run: 789 selected, 786 passed, 3 failed; retained historical log `build/shuttle-sable-full-native.log`. Those failures were subsequently addressed in current fixture/source revisions; this run is not the current certification result.

## Accepted M0 Sable-enabled validation

- `./gradlew test compileGametestJava assemble` — passed; 3,158 tests, 0 failures, 0 errors, 0 skipped. Log: `build/shuttle-final-fixture-build.log`.
- Filtered `shuttle` selector — 5/791 selected, 5 passed, 0 failed/incomplete. Log: `build/shuttle-final-aggregate-native.log`.
- Unfiltered `./gradlew runGameTestServer -Pms14GameTestDir=build/gametest-shuttle-final-full-fixture` — 791 selected / 791 total, 791 passed, 0 failed, 0 incomplete. Log: `build/shuttle-final-full-native.log`.
- The stock Sable runtime emits nonfatal `Unknown block: create:flywheel` physics-tag diagnostics at startup; Create is intentionally absent. The test suite claims no far-coordinate flight, client continuity, or seamlessness behavior beyond the explicitly tested operating envelope.

## Later acceptance gates

Reviewed M1 translation/diagnostic/disposable-lifecycle snapshot: 3,158 unit tests
and full 796/796 GameTests pass; evidence is indexed in `EXECUTION.md` (lifecycle7
logs). This adds real Human/corpse translation and safe fixture owner retirement,
not connected prediction, frame transitions, cargo carry or M2 persistence.
Loaded-query work follows that snapshot and is currently under correction and
validation; do not use either earlier green count to certify its unfinished edits.
