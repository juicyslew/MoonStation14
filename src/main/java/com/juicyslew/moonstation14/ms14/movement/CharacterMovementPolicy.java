package com.juicyslew.moonstation14.ms14.movement;

/** Physical profile for the shared initial human policy (used irrespective of player or Villager host). */
public record CharacterMovementPolicy(double accelerationPerSecondSquared,
                                      double walkSpeedPerSecond,
                                      double sprintSpeedPerSecond,
                                      double groundFrictionWithInputPerSecond,
                                      double groundFrictionNoInputPerSecond,
                                      double minimumFrictionSpeed) {
    /** SS14 human defaults: friction modifier 2.5 times pinned global tile friction 8.0; accel 20, walk 2.5/s, sprint 4.5/s. */
    public static final CharacterMovementPolicy HUMAN = new CharacterMovementPolicy(20d, 2.5d, 4.5d,
            20d, 20d, .005d);
    /** Pinned SS14 human crawler speed modifier; hand-occupancy scaling is not modeled locally. */
    public static final double HUMAN_KNOCKDOWN_SPEED_FACTOR = .4d;
    public static final String HUMAN_PROTOTYPE_ID = "moonstation14:human";

    /** Pure prototype-ID lookup only; runtime entity/host binding is outside this M1 module. */
    public static CharacterMovementPolicy forCharacterPrototype(String prototypeId) {
        if (HUMAN_PROTOTYPE_ID.equals(prototypeId)) return HUMAN;
        throw new IllegalArgumentException("unknown character prototype: " + prototypeId);
    }

    public CharacterMovementPolicy {
        requireNonnegative(accelerationPerSecondSquared, "acceleration");
        requireNonnegative(walkSpeedPerSecond, "walk speed");
        requireNonnegative(sprintSpeedPerSecond, "sprint speed");
        requireNonnegative(groundFrictionWithInputPerSecond, "ground friction with input");
        requireNonnegative(groundFrictionNoInputPerSecond, "ground friction without input");
        requireNonnegative(minimumFrictionSpeed, "minimum friction speed");
    }

    private static void requireNonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
