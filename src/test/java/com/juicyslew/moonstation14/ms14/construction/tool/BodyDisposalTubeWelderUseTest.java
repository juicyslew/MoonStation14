package com.juicyslew.moonstation14.ms14.construction.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyDisposalTubeWelderUseTest {
    @Test void resolvedWeldingSpeedSetsOneSecondBaseDuration() {
        assertEquals(20, BodyDisposalTubeWelderUse.delayTicks(1));
        assertEquals(10, BodyDisposalTubeWelderUse.delayTicks(2));
        assertEquals(40, BodyDisposalTubeWelderUse.delayTicks(.5));
        assertThrows(IllegalArgumentException.class, () -> BodyDisposalTubeWelderUse.delayTicks(0));
    }
}
