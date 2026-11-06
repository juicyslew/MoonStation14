package com.juicyslew.moonstation14.ms14.hands.client;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderFrameSnapshotCacheTest {
    @Test void sameFrameSharesOneCaptureAndFrameChangeRefreshes() {
        var cache = new RenderFrameSnapshotCache<Integer>();
        var calls = new AtomicInteger();
        Object body = new Object();
        cache.beginFrame(1);
        assertEquals(1, cache.get(1, body, 4, calls::incrementAndGet));
        assertEquals(1, cache.get(1, body, 4, calls::incrementAndGet));
        cache.beginFrame(2);
        assertEquals(2, cache.get(2, body, 4, calls::incrementAndGet));
        assertEquals(2, calls.get());
    }

    @Test void bodyAndEpochChangesRefreshWithinFrame() {
        var cache = new RenderFrameSnapshotCache<Integer>();
        var calls = new AtomicInteger();
        Object body = new Object();
        cache.beginFrame(1);
        cache.get(1, body, 4, calls::incrementAndGet);
        cache.get(1, new Object(), 4, calls::incrementAndGet);
        cache.get(1, body, 5, calls::incrementAndGet);
        assertEquals(3, calls.get());
    }

    @Test void clearInvalidatesLifecycleAndFreshActionCaptureDoesNotTouchMemo() {
        var cache = new RenderFrameSnapshotCache<Integer>();
        var calls = new AtomicInteger();
        Object body = new Object();
        cache.beginFrame(1);
        cache.get(1, body, 4, calls::incrementAndGet);
        int freshActionResult = calls.incrementAndGet();
        assertEquals(2, freshActionResult);
        cache.clear();
        assertEquals(3, cache.get(1, body, 4, calls::incrementAndGet));
    }

    @Test void clearAndLevelSwitchInvalidateEvenWithSameFrameBodyAndEpoch() {
        var cache = new RenderFrameSnapshotCache<Integer>();
        var calls = new AtomicInteger();
        Object body = new Object(), firstLevel = new Object(), secondLevel = new Object();
        cache.beginFrame(8);
        assertEquals(1, cache.get(8, body, firstLevel, 12, calls::incrementAndGet));
        assertEquals(2, cache.get(8, body, secondLevel, 12, calls::incrementAndGet));
        cache.clear();
        assertEquals(3, cache.get(8, body, secondLevel, 12, calls::incrementAndGet));
        assertEquals(3, calls.get());
    }
}
