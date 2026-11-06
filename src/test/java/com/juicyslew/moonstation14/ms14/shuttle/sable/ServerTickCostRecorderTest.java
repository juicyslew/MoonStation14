package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ServerTickCostRecorderTest {
    private static final class Clock implements java.util.function.LongSupplier {
        long now;
        @Override public long getAsLong() { return now; }
    }

    @Test void armingBeforeListenerFirstPreRecordsEveryPairExactlyOnce() {
        Clock clock = new Clock();
        var recorder = new ServerTickCostRecorder(2, clock);
        recorder.arm(); // adapter arms before listener registration
        for (int tick = 1; tick <= 2; tick++) {
            recorder.pre(tick);
            clock.now++;
            recorder.post(tick);
        }
        var summary = recorder.finish(2);
        assertEquals(2, summary.count());
        assertEquals(java.util.List.of(1L, 1L), summary.samples());
        assertThrows(IllegalStateException.class, recorder::arm);
    }

    @Test void armsOnNextCompletePairedTickAndComputesNearestRankDistribution() {
        Clock clock = new Clock();
        var recorder = new ServerTickCostRecorder(4, clock);
        recorder.pre(1); // unarmed tick ignored
        recorder.arm();
        for (int i = 0; i < 4; i++) {
            clock.now += 10;
            recorder.pre(i + 2);
            clock.now += i + 1;
            recorder.post(i + 2);
        }
        var summary = recorder.finish(4);
        assertEquals(4, summary.count());
        assertEquals(1, summary.min());
        assertEquals(4, summary.max());
        assertEquals(2.5, summary.mean());
        assertEquals(2, summary.p50());
        assertEquals(4, summary.p95());
        assertEquals(4, summary.p99());
        assertThrows(UnsupportedOperationException.class, () -> summary.samples().add(8L));
    }

    @Test void rejectsDuplicateMisorderedNonpositiveAndOverLimitSamples() {
        Clock clock = new Clock();
        var duplicate = new ServerTickCostRecorder(2, clock);
        duplicate.arm(); duplicate.pre(1);
        assertThrows(IllegalStateException.class, () -> duplicate.pre(1));
        assertThrows(IllegalStateException.class, () -> duplicate.post(2));

        var badTime = new ServerTickCostRecorder(1, clock);
        badTime.arm(); badTime.pre(1);
        assertThrows(IllegalStateException.class, () -> badTime.post(1));

        var full = new ServerTickCostRecorder(1, clock);
        full.arm(); full.pre(1); clock.now++; full.post(1);
        assertThrows(IllegalStateException.class, () -> full.pre(2));
        assertThrows(IllegalStateException.class, () -> full.finish(2));
    }

    @Test void launchCadenceIsSixInputsAtHundredTickIntervals() {
        assertEquals(6, ServerTickCostRecorder.launchCount());
        var offsets = new ArrayList<Integer>();
        for (int i = 0; i < 6; i++) offsets.add(ServerTickCostRecorder.launchOffset(i));
        assertEquals(java.util.List.of(0, 100, 200, 300, 400, 500), offsets);
        assertTrue(ServerTickCostRecorder.isLaunchTick(0));
        assertTrue(ServerTickCostRecorder.isLaunchTick(500));
        assertFalse(ServerTickCostRecorder.isLaunchTick(1));
        assertFalse(ServerTickCostRecorder.isLaunchTick(600));
        assertThrows(IllegalArgumentException.class, () -> ServerTickCostRecorder.launchOffset(6));
    }
}
