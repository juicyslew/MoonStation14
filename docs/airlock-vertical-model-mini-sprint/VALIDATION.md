# Validation — 2026-10-10

This record certifies the pre-main-merge slice. Subsequent combined validation and
the current merged artifact are recorded in [MERGE-MAIN.md](MERGE-MAIN.md).

## Build and focused checks

- `test compileGametestJava assemble`: passed, 2,982 JUnit tests, zero failures.
  Log: `build/airlock-vertical-build-final.log`.
- Filtered native run: **9/726 selected, 9 passed, zero failed**, tags
  `airlock-vertical-model,airlock_stationary_contact,airlock_crush_cycle`.
  Log: `build/airlock-vertical-native-focused-final.log`.
  Successful disposable fixture directory removed.
- All 27 imported model/texture assets matched both original source bytes and
  frozen accepted output hashes, zero mismatches. Portable import-manifest tests
  verify hashes, dependency closure, attribution and closed-item bounds.
- Read-only code review passed shared motion, resolved durations, sprite/overlay
  binding and preservation of power/bolt/deny priorities. `git diff --check` passed.

The first focused run passed eight tests and failed the new fixture's premature
endpoint lookup before block-entity load. The fixture now waits for real delivered
power/registration and checks the actual aperture rather than the entire root
volume. No production lifecycle or payment bypass was added. Retained failure
evidence: `build/airlock-vertical-native-focused.log` and
`build/gametest-airlock-vertical-fixture`.

## Artifact

Clean repair artifact: `build/libs/moonstation14-0.0.1.jar`, 7,637,113 bytes.
SHA-256: `E26410D1706A59BF7EC016FAD8AC74E00D5AE0B28E7D949D0A80FFAB6FFF5907`.
Supersedes the original visual-slice and intermediate repair artifacts.
Client and server both need this jar. No staging, commit or push was performed.

## Coverage limits

Native coverage uses the real placed, powered controller and resolved durations,
both tile flags/atmosphere topology, shared visual geometry and upper-target
identity. It is not an authenticated connected-client pixel/animation test.
Owner reports the model works well. Full-suite failure repair passed;
see `FAILURE-REPAIR.md`. Native overlays use
static frame 00; complete blink/panel-transition animation is not implemented.

## Full native suite

Original visual-slice full run: **726/726 selected, 685 passed, 41 failed**; not green.
Log: `build/airlock-vertical-native-full.log`. Retained world/artifacts:
`build/gametest-airlock-vertical-full-fixture`.

In that original run, the new vertical fixture failed before model/timing assertions because its real
airlock never reaches fresh delivered-power readiness (`known=false`,
`delivered=0.0`, `topologyReady=false`), despite passing the filtered run. Do not
weaken the power gate to make this pass.

Compared with the previous 725-test/40-failure run in
`build/device-ground-activation-native-full.log`: 39 failed names overlap.
New failures are `poweredverticaldoormovesandsynchronizesbothtiles` and
`slidingsuperslipperyslipdoesnotspendreactivetouchdose`; the previous
`ownedcriticalbodyfallsonnativeticks` failure is absent. This comparison does not
establish causation or prove the failures unrelated to this work. Full-suite
failure repair was subsequently requested by the owner. Current repair results,
root-cause evidence and remaining acceptance gates are in `FAILURE-REPAIR.md`.

## Failure-repair validation

Latest combined `test compileGametestJava assemble` passed: **2,989 JUnit tests,
zero failures/errors/skips** (`build/full-suite-repair-build-final-round12.log`).
Full native run: **730/730 selected, 730 passed, zero failed**, no tag filter,
no skipped fixtures (`build/full-suite-repair-native-final-round12.log`).
Successful disposable final fixture world removed; earlier failure artifacts retained.

Read-only reviews covered loaded-only lifecycle/power access, loaded-boundary
freshness, deferred FULL discovery, owned ticket cleanup, float-backed gas
conservation, safe native scheduling, current paid contact admission, collision
anchor invariants, isolated atmosphere authority and real native actor observations.
Quiet closed-contact deferral is bounded to one paid-solve interval and retains
fresh payment/actor/bolt gates; it is a Minecraft meter adaptation, not an SS14
cadence claim. The accepted visual assets and owner configuration remain unchanged.
