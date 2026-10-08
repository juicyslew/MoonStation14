package com.juicyslew.moonstation14.ms14.timed_interaction;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Server-authoritative callback runtime; callbacks never enter entity attachment data. */
public final class TimedInteractionSystem {
    public enum Completion { REPEAT, STOP, DENIED, RECOVERY_REQUIRED }
    private record Key(UUID body, long id) { }
    private record Context(int duration, double damageThreshold, BooleanSupplier valid, Supplier<Completion> completion, Runnable cancelled, ServerLevel level, net.minecraft.world.phys.Vec3 start) { }
    private record Presentation(long origin, ServerPlayer owner, Optional<net.minecraft.core.BlockPos> worldTarget) { }
    private static final Map<Key, Context> CALLBACKS = new HashMap<>();
    private static final Map<Key, Presentation> PRESENTATION = new HashMap<>();
    private static final Map<UUID, LivingEntity> ACTORS = new HashMap<>();
    private static Thread serverThread;
    private TimedInteractionSystem() { }
    private static void checkThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Timed interactions require server thread");
        if (serverThread == null) serverThread = Thread.currentThread();
        if (serverThread != Thread.currentThread()) throw new IllegalStateException("Timed interactions require server thread");
    }
    private static long now(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            BooleanSupplier valid, Supplier<Completion> completion) {
        return start(body, owner, originSequence, kind, sourceKey, durationTicks, breakDamage, breakMove, 1.0, valid, completion, Optional.empty(), () -> { });
    }
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            BooleanSupplier valid, Supplier<Completion> completion, Runnable cancelled) {
        return start(body, owner, originSequence, kind, sourceKey, durationTicks, breakDamage, breakMove, 1.0, valid, completion, Optional.empty(), cancelled);
    }
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            BooleanSupplier valid, Supplier<Completion> completion, Optional<net.minecraft.core.BlockPos> worldTarget, Runnable cancelled) {
        return start(body, owner, originSequence, kind, sourceKey, durationTicks, breakDamage, breakMove, 1.0, valid, completion, worldTarget, cancelled);
    }
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            double damageThreshold, BooleanSupplier valid, Supplier<Completion> completion) {
        return start(body, owner, originSequence, kind, sourceKey, durationTicks, breakDamage, breakMove, damageThreshold, valid, completion, Optional.empty(), () -> { });
    }
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            double damageThreshold, BooleanSupplier valid, Supplier<Completion> completion,
            Optional<net.minecraft.core.BlockPos> worldTarget, Runnable cancelled) {
        Objects.requireNonNull(body); Objects.requireNonNull(kind); Objects.requireNonNull(sourceKey); Objects.requireNonNull(valid); Objects.requireNonNull(completion);
        Objects.requireNonNull(cancelled);
        if (!Double.isFinite(damageThreshold) || damageThreshold <= 0) return TimedInteractionEngine.StartResult.DENIED;
        if (!(body.level() instanceof ServerLevel level)) return TimedInteractionEngine.StartResult.DENIED;
        checkThread(level);
        if (body.isRemoved() || !body.isAlive() || durationTicks < 1) return TimedInteractionEngine.StartResult.DENIED;
        TimedOperationState state = body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        long current = now(level);
        TimedInteractionEngine.Start result = TimedInteractionEngine.start(state, kind, sourceKey, current, durationTicks, breakDamage, breakMove);
        if (result.result() == TimedInteractionEngine.StartResult.STARTED && state.operations().size() >= 32) return TimedInteractionEngine.StartResult.DENIED;
        body.setData(ModDataAttachments.TIMED_OPERATIONS.get(), result.state());
        UUID uuid = body.getUUID(); ACTORS.put(uuid, body);
        if (result.result() == TimedInteractionEngine.StartResult.STARTED) {
            Key key = new Key(uuid, result.operation().id());
            CALLBACKS.put(key, new Context(durationTicks, damageThreshold, valid, completion, cancelled, level, body.position()));
            PRESENTATION.put(key, new Presentation(originSequence, owner, worldTarget == null ? Optional.empty() : worldTarget));
        }
        cleanupCallbacks(body, result.state());
        publish(body);
        return result.result();
    }
    public static boolean cancelDuplicate(LivingEntity body, String kind, String sourceKey) {
        if (!(body.level() instanceof ServerLevel level)) return false;
        checkThread(level);
        TimedOperationState state = body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()); if (state == null) return false;
        boolean found = state.operations().stream().anyMatch(o -> o.phase()==TimedOperation.Phase.ACTIVE && o.kind().equals(kind) && o.sourceKey().equals(sourceKey));
        if (!found) return false;
        var result = TimedInteractionEngine.start(state, kind, sourceKey, now(level), 1, false, false);
        body.setData(ModDataAttachments.TIMED_OPERATIONS.get(), result.state()); publish(body); cleanupCallbacks(body, result.state()); cleanupActor(body, result.state()); return true;
    }
    public static void damage(LivingEntity body, double typedDamage) {
        if (!(body.level() instanceof ServerLevel level)) return;
        checkThread(level);
        TimedOperationState state=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()); if(state==null)return;
        var next=state; long current=now(level);
        for(long id:TimedInteractionDamageSelector.select(state, typedDamage, op -> {
            Context context = CALLBACKS.get(new Key(body.getUUID(), op.id()));
            return context == null ? Double.POSITIVE_INFINITY : context.damageThreshold();
        })) next=TimedInteractionEngine.cancel(next,id,current);
        if(next!=state){body.setData(ModDataAttachments.TIMED_OPERATIONS.get(),next);publish(body);cleanupCallbacks(body,next);cleanupActor(body,next);}
    }
    public static void onPostServerTick(ServerTickEvent.Post event) {
        for (LivingEntity body : List.copyOf(ACTORS.values())) {
            if (!(body.level() instanceof ServerLevel level) || level.getServer() != event.getServer()) continue;
            checkThread(level); TimedOperationState state=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()); if(state==null){ACTORS.remove(body.getUUID());continue;}
            long current=now(level); TimedOperationState next=state;
            for (TimedOperation op : state.operations()) if(op.phase()==TimedOperation.Phase.ACTIVE) {
                Key key=new Key(body.getUUID(),op.id()); Context c=CALLBACKS.get(key);
                boolean invalid=c==null || c.level()!=level || body.isRemoved() || !body.isAlive();
                try { if (!invalid) invalid=!c.valid().getAsBoolean(); } catch (RuntimeException ex) { invalid=true; }
                if (!invalid && op.breakOnMove() && c.start().distanceTo(body.position()) > 0.3) invalid=true;
                if(invalid) next=TimedInteractionEngine.cancel(next,op.id(),current);
            }
            var advanced=TimedInteractionEngine.advance(next,current); next=advanced.state();
            for(TimedOperation due:advanced.completed()) {
                Key key=new Key(body.getUUID(),due.id()); Context c=CALLBACKS.get(key); if(c==null)continue;
                Completion outcome=Completion.DENIED;
                try { outcome=c.valid().getAsBoolean()?c.completion().get():Completion.DENIED; } catch(RuntimeException ignored) { }
                if(outcome==Completion.REPEAT) next=TimedInteractionEngine.repeat(next,due.id(),current,c.duration());
                else {
                    CALLBACKS.remove(key);
                    if (outcome == Completion.DENIED) try { c.cancelled().run(); } catch (RuntimeException ignored) { }
                }
            }
            next=TimedInteractionEngine.prune(next,current);
             if(next!=state){body.setData(ModDataAttachments.TIMED_OPERATIONS.get(),next);publish(body);cleanupCallbacks(body,next);cleanupActor(body,next);}
        }
    }
    private static void cleanupCallbacks(LivingEntity body, TimedOperationState state) {
        Set<Long> ids=new HashSet<>(); for(TimedOperation op:state.operations()) if(op.phase()==TimedOperation.Phase.ACTIVE) ids.add(op.id());
        for (var entry : List.copyOf(CALLBACKS.entrySet())) if (entry.getKey().body().equals(body.getUUID()) && !ids.contains(entry.getKey().id())) {
            boolean cancelled = state.operations().stream().anyMatch(o -> o.id() == entry.getKey().id() && o.phase() == TimedOperation.Phase.CANCELLED);
            CALLBACKS.remove(entry.getKey());
            if (cancelled) try { entry.getValue().cancelled().run(); } catch (RuntimeException ignored) { }
        }
        PRESENTATION.keySet().removeIf(k->k.body().equals(body.getUUID())&&state.operations().stream().noneMatch(o->o.id()==k.id()));
    }
    private static void cleanupActor(LivingEntity body, TimedOperationState state) { if(state.operations().isEmpty()) ACTORS.remove(body.getUUID()); }
    private static void publish(LivingEntity body) {
        if (!(body.level() instanceof ServerLevel level)) return;
        TimedOperationState state=body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        var packet=snapshot(body);
        PacketDistributor.sendToPlayersTrackingEntity(body,packet);
        Set<ServerPlayer> owners=new HashSet<>(); PRESENTATION.forEach((key,value)->{if(key.body().equals(body.getUUID())&&value.owner()!=null&&!value.owner().isRemoved())owners.add(value.owner());});
        for(ServerPlayer owner:owners) PacketDistributor.sendToPlayer(owner,packet);
    }
    /** Returns the same authoritative presentation snapshot sent to trackers and operation owners. */
    public static TimedInteractionSnapshot snapshot(LivingEntity body) {
        Objects.requireNonNull(body);
        if (!(body.level() instanceof ServerLevel level)) throw new IllegalArgumentException("Timed interaction snapshots require a server entity");
        checkThread(level);
        TimedOperationState state=body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        return new TimedInteractionSnapshot(level.dimension().location(),body.getId(),body.getUUID(),state.revision(),now(level),views(body,state));
    }
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity body) || !(event.getLevel() instanceof ServerLevel level)) return;
        checkThread(level); TimedOperationState state=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
         if(state!=null){long current=now(level);TimedOperationState next=state;for(TimedOperation op:state.operations())if(op.phase()==TimedOperation.Phase.ACTIVE)next=TimedInteractionEngine.cancel(next,op.id(),current);body.setData(ModDataAttachments.TIMED_OPERATIONS.get(),next);publish(body);cleanupCallbacks(body,next);}
        UUID id=body.getUUID(); CALLBACKS.keySet().removeIf(k->k.body().equals(id)); PRESENTATION.keySet().removeIf(k->k.body().equals(id)); ACTORS.remove(id);
    }
    public static void onLogout(LivingEntity body) {
        if (!(body.level() instanceof ServerLevel level)) return; checkThread(level);
        UUID id=body.getUUID(); TimedOperationState state=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
        if (state != null) {
            TimedOperationState next=state;
            for (TimedOperation op : state.operations()) if (op.phase()==TimedOperation.Phase.ACTIVE) next=TimedInteractionEngine.cancel(next,op.id(),now(level));
            body.setData(ModDataAttachments.TIMED_OPERATIONS.get(),next); publish(body); cleanupCallbacks(body,next);
        }
        CALLBACKS.keySet().removeIf(k->k.body().equals(id)); PRESENTATION.keySet().removeIf(k->k.body().equals(id));
        PRESENTATION.entrySet().removeIf(e->e.getValue().owner()==body);
        if(body.hasData(ModDataAttachments.TIMED_OPERATIONS.get())) publish(body);
        ACTORS.remove(id);
    }
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof LivingEntity body && body.level() instanceof ServerLevel level){checkThread(level);if(body.hasData(ModDataAttachments.TIMED_OPERATIONS.get()))sendSnapshot(body,observer);}
    }
    private static List<TimedInteractionSnapshot.View> views(LivingEntity body, TimedOperationState state) {
        return state.operations().stream().map(o -> {
            Presentation p=PRESENTATION.get(new Key(body.getUUID(),o.id()));
            return new TimedInteractionSnapshot.View(o.id(),o.startTick(),o.durationTicks(),o.phase(),o.terminalTick(),
                    p == null ? 0L : p.origin(), p == null ? Optional.empty() : p.worldTarget());
        }).toList();
    }
    private static void sendSnapshot(LivingEntity body,ServerPlayer observer){ServerLevel level=(ServerLevel)body.level();TimedOperationState state=body.getData(ModDataAttachments.TIMED_OPERATIONS.get());PacketDistributor.sendToPlayer(observer,new TimedInteractionSnapshot(level.dimension().location(),body.getId(),body.getUUID(),state.revision(),now(level),views(body,state)));}
    public static void onServerStopped(ServerStoppedEvent event) {
        for (LivingEntity body : List.copyOf(ACTORS.values())) {
            TimedOperationState state=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
            if (state == null || !(body.level() instanceof ServerLevel level)) continue;
            TimedOperationState next=state;
            for (TimedOperation op : state.operations()) if (op.phase()==TimedOperation.Phase.ACTIVE) next=TimedInteractionEngine.cancel(next,op.id(),now(level));
            cleanupCallbacks(body,next);
        }
        CALLBACKS.clear(); PRESENTATION.clear(); ACTORS.clear(); serverThread=null;
    }
}
