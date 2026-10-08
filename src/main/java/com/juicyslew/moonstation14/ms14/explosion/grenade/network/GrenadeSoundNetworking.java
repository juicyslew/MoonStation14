package com.juicyslew.moonstation14.ms14.explosion.grenade.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.Consumer;

public final class GrenadeSoundNetworking {
    private static volatile Consumer<GrenadeSoundPayload> clientHandler;
    private static volatile Consumer<ControlledSoundPayload> controlledClientHandler;
    private GrenadeSoundNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(GrenadeSoundPayload.TYPE, GrenadeSoundPayload.STREAM_CODEC, (payload, context) -> {
                    Consumer<GrenadeSoundPayload> handler = clientHandler;
                    if (handler != null) handler.accept(payload);
                }).playToClient(ControlledSoundPayload.TYPE, ControlledSoundPayload.STREAM_CODEC, (payload, context) -> {
                    Consumer<ControlledSoundPayload> handler = controlledClientHandler;
                    if (handler != null) handler.accept(payload);
                });
    }

    public static void installClientHandler(Consumer<GrenadeSoundPayload> handler) { clientHandler = handler; }
    public static void installControlledClientHandler(Consumer<ControlledSoundPayload> handler) { controlledClientHandler = handler; }

    public static void send(ServerPlayer player, GrenadeSoundPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
