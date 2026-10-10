# Tagged GameTest infrastructure

## STATUS

**Closed for integration — 2026-10-03.** Tag selection, fixture migration, fail-fast validation and agent guidance are delivered. Closure does not claim a green complete server suite: three fixture assertions remain failing in the latest isolated full run. Changes are uncommitted; new support classes, unit tests and documentation are untracked and must be included in integration.

The same working tree also contains 21 reconstructed historical sprint handoffs and `docs/HANDOFF_INDEX.md`. Those are a separate documentation deliverable, not new acceptance of those historical sprints.

## ARCHITECTURE

Fixture-only `@GameTestTags` supports class and method annotations. Effective tags are their union; comma-separated requested tags match **any** listed tag. Tags are case-insensitive plain identifiers, not Minecraft namespaces. With no property, all ordinary project GameTests are registered, including future untagged tests; catalogue coverage requires current tests to have tags.

`-Pms14GameTestTags=power,ventilation` is forwarded only to the GameTest server as `moonstation14.gameTestTags`. A conditional JavaExec preflight reads compiled fixture annotations with ASM, validates the filter and reports selected/total counts before Minecraft starts. The mod-bus registrar discovers project GameTest methods through NeoForge scan metadata and independently validates selection before publishing through `RegisterGameTestsEvent`.

All infrastructure stays in the `gametest` source set, outside the production jar. No gameplay systems, networking or prototype definitions changed.

## IMPORTANT FILES

- `build.gradle`: optional filter, preflight dependency, fixture output for unit coverage and isolated run directory.
- `src/gametest/java/com/juicyslew/moonstation14/gametest/support/`: `GameTestTags`, `GameTestTagSelector`, `GameTestTagRegistrar`, `GameTestTagPreflight`.
- `src/test/java/com/juicyslew/moonstation14/gametest/GameTestTagSelectorTest.java`: parsing, union/selection, registration seam and compiled annotation coverage.
- `src/gametest/java/`: migrated fixture metadata; bodies and existing test attributes preserved.
- `docs/gametest-tags.md`, repository `AGENTS.md`: usage and validation rules.
- `docs/HANDOFF_INDEX.md`: historical reconstruction index and this handoff.

## SS14 CORRESPONDENCE

Testing infrastructure only; no new SS14 gameplay/parity claim. Existing pinned reference remains `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. Registration behavior was verified against NeoForge 21.1.224, not assumed from SS14 or another Minecraft version.

## DECISIONS / WHY

- NeoForge scans `@GameTestHolder` **after** the registration event. Leaving holders would bypass filtering. All fixture holders were replaced with tags and explicit `templateNamespace = "moonstation14"`; existing template-prefix/batch/time-limit behavior is preserved.
- `LiveHandTransferGameTestEntry` is the canonical registered wrapper. Its helper in `ms14/hands/live/LiveHandTransferGameTests` previously had an unused GameTest annotation without a holder. Removing that helper annotation preserves **479 originally registered tests**, rather than registering an extra test with a nonexistent prefixed template.
- Runtime registration exceptions can log rejection while Gradle exits successfully. Preflight guarantees nonzero status for invalid filters without launching Minecraft.
- Raw bytecode coverage avoids NeoForge unit-runtime classloader/mixin linkage problems; it must not silently skip fixtures.
- Tags select tests, not isolation or execution order. Use a fresh isolated directory rather than changing owner configuration.

## KNOWN GAPS

Current support is ordinary `@GameTest` methods, not generators or batch callbacks. Tags require maintenance and cross-system fixtures need appropriate overlapping tags; filtered passes do not replace full regression validation.

Latest isolated full-run failures: `localextensionreceiverrespectsradiusandcablecuts`, `alarmusesinheritedhundredwattsandradiusthree`, `disableddevicedoesnotmovegas`. The union run has five fixture assertion failures; see its log. These were not repaired or established as preexisting by this sprint. Do not report either run as green.

## VALIDATION

- **2,066 unit tests passed**, zero failures/errors/skips.
- Isolated `chat`: **14/479 selected, all 14 passed**.
- Isolated `power,ventilation`: **86/479 selected, five failures**.
- Isolated default: **479/479 selected and completed, three failures**.
- Each runtime catalogue reported zero untagged tests. Unknown/blank filters exited 1 at preflight. Production jar inspection found no GameTest infrastructure. Independent implementation review and `git diff --check` passed.
- Evidence: `build/gametest-tags-isolated-{chat,union,full}.log`; failure world retained at `build/gametest-tags-fixture`. Build evidence is ignored by Git; copy explicitly if needed in another worktree.
- Earlier runs used owner `run/config`, where atmosphere was enabled, and produced isolation-guard failures. Those are superseded by the correctly isolated runs; owner configuration was not overwritten.

## NATIVE SCHEDULER SAFETY FOLLOW-UP

GameTest callbacks dispatched from task-map iteration must not recursively add another task-map entry. Recursive readiness polls and nested callback scheduling use `BoundedGameTestWait.safeDelay` (or `safeAtTickTime` when retaining an absolute test tick); its native sequence bridge defers registration until sequence processing has completed. Calls made in main fixture setup and native-sequence `thenExecute` callbacks remain native `runAfterDelay` calls: those registration points are already safe, and mutating the active sequence collection is not.

The follow-up parsed 287 fixture files with a disposable Javac AST audit, migrated 86 task-phase sites and 27 manually classified callbacks, and corrected two additional indirect recursive power waiters. The final direct-call inventory had zero flagged/manual cases; main/prequeued and sequence-phase registrations stay native. Two scheduling stress fixtures passed (2/730 selected). Full-suite acceptance is recorded in `docs/airlock-vertical-model-mini-sprint/FAILURE-REPAIR.md`, not inferred from the audit or stress tests. Earlier interrupted runs with zero assertion failures were not completed passes.

## NEXT WORK

Integrate `build.gradle`, new support sources, unit test and fixture migration together. For fixtures added by another agent, remove automatic holders, add tags and explicit template namespace, and preserve their existing prefix/batch/test attributes. Include all new untracked files; a tracked-only diff is incomplete.

Run Gradle/server validation sequentially. Example: `./gradlew runGameTestServer "-Pms14GameTestTags=power,ventilation" -Pms14GameTestDir=build/gametest-focused`. Use a fresh directory for clean comparisons. Remaining fixture failures are separate follow-up work, not authorization for unrelated gameplay changes.
