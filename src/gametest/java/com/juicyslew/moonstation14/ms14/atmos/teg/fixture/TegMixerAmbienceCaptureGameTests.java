package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/** Bounded server-side observation of the real continuous-fixture mixer's work/audio eligibility. */
@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegMixerAmbienceCaptureGameTests {
    private TegMixerAmbienceCaptureGameTests() { }

    @GameTest(template="disposal_power_clear", templateNamespace="moonstation14", batch="teg_mixer_ambience_capture", timeoutTicks=6000)
    @GameTestTags({"teg-mixer-ambience-capture"})
    public static void observeAutomaticContinuousMixer(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        TegContinuousEngineGameTests.beginAutomaticOperatingStartup(helper,level,TegMixerAmbienceCaptureGameTests::beginCapture);
    }

    private static void beginCapture(TegContinuousEngineGameTests.IgnitedFixture ctx) {
        try {
            var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
            var observed=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
            var mixer=observed.placement().additional().atmosMachines().stream()
                    .filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.MIXER)
                    .findFirst().orElseThrow(()->new GameTestAssertException("owned resolved mixer missing"));
            var settings=mixer.settingsSnapshot();var profile=mixer.resolvedProfile();
            if(!settings.enabled().orElse(false))throw new GameTestAssertException("automatic HEATER_OFF stage did not enable mixer");
            if(profile.ambientAudio().isEmpty())throw new GameTestAssertException("resolved mixer ambient cue unavailable");
            if(profile.configuredReceiverLoadWatts()!=0)
                throw new GameTestAssertException("fixture mixer is expected to be unpowered/no APC receiver");
            var id=mixer.placementIdentity();if(id==null)throw new GameTestAssertException("mixer placement identity unavailable");
            var state=mixer.deviceRuntimeState();
            var counts=new Counts(mixer.committedMovedMoles(),mixer.committedWorkEvents());
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_MIXER_AUDIO_SAMPLE event=begin stage=HEATER_OFF origin={} mixer={} identity={} profile={} enabled={} cue={} targetKPa={} fractionA={} baselineCommittedMoles={} baselineCommittedEvents={} scope=server-sequence-only heardAudioCertification=false",
                    ctx.origin(),mixer.getBlockPos(),id,mixer.profileIdentity(),settings.enabled(),profile.ambientAudio().get(),settings.targetPressureKpa(),settings.mixerFractionA(),counts.baseMoles,counts.baseEvents);
            poll(ctx,entry.token(),mixer.getBlockPos(),id,240,counts,new Snapshot(state.lastUnpoweredProcessWorldTick(),mixer.ambientEligible()));
        } catch(RuntimeException failure) { fail(ctx,"mixer ambience capture setup failed",failure); }
    }

    private static final class Counts {
        final double baseMoles; final long baseEvents;
        long processed,positive,zeroAtTarget,zeroFeed,unavailable,mismatch,eligibilityTransitions;
        Counts(double moles,long events){baseMoles=moles;baseEvents=events;}
    }
    private record Snapshot(long processTick,boolean eligible) { }
    private record PortSample(String id,double volume,double pressure,double oxygen,double plasma) { }

    private static void poll(TegContinuousEngineGameTests.IgnitedFixture ctx,Object token,BlockPos pos,java.util.UUID identity,int remaining,Counts counts,Snapshot previous) {
        ctx.helper().runAfterDelay(1,()->{
            try {
                var entry=TegFixtureSavedData.existing(ctx.level()).flatMap(d->d.entry(ctx.origin())).orElseThrow();
                if(!entry.token().equals(token))throw new GameTestAssertException("fixture token changed during capture");
                var observed=TegContinuousFixtureService.observeOwnedHardware(ctx.level(),entry);
                if(!(ctx.level().getBlockEntity(pos) instanceof AtmosMachineBlockEntity mixer)||mixer.isRemoved()||!identity.equals(mixer.placementIdentity()))
                    throw new GameTestAssertException("mixer unloaded/replaced during bounded capture");
                var state=mixer.deviceRuntimeState();long processTick=state.lastUnpoweredProcessWorldTick();
                boolean eligible=mixer.ambientEligible();
                if(eligible!=previous.eligible())counts.eligibilityTransitions++;
                boolean processed=processTick!=previous.processTick();
                if(processed) {
                    counts.processed++;
                    var samples=new java.util.ArrayList<PortSample>();
                    for(var port:mixer.gasPorts()) {
                        var component=TegContinuousFixtureService.capturedComponentForPort(observed.topology(),observed.capture(),new PipeTopology.NodeKey(pos,port.layer(),port.id())).orElse(null);
                        if(component==null){samples.add(new PortSample(port.id(),Double.NaN,Double.NaN,Double.NaN,Double.NaN));continue;}
                        double volume=component.nodes().stream().mapToDouble(n->n.volume()).sum();var gas=component.aggregate();
                        samples.add(new PortSample(port.id(),volume,gas.pressureKpa(volume),gas.moles(GasType.OXYGEN),gas.moles(GasType.PLASMA)));
                    }
                    PortSample outlet=samples.stream().filter(s->s.id().equals("outlet")).findFirst().orElse(null);
                    PortSample a=samples.stream().filter(s->s.id().equals("inlet")).findFirst().orElse(null);
                    PortSample b=samples.stream().filter(s->s.id().equals("filter")).findFirst().orElse(null);
                    boolean inputsKnown=a!=null&&b!=null&&Double.isFinite(a.oxygen())&&Double.isFinite(b.plasma());
                    double accepted=state.lastMoles();boolean positive=accepted>0;
                    if(positive)counts.positive++;
                    else if(!inputsKnown)counts.unavailable++;
                    else if(outlet!=null&&Double.isFinite(outlet.pressure())&&mixer.settingsSnapshot().targetPressureKpa().isPresent()
                            &&outlet.pressure()>=mixer.settingsSnapshot().targetPressureKpa().get())counts.zeroAtTarget++;
                    else if(a.oxygen()<=0||b.plasma()<=0)counts.zeroFeed++;
                    else counts.unavailable++;
                    if(mixer.settingsSnapshot().enabled().orElse(false)&&positive!=eligible)counts.mismatch++;
                    var sourceReadbacks=new java.util.ArrayList<String>();
                    for(BlockPos sourcePos:observed.layout().devices().stream()
                            .filter(device->device.role()==TegContinuousFuelPlan.DeviceRole.SCRUBBER)
                            .map(TegContinuousFuelPlan.Device::position).toList()) {
                        var source=ctx.level().getBlockEntity(sourcePos);
                        if(source instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
                            sourceReadbacks.add("SCRUBBER@"+sourcePos+"#"+device.deviceId()+" meter="+PowerRuntime.ventilationMeter(ctx.level(),device)+" species="+device.transferObservation().speciesMoles()+" thermalJ="+device.transferObservation().thermalJoules());
                        else sourceReadbacks.add("SCRUBBER@"+sourcePos+" unavailable");
                    }
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_MIXER_AUDIO_SAMPLE event=process worldTick={} eventTick={} status={} acceptedMoles={} eligible={} enabled={} ports={} cumulativeCommittedMoles={} committedEvents={} committedPaidJ={} scrubbers={} mixer={} identity={}",
                            ctx.level().getGameTime(),processTick,state.lastWorkStatus(),accepted,eligible,mixer.settingsSnapshot().enabled(),samples,mixer.committedMovedMoles(),mixer.committedWorkEvents(),mixer.committedPaidJoules(),sourceReadbacks,pos,identity);
                } else if(eligible!=previous.eligible()) {
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_MIXER_AUDIO_SAMPLE event=eligibility_transition worldTick={} processTick={} status={} acceptedMoles={} eligible={} mixer={} identity={}",ctx.level().getGameTime(),processTick,state.lastWorkStatus(),state.lastMoles(),eligible,pos,identity);
                }
                if(counts.mismatch>0)throw new GameTestAssertException("enabled mixer event eligibility differs from accepted work; mismatches="+counts.mismatch);
                if(remaining<=1) {
                    double delta=mixer.committedMovedMoles()-counts.baseMoles;long eventDelta=mixer.committedWorkEvents()-counts.baseEvents;
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_MIXER_AUDIO_SAMPLE event=summary durationTicks=240 processed={} positive={} zeroAtTarget={} zeroFeed={} unavailable={} mismatch={} eligibilityTransitions={} committedMolesDelta={} committedEventsDelta={} committedPaidJ={} clientLoopMeasured=false heardAudioCertification=false",
                            counts.processed,counts.positive,counts.zeroAtTarget,counts.zeroFeed,counts.unavailable,counts.mismatch,counts.eligibilityTransitions,delta,eventDelta,mixer.committedPaidJoules());
                    if(counts.processed==0||delta<=0||eventDelta<=0)throw new GameTestAssertException("capture window had no new positive mixer work; applicability not established");
                    ctx.lease().close();ctx.helper().succeed();return;
                }
                poll(ctx,token,pos,identity,remaining-1,counts,new Snapshot(processTick,eligible));
            } catch(RuntimeException failure) { fail(ctx,"mixer ambience capture failed",failure); }
        });
    }

    private static void fail(TegContinuousEngineGameTests.IgnitedFixture ctx,String label,RuntimeException failure) {
        ctx.lease().close();ctx.helper().fail(TegContinuousFixtureTestSupport.boundedFailureMessage(label+": "+failure));
    }
}
