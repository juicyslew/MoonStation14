package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackedCollisionSubstepsTest {
    @Test void preservesStockAndCapsRelativeSampling() {
        assertEquals(1, TrackedCollisionSubsteps.required(1, 0));
        assertEquals(1, TrackedCollisionSubsteps.required(1, Double.NaN));
        assertEquals(2, TrackedCollisionSubsteps.required(1, .02));
        assertEquals(10, TrackedCollisionSubsteps.required(1, 1));
        assertEquals(4, TrackedCollisionSubsteps.required(4, .001));
        assertEquals(10, TrackedCollisionSubsteps.required(10, 0));
    }

    @Test void relativePointSweepAccountsForTranslationRotationAndCancellation() {
        assertEquals(0, TrackedCollisionSubsteps.relativeSweep(0, 0, 0, 0, 0, 0));
        assertTrue(TrackedCollisionSubsteps.relativeSweep(0, 0, 0, 0.1, 0, 0) > 0.1 - 1e-12);
        // A rotation's point displacement arrives from the shared SDK pose transform, just like translation.
        assertEquals(.2, TrackedCollisionSubsteps.relativeSweep(0, 0, 0, .2, 0, 0), 1e-12);
        assertEquals(0, TrackedCollisionSubsteps.relativeSweep(.2, 0, 0, .2, 0, 0), 1e-12);
        assertEquals(3, TrackedCollisionSubsteps.required(3,
                TrackedCollisionSubsteps.relativeSweep(.2, 0, 0, .2, 0, 0)));
        assertTrue(Double.isNaN(TrackedCollisionSubsteps.relativeSweep(0, 0, 0, Double.NaN, 0, 0)));
    }
}
