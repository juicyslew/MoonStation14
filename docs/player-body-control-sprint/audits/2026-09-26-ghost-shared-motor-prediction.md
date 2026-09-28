# 2026-09-26 ghost shared-motor prediction audit

**Status: shared ghost movement and local prediction are implemented and pass the reported automated checks; connected-owner behavior remains unverified.** This is the authoritative current movement/prediction status for the experimental ghost slice. It supersedes earlier descriptions of server-only ghost movement and historical GameTest counts, but does not supersede the connected-owner smoke gate or claim that ghost movement feels fixed. See the [sprint instructions](../Instructions.md), [owner smoke checklist](experimental-ghost-owner-smoke.md), and [M2b control-wire audit](m2b-ghost-control-wire.md).

## Current movement and prediction path

- `src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/movement/GhostMovementMotor.java` wraps the existing `CharacterMovementMotor`. Ghost walk/sprint speeds are 8/12, vertical speed is 12, and the ghost is configured `noGravity`/`noPhysics`. The ghost's per-tick prior velocity is reset for instant stop behavior. This reuses the vertical-slice movement calculation; it does not provide collision parity, since `noPhysics` bypasses ordinary and entity collision.
- `GhostMobHarnessControl` runs the motor on the server and calls `Entity.move` once through the measured resolver. The server remains authoritative for the ghost's world position; the client sends quantized bounded intent, not position claims, and cannot decide server status or invoke `Touch`.
- `GhostControlClient` immediately simulates the same motor for the locally camera-owned ghost using quantized intent, and retains a 64-frame `GhostPredictionHistory`. For each committed owned tick the server sends a `Snapshot(epoch, id, serverTick, ack settled-through sequence, x/y/z, yaw/pitch)`. The client reconciles to authority and replays newer pending frames. A `HISTORY_LOST` result fails safe by snapping to authority rather than attempting an incomplete replay.
- The settled-through acknowledgement is deliberately not a count of applied inputs: every sequence at or below it has either been applied or permanently skipped. The server acknowledges the last applied frame only after motor simulation; an earlier gap is never allowed to apply later. This makes client replay discard decisions consistent with server ordering.
- `src/main/java/com/juicyslew/moonstation14/mixin/client/GhostOwnedEntityTrackerMixin.java` runs after the main-thread `PacketUtils` check. It suppresses vanilla relative tracker moves only for the actively owned camera ghost while advancing the `VecDeltaCodec` base. Vanilla absolute teleport, spawn/removal, and tracking for unrelated or non-owned ghosts remain on the ordinary path. On teleport, pending prediction is cleared while retaining sequence progression before the next snapshot, and `ghost.lerpTo(..., 0)` resets interpolation.

This follows the new movement norm for a locally controlled harness: where the movement calculation is deterministic, predict it locally for responsive control, while keeping authoritative collisions, external world effects, and status on the server and handling reconciliation/transitions explicitly. It is not a blanket client-authoritative gameplay rule.

## Gate, status, and evidence

Ghost startup still requires `experimentalMindGhostControl=true` and `experimentalVerticalSliceMovement=false`; the older vertical-slice gate remains default-off. **Do not remove or relax that gate yet.** The grounded Survival/Adventure ServerPlayer route is transitional, not a permanent second movement path, and its replacement has not been proven. The legacy route is scheduled for retirement only after the single Mind/harness replacement passes connected lifecycle, supported-mode, and teleport checks and no residual references remain; see the [single-route architecture decision](../architecture/single-mind-harness-movement-route.md). The ghost's use of the shared motor does not establish those behaviors or mean the old gate can be removed now.

Reported validation for the current revision:

- `& ".\\gradlew.bat" test --rerun-tasks --no-daemon` — `BUILD SUCCESSFUL` (about 1m23s).
- `& ".\\gradlew.bat" build --no-daemon` — passed (about 53s).
- `& ".\\gradlew.bat" runGameTestServer --no-daemon -Pms14GameTestDir=build/gametest-run` — 121/121 required GameTests passed. In `build/gametest-run/logs/latest.log`, the run starts at line 35 (2026-09-26 22:02:14) and completion/pass are at lines 242–243.

The GameTests include a real ghost-motor fixture, but do not exercise an authenticated owner client. In particular, the client-only tracker mixin's runtime injection, prediction, reconciliation, replay, and visual feel remain unverified in a matching connected-owner retest. Do not infer smoothness or owner acceptance from unit tests, GameTests, compile success, server displacement logs, or a camera-only observation. Use only the narrow [owner smoke checklist](experimental-ghost-owner-smoke.md); preserve any jitter, rewind, or teleport behavior as observations and stop on client crash or mixin injection failure.

## Limits and deferred behavior

The existing gate remains command-only and default-off; this audit does not alter authorization, lifecycle, or rollout. Remote/non-owned ghosts remain under vanilla tracking rather than this local predictor. The implementation does not model SS14 `GhostImpassable` wall/fixture behavior, and the legacy HUMAN carrier attachment/HUD identity remains unresolved. A 64-frame pending buffer is an implementation bound, not a performance benchmark. No connected-client acceptance, full SS14 parity, NPC transfer, on-join ghost creation, or broader lifecycle-transition proof is claimed.
