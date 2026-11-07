package com.juicyslew.moonstation14.ms14.atmos.teg;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TegCirculationPolicyTest {
    @Test void matchesUpstreamExtractionAndReversedPressureIsNoop() {
        var inlet = gas(10, 300);
        var outlet = gas(2, 300);
        var result = TegCirculationPolicy.plan(inlet, outlet, 1, 1);
        assertEquals(4, result.transferredMoles(), 2e-5);
        assertEquals(6, result.inletAfter().totalMoles(), 2e-5);
        assertEquals(4, result.packet().totalMoles(), 2e-5);
        var reversed = TegCirculationPolicy.plan(gas(2, 300), gas(10, 300), 1, 1);
        assertEquals(0, reversed.transferredMoles());
    }

    @Test void preservesVacuumOutletTemperatureInExactUpstreamFraction() {
        var inlet = gas(10, 300);
        var vacuum = GasMixture.vacuum();
        double expected = 10 - (10 + 0) * vacuum.temperatureKelvin() * 1 / (300 * 1 + vacuum.temperatureKelvin() * 1);
        var result = TegCirculationPolicy.plan(inlet, vacuum, 1, 1);
        assertEquals(expected, result.transferredMoles(), 2e-5);
    }

    @Test void usesProvidedNetworkVolumesForPressureAndTransfer() {
        var inlet = gas(10, 300);
        var outlet = gas(2, 300);
        var result = TegCirculationPolicy.plan(inlet, outlet, 2, 1);
        double expected = 10 - (10 + 2) * 300 * 2 / (300 * 1 + 300 * 2);
        assertEquals(expected, result.transferredMoles(), 3e-5);
        assertTrue(result.pressureDeltaKpa() > 0);
    }

    private static GasMixture gas(double moles, double temperature) {
        return new GasMixture(Map.of(GasType.OXYGEN, moles), temperature);
    }
}
