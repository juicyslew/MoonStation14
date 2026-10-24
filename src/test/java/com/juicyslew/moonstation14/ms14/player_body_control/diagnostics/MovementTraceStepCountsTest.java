package com.juicyslew.moonstation14.ms14.player_body_control.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MovementTraceStepCountsTest {
    @Test void dispatchedTicksDistinguishCompletedFailedAndSkippedMotorCalls() {
        var counts=new MovementTraceStepCounts();
        counts.observe(MovementTraceStepCounts.Outcome.COMPLETED);
        counts.observe(MovementTraceStepCounts.Outcome.FAILED_AFTER_MOVE);
        counts.observe(MovementTraceStepCounts.Outcome.SKIPPED);
        counts.observe(MovementTraceStepCounts.Outcome.SKIPPED);
        assertEquals(4,counts.dispatched());
        assertEquals(2,counts.attempted());
        assertEquals(1,counts.completed());
        assertEquals(1,counts.failed());
        assertEquals(2,counts.skipped());
    }
}
