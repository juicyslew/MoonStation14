package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly;
import com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntimeNode;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/** Construction admission for a continuous-layout hardware frame. It does not initialize resources. */
public final class TegContinuousFixtureService {
    static final double STARTUP_CARRIER_MINIMUM_K = 590d;
    static final String RESOURCE_POWER_WAIT_PREFIX="Resource initialization waiting for power topology:";
    enum PowerAdmissionStatus { READY, WAIT, REFUSED }
    record PowerAdmission(PowerAdmissionStatus status,String reason) { }
    private TegContinuousFixtureService() { }

    public static String build(ServerLevel level, BlockPos origin) {
        String phase="preflight";
        try {
            var saved=TegFixtureSavedData.existing(level).orElse(null);
            if(saved!=null&&(saved.entry(origin).isPresent()||saved.count()>=TegFixtureSavedData.MAX_FIXTURES))
                return "Build refused: saved fixture ownership already exists or capacity is full.";
            var mixerBlock=ModBlocks.GAS_MIXER.get();
            var radiatorBlock=ModBlocks.PASSIVE_HEAT_EXCHANGER.get();
            AtmosMachineProfile mixer=ModAtmosMachines.catalog(level).get(ModAtmosMachines.id(mixerBlock.profileIdentity()));
            AtmosMachineProfile radiator=ModAtmosMachines.catalog(level).get(ModAtmosMachines.id(radiatorBlock.profileIdentity()));
            if(mixer==null||radiator==null)return "Build refused: resolved mixer or radiator profile is unavailable.";
            var layout=TegContinuousFuelPlan.layout(origin,mixer,radiator);
            List<CableFaceNode> cables=TegContinuousPowerPlan.cables(origin);
            if(!layout.fueled().startupPowerValid()||!TegContinuousPowerPlan.secondaryStartupCircuitValid(layout,cables))
                return "Build refused: split startup-APC circuits failed their pure topology/isolation proof.";
            Set<BlockPos> planned=new LinkedHashSet<>(layout.fueled().allOwnedCells());
            planned.addAll(layout.roomA()); planned.addAll(layout.roomB());
            planned.addAll(layout.shellA()); planned.addAll(layout.shellB()); planned.addAll(layout.machines()); planned.addAll(layout.presentationSigns().keySet());
            planned.addAll(layout.outsideVent()); layout.routes().values().forEach(planned::addAll);
            planned.addAll(layout.wasteMountWall());
            cables.forEach(c->planned.add(c.host()));
            for(int x=0;x<TegContinuousFuelPlan.WIDTH;x++)for(int y=2;y<TegContinuousFuelPlan.HEIGHT;y++)
                for(int z=0;z<TegContinuousFuelPlan.DEPTH;z++)planned.add(origin.offset(x,y,z));
            String refusal=TegFixturePlacement.preflight(level,origin,planned,TegFueledFixturePlan.foundation(origin),TegContinuousFuelPlan.HEIGHT);
            if(refusal!=null)return refusal;
            refusal=TegFixturePlacement.continuousExteriorFailure(level,layout);
            if(refusal!=null)return refusal;

            phase="hardware placement";
            var placement=TegFixturePlacement.placeContinuousHardware(level,layout);
            phase="post-placement validation";
            var observed=observeHardware(level,layout,placement);
            var outputPos=layout.fueled().electrical().devices().outputApc();
            if(!(level.getBlockEntity(outputPos) instanceof PowerDeviceBlockEntity output)||output.energyJoules()!=0)
                throw new IllegalStateException("operating APC is not at zero bootstrap energy");
            var secondaryApc=level.getBlockEntity(TegContinuousPowerPlan.equipment(layout).secondaryStartupApc());
            if(!(secondaryApc instanceof PowerDeviceBlockEntity secondary)||secondary.energyJoules()!=0)
                throw new IllegalStateException("dedicated startup APC is not at zero bootstrap energy");
            var entry=new TegFixtureSavedData.Entry(origin,observed.coreIdentity(),observed.north(),observed.south(),UUID.randomUUID(),false,
                    TegFixtureSavedData.Mode.CONTINUOUS,observed.gasOwners(),observed.machineOwners());
            if(!TegFixtureSavedData.forMutation(level).recordBuilt(entry))
                throw new IllegalStateException("saved continuous fixture ownership admission failed");
            return "Built continuous TEG hardware at "+origin+" (uninitialized; no carrier gas or APC bootstrap seeded).";
        } catch(RuntimeException failure) {
            MoonStation14.LOGGER.error("Continuous TEG fixture build failed phase={} origin={}",phase,origin,failure);
            return "Partial build: phase="+phase+" cause="+failure.getClass().getSimpleName()+"; manual recovery required.";
        }
    }

    /** Read-only observation of the just-placed continuous frame; does not seed or enable resources. */
    static ObservedHardware observeHardware(ServerLevel level,TegContinuousFuelPlan.Layout layout,
                                               TegFixturePlacement.ContinuousHardware hardware) {
            requireLoaded(level,layout.core());
            var coreState=level.getBlockState(layout.core());
            if(!coreState.is(ModBlocks.TEG_CENTER.get())
                    || coreState.getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)!=Direction.NORTH
                    || level.getBlockEntity(layout.core())!=hardware.core()||!hardware.core().settingsValid()
                    ||hardware.core().resolvedProfile()==null)
                throw new IllegalStateException("TEG core kind/facing/profile/settings invalid");
            String refusal=TegFixturePlacement.continuousExteriorFailure(level,layout);
            if(refusal!=null)throw new IllegalStateException(refusal);
            if(TegAssembly.resolve(level,hardware.core()).isEmpty())throw new IllegalStateException("TEG assembly admission failed");
            var electrical=layout.fueled().electrical().devices();
            if(!level.getBlockState(electrical.outputApc()).is(ModBlocks.APC.get())
                    || !level.getBlockState(electrical.outputApc()).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING).equals(Direction.WEST)
                    || !(level.getBlockEntity(electrical.outputApc()) instanceof PowerDeviceBlockEntity))
                throw new IllegalStateException("operating APC block entity missing at "+electrical.outputApc());
            if(!level.getBlockState(electrical.startupApc()).isAir())
                throw new IllegalStateException("startup APC must not be part of continuous hardware");
            BlockPos secondaryApc=TegContinuousPowerPlan.equipment(layout).secondaryStartupApc();
            if(!level.getBlockState(secondaryApc).is(ModBlocks.APC.get())
                    ||level.getBlockState(secondaryApc).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING)!=TegContinuousPowerPlan.equipment(layout).secondaryStartupApcFacing()
                    ||!(level.getBlockEntity(secondaryApc) instanceof PowerDeviceBlockEntity))
                throw new IllegalStateException("dedicated second-heater startup APC is missing or misoriented");

            Map<BlockPos,List<PipeGasPortHost.Port>> actualPorts=new LinkedHashMap<>();
            for(var machine:hardware.baseMachines())actualPorts.put(machine.getBlockPos(),machine.gasPorts());
            for(var machine:hardware.additional().atmosMachines())actualPorts.put(machine.getBlockPos(),machine.gasPorts());
            var topology=TegContinuousFixtureTopology.plan(layout,actualPorts);
            var captured=LoadedPipeGasWork.capture(level,topology.representatives().stream().map(LoadedPipeGasWork.Port::new).toList()).orElse(null);
            if(captured==null||captured.aliases()||captured.components().size()!=10)
                throw new IllegalStateException("expected ten distinct continuous gas components");
            Map<PipeTopology.NodeKey,Collection<PipeTopology.NodeKey>> actual=new HashMap<>();
            for(var component:captured.components().values()) {
                Set<PipeTopology.NodeKey> nodes=new HashSet<>(); component.nodes().forEach(node->nodes.add(node.key()));
                PipeTopology.NodeKey representative=topology.representatives().stream().filter(nodes::contains).findFirst().orElse(null);
                if(representative==null||actual.put(representative,nodes)!=null)
                    throw new IllegalStateException("unexpected or duplicate continuous gas component");
            }
            if(!TegFueledFixtureTopology.exact(topology,actual))throw new IllegalStateException("exact ten-component gas topology mismatch");

            var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
             for(Set<BlockPos> room:List.of(layout.fueled().roomInterior(),layout.roomA(),layout.roomB()))
                for(BlockPos cell:room)if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())
                    throw new IllegalStateException("owned finite room endpoint missing at "+cell);

            Map<PipeTopology.NodeKey,UUID> gasOwners=TegFixturePlacement.gasOwners(captured);
            List<AtmosMachineBlockEntity> allMachines=new ArrayList<>(hardware.baseMachines());
            allMachines.addAll(hardware.additional().atmosMachines());
            var secondApc=(PowerDeviceBlockEntity)level.getBlockEntity(secondaryApc);
            var mainApc=(PowerDeviceBlockEntity)level.getBlockEntity(layout.fueled().electrical().devices().outputApc());
            Map<BlockPos,UUID> machineOwners=TegFixturePlacement.continuousMachineOwners(allMachines,hardware.core(),
                    hardware.additional().miners(),hardware.additional().ventilation(),List.of(mainApc,secondApc));
            int expectedOwners=layout.fueled().machines().size()+layout.devices().size()+3;
            if(machineOwners.size()!=expectedOwners)throw new IllegalStateException("expected "+expectedOwners+" unique machine owners, got "+machineOwners.size());
            UUID north=inletIdentity(hardware.baseMachines(),layout.fueled(),TegFueledFixturePlan.MachineKind.CIRCULATOR,true);
            UUID south=inletIdentity(hardware.baseMachines(),layout.fueled(),TegFueledFixturePlan.MachineKind.CIRCULATOR,false);
            UUID coreIdentity=hardware.core().apcPowerIdentity();
            if(coreIdentity==null||north==null||south==null)throw new IllegalStateException("core/circulator identity unavailable");
            return new ObservedHardware(coreIdentity,north,south,gasOwners,machineOwners,layout,hardware,topology,captured);
    }

    static ObservedHardware observeHardware(ServerLevel level,TegContinuousFuelPlan.Layout layout) {
        List<AtmosMachineBlockEntity> base=new ArrayList<>();
        for(var planned:layout.fueled().machines()) {
            requireLoaded(level,planned.position());
            if(!level.getBlockState(planned.position()).is(TegFixturePlacement.baseBlock(planned.kind())))throw new IllegalStateException("base machine kind mismatch at "+planned.position());
            if(!(level.getBlockEntity(planned.position()) instanceof AtmosMachineBlockEntity machine))
                throw new IllegalStateException("base machine missing at "+planned.position());
            if(!machine.settingsValid()||machine.resolvedProfile()==null||!level.getBlockState(planned.position()).hasProperty(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)||level.getBlockState(planned.position()).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)!=planned.facing())throw new IllegalStateException("base machine profile/settings/facing invalid at "+planned.position());
            var expectedProfileKind=switch(planned.kind()) {
                case CIRCULATOR -> AtmosMachineProfile.Kind.TEG_CIRCULATOR;
                case PUMP -> AtmosMachineProfile.Kind.VOLUME_PUMP;
                case PRESSURE_PUMP -> AtmosMachineProfile.Kind.PRESSURE_PUMP;
                case EXCHANGER -> AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER;
                case HEATER -> AtmosMachineProfile.Kind.HEATER;
            };
            if(machine.resolvedProfile().kind()!=expectedProfileKind)throw new IllegalStateException("base resolved profile kind mismatch at "+planned.position()+": expected="+expectedProfileKind+" actual="+machine.resolvedProfile().kind());
            base.add(machine);
        }
        requireLoaded(level,layout.core());
        if(!level.getBlockState(layout.core()).is(com.juicyslew.moonstation14.block.ModBlocks.TEG_CENTER.get())
                || !level.getBlockState(layout.core()).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING).equals(Direction.NORTH)
                || !(level.getBlockEntity(layout.core()) instanceof AtmosMachineBlockEntity coreFound))throw new IllegalStateException("TEG core missing or wrong kind/facing");
        AtmosMachineBlockEntity core=coreFound;
        if(!core.settingsValid()||core.resolvedProfile()==null)throw new IllegalStateException("TEG core profile/settings invalid");
        List<AtmosMachineBlockEntity> atmos=new ArrayList<>(); List<com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity> miners=new ArrayList<>();
        List<com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity> vents=new ArrayList<>();
        for(var d:layout.devices()) {
            requireLoaded(level,d.position()); var state=level.getBlockState(d.position()); var be=level.getBlockEntity(d.position());
            if(!state.is(TegFixturePlacement.continuousBlock(d.role())))throw new IllegalStateException("device kind mismatch at "+d.position());
             switch(d.role()) {
                 case MIXER, PASSIVE_HEAT_EXCHANGER, HOT_HEAT_EXCHANGER, COLD_PRESSURE_PUMP -> {
                     if(!(be instanceof AtmosMachineBlockEntity a)||!state.hasProperty(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)||state.getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)!=d.facing())throw new IllegalStateException("atmos device missing or facing mismatch at "+d.position());
                     if(!a.settingsValid()||a.resolvedProfile()==null)throw new IllegalStateException("atmos device invalid at "+d.position());
                      var expectedKind=switch(d.role()){
                          case MIXER->AtmosMachineProfile.Kind.MIXER;
                          case COLD_PRESSURE_PUMP->AtmosMachineProfile.Kind.PRESSURE_PUMP;
                          default->AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER;
                      };
                     if(a.resolvedProfile().kind()!=expectedKind)throw new IllegalStateException("resolved continuous device kind mismatch at "+d.position());
                     if(d.role()==TegContinuousFuelPlan.DeviceRole.PASSIVE_HEAT_EXCHANGER&&!layout.radiatorPositions().contains(d.position()))throw new IllegalStateException("outdoor radiator role is not the unique exterior host");
                     if(d.role()==TegContinuousFuelPlan.DeviceRole.HOT_HEAT_EXCHANGER&&!layout.hotExchangers().subList(1,3).contains(d.position()))throw new IllegalStateException("hot exchanger role is outside declared inner contacts");
                     atmos.add(a);
                 }
                case GAS_MINER_OXYGEN, GAS_MINER_PLASMA -> { if(!(be instanceof GasMinerBlockEntity m)||m.resolvedProfile()==null)throw new IllegalStateException("miner missing or unresolved at "+d.position()); miners.add(m); }
                 default -> { if(!(be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v)||!state.hasProperty(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock.FACING)||state.getValue(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock.FACING)!=d.facing())throw new IllegalStateException("ventilation device missing or facing mismatch at "+d.position()); if(v.prototype()==null||!v.settingsValid())throw new IllegalStateException("ventilation device invalid at "+d.position()); if(d.role()==TegContinuousFuelPlan.DeviceRole.VENT&&v.config().settings().selectedLayer()!=com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Layer.PRIMARY)throw new IllegalStateException("exterior waste vent is not configured on primary lane at "+d.position()); vents.add(v); }
            }
        }
        long expectedAtmos=layout.devices().stream().filter(d->d.role().isAtmosMachine()).count();
        if(atmos.size()!=expectedAtmos)throw new IllegalStateException("resolved layout atmosphere host count mismatch: expected "+expectedAtmos+" got "+atmos.size());
        var hotProfile=atmos.stream().filter(m->layout.hotExchangers().subList(1,3).contains(m.getBlockPos())).map(AtmosMachineBlockEntity::resolvedProfile).filter(Objects::nonNull).findFirst().orElseThrow(()->new IllegalStateException("resolved hot exchanger profile unavailable"));
        layout.validateHotExchanger(hotProfile);
        return observeHardware(level,layout,new TegFixturePlacement.ContinuousHardware(base,core,new TegFixturePlacement.ContinuousDevices(atmos,miners,vents)));
    }

    static ObservedHardware observeOwnedHardware(ServerLevel level,TegFixtureSavedData.Entry entry) {
        var catalog=ModAtmosMachines.catalog(level);
        var mixer=ModBlocks.GAS_MIXER.get(); var radiator=ModBlocks.PASSIVE_HEAT_EXCHANGER.get();
        var mp=catalog.get(ModAtmosMachines.id(mixer.profileIdentity())); var rp=catalog.get(ModAtmosMachines.id(radiator.profileIdentity()));
        if(mp==null||rp==null)throw new IllegalStateException("resolved mixer/radiator profiles unavailable");
        var layout=TegContinuousFuelPlan.layout(entry.origin(),mp,rp);
        var observed=observeHardware(level,layout);
        if(entry.mode()!=TegFixtureSavedData.Mode.CONTINUOUS||!Objects.equals(entry.core(),observed.coreIdentity())||!Objects.equals(entry.north(),observed.north())||!Objects.equals(entry.south(),observed.south())||!entry.ownedGas().equals(observed.gasOwners())||!entry.ownedMachines().equals(observed.machineOwners()))
            throw new IllegalStateException("saved continuous ownership does not match live hardware");
        return observed;
    }

    private static void requireLoaded(ServerLevel level,BlockPos pos) {
        if(level.getServer()==null||!level.getServer().isSameThread())throw new IllegalStateException("hardware observation requires server thread");
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL))throw new IllegalStateException("required observation chunk is not FULL-loaded at "+pos);
    }

    record ObservedHardware(UUID coreIdentity,UUID north,UUID south,Map<PipeTopology.NodeKey,UUID> gasOwners,
                            Map<BlockPos,UUID> machineOwners,TegContinuousFuelPlan.Layout layout,
                            TegFixturePlacement.ContinuousHardware placement,TegFueledFixtureTopology.Plan topology,
                            LoadedPipeGasWork.Snapshot capture) {
        ObservedHardware { gasOwners=Map.copyOf(gasOwners); machineOwners=Map.copyOf(machineOwners); }
    }

    static Optional<LoadedPipeGasWork.Component> capturedComponentForPort(
            TegFueledFixtureTopology.Plan topology, LoadedPipeGasWork.Snapshot capture, PipeTopology.NodeKey port) {
        int match=-1;
        for(int i=0;i<topology.components().size();i++) if(topology.components().get(i).contains(port)) {
            if(match!=-1) throw new IllegalStateException("port belongs to multiple expected gas components: "+port);
            match=i;
        }
        if(match==-1) throw new IllegalStateException("port is not in expected gas topology: "+port);
        var representative=topology.representatives().get(match);
        if(!capture.portComponents().containsKey(representative))
            throw new IllegalStateException("expected representative missing from actual gas capture: "+representative);
        return capture.componentFor(representative);
    }

    record BoundAtmosphere(AtmosMachineBlockEntity blockEntity, TegContinuousOperatingPlan.Plan plan) { }
    record BoundVentilation(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity blockEntity,
                            TegContinuousOperatingPlan.VentilationProposal proposal,
                            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary boundary) { }
    record OperatingSettings(List<BoundAtmosphere> atmosphere, List<BoundVentilation> ventilation) {
        OperatingSettings { atmosphere=List.copyOf(atmosphere); ventilation=List.copyOf(ventilation); }
    }

    /** Read-only binding of pure operating proposals to already observed live devices. */
    static OperatingSettings bindOperatingSettings(ServerLevel level, ObservedHardware observed,TegContinuousOperatingPlan.Stage stage) {
        Objects.requireNonNull(level); Objects.requireNonNull(observed);
        var hardware=observed.placement(); var layout=observed.layout();
        List<BoundAtmosphere> atmosphere=new ArrayList<>();
        bindAtmosphere(atmosphere,hardware.core(),TegContinuousOperatingPlan.Role.CORE,stage);
        for(var machine:hardware.baseMachines()) {
            var profile=Objects.requireNonNull(machine.resolvedProfile(),"resolved base profile required");
            var role=switch(profile.kind()) {
                case VOLUME_PUMP, PRESSURE_PUMP -> TegContinuousOperatingPlan.pumpRole(layout.fueled(),machine.getBlockPos());
                case HEATER -> TegContinuousOperatingPlan.Role.IGNITION_HEATER;
                case TEG_CIRCULATOR, PASSIVE_HEAT_EXCHANGER -> TegContinuousOperatingPlan.Role.PASSIVE;
                default -> throw new IllegalStateException("unsupported base atmosphere profile kind: "+profile.kind());
            };
            bindAtmosphere(atmosphere,machine,role,stage);
        }
        for(var machine:hardware.additional().atmosMachines()) {
            var kind=Objects.requireNonNull(machine.resolvedProfile(),"resolved additional profile required").kind();
             var role=switch(kind) {
                 case MIXER -> TegContinuousOperatingPlan.Role.MIXER;
                 case PASSIVE_HEAT_EXCHANGER -> TegContinuousOperatingPlan.Role.PASSIVE;
                 case PRESSURE_PUMP -> {
                     if(!machine.getBlockPos().equals(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP)))
                         throw new IllegalStateException("pressure pump is not the canonical cold-return pump position");
                     yield TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP;
                 }
                 default -> throw new IllegalStateException("unsupported additional atmosphere profile kind: "+kind);
             };
            bindAtmosphere(atmosphere,machine,role,stage);
        }
         long expectedAtmosphere=1L+layout.fueled().machines().size()+layout.devices().stream().filter(d->d.role().isAtmosMachine()).count();
        if(atmosphere.size()!=expectedAtmosphere) throw new IllegalStateException("expected "+expectedAtmosphere+" bound atmosphere hosts");

        var byPosition=new HashMap<BlockPos,com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity>();
        for(var vent:hardware.additional().ventilation()) if(byPosition.put(vent.getBlockPos(),vent)!=null)
            throw new IllegalStateException("duplicate observed ventilation host");
        List<BoundVentilation> ventilation=new ArrayList<>();
        for(var device:layout.devices()) {
            TegContinuousOperatingPlan.VentilationRole role;
            switch(device.role()) {
                case SCRUBBER -> {
                    boolean a=layout.roomA().contains(device.position()), b=layout.roomB().contains(device.position());
                    if(a==b) throw new IllegalStateException("scrubber must belong to exactly one source room");
                    role=a?TegContinuousOperatingPlan.VentilationRole.SOURCE_OXYGEN:TegContinuousOperatingPlan.VentilationRole.SOURCE_PLASMA;
                }
                case INJECTOR -> role=TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY;
                case WASTE_PUMP -> role=TegContinuousOperatingPlan.VentilationRole.BURNER_WASTE;
                case VENT -> role=TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE;
                default -> { continue; }
            }
            var vent=byPosition.remove(device.position());
            if(vent==null||vent.prototype()==null||!vent.settingsValid()) throw new IllegalStateException("expected valid ventilation host missing at "+device.position());
            var boundary=(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary)null;
            double ambient=0;
            if(role==TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE) {
                var front=layout.wasteVentBody().relative(layout.wasteVentFacing());
                boundary=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,front).orElseThrow(()->new IllegalStateException("real exterior boundary unavailable at "+front));
                if(!boundary.validate()) throw new IllegalStateException("exterior boundary changed during settings binding");
                 ambient=TegContinuousOperatingPlan.exteriorAmbientPressure(boundary.kind(),boundary.ambient().pressureKpa());
            }
            ventilation.add(new BoundVentilation(vent,TegContinuousOperatingPlan.proposeVentilation(role,vent.prototype(),vent.config().settings(),ambient,stage),boundary));
        }
        if(ventilation.size()!=5||!byPosition.isEmpty()) throw new IllegalStateException("expected exactly five bound ventilation hosts");
        return new OperatingSettings(atmosphere,ventilation);
    }

    /** Trusted one-shot startup orchestration; resources are delegated only after controls apply. */
    static String initializeOperating(ServerLevel level, BlockPos origin) {
        return initializeOperating(level,origin,TegContinuousOperatingPlan.Stage.STARTUP);
    }
    static String initializeOperating(ServerLevel level, BlockPos origin, TegContinuousOperatingPlan.Stage initialStage) {
        var saved=TegFixtureSavedData.existing(level).orElse(null);
        var initial=saved==null?Optional.<TegFixtureSavedData.Entry>empty():saved.entry(origin);
        if(initial.isEmpty())return "Operating startup refused: no saved fixture at that origin.";
        var entry=initial.get();
        if(entry.mode()!=TegFixtureSavedData.Mode.CONTINUOUS)return "Operating startup refused: fixture is not continuous.";
        if(entry.initialized())return "Operating startup refused: one-shot resource token was already consumed; no refill or reset is permitted.";
        final ObservedHardware observed;
        try {
            observed=observeOwnedHardware(level,entry);
            PowerAdmission admission=startupPowerAdmission(level,observed);
            if(admission.status()!=PowerAdmissionStatus.READY) {
                if(admission.status()==PowerAdmissionStatus.WAIT)
                    return TegAutomaticStartupPolicy.POWER_TOPOLOGY_WAIT_PREFIX+" "+admission.reason()+".";
                return "Operating startup refused before controls: "+admission.reason()+".";
            }
        } catch(RuntimeException failure) {
            return "Operating startup refused before controls: "+failure.getMessage()+".";
        }

        try {
            if(TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).filter(entry::equals).isEmpty())
                throw new IllegalStateException("saved fixture token or ownership changed");
            applyOperatingControls(level,observed,initialStage);
        } catch(RuntimeException failure) {
            return "Operating controls not confirmed; settings may be partially configured; resources were not requested; manual recovery required: "+failure.getMessage()+".";
        }
        String resources=initializeResources(level,origin);
        if(resources.startsWith("Startup resources admitted once:"))
            return "Operating startup admitted once: validated controls and startup resources admitted; paid ignition requested, not completed.";
        if(resources.startsWith(RESOURCE_POWER_WAIT_PREFIX))
            return "Operating startup waiting for power topology: controls are configured, the resource token is still unused, and no resources were written; retry start after normal power readiness. "+resources;
        return "Operating controls configured; "+resources;
    }

    static String startHeatingAfterColdPreparation(ServerLevel level,BlockPos origin) {
        var entry=continuousEntry(level,origin);
        if(entry==null||!entry.initialized()||entry.phase()!=TegFixtureSavedData.ContinuousPhase.STARTUP) return "Operating heating refused: initialized STARTUP fixture required.";
        try {
             var observed=observeOwnedHardware(level,entry);
            PowerAdmission admission=startupPowerAdmission(level,observed);
            if(admission.status()!=PowerAdmissionStatus.READY)return admission.status()==PowerAdmissionStatus.WAIT
                    ?TegAutomaticStartupPolicy.POWER_TOPOLOGY_WAIT_PREFIX+" "+admission.reason()+"."
                    :"Operating heating refused before controls: "+admission.reason()+".";
            if(TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).filter(entry::equals).isEmpty()) throw new IllegalStateException("saved fixture ownership changed");
            applyOperatingControls(level,observed,TegContinuousOperatingPlan.Stage.STARTUP);
            return "Operating heating controls confirmed: cold preparation complete; original startup clock retained.";
        } catch(RuntimeException failure) { return "Operating heating controls not confirmed; partial control failure requires recovery: "+failure.getMessage()+"."; }
    }

    /** Public fixture operator entry point; construction itself never seeds resources. */
    /** Internal manual initialization helper retained for focused native fixtures. */
    static String initializeForTest(ServerLevel level,BlockPos origin) {
        return initializeOperating(level,origin);
    }

    /** Legacy native-fixture helper: manual initialization, not the public automatic command path. */
    static String start(ServerLevel level,BlockPos origin) { return initializeForTest(level,origin); }

    static String startAutomatic(ServerLevel level, BlockPos origin, UUID initiator) {
        var data=TegFixtureSavedData.forMutation(level);
        var entry=data.entry(origin).orElse(null);
        if(entry==null)return "Automatic startup refused: no saved continuous fixture at this origin.";
        var refusal=data.armAutomaticStartup(entry,initiator,level.getGameTime()).orElse(null);
        return refusal==null?"Automatic startup armed once; it will preheat, ignite, qualify RUNNING, then disable the startup heater. Progress appears automatically; no ignite/run/heater-off commands are needed.":"Automatic startup refused: "+refusal+".";
    }

    static String automaticProgress(ServerLevel level,BlockPos origin) {
        var entry=continuousEntry(level,origin);
        if(entry==null)return "automatic fixture unavailable";
        var observed=observeOwnedHardware(level,entry);
        var carrier=carrierLoop(observed);
        var heater=heater(observed);
        long elapsed=entry.phase()==TegFixtureSavedData.ContinuousPhase.STARTUP?Math.max(0,level.getGameTime()-entry.phaseStartedTick()):Math.max(0,level.getGameTime()-entry.phaseStartedTick());
        long budget=entry.phase()==TegFixtureSavedData.ContinuousPhase.STARTUP?TegAutomaticStartupPolicy.PREHEAT_BUDGET_TICKS:TegAutomaticStartupPolicy.PHASE_BUDGET_TICKS;
        long elapsedSeconds=elapsed/20, budgetSeconds=budget/20;
        if(entry.phase()==TegFixtureSavedData.ContinuousPhase.STARTUP)
            return String.format(java.util.Locale.ROOT,"Heating %.0f/590 K",carrier.meanTemperatureKelvin());
        return String.format(java.util.Locale.ROOT,"%s %ds",entry.phase(),Math.max(0,budgetSeconds-elapsedSeconds));
    }
    static double automaticCarrierTemperature(ServerLevel level,ObservedHardware observed) {
        return carrierLoop(observed).meanTemperatureKelvin();
    }

    record ShutdownResult(int stopped, List<String> issues) {
        ShutdownResult { issues=List.copyOf(issues); }
        boolean complete() { return issues.isEmpty(); }
        String summary() { return String.join("; ",issues); }
    }
    static ShutdownResult quiesceOwned(ServerLevel level, TegFixtureSavedData.Entry entry) {
        return quiesceMatchingLoadedOwners(level,entry);
    }
    static ShutdownResult quiesceMatchingLoadedOwners(ServerLevel level,TegFixtureSavedData.Entry entry) {
        int stopped=0; List<String> issues=new ArrayList<>();
        for(var owner:entry.ownedMachines().entrySet()) {
            BlockPos pos=owner.getKey();
            try {
                var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
                if(chunk==null||!chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)) { issues.add("unloaded "+pos); continue; }
                var be=level.getBlockEntity(pos); if(be instanceof AtmosMachineBlockEntity machine && owner.getValue().equals(machine.placementIdentity())) {
                    if(needsDisable(machine.settingsSnapshot().enabled())) { if(!disableAtmosphere(machine)) issues.add("rejected "+pos); else stopped++; }
                } else if(be instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity vent && owner.getValue().equals(vent.deviceId())) {
                    if(vent.config().settings().configuredEnabled()) { if(!disableVentilation(vent)) issues.add("rejected "+pos); else stopped++; }
                } else if(be instanceof GasMinerBlockEntity miner
                        && matchingPassiveMiner(owner.getValue(),miner.placementIdentity(),miner.hasLivePlacementIdentity())) {
                    // Continuous fixture miners are deliberately passive/unpowered. Identity still
                    // matters: a replaced miner must not be silently accepted as owned hardware.
                } else if(be instanceof PowerDeviceBlockEntity apc&&owner.getValue().equals(apc.placementIdentity())
                        &&level.getBlockState(pos).is(ModBlocks.APC.get())) {
                    // Dedicated secondary APC is an owned battery source, not a control target.
                } else { issues.add("owner mismatch "+pos); continue; }
            } catch(RuntimeException failure) { issues.add("exception "+pos+" "+failure.getClass().getSimpleName()); }
        }
        return new ShutdownResult(stopped,issues);
    }
    static boolean matchingPassiveMiner(UUID savedIdentity,UUID liveIdentity,boolean live) {
        return live&&savedIdentity!=null&&savedIdentity.equals(liveIdentity);
    }
    static void quiesceObserved(ObservedHardware observed) {
        int stopped=0; List<String> issues=new ArrayList<>();
        var machines=new ArrayList<AtmosMachineBlockEntity>(); machines.add(observed.placement().core());machines.addAll(observed.placement().baseMachines());machines.addAll(observed.placement().additional().atmosMachines());
        for(var machine:machines) try { if(needsDisable(machine.settingsSnapshot().enabled())) { if(disableAtmosphere(machine)) stopped++; else issues.add("rejected "+machine.getBlockPos()); } } catch(RuntimeException e) { issues.add("exception "+machine.getBlockPos()+" "+e.getClass().getSimpleName()); }
        for(var vent:observed.placement().additional().ventilation()) try { if(vent.config().settings().configuredEnabled()) { if(disableVentilation(vent)) stopped++; else issues.add("rejected "+vent.getBlockPos()); }} catch(RuntimeException e) {issues.add("exception "+vent.getBlockPos()+" "+e.getClass().getSimpleName());}
        var result=new ShutdownResult(stopped,issues); if(!result.complete()) throw new IllegalStateException(result.summary());
    }
    static boolean needsDisable(Optional<Boolean> enabled) { return enabled.orElse(false); }
    private static boolean disableAtmosphere(AtmosMachineBlockEntity machine) {
        var settings=machine.settingsSnapshot();
        return settings.enabled().isEmpty() || !settings.enabled().get() || machine.applySettings(settings.revision(),com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Operation.SET_ENABLED,new com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.BooleanValue(false));
    }
    private static boolean disableVentilation(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity vent) {
        var s=vent.config().settings();
        return !s.configuredEnabled() || vent.compareAndSetSettings(s.revision(),new com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Settings(s.revision()+1,false,s.externalPressureBoundKPa(),s.targetPressureChangeKPaPerSecond(),s.targetPressureKPa(),s.volumeRateM3PerSecond(),s.scrubberMode(),s.filteredGases(),s.selectedLayer()));
    }

    /** Opens ordinary burner supply only after paid heater work warmed its owned carrier-loop gas. */
    static String ignite(ServerLevel level,BlockPos origin) {
        var entry=continuousEntry(level,origin);
        if(entry==null)return "Ignition refused: no continuous fixture at this origin.";
        if(entry.phase()!=TegFixtureSavedData.ContinuousPhase.STARTUP)return "Ignition refused: expected STARTUP; current phase="+entry.phase()+".";
        try {
            long cutoffPowerFloor=PowerRuntime.currentSampleTick(level);
            var observed=observeOwnedHardware(level,entry);
            String readiness=ignitionReadinessFailure(level,observed);
            if(readiness!=null)return "Ignition not ready: "+readiness;
            applyOperatingControls(level,observed,TegContinuousOperatingPlan.Stage.IGNITION);
            double baseline=co2Total(level,observed);
            if(!TegFixtureSavedData.forMutation(level).transition(entry,TegFixtureSavedData.ContinuousPhase.STARTUP,
                    TegFixtureSavedData.ContinuousPhase.IGNITION,level.getGameTime(),baseline))
                return "Ignition fuel admitted but phase persistence failed; manual recovery required; do not repeat ignition.";
            return "Ignition admitted: ordinary " + String.format(java.util.Locale.ROOT, "%.2f", TegContinuousOperatingPlan.IGNITION_BURNER_SUPPLY_TARGET_KPA) + " kPa supply opened at paid-feed readiness. Allow material reaction; within 600 ticks run `ms14 teg_fixture run`.";
        } catch(RuntimeException failure) { return "Ignition controls may be partially applied; fuel admission and phase transition were not confirmed; inspect status and use manual recovery if changed: "+failure.getMessage()+"."; }
    }

    /** Operator transition from startup recipe to normal circulation, retaining the original ignition deadline. */
    static String run(ServerLevel level,BlockPos origin) {
        var entry=continuousEntry(level,origin);
        if(entry==null)return "RUNNING refused: no continuous fixture at this origin.";
        if(entry.phase()!=TegFixtureSavedData.ContinuousPhase.IGNITION)return "RUNNING refused: expected IGNITION; current phase="+entry.phase()+".";
        if(level.getGameTime()-entry.phaseStartedTick()>=600)return "RUNNING refused: startup qualified at the original deadline with no time remaining for RUNNING qualification; no budget extension or restart.";
        try {
            var observed=observeOwnedHardware(level,entry);
            double co2=co2Total(level,observed),mean=burnerMeanKelvin(level,observed);
            if(co2-entry.phaseCo2Baseline()<.01||mean<550)
                return String.format(java.util.Locale.ROOT,"RUNNING not ready: actual CO2 gain %.2f mol (need .01) and burner mean %.2f K (need 550 K); ignition deadline remains %.2f s.",co2-entry.phaseCo2Baseline(),mean,Math.max(0,600-(level.getGameTime()-entry.phaseStartedTick()))/20d);
            applyOperatingControls(level,observed,TegContinuousOperatingPlan.Stage.RUNNING);
            double runningBaseline=co2Total(level,observeOwnedHardware(level,entry));
            long runningPowerFloor=PowerRuntime.currentSampleTick(level);
            var current=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(!TegFixtureSavedData.forMutation(level).transition(current,TegFixtureSavedData.ContinuousPhase.IGNITION,
                    TegFixtureSavedData.ContinuousPhase.RUNNING,entry.phaseStartedTick(),runningBaseline,runningPowerFloor))
                return "RUNNING controls applied but phase persistence failed; manual recovery required; do not repeat.";
            return "RUNNING controls selected with ordinary settings. Within the original ignition deadline wait for fresh material reaction, >=550 K mean temperature, and accepted APC input covering post-heater demand; then use `ms14 teg_fixture heateroff`.";
        } catch(RuntimeException failure) { return "RUNNING refused or partially applied: "+failure.getMessage()+"; inspect status; do not retry if settings changed."; }
    }

    /** Heater cutoff is guarded by live material qualification and matched accepted power, never by an offer. */
    static String heaterOff(ServerLevel level,BlockPos origin) {
        var entry=continuousEntry(level,origin);
        if(entry==null)return "Heater-off refused: no continuous fixture at this origin.";
        if(entry.phase()!=TegFixtureSavedData.ContinuousPhase.RUNNING)return "Heater-off refused: expected RUNNING; current phase="+entry.phase()+".";
        if(level.getGameTime()-entry.phaseStartedTick()>=600)return "Heater-off refused: original 600-tick ignition qualification expired; no budget extension.";
        try {
            long cutoffPowerFloor=PowerRuntime.currentSampleTick(level);
            var observed=observeOwnedHardware(level,entry);
            double co2=co2Total(level,observed),mean=burnerMeanKelvin(level,observed);
            if(co2-entry.phaseCo2Baseline()<.01||mean<550)return String.format(java.util.Locale.ROOT,"Heater-off not ready: actual running CO2 gain %.2f mol and burner mean %.2f K; wait within original deadline.",co2-entry.phaseCo2Baseline(),mean);
            var startupHeaters=heaters(observed);
            if(startupHeaters.stream().anyMatch(h->h.committedPaidJoules()<=0||h.committedThermalJoules()<=0))
                return "Heater-off not ready: both startup heaters must have positive paid work and positive committed heat receipts; status lists each heater receipt.";
            var frame=matchedPowerFrame(level,observed,entry.phasePowerSampleFloor(),true);
            if(!frame.generationReady())return "Heater-off not ready: accepted operating-APC input does not yet cover the resolved post-heater load on a fresh matched HV/APC sample; status reports the current frame.";
            // The guarded generation check above deliberately observes the unthrottled RUNNING controls.
            // Only after that fresh frame is accepted do we apply the post-cutoff fixture control plan.
            applyOperatingControls(level,observed,TegContinuousOperatingPlan.Stage.HEATER_OFF);
            var current=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            if(!TegFixtureSavedData.forMutation(level).transition(current,TegFixtureSavedData.ContinuousPhase.RUNNING,
                    TegFixtureSavedData.ContinuousPhase.HEATER_OFF,level.getGameTime(),co2,cutoffPowerFloor))
                return "Heater disabled but phase persistence failed; manual recovery required.";
            return "Startup heater disabled by guarded operator action. Verify fresh zero allocation, then observe the full 1,200-tick matched-load/no-storage-drawdown interval; status distinguishes verified SPACE radiation from signed PLANETARY_AMBIENT heat receipt.";
        } catch(RuntimeException failure) { return "Heater-off refused or partially applied: "+failure.getMessage()+"; inspect status and use manual recovery if settings changed."; }
    }

    /** Fresh ordinary power-frame acknowledgement after the heater cutoff; shared by automation and native proof. */
    static boolean heaterOffAcknowledged(ServerLevel level, TegFixtureSavedData.Entry entry) {
        if(entry.phase()!=TegFixtureSavedData.ContinuousPhase.HEATER_OFF)return false;
        try {
             var observed=observeOwnedHardware(level,entry);var heaterRoster=heaters(observed);var core=observed.placement().core();
            long floor=entry.phasePowerSampleFloor();
            var output=PowerRuntime.apcOutputObservation(level,(PowerDeviceBlockEntity)level.getBlockEntity(observed.layout().fueled().electrical().devices().outputApc()));
            var meter=PowerRuntime.apcPowerMeter(level,core);
            boolean paid=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level)
                    &&core.hvAllocationKnown()&&meter.known()&&meter.deliveredWatts()>0&&output.known()
                    &&output.topologyGeneration()==com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level)
                    &&matchedPowerFrame(level,observed,floor,false).fullLoadMatchedFrame();
            // This caller validates output topology equality above; the ACK helper owns only cutoff-clock freshness.
             return heaterRoster.stream().allMatch(heater->heater.apcAllocatedWatts()==0&&!heater.settingsSnapshot().enabled().orElse(true)
                     &&heaterOffAckFrameReady(true,heater.apcSampleTick(),core.hvSampleTick(),output.sampleTick(),floor,paid));
        } catch(RuntimeException ignored) { return false; }
    }

    static String operatingStatus(ServerLevel level,BlockPos origin) {
        var entry=continuousEntry(level,origin);
        if(entry==null)return "NOT READY: no continuous fixture at this origin.";
        var automatic=TegFixtureSavedData.existing(level).flatMap(d->d.automaticStartup(origin)).orElse(null);
        if(automatic!=null&&automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.FAILED)
            return "AUTOMATIC FAILED reason="+automatic.failureReason()+" phase="+entry.phase()+" initialized="+entry.initialized()+"; owned shutdown was attempted; inspect loaded hardware. ";
        try {
             var observed=observeOwnedHardware(level,entry);var bootstrap=startupBootstrapSplit(observed);
             var feed=fuelFeed(observed);var carrier=carrierLoop(observed);var heaterRoster=heaters(observed);var frame=matchedPowerFrame(level,observed,entry.phasePowerSampleFloor(),entry.phase()!=TegFixtureSavedData.ContinuousPhase.HEATER_OFF);
             double heaterPaidJ=heaterRoster.stream().mapToDouble(AtmosMachineBlockEntity::committedPaidJoules).sum();
             double heaterThermalJ=heaterRoster.stream().mapToDouble(AtmosMachineBlockEntity::committedThermalJoules).sum();
             String heaterReceipts=heaterRoster.stream().map(m->m.getBlockPos()+"[paidJ="+fmt(m.committedPaidJoules())+",thermalJ="+fmt(m.committedThermalJoules())+"]").collect(java.util.stream.Collectors.joining(";"));
             boolean heatersEnabled=heaterRoster.stream().allMatch(m->m.settingsSnapshot().enabled().orElse(false));
            var radiators=observed.placement().additional().atmosMachines().stream().filter(m->observed.layout().radiatorPositions().contains(m.getBlockPos())).toList();
            if(radiators.size()!=observed.layout().radiatorPositions().size())throw new IllegalStateException("owned radiator receipt set does not match resolved layout");
            List<String> boundaryKinds=new ArrayList<>();
            double radiatorAmbientReceipt=0,radiatorSpaceReceipt=0;
            for(var radiator:radiators){
                var boundary=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,radiator.getBlockPos()).orElse(null);
                String kind=boundary==null?null:boundary.kind().name();
                boundaryKinds.add(kind);
                radiatorAmbientReceipt+=radiator.ambientHeatReceiptJoules();radiatorSpaceReceipt+=radiator.spaceHeatReceiptJoules();
            }
             String boundaryKind=classifyRadiatorBoundaries(boundaryKinds);
             var output=(PowerDeviceBlockEntity)level.getBlockEntity(observed.layout().fueled().electrical().devices().outputApc());
             var secondaryApc=(PowerDeviceBlockEntity)level.getBlockEntity(TegContinuousPowerPlan.equipment(observed.layout()).secondaryStartupApc());
             var mainApcFrame=PowerRuntime.apcOutputObservation(level,output);
             var secondaryApcFrame=PowerRuntime.apcOutputObservation(level,secondaryApc);
             var tegState=observed.placement().core().tegRuntimeState();
             long currentSample=PowerRuntime.currentSampleTick(level);
             long preheatElapsed=automatic==null?-1:Math.max(0,level.getGameTime()-automatic.armTick());
             long offerAge=observed.placement().core().tegLastOfferSampleTick()>=0
                     ?currentSample-observed.placement().core().tegLastOfferSampleTick():-1;
             long supplyAge=observed.placement().core().hvSampleTick()>=0
                     ?currentSample-observed.placement().core().hvSampleTick():-1;
             double mean=burnerMeanKelvin(level,observed),co2=co2Total(level,observed),gain=co2-entry.phaseCo2Baseline();
             var burnerVisual=burnerDiagnostic(level,observed);
            boolean materialReady=gain>=.01&&mean>=550;
            String proof=entry.phase()==TegFixtureSavedData.ContinuousPhase.HEATER_OFF?"UNVERIFIED_1200_MATCHED_WINDOW":"NOT_STARTED";
            var coldPump=carrierPump(observed,TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP);
            boolean coldFlow=coldPump.settingsSnapshot().enabled().orElse(false);
            String next=nextAction(level,entry,observed,feed,mean,co2,frame);
            if(automatic!=null&&automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.COMPLETE)next="automatic startup finished; self-power proof remains unverified";
            else if(automatic!=null&&automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.ACTIVE)next="automatic startup active";
            else if(automatic!=null&&automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.WAITING_START)next="automatic startup waiting for power topology/preheat";
              return String.format(java.util.Locale.ROOT,"%sCONTINUOUS phase=%s initialized=%s bothHeatersEnabled=%s coldCarrierPumpFlow=%s(OFF STARTUP; ON IGNITION/RUNNING/HEATER_OFF) carrierMeanK=%.2f heaterPortK=%.2f carrierN2=%.2f startupHeatersPaidJ=%.2f startupHeatersThermalJ=%.2f heaterReceipts=%s fuelLineK=%.2f O2=%.2f plasma=%.2f burnerMeanK=%.2f CO2=%.2f burnerO2mol=%.2f burnerPlasmamol=%.2f burnerCO2mol=%.2f reactionEligibleCells=%d fireActiveCells=%d fireMaxIntensity=%d plasmaVisibleCells=%d/plasmaThreshold%.2fmolPerCell phaseCO2Gain=%.2f material=%s startupMainAPCSeedJ=%.2f startupAuxAPCSeedJ=%.2f bootstrapMainFullLoadZeroGenSec=%.2f bootstrapAuxFullLoadZeroGenSec=%.2f jointBootstrapZeroGenSec=%.2f mainCurrentStoreZeroGenSec=%.2f auxCurrentStoreZeroGenSec=%.2f jointCurrentStoreZeroGenSec=%.2f generatedW=%.2f policyOfferCeilingW=%.2f lastAllocatorOfferW=%.2f offerSampleAgeTicks=%d suppliedHVW=%.2f supplySampleAgeTicks=%d acceptedAPCInputW=%.2f postHeaterDemandW=%.2f mainAPCOutputW=%.2f mainAPCStorageDeltaJ=%.2f mainAPCStorageJ=%.2f secondHeaterAPCInputW=%.2f secondHeaterAPCOutputW=%.2f secondHeaterAPCStorageDeltaJ=%.2f secondHeaterAPCStorageJ=%.2f generationReady=%s freshPowerFloor=%d matchedSample=%d committedTegJ=%.2f radiatorBoundary=%s radiatorAmbientReceiptCumulativeJ=%.2f radiatorSpaceHeatReceiptCumulativeJ=%.2f proof=%s preheatElapsedTicks=%d phaseElapsedTicks=%d next=%s",
                     automatic==null?"":automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.COMPLETE?"AUTOMATIC COMPLETE ":"AUTOMATIC ",
                      entry.phase(),entry.initialized(),heatersEnabled,coldFlow,carrier.meanTemperatureKelvin(),carrier.heaterPortTemperatureKelvin(),carrier.nitrogenMoles(),heaterPaidJ,heaterThermalJ,heaterReceipts,feed.temperatureKelvin(),feed.oxygenMoles(),feed.plasmaMoles(),
                      mean,co2,burnerVisual.oxygenMoles(),burnerVisual.plasmaMoles(),burnerVisual.carbonDioxideMoles(),
                      burnerVisual.reactionEligibleCells(),burnerVisual.activeFireCells(),burnerVisual.maximumFireIntensity(),burnerVisual.plasmaVisibleCells(),TegBurnerDiagnostic.PLASMA_VISIBILITY_THRESHOLD_MOLES_PER_CELL,
                      gain,materialReady?"READY":"NOT_READY",bootstrap.operatingJoules(),bootstrap.secondaryJoules(),
                      bootstrap.operatingJoules()/bootstrap.operatingDemandWatts(),bootstrap.secondaryJoules()/bootstrap.secondaryDemandWatts(),
                      Math.min(bootstrap.operatingJoules()/bootstrap.operatingDemandWatts(),bootstrap.secondaryJoules()/bootstrap.secondaryDemandWatts()),
                      output.energyJoules()/bootstrap.operatingDemandWatts(),secondaryApc.energyJoules()/bootstrap.secondaryDemandWatts(),
                      Math.min(output.energyJoules()/bootstrap.operatingDemandWatts(),secondaryApc.energyJoules()/bootstrap.secondaryDemandWatts()),
                      tegState==null?0:tegState.lastGeneratedWatts(),tegState==null?0:tegState.supply().maximumSupplyWatts(),
                     observed.placement().core().tegLastOfferedWatts(),offerAge,observed.placement().core().hvDeliveredWatts(),supplyAge,frame.inputWatts(),frame.demandWatts(),
                     mainApcFrame.known()?mainApcFrame.outputWatts():-1,mainApcFrame.known()?mainApcFrame.energyAfterJoules()-mainApcFrame.energyBeforeJoules():0,output.energyJoules(),
                      secondaryApcFrame.known()?secondaryApcFrame.inputWatts():-1,secondaryApcFrame.known()?secondaryApcFrame.outputWatts():-1,secondaryApcFrame.known()?secondaryApcFrame.energyAfterJoules()-secondaryApcFrame.energyBeforeJoules():0,secondaryApc.energyJoules(),
                     frame.generationReady()?"READY":"NOT_READY",entry.phasePowerSampleFloor(),frame.sampleTick(),
                      tegState==null?0:tegState.cumulativeGeneratedJoules(),boundaryKind,radiatorAmbientReceipt,radiatorSpaceReceipt,
                      proof,preheatElapsed,entry.phaseStartedTick()<0?-1:level.getGameTime()-entry.phaseStartedTick(),next);
        } catch(RuntimeException failure) { return "NOT READY: continuous status could not validate owned hardware: "+failure.getMessage(); }
    }

    static String classifyRadiatorBoundaries(List<String> kinds){
        if(kinds==null||kinds.size()!=TegContinuousCoolingPlan.RADIATOR_COUNT||kinds.isEmpty())return "UNAVAILABLE";
        String first=kinds.getFirst();
        if(!("SPACE".equals(first)||"PLANETARY_AMBIENT".equals(first)))return "UNAVAILABLE";
        return kinds.stream().allMatch(first::equals)?first:"UNAVAILABLE";
    }
    private static String fmt(double value){return String.format(java.util.Locale.ROOT,"%.2f",value);}

    private static String nextAction(ServerLevel level,TegFixtureSavedData.Entry entry,ObservedHardware observed,FuelFeed feed,double burnerK,double co2,PowerReadiness power) {
        return switch(entry.phase()) {
            case BUILT -> "start once"; case STARTUP -> ignitionReadinessFailure(level,observed)==null?"automatic startup is advancing":"automatic preheat is waiting for both paid heaters to warm the whole hot loop to "+String.format(java.util.Locale.ROOT,"%.0f",STARTUP_CARRIER_MINIMUM_K)+" K and for real fuel O2/plasma; cold-loop flow remains off until ignition";
            case IGNITION -> co2-entry.phaseCo2Baseline()>=.01&&burnerK>=550?"automatic startup is qualifying running":"automatic ignition is waiting for real material reaction and >=550 K burner mean";
            case RUNNING -> co2-entry.phaseCo2Baseline()>=.01&&burnerK>=550&&power.generationReady()?"automatic startup is applying heater cutoff":"automatic startup is waiting for reaction, >=550 K, and accepted APC input covering post-heater load";
            case HEATER_OFF -> "observe heater-off 1,200-tick power and thermal proof"; case NONE -> "unsupported phase";
        };
    }

    /** Shared production guard used by operator ignition and truthful phase guidance. */
    static String ignitionReadinessFailure(ServerLevel level,ObservedHardware observed) {
        var heaters=heaters(observed);var carrier=carrierLoop(observed);var feed=fuelFeed(observed);
        for(var heater:heaters) {
            if(heater.resolvedProfile()==null||heater.resolvedProfile().thermostat()==null
                    ||heater.resolvedProfile().thermostat().maximumTemperatureK()<STARTUP_CARRIER_MINIMUM_K)
                return "resolved startup heater maximum is below carrier qualification temperature at "+heater.getBlockPos();
            var meter=PowerRuntime.apcPowerMeter(level,heater);Double requested=heater.apcPowerDemandWatts();
            if(!heater.settingsSnapshot().enabled().orElse(false)||heater.committedPaidJoules()<=0||heater.committedThermalJoules()<=0
                    ||requested==null||!Double.isFinite(requested)||requested<=0||heater.apcAllocatedWatts()<requested
                    ||!meter.known()||meter.deliveredWatts()<requested)
                return "wait for each startup heater's fully paid resolved grant and positive committed thermal contribution at "+heater.getBlockPos();
        }
        if(carrier.meanTemperatureKelvin()<STARTUP_CARRIER_MINIMUM_K)
            return String.format(java.util.Locale.ROOT,"wait for whole hot-loop mean >=%.0f K (current %.2f K)",STARTUP_CARRIER_MINIMUM_K,carrier.meanTemperatureKelvin());
        if(!carrier.heaterComponentConnected()||carrier.nitrogenMoles()<=0)
            return "owned heater gas component is not connected to the three-component hot loop or carrier nitrogen inventory is empty";
        if(feed.oxygenMoles()<=0||feed.plasmaMoles()<=0)return "wait for actual miner-fed fuel O2 and plasma";
        return null;
    }

    private static void applyOperatingControls(ServerLevel level,ObservedHardware observed,TegContinuousOperatingPlan.Stage stage) {
        var controls=bindOperatingSettings(level,observed,stage);
        for(var bound:controls.atmosphere()) {
            var machine=bound.blockEntity();requireLoaded(level,machine.getBlockPos());
            if(level.getBlockEntity(machine.getBlockPos())!=machine||!machine.settingsValid()
                    ||machine.settingsRevision()!=bound.plan().expectedInitialRevision())
                throw new IllegalStateException("atmosphere host/settings revision changed before "+stage+" at "+machine.getBlockPos());
        }
        for(var bound:controls.ventilation()) {
            var vent=bound.blockEntity();requireLoaded(level,vent.getBlockPos());
            if(level.getBlockEntity(vent.getBlockPos())!=vent||!vent.settingsValid()
                    ||vent.config().settings().revision()!=bound.proposal().expectedRevision())
                throw new IllegalStateException("ventilation host/settings revision changed before "+stage+" at "+vent.getBlockPos());
            if(bound.boundary()!=null&&!bound.boundary().validate())throw new IllegalStateException("exterior boundary changed before "+stage);
        }
        for(var bound:controls.atmosphere())applyPlan(bound.blockEntity(),bound.plan());
        for(var bound:controls.ventilation()) {
            var vent=bound.blockEntity();var proposal=bound.proposal();
            if(bound.boundary()!=null&&!bound.boundary().validate())throw new IllegalStateException("exterior boundary changed before ventilation apply");
            if(!sameVentilationValues(vent.config().settings(),proposal.settings())
                    &&!vent.compareAndSetSettings(proposal.expectedRevision(),proposal.settings()))
                throw new IllegalStateException("ventilation settings rejected at "+vent.getBlockPos());
            if(!sameVentilationValues(vent.config().settings(),proposal.settings()))throw new IllegalStateException("ventilation final settings mismatch at "+vent.getBlockPos());
        }
    }
    private static boolean sameVentilationValues(com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Settings a,
                                                  com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Settings b) {
        return a.configuredEnabled()==b.configuredEnabled()
                &&Double.compare(a.externalPressureBoundKPa(),b.externalPressureBoundKPa())==0
                &&Double.compare(a.targetPressureChangeKPaPerSecond(),b.targetPressureChangeKPaPerSecond())==0
                &&Double.compare(a.targetPressureKPa(),b.targetPressureKPa())==0
                &&Double.compare(a.volumeRateM3PerSecond(),b.volumeRateM3PerSecond())==0
                &&a.scrubberMode()==b.scrubberMode()&&a.filteredGases().equals(b.filteredGases())
                &&a.gasesToScrub().equals(b.gasesToScrub())&&a.selectedLayer()==b.selectedLayer();
    }

    enum WarmupDecision { WAIT, ADMIT, EXPIRED }
    static WarmupDecision warmupAdmission(long startTick,long nowTick,boolean fullPaidGenerationReady) {
        if(startTick<0||nowTick<startTick)return WarmupDecision.EXPIRED;
        long deadline=Math.addExact(startTick,1000);
        if(nowTick<deadline)return WarmupDecision.WAIT;
        if(nowTick==deadline&&fullPaidGenerationReady)return WarmupDecision.ADMIT;
        return WarmupDecision.EXPIRED;
    }

    private static void applyPlan(AtmosMachineBlockEntity machine,TegContinuousOperatingPlan.Plan plan) {
        long revision=plan.expectedInitialRevision();
        for(var request:plan.requests())if(!machine.applySettings(revision++,request.operation(),request.value()))throw new IllegalStateException("ordinary settings proposal rejected at "+machine.getBlockPos());
        if(!machine.settingsSnapshot().equals(plan.finalValues()))throw new IllegalStateException("ordinary settings proposal did not settle at "+machine.getBlockPos());
    }

    private record FuelFeed(double temperatureKelvin,double oxygenMoles,double plasmaMoles) { }
    private record CarrierLoop(double meanTemperatureKelvin,double heatCapacity,double nitrogenMoles,
                               double heaterPortTemperatureKelvin,boolean heaterComponentConnected) { }
    static record PowerReadiness(AtmosMachineBlockEntity core,PowerRuntime.ApcOutputObservation output,
                                  double inputWatts,double demandWatts,boolean fullLoadMatchedFrame,
                                  boolean generationReady,long sampleTick) { }
    record PowerFrameFlags(boolean fullPaidLoad,boolean generationReady) { }
    static boolean heaterOffAckFrameReady(boolean disabled,long heaterTick,long coreTick,long outputTick,long floor,boolean matchedAndPaid) {
        return disabled&&heaterTick>floor&&coreTick>floor&&outputTick>floor&&matchedAndPaid;
    }
    static PowerFrameFlags classifyPowerFrame(boolean common,double inputWatts,double outputWatts,
                                               double operatingDemandWatts,double heaterDemandWatts) {
        if(!Double.isFinite(inputWatts)||!Double.isFinite(outputWatts)||!Double.isFinite(operatingDemandWatts)
                ||!Double.isFinite(heaterDemandWatts)||inputWatts<0||outputWatts<0||operatingDemandWatts<=0||heaterDemandWatts<0)
            return new PowerFrameFlags(false,false);
        return new PowerFrameFlags(common&&outputWatts>=operatingDemandWatts+heaterDemandWatts,
                common&&inputWatts>=operatingDemandWatts);
    }
    private static TegFixtureSavedData.Entry continuousEntry(ServerLevel level,BlockPos origin) {
        var e=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        return e!=null&&e.mode()==TegFixtureSavedData.Mode.CONTINUOUS?e:null;
    }
    private static AtmosMachineBlockEntity heater(ObservedHardware observed) {
        return observed.placement().baseMachines().stream().filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.HEATER).findFirst().orElseThrow();
    }
    static TegContinuousPowerPlan.BootstrapSplit startupBootstrapSplit(ObservedHardware observed) {
        Objects.requireNonNull(observed);
        List<TegContinuousPowerPlan.StartupReceiver> roster=new ArrayList<>();
        var expected=TegContinuousPowerPlan.startupReceiverPositions(observed.layout());
        var actual=startupAdmissionReceivers((ServerLevel)observed.placement().core().getLevel(),observed);
        // The actual typed live roster must match the independently resolved plan before its demands seed stores.
        var actualSecondary=actualSecondaryStartupReceiver(observed);
        if(!TegContinuousPowerPlan.exactReceiverRoster(expected.mainApc(),TegContinuousPowerPlan.receiverIdentities(actual)))
            throw new IllegalStateException("actual operating APC receivers differ from typed plan");
        if(!TegContinuousPowerPlan.exactReceiverRoster(expected.secondaryApc(),TegContinuousPowerPlan.receiverIdentities(actualSecondary)))
            throw new IllegalStateException("actual auxiliary APC receivers differ from typed plan");
        for(var entry:expected.mainApc().entrySet())roster.add(resolvedStartupReceiver(observed,entry.getKey(),entry.getValue(),TegContinuousPowerPlan.StartupCircuit.OPERATING));
        for(var entry:expected.secondaryApc().entrySet())roster.add(resolvedStartupReceiver(observed,entry.getKey(),entry.getValue(),TegContinuousPowerPlan.StartupCircuit.SECONDARY_HEATER));
        return TegContinuousPowerPlan.splitBootstrapByResolvedDemand(TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES,roster);
    }
    private static TegContinuousPowerPlan.StartupReceiver resolvedStartupReceiver(ObservedHardware observed,BlockPos pos,
            TegContinuousPowerPlan.StartupReceiverKind expectedKind,TegContinuousPowerPlan.StartupCircuit circuit) {
        var level=observed.placement().core().getLevel();var blockEntity=level==null?null:level.getBlockEntity(pos);
        double watts;
        if(blockEntity instanceof AtmosMachineBlockEntity machine&&machine.resolvedProfile()!=null
                &&startupReceiverKind(machine.resolvedProfile().kind())==expectedKind)
            watts=machine.resolvedProfile().configuredReceiverLoadWatts();
        else if(expectedKind==TegContinuousPowerPlan.StartupReceiverKind.VENTILATION
                &&blockEntity instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity vent)
            watts=vent.prototype().apcPowerReceiver().requestedWatts();
        else if(expectedKind==TegContinuousPowerPlan.StartupReceiverKind.POWER_LAMP&&blockEntity instanceof PowerDeviceBlockEntity lamp
                &&lamp.getBlockState().getBlock() instanceof com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block
                &&block.kind().isLamp())watts=block.kind().lampDemandWatts();
        else throw new IllegalStateException("planned APC startup receiver is missing or has the wrong kind at "+pos);
        if(!Double.isFinite(watts)||watts<=0)throw new IllegalStateException("resolved startup receiver demand is not positive at "+pos);
        return new TegContinuousPowerPlan.StartupReceiver(pos,circuit,watts);
    }
    /** Read-only product roster seam: actual live APC-port hosts, excluding the separately powered auxiliary circuit. */
    static Map<BlockPos,TegContinuousPowerPlan.StartupReceiverKind> startupAdmissionReceivers(ServerLevel level,ObservedHardware observed) {
        var plan=TegContinuousPowerPlan.startupReceiverPositions(observed.layout());
        BlockPos auxiliaryHeater=plan.secondaryApc().keySet().stream().findFirst().orElseThrow();
        Map<BlockPos,TegContinuousPowerPlan.StartupReceiverKind> actual=new LinkedHashMap<>();
        addActualReceiver(actual,observed.placement().core().getBlockPos(),TegContinuousPowerPlan.StartupReceiverKind.TEG_CORE);
        for(var machine:observed.placement().baseMachines())if(hasApcRuntimePort(level,machine)&&!machine.getBlockPos().equals(auxiliaryHeater))
            addActualReceiver(actual,machine.getBlockPos(),startupReceiverKind(machine.resolvedProfile().kind()));
        for(var machine:observed.placement().additional().atmosMachines())if(hasApcRuntimePort(level,machine))
            addActualReceiver(actual,machine.getBlockPos(),startupReceiverKind(machine.resolvedProfile().kind()));
        observed.placement().additional().ventilation().forEach(vent->addActualReceiver(actual,vent.getBlockPos(),TegContinuousPowerPlan.StartupReceiverKind.VENTILATION));
        addActualReceiver(actual,observed.layout().fueled().electrical().devices().load(),TegContinuousPowerPlan.StartupReceiverKind.POWER_LAMP);
        return Map.copyOf(actual);
    }
    private static Map<BlockPos,TegContinuousPowerPlan.StartupReceiverKind> actualSecondaryStartupReceiver(ObservedHardware observed) {
        var heater=observed.layout().fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList().get(1);
        var machine=observed.placement().baseMachines().stream().filter(m->m.getBlockPos().equals(heater.position())).findFirst().orElse(null);
        if(machine==null||machine.resolvedProfile()==null||machine.resolvedProfile().kind()!=AtmosMachineProfile.Kind.HEATER)
            return Map.of();
        return Map.of(machine.getBlockPos(),TegContinuousPowerPlan.StartupReceiverKind.HEATER);
    }
    private static void addActualReceiver(Map<BlockPos,TegContinuousPowerPlan.StartupReceiverKind> receivers,BlockPos pos,
                                          TegContinuousPowerPlan.StartupReceiverKind kind) {
        if(receivers.putIfAbsent(pos,kind)!=null)throw new IllegalStateException("duplicate live APC receiver owner position: "+pos);
    }
    private static TegContinuousPowerPlan.StartupReceiverKind startupReceiverKind(AtmosMachineProfile.Kind kind) {
        return switch(kind) {
            case TEG_CENTER -> TegContinuousPowerPlan.StartupReceiverKind.TEG_CORE;
            case HEATER -> TegContinuousPowerPlan.StartupReceiverKind.HEATER;
            case VOLUME_PUMP -> TegContinuousPowerPlan.StartupReceiverKind.VOLUME_PUMP;
            case PRESSURE_PUMP -> TegContinuousPowerPlan.StartupReceiverKind.PRESSURE_PUMP;
            default -> throw new IllegalStateException("unexpected resolved APC receiver profile kind "+kind);
        };
    }
    private static List<AtmosMachineBlockEntity> heaters(ObservedHardware observed) {
        var result=observed.placement().baseMachines().stream().filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.HEATER).toList();
        long planned=observed.layout().fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).count();
        if(result.size()!=planned||result.isEmpty())throw new IllegalStateException("resolved startup-heater roster differs from fixture plan");
        return result;
    }
    private static AtmosMachineBlockEntity carrierPump(ObservedHardware observed,TegContinuousOperatingPlan.Role role) {
        if(role!=TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP&&role!=TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP)
            throw new IllegalArgumentException("carrier pump role required");
        var positions=TegContinuousOperatingPlan.carrierPumpPositions(observed.layout().fueled());
        var position=positions.get(role==TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP?0:1);
        var expected=role==TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP
                ?AtmosMachineProfile.Kind.PRESSURE_PUMP:AtmosMachineProfile.Kind.VOLUME_PUMP;
        var matches=observed.placement().baseMachines().stream().filter(machine->machine.getBlockPos().equals(position)
                &&machine.resolvedProfile()!=null&&machine.resolvedProfile().kind()==expected).toList();
        if(matches.size()!=1)throw new IllegalStateException("planned "+role+" is not exactly one owned resolved "+expected);
        return matches.getFirst();
    }
    private static com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity burnerSupply(ObservedHardware observed) {
        BlockPos pos=observed.layout().devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.INJECTOR).map(TegContinuousFuelPlan.Device::position).findFirst().orElseThrow();
        return observed.placement().additional().ventilation().stream().filter(v->v.getBlockPos().equals(pos)).findFirst().orElseThrow();
    }
    private static FuelFeed fuelFeed(ObservedHardware observed) {
        var mixer=observed.placement().additional().atmosMachines().stream().filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.MIXER).findFirst().orElseThrow();
        var port=mixer.gasPorts().stream().filter(p->p.id().equals("outlet")).findFirst().orElseThrow();
        var component=capturedComponentForPort(observed.topology(),observed.capture(),new PipeTopology.NodeKey(mixer.getBlockPos(),port.layer(),port.id())).orElseThrow();
        var gas=component.aggregate();return new FuelFeed(gas.temperatureKelvin(),gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN),gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA));
    }
    private static CarrierLoop carrierLoop(ObservedHardware observed) {
        var heater=heater(observed);
        var port=heater.gasPorts().stream().filter(p->p.id().equals("pipe")).findFirst().orElseThrow();
        var heaterComponent=capturedComponentForPort(observed.topology(),observed.capture(),port.key(heater.getBlockPos()))
                .orElseThrow(()->new IllegalStateException("owned heater carrier gas component unavailable"));
        double heatCapacity=0,thermalEnergy=0,nitrogen=0;Set<PipeTopology.NodeKey> roots=new HashSet<>();boolean heaterConnected=false;
        for(int i=0;i<3;i++) {
            var representative=observed.topology().representatives().get(i);
            var component=observed.capture().componentFor(representative).orElse(null);
            if(component==null)throw new IllegalStateException("owned hot-loop component unavailable at index "+i);
            if(!roots.add(component.root()))throw new IllegalStateException("hot-loop component ownership aliases at index "+i);
            var expected=observed.topology().components().get(i);
            if(!component.nodes().stream().map(n->n.key()).collect(java.util.stream.Collectors.toSet()).equals(expected))
                throw new IllegalStateException("owned hot-loop component membership changed at index "+i);
            var gas=component.aggregate();heatCapacity+=gas.heatCapacity();thermalEnergy+=gas.thermalEnergy();nitrogen+=gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN);
            if(component.root().equals(heaterComponent.root()))heaterConnected=true;
        }
        var heaterGas=heaterComponent.aggregate();
        if(!Double.isFinite(heatCapacity)||heatCapacity<=0||!Double.isFinite(thermalEnergy))throw new IllegalStateException("invalid owned whole-loop thermal inventory");
        return new CarrierLoop(thermalEnergy/heatCapacity,heatCapacity,nitrogen,heaterGas.temperatureKelvin(),heaterConnected);
    }
    private static double burnerMeanKelvin(ServerLevel level,ObservedHardware observed) {
        double sum=0;int n=0;for(BlockPos pos:observed.layout().fueled().roomInterior()){sum+=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,pos).orElseThrow().snapshot().temperatureKelvin();n++;}
        if(n!=27)throw new IllegalStateException("owned burner cell count changed: "+n);return sum/n;
    }
    static TegBurnerDiagnostic.Snapshot burnerDiagnostic(ServerLevel level,ObservedHardware observed) {
        List<TegBurnerDiagnostic.Cell> cells=new ArrayList<>();
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        for(BlockPos pos:observed.layout().fueled().roomInterior()) {
            var gas=atmosphere.finiteRoomEndpointForTrustedSimulation(level,pos).orElseThrow().snapshot();
            var visual=com.juicyslew.moonstation14.ms14.atmos.visual.network.AtmosphereVisualPayload.VisualCell.from(gas,pos);
            cells.add(new TegBurnerDiagnostic.Cell(gas.temperatureKelvin(),
                    gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA),
                    gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN),
                    gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.CARBON_DIOXIDE),
                    atmosphere.fireIntensity(level,pos),visual.plasmaAlpha()));
        }
        return TegBurnerDiagnostic.aggregate(cells);
    }
    private static double co2Total(ServerLevel level,ObservedHardware observed) {
        double amount=0;for(BlockPos pos:observed.layout().fueled().roomInterior())amount+=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,pos).orElseThrow().snapshot().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.CARBON_DIOXIDE);
        for(var component:observed.capture().components().values())amount+=component.aggregate().moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.CARBON_DIOXIDE);
        for(var vent:observed.placement().additional().ventilation())if(vent.getBlockPos().equals(observed.layout().wasteVentBody()))amount+=vent.exportObservation().speciesMoles().getOrDefault(com.juicyslew.moonstation14.ms14.atmos.core.GasType.CARBON_DIOXIDE,0d);
        return amount;
    }
    /** Shared raw operating-power observation used by both production transitions and steady proof. */
    static PowerReadiness matchedPowerFrame(ServerLevel level,ObservedHardware observed,long freshFloor,boolean expectedHeaterOn) {
        var core=observed.placement().core();
        var output=PowerRuntime.apcOutputObservation(level,(PowerDeviceBlockEntity)level.getBlockEntity(observed.layout().fueled().electrical().devices().outputApc()));
         var heaters=heaters(observed);double demand=0,heaterDemand=0,secondaryHeaterDemand=0;boolean hosts=true;
         BlockPos secondaryHeaterPosition=observed.layout().fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList().get(1).position();
        var mixer=observed.placement().additional().atmosMachines().stream().filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.MIXER).findFirst().orElse(null);
        hosts&=mixer!=null&&mixer.settingsSnapshot().enabled().orElse(false)
                &&Math.abs(mixer.settingsSnapshot().targetPressureKpa().orElse(-1d)-TegContinuousOperatingPlan.RUNNING_MIXER_TARGET_KPA)<1e-12
                &&Math.abs(mixer.settingsSnapshot().mixerFractionA().orElse(-1d)-TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION)<1e-12;
        var pumps=observed.placement().baseMachines().stream().filter(m->m.resolvedProfile()!=null&&(m.resolvedProfile().kind()==AtmosMachineProfile.Kind.VOLUME_PUMP||m.resolvedProfile().kind()==AtmosMachineProfile.Kind.PRESSURE_PUMP)).toList();
        var expectedStage=expectedHeaterOn?TegContinuousOperatingPlan.Stage.RUNNING:TegContinuousOperatingPlan.Stage.HEATER_OFF;
        hosts&=pumps.size()==2&&pumps.stream().allMatch(p->settingsMatch(p,observed.layout().fueled(),expectedStage));
        var pressurePump=observed.placement().additional().atmosMachines().stream().filter(m->m.getBlockPos().equals(observed.layout().devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP))).findFirst().orElse(null);
        hosts&=pressurePump!=null&&pumpSettingsMatch(TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP,pressurePump.resolvedProfile(),pressurePump.settingsSnapshot(),expectedStage);
        var supply=burnerSupply(observed);
        hosts&=supply.config().settings().configuredEnabled()
                &&Math.abs(supply.config().settings().targetPressureKPa()-TegContinuousOperatingPlan.RUNNING_BURNER_SUPPLY_TARGET_KPA)<1e-12
                &&normalVentilationRecipeValid(observed);
        List<AtmosMachineBlockEntity> machines=new ArrayList<>();machines.add(core);machines.addAll(observed.placement().baseMachines());machines.addAll(observed.placement().additional().atmosMachines());
        long expectedAtmosphere=1L+observed.layout().fueled().machines().size()+observed.layout().devices().stream().filter(d->d.role().isAtmosMachine()).count();
        hosts&=machines.size()==expectedAtmosphere&&machines.stream().map(m->m.getBlockPos()).distinct().count()==expectedAtmosphere;
        long passiveExchangers=machines.stream().filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER).count();
        hosts&=passiveExchangers==8;
        for(var machine:machines) {
             if(heaters.contains(machine)) {
                hosts&=machine.settingsSnapshot().enabled().orElse(false)==expectedHeaterOn;
                Double watts=machine.apcPowerDemandWatts();
                if(expectedHeaterOn) {
                    if(watts==null||!Double.isFinite(watts)||watts<=0)hosts=false;
                     else {
                         if(machine.getBlockPos().equals(secondaryHeaterPosition))secondaryHeaterDemand+=watts;
                         else heaterDemand+=watts;
                         var meter=PowerRuntime.apcPowerMeter(level,machine);hosts&=meter.known()&&meter.deliveredWatts()>=watts;
                     }
                } else hosts&=machine.apcAllocatedWatts()==0;
                continue;
            }
            if(machine.settingsSnapshot().enabled().isPresent()&&!machine.settingsSnapshot().enabled().orElse(false))hosts=false;
            Double watts=machine.apcPowerDemandWatts();
            if(machine.resolvedProfile()!=null&&machine.resolvedProfile().kind()==AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER) {
                hosts&=watts==null||watts==0;
                continue;
            }
            if(watts!=null) {
                if(!Double.isFinite(watts)||watts<=0)hosts=false;
                else {demand+=watts;var meter=PowerRuntime.apcPowerMeter(level,machine);hosts&=meter.known()&&meter.deliveredWatts()>=watts;}
            }
        }
        int ventilationHosts=0;
        for(var vent:observed.placement().additional().ventilation()) {
            ventilationHosts++;double watts=vent.prototype().apcPowerReceiver().requestedWatts();demand+=watts;
            var meter=PowerRuntime.ventilationMeter(level,vent);
            hosts&=Double.isFinite(watts)&&watts>0&&vent.config().settings().configuredEnabled()&&meter.known()&&meter.deliveredWatts()>=watts;
        }
        hosts&=ventilationHosts==5;
        var load=observed.layout().fueled().electrical().devices().load();var lamp=(PowerDeviceBlockEntity)level.getBlockEntity(load);
        var loadBlock=level.getBlockState(load).getBlock();
        if(!(loadBlock instanceof com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block)||!block.kind().isLamp()||lamp==null)hosts=false;
        else {demand+=block.kind().lampDemandWatts();hosts&=lamp.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT);}
        Double coreDemand=core.apcPowerDemandWatts();var coreMeter=PowerRuntime.apcPowerMeter(level,core);
        hosts&=coreDemand!=null&&Double.isFinite(coreDemand)&&coreDemand>0&&coreMeter.known()&&coreMeter.deliveredWatts()>=coreDemand;
        boolean matched=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level)&&output.known()
                &&core.hvAllocationKnown()&&core.hvSampleTick()==output.sampleTick()&&output.sampleTick()>freshFloor
                &&output.topologyGeneration()==com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level);
        boolean common=matched&&hosts&&core.hvDeliveredJoules()>0;
        var flags=classifyPowerFrame(common,output.inputWatts(),output.outputWatts(),demand,heaterDemand);
        BlockPos secondaryApcPos=TegContinuousPowerPlan.equipment(observed.layout()).secondaryStartupApc();
        var secondaryApc=(PowerDeviceBlockEntity)level.getBlockEntity(secondaryApcPos);
        var secondaryOutput=PowerRuntime.apcOutputObservation(level,secondaryApc);
        boolean secondaryFresh=secondaryOutput.known()&&secondaryOutput.sampleTick()==output.sampleTick()
                &&secondaryOutput.topologyGeneration()==output.topologyGeneration();
        if(expectedHeaterOn)hosts&=secondaryFresh&&secondaryOutput.inputWatts()==0
                &&secondaryOutput.outputWatts()+1e-9>=secondaryHeaterDemand;
        else hosts&=secondaryFresh&&secondaryOutput.inputWatts()==0&&secondaryOutput.outputWatts()==0
                &&Math.abs(secondaryOutput.energyAfterJoules()-secondaryOutput.energyBeforeJoules())<=1e-6;
        flags=classifyPowerFrame(matched&&hosts&&core.hvDeliveredJoules()>0,output.inputWatts(),output.outputWatts(),demand,heaterDemand);
        return new PowerReadiness(core,output,output.inputWatts(),demand,flags.fullPaidLoad(),flags.generationReady(),output.sampleTick());
    }

    static boolean settingsMatch(AtmosMachineBlockEntity pump,TegFueledFixturePlan.Layout layout,TegContinuousOperatingPlan.Stage stage) {
        if(pump==null||layout==null||stage==null||pump.resolvedProfile()==null)return false;
        try {
            var role=TegContinuousOperatingPlan.pumpRole(layout,pump.getBlockPos());
            return pumpSettingsMatch(role,pump.resolvedProfile(),pump.settingsSnapshot(),stage);
        } catch(IllegalArgumentException ex) { return false; }
    }
    static boolean pumpSettingsMatch(TegContinuousOperatingPlan.Role role,AtmosMachineProfile profile,
                                     com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Values current,
                                     TegContinuousOperatingPlan.Stage stage) {
        if(role!=TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP&&role!=TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP&&role!=TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP)return false;
        try { return TegContinuousOperatingPlan.create(role,profile,current,stage).finalValues().equals(current); }
        catch(IllegalArgumentException ex) { return false; }
    }

    /** Fixed ordinary source/waste valve recipe; siphon exports all residual fuel, not just products. */
    static boolean normalVentilationRecipeValid(ObservedHardware observed) {
        for(var role:List.of(TegContinuousOperatingPlan.VentilationRole.SOURCE_OXYGEN,
                TegContinuousOperatingPlan.VentilationRole.SOURCE_PLASMA,
                TegContinuousOperatingPlan.VentilationRole.BURNER_WASTE)) {
            var plannedRole=role;
            var devices=observed.layout().devices().stream().filter(d->switch(plannedRole) {
                case SOURCE_OXYGEN,SOURCE_PLASMA -> d.role()==TegContinuousFuelPlan.DeviceRole.SCRUBBER;
                case BURNER_WASTE -> d.role()==TegContinuousFuelPlan.DeviceRole.WASTE_PUMP;
                default -> false;
            }).toList();
            int index=role==TegContinuousOperatingPlan.VentilationRole.SOURCE_PLASMA?1:0;
            if(devices.size()<=index)return false;
            var device=devices.get(index);
            var vent=observed.placement().additional().ventilation().stream().filter(v->v.getBlockPos().equals(device.position())).findFirst().orElse(null);
            if(vent==null)return false;
            var current=vent.config().settings();
            var proposal=TegContinuousOperatingPlan.proposeVentilation(role,vent.prototype(),current,0,TegContinuousOperatingPlan.Stage.RUNNING);
            var expected=proposal.settings();
            if(current.revision()+1!=expected.revision()||current.configuredEnabled()!=expected.configuredEnabled()
                    ||Double.compare(current.externalPressureBoundKPa(),expected.externalPressureBoundKPa())!=0
                    ||Double.compare(current.targetPressureChangeKPaPerSecond(),expected.targetPressureChangeKPaPerSecond())!=0
                    ||Double.compare(current.targetPressureKPa(),expected.targetPressureKPa())!=0
                    ||Double.compare(current.volumeRateM3PerSecond(),expected.volumeRateM3PerSecond())!=0
                    ||current.scrubberMode()!=expected.scrubberMode()||!current.filteredGases().equals(expected.filteredGases())
                    ||current.selectedLayer()!=expected.selectedLayer())return false;
            if(role==TegContinuousOperatingPlan.VentilationRole.BURNER_WASTE
                    &&(current.scrubberMode()!=com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.ScrubberMode.SIPHON
                    ||!current.gasesToScrub().equals(Set.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()))))return false;
            if((role==TegContinuousOperatingPlan.VentilationRole.SOURCE_OXYGEN||role==TegContinuousOperatingPlan.VentilationRole.SOURCE_PLASMA)
                    &&current.scrubberMode()!=com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.ScrubberMode.FILTER)return false;
        }
        return true;
    }

    private static void bindAtmosphere(List<BoundAtmosphere> result, AtmosMachineBlockEntity machine, TegContinuousOperatingPlan.Role role, TegContinuousOperatingPlan.Stage stage) {
        if(!machine.settingsValid()||machine.resolvedProfile()==null) throw new IllegalStateException("invalid atmosphere host settings/profile");
        result.add(new BoundAtmosphere(machine,TegContinuousOperatingPlan.create(role,machine.resolvedProfile(),machine.settingsSnapshot(),stage)));
    }

    /** Read-only proof that every real operating receiver has the expected live power topology. */
    static String startupPowerFailure(ServerLevel level,ObservedHardware observed) {
        PowerAdmission admission=startupPowerAdmission(level,observed);
        return admission.status()==PowerAdmissionStatus.READY?null:admission.reason();
    }

    static PowerAdmission startupPowerAdmission(ServerLevel level,ObservedHardware observed) {
        long generation=PowerGraphService.topologyGeneration(level);
        if(!PowerGraphService.topologyReady(level))return new PowerAdmission(PowerAdmissionStatus.WAIT,"power topology is not ready");
        var layout=observed.layout();var devices=layout.fueled().electrical().devices();
        var hardware=observed.placement();
        var substation=level.getBlockEntity(devices.substation());
        var apc=level.getBlockEntity(devices.outputApc());
        if(!(substation instanceof PowerDeviceBlockEntity subEntity)||!(apc instanceof PowerDeviceBlockEntity apcEntity)
                ||!level.getBlockState(devices.substation()).is(ModBlocks.HV_MV_SUBSTATION.get())
                ||!level.getBlockState(devices.outputApc()).is(ModBlocks.APC.get()))return new PowerAdmission(PowerAdmissionStatus.REFUSED,"operating substation or APC kind is invalid");
        var lampPos=devices.load();var lamp=level.getBlockEntity(lampPos);
        if(!(lamp instanceof PowerDeviceBlockEntity)||!level.getBlockState(lampPos).is(ModBlocks.POWER_LAMP.get()))return new PowerAdmission(PowerAdmissionStatus.REFUSED,"operating lamp is missing or invalid");
        TegContinuousPowerPlan.StartupReceiverPositions receiverPlan;
        try { receiverPlan=TegContinuousPowerPlan.startupReceiverPositions(layout); }
        catch(RuntimeException invalidPlan) { return new PowerAdmission(PowerAdmissionStatus.REFUSED,"typed startup receiver plan is invalid: "+invalidPlan.getMessage()); }
        Map<BlockPos,TegContinuousPowerPlan.StartupReceiverKind> actualMainReceivers=startupAdmissionReceivers(level,observed);
        if(!TegContinuousPowerPlan.exactReceiverRoster(receiverPlan.mainApc(),TegContinuousPowerPlan.receiverIdentities(actualMainReceivers))) {
            Set<BlockPos> missing=new HashSet<>(receiverPlan.mainApc().keySet());missing.removeAll(actualMainReceivers.keySet());
            Set<BlockPos> unexpected=new HashSet<>(actualMainReceivers.keySet());unexpected.removeAll(receiverPlan.mainApc().keySet());
            Set<BlockPos> wrongKind=new HashSet<>();receiverPlan.mainApc().forEach((pos,kind)->{if(actualMainReceivers.containsKey(pos)&&actualMainReceivers.get(pos)!=kind)wrongKind.add(pos);});
            return new PowerAdmission(PowerAdmissionStatus.REFUSED,"actual main APC receiver roster differs from resolved plan; missing="+missing+" unexpected="+unexpected+" wrongKind="+wrongKind);
        }
        for(BlockPos receiverPos:receiverPlan.mainApc().keySet()) {
            if(!loadedReceiver(level,receiverPos))return new PowerAdmission(PowerAdmissionStatus.WAIT,"power receiver is not already FULL-loaded at "+receiverPos);
            var receiver=level.getBlockEntity(receiverPos);
            if(receiver==null)return new PowerAdmission(PowerAdmissionStatus.REFUSED,"planned main APC receiver block entity missing at "+receiverPos);
            var observation=PowerRuntime.apcConnection(level,receiver);
            PowerAdmission receiverAdmission=classifyReceiverAdmission(observation,devices.outputApc(),generation,
                    PowerGraphService.topologyReady(level),receiverPos);
            if(receiverAdmission.status()!=PowerAdmissionStatus.READY)return receiverAdmission;
        }
        BlockPos secondaryApc=TegContinuousPowerPlan.equipment(layout).secondaryStartupApc();
        if(receiverPlan.secondaryApc().size()!=1||receiverPlan.secondaryApc().values().iterator().next()!=TegContinuousPowerPlan.StartupReceiverKind.HEATER)
            return new PowerAdmission(PowerAdmissionStatus.REFUSED,"typed auxiliary receiver plan is not the single second heater");
        BlockPos secondaryHeater=receiverPlan.secondaryApc().keySet().iterator().next();
        var secondaryHeaterEntity=hardware.baseMachines().stream().filter(m->m.getBlockPos().equals(secondaryHeater)).findFirst().orElse(null);
        if(!(level.getBlockEntity(secondaryApc) instanceof PowerDeviceBlockEntity secondaryApcEntity)||secondaryHeaterEntity==null
                ||!level.getBlockState(secondaryApc).is(ModBlocks.APC.get()))return new PowerAdmission(PowerAdmissionStatus.REFUSED,"dedicated second-heater APC/receiver is missing");
        var secondaryConnection=PowerRuntime.apcConnection(level,secondaryHeaterEntity);
        if(!secondaryConnection.known())return new PowerAdmission(PowerAdmissionStatus.WAIT,"secondary APC output topology is not known: "+secondaryConnection.reason());
        if(!secondaryApc.equals(secondaryConnection.provider()))return new PowerAdmission(PowerAdmissionStatus.REFUSED,"second heater is not isolated on its dedicated APC output");
        if(!secondaryApcEntity.breakerClosed())return new PowerAdmission(PowerAdmissionStatus.REFUSED,"dedicated second-heater APC breaker is open");
        var corePort=onePort(hardware.core(),CableTier.HV);var subHv=onePort(subEntity,CableTier.HV);
        var subMv=onePort(subEntity,CableTier.MV);var apcMv=onePort(apcEntity,CableTier.MV);
        var isolatedMv=onePort(secondaryApcEntity,CableTier.MV);
        if(corePort==null||subHv==null||subMv==null||apcMv==null||isolatedMv==null)return new PowerAdmission(PowerAdmissionStatus.REFUSED,"HV/MV power port profile is missing or ambiguous");
        var coreState=PowerGraphService.state(level,corePort);var hvState=PowerGraphService.state(level,subHv);
        var mvState=PowerGraphService.state(level,subMv);var apcState=PowerGraphService.state(level,apcMv);var isolatedMvState=PowerGraphService.state(level,isolatedMv);
        if(coreState.knowledge()!=LoadedPowerGraph.Knowledge.KNOWN||hvState.knowledge()!=LoadedPowerGraph.Knowledge.KNOWN
                ||mvState.knowledge()!=LoadedPowerGraph.Knowledge.KNOWN||apcState.knowledge()!=LoadedPowerGraph.Knowledge.KNOWN
                ||isolatedMvState.knowledge()!=LoadedPowerGraph.Knowledge.KNOWN)
            return new PowerAdmission(PowerAdmissionStatus.WAIT,"power topology is not ready (HV/MV connection unknown)");
        if(sameKnown(isolatedMvState,mvState))return new PowerAdmission(PowerAdmissionStatus.REFUSED,"second-heater APC MV stub unexpectedly joins station MV; auxiliary storage must not charge from station or TEG output");
        if(!sameKnown(coreState,hvState)||!sameKnown(mvState,apcState))return new PowerAdmission(PowerAdmissionStatus.REFUSED,"HV/MV power connections are disconnected");
        if(!PowerGraphService.topologyReady(level)||PowerGraphService.topologyGeneration(level)!=generation)
            return new PowerAdmission(PowerAdmissionStatus.WAIT,"power topology changed during admission query; retry after graph settles");
        return new PowerAdmission(PowerAdmissionStatus.READY,"");
    }

    static PowerAdmission classifyReceiverAdmission(PowerRuntime.ApcConnectionObservation observation,BlockPos expectedProvider,
                                                     long generation,boolean topologyReady,BlockPos receiverPos) {
        String reason=observation.reason();
        if(observation.topologyGeneration()!=generation||!topologyReady||reason.equals("topology-unready")
                ||reason.equals("topology-changed-or-unready")||reason.equals("receiver-topology-unknown"))
            return new PowerAdmission(PowerAdmissionStatus.WAIT,"power topology is not ready ("+reason+")");
        if(observation.known()&&expectedProvider.equals(observation.provider()))return new PowerAdmission(PowerAdmissionStatus.READY,"");
        return new PowerAdmission(PowerAdmissionStatus.REFUSED,"APC receiver topology is unknown or not on the operating APC at "+receiverPos+" ("+reason+")");
    }

    private static boolean hasApcRuntimePort(ServerLevel level,net.minecraft.world.level.block.entity.BlockEntity machine) {
        return level.getBlockEntity(machine.getBlockPos()) instanceof PowerRuntimeNode node
                &&node.powerRuntimePorts()!=null&&node.powerRuntimePorts().stream().anyMatch(port->port!=null
                &&port.device().equals(machine.getBlockPos())&&port.tier()==CableTier.APC);
    }

    /** One-shot carrier gas and bootstrap charge admission; operating controls remain untouched. */
    static String initializeResources(ServerLevel level,BlockPos origin) {
        var data=TegFixtureSavedData.existing(level).orElse(null);
        var initial=data==null?Optional.<TegFixtureSavedData.Entry>empty():data.entry(origin);
        if(initial.isEmpty())return "Resource initialization refused: no saved fixture at that origin.";
        var entry=initial.get();
        if(entry.mode()!=TegFixtureSavedData.Mode.CONTINUOUS)return "Resource initialization refused: fixture is not continuous.";
        if(entry.initialized())return "Resource initialization refused: one-shot resource token was already consumed; no refill or reset is permitted.";
        String phase="preflight";
        try {
            var observed=observeOwnedHardware(level,entry);
            if(observed.capture().components().size()!=10)return "Resource initialization refused: expected ten captured gas components.";
            // Every gas component must be empty before the one-shot seed; exhaust paths are now
            // part of the hot carrier components and therefore intentionally become seeded too.
            for(int i=0;i<10;i++) {
                var component=observed.capture().componentFor(observed.topology().representatives().get(i)).orElseThrow();
                var gas=component.aggregate();
                if(!gas.gasMoles().isEmpty()||gas.thermalEnergy()!=0)
                    return "Resource initialization refused: gas component is not vacuum at "+component.root()+".";
                for(var node:component.nodes()) if(node.entry()==null)
                    return "Resource initialization refused: gas component metadata missing at "+node.key()+".";
            }
            var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
             for(var room:List.of(observed.layout().fueled().roomInterior()))
                for(BlockPos cell:room) {
                    var endpoint=atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElse(null);
                    var gas=endpoint==null?null:endpoint.snapshot();
                    if(gas==null||!gas.gasMoles().isEmpty()||gas.thermalEnergy()!=0)
                        return "Resource initialization refused: owned room cell is missing or not vacuum at "+cell+".";
                }
            var outputPos=observed.layout().fueled().electrical().devices().outputApc();
            if(!(level.getBlockEntity(outputPos) instanceof PowerDeviceBlockEntity output)
                    ||!level.getBlockState(outputPos).is(ModBlocks.APC.get())||output.energyJoules()!=0||!output.breakerClosed())
                return "Resource initialization refused: operating APC must be live, breaker-closed, and at zero joules.";
            var secondaryPos=TegContinuousPowerPlan.equipment(observed.layout()).secondaryStartupApc();
            if(!(level.getBlockEntity(secondaryPos) instanceof PowerDeviceBlockEntity secondary)
                    ||!level.getBlockState(secondaryPos).is(ModBlocks.APC.get())||secondary.energyJoules()!=0||!secondary.breakerClosed())
                return "Resource initialization refused: isolated second-heater APC must be live, breaker-closed, and at zero joules.";
            PowerAdmission admission=startupPowerAdmission(level,observed);
            if(admission.status()!=PowerAdmissionStatus.READY)return admission.status()==PowerAdmissionStatus.WAIT
                    ?RESOURCE_POWER_WAIT_PREFIX+" "+admission.reason()+"."
                    :"Resource initialization refused: "+admission.reason()+".";
            var bootstrap=startupBootstrapSplit(observed);
            var seeds=prepareCarrierSeeds(observed);
            if(seeds.size()!=7)return "Resource initialization refused: expected seven carrier seed ports.";
            var seedReadiness=TrustedPipeFixtureInitialization.validateVacuumPorts(level,seeds);
            if(!seedReadiness.successful())return "Resource initialization refused: carrier seed preflight "+seedReadiness.failure()+" ("+seedReadiness.detail()+").";
            // Recheck power after all other preflight work, immediately before consuming the token.
            admission=startupPowerAdmission(level,observed);
            if(admission.status()!=PowerAdmissionStatus.READY)return admission.status()==PowerAdmissionStatus.WAIT
                    ?RESOURCE_POWER_WAIT_PREFIX+" "+admission.reason()+"."
                    :"Resource initialization refused: "+admission.reason()+".";
            if(TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).filter(entry::equals).isEmpty())
                return "Resource initialization refused: saved fixture token or ownership changed.";
            if(!data.markInitialized(entry,level.getGameTime()))return "Resource initialization refused: one-shot token changed; no resources written.";
            phase="carrier gas seeding";
            var seeded=TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level,seeds);
            if(!seeded.successful())return "Resource token consumed but carrier gas seeding failed after "+seeded.completedPorts()+" ports; manual recovery required; do not retry.";
            phase="operating APC bootstrap";
            output.setEnergyJoules(bootstrap.operatingJoules());
            secondary.setEnergyJoules(bootstrap.secondaryJoules());
            double bootstrapTotal=output.energyJoules()+secondary.energyJoules();
            if(Math.abs(bootstrapTotal-bootstrap.totalJoules())>1e-6
                    ||bootstrapTotal>TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES
                    ||output.energyJoules()>PowerDeviceRules.APC_CAPACITY_JOULES
                    ||secondary.energyJoules()>PowerDeviceRules.APC_CAPACITY_JOULES)
                throw new IllegalStateException("split bootstrap does not preserve the original 1 MJ total");
            return String.format(java.util.Locale.ROOT,"Startup resources admitted once: 43 mol N2 hot carrier and 43 mol plasma coolant at 293.15 K; 1 MJ bootstrap split by resolved receiver demand (operating %.2f J, isolated heater %.2f J); operating controls not configured.",bootstrap.operatingJoules(),bootstrap.secondaryJoules());
        } catch(RuntimeException failure) {
            MoonStation14.LOGGER.error("Continuous TEG resource initialization failed phase={} origin={}",phase,origin,failure);
            var current=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
            return current!=null&&current.initialized()
                    ?"Resource token consumed; "+phase+" failed; manual recovery required and do not retry."
                    :"Resource initialization refused before token consumption: "+failure.getClass().getSimpleName()+"; no resources written.";
        }
    }

    private static DevicePort onePort(PowerRuntimeNode node,CableTier tier) {
        var ports=node.powerRuntimePorts();if(ports==null)return null;
        var matches=ports.stream().filter(Objects::nonNull).filter(p->p.device().equals(((net.minecraft.world.level.block.entity.BlockEntity)node).getBlockPos())&&p.tier()==tier).toList();
        return matches.size()==1?matches.get(0):null;
    }
    private static DevicePort onePort(PowerDeviceBlockEntity device,CableTier tier) {
        var matches=device.ports().stream().filter(Objects::nonNull).filter(p->p.device().equals(device.getBlockPos())&&p.tier()==tier).toList();
        return matches.size()==1?matches.get(0):null;
    }
    private static boolean sameKnown(LoadedPowerGraph.NodeState a,LoadedPowerGraph.NodeState b) {
        return a!=null&&b!=null&&a.knowledge()==LoadedPowerGraph.Knowledge.KNOWN&&b.knowledge()==LoadedPowerGraph.Knowledge.KNOWN&&a.componentId()==b.componentId();
    }
    static boolean loadedReceiver(ServerLevel level,BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)
                instanceof net.minecraft.world.level.chunk.LevelChunk;
    }

    /** Plans finite nitrogen carrier charges from observed live machine-port nodes only. */
    static List<TrustedPipeFixtureInitialization.PortSeed> prepareCarrierSeeds(ObservedHardware observed) {
        Objects.requireNonNull(observed, "observed");
        var topology=observed.topology();
        var capture=observed.capture();
        if(topology.representatives().size()!=10||topology.components().size()!=10||capture.components().size()!=10)
            throw new IllegalStateException("expected ten captured continuous gas components");

        List<List<LoadedPipeComponent.Node>> carriers=new ArrayList<>();
        List<Double> hotVolumes=new ArrayList<>(),coldVolumes=new ArrayList<>();
        List<PipeTopology.NodeKey> selectedPorts=new ArrayList<>();
        Set<PipeTopology.NodeKey> uniquePorts=new HashSet<>();
        List<AtmosMachineBlockEntity> machines=new ArrayList<>(observed.placement().baseMachines());
        machines.addAll(observed.placement().additional().atmosMachines());
        for(int i=0;i<7;i++) {
            var component=capture.componentFor(topology.representatives().get(i))
                    .orElseThrow(()->new IllegalStateException("carrier component capture unavailable"));
            var nodesByKey=new HashMap<PipeTopology.NodeKey,LoadedPipeComponent.Node>();
            for(var node:component.nodes())nodesByKey.put(node.key(),node);
            if(!nodesByKey.keySet().equals(topology.components().get(i)))
                throw new IllegalStateException("carrier component membership changed at index "+i);
            PipeTopology.NodeKey portKey=null;
            for(var machine:machines)for(var port:machine.gasPorts()) {
                var candidate=port.key(machine.getBlockPos());
                if(nodesByKey.containsKey(candidate)){portKey=candidate;break;}
            }
            if(portKey==null||!uniquePorts.add(portKey))
                throw new IllegalStateException("missing or duplicate actual machine carrier port for component "+i);
            selectedPorts.add(portKey);carriers.add(component.nodes());
            double volume=component.nodes().stream().mapToDouble(LoadedPipeComponent.Node::volume).sum();
            if(!Double.isFinite(volume)||volume<=0)throw new IllegalStateException("invalid carrier component volume at index "+i);
            (i<3?hotVolumes:coldVolumes).add(volume);
        }
        var hot=TegFueledFixturePlan.apportionFiniteMoles(TegContinuousFuelPlan.CARRIER_MOLES,hotVolumes);
        var cold=TegFueledFixturePlan.apportionFiniteMoles(TegContinuousFuelPlan.CARRIER_MOLES,coldVolumes);
        var seeds=new ArrayList<TrustedPipeFixtureInitialization.PortSeed>(7);
        for(int i=0;i<7;i++) {
            var key=selectedPorts.get(i);
            var node=carriers.get(i).stream().filter(n->n.key().equals(key)).findFirst()
                    .orElseThrow(()->new IllegalStateException("selected carrier machine port has no captured node"));
            double moles=i<3?hot.get(i):cold.get(i-3);
            var species=i<3?TegContinuousFuelPlan.HOT_CARRIER_GAS:TegContinuousFuelPlan.COLD_CARRIER_GAS;
            seeds.add(new TrustedPipeFixtureInitialization.PortSeed(key.position(),key.portId(),node.incarnation(),
                    new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(
                            Map.of(species,moles),TegContinuousFuelPlan.CARRIER_TEMPERATURE_K,node.volume())));
        }
        return List.copyOf(seeds);
    }

    private static UUID inletIdentity(List<AtmosMachineBlockEntity> machines,TegFueledFixturePlan.Layout plan,
                                      TegFueledFixturePlan.MachineKind kind,boolean north) {
        var planned=plan.machines().stream().filter(m->m.kind()==kind&&m.position().equals(north
                ?plan.electrical().northCirculator():plan.electrical().southCirculator())).findFirst().orElseThrow();
        var machine=machines.stream().filter(m->m.getBlockPos().equals(planned.position())).findFirst().orElseThrow();
        return machine.gasPorts().stream().filter(p->p.id().equals("inlet")).map(PipeGasPortHost.Port::incarnation).findFirst().orElse(null);
    }
}
