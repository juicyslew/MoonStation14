# Native suite stabilization

Latest status: production pipe redistribution is fixed and independently
reviewed; full native certification remains open. All fourteen original checks
passed together in full run 02. Expanded focused coverage is being extended beyond
the 29 passing checks for the remaining full-load fixture failures.

## Scope and baseline

Separate stabilization task after construction closure and merge commit `82ff81f`.
Address the fourteen failures from the merged full native run, not deferred gameplay
features. Baseline: **666/666 selected, 652 passed, 14 failed, zero incomplete**;
all **2,732 JUnit tests** passed. Evidence:
`build/construction-main-merge-gametest.log` and the retained
`build/gametest-construction-main-merge-fixture` world.

## Work units

1. Power graph/meter fixture readiness and real paid ventilation intervals (seven checks).
2. Pull admission geometry and FakePlayer lifetime/nearest-player isolation (five checks).
3. Finite gas ownership, airlock conservation and intake vacuum setup (two checks).

Fix actual causes. Preserve production authority, fail-closed loaded topology,
single-use paid work, conservation, resolved prototypes and accepted pulling physics.
Do not reinstate simulation switches, bypass admission or weaken assertions to pass.
The `suite-stabilization` tag selects the affected methods; tags are not isolation.
Focused native validation precedes an unfiltered full suite. Runs use disposable
fixture directories, never owner configuration; preserve useful failed artifacts.

## Results

- First diagnostic native run: **filtered 12/666 selected and completed,
  7 passed, 5 failed, zero untagged**. Three intended regressions were missing the
  shared selector; their annotations are corrected for the next run. The extra
  tether control passed. Log: `build/suite-stabilization-focused-01.log`.
- Selected dead-target, jump, chain and despawn checks passed after fixture aim
  and FakePlayer-lifetime changes. Detached-body coverage still awaits rerun.
- APC overload diagnostic showed a known connected graph and an already-tripped
  APC: requiring the latest global solve string was invalid. Gas diagnostics
  showed passive redistribution into the unmeasured, passable device cell.
- A pure capped-transaction regression with locally resolved ventilation
  prototypes passes for 0.05 s and 1.0 s intervals (**6/6 JUnit checks**, zero
  failures, errors or skips). Reference-plan float rounding
  has **not** been established as a runtime defect; no production change was made
  for that hypothesis. An earlier attempt failed on missing test-local catalog
  initialization, which was corrected. Log:
  `build/suite-stabilization-transfer-unit.log`.
- Source review passed for pull helper reuse, own-FakePlayer cleanup and shared
  trusted finite-room setup. Power/gas review rejected fixed eight-ULP budgets;
  operation-derived error accounting is being corrected before acceptance.
  Level-wide operation counts were also rejected: unrelated fixtures must not
  inflate a measured control volume's conservation allowance.

- Second native run: **filtered 16/666 selected and completed, 13 passed,
  3 failed, zero untagged**. All fourteen original regressions are now selected,
  alongside two controls. All pull/death/despawn, airlock conservation and intake
  checks passed. Log: `build/suite-stabilization-focused-02.log`.
- The two remaining paid-flow checks observed the first known meter, before a
  completed electrical interval exists. `PowerRuntime` creates a gas budget only
  from successive compatible receiver signatures; a meter is not that budget.
  The fixture wait is being corrected, not the production billing rules.
- The operator fixture uses an absolute remote origin `(200,20,200)`. Reusing its
  saved world retained the previous fixture, failing the fresh-footprint
  prerequisite before gas checks. This is not a gas-runtime failure.
- Final source review accepted fixture-local precision bounds, complete room
  accounting, removal of global dimension changes and bounded power readiness.
- Third native run: **filtered 16/666 selected and completed, 15 passed,
  1 failed, zero untagged**. The stable completed-interval wait fixes both paid-flow
  checks. Log: `build/suite-stabilization-focused-03.log`.
- Remaining operator-fixture failure is a sensitivity guard: its conservative
  source rounding allowance (0.482 mol) exceeded the captured 0.400 mol packet;
  no actual gas loss was reported. An additional pipe-component stability
  invariant can distinguish prohibited capture at the packet's smaller float
  scale while retaining closed-source and whole-volume conservation checks.
  Relative phase scheduling is also being corrected for delayed initialization.
- Fourth native run: **filtered 16/666 selected and completed, 15 passed,
  1 failed, zero untagged**. The operator captured 0.400 mol, but no gas reached
  its recipient through the far vent within the observation window. Log:
  `build/suite-stabilization-focused-04.log`. Concealed-pipe equalization is being
  traced; successful single-node paid-flow tests do not certify that longer path.
  Review also requires matching conservation snapshots to their counted phase
  and restricting pipe no-flow assertions to the disabled-vent phase.
- Final focused native run: **16/666 selected and completed, 16 passed,
  zero failures, zero untagged**. This covers every original failure plus two
  controls. Log: `build/suite-stabilization-focused-07.log`.
- Verified production cause: `PipeGasInventory.withBatchMixtures` rejected a
  valid eleven-node 0.4-mol Plasma redistribution. Its double-precision allowance
  was approximately `4e-15`; the actual float-storage delta was `2.235e-8`, below
  the ledger's existing float allowance `2.384e-7`. Batch validation now reuses
  `PipeGasLedger`'s finite float-storage checks. Owner activity, incarnation,
  expected version and atomic inventory revision checks remain unchanged.
- Focused pure regressions **23/23 passed**, including the real immutable batch
  consumer, rejected aggregate float overflow and locally resolved default
  pressure-vent settings. SS14 vent pumping uses pressure change and elapsed time;
  scrubber volume rate does not apply to it. Verified against pinned
  `c9df5ef5d675b0d1d226828bddf6b78c28502d91`,
  `GasVentPumpSystem.cs:101–156` and `GasVentScrubberSystem.cs:91–127`.
  Existing vent math and prototype defaults were not changed.
- Detached corpse coverage now establishes canonical medical-physics eligibility,
  sustained tautness and nonzero tether impulse before requiring movement. Its
  controlled native input is bounded; no production pulling physics changed.
- Final independent source reviews passed shared ledger reuse/finite overflow,
  packet-scale transport conservation, phase timing and corpse preconditions.

Initial compile attempts exposed two incomplete fixture edits, both repaired
before the diagnostic native run.

## Final broad validation

- Full `cleanTest test build compileGameTestJava --max-workers=4` passed:
  **2,736/2,736 JUnit tests**, 493 suites, zero failures, errors or skips.
  Log: `build/suite-stabilization-full-build.log`.
- First unfiltered native run: **666/666 selected and completed, 661 passed,
  5 failed, zero untagged**. Log: `build/suite-stabilization-full-native.log`.
  Thirteen original regressions passed. The remaining radius-four cable check
  still sampled before its node was known. Four additional checks failed: Critical
  passive fall, contained-refusal whole-stack drop, airlock auto-close timing and
  repeated log pulse. These names do not establish causal regressions.
- Follow-up fixtures wait for the actual cable node, use resolved airlock
  deadlines/fresh pulse admission, establish passive-physics prerequisites, and
  distinguish newly published drops from pre-existing matching world entities.
  The shared selector includes these full-run failures for the next validation.
  Full certification remains open until the unfiltered run passes.
- Expanded focused checks select **20/666**. A reused saved run produced four
  setup/admission failures; a fresh directory reduced this to **19 passed,
  1 failed**. All four added full-run regressions passed there. The remaining
  detached-body admission was intermittent; its shared fixture aiming helper
  now targets an inset interior interaction point rather than an exact edge.
  Production selection/range/visibility rules are unchanged. Fresh rerun pending.
  Logs: `build/suite-stabilization-focused-08.log` and
  `build/suite-stabilization-focused-09.log`.
- Exact detached-pull diagnostics subsequently established the rejection:
  the canonical ray hits the eligible corpse, but a real block obstructs the
  line of sight. Its `empty` template did not reserve its authored 14×7 platform
  and traversal corridor. The fixture is being moved to an existing adequately
  sized template, not clearing foreign blocks or relaxing selection. Interior
  aiming alone was not the cause/fix. Diagnostic predicates are shared directly
  with the gameplay service through package-private access; admission logic is
  unchanged. Log: `build/suite-stabilization-focused-11.log`.
- With the detached fixture's declared **36×5×8** template, fresh focused
  validation passed **20/20 selected of 666**, zero failures or untagged tests.
  Log: `build/suite-stabilization-focused-12.log`.
- Second fresh full native run: **666/666 completed, 665 passed, 1 failed**;
  **all fourteen original regressions passed**. The sole remaining failure is
  Critical passive fall. Its actor/floor also exceeded the tiny declared template;
  fixture bounds/native-tick diagnostics are being corrected before certification.
  Log: `build/suite-stabilization-full-native-02.log`.
- Third fresh full run: **666 completed, 663 passed, 3 failed**. Critical fall
  passed after reserving its full falling-body space. Chain admission again
  encountered the same undersized-template risk; its fixture now also reserves
  the existing larger space. Timed world ingestion and negative-stack drying
  exposed two additional timing failures; their occupied bounds and due clocks
  are being checked without changing gameplay intervals or continuation rules.
  Log: `build/suite-stabilization-full-native-03.log`.
- Expanded focused run: **22/666 selected, 21 passed, 1 failed**. World-source
  continuation passed; negative drying exposed a fixture sequence bug:
  `thenWaitUntil` callbacks returned normally before the due state, which advances
  the sequence rather than waiting. The first observation timestamp stayed `-1`.
  Wait predicates now need assertion failures until the actual due state, and
  both observations must use the canonical game-time buckets rather than an
  observed timestamp plus 20. Log: `build/suite-stabilization-focused-13.log`.
  The invalid early returns also exist in merge commit `82ff81f`; this was not
  evidence that the production drying interval changed.
- Corrected expanded focused validation passed **22/22 selected of 666**, zero
  failures/incomplete/untagged tests. Independent review verified canonical
  bucket timing and assertion-based waiting. Log:
  `build/suite-stabilization-focused-14.log`.
- Fourth fresh full run: **666 completed, 663 passed, 3 failed**. All expanded
  focused targets passed. New failures concerned reactive-touch sliding movement,
  the exact two-cell doorway obstruction probe, and a crush/reopen cycle.
  The airlock checks already use a purpose-sized power template; their failures
  do not support enlarging the shared `empty` template. Reactive touch exceeds
  the small template's declared space and is being moved to the existing larger
  fixture. Each airlock case is being traced independently.
  Log: `build/suite-stabilization-full-native-04.log`.
- Expanded focused run: **25/666 selected, 24 passed, 1 failed**. Both new
  airlock probes passed and their source review accepted exact doorway geometry
  and prototype-resolved reopening/fast-close deadlines. Reactive slip stopped
  at its air-corridor prerequisite: Y=1 contains stone. Its contact planes and
  actor clearance must be aligned above the actual fixture floor, not treating
  every declared template cell as air.
  Log: `build/suite-stabilization-focused-15.log`.
- The larger NBT is an air-only palette with no explicit block list; observed
  Y=1 stone was not a uniform authored floor. The slip fixture must build its
  own support plane inside its reserved bounds, preserving existing stone and
  filling air, rather than requiring every support cell already be stone.
  Focused run 16 retained **24 passes, 1 setup failure**; other targets stayed
  green. Log: `build/suite-stabilization-focused-16.log`.
- Focused run 17: **25 selected, 24 passed, 1 failed**. Reactive touch passed
  both actual movement events and unchanged sliding-dose assertions. The crush
  probe sampled its one-tick OPEN window one tick late: expected OPEN at 812,
  observed CLOSING started at 813. Runtime timing matches the resolved one-tick
  fast auto-close; helper-relative deadline translation is not a reliable witness
  of that short state. Observation is being corrected at the native tick boundary,
  without extending the gameplay window.
  Log: `build/suite-stabilization-focused-17.log`.
- The crush fixture now uses a scoped, read-only `LevelTickEvent.Post` observer
  for the real one-tick OPEN/CLOSING snapshots. Assertions remain in the GameTest
  trace; bus registration has completion/failure/fallback cleanup. No production
  timer, state transition or prototype value was changed. Runtime rerun pending.
- Final expanded focused validation: **25/666 selected and completed, 25 passed,
  zero failed/incomplete/untagged**. Independent review passed native-tick
  observation and listener cleanup. Log: `build/suite-stabilization-focused-18.log`.
  The next fresh unfiltered run and final clean unit/build validation are running.
- Fifth fresh full run: **666 completed, 661 passed, 5 failed**. Four additional
  generic-`empty` users failed paralyze expiry, alert expiry, oral healing and
  pickup; the crush trace ran out of callback-count budget after observing the
  correct OPEN/CLOSING states. This is not a green full certification.
  Log: `build/suite-stabilization-full-native-05.log`.
- Current-use audit supports correcting the shared blank fixture's reservation:
  its tiny size does not even enclose ordinary actors at `(1,1,1)` or
  `(2,1,2)`. A **16×10×16** reservation is being evaluated for existing generic
  callers, preserving the blank payload and specialized fixtures. Unloaded-edge
  assumptions must be audited and full validation rerun. Bounds/ticking causality
  for the four failures remains a hypothesis, not a demonstrated production bug.
  Crush observation is independently bounded against actual level game time.
- Typed NBT inspection established the actual generic resource was **1×1×1**;
  the earlier 3×1×1 reference was stale documentation. Only its size changed to
  **16×10×16**; all other tags and DataVersion were preserved. The structure
  encoding regression passed **1/1 selected JUnit test**, and compilation passed.
  An actual unloaded-neighbor pipe-scan probe was moved to a fixture-owned remote
  chunk rather than weakening its unavailable-neighbor assertion.
- Expanded focused validation passed **29/29 selected of 666**, zero failed,
  incomplete or untagged tests. Log: `build/suite-stabilization-focused-19.log`.
  Bounds expansion is not claimed as causal proof for every prior timing failure;
  full validation is required for this shared infrastructure change.
- Sixth fresh full run: **666 completed, 660 passed, 6 failed**; all previous
  29 targets passed, including expiry, healing, pickup and the real-time crush
  trace. Five additional power checks sampled while the world-wide loaded graph
  remained UNKNOWN under the larger reservation workload. They are being moved
  onto shared bounded prerequisite waits without changing graph budgets or
  fail-closed behavior. The remaining 15-block eye-line probe needs its complete
  actor/ray footprint reserved. Log: `build/suite-stabilization-full-native-06.log`.
- The long sight-range fixture now verifies an AIR corridor in the existing
  36×5×8 template above Y=1, retaining exactly the 15-block cutoff, +0.001 denial
  and 1.8-block eye-height difference. Production speech attenuation/range is
  unchanged. Shared power-readiness integration is being independently checked
  before the next full run.
- Five full-load power checks now reuse `PowerFixtureReadiness` for bounded
  topology prerequisites instead of copying fixed-delay waits. An ambiguous
  overload in the new speech diagnostic was corrected with an explicit empty
  collision context; GameTest compilation passed. Clean unit/build and the
  expanded focused selection are running on the combined source.
- Clean unit/build passed **2,736/2,736**, zero failures/errors/skips. Expanded
  focused validation selected **35/666**, with **34 passed, 1 failed**. All new
  speech/radius/replacement/alarm/readiness checks passed; the two-debug-lamp
  overload fixture reached a known graph but reported APC supply **LACK**, not
  demonstrated 24 kW delivery. Its delivery/timing prerequisite is being checked,
  not forcing a trip. Review also requires an independent real-level-time bound
  in the shared readiness helper.
  Logs: `build/suite-stabilization-full-build-final.log` and
  `build/suite-stabilization-focused-20.log`.
- The overload fixture's configured 25 kW source behind a 90%-efficient
  transformer provides only 22.5 kW before battery support against 24 kW of
  lamps. Investigation confirmed there is no source-watts configuration API;
  runtime supplies a fixed 25 kW. Sustained-overload setup must use a second
  existing native source, not inventing an unsupported configuration field or
  accepting requested watts as delivered work. Shared readiness now has both
  logical-test and real-level-time bounds.
- The two-lamp fixture now uses a second existing HV source and validates both
  live ports share the substation component. The existing 30 kW, 90%-efficient
  transform permits 27 kW against 24 kW of loads without battery dependence.
  Its observation window derives from protection duration plus solve quantum.
  No production source API/default, graph budget or protection rule changed.
- Focused run 21 retained **34/35 passes**. The remaining failure was diagnostic
  code reading `LIT` from the intentionally absent second lamp before its staged
  placement. This is not evidence that the authored lamp disappeared. Diagnostics
  must respect scene stages. Review additionally requires excluding battery
  contribution from the sustained two-source delivery proof.
  Log: `build/suite-stabilization-focused-21.log`.
- The existing `apc_debug_fixture` is verified **12×4×12** and the complete
  generated scene is checked within its bounds. The two-load case now starts
  its APC battery at zero and asserts nondecreasing stored energy across the
  measured overload interval, while retaining native delivery/trip/reclose
  checks. Stage-aware diagnostics no longer read lamp properties before creation.
- Focused run 22: **34/35 passed**; the strict scene check found a support/cable
  coordinate at local Y=0 below the actual structure bounds, whose minimum is
  local Y=1. The common debug-scene builder is being shifted up one block as a
  whole so hosts, devices and cables remain aligned and fully reserved.
  Log: `build/suite-stabilization-focused-22.log`.

The shared template's size is verified from the actual binary, not older notes.
Framework chunk-readiness references alone do not prove that every old expiry or
healing failure was caused by an unticked actor. Those causes remain bounded by
the observed validation evidence; no production status/metabolism rules changed.

No commit or push was made. Connected-client GUI and authenticated-success
coverage limitations from construction closure are unchanged by this task.
