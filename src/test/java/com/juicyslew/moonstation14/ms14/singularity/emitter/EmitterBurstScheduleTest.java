package com.juicyslew.moonstation14.ms14.singularity.emitter;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class EmitterBurstScheduleTest {
    private static final EmitterProfile PROFILE = new EmitterProfile(
            ResourceLocation.fromNamespaceAndPath("moonstation14","test_emitter"),600,1,4,40,80,200,40,60,14,.2);
    @Test void firstShotWaitsMaximumOnDelayAndBurstUsesResolvedCountAndSpacing() {
        var schedule=EmitterBurstSchedule.idle();
        schedule=schedule.tick(PROFILE,true,true,123).schedule();
        assertEquals(200,schedule.remainingDelay());
        for(int i=0;i<199;i++) {
            var tick=schedule.tick(PROFILE,true,true,80); assertFalse(tick.fire()); schedule=tick.schedule();
        }
        var shot=schedule.tick(PROFILE,true,true,80); assertTrue(shot.fire()); schedule=shot.schedule();
        assertEquals(3,schedule.shotsRemaining()); assertEquals(40,schedule.remainingDelay());
        for(int shotIndex=0;shotIndex<3;shotIndex++) {
            for(int i=0;i<39;i++) { var tick=schedule.tick(PROFILE,true,true,80); assertFalse(tick.fire()); schedule=tick.schedule(); }
            var tick=schedule.tick(PROFILE,true,true,80); assertTrue(tick.fire()); schedule=tick.schedule();
        }
        assertEquals(0,schedule.shotsRemaining()); assertEquals(80,schedule.remainingDelay());
    }
    @Test void powerLossOrIntentOffCancelsBurstAndRegainStartsFreshFirstDelay() {
        var schedule=EmitterBurstSchedule.idle().tick(PROFILE,true,true,120).schedule();
        schedule=schedule.tick(PROFILE,true,false,80).schedule();
        assertFalse(schedule.wasPowered()); assertEquals(0,schedule.remainingDelay());
        schedule=schedule.tick(PROFILE,true,true,150).schedule();
        assertEquals(200,schedule.remainingDelay()); assertEquals(0,schedule.shotsRemaining());
        assertFalse(schedule.tick(PROFILE,false,true,80).fire());
    }
}
