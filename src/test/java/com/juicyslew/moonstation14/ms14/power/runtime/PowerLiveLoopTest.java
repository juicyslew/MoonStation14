package com.juicyslew.moonstation14.ms14.power.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PowerLiveLoopTest {
    @Test void aggregateSourcesSubstationsAndApCsDoNotDuplicateSharedComponentPower() {
        var input = new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("s1", 1, true, 5_000),
                        new PowerLiveLoop.Source("s2", 1, true, 5_000)),
                List.of(new PowerLiveLoop.Substation("x1", 1, true, 2, true),
                        new PowerLiveLoop.Substation("x2", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("a1", 2, true, 3, true, true, 0),
                        new PowerLiveLoop.Apc("a2", 2, true, 3, true, true, 0)),
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)), 20);

        var result = PowerLiveLoop.solve(input);
        assertEquals(10_000, result.substationInputWatts().get("x1")
                + result.substationInputWatts().get("x2"), 1e-9);
        assertEquals(4_000, result.apcInputWatts().get("a1")
                + result.apcInputWatts().get("a2"), 1e-9);
        assertEquals(100, result.lampWatts().get("lamp"), 1e-9);
        assertEquals(2_000, result.batteryEnergyJoules().get("a1")
                + result.batteryEnergyJoules().get("a2"), 1e-9);
    }

    @Test void sourceRemovalAndUnknownOrCutPortsFailClosedWithoutCrossTierReuse() {
        var sub = new PowerLiveLoop.Substation("sub", 4, true, 8, true);
        var apc = new PowerLiveLoop.Apc("apc", 8, true, 12, true, true, 0);
        var lamp = new PowerLiveLoop.Lamp("lamp", 12, true);
        var source = new PowerLiveLoop.Source("source", 4, true, 10_000);
        var powered = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(source), List.of(sub), List.of(apc), List.of(lamp), 20));
        assertEquals(100, powered.lampWatts().get("lamp"), 1e-9);

        var removed = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(sub), List.of(apc), List.of(lamp), 20));
        assertEquals(0, removed.apcInputWatts().get("apc"), 1e-9);
        assertEquals(0, removed.lampWatts().get("lamp"), 1e-9);

        var cut = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(source),
                List.of(new PowerLiveLoop.Substation("sub", 4, false, 8, true)), List.of(apc), List.of(lamp), 20));
        assertEquals(0, cut.lampWatts().get("lamp"), 1e-9);

        var wrongTier = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 12, true, 10_000)), List.of(sub), List.of(apc), List.of(lamp), 20));
        assertEquals(0, wrongTier.lampWatts().get("lamp"), 1e-9);
    }

    @Test void breakerGatesOutputAndBatteryPolicyPersistsOneAggregateUpdatePerApc() {
        var noGrid = new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("closed", 2, true, 3, true, true, 10_000),
                        new PowerLiveLoop.Apc("open", 2, true, 3, true, false, 10_000)),
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)), 20);
        var result = PowerLiveLoop.solve(noGrid);
        assertEquals(100, result.lampWatts().get("lamp"), 1e-9);
        assertEquals(9_900, result.batteryEnergyJoules().get("closed"), 1e-9);
        assertEquals(10_000, result.batteryEnergyJoules().get("open"), 1e-9);

        var charging = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("charging", 2, true, 5, true, true, 0)), List.of(), 20));
        assertEquals(0, charging.batteryEnergyJoules().get("charging"), 1e-9);
        var fed = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(new PowerLiveLoop.Source("source", 1, true, 1_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("charging", 2, true, 5, true, true, 0)), List.of(), 20));
        assertEquals(900, fed.batteryEnergyJoules().get("charging"), 1e-9);
    }
}
