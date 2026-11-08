# Selected pickup and character direction corrections

Status: source changes under validation; not a validated replacement for r34.
No deployment or owner acceptance is implied.

## Verified upstream rules

Reference checkout HEAD: `c9df5ef5d675b0d1d226828bddf6b78c28502d91`.

- Selected interaction submits an entity UID. The server checks current target
  range, obstruction, accessibility and interaction permissions, not whether its
  current aim ray still intersects that target. See
  `Content.Shared/Interaction/SharedInteractionSystem.cs:322–332,381–436`.
  Local selected pickup now follows this admission model; automatic target
  discovery remains ray-based. No reach increase or client-position trust was added.
- Critical/dead mobs fail change-direction permission independently of movement
  permission. See `Content.Shared/Mobs/Systems/MobStateSystem.Subscribers.cs:31,41–43,171–179`
  and `Content.Shared/Interaction/RotateToFaceSystem.cs:80–107`.
  Local voluntary look uses shared character medical permission. Healthy held or
  thrown characters must not lose look permission merely because motor prediction
  is suspended. Authoritative pose updates and external physics remain separate.
- Same-holder hand transfer preserves escape: `TryMoveHeldEntityToActiveHand`
  calls `DoDrop(doDropInteraction: false)` before pickup
  (`Content.Shared/Hands/EntitySystems/SharedHandsSystem.Interactions.cs:167–189`).
  Real drops emit `DroppedEvent`; escape cancels on that event and has `NeedHand=false`
  (`Content.Server/Resist/EscapeInventorySystem.cs:59–72,89–93`).
  Earlier claims that every physical hand transfer should cancel were incorrect.
  **The local hand-transfer correction is not implemented.** Retain strict release
  snapshot checks until a committed-transfer-specific refresh is wired.

## Native presentation evidence and unresolved scope

Minecraft 1.21.1 sources in `build/moddev/artifacts/neoforge-21.1.224-sources.jar`
confirm remote positional and teleport packets call `lerpTo(..., 3)`
(`ClientPacketListener.java:570–584,621–640`). `LivingEntity` replaces its target
and remaining steps on every update and advances a fraction each client tick
(`LivingEntity.java:2721–2741,2971–2978`). Mouse tracking already uses interval 1.
Owned character snapshots generally apply position directly; these are distinct
presentation clocks, not proof of a server physics defect.

This supports trailing/gliding remote poses, but does not prove rendered jump
landings or perceived jitter. No interpolation or camera-position changes are
included here. Held camera placement remains the restored native placement.
`ContainedBodyView.ownerIdentity` is a custody token, not a carrier UUID; a future
carrier-relative presentation fix needs an explicit synchronized association.

## Validation boundaries

Focused medical direction JVM tests passed; pickup and direction native fixtures
compiled. Native execution, readiness, packaging and connected-client visuals have
not yet been validated for these changes. Helper-only facing tests do not establish
production intent admission. Jug pickup coverage does not establish mouse custody
conservation. Keep these gaps explicit in the next gate.

For a validated build, owner checks should include: critical mouse facing;
catching a moving jug and mouse by clicking their visible bodies; healthy held
mouse look; and separate observation of held jitter, drop glide and repeated jump
landings. Same-holder escape transfer remains a known issue, not a claimed fix.
