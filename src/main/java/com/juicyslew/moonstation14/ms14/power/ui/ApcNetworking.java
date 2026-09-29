package com.juicyslew.moonstation14.ms14.power.ui;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.function.BiConsumer;

/** APC payload registration with the client response consumer installed only from the client entrypoint. */
public final class ApcNetworking {
    private static volatile BiConsumer<ApcToggleResponse, IPayloadContext> clientHandler;

    private ApcNetworking() { }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(ApcToggleRequest.TYPE, ApcToggleRequest.STREAM_CODEC, (request, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        PacketDistributor.sendToPlayer(player, ApcMenuService.apply(player, request));
                })
                .playToClient(ApcToggleResponse.TYPE, ApcToggleResponse.STREAM_CODEC, (response, context) -> {
                    BiConsumer<ApcToggleResponse, IPayloadContext> handler = clientHandler;
                    if (handler != null) handler.accept(response, context);
                });
    }

    public static void installClientHandler(BiConsumer<ApcToggleResponse, IPayloadContext> handler) {
        clientHandler = handler;
    }
}
