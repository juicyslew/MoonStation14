package com.juicyslew.moonstation14.ms14.movement.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.protocol.MovementNetworking;
import com.juicyslew.moonstation14.ms14.movement.protocol.MovementPayloads;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.juicyslew.moonstation14.mixin.client.LivingEntityMovementAnimationInvoker;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Provisional client-frame prediction; bounded pending displacement preservation is not input replay, and the server remains authoritative. */
public final class MovementClientController {
    private static final double TICK_SECONDS = 1d / 20d;
    private static final double GRAVITY_PER_SECOND_SQUARED = 0.08d * 20d * 20d;
    private static final double JUMP_VELOCITY_PER_SECOND = 0.42d * 20d;
    private static final double VERTICAL_DRAG = 0.98d;
    private static long beginEpoch;
    private static long activeEpoch;
    private static long sentSequence;
    private static long acknowledgedSequence;
    private static boolean committed;
    private static boolean failedClosed;
    private static boolean pendingExit;
    private static boolean disableAcknowledged;
    private static LocalPlayer committedPlayer;
    private static long recentlyExitedEpoch;
    private static final MovementPredictionHistory predictionHistory = new MovementPredictionHistory();
    private static final MovementCorrectionMetrics correctionMetrics = new MovementCorrectionMetrics();
    private static long lastReceivedAck;
    private static boolean hasReceivedAck;
    private static PendingIntentFrame pendingIntentFrame;
    private static LocalPlayer lastTravelPlayer;
    private static int lastTravelTick = Integer.MIN_VALUE;
    private static LocalPlayer visualFacingPlayer;
    private static float previousVisualYaw;
    private static float currentVisualYaw;
    private static boolean hasVisualYaw;

    private record PendingIntentFrame(long epoch, long sequence, short x, short z, int buttons) { }

    private MovementClientController() { }

    public static void install() {
        MovementNetworking.installClientHandler(MovementClientController::onPayload);
    }

    private static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(context.player() instanceof LocalPlayer player) || player != minecraft.player || minecraft.level == null) return;
        if (committed && committedPlayer != player) reset();
        if (payload instanceof MovementPayloads.Begin begin) {
            if (begin.epoch() == recentlyExitedEpoch) return;
            if (!committed && !failedClosed && beginEpoch == 0) {
                if (currentPolicy(player) == null) {
                    failClosed("client character movement policy unavailable at begin");
                    return;
                }
                clearPendingFrame();
                clearVisualFacing();
                beginEpoch = begin.epoch();
                sentSequence = 0;
                acknowledgedSequence = 0;
                predictionHistory.clear();
                resetCorrectionDiagnostics(player);
                MovementNetworking.sendToServer(new MovementPayloads.Acknowledge(begin.epoch()));
                MoonStation14.LOGGER.info("[movement client] Begin acknowledged at epoch {}", begin.epoch());
            }
        } else if (payload instanceof MovementPayloads.Commit commit) {
            if (commit.epoch() == recentlyExitedEpoch) return;
            if (beginEpoch != 0 && commit.epoch() == beginEpoch && !failedClosed) {
                if (currentPolicy(player) == null) {
                    failClosed("client character movement policy unavailable at commit");
                    return;
                }
                activeEpoch = commit.epoch();
                committedPlayer = player;
                committed = true;
                clearVisualFacing();
                clearPendingFrame();
                predictionHistory.clear();
                resetCorrectionDiagnostics(player);
                MoonStation14.LOGGER.info("[movement client] Commit activated at epoch {}", commit.epoch());
            }
        } else if (payload instanceof MovementPayloads.Snapshot snapshot) {
            if (snapshot.epoch() == recentlyExitedEpoch || pendingExit) return;
            CharacterMovementPolicy policy = currentPolicy(player);
            if (policy == null) {
                failClosed("client character movement policy unavailable at snapshot");
                return;
            }
            if (!committed || snapshot.epoch() != activeEpoch || snapshot.acknowledgedSequence() < acknowledgedSequence
                    || snapshot.acknowledgedSequence() > sentSequence) {
                failClosed("stale or invalid authoritative snapshot");
                return;
            }
            boolean duplicateAck = hasReceivedAck && snapshot.acknowledgedSequence() == lastReceivedAck;
            acknowledgedSequence = snapshot.acknowledgedSequence();
            MovementPredictionHistory.Acknowledgement acknowledgement =
                    predictionHistory.acknowledge(snapshot.acknowledgedSequence());
            boolean duplicateAckMissingHistoryWhilePending = duplicateAck
                    && acknowledgement.predictedEnd() == null && acknowledgement.hasPending();
            lastReceivedAck = snapshot.acknowledgedSequence();
            hasReceivedAck = true;
            MovementPredictionHistory.Position authoritative = new MovementPredictionHistory.Position(
                    snapshot.x(), snapshot.y(), snapshot.z());
            Vec3 playerPosition = player.position();
            MovementPredictionHistory.Position current = new MovementPredictionHistory.Position(
                    playerPosition.x, playerPosition.y, playerPosition.z);
            boolean projectedCorrection = false;
            boolean hardAuthoritySnap = false;
            MovementPredictionHistory.Position appliedPosition = null;
            MovementPredictionHistory.Position projected = MovementPredictionHistory.project(authoritative, current,
                    snapshot.onGround(), acknowledgement.predictedEnd(), player.onGround(),
                    acknowledgement.hasPending(), policy.sprintSpeedPerSecond() * TICK_SECONDS);
            if (projected != null && !MovementPredictionHistory.effectivelyEqual(current, projected)) {
                Vec3 offset = new Vec3(projected.x() - current.x(), projected.y() - current.y(),
                        projected.z() - current.z());
                if (minecraft.level.noCollision(player, player.getBoundingBox().move(offset))) {
                    appliedPosition = projected;
                    projectedCorrection = true;
                } else {
                    appliedPosition = authoritative;
                    hardAuthoritySnap = true;
                }
            } else if (projected == null) {
                // Missing history, large/ground-changing correction, or no pending samples: authority wins.
                appliedPosition = authoritative;
                hardAuthoritySnap = true;
            }
            if (appliedPosition != null)
                player.setPos(appliedPosition.x(), appliedPosition.y(), appliedPosition.z());
            double appliedCorrection = appliedPosition == null ? 0d
                    : appliedPosition.minus(current).length();
            correctionMetrics.record(projectedCorrection, hardAuthoritySnap,
                    duplicateAckMissingHistoryWhilePending, appliedCorrection, snapshot.onGround(), player.onGround(),
                    predictionHistory.size());
            player.setDeltaMovement(snapshot.velocityX() / 20d, snapshot.velocityY() / 20d,
                    snapshot.velocityZ() / 20d);
            player.setOnGround(snapshot.onGround());
        } else if (payload instanceof MovementPayloads.Disable disable) {
            if (disable.epoch() == recentlyExitedEpoch) return;
            if (beginEpoch != 0 && disable.epoch() == beginEpoch && !committed) reset();
            else if (committed && !pendingExit && disable.epoch() == activeEpoch && committedPlayer == player) {
                // Keep ownership (and the travel suppression hook) until the server confirms its teleport barrier.
                // Stop issuing custom movement immediately and acknowledge this handoff only once.
                pendingExit = true;
                clearVisualFacing();
                resetCorrectionDiagnostics(player);
                clearPendingFrame();
                predictionHistory.clear();
                if (!disableAcknowledged) {
                    disableAcknowledged = true;
                    MovementNetworking.sendToServer(new MovementPayloads.DisableAcknowledge(disable.epoch()));
                    MoonStation14.LOGGER.info("[movement client] Disable acknowledged; handoff pending at epoch {}",
                            disable.epoch());
                }
            }
        } else if (payload instanceof MovementPayloads.ResumeVanilla resume) {
            if (resume.epoch() == recentlyExitedEpoch) return;
            if (!pendingExit || !committed || resume.epoch() != activeEpoch || committedPlayer != player) {
                MoonStation14.LOGGER.warn("Ignoring movement resume without a matching pending client handoff");
                return;
            }
            MoonStation14.LOGGER.info("[movement client] ResumeVanilla accepted at epoch {}", resume.epoch());
            recentlyExitedEpoch = activeEpoch;
            clearSession();
            // The server sends this only after it verified the vanilla teleport acknowledgement.
            MovementNetworking.sendToServer(new MovementPayloads.ResumeAcknowledge(resume.epoch()));
        }
    }

    /** Releases the exact input frame predicted during travel, immediately before position reporting. */
    public static void tick(LocalPlayer player) {
        if (committed && committedPlayer != player) {
            // A LocalPlayer replacement is a respawn/clone boundary, not continuity of the old prediction stream.
            reset();
            return;
        }
        if (!committed || pendingExit || failedClosed || player != Minecraft.getInstance().player || player.level() == null
                || player.isPassenger()) return;
        if (currentPolicy(player) == null) {
            failClosed("client character movement policy unavailable at tick");
            return;
        }
        PendingIntentFrame frame = pendingIntentFrame;
        pendingIntentFrame = null;
        if (frame == null) return;
        MovementCorrectionMetrics.Window window = correctionMetrics.takeWindow(player.tickCount);
        if (window != null) {
            MoonStation14.LOGGER.info("[movement client] correction diagnostics ticks={}..{} snapshots={} projected={} hardSnaps={} duplicateAckMissingHistoryPending={} appliedCorrections={} maxCorrectionBlocks={} meanCorrectionBlocks={} serverGroundedSnapshots={} clientGroundedSnapshots={} pendingSamples={}",
                    window.startTick(), window.endTick(), window.snapshots(), window.projectedCorrections(),
                    window.hardAuthoritySnaps(), window.duplicateAcksMissingHistoryWhilePending(),
                    window.appliedCorrectionSamples(), window.maximumCorrection(), window.meanCorrection(),
                    window.serverGroundedSnapshots(), window.clientGroundedSnapshots(), window.pendingSamples());
        }
        MovementNetworking.sendToServer(new MovementPayloads.Intent(
                frame.epoch(), frame.sequence(), frame.x(), frame.z(), frame.buttons()));
    }

    /**
     * Runs at LivingEntity.travel HEAD, after LocalPlayer.aiStep refreshed movement input and before
     * vanilla travel. The matching tick hook only releases this frame; it never predicts movement.
     */
    public static void onTravel(LocalPlayer player, Vec3 input) {
        if (!owns(player) || pendingExit || failedClosed || player.level() == null || player.isPassenger()
                || player.input == null) return;
        CharacterMovementPolicy policy = currentPolicy(player);
        if (policy == null) {
            failClosed("client character movement policy unavailable at travel");
            return;
        }
        CharacterMovementMotor motor = new CharacterMovementMotor(policy);
        if (lastTravelPlayer == player && lastTravelTick == player.tickCount) return;
        lastTravelPlayer = player;
        lastTravelTick = player.tickCount;
        boolean stunned = CharacterControlSystem.isClientActionBlocked(player);
        double voluntarySpeedFactor = CharacterControlSystem.isKnockedDown(player)
                ? CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR : 1d;
        double strafe = stunned ? 0d : player.input.leftImpulse;
        double forward = stunned ? 0d : player.input.forwardImpulse;
        double yaw = Math.toRadians(player.getYRot());
        double wishX = strafe * Math.cos(yaw) - forward * Math.sin(yaw);
        double wishZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        double length = Math.hypot(wishX, wishZ);
        if (length > 1d) { wishX /= length; wishZ /= length; }
        short quantizedX = (short) Math.round(wishX * 1000d);
        short quantizedZ = (short) Math.round(wishZ * 1000d);
        updateVisualFacing(player, quantizedX, quantizedZ);
        int buttons = (!stunned && player.input.jumping ? MovementPayloads.BUTTON_JUMP : 0)
                | (player.input.shiftKeyDown ? MovementPayloads.BUTTON_SNEAK : 0)
                | (player.isSprinting() ? MovementPayloads.BUTTON_SPRINT : 0);
        if (sentSequence == Long.MAX_VALUE) {
            failClosed("movement input sequence exhausted");
            return;
        }
        long sequence = ++sentSequence;
        pendingIntentFrame = new PendingIntentFrame(activeEpoch, sequence, quantizedX, quantizedZ, buttons);

        Vec3 initial = player.position();
        Vec3 initialVelocity = player.getDeltaMovement();
        CharacterMovementState state = new CharacterMovementState(
                new MovementVector(initial.x, initial.y, initial.z),
                new MovementVector(initialVelocity.x * 20d, initialVelocity.y * 20d, initialVelocity.z * 20d),
                player.onGround());
        CharacterMovementCommand command = new CharacterMovementCommand(quantizedX / 1000d, quantizedZ / 1000d,
                (buttons & MovementPayloads.BUTTON_JUMP) != 0,
                (buttons & MovementPayloads.BUTTON_SPRINT) != 0);
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            Vec3 before = player.position();
            player.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 after = player.position();
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(after.x - before.x, after.y - before.y, after.z - before.z), player.onGround());
        };
        CharacterMovementState result;
        try {
            result = motor.tick(state, command,
                    new CharacterMovementEnvironment(TICK_SECONDS, GRAVITY_PER_SECOND_SQUARED,
                            JUMP_VELOCITY_PER_SECOND, VERTICAL_DRAG, MovementVector.ZERO, 0d, player.maxUpStep(), resolver,
                             SlidingFrictionSystem.frictionFactor(player), voluntarySpeedFactor), false);
        } catch (IllegalArgumentException exception) {
            // Collision resolution can move the player before the motor rejects its result. Keep
            // custom ownership fail-closed; never retry or run vanilla movement for this tick.
            failClosed("collision result rejected (IllegalArgumentException)");
            return;
        }
        player.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d, result.velocity().z() / 20d);
        Vec3 predictedPosition = player.position();
        predictionHistory.record(sequence, new MovementPredictionHistory.Position(
                predictedPosition.x, predictedPosition.y, predictedPosition.z), result.onGround());
        ((LivingEntityMovementAnimationInvoker) player).moonstation14$calculateEntityAnimation(false);
    }

    public static boolean owns(LocalPlayer player) {
        return committed && player == committedPlayer && player == Minecraft.getInstance().player;
    }

    private static CharacterMovementPolicy currentPolicy(LocalPlayer player) {
        if (player == null || player.level() == null) return null;
        var identity = player.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound() || !ModCharacters.HUMAN_ID.equals(identity.characterId())) return null;
        try {
            return CharacterIdentitySystem.projectForActor(player)
                    .map(CharacterMovementPolicy::fromCharacterData).orElse(null);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
    }

    /** Client-only render pose angles; these never change the player's gameplay yaw. */
    public static Optional<VisualFacing> visualFacing(LocalPlayer player) {
        return hasVisualYaw && visualFacingPlayer == player ? Optional.of(
                new VisualFacing(previousVisualYaw, currentVisualYaw)) : Optional.empty();
    }

    public record VisualFacing(float previousYaw, float currentYaw) { }

    private static void updateVisualFacing(LocalPlayer player, short x, short z) {
        if (x == 0 && z == 0) return;
        float yaw = (float) MovementVisualFacingMath.yawDegrees(x, z).orElseThrow();
        if (!hasVisualYaw || visualFacingPlayer != player) {
            previousVisualYaw = yaw;
            currentVisualYaw = yaw;
            visualFacingPlayer = player;
            hasVisualYaw = true;
            return;
        }
        previousVisualYaw = currentVisualYaw;
        currentVisualYaw = yaw;
    }

    private static void clearVisualFacing() {
        visualFacingPlayer = null;
        previousVisualYaw = 0f;
        currentVisualYaw = 0f;
        hasVisualYaw = false;
    }

    public static void reset() {
        recentlyExitedEpoch = 0;
        clearSession();
    }

    private static void clearSession() {
        clearVisualFacing();
        clearPendingFrame();
        predictionHistory.clear();
        beginEpoch = 0;
        activeEpoch = 0;
        sentSequence = 0;
        acknowledgedSequence = 0;
        committed = false;
        failedClosed = false;
        pendingExit = false;
        disableAcknowledged = false;
        committedPlayer = null;
        correctionMetrics.reset(0);
        lastReceivedAck = 0;
        hasReceivedAck = false;
    }

    private static void resetCorrectionDiagnostics(LocalPlayer player) {
        correctionMetrics.reset(player.tickCount);
        lastReceivedAck = 0;
        hasReceivedAck = false;
    }

    private static void failClosed(String reason) {
        if (failedClosed) return;
        failedClosed = true;
        clearPendingFrame();
        predictionHistory.clear();
        MoonStation14.LOGGER.error("Experimental client movement prediction stopped fail-closed: {}", reason);
    }

    private static void clearPendingFrame() {
        pendingIntentFrame = null;
        lastTravelPlayer = null;
        lastTravelTick = Integer.MIN_VALUE;
    }
}
