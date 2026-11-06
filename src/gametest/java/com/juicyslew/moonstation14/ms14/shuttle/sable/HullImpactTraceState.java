package com.juicyslew.moonstation14.ms14.shuttle.sable;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded per-fixture evidence latch; a later recovery never erases a physical failure. */
final class HullImpactTraceState {
    static final int HISTORY_LIMIT=12;
    static final int MIN_POST_FAILURE_SAMPLES=20;
    static final int MAX_POST_FAILURE_SAMPLES=80;

    record Sample(int tick,HullTerrainImpactProbe.Observation observation) { }
    record Failure(String reason,int firstTick,int confirmedTick,Sample witness,Sample confirmation) { }

    private final ArrayDeque<Sample> history=new ArrayDeque<>();
    private Failure failure;
    private Sample pendingDeep;
    private int consecutiveDeep;
    private int postFailureSamples;
    private double maximumPenetration;
    private boolean supportSlabEscapeObserved;
    private boolean worldCoverageAvailable=true;

    void observe(int tick,HullTerrainImpactProbe.Observation row) {
        boolean alreadyFailed=failure!=null;
        if(alreadyFailed)postFailureSamples++;
        var sample=new Sample(tick,row);
        history.addLast(sample);
        while(history.size()>HISTORY_LIMIT)history.removeFirst();
        maximumPenetration=Math.max(maximumPenetration,row.maxTerrainPenetration());
        supportSlabEscapeObserved|=row.fullyBelowSupportSlab();
        worldCoverageAvailable=row.sceneComplete();
        if(alreadyFailed)return;

        if(!row.sceneComplete()) { latch("scene-coverage-lost",sample,sample); return; }
        if(!row.hullUnchanged()) { latch("authored-hull-changed",sample,sample); return; }
        if(row.fullyBelowSupportSlab()) { latch("entire-support-slab-escaped",sample,sample); return; }
        if(row.maxTerrainPenetration()>.10) {
            consecutiveDeep++;
            if(pendingDeep==null)pendingDeep=sample;
            if(consecutiveDeep>=2)latch("SAT-penetration-over-0.10-two-consecutive-samples",pendingDeep,sample);
        } else {
            consecutiveDeep=0;
            pendingDeep=null;
        }
    }

    private void latch(String reason,Sample witness,Sample confirmation) {
        if(failure==null)failure=new Failure(reason,witness.tick(),confirmation.tick(),witness,confirmation);
    }

    Failure failure(){return failure;}
    List<Sample> recentHistory(){return List.copyOf(history);}
    double maximumPenetration(){return maximumPenetration;}
    int postFailureSamples(){return postFailureSamples;}
    boolean supportSlabEscapeObserved(){return supportSlabEscapeObserved;}
    boolean requiresNativeOnlySample(){return failure!=null&&!worldCoverageAvailable;}

    boolean finishFailureDiagnostics(int settledSamples,int tick) {
        if(failure==null)return false;
        if(tick>=900||postFailureSamples>=MAX_POST_FAILURE_SAMPLES)return true;
        if(postFailureSamples<MIN_POST_FAILURE_SAMPLES)return false;
        return settledSamples>=10||supportSlabEscapeObserved;
    }
}
