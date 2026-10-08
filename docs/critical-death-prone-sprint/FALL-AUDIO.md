# Body-fall audio asset mapping

At pinned SS14 revision `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Resources/Prototypes/SoundCollections/body_fall.yml` defines `BodyFall` as the
uniform four-file collection `Resources/Audio/Effects/bodyfall1.ogg` through
`bodyfall4.ogg`. Those four files are copied byte-identically into
`assets/moonstation14/sounds/` and mapped with equal weights in the native
`moonstation14:body_fall` sound event. The Minecraft sound event plays one
controlled audio payload; Minecraft chooses one weighted variant. This is the
supported native sound-event adaptation of the upstream collection, not a new
generic local sound prototype.

Per-file attribution/license could not be verified: no matching entries were
found in `Resources/Audio/Effects/attributions.yml`. See
[`../audio-unknown-provenance.md`](../audio-unknown-provenance.md); do not infer
author or license for these files.

## Runtime validation attempt (2026-10-07)

The unit/build/fixture-compilation chain passed: **2,596 JUnit tests, 0 failures,
errors, or skips**; `compileGameTestJava` and `build` succeeded. The inspected JAR
`build/libs/moonstation14-0.0.1.jar` is 6,243,926 bytes. Log:
`build/validation-logs/body-fall-audio-unit.log`.

Focused native tags `standing,audio` selected **3 tests**: 2 passed and
`forceddownemitsresolvedsoundonceandmedicalrepeatisquiet` failed at the assertion
that a medically forced-down pig emits its resolved sound. Log and retained
fixture: `build/validation-logs/body-fall-audio-focused.log` and
`build/gametest-body-fall-audio-fixture`. This is unresolved; no full native run
was performed after focused failure. Fixture compilation corrections used the
existing `moonstation14:pig` ID, and the custom sound fixture clears inherited
host types to avoid duplicate `minecraft:player` ownership.

### Final-source rerun (2026-10-07)

- Final `test build compileGameTestJava`: **2,596 tests; 0 failures, errors, or
  skips**; build and GameTest compilation passed. JAR
  `build/libs/moonstation14-0.0.1.jar`: **6,243,926 bytes**. Log:
  `build/validation-logs/body-fall-audio-final-unit.log`.
- Focused tags `posture-fall-audio,audio`: **3 selected; 2 passed, 1 failed**.
  `forceddownemitsresolvedsoundonceandmedicalrepeatisquiet` failed at the initial
  human forced-down sound assertion. Log and retained fixture:
  `build/validation-logs/body-fall-audio-final-focused.log` and
  `build/gametest-body-fall-audio-final-fixture`.
- No full native rerun followed this focused failure. The reported passing
  repro-fixture run is not reproduced by this final union run; behavior/cause is
  unresolved. Connected client hearing remains unverified.

### Completed final-source validation (2026-10-07)

Final audio admission now uses authoritative entity membership in the live level
rather than `tickCount`, so an active entity with native tick zero can produce a
real forced-down sound while an unregistered startup entity remains silent. The
fixture checks live Human/Pig preconditions, startup and restored quiet paths, and
the inherited custom Sheep sound override. Earlier failed focus runs exposed
fixture lifecycle/timing assumptions; they are retained above as history, not
treated as current failures.

- `test build compileGameTestJava`: **2,596 tests; 0 failures, errors, or skips**;
  build and GameTest compilation passed. Final JAR
  `build/libs/moonstation14-0.0.1.jar`: **6,243,954 bytes**. Log:
  `build/validation-logs/body-fall-audio-complete-unit.log`.
- Focused union `posture-fall-audio,audio`: **3/661 selected; 3 passed**.
  Log: `build/validation-logs/body-fall-audio-complete-focused.log`.
- Unfiltered native suite: **661 selected; 649 passed, 12 failed**. Failures:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `fixturebuildsinitializespowersandflusheswholestack`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `cancelledattemptlatchesuntilvillagerleavespuddle`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `alarmusesinheritedhundredwattsandradiusthree`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `loosejugfollowsnativetickunderelasticpull`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  and `onlycompletedpaidsolvemovesconservedgas`. These are observed failures;
  this validation does not infer whether they are inherited or source-caused.
  Log and retained failed artifacts: `build/validation-logs/body-fall-audio-complete-full.log`,
  `build/gametest-body-fall-audio-complete-fixture`.

The full run is not green. Neither focused nor full native fixtures establish
connected-client hearing or remote recipient delivery. Four imported body-fall
files retain unknown attribution/license status as recorded above.
