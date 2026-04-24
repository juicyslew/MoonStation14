package com.juicyslew.moonstation14.effect;

import net.minecraft.world.entity.Entity;

public class EffectContext {
    private final Entity entity;
    // Optional fields with sensible defaults
    private float scale = 1.0f;

    private EffectContext(Builder builder) {
        this.entity = builder.entity;
        this.scale = builder.scale;
    }

    // Getters
    public Entity entity() { return entity; }
    public float scale() { return scale; }

    public static class Builder {
        private final Entity entity;
        private float scale = 1.0f;

        public Builder(Entity entity) { this.entity = entity; }
        public Builder scale(float scale) { this.scale = scale; return this; }
        public EffectContext build() { return new EffectContext(this); }
    }
}
