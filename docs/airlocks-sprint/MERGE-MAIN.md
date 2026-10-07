# Main merge integration — October 7, 2026

Merge inputs: `b83ed92c3ff0f29047da5a2096b754a93a13d13e` (ours) and
`26162c3024efebb716ae513e94aa1b9fef2a0d55` (`MERGE_HEAD`). All conflicts are
resolved and staged; this document does not record a merge commit.

## Integration corrections

- Kept both power-load families in `PowerRuntime`; closed the airlock receiver
  method before the disposal receiver method, and updated `PowerLiveLoopTest`
  inputs to include both airlock and disposal load lists.
- Removed duplicate body-world dispatch and the duplicate body-client
  `requestBlockUse` overload introduced by the source union. Kept the single
  authoritative airlock tool/activation dispatch and the `WirecutterItem`
  adapter (there is no local `CableCutterItem` type).
- Preserved the main construction screwdriver and the distinct airlock
  screwdriver by assigning the latter its own `airlock_screwdriver` registry
  ID, model, and localization.
- Added resolved tool-profile and footprint aliases for the airlock screwdriver:
  its runtime item-ID lookup inherits `SCREWING` from the shared screwdriver
  profile, and its grid storage footprint inherits the screwdriver dimensions.
  This keeps its distinct item identity without losing panel access or storage.
- Included the new airlock screwdriver profile in prototype-test fixtures so
  reload tests validate the complete registered profile catalog.
- Updated the server-classloading test's explicit client-source allowlist for
  merged client sources; no server/client boundary checks were removed.

## Validation

- Final light validation, `./gradlew test compileGameTestJava assemble
  --max-workers=4`: passed; **2,603 JUnit tests, zero failures or skips**,
  GameTest Java compilation and jar assembly passed. Report:
  `build/reports/tests/test/index.html`.
- Staged whitespace check passed; `git ls-files -u` is empty. Narrow conflict
  marker scan found no source/test markers.
- One unfiltered full GameTest server launch, isolated with
  `-Pms14GameTestDir=build/gametest-airlock-main-merge-final`: **656/656
  discovered and selected; 656 started/completed; 644 passed, 12 failed, 0
  incomplete** (40.95 server seconds). A later focused diagnostic retry passed
  **2/2 selected tests** (`logPulseDisablesLampAndRestoresAtResolvedDeadline`,
  `liveApcMeterDrivesScheduledTwoPhaseDoorAndAutoClose`), but did not reproduce
  the failures; it does not change the full-suite result. This remains a red
  full-suite gate. Log and
  failed world are retained at `build/gametest-airlock-main-merge-final/`;
  owner configuration was not overwritten. No retry or additional server was
  launched.
- Full-run named checks completed successfully (not listed among failures):
  `electrifiedBumpAppliesShockAndStillStartsDoorOpening`,
  `crushReleaseCompletesNormalOpenBeforeFastAutoClose`,
  `inactiveLogAndAiWiresPersistPhysicalCutsWithoutGameplayEffects`,
  `stunDeniesSelectionWithoutRevisionChange`,
  `oneEntityMoveOpensBothSeamAdjacentPaidAirlocks`,
  `nativeHandsAffectCrawlingAndDropOnCriticalNotification`,
  `criticalBodyCannotAdmitDeathGhost`,
  `nativeDeathGhostAndManualReturn`,
  `registeredReviveCommandEnforcesPermissionAndRevivesExactCorpse`,
  `criticalTransitionForceDropsNpcHandsAndHealingRestoresAction`,
  `fixtureBuildsInitializesPowersAndFlushesWholeStack`,
  `boundHumanRidesWholePoweredDisposalTourWithFiniteAir`,
  `liveIntakeWorksWithoutWholeComponentProof`, and
  `liveIntakeVacuumUnavailableAndStaleEndpointAreSafe`. All 656 tests
  completed, so each not named in the server's failure list passed.
- Movement/pulling coverage is mixed, not green: the full run failed
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `detacheddeadbodymovesfromliveactorsnativepull`,
  `loosejugfollowsnativetickunderelasticpull`, and
  `jumpingtautpullerfallsandlandsafterinputstops`. The same full run passed
  `controlledActorCanTurnRetainedDeadBodyWithoutChangingMedicalState` and
  `controlledActorCanTurnCriticalAndVoluntaryDownBodies`; this does not erase
  the pull failures.
- The full-run failures were
  `builtfixtureinitializesandreportsresolvedruntime`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `detacheddeadbodymovesfromliveactorsnativepull`,
  `loosejugfollowsnativetickunderelasticpull`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `logpulsedisableslampandrestoresatresolveddeadline`,
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  and `onlycompletedpaidsolvemovesconservedgas`. Do not treat historical
  overlap as proof that any current failure is harmless or pre-existing.
- Final light-validation artifact `build/libs/moonstation14-0.0.1.jar`,
  6,300,748 bytes; SHA-256
  `D1053AAA19B1D555D0127376F37BB15BE4F2929CA143EA8CFC21D8EADF9360D3`.
  The pre-alias build was 6,300,109 bytes with SHA-256
  `87AD5D818BEDCE172770D6BD0D74088C62BD2A035D8F2757D85EE6BCE749FA30`.

No client launch or connected-player acceptance was performed. Full GameTest
failure artifacts remain available for follow-up; the merge is still pending
completion/commit by its owner.
