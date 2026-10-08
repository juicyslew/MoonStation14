package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.ReactiveEffectsData;
import com.juicyslew.moonstation14.ms14.character.components.ReactiveComponent;
import com.juicyslew.moonstation14.ms14.effect.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dispatches reagent injection reactions before the injected solution is delivered. */
public final class InjectionReactionSystem {
    private static final EffectSystem EFFECTS = EffectSystem.withDefaults();
    private static final Logger LOGGER = LoggerFactory.getLogger(InjectionReactionSystem.class);
    private InjectionReactionSystem() { }

    /** Dispatch the actual removed dose. Targets must explicitly admit Injection and the reaction group. */
    public static void dispatch(ServerLevel level, LivingEntity target, ReactiveComponent reactive,
                                Map<ResourceKey<ReagentData>, Long> removed, RandomSource random) {
        if (level == null || target == null || reactive == null || removed == null || random == null
                || level.isClientSide || target.level() != level || !target.isAlive()
                || !reactive.reactiveMethods().contains(ReactiveComponent.ReactiveMethod.INJECTION)) return;
        // Validate and snapshot the entire removed dose before any effect callback can run.
        Map<ResourceKey<ReagentData>, Long> dose = new LinkedHashMap<>();
        Map<ResourceKey<ReagentData>, Float> quantities = new LinkedHashMap<>();
        for (var entry : removed.entrySet()) {
            ResourceKey<ReagentData> key = java.util.Objects.requireNonNull(entry.getKey(), "reagent key");
            Long cents = java.util.Objects.requireNonNull(entry.getValue(), "removed cents");
            ReagentUnits.validateCents(cents, "removed cents");
            if (cents == 0) continue;
            if (PrototypeRuntime.serverReagents().get(key.location()) == null)
                throw new IllegalArgumentException("Unknown reagent in removed injection dose: " + key.location());
            dose.put(key, cents);
            quantities.put(key, ReagentUnits.toFloat(cents));
        }
        ConditionContext conditions = ConditionContext.builder().sourceReagentQuantities(quantities).build();
        for (var entry : dose.entrySet()) {
            ReagentData reagent = PrototypeRuntime.serverReagents().get(entry.getKey().location());
            for (Map.Entry<String, ReactiveEffectsData> configured : reagent.reactiveEffects().entrySet()) {
                ReactiveComponent.ReactiveGroup group = group(configured.getKey());
                ReactiveEffectsData reaction = configured.getValue();
                if (!admitsInjection(reactive, group) || !reaction.methods().contains("injection")) continue;
                EffectContext context = new EffectContext(level, target, ReagentUnits.toFloat(entry.getValue()),
                        random, conditions, EffectCause.REACTION);
                for (EffectData effect : reaction.effects()) {
                    try { EFFECTS.apply(effect, context); }
                    catch (RuntimeException exception) {
                        LOGGER.error("Injection reaction callback failed for {}", target.getUUID(), exception);
                    }
                }
            }
        }
    }

    /** Convenience API for callers that have already split an actual reagent attachment dose. */
    public static void dispatch(ServerLevel level, LivingEntity target, ReactiveComponent reactive,
                                ReagentAttachment removed, RandomSource random) {
        if (removed == null) throw new IllegalArgumentException("removed dose is required");
        dispatch(level, target, reactive, removed.snapshotUnits(), random);
    }

    public static boolean admitsInjection(ReactiveComponent target, ReactiveComponent.ReactiveGroup group) {
        return target != null && group != null
                && target.reactiveMethods().contains(ReactiveComponent.ReactiveMethod.INJECTION)
                && target.reactiveGroups().contains(group);
    }

    private static ReactiveComponent.ReactiveGroup group(String name) {
        for (var group : ReactiveComponent.ReactiveGroup.values())
            if (group.serialized().equals(name)) return group;
        return null;
    }
}
