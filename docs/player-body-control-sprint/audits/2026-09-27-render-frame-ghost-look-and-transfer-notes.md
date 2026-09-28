# 2026-09-27 render-frame ghost look and transfer notes

**Status: a render-frame visual look projection is implemented; owner retest is pending.** The connected owner reports ghost movement is smooth but mouse turns jitter. This note records the narrow look response, the evidence boundary, and transfer semantics that future implementation must preserve. It does not claim the jitter is fixed, approve possession/transfer implementation, or establish full Mind/ghost acceptance. See the [owner smoke checklist](experimental-ghost-owner-smoke.md), [shared-motor prediction audit](2026-09-26-ghost-shared-motor-prediction.md), and [sprint instructions](../Instructions.md).

## Mouse-look path and rationale

Pinned MC1.21.1 source shows `Minecraft.runTick` runs client simulation ticks first, then `MouseHandler.handleAccumulatedMovement` -> `turnPlayer(D)V` calls `LocalPlayer.turn` for the current render frame, and then Minecraft renders. The old `GhostControlClient` copied `LocalPlayer` yaw/pitch to the ghost only at `ClientTickEvent.Post` (~20 Hz), so that frame's mouse turn was missing from the ghost until a later client tick. This is a source-based timing hypothesis, not connected runtime proof. The new `MouseHandlerMindGhostLookMixin` injects at TAIL of `MouseHandler.turnPlayer(D)V`, after vanilla applies this render frame's mouse input, and calls `GhostControlClient.projectOwnedGhostLookForRenderFrame()`.

The projection is strictly limited to an active committed Mind session: client is a spectator, owner and current level match, and the camera target is the exact currently owned ghost. It projects visual yaw, pitch, head yaw, and body yaw only. It changes neither position nor movement, sends no per-render-frame movement/position packet, and does not move authority from server to client. Existing client-tick look fallback remains. When the camera is no longer the owned ghost, this projection does not apply; the existing ghost intent path also stops sending intents when the camera target is not that ghost. Vanilla spectator camera responsiveness after a camera switch is not continued ghost control.

This change targets the likely client-tick-to-render-frame look lag, but the owner's new-build retest is pending. Automated build/test evidence and server movement logs cannot establish perceived look smoothness or successful runtime injection. Follow the checklist's isolated stationary small-mouse-motion observation at recorded high FPS, then test Shift staying on the ghost camera, movement separately, and the third-person visible proxy.

The owner's read-only `run/logs/latest.log` from 2026-09-27 has committed sessions and first accepted ghost displacements:

| Local log time | Ghost entity | Epoch | First accepted sequence | Displacement |
| --- | ---: | ---: | ---: | --- |
| 01:17:21.910 | 24 | 1 | 14 | (-0.230002, 0, -0.327260) |
| 01:18:22.058 | 25 | 4 | 26 | (-0.216157, 0, 0.336494) |

These records demonstrate accepted server movement intents in those sessions, not the quality of mouse-look. Together with the owner's report of smooth movement but jittering turns, they do not support treating movement transport as the established cause of the look issue. They also do not prove continued ghost control after any camera change.

## Spectator click is not possession

Vanilla spectator click-to-view is only a camera switch (`ServerPlayer.attack` calls `setCamera`). It does not bind/unbind the server-owned Mind, transfer Mob Harness ownership, authorize movement of the clicked entity, or create a controlled body. In the current client path, when camera target is not the committed owned ghost, GhostControlClient stops sending that ghost's intents. Therefore do not describe clicking a mob as supported possession, even if it enters the view or follows the target.

## Required future transfer semantics (not implemented)

The active-play invariant remains one Mind bound to exactly one active Mob Harness. Transfer requirements for any separately authorized feature:

1. **Enter ghost:** release the current character harness to remain in the world, create/register a ghost harness, and transfer the same Mind to it. Do not create a temporary active-play Mind with no harness.
2. **Possess configured body:** validate eligibility and complete the Mind unbind/bind transfer before removing or despawning the former ghost. If any step fails, reject atomically: retain the original ghost and its Mind ownership, and do not leave the body half-claimed.
3. **Return to ghost:** create a new ghost as the active harness and leave the body in-world. Preserve exclusive Mind-to-harness ownership and ensure the now-unowned body does not have conflicting AI/control.

The current `/ms14 mindghost start|stop` pair is temporary debug/recovery only; `stop` recovers the current experiment, it is not the desired final “exit ghost mode” semantic. Once real transfers work, use explicit enter-ghost and spawn-body/enter-body operations, or a click path that is explicitly validated and performs the same atomic Mind transfer. Do not offer “stop ghost mode” if it can strand an active Mind or leave an extra empty ghost entity.

## Validation evidence and limits

Reported validation for this revision:

- `gradlew test --rerun-tasks --no-daemon` — passed.
- `gradlew build --no-daemon` — passed.
- Dedicated GameTest run — 124 tests discovered/completed, but **3 REQUIRED atmosphere GameTests failed**: `sealedroomdiffusesgasandconservesspecies`, `skyexposedcellusesambientandrejectsinjection`, and `coveredexteriorsealingandbreachrespectownership`. These failures are concurrent atmospherics work; they were not fixed, waived, or ignored here. Do not report a full GameTest pass.
- Read-only owner server log evidence above proves accepted movement displacement only. No authenticated owner retest of the render-frame look change is available.

No SS14 assets were imported. The legacy `experimentalVerticalSliceMovement` flag remains default-off and transitional; do not remove it or relax the existing conflict gate on the basis of this look work. No source, test, configuration, or atmospherics code was changed for these documentation notes.
