package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostPredictionClockTest {
    @Test void anchorsOnlyAdvanceAndElapsedUsesTwentyHertzWallTime() {
        var c = new GhostPredictionClock(); assertEquals(-1, c.targetTick(0, 0));
        assertFalse(c.anchor(-1, 0)); assertTrue(c.anchor(10, 100));
        for (int i=0;i<10;i++) assertEquals(10, c.targetTick(100, 0));
        assertEquals(11, c.targetTick(60_000_100L, 0));
        assertFalse(c.anchor(10, 1)); assertTrue(c.anchor(11, 2));
        assertEquals(11, c.targetTick(2, -4));
    }
    @Test void leadAndHorizonAreBoundedAndAdditionSaturates() {
        var c = new GhostPredictionClock(); c.anchor(100, 0);
        assertEquals(100 + GhostControlPayloads.MAX_PENDING_INTENTS, c.targetTick(0, Integer.MAX_VALUE));
        assertEquals(100 + GhostControlPayloads.MAX_PENDING_INTENTS, c.targetTick(100_000_000_000L, 0));
        c.reset(); assertEquals(-1, c.targetTick(0, 3));
        c.anchor(Long.MAX_VALUE - 1, 0); assertEquals(Long.MAX_VALUE, c.targetTick(0, 64));
    }
    @Test void subtractionOverflowIsTreatedAsNegativeElapsed() {
        var c = new GhostPredictionClock(); c.anchor(4, Long.MIN_VALUE);
        assertEquals(4, c.targetTick(Long.MAX_VALUE, 0));
    }
}
