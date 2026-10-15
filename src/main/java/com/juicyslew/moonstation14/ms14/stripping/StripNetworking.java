package com.juicyslew.moonstation14.ms14.stripping;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import java.util.function.Consumer;

/** Common payload registration; client delivery is bound by the client feature without server class loading. */
public final class StripNetworking {
    private static volatile Consumer<StripViewSnapshot> clientConsumer;
    private StripNetworking() { }
    public static void bindClient(Consumer<StripViewSnapshot> consumer) { clientConsumer=consumer; }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        NeoForge.EVENT_BUS.addListener(StripService::logout);
        NeoForge.EVENT_BUS.addListener(StripService::entityLeave);
        NeoForge.EVENT_BUS.addListener(StripService::stopped);
        NeoForge.EVENT_BUS.addListener(StripService::tick);
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(StripViewRequest.TYPE,StripViewRequest.STREAM_CODEC,(p,c)->{
                    if(c.player() instanceof ServerPlayer actor && actor.connection!=null
                            &&actor.getServer()!=null&&actor.getServer().getPlayerList().getPlayer(actor.getUUID())==actor)
                        c.enqueueWork(()->StripService.request(actor,p));
                })
                .playToServer(StripSlotAction.TYPE,StripSlotAction.STREAM_CODEC,(p,c)->{
                    if(c.player() instanceof ServerPlayer actor && actor.connection!=null
                            &&actor.getServer()!=null&&actor.getServer().getPlayerList().getPlayer(actor.getUUID())==actor)
                        c.enqueueWork(()->StripService.action(actor,p));
                })
                .playToClient(StripViewSnapshot.TYPE,StripViewSnapshot.STREAM_CODEC,(p,c)->{
                    if(FMLEnvironment.dist==Dist.CLIENT) c.enqueueWork(()->{var consumer=clientConsumer;if(consumer!=null)consumer.accept(p);});
                });
    }
}

