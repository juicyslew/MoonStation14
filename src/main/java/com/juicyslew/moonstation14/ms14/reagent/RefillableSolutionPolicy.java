package com.juicyslew.moonstation14.ms14.reagent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Supported projection of SS14 RefillableSolution. Amounts are solution units. */
public record RefillableSolutionPolicy(boolean enabled, String solutionName, Optional<Float> maxRefill) {
    public static final Codec<RefillableSolutionPolicy> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(RefillableSolutionPolicy::enabled),
            Codec.STRING.optionalFieldOf("solutionName", "default").forGetter(RefillableSolutionPolicy::solutionName),
            Codec.FLOAT.optionalFieldOf("maxRefill").forGetter(RefillableSolutionPolicy::maxRefill)
    ).apply(i, RefillableSolutionPolicy::new));
    public static final RefillableSolutionPolicy DISABLED = new RefillableSolutionPolicy(false, "default", Optional.empty());
    public RefillableSolutionPolicy {
        if (solutionName == null || solutionName.isBlank() || maxRefill == null
                || maxRefill.isPresent() && (!Float.isFinite(maxRefill.get()) || maxRefill.get() <= 0))
            throw new IllegalArgumentException("invalid refill policy");
    }
}

