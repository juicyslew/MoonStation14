package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits;
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
        assertEquals(0.0001, decision.cellFraction());
        assertEquals(0.004, decision.exposedVolumeLiters());

        var intensityTen = ExplosionHotspotExposure.decide(fuelled(), 800, 10, 0, 0);
        assertEquals(0.1, intensityTen.cellFraction());
        var tinyFuel = new GasMixture(Map.of(GasType.OXYGEN, 1.0e-10, GasType.TRITIUM, 1.0e-10), 293.15);
        assertFalse(ExplosionHotspotExposure.decide(tinyFuel, 800, 0.01, 0, 0).expose());
    }

    @Test
    void requiresOxidizerAndFuelAndHotspotTemperature() {
        assertFalse(ExplosionHotspotExposure.decide(new GasMixture(Map.of(GasType.OXYGEN, 1.0), 293), 800, 1, 0, 0).expose());
        assertFalse(ExplosionHotspotExposure.decide(new GasMixture(Map.of(GasType.TRITIUM, 1.0), 293), 800, 1, 0, 0).expose());
        assertFalse(ExplosionHotspotExposure.decide(fuelled(), 373.15, 1, 0, 0).expose());
        assertTrue(ExplosionHotspotExposure.decide(fuelled(), Math.nextUp(373.15f), 1, 0, 0).expose());
    }

    @Test
    void intensitySaturatesAndExistingHotspotIsNeverReduced() {
        var decision = ExplosionHotspotExposure.decide(fuelled(), 800, 100, 1000, 0.3);
        assertEquals(1, decision.cellFraction());
        assertEquals(1000, decision.temperatureKelvin());
        var repeated = ExplosionHotspotExposure.decide(fuelled(), decision.temperatureKelvin(), 100,
                decision.temperatureKelvin(), decision.cellFraction());
        assertEquals(decision.cellFraction(), repeated.cellFraction());
        assertEquals(decision.temperatureKelvin(), repeated.temperatureKelvin());
        assertEquals(40, decision.exposedVolumeLiters());
        assertEquals(40, repeated.exposedVolumeLiters());
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

    @Test
    void existingHotspotCanBeBoostedBelowIgnitionTemperatureAndInputVolumeIsUnscaled() {
        var decision = ExplosionHotspotExposure.decide(fuelled(), 300, 2, 400, 0.1);
        assertTrue(decision.expose());
        assertEquals(400, decision.temperatureKelvin());
        assertEquals(0.8, decision.exposedVolumeLiters());
        assertEquals(0.1, decision.cellFraction());
    }

    @Test
    void oxygenAndFuelThresholdsAreStrictlyAboveHalfMole() {
        double threshold = (double) (float) AtmosphereUnits.referenceTileQuantity(.5);
        var lowOxygen = new GasMixture(Map.of(GasType.OXYGEN, threshold, GasType.TRITIUM, 1.0), 293.15);
        var lowFuel = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.TRITIUM, threshold), 293.15);
        assertFalse(ExplosionHotspotExposure.decide(lowOxygen, 800, 1, 0, 0).expose());
        assertFalse(ExplosionHotspotExposure.decide(lowFuel, 800, 1, 0, 0).expose());
        var mixedFuel = new GasMixture(Map.of(GasType.OXYGEN, (double) Math.nextUp((float) threshold),
                GasType.TRITIUM, (double) .1f, GasType.PLASMA, (double) .1f), 293.15);
        assertFalse(ExplosionHotspotExposure.decide(mixedFuel, 800, 1, 0, 0).expose());
        var above = new GasMixture(Map.of(GasType.OXYGEN, (double) Math.nextUp((float) threshold),
                GasType.TRITIUM, (double) Math.nextUp((float) threshold)), 293.15);
        assertTrue(ExplosionHotspotExposure.decide(above, 800, 1, 0, 0).expose());
    }
}
