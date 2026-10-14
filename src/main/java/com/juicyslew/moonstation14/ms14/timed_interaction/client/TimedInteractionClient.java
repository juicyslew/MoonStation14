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
    private static ResourceLocation dimension;
    private static Object levelIdentity;
    private record Prediction(long sequence, UUID body, int duration, long created) { }
    private static final Map<Long,Prediction> predictions=new HashMap<>();
    private TimedInteractionClient() { }

    public static void accept(TimedInteractionSnapshot s) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || !s.dimension().equals(mc.level.dimension().location())) return;
        syncLevel(mc);
        Entity entity=mc.level.getEntity(s.bodyEntityId());
        if(entity!=null && !entity.getUUID().equals(s.bodyUuid())) return;
        Cached old=snapshots.get(s.bodyEntityId());
        if(old!=null && old.snapshot().bodyUuid().equals(s.bodyUuid())
                && (s.revision()<old.snapshot().revision()
                || (s.revision()==old.snapshot().revision() && s.serverTick()<old.snapshot().serverTick()))) return;
        if(old!=null && !old.snapshot().bodyUuid().equals(s.bodyUuid()) && entity==null) return;
        var owned = GhostControlClient.ownedCharacterForHud();
        if (TimedInteractionInventoryRefreshPolicy.shouldRefresh(old == null ? null : old.snapshot(), s,
                owned == null ? null : owned.getUUID(), mc.level.dimension().location())) {
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.externalTimedWorldCompletion(s.bodyUuid(), s.dimension());
        }
        s.views().forEach(v -> {
            Prediction prediction=predictions.get(v.originatingActionSequence());
            if(prediction!=null && TimedInteractionPredictionIdentity.matches(prediction.body(),prediction.sequence(),
                    s.bodyUuid(),v.originatingActionSequence())) predictions.remove(v.originatingActionSequence());
        });
        snapshots.put(s.bodyEntityId(),new Cached(s,mc.level.getGameTime(),entity==null?mc.level.getGameTime():-1));
    }

    private static void syncLevel(Minecraft mc) {
        if(levelIdentity!=mc.level) { snapshots.clear(); predictions.clear(); levelIdentity=mc.level; dimension=mc.level==null?null:mc.level.dimension().location(); }
    }
    public static void tick() {
        Minecraft mc=Minecraft.getInstance(); syncLevel(mc);
        if(mc.level==null) return;
        long now=mc.level.getGameTime();
        snapshots.entrySet().removeIf(e -> {
            Cached c=e.getValue(); Entity entity=mc.level.getEntity(e.getKey());
            if(entity!=null && !entity.getUUID().equals(c.snapshot().bodyUuid())) return true;
            if(entity!=null) {
                e.setValue(new Cached(c.snapshot(),c.receivedAt(),-1));
                return false;
            }
            long missing=c.entityMissingSince()<0?now:c.entityMissingSince();
            e.setValue(new Cached(c.snapshot(),c.receivedAt(),missing));
            return now-missing>200;
        });
        predictions.entrySet().removeIf(e -> now-e.getValue().created()>80 || !e.getValue().body().equals(
                GhostControlClient.ownedCharacterForHud()==null?null:GhostControlClient.ownedCharacterForHud().getUUID()));
    }
    public static void clear() { snapshots.clear(); clearPredictions(); dimension=null; levelIdentity=null; }
    public static void clearPredictions() { predictions.clear(); }
    public static void predictStart(long actionSequence, UUID bodyId, int durationTicks) {
        Minecraft mc=Minecraft.getInstance(); if(mc.level==null || bodyId==null || actionSequence<=0 || durationTicks<=0) return;
        var auth=snapshotView().values().stream().filter(s->s.bodyUuid().equals(bodyId)).findFirst().orElse(null);
        // Restricted for concurrent operations: avoid predicting a start that could be rejected as occupied.
        if(auth!=null && auth.views().stream().anyMatch(v->v.phase()==TimedOperation.Phase.ACTIVE)) return;
        predictions.put(actionSequence,new Prediction(actionSequence,bodyId,durationTicks,mc.level.getGameTime()));
    }
     public static void actionResult(long sequence, boolean accepted) {
         if(!accepted) predictions.remove(sequence);
    }
    public static Map<Long,TimedOperationView> provisionalView(UUID body) {
        long now=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        Map<Long,TimedOperationView> result=new HashMap<>();
        predictions.values().stream().filter(p->p.body().equals(body)).forEach(p->result.put(p.sequence(),new TimedOperationView(p.duration(),now-p.created())));
        return Map.copyOf(result);
    }
    public record TimedOperationView(int durationTicks,long elapsedTicks) { }
    public static Map<Integer,TimedInteractionSnapshot> snapshotView() {
        Map<Integer,TimedInteractionSnapshot> result=new HashMap<>(); snapshots.forEach((id,c)->result.put(id,c.snapshot())); return Map.copyOf(result);
    }
    public static double estimatedServerTick(int entityId,float partialTick) {
        Cached c=snapshots.get(entityId); Minecraft mc=Minecraft.getInstance();
        return c==null?0:c.snapshot().serverTick()+mc.level.getGameTime()-c.receivedAt()+partialTick;
    }
    public static float predictedProgress(TimedInteractionSnapshot.View v,double serverTick) {
        if(v.phase()==com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE)
            return TimedInteractionMath.progress(v.startTick(),v.durationTicks(),serverTick);
        return TimedInteractionMath.progress(v.startTick(),v.durationTicks(),v.terminalTick());
    }
}

