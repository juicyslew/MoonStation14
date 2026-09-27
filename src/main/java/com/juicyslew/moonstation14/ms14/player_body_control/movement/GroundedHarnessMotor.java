package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;

import java.util.Objects;

/** Pure per-tick input adapter for a configured grounded harness; world collision remains caller-owned. */
public final class GroundedHarnessMotor {
    public static final int INPUT_QUANTIZATION = 1_000;

    private final CharacterMovementMotor motor;

    public GroundedHarnessMotor(CharacterMovementPolicy policy) {
        motor = new CharacterMovementMotor(Objects.requireNonNull(policy, "policy"));
    }

    /**
     * Applies local horizontal input to an existing character state. The supplied environment owns
     * tick timing, gravity, support/collision resolution, and surface/voluntary movement factors.
     */
    public CharacterMovementState tick(CharacterMovementState state, int wishX, int wishZ,
                                       boolean jump, boolean sprint, double yawDegrees,
                                       CharacterMovementEnvironment environment, boolean stunned) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(environment, "environment");
        if (wishX < -INPUT_QUANTIZATION || wishX > INPUT_QUANTIZATION
                || wishZ < -INPUT_QUANTIZATION || wishZ > INPUT_QUANTIZATION) {
            throw new IllegalArgumentException("quantized wishes must be between -1000 and 1000");
        }
        if (!Double.isFinite(yawDegrees) || yawDegrees < -180d || yawDegrees > 180d) {
            throw new IllegalArgumentException("yaw must be finite and between -180 and 180 degrees");
        }

        double localStrafe = wishX / (double) INPUT_QUANTIZATION;
        double localForward = wishZ / (double) INPUT_QUANTIZATION;
        double localLength = Math.hypot(localStrafe, localForward);
        if (localLength > 1d) {
            localStrafe /= localLength;
            localForward /= localLength;
        }
        double yaw = Math.toRadians(yawDegrees);
        double cosine = Math.cos(yaw);
        double sine = Math.sin(yaw);
        double worldX = localStrafe * cosine - localForward * sine;
        double worldZ = localForward * cosine + localStrafe * sine;

        CharacterMovementCommand command = new CharacterMovementCommand(worldX, worldZ, jump, sprint);
        return motor.tick(state, command, environment, stunned);
    }
}
