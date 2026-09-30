package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbe;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.juicyslew.moonstation14.component.ModDataAttachments;
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
    enum ReturnResult { RETURNED, CHUNK_LOADING, BLOCKED }
    private record ReturnWait(ServerPlayer player, SavedLifecycleProfile saved, int deadline) { }

    private LifecycleExistingBodyReconnect() { }

    /** Same-connection return loads only the saved chunk, without parking Creative until body proof. */
    static ReturnResult returnDevelopmentDetached(ServerPlayer player, MinecraftServer server) {
        if (!MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                || server == null || player == null || !server.isSameThread()
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
            waits.remove(player.getUUID());
            return ReturnResult.BLOCKED;
        }
        var observed = MinecraftLoadedBodyAdapter.observe(server, saved);
        if (expired && observed.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE) {
            waits.remove(player.getUUID());
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
                waits.put(player.getUUID(), new ReturnWait(player, saved,
                        server.getTickCount() + MAX_RETURN_WAIT_TICKS));
                return ReturnResult.CHUNK_LOADING;
            }
        } else if (wait != null && !expired && returnObservation(observed.outcome(),
                bodyVisibleInAnyLevel(server, saved.bodyId()), true) == ReturnResult.CHUNK_LOADING)
            return ReturnResult.CHUNK_LOADING;
        waits.remove(player.getUUID());
        if (observed.outcome() != LoadedBodyResolver.Outcome.SAME_BODY_AVAILABLE
                || !(observed.candidate().entity() instanceof PlayerCharacterHarnessEntity body)
                || !body.isNoAi()) return ReturnResult.BLOCKED;
        var binding = body.playerCharacterBinding();
        if (!bodyLevel.getChunkSource().hasChunk(cx, cz) || body.level() != bodyLevel
                || bodyLevel.getEntity(body.getUUID()) != body
                || binding == null || body.hasInvalidSavedBinding()
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
                return ReturnResult.BLOCKED;
            }
            player.teleportTo(oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            restoreCreative(player, expectedPark, oldLevel, oldX, oldY, oldZ, oldYaw, oldPitch);
            return ReturnResult.BLOCKED;
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
                } else disconnect.accept(DIRTY_CARRIER);
            } else disconnect.accept(DIRTY_CARRIER);
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
            disconnect.accept(RECOVERY); return;
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
            disconnect.accept(RECOVERY);
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
        if (!CarrierHandInventoryGate.allows(player)) { disconnect.accept(DIRTY_CARRIER); return; }
        // Durable OFFLINE data is authoritative over a stale/missing entity timestamp after restart.
        body.setOfflineSinceMillis(saved.offlineSinceMillis());
        // Place the carrier using the saved dimension as authority before any durable activation.
        if (!makeCarrierInert(player, newlyParked)) {
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
        if (!CarrierHandInventoryGate.allows(player)) { disconnect.accept(DIRTY_CARRIER); return; }
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

    private static boolean makeCarrierInert(ServerPlayer player, boolean newlyParked) {
        return inertPolicy(newlyParked, () -> player.setGameMode(GameType.SPECTATOR),
                () -> player.gameMode.getGameModeForPlayer(), () -> CarrierHandInventoryGate.allows(player),
                () -> CreativeCarrierTransition.restore(player), () -> LifecycleDevelopmentMode.syncCarrier(player));
    }

    static boolean inertPolicy(boolean newlyParked, BooleanSupplier switchMode, Supplier<GameType> actual,
                               BooleanSupplier clean, Supplier<CreativeCarrierTransition.Result> restore,
                               Runnable sync) {
        try {
            boolean switched = switchMode.getAsBoolean();
            GameType mode = actual.get();
            if (switched && mode == GameType.SPECTATOR && clean.getAsBoolean()) return true;
            if (newlyParked && mode == GameType.CREATIVE && clean.getAsBoolean()
                    && restore.get() == CreativeCarrierTransition.Result.RESTORED) sync.run();
        } catch (RuntimeException | Error failure) {
            // An exception may have changed the mode; caller disconnects and retains the marker.
        }
        return false;
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
                                    SavedLifecycleProfile saved, Consumer<String> disconnect, boolean newlyParked) {
        PENDING.computeIfAbsent(server, ignored -> new HashMap<>())
                .put(player.getUUID(), new Pending(player, context, saved, disconnect, newlyParked, 0));
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
                complete(server, pending.player, pending.context, pending.saved, observed,
                        pending.disconnect, pending.newlyParked);
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
        if (server != null) {
            removePending(server, player.getUUID(), null);
            Map<UUID, ReturnWait> waits = RETURN_WAITS.get(server);
            if (waits != null) {
                ReturnWait wait = waits.get(player.getUUID());
                if (wait != null && wait.player() == player) waits.remove(player.getUUID());
                if (waits.isEmpty()) RETURN_WAITS.remove(server);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void serverStopped(ServerStoppedEvent event) {
        PENDING.remove(event.getServer());
        RETURN_WAITS.remove(event.getServer());
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
