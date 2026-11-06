package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;

import java.util.Objects;

/**
 * Pure per-tick input adapter for a configured grounded harness; world collision remains caller-owned.
 * Preserve the shared server/live-prediction/replay phases and grounded transition rules; see
 * docs/shuttle-relocation-sprint/CHARACTER-INPUT-CADENCE.md.
 */
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
        return tick(state, wishX, wishZ, jump, sprint, yawDegrees, environment, stunned, false);
    }

    public CharacterMovementState tick(CharacterMovementState state, int wishX, int wishZ,
                                       boolean jump, boolean sprint, double yawDegrees,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight) {
        return tick(state, wishX, wishZ, jump, sprint, yawDegrees, environment, stunned, externalFlight, null);
    }

    /** Optional frozen baseline already expressed in the motor's current velocity basis. */
    public CharacterMovementState tick(CharacterMovementState state, int wishX, int wishZ,
                                       boolean jump, boolean sprint, double yawDegrees,
                                       CharacterMovementEnvironment environment, boolean stunned,
                                       boolean externalFlight, MovementVector baselineInMotorBasis) {
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

        boolean applyMinecraftAirConditioning = !externalFlight
                && environment.gravityPerSecondSquared() > 0d;
        CharacterMovementState prepared = state;
        var airReference = !state.onGround() ? state.airMotionReference() : null;
        if (applyMinecraftAirConditioning) {
            var velocity = state.velocity();
            if (airReference != null) velocity = relative(velocity, baselineInMotorBasis == null
                    ? airReference.pointVelocity() : baselineInMotorBasis);
            MovementVector baseline = airReference == null ? MovementVector.ZERO : baselineInMotorBasis == null
                    ? airReference.pointVelocity() : baselineInMotorBasis;
            double threshold = GroundedHarnessPhysics.VELOCITY_SNAP_THRESHOLD_BLOCKS_PER_SECOND;
            MovementVector snappedRelative = new MovementVector(
                    Math.abs(velocity.x()) < threshold ? 0d : velocity.x(),
                    Math.abs(velocity.y()) < threshold ? 0d : velocity.y(),
                    Math.abs(velocity.z()) < threshold ? 0d : velocity.z());
            if (airReference != null) snappedRelative = restore(snappedRelative, baseline);
            prepared = new CharacterMovementState(state.position(), snappedRelative, state.onGround(), airReference);
        }
        CharacterMovementCommand command = new CharacterMovementCommand(worldX, worldZ, jump, sprint);
        MovementVector baseline = airReference == null ? MovementVector.ZERO : baselineInMotorBasis == null
                ? airReference.pointVelocity() : baselineInMotorBasis;
        CharacterMovementState result = motor.tick(prepared, command, environment, stunned, externalFlight, baseline);
        // Players receive Minecraft's 0.91 airborne horizontal retention (not the 0.98 NoAI multiplier).
        if (applyMinecraftAirConditioning && !state.onGround()) {
            var velocity = result.velocity();
            if (airReference != null) velocity = relative(velocity, baseline);
            result = new CharacterMovementState(result.position(),
                    new MovementVector(
                            velocity.x() * GroundedHarnessPhysics.AIR_HORIZONTAL_RETENTION, velocity.y(),
                            velocity.z() * GroundedHarnessPhysics.AIR_HORIZONTAL_RETENTION), result.onGround(), airReference);
        }
        if (airReference != null && applyMinecraftAirConditioning) {
            var velocity = restore(result.velocity(), baselineInMotorBasis == null
                    ? airReference.pointVelocity() : baselineInMotorBasis);
            result = new CharacterMovementState(result.position(), velocity, result.onGround(), airReference);
        }
        return result;
    }

    private static MovementVector relative(MovementVector velocity, MovementVector baseline) {
        return new MovementVector(velocity.x() - baseline.x(), velocity.y() - baseline.y(), velocity.z() - baseline.z());
    }
    private static MovementVector restore(MovementVector relative, MovementVector baseline) {
        return new MovementVector(relative.x() + baseline.x(), relative.y() + baseline.y(), relative.z() + baseline.z());
    }
}
