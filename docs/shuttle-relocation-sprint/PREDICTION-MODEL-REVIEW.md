# Moving-ship prediction: current model and failed assumption

## Status

The common-world authority candidate regressed owner gameplay. Its diagnostic
PASS is not evidence that moving-ship prediction is correct. No further movement
implementation is justified by this document alone.

## The important distinction: coordinates versus time

World coordinates describe a location on the map. Ship-local coordinates describe
a location relative to the ship. Neither identifies **when** that location applies.

```
player world position at tick T
    = ship transform at tick T × player local position at tick T
```

Using the ship transform from an older instant changes that answer, even if every
number is valid. Mixing coordinates from different instants is the central problem.

## Current paths

```
KEYBOARD
   ├─> client records numbered input ─> predicts body immediately ─> camera
   │                                      ↑
   └─> network ─> server input queue ─> server movement/collision
                                           │
                 snapshot: position, velocity, last applied input,
                 ground state, optional ship ID/local position
                                           │
                 client applies authority, then replays unacknowledged inputs

SHIP PHYSICS
   server Sable physics ─> separate pose packets ─> client SDK interpolation
                                                    ├─> logical collision pose
                                                    └─> render interpolation
```

Production character control is `LifecycleCharacterSessionControl`, not the older
`GhostMobHarnessControl`. The client consumer is `GhostControlClient`. Server and
client share movement code, but shared code does not guarantee matching collider
state or timing. Replay also needs compatible moving-world state.

Sable's pinned client logical pose is already sampled from its network
interpolator; render interpolation is another stage. Sable's pose counter and the
character snapshot's level game time are different clock sources. Their mapping
must not be assumed.

### Verified clock/cadence details

Further pinned-JAR inspection establishes the normal server relationship:

- `mixin.plot.ServerLevelMixin.sable$tickPlotContainer` runs at `ServerLevel.tick`
  HEAD, gated by `tickRateManager().runsNormally()`.
- Container observers execute physics before tracking. The six physics substeps
  therefore precede **one** tracking pass per normally running level tick.
- `SubLevelTrackingSystem` starts its counter at zero for each newly constructed
  per-level tracking system. The level's game time can already be nonzero.
- Both counters advance once per normal level tick, so their normal relationship
  is an offset with a known update-phase convention, not unrelated rates.
- Public access is `ServerSubLevelContainer.trackingSystem().getInterpolationTick()`.
  Tracking sends with key S and then increments; after that pass the getter is S+1.
  Associate the character publication boundary with that completed pass rather
  than guessing a universal offset or requiring a private-field accessor.
- Changed or newly stopped ships generate pose updates during the tracking pass;
  stationary ships need not send a pose each tick. Normal moving-pose and eligible
  character snapshot opportunities are nominally 20 Hz, not 120 Hz.

`SubLevelSnapshotInterpolator.tick(pointer)` prunes oldest samples with tick less
than `(int)(pointer - 6)`. This is approximately six simulation ticks behind the
client interpolation pointer (about 0.3 seconds at 20 TPS), **not five seconds**.
No additional pose archive has been justified or implemented from that fact alone.

The normal run's `sable-client.toml` currently specifies an interpolation delay of
1.5 ticks (also documented there as the default): nominally 75 ms. At 2 m/s that
corresponds to 0.15 blocks, matching the scale of the observed offset. Aligning the
counter labels does not remove this intentional client sampling delay or remove
the need for client prediction/reconciliation. The configuration was read, not
changed.

## Why the two authority policies failed

Previously, framed snapshots projected through the client's current ship pose,
while airborne WORLD snapshots used server world XYZ. The first connected report
recorded two takeoff corrections of about +0.157/+0.134 blocks and two landing
corrections of about −0.138/−0.124 blocks, all with zero replay. Airborne nullable
frame IDs were omitted by JSON serialization; ordinary jump detachment did work.

The latest candidate instead always applies server world XYZ. That made the
position-selection rule consistent, but left client collision geometry on Sable's
interpolated timeline. It did **not** synchronize player and ship time.

```
Illustrative horizontal positions:

server:       ship origin 100.00 ─── player 102.00   (local offset 2.00)
client ship:  ship origin  99.85 ─── player 101.85   (same local offset)
latest patch: ship origin  99.85 ───── player 102.00 (local offset 2.15)
```

Reverse the ship's direction and the offset reverses. Near a wall, placing the
body using one instant and testing collision using another can place the body
inside the client's wall. Subsequent collision/carry and authority application
can then fight each other. This explains the architectural failure; it does not
measure every reported jitter or CPU stall.

The new report `epoch-2-1793580216089.json` contains 46 snapshots, two jumps, 263
camera samples, and a worst recorded before/final correction of about 0.405 blocks.
Reprojecting its server-local points through recorded client logical poses shows
roughly 0.10–0.20 blocks of disagreement in the sampled grounded cases. Its zero
authority-placement error merely proves that the selected raw-XYZ rule was applied.
There are 219 replay executions involving 50 distinct sequences; the report's
`actualReplayCount` counts distinct sequences, not executions.

## Airborne momentum is separate

```
launch world velocity = ship point velocity + relative launch velocity
ship point velocity  = linear velocity + angular velocity × offset
```

The current adapter applies 0.91 horizontal retention on airborne ticks. With no
other influence, 2 m/s becomes approximately 0.78 m/s after ten such ticks, while
a ship maintaining 2 m/s keeps moving. This supports investigating the owner's
air-braking concern; it cannot explain standing jitter by itself.

Preserving inherited launch momentum is not the same as continuously following
a ship that later accelerates or turns. Air-control/braking policy must distinguish
inherited momentum from voluntary steering rather than conceal timing errors.

## Standards and SS14 reference

A coherent multiplayer design needs:

1. Player state and moving colliders evaluated at compatible simulation times.
2. Reconciliation of authoritative state followed by correctly ordered pending
   input replay, including the moving-world context needed by those inputs.
3. Explicit local/world coordinate and velocity ownership transitions.
4. Stable local deck position without input; collision constraints must not be
   bypassed merely to make presentation smoother.
5. Rendering separated from physics, with parent/support-aware interpolation.
6. End-to-end assertions for gameplay, not only assertions that a chosen formula
   was implemented.

Pinned SS14 Content `c9df5ef5d675b0d1d226828bddf6b78c28502d91` and Robust
`3136118b5338ef2d9580178caf5c723e65eb76e7` provide concrete references:

- `ClientGameStateManager.ApplyGameState` / prediction: authoritative state and
  predicted entities are reset/applied before forward prediction.
- `SharedTransformSystem.Component`: transforms carry local coordinates and
  parents; state processing respects transform hierarchy.
- Client `TransformSystem.ActivateLerp` / `FrameUpdate`: rendering interpolation
  is distinct and parent changes are not blindly interpolated.
- `SharedMoverController.Input.TryUpdateRelative`: grid/map movement reference.
- `SharedPhysicsSystem.Velocities.GetMapLinearVelocity`: ancestor velocity includes
  linear and angular contributions.

These sources do not establish every SS14 moving-grid prediction detail, nor an
ordinary 3D jump/air-drag policy. They are architectural references, not proof that
the port already matches SS14.

## Process correction

Server fixtures found real contact bugs but omitted the actual client prediction
and rendering loop. The connected fixture improved observability, yet its protocol
oracle still accepted physically poor movement. The last patch addressed the
position switch without resolving collider timing, despite that known risk.

The next implementation should first narrowly back out the regressing authority
policy, then establish the player/ship time contract. Regression coverage must
include cold and warm sessions, standing/reversal, takeoff/landing with zero and
multiple pending inputs, stationary/receding/pushing walls, and observer views.
Performance needs separate timings. No additional gameplay retest is requested
for the current unstable candidate.
