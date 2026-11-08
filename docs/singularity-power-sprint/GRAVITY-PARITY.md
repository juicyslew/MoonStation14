# Gravity contract (upstream verified; ported integration verified)

Reference pin: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.
The root `Singularity` prototype has dynamic physics and composes `GravityWell`.
The `GravityWellComponent` code defaults are radial acceleration 0, maximum
range 0, minimum range 0, tangential acceleration 0 and a 0.5-second pulse
period. Root-prototype authored initial values override those defaults: radial
acceleration 10 and maximum range 4. `SharedSingularitySystem` then sets the
live singularity values by level: acceleration `10 × level`, range
`2 × (level + 1)`, and tangential acceleration 0.

For displacement `r = center − target`, squared distance `d²` and elapsed
seconds `t`, velocity change is `r × (acceleration × t / d²)`. Its magnitude is
`acceleration × t / distance`, not inverse-square magnitude. The physics impulse
includes target mass; the impulse consumer divides by mass. Skip targets with
`d² < max(minRange², 1e-5)`.

Upstream pulses are independently scheduled per well and use actual elapsed
time. Eligible targets have physics; map/grid entities, other wells, the
GhostImpassable collision layer and disabled pressure movement are excluded.
Static bodies do not respond. Do not invent unsupported local magboot components
merely to imitate that filter.

Timing detail: first deadline is map-init time plus one period. `LastPulseTime`
is derived as `NextPulseTime - TargetPulsePeriod`, not the last actual update.
A due update scales by current time minus that scheduled prior pulse, then
advances the deadline by one period without a catch-up loop. A late server may
therefore remain due on subsequent updates. Source verification confirms the
singularity radius constant is **2**, distinct from gravity range. The root
prototype's authored level-1 maximum gravity range is 4 (not a
`GravityWellComponent` default); level updates replace it with
`2 × (level + 1)`.
References: `Content.Server/Singularity/EntitySystems/GravityWellSystem.cs:57–59,74–116`,
`Content.Shared/Singularity/Components/GravityWellComponent.cs:62`,
`Content.Shared/Singularity/EntitySystems/SharedSingularitySystem.cs:181–207`.

Sources: `Content.Shared/Singularity/Components/GravityWellComponent.cs:19–50`,
`Content.Server/Singularity/EntitySystems/GravityWellSystem.cs:106–137,202–230`,
`Content.Shared/Singularity/EntitySystems/SharedSingularitySystem.cs:181–207`, and Robust
`SharedPhysicsSystem.Components.cs:220–228`.

Local `SingularityGravityPulse` now samples a source-local ten-tick schedule.
Each due source contributes elapsed time since `nextTick - 10`; one late query
advances the deadline once, so it can remain due on a following tick. The kernel
uses resolved level-scaled profile values and the upstream minimum-distance
threshold. Profile ID and next deadline are synchronized; client prediction
uses the separately synchronized, persisted first-deadline phase anchor and
derives only nominal ten-tick pulse slots. Replayed prediction reads are pure;
the client does not consume/advance source phase. This deliberately predicts a
normal 0.5-second pulse rather than the server's potentially longer elapsed
duration after a late update; authoritative movement snapshots reconcile that
server-only timing difference. The mutable server next deadline remains
persisted separately and retains upstream derived-prior-pulse semantics. The
shared kernel now resolves a source's gameplay profile before reading/advancing
its mutable pulse deadline, so an unavailable profile during reload cannot
consume a scheduled pulse.

The source-owned clock is sampled from `SingularityEntity.tick` every server
game time, even when no eligible gravity target queried the shared kernel.
Native living bodies and other consumers reuse that same cached sample. This
matches pinned `Content.Server/Singularity/EntitySystems/GravityWellSystem.cs`:
its update iterates every well and advances due `NextPulseTime` independently
of whether a target query finds any bodies. A focused quiet-source test
reproduced the previous dormant-clock failure after 600 target-free ticks and
verified the repair: the first post-idle native impulse is a normal scheduled
pulse, not accumulated idle elapsed time. See `HORIZON-CONTACT.md` for the
trajectory and exact focused GameTest evidence. Existing native travel's
one-delta test and the shared schedule unit test also pass after this change.

## Verified port integration seams

`CharacterMovementEnvironment.externalImpulse` is added to motor velocity in
**blocks/second**. `GroundedHarnessWorldStep` and `GhostControlClient` convert
native velocity ×20 into that motor and ÷20 on output. Both call the shared
kernel using the current world tick. Native living entities and owned grounded
bodies have separate movement-owner routes; owned bodies are excluded from the
native travel adapter to prevent applying the impulse twice. Client prediction
uses the synchronized source phase anchor and nominal pulse slots, while server
movement uses each source's mutable elapsed-time schedule.

Native living entities and `LooseItemEntity` use **blocks/tick**; a per-second
velocity change must be multiplied by 0.05 once. Reuse the existing tether
integration paths (`LivingEntityMindHarnessTravelMixin`, `LooseItemEntity`) and
movement ownership gates to avoid double application. `LooseItemEntity.applyImpulse`
divides by mass and clamps velocity; it is not interchangeable with a direct
gravity delta-velocity input. Settled loose items need an existing wake path.

`SingularityGravityGameTests` exercises actual grounded motor movement on the
server as well as the native living-entity and loose-item adapters. This proves
those fixture consumers receive nonzero gravity movement, not that a connected
player session used the same movement owner. Live client-session feel and
end-to-end catalog-reload behavior remain unverified; do not represent fixture
coverage as connected-playtest evidence. Do not assume an unsupported global
impulse accumulator or magboot component exists.
