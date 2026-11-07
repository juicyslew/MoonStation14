package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlock;
import com.juicyslew.moonstation14.ms14.atmos.monitor.AtmosAlarmSeverity;
import com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmMode;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceEndpoint;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceListService;
import com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkRuntime;
import com.juicyslew.moonstation14.ms14.doors.FirelockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.FirelockTargets;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;

@GameTestTags({"automatic_atmos_firelocks", "atmosphere", "power", "firelock"})
@PrefixGameTestTemplate(false)
public final class AutomaticAtmosFirelockGameTests {
    private AutomaticAtmosFirelockGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty",
            batch = "automatic_atmos_firelocks", timeoutTicks = 300)
    public static void finiteHazardRelaysThroughAlarmToPoweredFirelock(GameTestHelper helper) {
        withFixture(helper, false, false, f -> awaitPaidAndRelayed(helper, f.paid.level(), f, 0, -1, -1, false));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty",
            batch = "automatic_atmos_firelocks", timeoutTicks = 300)
    public static void poweredDangerFirelockResynchronizesAfterLatePowerRestoration(GameTestHelper helper) {
        withFixture(helper, false, true, f -> {
            require(CableStorage.remove(f.paid.level(), f.firelockBranchSegment, Direction.UP, CableTier.APC) == CableTier.APC,
                    "remove the dedicated firelock branch segment only");
            awaitFirelockPowerRestore(helper, f, 0, false, 0, -1);
        });
    }

    @GameTestTags({"automatic_atmos_firelocks"})
    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty",
            batch = "automatic_atmos_firelocks_queued_recovery", timeoutTicks = 300)
    public static void queuedDangerRelayRecoversAfterUnrelatedTopologyChange(GameTestHelper helper) {
        withFixture(helper, false, false, f -> awaitQueuedRelaySetup(helper, f, new QueuedDangerCapture(), 0));
    }

    private static void awaitQueuedRelaySetup(GameTestHelper helper, Fixture f, QueuedDangerCapture capture, int elapsed) {
        ServerLevel level=f.paid.level();
        var vent=PowerRuntime.ventilationMeter(level,f.paid.vent());
        var alarm=PowerRuntime.airAlarmMeter(level,f.alarm);
        var door=f.firelock.host().powerObservation();
        boolean full=vent.known()&&vent.deliveredWatts()==f.paid.vent().prototype().apcPowerReceiver().requestedWatts()
                &&alarm.known()&&alarm.deliveredWatts()==f.alarm.prototype().apcPowerReceiver().requestedWatts()
                &&door.known()&&door.deliveredWatts()==f.firelock.host().prototype().apcPowerReceiver().requestedWatts();
        long generation=PowerGraphService.topologyGeneration(level);
        if(full&&elapsed>=PowerRuntime.TICKS_PER_SOLVE+1){
            capture.install(helper,f);
            require(initializeGas(f.paid,hazardGas()),"trusted finite physical hazard injection");
            pumpDangerBeforeRelayDrain(helper,f,capture,0);return;
        }
        if(elapsed>=100)throw failure("fresh full-power setup deadline vent="+vent+", alarm="+alarm+", door="+door+diagnostics(f));
        BoundedGameTestWait.safeDelay(helper,1,()->awaitQueuedRelaySetup(helper,f,capture,elapsed+1));
    }

    private static void pumpDangerBeforeRelayDrain(GameTestHelper helper,Fixture f,QueuedDangerCapture capture,int operations){
        ServerLevel level=f.paid.level();DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(level);
        f.paid.atmosphere().tick(level,level.getGameTime());runtime.update();
        capture.rethrow();
        if(capture.captured){
            runtime.update();capture.rethrow();
            require(capture.drops.get()>0,"queued alarm-to-firelock DANGER relay dropped on stale power");
            require(f.firelock.state().phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN
                    &&coverOpen(level,f.firelock,f.firelockPos),"stale relay drop leaves physical firelock open");
            awaitFreshRecovery(helper,f,capture.drops,capture.dangerRevision,capture.dangerSources,0);return;
        }
        if(operations>=64)throw failure("bounded physical hazard monitor did not reach danger before drain; alarm="
                +f.alarm.alarmSeverity()+", room="+f.paid.atmosphere().readAtmosphere(level,f.paid.room())+diagnostics(f));
        BoundedGameTestWait.safeDelay(helper,1,()->pumpDangerBeforeRelayDrain(helper,f,capture,operations+1));
    }

    private static void invalidateQueuedRelayTopology(GameTestHelper helper,Fixture f,QueuedDangerCapture capture){
        ServerLevel level=f.paid.level();var transport=runtimeTransport(level);
        var firelockEndpoint=uniqueEndpoint(level,f.firelockPos);
        capture.dangerRevision=f.alarm.stateRevision();
        capture.dangerSources=java.util.Set.copyOf(f.alarm.alarmReceiverState().reports().keySet());
        AutoCloseable dropObserver=transport.observeDelivery(uniqueEndpoint(level,f.alarmPos).identity(),null,attempt->{
            if(attempt.packet().payload() instanceof com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload.Relay relay
                    &&relay.source().equals(f.alarm.controllerId())&&attempt.packet().destinationAddress().equals(firelockEndpoint.composition().network().orElseThrow().address())
                    &&((attempt.outcome()==com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport.DeliveryOutcome.SOURCE_UNAVAILABLE&&attempt.receiverDeviceIdentity()==null)
                    ||(attempt.outcome()==com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport.DeliveryOutcome.RECEIVER_UNPOWERED&&firelockEndpoint.identity().equals(attempt.receiverDeviceIdentity()))))capture.drops.incrementAndGet();
        });
        capture.observers.add(dropObserver);
        long before=PowerGraphService.topologyGeneration(level);
        BlockPos island=helper.absolutePos(new BlockPos(1,0,1));
        helper.setBlock(new BlockPos(1,0,1),net.minecraft.world.level.block.Blocks.STONE);
        require(CableStorage.place(level,island,Direction.UP,CableTier.APC),"place unrelated APC cable island on fixture-owned support");
        long changed=PowerGraphService.topologyGeneration(level);
        require(changed!=before,"actual cable placement advances graph generation: "+before+" -> "+changed);
        var stale=f.firelock.host().powerObservation();
        require(!stale.known(),"firelock meter immediately rejects stale topology: "+stale);
    }

    private static final class QueuedDangerCapture {
        final java.util.concurrent.atomic.AtomicInteger drops=new java.util.concurrent.atomic.AtomicInteger();
        boolean captured; long dangerRevision; java.util.Set<java.util.UUID> dangerSources=java.util.Set.of(); RuntimeException failure;
        final java.util.List<AutoCloseable> observers=new java.util.ArrayList<>();
        final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
        void install(GameTestHelper helper,Fixture f){
            var transport=runtimeTransport(f.paid.level());var monitor=uniqueEndpoint(f.paid.level(),f.paid.vent().getBlockPos());
            var alarm=uniqueEndpoint(f.paid.level(),f.alarmPos);
            AutoCloseable observer=transport.observeDelivery(monitor.identity(),alarm.identity(),attempt->{
                if(captured||failure!=null||attempt.outcome()!=com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport.DeliveryOutcome.ENDPOINT_CALLED
                        ||!(attempt.packet().payload() instanceof com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload.Measurement m)
                        ||m.severity()!=AtmosAlarmSeverity.DANGER)return;
                try {
                    require(f.alarm.alarmSeverity()==AtmosAlarmSeverity.DANGER,"alarm accepted real DANGER measurement");
                    require(f.firelock.state().phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN
                            &&coverOpen(f.paid.level(),f.firelock,f.firelockPos),"relay queued before firelock closes");
                    require(transport.queuedPackets()>0,"alarm receive enqueued relay before observer callback");
                    invalidateQueuedRelayTopology(helper,f,this);captured=true;
                } catch(RuntimeException|Error e){failure=e instanceof RuntimeException r?r:new RuntimeException(e);}
            });
            observers.add(observer);
            helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void close(){if(!closed.compareAndSet(false,true))return;for(var o:observers)try{o.close();}catch(Exception e){if(failure==null)failure=new IllegalStateException("failed to close delivery observer",e);}} public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){close();}});
        }
        void rethrow(){if(failure!=null)throw failure;}
    }

    private static void awaitFreshRecovery(GameTestHelper helper,Fixture f,java.util.concurrent.atomic.AtomicInteger drops,long dangerRevision,
                                           java.util.Set<java.util.UUID> dangerSources,int elapsed){
        ServerLevel level=f.paid.level();var vent=PowerRuntime.ventilationMeter(level,f.paid.vent());var alarm=PowerRuntime.airAlarmMeter(level,f.alarm);var door=f.firelock.host().powerObservation();
        boolean full=vent.known()&&vent.deliveredWatts()==f.paid.vent().prototype().apcPowerReceiver().requestedWatts()
                &&alarm.known()&&alarm.deliveredWatts()==f.alarm.prototype().apcPowerReceiver().requestedWatts()
                &&door.known()&&door.deliveredWatts()==f.firelock.host().prototype().apcPowerReceiver().requestedWatts();
        if(f.alarm.alarmSeverity()!=AtmosAlarmSeverity.DANGER)throw failure("alarm severity changed while recovering; "+diagnostics(f));
        var sourceReports=f.alarm.alarmReceiverState().reports();
        if(f.alarm.stateRevision()!=dangerRevision||f.alarm.alarmSeverity()!=AtmosAlarmSeverity.DANGER
                ||dangerSources.isEmpty()||dangerSources.stream().anyMatch(source->sourceReports.get(source)!=AtmosAlarmSeverity.DANGER))
            throw failure("original DANGER source reports or alarm mode/severity changed during recovery; revision="+f.alarm.stateRevision()+" expected="+dangerRevision+", sources="+sourceReports+", expectedDangerSources="+dangerSources);
        if(full&&f.firelock.state().phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.CLOSED
                &&!coverOpen(level,f.firelock,f.firelockPos)&&!AtmosphereTopology.isPassable(level,f.firelockPos)){
            require(drops.get()>0,"stale relay drop retained through recovery");
            require(f.paid.atmosphere().readAtmosphere(level,f.paid.room()).orElseThrow().status()==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,"finite physical hazard retained without rewrite");
            finish(helper,f);return;
        }
        if(elapsed>=100)throw failure("fresh-power danger replay deadline elapsed="+elapsed+", vent="+vent+", alarm="+alarm+", firelock="+door+", drops="+drops.get()+", generation="+PowerGraphService.topologyGeneration(level)+diagnostics(f));
        BoundedGameTestWait.safeDelay(helper,1,()->awaitFreshRecovery(helper,f,drops,dangerRevision,dangerSources,elapsed+1));
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty",
            batch = "automatic_atmos_firelocks", timeoutTicks = 300)
    public static void attachedCoverHazardClosurePreservesBaseAirlockOpenState(GameTestHelper helper) {
        withFixture(helper,true,f->{
            require(f.baseAirlock!=null&&f.firelock.attached(),"attached fixture owns an airlock base and cover target");
            awaitAttachedBasePaid(helper,f,0,0,-1);
        });
    }

    private static void withFixture(GameTestHelper helper, java.util.function.Consumer<Fixture> ready) {
        withFixture(helper,false,false,ready);
    }

    private static void withFixture(GameTestHelper helper,boolean attached,java.util.function.Consumer<Fixture> ready) {
        withFixture(helper,attached,false,ready);
    }
    private static void withFixture(GameTestHelper helper,boolean attached,boolean initialHazard,java.util.function.Consumer<Fixture> ready) {
        PaidVentilationFlowGameTests.fixture(helper, false, initialHazard?hazardGas():stationGas(), paid -> {
            ServerLevel level = paid.level();
            var ventSettings = paid.vent().config().settings();
            require(paid.vent().config().compareAndSet(ventSettings.revision(), new VentilationDeviceConfig.Settings(
                    ventSettings.revision(), false, ventSettings.externalPressureBoundKPa(),
                    ventSettings.targetPressureChangeKPaPerSecond(), ventSettings.targetPressureKPa(),
                    ventSettings.volumeRateM3PerSecond(), ventSettings.scrubberMode(), ventSettings.filteredGases(),
                    ventSettings.selectedLayer())), "disable gas transfer independently of paid monitoring");
            // Extend the canonical APC cable lane to the firelock and alarm; do not add a second source.
            for (int x = 7; x <= 10; x++) {
                helper.setBlock(new BlockPos(x, 0, 5), net.minecraft.world.level.block.Blocks.STONE);
                require(com.juicyslew.moonstation14.ms14.power.cable.CableStorage.place(level,
                        helper.absolutePos(new BlockPos(x, 0, 5)), Direction.UP,
                        com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC), "extend shared APC floor route");
            }
            BlockPos alarmLocal = new BlockPos(8, 1, 7);
            BlockPos alarmPos = helper.absolutePos(alarmLocal);
            helper.setBlock(alarmLocal.south(), net.minecraft.world.level.block.Blocks.STONE);
            helper.setBlock(alarmLocal, ModBlocks.AIR_ALARM.get().defaultBlockState()
                    .setValue(AirAlarmBlock.FACING, Direction.NORTH));
            BlockPos firelockLocal = new BlockPos(10, 1, 9);
            BlockPos firelockPos = helper.absolutePos(firelockLocal);
            for (int z = 6; z <= 7; z++) {
                helper.setBlock(new BlockPos(10, 0, z), net.minecraft.world.level.block.Blocks.STONE);
                require(CableStorage.place(level, helper.absolutePos(new BlockPos(10, 0, z)), Direction.UP,
                        CableTier.APC), "APC firelock branch cable");
            }
            helper.setBlock(new BlockPos(10, 0, 9), net.minecraft.world.level.block.Blocks.STONE);
            AirlockBlockEntity baseAirlock=null;
            if(attached){var lower=ModBlocks.AIRLOCK.get().defaultBlockState();helper.setBlock(new BlockPos(10,1,9),lower);helper.setBlock(new BlockPos(10,2,9),lower.setValue(AirlockBlock.HALF,DoubleBlockHalf.UPPER));
                baseAirlock=(AirlockBlockEntity)level.getBlockEntity(firelockPos);require(baseAirlock!=null&&baseAirlock.installFirelock(),"install attached firelock in fixture");}
            else {var lower=ModBlocks.FIRELOCK.get().defaultBlockState();helper.setBlock(new BlockPos(10,1,9),lower);helper.setBlock(new BlockPos(10,2,9),lower.setValue(FirelockBlock.HALF,DoubleBlockHalf.UPPER));}
            BlockPos finalFirelockPos=firelockPos;
            BlockPos finalAlarmPos=alarmPos;
            BlockPos finalBranch=helper.absolutePos(new BlockPos(10,0,6));
            AirlockBlockEntity finalBaseAirlock=baseAirlock;
            waitForFixtureRegistration(helper,level,paid,finalAlarmPos,finalFirelockPos,attached,0,()->{
            var alarm=(AirAlarmBlockEntity)level.getBlockEntity(finalAlarmPos);
            var firelock=FirelockTargets.atRoot(level,finalFirelockPos);
            if (firelock == null || alarm == null) throw failure("registered hazard endpoints missing");
            if(!firelock.attached())firelock.controller().initialize(firelock.host());
            require(firelock.state() != null && firelock.state().phase() == com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN,
                    "resolved standard firelock starts Open");
            require(coverOpen(level,firelock,firelockPos)&&coverOpen(level,firelock,firelockPos.above()),"initial cover state is Open");
            if(!attached)require(AtmosphereTopology.isPassable(level,firelockPos)&&AtmosphereTopology.isPassable(level,firelockPos.above()),"both initial standalone cells are passable");
            if(!initialHazard)require(initializeGas(paid, stationGas()), "trusted finite-cell initial normal atmosphere");

            var sample = paid.atmosphere().readAtmosphere(level, paid.room()).orElseThrow(() -> failure("room reading absent"));
            require(sample.status() == com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,
                    "finite room ownership before sampling");
            require(paid.atmosphere().sample(level, paid.room()).isPresent()
                    && paid.atmosphere().sample(level, paid.deviceCell()).isPresent(), "operational room/vent samples exist");
            DeviceEndpoint alarmOwner = uniqueEndpoint(level, alarmPos);
            DeviceEndpoint ventEndpoint = uniqueEndpoint(level, paid.vent().getBlockPos());
            DeviceEndpoint firelockEndpoint;
            if(firelock.attached()){
                var selected=DeviceNetworkRuntime.forLevel(level).transport().lookupAt(firelockPos.above());
                require(selected.size()==1&&selected.getFirst().identity().value().equals(firelock.identity()),"upper airlock cell priority selects attached FIRELOCK endpoint");
                firelockEndpoint=selected.getFirst();
            }else firelockEndpoint=uniqueEndpoint(level,firelockPos);
            require(alarmOwner.deviceList() != null, "resolved alarm DeviceList");
            require(new DeviceListService().apply(alarmOwner, DeviceListService.Mutation.set(List.of(
                    ventEndpoint.identity().value().toString(), firelockEndpoint.identity().value().toString()))),
                    "canonical alarm membership stores actual vent and firelock IDs");
            Fixture fixture=new Fixture(paid, finalAlarmPos, alarm, finalFirelockPos, firelock,finalBaseAirlock,finalBranch,new DiagnosticTrace());
            installDiagnostics(helper,fixture,runtimeTransport(level),alarmOwner.identity(),firelockEndpoint.identity());
            ready.accept(fixture);
            });
        });
    }

    private static com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport runtimeTransport(ServerLevel level){return DeviceNetworkRuntime.forLevel(level).transport();}
    private static void installDiagnostics(GameTestHelper helper,Fixture f,com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport transport,com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity alarmId,com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity firelockId){
        DiagnosticTrace trace=f.trace;
        var a=transport.observeDelivery(alarmId,null,attempt->{if(attempt.receiverDeviceIdentity()==null||attempt.receiverDeviceIdentity().equals(firelockId))trace.add(event(f,transport,"ALARM",alarmId,attempt));});
        var d=transport.observeDelivery(firelockId,null,attempt->{if(attempt.receiverDeviceIdentity()==null||attempt.receiverDeviceIdentity().equals(alarmId))trace.add(event(f,transport,"FIRELOCK",firelockId,attempt));});
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void close(){try{a.close();d.close();}catch(Exception e){throw new IllegalStateException(e);}} public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){close();} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){close();}});
    }
    private static String event(Fixture f,com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport transport,String direction,com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity sender,com.juicyslew.moonstation14.ms14.devicenet.DeviceNetworkTransport.DeliveryAttempt attempt){
        var payload=attempt.packet().payload();String report="";
        if(payload instanceof com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload.Relay relay){report=" reports="+relay.reports().size()+" aggregate="+relay.reports().values().stream().max(java.util.Comparator.comparingInt(Enum::ordinal)).orElse(null);}
        else if(payload instanceof com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload.Measurement measurement)report=" report="+measurement.severity();
        return "tick="+f.paid.level().getGameTime()+" dir="+direction+" outcome="+attempt.outcome()+" phase="+(f.firelock.state()==null?"null":f.firelock.state().phase())+" power="+f.firelock.host().powerObservation().deliveredWatts()+" alarm="+f.alarm.alarmSeverity()+" queued="+transport.queuedPackets()+" tx="+shortId(sender)+" rx="+shortId(attempt.receiverDeviceIdentity())+" payload="+payload.getClass().getSimpleName()+report;
    }
    private static String shortId(com.juicyslew.moonstation14.ms14.devicenet.DeviceIdentity identity){return identity==null?"null":identity.value().toString().substring(0,8);}
    private static final class DiagnosticTrace {private final ArrayDeque<String> events=new ArrayDeque<>();private static final int EVENT_LIMIT=240,EVENTS=24;void add(String event){if(events.size()==EVENTS)events.removeFirst();if(event.length()>EVENT_LIMIT)event=event.substring(0,EVENT_LIMIT);events.addLast(event);}public String toString(){return events.toString();}}
    private static String diagnostics(Fixture f){return " trace="+f.trace;}

    private static void waitForFixtureRegistration(GameTestHelper helper,ServerLevel level,
                                                   PaidVentilationFlowGameTests.Fixture paid,BlockPos alarmPos,
                                                   BlockPos firelockPos,boolean attached,int elapsed,Runnable ready){
        var transport=DeviceNetworkRuntime.forLevel(level).transport();
        var alarm=level.getBlockEntity(alarmPos);
        var firelock=level.getBlockEntity(firelockPos);
        BlockPos endpointPos=attached?firelockPos.above():firelockPos;
        var firelockTarget=FirelockTargets.atRoot(level,firelockPos);
        var alarmEndpoints=transport.lookupAt(alarmPos);
        var ventEndpoints=transport.lookupAt(paid.vent().getBlockPos());
        var firelockEndpoints=transport.lookupAt(endpointPos);
        boolean registered=alarm instanceof AirAlarmBlockEntity alarmBe&&firelockTarget!=null
                &&alarmEndpoints.size()==1&&alarmEndpoints.getFirst().identity().value().equals(alarmBe.controllerId())
                &&ventEndpoints.size()==1&&ventEndpoints.getFirst().identity().value().equals(paid.vent().deviceId())
                &&firelockEndpoints.size()==1&&firelockEndpoints.getFirst().identity().value().equals(firelockTarget.identity());
        if(registered){ready.run();return;}
        if(elapsed>=20)throw failure("placed endpoint lifecycle deadline: alarmBlock="+level.getBlockState(alarmPos)
                +", alarmBE="+alarm+", firelockBlock="+level.getBlockState(firelockPos)+"/"+level.getBlockState(firelockPos.above())
                +", firelockBE="+firelock+", alarmEndpoints="+alarmEndpoints.size()+"/"+(alarm instanceof AirAlarmBlockEntity be?be.controllerId():"n/a")
                +", ventEndpoints="+ventEndpoints.size()+"/"+paid.vent().deviceId()+", firelockEndpoints="+firelockEndpoints.size()
                +"/"+(firelockTarget==null?"n/a":firelockTarget.identity()));
        BoundedGameTestWait.safeDelay(helper,1,()->waitForFixtureRegistration(helper,level,paid,alarmPos,firelockPos,attached,elapsed+1,ready));
    }

    private static DeviceEndpoint uniqueEndpoint(ServerLevel level, BlockPos pos) {
        var found = DeviceNetworkRuntime.forLevel(level).transport().lookupAt(pos);
        require(found.size() == 1, "exactly one registered endpoint at " + pos + ", found=" + found.size());
        return found.getFirst();
    }

    private static boolean coverOpen(ServerLevel level,FirelockTargets.Target target,BlockPos cell){
        var state=level.getBlockState(cell);return target.attached()?target.state()!=null&&target.state().passable()
                :state.getBlock() instanceof FirelockBlock&&state.getValue(FirelockBlock.OPEN);
    }
    private static boolean baseAirlockOpen(ServerLevel level,BlockPos cell){var state=level.getBlockState(cell);return state.getBlock() instanceof AirlockBlock&&state.getValue(AirlockBlock.OPEN);}

    private static void awaitAttachedBasePaid(GameTestHelper helper,Fixture f,int elapsed,int stable,long generation){
        ServerLevel level=f.paid.level();var cover=f.firelock.host().powerObservation();var base=PowerRuntime.airlockPower(level,f.baseAirlock);
        var alarm=PowerRuntime.airAlarmMeter(level,f.alarm);var vent=PowerRuntime.ventilationMeter(level,f.paid.vent());
        boolean full=cover.known()&&cover.deliveredWatts()==f.firelock.host().prototype().apcPowerReceiver().requestedWatts()
                &&base.known()&&base.deliveredWatts()==f.baseAirlock.prototype().apcPowerReceiver().requestedWatts()
                &&alarm.known()&&alarm.deliveredWatts()==f.alarm.prototype().apcPowerReceiver().requestedWatts()
                &&vent.known()&&vent.deliveredWatts()==f.paid.vent().prototype().apcPowerReceiver().requestedWatts();
        long current=PowerGraphService.topologyGeneration(level);if(full&&(stable==0||generation==current))stable++;else stable=full?1:0;generation=full?current:-1;
        if(full&&stable>=PowerRuntime.TICKS_PER_SOLVE+1){
            require(base.deliveredWatts()==20.0&&cover.deliveredWatts()==5.0,"base and attached cover receive distinct full 20W/5W APC allocations: base="+base+", cover="+cover);
            require(f.firelock.controller().state()==f.firelock.state(),"target exposes its authoritative controller state");
            var identity=f.baseAirlock.identity();require(f.baseAirlock.activate(),"normally activate fully-paid base airlock");
            awaitAttachedBaseOpen(helper,f,identity,0);return;
        }
        if(elapsed>=100)throw failure("attached/base paid observations deadline; base="+base+", cover="+cover+", alarm="+alarm+", vent="+vent);
        int next=stable;long nextGeneration=generation;BoundedGameTestWait.safeDelay(helper,1,()->awaitAttachedBasePaid(helper,f,elapsed+1,next,nextGeneration));
    }

    private static void awaitAttachedBaseOpen(GameTestHelper helper,Fixture f,java.util.UUID identity,int elapsed){
        ServerLevel level=f.paid.level();if(!identity.equals(f.baseAirlock.identity()))throw failure("base identity changed while opening");
        if(f.baseAirlock.state().phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN){
            var captured=f.baseAirlock.state();long remaining=captured.autoCloseAt()-level.getGameTime();
            require(captured.autoCloseAt()>0&&remaining>20,"base auto-close deadline is active but leaves a bounded hazard window: remaining="+remaining);
            // The late-power test separately retains initial-hazard/unpowered-startup replay coverage.
            require(initializeGas(f.paid, hazardGas()), "single trusted finite hazard injection after paid base-open gate");
            awaitAttachedHazardClosure(helper,f,identity,captured,0);return;
        }
        if(elapsed>=30)throw failure("base did not finish opening within bounded stage window: "+f.baseAirlock.state());
        BoundedGameTestWait.safeDelay(helper,1,()->awaitAttachedBaseOpen(helper,f,identity,elapsed+1));
    }

    private static void awaitAttachedHazardClosure(GameTestHelper helper,Fixture f,java.util.UUID baseIdentity,
                                                    com.juicyslew.moonstation14.ms14.doors.AirlockState capturedBase,int elapsed){
        ServerLevel level=f.paid.level();f.paid.atmosphere().tick(level,level.getGameTime());DeviceNetworkRuntime runtime=DeviceNetworkRuntime.forLevel(level);runtime.gameTick();runtime.update();
        var sample=f.paid.atmosphere().readAtmosphere(level,f.paid.room()).orElseThrow(()->failure("finite hazard monitor reading disappeared"));
        require(sample.status()==com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereReading.Status.FINITE,"hazard remains a finite monitor sample");
        boolean coverClosed=f.firelock.state()!=null&&!f.firelock.state().passable()
                &&!coverOpen(level,f.firelock,f.firelockPos)&&!coverOpen(level,f.firelock,f.firelockPos.above())
                &&!AtmosphereTopology.isPassable(level,f.firelockPos)&&!AtmosphereTopology.isPassable(level,f.firelockPos.above());
        if(f.alarm.alarmSeverity()==AtmosAlarmSeverity.DANGER&&f.alarm.currentMode()==AirAlarmMode.WIDE_FILTERING&&coverClosed){
            require(baseIdentity.equals(f.baseAirlock.identity())&&capturedBase.equals(f.baseAirlock.state()),"hazard relay leaves base airlock identity and logical state unchanged");
            require(f.baseAirlock.state().phase()==com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN
                    &&baseAirlockOpen(level,f.firelockPos)&&baseAirlockOpen(level,f.firelockPos.above()),"base leaf remains logically and physically open behind the closed cover");
            var context=net.minecraft.world.phys.shapes.CollisionContext.empty();
            require(!level.getBlockState(f.firelockPos).getCollisionShape(level,f.firelockPos,context).isEmpty()
                    &&!level.getBlockState(f.firelockPos.above()).getCollisionShape(level,f.firelockPos.above(),context).isEmpty(),"both composed airlock cells retain collision");
            require(PowerRuntime.airlockPower(level,f.baseAirlock).deliveredWatts()==20.0
                    &&f.firelock.host().powerObservation().deliveredWatts()==5.0,"base and cover remain separately fully paid");finish(helper,f);return;
        }
        long deadline=capturedBase.autoCloseAt();if(elapsed>=75||level.getGameTime()>=deadline-5)
            throw failure("cover did not close through finite-hazard alarm relay before base auto-close; elapsed="+elapsed+", alarm="+f.alarm.alarmSeverity()+", cover="+f.firelock.state()+", base="+f.baseAirlock.state()+diagnostics(f));
        BoundedGameTestWait.safeDelay(helper,1,()->awaitAttachedHazardClosure(helper,f,baseIdentity,capturedBase,elapsed+1));
    }

    private static void awaitPaidAndRelayed(GameTestHelper helper, ServerLevel level, Fixture f, int elapsed,
                                            int stableTicks, long stableGeneration, boolean normalReturn) {
        var ventPower = PowerRuntime.ventilationMeter(level, f.paid.vent());
        var alarmPower = PowerRuntime.airAlarmMeter(level, f.alarm);
        var firelockPower = f.firelock.host().powerObservation();
        boolean full = ventPower.known() && ventPower.deliveredWatts() == f.paid.vent().prototype().apcPowerReceiver().requestedWatts()
                && alarmPower.known() && alarmPower.deliveredWatts() == f.alarm.prototype().apcPowerReceiver().requestedWatts()
                && firelockPower.known() && firelockPower.deliveredWatts() == f.firelock.host().prototype().apcPowerReceiver().requestedWatts();
        long generation = com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level);
        if (full && (stableTicks == 0 || generation == stableGeneration)) stableTicks++;
        else stableTicks = full ? 1 : 0;
        stableGeneration = full ? generation : -1;
        if (full && stableTicks >= PowerRuntime.TICKS_PER_SOLVE + 1) {
            assertApcExtensionGeometry(level, f, true);
            f.paid.atmosphere().tick(level, level.getGameTime());
            DeviceNetworkRuntime runtime = DeviceNetworkRuntime.forLevel(level);
            runtime.gameTick(); runtime.update();
            if (!normalReturn && f.alarm.alarmSeverity() == AtmosAlarmSeverity.NORMAL
                    && f.alarm.currentMode() == AirAlarmMode.FILTERING
                    && f.firelock.state().phase() == com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN
                    && coverOpen(level,f.firelock,f.firelockPos) && coverOpen(level,f.firelock,f.firelockPos.above())
                    && AtmosphereTopology.isPassable(level,f.firelockPos) && AtmosphereTopology.isPassable(level,f.firelockPos.above())) {
                require(initializeGas(f.paid, hazardGas()), "single trusted finite hazard injection after fresh full-paid normal gate");
                awaitPaidAndRelayed(helper, level, f, elapsed, 0, -1, false);
                return;
            }
            if (!normalReturn && f.alarm.alarmSeverity() == AtmosAlarmSeverity.DANGER
                    && f.alarm.currentMode() == AirAlarmMode.WIDE_FILTERING
                    && !coverOpen(level,f.firelock,f.firelockPos)
                    && !coverOpen(level,f.firelock,f.firelockPos.above())
                    && !AtmosphereTopology.isPassable(level, f.firelockPos)
                    && !AtmosphereTopology.isPassable(level, f.firelockPos.above())) {
                require(initializeGas(f.paid, stationGas()), "trusted finite-cell gas transaction restores physical station atmosphere");
                awaitPaidAndRelayed(helper, level, f, elapsed, 0, -1, true);
                return;
            }
            if (normalReturn && f.alarm.alarmSeverity() == AtmosAlarmSeverity.NORMAL
                    && f.alarm.currentMode() == AirAlarmMode.FILTERING
                    && f.firelock.state().phase() == com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN
                    && coverOpen(level,f.firelock,f.firelockPos)
                    && coverOpen(level,f.firelock,f.firelockPos.above())
                    && AtmosphereTopology.isPassable(level, f.firelockPos)
                    && AtmosphereTopology.isPassable(level, f.firelockPos.above())) { finish(helper,f); return; }
        }
        if (elapsed >= 255) throw failure("hazard relay deadline; vent=" + ventPower + ", alarm=" + alarmPower
                + ", firelock=" + firelockPower + ", severity=" + f.alarm.alarmSeverity() + ", receiver="
                + f.firelock.host().powerObservation() + ", topologyGeneration="
                + com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level)+diagnostics(f));
        int nextStableTicks = stableTicks;
        long nextStableGeneration = stableGeneration;
        BoundedGameTestWait.safeDelay(helper, 1, () -> awaitPaidAndRelayed(helper, level, f, elapsed + 1,
                nextStableTicks, nextStableGeneration, normalReturn));
    }

    private static void awaitFirelockPowerRestore(GameTestHelper helper, Fixture f, int elapsed,
                                                   boolean restoring, int stableTicks, long stableGeneration) {
        ServerLevel level = f.paid.level();
        f.paid.atmosphere().tick(level, level.getGameTime());
        DeviceNetworkRuntime runtime = DeviceNetworkRuntime.forLevel(level);
        runtime.gameTick(); runtime.update();
        var ventPower = PowerRuntime.ventilationMeter(level, f.paid.vent());
        var alarmPower = PowerRuntime.airAlarmMeter(level, f.alarm);
        var firelockPower = f.firelock.host().powerObservation();
        boolean monitorAndAlarmPaid = ventPower.known()
                && ventPower.deliveredWatts() == f.paid.vent().prototype().apcPowerReceiver().requestedWatts()
                && alarmPower.known() && alarmPower.deliveredWatts() == f.alarm.prototype().apcPowerReceiver().requestedWatts();
        if (!restoring) {
            if (monitorAndAlarmPaid && f.alarm.alarmSeverity() == AtmosAlarmSeverity.DANGER
                    && (!firelockPower.known() || firelockPower.deliveredWatts()
                    != f.firelock.host().prototype().apcPowerReceiver().requestedWatts())
                    && f.firelock.state().phase() == com.juicyslew.moonstation14.ms14.doors.AirlockState.DoorPhase.OPEN) {
                assertApcExtensionGeometry(level, f, false);
                require(CableStorage.place(level, f.firelockBranchSegment, Direction.UP, CableTier.APC),
                        "restore isolated APC firelock branch");
                awaitFirelockPowerRestore(helper, f, elapsed, true, 0, -1);
                return;
            }
        } else {
            boolean full = monitorAndAlarmPaid && firelockPower.known()
                    && firelockPower.deliveredWatts() == f.firelock.host().prototype().apcPowerReceiver().requestedWatts();
            long generation = com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level);
            if (full && (stableTicks == 0 || generation == stableGeneration)) stableTicks++;
            else stableTicks = full ? 1 : 0;
            stableGeneration = full ? generation : -1;
            if (f.alarm.alarmSeverity() != AtmosAlarmSeverity.DANGER)
                throw failure("alarm severity changed during firelock power restoration: " + f.alarm.alarmSeverity());
            if (full && stableTicks >= PowerRuntime.TICKS_PER_SOLVE + 1)
                assertApcExtensionGeometry(level, f, true);
            if (full && stableTicks >= PowerRuntime.TICKS_PER_SOLVE + 1
                    && !coverOpen(level,f.firelock,f.firelockPos)
                    && !coverOpen(level,f.firelock,f.firelockPos.above())
                    && !AtmosphereTopology.isPassable(level, f.firelockPos)
                    && !AtmosphereTopology.isPassable(level, f.firelockPos.above())) { finish(helper,f); return; }
        }
        if (elapsed >= 255) throw failure("late power resync deadline; restoring=" + restoring + ", vent=" + ventPower
                + ", alarm=" + alarmPower + ", firelock=" + firelockPower + ", severity=" + f.alarm.alarmSeverity()
                + ", topologyGeneration=" + com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level));
        int nextStable = stableTicks;
        long nextGeneration = stableGeneration;
        boolean nextRestoring = restoring;
        BoundedGameTestWait.safeDelay(helper, 1, () -> awaitFirelockPowerRestore(helper, f, elapsed + 1,
                nextRestoring, nextStable, nextGeneration));
    }

    private static GasMixture stationGas() {
        return GasMixture.breathableAir();
    }
    private static GasMixture hazardGas(){return new GasMixture(Map.of(GasType.OXYGEN,100.0,GasType.NITROGEN,300.0,GasType.CARBON_DIOXIDE,20.0),300.0);}
    private static boolean initializeGas(PaidVentilationFlowGameTests.Fixture paid,GasMixture gas){
        return paid.atmosphere().initializeFiniteMixtureForTrustedSetup(paid.level(),paid.room(),gas)
                &&paid.atmosphere().initializeFiniteMixtureForTrustedSetup(paid.level(),paid.deviceCell(),gas);
    }

    /** APC extension lookup is Euclidean radius 3: these deliberate sqrt(5) cable nodes are valid. */
    private static void assertApcExtensionGeometry(ServerLevel level, Fixture f, boolean firelockConnected) {
        CableFaceNode alarmNode = PowerGraphService.nearestApcExtensionNode(level, f.alarmPos);
        CableFaceNode firelockNode = PowerGraphService.nearestApcExtensionNode(level, f.firelockPos);
        BlockPos expectedAlarmHost = f.paid.apc().offset(5, -1, 0); // APC at local (3,1,5), cable host at (8,0,5).
        BlockPos expectedFirelockHost = f.firelockBranchSegment.offset(0, 0, 1); // branch node at absolute local (10,0,7).
        require(alarmNode != null && alarmNode.host().equals(expectedAlarmHost),
                "alarm nearest APC extension node is connected floor cable (8,0,5): " + alarmNode);
        require(firelockNode != null && firelockNode.host().equals(expectedFirelockHost),
                "firelock nearest APC extension node is dedicated branch cable (10,0,7): " + firelockNode);
        int alarmComponent = PowerGraphService.state(level, alarmNode).componentId();
        int firelockComponent = PowerGraphService.state(level, firelockNode).componentId();
        require((alarmComponent == firelockComponent) == firelockConnected,
                "alarm/firelock nearest-node APC component relation expected connected=" + firelockConnected
                        + " actual component IDs=" + alarmComponent + "/" + firelockComponent);
    }

    private static GameTestAssertException failure(String message) { return new GameTestAssertException(message); }
    private static void require(boolean value, String message) { if (!value) throw failure(message); }
    private static void finish(GameTestHelper helper,Fixture f){com.juicyslew.moonstation14.MoonStation14.LOGGER.info("AUTOMATIC_ATMOS_FIRELOCK_DIAGNOSTIC test={} tick={} alarm={} mode={} firelock={} queue={} topology={} trace={}",helper.testInfo.getTestName(),f.paid.level().getGameTime(),f.alarm.alarmSeverity(),f.alarm.currentMode(),f.firelock.state(),runtimeTransport(f.paid.level()).queuedPackets(),PowerGraphService.topologyGeneration(f.paid.level()),f.trace);helper.succeed();}
    private record Fixture(PaidVentilationFlowGameTests.Fixture paid, BlockPos alarmPos, AirAlarmBlockEntity alarm,
                            BlockPos firelockPos, FirelockTargets.Target firelock,AirlockBlockEntity baseAirlock,
                            BlockPos firelockBranchSegment,DiagnosticTrace trace) { }
}
