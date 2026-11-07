package com.juicyslew.moonstation14.ms14.atmos.machine.ui;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.BiConsumer;

/** Main-thread transport for authenticated atmos-machine controls and authoritative snapshots. */
public final class AtmosMachineNetworking {
    private static volatile BiConsumer<AtmosMachineControlSnapshot, IPayloadContext> clientHandler;
    private static volatile BiConsumer<AtmosMachineSessionState, IPayloadContext> sessionClientHandler;
    private AtmosMachineNetworking() { }
    public static void installClientHandler(BiConsumer<AtmosMachineControlSnapshot, IPayloadContext> handler) { clientHandler = handler; }
    public static void installSessionClientHandler(BiConsumer<AtmosMachineSessionState, IPayloadContext> handler) { sessionClientHandler = handler; }
    public static void send(ServerPlayer player, AtmosMachineControlSnapshot snapshot) { PacketDistributor.sendToPlayer(player, snapshot); }
    public static void send(ServerPlayer player, AtmosMachineSessionState state) { PacketDistributor.sendToPlayer(player, state); }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(AtmosMachineControlRequest.TYPE, AtmosMachineControlRequest.STREAM_CODEC, (request, context) -> {
                    if (context.player() instanceof ServerPlayer actor)
                        PacketDistributor.sendToPlayer(actor, AtmosMachineMenuService.handle(actor, request));
                })
                .playToServer(AtmosMachineSessionClose.TYPE, AtmosMachineSessionClose.STREAM_CODEC, (request, context) -> {
                    if (context.player() instanceof ServerPlayer actor) AtmosMachineMenuService.close(actor, request);
                })
                .playToClient(AtmosMachineSessionState.TYPE, AtmosMachineSessionState.STREAM_CODEC, (state, context) -> {
                    var handler = sessionClientHandler;
                    if (handler != null) handler.accept(state, context);
                })
                .playToClient(AtmosMachineControlSnapshot.TYPE, AtmosMachineControlSnapshot.STREAM_CODEC, (snapshot, context) -> {
                    var handler = clientHandler;
                    if (handler != null) handler.accept(snapshot, context);
                });
    }
}
