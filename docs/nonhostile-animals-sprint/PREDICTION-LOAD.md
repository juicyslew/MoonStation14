# Owned movement under load

## Current observation

The owner successfully joined after the pre-input sequence-zero crash correction.
Small animal counts felt fine. With many enclosed Kobolds, their own movement
appeared to advance about 5–10 times/second despite less severe FPS/server-tick
degradation. NPC movement was not obviously affected. This is an observation,
not a measured simulation rate. Crowded-animal deaths are a separate unresolved
damage-source question.

Owner clarification: every launch uses a **new world**. Once the Kobolds are
placed, the slowdown persists regardless of the player's location; this report
is not a near/far or look-direction-dependent symptom. Prepare a repeatable pit
fixture rather than asking the owner to recreate it manually on each launch.

The owner then completed a trace and reported smooth creative-mode movement
under the same Kobold load, contrasted with choppy harness movement. The creative
observation is an important control but is not itself a captured comparison.

### Captured presentation fault

Trace: `run/build/reports/nonhostile-animals/movement-traces/35042d99-43d0-4b96-ad7f-ef778f749990/trace.json`.
Explicit stop after approximately 26 seconds; 5,878 samples, no drops/invalid
values. Median frame-marker interval was 11.8 ms; client ticks and snapshot
execution were approximately 50 ms apart. In held-forward windows, 39% of camera
transitions below 40 ms had less than `1e-5` horizontal displacement. This is
presentation evidence, not a measured server CPU stall. The remote-client trace
contains no integrated-server stream.

A representative captured sequence establishes the scheduling fault:

- 0.228 s: a snapshot advances/replays the body to predicted tick 4407.
- 0.247 s: the client tick replays the same target, leaving position unchanged.
  Minecraft has copied that already-advanced pose into `xo/yo/zo`.
- Camera samples at partial ticks 0.02, 0.28 and 0.48 have identical old/current
  horizontal positions, so advancing render interpolation cannot move the camera.
- 0.280 s: the next snapshot advances tick 4408 mid-interval; the next client tick
  repeats that pose, producing another plateau.

Independent source review corroborated the mechanism. The correction candidate
defers ordinary predicted-pose application to the client tick while settling
snapshot ACK/history immediately. It preserves non-predicted medical/containment
presentation and authority barriers; it does not add a camera smoothing controller
or claim the separate target-clock difference has been resolved. Correction and
fresh-world fixture native validation remains pending. The combined candidate
passed 438 focused JVM tests across 63 suites and main/GameTest compilation;
independent review accepted the client scheduling correction. No post-correction
owner trace has run.

Diagnostic `run_7f570f0622197afb9158cacf` passed its selected JVM groups but failed
one of 123 native tests: the new Kobold command test attempted Ready immediately
after registering its player, before normal admission emitted Begin. The test
now follows the existing two-tick registered-player convention; compilation and
independent review passed. Production movement and fixture behavior were not
changed for that scheduling correction. Fresh diagnostic
`run_e4ed6101f4a242fe887194e6` passed selected JUnit/readiness checks and filtered
native selection 123/1,074 (all 123 passed in 18.61 seconds); acknowledged,
cleanup CONFIRMED. The owner subsequently reported movement was really good
with 100 crowded Kobolds. This is positive manual evidence for this correction;
remaining airborne steering feel is a separate motor issue under investigation.
It does not establish complete sustained/observer acceptance or target-clock
parity.

The owner authorized a short timestamped movement trace and an SS14 comparison.
This is not authorization for the full sustained population/observer acceptance
suite. Do not spawn animals, change cramming rules, or launch a capture implicitly.

## Verified comparison

SS14 checkout: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
Its verified Robust gitlink is `3136118b5338ef2d9580178caf5c723e65eb76e7`.
The local Robust working directory was not revision-verified. Prediction/state
buffer findings instead use exact-commit remote sources:

- `https://raw.githubusercontent.com/space-wizards/RobustToolbox/3136118b5338ef2d9580178caf5c723e65eb76e7/Robust.Client/GameStates/ClientGameStateManager.cs`
- `https://raw.githubusercontent.com/space-wizards/RobustToolbox/3136118b5338ef2d9580178caf5c723e65eb76e7/Robust.Client/GameStates/GameStateProcessor.cs`

`PredictTicks` applies due commands before each simulated tick, independently of
input sequence acknowledgement. Local `GhostPredictionHistory.planReplayTicks`
preserves that core ordering.

The timing models are **not equivalent**. Upstream's target is the last processed
server tick plus target state buffer, rounded RTT/bias ticks, and prediction bias.
States are buffered; excess applicable states can be processed together.
Prediction is scheduled through state application/main-loop timing, rather than
replaying once per packet arrival. Local `GhostPredictionClock` extrapolates at
50 ms/tick from the latest snapshot's receipt-time anchor, with bounded latency
lead. `GhostControlClient` rebuilds on snapshots and client input updates. These
are material adaptations to measure under cadence mismatch and clustered updates,
not proven explanations for this incident. Do not import SS14's whole world-state
buffer merely to imitate its structure.

Pinned Robust render-interpolation and game-loop adjustment implementations were
not independently verified; do not claim those parity boundaries are satisfied.

## Presentation and diagnostic boundaries

Mapped Minecraft 1.21.1 `Camera.setup` interpolates entity `xo/yo/zo` to current
position. `ClientLevel.tickNonPassenger` copies old pose before ticking the body.
Local snapshot replay changes current position; `setPos` and zero-step
`LivingEntity.lerpTo` do not synchronize that old camera pose. This is a specific
presentation boundary to observe, not proof of a defect: normal interpolation
also relies on distinct old/current poses. Owned vanilla movement/motion tracker
packets are suppressed by `GhostOwnedEntityTrackerMixin`.

Trace camera samples, client callbacks, accepted snapshot execution times, replay
targets/work, and authoritative step cadence separately. Client packet-handler
execution is not wire-arrival time. Correlate streams by epoch, server tick, and
sequence; do not subtract independent-process monotonic timestamps. Keep capture
bounded/default-off, report dropped samples and recorder cost, and export outside
the frame/physics hot paths. Simulation remains unchanged while diagnosing.

Regression timelines: regular 20 Hz, clustered 100 ms updates, a 200 ms client
stall, and sustained 20→15 server ticks/second. Passing these helper tests alone
does not establish smooth rendering or connected performance acceptance.

## Instrumentation candidate

`MovementTraceRecorder`/`MovementTraceSession` own bounded common recording;
`MovementTraceClient` owns explicit commands, frame sampling, expiry and async
export. `GhostControlClient` observes callbacks, accepted snapshot execution and
rebuild outcomes. `LifecycleCharacterSessionControl` observes authoritative tick
attempts, actual steps, and send invocations (delivery remains unverified until
correlated with client execution). No movement timing or smoothing policy changed.

Focused validation ran 424 tests across 59 suites with compilation, no failures,
errors or skips. A final disabled-trace allocation guard then passed 28 focused
tests and compilation. Independent reviews checked frame/camera sampling and
authority isolation. Diagnostic `run_9fa2bf2e755cc50fa1ba7f76` then passed selected
JUnit and UI/interaction/prediction readiness checks, plus filtered native
selection 121/1,072: all 121 required fixtures passed in 15.08 seconds. Result
acknowledged; cleanup CONFIRMED. No owner movement trace has yet been analyzed,
and these checks do not establish camera smoothness or under-load parity.
`MOVEMENT-TRACE.md` contains the short authorized reproduction steps.
