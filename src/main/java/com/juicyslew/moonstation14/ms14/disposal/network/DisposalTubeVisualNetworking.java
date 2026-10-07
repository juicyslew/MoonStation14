package com.juicyslew.moonstation14.ms14.disposal.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import java.util.function.Consumer;

/** Transport registration; server watch/mutation lifecycle is wired through DisposalTubeVisualServerHooks. */
public final class DisposalTubeVisualNetworking {
    private static volatile Consumer<DisposalTubeVisualPayload> clientHandler;
    private DisposalTubeVisualNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("2").executesOn(HandlerThread.MAIN).playToClient(DisposalTubeVisualPayload.TYPE,
                DisposalTubeVisualPayload.STREAM_CODEC,(payload,context)->{var handler=clientHandler;if(handler!=null)context.enqueueWork(()->handler.accept(payload));});
    }
    public static void installClientHandler(Consumer<DisposalTubeVisualPayload> handler){clientHandler=handler;}
    public static void send(ServerPlayer player, DisposalTubeVisualPayload payload){PacketDistributor.sendToPlayer(player,payload);}
    public static void sendTracking(ServerLevel level, ChunkPos chunk, DisposalTubeVisualPayload payload){PacketDistributor.sendToPlayersTrackingChunk(level,chunk,payload);}
}
