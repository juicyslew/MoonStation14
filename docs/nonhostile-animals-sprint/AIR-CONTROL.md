# Gravity-air steering correction

The owner confirmed good harness movement with 100 crowded Kobolds after the
pose-scheduling correction, then reported sluggish forward-jump-to-sideways
steering. Treat these as separate issues.

## Established cause and reference

The earlier circle-jump correction bounds new acceleration using total horizontal
speed, without horizontal air damping. At full target speed and perpendicular
wish, the remaining squared-speed budget and directional projection are both
zero, so no lateral acceleration is admitted. Existing forward momentum never
decays to make room. This is not a prediction scheduling problem.

Minecraft 1.21.1 movement was checked with `javap` against the cached mapped
NeoForge output JAR
`sourcesAndCompiledWithNeoForge_030c49e34b61d0ceaabf5a9185565bb391167493_output.jar`.
`Player.getFlyingSpeed` supplies 0.02 blocks/tick, or 0.026 while sprinting.
`LivingEntity` applies relative input, moves, then retains 0.91 horizontal velocity;
`LocalPlayer` also damps raw movement input by 0.98. For a single normalized axis,
post-tick velocity therefore follows `v_next = 0.91 * (v + input_acceleration)`.
Vanilla steering is not an instantaneous turn: old forward velocity decays while
sideways input builds.

## Implemented scope (selected diagnostics passed)

- Supply gravity-air coefficients through existing movement-environment physical
  adapter data. The Minecraft adapter owns constants; server and client use the
  same helper. No new entity component or movement payload is needed.
- Apply modest input acceleration and post-move horizontal damping only to
  ordinary gravity-air movement. Keep resolved prototype walk/sprint targets and
  voluntary modifiers relevant to the steering budget.
- Retain the scalar limit on **added** voluntary acceleration, accounting for
  post-move damping so perpendicular steering works on the first air tick.
  Do not normalize or hard-cap carried total velocity.
- Preserve grounded SS14 friction/snaking, zero-gravity movement, natural flight,
  and external-flight launch behavior. Existing callers without the adapter
  override retain their current behavior.
- Ordinary gravity-air impulses experience physical air damping, rather than
  having individual axes rescaled to enforce a steering cap. Constraint forces
  remain outside the voluntary-force budget.

This is a deliberate Minecraft jump adaptation. Normal upstream SS14 humans do
not inherit `CanMoveInAir`; this is not a claim that SS14 has these coefficients
or a universal speed cap. It also does not replace the whole movement model with
vanilla Minecraft or resolve the separately documented prediction-target timing
departure.

Validate first-tick perpendicular steering, measured multi-tick velocity, no-input
damping, finite jumping, circle-jump bounds, impulse/collision behavior, and all
excluded movement modes before asking for another feel check.

The optional `GravityAirControl` environment profile is supplied by
`GroundedHarnessPhysics.gravityAirControl(sprint)` to both authoritative
`GroundedHarnessWorldStep` and predicted `GhostControlClient` steps. Cached
profiles use 0.91 retention per 0.05 seconds and acceleration 7.84/10.192
blocks/second², including the 0.98 input factor. Current callers without a profile
retain legacy behavior. The scalar pre-damping budget accounts for retention;
ordinary carried momentum experiences physical damping, not a speed-clamp
renormalization.

Focused JVM checks passed 449 tests across 68 matching suites, with main/GameTest
compilation and independent implementation review. A registered-player native
regression covers sprint buildup, forward jump, first-air-tick lateral steering,
settled input acknowledgement and duplicate-tick protection. Its execution and
the owner's feel check was pending at that point.

Diagnostic `run_aaf36924495a9d1f4474d500` passed selected JVM checks but failed
the new native fixture's grounding precondition before any air-control input.
Owned bodies suppress vanilla travel, and the authoritative motor waits for its
first valid control state. The fixture now admits a neutral input before bounded
settling, then warms forward movement, jumps and verifies positive lateral
response on sequences 2–4. It does not fake ground flags or change production
physics. Compilation and fixture review passed. Fresh diagnostic
`run_f42445b639bd6c9203259466` passed selected JUnit/readiness checks and filtered
native selection 124/1,075: all 124 required tests passed in 20.09 seconds.
Result acknowledged; cleanup CONFIRMED. The owner subsequently confirmed that
air control felt like Minecraft movement and worked well. The requested feel
check is satisfied; this is not full sprint validation or acceptance, nor proof
of complete SS14 prediction-target parity.

After the new diagnostic passes: in the normal harness, jump forward, release
forward and press sideways while airborne; compare walking and sprinting. Some
forward momentum should remain, but lateral input should respond immediately.
No new trace is required unless the behavior still feels wrong. The existing
Kobold setup command remains available for a fresh-world load comparison.
