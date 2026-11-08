package com.juicyslew.moonstation14.ms14.projectile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Supported local data for SS14 FlyBySoundComponent (not playback or networking). */
public record ProjectileFlyByProfile(ResourceLocation sound, float probability, float range) {
    public static final ProjectileFlyByProfile DEFAULT = new ProjectileFlyByProfile(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "projectile/fly_by"), 0.10f, 1.5f);

    public static final Codec<ProjectileFlyByProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("sound", DEFAULT.sound()).forGetter(ProjectileFlyByProfile::sound),
            Codec.floatRange(0, 1).optionalFieldOf("probability", DEFAULT.probability()).forGetter(ProjectileFlyByProfile::probability),
            Codec.floatRange(0, 128).optionalFieldOf("range", DEFAULT.range()).forGetter(ProjectileFlyByProfile::range)
    ).apply(i, ProjectileFlyByProfile::new));

    public ProjectileFlyByProfile {
        if (sound == null || !Float.isFinite(probability) || probability < 0 || probability > 1
                || !Float.isFinite(range) || range < 0 || range > 128) {
            throw new IllegalArgumentException("Invalid projectile fly-by profile");
        }
    }
}
