package com.juicyslew.moonstation14.ms14.atmos.visual.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Common payload registration; the client renderer installs its handler at runtime. */
public final class AtmosphereVisualNetworking {
    public static final String PROTOCOL_VERSION = "2";
    private static volatile Consumer<AtmosphereVisualPayload> clientHandler;

    private AtmosphereVisualNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).executesOn(HandlerThread.MAIN)
                .playToClient(AtmosphereVisualPayload.TYPE, AtmosphereVisualPayload.STREAM_CODEC,
                        (payload, context) -> handleClientPayload(payload))
                .playToServer(AtmosphereVisualResyncRequest.TYPE, AtmosphereVisualResyncRequest.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player)
                                AtmosphereVisualServerHooks.requestResync(player, payload);
                        });
    }

    public static void installClientHandler(Consumer<AtmosphereVisualPayload> handler) {
        clientHandler = handler;
    }

    private static void handleClientPayload(AtmosphereVisualPayload payload) {
        Consumer<AtmosphereVisualPayload> handler = clientHandler;
        if (handler != null) handler.accept(payload);
    }
}
