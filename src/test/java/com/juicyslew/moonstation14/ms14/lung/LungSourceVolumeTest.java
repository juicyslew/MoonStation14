package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

class LungSourceVolumeTest {
    @Test void containedHolderDemandUsesIts70LiterVolumeWhileWorldDefaultStaysOneCubicMeter() {
        GasMixture gas = GasMixture.breathableAir();
        double holder = LungSystem.requestedMoles(gas, .07, .5, 100);
        double world = LungSystem.requestedMoles(gas, .5, 100);
        // Gas quantities are float-backed; preserve the 70 L / 1,000 L ratio
        // while allowing the rounding incurred by the source mixture's float storage.
        double expectedHolder = world / .07;
        assertEquals(expectedHolder, holder, Math.ulp((float) expectedHolder) * 2d);
        assertEquals(OptionalDouble.of(world), LungSystem.safeRequestedMoles(gas, .5, 100));
        assertTrue(gas.pressureKpa(.07) > gas.pressureKpa(1));
    }
}
