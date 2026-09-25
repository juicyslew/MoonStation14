# 2026-09-24 ground friction and client presentation audit

**Status:** this audit corrects the effective-friction explanation and records the latest experimental client-presentation changes. Automated validation was reported after the final source changes, but the owner has **not** connected with the matching rebuilt client/server to retest movement feel, animation, jitter/reconciliation, or camera behavior. None of these observations closes M2/M3 acceptance.

## Effective ground friction (correction)

The pinned SS14 source is commit `c9df5ef5d675b0d1d226828bddf6b78c28502d91`. `Resources/Prototypes/Entities/Mobs/base.yml:42-56` adds `MovementSpeedModifier` to the base mob; `MobHuman` inherits it. `MovementSpeedModifierComponent` has `BaseFriction = 2.5`, but that is **not** the final effective normal-ground friction. `MovementSpeedModifierSystem.cs:31-42` multiplies both friction values by `CCVars.Physics.cs:22-23` (`physics.tile_friction = 8.0`). The effective HUMAN normal and sprint friction values are therefore 20 each.

The local HUMAN movement policy now uses friction 20 for both normal and sprint movement, with the focused policy tests updated accordingly. At the default 20 Hz tick interval (0.05 seconds), the motor's neutral-floor retention factor is `clamp(1 - friction * dt, 0, 1)`. For ordinary HUMAN ground friction this is `clamp(1 - 20 * 0.05, 0, 1) = 0`, not `0.875` (which came from incorrectly treating 2.5 as final). This materially changes the expected ordinary-ground stopping/coasting interpretation; it does not, by itself, establish subjective feel or prove that the owner-observed concern is resolved.

SpaceLube contact supplies factor `0.05` while sliding and while feet overlap a currently qualifying puddle. Thus effective friction and acceleration are each `20 * 0.05 = 1` during that contact. The factor returns to neutral when the qualifying contact ends; there is no artificial post-contact timer. This policy introduces no artificial speed cap or post-contact speed clamp. These are source/configuration and implementation facts, not a connected feel result. See the prominent correction in the [movement-feel audit](2026-09-24-connected-movement-feel.md).

## Animation presentation

The custom path cancels `LivingEntity.travel`, which also omits vanilla `calculateEntityAnimation`. A client mixin `@Invoker` now invokes that animation calculation once after a successful custom motor `player.move`. This is intended to restore walking/sprinting leg animation without running a second movement operation. The owner has not yet confirmed animation in a connected session; compare walking and sprinting leg animation on the same matching build and report any stutter or mismatch.

## Snapshot jitter and bounded correction

Previously, applying a received server snapshot always set the local player to the older authoritative position, wiping newer client prediction. The current client keeps a bounded 64-entry history from input sequence to predicted end position. For acknowledged snapshots, it preserves the displacement of unacknowledged commands using arithmetic rather than replaying `Entity.move`; this handling is deliberately limited to grounded movement and small corrections no greater than one sprint tick (`0.225`) whose target AABB passes `noCollision`. Otherwise it applies a hard authoritative snap.

This is **not** full deterministic replay: history capacity 64 has not been measured as sufficient, and arithmetic displacement preservation does not re-simulate collision for pending commands. Jitter/rubberbanding can remain, especially under larger corrections, airborne movement, environmental divergence, or history exhaustion. There is no change to server authority. The owner must test under high latency and inspect corrections before this behavior can be accepted.

## Temporary camera lock policy

The selected policy is **stun-only** temporary camera lock, not lock for the full slide/knockdown. SS14's `ChangeDirectionAttempt` is blocked while Stunned, not merely while Sliding or Knockdown is active. The client angle guard and server angle guard now use synchronized client stun / authoritative server stun with the 0.5-second stun duration; sliding or knockdown can persist for 1.5 seconds. Other movement-mode rotation behavior is unchanged. Client status synchronization can delay onset or release, so verify both sides in a connected test. A future dedicated camera system is separate work and is not claimed here.

## Latest reported automated validation

After the final source changes, the coordinator reported:

- `& ".\gradlew.bat" test --rerun-tasks --no-daemon` — `BUILD SUCCESSFUL`, 34s.
- `& ".\gradlew.bat" build --no-daemon` — `BUILD SUCCESSFUL`, 6s.
- `& ".\gradlew.bat" runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run` — `BUILD SUCCESSFUL`, 23s; latest log reports 112 started at line 35 (`2026-09-24 18:37:49`), and 112 complete/pass at lines 229/230 (`18:37:52`).
- 603 JSON files parsed; `git diff --check` passed with CRLF warnings.

These automated checks do not verify a connected player's movement feel, animation, jitter/reconciliation, camera lock, or mode/teleport handoffs. The [connected smoke checklist](experimental-connected-smoke.md) records the required owner retest. The separately documented re-slip parity correction remains in force; the old continuous-superSlip three-launch claim was a local bug, not pinned upstream behavior.
