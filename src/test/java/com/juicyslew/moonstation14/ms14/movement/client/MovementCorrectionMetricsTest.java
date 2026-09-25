package com.juicyslew.moonstation14.ms14.movement.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MovementCorrectionMetricsTest {
    @Test
    void recordsWindowCountsCorrectionMagnitudesGroundFlagsAndPendingSamples() {
        MovementCorrectionMetrics metrics = new MovementCorrectionMetrics();
        metrics.reset(10);
        metrics.record(true, false, false, 0.25d, true, true, 3);
        metrics.record(false, true, true, 0.75d, false, true, 2);

        assertNull(metrics.takeWindow(109));
        MovementCorrectionMetrics.Window window = metrics.takeWindow(110);

        assertEquals(2L, window.snapshots());
        assertEquals(1L, window.projectedCorrections());
        assertEquals(1L, window.hardAuthoritySnaps());
        assertEquals(1L, window.duplicateAcksMissingHistoryWhilePending());
        assertEquals(2L, window.appliedCorrectionSamples());
        assertEquals(0.75d, window.maximumCorrection(), 1e-12);
        assertEquals(0.5d, window.meanCorrection(), 1e-12);
        assertEquals(1L, window.serverGroundedSnapshots());
        assertEquals(2L, window.clientGroundedSnapshots());
        assertEquals(2, window.pendingSamples());
        assertEquals(10L, window.startTick());
        assertEquals(110L, window.endTick());
    }

    @Test
    void resetClearsCountsAndStartsANewReportingWindow() {
        MovementCorrectionMetrics metrics = new MovementCorrectionMetrics();
        metrics.reset(0);
        metrics.record(true, false, false, 2d, true, false, 4);
        metrics.reset(500);
        metrics.record(false, false, false, 0d, false, true, 1);

        assertNull(metrics.takeWindow(599));
        MovementCorrectionMetrics.Window window = metrics.takeWindow(600);
        assertEquals(500L, window.startTick());
        assertEquals(1L, window.snapshots());
        assertEquals(0L, window.projectedCorrections());
        assertEquals(0L, window.appliedCorrectionSamples());
        assertEquals(0d, window.maximumCorrection());
        assertEquals(0d, window.meanCorrection());
        assertEquals(1, window.pendingSamples());
        assertNull(metrics.takeWindow(699));
    }
}
