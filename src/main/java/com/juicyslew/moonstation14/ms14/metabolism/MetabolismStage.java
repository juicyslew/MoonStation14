package com.juicyslew.moonstation14.ms14.metabolism;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** The closed set of metabolism compartments understood by the data layer. */
public enum MetabolismStage {
    RESPIRATION("respiration"),
    DIGESTION("digestion"),
    BLOODSTREAM("bloodstream"),
    METABOLITES("metabolites");

    public static final Codec<MetabolismStage> CODEC = Codec.STRING.comapFlatMap(
            value -> {
                for (MetabolismStage stage : values()) {
                    if (stage.serializedName.equals(value)) return DataResult.success(stage);
                }
                return DataResult.error(() -> "Unknown metabolism stage '" + value + "'");
            },
            MetabolismStage::serializedName
    );

    private final String serializedName;

    MetabolismStage(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MetabolismStage fromSerializedName(String value) {
        for (MetabolismStage stage : values()) {
            if (stage.serializedName.equals(value)) return stage;
        }
        throw new IllegalArgumentException("Unknown metabolism stage '" + value + "'");
    }

    @Override
    public String toString() {
        return serializedName;
    }
}
