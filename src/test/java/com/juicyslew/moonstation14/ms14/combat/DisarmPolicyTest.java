package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisarmPolicyTest {
    @Test void targetEligibilityRequiresHandsOrResolvedKnockdownAdmission() {
        assertTrue(DisarmPolicy.canTarget(true, false));
        assertTrue(DisarmPolicy.canTarget(false, true));
        assertTrue(DisarmPolicy.canTarget(true, true));
        assertFalse(DisarmPolicy.canTarget(false, false));
    }
    @Test void sourceChanceAndMarkerOverrides() {
        assertEquals(.75f, DisarmPolicy.failureChance(.75f, 0, false, false));
        assertEquals(1f, DisarmPolicy.failureChance(.75f, 0, true, false));
        assertEquals(0f, DisarmPolicy.failureChance(.75f, 0, false, true));
    }
}
