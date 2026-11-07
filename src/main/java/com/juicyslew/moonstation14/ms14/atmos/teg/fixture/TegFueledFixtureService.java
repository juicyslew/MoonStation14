package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import com.juicyslew.moonstation14.MoonStation14;
import java.util.*;

/** Explicit construction-only admission for the planned finite fueled-loop fixture. */
public final class TegFueledFixtureService {
    private TegFueledFixtureService() { }
    public static String build(ServerLevel level, BlockPos origin) {
        BlockPos core=origin.offset(22,2,18);
        TegFueledFixturePlan.Layout p=TegFueledFixturePlan.layout(core);var d=p.electrical().devices();
        var saved=TegFixtureSavedData.existing(level).orElse(null);
        if(saved!=null&&(saved.entry(origin).isPresent()||saved.count()>=TegFixtureSavedData.MAX_FIXTURES))return "Build refused: saved fixture ownership already exists or capacity is full.";
        if(!p.startupPowerValid())return "Build refused: fueled startup topology proof failed.";
        Set<BlockPos> cells=new LinkedHashSet<>(p.allOwnedCells());cells.addAll(p.sharedCableHosts());
        for(var n:p.combinedCables())cells.add(n.host());
        cells.addAll(List.of(d.core(),d.startupApc(),d.substation(),d.outputApc(),d.load(),d.startupApc().relative(Direction.WEST),d.outputApc().relative(Direction.EAST)));
        // Reserve air clearance above the foundation plane only. Ground below the
        // machine-feet origin is deliberately not treated as an excavation volume.
        for(int x=0;x<48;x++)for(int y=2;y<8;y++)for(int z=0;z<48;z++)cells.add(origin.offset(x,y,z));
        // Require existing terrain and admission for every reserved footprint cell.
        String preflight=TegFixturePlacement.preflight(level,origin,cells,TegFueledFixturePlan.foundation(origin));
        if(preflight!=null)return preflight;
        Set<BlockPos> shells=new HashSet<>(p.wallShell());shells.addAll(p.coldWallShell());shells.addAll(p.sharedCableHosts());
        String phase="placeHosts";
        try {
            // Shared presentation hook; finite layout and exact labels remain unchanged.
            TegFixturePlacement.placePresentation(level,shells,TegFueledFixturePlan.observationWindows(core),TegFueledFixturePlan.signs(core));
             TegFixturePlacement.placeCableHosts(level,p.combinedCables(),union(shells,p.allOwnedCells()));
            TegFixturePlacement.placeBaseAtmosMachines(level,p);
            TegFixturePlacement.placePowerDevices(level,d,true);
            phase="admitPipeLanes";String laneFailure=TegFixturePlacement.admitExposedLanes(level,union(p.exposedPipePositions(),p.coldExposedPipePositions()),VisualPipeBlockEntity.PRIMARY);if(laneFailure!=null)return laneFailure;
            phase="admitCables";String cableFailure=TegFixturePlacement.admitCables(level,p.combinedCables());if(cableFailure!=null)return cableFailure;
            Set<BlockPos> crossings=new HashSet<>(p.sealedCrossings());crossings.addAll(p.coldSealedCrossings());Map<BlockPos,Direction> crossingFaces=new LinkedHashMap<>();for(BlockPos pos:crossings)crossingFaces.put(pos,Direction.EAST);
            phase="admitSealedCrossings";String crossingFailure=TegFixturePlacement.admitSealedCrossings(level,crossingFaces,PipeTopology.Layer.PRIMARY);if(crossingFailure!=null)return crossingFailure;
            var atmosphere=AtmosphereService.INSTANCE;
            var empty=new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(Map.of(),293.15);
            phase="admitRooms";if(!atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,List.copyOf(p.roomInterior()),empty)||!atmosphere.initializeEnclosedFiniteMixtureForTrustedSetup(level,List.copyOf(p.coldRoomInterior()),empty))return "Partial build: finite vacuum room enclosure admission failed.";
            phase="reconcilePorts";List<AtmosMachineBlockEntity> machines=new ArrayList<>();for(var m:p.machines()){if(!(level.getBlockEntity(m.position()) instanceof AtmosMachineBlockEntity entity))return "Partial build: machine missing at "+m.position();entity.reconcileLoadedPorts(level);machines.add(entity);}
            if(!(level.getBlockEntity(d.core()) instanceof AtmosMachineBlockEntity teg))return "Partial build: TEG core block entity missing.";teg.reconcileLoadedPorts(level);
            if(TegAssembly.resolve(level,teg).isEmpty())return "Partial build: TEG assembly admission failed; manual recovery required.";
            if(teg.apcPowerIdentity()==null)return "Partial build: TEG core placement identity unavailable after reconciliation.";
            for(var machine:machines)machine.setControl("enabled",false);
            List<Map<String,PipeTopology.NodeKey>> livePorts=new ArrayList<>();for(var machine:machines){Map<String,PipeTopology.NodeKey> ports=new HashMap<>();for(var gp:machine.gasPorts())ports.put(gp.id(),gp.key(machine.getBlockPos()));livePorts.add(ports);}
            phase="planTopology";var topology=TegFueledFixtureTopology.plan(p,livePorts);List<LoadedPipeGasWork.Port> capturePorts=topology.representatives().stream().map(LoadedPipeGasWork.Port::new).toList();
            phase="captureComponents";
            var captured=LoadedPipeGasWork.capture(level,capturePorts).orElse(null);
            if(captured==null||captured.aliases()||captured.components().size()!=6)return "Partial build: expected six distinct live gas components; aliases="+(captured!=null&&captured.aliases())+" components="+(captured==null?0:captured.components().size())+" roots="+topology.representatives()+"; manual recovery required.";
            phase="exactValidate";Set<PipeTopology.NodeKey> expectedUnion=new HashSet<>(),actualUnion=new HashSet<>();
            for(int i=0;i<topology.components().size();i++){var root=topology.representatives().get(i);var component=captured.componentFor(root).orElse(null);if(component==null)return "Partial build: planned representative missing: "+root+".";Set<PipeTopology.NodeKey> actual=new HashSet<>();component.nodes().forEach(n->actual.add(n.key()));if(!actual.equals(topology.components().get(i))||!Collections.disjoint(expectedUnion,topology.components().get(i))||!actualUnion.addAll(actual))return "Partial build: exact planned gas membership mismatch at "+root+"; manual recovery required.";expectedUnion.addAll(topology.components().get(i));}
            if(!actualUnion.equals(expectedUnion))return "Partial build: unexpected gas component ownership; manual recovery required.";
            for(BlockPos cell:p.roomInterior())if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())return "Partial build: hot room enclosure is incomplete.";
            for(BlockPos cell:p.coldRoomInterior())if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())return "Partial build: cold room enclosure is incomplete.";
            UUID north=machines.stream().filter(m->m.getBlockPos().equals(p.electrical().northCirculator())).findFirst().orElseThrow().gasPorts().stream().filter(x->x.id().equals("inlet")).map(PipeGasPortHost.Port::incarnation).findFirst().orElse(null);
            UUID south=machines.stream().filter(m->m.getBlockPos().equals(p.electrical().southCirculator())).findFirst().orElseThrow().gasPorts().stream().filter(x->x.id().equals("inlet")).map(PipeGasPortHost.Port::incarnation).findFirst().orElse(null);
            if(north==null||south==null)return "Partial build: exact circulator port identity unavailable.";
             Map<PipeTopology.NodeKey,UUID> gasOwners=TegFixturePlacement.gasOwners(captured);
             Map<BlockPos,UUID> machineOwners=TegFixturePlacement.machineOwners(machines,teg);
            phase="persistToken";var record=new TegFixtureSavedData.Entry(origin,teg.apcPowerIdentity(),north,south,UUID.randomUUID(),false,TegFixtureSavedData.Mode.FUELED,gasOwners,machineOwners);
            if(!TegFixtureSavedData.forMutation(level).recordBuilt(record))return "Structure placed, but saved ownership admission failed; manual recovery required.";
            return "Built fueled TEG hardware in clear 48x8x48 FULL-loaded footprint at "+origin+" (uninitialized; no fuel or gas seeded). Core "+d.core()+"; heater "+p.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow().position()+"; lamp "+d.load()+". Initialize once, then status.";
        }catch(RuntimeException failure){MoonStation14.LOGGER.error("Fueled TEG fixture build failed phase={} origin={}",phase,origin,failure);return "Partial build: phase="+phase+" cause="+failure.getClass().getSimpleName()+"; manual recovery required.";}
    }

    /** One-shot, identity-bound species admission for a freshly built fueled fixture. */
    public static String initialize(ServerLevel level, BlockPos origin) {
        var data=TegFixtureSavedData.existing(level).orElse(null);
        var saved=data==null?Optional.<TegFixtureSavedData.Entry>empty():data.entry(origin);
        if(saved.isEmpty())return "Initialization refused: no saved fueled fixture at that origin.";
        var e=saved.get();
        if(e.mode()!=TegFixtureSavedData.Mode.FUELED)return "Initialization refused: this fixture is not fueled; no resources were written.";
        if(e.initialized())return "Initialization refused: fixture token was already consumed; no refill or reset is permitted.";
        if(!loaded(level,origin))return "Initialization refused: fixture footprint is not already fully loaded.";
        try {
            BlockPos corePos=origin.offset(22,2,18);var p=TegFueledFixturePlan.layout(corePos);var d=p.electrical().devices();
            List<AtmosMachineBlockEntity> machines=new ArrayList<>();
            for(var m:p.machines()){
                if(!(level.getBlockEntity(m.position()) instanceof AtmosMachineBlockEntity be))return refused("machine missing/replaced at "+m.position());
                var expectedBlock=switch(m.kind()){case CIRCULATOR->ModBlocks.TEG_CIRCULATOR.get();case PUMP->ModBlocks.GAS_VOLUME_PUMP.get();case PRESSURE_PUMP->ModBlocks.GAS_PRESSURE_PUMP.get();case EXCHANGER->ModBlocks.PASSIVE_HEAT_EXCHANGER.get();case HEATER->ModBlocks.GAS_THERMO_HEATER.get();};
                if(!be.getBlockState().is(expectedBlock)||!be.getBlockState().hasProperty(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)||be.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)!=m.facing())return refused("machine kind/facing mismatch at "+m.position());
                be.reconcileLoadedPorts(level); if(!Objects.equals(e.ownedMachines().get(m.position()),be.placementIdentity())||!be.settingsValid()||be.resolvedProfile()==null)return refused("machine identity/profile mismatch at "+m.position()); machines.add(be);
            }
            if(!(level.getBlockEntity(corePos) instanceof AtmosMachineBlockEntity core))return refused("core missing/replaced");
            core.reconcileLoadedPorts(level);
            if(!core.getBlockState().is(ModBlocks.TEG_CENTER.get())||!core.getBlockState().hasProperty(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)||core.getBlockState().getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)!=Direction.NORTH
                    ||!Objects.equals(e.ownedMachines().get(corePos),core.placementIdentity())||!Objects.equals(e.core(),core.apcPowerIdentity())||!core.settingsValid()||core.resolvedProfile()==null||TegAssembly.resolve(level,core).isEmpty())return refused("core kind/facing, identity, or TEG assembly mismatch");
            if(e.ownedMachines().size()!=8)return refused("saved machine ownership fingerprint incomplete");
            if(!p.startupPowerValid()||!validateCables(level,p))return refused("planned cable topology mismatch");
            List<Map<String,PipeTopology.NodeKey>> ports=new ArrayList<>();for(var m:machines){Map<String,PipeTopology.NodeKey> x=new HashMap<>();for(var gp:m.gasPorts())x.put(gp.id(),gp.key(m.getBlockPos()));ports.add(x);}
            var topology=TegFueledFixtureTopology.plan(p,ports);
            if(e.ownedGas().size()==0)return refused("saved gas ownership fingerprint incomplete");
            var capture=LoadedPipeGasWork.capture(level,topology.representatives().stream().map(LoadedPipeGasWork.Port::new).toList()).orElse(null);
            if(capture==null||capture.aliases()||capture.components().size()!=6)return refused("six distinct gas components unavailable");
            Map<PipeTopology.NodeKey,UUID> actual=new HashMap<>();Set<PipeTopology.NodeKey> actualUnion=new HashSet<>();
            for(int i=0;i<6;i++){var root=topology.representatives().get(i);var c=capture.componentFor(root).orElse(null);if(c==null)return refused("component missing");var gas=c.aggregate();if(!gas.gasMoles().isEmpty()||gas.thermalEnergy()!=0)return refused("gas component is not vacuum at "+root);Set<PipeTopology.NodeKey> keys=new HashSet<>();for(var n:c.nodes()){keys.add(n.key());actual.put(n.key(),n.incarnation());}if(!keys.equals(topology.components().get(i))||!Collections.disjoint(actualUnion,keys))return refused("exact gas component membership changed");actualUnion.addAll(keys);}
            if(!actual.equals(e.ownedGas()))return refused("saved gas owner fingerprint changed");
            var atmosphere=AtmosphereService.INSTANCE;
            for(BlockPos cell:p.roomInterior())if(!vacuumRoomCell(level,atmosphere,cell))return refused("hot room is missing or nonempty");
            for(BlockPos cell:p.coldRoomInterior())if(!vacuumRoomCell(level,atmosphere,cell))return refused("cold room is missing or nonempty");
            var north=machines.get(0);var south=machines.get(4);
            if(!Objects.equals(e.north(),port(north,"inlet"))||!Objects.equals(e.south(),port(south,"inlet")))return refused("circulator port identity changed");
            var seeds=new ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
            List<Double> loopVolumes=new ArrayList<>();for(var root:topology.representatives()){var c=capture.componentFor(root).orElseThrow();loopVolumes.add(c.nodes().stream().mapToDouble(LoadedPipeComponent.Node::volume).sum());}
            var hotAllocation=TegFueledFixturePlan.apportionFiniteMoles(43,loopVolumes.subList(0,3));var coldAllocation=TegFueledFixturePlan.apportionFiniteMoles(43,loopVolumes.subList(3,6));
            for(int i=0;i<6;i++){
                var root=topology.representatives().get(i);var component=capture.componentFor(root).orElseThrow();var node=component.nodes().stream().filter(n->n.key().equals(root)).findFirst().orElse(null);
                if(node==null)return refused("component seed port unavailable");
                double moles=i<3?hotAllocation.get(i):coldAllocation.get(i-3);
                seeds.add(new TrustedPipeFixtureInitialization.PortSeed(root.position(),root.portId(),node.incarnation(),new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN,moles),293.15,node.volume())));
            }
            var readiness=TrustedPipeFixtureInitialization.validateVacuumPorts(level,seeds);if(!readiness.successful())return refused("pipe seed preflight "+readiness.failure()+" ("+readiness.detail()+")");
            var startup=powerDevice(level,d.startupApc());var output=powerDevice(level,d.outputApc());
            if(startup==null||output==null||startup.energyJoules()!=0||output.energyJoules()!=0||!startup.breakerClosed()||!output.breakerClosed()
                    ||!validApc(level,d.startupApc(),Direction.EAST,startup)||!validApc(level,d.outputApc(),Direction.WEST,output))return refused("startup/output APC identity, kind, facing, support, or zero-energy state changed");
            if(!data.markInitialized(e))return refused("initialization token changed; no resources written");
            var seeded=TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level,seeds);
            if(!seeded.successful())return "Initialization token consumed but gas seeding failed after "+seeded.completedPorts()+" ports; use a fresh disposable fixture and manual recovery (no retry).";
            var hot=new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,.1,com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN,1d),293.15);
            var cold=new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN,10d),293.15);
            for(BlockPos cell:p.roomInterior())if(!atmosphere.initializeFiniteMixtureForTrustedSetup(level,cell,hot))return partial("hot room seeding failed");
            for(BlockPos cell:p.coldRoomInterior())if(!atmosphere.initializeFiniteMixtureForTrustedSetup(level,cell,cold))return partial("cold room seeding failed");
            startup.setEnergyJoules(1_000_000); output.setEnergyJoules(0);
            for(int i:new int[]{1,5})if(!machines.get(i).applySettings(AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(true)))return partial("pump enable rejected");
            if(!machines.get(3).applySettings(AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(true))
                    ||!machines.get(3).applySettings(AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K,new AtmosMachineSettings.NumericValue(593.15)))return partial("heater settings rejected");
            if(!core.applySettings(AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(true)))return partial("core enable rejected");
            return "Initialized fueled finite fixture once: 43 mol N2 in each loop, finite room mixtures at 293.15 K, startup APC 1 MJ; output APC 0 J.";
        } catch(RuntimeException failure){MoonStation14.LOGGER.error("Fueled TEG initialization failed origin={}",origin,failure);return partial("unexpected "+failure.getClass().getSimpleName());}
    }

    /** Read-only live snapshot; refuses stale fixture fingerprints rather than reporting replaced hardware. */
    public static String status(ServerLevel level,BlockPos origin){
        if(!loaded(level,origin))return "NOT READY: fueled 48x8x48 footprint is not FULL-loaded; status never loads chunks.";
        var e=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(e==null||e.mode()!=TegFixtureSavedData.Mode.FUELED)return "NOT READY: no saved fueled fixture ownership at "+origin+".";
        var p=TegFueledFixturePlan.layout(origin.offset(22,2,18));var d=p.electrical().devices();
        List<Map<String,PipeTopology.NodeKey>> livePorts=new ArrayList<>();
        for(var m:p.machines()){
            var be=level.getBlockEntity(m.position());var expected=switch(m.kind()){case CIRCULATOR->ModBlocks.TEG_CIRCULATOR.get();case PUMP->ModBlocks.GAS_VOLUME_PUMP.get();case PRESSURE_PUMP->ModBlocks.GAS_PRESSURE_PUMP.get();case EXCHANGER->ModBlocks.PASSIVE_HEAT_EXCHANGER.get();case HEATER->ModBlocks.GAS_THERMO_HEATER.get();};
            if(!(be instanceof AtmosMachineBlockEntity machine)||!level.getBlockState(m.position()).is(expected)||!level.getBlockState(m.position()).hasProperty(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)||level.getBlockState(m.position()).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)!=m.facing()||!Objects.equals(e.ownedMachines().get(m.position()),machine.placementIdentity()))return "NOT READY: machine missing/replaced, wrong-facing, or ownership changed at "+m.position()+".";
            Map<String,PipeTopology.NodeKey> ports=new HashMap<>();for(var gp:machine.gasPorts())ports.put(gp.id(),gp.key(m.position()));livePorts.add(ports);
        }
        var core=level.getBlockEntity(d.core());if(!(core instanceof AtmosMachineBlockEntity teg)||!level.getBlockState(d.core()).is(ModBlocks.TEG_CENTER.get())||!Objects.equals(e.ownedMachines().get(d.core()),teg.placementIdentity())||!Objects.equals(e.core(),teg.apcPowerIdentity())||TegAssembly.resolve(level,teg).isEmpty()||e.ownedMachines().size()!=8||!p.startupPowerValid()||!validateCables(level,p))return "NOT READY: core identity/assembly, cable topology, or saved ownership changed.";
        if(!teg.gasPorts().isEmpty()||livePorts.size()!=7)return "NOT READY: expected seven reconciled gas machines and a gas-port-free core.";
        TegFueledFixtureTopology.Plan topology;try{topology=TegFueledFixtureTopology.plan(p,livePorts);}catch(IllegalArgumentException invalid){return "NOT READY: canonical gas-port topology unavailable ("+invalid.getMessage()+").";}var captured=LoadedPipeGasWork.capture(level,topology.representatives().stream().map(LoadedPipeGasWork.Port::new).toList()).orElse(null);
        if(captured==null||captured.aliases()||captured.components().size()!=6)return "NOT READY: six distinct loaded gas components unavailable.";
        Map<PipeTopology.NodeKey,UUID> actualOwners=new HashMap<>();Map<PipeTopology.NodeKey,Set<PipeTopology.NodeKey>> membership=new HashMap<>();for(var comp:captured.components().values()){Set<PipeTopology.NodeKey> keys=new HashSet<>();for(var n:comp.nodes()){actualOwners.put(n.key(),n.incarnation());keys.add(n.key());}membership.put(comp.root(),keys);}if(!actualOwners.equals(e.ownedGas())||!TegFueledFixtureTopology.exact(topology,membership))return "NOT READY: saved gas ownership or canonical component topology changed.";
        var startup=powerDevice(level,d.startupApc());var output=powerDevice(level,d.outputApc());if(startup==null||output==null||!validApc(level,d.startupApc(),Direction.EAST,startup)||!validApc(level,d.outputApc(),Direction.WEST,output)||!Objects.equals(e.core(),teg.apcPowerIdentity())||!level.getBlockState(d.load()).is(ModBlocks.POWER_LAMP.get()))return "NOT READY: APC/core/load identity, kind, facing, or support changed.";
        var atmosphere=AtmosphereService.INSTANCE;double plasma=0,oxygen=0,coldN2=0,hotRoomEnergy=0,hotRoomCp=0,coldRoomEnergy=0,coldRoomCp=0;
        for(BlockPos cell:p.roomInterior()){if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())return "NOT READY: hot finite room ownership missing at "+cell+".";var gas=atmosphere.sample(level,cell).orElse(null);if(gas==null)return "NOT READY: hot room sample unavailable at "+cell+".";plasma+=gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA);oxygen+=gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);hotRoomEnergy+=gas.thermalEnergy();hotRoomCp+=gas.heatCapacity();}
        for(BlockPos cell:p.coldRoomInterior()){if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())return "NOT READY: cold finite room ownership missing at "+cell+".";var gas=atmosphere.sample(level,cell).orElse(null);if(gas==null)return "NOT READY: cold room sample unavailable at "+cell+".";coldN2+=gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN);coldRoomEnergy+=gas.thermalEnergy();coldRoomCp+=gas.heatCapacity();}
        var hot=TegFueledLoopMeasurement.measure(topology.representatives().subList(0,3).stream().map(root->captured.componentFor(root).orElseThrow().aggregate()).toList()).mixture();var cold=TegFueledLoopMeasurement.measure(topology.representatives().subList(3,6).stream().map(root->captured.componentFor(root).orElseThrow().aggregate()).toList()).mixture();var state=teg.tegRuntimeState();var allocation=PowerRuntime.apcOutputObservation(level,output);boolean lit=level.getBlockState(d.load()).hasProperty(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)&&level.getBlockState(d.load()).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT);
        String apc=allocation.known()?fmt(allocation.outputWatts())+" W @sample "+allocation.sampleTick()+" / topology "+allocation.topologyGeneration():"UNKNOWN";
        var heater=p.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow();var h=(AtmosMachineBlockEntity)level.getBlockEntity(heater.position());
        StringBuilder loads=new StringBuilder();loads.append("Core ").append(machineAllocation(teg,level));for(var m:p.machines())if(m.kind()==TegFueledFixturePlan.MachineKind.PUMP||m.kind()==TegFueledFixturePlan.MachineKind.HEATER){var be=(AtmosMachineBlockEntity)level.getBlockEntity(m.position());loads.append("; ").append(m.kind()).append('@').append(m.position()).append(' ').append(machineAllocation(be,level));}
        return TegFueledFixtureStatusFormatter.format(new TegFueledFixtureStatusFormatter.Snapshot(e.initialized()?"initialized / finite":"BUILT / NOT INITIALIZED",d.core().toString(),heater.position().toString(),h.control("enabled",false),plasma,oxygen,hotRoomCp>0?hotRoomEnergy/hotRoomCp:293.15,coldN2,coldRoomCp>0?coldRoomEnergy/coldRoomCp:293.15,hot.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN),hot.temperatureKelvin(),hot.pressureKpa(),cold.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN),cold.temperatureKelvin(),cold.pressureKpa(),loads.toString(),startup.energyJoules(),state.lastGeneratedJoules(),state.lastGeneratedWatts(),state.cumulativeGeneratedJoules(),teg.powerSupplierOffer()==null?"UNKNOWN":fmt(teg.powerSupplierOffer().currentWatts())+" W",apc,output.energyJoules(),lit,TegFixtureService.deliveryReadout(level,origin)));
    }
    private static boolean fresh(AtmosMachineBlockEntity m,ServerLevel l){long now=PowerRuntime.currentSampleTick(l);return m.apcAllocationKnown()&&m.apcSampleTick()>=0&&now>=m.apcSampleTick()&&now-m.apcSampleTick()<=PowerRuntime.TICKS_PER_SOLVE;}
    private static String machineAllocation(AtmosMachineBlockEntity m,ServerLevel l){Double demand=m.apcPowerDemandWatts();return (demand==null?"0.0":fmt(demand))+" W requested / "+(fresh(m,l)?fmt(m.apcAllocatedWatts())+" W actual @sample "+m.apcSampleTick()+" topology "+PowerGraphService.topologyGeneration(l):"UNKNOWN (stale/unsampled)");}
    private static String fmt(double n){return String.format(Locale.ROOT,"%.1f",n);}
    private static String refused(String why){return "Initialization refused before consuming token: "+why+".";}
    private static String partial(String why){return "Initialization token consumed; "+why+". Use a fresh disposable fixture and manual recovery; do not retry or refill.";}
    private static boolean loaded(ServerLevel level,BlockPos origin){for(int x=origin.getX()>>4;x<=(origin.getX()+47)>>4;x++)for(int z=origin.getZ()>>4;z<=(origin.getZ()+47)>>4;z++)if(level.getChunkSource().getChunk(x,z,ChunkStatus.FULL,false)==null)return false;return true;}
    private static UUID port(AtmosMachineBlockEntity m,String id){return m.gasPorts().stream().filter(p->p.id().equals(id)).map(PipeGasPortHost.Port::incarnation).findFirst().orElse(null);}
    private static boolean vacuumRoomCell(ServerLevel level,AtmosphereService atmosphere,BlockPos pos){var endpoint=atmosphere.finiteRoomEndpointForTrustedSimulation(level,pos).orElse(null);if(endpoint==null)return false;var gas=endpoint.snapshot();return gas!=null&&gas.gasMoles().isEmpty()&&gas.thermalEnergy()==0;}
    private static PowerDeviceBlockEntity powerDevice(ServerLevel l,BlockPos p){return l.getBlockEntity(p) instanceof PowerDeviceBlockEntity e?e:null;}
    private static boolean validApc(ServerLevel l,BlockPos p,Direction facing,PowerDeviceBlockEntity be){var state=l.getBlockState(p);BlockPos support=p.relative(facing.getOpposite());return state.is(ModBlocks.APC.get())&&state.hasProperty(PowerDeviceBlock.FACING)&&state.getValue(PowerDeviceBlock.FACING)==facing&&l.getBlockState(support).isFaceSturdy(l,support,facing);}
    private static boolean validateCables(ServerLevel l,TegFueledFixturePlan.Layout p){for(var n:p.combinedCables())if(!CableStorage.has(l,n.host(),n.face(),n.tier()))return false;return true;}
    private static Set<BlockPos> union(Set<BlockPos>a,Set<BlockPos>b){Set<BlockPos> r=new LinkedHashSet<>(a);r.addAll(b);return r;}
}
