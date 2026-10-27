package com.juicyslew.moonstation14.ms14.botany;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FixtureSchedulePauseTest {
    @Test void preexistingPlantRetainsRemainingPollAndCycleDelay() {
        var p=new PlantInstance(50,50,90); p.updatePoll(110);
        long cycle=p.lastCycle(), poll=p.nextUpdate();
        p.resumeFixtureSchedule(100,150);
        assertEquals(cycle+50,p.lastCycle()); assertEquals(poll+50,p.nextUpdate());
    }
    @Test void plantingExactlyAtPauseStartFreezesFullHold() {
        var p=new PlantInstance(50,50,100); p.updatePoll(120);
        p.resumeFixtureSchedule(100,150);
        assertEquals(150,p.lastCycle()); assertEquals(170,p.nextUpdate());
    }
    @Test void replantDuringPauseFreezesOnlyPostPlantElapsedTime() {
        var p=new PlantInstance(50,50,130); p.updatePoll(150);
        p.resumeFixtureSchedule(100,150);
        assertEquals(150,p.lastCycle()); assertEquals(170,p.nextUpdate());
    }
    @Test void negativeElapsedDoesNotMoveScheduleBackwards() {
        var p=new PlantInstance(50,50,100); p.resumeFixtureSchedule(120,110);
        assertEquals(100,p.lastCycle()); assertEquals(100,p.nextUpdate());
    }
}
