package com.juicyslew.moonstation14.ms14.power.cable.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

public final class CableVisualNetworking {
    private static volatile Consumer<CableVisualPayload> clientHandler;
    private CableVisualNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(CableVisualPayload.TYPE, CableVisualPayload.STREAM_CODEC,
                (payload, context) -> {
                    Consumer<CableVisualPayload> handler = clientHandler;
                    if (handler != null) context.enqueueWork(() -> handler.accept(payload));
                }).playToServer(CableVisualResyncRequest.TYPE, CableVisualResyncRequest.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player)
                                CableVisualServerHooks.requestResync(player, payload);
                        });
    }

    public static void installClientHandler(Consumer<CableVisualPayload> handler) { clientHandler = handler; }
}
