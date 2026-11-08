package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.atmos.machine.thermal.PassiveHeatExchangerRuntime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosDeviceRuntimeTest {
    @Test void disableClearsOnlyAcceptedTransferObservation() {
        var state = new AtmosDeviceRuntime.State(12, 24, false, AtmosDeviceRuntime.WorkStatus.COMMITTED,
                false, 2.5, 10, 3);
        var cleared = AtmosDeviceRuntime.clearAcceptedTransfer(state);
        assertEquals(0, cleared.lastMoles());
        assertEquals(12, cleared.lastPassiveWorldTick());
        assertEquals(10, cleared.lastThermalJoules());
        assertEquals(3, cleared.lastActualPaidInputJoules());
    }
    @Test void passiveEqualizationPreservesAcceptedTransferObservation() {
        var accepted = new AtmosDeviceRuntime.State(1, 2, false, AtmosDeviceRuntime.WorkStatus.COMMITTED,
                false, 2.5, 0, 0);
        var passive = AtmosDeviceRuntime.passiveEqualizationState(accepted, 12, 2, true, 0);
        assertEquals(AtmosDeviceRuntime.WorkStatus.PASSIVE_EQUALIZATION, passive.lastWorkStatus());
        assertEquals(2.5, passive.lastMoles());
    }
    @Test void passiveExchangerRuntimeReportsItsOwnCommittedPipeWorkAndNoElectricalInput() {
        var previous = AtmosDeviceRuntime.State.initial();
        var result = new PassiveHeatExchangerRuntime.Result(
                PassiveHeatExchangerRuntime.Status.COMMITTED, 2.5, -450, 300, 150);
        var state = AtmosDeviceRuntime.passiveExchangerState(previous, previous.lastPassiveWorldTick(), 82, result);

        assertEquals(AtmosDeviceRuntime.WorkStatus.COMMITTED, state.lastWorkStatus());
        assertEquals(2.5, state.lastMoles());
        assertEquals(-450, state.lastThermalJoules());
        assertEquals(0, state.lastActualPaidInputJoules());
        assertEquals(82, state.lastUnpoweredProcessWorldTick());
        assertEquals(-1, state.lastPassiveWorldTick());
        assertTrue(state.lastUnpoweredProcessWorldTick() > state.lastPassiveWorldTick());
    }

    @Test void passiveExchangerStatusesMapWithoutInventingWork() {
        var previous = AtmosDeviceRuntime.State.initial();
        for (var status : new PassiveHeatExchangerRuntime.Status[]{
                PassiveHeatExchangerRuntime.Status.UNCHANGED,
                PassiveHeatExchangerRuntime.Status.STALE,
                PassiveHeatExchangerRuntime.Status.REJECTED}) {
            var result = new PassiveHeatExchangerRuntime.Result(status, 0, 0, 0, 0);
            var state = AtmosDeviceRuntime.passiveExchangerState(previous, 0, 12, result);
            assertEquals(AtmosDeviceRuntime.WorkStatus.valueOf(status.name()), state.lastWorkStatus());
            assertEquals(0, state.lastMoles());
            assertEquals(0, state.lastActualPaidInputJoules());
        }
    }
}
