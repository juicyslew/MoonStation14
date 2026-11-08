package com.juicyslew.moonstation14.ms14.character;

import net.minecraft.world.entity.LivingEntity;

/** Immutable, entity-owned appearance data used when making detached character portraits. */
public interface CharacterVisualState {
    VisualSnapshot captureVisualState();

    interface VisualSnapshot {
        void applyTo(LivingEntity detached);
    }
}
