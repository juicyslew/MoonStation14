# Main integration — 2026-10-07

Inputs: medical/posture branch `90d8730`, incoming main `6a8641b`.
Conflicts are resolved; no merge commit or push was requested.

## Integration boundaries

- Disposal prototypes, construction, containment, finite carried air, power,
  cameras, networking and outlet flight coexist with medical/posture/observer
  and elastic pulling. Both prototype catalogs and reload paths remain installed.
- Observer movement still comes from resolved Observer composition and existing
  lifecycle/prediction. Incoming pulled bodies and contained transit retain their
  authoritative interpolation barriers; outgoing-only pullers retain prediction.
- Critical/Dead cannot voluntarily move or escape. Containment retains the exact
  loaded medical corpse and suppresses passive physics; exit restores flags and
  native corpse stepping. Unsupported native dead entities still release.
  Detached incapacitated bodies consume outlet flight without double stepping.
- Native death must not replace resolved prone collision with standing-height
  `DYING` dimensions. The medical mixin reapplies posture after `die`; focused
  runtime evidence confirms `SWIMMING` and compact collision after correction.
- Shared default K is context-sensitive: standing climb, prone roll, Shift+K
  body turn. Separate remappings retain independent climb. Accepted Ctrl-click
  pulling, C posture, sprites and rendering remain.
- Main's pressure scheduler, continuations, finite-cell transfers, diagnostics
  and separate vacuum/space profiles remain. Retired global switches and legacy
  movement controllers were not restored.
- Damage publishes feedback-aware and typed applied-damage events. Quiet poison
  stays quiet; containment air routing and gasps coexist. Wrapped-row chat paging
  preserves append anchoring/autofollow and a full oldest viewport, allowing
  bounded overlap on the final page.

## Validation

- Full lightweight chain: **2,517 tests / 440 suites**, zero failures/errors/skips;
  Java/GameTest compilation, server/client preparation and jar generation passed.
- One unfiltered native run: **635/635 selected, 613 passed / 22 failed**.
  Log: `build/merge-main-medical-full-gametest.log`; failed world retained at
  `build/gametest-merge-medical-main`.
- Corrective focused runs selected **13/635** (`merge-main-regression`): first
  **8 passed / 5 failed**, then **11 passed / 2 failed**. Latest log:
  `build/validation-logs/merge-main-medical-regression-final-gametest.log`; failed worlds remain.
  This is not a green full-suite rerun.
- Latest focused failures: corpse hauling onto a one-block ledge (compact pose
  fixed, platform traversal still fails) and loose-pull fixture admission.
  Admission aim now uses the settled target's bounding-box center. Final isolated
  `loose-pull-admission` validation selected **1/635**, **1 passed**; log:
  `build/validation-logs/merge-main-loose-admission-gametest.log`. The final lightweight chain
  also passed all **2,517 tests** after this correction and bounded diagnostics.
  The full suite and 13-case selection were not rerun afterward.
- Other full-run power/ventilation, meter and despawn failures remain unresolved;
  do not label all failures pre-existing without evidence. Pressure/reactive-front
  fixtures passed after always-on API migration. Synthetic local service instances
  share world storage; tags provide selection, not isolation.

Corrected stale fixtures cover preserved medical facing, native physics after
exit, unchanged initial ownership, finite vacuum storage, valid temperature
floor, unavailable contained-air rejection and travel beyond tether slack.
No client was launched; owner configuration was untouched. Closed medical-sprint
owner acceptance remains distinct from automated merge validation.

## Owner disposition

- Removed `higherPullerPhysicallyHaulsCorpseOntoOneBlockLedge` at owner request.
  One-block corpse hauling is not currently achievable; its solution is deferred.
  Production tether behavior and the other pulling tests are unchanged.
- Power/ventilation failures are explicitly deferred. Initialization timing is
  an owner hypothesis, not an established root cause; no fixes or test changes
  were made for these failures.
- Despawn fixture interference is left unchanged at owner request.
- GameTest compilation passed after removal. Recorded 635-test runs above are
  historical and include the removed test; no native suite was rerun afterward.
