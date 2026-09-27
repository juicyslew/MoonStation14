# 2026-09-26 ghost camera Shift and presentation audit

**Status: code-level fixes are present, but the owner's Shift/camera failure has not been retested in a connected session.** The historical owner observation is a real failure, not disproved by automated tests. This audit updates the [owner smoke checklist](experimental-ghost-owner-smoke.md) and the [sprint status](../../../instructions-player-body-control-sprint.md); it does not establish M2 acceptance or full SS14 parity.

## What the owner finding means

On the pinned Minecraft 1.21.1 spectator path, `ServerPlayer.tick` checks `wantsToStopRiding()` and calls `setCamera(this)` when Shift is pressed. That vanilla behavior can detach a ghost camera and make spectator flight feel normal again. `GhostControlClient` only sends ghost movement intent while the committed ghost is still the camera target, so after detachment the camera-target guard suppresses further intents. Vanilla camera movement or an apparently responsive spectator view is not evidence of continued ghost control.

The historical `run/logs/debug.log` owner sessions show accepted server displacements before/around the reported camera behavior, but do not establish what the owner saw after Shift. Recorded session commit / first accepted horizontal intent pairs:

| Commit | First accepted horizontal intent |
| --- | --- |
| 19:24:46 | sequence 88 at 19:24:50 |
| 19:39:06 | sequence 173 at 19:39:15 |
| 19:40:42 | sequence 43 at 19:40:44 |

These are historical server-side facts only; none proves sustained control after the camera detached. Preserve the original failure as the reason for the retest.

## Current code changes (not yet owner-verified)

- `src/main/java/com/juicyslew/moonstation14/mixin/ServerPlayerMindGhostCameraMixin.java` modifies the `wantsToStopRiding()` expression in `ServerPlayer.tick`. It suppresses detach only when `GhostMobHarnessControl.shouldKeepGhostCamera` confirms the exact connected, committed ghost camera and the carrier is not a passenger. Ordinary spectator dismount behavior and unrelated camera cases are intended to remain vanilla. The mixin is registered in `src/main/resources/moonstation14.mixins.json`.
- `GhostControlClient` projects the ghost's visual yaw, pitch, head yaw, and body yaw locally during client tick only while the ghost is the camera target. This is presentation smoothing, not local position/velocity movement or movement authority. The existing bounded intent still goes to the server for authoritative movement.
- `GhostMobHarnessRenderer` now renders a visible vanilla slime-shaped DEBUG proxy, using Minecraft's own slime model and texture (not an SS14 asset or player skin). It is hidden only when it is the active first-person camera target. Third-person visibility to the owner and visibility to other clients are expected from the renderer, but are not connected-client proven.

## Validation and evidence boundary

Reported automated validation for this code revision: `gradlew test --rerun-tasks --no-daemon`, `gradlew build --no-daemon`, and dedicated-server GameTests **120/120**. The coordinator's read of `build/gametest-run/logs/latest.log` on 2026-09-26 at 20:09:37.472 reported that 120 tests were running; at 20:09:40.344 it reported `120 GAME TESTS COMPLETE` and `All 120 required tests passed`. These checks do not exercise a connected owner's camera, Shift, mouse-look response, renderer presentation, or recovery. No owner retest on the new build has been performed; do not describe the Shift issue as fixed until that smoke is completed.

## Remaining limitations

The carrier's legacy HUMAN attachment and HUD/player identity are unresolved. This experiment is not the desired no-dedicated-player-character model and does not provide full SS14 parity. Ghost collision remains limited by `noPhysics`; rendering and visibility claims remain limited to the specific connected observations still to be recorded. Do not use this smoke to test NPC transfer, teleport, dimension changes, respawn, or other lifecycle transitions.
