package com.juicyslew.moonstation14.ms14.disposal.ui;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisposalConstructionModeTest {
    private static final String PIPE="moonstation14:disposal_pipe";
    private static final String BEND="moonstation14:disposal_bend";

    @Test void actualTransitionHandlesEnterSelectionRotationAndExitWithoutMaterialState(){
        var empty=new DisposalConstructionMode.Entry("",0,0,0,false);
        var entered=apply(empty,1,DisposalConstructionRequest.Action.ENTER,"");
        assertTrue(entered.entry().active());
        assertEquals(PIPE,entered.entry().prototype());
        assertEquals(1,entered.entry().revision());

        var selected=apply(entered.entry(),2,DisposalConstructionRequest.Action.SELECT,BEND);
        assertEquals(BEND,selected.entry().prototype());
        assertEquals(0,selected.entry().rotationOffset());
        assertEquals(2,selected.entry().revision());
        assertTrue(selected.changedPlan());

        var rotated=apply(selected.entry(),3,DisposalConstructionRequest.Action.ROTATE,"");
        assertEquals(1,rotated.entry().rotationOffset());
        assertEquals(3,rotated.entry().revision());
        assertTrue(rotated.changedPlan());

        var exited=apply(rotated.entry(),4,DisposalConstructionRequest.Action.EXIT,"");
        assertFalse(exited.entry().active());
        assertEquals("",exited.entry().prototype());
        assertEquals(0,exited.entry().rotationOffset());
        assertTrue(exited.changedPlan());
    }

    @Test void staleAndUnsupportedRequestsDoNotMutateModeOrReportPlanInvalidation(){
        var active=new DisposalConstructionMode.Entry(BEND,1,4,8,true);
        var stale=apply(active,8,DisposalConstructionRequest.Action.EXIT,"");
        assertSame(active,stale.entry());
        assertFalse(stale.changedPlan());
        var invalid=DisposalConstructionMode.transition(active,request(9,DisposalConstructionRequest.Action.SELECT,"unknown:recipe"),
                id->id.equals(PIPE)||id.equals(BEND));
        assertSame(active,invalid.entry());
        assertFalse(invalid.changedPlan());
        assertEquals(active,invalid.entry());
    }

    @Test void bodyMindAndEpochArePartOfSessionAndOldModesAreDiscarded(){
        UUID actor=UUID.randomUUID(),body=UUID.randomUUID();
        var current=new DisposalConstructionMode.BodySession(actor,body,"mind-a",12);
        var same=new DisposalConstructionMode.BodySession(actor,body,"mind-a",12);
        var newBody=new DisposalConstructionMode.BodySession(actor,UUID.randomUUID(),"mind-a",12);
        var newMind=new DisposalConstructionMode.BodySession(actor,body,"mind-b",12);
        var newEpoch=new DisposalConstructionMode.BodySession(actor,body,"mind-a",13);
        assertEquals(current,same);
        assertNotEquals(current,newBody);
        assertNotEquals(current,newMind);
        assertNotEquals(current,newEpoch);

        Map<DisposalConstructionMode.BodySession,DisposalConstructionMode.Entry> modes=new HashMap<>();
        var oldMode=new DisposalConstructionMode.Entry(BEND,0,2,1,true);
        modes.put(current,oldMode);
        DisposalConstructionMode.clearStale(modes,newBody);
        assertTrue(modes.isEmpty());
        assertNull(modes.get(newBody));
    }

    private static DisposalConstructionMode.Transition apply(DisposalConstructionMode.Entry state,long sequence,
            DisposalConstructionRequest.Action action,String prototype){
        return DisposalConstructionMode.transition(state,request(sequence,action,prototype),id->id.equals(PIPE)||id.equals(BEND));
    }
    private static DisposalConstructionRequest request(long sequence,DisposalConstructionRequest.Action action,String prototype){
        return new DisposalConstructionRequest(UUID.randomUUID(),1,sequence,action,prototype);
    }
}
