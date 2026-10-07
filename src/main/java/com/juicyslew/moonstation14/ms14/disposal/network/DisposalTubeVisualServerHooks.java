package com.juicyslew.moonstation14.ms14.disposal.network;

import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Watch-scoped snapshots and mutation refreshes; empty replacement snapshots remove invalidated records. */
public final class DisposalTubeVisualServerHooks {
    private static final int BATCH=256;
    private DisposalTubeVisualServerHooks() { }
    public static void install() { DisposalTubeWorldData.setChangeListener(DisposalTubeVisualServerHooks::changed); }
    @net.neoforged.bus.api.SubscribeEvent public static void watched(ChunkWatchEvent.Sent event) {
        if(event.getLevel() instanceof ServerLevel level && event.getPlayer()!=null) send(level,event.getPlayer(),event.getChunk().getPos());
    }
    @net.neoforged.bus.api.SubscribeEvent public static void unwatched(ChunkWatchEvent.UnWatch event) {
        if(event.getLevel() instanceof ServerLevel level && event.getPlayer()!=null){ChunkPos chunk=event.getPos();
            DisposalTubeVisualNetworking.send(event.getPlayer(),new DisposalTubeVisualPayload(level.dimension().location(),chunk.x,chunk.z,true,List.of()));}
    }
    private static void changed(ServerLevel level, DisposalTubeWorldData.Change change) {
        ChunkPos chunk=new ChunkPos(change.record().pos());
        if(!level.getChunkSource().hasChunk(chunk.x,chunk.z))return;
        var records=DisposalTubeWorldData.existing(level).map(data->data.inChunk(chunk.x,chunk.z)).orElse(List.of());
        // Mutation notifies only players tracking this already loaded host chunk.
        sendTracking(level,chunk,records);
    }
    private static void send(ServerLevel level,ServerPlayer player,ChunkPos chunk) {
        var all=DisposalTubeWorldData.existing(level).map(data->data.inChunk(chunk.x,chunk.z)).orElse(List.of());
        if(all.isEmpty()){DisposalTubeVisualNetworking.send(player,new DisposalTubeVisualPayload(level.dimension().location(),chunk.x,chunk.z,true,List.of()));return;}
        for(int start=0;start<all.size();start+=BATCH) DisposalTubeVisualNetworking.send(player,new DisposalTubeVisualPayload(level.dimension().location(),chunk.x,chunk.z,start==0,all.subList(start,Math.min(start+BATCH,all.size()))));
    }
    private static void sendTracking(ServerLevel level,ChunkPos chunk,List<com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord> all){
        if(all.isEmpty()){DisposalTubeVisualNetworking.sendTracking(level,chunk,new DisposalTubeVisualPayload(level.dimension().location(),chunk.x,chunk.z,true,List.of()));return;}
        for(int start=0;start<all.size();start+=BATCH) DisposalTubeVisualNetworking.sendTracking(level,chunk,
                new DisposalTubeVisualPayload(level.dimension().location(),chunk.x,chunk.z,start==0,all.subList(start,Math.min(start+BATCH,all.size()))));
    }
}
