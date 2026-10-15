package com.juicyslew.moonstation14.ms14.devicenet.item;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Registered play-channel transport for configurator requests and authoritative snapshots. */
public final class NetworkConfiguratorNetworking {
    private NetworkConfiguratorNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(NetworkConfiguratorPayloads.Request.TYPE, NetworkConfiguratorPayloads.Request.STREAM_CODEC,
                        (p,c)->{if(c.player() instanceof ServerPlayer actor)c.enqueueWork(()->{var snapshot=NetworkConfiguratorServer.handle(actor,p.value());if(snapshot!=null)sendSnapshot(actor,snapshot);});})
                .playToClient(NetworkConfiguratorPayloads.Snapshot.TYPE, NetworkConfiguratorPayloads.Snapshot.STREAM_CODEC,
                        (p,c)->c.enqueueWork(()->{if(FMLEnvironment.dist==Dist.CLIENT)
                                com.juicyslew.moonstation14.ms14.devicenet.item.client.NetworkConfiguratorClientReceiver.receive(p.value());}))
                .playToServer(NetworkConfiguratorPayloads.ListRequest.TYPE,NetworkConfiguratorPayloads.ListRequest.STREAM_CODEC,
                        (p,c)->{if(c.player() instanceof ServerPlayer actor)c.enqueueWork(()->{var snapshot=NetworkConfiguratorListServer.apply(actor,p.value());if(snapshot!=null)sendListSnapshot(actor,snapshot);});})
                .playToClient(NetworkConfiguratorPayloads.ListSnapshot.TYPE,NetworkConfiguratorPayloads.ListSnapshot.STREAM_CODEC,
                         (p,c)->c.enqueueWork(()->{if(FMLEnvironment.dist==Dist.CLIENT)
                                 com.juicyslew.moonstation14.ms14.devicenet.item.client.NetworkConfiguratorClientReceiver.receive(p.value());}));
    }
    /** Public target-specific emission hook, also used by command responses. */
    public static void sendSnapshot(ServerPlayer actor, NetworkConfiguratorSnapshot snapshot) {
        if(actor!=null&&snapshot!=null)PacketDistributor.sendToPlayer(actor,new NetworkConfiguratorPayloads.Snapshot(snapshot));
    }
    /** Fresh list state emission hook for LIST activation and mode actions. */
    public static void sendListSnapshot(ServerPlayer actor, NetworkConfiguratorListSnapshot snapshot) {
        if(actor!=null&&snapshot!=null)PacketDistributor.sendToPlayer(actor,new NetworkConfiguratorPayloads.ListSnapshot(snapshot));
    }
}

