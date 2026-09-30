package com.juicyslew.moonstation14.ms14.chat.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.Consumer;

/** Common registration for server-authored speech and owner-only character identity. */
public final class LocalSpeechNetworking {
    private static volatile Consumer<CustomPacketPayload> clientHandler;
    private LocalSpeechNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToClient(LocalSpeechPayload.TYPE, LocalSpeechPayload.STREAM_CODEC, (payload, context) -> {
                    Consumer<CustomPacketPayload> handler = clientHandler;
                    if (handler != null) handler.accept(payload);
                })
                .playToClient(LocalCharacterIdentityPayload.TYPE, LocalCharacterIdentityPayload.STREAM_CODEC, (payload, context) -> {
                    Consumer<CustomPacketPayload> handler = clientHandler;
                    if (handler != null) handler.accept(payload);
                });
    }

    public static void installClientHandler(Consumer<CustomPacketPayload> handler) { clientHandler = handler; }

    public static void send(ServerPlayer player, LocalSpeechPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendIdentity(ServerPlayer player, LocalCharacterIdentityPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
