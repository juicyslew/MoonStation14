package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachinePowerStateTest {
    private static final UUID PLACEMENT = UUID.fromString("9f62c2aa-813b-4424-93c6-12ce78ef5e9d");

    @Test void tegGenerationIsBoundedAndSettledOncePerSolve() {
        var budget = new AtmosMachinePowerState.TegEnergyBudget();
        budget.credit(600, true);
        budget.credit(900, false);
        assertEquals(1000, budget.capWatts(1000, 1000, 12), "short solve uses actual elapsed joule bound");
        assertEquals(600, budget.capWatts(1000, 1000, 20));
        budget.credit(600, true);
        assertEquals(1200, budget.capWatts(2000, 2000, 20), "separate paid commits sum within interval");
        for (long ticks : new long[]{1, 5, 12, 20}) {
            budget.clear(); budget.credit(600, true);
            double bounded = Math.min(2000, 600d / (ticks / 20d));
            assertEquals(bounded, budget.capWatts(2000, 2000, ticks), 1e-9);
            assertEquals(bounded, budget.capWatts(2000, 2000, ticks), 1e-9,
                    "queries do not grant energy");
        }
        budget.clear(); budget.credit(600, true);
        assertTrue(budget.settle(300, 20, true));
        assertEquals(0, budget.pendingJoules(), "surplus is discarded at settlement");
        assertFalse(budget.settle(1, 20, true), "callback cannot spend the interval twice");
        budget.credit(600, true);
        assertFalse(budget.settle(0, 20, false));
        assertEquals(0, budget.pendingJoules(), "unknown callbacks discard budget");
        budget.credit(600, true); budget.clear();
        assertEquals(0, budget.capWatts(2000, 2000, 20), "reset/placement replacement clears old energy");
        budget.credit(600, true);
        assertEquals(400, budget.capWatts(400, 1000, 20), "smoothed upstream offer remains limiting");
        assertEquals(0, budget.capWatts(2000, 2000, 0), "invalid elapsed time fails closed");
        assertEquals(0, budget.capWatts(2000, 2000, 21), "long elapsed time fails closed");
        assertEquals(0, budget.capWatts(Double.NaN, 2000, 20), "nonfinite offer fails closed");
        assertEquals(0, budget.capWatts(1000, 900, 20), "invalid rated bound fails closed");
    }

    @Test void repeatedPaidPublicationsAccumulateOnlyUntilAllocationSettlement() {
        var budget = new AtmosMachinePowerState.TegEnergyBudget();
        // Mirrors separately completed, paid commits under one still-fresh input lease.
        budget.credit(600, true);
        budget.credit(900, true);
        assertEquals(1500, budget.capWatts(2000, 2000, 20));
        assertTrue(budget.settle(1000, 20, true));
        assertEquals(0, budget.capWatts(2000, 2000, 20), "source allocation consumes and discards the interval");
    }

    @Test void pumpFlowEnableDoesNotRemoveItsConfiguredApcReceiverLoad() {
        var profile = AtmosMachineSettingsTest.profile("gas_pressure_pump");
        assertEquals(200, AtmosMachinePowerState.apcDemand(profile, false));
        assertEquals(200, AtmosMachinePowerState.apcDemand(profile, true));

        var thermoProfile = AtmosMachineSettingsTest.profile("gas_thermo_heater");
        assertEquals(0, AtmosMachinePowerState.apcDemand(thermoProfile, false));
        assertEquals(5000, AtmosMachinePowerState.apcDemand(thermoProfile, true));
    }

    @Test void resolvedMixerHasNoReceiverAndNeverCreatesApcDemand() {
        var mixer = AtmosMachineSettingsTest.profile("gas_mixer");
        assertEquals(0, mixer.configuredReceiverLoadWatts(),
                "resolved gas_trinary_base -> pipe bases composition has no power receiver");
        assertNull(mixer.pump());
        assertNotNull(mixer.mixer(), "mixer behavior comes from its resolved component composition");
        assertNull(AtmosMachinePowerState.apcDemand(mixer, false), "no receiver component means no APC demand");
        assertNull(AtmosMachinePowerState.apcDemand(mixer, true), "enabling flow cannot synthesize a receiver");
    }

    @Test void paidIntervalUsesCompletingAllocationAndCompletionTickExactlyOnce() {
        var tracker = new AtmosMachinePowerState.PaidIntervalTracker();
        tracker.accept(PLACEMENT, 500, 500, true, 20);
        assertNull(tracker.consume(20), "first allocation establishes an anchor, not paid elapsed work");
        tracker.accept(PLACEMENT, 800, 500, true, 40);
        var completed = tracker.consume(41);
        assertNotNull(completed);
        assertEquals(20, completed.actualElapsedTicks());
        assertEquals(800, completed.allocationWatts(), "settle with this solve's actual allocation");
        assertEquals(800, completed.actualJoules(), 1e-9);
        assertEquals(40, completed.sampleTick(), "sample tick marks completion, not interval start");
        assertNull(tracker.consume(41), "interval is consumed once");

        var sameTick = new AtmosMachinePowerState.PaidIntervalTracker();
        sameTick.accept(PLACEMENT, 500, 500, true, 20);
        sameTick.accept(PLACEMENT, 500, 500, true, 40);
        assertNotNull(sameTick.consume(40), "consumer may take the interval during its completing callback tick");
    }

    @Test void partialUnknownDuplicateAndLongGapCannotCreateCatchupEnergy() {
        var tracker = new AtmosMachinePowerState.PaidIntervalTracker();
        tracker.accept(PLACEMENT, 500, 500, true, 20);
        tracker.accept(PLACEMENT, 499, 500, true, 40);
        assertNull(tracker.consume(40));
        tracker.accept(PLACEMENT, 500, 500, true, 60);
        assertNull(tracker.consume(60), "the first valid sample after a failure is only a new anchor");
        tracker.accept(PLACEMENT, 700, 500, true, 60);
        assertNull(tracker.consume(60), "duplicate sample ticks cannot accumulate time");
        tracker.accept(PLACEMENT, 500, 500, true, 100);
        assertNull(tracker.consume(100), "a gap longer than one solve cannot credit unloaded time");
        tracker.accept(PLACEMENT, 500, 500, true, 120);
        assertNotNull(tracker.consume(120));

        tracker.accept(PLACEMENT, 500, 500, true, 140);
        tracker.accept(PLACEMENT, 500, 500, false, 160);
        assertNull(tracker.consume(160), "unknown topology clears the interval");
    }

    @Test void completedIntervalExpiresAndTegOffersFailClosedBeforeSolverUse() {
        var tracker = new AtmosMachinePowerState.PaidIntervalTracker();
        tracker.accept(PLACEMENT, 500, 500, true, 20);
        tracker.accept(PLACEMENT, 500, 500, true, 40);
        assertNull(tracker.consume(61), "completion age beyond one solve is stale");

        var positive = new PowerRuntimeNode.SupplierOffer(120, 200);
        assertEquals(positive, AtmosMachinePowerState.offer(true, true, true, true,
                500, 500, 40, 41, positive, 40));
        assertEquals(AtmosMachinePowerState.ZERO_OFFER, AtmosMachinePowerState.offer(true, true, true, true,
                500, 500, 40, 61, positive, 40), "old input/offer is zero before source allocation");
        assertEquals(AtmosMachinePowerState.ZERO_OFFER, AtmosMachinePowerState.offer(true, true, true, true,
                500, 500, 60, 61, positive, 40), "stale positive offer is zero even with a fresh APC allocation");
        assertEquals(AtmosMachinePowerState.ZERO_OFFER, AtmosMachinePowerState.offer(true, true, true, true,
                500, 500, 40, 61, positive, 60), "stale APC allocation blocks an otherwise fresh positive offer");
        assertEquals(AtmosMachinePowerState.ZERO_OFFER, AtmosMachinePowerState.offer(true, true, true, false,
                0, 500, 40, 41, positive, 40));
        assertEquals(AtmosMachinePowerState.ZERO_OFFER, AtmosMachinePowerState.offer(true, false, true, true,
                500, 500, 40, 41, positive, 40));
        assertNull(AtmosMachinePowerState.offer(false, true, true, true,
                500, 500, 40, 41, positive, 40), "non-TEG machines are not supplier roles");
    }
}
