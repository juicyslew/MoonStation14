package com.juicyslew.moonstation14.ms14.radiation.control;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.Objects;
import java.util.function.Consumer;

/** Common-only registration for radiation collector control payloads. */
public final class RadiationCollectorControlNetworking {
    private static volatile Consumer<RadiationCollectorControlSnapshot> clientReceiver = ignored -> { };

    private RadiationCollectorControlNetworking() { }

    public static void installClientReceiver(Consumer<RadiationCollectorControlSnapshot> receiver) {
        clientReceiver = Objects.requireNonNull(receiver);
    }

    public static void request(RadiationCollectorControlRequest request) {
        PacketDistributor.sendToServer(request);
    }

    public static void sendSnapshot(ServerPlayer player, RadiationCollectorControlSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, snapshot);
    }

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(RadiationCollectorControlRequest.TYPE, RadiationCollectorControlRequest.STREAM_CODEC, (request, ctx) -> {
                    if (!(ctx.player() instanceof ServerPlayer player) || player.connection == null || player.getServer() == null
                            || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    RadiationCollectorControlService.handle(player, request).ifPresent(snapshot -> sendSnapshot(player, snapshot));
                })
                .playToClient(RadiationCollectorControlSnapshot.TYPE, RadiationCollectorControlSnapshot.STREAM_CODEC,
                        (snapshot, ctx) -> clientReceiver.accept(snapshot));
    }
}
