# Examination sprint validation

## Closure status

Owner accepted and closed the sprint on 2026-10-07 after manual feedback and the
final status-column/refresh/menu-priority refinement. Historical pending-acceptance
notes below describe their recorded runs, not current delivery status. Final
evidence: 2,676 JUnit tests passed; 12/668 focused native tests selected and passed.
Latest full run remains 655/668 passed with 13 unresolved failures, not waived.
See `HANDOFF.md` for current architecture and artifact identity.

Disposable `world` directories were removed from the passing status-focus,
jug-scene-focused, and formatting-focused fixture runs after confirming no matching
server process remained. Logs/reports/config evidence and all failed full runs
were retained; no owner run directory was changed.

## Automated checks

- `./gradlew test compileGameTestJava assemble --max-workers=4`: **PASS**.
  JUnit: 2,624 tests, 0 failures, 0 errors, 0 skipped. Production, GameTest,
  and test sources compiled; assembly completed. `ObserverExamineRoleTest` had
  2/2 passing cases, including
  `corpseModeUsesResolvedCorpseExaminerWhileGhostModeUsesResolvedObserverExaminer`
  and `corpseModeFailsClosedWithoutResolvedBodyExaminer`.
- One unfiltered full GameTest run, isolated with
  `-Pms14GameTestDir=build/gametest-examination-final`: **660/660 selected and
  discovered; all completed, 647 passed, 13 failed**. No automatic retry.
- Examination fixture outcomes:
  - `resolvedHumanObserverAndMedicalPolicyBoundaries` — PASS.
  - `physicalEyeRangeAndTransparentCollisionLos` — failed in the original full
    run because near-target fixture geometry measured 3.5 instead of 3.0. Two
    separately authorized focused fixture retries corrected near-target geometry;
    the first then failed physical-16 eligibility, and the final retry added
    independent clear-LOS diagnostics plus live inside/outside probes. The final
    retry now fails earlier and more specifically: nominal endpoint is eye
    `(-5511661.5, -57.0, -6639791.5)`, point
    `(-5511661.5, -41.0, -6639791.5)`, distance `16.0000000000000000`, but
    `ExamineTargetResolver.visible` returns false. Thus the new in/out probes
    are not reached. No production range change or assertion weakening was made;
    no more retries are authorized.
  - `staleEntityIdentityAndUnloadedRootsFailClosed` — PASS.
  - `controllerIncarnationSurvivesStateUpdatesAndChangesOnReplacement` — PASS.

The other full-run failures were `builtfixtureinitializesandreportsresolvedruntime`,
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`poweredventmetertracksbreakerandreplacement`,
`pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
`ownedmedicalcorpsefallsandsettleswithnativeticks`,
`loosejugfollowsnativetickunderelasticpull`,
`jumpingtautpullerfallsandlandsafterinputstops`,
`logpulsedisableslampandrestoresatresolveddeadline`,
`liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
`disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`, and
`onlycompletedpaidsolvemovesconservedgas`. See the preserved full log for
assertion detail; none are waived by this report.

## Artifacts and remaining acceptance

- Full server log: `build/gametest-examination-final/logs/latest.log` (debug
  log alongside it); failed GameTest world/artifacts remain under
  `build/gametest-examination-final`.
- Parent-authorized filtered retry log: `build/gametest-examination-retry/logs/latest.log`.
  Tag `examine_policy` selected 4/660 discovered methods; 4 completed, 3 passed,
  1 failed (`physicalEyeRangeAndTransparentCollisionLos`) while still testing
  endpoint eligibility. Final endpoint diagnostic run:
  `build/gametest-examination-endpoint-final/logs/latest.log`; also selected
  4/660, completed 4, passed 3, failed 1 on the explicit nominal-endpoint LOS
  assertion above. Tags do not isolate fixtures; each filtered run used its own
  directory and neither replaced the preserved full-run history.
- Built jar: `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `1FA16181A12C72CB2D5450A309051C60718B098A755C1590BCE62B32C16F8D01` (after
  final fixture update). Final build/test/assembly: 2,624 JUnit tests, 0
  failures/errors/skips; the two `ObserverExamineRoleTest` CORPSE/GHOST cases
  passed. `ExaminationPolicyTest` still checks exact 16 allowed and the next
  representable value above 16 denied.
- Connected-client acceptance remains pending. Headless fixtures do not verify
  authenticated-player UI presentation, input precedence, or visual behavior;
  use the six-step checklist in [`MANUAL.md`](MANUAL.md).

## Post-LOS-source-fix validation (2026-10-07)

- Production LOS now visits crossed grid cells using bounded supercover DDA
  instead of quarter-block samples. It checks chunk availability before block
  reads, retains explicit examination transparency and the airlock read-only
  geometry special case, and treats null shape clips as misses. Only exactly
  equal boundary parameters are treated as tied; near-but-distinct crossings
  must not add cells the ray does not enter.
- Focused production-helper tests: `ExamineCellTraversalTest`, 4 tests passed.
  Coverage includes negative/vertical/diagonal/axis-aligned/end-point traversal,
  exact and near-but-distinct crossings, thin collision geometry, empty/null
  shapes, and a nonempty off-ray shape whose clip misses. The focused suite was
  rerun after this assertion: 4 tests passed. No GameTest server or client was launched.
- Final `./gradlew test compileGameTestJava assemble --max-workers=4`: **PASS**.
  JUnit XML: 2,628 tests, 0 failures, 0 errors, 0 skipped (470 suites);
  `compileGameTestJava` and assembly completed.
- Final jar `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `6498D2657E5C08E3211023609A526FD2012DB30E252B64F6E4C036997FF0FBEC`.
- This source-level/unit validation does **not** establish that the runtime
  `physicalEyeRangeAndTransparentCollisionLos` fixture now passes. Its nominal
  exact-16 LOS blocker remains **UNVERIFIED** after the patch; connected manual
  acceptance is pending. Historical full GameTest result remains 647/660 with
  13 failures, including this LOS fixture; historical focused runs remain 3/4
  with 1 failure. The unit pass does not waive or resolve those runtime failures.

## Final native server validation (2026-10-07)

- `./gradlew test compileGameTestJava assemble --max-workers=4`: **PASS** on the
  final production/test source state. JUnit XML under `build/test-results/test`:
  471 suites, 2,631 tests, 0 failures, 0 errors, 0 skipped. GameTest compilation
  and assembly completed. Jar `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `9864F331F4783FF43168C2BB4B105813582AF3DBC18D19598ED8DAE8C46EC17A`.
- Focused native server command selected `examine_policy,examine_selection`:
  **7/663 selected/discovered; 7 completed and passed, 0 failed/incomplete**.
  This includes all four policy fixtures and all three selector fixtures:
  `resolvedHumanObserverAndMedicalPolicyBoundaries`,
  `physicalEyeRangeAndTransparentCollisionLos`,
  `staleEntityIdentityAndUnloadedRootsFailClosed`,
  `controllerIncarnationSurvivesStateUpdatesAndChangesOnReplacement`,
  `airlockHalvesAndFacingsCanonicalizeToLowerRoot`,
  `closedLeavesAndOpenApertureUseRenderedGeometry`, and
  `worldQueryReturnsGlassAndOpaqueWallCandidates`. The physical-range fixture
  uses a horizontal ray inside the observed 36x5x8 loaded template bounds; the
  exact 16-block endpoint and live 15.5/16.5 probes execute. Focused log:
  `build/gametest-examination-production-verified/logs/latest.log`.
- Final unfiltered native server run: **663/663 selected/discovered and
  completed; 651 passed, 12 failed, 0 incomplete**. This is not a green full
  suite. Failed tests (all retained in
  `build/gametest-examination-production-full/logs/latest.log`):
  `builtfixtureinitializesandreportsresolvedruntime`,
  `sealedsegmentadmissionisclickedonlyandquarantinesreplacement`,
  `slidingsuperslipperyslipdoesnotspendreactivetouchdose`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `closedleavesandopenapertureuserenderedgeometry`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  and `onlycompletedpaidsolvemovesconservedgas`. The examination aperture
  fixture specifically timed out without succeeding or failing within 400 ticks
  in this broad run, despite passing in the focused run; preserve this failure
  as unresolved rather than attributing it to baseline.
- The earlier vertical exact-range probe diagnosed a `minecraft:barrier` at
  `(5858584,-54,-9749478)` and was then shown outside the actual template's
  five-block height. The fixture was corrected to probe horizontally and now
  passes focused execution; no production LOS bypass or global barrier removal
  was introduced.
- These server-side policy/selector fixtures do not exercise authenticated
  player RPC/UI popup presentation. Connected-client acceptance remains
  pending; the focused pass does not claim the reported popup/highlight issues
  are fixed.

## Corrected full-server rerun (2026-10-07, supersedes preceding full-run result)

- Final build command `./gradlew test compileGameTestJava assemble --max-workers=4`:
  **PASS**, JUnit XML 471 suites / 2,631 tests / 0 failures / 0 errors / 0
  skipped; GameTest compilation and assembly passed. Current jar SHA-256 remains
  `9864F331F4783FF43168C2BB4B105813582AF3DBC18D19598ED8DAE8C46EC17A`.
- Unfiltered server command used isolated directory
  `build/gametest-examination-final-corrected`: **663/663 selected/discovered,
  all 663 completed; 652 passed, 11 failed, 0 incomplete**. All seven
  examination fixtures passed in this full run, including
  `closedLeavesAndOpenApertureUseRenderedGeometry`; the earlier 400-tick
  timeout did not recur after replacing the absolute-world-time polling with
  `GameTestHelper.runAfterDelay(1, ...)` (relative fixture-tick scheduling).
- Remaining full-run failures, not waived:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `slidingsuperslipperyslipdoesnotspendreactivetouchdose`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `logpulsedisableslampandrestoresatresolveddeadline`,
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  and `onlycompletedpaidsolvemovesconservedgas`. Full log retained at
  `build/gametest-examination-final-corrected/logs/latest.log`.
- Native examination server validation still does not test authenticated
  player RPC/UI popup presentation. Connected-client acceptance remains
  unverified.

## Owner feedback integration validation (2026-10-07, latest)

- `./gradlew test compileGameTestJava assemble --max-workers=4`: **PASS**.
  JUnit XML: 473 suites, 2,637 tests, 0 failures, 0 errors, 0 skipped; production,
  GameTest and test compilation plus assembly succeeded. Fixed two Minecraft API
  compile mismatches in the examination UI (`Font#split` returns formatted
  sequences; use `new AABB(pos)` for a unit block box). Jar SHA-256:
  `EB396CBB6B9F513D83F70CD5DA66E8CEA352E1231F2C5BB6B5C235D07C7ABC4F`.
- Focused `examine_policy,examine_selection`: **8/664 selected/discovered; all
  8 completed and passed, 0 failed/incomplete**. This covers the seven policy
  and selection fixtures plus
  `freshEnrolledBodyUsesSharedDefaultWithoutInitializingState`, which exercises
  the fresh-body authority adapter without pre-seeding mob-state data. Logs:
  `build/gametest-examination-feedback-focused/logs/latest.log`.
- Unfiltered full server run, isolated at
  `build/gametest-examination-feedback-full`: **664/664 selected/discovered and
  completed; 654 passed, 10 failed, 0 incomplete**. The full examination suite
  passed, including the open-airlock fixture and fresh-body regression. Remaining
  failures (not waived): `builtfixtureinitializesandreportsresolvedruntime`,
  `containedrefusaldropsatownerposition`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  and `onlycompletedpaidsolvemovesconservedgas`. Full log retained at
  `build/gametest-examination-feedback-full/logs/latest.log`.
- Although all examination policy/selection native fixtures pass, none of these
  headless checks establishes authenticated-player RPC delivery or visual popup
  acceptance. Connected-client validation remains pending.

## Ordinary-response incarnation fix validation (2026-10-07, latest)

- The client response boundary previously required a non-null controller
  incarnation for every accepted result, rejecting legitimate accepted wall and
  entity responses (which have no controller). The boundary now distinguishes
  ordinary null-incarnation results, initial controller bindings (require a
  stamp), and refreshes (require exact UUID match), while preserving request,
  actor, epoch, and selected-target correlation.
- `./gradlew test compileGameTestJava assemble --max-workers=4`: **PASS**.
  JUnit XML: 474 suites, 2,641 tests, 0 failures, 0 errors, 0 skipped; GameTest
  compilation and assembly passed. `ExamineResponseStampTest` now exercises
  production `ExamineResponse.STREAM_CODEC` encode/decode and boundary acceptance
  for ordinary block and entity responses with null stamps, along with initial,
  refresh, and stale-correlation cases. Jar SHA-256:
  `C9B144BCDB0D6B3DCAF684A72E8B47C550AF5E52027216FEBA7802FAEF24C8E7`.
- Focused native server tags `examine_policy,examine_selection`:
  **8/664 selected/discovered; all 8 completed and passed, 0 failed/incomplete**.
  Log: `build/gametest-examination-response-fix/logs/latest.log`.
- This confirms focused server examination fixtures, not an authenticated
  client RPC/UI session; manual connected-player popup acceptance is still
  needed. The latest full-suite result remains the prior 664-test run
  (654 passed, 10 failed), not rerun for this narrow response-boundary change.

## Examination UI, puddle and endpoint-LOS refinement (2026-10-07, latest)

- Final `./gradlew test compileGameTestJava assemble --max-workers=4`:
  **PASS**. JUnit XML: 476 suites / 2,651 tests / 0 failures / 0 errors / 0
  skipped. Current built jar SHA-256:
  `D1EF365BCE5989815A6EDDE3779F0B1C3BF43FBA569A74626D0C2D6DEB7DAE80`.
- Combined native focus `examine_policy,examine_selection,examine_puddle`:
  **10/666 selected/discovered, 10 started and completed; 10 passed, 0 failed,
  0 incomplete**. All examination fixture methods passed:
  `contiguousSteelWallFacesAreVisibleButOpaqueWallBehindBlocks`,
  `resolvedHumanObserverAndMedicalPolicyBoundaries`,
  `physicalEyeRangeAndTransparentCollisionLos`,
  `staleEntityIdentityAndUnloadedRootsFailClosed`,
  `controllerIncarnationSurvivesStateUpdatesAndChangesOnReplacement`,
  `freshEnrolledBodyUsesSharedDefaultWithoutInitializingState`,
  `airlockHalvesAndFacingsCanonicalizeToLowerRoot`,
  `closedLeavesAndOpenApertureUseRenderedGeometry`,
  `worldQueryReturnsGlassAndOpaqueWallCandidates`, and
  `resolvedPuddleMetadataAndStoredSolutionAreReadOnlyAndDetailGated`.
- The new endpoint LOS fixture exposed a negative-direction exact-boundary bug:
  voxel traversal compared `floor(to)` against a segment terminating on a
  boundary, so it passed the endpoint and iterated toward unloaded space.
  Endpoint-cell selection now accounts for direction; traversal remains
  bounded to the actual endpoint. The fixture also uses a two-block-high
  opaque steel wall so a ray to the target behind cannot pass over its top.
- Puddle integration validation uses the live stored reagent bridge and an
  instance-bound inherited 500-unit capacity. The fixed non-exact volume
  category conversion now treats reagent units as hundredths; tests assert the
  component's category argument and recognizable water translation key rather
  than relying on headless language rendering.
- Full unfiltered native server run: **666/666 selected/discovered and
  completed; 653 passed, 13 failed, 0 incomplete**. All ten examination-tagged
  fixtures passed in the full run. Remaining failures, not waived:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `stundeniesselectionwithoutrevisionchange`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `loosejugfollowsnativetickunderelasticpull`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `logpulsedisableslampandrestoresatresolveddeadline`,
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  and `onlycompletedpaidsolvemovesconservedgas`. Full log:
  `build/gametest-examination-refinement-full/logs/latest.log`.
- Despite passing headless selection, authority, puddle, and LOS coverage,
  authenticated-client RPC and rendered UI acceptance are still not proven;
  no GUI automation or client launch was performed.

## Examination formatting and presentation validation (2026-10-07)

- `./gradlew test compileGameTestJava assemble --max-workers=4`: **PASS**.
  JUnit XML: 477 suites / 2,657 tests / 0 failures / 0 errors / 0 skipped;
  production and GameTest compilation plus assembly succeeded. Jar
  `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `C613CA5FCB7742EE1916BBB44137F3B7968ECA29DBEFCFBBDBE2E9772ADD72E8`.
- Focused native server tags `examine_policy,examine_selection,examine_puddle`:
  **10/666 selected/discovered; all 10 completed and passed, 0 failed, 0
  incomplete**. Log: `build/gametest-examination-formatting-focused/logs/latest.log`.
- Formatter volume categories follow the pinned SS14 switch in
  `SharedSolutionContainerSystem.cs` (100 full, >66 mostly-full, >33 middle,
  >0 mostly-empty, otherwise empty); integer-rounded zero remains empty. The
  SS14 middle category is examiner-dependent, so local output deterministically
  uses half-full. The GameTest checks nested reagent-name components and blended
  color while preserving the stored solution. Internal examine group IDs are
  not rendered as UI text.
- This narrow run does not rerun or resolve historical full-suite failures. The
  latest full native result remains **666/666 completed, 653 passed, 13 failed,
  0 incomplete** (see the preceding section and retained full log). No client
  was launched; authenticated UI/RPC and visual acceptance remain pending.

## Jug, celestial-scene and nullable appearance validation (2026-10-07)

- `ModJugs.resolved(null)` now returns no profile instead of dereferencing a
  nullable `Level`; `JugItem` consequently uses its existing 200-unit default.
  Non-null levels still select their side's catalog. A focused appearance test
  calls the production registered jug through `JugItemAppearance.capacityCents(null)`.
- The initial focused GameTest attempt exposed that constructing a new vanilla
  `Item` after registry freeze is invalid. The Jug fixture now uses the registered
  `ModItems.JUG`, verifies the root's 200-unit provider output and real stored
  mixed contents/read-only examine output; it does not test a child override.
  The scene fixture also
  invokes production `CelestialSceneExamination.rayVisibility` toward a loaded
  wall beyond its short virtual sky distance, in addition to the world-ray helper.
- Final `./gradlew test compileGameTestJava assemble --max-workers=4`: **PASS**;
  JUnit XML reports 479 suites, 2,667 tests, 0 failures, 0 errors, 0 skipped.
  Jar SHA-256: `EED28B7C3F07DCFE2E23FF05F6E9E970AA0CA30F675C14C8B07118D9D7114C2D`.
- Focused command `./gradlew runGameTestServer "-Pms14GameTestTags=examine_policy,examine_selection,examine_puddle,examine_jug,examine_scene" "-Pms14GameTestDir=build/gametest-examination-jug-scene-focused" --max-workers=4`:
  **12/668 selected/discovered; 12 completed, 12 passed, 0 failed/incomplete**.
  Both new jug and celestial-scene fixtures passed. Log:
  `build/gametest-examination-jug-scene-focused/logs/latest.log`.
- Required unfiltered command `./gradlew runGameTestServer "-Pms14GameTestDir=build/gametest-examination-jug-scene-full" --max-workers=4`:
  **668/668 selected/discovered; all completed, 655 passed, 13 failed, 0 incomplete**.
  All examination-tagged fixtures passed, including the new Jug and scene fixtures.
  Remaining failures (not waived): `builtfixtureinitializesandreportsresolvedruntime`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `loosejugfollowsnativetickunderelasticpull`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `puddleconsumerwaitsforresolvedprofiledeadlineandrepeats`,
  `logpulsedisableslampandrestoresatresolveddeadline`,
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`, and
  `onlycompletedpaidsolvemovesconservedgas`. Full log and failure artifacts remain
  under `build/gametest-examination-jug-scene-full`.
- These server checks do not validate connected-client popup rendering/RPC; no GUI
  automation or client launch was performed.

## Client status-column and refresh-policy refinement (2026-10-07)

- Removed obsolete click-anchor/bottom-right placement helpers and unused anchor
  state. Render placement now computes its rectangle only from
  `TopRightHudLayout.examineLeft/examineTop`, retaining the shared seven-row
  reservation. Replaced tests of abandoned placement math with stable right-edge
  and fixed-top assertions against the live shared layout helper.
- Added production helper-backed regressions for the A→explicit B request sequence:
  stale A fails both `ExamineResponseStamp.correlates` and `ExamineLatchSession.accepts`
  and cannot release B's in-flight slot; correlated B releases the coalesced retry.
  Tests cover disagreement retry tick 14 (not 13), pending suppression, matched
  20-tick cadence at 30 (not 29), and no scene mismatch retry. The live renderer
  now shares the tested detail-entry filter; revocation removes styled private
  rows and scroll offset clamps against the remaining public result.
- Layout tests assert 320×240 top=164/available=42 below all seven status row
  backgrounds, reserve bottom space, and show all 13 lines through both the
  two-line normal viewport and a three-line constrained fixture viewport. Pause,
  menu, and storage routing policy tests remain pure; `BodyHandClient` call order
  was source-reviewed, not GUI-input tested.
- The clipped header-cue width now comes from the same tested policy helper used
  by rendering: 18 pixels at the 56-pixel minimum, 74 at 112, and bounded to the
  actual narrower viewport when width cannot reach the normal maximum.
- Focused JUnit tests `ExamineClientPolicyTest` / `ExamineRefreshPolicyTest`:
  **16/16 passed**. `./gradlew test compileGameTestJava assemble --max-workers=4`:
  **PASS**, 480 suites / 2,676 tests / 0 failures / 0 errors / 0 skipped. Jar
  SHA-256: `F17A2DF0F3B697EC877FF78678B801B2193EEE1F8C9DDDE23B7106141BCF854C`.
- Focused native tags `examine_policy,examine_selection,examine_puddle,examine_jug,examine_scene`,
  isolated at `build/gametest-examination-status-focus`: **12/668 selected/discovered;
  12 completed, 12 passed, 0 failed, 0 incomplete**. Log:
  `build/gametest-examination-status-focus/logs/latest.log`. This is not GUI/RPC
  proof. Full suite was not rerun; latest full result remains 668/668 completed,
  655 passed, 13 failed, 0 incomplete as recorded above.
- The aggregate failure set is not identical to the prior 13-failure run:
  `puddleconsumerwaitsforresolvedprofiledeadlineandrepeats` now fails while the
  prior stun-selection case passes. Read-only tracing found no Jug on that path;
  it is timed puddle ingestion. An exact repeat-deadline assertion may race the
  completion tick, but logs lack measured deadlines/volumes to establish the
  cause. This remains an unresolved aggregate-suite failure, not a waived
  pre-existing failure or proof of a jug/scene regression.

## Jug runtime-capacity evidence correction (2026-10-07)

- `JugItem.getCapacity()` and `getCapacity(Level)` now share the small
  `capacityFromResolved(JugData)` projection. `JugPrototypeTest` resolves an
  inherited child overriding capacity to 500 and asserts that helper returns 500;
  it also checks the 200-unit root through the same helper. This is resolved
  child-to-runtime-helper coverage, not an instantiated child item test (vanilla
  item registries are frozen in the JUnit fixture).
- The native Jug provider fixture remains on registered `ModItems.JUG` and verifies
  the root's resolved 200-unit output with actual stored contents. It makes no claim
  of native child-override coverage. Focused GameTest evidence above remains 12/12;
  semantics-preserving getter refactor did not require a server rerun.
- Focused JUnit command for `JugPrototypeTest` and `JugItemAppearanceTest`: **5/5
  passed**. Final `./gradlew test compileGameTestJava assemble --max-workers=4`:
  **PASS**, 479 suites / 2,667 tests / 0 failures / 0 errors / 0 skipped.
  Final jar SHA-256:
  `BCE14EC82585F0991D305B3459E808AD3DD6B042869E198A50F50EF23D68EF83`.
