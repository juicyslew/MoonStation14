package com.juicyslew.moonstation14.eventhooks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickHooksTest {
    @Test
    void detachedStateFinalizationRunsAndCallbackFailureStillPropagates() {
        IllegalStateException failure = new IllegalStateException("effect callback failure");
        boolean[] finalized = {false};

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> TickHooks.runWithFinalization(() -> { throw failure; }, () -> finalized[0] = true));

        assertSame(failure, thrown);
        assertTrue(finalized[0]);
    }

    @Test
    void metabolismUsesStableEntityBuckets() {
        assertTrue(TickHooks.shouldMetabolize(100L, 0));
        assertFalse(TickHooks.shouldMetabolize(101L, 0));
        assertTrue(TickHooks.shouldMetabolize(101L, -1));
        assertEquals(TickHooks.metabolismBucket(100L, 7), TickHooks.metabolismBucket(120L, -13));
    }

    @Test
    void metabolismBucketIsFloorModSafeForNegativeInputs() {
        assertEquals(19, TickHooks.metabolismBucket(-1L, 0));
        assertEquals(0, TickHooks.metabolismBucket(-21L, 1));
        assertTrue(TickHooks.shouldMetabolize(-21L, 1));
    }
}
