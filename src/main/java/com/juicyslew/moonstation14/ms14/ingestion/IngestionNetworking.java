package com.juicyslew.moonstation14.ms14.ingestion;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.minecraft.server.level.ServerPlayer;

/** Registration of ingestion-specific owner notifications. */
public final class IngestionNetworking {
    private static final java.util.Map<ServerPlayer, BodyOralSequenceGate> ORAL_GATES = new java.util.WeakHashMap<>();
    private IngestionNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(BodyOralIngestionRequest.TYPE, BodyOralIngestionRequest.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) handle(payload, player);
                        })
                .playToClient(IngestionCommitted.TYPE, IngestionCommitted.STREAM_CODEC,
                        (payload, context) -> IngestionCommitted.deliverClient(payload));
    }

    private static void handle(BodyOralIngestionRequest request, ServerPlayer player) {
        var snapshot = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.resolve(player).orElse(null);
        if (snapshot == null || !snapshot.body().getUUID().equals(request.bodyId())
                || snapshot.identity().epoch() != request.epoch()
                || !com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.revalidate(player, snapshot)
                || !OralAccess.handless(snapshot.body())
                || !ORAL_GATES.computeIfAbsent(player, ignored -> new BodyOralSequenceGate())
                        .admit(request.epoch(), request.sequence())) return;
        if (request.source() == BodyOralIngestionRequest.Source.PUDDLE)
            BodyTimedIngestionService.startPuddle(player, request.puddlePos(), null, 0, request.sequence());
        else BodyTimedIngestionService.startWorld(player, request.itemId(), null, 0, request.sequence());
    }
}
