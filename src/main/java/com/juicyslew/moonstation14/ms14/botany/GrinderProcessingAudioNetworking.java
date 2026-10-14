package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import java.util.function.Consumer;

public final class GrinderProcessingAudioNetworking {
    private static volatile Consumer<GrinderProcessingSoundPayload> client = ignored -> {};
    private GrinderProcessingAudioNetworking() {}
    public static void setClientConsumer(Consumer<GrinderProcessingSoundPayload> consumer) { client = consumer == null ? ignored -> {} : consumer; }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN).playToClient(GrinderProcessingSoundPayload.TYPE, GrinderProcessingSoundPayload.STREAM_CODEC, (p,c) -> client.accept(p));
    }
    public static void broadcast(ServerLevel level, BlockPos location, GrinderProcessingSoundPayload payload) {
        if (level == null || location == null || payload == null || level.getServer() == null) return;
        double x = location.getX() + .5, y = location.getY() + .5, z = location.getZ() + .5;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (com.juicyslew.moonstation14.ms14.audio.BodyViewAudioAudience.canHear(level, player, x, y, z, 16))
                PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
