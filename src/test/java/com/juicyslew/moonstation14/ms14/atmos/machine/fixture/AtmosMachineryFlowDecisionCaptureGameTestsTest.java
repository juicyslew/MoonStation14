package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import org.junit.jupiter.api.Test;
import net.minecraft.gametest.framework.GameTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtmosMachineryFlowDecisionCaptureGameTestsTest {
    @Test void continuousCaptureRunsInItsOwnNativeBatch() throws Exception {
        assertEquals("atmos_lab_continuous_flow", AtmosMachineryFlowDecisionCaptureGameTests.class
                .getMethod("verifyContinuousFundedFlowAndQuietStop", net.minecraft.gametest.framework.GameTestHelper.class)
                .getAnnotation(GameTest.class).batch());
    }

    @Test void fundedStartupPrimingUsesBillingAndResolvedDeviceIntervals() {
        assertEquals(60, AtmosMachineryFlowDecisionCaptureGameTests.commonPrimingTicks(20, List.of(12L, 12L, 12L)));
        assertEquals(60, AtmosMachineryFlowDecisionCaptureGameTests.commonPrimingTicks(20, List.of(12L)));
        assertThrows(IllegalArgumentException.class,
                () -> AtmosMachineryFlowDecisionCaptureGameTests.commonPrimingTicks(0, List.of(12L)));
    }
}
