package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.List;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegContinuousSteadyGameTests {
    private static TegFixtureRegistryScope registryScope;

    private TegContinuousSteadyGameTests() { }

    @BeforeBatch(batch="teg_continuous_steady")
    public static void setupBatch(ServerLevel level) { registryScope=TegFixtureRegistryScope.acquire(level); }

    @AfterBatch(batch="teg_continuous_steady")
    public static void teardownBatch(ServerLevel level) {
        if(registryScope!=null){registryScope.close();registryScope=null;}
    }

    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",batch="teg_continuous_steady",timeoutTicks=6000)
    @GameTestTags({"teg-continuous-steady","teg-continuous-thermal-characterization"})
    public static void continuousBurnCanDisableStartupHeater(GameTestHelper helper) {
        ServerLevel end=helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.END);
        if(end==null)throw new GameTestAssertException("native End server level is unavailable");
        TegContinuousEngineGameTests.beginAutomaticOperatingStartup(helper,end,ctx->awaitMaterialBurn(ctx,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.SPACE));
    }

    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",batch="teg_continuous_steady",timeoutTicks=6000)
    @GameTestTags({"teg-continuous-steady"})
    public static void planetaryAmbientContinuousBoundaryComparison(GameTestHelper helper) {
        TegContinuousEngineGameTests.beginAutomaticOperatingStartup(helper,(ServerLevel)helper.getLevel(),ctx->awaitMaterialBurn(ctx,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT));
    }

    private static void awaitMaterialBurn(TegContinuousEngineGameTests.IgnitedFixture ctx,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        TegContinuousFixtureService.ObservedHardware obs=ctx.observed();
        try {
             var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
            obs=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
             TegContinuousFixtureTestSupport.assertThreeHotContacts(ctx.level(),obs);
             if(entry.phase()==TegFixtureSavedData.ContinuousPhase.HEATER_OFF) {
                 require(heatersOff(heaters(obs)),"automatic controller completed cutoff for both startup heaters before sustained proof");
                 com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY automatic consumer reached heater-off; beginning native 1000-tick full-load warmup phaseTick={}",entry.phaseStartedTick());
                 beginPowerWarmup(ctx,obs,heaters(obs),expectedBoundary);return;
            }
            long start=entry.phaseStartedTick(),deadline=start+600;
            if(entry.phase()!=TegFixtureSavedData.ContinuousPhase.IGNITION||start<0)throw new GameTestAssertException("production ignition phase/tick missing");
            double baseline=co2(TegContinuousEngineGameTests.sampleIgnition(ctx.level(),obs,ctx.output()));
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=STARTUP_QUALIFICATION worldTick={} co2Baseline={} heaterEnabled={}",start,baseline,heaterEnabled(obs));
             pollReaction(ctx,baseline,start,deadline,TegContinuousOperatingPlan.Stage.IGNITION,Long.MIN_VALUE,expectedBoundary);
        } catch(Throwable failure) { fail(ctx,obs,"startup qualification setup failed",failure); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static void pollReaction(TegContinuousEngineGameTests.IgnitedFixture ctx,double baseline,long start,long deadline,TegContinuousOperatingPlan.Stage stage,long freshFloor,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        ctx.helper().runAfterDelay(1,()->{
            var obs=ctx.observed();
            try {
                var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
                obs=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
                long now=ctx.level().getGameTime();
                if(now>deadline)throw new GameTestAssertException(stage+" qualification exceeded original 600-tick deadline; elapsed="+(now-start));
                var sample=TegContinuousEngineGameTests.sampleIgnition(ctx.level(),obs,ctx.output());
                double gain=co2(sample)-baseline;long ticks=now-start;
                boolean controlsValid=qualificationControlsValid(obs,stage);
                PowerFrame runningFrame=null;
                if(stage==TegContinuousOperatingPlan.Stage.RUNNING&&gain>=.01&&sample.meanBurnerKelvin()>=550&&controlsValid)runningFrame=powerFrame(ctx,obs,true,freshFloor);
                boolean generationReady=stage!=TegContinuousOperatingPlan.Stage.RUNNING||startupGenerationReady(runningFrame,obs,freshFloor);
                  if(ticks%100==0)com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase={}_QUALIFICATION elapsed={} worldTick={} powerSample={} co2Baseline={} co2Gain={} meanKelvin={} gasProduct={} heaters={} acceptedSourceW={} matchedApcInputW={} matchedApcOutputW={} matchedApcStorageJ={} matchedHvDeliveredJ={} matchedHvTick={} postHeaterDemandW={} frameReady={} generation={}",stage,ticks,ctx.level().getGameTime(),sample.powerSampleTick(),baseline,gain,sample.meanBurnerKelvin(),sample,heaterReceipts(heaters(obs)),runningFrame==null?0:runningFrame.output().inputWatts(),runningFrame==null?0:runningFrame.output().inputWatts(),runningFrame==null?0:runningFrame.output().outputWatts(),runningFrame==null?0:runningFrame.output().energyAfterJoules(),runningFrame==null?0:runningFrame.core().hvDeliveredJoules(),runningFrame==null?-1:runningFrame.core().hvSampleTick(),runningFrame==null?0:runningFrame.operatingDemandWatts(),runningFrame!=null&&runningFrame.ready(),runningFrame==null?-1:runningFrame.output().topologyGeneration());
                if(ticks%100==0){TegContinuousEngineGameTests.logBurn(ticks,obs,ctx.level());TegContinuousEngineGameTests.logCarrierThermal(ticks,obs,ctx.level());}
                 if(stage!=TegContinuousOperatingPlan.Stage.STARTUP&&gain>=.01&&sample.meanBurnerKelvin()>=550&&controlsValid) {
                      if(stage==TegContinuousOperatingPlan.Stage.IGNITION) {
                         if(now>=deadline)throw new GameTestAssertException("STARTUP qualified at original deadline; no time remains for RUNNING qualification");
                         String response=TegContinuousFixtureService.run(ctx.level(),ctx.origin());
                         if(!response.startsWith("RUNNING controls selected")) {
                              if(response.startsWith("RUNNING not ready")){pollReaction(new TegContinuousEngineGameTests.IgnitedFixture(ctx.helper(),ctx.level(),ctx.origin(),ctx.output(),ctx.lease(),obs),baseline,start,deadline,stage,freshFloor,expectedBoundary);return;}
                             throw new GameTestAssertException("production RUNNING transition refused: "+response);
                         }
                         var runningEntry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
                         var transitioned=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),runningEntry);
                         double runningBaseline=co2(TegContinuousEngineGameTests.sampleIgnition(ctx.level(),transitioned,ctx.output()));
                         com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=RUNNING_QUALIFICATION transitionElapsed={} co2Baseline={} worldTick={} powerSampleFloor={}",ticks,runningBaseline,ctx.level().getGameTime(),runningEntry.phasePowerSampleFloor());
                          long runningFloor=runningEntry.phasePowerSampleFloor();
                           pollReaction(new TegContinuousEngineGameTests.IgnitedFixture(ctx.helper(),ctx.level(),ctx.origin(),ctx.output(),ctx.lease(),transitioned),runningBaseline,start,deadline,TegContinuousOperatingPlan.Stage.RUNNING,runningFloor,expectedBoundary);return;
                     }
                       if(runningFrame==null||!generationReady||!heaterEnabled(obs)) {
                           if(now>=deadline)throw new GameTestAssertException("RUNNING generation qualification expired: materialReady=true generationReady="+generationReady+" frame="+runningFrame);
                            pollReaction(new TegContinuousEngineGameTests.IgnitedFixture(ctx.helper(),ctx.level(),ctx.origin(),ctx.output(),ctx.lease(),obs),baseline,start,deadline,stage,freshFloor,expectedBoundary);return;
                       }
                       com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=heater-on-generation-qualified acceptedSourceW={} operatingDemandW={} sample={} sampleFloor={} generation={} elapsed={}",runningFrame.output().inputWatts(),runningFrame.operatingDemandWatts(),runningFrame.output().sampleTick(),freshFloor,runningFrame.output().topologyGeneration(),ticks);
                        disableAndAwait(ctx,obs,ctx.helper().getTick()+200,expectedBoundary);return;
                }
                if(now>=deadline)throw new GameTestAssertException(stage+" qualification deadline within original 600 ticks; elapsed="+ticks+" gain="+gain+" meanKelvin="+sample.meanBurnerKelvin()+" controlsValid="+controlsValid+" sample="+sample);
                  pollReaction(new TegContinuousEngineGameTests.IgnitedFixture(ctx.helper(),ctx.level(),ctx.origin(),ctx.output(),ctx.lease(),obs),baseline,start,deadline,stage,freshFloor,expectedBoundary);
             } catch(Throwable failure){fail(ctx,obs,stage==TegContinuousOperatingPlan.Stage.RUNNING
                     ?"RUNNING qualification failed (material readiness is reported separately)":"ignition material qualification failed",failure);}
        });
    }

    private static boolean heaterEnabled(TegContinuousFixtureService.ObservedHardware o) {
        return heaters(o).stream().allMatch(h->h.settingsSnapshot().enabled().orElse(false));
    }
    private static boolean qualificationControlsValid(TegContinuousFixtureService.ObservedHardware o,TegContinuousOperatingPlan.Stage stage) { return qualificationControlsValid(o,stage,true); }
    private static boolean qualificationControlsValid(TegContinuousFixtureService.ObservedHardware o,TegContinuousOperatingPlan.Stage stage,boolean expectedHeaterEnabled) {
        if(stage!=TegContinuousOperatingPlan.Stage.IGNITION&&stage!=TegContinuousOperatingPlan.Stage.RUNNING
                &&stage!=TegContinuousOperatingPlan.Stage.HEATER_OFF)return false;
        boolean running=stage==TegContinuousOperatingPlan.Stage.RUNNING||stage==TegContinuousOperatingPlan.Stage.HEATER_OFF;
        double mixerTarget=running?TegContinuousOperatingPlan.RUNNING_MIXER_TARGET_KPA:TegContinuousOperatingPlan.STARTUP_MIXER_TARGET_KPA;
          double expectedSupplyPressure=running?TegContinuousOperatingPlan.RUNNING_BURNER_SUPPLY_TARGET_KPA:TegContinuousOperatingPlan.IGNITION_BURNER_SUPPLY_TARGET_KPA;
        var mixer=o.placement().additional().atmosMachines().stream().filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.MIXER).findFirst().orElse(null);
        var injectorBody=o.layout().devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.INJECTOR)
                .map(TegContinuousFuelPlan.Device::position).findFirst().orElse(null);
         var supply=o.placement().additional().ventilation().stream().filter(v->v.getBlockPos().equals(injectorBody)).findFirst().orElse(null);
           var pumps=o.placement().baseMachines().stream().filter(m->m.resolvedProfile()!=null&&(m.resolvedProfile().kind()==AtmosMachineProfile.Kind.VOLUME_PUMP||m.resolvedProfile().kind()==AtmosMachineProfile.Kind.PRESSURE_PUMP)).toList();
          boolean pumpsMatch=pumps.size()==2&&pumps.stream().allMatch(p->{
             var role=TegContinuousOperatingPlan.pumpRole(o.layout().fueled(),p.getBlockPos());
             return p.settingsSnapshot().equals(TegContinuousOperatingPlan.create(role,p.resolvedProfile(),p.settingsSnapshot(),stage).finalValues());
         });
         return mixer!=null&&mixer.settingsSnapshot().enabled().orElse(false)
                    &&pumpsMatch
                 &&Math.abs(mixer.settingsSnapshot().mixerFractionA().orElse(-1d)-TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION)<1e-12
                  &&Math.abs(mixer.settingsSnapshot().targetPressureKpa().orElse(-1d)-mixerTarget)<1e-12
                  &&supply!=null&&supply.config().settings().configuredEnabled()
                   &&Math.abs(supply.config().settings().targetPressureKPa()-expectedSupplyPressure)<1e-12
                  &&TegContinuousFixtureService.normalVentilationRecipeValid(o)
                  &&heaterEnabled(o)==expectedHeaterEnabled;
     }

    private static void disableAndAwait(TegContinuousEngineGameTests.IgnitedFixture ctx,TegContinuousFixtureService.ObservedHardware obs,long deadline,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        try {
            List<AtmosMachineBlockEntity> h=heaters(obs);
            long stamp=PowerRuntime.currentSampleTick(ctx.level());
            String response=TegContinuousFixtureService.heaterOff(ctx.level(),ctx.origin());
            if(!response.startsWith("Startup heater disabled"))throw new GameTestAssertException("production heater-off operation refused: "+response);
            pollOff(ctx,obs,h,stamp,deadline,expectedBoundary);
        } catch(Throwable failure){fail(ctx,obs,"heater off command failed",failure);}
    }

    private static void pollOff(TegContinuousEngineGameTests.IgnitedFixture ctx,TegContinuousFixtureService.ObservedHardware previousObserved,List<AtmosMachineBlockEntity> h,long stamp,long deadline,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        ctx.helper().runAfterDelay(1,()->{
            var currentObserved=previousObserved;
            try {
                var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
                currentObserved=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
                var core=currentObserved.placement().core();var meter=PowerRuntime.apcPowerMeter(ctx.level(),core);
                var output=PowerRuntime.apcOutputObservation(ctx.level(),ctx.output());
                 boolean allCutoffFresh=h.stream().allMatch(heater->heater.settingsSnapshot().enabled().map(v->!v).orElse(false)
                         &&heater.apcSampleTick()>stamp&&heater.apcAllocatedWatts()==0);
                 boolean okay=allCutoffFresh&&PowerGraphService.topologyReady(ctx.level())
                        &&core.apcSampleTick()>stamp&&meter.known()&&meter.deliveredWatts()>0
                        &&output.known()&&output.sampleTick()>stamp
                        &&output.topologyGeneration()==PowerGraphService.topologyGeneration(ctx.level())
                        &&TegContinuousFixtureService.heaterOffAcknowledged(ctx.level(),entry);
                 if(okay){com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=heater-off-ack helperTick={} worldTick={} privatePowerSample={} heaterReceipts={} coreSample={} coreHVJ={} apcEnergyJ={}",ctx.helper().getTick(),ctx.level().getGameTime(),PowerRuntime.currentSampleTick(ctx.level()),heaterReceipts(h),core.apcSampleTick(),core.hvDeliveredJoules(),ctx.output().energyJoules());beginPowerWarmup(ctx,currentObserved,h,expectedBoundary);return;}
                 if(ctx.helper().getTick()>=deadline)throw new GameTestAssertException("fresh heater-off acknowledgement deadline: heaterReceipts="+heaterReceipts(h)+" coreSample="+core.apcSampleTick()+" meter="+meter+" output="+output);
                pollOff(ctx,currentObserved,h,stamp,deadline,expectedBoundary);
            } catch(Throwable failure){fail(ctx,currentObserved,"heater off acknowledgement failed",failure);}
        });
    }

    private static void beginPowerWarmup(TegContinuousEngineGameTests.IgnitedFixture ctx,TegContinuousFixtureService.ObservedHardware observed,List<AtmosMachineBlockEntity> h,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        long start=ctx.helper().getTick();
        pollWarmup(ctx,observed,h,start,start+1000,expectedBoundary);
    }

    private static void pollWarmup(TegContinuousEngineGameTests.IgnitedFixture ctx,TegContinuousFixtureService.ObservedHardware previous,List<AtmosMachineBlockEntity> h,long start,long deadline,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        ctx.helper().runAfterDelay(1,()->{
            var obs=previous;
            try {
                var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
                obs=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
                 if(!heatersOff(h))throw new GameTestAssertException("startup heater enabled during warmup: "+heaterReceipts(h));
                var frame=powerFrame(ctx,obs,false,Long.MIN_VALUE);
                boolean eligible=frame.ready()&&frame.generationReady()&&frame.output().inputWatts()>=frame.output().outputWatts()&&frame.core().hvDeliveredJoules()>0;
                var admission=TegContinuousFixtureService.warmupAdmission(start,ctx.helper().getTick(),eligible);
                if((ctx.helper().getTick()-start)%100==0){com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=power-warmup ticks={} worldTick={} privatePowerSample={} eligible={} admission={} frame={} stock={} ignition={}",ctx.helper().getTick()-start,ctx.level().getGameTime(),PowerRuntime.currentSampleTick(ctx.level()),eligible,admission,frame,stockSummary(TegContinuousEngineGameTests.sampleStock(ctx.level(),obs)),TegContinuousEngineGameTests.sampleIgnition(ctx.level(),obs,ctx.output()));TegContinuousEngineGameTests.logBurn(ctx.helper().getTick()-start,obs,ctx.level());TegContinuousEngineGameTests.logCarrierThermal(ctx.helper().getTick()-start,obs,ctx.level());}
                if(admission==TegContinuousFixtureService.WarmupDecision.ADMIT) {
                    long warmupAdmissionHelperTick=ctx.helper().getTick();
                    long observationDeadline=warmupAdmissionHelperTick+1300;
                    awaitFreshWindowBaseline(ctx,obs,h,frame.output().sampleTick(),warmupAdmissionHelperTick,observationDeadline,expectedBoundary);
                    return;
                }
                if(admission==TegContinuousFixtureService.WarmupDecision.EXPIRED)throw new GameTestAssertException("full power warmup expired at original 1000-tick deadline: "+frame+" eligible="+eligible);
                pollWarmup(ctx,obs,h,start,deadline,expectedBoundary);
            } catch(Throwable failure){fail(ctx,obs,"power warmup failed",failure);}
        });
    }

    private static void awaitFreshWindowBaseline(TegContinuousEngineGameTests.IgnitedFixture ctx,
            TegContinuousFixtureService.ObservedHardware previous,List<AtmosMachineBlockEntity> heaterRoster,long oldSampleTick,
            long warmupAdmissionHelperTick,long deadline,
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        ctx.helper().runAfterDelay(1,()->{
            var obs=previous;
            try {
                var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
                obs=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
                if(!heatersOff(heaterRoster))throw new GameTestAssertException("startup heater enabled while waiting for post-warmup fresh solve baseline: "+heaterReceipts(heaterRoster));
                var frame=powerFrame(ctx,obs,false,oldSampleTick);
                if(frame.ready()&&frame.generationReady()&&frame.output().inputWatts()>=frame.output().outputWatts()
                        &&frame.core().hvDeliveredJoules()>0&&TegFixtureWindowClock.isFreshSample(frame.output().sampleTick(),oldSampleTick)) {
                    Object binding=new Object();Object token=entry.token();
                    var baseline=TegContinuousEngineGameTests.sampleStock(ctx.level(),obs);
                    TegContinuousEngineGameTests.logWasteDischarge(ctx.level(),obs,"BASE");
                    var wholeAir=new TegWholeAirEventObservation();
                    for(var cell:obs.layout().fueled().roomInterior())
                        com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.diagnosticCommittedWholeAir(ctx.level(),cell)
                                .ifPresent(d->wholeAir.markBaseline(cell,d.cycleIdentity(),TegWholeAirEventObservation.Channel.WHOLE_AIR,d.events()));
                    for(var cell:obs.layout().fueled().roomInterior())
                        com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.diagnosticCommittedHotspot(ctx.level(),cell)
                                .ifPresent(d->wholeAir.markBaseline(cell,d.cycleIdentity(),TegWholeAirEventObservation.Channel.HOTSPOT,d.events(),d.discardedMoles()));
                    var state=TegFixtureDeliveryMeter.observeSettled(null,frame.core().hvAllocationKnown(),frame.core().hvSampleTick(),frame.core().hvDeliveredJoules(),frame.output(),binding,token);
                    var clock=new TegFixtureWindowClock(ctx.level().getGameTime(),state.lastSampleTick());
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=fresh-settled-baseline helperTick={} warmupAdmissionHelperTick={} observationDeadline={} worldAnchor={} privateSampleAnchor={} priorSample={} stock={}",ctx.helper().getTick(),warmupAdmissionHelperTick,deadline,clock.worldTick(),clock.sampleTick(),oldSampleTick,stockSummary(baseline));
                    TegBurnerCellWriteObservation writes=null;
                    try {
                        writes=TegBurnerCellWriteObservation.acquire(ctx.helper(),ctx.level(),obs.layout().fueled().roomInterior());
                        pollWindow(ctx,obs,heaterRoster,token,binding,baseline,state,wholeAir,writes,clock,warmupAdmissionHelperTick,deadline,expectedBoundary);
                    } catch(Throwable failure) { if(writes!=null)writes.close(); throw failure; }
                    return;
                }
                if(ctx.helper().getTick()>=deadline)
                    throw new GameTestAssertException("post-warmup fresh matched full-load solve missed original 1300-tick observation deadline; oldSample="+oldSampleTick+" frame="+frame);
                awaitFreshWindowBaseline(ctx,obs,heaterRoster,oldSampleTick,warmupAdmissionHelperTick,deadline,expectedBoundary);
            } catch(Throwable failure){fail(ctx,obs,"fresh post-warmup window baseline failed",failure);}
        });
    }

    private static void pollWindow(TegContinuousEngineGameTests.IgnitedFixture ctx,TegContinuousFixtureService.ObservedHardware previous,List<AtmosMachineBlockEntity> heaterRoster,Object token,Object binding,TegContinuousEngineGameTests.StockSample baseline,TegFixtureDeliveryMeter.SettledState previousState,TegWholeAirEventObservation wholeAir,TegBurnerCellWriteObservation writes,TegFixtureWindowClock clock,long start,long deadline,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary) {
        try {
        ctx.helper().runAfterDelay(1,()->{
            var obs=previous;
            try {
                var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
                if(!entry.token().equals(token))throw new GameTestAssertException("fixture token changed during window");
                obs=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
                    for(var cell:obs.layout().fueled().roomInterior())
                        com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                                .diagnosticCommittedWholeAir(ctx.level(),cell)
                                .ifPresent(d->wholeAir.observe(cell,d.cycleIdentity(),TegWholeAirEventObservation.Channel.WHOLE_AIR,d.events(),java.util.Map.of()));
                for(var cell:obs.layout().fueled().roomInterior())
                    com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                            .diagnosticCommittedHotspot(ctx.level(),cell)
                            .ifPresent(d->wholeAir.observe(cell,d.cycleIdentity(),TegWholeAirEventObservation.Channel.HOTSPOT,d.events(),d.discardedMoles()));
                 if(!heatersOff(heaterRoster))throw new GameTestAssertException("startup heater enabled during window: "+heaterReceipts(heaterRoster));
                  if(!qualificationControlsValid(obs,TegContinuousOperatingPlan.Stage.HEATER_OFF,false))
                     throw new GameTestAssertException("post-cutoff recipe changed during heater-off steady window");
                var frame=powerFrame(ctx,obs,false,Long.MIN_VALUE);
                if(!frame.ready())throw new GameTestAssertException("unknown or unpaid full-load power frame: "+frame);
                var currentState=TegFixtureDeliveryMeter.observeSettled(previousState,frame.core().hvAllocationKnown(),frame.core().hvSampleTick(),frame.core().hvDeliveredJoules(),frame.output(),binding,token);
                long elapsed=currentState.matchedElapsedTicks();
                if(currentState.lastSampleTick()!=previousState.lastSampleTick()) {
                    var before=previousState.lastSample();var after=frame.output();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=matched-solve elapsed={} solveTicks={} worldTick={} sampleTick={} previousInputW={} previousOutputW={} inputW={} outputW={} hvAcceptedJ={} batteryDeltaJ={} cumulativeInputJ={} cumulativeOutputJ={} cumulativeHvJ={}",elapsed,currentState.lastSampleTick()-previousState.lastSampleTick(),ctx.level().getGameTime(),after.sampleTick(),before.inputWatts(),before.outputWatts(),after.inputWatts(),after.outputWatts(),frame.core().hvDeliveredJoules(),after.energyAfterJoules()-before.energyAfterJoules(),currentState.apcInputJoules(),currentState.apcOutputJoules(),currentState.acceptedHvJoules());
                }
                 if(currentState.lastSampleTick()!=previousState.lastSampleTick()&&elapsed%100==0){com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=settled-window elapsed={} helperTicks={} worldTick={} privatePowerSample={} apcInputJ={} apcOutputJ={} batteryDeltaJ={} sourceHvJ={} state={}",elapsed,ctx.helper().getTick()-start,ctx.level().getGameTime(),frame.output().sampleTick(),currentState.apcInputJoules(),currentState.apcOutputJoules(),currentState.storageDeltaJoules(),currentState.acceptedHvJoules(),currentState);TegContinuousEngineGameTests.logCarrierThermal(elapsed,obs,ctx.level());TegContinuousEngineGameTests.logBurn(elapsed,obs,ctx.level());}
                 if(elapsed>1200)throw new GameTestAssertException("private matched-solve window skipped exact 1200-tick endpoint: "+elapsed);
                 if(elapsed==1200) {
                     try { clock.requireExactWindow(ctx.level().getGameTime(),currentState.lastSampleTick(),elapsed,1200); }
                     catch(IllegalArgumentException mismatch) { throw new GameTestAssertException("world/private matched clock proof failed: "+mismatch.getMessage()); }
                     com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=exact-world-window-clock origin={} token={} worldAnchor={} worldNow={} worldElapsed={} privateAnchor={} privateNow={} privateElapsed={} matchedElapsed={} strictTicks=1200 result=PASS",ctx.origin(),token,clock.worldTick(),ctx.level().getGameTime(),ctx.level().getGameTime()-clock.worldTick(),clock.sampleTick(),currentState.lastSampleTick(),currentState.lastSampleTick()-clock.sampleTick(),elapsed);
                     com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY useful-power settlement inputJ={} outputJ={} netUsefulW={} seconds={} apcStorageDeltaJ={} matchedTicks={}",currentState.apcInputJoules(),currentState.apcOutputJoules(),currentState.netUsefulWatts(),elapsed/20d,currentState.storageDeltaJoules(),elapsed);
                     var wholeSummary=wholeAir.summary();
                      com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY COMMITTED_CHEMISTRY_OBSERVATION windowTicks=1200 observedCellChannels={} observedCycles={} eventCount={} countsByEffect={} speciesDeltaByEffect={} energyDeltaJByEffect={} actualHotspotCutoffCleanupMoles={} scope=committed-whole-air-and-hotspot channels native-completeness-unverified=true",wholeSummary.cells(),wholeSummary.cycles(),wholeSummary.eventCount(),wholeSummary.counts(),wholeSummary.speciesMoles(),wholeSummary.energyJoules(),wholeAir.cleanupSnapshot());
                      if(!currentState.satisfiesNetBacking())
                         throw new GameTestAssertException("settled energy accounting failed: "+currentState);
                     if(currentState.netUsefulWatts()<1000)
                         throw new GameTestAssertException("net useful power below 1000 W: "+currentState.netUsefulWatts()+" W, "+currentState);
                     var end=TegContinuousEngineGameTests.sampleStock(ctx.level(),obs);
                     TegContinuousEngineGameTests.logWasteDischarge(ctx.level(),obs,"END");
                     com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY measured stock endpoints baseline={} end={}",stockSummary(baseline),stockSummary(end));
                     var hotBefore=TegFixtureEnergyBalance.hotThermalStock(baseline.hotLoopThermalJoules(),baseline.burnerThermalJoules(),baseline.hotCarrierMoles(),baseline.burnerMoles());
                     var hotEnd=TegFixtureEnergyBalance.hotThermalStock(end.hotLoopThermalJoules(),end.burnerThermalJoules(),end.hotCarrierMoles(),end.burnerMoles());
                     com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY aggregate-hot-stock rawBeforeJ={} rawEndJ={} cpBeforeJPerK={} cpEndJPerK={} referenceK={} excessBeforeJ={} excessEndJ={} deltaJ={} toleranceJ={} feedKBefore={} feedKEnd={}",hotBefore.rawJoules(),hotEnd.rawJoules(),hotBefore.heatCapacityJoulesPerKelvin(),hotEnd.heatCapacityJoulesPerKelvin(),TegFixtureEnergyBalance.INLET_REFERENCE_KELVIN,hotBefore.excessJoules(),hotEnd.excessJoules(),hotEnd.excessJoules()-hotBefore.excessJoules(),budget(hotBefore.rawJoules(),hotEnd.rawJoules()),baseline.fuelFeedTemperatureKelvin(),end.fuelFeedTemperatureKelvin());
                     double feedTolerance=budget(TegFixtureEnergyBalance.INLET_REFERENCE_KELVIN,baseline.fuelFeedTemperatureKelvin(),end.fuelFeedTemperatureKelvin());
                     if(Math.abs(baseline.fuelFeedTemperatureKelvin()-TegFixtureEnergyBalance.INLET_REFERENCE_KELVIN)>feedTolerance||Math.abs(end.fuelFeedTemperatureKelvin()-TegFixtureEnergyBalance.INLET_REFERENCE_KELVIN)>feedTolerance)
                         throw new GameTestAssertException("continuous fuel feed is not at ambient inlet reference; excluded feed thermal store would be unjustified");
                     logCommittedCounterWindow(baseline,end);
                     var writeSnapshot=writes.snapshot();
                     if(writeSnapshot.writeCount()<=0)throw new GameTestAssertException("burner-cell write observation coverage incomplete: no writes observed");
                      com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY burner-cell writes count={} speciesAllowance={} actualMonstermosCleanupMoles={}",writeSnapshot.writeCount(),writeSnapshot.speciesBounds(),writeSnapshot.cleanupMoles());
                       var reaction=assertSteadyStocks(baseline,end,expectedBoundary,wholeAir,writeSnapshot.speciesBounds(),writeSnapshot.cleanupMoles());
                     assertHotStock(hotBefore,hotEnd,budget(hotBefore.rawJoules(),hotEnd.rawJoules()));
                    double committedGeneration=end.tegGeneratedJoules()-baseline.tegGeneratedJoules();
                    double hvGenerationBudget=budget(baseline.tegGeneratedJoules(),end.tegGeneratedJoules(),currentState.acceptedHvJoules());
                    if(currentState.acceptedHvJoules()>committedGeneration+hvGenerationBudget)
                        throw new GameTestAssertException("accepted HV exceeds committed generation acceptedHVJ="+currentState.acceptedHvJoules()+" committedGenerationJ="+committedGeneration+" numericalBudget="+hvGenerationBudget);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=hv-generation-check acceptedHVJ={} committedGenerationJ={} numericalBudget={} result=PASS",currentState.acceptedHvJoules(),committedGeneration,hvGenerationBudget);
                      assertThermalBoundary(baseline,end,reaction);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=window-complete baseline={} end={} meter={}",stockSummary(baseline),stockSummary(end),currentState);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=reaction-window reaction={}",reaction);
                    var statusBefore=TegFixtureSavedData.existing(ctx.level()).orElseThrow().entry(ctx.origin()).orElseThrow();
                    if(!statusBefore.token().equals(token)||statusBefore.mode()!=TegFixtureSavedData.Mode.CONTINUOUS
                            ||statusBefore.phase()!=TegFixtureSavedData.ContinuousPhase.HEATER_OFF||!statusBefore.initialized())
                        throw new GameTestAssertException("completed proof window no longer belongs to same initialized HEATER_OFF fixture token");
                    var statusCommand=TegPublicCommandTestSupport.execute(ctx.level(),"ms14 teg_fixture status "+ctx.origin().getX()+" "+ctx.origin().getY()+" "+ctx.origin().getZ());
                    if(statusCommand.returnCode()!=1||statusCommand.messages().size()!=1
                            ||!statusCommand.messages().getFirst().contains("CONTINUOUS phase=HEATER_OFF initialized=true"))
                        throw new GameTestAssertException("public status after completed window did not report same HEATER_OFF fixture: "+statusCommand);
                    var statusAfter=TegFixtureSavedData.existing(ctx.level()).orElseThrow().entry(ctx.origin()).orElseThrow();
                    if(!statusBefore.equals(statusAfter)||!statusAfter.token().equals(token))
                        throw new GameTestAssertException("public completed-window status mutated/reselected fixture origin/token");
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_PUBLIC_COMMAND marker=completed-window-status origin={} token={} returnCode={} feedback={} readOnlyEntryUnchanged=true",ctx.origin(),token,statusCommand.returnCode(),statusCommand.messages());
                    TegContinuousFixtureTestSupport.quiesceOperatingHardware(obs);ctx.lease().close();
                    if(!ctx.lease().wakeAnchorClaimReleased())throw new GameTestAssertException("fixture wake-anchor claim was not released");
                     writes.close();ctx.helper().succeed();return;
                }
                if(ctx.helper().getTick()>=deadline)throw new GameTestAssertException("settled window deadline elapsed="+elapsed+" state="+currentState);
                  pollWindow(ctx,obs,heaters(obs),token,binding,baseline,currentState,wholeAir,writes,clock,start,deadline,expectedBoundary);
            } catch(Throwable failure){writes.close();fail(ctx,obs,"settled power window failed",failure);}
        });
        } catch(Throwable schedulingFailure) { writes.close(); throw schedulingFailure; }
    }

    private static void logCommittedCounterWindow(TegContinuousEngineGameTests.StockSample before,
                                                   TegContinuousEngineGameTests.StockSample after) {
        var delta=TegCommittedCounterObservation.delta(before.committedCounters(),after.committedCounters());
        var owners=delta.owners();
        double hotHx=owners.entrySet().stream().filter(e->e.getKey().startsWith("hotHX"))
                .mapToDouble(e->e.getValue().thermalJoules()).sum();
        var injector=owners.get("burnerInjector");var waste=owners.get("burnerWasteExtraction");
        double reactionInference=after.burnerThermalJoules()-before.burnerThermalJoules()
                -injector.thermalJoules()+waste.thermalJoules()+hotHx;
        double coldDelta=after.coldLoopThermalJoules()-before.coldLoopThermalJoules();
        double radiationDelta=(after.radiatorAmbientHeatJoules()+after.radiatorSpaceHeatJoules())
                -(before.radiatorAmbientHeatJoules()+before.radiatorSpaceHeatJoules());
        double generation=after.tegGeneratedJoules()-before.tegGeneratedJoules();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY committed-counter summary burnerInjector thermalDeltaJ={} speciesDelta={}",injector.thermalJoules(),injector.speciesMoles());
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY committed-counter summary burnerWasteCapture thermalDeltaJ={} speciesDelta={}",waste.thermalJoules(),waste.speciesMoles());
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY committed-counter hotHX signedPipeHeatDeltasJ={},{},{} sumJ={}",owners.get("hotHX1").thermalJoules(),owners.get("hotHX2").thermalJoules(),owners.get("hotHX3").thermalJoules(),hotHx);
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY inferred burner balance (not committed reaction) burnerStockDeltaJ={} injectorPacketDeltaJ={} wasteCapturePacketDeltaJ={} sumHotHXPipeHeatDeltaJ={} inferredQJ={}",after.burnerThermalJoules()-before.burnerThermalJoules(),injector.thermalJoules(),waste.thermalJoules(),hotHx,reactionInference);
        double radiatorPipeDelta=owners.entrySet().stream().filter(e->e.getKey().startsWith("spaceRadiator:")).mapToDouble(e->e.getValue().thermalJoules()).sum();
        double coldHxPipeDelta=owners.entrySet().stream().filter(e->e.getKey().startsWith("coldHX")).mapToDouble(e->e.getValue().thermalJoules()).sum();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY cold/radiation/generation coldStockDeltaJ={} coldHXPipeHeatDeltaJ={} spaceRadiatorPipeHeatDeltaJ={} actualRadiationReceiptDeltaJ={} committedTEGGenerationDeltaJ={}",coldDelta,coldHxPipeDelta,radiatorPipeDelta,radiationDelta,generation);
    }

    private record ReactionWindow(double plasmaConsumedMoles,double co2FormedMoles,double moleTolerance,double chemicalHeatJoules,int eventCount) { }
       private static ReactionWindow assertSteadyStocks(TegContinuousEngineGameTests.StockSample a,TegContinuousEngineGameTests.StockSample b,com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind expectedBoundary,TegWholeAirEventObservation observations,java.util.Map<GasType,Double> writeAllowance,java.util.Map<GasType,Double> monstermosCleanup) {
         if(!a.machineOwners().equals(b.machineOwners())||!a.gasOwners().equals(b.gasOwners()))throw new GameTestAssertException("stock ownership changed");
         try {
              verifyHotInventory(a.hotCarrierMoles()); verifyHotInventory(b.hotCarrierMoles());
              TegFixtureEnergyBalance.verifyCarrierSpeciesIsolation(a.coldCarrierMoles(),b.coldCarrierMoles(),TegContinuousFuelPlan.COLD_CARRIER_GAS);
         } catch(IllegalArgumentException invalid) { throw new GameTestAssertException("continuous carrier species/inventory isolation failed: "+invalid.getMessage()); }
        for(double v:new double[]{a.inventoryThermalJoules(),b.inventoryThermalJoules(),a.hotLoopThermalJoules(),b.hotLoopThermalJoules(),a.coldLoopThermalJoules(),b.coldLoopThermalJoules(),a.oxygenProducedMoles(),b.oxygenProducedMoles(),a.plasmaProducedMoles(),b.plasmaProducedMoles(),a.minerThermalJoules(),b.minerThermalJoules(),a.radiatorAmbientHeatJoules(),b.radiatorAmbientHeatJoules(),a.radiatorSpaceHeatJoules(),b.radiatorSpaceHeatJoules(),a.allAmbientHeatJoules(),b.allAmbientHeatJoules(),a.allSpaceHeatJoules(),b.allSpaceHeatJoules(),a.tegGeneratedJoules(),b.tegGeneratedJoules()})finite(v);
        for(GasType g:GasType.values()){finite(a.inventoryMoles().getOrDefault(g,0d));finite(b.inventoryMoles().getOrDefault(g,0d));finite(a.exportedMoles().getOrDefault(g,0d));finite(b.exportedMoles().getOrDefault(g,0d));if(b.exportedMoles().getOrDefault(g,0d)<a.exportedMoles().getOrDefault(g,0d))bad("export counter decreased "+g,b.exportedMoles().getOrDefault(g,0d)-a.exportedMoles().getOrDefault(g,0d),0,budget(a.exportedMoles().getOrDefault(g,0d),b.exportedMoles().getOrDefault(g,0d)));}
          double n2Before=a.inventoryMoles().getOrDefault(GasType.NITROGEN,0d)+a.exportedMoles().getOrDefault(GasType.NITROGEN,0d);
          double n2After=b.inventoryMoles().getOrDefault(GasType.NITROGEN,0d)+b.exportedMoles().getOrDefault(GasType.NITROGEN,0d);
          FinitePipeSpeciesAudit.Report n2Audit;
          try { n2Audit=FinitePipeSpeciesAudit.assessNetworks(a.ownedNetworkCounters(),b.ownedNetworkCounters(),n2Before,n2After,Math.max(n2Before,n2After)); }
          catch(IllegalArgumentException invalid){throw new GameTestAssertException("global nitrogen pipe-version audit invalid: "+invalid.getMessage());}
          com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=global-nitrogen-pipe-audit windowTicks=1200 nodeWrites={} operandMagnitude={} expected={} current={} residual={} absoluteError={} derivedBound={} result={}",n2Audit.nodeWrites(),n2Audit.operandMagnitude(),n2Audit.expected(),n2Audit.current(),n2Audit.signedResidual(),n2Audit.absoluteError(),n2Audit.bound(),n2Audit.conserved()?"PASS":"FAIL");
          if(!n2Audit.conserved())bad("global nitrogen inventory plus exterior export is not conserved",n2Audit.signedResidual(),0,n2Audit.bound());
        for(var k:com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.values()){finite(a.exportedThermalJoules().getOrDefault(k,0d));finite(b.exportedThermalJoules().getOrDefault(k,0d));if(b.exportedThermalJoules().getOrDefault(k,0d)<a.exportedThermalJoules().getOrDefault(k,0d))bad("export heat counter decreased",b.exportedThermalJoules().getOrDefault(k,0d)-a.exportedThermalJoules().getOrDefault(k,0d),0,budget(a.exportedThermalJoules().getOrDefault(k,0d),b.exportedThermalJoules().getOrDefault(k,0d)));}
        if(b.tegGeneratedJoules()<a.tegGeneratedJoules()||b.minerThermalJoules()<a.minerThermalJoules())bad("committed generation/miner heat decreased",b.tegGeneratedJoules()-a.tegGeneratedJoules(),b.minerThermalJoules()-a.minerThermalJoules(),budget(a.tegGeneratedJoules(),b.tegGeneratedJoules(),a.minerThermalJoules(),b.minerThermalJoules()));
         double producedO=b.oxygenProducedMoles()-a.oxygenProducedMoles(),producedP=b.plasmaProducedMoles()-a.plasmaProducedMoles();
         double tol=budget(a.oxygenProducedMoles(),b.oxygenProducedMoles(),a.plasmaProducedMoles(),b.plasmaProducedMoles());
         if(producedO<=0||producedP<=0)bad("miners did not both produce gas",producedO,producedP,tol);
         var observed=observations.combustionSnapshot();
           var counters=TegCommittedCounterObservation.delta(a.committedCounters(),b.committedCounters()).owners();
           var hotPump=counters.get("hotCarrierPump");
          if(hotPump==null||!Double.isFinite(hotPump.movedMoles())||hotPump.movedMoles()<=0)
               throw new GameTestAssertException("owned hot carrier pump committed moved-moles delta must be positive");
           com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY committed-counter hotCarrierPump position={} movedMolesDelta={}",hotPump.position(),hotPump.movedMoles());
         var injector=counters.get("burnerInjector");var waste=counters.get("burnerWasteExtraction");
         double oxygenBefore=a.inventoryMoles().getOrDefault(GasType.OXYGEN,0d),oxygenAfter=b.inventoryMoles().getOrDefault(GasType.OXYGEN,0d);
         double plasmaBefore=a.inventoryMoles().getOrDefault(GasType.PLASMA,0d),plasmaAfter=b.inventoryMoles().getOrDefault(GasType.PLASMA,0d);
         double oxygenExport=b.exportedMoles().getOrDefault(GasType.OXYGEN,0d)-a.exportedMoles().getOrDefault(GasType.OXYGEN,0d);
         double plasmaExport=b.exportedMoles().getOrDefault(GasType.PLASMA,0d)-a.exportedMoles().getOrDefault(GasType.PLASMA,0d);
         double burnerOxygenBefore=a.burnerMoles().getOrDefault(GasType.OXYGEN,0d),burnerOxygenAfter=b.burnerMoles().getOrDefault(GasType.OXYGEN,0d);
         double burnerPlasmaBefore=a.burnerMoles().getOrDefault(GasType.PLASMA,0d),burnerPlasmaAfter=b.burnerMoles().getOrDefault(GasType.PLASMA,0d);
         com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY paired fuel diagnostics window=1200 O2_inventoryBefore={} O2_inventoryAfter={} O2_inventoryDelta={} O2_minerReceiptBefore={} O2_minerReceiptAfter={} O2_minerProductionDelta={} O2_exportDelta={} O2_committedReactionConsumed={} O2_burnerLocalBefore={} O2_burnerLocalAfter={} O2_burnerLocalDelta={} O2_injectorCommittedDelta={} O2_wasteCommittedDelta={} PL_inventoryBefore={} PL_inventoryAfter={} PL_inventoryDelta={} PL_minerReceiptBefore={} PL_minerReceiptAfter={} PL_minerProductionDelta={} PL_exportDelta={} PL_committedReactionConsumed={} PL_burnerLocalBefore={} PL_burnerLocalAfter={} PL_burnerLocalDelta={} PL_injectorCommittedDelta={} PL_wasteCommittedDelta={}",
                 oxygenBefore,oxygenAfter,oxygenAfter-oxygenBefore,a.oxygenProducedMoles(),b.oxygenProducedMoles(),producedO,oxygenExport,observed.oxygenConsumed(),burnerOxygenBefore,burnerOxygenAfter,burnerOxygenAfter-burnerOxygenBefore,injector.speciesMoles().getOrDefault(GasType.OXYGEN,0d),waste.speciesMoles().getOrDefault(GasType.OXYGEN,0d),
                 plasmaBefore,plasmaAfter,plasmaAfter-plasmaBefore,a.plasmaProducedMoles(),b.plasmaProducedMoles(),producedP,plasmaExport,observed.plasmaConsumed(),burnerPlasmaBefore,burnerPlasmaAfter,burnerPlasmaAfter-burnerPlasmaBefore,injector.speciesMoles().getOrDefault(GasType.PLASMA,0d),waste.speciesMoles().getOrDefault(GasType.PLASMA,0d));
          for(GasType g:new GasType[]{GasType.OXYGEN,GasType.PLASMA}) {
              double before=a.inventoryMoles().getOrDefault(g,0d),after=b.inventoryMoles().getOrDefault(g,0d),inventoryDelta=after-before;
              double minerProduced=g==GasType.OXYGEN?producedO:producedP;
              double inventoryBudget=budget(before,after);
              if(after<before-inventoryBudget)bad("fuel inventory drawdown "+g,inventoryDelta,minerProduced,inventoryBudget);
          }
         double dP=b.inventoryMoles().getOrDefault(GasType.PLASMA,0d)-a.inventoryMoles().getOrDefault(GasType.PLASMA,0d),dO=b.inventoryMoles().getOrDefault(GasType.OXYGEN,0d)-a.inventoryMoles().getOrDefault(GasType.OXYGEN,0d),eP=b.exportedMoles().getOrDefault(GasType.PLASMA,0d)-a.exportedMoles().getOrDefault(GasType.PLASMA,0d),eO=b.exportedMoles().getOrDefault(GasType.OXYGEN,0d)-a.exportedMoles().getOrDefault(GasType.OXYGEN,0d);
          // Scrubber transfer is internal collection, not exterior export. Only the vent's
          // downstream discharge contributes to exportedMoles; residual fuel exported there
          // is subtracted before deriving reaction consumption.
         double co2=b.inventoryMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d)-a.inventoryMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d)+b.exportedMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d)-a.exportedMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d);
         tol=budget(a.plasmaProducedMoles(),b.plasmaProducedMoles(),a.oxygenProducedMoles(),b.oxygenProducedMoles(),a.inventoryMoles().getOrDefault(GasType.PLASMA,0d),b.inventoryMoles().getOrDefault(GasType.PLASMA,0d),a.inventoryMoles().getOrDefault(GasType.OXYGEN,0d),b.inventoryMoles().getOrDefault(GasType.OXYGEN,0d),a.exportedMoles().getOrDefault(GasType.PLASMA,0d),b.exportedMoles().getOrDefault(GasType.PLASMA,0d),a.exportedMoles().getOrDefault(GasType.OXYGEN,0d),b.exportedMoles().getOrDefault(GasType.OXYGEN,0d),a.inventoryMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d),b.inventoryMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d),a.exportedMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d),b.exportedMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d));
           var cutoffCleanup=observations.cleanupSnapshot();
           var actualCleanup=new java.util.EnumMap<GasType,Double>(GasType.class);
           cutoffCleanup.forEach((g,v)->actualCleanup.merge(g,v,Double::sum));
           monstermosCleanup.forEach((g,v)->actualCleanup.merge(g,v,Double::sum));
           com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY actual cleanup channels hotspot={} monstermos={} combined={}",cutoffCleanup,monstermosCleanup,actualCleanup);
           double ep=producedP-dP-eP-actualCleanup.getOrDefault(GasType.PLASMA,0d),eo=producedO-dO-eO-actualCleanup.getOrDefault(GasType.OXYGEN,0d);
          double co2ExportDelta=b.exportedMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d)-a.exportedMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d);
           com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY reaction checks CO2Gain={} CO2ExportDelta={} plasmaConsumed={} thresholds co2Gain>0.01 exportDelta>=0 plasmaConsumed>0 (products may be retained)",co2,co2ExportDelta,ep);
          if(co2<.01)bad("CO2 gain floor 0.01",co2,0.01,tol);
           if(ep<=0)bad("plasma consumed must be positive",ep,0,tol);
           if(observed.eventCount()==0)throw new GameTestAssertException("reaction observation coverage incomplete: no committed combustion events observed");
          if(Math.abs(ep-observed.plasmaConsumed())>tol||Math.abs(eo-observed.oxygenConsumed())>tol)
              bad("global miner/inventory/export closure versus committed reaction events",ep-observed.plasmaConsumed(),eo-observed.oxygenConsumed(),tol);
              try { TegFixtureCombustionAccounting.verifyLocalClosure(a.burnerMoles(),b.burnerMoles(),injector.speciesMoles(),waste.speciesMoles(),observed,writeAllowance,actualCleanup); }
          catch(IllegalArgumentException invalid){throw new GameTestAssertException(invalid.getMessage());}
          if(Math.abs(observed.plasmaConsumed()-observed.carbonDioxideProduced()-observed.tritiumProduced())>tol
                  ||Math.abs(observed.tritiumProduced()-observed.tritiumConsumed()-observed.netSpeciesDelta().getOrDefault(GasType.TRITIUM,0d))>tol
                  ||Math.abs(observed.tritiumConsumed()-observed.waterProduced())>tol)throw new GameTestAssertException("committed combustion branch material closure failed");
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=committed-reaction-closure result=PASS scope=COMMITTED_WHOLE_AIR_AND_HOTSPOT globalPlasmaResidual={} globalOxygenResidual={} plasmaConsumed={} oxygenConsumed={} carbonDioxideProduced={} tritiumProduced={} tritiumConsumed={} waterProduced={} chemicalHeatJ={} eventCount={} actualCutoffCleanupMoles={} nativeCompletenessUnverified=true acceptanceOverall=false",ep-observed.plasmaConsumed(),eo-observed.oxygenConsumed(),observed.plasmaConsumed(),observed.oxygenConsumed(),observed.carbonDioxideProduced(),observed.tritiumProduced(),observed.tritiumConsumed(),observed.waterProduced(),observed.chemicalHeatJoules(),observed.eventCount(),cutoffCleanup);
           com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY CO2 product disposition retainedGainMoles={} exportedMoles={} (either disposition valid; no export floor)",co2-co2ExportDelta,co2ExportDelta);
         double coldDelta=b.coldLoopThermalJoules()-a.coldLoopThermalJoules();
         double coldTolerance=budget(a.coldLoopThermalJoules(),b.coldLoopThermalJoules());
         boolean strictSpace=expectedBoundary==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.SPACE;
         com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY cold-boundary proof boundary={} coldThermalDeltaJ={} toleranceJ={} strictColdNonincreaseRequired={} proofScope={}",expectedBoundary,coldDelta,coldTolerance,strictSpace,strictSpace?"strict-space-cold-nonincreasing":"planetary-ambient-boundary-comparison-not-strict-cold-steady-state");
         if(!TegFixtureEnergyBalance.retainsColdStock(strictSpace,coldDelta,coldTolerance))bad("strict SPACE cold loop thermal increase",0,coldDelta,coldTolerance);
         double amb=b.radiatorAmbientHeatJoules()-a.radiatorAmbientHeatJoules(),space=b.radiatorSpaceHeatJoules()-a.radiatorSpaceHeatJoules();
         if(amb<0||space<0)bad("radiator boundary receipt reset",amb,space,budget(a.radiatorAmbientHeatJoules(),b.radiatorAmbientHeatJoules(),a.radiatorSpaceHeatJoules(),b.radiatorSpaceHeatJoules()));
         double expectedReceipt=expectedBoundary==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.SPACE?space:amb;
         double otherReceipt=expectedBoundary==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.SPACE?amb:space;
         double receiptTolerance=budget(a.radiatorAmbientHeatJoules(),b.radiatorAmbientHeatJoules(),a.radiatorSpaceHeatJoules(),b.radiatorSpaceHeatJoules());
         if(!(expectedReceipt>0)||otherReceipt>receiptTolerance)bad("radiator receipt did not match declared exterior boundary "+expectedBoundary,amb,space,receiptTolerance);
        if(b.allSpaceHeatJoules()<a.allSpaceHeatJoules())bad("space heat counter reset",b.allSpaceHeatJoules()-a.allSpaceHeatJoules(),0,budget(a.allSpaceHeatJoules(),b.allSpaceHeatJoules()));
          return new ReactionWindow(ep,co2,tol,observed.chemicalHeatJoules(),observed.eventCount());
    }
     private static void assertHotStock(TegFixtureEnergyBalance.HotThermalStock before,TegFixtureEnergyBalance.HotThermalStock end,double tolerance){
         if(!TegFixtureEnergyBalance.retainsHotExcess(before.excessJoules(),end.excessJoules(),tolerance))bad("aggregate hot-plus-burner thermal excess drawdown",end.excessJoules()-before.excessJoules(),0,tolerance);
     }
     private static double budget(double... values){double n=0;for(double v:values){finite(v);float f=(float)v;if(!Float.isFinite(f))throw new GameTestAssertException("non-finite float stock measurement: "+v);n+=Math.max(8*Math.ulp(f),8*Math.ulp(v));}return Math.max(1e-6,n);}
    private static void verifyHotInventory(java.util.Map<GasType,Double> moles){
        double total=0;
        for(GasType gas:GasType.values()){double value=moles.getOrDefault(gas,0d);finite(value);if(value<0)bad("negative hot inventory species "+gas,value,0,budget(value));total+=value;}
        finite(total);
        if(!(total>0))throw new GameTestAssertException("hot inventory contains no gas");
    }
    private static void finite(double v){if(!Double.isFinite(v))throw new GameTestAssertException("non-finite stock observation: "+v);}
      private static String stockSummary(TegContinuousEngineGameTests.StockSample s){return TegContinuousEngineGameTests.stockSummary(s);}
    private static void assertThermalBoundary(TegContinuousEngineGameTests.StockSample a,TegContinuousEngineGameTests.StockSample b,ReactionWindow reaction){
        double exportA=a.exportedThermalJoules().values().stream().mapToDouble(Double::doubleValue).sum(),exportB=b.exportedThermalJoules().values().stream().mapToDouble(Double::doubleValue).sum();
        var base=new TegFixtureEnergyBalance.BoundaryObservation(a.inventoryThermalJoules(),a.tegGeneratedJoules(),a.conversionModel(),a.minerThermalJoules(),exportA,a.allAmbientHeatJoules(),a.allSpaceHeatJoules());
        var end=new TegFixtureEnergyBalance.BoundaryObservation(b.inventoryThermalJoules(),b.tegGeneratedJoules(),b.conversionModel(),b.minerThermalJoules(),exportB,b.allAmbientHeatJoules(),b.allSpaceHeatJoules());
        var result=TegFixtureEnergyBalance.compareBoundary(base,end);double expected=reaction.chemicalHeatJoules();
         double sinkA=a.conversionModel().modeledThermalSinkForGeneration(a.tegGeneratedJoules()),sinkB=b.conversionModel().modeledThermalSinkForGeneration(b.tegGeneratedJoules());
         double uncertainty=budget(base.thermalInventoryJoules(),end.thermalInventoryJoules(),base.generatedJoules(),end.generatedJoules(),sinkA,sinkB,base.minerThermalJoules(),end.minerThermalJoules(),base.exportedGasThermalJoules(),end.exportedGasThermalJoules(),base.ambientHeatOutJoules(),end.ambientHeatOutJoules(),base.spaceHeatOutJoules(),end.spaceHeatOutJoules(),expected)
                 +Math.max(8*Math.ulp((float)expected),8*Math.ulp(expected))*Math.max(1,reaction.eventCount());
         double residual=result.inferredReactionHeatJoules()-expected;com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_STEADY phase=SS14_MODEL_THERMAL_BOUNDARY efficiency={} powerFactor={} generatedJ={} modeledThermalSinkJ={} chemicalExpectedQ={} inferredQ={} residual={} numericalBudget={} result={}",a.conversionModel().efficiency(),a.conversionModel().powerFactor(),result.generatedDeltaJoules(),result.modeledThermalSinkDeltaJoules(),expected,result.inferredReactionHeatJoules(),residual,uncertainty,result);
         if(!result.provesChemicalExcess()||!(expected>TegFixtureEnergyBalance.MATERIAL_MARGIN_JOULES))throw new GameTestAssertException("continuous chemical heat proof must exceed strict material margin inferredQ="+result.inferredReactionHeatJoules()+" validatedChemicalQ="+expected+" strictMargin="+TegFixtureEnergyBalance.MATERIAL_MARGIN_JOULES);
         if(Math.abs(residual)>uncertainty)throw new GameTestAssertException("heater-off reaction thermal budget mismatch inferredQ="+result.inferredReactionHeatJoules()+" expectedQ="+expected+" residual="+residual+" numericalBudget="+uncertainty);
    }
    private static void bad(String s,double x,double y,double t){throw new GameTestAssertException(s+" delta1="+x+" delta2="+y+" measurementBudget="+t);}

    private record PowerFrame(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity core,
                              PowerRuntime.ApcOutputObservation output,double operatingDemandWatts,
                              boolean ready,boolean generationReady) { }
    private static boolean startupGenerationReady(PowerFrame frame,TegContinuousFixtureService.ObservedHardware obs,long freshFloor) {
        return frame!=null&&heaterEnabled(obs)&&frame.ready()&&frame.generationReady()&&Double.isFinite(frame.operatingDemandWatts())
                &&frame.operatingDemandWatts()>0&&frame.output().inputWatts()>=frame.operatingDemandWatts()
                &&frame.output().sampleTick()>freshFloor;
    }
    private static PowerFrame powerFrame(TegContinuousEngineGameTests.IgnitedFixture ctx,TegContinuousFixtureService.ObservedHardware obs,
                                        boolean expectHeaterOn,long freshFloor) {
        var frame=TegContinuousFixtureService.matchedPowerFrame(ctx.level(),obs,freshFloor,expectHeaterOn);
        return new PowerFrame(frame.core(),frame.output(),frame.demandWatts(),frame.fullLoadMatchedFrame(),frame.generationReady());
    }
    private static boolean heatersOff(List<AtmosMachineBlockEntity> roster){return roster.size()==2&&roster.stream().allMatch(h->h.settingsSnapshot().enabled().map(v->!v).orElse(false)&&h.apcAllocatedWatts()==0);}

    private static double co2(TegContinuousEngineGameTests.IgnitionSample s){return s.burnerCo2()+s.pipeCo2()+s.exportedCo2();}
    private static List<AtmosMachineBlockEntity> heaters(TegContinuousFixtureService.ObservedHardware o){
        var roster=o.placement().baseMachines().stream().filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.HEATER).toList();
        if(roster.size()!=2)throw new IllegalStateException("expected exactly two owned continuous startup heaters, found "+roster.size());
        return roster;
    }
    private static String heaterReceipts(List<AtmosMachineBlockEntity> roster){
        return roster.stream().map(h->h.getBlockPos()+"[paidJ="+h.committedPaidJoules()+",thermalJ="+h.committedThermalJoules()+",enabled="+h.settingsSnapshot().enabled()+",allocatedW="+h.apcAllocatedWatts()+"]").collect(java.util.stream.Collectors.joining(";"));
    }
    private static void fail(TegContinuousEngineGameTests.IgnitedFixture ctx,TegContinuousFixtureService.ObservedHardware obs,String message,Throwable t){try{TegContinuousFixtureTestSupport.quiesceOperatingHardware(obs);}catch(Throwable cleanup){t.addSuppressed(cleanup);}try{ctx.lease().close();}catch(Throwable cleanup){t.addSuppressed(cleanup);}String reason=t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();com.juicyslew.moonstation14.MoonStation14.LOGGER.error("TEG continuous steady GameTest failed: phase={} reason={}",message,reason,t);ctx.helper().fail(TegContinuousFixtureTestSupport.boundedFailureMessage("continuousBurnCanDisableStartupHeater: "+message+": "+reason));}
}
