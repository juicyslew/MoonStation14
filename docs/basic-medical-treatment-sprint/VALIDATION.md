# Medical sprint validation

Post-closure integration with main is recorded separately in `MERGE-MAIN.md`.
The sprint results below describe the pre-integration medical tree.

## Sprint closure

Owner accepted and closed the sprint on 2026-10-07. `HANDOFF.md` indexes current
state and final evidence. Historical pending-acceptance statements and failed
attempts below are retained, not active sprint blockers. Final build: 2,596 unit
tests passed; final focused fall/audio: 3/661 selected and passed; final full
native: 649/661 passed, 12 unclassified failures. Owner closure is manual
acceptance, not automated proof of connected request/transport/render/hearing
paths or a clean full suite. No commit/push was requested.

Closure cleanup removed disposable/resolved-failure fall-audio repro worlds;
their logs remain. The final failed full-suite world at
`build/gametest-body-fall-audio-complete-fixture` is retained. Historical fixture
paths below are not a guarantee that earlier disposable worlds still exist.

## Acceptance boundaries

Independent source review covers treatment authority/source checks, resolved
container compatibility, self/other delay, consumption/repeats, scanner physical
roots, paused cached readings, wire damage groups and persistent window closure.
No remaining concrete defect was identified after the reviewed corrections.
Asset review confirmed supply PNGs against the pinned checkout and all five
sound registrations against present files; the four treatment sounds retain the
explicit unknown-provenance record. The analyzer icon, unknown-state art and
renamed out-of-range image were confirmed byte-identical to their exact pinned
sources; dimensions and SHA-256 hashes are documented in `ASSETS.md`.

Real authenticated-player treatment/scanner completion remains unverified.
GameTest FakePlayers are intentionally rejected by production hand authority.
A proposed plain-player fixture could not establish the connected lifecycle's
initialized durable context and normal Begin/Ready handshake. Admission was not
weakened. Perform the connected checks in `MANUAL.md` before owner acceptance.

## Full native run

Unfiltered: **638/638 selected; 625 passed, 13 failed**. This run predates the
final scanner transfer-root correction. No claim of final full-suite green.
Evidence: `build/validation-logs/basic-medical-full-gametest.log`.

Failures:
- Medical catalog lookup: `resolvedMedicalPolicyFeedsTypedDamageConsumer`,
  `typedTreatmentDeltaDoesNotReviveDefaultDeadBody` (fixed resource placement;
  final focused medical run passes).
- Pulling: `pullsDeadHumanTargetAndReleasesWhenSourceBecomesCritical`,
  `jumpingTautPullerFallsAndLandsAfterInputStops`,
  `looseJugFollowsNativeTickUnderElasticPull`.
- Ingestion: `puddleConsumerWaitsForResolvedProfileDeadlineAndRepeats`.
- Deferred power/ventilation: `builtFixtureInitializesAndReportsResolvedRuntime`,
  `fixtureBuildsInitializesPowersAndFlushesWholeStack`,
  `connectedCablePathLampLoadTripsApcAndManualRecloseRestoresIt`,
  `poweredVentMeterTracksBreakerAndReplacement`,
  `poweredScrubberCapturesOnlySelectedGas`, `disabledDeviceDoesNotMoveGas`,
  `onlyCompletedPaidSolveMovesConservedGas`.

Loose-jug travel and puddle repeat have prior recorded failures. That does not
establish the cause of this run's other failures. Focused results must be reported
separately; tag selections share services and are not isolation guarantees.

## Final-source validation

- Full lightweight chain: **2,542 unit tests; zero failures, errors or skips**.
  GameTest compilation, server/client preparation and build succeeded.
  Artifact: `build/libs/moonstation14-0.0.1.jar` (6,089,492 bytes).
- Medical treatment/analyzer: **4/638 selected; 4 passed, 0 failed**.
- Ingestion: **14/638 selected; 14 passed, 0 failed**. The full-run puddle
  assertion did not reproduce in this selection; this does not prove its cause.
- Pulling: **8/638 selected; 5 passed, 3 failed**:
  `pullsDeadHumanTargetAndReleasesWhenSourceBecomesCritical`,
  `jumpingTautPullerFallsAndLandsAfterInputStops`,
  `detachedDeadBodyFallsAndSettlesOnNativeTick`.
  Loose-jug travel did not fail in this selection. These outcomes do not establish
  that the three remaining failures are inherited or caused by medical changes.

Evidence lives under `build/validation-logs/basic-medical-*-gametest.log` and
`build/validation-logs/basic-medical-unit.log`. Failed native worlds were retained;
successful disposable ingestion artifacts were removed. No second full native
run was performed after the final corrections.

Medical catalog resources now use the actual family-loader directory. Creative
medical entries invoke the resolved stack factory (including pre-sync bundled
client bootstrap), not native one-count creation or hardcoded ten-count stacks.
The live creation dispatch and resolved counts have focused unit coverage.

Connected medical acceptance and the three pulling failures remain explicit
validation limitations; this document does not declare the sprint accepted.

## Syringe / animation extension

Owner-requested extension is implemented and independently source-reviewed.
The analyzer uses its continuously animated, layered SS14 sprite;
default in-hand use opens its UI without toggling off a linked scan. Basic syringe
composition, draw/inject modes, dose selection, blood/container transfers and
immediate reaction admission are covered by `SYRINGE-PARITY.md`.

Final-source evidence:
- Build passed; **2,569 tests in 455 suites**, zero failures/errors/skips. Counts
  were independently summed from JUnit XML. GameTest Java compiled.
- Final focused native: **8/642 selected; all 8 passed**, tags
  `medical-treatment,syringe,health-analyzer`. This run followed the final body
  full/empty auto-switch wiring. Its successful disposable world was removed;
  its log remains `build/validation-logs/medical-syringe-focused-gametest.log`.
- Full native: **642/642 selected; 630 passed, 12 failed**. This run preceded
  the final body auto-switch wiring and was not repeated. Failure artifacts and
  `build/validation-logs/medical-syringe-full-gametest.log` were retained.
- Final artifact inspected: `build/libs/moonstation14-0.0.1.jar`, 6,159,174 bytes.
  Unit log: `build/validation-logs/medical-syringe-unit.log`.

The full extension run's failures were the seven power/ventilation assertions
listed above, `pullsDeadHumanTargetAndReleasesWhenSourceBecomesCritical`,
`jumpingTautPullerFallsAndLandsAfterInputStops`,
`looseJugFollowsNativeTickUnderElasticPull`,
`containedDeadTransitHolderBodyRemainsPinnedAndResumesCorpsePhysicsOnExit`,
and `farPlayerCannotDespawnBoundUnboundOrCorpse`. The despawn assertion again
identified the `soap-slip` FakePlayer as the nearest player. Power/ventilation
and that interference remain owner-deferred; the other failures are unresolved,
not automatically classified as inherited or caused by this extension.

Connected checks are in `SYRINGE-MANUAL.md`; real authenticated-player medical
completion remains unverified. No production authority exceptions were added.
Native target/store fixtures and pure transfer tests do not substitute for that
connected evidence. The sprint is not declared owner-accepted here.

## Correction validation attempts (2026-10-07)

- First correction attempt: **2,580 unit tests; 2 failures**, both in
  `BodyHandStatePolicyTest` malformed/incomplete packet fixtures (the decoder
  throws `ArrayIndexOutOfBoundsException` where tests require
  `IllegalArgumentException`). `compileGametestJava` completed; `build` is blocked
  by these unit failures. Log: `build/validation-logs/medical-corrections-unit.log`.
- First focused correction attempt: **183/646 selected; 172 passed, 11 failed**;
  log and retained fixture are
  `build/validation-logs/medical-corrections-focused-gametest.log` and
  `build/gametest-medical-corrections-fixture`.
- Final-source lightweight chain: **2,580 tests; zero failures/errors/skips**;
  `compileGametestJava` and `build` succeeded. Counts independently summed from
  JUnit XML. Log: `build/validation-logs/medical-corrections-final-unit.log`.
- Final focused tags `medical-treatment,health-analyzer,syringe,creative-menu,hands,storage`:
  **183/646 selected; 181 passed, 2 failed**:
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical` and
  `creativedropreplacesmoditemsandleavesvanilladropsalone`. Log and failed fixture:
  `build/validation-logs/medical-corrections-final-focused-gametest.log` and
  `build/gametest-medical-corrections-fixture`.
- Diagnostic `medical-corrections-regression` filter: **2/646 selected; 2 passed**:
  `containedrefusaldropsatownerposition` and
  `blastknockslivingtargetsoutwardandskipsineligibletargets`. The slip failure was
  not included in the filter. This narrows reproduction only and does not prove
  cause. Log: `build/validation-logs/medical-corrections-causal-gametest.log`.
- Final unfiltered native: **646/646 selected; 634 passed, 12 failed**:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `containedrefusaldropsatownerposition`,
  `slidingsuperslipperslipdoesnotspendreactivetouchdose`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `detacheddeadbodymovesfromliveactorsnativepull`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  `onlycompletedpaidsolvemovesconservedgas`. Preserve diagnostics/worlds in
  `build/validation-logs/medical-corrections-final-full-gametest.log` and the
  designated fixture. Power/pulling failures remain deferred/unresolved; the
  others are not attributed without evidence.
- Corrected Z in-hand input to use the existing held-key first-press guard while
  preserving Shift/Alt routing; restricted detached analyzer portraits to the
  harness entity whose appearance snapshot is saved/restored. The candidate-state
  validation regression is covered by corrected candidate validation and the new
  legacy-admission fixture. No real
  authenticated-player completion or client visual acceptance was established;
  both remain gaps.

## Latest-source validation after analyzer close fix (2026-10-07)

This source boundary includes `BodyHandClient`/pointer-policy analyzer-last-close
behavior and tests, candidate-state equipment/grid validation, legacy admission
coverage, packet truncation handling, and the grenade-beep revision correction.
The initial full correction run recorded above had 22 failures; the subsequent
candidate-validation run had 12. These historical counts remain unchanged.

- Fresh full unit/compile/build chain: **2,581 tests, 0 failures/errors/skips**;
  GameTest Java compiled and build succeeded. JUnit XML was summed. Fresh artifact
  `build/libs/moonstation14-0.0.1.jar`: 6,180,168 bytes, timestamp
  `2026-10-07T16:14:43.7416727-07:00`. Log:
  `build/validation-logs/medical-corrections-accepted-source-unit.log`.
- Focused medical tags `medical-treatment,health-analyzer,syringe,creative-menu`:
  **14/646 selected; 14 passed**. Log:
  `build/validation-logs/medical-corrections-accepted-source-medical-gametest.log`.
- Medical/hands/storage union: **183/646 selected; 181 passed, 2 failed**:
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical` and
  `creativedropreplacesmoditemsandleavesvanilladropsalone` (assertion:
  `creative-steel admits into Creative inventory and consumes loose entity`).
  Log: `build/validation-logs/medical-corrections-accepted-source-hands-gametest.log`.
- `medical-corrections-regression,medical-creative-drop`: **3/646 selected; 3
  passed**. The 3-method diagnostic tag selected only 2 methods; adding the new
  creative-drop tag selected 3, not the expected 4. The separate existing `slip`
  selector selected **19/646; all 19 passed**, including the slip assertion.
  Logs: `build/validation-logs/medical-corrections-accepted-source-causal-gametest.log`
  and `medical-corrections-accepted-source-slip-gametest.log`.

## Syringe authority correction rerun (2026-10-07)

- Final lightweight chain: **2,593 unit tests, 0 failures/errors/skips**;
  GameTest compilation and `build` passed. Fresh artifact
  `build/libs/moonstation14-0.0.1.jar` (6,199,170 bytes). Log:
  `build/validation-logs/syringe-authority-correction-final-unit.log`.
- Focused tags `syringe,medical-treatment,health-analyzer`: **20/660 selected;
  19 passed, 1 failed**, `syringecontainerselecteddosesdrawandinjectattwoblockreach`.
  The exact failing check is `loadedReachableItem(..., 1.5)` unexpectedly accepting
  the loose jug (`the former non-inflated completion predicate rejects this target
  at 1.5 blocks`). The aimed target was directly probed at 2 blocks; no dose
  expectations were changed. Log:
  `build/validation-logs/syringe-authority-correction-final-focused.log`.
- Full native suite was not rerun after this focused failure. The previous run on
  the preceding fixture source selected 660 and had 14 failures; it does not
  constitute validation of this final fixture source.
- QueryGate predicate tests exercise the production gate but do not establish
  authenticated request-wrapper/transport acceptance. Real connected-player
  retesting remains required.

## Syringe dose-ceiling root correction (2026-10-07)

The transfer planner had a shared explicit-dose ceiling of 500 hundredths, which
prevented valid resolved syringe doses of 10 and 15 units from being admitted.
The explicit-dose path now relies on checked reagent units and source/free capacity;
the default three-argument path retains its 500-cent limit. No authority bypass
was added.

- Fresh `test build compileGameTestJava`: **2,594 tests; 0 failures/errors/skips**.
  Build passed; artifact `build/libs/moonstation14-0.0.1.jar` (6,199,162 bytes).
  Log: `build/validation-logs/syringe-authority-correction-rootfix-unit.log`.
- Focused tags `syringe,medical-treatment,health-analyzer`: **20/660 selected,
  all passed** including actual 5/10/15-unit draw/inject transfers. Log:
  `build/validation-logs/syringe-authority-correction-rootfix-focused.log`.
- Full native suite: **660 selected; 647 passed, 13 failed**. Failed methods:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `fixturebuildsinitializespowersandflusheswholestack`,
  `containeddeadtransitholderbodyremainspinnedandresumescorpsephysicsonexit`,
  `slidingsuperslipperslipdoesnotspendreactivetouchdose`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `lampextensionreceiverusesnearestknownlvcablewithinthreeblocks`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `farplayercannotdespawnboundunboundorcorpse`,
  `poweredscrubbercapturesonlyselectedgas`,
  `onlycompletedpaidsolvemovesconservedgas`, and
  `disableddevicedoesnotmovegas`. These are reported without attributing cause
  or inheritance. Log and failure artifacts:
  `build/validation-logs/syringe-authority-correction-rootfix-full.log` and
  `build/gametest-syringe-authority-final-fixture`.

The tested native consumer calls are trusted fixture paths, not authenticated
`SyringeContainerService` request-wrapper/transport acceptance. Connected-player
retesting remains required.
- Final unfiltered suite: **646/646 selected; 635 passed, 11 failed**:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `containedrefusaldropsatownerposition`,
  `slidingsuperslipperslipdoesnotspendreactivetouchdose`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`, and
  `onlycompletedpaidsolvemovesconservedgas`. Exact logs/assertions are in
  `build/validation-logs/medical-corrections-accepted-source-full-gametest.log`;
  failed worlds retained under `build/gametest-medical-corrections-fixture`.
  Creative-drop passed in the full run despite its focused hands failure; this is
  context-sensitive and unresolved, not classified as inherited. Contained refusal
  and blast passed in the diagnostic tag run but failed in the full run; no cause
  is inferred. Power/pulling remain deferred/unresolved, and remaining failures
  are unclassified absent evidence.

Real authenticated-player medical completion and client visual acceptance remain
unverified; native tests do not establish connected GUI behavior. No physics or
power failure was changed as part of this validation. The log filename's
`accepted-source` suffix does not indicate owner acceptance of the sprint.

## Medical treatment/audio/portrait/layout follow-up (2026-10-07)

This source boundary adds five registered native medical sound events, a resolved
catalog sound-registration GameTest, a compound treatment-effects consumer test,
detached portrait snapshots for human harness/pig/villager, and the compact
144px-wide shared-window analyzer layout. Existing prior run counts above (including
the earlier 22- and 11-failure full runs) are historical and unchanged.

- Final `test compileGametestJava build`: **2,585 unit tests; 0 failures, errors,
  or skips** (JUnit XML independently summed); GameTest Java compiled and build
  succeeded. Fresh JAR: `build/libs/moonstation14-0.0.1.jar`, 6,188,837 bytes,
  timestamp `2026-10-07T17:26:24.5170622-07:00`. Log:
  `build/validation-logs/medical-followup-unit.log`.
- Focused tags `medical-treatment,health-analyzer,syringe,creative-menu,audio`:
  **19/650 selected; 19 passed**. This includes the native sound-registry check
  and all three human/pig/villager portrait snapshot fixtures. Log:
  `build/validation-logs/medical-followup-focused-gametest.log`.
- Full native suite: **650/650 selected; 639 passed, 11 failed**:
  `builtfixtureinitializesandreportsresolvedruntime` (no delivered power meter),
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` (expected
  solved connected fixture assertion),
  `detacheddeadbodyfallsandsettlesonnativetick`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `loosejugfollowsnativetickunderelasticpull`,
  `farplayercannotdespawnboundunboundorcorpse` (nearest player was `soap-slip`
  FakePlayer), `disableddevicedoesnotmovegas`,
  `poweredscrubbercapturesonlyselectedgas`, and
  `onlycompletedpaidsolvemovesconservedgas`. Power/pulling remain deferred or
  unresolved; the other failures are not attributed without evidence. Full log and
  failed artifacts: `build/validation-logs/medical-followup-full-gametest.log`,
  `build/gametest-medical-followup-fixture`.

The native gauze test bounds treatment behavior to its authoritative typed damage
ledger and blood-bleed state: with blunt 12, slash 8, piercing 15, bloodloss 7,
and bleed rate 10, the consumer leaves blunt/bloodloss unchanged, reduces slash
to 3 and piercing to 5, and sets bleed rate to 0; repeated passive updates leave
bleed at 0. A bleed-only case likewise stops bleed without typed damage changes.
This verifies the modeled consumer semantics, not a diagnosis or root cause for
the owner's bleed report. `/ms14damagecheck` compares bleed amount separately
from typed bloodloss. Fake/native fixtures do not establish connected-human
treatment completion, client UI visuals, or audible sound playback; all remain
explicit acceptance gaps.

## Final UI / controlled-body give validation (2026-10-07)

- `test build compileGameTestJava`: failed in `test` after GameTest Java compiled.
  JUnit XML: **2,590 tests; 4 failures, 0 errors, 0 skips**:
  `HealthAnalyzerWorkspaceTest.scrollBelongsToWindowAndIsClampedToContentBounds`,
  `HealthAnalyzerLayoutTest.fixedSummaryDamageViewportAndProportionalThumbStayIndependent`,
  `HealthAnalyzerLayoutTest.compactGeometryAllocatesAlertsOnlyWhenPresentAndPreservesDamageOrderExtent`,
  `ServerClassloadingTest.serverOwnedProductionSourcesDoNotImportOrReferenceClientClassLiterals`.
  Log: `build/validation-logs/medical-ui-give-unit.log`.
- Focused tags `give,health-analyzer,medical-treatment`: **27/27 selected; 27
  passed**. Log: `build/validation-logs/medical-ui-give-focused.log`.
- Final native run: **659/659 selected; 646 passed, 13 failed**:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `sealedsegmentadmissionisclickedonlyandquarantinesreplacement`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `cancelledattemptlatchesuntilvillagerleavespuddle`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `leasesonlyexplicitlyconfiguredhumanvillagers`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `loosejugfollowsnativetickunderelasticpull`,
  `farplayercannotdespawnboundunboundorcorpse`,
  `poweredscrubbercapturesonlyselectedgas`, `onlycompletedpaidsolvemovesconservedgas`,
  and `disableddevicedoesnotmovegas`. These are observed failures; no cause or
  inherited status is inferred. Log: `build/validation-logs/medical-ui-give-full.log`;
  failed worlds retained in `build/gametest-medical-ui-give-fixture`.

The authenticated connected `/give` branch and connected UI/render/keyboard behavior
remain untested. See `docs/controlled-body-give/IMPLEMENTATION.md` for core fixture
coverage and its boundary.

## Body-fall audio follow-up (2026-10-07)

- `test build compileGameTestJava`: **2,596 tests; 0 failures, errors, or skips**;
  build and GameTest compilation passed. JAR:
  `build/libs/moonstation14-0.0.1.jar` (6,243,926 bytes). Log:
  `build/validation-logs/body-fall-audio-unit.log`.
- Focused tags `standing,audio`: **3 selected; 2 passed, 1 failed**:
  `forceddownemitsresolvedsoundonceandmedicalrepeatisquiet`, on the medically
  forced-down pig sound assertion. Log and retained fixture:
  `build/validation-logs/body-fall-audio-focused.log`,
  `build/gametest-body-fall-audio-fixture`.
- No full native run followed the focused failure. Cause remains unresolved;
  native fixture output does not establish audible/client transport behavior.
  Mapping and provenance notes are in `../critical-death-prone-sprint/FALL-AUDIO.md`.

Final-source rerun supersedes that focused result: final unit/build/compile passed
again (**2,596 tests**, zero failures/errors/skips; 6,243,926-byte JAR;
`build/validation-logs/body-fall-audio-final-unit.log`). Tags
`posture-fall-audio,audio` selected 3; 2 passed and the same test failed this time
at the initial human forced-down sound assertion. Log and retained fixture:
`build/validation-logs/body-fall-audio-final-focused.log` and
`build/gametest-body-fall-audio-final-fixture`. Full native suite was not run.
This contradicts the reported passing repro run; cause remains unresolved.

### Completed final-source audio validation (2026-10-07)

Final `test build compileGameTestJava` passed: **2,596 tests, zero failures/errors/
skips**; JAR `build/libs/moonstation14-0.0.1.jar` is **6,243,954 bytes**.
Focused `posture-fall-audio,audio` selected **3/661; all 3 passed**. The
unfiltered suite selected **661; 649 passed, 12 failed** (exact methods and
unclassified status are recorded in `../critical-death-prone-sprint/FALL-AUDIO.md`).
Logs: `build/validation-logs/body-fall-audio-complete-{unit,focused,full}.log`;
failed worlds retained under `build/gametest-body-fall-audio-complete-fixture`.
The initial startup/tick-zero sound assertion failures were resolved by basing
sound admission on actual live-level entity membership and correcting fixtures to
respect entity join timing. Connected-client hearing remains unverified.

### Final analyzer polish validation (2026-10-07)

Final polish widens the shared hand-info inner width to 96 while keeping its old
inner-edge anchors, and aligns analyzer category rows (8px), inter-group
separators (3px), 42px logical number indentation, and shared number/label colors.
Resolved scan interval remains 3.5 and UI retention remains 3.

- `test build compileGameTestJava`: **2,591 tests; 0 failures, errors, or skips**;
  GameTest Java compiled and build succeeded. JUnit XML was summed. Log:
  `build/validation-logs/medical-final-polish-unit.log`.
- Focused `health-analyzer`: **5/659 selected; 5 passed**. Log:
  `build/validation-logs/medical-final-polish-focused.log`; successful disposable
  fixture removed.
- Artifact: `build/libs/moonstation14-0.0.1.jar` (6,198,648 bytes).
- No full native rerun was made for these bounded UI/layout/resolved-value changes.
  The prior full run remains **659/659 selected; 646 passed, 13 failed** as
  documented above; these focused results do not supersede it. Layout/unit and
  fixture coverage are not evidence of connected-client rendering or gameplay UI
  acceptance, which remain unverified.

### Unit-only test corrections and final build (2026-10-07)

After correcting three HealthAnalyzer test expectations and classifying
`HealthAnalyzerLayout` as client-only in the classloading test (test-only changes),
the final `test build compileGameTestJava` run succeeded: **2,590 JUnit tests, 0
failures, 0 errors, 0 skips**; GameTest Java compiled and build completed. This
supersedes the earlier four-unit-failure result above. Artifact:
`build/libs/moonstation14-0.0.1.jar` (**6,198,443 bytes**, timestamp
`2026-10-08T02:18:32Z`). Log: `build/validation-logs/medical-ui-give-final-unit.log`.
Native runs above remain the latest production-source GameTest evidence; these
unit-only changes did not require repeating them.

## Creative-menu crash found during connected testing

Owner's crash: `run/crash-reports/crash-2026-10-07_14.56.38-client.txt`.
NeoForge 21.1.224 threw `IllegalArgumentException: The stack count must be 1`
while building creative contents, through `ModCreativeModeTabs` during actual
`CreativeModeInventoryScreen` initialization.

The prototype factory's ten-item healing stacks were incorrectly reused as
creative display templates. Previous factory tests even asserted ten-count
creative entries; they did not exercise NeoForge's output validation. This was
an implementation and test-contract error, not a user configuration issue.

The correction normalizes only detached creative display stacks to one item,
retaining components and the separate gameplay factory's prototype quantities.
The actual native creative-tab callback regression now passes: **1/643 selected,
1 passed**, using tag `creative-menu`; unit/build validation also passes with
**2,569 tests, zero failures/errors/skips**. Evidence:
`build/validation-logs/creative-menu-fix-{unit,gametest}.log`.
See `CREATIVE-CRASH.md` for the permanent regression/prevention contract. A
connected client GUI opening was not performed; do not treat callback coverage
or earlier medical checks as proof that the actual screen was opened.
