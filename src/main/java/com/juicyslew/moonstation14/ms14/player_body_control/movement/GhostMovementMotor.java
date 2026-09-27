package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;

import java.util.Objects;

/**
 * Deterministic flight adapter for the existing movement kernel. This intentionally models
 * ghost flight, not full SS14 ghost collision parity (in particular GhostImpassable handling).
 * It performs no Entity movement; callers supply their world collision resolver.
 */
public final class GhostMovementMotor {
    public static final int INPUT_QUANTIZATION = 1_000;
    public static final double WALK_SPEED_PER_SECOND = 8d;
    public static final double SPRINT_SPEED_PER_SECOND = 12d;
    public static final double VERTICAL_SPEED_PER_SECOND = 12d;
    private static final double SECONDS_PER_TICK = .05d;

    private static final CharacterMovementEnvironment ENVIRONMENT_TEMPLATE = new CharacterMovementEnvironment(
            SECONDS_PER_TICK, 0d, 0d, 1d, MovementVector.ZERO, 0d, 0d,
            (position, displacement, wasOnGround) -> new MovementCollisionResolver.CollisionResult(displacement, false));

    private final CharacterMovementMotor motor = new CharacterMovementMotor(
            new CharacterMovementPolicy(20d, WALK_SPEED_PER_SECOND, SPRINT_SPEED_PER_SECOND, 0d, 0d, 0d));

    /**
     * Applies quantized local wishes (strafe, forward) and an independent vertical flight wish.
     * Horizontal velocity is reset each tick by ghost flight policy, so turns are immediate and
     * zero horizontal input stops immediately rather than retaining motor inertia.
     *
     * @param position current world position
     * @param wishX quantized local strafe wish in [-1000, 1000]
     * @param wishZ quantized local forward wish in [-1000, 1000]
     * @param verticalWish one of -1, 0, or 1; vertical speed is 12 blocks/s regardless of sprint
     * @param sprint selects 12 blocks/s horizontal travel instead of 8 blocks/s
     * @param yawDegrees yaw in degrees, bounded to [-180, 180]
     * @param collisionResolver caller-owned world resolver (e.g. Entity.move adapter)
     * @return resolved position and velocity; displacement is the resolver's measured result
     */
    public CharacterMovementState tick(MovementVector position, int wishX, int wishZ, int verticalWish,
                                       boolean sprint, double yawDegrees,
                                       MovementCollisionResolver collisionResolver) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(collisionResolver, "collisionResolver");
        if (wishX < -INPUT_QUANTIZATION || wishX > INPUT_QUANTIZATION
                || wishZ < -INPUT_QUANTIZATION || wishZ > INPUT_QUANTIZATION) {
            throw new IllegalArgumentException("quantized wishes must be between -1000 and 1000");
        }
        if (verticalWish < -1 || verticalWish > 1) {
            throw new IllegalArgumentException("vertical wish must be -1, 0, or 1");
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

        CharacterMovementState state = new CharacterMovementState(position,
                new MovementVector(0d, verticalWish * VERTICAL_SPEED_PER_SECOND, 0d), false);
        CharacterMovementCommand command = new CharacterMovementCommand(worldX, worldZ, false, sprint);
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(
                ENVIRONMENT_TEMPLATE.secondsPerTick(), ENVIRONMENT_TEMPLATE.gravityPerSecondSquared(),
                ENVIRONMENT_TEMPLATE.jumpVelocityPerSecond(), ENVIRONMENT_TEMPLATE.verticalDrag(),
                ENVIRONMENT_TEMPLATE.externalImpulse(), ENVIRONMENT_TEMPLATE.maximumExternalImpulsePerTick(),
                ENVIRONMENT_TEMPLATE.maxUpwardStepBlocks(), collisionResolver);
        CharacterMovementState resolved = motor.tick(state, command, environment, false);
        // Flight is never grounded even if a generic world resolver reports support.
        return new CharacterMovementState(resolved.position(), resolved.velocity(), false);
    }
}
