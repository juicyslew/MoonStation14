package com.juicyslew.moonstation14.ms14.movement;

/** Shared movement multiplier applied to the controlled body while pulling. */
public final class PullMovementPolicy {
    public static final double MOVEMENT_FACTOR = 0.95d;

    public static double voluntaryMovementFactor(boolean knockedDown, boolean pulling) {
        return (knockedDown ? CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR : 1d)
                * (pulling ? MOVEMENT_FACTOR : 1d);
    }

    private PullMovementPolicy() { }
}
