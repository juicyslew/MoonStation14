package com.juicyslew.moonstation14.ms14.status_effect;

import java.util.Objects;
import java.util.Optional;

/** Immutable result of advancing one status effect by one tick. */
public record StatusEffectTickResult(
        StatusEffectTransition transition,
        Optional<StatusEffectInstance> next
) {
    public StatusEffectTickResult {
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(next, "next");
    }
}
