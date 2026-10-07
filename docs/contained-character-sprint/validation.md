# Validation (2026-10-05)

- `./gradlew.bat test`: passed, 2,190 tests. Focused coverage includes resolved unit/holder view defaults, hand-action policy, camera lease transfer/restore, and payload codecs.
- `./gradlew.bat build` and `./gradlew.bat compileGameTestJava`: passed. Artifact: `build/libs/moonstation14-0.0.1.jar` (5,293,562 bytes; 2026-10-05 20:07).
- `./gradlew.bat runGameTestServer -Pms14GameTestDir=build/gametest-disposal-focused-01`: 518 tests ran; only `poweredscrubbercapturesonlyselectedgas` failed (ventilation solve count remained zero). The unit contained-drop, transit-holder drop, and refusal-fallback tests passed. Live unit and holder assertions verified owner identity, target presence/absence, and collision flags against their resolved runtime prototype data.
- After target tests passed, removed only the disposable `build/gametest-disposal-focused-01/world`; logs and configuration were retained.
- No connected-client visual/manual validation was performed. Camera and containment parity notes and deliberate Minecraft adaptations are in `plan.md`; upstream prototype and behavior audit is pinned there to SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
