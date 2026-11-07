package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** End-to-end TEG charge proof: paid LV, finite gas conversion, HV offer and real lamp APC allocation. */
@GameTestTags({"atmosphere", "teg", "teg_real_power", "atmos-machine-appearance"})
@PrefixGameTestTemplate(false)
public final class TegRealPowerGameTests {
    private static TegFixtureRegistryScope registryScope;

    private TegRealPowerGameTests() { }

    @BeforeBatch(batch="teg_real_power_energy")
    public static void setupBatch(ServerLevel level) { registryScope=TegFixtureRegistryScope.acquire(level); }

    @AfterBatch(batch="teg_real_power_energy")
    public static void teardownBatch(ServerLevel level) {
        if(registryScope!=null){registryScope.close();registryScope=null;}
    }
    private record PortState(PipeTopology.NodeKey key,GasMixture mixture) { }
    private record StreamState(PortState inlet,PortState outlet) { }
    private record ChargeState(StreamState hot,StreamState cold) { }

    @GameTest(template="disposal_clear",timeoutTicks=700,templateNamespace="moonstation14",batch="teg_real_power_energy")
    public static void finiteThermalChargePaysLvAndLightsIsolatedRealLoad(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos origin=helper.absolutePos(new BlockPos(0,1,0));
        BlockPos end=origin.offset(TegFixturePlan.WIDTH,TegFixturePlan.HEIGHT,TegFixturePlan.DEPTH);
        var lease=FixtureChunkTickets.acquire(helper,new net.minecraft.world.phys.AABB(origin.getX(),origin.getY(),origin.getZ(),end.getX(),end.getY(),end.getZ()));
        var probes=readinessProbes(origin);
        FixtureChunkTickets.awaitEntityTicking(helper,lease,100,()->true,"reserved TEG footprint chunks entity-ticking",
                ()->buildAndInitialize(helper,level,origin,lease),probes);
    }

    private static void buildAndInitialize(GameTestHelper helper,ServerLevel level,BlockPos origin,
                                           FixtureChunkTickets.Lease lease) {
        try {
            String built=TegFixtureService.build(level,origin);
            require(built.startsWith("Built finite-charge TEG fixture"),"fixture build: "+built);
            var devices=TegFixturePlan.layout(origin.offset(8,1,8)).devices();
            var seeds=seeds(level,devices);
            require(seeds.size()==4,"expected four resolved fixture port seed identities");
            var inventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            var bad=seeds.get(0);
            var wrongIdentity=new TrustedPipeFixtureInitialization.PortSeed(bad.host(),bad.portId(),UUID.randomUUID(),bad.mixture());
            var stale=TrustedPipeFixtureInitialization.validateVacuumPorts(level,List.of(wrongIdentity));
            require(!stale.successful()&&stale.failure()==TrustedPipeFixtureInitialization.Failure.INCARNATION_MISMATCH,
                    "wrong gas-port incarnation must fail the preflight specifically");
            require(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot()==inventory,
                    "rejected identity preflight must not mutate pipe gas inventory");
            var wrongVolume=new TrustedPipeFixtureInitialization.PortSeed(bad.host(),bad.portId(),bad.incarnation(),
                    new GasMixture(bad.mixture().gasMoles(),bad.mixture().temperatureKelvin(),1.0));
            var volumeMismatch=TrustedPipeFixtureInitialization.validateVacuumPorts(level,List.of(wrongVolume));
            require(!volumeMismatch.successful()&&volumeMismatch.failure()==TrustedPipeFixtureInitialization.Failure.VOLUME_MISMATCH,
                    "seed with default cell volume must be rejected against the smaller live port volume");
            require(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot()==inventory,
                    "rejected volume preflight must not mutate pipe gas inventory");
            var ready=TrustedPipeFixtureInitialization.validateVacuumPorts(level,seeds);
            require(ready.successful()&&ready.completedPorts()==0,"valid four-port vacuum preflight: "+ready.detail());
            String seeded=TegFixtureService.initialize(level,origin);
            require(seeded.startsWith("Initialized one finite thermal charge"),"finite gas/battery setup: "+seeded);
            require(TegFixtureService.initialize(level,origin).contains("already initialized"),"persisted token must refuse a second charge");
            ChargeState initial=charge(level,TegFixturePlan.layout(origin.offset(8,1,8)).devices());
            require(initial!=null,"all four charged gas ports must be observable");
            assertInitial(initial.hot(),8,1,900,"hot");assertInitial(initial.cold(),8,1,100,"cold");
            require(Set.of(initial.hot().inlet().key(),initial.hot().outlet().key(),initial.cold().inlet().key(),initial.cold().outlet().key()).size()==4,
                    "hot/cold inlet and outlet ports must remain four separate inventory roots");
            awaitPower(helper,level,origin,lease,new int[]{0,0},initial,new boolean[2]);
        } catch(Throwable failure) {
            cleanup(level,origin);lease.close();helper.fail("TEG real-power fixture setup failed: "+failure.getMessage());return;
        }
    }

    private static BlockPos[] readinessProbes(BlockPos origin) {
        var points=new java.util.ArrayList<BlockPos>(9);
        for(int x:new int[]{0,15,TegFixturePlan.WIDTH-1})for(int z:new int[]{0,15,TegFixturePlan.DEPTH-1})
            points.add(origin.offset(x,0,z));
        return points.toArray(BlockPos[]::new);
    }

    private static void awaitPower(GameTestHelper helper,ServerLevel level,BlockPos origin,
                                   FixtureChunkTickets.Lease lease,int[] waits,ChargeState initial,boolean[] motionSeen) {
        helper.runAfterDelay(1,()->{
            try {
                var plan=TegFixturePlan.layout(origin.offset(8,1,8));var d=plan.devices();
                var core=level.getBlockEntity(d.core()) instanceof AtmosMachineBlockEntity m?m:null;
                var output=level.getBlockEntity(d.outputApc()) instanceof PowerDeviceBlockEntity p?p:null;
                if(core==null||output==null)throw new GameTestAssertException("TEG core/output APC disappeared");
                var allocation=PowerRuntime.apcOutputObservation(level,output);
                boolean lamp=level.getBlockState(d.load()).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT);
                var assembly=com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly.resolve(level,core).orElse(null);
                var runtime=core.tegRuntimeState();
                if(assembly!=null) {
                    boolean firstA=!motionSeen[0]&&acceptedTransfer(runtime,runtime.circulatorA());
                    boolean firstB=!motionSeen[1]&&acceptedTransfer(runtime,runtime.circulatorB());
                    motionSeen[0] |= acceptedTransfer(runtime,runtime.circulatorA());
                    motionSeen[1] |= acceptedTransfer(runtime,runtime.circulatorB());
                    assertLatestCirculatorDisplay(core,assembly.circulatorA(),runtime.circulatorA());
                    assertLatestCirculatorDisplay(core,assembly.circulatorB(),runtime.circulatorB());
                    if(firstA)assertOwnTickerStable(level,assembly.circulatorA());
                    if(firstB)assertOwnTickerStable(level,assembly.circulatorB());
                }
                boolean delivery=core.tegRuntimeState().cumulativeGeneratedJoules()>0&&allocation.known()&&allocation.outputWatts()>0&&lamp;
                    if(delivery&&motionSeen[0]&&motionSeen[1]) {
                    ChargeState after=charge(level,d);
                    require(after!=null,"port inventories must remain observable after power generation");
                    assertFiniteStream(initial.hot(),after.hot(),"hot");
                    assertFiniteStream(initial.cold(),after.cold(),"cold");
                    require(changed(initial.hot(),after.hot()),"hot inlet/outlet pair must change state during generation");
                    require(changed(initial.cold(),after.cold()),"cold inlet/outlet pair must change state during generation");
                    double generated=core.tegRuntimeState().cumulativeGeneratedJoules();
                    require(core.tegRuntimeState().lastInputPaidJoules()>0,"TEG generation must follow positive actual paid LV joules");
                    var centerProfile=core.resolvedProfile();
                    boolean corePowered=coreApcesConnected(core);
                    int projectedLevel=corePowered?1+com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineAppearance.tegLevel(true,
                            runtime.supply(),centerProfile.teg().minimumVisualWatts(),centerProfile.teg().maximumVisualWatts()):0;
                    require(level.getBlockState(d.core()).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY)==projectedLevel,
                            "core DISPLAY must project actual APC gate and runtime ramp, not delivered load watts; "+diagnostic(core,assembly));
                    assertLatestCirculatorDisplay(core,assembly.circulatorA(),runtime.circulatorA());
                    assertLatestCirculatorDisplay(core,assembly.circulatorB(),runtime.circulatorB());
                    var startup=level.getBlockEntity(d.startupApc()) instanceof PowerDeviceBlockEntity p?p:null;
                    require(startup!=null&&startup.energyJoules()<500_000,"finite startup APC must actually pay its LV load");
                    require(startup.toggleBreakerAuthorized(true,0),"startup breaker must open for LV-loss phase");
                    helper.runAfterDelay(60,()->{
                        try {
                               require(core.tegRuntimeState().cumulativeGeneratedJoules()<=generated+1e-6,
                                      "TEG generated again after its isolated LV gate was removed");
                             require(!core.apcAllocationKnown()||core.apcAllocatedWatts()<1000,
                                     "LV gate removal must be visible in the real APC allocation");
                             require(output.breakerClosed(),"output APC breaker must remain closed during generation loss");
                               require(output.energyJoules()>200,
                                       "real TEG output APC must have banked enough finite energy to sustain a 100 W lamp");
                               awaitBufferedDischarge(helper,level,origin,lease,core,output,d,generated,waits);
                               return;
                        } catch(Throwable failure) { cleanup(level,origin);lease.close();helper.fail("TEG LV-loss proof failed: "+failure.getMessage()); }
                    });
                    return;
                }
                if(++waits[0]>=400)throw new GameTestAssertException("No delivered TEG load plus accepted motion on both circulators within 400 ticks; "+TegFixtureService.status(level,origin));
                awaitPower(helper,level,origin,lease,waits,initial,motionSeen);
            } catch(Throwable failure) { cleanup(level,origin);lease.close();helper.fail("TEG real-power proof failed: "+failure.getMessage()); }
        });
    }

    private static void assertLatestCirculatorDisplay(AtmosMachineBlockEntity core,AtmosMachineBlockEntity circulator,
            com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.Observation observation) {
        var profile=circulator.resolvedProfile();
        int expected=com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineAppearance.circulatorDisplay(coreApcesConnected(core),
                observation.pressureDeltaKpa(),observation.transferredMoles(),profile.visual().circulatorFastDeltaKpa());
        int actual=circulator.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY);
        require(expected==actual,"circulator DISPLAY differs from actual core gate/last accepted transfer; "+diagnostic(core,
                com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly.resolve((ServerLevel)core.getLevel(),core).orElse(null)));
    }
    private static void awaitBufferedDischarge(GameTestHelper helper,ServerLevel level,BlockPos origin,
            FixtureChunkTickets.Lease lease,AtmosMachineBlockEntity core,PowerDeviceBlockEntity output,
            TegFixturePlan.Devices d,double generated,int[] waits) {
        try {
            require(core.tegRuntimeState().cumulativeGeneratedJoules()<=generated+1e-6,
                    "TEG generated again after its isolated LV gate was removed");
            var sample=PowerRuntime.apcOutputObservation(level,output);
            if (++waits[1]>=100) throw new GameTestAssertException("No fresh zero-input output sample with continued delivery");
            if(!sample.known()||sample.inputWatts()>0||sample.outputWatts()<100
                    ||!level.getBlockState(d.load()).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)) {
                helper.runAfterDelay(1,()->awaitBufferedDischarge(helper,level,origin,lease,core,output,d,generated,waits));return;
            }
            require(sample.energyAfterJoules()<sample.energyBeforeJoules(),
                    "fresh zero-MV-input lamp delivery receipt must show finite stored-energy withdrawal");
            verifyPostLoss(helper,level,origin,lease,core,output,d);
        } catch(Throwable failure){cleanup(level,origin);lease.close();helper.fail("TEG LV-loss proof failed: "+failure.getMessage());}
    }
    private static void verifyPostLoss(GameTestHelper helper,ServerLevel level,BlockPos origin,
            FixtureChunkTickets.Lease lease,AtmosMachineBlockEntity core,PowerDeviceBlockEntity output,TegFixturePlan.Devices d) {
        var latestAssembly=com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly.resolve(level,core).orElse(null);
        int expectedCore=coreApcesConnected(core)?1+com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineAppearance.tegLevel(true,
                core.tegRuntimeState().supply(),core.resolvedProfile().teg().minimumVisualWatts(),core.resolvedProfile().teg().maximumVisualWatts()):0;
        require(level.getBlockState(d.core()).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY)==expectedCore,
                "post-breaker core display disagrees with actual APC gate/ramp; "+diagnostic(core,latestAssembly));
        if(latestAssembly!=null) {
            assertLatestCirculatorDisplay(core,latestAssembly.circulatorA(),core.tegRuntimeState().circulatorA());
            assertLatestCirculatorDisplay(core,latestAssembly.circulatorB(),core.tegRuntimeState().circulatorB());
        }
        cleanup(level,origin);lease.close();helper.succeed();
    }
    private static void assertOwnTickerStable(ServerLevel level,AtmosMachineBlockEntity circulator) {
        int actual=circulator.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY);
        AtmosMachineBlockEntity.tick(level,circulator);
        require(circulator.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY)==actual,
                "circulator's own ticker overwrote core-owned motion DISPLAY");
    }

    private static boolean coreApcesConnected(AtmosMachineBlockEntity core) {
        Double requested=core.apcPowerDemandWatts();
        return requested!=null&&requested>0&&core.apcAllocationKnown()&&core.apcAllocatedWatts()>=requested;
    }
    private static boolean positive(com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.Observation observation) {
        return observation.pressureDeltaKpa()>0&&observation.transferredMoles()>0;
    }
    private static boolean acceptedTransfer(com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.State state,
            com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.Observation observation) {
        return (state.status()==com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.Status.PROCESSED
                ||state.status()==com.juicyslew.moonstation14.ms14.atmos.teg.TegRuntime.Status.OFFER_REJECTED)&&positive(observation);
    }
    private static String diagnostic(AtmosMachineBlockEntity core,
                                     com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly.Assembly assembly) {
        var state=core.tegRuntimeState();Double requested=core.apcPowerDemandWatts();
        String members=assembly==null?"assembly=missing":"A-display="+assembly.circulatorA().getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY)
                +",B-display="+assembly.circulatorB().getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY);
        return "coreDisplay="+core.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.DISPLAY)
                +",gateKnown="+core.apcAllocationKnown()+",requested="+requested+",allocated="+core.apcAllocatedWatts()+",sample="+core.apcSampleTick()
                +",status="+state.status()+",eventTick="+state.lastPaidSampleTick()+",ramp="+state.supply().rampPositionWatts()
                +",obsA="+state.circulatorA()+",obsB="+state.circulatorB()+","+members;
    }

    private static void cleanup(ServerLevel level,BlockPos origin) {
        var plan=TegFixturePlan.layout(origin.offset(8,1,8));var d=plan.devices();
        logCleanupGraphState(level,"before");
        for(var cable:plan.cables())com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,cable.host(),cable.face(),cable.tier());
        for(BlockPos pos:new BlockPos[]{d.core(),d.northCirc(),d.southCirc(),d.startupApc(),d.substation(),d.outputApc(),d.load()})
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        for(BlockPos pos:plan.cableHosts())if(level.getBlockState(pos).is(Blocks.STONE))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        for(BlockPos pos:new BlockPos[]{d.core().below(),d.northCirc().below(),d.southCirc().below()})
            if(level.getBlockState(pos).is(Blocks.STONE))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        logCleanupGraphState(level,"after");
    }
    private static void logCleanupGraphState(ServerLevel level,String phase) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_REAL_POWER_CLEANUP phase={} worldTick={} privatePowerSample={} generation={} nodes={}",
                phase,level.getGameTime(),PowerRuntime.currentSampleTick(level),
                com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level),
                com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(level));
    }
    private static ChargeState charge(ServerLevel level,TegFixturePlan.Devices d) {
        var hotIn=port(level,d.northCirc(),"inlet");var hotOut=port(level,d.northCirc(),"outlet");
        var coldIn=port(level,d.southCirc(),"inlet");var coldOut=port(level,d.southCirc(),"outlet");
        return hotIn==null||hotOut==null||coldIn==null||coldOut==null?null:new ChargeState(new StreamState(hotIn,hotOut),new StreamState(coldIn,coldOut));
    }
    private static List<TrustedPipeFixtureInitialization.PortSeed> seeds(ServerLevel level,TegFixturePlan.Devices d) {
        var result=new ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
        addSeeds(result,level,d.northCirc(),Map.of("inlet",8d,"outlet",1d),900);
        addSeeds(result,level,d.southCirc(),Map.of("inlet",8d,"outlet",1d),100);
        return result;
    }
    private static void addSeeds(List<TrustedPipeFixtureInitialization.PortSeed> result,ServerLevel level,BlockPos pos,
                                 Map<String,Double> moles,double kelvin) {
        if(!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity host))return;
        for(var port:host.gasPorts())if(moles.containsKey(port.id()))result.add(new TrustedPipeFixtureInitialization.PortSeed(
                pos,port.id(),port.incarnation(),new GasMixture(Map.of(GasType.OXYGEN,moles.get(port.id())),kelvin,port.volume())));
    }
    private static PortState port(ServerLevel level,BlockPos pos,String portId) {
        if(!(level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity host))return null;
        var port=host.gasPorts().stream().filter(p->p.id().equals(portId)).findFirst().orElse(null);if(port==null)return null;
        var entry=PipeGasInventorySavedData.existing(level).map(d->d.snapshot().get(port.key(pos)).orElse(null)).orElse(null);
        return entry==null||!entry.active()?null:new PortState(port.key(pos),PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().nodeShare(entry.node()));
    }
    private static void assertInitial(StreamState stream,double inletMoles,double outletMoles,double kelvin,String label) {
        require(Math.abs(stream.inlet().mixture().moles(GasType.OXYGEN)-inletMoles)<1e-6,label+" inlet initial moles");
        require(Math.abs(stream.outlet().mixture().moles(GasType.OXYGEN)-outletMoles)<1e-6,label+" outlet initial moles");
        require(Math.abs(stream.inlet().mixture().temperatureKelvin()-kelvin)<1e-4,label+" inlet initial temperature");
        require(Math.abs(stream.outlet().mixture().temperatureKelvin()-kelvin)<1e-4,label+" outlet initial temperature");
    }
    private static void assertFiniteStream(StreamState before,StreamState after,String label) {
        for(GasType type:GasType.values()) {
            double initial=before.inlet().mixture().moles(type)+before.outlet().mixture().moles(type);
            double current=after.inlet().mixture().moles(type)+after.outlet().mixture().moles(type);
            require(Math.abs(current-initial)<1e-5,label+" stream must conserve finite "+type+" without refill");
        }
        double beforeEnergy=thermal(before),afterEnergy=thermal(after);
        require(Math.abs(afterEnergy-beforeEnergy)>1e-3,label+" stream thermal inventory must change");
        if(label.equals("hot"))require(afterEnergy<beforeEnergy,label+" hot stream must lose finite thermal energy");
        else require(afterEnergy>beforeEnergy,label+" cold stream must receive finite thermal energy");
    }
    private static double thermal(StreamState stream) {
        return stream.inlet().mixture().thermalEnergy()+stream.outlet().mixture().thermalEnergy();
    }
    private static boolean changed(StreamState before,StreamState after) {
        return !same(before.inlet().mixture(),after.inlet().mixture())
                ||!same(before.outlet().mixture(),after.outlet().mixture());
    }
    private static boolean same(GasMixture a,GasMixture b) {
        return a.gasMoles().equals(b.gasMoles())&&Math.abs(a.temperatureKelvin()-b.temperatureKelvin())<1e-4;
    }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
