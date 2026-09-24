package com.juicyslew.moonstation14.ms14.status_effect;

import java.util.Objects;
import java.util.Optional;

/** Immutable result of a pure status-effect reduction. */
public record StatusEffectReduction(
        StatusEffectChangeKind kind,
        Optional<StatusEffectInstance> next
) {
    public StatusEffectReduction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(next, "next");
    }
}
