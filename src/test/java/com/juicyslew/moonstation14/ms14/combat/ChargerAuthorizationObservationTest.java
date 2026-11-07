package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChargerAuthorizationObservationTest {
    @Test
    void countChangedAuthorizationStampsOnceEvenWhenTickTimestampHasGap() {
        var unchanged = ChargerAuthorizationObservation.observe(100, 100, 4, 4, 1);
        assertTrue(unchanged.valid());
        assertFalse(unchanged.admitted());

        var admittedAfterUnobservedTick = ChargerAuthorizationObservation.observe(100, 102, 4, 5, 1);
        assertTrue(admittedAfterUnobservedTick.valid());
        assertTrue(admittedAfterUnobservedTick.admitted());
        assertEquals(1f, admittedAfterUnobservedTick.chargeDelta());

        assertFalse(ChargerAuthorizationObservation.observe(100, 102, 4, 6, 1).valid(),
                "one observed stamp change cannot justify two joules");
        assertFalse(ChargerAuthorizationObservation.observe(100, 100, 4, 5, 1).valid(),
                "charge without a changed authorization stamp is rejected");
    }

    @Test
    void twentyActualAdmissionsAcrossTwentyTwoWallObservationsStillCountAndChargeTwentyJoules() {
        long stamp = 100;
        float charge = 0;
        int admissions = 0;
        for (int wallTick = 0; wallTick < 22; wallTick++) {
            boolean poweredAndAuthorized = wallTick != 4 && wallTick != 12;
            long nextStamp = stamp;
            float nextCharge = charge;
            if (poweredAndAuthorized) {
                nextStamp = 101 + wallTick;
                nextCharge += 1;
            }
            var observed = ChargerAuthorizationObservation.observe(stamp, nextStamp, charge, nextCharge, 1);
            assertTrue(observed.valid(), "wallTick=" + wallTick + " decision=" + observed);
            if (observed.admitted()) admissions++;
            stamp = nextStamp;
            charge = nextCharge;
        }
        assertEquals(20, admissions);
        assertEquals(20f, charge);
    }
}
