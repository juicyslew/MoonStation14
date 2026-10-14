package com.juicyslew.moonstation14.ms14.atmos.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GasPressureReleaseTest {
    @Test
    void transfersFinitePressureLimitedPacketAndConservesSpeciesAndEnergy() {
        GasMixture source = new GasMixture(Map.of(GasType.PLASMA, 2769.36), 293.15, 1.5);
        GasMixture receiver = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 310, .005);
        double sourceEnergy = source.thermalEnergy();
        double receiverEnergy = receiver.thermalEnergy();
        double sourcePlasma = source.moles(GasType.PLASMA);

        var result = GasPressureRelease.release(source, receiver, 1013.25);

        assertTrue(result.transferredMoles() > 0);
        assertEquals(1.5, result.sourceAfter().volumeCubicMeters(), 1e-6);
        assertEquals(.005, result.receiverAfter().volumeCubicMeters(), 1e-9);
        assertEquals(sourcePlasma, result.sourceAfter().moles(GasType.PLASMA)
                + result.receiverAfter().moles(GasType.PLASMA), .01);
        assertEquals(1, result.receiverAfter().moles(GasType.OXYGEN), 1e-6);
        assertEquals(sourceEnergy, result.sourceAfter().thermalEnergy()
                + result.transferredMoles() * GasType.PLASMA.molarHeatCapacity() * source.temperatureKelvin(), 200);
        assertEquals(receiverEnergy + result.transferredMoles() * GasType.PLASMA.molarHeatCapacity()
                * source.temperatureKelvin(), result.receiverAfter().thermalEnergy(), 200);
        assertEquals(sourcePlasma, source.moles(GasType.PLASMA), 0);
    }

    @Test
    void refusesEmptyLowHeadroomAndAlreadyAtTarget() {
        GasMixture vacuum = GasMixture.vacuum();
        assertEquals(0, GasPressureRelease.release(vacuum, vacuum, 100).transferredMoles());
        GasMixture low = new GasMixture(Map.of(GasType.NITROGEN, .001), 293.15, .001);
        assertEquals(0, GasPressureRelease.release(low, vacuum, 100).transferredMoles());
        GasMixture pressurized = new GasMixture(Map.of(GasType.NITROGEN, 1.0), 293.15, .001);
        assertEquals(0, GasPressureRelease.release(low, pressurized, 0).transferredMoles());
    }

    @Test
    void capsAtHalfPressureDifferenceWhenTargetIsHigh() {
        GasMixture source = new GasMixture(Map.of(GasType.NITROGEN, 100.0), 293.15, .01);
        GasMixture receiver = new GasMixture(Map.of(), 2.7, .01);
        var result = GasPressureRelease.release(source, receiver, 100_000);
        assertTrue(result.transferredMoles() < source.totalMoles());
        assertEquals(0, result.sourceAfter().pressureKpa() - result.receiverAfter().pressureKpa(), 1.0);
    }
}
