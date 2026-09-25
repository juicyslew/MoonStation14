package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.Config;
import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.TargetEligibility;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import net.minecraft.commands.Commands;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Operator-invoked, deliberately narrow ghost-body control experiment. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class GhostMobHarnessControl {
    private static final long READY_TIMEOUT_TICKS = 100;
    private static final int MAX_INTENTS_PER_TICK = 8;
    private static final double MAX_SPEED = 12.0;
    private static final double MAX_STEP = MAX_SPEED / 20.0;
    private static final Map<MinecraftServer, RuntimeState> SERVERS = new IdentityHashMap<>();

    private GhostMobHarnessControl() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14").requires(source -> source.hasPermission(2))
                .then(Commands.literal("mindghost")
                        .then(Commands.literal("start").executes(context -> start(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("stop").executes(context -> stop(context.getSource().getPlayerOrException())))));
    }

    private static int start(ServerPlayer player) {
        if (!MindGhostStartupGate.enabledForServer()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost control is disabled; set experimentalMindGhostControl = true in config/moonstation14-common.toml and restart."));
            return 0;
        }
        if (!connected(player) || player instanceof FakePlayer || MovementServerController.owns(player)
                || MovementStartupGate.enabledForServer()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Mind ghost unavailable for this player."));
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        RuntimeState state = SERVERS.computeIfAbsent(server, ignored -> {
            GhostControlNetworking.installServerHandler(GhostMobHarnessControl::onPayload);
            return new RuntimeState();
        });
        if (state.sessions.containsKey(player.getUUID())) return 0;

        GameType previous = player.gameMode.getGameModeForPlayer();
        GhostMobHarnessEntity ghost = null;
        MobHarnessId id = null;
        boolean modeChanged = false;
        boolean harnessRegistered = false;
        boolean mindCreated = false;
        boolean inserted = false;
        boolean started = false;
        try {
            Vec3 origin = player.position();
            ghost = GhostMobHarnessRegistration.getEntityType().create(player.level());
            if (ghost == null) return 0;
            ghost.moveTo(origin.x, origin.y, origin.z, player.getYRot(), player.getXRot());
            if (!player.level().addFreshEntity(ghost)) return 0;
            GhostMobHarnessEntity spawnedGhost = ghost;
            id = new MobHarnessId(spawnedGhost.getUUID());
            MobHarnessId harnessId = id;
            TargetEligibility eligible = target -> target.id().equals(harnessId) && live(spawnedGhost, player.level());
            if (!state.registry.registerHarness(new MobHarness(id, MobHarnessKind.GHOST))) {
                return 0;
            }
            harnessRegistered = true;
            var mind = state.registry.createMind(player.getUUID(), id, eligible);
            if (mind.isEmpty()) return 0;
            mindCreated = true;
            if (previous != GameType.SPECTATOR) {
                if (!player.setGameMode(GameType.SPECTATOR)) return 0;
                modeChanged = true;
            }
            long epoch = mind.get().epoch();
            Session session = new Session(player, ghost, id, previous, epoch, player.level().getGameTime(), modeChanged);
            state.sessions.put(player.getUUID(), session);
            inserted = true;
            GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Begin(epoch, ghost.getId()));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost waiting for ghost client ready; stop command recovery: /ms14 mindghost stop"));
            started = true;
            return 1;
        } catch (RuntimeException exception) {
            MoonStation14.LOGGER.error("[mind ghost] Failed to start experimental session", exception);
            return 0;
        } finally {
            if (!started) {
                if (inserted) state.sessions.remove(player.getUUID());
                if (mindCreated) state.registry.logout(player.getUUID());
                if (harnessRegistered) state.registry.unregisterHarness(id);
                if (ghost != null && !ghost.isRemoved()) ghost.discard();
                if (modeChanged && connected(player)
                        && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) player.setGameMode(previous);
            }
        }
    }

    private static int stop(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        RuntimeState state = server == null ? null : SERVERS.get(server);
        if (state == null) return 0;
        Session session = state.sessions.get(player.getUUID());
        if (session == null || session.player != player) return 0;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Mind ghost stopped; player control recovered."));
        end(state, session, true);
        return 1;
    }

    private static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session == null || session.player != player || !connected(player)) return;
        if (payload instanceof GhostControlPayloads.Ready ready) {
            if (ready.epoch() != session.epoch || session.committed) return;
            if (player.level().getGameTime() - session.startedTick >= READY_TIMEOUT_TICKS) {
                handshakeExpired(state, session);
                return;
            }
            if (!eligible(state, session, player)) {
                end(state, session, true);
                return;
            }
            session.ready = true;
            if (session.ready) {
                session.ghost.setYRot(player.getYRot());
                session.ghost.setXRot(player.getXRot());
                session.ghost.setYHeadRot(player.getYRot());
                player.setCamera(session.ghost);
                session.committed = true;
                GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Commit(session.epoch));
                MoonStation14.LOGGER.info("[mind ghost] Ready handshake committed for player={} epoch={} ghostEntityId={} ghostUuid={}",
                        player.getGameProfile().getName(), session.epoch, session.ghost.getId(), session.ghost.getUUID());
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Mind ghost active; /ms14 mindghost stop to recover"));
            }
        } else if (payload instanceof GhostControlPayloads.Intent intent) {
            if (!session.committed || intent.epoch() != session.epoch) return;
            long tick = player.level().getGameTime();
            if (session.intentTick != tick) {
                session.intentTick = tick;
                session.intentCount = 0;
            }
            if (++session.intentCount > MAX_INTENTS_PER_TICK) {
                if (!session.floodLogged) {
                    MoonStation14.LOGGER.warn("[mind ghost] Dropping excessive intents for {}", player.getGameProfile().getName());
                    session.floodLogged = true;
                }
                return;
            }
            if (!eligible(state, session, player)) {
                end(state, session, true);
                return;
            }
            session.gate.offer(intent.sequence(), tick, intent);
            if (session.gate.exhausted()) end(state, session, true);
        }
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session == null || session.player != player) return;
        if (player.level() != session.ghost.level()) {
            end(state, session, true);
            return;
        }
        if (!eligible(state, session, player)) {
            end(state, session, true);
            return;
        }
        if (!session.committed && player.level().getGameTime() - session.startedTick >= READY_TIMEOUT_TICKS) {
            handshakeExpired(state, session);
            return;
        }
        GhostControlPayloads.Intent intent = session.gate.take();
        if (intent == null || !session.committed) return;
        double x = intent.wishX() / 1000.0;
        double z = intent.wishZ() / 1000.0;
        double length = Math.sqrt(x * x + z * z);
        if (length > 1) { x /= length; z /= length; }
        double speed = (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0 ? MAX_SPEED : 8.0;
        double radians = Math.toRadians(intent.yaw());
        double dx = (x * Math.cos(radians) - z * Math.sin(radians)) * speed / 20.0;
        double dz = (z * Math.cos(radians) + x * Math.sin(radians)) * speed / 20.0;
        double dy = intent.verticalWish() * MAX_STEP;
        session.ghost.setDeltaMovement(Vec3.ZERO);
        session.ghost.setYRot(intent.yaw());
        session.ghost.setXRot(intent.pitch());
        Vec3 beforeMove = session.ghost.position();
        session.ghost.move(MoverType.SELF, new Vec3(clamp(dx), clamp(dy), clamp(dz)));
        Vec3 accepted = session.ghost.position().subtract(beforeMove);
        if (!session.acceptedMovementLogged && boundedDisplacement(accepted) && accepted.lengthSqr() > 0) {
            MoonStation14.LOGGER.info("[mind ghost] First server-owned noPhysics move accepted (ordinary walls are passed; not collision parity): ghostUuid={} ghostEntityId={} displacement=({}, {}, {}) sequence={} epoch={}",
                    session.ghost.getUUID(), session.ghost.getId(), accepted.x, accepted.y, accepted.z,
                    intent.sequence(), session.epoch);
            session.acceptedMovementLogged = true;
        }
        session.ghost.setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session != null && session.player == player) end(state, session, false);
    }

    @SubscribeEvent
    public static void playerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) cleanupLifecycle(player);
    }

    private static void cleanupLifecycle(ServerPlayer player) {
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session != null && session.player == player) end(state, session, false);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        RuntimeState state = SERVERS.get(event.getServer());
        if (state == null) return;
        for (Session session : state.sessions.values().toArray(Session[]::new)) {
            if (!connected(session.player)) end(state, session, false);
        }
    }

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        MindGhostStartupGate.onServerStarting(Config.EXPERIMENTAL_MIND_GHOST_CONTROL.get());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        MindGhostStartupGate.onServerStopped();
        RuntimeState state = SERVERS.remove(event.getServer());
        if (state == null) return;
        for (Session session : state.sessions.values().toArray(Session[]::new)) end(state, session, false);
        GhostControlNetworking.installServerHandler(null);
    }

    private static boolean eligible(RuntimeState state, Session session, ServerPlayer player) {
        return player == session.player && connected(player) && player.isAlive()
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR && player.level() == session.ghost.level()
                && live(session.ghost, player.level()) && state.registry.authorizes(player.getUUID(), session.id,
                session.epoch, target -> target.id().equals(session.id) && live(session.ghost, player.level()));
    }

    private static boolean live(GhostMobHarnessEntity ghost, net.minecraft.world.level.Level level) {
        return ghost != null && !ghost.isRemoved() && ghost.isAlive() && ghost.level() == level && ghost.isAddedToLevel();
    }

    private static boolean connected(ServerPlayer player) {
        return player != null && !player.isRemoved() && !player.level().isClientSide && player.level().getServer() != null
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void end(RuntimeState state, Session session, boolean connected) {
        if (state.sessions.remove(session.player.getUUID()) == null) return;
        if (connected) GhostControlNetworking.sendToPlayer(session.player, new GhostControlPayloads.Stop(session.epoch));
        if (connected(session.player)) {
            if (session.player.getCamera() == session.ghost) session.player.setCamera(session.player);
            if (session.modeChanged && session.player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR)
                session.player.setGameMode(session.previousGameType);
        }
        state.registry.logout(session.player.getUUID());
        state.registry.unregisterHarness(session.id);
        if (!session.ghost.isRemoved()) session.ghost.discard();
    }

    private static void handshakeExpired(RuntimeState state, Session session) {
        MoonStation14.LOGGER.warn("[mind ghost] Ready handshake expired for player={} epoch={}; ending pending session",
                session.player.getGameProfile().getName(), session.epoch);
        if (connected(session.player)) {
            session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost did not become ready; player control recovered. Use /ms14 mindghost start to retry."));
        }
        end(state, session, true);
    }

    private static boolean boundedDisplacement(Vec3 displacement) {
        return Double.isFinite(displacement.x) && Double.isFinite(displacement.y) && Double.isFinite(displacement.z)
                && Math.abs(displacement.x) <= MAX_STEP && Math.abs(displacement.y) <= MAX_STEP
                && Math.abs(displacement.z) <= MAX_STEP;
    }

    private static double clamp(double value) { return Math.max(-MAX_STEP, Math.min(MAX_STEP, value)); }

    private static final class RuntimeState {
        final BodyControlRegistry registry = new BodyControlRegistry();
        final Map<UUID, Session> sessions = new java.util.HashMap<>();
    }

    private static final class Session {
        final ServerPlayer player;
        final GhostMobHarnessEntity ghost;
        final MobHarnessId id;
        final GameType previousGameType;
        final long epoch;
        final long startedTick;
        final GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        boolean ready;
        boolean committed;
        final boolean modeChanged;
        long intentTick = Long.MIN_VALUE;
        int intentCount;
        boolean floodLogged;
        boolean acceptedMovementLogged;
        Session(ServerPlayer player, GhostMobHarnessEntity ghost, MobHarnessId id, GameType previousGameType,
                long epoch, long startedTick, boolean modeChanged) {
            this.player = player; this.ghost = ghost; this.id = id; this.previousGameType = previousGameType;
            this.epoch = epoch; this.startedTick = startedTick; this.modeChanged = modeChanged;
        }
    }
}
