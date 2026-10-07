package com.juicyslew.moonstation14.ms14.disposal.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisposalUnitFlushGasTest {
    private static final double TARGET_KPA = .25 * 101.325 * 1.01;

    @Test
    void intakeUsesSourceTemperatureAndResolvedUnitVolume() {
        double cold = DisposalUnitFlushGas.requestedIntakeMoles(0, 2500, 250);
        double hot = DisposalUnitFlushGas.requestedIntakeMoles(0, 2500, 500);
        assertEquals(.1 * TARGET_KPA * 2500 / (250 * 8.31446261815324), cold, 1e-12);
        assertEquals(cold / 2, hot, 1e-12);
    }

    @Test
    void doesNotRequestGasWhenHolderAlreadyMeetsTarget() {
        assertEquals(0, DisposalUnitFlushGas.requestedIntakeMoles(TARGET_KPA, 2500, 293), 0);
        assertEquals(0, DisposalUnitFlushGas.requestedIntakeMoles(TARGET_KPA + 10, 2500, 293), 0);
    }
}
