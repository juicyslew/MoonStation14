package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasLedgerTest {
    @Test
    void splitAndMergePreserveSpeciesAndThermalEnergyAcrossVolumes() {
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 3.0, GasType.PLASMA, 1.0), 300.0);
        List<GasMixture> parts = PipeGasLedger.split(source, new double[]{1.0, 2.0, 3.0});
        assertEquals(0.5, parts.get(0).moles(GasType.OXYGEN), 1e-15);
        assertEquals(0.5, parts.get(2).moles(GasType.PLASMA), 0.0);
        GasMixture merged = PipeGasLedger.merge(parts);
        assertEquals(source.moles(GasType.OXYGEN), merged.moles(GasType.OXYGEN), 1e-14);
        assertEquals(source.moles(GasType.PLASMA), merged.moles(GasType.PLASMA), 1e-14);
        assertEquals(source.thermalEnergy(), merged.thermalEnergy(), 1e-10);
    }

    @Test
    void hotColdAndVacuumMergeUseEnergyAndZeroVacuumCapacity() {
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 600.0);
        GasMixture cold = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 150.0);
        GasMixture merged = PipeGasLedger.merge(List.of(hot, cold, GasMixture.vacuum()));
        assertEquals((hot.thermalEnergy() + cold.thermalEnergy()) / merged.heatCapacity(), merged.temperatureKelvin(), 1e-12);
        assertEquals(hot.thermalEnergy() + cold.thermalEnergy(), merged.thermalEnergy(), 1e-10);
    }

    @Test
    void preservesTinyTraceAndRejectsInvalidOrUnrepresentableArithmetic() {
        GasMixture trace = new GasMixture(Map.of(GasType.OXYGEN, 1e-100), 300.0);
        GasMixture roundTrip = PipeGasLedger.merge(PipeGasLedger.split(trace, new double[]{1.0, 2.0}));
        assertEquals(1e-100, roundTrip.moles(GasType.OXYGEN), 1e-115);
        assertThrows(IllegalArgumentException.class, () -> PipeGasLedger.split(trace, new double[]{Double.MAX_VALUE, Double.MAX_VALUE}));
        assertThrows(IllegalArgumentException.class, () -> PipeGasLedger.split(trace, new double[]{0.0}));
        assertThrows(IllegalArgumentException.class, () -> PipeGasLedger.split(trace, new double[]{Double.MIN_VALUE, Double.MAX_VALUE}));
        assertThrows(IllegalArgumentException.class, () -> PipeGasLedger.merge(List.of(
                new GasMixture(Map.of(GasType.FREZON, 1.5e306), 1.0), new GasMixture(Map.of(GasType.FREZON, 1.5e306), 1.0))));
    }

    @Test
    void apportionsEnergyUsingRecipientCompositionAndLastResidual() {
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.PLASMA, 1.0), 400.0);
        List<GasMixture> parts = PipeGasLedger.split(source, new double[]{1.0, 3.0});
        GasMixture merged = PipeGasLedger.merge(parts);
        assertEquals(source.thermalEnergy(), merged.thermalEnergy(), Math.max(Math.ulp(source.thermalEnergy()) * 8, source.thermalEnergy() * 1e-14));
        assertEquals(source.thermalEnergy() - parts.get(0).thermalEnergy(), parts.get(1).thermalEnergy(), Math.ulp(source.thermalEnergy()) * 2);
    }

    @Test
    void handlesZeroKelvinAndVacuumAndRejectsUnrepresentableEnergy() {
        GasMixture zeroK = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 0.0);
        assertEquals(0.0, PipeGasLedger.merge(PipeGasLedger.split(zeroK, new double[]{1.0, 2.0})).thermalEnergy());
        assertEquals(0.0, PipeGasLedger.merge(PipeGasLedger.split(GasMixture.vacuum(), new double[]{1.0, 2.0})).thermalEnergy());
        assertThrows(IllegalArgumentException.class, () -> PipeGasLedger.split(zeroK, new double[]{Double.MAX_VALUE, Double.MAX_VALUE}));
    }
}
