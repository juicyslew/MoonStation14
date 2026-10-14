package com.juicyslew.moonstation14.ms14.singularity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Resolved root AmbientSound plus SingularityComponent audio defaults. */
public record SingularitySoundProfile(ResourceLocation ambient, float ambientDb, float ambientRange,
                                      ResourceLocation componentAmbient, float componentAmbientDb,
                                      float componentAmbientRange, ResourceLocation formation,
                                      ResourceLocation dissipation) {
    public static final SingularitySoundProfile DEFAULT = new SingularitySoundProfile(
            id("singularity/ambient"), -4, 14, id("singularity/component_ambient"), 5, 20,
            null, id("singularity/dissipate"));
    public static final Codec<SingularitySoundProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("ambient").forGetter(SingularitySoundProfile::ambient),
            Codec.FLOAT.fieldOf("ambient_db").forGetter(SingularitySoundProfile::ambientDb),
            Codec.floatRange(0, 128).fieldOf("ambient_range").forGetter(SingularitySoundProfile::ambientRange),
            ResourceLocation.CODEC.fieldOf("component_ambient").forGetter(SingularitySoundProfile::componentAmbient),
            Codec.FLOAT.fieldOf("component_ambient_db").forGetter(SingularitySoundProfile::componentAmbientDb),
            Codec.floatRange(0, 128).fieldOf("component_ambient_range").forGetter(SingularitySoundProfile::componentAmbientRange),
            ResourceLocation.CODEC.optionalFieldOf("formation").forGetter(s -> java.util.Optional.ofNullable(s.formation)),
            ResourceLocation.CODEC.fieldOf("dissipation").forGetter(SingularitySoundProfile::dissipation)
    ).apply(i, (a, adb, ar, ca, cadb, car, f, d) -> new SingularitySoundProfile(a, adb, ar, ca, cadb, car, f.orElse(null), d)));
    public static float gain(float db) { return (float) Math.pow(10, db / 20.0); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
}
