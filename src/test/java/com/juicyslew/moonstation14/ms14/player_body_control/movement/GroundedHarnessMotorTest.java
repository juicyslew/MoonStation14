package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy;
import com.juicyslew.moonstation14.ms14.movement.CharacterMovementState;
import com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundedHarnessMotorTest {
    private static final double DT = 1d / 20d;
    private static final MovementCollisionResolver GROUNDED = (position, requested, wasOnGround) ->
            new MovementCollisionResolver.CollisionResult(requested, wasOnGround);
    private static final MovementCollisionResolver OPEN = (position, requested, wasOnGround) ->
            new MovementCollisionResolver.CollisionResult(requested, false);
    private final GroundedHarnessMotor motor = new GroundedHarnessMotor(CharacterMovementPolicy.HUMAN);

    @Test
    void usesSuppliedSpeciesPolicyRatherThanHumanConstants() {
        CharacterMovementPolicy pig = new CharacterMovementPolicy(20d, 4d, 4d, 20d, 20d, .005d);
        GroundedHarnessMotor pigMotor = new GroundedHarnessMotor(pig);
        CharacterMovementState result = state(MovementVector.ZERO, true);
        for (int tick = 0; tick < 120; tick++) {
            result = pigMotor.tick(result, 0, 1000, false, false, 0d, env(0d, 0d, GROUNDED), false);
        }
        assertEquals(4d, result.velocity().z(), 1e-9);
    }

    @Test
    void walkSprintAndAccelerationUseHumanPolicy() {
        CharacterMovementState walk = state(MovementVector.ZERO, true);
        CharacterMovementState sprint = walk;
        for (int tick = 0; tick < 120; tick++) {
            walk = tick(walk, 0, 1000, false, false, 0d, env(0d, 0d, GROUNDED), false);
            sprint = tick(sprint, 0, 1000, false, true, 0d, env(0d, 0d, GROUNDED), false);
        }
        assertEquals(2.5d, walk.velocity().z(), 1e-9);
        assertEquals(4.5d, sprint.velocity().z(), 1e-9);
        assertEquals(20d, CharacterMovementPolicy.HUMAN.accelerationPerSecondSquared());
        CharacterMovementState first = tick(state(MovementVector.ZERO, false), 0, 1000,
                false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(2.5d, first.velocity().z(), 1e-12);
    }

    @Test
    void yawCardinalsDiagonalAndPartialAnalogPreserveWishMagnitude() {
        assertDisplacement(0, 1000, 0d, 0d, 2.5d);
        assertDisplacement(0, 1000, 90d, -2.5d, 0d);
        assertDisplacement(0, 1000, -90d, 2.5d, 0d);
        CharacterMovementState diagonal = tick(state(MovementVector.ZERO, false), 1000, 1000,
                false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(2.5d / Math.sqrt(2d), diagonal.velocity().x(), 1e-12);
        assertEquals(2.5d / Math.sqrt(2d), diagonal.velocity().z(), 1e-12);
        CharacterMovementState partial = tick(state(MovementVector.ZERO, false), 0, 250,
                false, false, 0d, env(0d, 0d, OPEN), false);
        assertEquals(.625d, partial.velocity().z(), 1e-12);
    }

    @Test
    void neutralGroundFrictionStopsHumanMomentumInOneTick() {
        CharacterMovementState result = tick(state(new MovementVector(2.5d, 0d, 0d), true),
                0, 0, false, false, 0d, env(0d, 0d, GROUNDED), false);
        assertEquals(MovementVector.ZERO, result.velocity());
    }

    @Test
    void lubeSurfaceFactorScalesAccelerationAndKnockdownScalesVoluntarySpeed() {
        CharacterMovementEnvironment lube = new CharacterMovementEnvironment(DT, 0d, 0d, .98d,
                MovementVector.ZERO, 0d, 0d, GROUNDED, .05d, 1d);
        CharacterMovementState onLube = tick(state(MovementVector.ZERO, true), 0, 1000,
                false, false, 0d, lube, false);
        assertEquals(.125d, onLube.velocity().z(), 1e-12);

        CharacterMovementEnvironment knockdown = new CharacterMovementEnvironment(DT, 0d, 0d, .98d,
                MovementVector.ZERO, 0d, 0d, GROUNDED, 1d,
                CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR);
        CharacterMovementState crawling = state(MovementVector.ZERO, true);
        for (int tick = 0; tick < 120; tick++) {
            crawling = tick(crawling, 0, 1000, false, false, 0d, knockdown, false);
        }
        assertEquals(1d, crawling.velocity().z(), 1e-9);
    }

    @Test
    void stunSuppressesVoluntaryInputWithoutErasingExternalMomentum() {
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(DT, 0d, 0d, .98d,
                MovementVector.ZERO, 0d, 0d, OPEN);
        CharacterMovementState result = tick(state(new MovementVector(3d, 0d, 0d), false),
                0, 1000, false, false, 0d, environment, true);
        assertEquals(3d, result.velocity().x(), 1e-12);
        assertEquals(0d, result.velocity().z(), 1e-12);
        assertEquals(.15d, result.position().x(), 1e-12);
    }

    @Test
    void groundedJumpUsesInjectedGravityAndReturnsResolverGrounding() {
        CharacterMovementEnvironment environment = new CharacterMovementEnvironment(DT, .08d * 400d,
                8.4d, .98d, MovementVector.ZERO, 0d, 0d, GROUNDED);
        CharacterMovementState result = tick(state(MovementVector.ZERO, true), 0, 0,
                true, false, 0d, environment, false);
        assertEquals(.42d, result.position().y(), 1e-12);
        assertEquals((8.4d - 32d * DT) * .98d, result.velocity().y(), 1e-12);
        assertTrue(result.onGround(), "ground state is the resolver's result, not adapter policy");
    }

    @Test
    void collisionResolverDisplacementAndSupportResultAreReturned() {
        MovementCollisionResolver clipped = (position, requested, wasOnGround) ->
                new MovementCollisionResolver.CollisionResult(new MovementVector(requested.x(), 0d, 0d), true);
        CharacterMovementState result = tick(state(MovementVector.ZERO, false), 0, 1000,
                false, false, 0d, env(0d, 0d, clipped), false);
        assertEquals(MovementVector.ZERO, result.position());
        assertEquals(0d, result.velocity().z(), 0d);
        assertTrue(result.onGround());
    }

    @Test
    void rejectsInvalidQuantizedWishesAndYaw() {
        CharacterMovementEnvironment environment = env(0d, 0d, OPEN);
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 1001,
                0, false, false, 0d, environment, false));
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 0,
                -1001, false, false, 0d, environment, false));
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 0,
                0, false, false, Double.NaN, environment, false));
        assertThrows(IllegalArgumentException.class, () -> tick(state(MovementVector.ZERO, false), 0,
                0, false, false, 180.1d, environment, false));
        assertThrows(NullPointerException.class, () -> motor.tick(null, 0, 0,
                false, false, 0d, environment, false));
    }

    private void assertDisplacement(int wishX, int wishZ, double yaw, double expectedX, double expectedZ) {
        CharacterMovementState result = tick(state(MovementVector.ZERO, false), wishX, wishZ,
                false, false, yaw, env(0d, 0d, OPEN), false);
        assertEquals(expectedX * DT, result.position().x(), 1e-12);
        assertEquals(expectedZ * DT, result.position().z(), 1e-12);
    }

    private CharacterMovementState tick(CharacterMovementState state, int wishX, int wishZ, boolean jump,
                                        boolean sprint, double yaw, CharacterMovementEnvironment environment,
                                        boolean stunned) {
        return motor.tick(state, wishX, wishZ, jump, sprint, yaw, environment, stunned);
    }

    private static CharacterMovementState state(MovementVector velocity, boolean onGround) {
        return new CharacterMovementState(MovementVector.ZERO, velocity, onGround);
    }

    private static CharacterMovementEnvironment env(double gravity, double jump,
                                                    MovementCollisionResolver resolver) {
        return new CharacterMovementEnvironment(DT, gravity, jump, .98d, MovementVector.ZERO,
                0d, 0d, resolver);
    }
}
