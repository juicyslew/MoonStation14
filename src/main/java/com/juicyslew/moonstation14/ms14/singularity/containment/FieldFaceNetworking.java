package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.juicyslew.moonstation14.ms14.medical_observer.ObserverTracking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import java.util.*;
import java.util.function.Consumer;

/** Event subscriber registrar and change-driven field-face snapshot transport. */
public final class FieldFaceNetworking {
    private static final Map<ServerLevel,FieldFaceRecipientSnapshots> SENT = new WeakHashMap<>();
    private static volatile Consumer<FieldFaceSnapshotPayload> clientHandler;
    private FieldFaceNetworking() { }
    public static void register(IEventBus bus) { bus.register(FieldFaceNetworking.class); }
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").executesOn(HandlerThread.MAIN).playToClient(FieldFaceSnapshotPayload.TYPE,FieldFaceSnapshotPayload.STREAM_CODEC,(p,c)->{var h=clientHandler;if(h!=null)h.accept(p);});
    }
    public static void installClientHandler(Consumer<FieldFaceSnapshotPayload> handler) { clientHandler=handler; }
    @net.neoforged.bus.api.SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(ServerLevel level:event.getServer().getAllLevels()) {
            List<FieldFaceSnapshotPayload.Face> faces=FieldContainmentRuntime.currentFaces(level).stream().map(FieldFaceSnapshotPayload.Face::from).toList();
            FieldFaceRecipientSnapshots sent=SENT.computeIfAbsent(level,k->new FieldFaceRecipientSnapshots());
            for(ServerPlayer player:level.players()) {
                var pos=ObserverTracking.viewPosition(player);
                boolean relevant=faces.stream().anyMatch(f->{
                    int axis=f.axis().ordinal(), u=(axis+1)%3, v=(axis+2)%3;
                    double[] p={pos.x,pos.y,pos.z};
                    double[] low=new double[3],high=new double[3];
                    low[axis]=high[axis]=FieldGeometry.center(f.plane()); low[u]=FieldGeometry.center(f.minU());high[u]=FieldGeometry.center(f.maxU());low[v]=FieldGeometry.center(f.minV());high[v]=FieldGeometry.center(f.maxV());
                    double dx=Math.max(0,Math.max(low[0]-p[0],p[0]-high[0]));
                    double dy=Math.max(0,Math.max(low[1]-p[1],p[1]-high[1]));
                    double dz=Math.max(0,Math.max(low[2]-p[2],p[2]-high[2]));
                    return dx*dx+dy*dy+dz*dz<=128*128;
                });
                UUID id=player.getUUID();
                // A player with no entry is a new observer, even when level geometry is stable.
                sent.update(id,relevant,faces).ifPresent(desired -> PacketDistributor.sendToPlayer(player,
                        new FieldFaceSnapshotPayload(level.dimension().location(),desired)));
            }
        }
    }
    @net.neoforged.bus.api.SubscribeEvent public static void levelUnload(LevelEvent.Unload e) { if(e.getLevel() instanceof ServerLevel l)SENT.remove(l); }
    @net.neoforged.bus.api.SubscribeEvent public static void stop(ServerStoppedEvent e) { SENT.clear(); }
    @net.neoforged.bus.api.SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) removeRecipient(p.getUUID());
    }
    @net.neoforged.bus.api.SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) removeRecipient(p.getUUID());
    }
    private static void removeRecipient(UUID id) { for(FieldFaceRecipientSnapshots s:SENT.values())s.remove(id); }
}

