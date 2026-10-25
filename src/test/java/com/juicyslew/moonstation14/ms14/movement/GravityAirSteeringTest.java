package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GravityAirSteeringTest {
    private static final MovementCollisionResolver OPEN = (p, d, g) ->
            new MovementCollisionResolver.CollisionResult(d, g);
    private static final GravityAirControl PROFILE = new GravityAirControl(.05d, .91d, 7.84d);

    private static CharacterMovementEnvironment env(double dt, double gravity, MovementVector constraint, boolean sprint) {
        GravityAirControl control = sprint ? new GravityAirControl(.05d, .91d, 10.192d) : PROFILE;
        return new CharacterMovementEnvironment(dt, gravity, 8.4d, .98d, MovementVector.ZERO,
                10d, 0d, OPEN, 1d, 1d, constraint, Optional.of(control));
    }
    private static CharacterMovementState state(double x, double z, boolean ground) {
        return new CharacterMovementState(MovementVector.ZERO, new MovementVector(x, 0d, z), ground);
    }

    @Test
    void perpendicularWishStartsImmediatelyAndMoveUsesPreDampingVelocity() {
        var motor = new CharacterMovementMotor(new CharacterMovementPolicy(20d, 20d, 20d, 20d, 20d, .005d));
        for (boolean sprint : new boolean[]{false, true}) {
            double speed = sprint ? 5.625d : 3.125d;
            double acceleration = sprint ? 10.192d : 7.84d;
            var result = motor.tick(state(speed, 0d, false),
                    new CharacterMovementCommand(0d, 1d, false, sprint), env(.05d, 32d, MovementVector.ZERO, sprint), false);
            assertEquals(speed * .91d, result.velocity().x(), 1e-12);
            assertEquals((acceleration * .05d) * .91d, result.velocity().z(), 1e-12);
            assertEquals(speed * .05d, result.position().x(), 1e-12);
            assertEquals(acceleration * .05d * .05d, result.position().z(), 1e-12);
        }
    }

    @Test
    void noInputDampsAndCircleSteeringCannotExceedEntryOrWishSpeed() {
        var motor = new CharacterMovementMotor(new CharacterMovementPolicy(20d, 3.125d, 5.625d, 20d, 20d, .005d));
        var current = state(5.625d, 0d, false);
        for (int i = 0; i < 200; i++) {
            double angle = Math.toRadians(i * 13d);
            current = motor.tick(current, new CharacterMovementCommand(Math.cos(angle), Math.sin(angle), false, true),
                    env(.05d, 32d, MovementVector.ZERO, true), false);
            assertTrue(Double.isFinite(current.velocity().horizontalLength()));
            assertTrue(current.velocity().horizontalLength() <= 5.625d + 1e-12);
        }
        var coast = motor.tick(state(5.625d, 0d, false), new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 32d, MovementVector.ZERO, false), false);
        assertEquals(5.11875d, coast.velocity().x(), 1e-12);
    }

    @Test
    void fastImpulseAndConstraintAreNotVoluntaryCappedButExperiencePhysicalDrag() {
        var motor = new CharacterMovementMotor(new CharacterMovementPolicy(20d, 20d, 20d, 20d, 20d, .005d));
        var impulseEnv = new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d,
                new MovementVector(0d, 0d, 3d), 3d, 0d, OPEN, 1d, 1d, MovementVector.ZERO, Optional.of(PROFILE));
        var impulse = motor.tick(state(0d, 0d, false), new CharacterMovementCommand(0d, 0d, false, false), impulseEnv, false);
        assertEquals(.15d, impulse.position().z(), 1e-12);
        assertEquals(2.73d, impulse.velocity().z(), 1e-12);
        var tether = motor.tick(state(0d, 0d, false), new CharacterMovementCommand(0d, 0d, false, false),
                env(.05d, 32d, new MovementVector(0d, 0d, 3d), false), false);
        assertEquals(.15d, tether.position().z(), 1e-12);
        assertEquals(2.73d, tether.velocity().z(), 1e-12);
    }

    @Test
    void profileDoesNotChangeGroundZeroGravityOrFlightExclusions() {
        var motor = new CharacterMovementMotor(new CharacterMovementPolicy(20d, 20d, 20d, 20d, 20d, .005d));
        var profiledGround = motor.tick(state(0d, 0d, true), new CharacterMovementCommand(1d, 0d, false, false),
                env(.05d, 32d, MovementVector.ZERO, false), false);
        var emptyGround = motor.tick(state(0d, 0d, true), new CharacterMovementCommand(1d, 0d, false, false),
                new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d, MovementVector.ZERO, 10d, 0d, OPEN), false);
        assertEquals(emptyGround, profiledGround);
        for (boolean natural : new boolean[]{false, true}) {
            var profiled = motor.tick(state(2d, 0d, false), new CharacterMovementCommand(0d, 1d, false, false),
                    env(.05d, 0d, MovementVector.ZERO, false), false, false, 0d, natural);
            var empty = motor.tick(state(2d, 0d, false), new CharacterMovementCommand(0d, 1d, false, false),
                    new CharacterMovementEnvironment(.05d, 0d, 8.4d, .98d, MovementVector.ZERO, 10d, 0d, OPEN),
                    false, false, 0d, natural);
            assertEquals(empty, profiled);
        }
        var external = motor.tick(state(2d, 0d, false), new CharacterMovementCommand(0d, 1d, false, false),
                env(.05d, 32d, MovementVector.ZERO, false), false, true);
        assertEquals(2d, external.velocity().x(), 1e-12);
        assertEquals(.1d, external.position().x(), 1e-12);
    }
}
