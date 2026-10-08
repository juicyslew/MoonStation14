package com.juicyslew.moonstation14.ms14.examine;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ExaminationPolicyTest {
    @Test void ordinaryRangeUsesUpstreamHealthAndVisionBoundaries() {
        var examiner = ExaminationPolicy.Examiner.DEFAULT;
        assertEquals(16, ExaminationPolicy.range(examiner, ExaminationPolicy.State.ALIVE, false, 0));
        assertEquals(1.3, ExaminationPolicy.range(examiner, ExaminationPolicy.State.CRITICAL, false, 0));
        assertEquals(.75, ExaminationPolicy.range(examiner, ExaminationPolicy.State.DEAD, false, 0));
        assertEquals(1.3, ExaminationPolicy.range(examiner, ExaminationPolicy.State.ALIVE, true, 0));
        assertEquals(2, ExaminationPolicy.range(examiner, ExaminationPolicy.State.ALIVE, false, 100));
        assertEquals(8.5, ExaminationPolicy.range(examiner, ExaminationPolicy.State.ALIVE, false, 3));
    }

    @Test void visibilityAndCloseDetailsAreSeparateFromStunAndMovementActions() {
        var examiner = ExaminationPolicy.Examiner.DEFAULT;
        assertTrue(ExaminationPolicy.canExamine(examiner, ExaminationPolicy.State.ALIVE, false, 0, 16, true, true, true));
        assertFalse(ExaminationPolicy.canExamine(examiner, ExaminationPolicy.State.ALIVE, false, 0,
                16 + Math.ulp(16.0), true, true, true));
        assertFalse(ExaminationPolicy.canExamine(examiner, ExaminationPolicy.State.ALIVE, false, 0, 16.01, true, true, true));
        assertTrue(ExaminationPolicy.detailsAllowed(examiner, ExaminationPolicy.State.ALIVE, false, 3, true, true));
        assertFalse(ExaminationPolicy.detailsAllowed(examiner, ExaminationPolicy.State.CRITICAL, false, 1, true, true));
        assertFalse(ExaminationPolicy.detailsAllowed(examiner, ExaminationPolicy.State.DEAD, false, 1, true, true));
        assertTrue(ExaminationPolicy.detailsAllowed(ExaminationPolicy.Examiner.OBSERVER, ExaminationPolicy.State.DEAD, true, 100, false, false));
    }

    @Test void attemptCancellationAndOcclusionDenyOrdinaryExamination() {
        var examiner = ExaminationPolicy.Examiner.DEFAULT;
        assertFalse(ExaminationPolicy.canExamine(examiner, ExaminationPolicy.State.ALIVE, false, 0, 1, true, false, true));
        assertFalse(ExaminationPolicy.canExamine(examiner, ExaminationPolicy.State.ALIVE, false, 0, 1, true, true, false));
        assertFalse(ExaminationPolicy.canExamine(examiner, ExaminationPolicy.State.ALIVE, false, 0, 1, false, true, true));
        assertTrue(ExaminationPolicy.canExamine(new ExaminationPolicy.Examiner(false, false), ExaminationPolicy.State.ALIVE, false, 0, 1000, true, false, true));
    }
}
