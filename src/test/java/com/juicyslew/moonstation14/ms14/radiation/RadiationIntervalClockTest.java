package com.juicyslew.moonstation14.ms14.radiation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadiationIntervalClockTest {
    @Test void consecutiveServerTicksDoseAtTwentyAndFortyOnly() {
        RadiationIntervalClock clock = new RadiationIntervalClock();
        int dueCount = 0;
        for (int tick = 0; tick <= 40; tick++) {
            int elapsed = clock.advance(tick);
            if (elapsed > 0) {
                dueCount++;
                assertEquals(20, elapsed);
                assertTrue(tick == 20 || tick == 40, "one-second intervals trigger at 20-tick boundaries");
            }
        }
        assertEquals(2, dueCount);
    }

    @Test void lagIsIntegratedInBoundedIntervalsAndClockRollbackDoesNotDose() {
        RadiationIntervalClock clock = new RadiationIntervalClock();
        assertEquals(0, clock.advance(100));
        assertEquals(20, clock.advance(150));
        assertEquals(20, clock.advance(151));
        assertEquals(0, clock.advance(5));
    }
}
