package com.juicyslew.moonstation14.ms14.character.stamina;

import java.util.Objects;

/** Immutable value wrapper for the synchronized/persisted body resource. */
public record StaminaAttachment(StaminaState state) {
    public StaminaAttachment { Objects.requireNonNull(state, "state"); }
}
