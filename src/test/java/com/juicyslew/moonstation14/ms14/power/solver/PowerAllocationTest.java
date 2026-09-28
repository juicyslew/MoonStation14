package com.juicyslew.moonstation14.ms14.power.solver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PowerAllocationTest {
    @Test void shortageIsProportionalAndDeterministic() {
        var first = network(List.of(new PowerAllocation.Source("g", 100, 60)), List.of(
                new PowerAllocation.Load("b", 60), new PowerAllocation.Load("a", 40)), null);
        var result = PowerAllocation.solve(first, 1);
        assertEquals(24, result.deliveredWatts().get("a"), 1e-9);
        assertEquals(36, result.deliveredWatts().get("b"), 1e-9);
        assertEquals(40, result.unmetLoadWatts(), 1e-9);
        assertEquals(0.05, result.elapsedSeconds(), 1e-12);
        assertEquals(result.deliveredWatts(), PowerAllocation.solve(first, 1).deliveredWatts());
    }

    @Test void sourceLimitsAndBatteryChargeDischargeConserveEnergy() {
        var emptyBattery = new PowerAllocation.Storage(0, 100, 100, 100, 0.8, 0.5);
        var charging = PowerAllocation.solve(network(List.of(new PowerAllocation.Source("g", 80, 100)),
                List.of(new PowerAllocation.Load("l", 20)), emptyBattery), 20);
        assertEquals(80, charging.sourceAvailableWatts(), 1e-9);
        assertEquals(20, charging.sourceUsedWatts(), 1e-9);
        assertEquals(60, charging.batteryInputWatts(), 1e-9);
        assertEquals(48, charging.batteryEnergyJoules(), 1e-9);
        assertEquals(0, charging.curtailedWatts(), 1e-9);
        assertEquals(charging.sourceAvailableWatts(), charging.sourceUsedWatts()
                + charging.batteryInputWatts() + charging.curtailedWatts(), 1e-9);

        var charged = new PowerAllocation.Storage(10, 100, 10, 100, 0.8, 0.5);
        var discharging = PowerAllocation.solve(network(List.of(), List.of(new PowerAllocation.Load("l", 80)), charged), 20);
        assertEquals(5, discharging.batteryOutputWatts(), 1e-9);
        assertEquals(5, discharging.deliveredWatts().get("l"), 1e-9);
        assertEquals(0, discharging.batteryEnergyJoules(), 1e-9);
        assertEquals(75, discharging.unmetLoadWatts(), 1e-9);
    }

    @Test void unknownGraphFailsClosedWithoutChangingStorage() {
        var storage = new PowerAllocation.Storage(25, 100, 100, 100, 1, 1);
        var result = PowerAllocation.solve(new PowerAllocation.Network(PowerAllocation.Tier.MV, false,
                List.of(new PowerAllocation.Source("g", 100, 100)), List.of(new PowerAllocation.Load("l", 20)), storage), 20);
        assertFalse(result.graphKnown());
        assertEquals(0, result.sourceAvailableWatts());
        assertEquals(0, result.deliveredWatts().get("l"));
        assertEquals(25, result.batteryEnergyJoules());
    }

    @Test void bridgeRequiresItsExplicitTypedDirectionBreakerAndKnownPorts() {
        var substation = new PowerAllocation.Bridge(PowerAllocation.Tier.HV, PowerAllocation.Tier.MV,
                100, 0.9, true, PowerAllocation.Bridge.Kind.HV_TO_MV_SUBSTATION);
        var transfer = substation.transfer(80, true, true);
        assertEquals(80, transfer.inputWatts(), 1e-9);
        assertEquals(72, transfer.outputWatts(), 1e-9);
        assertEquals(8, transfer.lossWatts(), 1e-9);
        assertEquals(0, substation.transfer(80, true, false).outputWatts());
        assertThrows(IllegalArgumentException.class, () -> new PowerAllocation.Bridge(PowerAllocation.Tier.HV,
                PowerAllocation.Tier.APC, 100, 1, true,
                PowerAllocation.Bridge.Kind.HV_TO_MV_SUBSTATION));

        var apc = new PowerAllocation.Bridge(PowerAllocation.Tier.MV, PowerAllocation.Tier.APC,
                30, 1, false, PowerAllocation.Bridge.Kind.MV_TO_APC_BATTERY_BACKED_APC);
        assertEquals(0, apc.transfer(30, true, true).outputWatts());
    }

    @Test void invalidAndExtremeNumbersAreClamped() {
        var storage = new PowerAllocation.Storage(Double.NaN, Double.POSITIVE_INFINITY,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, 1);
        var result = PowerAllocation.solve(network(List.of(new PowerAllocation.Source("g", Double.NaN,
                Double.POSITIVE_INFINITY)), List.of(new PowerAllocation.Load("l", Double.POSITIVE_INFINITY)), storage), Long.MAX_VALUE);
        assertTrue(Double.isFinite(result.sourceAvailableWatts()));
        assertTrue(Double.isFinite(result.deliveredWatts().get("l")));
        assertTrue(Double.isFinite(result.batteryEnergyJoules()));
        assertTrue(result.batteryEnergyJoules() <= PowerAllocation.MAX_VALUE);
    }

    private static PowerAllocation.Network network(List<PowerAllocation.Source> sources,
                                                   List<PowerAllocation.Load> loads,
                                                   PowerAllocation.Storage storage) {
        return new PowerAllocation.Network(PowerAllocation.Tier.MV, true, sources, loads, storage);
    }
}
