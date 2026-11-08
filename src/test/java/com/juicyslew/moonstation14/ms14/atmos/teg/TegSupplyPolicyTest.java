package com.juicyslew.moonstation14.ms14.atmos.teg;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineCadence;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePowerState;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerLiveLoop;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TegSupplyPolicyTest {
    @Test void runtimeInitialStateDoesNotInjectDefaultMinimumRampPower() {
        var initial = TegRuntime.initial();
        assertEquals(0, initial.supply().rampPositionWatts());
        assertEquals(0, initial.supply().maximumSupplyWatts());
    }

    @Test void smoothsOfferAndTracksDrawWithinRampWindowWithoutCappingOffer() {
        var prior = new TegSupplyPolicy.State(10_000, 1000);
        var result = TegSupplyPolicy.update(prior, 2000, 2, 20_000, true);
        assertEquals(1000, result.physicalGenerationWatts(), 0);
        assertEquals(1010, result.offeredMaximumSupplyWatts(), 0);
        assertEquals(10_500, result.state().rampPositionWatts(), 0);
        assertTrue(result.offeredMaximumSupplyWatts() < result.state().rampPositionWatts());

        var largerOffer = TegSupplyPolicy.update(new TegSupplyPolicy.State(5000, 100_000), 0, 1, 5000, true);
        assertEquals(80_000, largerOffer.offeredMaximumSupplyWatts(), 0);
    }

    @Test void zeroGenerationDecaysOfferButIneligibleHardZeros() {
        var prior = new TegSupplyPolicy.State(7000, 1000);
        var noFlow = TegSupplyPolicy.update(prior, 0, 1, 0, true);
        assertEquals(800, noFlow.offeredMaximumSupplyWatts(), 0);
        assertEquals(7000 / TegSupplyPolicy.DEFAULT_RAMP_FACTOR, noFlow.state().rampPositionWatts(), 1e-9);
        var ineligible = TegSupplyPolicy.update(prior, 5000, 1, 3000, false);
        assertEquals(0, ineligible.offeredMaximumSupplyWatts(), 0);
        assertEquals(0, ineligible.state().maximumSupplyWatts(), 0);
    }

    @Test void rejectsInvalidInputsAndState() {
        var state = new TegSupplyPolicy.State(5000, 0);
        assertThrows(IllegalArgumentException.class, () -> TegSupplyPolicy.update(state, -1, 1, 0, true));
        assertThrows(IllegalArgumentException.class, () -> TegSupplyPolicy.update(state, 1, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new TegSupplyPolicy.State(Double.NaN, 0));
    }

    @Test void paidTwelveTickEventsAndTwentyTickSolvesExposeRealWindowVariationWithoutUnpaidSupply() {
        var identity=UUID.randomUUID();
        var settings=new AtmosMachineCadence.Settings(.6,8,30);
        var cadence=new AtmosMachineCadence.Clock();
        var paid=new AtmosMachineCadence.PaidWorkBudget();
        var intervalEnergy=new AtmosMachinePowerState.TegEnergyBudget();
        var policy=TegRuntime.initial().supply();
        double priorAcceptedWatts=0;
        long totalGeneratedJ=0,totalAcceptedJ=0;
        long[] windowGenerationJ=new long[3], windowAcceptedJ=new long[3];
        int solve=0;
        for(long tick=0;tick<=60;tick++) {
            if(tick>0&&tick%PowerRuntime.TICKS_PER_SOLVE==0)
                paid.accept(identity,10_000, tick,600);
            if(cadence.due(tick,settings)&&paid.canWork(identity,tick,tick,600)) {
                assertTrue(paid.commitWork(identity,tick,tick,600));
                double generatedJ=3_000; // 5 kW physical generation over the resolved 0.6 s device interval.
                intervalEnergy.credit(generatedJ,true);
                var updated=TegSupplyPolicy.update(policy,generatedJ,.6,priorAcceptedWatts,true,1.05,5000,.2);
                policy=updated.state();totalGeneratedJ+=3_000;
                windowGenerationJ[solve]+=3_000;
                cadence.completed();
            }
            if(tick>0&&tick%PowerRuntime.TICKS_PER_SOLVE==0) {
                double offered=intervalEnergy.capWatts(policy.maximumSupplyWatts(),policy.maximumSupplyWatts(),PowerRuntime.TICKS_PER_SOLVE);
                double actualLoad=switch(solve){case 0->1_000;case 1->3_000;default->30_000;};
                double actualAccepted=Math.min(offered,Math.min(actualLoad,30_000));
                assertTrue(intervalEnergy.settle(actualAccepted,PowerRuntime.TICKS_PER_SOLVE,true));
                long acceptedJ=(long)(actualAccepted*PowerRuntime.TICKS_PER_SOLVE/20d);
                windowAcceptedJ[solve]=acceptedJ;totalAcceptedJ+=acceptedJ;
                assertTrue(acceptedJ<=windowGenerationJ[solve],"acceptance must remain backed by committed generated joules");
                priorAcceptedWatts=actualAccepted;
                solve++;
            }
        }
        assertEquals(3,solve);
        assertArrayEquals(new long[]{3_000,6_000,6_000},windowGenerationJ);
        assertEquals(15_000,totalGeneratedJ);
        assertTrue(totalAcceptedJ>0&&totalAcceptedJ<=totalGeneratedJ);
        assertTrue(windowAcceptedJ[0]<windowAcceptedJ[1],"lower actual load and one-event window remain distinct from physical generation");
        assertTrue(windowAcceptedJ[2]>0,"a full 30 kW consumer cap does not create energy beyond the TEG budget");
    }

    @Test void allocatorSeparatesPhysicalGenerationOfferAcceptedHvAndFullBatteryOutput() {
        var energy=new AtmosMachinePowerState.TegEnergyBudget();
        energy.credit(10_000,true);
        double offered=energy.capWatts(20_000,30_000,20);
        assertEquals(10_000,offered,0,"actual offer is capped by committed generation energy, not smoothed rating");
        var full=PowerLiveLoop.solve(new PowerLiveLoop.Input(
                java.util.List.of(new PowerLiveLoop.Source("teg",1,true,offered,30_000)),
                java.util.List.of(new PowerLiveLoop.Substation("sub",1,true,2,true)),
                java.util.List.of(new PowerLiveLoop.Apc("apc",2,true,3,true,true,1_000_000)),
                java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),
                java.util.List.of(),java.util.List.of(),java.util.List.of(new PowerLiveLoop.ApcConsumer("load",3,true,1_000)),
                java.util.List.of(),20));
        double acceptedHv=full.hvSourceWatts().get("teg");
        double acceptedJ=acceptedHv;
        assertTrue(acceptedHv>0&&acceptedHv<=offered,"HV allocation is actual accepted output, not offer ceiling");
        assertTrue(energy.settle(acceptedHv,20,true));
        assertEquals(1_000,full.apcConsumerWatts().get("load"),1e-6);
        assertEquals(1_000_000,full.batteryEnergyJoules().get("apc"),1e-6,"full battery has no storage increase despite offered TEG power");
        assertTrue(acceptedJ<=10_000,"accepted HV remains bounded by committed generation energy");

        var variable=PowerLiveLoop.solve(new PowerLiveLoop.Input(
                java.util.List.of(new PowerLiveLoop.Source("teg2",1,true,3_000,30_000)),
                java.util.List.of(new PowerLiveLoop.Substation("sub2",1,true,2,true)),
                java.util.List.of(new PowerLiveLoop.Apc("apc2",2,true,3,true,true,1_000)),
                java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),
                java.util.List.of(),java.util.List.of(),java.util.List.of(new PowerLiveLoop.ApcConsumer("load2",3,true,5_000)),
                java.util.List.of(),20));
        assertTrue(variable.hvSourceWatts().get("teg2")<=3_000);
        assertTrue(variable.apcConsumerWatts().get("load2")>0&&variable.apcConsumerWatts().get("load2")<5_000,
                "variable load is limited by accepted source plus the battery's existing per-device discharge cap");
    }
}
