package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExplosionHotspotExposureTest {
    private static GasMixture fuelled() {
        return new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.TRITIUM, 1.0), 293.15);
    }

    @Test
    void coldOxygenAndTritiumCanBeExposedAtExplosionTemperature() {
        var decision = ExplosionHotspotExposure.decide(fuelled(), 800, 0.01, 0, 0);
        assertTrue(decision.expose());
        assertEquals(800, decision.temperatureKelvin());
        assertEquals(0.00025, decision.cellFraction());

        var intensityTen = ExplosionHotspotExposure.decide(fuelled(), 800, 10, 0, 0);
        assertEquals(0.25, intensityTen.cellFraction());
        var tinyFuel = new GasMixture(Map.of(GasType.OXYGEN, 1.0e-10, GasType.TRITIUM, 1.0e-10), 293.15);
        assertTrue(ExplosionHotspotExposure.decide(tinyFuel, 800, 0.01, 0, 0).expose());
    }

    @Test
    void requiresOxidizerAndFuelAndHotspotTemperature() {
        assertFalse(ExplosionHotspotExposure.decide(new GasMixture(Map.of(GasType.OXYGEN, 1.0), 293), 800, 1, 0, 0).expose());
        assertFalse(ExplosionHotspotExposure.decide(new GasMixture(Map.of(GasType.TRITIUM, 1.0), 293), 800, 1, 0, 0).expose());
        assertFalse(ExplosionHotspotExposure.decide(fuelled(), 373.15, 1, 0, 0).expose());
    }

    @Test
    void intensitySaturatesAndExistingHotspotIsNeverReduced() {
        var decision = ExplosionHotspotExposure.decide(fuelled(), 800, 40, 1000, 0.3);
        assertEquals(1, decision.cellFraction());
        assertEquals(1000, decision.temperatureKelvin());
        var repeated = ExplosionHotspotExposure.decide(fuelled(), decision.temperatureKelvin(), 100,
                decision.temperatureKelvin(), decision.cellFraction());
        assertEquals(decision, repeated);
    }

    @Test
    void invalidInputsAreNoOpAndOutputsRemainFinite() {
        assertFalse(ExplosionHotspotExposure.decide(fuelled(), Double.NaN, 1, 0, 0).expose());
        assertFalse(ExplosionHotspotExposure.decide(fuelled(), 800, Double.POSITIVE_INFINITY, 0, 0).expose());
        assertFalse(ExplosionHotspotExposure.decide(fuelled(), 800, 1, 0, 1.01).expose());
        var decision = ExplosionHotspotExposure.decide(fuelled(), 800, Double.MAX_VALUE, 0, 0);
        assertTrue(decision.expose());
        assertTrue(Double.isFinite(decision.temperatureKelvin()));
        assertTrue(Double.isFinite(decision.cellFraction()));
    }
}
