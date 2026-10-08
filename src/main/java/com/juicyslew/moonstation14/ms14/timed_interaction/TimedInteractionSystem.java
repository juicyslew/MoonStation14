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
import java.util.function.IntSupplier;

/** Server-authoritative callback runtime; callbacks never enter entity attachment data. */
public final class TimedInteractionSystem {
    public enum Completion { REPEAT, STOP, DENIED, RECOVERY_REQUIRED }
    private record Key(UUID body, long id) { }
    private record Context(IntSupplier repeatDuration, double damageThreshold, double movementThreshold, BooleanSupplier valid, Supplier<Completion> completion, ServerLevel level, net.minecraft.world.phys.Vec3 start) { }
    private record Presentation(long origin, ServerPlayer owner) { }
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
        return start(body, owner, originSequence, kind, sourceKey, durationTicks, breakDamage, breakMove, 1.0, valid, completion);
    }
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            double damageThreshold, BooleanSupplier valid, Supplier<Completion> completion) {
        return start(body, owner, originSequence, kind, sourceKey, durationTicks, breakDamage, breakMove,
                damageThreshold, () -> durationTicks, valid, completion);
    }
    /** Start an operation whose committed repeat duration is recalculated from live state each cycle. */
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            double damageThreshold, IntSupplier repeatDuration, BooleanSupplier valid, Supplier<Completion> completion) {
        return start(body, owner, originSequence, kind, sourceKey, durationTicks, breakDamage, breakMove,
                damageThreshold, 0.3, repeatDuration, valid, completion);
    }
    /** Start an operation with explicit movement cancellation distance; existing callers retain the 0.3 default. */
    public static TimedInteractionEngine.StartResult start(LivingEntity body, ServerPlayer owner, long originSequence,
            String kind, String sourceKey, int durationTicks, boolean breakDamage, boolean breakMove,
            double damageThreshold, double movementThreshold, IntSupplier repeatDuration,
            BooleanSupplier valid, Supplier<Completion> completion) {
        Objects.requireNonNull(body); Objects.requireNonNull(kind); Objects.requireNonNull(sourceKey); Objects.requireNonNull(valid); Objects.requireNonNull(completion);
        Objects.requireNonNull(repeatDuration);
        if (!Double.isFinite(damageThreshold) || damageThreshold <= 0 || !Double.isFinite(movementThreshold) || movementThreshold <= 0) return TimedInteractionEngine.StartResult.DENIED;
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
            CALLBACKS.put(key, new Context(repeatDuration, damageThreshold, movementThreshold, valid, completion, level, body.position()));
            PRESENTATION.put(key, new Presentation(originSequence, owner));
        }
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
                if (!invalid && op.breakOnMove() && c.start().distanceTo(body.position()) > c.movementThreshold()) invalid=true;
                if(invalid) next=TimedInteractionEngine.cancel(next,op.id(),current);
            }
            var advanced=TimedInteractionEngine.advance(next,current); next=advanced.state();
            for(TimedOperation due:advanced.completed()) {
                Key key=new Key(body.getUUID(),due.id()); Context c=CALLBACKS.get(key); if(c==null)continue;
                Completion outcome=Completion.DENIED;
                try { outcome=c.valid().getAsBoolean()?c.completion().get():Completion.STOP; } catch(RuntimeException ignored) { }
                if(outcome==Completion.REPEAT) {
                    int duration;
                    try { duration = c.repeatDuration().getAsInt(); } catch (RuntimeException ex) { duration = 0; }
                    if (duration < 1) { next = TimedInteractionEngine.cancel(next, due.id(), current); CALLBACKS.remove(key); }
                    else {
                        next=TimedInteractionEngine.repeat(next,due.id(),current,duration);
                        CALLBACKS.put(key, new Context(c.repeatDuration(), c.damageThreshold(), c.movementThreshold(), c.valid(), c.completion(), c.level(), body.position()));
                    }
                }
                else CALLBACKS.remove(key);
            }
            next=TimedInteractionEngine.prune(next,current);
             if(next!=state){body.setData(ModDataAttachments.TIMED_OPERATIONS.get(),next);publish(body);cleanupCallbacks(body,next);cleanupActor(body,next);}
        }
    }
    private static void cleanupCallbacks(LivingEntity body, TimedOperationState state) {
        Set<Long> ids=new HashSet<>(); for(TimedOperation op:state.operations()) if(op.phase()==TimedOperation.Phase.ACTIVE) ids.add(op.id());
        CALLBACKS.keySet().removeIf(k->k.body().equals(body.getUUID())&&!ids.contains(k.id()));
        PRESENTATION.keySet().removeIf(k->k.body().equals(body.getUUID())&&state.operations().stream().noneMatch(o->o.id()==k.id()));
    }
    private static void cleanupActor(LivingEntity body, TimedOperationState state) { if(state.operations().isEmpty()) ACTORS.remove(body.getUUID()); }
    private static void publish(LivingEntity body) {
        if (!(body.level() instanceof ServerLevel level)) return;
        TimedOperationState state=body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        List<TimedInteractionSnapshot.View> views=state.operations().stream().map(o->new TimedInteractionSnapshot.View(o.id(),o.startTick(),o.durationTicks(),o.phase(),o.terminalTick(),Optional.ofNullable(PRESENTATION.get(new Key(body.getUUID(),o.id()))).map(Presentation::origin).orElse(0L))).toList();
        var packet=new TimedInteractionSnapshot(level.dimension().location(),body.getId(),body.getUUID(),state.revision(),now(level),views);
        PacketDistributor.sendToPlayersTrackingEntity(body,packet);
        Set<ServerPlayer> owners=new HashSet<>(); PRESENTATION.forEach((key,value)->{if(key.body().equals(body.getUUID())&&value.owner()!=null&&!value.owner().isRemoved())owners.add(value.owner());});
        for(ServerPlayer owner:owners) PacketDistributor.sendToPlayer(owner,packet);
    }
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity body) || !(event.getLevel() instanceof ServerLevel level)) return;
        checkThread(level); TimedOperationState state=body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get());
         if(state!=null){long current=now(level);TimedOperationState next=state;for(TimedOperation op:state.operations())if(op.phase()==TimedOperation.Phase.ACTIVE)next=TimedInteractionEngine.cancel(next,op.id(),current);body.setData(ModDataAttachments.TIMED_OPERATIONS.get(),next);publish(body);cleanupCallbacks(body,next);}
        UUID id=body.getUUID(); CALLBACKS.keySet().removeIf(k->k.body().equals(id)); PRESENTATION.keySet().removeIf(k->k.body().equals(id)); ACTORS.remove(id);
    }
    public static void onLogout(LivingEntity body) {
        if (!(body.level() instanceof ServerLevel level)) return; checkThread(level);
        UUID id=body.getUUID(); CALLBACKS.keySet().removeIf(k->k.body().equals(id)); PRESENTATION.keySet().removeIf(k->k.body().equals(id));
        PRESENTATION.entrySet().removeIf(e->e.getValue().owner()==body);
        if(body.hasData(ModDataAttachments.TIMED_OPERATIONS.get())) publish(body);
        ACTORS.remove(id);
    }
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if(event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof LivingEntity body && body.level() instanceof ServerLevel level){checkThread(level);if(body.hasData(ModDataAttachments.TIMED_OPERATIONS.get()))sendSnapshot(body,observer);}
    }
    private static void sendSnapshot(LivingEntity body,ServerPlayer observer){ServerLevel level=(ServerLevel)body.level();TimedOperationState state=body.getData(ModDataAttachments.TIMED_OPERATIONS.get());List<TimedInteractionSnapshot.View> views=state.operations().stream().map(o->new TimedInteractionSnapshot.View(o.id(),o.startTick(),o.durationTicks(),o.phase(),o.terminalTick(),Optional.ofNullable(PRESENTATION.get(new Key(body.getUUID(),o.id()))).map(Presentation::origin).orElse(0L))).toList();PacketDistributor.sendToPlayer(observer,new TimedInteractionSnapshot(level.dimension().location(),body.getId(),body.getUUID(),state.revision(),now(level),views));}
    public static void onServerStopped(ServerStoppedEvent event) { CALLBACKS.clear(); PRESENTATION.clear(); ACTORS.clear(); serverThread=null; }
}
