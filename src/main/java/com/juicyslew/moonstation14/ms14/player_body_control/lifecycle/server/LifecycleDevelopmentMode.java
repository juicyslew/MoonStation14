package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbe;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeParkedInventory;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

/** Temporary operator-only escape from a possessed lifecycle body into vanilla Creative building mode. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class LifecycleDevelopmentMode {
    private static final Map<MinecraftServer, Map<UUID, ParkedBody>> DETACHED = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, OfflineCreative>> OFFLINE_CREATIVE = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, ParkedGhost>> PARKED_GHOSTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> PARKING_GHOSTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> PARKING_CREATIVE = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> SWITCHING_BODY_CREATIVE = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> RETURNING_GHOSTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> RETURNING_BODIES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, ServerPlayer> RECOVERING_CREATIVE = new IdentityHashMap<>();
    private record ParkedGhost(ServerPlayer player, SavedLifecycleProfile saved) { }
    private record ParkedBody(ServerPlayer player, SavedLifecycleProfile active, SavedLifecycleProfile offline,
                               SavedLifecycleProfile retryDeath) { }
    private record OfflineCreative(ServerPlayer player, SavedLifecycleProfile offline) { }
    static final String DEAD_RETURN_MESSAGE =
            "Your character has died. Use /ms14dev return to enter as a fresh ghost.";
    private LifecycleDevelopmentMode() { }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ms14dev").requires(source -> source.hasPermission(2))
                .then(Commands.literal("creative").executes(context -> enterCreative(
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
            if (rejectRawGhostModeChange(true, requested)
                    && !(requested == GameType.CREATIVE
                        && exactTransitionOwner(PARKING_GHOSTS.get(level.getServer()), player)
                        && exactTransitionOwner(PARKING_CREATIVE.get(level.getServer()), player))) {
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
            if (rejectRawBodyCreativeChange(true, requested,
                    exactTransitionOwner(SWITCHING_BODY_CREATIVE.get(level.getServer()), player))) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal(
                        "Body control is active. Use /ms14dev creative to park your character first."), true);
                return;
            }
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
            // The pre-change event runs before vanilla switches modes. Do not durably detach
            // a body from an occupied or mismatched carrier, even for an operator.
            if (active == null || !CarrierHandInventoryGate.allows(player)
                    || !LifecycleCharacterSessionControl.parkForDevelopmentMode(player)) {
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

    enum CreativeRoute { BODY, GHOST, NONE }

    static CreativeRoute creativeRoute(boolean exactCommittedBody, boolean exactCommittedGhost) {
        if (exactCommittedBody == exactCommittedGhost) return CreativeRoute.NONE;
        return exactCommittedBody ? CreativeRoute.BODY : CreativeRoute.GHOST;
    }

    static boolean rejectRawBodyCreativeChange(boolean bodyOwner, GameType requested, boolean exactCommandSwitch) {
        return bodyOwner && requested == GameType.CREATIVE && !exactCommandSwitch;
    }

    private static int enterCreative(ServerPlayer player) {
        if (player == null || player instanceof FakePlayer || !(player.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player
                || !player.hasPermissions(2) || player.connection == null
                || !player.connection.isAcceptingMessages()) return 0;
        CreativeRoute route = creativeRoute(LifecycleCharacterSessionControl.activeCharacterBody(player).isPresent(),
                LifecycleGhostSessionControl.committedControlledEntity(player).isPresent());
        return switch (route) {
            case BODY -> parkBody(player, level.getServer());
            case GHOST -> parkGhost(player);
            case NONE -> 0;
        };
    }

    private static int parkBody(ServerPlayer player, MinecraftServer server) {
        if (LifecycleGhostSessionControl.ownsAny(player) || isTracked(server, player)
                || SWITCHING_BODY_CREATIVE.putIfAbsent(server, player) != null) return 0;
        CreativeParkedInventory expected;
        try {
            // The body gate proves the physical carrier is empty *before* the switch.
            // The exact account-owned marker is the only source for the subsequent restore check.
            expected = CreativeParkedInventory.existingFor(player).orElse(null);
            if (expected == null || parkedMarker(player) != expected || !CarrierHandInventoryGate.allows(player)
                    || player.getInventory().selected != expected.snapshot().selectedHotbarIndex()) {
                SWITCHING_BODY_CREATIVE.remove(server, player);
                return 0;
            }
        } catch (RuntimeException | Error failure) {
            SWITCHING_BODY_CREATIVE.remove(server, player);
            return 0;
        }
        boolean changed;
        try {
            // beforeGameModeChange performs the existing exact ACTIVE -> OFFLINE durable park.
            changed = player.setGameMode(GameType.CREATIVE);
        } catch (RuntimeException | Error failure) {
            changed = false;
        } finally {
            SWITCHING_BODY_CREATIVE.remove(server, player);
        }
        boolean valid;
        try {
            valid = bodyCreativeCompleted(changed, player.gameMode.getGameModeForPlayer(),
                    isTracked(server, player), !LifecycleCharacterSessionControl.ownsAny(player)
                            && !LifecycleGhostSessionControl.ownsAny(player)
                            && !com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl.ownsDebugSession(player),
                    verifiedDetachedCreativeClaim(server, player),
                    restoredBodyCreativeCarrier(player, expected),
                    player.connection != null && player.connection.isAcceptingMessages()
                            && server.getPlayerList().getPlayer(player.getUUID()) == player);
        } catch (RuntimeException | Error failure) {
            valid = false;
        }
        if (!valid) {
            // Once the pre-event park committed OFFLINE, no active session may be left on a
            // spectator or un-restored Creative carrier. Reconnect uses the durable claim.
            // A pre-existing park may have been consumed or the durable profile may have
            // changed even when vanilla reports a canceled switch. Never guess or clear it.
            disconnectCarrier(player);
            return 0;
        }
        player.sendSystemMessage(Component.literal("Body parked. Use /ms14dev return to reclaim it, or reconnect."));
        return 1;
    }

    static boolean bodyCreativeCompleted(boolean changed, GameType actual, boolean exactDetached,
                                         boolean noController, boolean verifiedClaim, boolean restored,
                                         boolean connected) {
        return changed && actual == GameType.CREATIVE && exactDetached && noController
                && verifiedClaim && restored && connected;
    }

    private static boolean verifiedDetachedCreativeClaim(MinecraftServer server, ServerPlayer player) {
        if (!player.hasPermissions(2)) return false;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return false;
        try {
            return verifiedDetachedOffline(server, player,
                    context.currentAccountProfile(player.getUUID()).orElse(null));
        } catch (IOException | RuntimeException failure) { return false; }
    }

    private static boolean restoredBodyCreativeCarrier(ServerPlayer player, CreativeParkedInventory expected) {
        if (expected == null || parkedMarker(player) != null
                || player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())) return false;
        var observed = CarrierHandInventoryGate.inspect(player).orElse(null);
        if (observed == null || observed.account() == null || !observed.account().equals(expected.account()))
            return false;
        // Check the menu slots still address the actual inventory, not a substitute container.
        for (int slot = 5; slot < 46; slot++)
            if (player.inventoryMenu.slots.get(slot).container != player.getInventory()) return false;
        return bodyCreativeRestored(expected.snapshot(), observed);
    }

    /** Read-only value policy for the 42 supported physical slots and every unsupported compartment. */
    static boolean bodyCreativeRestored(CreativeInventorySnapshot expected, CarrierHandInventoryGate.Fixture actual) {
        if (expected == null || actual == null || actual.markerPresent() || actual.existingPark() != null
                || actual.main() == null || actual.main().size() != 36
                || actual.armor() == null || actual.armor().size() != 4
                || actual.offhand() == null || actual.offhand().size() != 1
                || actual.selected() != expected.selectedHotbarIndex()
                || !canonicalEmptySlots(actual.crafting(), 4) || !canonicalEmpty(actual.result())
                || !canonicalEmptySlots(actual.ender(), 27)
                || actual.menuSlots() == null || actual.menuSlots().size() != 46) return false;
        for (int i = 0; i < CreativeInventorySnapshot.SLOT_COUNT; i++) {
            ItemStack stack = i < 36 ? actual.main().get(i)
                    : i < 40 ? actual.armor().get(i - 36)
                    : i == 40 ? actual.offhand().get(0) : actual.cursor();
            if (stack == null || stack.isEmpty() && !canonicalEmpty(stack)
                    || !ItemStack.matches(stack, expected.stackCopy(i))) return false;
        }
        for (int i = 0; i < 46; i++) {
            ItemStack expectedSlot = i < 5 ? ItemStack.EMPTY
                    : i < 9 ? actual.armor().get(8 - i)
                    : i < 36 ? actual.main().get(i)
                    : i < 45 ? actual.main().get(i - 36) : actual.offhand().get(0);
            if (actual.menuSlots().get(i) == null || !ItemStack.matches(actual.menuSlots().get(i), expectedSlot))
                return false;
        }
        return true;
    }

    private static boolean canonicalEmptySlots(java.util.List<ItemStack> slots, int size) {
        if (slots == null || slots.size() != size) return false;
        for (ItemStack stack : slots) if (!canonicalEmpty(stack)) return false;
        return true;
    }

    private static boolean canonicalEmpty(ItemStack stack) {
        return stack == ItemStack.EMPTY && stack.getCount() == 0;
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
        var result = LifecycleExistingBodyReconnect.returnDevelopmentDetached(player, server);
        if (result == LifecycleExistingBodyReconnect.ReturnResult.CHUNK_LOADING)
            player.sendSystemMessage(Component.literal("Saved body chunk loading; you remain in Creative with your inventory unchanged. Retry /ms14dev return shortly."));
        else if (result == LifecycleExistingBodyReconnect.ReturnResult.BLOCKED
                && player.connection != null && player.connection.isAcceptingMessages())
            player.sendSystemMessage(Component.literal("The exact saved character body could not be proven safe. You remain in Creative; inspect recovery or reconnect."));
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
        player.sendSystemMessage(Component.literal("Ghost parked. Use /ms14dev return for a fresh ghost, or reconnect."));
        return 1;
    }

    /** Called after the exact ghost claim is returned and transient authority is retired. */
    static boolean completeGhostCreativePark(ServerPlayer player, SavedLifecycleProfile saved) {
        MinecraftServer server = player.getServer();
        if (server == null || saved == null || !server.isSameThread() || PARKING_GHOSTS.get(server) != player
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                || !LifecycleGhostSessionControl.savedClaimMatches(saved, player.getUUID(),
                        saved.mindId(), saved.bodyId(), saved.connectionGeneration())) return false;
        Map<UUID, ParkedGhost> entries = PARKED_GHOSTS.computeIfAbsent(server, ignored -> new HashMap<>());
        if (entries.putIfAbsent(player.getUUID(), new ParkedGhost(player, saved)) != null) return false;
        return creativeExitHandoffPolicy(true, true, player.gameMode.getGameModeForPlayer(),
                player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                () -> verifiedCreativeExitClaim(server, player),
                () -> CarrierHandInventoryGate.allows(player),
                () -> CreativeCarrierTransition.restore(player), () -> syncCarrier(player),
                () -> disconnectCarrier(player));
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

    enum ReturnSwitch { COMMITTED, ALREADY_RESTORED, NO_WRITE_DENIAL, FAILED }

    record BodyReturnSwitch(ReturnSwitch result, CreativeParkedInventory marker) { }

    static BodyReturnSwitch setReturningBodySpectator(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || !isTracked(server, player)
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                || RETURNING_BODIES.putIfAbsent(server, player) != null)
            return new BodyReturnSwitch(ReturnSwitch.FAILED, null);
        try {
            ReturnSwitch result = returningBodySpectatorResultPolicy(true, player.gameMode.getGameModeForPlayer(),
                    player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                    () -> CarrierHandInventoryGate.allows(player), () -> CreativeCarrierTransition.parkAttempt(player),
                    () -> player.setGameMode(GameType.SPECTATOR),
                    () -> player.gameMode.getGameModeForPlayer(),
                    () -> CreativeCarrierTransition.restore(player), () -> syncCarrier(player),
                    () -> disconnectCarrier(player), () -> {
                        if (player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())) disconnectCarrier(player);
                        else reportNoWriteDenial(player);
                    }, () -> parkedMarker(player) != null);
            CreativeParkedInventory marker = result == ReturnSwitch.COMMITTED ? parkedMarker(player) : null;
            if (result == ReturnSwitch.COMMITTED && (marker == null || !marker.account().equals(player.getUUID()))) {
                disconnectCarrier(player);
                return new BodyReturnSwitch(ReturnSwitch.FAILED, null);
            }
            return new BodyReturnSwitch(result, marker);
        } catch (RuntimeException | Error failure) {
            disconnectCarrier(player);
            return new BodyReturnSwitch(ReturnSwitch.FAILED, null);
        }
        finally { RETURNING_BODIES.remove(server, player); }
    }

    /** Policy fixture seam: callbacks are production-only when invoked by the exact transition owner. */
    static boolean returningBodySpectatorPolicy(boolean exactOwner, GameType initial, boolean markerPresent,
                                                  BooleanSupplier clean, Supplier<CreativeCarrierTransition.Result> park,
                                                  BooleanSupplier switchMode, Supplier<GameType> actual,
                                                  Supplier<CreativeCarrierTransition.Result> restore,
                                                  Runnable sync, Runnable disconnect) {
        return returningBodySpectatorAttemptPolicy(exactOwner, initial, markerPresent, clean,
                () -> new CreativeCarrierTransition.Attempt(park.get(), false, CreativeCarrierSnapshotProbe.Reason.READY),
                switchMode, actual, restore, sync, disconnect, () -> { });
    }

    static boolean returningBodySpectatorAttemptPolicy(boolean exactOwner, GameType initial, boolean markerPresent,
                                                  BooleanSupplier clean, Supplier<CreativeCarrierTransition.Attempt> park,
                                                  BooleanSupplier switchMode, Supplier<GameType> actual,
                                                  Supplier<CreativeCarrierTransition.Result> restore,
                                                  Runnable sync, Runnable disconnect, Runnable noWriteDenial) {
        return returningBodySpectatorResultPolicy(exactOwner, initial, markerPresent, clean, park,
                switchMode, actual, restore, sync, disconnect, noWriteDenial, () -> true) == ReturnSwitch.COMMITTED;
    }

    static ReturnSwitch returningBodySpectatorResultPolicy(boolean exactOwner, GameType initial, boolean markerPresent,
                                                   BooleanSupplier clean, Supplier<CreativeCarrierTransition.Attempt> park,
                                                   BooleanSupplier switchMode, Supplier<GameType> actual,
                                                   Supplier<CreativeCarrierTransition.Result> restore,
                                                   Runnable sync, Runnable disconnect, Runnable noWriteDenial,
                                                   BooleanSupplier exactMarker) {
        if (!exactOwner || initial != GameType.CREATIVE) return ReturnSwitch.FAILED;
        try {
            if (markerPresent) {
                if (!clean.getAsBoolean()) { disconnect.run(); return ReturnSwitch.FAILED; }
            } else {
                CreativeCarrierTransition.Attempt attempt = park.get();
                if (attempt.result() != CreativeCarrierTransition.Result.PARKED) {
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                            "Creative carrier park refused: result={}, reason={}, noWriteDenial={}",
                            attempt.result(), attempt.reason(), attempt.noWriteDenial());
                    // Only a read-only denial is safe to leave online. A rollback that reports
                    // DENIED may have written; never infer no-write from the result alone.
                    if (attempt.result() == CreativeCarrierTransition.Result.DENIED
                            && attempt.noWriteDenial() && actual.get() == GameType.CREATIVE) {
                        noWriteDenial.run();
                        return ReturnSwitch.NO_WRITE_DENIAL;
                    }
                    disconnect.run();
                    return ReturnSwitch.FAILED;
                }
            }
            if (!clean.getAsBoolean()) { disconnect.run(); return ReturnSwitch.FAILED; }
            // Send the cleared slots and retained selection before Spectator/ghost startup.
            sync.run();
            boolean switched = switchMode.getAsBoolean();
            GameType mode = actual.get();
            if (switched && mode == GameType.SPECTATOR && clean.getAsBoolean() && exactMarker.getAsBoolean())
                return ReturnSwitch.COMMITTED;
            if (mode == GameType.CREATIVE && clean.getAsBoolean()
                    && exactMarker.getAsBoolean()
                    && restore.get() == CreativeCarrierTransition.Result.RESTORED) {
                sync.run();
                return ReturnSwitch.ALREADY_RESTORED;
            }
        } catch (RuntimeException | Error failure) {
            // Even a thrown mode switch may have changed the mode; never guess which writes are safe.
        }
        disconnect.run();
        return ReturnSwitch.FAILED;
    }

    /** The ghost path uses the identical park/rollback rules, but only under its own transition owner. */
    static boolean returningGhostSpectatorPolicy(boolean exactOwner, GameType initial, boolean markerPresent,
                                                 BooleanSupplier clean, Supplier<CreativeCarrierTransition.Result> park,
                                                 BooleanSupplier switchMode, Supplier<GameType> actual,
                                                 Supplier<CreativeCarrierTransition.Result> restore,
                                                 Runnable sync, Runnable disconnect) {
        return returningBodySpectatorPolicy(exactOwner, initial, markerPresent, clean, park, switchMode,
                actual, restore, sync, disconnect);
    }

    private static void reportNoWriteDenial(ServerPlayer player) {
        CreativeCarrierSnapshotProbe.Reason reason = CreativeCarrierSnapshotProbe.reason(player);
        com.juicyslew.moonstation14.MoonStation14.LOGGER.warn("Creative carrier park refused without writes: reason={}", reason);
        player.sendSystemMessage(Component.literal("Creative inventory was not parked (" + reason
                + "). You remain in Creative with your items. Keep all items; resolve unsupported compartments or ask an administrator to inspect the carrier before retrying /ms14dev return."));
    }

    private static CreativeParkedInventory parkedMarker(ServerPlayer player) {
        return player.getExistingDataOrNull(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get());
    }

    static boolean restoreCreativeInventory(ServerPlayer player, CreativeParkedInventory expected) {
        try {
            return restoreParkPolicy(player.gameMode.getGameModeForPlayer(), expected, parkedMarker(player),
                    player.getUUID(), () -> CarrierHandInventoryGate.allows(player),
                    () -> CreativeCarrierTransition.restore(player), () -> parkedMarker(player) == null,
                    () -> syncCarrier(player), () -> disconnectCarrier(player));
        } catch (RuntimeException | Error failure) {
            disconnectCarrier(player);
            return false;
        }
    }

    static boolean restoreParkPolicy(GameType mode, CreativeParkedInventory expected, CreativeParkedInventory actual,
                                     UUID account, BooleanSupplier clean,
                                     Supplier<CreativeCarrierTransition.Result> restore,
                                     BooleanSupplier markerConsumed, Runnable sync, Runnable disconnect) {
        try {
            if (mode != GameType.CREATIVE || expected == null || actual != expected || account == null
                    || !account.equals(expected.account()) || !clean.getAsBoolean()
                    || restore.get() != CreativeCarrierTransition.Result.RESTORED
                    || !markerConsumed.getAsBoolean()) {
                disconnect.run();
                return false;
            }
            sync.run();
            return true;
        } catch (RuntimeException | Error failure) {
            disconnect.run();
            return false;
        }
    }

    static boolean restoreCreativeInventory(ServerPlayer player) {
        return restoreCreativeInventory(player, parkedMarker(player));
    }

    /** Called only by the ServerPlayer Creative return hook, after vanilla has applied the mode. */
    public static void creativeModeChangeReturned(ServerPlayer player, GameType requested, boolean changed) {
        if (requested != GameType.CREATIVE || player == null || player instanceof FakePlayer
                || !(player.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (server == null || !server.isSameThread()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || (parkedBody(server, player) == null && parkedGhost(server, player) == null)) return;
        // restoreDetachedCreative owns the rollback restore after its nested mode switch.
        if (RECOVERING_CREATIVE.get(server) == player) return;
        creativeExitHandoffPolicy(true, changed, player.gameMode.getGameModeForPlayer(),
                player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                () -> verifiedCreativeExitClaim(server, player),
                () -> CarrierHandInventoryGate.allows(player),
                () -> CreativeCarrierTransition.restore(player), () -> syncCarrier(player),
                () -> disconnectCarrier(player));
    }

    private static boolean verifiedCreativeExitClaim(MinecraftServer server, ServerPlayer player) {
        if (!player.hasPermissions(2) || RETURNING_BODIES.get(server) == player
                || RETURNING_GHOSTS.get(server) == player) return false;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return false;
        SavedLifecycleProfile current;
        try { current = context.currentAccountProfile(player.getUUID()).orElse(null); }
        catch (IOException | RuntimeException failure) { return false; }
        ParkedGhost ghost = parkedGhost(server, player);
        if (ghost != null) return verifiedParkedGhostClaim(current, ghost.saved(), player.getUUID());
        return verifiedDetachedOffline(server, player, current)
                || verifiedOfflineCreative(server, player, current);
    }

    /** Value/callback policy only; the production caller supplies exact connected-player ownership. */
    static boolean creativeExitHandoffPolicy(boolean exactOwner, boolean changed, GameType actual,
                                             boolean markerPresent, BooleanSupplier verifiedClaim, BooleanSupplier clean,
                                             Supplier<CreativeCarrierTransition.Result> restore,
                                             Runnable sync, Runnable disconnect) {
        if (!exactOwner) return false;
        try {
            if (!changed || actual != GameType.CREATIVE) {
                // A canceled switch normally leaves Spectator untouched. If it changed the
                // mode despite returning false, never leave a parked Creative carrier online.
                if (actual == GameType.CREATIVE && markerPresent) disconnect.run();
                return false;
            }
            // An original empty/unmarked ghost may never have published a park, but it still
            // needs the pinned saved claim; absence alone is not evidence of a safe exit.
            if (!verifiedClaim.getAsBoolean() || !clean.getAsBoolean()
                    || markerPresent && restore.get() != CreativeCarrierTransition.Result.RESTORED) {
                disconnect.run();
                return false;
            }
            if (markerPresent) sync.run();
            return true;
        } catch (RuntimeException | Error failure) {
            disconnect.run();
            return false;
        }
    }

    static void syncCarrier(ServerPlayer player) {
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        player.inventoryMenu.sendAllDataToRemote();
    }

    private static void disconnectCarrier(ServerPlayer player) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "Creative carrier handoff requires recovery: reason={}, markerPresent={}, mode={}",
                CreativeCarrierSnapshotProbe.reason(player),
                player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                player.gameMode.getGameModeForPlayer());
        if (player.connection != null && player.connection.isAcceptingMessages())
            player.connection.disconnect(Component.literal("Creative carrier inventory handoff could not be verified. Reconnect for recovery."));
    }

    static boolean restoreDetachedCreative(ServerPlayer player) {
        return restoreDetachedCreative(player, parkedMarker(player));
    }

    static boolean restoreDetachedCreative(ServerPlayer player, CreativeParkedInventory expected) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || server.getPlayerList().getPlayer(player.getUUID()) != player
                || !isTracked(server, player) || RECOVERING_CREATIVE.putIfAbsent(server, player) != null)
            return false;
        try {
            return (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                    || player.setGameMode(GameType.CREATIVE)
                    && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE)
                    && restoreCreativeInventory(player, expected);
        } catch (RuntimeException | Error failure) {
            disconnectCarrier(player);
            return false;
        }
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
        try {
            spectator = returningBodySpectatorAttemptPolicy(RETURNING_GHOSTS.get(server) == player,
                    player.gameMode.getGameModeForPlayer(),
                    player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                    () -> CarrierHandInventoryGate.allows(player), () -> CreativeCarrierTransition.parkAttempt(player),
                    () -> player.setGameMode(GameType.SPECTATOR),
                    () -> player.gameMode.getGameModeForPlayer(),
                    () -> CreativeCarrierTransition.restore(player), () -> syncCarrier(player),
                    () -> disconnectCarrier(player), () -> {
                        if (player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())) disconnectCarrier(player);
                        else reportNoWriteDenial(player);
                    });
        } catch (RuntimeException | Error failure) { disconnectCarrier(player); spectator = false; }
        finally { RETURNING_GHOSTS.remove(server, player); }
        if (!spectator) return 0;
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
                if ((player.gameMode.getGameModeForPlayer() == GameType.CREATIVE
                        || restoreRecoveryCreative(player, server)
                        && player.gameMode.getGameModeForPlayer() == GameType.CREATIVE)
                        && restoreCreativeInventory(player)) {
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

    /** Login refusal pins an already OFFLINE row; it is not a body park or an ACTIVE claim. */
    static boolean registerOfflineCreative(MinecraftServer server, ServerPlayer player, SavedLifecycleProfile saved) {
        if (server == null || player == null || saved == null || !server.isSameThread()
                || player.getServer() != server || player instanceof FakePlayer
                || player.connection == null || !player.connection.isAcceptingMessages() || player.isRemoved()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || !player.hasPermissions(2) || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                || player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())
                || parkedBody(server, player) != null || parkedGhost(server, player) != null
                || LifecycleGhostSessionControl.ownsAny(player) || LifecycleCharacterSessionControl.ownsAny(player)
                || com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl.ownsDebugSession(player))
            return false;
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null || !verifiedOfflineCreativeRow(saved, saved, player.getUUID(),
                context.lifecycle().profile(player.getUUID()).orElse(null))) return false;
        return OFFLINE_CREATIVE.computeIfAbsent(server, ignored -> new HashMap<>())
                .putIfAbsent(player.getUUID(), new OfflineCreative(player, saved)) == null;
    }

    static boolean verifiedOfflineCreative(MinecraftServer server, ServerPlayer player, SavedLifecycleProfile current) {
        if (server == null || player == null || !server.isSameThread() || player instanceof FakePlayer
                || player.getServer() != server || player.isRemoved() || player.connection == null
                || !player.connection.isAcceptingMessages() || !player.hasPermissions(2)
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                || player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())
                || LifecycleCharacterSessionControl.ownsAny(player) || LifecycleGhostSessionControl.ownsAny(player)
                || com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl.ownsDebugSession(player)) return false;
        Map<UUID, OfflineCreative> entries = OFFLINE_CREATIVE.get(server);
        OfflineCreative claim = entries == null ? null : entries.get(player.getUUID());
        var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        return offlineClaimOwner(claim == null ? null : claim.player(), player) && context != null
                && verifiedOfflineCreativeRow(current, claim.offline(), player.getUUID(),
                        context.lifecycle().profile(player.getUUID()).orElse(null));
    }

    /** Pure policy, not proof of a connected player or of a unique current-primary row. */
    static boolean verifiedOfflineCreativeRow(SavedLifecycleProfile current, SavedLifecycleProfile pinned,
                                               UUID account, PlayerLifecycleRegistry.Snapshot memory) {
        return current != null && pinned != null && account != null
                && pinned.state() == SavedLifecycleProfile.State.OFFLINE && current.equals(pinned)
                && account.equals(current.accountId())
                && (memory == null || !memory.active() && !memory.deadClaim()
                    && memory.state() == PlayerLifecycleRegistry.LifecycleState.OFFLINE
                    && account.equals(memory.accountId()) && current.profileKey().equals(memory.profileId())
                    && current.bodyId().equals(memory.bodyId().value())
                    && current.mindId().equals(memory.mindId().value())
                    && current.connectionGeneration() == memory.connectionGeneration());
    }

    /** Stale same-UUID login/logout events must never consume a different player's claim. */
    static boolean offlineClaimOwner(Object claimedPlayer, Object player) {
        return claimedPlayer != null && claimedPlayer == player;
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
        if (server == null || player == null) return false;
        Map<UUID, OfflineCreative> entries = OFFLINE_CREATIVE.get(server);
        OfflineCreative claim = entries == null ? null : entries.get(player.getUUID());
        return parkedBody(server, player) != null
                || offlineClaimOwner(claim == null ? null : claim.player(), player);
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

    /** The exit event runs before vanilla applies Creative; restore only after the mode is actual. */
    @SubscribeEvent
    public static void restoreOnExplicitCreativeExit(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Map<UUID, ParkedBody> entries = DETACHED.get(server);
        if (entries == null || !server.isSameThread()) return;
        for (ParkedBody parked : entries.values().toArray(ParkedBody[]::new)) {
            ServerPlayer player = parked.player();
            if (server.getPlayerList().getPlayer(player.getUUID()) != player
                    || player.connection == null || !player.connection.isAcceptingMessages()
                    || player.gameMode.getGameModeForPlayer() != GameType.CREATIVE
                    || !player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get())) continue;
            // A parked body is the evidence of the explicit operator exit. Never restore on
            // a changed/offline-unverified profile or while another transition owns the carrier.
            if (RETURNING_BODIES.get(server) == player || RECOVERING_CREATIVE.get(server) == player) continue;
            var context = LifecycleStartupRuntime.contextFor(server).orElse(null);
            SavedLifecycleProfile current = null;
            if (context != null) try { current = context.currentAccountProfile(player.getUUID()).orElse(null); }
            catch (IOException | RuntimeException ignored) { }
            if (current == null || !player.hasPermissions(2) || !verifiedDetachedOffline(server, player, current)
                    || !restoreCreativeInventory(player)) disconnectCarrier(player);
        }
    }

    private static void clearTracked(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return;
        Map<UUID, OfflineCreative> offline = OFFLINE_CREATIVE.get(server);
        if (offline != null) {
            OfflineCreative claim = offline.get(player.getUUID());
            if (offlineClaimOwner(claim == null ? null : claim.player(), player)) offline.remove(player.getUUID());
            if (offline.isEmpty()) OFFLINE_CREATIVE.remove(server);
        }
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
        OFFLINE_CREATIVE.remove(event.getServer());
        PARKED_GHOSTS.remove(event.getServer());
        PARKING_GHOSTS.remove(event.getServer());
        PARKING_CREATIVE.remove(event.getServer());
        SWITCHING_BODY_CREATIVE.remove(event.getServer());
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
