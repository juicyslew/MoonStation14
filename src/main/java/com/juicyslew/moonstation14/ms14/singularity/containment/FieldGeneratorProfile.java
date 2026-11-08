package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved supported containment-generator defaults; this is domain state, not a live block. */
public record FieldGeneratorProfile(int maxCharge, int boltCharge, int connectionThreshold,
                                    int decayAmount, int decayIntervalTicks, int maxConnectionLength) {
    public static final Codec<FieldGeneratorProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("maxCharge").forGetter(FieldGeneratorProfile::maxCharge),
            Codec.INT.fieldOf("boltCharge").forGetter(FieldGeneratorProfile::boltCharge),
            Codec.INT.fieldOf("connectionThreshold").forGetter(FieldGeneratorProfile::connectionThreshold),
            Codec.INT.fieldOf("decayAmount").forGetter(FieldGeneratorProfile::decayAmount),
            Codec.INT.fieldOf("decayIntervalTicks").forGetter(FieldGeneratorProfile::decayIntervalTicks),
            Codec.INT.fieldOf("maxConnectionLength").forGetter(FieldGeneratorProfile::maxConnectionLength)
    ).apply(i, FieldGeneratorProfile::new));

    public FieldGeneratorProfile {
        if (maxCharge <= 0 || boltCharge <= 0 || connectionThreshold <= 0 || connectionThreshold > maxCharge
                || decayAmount <= 0 || decayIntervalTicks <= 0 || maxConnectionLength <= 0)
            throw new IllegalArgumentException("Invalid field generator profile");
    }
}

