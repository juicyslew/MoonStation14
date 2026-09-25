package com.juicyslew.moonstation14.ms14.movement.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MovementSurfaceTraceTest {
    @Test
    void summarizesSurfaceCountsTransitionsAndPreFrictionProjection() {
        MovementSurfaceTrace trace = new MovementSurfaceTrace();
        for (int tick = 0; tick < MovementSurfaceTrace.SUMMARY_INTERVAL_TICKS; tick++) {
            assertNull(trace.sample(tick, true, true, .05d, 6d, 1.8d, 2d));
        }

        MovementSurfaceTrace.Summary summary = trace.sample(100, true, true, 1d, 4d, 1.8d, 2d);
        assertEquals(101, summary.samples());
        assertEquals(.05d, summary.minimumFactor());
        assertEquals(1d, summary.maximumFactor());
        assertEquals(1, summary.slidingNeutralTicks());
        assertEquals(100, summary.slidingLubeTicks());
        assertEquals(101, summary.projectedWishBlockedTicks());
        assertEquals(1, summary.lubeToNeutralTransitions());
        assertEquals(6d, summary.maximumHorizontalSpeed());
    }

    @Test
    void resetClearsWindowAndStartsASeparateSummaryWindow() {
        MovementSurfaceTrace trace = new MovementSurfaceTrace();
        trace.sample(10, true, true, .05d, 6d, 1.8d, 2d);
        trace.reset();

        assertNull(trace.sample(11, true, true, 1d, 2d, 1d, 0d));
        MovementSurfaceTrace.Summary summary = null;
        for (int tick = 12; tick <= 111; tick++)
            summary = trace.sample(tick, false, false, 1d, 2d, 1d, 2d);
        assertEquals(101, summary.samples());
        assertEquals(1d, summary.minimumFactor());
        assertEquals(1d, summary.maximumFactor());
        assertEquals(0, summary.slidingLubeTicks());
        assertEquals(0, summary.lubeToNeutralTransitions());
        assertEquals(0, summary.projectedWishBlockedTicks());
    }
}
