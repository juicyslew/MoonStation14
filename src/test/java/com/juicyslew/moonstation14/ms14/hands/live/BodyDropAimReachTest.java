package com.juicyslew.moonstation14.ms14.hands.live;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyDropAimReachTest {
    @Test void nominatedPointUsesExactThreeAndAHalfBlockReach() {
        assertTrue(BodyItemTargetProbe.withinDropAimReach(3.5));
        assertTrue(BodyItemTargetProbe.withinDropAimReach(Math.nextDown(3.5)));
        assertFalse(BodyItemTargetProbe.withinDropAimReach(Math.nextUp(3.5)));
        assertFalse(BodyItemTargetProbe.withinDropAimReach(Double.NaN));
        assertFalse(BodyItemTargetProbe.withinDropAimReach(Double.POSITIVE_INFINITY));
        assertFalse(BodyItemTargetProbe.withinDropAimReach(0));
    }
}
