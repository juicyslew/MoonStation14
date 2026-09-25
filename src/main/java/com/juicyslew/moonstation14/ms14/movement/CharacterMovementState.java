package com.juicyslew.moonstation14.ms14.movement;

import java.util.Objects;

/** Immutable simulation state; no actor/controller identity is part of motor state. */
public record CharacterMovementState(MovementVector position, MovementVector velocity, boolean onGround) {
    public CharacterMovementState {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
    }
}
