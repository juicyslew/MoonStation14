package com.juicyslew.moonstation14.ms14.damage;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

import java.util.Objects;

/** Published after positive typed damage has been admitted to the damage ledger. */
public final class AppliedDamageEvent extends Event {
    private final LivingEntity entity;
    private final double typedDamage;
    private final DamageFeedbackProfile feedback;

    public AppliedDamageEvent(LivingEntity entity, double typedDamage, DamageFeedbackProfile feedback) {
        this.entity = Objects.requireNonNull(entity, "entity");
        if (!Double.isFinite(typedDamage) || typedDamage < 0d) {
            throw new IllegalArgumentException("typedDamage must be finite and non-negative");
        }
        this.typedDamage = typedDamage;
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    public LivingEntity entity() {
        return entity;
    }

    public double typedDamage() {
        return typedDamage;
    }
    public DamageFeedbackProfile feedback() { return feedback; }
}

