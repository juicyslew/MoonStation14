package com.juicyslew.moonstation14.ms14.player_body_control;

import java.util.Objects;
import java.util.UUID;

/** Identity of a control mind; deliberately separate from the authenticated session UUID. */
public record MindId(UUID value) {
    public MindId {
        Objects.requireNonNull(value, "value");
    }
}
