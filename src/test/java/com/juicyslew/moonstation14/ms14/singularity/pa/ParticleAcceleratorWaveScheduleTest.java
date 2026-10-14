package com.juicyslew.moonstation14.ms14.singularity.pa;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParticleAcceleratorWaveScheduleTest {
    @Test void delaysFirstWaveAndUsesProfileInterval() {
        var schedule=ParticleAcceleratorWaveSchedule.IDLE;
        var first=schedule.advance(true,100,120);
        assertFalse(first.fire());
        assertEquals(220,first.schedule().nextWaveTick());
        assertFalse(first.schedule().advance(true,219,120).fire());
        var wave=first.schedule().advance(true,220,120);
        assertTrue(wave.fire());
        assertEquals(340,wave.schedule().nextWaveTick());
    }
    @Test void losesEligibilityAndStartsFreshAfterward() {
        var started=ParticleAcceleratorWaveSchedule.IDLE.advance(true,10,40).schedule();
        assertEquals(ParticleAcceleratorWaveSchedule.IDLE,started.advance(false,25,40).schedule());
        var restarted=started.advance(false,25,40).schedule().advance(true,26,40);
        assertFalse(restarted.fire());
        assertEquals(66,restarted.schedule().nextWaveTick());
    }
    @Test void lateTickFiresOnlyOneWaveWithoutCatchup() {
        var scheduled=ParticleAcceleratorWaveSchedule.IDLE.advance(true,0,40).schedule();
        var late=scheduled.advance(true,1000,40);
        assertTrue(late.fire());
        assertEquals(1040,late.schedule().nextWaveTick());
        assertFalse(late.schedule().advance(true,1000,40).fire());
    }
}
