package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GhostMovementMotorTest {
    private static final MovementCollisionResolver OPEN = (position, requested, wasOnGround) ->
            new MovementCollisionResolver.CollisionResult(requested, false);
    private final GhostMovementMotor motor = new GhostMovementMotor();

    @Test
    void forwardWishRotatesAtCardinalYaw() {
        assertDisplacement(0, 1000, 0d, 0d, 0d, .4d);
        assertDisplacement(0, 1000, 90d, -.4d, 0d, 0d);
        assertDisplacement(0, 1000, -90d, .4d, 0d, 0d);
    }

    @Test
    void diagonalInputIsNormalizedAndWalkSprintSpeedsAreExact() {
        CharacterMovementState diagonal = motor.tick(MovementVector.ZERO, 1000, 1000, 0, false, 0d, OPEN);
        assertEquals(.4d / Math.sqrt(2d), diagonal.position().x(), 1e-12);
        assertEquals(.4d / Math.sqrt(2d), diagonal.position().z(), 1e-12);
        CharacterMovementState walk = motor.tick(MovementVector.ZERO, 0, 1000, 0, false, 0d, OPEN);
        CharacterMovementState analogWalk = motor.tick(MovementVector.ZERO, 0, 500, 0, false, 0d, OPEN);
        CharacterMovementState sprint = motor.tick(MovementVector.ZERO, 0, 1000, 0, true, 0d, OPEN);
        assertEquals(.4d, walk.position().z(), 1e-12);
        assertEquals(.2d, analogWalk.position().z(), 1e-12);
        assertEquals(.6d, sprint.position().z(), 1e-12);
    }

    @Test
    void verticalWishHasFixedSpeedAndZeroInputStopsImmediately() {
        assertEquals(.6d, motor.tick(MovementVector.ZERO, 0, 0, 1, false, 0d, OPEN).position().y(), 1e-12);
        assertEquals(-.6d, motor.tick(MovementVector.ZERO, 0, 0, -1, true, 0d, OPEN).position().y(), 1e-12);
        CharacterMovementState moving = motor.tick(MovementVector.ZERO, 0, 1000, 1, true, 0d, OPEN);
        CharacterMovementState stopped = motor.tick(moving.position(), 0, 0, 0, false, 90d, OPEN);
        assertEquals(moving.position(), stopped.position());
        assertEquals(MovementVector.ZERO, stopped.velocity());
    }

    @Test
    void resolverIsCalledOnceAndItsMeasuredDisplacementIsPreserved() {
        AtomicInteger calls = new AtomicInteger();
        MovementCollisionResolver resolver = (position, requested, wasOnGround) -> {
            calls.incrementAndGet();
            assertEquals(MovementVector.ZERO, position);
            assertEquals(.4d, requested.z(), 1e-12);
            assertEquals(false, wasOnGround);
            return new MovementCollisionResolver.CollisionResult(new MovementVector(0d, 0d, .125d), true);
        };
        CharacterMovementState result = motor.tick(MovementVector.ZERO, 0, 1000, 0, false, 0d, resolver);
        assertEquals(1, calls.get());
        assertEquals(.125d, result.position().z(), 0d);
        assertEquals(false, result.onGround());
        assertEquals(0d, result.velocity().z(), 0d);
    }

    @Test
    void replayIsDeterministicAndLargeCoordinatesRemainWithinKernelCollisionBounds() {
        MovementVector initial = new MovementVector(29_999_999d, 100d, -29_999_999d);
        CharacterMovementState first = motor.tick(initial, 1000, 1000, 1, true, 37d, OPEN);
        CharacterMovementState replay = motor.tick(initial, 1000, 1000, 1, true, 37d, OPEN);
        assertEquals(first, replay);
        assertNotEquals(initial, first.position());
    }

    @Test
    void rejectsOutOfRangeAndNonfiniteInputs() {
        assertThrows(IllegalArgumentException.class, () -> motor.tick(MovementVector.ZERO, 1001, 0, 0, false, 0d, OPEN));
        assertThrows(IllegalArgumentException.class, () -> motor.tick(MovementVector.ZERO, 0, -1001, 0, false, 0d, OPEN));
        assertThrows(IllegalArgumentException.class, () -> motor.tick(MovementVector.ZERO, 0, 0, 2, false, 0d, OPEN));
        assertThrows(IllegalArgumentException.class, () -> motor.tick(MovementVector.ZERO, 0, 0, 0, false, Double.NaN, OPEN));
        assertThrows(IllegalArgumentException.class, () -> motor.tick(MovementVector.ZERO, 0, 0, 0, false, 181d, OPEN));
        assertThrows(NullPointerException.class, () -> motor.tick(null, 0, 0, 0, false, 0d, OPEN));
    }

    private void assertDisplacement(int wishX, int wishZ, double yaw, double expectedX, double expectedY,
                                    double expectedZ) {
        CharacterMovementState result = motor.tick(MovementVector.ZERO, wishX, wishZ, 0, false, yaw, OPEN);
        assertEquals(expectedX, result.position().x(), 1e-12);
        assertEquals(expectedY, result.position().y(), 0d);
        assertEquals(expectedZ, result.position().z(), 1e-12);
    }
}
