package com.juicyslew.moonstation14.ms14.botany.client;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class GrinderJitterMotionTest {
    private static final class ScriptedRandom extends Random {
        private final boolean[] booleans; private int booleanIndex;
        ScriptedRandom(boolean... booleans) { this.booleans = booleans; }
        @Override public double nextDouble() { return 0; }
        @Override public boolean nextBoolean() { return booleans[booleanIndex++]; }
    }

    @Test void boundsInterpolationAndElapsedCadenceArePreserved() {
        var one = new GrinderJitterMotion(); var split = new GrinderJitterMotion();
        var a = one.sample(.035, new Random(41));
        Random same = new Random(41);
        var b = split.sample(.005, same);
        for (int i = 1; i < 7; i++) b = split.sample(.005, same);
        assertEquals(a.x(), b.x(), 1e-12); assertEquals(a.z(), b.z(), 1e-12);
        assertTrue(Math.abs(one.targetX()) >= .0225 && Math.abs(one.targetX()) <= .09);
        assertTrue(Math.abs(one.targetZ()) >= .0225 && Math.abs(one.targetZ()) <= .03);
        assertTrue(Math.abs(a.x()) <= .09); assertTrue(Math.abs(a.z()) <= .03);
    }

    @Test void sampledSourceAxisRangesAndZeroBaselineArePreserved() {
        var motion = new GrinderJitterMotion();
        assertEquals(new GrinderJitterMotion.Offset(0, 0), motion.sample(0, new Random(7)));
        motion.sample(.01, new Random(7));
        assertTrue(Math.abs(motion.targetX()) >= .0225 && Math.abs(motion.targetX()) <= .09);
        assertTrue(Math.abs(motion.targetZ()) >= .0225 && Math.abs(motion.targetZ()) <= .03);
    }

    @Test void signMatchUsesSourceOrConditionAndRandomlyFlipsOneAxis() {
        var motion = new GrinderJitterMotion();
        var random = new ScriptedRandom(false, false, false, false, true);
        motion.sample(0, random);
        motion.sample(.01, random);
        assertEquals(-1, motion.signX());
        assertEquals(1, motion.signZ());
    }
}
