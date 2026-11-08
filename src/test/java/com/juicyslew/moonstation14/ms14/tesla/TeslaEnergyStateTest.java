package com.juicyslew.moonstation14.ms14.tesla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeslaEnergyStateTest {
    private static final TeslaBallProfile.Energy RULES = new TeslaBallProfile.Energy(0, 2, 100, -100);

    @Test void strictGrowthThresholdSpawnsExactlyOneAndCarriesRemainder() {
        assertEquals(TeslaEnergyState.Transition.NONE, TeslaEnergyState.adjust(99, 1, RULES).transition());
        var result = TeslaEnergyState.adjust(99, 2, RULES);
        assertEquals(TeslaEnergyState.Transition.SPAWN_MINI, result.transition());
        assertEquals(1, result.energy());
        assertEquals(251, TeslaEnergyState.adjust(350, 1, RULES).energy());
    }

    @Test void strictCollapseThresholdAndNoNaturalDrain() {
        assertEquals(TeslaEnergyState.Transition.NONE, TeslaEnergyState.adjust(-99, -1, RULES).transition());
        assertEquals(TeslaEnergyState.Transition.COLLAPSE, TeslaEnergyState.adjust(-99, -2, RULES).transition());
        var unchanged = TeslaEnergyState.adjust(42, 0, RULES);
        assertEquals(TeslaEnergyState.Transition.NONE, unchanged.transition());
        assertEquals(42, unchanged.energy());
    }
}
