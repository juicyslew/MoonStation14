package com.juicyslew.moonstation14.ms14.atmos.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GasMixtureTest {
    @Test
    void stableGasIdsAndHeatCapacities() {
        assertEquals("carbon_dioxide", GasType.CARBON_DIOXIDE.id());
        assertEquals(GasType.CARBON_DIOXIDE, GasType.fromId("carbon_dioxide"));
        assertEquals(600.0, GasType.FREZON.molarHeatCapacity());
        assertThrows(IllegalArgumentException.class, () -> GasType.fromId("not_a_gas"));
    }

    @Test
    void mixturesSnapshotInputAndExposeUnmodifiableComposition() {
        Map<GasType, Double> source = new HashMap<>();
        source.put(GasType.OXYGEN, 2.0);
        GasMixture mixture = new GasMixture(source, 300.0);
        source.put(GasType.OXYGEN, 9.0);
        assertEquals(2.0, mixture.moles(GasType.OXYGEN));
        assertThrows(UnsupportedOperationException.class, () -> mixture.gasMoles().put(GasType.NITROGEN, 1.0));
    }

    @Test
    void vacuumAndBreathableAirHaveSpecifiedState() {
        assertEquals(2.7, GasMixture.vacuum().temperatureKelvin(), Math.ulp((float) 2.7));
        GasMixture air = GasMixture.breathableAir();
        assertEquals(101.325, air.pressureKpa(1.0), 2e-5);
        assertEquals(0.21, air.moles(GasType.OXYGEN) / air.totalMoles(), 1e-7);
        assertEquals(0.79, air.moles(GasType.NITROGEN) / air.totalMoles(), 1e-7);
    }

    @Test
    void gasInjectionCarriesEnthalpyAndEnergyOperationsRejectInvalidStates() {
        GasMixture injected = GasMixture.vacuum().withGasDelta(GasType.OXYGEN, 1.0, 400.0);
        assertEquals(400.0, injected.temperatureKelvin());
        assertEquals(injected.thermalEnergy() + 20.0, injected.withEnergyDelta(20.0).thermalEnergy(), 1e-4);
        assertThrows(IllegalArgumentException.class, () -> injected.withEnergyDelta(-injected.thermalEnergy() - 1));
        assertThrows(IllegalArgumentException.class, () -> new GasMixture(Map.of(GasType.OXYGEN, -1.0), 300.0));
        assertThrows(IllegalArgumentException.class, () -> new GasMixture(Map.of(), Double.NaN));
        assertThrows(IllegalStateException.class, () -> GasMixture.vacuum().withThermalEnergy(1.0));
    }

    @Test
    void gasRemovalUsesSourceTemperatureAndCannotDiscardEnergy() {
        GasMixture mixture = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 300.0);
        double originalEnergy = mixture.thermalEnergy();
        assertThrows(IllegalArgumentException.class,
                () -> mixture.withGasDelta(GasType.OXYGEN, -2.0, 400.0));
        assertThrows(IllegalArgumentException.class,
                () -> mixture.withGasDelta(GasType.OXYGEN, -2.0, 0.0));
        assertEquals(2.0, mixture.moles(GasType.OXYGEN));
        assertEquals(originalEnergy, mixture.thermalEnergy());

        GasMixture partiallyRemoved = mixture.withGasDelta(GasType.OXYGEN, -1.0);
        assertEquals(1.0, partiallyRemoved.moles(GasType.OXYGEN));
        assertEquals(originalEnergy / 2.0, partiallyRemoved.thermalEnergy(), 1e-4);
        assertEquals(originalEnergy, partiallyRemoved.thermalEnergy()
                + GasType.OXYGEN.molarHeatCapacity() * mixture.temperatureKelvin());
        GasMixture emptied = mixture.withGasDelta(GasType.OXYGEN, -2.0);
        assertEquals(0.0, emptied.totalMoles());
        assertEquals(0.0, emptied.thermalEnergy());
    }

    @Test
    void proportionalScalingPreservesCompositionAndScalesThermalEnergy() {
        GasMixture mixture = new GasMixture(Map.of(
                GasType.OXYGEN, 2.0,
                GasType.NITROGEN, 5.0,
                GasType.CARBON_DIOXIDE, 1.0), 320.0);
        double energy = mixture.thermalEnergy();
        GasMixture remaining = mixture.withScaledMoles(0.25);

        assertEquals(2.0, remaining.totalMoles());
        assertEquals(0.25, remaining.moles(GasType.OXYGEN) / mixture.moles(GasType.OXYGEN));
        assertEquals(0.25, remaining.moles(GasType.NITROGEN) / mixture.moles(GasType.NITROGEN));
        assertEquals(0.25, remaining.moles(GasType.CARBON_DIOXIDE) / mixture.moles(GasType.CARBON_DIOXIDE));
        assertEquals(mixture.temperatureKelvin(), remaining.temperatureKelvin());
        assertEquals(energy * 0.25, remaining.thermalEnergy(), 1e-3);
        assertEquals(energy, remaining.thermalEnergy() + mixture.withScaledMoles(0.75).thermalEnergy(), 1e-3);
        assertEquals(8.0, mixture.totalMoles());

        GasMixture empty = mixture.withScaledMoles(0.0);
        assertEquals(0.0, empty.totalMoles());
        assertEquals(0.0, empty.thermalEnergy());
        assertEquals(mixture.temperatureKelvin(), empty.temperatureKelvin());
        assertEquals(mixture.totalMoles(), mixture.withScaledMoles(1.0).totalMoles());
    }

    @Test
    void proportionalScalingRejectsInvalidFractions() {
        GasMixture mixture = GasMixture.breathableAir();
        assertThrows(IllegalArgumentException.class, () -> mixture.withScaledMoles(-0.01));
        assertThrows(IllegalArgumentException.class, () -> mixture.withScaledMoles(1.01));
        assertThrows(IllegalArgumentException.class, () -> mixture.withScaledMoles(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> mixture.withScaledMoles(Double.POSITIVE_INFINITY));
    }

    @Test
    void breathableAirInjectionCombinesSpeciesAndTheirInjectedHeat() {
        GasMixture initial = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 2.0), 250.0);
        double injectedMoles = 4.0;
        double injectedTemperature = 400.0;
        GasMixture combined = initial
                .withGasDelta(GasType.OXYGEN, injectedMoles * 0.21, injectedTemperature)
                .withGasDelta(GasType.NITROGEN, injectedMoles * 0.79, injectedTemperature);

        assertEquals((double) (float) .84, combined.moles(GasType.OXYGEN), 0);
        assertEquals((double) (float) 3.16, combined.moles(GasType.NITROGEN), 0);
        assertEquals(2.0, combined.moles(GasType.CARBON_DIOXIDE));
        double expectedInjectedEnergy = injectedMoles *
                (0.21 * GasType.OXYGEN.molarHeatCapacity() + 0.79 * GasType.NITROGEN.molarHeatCapacity())
                * injectedTemperature;
        assertEquals(initial.thermalEnergy() + expectedInjectedEnergy, combined.thermalEnergy(), 1e-3);
    }

    @Test
    void mixturesRejectNonFiniteAggregateValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new GasMixture(Map.of(GasType.FREZON, 1e306), 300.0));
        assertEquals(262144, new GasMixture(Map.of(GasType.OXYGEN, 1e10), 1e210).temperatureKelvin());
    }

    @Test
    void explicitRemovalTemperatureComparesAtStoredFloatPrecision() {
        GasMixture mixture = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 293.15);
        GasMixture removed = mixture.withGasDelta(GasType.OXYGEN, -1.0, 293.15);
        assertEquals(1.0, removed.moles(GasType.OXYGEN));
    }

    @Test
    void upstreamReferenceQuantitiesScaleOnlyByCellVolumeRatio() {
        assertEquals(0.4, AtmosphereUnits.referenceTileQuantity(1.0));
        assertEquals(0.004, AtmosphereUnits.referenceTileQuantity(0.01));
        assertEquals(57_200, AtmosphereUnits.referenceTileQuantity(143_000));
        assertEquals(0.10393, AtmosphereUnits.referenceTileQuantity(0.259825), 1e-12);
        assertEquals(1.0, AtmosphereUnits.minecraftCellVolumeCubicMeters());
        assertEquals(143_000, AtmosphereUnits.localCellQuantityToReferenceTile(57_200));
    }

    @Test
    void upstreamMixtureClampMergeAndRemovalContracts() {
        assertEquals(2.7, new GasMixture(Map.of(), -10).temperatureKelvin(), Math.ulp((float) 2.7));
        assertEquals(262144, new GasMixture(Map.of(), 300000).temperatureKelvin());
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 8e-8, GasType.NITROGEN, 1.0), 300, 1.0);
        GasMixture removed = source.removeRatio(0.5);
        assertEquals(0.0, removed.moles(GasType.OXYGEN)); // below upstream GasMinMoles
        assertEquals(0.5, removed.moles(GasType.NITROGEN));
        assertEquals(1.0, removed.volumeCubicMeters());
        GasMixture.Removal split = source.splitRatio(0.5);
        assertEquals(0.0, split.remaining().moles(GasType.OXYGEN));
        assertEquals(0.0, split.removed().moles(GasType.OXYGEN));
        assertEquals(0.5, split.remaining().moles(GasType.NITROGEN));
        assertEquals(source.totalMoles(), source.splitRatio(0).remaining().totalMoles());

        GasMixture customVolume = new GasMixture(Map.of(GasType.OXYGEN, 1.123456789), 300, 0.25);
        assertEquals((double) (float) 1.123456789, customVolume.moles(GasType.OXYGEN));
        assertEquals(0.25, customVolume.withGasDelta(GasType.NITROGEN, 1).volumeCubicMeters());
        assertEquals(0.25, customVolume.withScaledMoles(0.5).volumeCubicMeters());
        assertEquals(0.25, customVolume.withThermalEnergy(customVolume.thermalEnergy()).volumeCubicMeters());
        assertEquals(0, customVolume.pressureKpa(0));
        assertEquals(customVolume.pressureKpa(0.25), customVolume.pressureKpa(), 0);
        assertEquals(0, new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300, 0).pressureKpa());

        GasMixture near = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300.005);
        GasMixture nearMerged = near.mergedWith(new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300));
        assertEquals(300.005, nearMerged.temperatureKelvin(), Math.ulp((float) 300.005)); // <= 0.01 gate retains receiver temperature
        GasMixture farMerged = near.mergedWith(new GasMixture(Map.of(GasType.OXYGEN, 1.0), 310));
        assertEquals((300.005 + 310) / 2, farMerged.temperatureKelvin(), Math.ulp((float) 305.0025));
        GasMixture trace = new GasMixture(Map.of(GasType.OXYGEN, 1e-7), 300);
        assertEquals((double) 0.0003f, trace.normalizedHeatCapacity(), 0);
        assertEquals(0.0024, trace.effectiveHeatCapacity(), 1e-9);
        assertEquals(0.0003 * 300, trace.simulationThermalEnergy(), 1e-5);
        // Each tiny mixture uses upstream's normalized Cp floor in the merge's weighted temperature.
        assertEquals(305, trace.mergedWith(new GasMixture(Map.of(GasType.NITROGEN, 1e-7), 310)).temperatureKelvin(),
                2 * Math.ulp(305f));
        assertEquals(2800, GasMixture.spaceNormalizedHeatCapacity());
        GasMixture roomGas = new GasMixture(Map.of(GasType.NITROGEN, 10.0), 300);
        GasMixture immutableSpaceProfile = GasMixture.vacuum();
        GasMixture spaceMerged = roomGas.mergedWith(immutableSpaceProfile, GasMixture.spaceNormalizedHeatCapacity());
        assertEquals((37.5 * 300 + 2800 * 2.7) / (37.5 + 2800), spaceMerged.temperatureKelvin(), 0.01);
        assertEquals(10.0, spaceMerged.totalMoles()); // virtual capacity does not create gas
        assertThrows(IllegalArgumentException.class, () -> roomGas.mergedWith(immutableSpaceProfile, 0.0002));
    }
}
