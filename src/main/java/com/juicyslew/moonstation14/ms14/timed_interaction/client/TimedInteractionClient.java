package com.juicyslew.moonstation14.ms14.timed_interaction.client;

import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSnapshot;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;

/** Client-only replicated presentation with a level-clock estimate of the server clock. */
@OnlyIn(Dist.CLIENT)
public final class TimedInteractionClient {
    private record Cached(TimedInteractionSnapshot snapshot, long receivedAt, long entityMissingSince) { }
    private static final Map<Integer,Cached> snapshots=new HashMap<>();
    private static final TimedInteractionReconciler reconciler = new TimedInteractionReconciler();
    private static ResourceLocation dimension;
    private static Object levelIdentity;
    private TimedInteractionClient() { }

    public static void accept(TimedInteractionSnapshot s) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || !s.dimension().equals(mc.level.dimension().location())) return;
        syncLevel(mc);
        Entity entity=mc.level.getEntity(s.bodyEntityId());
        if(entity!=null && !entity.getUUID().equals(s.bodyUuid())) return;
        var owned = GhostControlClient.ownedCharacterForHud();
        Cached old = snapshots.get(s.bodyEntityId());
        var acceptance = reconciler.accept(s, mc.level.dimension().location(), entity == null ? null : entity.getUUID(),
                owned == null ? null : owned.getUUID(), mc.level.dimension().location());
        if (!acceptance.accepted()) return;
        if (acceptance.refreshInventory()) {
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.externalTimedWorldCompletion(s.bodyUuid(), s.dimension());
        }
        long now = mc.level.getGameTime();
        long receivedAt = TimedInteractionClockPolicy.receivedAt(old == null ? null : old.snapshot(),
                old == null ? now : old.receivedAt(), s, now);
        snapshots.put(s.bodyEntityId(),new Cached(s,receivedAt,entity==null?now:-1));
    }

    private static void syncLevel(Minecraft mc) {
        if(levelIdentity!=mc.level) { snapshots.clear(); reconciler.clear(); levelIdentity=mc.level; dimension=mc.level==null?null:mc.level.dimension().location(); }
    }
    public static void tick() {
        Minecraft mc=Minecraft.getInstance(); syncLevel(mc);
        if(mc.level==null) return;
        long now=mc.level.getGameTime();
        snapshots.entrySet().removeIf(e -> {
            Cached c=e.getValue(); Entity entity=mc.level.getEntity(e.getKey());
            if(entity!=null && !entity.getUUID().equals(c.snapshot().bodyUuid())) {
                reconciler.forgetSnapshot(e.getKey());
                return true;
            }
            if(entity!=null) {
                e.setValue(new Cached(c.snapshot(),c.receivedAt(),-1));
                return false;
            }
            long missing=c.entityMissingSince()<0?now:c.entityMissingSince();
            e.setValue(new Cached(c.snapshot(),c.receivedAt(),missing));
            boolean expired = now-missing>200;
            if (expired) reconciler.forgetSnapshot(e.getKey());
            return expired;
        });
        var owned = GhostControlClient.ownedCharacterForHud();
        reconciler.tick(now, owned == null ? null : owned.getUUID());
    }
    public static void clear() { snapshots.clear(); reconciler.clear(); dimension=null; levelIdentity=null; }
    public static void clearPredictions() { reconciler.clearPredictions(); }
    public static void predictStart(long actionSequence, UUID bodyId, int durationTicks) {
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null || bodyId==null || actionSequence<=0 || durationTicks<=0) return;
        reconciler.predictStart(actionSequence, bodyId, mc.level.dimension().location(), durationTicks, mc.level.getGameTime());
    }
     public static void actionResult(long sequence, boolean accepted) {
          reconciler.actionResult(sequence, accepted);
    }
    public static Map<Long,TimedOperationView> provisionalView(UUID body) {
        long now=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        Map<Long,TimedOperationView> result=new HashMap<>();
        reconciler.provisionalView(body, now).forEach((sequence, view) -> result.put(sequence,
                new TimedOperationView(view.durationTicks(), view.elapsedTicks())));
        return Map.copyOf(result);
    }
    public record TimedOperationView(int durationTicks,long elapsedTicks) { }
    public static Map<Integer,TimedInteractionSnapshot> snapshotView() {
        return reconciler.snapshots();
    }
    public static double estimatedServerTick(int entityId,float partialTick) {
        Cached c=snapshots.get(entityId); Minecraft mc=Minecraft.getInstance();
        return c==null?0:TimedInteractionClockPolicy.estimatedServerTick(c.snapshot().serverTick(),
                c.receivedAt(),mc.level.getGameTime(),partialTick);
    }
    public static float predictedProgress(TimedInteractionSnapshot.View v,double serverTick) {
        if(v.phase()==com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE)
            return TimedInteractionMath.progress(v.startTick(),v.durationTicks(),serverTick);
        return TimedInteractionMath.progress(v.startTick(),v.durationTicks(),v.terminalTick());
    }
}

