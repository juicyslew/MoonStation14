package com.juicyslew.moonstation14.ms14.status_effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** Named production alias for the living-entity status lifecycle. */
public final class LivingEntityStatusEffectLifecycle extends DefaultStatusEffectLifecycle {
    public LivingEntityStatusEffectLifecycle(LivingEntity entity, ServerLevel level) {
        super(entity, level);
    }

}
