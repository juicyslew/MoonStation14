package com.juicyslew.moonstation14.ms14.damage;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

import java.util.Map;

/** Canonical signed typed-damage ledger delta after native resistance. */
public final class TypedDamageAppliedEvent extends Event {
    private final LivingEntity entity;
    private final Map<String, Float> delta;
    private final net.minecraft.world.entity.Entity origin;

    public TypedDamageAppliedEvent(LivingEntity entity, Map<String, Float> delta) {
        this(entity, delta, null);
    }
    public TypedDamageAppliedEvent(LivingEntity entity, Map<String, Float> delta, net.minecraft.world.entity.Entity origin) {
        this.entity = entity;
        this.delta = Map.copyOf(delta);
        this.origin = origin;
    }

    public LivingEntity entity() { return entity; }
    public Map<String, Float> delta() { return delta; }
    public net.minecraft.world.entity.Entity origin() { return origin; }
}

