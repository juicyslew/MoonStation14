package com.juicyslew.moonstation14.ms14.tesla;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class TeslaArcNetworking {
    private static volatile java.util.function.Consumer<TeslaArcPayload> clientHandler;
    private static volatile java.util.function.Consumer<TeslaJumpPulsePayload> jumpHandler;
    private TeslaArcNetworking() { }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("3").executesOn(HandlerThread.MAIN);
        registrar.playToClient(TeslaArcPayload.TYPE,
                TeslaArcPayload.STREAM_CODEC, (payload, context) -> { var handler=clientHandler; if(handler!=null) handler.accept(payload); });
        registrar.playToClient(TeslaJumpPulsePayload.TYPE,
                TeslaJumpPulsePayload.STREAM_CODEC, (payload, context) -> { var handler=jumpHandler; if(handler!=null) handler.accept(payload); });
    }
    public static void installClientHandler(java.util.function.Consumer<TeslaArcPayload> handler) { clientHandler=handler; }
    public static void installJumpHandler(java.util.function.Consumer<TeslaJumpPulsePayload> handler) { jumpHandler=handler; }
    public static void sendJump(ServerLevel level, Vec3 at) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(at)),
                new TeslaJumpPulsePayload(level.dimension().location(), at.x, at.y, at.z));
    }
    public static void send(ServerLevel level, Vec3 from, Vec3 to, long seed, double seconds) {
        send(level,from,to,seed,seconds,0);
    }
    public static void send(ServerLevel level, Vec3 from, Vec3 to, long seed, double seconds, double halfWidth) {
        var packet=new TeslaArcPayload(level.dimension().location(),from.x,from.y,from.z,to.x,to.y,to.z,seed,(float)seconds,(float)halfWidth);
        PacketDistributor.sendToPlayersNear(level,null,from.x,from.y,from.z,64,packet);
    }
}
