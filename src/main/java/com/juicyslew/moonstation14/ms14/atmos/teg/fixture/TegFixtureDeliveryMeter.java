package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import java.util.Objects;

/** Pure transition for the fixture's existing APC-output observer; energy is credited only across adjacent known samples. */
final class TegFixtureDeliveryMeter {
    static final double RECEIPT_TOLERANCE_JOULES=1e-6;
    record State(long sampleTick,double previousWatts,double totalJoules,long topologyGeneration,Object outputIdentity,Object token) { }
    /** Immutable proof of energy settled inside a continuous observation window. */
    record SettledState(long firstSampleTick,long lastSampleTick,long matchedElapsedTicks,
                        double acceptedHvJoules,double apcInputJoules,double apcOutputJoules,
                        double initialBatteryAfterJoules,double lastBatteryAfterJoules,int sampleCount,double lastHvDeliveredJoules,
                        PowerRuntime.ApcOutputObservation lastSample,long topologyGeneration,
                        Object outputIdentity,Object token) {
        double storageDeltaJoules(){return lastBatteryAfterJoules-initialBatteryAfterJoules;}
        double netUsefulWatts(){
            if(matchedElapsedTicks<=0)throw new IllegalStateException("no matched settlement duration");
            double watts=storageDeltaJoules()/(matchedElapsedTicks/20d);
            if(!Double.isFinite(watts))throw new IllegalStateException("non-finite net useful power");
            return watts;
        }
        boolean satisfiesNetBacking(){
            double required=apcOutputJoules+Math.max(storageDeltaJoules(),0)-RECEIPT_TOLERANCE_JOULES;
            return apcInputJoules>0&&apcOutputJoules>0&&acceptedHvJoules>=required
                    &&storageDeltaJoules()>=-RECEIPT_TOLERANCE_JOULES;
        }
    }
    static final class ReceiptRejectedException extends IllegalArgumentException { ReceiptRejectedException(String reason){super(reason);} }
    private TegFixtureDeliveryMeter() { }
    static State initial(){return new State(-1,0,0,-1,null,null);}
    static State observe(State old,boolean known,long tick,double watts,long topology,Object output,Object token){
        if(old==null)old=initial();
        if(!known||output==null||token==null||tick<0||!Double.isFinite(watts)||watts<0)return new State(-1,0,old.totalJoules(),-1,output,token);
        if(old.sampleTick()>=0&&old.outputIdentity()==output&&old.token().equals(token)&&old.topologyGeneration()==topology&&tick<=old.sampleTick())return old;
        if(old.sampleTick()<0||old.outputIdentity()!=output||!old.token().equals(token)||old.topologyGeneration()!=topology||tick<=old.sampleTick()||tick-old.sampleTick()>PowerRuntime.TICKS_PER_SOLVE)
            return new State(tick,watts,old.totalJoules(),topology,output,token);
        double total=old.totalJoules()+old.previousWatts()*(tick-old.sampleTick())/20d;
        return new State(tick,watts,total,topology,output,token);
    }
    static SettledState initialSettled(){return null;}
    static SettledState observeSettled(SettledState old,boolean hvKnown,long hvTick,double hvJoules,
                                       PowerRuntime.ApcOutputObservation apc,Object identity,Object token){
        if(!hvKnown||apc==null||!apc.known())throw rejected("unknown HV/APC receipt");
        if(identity==null||token==null)throw rejected("missing binding identity/token");
        if(hvTick<0||apc.sampleTick()<0||hvTick!=apc.sampleTick())throw rejected("sample tick mismatch");
        if(!finiteNonnegative(hvJoules)||!finiteNonnegative(apc.inputWatts())||!finiteNonnegative(apc.outputWatts())||
           !finiteNonnegative(apc.energyBeforeJoules())||!finiteNonnegative(apc.energyAfterJoules()))throw rejected("invalid receipt value");
        long generation=apc.topologyGeneration();
        if(generation<0)throw rejected("invalid topology generation");
        if(old==null)return new SettledState(hvTick,hvTick,0,0,0,0,apc.energyAfterJoules(),apc.energyAfterJoules(),1,hvJoules,apc,generation,identity,token);
        if(old.outputIdentity()!=identity||!Objects.equals(old.token(),token)||old.topologyGeneration()!=generation)throw rejected("binding/topology changed");
        if(hvTick==old.lastSampleTick()){
            if(apc.equals(old.lastSample())&&Double.compare(hvJoules,old.lastHvDeliveredJoules())==0)return old;
            throw rejected("conflicting duplicate sample");
        }
        long elapsed=hvTick-old.lastSampleTick();
        if(elapsed<=0)throw rejected("out-of-order sample");
        if(elapsed>PowerRuntime.TICKS_PER_SOLVE)throw rejected("sample gap exceeds solve interval");
        if(!near(apc.energyBeforeJoules(),old.lastBatteryAfterJoules()))throw rejected("battery continuity failed");
        double input=apc.inputWatts()*elapsed/20d,output=apc.outputWatts()*elapsed/20d;
        double delta=apc.energyAfterJoules()-apc.energyBeforeJoules();
        if(!near(input-output,delta))throw rejected("APC interval settlement unbalanced");
        return new SettledState(old.firstSampleTick(),hvTick,old.matchedElapsedTicks()+elapsed,
                old.acceptedHvJoules()+hvJoules,old.apcInputJoules()+input,old.apcOutputJoules()+output,
                old.initialBatteryAfterJoules(),apc.energyAfterJoules(),old.sampleCount()+1,hvJoules,apc,generation,identity,token);
    }
    private static boolean finiteNonnegative(double value){return Double.isFinite(value)&&value>=0;}
    private static boolean near(double a,double b){return Math.abs(a-b)<=RECEIPT_TOLERANCE_JOULES;}
    private static ReceiptRejectedException rejected(String reason){return new ReceiptRejectedException(reason);}
}
