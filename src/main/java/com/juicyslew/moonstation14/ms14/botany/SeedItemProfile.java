package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Item-side Seed component presence and optional carried-only seed identity. */
public record SeedItemProfile(ResourceLocation seedId) {
    public static final Codec<SeedItemProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("seed_id").forGetter(p -> java.util.Optional.ofNullable(p.seedId()))
    ).apply(i, seedId -> new SeedItemProfile(seedId.orElse(null))));
}
