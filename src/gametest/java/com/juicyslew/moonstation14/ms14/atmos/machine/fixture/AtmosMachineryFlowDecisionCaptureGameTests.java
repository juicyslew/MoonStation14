package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineAppearance;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineCadence;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.GasWorkDecision;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationGasRuntime;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;

/** One bounded, native capture of atomic pump/mixer and paid ventilation decisions. */
@PrefixGameTestTemplate(false)
public final class AtmosMachineryFlowDecisionCaptureGameTests {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AtmosMachineryFlowDecisionCaptureGameTests.class);
    private static final List<AtmosMachineryFixturePlan.Kind> SELECTED = List.of(
            AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP,
            AtmosMachineryFixturePlan.Kind.VOLUME_PUMP,
            AtmosMachineryFixturePlan.Kind.MIXER);
    private record ExpectedVent(BlockPos position, java.util.UUID deviceId,
                                com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind kind) { }
    private enum CaptureMode { DIAGNOSTIC, CONTINUITY }
    private AtmosMachineryFlowDecisionCaptureGameTests() { }

    @GameTest(template="atmos_machinery_lab_clear", templateNamespace="moonstation14", timeoutTicks=1200)
    @GameTestTags({"atmos-lab-flow-decision-capture"})
    public static void captureAtomicFlowDecisions(GameTestHelper helper) {
        runCapture(helper,CaptureMode.DIAGNOSTIC);
    }

    /** Post-fix regression: setup readiness, funded-event priming, fixed steady window and bounded quiet-stop phase. */
    // Bounded phases: 100 lease + 400 physical/power + 360 positive readiness + derived priming
    // + 240 steady + 40 stop + bounded callback scheduling margin.
    @GameTest(template="atmos_machinery_lab_clear", templateNamespace="moonstation14", batch="atmos_lab_continuous_flow", timeoutTicks=1400)
    @GameTestTags({"atmos-lab-continuous-flow","atmos-machinery-fixture-runtime","atmos-device-work"})
    public static void verifyContinuousFundedFlowAndQuietStop(GameTestHelper helper) {
        runCapture(helper,CaptureMode.CONTINUITY);
    }

    private static void runCapture(GameTestHelper helper,CaptureMode mode) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos end = origin.offset(AtmosMachineryFixturePlan.WIDTH, AtmosMachineryFixturePlan.HEIGHT,
                AtmosMachineryFixturePlan.DEPTH);
        var lease = FixtureChunkTickets.acquire(helper, new net.minecraft.world.phys.AABB(origin.getX(), origin.getY(), origin.getZ(),
                end.getX(), end.getY(), end.getZ()));
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 100, () -> true,
                "flow decision capture footprint entity-ticking", () -> {
                    for (int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++) for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)
                        for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++) level.setBlock(origin.offset(x,y,z), Blocks.AIR.defaultBlockState(),3);
                    if (!AtmosMachineryFixtureService.build(level,origin).startsWith("Built nine-machine "))
                        throw new GameTestAssertException("flow capture could not build shared laboratory fixture");
                    var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
                    var circuits=AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints();
                    var capture=new Capture(helper,level,origin,lease,plan,circuits,mode);
                    PowerFixtureReadiness.await(helper,level,400,
                            () -> finiteSelectedRooms(level,origin,circuits)
                                    && selectedPowerReady(level,origin,plan)
                                    && capture.expectedVentDevicesOwned(),
                            () -> "selected lab rooms/power/vent identities unresolved; rooms="+finiteSelectedRooms(level,origin,circuits)
                                    +" vents="+capture.missingVentIdentities(), capture::startAndPoll);
                }, origin, origin.offset(AtmosMachineryFixturePlan.WIDTH-1,AtmosMachineryFixturePlan.HEIGHT-1,
                        AtmosMachineryFixturePlan.DEPTH-1));
    }

    private static boolean finiteSelectedRooms(ServerLevel level,BlockPos origin,List<AtmosMachineryRoomCircuitPlan.Endpoint> endpoints) {
        var atmosphere=AtmosphereService.INSTANCE;
        return endpoints.stream().filter(e->SELECTED.contains(e.station())).flatMap(e->e.room().roomCells().stream())
                .allMatch(p->atmosphere.finiteRoomEndpointForTrustedSimulation(level,p).isPresent());
    }
    private static boolean selectedPowerReady(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        for(var kind:SELECTED) {
            if(!(level.getBlockEntity(origin.offset(plan.station(kind))) instanceof AtmosMachineBlockEntity machine)
                    || machine.resolvedProfile()==null||machine.placementIdentity()==null||machine.gasPorts().isEmpty()) return false;
            if(kind!=AtmosMachineryFixturePlan.Kind.MIXER&&!machine.apcAllocationKnown())return false;
        }
        return true;
    }

    private static final class Capture {
        final GameTestHelper h; final ServerLevel level; final BlockPos origin; final FixtureChunkTickets.Lease lease;
        final AtmosMachineryFixturePlan plan; final List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits;
        final CaptureMode mode;
        final Map<BlockPos,ExpectedVent> expectedVents=new LinkedHashMap<>();
        final Map<AtmosMachineryFixturePlan.Kind,Long> lastSequence=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Long> events=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Long> positive=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Long> zero=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Long> rejected=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<BlockPos,Long> ventSequences=new HashMap<>();
        final Map<BlockPos,Long> ventEvents=new HashMap<>();
        final Map<BlockPos,Long> ventPositive=new HashMap<>();
        final Map<BlockPos,Long> ventNoPlan=new HashMap<>();
        final Map<BlockPos,Long> ventKnownEvents=new HashMap<>();
        final Map<BlockPos,Long> ventUnknownEvents=new HashMap<>();
        final Map<BlockPos,Double> ventSeconds=new HashMap<>();
        final Map<BlockPos,Double> ventAccepted=new HashMap<>();
        final Map<BlockPos,Double> ventSpentAtLastReceipt=new HashMap<>();
        final Map<BlockPos,Long> ventLastReceiptTick=new HashMap<>();
        final Map<BlockPos,Long> ventFirstEventTick=new HashMap<>();
        final Map<BlockPos,Long> ventLastEventTick=new HashMap<>();
        final Map<AtmosMachineryFixturePlan.Kind,Long> machineLastCommittedTick=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Long> machineFirstCommittedTick=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Double> machineBaselineMoved=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Double> machineBaselinePaid=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Long> machineBaselineWorkEvents=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Double> machinePaidInputs=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,java.util.UUID> machinePlacements=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,String> machineProfiles=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,List<String>> machineGasOwners=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Double> stopMoved=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Double> stopPaid=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        final Map<AtmosMachineryFixturePlan.Kind,Long> stopWorkEvents=new EnumMap<>(AtmosMachineryFixturePlan.Kind.class);
        long elapsed; long readyElapsed; long readyPositiveMask; boolean windowStarted;
        boolean stopPhase; boolean priming; long primingTicks; long primingElapsed; long stopElapsed;
        long windowStartSampleTick=-1,windowEndSampleTick=-1;
        double baselineTotal=Double.NaN; long baselineRevision=-1;
        Capture(GameTestHelper h,ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease,
                AtmosMachineryFixturePlan plan,List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,CaptureMode mode) {
            this.h=h;this.level=level;this.origin=origin;this.lease=lease;this.plan=plan;this.circuits=circuits;this.mode=mode;
            for(var kind:SELECTED){events.put(kind,0L);positive.put(kind,0L);zero.put(kind,0L);rejected.put(kind,0L);lastSequence.put(kind,0L);machinePaidInputs.put(kind,0d);}
            var positions=circuits.stream().filter(e->SELECTED.contains(e.station())).flatMap(e->e.ventHosts().stream())
                    .map(BlockPos::immutable).collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var owners=saved.roomDeviceOwners().stream().collect(java.util.stream.Collectors.toMap(
                    owner->owner.position().immutable(),owner->owner,(left,right)->left));
            if(!owners.keySet().containsAll(positions))throw new GameTestAssertException(
                    "selected circuit hosts lack saved ventilation identity records: "+positions.stream().filter(p->!owners.containsKey(p)).toList());
            for(BlockPos pos:positions){
                if(!(level.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity device))
                    throw new GameTestAssertException("selected ventilation device missing while resolving owner identity: "+pos);
                var owner=owners.get(pos);
                if(owner.deviceId()==null)throw new GameTestAssertException("selected ventilation owner has no stable identity: "+pos);
                expectedVents.put(pos,new ExpectedVent(pos.immutable(),owner.deviceId(),device.kind()));
                ventSequences.put(pos,0L);ventEvents.put(pos,0L);ventPositive.put(pos,0L);ventNoPlan.put(pos,0L);
                ventKnownEvents.put(pos,0L);ventUnknownEvents.put(pos,0L);ventSeconds.put(pos,0d);ventAccepted.put(pos,0d);
                ventSpentAtLastReceipt.put(pos,device.actualPaidVentilationSpentJoules());
                ventLastReceiptTick.put(pos,device.lastPaidVentilationEvent()==null?-1L:device.lastPaidVentilationEvent().eventTick());
            }
        }
        boolean expectedVentDevicesOwned(){return missingVentIdentities().isEmpty();}
        List<String> machineIdentityFailures(){
            var failures=new ArrayList<String>();
            for(var kind:SELECTED){
                BlockPos pos=origin.offset(plan.station(kind));var current=level.getBlockEntity(pos);
                if(!(current instanceof AtmosMachineBlockEntity machine)||machine.isRemoved()
                        ||!Objects.equals(machinePlacements.get(kind),machine.placementIdentity())
                        ||!Objects.equals(machineProfiles.get(kind),machine.profileIdentity())){
                    failures.add(kind+"@"+pos+" placement="+machinePlacements.get(kind)+" profile="+machineProfiles.get(kind));continue;
                }
                var owners=machine.gasPorts().stream().map(p->p.id()+"@"+p.layer()+"#"+p.incarnation()).sorted().toList();
                if(!owners.equals(machineGasOwners.get(kind)))failures.add(kind+"@"+pos+" portOwners="+owners+" expected="+machineGasOwners.get(kind));
            }
            return List.copyOf(failures);
        }
        List<String> missingVentMeters(){
            var missing=new ArrayList<String>();
            for(var expected:expectedVents.values()){
                var current=level.getBlockEntity(expected.position());
                if(!(current instanceof VentilationDeviceBlockEntity vent)||vent.prototype()==null){
                    missing.add(expected.position()+"#"+expected.deviceId()+" profile/meter unavailable");continue;
                }
                var meter=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ventilationMeter(level,vent);
                if(!meter.known()||!Double.isFinite(meter.deliveredWatts())
                        ||meter.deliveredWatts()<vent.prototype().apcPowerReceiver().requestedWatts())
                    missing.add(expected.position()+"#"+expected.deviceId()+" meter="+meter);
            }
            return List.copyOf(missing);
        }
        List<String> missingVentIdentities(){
            var missing=new ArrayList<String>();
            for(var expected:expectedVents.values())if(!(level.getBlockEntity(expected.position()) instanceof VentilationDeviceBlockEntity device)
                    ||device.isRemoved()||!expected.deviceId().equals(device.deviceId())||device.kind()!=expected.kind())
                missing.add(expected.position()+"#"+expected.deviceId()+"/"+expected.kind());
            return List.copyOf(missing);
        }
        boolean knownActualDecision(ExpectedVent expected,VentilationGasRuntime.TransferObservation.Decision d){
            return d!=null&&expected.deviceId().equals(d.deviceId())&&expected.position().equals(d.devicePos())
                    &&expected.kind().name().equals(d.kind())&&d.configurationAvailable()
                    &&Double.isFinite(d.paidSeconds())&&d.paidSeconds()>0&&d.pipeBefore()!=null&&d.roomBefore()!=null
                    &&Double.isFinite(d.pipePressureKpa())&&Double.isFinite(d.roomPressureKpa())
                    &&d.pipeNodeIdentity()!=null&&!d.pipeNodeIdentity().isBlank()&&!d.pipeNodeIdentity().equals("unavailable");
        }
        boolean expectedVentDecisionsKnown(){
            if(!expectedVentDevicesOwned()||!missingVentMeters().isEmpty())return false;
            for(var expected:expectedVents.values()){
                var device=(VentilationDeviceBlockEntity)level.getBlockEntity(expected.position());
                if(!knownActualDecision(expected,device.transferObservation().lastDecision()))return false;
            }
            return true;
        }
        void assertVentBudgetsNeverOverspent(){
            for(var expected:expectedVents.values()){
                var vent=(VentilationDeviceBlockEntity)level.getBlockEntity(expected.position());
                double granted=vent.actualPaidVentilationGrantedJoules(),spent=vent.actualPaidVentilationSpentJoules();
                if(!Double.isFinite(granted)||!Double.isFinite(spent)||spent>granted+1e-8)
                    fail("vent actual cumulative paid-work debit exceeds delivered grants: "+expected.position()+" granted="+granted+" spent="+spent);
            }
        }
        void assertMachinePresentationAndEligibility(){
            for(var kind:SELECTED){
                BlockPos pos=origin.offset(plan.station(kind));
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(pos);
                var profile=machine.resolvedProfile();var runtime=machine.deviceRuntimeState();
                boolean enabled=machine.settingsSnapshot().enabled().orElse(false);
                boolean powered=profile.configuredReceiverLoadWatts()>0&&machine.apcAllocationKnown()
                        &&machine.apcAllocatedWatts()>=profile.configuredReceiverLoadWatts();
                String visual=AtmosMachineAppearance.project(profile,powered,enabled,runtime.lastPumpBlocked(),null);
                int expectedDisplay=AtmosMachineAppearance.nativeDisplay(profile,visual,powered);
                int actualDisplay=machine.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY);
                boolean expectedAudio=com.juicyslew.moonstation14.ms14.atmos.machine.AtmosAmbientEligibility.eligible(
                        profile,enabled,runtime.lastMoles(),powered,actualDisplay);
                if(actualDisplay!=expectedDisplay||machine.ambientEligible()!=expectedAudio)
                    fail("actual native display/ambient state disagrees with current work authority for "+kind+
                            " display="+actualDisplay+" expected="+expectedDisplay+" eligible="+machine.ambientEligible()+" expected="+expectedAudio+
                            " lastAccepted="+runtime.lastMoles()+" tick="+level.getGameTime());
            }
        }
        void beginPriming(){
            priming=true;primingElapsed=0;
            LOGGER.info("LAB_FLOW_CONTINUITY event=priming_start worldTick={} commonCycleTicks={} billingTicks={} resolvedMachineIntervals={} expectedVentIdentities={} purpose=funded-cadence-buffer-only gasRefill=false",
                    level.getGameTime(),primingTicks,com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.TICKS_PER_SOLVE,
                    machineIntervalTicks(),expectedVentIdentities());
        }
        List<String> missingVentDecisionIdentities(){
            var missing=new ArrayList<String>();
            for(var expected:expectedVents.values()){
                var current=level.getBlockEntity(expected.position());
                var d=current instanceof VentilationDeviceBlockEntity device?device.transferObservation().lastDecision():null;
                if(!knownActualDecision(expected,d))missing.add(expected.position()+"#"+expected.deviceId()+" latest="+d);
            }
            return List.copyOf(missing);
        }
        void startAndPoll() {
            for(var kind:SELECTED) {
                String started=AtmosMachineryFixtureService.start(level,origin,kind);
                if(!started.startsWith("Started ")) fail("selected demo start refused for "+kind+": "+started);
            }
            for(var kind:SELECTED){
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                machinePlacements.put(kind,machine.placementIdentity());machineProfiles.put(kind,machine.profileIdentity());
                machineGasOwners.put(kind,machine.gasPorts().stream().map(p->p.id()+"@"+p.layer()+"#"+p.incarnation()).sorted().toList());
            }
            if(mode==CaptureMode.CONTINUITY){
                primingTicks=commonPrimingTicks(com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.TICKS_PER_SOLVE,
                        List.copyOf(machineIntervalTicks().values()));
                LOGGER.info("LAB_FLOW_CONTINUITY event=priming_begin billingTicks={} machineIntervals={} derivedCommonTicks={} expectedVentIdentities={} noGasRefill=true",
                        com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.TICKS_PER_SOLVE,
                        machineIntervalTicks(),
                        primingTicks,expectedVentIdentities());
            }
            poll();
        }
        void poll(){h.runAfterDelay(1,()->{
            try {
                if(!lease.entityTicking(origin)||!lease.entityTicking(origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1)))
                    fail("fixture lease stopped ticking during decision capture");
                if(!expectedVentDevicesOwned())fail("expected selected ventilation identity missing/replaced: "+missingVentIdentities());
                var identityFailures=machineIdentityFailures();
                if(!identityFailures.isEmpty())fail("selected machine/profile/gas-port ownership changed: "+identityFailures);
                if(mode==CaptureMode.CONTINUITY){
                    var missingMeters=missingVentMeters();
                    if((priming||windowStarted)&&!missingMeters.isEmpty())failMeterDiagnostics(missingMeters);
                    assertMachinePresentationAndEligibility();
                    assertVentBudgetsNeverOverspent();
                }
                for(var kind:SELECTED) captureMachine(kind);
                captureVents();
                if(stopPhase){
                    stopElapsed++;
                    assertQuietStopCounters();
                    if(stopElapsed>=40)finishQuietStop();
                    else poll();
                } else if(priming){
                    if(++primingElapsed>=primingTicks){
                        LOGGER.info("LAB_FLOW_CONTINUITY event=priming_complete ticks={} worldTick={} sampleTick={} noGasRefill=true",primingElapsed,
                                level.getGameTime(),com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.currentSampleTick(level));
                        beginWindow();
                    }
                    poll();
                } else if(!windowStarted) {
                    readyElapsed++;
                    long expected=(1L<<SELECTED.get(0).ordinal())|(1L<<SELECTED.get(1).ordinal())|(1L<<SELECTED.get(2).ordinal());
                    if(readyPositiveMask==expected&&expectedVentDecisionsKnown()){
                        if(mode==CaptureMode.CONTINUITY)beginPriming();else beginWindow();
                    }
                    else if(readyElapsed>=360)fail("decision readiness timed out; positiveMask="+readyPositiveMask+" events="+events+" zero="+zero+" rejected="+rejected
                            +" missingVentDecisions="+missingVentDecisionIdentities()+" missingVentIdentities="+missingVentIdentities()+" missingMeters="+missingVentMeters());
                    if(priming||!windowStarted||elapsed==0)poll();
                } else if(++elapsed>=240) finish();
                else poll();
            } catch(RuntimeException failure){fail("native decision capture failed: "+failure.getMessage());}
        });}
        void beginWindow(){
            priming=false;windowStarted=true;windowStartSampleTick=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.currentSampleTick(level);
            windowEndSampleTick=windowStartSampleTick+240;
            baselineRevision=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().revision();
            baselineTotal=systemMoles(level,origin);
            events.replaceAll((k,v)->0L);positive.replaceAll((k,v)->0L);zero.replaceAll((k,v)->0L);rejected.replaceAll((k,v)->0L);
            ventEvents.replaceAll((p,v)->0L);ventPositive.replaceAll((p,v)->0L);ventNoPlan.replaceAll((p,v)->0L);
            ventKnownEvents.replaceAll((p,v)->0L);ventUnknownEvents.replaceAll((p,v)->0L);
            ventSeconds.replaceAll((p,v)->0d);ventAccepted.replaceAll((p,v)->0d);
            ventFirstEventTick.clear();ventLastEventTick.clear();
            ventSpentAtLastReceipt.clear();ventLastReceiptTick.clear();
            for(var expected:expectedVents.values()){
                var vent=(VentilationDeviceBlockEntity)level.getBlockEntity(expected.position());
                ventSpentAtLastReceipt.put(expected.position(),vent.actualPaidVentilationSpentJoules());
                var receipt=vent.lastPaidVentilationEvent();
                ventLastReceiptTick.put(expected.position(),receipt==null?-1L:receipt.eventTick());
            }
            machineLastCommittedTick.clear();machineFirstCommittedTick.clear();
            for(var kind:SELECTED){
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                machineBaselineMoved.put(kind,machine.committedMovedMoles());
                machineBaselinePaid.put(kind,machine.committedPaidJoules());
                machineBaselineWorkEvents.put(kind,machine.committedWorkEvents());
                machinePaidInputs.put(kind,0d);
            }
            LOGGER.info("LAB_FLOW_DECISION event=begin tag=atmos-lab-flow-decision-capture positiveReadiness=true expectedVentIdentities={} worldTick={} sampleTick={} inventoryRevision={} finiteRoomsMoles={} pipeMoles={} totalMoles={} machineIntervals=resolved-profile-deviceIntervalSeconds ventPaidSecondsSource=funded-event-duration ventPowerSource=actual-paid-credit-funded-atmos-event",
                    expectedVentIdentities(),level.getGameTime(),com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.currentSampleTick(level),baselineRevision,
                    roomMoles(level,origin),pipeMoles(level),baselineTotal);
        }
        void captureMachine(AtmosMachineryFixturePlan.Kind kind){
            BlockPos pos=origin.offset(plan.station(kind));
            if(!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity)||
                    ((AtmosMachineBlockEntity)level.getBlockEntity(pos)).isRemoved()) {
                fail("selected machine missing: "+kind);
                return;
            }
            AtmosMachineBlockEntity machine=(AtmosMachineBlockEntity)level.getBlockEntity(pos);
            GasWorkDecision d=machine.lastFlowDecision();
            if(d==null||d.sequence()==lastSequence.get(kind))return;
            lastSequence.put(kind,d.sequence());events.merge(kind,1L,Long::sum);
            boolean moved=d.outcome()==GasWorkDecision.Outcome.COMMITTED&&d.acceptedMoles()>0;
            if(moved)positive.merge(kind,1L,Long::sum);else if(d.outcome()==GasWorkDecision.Outcome.NO_PLAN||d.outcome()==GasWorkDecision.Outcome.UNCHANGED)zero.merge(kind,1L,Long::sum);
            else rejected.merge(kind,1L,Long::sum);
            if(moved)readyPositiveMask|=1L<<kind.ordinal();
            if(mode==CaptureMode.CONTINUITY&&windowStarted&&!stopPhase)
                machinePaidInputs.merge(kind,d.actualPaidInputJoules(),Double::sum);
            boolean enabled=machine.settingsSnapshot().enabled().orElse(false);
            boolean eligible=machine.ambientEligible();
            boolean sameDecisionTick=level.getGameTime()==d.worldTick();
            boolean expectedEligible=com.juicyslew.moonstation14.ms14.atmos.machine.AtmosAmbientEligibility.eligible(
                    machine.resolvedProfile(),enabled,d.acceptedMoles(),machine.apcAllocationKnown()
                            &&machine.apcAllocatedWatts()>=machine.resolvedProfile().configuredReceiverLoadWatts(),
                    machine.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY));
            boolean eligibilityMatches=sameDecisionTick&&eligible==expectedEligible;
            if(sameDecisionTick&&machine.resolvedProfile().ambientAudio().isPresent()&&!eligibilityMatches)
                fail("same-tick decision/ambient eligibility mismatch for "+kind+" seq="+d.sequence());
            if(mode==CaptureMode.DIAGNOSTIC)LOGGER.info("LAB_FLOW_DECISION event=machine sequence={} kind={} machinePos={} identity={} worldTick={} pollWorldTick={} sampleTick={} intervalTicks={} intervalSeconds={} status={} enabledAfter={} ambientEligibleAfter={} expectedAmbientEligible={} eligibilityComparableSameTick={} eligibilityMatches={} planPresent={} plannedMoles={} outcome={} acceptedMoles={} actualPaidInputJ={} targetKPa={} ratio={} rateM3s={} lowerKPa={} upperKPa={} overclockBoundKPa={} pumpSpeedup={} roots={} inputs={} plannedAfter={} committedAfter={} resolvedIntervalTicks={} resolvedIntervalSeconds={} roomReadings={}",
                    d.sequence(),kind,pos,machine.placementIdentity(),d.worldTick(),level.getGameTime(),d.sampleTick(),d.intervalTicks(),d.intervalSeconds(),d.outcome(),enabled,eligible,expectedEligible,sameDecisionTick,eligibilityMatches,d.planPresent(),d.plannedMoles(),d.outcome(),d.acceptedMoles(),d.actualPaidInputJoules(),
                    d.targetKpa(),d.ratio(),d.rateM3PerSecond(),d.lowerBoundKpa(),d.upperBoundKpa(),d.overclockBoundKpa(),d.pumpSpeedup(),d.roots(),inputs(d.inputs()),d.plannedAfter(),d.committedAfter(),
                    machine.resolvedProfile().simulation().intervalTicks(),machine.resolvedProfile().simulation().deviceIntervalSeconds(),stationRooms(kind));
            if(mode==CaptureMode.CONTINUITY&&windowStarted&&!stopPhase)validateSteadyMachineEvent(kind,machine,d);
        }
        void validateSteadyMachineEvent(AtmosMachineryFixturePlan.Kind kind,AtmosMachineBlockEntity machine,GasWorkDecision d){
            if(d.outcome()!=GasWorkDecision.Outcome.COMMITTED||!Double.isFinite(d.acceptedMoles())||d.acceptedMoles()<=0)
                fail("steady headroom window requires every observed machine attempt to commit positive gas: "+kind+" decision="+d);
            if(kind!=AtmosMachineryFixturePlan.Kind.MIXER&&(!Double.isFinite(d.actualPaidInputJoules())||d.actualPaidInputJoules()<=0))
                fail("steady powered pump event lacks actual paid input: "+kind+" decision="+d);
            if(!machineInputsHaveFeedAndDemand(kind,d))fail("steady machine decision lost real feed or outlet headroom: "+kind+" decision="+d);
            long prior=machineLastCommittedTick.getOrDefault(kind,-1L);
            if(prior>=0&&d.sampleTick()-prior<d.intervalTicks())
                fail("funded machine event spacing fell below resolved cadence "+kind+" previousSample="+prior+" currentSample="+d.sampleTick()+" interval="+d.intervalTicks());
            if(prior<0)machineFirstCommittedTick.put(kind,d.sampleTick());
            machineLastCommittedTick.put(kind,d.sampleTick());
            if(kind==AtmosMachineryFixturePlan.Kind.VOLUME_PUMP&&machine.deviceRuntimeState().lastPumpBlocked())
                fail("positive volume-pump work was reported while actual blocked state remained true");
        }
        boolean machineInputsHaveFeedAndDemand(AtmosMachineryFixturePlan.Kind kind,GasWorkDecision d){
            var inlet=d.inputs().stream().filter(i->i.portId().equals("inlet")).findFirst().orElse(null);
            var outlet=d.inputs().stream().filter(i->i.portId().equals("outlet")).findFirst().orElse(null);
            if(outlet==null)return false;
            if(kind==AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP)
                return inlet!=null&&inlet.gas().totalMoles()>0&&Double.isFinite(d.targetKpa())&&outlet.pressureKpa()<d.targetKpa();
            if(kind==AtmosMachineryFixturePlan.Kind.VOLUME_PUMP)
                return inlet!=null&&inlet.gas().totalMoles()>0&&inlet.pressureKpa()>d.lowerBoundKpa()
                        &&outlet.pressureKpa()<d.upperBoundKpa()&&d.rateM3PerSecond()>0;
            var second=d.inputs().stream().filter(i->i.portId().equals("filter")).findFirst().orElse(null);
            return inlet!=null&&second!=null&&inlet.gas().totalMoles()>0&&second.gas().totalMoles()>0
                    &&Double.isFinite(d.targetKpa())&&outlet.pressureKpa()<d.targetKpa();
        }
        void captureVents(){
            for(var expected:expectedVents.values()){
                BlockPos pos=expected.position();
                if(!(level.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity vent))continue;
                VentilationGasRuntime.TransferObservation.Decision d=vent.transferObservation().lastDecision();
                long last=ventSequences.getOrDefault(pos,0L);
                if(d==null||d.sequence()==last)continue;
                if(d.outcome().equals("COMMIT_EXCEPTION"))fail("vent gas transaction raised commit exception at "+pos+" decision="+d);
                ventSequences.put(pos,d.sequence());ventEvents.merge(pos,1L,Long::sum);
                if(!expected.deviceId().equals(vent.deviceId())||!knownActualDecision(expected,d)){
                    ventUnknownEvents.merge(pos,1L,Long::sum);
                    if(mode==CaptureMode.DIAGNOSTIC)LOGGER.info("LAB_FLOW_DECISION event=vent_unknown expectedPosition={} expectedDeviceId={} expectedKind={} currentDeviceId={} currentKind={} decision={}",
                            pos,expected.deviceId(),expected.kind(),vent.deviceId(),vent.kind(),d);
                    continue;
                }
                ventKnownEvents.merge(pos,1L,Long::sum);
                if(d.acceptedMoles()>0)ventPositive.merge(pos,1L,Long::sum);
                if("NO_PLAN".equals(d.outcome()))ventNoPlan.merge(pos,1L,Long::sum);
                ventFirstEventTick.putIfAbsent(pos,d.sampleTick());ventLastEventTick.put(pos,d.sampleTick());
                ventSeconds.merge(pos,d.paidSeconds(),Double::sum);ventAccepted.merge(pos,d.acceptedMoles(),Double::sum);
                if(mode==CaptureMode.CONTINUITY&&windowStarted&&!stopPhase)validateSteadyVentEvent(expected,vent,d);
                if(mode==CaptureMode.DIAGNOSTIC)LOGGER.info("LAB_FLOW_DECISION event=vent sequence={} deviceId={} kind={} worldTick={} sampleTick={} fundedEventSeconds={} resolvedIntervalSeconds={} requestedWatts={} expectedEventCostJ={} meterObservation={} lastGrantTick={} actualGrantedJoules={} actualSpentJoules={} paidEventReceipt={} pipePos={} pipeNodeIdentity={} lane={} pipeVolumeM3={} pipeBefore={} pipeKPa={} roomPos={} boundary={} roomVolumeM3={} roomBefore={} roomKPa={} settings={} pumpPowerFactor={} resolvedPumpProfile={} pressureChecks={} liveExternalBoundKPa={} profileExternalDefaultKPa={} internalBoundKPa={} configurationAvailable={} planPresent={} plannedPacket={} plannedSourceAfter={} plannedDestinationAfter={} outcome={} acceptedMoles={} actualPacket={} pipeAfter={} roomAfter={} export={} roomReadings={}",
                         d.sequence(),d.deviceId(),d.kind(),d.worldTick(),d.sampleTick(),d.paidSeconds(),vent.prototype().deviceIntervalSeconds(),vent.prototype().apcPowerReceiver().requestedWatts(),vent.prototype().deviceIntervalSeconds()*vent.prototype().apcPowerReceiver().requestedWatts(),com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ventilationMeter(level,vent),vent.lastPaidVentilationGrantTick(),vent.actualPaidVentilationGrantedJoules(),vent.actualPaidVentilationSpentJoules(),vent.lastPaidVentilationEvent(),d.pipePos(),d.pipeNodeIdentity(),d.lane(),d.pipeVolumeM3(),gas(d.pipeBefore()),d.pipePressureKpa(),d.roomPos(),d.boundaryKind(),d.roomVolumeM3(),gas(d.roomBefore()),d.roomPressureKpa(),d.settings(),d.pumpPowerFactor(),d.powerProfilePresent(),d.pressureChecks(),d.liveExternalPressureBoundKpa(),d.profileExternalPressureBoundKpa(),d.internalPressureBoundKpa(),d.configurationAvailable(),d.planPresent(),gas(d.plannedPacket()),gas(d.plannedSourceAfter()),gas(d.plannedDestinationAfter()),d.outcome(),d.acceptedMoles(),gas(d.actualPacket()),gas(d.pipeAfter()),gas(d.roomAfter()),d.exportReceipt(),ventRoomReadings(d));
            }
        }
        void validateSteadyVentEvent(ExpectedVent expected,VentilationDeviceBlockEntity vent,
                VentilationGasRuntime.TransferObservation.Decision d){
            var profile=vent.prototype();var receipt=vent.lastPaidVentilationEvent();
            if(profile==null||receipt==null)fail("funded ventilation event receipt/profile unavailable for "+expected.position());
            long expectedInterval=AtmosMachineCadence.intervalTicks(profile.deviceIntervalSeconds());
            if(!"COMMITTED".equals(d.outcome())||!Double.isFinite(d.acceptedMoles())||d.acceptedMoles()<=0)
                fail("steady finite-room ventilation event did not commit positive transfer: "+expected.position()+" decision="+d);
            if(!ventInputsHaveFeedAndRoomHeadroom(expected.kind(),d))
                fail("steady ventilation event lacks source gas or finite-room pressure headroom: "+expected.position()+" decision="+d);
            if(Math.abs(d.paidSeconds()-profile.deviceIntervalSeconds())>1e-9
                    ||Math.abs(receipt.fundedEventSeconds()-profile.deviceIntervalSeconds())>1e-9
                    ||Math.abs(d.paidSeconds()-receipt.fundedEventSeconds())>1e-9)
                fail("ventilation transfer duration differs from resolved funded event interval: "+expected.position()+" decision="+d+" receipt="+receipt);
            double watts=profile.apcPowerReceiver().requestedWatts();
            double eventCost=watts*profile.deviceIntervalSeconds();
            double configuredVentWatts=com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults.APC_REQUESTED_POWER_WATTS;
            if(Math.abs(watts-configuredVentWatts)>1e-9||Math.abs(receipt.eventJoules()-eventCost)>1e-7||receipt.eventTick()!=d.sampleTick()
                    ||receipt.lastGrantTick()!=vent.lastPaidVentilationGrantTick())
                fail("ventilation paid-event receipt does not identify one current funded event: "+expected.position()+" receipt="+receipt+" decision="+d);
            double previousSpent=ventSpentAtLastReceipt.getOrDefault(expected.position(),0d);
            double spendDelta=vent.actualPaidVentilationSpentJoules()-previousSpent;
            if(!Double.isFinite(spendDelta)||Math.abs(spendDelta-eventCost)>1e-6)
                fail("ventilation event was not charged exactly once: "+expected.position()+" spendDelta="+spendDelta+" cost="+eventCost+" receipt="+receipt);
            long priorTick=ventLastReceiptTick.getOrDefault(expected.position(),-1L);
            if(priorTick>=0&&receipt.eventTick()-priorTick<expectedInterval)
                fail("ventilation funded events are closer than resolved device interval: "+expected.position()+" priorSample="+priorTick+" nowSample="+receipt.eventTick()+" interval="+expectedInterval);
            ventSpentAtLastReceipt.put(expected.position(),vent.actualPaidVentilationSpentJoules());
            ventLastReceiptTick.put(expected.position(),receipt.eventTick());
        }
        boolean ventInputsHaveFeedAndRoomHeadroom(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind kind,
                VentilationGasRuntime.TransferObservation.Decision d){
            if(kind==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.VENT)
                return d.pipeBefore().totalMoles()>0&&d.roomPressureKpa()<d.settings().externalPressureBoundKPa();
            boolean scrubbedGasAvailable=d.settings().gasesToScrub().stream().anyMatch(g->d.roomBefore().moles(g)>0);
            return scrubbedGasAvailable&&d.pipePressureKpa()<5_066.25;
        }
        void finish(){
            if(readyPositiveMask!=((1L<<SELECTED.get(0).ordinal())|(1L<<SELECTED.get(1).ordinal())|(1L<<SELECTED.get(2).ordinal())))
                fail("capture applicability incomplete: each selected machine requires at least one positive accepted event; mask="+readyPositiveMask);
            long revision=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().revision();
            double now=systemMoles(level,origin);
            var missingVentEvents=missingWindowVentEvents();
            var cadenceIssues=mode==CaptureMode.CONTINUITY?continuityWindowIssues():List.<String>of();
            LOGGER.info("LAB_FLOW_DECISION event=summary mode={} elapsedTicks={} windowStartSampleTick={} windowEndSampleTick={} expectedVentIdentities={} machineEvents={} positives={} noPlanOrUnchanged={} rejected={} ventEvents={} ventKnownEvents={} ventUnknownEvents={} ventPositive={} ventNoPlan={} ventFundedEventSeconds={} ventAcceptedMoles={} ventPerDeviceCounts={} missingVentEvents={} cadenceIssues={} machineCounterDeltas={} baselineInventoryRevision={} currentInventoryRevision={} baselineFiniteRoomAndPipeMoles={} currentFiniteRoomAndPipeMoles={} finiteRoomMoles={} pipeMoles={} conservationDelta={} resolvedMachineIntervalsTicks={} resolvedMachineIntervalsSeconds={}",
                    mode,elapsed,windowStartSampleTick,windowEndSampleTick,expectedVentIdentities(),events,positive,zero,rejected,ventEvents,ventKnownEvents,ventUnknownEvents,ventPositive,ventNoPlan,ventSeconds,ventAccepted,
                    perDeviceVentCounts(),missingVentEvents,cadenceIssues,machineCounterDeltas(),baselineRevision,revision,baselineTotal,now,roomMoles(level,origin),pipeMoles(level),now-baselineTotal,
                    machineIntervalTicks(),SELECTED.stream().collect(java.util.stream.Collectors.toMap(k->k.name(),k->((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(k)))).resolvedProfile().simulation().deviceIntervalSeconds())));
            if(!missingVentEvents.isEmpty())fail("capture applicability incomplete for expected ventilation owners: "+missingVentEvents);
            if(!cadenceIssues.isEmpty())fail("continuous-flow cadence window failed: "+cadenceIssues);
            if(revision<baselineRevision)fail("inventory revision moved backwards");
            if(mode==CaptureMode.CONTINUITY)startQuietStop();
            else {lease.close();h.succeed();}
        }
        List<String> continuityWindowIssues(){
            var issues=new ArrayList<String>();
            for(var kind:SELECTED){
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                long interval=machine.resolvedProfile().simulation().intervalTicks();
                long count=events.getOrDefault(kind,0L);long min=(long)Math.floor(240d/interval)-1;long max=(long)Math.ceil(240d/interval)+1;
                long first=machineFirstCommittedTick.getOrDefault(kind,-1L),last=machineLastCommittedTick.getOrDefault(kind,-1L);
                long workDelta=machine.committedWorkEvents()-machineBaselineWorkEvents.getOrDefault(kind,0L);
                double movedDelta=machine.committedMovedMoles()-machineBaselineMoved.getOrDefault(kind,0d);
                double paidDelta=machine.committedPaidJoules()-machineBaselinePaid.getOrDefault(kind,0d);
                if(count<min||count>max||first<windowStartSampleTick||first-windowStartSampleTick>interval+1||last<0||windowEndSampleTick-last>interval+1)
                    issues.add(kind+" eventCount="+count+" expected="+min+".."+max+" first="+first+" last="+last+" interval="+interval);
                if(positive.getOrDefault(kind,0L)!=count||zero.getOrDefault(kind,0L)!=0||rejected.getOrDefault(kind,0L)!=0)
                    issues.add(kind+" positive="+positive.get(kind)+" zero="+zero.get(kind)+" rejected="+rejected.get(kind));
                if(workDelta!=count||!Double.isFinite(movedDelta)||movedDelta<=0)
                    issues.add(kind+" committedWorkDelta="+workDelta+" decisions="+count+" movedMolesDelta="+movedDelta);
                if(!Double.isFinite(paidDelta)||Math.abs(paidDelta-machinePaidInputs.getOrDefault(kind,Double.NaN))>1e-6)
                    issues.add(kind+" committedPaidDelta="+paidDelta+" decisionPaidInputs="+machinePaidInputs.get(kind));
            }
            for(var expected:expectedVents.values()){
                BlockPos pos=expected.position();var vent=(VentilationDeviceBlockEntity)level.getBlockEntity(pos);var profile=vent.prototype();
                long interval=profile==null?Long.MAX_VALUE:AtmosMachineCadence.intervalTicks(profile.deviceIntervalSeconds());
                long count=ventEvents.getOrDefault(pos,0L);long min=(long)Math.floor(240d/interval)-1;long max=(long)Math.ceil(240d/interval)+1;
                long first=ventFirstEventTick.getOrDefault(pos,-1L),last=ventLastEventTick.getOrDefault(pos,-1L);
                if(profile==null||count<min||count>max||first<windowStartSampleTick||first-windowStartSampleTick>interval+1||last<0||windowEndSampleTick-last>interval+1)
                    issues.add(pos+" vent events="+count+" expected="+min+".."+max+" intervalTicks="+interval+" first="+first+" last="+last);
                if(ventPositive.getOrDefault(pos,0L)!=count||ventKnownEvents.getOrDefault(pos,0L)!=count
                        ||ventUnknownEvents.getOrDefault(pos,0L)!=0||ventNoPlan.getOrDefault(pos,0L)!=0)
                    issues.add(pos+" vent positive="+ventPositive.get(pos)+" known="+ventKnownEvents.get(pos)+" unknown="+ventUnknownEvents.get(pos)+" noPlan="+ventNoPlan.get(pos));
            }
            return List.copyOf(issues);
        }
        void startQuietStop(){
            for(var kind:SELECTED){
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                boolean applied=switch(kind){
                    case PRESSURE_PUMP->machine.applySettings(AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA,
                            new AtmosMachineSettings.NumericValue(0));
                    case VOLUME_PUMP->machine.applySettings(AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND,
                            new AtmosMachineSettings.NumericValue(0));
                    case MIXER->machine.applySettings(AtmosMachineSettings.Operation.SET_ENABLED,
                            new AtmosMachineSettings.BooleanValue(false));
                    default->false;
                };
                if(!applied)fail("safe native quiet-stop setting refused for "+kind);
                stopMoved.put(kind,machine.committedMovedMoles());
                stopPaid.put(kind,machine.committedPaidJoules());
                stopWorkEvents.put(kind,machine.committedWorkEvents());
            }
            stopPhase=true;stopElapsed=0;
            LOGGER.info("LAB_FLOW_CONTINUITY event=quiet_stop_start tick={} stopWindowTicks=40 actualSettings=pressureTarget0,volumeRate0,mixerDisabled",level.getGameTime());
            poll();
        }
        void assertQuietStopCounters(){
            for(var kind:SELECTED){
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                if(Double.compare(machine.committedMovedMoles(),stopMoved.get(kind))!=0
                        ||Double.compare(machine.committedPaidJoules(),stopPaid.get(kind))!=0
                        ||machine.committedWorkEvents()!=stopWorkEvents.get(kind))
                    fail("safe quiet-stop settings did not stop committed gas work: "+kind+" moved="+machine.committedMovedMoles()
                            +" baseline="+stopMoved.get(kind)+" events="+machine.committedWorkEvents()+" baselineEvents="+stopWorkEvents.get(kind));
            }
        }
        void finishQuietStop(){
            assertQuietStopCounters();
            var missingAudio=new ArrayList<String>();
            for(var kind:SELECTED){
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                if(Math.abs(machine.deviceRuntimeState().lastMoles())>1e-12||machine.ambientEligible())
                    missingAudio.add(kind+" lastAccepted="+machine.deviceRuntimeState().lastMoles()+" eligible="+machine.ambientEligible());
            }
            LOGGER.info("LAB_FLOW_CONTINUITY event=quiet_stop_summary ticks={} stopSettings={} finalWorkEvents={} finalMovedMoles={} finalPaidJ={} quietAudioFailures={}",
                    stopElapsed,SELECTED.stream().collect(java.util.stream.Collectors.toMap(k->k.name(),k->((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(k)))).settingsSnapshot())),
                    SELECTED.stream().collect(java.util.stream.Collectors.toMap(k->k.name(),k->((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(k)))).committedWorkEvents())),
                    SELECTED.stream().collect(java.util.stream.Collectors.toMap(k->k.name(),k->((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(k)))).committedMovedMoles())),
                    SELECTED.stream().collect(java.util.stream.Collectors.toMap(k->k.name(),k->((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(k)))).committedPaidJoules())),missingAudio);
            if(!missingAudio.isEmpty())fail("quiet-stop sample still has accepted-flow audio eligibility: "+missingAudio);
            lease.close();h.succeed();
        }
        Map<String,Long> machineIntervalTicks(){
            return SELECTED.stream().collect(java.util.stream.Collectors.toMap(k->k.name(),
                    k->((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(k)))).resolvedProfile().simulation().intervalTicks()));
        }
        List<String> missingWindowVentEvents(){
            var missing=new ArrayList<String>();
            for(var expected:expectedVents.values()){
                BlockPos pos=expected.position();double seconds=ventSeconds.getOrDefault(pos,Double.NaN);
                if(ventEvents.getOrDefault(pos,0L)<1||ventKnownEvents.getOrDefault(pos,0L)<1
                        ||ventUnknownEvents.getOrDefault(pos,0L)>0||!Double.isFinite(seconds)||seconds<=0)
                    missing.add(pos+"#"+expected.deviceId()+"/"+expected.kind()+" events="+ventEvents.getOrDefault(pos,0L)
                            +" known="+ventKnownEvents.getOrDefault(pos,0L)+" unknown="+ventUnknownEvents.getOrDefault(pos,0L)
                            +" fundedEventSeconds="+seconds+" noPlan="+ventNoPlan.getOrDefault(pos,0L));
            }
            return List.copyOf(missing);
        }
        List<String> expectedVentIdentities(){return expectedVents.values().stream()
                .map(v->v.position()+"#"+v.deviceId()+"/"+v.kind()).toList();}
        Map<BlockPos,String> perDeviceVentCounts(){
            var counts=new LinkedHashMap<BlockPos,String>();
            for(var expected:expectedVents.values()){
                BlockPos p=expected.position();
                VentilationDeviceBlockEntity vent=(VentilationDeviceBlockEntity)level.getBlockEntity(p);
                counts.put(p,"id="+expected.deviceId()+",kind="+expected.kind()+",events="+ventEvents.get(p)
                        +",known="+ventKnownEvents.get(p)+",unknown="+ventUnknownEvents.get(p)+",positive="+ventPositive.get(p)
                        +",noPlan="+ventNoPlan.get(p)+",fundedEventSeconds="+ventSeconds.get(p)+",acceptedMoles="+ventAccepted.get(p)
                        +",lastGrantTick="+vent.lastPaidVentilationGrantTick()+",actualGrantedJ="+vent.actualPaidVentilationGrantedJoules()
                        +",actualSpentJ="+vent.actualPaidVentilationSpentJoules()+",lastPaidEvent="+vent.lastPaidVentilationEvent());
            }
            return Map.copyOf(counts);
        }
        Map<AtmosMachineryFixturePlan.Kind,String> machineCounterDeltas(){
            var deltas=new EnumMap<AtmosMachineryFixturePlan.Kind,String>(AtmosMachineryFixturePlan.Kind.class);
            for(var kind:SELECTED){
                var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                deltas.put(kind,"moved="+(machine.committedMovedMoles()-machineBaselineMoved.getOrDefault(kind,0d))
                        +",workEvents="+(machine.committedWorkEvents()-machineBaselineWorkEvents.getOrDefault(kind,0L))
                        +",paidJ="+(machine.committedPaidJoules()-machineBaselinePaid.getOrDefault(kind,0d))
                        +",decisionPaidJ="+machinePaidInputs.get(kind));
            }
            return Map.copyOf(deltas);
        }
        Map<String,Object> stationRooms(AtmosMachineryFixturePlan.Kind kind){
            var values=AtmosMachineryFixtureService.roomMeasurements(level,origin);var result=new LinkedHashMap<String,Object>();
            for(var e:circuits)if(e.station()==kind){String prefix=AtmosMachineryFixtureService.roomMeasurementKey(e)+".";
                values.forEach((key,value)->{if(key.startsWith(prefix))result.put(key,value);});}
            return result;
        }
        Map<String,Object> ventRoomReadings(VentilationGasRuntime.TransferObservation.Decision d){
            var values=AtmosMachineryFixtureService.roomMeasurements(level,origin);var result=new LinkedHashMap<String,Object>();
            for(var endpoint:circuits)if(endpoint.room().roomCells().contains(d.roomPos())){
                String prefix=AtmosMachineryFixtureService.roomMeasurementKey(endpoint)+".";
                values.forEach((key,value)->{if(key.startsWith(prefix))result.put(key,value);});}
            return result;
        }
        void failMeterDiagnostics(List<String> missing){
            long generation=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level);
            long clock=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.currentSampleTick(level);
            for(var expected:expectedVents.values())if(level.getBlockEntity(expected.position()) instanceof VentilationDeviceBlockEntity vent){
                LOGGER.error("LAB_FLOW_CONTINUITY meter_failure host={} device={} scenePhase={} diagnostic={} globalTopologyGeneration={} currentPowerClock={} lastGrantTick={} paidSpentJ={}",
                        expected.position(),expected.deviceId(),priming?"priming":stopPhase?"quiet-stop":"steady-window",
                        com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ventilationMeterDiagnostic(level,vent),generation,clock,
                        vent.lastPaidVentilationGrantTick(),vent.actualPaidVentilationSpentJoules());
            }
            fail(com.juicyslew.moonstation14.gametest.support.GameTestFailureMessage.concise(
                    "actual powered-vent meter became unknown/zero; affected="+missing.size()+"/"+expectedVents.size()+" first="+missing.get(0)));
        }
        void fail(String reason){lease.close();throw new GameTestAssertException(com.juicyslew.moonstation14.gametest.support.GameTestFailureMessage.concise(reason));}
    }
    static long commonPrimingTicks(long billingTicks,List<Long> deviceIntervals){
        if(billingTicks<1||deviceIntervals==null||deviceIntervals.isEmpty()||deviceIntervals.stream().anyMatch(t->t==null||t<1))
            throw new IllegalArgumentException("positive billing and device intervals required");
        long common=billingTicks;
        for(long interval:deviceIntervals){long gcd=gcd(common,interval);common=Math.multiplyExact(common/gcd,interval);}
        return common;
    }
    private static long gcd(long first,long second){while(second!=0){long remainder=first%second;first=second;second=remainder;}return first;}
    private static String inputs(List<GasWorkDecision.Input> inputs){return inputs.stream().map(i->i.portId()+"@"+i.root()+"[V="+i.volumeM3()+",P="+i.pressureKpa()+",gas="+gas(i.gas())+"]").toList().toString();}
    private static String gas(com.juicyslew.moonstation14.ms14.atmos.core.GasMixture gas){
        if(gas==null)return "unavailable";
        return "{T="+gas.temperatureKelvin()+",V="+gas.volumeCubicMeters()+",moles="+gas.totalMoles()+",species="+gas.gasMoles()+"}";
    }
    private static double roomMoles(ServerLevel level,BlockPos origin){return AtmosMachineryFixtureService.roomMeasurements(level,origin).entrySet().stream()
            .filter(e->e.getKey().startsWith("room.")&&e.getKey().endsWith(".moles")).mapToDouble(Map.Entry::getValue).sum();}
    private static double pipeMoles(ServerLevel level){return PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot)
            .map(snapshot->snapshot.snapshot().keySet().stream().mapToDouble(key->snapshot.nodeShare(key).totalMoles()).sum()).orElse(Double.NaN);}
    private static double systemMoles(ServerLevel level,BlockPos origin){return roomMoles(level,origin)+pipeMoles(level);}
}
