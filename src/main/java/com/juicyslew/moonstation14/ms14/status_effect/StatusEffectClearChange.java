package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;

/** One status removed by a centralized clear-all operation. */
public record StatusEffectClearChange(
        ResourceKey<StatusEffectData> key,
        StatusEffectInstance before,
        boolean definitionPresent
) {
    public StatusEffectClearChange {
        Objects.requireNonNull(key, "status effect key");
        Objects.requireNonNull(before, "status effect instance");
    }
}
