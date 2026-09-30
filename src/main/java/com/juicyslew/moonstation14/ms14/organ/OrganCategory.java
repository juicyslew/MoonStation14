package com.juicyslew.moonstation14.ms14.organ;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Bounded upstream organ category vocabulary supported by this phase. */
public enum OrganCategory {
    LUNGS("Lungs"), HEART("Heart");

    public static final Codec<OrganCategory> CODEC = Codec.STRING.comapFlatMap(value -> {
        for (OrganCategory category : values()) if (category.serialized.equals(value)) return DataResult.success(category);
        return DataResult.error(() -> "unknown organ category '" + value + "'");
    }, OrganCategory::serialized);
    private final String serialized;
    OrganCategory(String serialized) { this.serialized = serialized; }
    public String serialized() { return serialized; }
}
