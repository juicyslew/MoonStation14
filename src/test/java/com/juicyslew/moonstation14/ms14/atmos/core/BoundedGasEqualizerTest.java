package com.juicyslew.moonstation14.ms14.atmos.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedGasEqualizerTest {
    @Test
    void equalMoleMixedGasCellsKeepTheirCompositionAndTemperatureGradient() {
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.NITROGEN, 3.0), 600.0);
        GasMixture cold = new GasMixture(Map.of(GasType.OXYGEN, 3.0, GasType.NITROGEN, 1.0), 200.0);

        List<GasMixture> result = BoundedGasEqualizer.equalize(List.of(hot, cold));

        assertEquals(hot.gasMoles(), result.get(0).gasMoles());
        assertEquals(cold.gasMoles(), result.get(1).gasMoles());
        assertEquals(600.0, result.get(0).temperatureKelvin());
        assertEquals(200.0, result.get(1).temperatureKelvin());
    }

    @Test
    void equalizesMolesWithoutHomogenizingSpeciesOrTemperatures() {
        GasMixture oxygenHot = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 500.0);
        GasMixture nitrogenCold = new GasMixture(Map.of(GasType.NITROGEN, 2.0), 200.0);
        List<GasMixture> result = BoundedGasEqualizer.equalize(List.of(oxygenHot, nitrogenCold));

        assertEquals(3.0, result.get(0).totalMoles(), 1e-12);
        assertEquals(3.0, result.get(1).totalMoles(), 1e-12);
        assertEquals(3.0, result.get(0).moles(GasType.OXYGEN), 1e-12);
        assertEquals(0.0, result.get(0).moles(GasType.NITROGEN));
        assertEquals(2.0, result.get(1).moles(GasType.NITROGEN), 1e-12);
        assertEquals(1.0, result.get(1).moles(GasType.OXYGEN), 1e-12);
        assertEquals(500.0, result.get(0).temperatureKelvin(), 1e-12);
        assertEquals(275.0, result.get(1).temperatureKelvin(), 1e-12);
    }

    @Test
    void conservesEverySpeciesAndThermalEnergyForUnevenMixedCells() {
        List<GasMixture> input = List.of(
                new GasMixture(Map.of(GasType.OXYGEN, 8.0, GasType.NITROGEN, 2.0), 600.0),
                new GasMixture(Map.of(GasType.NITROGEN, 1.0), 250.0),
                new GasMixture(Map.of(GasType.OXYGEN, 1.0, GasType.CARBON_DIOXIDE, 2.0), 350.0));
        List<GasMixture> result = BoundedGasEqualizer.equalize(input);

        for (GasType type : GasType.values()) {
            assertEquals(sum(input, type), sum(result, type), 1e-11, type.name());
        }
        assertEquals(input.stream().mapToDouble(GasMixture::thermalEnergy).sum(),
                result.stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1e-8);
        assertEquals(14.0 / 3.0, result.get(0).totalMoles(), 1e-12);
        assertEquals(14.0 / 3.0, result.get(1).totalMoles(), 1e-12);
        assertEquals(14.0 / 3.0, result.get(2).totalMoles(), 1e-12);
    }

    @Test
    void vacuumAcceptsHotGasAndAllEmptyRegionRemainsWithoutHeat() {
        GasMixture hot = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 400.0);
        List<GasMixture> moved = BoundedGasEqualizer.equalize(List.of(hot, GasMixture.vacuum()));
        assertEquals(1.0, moved.get(0).totalMoles(), 1e-12);
        assertEquals(1.0, moved.get(1).totalMoles(), 1e-12);
        assertEquals(400.0, moved.get(1).temperatureKelvin(), 1e-12);
        assertEquals(hot.thermalEnergy(), moved.stream().mapToDouble(GasMixture::thermalEnergy).sum(), 1e-10);

        List<GasMixture> empty = BoundedGasEqualizer.equalize(List.of(GasMixture.vacuum(), GasMixture.vacuum()));
        assertEquals(0.0, empty.stream().mapToDouble(GasMixture::thermalEnergy).sum());
        assertEquals(2.7, empty.get(0).temperatureKelvin());
        assertEquals(2.7, empty.get(1).temperatureKelvin());
    }

    @Test
    void outputIsImmutableAndIndependentOfInputOrdering() {
        GasMixture a = new GasMixture(Map.of(GasType.OXYGEN, 5.0, GasType.NITROGEN, 1.0), 500.0);
        GasMixture b = new GasMixture(Map.of(GasType.NITROGEN, 2.0), 250.0);
        GasMixture c = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300.0);
        List<GasMixture> first = BoundedGasEqualizer.equalize(List.of(a, b, c));
        List<GasMixture> reverse = BoundedGasEqualizer.equalize(List.of(c, b, a));
        for (int i = 0; i < first.size(); i++) {
            GasMixture corresponding = reverse.get(2 - i);
            assertEquals(first.get(i).gasMoles(), corresponding.gasMoles());
            assertEquals(first.get(i).temperatureKelvin(), corresponding.temperatureKelvin(), 1e-10);
        }
        assertThrows(UnsupportedOperationException.class, () -> first.add(a));
    }

    @Test
    void rejectsOversizedAndInvalidInputsWithoutChangingInput() {
        GasMixture original = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 300.0);
        List<GasMixture> oversized = new ArrayList<>(java.util.Collections.nCopies(
                BoundedGasEqualizer.MAX_EQUALIZE_CELLS + 1, original));
        assertThrows(IllegalArgumentException.class, () -> BoundedGasEqualizer.equalize(oversized));
        assertThrows(IllegalArgumentException.class, () -> BoundedGasEqualizer.equalize(null));
        assertThrows(IllegalArgumentException.class, () -> BoundedGasEqualizer.equalize(List.of()));
        List<GasMixture> withNull = new ArrayList<>();
        withNull.add(original);
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> BoundedGasEqualizer.equalize(withNull));
        assertEquals(4.0, original.totalMoles());
        assertEquals(300.0, original.temperatureKelvin());
    }

    private static double sum(List<GasMixture> mixtures, GasType type) {
        return mixtures.stream().mapToDouble(mixture -> mixture.moles(type)).sum();
    }
}
