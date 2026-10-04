package com.juicyslew.moonstation14.ms14.disposal.ui;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Common payload registration; client callback invocation is guarded without referencing client classes. */
public final class DisposalConstructionNetworking {
    private DisposalConstructionNetworking(){}
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event){
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent logout)->DisposalConstructionMode.clearActor(logout.getEntity().getUUID()));
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(DisposalConstructionRequest.TYPE,DisposalConstructionRequest.STREAM_CODEC,(request,context)->{
                    if(!(context.player() instanceof ServerPlayer actor)||actor.connection==null||actor.getServer()==null
                            ||actor.getServer().getPlayerList().getPlayer(actor.getUUID())!=actor)return;
                    DisposalConstructionMode.handle(actor,request).ifPresent(state->{
                        if(actor.getServer().getPlayerList().getPlayer(actor.getUUID())==actor)PacketDistributor.sendToPlayer(actor,state);
                    });
                })
                .playToClient(DisposalConstructionState.TYPE,DisposalConstructionState.STREAM_CODEC,(state,context)->{
                    if(FMLEnvironment.dist==Dist.CLIENT)DisposalConstructionState.deliverClient(state);
                });
    }
}
