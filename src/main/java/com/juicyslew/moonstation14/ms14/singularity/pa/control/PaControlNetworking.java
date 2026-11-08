package com.juicyslew.moonstation14.ms14.singularity.pa.control;

import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import java.util.Objects;
import java.util.function.Consumer;

/** Common-side payload registration with an opt-in client receiver to keep client linkage lazy. */
public final class PaControlNetworking {
    private static volatile Consumer<PaControlSnapshot> clientReceiver=ignored->{ };
    private PaControlNetworking() { }
    /** Client initialization installs its snapshot consumer here; common registration never links client classes eagerly. */
    public static void installClientReceiver(Consumer<PaControlSnapshot> receiver){clientReceiver=Objects.requireNonNull(receiver);}
    /** Action request id is independent of the ACTIVATE_WORLD open correlation (the first snapshot echoes that correlation). */
    public static void requestAction(PaControlRequest request){PacketDistributor.sendToServer(request);}
    public static void sendOpenSnapshot(ServerPlayer p,PaControlSnapshot snapshot){PacketDistributor.sendToPlayer(p,snapshot);}
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event){
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PaControlSessionService::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PaControlSessionService::serverStopped);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(PaControlSessionService::serverTick);
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(PaControlRequest.TYPE,PaControlRequest.STREAM_CODEC,(request,ctx)->{
                    if(!(ctx.player() instanceof ServerPlayer p)||p.connection==null||p.getServer()==null||p.getServer().getPlayerList().getPlayer(p.getUUID())!=p)return;
                    PaControlSessionService.handle(p,request).ifPresent(s->sendOpenSnapshot(p,s));
                })
                .playToClient(PaControlSnapshot.TYPE,PaControlSnapshot.STREAM_CODEC,(snapshot,ctx)->clientReceiver.accept(snapshot));
    }
}
