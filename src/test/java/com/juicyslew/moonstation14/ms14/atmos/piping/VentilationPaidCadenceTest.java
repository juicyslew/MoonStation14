package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineCadence;
import com.juicyslew.moonstation14.ms14.power.runtime.VentilationPaidInterval;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class VentilationPaidCadenceTest {
    private record Grant(long tick, double paidSeconds, double paidJoules) { }
    private record Timeline(List<Long> eventTicks, double grantedJoules, double spentJoules,
                            VentilationGasRuntime.DeviceCadence cadence) { }

    private static VentilationPaidInterval.ReceiverSignature signature(Object vent, Object apc,
            long breaker, long generation, long tick) {
        return new VentilationPaidInterval.ReceiverSignature(vent, apc, breaker, generation,
                true, true, true, tick);
    }

    private static Timeline simulate(double intervalSeconds, int endTick, List<Grant> grants) {
        var cadence = new VentilationGasRuntime.DeviceCadence();
        UUID id = UUID.randomUUID();
        Object vent = new Object();
        Object apc = new Object();
        double watts = 5;
        double eventCost = watts * intervalSeconds;
        double maxOldCarry = watts * VentilationGasRuntime.DeviceCadence.MAX_BILLING_QUANTUM_SECONDS + eventCost;
        long previousGrantTick = -1;
        double granted = 0;
        List<Long> events = new ArrayList<>();
        for (long tick = 0; tick <= endTick; tick++) {
            var meter = signature(vent, apc, 1, 2, tick);
            assertTrue(cadence.reconcile(id, meter, 0, intervalSeconds));
            for (Grant grant : grants) if (grant.tick() == tick) {
                boolean accepted = cadence.grant(grant.paidSeconds(), grant.paidJoules(), tick,
                        eventCost, maxOldCarry);
                if (accepted) {
                    granted += grant.paidJoules();
                    previousGrantTick = tick;
                }
            }
            // The accepted-work fixture holds both feed and demand present for the whole timeline.
            boolean feedAndDemandPresent = true;
            if (feedAndDemandPresent && cadence.due(tick, intervalSeconds)
                    && cadence.canWork(tick, tick, eventCost)) {
                assertTrue(cadence.commitWork(tick, tick, eventCost), "accepted gas mutation must debit once");
                cadence.attempted();
                events.add(tick);
            }
        }
        assertTrue(previousGrantTick >= 0);
        return new Timeline(List.copyOf(events), granted, cadence.actualSpentJoules(), cadence);
    }

    private static List<Grant> fullGrants() {
        List<Grant> grants = new ArrayList<>();
        for (int tick = 20; tick <= 360; tick += 20) grants.add(new Grant(tick, 1, 5));
        return grants;
    }

    private static void assertSteadyCadence(Timeline timeline) {
        assertTrue(timeline.eventTicks().size() >= 20, "timeline must contain sustained funded work");
        assertEquals(32L, timeline.eventTicks().getFirst().longValue(), "one paid second anchors before the first .6 s event");
        for (int i = 1; i < timeline.eventTicks().size(); i++)
            assertEquals(12, timeline.eventTicks().get(i) - timeline.eventTicks().get(i - 1),
                    "events remain on a constant twelve-tick grid after startup");
        assertTrue(timeline.spentJoules() <= timeline.grantedJoules() + 1e-9,
                "spent energy cannot exceed accepted actual paid grants");
    }

    @Test void sustainedFullBillingGrantsProduceTwelveTickEventsFor360Ticks() {
        var timeline = simulate(.6, 360, fullGrants());
        assertSteadyCadence(timeline);
        assertEquals(90, timeline.grantedJoules(), 1e-9);
        assertEquals(timeline.eventTicks().size() * 3, timeline.spentJoules(), 1e-9);
    }

    @Test void earlyPartialGrantsThenTwentyTickGrantsStillUseFundingReadyAnchor() {
        List<Grant> grants = new ArrayList<>(List.of(
                new Grant(5, .25, 1.25), new Grant(10, .25, 1.25), new Grant(20, .5, 2.5)));
        for (int tick = 40; tick <= 360; tick += 20) grants.add(new Grant(tick, 1, 5));
        var timeline = simulate(.6, 360, grants);
        assertSteadyCadence(timeline);
        assertEquals(90, timeline.grantedJoules(), 1e-9);
    }

    @Test void continuouslySmallFreshPaidIntervalsDoNotCoalesceEventsOrCreateEnergy() {
        List<Grant> grants = new ArrayList<>();
        for (int tick = 4; tick <= 360; tick += 4) grants.add(new Grant(tick, .2, 1));
        var timeline = simulate(.6, 360, grants);
        assertSteadyCadence(timeline);
        assertEquals(90, timeline.grantedJoules(), 1e-9);
    }

    @Test void fundedNoPlanConsumesOnlyItsEventAndDoesNotRetryEveryTick() {
        var cadence = new VentilationGasRuntime.DeviceCadence();
        UUID id = UUID.randomUUID();
        Object vent = new Object();
        Object apc = new Object();
        assertTrue(cadence.reconcile(id, signature(vent, apc, 1, 2, 0), 0, .6));
        assertTrue(cadence.grant(1, 5, 20, 3, 8));
        assertFalse(cadence.due(20, .6), "funding tick anchors the grid, it is not an event");
        assertFalse(cadence.due(31, .6));
        assertTrue(cadence.due(32, .6));
        assertTrue(cadence.canWork(32, 32, 3));
        cadence.attempted(); // NoPlan: keep credit, consume the event cadence.
        assertFalse(cadence.due(32, .6));
        assertFalse(cadence.due(33, .6));
        assertEquals(5, cadence.remainingJoules());
        assertTrue(cadence.grant(1, 5, 40, 3, 8));
        assertEquals(10, cadence.actualGrantedJoules(), 1e-9);
        assertTrue(cadence.due(44, .6));
        assertTrue(cadence.commitWork(44, 44, 3));
        assertFalse(cadence.commitWork(44, 44, 3), "event authorization is single-spend");
        cadence.attempted();
        assertEquals(7, cadence.remainingJoules(), 1e-9);
        assertEquals(3, cadence.actualSpentJoules(), 1e-9);
    }

    @Test void newMeterSampleDoesNotRefreshAnOldPaidGrantAndDuplicateCannotAdvanceStartup() {
        var cadence = new VentilationGasRuntime.DeviceCadence();
        UUID id = UUID.randomUUID();
        Object vent = new Object();
        Object apc = new Object();
        cadence.reconcile(id, signature(vent, apc, 1, 2, 20), 0, .3);
        assertTrue(cadence.grant(.5, 2.5, 20, 1.5, 6.5));
        assertFalse(cadence.grant(.5, 2.5, 20, 1.5, 6.5), "same grant watermark cannot mint startup time");
        assertEquals(.5, cadence.startupPaidSeconds(), 1e-9);
        cadence.reconcile(id, signature(vent, apc, 1, 2, 40), 0, .3); // fresh meter observation, no paid grant
        assertTrue(cadence.canWork(40, 40, 1.5), "exactly twenty ticks remains fresh");
        assertFalse(cadence.canWork(41, 41, 1.5), "the current power clock expires the grant after twenty ticks");
        assertFalse(cadence.due(41, .3), "meter observation alone never starts the event clock");
    }

    @Test void unaryCarryCapPreservesEarlyActualPaymentsWithoutChangingLegacyFourArgumentPolicy() {
        UUID owner = UUID.randomUUID();
        var legacy = new AtmosMachineCadence.PaidWorkBudget();
        assertTrue(legacy.accept(owner, 4, 0, 3));
        assertTrue(legacy.accept(owner, 1, 20, 3));
        assertEquals(4, legacy.remainingJoules(), 1e-9, "legacy API caps old carry at one event");

        var unary = new AtmosMachineCadence.PaidWorkBudget();
        assertTrue(unary.accept(owner, 4, 0, 3, 8));
        assertTrue(unary.accept(owner, 1, 20, 3, 8));
        assertEquals(5, unary.remainingJoules(), 1e-9, "explicit unary cap retains only previously paid joules");
        assertFalse(unary.accept(owner, 1, 40, 3, Double.POSITIVE_INFINITY));
        assertFalse(unary.accept(owner, 1, 40, 3, Double.NaN));
        assertEquals(0, unary.remainingJoules(), "invalid cap fails closed");
    }

    @Test void receiverSettingsTimingAndUnavailableMeterClearCreditsAndFundingPhase() {
        var cadence = new VentilationGasRuntime.DeviceCadence();
        UUID id = UUID.randomUUID();
        Object vent = new Object();
        Object apc = new Object();
        assertTrue(cadence.reconcile(id, signature(vent, apc, 1, 2, 20), 4, .6));
        assertTrue(cadence.grant(1, 5, 20, 3, 8));
        assertTrue(cadence.reconcile(id, signature(vent, new Object(), 1, 2, 40), 4, .6));
        assertEquals(0, cadence.remainingJoules());
        assertTrue(cadence.grant(1, 5, 40, 3, 8));
        assertTrue(cadence.reconcile(id, signature(vent, apc, 1, 2, 60), 5, .6));
        assertEquals(0, cadence.remainingJoules(), "settings revision resets carry");
        assertTrue(cadence.grant(1, 5, 60, 3, 8));
        assertTrue(cadence.reconcile(id, signature(vent, apc, 1, 2, 80), 5, .3));
        assertEquals(0, cadence.remainingJoules(), "resolved timing change resets carry");
        assertTrue(cadence.grant(1, 5, 80, 1.5, 6.5));
        assertFalse(cadence.reconcile(id, null, 5, .3), "unknown/stale current meter resets state");
        assertEquals(0, cadence.remainingJoules());
        assertFalse(cadence.canWork(81, 81, 1.5));
        assertTrue(cadence.reconcile(id, signature(vent, apc, 2, 2, 82), 5, .3));
        assertTrue(cadence.grant(.3, 1.5, 82, 1.5, 6.5));
        assertTrue(cadence.reconcile(id, signature(vent, apc, 3, 2, 83), 5, .3));
        assertEquals(0, cadence.remainingJoules(), "breaker revision never migrates carry");
        assertTrue(cadence.grant(.3, 1.5, 83, 1.5, 6.5));
        assertTrue(cadence.reconcile(id, signature(vent, apc, 3, 3, 84), 5, .3));
        assertEquals(0, cadence.remainingJoules(), "topology generation never migrates carry");
        cadence.clear(); // chunk unload/removal/quarantine
        assertEquals(0, cadence.remainingJoules());
    }
}
