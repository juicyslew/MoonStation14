package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereSampleFormatterTest {
    @Test
    void formatsDefaultAirWithPressureTemperatureAndComposition() {
        String formatted = AtmosphereSampleFormatter.format(GasMixture.breathableAir());

        assertTrue(formatted.startsWith("P: 101.325 kPa | T: 293.15 K | n: 41.57 mol"));
        assertTrue(formatted.contains("O2: 8.73 mol"));
        assertTrue(formatted.contains("N2: 32.84 mol"));
    }

    @Test
    void formatsVacuumAndIncludesOtherNonzeroGases() {
        assertEquals("P: 0.000 kPa | T: 2.70 K | n: 0.00 mol | O2: 0.00 mol | N2: 0.00 mol",
                AtmosphereSampleFormatter.format(GasMixture.vacuum()));

        String formatted = AtmosphereSampleFormatter.format(new GasMixture(
                Map.of(GasType.OXYGEN, 1.0, GasType.CARBON_DIOXIDE, 0.75), 300.0));
        assertTrue(formatted.contains("carbon_dioxide: 0.75 mol"));
    }
}
