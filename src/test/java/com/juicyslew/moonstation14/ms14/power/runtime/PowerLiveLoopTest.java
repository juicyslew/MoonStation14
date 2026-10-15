package com.juicyslew.moonstation14.ms14.power.runtime;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PowerLiveLoopTest {
    @Test void genericApcConsumerSharesProductionDownstreamAllocation() {
        var input = new PowerLiveLoop.Input(List.of(new PowerLiveLoop.Source("src", 1, true, 1_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)),
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(new PowerLiveLoop.ApcConsumer("pa", 3, true, 250)), 20);
        var result = PowerLiveLoop.solve(input);
        assertEquals(100, result.lampWatts().get("lamp"), 1e-9);
        assertEquals(250, result.apcConsumerWatts().get("pa"), 1e-9);
        assertEquals(result.apcOutputWatts().values().stream().mapToDouble(Double::doubleValue).sum(),
                result.lampWatts().values().stream().mapToDouble(Double::doubleValue).sum()
                        + result.apcConsumerWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }
    @Test void isolatedGenericApcComponentHasNoPhantomAllocation() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PowerLiveLoop.ApcConsumer("isolated", 44, true, 250)), 20));
        assertEquals(0, result.apcConsumerWatts().get("isolated"), 1e-9);
    }
    @Test void processingLoadsShareApcTierAllocationAndUnknownComponentsFailClosed() {
        var apc = List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 1_000));
        var one = new PowerLiveLoop.ProcessingLoad("processor", 3, true, 750);
        var full = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), apc, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(one), 20));
        assertEquals(750, full.processingWatts().get("processor"), 1e-9);

        var competing = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), apc, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(one, new PowerLiveLoop.ProcessingLoad("other", 3, true, 750)), 20));
        assertEquals(1_000, competing.processingWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        var unknown = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), apc, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(new PowerLiveLoop.ProcessingLoad("unknown", 3, false, 750)), 20));
        assertEquals(0, unknown.processingWatts().get("unknown"), 1e-9);
    }
    @Test void chemMasterUsesResolvedDemandAndActualApcAllocation() {
        var source = List.of(new PowerLiveLoop.Source("source", 1, true, 1_000));
        var sub = List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true));
        var apc = List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0));
        var machine = new PowerLiveLoop.ChemMasterLoad("chem", 3, true, 250);
        var full = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(machine), 20));
        assertEquals(250, full.chemMasterWatts().get("chem"), 1e-9);
        var unknown = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(new PowerLiveLoop.ChemMasterLoad("chem", 3, false, 250)), 20));
        assertEquals(0, unknown.chemMasterWatts().get("chem"), 1e-9);
        var partial = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(new PowerLiveLoop.Source("small", 1, true, 20)),
                sub, apc, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(machine), 20));
        assertTrue(partial.chemMasterWatts().get("chem") < 250);
        var battery = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("battery-apc", 2, true, 3, true, true, 20_000)), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(new PowerLiveLoop.ChemMasterLoad("battery-chem", 3, true, 250)), 20));
        assertEquals(250, battery.chemMasterWatts().get("battery-chem"), 1e-9);
        assertEquals(20_000 - 250 * 20 / 20.0, battery.batteryEnergyJoules().get("battery-apc"), 1e-9,
                "250 W for the solver's one-second (20 tick) interval withdraws 250 J");
        for (int elapsedTicks : List.of(0, 20, 40)) {
            var elapsed = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                    List.of(new PowerLiveLoop.Apc("elapsed-apc", 2, true, 3, true, true, 20_000)),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new PowerLiveLoop.ChemMasterLoad("elapsed-chem", 3, true, 250)), elapsedTicks));
            assertEquals(20_000 - 250 * (elapsedTicks / 20.0),
                    elapsed.batteryEnergyJoules().get("elapsed-apc"), 1e-9,
                    elapsedTicks + " ticks bill demand only for elapsed seconds");
        }
    }
    @Test void disposalUnitsAreActualFullDemandApcLoads() {
        var src = List.of(new PowerLiveLoop.Source("src", 1, true, 2_000));
        var sub = List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true));
        var apc = List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0));
        var unit = new PowerLiveLoop.DisposalLoad("unit", 3, true, 1_000);
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(src, sub, apc, List.of(), List.of(), List.of(), List.of(), List.of(unit),
                List.of(), List.of(), List.of(), List.of(), 20));
        assertEquals(1_000, result.disposalWatts().get("unit"), 1e-9);
        var unknown = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(), apc, List.of(), List.of(), List.of(), List.of(),
                List.of(new PowerLiveLoop.DisposalLoad("unit", 3, false, 1_000)), List.of(), List.of(), List.of(), List.of(), 20));
        assertEquals(0, unknown.disposalWatts().get("unit"), 1e-9);
    }
    @Test void ventsShareApcAllocationWithLampsAndAreBilledOnlyForElapsedTime() {
        var source = List.of(new PowerLiveLoop.Source("source", 1, true, 1_000));
        var sub = List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true));
        var apc = List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0));
        var lamp = new PowerLiveLoop.Lamp("lamp", 3, true);
        var vent = PowerLiveLoop.VentLoad.defaultUnaryAtmos("vent", 3, true);
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc,
                List.of(lamp), List.of(vent), 20));
        assertEquals(5, result.ventWatts().get("vent"), 1e-9);
        assertEquals(100, result.lampWatts().get("lamp"), 1e-9);

        var shortSupply = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("short-source", 1, true, 1)), sub, apc,
                List.of(lamp), List.of(vent), 20));
        assertTrue(shortSupply.ventWatts().get("vent") > 0 && shortSupply.ventWatts().get("vent") < 5,
                "partial watts are not authorization to run gas simulation");
        assertTrue(shortSupply.ventWatts().get("vent") + shortSupply.lampWatts().get("lamp") < 105);

        var noVent = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc, List.of(lamp), 20));
        assertEquals(100, noVent.lampWatts().get("lamp"), 1e-9);
        assertEquals(0, noVent.ventWatts().size());
        var zeroElapsed = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("battery", 2, true, 3, true, true, 10_000)),
                List.of(), List.of(PowerLiveLoop.VentLoad.defaultUnaryAtmos("vent", 3, true)), 0));
        assertEquals(0, zeroElapsed.ventWatts().get("vent"), 1e-9);
        assertEquals(10_000, zeroElapsed.batteryEnergyJoules().get("battery"), 1e-9);
    }
    @Test void airlockIsAnIndependentTwentyWattContinuousLoad() {
        var source=List.of(new PowerLiveLoop.Source("source",1,true,1_000));
        var sub=List.of(new PowerLiveLoop.Substation("sub",1,true,2,true));
        var apc=List.of(new PowerLiveLoop.Apc("apc",2,true,3,true,true,0));
        var door=new PowerLiveLoop.AirlockLoad("airlock",3,true,20);
        var result=PowerLiveLoop.solve(new PowerLiveLoop.Input(source,sub,apc,List.of(),List.of(),List.of(),List.of(door),List.of(),
                List.of(),List.of(),List.of(),List.of(),20));
        assertEquals(20,result.airlockWatts().get("airlock"),1e-9);
        var shortSupply=PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(new PowerLiveLoop.Source("source2",1,true,10)),sub,
                List.of(new PowerLiveLoop.Apc("apc2",2,true,3,true,true,0)),List.of(),List.of(),List.of(),List.of(new PowerLiveLoop.AirlockLoad("door2",3,true,20)),List.of(),
                List.of(),List.of(),List.of(),List.of(),20));
        assertTrue(shortSupply.airlockWatts().get("door2")<20);
    }

    @Test void ventsFailClosedForUnknownOrOpenPortsAndBatteryDeliveryIsBilledOnce() {
        var vent = PowerLiveLoop.VentLoad.defaultUnaryAtmos("vent", 3, true);
        var open = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(new PowerLiveLoop.Source("source", 1, true, 1_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, false, 10_000)), List.of(), List.of(vent), 20));
        assertEquals(0, open.ventWatts().get("vent"), 1e-9);
        var unknown = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 10_000)), List.of(),
                 List.of(PowerLiveLoop.VentLoad.defaultUnaryAtmos("vent", 3, false)), 20));
        assertEquals(0, unknown.ventWatts().get("vent"), 1e-9);
        var battery = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(), List.of(),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 5_000)), List.of(), List.of(vent), 20));
        assertEquals(5, battery.ventWatts().get("vent"), 1e-9);
        assertEquals(4_995, battery.batteryEnergyJoules().get("apc"), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.VentLoad("bad", 3, true, 5_001));
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.Input(List.of(), List.of(), List.of(),
                List.of(new PowerLiveLoop.Lamp("same", 3, true)),
                List.of(new PowerLiveLoop.VentLoad("same", 3, true, 500)), 20));
    }

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
        assertEquals(5_000, unknown.batteryEnergyJoules().get("open"), 1e-9,
                "known MV input may charge an APC with unknown APC-port status, up to the existing rate limit");
        assertEquals(0, unknown.lampWatts().get("lamp"), 1e-9);
        assertEquals(0, unknown.apcOutputWatts().get("open"), 1e-9);

        var unknownMvPort = new PowerLiveLoop.Apc("open", 2, false, 5, true, false, 0);
        var disconnected = PowerLiveLoop.solve(new PowerLiveLoop.Input(external, substation,
                List.of(unknownMvPort), List.of(lamp), 20));
        assertEquals(0, disconnected.batteryEnergyJoules().get("open"), 1e-9,
                "unknown MV-input status prevents charging even when the APC port is known");
        assertEquals(0, disconnected.lampWatts().get("lamp"), 1e-9);
        assertEquals(0, disconnected.apcOutputWatts().get("open"), 1e-9);
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

    @Test void collectorRatedOfferCanExceedLegacySourceCeilingAcrossSubstations() {
        var source = new PowerLiveLoop.Source("collector", 1, true, 41_300, 41_300);
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(source),
                List.of(new PowerLiveLoop.Substation("sub-a", 1, true, 2, true),
                        new PowerLiveLoop.Substation("sub-b", 1, true, 3, true)),
                List.of(new PowerLiveLoop.Apc("apc-a", 2, true, 4, true, true, 0),
                        new PowerLiveLoop.Apc("apc-b", 3, true, 5, true, true, 0)),
                java.util.stream.IntStream.range(0, 600).mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i,
                        i < 300 ? 4 : 5, true)).toList(), 20));
        assertEquals(41_300, result.substationInputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertTrue(result.substationInputWatts().values().stream().mapToDouble(Double::doubleValue).sum() > 30_000);
        assertEquals(37_170, result.apcInputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(30_000, new PowerLiveLoop.Source("legacy", 1, true, 41_300).watts());
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.Source("bad", 1, true, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.Source("bad", 1, true, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.Source("bad", 1, true, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.Source("bad", 1, true, Double.NEGATIVE_INFINITY));
        assertEquals(0, new PowerLiveLoop.Source("inactive", 1, true, 0, 0).watts());
        assertEquals(0, new PowerRuntimeNode.SupplierOffer(0, 0).ratedMaximumWatts());
        var zeroRated = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("inactive", 1, true, 0, 0),
                        new PowerLiveLoop.Source("zero-rated", 1, true, 1_000, 0)),
                List.of(new PowerLiveLoop.Substation("zero-sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("zero-apc", 2, true, 3, true, true, 0)),
                java.util.stream.IntStream.range(0, 100).mapToObj(i -> new PowerLiveLoop.Lamp("zero-lamp" + i, 3, true)).toList(), 20));
        assertEquals(0, zeroRated.substationInputWatts().get("zero-sub"), 1e-9,
                "an offer cannot exceed its actual zero rated maximum");
    }

    @Test void mvConsumersShareUpstreamAllocationWithApcInputsAndFailClosedWhenUnknown() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("src", 1, true, 10_000, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(
                        new PowerLiveLoop.MvConsumer("pa", 2, true, 6_000),
                        new PowerLiveLoop.MvConsumer("emitter", 2, true, 600)), 20));
        assertEquals(9_000, result.apcInputWatts().get("apc")
                + result.mvConsumerWatts().get("pa") + result.mvConsumerWatts().get("emitter"), 1e-9,
                "one MV solve conserves substation output across every receiver");
        assertTrue(result.mvConsumerWatts().get("pa") > 0);
        assertTrue(result.mvConsumerWatts().get("emitter") > 0);
    }

    @Test void mvConsumerUnderfeedZeroDemandAndUnknownComponentAreReportedWithoutReallocation() {
        var input = new PowerLiveLoop.Input(List.of(new PowerLiveLoop.Source("src", 1, true, 1_000, 1_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(new PowerLiveLoop.MvConsumer("load", 2, true, 1_000),
                        new PowerLiveLoop.MvConsumer("zero", 2, true, 0),
                        new PowerLiveLoop.MvConsumer("unknown", 8, false, 500)), 20);
        var result = PowerLiveLoop.solve(input);
        assertEquals(900, result.apcInputWatts().get("apc") + result.mvConsumerWatts().get("load"), 1e-9);
        assertTrue(result.mvConsumerWatts().get("load") > 0);
        assertEquals(0, result.mvConsumerWatts().get("zero"), 1e-9);
        assertEquals(0, result.mvConsumerWatts().get("unknown"), 1e-9);
        assertEquals(750, result.apcInputWatts().get("apc"), 1e-9,
                "empty APC requests its bounded 5 kW charging headroom in the same proportional MV solve");
        assertEquals(150, result.mvConsumerWatts().get("load"), 1e-9);

        var competing = PowerLiveLoop.solve(new PowerLiveLoop.Input(input.sources(), input.substations(),
                List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0)), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(new PowerLiveLoop.MvConsumer("first", 2, true, 900),
                        new PowerLiveLoop.MvConsumer("last", 2, true, 900)), 20));
        assertEquals(900, competing.mvConsumerWatts().get("first") + competing.mvConsumerWatts().get("last")
                + competing.apcInputWatts().get("apc"), 1e-9);
        assertTrue(competing.mvConsumerWatts().get("first") + competing.mvConsumerWatts().get("last") < 900,
                "the APC shares one proportional allocation; no second pass reallocates its unmet demand");
    }

    @Test void identicalMvConsumerIdsAreRejectedAcrossAllInputLists() {
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("same", 1, true, 1)), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PowerLiveLoop.MvConsumer("same", 2, true, 1)), 20));
        assertThrows(IllegalArgumentException.class, () -> new PowerLiveLoop.MvConsumer("bad", 1, true, Double.NaN));
    }

    @Test void mvComponentsDoNotPoolSubstationOffersAcrossNetworks() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("small", 1, true, 500, 500),
                        new PowerLiveLoop.Source("large", 2, true, 1_500, 1_500)),
                List.of(new PowerLiveLoop.Substation("sub-small", 1, true, 10, true),
                        new PowerLiveLoop.Substation("sub-large", 2, true, 20, true)), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PowerLiveLoop.MvConsumer("consumer-small", 10, true, 1_000),
                        new PowerLiveLoop.MvConsumer("consumer-large", 20, true, 1_000)), 20));
        assertEquals(450, result.mvConsumerWatts().get("consumer-small"), 1e-9);
        assertEquals(1_000, result.mvConsumerWatts().get("consumer-large"), 1e-9,
                "the larger network's offer is isolated, then capped by its own demand");
    }

    @Test void apcRequestsActualReceiverDemandAndBoundedChargeHeadroom() {
        var source = List.of(new PowerLiveLoop.Source("src", 1, true, 2_000));
        var sub = List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true));
        var apc = List.of(new PowerLiveLoop.Apc("apc", 2, true, 3, true, true, 0));
        var actual = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub, apc,
                java.util.stream.IntStream.range(0, 20).mapToObj(i -> new PowerLiveLoop.Lamp("lamp" + i, 3, true)).toList(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PowerLiveLoop.MvConsumer("pa", 2, true, 1_000)), 20));
        assertTrue(actual.mvConsumerWatts().get("pa") > 0,
                "APC's bounded charging headroom cannot claim the unconditional 30 kW port rating");
        assertEquals(1_800, actual.apcInputWatts().get("apc") + actual.mvConsumerWatts().get("pa"), 1e-9);
        assertEquals(0, actual.batteryEnergyJoules().get("apc"), 1e-9);

        var full = PowerLiveLoop.solve(new PowerLiveLoop.Input(source, sub,
                List.of(new PowerLiveLoop.Apc("full", 2, true, 3, true, true, 1_000_000)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 20));
        assertEquals(0, full.apcInputWatts().get("full"), 1e-9);
    }

    @Test void unknownOutputCannotReserveReceiverDemandButKnownInputCanStillCharge() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("src", 1, true, 1_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("isolated", 2, true, 3, false, true, 0)), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                 List.of(new PowerLiveLoop.MvConsumer("pa", 2, true, 600)), 20));
        assertEquals(96.42857142857143, result.mvConsumerWatts().get("pa"), 1e-9);
        assertEquals(803.5714285714286, result.apcInputWatts().get("isolated"), 1e-9);
        assertEquals(900, result.apcInputWatts().get("isolated")
                + result.mvConsumerWatts().get("pa"), 1e-9);
        assertEquals(result.apcInputWatts().get("isolated"), result.batteryEnergyJoules().get("isolated"), 1e-9,
                "unknown APC output must not prohibit charging from its known input");
    }

    @Test void sharedLoadsAreRequestedOnceByEligibleClosedKnownProviders() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("src", 1, true, 2_222.23)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("valid", 2, true, 3, true, true, 1_000_000),
                        new PowerLiveLoop.Apc("open", 2, true, 3, true, false, 1_000_000),
                        new PowerLiveLoop.Apc("unknown", 2, true, 3, false, true, 1_000_000)),
                List.of(new PowerLiveLoop.Lamp("receiver", 3, true)), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PowerLiveLoop.MvConsumer("pa", 2, true, 600),
                        new PowerLiveLoop.MvConsumer("emitter", 2, true, 2_000)), 20));
        assertTrue(result.apcInputWatts().get("valid") > 0,
                "aggregate APC receiver demand is assigned to the sole eligible output provider");
        assertEquals(0, result.apcInputWatts().get("open"), 1e-9);
        assertEquals(0, result.apcInputWatts().get("unknown"), 1e-9);
        assertTrue(result.mvConsumerWatts().get("pa") > 0);
        assertTrue(result.mvConsumerWatts().get("emitter") > 0);
        assertEquals(2_000.007, result.apcInputWatts().values().stream().mapToDouble(Double::doubleValue).sum()
                + result.mvConsumerWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6,
                "downstream MV allocation conserves 90% substation output; lamp delivery is downstream of the APC input");
    }

    @Test void sharedApcDemandIsRequestedOnceAcrossMvBuses() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("srcA", 1, true, 500 / .9),
                        new PowerLiveLoop.Source("srcB", 2, true, 500 / .9)),
                List.of(new PowerLiveLoop.Substation("subA", 1, true, 10, true),
                        new PowerLiveLoop.Substation("subB", 2, true, 20, true)),
                List.of(new PowerLiveLoop.Apc("a", 10, true, 30, true, true, 1_000_000),
                        new PowerLiveLoop.Apc("b", 20, true, 30, true, true, 1_000_000)),
                List.of(new PowerLiveLoop.Lamp("receiver", 30, true)), 20));
        assertEquals(50, result.apcInputWatts().get("a"), 1e-9);
        assertEquals(50, result.apcInputWatts().get("b"), 1e-9);
        assertEquals(100, result.apcInputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(100, result.lampWatts().get("receiver"), 1e-9);
        assertEquals(100, result.apcOutputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertEquals(1_000_000, result.batteryEnergyJoules().get("a"), 1e-9);
        assertEquals(1_000_000, result.batteryEnergyJoules().get("b"), 1e-9);
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
        assertEquals(5_100, input, 1e-9);
        assertEquals(100, delivered, 1e-9);
        assertEquals(input, delivered + storedJoules, 1e-9,
                "one second of MV input is partitioned into delivered watts and stored joules");
    }

    @Test void chargingUsesOnlyEachApcsOwnMvInputAfterSharedLoadContribution() {
        var result = PowerLiveLoop.solve(new PowerLiveLoop.Input(
                List.of(new PowerLiveLoop.Source("source", 1, true, 10_000)),
                List.of(new PowerLiveLoop.Substation("sub", 1, true, 2, true)),
                List.of(new PowerLiveLoop.Apc("a", 2, true, 3, true, true, 1_000_000),
                        new PowerLiveLoop.Apc("b", 4, true, 3, true, true, 0)),
                List.of(new PowerLiveLoop.Lamp("lamp", 3, true)), 20));

        assertEquals(50, result.apcInputWatts().get("a"), 1e-9);
        assertEquals(0, result.apcInputWatts().get("b"), 1e-9);
        assertEquals(0, result.batteryEnergyJoules().get("b"), 1e-9,
                "A's MV input cannot charge B, even though output loads share an APC bus");
        assertEquals(100, result.apcOutputWatts().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9,
                "A's 50 W input and 50 W battery discharge jointly cover the shared lamp load");
        assertEquals(999_950, result.batteryEnergyJoules().get("a"), 1e-9);
        assertEquals(0, result.batteryEnergyJoules().get("b"), 1e-9);
        assertEquals(100, result.apcInputWatts().get("a")
                + (1_000_000 - result.batteryEnergyJoules().get("a")), 1e-9,
                "A's MV input plus its own battery withdrawal equals delivered lamp power");

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
