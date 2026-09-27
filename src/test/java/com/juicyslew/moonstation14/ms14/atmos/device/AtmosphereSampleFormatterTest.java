package com.juicyslew.moonstation14.ms14.atmos.device;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereSampleFormatterTest {
    @Test
    void formatsDefaultAirWithPressureTemperatureAndComposition() {
        String formatted = AtmosphereSampleFormatter.format(GasMixture.breathableAir());

        assertTrue(formatted.startsWith("P: 101.325 kPa | T: 293.15 K | n: 41.57 mol"));
        assertTrue(formatted.contains("oxygen: 8.72995 mol"));
        assertTrue(formatted.contains("nitrogen: 32.8412 mol"));
    }

    @Test
    void formatsEveryGasInStableEnumOrder() {
        EnumMap<GasType, Double> allGases = new EnumMap<>(GasType.class);
        for (GasType type : GasType.values()) allGases.put(type, 1.0);
        String formatted = AtmosphereSampleFormatter.format(new GasMixture(allGases, 300.0));

        int previousIndex = -1;
        for (GasType type : GasType.values()) {
            int index = formatted.indexOf(type.id() + ": 1.00000 mol");
            assertTrue(index > previousIndex, "Expected " + type.id() + " in enum order");
            previousIndex = index;
        }
    }

    @Test
    void formatsPresentGasWithoutRequiringOxygenOrNitrogen() {
        String formatted = AtmosphereSampleFormatter.format(new GasMixture(
                Map.of(GasType.TRITIUM, 0.75), 300.0));

        assertTrue(formatted.contains("tritium: 0.750000 mol"));
        assertFalse(formatted.contains("oxygen:"));
        assertFalse(formatted.contains("nitrogen:"));
    }

    @Test
    void formatsOxygenWhenItIsTheOnlyPresentGas() {
        String formatted = AtmosphereSampleFormatter.format(new GasMixture(
                Map.of(GasType.OXYGEN, 1.0), 300.0));

        assertTrue(formatted.contains("oxygen: 1.00000 mol"));
        assertFalse(formatted.contains("nitrogen:"));
    }

    @Test
    void marksVacuumCompositionAsNone() {
        assertEquals("P: 0.000 kPa | T: 2.70 K | n: 0.00 mol | gases: none",
                AtmosphereSampleFormatter.format(GasMixture.vacuum()));
    }

    @Test
    void displaysTinyPositiveGasAsNonzero() {
        String formatted = AtmosphereSampleFormatter.format(new GasMixture(
                Map.of(GasType.TRITIUM, 1.0e-12), 300.0));

        assertTrue(formatted.contains("tritium: 1.00000e-12 mol"));
        assertFalse(formatted.contains("tritium: 0.00 mol"));
    }

    @Test
    void formattingIsIndependentOfClientLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            String germanLocale = AtmosphereSampleFormatter.format(GasMixture.breathableAir());
            Locale.setDefault(Locale.US);
            assertEquals(germanLocale, AtmosphereSampleFormatter.format(GasMixture.breathableAir()));
            assertTrue(germanLocale.contains("oxygen: 8.72995 mol"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void provisionalReadingsAreExplicitlyPendingWhileFiniteAndExteriorStayNormal() {
        GasMixture air = GasMixture.breathableAir();
        String provisional = AtmosphereSampleFormatter.format(
                new AtmosphereReading(air, AtmosphereReading.Status.PROVISIONAL));
        assertTrue(provisional.startsWith("Provisional / classification pending | P: 101.325 kPa"));
        assertTrue(provisional.contains("oxygen: 8.72995 mol"));
        assertTrue(provisional.contains("nitrogen: 32.8412 mol"));

        assertEquals(AtmosphereSampleFormatter.format(air), AtmosphereSampleFormatter.format(
                new AtmosphereReading(air, AtmosphereReading.Status.FINITE)));
        assertEquals(AtmosphereSampleFormatter.format(air), AtmosphereSampleFormatter.format(
                new AtmosphereReading(air, AtmosphereReading.Status.EXTERIOR)));
    }
}
