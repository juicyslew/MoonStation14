package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.ui;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.function.BiConsumer;

public final class AirAlarmNetworking {
    private static volatile BiConsumer<AirAlarmControlSnapshot, IPayloadContext> clientHandler;
    private AirAlarmNetworking() { }
    public static void installClientHandler(BiConsumer<AirAlarmControlSnapshot, IPayloadContext> handler) {
        clientHandler = handler;
    }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(AirAlarmControlRequest.TYPE, AirAlarmControlRequest.STREAM_CODEC, (request, context) -> {
                    if (context.player() instanceof ServerPlayer actor)
                        PacketDistributor.sendToPlayer(actor, AirAlarmMenuService.apply(actor, request));
                })
                .playToClient(AirAlarmControlSnapshot.TYPE, AirAlarmControlSnapshot.STREAM_CODEC, (snapshot, context) -> {
                    var handler = clientHandler;
                    if (handler != null) handler.accept(snapshot, context);
                });
    }
}
