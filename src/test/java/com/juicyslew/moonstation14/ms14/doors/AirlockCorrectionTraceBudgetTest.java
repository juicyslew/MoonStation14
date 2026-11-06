package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirlockCorrectionTraceBudgetTest {
    @Test
    void initializationEventsCannotStartWindowAndDisabledCannotArm() {
        var budget=new AirlockBlockEntity.CorrectionTraceBudget();
        assertFalse(budget.permit(false,10));
        assertFalse(budget.permit(true,900));
        assertFalse(budget.arm(false,900));
        assertTrue(budget.arm(true,1000));
        assertTrue(budget.permit(true,1000));
    }

    @Test
    void captureStopsAtPerControllerRecordCapAndWindow() {
        var capped=new AirlockBlockEntity.CorrectionTraceBudget();
        assertTrue(capped.arm(true,0));
        for(int i=0;i<AirlockBlockEntity.CORRECTION_TRACE_MAX_RECORDS;i++)
            assertTrue(capped.permit(true,0));
        assertFalse(capped.permit(true,0));
        assertFalse(capped.arm(true,50));
        assertFalse(capped.permit(true,50));

        var expired=new AirlockBlockEntity.CorrectionTraceBudget();
        assertFalse(expired.permit(true,900));
        assertTrue(expired.arm(true,1000));
        assertTrue(expired.permit(true,1199));
        assertFalse(expired.permit(true,1200));
        assertFalse(expired.arm(true,1200));
        assertFalse(expired.permit(true,1200));
    }
}
