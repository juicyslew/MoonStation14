package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.MinecraftLoadedBodyAdapter;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Exact, loaded-body-only production reconnect for one authenticated account. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleExistingBodyReconnect {
    private static final String DEFER = "Your saved character area is not currently loaded. Reconnect was deferred; retry shortly or contact an administrator. No replacement was spawned.";
    private static final String RECOVERY = "Your saved character could not be proven safe to reconnect. No replacement was spawned; contact an administrator for recovery.";
    static final int MAX_PENDING_TICKS = 20;
    private static final Map<MinecraftServer, Map<UUID, Pending>> PENDING = new HashMap<>();

    private LifecycleExistingBodyReconnect() { }

    /** Same-connection development return deliberately accepts only an already loaded exact body. */
    static boolean returnDevelopmentDetached(ServerPlayer player, MinecraftServer server) {
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                || !LifecycleDevelopmentMode.isTracked(server, player) || server == null || !server.isSameThread()
                || player == null || player.connection == null || !player.connection.isAcceptingMessages()
                || player.isRemoved() || player.getServer() != server
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.CREATIVE) return false;
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return false;
        SavedLifecycleProfile saved;
        try {
            saved = context.primaryStore().withCurrentPrimary(lease -> {
                var rows = lease.envelope().profiles().stream()
                        .filter(row -> row.accountId().equals(player.getUUID())).toList();
                return rows.size() == 1 && rows.get(0).state() == SavedLifecycleProfile.State.OFFLINE
                        ? rows.get(0) : null;
            }).orElse(null);
        } catch (IOException | RuntimeException failure) { return false; }
        if (saved == null || !LifecycleDevelopmentMode.verifiedDetachedOffline(server, player, saved)) return false;
        var observed = MinecraftLoadedBodyAdapter.observe(server, saved);
        if (observed.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
                || !(observed.candidate().entity() instanceof PlayerCharacterHarnessEntity body)
                || !body.isNoAi()) return false;
        ServerLevel bodyLevel = recordedLevel(server, saved);
        var binding = body.playerCharacterBinding();
        if (bodyLevel == null || body.level() != bodyLevel || bodyLevel.getEntity(body.getUUID()) != body
                || binding == null || body.hasInvalidSavedBinding()
                || !binding.accountId().equals(player.getUUID()) || !binding.profileKey().equals(saved.profileKey())
                || !binding.mindId().equals(saved.mindId()) || !body.getUUID().equals(saved.bodyId())) return false;
        double oldX = player.getX(), oldY = player.getY(), oldZ = player.getZ();
        ServerLevel oldLevel = (ServerLevel) player.level();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        // Recheck current primary after observing the body, before moving the Creative carrier.
        try {
            if (!saved.equals(context.currentAccountProfile(player.getUUID()).orElse(null))
                    || !LifecycleDevelopmentMode.verifiedDetachedOffline(server, player, saved)) return false;
        } catch (IOException | RuntimeException failure) { return false; }
        if (!LifecycleDevelopmentMode.setReturningBodySpectator(player)) {
            restoreCreative(player, oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            return false;
        }
        player.teleportTo(bodyLevel, saved.location().x(), saved.location().y(), saved.location().z(), oldYaw, oldPitch);
        if (player.level() != bodyLevel || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            restoreCreative(player, oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            return false;
        }
        body.setOfflineSinceMillis(saved.offlineSinceMillis());
        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.Snapshot active;
        try {
            active = context.reconnectOffline(saved,
                    new MobHarness(new MobHarnessId(body.getUUID()), MobHarnessKind.CHARACTER)).orElse(null);
        } catch (IOException | RuntimeException failure) { active = null; }
        if (active == null || active.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE
                || !active.active() || active.connectionGeneration() <= saved.connectionGeneration()) {
            var current = context.lifecycle().profile(player.getUUID()).orElse(null);
            if (current != null && current.active()
                    && current.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE
                    && current.connectionGeneration() > saved.connectionGeneration()) {
                player.connection.disconnect(Component.literal("Character authority may be active but reconnect could not be verified. Disconnecting for recovery."));
                return false;
            }
            player.teleportTo(oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            restoreCreative(player, oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            return false;
        }
        body.clearOfflineSinceMillis();
        try {
            if (LifecycleCharacterSessionControl.beginPrepared(player, body, context.lifecycle(), active.connectionGeneration())
                    != LifecycleCharacterSessionControl.StartResult.PREPARED) throw new IllegalStateException("session was not prepared");
        } catch (RuntimeException | Error failure) {
            context.lifecycle().suspendActiveSessionForRecovery(player.getUUID(),
                    new com.juicyslew.moonstation14.ms14.player_body_control.MindId(saved.mindId()),
                    new MobHarnessId(saved.bodyId()), active.connectionGeneration());
            player.connection.disconnect(Component.literal("Character authority was durably reconnected but controller startup failed. Reconnect for recovery."));
            return false;
        }
        LifecycleDevelopmentMode.returned(server, player);
        player.sendSystemMessage(Component.literal("Returned to your existing character."));
        return true;
    }

    private static void restoreCreative(ServerPlayer player, ServerLevel oldLevel, double x, double y, double z,
                                        float yaw, float pitch) {
        if (player.connection == null || !player.connection.isAcceptingMessages()) return;
        try {
            if (player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.CREATIVE
                    && (!LifecycleDevelopmentMode.restoreDetachedCreative(player)
                    || player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.CREATIVE)) {
                disconnectFailedRestore(player);
                return;
            }
            if (player.level() != oldLevel) player.teleportTo(oldLevel, x, y, z, yaw, pitch);
        } catch (RuntimeException | Error failure) {
            disconnectFailedRestore(player);
        }
    }

    private static void disconnectFailedRestore(ServerPlayer player) {
        if (player.connection != null && player.connection.isAcceptingMessages())
            player.connection.disconnect(Component.literal("Character return could not restore the Creative carrier safely. Reconnect for recovery."));
    }

    static void handle(ServerPlayer player, MinecraftServer server, LifecycleServerContext context,
                       Consumer<String> disconnect) {
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()) return;
        if (server == null || context == null || !server.isSameThread() || player == null
                || player.connection == null || !player.connection.isAcceptingMessages()
                || player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || GhostMobHarnessControl.ownsDebugSession(player)) {
            disconnect.accept(RECOVERY);
            return;
        }
        SavedLifecycleProfile saved;
        try {
            saved = context.primaryStore().withCurrentPrimary(lease -> {
                var matches = lease.envelope().profiles().stream()
                        .filter(row -> row.accountId().equals(player.getUUID())).toList();
                return matches.size() == 1 && matches.get(0).state() == SavedLifecycleProfile.State.OFFLINE
                        ? matches.get(0) : null;
            }).orElse(null);
        } catch (IOException | RuntimeException failure) {
            disconnect.accept(RECOVERY);
            return;
        }
        if (saved == null) { disconnect.accept(RECOVERY); return; }

        if (hasPending(server, player.getUUID())) { disconnect.accept(RECOVERY); return; }
        LoadedBodyResolver.Resolution observed = MinecraftLoadedBodyAdapter.observe(server, saved);
        if (observed.outcome() == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY) {
            ServerLevel recorded = recordedLevel(server, saved);
            Integer cx = MinecraftLoadedBodyAdapter.savedChunkCoordinate(saved.location().x());
            Integer cz = MinecraftLoadedBodyAdapter.savedChunkCoordinate(saved.location().z());
            if (recorded == null || cx == null || cz == null) {
                disconnect.accept(DEFER);
                return;
            }
            try {
                // One exact persisted chunk only. Never search, create a replacement, or walk neighboring chunks.
                recorded.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
            } catch (RuntimeException | Error failure) {
                disconnect.accept(DEFER);
                return;
            }
            observed = MinecraftLoadedBodyAdapter.observe(server, saved);
            if (observed.outcome() == LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) {
                complete(server, player, context, saved, observed, disconnect);
                return;
            }
            // FULL can return before vanilla drains its entity-loading inbox. Permit only an
            // exact-UUID absence after this one forced load; any visible bad evidence fails closed.
            if (observed.outcome() == LoadedBodyResolver.Outcome.RECOVERY_REQUIRED
                    && !bodyVisibleInAnyLevel(server, saved.bodyId())) {
                startPending(server, player, context, saved, disconnect);
                return;
            }
        }
        if (observed.outcome() == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY) {
            startPending(server, player, context, saved, disconnect);
            return;
        }
        complete(server, player, context, saved, observed, disconnect);
    }

    private static void startPending(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                     SavedLifecycleProfile saved, Consumer<String> disconnect) {
        if (!makeCarrierInert(player) || !retryOwnerValid(server, player, context, saved)) {
            disconnect.accept(RECOVERY);
            return;
        }
        putPending(server, player, context, saved, disconnect);
    }

    private static boolean bodyVisibleInAnyLevel(MinecraftServer server, UUID bodyId) {
        for (ServerLevel level : server.getAllLevels())
            if (level.getEntity(bodyId) != null) return true;
        return false;
    }

    private static void complete(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                 SavedLifecycleProfile saved, LoadedBodyResolver.Resolution observed,
                                 Consumer<String> disconnect) {
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                || observed.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
                || !(observed.candidate().entity() instanceof PlayerCharacterHarnessEntity body)
                || !body.isNoAi()) {
            disconnect.accept(observed.outcome() == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY ? DEFER : RECOVERY);
            return;
        }
        ServerLevel bodyLevel = recordedLevel(server, saved);
        if (bodyLevel == null || body.level() != bodyLevel || bodyLevel.getEntity(body.getUUID()) != body) {
            disconnect.accept(RECOVERY);
            return;
        }
        var binding = body.playerCharacterBinding();
        if (binding == null || body.hasInvalidSavedBinding()
                || !binding.accountId().equals(saved.accountId())
                || !binding.profileKey().equals(saved.profileKey())
                || !binding.mindId().equals(saved.mindId())
                || !body.getUUID().equals(saved.bodyId()) || saved.offlineSinceMillis() == null) {
            disconnect.accept(RECOVERY);
            return;
        }
        // Durable OFFLINE data is authoritative over a stale/missing entity timestamp after restart.
        body.setOfflineSinceMillis(saved.offlineSinceMillis());
        // Place the carrier using the saved dimension as authority before any durable activation.
        if (!makeCarrierInert(player)) {
            disconnect.accept(RECOVERY);
            return;
        }
        double x = saved.location().x(), y = saved.location().y(), z = saved.location().z();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            disconnect.accept(RECOVERY);
            return;
        }
        player.teleportTo(bodyLevel, x, y, z, player.getYRot(), player.getXRot());
        if (player.level() != bodyLevel || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            disconnect.accept(RECOVERY);
            return;
        }
        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.Snapshot active;
        try {
            active = context.reconnectOffline(saved,
                    new MobHarness(new MobHarnessId(body.getUUID()), MobHarnessKind.CHARACTER)).orElse(null);
        } catch (IOException | RuntimeException failure) {
            active = null;
        }
        if (active == null || active.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE
                || !active.active() || active.connectionGeneration() <= saved.connectionGeneration()) {
            disconnect.accept(RECOVERY);
            return;
        }
        // The marker remains if the durable CAS failed or returned ambiguous evidence.
        body.clearOfflineSinceMillis();
        try {
            var result = LifecycleCharacterSessionControl.beginPrepared(player, body, context.lifecycle(), active.connectionGeneration());
            if (result != LifecycleCharacterSessionControl.StartResult.PREPARED) throw new IllegalStateException("session was not prepared");
        } catch (RuntimeException | Error failure) {
            context.lifecycle().suspendActiveSessionForRecovery(player.getUUID(),
                    new com.juicyslew.moonstation14.ms14.player_body_control.MindId(saved.mindId()),
                    new MobHarnessId(saved.bodyId()), active.connectionGeneration());
            disconnect.accept("Character authority was durably reconnected but controller startup failed. Reconnect is deferred; contact an administrator.");
        }
    }

    private static boolean makeCarrierInert(ServerPlayer player) {
        return player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)
                && player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR;
    }

    private static boolean retryOwnerValid(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                           SavedLifecycleProfile saved) {
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                || server == null || context == null || !server.isSameThread() || player == null
                || player.getServer() != server || player.connection == null || !player.connection.isAcceptingMessages()
                || player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || GhostMobHarnessControl.ownsDebugSession(player)) return false;
        var snapshot = LifecycleStartupRuntime.snapshot(server).orElse(null);
        if (snapshot == null || !LifecycleFirstJoinHandler.existingAccountMayReconnect(snapshot.state(), context,
                player.getUUID(), saved.state())) return false;
        try {
            return context.primaryStore().withCurrentPrimary(lease -> {
                var rows = lease.envelope().profiles().stream()
                        .filter(row -> row.accountId().equals(player.getUUID())).toList();
                return rows.size() == 1 && rows.get(0).state() == SavedLifecycleProfile.State.OFFLINE
                        && rows.get(0).equals(saved);
            }).orElse(false);
        } catch (IOException | RuntimeException failure) { return false; }
    }

    private static boolean hasPending(MinecraftServer server, UUID account) {
        Map<UUID, Pending> entries = PENDING.get(server);
        return entries != null && entries.containsKey(account);
    }

    private static void putPending(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                   SavedLifecycleProfile saved, Consumer<String> disconnect) {
        PENDING.computeIfAbsent(server, ignored -> new HashMap<>())
                .put(player.getUUID(), new Pending(player, context, saved, disconnect, 0));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, Pending> entries = PENDING.get(server);
        if (entries == null) return;
        for (var item : entries.entrySet().toArray(Map.Entry[]::new)) {
            @SuppressWarnings("unchecked") Map.Entry<UUID, Pending> entry = (Map.Entry<UUID, Pending>) item;
            Pending pending = entry.getValue();
            if (entries.get(entry.getKey()) != pending) continue;
            boolean ownerValid = retryOwnerValid(server, pending.player, pending.context, pending.saved);
            if (!ownerValid) {
                removePending(server, entry.getKey(), pending);
                pending.disconnect.accept(RECOVERY);
                continue;
            }
            LoadedBodyResolver.Resolution observed = MinecraftLoadedBodyAdapter.observe(server, pending.saved);
            if (observed.outcome() == LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) {
                removePending(server, entry.getKey(), pending);
                complete(server, pending.player, pending.context, pending.saved, observed, pending.disconnect);
            } else {
                boolean inboxAbsence = observed.outcome() == LoadedBodyResolver.Outcome.RECOVERY_REQUIRED
                        && !bodyVisibleInAnyLevel(server, pending.saved.bodyId());
                PendingDecision decision = pendingDecision(pending.elapsedTicks + 1, observed.outcome(), true,
                        inboxAbsence);
                if (decision == PendingDecision.FAIL_CLOSED) {
                    removePending(server, entry.getKey(), pending);
                    pending.disconnect.accept(RECOVERY);
                } else if (decision == PendingDecision.TIMEOUT) {
                    removePending(server, entry.getKey(), pending);
                    pending.disconnect.accept(DEFER);
                } else {
                    entries.put(entry.getKey(), pending.tick());
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.level() instanceof ServerLevel level ? level.getServer() : player.getServer();
        if (server != null) removePending(server, player.getUUID(), null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStopped(ServerStoppedEvent event) { PENDING.remove(event.getServer()); }

    private static void removePending(MinecraftServer server, UUID account, Pending expected) {
        Map<UUID, Pending> entries = PENDING.get(server);
        if (entries == null) return;
        if (expected == null) entries.remove(account); else entries.remove(account, expected);
        if (entries.isEmpty()) PENDING.remove(server);
    }

    private record Pending(ServerPlayer player, LifecycleServerContext context, SavedLifecycleProfile saved,
                           Consumer<String> disconnect, int elapsedTicks) {
        private Pending tick() { return new Pending(player, context, saved, disconnect, elapsedTicks + 1); }
    }

    static PendingDecision pendingDecision(int elapsedTicks, LoadedBodyResolver.Outcome outcome, boolean ownerValid) {
        return pendingDecision(elapsedTicks, outcome, ownerValid, false);
    }

    static PendingDecision pendingDecision(int elapsedTicks, LoadedBodyResolver.Outcome outcome, boolean ownerValid,
                                           boolean bodyEntirelyAbsent) {
        if (!ownerValid) return PendingDecision.FAIL_CLOSED;
        if (outcome == LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) return PendingDecision.COMPLETE;
        if (outcome != LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY
                && !(outcome == LoadedBodyResolver.Outcome.RECOVERY_REQUIRED && bodyEntirelyAbsent))
            return PendingDecision.FAIL_CLOSED;
        return elapsedTicks >= MAX_PENDING_TICKS ? PendingDecision.TIMEOUT : PendingDecision.RETRY;
    }

    enum PendingDecision { COMPLETE, RETRY, TIMEOUT, FAIL_CLOSED }

    private static ServerLevel recordedLevel(MinecraftServer server, SavedLifecycleProfile saved) {
        ResourceLocation dimension;
        try { dimension = ResourceLocation.parse(saved.dimension()); }
        catch (RuntimeException invalid) { return null; }
        ServerLevel match = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) {
                if (match != null) return null;
                match = level;
            }
        }
        return match;
    }
}
