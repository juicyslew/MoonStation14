package com.juicyslew.moonstation14.ms14.atmos.teg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TegRuntimeObservationTest {
    @Test void initialStateHasNoCommittedEventAndLegacyConstructorsDefaultToEmpty() {
        assertNull(TegRuntime.initial().lastCommittedEvent());
        var supply = new TegSupplyPolicy.State(0, 0);
        assertNull(new TegRuntime.State(supply, 0, -1, TegRuntime.Status.IDLE, 0, 0, 0)
                .lastCommittedEvent());
        assertNull(new TegRuntime.State(supply, 0, -1, TegRuntime.Status.IDLE, 0, 0, 0,
                TegRuntime.Observation.still(), TegRuntime.Observation.still()).lastCommittedEvent());
    }

    @Test void noPowerAndOtherStatusTransitionsRetainStaleEventWithoutCallingItFresh() {
        var observation = new TegRuntime.EventObservation(500, 300, 80, 20, 12_345,
                .25, .65, 2, .6, 120L, true, false);
        var state = new TegRuntime.State(new TegSupplyPolicy.State(120, 80), 1_000, 120,
                TegRuntime.Status.PROCESSED, 300, 500, 4, new TegRuntime.Observation(2, 3),
                new TegRuntime.Observation(4, 5), observation);

        var noPower = state.with(TegRuntime.Status.NO_POWER, 125);
        assertEquals(TegRuntime.Status.NO_POWER, noPower.status());
        assertEquals(125, noPower.lastPaidSampleTick());
        assertSame(observation, noPower.lastCommittedEvent());
        assertEquals(120, noPower.lastCommittedEvent().sampleTick());
        assertTrue(noPower.lastCommittedEvent().paidWorkCommitted());
        assertFalse(noPower.lastCommittedEvent().offerPublished());
        assertEquals(1_000, noPower.cumulativeGeneratedJoules());
        assertEquals(0, noPower.lastGeneratedJoules());
        assertEquals(0, noPower.lastGeneratedWatts());
        assertEquals(0, noPower.supply().maximumSupplyWatts());
        assertEquals(0, noPower.supply().rampPositionWatts());

        for (var status : new TegRuntime.Status[]{TegRuntime.Status.DISABLED,
                TegRuntime.Status.SHARED_ROOT_REJECTED, TegRuntime.Status.GAS_COMMIT_REJECTED}) {
            var later = state.with(status, 126);
            assertEquals(status, later.status());
            assertEquals(126, later.lastPaidSampleTick());
            assertSame(observation, later.lastCommittedEvent());
            assertEquals(120, later.lastCommittedEvent().sampleTick());
            assertTrue(later.lastCommittedEvent().paidWorkCommitted());
            assertFalse(later.lastCommittedEvent().offerPublished());
            assertEquals(0, later.lastGeneratedJoules());
            assertEquals(0, later.lastGeneratedWatts());
            assertEquals(0, later.supply().maximumSupplyWatts());
            assertEquals(0, later.supply().rampPositionWatts());
        }
    }

    @Test void acceptedCommitObservationStoresExactKernelAndSettlementValues() {
        var event = new TegRuntime.EventObservation(910.25, 280.5, 43.75, 17.125,
                7_654.5, .31, .65, 2.0, .6, 9876L, true, true);
        assertEquals(910.25, event.temperatureAKelvin());
        assertEquals(280.5, event.temperatureBKelvin());
        assertEquals(43.75, event.heatCapacityAJoulesPerKelvin());
        assertEquals(17.125, event.heatCapacityBJoulesPerKelvin());
        assertEquals(7_654.5, event.maximumWorkJoules());
        assertEquals(.31, event.effectiveEfficiency());
        assertEquals(.65, event.configuredEfficiency());
        assertEquals(2.0, event.powerFactor());
        assertEquals(.6, event.intervalSeconds());
        assertEquals(9876L, event.sampleTick());
        assertTrue(event.paidWorkCommitted());
        assertTrue(event.offerPublished());
    }
}
