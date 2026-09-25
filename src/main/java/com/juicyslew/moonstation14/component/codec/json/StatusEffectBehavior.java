package com.juicyslew.moonstation14.component.codec.json;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Closed set of composable status behavior projections. */
public enum StatusEffectBehavior {
    MARKER("marker"),
    CLIENT_JITTER("client_jitter"),
    MOVEMENT_SPEED("movement_speed"),
    STUN_ACTION_BLOCK("stun_action_block");

    public static final Codec<StatusEffectBehavior> CODEC = Codec.STRING.comapFlatMap(
            value -> {
                for (StatusEffectBehavior behavior : values()) {
                    if (behavior.serializedName.equals(value)) {
                        return DataResult.success(behavior);
                    }
                }
                return DataResult.error(() -> "unknown status effect behavior '" + value + "'");
            },
            value -> value.serializedName);

    private final String serializedName;

    StatusEffectBehavior(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
