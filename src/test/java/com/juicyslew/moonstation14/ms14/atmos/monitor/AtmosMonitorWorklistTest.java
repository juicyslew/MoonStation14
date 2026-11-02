package com.juicyslew.moonstation14.ms14.atmos.monitor;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMonitorWorklistTest {
    private static final class FakeDevice implements AtmosMonitorWorklist.Device {
        final UUID id;
        boolean loaded = true, paid = true, configuredEnabled;
        final List<Long> sampledCycles = new ArrayList<>();
        FakeDevice(UUID id) { this.id = id; }
        public UUID identity() { return id; }
        public boolean available() { return loaded; }
        public boolean paidAndPowered() { return paid; }
        public void sample(long cycle) { sampledCycles.add(cycle); }
    }

    @Test void twoLoadedDevicesAreBoundedAndEventuallySampleOncePerAtmosphereCycle() {
        var work = new AtmosMonitorWorklist();
        var firstPos = new BlockPos(1, 64, 0); var secondPos = new BlockPos(2, 64, 0);
        var first = new FakeDevice(UUID.randomUUID()); var second = new FakeDevice(UUID.randomUUID());
        work.register(firstPos, first.id); work.register(secondPos, second.id);
        var devices = java.util.Map.of(firstPos, first, secondPos, second);
        var firstSlice = work.drain(11, 1, () -> false, registration -> devices.get(registration.position()));
        assertEquals(new AtmosMonitorWorklist.DrainResult(1, 1), firstSlice);
        assertEquals(1, first.sampledCycles.size() + second.sampledCycles.size());
        var secondSlice = work.drain(11, 1, () -> false, registration -> devices.get(registration.position()));
        assertEquals(new AtmosMonitorWorklist.DrainResult(1, 1), secondSlice);
        assertEquals(2, first.sampledCycles.size() + second.sampledCycles.size());
        var duplicateCycle = work.drain(11, 2, () -> false, registration -> devices.get(registration.position()));
        assertEquals(0, duplicateCycle.sampled());
        var nextCycle = work.drain(12, 2, () -> false, registration -> devices.get(registration.position()));
        assertEquals(2, nextCycle.sampled());
        assertEquals(List.of(11L, 12L), first.sampledCycles);
        assertEquals(List.of(11L, 12L), second.sampledCycles);
    }

    @Test void replacementAndUnloadIdentityAreSkippedAndRemovedFromDerivedQueue() {
        var work = new AtmosMonitorWorklist();
        var pos = new BlockPos(3, 64, 0);
        var old = new FakeDevice(UUID.randomUUID()); var replacement = new FakeDevice(UUID.randomUUID());
        work.register(pos, old.id);
        work.register(pos, replacement.id);
        var result = work.drain(1, 2, () -> false, r -> r.identity().equals(replacement.id) ? replacement : old);
        assertEquals(1, result.sampled());
        assertTrue(old.sampledCycles.isEmpty());
        assertEquals(List.of(1L), replacement.sampledCycles);
        work.unregister(pos, replacement.id);
        assertEquals(0, work.size());
        assertEquals(0, work.drain(2, 8, () -> false, r -> replacement).sampled());
    }

    @Test void stageUsesFreshPowerGateRegardlessOfConfigurationAndDoesNotOwnGasGrant() {
        var work = new AtmosMonitorWorklist();
        var pos = new BlockPos(4, 64, 0); var vent = new FakeDevice(UUID.randomUUID());
        vent.configuredEnabled = false; // Monitor processing is not gated by gas configuration.
        work.register(pos, vent.id);
        vent.paid = false;
        assertEquals(0, work.drain(1, 8, () -> false, r -> vent).sampled());
        assertTrue(vent.sampledCycles.isEmpty());
        vent.paid = true;
        assertEquals(1, work.drain(2, 8, () -> false, r -> vent).sampled());
        assertEquals(List.of(2L), vent.sampledCycles);
        // The scheduler delegate receives only the paid/power observation and cycle identity; it has no gas-work grant API.
    }

    @Test void staleReplacementEntryDoesNotConsumeUnboundedStageBudget() {
        var work = new AtmosMonitorWorklist();
        var pos = new BlockPos(5, 64, 0); var old = new FakeDevice(UUID.randomUUID()); var current = new FakeDevice(UUID.randomUUID());
        work.register(pos, old.id); work.register(pos, current.id);
        var first = work.drain(5, 1, () -> false, r -> r.identity().equals(current.id) ? current : old);
        assertEquals(1, first.inspected());
        assertTrue(old.sampledCycles.isEmpty());
        var second = work.drain(5, 1, () -> false, r -> r.identity().equals(current.id) ? current : old);
        assertEquals(1, second.inspected());
        assertEquals(List.of(5L), current.sampledCycles);
    }
}
