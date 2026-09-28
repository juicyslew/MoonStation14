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

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

/** Temporary operator-only escape from a possessed lifecycle body into vanilla Creative building mode. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleDevelopmentMode {
    private static final Map<MinecraftServer, Map<UUID, ServerPlayer>> DETACHED = new IdentityHashMap<>();
    static final String DEAD_RETURN_MESSAGE =
            "Your character has died. You cannot return to that body. Log out and reconnect to enter as a ghost.";
    private LifecycleDevelopmentMode() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14dev").requires(source -> source.hasPermission(2))
                .then(Commands.literal("return").executes(context -> returnToCharacter(
                        context.getSource().getPlayerOrException()))));
    }

    @SubscribeEvent
    public static void beforeGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return;

        GameType requested = event.getNewGameMode();
        if (isTracked(level.getServer(), player)) {
            if (requested != GameType.CREATIVE && requested != GameType.SPECTATOR) {
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
            if (!LifecycleCharacterSessionControl.parkForDevelopmentMode(player)) {
                event.setCanceled(true);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Could not park body; remain in character."), true);
            } else {
                detached(level.getServer()).put(player.getUUID(), player);
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
        boolean gates = com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate.enabledForServer()
                && !com.juicyslew.moonstation14.ms14.movement.MovementStartupGate.enabledForServer();
        boolean exactTrackedCarrier = isTracked(server, player)
                && server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
        if (deadClaimReturnMessageEligible(gates, exactTrackedCarrier, player.hasPermissions(2),
                player.gameMode.getGameModeForPlayer(), currentPrimaryProfileState(server, player), true)) {
            player.sendSystemMessage(Component.literal(DEAD_RETURN_MESSAGE));
            return 0;
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

    static boolean isTracked(MinecraftServer server, ServerPlayer player) {
        return server != null && player != null && detached(server).get(player.getUUID()) == player;
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
        Map<UUID, ServerPlayer> players = DETACHED.get(server);
        if (players != null) {
            players.remove(player.getUUID(), player);
            if (players.isEmpty()) DETACHED.remove(server);
        }
    }

    @SubscribeEvent
    public static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clearTracked(player.getServer(), player);
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) { DETACHED.remove(event.getServer()); }

    private static boolean isDetached(ServerPlayer player) {
        return player.level() instanceof ServerLevel level && isTracked(level.getServer(), player);
    }

    private static Map<UUID, ServerPlayer> detached(MinecraftServer server) {
        return DETACHED.computeIfAbsent(server, ignored -> new HashMap<>());
    }
}
