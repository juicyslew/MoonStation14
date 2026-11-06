package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HullImpactTraceStateTest {
    @Test void twoConsecutiveDeepSamplesLatchOriginalWitnessAfterLaterRecovery() {
        var state=new HullImpactTraceState();
        state.observe(1,row(.15,true,true,false));
        assertNull(state.failure(),"one deep sample remains below the unchanged two-sample failure criterion");
        state.observe(2,row(.16,true,true,false));
        assertEquals("SAT-penetration-over-0.10-two-consecutive-samples",state.failure().reason());
        assertEquals(1,state.failure().firstTick());
        assertEquals(2,state.failure().confirmedTick());
        state.observe(3,row(0,true,true,false));
        for(int tick=4;tick<=22;tick++)state.observe(tick,row(0,true,true,false));
        assertEquals(1,state.failure().firstTick(),"recovery cannot erase or move the first witness");
        assertEquals(20,state.postFailureSamples());
        assertTrue(state.finishFailureDiagnostics(10,22));
    }

    @Test void isolatedDeepSampleThatClearsDoesNotLatchFailure() {
        var state=new HullImpactTraceState();
        state.observe(1,row(.8,true,true,false));
        state.observe(2,row(.02,true,true,false));
        state.observe(3,row(.7,true,true,false));
        state.observe(4,row(.01,true,true,false));
        assertNull(state.failure());
        assertEquals(.8,state.maximumPenetration(),0d);
    }

    @Test void wholeSlabEscapeLatchesEvenWithZeroSatAndCoverageLossCannotMaskIt() {
        var state=new HullImpactTraceState();
        state.observe(70,row(0,true,true,true));
        assertEquals("entire-support-slab-escaped",state.failure().reason());
        assertTrue(state.supportSlabEscapeObserved());
        for(int tick=71;tick<=90;tick++)state.observe(tick,row(0,false,false,true));
        assertTrue(state.requiresNativeOnlySample());
        assertTrue(state.finishFailureDiagnostics(0,90));
        state.observe(91,row(0,false,false,false));
        assertEquals("entire-support-slab-escaped",state.failure().reason());
        assertEquals(70,state.failure().firstTick());
    }

    @Test void failureWindowKeepsMinimumSamplesAndCapsAtEightyOrNaturalSettlement() {
        var capped=new HullImpactTraceState();
        latchDeep(capped);
        for(int tick=2;tick<2+HullImpactTraceState.MIN_POST_FAILURE_SAMPLES-1;tick++)
            capped.observe(tick,row(0,true,true,false));
        assertFalse(capped.finishFailureDiagnostics(10,20),"must keep the full minimum post-failure window");
        capped.observe(21,row(0,true,true,false));
        assertTrue(capped.finishFailureDiagnostics(10,21),"natural settling permits a bounded 20-sample report");

        var bounded=new HullImpactTraceState();
        bounded.observe(1,row(.2,true,true,false));bounded.observe(2,row(.2,true,true,false));
        for(int i=1;i<=HullImpactTraceState.MAX_POST_FAILURE_SAMPLES;i++)
            bounded.observe(2+i,row(0,true,true,false));
        assertEquals(HullImpactTraceState.MAX_POST_FAILURE_SAMPLES,bounded.postFailureSamples());
        assertTrue(bounded.finishFailureDiagnostics(0,82));
    }

    private static void latchDeep(HullImpactTraceState state) {
        state.observe(0,row(.11,true,true,false));
        state.observe(1,row(.11,true,true,false));
    }

    private static HullTerrainImpactProbe.Observation row(double depth,boolean coverage,
                                                           boolean unchanged,boolean escaped) {
        return new HullTerrainImpactProbe.Observation(0,new Vector3d(3600,-50,3600),new Quaterniond(),
                new Vector3d(0,-1,0),new Vector3d(),new AABB(3600,-52,3600,3608,-50,3613),depth,
                depth>0?new BlockPos(3600,-48,3600):null,null,new Vector3d(0,depth,0),coverage,
                unchanged,escaped);
    }
}
