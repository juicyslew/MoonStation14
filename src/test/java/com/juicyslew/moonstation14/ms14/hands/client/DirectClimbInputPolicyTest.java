package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectClimbInputPolicyTest {
    @Test void sharedDefaultKClimbsOnlyStandingWithNoModifier() {
        assertTrue(DirectClimbInputPolicy.mayClimb(true, false, false, false, false));
        assertFalse(DirectClimbInputPolicy.mayClimb(true, true, false, false, false), "plain K remains prone roll");
        assertFalse(DirectClimbInputPolicy.mayClimb(true, false, true, false, false), "Shift+K remains body turn");
        assertFalse(DirectClimbInputPolicy.mayClimb(true, false, false, true, false));
        assertFalse(DirectClimbInputPolicy.mayClimb(true, false, false, false, true));
    }

    @Test void separatelyReboundClimbKeyDoesNotInheritRollContextGate() {
        assertTrue(DirectClimbInputPolicy.mayClimb(false, true, false, false, false));
        assertTrue(DirectClimbInputPolicy.mayClimb(false, false, true, false, false));
    }
}
