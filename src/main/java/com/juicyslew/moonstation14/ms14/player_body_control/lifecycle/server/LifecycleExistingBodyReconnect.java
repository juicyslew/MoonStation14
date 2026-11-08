package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbe;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.LoadedBodyResolver;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.MinecraftLoadedBodyAdapter;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Exact, loaded-body-only production reconnect for one authenticated account. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleExistingBodyReconnect {
    private static final String DEFER = "Your saved character area is not currently loaded. Reconnect was deferred; retry shortly or contact an administrator. No replacement was spawned.";
    private static final String RECOVERY = "Your saved character could not be proven safe to reconnect. No replacement was spawned; contact an administrator for recovery.";
    private static final String DIRTY_CARRIER = "Character reconnect could not verify that the carrier inventory is isolated from character control. Your items were left untouched; contact an administrator for recovery.";
    static final int MAX_PENDING_TICKS = 20;
    private static final Map<MinecraftServer, Map<UUID, Pending>> PENDING = new HashMap<>();
    private static final int MAX_RETURN_WAIT_TICKS = 200;
    private static final Map<MinecraftServer, Map<UUID, ReturnWait>> RETURN_WAITS = new HashMap<>();
    private static final Map<MinecraftServer, Map<UUID, Waiting>> WAITING = new HashMap<>();
    private static final int RETURN_TICKET_RADIUS = 0;
    private static final TicketType<UUID> RETURN_TICKET = TicketType.create("moonstation14_exact_body_return", UUID::compareTo);
    enum ReturnResult { RETURNED, CHUNK_LOADING, BLOCKED }
    private record ReturnWait(ServerPlayer player, SavedLifecycleProfile saved, int deadline,
                              ServerLevel level, ChunkPos chunk, UUID ticketKey) { }
    private record Waiting(ServerPlayer player, SavedLifecycleProfile saved, GameType originalMode,
                           boolean readOnlyCreativeParkDenial) { }

    private LifecycleExistingBodyReconnect() { }

    /** Saved-body admission failures are recoverable: retain the authenticated vanilla carrier and claim. */
    static void reportDeferredAdmission(ServerPlayer player, String reason) {
        reportDeferredAdmission(player, reason, null, false);
    }

    private static void reportDeferredAdmission(ServerPlayer player, String reason,
                                                SavedLifecycleProfile saved, boolean creativeParkDenial) {
        if (player == null || player.connection == null || !player.connection.isAcceptingMessages()) return;
        MinecraftServer server = player.getServer();
        String phase = reason == null ? "unknown" : reason;
        rememberWaiting(server, player, saved, creativeParkDenial);
        // A vanilla carrier is never character authority. Spectator removes movement/item use
        // without mutating carrier inventory or the saved character/Mind.
        boolean spectator = player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
        if (server != null && server.isSameThread() && !spectator) {
            try { spectator = safeWaitAdmission(() -> LifecycleDevelopmentMode.switchWaitingSpectator(player, server),
                    () -> player.gameMode.getGameModeForPlayer()); }
            catch (RuntimeException | Error failure) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Saved-character safe-carrier spectator switch failed account={}", player.getUUID(), failure);
            }
        }
        com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "Saved-character reconnect deferred phase={} account={} actualMode={} dimension={} position=({}, {}, {})",
                phase, player.getUUID(), player.gameMode.getGameModeForPlayer(),
                player.level().dimension().location(), player.getX(), player.getY(), player.getZ());
        player.sendSystemMessage(Component.literal("Character reconnect is waiting for safe proof of your saved body. Your saved character and items are unchanged. "
                + (spectator ? "You remain in safe spectator mode; use /ms14_return to retry."
                        : "Safe spectator switch could not be verified; character control was not granted. Retry /ms14_return.")));
    }

    private static void rememberWaiting(MinecraftServer server, ServerPlayer player,
                                        SavedLifecycleProfile saved, boolean creativeParkDenial) {
        if (server == null || player == null) return;
        Map<UUID, Waiting> waiting = WAITING.computeIfAbsent(server, ignored -> new HashMap<>());
        Waiting previous = waiting.get(player.getUUID());
        if (previous != null && previous.player() == player) {
            waiting.put(player.getUUID(), new Waiting(player, saved != null ? saved : previous.saved(),
                    previous.originalMode(), creativeParkDenial || previous.readOnlyCreativeParkDenial()));
        } else waiting.put(player.getUUID(), new Waiting(player, saved, player.gameMode.getGameModeForPlayer(), creativeParkDenial));
    }

    static boolean safeWaitAdmission(Supplier<Boolean> switchSpectator, Supplier<GameType> actualMode) {
        try {
            switchSpectator.get();
            return actualMode.get() == GameType.SPECTATOR;
        } catch (RuntimeException | Error failure) { return false; }
    }

    static boolean isWaiting(MinecraftServer server, ServerPlayer player) {
        Waiting waiting = server == null || player == null ? null : WAITING.getOrDefault(server, Map.of()).get(player.getUUID());
        return waiting != null && waiting.player() == player;
    }

    static boolean waitingCreativeRepairEvidence(MinecraftServer server, ServerPlayer player,
                                                 SavedLifecycleProfile saved) {
        Waiting waiting = server == null || player == null ? null : WAITING.getOrDefault(server, Map.of()).get(player.getUUID());
        return waiting != null && waiting.player() == player && waiting.saved() != null
                && waiting.saved().equals(saved) && waiting.originalMode() == GameType.CREATIVE
                && waiting.readOnlyCreativeParkDenial();
    }

    static boolean creativeRepairEligible(GameType originalMode, boolean readOnlyParkDenial,
                                          boolean unchangedOfflineClaim, boolean noConflictingSession) {
        return originalMode == GameType.CREATIVE && readOnlyParkDenial && unchangedOfflineClaim && noConflictingSession;
    }

    static void retryWaiting(ServerPlayer player, MinecraftServer server) {
        if (!isWaiting(server, player) || !server.isSameThread() || player.connection == null
                || !player.connection.isAcceptingMessages() || player.isRemoved()
                || server.getPlayerList().getPlayer(player.getUUID()) != player) return;
        Waiting waiting = WAITING.getOrDefault(server, Map.of()).get(player.getUUID());
        if (waiting != null && waiting.player() == player
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR
                && !player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())
                && !CarrierHandInventoryGate.allows(player)) {
            // The original park attempt was read-only denied (for example, unsupported Ender/cursor
            // contents). Only that exact admission path may enter the original Creative mode to repair.
            LifecycleServerContext repairContext = LifecycleStartupRuntime.contextFor(server).orElse(null);
            boolean unchanged = false, noConflict = false;
            if (repairContext != null && waiting.saved() != null) try {
                unchanged = waiting.saved().equals(repairContext.currentAccountProfile(player.getUUID()).orElse(null));
                var memory = repairContext.lifecycle().profile(player.getUUID()).orElse(null);
                noConflict = (memory == null || !memory.active()) && !LifecycleCharacterSessionControl.ownsAny(player)
                        && !LifecycleGhostSessionControl.ownsAny(player)
                        && !GhostMobHarnessControl.ownsDebugSession(player);
            } catch (IOException | RuntimeException ignored) { }
            if (creativeRepairEligible(waiting.originalMode(), waiting.readOnlyCreativeParkDenial(), unchanged, noConflict)
                    && LifecycleDevelopmentMode.enterWaitingCreativeForRepair(player, server, waiting.saved())) {
                player.sendSystemMessage(Component.literal("Your items were left untouched. Repair the unsupported carrier compartments, then run /ms14_return again."));
                return;
            }
        }
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) { player.sendSystemMessage(Component.literal("Reconnect is still waiting for lifecycle recovery; retry shortly.")); return; }
        handle(player, server, context, reason -> reportDeferredAdmission(player, reason));
    }

    /** Same-connection return loads only the saved chunk, without parking Creative until body proof. */
    static ReturnResult returnDevelopmentDetached(ServerPlayer player, MinecraftServer server) {
        if (server == null || player == null || !server.isSameThread()
                || !LifecycleDevelopmentMode.isTracked(server, player)
                || player.connection == null || !player.connection.isAcceptingMessages()
                || player.isRemoved() || player.getServer() != server
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) return ReturnResult.BLOCKED;
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return ReturnResult.BLOCKED;
        SavedLifecycleProfile saved;
        try {
            saved = context.primaryStore().withCurrentPrimary(lease -> {
                var rows = lease.envelope().profiles().stream()
                        .filter(row -> row.accountId().equals(player.getUUID())).toList();
                return rows.size() == 1 && rows.get(0).state() == SavedLifecycleProfile.State.OFFLINE
                        ? rows.get(0) : null;
            }).orElse(null);
        } catch (IOException | RuntimeException failure) { return ReturnResult.BLOCKED; }
        if (saved == null || !verifiedReturnClaim(server, player, saved)) return ReturnResult.BLOCKED;
        ServerLevel bodyLevel = recordedLevel(server, saved);
        Integer cx = MinecraftLoadedBodyAdapter.savedChunkCoordinate(saved.location().x());
        Integer cz = MinecraftLoadedBodyAdapter.savedChunkCoordinate(saved.location().z());
        if (bodyLevel == null || cx == null || cz == null || !Double.isFinite(saved.location().y())
                || Math.abs(saved.location().y()) > 30_000_000 || saved.offlineSinceMillis() == null)
            return ReturnResult.BLOCKED;
        Map<UUID, ReturnWait> waits = RETURN_WAITS.computeIfAbsent(server, ignored -> new HashMap<>());
        ReturnWait wait = waits.get(player.getUUID());
        boolean expired = wait != null && server.getTickCount() > wait.deadline();
        if (wait != null && (wait.player() != player || !wait.saved().equals(saved))) {
            removeReturnWait(server, player.getUUID(), wait);
            return ReturnResult.BLOCKED;
        }
        var observed = MinecraftLoadedBodyAdapter.observe(server, saved);
        if (expired && observed.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) {
            removeReturnWait(server, player.getUUID(), wait);
            return ReturnResult.BLOCKED;
        }
        if (observed.outcome() == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY && wait == null) {
            try {
                // One exact persisted chunk only. No neighboring search or replacement body.
                bodyLevel.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
            } catch (RuntimeException | Error failure) { return ReturnResult.BLOCKED; }
            observed = MinecraftLoadedBodyAdapter.observe(server, saved);
            if (returnObservation(observed.outcome(), bodyVisibleInAnyLevel(server, saved.bodyId()), true)
                    == ReturnResult.CHUNK_LOADING) {
                ChunkPos exactChunk = new ChunkPos(cx, cz);
                UUID ticketKey = player.getUUID();
                try { retainReturnChunk(bodyLevel, exactChunk, ticketKey); }
                catch (RuntimeException | Error failure) { return ReturnResult.BLOCKED; }
                ReturnWait pending = new ReturnWait(player, saved,
                        server.getTickCount() + MAX_RETURN_WAIT_TICKS, bodyLevel, exactChunk, ticketKey);
                ReturnWait prior = waits.put(player.getUUID(), pending);
                if (prior != null) releaseReturnTicket(prior);
                return ReturnResult.CHUNK_LOADING;
            }
        } else if (wait != null && !expired && returnObservation(observed.outcome(),
                bodyVisibleInAnyLevel(server, saved.bodyId()), true) == ReturnResult.CHUNK_LOADING)
            return ReturnResult.CHUNK_LOADING;
        if (wait != null) removeReturnWait(server, player.getUUID(), wait);
        if (observed.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
                || !(observed.candidate().entity() instanceof net.minecraft.world.entity.Mob body)) return ReturnResult.BLOCKED;
        var binding = PlayerCharacterHarnessBinder.binding(body);
        if (!bodyLevel.getChunkSource().hasChunk(cx, cz) || body.level() != bodyLevel
                || bodyLevel.getEntity(body.getUUID()) != body
                || binding == null || body instanceof PlayerCharacterHarnessEntity human && human.hasInvalidSavedBinding()
                || !binding.accountId().equals(player.getUUID()) || !binding.profileKey().equals(saved.profileKey())
                || !binding.mindId().equals(saved.mindId()) || !body.getUUID().equals(saved.bodyId())) return ReturnResult.BLOCKED;
        double oldX = player.getX(), oldY = player.getY(), oldZ = player.getZ();
        ServerLevel oldLevel = (ServerLevel) player.level();
        float oldYaw = player.getYRot(), oldPitch = player.getXRot();
        // Recheck current primary after observing the body, before moving the Creative carrier.
        try {
            if (!saved.equals(context.currentAccountProfile(player.getUUID()).orElse(null))
                    || !verifiedReturnClaim(server, player, saved)
                    || !bodyLevel.getChunkSource().hasChunk(cx, cz)
                    || MinecraftLoadedBodyAdapter.observe(server, saved).outcome()
                            != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
                    || bodyLevel.getEntity(saved.bodyId()) != body) return ReturnResult.BLOCKED;
        } catch (IOException | RuntimeException failure) { return ReturnResult.BLOCKED; }
        var handoff = LifecycleDevelopmentMode.setReturningBodySpectator(player);
        if (handoff.result() != LifecycleDevelopmentMode.ReturnSwitch.COMMITTED) {
            // Only the switch owner's explicit completed rollback can skip a second restore.
            if (handoff.result() == LifecycleDevelopmentMode.ReturnSwitch.FAILED
                    && player.connection != null && player.connection.isAcceptingMessages())
                player.connection.disconnect(Component.literal("Character return switch could not be verified. Reconnect for recovery."));
            return ReturnResult.BLOCKED;
        }
        CreativeParkedInventory expectedPark = handoff.marker();
        player.teleportTo(bodyLevel, saved.location().x(), saved.location().y(), saved.location().z(), oldYaw, oldPitch);
        if (player.level() != bodyLevel || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            restoreCreative(player, expectedPark, oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            return ReturnResult.BLOCKED;
        }
        if (!returnParkIntact(player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                expectedPark, player.getUUID(), CarrierHandInventoryGate.allows(player))) {
            player.connection.disconnect(Component.literal("Character return carrier was changed after parking. Reconnect for recovery."));
            return ReturnResult.BLOCKED;
        }
        if (!bodyLevel.getChunkSource().hasChunk(cx, cz) || bodyLevel.getEntity(saved.bodyId()) != body
                || MinecraftLoadedBodyAdapter.observe(server, saved).outcome()
                        != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) {
            restoreCreative(player, expectedPark, oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            return ReturnResult.BLOCKED;
        }
        if (body instanceof PlayerCharacterHarnessEntity humanBody) humanBody.setOfflineSinceMillis(saved.offlineSinceMillis());
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
                return ReturnResult.BLOCKED;
            }
            player.teleportTo(oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            restoreCreative(player, expectedPark, oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            return ReturnResult.BLOCKED;
        }
        if (body instanceof PlayerCharacterHarnessEntity humanBody) humanBody.clearOfflineSinceMillis();
        try {
            var start = LifecycleCharacterSessionControl.beginPrepared(player, body, context.lifecycle(), active.connectionGeneration());
            if (start != LifecycleCharacterSessionControl.StartResult.PREPARED) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                        "Reconnect controller startup rejected (result={}, bodyType={}, noAi={}, epoch={})",
                        start, body.getType(), body.isNoAi(), active.connectionGeneration());
                throw new IllegalStateException("session was not prepared: " + start);
            }
        } catch (RuntimeException | Error failure) {
            context.lifecycle().suspendActiveSessionForRecovery(player.getUUID(),
                    new com.juicyslew.moonstation14.ms14.player_body_control.MindId(saved.mindId()),
                    new MobHarnessId(saved.bodyId()), active.connectionGeneration());
            player.connection.disconnect(Component.literal("Character authority was durably reconnected but controller startup failed. Reconnect for recovery."));
            return ReturnResult.BLOCKED;
        }
        LifecycleDevelopmentMode.returned(server, player);
        player.sendSystemMessage(Component.literal("Returned to your existing character."));
        return ReturnResult.RETURNED;
    }

    /** A FULL chunk can precede vanilla draining its entity inbox; a visible mismatch is never pending. */
    static ReturnResult returnObservation(LoadedBodyResolver.Outcome outcome, boolean uuidVisible,
                                          boolean exactChunkLoadAttempted) {
        if (outcome == LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) return ReturnResult.RETURNED;
        if (exactChunkLoadAttempted && (outcome == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY
                || outcome == LoadedBodyResolver.Outcome.RECOVERY_REQUIRED && !uuidVisible))
            return ReturnResult.CHUNK_LOADING;
        return ReturnResult.BLOCKED;
    }

    static boolean returnParkIntact(CreativeParkedInventory actual, CreativeParkedInventory expected,
                                    UUID account, boolean clean) {
        return expected != null && actual == expected && account != null
                && account.equals(expected.account()) && clean;
    }

    private static void restoreCreative(ServerPlayer player, CreativeParkedInventory expected,
                                        ServerLevel oldLevel, double x, double y, double z,
                                        float yaw, float pitch) {
        if (player.connection == null || !player.connection.isAcceptingMessages()) return;
        try {
            boolean recovered = player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE
                    ? LifecycleDevelopmentMode.restoreCreativeInventory(player, expected)
                    : LifecycleDevelopmentMode.restoreDetachedCreative(player, expected);
            if (!recovered || player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.CREATIVE) {
                disconnectFailedRestore(player);
                return;
            }
            if (player.level() != oldLevel) player.teleportTo(oldLevel, x, y, z, yaw, pitch);
        } catch (RuntimeException | Error failure) {
            disconnectFailedRestore(player);
        }
    }

    private static boolean verifiedReturnClaim(MinecraftServer server, ServerPlayer player, SavedLifecycleProfile saved) {
        return LifecycleDevelopmentMode.verifiedDetachedOffline(server, player, saved)
                || LifecycleDevelopmentMode.verifiedOfflineCreative(server, player, saved);
    }

    private static void disconnectFailedRestore(ServerPlayer player) {
        if (player.connection != null && player.connection.isAcceptingMessages())
            player.connection.disconnect(Component.literal("Character return could not restore the Creative carrier safely. Reconnect for recovery."));
    }

    static void handle(ServerPlayer player, MinecraftServer server, LifecycleServerContext context,
                       Consumer<String> disconnect) {
        if (server == null || context == null || !server.isSameThread() || player == null
                || player.connection == null || !player.connection.isAcceptingMessages()
                || player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || GhostMobHarnessControl.ownsDebugSession(player)) {
            disconnect.accept("entry.authenticated-player-or-session-conflict");
            return;
        }
        SavedLifecycleProfile loadedSaved;
        try {
            loadedSaved = context.primaryStore().withCurrentPrimary(lease -> {
                var matches = lease.envelope().profiles().stream()
                        .filter(row -> row.accountId().equals(player.getUUID())).toList();
                return matches.size() == 1 && (matches.get(0).state() == SavedLifecycleProfile.State.OFFLINE
                        || matches.get(0).state() == SavedLifecycleProfile.State.ACTIVE)
                        ? matches.get(0) : null;
            }).orElse(null);
        } catch (IOException | RuntimeException failure) {
            disconnect.accept("primary.read-current-row-exception=" + failure.getClass().getSimpleName());
            return;
        }
        if (loadedSaved == null) { disconnect.accept("primary.no-unique-offline-or-active-row"); return; }
        rememberWaiting(server, player, loadedSaved, false);
        SavedLifecycleProfile resolvedSaved = loadedSaved;
        if (resolvedSaved.state() == SavedLifecycleProfile.State.ACTIVE) {
            // This is not a reason-based bypass: only the unique loaded exact body may establish
            // that the previous controller was abandoned while retaining its original Mind.
            var abandoned = MinecraftLoadedBodyAdapter.observeAbandonedActive(server, resolvedSaved);
            if (abandoned.outcome() == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY) {
                ServerLevel savedLevel = recordedLevel(server, resolvedSaved);
                Integer cx = MinecraftLoadedBodyAdapter.savedChunkCoordinate(resolvedSaved.location().x());
                Integer cz = MinecraftLoadedBodyAdapter.savedChunkCoordinate(resolvedSaved.location().z());
                if (savedLevel != null && cx != null && cz != null) try {
                    // Availability hint only; the subsequent observer still proves live identity/ownership.
                    savedLevel.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
                    abandoned = MinecraftLoadedBodyAdapter.observeAbandonedActive(server, resolvedSaved);
                } catch (RuntimeException | Error failure) {
                    logReconnectFailure("active-exact-chunk-load", player, "exception=" + failure.getClass().getSimpleName());
                }
            }
            if (abandoned.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
                    || !(abandoned.candidate().entity() instanceof PlayerCharacterHarnessEntity abandonedBody)
                    || !abandonedBody.isNoAi() || abandonedBody.hasInvalidSavedBinding()
                    || abandonedBody.playerCharacterBinding() == null) {
                logReconnectFailure("active-body-proof", player, "outcome=" + abandoned.outcome());
                disconnect.accept("active.exact-body-binding-noai-outcome=" + abandoned.outcome()); return;
            }
            var actorPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                    .currentActorPoint(abandonedBody.level(), abandonedBody);
            if (actorPoint == null || !Double.isFinite(abandonedBody.getX())
                    || !Double.isFinite(abandonedBody.getY()) || !Double.isFinite(abandonedBody.getZ())) {
                disconnect.accept("active.actual-pose-frame-proof"); return;
            }
            try {
                resolvedSaved = context.parkAbandonedActive(resolvedSaved,
                        new SavedLifecycleProfile.Location(abandonedBody.getX(), abandonedBody.getY(), abandonedBody.getZ()),
                        abandonedBody.level().dimension().location().toString(), Math.max(0L, System.currentTimeMillis()),
                        new MobHarness(new MobHarnessId(abandonedBody.getUUID()), MobHarnessKind.CHARACTER),
                        harness -> harness.id().value().equals(abandonedBody.getUUID())
                                && harness.kind() == MobHarnessKind.CHARACTER).orElse(null);
            } catch (IOException | RuntimeException failure) { resolvedSaved = null; }
            if (resolvedSaved == null) { disconnect.accept("active.park-abandoned-cas-or-ownership"); return; }
        }
        final SavedLifecycleProfile saved = resolvedSaved;
        if (hasPending(server, player.getUUID())) { disconnect.accept("pending.duplicate-owner"); return; }
        CreativeCarrierTransition.Attempt[] attempt = {null};
        Admission admission = admitCarrier(player.gameMode.getGameModeForPlayer(),
                player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                () -> {
                    attempt[0] = CreativeCarrierTransition.parkAttempt(player);
                    return attempt[0].result();
                }, () -> CarrierHandInventoryGate.allows(player));
        if (admission == Admission.DENIED) {
            GameType observedMode = player.gameMode.getGameModeForPlayer();
            boolean markerPresent = player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
            boolean forceGameMode = server instanceof net.minecraft.server.dedicated.DedicatedServer dedicated
                    && dedicated.getProperties().forceGameMode;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                    "OFFLINE carrier admission denied: observedMode={}, markerPresent={}, forceGamemode={}, reason={}, noWriteDenial={}",
                    observedMode, markerPresent, forceGameMode,
                    attempt[0] == null ? CreativeCarrierSnapshotProbe.reason(player) : attempt[0].reason(),
                    attempt[0] != null && attempt[0].noWriteDenial());
            if (offlineCreativeRefusalEligible(admission, attempt[0], observedMode, markerPresent)) {
                CreativeCarrierSnapshotProbe.Reason reason = attempt[0].reason();
                if (offlineCreativeAdmission(server, player, context, saved)) {
                    player.sendSystemMessage(Component.literal("Creative inventory was not parked (" + reason
                            + "). Your items remain unchanged. Resolve the unsupported compartment, then retry /ms14dev return."));
                } else reportDeferredAdmission(player, "carrier.park-readonly-denied", saved,
                        offlineCreativeRefusalEligible(admission, attempt[0], observedMode, markerPresent));
            } else reportDeferredAdmission(player, "carrier.admission-denied", saved,
                    offlineCreativeRefusalEligible(admission, attempt[0], observedMode, markerPresent));
            return;
        }
        if (admission == Admission.RECOVERY) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                    "OFFLINE carrier admission requires recovery: observedMode={}, markerPresent={}, forceGamemode={}, reason={}",
                    player.gameMode.getGameModeForPlayer(),
                    player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                    server instanceof net.minecraft.server.dedicated.DedicatedServer dedicated
                            && dedicated.getProperties().forceGameMode,
                    attempt[0] == null ? CreativeCarrierSnapshotProbe.reason(player) : attempt[0].reason());
            disconnect.accept("carrier.park-transaction-recovery-required reason="
                    + (attempt[0] == null ? CreativeCarrierSnapshotProbe.reason(player) : attempt[0].reason())); return;
        }
        boolean newlyParked = admission == Admission.NEWLY_PARKED;
        Consumer<String> beforeActivation = reason -> {
            if (newlyParked && player.connection != null && player.connection.isAcceptingMessages()
                    && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                    && CarrierHandInventoryGate.allows(player)) {
                try {
                    var current = context.currentAccountProfile(player.getUUID()).orElse(null);
                    var memory = context.lifecycle().profile(player.getUUID()).orElse(null);
                    if (saved.equals(current) && (memory == null || !memory.active())
                            && CreativeCarrierTransition.restore(player) == CreativeCarrierTransition.Result.RESTORED)
                        LifecycleDevelopmentMode.syncCarrier(player);
                } catch (IOException | RuntimeException failure) {
                    // Keep the marker in playerdata rather than restoring against ambiguous authority.
                }
            }
            disconnect.accept(reason);
        };
        if (newlyParked) {
            try { LifecycleDevelopmentMode.syncCarrier(player); }
            catch (RuntimeException | Error failure) { beforeActivation.accept(RECOVERY); return; }
        }
        LoadedBodyResolver.Resolution observed = MinecraftLoadedBodyAdapter.observe(server, saved);
        if (observed.outcome() == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY) {
            ServerLevel recorded = recordedLevel(server, saved);
            Integer cx = MinecraftLoadedBodyAdapter.savedChunkCoordinate(saved.location().x());
            Integer cz = MinecraftLoadedBodyAdapter.savedChunkCoordinate(saved.location().z());
            if (recorded == null || cx == null || cz == null) {
                beforeActivation.accept(DEFER);
                return;
            }
            try {
                // One exact persisted chunk only. Never search, create a replacement, or walk neighboring chunks.
                recorded.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
            } catch (RuntimeException | Error failure) {
                beforeActivation.accept(DEFER);
                return;
            }
            observed = MinecraftLoadedBodyAdapter.observe(server, saved);
            if (observed.outcome() == LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) {
                complete(server, player, context, saved, observed, beforeActivation, newlyParked);
                return;
            }
            // FULL can return before vanilla drains its entity-loading inbox. Permit only an
            // exact-UUID absence after this one forced load; any visible bad evidence fails closed.
            if (observed.outcome() == LoadedBodyResolver.Outcome.RECOVERY_REQUIRED
                    && !bodyVisibleInAnyLevel(server, saved.bodyId())) {
                startPending(server, player, context, saved, beforeActivation, newlyParked);
                return;
            }
        }
        if (observed.outcome() == LoadedBodyResolver.Outcome.DEFER_KNOWN_BODY) {
            startPending(server, player, context, saved, beforeActivation, newlyParked);
            return;
        }
        complete(server, player, context, saved, observed, beforeActivation, newlyParked);
    }

    private static boolean offlineCreativeAdmission(MinecraftServer server, ServerPlayer player,
                                                     LifecycleServerContext context, SavedLifecycleProfile saved) {
        if (player instanceof FakePlayer || !player.hasPermissions(2) || hasPending(server, player.getUUID())
                || LifecycleDevelopmentMode.isTracked(server, player)
                || LifecycleGhostSessionControl.ownsAny(player) || LifecycleCharacterSessionControl.ownsAny(player)
                || GhostMobHarnessControl.ownsDebugSession(player) || !retryOwnerValid(server, player, context, saved))
            return false;
        var memory = context.lifecycle().profile(player.getUUID()).orElse(null);
        return LifecycleDevelopmentMode.verifiedOfflineCreativeRow(saved, saved, player.getUUID(), memory)
                && LifecycleDevelopmentMode.registerOfflineCreative(server, player, saved);
    }

    /** Policy fixture: does not claim a connected operator or authorize registration. */
    static boolean offlineCreativeRefusalEligible(Admission admission, CreativeCarrierTransition.Attempt attempt,
                                                   GameType mode, boolean markerPresent) {
        return admission == Admission.DENIED && attempt != null
                && attempt.result() == CreativeCarrierTransition.Result.DENIED && attempt.noWriteDenial()
                && mode == GameType.CREATIVE && !markerPresent;
    }

    enum Admission { DENIED, RECOVERY, READY, NEWLY_PARKED }

    /** Production supplies the exact connected player's mode, marker, and guarded transition. */
    static Admission admitCarrier(GameType mode, boolean markerPresent,
                                  Supplier<CreativeCarrierTransition.Result> park, BooleanSupplier clean) {
        try {
            if (mode == GameType.CREATIVE && !markerPresent) {
                CreativeCarrierTransition.Result result = park.get();
                if (result == CreativeCarrierTransition.Result.RECOVERY_REQUIRED) return Admission.RECOVERY;
                if (result != CreativeCarrierTransition.Result.PARKED) return Admission.DENIED;
                return clean.getAsBoolean() ? Admission.NEWLY_PARKED : Admission.RECOVERY;
            }
            return clean.getAsBoolean() ? Admission.READY : Admission.DENIED;
        } catch (RuntimeException | Error failure) { return Admission.RECOVERY; }
    }

    private static void startPending(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                     SavedLifecycleProfile saved, Consumer<String> disconnect, boolean newlyParked) {
        if (!CarrierHandInventoryGate.allows(player) || !makeCarrierInert(player, newlyParked)
                || !retryOwnerValid(server, player, context, saved)) {
            disconnect.accept(!CarrierHandInventoryGate.allows(player) ? "carrier.inert-inventory-gate"
                    : !retryOwnerValid(server, player, context, saved) ? "owner.current-primary-or-session-mismatch"
                    : "carrier.spectator-transition");
            return;
        }
        putPending(server, player, context, saved, disconnect, newlyParked);
    }

    private static boolean bodyVisibleInAnyLevel(MinecraftServer server, UUID bodyId) {
        for (ServerLevel level : server.getAllLevels())
            if (level.getEntity(bodyId) != null) return true;
        return false;
    }

    private static void complete(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                  SavedLifecycleProfile saved, LoadedBodyResolver.Resolution observed,
                                  Consumer<String> disconnect, boolean newlyParked) {
        if (observed.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
                || !(observed.candidate().entity() instanceof PlayerCharacterHarnessEntity body)
                || !body.isNoAi()) {
            disconnect.accept("body.proof-or-noai outcome=" + observed.outcome()
                    + " entity=" + (observed.candidate() == null ? "none" : observed.candidate().entity() == null
                    ? "null" : observed.candidate().entity().getClass().getSimpleName())
                    + " noAi=" + (observed.candidate() != null
                    && observed.candidate().entity() instanceof PlayerCharacterHarnessEntity candidate && candidate.isNoAi()));
            return;
        }
        ServerLevel bodyLevel = recordedLevel(server, saved);
        if (bodyLevel == null || body.level() != bodyLevel || bodyLevel.getEntity(body.getUUID()) != body) {
            disconnect.accept("body.recorded-level-or-exact-entity-mismatch");
            return;
        }
        var binding = PlayerCharacterHarnessBinder.binding(body);
        if (binding == null || body instanceof PlayerCharacterHarnessEntity human && human.hasInvalidSavedBinding()
                || !binding.accountId().equals(saved.accountId())
                || !binding.profileKey().equals(saved.profileKey())
                || !binding.mindId().equals(saved.mindId())
                || !body.getUUID().equals(saved.bodyId()) || saved.offlineSinceMillis() == null) {
            disconnect.accept("body.binding-or-offline-timestamp-mismatch");
            return;
        }
        if (!CarrierHandInventoryGate.allows(player)) { disconnect.accept("carrier.inventory-gate-before-inert"); return; }
        // Durable OFFLINE data is authoritative over a stale/missing entity timestamp after restart.
        if (body instanceof PlayerCharacterHarnessEntity humanBody) humanBody.setOfflineSinceMillis(saved.offlineSinceMillis());
        // Place the carrier using the saved dimension as authority before any durable activation.
        if (!makeCarrierInert(player, newlyParked)) {
            disconnect.accept("carrier.spectator-transition-or-inventory-gate");
            return;
        }
        double x = saved.location().x(), y = saved.location().y(), z = saved.location().z();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            disconnect.accept("carrier.teleport-level-or-connection-check");
            return;
        }
        player.teleportTo(bodyLevel, x, y, z, player.getYRot(), player.getXRot());
        if (player.level() != bodyLevel || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            disconnect.accept(RECOVERY);
            return;
        }
        if (!CarrierHandInventoryGate.allows(player)) { disconnect.accept("carrier.inventory-gate-before-cas"); return; }
        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.Snapshot active;
        try {
            active = context.reconnectOffline(saved,
                    new MobHarness(new MobHarnessId(body.getUUID()), MobHarnessKind.CHARACTER)).orElse(null);
        } catch (IOException | RuntimeException failure) {
            active = null;
        }
        if (active == null || active.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE
                || !active.active() || active.connectionGeneration() <= saved.connectionGeneration()) {
            disconnect.accept("durable.offline-reconnect-cas-or-generation-check");
            return;
        }
        // The marker remains if the durable CAS failed or returned ambiguous evidence.
        if (body instanceof PlayerCharacterHarnessEntity humanBody) humanBody.clearOfflineSinceMillis();
        try {
            var result = LifecycleCharacterSessionControl.beginPrepared(player, body, context.lifecycle(), active.connectionGeneration());
            if (result != LifecycleCharacterSessionControl.StartResult.PREPARED) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                        "Reconnect controller startup rejected (result={}, bodyType={}, noAi={}, epoch={})",
                        result, body.getType(), body.isNoAi(), active.connectionGeneration());
                throw new IllegalStateException("session was not prepared: " + result);
            }
        } catch (RuntimeException | Error failure) {
            logReconnectFailure("controller-start", player, "exception=" + failure.getClass().getSimpleName());
            context.lifecycle().suspendActiveSessionForRecovery(player.getUUID(),
                    new com.juicyslew.moonstation14.ms14.player_body_control.MindId(saved.mindId()),
                    new MobHarnessId(saved.bodyId()), active.connectionGeneration());
            disconnect.accept("session.begin-prepared-failed");
        }
        clearWaiting(server, player);
    }

    private static void logReconnectFailure(String phase, ServerPlayer player, String detail) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "Saved-character reconnect failed phase={} account={} mode={} detail={}",
                phase, player == null ? null : player.getUUID(),
                player == null ? null : player.gameMode.getGameModeForPlayer(), detail);
    }

    private static void clearWaiting(MinecraftServer server, ServerPlayer player) {
        Map<UUID, Waiting> waiting = WAITING.get(server);
        if (waiting != null) {
            Waiting current = waiting.get(player.getUUID());
            if (current != null && current.player() == player) waiting.remove(player.getUUID());
            if (waiting.isEmpty()) WAITING.remove(server);
        }
    }

    private static boolean makeCarrierInert(ServerPlayer player, boolean newlyParked) {
        return inertPolicy(newlyParked, () -> player.setGameMode(GameType.SPECTATOR),
                () -> player.gameMode.getGameModeForPlayer(), () -> CarrierHandInventoryGate.allows(player),
                () -> CreativeCarrierTransition.restore(player), () -> LifecycleDevelopmentMode.syncCarrier(player));
    }

    static boolean inertPolicy(boolean newlyParked, BooleanSupplier switchMode, Supplier<GameType> actual,
                               BooleanSupplier clean, Supplier<CreativeCarrierTransition.Result> restore,
                               Runnable sync) {
        try {
            switchMode.getAsBoolean();
            GameType mode = actual.get();
            // setGameMode returns false when already in the requested mode. An exact, clean
            // Spectator carrier is therefore a valid idempotent no-op, including safe retries.
            if (mode == GameType.SPECTATOR && clean.getAsBoolean()) return true;
            if (newlyParked && mode == GameType.CREATIVE && clean.getAsBoolean()
                    && restore.get() == CreativeCarrierTransition.Result.RESTORED) sync.run();
        } catch (RuntimeException | Error failure) {
            // An exception may have changed the mode; caller disconnects and retains the marker.
        }
        return false;
    }

    private static boolean retryOwnerValid(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                           SavedLifecycleProfile saved) {
        if (server == null || context == null || !server.isSameThread() || player == null
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
                                    SavedLifecycleProfile saved, Consumer<String> disconnect, boolean newlyParked) {
        PENDING.computeIfAbsent(server, ignored -> new HashMap<>())
                .put(player.getUUID(), new Pending(player, context, saved, disconnect, newlyParked, 0));
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, ReturnWait> returnWaits = RETURN_WAITS.get(server);
        if (returnWaits != null) {
            for (var item : returnWaits.entrySet().toArray(Map.Entry[]::new)) {
                @SuppressWarnings("unchecked") Map.Entry<UUID, ReturnWait> entry = (Map.Entry<UUID, ReturnWait>) item;
                ReturnWait wait = entry.getValue();
                if (returnWaits.get(entry.getKey()) != wait) continue;
                ServerPlayer player = wait.player();
                advanceReturnWait(server.getTickCount(), wait.deadline(),
                        () -> waitOwnerValid(server, player, wait.saved()),
                        () -> {
                            var observation = MinecraftLoadedBodyAdapter.observe(server, wait.saved());
                            return new ReturnTickObservation(observation.outcome(),
                                    bodyVisibleInAnyLevel(server, wait.saved().bodyId()));
                        },
                        () -> resumeReturnWait(player, server),
                        () -> removeReturnWait(server, entry.getKey(), wait),
                        () -> player.sendSystemMessage(Component.literal(
                                "The exact saved character body could not be proven safe. You remain in Creative; inspect recovery or reconnect.")));
            }
            if (returnWaits.isEmpty()) RETURN_WAITS.remove(server);
        }
        Map<UUID, Pending> entries = PENDING.get(server);
        if (entries == null) return;
        for (var item : entries.entrySet().toArray(Map.Entry[]::new)) {
            @SuppressWarnings("unchecked") Map.Entry<UUID, Pending> entry = (Map.Entry<UUID, Pending>) item;
            Pending pending = entry.getValue();
            if (entries.get(entry.getKey()) != pending) continue;
            boolean ownerValid = retryOwnerValid(server, pending.player, pending.context, pending.saved);
            if (!ownerValid) {
                removePending(server, entry.getKey(), pending);
                pending.disconnect.accept("pending.owner-current-primary-or-session-mismatch");
                continue;
            }
            LoadedBodyResolver.Resolution observed = MinecraftLoadedBodyAdapter.observe(server, pending.saved);
            if (observed.outcome() == LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) {
                removePending(server, entry.getKey(), pending);
                complete(server, pending.player, pending.context, pending.saved, observed,
                        pending.disconnect, pending.newlyParked);
            } else {
                boolean inboxAbsence = observed.outcome() == LoadedBodyResolver.Outcome.RECOVERY_REQUIRED
                        && !bodyVisibleInAnyLevel(server, pending.saved.bodyId());
                PendingDecision decision = pendingDecision(pending.elapsedTicks + 1, observed.outcome(), true,
                        inboxAbsence);
                if (decision == PendingDecision.FAIL_CLOSED) {
                    removePending(server, entry.getKey(), pending);
                    pending.disconnect.accept("pending.visible-body-conflict outcome=" + observed.outcome());
                } else if (decision == PendingDecision.TIMEOUT) {
                    removePending(server, entry.getKey(), pending);
                    pending.disconnect.accept("pending.exact-body-not-available-after-ticks=" + pending.elapsedTicks);
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
        if (server != null) {
            removePending(server, player.getUUID(), null);
            Map<UUID, Waiting> waiting = WAITING.get(server);
            if (waiting != null) {
                Waiting current = waiting.get(player.getUUID());
                if (current != null && current.player() == player) waiting.remove(player.getUUID());
                if (waiting.isEmpty()) WAITING.remove(server);
            }
            Map<UUID, ReturnWait> waits = RETURN_WAITS.get(server);
            if (waits != null) {
                ReturnWait wait = waits.get(player.getUUID());
                if (wait != null && wait.player() == player) removeReturnWait(server, player.getUUID(), wait);
                if (waits.isEmpty()) RETURN_WAITS.remove(server);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStopped(ServerStoppedEvent event) {
        PENDING.remove(event.getServer());
        Map<UUID, ReturnWait> waits = RETURN_WAITS.remove(event.getServer());
        if (waits != null) waits.values().forEach(LifecycleExistingBodyReconnect::releaseReturnTicket);
        WAITING.remove(event.getServer());
    }

    record ReturnTickObservation(LoadedBodyResolver.Outcome outcome, boolean uuidVisible) { }

    private static void resumeReturnWait(ServerPlayer player, MinecraftServer server) {
        if (returnDevelopmentDetached(player, server) == ReturnResult.BLOCKED
                && player.connection != null && player.connection.isAcceptingMessages())
            player.sendSystemMessage(Component.literal(
                    "The exact saved character body could not be proven safe. You remain in Creative; inspect recovery or reconnect."));
    }

    static void advanceReturnWait(int tick, int deadline, BooleanSupplier ownerValid,
                                  Supplier<ReturnTickObservation> observe, Runnable resume,
                                  Runnable cancel, Runnable timeout) {
        if (!ownerValid.getAsBoolean()) { cancel.run(); return; }
        ReturnTickObservation current = observe.get();
        if (current.outcome() == LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) { resume.run(); return; }
        if (returnObservation(current.outcome(), current.uuidVisible(), true) == ReturnResult.BLOCKED) {
            cancel.run(); timeout.run(); return;
        }
        if (tick > deadline) { cancel.run(); timeout.run(); }
    }

    private static boolean waitOwnerValid(MinecraftServer server, ServerPlayer player, SavedLifecycleProfile saved) {
        if (!(player != null && player.connection != null && player.connection.isAcceptingMessages()
                && !player.isRemoved() && player.getServer() == server
                && server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                && LifecycleDevelopmentMode.isTracked(server, player)
                && LifecycleStartupRuntime.contextFor(server).isPresent())) return false;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        try {
            return context != null && saved.equals(context.currentAccountProfile(player.getUUID()).orElse(null))
                    && verifiedReturnClaim(server, player, saved);
        } catch (IOException | RuntimeException failure) { return false; }
    }

    private static void removeReturnWait(MinecraftServer server, UUID account, ReturnWait expected) {
        Map<UUID, ReturnWait> waits = RETURN_WAITS.get(server);
        if (waits == null) return;
        if (waits.remove(account, expected)) releaseReturnTicket(expected);
        if (waits.isEmpty()) RETURN_WAITS.remove(server);
    }

    private static void releaseReturnTicket(ReturnWait wait) {
        releaseReturnChunk(wait.level(), wait.chunk(), wait.ticketKey());
    }

    static void retainReturnChunk(ServerLevel level, ChunkPos chunk, UUID key) {
        level.getChunkSource().addRegionTicket(RETURN_TICKET, chunk, RETURN_TICKET_RADIUS, key);
    }

    static void releaseReturnChunk(ServerLevel level, ChunkPos chunk, UUID key) {
        level.getChunkSource().removeRegionTicket(RETURN_TICKET, chunk, RETURN_TICKET_RADIUS, key);
    }

    private static void removePending(MinecraftServer server, UUID account, Pending expected) {
        Map<UUID, Pending> entries = PENDING.get(server);
        if (entries == null) return;
        if (expected == null) entries.remove(account); else entries.remove(account, expected);
        if (entries.isEmpty()) PENDING.remove(server);
    }

    private record Pending(ServerPlayer player, LifecycleServerContext context, SavedLifecycleProfile saved,
                           Consumer<String> disconnect, boolean newlyParked, int elapsedTicks) {
        private Pending tick() { return new Pending(player, context, saved, disconnect, newlyParked, elapsedTicks + 1); }
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
