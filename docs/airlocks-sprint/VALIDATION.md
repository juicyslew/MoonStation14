# Current validation

## Subsequent model and manual-test follow-up

The later exported-model replacement and screwdriver/stationary-contact fixes
are recorded in `../airlock-model-mini-sprint/IMPLEMENTATION.md`. That document
contains the newer artifact identity and validation evidence; closure artifacts
below are historical.

## Sprint closure (2026-10-07)

The owner declared the work complete and closed the sprint. See `HANDOFF.md`
for the accepted state and remaining gaps. This acceptance is not a new
automated or individually recorded manual test result; the historical full
GameTest gate remains red. No additional validation was launched for closure.

## Log pulse and stun-feedback final native gate (2026-10-07)

Latest assembled `build/libs/moonstation14-0.0.1.jar`, SHA-256
`C9A2D693C5F865F5D287B1480DDC76342CBE0561EF0A467BF7E2C4AE26C34A74`.
`test compileGameTestJava assemble` passed: **2,393 JUnit tests**, zero
failures/errors/skips; GameTest compilation and assembly passed. The one focused
tag-union server run selected **5/555 discovered**, started/completed **5**, passed
**5**, failed **0**: `inactiveLogAndAiWiresPersistPhysicalCutsWithoutGameplayEffects`,
`logPulseDisablesLampAndRestoresAtResolvedDeadline`,
`electrifiedBumpAppliesShockAndStillStartsDoorOpening`,
`crushReleaseCompletesNormalOpenBeforeFastAutoClose`, and
`stunDeniesSelectionWithoutRevisionChange`. Artifacts/config: 
`build/airlocks-validation/log-feedback-final/`; passing disposable world removed.
At this gate, human acceptance still awaited visual confirmation of the Log
pulse lamp and stunned wire-feedback message; the owner subsequently closed the
sprint as recorded above. The known
historical full GameTest result remains **544 passed, 5 failed, 3 incomplete**;
this focused run does not make that full-suite gate green.

## Shock/open, fresh wire status, and crush-cycle corrections (2026-10-07)

Latest assembled `build/libs/moonstation14-0.0.1.jar`, SHA-256
`12F9898CEE9C0D2A7ECC363DD92C3137C13AE24651EBFD4EECDB03230432111E`.
`test compileGameTestJava assemble` passed: **2,389 JUnit tests**, zero
failures/errors; GameTest compilation and assembly passed. One focused filtered
GameTest launch selected **4/554 discovered**, started/completed **4**, passed
**4**, failed **0**: `inactiveLogAndAiWiresPersistPhysicalCutsWithoutGameplayEffects`,
`electrifiedBumpAppliesShockAndStillStartsDoorOpening`,
`crushReleaseCompletesNormalOpenBeforeFastAutoClose`, and
`stunDeniesSelectionWithoutRevisionChange`. Run log and isolated configuration:
`build/airlocks-validation/airlock-corrections-2026-10-07/`; successful disposable
world removed. Fixture review also corrected the crush trace to verify only
post-crush opening phases and a stable animation timestamp within the first
opening interval. The previous unfiltered **552-test** run remains red at
**544 passed, 5 failed, 3 incomplete**; this focused correction run does not
resolve or reclassify those historical full-suite failures. E interaction still
has no connected authenticated-player acceptance; human HUD/visual/input checks
remain pending in `MANUAL.md`.

## Stun action-admission retry (2026-10-06)

Focused `stun_action_gate`: **1/552 selected, 1 completed, 1 passed**. The initial
full run remains historical: **544 passed, 5 failed, 3 selected tests did not
complete**; `stundeniesselectionwithoutrevisionchange` timed out within its
20-tick limit (it is a failure, not one of the three unexecuted tests). Retry log
and isolated configuration: `build/airlocks-validation/stun-action-retry/`.
The passing disposable retry world was removed; failed full-run world remains.
Current production jar SHA-256 remains
`E60FD2D40B337328A2939B99B3C6F34040DEA4BFC33B386D05137917BDBD363F`.
This focused retry does not make the full GameTest gate green. Human visual/HUD
and input acceptance remains pending.

## Final eight-wire/stun-admission checkpoint (2026-10-06)

Latest artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`E60FD2D40B337328A2939B99B3C6F34040DEA4BFC33B386D05137917BDBD363F`.
`test compileGameTestJava assemble` passed; JUnit **2,387 passed**, with zero
failures/errors. The single required full GameTest launch used
`build/gametest-stun-eight-wires-final`: **552 discovered/selected, 549 started,
544 passed, 5 failed; 3 selected did not complete**. Failures:
`builtfixtureinitializesandreportsresolvedruntime`,
`stundeniesselectionwithoutrevisionchange`,
`localextensionreceiverrespectsradiusandcablecuts`,
`liveapcmeterdrivesscheduledtwophasedoorandautoclose`, and
`farplayercannotdespawnboundunboundorcorpse`. The new
`inactiveLogAndAiWiresPersistPhysicalCutsWithoutGameplayEffects` placeholder
fixture passed; the new `stundeniesselectionwithoutrevisionchange` fixture did
not complete within its 20-tick limit. Do not claim the new stun behavior is
validated in live GameTest. Full run log:
`build/gametest-stun-eight-wires-final/logs/latest.log`; failed world retained.
No retry/other server was launched. Existing full-suite failures remain
unattributed absent evidence; overall GameTest gate is not green.

Airlock acceptance remains **open**. No assistant commit or push was made.

## Current panel-font fix checkpoint

The latest artifact is `build/libs/moonstation14-0.0.1.jar`, SHA-256
`97F3FE27BFBF6BECC5435B5D0F2364278E0938883220949361482C40FB6F9B15`.
One targeted Gradle invocation ran both focused test classes, GameTest compilation,
and assembly successfully: `AirlockFramePlannerTest` **13/13 passed** and
`AirlockWireScreenLayoutTest` **4/4 passed** (17/17 total); `compileGameTestJava`
and `assemble` passed. Log: `build/airlocks-validation/panel-font-fix/build.log`.
No server, client, full JUnit suite, or full GameTests were launched for this
checkpoint. Human visual/input acceptance remains pending; the user will validate
the in-game HUD and interaction.

## Current connected-test follow-up

**Presentation/input/seam follow-up checkpoint (2026-10-07).** The latest
assembled artifact is `build/libs/moonstation14-0.0.1.jar`, SHA-256
`E00921996DBCD6E871A8059B6327F8FDA8C9F49A35BA6B7CE799DB0223D53F91`.
`test compileGameTestJava assemble` completed successfully (log:
`build/airlocks-validation/connected-followup/build-presentation.log`; the JUnit
task passed). Do not treat this as acceptance: the new seam fixture fails in
the focused run and the full suite remains red. No more test/client servers
were launched after this checkpoint at the user's request.

Focused seam follow-up (2026-10-07): initial `seam-final` run selected **1/551**
and failed at the now-removed, unnecessary cable extension. A later retry selected
**1/551** and failed movement because the actor's approach corridor was occupied
by generated GameTest boundary barriers. Offline inspection of that retained world
and template showed the actor bbox intersects the barrier strip at `rootZ+1` from
move start, so the bump system correctly treated the barrier as the first
obstruction; this was not evidence of a production power/contact regression.

Source review found both extra cable placements unnecessary: `base_airlock.json`
resolves `extensionCableReceiver.localReachBlocks=3`; the existing paid WEST-face
APC spine at `(4,1,6..8)` remains continuous after removing its endpoint `(4,1,9)`.
The adjacent roots `(3,1,9)` and `(4,1,9)` are each within radius 3 of that spine.
`PowerTopology.cableEdge` connects same-face tangent/vertical host steps, and
`LoadedPowerGraph.nodesNear` uses the receiver radius, so both roots can use the
same spine without extending it. The failed `(5,1,9)` placement had no eligible
host; its off-route setup could also have introduced a distinct path. The fixture
now removes that extra route and waits for both doors' known exact resolved
requested-watt meter observations, rechecking both immediately before the single
move. No power values/assertions were weakened. The fixture now clears and verifies
only the four two-wide actor approach cells at `rootX..rootX+1`, `rootY..rootY+1`,
`rootZ+1`; it preserves the floor, doors and paid spine. Failure assertions include
pre/post actor AABBs plus each door's phase, power/bolt state, known meter and
resolved demand. `compileGametestJava` passed. The approved single focused retry
selected **1/551, completed 1, passed 1, failed 0**. The existing paid spine powered
both doors, both fresh exact-demand meter checks passed, and one `Entity.move`
opened both seam-adjacent doors. Log/config archived at
`build/airlocks-validation/seam-clear-approach/`; successful disposable world
removed. Earlier failed worlds and logs remain retained.

The existing `ConcurrentModificationException` during vanilla `TextureManager.close`
concurrent reload-worker shutdown remains unresolved; do not dismiss it as harmless
or resolved.
Full GameTests: **548 completed, 542 passed, 6 failed** (three selected tests
did not complete): `builtfixtureinitializesandreportsresolvedruntime`,
`receiverreconcilesalreadyloadedmissingfootprint`,
`realblockcontactslipsboundfakeplayerandvillageronlyonce`,
`oneentitymoveopensbothseamadjacentpaidairlocks`,
`obstructionusesexactlytwodoorwaycellsandsafetydeniescloseactor`, and
`farplayercannotdespawnboundunboundorcorpse`. Failed full log/world retained at
`build/airlocks-validation/connected-followup/gametest-full-presentation.log` /
`build/gametest-presentation-full/world`. These results do not establish which
failures are regressions versus baseline.

An isolated client launch applied `LivingEntityWishFacingMixin` and loaded the
GUI/resource atlases, but exited with a `ConcurrentModificationException`
during texture-manager shutdown; this is not a clean client-startup result and
no scene/screenshot was validated. Log:
`build/airlocks-validation/connected-followup/client-launch.log`. Human visual,
connected input, and UI acceptance remain open.

Client startup correction: the renderer callback now includes the required
fourth float argument (`scale`). Focused signature/knockdown tests and assembly
passed; an isolated client launch applied the renderer mixin and initialized
audio/GUI resources before normal shutdown. See `docs/render-mixin-fix/VALIDATION.md`.
Current jar SHA-256:
`3832FEC31452A844B75D42F9F3207EAE8760A250A7FE713B60ADD46308AC5241`.
The gameplay suite counts below predate this signature-only correction and are
not a new full-suite run.

Latest fresh readiness rerun (2026-10-07): GameTest sources compile; focused
`airlock` **16/16 passed** (`build/airlocks-validation/connected-followup/focused.log`).
The connected airlock fixtures now require a new known, exactly fully-paid power
observation immediately before each later controller activation, while asserting
the expected OPEN phase and reporting power/phase/root/deadline on timeout.
Production behavior and safety/crush assertions are unchanged.

Sequential full GameTests selected **550/550**, completed **547**, passed **539**,
failed **8**; three selected tests did not complete. These counts remain unchanged;
the unit gate remains **2,371 passed** and focused airlock **16/16** (character
lifecycle **62/62**). This gate is not green.
Log: `build/airlocks-validation/connected-followup/full.log`; failed world is
retained under `build/gametest-connected-full-readiness/world`. Failures:
`builtfixtureinitializesandreportsresolvedruntime`,
`receiverreconcilesalreadyloadedmissingfootprint`,
`poweredventmetertracksbreakerandreplacement`,
`leasesonlyexplicitlyconfiguredhumanvillagers`,
`alarmusesinheritedhundredwattsandradiusthree`,
`realblockcontactslipsboundfakeplayerandvillageronlyonce`,
`liveapcmeterdrivesscheduledtwophasedoorandautoclose`, and
`farplayercannotdespawnboundunboundorcorpse`. The remaining full auto-close
failure is unresolved: its exact-tick fixture assumes fresh readiness, whereas
production retries unknown power every 20 ticks and rearms known-loss auto-close
5 seconds after power restoration. The available failure does not establish
which gate failed; do not claim it was unrelated or attribute it to a specific
gate. The other listed failures likewise are not attributed without evidence.

Latest checkpoint (2026-10-07): `build/airlocks-validation/connected-followup/`.
Fresh `test compileGameTestJava assemble` completed successfully
(`build-final.log`): **2,371 JUnit passed**, zero failures/errors/skips, GameTest
compile and assembly passed. Previous follow-up `build/libs/moonstation14-0.0.1.jar`
SHA-256: `3AC49FA0995B7334618828B02650EA9971ECAC6197FF9676953B42B950220A6C`
(assembled 2026-10-07 02:36 UTC). The previously reported `F86A...` is stale,
not the current artifact.

Fresh focused `airlock`: **16/550 selected, 16 completed, 16 passed**.
Fresh `airlock_admission_probe`: **1/550 selected/completed, passed**. Prior
`character` lifecycle/knockdown run remains **62/550 completed, 62 passed**.
Passing disposable worlds were removed; logs and isolated run configs remain:
`gametest-airlock-final.log`, `gametest-admission-final.log`, and
`gametest-character.log`.

Earlier follow-up full GameTests: **550/550 discovered/selected, 547 completed, 537 passed,
10 failed; 3 selected tests did not complete**. Failed run/world retained at
`gametest-full-final.log` / `build/gametest-connected-full-final/world`.
Failures: built fixture resolved-runtime power; APC cable/receiver topology
(`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`localextensionreceiverrespectsradiusandcablecuts`,
`receiverreconcilesalreadyloadedmissingfootprint`); staggered retry; puddle
repeat deadline; scheduled airlock gas transport; two unsafe-close/crush
fixtures failed their live-controller setup; and distant-player despawn.

Compared with the historical full checkpoint (540/544 pass, 4 fail; 3
unexecuted), two named failures overlap: built fixture runtime and puddle repeat.
The historical contact-slip and inherited alarm failures did not recur under
those names; this does not prove their underlying causes are resolved or that
other current failures are unrelated. Current overall full suite is **not
green**. Airlock focused tests pass, but full-suite airlock failures and the
other failures remain unresolved; do not characterize the sprint as accepted.
Connected human presentation/audio/interaction acceptance remains open.

## Historical presentation/UI build (before connected-test fixes)

Historical artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`802E24DA5A93D4F8D7AEBC6F2FFA9D6247BD075AB2F2B4711E9081008FC6F628`.
This hash is not the current follow-up artifact; use the current checkpoint above.

- Full JUnit: **2,355 passed**, zero failures/errors/skips. Java/GameTest
  compilation and jar assembly passed.
- Focused `airlock`: **14/547 selected, 14 passed**, twice in clean separate
  directories. Logs/configuration are archived under
  `build/airlocks-validation/final-presentation/gametest-airlock-{a,b}`;
  disposable passing worlds were removed.
- Full GameTests: **547 discovered, 544 started/completed, 540 passed, 4 failed**.
  The three discovered-but-unexecuted tests remain unexplained.
  Log: `build/gametest-full.log`; world: `build/gametest-full-20261005`.

Remaining full-suite failures:
1. `builtfixtureinitializesandreportsresolvedruntime` — power UNKNOWN.
2. `realblockcontactslipsboundfakeplayerandvillageronlyonce` — villager re-entry count.
3. `alarmusesinheritedhundredwattsandradiusthree` — fresh solve restoration.
4. `puddleconsumerwaitsforresolvedprofiledeadlineandrepeats` — repeat interval.

The full suite is **not green**. The loaded-index reconciliation has live recovery
coverage through normal allocation, and contact-memo tests cover fresh-readiness
rearming without a root revision change. No power assertions, budgets or waits
were weakened. Temporary diagnostics were removed; the focused probe tag remains.

Presentation, prototype composition, session authority and input paths passed
independent static review. Connected human rendering/audio/nonmodal interaction
acceptance still requires `MANUAL.md`; fixture successes are not authenticated
connected-player proof. The prior checkpoints below are historical.

## Latest 3D/presentation/UI checkpoint

Historical pre-index-reconciliation artifact: `build/libs/moonstation14-0.0.1.jar`, SHA-256
`808D73A5586A7985DC159D49D4D5F3A30B4A9E1E5AB337936A38F7B26FD74CDE`.

- Full JUnit: **2,352 passed**, zero failures/errors/skips; Java and GameTest
  compilation and jar assembly passed.
- Focused `airlock_bump,airlock_override`: **2/546 selected, 2 passed**.
- Focused `airlock`: **13/546 selected/completed, 10 passed, 3 failed** on
  full-power admission.
- Full GameTests: **543/546 completed, 507 passed, 36 failed**. This is a
  materially worse result than the prior full run, not an accepted green gate.
  Log: `build/airlocks-validation-full-gametest.log`.

Implemented: separate resolved extension receiver; simple 3D horizontal sliding
leaves and corrected live render-view packet decoding; shader-backed 4px outline;
official tool art and successful panel-toggle audio; compact circuit window with
held-tool CLICK intent, seven lights (AIA/Log inactive), and normal-inventory
body-bound sessions. Outside-window right-drag provides camera aiming; normal
movement, hand actions and modifier-clicks retain their existing request routes.

An isolated clean full rerun completed **543 tests: 532 passed, 11 failed**.
The preceding 36-failure run included explicit atmosphere-isolation and dirty
fixture failures; it is not the current clean gate. A fresh isolated `airlock`
rerun still passed **10/13**, with three connected fixtures timing out before
their behavior assertions (`known=false`, `delivered=0`, clean topology).
Those same three connected fixtures subsequently passed **3/3** together, and
the admission probe passed **1/1**. A complete-cohort diagnostic rerun passed
**12/13**; the unsafe-close fixture timed out before its behavior assertions.
At that timeout, the receiver lookup returned `NULL` despite a clean graph,
and no meter existed. This is intermittent/cohort-sensitive evidence, not a
proven power fix. Per-chunk loaded/index coverage is the next diagnostic.

A subsequent trace established physically FULL/forced chunks missing from the
graph's loaded-space set. At receiver `(6658738,-59,6225439)`, reach 3, three of
four required footprint chunks were graph-unloaded despite remaining server-loaded.
This is a demonstrated bookkeeping inconsistency, not missing physical chunk
coverage. The bounded reconciliation fix queues already-loaded missing chunks
through ordinary snapshot validation/rebuild; unknown admission remains strict.
Final regression/gate results are recorded above.

Connected human
rendering, audio and nonmodal input/session acceptance are still required.
Earlier checkpoints below are historical.

## Latest naming-fix checkpoint

### Superseding visual/contact follow-up

Historical artifact SHA-256:
`847F70CB2ED938DA1B78F770B5DFA549AC11C32CD38E740D5F1C082556AE1972`.
The renderer now always draws the complete base door; powered unlit/glow states
are separate overlays, never the entire unpowered sprite. The server movement
hook consumes resolved Door.bumpOpen and DoorBumpOpener composition for contact
opening. E remains available, and neither route requires a PDA or ID. Power and
bolt checks remain authoritative.

Follow-up full JUnit/compilation: **2,348 passed**. Focused live bump movement:
**1/546 selected, passed**. Full GameTests completed **543**, with **538 passed
and five failed**. Failures: built fixture runtime, connected cable lamp/APC
trip/reclose, scheduled airlock gas transport, unsafe airlock close/crush, and
powered scrubber capture. Two are airlock fixtures: final acceptance remains
open. The new build needs connected human rendering/E/contact checks.

Earlier naming-fix and validation results below are historical.

The cutter now uses SS14's `Wirecutter` name, `WirecutterItem` class and
`moonstation14:wirecutter` registry/profile ID. No legacy item aliases remain.
All 2,342 JUnit tests pass, and GameTest compilation/production assembly pass.
Current jar SHA-256:
`9C1DB39D8811155A7C0E8CBE163DF4FBB07B764B5E14251455605EB223333E1F`.

The post-rename focused airlock rerun selected 12 tests, passed nine and failed
three power/transition checks. Log:
`build/wirecutter-rename-validation-gametest.log`. Consequently the earlier
12/12 focused runs below do not establish stable final airlock acceptance.
The rename preserves cutter behavior; the cause of the recurring fixture/runtime
failures remains unresolved. Earlier artifact hashes below are historical.

## Automated evidence

- Full JUnit/compilation gate: 2,342 passed, zero failures.
- Latest focused airlock checks: 12 selected/started, all 12 passed in two fresh
  world origins after loaded-chunk fixture coverage was added.
- Latest unfiltered GameTests: 545 selected, 542 completed, **536 passed and six
  failed**. The three discovered-but-not-executed methods remain unexplained.
  Full-suite log: `build/airlock-full.log`.
- Compilation and production assembly succeed. Current artifact:
  `build/libs/moonstation14-0.0.1.jar`, SHA-256
  `443BF98AB3C17AD9F528CE635D6AAD6CAA58C30CEBF2E02A6DB4711864F76F44`.

Full-suite failures:

1. `builtfixtureinitializesandreportsresolvedruntime`
2. `onewireddebuglampstaysbelowtripthreshold`
3. `crowdedcarrierdropusesfallback`
4. `receiverrequiresfullresolvedwattsandboltsgatenormalactivation`
5. `farplayercannotdespawnboundunboundorcorpse`
6. `poweredscrubbercapturesonlyselectedgas`

The airlock receiver fixture failed its bounded fresh/full-power **precondition**
with ready topology, an unchanged closed controller and an unknown meter. Its
bolt assertions therefore did not execute in this full run. Shared-world power
interference remains a candidate, not an established cause. Do not call this
gate green or assume all six failures predate the sprint.

## Scope of the passing fixtures

Real APC allocation and resolved receiver overrides; scheduled two-half
opening/closing and auto-close; root-domain blackout pry in both directions;
late-obstruction safety and partial-close crush/stun; sealed gas transport and
finite nitrogen/energy accounting; upper/lower and open-door tool LOS;
prototype, tool-quality, wire-state, opaque-layout, menu-protocol, indicator and
frame-planning tests. Trusted root/NPC fixtures do not prove authenticated
connected-player admission.

Important fixes established during validation: fresh controller defaults apply
once before mutation or on load; subsequent onLoad cannot discard a live action.
Controller scheduling reconciles its earliest deadline; power restoration
reschedules auto-close, and wire expiry cannot be starved by close retries.

## Remaining acceptance

Run `MANUAL.md` with a real controlled character. Verify screwdriver operation
on grey working parts (not merely crowbar/wirecutter wording), tool/window authority,
rendering/audio, upper-half interaction and aiming/actions while the window is
open. Hands must hold the actual tools; tool changes must work without reopening.

Fixture receiver searches require their full local loaded-chunk footprint;
test-only chunk tickets now cover it and release newly forced chunks by batch.
Production lookups still fail closed on incomplete loaded graph knowledge.
See `power-fixture-chunk-coverage.md`. Temporary runtime tracing was removed.

Keep full-suite failure artifacts for diagnosis; historical failed runs under
`build/airlocks-validation/` are not current acceptance evidence. See `PARITY.md`
for supported composition, deliberate Minecraft adaptations and deferred systems.
