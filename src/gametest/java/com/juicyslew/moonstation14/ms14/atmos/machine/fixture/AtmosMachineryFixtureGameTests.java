package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.chat.Component;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere", "atmos-machinery-fixture", "atmos-laboratory-functional"})
public final class AtmosMachineryFixtureGameTests {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(AtmosMachineryFixtureGameTests.class);
    private static final int ALL_FUNCTIONAL_PROGRESS_WAIT_TICKS=800;
    /** Independent operator-sign regression; unlike production it deliberately enumerates every user-visible token. */
    private static final Map<AtmosMachineryFixturePlan.Kind,String> STATION_COMMAND_GOLDEN=Map.of(
            AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP,"pressure_pump",
            AtmosMachineryFixturePlan.Kind.VOLUME_PUMP,"volume_pump",
            AtmosMachineryFixturePlan.Kind.MIXER,"mixer",
            AtmosMachineryFixturePlan.Kind.FILTER,"filter",
            AtmosMachineryFixturePlan.Kind.VALVE,"valve",
            AtmosMachineryFixturePlan.Kind.HEATER,"heater",
            AtmosMachineryFixturePlan.Kind.FREEZER,"freezer",
            AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER,"heat_exchanger",
            AtmosMachineryFixturePlan.Kind.MANIFOLD,"manifold");
    /** Numerical fixture readback allowance for aggregate room measurements; not a gas-work tolerance. */
    private static final double ROOM_READBACK_ROUNDING_MOLES=1e-4;
    private static final int HEATER_PRIMING_TICK_CAP=136;
    private AtmosMachineryFixtureGameTests() { }

    // 400 setup + 80 mixer + 136 heater priming + 80 heater proof + 800 all-functional proof + 10 margin.
    @GameTest(template="atmos_machinery_lab_clear",templateNamespace="moonstation14",timeoutTicks=1506,batch="atmos_machinery_individual_start")
    @GameTestTags({"atmos-machinery-fixture-individual-start"})
    public static void individualDispatcherStartLeavesOtherDemosUnseeded(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        if(!AtmosMachineryFixtureService.build(level,origin).startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed");
            var telemetryViewer=RegisteredServerPlayerFixture.createUnenrolled(level,"atmostelemetry");
            telemetryViewer.player().setPos(origin.getX()+32.5,origin.getY()+1,origin.getZ()+32.5);
            var forcedBeforeTelemetry=Set.copyOf(level.getForcedChunks());
            h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo ignored) { }
                @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo ignored,net.minecraft.gametest.framework.GameTestRunner runner){telemetryViewer.close();}
                @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo ignored,net.minecraft.gametest.framework.GameTestRunner runner){telemetryViewer.close();}
                @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest,net.minecraft.gametest.framework.GameTestInfo newTest,net.minecraft.gametest.framework.GameTestRunner runner){telemetryViewer.close();}
            });
            var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
            var circuits=AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints();
            var pendingThermalSigns=Map.of(
                    AtmosMachineryFixturePlan.Kind.FREEZER,captureThermalSign(level,circuits,AtmosMachineryFixturePlan.Kind.FREEZER),
                    AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER,captureThermalSign(level,circuits,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER));
            manifoldTopology(level,origin,plan,circuits,false,false);
            for(var gauge:circuits.stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                    ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM).toList()) {
                var gaugePos=gauge.room().labelPosition().above();
                if(gauge.room().roomCells().contains(gaugePos)||!level.getBlockState(gaugePos).is(Blocks.REDSTONE_LAMP)
                        ||level.getBlockState(gaugePos).getValue(RedstoneLampBlock.LIT))
                    throw new GameTestAssertException("unstarted thermal gauge is missing, lit, or inside its finite room: "+gauge.station());
            }
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            PowerFixtureReadiness.await(h,level,400,
                     ()->lease.entityTicking(origin)&&lease.entityTicking(origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1))
                             &&fixtureRoomsReadyForTrustedSetup(level,origin,AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints().stream()
                             .filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER).toList(),false,false)
                             &&mixerVentMetersReady(level,origin,circuits,AtmosMachineryFixturePlan.Kind.MIXER),
                     ()->mixerSetupFailureFacts(level,origin,circuits,lease),()->{
            var manifoldRoom=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD).findFirst().orElseThrow().room();
            BlockPos hole=manifoldRoom.glassPositions().stream().filter(p->level.getBlockState(p).is(Blocks.GLASS)
                    &&level.getBlockEntity(p)==null&&manifoldRoom.roomCells().stream().anyMatch(c->c.distManhattan(p)==1))
                    .findFirst().orElseThrow(()->new GameTestAssertException("no safe normal-glass boundary in manifold room"));
            var wallState=level.getBlockState(hole);
            level.setBlock(hole,Blocks.AIR.defaultBlockState(),3);
            try {
            if(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                    .finiteRoomEndpointForTrustedSimulation(level,manifoldRoom.roomCells().getFirst()).isPresent())
                throw new GameTestAssertException("opened manifold room boundary unexpectedly remained finite");
            var manifoldEndpointPlan=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD).toList();
            if(fixtureRoomsReadyForTrustedSetup(level,origin,manifoldEndpointPlan,false,false))
                throw new GameTestAssertException("open manifold room incorrectly passed trusted setup enclosure proof");
            var selectedMixerEndpoints=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER).toList();
            if(!fixtureRoomsReadyForTrustedSetup(level,origin,selectedMixerEndpoints,false,false))
                throw new GameTestAssertException("selected mixer room lost finite readiness while manifold boundary was open");
            var manifoldReadings=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            var manifoldEndpoint=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD).findFirst().orElseThrow();
            String manifoldMolesKey=AtmosMachineryFixtureService.roomMeasurementKey(manifoldEndpoint)+".moles";
            if(manifoldReadings.containsKey(manifoldMolesKey)&&Double.isFinite(manifoldReadings.get(manifoldMolesKey)))
                throw new GameTestAssertException("opened manifold room still has a finite canonical measurement");
            var before=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var barePipe=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            var bareRooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            int bare=executeCommand(level,source,"ms14 atmos_fixture start");
            var afterBare=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(bare!=0||!before.equals(afterBare)||!barePipe.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())
                    ||!bareRooms.equals(AtmosMachineryFixtureService.roomMeasurements(level,origin)))
                throw new GameTestAssertException("bare public start was not a mutation-free help/refusal");
            if(!STATION_COMMAND_GOLDEN.keySet().equals(java.util.EnumSet.allOf(AtmosMachineryFixturePlan.Kind.class)))
                throw new GameTestAssertException("test command golden does not cover all nine station kinds");
            var beforeMixerStart=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            int mixerStart=executeCommand(level,source,"ms14 atmos_fixture start mixer "+origin.getX()+" "+origin.getY()+" "+origin.getZ());
            if(mixerStart!=1)throw new GameTestAssertException("selected mixer start command did not report exactly one successful action: "+mixerStart);
            assertUnselectedRoomsUnchanged(beforeMixerStart,AtmosMachineryFixtureService.roomMeasurements(level,origin),circuits,Set.of(AtmosMachineryFixturePlan.Kind.MIXER),"mixer start");
            } finally { level.setBlock(hole,wallState,3); }
            var after=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(!after.reservedDemos().contains("mixer")||after.reservedDemos().contains("heater")||after.laboratoryStarted())throw new GameTestAssertException("mixer-only start reservation invalid");
            for(var placement:plan.placements()) if(level.getBlockEntity(origin.offset(placement.position())) instanceof AtmosMachineBlockEntity machine) {
                boolean enabled=machine.settingsSnapshot().enabled().orElse(false);
                if(enabled!=(placement.kind()==AtmosMachineryFixturePlan.Kind.MIXER))throw new GameTestAssertException("selected start enabled wrong machine: "+placement.kind());
            }
            var passive=AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER).toList();
            var measurements=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            double mixerStock=0;
            for(var endpoint:circuits) if(endpoint.station()==AtmosMachineryFixturePlan.Kind.MIXER) {
                String roomKey=AtmosMachineryFixtureService.roomMeasurementKey(endpoint)+".moles";
                double moles=measurements.getOrDefault(roomKey,Double.NaN);
                if(!Double.isFinite(moles))throw new GameTestAssertException("selected mixer finite room measurement missing: "+endpoint.station()+"="+moles);
                mixerStock+=moles;
            }
            if(!Double.isFinite(mixerStock)||Math.abs(mixerStock-480)>1e-3)throw new GameTestAssertException("selected mixer stock is not its two finite 240 mol sources: "+mixerStock);
            var visibleMixerRoom=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER&&e.role()==AtmosMachineryRoomCircuitPlan.Role.SOURCE
                    &&e.ports().contains(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.FILTER)).findFirst().orElseThrow();
            var sampleCell=AtmosMachineryLaboratoryRooms.passableCells(level,visibleMixerRoom.room()).getFirst();
            var visibleGas=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.sample(level,sampleCell).orElseThrow();
            var visual=com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualPayload.VisualCell.from(visibleGas,sampleCell);
            if(visibleGas.moles(GasType.WATER_VAPOR)<=0||visibleGas.moles(GasType.OXYGEN)>1e-9||visual.waterVaporAlpha()==0)
                throw new GameTestAssertException("selected second mixer gas is not the real visible, room-temperature water-vapor source");
            var hxRoom=passive.stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM).findFirst().orElseThrow();
            String key=AtmosMachineryFixtureService.roomMeasurementKey(hxRoom)+".moles";
            for(var owner:after.roomDeviceOwners())if(level.getBlockEntity(owner.position()) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device) {
                boolean shouldEnable=circuits.stream().anyMatch(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER&&e.ventHosts().contains(owner.position()));
                if(device.config().settings().configuredEnabled()!=shouldEnable)throw new GameTestAssertException("selected start enabled wrong room vent: "+owner.position());
            }
            awaitSelectedMachineWork(h,level,origin,source,plan,circuits,forcedBeforeTelemetry,pendingThermalSigns);
            });
    }

    static long heaterPrimingEvents(double pipeVolume,double pumpPower,double roomCellVolume,
            double siphonRate,double intervalSeconds,double authoredRoomMoles,int roomCells) {
        if(!Double.isFinite(pipeVolume)||!Double.isFinite(pumpPower)||!Double.isFinite(roomCellVolume)
                ||!Double.isFinite(siphonRate)||!Double.isFinite(intervalSeconds)||!Double.isFinite(authoredRoomMoles)
                ||pipeVolume<=0||pumpPower<=0||roomCellVolume<=0||siphonRate<=0||intervalSeconds<=0
                ||authoredRoomMoles<=0||roomCells<=0)return -1;
        double required=pipeVolume/(pumpPower*roomCellVolume);
        double fraction=siphonRate*intervalSeconds/roomCellVolume;
        if(!Double.isFinite(required)||!Double.isFinite(fraction)||required<=0||required>=1||fraction<=0||fraction>=1)return -1;
        double events=Math.floor(Math.log1p(-required)/Math.log1p(-fraction))+1;
        if(!Double.isFinite(events)||events<1||events>Integer.MAX_VALUE)return -1;
        // Authored room uniformity bounds the local donor density. Convert the event
        // count to a finite startup allowance; two solve quanta cover identity/grant startup.
        double initialDensity=authoredRoomMoles/roomCells;
        if(!Double.isFinite(initialDensity)||initialDensity<=0)return -1;
        return (long)events;
    }

    static long heaterPrimingTicks(long events,long intervalTicks) {
        if(events<1||intervalTicks<1||events>(Long.MAX_VALUE-2L*PowerRuntime.TICKS_PER_SOLVE)/intervalTicks)return -1;
        return 2L*PowerRuntime.TICKS_PER_SOLVE+events*intervalTicks;
    }

    private record HeaterHostMark(UUID deviceId,long decisionSequence,double grantedJoules) { }
    private record HeaterHostAdmission(UUID deviceId,long decisionSequence,long settingsRevision,double grantedJoules) { }
    private record HeaterCirculationPlan(AtmosMachineryRoomCircuitPlan.Endpoint endpoint,
            BlockPos ventPos,BlockPos scrubberPos,Map<BlockPos,HeaterHostMark> preStartHosts,
            int pipeNodeCount,double pipeVolume,double pumpPower,double roomCellVolume,double siphonRate,
            double eventSeconds,long intervalTicks,double authoredRoomMoles,int roomCells,long requiredEvents,long waitTicks) {
        private HeaterCirculationPlan { preStartHosts=Map.copyOf(preStartHosts); }
    }
    private record HeaterCirculationAdmission(HeaterCirculationPlan plan,
            Map<BlockPos,HeaterHostAdmission> hosts) {
        private HeaterCirculationAdmission { hosts=Map.copyOf(hosts); }
    }

    /** Uses the production read-only component discovery; rejects any change to the authored bound assumptions. */
    private static HeaterCirculationPlan prepareHeaterCirculation(ServerLevel level,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits) {
        var endpoint=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.HEATER
                &&e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM).findFirst()
                .orElseThrow(()->new GameTestAssertException("heater thermal-room endpoint missing for circulation bound"));
        if(endpoint.ventHosts().size()!=2)throw new GameTestAssertException("heater circulation requires exactly its vent and siphon hosts: "+endpoint.ventHosts());
        BlockPos ventPos=null,scrubberPos=null;
        var preStart=new LinkedHashMap<BlockPos,HeaterHostMark>();
        for(BlockPos pos:endpoint.ventHosts()) {
            if(!(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
                    ||device.deviceId()==null||!device.settingsValid()||device.prototype()==null)
                throw new GameTestAssertException("heater circulation host not a valid live ventilation device at "+pos);
            if(device.kind()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.VENT) {
                if(ventPos!=null)throw new GameTestAssertException("heater has more than one VENT host: "+endpoint.ventHosts());
                ventPos=pos;
            } else {
                if(scrubberPos!=null)throw new GameTestAssertException("heater has more than one SCRUBBER host: "+endpoint.ventHosts());
                scrubberPos=pos;
            }
            var decision=device.transferObservation().lastDecision();
            double granted=device.actualPaidVentilationGrantedJoules();
            if(!Double.isFinite(granted)||granted<0)throw new GameTestAssertException("invalid pre-start ventilation grant observation at "+pos+": "+granted);
            preStart.put(pos,new HeaterHostMark(device.deviceId(),decision==null?0:decision.sequence(),granted));
        }
        if(ventPos==null||scrubberPos==null)throw new GameTestAssertException("heater host kinds are not one VENT plus one SCRUBBER: "+endpoint.ventHosts());
        var vent=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(ventPos);
        var scrubber=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(scrubberPos);
        var ventProfile=vent.prototype();
        var scrubberProfile=scrubber.prototype();
        if(ventProfile==null||ventProfile.gasVentPump()==null||scrubberProfile==null||scrubberProfile.gasVentScrubber()==null)
            throw new GameTestAssertException("heater host resolved unary profiles are incomplete: vent="+ventProfile+" scrubber="+scrubberProfile);
        double pumpPower=ventProfile.gasVentPump().pumpPower();
        double siphonRate=scrubber.config().settings().volumeRateM3PerSecond();
        double eventSeconds=ventProfile.deviceIntervalSeconds();
        double cellVolume=AtmosphereUnits.minecraftCellVolumeCubicMeters();
        double authoredMoles=endpoint.initialInventory().totalMoles();
        int roomCells=endpoint.room().roomCells().size();
        var livePipe=LoadedPipeComponent.discover(level,endpoint.pipeStub(),VisualPipeBlockEntity.PRIMARY).orElse(null);
        if(livePipe==null)throw new GameTestAssertException("heater pipe component is not fully loaded/owned from planned stub="+endpoint.pipeStub());
        double pipeVolume=livePipe.totalVolume();
        long intervalTicks=com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineCadence.intervalTicks(eventSeconds);
        // Without refill, uniform room donors lose at most this geometric fraction per siphon event.
        // The bound assumes isothermal room pressure equalization; heater work only raises pipe temperature.
        long events=heaterPrimingEvents(pipeVolume,pumpPower,cellVolume,siphonRate,eventSeconds,authoredMoles,roomCells);
        long waitTicks=heaterPrimingTicks(events,intervalTicks);
        boolean assumptions=livePipe.nodes().size()==6&&Math.abs(pipeVolume-1.2)<1e-6
                &&Math.abs(pumpPower-2.0)<1e-9&&Math.abs(siphonRate-.2)<1e-9
                &&Math.abs(scrubberProfile.gasVentScrubber().volumeRateM3PerSecond()-siphonRate)<1e-9
                &&Math.abs(scrubberProfile.deviceIntervalSeconds()-eventSeconds)<1e-9
                &&Math.abs(eventSeconds-.6)<1e-9&&Math.abs(cellVolume-1.0)<1e-9
                &&Math.abs(authoredMoles-100)<1e-3&&roomCells==18&&events==8
                &&intervalTicks==12&&waitTicks==HEATER_PRIMING_TICK_CAP;
        if(!assumptions||waitTicks<1||waitTicks>HEATER_PRIMING_TICK_CAP)
            throw new GameTestAssertException("heater live circulation assumptions changed: pipeNodes="+livePipe.nodes().size()
                    +" pipeVolume="+pipeVolume+" pumpPower="+pumpPower+" roomCellVolume="+cellVolume
                    +" siphonRate="+siphonRate+" eventSeconds="+eventSeconds+" intervalTicks="+intervalTicks
                    +" authoredRoomMoles="+authoredMoles+" roomCells="+roomCells+" events="+events
                    +" derivedWaitTicks="+waitTicks+" cap="+HEATER_PRIMING_TICK_CAP);
        return new HeaterCirculationPlan(endpoint,ventPos,scrubberPos,preStart,livePipe.nodes().size(),pipeVolume,
                pumpPower,cellVolume,siphonRate,eventSeconds,intervalTicks,authoredMoles,roomCells,events,waitTicks);
    }

    /** Captures each exact live host's sequence, settings revision, and actual-credit baseline at heater admission. */
    private static HeaterCirculationAdmission captureHeaterCirculationBaseline(ServerLevel level,HeaterCirculationPlan plan) {
        var admission=new LinkedHashMap<BlockPos,HeaterHostAdmission>();
        for(var entry:plan.preStartHosts().entrySet()) {
            BlockPos pos=entry.getKey();
            if(!(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
                    ||!entry.getValue().deviceId().equals(device.deviceId())||!device.settingsValid()||device.prototype()==null)
                throw new GameTestAssertException("heater circulation host identity/settings changed at admission: "+pos);
            var settings=device.config().settings();
            boolean expectedVent=pos.equals(plan.ventPos());
            if(!settings.configuredEnabled()||expectedVent!=(device.kind()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.VENT)
                    ||(!expectedVent&&settings.scrubberMode()!=com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.ScrubberMode.SIPHON))
                throw new GameTestAssertException("heater circulation host not enabled with planned VENT/SIPHON mode at "+pos+": "+settings);
            var profile=device.prototype();
            if(profile.apcPowerReceiver().requestedWatts()!=5.0
                    ||(expectedVent?profile.gasVentPump()==null:profile.gasVentScrubber()==null)
                    ||Math.abs(profile.deviceIntervalSeconds()-.6)>1e-9
                    ||(!expectedVent&&Math.abs(settings.volumeRateM3PerSecond()-.2)>1e-9))
                throw new GameTestAssertException("heater circulation host lacks resolved fixed 5 W prototype at "+pos+": "+profile);
            var decision=device.transferObservation().lastDecision();
            double granted=device.actualPaidVentilationGrantedJoules();
            if(!Double.isFinite(granted)||granted+1e-9<entry.getValue().grantedJoules())
                throw new GameTestAssertException("heater host grant observation regressed at admission "+pos+": "+granted);
            long decisionSequence=decision==null?0:decision.sequence();
            if(decisionSequence<entry.getValue().decisionSequence())
                throw new GameTestAssertException("heater host transfer sequence regressed at admission "+pos+": "+decisionSequence);
            admission.put(pos,new HeaterHostAdmission(device.deviceId(),decisionSequence,settings.revision(),granted));
        }
        return new HeaterCirculationAdmission(plan,admission);
    }

    /** Waits only for an actual post-admission vent room-release, then chains the original thermal proof phase. */
    private static void awaitHeaterCirculationReady(GameTestHelper h,ServerLevel level,BlockPos origin,
            HeaterCirculationAdmission admission,Runnable afterReady) {
        var plan=admission.plan();
        LOGGER.info("ATMOS_HEATER_CIRCULATION event=priming_begin worldTick={} sampleTick={} vent={} scrubber={} pipeNodes={} VpipeM3={} pumpPower={} roomCellVolumeM3={} siphonRateM3s={} eventSeconds={} intervalTicks={} authoredRoomMoles={} NroomCells={} kRequiredEvents={} boundTicks={} hostBaselines={}",
                level.getGameTime(),PowerRuntime.currentSampleTick(level),plan.ventPos(),plan.scrubberPos(),plan.pipeNodeCount(),plan.pipeVolume(),
                plan.pumpPower(),plan.roomCellVolume(),plan.siphonRate(),plan.eventSeconds(),plan.intervalTicks(),plan.authoredRoomMoles(),
                plan.roomCells(),plan.requiredEvents(),plan.waitTicks(),admission.hosts());
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,Math.toIntExact(plan.waitTicks()),
                ()->heaterCirculationFailureFacts(level,origin,admission),
                ()->{
                    for(var baselineEntry:admission.hosts().entrySet()) {
                        BlockPos pos=baselineEntry.getKey();var baseline=baselineEntry.getValue();
                        if(!(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
                                ||device.isRemoved()||!baseline.deviceId().equals(device.deviceId())||!device.settingsValid()
                                ||device.prototype()==null||device.config().settings().revision()!=baseline.settingsRevision()
                                ||!device.config().settings().configuredEnabled())return false;
                        double requested=device.prototype().apcPowerReceiver().requestedWatts();
                        var meter=PowerRuntime.ventilationMeter(level,device);
                        var signature=PowerRuntime.ventilationReceiverSignature(level,device).orElse(null);
                        if(requested!=5.0||!meter.known()||Double.compare(meter.deliveredWatts(),requested)!=0
                                ||signature==null||signature.ventilationBE()!=device
                                ||device.actualPaidVentilationGrantedJoules()<=baseline.grantedJoules()+1e-9)return false;
                        if(pos.equals(plan.scrubberPos())&&device.config().settings().scrubberMode()
                                !=com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.ScrubberMode.SIPHON)return false;
                    }
                    if(!(level.getBlockEntity(plan.ventPos()) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity vent))return false;
                    var baseline=admission.hosts().get(plan.ventPos());
                    var decision=vent.transferObservation().lastDecision();
                    return decision!=null&&decision.sequence()>baseline.decisionSequence()
                            &&decision.deviceId().equals(baseline.deviceId())&&decision.devicePos().equals(plan.ventPos())
                            &&"VENT".equals(decision.kind())&&decision.settings()!=null
                            &&decision.settings().revision()==baseline.settingsRevision()
                            &&Math.abs(decision.paidSeconds()-plan.eventSeconds())<1e-9
                            &&"COMMITTED".equals(decision.outcome())&&decision.actualPacket()!=null
                            &&Double.isFinite(decision.acceptedMoles())&&decision.acceptedMoles()>0
                            &&decision.actualPacket().totalMoles()>0&&plan.endpoint().room().roomCells().contains(decision.roomPos());
                },()->{
                    var vent=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(plan.ventPos());
                    var scrubber=(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity)level.getBlockEntity(plan.scrubberPos());
                    var release=vent.transferObservation().lastDecision();
                    LOGGER.info("ATMOS_HEATER_CIRCULATION event=priming_ready worldTick={} sampleTick={} ventId={} decisionSequence={} settingsRevision={} outcome={} acceptedMoles={} releasePacket={} ventMeter={} ventGrantTick={} ventGrantedJ={} ventSpentJ={} ventPaidEvent={} scrubberMeter={} scrubberGrantTick={} scrubberGrantedJ={} scrubberSpentJ={} scrubberPaidEvent={}",
                            level.getGameTime(),PowerRuntime.currentSampleTick(level),vent.deviceId(),release.sequence(),
                            release.settings().revision(),release.outcome(),release.acceptedMoles(),release.actualPacket(),
                            PowerRuntime.ventilationMeter(level,vent),vent.lastPaidVentilationGrantTick(),vent.actualPaidVentilationGrantedJoules(),
                            vent.actualPaidVentilationSpentJoules(),vent.lastPaidVentilationEvent(),PowerRuntime.ventilationMeter(level,scrubber),
                            scrubber.lastPaidVentilationGrantTick(),scrubber.actualPaidVentilationGrantedJoules(),
                            scrubber.actualPaidVentilationSpentJoules(),scrubber.lastPaidVentilationEvent());
                    afterReady.run();
                });
    }

    private static String heaterCirculationFailureFacts(ServerLevel level,BlockPos origin,
            HeaterCirculationAdmission admission) {
        var plan=admission.plan();
        var facts=new StringBuilder("heater circulation did not observe a paid post-start room-release; boundTicks=")
                .append(plan.waitTicks()).append(" now=").append(level.getGameTime()).append(" sampleTick=")
                .append(PowerRuntime.currentSampleTick(level)).append(" requiredEvents=").append(plan.requiredEvents())
                .append(" pipeNodes/volume=").append(plan.pipeNodeCount()).append('/').append(plan.pipeVolume())
                .append(" pumpPower=").append(plan.pumpPower()).append(" siphonRate/eventSeconds=")
                .append(plan.siphonRate()).append('/').append(plan.eventSeconds()).append(" grants={");
        for(var entry:admission.hosts().entrySet()) {
            BlockPos pos=entry.getKey();var baseline=entry.getValue();
            if(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
                facts.append(pos.toShortString()).append("#").append(device.deviceId()).append(" settings=")
                        .append(device.config().settings()).append(" meter=").append(PowerRuntime.ventilationMeter(level,device))
                        .append(" receiver=").append(PowerRuntime.ventilationReceiverSignature(level,device).orElse(null))
                        .append(" baselineSeq=").append(baseline.decisionSequence()).append(" baselineGrantJ=").append(baseline.grantedJoules())
                        .append(" grantJ=").append(device.actualPaidVentilationGrantedJoules())
                        .append(" grantTick=").append(device.lastPaidVentilationGrantTick())
                        .append(" spentJ=").append(device.actualPaidVentilationSpentJoules())
                        .append(" event=").append(device.lastPaidVentilationEvent()).append("; ");
            else facts.append(pos.toShortString()).append("=unavailable; ");
        }
        facts.append("} ");
        var decision=level.getBlockEntity(plan.ventPos()) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity vent
                ?vent.transferObservation().lastDecision():null;
        facts.append("latestVentDecision=").append(decision==null?"none":("{seq="+decision.sequence()+",id="+decision.deviceId()
                +",settings="+decision.settings()+",outcome="+decision.outcome()+",acceptedMol="+decision.acceptedMoles()
                +",packet="+decision.actualPacket()+",pipeBefore="+decision.pipeBefore()+",roomBefore="+decision.roomBefore()+"}"));
        var readings=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        String roomKey=AtmosMachineryFixtureService.roomMeasurementKey(plan.endpoint());
        facts.append(" roomMoles=").append(readings.get(roomKey+".moles"))
                .append(" roomTemperatureK=").append(readings.get(roomKey+".temperatureK"));
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        var baseline=saved==null?Map.<String,Double>of():saved.demoBaselines().getOrDefault("heater",Map.of());
        if(level.getBlockEntity(plan.endpoint().machine()) instanceof AtmosMachineBlockEntity heater) {
            double paid=heater.committedPaidJoules()-baseline.getOrDefault("work.heater.paidJ",0d);
            double thermal=heater.committedThermalJoules()-baseline.getOrDefault("work.heater.thermalJ",0d);
            facts.append(" heaterPaidJ=").append(paid).append(" heaterThermalJ=").append(thermal)
                    .append(" heaterBaselineT=").append(baseline.get(roomKey+".temperatureK"))
                    .append(" heaterCurrentT=").append(readings.get(roomKey+".temperatureK"));
        }
        var livePipe=LoadedPipeComponent.discover(level,plan.endpoint().pipeStub(),VisualPipeBlockEntity.PRIMARY).orElse(null);
        if(livePipe==null)facts.append(" livePipe=unavailable");
        else {
            facts.append(" livePipeNodes=").append(livePipe.nodes().size()).append(" livePipeVolume=").append(livePipe.totalVolume());
            var pipeInventory=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level)
                    .map(com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData::snapshot).orElse(null);
            for(var node:livePipe.nodes()) {
                var gas=pipeInventory==null?null:pipeInventory.nodeShare(node.key());
                facts.append(" [").append(node.key()).append(" V=").append(node.volume());
                if(gas==null)facts.append(" gas=unavailable");
                else facts.append(" T=").append(gas.temperatureKelvin()).append(" mol=").append(gas.totalMoles())
                        .append(" kPa=").append(gas.pressureKpa());
                facts.append(']');
            }
        }
        String detailed=facts.toString();
        LOGGER.error("{}",detailed);
        return com.juicyslew.moonstation14.gametest.support.GameTestFailureMessage.concise(detailed);
    }

    private static void awaitSelectedMachineWork(GameTestHelper h,ServerLevel level,BlockPos origin,
            net.minecraft.commands.CommandSourceStack source,AtmosMachineryFixturePlan plan,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,Set<Long> forcedBeforeTelemetry,
            Map<AtmosMachineryFixturePlan.Kind,List<String>> pendingThermalSigns) {
        var mixerPos=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MIXER));
        if(!(level.getBlockEntity(mixerPos) instanceof AtmosMachineBlockEntity mixer))throw new GameTestAssertException("mixer missing after selected start");
        // The 400-tick setup phase already established live PowerRuntime meter
        // publication for disabled hosts before admission. This unchanged 80-tick
        // phase additionally proves actual unpowered mixer movement and zero paid work.
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,80,
                    ()->mixerFailureFacts(level,origin,mixer),
                 ()->mixer.resolvedProfile()!=null&&mixer.resolvedProfile().configuredReceiverLoadWatts()==0
                         &&mixerVentMetersReady(level,origin,circuits,AtmosMachineryFixturePlan.Kind.MIXER)
                         &&mixer.committedMovedMoles()>0&&mixer.committedWorkEvents()>0&&mixer.committedPaidJoules()==0,()->{
            var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(entry.laboratoryStarted()||!entry.reservedDemos().equals(java.util.Set.of("mixer")))throw new GameTestAssertException("individual mixer startup changed auxiliary/global start state");
            var mixerBaseline=entry.demoBaselines().get("mixer");
            if(mixerBaseline==null||!mixerBaseline.containsKey("room.mixer")) {
                if(mixerBaseline==null||mixerBaseline.keySet().stream().noneMatch(k->k.startsWith("room.mixer.")))throw new GameTestAssertException("mixer baseline was not captured after source seeding");
            }
            var stockBeforeRepeat=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            var gasBeforeRepeat=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            int repeated=executeCommand(level,source,startCommand("mixer",origin));
            var repeatedEntry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(repeated!=0||!entry.equals(repeatedEntry)||!gasBeforeRepeat.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())
                    ||!mixerBaseline.equals(repeatedEntry.demoBaselines().get("mixer")))throw new GameTestAssertException("same-demo repeat refilled stock or reset its baseline");

            var beforeHeaterStart=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            var heaterCirculationPlan=prepareHeaterCirculation(level,circuits);
            if(executeCommand(level,source,startCommand("heater",origin))!=1)throw new GameTestAssertException("second selected heater command refused");
            // Startup seeds synchronously, before the enabled heater/room vent can
            // participate in any world tick. Prove the authored stock at admission,
            // rather than mistaking later live room/pipe exchange for a failed seed.
            var immediatelyAfterHeaterStart=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            double admittedHeaterStock=stationMoles(immediatelyAfterHeaterStart,circuits,AtmosMachineryFixturePlan.Kind.HEATER);
            if(!Double.isFinite(admittedHeaterStock)||Math.abs(admittedHeaterStock-100)>1e-3)
                throw new GameTestAssertException("heater start did not synchronously seed its planned 100 mol stock: "+admittedHeaterStock);
            var admittedEntry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var admittedBaseline=admittedEntry.demoBaselines().get("heater");
            String heaterRoomKey=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.HEATER
                    &&e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM).findFirst().map(AtmosMachineryFixtureService::roomMeasurementKey).orElseThrow();
            double baselineHeaterStock=admittedBaseline==null?Double.NaN:admittedBaseline.getOrDefault(heaterRoomKey+".moles",Double.NaN);
            if(admittedBaseline==null||!admittedBaseline.containsKey(heaterRoomKey+".moles")
                    ||!Double.isFinite(baselineHeaterStock)||Math.abs(baselineHeaterStock-100)>1e-3)
                throw new GameTestAssertException("heater saved baseline did not capture its independently planned 100 mol admission stock: "+baselineHeaterStock);
            assertUnselectedRoomsUnchanged(beforeHeaterStart,AtmosMachineryFixtureService.roomMeasurements(level,origin),circuits,
                    Set.of(AtmosMachineryFixturePlan.Kind.HEATER),"heater start");
            assertUnselectedThermalPresentation(level,origin,circuits,AtmosMachineryFixturePlan.Kind.FREEZER,pendingThermalSigns.get(AtmosMachineryFixturePlan.Kind.FREEZER));
            assertUnselectedThermalPresentation(level,origin,circuits,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER,pendingThermalSigns.get(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER));
            var telemetryEntry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var mixerSettings=((AtmosMachineBlockEntity)level.getBlockEntity(mixerPos)).settingsSnapshot();
            var telemetryHeaterSettings=((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.HEATER)))).settingsSnapshot();
            var heaterAdmission=captureHeaterCirculationBaseline(level,heaterCirculationPlan);
            awaitHeaterCirculationReady(h,level,origin,heaterAdmission,()->
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,80,
                            ()->heaterWorkFailureFacts(level,origin,circuits),
                            ()->mixerVentMetersReady(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER)
                                    &&thermalTransferObserved(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER)
                                    &&thermalGaugePresented(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER),
                            ()->continueSelectedHeaterToAll(h,level,origin,source,plan,circuits,mixerBaseline,forcedBeforeTelemetry,telemetryEntry,mixerSettings,telemetryHeaterSettings)));
        });
    }

    private static String mixerFailureFacts(ServerLevel level,BlockPos origin,AtmosMachineBlockEntity mixer) {
        var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
        var endpoints=AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints().stream()
                .filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER).toList();
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        var facts=new StringBuilder("selected mixer work timeout: moved=").append(mixer.committedMovedMoles())
                .append(", events=").append(mixer.committedWorkEvents()).append(", paid=").append(mixer.committedPaidJoules());
        for(var room:endpoints.stream().map(AtmosMachineryRoomCircuitPlan.Endpoint::room).distinct().toList())for(BlockPos cell:AtmosMachineryLaboratoryRooms.passableCells(level,room)) {
            var owned=atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isPresent();
            facts.append("; finite[").append(cell.toShortString()).append("]=").append(owned);
        }
        for(BlockPos host:endpoints.stream().flatMap(e->e.ventHosts().stream()).distinct().toList())if(level.getBlockEntity(host) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
            facts.append("; vent[").append(host.toShortString()).append("] meter=")
                    .append(PowerRuntime.ventilationMeter(level,device)).append(" transfer=").append(device.transferObservation());
        PipeGasInventorySavedData.existing(level).ifPresent(data->{var inventory=data.snapshot();inventory.snapshot().forEach((node,entry)->{
                var gas=inventory.nodeShare(node); facts.append("; pipe[").append(node).append("] kPa=").append(gas.pressureKpa(gas.volumeCubicMeters()))
                        .append(" mol=").append(gas.totalMoles());});});
        return facts.toString();
    }

    private static List<String> captureThermalSign(ServerLevel level,List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,
            AtmosMachineryFixturePlan.Kind kind) {
        var endpoint=circuits.stream().filter(e->e.station()==kind&&(e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM)).findFirst().orElseThrow();
        if(!(level.getBlockEntity(endpoint.room().labelPosition()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign))
            throw new GameTestAssertException("authored thermal sign missing before startup: "+kind);
        return List.of(sign.getFrontText().getMessage(0,false).getString(),sign.getFrontText().getMessage(1,false).getString(),
                sign.getFrontText().getMessage(2,false).getString(),sign.getFrontText().getMessage(3,false).getString());
    }

    private static void assertUnselectedThermalPresentation(ServerLevel level,BlockPos origin,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,AtmosMachineryFixturePlan.Kind kind,List<String> authoredPending) {
        var endpoint=circuits.stream().filter(e->e.station()==kind&&(e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM)).findFirst().orElseThrow();
        var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var sign=level.getBlockEntity(endpoint.room().labelPosition());
        boolean authoredStillShown=sign instanceof net.minecraft.world.level.block.entity.SignBlockEntity s
                &&authoredPending.equals(List.of(s.getFrontText().getMessage(0,false).getString(),s.getFrontText().getMessage(1,false).getString(),
                s.getFrontText().getMessage(2,false).getString(),s.getFrontText().getMessage(3,false).getString()));
        boolean dynamicWaiting=thermalGaugePresented(level,origin,circuits,kind);
        var lamp=level.getBlockState(endpoint.room().labelPosition().above());
        if(!(level.getBlockEntity(endpoint.machine()) instanceof AtmosMachineBlockEntity machine)
                ||machine.settingsSnapshot().enabled().orElse(false)||machine.committedMovedMoles()!=0
                ||machine.committedPaidJoules()!=0||machine.committedThermalJoules()!=0
                ||entry.reservedDemos().contains(kind.name().toLowerCase(Locale.ROOT))
                ||entry.demoBaselines().containsKey(kind.name().toLowerCase(Locale.ROOT))
                ||!lamp.is(Blocks.REDSTONE_LAMP)||lamp.getValue(RedstoneLampBlock.LIT)
                ||(!authoredStillShown&&!dynamicWaiting))
            throw new GameTestAssertException("unselected thermal device has work or lacks truthful waiting/unavailable gauge: "+kind);
    }

    private static String heaterWorkFailureFacts(ServerLevel level,BlockPos origin,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits) {
        return heaterWorkFailureFacts(level,origin,circuits,true);
    }

    private static String heaterWorkFailureFacts(ServerLevel level,BlockPos origin,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,boolean logFullFacts) {
        var endpoint=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.HEATER
                &&e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM).findFirst().orElseThrow();
        var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var baseline=entry.demoBaselines().getOrDefault("heater",Map.of());
        String key=AtmosMachineryFixtureService.roomMeasurementKey(endpoint);
        var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        var machine=(AtmosMachineBlockEntity)level.getBlockEntity(endpoint.machine());
        double paid=machine.committedPaidJoules()-baseline.getOrDefault("work.heater.paidJ",0d);
        double thermal=machine.committedThermalJoules()-baseline.getOrDefault("work.heater.thermalJ",0d);
        double before=baseline.getOrDefault(key+".temperatureK",Double.NaN),now=rooms.getOrDefault(key+".temperatureK",Double.NaN);
        var pos=endpoint.room().labelPosition().above();
        boolean lit=level.getBlockState(pos).is(Blocks.REDSTONE_LAMP)&&level.getBlockState(pos).getValue(RedstoneLampBlock.LIT);
        var sign=level.getBlockEntity(endpoint.room().labelPosition());
        String meter=sign instanceof net.minecraft.world.level.block.entity.SignBlockEntity s?s.getFrontText().getMessage(1,false).getString():"unavailable";
        var facts=new StringBuilder("heater work timeout: paidJ=").append(paid).append(", thermalJ=").append(thermal)
                .append(", baselineT=").append(before).append(", currentT=").append(now).append(", warming=")
                .append(AtmosMachineryThermalGauge.warming(before,now,paid,thermal)).append(", lamp=").append(lit).append(", meter=").append(meter);
        for(var hostEndpoint:circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.HEATER).toList())
            for(BlockPos host:hostEndpoint.ventHosts()) {
                var blockEntity=level.getBlockEntity(host);
                if(blockEntity instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device) {
                    var receipt=device.lastPaidVentilationEvent();
                    facts.append("; vent[").append(host.toShortString()).append("] id=").append(device.deviceId())
                            .append(" enabled=").append(device.config().settings().configuredEnabled())
                            .append(" settings=").append(device.config().settings())
                            .append(" meter=").append(PowerRuntime.ventilationMeter(level,device))
                            .append(" receiver=").append(PowerRuntime.ventilationReceiverSignature(level,device).orElse(null))
                            .append(" diagnostic=").append(PowerRuntime.ventilationMeterDiagnostic(level,device))
                            .append(" lastGrantTick=").append(device.lastPaidVentilationGrantTick())
                            .append(" grantedJ=").append(device.actualPaidVentilationGrantedJoules())
                            .append(" spentJ=").append(device.actualPaidVentilationSpentJoules())
                            .append(" event=").append(receipt)
                            .append(" decision=");
                    var decision=device.transferObservation().lastDecision();
                    if(decision==null) facts.append("unavailable");
                    else facts.append("{sequence=").append(decision.sequence()).append(",sampleTick=").append(decision.sampleTick())
                            .append(",paidSeconds=").append(decision.paidSeconds()).append(",outcome=").append(decision.outcome())
                            .append(",pipeBeforeT/mol=").append(mixtureFacts(decision.pipeBefore()))
                            .append(",pipeAfterT/mol=").append(mixtureFacts(decision.pipeAfter()))
                            .append(",roomBeforeT/mol=").append(mixtureFacts(decision.roomBefore()))
                            .append(",roomAfterT/mol=").append(mixtureFacts(decision.roomAfter())).append('}');
                    facts.append(" transfer=").append(device.transferObservation().speciesMoles()).append(" thermalJ=")
                            .append(device.transferObservation().thermalJoules());
                } else facts.append("; vent[").append(host.toShortString()).append("]=unavailable");
            }
        facts.append("; roomBeforeAfterT=").append(before).append("->").append(now);
        String detailed=facts.toString();
        if(logFullFacts) {
            LOGGER.error("{}",detailed);
            return com.juicyslew.moonstation14.gametest.support.GameTestFailureMessage.concise(detailed);
        }
        return detailed;
    }

    private static String mixtureFacts(com.juicyslew.moonstation14.ms14.atmos.core.GasMixture mixture) {
        return mixture==null?"unavailable":"T="+mixture.temperatureKelvin()+",mol="+mixture.totalMoles();
    }

    /** Runtime readiness after selected vent admission; meters remain owned by PowerRuntime. */
    private static boolean mixerVentMetersReady(ServerLevel level,BlockPos origin,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> endpoints,AtmosMachineryFixturePlan.Kind station) {
        var hosts=endpoints.stream().filter(e->e.station()==station).flatMap(e->e.ventHosts().stream()).distinct().toList();
        return !hosts.isEmpty()&&hosts.stream().allMatch(host->level.getBlockEntity(host)
                instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device
                &&device.prototype()!=null
                &&Double.isFinite(device.prototype().apcPowerReceiver().requestedWatts())
                &&PowerRuntime.ventilationMeter(level,device).known());
    }

    private static String mixerSetupFailureFacts(ServerLevel level,BlockPos origin,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,FixtureChunkTickets.Lease lease) {
        var hosts=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER)
                .flatMap(e->e.ventHosts().stream()).distinct().toList();
        var facts=new StringBuilder("selected mixer precommand setup timeout; entityTicking=").append(lease.entityTicking(origin));
        for(BlockPos host:hosts)if(level.getBlockEntity(host) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
            facts.append("; selected vent[").append(host.toShortString()).append("] enabled=")
                    .append(device.config().settings().configuredEnabled()).append(" requestedW=")
                    .append(device.prototype()==null?"unknown":device.prototype().apcPowerReceiver().requestedWatts())
                    .append(" meter=").append(PowerRuntime.ventilationMeter(level,device));
        return facts.toString();
    }

    private static void continueSelectedHeaterToAll(GameTestHelper h,ServerLevel level,BlockPos origin,
            net.minecraft.commands.CommandSourceStack source,AtmosMachineryFixturePlan plan,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,Map<String,Double> mixerBaseline,Set<Long> forcedBeforeTelemetry,
            AtmosMachineryFixtureSavedData.Entry telemetryEntry,AtmosMachineSettings.Values mixerSettings,AtmosMachineSettings.Values heaterSettings) {
        if(!level.getForcedChunks().equals(forcedBeforeTelemetry))throw new GameTestAssertException("live telemetry changed forced-chunk ownership");
        if(!telemetryEntry.equals(AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow())
                ||!mixerSettings.equals(((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MIXER)))).settingsSnapshot())
                ||!heaterSettings.equals(((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.HEATER)))).settingsSnapshot()))
            throw new GameTestAssertException("read-only telemetry changed saved baselines or selected machine settings");
        var heaterPos=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.HEATER));
        if(!(level.getBlockEntity(heaterPos) instanceof AtmosMachineBlockEntity heater)||!heater.settingsSnapshot().enabled().orElse(false))
            throw new GameTestAssertException("selected heater not enabled");
        var beforeAll=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var mixerBaselineBeforeAll=beforeAll.demoBaselines().get("mixer");
        var heaterBaselineBeforeAll=beforeAll.demoBaselines().get("heater");
        if(mixerBaselineBeforeAll==null||heaterBaselineBeforeAll==null||beforeAll.laboratoryStarted())
            throw new GameTestAssertException("two selected demos lack independent baselines or incorrectly started auxiliary lab");
        // Heater stock is intentionally not asserted here: powered thermal work and
        // its enabled room receiver have run since synchronous admission. The saved
        // admission baseline above is the immutable exact-seed proof; subsequent
        // checks below prove paid work and measured thermal response.
        for(var placement:plan.placements())if(level.getBlockEntity(origin.offset(placement.position())) instanceof AtmosMachineBlockEntity device) {
            boolean expected=placement.kind()==AtmosMachineryFixturePlan.Kind.MIXER||placement.kind()==AtmosMachineryFixturePlan.Kind.HEATER;
            if(device.settingsSnapshot().enabled().orElse(false)!=expected)throw new GameTestAssertException("second selected demo enabled an unselected machine: "+placement.kind());
            if(!expected&&(device.committedMovedMoles()!=0||device.committedPaidJoules()!=0||device.committedThermalJoules()!=0))
                throw new GameTestAssertException("unselected machine committed work before explicit all: "+placement.kind());
        }
        var hx=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER)));
        if(hx.committedThermalJoules()!=0)throw new GameTestAssertException("unselected passive HX recorded thermal work");
        for(var minerRoom:AtmosMachineryLaboratoryRooms.miners(origin))
            if(level.getBlockEntity(minerRoom.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner&&miner.producedMoles()!=0)
                throw new GameTestAssertException("fixture miner produced before explicit all");
        var ventOrigin=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var ventPlan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        for(BlockPos p:List.of(ventPlan.scrubber(),ventPlan.vent()))
            if(level.getBlockEntity(ventOrigin.offset(p)) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device) {
                if(device.config().settings().configuredEnabled())throw new GameTestAssertException("auxiliary lab ventilation enabled before explicit all");
                if(device.transferObservation().speciesMoles().values().stream().anyMatch(n->n>0)||device.transferObservation().thermalJoules()!=0)
                    throw new GameTestAssertException("auxiliary lab ventilation recorded work before explicit all");
            }
        assertThermalIndicator(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER,true);
        assertThermalIndicator(level,origin,circuits,AtmosMachineryFixturePlan.Kind.FREEZER,false);
        assertThermalIndicator(level,origin,circuits,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER,false);

        final double[] mixerStockBeforeAll={Double.NaN},mixerStockImmediatelyAfterAll={Double.NaN};
        final double[] heaterStockBeforeAll={Double.NaN},heaterStockImmediatelyAfterAll={Double.NaN};
        final boolean[] allStarted={false};
        final ManifoldFlowProof[] manifoldProof={null};
         com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,ALL_FUNCTIONAL_PROGRESS_WAIT_TICKS,
                 ()->explicitAllTimeoutFacts(level,origin,plan,circuits,allStarted[0],manifoldProof[0]),
                ()->{
                     if(allStarted[0]) {
                         boolean manifoldReady=manifoldFiniteFlowObserved(level,origin,plan,circuits,proof->manifoldProof[0]=proof);
                         return manifoldReady&&thermalTransferObserved(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER)
                             &&thermalTransferObserved(level,origin,circuits,AtmosMachineryFixturePlan.Kind.FREEZER)
                             &&thermalTransferObserved(level,origin,circuits,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER)
                             &&thermalGaugePresented(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER)
                             &&thermalGaugePresented(level,origin,circuits,AtmosMachineryFixturePlan.Kind.FREEZER)
                             &&thermalGaugePresented(level,origin,circuits,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
                     }
                    var current=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
                    mixerStockBeforeAll[0]=stationMoles(rooms,circuits,AtmosMachineryFixturePlan.Kind.MIXER);
                    heaterStockBeforeAll[0]=stationMoles(rooms,circuits,AtmosMachineryFixturePlan.Kind.HEATER);
                    var beforeGas=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                    int result=executeCommand(level,source,startCommand("all",origin));
                    if(result==1) {
                        allStarted[0]=true;
                        var afterRooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
                        mixerStockImmediatelyAfterAll[0]=stationMoles(afterRooms,circuits,AtmosMachineryFixturePlan.Kind.MIXER);
                        heaterStockImmediatelyAfterAll[0]=stationMoles(afterRooms,circuits,AtmosMachineryFixturePlan.Kind.HEATER);
                        return false;
                    }
                    var after=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(!current.equals(after)||!beforeGas.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot()))
                        throw new GameTestAssertException("not-ready all attempt mutated reservations or pipe inventory");
                    return false;
                },()->{
                    var all=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(!all.laboratoryStarted()||all.reservedDemos().size()!=AtmosMachineryFixturePlan.Kind.values().length
                            ||!mixerBaseline.equals(all.demoBaselines().get("mixer"))||!heaterBaselineBeforeAll.equals(all.demoBaselines().get("heater")))
                        throw new GameTestAssertException("all failed to preserve individually captured baselines/reservations");
                    if(!level.getForcedChunks().equals(forcedBeforeTelemetry))throw new GameTestAssertException("live telemetry forced an additional chunk");
                    assertCapturedManifoldFlow(level,origin,plan,circuits,manifoldProof[0]);
                    var allRooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
                    if(!Double.isFinite(mixerStockBeforeAll[0])||!Double.isFinite(mixerStockImmediatelyAfterAll[0])
                            ||!Double.isFinite(heaterStockBeforeAll[0])||!Double.isFinite(heaterStockImmediatelyAfterAll[0])
                            ||Math.abs(mixerStockImmediatelyAfterAll[0]-mixerStockBeforeAll[0])>1e-6
                            ||Math.abs(heaterStockImmediatelyAfterAll[0]-heaterStockBeforeAll[0])>1e-6)
                        throw new GameTestAssertException("all refilled an already-started mixer/heater room");
                    var hxEndpoint=circuits.stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM).findFirst().orElseThrow();
                    double hxCenter=allRooms.getOrDefault(AtmosMachineryFixtureService.roomMeasurementKey(hxEndpoint)+".moles",Double.NaN);
                    if(!Double.isFinite(hxCenter)||Math.abs(hxCenter-60)>1e-3)throw new GameTestAssertException("all failed to seed passive HX center exactly once");
                    var beforeRepeat=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    var gasBeforeRepeat=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                    var roomsBeforeRepeat=AtmosMachineryFixtureService.roomMeasurements(level,origin);
                    int refusal=executeCommand(level,source,startCommand("freezer",origin));
                    var afterRepeat=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(refusal!=0||!beforeRepeat.equals(afterRepeat)||!gasBeforeRepeat.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())
                            ||!roomsBeforeRepeat.equals(AtmosMachineryFixtureService.roomMeasurements(level,origin)))
                        throw new GameTestAssertException("individual command after all mutated already-started fixture state");
                    h.succeed();
                 });
     }

    /** Read-only timeout facts for the explicit-all 800-tick readiness wait. */
    private static String explicitAllTimeoutFacts(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,boolean allStarted,ManifoldFlowProof manifoldProof) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        var manifoldIn=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD
                &&e.role()==AtmosMachineryRoomCircuitPlan.Role.MANIFOLD_IN).findFirst().orElse(null);
        var manifoldOut=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD
                &&e.role()==AtmosMachineryRoomCircuitPlan.Role.MANIFOLD_OUT).findFirst().orElse(null);
        var manifoldHost=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MANIFOLD));
        var inventory=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        ManifoldFixtureObservation.Result manifoldObservation=level.getBlockEntity(manifoldHost) instanceof AtmosMachineBlockEntity machine
                ?ManifoldFixtureObservation.observe(machine,manifoldHost,inventory,saved)
                :new ManifoldFixtureObservation.Result(false,false,List.of(),"HOST_UNAVAILABLE");
        var manifoldBaseline=saved==null?Map.<String,Double>of():saved.demoBaselines().getOrDefault("manifold",Map.of());
        String inKey=manifoldIn==null?"":AtmosMachineryFixtureService.roomMeasurementKey(manifoldIn)+".plasmaMoles";
        String outKey=manifoldOut==null?"":AtmosMachineryFixtureService.roomMeasurementKey(manifoldOut)+".plasmaMoles";
        double inBefore=manifoldBaseline.getOrDefault(inKey,Double.NaN),inNow=rooms.getOrDefault(inKey,Double.NaN);
        double outBefore=manifoldBaseline.getOrDefault(outKey,Double.NaN),outNow=rooms.getOrDefault(outKey,Double.NaN);
        boolean sourceRoomDrop=Double.isFinite(inBefore)&&Double.isFinite(inNow)&&inNow<inBefore-1e-6;
        boolean receiverRoomRise=Double.isFinite(outBefore)&&Double.isFinite(outNow)&&outNow>outBefore+1e-6;
        boolean manifoldNonTopologyReady=manifoldObservation.valid()&&manifoldObservation.observed()&&sourceRoomDrop&&receiverRoomRise;

        boolean heaterTransfer=saved!=null&&thermalTransferObserved(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER);
        boolean freezerTransfer=saved!=null&&thermalTransferObserved(level,origin,circuits,AtmosMachineryFixturePlan.Kind.FREEZER);
        boolean exchangerTransfer=saved!=null&&thermalTransferObserved(level,origin,circuits,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
        boolean heaterGauge=thermalGaugePresented(level,origin,circuits,AtmosMachineryFixturePlan.Kind.HEATER);
        boolean freezerGauge=thermalGaugePresented(level,origin,circuits,AtmosMachineryFixturePlan.Kind.FREEZER);
        boolean exchangerGauge=thermalGaugePresented(level,origin,circuits,AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);

        var facts=new StringBuilder("explicit-all readiness timeout tick=").append(level.getGameTime())
                .append(" sampleTick=").append(PowerRuntime.currentSampleTick(level)).append(" allStarted=").append(allStarted)
                .append(" waitTicks=").append(ALL_FUNCTIONAL_PROGRESS_WAIT_TICKS)
                .append(" manifold{valid=").append(manifoldObservation.valid()).append(",mix=").append(manifoldObservation.observed())
                .append(",sourceRoomDrop=").append(sourceRoomDrop).append(",receiverRoomRise=").append(receiverRoomRise)
                .append(",nonTopologyReady=").append(manifoldNonTopologyReady).append(",topologyProofCaptured=").append(manifoldProof!=null)
                .append(",reason=").append(manifoldObservation.reason()).append(",ports=").append(manifoldObservation.ports())
                .append(",proof=").append(manifoldProof==null?"none":("readyTick="+manifoldProof.readyTick()+",walls="+manifoldProof.walls().size()+",mask="+manifoldProof.appearanceMask()))
                .append("} thermal{heaterTransfer=").append(heaterTransfer).append(",heaterGauge=").append(heaterGauge)
                .append(",freezerTransfer=").append(freezerTransfer).append(",freezerGauge=").append(freezerGauge)
                .append(",hxTransfer=").append(exchangerTransfer).append(",hxGauge=").append(exchangerGauge).append("}")
                .append(" saved{laboratoryStarted=").append(saved!=null&&saved.laboratoryStarted())
                .append(",reserved=").append(saved==null?"unavailable":saved.reservedDemos())
                .append(",baselineKeys=").append(saved==null?0:saved.baseline().size())
                .append(",demoBaselines=").append(saved==null?"unavailable":saved.demoBaselines().entrySet().stream()
                        .map(e->e.getKey()+"/"+e.getValue().size()).toList()).append("}")
                .append(" machineReceipts={").append(heaterWorkFailureFacts(level,origin,circuits,false));
        for(var kind:List.of(AtmosMachineryFixturePlan.Kind.HEATER,AtmosMachineryFixturePlan.Kind.FREEZER,
                AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER)) {
            var endpoint=circuits.stream().filter(e->e.station()==kind&&(e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                    ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM)).findFirst().orElse(null);
            if(endpoint==null||!(level.getBlockEntity(endpoint.machine()) instanceof AtmosMachineBlockEntity machine)) {
                facts.append("; ").append(kind).append("=unavailable");continue;
            }
            var baseline=saved==null?Map.<String,Double>of():saved.demoBaselines().getOrDefault(kind.name().toLowerCase(Locale.ROOT),Map.of());
            String key="work."+kind.name().toLowerCase(Locale.ROOT)+".";
            facts.append("; ").append(kind).append("{paidJ=")
                    .append(machine.committedPaidJoules()-baseline.getOrDefault(key+"paidJ",0d))
                    .append(",thermalJ=").append(machine.committedThermalJoules()-baseline.getOrDefault(key+"thermalJ",0d))
                    .append(",movedMol=").append(machine.committedMovedMoles()-baseline.getOrDefault(key+"movedMoles",0d))
                    .append(",events=").append(machine.committedWorkEvents()).append('}');
        }
        facts.append("}");
        for(var endpoint:circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.HEATER
                ||e.station()==AtmosMachineryFixturePlan.Kind.FREEZER).toList())for(BlockPos host:endpoint.ventHosts()) {
            if(!(level.getBlockEntity(host) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)) {
                facts.append(" host[").append(host.toShortString()).append("]=unavailable");continue;
            }
            var settings=device.config().settings();
            var meter=PowerRuntime.ventilationMeter(level,device);
            var decision=device.transferObservation().lastDecision();
            facts.append(" host[").append(host.toShortString()).append("]{").append(device.kind()).append(" id=")
                    .append(device.deviceId()).append(" enabled=").append(settings.configuredEnabled())
                    .append(" revision=").append(settings.revision()).append(" mode=").append(settings.scrubberMode())
                    .append(" requestedW=").append(device.prototype()==null?Double.NaN:device.prototype().apcPowerReceiver().requestedWatts())
                    .append(" meter=").append(meter).append(" grantTick=").append(device.lastPaidVentilationGrantTick())
                    .append(" grantJ=").append(device.actualPaidVentilationGrantedJoules()).append(" spentJ=").append(device.actualPaidVentilationSpentJoules())
                    .append(" finiteDecision=").append(decision==null?"none":("seq="+decision.sequence()+",settingsRev="
                            +(decision.settings()==null?"unknown":decision.settings().revision())+",kind="+decision.kind()+",outcome="+decision.outcome()
                            +",paidSeconds="+decision.paidSeconds()+",acceptedMol="+decision.acceptedMoles()+",pipeKPa="
                            +(decision.pipeBefore()==null?Double.NaN:decision.pipeBefore().pressureKpa())+"->"
                            +(decision.pipeAfter()==null?Double.NaN:decision.pipeAfter().pressureKpa())+",roomKPa="
                            +(decision.roomBefore()==null?Double.NaN:decision.roomBefore().pressureKpa())+"->"
                            +(decision.roomAfter()==null?Double.NaN:decision.roomAfter().pressureKpa())))
                    .append('}');
        }
        String detailed=facts.toString();
        LOGGER.error("Explicit-all readiness timeout: {}",truncate(detailed,8000));
        return com.juicyslew.moonstation14.gametest.support.GameTestFailureMessage.concise(detailed);
    }

    private static String startCommand(String demo,BlockPos origin){return "ms14 atmos_fixture start "+demo+" "+origin.getX()+" "+origin.getY()+" "+origin.getZ();}
    private static int executeCommand(ServerLevel level,net.minecraft.commands.CommandSourceStack source,String command) {
        try {
            return level.getServer().getCommands().getDispatcher().execute(command,source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new GameTestAssertException("fixture command syntax error: "+exception.getMessage());
        }
    }
    private static double stationMoles(Map<String,Double> readings,List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,AtmosMachineryFixturePlan.Kind station){
        return circuits.stream().filter(e->e.station()==station).mapToDouble(e->readings.getOrDefault(AtmosMachineryFixtureService.roomMeasurementKey(e)+".moles",0d)).sum();
    }

    static void assertUnselectedRoomsUnchanged(Map<String,Double> before,Map<String,Double> after,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,Set<AtmosMachineryFixturePlan.Kind> selected,String action) {
        for(var endpoint:circuits)if(!selected.contains(endpoint.station())) {
            String prefix=AtmosMachineryFixtureService.roomMeasurementKey(endpoint)+".";
            var beforeKeys=before.keySet().stream().filter(k->k.startsWith(prefix)).collect(java.util.stream.Collectors.toSet());
            var afterKeys=after.keySet().stream().filter(k->k.startsWith(prefix)).collect(java.util.stream.Collectors.toSet());
            if(!beforeKeys.equals(afterKeys))throw new GameTestAssertException(action+" changed unselected room observability for "+endpoint.station()+": "+beforeKeys+" -> "+afterKeys);
            for(String key:beforeKeys) {
                double old=before.get(key),now=after.get(key);
                if(!Double.isFinite(old)||!Double.isFinite(now)||Double.doubleToLongBits(old)!=Double.doubleToLongBits(now))
                    throw new GameTestAssertException(action+" changed unselected room reading "+key+": "+old+" -> "+now);
            }
        }
    }

    private static ManifoldTopologySnapshot manifoldTopology(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,boolean requireWallGas,boolean requireAppearance) {
        BlockPos machinePos=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MANIFOLD));
        if(!(level.getBlockEntity(machinePos) instanceof AtmosMachineBlockEntity manifold))throw new GameTestAssertException("manifold machine missing");
        var walls=new java.util.ArrayList<WallIdentity>();var gas=new java.util.ArrayList<Double>();
        for(var endpoint:circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD).toList()) {
            if(endpoint.pipeSeeds().size()!=3||endpoint.hostLayers().size()!=1)throw new GameTestAssertException("manifold plan lost layer/host provenance");
            for(var seed:endpoint.pipeSeeds()) {
                var direction=net.minecraft.core.Direction.getNearest(seed.position().getX()-machinePos.getX(),0,seed.position().getZ()-machinePos.getZ());
                var crossing=AtmosMachineryLaboratoryRooms.sealedCrossing(machinePos,direction);
                if(com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallSegment.matchingLive(level,crossing,seed.layer()).isEmpty()
                        ||com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallGasOwner.activeSnapshot(level,crossing,seed.layer()).isEmpty())
                    throw new GameTestAssertException("manifold sealed-wall lane lacks exact persisted gas owner: "+seed.layer()+" at "+crossing);
                int bit=switch(seed.layer()){case PRIMARY->VisualPipeBlockEntity.PRIMARY;case SECONDARY->VisualPipeBlockEntity.SECONDARY;case TERTIARY->VisualPipeBlockEntity.TERTIARY;};
                if(!(level.getBlockEntity(seed.position()) instanceof VisualPipeBlockEntity visual)||!visual.hasLayer(bit)
                        ||com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner.activeSnapshot(level,seed.position(),bit).isEmpty())
                    throw new GameTestAssertException("manifold exposed layer stub/owner missing: "+seed.layer());
                var port=manifold.gasPorts().stream().filter(p->p.id().equals(seed.port().name().toLowerCase(Locale.ROOT))).findFirst().orElseThrow();
                if(!com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent.hasExternalConnection(level,port.key(machinePos),direction))
                    throw new GameTestAssertException("manifold actual machine port is not joined through steel: "+seed.port());
                var wall=com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallGasOwner.activeSnapshot(level,crossing,seed.layer()).orElseThrow();
                var stub=com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner.activeSnapshot(level,seed.position(),bit).orElseThrow();
                var wallInventory=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level)
                        .map(com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData::snapshot).orElse(null);
                double wallMoles=wallInventory==null?0.0:wallInventory.nodeShare(wall.node()).totalMoles();
                if(requireWallGas&&wallMoles<=1e-6)throw new GameTestAssertException("manifold layer has no finite gas flow through its owned wall segment: "+seed.layer());
                walls.add(new WallIdentity(crossing,seed.layer(),wall.incarnation(),stub.incarnation()));gas.add(wallMoles);
            }
        }
        if(requireAppearance&&manifold.manifoldAppearanceMask()!=0x3f)
            throw new GameTestAssertException("manifold appearance mask does not expose six actual joined ports: "+manifold.manifoldAppearanceMask());
        return new ManifoldTopologySnapshot(List.copyOf(walls),List.copyOf(gas),manifold.manifoldAppearanceMask());
    }

    private record WallIdentity(BlockPos position,com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer layer,
                                UUID wallOwner,UUID stubOwner) {}
    private record ManifoldTopologySnapshot(List<WallIdentity> walls,List<Double> wallGasMoles,int appearanceMask) {}
    private record ManifoldFlowProof(ManifoldFixtureObservation.Result observation,double sourceBefore,double sourceNow,
                                     double recipientBefore,double recipientNow,List<WallIdentity> walls,List<Double> wallGasMoles,
                                     int appearanceMask,long readyTick) {
        private ManifoldFlowProof { walls=List.copyOf(walls);wallGasMoles=List.copyOf(wallGasMoles); }
    }

    private static boolean manifoldFiniteFlowObserved(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,java.util.function.Consumer<ManifoldFlowProof> capture) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var baseline=saved.demoBaselines().get("manifold");if(baseline==null)return false;
        BlockPos host=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MANIFOLD));
        if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity manifold))return false;
        var inventory=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        var observed=ManifoldFixtureObservation.observe(manifold,host,inventory,saved);
        if(!observed.valid()||!observed.observed()||observed.ports().size()!=6)return false;
        for(var port:observed.ports())if(port.nowPlasma()<=1e-6||port.nowNitrogen()<=1e-6
                ||Math.abs(port.nowMoles()-port.beforeMoles())<=1e-6)return false;
        var source=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD&&e.role()==AtmosMachineryRoomCircuitPlan.Role.MANIFOLD_IN).findFirst().orElseThrow();
        var receiver=circuits.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD&&e.role()==AtmosMachineryRoomCircuitPlan.Role.MANIFOLD_OUT).findFirst().orElseThrow();
        var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        String src=AtmosMachineryFixtureService.roomMeasurementKey(source)+".plasmaMoles",dst=AtmosMachineryFixtureService.roomMeasurementKey(receiver)+".plasmaMoles";
        if(!(rooms.getOrDefault(src,Double.NaN)<baseline.getOrDefault(src,Double.NaN)-1e-6
                &&rooms.getOrDefault(dst,Double.NaN)>baseline.getOrDefault(dst,Double.NaN)+1e-6))return false;
        try {
            var topology=manifoldTopology(level,origin,plan,circuits,true,true);
            double sourceBefore=baseline.getOrDefault(src,Double.NaN),sourceNow=rooms.getOrDefault(src,Double.NaN);
            double recipientBefore=baseline.getOrDefault(dst,Double.NaN),recipientNow=rooms.getOrDefault(dst,Double.NaN);
            capture.accept(new ManifoldFlowProof(observed,sourceBefore,sourceNow,recipientBefore,recipientNow,topology.walls(),topology.wallGasMoles(),topology.appearanceMask(),level.getGameTime()));
            return true;
        } catch(GameTestAssertException waiting){return false;}
    }

    private static void assertCapturedManifoldFlow(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,ManifoldFlowProof proof) {
        if(proof==null||!proof.observation().valid()||!proof.observation().observed()||proof.observation().ports().size()!=6
                ||proof.sourceNow()>=proof.sourceBefore()-1e-6||proof.recipientNow()<=proof.recipientBefore()+1e-6
                ||proof.walls().size()!=6||proof.wallGasMoles().size()!=6||proof.wallGasMoles().stream().anyMatch(moles->!Double.isFinite(moles)||moles<=1e-6)
                ||proof.appearanceMask()!=0x3f)
            throw new GameTestAssertException("manifold finite-flow readiness proof incomplete at tick "+level.getGameTime()+": ready="+proof);
        BlockPos host=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MANIFOLD));
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var machine=(AtmosMachineBlockEntity)level.getBlockEntity(host);
        var inventory=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
        var currentObservation=ManifoldFixtureObservation.observe(machine,host,inventory,saved);
        var current=manifoldTopology(level,origin,plan,circuits,false,true);
        if(!currentObservation.valid()||!proof.walls().equals(current.walls()))
            throw new GameTestAssertException("manifold port/wall/stub identity changed after finite-flow readiness at tick "+level.getGameTime()+" (ready tick "+proof.readyTick()+")");
    }

    private static boolean thermalTransferObserved(ServerLevel level,BlockPos origin,List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,
            AtmosMachineryFixturePlan.Kind kind) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var baseline=saved.demoBaselines().get(kind.name().toLowerCase(Locale.ROOT));if(baseline==null)return false;
        var endpoint=circuits.stream().filter(e->e.station()==kind&&(e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM)).findFirst().orElseThrow();
        String roomKey=AtmosMachineryFixtureService.roomMeasurementKey(endpoint);
        var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        if(!(level.getBlockEntity(endpoint.machine()) instanceof AtmosMachineBlockEntity machine))return false;
        if(kind==AtmosMachineryFixturePlan.Kind.HEATER||kind==AtmosMachineryFixturePlan.Kind.FREEZER) {
            double before=baseline.getOrDefault(roomKey+".temperatureK",Double.NaN),now=rooms.getOrDefault(roomKey+".temperatureK",Double.NaN);
            double paid=machine.committedPaidJoules()-baseline.getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".paidJ",0d);
            double work=machine.committedThermalJoules()-baseline.getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".thermalJ",0d);
            return kind==AtmosMachineryFixturePlan.Kind.HEATER?AtmosMachineryThermalGauge.warming(before,now,paid,work)
                    :AtmosMachineryThermalGauge.cooling(before,now,paid,work);
        }
        var port=machine.gasPorts().stream().findFirst().orElse(null);if(port==null)return false;
        var pipe=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot)
                .flatMap(snapshot->snapshot.get(port.key(endpoint.machine()))).orElse(null);
        if(pipe==null)return false;
        double roomBefore=baseline.getOrDefault(roomKey+".thermalJ",Double.NaN),roomNow=rooms.getOrDefault(roomKey+".thermalJ",Double.NaN);
        double pipeBefore=baseline.getOrDefault("port.passive_exchanger."+port.id()+".thermalJ",Double.NaN);
        double work=machine.committedThermalJoules()-baseline.getOrDefault("work.passive_exchanger.thermalJ",0d);
        return AtmosMachineryThermalGauge.oppositeExchange(roomBefore,roomNow,pipeBefore,PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().nodeShare(port.key(endpoint.machine())).thermalEnergy(),work);
    }

    private static void assertThermalIndicator(ServerLevel level,BlockPos origin,List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,
            AtmosMachineryFixturePlan.Kind kind,boolean expectedOn) {
        var endpoint=circuits.stream().filter(e->e.station()==kind&&(e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM)).findFirst().orElseThrow();
        var pos=endpoint.room().labelPosition().above();
        if(!level.getBlockState(pos).is(Blocks.REDSTONE_LAMP))throw new GameTestAssertException("measured-temperature gauge lamp missing for "+kind);
        boolean actual=level.getBlockState(pos).getValue(RedstoneLampBlock.LIT);
        boolean observed=thermalTransferObserved(level,origin,circuits,kind);
        if(actual!=observed||actual!=expectedOn)throw new GameTestAssertException("thermal visual does not match measured committed transfer for "+kind+": lit="+actual+", observed="+observed);
    }

    private static boolean thermalGaugePresented(ServerLevel level,BlockPos origin,List<AtmosMachineryRoomCircuitPlan.Endpoint> circuits,
            AtmosMachineryFixturePlan.Kind kind) {
        var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);if(entry==null)return false;
        var endpoint=circuits.stream().filter(e->e.station()==kind&&(e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM)).findFirst().orElse(null);if(endpoint==null)return false;
        var sign=level.getBlockEntity(endpoint.room().labelPosition());
        if(!(sign instanceof net.minecraft.world.level.block.entity.SignBlockEntity blockSign))return false;
        boolean started=entry.reservedDemos().contains(kind.name().toLowerCase(Locale.ROOT));
        boolean observed=thermalTransferObserved(level,origin,circuits,kind);
        var baseline=entry.demoBaselines().get(kind.name().toLowerCase(Locale.ROOT));
        String expected;
        if(!started)expected=kind==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER?"WAITING · START HX":"WAITING · START DEMO";
        else if(baseline==null||baseline.isEmpty())expected=kind==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER?"START RESERVED · NO BASELINE":"START RESERVED · NO BASELINE";
        else if(!observed)expected=kind==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER?"NO MEASURED EXCHANGE":"NO MEASURED TRANSFER";
        else if(kind==AtmosMachineryFixturePlan.Kind.HEATER)expected="MEASURED WARMING";
        else if(kind==AtmosMachineryFixturePlan.Kind.FREEZER)expected="MEASURED COOLING";
        else {
            var port=((AtmosMachineBlockEntity)level.getBlockEntity(endpoint.machine())).gasPorts().stream().findFirst().orElseThrow();
            var pipe=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot)
                    .flatMap(snapshot->snapshot.get(port.key(endpoint.machine()))).orElse(null);
            expected=pipe==null?"NO MEASURED EXCHANGE":PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().nodeShare(port.key(endpoint.machine())).thermalEnergy()>baseline.getOrDefault("port.passive_exchanger."+port.id()+".thermalJ",Double.NaN)
                    ?"MEASURED ROOM → PIPE":"MEASURED PIPE → ROOM";
        }
        var front=blockSign.getFrontText();String shown=front.getMessage(1,false).getString();
        boolean lit=level.getBlockState(endpoint.room().labelPosition().above()).getValue(RedstoneLampBlock.LIT);
        if(lit!=observed||!expected.equals(shown))return false;
        if((kind==AtmosMachineryFixturePlan.Kind.HEATER||kind==AtmosMachineryFixturePlan.Kind.FREEZER)
                &&(!front.getMessage(2,false).getString().startsWith("T0 ")||!front.getMessage(3,false).getString().contains("ΔT")))return false;
        if(kind==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER
                &&(!front.getMessage(2,false).getString().startsWith("Room ΔE ")||!front.getMessage(3,false).getString().startsWith("Pipe ΔE ")))return false;
        return true;
    }

    // Ownership mismatch refuses before finite-room admission; no unrelated discovery wait is needed.
    @GameTest(template="atmos_machinery_lab_clear",templateNamespace="moonstation14",timeoutTicks=120)
    @GameTestTags({"atmos-machinery-fixture-individual-start-stale-owner"})
    public static void staleOtherMachineOwnershipRefusesBeforeSelectedRoomWrites(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        if(!AtmosMachineryFixtureService.build(level,origin).startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed");
        var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
        var freezerPos=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FREEZER));
        var facing=level.getBlockState(freezerPos).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING);
        level.setBlock(freezerPos,Blocks.AIR.defaultBlockState(),3);
        if(level.getBlockEntity(freezerPos)!=null)throw new GameTestAssertException("ordinary removal did not remove stale freezer BE");
        level.setBlock(freezerPos,com.juicyslew.moonstation14.block.ModBlocks.GAS_THERMO_FREEZER.get().defaultBlockState()
                .setValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING,facing),3);
        if(!(level.getBlockEntity(freezerPos) instanceof AtmosMachineBlockEntity replacement))throw new GameTestAssertException("replacement machine missing");
        replacement.reconcileLoadedPorts(level);
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var gas=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
        var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        String reason=AtmosMachineryFixtureService.start(level,origin,AtmosMachineryFixturePlan.Kind.MIXER);
        if(!reason.contains("saved fixture ownership is no longer current"))throw new GameTestAssertException("stale selected machine did not fail at ownership preflight: "+reason);
        var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
        int result=executeCommand(level,source,startCommand("mixer",origin));
        var after=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        if(result!=0||!saved.equals(after)||!gas.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())
                ||!rooms.equals(AtmosMachineryFixtureService.roomMeasurements(level,origin)))
            throw new GameTestAssertException("stale ownership was not refused mutation-free before selected gas writes");
        h.succeed();
    }

    // 400-tick room discovery + 6-tick owned-gate observation + 12-tick normal miner production cadence.
    @GameTest(template="atmos_machinery_lab_clear",templateNamespace="moonstation14",timeoutTicks=430)
    @GameTestTags({"atmos-machinery-fixture-miner-identity-gate"})
    public static void fixtureMinerProductionGateUsesExactSavedPlacementIdentity(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        if(!AtmosMachineryFixtureService.build(level,origin).startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed");
        var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
        PowerFixtureReadiness.await(h,level,400,
                    ()->lease.entityTicking(origin)&&lease.entityTicking(origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1))
                            &&fixtureRoomsReadyForTrustedSetup(level,origin,List.of(),false,true)
                            &&requestOwnedMinerTargetReadiness(level,origin),
                    ()->minerTargetReadinessFailureFacts(level,origin,lease),()->{
                var room=AtmosMachineryLaboratoryRooms.miners(origin).getFirst();
                if(!(level.getBlockEntity(room.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity original))throw new GameTestAssertException("fixture miner missing");
                var owner=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow().minerOwners().stream()
                        .filter(o->o.host().equals(room.machine())).findFirst().orElseThrow();
                UUID oldIdentity=original.placementIdentity();
                if(!owner.placementIdentity().equals(oldIdentity)||original.producedMoles()!=0)throw new GameTestAssertException("fixture miner was not exact-owned and initially gated");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,6,()->"owned fixture miner emitted before explicit all",
                        ()->h.getTick()>=4,()->{
                    if(original.producedMoles()!=0)throw new GameTestAssertException("saved-identity fixture miner produced before all");
                    var oldState=level.getBlockState(room.machine());
                    level.setBlock(room.machine(),Blocks.AIR.defaultBlockState(),3);
                    if(level.getBlockEntity(room.machine())!=null)throw new GameTestAssertException("ordinary removal did not remove the old miner BE");
                    level.setBlock(room.machine(),oldState,3);
                    if(!(level.getBlockEntity(room.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity replacement)
                            ||replacement==original)throw new GameTestAssertException("ordinary block replacement retained the old miner BE");
                    UUID newIdentity=replacement.placementIdentity();
                    var replacementEntry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(newIdentity==null||newIdentity.equals(oldIdentity)||replacementEntry.ownsMiner(room.machine(),newIdentity))
                        throw new GameTestAssertException("replacement miner incorrectly inherited fixture ownership");
                    if(replacement.resolvedProfile()==null||com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                            .finiteRoomEndpointForTrustedSimulation(level,room.machine()).isEmpty())
                        throw new GameTestAssertException("unowned replacement lacks its resolved source/finite destination");
                    com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,12,
                            ()->"unowned replacement did not produce under normal miner cadence; finiteTarget="
                                    +com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,room.machine()).isPresent()
                                    +", produced="+replacement.producedMoles(),
                            ()->replacement.producedMoles()>0&&com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                                    .finiteRoomEndpointForTrustedSimulation(level,room.machine()).isPresent(),()->h.succeed());
                });
            });
    }

    /** Re-enqueues real ownership discovery through the normal sample path if no finite endpoint exists yet. */
    private static boolean requestOwnedMinerTargetReadiness(ServerLevel level,BlockPos origin) {
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        boolean ready=true;
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,room.machine()).isEmpty())
                atmosphere.sample(level,room.machine());
            ready&=atmosphere.finiteRoomEndpointForTrustedSimulation(level,room.machine()).isPresent();
        }
        return ready;
    }

    private static String minerTargetReadinessFailureFacts(ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease) {
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        var facts=new StringBuilder("miner target readiness timeout; lease corners entityTicking=")
                .append(lease.entityTicking(origin)).append('/')
                .append(lease.entityTicking(origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1)));
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            var diagnostic=atmosphere.diagnosticCell(level,room.machine());
            var state=level.getBlockState(room.machine());
            facts.append("; miner=").append(room.machine()).append(" cell=").append(diagnostic.status())
                    .append(" passable=").append(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,room.machine()))
                    .append(" endpoint=").append(atmosphere.finiteRoomEndpointForTrustedSimulation(level,room.machine()).isPresent())
                    .append(" ownership=").append(atmosphere.diagnosticOwnership(level,room.machine()))
                    .append(" lease=").append(lease.entityTicking(room.machine()));
        }
        return facts.toString();
    }

    // 400-tick room discovery + the existing 120-tick auxiliary paid-work cadence budget.
    // 400-tick canonical finite-room discovery + existing 120-tick auxiliary work wait + bounded sequencing margin.
    @GameTest(template="atmos_machinery_lab_clear",templateNamespace="moonstation14",timeoutTicks=600)
    @GameTestTags({"atmos-machinery-fixture-individual-all-aux"})
    public static void explicitAllAfterNineIndividualStartsRunsAuxiliaryOnceWithoutReseedingDemos(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        if(!AtmosMachineryFixtureService.build(level,origin).startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed");
        var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
        var circuits=AtmosMachineryRoomCircuitPlan.standard(plan,origin);
        var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
        PowerFixtureReadiness.await(h,level,400,
                 ()->lease.entityTicking(origin)&&lease.entityTicking(origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1))&&fixtureRoomsReadyForTrustedSetup(level,origin,circuits.endpoints(),false,false)
                         &&auxiliaryVentMetersReady(level,origin),
                 ()->"all nine demonstration rooms or auxiliary runtime meters remain pending: "+auxiliaryReadinessFacts(level,origin,lease),
                ()->runNineIndividualDemosThenAux(h,level,origin,source,plan,circuits));
    }

    private static boolean fixtureRoomsReadyForTrustedSetup(ServerLevel level,BlockPos origin,
            java.util.Collection<AtmosMachineryRoomCircuitPlan.Endpoint> endpoints,boolean auxiliary,boolean miners) {
        return AtmosMachineryFixtureService.finiteRoomReadinessFailure(level,origin,endpoints,auxiliary,miners)==null;
    }

    /** Called only by the bounded readiness timeout supplier; all observations are read-only and loaded-only. */
    private static String commonStartFailure(ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease) {
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        var plan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        BlockPos fixture=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var cells=new java.util.ArrayList<BlockPos>(36);
        for(BlockPos p:plan.sourceCells())cells.add(fixture.offset(p));
        for(BlockPos p:plan.recipientCells())cells.add(fixture.offset(p));
        BlockPos missing=null;
        for(BlockPos p:cells)if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,p).isEmpty()){missing=p;break;}
        if(missing==null)missing=fixture.offset(plan.sourceCells().getFirst());
        var chunk=level.getChunkSource().getChunkNow(missing.getX()>>4,missing.getZ()>>4);
        return "common start readiness timeout; failed="+(cells.indexOf(missing)<plan.sourceCells().size()?"source":"recipient")
                +", target="+missing+", finiteEndpoint="
                +atmosphere.finiteRoomEndpointForTrustedSimulation(level,missing).isPresent()+", fullChunk="
                +(chunk!=null&&chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL))
                +", entityTicking="+lease.entityTicking(missing)+", ownership="+atmosphere.diagnosticOwnership(level,missing);
    }

    private static String legacyMixerFailure(ServerLevel level,BlockPos origin,
            List<AtmosMachineryRoomCircuitPlan.Endpoint> endpoints,FixtureChunkTickets.Lease lease) {
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        BlockPos missing=null;
        outer: for(var endpoint:endpoints)if(endpoint.station()==AtmosMachineryFixturePlan.Kind.MIXER)
            for(BlockPos cell:endpoint.room().roomCells())if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty()) {missing=cell;break outer;}
        if(missing==null)missing=origin;
        var chunk=level.getChunkSource().getChunkNow(missing.getX()>>4,missing.getZ()>>4);
        return "legacy mixer room timeout; first missing endpoint cell="+missing+", fullChunk="
                +(chunk!=null&&chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL))
                +", entityTicking="+lease.entityTicking(missing)+", ownership="+atmosphere.diagnosticOwnership(level,missing);
    }

    private static void runNineIndividualDemosThenAux(GameTestHelper h,ServerLevel level,BlockPos origin,
            net.minecraft.commands.CommandSourceStack source,AtmosMachineryFixturePlan plan,AtmosMachineryRoomCircuitPlan circuits) {
        var demos=List.of("pressure_pump","volume_pump","mixer","filter","valve","heater","freezer","heat_exchanger","manifold");
        for(int i=0;i<demos.size();i++) {
            if(executeCommand(level,source,startCommand(demos.get(i),origin))!=1)
                throw new GameTestAssertException("explicit individual demo refused after all room endpoints became finite: "+demos.get(i));
            var current=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var expectedCanonical=demos.subList(0,i+1).stream()
                    .map(AtmosMachineryFixtureCommands::parseDemo).map(java.util.Optional::orElseThrow)
                    .map(kind->kind.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            if(current.laboratoryStarted()||!current.reservedDemos().equals(expectedCanonical))
                throw new GameTestAssertException("individual start unexpectedly consumed the global auxiliary guard: "+demos.get(i));
        }
        var beforeAll=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        var demoBaselines=beforeAll.demoBaselines();
        var globalDemoBaseline=Map.copyOf(beforeAll.baseline());
        final boolean[] auxStarted={false};
        final AtomicReference<Map<String,Double>> immediateRoomBefore=new AtomicReference<>(),immediateRoomAfter=new AtomicReference<>();
        final PipeGasInventory[] immediatePipeBefore={null},immediatePipeAfter={null};
        com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.await(h,120,
                 ()->auxiliaryWorkFailureFacts(level,origin,auxStarted[0]),
                ()->{
                    if(auxStarted[0])return auxiliaryWorkObserved(level,origin);
                    var current=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    var pipe=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                    var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
                    int result=executeCommand(level,source,startCommand("all",origin));
                    if(result==1) {
                        auxStarted[0]=true;
                        immediateRoomBefore.set(rooms);
                        immediateRoomAfter.set(AtmosMachineryFixtureService.roomMeasurements(level,origin));
                        immediatePipeBefore[0]=pipe;
                        immediatePipeAfter[0]=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                        var started=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
                            for(BlockPos cell:AtmosMachineryLaboratoryRooms.passableCells(level,room)) {
                                var endpoint=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                                        .finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow(()->
                                                new GameTestAssertException("auxiliary all returned before miner cell had actual finite ownership: "+cell));
                                var sample=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.sample(level,cell).orElseThrow();
                                if(endpoint==null||sample.totalMoles()!=0)
                                    throw new GameTestAssertException("auxiliary all did not synchronously initialize owned miner chamber to vacuum at "+cell+": "+sample);
                            }
                            var miner=(com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity)level.getBlockEntity(room.machine());
                            String key="miner."+miner.resolvedProfile().gas().name().toLowerCase(Locale.ROOT)+"."+room.machine().getX()+"."+room.machine().getZ()+".producedMoles";
                            if(!started.baseline().containsKey(key)||!Double.isFinite(started.baseline().get(key)))
                                throw new GameTestAssertException("auxiliary all omitted miner production baseline: "+key);
                        }
                        for(var entry:beforeAll.demoBaselines().entrySet())
                            if(!Objects.equals(entry.getValue(),started.demoBaselines().get(entry.getKey())))
                                throw new GameTestAssertException("auxiliary all changed prior demo baseline: "+entry.getKey());
                        for(var entry:beforeAll.baseline().entrySet())
                            if(!Objects.equals(entry.getValue(),started.baseline().get(entry.getKey())))
                                throw new GameTestAssertException("auxiliary all changed prior global demo baseline: "+entry.getKey());
                        return false;
                    }
                    var after=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(!current.equals(after)||!pipe.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())
                            ||!rooms.equals(AtmosMachineryFixtureService.roomMeasurements(level,origin)))
                        throw new GameTestAssertException("not-ready auxiliary all attempt changed source inventories or saved baselines");
                    return false;
                },()->{
                    var after=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(!after.laboratoryStarted()||after.reservedDemos().size()!=demos.size()||!demoBaselines.equals(after.demoBaselines())
                            ||globalDemoBaseline.entrySet().stream().anyMatch(e->!Objects.equals(after.baseline().get(e.getKey()),e.getValue())))
                        throw new GameTestAssertException("auxiliary all reset demo reservations or per-demo baselines");
                    if(!immediateRoomBefore.get().equals(immediateRoomAfter.get())||!immediatePipeBefore[0].equals(immediatePipeAfter[0]))
                        throw new GameTestAssertException("auxiliary-only all reinitialized demo rooms or pipe inventories");
                    var currentGas=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                    var currentRooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
                    int repeat=executeCommand(level,source,startCommand("all",origin));
                    var repeated=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(repeat!=0||!after.equals(repeated)||!currentGas.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())
                            ||!currentRooms.equals(AtmosMachineryFixtureService.roomMeasurements(level,origin)))
                        throw new GameTestAssertException("repeated all changed completed auxiliary or demo state");
                    h.succeed();
                });
    }

    private static boolean auxiliaryWorkObserved(ServerLevel level,BlockPos origin) {
        var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
        if(!entry.laboratoryStarted())return false;
        var ventilationOrigin=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var ventPlan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        if(!(level.getBlockEntity(ventilationOrigin.offset(ventPlan.scrubber())) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity scrubber)
                ||!(level.getBlockEntity(ventilationOrigin.offset(ventPlan.vent())) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity vent))return false;
        if(!scrubber.config().settings().configuredEnabled()||!vent.config().settings().configuredEnabled())return false;
        var scrubPower=PowerRuntime.ventilationMeter(level,scrubber);var ventPower=PowerRuntime.ventilationMeter(level,vent);
        double scrubRequested=scrubber.prototype()==null?Double.NaN:scrubber.prototype().apcPowerReceiver().requestedWatts();
        double ventRequested=vent.prototype()==null?Double.NaN:vent.prototype().apcPowerReceiver().requestedWatts();
        boolean paid=Double.isFinite(scrubRequested)&&scrubRequested>0&&scrubPower.known()&&scrubPower.deliveredWatts()>=scrubRequested
                &&Double.isFinite(ventRequested)&&ventRequested>0&&ventPower.known()&&ventPower.deliveredWatts()>=ventRequested;
        boolean gas=scrubber.transferObservation().speciesMoles().values().stream().anyMatch(m->m>1e-6)
                &&vent.transferObservation().speciesMoles().values().stream().anyMatch(m->m>1e-6);
        boolean miners=AtmosMachineryLaboratoryRooms.miners(origin).stream().allMatch(room->
                level.getBlockEntity(room.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner&&miner.producedMoles()>0);
        return paid&&gas&&miners;
    }

    private static boolean auxiliaryVentMetersReady(ServerLevel level,BlockPos origin) {
        var vo=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var plan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        for(BlockPos p:List.of(plan.scrubber(),plan.vent())) {
            if(!(level.getBlockEntity(vo.offset(p)) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device)
                    ||device.prototype()==null)return false;
            double requested=device.prototype().apcPowerReceiver().requestedWatts();
            if(!Double.isFinite(requested)||requested<=0||!PowerRuntime.ventilationMeter(level,device).known())return false;
        }
        return true;
    }

    private static String auxiliaryReadinessFacts(ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease) {
        var vo=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var plan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        var facts=new StringBuilder("entityTicking=").append(lease.entityTicking(origin));
        for(BlockPos p:List.of(plan.scrubber(),plan.vent())) {
            BlockPos pos=vo.offset(p);
            if(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity d)
                facts.append("; device[").append(pos.toShortString()).append("] requestedW=")
                        .append(d.prototype()==null?"unknown":d.prototype().apcPowerReceiver().requestedWatts())
                        .append(" meter=").append(PowerRuntime.ventilationMeter(level,d));
            else facts.append("; device[").append(pos.toShortString()).append("]=missing");
        }
        return facts.toString();
    }

    private static String auxiliaryWorkFailureFacts(ServerLevel level,BlockPos origin,boolean admitted) {
        var vo=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var plan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        var facts=new StringBuilder("auxiliary work timeout: admitted=").append(admitted);
        for(BlockPos p:List.of(plan.scrubber(),plan.vent())) {
            BlockPos pos=vo.offset(p);
            if(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity d) {
                double requested=d.prototype()==null?Double.NaN:d.prototype().apcPowerReceiver().requestedWatts();
                facts.append("; device[").append(pos.toShortString()).append("] requestedW=").append(requested)
                        .append(" meter=").append(PowerRuntime.ventilationMeter(level,d)).append(" transfer=").append(d.transferObservation());
            }
        }
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            if(level.getBlockEntity(room.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity m) {
                var target=room.roomCells().stream().map(p->com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                        .finiteRoomEndpointForTrustedSimulation(level,p)).filter(java.util.Optional::isPresent).map(java.util.Optional::orElseThrow).findFirst().orElse(null);
                facts.append("; miner[").append(room.machine().toShortString()).append("] produced=").append(m.producedMoles())
                        .append(" targetFinite=").append(target!=null).append(" targetMoles=").append(target==null?"unavailable":target.snapshot().totalMoles());
            }
        }
        return facts.toString();
    }

    // Finite trusted source setup, NBT round-trip, and immediate public-refusal assertions.
    @GameTest(template="atmos_machinery_lab_clear",templateNamespace="moonstation14",timeoutTicks=500)
    @GameTestTags({"atmos-machinery-fixture-legacy-global-guard"})
    public static void legacyGlobalStartRefusesIndividualAndAllWithoutMutation(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->lease.entityTicking(origin)&&lease.entityTicking(origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1)),"legacy fixture footprint entity-ticking",()->{
        for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        if(!AtmosMachineryFixtureService.build(level,origin).startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed");
        var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
        var circuits=AtmosMachineryRoomCircuitPlan.standard(plan,origin);
             var sourceRoom=circuits.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER
                    &&e.role()==AtmosMachineryRoomCircuitPlan.Role.SOURCE
                    &&e.ports().contains(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.INLET)).findFirst().orElseThrow();
            var sourceCells=AtmosMachineryLaboratoryRooms.passableCells(level,sourceRoom.room());
             if(!com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.initializeEnclosedFiniteMixtureForTrustedSetup(level,sourceCells,
                     new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(Map.of(GasType.PLASMA,4d,GasType.WATER_VAPOR,5d),293.15)))
                 throw new GameTestAssertException("could not establish pre-existing legacy source inventory");
             var sourceCell=sourceCells.getFirst();
             var finiteSource=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,sourceCell)
                     .orElseThrow(()->new GameTestAssertException("legacy source setup did not retain a finite room endpoint"));
             var sourceGas=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.sample(level,sourceCell).orElseThrow();
             var finiteGas=finiteSource.snapshot();
             if(finiteGas.moles(GasType.PLASMA)<4-1e-6||finiteGas.moles(GasType.WATER_VAPOR)<5-1e-6
                     ||sourceGas.moles(GasType.PLASMA)<4-1e-6||sourceGas.moles(GasType.WATER_VAPOR)<5-1e-6)
                 throw new GameTestAssertException("canonical legacy source setup did not produce the expected finite 4 plasma + 5 water inventory");
             var writable=AtmosMachineryFixtureSavedData.forMutation(level);
             var built=writable.entry(origin).orElseThrow();
             var otherEntries=writable.entries().stream().filter(e->!e.origin().equals(origin))
                     .collect(java.util.stream.Collectors.toMap(AtmosMachineryFixtureSavedData.Entry::origin,e->e));
             if(!writable.markLaboratoryStarted(origin,built.expectedPortOwners(),built.minerOwners(),Map.of("legacy.marker",42d)))
                 throw new GameTestAssertException("could not construct old global-start fixture state");
             var serialized=writable.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
              var rows=serialized.getList("fixtures",net.minecraft.nbt.CompoundTag.TAG_COMPOUND);
              int ownRow=-1;
              for(int i=0;i<rows.size();i++) {
                  var row=rows.getCompound(i);
                  if(!row.contains("origin"))throw new GameTestAssertException("saved fixture row origin is missing");
                  BlockPos rowOrigin=BlockPos.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,row.get("origin"))
                          .getOrThrow(message->new IllegalStateException("saved fixture origin decode failed: "+message));
                 if(rowOrigin.equals(origin)) {
                     if(ownRow>=0)throw new GameTestAssertException("legacy NBT fixture origin is duplicated");
                     ownRow=i;
                 }
             }
             if(ownRow<0)throw new GameTestAssertException("legacy NBT row for this fixture origin is missing");
             var oldRow=rows.getCompound(ownRow);oldRow.remove("reservedDemos");oldRow.remove("demoBaselines");rows.set(ownRow,oldRow);
             serialized.put("fixtures",rows);
             var legacy=AtmosMachineryFixtureSavedData.load(serialized,level.registryAccess());
             try {
             level.getDataStorage().set(AtmosMachineryFixtureSavedData.DATA_NAME,legacy);
            var before=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var gas=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            int individual=executeCommand(level,source,startCommand("mixer",origin));
            int batch=executeCommand(level,source,startCommand("all",origin));
            var after=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(individual!=0||batch!=0||!before.equals(after)||!before.legacyGlobalStart()
                    ||!gas.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())
                    ||!rooms.equals(AtmosMachineryFixtureService.roomMeasurements(level,origin)))
                throw new GameTestAssertException("legacy global-start entry allowed reseeding, aux admission, or baseline mutation");
            var afterData=AtmosMachineryFixtureSavedData.existing(level).orElseThrow();
             if(afterData.entries().size()!=otherEntries.size()+1||otherEntries.entrySet().stream()
                    .anyMatch(e->!e.getValue().equals(afterData.entry(e.getKey()).orElse(null))))
                throw new GameTestAssertException("legacy NBT compatibility rewrite changed another fixture entry");
             for(var owner:after.roomDeviceOwners())if(level.getBlockEntity(owner.position()) instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity device
                     &&device.config().settings().configuredEnabled())throw new GameTestAssertException("legacy refusal enabled a room ventilation device");
             } finally {
                 level.getDataStorage().set(AtmosMachineryFixtureSavedData.DATA_NAME,writable);
             }
             h.succeed();
        },origin,origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1));
    }

    @GameTest(template="atmos_machinery_lab_clear", templateNamespace="moonstation14", timeoutTicks=1)
    @GameTestTags({"atmos-machinery-fixture-geometry"})
    public static void plannedRoomPipeGeometryIsReciprocal(GameTestHelper h) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        var catalog = com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog((ServerLevel) h.getLevel());
        var fixture = AtmosMachineryFixturePlan.standard(catalog::get);
        var plan = AtmosMachineryRoomCircuitPlan.standard(fixture, origin);
        for (var endpoint : plan.endpoints()) {
            // Thermal device endpoints are room-only; the exchanger room is
            // room-only as well. Neither has a pipe crossing to validate.
            if (endpoint.role() == AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM) continue;
            if (endpoint.ventHosts().isEmpty()) throw new GameTestAssertException("non-room-only endpoint has no vent host: "+endpoint);
            BlockPos vent = endpoint.ventHosts().getFirst();
            var facing = net.minecraft.core.Direction.getNearest(vent.getX()-endpoint.pipeStub().getX(), 0,
                    vent.getZ()-endpoint.pipeStub().getZ());
            if (!endpoint.pipeStub().relative(facing).equals(endpoint.wallCrossing())
                    || !endpoint.wallCrossing().relative(facing.getOpposite()).equals(endpoint.pipeStub()))
                throw new GameTestAssertException("planned back-pipe crossing is not reciprocal at " + endpoint.pipeStub());
        }
        h.succeed();
    }

    @GameTest(template="atmos_machinery_lab_clear", templateNamespace="moonstation14", timeoutTicks=200)
    @GameTestTags({"atmos-machinery-fixture-build", "atmos-machinery-fixture-status"})
    public static void publicServiceBuildOwnershipRepeatAndReadonlyStatus(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->true,"atmos machinery fixture footprint ticking",()->{
            for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            String built=AtmosMachineryFixtureService.build(level,origin);
            if(!built.startsWith("Built nine-machine "))throw new GameTestAssertException("public fixture service build failed: "+built);
            var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow(()->new GameTestAssertException("build did not save owner"));
            var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
            var owners=new HashSet<AtmosMachineryFixtureSavedData.PortOwner>();
            int hosts=0;
            for(var placement:plan.placements()) {
                BlockPos pos=origin.offset(placement.position());
                if(!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine))throw new GameTestAssertException("machine host missing at "+pos);
                if(!level.getBlockState(pos.below()).is(Blocks.IRON_BLOCK))throw new GameTestAssertException("machine lacks planned foundation at "+pos);
                hosts++;
                for(var port:machine.gasPorts())owners.add(new AtmosMachineryFixtureSavedData.PortOwner(pos,port.id(),port.incarnation()));
            }
            for(BlockPos relative:AtmosMachineryFixtureService.crossingValvePositions()) {
                BlockPos pos=origin.offset(relative);
                if(!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine))throw new GameTestAssertException("crossing valve host missing at "+pos);
                for(var port:machine.gasPorts())owners.add(new AtmosMachineryFixtureSavedData.PortOwner(pos,port.id(),port.incarnation()));
            }
            BlockPos crossing=origin.offset(AtmosMachineryFixtureService.crossingCenter());
            if(!(level.getBlockEntity(crossing) instanceof VisualPipeBlockEntity visual)
                    || !visual.hasLayer(VisualPipeBlockEntity.PRIMARY)
                    || !visual.hasLayer(VisualPipeBlockEntity.SECONDARY))throw new GameTestAssertException("crossing center lacks primary and secondary pipe layers");
            for(var label:plan.labels()) {
                BlockPos signPos=origin.offset(label.position());
                if(!(level.getBlockEntity(signPos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign))throw new GameTestAssertException("planned device label missing");
                String[] description=label.text().split("\\n",-1);
                String token=STATION_COMMAND_GOLDEN.get(label.kind());
                if(description.length!=2||token==null||token.contains("\n"))throw new GameTestAssertException("station label is not a compact two-line label plus complete literal: "+label.kind());
                assertLabel(level, signPos, sign,List.of(Component.literal(description[0]),Component.literal(description[1]),
                        Component.literal("start"),Component.literal(token)));
            }
            for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
                BlockPos signPos=room.labelPosition();
                if(!(level.getBlockEntity(signPos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign))throw new GameTestAssertException("miner label missing at "+signPos);
                Component actualName=level.getBlockState(room.machine()).getBlock().getName();
                assertLabel(level,signPos,sign,List.of(actualName));
            }
            BlockPos ventOrigin=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
            assertLabelAt(level,ventOrigin.offset(4,1,-1),List.of(Component.literal("Scrubber"),Component.literal("Source room")));
            assertLabelAt(level,ventOrigin.offset(12,1,-1),List.of(Component.literal("Vent"),Component.literal("Recipient room")));
            assertLabelAt(level,origin.offset(AtmosMachineryFixtureService.crossingCenter()).offset(3,0,3),List.of(Component.literal("Crossing"),Component.literal("Layers stay"),Component.literal("separate")));
            if(hosts!=9||!entry.initialized()||!new HashSet<>(entry.expectedPortOwners()).equals(owners))throw new GameTestAssertException("saved nine-host initialized ownership mismatch");
            if(entry.minerOwners().size()!=3)throw new GameTestAssertException("saved ownership does not include three miners");
            var inventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            var saved=entry;
            String repeated=AtmosMachineryFixtureService.build(level,origin);
            if(!repeated.startsWith("Build refused")||!AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow().equals(saved))throw new GameTestAssertException("second build was not refused without ownership change: "+repeated);
            String status=AtmosMachineryFixtureService.status(level,origin);
            if(status.toLowerCase(Locale.ROOT).contains("incarnation mismatch"))throw new GameTestAssertException("status reports mismatch for current fixture: "+status);
            if(!status.contains("Device work (committed readings; baseline → now)")
                    || !status.contains("pending start"))
                throw new GameTestAssertException("operator status lacks human-readable pending committed-gas receipts: "+truncate(status,500));
            if(!inventory.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot()))throw new GameTestAssertException("read-only status changed pipe inventory");
            var replacement=plan.placements().get(0);
            BlockPos host=origin.offset(replacement.position());
            level.setBlock(host,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(host,com.juicyslew.moonstation14.block.ModBlocks.GAS_PRESSURE_PUMP.get().defaultBlockState(),3);
            String mismatch=AtmosMachineryFixtureService.status(level,origin);
            if(!mismatch.contains("incarnation mismatch"))throw new GameTestAssertException("replaced host did not report ownership mismatch: "+mismatch);
            h.succeed();
        },origin,origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1));
    }

    private static void assertLabelAt(ServerLevel level,BlockPos pos,List<? extends Component> expected) {
        if(!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign))throw new GameTestAssertException("label missing at "+pos);
        assertLabel(level,pos,sign,expected);
    }

    private static void assertLabel(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.entity.SignBlockEntity sign,List<? extends Component> expected) {
        var text=sign.getFrontText();
        for(int line=0;line<4;line++) {
            Component wanted=line<expected.size()?expected.get(line):Component.empty();
            Component actual=text.getMessage(line,false);
            if(!actual.equals(wanted))throw new GameTestAssertException("wrong label line "+line+" at "+pos+": "+actual);
        }
        if(!level.getBlockState(pos.below()).is(Blocks.IRON_BLOCK))throw new GameTestAssertException("label lacks foundation at "+pos);
    }

    @GameTest(template="atmos_machinery_lab_clear", templateNamespace="moonstation14", timeoutTicks=600)
    @GameTestTags({"atmos-machinery-fixture-runtime", "atmosphere"})
    public static void builtLabMachinesReceiveApcPowerAndFreezerCoolsFiniteGas(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->true,"atmos machinery runtime footprint ticking",()->{
            for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            String built=AtmosMachineryFixtureService.build(level,origin);
            if(!built.startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed: "+built);
            var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
              var freezer=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FREEZER)));
             var filter=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FILTER)));
              if(filter.settingsSnapshot().filterGas().orElse(null)!=GasType.PLASMA
                     ||filter.settingsSnapshot().enabled().orElse(true))
                          throw new GameTestAssertException("fixture filter must select plasma while remaining disabled before startup");
             final String[] startResult={"not attempted"};
             PowerFixtureReadiness.await(h,level,600,
                     () -> {
                         var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
                         if(entry==null||!entry.laboratoryStarted())startResult[0]=AtmosMachineryFixtureService.startAll(level,origin);
                         return cooledFreezerRoom(level,origin,plan);
                     },
                     () -> "start="+startResult[0]+", freezer APC="+freezer.apcAllocatedWatts()+", room="+cooledFreezerRoomFacts(level,origin,plan)+", status="+AtmosMachineryFixtureService.status(level,origin),
                     () -> h.succeed());
        },origin,origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1));
    }

    private static boolean cooledFreezerRoom(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(saved==null||!saved.laboratoryStarted())return false;
        var endpoint=AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints().stream()
                .filter(e->e.station()==AtmosMachineryFixturePlan.Kind.FREEZER&&e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM).findFirst().orElseThrow();
        var current=AtmosMachineryFixtureService.roomMeasurements(level,origin);String key=AtmosMachineryFixtureService.roomMeasurementKey(endpoint);
        double now=current.getOrDefault(key+".temperatureK",Double.NaN),before=saved.baseline().getOrDefault(key+".temperatureK",Double.NaN);
        var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FREEZER)));
        double paid=machine.committedPaidJoules()-saved.baseline().getOrDefault("work.freezer.paidJ",0d);
        double thermal=machine.committedThermalJoules()-saved.baseline().getOrDefault("work.freezer.thermalJ",0d);
        return Double.isFinite(now)&&Double.isFinite(before)&&now<before-1e-6&&paid>0&&thermal<0;
    }
    private static String cooledFreezerRoomFacts(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(saved==null)return "startup baseline unavailable";
        var endpoint=AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.FREEZER&&e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM).findFirst().orElseThrow();
        var current=AtmosMachineryFixtureService.roomMeasurements(level,origin);String key=AtmosMachineryFixtureService.roomMeasurementKey(endpoint);
        return "T="+current.getOrDefault(key+".temperatureK",Double.NaN)+" baseline="+saved.baseline().getOrDefault(key+".temperatureK",Double.NaN);
    }

    @GameTest(template="atmos_machinery_lab_clear", templateNamespace="moonstation14", timeoutTicks=1400)
    @GameTestTags({"atmos-laboratory-functional", "common-start"})
    public static void commonStartImmediatelyAfterBuildProvesAndSeedsUnownedRooms(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->true,"immediate trusted lab startup",()->{
            for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            String built=AtmosMachineryFixtureService.build(level,origin);
            if(!built.startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed: "+built);
            var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
            var ventPlan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
            BlockPos ventOrigin=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
            var ventSource=ventPlan.sourceCells().stream().map(ventOrigin::offset).toList();
            if(ventSource.stream().anyMatch(p->atmosphere.finiteRoomEndpointForTrustedSimulation(level,p).isPresent()))
                throw new GameTestAssertException("fresh laboratory source unexpectedly had finite-owned cells before startup");
            BlockPos proofCell=ventSource.getFirst();
            var proofChunk=level.getChunkSource().getChunkNow(proofCell.getX()>>4,proofCell.getZ()>>4);
            if(proofChunk==null)throw new GameTestAssertException("trusted setup proof chunk is not loaded");
            var proofData=proofChunk.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ATMOSPHERE_CHUNK.get());
            var proofClaims=proofData==null?0:proofData.claimCount();
            var proofGas=proofData==null?java.util.Map.of():proofData.snapshot();
            boolean proofDirty=proofChunk.isUnsaved();
            if(!atmosphere.trustedSetupRoomReady(level,ventSource,null))throw new GameTestAssertException("closed unowned laboratory source failed trusted setup proof");
            if(proofChunk.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.ATMOSPHERE_CHUNK.get())!=proofData
                    ||(proofData!=null&&(proofData.claimCount()!=proofClaims||!proofData.snapshot().equals(proofGas)))||proofChunk.isUnsaved()!=proofDirty)
                throw new GameTestAssertException("read-only source proof changed attachment, ownership, gas, or dirty state");
            // No world tick, sample, or manual claim between successful read-only proof and public startup.
            String started=AtmosMachineryFixtureService.startAll(level,origin);
            if(!started.startsWith("Started "))throw new GameTestAssertException("immediate common start rejected: "+started);
            var entry=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(!entry.laboratoryStarted()||entry.baseline().isEmpty())throw new GameTestAssertException("immediate start omitted one-shot baseline");
            var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
            for(var endpoint:AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints())
                for(BlockPos cell:endpoint.room().roomCells())
                    if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())
                        throw new GameTestAssertException("post-start room cell is not actually finite/owned: "+cell);
            for(BlockPos cell:ventSource) {
                var mixture=atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot();
                if(Math.abs(mixture.moles(GasType.OXYGEN)-8)>1e-5||Math.abs(mixture.moles(GasType.NITROGEN)-30)>1e-5
                        ||Math.abs(mixture.moles(ventPlan.testGas())-ventPlan.testGasMolesPerCell())>1e-5||Math.abs(mixture.temperatureKelvin()-300)>1e-4)
                    throw new GameTestAssertException("vent source stock mismatch at "+cell+": "+mixture);
            }
            for(BlockPos local:ventPlan.recipientCells()) {
                var mixture=atmosphere.finiteRoomEndpointForTrustedSimulation(level,ventOrigin.offset(local)).orElseThrow().snapshot();
                if(mixture.totalMoles()!=0)throw new GameTestAssertException("vent recipient is not vacuum at "+ventOrigin.offset(local));
            }
            h.succeed();
        },origin,end);
    }

    @GameTest(template="atmos_machinery_lab_clear", templateNamespace="moonstation14", timeoutTicks=1400)
    @GameTestTags({"atmos-laboratory-functional", "common-start"})
    public static void commonStartSeedsOnceAndProducesFiniteLabChanges(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(BlockPos.ZERO);
        BlockPos end=origin.offset(AtmosMachineryFixturePlan.WIDTH,AtmosMachineryFixturePlan.HEIGHT,AtmosMachineryFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        FixtureChunkTickets.awaitEntityTicking(h,lease,100,()->true,"common-start laboratory footprint",()->{
            for(int x=0;x<AtmosMachineryFixturePlan.WIDTH;x++)for(int y=0;y<AtmosMachineryFixturePlan.HEIGHT;y++)for(int z=0;z<AtmosMachineryFixturePlan.DEPTH;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
             String built=AtmosMachineryFixtureService.build(level,origin);
              if(!built.startsWith("Built nine-machine "))throw new GameTestAssertException("fixture build failed: "+built);
             var buildPlan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
             for(var endpoint:AtmosMachineryRoomCircuitPlan.standard(buildPlan,origin).endpoints()) {
                  if(endpoint.ventHosts().isEmpty())continue;
                  var crossings=new java.util.ArrayList<BlockPos>();
                  if(endpoint.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM)crossings.addAll(endpoint.additionalWallCrossings());
                  else crossings.add(endpoint.wallCrossing());
                 for(BlockPos crossing:crossings) {
                     if(!level.getBlockState(crossing).is(com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get()))
                         throw new GameTestAssertException("public build crossing material is not steel at "+crossing);
                     var record=com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallSegment.matchingLive(level,crossing,com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY).orElse(null);
                     var snapshot=com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallGasOwner.activeSnapshot(level,crossing,com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY).orElse(null);
                     if(record==null||snapshot==null||!record.id().equals(snapshot.incarnation()))
                         throw new GameTestAssertException("public build crossing lacks matching live active primary owner at "+crossing);
                 }
             }
             var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
             var initialPlan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
             BlockPos valvePos=origin.offset(initialPlan.station(AtmosMachineryFixturePlan.Kind.VALVE));
             var closedValve=(AtmosMachineBlockEntity)level.getBlockEntity(valvePos);
             if(closedValve.settingsSnapshot().valveOpen().orElse(true))throw new GameTestAssertException("fixture valve must begin closed");
             final int[] attempts={0};
             final String[] lastStartResult={"not attempted"};
             final List<LoadedPipeComponent.Node>[] valveSourceNodes=new List[]{null};
             final List<LoadedPipeComponent.Node>[] valveReceiverNodes=new List[]{null};
             final ComponentTotals[] valveSourceBefore={null},valveReceiverBefore={null},valveSourceRoomBefore={null},valveReceiverRoomBefore={null};
             final String[] valveSourceRoomKey={null},valveReceiverRoomKey={null};
            PowerFixtureReadiness.await(h,level,400,()->{
                var current=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                 if(current.laboratoryStarted())throw new GameTestAssertException("startup guard set before successful test start; lastStartResult="+lastStartResult[0]+", attempts="+attempts[0]);
                 // Pipe components equalize naturally while APC readiness is pending. Capture
                 // immediately before the synchronous start, not immediately after build;
                 // the latter incorrectly treats the original per-port seed as immutable.
                 var closedValveGas=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                 var valvePorts=closedValve.gasPorts();
                 var inletPort=valvePorts.stream().filter(p->p.id().equalsIgnoreCase("inlet")).findFirst().orElseThrow();
                 var outletPort=valvePorts.stream().filter(p->p.id().equalsIgnoreCase("outlet")).findFirst().orElseThrow();
                 var sourceComponent=LoadedPipeComponent.discover(level,inletPort.key(valvePos)).orElseThrow(()->new GameTestAssertException("closed valve inlet component unavailable"));
                 var receiverComponent=LoadedPipeComponent.discover(level,outletPort.key(valvePos)).orElseThrow(()->new GameTestAssertException("closed valve outlet component unavailable"));
                 if(sourceComponent.nodes().stream().anyMatch(n->receiverComponent.nodes().stream().anyMatch(r->r.key().equals(n.key()))))
                     throw new GameTestAssertException("closed valve must separate inlet and outlet components");
                  var sourceBefore=componentTotals(sourceComponent.nodes(),closedValveGas);
                  var receiverBefore=componentTotals(receiverComponent.nodes(),closedValveGas);
                 String result=AtmosMachineryFixtureService.startAll(level,origin);
                lastStartResult[0]=result;
                if(result.startsWith("Started ")) {
                     var started=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                     if(!started.laboratoryStarted()||started.baseline().isEmpty())throw new GameTestAssertException("successful start did not save guard and nonempty baseline");
                      for(var port:closedValve.gasPorts()) {
                          var initial=closedValveGas.nodeShare(port.key(valvePos)).totalMoles();
                          var admitted=started.baseline().get("port.valve."+port.id()+".moles");
                          if(admitted==null||Math.abs(initial-admitted)>1e-9)throw new GameTestAssertException("start baseline lost pre-activation closed-valve inventory for "+port.id());
                      }
                      var startInventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                      var sourceAtStart=componentTotals(sourceComponent.nodes(),startInventory);
                      var receiverAtStart=componentTotals(receiverComponent.nodes(),startInventory);
                       valveSourceNodes[0]=sourceComponent.nodes(); valveReceiverNodes[0]=receiverComponent.nodes();
                       var circuit=AtmosMachineryRoomCircuitPlan.standard(initialPlan,origin);
                       var sourceEp=circuit.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.VALVE&&e.role()==AtmosMachineryRoomCircuitPlan.Role.SOURCE).findFirst().orElseThrow();
                       var receiverEp=circuit.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.VALVE&&e.role()==AtmosMachineryRoomCircuitPlan.Role.RECEIVER).findFirst().orElseThrow();
                       var startRooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
                       valveSourceRoomKey[0]=AtmosMachineryFixtureService.roomMeasurementKey(sourceEp);
                       valveReceiverRoomKey[0]=AtmosMachineryFixtureService.roomMeasurementKey(receiverEp);
                       valveSourceBefore[0]=sourceAtStart; valveReceiverBefore[0]=receiverAtStart;
                       valveSourceRoomBefore[0]=roomTotals(startRooms,valveSourceRoomKey[0]);
                       valveReceiverRoomBefore[0]=roomTotals(startRooms,valveReceiverRoomKey[0]);
                     var filterPlan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
                     var filter=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(filterPlan.station(AtmosMachineryFixturePlan.Kind.FILTER)));
                      if(filter.settingsSnapshot().filterGas().orElse(null)!=GasType.PLASMA)
                          throw new GameTestAssertException("common start requires explicitly selected plasma filter gas");
                    var before=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                    String duplicate=AtmosMachineryFixtureService.startAll(level,origin);
                    lastStartResult[0]=duplicate;
                    var after=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                    if(!duplicate.startsWith("Start refused")||!started.equals(AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow())||!before.equals(after))throw new GameTestAssertException("repeat start changed saved ownership or gas");
                    var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
                    var heater=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.HEATER)));
                    var freezer=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FREEZER)));
                     if (heater.settingsSnapshot().thermostatSetpointK().orElse(Double.NaN)!=350 || freezer.settingsSnapshot().thermostatSetpointK().orElse(Double.NaN)!=250)
                         throw new GameTestAssertException("common start did not resolve heater/freezer targets: heater="+heater.settingsSnapshot().thermostatSetpointK()+", freezer="+freezer.settingsSnapshot().thermostatSetpointK());
                      return true;
                }
                attempts[0]++;
                return false;
            },()->"common start never became ready; attempts="+attempts[0]+", lastStartResult="+lastStartResult[0]+", status="+truncate(AtmosMachineryFixtureService.status(level,origin),500),()->{
                var plan=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
                var heater=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.HEATER)));
                var freezer=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FREEZER)));
                  var minerBaselines=new LinkedHashMap<BlockPos,Double>();
                 for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
                     if(!(level.getBlockEntity(room.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner))throw new GameTestAssertException("lab miner host missing at "+room.machine());
                     minerBaselines.put(room.machine().immutable(),miner.producedMoles());
                 }
                  final Map<BlockPos,Double> baselines=Map.copyOf(minerBaselines);
                                        PowerFixtureReadiness.await(h,level,ALL_FUNCTIONAL_PROGRESS_WAIT_TICKS,()->thermostatProgress(level,origin,plan,AtmosMachineryFixturePlan.Kind.HEATER)&&thermostatProgress(level,origin,plan,AtmosMachineryFixturePlan.Kind.FREEZER)&&visibleRoomProgress(level,origin).complete()&&minerProgress(level,origin,baselines)&&manifoldMixed(level,origin,plan)&&crossingIsolated(level,origin)&&exchangerProgress(level,origin,plan)&&flowMachinesProgress(level,origin,plan).complete()&&valveComponentFlow(level,valveSourceNodes[0],valveReceiverNodes[0],valveSourceBefore[0],valveReceiverBefore[0],valveSourceRoomBefore[0],valveReceiverRoomBefore[0],valveSourceRoomKey[0],valveReceiverRoomKey[0],origin).complete()&&ventilationProgress(level,origin),
                                  diagnosticOnce(()->runtimeDiagnostics(level,origin,plan,baselines,heater,freezer,valveSourceNodes[0],valveReceiverNodes[0],valveSourceBefore[0],valveReceiverBefore[0],valveSourceRoomBefore[0],valveReceiverRoomBefore[0],valveSourceRoomKey[0],valveReceiverRoomKey[0])),()->{
                                     String proof=AtmosMachineryFixtureService.status(level,origin);
                                        var selectedFilterGas=((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FILTER)))).settingsSnapshot().filterGas().orElseThrow();
                                        if(selectedFilterGas!=GasType.PLASMA||!mixerFlowObserved(level,origin,
                                                (AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MIXER))),
                                                origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MIXER)),
                                                PipeGasInventorySavedData.existing(level).orElseThrow().snapshot(),"outlet")
                                                ||!filterFlowObserved(level,origin,
                                                (AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FILTER))),
                                                origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FILTER)),
                                                PipeGasInventorySavedData.existing(level).orElseThrow().snapshot(),selectedFilterGas)
                                                ||!mixerReceiverRoomFlowObserved(level,origin,plan))
                                            throw new GameTestAssertException("common-start lacks forward finite Plasma/water-vapor mixer/filter work and receiver changes");
                                          for(String evidence:List.of("paid transfer observed","selected outlet "+AtmosMachineryFixtureProofSignFormatter.operatorGasLabel(selectedFilterGas)+" ","open room flow observed","APC ","paid ","thermal work observed","opposite heat transfer observed","Scrubber committed capture","capture and room decrease observed","Miner production receipts","ordinary crossing isolated"))
                                         if(!proof.contains(evidence))throw new GameTestAssertException(com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegContinuousFixtureTestSupport.boundedFailureMessage("common-start operator proof missing '"+evidence+"': "+truncate(proof,700)));
                                      var expectedSignStatuses=Map.of(
                                              AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP,"PAID TRANSFER",
                                              AtmosMachineryFixturePlan.Kind.VOLUME_PUMP,"PAID TRANSFER",
                                              AtmosMachineryFixturePlan.Kind.MIXER,"MIXED",
                                              AtmosMachineryFixturePlan.Kind.FILTER,"SEPARATED",
                                              AtmosMachineryFixturePlan.Kind.VALVE,"OPEN / FLOW",
                                                AtmosMachineryFixturePlan.Kind.HEATER,"PAID HEATING",
                                                AtmosMachineryFixturePlan.Kind.FREEZER,"PAID COOLING",
                                                AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER,"ROOM -> PIPE",
                                               AtmosMachineryFixturePlan.Kind.MANIFOLD,"ONE MIX/3 LANES");
                                      for(var label:plan.labels()) {
                                          BlockPos signPos=origin.offset(label.position());
                                          if(!(level.getBlockEntity(signPos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign))throw new GameTestAssertException("runtime proof sign missing for "+label.kind());
                                           var front=sign.getFrontText();String status=front.getMessage(1,false).getString();
                                           String first=front.getMessage(2,false).getString(),second=front.getMessage(3,false).getString();
                                           String signText=status+" "+first+" "+second;
                                            if(!expectedSignStatuses.get(label.kind()).equals(status))throw new GameTestAssertException(com.juicyslew.moonstation14.ms14.atmos.teg.fixture.TegContinuousFixtureTestSupport.boundedFailureMessage("status refresh did not publish measured "+expectedSignStatuses.get(label.kind())+" proof for "+label.kind()+": "+signText));
                                             if(label.kind()==AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER&&(!first.startsWith("Room -")||!second.startsWith("Pipe +")))throw new GameTestAssertException("exchanger machine sign lacks its actual room-to-pipe receipt: "+signText);
                                            if(label.kind()==AtmosMachineryFixturePlan.Kind.HEATER&&(!first.contains("→")||!second.startsWith("Paid ")))throw new GameTestAssertException("heater machine sign lacks actual temperature and paid-work receipt: "+signText);
                                            if(label.kind()==AtmosMachineryFixturePlan.Kind.FREEZER&&(!first.contains("→")||!second.startsWith("Paid ")))throw new GameTestAssertException("freezer machine sign lacks actual temperature and paid-work receipt: "+signText);
                                           if((label.kind()==AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP||label.kind()==AtmosMachineryFixturePlan.Kind.VOLUME_PUMP)&&(!first.startsWith("In ")||!second.startsWith("Out ")))throw new GameTestAssertException("pump sign lacks inlet/outlet quantities: "+signText);
                                            if(label.kind()==AtmosMachineryFixturePlan.Kind.FILTER) {
                                                var selected=((AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FILTER)))).settingsSnapshot().filterGas().orElse(null);
                                                String prefix=selected==null?"All -> outlet":AtmosMachineryFixtureProofSignFormatter.operatorGasLabel(selected)+" ";
                                                if(!first.startsWith(prefix))throw new GameTestAssertException("filter sign lacks current selected-species quantity/routing: "+signText);
                                            }
                                              if(label.kind()==AtmosMachineryFixturePlan.Kind.MIXER&&(!first.startsWith("PLASMA ")||!second.startsWith("H2O ")))throw new GameTestAssertException("mixer sign lacks measured Plasma/water-vapor fractions: "+signText);
                                             if(label.kind()==AtmosMachineryFixturePlan.Kind.MANIFOLD&&(!first.startsWith("PLASMA ")||!second.startsWith("N2 ")))throw new GameTestAssertException("manifold sign lacks measured species quantities: "+signText);
                                      }
                                       for(var kind:List.of(AtmosMachineryFixturePlan.Kind.HEATER,AtmosMachineryFixturePlan.Kind.FREEZER,
                                               AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER)) {
                                           if(!thermalGaugePresented(level,origin,AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints(),kind))
                                               throw new GameTestAssertException("live room gauge missed measured paid thermal change for "+kind);
                                           assertThermalIndicator(level,origin,AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints(),kind,true);
                                       }
                                       for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
                                          if(!(level.getBlockEntity(room.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner)
                                                  ||!(level.getBlockEntity(room.labelPosition()) instanceof net.minecraft.world.level.block.entity.SignBlockEntity minerSign))
                                              throw new GameTestAssertException("live miner proof sign/host missing at "+room.machine());
                                          double made=miner.producedMoles()-baselines.get(room.machine());
                                          var profile=miner.resolvedProfile();
                                          var expected=AtmosMachineryFixtureProofSignFormatter.miner(level.getBlockState(room.machine()).getBlock().getName(),true,made,profile.maxExternalPressureKpa());
                                          var front=minerSign.getFrontText();
                                          if(!"PRODUCED".equals(front.getMessage(1,false).getString())
                                                  ||!expected.first().equals(front.getMessage(2,false).getString())
                                                  ||!expected.second().equals(front.getMessage(3,false).getString()))
                                              throw new GameTestAssertException("miner receipt does not expose measured production delta: expected="+expected+", actual="+front.getMessage(1,false).getString()+" / "+front.getMessage(2,false).getString()+" / "+front.getMessage(3,false).getString());
                                      }
                                      BlockPos scrubberSign=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN).offset(4,1,-1);
                                      var scrubberGas=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard().testGas();
                                      if(!(level.getBlockEntity(scrubberSign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)
                                               ||!sign.getFrontText().getMessage(1,false).getString().equals("CAPTURED")
                                               ||!sign.getFrontText().getMessage(2,false).getString().startsWith(AtmosMachineryFixtureProofSignFormatter.operatorGasLabel(scrubberGas)+" ")
                                              ||!sign.getFrontText().getMessage(3,false).getString().startsWith("Room -"))
                                         throw new GameTestAssertException("status refresh did not publish scrubber capture receipt on its sign");
                                      BlockPos ventSign=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN).offset(12,1,-1);
                                      if(!(level.getBlockEntity(ventSign) instanceof net.minecraft.world.level.block.entity.SignBlockEntity receiverSign)
                                               ||!receiverSign.getFrontText().getMessage(1,false).getString().equals("TRANSFERRED")
                                               ||!receiverSign.getFrontText().getMessage(2,false).getString().startsWith("Moved ")
                                               ||!receiverSign.getFrontText().getMessage(3,false).getString().startsWith("Room +"))
                                          throw new GameTestAssertException("status refresh did not publish vent receiver receipt on its sign");
                                      // Pause the paid machines after they have produced real work. Historical
                                      // receipts must remain inspectable even when current allocation disappears.
                                      for(var kind:List.of(AtmosMachineryFixturePlan.Kind.HEATER,AtmosMachineryFixturePlan.Kind.FREEZER,
                                              AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP,AtmosMachineryFixturePlan.Kind.VOLUME_PUMP)) {
                                          var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(plan.station(kind)));
                                          machine.setControl("enabled",false);
                                          if(machine.committedPaidJoules()<=0||machine.committedWorkEvents()<=0)
                                              throw new GameTestAssertException("missing durable committed-work receipt after pause: "+kind);
                                      }
                                      String pausedProof=AtmosMachineryFixtureService.status(level,origin);
                                       for(String evidence:List.of("thermal work observed","paid transfer observed","APC "))
                                          if(!pausedProof.contains(evidence))throw new GameTestAssertException("idle status lost historical work proof '"+evidence+"': "+truncate(pausedProof,900));
                                      h.succeed();
                                 });
            });
        },origin,origin.offset(AtmosMachineryFixturePlan.WIDTH-1,0,AtmosMachineryFixturePlan.DEPTH-1));
    }

    private static String truncate(String value,int limit) {
        String suffix=" (truncated)";
        return value.length()<=limit?value:value.substring(0,Math.max(0,limit-suffix.length()))+suffix;
    }

    private record ComponentTotals(double oxygen,double nitrogen,double plasma,double other,double thermal) {
        double totalMoles(){return oxygen+nitrogen+plasma+other;}
    }
    private static ComponentTotals componentTotals(List<LoadedPipeComponent.Node> nodes,PipeGasInventory inventory) {
        double oxygen=0,nitrogen=0,plasma=0,other=0,thermal=0;
        for(var node:nodes) {
            var mixture=inventory.nodeShare(node.key());
            oxygen+=mixture.moles(GasType.OXYGEN); nitrogen+=mixture.moles(GasType.NITROGEN);plasma+=mixture.moles(GasType.PLASMA);
            for(var gas:GasType.values())if(gas!=GasType.OXYGEN&&gas!=GasType.NITROGEN&&gas!=GasType.PLASMA)other+=mixture.moles(gas);
            thermal+=mixture.thermalEnergy();
        }
        return new ComponentTotals(oxygen,nitrogen,plasma,other,thermal);
    }
    private static void requireComponentConserved(ComponentTotals before,ComponentTotals after,String where) {
        if(Math.abs(before.oxygen()-after.oxygen())>1e-6||Math.abs(before.nitrogen()-after.nitrogen())>1e-6
                ||Math.abs(before.other()-after.other())>1e-6||Math.abs(before.thermal()-after.thermal())>1e-3)
            throw new GameTestAssertException(where+" changed closed-component inventory: "+before+" -> "+after);
    }
    private static ComponentTotals roomTotals(Map<String,Double> readings,String key) {
        return new ComponentTotals(0,readings.getOrDefault(key+".nitrogenMoles",Double.NaN),
                readings.getOrDefault(key+".plasmaMoles",Double.NaN),0,
                readings.getOrDefault(key+".thermalJ",Double.NaN));
    }
    private record ValveFlow(boolean sourceTransfer,boolean receiverTransfer,boolean conserved,String conservationFacts) {
        boolean complete(){return sourceTransfer&&receiverTransfer&&conserved;}
        @Override public String toString(){return "source="+sourceTransfer+",receiver="+receiverTransfer+",conserved="+conserved+"("+conservationFacts+")";}
    }
    private static ValveFlow valveComponentFlow(ServerLevel level,List<LoadedPipeComponent.Node> sourceNodes,
                                                List<LoadedPipeComponent.Node> receiverNodes,
                                                ComponentTotals sourceBefore,ComponentTotals receiverBefore,
                                                ComponentTotals sourceRoomBefore,ComponentTotals receiverRoomBefore,
                                                String sourceRoomKey,String receiverRoomKey,BlockPos origin) {
         if(sourceNodes==null||receiverNodes==null||sourceBefore==null||receiverBefore==null||sourceRoomBefore==null||receiverRoomBefore==null)return new ValveFlow(false,false,false,"baseline unavailable");
        var inventory=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
         if(inventory==null)return new ValveFlow(false,false,false,"inventory unavailable");
        var sourceNow=componentTotals(sourceNodes,inventory); var receiverNow=componentTotals(receiverNodes,inventory);
        var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        var sourceRoomNow=roomTotals(rooms,sourceRoomKey);var receiverRoomNow=roomTotals(rooms,receiverRoomKey);
        double sourcePlasma=sourceRoomNow.plasma()+sourceNow.plasma()-sourceRoomBefore.plasma()-sourceBefore.plasma();
        double receiverPlasma=receiverRoomNow.plasma()+receiverNow.plasma()-receiverRoomBefore.plasma()-receiverBefore.plasma();
         boolean sourceTransfer=sourcePlasma< -1e-6,receiverTransfer=receiverPlasma>1e-6;
         boolean conserved=true; StringBuilder facts=new StringBuilder();
        for(GasType gas:GasType.values()) {
            double before=componentSpecies(sourceBefore,gas)+componentSpecies(receiverBefore,gas)+roomSpecies(sourceRoomBefore,gas)+roomSpecies(receiverRoomBefore,gas);
            double now=componentSpecies(sourceNow,gas)+componentSpecies(receiverNow,gas)+roomSpecies(sourceRoomNow,gas)+roomSpecies(receiverRoomNow,gas);
            // Fixture readback-rounding allowance only, not physical throughput tolerance.
             double delta=now-before; boolean speciesConserved=Math.abs(delta)<=ROOM_READBACK_ROUNDING_MOLES; conserved&=speciesConserved;
             if(facts.length()>0)facts.append(','); facts.append(gas).append("Δ=").append(delta).append("[ROOM_READBACK ±").append(ROOM_READBACK_ROUNDING_MOLES).append("]");
         }
         var sourcePipeNow=componentTotals(sourceNodes,inventory); var receiverPipeNow=componentTotals(receiverNodes,inventory);
         return new ValveFlow(sourceTransfer,receiverTransfer,conserved,facts+",pipePLΔ="+(sourcePipeNow.plasma()-sourceBefore.plasma())+"/"+(receiverPipeNow.plasma()-receiverBefore.plasma())+",roomPLΔ="+(sourceRoomNow.plasma()-sourceRoomBefore.plasma())+"/"+(receiverRoomNow.plasma()-receiverRoomBefore.plasma())+",combinedPLΔ="+sourcePlasma+"/"+receiverPlasma);
    }
    private static double roomSpecies(ComponentTotals totals,GasType gas) {
        return gas==GasType.PLASMA?totals.plasma():gas==GasType.NITROGEN?totals.nitrogen():0;
    }
    private static double componentSpecies(ComponentTotals totals,GasType gas) {
        return gas==GasType.OXYGEN?totals.oxygen():gas==GasType.PLASMA?totals.plasma():gas==GasType.NITROGEN?totals.nitrogen():totals.other();
    }

    private static boolean ventilationProgress(ServerLevel level,BlockPos origin) {
         BlockPos fixture=origin.offset(AtmosMachineryFixturePlan.VENTILATION_ORIGIN);
        var source=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureTelemetry.roomTotals(level,fixture,true).orElse(null);
        var recipient=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixtureTelemetry.roomTotals(level,fixture,false).orElse(null);
        if(source==null||recipient==null||!Double.isFinite(source.moles(GasType.PLASMA))||!Double.isFinite(recipient.moles(GasType.PLASMA))
                ||!(source.moles(GasType.PLASMA)<36.0-1e-6)||!(recipient.moles(GasType.PLASMA)>1e-6))return false;
        var plan=com.juicyslew.moonstation14.ms14.atmos.piping.fixture.VentilationFixturePlan.standard();
        var scrubber=level.getBlockEntity(fixture.offset(plan.scrubber()));
        var vent=level.getBlockEntity(fixture.offset(plan.vent()));
        if(!(scrubber instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity s)
                ||!(vent instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v))return false;
        var sp=PowerRuntime.ventilationMeter(level,s); var vp=PowerRuntime.ventilationMeter(level,v);
        return sp.known()&&sp.deliveredWatts()>=5&&vp.known()&&vp.deliveredWatts()>=5;
    }

    private static boolean thermostatProgress(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,AtmosMachineryFixturePlan.Kind kind) {
        BlockPos host=origin.offset(plan.station(kind));
        if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine)||machine.gasPorts().isEmpty())return false;
        var port=machine.gasPorts().getFirst();
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        var inventory=PipeGasInventorySavedData.existing(level).map(d->d.snapshot()).orElse(null);
        if(saved==null||inventory==null)return false;
        var entry=inventory.get(port.key(host)).orElse(null); if(entry==null)return false;
         String key=AtmosMachineryFixtureService.roomMeasurementKey(kind,AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM,List.of(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.valueOf(port.id().toUpperCase(Locale.ROOT))));
         var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
         var proof=new AtmosMachineryFixtureService.ThermalRoomProof(saved.baseline().getOrDefault(key+".temperatureK",Double.NaN),rooms.getOrDefault(key+".temperatureK",Double.NaN));
         String work="work."+kind.name().toLowerCase(Locale.ROOT)+".";
         double paid=machine.committedPaidJoules()-saved.baseline().getOrDefault(work+"paidJ",0d);
         double thermal=machine.committedThermalJoules()-saved.baseline().getOrDefault(work+"thermalJ",0d);
         return AtmosMachineryFixtureService.thermalRoomObserved(proof,kind,paid,thermal);
    }

    private static boolean manifoldMixed(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        BlockPos host=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MANIFOLD));
        if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine)||machine.gasPorts().size()!=6)return false;
        var inventory=PipeGasInventorySavedData.existing(level).map(d->d.snapshot()).orElse(null);
        if(inventory==null)return false;
        Double fraction=null;
        for(var port:machine.gasPorts()) {
            var entry=inventory.get(port.key(host)).orElse(null);
            if(entry==null||!entry.active())return false;
            var mixture=inventory.nodeShare(entry.node());
             double plasma=mixture.moles(GasType.PLASMA), water=mixture.moles(GasType.NITROGEN), total=plasma+water;
             if(!Double.isFinite(total)||plasma<=0||water<=0||total<=0)return false;
             double current=plasma/total;
            if(fraction==null)fraction=current; else if(Math.abs(current-fraction)>1e-6)return false;
        }
         // The shared 300 L owner must visibly receive plasma, not just retain
         // its seeded nitrogen lanes.
          double plasma=0;
          for(var port:machine.gasPorts())plasma+=inventory.nodeShare(port.key(host)).moles(GasType.PLASMA);
          return fraction!=null && plasma>1e-6;
    }

    private static boolean crossingIsolated(ServerLevel level,BlockPos origin) {
        BlockPos center=origin.offset(AtmosMachineryFixtureService.crossingCenter());
        var primary=LoadedPipeGasOwner.activeSnapshot(level,center,VisualPipeBlockEntity.PRIMARY).orElse(null);
        var secondary=LoadedPipeGasOwner.activeSnapshot(level,center,VisualPipeBlockEntity.SECONDARY).orElse(null);
        if(primary==null||secondary==null)return false;
        var inventory=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);if(inventory==null)return false;
        var p=inventory.nodeShare(primary.node()); var s=inventory.nodeShare(secondary.node());
        return p.moles(GasType.OXYGEN)>0&&p.moles(GasType.NITROGEN)<=1e-9
                &&s.moles(GasType.NITROGEN)>0&&s.moles(GasType.OXYGEN)<=1e-9;
    }

    private static boolean exchangerProgress(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        BlockPos host=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER));
        if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine))return false;
        var outlet=machine.gasPorts().stream().filter(port->port.id().toLowerCase(Locale.ROOT).contains("outlet")).findFirst().orElse(null);
        if(outlet==null)return false;
        var entry=PipeGasInventorySavedData.existing(level).map(d->d.snapshot().get(outlet.key(host)).orElse(null)).orElse(null);
        if(entry==null)return false;
         var outletGas=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().nodeShare(outlet.key(host));
         var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
         if(saved==null)return false;
         var roomNow=AtmosMachineryFixtureService.roomMeasurements(level,origin);
         String roomKey=AtmosMachineryFixtureService.roomMeasurementKey(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER,AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM,List.of());
          var hxBaseline=saved.demoBaselines().getOrDefault("passive_exchanger",Map.of());
          var roomProof=new AtmosMachineryFixtureService.ExchangerRoomProof(hxBaseline.getOrDefault(roomKey+".temperatureK",Double.NaN),roomNow.getOrDefault(roomKey+".temperatureK",Double.NaN),
                  hxBaseline.getOrDefault(roomKey+".thermalJ",Double.NaN),roomNow.getOrDefault(roomKey+".thermalJ",Double.NaN));
          double thermal=machine.committedThermalJoules()-hxBaseline.getOrDefault("work.passive_exchanger.thermalJ",0d);
          double pipeBefore=hxBaseline.getOrDefault("port.passive_exchanger."+outlet.id()+".thermalJ",Double.NaN);
          double pipeDelta=outletGas.thermalEnergy()-pipeBefore;
          return outletGas.moles(GasType.PLASMA)>0&&Double.isFinite(outletGas.temperatureKelvin())
                   &&Double.isFinite(pipeDelta)&&AtmosMachineryFixtureService.exchangerObserved(roomProof,pipeBefore,outletGas.thermalEnergy(),thermal)
                  &&roomProof.nowK()<roomProof.beforeK()-1e-6;
    }

    private record FlowProgress(boolean pressure,boolean volume,boolean mixer,boolean filter,boolean valve,GasType selectedFilterGas) {
        boolean complete(){return pressure&&volume&&mixer&&filter&&valve;}
        @Override public String toString(){return "pressure="+pressure+",volume="+volume+",mixer="+mixer+",filter="+filter+",valve="+valve+",filterGas="+selectedFilterGas;}
    }

    private static FlowProgress flowMachinesProgress(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        var inventory=PipeGasInventorySavedData.existing(level).map(d->d.snapshot()).orElse(null);
        if(inventory==null)return new FlowProgress(false,false,false,false,false,null);
        boolean pressure=pumpTransferObserved(level,origin,plan,inventory,AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP);
        boolean volume=pumpTransferObserved(level,origin,plan,inventory,AtmosMachineryFixturePlan.Kind.VOLUME_PUMP);
        BlockPos mixerHost=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MIXER));
        if(!(level.getBlockEntity(mixerHost) instanceof AtmosMachineBlockEntity mixer))return new FlowProgress(pressure,volume,false,false,false,null);
        var mixerOut=mixer.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("OUTLET")).findFirst().orElse(null);
        boolean mixerProgress=mixerFlowObserved(level,origin,mixer,mixerHost,inventory,mixerOut==null?null:mixerOut.id());
        BlockPos filterHost=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FILTER));
        if(!(level.getBlockEntity(filterHost) instanceof AtmosMachineBlockEntity filter))return new FlowProgress(pressure,volume,mixerProgress,false,false,null);
        GasType selectedGas=filter.settingsSnapshot().filterGas().orElse(null);
         boolean filterProgress=filterFlowObserved(level,origin,filter,filterHost,inventory,selectedGas);
        BlockPos valveHost=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.VALVE));
        if(!(level.getBlockEntity(valveHost) instanceof AtmosMachineBlockEntity valve))return new FlowProgress(pressure,volume,mixerProgress,filterProgress,false,selectedGas);
         var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
         var roomNow=AtmosMachineryFixtureService.roomMeasurements(level,origin);
         String valveSource=AtmosMachineryFixtureService.roomMeasurementKey(AtmosMachineryFixturePlan.Kind.VALVE,AtmosMachineryRoomCircuitPlan.Role.SOURCE,List.of(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.INLET));
         String valveDestination=AtmosMachineryFixtureService.roomMeasurementKey(AtmosMachineryFixturePlan.Kind.VALVE,AtmosMachineryRoomCircuitPlan.Role.RECEIVER,List.of(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.OUTLET));
         boolean valveProgress=saved!=null&&AtmosMachineryFixtureService.valveRoomFlowObserved(valve.settingsSnapshot().valveOpen().orElse(false),
                 saved.baseline().getOrDefault(valveSource+".plasmaMoles",Double.NaN),roomNow.getOrDefault(valveSource+".plasmaMoles",Double.NaN),
                 saved.baseline().getOrDefault(valveDestination+".plasmaMoles",Double.NaN),roomNow.getOrDefault(valveDestination+".plasmaMoles",Double.NaN));
        return new FlowProgress(pressure,volume,mixerProgress,filterProgress,valveProgress,selectedGas);
    }

    private static boolean mixerFlowObserved(ServerLevel level,BlockPos origin,AtmosMachineBlockEntity mixer,BlockPos host,
            PipeGasInventory inventory,String outputPortId) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        var port=outputPortId==null?null:mixer.gasPorts().stream().filter(p->p.id().equals(outputPortId)).findFirst().orElse(null);
        var outlet=port==null?null:inventory.get(port.key(host)).orElse(null);
        if(saved==null||outlet==null)return false;
        var baseline=saved.demoBaselines().get("mixer");if(baseline==null)return false;
        var mixture=inventory.nodeShare(outlet.node());double plasma=mixture.moles(GasType.PLASMA),water=mixture.moles(GasType.WATER_VAPOR),total=plasma+water;
        double beforePlasma=baseline.getOrDefault(AtmosMachineryFixtureService.portSpeciesMeasurementKey(AtmosMachineryFixturePlan.Kind.MIXER,"outlet",GasType.PLASMA),Double.NaN);
        double beforeWater=baseline.getOrDefault(AtmosMachineryFixtureService.portSpeciesMeasurementKey(AtmosMachineryFixturePlan.Kind.MIXER,"outlet",GasType.WATER_VAPOR),Double.NaN);
        double moved=mixer.committedMovedMoles()-baseline.getOrDefault("work.mixer.movedMoles",0d);
        double fraction=mixer.settingsSnapshot().mixerFractionA().orElse(Double.NaN);
        return Double.isFinite(beforePlasma)&&Double.isFinite(beforeWater)&&plasma>beforePlasma+1e-6&&water>beforeWater+1e-6
                &&Double.isFinite(moved)&&moved>0&&Double.isFinite(total)&&total>0&&Double.isFinite(fraction)
                &&Math.abs(plasma/total-fraction)<=1e-5;
    }

    private static boolean filterFlowObserved(ServerLevel level,BlockPos origin,AtmosMachineBlockEntity filter,BlockPos host,
            PipeGasInventory inventory,GasType selectedGas) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(saved==null||selectedGas!=GasType.PLASMA)return false;
        var baseline=saved.demoBaselines().get("filter");if(baseline==null)return false;
        var selectedPort=filter.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("filter")).findFirst().orElse(null);
        var outletPort=filter.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("outlet")).findFirst().orElse(null);
        if(selectedPort==null||outletPort==null)return false;
        var selectedPipe=inventory.get(selectedPort.key(host)).orElse(null);var outletPipe=inventory.get(outletPort.key(host)).orElse(null);
        if(selectedPipe==null||outletPipe==null)return false;
        double selectedBefore=baseline.getOrDefault(AtmosMachineryFixtureService.portSpeciesMeasurementKey(AtmosMachineryFixturePlan.Kind.FILTER,selectedPort.id(),GasType.PLASMA),Double.NaN);
        double waterBefore=baseline.getOrDefault(AtmosMachineryFixtureService.portSpeciesMeasurementKey(AtmosMachineryFixturePlan.Kind.FILTER,outletPort.id(),GasType.WATER_VAPOR),Double.NaN);
        double moved=filter.committedMovedMoles()-baseline.getOrDefault("work.filter.movedMoles",0d);
        return Double.isFinite(selectedBefore)&&Double.isFinite(waterBefore)
                 &&inventory.nodeShare(selectedPort.key(host)).moles(GasType.PLASMA)>selectedBefore+1e-6
                 &&inventory.nodeShare(outletPort.key(host)).moles(GasType.WATER_VAPOR)>waterBefore+1e-6
                 &&inventory.nodeShare(selectedPort.key(host)).moles(GasType.WATER_VAPOR)<=1e-6
                 &&inventory.nodeShare(outletPort.key(host)).moles(GasType.PLASMA)<=1e-6&&moved>0;
    }

    private static boolean mixerReceiverRoomFlowObserved(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(saved==null)return false;
        var baseline=saved.demoBaselines().get("mixer");if(baseline==null)return false;
        var receiver=AtmosMachineryRoomCircuitPlan.standard(plan,origin).endpoints().stream()
                .filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER&&e.role()==AtmosMachineryRoomCircuitPlan.Role.RECEIVER).findFirst().orElse(null);
        if(receiver==null)return false;
        String key=AtmosMachineryFixtureService.roomMeasurementKey(receiver);
        var now=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        String plasma=AtmosMachineryFixtureService.roomSpeciesMeasurementKey(key,GasType.PLASMA);
        String water=AtmosMachineryFixtureService.roomSpeciesMeasurementKey(key,GasType.WATER_VAPOR);
        return now.getOrDefault(plasma,Double.NaN)>baseline.getOrDefault(plasma,Double.NaN)+1e-6
                &&now.getOrDefault(water,Double.NaN)>baseline.getOrDefault(water,Double.NaN)+1e-6;
    }

    private static boolean pumpTransferObserved(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,PipeGasInventory inventory,AtmosMachineryFixturePlan.Kind kind) {
        BlockPos host=origin.offset(plan.station(kind));
        if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine))return false;
        var input=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("inlet")).findFirst().orElse(null);
        var output=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase("outlet")).findFirst().orElse(null);
        if(input==null||output==null)return false;
         var in=inventory.nodeShare(input.key(host));
         var out=inventory.nodeShare(output.key(host));
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(in==null||out==null||saved==null)return false;
        String stem="port."+kind.name().toLowerCase(Locale.ROOT)+".";
        var state=machine.deviceRuntimeState();
         var rooms=AtmosMachineryFixtureService.roomMeasurements(level,origin);
         String sourceKey=AtmosMachineryFixtureService.roomMeasurementKey(kind,AtmosMachineryRoomCircuitPlan.Role.SOURCE,List.of(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.INLET));
         String receiverKey=AtmosMachineryFixtureService.roomMeasurementKey(kind,AtmosMachineryRoomCircuitPlan.Role.RECEIVER,List.of(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.OUTLET));
         var proof=new AtmosMachineryFixtureService.PumpRoomReadings(saved.baseline().getOrDefault(sourceKey+".moles",Double.NaN),rooms.getOrDefault(sourceKey+".moles",Double.NaN),
                 saved.baseline().getOrDefault(receiverKey+".moles",Double.NaN),rooms.getOrDefault(receiverKey+".moles",Double.NaN),
                 saved.baseline().getOrDefault(sourceKey+".averagePressureKpa",Double.NaN),rooms.getOrDefault(sourceKey+".averagePressureKpa",Double.NaN),
                 saved.baseline().getOrDefault(receiverKey+".averagePressureKpa",Double.NaN),rooms.getOrDefault(receiverKey+".averagePressureKpa",Double.NaN));
         boolean transfer=AtmosMachineryFixtureService.pumpTransferObserved(proof,
                 machine.committedMovedMoles()-saved.baseline().getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".movedMoles",0d),
                 machine.committedPaidJoules()-saved.baseline().getOrDefault("work."+kind.name().toLowerCase(Locale.ROOT)+".paidJ",0d),machine.apcAllocationKnown());
        var settings=machine.settingsSnapshot();
        boolean targetOrRate=kind==AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP
                ?settings.targetPressureKpa().isPresent()&&out.pressureKpa(out.volumeCubicMeters())<=settings.targetPressureKpa().get()+0.01
                :settings.transferRateM3PerSecond().isPresent()&&settings.transferRateM3PerSecond().get()>0;
        return transfer&&targetOrRate;
    }

    private static String runtimeDiagnostics(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan,Map<BlockPos,Double> baselines,AtmosMachineBlockEntity heater,AtmosMachineBlockEntity freezer,List<LoadedPipeComponent.Node> valveSourceNodes,List<LoadedPipeComponent.Node> valveReceiverNodes,ComponentTotals valveSourceBefore,ComponentTotals valveReceiverBefore,ComponentTotals valveSourceRoomBefore,ComponentTotals valveReceiverRoomBefore,String valveSourceRoomKey,String valveReceiverRoomKey) {
         var flow=flowMachinesProgress(level,origin,plan);
         var rooms=visibleRoomProgress(level,origin); var valve=valveComponentFlow(level,valveSourceNodes,valveReceiverNodes,valveSourceBefore,valveReceiverBefore,valveSourceRoomBefore,valveReceiverRoomBefore,valveSourceRoomKey,valveReceiverRoomKey,origin);
         var flags="H="+thermostatProgress(level,origin,plan,AtmosMachineryFixturePlan.Kind.HEATER)+",F="+thermostatProgress(level,origin,plan,AtmosMachineryFixturePlan.Kind.FREEZER)+",N="+minerProgress(level,origin,baselines)+",M="+manifoldMixed(level,origin,plan)+",X="+crossingIsolated(level,origin)+",E="+exchangerProgress(level,origin,plan)+",flow.pressure="+flow.pressure()+",flow.volume="+flow.volume()+",flow.mixer="+flow.mixer()+",flow.filter="+flow.filter()+",flow.valve="+flow.valve()+",valve.total="+valve.complete()+",V="+ventilationProgress(level,origin)+",ROOMS="+rooms.complete();
         var failed=rooms.facts().stream().filter(f->f.startsWith("WAIT ")).toList();
         String details=";failedRooms="+failed+";valve{"+valve+"};flowFacts="+flow+";flowMeasurements="+flowMeasurements(level,origin,plan)+";manifold="+manifoldDiagnostics(level,origin,plan)+";exchanger="+exchangerDiagnostics(level,origin,plan)+";roomsPassed="+(rooms.facts().size()-failed.size())+"/"+rooms.facts().size()+";receipts="+minerMoles(level,origin);
         LOGGER.error("Atmos machinery lab 800-tick timeout: {}",truncate("FAILURE-FIRST "+flags+details,4096));
         return truncate("FAILURE-FIRST "+flags+details,700);
     }

    private static java.util.function.Supplier<String> diagnosticOnce(java.util.function.Supplier<String> source) {
        boolean[] logged={false};
        return ()->{if(logged[0])return "timeout diagnostics already recorded"; logged[0]=true; return source.get();};
    }
    private static String manifoldDiagnostics(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        BlockPos host=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MANIFOLD));
        if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine))return "host unavailable";
        var inventory=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null); if(inventory==null)return "inventory unavailable";
        var facts=new java.util.ArrayList<String>();
         for(var port:machine.gasPorts()) {var entry=inventory.get(port.key(host)).orElse(null); if(entry==null){facts.add(port.id()+"=missing");continue;} var mix=inventory.nodeShare(entry.node()); double p=mix.moles(GasType.PLASMA),n=mix.moles(GasType.NITROGEN); facts.add(port.id()+"{PL="+p+",N2="+n+",fraction="+(p+n>0?p/(p+n):Double.NaN)+"}");}
        return facts.toString();
    }
    private static String flowMeasurements(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        var inv=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null); if(inv==null)return "inventory unavailable";
        var out=new java.util.ArrayList<String>();
        BlockPos mixerPos=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.MIXER));
        if(level.getBlockEntity(mixerPos) instanceof AtmosMachineBlockEntity mixer) {
            var gas=machinePortGas(mixer,mixerPos,inv,"OUTLET"); double target=mixer.settingsSnapshot().mixerFractionA().orElse(Double.NaN);
            out.add("mixer{actualPLfraction="+(gas==null?Double.NaN:gas.moles(GasType.PLASMA)/(gas.moles(GasType.PLASMA)+gas.moles(GasType.WATER_VAPOR)))+",target="+target+",PL="+(gas==null?Double.NaN:gas.moles(GasType.PLASMA))+",waterVapor="+(gas==null?Double.NaN:gas.moles(GasType.WATER_VAPOR))+"}");
        }
        BlockPos filterPos=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.FILTER));
        if(level.getBlockEntity(filterPos) instanceof AtmosMachineBlockEntity filter) {
            var selected=filter.settingsSnapshot().filterGas().orElse(null); var inlet=machinePortGas(filter,filterPos,inv,"FILTER"); var outlet=machinePortGas(filter,filterPos,inv,"OUTLET");
            out.add("filter{selected="+selected+",inPL="+(inlet==null?Double.NaN:inlet.moles(GasType.PLASMA))+",outPL="+(outlet==null?Double.NaN:outlet.moles(GasType.PLASMA))+",outWaterVapor="+(outlet==null?Double.NaN:outlet.moles(GasType.WATER_VAPOR))+"}");
        }
        return out.toString();
    }
    private static String exchangerDiagnostics(ServerLevel level,BlockPos origin,AtmosMachineryFixturePlan plan) {
        BlockPos host=origin.offset(plan.station(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER));
        if(!(level.getBlockEntity(host) instanceof AtmosMachineBlockEntity machine))return "host unavailable";
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null); if(saved==null)return "baseline unavailable";
        var ambient=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,host).orElse(null);
        var outlet=machine.gasPorts().stream().filter(p->p.id().toLowerCase(Locale.ROOT).contains("outlet")).findFirst().orElse(null);
        var inv=PipeGasInventorySavedData.existing(level).map(PipeGasInventorySavedData::snapshot).orElse(null);
         double outPL=outlet==null||inv==null?Double.NaN:inv.nodeShare(outlet.key(host)).moles(GasType.PLASMA);
        double now=ambient==null?Double.NaN:ambient.snapshot().temperatureKelvin(),before=saved.baseline().getOrDefault("chamber.exchanger.temperatureK",Double.NaN);
        double thermal=machine.committedThermalJoules()-saved.baseline().getOrDefault("work.passive_exchanger.thermalJ",0d);
        return "roomT="+now+" baselineT="+before+" committedThermal="+thermal+" outletPL="+outPL;
    }

    private record RoomProgress(boolean complete,List<String> facts) {
        @Override public String toString(){return String.join(";",facts);}
    }
    /** Proves room-side changes against the actual room baseline captured by public start. */
    private static RoomProgress visibleRoomProgress(ServerLevel level,BlockPos origin) {
        var saved=AtmosMachineryFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(saved==null||saved.baseline().isEmpty())return new RoomProgress(false,List.of("baseline unavailable"));
        var now=AtmosMachineryFixtureService.roomMeasurements(level,origin);
        var fixture=AtmosMachineryFixturePlan.standard(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level)::get);
        var circuits=AtmosMachineryRoomCircuitPlan.standard(fixture,origin);
        var facts=new java.util.ArrayList<String>(); boolean complete=true;
        for(var ep:circuits.endpoints()) {
            String key=AtmosMachineryFixtureService.roomMeasurementKey(ep);
            var demoBaseline=saved.demoBaselines().getOrDefault(ep.station().name().toLowerCase(Locale.ROOT),Map.of());
            double t=now.getOrDefault(key+".temperatureK",Double.NaN), tb=demoBaseline.getOrDefault(key+".temperatureK",Double.NaN);
            double p=now.getOrDefault(key+".plasmaMoles",Double.NaN),pb=saved.baseline().getOrDefault(key+".plasmaMoles",Double.NaN);
            double n=now.getOrDefault(key+".nitrogenMoles",Double.NaN),nb=saved.baseline().getOrDefault(key+".nitrogenMoles",Double.NaN);
            double w=now.getOrDefault(AtmosMachineryFixtureService.roomSpeciesMeasurementKey(key,GasType.WATER_VAPOR),Double.NaN);
            double wb=saved.baseline().getOrDefault(AtmosMachineryFixtureService.roomSpeciesMeasurementKey(key,GasType.WATER_VAPOR),Double.NaN);
            boolean ok=false; String fact=ep.station()+"/"+ep.role()+" pending";
            switch(ep.role()) {
                case SOURCE,MANIFOLD_IN -> {
                    boolean waterVaporSource=ep.station()==AtmosMachineryFixturePlan.Kind.MIXER&&ep.ports().contains(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.FILTER);
                    boolean filterMixtureSource=ep.station()==AtmosMachineryFixturePlan.Kind.FILTER;
                    ok=waterVaporSource?Double.isFinite(wb)&&w<wb-1e-6:filterMixtureSource
                            ?Double.isFinite(pb)&&Double.isFinite(wb)&&p<pb-1e-6&&w<wb-1e-6:Double.isFinite(pb)&&p<pb-1e-6;
                    fact=ep.station()+"/"+ep.role()+" plasma="+p+" (baseline "+pb+")"+(waterVaporSource||filterMixtureSource?", waterVapor="+w+" (baseline "+wb+")":"");
                }
                case RECEIVER,MANIFOLD_OUT,FILTERED -> {
                    boolean mixedReceiver=ep.station()==AtmosMachineryFixturePlan.Kind.MIXER&&ep.role()==AtmosMachineryRoomCircuitPlan.Role.RECEIVER;
                    ok=Double.isFinite(pb)&&p>pb+1e-6&&(!mixedReceiver||Double.isFinite(wb)&&w>wb+1e-6);
                    fact=ep.station()+"/"+ep.role()+" plasma="+p+" (baseline "+pb+")"+(mixedReceiver?", waterVapor="+w+" (baseline "+wb+")":"");
                }
                case EXHAUST -> {ok=Double.isFinite(wb)&&w>wb+1e-6&&Double.isFinite(p)&&p<=1e-6;fact=ep.station()+"/EXHAUST waterVapor="+w+" (baseline "+wb+"), plasma="+p;}
                case THERMAL_ROOM -> {
                    var machine=(AtmosMachineBlockEntity)level.getBlockEntity(origin.offset(fixture.station(ep.station())));
                    double paid=machine.committedPaidJoules()-demoBaseline.getOrDefault("work."+ep.station().name().toLowerCase(Locale.ROOT)+".paidJ",0d);
                    double thermal=machine.committedThermalJoules()-demoBaseline.getOrDefault("work."+ep.station().name().toLowerCase(Locale.ROOT)+".thermalJ",0d);
                    ok=AtmosMachineryFixtureService.thermalRoomObserved(new AtmosMachineryFixtureService.ThermalRoomProof(tb,t),ep.station(),paid,thermal);
                    fact=ep.station()+" roomT="+t+" (baseline "+tb+"), paid="+paid+", thermal="+thermal;
                }
                case EXCHANGER_ROOM -> {
                    ok=thermalTransferObserved(level,origin,circuits.endpoints(),AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER);
                    fact="HX roomT="+t+" (baseline "+tb+"), opposite measured room/pipe transfer="+ok;
                }
            }
            facts.add((ok?"PASS ":"WAIT ")+fact);complete&=ok;
        }
        return new RoomProgress(complete,List.copyOf(facts));
    }

    private static String minerMoles(ServerLevel level,BlockPos origin) {
        var values=new java.util.ArrayList<String>();
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            var be=level.getBlockEntity(room.machine());
            values.add(be instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner?Double.toString(miner.producedMoles()):"?");
        }
        return values.toString();
    }

    private static com.juicyslew.moonstation14.ms14.atmos.core.GasMixture machinePortGas(AtmosMachineBlockEntity machine,BlockPos host,PipeGasInventory inventory,String id) {
        var port=machine.gasPorts().stream().filter(p->p.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        var entry=port==null?null:inventory.get(port.key(host)).orElse(null);
        return entry==null?null:inventory.nodeShare(entry.node());
    }

    private static boolean minerProgress(ServerLevel level,BlockPos origin,Map<BlockPos,Double> baseline) {
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            if(!(level.getBlockEntity(room.machine()) instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner))return false;
            var profile=miner.resolvedProfile();
            double moles=miner.producedMoles(),thermal=miner.producedThermalJoules();
            var roomGas=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,room.machine());
            if(profile==null||!(moles>baseline.getOrDefault(room.machine(),Double.POSITIVE_INFINITY))||!(thermal>0)
                    ||!Double.isFinite(moles)||!Double.isFinite(thermal)||roomGas.isEmpty()
                    ||!(roomGas.get().snapshot().moles(profile.gas())>0))return false;
        }
        return true;
    }

    private static String minerDiagnostics(ServerLevel level,BlockPos origin) {
        var values=new java.util.ArrayList<String>();
        for(var room:AtmosMachineryLaboratoryRooms.miners(origin)) {
            var be=level.getBlockEntity(room.machine());
            if(be instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner) {
                var profile=miner.resolvedProfile();
                var endpoint=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,room.machine());
                double roomGas=endpoint.map(e->e.snapshot().totalMoles()).orElse(Double.NaN);
                values.add(room.machine()+"{moles="+miner.producedMoles()+", thermal="+miner.producedThermalJoules()+", profile="+(profile==null?"unavailable":profile.gas())+", roomGas="+roomGas+"}");
            } else values.add(room.machine()+"{host unavailable}");
        }
        return values.toString();
    }

    private static int footprintDepth(){return AtmosMachineryFixturePlan.DEPTH;}
}
