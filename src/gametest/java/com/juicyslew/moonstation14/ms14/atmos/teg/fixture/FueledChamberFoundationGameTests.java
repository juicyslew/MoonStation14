package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasOwner;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

/** Paid-power foundation: sealed finite chamber, APC-funded machines, and finite pipe heating. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere", "teg", "atmos-burn-chamber"})
public final class FueledChamberFoundationGameTests {
    private FueledChamberFoundationGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", timeoutTicks=4500)
    public static void foundationSealedRoomAndFiniteLoopSetup(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        List<Owned> owned=new ArrayList<>();
        List<CableFaceNode> cables=new ArrayList<>();
        var lease=FixtureChunkTickets.acquire(helper,new net.minecraft.world.phys.AABB(
                helper.absolutePos(new BlockPos(0,0,0)).getX(),helper.absolutePos(new BlockPos(0,0,0)).getY(),helper.absolutePos(new BlockPos(0,0,0)).getZ(),
                helper.absolutePos(new BlockPos(10,8,10)).getX(),helper.absolutePos(new BlockPos(10,8,10)).getY(),helper.absolutePos(new BlockPos(10,8,10)).getZ()));
        BlockPos exchanger=helper.absolutePos(new BlockPos(4,2,4));
        BlockPos pump=helper.absolutePos(new BlockPos(3,2,4));
        BlockPos heater=helper.absolutePos(new BlockPos(5,2,3));
        try {
            // Enclosed 3x3x3 passable chamber. Keep all machines within its interior.
            List<BlockPos> room=new ArrayList<>();
            for(int x=2;x<=6;x++) for(int z=2;z<=6;z++) {
                put(helper,new BlockPos(x,1,z),Blocks.STONE,owned);
                put(helper,new BlockPos(x,5,z),Blocks.STONE,owned);
                for(int y=2;y<=4;y++) {
                    BlockPos p=new BlockPos(x,y,z);
                    boolean interior=x>=3&&x<=5&&z>=3&&z<=5;
                    if(interior) {
                        room.add(helper.absolutePos(p));
                        if(!helper.getLevel().getBlockState(helper.absolutePos(p)).isAir())
                            throw new GameTestAssertException("chamber interior occupied");
                    } else put(helper,p,Blocks.STONE,owned);
                }
            }
            placeMachine(helper,exchanger,ModBlocks.PASSIVE_HEAT_EXCHANGER.get(),Direction.SOUTH,owned);
            placeMachine(helper,pump,ModBlocks.GAS_VOLUME_PUMP.get(),Direction.NORTH,owned);
            placeMachine(helper,heater,ModBlocks.GAS_THERMO_HEATER.get(),Direction.EAST,owned);
            for(BlockPos pipe:List.of(new BlockPos(3,2,3),new BlockPos(4,2,3),new BlockPos(3,2,5),new BlockPos(4,2,5))) {
                BlockPos world=helper.absolutePos(pipe);
                if(!helper.getLevel().getBlockState(world).isAir()) throw new GameTestAssertException("pipe location occupied");
                helper.setBlock(pipe,ModBlocks.GAS_PIPE_VISUAL.get());
                var visual=(VisualPipeBlockEntity)level.getBlockEntity(world);
                if(visual==null||!visual.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY))
                    throw new GameTestAssertException("primary pipe layer setup rejected");
                if(LoadedPipeGasOwner.createVacuumLane(level,world,VisualPipeBlockEntity.PRIMARY).isEmpty())
                    throw new GameTestAssertException("primary finite pipe lane admission rejected");
                owned.add(new OwnedPipe(world,visual,visual.functionalIdentity(VisualPipeBlockEntity.PRIMARY).orElseThrow()));
            }
            BlockPos apc=helper.absolutePos(new BlockPos(3,4,4));
            if(!com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement.placeApc(level,apc,Direction.EAST))
                throw new GameTestAssertException("APC placement rejected");
            owned.add(new OwnedApc(apc,(PowerDeviceBlockEntity)level.getBlockEntity(apc),level.getBlockState(apc).getBlock(),Direction.EAST,
                    apc.relative(Direction.WEST),level.getBlockState(apc.relative(Direction.WEST))));
            for(var entry:List.of(new Object[]{new BlockPos(2,4,4),Direction.EAST},new Object[]{new BlockPos(2,3,4),Direction.EAST},
                    new Object[]{new BlockPos(2,2,4),Direction.EAST},new Object[]{new BlockPos(2,2,3),Direction.EAST},
                    new Object[]{new BlockPos(3,2,2),Direction.SOUTH},new Object[]{new BlockPos(4,2,2),Direction.SOUTH},
                    new Object[]{new BlockPos(5,2,2),Direction.SOUTH})) {
                BlockPos host=helper.absolutePos((BlockPos)entry[0]); Direction face=(Direction)entry[1];
                if(CableStorage.get(level,host,face)!=null) throw new GameTestAssertException("APC cable key occupied");
                if(!CableStorage.place(level,host,face,CableTier.APC)) throw new GameTestAssertException("APC cable placement rejected");
                cables.add(new CableFaceNode(host,face,CableTier.APC));
            }
            room.removeIf(cell->!AtmosphereTopology.isPassable(level,cell));
            awaitResolved(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,80);
        } catch(Throwable failure) { cleanup(level,owned,cables,lease); throw failure; }
    }

    private static void awaitResolved(GameTestHelper helper,ServerLevel level,List<BlockPos> room,BlockPos exchanger,
                                      BlockPos pump,BlockPos heater,BlockPos apc,List<CableFaceNode> cables,List<Owned> owned,FixtureChunkTickets.Lease lease,int remaining) {
        try {
            var e=machine(level,exchanger,"passive_heat_exchanger");
            var p=machine(level,pump,"gas_volume_pump");
            var h=machine(level,heater,"gas_thermo_heater");
            if(e.resolvedProfile()==null||p.resolvedProfile()==null||h.resolvedProfile()==null) throw new IllegalStateException("profiles pending");
            e.reconcileLoadedPorts(level);p.reconcileLoadedPorts(level);h.reconcileLoadedPorts(level);
            for(int i=0;i<owned.size();i++) if(owned.get(i) instanceof OwnedMachine m && m.placement()==null) {
                UUID identity=m.be().placementIdentity();
                if(identity==null) throw new IllegalStateException("machine identity pending");
                owned.set(i,new OwnedMachine(m.pos(),m.be(),identity,m.blockId()));
            }
            var ein=port(e,"inlet"); var eout=port(e,"outlet");
            var pin=port(p,"inlet"); var pout=port(p,"outlet"); var hp=port(h,"pipe");
            // The single host positions are distinct and the exchanger endpoints are not aliased.
            if(ein.key(exchanger).equals(eout.key(exchanger))||pin.key(pump).equals(pout.key(pump)))
                throw new GameTestAssertException("machine's named ports alias");
            var data=PipeGasInventorySavedData.existing(level).orElseThrow();
            for(var item:List.of(new PortAt(exchanger,ein),new PortAt(exchanger,eout),new PortAt(pump,pin),new PortAt(pump,pout),new PortAt(heater,hp)))
                if(data.snapshot().get(item.port().key(item.host())).isEmpty())
                throw new GameTestAssertException("live machine gas port not registered");
            var north=helper.absolutePos(new BlockPos(3,2,3)); var northRight=helper.absolutePos(new BlockPos(4,2,3));
            var south=helper.absolutePos(new BlockPos(3,2,5)); var southRight=helper.absolutePos(new BlockPos(4,2,5));
            var northNet=LoadedPipeComponent.discover(level,pin.key(pump)).orElseThrow(()->new GameTestAssertException("pump inlet circuit not connected"));
            var southNet=LoadedPipeComponent.discover(level,pout.key(pump)).orElseThrow(()->new GameTestAssertException("pump outlet circuit not connected"));
            var northKeys=northNet.nodes().stream().map(LoadedPipeComponent.Node::key).collect(java.util.stream.Collectors.toSet());
            var southKeys=southNet.nodes().stream().map(LoadedPipeComponent.Node::key).collect(java.util.stream.Collectors.toSet());
            if(northKeys.equals(southKeys)||southKeys.contains(eout.key(exchanger))||northKeys.contains(ein.key(exchanger)))
                throw new GameTestAssertException("pipe circuits aliased across exchanger");
            if(!northKeys.contains(eout.key(exchanger))||!southKeys.contains(ein.key(exchanger))
                    ||!northKeys.contains(hp.key(heater))||!northKeys.contains(new PipeTopology.NodeKey(north,PipeTopology.Layer.PRIMARY))
                    ||!northKeys.contains(new PipeTopology.NodeKey(northRight,PipeTopology.Layer.PRIMARY))
                    ||!southKeys.contains(new PipeTopology.NodeKey(south,PipeTopology.Layer.PRIMARY))
                    ||!southKeys.contains(new PipeTopology.NodeKey(southRight,PipeTopology.Layer.PRIMARY)))
                throw new GameTestAssertException("loop membership does not match configured ports");
        } catch(GameTestAssertException failed) { cleanup(level,owned,cables,lease); throw failed; }
        catch(RuntimeException pending) {
            if(remaining<=0) { cleanup(level,owned,cables,lease); throw new GameTestAssertException("machine/profile admission timeout"); }
            helper.runAfterDelay(1,()->awaitResolved(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,remaining-1)); return;
        }
        // Mutation phase is intentionally outside readiness retry handling; never seed twice.
        try {
            var e=machine(level,exchanger,"passive_heat_exchanger");
            var p=machine(level,pump,"gas_volume_pump");
            var h=machine(level,heater,"gas_thermo_heater");
            var ein=port(e,"inlet"); var eout=port(e,"outlet");
            var pin=port(p,"inlet"); var pout=port(p,"outlet"); var hp=port(h,"pipe");
            var atmosphere=AtmosphereService.INSTANCE;
            GasMixture roomGas=new GasMixture(Map.of(GasType.PLASMA,.1,GasType.OXYGEN,1d),293.15);
            if(!atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,room,roomGas))
                throw new GameTestAssertException("trusted enclosed room admission rejected");
            var seeds=new ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
            for(var item:List.of(new SeedAt(exchanger,ein,1),new SeedAt(exchanger,eout,1),
                    new SeedAt(pump,pin,20),new SeedAt(pump,pout,20),new SeedAt(heater,hp,1))) {
                var gasPort=item.port();
                seeds.add(new TrustedPipeFixtureInitialization.PortSeed(item.host(),gasPort.id(),gasPort.incarnation(),
                        new GasMixture(Map.of(GasType.NITROGEN,item.moles()),293.15,gasPort.volume())));
            }
            var seeded=TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level,seeds);
            if(!seeded.successful()) throw new GameTestAssertException("finite loop seed failed: "+seeded.detail());
            for(BlockPos cell:room) {
                var chunk=(LevelChunk)level.getChunk(cell);
                if(!chunk.getExistingDataOrNull(ModDataAttachments.ATMOSPHERE_CHUNK.get()).isFiniteClaimed(cell.getX()&15,cell.getY(),cell.getZ()&15))
                    throw new GameTestAssertException("room cell not finitely claimed");
            }
            var pport=p.powerRuntimePorts().stream().filter(x->x.tier()==CableTier.APC).findFirst().orElseThrow();
            var hport=h.powerRuntimePorts().stream().filter(x->x.tier()==CableTier.APC).findFirst().orElseThrow();
            var apcEntity=(PowerDeviceBlockEntity)level.getBlockEntity(apc);
            var apcPort=apcEntity.ports().stream().filter(x->x.tier()==CableTier.APC).findFirst().orElseThrow();
            if(!PowerTopology.deviceAdjacent(pport,cables.get(2))||!PowerTopology.deviceAdjacent(hport,cables.get(6))
                    ||!PowerTopology.deviceAdjacent(apcPort,cables.get(0))) throw new GameTestAssertException("power port geometry mismatch");
            for(int i=1;i<cables.size();i++) if(PowerTopology.cableEdge(cables.get(i-1),cables.get(i)).isEmpty())
                throw new GameTestAssertException("APC cable path discontinuity");
            if(!p.applySettings(AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(true))
                    ||!h.applySettings(AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K,new AtmosMachineSettings.NumericValue(593.15))
                    ||!h.applySettings(AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(true)))
                throw new GameTestAssertException("machine settings rejected");
            apcEntity.setEnergyJoules(1_000_000);
            double initialEnergy=apcEntity.energyJoules();
            var northNodes=LoadedPipeComponent.discover(level,pin.key(pump)).orElseThrow().nodes();
            var southNodes=LoadedPipeComponent.discover(level,pout.key(pump)).orElseThrow().nodes();
            var inventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            var tracked=new java.util.LinkedHashMap<PipeTopology.NodeKey,TrackedNode>();
            for(var node:northNodes) tracked.put(node.key(),new TrackedNode(node.incarnation(),node.volume()));
            for(var node:southNodes) if(tracked.putIfAbsent(node.key(),new TrackedNode(node.incarnation(),node.volume()))!=null)
                throw new GameTestAssertException("loop components overlap");
            if(tracked.size()!=northNodes.size()+southNodes.size()) throw new GameTestAssertException("loop node identity alias");
            double initialNitrogen=loopNitrogen(inventory,tracked);
            ChamberState baseline=state(level,room,tracked);
            if(Math.abs(baseline.room().averageKelvin()-293.15)>.02) throw new GameTestAssertException("room seed temperature mismatch");
            MoonStation14.LOGGER.info("CHAMBER_BASE cells={} nodes={} fuel={} roomE={} pipeE={} K={}",room.size(),tracked.size(),baseline.room().plasma(),baseline.room().energy(),baseline.pipeEnergy(),baseline.room().averageKelvin());
            helper.runAfterDelay(1,()->awaitPaid(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initialEnergy,initialNitrogen,tracked,baseline,200));
        } catch(Throwable failure) { cleanup(level,owned,cables,lease); throw failure; }
    }

    private static void awaitPaid(GameTestHelper helper,ServerLevel level,List<BlockPos> room,BlockPos exchanger,BlockPos pump,BlockPos heater,BlockPos apc,
                                  List<CableFaceNode> cables,List<Owned> owned,FixtureChunkTickets.Lease lease,double initial,double initialNitrogen,
                                  Map<PipeTopology.NodeKey,TrackedNode> tracked,ChamberState baseline,int left) {
        try {
            var p=machine(level,pump,"gas_volume_pump");var h=machine(level,heater,"gas_thermo_heater");
            var a=(PowerDeviceBlockEntity)level.getBlockEntity(apc);
            var port=port(h,"pipe");var snap=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();var gas=snap.nodeShare(port.key(heater));
            var profile=h.resolvedProfile();
            double required=profile==null||profile.thermostat()==null?Double.POSITIVE_INFINITY:profile.thermostat().inputPowerWatts();
            Double pumpDemand=p.apcPowerDemandWatts();
            if(pumpDemand!=null&&p.apcAllocationKnown()&&p.apcAllocatedWatts()>=pumpDemand
                    &&h.apcAllocationKnown()&&h.apcAllocatedWatts()>=required
                    &&a.energyJoules()<initial&&gas.temperatureKelvin()>293.20) {
                MoonStation14.LOGGER.info("CHAMBER_PAID K={} battery={} allocations={}/{}",gas.temperatureKelvin(),a.energyJoules(),p.apcAllocatedWatts(),h.apcAllocatedWatts());
                long stamp=machine(level,exchanger,"passive_heat_exchanger").deviceRuntimeState().lastUnpoweredProcessWorldTick();
                helper.runAfterDelay(1,()->awaitCombustion(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initial,initialNitrogen,tracked,baseline,stamp,2400));return;
            }
            if(left<=0)throw new GameTestAssertException("paid heating timeout");
            helper.runAfterDelay(1,()->awaitPaid(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initial,initialNitrogen,tracked,baseline,left-1));
        } catch(Throwable failure){cleanup(level,owned,cables,lease);throw failure;}
    }

    private static void awaitCombustion(GameTestHelper helper,ServerLevel level,List<BlockPos> room,BlockPos exchanger,BlockPos pump,BlockPos heater,BlockPos apc,
            List<CableFaceNode> cables,List<Owned> owned,FixtureChunkTickets.Lease lease,double initial,double initialNitrogen,
            Map<PipeTopology.NodeKey,TrackedNode> tracked,ChamberState baseline,long priorStamp,int left) {
        try {
            ChamberState now=state(level,room,tracked);
            var h=machine(level,heater,"gas_thermo_heater");var e=machine(level,exchanger,"passive_heat_exchanger");
            var pumpBe=machine(level,pump,"gas_volume_pump");var apcBe=(PowerDeviceBlockEntity)level.getBlockEntity(apc);
            var runtime=e.deviceRuntimeState();
            double batteryLoss=initial-apcBe.energyJoules();
            boolean chemistry=baseline.room().plasma()-now.room().plasma()>.01&&baseline.room().oxygen()-now.room().oxygen()>.01
                    &&(now.room().co2()>baseline.room().co2()+.005||now.room().tritium()>baseline.room().tritium()+.005);
            boolean event=runtime.lastUnpoweredProcessWorldTick()>priorStamp&&runtime.lastThermalJoules()>0;
            boolean robust=chemistry&&now.room().energy()+now.pipeEnergy()>baseline.room().energy()+baseline.pipeEnergy()+batteryLoss+1000
                    &&now.room().averageKelvin()>baseline.room().averageKelvin()+20&&event;
            if(robust) {
                if(!h.applySettings(AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(false)))
                    throw new GameTestAssertException("heater disable rejected");
                long exchangerStamp=e.deviceRuntimeState().lastUnpoweredProcessWorldTick();
                MoonStation14.LOGGER.info("CHAMBER_ADMIT cells={} fuel={}->{} O2={}->{} products=({}, {}) Kmean={} Krange={} roomPipeE={} batteryLoss={} exchDeviceStamp={} passiveStamp={} exchStatus={} exchDelta={} pump={} battery={}",room.size(),baseline.room().plasma(),now.room().plasma(),baseline.room().oxygen(),now.room().oxygen(),now.room().co2(),now.room().tritium(),now.room().averageKelvin(),temperatureRange(level,room),now.room().energy()+now.pipeEnergy(),batteryLoss,runtime.lastUnpoweredProcessWorldTick(),runtime.lastPassiveWorldTick(),runtime.lastWorkStatus(),runtime.lastThermalJoules(),pumpBe.apcAllocatedWatts(),apcBe.energyJoules());
                double battery=((PowerDeviceBlockEntity)level.getBlockEntity(apc)).energyJoules();
                helper.runAfterDelay(1,()->awaitExchange(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initial,initialNitrogen,tracked,baseline,Double.NaN,exchangerStamp,battery,1000));return;
            }
            if(left%100==0) MoonStation14.LOGGER.info("CHAMBER_BURN_WAIT left={} fuelLoss={} oxygenLoss={} CO2={} T3={} Kmean={} Krange={} roomPipeGain={} batteryLoss={} event={} deviceStamp={} passiveStamp={} delta={} status={} pumpAlloc={} battery={}",left,baseline.room().plasma()-now.room().plasma(),baseline.room().oxygen()-now.room().oxygen(),now.room().co2(),now.room().tritium(),now.room().averageKelvin(),temperatureRange(level,room),now.room().energy()+now.pipeEnergy()-baseline.room().energy()-baseline.pipeEnergy(),batteryLoss,event,runtime.lastUnpoweredProcessWorldTick(),runtime.lastPassiveWorldTick(),runtime.lastThermalJoules(),runtime.lastWorkStatus(),pumpBe.apcAllocatedWatts(),apcBe.energyJoules());
            if(apcBe.energyJoules()<=0)throw new GameTestAssertException("startup battery depleted before robust burn");
            if(left<=0)throw new GameTestAssertException(String.format(java.util.Locale.ROOT,"burn timeout fuelLoss=%.4f O2Loss=%.4f CO2=%.4f T3=%.4f meanK=%.1f range=%s energyGain=%.0f batteryLoss=%.0f event=%s stamp=%d delta=%.0f",
                    baseline.room().plasma()-now.room().plasma(),baseline.room().oxygen()-now.room().oxygen(),now.room().co2(),now.room().tritium(),now.room().averageKelvin(),temperatureRange(level,room),now.room().energy()+now.pipeEnergy()-baseline.room().energy()-baseline.pipeEnergy(),batteryLoss,event,runtime.lastUnpoweredProcessWorldTick(),runtime.lastThermalJoules()));
            helper.runAfterDelay(1,()->awaitCombustion(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initial,initialNitrogen,tracked,baseline,runtime.lastUnpoweredProcessWorldTick(),left-1));
        } catch(Throwable failure){cleanup(level,owned,cables,lease);throw failure;}
    }

    private static void awaitExchange(GameTestHelper helper,ServerLevel level,List<BlockPos> room,BlockPos exchanger,BlockPos pump,BlockPos heater,BlockPos apc,
            List<CableFaceNode> cables,List<Owned> owned,FixtureChunkTickets.Lease lease,double initial,double initialNitrogen,
            Map<PipeTopology.NodeKey,TrackedNode> tracked,ChamberState baseline,double offEnergy,long oldStamp,double priorBattery,int left) {
        try {
            ChamberState now=state(level,room,tracked);var e=machine(level,exchanger,"passive_heat_exchanger");var h=machine(level,heater,"gas_thermo_heater");
            var pumpBe=machine(level,pump,"gas_volume_pump");
            var runtime=e.deviceRuntimeState();var apcBe=(PowerDeviceBlockEntity)level.getBlockEntity(apc);
            var ownedApc=owned.stream().filter(OwnedApc.class::isInstance).map(OwnedApc.class::cast).filter(x->x.pos().equals(apc)).findFirst().orElseThrow();
            if(!ownsApc(level,ownedApc)) {
                var actual=level.getBlockState(apc);
                MoonStation14.LOGGER.error("CHAMBER_APC_OWNERSHIP expectedBE={} actualBE={} expectedBlock={} actualBlock={} expectedFacing={} actualFacing={} expectedMount={} actualMount={}",
                        ownedApc.be(),level.getBlockEntity(apc),ownedApc.block(),actual.getBlock(),ownedApc.facing(),actual.hasProperty(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING)?actual.getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING):null,
                        ownedApc.supportState(),level.getBlockState(ownedApc.supportPos()));
                throw new GameTestAssertException("owned APC structure changed");
            }
            double batteryTolerance=Math.ulp(Math.max(priorBattery,apcBe.energyJoules()))*8d;
            boolean batteryNonincreasing=apcBe.energyJoules()<=priorBattery+batteryTolerance;
            if(!batteryNonincreasing) throw new GameTestAssertException("startup battery increased during chamber run");
            Double requested=h.apcPowerDemandWatts();
            boolean heaterStopped=requested!=null&&requested==0&&h.apcAllocatedWatts()==0
                    &&h.settingsSnapshot().enabled().map(v->!v).orElse(false);
            if(!Double.isFinite(offEnergy)) {
                if(heaterStopped) {
                    double checkpoint=now.pipeEnergy();long stamp=runtime.lastUnpoweredProcessWorldTick();
                    MoonStation14.LOGGER.info("CHAMBER_HEATER_STOP demand={} allocation={} checkpointPipeE={} exchanger={}",requested,h.apcAllocatedWatts(),checkpoint,runtime);
                    helper.runAfterDelay(1,()->awaitExchange(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initial,initialNitrogen,tracked,baseline,checkpoint,stamp,apcBe.energyJoules(),left-1));return;
                }
                if(left<=0)throw new GameTestAssertException("heater work did not stop");
                helper.runAfterDelay(1,()->awaitExchange(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initial,initialNitrogen,tracked,baseline,offEnergy,oldStamp,apcBe.energyJoules(),left-1));return;
            }
            Double pumpDemand=pumpBe.apcPowerDemandWatts();
            double nitrogenError=now.nitrogen()-initialNitrogen;
            boolean freshEvent=runtime.lastUnpoweredProcessWorldTick()>oldStamp&&runtime.lastThermalJoules()>0;
            boolean positiveExchange=runtime.lastThermalJoules()>0;
            boolean postOffPipeGain=now.pipeEnergy()>offEnergy+1;
            boolean chemicalExcess=now.room().energy()+now.pipeEnergy()>baseline.room().energy()+baseline.pipeEnergy()+Math.max(1,initial-apcBe.energyJoules());
            batteryNonincreasing=apcBe.energyJoules()<=initial;
            boolean batteryReserve=apcBe.energyJoules()>0;
            var nitrogenAudit=FinitePipeSpeciesAudit.assess(baseline.versions(),now.versions(),initialNitrogen,now.nitrogen(),Math.max(initialNitrogen,now.nitrogen()));
            boolean nitrogenConserved=nitrogenAudit.conserved();
            boolean pumpPowered=pumpDemand!=null&&pumpDemand>0&&pumpBe.apcAllocationKnown()&&pumpBe.apcAllocatedWatts()>=pumpDemand;
            boolean accepted=heaterStopped&&freshEvent&&positiveExchange&&postOffPipeGain&&chemicalExcess&&batteryNonincreasing&&batteryReserve&&nitrogenConserved&&pumpPowered;
            if(accepted) {
                MoonStation14.LOGGER.info("CHAMBER_EXCHANGE fuel={} products=({}, {}) roomE={} pipeE={} batteryLoss={} exch={}",now.room().plasma(),now.room().co2(),now.room().tritium(),now.room().energy(),now.pipeEnergy(),initial-apcBe.energyJoules(),runtime);
                cleanup(level,owned,cables,lease);helper.succeed();return;
            }
            if(left%100==0)logExchange(left,heaterStopped,freshEvent,positiveExchange,postOffPipeGain,chemicalExcess,batteryNonincreasing,batteryReserve,nitrogenConserved,pumpPowered,nitrogenAudit,initialNitrogen,initial,now,offEnergy,baseline,apcBe,h,pumpBe,requested,pumpDemand,runtime);
            if(apcBe.energyJoules()<=0)throw new GameTestAssertException("startup battery depleted post-heater");
            if(left<=0) {
                logExchange(left,heaterStopped,freshEvent,positiveExchange,postOffPipeGain,chemicalExcess,batteryNonincreasing,batteryReserve,nitrogenConserved,pumpPowered,nitrogenAudit,initialNitrogen,initial,now,offEnergy,baseline,apcBe,h,pumpBe,requested,pumpDemand,runtime);
                throw new GameTestAssertException("exchange timeout; see CHAMBER_EXCHANGE_FAILURE flags and N2 delta");
            }
            helper.runAfterDelay(1,()->awaitExchange(helper,level,room,exchanger,pump,heater,apc,cables,owned,lease,initial,initialNitrogen,tracked,baseline,offEnergy,oldStamp,apcBe.energyJoules(),left-1));
        } catch(Throwable failure){cleanup(level,owned,cables,lease);throw failure;}
    }

    private record RoomState(double plasma,double oxygen,double co2,double tritium,double energy,double averageKelvin) { }
    private record ChamberState(RoomState room,double pipeEnergy,double nitrogen,long inventoryVersionSum,Map<UUID,Long> versions) { }
    private static String temperatureRange(ServerLevel level,List<BlockPos> room) {
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(BlockPos cell:room){double t=AtmosphereService.INSTANCE.sample(level,cell).orElseThrow().temperatureKelvin();min=Math.min(min,t);max=Math.max(max,t);}
        return String.format(java.util.Locale.ROOT,"%.1f..%.1f",min,max);
    }
    private static ChamberState state(ServerLevel level,List<BlockPos> room,Map<PipeTopology.NodeKey,TrackedNode> tracked) {
        double plasma=0,oxygen=0,co2=0,tritium=0,energy=0,temp=0;
        for(BlockPos cell:room) { var gas=AtmosphereService.INSTANCE.sample(level,cell).orElseThrow();plasma+=gas.moles(GasType.PLASMA);oxygen+=gas.moles(GasType.OXYGEN);co2+=gas.moles(GasType.CARBON_DIOXIDE);tritium+=gas.moles(GasType.TRITIUM);energy+=gas.thermalEnergy();temp+=gas.temperatureKelvin(); }
        var inventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
        double pipeEnergy=0,nitrogen=0;long versionSum=0;var versions=new java.util.LinkedHashMap<UUID,Long>();
        for(var item:tracked.entrySet()) { var entry=inventory.get(item.getKey()).orElseThrow();if(!entry.active()||!entry.incarnation().equals(item.getValue().incarnation())||Math.abs(entry.volume()-item.getValue().volume())>1e-9)throw new GameTestAssertException("loop node identity changed");var share=inventory.nodeShare(item.getKey());pipeEnergy+=share.thermalEnergy();nitrogen+=share.moles(GasType.NITROGEN);var network=inventory.network(item.getKey()).orElseThrow();if(versions.putIfAbsent(network.id(),network.gasRevision())==null)versionSum+=network.gasRevision(); }
        return new ChamberState(new RoomState(plasma,oxygen,co2,tritium,energy,temp/room.size()),pipeEnergy,nitrogen,versionSum,Map.copyOf(versions));
    }

    private static void logExchange(int left,boolean heaterStopped,boolean freshEvent,boolean positiveExchange,boolean pipeGain,
            boolean chemicalExcess,boolean batteryNonincreasing,boolean batteryReserve,boolean nitrogenConserved,boolean pumpPowered,
            FinitePipeSpeciesAudit.Report audit,double initialNitrogen,double initialEnergy,ChamberState now,double checkpoint,ChamberState baseline,
            PowerDeviceBlockEntity apc,AtmosMachineBlockEntity heater,AtmosMachineBlockEntity pump,Double heaterDemand,Double pumpDemand,
            com.juicyslew.moonstation14.ms14.atmos.machine.AtmosDeviceRuntime.State event) {
        MoonStation14.LOGGER.info("CHAMBER_EXCHANGE_FLAGS left={} stop={} fresh={} exchPositive={} pipeGain={} chemical={} battNoninc={} battReserve={} N2ok={} pumpPowered={} N2={}/{} signed={} abs={} bound={} writes={} operand={} versions={} roomPipeGain={} pipeGainJ={} battery={} battSpent={} heaterDemand={} heaterAlloc={} pumpDemand={} pumpKnown={} pumpAlloc={} eventStamp={} eventStatus={} eventDelta={}",
                left,heaterStopped,freshEvent,positiveExchange,pipeGain,chemicalExcess,batteryNonincreasing,batteryReserve,nitrogenConserved,pumpPowered,now.nitrogen(),initialNitrogen,audit.signedResidual(),audit.absoluteError(),audit.bound(),audit.nodeWrites(),audit.operandMagnitude(),now.inventoryVersionSum(),
                now.room().energy()+now.pipeEnergy()-baseline.room().energy()-baseline.pipeEnergy(),now.pipeEnergy()-checkpoint,apc.energyJoules(),initialEnergy-apc.energyJoules(),heaterDemand,
                heater.apcAllocatedWatts(),pumpDemand,pump.apcAllocationKnown(),pump.apcAllocatedWatts(),event.lastUnpoweredProcessWorldTick(),event.lastWorkStatus(),event.lastThermalJoules());
    }

    private record TrackedNode(UUID incarnation,double volume) { }
    private static double loopNitrogen(PipeGasInventory inventory,Map<PipeTopology.NodeKey,TrackedNode> tracked) {
        double total=0;
        for(var item:tracked.entrySet()) {
            var entry=inventory.get(item.getKey()).orElseThrow(()->new GameTestAssertException("finite loop node disappeared"));
            if(!entry.active()||!entry.incarnation().equals(item.getValue().incarnation())
                    ||Math.abs(entry.volume()-item.getValue().volume())>1e-9) throw new GameTestAssertException("finite loop node identity changed");
            total+=inventory.nodeShare(item.getKey()).moles(GasType.NITROGEN);
        }
        return total;
    }

    private static AtmosMachineBlockEntity machine(ServerLevel level,BlockPos pos,String id) {
        if(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity m && m.profileIdentity().equals(id)) return m;
        throw new IllegalStateException("waiting for "+id);
    }
    private record PortAt(BlockPos host,PipeGasPortHost.Port port) { }
    private record SeedAt(BlockPos host,PipeGasPortHost.Port port,double moles) { }
    private static PipeGasPortHost.Port port(AtmosMachineBlockEntity m,String id) {
        return m.gasPorts().stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();
    }
    private static void placeMachine(GameTestHelper helper,BlockPos pos,net.minecraft.world.level.block.Block block,Direction facing,List<Owned> owned) {
        if(!helper.getLevel().getBlockState(pos).isAir()) throw new GameTestAssertException("machine location occupied");
        helper.getLevel().setBlock(pos,block.defaultBlockState().setValue(AtmosMachineBlock.FACING,facing),3);
        var be=(AtmosMachineBlockEntity)helper.getLevel().getBlockEntity(pos);
        owned.add(new OwnedMachine(pos,be,be.placementIdentity(),BuiltInRegistries.BLOCK.getKey(block).toString()));
    }
    private static void put(GameTestHelper helper,BlockPos pos,net.minecraft.world.level.block.Block block,List<Owned> owned) {
        if(!helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir()) throw new GameTestAssertException("shell location occupied");
        helper.setBlock(pos,block); owned.add(new OwnedShell(helper.absolutePos(pos),helper.getLevel().getBlockState(helper.absolutePos(pos))));
    }
    private sealed interface Owned permits OwnedMachine,OwnedPipe,OwnedApc,OwnedShell { BlockPos pos(); }
    private record OwnedMachine(BlockPos pos,AtmosMachineBlockEntity be,UUID placement,String blockId) implements Owned { }
    private record OwnedPipe(BlockPos pos,VisualPipeBlockEntity be,UUID identity) implements Owned { }
    private record OwnedApc(BlockPos pos,PowerDeviceBlockEntity be,net.minecraft.world.level.block.Block block,Direction facing,
                            BlockPos supportPos,BlockState supportState) implements Owned { }
    private record OwnedShell(BlockPos pos,BlockState state) implements Owned { }
    private static void cleanup(ServerLevel level,List<Owned> owned,List<CableFaceNode> cables,FixtureChunkTickets.Lease lease) {
        for(CableFaceNode cable:cables) if(CableStorage.has(level,cable.host(),cable.face(),CableTier.APC))
            CableStorage.remove(level,cable.host(),cable.face(),CableTier.APC);
        for(Owned item:owned) {
            BlockPos p=item.pos();
            if(item instanceof OwnedMachine m && level.getBlockEntity(p)==m.be()
                    &&(m.placement()==null||m.placement().equals(m.be().placementIdentity()))
                    &&BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString().equals(m.blockId()))
                level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            else if(item instanceof OwnedPipe q && level.getBlockEntity(p)==q.be()
                    &&q.be().functionalIdentity(VisualPipeBlockEntity.PRIMARY).filter(q.identity()::equals).isPresent()) {
                LoadedPipeGasOwner.deactivate(level,p,VisualPipeBlockEntity.PRIMARY);
                if(level.getBlockEntity(p)==q.be())level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            } else if(item instanceof OwnedApc a && ownsApc(level,a))
                level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
            else if(item instanceof OwnedShell s && level.getBlockState(p).equals(s.state())) level.setBlock(p,Blocks.AIR.defaultBlockState(),3);
        }
        lease.close();
    }

    private static boolean ownsApc(ServerLevel level,OwnedApc owner) {
        var current=level.getBlockState(owner.pos());
        var actualBe=level.getBlockEntity(owner.pos());
        if(!ApcFixtureOwnership.matches(owner.be(),actualBe,
                owner.block().defaultBlockState().setValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING,owner.facing()),current)) return false;
        return level.getBlockState(owner.supportPos()).equals(owner.supportState());
    }
}
