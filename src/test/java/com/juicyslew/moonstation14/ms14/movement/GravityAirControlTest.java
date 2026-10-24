package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GravityAirControlTest {
    private static final GravityAirControl PROFILE = new GravityAirControl(.05d, .91d, 7.84d);

    @Test
    void validatesPhysicalRangesAndScalesRetentionByElapsedTime() {
        assertEquals(.91d, PROFILE.retentionFor(.05d), 1e-12);
        assertEquals(Math.sqrt(.91d), PROFILE.retentionFor(.025d), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new GravityAirControl(0d, .91d, 1d));
        assertThrows(IllegalArgumentException.class, () -> new GravityAirControl(.05d, 0d, 1d));
        assertThrows(IllegalArgumentException.class, () -> new GravityAirControl(.05d, 1.01d, 1d));
        assertThrows(IllegalArgumentException.class, () -> new GravityAirControl(.05d, .91d, -1d));
        assertThrows(IllegalArgumentException.class, () -> PROFILE.retentionFor(Double.NaN));
    }

    @Test
    void oldEnvironmentConstructorsDefaultToEmptyAndCanonicalPreservesProfileAndOtherValues() {
        MovementVector impulse = new MovementVector(1d, 0d, 0d);
        MovementVector tether = new MovementVector(0d, 0d, 1d);
        MovementCollisionResolver resolver = (position, requested, grounded) ->
                new MovementCollisionResolver.CollisionResult(requested, false);
        CharacterMovementEnvironment oldFull = new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d,
                impulse, 2d, .6d, resolver, .75d, .8d, tether);
        CharacterMovementEnvironment oldShort = new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d,
                MovementVector.ZERO, 2d, .6d, resolver);
        CharacterMovementEnvironment profiled = new CharacterMovementEnvironment(.05d, 32d, 8.4d, .98d,
                impulse, 2d, .6d, resolver, .75d, .8d, tether, Optional.of(PROFILE));

        assertTrue(oldFull.gravityAirControl().isEmpty());
        assertTrue(oldShort.gravityAirControl().isEmpty());
        assertSame(PROFILE, profiled.gravityAirControl().orElseThrow());
        assertEquals(impulse, profiled.externalImpulse());
        assertEquals(tether, profiled.constraintImpulse());
        assertEquals(.75d, profiled.surfaceMovementFactor());
        assertEquals(.8d, profiled.voluntarySpeedFactor());
    }
}
