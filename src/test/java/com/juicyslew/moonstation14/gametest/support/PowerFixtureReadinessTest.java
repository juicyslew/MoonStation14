package com.juicyslew.moonstation14.gametest.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerFixtureReadinessTest {
    private static final int GAME_TEST_REPORTER_LIMIT = 1024;

    @Test void timeoutExceptionMessageStaysBoundedForExtremeAndNormalTimingValues() {
        String extreme = "twoWiredDebugLampsTripAndGoDarkThenReclose: "
                + PowerFixtureReadiness.timeoutFailureMessage(Long.MAX_VALUE, Long.MIN_VALUE,
                        Long.MAX_VALUE, Long.MIN_VALUE);
        String normal = "twoWiredDebugLampsTripAndGoDarkThenReclose: "
                + PowerFixtureReadiness.timeoutFailureMessage(600, 600, 600, 600);

        for (String message : java.util.List.of(extreme, normal)) {
            assertTrue(message.length() <= 600, "GameTest diagnostic should remain comfortably below 1024 chars");
            assertTrue(message.length() < GAME_TEST_REPORTER_LIMIT);
            assertTrue(message.contains("helperTicks="));
            assertTrue(message.contains("levelTicks="));
            assertTrue(message.contains("levelGameTime="));
            assertTrue(message.contains("[power-fixture-readiness-timeout]"));
            assertFalse(message.contains("diagnostic="), "full state belongs in the server log, not exception text");
        }
    }
}
