package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Common play-phase registration and fail-closed dispatch for ghost control payloads. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class GhostControlNetworking {
    public static final String PROTOCOL_VERSION = "3";

    private static volatile ServerHandler legacyServerHandler;
    private static volatile ServerHandler debugServerHandler;
    private static volatile ServerHandler lifecycleServerHandler;
    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> clientHandler;
    private static final java.util.concurrent.atomic.AtomicBoolean UNKNOWN_SERVER_ROUTE_LOGGED = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean AMBIGUOUS_SERVER_ROUTE_LOGGED = new java.util.concurrent.atomic.AtomicBoolean();

    private GhostControlNetworking() { }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN)
                .playToClient(GhostControlPayloads.Offer.TYPE, GhostControlPayloads.Offer.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToClient(GhostControlPayloads.Begin.TYPE, GhostControlPayloads.Begin.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToClient(GhostControlPayloads.Commit.TYPE, GhostControlPayloads.Commit.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToClient(GhostControlPayloads.Stop.TYPE, GhostControlPayloads.Stop.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToClient(GhostControlPayloads.Snapshot.TYPE, GhostControlPayloads.Snapshot.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToServer(GhostControlPayloads.Ready.TYPE, GhostControlPayloads.Ready.STREAM_CODEC,
                        GhostControlNetworking::handleServerPayload)
                .playToServer(GhostControlPayloads.OfferReady.TYPE, GhostControlPayloads.OfferReady.STREAM_CODEC,
                        GhostControlNetworking::handleServerPayload)
                .playToServer(GhostControlPayloads.Intent.TYPE, GhostControlPayloads.Intent.STREAM_CODEC,
                        GhostControlNetworking::handleServerPayload);
    }

    /** Installs a server controller explicitly; no handler is installed by registration itself. */
    public static void installServerHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        legacyServerHandler = handler == null ? null : new ServerHandler(handler, player -> true);
    }

    /** Installs the debug controller independently, with explicit exact-session ownership. */
    public static void installDebugServerHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler,
                                                 Predicate<ServerPlayer> ownerPredicate) {
        debugServerHandler = handler == null || ownerPredicate == null ? null : new ServerHandler(handler, ownerPredicate);
    }

    /** Clears only the debug controller, leaving any lifecycle controller installed. */
    public static void clearDebugServerHandler() {
        debugServerHandler = null;
    }

    /** Installs the lifecycle controller independently, with explicit exact-session ownership. */
    public static void installLifecycleServerHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler,
                                                     Predicate<ServerPlayer> ownerPredicate) {
        lifecycleServerHandler = handler == null || ownerPredicate == null ? null
                : new ServerHandler(handler, ownerPredicate);
    }

    /** Clears only the lifecycle controller. */
    public static void clearLifecycleServerHandler() {
        lifecycleServerHandler = null;
    }

    /** Installs the client controller explicitly without referencing client-only classes here. */
    public static void installClientHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        clientHandler = handler;
    }

    /** Sends a ghost-control packet only from a physical client. */
    public static void sendToServer(CustomPacketPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            MoonStation14.LOGGER.warn("Dropping ghost-control send to server outside the client distribution");
            return;
        }
        try {
            PacketDistributor.sendToServer(payload);
        } catch (Exception exception) {
            MoonStation14.LOGGER.error("Failed to send ghost-control payload to server", exception);
        }
    }

    /** Sends a ghost-control packet to one server player. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (Exception exception) {
            MoonStation14.LOGGER.error("Failed to send ghost-control payload to player", exception);
        }
    }

    private static void handleServerPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            MoonStation14.LOGGER.warn("Dropping ghost-control payload without a server player: {}", payload.type().id());
            return;
        }
        var level = player.level();
        var server = level.getServer();
        if (!isConnectedServerPlayer(true, player instanceof FakePlayer, player.isRemoved(), level.isClientSide,
                server != null, server != null && server.getPlayerList().getPlayer(player.getUUID()) == player)) {
            MoonStation14.LOGGER.warn("Dropping ghost-control payload from an unconnected or fake player: {}",
                    payload.type().id());
            return;
        }
        ServerHandler debug = debugServerHandler;
        ServerHandler lifecycle = lifecycleServerHandler;
        ServerHandler legacy = legacyServerHandler;
        boolean debugOwns = owns(debug, player);
        boolean lifecycleOwns = owns(lifecycle, player);
        boolean legacyOwns = owns(legacy, player);
        ServerRoute route = routeServerPayload(debugOwns, lifecycleOwns, legacyOwns, selectedRoute -> {
            ServerHandler handler = switch (selectedRoute) {
                case DEBUG -> debug;
                case LIFECYCLE -> lifecycle;
                case LEGACY -> legacy;
                default -> null;
            };
            dispatch(payload, context, handler == null ? null : handler.handler, "server");
        });
        if (route == ServerRoute.UNKNOWN) {
            if (UNKNOWN_SERVER_ROUTE_LOGGED.compareAndSet(false, true))
                MoonStation14.LOGGER.warn("Dropping ghost-control server payload with no session owner: {}", payload.type().id());
            return;
        }
        if (route == ServerRoute.AMBIGUOUS) {
            if (AMBIGUOUS_SERVER_ROUTE_LOGGED.compareAndSet(false, true))
                MoonStation14.LOGGER.warn("Dropping ghost-control server payload with ambiguous session ownership: {}", payload.type().id());
            return;
        }
    }

    private static boolean owns(ServerHandler handler, ServerPlayer player) {
        if (handler == null) return false;
        try {
            return handler.ownerPredicate.test(player);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static ServerRoute selectServerRoute(boolean debugOwns, boolean lifecycleOwns, boolean legacyOwns) {
        int owners = (debugOwns ? 1 : 0) + (lifecycleOwns ? 1 : 0) + (legacyOwns ? 1 : 0);
        if (owners == 0) return ServerRoute.UNKNOWN;
        if (owners != 1) return ServerRoute.AMBIGUOUS;
        if (debugOwns) return ServerRoute.DEBUG;
        if (lifecycleOwns) return ServerRoute.LIFECYCLE;
        return ServerRoute.LEGACY;
    }

    static ServerRoute routeServerPayload(boolean debugOwns, boolean lifecycleOwns, boolean legacyOwns,
                                          java.util.function.Consumer<ServerRoute> handler) {
        ServerRoute route = selectServerRoute(debugOwns, lifecycleOwns, legacyOwns);
        if (route == ServerRoute.DEBUG || route == ServerRoute.LIFECYCLE || route == ServerRoute.LEGACY)
            handler.accept(route);
        return route;
    }

    enum ServerRoute { DEBUG, LIFECYCLE, LEGACY, UNKNOWN, AMBIGUOUS }

    private record ServerHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler,
                                 Predicate<ServerPlayer> ownerPredicate) { }

    private static void handleClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!supportedClientHarnessKind(payload)) {
            MobHarnessKind harnessKind = payload instanceof GhostControlPayloads.Begin begin
                    ? begin.harnessKind() : ((GhostControlPayloads.Snapshot) payload).harnessKind();
            MoonStation14.LOGGER.warn("Rejecting unsupported ghost-control harness kind on client: {} ({})",
                    harnessKind, payload.type().id());
            return;
        }
        dispatch(payload, context, clientHandler, "client");
    }

    static boolean supportedClientHarnessKind(CustomPacketPayload payload) {
        return true;
    }

    /** Pure tuple validation used by the client before retaining or acknowledging an offer. */
    public static boolean matchesOffer(long currentEpoch, int currentEntityId, MobHarnessKind currentKind,
                                       GhostControlPayloads.Offer offer) {
        return currentEpoch > 0 && currentEntityId >= 0 && currentKind != null && offer != null
                && offer.currentEpoch() == currentEpoch && offer.targetEntityId() != currentEntityId
                && offer.targetKind() != currentKind;
    }

    static boolean isConnectedServerPlayer(boolean isServerPlayer, boolean fakePlayer, boolean removed,
                                           boolean clientLevel, boolean hasServer, boolean listedPlayer) {
        return isServerPlayer && !fakePlayer && !removed && !clientLevel && hasServer && listedPlayer;
    }

    private static void dispatch(CustomPacketPayload payload, IPayloadContext context,
                                 BiConsumer<CustomPacketPayload, IPayloadContext> handler, String side) {
        if (handler == null) {
            MoonStation14.LOGGER.debug("Dropping ghost-control payload before {} handler installation: {}",
                    side, payload.type().id());
            return;
        }
        try {
            handler.accept(payload, context);
        } catch (Exception exception) {
            MoonStation14.LOGGER.error("Ghost-control {} payload handler failed; dropping payload {}", side,
                    payload.type().id(), exception);
        }
    }
}
