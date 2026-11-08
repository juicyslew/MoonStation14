package com.juicyslew.moonstation14.ms14.atmos.machine.thermal;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ThermalMachinePolicyTest {
    private GasMixture gas(double temperature) { return new GasMixture(Map.of(GasType.NITROGEN, 10d), temperature); }

    @Test void heaterUsesPaidEnergyAndStopsAtUpperHysteresisBound() {
        var gas = gas(290);
        var r = GasThermostatPolicy.step(gas, GasThermostatPolicy.Profile.heater(),
                new GasThermostatPolicy.State(293.15, true), 1, 1);
        assertEquals(1, r.electricalEnergyRequiredJoules(), 1e-6);
        assertEquals(0.95, r.gasEnergyDeltaJoules(), Math.ulp((float) gas.thermalEnergy()) * 8.0);
        assertEquals(r.gasAfter().thermalEnergy() - gas.thermalEnergy(), r.gasEnergyDeltaJoules(), 0);
        assertTrue(r.gasAfter().temperatureKelvin() > gas.temperatureKelvin());
        assertTrue(r.stateAfter().on());
        var limit = GasThermostatPolicy.step(gas(294), GasThermostatPolicy.Profile.heater(),
                new GasThermostatPolicy.State(293.15, true), 1, 5000);
        assertEquals(295.15, limit.gasAfter().temperatureKelvin(), 1e-4);
        assertFalse(limit.stateAfter().on());
    }

    @Test void freezerReportsActualBoundedCoolingAndEmptyGasDoesNothing() {
        var r = GasThermostatPolicy.step(gas(80), GasThermostatPolicy.Profile.freezer(),
                new GasThermostatPolicy.State(75, true), 1, 5000);
        assertEquals(73, r.gasAfter().temperatureKelvin(), 1e-4);
        assertEquals(r.gasAfter().thermalEnergy() - gas(80).thermalEnergy(), r.gasEnergyDeltaJoules(), 1e-4);
        assertTrue(r.electricalEnergyRequiredJoules() <= 5000);
        var vacuum = GasMixture.vacuum();
        assertSame(vacuum, GasThermostatPolicy.step(vacuum,
                GasThermostatPolicy.Profile.heater(), new GasThermostatPolicy.State(293, true), 1, 10).gasAfter());
        var max = GasThermostatPolicy.step(gas(262145), GasThermostatPolicy.Profile.heater(),
                new GasThermostatPolicy.State(1_000_000, true), 100, 500_000);
        assertEquals(GasMixture.MAX_TEMPERATURE_KELVIN, max.gasAfter().temperatureKelvin(), Math.ulp((float) GasMixture.MAX_TEMPERATURE_KELVIN));
        assertTrue(max.electricalEnergyRequiredJoules() <= 500_000);
        var min = GasThermostatPolicy.step(gas(1), GasThermostatPolicy.Profile.freezer(),
                new GasThermostatPolicy.State(-100, true), 100, 500_000);
        assertEquals(GasMixture.MIN_TEMPERATURE_KELVIN, min.gasAfter().temperatureKelvin(), Math.ulp((float) GasMixture.MIN_TEMPERATURE_KELVIN));
        assertTrue(min.electricalEnergyRequiredJoules() <= 500_000);
    }

    @Test void exchangerConservesFiniteEnvironmentAndRadiatesVacuumWithoutCreatingGas() {
        var hot = gas(500); var cold = gas(250);
        var finite = PassiveHeatExchangePolicy.exchange(hot, cold, 1, 1, 140, 8000, false);
        assertEquals(hot.totalMoles() + cold.totalMoles(), finite.packetAfter().totalMoles() + finite.environmentAfter().totalMoles(), 1e-5);
        assertEquals(hot.thermalEnergy() + cold.thermalEnergy(), finite.packetAfter().thermalEnergy() + finite.environmentAfter().thermalEnergy(), 0.05);
        assertTrue(finite.packetAfter().temperatureKelvin() < hot.temperatureKelvin());
        var vacuum = PassiveHeatExchangePolicy.exchange(hot, null, 1, 1, 140, 8000, false);
        assertEquals(hot.totalMoles(), vacuum.packetAfter().totalMoles(), 0);
        assertTrue(vacuum.radiatedEnergyJoules() > 0);
        var blocked = PassiveHeatExchangePolicy.exchange(hot, cold, 1, 1, 140, 8000, true);
        assertSame(hot, blocked.packetAfter()); assertSame(cold, blocked.environmentAfter());
        var finiteVacuum = PassiveHeatExchangePolicy.exchange(hot, GasMixture.vacuum(), 1, 1, 140, 8000, true);
        assertSame(hot, finiteVacuum.packetAfter());
        assertEquals(0, finiteVacuum.radiatedEnergyJoules());
        assertEquals(0, PassiveHeatExchangePolicy.exchange(GasMixture.vacuum(), cold, 1, 1, 140, 8000, false).radiatedEnergyJoules());
    }

    @Test void finiteExchangeUsesRepresentablePacketEnergyForOppositeEnvironmentDelta() {
        var tinyHot = new GasMixture(Map.of(GasType.NITROGEN, 0.001), 900);
        var largeCold = new GasMixture(Map.of(GasType.NITROGEN, 1000d), 200);
        var r = PassiveHeatExchangePolicy.exchange(tinyHot, largeCold, 10, 1, 140, 8000, false);
        double before = tinyHot.thermalEnergy() + largeCold.thermalEnergy();
        double after = r.packetAfter().thermalEnergy() + r.environmentAfter().thermalEnergy();
        float stored = (float) before;
        assertEquals(before, after, Math.ulp(stored) * 8.0);
        assertEquals(tinyHot.totalMoles() + largeCold.totalMoles(), r.packetAfter().totalMoles() + r.environmentAfter().totalMoles(), 1e-4);
    }

    @Test void passiveExchangerFlowIsBidirectionalAndRetainsGasAndThermalEnergy() {
        var hotHighPressure = new GasMixture(Map.of(GasType.NITROGEN, 20d), 300, 1);
        var low = new GasMixture(Map.of(GasType.OXYGEN, 2d), 300, 1);
        var forward = PassiveHeatExchangerFlowPolicy.plan(hotHighPressure, low, 1, .6).orElseThrow();
        assertTrue(forward.movedMoles() > 0);
        assertFalse(forward.receivingInlet());
        assertEquals(hotHighPressure.totalMoles() + low.totalMoles(),
                forward.inletAfter().totalMoles() + forward.outletAfter().totalMoles(), 1e-4);
        assertEquals(hotHighPressure.thermalEnergy() + low.thermalEnergy(),
                forward.inletAfter().thermalEnergy() + forward.outletAfter().thermalEnergy(), 0.1);
        var reverse = PassiveHeatExchangerFlowPolicy.plan(low, hotHighPressure, 1, .6).orElseThrow();
        assertTrue(reverse.movedMoles() > 0);
        assertTrue(reverse.receivingInlet());
        assertTrue(PassiveHeatExchangerFlowPolicy.plan(low, low, 1, 1).isEmpty());
        assertTrue(PassiveHeatExchangerFlowPolicy.plan(low, hotHighPressure, 1, 0).isEmpty());
    }

    @Test void exchangerTransfersIntoVacuumAndUsesResolvedConductivity() {
        var gas = new GasMixture(Map.of(GasType.NITROGEN, 10d), 300, 1);
        var empty = new GasMixture(Map.of(), 2.7, 1);
        var transfer = PassiveHeatExchangerFlowPolicy.plan(gas, empty, .5, 1).orElseThrow();
        assertTrue(transfer.movedMoles() > 0);
        assertFalse(transfer.receivingInlet());
        var weaker = PassiveHeatExchangerFlowPolicy.plan(gas, empty, .1, 1).orElseThrow();
        assertTrue(weaker.movedMoles() < transfer.movedMoles());
        assertEquals(gas.totalMoles(), transfer.inletAfter().totalMoles() + transfer.outletAfter().totalMoles(), 1e-4);
    }

    @Test void finiteRadiatorFlowAndPacketExchangeConservePipeEnvironmentEnergy() {
        var hot = new GasMixture(Map.of(GasType.NITROGEN, 10d), 600, .1);
        var vacuum = new GasMixture(Map.of(), 2.7, .1);
        var environment = new GasMixture(Map.of(GasType.NITROGEN, 2d), 250, 1);
        var flow = PassiveHeatExchangerFlowPolicy.plan(hot, vacuum, 1, .6).orElseThrow();
        var exchange = PassiveHeatExchangePolicy.exchange(flow.packet(), environment, .6, 30, 140, 8000, false);
        double packetDelta = exchange.packetAfter().thermalEnergy() - flow.packet().thermalEnergy();
        var inAfter = flow.receivingInlet() ? flow.inletAfter().withEnergyDelta(packetDelta) : flow.inletAfter();
        var outAfter = flow.receivingInlet() ? flow.outletAfter() : flow.outletAfter().withEnergyDelta(packetDelta);
        double pipeDelta = inAfter.thermalEnergy() + outAfter.thermalEnergy() - hot.thermalEnergy() - vacuum.thermalEnergy();
        double envDelta = exchange.environmentAfter().thermalEnergy() - environment.thermalEnergy();
        assertTrue(flow.movedMoles() > 0);
        assertEquals(0, pipeDelta + envDelta, .2, "pipe/environment energy must cancel after packet-only heating");
        assertTrue(pipeDelta < 0 && envDelta > 0);
        assertEquals(hot.totalMoles(), inAfter.totalMoles() + outAfter.totalMoles(), 1e-4);
        assertEquals(environment.gasMoles(), exchange.environmentAfter().gasMoles());
    }
}
