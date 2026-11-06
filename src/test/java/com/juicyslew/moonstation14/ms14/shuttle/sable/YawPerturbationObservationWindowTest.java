package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleFixtureMotionService;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionRequest;
import com.juicyslew.moonstation14.ms14.shuttle.fixture.ShuttleMotionMath;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class YawPerturbationObservationWindowTest {
    private static final ShuttleMotionRequest REQUEST = new ShuttleMotionRequest(0, 0, 0, 4, 2400);

    @Test void ordinalsAreStrictAndWorldTicksRemainSeparateWithMultipleSubstepsPerTick() {
        UUID frame = UUID.randomUUID();
        var window = new YawPerturbationObservationWindow(frame);
        assertEquals(YawPerturbationObservationWindow.Result.ACCEPTED, window.accept(observation(frame, 20, 1, 0)));
        assertEquals(YawPerturbationObservationWindow.Result.DUPLICATE, window.accept(observation(frame, 20, 1, 0)));
        assertEquals(YawPerturbationObservationWindow.Result.ACCEPTED, window.accept(observation(frame, 20, 2, 0)));
        assertTrue(window.markSeed(2));
        assertEquals(YawPerturbationObservationWindow.Result.ACCEPTED, window.accept(observation(frame, 21, 3, 0)));
        assertEquals(1, window.firstAfterSeedRows());

        var wrong = new YawPerturbationObservationWindow(frame);
        assertEquals(YawPerturbationObservationWindow.Result.FAILED,
                wrong.accept(observation(UUID.randomUUID(), 20, 1, 0)));
        var backward = new YawPerturbationObservationWindow(frame);
        backward.accept(observation(frame, 20, 2, 0));
        assertEquals(YawPerturbationObservationWindow.Result.FAILED, backward.accept(observation(frame, 20, 1, 0)));
        var gap = new YawPerturbationObservationWindow(frame);
        gap.accept(observation(frame, 20, 1, 0));
        assertEquals(YawPerturbationObservationWindow.Result.FAILED, gap.accept(observation(frame, 20, 3, 0)));
        var worldGap = new YawPerturbationObservationWindow(frame);
        worldGap.accept(observation(frame, 20, 1, 0));
        assertEquals(YawPerturbationObservationWindow.Result.FAILED,
                worldGap.accept(observation(frame, 22, 2, 0)), "source world time is tracked separately from ordinal");
    }

    @Test void seedAndLateTiltWindowsAreBoundedOrdinalBasedAndDumpOnlyOnce() {
        UUID frame = UUID.randomUUID();
        var window = new YawPerturbationObservationWindow(frame);
        for (int ordinal = 1; ordinal <= 5; ordinal++)
            assertEquals(YawPerturbationObservationWindow.Result.ACCEPTED,
                    window.accept(observation(frame, 100 + ordinal / 6, ordinal, 0)));
        assertTrue(window.markSeed(5));
        for (int ordinal = 6; ordinal < 130; ordinal++)
            window.accept(observation(frame, 100 + ordinal / 6, ordinal, ordinal < 126 ? 0 : Math.toRadians(2)));
        assertEquals(120, window.firstAfterSeedRows());
        assertEquals(126, window.lateStartOrdinal());
        assertTrue(window.rollingRows() <= YawPerturbationObservationWindow.ROLLING_CAP);
        assertTrue(window.lateRows() > 0);
        assertTrue(window.complete());
        window.markExpectedOrdinal(129);
        var dump = window.dumpOnce("unsafe-capsize");
        assertNotNull(dump);
        assertTrue(dump.header().contains("seedOrdinal=5"));
        assertTrue(dump.header().contains("lateStartOrdinal=126"));
        assertTrue(dump.header().contains("expectedOrdinalAtDump=129"));
        assertTrue(dump.header().contains("ordinalCoverageAtDump=true"));
        assertTrue(dump.rows().stream().anyMatch(row -> row.sourceWorldGameTime() >= 100));
        assertTrue(dump.rows().get(0).toString().length() < 8192);
        assertNull(window.dumpOnce("cleanup"), "payload emitted at most once across terminal/cleanup paths");
    }

    @Test void expectedOrdinalMismatchIsExplicitIncompleteTerminalCoverage() {
        UUID frame = UUID.randomUUID();
        var window = new YawPerturbationObservationWindow(frame);
        window.accept(observation(frame, 1, 1, 0));
        window.markExpectedOrdinal(2);
        assertFalse(window.complete());
        assertTrue(window.dumpOnce("terminal").header().contains("ordinalCoverageAtDump=false"));
    }

    @Test void worstNumericObservationStillFitsAsOneBoundedLoggerRecord() {
        double m = Double.MAX_VALUE;
        var row = new ShuttleFixtureMotionService.AngularControlObservation(UUID.randomUUID(), Long.MAX_VALUE,
                Long.MAX_VALUE, Double.MIN_NORMAL,
                new ShuttleFixtureMotionService.QuaternionValue(m, -m, m, -m),
                new ShuttleMotionMath.Vector(m, -m, m), new ShuttleMotionMath.Vector(m, -m, m),
                new ShuttleMotionMath.Vector(m, -m, m), new ShuttleMotionMath.Vector(m, -m, m),
                new ShuttleFixtureMotionService.QuaternionValue(m,-m,m,-m),
                new ShuttleMotionMath.Vector(m, -m, m), new ShuttleMotionMath.Vector(m, -m, m),
                new ShuttleMotionMath.Vector(m, -m, m), new ShuttleMotionMath.Vector(m, -m, m),
                new ShuttleMotionMath.Vector(m, -m, m),
                new ShuttleFixtureMotionService.InertiaValue(m,-m,m,-m,m,-m,m,-m,m), m, m,
                new ShuttleMotionRequest(m, -m, m, m, ShuttleMotionRequest.MAX_DURATION_TICKS));
        assertTrue(row.toString().length() < 8192);
    }

    @Test void totalRetainedCaptureCapMarksOverflowInsteadOfSilentlyClaimingCompleteness() {
        UUID frame = UUID.randomUUID();
        var window = new YawPerturbationObservationWindow(frame);
        window.accept(observation(frame, 0, 1, 0));
        assertTrue(window.markSeed(1));
        for (int ordinal = 2; ordinal <= YawPerturbationObservationWindow.TOTAL_UNIQUE_CAP + 100; ordinal++)
            window.accept(observation(frame, ordinal / 6, ordinal, Math.toRadians(2)));
        assertTrue(window.overflow());
        assertFalse(window.complete());
        assertEquals(YawPerturbationObservationWindow.TOTAL_UNIQUE_CAP, window.retainedRows());
    }

    private static ShuttleFixtureMotionService.AngularControlObservation observation(UUID frame, long worldTime,
            long ordinal, double rollRadians) {
        var q = new Quaterniond().rotationZ(rollRadians);
        return new ShuttleFixtureMotionService.AngularControlObservation(frame, worldTime, ordinal, 1d / 120d,
                new ShuttleFixtureMotionService.QuaternionValue(q.x, q.y, q.z, q.w),
                new ShuttleMotionMath.Vector(1, 2, 3), new ShuttleMotionMath.Vector(4, 5, 6),
                new ShuttleMotionMath.Vector(0, 0, .1), ordinal == 1 ? null : new ShuttleMotionMath.Vector(0, 0, .09),
                ordinal == 1 ? null : new ShuttleFixtureMotionService.QuaternionValue(q.x, q.y, q.z, q.w),
                new ShuttleMotionMath.Vector(0, 0, .1), new ShuttleMotionMath.Vector(0, 0, .1),
                new ShuttleMotionMath.Vector(0, -9.8, 0),
                new ShuttleMotionMath.Vector(7, 8, 9), new ShuttleMotionMath.Vector(0, 0, .2),
                new ShuttleFixtureMotionService.InertiaValue(1,0,0,0,2,0,0,0,3), 12, .4, REQUEST);
    }
}
