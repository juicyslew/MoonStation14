package com.juicyslew.moonstation14.ms14.standing;

/** Pure conversions for the grounded prone movement model. */
public final class PostureMovementPhysics {
    private PostureMovementPhysics() { }

    public static double groundedDamping(double ordinaryDamping, double downFriction) {
        return 1d - (1d - ordinaryDamping) * downFriction;
    }

    public static double inputGain(double downFriction) {
        return downFriction;
    }
}

