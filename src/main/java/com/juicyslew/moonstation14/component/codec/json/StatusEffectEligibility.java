package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Closed set of status-effect target eligibility predicates available now. */
public enum StatusEffectEligibility {
    LIVING_ENTITY("living_entity"),
    PROTOTYPE_ADMITTED("prototype_admitted");

    public static final Codec<StatusEffectEligibility> CODEC = Codec.STRING.comapFlatMap(
            value -> {
                for (var eligibility : values()) if (eligibility.serializedName.equals(value)) return DataResult.success(eligibility);
                return DataResult.error(() -> "unknown status effect eligibility '" + value + "'");
            },
            value -> value.serializedName);

    private final String serializedName;

    StatusEffectEligibility(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}

