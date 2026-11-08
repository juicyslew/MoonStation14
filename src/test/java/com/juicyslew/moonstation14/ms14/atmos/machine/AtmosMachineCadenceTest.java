package com.juicyslew.moonstation14.ms14.atmos.machine;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineCadenceTest {
    @Test void cadenceUsesResolvedIntervalAndDoesNotRepeatTick() {
        var clock = new AtmosMachineCadence.Clock();
        var settings = new AtmosMachineCadence.Settings(.6, 8, 30);
        assertFalse(clock.due(100, settings));
        int events = 0;
        for (int tick = 101; tick <= 160; tick++) {
            if (clock.due(tick, settings)) { events++; clock.completed(); }
            assertFalse(clock.due(tick, settings));
        }
        assertEquals(5, events);
        assertEquals(10, new AtmosMachineCadence.Settings(.5, 8, 30).intervalTicks());
    }

    @Test void longIntervalOverloadSharesTickRoundingAndPendingGrid() {
        var clock = new AtmosMachineCadence.Clock();
        assertEquals(6, AtmosMachineCadence.intervalTicks(.3));
        assertFalse(clock.due(50, 6));
        assertTrue(clock.due(56, 6));
        assertTrue(clock.due(57, 6));
        clock.completed();
        assertFalse(clock.due(57, 6));
    }

    @Test void missedFramesCollapseToOnePendingEventUntilCompleted() {
        var clock = new AtmosMachineCadence.Clock();
        var settings = new AtmosMachineCadence.Settings(.6, 8, 30);
        assertFalse(clock.due(0, settings));
        assertTrue(clock.due(60, settings));
        assertTrue(clock.due(61, settings));
        clock.completed();
        assertFalse(clock.due(61, settings));
        assertFalse(clock.due(62, settings));
    }

    @Test void fundedAttemptCompletesCadenceWithoutCommittingWork() {
        var clock = new AtmosMachineCadence.Clock();
        var settings = new AtmosMachineCadence.Settings(.6, 8, 30);
        assertFalse(clock.due(0, settings));
        assertTrue(clock.due(12, settings));
        // A funded NoPlan/UNCHANGED/rejected attempt consumes its event cadence only.
        clock.completed();
        assertFalse(clock.due(12, settings));
        assertFalse(clock.due(13, settings));
        assertTrue(clock.due(24, settings));
    }

    @Test void unfundedPendingEventRemainsDueUntilAttemptCanRun() {
        var clock = new AtmosMachineCadence.Clock();
        var settings = new AtmosMachineCadence.Settings(.6, 8, 30);
        assertFalse(clock.due(0, settings));
        assertTrue(clock.due(12, settings));
        assertTrue(clock.due(13, settings));
        clock.completed();
        assertFalse(clock.due(13, settings));
        assertTrue(clock.due(24, settings));
    }

    @Test void paidBudgetCarriesAtMostOneEventAndClearsOnUnavailableOrIdentityChange() {
        var budget = new AtmosMachineCadence.PaidWorkBudget();
        var id = UUID.randomUUID();
        budget.accept(id, 1000, 20, 600);
        assertTrue(budget.commitWork(id, 12, 20, 600));
        assertFalse(budget.commitWork(id, 12, 20, 600));
        budget.accept(id, 1000, 40, 600);
        assertTrue(budget.commitWork(id, 24, 40, 600));
        assertTrue(budget.remainingJoules() <= 1000);
        assertFalse(budget.canWork(id, 25, 61, 600));
        budget.unavailable();
        assertEquals(0, budget.remainingJoules());
        budget.accept(id, 1000, 40, 600);
        assertEquals(0, budget.remainingJoules(), "clearing credits must not reopen a consumed grant");
        budget.accept(UUID.randomUUID(), 500, 80, 600);
        assertEquals(500, budget.remainingJoules());
    }

    @Test void oneKilowattGrantsFundFiveCadenceEventsPerSteadyThreeSeconds() {
        var clock = new AtmosMachineCadence.Clock();
        var budget = new AtmosMachineCadence.PaidWorkBudget();
        var id = UUID.randomUUID();
        var settings = new AtmosMachineCadence.Settings(.6, 8, 30);
        int total = 0;
        int window = 0;
        long lastGrant = -1;
        for (int tick = 0; tick <= 240; tick++) {
            if (tick > 0 && tick % 20 == 0) {
                budget.accept(id, 1000, tick, 600);
                lastGrant = tick;
            }
            if (clock.due(tick, settings) && budget.canWork(id, tick, lastGrant, 600)) {
                assertTrue(budget.commitWork(id, tick, lastGrant, 600));
                clock.completed();
                total++;
                if (tick > 120 && tick <= 180) window++;
            }
        }
        assertEquals(5, window, "steady 60-tick window must complete five 0.6-second events");
        assertTrue(total * 600 <= 12_000, "completed work cannot exceed 12 seconds of 1 kW allocations");
    }

    @Test void paidIntervalTrackerAndMachineBudgetCompleteFiveDistinctEventsPerSteadyThreeSeconds() {
        var tracker = new AtmosMachinePowerState.PaidIntervalTracker();
        var clock = new AtmosMachineCadence.Clock();
        var budget = new AtmosMachineCadence.PaidWorkBudget();
        var id = UUID.randomUUID();
        var settings = new AtmosMachineCadence.Settings(.6, 8, 30);
        int events = 0;
        int windowEvents = 0;
        double paidJoules = 0;
        double consumedJoules = 0;
        for (int tick = 0; tick <= 240; tick++) {
            if (tick % 20 == 0) {
                tracker.accept(id, 1000, 1000, true, tick);
                var completed = tracker.consume(tick);
                if (completed != null) {
                    paidJoules += completed.actualJoules();
                    AtmosMachineBlockEntity.acceptPaidInterval(budget, id,
                            new AtmosMachineBlockEntity.PaidPowerInterval(completed.placementIdentity(),
                                    completed.actualElapsedTicks(), completed.actualJoules(),
                                    completed.allocationWatts(), completed.requestedWatts(), completed.sampleTick()), 600);
                    // A repeated completion callback cannot mint the same grant twice.
                    tracker.accept(id, 1000, 1000, true, tick);
                    assertNull(tracker.consume(tick));
                    AtmosMachineBlockEntity.acceptPaidInterval(budget, id,
                            new AtmosMachineBlockEntity.PaidPowerInterval(completed.placementIdentity(),
                                    completed.actualElapsedTicks(), completed.actualJoules(),
                                    completed.allocationWatts(), completed.requestedWatts(), completed.sampleTick()), 600);
                }
            }
            boolean due = clock.due(tick, settings);
            if (due && budget.canWork(id, tick, tick - tick % 20, 600)) {
                assertTrue(budget.commitWork(id, tick, tick - tick % 20, 600));
                assertFalse(budget.commitWork(id, tick, tick - tick % 20, 600), "event cannot be debited twice");
                clock.completed();
                events++;
                consumedJoules += 600;
                if (tick > 120 && tick <= 180) windowEvents++;
            }
        }
        assertEquals(5, windowEvents);
        assertTrue(consumedJoules <= paidJoules);
        assertTrue(events > 0);
    }
}
