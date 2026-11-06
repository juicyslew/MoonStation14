package com.juicyslew.moonstation14.ms14.player_body_control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterCadenceDiagnosticsTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void reservesThirtyRowsForImportantTransitionsWithinHardCap() {
        var sampler = new CharacterCadenceDiagnostics();
        assertFalse(sampler.allow(0, 0, true));
        assertFalse(sampler.allow(-1, 0, false));
        for (int i = 0; i < 90; i++) assertTrue(sampler.allow(1, i, false));
        assertFalse(sampler.allow(1, 90, false));
        for (int i = 90; i < 120; i++) assertTrue(sampler.allow(1, i, true));
        assertFalse(sampler.allow(1, 121, true));
    }

    @Test
    void expiresAfterSixtySecondsAndStartsFreshForNewEpoch() {
        var sampler = new CharacterCadenceDiagnostics();
        assertTrue(sampler.allow(1, 10, false));
        assertFalse(sampler.allow(1, 10 + 60 * SECOND + 1, true));
        assertTrue(sampler.allow(2, 10 + 60 * SECOND + 1, true));
    }

    @Test
    void resetAllowsSameEpochAgainAndBackwardsClockFailsClosed() {
        var sampler = new CharacterCadenceDiagnostics();
        assertTrue(sampler.allow(7, 100, false));
        assertFalse(sampler.allow(7, 99, true));
        sampler.reset();
        assertTrue(sampler.allow(7, 100, true));
    }
}
