package com.juicyslew.moonstation14.ms14.animals.flight;

import com.juicyslew.moonstation14.ms14.character.components.FlightComponent;
import com.juicyslew.moonstation14.ms14.movement.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightGroundSpeedPolicyTest {
    private static final FlightComponent FLYER = new FlightComponent(1.5, .25);

    @Test void onlyGroundedNaturalFlightCapabilityScalesVoluntarySpeed() {
        assertEquals(.25, FlightGroundSpeedPolicy.voluntaryFactor(FLYER, FlightState.GROUNDED, false));
        assertEquals(.80, FlightGroundSpeedPolicy.voluntaryFactor(FLYER, FlightState.FLYING, false));
        assertEquals(.80, FlightGroundSpeedPolicy.voluntaryFactor(FLYER, FlightState.LANDING, false));
        assertEquals(1d, FlightGroundSpeedPolicy.voluntaryFactor(FLYER, FlightState.GROUNDED, true));
        assertEquals(1d, FlightGroundSpeedPolicy.voluntaryFactor(null, FlightState.GROUNDED, false));
    }

    @Test void cadenceUsesGroundedVoluntaryFactorButPreservesAirAndExternalBaselines() {
        assertEquals(.9375, FlightGroundSpeedPolicy.cadenceWalkSpeed(3.75, FLYER,
                FlightState.GROUNDED, true, false));
        assertEquals(3.75, FlightGroundSpeedPolicy.cadenceWalkSpeed(3.75, FLYER,
                FlightState.FLYING, false, false));
        assertEquals(3.75, FlightGroundSpeedPolicy.cadenceWalkSpeed(3.75, FLYER,
                FlightState.GROUNDED, true, true));
        assertEquals(3.75, FlightGroundSpeedPolicy.cadenceWalkSpeed(3.75, null,
                FlightState.GROUNDED, true, false));
    }

    @Test void factorFeedsSharedMotorOnceAndLeavesAirAndOrdinaryBodiesAtBaseline() {
        var policy = new CharacterMovementPolicy(200, 3.75, 7.5, 0, 0, .005);
        var motor = new CharacterMovementMotor(policy);
        var open = (MovementCollisionResolver) (position, displacement, grounded) ->
                new MovementCollisionResolver.CollisionResult(displacement, grounded);
        var command = new CharacterMovementCommand(1, 0, false, false);
        CharacterMovementState grounded = new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, true);
        CharacterMovementState airborne = new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, false);
        var slow = new CharacterMovementEnvironment(.05, 0, 0, 1, MovementVector.ZERO, 0, 0, open,
                1, FlightGroundSpeedPolicy.voluntaryFactor(FLYER, FlightState.GROUNDED, false));
        var normal = new CharacterMovementEnvironment(.05, 0, 0, 1, MovementVector.ZERO, 0, 0, open,
                 1.20, FlightGroundSpeedPolicy.voluntaryFactor(FLYER, FlightState.FLYING, false));
        for (int i = 0; i < 120; i++) {
            grounded = motor.tick(grounded, command, slow, false);
            airborne = motor.tick(airborne, command, normal, false);
        }
        assertEquals(.9375, grounded.velocity().x(), 1e-9);
        assertEquals(3.0, airborne.velocity().x(), 1e-9);
        assertEquals(3.75, motor.tick(new CharacterMovementState(MovementVector.ZERO, MovementVector.ZERO, true),
                command, new CharacterMovementEnvironment(.05, 0, 0, 1, MovementVector.ZERO, 0, 0, open,
                        1, FlightGroundSpeedPolicy.voluntaryFactor(null, FlightState.GROUNDED, false)), false)
                .velocity().x(), 1e-9, "non-flyer baseline remains unchanged");
    }
}
