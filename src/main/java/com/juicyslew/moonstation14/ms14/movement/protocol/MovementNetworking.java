package com.juicyslew.moonstation14.ms14.movement.protocol;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.BiConsumer;

/** Common play-phase registration and fail-closed dispatch for movement protocol payloads. */
public final class MovementNetworking {
    public static final String PROTOCOL_VERSION = "1";

    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> serverHandler;
    private static volatile BiConsumer<CustomPacketPayload, IPayloadContext> clientHandler;

    private MovementNetworking() {
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN)
                .playToServer(MovementPayloads.Acknowledge.TYPE, MovementPayloads.Acknowledge.STREAM_CODEC,
                        MovementNetworking::handleServerPayload)
                .playToServer(MovementPayloads.DisableAcknowledge.TYPE, MovementPayloads.DisableAcknowledge.STREAM_CODEC,
                        MovementNetworking::handleServerPayload)
                .playToServer(MovementPayloads.ResumeAcknowledge.TYPE, MovementPayloads.ResumeAcknowledge.STREAM_CODEC,
                        MovementNetworking::handleServerPayload)
                .playToServer(MovementPayloads.Intent.TYPE, MovementPayloads.Intent.STREAM_CODEC,
                        MovementNetworking::handleServerPayload)
                .playToClient(MovementPayloads.Begin.TYPE, MovementPayloads.Begin.STREAM_CODEC,
                        MovementNetworking::handleClientPayload)
                .playToClient(MovementPayloads.Commit.TYPE, MovementPayloads.Commit.STREAM_CODEC,
                        MovementNetworking::handleClientPayload)
                .playToClient(MovementPayloads.Disable.TYPE, MovementPayloads.Disable.STREAM_CODEC,
                        MovementNetworking::handleClientPayload)
                .playToClient(MovementPayloads.ResumeVanilla.TYPE, MovementPayloads.ResumeVanilla.STREAM_CODEC,
                        MovementNetworking::handleClientPayload)
                .playToClient(MovementPayloads.Snapshot.TYPE, MovementPayloads.Snapshot.STREAM_CODEC,
                        MovementNetworking::handleClientPayload);
    }

    /** Installs the server's packet consumer; it must independently validate session/authentication state. */
    public static void installServerHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        serverHandler = handler;
    }

    /** Installs the client's packet consumer. No ownership transition is performed by this dispatcher. */
    public static void installClientHandler(BiConsumer<CustomPacketPayload, IPayloadContext> handler) {
        clientHandler = handler;
    }

    /** Sends a movement packet only from a physical client; this common class has no client-class references. */
    public static void sendToServer(CustomPacketPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            MoonStation14.LOGGER.warn("Dropping clientbound movement send outside the client distribution");
            return;
        }
        try {
            PacketDistributor.sendToServer(payload);
        } catch (RuntimeException exception) {
            MoonStation14.LOGGER.error("Failed to send movement payload to server", exception);
        }
    }

    /** Sends a movement packet to one server player. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException exception) {
            MoonStation14.LOGGER.error("Failed to send movement payload to player", exception);
        }
    }

    private static void handleServerPayload(CustomPacketPayload payload, IPayloadContext context) {
        if (!MovementStartupGate.enabledForServer()) {
            MoonStation14.LOGGER.debug("Dropping movement payload while experimental movement is disabled: {}",
                    payload.type().id());
            return;
        }
        if (!(context.player() instanceof ServerPlayer)) {
            MoonStation14.LOGGER.warn("Dropping movement payload without a server player: {}", payload.type().id());
            return;
        }
        dispatch(payload, context, serverHandler, "server");
    }

    private static void handleClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        dispatch(payload, context, clientHandler, "client");
    }

    private static void dispatch(CustomPacketPayload payload, IPayloadContext context,
                                 BiConsumer<CustomPacketPayload, IPayloadContext> handler, String side) {
        if (handler == null) {
            MoonStation14.LOGGER.debug("Dropping movement payload before {} handler installation: {}", side,
                    payload.type().id());
            return;
        }
        try {
            handler.accept(payload, context);
        } catch (Exception exception) {
            MoonStation14.LOGGER.error("Movement {} payload handler failed; dropping payload {}", side,
                    payload.type().id(), exception);
        }
    }
}
