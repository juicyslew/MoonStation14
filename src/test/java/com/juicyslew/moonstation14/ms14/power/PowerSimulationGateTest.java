package com.juicyslew.moonstation14.ms14.power;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerSimulationGateTest {
    @AfterEach
    void restoreDefault() {
        PowerSimulationGate.onServerStopped();
    }

    @Test
    void defaultsEnabledToPreserveExistingWorldBehavior() {
        assertTrue(PowerSimulationGate.isEnabled());
    }

    @Test
    void samplesConfiguredValueUntilServerStops() {
        PowerSimulationGate.configureAtServerStart(false);
        assertFalse(PowerSimulationGate.isEnabled());

        PowerSimulationGate.onServerStopped();
        assertTrue(PowerSimulationGate.isEnabled());
    }
}
