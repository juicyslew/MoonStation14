# Paid power and device-signal phase

The feedback-06 r7 focused native run passed **42/42 selected, 877 total**.
The full run completed **876/877 passed**, with one button-to-airlock failure
and clean world save/shutdown (`build/shuttle-feedback-06-gate-r7-full.log`).

At lines 22733–22740, the button submitted with a known paid 20 W meter.
The deferred signal reached the intended airlock endpoint, but activation
rejected unknown power at delivery. Later accepted meter diagnostics do not
prove power was known at that earlier transaction boundary. The precise unknown
meter reason was not captured in that run; activation diagnostics now include
the existing read-only `PowerRuntime.airlockPowerDiagnostic`.

Existing `ServerTickEvent.Post` owners now have explicit order:

1. `PowerGraphService.onPostServerTick`: **HIGH**, bounded topology refresh.
2. `PowerRuntime.onTick`: **NORMAL**, existing solve/publication cadence.
3. `DeviceNetworkLifecycle.serverTick`: **LOWEST**, existing deferred drain.

No additional solve, billing, callback, retry queue, or coordinator was added.
Meter generation, identity, age and breaker checks remain fail-closed; a topology
mutation may still make power unknown until the existing scheduled solve.
Signal delivery does not invent power or ignore a changed topology.

The button fixture uses `BoundedGameTestWait` at **LOW** to submit exactly once
after observing actual fully paid power and connected live endpoints, before
normal deferred delivery. It requires the door to remain closed synchronously
after submission, then observes native opening. This proves a valid paid
transaction, not guaranteed acceptance across intervening invalidation.

Important paths: `ms14/power/graph/PowerGraphService.java`,
`ms14/power/runtime/PowerRuntime.java`, `ms14/devicenet/DeviceNetworkLifecycle.java`,
`gametest/support/BoundedGameTestWait.java`, and
`DeviceButtonAirlockWorkflowGameTests.java`. `PowerDeliveryTickOrderingTest`
pins the live annotation ordering. Author compilation and **104/104** focused
tests passed (`build/shuttle-feedback-06-r8-author-phase.log`); fresh native
validation is still required. Owner configuration and launch settings are unchanged.

## r9: loaded-frontier fixture and bounded timeout diagnostics

The r8 full run completed **875/877**. Its remaining failures were the APC
overload fixture's wired debug-lamp readiness timeout and an unrelated critical
shock test. The APC fixture's source cable host at local `(x=4,y=1,z=2)` maps in the retained
world to Z `-7925888`, chunk Z `-495368`; a neighboring cable-host probe at
local Z `1` maps to chunk `-495369`, outside its prior local-Z-2 ticket minimum.
The component's currently loaded nodes and `topologyReady=true` do not imply that
this graph frontier is loaded. The power graph's existing 3D neighbor search
therefore correctly keeps that component unknown; no production graph rule or
unknown-power acceptance was changed.

The owned APC fixture now tickets the local `(1,-1,1)..(6,2,8)` region and checks
that every cable-face host's complete `LoadedPowerGraph` 3x3x3 potential-neighbor
stencil is within that fixture lease. `FixtureChunkTickets` remains the sole
ticket owner; this is test-only residency, not gameplay force-loading. The shared
`PowerFixtureReadiness` helper writes its full timeout diagnostic and timing
context once to the server log under `[power-fixture-readiness-timeout]`, while
the GameTest exception stays concise enough for the reporter. It retains the
same bounded deadline and readiness predicate.

Author `compileJava compileGameTestJava` and focused JUnit validation passed
**170/170** (`build/shuttle-feedback-06-r9-author-power.log`), including a pure
timeout-message size regression test at `PowerFixtureReadinessTest`. Native
validation remains queued for the central gate; this worker did not launch a runtime.

## r12: APC overload fixture observation window

The fresh r11 run had one remaining failure: the overload fixture observed a known
29.8 kW output, but only 57 native overload ticks had elapsed at its premature
deadline. The fixture now waits (bounded, fail-closed) for the first known connected
sample that powers the full load, then observes the existing typed APC protection
curve for `floor(tripTimeSeconds * SERVER_TICKS_PER_SECOND) + 1` ticks plus two
solve intervals. The observation baseline is seeded from the actual native
protection counter at registration, since readiness itself may have accumulated
valid ticks. It still requires the real breaker trip immediately after that native
counter reaches its configured threshold; failure exceptions stay concise while
full native/graph state is logged. No gameplay protection, graph, billing, or
sample-validity behavior changed.

Author `compileJava compileGameTestJava` and focused power/readiness JUnit checks
passed **61/61** (`build/shuttle-feedback-06-r12-author-overload.log`). No native
runtime was launched; central native validation remains required.

## r13: isolate the paid protection episode

The r12 shared-runner result still failed at the bounded deadline with only 16
native overload ticks. Source confirms this is not a pause: `observeApcOutput`
resets its counter when the sample is unknown/stale or a server-tick continuity
gap occurs. Other fixtures in the default batch can change graph generation and
make those samples unknown. The real overload fixture now runs in its own named
GameTest batch, leaving all other tests selected and preserving the production
fail-closed graph and counter-reset rules. It waits for a fully powered connected
sample before observing the configured threshold. Failure logs retain a bounded
per-tick timeline of meter knownness/generation and native counter/last-tick state;
the fixture does not grant counter progress across unknown observations.

`FixtureChunkTickets` already binds leases to each test's pass/fail/rerun listener,
so isolation required no new ticket cleanup mechanism. Author compile and the
focused power/readiness checks passed **61/61** (`build/shuttle-feedback-06-r13-author-overload.log`).
No GameTest runtime was launched by this worker; fresh native batch validation is
still required.
