package com.juicyslew.moonstation14.ms14.ghost_roles.client;

import com.juicyslew.moonstation14.ms14.ghost_roles.network.GhostRolePayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.BiConsumer;

/** Client-side hookup seam; transport registrations are deliberately left to the shared bootstrap owner. */
public final class GhostRoleNetworking {
    private static BiConsumer<CustomPacketPayload, IPayloadContext> handler;
    private static Runnable snapshotRequest=()->{};
    private GhostRoleNetworking() { }
    public static void installClientHandler(BiConsumer<CustomPacketPayload,IPayloadContext> value){
        handler=value;
        com.juicyslew.moonstation14.ms14.ghost_roles.server.GhostRoleNetworking.installClientHandler(GhostRoleNetworking::receive);
    }
    public static void installSnapshotRequester(Runnable requester){snapshotRequest=requester==null?()->{}:requester;}
    public static void receive(CustomPacketPayload payload,IPayloadContext context){if(handler!=null)handler.accept(payload,context);}
    public static void send(CustomPacketPayload payload){PacketDistributor.sendToServer(payload);}
    public static void requestSnapshot(){snapshotRequest.run();}
    public static void installDefaultSnapshotRequester(){
        installSnapshotRequester(() -> {
            var context = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedGhostContext();
            if (context != null) send(new GhostRolePayloads.SnapshotRequest(context));
        });
    }
    /** Suitable as the handler supplied to the later payload registration adapter. */
    public static void handleClientPayload(CustomPacketPayload payload,IPayloadContext context){
        if(context.player()!=net.minecraft.client.Minecraft.getInstance().player)return;
        var current=com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.committedGhostContext();
        if(current==null)return;
        if(payload instanceof GhostRolePayloads.Snapshot snapshot && current.equals(snapshot.context()))GhostRoleMenuWindow.deliver(snapshot);
        else if(payload instanceof GhostRolePayloads.Result result && current.equals(result.context())
                && GhostRoleMenuWindow.deliver(result) && result.success() && result.teleportLook() != null)
            com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.applyAuthoritativeTeleportLook(
                    result.context(), result.teleportLook().yaw(), result.teleportLook().pitch());
    }
}
