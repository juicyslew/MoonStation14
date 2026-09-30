package com.juicyslew.moonstation14.ms14.movement.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.mixin.MovementTeleportStateAccessor;
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
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.protocol.MovementNetworking;
import com.juicyslew.moonstation14.ms14.movement.protocol.MovementPayloads;
import com.juicyslew.moonstation14.ms14.movement.protocol.MovementSession;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Experimental, server-authoritative bridge from movement protocol to the M1 motor. */
public final class MovementServerController {
    private static final double TICK_SECONDS = 1d / 20d;
    // Vanilla LivingEntity travel uses 0.08 blocks/tick^2 gravity and 0.42 blocks/tick jump velocity.
    // Conversion to the motor's blocks/second units is respectively 0.08 * 20^2 and 0.42 * 20.
    private static final double GRAVITY_PER_SECOND_SQUARED = 0.08d * 20d * 20d;
    private static final double JUMP_VELOCITY_PER_SECOND = 0.42d * 20d;
    private static final double VERTICAL_DRAG = 0.98d;
    // Experimental handshake allowance, not a packet-rate or performance budget.
    private static final long BEGIN_TIMEOUT_TICKS = 5L * 20L;
    private static final AtomicLong NEXT_EPOCH = new AtomicLong(1);
    private static final Map<ServerPlayer, PlayerSession> SESSIONS = new IdentityHashMap<>();

    private MovementServerController() { }

    public static void install() {
        MovementNetworking.installServerHandler(MovementServerController::onPayload);
    }

    /** Called after the pinned vanilla packet-position restore point; intentionally not wired until its mixin lands. */
    public static void onAfterVanillaRestore(ServerPlayer player) {
        if (!MovementStartupGate.enabledForServer()) return;
        PlayerSession tracked = SESSIONS.get(player);
        if (tracked == null) {
            if (!eligible(player)) return;
            long epoch = NEXT_EPOCH.getAndIncrement();
            if (epoch <= 0) {
                NEXT_EPOCH.set(2);
                epoch = 1;
            }
            MovementSession session = new MovementSession();
            if (session.begin(epoch, true, true, player.onGround()) != MovementSession.Result.ACCEPTED) return;
            tracked = new PlayerSession(session);
            SESSIONS.put(player, tracked);
            tracked.surfaceTrace.reset();
            tracked.beginTick = player.level().getGameTime();
            MovementNetworking.sendToPlayer(player, new MovementPayloads.Begin(epoch));
            MoonStation14.LOGGER.info("[movement server] Begin dispatched for {} at epoch {}",
                    player.getGameProfile().getName(), epoch);
            return;
        }

        MovementSession session = tracked.protocol;
        if (session.phase() == MovementSession.Phase.VANILLA) {
            beginIfEligible(player, tracked);
            return;
        }
        if (session.phase() == MovementSession.Phase.PENDING_BEGIN) {
            if (!eligible(player)) {
                abortPendingBegin(player, tracked, "eligibility was lost");
                return;
            }
            if (player.level().getGameTime() - tracked.beginTick >= BEGIN_TIMEOUT_TICKS) {
                abortPendingBegin(player, tracked, "acknowledgement timed out");
                return;
            }
            if (!tracked.ackReceived) return;
            if (session.commit(session.epoch(), true) == MovementSession.Result.ACCEPTED) {
                tracked.surfaceTrace.reset();
                tracked.lastCommand = null;
                MovementNetworking.sendToPlayer(player, new MovementPayloads.Commit(session.epoch()));
                MoonStation14.LOGGER.info("[movement server] Commit activated; server owns movement for {} at epoch {}",
                        player.getGameProfile().getName(), session.epoch());
            }
            return;
        }
        if (session.phase() == MovementSession.Phase.PENDING_END) {
            if (expirePendingEnd(player, tracked)) return;
            advanceDisable(player, tracked);
            return;
        }
        if (!session.customOwnsMovement()) return;
        CharacterMovementPolicy policy = currentPolicy(player);
        if (!supportedCustomMode(player) || policy == null) {
            if (session.beginDisable(session.epoch(), true) == MovementSession.Result.ACCEPTED) {
                tracked.surfaceTrace.reset();
                tracked.pendingEndTick = player.level().getGameTime();
                MovementNetworking.sendToPlayer(player, new MovementPayloads.Disable(session.epoch()));
                MoonStation14.LOGGER.warn("Experimental movement disabled for {} at unsupported movement mode; " +
                        "custom ownership is retained fail-closed", player.getGameProfile().getName());
            }
            return;
        }

        CharacterMovementCommand command = tracked.lastCommand;
        CharacterMovementMotor motor = new CharacterMovementMotor(policy);
        if (command == null) command = new CharacterMovementCommand(0, 0, false, false);
        boolean stunned = CharacterControlSystem.isStunned(player);
        double voluntarySpeedFactor = CharacterControlSystem.isKnockedDown(player)
                ? CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR : 1d;
        Vec3 initial = player.position();
        Vec3 vanillaVelocity = player.getDeltaMovement();
        double surfaceFactor = SlidingFrictionSystem.frictionFactor(player);
        double wishX = stunned ? 0d : command.wishX();
        double wishZ = stunned ? 0d : command.wishZ();
        double wishLength = Math.hypot(wishX, wishZ);
        double wishSpeed = wishLength
                * (command.sprint() ? policy.sprintSpeedPerSecond()
                : policy.walkSpeedPerSecond()) * voluntarySpeedFactor;
        double preFrictionWishProjection = wishLength == 0d ? 0d
                : (vanillaVelocity.x * wishX + vanillaVelocity.z * wishZ)
                / wishLength * 20d;
        CharacterMovementState state = new CharacterMovementState(new MovementVector(initial.x, initial.y, initial.z),
                new MovementVector(vanillaVelocity.x * 20d, vanillaVelocity.y * 20d, vanillaVelocity.z * 20d),
                player.onGround());
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            // Entity.move performs Minecraft's collision/step resolution exactly once. Do not set position afterward.
            Vec3 before = player.position();
            player.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
            Vec3 after = player.position();
            Vec3 acceptedDisplacement = after.subtract(before);
            // Preserve vanilla accepted-movement bookkeeping, sourced only from Entity.move.
            player.setKnownMovement(acceptedDisplacement);
            player.serverLevel().getChunkSource().move(player);
            return new MovementCollisionResolver.CollisionResult(
                    new MovementVector(acceptedDisplacement.x, acceptedDisplacement.y, acceptedDisplacement.z),
                    player.onGround());
        };
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(TICK_SECONDS,
                GRAVITY_PER_SECOND_SQUARED, JUMP_VELOCITY_PER_SECOND, VERTICAL_DRAG, MovementVector.ZERO,
                 0d, player.maxUpStep(), resolver, surfaceFactor, voluntarySpeedFactor);
        MovementSurfaceTrace.Summary surfaceSummary = tracked.surfaceTrace.sample(player.level().getGameTime(),
                com.juicyslew.moonstation14.ms14.slip.SlipSystem.isSliding(player), player.onGround(), surfaceFactor,
                Math.hypot(vanillaVelocity.x, vanillaVelocity.z) * 20d, wishSpeed, preFrictionWishProjection);
        if (surfaceSummary != null) {
            // This is a bounded correlation aid only: neither a lube contact transition nor the
            // pre-friction projection proves the cause of an owner's reported turn/burst. Correlate
            // with the owner's logs, Slipped HUD, and video before drawing conclusions.
            MoonStation14.LOGGER.info("[movement surface trace] {} ticks={} factor={}..{} slidingNeutral={} " +
                            "slidingLube={} preFrictionWishBlocked={} lubeToNeutral={} maxHorizontalSpeed={} blocks/s",
                    player.getGameProfile().getName(), surfaceSummary.samples(), surfaceSummary.minimumFactor(),
                    surfaceSummary.maximumFactor(), surfaceSummary.slidingNeutralTicks(), surfaceSummary.slidingLubeTicks(),
                    surfaceSummary.projectedWishBlockedTicks(), surfaceSummary.lubeToNeutralTransitions(),
                    surfaceSummary.maximumHorizontalSpeed());
        }
        CharacterMovementState result;
        try {
            result = motor.tick(state, command, environment, stunned);
        } catch (IllegalArgumentException exception) {
            // Entity.move may already have applied its collision-resolved displacement. Do not
            // restore vanilla state or retry; stop this owned tick and disconnect fail-closed.
            failClosed(player, tracked, "collision result rejected (IllegalArgumentException)");
            return;
        }
        // Collision resolver moved the entity as a side effect; only publish resulting velocity here.
        player.setDeltaMovement(result.velocity().x() / 20d, result.velocity().y() / 20d,
                result.velocity().z() / 20d);
        Vec3 acceptedDisplacement = player.position().subtract(initial);
        Vec3 velocityBeforeSlip = player.getDeltaMovement();
        com.juicyslew.moonstation14.ms14.slip.SlipSystem.onAcceptedPlayerMovement(player, acceptedDisplacement);
        Vec3 velocityAfterSlip = player.getDeltaMovement();
        double speedBeforeSlip = Math.hypot(velocityBeforeSlip.x, velocityBeforeSlip.z);
        double speedAfterSlip = Math.hypot(velocityAfterSlip.x, velocityAfterSlip.z);
        if (Double.isFinite(speedBeforeSlip) && Double.isFinite(speedAfterSlip)
                && (Double.compare(velocityBeforeSlip.x, velocityAfterSlip.x) != 0
                || Double.compare(velocityBeforeSlip.z, velocityAfterSlip.z) != 0)) {
            MoonStation14.LOGGER.info("[movement server] admitted slip launch changed horizontal speed for {}: {} -> {} blocks/s",
                    player.getGameProfile().getName(), speedBeforeSlip * 20d, speedAfterSlip * 20d);
        }
        long acknowledged = session.lastSequence();
        Vec3 position = player.position();
        Vec3 velocity = player.getDeltaMovement();
        // Deliberately verbose early-smoke behavior: one authoritative snapshot per owned tick;
        // reduce this cadence/bandwidth before production rollout.
        MovementNetworking.sendToPlayer(player, new MovementPayloads.Snapshot(session.epoch(), acknowledged,
                position.x, position.y, position.z, velocity.x * 20d, velocity.y * 20d, velocity.z * 20d,
                player.onGround()));
        tracked.lastCommand = null; // Input is consumed at most once; no stale jump is replayed.
    }

    public static boolean owns(ServerPlayer player) {
        PlayerSession session = SESSIONS.get(player);
        return session != null && session.protocol.customOwnsMovement();
    }

    public static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!MovementStartupGate.enabledForServer() || !(context.player() instanceof ServerPlayer player)) return;
        PlayerSession tracked = SESSIONS.get(player);
        if (tracked == null) return;
        if (tracked.protocol.phase() == MovementSession.Phase.PENDING_END
                && expirePendingEnd(player, tracked)) return;
        if (payload instanceof MovementPayloads.Acknowledge ack) {
            if (tracked.protocol.phase() == MovementSession.Phase.PENDING_BEGIN
                    && tracked.protocol.acknowledge(ack.epoch(), true) == MovementSession.Result.ACCEPTED)
                tracked.ackReceived = true;
        } else if (payload instanceof MovementPayloads.DisableAcknowledge ack) {
            if (tracked.protocol.phase() == MovementSession.Phase.PENDING_END
                    && tracked.protocol.acknowledgeDisable(ack.epoch(), true) == MovementSession.Result.ACCEPTED) {
                tracked.disableAcknowledged = true;
                tracked.transitionDimension = player.level().dimension();
                advanceDisable(player, tracked);
            }
        } else if (payload instanceof MovementPayloads.ResumeAcknowledge ack) {
            if (tracked.protocol.phase() == MovementSession.Phase.PENDING_END && tracked.resumeSent
                    && tracked.protocol.finishDisable(ack.epoch(), true) == MovementSession.Result.ACCEPTED) {
                MoonStation14.LOGGER.info("[movement server] final ResumeAcknowledge received; vanilla owns movement for {} at epoch {}",
                        player.getGameProfile().getName(), ack.epoch());
                tracked.clearTransition();
            }
        } else if (payload instanceof MovementPayloads.Intent intent) {
            if (!tracked.protocol.customOwnsMovement() || intent.epoch() != tracked.protocol.epoch()) return;
            long expected = nextSequence(tracked.protocol);
            if (intent.sequence() != expected) {
                MoonStation14.LOGGER.warn("Experimental movement rejected {} sequence for {} (expected {}, received {}); " +
                                "future gaps may starve input until session reset",
                        intent.sequence() < expected ? "stale" : "future",
                        player.getGameProfile().getName(), expected, intent.sequence());
                return;
            }
            if (!acceptNextSequence(tracked.protocol, intent.epoch(), intent.sequence())) return;
            tracked.lastCommand = new CharacterMovementCommand(intent.wishX() / 1000d, intent.wishZ() / 1000d,
                    (intent.buttons() & MovementPayloads.BUTTON_JUMP) != 0,
                    (intent.buttons() & MovementPayloads.BUTTON_SPRINT) != 0);
        }
    }

    /** Called after vanilla has completed accept-teleport handling on the server thread. */
    public static void onVanillaTeleportAccepted(ServerPlayer player, int packetId) {
        if (!MovementStartupGate.enabledForServer()) return;
        PlayerSession tracked = SESSIONS.get(player);
        if (tracked == null || tracked.protocol.phase() != MovementSession.Phase.PENDING_END
                || tracked.expectedTeleportId == null || tracked.expectedTeleportId != packetId) return;
        if (expirePendingEnd(player, tracked)) return;
        ServerGamePacketListenerImpl listener = player.connection;
        MovementTeleportStateAccessor state = (MovementTeleportStateAccessor) listener;
        if (state.moonstation14$getAwaitingPositionFromClient() != null) {
            failClosed(player, tracked, "vanilla teleport state was not cleared after the expected acknowledgement");
            return;
        }
        if (tracked.protocol.acknowledgeTeleport(tracked.protocol.epoch(), true) == MovementSession.Result.ACCEPTED
                && tracked.protocol.authorizeResume(tracked.protocol.epoch(), true) == MovementSession.Result.ACCEPTED) {
            tracked.resumeSent = true;
            MovementNetworking.sendToPlayer(player, new MovementPayloads.ResumeVanilla(tracked.protocol.epoch()));
            MoonStation14.LOGGER.info("[movement server] ResumeVanilla sent after teleport acknowledgement for {} at epoch {}",
                    player.getGameProfile().getName(), tracked.protocol.epoch());
        }
    }

    private static void advanceDisable(ServerPlayer player, PlayerSession tracked) {
        if (!tracked.protocol.customOwnsMovement() || !tracked.disableAcknowledged) return;
        if (tracked.transitionDimension != null && !tracked.transitionDimension.equals(player.level().dimension())) {
            failClosed(player, tracked, "dimension changed during vanilla movement handoff");
            return;
        }
        MovementTeleportStateAccessor state = (MovementTeleportStateAccessor) player.connection;
        int pendingId = state.moonstation14$getAwaitingTeleport();
        Vec3 pendingPosition = state.moonstation14$getAwaitingPositionFromClient();
        BarrierState barrierState = barrierState(tracked.expectedTeleportId, pendingId,
                pendingPosition != null, tracked.resumeSent);
        if (barrierState == BarrierState.RESUME_SENT) return;
        if (barrierState == BarrierState.REPLACED || barrierState == BarrierState.ACKNOWLEDGEMENT_MISSING) {
            if (barrierState == BarrierState.REPLACED) {
                failClosed(player, tracked, "another teleport replaced the handoff barrier");
            } else {
                failClosed(player, tracked, "handoff barrier cleared without an accepted acknowledgement");
            }
            return;
        }
        // A teleport already in flight belongs to vanilla. Let its real acknowledgement complete first.
        if (barrierState == BarrierState.WAITING_FOR_VANILLA) return;
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        int issuedId = state.moonstation14$getAwaitingTeleport();
        if (issuedId == 0 || state.moonstation14$getAwaitingPositionFromClient() == null) {
            failClosed(player, tracked, "could not capture vanilla teleport barrier state");
            return;
        }
        tracked.expectedTeleportId = issuedId;
    }

    static BarrierState barrierState(Integer expectedId, int pendingId, boolean hasPendingPosition, boolean resumeSent) {
        if (resumeSent) return BarrierState.RESUME_SENT;
        if (expectedId == null) return hasPendingPosition ? BarrierState.WAITING_FOR_VANILLA : BarrierState.READY_TO_ISSUE;
        if (!hasPendingPosition) return BarrierState.ACKNOWLEDGEMENT_MISSING;
        return pendingId == expectedId ? BarrierState.WAITING_FOR_BARRIER : BarrierState.REPLACED;
    }

    enum BarrierState {
        READY_TO_ISSUE,
        WAITING_FOR_VANILLA,
        WAITING_FOR_BARRIER,
        REPLACED,
        ACKNOWLEDGEMENT_MISSING,
        RESUME_SENT
    }

    private static void beginIfEligible(ServerPlayer player, PlayerSession tracked) {
        if (!eligible(player)) return;
        long epoch = NEXT_EPOCH.getAndIncrement();
        if (epoch <= 0) {
            NEXT_EPOCH.set(2);
            epoch = 1;
        }
        if (tracked.protocol.begin(epoch, true, true, player.onGround()) != MovementSession.Result.ACCEPTED) return;
        tracked.surfaceTrace.reset();
        tracked.beginTick = player.level().getGameTime();
        tracked.lastCommand = null;
        tracked.ackReceived = false;
        MovementNetworking.sendToPlayer(player, new MovementPayloads.Begin(epoch));
        MoonStation14.LOGGER.info("[movement server] Begin dispatched for {} at epoch {}",
                player.getGameProfile().getName(), epoch);
    }

    private static void failClosed(ServerPlayer player, PlayerSession tracked, String reason) {
        if (tracked.handoffFailed) return;
        tracked.handoffFailed = true;
        MoonStation14.LOGGER.error("Experimental movement handoff failed closed for {}: {}",
                player.getGameProfile().getName(), reason);
        player.connection.disconnect(Component.literal("Movement handoff failed safely; please reconnect."));
    }

    private static boolean expirePendingEnd(ServerPlayer player, PlayerSession tracked) {
        if (!isPendingEndTimedOut(tracked.pendingEndTick, player.level().getGameTime())) return false;
        failClosed(player, tracked, "pending-end handshake timed out");
        return true;
    }

    /** Experimental bound reused from begin; this is not a measured packet budget. */
    static boolean isPendingEndTimedOut(long startedTick, long currentTick) {
        return currentTick - startedTick >= BEGIN_TIMEOUT_TICKS;
    }

    private static void abortPendingBegin(ServerPlayer player, PlayerSession tracked, String reason) {
        long epoch = tracked.protocol.epoch();
        tracked.protocol.reset();
        SESSIONS.remove(player);
        tracked.surfaceTrace.reset();
        // Pending begin never transferred movement ownership, so vanilla remains the only owner.
        MovementNetworking.sendToPlayer(player, new MovementPayloads.Disable(epoch));
        MoonStation14.LOGGER.warn("Experimental movement begin aborted for {}: {}",
                player.getGameProfile().getName(), reason);
    }

    /** Accept exactly the next sequence; multiple calls may occur before the same motor tick. */
    static boolean acceptNextSequence(MovementSession session, long epoch, long sequence) {
        if (sequence != nextSequence(session)) return false;
        return session.acceptIntent(epoch, sequence, true) == MovementSession.Result.ACCEPTED;
    }

    private static long nextSequence(MovementSession session) {
        long lastSequence = session.lastSequence();
        return lastSequence == Long.MAX_VALUE ? Long.MAX_VALUE : lastSequence + 1;
    }

    public static void onDisconnect(ServerPlayer player) {
        PlayerSession tracked = SESSIONS.remove(player);
        if (tracked != null) tracked.surfaceTrace.reset();
    }

    public static void onServerStopped() {
        for (PlayerSession tracked : SESSIONS.values()) tracked.surfaceTrace.reset();
        SESSIONS.clear();
        NEXT_EPOCH.set(1);
        MovementNetworking.installServerHandler(null);
    }

    private static boolean isConnectedHuman(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || player.isRemoved() || player.level().isClientSide
                || player.getServer() == null
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return false;
        var identity = player.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        return identity != null && identity.isBound() && ModCharacters.HUMAN_ID.equals(identity.characterId())
                && CharacterIdentitySystem.resolveForActor(player).isPresent();
    }

    private static boolean eligible(ServerPlayer player) {
        return supportedCustomMode(player) && currentPolicy(player) != null && player.onGround();
    }

    private static CharacterMovementPolicy currentPolicy(ServerPlayer player) {
        try {
            return CharacterIdentitySystem.resolveForActor(player)
                    .map(CharacterMovementPolicy::fromCharacterData).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean supportedCustomMode(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return isConnectedHuman(player) && player.isAlive()
                && (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE)
                && !player.isPassenger() && !player.isFallFlying() && !player.isSwimming()
                && !player.isInWater() && !player.onClimbable() && !player.getAbilities().flying
                && !player.isSpectator();
    }

    private static final class PlayerSession {
        private final MovementSession protocol;
        private final MovementSurfaceTrace surfaceTrace = new MovementSurfaceTrace();
        private boolean ackReceived;
        private CharacterMovementCommand lastCommand;
        private long beginTick;
        private boolean disableAcknowledged;
        private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> transitionDimension;
        private Integer expectedTeleportId;
        private boolean resumeSent;
        private long pendingEndTick;
        private boolean handoffFailed;

        private PlayerSession(MovementSession protocol) { this.protocol = protocol; }
        private void clearTransition() {
            surfaceTrace.reset();
            disableAcknowledged = false;
            transitionDimension = null;
            expectedTeleportId = null;
            resumeSent = false;
            lastCommand = null;
        }
    }
}
