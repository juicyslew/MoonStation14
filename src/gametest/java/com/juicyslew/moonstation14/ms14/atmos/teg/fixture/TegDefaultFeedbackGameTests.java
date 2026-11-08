package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Default finite fueled public fixture characterization; this measures feedback behavior, not self-sustain PASS. */
@GameTestTags({"atmosphere", "teg", "teg-default-feedback"})
@PrefixGameTestTemplate(false)
public final class TegDefaultFeedbackGameTests {
    private static TegFixtureRegistryScope registryScope;
    private TegDefaultFeedbackGameTests() { }
    @BeforeBatch(batch="teg_default_feedback_budget")
    public static void setupBatch(ServerLevel level) { registryScope=TegFixtureRegistryScope.acquire(level); }
    @AfterBatch(batch="teg_default_feedback_budget")
    public static void teardownBatch(ServerLevel level) { if(registryScope!=null){registryScope.close();registryScope=null;} }

    @GameTest(template="disposal_power_clear", templateNamespace="moonstation14", timeoutTicks=1600,
            batch="teg_default_feedback_budget")
    @GameTestTags({"teg-default-feedback"})
    public static void characterizePublicDefaultFiniteFeedback(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel(); BlockPos origin=helper.absolutePos(BlockPos.ZERO);
        for(int x=0;x<48;x++)for(int y=0;y<8;y++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        for(int x=0;x<48;x++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),3);
        var end=origin.offset(48,8,48);var lease=FixtureChunkTickets.acquire(helper,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        var owned=new OwnedFixture(level,origin,lease);
        helper.testInfo.addListener(new GameTestListener(){private void clean(net.minecraft.gametest.framework.GameTestInfo i){try{owned.cleanup();}catch(Throwable cleanup){if(i.getError()!=null)i.getError().addSuppressed(cleanup);else i.fail(cleanup);}}public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){}public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);}public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);}public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo a,net.minecraft.gametest.framework.GameTestInfo b,net.minecraft.gametest.framework.GameTestRunner r){clean(a);}});
        FixtureChunkTickets.awaitEntityTicking(helper,lease,100,()->true,"default fueled TEG footprint ticking",()->{
            try {
                var command=level.getServer().getCommands().getDispatcher();var console=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
                require(command.execute("ms14 teg_fixture finite build "+origin.getX()+" "+origin.getY()+" "+origin.getZ(),console)==1,"public finite build command failed");
                var entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow(()->new GameTestAssertException("public fixture ownership missing"));
                var p=TegFueledFixturePlan.layout(origin.offset(22,2,18));var d=p.electrical().devices();
                var core=(AtmosMachineBlockEntity)level.getBlockEntity(d.core());var startup=(PowerDeviceBlockEntity)level.getBlockEntity(d.startupApc());var output=(PowerDeviceBlockEntity)level.getBlockEntity(d.outputApc());
                var substation=(PowerDeviceBlockEntity)level.getBlockEntity(d.substation());
                owned.capture(entry,p,core,startup,output,substation);
                require(entry.mode()==TegFixtureSavedData.Mode.FUELED&&entry.initialized(),"public build did not initialize default finite fixture");
                require(core!=null&&startup!=null&&output!=null,"fixture power devices missing");require(startup.energyJoules()==1_000_000&&output.energyJoules()==0,"initial finite source energy mismatch");
                require(substation!=null,"substation APC missing");
                require(entry.ownedMachines().containsKey(d.core()),"saved core ownership not captured");
                var startupPort=powerPort(startup,CableTier.MV);
                var contacts=com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceContacts(startupPort);
                var feedback=p.combinedCables().stream().filter(c->c.tier()==CableTier.MV&&contacts.contains(new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(c.host(),c.face(),c.tier()))).findFirst().orElseThrow(()->new GameTestAssertException("public startup MV return route absent"));
                require(CableStorage.has(level,feedback.host(),feedback.face(),CableTier.MV),"startup APC MV cable missing");
                var apcPort=powerPort(startup,CableTier.APC);var apcContacts=com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceContacts(apcPort);
                var apcCable=p.combinedCables().stream().filter(c->c.tier()==CableTier.APC&&apcContacts.contains(new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(c.host(),c.face(),c.tier()))).findFirst().orElseThrow(()->new GameTestAssertException("startup APC local APC cable absent"));
                require(CableStorage.has(level,apcCable.host(),apcCable.face(),CableTier.APC),"startup APC local APC face absent");
                awaitTopology(helper,level,origin,lease,p,core,startup,output,substation,owned,0);
            } catch(Throwable ex){failWithCleanup(helper,owned,"default feedback fixture setup",ex);}
        },new BlockPos[]{origin,origin.offset(47,0,47),origin.offset(47,7,47)});
    }

    private static void awaitTopology(GameTestHelper h,ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease,
            TegFueledFixturePlan.Layout p,AtmosMachineBlockEntity core,PowerDeviceBlockEntity startup,PowerDeviceBlockEntity output,
            PowerDeviceBlockEntity substation,OwnedFixture owned,int waited){
        try{
            if(PowerGraphService.topologyReady(level)){
                var a=PowerGraphService.state(level,powerPort(startup,CableTier.MV));var b=PowerGraphService.state(level,powerPort(substation,CableTier.MV));var c=PowerGraphService.state(level,powerPort(output,CableTier.MV));
                if(a.knowledge()==com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph.Knowledge.KNOWN&&b.knowledge()==com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph.Knowledge.KNOWN&&c.knowledge()==com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph.Knowledge.KNOWN){
                    require(a.componentId()==b.componentId()&&b.componentId()==c.componentId(),"startup APC MV binding must share station and output MV component");
                    long start=level.getGameTime();var trace=new Trace(level,core,startup,output,substation,start,core.tegRuntimeState().cumulativeGeneratedJoules());
                    h.runAfterDelay(1,()->poll(h,level,origin,lease,p,core,startup,output,trace,owned,0));return;
                }
            }
            if(waited>=100){
                logTopologyAdmissionFailure(level,origin,p,startup,substation,output,lease,waited);
                throw new GameTestAssertException("startup/station/output MV bindings did not become known after graph settlement");
            }
            h.runAfterDelay(1,()->awaitTopology(h,level,origin,lease,p,core,startup,output,substation,owned,waited+1));
        }catch(Throwable failure){failWithCleanup(h,owned,"default MV route settlement",failure);}
    }

    /** Failure-only observations: diagnostics do not reconcile graph state or load chunks. */
    private static void logTopologyAdmissionFailure(ServerLevel level,BlockPos origin,TegFueledFixturePlan.Layout p,
            PowerDeviceBlockEntity startup,PowerDeviceBlockEntity station,PowerDeviceBlockEntity output,
            FixtureChunkTickets.Lease lease,int waited){
        StringBuilder ports=new StringBuilder();
        for(var device:List.of(startup,station,output)){
            if(ports.length()>0)ports.append(' ');
            if(device==null){ports.append("missing-device");continue;}
            ports.append(device.getBlockPos()).append("[MV=");
            var port=device.ports().stream().filter(candidate->candidate.tier()==CableTier.MV).findFirst().orElse(null);
            if(port==null)ports.append("missing");
            else ports.append("face=").append(port.face()).append(" contacts=").append(com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceContacts(port));
            ports.append(']');
        }
        var graph=PowerGraphService.diagnosticSnapshot(level);
        var footprint=PowerGraphService.diagnosticSnapshot(level,origin.offset(22,2,18),6,CableTier.MV);
        MoonStation14.LOGGER.error("TEG_DEFAULT_BUDGET topology-admission-failure waited={} tick={} ready={} generation={} lease={} ports={} graph={} receiverFootprint={}",
                waited,level.getGameTime(),PowerGraphService.topologyReady(level),PowerGraphService.topologyGeneration(level),lease,ports,graph,footprint);
    }

    private static final class Trace {
        final ServerLevel level;final AtmosMachineBlockEntity core;final PowerDeviceBlockEntity startup,output,substation;final long start;final double cumulative0,startup0,output0,substation0;
        long eventTick=-1,hvTick=-1,apcTick=-1,startupTick=-1,frameTick=-1,frameGeneration=-1,eventCount;double hvJ,hvW,apcWatts,apcInput,apcEnergyBefore,apcEnergyAfter,startupInput,startupOutput,startupEnergyBefore,startupEnergyAfter;String lastEvent="none",receiverFrame="unknown";
        Trace(ServerLevel l,AtmosMachineBlockEntity c,PowerDeviceBlockEntity s,PowerDeviceBlockEntity o,PowerDeviceBlockEntity ss,long t,double g){level=l;core=c;startup=s;output=o;substation=ss;start=t;cumulative0=g;startup0=s.energyJoules();output0=o.energyJoules();substation0=ss.energyJoules();}
    }
    private static void poll(GameTestHelper h,ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease,TegFueledFixturePlan.Layout p,
            AtmosMachineBlockEntity core,PowerDeviceBlockEntity startup,PowerDeviceBlockEntity output,Trace tr,OwnedFixture owned,int elapsed) {
        try {
            var state=core.tegRuntimeState();var event=state.lastCommittedEvent();
            if(event!=null&&event.sampleTick()!=tr.eventTick){tr.eventTick=event.sampleTick();tr.eventCount++;tr.lastEvent="tick="+event.sampleTick()+" TA="+event.temperatureAKelvin()+" TB="+event.temperatureBKelvin()+" Cpa="+event.heatCapacityAJoulesPerKelvin()+" Cpb="+event.heatCapacityBJoulesPerKelvin()+" maxWork="+event.maximumWorkJoules()+" eff="+event.effectiveEfficiency()+" configured="+event.configuredEfficiency()+" factor="+event.powerFactor()+" dt="+event.intervalSeconds()+" paid="+event.paidWorkCommitted()+" offer="+event.offerPublished()+" generated="+(state.lastPaidSampleTick()==event.sampleTick()?state.lastGeneratedJoules():"stale");}
            if(core.hvSampleTick()!=tr.hvTick){tr.hvTick=core.hvSampleTick();tr.hvJ=core.hvDeliveredJoules();tr.hvW=core.hvDeliveredWatts();}
            var meter=PowerRuntime.apcOutputObservation(level,output);var sm=PowerRuntime.apcOutputObservation(level,startup);long generation=PowerGraphService.topologyGeneration(level);
            var coreRx=receiver(level,core);var pumpARx=receiver(level,machineEntity(level,p.core().offset(-8,0,-1)));var pumpBRx=receiver(level,machineEntity(level,p.core().offset(8,0,1)));var heaterRx=receiver(level,machineEntity(level,p.core().offset(-11,0,-2)));
            if(meter.known()&&sm.known()&&meter.sampleTick()==sm.sampleTick()
                    &&meter.topologyGeneration()==generation&&sm.topologyGeneration()==generation
                    &&coreRx.known()&&pumpARx.known()&&pumpBRx.known()&&heaterRx.known()
                    &&coreRx.sampleTick()==meter.sampleTick()&&pumpARx.sampleTick()==meter.sampleTick()&&pumpBRx.sampleTick()==meter.sampleTick()&&heaterRx.sampleTick()==meter.sampleTick()
                    &&meter.sampleTick()!=tr.frameTick){tr.frameTick=meter.sampleTick();tr.frameGeneration=generation;tr.apcTick=meter.sampleTick();tr.apcInput=meter.inputWatts();tr.apcWatts=meter.outputWatts();tr.apcEnergyBefore=meter.energyBeforeJoules();tr.apcEnergyAfter=meter.energyAfterJoules();tr.startupTick=sm.sampleTick();tr.startupInput=sm.inputWatts();tr.startupOutput=sm.outputWatts();tr.startupEnergyBefore=sm.energyBeforeJoules();tr.startupEnergyAfter=sm.energyAfterJoules();tr.receiverFrame="core="+coreRx+" pumpA="+pumpARx+" pumpB="+pumpBRx+" heater="+heaterRx;}
            if(elapsed%100==0||elapsed==0)MoonStation14.LOGGER.info("TEG_DEFAULT_BUDGET elapsed={} tick={} generatedDeltaJ={} generatedAvgW={} lastEvent={} eventCount={} lastAssignedHvJ={} lastAssignedHvW={} hvTick={} matchedAPCTick={} graphGeneration={} outputAPC=input:{}/output:{} startupAPC=input:{}/output:{} receiverFrames={} startupJ={} outputJ={} substationEnergyJ={} substationDeltaJ={} substationInput=UNAVAILABLE substationOutput=UNAVAILABLE demands={} graph={}",elapsed,level.getGameTime(),state.cumulativeGeneratedJoules()-tr.cumulative0,(state.cumulativeGeneratedJoules()-tr.cumulative0)/Math.max(1,(level.getGameTime()-tr.start)/20d),tr.lastEvent,tr.eventCount,tr.hvJ,tr.hvW,tr.hvTick,tr.frameTick,tr.frameGeneration,tr.apcInput,tr.apcWatts,tr.startupInput,tr.startupOutput,tr.receiverFrame,startup.energyJoules(),output.energyJoules(),tr.substation.energyJoules(),tr.substation.energyJoules()-tr.substation0,"core="+coreRx+" pumpA="+pumpARx+" pumpB="+pumpBRx+" heater="+heaterRx,PowerGraphService.diagnosticSnapshot(level));
            if(elapsed>=1200){require(PowerGraphService.topologyReady(level),"power graph never became known");require(tr.eventCount>0,"missing committed-event measurement");require(tr.eventTick>=0&&tr.hvTick>=0,"missing source-HV measurement");require(Double.isFinite(state.cumulativeGeneratedJoules())&&Double.isFinite(tr.hvJ),"non-finite runtime measurement");require(core.hvAllocationKnown(),"source HV allocation measurement unknown");require(meter.known()&&sm.known()&&meter.sampleTick()==sm.sampleTick()&&meter.sampleTick()==tr.frameTick&&meter.topologyGeneration()==PowerGraphService.topologyGeneration(level)&&tr.frameGeneration==PowerGraphService.topologyGeneration(level)&&coreRx.known()&&pumpARx.known()&&pumpBRx.known()&&heaterRx.known()&&coreRx.sampleTick()==tr.frameTick&&pumpARx.sampleTick()==tr.frameTick&&pumpBRx.sampleTick()==tr.frameTick&&heaterRx.sampleTick()==tr.frameTick,"no fresh coherent current-generation APC frame");
                MoonStation14.LOGGER.info("TEG_DEFAULT_BUDGET END ticks={} generatedDeltaJ={} generatedAvgW={} lastAssignedHvJ={} lastAssignedHvW={} eventCount={} lastEvent={} hvTick={} matchedAPCTick={} graphGeneration={} outputAPC=input:{}/output:{} storage:{}/{} startupAPC=input:{}/output:{} storage:{}/{} receiverFrames={} startupJ={} startupDeltaJ={} outputJ={} outputDeltaJ={} substationEnergyJ={} substationDeltaJ={} substationInput=UNAVAILABLE substationOutput=UNAVAILABLE clocks=generated-average-vs-single-matched-APC-frame characterizationOnly=true",level.getGameTime()-tr.start,state.cumulativeGeneratedJoules()-tr.cumulative0,(state.cumulativeGeneratedJoules()-tr.cumulative0)/((level.getGameTime()-tr.start)/20d),tr.hvJ,tr.hvW,tr.eventCount,tr.lastEvent,tr.hvTick,tr.frameTick,tr.frameGeneration,tr.apcInput,tr.apcWatts,tr.apcEnergyBefore,tr.apcEnergyAfter,tr.startupInput,tr.startupOutput,tr.startupEnergyBefore,tr.startupEnergyAfter,tr.receiverFrame,startup.energyJoules(),startup.energyJoules()-tr.startup0,output.energyJoules(),output.energyJoules()-tr.output0,tr.substation.energyJoules(),tr.substation.energyJoules()-tr.substation0);
                owned.cleanup();h.succeed();return;}
            h.runAfterDelay(1,()->poll(h,level,origin,lease,p,core,startup,output,tr,owned,elapsed+1));
        }catch(Throwable ex){failWithCleanup(h,owned,"TEG_DEFAULT_BUDGET missing/invalid measurement",ex);}
    }
    private static com.juicyslew.moonstation14.ms14.power.topology.DevicePort powerPort(PowerDeviceBlockEntity device,CableTier tier){return device.ports().stream().filter(p->p.tier()==tier).findFirst().orElseThrow(()->new GameTestAssertException("missing "+tier+" device port at "+device.getBlockPos()));}
    private static AtmosMachineBlockEntity machineEntity(ServerLevel l,BlockPos p){return l.getBlockEntity(p) instanceof AtmosMachineBlockEntity a?a:null;}
    private record Receiver(boolean known,long sampleTick,String detail) { }
    private static Receiver receiver(ServerLevel l,AtmosMachineBlockEntity a){if(a==null)return new Receiver(false,-1,"missing");var meter=PowerRuntime.apcPowerMeter(l,a);long tick=a.apcSampleTick();return new Receiver(meter.known(),tick,"demand="+a.apcPowerDemandWatts()+" delivered="+(meter.known()?meter.deliveredWatts():"unknown")+" known="+meter.known()+" sample="+tick);}
    private static void failWithCleanup(GameTestHelper helper,OwnedFixture owned,String phase,Throwable primary){try{owned.cleanup();}catch(Throwable cleanup){primary.addSuppressed(cleanup);}helper.fail(phase+": "+primary.getMessage());}
    private static TegFixtureSavedData.Entry entry(ServerLevel l,BlockPos o){return TegFixtureSavedData.existing(l).flatMap(d->d.entry(o)).orElse(null);}
    private static final class OwnedFixture {
        final ServerLevel level;final BlockPos origin;final FixtureChunkTickets.Lease lease;TegFixtureSavedData.Entry entry;TegFueledFixturePlan.Layout plan;
        final java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> states=new java.util.LinkedHashMap<>();
        final java.util.Map<BlockPos,AtmosMachineBlockEntity> machines=new java.util.LinkedHashMap<>();
        PowerDeviceBlockEntity startup,output,substation;boolean cleaned;
        OwnedFixture(ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease){this.level=level;this.origin=origin;this.lease=lease;}
        void capture(TegFixtureSavedData.Entry entry,TegFueledFixturePlan.Layout plan,AtmosMachineBlockEntity core,PowerDeviceBlockEntity startup,PowerDeviceBlockEntity output,PowerDeviceBlockEntity substation){
            this.entry=entry;this.plan=plan;this.startup=startup;this.output=output;this.substation=substation;
            for(var pos:plan.allOwnedCells())states.put(pos.immutable(),level.getBlockState(pos));
            for(var owner:entry.ownedGas().keySet())states.putIfAbsent(owner.position(),level.getBlockState(owner.position()));
            for(var pos:entry.ownedMachines().keySet())if(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine
                    &&entry.ownedMachines().get(pos).equals(machine.placementIdentity()))machines.put(pos.immutable(),machine);
            if(core!=null&&machines.get(core.getBlockPos())!=core)throw new GameTestAssertException("captured core owner identity mismatch");
        }
        void cleanup(){if(cleaned)return;cleaned=true;try{if(entry!=null&&plan!=null){
            for(var cable:plan.combinedCables())if(CableStorage.has(level,cable.host(),cable.face(),cable.tier()))CableStorage.remove(level,cable.host(),cable.face(),cable.tier());
            machines.forEach((pos,be)->{if(level.getBlockEntity(pos)==be&&entry.ownedMachines().get(pos).equals(be.placementIdentity()))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);});
            states.forEach((pos,state)->{
                if(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity)return;
                if(hasOwnedGasAt(pos)){
                    if(exactGasOwnerAt(pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                    else logActiveOwnerSkip(pos,state);
                }else if(level.getBlockState(pos).equals(state))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            });
            var d=plan.electrical().devices();if(level.getBlockEntity(d.startupApc())==startup)level.setBlock(d.startupApc(),Blocks.AIR.defaultBlockState(),3);
            if(level.getBlockEntity(d.outputApc())==output)level.setBlock(d.outputApc(),Blocks.AIR.defaultBlockState(),3);if(level.getBlockEntity(d.substation())==substation)level.setBlock(d.substation(),Blocks.AIR.defaultBlockState(),3);
            for(var pair:entry.ownedGas().entrySet()){var current=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level).map(dta->dta.snapshot().get(pair.getKey()).orElse(null)).orElse(null);
                if(current!=null&&current.active()&&current.incarnation().equals(pair.getValue()))throw new IllegalStateException("owned gas incarnation remains active after ordinary block removal node="+pair.getKey()+" expected="+pair.getValue()+" live="+current.incarnation()+" blockNow="+level.getBlockState(pair.getKey().position())+" capturedBlock="+states.get(pair.getKey().position())+" blockEntity="+level.getBlockEntity(pair.getKey().position()));}
        }}finally{lease.close();}}
        boolean hasOwnedGasAt(BlockPos pos){return entry.ownedGas().keySet().stream().anyMatch(key->key.position().equals(pos)&&!key.devicePort());}
        boolean exactGasOwnerAt(BlockPos pos){
            var inventory=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level).map(com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData::snapshot).orElse(null);
            if(inventory==null)return false;boolean matched=false;
            for(var owner:entry.ownedGas().entrySet()){
                var key=owner.getKey();if(!key.position().equals(pos)||key.devicePort())continue;
                var live=inventory.get(key).orElse(null);if(live!=null&&!live.incarnation().equals(owner.getValue()))return false;
                int lane=key.layer()==com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY?com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.PRIMARY:key.layer()==com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY?com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.SECONDARY:com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity.TERTIARY;
                boolean descriptor=level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity visual
                        &&level.getBlockState(pos).is(com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get())&&visual.hasLayer(lane)
                        &&visual.functionalIdentity(lane).filter(owner.getValue()::equals).isPresent();
                if(!descriptor)descriptor=com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallSegment.matchingLive(level,pos,key.layer()).filter(record->record.id().equals(owner.getValue())).isPresent();
                if(descriptor)matched=true;
            }
            return matched;
        }
        void logActiveOwnerSkip(BlockPos pos,net.minecraft.world.level.block.state.BlockState expected){
            var data=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level).orElse(null);if(data==null)return;
            for(var owner:entry.ownedGas().entrySet())if(owner.getKey().position().equals(pos)){var live=data.snapshot().get(owner.getKey()).orElse(null);if(live!=null&&live.active()&&live.incarnation().equals(owner.getValue()))
                MoonStation14.LOGGER.warn("TEG_DEFAULT_BUDGET cleanup-skip-active-owner pos={} stateNow={} capturedState={} node={} expectedIncarnation={} liveIncarnation={} blockEntity={}",pos,level.getBlockState(pos),expected,owner.getKey(),owner.getValue(),live.incarnation(),level.getBlockEntity(pos));}
        }
    }
    private static void require(boolean ok,String reason){if(!ok)throw new GameTestAssertException(reason);}
}
