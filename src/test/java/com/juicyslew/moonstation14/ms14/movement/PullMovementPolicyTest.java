package com.juicyslew.moonstation14.ms14.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PullMovementPolicyTest {
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
}
