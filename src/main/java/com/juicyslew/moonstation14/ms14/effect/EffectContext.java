package com.juicyslew.moonstation14.ms14.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

/** Immutable, server-authoritative inputs for executing an effect handler. */
public record EffectContext(
        ServerLevel level,
        Entity entity,
        float scale,
        RandomSource random,
        ConditionContext conditionContext,
        EffectCause cause,
        Optional<ReagentEffectContext> reagentContext
) {
    public EffectContext(
            ServerLevel level,
            Entity entity,
            float scale,
            RandomSource random,
            ConditionContext conditionContext,
            EffectCause cause) {
        this(level, entity, scale, random, conditionContext, cause, Optional.empty());
    }

    public EffectContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(conditionContext, "conditionContext");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(reagentContext, "reagentContext");
        if (level.isClientSide() || entity.level().isClientSide()) {
            throw new IllegalArgumentException("Effects may only execute on a server level");
        }
        if (entity.level() != level) {
            throw new IllegalArgumentException("Effect entity must belong to the effect level");
        }
    }

    public ConditionContext conditions() {
        return conditionContext;
    }

    public Entity target() {
        return entity;
    }

    public EffectContext withScale(float adjustedScale) {
        return new EffectContext(level, entity, adjustedScale, random, conditionContext, cause, reagentContext);
    }

    public EffectContext withConditionContext(ConditionContext conditions) {
        return new EffectContext(level, entity, scale, random, conditions, cause, reagentContext);
    }
}
