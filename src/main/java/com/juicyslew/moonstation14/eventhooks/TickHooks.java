package com.juicyslew.moonstation14.eventhooks;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivityAttachment;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.effect.ReagentEffectContext;
import com.juicyslew.moonstation14.ms14.effect.ReagentEffectTransactionState;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolismSystem;
import com.juicyslew.moonstation14.ms14.metabolism.MetabolizerProfile;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import com.juicyslew.moonstation14.ms14.stomach.StomachDigestionTransfer;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.fire.FireStackSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


public class TickHooks {
    private static final EffectSystem EFFECT_SYSTEM = EffectSystem.withDefaults();
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED_STOMACH_ADJUST_REAGENT =
            new java.util.concurrent.atomic.AtomicBoolean();
    public static void onLivingEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof LivingEntity livingEntity)
                || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        EntityActivityAttachment active = entity.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        // Eligibility is temporary policy, while the attachment is durable owner state.
        // Check even with no scheduled activity so a zero-hunger entity can shed stale
        // alerts/modifiers immediately after transformation.
        if (!com.juicyslew.moonstation14.ms14.hunger.HungerSystem.isEligible(livingEntity)
                && entity.getExistingDataOrNull(ModDataAttachments.HUNGER.get()) != null) {
            com.juicyslew.moonstation14.ms14.hunger.HungerSystem.reconcile(livingEntity, serverLevel);
            com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem.update(
                    livingEntity, EntityActivity.HUNGER, false);
            active = entity.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        }
        if (active == null) {
            return;
        }

        // Entity-type enrollment can change after a transformation. Retain the
        // authoritative attachment, but immediately shed its derived work flag.
        if (active.isActive(EntityActivity.THIRST)
                && !com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isEligible(livingEntity)) {
            // Shed the authoritative scalar's derived projection at the same
            // tick as its scheduler flag, while retaining the attachment.
            com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.reconcile(livingEntity, serverLevel);
            com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem.update(
                    livingEntity, EntityActivity.THIRST, false);
            active = entity.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
            if (active == null) return;
        }
        if (active.isActive(EntityActivity.HUNGER)
                && !com.juicyslew.moonstation14.ms14.hunger.HungerSystem.isEligible(livingEntity)) {
            com.juicyslew.moonstation14.ms14.hunger.HungerSystem.reconcile(livingEntity, serverLevel);
            com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem.update(
                    livingEntity, EntityActivity.HUNGER, false);
            active = entity.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
            if (active == null) return;
        }

        Set<EntityActivity> due = dueActivities(active, serverLevel.getGameTime(), entity.getId());
        runDueActivities(livingEntity, serverLevel, due);
    }

    /** Snapshots due work before any subsystem can enable or disable another activity. */
    public static EnumSet<EntityActivity> dueActivities(EntityActivityAttachment active,
                                                        long gameTime, int entityId) {
        EnumSet<EntityActivity> due = EnumSet.noneOf(EntityActivity.class);
        for (EntityActivity activity : active.snapshot()) {
            if (activity.isDue(gameTime, entityId)) {
                due.add(activity);
            }
        }
        return due;
    }

    /** Narrow scheduler seam used by server tests and the entity tick hook. */
    public static void runDueActivities(LivingEntity livingEntity, ServerLevel serverLevel,
                                        Set<EntityActivity> due) {
        if (due.contains(EntityActivity.STATUS_EFFECT)) {
            StatusEffectUpdate(livingEntity, serverLevel);
        }
        if (due.contains(EntityActivity.ALERT)) {
            com.juicyslew.moonstation14.ms14.alert.AlertSystem.expire(livingEntity, serverLevel);
        }
        if (due.contains(EntityActivity.REAGENT_METABOLISM)) {
            ReagentUpdate(livingEntity, serverLevel);
        }
        if (due.contains(EntityActivity.FIRE_DRYING)) {
            FireStackSystem.dry(livingEntity);
        }
        if (due.contains(EntityActivity.THIRST)) {
            com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.decayOneSecond(livingEntity);
        }
        if (due.contains(EntityActivity.HUNGER)) {
            com.juicyslew.moonstation14.ms14.hunger.HungerSystem.decayOneSecond(livingEntity);
        }
    }

    /** Returns the deterministic metabolism bucket for a server tick and entity id. */
    static int metabolismBucket(long serverGameTime, int entityId) {
        int interval = EntityActivity.REAGENT_METABOLISM.tickInterval();
        return (int) Math.floorMod(Math.floorMod(serverGameTime, (long) interval) + entityId, interval);
    }

    /** Keeps the once-per-20-ticks schedule evenly distributed across entities. */
    static boolean shouldMetabolize(long serverGameTime, int entityId) {
        return EntityActivity.REAGENT_METABOLISM.isDue(serverGameTime, entityId);
    }

    static void ReagentUpdate(LivingEntity livingEntity, ServerLevel serverLevel){
        PrototypeCatalog<ReagentData> reagents = PrototypeRuntime.serverReagents();
        IReagentTrait reagentTrait = (IReagentTrait) livingEntity;
        TraitHandler<IReagentTrait> source = reagentTrait.toHandleSelf();
        ReagentAttachment body = MS14Provider.getDetached(livingEntity, MS14Bridges.REAGENT);
        ReagentAttachment stomach = MS14Provider.getDetached(livingEntity, MS14Bridges.STOMACH);
        var bodyBefore = MS14Provider.snapshot(body);
        var stomachBefore = MS14Provider.snapshot(stomach);
        runWithFinalization(() -> {
            if (!body.isEmpty()) {
                runMetabolism(livingEntity, serverLevel, source, body, body, reagents,
                        MetabolizerProfile.SHARED_BODY, reagentTrait.getCapacity(), reagentTrait.getCapacity());
            }
            if (!stomach.isEmpty()) {
                runMetabolism(livingEntity, serverLevel, source, stomach, body, reagents,
                        MetabolizerProfile.STOMACH, StomachSystem.CAPACITY, reagentTrait.getCapacity());
                StomachDigestionTransfer.transfer(stomach, body, reagents.asMap(), reagentTrait.getCapacity());
            }
        }, () -> {
            MS14Provider.updateIfChanged(livingEntity, MS14Bridges.REAGENT, bodyBefore, body);
            MS14Provider.updateIfChanged(livingEntity, MS14Bridges.STOMACH, stomachBefore, stomach);
            com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem.update(livingEntity,
                    EntityActivity.REAGENT_METABOLISM, !body.isEmpty() || !stomach.isEmpty());
        });
    }

    static void runWithFinalization(Runnable processing, Runnable finalization) {
        Objects.requireNonNull(processing, "processing");
        Objects.requireNonNull(finalization, "finalization");
        try {
            processing.run();
        } finally {
            finalization.run();
        }
    }

    private static void runMetabolism(LivingEntity livingEntity, ServerLevel serverLevel,
                                      TraitHandler<IReagentTrait> source,
                                       ReagentAttachment sourceSolution, ReagentAttachment metaboliteDestination,
                                       PrototypeCatalog<ReagentData> reagents, MetabolizerProfile profile,
                                       float sourceCapacity, float destinationCapacity) {
        MetabolismSystem.process(sourceSolution, metaboliteDestination, reagents.asMap(), profile,
                sourceCapacity, destinationCapacity, serverLevel.getRandom(), invocation -> {
                    TraitHandler<IReagentTrait> effectSource = profile == MetabolizerProfile.STOMACH
                            ? new TraitHandler<>(livingEntity, () -> StomachSystem.CAPACITY) : source;
                    // Ordinary removal has already happened. Keep this baseline once so
                    // each effect can route the pre-removal view plus only later mutations.
                    Map<ResourceKey<ReagentData>, Float> postRemovalBaseline =
                            copyValidatedQuantities(sourceSolution.getMap());
                    ReagentEffectTransactionState transactionState =
                            new ReagentEffectTransactionState(invocation.reagent(), invocation.actualRemoved());
                    invocation.metabolism().effects().forEach(effect -> {
                        // AdjustReagent is source-sensitive and currently has no digestion prototypes.
                        // Until effect context can identify a stomach as the capacity owner, reject it
                        // rather than exposing the living body's 1000-unit capacity to a 50-unit source.
                        if (profile == MetabolizerProfile.STOMACH
                                && effect instanceof com.juicyslew.moonstation14.component.codec.json.EffectData.AdjustReagent) {
                            if (WARNED_STOMACH_ADJUST_REAGENT.compareAndSet(false, true)) {
                                com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                                        "Skipping digestion AdjustReagent: stomach source capacity/routing is unsupported");
                            }
                            return;
                        }
                        ConditionContext conditionContext = metabolismConditionContext(
                                invocation.sourceSnapshot(), postRemovalBaseline, sourceSolution.getMap(),
                                transactionState);
                        ReagentEffectContext reagentContext = new ReagentEffectContext(
                                effectSource, sourceSolution, invocation.reagent(), invocation.stage(),
                                profile, invocation.sourceSnapshot(),
                                invocation.result().actualRemoved(),
                                transactionState,
                                Optional.empty());
                        EffectContext effectContext = new EffectContext(serverLevel, livingEntity,
                                invocation.scale(), serverLevel.getRandom(), conditionContext,
                                EffectCause.METABOLISM, Optional.of(reagentContext));
                        EFFECT_SYSTEM.apply(effect, effectContext);
                    });
                });
    }

    /**
     * Creates the condition capabilities for one metabolism effect attempt.
     *
     * The copy is intentional: effect execution must observe a point-in-time source
     * solution and must not be able to mutate the reagent attachment through a
     * condition context.  This method is also kept separate from the tick loop so
     * the source-solution contract can be tested without constructing a level.
     */
    public static ConditionContext metabolismConditionContext(ReagentAttachment source) {
        Objects.requireNonNull(source, "source");
        return ConditionContext.builder()
                .sourceReagentQuantities(copyValidatedQuantities(source.getMap()))
                .build();
    }

    /**
     * Routes metabolism conditions through the pre-removal source snapshot while preserving
     * mutations made to the live solution after the post-removal baseline. Effect handlers still
     * receive that live solution through {@link ReagentEffectContext}; this view only restores
     * pre-removal visibility for their condition gates.
     *
     * <p>The inputs are copied and validated, and the returned context is immutable. In
     * particular, a context already handed to an effect is not changed by a later live mutation.</p>
     */
    public static ConditionContext metabolismConditionContext(
            MetabolismSystem.SourceSnapshot sourceSnapshot,
            Map<ResourceKey<ReagentData>, Float> postRemovalBaseline,
            Map<ResourceKey<ReagentData>, Float> liveSource) {
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        Map<ResourceKey<ReagentData>, Float> preRemoval =
                copyValidatedQuantities(sourceSnapshot.quantities());
        Map<ResourceKey<ReagentData>, Float> baseline =
                copyValidatedQuantities(postRemovalBaseline);
        Map<ResourceKey<ReagentData>, Float> live = copyValidatedQuantities(liveSource);

        Map<ResourceKey<ReagentData>, Float> routed = new LinkedHashMap<>(preRemoval);
        baseline.keySet().forEach(key -> routed.putIfAbsent(key, 0f));
        live.keySet().forEach(key -> routed.putIfAbsent(key, 0f));
        for (ResourceKey<ReagentData> key : routed.keySet()) {
            float preRemovalAmount = preRemoval.getOrDefault(key, 0f);
            float baselineAmount = baseline.getOrDefault(key, 0f);
            float liveAmount = live.getOrDefault(key, 0f);
            float routedAmount = preRemovalAmount + (liveAmount - baselineAmount);
            if (!Float.isFinite(routedAmount) || routedAmount < 0f) {
                throw new IllegalArgumentException("routed reagent quantities must be finite and nonnegative");
            }
            routed.put(key, routedAmount);
        }
        return ConditionContext.builder()
                .sourceReagentQuantities(routed)
                .build();
    }

    /**
     * Builds the same immutable condition view while restoring only the
     * transaction's still-reserved ordinary removal on its source reagent.
     */
    public static ConditionContext metabolismConditionContext(
            MetabolismSystem.SourceSnapshot sourceSnapshot,
            Map<ResourceKey<ReagentData>, Float> postRemovalBaseline,
            Map<ResourceKey<ReagentData>, Float> liveSource,
            ReagentEffectTransactionState transactionState) {
        Objects.requireNonNull(transactionState, "transactionState");
        ConditionContext base = metabolismConditionContext(sourceSnapshot, postRemovalBaseline, liveSource);
        Map<ResourceKey<ReagentData>, Float> routed = new LinkedHashMap<>(
                base.sourceReagentQuantities().orElseThrow());
        ResourceKey<ReagentData> sourceReagent = transactionState.metabolizedReagent();
        float liveAmount = liveSource.getOrDefault(sourceReagent, 0f);
        float reservation = transactionState.remainingReservation();
        float virtualAmount = liveAmount + reservation;
        if (!Float.isFinite(liveAmount) || liveAmount < 0f
                || !Float.isFinite(reservation) || reservation < 0f
                || !Float.isFinite(virtualAmount) || virtualAmount < 0f) {
            throw new IllegalArgumentException("virtual source quantity must be finite and nonnegative");
        }
        routed.put(sourceReagent, virtualAmount);
        return ConditionContext.builder()
                .sourceReagentQuantities(routed)
                .build();
    }

    private static Map<ResourceKey<ReagentData>, Float> copyValidatedQuantities(
            Map<ResourceKey<ReagentData>, Float> source) {
        Objects.requireNonNull(source, "source");
        Map<ResourceKey<ReagentData>, Float> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<ReagentData>, Float> entry : source.entrySet()) {
            ResourceKey<ReagentData> key = Objects.requireNonNull(entry.getKey(), "reagent key");
            Float amount = entry.getValue();
            if (amount == null || !Float.isFinite(amount) || amount < 0f) {
                throw new IllegalArgumentException("reagent quantities must be finite and nonnegative");
            }
            copy.put(key, amount);
        }
        return Map.copyOf(copy);
    }

    static void StatusEffectUpdate(LivingEntity livingEntity, ServerLevel serverLevel){
        if (livingEntity instanceof IStatusEffectTrait statusEffectTrait) {
            StatusEffectSystem.advanceOneTick(statusEffectTrait.toHandleSelf(), serverLevel);
        }
    }
}
