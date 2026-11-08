package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Per-wave values; food and charge belong to selected strength, never physical PA parts. */
public record ParticleAcceleratorStrengthProfile(ParticleAcceleratorStrength strength, int powerBoxMv,
                                                 int singularityFood, int generatorCharge) {
    public static final Codec<ParticleAcceleratorStrengthProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ParticleAcceleratorStrength.CODEC.fieldOf("strength").forGetter(ParticleAcceleratorStrengthProfile::strength),
            Codec.intRange(0,100000).fieldOf("power_box_mv").forGetter(ParticleAcceleratorStrengthProfile::powerBoxMv),
            Codec.intRange(0,100000).fieldOf("singularity_food").forGetter(ParticleAcceleratorStrengthProfile::singularityFood),
            Codec.intRange(0,100000).fieldOf("generator_charge").forGetter(ParticleAcceleratorStrengthProfile::generatorCharge)
    ).apply(i, ParticleAcceleratorStrengthProfile::new));
    public ParticleAcceleratorStrengthProfile {
        if (strength == null || powerBoxMv < 0 || singularityFood < 0 || generatorCharge < 0)
            throw new IllegalArgumentException("Invalid PA strength profile");
    }
}

