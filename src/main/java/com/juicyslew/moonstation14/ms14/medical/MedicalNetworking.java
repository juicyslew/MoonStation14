package com.juicyslew.moonstation14.ms14.medical;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Play-channel registration for the analyzer's read-only stream and close intent. */
public final class MedicalNetworking {
    private MedicalNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(HealthAnalyzerWindowUpdate.TYPE, HealthAnalyzerWindowUpdate.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> HealthAnalyzerClientReceiver.receive(payload)))
                .playToServer(HealthAnalyzerCloseRequest.TYPE, HealthAnalyzerCloseRequest.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer actor)
                                context.enqueueWork(() -> HealthAnalyzerServer.closeUI(actor, payload.scannerId(),
                                        payload.epoch(), payload.actorId()));
                        });
    }
}
