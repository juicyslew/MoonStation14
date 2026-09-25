package com.juicyslew.moonstation14.ms14.player_body_control.network;

import com.juicyslew.moonstation14.MoonStation14;
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

/** Common play-phase registration and fail-closed dispatch for ghost control payloads. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class GhostControlNetworking {
    public static final String PROTOCOL_VERSION = "1";

    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> serverHandler;
    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> clientHandler;

    private GhostControlNetworking() { }

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN)
                .playToClient(GhostControlPayloads.Begin.TYPE, GhostControlPayloads.Begin.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToClient(GhostControlPayloads.Commit.TYPE, GhostControlPayloads.Commit.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToClient(GhostControlPayloads.Stop.TYPE, GhostControlPayloads.Stop.STREAM_CODEC,
                        GhostControlNetworking::handleClientPayload)
                .playToServer(GhostControlPayloads.Ready.TYPE, GhostControlPayloads.Ready.STREAM_CODEC,
                        GhostControlNetworking::handleServerPayload)
                .playToServer(GhostControlPayloads.Intent.TYPE, GhostControlPayloads.Intent.STREAM_CODEC,
                        GhostControlNetworking::handleServerPayload);
    }

    /** Installs a server controller explicitly; no handler is installed by registration itself. */
    public static void installServerHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        serverHandler = handler;
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
        dispatch(payload, context, serverHandler, "server");
    }

    private static void handleClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        dispatch(payload, context, clientHandler, "client");
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
