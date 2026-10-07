package com.juicyslew.moonstation14.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LooseClientPositionInterpolationTest {
    @Test void latestPacketWinsAndGetsAdoptedAsSingleRenderStep() {
        var state = new LooseClientPositionInterpolation();
        assertFalse(state.stage(0, 0, 0, 0.5, 0.75, 1));
        assertFalse(state.stage(0, 0, 0, 1, 1.5, 2));
        assertEquals(1, state.targetX(0));
        assertEquals(1.5, state.targetY(0));
        double[] next = state.advance(0, 0, 0);
        assertArrayEquals(new double[]{1, 1.5, 2}, next);
        assertFalse(state.pending());
        assertArrayEquals(next, state.advance(next[0], next[1], next[2]));
    }

    @Test void initialPositionAndLargeCorrectionSnapWithoutOldPositionStreak() {
        var initial = new LooseClientPositionInterpolation();
        assertTrue(initial.stage(0, 0, 0, 100, 64, -100));
        assertFalse(initial.pending());
        assertEquals(100, initial.targetX(100));
        assertArrayEquals(new double[]{100, 64, -100}, initial.advance(100, 64, -100));
    }

    @Test void successiveTickAdoptionsConvergeToServerAuthority() {
        var state = new LooseClientPositionInterpolation();
        double x = 0, y = 0, z = 0;
        for (int i = 1; i <= 20; i++) {
            assertFalse(state.stage(x, y, z, i, i * 2, -i));
            double[] next = state.advance(x, y, z);
            x = next[0]; y = next[1]; z = next[2];
        }
        assertEquals(20, x);
        assertEquals(40, y);
        assertEquals(-20, z);
    }
}
