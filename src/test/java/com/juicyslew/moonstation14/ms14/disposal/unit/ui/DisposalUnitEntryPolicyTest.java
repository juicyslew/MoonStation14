package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;

class DisposalUnitEntryPolicyTest {
    @Test void nativeDamageThresholdAppliesPerEventWithoutAccumulation() {
        assertFalse(DisposalUnitEntryPolicy.damageCancels(0.001f));
        assertFalse(DisposalUnitEntryPolicy.damageCancels(0.999f));
        assertTrue(DisposalUnitEntryPolicy.damageCancels(1.0f));
        assertTrue(DisposalUnitEntryPolicy.damageCancels(2.0f));
        assertFalse(DisposalUnitEntryPolicy.damageCancels(-2.0f));
        assertFalse(DisposalUnitEntryPolicy.damageCancels(0.6f));
    }
    private DisposalUnitEntryPolicy.TickResult tick(long elapsed,long required,boolean unmoved,boolean authority){
        return DisposalUnitEntryPolicy.tick(elapsed,required,true,true,true,authority,true,unmoved);
    }
    @Test void movementUsesStrictFullThreeDimensionalDistanceThreshold(){
        Vec3 origin=Vec3.ZERO;
        assertTrue(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(.001,0,0)));
        assertTrue(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(.2999,0,0)));
        assertFalse(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(.3,0,0)));
        assertFalse(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(.3001,0,0)));
        assertTrue(DisposalUnitEntryPolicy.withinMovementThreshold(new Vec3(.99,0,0),new Vec3(1.01,0,0)));
        assertTrue(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(0,.001,0)));
        assertFalse(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(.2,.2,.2)));
        // Independent queries model repeated ticks: displacement is measured from the same start, not accumulated.
        assertTrue(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(.2,0,0)));
        assertTrue(DisposalUnitEntryPolicy.withinMovementThreshold(origin,new Vec3(.2,0,0)));
    }
    @Test void pendingLifecycleWaitsUntilResolvedDelayThenCompletes(){
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(1,10,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(9,10,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.COMPLETE,tick(10,10,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(10,20,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.COMPLETE,tick(20,20,true,true));
    }
    @Test void resolvedUnitPrototypeEntrySecondsDetermineDoAfterDelay(){
        var resolvedUnit=new com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Unit(100,100,true,"unit",.6,.8,2,3,4,2,1,
                com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Unit.DEFAULT_INSERTION_SOUND);
        long required=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalUnitTimingKernel.ticks(resolvedUnit.entrySeconds());
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(10,required,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.COMPLETE,tick(12,required,true,true));
    }
    @Test void interruptionCancelsRatherThanWaitingOrCompleting(){
        assertEquals(DisposalUnitEntryPolicy.TickResult.CANCEL,tick(1,10,false,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.CANCEL,tick(10,10,true,false));
        assertEquals(DisposalUnitEntryPolicy.TickResult.CANCEL,
                DisposalUnitEntryPolicy.tick(10,10,true,false,true,true,true,true));
    }
    @Test void operatorControlsAreAuthorizedOnlyAtReachOrAsThisUnitsBody(){
        assertTrue(DisposalUnitEntryPolicy.mayOperate(true,true,false,false,false)); // outside operator at reachable unit
        assertTrue(DisposalUnitEntryPolicy.mayOperate(true,true,true,true,false)); // own contained body
        assertFalse(DisposalUnitEntryPolicy.mayOperate(true,false,true,false,true)); // transit/other unit owner
        assertFalse(DisposalUnitEntryPolicy.mayOperate(false,true,false,false,false));
    }
}
