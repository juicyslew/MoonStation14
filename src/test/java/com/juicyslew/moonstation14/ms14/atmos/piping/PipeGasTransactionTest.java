package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasTransactionTest {
    @Test void planCannotBeConstructedByCallers() {
        assertTrue(java.util.Arrays.stream(PipeGasTransaction.Plan.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
        var source = source(); var destination = destination();
        var plan = plan(source, destination);
        for (GasType gas : GasType.values())
            assertEquals(source.gas.moles(gas) + destination.gas.moles(gas),
                    plan.transfer().sourceAfter().moles(gas) + plan.transfer().destinationAfter().moles(gas), 1e-12);
        assertEquals(source.gas.thermalEnergy() + destination.gas.thermalEnergy(),
                plan.transfer().sourceAfter().thermalEnergy() + plan.transfer().destinationAfter().thermalEnergy(), 1e-8);
    }

    private static class MemoryEndpoint implements PipeGasTransaction.Endpoint {
        final Object id = new Object();
        GasMixture gas;
        long version = 1;
        int writes, publications;
        int failWrite;
        boolean throwsOnFailure;
        MemoryEndpoint(GasMixture gas) { this.gas = gas; }
        public Object identity() { return id; }
        public long version() { return version; }
        public GasMixture snapshot() { return gas; }
        public boolean eligible() { return true; }
        public boolean writeRaw(GasMixture before, GasMixture next) {
            writes++;
            if (next != null && before != null && !same(gas, before)) return false;
            gas = next;
            if (writes == failWrite) {
                if (throwsOnFailure) throw new IllegalStateException("injected");
                return false;
            }
            return true;
        }
        public void publish() { publications++; }
    }

    @Test void rollbackIncludesRejectingAndThrowingWriterEndpoints() {
        for (int failedEnd = 1; failedEnd <= 2; failedEnd++) for (boolean throwing : new boolean[]{false, true}) {
            var source = source(); var destination = destination();
            var plan = plan(source, destination);
            MemoryEndpoint failing = failedEnd == 1 ? source : destination;
            failing.failWrite = failedEnd == 1 ? 1 : 1;
            failing.throwsOnFailure = throwing;
            GasMixture originalSource = source.gas, originalDestination = destination.gas;
            if (failedEnd == 1) assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
            else assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
            assertTrue(same(source.gas, originalSource)); assertTrue(same(destination.gas, originalDestination));
            assertEquals(0, source.publications + destination.publications);
        }
    }

    @Test void destinationRejectAfterMutationRestoresBothEnds() {
        var source = source(); var destination = destination(); var plan = plan(source, destination);
        GasMixture sb = source.gas, db = destination.gas;
        destination.failWrite = 1;
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
        assertTrue(same(source.gas, sb)); assertTrue(same(destination.gas, db));
        assertEquals(0, source.publications + destination.publications);
    }

    @Test void staleInputsAreRejectedBeforeWrites() {
        var source = source(); var destination = destination(); var plan = plan(source, destination);
        source.version++;
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
        assertEquals(0, source.writes + destination.writes);
        source.version--;
        destination.gas = new GasMixture(Map.of(GasType.NITROGEN, 1.0), 300);
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
        assertEquals(0, source.writes + destination.writes);
    }

    @Test void selectedGasCommitConservesSpeciesAndEnergyAndPublishesOnce() {
        var source = source(); var destination = destination();
        var plan = PipeGasTransaction.plan(source, destination, 1, 1, 1, 10000, Set.of(GasType.OXYGEN)).orElseThrow();
        double energy = source.gas.thermalEnergy() + destination.gas.thermalEnergy();
        PipeGasTransaction.commit(source, destination, plan);
        assertEquals(1, source.publications); assertEquals(1, destination.publications);
        assertEquals(2, source.gas.moles(GasType.PLASMA), 0);
        assertEquals(2, source.gas.moles(GasType.PLASMA) + destination.gas.moles(GasType.PLASMA), 1e-12);
        assertEquals(energy, source.gas.thermalEnergy() + destination.gas.thermalEnergy(), 1e-8);
    }

    private static MemoryEndpoint source() { return new MemoryEndpoint(new GasMixture(Map.of(GasType.OXYGEN, 4.0, GasType.PLASMA, 2.0), 800)); }
    private static MemoryEndpoint destination() { return new MemoryEndpoint(new GasMixture(Map.of(GasType.NITROGEN, 3.0), 200)); }
    private static PipeGasTransaction.Plan plan(MemoryEndpoint s, MemoryEndpoint d) {
        return PipeGasTransaction.plan(s, d, 1, 1, 1, 10000, Set.of(GasType.OXYGEN)).orElseThrow();
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0) return false;
        for (GasType type : GasType.values()) if (Double.compare(a.moles(type), b.moles(type)) != 0) return false;
        return true;
    }
}
