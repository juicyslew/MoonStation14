package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import net.minecraft.resources.ResourceKey;

import java.util.Objects;
import java.util.Optional;

/** Immutable result for one status during one tick. */
public record StatusEffectTickChange(
        ResourceKey<StatusEffectData> key,
        StatusEffectInstance before,
        Optional<StatusEffectInstance> after,
        StatusEffectTransition transition
) {
    public StatusEffectTickChange {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(transition, "transition");
        if ((transition == StatusEffectTransition.EXPIRED
                || transition == StatusEffectTransition.INVALIDATED) && after.isPresent()) {
            throw new IllegalArgumentException("removed status must have no after instance");
        }
        if (transition != StatusEffectTransition.EXPIRED
                && transition != StatusEffectTransition.INVALIDATED && after.isEmpty()) {
            throw new IllegalArgumentException("non-expired status must have an after instance");
        }
    }
}
