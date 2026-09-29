package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

/** Temporary operator-only escape from a possessed lifecycle body into vanilla Creative building mode. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleDevelopmentMode {
    private static final Map<MinecraftServer, Map<UUID, ParkedBody>> DETACHED = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, ParkedGhost>> PARKED_GHOSTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> PARKING_GHOSTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> PARKING_CREATIVE = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> RETURNING_GHOSTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> RETURNING_BODIES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> RECOVERING_CREATIVE = new IdentityHashMap<>();
    private record ParkedGhost(ServerPlayer player, SavedLifecycleProfile saved) { }
    private record ParkedBody(ServerPlayer player, SavedLifecycleProfile active, SavedLifecycleProfile offline,
                              SavedLifecycleProfile retryDeath) { }
    static final String DEAD_RETURN_MESSAGE =
            "Your character has died. Use /ms14dev return to enter as a fresh ghost.";
    private LifecycleDevelopmentMode() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14dev").requires(source -> source.hasPermission(2))
                .then(Commands.literal("creative").executes(context -> parkGhost(
                        context.getSource().getPlayerOrException())))
                .then(Commands.literal("return").executes(context -> returnToCharacter(
                        context.getSource().getPlayerOrException()))));
    }

    @SubscribeEvent
    public static void beforeGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return;

        GameType requested = event.getNewGameMode();
        if (LifecycleGhostSessionControl.ownsAny(player)) {
            if (rejectRawGhostModeChange(true, requested)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal(
                        "Ghost control is active. Operators must use /ms14dev creative first."), true);
            }
            return;
        }
        if (exactTransitionOwner(PARKING_GHOSTS.get(level.getServer()), player)) {
            if (rejectParkingGhostModeChange(requested,
                    exactTransitionOwner(PARKING_CREATIVE.get(level.getServer()), player))) {
                event.setCanceled(true);
            }
            return;
        }
        if (parkedGhost(level.getServer(), player) != null) {
            if (rejectParkedGhostModeChange(requested,
                    exactTransitionOwner(RETURNING_GHOSTS.get(level.getServer()), player),
                    exactTransitionOwner(RECOVERING_CREATIVE.get(level.getServer()), player))) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal(
                        "Your ghost is parked. Use /ms14dev return or reconnect to reclaim a fresh ghost."), true);
            }
            return;
        }
        if (isTracked(level.getServer(), player)) {
            if (rejectDetachedModeChange(requested,
                    exactTransitionOwner(RETURNING_GHOSTS.get(level.getServer()), player)
                            || exactTransitionOwner(RETURNING_BODIES.get(level.getServer()), player),
                    exactTransitionOwner(RECOVERING_CREATIVE.get(level.getServer()), player))) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal(
                        "Your lifecycle character is parked. Use /ms14dev return to reclaim it."), true);
            }
            return;
        }
        if (!LifecycleCharacterSessionControl.ownsAny(player)) return;
        if (requested == GameType.CREATIVE) {
            if (!LifecycleCharacterSessionControl.developmentModeDecision(true, requested,
                    player.hasPermissions(2))) {
                event.setCanceled(true);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Only an operator may leave a lifecycle character for Creative mode."), true);
                return;
            }
            var context = LifecycleStartupRuntime.contextFor(level.getServer()).orElse(null);
            SavedLifecycleProfile active = null;
            if (context != null) try {
                var row = context.currentAccountProfile(player.getUUID()).orElse(null);
                var memory = context.lifecycle().profile(player.getUUID()).orElse(null);
                if (row != null && row.state() == SavedLifecycleProfile.State.ACTIVE && memory != null
                        && memory.active() && memory.state() == PlayerLifecycleRegistry.LifecycleState.ACTIVE
                        && memory.connectionGeneration() == row.connectionGeneration()
                        && memory.mindId().value().equals(row.mindId())
                        && memory.bodyId().value().equals(row.bodyId())) active = row;
            } catch (IOException | RuntimeException ignored) { }
            if (active == null || !LifecycleCharacterSessionControl.parkForDevelopmentMode(player)) {
                event.setCanceled(true);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Could not park body; remain in character."), true);
            } else {
                SavedLifecycleProfile offline = null;
                if (context != null) try {
                    var row = context.currentAccountProfile(player.getUUID()).orElse(null);
                    if (verifiedParkedBodyOffline(row, active, player.getUUID())) offline = row;
                } catch (IOException | RuntimeException ignored) { }
                // Retain the pre-park ACTIVE identity even when the post-park read fails.
                detached(level.getServer()).put(player.getUUID(), new ParkedBody(player, active, offline, null));
            }
            return;
        }
        if (!LifecycleCharacterSessionControl.developmentModeDecision(true, requested, player.hasPermissions(2))) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "That game mode is unsupported while controlling a lifecycle character."), true);
        }
    }

    private static int returnToCharacter(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || player instanceof FakePlayer
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || !player.hasPermissions(2)) return 0;
        MinecraftServer server = level.getServer();
        if (parkedGhost(server, player) != null) return returnToGhost(player, server);
        boolean gates = com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate.enabledForServer()
                && !com.juicyslew.moonstation14.ms14.movement.MovementStartupGate.enabledForServer();
        boolean exactTrackedCarrier = isTracked(server, player)
                && server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
        if (deadClaimReturnMessageEligible(gates, exactTrackedCarrier, player.hasPermissions(2),
                player.gameMode.getGameModeForPlayer(), currentPrimaryProfileState(server, player), true)) {
            ParkedBody parked = parkedBody(server, player);
            parked = refreshParkedBody(server, player, parked);
            var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
            SavedLifecycleProfile saved = null;
            if (context != null) try { saved = context.currentAccountProfile(player.getUUID()).orElse(null); }
            catch (IOException | RuntimeException ignored) { }
            if (parked == null || !verifiedDetachedDeathClaim(saved, parked, player.getUUID())) {
                player.sendSystemMessage(Component.literal("Death claim does not match the parked body; remain in Creative and inspect lifecycle recovery."));
                return 0;
            }
            return returnToGhost(player, server, saved, true);
        }
        if (!returnEligibilityDecision(gates, isTracked(server, player), player.hasPermissions(2),
                player.gameMode.getGameModeForPlayer(), true)) {
            player.sendSystemMessage(Component.literal("No eligible parked lifecycle character is attached to this Creative carrier."));
            return 0;
        }
        if (!LifecycleExistingBodyReconnect.returnDevelopmentDetached(player, server)) {
            player.sendSystemMessage(Component.literal("The exact saved character body is not safely available yet. You remain in Creative; retry or logout/rejoin."));
        }
        return 1;
    }

    private static int parkGhost(ServerPlayer player) {
        if (player == null || !player.hasPermissions(2) || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || parkedGhost(level.getServer(), player) != null) return 0;
        MinecraftServer server = level.getServer();
        if (!server.isSameThread() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || PARKING_GHOSTS.containsKey(server)) return 0;
        PARKING_GHOSTS.put(server, player);
        java.util.Optional<SavedLifecycleProfile> saved;
        try { saved = LifecycleGhostSessionControl.parkForDevelopmentMode(player); }
        finally {
            PARKING_CREATIVE.remove(server, player);
            PARKING_GHOSTS.remove(server, player);
        }
        if (saved.isEmpty()) {
            if (player.connection != null && player.connection.isAcceptingMessages())
                player.sendSystemMessage(Component.literal("Cannot safely park this exact committed ghost; remain in ghost control."));
            return 0;
        }
        PARKED_GHOSTS.computeIfAbsent(level.getServer(), ignored -> new HashMap<>())
                .put(player.getUUID(), new ParkedGhost(player, saved.orElseThrow()));
        player.sendSystemMessage(Component.literal("Ghost parked. Use /ms14dev return for a fresh ghost, or reconnect."));
        return 1;
    }

    static boolean rejectRawGhostModeChange(boolean exactGhostOwner, GameType requested) {
        return exactGhostOwner && requested != GameType.SPECTATOR;
    }

    static boolean rejectParkingGhostModeChange(GameType requested, boolean exactInternalPark) {
        return requested != GameType.SPECTATOR && !(requested == GameType.CREATIVE && exactInternalPark);
    }

    static boolean exactTransitionOwner(Object owner, Object player) {
        return owner != null && owner == player;
    }

    static boolean setParkingGhostCreative(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || PARKING_GHOSTS.get(server) != player
                || PARKING_CREATIVE.putIfAbsent(server, player) != null) return false;
        try { return player.setGameMode(GameType.CREATIVE); }
        finally { PARKING_CREATIVE.remove(server, player); }
    }

    static boolean rejectParkedGhostModeChange(GameType requested, boolean exactInternalReturn) {
        return rejectParkedGhostModeChange(requested, exactInternalReturn, false);
    }

    static boolean rejectParkedGhostModeChange(GameType requested, boolean exactInternalReturn, boolean exactRecovery) {
        return !(requested == GameType.CREATIVE && exactRecovery
                || requested == GameType.SPECTATOR && exactInternalReturn);
    }

    static boolean rejectDetachedModeChange(GameType requested, boolean exactInternalReturn) {
        return rejectDetachedModeChange(requested, exactInternalReturn, false);
    }

    static boolean rejectDetachedModeChange(GameType requested, boolean exactInternalReturn, boolean exactRecovery) {
        return rejectParkedGhostModeChange(requested, exactInternalReturn, exactRecovery);
    }

    static boolean setReturningBodySpectator(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || parkedBody(server, player) == null
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                || RETURNING_BODIES.putIfAbsent(server, player) != null) return false;
        try { return player.setGameMode(GameType.SPECTATOR); }
        finally { RETURNING_BODIES.remove(server, player); }
    }

    static boolean restoreDetachedCreative(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || parkedBody(server, player) == null || RECOVERING_CREATIVE.putIfAbsent(server, player) != null)
            return false;
        try { return player.setGameMode(GameType.CREATIVE); }
        finally { RECOVERING_CREATIVE.remove(server, player); }
    }

    private static int returnToGhost(ServerPlayer player, MinecraftServer server) {
        ParkedGhost parked = parkedGhost(server, player);
        return parked == null ? 0 : returnToGhost(player, server, parked.saved(), false);
    }

    private static int returnToGhost(ServerPlayer player, MinecraftServer server,
                                     SavedLifecycleProfile expected, boolean detachedBody) {
        if ((detachedBody ? parkedBody(server, player) == null : parkedGhost(server, player) == null)
                || !player.hasPermissions(2) || !server.isSameThread()
                || player.connection == null || !player.connection.isAcceptingMessages()
                || player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                || !MindGhostStartupGate.enabledForServer() || MovementStartupGate.enabledForServer()
                || LifecycleGhostSessionControl.ownsAny(player) || LifecycleCharacterSessionControl.ownsAny(player))
            return 0;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null || com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl.ownsDebugSession(player))
            return 0;
        SavedLifecycleProfile saved;
        try { saved = context.currentAccountProfile(player.getUUID()).orElse(null); }
        catch (IOException | RuntimeException failure) { saved = null; }
        if (!(detachedBody ? verifiedDetachedDeathClaim(saved, parkedBody(server, player), player.getUUID())
                : verifiedParkedGhostClaim(saved, expected, player.getUUID()))
                || saved == null || !saved.dimension().equals(player.level().dimension().location().toString())) {
            player.sendSystemMessage(Component.literal("Saved death claim cannot be verified in this dimension. Remain in Creative; retry in the saved dimension or reconnect."));
            return 0;
        }
        var memory = context.lifecycle().profile(player.getUUID()).orElse(null);
        if (memory == null || memory.active() || memory.state() != PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM
                || !memory.bodyId().value().equals(saved.bodyId())
                || !memory.mindId().value().equals(saved.mindId())
                || memory.connectionGeneration() != saved.connectionGeneration()) {
            player.sendSystemMessage(Component.literal("Saved death claim has conflicting lifecycle ownership. Remain in Creative; inspect recovery."));
            return 0;
        }

        boolean spectator;
        if (RETURNING_GHOSTS.putIfAbsent(server, player) != null) return 0;
        try { spectator = player.setGameMode(GameType.SPECTATOR)
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR; }
        catch (RuntimeException | Error failure) { spectator = false; }
        finally { RETURNING_GHOSTS.remove(server, player); }
        if (!spectator) {
            if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) disconnectGhostReturn(player);
            return 0;
        }
        try {
            // Pin the same saved claim across the staging CAS; the supplied-row overload
            // re-reads current primary and refuses a changed claim before world mutation.
            var staged = LifecycleDeadClaimGhostStager.stage(player, server, saved);
            if (staged.outcome() != LifecycleDeadClaimGhostStager.Outcome.PREPARED) {
                // A rejected preparation can leave DEAD_CLAIM intact, or reserve recovery.
                // Only restore Creative when the exact claim remains inactive and unchanged.
                recoverCreativeOrDisconnect(player, server, context, saved, false);
                return 0;
            }
            var prepared = staged.prepared();
            try {
                if (prepared.ghost().level() != player.level()
                        || LifecycleGhostSessionControl.beginPrepared(player, prepared.ghost(), context.lifecycle(),
                                prepared.generation(), new com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId(prepared.corpseUUID()))
                        != LifecycleGhostSessionControl.StartResult.PREPARED)
                    throw new IllegalStateException("ghost return startup rejected");
            } catch (RuntimeException | Error failure) {
                boolean rolledBack = false;
                try {
                    if (!LifecycleDeadClaimGhostStager.returnPreparedToDeadClaim(context, prepared)) {
                        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                "[lifecycle ghost] Prepared return rollback rejected; recovery required");
                        try { LifecycleDeadClaimGhostStager.suspendAndDiscardPrepared(context, prepared); }
                        finally { discardFailedPrepared(context, prepared); }
                    } else rolledBack = true;
                } catch (RuntimeException | Error cleanupFailure) {
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                            "[lifecycle ghost] Prepared return rollback failed; recovery required", cleanupFailure);
                    discardFailedPrepared(context, prepared);
                } finally {
                    if (rolledBack) recoverCreativeOrDisconnect(player, server, context, saved, true);
                    else disconnectGhostReturn(player);
                }
                return 0;
            }
            if (detachedBody) clearTracked(server, player);
            else {
                clearParkedGhost(server, player);
                // A parked living body may have died before the ghost was parked.
                clearTracked(server, player);
            }
            player.sendSystemMessage(Component.literal("A fresh ghost is ready from your saved death claim."));
            return 1;
        } catch (RuntimeException | Error failure) {
            recoverCreativeOrDisconnect(player, server, context, saved, false);
            return 0;
        }
    }

    private static void recoverCreativeOrDisconnect(ServerPlayer player, MinecraftServer server,
                                                       LifecycleServerContext context, SavedLifecycleProfile saved,
                                                       boolean rolledBack) {
        ParkedGhost ghost = parkedGhost(server, player);
        ParkedBody body = parkedBody(server, player);
        if ((ghost == null && body == null)
                || (ghost != null && !verifiedParkedGhostClaim(saved, ghost.saved(), player.getUUID()))
                || (ghost == null && !verifiedDetachedDeathClaim(saved, body, player.getUUID()))) {
            disconnectGhostReturn(player);
            return;
        }
        SavedLifecycleProfile verified = null;
        try {
            var current = context.currentAccountProfile(player.getUUID()).orElse(null);
            var memory = context.lifecycle().profile(player.getUUID()).orElse(null);
            if (verifiedRecoveryClaim(current, saved, memory, rolledBack)) verified = current;
        } catch (IOException | RuntimeException ignored) { }
        if (verified != null && server.getPlayerList().getPlayer(player.getUUID()) == player
                && !LifecycleGhostSessionControl.ownsAny(player)
                && !LifecycleCharacterSessionControl.ownsAny(player)) {
            try {
                if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                        || restoreRecoveryCreative(player, server)
                        && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) {
                    if (parkedGhost(server, player) != null)
                        PARKED_GHOSTS.get(server).put(player.getUUID(), new ParkedGhost(player, verified));
                    if (parkedBody(server, player) != null) {
                        ParkedBody parked = parkedBody(server, player);
                        detached(server).put(player.getUUID(), new ParkedBody(player, parked.active(), parked.offline(), verified));
                    }
                    player.sendSystemMessage(Component.literal("Ghost return was not prepared. You remain in Creative; retry /ms14dev return."));
                    return;
                }
            } catch (RuntimeException | Error ignored) { }
        }
        disconnectGhostReturn(player);
    }

    private static boolean restoreRecoveryCreative(ServerPlayer player, MinecraftServer server) {
        if (server.getPlayerList().getPlayer(player.getUUID()) != player
                || (parkedGhost(server, player) == null && parkedBody(server, player) == null)
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) return false;
        if (RECOVERING_CREATIVE.putIfAbsent(server, player) != null) return false;
        try { return player.setGameMode(GameType.CREATIVE); }
        finally { RECOVERING_CREATIVE.remove(server, player); }
    }

    static boolean verifiedRecoveryClaim(SavedLifecycleProfile current, SavedLifecycleProfile expected,
                                         PlayerLifecycleRegistry.Snapshot memory, boolean rolledBack) {
        if (current == null || expected == null || memory == null || memory.active()
                || expected.state() != SavedLifecycleProfile.State.DEAD_CLAIM
                || current.state() != SavedLifecycleProfile.State.DEAD_CLAIM
                || memory.state() != PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM
                || !current.accountId().equals(expected.accountId())
                || !memory.accountId().equals(current.accountId())
                || !memory.profileId().equals(current.profileKey())
                || !current.bodyId().equals(expected.bodyId()) || !current.mindId().equals(expected.mindId())
                || !memory.bodyId().value().equals(current.bodyId())
                || !memory.mindId().value().equals(current.mindId())
                || memory.connectionGeneration() != current.connectionGeneration()) return false;
        if (!rolledBack) return current.equals(expected);
        return current.schemaVersion() == expected.schemaVersion()
                && current.profileKey().equals(expected.profileKey())
                && current.connectionEpoch().equals(expected.connectionEpoch())
                && current.dimension().equals(expected.dimension())
                && current.location().equals(expected.location())
                && current.appearance().equals(expected.appearance())
                && current.offlineSinceMillis().equals(expected.offlineSinceMillis())
                && expected.revision() < Long.MAX_VALUE && current.revision() == expected.revision() + 1
                && current.connectionGeneration() > expected.connectionGeneration();
    }

    private static boolean verifiedDetachedDeathClaim(SavedLifecycleProfile current, ParkedBody parked, UUID account) {
        return parked != null && (parked.retryDeath() != null
                ? verifiedParkedGhostClaim(current, parked.retryDeath(), account)
                : verifiedParkedBodyDeathClaim(current, parked.offline(), account));
    }

    static boolean verifiedParkedBodyOffline(SavedLifecycleProfile current, SavedLifecycleProfile active, UUID account) {
        return current != null && active != null && account != null
                && active.state() == SavedLifecycleProfile.State.ACTIVE
                && current.state() == SavedLifecycleProfile.State.OFFLINE
                && account.equals(current.accountId()) && account.equals(active.accountId())
                && current.profileKey().equals(active.profileKey())
                && current.mindId().equals(active.mindId()) && current.bodyId().equals(active.bodyId())
                && current.connectionEpoch().equals(active.connectionEpoch())
                && active.revision() < Long.MAX_VALUE && current.revision() == active.revision() + 1
                && current.connectionGeneration() > active.connectionGeneration();
    }

    /** The exact carrier's pinned post-park OFFLINE row, not just any account row. */
    static boolean verifiedDetachedOffline(MinecraftServer server, ServerPlayer player, SavedLifecycleProfile current) {
        if (server == null || player == null || !server.isSameThread()
                || server.getPlayerList().getPlayer(player.getUUID()) != player) return false;
        ParkedBody parked = refreshParkedBody(server, player, parkedBody(server, player));
        return parked != null && parked.retryDeath() == null
                && verifiedDetachedOfflineRow(current, parked.active(), parked.offline(), player.getUUID());
    }

    static boolean verifiedDetachedOfflineRow(SavedLifecycleProfile current, SavedLifecycleProfile active,
                                              SavedLifecycleProfile offline, UUID account) {
        return verifiedParkedBodyOffline(offline, active, account) && offline.equals(current);
    }

    private static ParkedBody refreshParkedBody(MinecraftServer server, ServerPlayer player, ParkedBody parked) {
        if (parked == null || parked.offline() != null) return parked;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return parked;
        try {
            var current = context.currentAccountProfile(player.getUUID()).orElse(null);
            if (verifiedParkedBodyOffline(current, parked.active(), player.getUUID())) {
                parked = new ParkedBody(player, parked.active(), current, parked.retryDeath());
                detached(server).put(player.getUUID(), parked);
            }
        } catch (IOException | RuntimeException ignored) { }
        return parked;
    }

    private static void disconnectGhostReturn(ServerPlayer player) {
        if (player.connection != null && player.connection.isAcceptingMessages())
            player.connection.disconnect(Component.literal("Ghost return could not complete safely. Reconnect for recovery."));
    }

    private static void discardFailedPrepared(LifecycleServerContext context,
                                              LifecycleDeadClaimGhostStager.PreparedGhost prepared) {
        try {
            context.lifecycle().unregisterTransientGhost(prepared.accountUUID(),
                    new com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId(prepared.ghost().getUUID()));
        } catch (RuntimeException | Error failure) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[lifecycle ghost] Transient ghost unregister failed; recovery required", failure);
        } finally {
            try { if (!prepared.ghost().isRemoved()) prepared.ghost().discard(); }
            catch (RuntimeException | Error failure) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                        "[lifecycle ghost] Transient ghost discard failed; recovery required", failure);
            }
        }
    }

    static boolean verifiedParkedGhostClaim(SavedLifecycleProfile current, SavedLifecycleProfile parked, UUID account) {
        return parked != null && current != null && current.equals(parked)
                && LifecycleGhostSessionControl.savedClaimMatches(current, account, parked.mindId(),
                        parked.bodyId(), parked.connectionGeneration());
    }

    static boolean verifiedParkedBodyDeathClaim(SavedLifecycleProfile current, SavedLifecycleProfile offline, UUID account) {
        return current != null && offline != null && account != null
                && offline.state() == SavedLifecycleProfile.State.OFFLINE
                && current.state() == SavedLifecycleProfile.State.DEAD_CLAIM
                && account.equals(offline.accountId()) && account.equals(current.accountId())
                && offline.profileKey().equals(current.profileKey())
                && offline.mindId().equals(current.mindId()) && offline.bodyId().equals(current.bodyId())
                && offline.connectionEpoch().equals(current.connectionEpoch())
                && offline.dimension().equals(current.dimension())
                && offline.offlineSinceMillis().equals(current.offlineSinceMillis())
                && offline.revision() < Long.MAX_VALUE && current.revision() == offline.revision() + 1
                && current.connectionGeneration() > offline.connectionGeneration();
    }

    private static ParkedBody parkedBody(MinecraftServer server, ServerPlayer player) {
        Map<UUID, ParkedBody> entries = DETACHED.get(server);
        ParkedBody parked = entries == null ? null : entries.get(player.getUUID());
        return parked != null && parked.player() == player ? parked : null;
    }

    private static ParkedGhost parkedGhost(MinecraftServer server, ServerPlayer player) {
        Map<UUID, ParkedGhost> entries = PARKED_GHOSTS.get(server);
        ParkedGhost parked = entries == null ? null : entries.get(player.getUUID());
        return parked != null && parked.player() == player ? parked : null;
    }

    private static void clearParkedGhost(MinecraftServer server, ServerPlayer player) {
        Map<UUID, ParkedGhost> entries = PARKED_GHOSTS.get(server);
        if (entries != null && entries.get(player.getUUID()) != null
                && entries.get(player.getUUID()).player() == player) {
            entries.remove(player.getUUID());
            if (entries.isEmpty()) PARKED_GHOSTS.remove(server);
        }
    }

    static boolean isTracked(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null && parkedBody(server, player) != null;
    }

    static boolean returnEligibilityDecision(boolean gatesEnabled, boolean exactTrackedCarrier, boolean operator,
                                             GameType mode, boolean uniqueOfflineProfileMatchesBody) {
        return gatesEnabled && exactTrackedCarrier && operator && mode == GameType.CREATIVE
                && uniqueOfflineProfileMatchesBody;
    }

    static boolean deadClaimReturnMessageEligible(boolean gatesEnabled, boolean exactTrackedCarrier, boolean operator,
                                                  GameType mode,
                                                  com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State profileState,
                                                  boolean uniqueCurrentPrimaryProfile) {
        return gatesEnabled && exactTrackedCarrier && operator && mode == GameType.CREATIVE
                && uniqueCurrentPrimaryProfile && profileState ==
                com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM;
    }

    /** Read-only current-primary check used solely to explain a confirmed death to the exact Creative carrier. */
    private static com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State
    currentPrimaryProfileState(MinecraftServer server, ServerPlayer player) {
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return null;
        try {
            return context.primaryStore().withCurrentPrimary(lease -> {
                var rows = lease.envelope().profiles().stream()
                        .filter(row -> row.accountId().equals(player.getUUID())).toList();
                return rows.size() == 1 ? rows.get(0).state() : null;
            }).orElse(null);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    static boolean failedReturnKeepsDetached(boolean preActivationFailure, boolean detachedStatePresent) {
        return preActivationFailure && detachedStatePresent;
    }

    static void returned(MinecraftServer server, ServerPlayer player) { clearTracked(server, player); }

    private static void clearTracked(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return;
        Map<UUID, ParkedBody> players = DETACHED.get(server);
        if (players != null) {
            ParkedBody parked = players.get(player.getUUID());
            if (parked != null && parked.player() == player) players.remove(player.getUUID());
            if (players.isEmpty()) DETACHED.remove(server);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearTracked(player.getServer(), player);
            clearParkedGhost(player.getServer(), player);
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        DETACHED.remove(event.getServer());
        PARKED_GHOSTS.remove(event.getServer());
        PARKING_GHOSTS.remove(event.getServer());
        PARKING_CREATIVE.remove(event.getServer());
        RETURNING_GHOSTS.remove(event.getServer());
        RETURNING_BODIES.remove(event.getServer());
        RECOVERING_CREATIVE.remove(event.getServer());
    }

    private static boolean isDetached(ServerPlayer player) {
        return player.level() instanceof ServerLevel level && isTracked(level.getServer(), player);
    }

    private static Map<UUID, ParkedBody> detached(MinecraftServer server) {
        return DETACHED.computeIfAbsent(server, ignored -> new HashMap<>());
    }
}
