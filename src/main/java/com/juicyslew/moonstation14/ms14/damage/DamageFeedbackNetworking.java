package com.juicyslew.moonstation14.ms14.damage;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.Consumer;

public final class DamageFeedbackNetworking {
    private static volatile Consumer<DamageFeedbackPayload> clientHandler;
    private DamageFeedbackNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(DamageFeedbackPayload.TYPE, DamageFeedbackPayload.STREAM_CODEC,
                        (payload, context) -> {
                            Consumer<DamageFeedbackPayload> handler = clientHandler;
                            if (handler != null) handler.accept(payload);
                        });
    }
    public static void installClientHandler(Consumer<DamageFeedbackPayload> handler) { clientHandler = handler; }
}

