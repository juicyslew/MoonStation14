# Tag-selected GameTests

Fixtures are in `src/gametest/java` and excluded from the production jar. Put `@GameTestTags` on a fixture class and/or method; annotations are unioned and selecting multiple tags uses OR semantics. Tags are case-insensitive plain values (letters, digits, `_` and `-`; first character must be a letter). Primary current selectors include `hands`, `storage`, `chat`, `ventilation`, `atmosphere`, `power`, and `air-alarm`. Other selectors include `character`, `chemistry`, `damage`, `explosion`, `interaction`, `lifecycle`, `movement`, `physiology`, `prototype`, `status`, and `slip`. `loose-objects` selects loose-item physics, lifecycle, throwing, and pulling fixtures; cross-domain loose-object interactions also retain their applicable domain tags. Treat the preflight's printed catalogue as authoritative as tags change.

`timed-interaction` selects the live timed-ingestion and generic timed-operation
fixtures; `ingestion` selects the held, loose-container, puddle and interruption
fixtures. These retain their applicable hands, chemistry and damage tags.

Disposal intake coverage is also tagged `atmosphere` because it exercises finite
room ownership and live gas transfer. Avoid assigning `hands` to intake tests
that only create a transit holder; body-hand admission coverage is tagged
separately. For mixed movement fixtures, keep disposal/containment tags on the
methods that actually exercise those paths rather than the whole fixture class.

```powershell
./gradlew runGameTestServer
./gradlew runGameTestServer "-Pms14GameTestTags=chat" -Pms14GameTestDir=build/gametest-tags-fixture
./gradlew runGameTestServer "-Pms14GameTestTags=power,ventilation" -Pms14GameTestDir=build/gametest-tags-isolated-union
```

For example, `@GameTestTags({"power", "ventilation"})` selects a fixture for either tag; multiple requested tags also use OR semantics. Use a fresh directory for clean comparisons; failed worlds are retained under `build/gametest-tags-fixture` when useful.

For isolated fixture checks, use a separate GameTest directory as above: the owner's normal run configuration can enable atmosphere and break fixture assumptions. Do not overwrite the owner's configuration. Preserve failed-world artifacts when useful. Tags select related tests, not fixture isolation or execution order. Blank, malformed, unknown, and zero-match filters fail before Minecraft starts rather than falling back to a full run. Preflight reports selected/total methods and the available catalogue; default registration still includes untagged `@GameTest` methods.

Tags select ordinary `@GameTest` methods. `@BeforeBatch` and `@AfterBatch` callbacks are registered only for batches containing selected tests, so filtered runs retain their required setup and teardown without activating unrelated batches. Generators are not tag-integrated. Report whether validation was filtered or full, including selected/total and pass/failure counts. Tags are not a substitute for a full run on broad changes.

Recent verification logs: `build/gametest-tags-isolated-chat.log`, `build/gametest-tags-isolated-union.log`, and `build/gametest-tags-isolated-full.log`. Chat: 14/479 passed. The power/ventilation union selected 86/479 and had five fixture assertion failures. Full: 479/479 completed with three failures: `localExtensionReceiverRespectsRadiusAndCableCuts`, `alarmUsesInheritedHundredWattsAndRadiusThree`, and `disabledDeviceDoesNotMoveGas`. These records do not claim that the union or all fixtures are green. The canonical entry wrapper is `LiveHandTransferGameTestEntry`; the unused helper-level `@GameTest` was removed to avoid registering a duplicate/nonexistent prefixed template.
