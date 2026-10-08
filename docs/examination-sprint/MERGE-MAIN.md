# Examination / main integration

Inputs: examination `07a04fc` and incoming main `86145a89f85dce2e91d0718aeb906b7609792a57`.
This is post-closure integration, not a new examination sprint. Owner requested
retaining both branches and checking basic game launch; native GameTest failures
are not an acceptance blocker for this task.

## Retained behavior

- Character composition includes examination's `Examiner` and medical's
  `Damageable`/`HumanoidProfile`, with both typed registry and sealed-interface
  support. Prototype registrations, synchronized families, resource translations
  and classloading checks retain examination, celestial/Jug/Puddle, medical,
  injector, and construction data.
- Human now has 33 authored components; the registry has 36 supported types.
  `CharacterData.MAX_COMPONENTS = 64` is shared by typed construction and JSON
  auditing, preserving duplicate/type checks while allowing the combined profile.
  Regression coverage checks Human's combined composition and the shared bound.
- Jug rendering keeps the resolved Jug-specific capacity path; syringe/bottle
  properties retain stack-aware capacity, null-level defaults, and bounded fill.
- Main's shared pointer workspace, construction browser and medical analyzer
  coexist with examination setup/ticking, Shift-click/cycling, panel controls,
  and scrolling. Existing registry, workspace and hit-test helpers are reused;
  no competing HUD painter or gameplay store was introduced.
- Input routing follows paint priority: wire is above storage/analyzer siblings;
  analyzer bounds participate in the shared interactive-window check. Examination
  cannot steal analyzer scrolling. Consumed global panel wheel events are canceled,
  and ungrabbed world popup clicks retain pin/close routing. Non-world screens keep
  priority; storage-screen handling does not race the global handler.

## Validation

Initial main compilation passed; the first unit run completed 2,886 tests with
45 failures. The principal root was the combined human composition exceeding the
old 32-component schema/constructor limit, not a missing component registration.
Initial logs are retained at `build/validation-logs/examination-main-merge-unit.log`.
The combined prototype export assertion also required its single `Set.of` boundary
to be corrected after resolution.

Independent source review accepted the corrected pointer routes, combined typed
composition and localization (all keys and values from both inputs retained).
The existing need-enrollment bound regression was updated to the shared 64 cap;
host-count and duplicate-component constraints remain unchanged.

Final validation on 2026-10-08:

- `cleanTest test build compileGameTestJava --max-workers=4`: **PASS**, 2,887
  JUnit tests, zero failures or skips. Evidence:
  `build/validation-logs/examination-main-merge-final-unit.log`.
- Normal isolated `runClient` reached the first-run accessibility screen, loaded
  audio/GUI resources and remained stable for 15 seconds. No GUI input was sent;
  the main menu and connected gameplay were not tested. The smoke-owned client
  was stopped intentionally (run-task exit -1); no duplicate client remains.
  Evidence: `examination-main-merge-client-runtime.log`,
  `examination-main-merge-client.log` and the window-only
  `examination-main-merge-client-smoke.png` under `build/validation-logs/`.
- Default `prepareClientRun` was regenerated without isolation. Generated normal
  launch arguments contain no smoke-directory path, QuickPlay or connection options.
  Owner options/worlds/configuration were not altered. Disposable smoke data was
  removed after retaining logs and the cropped screenshot.
- **Full native GameTests:** 708/708 selected/discovered and completed;
  **695 passed, 13 required failures, zero incomplete/optional failures**. Server
  startup succeeded. Failures were not investigated or fixed, as directed; their
  cause/inherited status is not established. Exact names and output are in
  `build/validation-logs/examination-main-merge-full-gametest.log`; failed-world
  evidence remains under `build/gametest-examination-main-merge-fixture`.
  Shutdown also logged oversized diagnostic book pages after all tests completed.
- No unresolved index entries or conflict markers remain; staged diff checks pass.

Final artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`A3905F3FDB692654499CDBB52779E1760AA8FE0C31546379A0850A7198ACFBB3`.
Historical sprint hashes do not identify this combined artifact. Resolution changes
are staged; no merge commit or push was created.
