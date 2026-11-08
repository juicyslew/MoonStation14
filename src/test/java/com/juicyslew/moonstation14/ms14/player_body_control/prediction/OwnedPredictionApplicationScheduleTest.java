package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedPredictionApplicationScheduleTest {
    @Test void productionTickDispatcherAdvancesNativeClockOnceAndAnimatesOnlyNewestNewApplication() {
        var history = new GhostPredictionHistory(9, 4, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER);
        var base = snapshot(9, 4, 10).withControlState(
                new com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState(
                        (short) 100, (short) 0, (byte) 0, 0, 0, 0),
                com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED);
        assertEquals(GhostPredictionHistory.SnapshotStatus.REPLAY, history.reconcile(base).status());
        var ticks = history.planReplayTicks(base, 12).ticks();
        assertTrue(ticks.stream().allMatch(tick -> tick.sequence() == 0));
        var clock = new com.juicyslew.moonstation14.ms14.player_body_control.client.ClientOwnedSimulationClock();
        clock.restore(9, 40, 0, 10);
        var schedule = new OwnedPredictionApplicationSchedule();
        int[] calls = {0}, gait = {0};
        var progress = schedule.dispatchTicks(ticks, clock.applicationTick(), -1, true, (tick, animate) -> {
            var interval = clock.prepare(9, tick.logicalTick(), tick.sequence());
            assertNotNull(interval);
            assertTrue(clock.commit(interval, 9, tick.logicalTick(), tick.sequence()));
            calls[0]++;
            if (animate) gait[0]++;
            return true;
        });
        assertEquals(2, calls[0]);
        assertEquals(1, gait[0]);
        assertEquals(12, clock.applicationTick());
        assertEquals(12, progress.consumedThrough());
        var duplicate = schedule.dispatchTicks(ticks, clock.applicationTick(), progress.presentedThrough(),
                true,
                (tick, animate) -> { calls[0]++; return true; });
        assertEquals(2, calls[0]);
        assertEquals(0, duplicate.completed());

        // A correction may replay physical history, but already-presented intervals cannot gait again.
        var corrected = schedule.dispatchTicks(ticks, 10, progress.presentedThrough(), true, (tick, animate) -> {
            calls[0]++;
            if (animate) gait[0]++;
            return true;
        });
        assertEquals(4, calls[0]);
        assertEquals(1, gait[0]);
        assertEquals(12, corrected.consumedThrough());
    }

    @Test void productionQueueDispatchesOnlyNewestExactOwnerSnapshotOnceAfterPoseCopy() {
        var schedule = new OwnedPredictionApplicationSchedule();
        var first = snapshot(9, 4, 10);
        var latest = snapshot(9, 4, 11);
        assertTrue(schedule.queue(first, 9, 4, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER));
        assertTrue(schedule.queue(latest, 9, 4, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER));
        assertFalse(schedule.queue(snapshot(8, 4, 99), 9, 4,
                com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER));
        assertFalse(schedule.queue(snapshot(9, 4, 10), 9, 4,
                com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER));
        assertFalse(schedule.queue(snapshot(9, 5, 12), 9, 4,
                com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER));
        int[] calls = {0};
        assertTrue(schedule.dispatchQueued(applied -> { assertSame(latest, applied); calls[0]++; }));
        assertFalse(schedule.dispatchQueued(applied -> calls[0]++));
        assertEquals(1, calls[0]);
    }

    private static com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot snapshot(
            long epoch, int body, long tick) {
        return new com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot(
                epoch, body, com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind.CHARACTER,
                tick, 0, 0d, 0d, 0d, 0f, 0f, 0d, 0d, 0d, false, 1f, 1f, false, 0,
                com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Snapshot.MovementMode.TRANSIT,
                java.util.Optional.empty(), com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlState.neutral(),
                com.juicyslew.moonstation14.ms14.animals.flight.FlightState.GROUNDED).withInputSettlement(0);
    }
    @Test void packetClumpAppliesOnlyLatestAuthorityOnceAfterVanillaPoseCopy() {
        var schedule = new OwnedPredictionApplicationSchedule();
        double current = 0;
        schedule.defer(true, false); // .228, base 1
        double latestBase = 1;
        schedule.defer(true, false); // another packet before .247, base 2 supersedes it
        latestBase = 2;
        assertEquals(0, current); // network callback never rewrites interpolated pose
        double oldPose = current; // vanilla tick copies old current to previous
        var work = schedule.take();
        assertEquals(OwnedPredictionApplicationSchedule.Work.APPLY_AND_ALLOW_REPLAY, work);
        current = latestBase;
        current += .05; // one normal client-tick replay; no callback replay
        assertEquals(0, oldPose);
        assertEquals(2.05, current, 1e-9);
        assertEquals(OwnedPredictionApplicationSchedule.Work.NONE, schedule.take());

        oldPose = current; // .303 tick; another accepted snapshot was cached at .280
        schedule.defer(true, false);
        current = 3;
        current += .05;
        assertTrue(current - oldPose > 0); // render interpolation has a non-flat interval
    }

    @Test void historyLossStoppedPredictionAndBarriersApplyAuthorityWithoutReplay() {
        var schedule = new OwnedPredictionApplicationSchedule();
        schedule.defer(false, false); // HISTORY_LOST or post-move failure
        assertEquals(OwnedPredictionApplicationSchedule.Work.APPLY_AUTHORITY_ONLY, schedule.take());
        schedule.defer(true, false);
        schedule.clear(); // UNIT/TRANSIT or lifecycle/epoch barrier retains its own immediate policy
        assertEquals(OwnedPredictionApplicationSchedule.Work.NONE, schedule.take());
    }

    @Test void stoppedReplayCandidateIsAuthorityOnlyAndDoesNotRetry() {
        var schedule = new OwnedPredictionApplicationSchedule();
        schedule.defer(true, true); // history says replayable, but a prior post-move failure stopped prediction
        assertEquals(OwnedPredictionApplicationSchedule.Work.APPLY_AUTHORITY_ONLY, schedule.take());
        assertEquals(OwnedPredictionApplicationSchedule.Work.NONE, schedule.take());
    }
}
