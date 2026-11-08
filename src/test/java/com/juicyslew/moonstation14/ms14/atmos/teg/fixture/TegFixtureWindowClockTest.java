package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TegFixtureWindowClockTest {
    @Test void exactWorldAndMatchedPrivateWindowIsRequired() {
        new TegFixtureWindowClock(10_000, 800).requireExactWindow(11_200, 2_000, 1_200, 1_200);
    }

    @Test void rejectsWorldClockDriftEvenWhenPrivateMeterLooksSettled() {
        var clock = new TegFixtureWindowClock(10_000, 800);
        assertThrows(IllegalArgumentException.class, () -> clock.requireExactWindow(11_199, 2_000, 1_200, 1_200));
    }

    @Test void rejectsPrivateSampleClockMismatchAndSkippedWindowEndpoint() {
        var clock = new TegFixtureWindowClock(10_000, 800);
        assertThrows(IllegalArgumentException.class, () -> clock.requireExactWindow(11_200, 1_999, 1_200, 1_200));
        assertThrows(IllegalArgumentException.class, () -> clock.requireExactWindow(11_201, 2_001, 1_201, 1_200));
    }

    @Test void rejectsInvalidOrOldBaselineInsteadOfResettingAnchor() {
        assertThrows(IllegalArgumentException.class, () -> new TegFixtureWindowClock(-1, 800));
        var clock = new TegFixtureWindowClock(10_000, 800);
        assertThrows(IllegalArgumentException.class, () -> clock.requireExactWindow(11_200, 799, 1_200, 1_200));
    }

    @Test void freshBaselineMustAdvanceBeyondAdmissionSolve() {
        assertFalse(TegFixtureWindowClock.isFreshSample(800, 800));
        assertFalse(TegFixtureWindowClock.isFreshSample(799, 800));
        assertFalse(TegFixtureWindowClock.isFreshSample(-1, 800));
        assertTrue(TegFixtureWindowClock.isFreshSample(801, 800));
    }
}
