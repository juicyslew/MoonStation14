# Construction validation

Status: sprint closed at the owner's direction. Final repairs are built and checked;
closure does not add connected-client or authenticated-path test evidence.
Aggregate native suite is not green; known failures and evidence limits remain below.

## Open-browser G toggle

The configured construction key now closes an already-open browser immediately
when text is not focused. Screen dispatch and locked-gameplay input share the
existing gesture/close owner; its physical-press latch prevents release from
reopening the browser. Screen-release-pre and raw input share an idempotent
release owner, since handled screen releases do not reach raw input. Search
typing, closed-browser tap/hold family selection, plans and crafting queues remain
unchanged. Independent source review passed after fixing the screen-release path.

- Filtered gesture, disposal-client, movement and body-hand checks: **56 selected,
  56 passed**, **0 failures/errors/skips**; `build` and GameTest compilation
  succeeded. Log: `build/construction-g-toggle-validation.log`.
- `git diff --check` passed. Latest jar: `build/libs/moonstation14-0.0.1.jar`,
  **6,018,033 bytes**, UTC **2026-10-08T05:15:46.4474640Z**.
- No native-server rerun for this client-input-only bugfix. Earlier native
  failures remain unresolved; no connected-client observation is claimed.

## Final icon alignment

Owner reports all other latest fixes acceptable; icons were slightly too low.
The common icon offset moved from **2 to 1 logical pixels**, for sprites/items
and disposal mesh thumbnails alike. Twelve-unit icons now occupy **[1,13]**
within the fourteen-unit row, centered with equal margins. Text placement, row
spacing, guide formatting, quantities and family selection are unchanged.

- Filtered checks selected the three row/icon/layout fixture classes: **17/17
  passed**, **0 failures/errors/skips**. This is not a new full-unit-suite run.
- `build` succeeded; GameTest compilation was up-to-date. Log:
  `build/construction-final-icon-alignment.log`. `git diff --check` passed.
- Historical jar: `build/libs/moonstation14-0.0.1.jar`, **6,016,576 bytes**, UTC
  **2026-10-08T03:56:51.1838902Z**. Earlier timestamps below are historical.
- No native server rerun for this single client-only offset. Previous focused
  **48/49** and full **586/595** native results/failures remain documented below.
  No separate connected-client pixel observation of this final nudge is claimed.

Durable subsystem orientation is now in `HANDOFF.md`. No commit/push was made.

## Resource-only instructions and family-scroll follow-up

The owner confirms search now works and the UI is close to acceptance. Remaining
changes shift list icons/labels down, center crafting quantity text, and simplify
structure instructions to actual resource/tool steps. Complete structure paths
include foundation requirements: Girder shows `2 Steel`; Steel Wall shows
`1) 2 Steel` and `2) 2 Steel`. Portable input→output summaries remain unchanged.
No construction costs, durations, output counts or authority rules change.

Disposal-family bouncing was traced to one mutable cursor indexing both legacy
`SHAPES` and a differently ordered catalogue recipe list. After a shared ACK,
legacy snapshots could overwrite that cursor. Shared scroll/render selection now
derives only from the pending-or-confirmed recipe identity; `legacyCursor` is
legacy-only. Independent source review passed for shared selection/ACK ownership
and the guide/row/quantity consumers. Final evidence:

- `cleanTest test build compileGameTestJava`: **2,590/2,590 passed**, **0 failures,
  errors or skips**; build and GameTest compilation successful. Log:
  `build/construction-family-guide-final-validation.log`.
- Current artifact: `build/libs/moonstation14-0.0.1.jar`, **6,016,576 bytes**, UTC
  **2026-10-08T03:38:25.2922668Z**. Earlier artifact metadata below is historical.
- Focused native `construction,disposal`: **49/595 selected**, **0 untagged**, all
  49 completed: **48 passed, 1 required failure**. Failure:
  `liveintakeworkswithoutwholecomponentproof` — “sealed finite source claimed.”
  Native process and Gradle command exit 1; no construction failures reported.
  Log: `build/construction-family-guide-focused-gametest.log`. Failed fixture
  retained at `build/gametest-construction-family-guide-fixture`; owner config
  untouched.
- The full suite was not rerun for this narrow client-only follow-up. The prior
  full result remains **586/595 passed, 9 failures**, documented below; aggregate
  certification is not green. No parent reproduction establishes inherited
  status or a cause for the unresolved intake failure.

Geometry tests cover normalized thumbnail bounds, lowered row placement and
centered quantity origins. Guide tests cover real full structure routes and
preserve declared item starting nodes. Family tests cover all nine options,
both-direction wraparound and pending/matching/stale ACK selection. Search
confirmation does not establish connected-client acceptance of these later
changes or authenticated runtime fixture coverage. Updated checks are in
`client-checks.md`; sprint acceptance remains pending.

## Smaller-controls, icons and search-focus follow-up

The latest owner run accepted construction and scrolling but rejected oversized
controls, vanilla close styling, verbose portable instructions, remaining search
failure, guidance border overlap and late/absent concurrent progress. New repairs
explicitly focus the EditBox after an overlay click, target a smaller browser,
use toolkit-painted chrome, compact rows/actions/status, simplify portable inputs
and outputs, and add resolved-output icons. Guidance dwell is 500 ms; shared
physical metrics protect glyph/shadow margins and expand for wrapped requirements.
Owned first-person world jobs retain camera-anchored bars alongside workspot
markers. Server start publication remains immediate; no network-delay cause was
established for the connected-client report.

The search root was verified in the target Minecraft source: direct widget
`mouseClicked` runs `onClick`, but normal Screen container dispatch separately
establishes focus. The old browser omitted that second step; prior typing tests
manually pre-focused the field and therefore missed the production failure.
New tests begin unfocused, exercise the shared production focus transition and
then type into the real EditBox. Headless tests do not dispatch its complete
pointer click, which also invokes Minecraft's sound/window-dependent behavior.

### Final evidence

- `cleanTest test build compileGameTestJava`: **2,587/2,587 passed**, **0 failures,
  errors or skips**; production/GameTest compilation and jar build successful.
  Log: `build/construction-search-icons-final-validation.log`.
- Current jar: `build/libs/moonstation14-0.0.1.jar`, **6,013,456 bytes**, UTC
  modification **2026-10-08T02:55:14.1562298Z**. Earlier jar metadata below is
  historical. Install this artifact on client and server.
- Focused native union (`construction,disposal,timed-interaction`): **63/595
  selected**, **0 untagged**, all 63 completed: **61 passed, 2 required failures**,
  8.444 seconds of test execution. Gradle task failed (native process exit 2).
  Failures: `heldbicaridinedosemetabolizesandhealsbrutedamage` — scheduled oral
  metabolism did not reduce actual blunt damage; and
  `liveintakeworkswithoutwholecomponentproof` — “sealed finite source claimed.”
  Log: `build/construction-search-icons-focused-gametest.log`.
- Unfiltered native run: **595/595 selected/completed**, **0 untagged**, **586
  passed, 9 required failures**, 39.79 seconds of test execution. Gradle task
  failed (native process exit 9). Log:
  `build/construction-search-icons-full-gametest.log`.
- No construction fixture failures were reported, including the new four-job
  immediate runtime-state fixture. That fixture verifies active targets before
  clock advancement, not authenticated packet delivery or client rendering.
- Focused/full failed worlds retained at
  `build/gametest-construction-search-icons-fixture` and
  `build/gametest-construction-search-icons-full-fixture`; owner config untouched.

| Full native failure | Observed failure |
| --- | --- |
| `builtfixtureinitializesandreportsresolvedruntime` | Scrubber/vent/alarm power UNKNOWN rather than live delivered power. |
| `onewireddebuglampstaysbelowtripthreshold` | APC output/supply UNKNOWN; receiver absent. |
| `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` | Breaker remained closed, trip not latched. |
| `localextensionreceiverrespectsradiusandcablecuts` | Remaining APC cable not known at radius four. |
| `poweredventmetertracksbreakerandreplacement` | Vent meter not known after solve. |
| `twowireddebuglampstripandgodarkthenreclose` | Fixture power nodes UNKNOWN; breaker closed and not latched. |
| `alarmusesinheritedhundredwattsandradiusthree` | Fresh solve did not restore alarm. |
| `farplayercannotdespawnboundunboundorcorpse` | Nearest-player assertion found a different fixture's FakePlayer. |
| `disableddevicedoesnotmovegas` | Two paid ventilation solves did not complete before deadline; meter unknown. |

Failures remain unresolved; no parent reproduction establishes inherited status.
Aggregate native certification is not green. Source reviews accepted search,
compact geometry/chrome, portable summary, shape-qualified icons/base-family
selection, guidance margins and local concurrent bars. No new font or asset
import was made. Connected-client appearance, complete pointer/keyboard dispatch,
authenticated crafting/construction and timer packet receipt remain separate
from headless/domain validation. See `client-checks.md`; sprint acceptance remains
pending, with no completion handoff.

## Latest compact-layout follow-up

All 15 latest reports received implementation changes: chat-sized text, compact
shared close chrome, narrower left/wider right panes, right-column actions with
no bottom banner, global search from remembered families, viewport-matched list
scrolling, active-plan placement before hit-host progression, concise structured
goal/requirement guidance and no misleading family-palette footer. Storage focus
changes order rather than hiding the browser. Screen-pre hooks defer to the
storage canvas's sole input router. Gameplay paints the workspace after HUD;
approved screens paint it after their screen, through the same dispatcher once
per frame. NeoForge HUD-post precedes screen rendering, so it cannot substitute
for that final screen pass.

### Final artifact and checks

- `cleanTest test build compileGameTestJava`: **2,575/2,575 passed**, **0 failures,
  errors or skips**; production/GameTest compilation and build passed. Log:
  `build/construction-compact-followup-delivery-validation.log`.
- Jar: `build/libs/moonstation14-0.0.1.jar`, **5,996,155 bytes**, UTC modification
  **2026-10-08T02:01:14.0557878Z**. Use this jar on both client and server; guidance
  payload fields changed.
- Focused native union (`construction,disposal,timed-interaction`): **62/594
  selected**, **0 untagged**, **61 passed, 1 required failure**, exit 1; 7.798
  seconds of fixture execution. Failure: `liveintakeworkswithoutwholecomponentproof`
  — “sealed finite source claimed.” Log:
  `build/construction-compact-accepted-focused-gametest.log`.
- Unfiltered native run: **594/594 selected/completed**, **0 untagged**, **586
  passed, 8 required failures**, exit 1; 39.99 seconds of fixture execution. Log:
  `build/construction-compact-accepted-full-gametest.log`. Construction fixtures,
  including corrected real-catalogue guidance, had no reported failures.
- Failure worlds retained at `build/gametest-construction-compact-accepted-fixture`
  and `build/gametest-construction-compact-accepted-full-fixture`; owner config
  untouched. Earlier failed diagnostic artifacts/logs remain available.

| Full native failure | Observed failure |
| --- | --- |
| `builtfixtureinitializesandreportsresolvedruntime` | Scrubber/vent/alarm power UNKNOWN instead of live delivered power. |
| `subthresholdrejecteddamageandhealingdonotcancel` | Zero native damage cancelled an operation. |
| `cleareyelineusesfifteenblockeyetoeyerange` | Clear eye-to-eye ray at exactly 15 blocks rejected. |
| `localextensionreceiverrespectsradiusandcablecuts` | Remaining APC cable not known at radius four. |
| `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` | Breaker remained closed; trip not latched. |
| `poweredventmetertracksbreakerandreplacement` | Powered vent meter not known after solve. |
| `negativestacksdryoneperdueintervalandcleanup` | Second due interval did not clean up; stack remained `-2.0`. |
| `farplayercannotdespawnboundunboundorcorpse` | Nearest-player assertion found a distant FakePlayer. |

These failures remain unresolved; no parent reproduction establishes inherited
status. Aggregate native certification is not green. Trusted-body/domain and
real-catalogue fixtures do not certify authenticated construction/crafting or
connected-client pixels, opening, Backspace dispatch, cursor locking or layering.
Headless real EditBox character/responder tests cover search; modifier checks and
widget click sounds need Minecraft's actual window/sound manager. No fabricated
client singleton or authority bypass was introduced. Connected-client acceptance
remains pending against `client-checks.md`; no completion handoff is warranted.

### Diagnostic context

An earlier follow-up native union had **59/62 passes**, with a new bare-girder
guidance failure plus blood digestion and intake failures. Real-catalogue
diagnosis found finished-target classification masking the bare-girder goal's
forward branches. The producer now permits one resolved forward requirement
while retaining completed-wall/pipe suppression. The construction-only rerun
passed **8/8 selected of 594**. Initial failed union log:
`build/construction-compact-followup-focused-gametest.log`; construction diagnosis:
`build/construction-guidance-compact-diagnostic.log`. Earlier intermediate jars
and unit counts are superseded by the final evidence above.

## Evidence so far

- Pinned upstream checkout verified: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
- Initial graph-only focused run: 3 tests passed, before subsequent catalogue and integration changes.
- A central `compileJava` passed before subsequent client/world integration changes (`build/construction-compile.log`).
- After integration fixes, coordinated focused tests passed **83/83**, zero failures/errors, and production/GameTest compilation passed (`build/construction-focused-tests.log`).
- Final full unit/build run passed **2,515/2,515**, zero failures/errors; production/GameTest compilation and jar generation passed (`build/construction-full-unit-build.log`). The catalogue/export fixture was corrected to include real construction resources and their parents; no production validation was weakened.
- Static foundation, corrected world runtime, nearby publication and audio reviews passed. Subsequent UI/guidance fixes have focused coverage; connected-client presentation remains unchecked.
- One isolated **unfiltered** native run selected/discovered **588/588**, **0 untagged**: **583 passed, 5 required failures**, 41.18 seconds of test execution (`build/construction-full-gametest.log`). Both new required construction fixtures passed in this full run; there was no separate focused native construction run. They exercise graph/world/atmosphere behavior and stocked FakePlayer denial, not authenticated success.
- Failure artifacts are retained in `build/gametest-construction-fixture`; owner configuration was untouched. Latest production artifact: `build/libs/moonstation14-0.0.1.jar`.

## Owner-reported menu/material follow-up

The owner found that G did not open the browser. With no plan, production tick cleared the input gesture every tick because its current body/epoch binding was never retained after reset. The corrected tick binds immediately after a genuine context transition; tests exercise initial no-plan tap, no-family hold, family hold and context resets through the production binding helper. This fixes a real gap not covered by the previous isolated gesture tests; GUI confirmation is still pending.

Steel, glass, plastic, paper material, cloth and wood now have maximum stacks of 30; metal rods already did. New stacks still start at count 1, and recipe quantities/footprints/tool/container limits are unchanged. See `material-stack-audit.md` and `BasicMaterialStackLimitTest`.

Final follow-up full unit/build run passed **2,518/2,518**, zero failures/errors, with jar and GameTest compilation successful (`build/construction-menu-material-followup.log`). Jar: **5,931,135 bytes**, UTC modification **2026-10-07T12:17:02.0830165Z**. No additional native server run was performed for this narrow client/value fix; the prior 583/588 full native result remains the latest native evidence.

## Owner-reported UI integration follow-up

The owner rejected the browser's size, modal input/background behavior, inconsistent family HUDs, vanilla-looking controls, overlapping content and untranslated labels. The replacement is a compact machine-toolkit window hosted alongside the existing storage pointer workspace, with a shared top-center plan HUD. Categories are removed; structure actions select a plan without quantity/Craft controls. Item queues retain quantity, progress and cancellation.

The first integration build passed **2,526/2,526** unit tests with no failures, errors or skips (`build/construction-ui-integration-validation.log`); jar generation and GameTest Java compilation succeeded. Independent review subsequently found browser clipping/queue-label overlap and missing logout/context cleanup. Their corrections passed source review and **2,529** unit tests (`build/construction-ui-final-validation.log`), including three production pointer-registry orchestration tests.

The final guidance-label/reopen-selection follow-up also passed independent review and **2,529/2,529** unit tests, **0 failures/errors/skips**, with jar generation and GameTest Java compilation successful (`build/construction-ui-accepted-validation.log`). The latest jar is **5,950,112 bytes**, UTC modification **2026-10-07T22:22:25Z**, at `build/libs/moonstation14-0.0.1.jar`. No connected-client run was performed; unit/layout/dispatcher checks are not visual acceptance.

## Previous full native run after initial UI fixes

Unfiltered `runGameTestServer` selected/discovered **588/588**, **0 untagged**; all 588 completed, with **579 passes and 9 required failures**, Gradle exit 1. Log: `build/construction-ui-full-gametest.log`. Failed artifacts are retained at `build/gametest-construction-ui-fixture`; previous failure artifacts and owner configuration were untouched. No production source changes were made during this run.

| Failed test | Observed failure |
| --- | --- |
| `builtfixtureinitializesandreportsresolvedruntime` | Scrubber, vent and alarm power remained UNKNOWN. |
| `onewireddebuglampstaysbelowtripthreshold` | Source/substation HV nodes and APC supply were UNKNOWN. |
| `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` | Connected path solved, but APC did not trip/latch. |
| `alarmusesinheritedhundredwattsandradiusthree` | Fresh solve did not restore the alarm. |
| `puddleconsumerwaitsforresolvedprofiledeadlineandrepeats` | Second dose did not commit after the expected interval. |
| `farplayercannotdespawnboundunboundorcorpse` | Nearest-player assertion encountered `FakePlayer['soap-slip']`. |
| `disableddevicedoesnotmovegas` | Paid-interval timeout; meter UNKNOWN/0 W, no positive solves. |
| `poweredscrubbercapturesonlyselectedgas` | Paid-interval timeout; meter UNKNOWN/0 W, no positive solves. |
| `onlycompletedpaidsolvemovesconservedgas` | Paid-interval timeout; meter UNKNOWN/0 W, no positive solves. |

All five failures from the earlier recorded run remain, with four additional failures. The connected-cable failure now reaches a later assertion. This is not parent-branch reproduction and does not establish that failures are inherited, harmless or caused by construction.

`resolvesSteelBranchAndPreservesIndependentPlasticGoal` and `fakeCarrierCannotCraftFromBodyOwnedSteel` passed. Their coverage is narrow: the former directly exercises resolved graph/world records, anchoring gating, remembered goals and wall/girder atmosphere composition; it does not drive authenticated placement, live tool/material consumption, timed work or dismantling returns. The latter proves FakePlayer denial preserves materials, not successful crafting. No existing authenticated construction-player GameTest fixture was found. Server results do not verify browser opening, blur, layout or cursor/input behavior. Aggregate native certification and connected-client acceptance remain unresolved.

## Earlier full native failures

| Test | Observed failure |
| --- | --- |
| `builtfixtureinitializesandreportsresolvedruntime` | Live delivered scrubber power remained UNKNOWN. |
| `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` | Expected connected fixture solve was not observed. |
| `alarmusesinheritedhundredwattsandradiusthree` | Fresh solve did not restore the alarm. |
| `farplayercannotdespawnboundunboundorcorpse` | Nearest-player assertion encountered `FakePlayer['soap-slip']`. |
| `onlycompletedpaidsolvemovesconservedgas` | Two positive paid solves did not complete by the deadline; meter UNKNOWN/0 W. |

The alarm and paid-solve cases were not in the last retained merge run's failure list; the overall list varies between runs. Alarm failure also appears in older timed-interaction records, but that is not parent-branch reproduction. No demonstrated construction cause was found, and no parent run establishes that all five are inherited or harmless. The despawn log identifies `soap-slip`, not the new crafting fixture's carrier. Do not claim aggregate regression-free certification or repair unrelated power/physiology behavior merely to make this suite green.

## Required acceptance

### Latest owner-reported regressions (validation pending)

Repairs include single browser snapshot publication, explicit screen-space
clipping (Minecraft 1.21.1 scissor coordinates are not pose-transformed), compact
dynamic layout, root recipe search, shared flushed window depth ranks, pause
suppression, outside-click cursor locking and select-plan close/lock behavior.
Family selection retains optimistic intent until an accepted shared ACK settles
it; the compact centered banner avoids published chat bounds and uses colored
material counts without readiness text.

Resolved first-stage previews now use actual block vertex alpha and the
controlled-body view path. Initial construction reserves materials before
graph-derived work, shares disposal physical transactions, permits independent
jobs and refunds cancellations. All nine supported pipe foundations use the
graph's 2 Steel / 1 second. Later material operations allow count changes in the
same surviving held stack. Existing timed snapshots carry optional world targets
for ephemeral pending-work markers. Accepted work refreshes hand proof before
the next request; deferred clicks reuse one shared structure intent owner.

The real catalogue guidance producer previously classified the Girder menu goal
as finished and suppressed guidance on unfinished walls. Classification now
uses resolved intermediate-stage roles; real-catalogue and native world-record
regressions were added. New trusted-body timed fixtures do not bypass player
authority and do not establish authenticated placement or connected GUI success.
The normal CHARACTER-control handshake is not exposed as a verified public
GameTest bootstrap; no authority bypass was introduced.

Connected-client checks were updated for all reported regressions; they remain
unperformed and are not acceptance evidence.

The first coordinated compile found fixture package-access and bulk-vertex API
errors, which were corrected without widening production inventory APIs. Its
subsequent unit execution reported **2,560 tests: 2,556 passes, 4 failures,
0 skipped**. These are not delivery evidence; catalogue/deferred test fixtures
and HUD expectations/formatting are being corrected and rerun. Log:
`build/construction-owner-fixes-validation.log` (subsequently overwritten by the
successful rerun).

Latest coordinated `cleanTest test build compileGameTestJava` passed
**2,561/2,561 unit tests**, **0 failures/errors/skips**; production build and
GameTest Java compilation passed. Jar: `build/libs/moonstation14-0.0.1.jar`,
**5,989,433 bytes**, UTC modification **2026-10-08T00:29:18.024Z**. This includes
the compact guidance line-budget/final chat-bound fitting repairs.

Focused native selection `construction,disposal,timed-interaction` selected
**62/594**, **0 untagged**, with **61 passes and 1 required failure**, Gradle
exit 1. Failure: `liveintakeworkswithoutwholecomponentproof` — `sealed finite
source claimed`. Log: `build/construction-owner-fixes-focused-gametest.log`;
failed artifacts: `build/gametest-construction-owner-fixes-fixture`. The new
construction/timed-disposal fixtures completed without logged failures. Their
trusted-body timing/world and catalogue coverage is not authenticated player
admission. The intake failure was not in the previously recorded nine-failure
list; its cause is unresolved, not classified as inherited or harmless.
First unfiltered rerun selected/completed **594/594**, **0 untagged**:
**586 passes, 8 required failures**, Gradle exit 1, 36.64 seconds of fixture
execution. Log: `build/construction-owner-fixes-full-gametest.log`; failed world:
`build/gametest-construction-owner-fixes-full-fixture`.

Failures: `builtfixtureinitializesandreportsresolvedruntime`,
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`poweredventmetertracksbreakerandreplacement`,
`humandrinkingownbloodpuddlecausesdigestivepoison`,
`leavinghandscancelsheldingestion`,
`sametargetcompletioninvalidationrefundssecondreservation`,
`farplayercannotdespawnboundunboundorcorpse`, and
`disableddevicedoesnotmovegas`. The new same-target reservation fixture failed
its single-install assertion and is under focused diagnosis; it had no reported
failure in the preceding mixed focused run. This is a construction acceptance
blocker, not dismissed as an unrelated failure. The intake fixture did not fail
this full run; that alone does not establish its earlier failure's cause.
No parent reproduction classifies the other failures as inherited or harmless.

Same-target diagnosis observed **0 installs, 2 refunds**, an air target, no
remaining active jobs, and a live fixture villager at **15 health**: both jobs
were interrupted before placement rather than losing a late-invalidated refund.
The focused `construction` rerun passed **8/8**. Trusted fixture villagers are
now invulnerable to isolate completion settlement from external damage in mixed
suite execution; production initial-work damage cancellation is unchanged.
The successful disposable diagnostic fixture directory was removed; failed
worlds remain available.

### Final verification after fixture isolation

`cleanTest test build compileGameTestJava` passed **2,561/2,561 unit tests**,
**0 failures/errors/skips**, with GameTest compilation successful. Log:
`build/construction-owner-fixes-final-validation.log`. Production jar remains
**5,989,433 bytes**, UTC modification **2026-10-08T00:29:18.0245812Z**.

Unfiltered native rerun selected/completed **594/594**, **0 untagged**:
**587 passes, 7 required failures**, Gradle exit 1, 35.42 seconds of fixture
execution. Log: `build/construction-owner-fixes-final-gametest.log`; failed world:
`build/gametest-construction-owner-fixes-final-fixture`. All construction and
timed-disposal fixtures completed without reported failures, including
same-target settlement, concurrent world-target deadlines/refunds, resolved
initial stages and catalogue-backed girder guidance. They do **not** certify
authenticated construction/crafting admission or connected-client pixels/input.

| Final failed test | Observed failure |
| --- | --- |
| `builtfixtureinitializesandreportsresolvedruntime` | Scrubber/vent/alarm power UNKNOWN. |
| `connectedcablepathlamploadtripsapcandmanualrecloserestoresit` | Breaker remained closed; trip not latched. |
| `localextensionreceiverrespectsradiusandcablecuts` | Remaining APC cable UNKNOWN at radius four. |
| `poweredventmetertracksbreakerandreplacement` | Powered vent meter UNKNOWN after solve. |
| `humandrinkingownbloodpuddlecausesdigestivepoison` | Expected typed poison damage absent. |
| `alarmusesinheritedhundredwattsandradiusthree` | Fresh solve did not restore alarm. |
| `farplayercannotdespawnboundunboundorcorpse` | Nearest-player assertion encountered a distant FakePlayer. |

These failures remain unresolved; no parent reproduction establishes their
cause or permits calling them inherited/harmless. Aggregate native certification
and connected-client acceptance remain pending. No commits or push were made.

Read-only intake diagnosis located the failed prerequisite in
`DisposalLiveIntakeGameTests`: it expects a trusted whole-component endpoint
after up to twelve ownership ticks. Live intake itself uses the immediate
finite-cell endpoint. The assertion does not distinguish incomplete ownership
from rejected enclosure proof. Atmosphere topology changes are relevant overlap,
but no cause was established; the failure was not repaired or waived.

- Resolved recipe/graph inheritance and defaults; live consumers use resolved costs, timings, tool fuel, output counts and conditions.
- Actual girder placement, both steel/plastic branches, ordered tool work and dismantling returns; original goal retained without restricting branches.
- Atomic body-owned and nearby-world crafting, capacity denial, stale identity/authority rejection, sequential quantity and cancellation.
- Browser/family navigation, tap/hold controls, configured binding labels, readiness/preview states and ordinary interactions.
- Continuous 1.5-second aim guidance using current stage plus remembered goal; dismissal and stale-response rejection.
- Disposal exposed/vertical/sealed-wall placement and existing orientation/transport behavior preserved.
- Focused tests, full unit/fixture compilation and full native regression run: performed, with results above. Filtering does not isolate fixtures. Remaining GUI/authenticated criteria require the connected-client checks in `client-checks.md`.

Use an isolated GameTest directory such as `build/gametest-construction-fixture`; never alter owner run configuration. Consolidate server runs and preserve useful failures.

## Authenticated acceptance limitation

`HandActorAuthority` deliberately rejects `FakePlayer` and unregistered players. Body-only GameTests cannot prove authenticated service publication. No legitimate registered-player fixture has yet been established. Do not weaken production authority or label denial tests as positive publication coverage. A connected-client acceptance check remains required unless a genuine fixture is completed.

The previous merged full native baseline had seven unresolved failures after the two disposal follow-up tests passed. Do not classify new failures as pre-existing without evidence.

## Remaining asset risk

Six canonical tool audio clips are byte-matched to the pinned checkout, with adjacent notices and entries in `docs/audio-unknown-provenance.md`. File-specific creator/license rights remain unresolved; importing pinned bytes does not establish distribution rights.
