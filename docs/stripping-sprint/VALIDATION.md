# Stripping validation

Owner closed the sprint on 2026-10-09. `HANDOFF.md` records the current state;
the sections below retain chronological evidence. Closure does not imply an
assistant-run graphical/connected-client check or a green full native suite.

## Earlier implementation validation (2026-10-08; pre-owner-fix)

The earlier compile/test build and filtered native run are recorded above only as
historical results; they do not describe the current package. The earlier run
passed 2,961 unit tests and its selected stripping/equipment fixtures. Its older
distributable had SHA-256
`a72356772732d8d8be784750202e02ca54d2e333c61a4bde1a33f8c465e55959`.

The hidden-slot artwork is an unchanged imported SS14 PNG; its source and packaged
attribution sidecar are described in `ASSETS.md`. Existing authored wearable
exports were not edited. No new audio was added.

## Latest owner-repair validation (2026-10-09)

The ordinary-villager join path initializes resolved LIVE_HANDS. Explicit repeat
initialization preserves the exact existing compatible snapshot; incompatible and
materialized-null states remain rejected. Ginormous Utility Case uses the existing
shared `ItemSize` classification rather than `Small`, without changing internal
storage capacity. F5 mixed-auth/fallback handling borrows a fresh per-garment
consumer and invalidates the production buffer on regression. Source authority was
not bypassed. Existing authored exports and first-person mesh/assets were unchanged;
no asset regeneration occurred.

`build/validation-logs/stripping-owner-bugs-build.log` reports `BUILD SUCCESSFUL`.
JUnit XML totals 2,963 unit tests, with 0 failures/errors/skips.

Focused tags
`stripping,equipment,equipment-transfer,equipment-quick-equip,equipment-regression,
equipment-drag-world,storage,timed-interaction,medical-treatment,hands,prototype,
lifecycle` selected **298/728**; all 298 completed and passed, with 0
failed/incomplete. Log:
`build/validation-logs/stripping-owner-bugs-focused-gametest.log`. All
stripping/equipment fixtures and selected hands/lifecycle fixtures passed. The
passed focused fixture directory `build/gametest-stripping-owner-bugs-fixture` was
absent at documentation time. Full-run fixture artifacts are retained at
`build/gametest-stripping-owner-bugs-full-fixture`.

The full native run completed **728/728**, 724 passed, 4 failed, 0 incomplete.
Distinct failures were:

- `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` — >20 kW delivery
  trip was not latched.
- `logpulsedisableslampandrestoresatresolveddeadline` — log pulse did not restore
  at resolved expiry.
- `liveapcmeterdrivesscheduledtwophasedoorandautoclose` — power-unknown auto-close
  deadline remained disarmed.
- `obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor` — doorway close
  phase assertion for actor above the two-cell doorway.

These failures are outside changed character/equipment paths; matching names do
not establish inheritance or causation. Full log:
`build/validation-logs/stripping-owner-bugs-full-gametest.log`. Full suite is not
green; no green full-suite claim is made.

The current owner-repair package is `build/libs/moonstation14-0.0.1.jar`, SHA-256
`286DB3FA103701408122F8E6827A231318C9C8A7956F748DD14B6B8FE292F0E2` (parent
verified). Connected E/F5 acceptance remains manual; no GPU client was launched
and no connected E/F5 retest was performed against the matching jar. Verify E
opens stripping on a normally spawned villager, Ginormous Utility Case is denied
in pockets, and clothing still renders in F5. Native tests do not prove connected
packet acceptance or rendered pixels.

## ID preference and range-close validation (2026-10-09)

Latest serial command `./gradlew --no-parallel compileJava compileTestJava
compileGameTestJava test jar` passed; JUnit XML totals 2,967 unit tests with
0 failures/errors/skips. Log:
`build/validation-logs/stripping-id-close-build.log`.
The resulting JAR SHA-256 is
`F7B603640E7B0D48E4C19AEFA1CB575B56C83E1A6F49C4BD3928CBD8B538AE18`.

Focused tags `stripping,equipment,equipment-transfer,equipment-quick-equip,
equipment-regression,equipment-drag-world,storage,timed-interaction,
medical-treatment,hands,prototype,lifecycle` selected **299** tests; 299 passed,
0 failed/incomplete. Log: `build/validation-logs/stripping-id-close-focused-gametest.log`.
The full unfiltered run selected/completed **729/729**: 726 passed, 3 failed,
0 incomplete. The failures were `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`logpulsedisableslampandrestoresatresolveddeadline`, and
`liveapcmeterdrivesscheduledtwophasedoorandautoclose`, all power/airlock behavior
outside the changed character/equipment paths. Log and fixture artifacts:
`build/validation-logs/stripping-id-close-full-gametest.log` and
`build/gametest-stripping-id-close-full-fixture`. Full suite is not green.

The scoped fixes add the resolved blank-ID quick-equip preference (while retaining
manual pocket fit), strict canonical zero fields for MOVE_EQUIPMENT, and server
closure/pruning of stripping views outside the shared interaction range. Matching
focused native fixtures passed. At this intermediate checkpoint, increased strip
reach/speed calibration had not yet been approved or implemented; the range was
still 1.5 blocks. The approved calibration below supersedes that baseline.
No client was launched; connected drag/E and visible closure acceptance remains
manual, with range and target lifecycle fixtures supplying the headless checks.

## Approved grounded-speed and interaction-reach calibration (2026-10-09)

`CharacterMovementPolicy.fromCharacterData` applies the shared 1.25 grounded
walk/sprint scale once to resolved values; raw human prototype 2.5/4.5 remains
unchanged and effective live values are 3.125/5.625. The production shared
`GroundedHarnessWorldStep` GameTest covers the resolved raw values and observed
human walk and sprint caps; Pig coverage was updated for the same common grounded
conversion. Acceleration, friction, gravity, crawl/pull/status factors, ghost
flying, vanilla Creative flight and legacy experimental behavior were not changed.

Shared `InteractionReach` uses ordinary SS14 base reach plus the approved flat
Minecraft +1.5-block adaptation: 3.0 blocks for strip open/actions/progress/view
closure and body-to-body medical treatment/syringe access. These consumers share
the same reach/physical-eye loaded-LOS helper; medical analyzer threshold naming
and its existing 3.5 scan / 3.0 UI limits remain untouched. No global cap changes:
World-target aiming, block-use/APC/examine/pickup retain their separate local
limits; none are extended by blindly adding the offset. Geometry, station blocks,
and prototype source values are unchanged.

Serial `./gradlew --no-parallel compileJava compileTestJava compileGameTestJava
test jar` succeeded; JUnit: 2,968 tests, 0 failures/errors/skips. Log:
`build/validation-logs/stripping-calibration-build.log`. Focused tags including
movement and all stripping/equipment/medical/syringe scopes selected/completed
**368/368**, 368 passed, 0 failed/incomplete. Production-consumer geometry
fixtures cover stripping admission plus completion, treatment reach, and syringe
reach at 2.5, exactly 3.0, beyond 3.0, and blocked eye-ray cases. Log:
`build/validation-logs/stripping-calibration-focused-gametest.log`; fixture:
`build/gametest-stripping-calibration-fixture` (passing disposable fixture removed
at closure; validation logs retained).

The full native run completed **732/732**: 729 passed, 3 failed, 0 incomplete.
The failures were `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`livesourcesubstationapcbatteryandlampchain`, and
`liveapcmeterdrivesscheduledtwophasedoorandautoclose`; these unrelated
power/cable/airlock fixtures reported an unknown APC output, a lamp not lit despite
known cable paths, and a door deadline not yet reached. Full log:
`build/validation-logs/stripping-calibration-full-gametest.log`; artifact directory:
`build/gametest-stripping-calibration-full-fixture`. Full suite is not green.

Current jar: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`F53B53861F485BD3D3B417D8B05261A8F094E1B4F9F15C955E1261939F7A48F3`.
These native fixtures prove production reach predicates/strip timed behavior, not
the authenticated connected-player treatment/syringe packet effect route; that
positive authority/effect path and connected E/F5 acceptance remain manual and
have not been run against this jar.
