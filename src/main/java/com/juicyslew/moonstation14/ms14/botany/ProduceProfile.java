package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Produce-specific genetics and visual composition. */
public record ProduceProfile(ResourceLocation seed, PotencyVisuals potencyVisuals) {
    public record PotencyVisuals(float minimumScale,float maximumScale) {
        public static final Codec<PotencyVisuals> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.FLOAT.fieldOf("minimum_scale").forGetter(PotencyVisuals::minimumScale),
                Codec.FLOAT.fieldOf("maximum_scale").forGetter(PotencyVisuals::maximumScale)
        ).apply(i,PotencyVisuals::new));
        public PotencyVisuals { if(!Float.isFinite(minimumScale)||!Float.isFinite(maximumScale)) throw new IllegalArgumentException("potency visual scales must be finite"); }
    }
    public static final Codec<ProduceProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("seed").forGetter(p -> java.util.Optional.ofNullable(p.seed())),
             PotencyVisuals.CODEC.optionalFieldOf("potency_visuals").forGetter(p -> java.util.Optional.ofNullable(p.potencyVisuals()))
        ).apply(i, (seed, visuals) -> new ProduceProfile(seed.orElse(null), visuals.orElse(null))));
}
