package com.juicyslew.moonstation14.ms14.singularity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingularityFrameTimingTest {
    private static final double[] DELAYS = {0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1};

    @Test
    void selectsBoundaryFramesAndWrapsByFullAuthoredCycle() {
        assertEquals(0, SingularityFrameTiming.frameAt(0, DELAYS));
        assertEquals(1, SingularityFrameTiming.frameAt(0.1, DELAYS));
        assertEquals(7, SingularityFrameTiming.frameAt(0.7, DELAYS));
        assertEquals(0, SingularityFrameTiming.frameAt(0.8, DELAYS));
        assertEquals(4, SingularityFrameTiming.frameAt(1.25, DELAYS));
    }

    @Test
    void respectsUnequalDelaysAndRejectsInvalidSequences() {
        assertEquals(0, SingularityFrameTiming.frameAt(0.19, new double[]{0.2, 0.3}));
        assertEquals(1, SingularityFrameTiming.frameAt(0.2, new double[]{0.2, 0.3}));
        assertThrows(IllegalArgumentException.class, () -> SingularityFrameTiming.frameAt(-1, DELAYS));
        assertThrows(IllegalArgumentException.class, () -> SingularityFrameTiming.frameAt(0, new double[]{0}));
    }
}
