package com.juicyslew.moonstation14.ms14.construction.network;

import com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldData;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Server-authoritative, read-only construction guidance transport. */
public final class ConstructionGuidanceNetworking {
    private ConstructionGuidanceNetworking() { }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(ConstructionGuidanceRequest.TYPE,ConstructionGuidanceRequest.STREAM_CODEC,(r,c)->{
                    if(c.player() instanceof ServerPlayer p && p.connection!=null
                            && p.getServer()!=null&&p.getServer().getPlayerList().getPlayer(p.getUUID())==p) handle(p,r);
                }).playToClient(ConstructionGuidanceState.TYPE,ConstructionGuidanceState.STREAM_CODEC,
                        (s,c)->ConstructionGuidanceState.deliverClient(s));
    }
    private static void handle(ServerPlayer actor, ConstructionGuidanceRequest request) {
        var authority=HandActorAuthority.resolve(actor).orElse(null);
        if(authority==null||authority.epoch()!=request.epoch())return;
        var body=authority.body();
        if(!(body.level() instanceof net.minecraft.server.level.ServerLevel level)||!level.getServer().isSameThread()
                ||!level.hasChunkAt(request.target())||!level.getWorldBorder().isWithinBounds(request.target())
                ||BodyBlockUseTargetProbe.probe(level,body,request.target(),request.face())==null){
            send(actor,request,body.getUUID(),null,null);return;
        }
        var record=ConstructionWorldData.existing(level).flatMap(data->data.get(request.target())).orElse(null);
        if(record==null||!record.hostBlock().equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(request.target()).getBlock()).toString())
                ||request.expectedHost()!=null&&!request.expectedHost().equals(record.hostId())) {
            send(actor,request,authority.body().getUUID(),null,null);return;
        }
        var content=guidance(record);
        send(actor,request,body.getUUID(),record.hostId(),content);
    }
    private static ConstructionGuidanceText.Content guidance(ConstructionWorldData.Record record) {
        return ConstructionGuidanceText.content(PrototypeRuntime.serverConstructionCatalog(),record);
    }
    private static void send(ServerPlayer p,ConstructionGuidanceRequest r,java.util.UUID body,java.util.UUID host,ConstructionGuidanceText.Content content){
        PacketDistributor.sendToPlayer(p,new ConstructionGuidanceState(r.epoch(),r.sequence(),body,r.target(),host,content==null?"":content.goal(),content==null?"":content.requirement()));
    }
}
