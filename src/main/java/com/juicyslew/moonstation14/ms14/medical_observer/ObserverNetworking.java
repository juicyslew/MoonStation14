package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class ObserverNetworking {
    private ObserverNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(ObserverPayloads.Request.TYPE,ObserverPayloads.Request.STREAM_CODEC,ObserverNetworking::request)
                .playToServer(ObserverPayloads.Intent.TYPE,ObserverPayloads.Intent.STREAM_CODEC,ObserverNetworking::intent)
                .playToClient(ObserverPayloads.Snapshot.TYPE,ObserverPayloads.Snapshot.STREAM_CODEC,(p,c)-> {
                    if (FMLEnvironment.dist == Dist.CLIENT) com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.deliverClient(p);
                });
    }
    public static void send(ObserverPayloads.Snapshot snapshot, ServerPlayer player) { PacketDistributor.sendToPlayer(player,snapshot); }
    private static void request(ObserverPayloads.Request p, IPayloadContext c) {
        if(!(c.player() instanceof ServerPlayer player))return;
        switch(p.kind()) {
            case QUERY -> { ObserverSystem.sendSnapshot(player, true); return; }
            case GHOST -> ObserverSystem.requestGhost(player,p.epoch());
            case RETURN -> ObserverSystem.requestReturn(player,p.epoch());
        }
        ObserverSystem.sendSnapshot(player);
    }
    private static void intent(ObserverPayloads.Intent p, IPayloadContext c) {
        if(c.player() instanceof ServerPlayer player) ObserverSystem.submitIntent(player,p.epoch(),new ObserverIntent(p.strafe(),p.forward(),p.vertical(),p.sprint(),p.yaw()));
    }
}
