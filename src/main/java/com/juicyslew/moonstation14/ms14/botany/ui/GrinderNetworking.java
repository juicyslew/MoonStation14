package com.juicyslew.moonstation14.ms14.botany.ui;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class GrinderNetworking {
    private GrinderNetworking() { }
    private static volatile java.util.function.Consumer<GrinderSnapshot> clientConsumer = ignored -> { };
    public static void setClientConsumer(java.util.function.Consumer<GrinderSnapshot> consumer) { clientConsumer = consumer == null ? ignored -> { } : consumer; }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        NeoForge.EVENT_BUS.addListener(GrinderSessionService::serverTick);
        NeoForge.EVENT_BUS.addListener(GrinderSessionService::logout);
        NeoForge.EVENT_BUS.addListener(GrinderSessionService::serverStopped);
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(GrinderControlPayload.TYPE, GrinderControlPayload.STREAM_CODEC, (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player) || player.connection == null || player.getServer() == null
                            || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
                    GrinderSessionService.control(player, payload.control());
                })
                .playToClient(GrinderSnapshotPayload.TYPE, GrinderSnapshotPayload.STREAM_CODEC, (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) clientConsumer.accept(payload.snapshot());
                });
    }
    public static void send(ServerPlayer player, GrinderSnapshot snapshot) { PacketDistributor.sendToPlayer(player, new GrinderSnapshotPayload(snapshot)); }
}
