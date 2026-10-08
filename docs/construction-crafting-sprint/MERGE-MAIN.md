# Construction / main integration

Inputs: construction `5e366dcb72197b16e446408fbce94bd59272d052`
and main `fd918c3f7d3874aec514590ab2cb5701267111bb`.
All eleven conflicts are resolved and staged; no assistant merge commit or push.

## Retained behavior

- Shared construction browser, search/focus, G toggle/release consumption,
  graph progression, crafting queues, material reservation/refunds and workspot
  presentation coexist with main's medical/posture/pulling and airlock workspaces.
- Storage canvas remains the sole pointer-input router. Browser, storage and
  embedded wire windows use their shared workspace rather than parallel managers.
  Wire depth is above storage and below a front browser; raising the wire/storage
  family puts it above a back browser. The same ranks govern pointer routing.
  Taking wire focus clears browser text focus; top-window bounds prevent scroll
  from leaking into a covered browser.
  With the browser visible, outside-all-window clicks lock without world actions,
  even with a retained wire session. Wire-only world-click routing remains intact.
  HUD rendering receives partial tick explicitly for gameplay and chat callers;
  construction workspace paint remains gameplay/approved-screen gated.
- Timed work has one canonical start path combining per-operation damage threshold,
  cancellation callback and world target. Existing overloads retain default damage
  threshold 1.0. The authoritative snapshot builder serves publication and the new
  live threshold/cancellation/presentation regression.
- Construction and airlock prototype catalogs, distinct screwdriver item IDs,
  medical sound entries and both sets of active mixins are retained. Main's
  `wirecutter` rename remains canonical; retired movement classes/gates stay retired.
- Authenticated hand admission retains `CharacterControlSystem.canAct` before
  sequence admission; identical guards introduced by automatic merging were deduplicated.
- Construction topology notifications use main's always-active atmosphere service;
  removed enable-switch APIs are not restored or used to suppress wall/girder updates.

## Validation

- Full `cleanTest test build compileGameTestJava --max-workers=4` passed:
  **2,732/2,732 JUnit tests**, 493 suites, zero failures/errors/skips.
  Log: `build/construction-main-merge-build.log`.
- Independent source reviews passed for shared workspace paint/input, combined
  prototype/asset registrations and timed cancellation/threshold/presentation.
  A new live timed regression covers the combined threshold/refund-callback path.
- `git ls-files -u` is empty; source conflict-marker scan is clear. Resolution
  changes are whitespace-clean. Full staged whitespace check still reports two
  warnings also present in the main input: `docs/airlocks-sprint/VALIDATION.md:21`
  trailing whitespace and `AirlockBlockEntity.java:317` an extra EOF blank line.
  No unrelated whitespace cleanup was applied.
- Current jar: `build/libs/moonstation14-0.0.1.jar`, **6,641,933 bytes**, UTC
  `2026-10-08T05:53:00.5926219Z`; SHA-256:
  `F66D70A8652F0986D9E1178BEEDDE9109D86F70A4439774E4C961BB64F88F9E4`.

- One unfiltered full native run selected/discovered **666/666**, zero untagged;
  all 666 completed: **652 passed, 14 required failures, zero incomplete**.
  Native exit 14 / Gradle exit 1. Log: `build/construction-main-merge-gametest.log`;
  failed fixture world retained at `build/gametest-construction-main-merge-fixture`.
  Owner configuration was not overwritten. The final client-only input follow-up
  does not change server behavior; no duplicate native run is claimed.
  Construction graph/world checks and the new threshold/cancellation/world-target
  regression passed; authenticated-player admission remains outside this evidence.
- Failures: `builtfixtureinitializesandreportsresolvedruntime`,
  `liveintakevacuumunavailableandstaleendpointaresafe`,
  `stoppingonechainlinkpreservestheotheruntilallendpointsclear`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `localextensionreceiverrespectsradiusandcablecuts`,
  `poweredventmetertracksbreakerandreplacement`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `detacheddeadbodymovesfromliveactorsnativepull`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `scheduleddooropeningtransportsandconservesfinitegas`,
  `farplayercannotdespawnboundunboundorcorpse`, `disableddevicedoesnotmovegas`,
  `poweredscrubbercapturesonlyselectedgas`, `onlycompletedpaidsolvemovesconservedgas`.
  Historical overlap with parent failures is not proof of inherited status;
  causality remains unresolved without parent reproduction.
- Shutdown also logged oversize writable-book pages in failure diagnostics;
  these do not change the completed-test counts. Failure logs/world remain retained.

No connected-client verification or merge commit is implied by source review.

## Remaining behavior question

An already-reserved foundation uses identity/alive continuation checks, so it may
finish after poison-induced Critical. Main explicitly retains quiet chemical
damage's non-interruption of timed work; this merge does not add a blanket
`canAct` continuation veto. Whether this specific reservation should finish is
unresolved, not an established merge regression. Portable crafting continues to
recheck its existing action gate when consuming materials.
