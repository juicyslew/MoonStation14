# Experimental ghost owner-control smoke checklist

**Status: owner-run smoke test pending; no connected-client result is claimed.** This checklist covers only the experimental operator-command ghost control slice. It is not acceptance of the full Mind/ghost system, SS14 parity, NPC control, or lifecycle-transition coverage. The spectator carrier may retain a legacy HUMAN attachment from join-time enrollment; the debug command does not separate that identity. This experiment therefore does not meet the desired no-dedicated-player-character model until identity/enrollment is separated. Do not use character status or HUD as proof of that model. See the [sprint instructions](../../../instructions-player-body-control-sprint.md) and [M2b latest status](m2b-ghost-control-wire.md).

## Scope and safety

Test only one real, connected op-level-2 owner controlling the separate ghost Mob Harness. Do **not** test cross-dimension movement, teleport, respawn, higher permission roles, NPC/body transfer, or join-time behavior in this smoke. Do not change atmospherics configuration. `/ms14 mindghost start|stop` is registered for operators, but start refuses without changing ghost, Mind, or game mode unless the COMMON `experimentalMindGhostControl` gate was enabled when the server started. The command is unavailable to non-operators and start also intentionally refuses if the legacy `experimentalVerticalSliceMovement` gate is enabled; if that gate cannot safely be disabled, skip this smoke rather than enabling both experiments.

Before testing, back up the test world and use matching rebuilt mod client and server. In the **correct running installation's** `config/moonstation14-common.toml`, set `experimentalMindGhostControl = true` and `experimentalVerticalSliceMovement = false`; both settings are required. Stop the server before editing and restart it so the startup gate samples the new value. Do not edit an atmospherics config. The repository's `run/config/moonstation14-common.toml` is a local, gitignored runtime config, not a versioned config template; other installations get the config-spec default (`experimentalMindGhostControl = false`) and must set their own active config. The gate cannot be hot-toggled: an active session persists until explicitly stopped or the server is stopped/restarted. Join with a real op-level-2 player in Survival or already-Spectator mode, recording which mode is in use. Keep a second spectator available only for checking ghost visibility.

## Owner procedure

1. Run `/ms14 mindghost start` once. Wait for the owner chat confirmation that the session committed.
2. Check server INFO output for the matching Ready-handshake commit record (player, epoch, entity ID, ghost UUID) and the first accepted nonzero ghost displacement. Correlate the records to this owner/session; command registration or a Begin message alone is not proof.
3. Confirm the camera follows the ghost. Use WASD, strafe, look, jump, sneak, and sprint; verify movement is the ghost harness and not the `ServerPlayer` carrier. A ghost moving through an ordinary wall is an expected bounded mismatch because this harness has `noPhysics`; record it, do not treat it as collision success.
4. Observe and record ghost skin/visibility from both the owner and the second spectator. Also note lag, chat, and camera behavior; report unexpected UI as an abort condition. The renderer is currently an invisible placeholder, so record exactly what is seen rather than assuming the ghost is visible. Spectator visibility remains unverified beyond this specific observation.
5. Run `/ms14 mindghost stop`. Confirm the owner is returned to the previously recorded game mode and the expected camera/view is restored. If the starting mode was Spectator, confirm it remains Spectator.

## Abort conditions

Stop the test immediately on missing Begin/Commit, failure to restore mode or camera, unexpected UI, or disconnect. If movement freezes, issue `/ms14 mindghost stop`; if that does not recover safely, restart the server with legacy movement left unchanged/off. Do not repeat the test until the cause and recovery are understood. Preserve relevant server log lines and record the precise observed behavior.

## Record the result

Record client/server build identity, owner starting mode, whether Ready/Commit and first nonzero displacement logs matched, camera and each movement input result, owner/second-spectator skin visibility, lag, chat/status/HUD observations, stop restoration, and any abort/recovery. Label the result **smoke observation only**. A successful pass does not establish full-system acceptance, NPC transfer, on-join ghosting, visibility policy for all clients, collision fidelity, or cleanup for teleport/dimension/death/respawn transitions. Those remain separate deferred work and proof gates.
