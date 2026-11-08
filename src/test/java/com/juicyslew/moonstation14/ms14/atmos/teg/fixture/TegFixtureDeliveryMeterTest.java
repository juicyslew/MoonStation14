package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import static org.junit.jupiter.api.Assertions.*;

class TegFixtureDeliveryMeterTest {
    private final Object output=new Object();private final UUID token=UUID.randomUUID();
    private TegFixtureDeliveryMeter.State sample(TegFixtureDeliveryMeter.State s,long tick,double watts,long topology,Object be,UUID id){return TegFixtureDeliveryMeter.observe(s,true,tick,watts,topology,be,id);}
    @Test void creditsOnlyFreshContiguousUniqueSamples(){
        var s=sample(null,100,100,4,output,token);s=sample(s,120,100,4,output,token);assertEquals(100,s.totalJoules());
        s=sample(s,120,999,4,output,token);assertEquals(100,s.totalJoules(),"duplicate read/sample must not credit energy");
        s=TegFixtureDeliveryMeter.observe(s,false,121,100,4,output,token);assertEquals(100,s.totalJoules());
        s=sample(s,122,100,4,output,token);s=sample(s,142,100,4,output,token);assertEquals(200,s.totalJoules(),"unknown interval must not be integrated; resumed interval is measured");
        s=sample(s,200,100,4,output,token);assertEquals(200,s.totalJoules(),"oversized sample gap is not integrated");
        s=sample(s,220,100,5,output,token);assertEquals(200,s.totalJoules(),"topology change establishes a new baseline");
    }
    @Test void replacementTokenOutOfOrderAndInvalidWattsDoNotIntegrateOldOffer(){
        var s=sample(null,1,100,1,output,token);Object replacement=new Object();s=sample(s,21,100,1,replacement,token);assertEquals(0,s.totalJoules());
        UUID next=UUID.randomUUID();s=sample(s,41,100,1,replacement,next);assertEquals(0,s.totalJoules());
        s=sample(s,40,100,1,replacement,next);assertEquals(0,s.totalJoules());
        s=TegFixtureDeliveryMeter.observe(s,true,61,-1,1,replacement,next);assertEquals(0,s.totalJoules());assertEquals(-1,s.sampleTick());
    }
    @Test void exactHundredWattSecondAndRepeatedObservations(){
        var s=sample(null,200,100,8,output,token);s=sample(s,220,100,8,output,token);assertEquals(100,s.totalJoules());
        s=sample(s,220,100,8,output,token);assertEquals(100,s.totalJoules());s=sample(s,240,0,8,output,token);assertEquals(200,s.totalJoules());
    }
    private PowerRuntime.ApcOutputObservation apc(long tick,double in,double out,double before,double after,long generation){return new PowerRuntime.ApcOutputObservation(true,in,out,before,after,tick,generation);}
    private TegFixtureDeliveryMeter.SettledState settled(TegFixtureDeliveryMeter.SettledState s,long tick,double hv,double in,double out,double before,double after,long generation,Object id,UUID tok){return TegFixtureDeliveryMeter.observeSettled(s,true,tick,hv,apc(tick,in,out,before,after,generation),id,tok);}
    @Test void settledWindowExcludesAnchorAndIntegratesCurrentReceiptOnce(){
        var s=settled(null,0,999,500,100,0,0,4,output,token);
        assertEquals(0,s.matchedElapsedTicks());assertEquals(0,s.acceptedHvJoules());
        var second=settled(s,5,10,200,100,0,25,4,output,token);
        assertEquals(5,second.matchedElapsedTicks());assertEquals(10,second.acceptedHvJoules());
        assertEquals(50,second.apcInputJoules());assertEquals(25,second.apcOutputJoules());
        assertSame(second,TegFixtureDeliveryMeter.observeSettled(second,true,5,10,second.lastSample(),output,token));
        var third=settled(second,25,7,300,100,25,225,4,output,token);
        assertEquals(17,third.acceptedHvJoules());assertEquals(350,third.apcInputJoules());assertEquals(125,third.apcOutputJoules());
        assertEquals(225,third.storageDeltaJoules());
         assertEquals(180,third.netUsefulWatts());
    }
    @Test void acceptsShortIntervalsAndRejectsInvalidContinuationsWithoutReset(){
        for(int n:new int[]{1,5,20}){
            var s=settled(null,10,0,0,0,0,0,2,output,token);
            s=settled(s,10+n,3,20,0,0,n,2,output,token);assertEquals(n,s.matchedElapsedTicks());
        }
        var base=settled(null,1,0,0,0,0,0,2,output,token);
        assertThrows(IllegalArgumentException.class,()->settled(base,2,0,0,0,1,1,2,output,token));
        assertThrows(IllegalArgumentException.class,()->settled(base,22,0,0,0,0,0,2,output,token));
        assertThrows(IllegalArgumentException.class,()->settled(base,2,1,0,0,0,0,3,output,token));
        assertThrows(IllegalArgumentException.class,()->settled(base,2,1,0,0,0,0,2,new Object(),token));
        assertThrows(IllegalArgumentException.class,()->settled(base,2,1,0,0,0,0,2,output,UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,()->TegFixtureDeliveryMeter.observeSettled(base,true,2,1,apc(3,0,0,0,0,2),output,token));
        assertThrows(IllegalArgumentException.class,()->TegFixtureDeliveryMeter.observeSettled(base,false,2,1,apc(2,0,0,0,0,2),output,token));
        assertThrows(IllegalArgumentException.class,()->settled(base,0,1,0,0,0,0,2,output,token));
    }
    @Test void conflictingDuplicateAndBootstrapDischargeAreReceiptsNotSuccessVerdicts(){
        var s=settled(null,0,0,0,0,0,10,1,output,token);
        assertThrows(IllegalArgumentException.class,()->TegFixtureDeliveryMeter.observeSettled(s,true,0,0,apc(0,1,0,10,10,1),output,token));
        assertThrows(IllegalArgumentException.class,()->TegFixtureDeliveryMeter.observeSettled(s,true,0,1,apc(0,0,0,0,10,1),output,token));
        var discharged=settled(s,1,0,0,1,10,9.95,1,output,token);
        assertEquals(-.05,discharged.storageDeltaJoules(),1e-9);
    }
    @Test void fullPaidLoadAndGenerationReadinessRemainDistinct(){
        var buffered=TegContinuousFixtureService.classifyPowerFrame(true,1083.9186,1525,1200,325);
        assertTrue(buffered.fullPaidLoad());assertFalse(buffered.generationReady());
        assertFalse(TegContinuousFixtureService.classifyPowerFrame(false,2200,1525,1000,525).fullPaidLoad());
        assertFalse(TegContinuousFixtureService.classifyPowerFrame(true,2200,1524.99,1000,525).fullPaidLoad());
        assertFalse(TegContinuousFixtureService.classifyPowerFrame(true,2200,1525,1000,526).fullPaidLoad());
        assertFalse(TegContinuousFixtureService.classifyPowerFrame(true,Double.NaN,1525,1000,525).fullPaidLoad());
        assertFalse(TegContinuousFixtureService.classifyPowerFrame(true,2200,Double.POSITIVE_INFINITY,1000,525).fullPaidLoad());
        var mainBatteryStartup=TegContinuousFixtureService.classifyPowerFrame(true,0,6725,1725,5000);
        assertTrue(mainBatteryStartup.fullPaidLoad());
        assertFalse(mainBatteryStartup.generationReady(),"pre-combustion startup must not claim TEG generation");
        assertTrue(5000d<10_000d,"dedicated APC can pay its one 5 kW heater below the existing 10 kW per-APC cap");
        assertEquals(1_000_000d,TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES,"proportional APC split preserves the original total bootstrap");
    }
    @Test void heaterOffAcknowledgementRequiresFreshZeroAndFullPaidMatchedFrame(){
        assertFalse(TegContinuousFixtureService.heaterOffAckFrameReady(true,10,11,15,10,true));
        assertFalse(TegContinuousFixtureService.heaterOffAckFrameReady(true,11,10,15,10,true));
        assertFalse(TegContinuousFixtureService.heaterOffAckFrameReady(true,11,11,10,10,true));
        assertFalse(TegContinuousFixtureService.heaterOffAckFrameReady(false,11,11,15,10,true));
        assertFalse(TegContinuousFixtureService.heaterOffAckFrameReady(true,11,11,15,10,false));
        assertTrue(TegContinuousFixtureService.heaterOffAckFrameReady(true,11,11,11,10,true));
        assertFalse(TegContinuousFixtureService.heaterOffAckFrameReady(true,11,11,11,Long.MAX_VALUE,true));
    }
    @Test void settledOneMinuteBufferedIntervalsRequireWholeWindowNetBacking(){
        var balanced=alternatingSettled(1000,2200,5000);
        assertEquals(1200,balanced.matchedElapsedTicks());
        assertEquals(96000,balanced.apcInputJoules(),1e-8);
        assertEquals(91500,balanced.apcOutputJoules(),1e-8);
        assertEquals(4500,balanced.storageDeltaJoules(),1e-8);
        assertTrue(balanced.satisfiesNetBacking());
        assertFalse(alternatingSettled(1000,1800,9000).satisfiesNetBacking());
    }
    @Test void netUsefulPowerMeasuresStorageIncreaseAndRequiresPositiveMatchedDuration(){
        var zero=settled(null,0,0,0,0,0,0,4,output,token);
        assertThrows(IllegalStateException.class,zero::netUsefulWatts);
        var malformed=new TegFixtureDeliveryMeter.SettledState(0,20,20,0,0,0,Double.NaN,0,2,0,apc(20,0,0,0,0,4),4,output,token);
        assertThrows(IllegalStateException.class,malformed::netUsefulWatts);
        var useful=zero;double battery=0;
        for(int i=1;i<=60;i++){
            double input=299546.363/55,outputWatts=94875/55;
            double after=battery+(input-outputWatts);
             useful=settled(useful,i*20,6000,input,outputWatts,battery,after,4,output,token);battery=after;
        }
        assertEquals(1200,useful.matchedElapsedTicks());
        assertEquals(204671.363/55, useful.netUsefulWatts(),1e-8);
        assertTrue(useful.satisfiesNetBacking());
        var negative=settled(null,0,0,0,0,10000,10000,4,output,token);
         negative=settled(negative,20,0,950,1000,10000,9950,4,output,token);
        assertEquals(-50,negative.netUsefulWatts());
        var phantom=settled(null,0,1e9,1000,1000,0,0,4,output,token);
        assertThrows(IllegalStateException.class,phantom::netUsefulWatts);
        assertFalse(phantom.satisfiesNetBacking());
    }
    private TegFixtureDeliveryMeter.SettledState alternatingSettled(double low,double high,double initialEnergy){
        var s=settled(null,0,0,0,0,initialEnergy,initialEnergy,4,output,token);
        double energy=initialEnergy;
        for(int i=0;i<60;i++){
            double input=i%2==0?low:high,delta=input-1525;
            double after=energy+delta;
            s=settled(s,(i+1)*20,2000,input,1525,energy,after,4,output,token);
            energy=after;
        }
        return s;
    }
}
