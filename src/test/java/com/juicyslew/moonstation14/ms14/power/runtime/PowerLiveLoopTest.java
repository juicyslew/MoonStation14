package com.juicyslew.moonstation14.ms14.power.runtime;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PowerLiveLoopTest {
    @Test void debugFixtureUsesTypedDemandAndBatteryToDeliverTwentyFourKilowatts() {
        assertEquals(100, PowerDeviceKind.LAMP.lampDemandWatts());
        assertEquals(12_000, PowerDeviceKind.DEBUG_LOAD_LAMP.lampDemandWatts());
        var source = List.of(new PowerLiveLoop.Source("source", 1, true, PowerRuntime.SOURCE_WATTS));
        var sub = List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true));
        var apc = List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 100_000));
        var one = new PowerLiveLoop.Lamp("one", 3, true, PowerDeviceKind.DEBUG_LOAD_LAMP);
        var two = new PowerLiveLoop.Lamp("two", 3, true, PowerDeviceKind.DEBUG_LOAD_LAMP);
        var both = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc, List.of(one, two), 20));
        assertEquals(22_500, both.apcInputWatts().get("apc"));
        assertEquals(24_000, both.apcOutputWatts().get("apc"));
        assertEquals(98_500, both.batteryEnergyJoules().get("apc"));
        assertEquals(12_000, both.lampWatts().get("one"));
        assertEquals(12_000, both.lampWatts().get("two"));

        var single = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc, List.of(one), 20));
        assertEquals(12_000, single.apcOutputWatts().get("apc"));
        var ordinary = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc,
                List.of(new PowerLiveLoop.Lamp("normal", 3, true)), 20));
        assertEquals(100, ordinary.lampWatts().get("normal"));
    }

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
        assertEquals(9_000, result.apcInputWatts().get("a1")
                + result.apcInputWatts().get("a2"), 1e-9);
        assertEquals(100, result.lampWatts().get("lamp"), 1e-9);
        assertEquals(100, result.apcOutputWatts().get("a1") + result.apcOutputWatts().get("a2"), 1e-9);
        assertEquals(8_900, result.batteryEnergyJoules().get("a1")
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
        assertEquals(0, removed.apcOutputWatts().get("apc"), 1e-9);

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
        assertEquals(0, result.apcOutputWatts().get("open"), 1e-9);

        var charging = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("charging", 2, true, 5, true, true, 0)), List.of(), 20));
        assertEquals(0, charging.batteryEnergyJoules().get("charging"), 1e-9);
        var fed = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(new PowerLiveLoop.Source("source", 1, true, 1_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("charging", 2, true, 5, true, true, 0)), List.of(), 20));
        assertEquals(900, fed.batteryEnergyJoules().get("charging"), 1e-9);
    }

    @Test void openBreakerCanChargeFromKnownGridInputButNeverDischargesOrPowersLamps() {
        var external = List.of(new PowerLiveLoop.Source("source", 1, true, 10_000));
        var substation = List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true));
        var open = new PowerLiveLoop.Apc("open", 2, true, 5, true, false, 0);
        var lamp = new PowerLiveLoop.Lamp("lamp", 5, true);

        var fed = PowerLiveLoop.solve(new PowerLiveLoop.Input(external, substation,
                List.of(open), List.of(lamp), 20));
        assertEquals(0, fed.lampWatts().get("lamp"), 1e-9);
        assertEquals(5_000, fed.batteryEnergyJoules().get("open"), 1e-9,
                "charging uses external power but remains bounded by the existing battery rate");

        var noGrid = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), substation,
                List.of(open), List.of(lamp), 20));
        assertEquals(0, noGrid.batteryEnergyJoules().get("open"), 1e-9);
        var isolatedBattery = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("open", 2, true, 5, true, false, 10_000)),
                List.of(lamp), 20));
        assertEquals(0, isolatedBattery.lampWatts().get("lamp"), 1e-9);
        assertEquals(10_000, isolatedBattery.batteryEnergyJoules().get("open"), 1e-9);

        var unknownApcPort = new PowerLiveLoop.Apc("open", 2, true, 5, false, false, 0);
        var unknown = PowerLiveLoop.solve(new PowerLiveLoop.Input(external, substation,
                List.of(unknownApcPort), List.of(lamp), 20));
        assertEquals(0, unknown.batteryEnergyJoules().get("open"), 1e-9);
        assertEquals(0, unknown.lampWatts().get("lamp"), 1e-9);
        assertEquals(0, unknown.apcOutputWatts().get("open"), 1e-9);

        var unknownMvPort = new PowerLiveLoop.Apc("open", 2, false, 5, true, false, 0);
        var disconnected = PowerLiveLoop.solve(new PowerLiveLoop.Input(external, substation,
                List.of(unknownMvPort), List.of(lamp), 20));
        assertEquals(0, disconnected.batteryEnergyJoules().get("open"), 1e-9);
    }

    @Test void knownInputPassesThroughAboveOldApcCapAndMetersActualDelivery() {
        var lamps = java.util.stream.IntStream.range(0, 90)
                .mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList();
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)), lamps, 20));

        assertEquals(9_000, result.apcInputWatts().get("apc"), 1e-9);
        assertEquals(9_000, result.apcOutputWatts().get("apc"), 1e-9);
        assertEquals(9_000, result.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertTrue(result.apcOutputWatts().get("apc") > 2_000);
        assertEquals(0, result.batteryEnergyJoules().get("apc"), 1e-9,
                "input spent on delivered loads is not also counted as charge");
    }

    @Test void batteryOnlyAndMixedInputOutputMetersCountDeliveryOnce() {
        var lamps = java.util.stream.IntStream.range(0, 15)
                .mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList();
        var substation = List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true));
        var batteryOnly = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("battery", 2, true, 3, true, true, 10_000)), lamps, 20));
        assertEquals(1_500, batteryOnly.apcOutputWatts().get("battery"), 1e-9);
        assertEquals(8_500, batteryOnly.batteryEnergyJoules().get("battery"), 1e-9);

        var mixed = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 1_000)), substation,
                List.of(new PowerLiveLoop.Apc("mixed", 2, true, 3, true, true, 10_000)), lamps, 20));
        assertEquals(1_500, mixed.apcOutputWatts().get("mixed"), 1e-9);
        assertEquals(9_400, mixed.batteryEnergyJoules().get("mixed"), 1e-9);
        assertEquals(1_500, mixed.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test void sharedApcTierOutputIsAttributedDeterministicallyPerApc() {
        var lamps = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList();
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub-a", 1, true, 2, true),
                        new PowerLiveLoop.Substation("sub-b", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("a", 2, true, 3, true, true, 0),
                        new PowerLiveLoop.Apc("b", 2, true, 3, true, true, 0)), lamps, 20));

        assertEquals(9_000, result.apcOutputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(4_500, result.apcOutputWatts().get("a"), 1e-9);
        assertEquals(4_500, result.apcOutputWatts().get("b"), 1e-9);
        assertEquals(9_000, result.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test void sharedBatterySupplementIsAttributedByStoredEnergyWithoutDuplicatingComponentTotal() {
        var lamps = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList();
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("a", 2, true, 3, true, true, 10_000),
                        new PowerLiveLoop.Apc("b", 2, true, 3, true, true, 20_000)), lamps, 20));

        assertEquals(3_000, result.apcOutputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(1_000, result.apcOutputWatts().get("a"), 1e-9);
        assertEquals(2_000, result.apcOutputWatts().get("b"), 1e-9);
        assertEquals(3_000, result.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test void demandDrivenPassthroughCanCrossTripThresholdButLowSupplyCannot() {
        var lamps = java.util.stream.IntStream.range(0, 225)
                .mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList();
        var powered = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 25_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)), lamps, 20));
        assertEquals(22_500, powered.apcInputWatts().get("apc"), 1e-9);
        assertEquals(22_500, powered.apcOutputWatts().get("apc"), 1e-9);
        assertTrue(powered.apcOutputWatts().get("apc") > 20_000);
        assertEquals(22_500, powered.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);

        var underfed = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)), lamps, 20));
        assertEquals(9_000, underfed.apcOutputWatts().get("apc"), 1e-9,
                "9 kW external input and an empty battery remain below the threshold");
        assertTrue(underfed.apcOutputWatts().get("apc") < 20_000);
    }

    @Test void batterySupplementCanCrossThresholdAndTwentyTickEnergyIsConserved() {
        var lamps = java.util.stream.IntStream.range(0, 215)
                .mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList();
        var supplemented = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 22_223)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 50_000)), lamps, 20));
        assertEquals(20_000.7, supplemented.apcInputWatts().get("apc"), 1e-9);
        assertEquals(21_500, supplemented.apcOutputWatts().get("apc"), 1e-9);
        assertEquals(48_500.7, supplemented.batteryEnergyJoules().get("apc"), 1e-9);

        var charging = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)),
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)), 20));
        double input = charging.apcInputWatts().get("apc");
        double delivered = charging.apcOutputWatts().get("apc");
        double storedJoules = charging.batteryEnergyJoules().get("apc");
        assertEquals(5_000, storedJoules, 1e-9);
        assertEquals(9_000, input, 1e-9);
        assertEquals(100, delivered, 1e-9);
        assertEquals(input, delivered + storedJoules + 3_900, 1e-9,
                "one second of source input is partitioned into delivered watts, stored joules, and curtailed watts");
    }

    @Test void chargingUsesOnlyEachApcsOwnMvInputAfterSharedLoadContribution() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("a", 2, true, 3, true, true, 1_000_000),
                        new PowerLiveLoop.Apc("b", 4, true, 3, true, true, 0)),
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)), 20));

        assertEquals(9_000, result.apcInputWatts().get("a"), 1e-9);
        assertEquals(0, result.apcInputWatts().get("b"), 1e-9);
        assertEquals(0, result.batteryEnergyJoules().get("b"), 1e-9,
                "A's MV input cannot charge B, even though output loads share an APC bus");
        assertEquals(100, result.apcOutputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);

        var openB = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("a", 2, true, 3, true, true, 1_000_000),
                        new PowerLiveLoop.Apc("b", 4, true, 3, true, false, 0)),
                List.of(), 20));
        assertEquals(0, openB.batteryEnergyJoules().get("b"), 1e-9,
                "open APC with no own MV allocation cannot charge from its neighbor");
    }

    @Test void sharedBatteryMeterMatchesActualPerApcWithdrawalAndDischargeCap() {
        var lamps = java.util.stream.IntStream.range(0, 215)
                .mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList();
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 1_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("a", 2, true, 3, true, true, 0),
                        new PowerLiveLoop.Apc("b", 4, true, 3, true, true, 50_000)), lamps, 20));

        assertEquals(900, result.apcInputWatts().get("a"), 1e-9);
        assertEquals(0, result.apcInputWatts().get("b"), 1e-9);
        assertEquals(10_000, result.apcOutputWatts().get("b"), 1e-9);
        assertEquals(40_000, result.batteryEnergyJoules().get("b"), 1e-9);
        assertEquals(10_900, result.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(result.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum(),
                result.apcOutputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(10_900,
                result.apcInputWatts().values().stream().mapToDouble(Double::doubleValue).sum()
                        + (50_000 - result.batteryEnergyJoules().get("b")), 1e-9,
                "shared delivery is bounded by MV input plus the exact stored-energy withdrawal");
    }
}
