package com.juicyslew.moonstation14.ms14.ingestion;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Registration of ingestion-specific owner notifications. */
public final class IngestionNetworking {
    private IngestionNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(IngestionCommitted.TYPE, IngestionCommitted.STREAM_CODEC,
                        (payload, context) -> IngestionCommitted.deliverClient(payload));
    }
}

