package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Prepared durable GHOST handshake. No death or reconnect path invokes this controller yet. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleGhostSessionControl {
    private static final long READY_TIMEOUT_TICKS = 100;
    private static final int MAX_INTENTS_PER_TICK = 8;
    private static final double WORLD_BOUND = 30_000_000.0;
    private static final double MOTION_BOUNDARY_ULPS = 4d;
    private static final GhostMovementMotor GHOST_MOVEMENT_MOTOR = new GhostMovementMotor();
    private static final Map<MinecraftServer, Map<UUID, Session>> SERVERS = new IdentityHashMap<>();

    private LifecycleGhostSessionControl() { }

    /** Called only after a future trusted caller has durably activated the registered transient ghost. */
    static StartResult beginPrepared(ServerPlayer player, GhostMobHarnessEntity ghost,
                                     PlayerLifecycleRegistry lifecycle, long epoch, MobHarnessId corpseId) {
        StartResult invalid = preflight(player, ghost, lifecycle, epoch, corpseId);
        if (invalid != null) return invalid;
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        Map<UUID, Session> sessions = SERVERS.computeIfAbsent(server, ignored -> new HashMap<>());
        if (sessions.containsKey(player.getUUID())) return StartResult.SESSION_CONFLICT;
        Session session = new Session(player, ghost, lifecycle, epoch, corpseId, player.level().getGameTime());
        sessions.put(player.getUUID(), session);
        try {
            LifecycleSessionPacketRouter router = LifecycleSessionPacketRouter.instance();
            if (!router.ghostHandlerInstalled())
                router.installGhostHandler(LifecycleGhostSessionControl::onPayload, LifecycleGhostSessionControl::ownsAny);
            GhostControlNetworking.sendToPlayer(player,
                    new GhostControlPayloads.Begin(epoch, ghost.getId(), MobHarnessKind.GHOST));
            return StartResult.PREPARED;
        } catch (RuntimeException | Error failure) {
            end(server, session, true, "begin_send_failed");
            throw failure;
        }
    }

    private static StartResult preflight(ServerPlayer player, GhostMobHarnessEntity ghost,
                                        PlayerLifecycleRegistry lifecycle, long epoch, MobHarnessId corpseId) {
        if (player == null || ghost == null || lifecycle == null || corpseId == null || epoch <= 0
                || !(player.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()
                || player instanceof FakePlayer || player.isRemoved() || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || GhostMobHarnessControl.ownsDebugSession(player)) return StartResult.INVALID_PLAYER;
        var profile = lifecycle.profile(player.getUUID()).orElse(null);
        MobHarnessId ghostId = new MobHarnessId(ghost.getUUID());
        if (profile == null || !profile.active() || !profile.deadClaim()
                || profile.state() != PlayerLifecycleRegistry.LifecycleState.GHOST
                || profile.connectionGeneration() != epoch || !profile.bodyId().equals(ghostId)
                || !distinctHarnessIds(corpseId, ghostId) || !live(ghost, level)
                || !lifecycle.authorizes(player.getUUID(), epoch, ghostId,
                        harness -> harness.id().equals(ghostId) && harness.kind() == MobHarnessKind.GHOST))
            return StartResult.INVALID_GHOST;
        return null;
    }

    private static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = session(server, player.getUUID());
        if (session == null || session.player != player || !connectedExact(server, player)) return;
        if (payload instanceof GhostControlPayloads.Ready ready) {
            if (!readyDecision(ready.epoch(), session.epoch, session.committed,
                    level.getGameTime() - session.startedTick, READY_TIMEOUT_TICKS)) return;
            if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                    || !authorized(session) || !live(session.ghost, level)
                    || session.ghost.level() != player.level()
                    || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                end(server, session, true, "ready_validation_failed");
                return;
            }
            player.setCamera(session.ghost);
            session.committed = true;
            GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Commit(session.epoch));
        } else if (payload instanceof GhostControlPayloads.Intent intent) {
            if (!session.committed || !intentMatchesSession(intent, session.epoch)) return;
            if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                    || !intentEligible(session, level)) {
                end(server, session, true, "intent_validation_failed");
                return;
            }
            long tick = level.getGameTime();
            if (session.intentTick != tick) {
                session.intentTick = tick;
                session.intentCount = 0;
            }
            if (!intentRateAllows(++session.intentCount)) return;
            session.gate.offer(intent.sequence(), tick, intent);
            if (session.gate.exhausted()) end(server, session, true, "intent_gate_exhausted");
        } else if (payload instanceof GhostControlPayloads.Stop stop && stop.epoch() == session.epoch) {
            end(server, session, true, "client_requested_stop");
        }
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session == null || session.player != player || !session.committed) return;
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                || !connectedExact(server, player) || !authorized(session)
                || !shouldKeepGhostCamera(player) || !intentEligible(session, level)) {
            end(server, session, true, "session_authority_or_eligibility_lost");
            return;
        }
        GhostControlPayloads.Intent intent = session.gate.take();
        if (intent != null) {
            try {
                GhostMobHarnessEntity ghost = session.ghost;
                ghost.setNoGravity(true);
                ghost.noPhysics = true;
                ghost.setYRot(intent.yaw());
                ghost.setXRot(intent.pitch());
                ghost.setDeltaMovement(Vec3.ZERO);
                MovementVector before = movementPosition(ghost);
                var result = GHOST_MOVEMENT_MOTOR.tick(before, intent.wishX(), intent.wishZ(), intent.verticalWish(),
                        (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, intent.yaw(),
                        (position, requested, wasOnGround) -> {
                            Vec3 prior = ghost.position();
                            ghost.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
                            Vec3 moved = ghost.position().subtract(prior);
                            return new MovementCollisionResolver.CollisionResult(
                                    new MovementVector(moved.x, moved.y, moved.z), ghost.onGround());
                        });
                Vec3 displacement = ghost.position().subtract(new Vec3(before.x(), before.y(), before.z()));
                MovementVector actual = movementPosition(ghost);
                if (!validMotion(actual, displacement, result.position())) {
                    end(server, session, true, "invalid_motion");
                    return;
                }
                ghost.setDeltaMovement(Vec3.ZERO);
                session.lastAppliedSequence = intent.sequence();
            } catch (RuntimeException | Error failure) {
                end(server, session, true, "movement_processing_failed");
                return;
            }
        }
        sendSnapshot(server, session);
    }

    public static boolean shouldKeepGhostCamera(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return false;
        MinecraftServer server = player.level().getServer();
        Session session = session(server, player.getUUID());
        return session != null && session.player == player && session.committed
                && connectedExact(server, player) && authorized(session)
                && session.ghost.level() == player.level() && live(session.ghost, (ServerLevel) player.level())
                && player.getCamera() == session.ghost
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                    || !connectedExact(server, session.player)
                    || session.ghost.level() != session.player.level()
                    || !live(session.ghost, (ServerLevel) session.ghost.level())
                    || !session.committed && session.player.level().getGameTime() - session.startedTick >= READY_TIMEOUT_TICKS)
                end(server, session, true, "session_expired_or_invalid");
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session != null && session.player == player) end(server, session, false, "player_disconnected");
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) { clearServer(event.getServer()); }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) { clearServer(event.getServer()); }

    private static void clearServer(MinecraftServer server) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions != null) for (Session session : sessions.values().toArray(Session[]::new))
            end(server, session, false, "server_stopping");
    }

    private static void end(MinecraftServer server, Session session, boolean disconnect, String reason) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.accountId) != session) return;
        sessions.remove(session.accountId);
        if (sessions.isEmpty()) SERVERS.remove(server);
        session.gate.reset();
        boolean returned = session.lifecycle.returnGhostToDeadClaim(session.accountId, session.mindId,
                session.ghostId, session.corpseId, session.epoch).isPresent();
        if (disconnect) com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "[lifecycle ghost] Ending ghost session reason={} recovery={}", reason,
                returned ? "saved_death_preserved" : "explicit_recovery_required");
        unregisterAndDiscard(session);
        if (session.player.getCamera() == session.ghost) session.player.setCamera(session.player);
        if (!returned) {
            // The registry's failed exact revocation suspends lifecycle authority for recovery.
            if (connectedExact(server, session.player)) session.player.connection.disconnect(
                    net.minecraft.network.chat.Component.literal(
                            "Ghost control stopped, but recovery needs administrator attention."));
        } else if (disconnect && connectedExact(server, session.player)) {
            session.player.connection.disconnect(net.minecraft.network.chat.Component.literal(
                    "Your original body died. Ghost control has stopped, and the death remains saved. "
                            + "Reconnect once; if this keeps happening, contact an admin."));
        }
        clearRouterIfEmpty();
    }

    private static void unregisterAndDiscard(Session session) {
        session.lifecycle.unregisterTransientGhost(session.accountId, session.ghostId);
        if (!session.ghost.isRemoved()) session.ghost.discard();
    }

    private static void clearRouterIfEmpty() {
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearGhostHandler();
    }

    private static boolean authorized(Session session) {
        var profile = session.lifecycle.profile(session.accountId).orElse(null);
        return profile != null && profile.active() && profile.deadClaim()
                && profile.state() == PlayerLifecycleRegistry.LifecycleState.GHOST
                && profile.connectionGeneration() == session.epoch && profile.mindId().equals(session.mindId)
                && profile.bodyId().equals(session.ghostId)
                && session.lifecycle.authorizes(session.accountId, session.epoch, session.ghostId,
                harness -> harness.id().equals(session.ghostId) && harness.kind() == MobHarnessKind.GHOST);
    }

    private static boolean intentEligible(Session session, ServerLevel level) {
        return session.ghostId.equals(new MobHarnessId(session.ghost.getUUID()))
                && session.ghost.level() == session.player.level() && session.ghost.level() == level
                && live(session.ghost, level) && session.player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                && session.player.getCamera() == session.ghost && authorized(session);
    }

    private static MovementVector movementPosition(GhostMobHarnessEntity ghost) {
        Vec3 position = ghost.position();
        return new MovementVector(position.x, position.y, position.z);
    }

    static boolean validMotion(MovementVector position, Vec3 displacement, MovementVector resolved) {
        return boundedCoordinate(position.x()) && boundedCoordinate(position.y()) && boundedCoordinate(position.z())
                && Double.isFinite(displacement.x) && Double.isFinite(displacement.y) && Double.isFinite(displacement.z)
                && withinMotionBound(displacement.x, position.x(), GhostMovementMotor.SPRINT_SPEED_PER_SECOND / 20d)
                && withinMotionBound(displacement.y, position.y(), GhostMovementMotor.VERTICAL_SPEED_PER_SECOND / 20d)
                && withinMotionBound(displacement.z, position.z(), GhostMovementMotor.SPRINT_SPEED_PER_SECOND / 20d)
                && Math.abs(position.x() - resolved.x()) <= 1.0e-6
                && Math.abs(position.y() - resolved.y()) <= 1.0e-6
                && Math.abs(position.z() - resolved.z()) <= 1.0e-6;
    }

    private static boolean withinMotionBound(double displacement, double resultingCoordinate, double bound) {
        double priorCoordinate = resultingCoordinate - displacement;
        if (!boundedCoordinate(priorCoordinate)) return false;
        double tolerance = MOTION_BOUNDARY_ULPS * Math.ulp(bound)
                + Math.ulp(priorCoordinate) + Math.ulp(resultingCoordinate);
        return Math.abs(displacement) <= bound + tolerance;
    }

    private static boolean boundedCoordinate(double value) {
        return Double.isFinite(value) && value >= -WORLD_BOUND && value <= WORLD_BOUND;
    }

    private static void sendSnapshot(MinecraftServer server, Session session) {
        GhostMobHarnessEntity ghost = session.ghost;
        long tick = ghost.level().getGameTime();
        if (tick <= session.lastSnapshotTick) return;
        Vec3 position = ghost.position();
        Vec3 velocity = ghost.getDeltaMovement().scale(20d);
        float yaw = (float) (ghost.getYRot() - 360.0 * Math.floor((ghost.getYRot() + 180.0) / 360.0));
        float pitch = Math.max(-90f, Math.min(90f, ghost.getXRot()));
        try {
            var snapshot = new GhostControlPayloads.Snapshot(session.epoch, ghost.getId(), MobHarnessKind.GHOST,
                    tick, session.lastAppliedSequence, position.x, position.y, position.z, yaw, pitch,
                    velocity.x, velocity.y, velocity.z, false, 1f, 1f, false);
            if (!snapshotBoundToSession(snapshot, session.epoch, ghost.getId())
                    || !session.ghostId.equals(new MobHarnessId(ghost.getUUID()))) {
                end(server, session, true, "snapshot_binding_invalid");
                return;
            }
            session.lastSnapshotTick = tick;
            GhostControlNetworking.sendToPlayer(session.player, snapshot);
        } catch (RuntimeException failure) {
            end(server, session, true, "snapshot_send_failed");
        }
    }

    static boolean snapshotBoundToSession(GhostControlPayloads.Snapshot snapshot, long epoch, int entityId) {
        return snapshot != null && snapshot.epoch() == epoch && snapshot.harnessEntityId() == entityId
                && snapshot.harnessKind() == MobHarnessKind.GHOST && epoch > 0 && entityId >= 0;
    }

    static boolean intentMatchesSession(GhostControlPayloads.Intent intent, long epoch) {
        return intent != null && epoch > 0 && intent.epoch() == epoch;
    }

    static boolean intentRateAllows(int count) {
        return count > 0 && count <= MAX_INTENTS_PER_TICK;
    }

    private static boolean live(GhostMobHarnessEntity ghost, ServerLevel level) {
        return ghost != null && !ghost.isRemoved() && ghost.isAlive() && ghost.isAddedToLevel()
                && ghost.level() == level && level.getEntity(ghost.getUUID()) == ghost && !ghost.isPassenger();
    }

    private static boolean connectedExact(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null && !player.isRemoved() && !(player instanceof FakePlayer)
                && player.level().getServer() == server && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static boolean owns(MinecraftServer server, ServerPlayer player) {
        Session session = player == null ? null : session(server, player.getUUID());
        return exactSessionOwnerMatches(session != null, session != null && session.player == player,
                connectedExact(server, player));
    }

    static boolean ownsAny(ServerPlayer player) {
        return player != null && owns(player.level().getServer(), player);
    }

    private static Session session(MinecraftServer server, UUID account) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        return sessions == null ? null : sessions.get(account);
    }

    static boolean readyDecision(long readyEpoch, long sessionEpoch, boolean committed, long elapsedTicks,
                                 long timeoutTicks) {
        return !committed && readyEpoch == sessionEpoch && elapsedTicks >= 0 && elapsedTicks < timeoutTicks;
    }

    static boolean distinctHarnessIds(MobHarnessId corpse, MobHarnessId ghost) {
        return corpse != null && ghost != null && !corpse.equals(ghost);
    }

    static boolean exactSessionOwnerMatches(boolean sessionExists, boolean exactPlayerInstance,
                                            boolean connectedOnOwningServer) {
        return sessionExists && exactPlayerInstance && connectedOnOwningServer;
    }

    enum StartResult { INVALID_PLAYER, INVALID_GHOST, SESSION_CONFLICT, PREPARED }

    private static final class Session {
        final ServerPlayer player;
        final GhostMobHarnessEntity ghost;
        final PlayerLifecycleRegistry lifecycle;
        final UUID accountId;
        final com.juicyslew.moonstation14.ms14.player_body_control.MindId mindId;
        final MobHarnessId ghostId, corpseId;
        final long epoch, startedTick;
        boolean committed;
        final GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        long intentTick = Long.MIN_VALUE, lastAppliedSequence, lastSnapshotTick = Long.MIN_VALUE;
        int intentCount;

        Session(ServerPlayer player, GhostMobHarnessEntity ghost, PlayerLifecycleRegistry lifecycle,
                long epoch, MobHarnessId corpseId, long startedTick) {
            this.player = player; this.ghost = ghost; this.lifecycle = lifecycle; this.epoch = epoch;
            this.accountId = player.getUUID(); this.mindId = lifecycle.profile(accountId).orElseThrow().mindId();
            this.ghostId = new MobHarnessId(ghost.getUUID()); this.corpseId = corpseId; this.startedTick = startedTick;
        }
    }
}
