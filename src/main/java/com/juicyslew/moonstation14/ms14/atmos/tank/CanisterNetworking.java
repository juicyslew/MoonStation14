package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.entities.GasCanisterEntity;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class CanisterNetworking {
    private static volatile java.util.function.Consumer<CanisterSessionSnapshot> clientReceiver = ignored -> { };
    private CanisterNetworking() { }
    public static void installClientReceiver(java.util.function.Consumer<CanisterSessionSnapshot> receiver) { clientReceiver = java.util.Objects.requireNonNull(receiver); }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN)
                .playToServer(CanisterOpenRequest.TYPE, CanisterOpenRequest.STREAM_CODEC, (request,context)-> {
                    if(!(context.player() instanceof ServerPlayer player)||!authenticated(player))return;
                    var authority=HandActorAuthority.resolve(player).orElse(null);
                    if(authority==null||!(player.level() instanceof net.minecraft.server.level.ServerLevel level))return;
                    if(level.getEntity(request.machineId()) instanceof GasCanisterEntity entity)
                        CanisterSessionService.openForActor(player,entity,authority).ifPresent(s->send(player,s));
                })
                .playToServer(CanisterControlRequest.TYPE, CanisterControlRequest.STREAM_CODEC, (request,context)-> {
                    if(!(context.player() instanceof ServerPlayer player)||!authenticated(player))return;
                    CanisterSessionService.handle(player,request).ifPresent(s->send(player,s));
                })
                .playToServer(CanisterCloseRequest.TYPE, CanisterCloseRequest.STREAM_CODEC, (request,context)-> {
                    if(context.player() instanceof ServerPlayer player&&authenticated(player)) CanisterSessionService.close(player,request.sessionId());
                })
                .playToClient(CanisterSnapshotPayload.TYPE, CanisterSnapshotPayload.STREAM_CODEC, (payload,context)-> {
                     clientReceiver.accept(payload.snapshot());
                });
    }
    private static boolean authenticated(ServerPlayer p) { return p.connection!=null&&p.getServer()!=null&&p.getServer().getPlayerList().getPlayer(p.getUUID())==p; }
    public static void send(ServerPlayer p,CanisterSessionSnapshot s) { PacketDistributor.sendToPlayer(p,new CanisterSnapshotPayload(s)); }
}

