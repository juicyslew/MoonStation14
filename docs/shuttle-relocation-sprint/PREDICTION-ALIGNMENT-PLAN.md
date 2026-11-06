# Owned-character / ship prediction alignment

## Goal and agreed behavior

Replace the mismatched character/Sable prediction paths, not compensate for their
output. A non-rotating, non-accelerating moving cabin must feel like the same cabin
at rest: stable standing, equivalent walking/braking/jumping, and solid walls.
Keep stock Sable `fg9dTRz9`, six physics substeps, normal launch/configuration,
world-down gravity, normal controls, and the existing strict collision proofs.

Airborne controls use the ship's launch-point velocity, captured at takeoff, as
their fixed inertial reference. They do not follow later ship acceleration or
turning. Inherited velocity is applied once, not added again by the reference.
The reference is XYZ: vertical drag acts on relative vertical velocity, while
gravity remains world-down. Horizontal-only conditioning does not satisfy the
constant-translation requirement for ascending or descending cabins.

## Composition and parity boundary

The upstream audit verified Content `c9df5ef5d675b0d1d226828bddf6b78c28502d91`
and Robust `3136118b5338ef2d9580178caf5c723e65eb76e7`. Relevant human composition:
`MobHuman` inherits `AppearanceHuman` and `BaseSpeciesMobOrganic`; the latter
inherits the organic body mixins and `BaseSpeciesMob`, whose movement branch is
`BaseMob -> BaseControllable`. `BaseMob` supplies `Physics` (kinematic controller),
`InputMover`, `MobMover`, and `MovementSpeedModifier`; `BaseSpeciesMob` supplies
`StandingState`. Human does not override movement defaults.

Keep the existing resolved local `CharacterMovementPolicy.fromCharacterData`
consumer. Upstream defaults are walk 2.5, sprint 4.5, acceleration 20, friction and
no-input friction 2.5, minimum friction speed .005. References:
`Resources/Prototypes/Body/Species/human.yml`, `Resources/Prototypes/Body/species_base.yml`,
`Resources/Prototypes/Entities/Mobs/base.yml`, and
`Content.Shared/Movement/{Components/MovementSpeedModifierComponent.cs,Systems/SharedMoverController.cs}`.
The local grounded speed scale and ordinary 3D jump remain Minecraft adaptations.
Frozen launch-reference state is transient movement/prediction state, not a new
upstream component or an unsupported prototype field.

## Verified constraints

- Sable physics and tracking run at level-tick HEAD, physics before tracking;
  tracking has one pass per normal level tick, not one per physics substep.
- Character snapshots publish after physical movement. Both clocks normally
  advance once per level tick, but Sable's per-level counter starts at zero.
- Tracking emits key S then increments its public counter. Associate character
  snapshots with the completed Sable update at a defined publication phase.
- Sable's client history prunes before `(int)(pointer - 6)`; its configured normal
  interpolation delay is 1.5 ticks. Do not assume a five-second SDK history.
- History retained by this integration is bounded to **two seconds / 40 normal
  simulation ticks**, plus any necessary bracketing endpoint. Store transforms
  and motion samples, not block/world copies; share history per tracked frame.
- Replay may require poses after the authoritative starting tick. Historical
  lookup and bounded future transform prediction are distinct operations.

## Temporal contract

```
authoritative player state + completed Sable step S
                      |
            restore player/context at S
                      |
       replay pending input 1 using interval S -> S+1
       replay pending input 2 using interval S+1 -> S+2
                      |
        live prediction continues at the next interval
```

An input sequence is not itself a Sable timestamp. The client resets its temporal
anchor from an accepted snapshot and assigns consecutive simulation steps to the
pending suffix. Native carry belongs to a particular simulated interval and must
occur once in that prediction pass, including replay when appropriate. Replace
the old blanket replay-carrier suppression only after this explicit interval
ownership is wired; never simply enable repeated carry against the same SDK pose.

The selected temporal pose must reach native narrowphase **and broadphase**,
support/loaded-frame queries, actor-point conversion, and point-velocity queries.
An actor-scoped context must not mutate SDK logical poses globally. Other owners
retain their normal paths. Cache pose selection per frame/step, not per SAT row.
Unknown, removed, composition-incompatible, or out-of-window state must not silently
reuse a wrong pose. Use the existing conservative pause/rebase boundaries.

Rendering remains a separate stage but must present compatible ship/body states.
Do not fix simulation with camera offsets or a global interpolation-delay change.
Transitions between deck support and airborne WORLD state must not switch visible
ship timelines. Audit SDK remote frame presentation before adding a parallel path.

## Ordered work units

1. **Bounded timeline foundation:** immutable pose/motion samples, explicit step
   keys, two-second retention, interpolation/prediction, lifetime/reset rules,
   exact-once interval ownership. Reuse `NativeFrameInterval`, SDK pose math, and
   existing point-velocity math. Focused deterministic tests first.
2. **Live Sable integration:** capture authoritative pose and velocity together
   before SDK pruning; stamp character snapshots from the completed tracking
   step; wire the temporal provider through native/query/presentation entrypoints.
   Reconcile and replay the real owned client through that provider.
3. **Launch reference:** complete the pinned upstream composition/default audit,
   map to existing resolved local movement policy, then add the fixed airborne
   reference to shared movement, snapshot/replay state, and transition handling.
   No fake upstream component or second movement controller.
4. **Replace obsolete paths:** remove the always-raw-world position selector and
   its circular placement oracle; replace mismatched frame projection and blanket
   replay carrier rules with the common temporal contract. Retain independently
   valid contact fixes and explicit-only diagnostic logging. No broad worktree
   reset; unrelated uncommitted work must survive.
5. **Acceptance:** independent review, focused checks, serialized integration,
   then actual connected-client/server evidence. Native/server-only tests are
   regression coverage, not proof of connected prediction. No automatic client
   launch or owner run-configuration changes.

## Required regression evidence

The connected fixture's former snapshot-world versus authority-projection
comparison is not an oracle: both values can derive from the same projection.
Retain them as raw coordinates. Require explicit completed/restored SDK steps,
consecutive selected-pose intervals and paired body/camera render samples before
reporting temporal coverage. Record support-relative standing/flight/landing
metrics against the actual selected frame pose (air uses the original launch
frame); until measured limits are validated, report metrics without a PASS bound.
Input sequence numbers must never be treated as SDK step numbers. Collector unit
tests are not connected-runtime proof.

- Prototype resolution/inherited movement defaults and the live policy consumer.
- Clock stamp phase, restart/level lifetime, pose/velocity capture consistency,
  late/duplicate samples, pruning/bracketing, missing-history handling, and bounds.
- Standing and walking in stationary versus uniformly translating cabins;
  direction reversal between trials; translation and yaw cases.
- Zero-pending and delayed-ACK jump/landing, repeated jumps, and boarding. No
  unexplained support-relative discontinuity or replay of acknowledged input.
- Exactly-once launch inheritance and carrier intervals, with the air reference
  preserved through corrections/replay; later ship acceleration does not drag an
  airborne actor along.
- Stationary/receding/pushing walls: no penetration, self-jitter, or hidden reset.
  Check observer presentation separately after owned motion is coherent.
- Actual client/body/camera and ship poses on one timestamped report; meaningful
  relative-motion and collision oracles, not merely a chosen position formula.
- Bounded history/selection work and separate movement/collision timings. Do not
  claim startup lag fixed from functional passes or reduced logging alone.

## Completion boundary

An unwired history/helper is not delivered functionality. A unit/diagnostic PASS
is not an actual-client pass, OWNER_READY, M1 acceptance, or proof of smoothness.
No additional retest of the regressing raw-world candidate is requested. Record
concrete integration hazards and resolve them before declaring the replacement
technically ready.
