package com.juicyslew.moonstation14.ms14.movement;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent;

import java.util.Objects;

/** Grounded motor parameters resolved from a character's typed movement component. */
public record CharacterMovementPolicy(double accelerationPerSecondSquared,
                                      double walkSpeedPerSecond,
                                      double sprintSpeedPerSecond,
                                      double groundFrictionWithInputPerSecond,
                                      double groundFrictionNoInputPerSecond,
                                      double minimumFrictionSpeed) {
    /** Test fixture only. Live movement must resolve the current typed prototype instead. */
    public static final CharacterMovementPolicy HUMAN = new CharacterMovementPolicy(20d, 2.5d, 4.5d,
            20d, 20d, .005d);
    /** Pinned SS14 human crawler speed modifier; hand-occupancy scaling is not modeled locally. */
    public static final double HUMAN_KNOCKDOWN_SPEED_FACTOR = .4d;
    public static final String HUMAN_PROTOTYPE_ID = "moonstation14:human";

    /** Legacy pure test fixture lookup; live controllers must never use this ID shortcut. */
    public static CharacterMovementPolicy forCharacterPrototype(String prototypeId) {
        if (HUMAN_PROTOTYPE_ID.equals(prototypeId)) return HUMAN;
        throw new IllegalArgumentException("unknown character prototype: " + prototypeId);
    }

    /** Resolves the generic grounded motor policy from a typed character prototype. */
    public static CharacterMovementPolicy fromCharacterData(CharacterData data) {
        Objects.requireNonNull(data, "data");
        MovementSpeedModifierComponent movement = data.component(MovementSpeedModifierComponent.class).orElseThrow(() ->
                new IllegalArgumentException("character prototype has no movement policy"));
        if (!"grounded".equals(movement.mode())) {
            throw new IllegalArgumentException("unsupported character movement mode: " + movement.mode());
        }
        return new CharacterMovementPolicy(movement.acceleration(), movement.walkSpeed(), movement.sprintSpeed(),
                movement.groundFrictionWithInput(), movement.groundFrictionWithoutInput(),
                movement.minimumFrictionSpeed());
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
