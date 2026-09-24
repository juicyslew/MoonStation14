package com.juicyslew.moonstation14.ms14.status_effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** The lifecycle state of a status effect instance. */
public enum StatusEffectState {
    PENDING,
    ACTIVE;

    /** Strict lowercase names are part of the persistent status format. */
    public static final Codec<StatusEffectState> CODEC = Codec.STRING.comapFlatMap(
            value -> switch (value) {
                case "pending" -> DataResult.success(PENDING);
                case "active" -> DataResult.success(ACTIVE);
                default -> DataResult.error(() -> "Unknown status effect state '" + value + "'");
            },
            state -> state == PENDING ? "pending" : "active"
    );
}
