package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData;
import com.juicyslew.moonstation14.ms14.chat.network.LocalCharacterIdentityPayload;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechNetworking;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MindId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.character.GroundedHarnessWorldStep;
import com.juicyslew.moonstation14.ms14.player_body_control.character.MindControlledMob;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostIntentGate;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.CommittedSpectatorGuard;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.slip.SlidingFrictionSystem;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterAppearance;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBodyShape;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.util.Optional;

/** Durable-ACTIVE CHARACTER controller; production activation is limited to the first-account join transaction. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleCharacterSessionControl {
    private static final long READY_TIMEOUT_TICKS = 100;
    private static final int MAX_INTENTS_PER_TICK = 8;
    private static final GroundedHarnessWorldStep CHARACTER_WORLD_STEP = new GroundedHarnessWorldStep();
    private static final Map<MinecraftServer, Map<UUID, Session>> SERVERS = new IdentityHashMap<>();
    private LifecycleCharacterSessionControl() { }

    public enum StartResult {
        GATE_OFF,
        MOVEMENT_CONFLICT,
        INVALID_PLAYER,
        DEBUG_SESSION_CONFLICT,
        INVALID_ACTIVE_BODY,
        CONTROLLER_INTEGRATION_REQUIRED,
        PREPARED
    }

    /**
     * Checks that a caller's already-durable ACTIVE profile could be considered for a session.
     * This deliberately does not mutate the carrier, body, registry, handler, or camera: current
     * shared camera retention recognizes debug sessions only, so beginning a lifecycle camera here
     * could silently fall back to the vanilla spectator camera.
     */
    public static StartResult start(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                     PlayerLifecycleRegistry lifecycle, long epoch) {
        StartResult preflight = preflight(player, body, lifecycle, epoch);
        if (preflight != null) return preflight;
        return StartResult.CONTROLLER_INTEGRATION_REQUIRED;
    }

    private static StartResult preflight(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                         PlayerLifecycleRegistry lifecycle, long epoch) {
        if (!MindGhostStartupGate.enabledForServer()) return StartResult.GATE_OFF;
        if (MovementStartupGate.enabledForServer()) return StartResult.MOVEMENT_CONFLICT;
        if (player == null || body == null || lifecycle == null || epoch <= 0
                || !(player.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()
                || player instanceof FakePlayer || player.isRemoved() || !player.isAlive()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || player.level().isClientSide
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            return StartResult.INVALID_PLAYER;
        }
        if (GhostMobHarnessControl.ownsDebugSession(player)) return StartResult.DEBUG_SESSION_CONFLICT;

        var profile = lifecycle.profile(player.getUUID()).orElse(null);
        var binding = body.playerCharacterBinding();
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        MobHarnessId bodyId = new MobHarnessId(body.getUUID());
        if (profile == null || !profile.active() || profile.state() != PlayerLifecycleRegistry.LifecycleState.ACTIVE
                || profile.connectionGeneration() != epoch || !profile.bodyId().equals(bodyId)
                || binding == null || !binding.accountId().equals(player.getUUID())
                || !binding.profileKey().equals(profile.profileId())
                || !binding.mindId().equals(profile.mindId().value())
                || identity == null || !identity.isBound() || !ModCharacters.HUMAN_ID.equals(identity.characterId())
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel() || body.isPassenger()
                || body.level() != level || level.getEntity(body.getUUID()) != body || !body.isNoAi()
                || CharacterIdentitySystem.resolve(body).isEmpty()
                || !lifecycle.authorizes(player.getUUID(), epoch, bodyId,
                        harness -> harness.id().equals(bodyId)
                                && harness.kind() == com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER)) {
            return StartResult.INVALID_ACTIVE_BODY;
        }

        return null;
    }

    /** Prepares the authenticated Begin/Ready/Commit handshake after durable ACTIVE promotion. */
    static StartResult beginPrepared(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                     PlayerLifecycleRegistry lifecycle, long epoch) {
        StartResult rejection = preflight(player, body, lifecycle, epoch);
        if (rejection != null) return rejection;
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        Map<UUID, Session> sessions = SERVERS.computeIfAbsent(server, ignored -> new java.util.HashMap<>());
        if (sessions.containsKey(player.getUUID())) return StartResult.DEBUG_SESSION_CONFLICT;
        Session session = new Session(player, body, lifecycle, epoch);
        sessions.put(player.getUUID(), session);
        LifecycleSessionPacketRouter.instance().installCharacterHandler(LifecycleCharacterSessionControl::onPayload,
                LifecycleCharacterSessionControl::ownsAny);
        // Login callbacks can precede client player/level binding. The first server Post tick
        // sends Begin only after rechecking the exact connected owner and body.
        return StartResult.PREPARED;
    }

    /**
     * Reports lifecycle camera ownership only for a committed, still-authorized runtime session.
     */
    public static boolean shouldKeepCharacterCamera(ServerPlayer player) {
        if (player == null || player.level().getServer() == null) return false;
        MinecraftServer server = player.level().getServer();
        Session session = session(server, player.getUUID());
        return session != null && session.player == player
                && committedCameraOwnershipMatches(session.committed, connectedExact(server, player),
                authorized(session), player.getCamera() == session.body,
                player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, player.isPassenger());
    }

    /** Exact committed owner, independent of the current camera; never changes registry state. */
    public static Optional<Entity> committedControlledEntity(ServerPlayer player) {
        return Optional.ofNullable(committedControlSnapshot(player).owned());
    }

    public static CommittedSpectatorGuard.Ownership committedControlSnapshot(ServerPlayer player) {
        var absent = CommittedSpectatorGuard.Ownership.absent();
        if (player == null || !(player.level() instanceof ServerLevel level)) return absent;
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) return absent;
        Session pinned = session(server, player.getUUID());
        if (pinned == null || pinned.player != player || !pinned.committed) return absent;
        var unavailable = CommittedSpectatorGuard.Ownership.unavailable();
        if (!connectedExact(server, player)
                || !player.isAlive() || player.isPassenger()
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || !live(pinned.body, level) || !pinned.bodyId.value().equals(pinned.body.getUUID())
                || pinned.body.playerCharacterBinding() == null
                || !pinned.accountId.equals(pinned.body.playerCharacterBinding().accountId())
                || !pinned.profileId.equals(pinned.body.playerCharacterBinding().profileKey())
                || !pinned.mindId.value().equals(pinned.body.playerCharacterBinding().mindId())
                || !pinned.lifecycle.authorizesActiveCharacterReadOnly(pinned.accountId, pinned.mindId,
                         pinned.epoch, pinned.bodyId)) return unavailable;
        return session(server, player.getUUID()) == pinned
                ? CommittedSpectatorGuard.Ownership.valid(pinned.body)
                : unavailable;
    }

    /**
     * Returns the exact live body for a committed lifecycle session, not an arbitrary player camera.
     * The immutable result is a point-in-time view; callers must recheck its body and epoch before commit.
     */
    public static Optional<ActiveCharacterBody> activeCharacterBody(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return Optional.empty();
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()) return Optional.empty();

        Session pinned = session(server, player.getUUID());
        if (pinned == null || !activeBodySnapshotEligible(pinned.player == player, pinned.committed,
                connectedExact(server, player), server.isSameThread(), player.isAlive(),
                player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, player.isPassenger(),
                player.getCamera() == pinned.body, live(pinned.body, level))
                || !activeBodyBindingMatches(pinned.body.playerCharacterBinding(), pinned.accountId,
                        pinned.profileId, pinned.mindId))
            return Optional.empty();

        if (!pinned.lifecycle.authorizesActiveCharacterReadOnly(pinned.accountId, pinned.mindId,
                pinned.epoch, pinned.bodyId)) return Optional.empty();

        // Keep the same session pinned throughout this snapshot; only cheap identity/entity checks follow.
        if (session(server, player.getUUID()) != pinned || pinned.player != player || pinned.body.level() != level
                || !connectedExact(server, player) || !live(pinned.body, level)
                || !activeBodyBindingMatches(pinned.body.playerCharacterBinding(), pinned.accountId,
                        pinned.profileId, pinned.mindId)) return Optional.empty();
        return Optional.of(new ActiveCharacterBody(pinned.body, pinned.epoch, pinned.mindId, pinned.bodyId));
    }

    /** Immutable point-in-time authority view for the lifecycle-owned CHARACTER body. */
    public record ActiveCharacterBody(PlayerCharacterHarnessEntity body, long epoch,
                                      MindId mindId, MobHarnessId harnessId) { }

    static void onPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session == null || session.player != player || !connectedExact(server, player)) return;
        if (payload instanceof GhostControlPayloads.Ready ready) {
            if (!session.beginSent || !readyDecision(ready.epoch(), session.epoch, session.committed,
                    level.getGameTime() - session.startedTick, READY_TIMEOUT_TICKS)) return;
            if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                    || preflight(player, session.body, session.lifecycle, session.epoch) != null
                    || !authorized(session)) {
                fail(server, session, FailureReason.PRECOMMIT_INELIGIBLE);
                return;
            }
            Mob mob = session.body;
            if (!(mob instanceof MindControlledMob owner)
                    || !mob.isNoAi() || !live(session.body, level)) {
                fail(server, session, FailureReason.BODY_NOT_INTENT_ELIGIBLE);
                return;
            }
            try {
                owner.moonstation14$setMovementOwned(true);
                if (!owner.moonstation14$isMovementOwned() || !mob.isNoAi()) {
                    fail(server, session, FailureReason.MOVEMENT_OWNERSHIP_REJECTED);
                    return;
                }
            } catch (RuntimeException | Error failure) {
                fail(server, session, FailureReason.MOVEMENT_OWNERSHIP_ERROR);
                return;
            }
            player.setCamera(session.body);
            session.committed = true;
            GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Commit(session.epoch));
            sendSavedIdentity(server, session);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "Lifecycle CHARACTER Ready committed (epoch={}, body={})", session.epoch, session.body.getId());
        } else if (payload instanceof GhostControlPayloads.Intent intent) {
            if (!session.committed || intent.epoch() != session.epoch) return;
            if (!authorized(session) || !connectedExact(server, player) || !shouldKeepCharacterCamera(player)
                    || !intentEligible(session, level)) {
                fail(server, session, FailureReason.RUNTIME_INELIGIBLE);
                return;
            }
            long tick = level.getGameTime();
            if (session.intentTick != tick) {
                session.intentTick = tick;
                session.intentCount = 0;
            }
            if (!intentRateAllows(++session.intentCount)) return;
            session.gate.offer(intent.sequence(), tick, intent);
            if (session.gate.exhausted()) fail(server, session, FailureReason.INTENT_GATE_EXHAUSTED);
        } else if (payload instanceof GhostControlPayloads.Stop stop && stop.epoch() == session.epoch) {
            fail(server, session, FailureReason.CLIENT_STOP);
        }
    }

    /** Only the exact committed owner receives the already-saved identity for this bound body. */
    private static void sendSavedIdentity(MinecraftServer server, Session session) {
        ServerPlayer player = session.player;
        if (!server.isSameThread() || !connectedExact(server, player) || !session.committed
                || session(server, session.accountId) != session || !shouldKeepCharacterCamera(player)
                || !activeBodyBindingMatches(session.body.playerCharacterBinding(), session.accountId,
                        session.profileId, session.mindId)) return;
        try {
            var identity = ChatIdentitySavedData.existing(server.overworld())
                    .flatMap(data -> data.character(new ChatIdentityRegistry.CharacterKey(
                            session.accountId, session.profileId))).orElse(null);
            if (identity == null) return; // No lookup may create or allocate an identity here.
            LocalSpeechNetworking.sendIdentity(player, new LocalCharacterIdentityPayload(
                    identity.name(), identity.rgb(), session.body.level().dimension().location()));
        } catch (RuntimeException failure) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn("Could not load or deliver committed character identity to owner", failure);
        }
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session == null || session.player != player || !session.committed) return;
        if (!connectedExact(server, player) || !authorized(session) || !shouldKeepCharacterCamera(player)
                || !intentEligible(session, level)) {
            fail(server, session, FailureReason.RUNTIME_INELIGIBLE);
            return;
        }
        GhostControlPayloads.Intent intent = session.gate.take();
        Mob body = (Mob) session.body;
        if (intent != null) {
            try {
                var result = CHARACTER_WORLD_STEP.step(body, intent.wishX(), intent.wishZ(),
                        (intent.buttons() & GhostControlPayloads.BUTTON_JUMP) != 0,
                        (intent.buttons() & GhostControlPayloads.BUTTON_SPRINT) != 0, intent.yaw());
                if (result.isEmpty()) {
                    fail(server, session, FailureReason.WORLD_STEP_REJECTED);
                    return;
                }
                body.setYRot(intent.yaw());
                body.setXRot(intent.pitch());
                body.setYHeadRot(intent.yaw());
                session.lastAppliedSequence = intent.sequence();
            } catch (RuntimeException | Error failure) {
                fail(server, session, FailureReason.WORLD_STEP_ERROR);
                return;
            }
        }
        sendCharacterSnapshot(server, session, body);
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()) return;
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null) return;
        for (Session session : sessions.values().toArray(Session[]::new)) {
            if (!connectedExact(server, session.player)) {
                cleanLogout(server, session);
            } else if (!live(session.body, (ServerLevel) session.player.level())) {
                fail(server, session, FailureReason.BODY_NOT_LIVE);
            } else if (!session.beginSent) {
                if (preflight(session.player, session.body, session.lifecycle, session.epoch) != null
                        || !authorized(session)) {
                    fail(server, session, FailureReason.PRECOMMIT_INELIGIBLE);
                    continue;
                }
                if (!delayedBeginDecision(sessions.get(session.accountId) == session,
                        connectedExact(server, session.player), live(session.body, (ServerLevel) session.player.level()),
                        session.beginSent)) continue;
                try {
                    GhostControlNetworking.sendToPlayer(session.player, new GhostControlPayloads.Begin(
                            session.epoch, session.body.getId(), MobHarnessKind.CHARACTER));
                    session.beginSent = true;
                    session.startedTick = session.player.level().getGameTime();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                            "Lifecycle CHARACTER Begin sent (epoch={}, body={})", session.epoch, session.body.getId());
                } catch (RuntimeException | Error failure) {
                    fail(server, session, FailureReason.BEGIN_SEND_ERROR);
                }
            } else if (timeoutDecision(session.beginSent, session.committed,
                    session.player.level().getGameTime() - session.startedTick, READY_TIMEOUT_TICKS)) {
                fail(server, session, FailureReason.READY_TIMEOUT);
            }
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session != null && session.player == player && MindGhostStartupGate.enabledForServer()
                && !MovementStartupGate.enabledForServer()) cleanLogout(server, session);
    }

    /** Drain while level entities and the startup context still exist, ahead of world saving. */
    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions != null && MindGhostStartupGate.enabledForServer() && !MovementStartupGate.enabledForServer())
            for (Session session : sessions.values().toArray(Session[]::new)) cleanLogout(server, session);
    }

    private static void cleanLogout(MinecraftServer server, Session session) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.accountId) != session) return;
        ServerLevel level = session.body.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        var binding = session.body.playerCharacterBinding();
        boolean exactBody = level != null && live(session.body, level) && session.body.isNoAi()
                && binding != null && binding.accountId().equals(session.accountId)
                && binding.profileKey().equals(session.profileId) && binding.mindId().equals(session.mindId.value())
                && session.body.getUUID().equals(session.bodyId.value());
        boolean offline = false;
        long offlineSinceMillis = Math.max(0L, System.currentTimeMillis());
        if (exactBody) {
            var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
            if (context != null) {
                var appearance = Map.of("appearance", session.body.appearance().name(),
                        "bodyShape", session.body.bodyShape().name());
                try {
                    offline = context.disconnectCleanly(session.accountId, session.epoch, session.mindId.value(),
                            session.bodyId.value(), level.dimension().location().toString(),
                            new SavedLifecycleProfile.Location(session.body.getX(), session.body.getY(), session.body.getZ()),
                            appearance, offlineSinceMillis);
                } catch (IOException | RuntimeException failure) {
                    offline = false;
                }
            }
        }
        if (offline && exactBody) session.body.setOfflineSinceMillis(offlineSinceMillis);
        if (!offline) {
            session.lifecycle.suspendActiveSessionForRecovery(session.accountId, session.mindId,
                    session.bodyId, session.epoch);
        }
        sessions.remove(session.accountId);
        if (sessions.isEmpty()) SERVERS.remove(server);
        session.gate.reset();
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
    }

    /** Durably detach an exact live CHARACTER session before allowing an operator into vanilla Creative. */
    static boolean parkForDevelopmentMode(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, player.getUUID());
        if (session == null || session.player != player || !parkPreflight(server, player, session)) return false;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return false;
        long offlineSinceMillis = Math.max(0L, System.currentTimeMillis());
        var appearance = Map.of("appearance", session.body.appearance().name(),
                "bodyShape", session.body.bodyShape().name());
        boolean persisted;
        try {
            persisted = context.disconnectCleanly(session.accountId, session.epoch, session.mindId.value(),
                    session.bodyId.value(), level.dimension().location().toString(),
                    new SavedLifecycleProfile.Location(session.body.getX(), session.body.getY(), session.body.getZ()),
                    appearance, offlineSinceMillis);
        } catch (IOException | RuntimeException failure) {
            persisted = false;
        }
        return finishDevelopmentPark(persisted, () -> {
        session.body.setOfflineSinceMillis(offlineSinceMillis);
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions != null && sessions.get(session.accountId) == session) {
            sessions.remove(session.accountId);
            if (sessions.isEmpty()) SERVERS.remove(server);
        }
        session.gate.reset();
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
        try {
            GhostControlNetworking.sendToPlayer(player, new GhostControlPayloads.Stop(session.epoch));
        } catch (RuntimeException | Error ignored) {
            // Durable OFFLINE is authoritative; a failed stop packet cannot restore the revoked epoch.
        }
        player.setCamera(player);
        });
    }

    static boolean finishDevelopmentPark(boolean persisted, Runnable revokeSession) {
        if (!persisted) return false;
        revokeSession.run();
        return true;
    }

    private static boolean parkPreflight(MinecraftServer server, ServerPlayer player, Session session) {
        return MindGhostStartupGate.enabledForServer() && !MovementStartupGate.enabledForServer()
                && server != null && server.isSameThread() && connectedExact(server, player)
                && session.player == player && authorized(session)
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                && !player.isPassenger()
                && live(session.body, (ServerLevel) player.level()) && session.body.isNoAi();
    }

    static boolean developmentModeDecision(boolean ownsSession, GameType requested, boolean operator) {
        if (!ownsSession) return true;
        return requested == GameType.SPECTATOR || requested == GameType.CREATIVE && operator;
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions != null) for (Session session : sessions.values().toArray(Session[]::new)) stopSession(server, session);
        SERVERS.remove(server);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
    }

    enum FailureReason {
        CONNECTION_MISMATCH, MODE_MISMATCH, PLAYER_PASSENGER, CAMERA_MISMATCH,
        AUTHORIZATION_REVOKED, BODY_NOT_LIVE, BODY_NOT_INTENT_ELIGIBLE,
        PRECOMMIT_INELIGIBLE, RUNTIME_INELIGIBLE, MOVEMENT_OWNERSHIP_REJECTED,
        MOVEMENT_OWNERSHIP_ERROR, INTENT_GATE_EXHAUSTED, CLIENT_STOP,
        WORLD_STEP_REJECTED, WORLD_STEP_ERROR, BEGIN_SEND_ERROR, READY_TIMEOUT,
        SNAPSHOT_BINDING_MISMATCH, SNAPSHOT_SEND_ERROR, APPEARANCE_FAILURE, DEATH_CLAIM_FAILURE
    }

    /** Priority is deterministic when more than one prerequisite is false at the failure site. */
    static FailureReason classifyRuntimeFailure(boolean exactConnection, boolean spectator,
                                                boolean passenger, boolean exactCamera,
                                                boolean authorized, boolean bodyLive,
                                                boolean intentEligible) {
        if (!exactConnection) return FailureReason.CONNECTION_MISMATCH;
        if (!spectator) return FailureReason.MODE_MISMATCH;
        if (passenger) return FailureReason.PLAYER_PASSENGER;
        if (!exactCamera) return FailureReason.CAMERA_MISMATCH;
        if (!authorized) return FailureReason.AUTHORIZATION_REVOKED;
        if (!bodyLive) return FailureReason.BODY_NOT_LIVE;
        if (!intentEligible) return FailureReason.BODY_NOT_INTENT_ELIGIBLE;
        return FailureReason.RUNTIME_INELIGIBLE;
    }

    private static void fail(MinecraftServer server, Session session, FailureReason cause) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.player.getUUID()) != session) return;
        ServerPlayer player = session.player;
        ServerLevel level = player.level() instanceof ServerLevel current ? current : null;
        boolean exactConnection = connectedExact(server, player);
        boolean spectator = player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
        boolean passenger = player.isPassenger();
        Entity camera = player.getCamera();
        boolean exactCamera = camera == session.body;
        boolean bodyLive = level != null && live(session.body, level);
        boolean authorized;
        boolean eligible;
        try {
            authorized = authorized(session);
        } catch (RuntimeException | Error diagnosticFailure) {
            authorized = false;
        }
        try {
            eligible = level != null && intentEligible(session, level);
        } catch (RuntimeException | Error diagnosticFailure) {
            eligible = false;
        }
        FailureReason reason = cause == FailureReason.RUNTIME_INELIGIBLE
                ? classifyRuntimeFailure(exactConnection, spectator, passenger, exactCamera,
                        authorized, bodyLive, eligible)
                : cause == FailureReason.PRECOMMIT_INELIGIBLE && !authorized
                    ? FailureReason.AUTHORIZATION_REVOKED : cause;
        // Capture before revoking authority or disconnecting; never log account/profile/mind IDs.
        com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "Lifecycle CHARACTER terminal failure reason={} phase={} epoch={} expectedBodyEntityId={} cameraEntityId={} mode={} passenger={} exactConnection={} authorized={} bodyLive={} intentEligible={}",
                reason, session.committed ? "COMMITTED" : "HANDSHAKE", session.epoch,
                session.body.getId(), camera == null ? -1 : camera.getId(),
                player.gameMode.getGameModeForPlayer(), passenger, exactConnection, authorized, bodyLive, eligible);
        sessions.remove(session.player.getUUID());
        if (sessions.isEmpty()) SERVERS.remove(server);
        clearOwnedMarkerForSession(session);
        session.lifecycle.suspendActiveSessionForRecovery(session.player.getUUID(), session.mindId,
                session.bodyId, session.epoch);
        if (connectedExact(server, session.player)) session.player.connection.disconnect(
                net.minecraft.network.chat.Component.literal("Character session requires explicit recovery (" + reason + ")."));
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
    }

    /** Server shutdown ends only ephemeral controller state; durable ACTIVE claims are not deleted or rewritten. */
    private static void stopSession(MinecraftServer server, Session session) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.player.getUUID()) != session) return;
        sessions.remove(session.player.getUUID());
        if (sessions.isEmpty()) SERVERS.remove(server);
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
    }

    private static void clearOwnedMarkerForSession(Session session) {
        Mob body = session.body;
        if (body instanceof MindControlledMob owner
                && session.bodyId.equals(new MobHarnessId(session.body.getUUID()))
                && session.body.playerCharacterBinding() != null
                && session.body.playerCharacterBinding().accountId().equals(session.accountId)
                && session.body.playerCharacterBinding().profileKey().equals(session.profileId)
                && session.body.playerCharacterBinding().mindId().equals(session.mindId.value())) {
            try {
                owner.moonstation14$setMovementOwned(false);
            } catch (RuntimeException | Error ignored) {
                // Fail closed: leave the custom body protected if ownership cannot be safely cleared.
            }
        }
    }

    private static boolean intentEligible(Session session, ServerLevel level) {
        Mob mob = session.body;
        return mob instanceof MindControlledMob owner
                && live(session.body, level) && mob.isNoAi() && owner.moonstation14$isMovementOwned()
                && session.bodyId.equals(new MobHarnessId(session.body.getUUID()));
    }

    private static void sendCharacterSnapshot(MinecraftServer server, Session session, Mob body) {
        long tick = body.level().getGameTime();
        if (tick <= session.lastSnapshotTick) return;
        Vec3 position = body.position();
        Vec3 velocity = body.getDeltaMovement().scale(20d);
        float yaw = (float) (body.getYRot() - 360.0 * Math.floor((body.getYRot() + 180.0) / 360.0));
        float pitch = Math.max(-90f, Math.min(90f, body.getXRot()));
        float surface = (float) SlidingFrictionSystem.frictionFactor(body);
        float voluntary = (float) (CharacterControlSystem.isKnockedDown(body)
                ? com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR : 1d);
        try {
            var snapshot = new GhostControlPayloads.Snapshot(session.epoch, body.getId(), MobHarnessKind.CHARACTER,
                    tick, session.lastAppliedSequence, position.x, position.y, position.z, yaw, pitch,
                    velocity.x, velocity.y, velocity.z, body.onGround(), surface, voluntary,
                    CharacterControlSystem.isStunned(body));
            if (!snapshotBoundToSession(snapshot, session.epoch, body.getId())) {
                fail(server, session, FailureReason.SNAPSHOT_BINDING_MISMATCH);
                return;
            }
            session.lastSnapshotTick = tick;
            GhostControlNetworking.sendToPlayer(session.player, snapshot);
        } catch (RuntimeException failure) {
            fail(server, session, FailureReason.SNAPSHOT_SEND_ERROR);
        }
    }

    private static boolean authorized(Session session) {
        return session.lifecycle.authorizes(session.player.getUUID(), session.epoch, session.bodyId,
                harness -> harness.id().equals(session.bodyId) && harness.kind() == MobHarnessKind.CHARACTER)
                && session.player.getUUID().equals(session.accountId)
                && session.profileId.equals(session.body.playerCharacterBinding() == null ? null
                : session.body.playerCharacterBinding().profileKey());
    }

    private static boolean live(PlayerCharacterHarnessEntity body, ServerLevel level) {
        return body != null && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && body.level() == level && level.getEntity(body.getUUID()) == body && !body.isPassenger();
    }

    private static Session session(MinecraftServer server, UUID account) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        return sessions == null ? null : sessions.get(account);
    }

    /** Exact committed lifecycle possession; appearance self-service is unavailable during handshake. */
    static boolean ownsCommittedAppearanceSession(ServerPlayer player, PlayerCharacterHarnessEntity body,
                                                   PlayerLifecycleRegistry lifecycle, long generation) {
        if (player == null || body == null || lifecycle == null || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return false;
        Session session = session(level.getServer(), player.getUUID());
        return session != null && session.player == player && session.body == body && session.lifecycle == lifecycle
                && session.epoch == generation && session.committed && authorized(session)
                && connectedExact(level.getServer(), player);
    }

    /** Post-durable appearance mutation failure invalidates this exact session and fails closed. */
    static void failAppearanceSession(ServerPlayer player, PlayerCharacterHarnessEntity body, long generation) {
        if (player == null || body == null || !(player.level() instanceof ServerLevel level)) return;
        Session session = session(level.getServer(), player.getUUID());
        if (session != null && session.player == player && session.body == body && session.epoch == generation)
            fail(level.getServer(), session, FailureReason.APPEARANCE_FAILURE);
    }

    /** Durably claim an exact connected corpse before dropping its ACTIVE session. */
    static boolean hasExactDeathSession(PlayerCharacterHarnessEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || body.playerCharacterBinding() == null) return false;
        Session session = session(level.getServer(), body.playerCharacterBinding().accountId());
        return session != null && session.body == body && session.bodyId.value().equals(body.getUUID());
    }

    static boolean claimConfirmedDeath(PlayerCharacterHarnessEntity body, LifecycleServerContext context) {
        if (body == null || context == null || !(body.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        Session session = server == null ? null : session(server, body.playerCharacterBinding() == null
                ? new UUID(0, 0) : body.playerCharacterBinding().accountId());
        if (session == null || session.body != body || !session.bodyId.value().equals(body.getUUID())) return false;
        var binding = body.playerCharacterBinding();
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        boolean exact = connectedDeathDecision(binding != null && identity != null && identity.isBound()
                && ModCharacters.HUMAN_ID.equals(identity.characterId()), binding != null
                && session.accountId.equals(binding.accountId()) && session.profileId.equals(binding.profileKey())
                && session.mindId.value().equals(binding.mindId()) && session.player.getUUID().equals(session.accountId)
                && session.body == body && session.bodyId.value().equals(body.getUUID()),
                session.epoch > 0 && session.lifecycle.profile(session.accountId).map(profile -> profile.active()
                && profile.state() == PlayerLifecycleRegistry.LifecycleState.ACTIVE
                && profile.connectionGeneration() == session.epoch && profile.mindId().equals(session.mindId)
                && profile.bodyId().equals(session.bodyId) && profile.profileId().equals(session.profileId)).orElse(false),
                connectedExact(server, session.player), level.getServer().isSameThread());
        boolean claimed = false;
        if (exact) {
            var appearance = Map.of("appearance", body.appearance().name(), "bodyShape", body.bodyShape().name());
            try {
                claimed = context.claimActiveDeath(session.accountId, session.profileId, session.mindId.value(),
                        session.bodyId.value(), level.dimension().location().toString(),
                        new SavedLifecycleProfile.Location(body.getX(), body.getY(), body.getZ()), appearance,
                        session.epoch, candidate -> candidate.equals(new com.juicyslew.moonstation14.ms14.player_body_control.MobHarness(
                                session.bodyId, MobHarnessKind.CHARACTER)) && body.hasConfirmedDeath()
                                && body.getHealth() <= 0 && !body.isRemoved() && body.level() == level
                                && level.getEntity(body.getUUID()) == body
                                && binding.accountId().equals(session.accountId)
                                && binding.profileKey().equals(session.profileId)
                                && binding.mindId().equals(session.mindId.value()));
            } catch (IOException | RuntimeException failure) {
                claimed = false;
            }
        }
        if (claimed) {
            boolean stopSent = finishDeathClaim(server, session);
            if (stopSent) handoffClaimedDeath(server, session);
            else disconnectAfterDeathClaim(server, session.player,
                    "Your character has died, but ghost entry could not be completed safely. Log out and reconnect to retry, or contact an administrator.");
        }
        else fail(server, session, FailureReason.DEATH_CLAIM_FAILURE);
        return claimed;
    }

    private static boolean finishDeathClaim(MinecraftServer server, Session session) {
        Map<UUID, Session> sessions = SERVERS.get(server);
        if (sessions == null || sessions.get(session.accountId) != session) return false;
        sessions.remove(session.accountId);
        if (sessions.isEmpty()) SERVERS.remove(server);
        session.gate.reset();
        clearOwnedMarkerForSession(session);
        if (SERVERS.isEmpty()) LifecycleSessionPacketRouter.instance().clearCharacterHandler();
        try {
            if (connectedExact(server, session.player)) {
                GhostControlNetworking.sendToPlayer(session.player, new GhostControlPayloads.Stop(session.epoch));
                return true;
            }
        } catch (RuntimeException | Error failure) {
            // The old epoch is revoked; do not start a new handshake if Stop could not be delivered.
        }
        return false;
    }

    /** Claim is durable and the old epoch is revoked before staging or authorizing a ghost. */
    private static void handoffClaimedDeath(MinecraftServer server, Session oldSession) {
        ServerPlayer player = oldSession.player;
        if (!connectedExact(server, player)) return;
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) {
            disconnectAfterDeathClaim(server, player, "Your character has died, but ghost entry is temporarily unavailable. Log out and reconnect to retry, or contact an administrator.");
            return;
        }
        LifecycleDeadClaimGhostStager.Result staged;
        try {
            staged = LifecycleDeadClaimGhostStager.stageConnectedDeath(player, server);
        } catch (RuntimeException | Error failure) {
            staged = null;
        }
        if (staged == null || staged.outcome() != LifecycleDeadClaimGhostStager.Outcome.PREPARED
                || staged.prepared() == null) {
            String detail = staged == null ? "unexpected staging failure" : staged.diagnostic();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                    "Saved character death could not be handed off to a ghost (account={}): {}",
                    player.getUUID(), detail);
            disconnectAfterDeathClaim(server, player, "Your character has died, but ghost entry could not be prepared safely. Log out and reconnect to retry, or contact an administrator.");
            return;
        }

        var prepared = staged.prepared();
        try {
            LifecycleGhostSessionControl.StartResult started = LifecycleGhostSessionControl.beginPrepared(
                    player, prepared.ghost(), context.lifecycle(), prepared.generation(),
                    new MobHarnessId(prepared.corpseUUID()));
            if (started == LifecycleGhostSessionControl.StartResult.PREPARED) return;
        } catch (RuntimeException | Error failure) {
            // The prepared ghost is returned below; the durable DEAD_CLAIM and corpse remain authoritative.
        }
        if (!LifecycleDeadClaimGhostStager.returnPreparedToDeadClaim(context, prepared))
            LifecycleDeadClaimGhostStager.suspendAndDiscardPrepared(context, prepared);
        disconnectAfterDeathClaim(server, player, "Your character has died, but ghost entry could not be started safely. Log out and reconnect to retry, or contact an administrator.");
    }

    private static void disconnectAfterDeathClaim(MinecraftServer server, ServerPlayer player, String message) {
        if (connectedExact(server, player)) player.connection.disconnect(net.minecraft.network.chat.Component.literal(message));
    }

    private static boolean owns(MinecraftServer server, ServerPlayer player) {
        return player != null && player.level().getServer() == server && connectedExact(server, player)
                && session(server, player.getUUID()) != null && session(server, player.getUUID()).player == player;
    }

    static boolean ownsAny(ServerPlayer player) {
        return player != null && owns(player.level().getServer(), player);
    }

    private static boolean connectedExact(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null && !player.isRemoved() && !(player instanceof FakePlayer)
                && player.level().getServer() == server && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    static boolean readyDecision(long readyEpoch, long sessionEpoch, boolean committed, long elapsedTicks,
                                 long timeoutTicks) {
        return !committed && readyEpoch == sessionEpoch && elapsedTicks >= 0 && elapsedTicks < timeoutTicks;
    }

    static boolean delayedBeginDecision(boolean exactSession, boolean exactConnectedPlayer,
                                        boolean eligibleBody, boolean alreadySent) {
        return exactSession && exactConnectedPlayer && eligibleBody && !alreadySent;
    }

    static boolean timeoutDecision(boolean beginSent, boolean committed, long elapsedTicks, long timeoutTicks) {
        return beginSent && !committed && elapsedTicks >= timeoutTicks;
    }

    private static final class Session {
        final ServerPlayer player; final PlayerCharacterHarnessEntity body; final PlayerLifecycleRegistry lifecycle;
        final long epoch; long startedTick = -1; final UUID accountId; final String profileId;
        final MindId mindId; final MobHarnessId bodyId; boolean committed, beginSent;
        final GhostIntentGate<GhostControlPayloads.Intent> gate = new GhostIntentGate<>();
        long intentTick = Long.MIN_VALUE, lastAppliedSequence, lastSnapshotTick = Long.MIN_VALUE;
        int intentCount;
        Session(ServerPlayer player, PlayerCharacterHarnessEntity body, PlayerLifecycleRegistry lifecycle,
                 long epoch) {
            this.player = player; this.body = body; this.lifecycle = lifecycle; this.epoch = epoch;
            this.accountId = player.getUUID();
            var profile = lifecycle.profile(accountId).orElseThrow();
            this.profileId = profile.profileId(); this.mindId = profile.mindId(); this.bodyId = profile.bodyId();
        }
    }

    static boolean committedCameraOwnershipMatches(boolean committedSession,
                                                    boolean exactConnectedPlayerOnSameServer,
                                                    boolean currentMindEpochAndBodyAuthorized,
                                                    boolean cameraIsAuthorizedBody,
                                                    boolean spectator,
                                                    boolean hasPassenger) {
        return committedSession && exactConnectedPlayerOnSameServer && currentMindEpochAndBodyAuthorized
                && cameraIsAuthorizedBody && spectator && !hasPassenger;
    }

    static boolean activeBodySnapshotEligible(boolean exactSession, boolean committedSession,
                                               boolean exactConnectedPlayer, boolean serverThread,
                                               boolean playerAlive, boolean spectator, boolean hasPassenger,
                                               boolean cameraIsExactBody, boolean liveBody) {
        return exactSession && committedSession && exactConnectedPlayer && serverThread && playerAlive
                && spectator && !hasPassenger && cameraIsExactBody && liveBody;
    }

    static boolean activeBodyBindingMatches(PlayerCharacterBinding binding, UUID accountId,
                                            String profileId, MindId mindId) {
        return binding != null && accountId.equals(binding.accountId())
                && profileId.equals(binding.profileKey()) && mindId.value().equals(binding.mindId());
    }

    static boolean connectedDeathDecision(boolean exactBindingAndHuman, boolean exactSession,
                                          boolean currentActiveEpoch, boolean exactConnectedOwner,
                                          boolean serverThread) {
        return exactBindingAndHuman && exactSession && currentActiveEpoch && exactConnectedOwner && serverThread;
    }

    static StartResult gateDecision(boolean masterEnabled, boolean movementConflict) {
        if (!masterEnabled) return StartResult.GATE_OFF;
        if (movementConflict) return StartResult.MOVEMENT_CONFLICT;
        return StartResult.CONTROLLER_INTEGRATION_REQUIRED;
    }

    static boolean intentRateAllows(int intentCount) {
        return intentCount > 0 && intentCount <= MAX_INTENTS_PER_TICK;
    }

    static boolean snapshotBoundToSession(GhostControlPayloads.Snapshot snapshot, long epoch, int entityId) {
        return snapshot != null && snapshot.epoch() == epoch && snapshot.harnessEntityId() == entityId
                && snapshot.harnessKind() == MobHarnessKind.CHARACTER
                && entityId >= 0;
    }
}
