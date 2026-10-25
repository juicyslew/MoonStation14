package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.ms14.movement.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NaturalFlightMotorTest {
    private static final CharacterMovementMotor MOTOR = new CharacterMovementMotor(CharacterMovementPolicy.HUMAN);

    @Test void hoverAndVerticalSteeringUseSharedCollisionStepAndRetainConstraints() {
        MovementCollisionResolver open = (position, requested, wasGrounded) ->
                new MovementCollisionResolver.CollisionResult(requested, false);
        var env = new CharacterMovementEnvironment(.05, 32, 8, .98, MovementVector.ZERO, 32, .6,
                open, 1, 1, new MovementVector(0, 1, 0));
        var state = new CharacterMovementState(new MovementVector(0, 10, 0), MovementVector.ZERO, false);
        var result = MOTOR.tick(state, new CharacterMovementCommand(0, 0, false, false), env,
                false, false, 4, true);
        assertEquals(.25, result.position().y() - 10, 1e-9);
        assertEquals(5, result.velocity().y(), 1e-9);
        assertFalse(result.onGround());
    }

    @Test void naturalFlightCancelsOnlyAmbientGravityAndKeepsSingularityAndTetherImpulsesOnce() {
        MovementCollisionResolver open = (position, requested, wasGrounded) ->
                new MovementCollisionResolver.CollisionResult(requested, false);
        var env = new CharacterMovementEnvironment(.05, 32, 8, .98,
                new MovementVector(0, .4, 0), 32, .6, open, 1, 1,
                new MovementVector(0, .6, 0));
        var state = new CharacterMovementState(new MovementVector(0, 10, 0), MovementVector.ZERO, false);
        var result = MOTOR.tick(state, new CharacterMovementCommand(0, 0, false, false), env,
                false, false, 0, true);
        assertEquals(.05, result.position().y() - 10, 1e-9);
        assertEquals(1, result.velocity().y(), 1e-9);
    }

    @Test void realFloorClipStopsLandingWithoutPhaseAndExternalThrowLeaseKeepsMomentum() {
        MovementCollisionResolver floor = (position, requested, wasGrounded) -> {
            if (requested.y() < 0 && position.y() + requested.y() < 0.9)
                return new MovementCollisionResolver.CollisionResult(
                        new MovementVector(requested.x(), .9 - position.y(), requested.z()), true);
            return new MovementCollisionResolver.CollisionResult(requested, false);
        };
        var env = new CharacterMovementEnvironment(.05, 32, 8, .98,
                new MovementVector(0, .4, 0), 32, .6, floor, 1, 1,
                new MovementVector(0, .6, 0));
        var state = new CharacterMovementState(new MovementVector(0, 1, 0), MovementVector.ZERO, false);
        var landed = MOTOR.tick(state, new CharacterMovementCommand(0, 0, false, false), env,
                false, false, -4, true);
        assertEquals(.9, landed.position().y(), 1e-9);
        assertTrue(landed.onGround());
        assertEquals(0, landed.velocity().y(), 1e-9);

        var thrown = new CharacterMovementState(new MovementVector(0, 10, 0), new MovementVector(0, 3, 0), false);
        var inFlight = MOTOR.tick(thrown, new CharacterMovementCommand(0, 0, false, false), env,
                false, true);
        assertEquals(.2, inFlight.position().y() - 10, 1e-9);
        assertEquals((3 + .4 + .6 - 32 * .05) * .98, inFlight.velocity().y(), 1e-9);
    }
}
