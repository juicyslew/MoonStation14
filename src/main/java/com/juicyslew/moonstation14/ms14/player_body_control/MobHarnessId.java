package com.juicyslew.moonstation14.ms14.player_body_control;

import java.util.Objects;
import java.util.UUID;

/** Stable pure-model identity for a controllable entity. */
public record MobHarnessId(UUID value) {
    public MobHarnessId {
        Objects.requireNonNull(value, "value");
    }
}
