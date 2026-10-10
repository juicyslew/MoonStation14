package com.juicyslew.moonstation14.ms14.reagent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Locally supported projection of SS14 DrainableSolution. */
public record DrainableSolutionPolicy(boolean enabled, String solutionName) {
    public static final Codec<DrainableSolutionPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(DrainableSolutionPolicy::enabled),
            Codec.STRING.optionalFieldOf("solutionName", "default").forGetter(DrainableSolutionPolicy::solutionName)
    ).apply(i, DrainableSolutionPolicy::new));
    public static final DrainableSolutionPolicy DISABLED = new DrainableSolutionPolicy(false, "default");
    public DrainableSolutionPolicy { if (solutionName == null || solutionName.isBlank()) throw new IllegalArgumentException("invalid drain policy"); }
}
