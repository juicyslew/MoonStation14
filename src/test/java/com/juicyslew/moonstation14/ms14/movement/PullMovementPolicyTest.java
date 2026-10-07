package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PullMovementPolicyTest {
    @org.junit.jupiter.api.Test
    void modifiedPolicyScalesAccelerationAndFrictionButPreservesWalkSpeeds() {
        CharacterMovementPolicy base = new CharacterMovementPolicy(20d, 2.5d, 4.5d, 20d, 10d, .005d);
        for (double factor : new double[] {.4d, .2d, 0d}) {
            CharacterMovementPolicy modified = base.withAccelerationAndFrictionFactor(factor);
            assertEquals(20d * factor, modified.accelerationPerSecondSquared());
            assertEquals(20d * factor, modified.groundFrictionWithInputPerSecond());
            assertEquals(10d * factor, modified.groundFrictionNoInputPerSecond());
            assertEquals(2.5d, modified.walkSpeedPerSecond());
            assertEquals(4.5d, modified.sprintSpeedPerSecond());
        }
        assertEquals(base.withAccelerationAndFrictionFactor(.4d), base.withDownFriction());
    }

    @Test
    void combinesAuthoritativePullWithKnockdownAndRestoresAfterPullEnds() {
        assertEquals(1d, PullMovementPolicy.voluntaryMovementFactor(false, false));
        assertEquals(PullMovementPolicy.MOVEMENT_FACTOR,
                PullMovementPolicy.voluntaryMovementFactor(false, true));
        assertEquals(CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR,
                PullMovementPolicy.voluntaryMovementFactor(true, false));
        assertEquals(CharacterMovementPolicy.HUMAN_KNOCKDOWN_SPEED_FACTOR * PullMovementPolicy.MOVEMENT_FACTOR,
                PullMovementPolicy.voluntaryMovementFactor(true, true));
        // A false authoritative snapshot (and session reset) uses this unmodified factor.
        assertEquals(1d, PullMovementPolicy.voluntaryMovementFactor(false, false));
    }

    @Test
    void resolvedDownPostureDoesNotStackLegacyKnockdownSpeed() {
        assertEquals(.4d, PullMovementPolicy.voluntaryMovementFactor(true, true, .4d, 1d));
        assertEquals(.2d, PullMovementPolicy.voluntaryMovementFactor(true, true, .2d, 1d));
        assertEquals(0d, PullMovementPolicy.voluntaryMovementFactor(true, true, 0d, 1d));
        assertEquals(.9d, PullMovementPolicy.voluntaryMovementFactor(false, false, 1d, .9d));
        assertEquals(.8d, PullMovementPolicy.voluntaryMovementFactor(false, false, 1d, .8d));
    }
}
