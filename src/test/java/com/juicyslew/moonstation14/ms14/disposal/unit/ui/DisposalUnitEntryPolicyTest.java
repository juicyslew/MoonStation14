package com.juicyslew.moonstation14.ms14.disposal.unit.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisposalUnitEntryPolicyTest {
    private DisposalUnitEntryPolicy.TickResult tick(long elapsed,long required,boolean unmoved,boolean authority){
        return DisposalUnitEntryPolicy.tick(elapsed,required,true,true,true,authority,true,unmoved,true,true);
    }
    @Test void pendingLifecycleWaitsUntilResolvedDelayThenCompletes(){
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(1,10,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(9,10,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.COMPLETE,tick(10,10,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(10,20,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.COMPLETE,tick(20,20,true,true));
    }
    @Test void resolvedUnitPrototypeEntrySecondsDetermineDoAfterDelay(){
        var resolvedUnit=new com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData.Unit(100,100,true,"unit",.6,.8,2,3,4,2,1);
        long required=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalUnitTimingKernel.ticks(resolvedUnit.entrySeconds());
        assertEquals(DisposalUnitEntryPolicy.TickResult.WAIT,tick(10,required,true,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.COMPLETE,tick(12,required,true,true));
    }
    @Test void interruptionCancelsRatherThanWaitingOrCompleting(){
        assertEquals(DisposalUnitEntryPolicy.TickResult.CANCEL,tick(1,10,false,true));
        assertEquals(DisposalUnitEntryPolicy.TickResult.CANCEL,tick(10,10,true,false));
        assertEquals(DisposalUnitEntryPolicy.TickResult.CANCEL,
                DisposalUnitEntryPolicy.tick(10,10,true,false,true,true,true,true,true,true));
    }
    @Test void operatorControlsAreAuthorizedOnlyAtReachOrAsThisUnitsBody(){
        assertTrue(DisposalUnitEntryPolicy.mayOperate(true,true,false,false,false)); // outside operator at reachable unit
        assertTrue(DisposalUnitEntryPolicy.mayOperate(true,true,true,true,false)); // own contained body
        assertFalse(DisposalUnitEntryPolicy.mayOperate(true,false,true,false,true)); // transit/other unit owner
        assertFalse(DisposalUnitEntryPolicy.mayOperate(false,true,false,false,false));
    }
}
