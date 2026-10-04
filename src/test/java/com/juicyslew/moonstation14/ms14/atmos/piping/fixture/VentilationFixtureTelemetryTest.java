package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VentilationFixtureTelemetryTest {
    @Test
    void roomPressureUsesWholeEighteenCellVolume() {
        GasMixture oneCell = new GasMixture(Map.of(GasType.OXYGEN, 39.0), 300.0);
        GasMixture aggregate = oneCell;
        for (int i = 1; i < 18; i++) aggregate = merge(aggregate, oneCell);

        assertEquals(97.28, VentilationFixtureTelemetry.averagePressureKpa(aggregate, 18), 0.01);
    }

    @Test
    void knownZeroDeliveryIsNotLabeledPowered() {
        String text = VentilationFixtureTelemetry.powerText(new PowerRuntime.VentMeterObservation(true, 0.0));

        assertEquals("meter known delivered=0.00 W", text);
        assertFalse(text.contains("powered"));
    }

    private static GasMixture merge(GasMixture a, GasMixture b) {
        return new GasMixture(Map.of(GasType.OXYGEN, a.totalMoles() + b.totalMoles()), 300.0);
    }
}
