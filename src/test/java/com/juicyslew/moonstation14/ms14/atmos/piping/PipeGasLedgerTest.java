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
        assertEquals(0.5, parts.get(0).moles(GasType.OXYGEN), Math.ulp(0.5f) * 2);
        assertEquals(0.5, parts.get(2).moles(GasType.PLASMA), 0.0);
        GasMixture merged = PipeGasLedger.merge(parts);
        assertEquals(source.moles(GasType.OXYGEN), merged.moles(GasType.OXYGEN), 1e-14);
        assertEquals(source.moles(GasType.PLASMA), merged.moles(GasType.PLASMA), 1e-14);
        assertEquals(source.thermalEnergy(), merged.thermalEnergy(), 1e-10);
    }

    @Test void floatCanonicalFractionsAndUnequalVolumesConserveRealizedInventoryAndEnergy() {
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 0.1, GasType.NITROGEN, 0.2), 300.0, 0.6);
        List<GasMixture> parts = PipeGasLedger.split(source, new double[]{1.0, 2.0, 3.0});
        GasMixture merged = PipeGasLedger.merge(parts);
        assertEquals(source.moles(GasType.OXYGEN), merged.moles(GasType.OXYGEN), 0.0);
        assertEquals(source.moles(GasType.NITROGEN), merged.moles(GasType.NITROGEN), 0.0);
        assertEquals(source.thermalEnergy(), merged.thermalEnergy(), Math.ulp((float) source.thermalEnergy()) * 8);
        assertEquals((float) ((float) source.moles(GasType.OXYGEN) / 6), (float) parts.get(0).moles(GasType.OXYGEN), 0.0f);
        assertEquals((float) 0.6, (float) parts.stream().mapToDouble(GasMixture::volumeCubicMeters).sum(), Math.ulp((float) .6f) * 2);
    }

    @Test
    void hotColdAndVacuumMergeUseEnergyAndZeroVacuumCapacity() {
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 600.0);
        GasMixture cold = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 150.0);
        GasMixture merged = PipeGasLedger.merge(List.of(hot, cold, GasMixture.vacuum()));
        assertEquals((hot.thermalEnergy() + cold.thermalEnergy()) / merged.heatCapacity(), merged.temperatureKelvin(), 1e-12);
        assertEquals(hot.thermalEnergy() + cold.thermalEnergy(), merged.thermalEnergy(), 1e-10);
    }

    @Test void mergeCanRetainOwnerVolumeWithoutChangingSpeciesOrHeatMix() {
        var hot=new GasMixture(Map.of(GasType.OXYGEN,2.0),600.0,.2);
        var cold=new GasMixture(Map.of(GasType.NITROGEN,3.0),200.0,.15);
        var legacy=PipeGasLedger.merge(List.of(hot,cold));
        var owned=PipeGasLedger.merge(List.of(hot,cold),.35);
        assertEquals(legacy.gasMoles(),owned.gasMoles());
        assertEquals(legacy.temperatureKelvin(),owned.temperatureKelvin(),0);
        assertEquals(legacy.thermalEnergy(),owned.thermalEnergy(),0);
        assertEquals((double)(float).35,owned.volumeCubicMeters(),0);
        assertThrows(IllegalArgumentException.class,()->PipeGasLedger.merge(List.of(hot),0));
    }

    @Test void explicitVolumeMergeKeepsEmptyReceiverTemperatureWhileAggregateKeepsLegacyFloor() {
        var receiver=new GasMixture(Map.of(),293.15,.2);
        var emptyPacket=GasMixture.vacuum();
        var owned=PipeGasLedger.merge(List.of(receiver,emptyPacket),.2);
        var aggregate=PipeGasLedger.merge(List.of(receiver,emptyPacket));
        assertEquals((double)(float)293.15,owned.temperatureKelvin());
        assertEquals((double)(float).2,owned.volumeCubicMeters(),0);
        assertEquals((double)(float)GasMixture.MIN_TEMPERATURE_KELVIN,aggregate.temperatureKelvin());
    }

    @Test
    void preservesTinyTraceAndRejectsInvalidOrUnrepresentableArithmetic() {
        GasMixture trace = new GasMixture(Map.of(GasType.OXYGEN, 1e-30), 300.0);
        GasMixture roundTrip = PipeGasLedger.merge(PipeGasLedger.split(trace, new double[]{1.0, 2.0}));
        assertEquals((double) (float) 1e-30, roundTrip.moles(GasType.OXYGEN), 0.0);
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
        assertEquals(zeroK.thermalEnergy(), PipeGasLedger.merge(PipeGasLedger.split(zeroK, new double[]{1.0, 2.0})).thermalEnergy(),
                Math.ulp((float) zeroK.thermalEnergy()) * 8);
        assertEquals(0.0, PipeGasLedger.merge(PipeGasLedger.split(GasMixture.vacuum(), new double[]{1.0, 2.0})).thermalEnergy());
        assertThrows(IllegalArgumentException.class, () -> PipeGasLedger.split(zeroK, new double[]{Double.MAX_VALUE, Double.MAX_VALUE}));
    }
}
