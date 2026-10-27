package com.juicyslew.moonstation14.gametest.support;

import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetabolismObservationWindowTest {
    @Test
    void trashWindowAdmitsAQuietStartWithinOneCadenceForEveryPhaseAndEntity() {
        int[] entityIds = {-39, -1, 0, 1, 19, 37};
        for (int entityId : entityIds) {
            for (int phase = 0; phase < 20; phase++) {
                long phaseStart = phase;
                int admitted = 0;
                for (int elapsed = 0; elapsed < 20; elapsed++) {
                    long candidate = phaseStart + elapsed;
                    if (MetabolismObservationWindow.isQuiet(candidate, entityId, 1, 8)) {
                        admitted++;
                        for (int offset = 1; offset <= 8; offset++)
                            assertFalse(EntityActivity.REAGENT_METABOLISM.isDue(candidate + offset, entityId));
                    }
                }
                assertTrue(admitted > 0, "phase=" + phase + " entity=" + entityId);
            }
        }
    }

    @Test
    void handlessWindowMatchesItsPriorTwoDueChecks() {
        for (int entityId : new int[]{-39, -1, 0, 1, 19, 37}) {
            for (long start = 0; start < 40; start++) {
                boolean prior = !EntityActivity.REAGENT_METABOLISM.isDue(start + 20, entityId)
                        && !EntityActivity.REAGENT_METABOLISM.isDue(start + 21, entityId);
                assertEquals(prior, MetabolismObservationWindow.isQuiet(start, entityId, 20, 21));
            }
        }
    }

    @Test
    void rejectsInvalidOrUnsatisfiableWindows() {
        assertThrows(IllegalArgumentException.class, () -> MetabolismObservationWindow.isQuiet(0, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> MetabolismObservationWindow.isQuiet(0, 1, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> MetabolismObservationWindow.isQuiet(0, 1, 0, 20));
    }
}
