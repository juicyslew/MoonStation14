# Equipment / examination merge

Inputs: equipment `fe538aa` and incoming main `5f8c7f6`.
This is post-closure integration, not a reopened equipment sprint.

Merge acceptance was reopened after the owner rejected completion with a failing
equipment check. The owner clarified that **all equipment-related tests must
pass**; unrelated full-suite failures are reported separately, not a requirement
to repair other subsystems.

The four conflict resolutions retain equipment panel state and examination
lifecycle/input handling, both prototype-family registrations/accessors and their
test expectations, and both client-source classloading exclusions. Interactive
HUD areas take priority over world examination. Independent source review passed.

Validation on 2026-10-08:

- Serial `compileJava compileTestJava compileGameTestJava test jar` passed:
  **2,936 unit tests**, zero failures/errors/skips.
- Full native run selected and completed **713/713**: **708 passed, five required
  failures**. Failing checks: `onewireddebuglampstaysbelowtripthreshold`,
  `poweredventmetertracksbreakerandreplacement`,
  `removingprerequisitedropsdependentsandpreservesunrelatedequipment`,
  `logpulsedisableslampandrestoresatresolveddeadline`, and
  `liveapcmeterdrivesscheduledtwophasedoorandautoclose`. Causes are not established
  by matching historical failure names. The equipment failure was
  traced to contradictory fixture setup: the denied case remains in the left
  hand and removing the coat fills the right hand, yet the fixture then expects
  jumpsuit removal into an empty hand. Current production correctly drops the
  garment when both hands are full. Dependency-aware unequip and staged commit
  remain present; no missing production-path regression was established.
- Corrected the fixture by separating suit-storage denial from dependency
  removal, preserving case/coat ownership, requested-hand and unchanged-selection
  checks, dependent drops, and unrelated equipment/grid contents. No production
  rules or fit permissions were changed. Equipped-storage and appearance
  publication fixtures now carry the equipment tag for complete selection.
- Final filtered native run: **73/714 selected/discovered**, all **73 completed
  and passed**, zero failures/incomplete. Selectors: `equipment`,
  `equipment-transfer`, `equipment-quick-equip`, `equipment-regression`,
  `equipment-drag-world`, and `storage`. Independent review confirmed equipment
  coverage, including public appearance and equipped-grid paths, and accepted
  the repaired fixture. Final build/unit run passed **2,936 tests**, zero
  failures/errors/skips. Logs in `build/validation-logs/`:
  `equipment-examination-merge-equipment-build-final.log`,
  `equipment-examination-merge-equipment-compile-final.log`, and
  `equipment-examination-merge-equipment-gametest-final.log`.
- The equipment acceptance gate is now satisfied. The full native suite was not
  rerun after the fixture repair; the four power/airlock failures remain outside
  this gate and are not claimed resolved. The passing focused fixture directory
  was removed after retaining logs; original failed-run artifacts remain.
- Logs: `build/validation-logs/equipment-examination-merge-build.log` and
  `equipment-examination-merge-gametest.log` in the same directory. Failed native
  artifacts are retained. No client/GUI validation was performed.

JAR: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`E9947883507FA35244CAD7149D400F1544673A542A81099E487E72696955408A`.
The conflict resolutions are staged; no unresolved index entries remain. No merge
commit or push was performed. Historical sprint artifacts are not this merged JAR.
