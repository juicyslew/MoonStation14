package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Sole lifecycle owner of the lifecycle networking slot; character and future ghost handlers share it. */
final class LifecycleSessionPacketRouter {
    private static final LifecycleSessionPacketRouter INSTANCE = new LifecycleSessionPacketRouter();
    private static final AtomicBoolean UNKNOWN_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean AMBIGUOUS_LOGGED = new AtomicBoolean();

    private BiConsumer<CustomPacketPayload, IPayloadContext> characterHandler;
    private Predicate<ServerPlayer> characterOwner;
    private BiConsumer<CustomPacketPayload, IPayloadContext> ghostHandler;
    private Predicate<ServerPlayer> ghostOwner;

    LifecycleSessionPacketRouter() { }

    static LifecycleSessionPacketRouter instance() {
        return INSTANCE;
    }

    synchronized void installCharacterHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler,
                                              Predicate<ServerPlayer> ownerPredicate) {
        if (handler == null || ownerPredicate == null) throw new IllegalArgumentException("Character handler and owner are required");
        characterHandler = handler;
        characterOwner = ownerPredicate;
        installNetworkingSlot();
    }

    /** Reserved for the future lifecycle ghost controller. It cannot replace the existing ghost slot. */
    synchronized void installGhostHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler,
                                          Predicate<ServerPlayer> ownerPredicate) {
        if (handler == null || ownerPredicate == null) throw new IllegalArgumentException("Ghost handler and owner are required");
        if (ghostHandler != null || ghostOwner != null) throw new IllegalStateException("Lifecycle ghost handler already installed");
        ghostHandler = handler;
        ghostOwner = ownerPredicate;
        installNetworkingSlot();
    }

    synchronized void clearCharacterHandler() {
        characterHandler = null;
        characterOwner = null;
        refreshNetworkingSlot();
    }

    synchronized void clearGhostHandler() {
        ghostHandler = null;
        ghostOwner = null;
        refreshNetworkingSlot();
    }

    synchronized boolean ownsAny(ServerPlayer player) {
        return owns(characterOwner, player) || owns(ghostOwner, player);
    }

    synchronized boolean ghostHandlerInstalled() {
        return ghostHandler != null && ghostOwner != null;
    }

    synchronized boolean characterHandlerInstalled() {
        return characterHandler != null && characterOwner != null;
    }

    void handlePayload(CustomPacketPayload payload, IPayloadContext context) {
        if (context == null || !(context.player() instanceof ServerPlayer player)) {
            if (UNKNOWN_LOGGED.compareAndSet(false, true))
                MoonStation14.LOGGER.warn("Dropping lifecycle payload without a server player: {}", payload.type().id());
            return;
        }
        BiConsumer<CustomPacketPayload, IPayloadContext> character;
        BiConsumer<CustomPacketPayload, IPayloadContext> ghost;
        boolean characterOwns;
        boolean ghostOwns;
        synchronized (this) {
            character = characterHandler;
            ghost = ghostHandler;
            characterOwns = owns(characterOwner, player);
            ghostOwns = owns(ghostOwner, player);
        }
        route(characterOwns, ghostOwns, selected -> {
            if (selected == Route.CHARACTER && character != null) character.accept(payload, context);
            else if (selected == Route.GHOST && ghost != null) ghost.accept(payload, context);
        }, payload);
    }

    private synchronized void installNetworkingSlot() {
        GhostControlNetworking.installLifecycleServerHandler(this::handlePayload, this::ownsAny);
    }

    private synchronized void refreshNetworkingSlot() {
        if (networkingSlotRequired(characterHandler != null, characterOwner != null,
                ghostHandler != null, ghostOwner != null)) installNetworkingSlot();
        else GhostControlNetworking.clearLifecycleServerHandler();
    }

    static boolean networkingSlotRequired(boolean characterHandlerPresent, boolean characterOwnerPresent,
                                          boolean ghostHandlerPresent, boolean ghostOwnerPresent) {
        return (characterHandlerPresent && characterOwnerPresent) || (ghostHandlerPresent && ghostOwnerPresent);
    }

    private static boolean owns(Predicate<ServerPlayer> predicate, ServerPlayer player) {
        if (predicate == null || player == null) return false;
        try {
            return predicate.test(player);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static Route route(boolean characterOwns, boolean ghostOwns, Consumer<Route> dispatch) {
        if (characterOwns && ghostOwns) return Route.AMBIGUOUS;
        if (!characterOwns && !ghostOwns) return Route.UNKNOWN;
        Route selected = characterOwns ? Route.CHARACTER : Route.GHOST;
        dispatch.accept(selected);
        return selected;
    }

    private static void route(boolean characterOwns, boolean ghostOwns, Consumer<Route> dispatch,
                              CustomPacketPayload payload) {
        Route route = route(characterOwns, ghostOwns, dispatch);
        if (route == Route.UNKNOWN && UNKNOWN_LOGGED.compareAndSet(false, true))
            MoonStation14.LOGGER.warn("Dropping lifecycle payload with no session owner: {}", payload.type().id());
        if (route == Route.AMBIGUOUS && AMBIGUOUS_LOGGED.compareAndSet(false, true))
            MoonStation14.LOGGER.warn("Dropping lifecycle payload with overlapping session ownership: {}", payload.type().id());
    }

    enum Route { CHARACTER, GHOST, UNKNOWN, AMBIGUOUS }
}
