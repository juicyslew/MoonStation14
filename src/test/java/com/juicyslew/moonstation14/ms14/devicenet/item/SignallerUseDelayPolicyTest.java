package com.juicyslew.moonstation14.ms14.devicenet.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignallerUseDelayPolicyTest {
    @Test void oneSecondCooldownHasInclusiveTwentyTickBoundaryAndEarlyNoopDoesNotMoveDeadline() {
        long deadline = SignallerUseDelayPolicy.nextUse(0, 1f);
        assertEquals(20, deadline);
        assertTrue(SignallerUseDelayPolicy.coolingDown(0, deadline));
        assertTrue(SignallerUseDelayPolicy.coolingDown(19, deadline));
        assertFalse(SignallerUseDelayPolicy.coolingDown(20, deadline));
        // A cooldown response is an ACK/no-op; it does not write another deadline.
        assertEquals(20, deadline);
    }
}
