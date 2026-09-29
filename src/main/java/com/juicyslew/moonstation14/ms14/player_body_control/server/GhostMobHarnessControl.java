package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.Config;
import com.juicyslew.moonstation14.ms14.movement.server.MovementServerController;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.TargetEligibility;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.movement.GhostMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessLease;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleStartupRuntime;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.hunger.HungerSystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstSystem;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Operator-invoked, deliberately narrow ghost-body control experiment. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class GhostMobHarnessControl {
    private static final long READY_TIMEOUT_TICKS = 100;
    private static final int MAX_INTENTS_PER_TICK = 8;
    private static final double WORLD_BOUND = 30_000_000.0;
    private static final GhostMovementMotor GHOST_MOVEMENT_MOTOR = new GhostMovementMotor();
    private static final GroundedHarnessWorldStep CHARACTER_WORLD_STEP = new GroundedHarnessWorldStep();
    private static final Map<MinecraftServer, RuntimeState> SERVERS = new IdentityHashMap<>();

    private GhostMobHarnessControl() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14").requires(source -> source.hasPermission(2))
                .then(Commands.literal("mindghost")
                        .then(Commands.literal("start").executes(context -> start(context.getSource().getPlayerOrException())))
                 .then(Commands.literal("stop").executes(context -> stop(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("return").executes(context -> returnToGhost(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("possess").then(Commands.argument("body", EntityArgument.entity())
                                .executes(context -> possess(context.getSource().getPlayerOrException(),
                                        EntityArgument.getEntity(context, "body")))))
                        .then(Commands.literal("configure").then(Commands.argument("body", EntityArgument.entity())
                                .executes(context -> configure(context.getSource().getPlayerOrException(),
                                        EntityArgument.getEntity(context, "body")))))));
    }

    private static int possess(ServerPlayer player, Entity entity) {
        if (!MindGhostStartupGate.enabledForServer()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost control is disabled; possession was not started."));
            return 0;
        }
        RuntimeState state = player.level().getServer() == null ? null : SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session == null || session.player != player || !connected(player) || player instanceof FakePlayer
                || !session.committed || session.kind != MobHarnessKind.GHOST || session.offerBody != null
                || !eligible(state, session, player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Possession requires this connected player’s committed experimental ghost session."));
            return 0;
        }
        if (!(entity instanceof Mob body) || body instanceof GhostMobHarnessEntity || body.level() != player.level()
                || body.isRemoved() || !body.isAlive() || body.isPassenger()
                || !body.getPersistentData().getBoolean(GroundedHarnessLease.CONFIGURED_MARKER)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Possession requires a live, configured Mob in the same loaded dimension."));
            return 0;
        }
        MobHarnessId targetId = new MobHarnessId(body.getUUID());
        var acquired = GroundedHarnessLease.tryAcquire(body);
        if (acquired.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Body is not eligible for grounded harness possession or is already movement-owned."));
            return 0;
        }
        GroundedHarnessLease lease = acquired.get();
        boolean registered = false;
        try {
            if (!state.registry.registerHarness(new MobHarness(targetId, MobHarnessKind.CHARACTER))) {
                lease.close();
                return 0;
            }
            registered = true;
            session.offerBody = body;
            session.offerId = targetId;
            session.offerLease = lease;
            session.offerStartedTick = player.level().getGameTime();
            GhostControlNetworking.sendToPlayer(player,
                    new GhostControlPayloads.Offer(session.epoch, body.getId(), MobHarnessKind.CHARACTER));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Grounded body offered; waiting for client handoff readiness."));
            return 1;
        } catch (RuntimeException | Error failure) {
            if (registered) state.registry.unregisterHarness(targetId);
            try { lease.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            MoonStation14.LOGGER.error("[mind ghost] Failed to prepare character possession offer", failure);
            return 0;
        }
    }

    private static int returnToGhost(ServerPlayer player) {
        if (!MindGhostStartupGate.enabledForServer()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost control is disabled; return was not started."));
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        RuntimeState state = server == null ? null : SERVERS.get(server);
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session == null || session.player != player || !connected(player) || player instanceof FakePlayer
                || !session.committed || session.kind != MobHarnessKind.CHARACTER || session.returnGhost != null
                || session.offerBody != null || !eligible(state, session, player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Return requires this authenticated player’s committed character harness session."));
            return 0;
        }
        GhostMobHarnessEntity ghost = null;
        MobHarnessId id = null;
        boolean registered = false;
        try {
            Mob body = session.body;
            if (!eligibleCharacter(session, body, player)) return 0;
            ghost = GhostMobHarnessRegistration.getEntityType().create(player.level());
            if (ghost == null) return 0;
            ghost.moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
            if (!player.level().addFreshEntity(ghost)) {
                ghost.discard();
                return 0;
            }
            id = new MobHarnessId(ghost.getUUID());
            if (!state.registry.registerHarness(new MobHarness(id, MobHarnessKind.GHOST))) {
                ghost.discard();
                return 0;
            }
            registered = true;
            session.returnGhost = ghost;
            session.returnId = id;
            session.returnStartedTick = player.level().getGameTime();
            GhostControlNetworking.sendToPlayer(player,
                    new GhostControlPayloads.Offer(session.epoch, ghost.getId(), MobHarnessKind.GHOST));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Return ghost offered; character control remains active until ghost readiness is committed."));
            return 1;
        } catch (RuntimeException | Error failure) {
            if (registered) state.registry.unregisterHarness(id);
            if (ghost != null && !ghost.isRemoved()) ghost.discard();
            if (session.returnGhost == ghost) {
                session.returnGhost = null;
                session.returnId = null;
                session.returnStartedTick = 0;
            }
            MoonStation14.LOGGER.error("[mind ghost] Failed to prepare return ghost", failure);
            return 0;
        }
    }

    /** Explicitly opts an enrolled, grounded mob into the temporary harness-lease system. */
    private static int configure(ServerPlayer operator, Entity entity) {
        if (!MindGhostStartupGate.enabledForServer()) {
            operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost control is disabled; configuration was not changed."));
            return 0;
        }
        if (!(entity instanceof Mob body) || body.level() != operator.level() || body.isRemoved() || !body.isAlive()) {
            operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost configure requires a live Mob in your current dimension."));
            return 0;
        }
        var data = body.getPersistentData();
        boolean previouslyConfigured = data.getBoolean(GroundedHarnessLease.CONFIGURED_MARKER);
        data.putBoolean(GroundedHarnessLease.CONFIGURED_MARKER, true);
        GroundedHarnessLease lease;
        try {
            var acquired = GroundedHarnessLease.tryAcquire(body);
            if (acquired.isEmpty()) {
                if (!previouslyConfigured) data.remove(GroundedHarnessLease.CONFIGURED_MARKER);
                operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Body is not an eligible bound identity with grounded movement and a supported host type."));
                return 0;
            }
            lease = acquired.get();
        } catch (RuntimeException | Error failure) {
            if (!previouslyConfigured) data.remove(GroundedHarnessLease.CONFIGURED_MARKER);
            MoonStation14.LOGGER.error("[mind ghost] Failed to acquire validation lease for configured body {}",
                    body.getUUID(), failure);
            operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Configuration validation failed; the body was not newly configured. Inspect the server log."));
            return 0;
        }
        try {
            lease.close();
        } catch (RuntimeException | Error failure) {
            MoonStation14.LOGGER.error("[mind ghost] Failed to release validation lease for configured body {}",
                    body.getUUID(), failure);
            MinecraftServer server = operator.level().getServer();
            if (server != null) {
                RuntimeState state = serverState(server);
                quarantineLease(state, lease, operator, "configured body validation");
            }
            operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Configuration validation succeeded, but lease restoration failed; inspect the server log."));
            return 0;
        }
        operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Body explicitly configured for experimental mind harness possession: " + body.getUUID()));
        return 1;
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
        if (LifecycleStartupRuntime.blocksDebugMindFor(server, player.getUUID())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost unavailable: lifecycle startup has a reserved account claim or requires operator recovery."));
            return 0;
        }
        RuntimeState state = serverState(server);
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
            Session session = new Session(player, ghost, id, mind.get().id(), previous, epoch,
                    player.level().getGameTime(), modeChanged);
            state.sessions.put(player.getUUID(), session);
            inserted = true;
            reconcileCarrierNutrition(player);
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

    /** True only while this exact connected player is committed to the currently owned harness camera. */
    public static boolean shouldKeepGhostCamera(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return false;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        return session != null && session.player == player && session.committed
                && player.getCamera() == currentEntity(session) && !player.isPassenger()
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                && eligible(state, session, player);
    }

    /** Exact committed character-body camera ownership predicate. */
    public static boolean shouldKeepCharacterCamera(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return false;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        return session != null && session.player == player && session.committed
                && session.kind == MobHarnessKind.CHARACTER && player.getCamera() == session.body
                && !player.isPassenger() && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                && eligible(state, session, player);
    }

    private static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session == null || session.player != player || !connected(player)) return;
        if (payload instanceof GhostControlPayloads.Ready ready) {
            if (ready.epoch() != session.epoch || session.committed) return;
            if (session.returnGhost != null && session.kind == MobHarnessKind.GHOST
                    && !live(session.returnGhost, player.level())) {
                rollbackReturn(state, session);
                return;
            }
            if (player.level().getGameTime() - session.startedTick >= READY_TIMEOUT_TICKS) {
                if (session.returnGhost != null) rollbackReturn(state, session);
                else handshakeExpired(state, session);
                return;
            }
            if (!eligible(state, session, player)) {
                if (recoverLostCharacterBody(state, session)) return;
                if (session.returnGhost != null) {
                    rollbackReturn(state, session);
                    return;
                }
                if (session.kind == MobHarnessKind.CHARACTER && !session.committed && live(session.ghost, player.level()))
                    rollbackCharacterHandoff(state, session);
                else end(state, session, true);
                return;
            }
            session.ready = true;
            if (session.ready) {
                Entity target = currentEntity(session);
                target.setYRot(player.getYRot());
                target.setXRot(player.getXRot());
                if (target instanceof LivingEntity living) living.setYHeadRot(player.getYRot());
                player.setCamera(target);
                session.committed = true;
                GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Commit(session.epoch));
                if (session.kind == MobHarnessKind.CHARACTER) finishCharacterCommit(state, session);
                else if (session.returnGhost != null) finishReturnCommit(state, session);
                MoonStation14.LOGGER.info("[mind ghost] Ready handshake committed for player={} epoch={} kind={} entityId={}",
                        player.getGameProfile().getName(), session.epoch, session.kind, target.getId());
            }
        } else if (payload instanceof GhostControlPayloads.OfferReady ready) {
            if (session.returnGhost != null && session.kind == MobHarnessKind.CHARACTER && session.committed
                    && ready.currentEpoch() == session.epoch && ready.targetKind() == MobHarnessKind.GHOST
                    && session.returnGhost.getId() == ready.targetEntityId()) {
                if (player.level().getGameTime() - session.returnStartedTick >= READY_TIMEOUT_TICKS
                        || !eligibleCharacter(session, session.body, player) || !live(session.returnGhost, player.level())) {
                    cancelReturn(state, session, "Return offer expired or became ineligible; character control remains active.");
                    return;
                }
                MobHarnessId stagedId = session.returnId;
                var transferred = state.registry.transfer(player.getUUID(), stagedId, session.epoch,
                        harness -> harness.id().equals(stagedId) && harness.kind() == MobHarnessKind.GHOST
                                && live(session.returnGhost, player.level()) && eligibleCharacter(session, session.body, player));
                if (transferred != BodyControlRegistry.OperationResult.CHANGED) {
                    cancelReturn(state, session, "Registry rejected return handoff; character control remains active.");
                    return;
                }
                session.ghost = session.returnGhost;
                session.id = session.returnId;
                session.epoch = state.registry.mind(player.getUUID()).orElseThrow().epoch();
                session.kind = MobHarnessKind.GHOST;
                resetHandoff(session, player);
                GhostControlNetworking.sendToPlayer(player,
                        new GhostControlPayloads.Begin(session.epoch, session.ghost.getId(), MobHarnessKind.GHOST));
                return;
            }
            if (!session.committed || session.kind != MobHarnessKind.GHOST || !eligible(state, session, player)
                    || session.offerBody == null || ready.currentEpoch() != session.epoch
                    || ready.targetKind() != MobHarnessKind.CHARACTER
                    || ready.targetEntityId() != session.offerBody.getId()) return;
            if (player.level().getGameTime() - session.offerStartedTick >= READY_TIMEOUT_TICKS) {
                cancelOffer(state, session, "Character offer expired before client readiness.");
                return;
            }
            if (!eligibleCharacter(session, session.offerBody, player)) {
                cancelOffer(state, session, "Character body became ineligible before handoff.");
                return;
            }
            var transferred = state.registry.transfer(player.getUUID(), session.offerId, session.epoch,
                    harness -> harness.id().equals(session.offerId) && harness.kind() == MobHarnessKind.CHARACTER
                            && eligibleCharacter(session, session.offerBody, player));
            if (transferred != BodyControlRegistry.OperationResult.CHANGED) {
                cancelOffer(state, session, "Registry rejected character possession handoff: " + transferred);
                return;
            }
            session.epoch = state.registry.mind(player.getUUID()).orElseThrow().epoch();
            session.kind = MobHarnessKind.CHARACTER;
            session.body = session.offerBody;
            session.bodyId = session.offerId;
            session.lease = session.offerLease;
            session.offerBody = null;
            session.offerId = null;
            session.offerLease = null;
            session.gate.reset();
            session.lastAppliedSequence = 0;
            session.lastSnapshotTick = Long.MIN_VALUE;
            session.intentTick = Long.MIN_VALUE;
            session.intentCount = 0;
            session.committed = false;
            session.ready = false;
            session.startedTick = player.level().getGameTime();
            GhostControlNetworking.sendToPlayer(player,
                    new GhostControlPayloads.Begin(session.epoch, session.body.getId(), MobHarnessKind.CHARACTER));
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
                if (!recoverLostCharacterBody(state, session)) end(state, session, true);
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
        Entity controlled = currentEntity(session);
        if (controlled == null || player.level() != controlled.level()) {
            end(state, session, true);
            return;
        }
        if (session.returnGhost != null && session.kind == MobHarnessKind.GHOST
                && !live(session.returnGhost, player.level())) {
            rollbackReturn(state, session);
            return;
        }
        if (session.returnGhost != null && session.kind == MobHarnessKind.CHARACTER
                && !live(session.returnGhost, player.level())) {
            cancelReturn(state, session, "Return ghost became invalid; character control remains active.");
            return;
        }
        if (!eligible(state, session, player)) {
            if (recoverLostCharacterBody(state, session)) return;
            if (session.returnGhost != null) {
                rollbackReturn(state, session);
                return;
            }
            if (session.kind == MobHarnessKind.CHARACTER && !session.committed && live(session.ghost, player.level()))
                rollbackCharacterHandoff(state, session);
            else end(state, session, true);
            return;
        }
        if (!session.committed && player.level().getGameTime() - session.startedTick >= READY_TIMEOUT_TICKS) {
            if (session.returnGhost != null) rollbackReturn(state, session);
            else handshakeExpired(state, session);
            return;
        }
        GhostControlPayloads.Intent intent = session.gate.take();
        if (!session.committed) return;
        if (session.kind == MobHarnessKind.CHARACTER) {
            if (intent != null) {
                var result = CHARACTER_WORLD_STEP.step(session.body, intent.wishX(), intent.wishZ(),
                        (intent.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0,
                        (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, intent.yaw());
                // The world step can partially move before returning empty; acknowledge it and never retry.
                session.lastAppliedSequence = intent.sequence();
                if (result.isEmpty() && !eligibleCharacter(session, session.body, player)) {
                    MoonStation14.LOGGER.warn("[mind ghost] Character world step rejected an invalid body; ending possession player={} body={} sequence={} epoch={}",
                            player.getGameProfile().getName(), session.bodyId, intent.sequence(), session.epoch);
                    if (!recoverLostCharacterBody(state, session)) end(state, session, true);
                    return;
                }
                session.body.setYRot(intent.yaw());
                session.body.setXRot(intent.pitch());
                session.body.setYHeadRot(intent.yaw());
            }
            sendCharacterSnapshot(state, session);
            return;
        }
        if (intent != null) {
            session.ghost.setYRot(intent.yaw());
            session.ghost.setXRot(intent.pitch());
            session.ghost.setDeltaMovement(Vec3.ZERO);
            MovementVector beforeMove = movementPosition(session.ghost);
            var resolved = GHOST_MOVEMENT_MOTOR.tick(beforeMove, intent.wishX(), intent.wishZ(), intent.verticalWish(),
                    (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, intent.yaw(),
                    (position, requested, wasOnGround) -> {
                        Vec3 before = session.ghost.position();
                        session.ghost.move(MoverType.SELF, new Vec3(requested.x(), requested.y(), requested.z()));
                        Vec3 acceptedDisplacement = session.ghost.position().subtract(before);
                        return new MovementCollisionResolver.CollisionResult(
                                new MovementVector(acceptedDisplacement.x, acceptedDisplacement.y, acceptedDisplacement.z),
                                session.ghost.onGround());
                    });
            // The intent is acknowledged only after its one server-side motor/entity simulation completed.
            session.lastAppliedSequence = intent.sequence();
            Vec3 accepted = session.ghost.position().subtract(new Vec3(beforeMove.x(), beforeMove.y(), beforeMove.z()));
            // Entity coordinates are authoritative; the returned pure state is based on the resolver's measured move.
            MovementVector actualPosition = movementPosition(session.ghost);
            if (Math.abs(resolved.position().x() - actualPosition.x()) > 1.0e-6
                    || Math.abs(resolved.position().y() - actualPosition.y()) > 1.0e-6
                    || Math.abs(resolved.position().z() - actualPosition.z()) > 1.0e-6) {
                MoonStation14.LOGGER.warn("[mind ghost] Movement resolver state differs from entity position: ghostUuid={} state={} entity={}",
                        session.ghost.getUUID(), resolved.position(), actualPosition);
            }
            if (!session.acceptedMovementLogged && boundedDisplacement(accepted) && accepted.lengthSqr() > 0) {
                MoonStation14.LOGGER.info("[mind ghost] First server-owned ghost Entity.move accepted (not full collision parity): ghostUuid={} ghostEntityId={} displacement=({}, {}, {}) sequence={} epoch={}",
                        session.ghost.getUUID(), session.ghost.getId(), accepted.x, accepted.y, accepted.z,
                        intent.sequence(), session.epoch);
                session.acceptedMovementLogged = true;
            }
            session.ghost.setDeltaMovement(Vec3.ZERO);
        }
        sendSnapshot(state, session);
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
            else if (session.offerBody != null
                    && session.player.level().getGameTime() - session.offerStartedTick >= READY_TIMEOUT_TICKS)
                cancelOffer(state, session, "Character offer expired; ghost control remains active.");
            else if (session.returnGhost != null
                    && (session.player.level().getGameTime() - session.returnStartedTick >= READY_TIMEOUT_TICKS
                    || !live(session.returnGhost, session.player.level()))) {
                if (session.kind == MobHarnessKind.GHOST) rollbackReturn(state, session);
                else cancelReturn(state, session, "Return offer expired; character control remains active.");
            }
        }
        retryQuarantinedLeases(state);
    }

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent event) {
        MindGhostStartupGate.onServerStarting(Config.EXPERIMENTAL_MIND_GHOST_CONTROL.get());
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        MindGhostStartupGate.onServerStopped();
        GhostControlNetworking.clearDebugServerHandler();
        RuntimeState state = SERVERS.get(event.getServer());
        if (state == null) return;
        for (Session session : state.sessions.values().toArray(Session[]::new)) end(state, session, false);
        retryQuarantinedLeases(state);
        for (LeaseCleanup cleanup : state.quarantinedLeases) {
            MoonStation14.LOGGER.error("[mind ghost] Lease restoration remains failed at server shutdown; no retry is possible after runtime state removal context={} player={}",
                    cleanup.context, cleanup.player.getGameProfile().getName());
        }
        SERVERS.remove(event.getServer());
    }

    private static boolean eligible(RuntimeState state, Session session, ServerPlayer player) {
        if (player != session.player || !connected(player) || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || player.level() != (session.kind == MobHarnessKind.GHOST ? session.ghost.level() : session.body.level())
                || session.kind == MobHarnessKind.GHOST && !live(session.ghost, player.level())) return false;
        MobHarnessId id = session.kind == MobHarnessKind.GHOST ? session.id : session.bodyId;
        return id != null && state.registry.authorizes(player.getUUID(), id, session.epoch,
                target -> target.id().equals(id) && (session.kind == MobHarnessKind.GHOST
                        ? target.kind() == MobHarnessKind.GHOST && live(session.ghost, player.level())
                        : target.kind() == MobHarnessKind.CHARACTER && eligibleCharacter(session, session.body, player)));
    }

    private static boolean eligibleCharacter(Session session, Mob body, ServerPlayer player) {
        MobHarnessId expected = session.bodyId != null ? session.bodyId : session.offerId;
        return body != null && body.level() == player.level() && !body.isRemoved() && body.isAlive()
                && body.isAddedToLevel() && !body.isPassenger()
                && body.getPersistentData().getBoolean(GroundedHarnessLease.CONFIGURED_MARKER)
                && expected != null && body.getUUID().equals(expected.value())
                && body.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getEntity(body.getUUID()) == body
                && body instanceof MindControlledMob owner && owner.moonstation14$isMovementOwned()
                && GroundedHarnessLease.isOwnedBodyEligible(body);
    }

    /** Exact, read-only ownership check for this controller's connected debug session. */
    public static boolean ownsDebugSession(ServerPlayer player) {
        if (player == null || !connected(player) || player.level().getServer() == null) return false;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        return session != null && session.player == player;
    }

    /** Creates the runtime state and ensures the debug packet handler owns only its exact sessions. */
    private static RuntimeState serverState(MinecraftServer server) {
        RuntimeState state = SERVERS.computeIfAbsent(server, ignored -> new RuntimeState());
        GhostControlNetworking.installDebugServerHandler(GhostMobHarnessControl::onPayload,
                GhostMobHarnessControl::ownsDebugSession);
        return state;
    }

    private static Entity currentEntity(Session session) {
        return session.kind == MobHarnessKind.GHOST ? session.ghost : session.body;
    }

    private static void finishCharacterCommit(RuntimeState state, Session session) {
        if (session.id != null) state.registry.unregisterHarness(session.id);
        if (session.ghost != null && !session.ghost.isRemoved()) session.ghost.discard();
        session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Configured grounded character possession active; /ms14 mindghost stop to recover."));
    }

    /** Recovers only a committed character whose own target was lost, never player/session invalidation. */
    private static boolean recoverLostCharacterBody(RuntimeState state, Session session) {
        ServerPlayer player = session.player;
        Mob body = session.body;
        if (!session.committed || session.kind != MobHarnessKind.CHARACTER || body == null
                || !connected(player) || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || player.level() != body.level() || eligibleCharacter(session, body, player)) return false;

        var mind = state.registry.mind(player.getUUID());
        if (mind.isEmpty() || (mind.get().harnessId() != null
                && !mind.get().harnessId().equals(session.bodyId))) {
            MoonStation14.LOGGER.error("[mind ghost] Lost character body recovery found absent mind or unexpected owned harness; ending fail closed player={} body={} binding={}",
                    player.getGameProfile().getName(), session.bodyId,
                    mind.map(BodyControlRegistry.MindSnapshot::harnessId).orElse(null));
            end(state, session, true);
            return true;
        }

        GhostMobHarnessEntity replacement = null;
        MobHarnessId replacementId = null;
        boolean registered = false;
        try {
            Vec3 origin = recoveryPosition(body, player);
            replacement = GhostMobHarnessRegistration.getEntityType().create(player.level());
            if (replacement == null) throw new IllegalStateException("Ghost entity type could not create recovery harness");
            replacement.moveTo(origin.x, origin.y, origin.z, body.getYRot(), body.getXRot());
            if (!player.level().addFreshEntity(replacement))
                throw new IllegalStateException("Recovery ghost could not be added to the current level");
            GhostMobHarnessEntity stagedGhost = replacement;
            replacementId = new MobHarnessId(stagedGhost.getUUID());
            if (!state.registry.registerHarness(new MobHarness(replacementId, MobHarnessKind.GHOST)))
                throw new IllegalStateException("Recovery ghost harness ID was already registered");
            registered = true;
            MobHarnessId stagedId = replacementId;
            var eligibility = (TargetEligibility) harness -> harness.id().equals(stagedId)
                    && harness.kind() == MobHarnessKind.GHOST && live(stagedGhost, player.level());
            var recovered = mind.get().harnessId() == null
                    ? state.registry.attach(player.getUUID(), stagedId, mind.get().epoch(), eligibility)
                    : state.registry.transfer(player.getUUID(), stagedId, mind.get().epoch(), eligibility);
            if (recovered != BodyControlRegistry.OperationResult.CHANGED)
                throw new IllegalStateException("Registry rejected recovery ghost binding: " + recovered);
            var bound = state.registry.mind(player.getUUID());
            if (bound.isEmpty() || !bound.get().id().equals(mind.get().id())
                    || !stagedId.equals(bound.get().harnessId()))
                throw new IllegalStateException("Recovery binding did not retain the existing mind and ghost owner");

            // Retire the old harness only after the new binding is established. The body remains in-world.
            session.ghost = replacement;
            session.id = replacementId;
            session.epoch = bound.get().epoch();
            session.kind = MobHarnessKind.GHOST;
            state.registry.unregisterHarness(session.bodyId);
            cancelPendingCharacterResources(state, session);
            closeLease(state, session, session.lease, "lost character body recovery");
            session.lease = null;
            session.body = null;
            session.bodyId = null;
            resetHandoff(session, player);
            if (player.getCamera() != null && player.getCamera() != player && player.getCamera().isRemoved())
                player.setCamera(player);
            else if (player.getCamera() == body) player.setCamera(player);
            GhostControlNetworking.sendToPlayer(player,
                    new GhostControlPayloads.Begin(session.epoch, replacement.getId(), MobHarnessKind.GHOST));
            MoonStation14.LOGGER.warn("[mind ghost] Recovered lost committed character body onto replacement ghost player={} epoch={}",
                    player.getGameProfile().getName(), session.epoch);
            return true;
        } catch (RuntimeException | Error failure) {
            MoonStation14.LOGGER.error("[mind ghost] Failed to recover lost committed character body; ending fail closed player={} body={}",
                    player.getGameProfile().getName(), session.bodyId, failure);
            if (registered) state.registry.unregisterHarness(replacementId);
            if (replacement != null && !replacement.isRemoved()) replacement.discard();
            end(state, session, true);
            return true;
        }
    }

    private static Vec3 recoveryPosition(Mob body, ServerPlayer player) {
        Vec3 bodyPosition = body.position();
        if (body.level() == player.level() && body.isAddedToLevel()
                && boundedCoordinate(bodyPosition.x) && boundedCoordinate(bodyPosition.y)
                && boundedCoordinate(bodyPosition.z)) return bodyPosition;
        Vec3 playerPosition = player.position();
        if (!boundedCoordinate(playerPosition.x) || !boundedCoordinate(playerPosition.y)
                || !boundedCoordinate(playerPosition.z))
            throw new IllegalStateException("Neither body nor carrier has a finite safe recovery position");
        return playerPosition;
    }

    private static void cancelPendingCharacterResources(RuntimeState state, Session session) {
        if (session.offerId != null) state.registry.unregisterHarness(session.offerId);
        closeLease(state, session, session.offerLease, "lost body pending possession offer");
        session.offerBody = null;
        session.offerId = null;
        session.offerLease = null;
        session.offerStartedTick = 0;
        if (session.returnId != null) state.registry.unregisterHarness(session.returnId);
        if (session.returnGhost != null && session.returnGhost != session.ghost && !session.returnGhost.isRemoved())
            session.returnGhost.discard();
        session.returnGhost = null;
        session.returnId = null;
        session.returnStartedTick = 0;
    }

    private static void finishReturnCommit(RuntimeState state, Session session) {
        state.registry.unregisterHarness(session.bodyId);
        closeLease(state, session, session.lease, "committed return to ghost");
        session.lease = null;
        session.body = null;
        session.bodyId = null;
        session.returnGhost = null;
        session.returnId = null;
        session.returnStartedTick = 0;
    }

    private static void cancelReturn(RuntimeState state, Session session, String reason) {
        if (session.returnId != null) state.registry.unregisterHarness(session.returnId);
        if (session.returnGhost != null && !session.returnGhost.isRemoved()) session.returnGhost.discard();
        session.returnGhost = null;
        session.returnId = null;
        session.returnStartedTick = 0;
        if (connected(session.player)) session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(reason));
    }

    private static void rollbackReturn(RuntimeState state, Session session) {
        Mob body = session.body;
        if (body == null || !eligibleCharacter(session, body, session.player)) {
            MoonStation14.LOGGER.error("[mind ghost] Return ghost failed and character body is no longer eligible; ending fail closed player={} body={}",
                    session.player.getGameProfile().getName(), session.bodyId);
            end(state, session, true);
            return;
        }
        var snapshot = state.registry.mind(session.player.getUUID());
        if (snapshot.isEmpty()) {
            MoonStation14.LOGGER.error("[mind ghost] Return rollback has no registry mind; ending fail closed player={}",
                    session.player.getGameProfile().getName());
            end(state, session, true);
            return;
        }
        if (snapshot.get().harnessId() != null && !snapshot.get().harnessId().equals(session.id)) {
            MoonStation14.LOGGER.error("[mind ghost] Return rollback found a different owned harness; refusing to transfer it player={} expected={} actual={}",
                    session.player.getGameProfile().getName(), session.id, snapshot.get().harnessId());
            end(state, session, true);
            return;
        }
        if (snapshot.get().harnessId() == null) {
            GhostMobHarnessEntity replacement = GhostMobHarnessRegistration.getEntityType().create(session.player.level());
            if (replacement == null) {
                MoonStation14.LOGGER.error("[mind ghost] Detached return rollback cannot create replacement ghost; retained character recovery unavailable, ending fail closed player={}",
                        session.player.getGameProfile().getName());
                end(state, session, true);
                return;
            }
            replacement.moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
            MobHarnessId replacementId = new MobHarnessId(replacement.getUUID());
            if (!session.player.level().addFreshEntity(replacement)
                    || !state.registry.registerHarness(new MobHarness(replacementId, MobHarnessKind.GHOST))) {
                if (!replacement.isRemoved()) replacement.discard();
                MoonStation14.LOGGER.error("[mind ghost] Detached return rollback cannot register replacement ghost; retained character recovery unavailable, ending fail closed player={}",
                        session.player.getGameProfile().getName());
                end(state, session, true);
                return;
            }
            var attached = state.registry.attach(session.player.getUUID(), replacementId, snapshot.get().epoch(),
                    harness -> harness.id().equals(replacementId) && harness.kind() == MobHarnessKind.GHOST
                            && live(replacement, session.player.level()));
            if (attached != BodyControlRegistry.OperationResult.CHANGED) {
                state.registry.unregisterHarness(replacementId);
                replacement.discard();
                MoonStation14.LOGGER.error("[mind ghost] Detached return rollback cannot attach replacement ghost; retained character recovery unavailable, ending fail closed player={} result={}",
                        session.player.getGameProfile().getName(), attached);
                end(state, session, true);
                return;
            }
            // Keep the newly owned ghost in the session so end() can always clean it up if transfer fails.
            session.ghost = replacement;
            session.id = replacementId;
            snapshot = state.registry.mind(session.player.getUUID());
        }
        var result = state.registry.transfer(session.player.getUUID(), session.bodyId, snapshot.get().epoch(),
                harness -> harness.id().equals(session.bodyId) && harness.kind() == MobHarnessKind.CHARACTER
                        && eligibleCharacter(session, body, session.player));
        if (result != BodyControlRegistry.OperationResult.CHANGED
                && result != BodyControlRegistry.OperationResult.UNCHANGED) {
            MoonStation14.LOGGER.error("[mind ghost] Return rollback to character failed; ending fail closed player={} result={}",
                    session.player.getGameProfile().getName(), result);
            end(state, session, true);
            return;
        }
        if (session.id != null) state.registry.unregisterHarness(session.id);
        if (session.returnId != null) state.registry.unregisterHarness(session.returnId);
        if (session.returnGhost != null && !session.returnGhost.isRemoved()) session.returnGhost.discard();
        if (session.ghost != null && !session.ghost.isRemoved()) session.ghost.discard();
        session.ghost = null;
        session.id = null;
        session.returnGhost = null;
        session.returnId = null;
        session.returnStartedTick = 0;
        session.kind = MobHarnessKind.CHARACTER;
        session.epoch = state.registry.mind(session.player.getUUID()).orElseThrow().epoch();
        resetHandoff(session, session.player);
        GhostControlNetworking.sendToPlayer(session.player,
                new GhostControlPayloads.Begin(session.epoch, body.getId(), MobHarnessKind.CHARACTER));
        MoonStation14.LOGGER.warn("[mind ghost] Return handshake failed; transferred mind back to leased character player={} epoch={}",
                session.player.getGameProfile().getName(), session.epoch);
    }

    private static void resetHandoff(Session session, ServerPlayer player) {
        session.gate.reset();
        session.lastAppliedSequence = 0;
        session.lastSnapshotTick = Long.MIN_VALUE;
        session.intentTick = Long.MIN_VALUE;
        session.intentCount = 0;
        session.committed = false;
        session.ready = false;
        session.startedTick = player.level().getGameTime();
    }

    private static boolean live(GhostMobHarnessEntity ghost, net.minecraft.world.level.Level level) {
        return ghost != null && !ghost.isRemoved() && ghost.isAlive() && ghost.level() == level && ghost.isAddedToLevel();
    }

    private static boolean connected(ServerPlayer player) {
        return player != null && !player.isRemoved() && !player.level().isClientSide && player.level().getServer() != null
                && player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    /** True only for this exact connected, non-fake player object's server-owned mind-ghost session. */
    public static boolean isExperimentalCarrier(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !connected(player)) return false;
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        return session != null && session.player == player;
    }

    /**
     * Returns the currently committed and registry-bound harness for the authenticated session.
     * This is deliberately a read-only snapshot lookup; unlike authorizes(), it never revokes ownership.
     */
    public static Optional<ActiveHarness> activeHarness(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !connected(player)) return Optional.empty();
        RuntimeState state = SERVERS.get(player.level().getServer());
        Session session = state == null ? null : state.sessions.get(player.getUUID());
        if (session == null || session.player != player || !session.committed || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR || player.isPassenger())
            return Optional.empty();

        MobHarnessId harnessId = session.kind == MobHarnessKind.GHOST ? session.id : session.bodyId;
        Entity entity = currentEntity(session);
        if (harnessId == null || !(entity instanceof LivingEntity living)) return Optional.empty();
        boolean eligible = session.kind == MobHarnessKind.GHOST
                ? entity == session.ghost && live(session.ghost, player.level())
                    && harnessId.value().equals(entity.getUUID())
                : session.kind == MobHarnessKind.CHARACTER && entity == session.body
                    && eligibleCharacter(session, session.body, player);
        if (!eligible) return Optional.empty();

        var mind = state.registry.mind(player.getUUID());
        if (mind.isEmpty() || !session.mindId.equals(mind.get().id())
                || !harnessId.equals(mind.get().harnessId()) || session.epoch != mind.get().epoch())
            return Optional.empty();
        return Optional.of(new ActiveHarness(session.mindId, session.kind, living, harnessId, session.epoch));
    }

    /** Read-only exact committed debug binding, independent of camera state. */
    public static Optional<Entity> committedControlledEntity(ServerPlayer player) {
        return Optional.ofNullable(committedControlSnapshot(player).owned());
    }

    public static CommittedSpectatorGuard.Ownership committedControlSnapshot(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level))
            return CommittedSpectatorGuard.Ownership.absent();
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread() || player instanceof FakePlayer)
            return CommittedSpectatorGuard.Ownership.absent();
        RuntimeState state = SERVERS.get(server);
        Session pinned = state == null ? null : state.sessions.get(player.getUUID());
        if (pinned == null || pinned.player != player || !pinned.committed)
            return CommittedSpectatorGuard.Ownership.absent();
        if (!connected(player) || !player.isAlive()
                || player.isPassenger() || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR)
            return CommittedSpectatorGuard.Ownership.unavailable();
        Entity entity = currentEntity(pinned);
        MobHarnessId id = pinned.kind == MobHarnessKind.GHOST ? pinned.id : pinned.bodyId;
        if (entity == null || id == null || !id.value().equals(entity.getUUID())
                || entity.level() != level || !(entity instanceof LivingEntity)
                || !(pinned.kind == MobHarnessKind.GHOST ? entity == pinned.ghost && live(pinned.ghost, level)
                        : pinned.kind == MobHarnessKind.CHARACTER && entity == pinned.body
                         && eligibleCharacter(pinned, pinned.body, player))) return CommittedSpectatorGuard.Ownership.unavailable();
        var mind = state.registry.mind(player.getUUID());
        if (mind.isEmpty() || !pinned.mindId.equals(mind.get().id())
                || !id.equals(mind.get().harnessId()) || pinned.epoch != mind.get().epoch()
                || !state.registry.authorizesReadOnly(player.getUUID(), id, pinned.epoch,
                 target -> target.id().equals(id) && target.kind() == pinned.kind))
            return CommittedSpectatorGuard.Ownership.unavailable();
        return state.sessions.get(player.getUUID()) == pinned
                ? CommittedSpectatorGuard.Ownership.valid(entity)
                : CommittedSpectatorGuard.Ownership.unavailable();
    }

    /** Returns the active character Mob only; ghost sessions expose no character body. */
    public static Optional<Mob> activeCharacterBody(ServerPlayer player) {
        return activeHarness(player)
                .filter(harness -> harness.kind() == MobHarnessKind.CHARACTER)
                .map(harness -> (Mob) harness.entity());
    }

    /** Immutable, server-authenticated view of one committed harness binding. */
    public record ActiveHarness(MindId mindId, MobHarnessKind kind, LivingEntity entity,
                                MobHarnessId harnessId, long epoch) { }

    private static void end(RuntimeState state, Session session, boolean connected) {
        if (state.sessions.remove(session.player.getUUID()) == null) return;
        if (connected) GhostControlNetworking.sendToPlayer(session.player, new GhostControlPayloads.Stop(session.epoch));
        if (connected(session.player)) {
            // Ending this exact session must clear even a third-party camera selected while possessing.
            if (session.player.getCamera() != session.player)
                session.player.setCamera(session.player);
            if (session.modeChanged && session.player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR)
                session.player.setGameMode(session.previousGameType);
            reconcileCarrierNutrition(session.player);
        }
        state.registry.logout(session.player.getUUID());
        if (session.id != null) state.registry.unregisterHarness(session.id);
        if (session.bodyId != null) state.registry.unregisterHarness(session.bodyId);
        if (session.offerId != null) state.registry.unregisterHarness(session.offerId);
        if (session.returnId != null) state.registry.unregisterHarness(session.returnId);
        closeLease(state, session, session.offerLease, "pending possession offer");
        if (session.lease != session.offerLease) closeLease(state, session, session.lease, "character possession");
        session.offerLease = null;
        session.lease = null;
        if (session.ghost != null && !session.ghost.isRemoved()) session.ghost.discard();
        if (session.returnGhost != null && session.returnGhost != session.ghost && !session.returnGhost.isRemoved())
            session.returnGhost.discard();
    }

    /** Rebuilds only existing carrier nutrition projections and their derived activity flags. */
    private static void reconcileCarrierNutrition(ServerPlayer player) {
        if (!connected(player) || !(player.level() instanceof ServerLevel level)) return;
        if (player.hasData(ModDataAttachments.HUNGER.get())) {
            try {
                HungerSystem.reconcile(player, level);
            } catch (RuntimeException | Error failure) {
                MoonStation14.LOGGER.warn("[mind ghost] Unable to reconcile carrier hunger for {}",
                        player.getGameProfile().getName(), failure);
            }
        }
        if (player.hasData(ModDataAttachments.THIRST.get())) {
            try {
                ThirstSystem.reconcile(player, level);
            } catch (RuntimeException | Error failure) {
                MoonStation14.LOGGER.warn("[mind ghost] Unable to reconcile carrier thirst for {}",
                        player.getGameProfile().getName(), failure);
            }
        }
        try {
            EntityActivitySystem.reconcile(player);
        } catch (RuntimeException | Error failure) {
            MoonStation14.LOGGER.warn("[mind ghost] Unable to reconcile carrier activity for {}",
                    player.getGameProfile().getName(), failure);
        }
    }

    private static void handshakeExpired(RuntimeState state, Session session) {
        if (session.kind == MobHarnessKind.CHARACTER) {
            rollbackCharacterHandoff(state, session);
            return;
        }
        MoonStation14.LOGGER.warn("[mind ghost] Ready handshake expired for player={} epoch={}; ending pending session",
                session.player.getGameProfile().getName(), session.epoch);
        if (connected(session.player)) {
            session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind ghost did not become ready; player control recovered. Use /ms14 mindghost start to retry."));
        }
        end(state, session, true);
    }

    private static void cancelOffer(RuntimeState state, Session session, String reason) {
        MoonStation14.LOGGER.warn("[mind ghost] {} player={}", reason, session.player.getGameProfile().getName());
        if (session.offerId != null) state.registry.unregisterHarness(session.offerId);
        closeLease(state, session, session.offerLease, "cancelled possession offer");
        session.offerBody = null;
        session.offerId = null;
        session.offerLease = null;
        if (connected(session.player)) session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(reason));
    }

    private static void rollbackCharacterHandoff(RuntimeState state, Session session) {
        var snapshot = state.registry.mind(session.player.getUUID());
        var result = snapshot.isEmpty() ? BodyControlRegistry.OperationResult.UNKNOWN_SESSION
                : snapshot.get().harnessId() == null
                ? state.registry.attach(session.player.getUUID(), session.id, snapshot.get().epoch(),
                    harness -> harness.id().equals(session.id) && harness.kind() == MobHarnessKind.GHOST
                            && live(session.ghost, session.player.level()))
                : state.registry.transfer(session.player.getUUID(), session.id, snapshot.get().epoch(),
                    harness -> harness.id().equals(session.id) && harness.kind() == MobHarnessKind.GHOST
                            && live(session.ghost, session.player.level()));
        if (result != BodyControlRegistry.OperationResult.CHANGED
                && result != BodyControlRegistry.OperationResult.UNCHANGED) {
            MoonStation14.LOGGER.error("[mind ghost] Character handshake timed out and rollback to ghost failed: player={} epoch={} result={}",
                    session.player.getGameProfile().getName(), session.epoch, result);
            if (connected(session.player)) session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Character handoff failed and ghost recovery could not be established; inspect the server log."));
            end(state, session, true);
            return;
        }
        state.registry.unregisterHarness(session.bodyId);
        closeLease(state, session, session.lease, "timed-out character possession");
        session.lease = null;
        session.body = null;
        session.bodyId = null;
        session.kind = MobHarnessKind.GHOST;
        session.epoch = state.registry.mind(session.player.getUUID()).orElseThrow().epoch();
        session.gate.reset();
        session.lastAppliedSequence = 0;
        session.lastSnapshotTick = Long.MIN_VALUE;
        session.intentTick = Long.MIN_VALUE;
        session.intentCount = 0;
        session.committed = false;
        session.ready = false;
        session.startedTick = session.player.level().getGameTime();
        GhostControlNetworking.sendToPlayer(session.player,
                new GhostControlPayloads.Begin(session.epoch, session.ghost.getId(), MobHarnessKind.GHOST));
        MoonStation14.LOGGER.warn("[mind ghost] Character readiness timed out; transferred mind back to preserved ghost with new epoch player={} epoch={}",
                session.player.getGameProfile().getName(), session.epoch);
    }

    private static void closeLease(RuntimeState state, Session session, GroundedHarnessLease lease, String context) {
        if (lease == null) return;
        try {
            lease.close();
        } catch (RuntimeException | Error failure) {
            MoonStation14.LOGGER.error("[mind ghost] Failed to restore AI/movement ownership during {} player={} body={}",
                    context, session.player.getGameProfile().getName(), session.body == null ? "pending" : session.body.getUUID(), failure);
            if (connected(session.player)) session.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Mind harness cleanup failed to restore body AI; inspect the server log."));
            quarantineLease(state, lease, session.player, context);
        }
    }

    private static void quarantineLease(RuntimeState state, GroundedHarnessLease lease, ServerPlayer player, String context) {
        if (lease == null) return;
        if (state.quarantinedLeases.stream().noneMatch(entry -> entry.lease == lease))
            state.quarantinedLeases.add(new LeaseCleanup(lease, player, context));
    }

    private static void retryQuarantinedLeases(RuntimeState state) {
        for (LeaseCleanup cleanup : state.quarantinedLeases.toArray(LeaseCleanup[]::new)) {
            try {
                cleanup.lease.close();
                state.quarantinedLeases.remove(cleanup);
                MoonStation14.LOGGER.info("[mind ghost] Retried lease restoration successfully context={} player={}",
                        cleanup.context, cleanup.player.getGameProfile().getName());
            } catch (RuntimeException | Error failure) {
                MoonStation14.LOGGER.error("[mind ghost] Quarantined body lease restoration still failing context={} player={}; AI competition remains suppressed while movement ownership holds",
                        cleanup.context, cleanup.player.getGameProfile().getName(), failure);
            }
        }
    }

    private static MovementVector movementPosition(GhostMobHarnessEntity ghost) {
        Vec3 position = ghost.position();
        return new MovementVector(position.x, position.y, position.z);
    }

    private static void sendSnapshot(RuntimeState state, Session session) {
        long serverGameTick = session.ghost.level().getGameTime();
        if (serverGameTick <= session.lastSnapshotTick) return;
        Vec3 position = session.ghost.position();
        float rawYaw = session.ghost.getYRot();
        float pitch = session.ghost.getXRot();
        if (!boundedCoordinate(position.x) || !boundedCoordinate(position.y) || !boundedCoordinate(position.z)
                || !Float.isFinite(rawYaw) || !Float.isFinite(pitch) || serverGameTick < 0) {
            MoonStation14.LOGGER.warn("[mind ghost] Invalid authoritative ghost state; ending session player={} epoch={}",
                    session.player.getGameProfile().getName(), session.epoch);
            end(state, session, true);
            return;
        }
        float yaw = wrapYaw(rawYaw);
        pitch = Math.max(-90.0f, Math.min(90.0f, pitch));
        GhostControlPayloads.Snapshot snapshot;
        try {
            snapshot = new GhostControlPayloads.Snapshot(session.epoch, session.ghost.getId(), serverGameTick,
                    session.lastAppliedSequence, position.x, position.y, position.z, yaw, pitch);
        } catch (IllegalArgumentException exception) {
            // Defensive fail-closed path: malformed entity state must never terminate the server tick.
            MoonStation14.LOGGER.warn("[mind ghost] Could not construct authoritative snapshot; ending session player={} epoch={}",
                    session.player.getGameProfile().getName(), session.epoch);
            end(state, session, true);
            return;
        }
        session.lastSnapshotTick = serverGameTick;
        GhostControlNetworking.sendToPlayer(session.player, snapshot);
    }

    private static void sendCharacterSnapshot(RuntimeState state, Session session) {
        Mob body = session.body;
        long tick = body.level().getGameTime();
        if (tick <= session.lastSnapshotTick) return;
        Vec3 position = body.position();
        Vec3 velocity = body.getDeltaMovement().scale(20d);
        float yaw = wrapYaw(body.getYRot());
        float pitch = Math.max(-90f, Math.min(90f, body.getXRot()));
        float surface = (float) SlidingFrictionSystem.frictionFactor(body);
        float voluntary = (float) (CharacterControlSystem.isKnockedDown(body)
                ? com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR : 1d);
        try {
            var snapshot = new GhostControlPayloads.Snapshot(session.epoch, body.getId(), MobHarnessKind.CHARACTER,
                    tick, session.lastAppliedSequence, position.x, position.y, position.z, yaw, pitch,
                    velocity.x, velocity.y, velocity.z, body.onGround(), surface, voluntary,
                    CharacterControlSystem.isStunned(body));
            session.lastSnapshotTick = tick;
            GhostControlNetworking.sendToPlayer(session.player, snapshot);
        } catch (IllegalArgumentException failure) {
            MoonStation14.LOGGER.error("[mind ghost] Invalid authoritative character state; ending session player={} epoch={}",
                    session.player.getGameProfile().getName(), session.epoch, failure);
            end(state, session, true);
        }
    }

    private static boolean boundedCoordinate(double value) {
        return Double.isFinite(value) && value >= -WORLD_BOUND && value <= WORLD_BOUND;
    }

    private static float wrapYaw(float yaw) {
        return (float) (yaw - 360.0 * Math.floor((yaw + 180.0) / 360.0));
    }

    private static boolean boundedDisplacement(Vec3 displacement) {
        return Double.isFinite(displacement.x) && Double.isFinite(displacement.y) && Double.isFinite(displacement.z)
                && Math.abs(displacement.x) <= GhostMovementMotor.VERTICAL_SPEED_PER_SECOND / 20.0
                && Math.abs(displacement.y) <= GhostMovementMotor.VERTICAL_SPEED_PER_SECOND / 20.0
                && Math.abs(displacement.z) <= GhostMovementMotor.VERTICAL_SPEED_PER_SECOND / 20.0;
    }

    private static final class RuntimeState {
        final BodyControlRegistry registry = new BodyControlRegistry();
        final Map<UUID, Session> sessions = new java.util.HashMap<>();
        final List<LeaseCleanup> quarantinedLeases = new ArrayList<>();
    }

    private record LeaseCleanup(GroundedHarnessLease lease, ServerPlayer player, String context) { }

    private static final class Session {
        final ServerPlayer player;
        final MindId mindId;
        GhostMobHarnessEntity ghost;
        MobHarnessId id;
        final GameType previousGameType;
        long epoch;
        long startedTick;
        final GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        boolean ready;
        boolean committed;
        final boolean modeChanged;
        long intentTick = Long.MIN_VALUE;
        int intentCount;
        boolean floodLogged;
        boolean acceptedMovementLogged;
        long lastAppliedSequence;
        long lastSnapshotTick = Long.MIN_VALUE;
        MobHarnessKind kind = MobHarnessKind.GHOST;
        Mob body;
        MobHarnessId bodyId;
        GroundedHarnessLease lease;
        Mob offerBody;
        MobHarnessId offerId;
        GroundedHarnessLease offerLease;
        long offerStartedTick;
        GhostMobHarnessEntity returnGhost;
        MobHarnessId returnId;
        long returnStartedTick;
        Session(ServerPlayer player, GhostMobHarnessEntity ghost, MobHarnessId id, MindId mindId, GameType previousGameType,
                long epoch, long startedTick, boolean modeChanged) {
            this.player = player; this.ghost = ghost; this.id = id; this.previousGameType = previousGameType;
            this.mindId = mindId;
            this.epoch = epoch; this.startedTick = startedTick; this.modeChanged = modeChanged;
        }
    }
}
