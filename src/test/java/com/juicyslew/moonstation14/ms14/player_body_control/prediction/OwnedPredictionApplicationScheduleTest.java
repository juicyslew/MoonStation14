package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnedPredictionApplicationScheduleTest {
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
