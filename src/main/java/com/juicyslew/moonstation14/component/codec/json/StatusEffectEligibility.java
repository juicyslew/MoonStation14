package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Closed set of status-effect target eligibility predicates available now. */
public enum StatusEffectEligibility {
    LIVING_ENTITY("living_entity");

    public static final Codec<StatusEffectEligibility> CODEC = Codec.STRING.comapFlatMap(
            value -> "living_entity".equals(value)
                    ? DataResult.success(LIVING_ENTITY)
                    : DataResult.error(() -> "unknown status effect eligibility '" + value + "'"),
            value -> value.serializedName);

    private final String serializedName;

    StatusEffectEligibility(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
