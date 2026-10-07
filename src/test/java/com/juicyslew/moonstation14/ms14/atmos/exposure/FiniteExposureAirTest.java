package com.juicyslew.moonstation14.ms14.atmos.exposure;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FiniteExposureAirTest {
    @Test void validatesBeforeCommittingAndKeepsFiniteExhaledSpeciesAndEnergy() {
        var source = new FiniteExposureAir(new GasMixture(Map.of(GasType.OXYGEN, 3d), 300), .07);
        var exhaled = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, .2), 310);
        var before = source.sample().orElseThrow().mixture();
        AtomicInteger validations = new AtomicInteger();
        assertTrue(source.exchange(1, exhaled, gas -> { validations.incrementAndGet(); return false; }).isEmpty());
        assertEquals(1, validations.get());
        assertEquals(before, source.sample().orElseThrow().mixture());

        var exchange = source.exchange(1, exhaled, gas -> { validations.incrementAndGet(); return true; }).orElseThrow();
        assertEquals(2, validations.get());
        assertEquals(1, exchange.inhaled().moles(GasType.OXYGEN), 1e-7);
        var after = source.sample().orElseThrow().mixture();
        // Mixture species are stored as floats; compare against the representable input.
        assertEquals((double) .2f, after.moles(GasType.CARBON_DIOXIDE), 1e-7);
        assertEquals(2, after.moles(GasType.OXYGEN), 1e-7);
        assertTrue(after.thermalEnergy() > 0);
    }

    @Test void finiteExhaustionIsKnownVacuumAndSampleCarriesPhysicalVolume() {
        var source = new FiniteExposureAir(GasMixture.vacuum(), .07);
        var sample = source.sample().orElseThrow();
        assertEquals(.07, sample.volumeCubicMeters());
        assertEquals(0, sample.pressureKpa());
        var result = source.exchange(10, GasMixture.vacuum(), gas -> true).orElseThrow();
        assertEquals(0, result.inhaled().totalMoles());
    }
}
