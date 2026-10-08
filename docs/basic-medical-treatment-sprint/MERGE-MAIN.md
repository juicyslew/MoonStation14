# Medical / main integration

Inputs: medical `de8b8ae` and main `82ff81f7258097d4d1b516f7cd0045e34f7bb40d`.
This record concerns integration after sprint closure, not a new feature sprint.
All thirteen conflicts are resolved. No merge commit or push is authorized or
implied. Integration validation completed on 2026-10-08; the full native gate
remains red.

## Retained behavior

- Medical supplies, persistent analyzer, 5/10/15-unit syringes, hand labels,
  controlled-body grants, and resolved fall audio coexist with construction,
  crafting, airlocks, tool profiles, and stun action admission.
- Resolved prototype catalogs and character component registrations retain both
  branches. Creative display copies remain count one; gameplay quantities retain
  medical and construction stack policies. Sound entries and provenance records
  preserve both branches.
- Construction inventory publication and reservation refunds use
  `LiveHandsCommitGateway`, including batched publication and existing nearby
  material compensation. No construction attachment writes bypass the gateway.
- Timed work has one canonical start/advance path. Dynamic repeat durations,
  cancellation callbacks, damage/movement thresholds, and world-target snapshots
  share it; completion invokes the consumer once per cycle. Denied completion
  invokes main's refund callback once; successful `STOP` does not refund.
- Main's pointer registry owns the outer workspace pass. Storage and analyzer
  windows share the storage family and their existing sibling ordering; wire and
  construction browser routing remain integrated. Analyzer clicks release browser
  search focus. There is no second analyzer HUD painter. Chat retains main's hand
  HUD/feedback; count-aware depth bands allow more than three combined siblings
  without crashing or overlapping the wire/browser bands.
- Pull cleanup uses the same hand gateway. A narrow trusted operation can remove
  only the exact reserved target from an incompatible body's existing hand state;
  it does not grant capability, insert items, or publish an invalid snapshot.
  Generic hand commits retain their strict validation. Both removed and live
  incompatible pullers detach without crashing or retaining their rope.

## Validation

Final `cleanTest test build compileGameTestJava --max-workers=4` passed:
**2,813 JUnit tests, zero failures/errors/skips**.

- Filtered tags `airlock,health-analyzer,medical-treatment,syringe,creative-menu,
  merge-main-regression,construction,pulling,posture-fall-audio`: **72/696 selected;
  all 72 completed, 67 passed, 5 failed**. Failures: Critical pull release, jumping
  taut puller, detached corpse pulling, contained dead-unit exit physics, and
  detached corpse settling (exact names in the focused log).
- Unfiltered: **696/696 selected and completed; 682 passed, 14 failed**, zero
  incomplete. Both native Gradle tasks returned nonzero due to test failures.
  New removed/incompatible-puller regressions, dynamic-repeat/world-target checks,
  and the three denied-construction/refund regressions passed.
- Full-run failures:
  `builtfixtureinitializesandreportsresolvedruntime`,
  `connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
  `pullsdeadhumantargetandreleaseswhensourcebecomescritical`,
  `detacheddeadbodymovesfromliveactorsnativepull`,
  `jumpingtautpullerfallsandlandsafterinputstops`,
  `blastknockslivingtargetsoutwardandskipsineligibletargets`,
  `loosejugfollowsnativetickunderelasticpull`,
  `negativestacksdryoneperdueintervalandcleanup`,
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`,
  `logpulsedisableslampandrestoresatresolveddeadline`,
  `scheduleddooropeningtransportsandconservesfinitegas`,
  `poweredscrubbercapturesonlyselectedgas`,
  `onlycompletedpaidsolvemovesconservedgas`, `disableddevicedoesnotmovegas`.

Final logs: `build/validation-logs/medical-main-merge-final2-{unit,
focused-gametest,full-gametest}.log`. Failed worlds/crash reports remain at
`build/gametest-medical-main-merge-fixture`; owner configuration was not changed.
Earlier native attempts crashed before completion and do not establish pass
counts. Their logs remain as diagnostic history.

Final JAR: `build/libs/moonstation14-0.0.1.jar`, **6,983,974 bytes**, UTC
`2026-10-08T06:57:39Z`; SHA-256:
`9F0327796DAEF2C6E3E4A7C876FC9792148728CE7013AA80004388A72C6BB8ED`.
Independent source review passed for the combined catalogs/assets, pointer
workspace boundaries, and narrow reservation cleanup. Sound JSON parses with
41 distinct top-level events and four body-fall samples.

Remaining native failures are **unclassified**, not established as inherited.
Connected client rendering, sound hearing and authenticated request transport
remain outside this automated evidence. The incompatible-puller regression uses
an unowned Villager; actual player snapshot delivery is source-reviewed, not
end-to-end tested. Owner-deferred unrelated ordering/power/ventilation work was
not pulled into the merge.

Resolution changes are whitespace-clean; whole staged checks retain two warnings
already present in the main input: `docs/airlocks-sprint/VALIDATION.md:21` and
`AirlockBlockEntity.java:317`. No unrelated whitespace cleanup was applied.
