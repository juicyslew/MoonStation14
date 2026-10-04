package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class DisposalUnitNetworking {
    private DisposalUnitNetworking(){}
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN).playToServer(DisposalUnitAction.TYPE,DisposalUnitAction.STREAM_CODEC,(request,context)->{
            if(context.player() instanceof ServerPlayer actor)DisposalUnitMenuService.apply(actor,request);
        });
    }
}
