package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalFlightPredictionPolicyTest {
    @Test void remainingTicksCoverUpcomingStepsAndExpire() {
        var policy = new ExternalFlightPredictionPolicy();
        policy.reset(6);
        for (int i = 0; i < 5; i++) assertTrue(policy.advanceFrame());
        assertFalse(policy.advanceFrame());
        assertFalse(policy.advanceFrame());
    }

    @Test void authoritativeRefreshReplacesOldFlightAndClearDropsIt() {
        var policy = new ExternalFlightPredictionPolicy();
        policy.reset(6);
        assertTrue(policy.advanceFrame());
        policy.reset(1);
        assertFalse(policy.advanceFrame());
        policy.reset(6);
        policy.clear();
        assertFalse(policy.advanceFrame());
    }
}
