package com.juicyslew.moonstation14.ms14.movement;

import java.util.Objects;
import java.util.Optional;

/** Tick-scaled environmental inputs; gravity and jump speed must come from the Minecraft adapter, not guessed here. */
public record CharacterMovementEnvironment(double secondsPerTick, double gravityPerSecondSquared,
                                           double jumpVelocityPerSecond, double verticalDrag, MovementVector externalImpulse,
                                           double maximumExternalImpulsePerTick,
                                           double maxUpwardStepBlocks,
                                           MovementCollisionResolver collisionResolver,
                                           double surfaceMovementFactor,
                                           double voluntarySpeedFactor,
                                           MovementVector constraintImpulse,
                                           Optional<GravityAirControl> gravityAirControl) {
    /** Existing callers default to legacy gravity-air behavior unless an adapter supplies a profile. */
    public CharacterMovementEnvironment(double secondsPerTick, double gravityPerSecondSquared,
                                        double jumpVelocityPerSecond, double verticalDrag, MovementVector externalImpulse,
                                        double maximumExternalImpulsePerTick, double maxUpwardStepBlocks,
                                        MovementCollisionResolver collisionResolver) {
        this(secondsPerTick, gravityPerSecondSquared, jumpVelocityPerSecond, verticalDrag, externalImpulse,
                maximumExternalImpulsePerTick, maxUpwardStepBlocks, collisionResolver, 1d, 1d, MovementVector.ZERO);
    }

    /** Compatibility constructor retaining existing surface-factor callers. */
    public CharacterMovementEnvironment(double secondsPerTick, double gravityPerSecondSquared,
                                        double jumpVelocityPerSecond, double verticalDrag, MovementVector externalImpulse,
                                        double maximumExternalImpulsePerTick, double maxUpwardStepBlocks,
                                        MovementCollisionResolver collisionResolver, double surfaceMovementFactor) {
        this(secondsPerTick, gravityPerSecondSquared, jumpVelocityPerSecond, verticalDrag, externalImpulse,
                maximumExternalImpulsePerTick, maxUpwardStepBlocks, collisionResolver, surfaceMovementFactor, 1d,
                MovementVector.ZERO);
    }

    /** Existing full constructor; no gravity-air profile means legacy behavior. */
    public CharacterMovementEnvironment(double secondsPerTick, double gravityPerSecondSquared,
                                         double jumpVelocityPerSecond, double verticalDrag, MovementVector externalImpulse,
                                         double maximumExternalImpulsePerTick, double maxUpwardStepBlocks,
                                         MovementCollisionResolver collisionResolver, double surfaceMovementFactor,
                                         double voluntarySpeedFactor, MovementVector constraintImpulse) {
        this(secondsPerTick, gravityPerSecondSquared, jumpVelocityPerSecond, verticalDrag, externalImpulse,
                maximumExternalImpulsePerTick, maxUpwardStepBlocks, collisionResolver, surfaceMovementFactor,
                voluntarySpeedFactor, constraintImpulse, Optional.empty());
    }

    /** Compatibility constructor retaining both existing movement modifiers. */
    public CharacterMovementEnvironment(double secondsPerTick, double gravityPerSecondSquared,
                                        double jumpVelocityPerSecond, double verticalDrag, MovementVector externalImpulse,
                                        double maximumExternalImpulsePerTick, double maxUpwardStepBlocks,
                                        MovementCollisionResolver collisionResolver, double surfaceMovementFactor,
                                        double voluntarySpeedFactor) {
        this(secondsPerTick, gravityPerSecondSquared, jumpVelocityPerSecond, verticalDrag, externalImpulse,
                maximumExternalImpulsePerTick, maxUpwardStepBlocks, collisionResolver, surfaceMovementFactor,
                voluntarySpeedFactor, MovementVector.ZERO);
    }

    public CharacterMovementEnvironment {
        requireNonnegative(secondsPerTick, "secondsPerTick");
        if (secondsPerTick == 0d) throw new IllegalArgumentException("secondsPerTick must be positive");
        requireNonnegative(gravityPerSecondSquared, "gravity");
        requireNonnegative(jumpVelocityPerSecond, "jump velocity");
        if (!Double.isFinite(verticalDrag) || verticalDrag < 0d || verticalDrag > 1d) {
            throw new IllegalArgumentException("vertical drag must be finite and between 0 and 1");
        }
        requireNonnegative(maximumExternalImpulsePerTick, "maximum external impulse");
        requireNonnegative(maxUpwardStepBlocks, "maximum upward step");
        requireNonnegative(surfaceMovementFactor, "surface movement factor");
        requireNonnegative(voluntarySpeedFactor, "voluntary speed factor");
        Objects.requireNonNull(externalImpulse, "externalImpulse");
        Objects.requireNonNull(constraintImpulse, "constraintImpulse");
        Objects.requireNonNull(collisionResolver, "collisionResolver");
        Objects.requireNonNull(gravityAirControl, "gravityAirControl");
        double impulseLength = vectorLength(externalImpulse);
        if (!Double.isFinite(impulseLength) || impulseLength > maximumExternalImpulsePerTick) {
            throw new IllegalArgumentException("external impulse exceeds environment bound");
        }
        double constraintLength = vectorLength(constraintImpulse);
        if (!Double.isFinite(constraintLength) || constraintLength > maximumExternalImpulsePerTick) {
            throw new IllegalArgumentException("constraint impulse exceeds environment bound");
        }
    }

    private static double vectorLength(MovementVector vector) {
        return Math.hypot(Math.hypot(vector.x(), vector.y()), vector.z());
    }

    private static void requireNonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
