# Validation status

### Sprint closure and live finite-air fix

The owner reports all observed issues resolved and authorizes sprint closure. See `HANDOFF.md` for final architecture, acceptance and remaining parity gaps; earlier entries below are chronological and may describe superseded states.

Live intake and outlet gas release now use the synchronous owned finite-cell endpoint rather than the trusted whole-room enclosure-proof endpoint. Intake uses source temperature; unknown/vacuum sources do not manufacture oxygen. The strict trusted setup endpoint remains unchanged in purpose. Room CO₂ and damage policies were not changed by this fix.

**123 focused JUnit tests passed**, Java/GameTest compilation and jar generation passed, and independent review passed. Log: `build/disposals-live-intake-focused.log`. New live-intake native regressions compiled but were not run. No headless server/client was launched for this follow-up. Owner connected acceptance is separate from automated coverage; historical broad-suite failures and the unrerun landing regression are not erased by closure.

### Existing-pipe clicks, material refresh and upward flight (October 6 local)

Found and fixed a second construction-admission defect: `BodyBlockUseTargetProbe` clips through a bounded `BlockGetter`, but disposal outline geometry previously returned empty unless its shape context was a real `Level`. Shape callbacks now receive the actual server level while ray block/fluid reads remain bounded. Exact hit-block/face, loaded-chunk and owner admission rules remain unchanged. A new native-level tube-ray regression compiles but was not run.

Committed construction was previously acknowledged as `NO_CHANGE`, despite advancing `LiveHands` and consuming steel. `BodyConstructionOutcomePolicy` now emits `PLACED` when the authoritative hand revision advances, activating the existing immediate fresh-query path; deferred starts remain `NO_CHANGE`. The one-frame red material text had a separate confirmed cause: refreshed hand proof is briefly absent and the HUD treated it as an empty/wrong-material hand. That state now displays neutral “Updating held material…”; confirmed empty hands still count as wrong material. No optimistic debit or action-authority relaxation was added.

External flight now preserves vertical momentum during the short throw interval, including suppressing adapter gravity/vertical drag, then resumes ordinary gravity afterward. Collision still clips the trajectory. Pure six-tick upward, gravity-resumption and ceiling-collision regressions pass; actual height is still subject to Minecraft/entity behavior and headroom.

The owner's severe damage occurred **before ejection**. Its cause remains unconfirmed. `DisposalRideDamageDiagnostics` observes actual positive typed-damage events for valid TRANSIT ownership and logs `Disposal ride damage` with damage keys, health, owner/position and carried-air pressure, volume, O2 and CO2. Missing air is labeled unknown, not vacuum. This observer does not alter injury or gas state; room CO2 work was explicitly left untouched. Next evidence needed: these server log lines from an affected connected ride.

**82 focused JUnit tests passed**, zero failures/errors; source/GameTest compilation and jar generation passed (`build/disposals-pipe-extension-upward-focused.log`). Independent source review passed. **No headless server/client launched** for this follow-up; connected placement, count refresh, upward motion and damage diagnostics remain for retest. Prior full-suite failures and the unrerun airborne-landing native regression remain visible.

Artifact `build/libs/moonstation14-0.0.1.jar`: **5,335,683 bytes**, UTC mtime `2026-10-07T05:55:47.5911637Z`.

### Steel-wall construction feedback follow-up (October 6 local)

Fixed client/server target disagreement: the client previewed bends adjacent to steel walls while the server classified them as sealed placements and rejected them. Both now consume `DisposalConstructionTargetPolicy`: only an ordinary straight pipe in an empty eligible wall selects sealed placement; non-straight shapes and occupied crossings select the adjacent exposed cell. Existing material, collision, loaded-host, incarnation, authority and deferred-placement checks remain in force.

Selected sealed crossings now show a cached translucent no-depth-test ghost with color-only writes and an explicit “inside steel wall” HUD label. The render state is scoped to that selected ghost; ordinary previews retain depth testing. Local placement issues use readable localization and persist for **80 client ticks**, even after target loss/readiness changes and while the palette is open; exit, selection and body/epoch changes clear them. This is local preview feedback, not a new server-rejection protocol.

**34 focused disposal UI JUnit tests passed**, zero failures/errors; source/GameTest compilation and jar generation passed (`build/disposals-wall-construction-focused.log`). Independent source review passed. No headless server/client was launched for this fix. Connected ghost visibility and readable feedback remain for the owner's retest. The prior airborne-landing native regression was not rerun. Room CO2/damage was deliberately left untouched at the owner's request.

Artifact `build/libs/moonstation14-0.0.1.jar`: **5,330,063 bytes**, UTC mtime `2026-10-07T04:41:20.0272926Z`.

### Shared eligibility, transit interpolation and outlet flight batch (October 6 local)

`CharacterControlSystem.canMove` is the shared eligibility query used by escape and ordinary movement; harness snapshots publish `movementBlocked` for prediction. It currently rejects sleeping, dead and stunned bodies. Knockdown still permits slowed movement; action permission remains separate. Canonical critical mob state is **not implemented**. The existing resolved unit-wide 0.5-second exit cooldown is retained and now has live nine/ten-world-tick boundary coverage.

Stable TRANSIT snapshots interpolate through the inherited Minecraft `LivingEntity.lerpTo`/`aiStep` path. Body/epoch/holder changes, UNIT, missing containment views and exit snap/reset. This does not predict pipe routing. Open exits now launch owned bodies into generic runtime `ExternalBodyFlight`, derived from native distance 3/speed 10 (six ticks), with passive physics, same-tick deduplication, containment cancellation and separate replicated/predicted flight timing. Gravity and collision remain Minecraft adaptations.

Normal `test build` passed **2,207 JUnit tests**, zero failures/errors (`build/disposals-movement-batch-build.log`). Following the owner's request, only **one consolidated headless run** was launched for the completed batch: **11/12 passed** (`build/disposals-movement-batch-gametest.log`). Passing targets included both full rides, entry/cancellation, APC switching, movement eligibility, sleeping/stun recovery, exact unit cooldown, passive outlet flight and cancellation/deduplication. The airborne-expiry case failed because tracking ended on first landing, before the next grounded-friction tick.

That landing defect was subsequently fixed: tracking ends only when grounded motion is actually settled, with signed-zero-safe velocity testing. Independent source review passed. **49 focused JUnit tests passed** after the correction, including positive/westward landing, flight timing, snapshot round-trip and interpolation boundaries; final source/GameTest compilation and jar generation passed (`build/disposals-movement-batch-final-focused.log`). **No post-fix headless rerun** was performed, respecting the owner's resource limit; the airborne live regression remains unconfirmed after the fix. Its designated failure world/logs are retained. Earlier broad-suite failures remain unresolved.

Artifact `build/libs/moonstation14-0.0.1.jar`: **5,326,416 bytes**, UTC mtime `2026-10-07T04:00:43.0145225Z`. Connected ride smoothness and outlet momentum still need confirmation with this jar on both sides. Ordinary ride damage is not required by native defaults; no generic launch sound or unconditional injury was added.

### Idle-input containment and APC follow-up (October 6 local)

Connected testing reached “Entered” but retained free movement and no contained camera. The concrete source defect was the UNIT escape predicate in `GroundedHarnessWorldStep`: CHARACTER input sets the run-mode bit even while idle, and that bit alone invoked escape immediately after insertion. Escape now requires directional or jump input, not a gait selection. The real HUMAN entry regression feeds repeated eligible idle-run world steps after actual unit insertion and verifies continued ownership, pinning, view, identity and hands. Connected camera/F5 behavior still needs confirmation; this does not add client self-entry prediction.

Separately, APC body-menu toggles incorrectly used the spectator carrier's build permission and reach. They now use freshly revalidated controlled-body authority, action capability and reach, with contained external mutation denied. Ordinary player permission/range checks remain unchanged. Empty charge does not prevent switching the breaker; no charge or power rules were changed. Policy tests cover actor selection, while the native APC test verifies trusted-core off/on switching at zero joules, revision changes and permission/range refusal—not authenticated menu packet handling.

Fresh normal `test build` passed **2,200 JUnit tests**, zero failures/errors (`build/disposals-idle-breaker-build.log`). Focused native GameTests passed **5/5**, including the two complete rides, timed-entry/cancellation and zero-charge APC core (`build/disposals-idle-breaker-gametest.log`). Independent source reviews passed. Earlier full GameTest failures remain unresolved; this is not a full-suite pass.

Artifact `build/libs/moonstation14-0.0.1.jar`: **5,317,542 bytes**, UTC mtime `2026-10-07T03:10:31.6184866Z`. The designated disposable `build/gametest-disposal-focused-01/world` was removed; logs/configuration remain. Retest with this jar on both sides: tap/release K while stationary, wait half a second without movement/jump, and check third-person/F5 lock; separately switch the empty APC breaker off/on. Directional movement intentionally attempts unit escape.

### Self-entry clock-driver follow-up (October 6 local)

The reported K-key climb can remain accepted without reaching its terminal transition if completion depends on the physical carrier's player tick. Self-entry progress now advances from server post-tick over pending and online actors, so it does not rely on that carrier ticking. The legacy menu path uses the same timed insertion engine. The delay comes from the resolved unit prototype's `worldGameTime` duration (0.5 s); completion messaging is emitted only after verified insertion.

Fresh `test compileGameTestJava` passed **2,197 tests**, zero failures/errors (`build/disposals-self-entry-clock-junit.log`); `build` passed (`build/disposals-self-entry-clock-build.log`). Focused native GameTests passed all four required tests in 2.498 s (`build/disposals-self-entry-clock-gametest.log`), including entry timing/real unit binding, unpowered/no-player-tick progression and movement/context cancellation. A reflection test verifies the `ServerTickEvent.Post` driver signature and registration. This automated coverage does not prove authenticated connected-client K behavior, exact physical-player-tick absence, or an end-to-end client result. It does not introduce FakePlayer authority bypass; raw-unit, level, control and range checks and cancellation on movement/damage remain in place.

Artifact `build/libs/moonstation14-0.0.1.jar`: **5,315,721 bytes**, mtime `2026-10-07T02:35:51.8629974Z`. Only the approved disposable `build/gametest-disposal-focused-01/world` was removed after all four targets passed; logs/configuration and other worlds remain untouched. Brief connected-client retest remains outstanding: replace the jar on both sides, tap/release K while stationary and wait 0.5 s. Expect the terminal message and third-person/F5 lock with body immobile; if it still only says “Climbing,” capture the new server started/completed/cancel line. No user confirmation or end-to-end fix is claimed.

### Self-entry follow-up (October 6 local)

Fresh `test compileGameTestJava` passed **2,196 JUnit tests** with zero failures/errors (`build/disposals-self-entry-junit.log`); `build` passed (`build/disposals-self-entry-build.log`). Focused native camera-tour GameTests both passed in 2.583 s (`build/disposals-self-entry-gametest.log`). They use trusted insertion and do **not** exercise authenticated K input. The connected K roof/free-movement symptom is therefore still unverified; see `interaction-checks.md` for the concise retest and verified policy behavior. These results do not clear or explain prior full-suite failures.

Artifact `build/libs/moonstation14-0.0.1.jar`: **5,309,677 bytes**, mtime `2026-10-07T01:43:53.1819797Z`. Only the approved disposable `build/gametest-disposal-focused-01/world` was removed after focused targets passed. Logs/configuration and other historical worlds are retained.

### Accepted focused camera-tour evidence (October 5 local / October 6 UTC)

Fresh `test compileGameTestJava` passed **2,194/2,194 JUnit tests** (zero
failures/errors); full build passed. Native focused GameTest run passed both
required disposal camera-tour tests. Logs: `build/disposals-camera-tour-accepted-junit.log`,
`build/disposals-camera-tour-accepted-build.log`, and
`build/disposals-camera-tour-accepted-gametest.log`.

Both three-ingot stacks and the bound HUMAN traversed all **259/259** cells and
all **10/10** supported route types. HUMAN mind/UUID/hands remained bound, and
native `INHALING`→`EXHALING` breathing withdrew finite O2 from the same-owner
holder. CO2 poison remained within measured withdrawals and resolved lung-policy
limits; there was no other native damage, the HUMAN survived, and ejection was
indoors at the safe outlet. This verifies automated server behavior, not the
connected-client camera view.

The latest *full* 519-test run is not green: its six failures included
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`realblockcontactslipsboundfakeplayerandvillageronlyonce`,
`disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
`onlycompletedpaidsolvemovesconservedgas`, and the bound-HUMAN assertion that
was subsequently corrected. Causes for the other failures are not established;
they are not waived or claimed unrelated. The focused pass is deliberately not
presented as a full-suite pass.

Artifact `build/libs/moonstation14-0.0.1.jar`: **5,306,968 bytes**, mtime
`2026-10-06T05:14:52.2383129Z`. Only the approved disposable world
`build/gametest-disposal-focused-01/world` was removed after all focused targets
passed. Other historical test worlds were left untouched; logs are retained.

### Bound-human inhale assertion correction (October 6)

Historical, superseded by the accepted focused result above.

The camera-tour test now pairs successive finite-air samples from the actual
containment owner with the native respiration phase, and requires a measured
O2 decrease across `INHALING` → `EXHALING`. It also confirms that the selected
exposure mixture equals the holder's finite sampled mixture. This avoids the
invalid old whole-ride net-O2-loss requirement: exhalation returns the stored
gas unchanged, while blood O2 metabolism is separate.

Native SS14 HUMAN respiration can accumulate small CO2 poison damage when
finite closed air is rebreathed (pinned `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
`Content.Shared/…/Metabolism/Systems/MetabolismSystem.cs` and
`Content.Shared/…/RespiratorySystem.cs`). This is not a zero-damage acceptance
case: the local `organ_lungs_human.json` explicitly adapts toxicity to a typed
CO2 poison-per-mole rate and per-inhale cap, rather than claiming native
per-mole parity. The fixture therefore permits poison as a trace category while
still rejecting other damage and requiring the HUMAN to remain alive. Its
quantitative bound/accounting is not yet demonstrated; no final PASS is claimed.

Fresh `test compileGameTestJava` passed **2,193/2,193 JUnit tests** (374 XML,
zero failures/errors/skips). A fresh designated GameTestServer run completed 519
tests with **4 required failures**: `boundhumanrideswholepowereddisposaltourwithfiniteair`
failed because no sampled `INHALING` → `EXHALING` transition was observed during
ownership (`last transition: null`; the finite source samples remained
0.5524593273809555 mol). Also failed:
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`localextensionreceiverrespectsradiusandcablecuts`, and
`poweredscrubbercapturesonlyselectedgas`. Stack/tour, no-native-damage, same
UUID/mind/hands and safe indoor outlet acceptance is **not proven**. No
production lung, exposure, metabolism or timing behavior changed. Full log:
`build/disposals-camera-tour-inhale-gametest.log`. Disposable world retained
because target tests did not pass. `build` succeeded (UP-TO-DATE); ready jar:
`build/libs/moonstation14-0.0.1.jar`, 5,306,809 bytes, mtime
`2026-10-06T04:35:37.0172527Z`.

### Bound-human finite-air fixture follow-up (October 5)

The disposable `build/gametest-disposal-focused-01/world` was reset and run
fresh after initializing the enrolled HUMAN's hands with the normal
`BodyHandBootstrap.ensure` lifecycle service. This resolves the prior setup
failure; the actual bound-human ride now reaches its native-air assertion after
visiting the tour. It still fails meaningfully: the global breath phase changed,
but exposure-source oxygen remained **0.5524593273809555 mol →
0.5524593273809555 mol**. No reservoir was increased and no native damage or
air-ledger assertion was weakened. The exact unresolved exposure/breath
accounting path needs follow-up before claiming the bound-HUMAN target passes.

Fresh `test compileGameTestJava` passed **2,193/2,193 JUnit tests** (374 XML
files, zero failures/errors/skips); `build` passed. Artifact
`build/libs/moonstation14-0.0.1.jar`: **5,306,809 bytes**, mtime
`2026-10-06T04:35:37.0172527Z`. Logs: `build/disposals-camera-tour-junit.log`,
`build/disposals-camera-tour-build.log`, and
`build/disposals-camera-tour-human-gametest.log`.

The fresh server run completed **519** tests with six required failures:
`boundhumanrideswholepowereddisposaltourwithfiniteair` (above),
`connectedcablepathlamploadtripsapcandmanualrecloserestoresit`,
`realblockcontactslipsboundfakeplayerandvillageronlyonce`,
`disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
and `onlycompletedpaidsolvemovesconservedgas`. Other fixture/power/stack setup
targets passed. The failures outside the bound-human target remain unresolved
and are not claimed fixed or waived. The disposable world is retained because
the designated targets did not all pass.

### Isolated camera-tour GameTest retry (October 6)

Fresh `runGameTestServer -Pms14GameTestDir=build/gametest-disposal-focused-01`
started 519 tests and ended with **4 required failures** (Gradle exit 1).
Both disposal fixture targets failed at the power gate: `gate=true`, lamp lit,
but the unit remained unpowered/`READY` was not reached; the 1000 W meter was
unknown and delivered 0 W. The diagnostics report one disconnected route
sub-port (`14/true->-1/false`) and APC ports ending `-1/false`; natural unit
power is still the blocker despite the fixture gate. The bound-human tour never
started, so stack-259 ride, preserved ingots, safe indoor outlet, bound HUMAN
identity/hands, finite native holder air/breaths, O2 ledger and no-damage claims
remain unproven. Other required failures were `alertapplicationclearandexpiry`
and `onlycompletedpaidsolvemovesconservedgas`; neither is waived. Full log:
`build/disposals-camera-tour-isolated-gametest.log`. Designated world retained
because fixture targets did not pass. No jar build was run.

### Camera-tour fixture/body integration attempt (October 6)

`./gradlew.bat test compileGameTestJava` passed **2,193/2,193 JUnit tests**
(374 XML files); `./gradlew.bat build` passed. Artifact:
`build/libs/moonstation14-0.0.1.jar` (5,305,599 bytes, mtime
`2026-10-06T04:04:54Z`). Logs:
`build/disposals-camera-tour-{junit,build}.log`.

The latest designated full GameTestServer run started **519 tests** and ended with
**8 required failures**. Both disposal-fixture integration tests failed before
power/ride: builder returned `PARTIAL` because finite-room initialization's
`fixtureRoomValid` check rejected the just-built enclosure/reciprocal route.
This includes `boundhumanrideswholepowereddisposaltourwithfiniteair` and the
existing `fixturebuildsinitializespowersandflusheswholestack`; the cause within
that validity check is not isolated, so this is a production fixture/route
blocker and is not waived. The character test's breath-source/O2-consumption,
damage-ledger, full-tour and safe-outlet assertions therefore remain unproven.
The first attempt also used a nonexistent `disposal_clear32x8x32` test template;
that was corrected to the checked-in `disposal_clear`. A second run confirmed
the fixture rejection independently. The follow-up restores power/atmosphere
test settings on all assertion failures; the latest six other failures are
`builtfixtureinitializesandreportsresolvedruntime` (remote footprint not
empty), `movesbetweentwoworldhosts`, `itemoverlappingeyeremainsvisibleatzerodistanceblockhit`,
`alarmusesinheritedhundredwattsandradiusthree`, and two paid ventilation
fixtures; causes are not established. Log:
`build/disposals-camera-tour-gametest.log`. The test world is retained because
the target run failed. No connected-client view/camera claim is made.

### Bounded finite-fixture proof and interaction validation (October 6)

Trusted authored seeding proves the complete passable component (including
already-finite cells) before claims or gas writes. Candidate capacity includes
blocked boundary probes; validated no-op writes and repeated initialization
succeed. A GameTest verifies that out-of-input connected cells are rejected
without claims or gas changes. Fresh `test compileGameTestJava` passed
**2,184/2,184 JUnit tests** (370 XML files). `build` passed; artifact
`build/libs/moonstation14-0.0.1.jar` is 5,271,875 bytes (mtime
`2026-10-06T00:20:28Z`). Logs: `build/disposals-interaction-proof-{junit,build}.log`.

Full GameTest: **512/515 passed**. Focused passes: bounded-proof rejection,
`fixturebuildsinitializespowersandflusheswholestack` (auto-seeds finite breathable
air, reinitializes existing claims, powers the unit and flushes the whole stack),
`futurePersistedClockRebasesBeforePowerCallback`, and
`successfulBodyAdmissionPlaysResolvedInsertionSoundOnlyOnce`. Three failures:
`poweredventmetertracksbreakerandreplacement`,
`arbitrarystonehostreplacementeventuallystopsenergizedcable`, and
`twowireddebuglampstripandgodarkthenreclose`; causes are not established or
waived. Log: `build/disposals-interaction-proof-gametest.log`. The disposable
GameTest world was removed after all targeted tests passed. Connected-client
audio and climb-input checks remain unverified; see `interaction-checks.md` and
`timing-clock.md`.

Not yet accepted as a completed sprint. Connected checks are in
`client-checks.md`; deliberate scope limits are in `architecture.md`.

### Landing-step movement follow-up (October 5)

The temporary administrative loaded-body recovery command and its supporting
test/documentation were removed: disposable test worlds do not justify a repair
workflow. The operator recovery path remains absent. The earlier 2,184-test
result is historical and included five tests for that removed command.

Fresh validation after removal: `./gradlew.bat test compileGameTestJava` exited
0; **2,179 JUnit tests passed**, with zero failures/errors/skips and 368 fresh
XML results. Movement motor (37) and harness (9) tests passed. The actual build
exited 0; artifact `build/libs/moonstation14-0.0.1.jar` is 5,253,289 bytes,
mtime `2026-10-05T10:40:35.7174620Z`. Logs:
`build/disposals-landing-prevention-{junit,build}.log`.

The latest live GameTest run remains **509/512 passed**; all **33/33 disposal tests passed**,
including both owned-world hurt-landing tests (descending landing onto a
resolved disposal pipe and from a resolved pipe onto a resolved trunk). The
bounded motor review passed. Three selectors
failed: `blockedforwarddropstaysnearside`,
`realblockcontactslipsboundfakeplayerandvillageronlyonce`, and
`poweredscrubbercapturesonlyselectedgas`; causes remain under read-only
investigation, so this is not a green full suite and they are not claimed as
unrelated. The first GameTest run's fixture coordinate spill was corrected;
the final run kept new test writes within its 32×8 bounds. Test world removal
occurred only after server exit; the first-run log is preserved as
`build/disposals-landing-recovery-gametest-first.log`. The temporary test world
was removed. The movement kernel, world-step and fixture code were unchanged
during recovery removal, so this existing run is reused rather than repeating
the expensive full server suite. The three failures remain unresolved and are
not claimed as proven unrelated. Connected hurt-landing/jump checks remain
pending; world/character repair is not a goal.

**Latest fresh validation (October 5):** implementation gates passed; the full
GameTest run is not green and connected-client acceptance remains outstanding.

- `build/disposals-usability-regressions-junit.log`: started
  `2026-10-05T07:29:50.0922341Z`, exit 0; **2,178 passed**, zero failures,
  errors or skips. All 368 XML results were newer than the run. Prototype cache:
  72 cold lookups 0.099–5.019 ms; 1,000 warm lookups 0.134–1.846 ms.
  Immutable-cache and 1/16 grid-bound tests passed.
- `build/disposals-usability-regressions-build.log`: `./gradlew.bat build`
  exit 0; artifact `build/libs/moonstation14-0.0.1.jar` (5,252,754 bytes).
- `build/disposals-usability-regressions-gametest.log`: **504/510 passed**;
  all **31/31 disposal tests passed**. Six failed selectors:
  `poweredventmetertracksbreakerandreplacement`,
  `lampextensionreceiverusesnearestknownlvcablewithinthreeblocks`,
  `itemoverlappingeyeremainsvisibleatzerodistanceblockhit`,
  `disableddevicedoesnotmovegas`, `poweredscrubbercapturesonlyselectedgas`,
  and `onlycompletedpaidsolvemovesconservedgas`. They are outside the disposal
  batch; causes remain under read-only investigation, not established or waived.
  All three logs are preserved; the test world was removed only after server exit.

Independent reviews passed for cache bounds, placement replay, fixture behavior,
and role visuals (including signed physical-arm output, collar area/winding and
normals). Native asset license credits were retained. Connected-client appearance,
input and lag checks remain pending manual acceptance. This is not a green full
suite or completed sprint.

The construction-family follow-up compiled main/test/GameTest sources and
passed fresh unfiltered **2,153 JUnit tests** (no failures/errors/skips),
`disposals-family-junit-03.log`. The first full family regression run completed
**505/509**, with **30/30 disposal tests** passing. Failures were negative-stack
drying/cleanup and three paid-ventilation fixtures without known meters:
`disposals-family-gametest-01.log`. The source-frozen retry completed
**502/509**, again **30/30 disposal tests** passing:
`disposals-family-gametest-02.log`. Seven non-disposal failures were exterior
atmosphere preconditions, alert expiry, vent-meter breaker/replacement,
lamp-extension reach, stone-host cable replacement (both reach diagnostics
threw substring-range errors), post-lease vanilla movement, and paid ventilation
conservation. Causes remain unresolved; none is waived as proof of a green full
suite. `disposals-family-final-build.log` records a successful actual build;
`jar` executed, while test/compilation were up-to-date against the fresh green
results. Artifact: `build/libs/moonstation14-0.0.1.jar` (5,188,555 bytes).

G/H/R controls, non-container palette, material HUD, neighbour-aware rotation,
and orientation-only screwdriver workflow passed independent source reviews.
New tests cover the actual runtime state reducer, payloads, material/count/time
resolution, semantic orientation candidates, readiness and rotation confirmation
policies. Authenticated key/input and timed-placement/rotation still require a
connected client; no FakePlayer authority bypass was introduced merely to obtain
a headless test. The full regression run includes two unrelated test-fixture
worktree edits excluded from the planned disposal commit; production/JUnit
sources are the staged feature sources.

## Latest recorded evidence

### Rounded-model and fixture-origin follow-up

World pipes and previews now use shared octagonal mesh geometry and a derived
native-palette material instead of 2D pipe sprites on cuboids. Rounded collision
arms and joins consume resolved diameter/ports; opposite straight runs have no
extra hub. Vertical joins are welded rather than drawing overlapping opaque
facets. Fixture command origin is now its clear standing-air entry, and
coordinate-free follow-ups reuse the operator's last successful build site.
The room includes a real APC-powered lamp without blocking the unit's finite
air sample above it.

Fresh automated gates passed:

- `disposals-round-models-junit-05.log`: **2,169 passed**, no failures;
  main, test and GameTest compilation succeeded.
- `disposals-round-models-gametest-02.log`: **509/510 passed**, including
  **31/31 disposal-batch tests**. The real fixture builds at its standing-air
  origin, initializes finite air, powers its 1000 W unit and 100 W lamp, and
  transports one whole named three-item stack with positive finite holder air.
  The sole failed selector was outside disposals:
  `onlycompletedpaidsolvemovesconservedgas` (`PaidVentilationFlowGameTests`).
  That ventilation failure remains unresolved; the full server suite is not green.
- `disposals-round-models-final-build.log`: actual full build succeeded.
  Artifact: `build/libs/moonstation14-0.0.1.jar`, **5,227,949 bytes**.

Earlier unit attempts exposed stale face
counts, signed-axis cap assertions and incorrect cap normals; corrections keep
the normal/winding, cap-plane, corner-clearance and coplanar-overlap checks.
An initial live fixture exception exposed a missing lamp dispatch case and
test-setting leakage; both were corrected. Setup/deferred failures now restore
their original simulation settings, and the final run had no reaction-isolation
failures. The lamp remains beside, not in, the unit's required above-cell air source.
Connected model/ghost readability and fixture-enter camera prediction remain
human checks even after automated gates pass.

### Usability follow-up

Independent source reviews cover held-key/exclusive-palette rendering,
blocker-aware item pickup priority, the catalogue-only pipe icon, authoritative
unit-item placement, shared multipart unit models, and the bounded powered
fixture. New fixture coverage exercises the actual build/initialize service,
paid power, finite flush air and whole-stack ejection. Compilation initially
exposed command-parenthesis and Minecraft API/test-property mistakes; those
were corrected. Fresh automated results for these follow-ups must be recorded
before treating the fixture as proven working. Those fresh gates now passed:

- `disposals-usability-junit-05.log`: **2,160 passed**, no failures/errors/skips;
  main, test and GameTest compilation succeeded.
- `disposals-usability-gametest-03.log`: **507/510 passed**, including **31/31
  disposal-batch tests**. The new `fixturebuildsinitializespowersandflusheswholestack`
  exercises actual fixture build/finite initialization, paid 1000 W power,
  positive finite holder air and one whole named three-item stack at the outlet.
  All three failed selectors map outside the disposal batch:
  `builtfixtureinitializesandreportsresolvedruntime` (remote footprint not empty),
  `poweredventmetertracksbreakerandreplacement` (unknown paid meter), and
  `poweredscrubbercapturesonlyselectedgas` (no positive paid ventilation solve).
  Their causes remain unresolved; this is not a green full-server suite.
- `disposals-usability-final-build.log`: actual full build succeeded. Artifact:
  `build/libs/moonstation14-0.0.1.jar`, **5,212,331 bytes**.

The first live fixture gate exposed a mistaken UP transport port on the trunk;
the unit inlet is separate from its sole routed port. The second exposed a
500-cell room exceeding the existing 1,024-probe finite-endpoint proof limit.
The fixture uses a 96-cell play room within the 1,024-probe trusted finite-room
proof budget, including its connected gas-passable tube/unit cells; gas proofs,
power authority and reservoir capture are unchanged. Resolved-port/layout regression
tests and the final live flush test cover these corrections.

Connected palette/pickup input, authenticated unit placement, the revised bin
appearance, and fixture `enter` camera/prediction remain human checks. Unit
crafting remains deferred, not delivered by the catalogue placement adaptation.

- `disposals-spatial-junit-06.log`: fresh unfiltered **2,132 passed**, no
  failures/errors/skips; main, test and GameTest compilation succeeded.
- `disposals-spatial-gametest-02.log`: **508/509 passed**, including **30/30
  disposal tests**. Sole failure was a powered scrubber's unknown paid meter.
- `disposals-spatial-gametest-03.log`: source-frozen **505/509 passed**, again
  **30/30 disposal tests**. The scrubber passed; remaining failures were exterior
  atmosphere override preconditions, alert expiry, vanilla movement after lease
  close, and local extension receiver/cable-cut reach. These are non-disposal
  selectors; their causes are not established or waived by this sprint.
- `disposals-spatial-final-build.log`: actual `jar` and `build` succeeded;
  `test`/`check` and compilation were up-to-date against the fresh green results.
  Artifact: `build/libs/moonstation14-0.0.1.jar`. This does not clear the separate
  full-server failures.

Coverage includes live exposed block ownership, floor-host rejection, sealed
wall admission/replacement/same-state invalidation, inherited/custom geometry,
six-axis frames and routing, natural vertical/bend traversal, DOWN unit inlets,
broken-trunk return, safe ejection, exact body/camera pin position and persisted
holder identity mismatch. Source reviews passed placement/preview authority,
resolved geometry consumers, renderer/texture resources and host/ownership fixes.
The pipe block is intentionally itemless; construction does not produce a
portable BlockItem. The earlier floor-overlay suite is historical only.

Earlier failure logs remain under `build/disposals-*.log`. They exposed and
helped repair fixture bounds, NBT encoding, readiness scheduling and incorrect
gas accounting. The gas test now sums distinct withdrawal and release cells
plus holder inventory; it does not assume a cell endpoint is a whole-room sum.
Natural-tick traversal exercises resolved ports, actual bends and ejection.

## Outstanding acceptance

Connected-client checks remain unperformed. Independent source review passed
destruction gas ownership handoff and passive held-fuse menu authority, but
live spawn-rejected destruction/fallback and bound-owner held-explosion
collection still lack dedicated integration tests. SavedData round-trip and
expiry ordering tests are narrower evidence, not substitutes for those paths.
Normal unload/reload camera and original-flag behavior still needs connected
verification. Latest automated results above supersede earlier gate counts, but
remaining connected evidence and unresolved full-suite failures still preclude
sprint acceptance.

## Execution invariants

The shared `empty.nbt` is only **3×1×1**. Disposal layouts extending to local
X/Z 6 cannot use that footprint: boards may overlap and preparation does not
declare their real loaded region. New disposal tests now use `disposal_clear`
(32×8×32). The real power fixture uses `disposal_power_clear` (48×8×48), with
its layout offset +16 X/Z so the receiver search stays inside its bounds.
These are fixture bounds, not permission to force chunks in gameplay.

Only one designated Gradle/server validator runs at a time. The configured
GameTestServer is headless and does not bind a TCP port (verified against the
cached Minecraft `GameTestServer.initServer` source); another development
server on 25565 is not a port conflict and is not stopped.

Use `-Pms14GameTestDir=build/gametest-disposal-focused-01`. No established
per-method Gradle filter exists, so server validation runs all registered tests.
Retry with only that disposable fixture world reset. Retain failure logs,
never reset another workspace's server/world or weaken loaded-world guards.

Logs are under `build/disposals-*.log`; prior server failure logs are preserved
separately from the reused run directory. Compilation and isolated tests do
not establish connected camera, presentation, or menu behavior.
