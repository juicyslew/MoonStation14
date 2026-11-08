package com.juicyslew.moonstation14.ms14.disposal.ui;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
public final class DisposalBuildNetworking {
 private DisposalBuildNetworking(){}
  public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event){event.registrar("2").executesOn(HandlerThread.MAIN)
   .playToServer(DisposalBuildSelection.TYPE,DisposalBuildSelection.STREAM_CODEC,(r,c)->{if(c.player() instanceof ServerPlayer p)DisposalBuildMenuService.select(p,r);})
   .playToServer(DisposalTubeConfigureRequest.TYPE,DisposalTubeConfigureRequest.STREAM_CODEC,(r,c)->{if(c.player() instanceof ServerPlayer p)DisposalBuildMenuService.configure(p,r);});}
}

