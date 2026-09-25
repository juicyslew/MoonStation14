package com.juicyslew.moonstation14.ms14.player_body_control;

import java.util.Objects;

/** A registered ghost or configured character that may be considered for control. */
public record MobHarness(MobHarnessId id, MobHarnessKind kind) {
    public MobHarness {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
    }
}
