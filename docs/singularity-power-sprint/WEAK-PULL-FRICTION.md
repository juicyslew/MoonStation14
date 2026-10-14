# Weak gravity and human ground friction

Reference check: SS14 `c9df5ef5d675b0d1d226828bddf6b78c28502d91`, with its
recorded RobustToolbox submodule at `3136118b5338ef2d9580178caf5c723e65eb76e7`.
The cited movement/CVar files have no local diff in that checkout.

The pinned SS14 checkout is `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. `MobHuman` inherits the organic species/base-mob chain that composes `MobMover`, `InputMover`, and `MovementSpeedModifier`. The component's base friction is 2.5, but the runtime multiplier matters: `Content.Shared/CCVar/CCVars.Physics.cs` defines `physics.tile_friction` with default 8.0, and `MovementSpeedModifierSystem` multiplies the base friction by that value. Thus SS14's resolved runtime human friction is 20 with and without input. Local resolved human friction 20 is intentional SS14 runtime parity; pig's authored 20 remains unchanged.

The local 20 Hz motor now subdivides only grounded horizontal friction and projected-wish acceleration into `ceil(dt * 60)` math steps, matching the upstream 60 Hz motor cadence while still issuing one Minecraft collision sweep per native tick. It accumulates each substep's displacement and carries the final substep velocity forward. The impulse enters once before the loop; gravity/vertical motion, external-flight bypass, collision/step handling, and post-locomotion constraint impulse ordering remain unchanged. At `dt=.05`, neutral human friction gives `r=2/3`; a `5/3` pulse ends at `40/81` velocity and contributes `19/486` displacement over the tick. The local timebase adapts upstream cadence; it does not claim equivalent native world physics or scheduler phase.

Upstream evidence: `Content.Shared/CCVar/CCVars.Physics.cs` (`TileFrictionModifier`, default 8.0); `Content.Shared/Movement/Systems/MovementSpeedModifierSystem.cs` (component base friction multiplied by configured modifier); `RobustToolbox/Robust.Shared/CVars.cs` (`NetTickrate`, `TargetMinimumTickrate`) and `RobustToolbox/Robust.Shared/Physics/Systems/SharedPhysicsSystem.cs:157–161,265–268` (substep count and timestep). Local typed profile fields encode resolved runtime values, not the unmultiplied component defaults.
