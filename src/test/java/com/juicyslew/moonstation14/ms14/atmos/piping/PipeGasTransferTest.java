package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasTransferTest {
    @Test void selectiveTransferConservesSpeciesAndEnergyWithoutMutatingInputs() {
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 4.0, GasType.PLASMA, 2.0), 800.0);
        GasMixture destination = new GasMixture(Map.of(GasType.NITROGEN, 3.0), 200.0);
        var first = PipeGasTransfer.plan(source, destination, 1, 1, 1, 10_000, Set.of(GasType.OXYGEN)).orElseThrow();
        var again = PipeGasTransfer.plan(source, destination, 1, 1, 1, 10_000, Set.of(GasType.OXYGEN)).orElseThrow();
        assertEquals(first.perGasMoved(), again.perGasMoved());
        assertEquals(1.0, first.perGasMoved().get(GasType.OXYGEN), 1e-12);
        assertEquals(2.0, first.sourceAfter().moles(GasType.PLASMA), 0.0);
        assertEquals(0.0, first.packet().moles(GasType.PLASMA), 0.0);
        assertEquals(800.0, source.temperatureKelvin(), 0.0);
        assertEquals(200.0, destination.temperatureKelvin(), 0.0);
        assertEquals(source.thermalEnergy() + destination.thermalEnergy(),
                first.sourceAfter().thermalEnergy() + first.destinationAfter().thermalEnergy(), 1e-9);
    }

    @Test void pressureCapBoundsTransferAndAlreadyFullDestinationIsNoOp() {
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 100.0), 300.0);
        GasMixture empty = GasMixture.vacuum();
        var plan = PipeGasTransfer.plan(source, empty, 1, 1, 100, 10, Set.of(GasType.OXYGEN)).orElseThrow();
        assertTrue(plan.destinationAfter().pressureKpa(1) <= 10);
        GasMixture full = new GasMixture(Map.of(GasType.NITROGEN, 100.0), 300.0);
        assertTrue(PipeGasTransfer.plan(source, full, 1, 1, 100, 1, Set.of(GasType.OXYGEN)).isEmpty());
    }

    @Test void validatesParametersAndHandlesTinyTraceAndZeroRequest() {
        GasMixture trace = new GasMixture(Map.of(GasType.OXYGEN, 1e-100), 300.0);
        assertTrue(PipeGasTransfer.plan(trace, GasMixture.vacuum(), 1, 1, 1e-101, 1, Set.of(GasType.OXYGEN)).isPresent());
        assertTrue(PipeGasTransfer.plan(trace, GasMixture.vacuum(), 1, 1, 0, 1, Set.of(GasType.OXYGEN)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PipeGasTransfer.plan(trace, GasMixture.vacuum(), 0, 1, 1, 1, Set.of(GasType.OXYGEN)));
        assertThrows(IllegalArgumentException.class, () -> PipeGasTransfer.plan(trace, GasMixture.vacuum(), 1, 1, Double.POSITIVE_INFINITY, 1, Set.of(GasType.OXYGEN)));
    }

    @Test void rejectsFractionThatCannotRepresentEverySelectedSpecies() {
        GasMixture source = new GasMixture(Map.of(GasType.OXYGEN, 1e10, GasType.PLASMA, 1e-320), 300.0);
        GasMixture destination = GasMixture.vacuum();
        assertTrue(PipeGasTransfer.plan(source, destination, 1, 1, 1e-5, 1e300,
                Set.of(GasType.OXYGEN, GasType.PLASMA)).isEmpty());

        var oxygenOnly = PipeGasTransfer.plan(source, destination, 1, 1, 1e-5, 1e300,
                Set.of(GasType.OXYGEN)).orElseThrow();
        assertTrue(oxygenOnly.perGasMoved().get(GasType.OXYGEN) > 0.0);
        assertFalse(oxygenOnly.perGasMoved().containsKey(GasType.PLASMA));
    }
}
