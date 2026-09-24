package com.juicyslew.moonstation14.ms14.status_effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** The four duration operations supported by status effect applications. */
public enum StatusEffectOperation {
    UPDATE("update"),
    ADD("add"),
    REMOVE("remove"),
    SET("set");

    /** A strict codec: names are lowercase and no aliases are accepted. */
    public static final Codec<StatusEffectOperation> CODEC = Codec.STRING.comapFlatMap(
            value -> {
                for (StatusEffectOperation operation : values()) {
                    if (operation.serializedName.equals(value)) {
                        return DataResult.success(operation);
                    }
                }
                return DataResult.error(() -> "Unknown status effect operation '" + value + "'");
            },
            StatusEffectOperation::serializedName
    );

    private final String serializedName;

    StatusEffectOperation(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
