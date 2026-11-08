package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeComponent;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationGasFlow;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import java.util.function.Consumer;
import java.util.*;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegContinuousEngineGameTests {
    private static TegFixtureRegistryScope registryScope;

    private TegContinuousEngineGameTests() { }

    @BeforeBatch(batch="teg_continuous_engine")
    public static void setupBatch(ServerLevel level) { registryScope = TegFixtureRegistryScope.acquire(level); }

    @AfterBatch(batch="teg_continuous_engine")
    public static void teardownBatch(ServerLevel level) {
        if (registryScope != null) { registryScope.close(); registryScope = null; }
    }

    @GameTest(template="disposal_power_clear", templateNamespace="moonstation14", batch="teg_continuous_engine", timeoutTicks=3600)
    @GameTestTags({"teg-continuous-engine"})
    public static void ordinaryContinuousSupplyReachesPaidIgnition(GameTestHelper helper) {
        beginOperatingIgnition(helper,ctx->{TegContinuousFixtureTestSupport.quiesceOperatingHardware(ctx.observed());ctx.helper().succeed();});
    }

    record IgnitedFixture(GameTestHelper helper,ServerLevel level,BlockPos origin,PowerDeviceBlockEntity output,
                          FixtureChunkTickets.Lease lease,TegContinuousFixtureService.ObservedHardware observed) { }

    record StockSample(Map<GasType,Double> inventoryMoles,Map<GasType,Double> burnerMoles,double inventoryThermalJoules,
                       double burnerThermalJoules,double fuelFeedTemperatureKelvin,
                       double hotLoopThermalJoules,double coldLoopThermalJoules,
                       double oxygenProducedMoles,double plasmaProducedMoles,double minerThermalJoules,
                        Map<GasType,Double> exportedMoles,
                        Map<GasType,Double> hotCarrierMoles,Map<GasType,Double> coldCarrierMoles,
                        Map<AtmosphereService.ExteriorBoundary.Kind,Double> exportedThermalJoules,
                       double radiatorAmbientHeatJoules,double radiatorSpaceHeatJoules,
                         double allAmbientHeatJoules,double allSpaceHeatJoules,double tegGeneratedJoules,TegFixtureEnergyBalance.ConversionModel conversionModel,
                          Map<BlockPos,UUID> machineOwners,Map<PipeTopology.NodeKey,UUID> gasOwners,
                          Map<PipeTopology.NodeKey,Long> ownedNodeVersions,
                          Map<UUID,FinitePipeSpeciesAudit.NetworkCounter> ownedNetworkCounters,
                          TegCommittedCounterObservation committedCounters) {
        StockSample {
            inventoryMoles=Map.copyOf(inventoryMoles);burnerMoles=Map.copyOf(burnerMoles);exportedMoles=Map.copyOf(exportedMoles);
            hotCarrierMoles=Map.copyOf(hotCarrierMoles);coldCarrierMoles=Map.copyOf(coldCarrierMoles);
            exportedThermalJoules=Map.copyOf(exportedThermalJoules);
            machineOwners=Map.copyOf(machineOwners);gasOwners=Map.copyOf(gasOwners);
            ownedNodeVersions=Map.copyOf(ownedNodeVersions);
            ownedNetworkCounters=Map.copyOf(ownedNetworkCounters);
        }
    }

    static StockSample sampleStock(ServerLevel level,TegContinuousFixtureService.ObservedHardware observed) {
        var layout=observed.layout();
        Set<BlockPos> cells=new HashSet<>();
        for(var room:List.of(layout.fueled().roomInterior(),layout.fueled().coldRoomInterior(),layout.roomA(),layout.roomB()))
            for(BlockPos cell:room)if(!cells.add(cell))throw new IllegalStateException("owned room inventories overlap at "+cell);
        EnumMap<GasType,Double> moles=new EnumMap<>(GasType.class);
        EnumMap<GasType,Double> burnerMoles=new EnumMap<>(GasType.class);
        for(GasType gas:GasType.values())moles.put(gas,0d);
        for(GasType gas:GasType.values())burnerMoles.put(gas,0d);
        double thermal=0,burnerThermal=0;
        for(BlockPos cell:cells) {
            GasMixture mixture=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot();
            mixture.gasMoles().forEach((gas,value)->moles.merge(gas,value,Double::sum));thermal+=mixture.thermalEnergy();
            if(layout.fueled().roomInterior().contains(cell)){burnerThermal+=mixture.thermalEnergy();mixture.gasMoles().forEach((gas,value)->burnerMoles.merge(gas,value,Double::sum));}
        }
        for(var component:observed.capture().components().values()) {
            var mixture=component.aggregate();mixture.gasMoles().forEach((gas,value)->moles.merge(gas,value,Double::sum));thermal+=mixture.thermalEnergy();
        }
        var ownedNodeVersions=new LinkedHashMap<PipeTopology.NodeKey,Long>();
        var ownedNetworkCounters=new LinkedHashMap<UUID,FinitePipeSpeciesAudit.NetworkCounter>();
        for(var component:observed.capture().components().values()) {
            var network=Objects.requireNonNull(component.network(),"captured network metadata missing");
            var capturedMembers=component.nodes().stream().map(LoadedPipeComponent.Node::key).collect(java.util.stream.Collectors.toSet());
            if(!capturedMembers.equals(network.members()))
                throw new IllegalStateException("captured pipe node roster differs from network membership: "+network.id());
            if(ownedNetworkCounters.putIfAbsent(network.id(),new FinitePipeSpeciesAudit.NetworkCounter(
                    network.topologyGeneration(),network.members(),network.gasRevision()))!=null)
                throw new IllegalStateException("duplicate captured pipe network: "+network.id());
        }
        for(var component:observed.capture().components().values()) for(var node:component.nodes()) {
            var entry=node.entry();
            if(entry==null||!entry.active()||!entry.incarnation().equals(node.incarnation())||!entry.node().equals(node.key()))
                throw new IllegalStateException("owned pipe capture entry identity is invalid at "+node.key());
            if(ownedNodeVersions.putIfAbsent(node.key(),entry.version())!=null)
                throw new IllegalStateException("duplicate owned pipe node in capture: "+node.key());
        }
        if(!ownedNodeVersions.keySet().equals(observed.gasOwners().keySet()))
            throw new IllegalStateException("captured pipe version keys differ from owned gas keys");
        double hot=0,cold=0;
        EnumMap<GasType,Double> hotMoles=new EnumMap<>(GasType.class),coldMoles=new EnumMap<>(GasType.class);
        for(GasType gas:GasType.values()){hotMoles.put(gas,0d);coldMoles.put(gas,0d);}
        for(int i=0;i<7;i++) {
            var component=observed.capture().componentFor(observed.topology().representatives().get(i)).orElseThrow();
            var mixture=component.aggregate();var inventory=i<3?hotMoles:coldMoles;
            mixture.gasMoles().forEach((gas,value)->inventory.merge(gas,value,Double::sum));
            if(i<3)hot+=mixture.thermalEnergy();else cold+=mixture.thermalEnergy();
        }
        double oxygen=0,plasma=0,minerHeat=0;
        for(GasMinerBlockEntity miner:observed.placement().additional().miners()) {
            var profile=Objects.requireNonNull(miner.resolvedProfile(),"owned miner profile missing");
            switch(profile.gas()) {case OXYGEN->oxygen+=miner.producedMoles();case PLASMA->plasma+=miner.producedMoles();default->{}}
            minerHeat+=miner.producedThermalJoules();
        }
        VentilationDeviceBlockEntity vent=observed.placement().additional().ventilation().stream()
                .filter(v->v.getBlockPos().equals(layout.wasteVentBody())).findFirst().orElseThrow(()->new IllegalStateException("owned waste vent missing"));
        if(level.getBlockEntity(vent.getBlockPos())!=vent)throw new IllegalStateException("owned waste vent missing");
        Map<GasType,Double> exported=Map.copyOf(vent.exportObservation().speciesMoles());
        Map<AtmosphereService.ExteriorBoundary.Kind,Double> exportedHeat=Map.copyOf(vent.exportObservation().thermalJoulesByBoundary());
        var hardware=observed.placement();
        List<AtmosMachineBlockEntity> hosts=new ArrayList<>();hosts.add(hardware.core());hosts.addAll(hardware.baseMachines());hosts.addAll(hardware.additional().atmosMachines());
        double ambient=0,space=0,radiatorAmbient=0,radiatorSpace=0;int radiators=0;
        for(var host:hosts) {
            ambient+=host.ambientHeatReceiptJoules();space+=host.spaceHeatReceiptJoules();
            if(layout.radiatorPositions().contains(host.getBlockPos())) {radiatorAmbient+=host.ambientHeatReceiptJoules();radiatorSpace+=host.spaceHeatReceiptJoules();radiators++;}
        }
        if(radiators!=layout.radiatorPositions().size())throw new IllegalStateException("expected all owned radiator hosts: expected="+layout.radiatorPositions().size()+" found="+radiators);
        double fuelFeedTemperature=sampleFeedline(observed).temperatureKelvin();
         var teg=Objects.requireNonNull(hardware.core().resolvedProfile(),"resolved core profile missing").teg();if(teg==null)throw new IllegalStateException("resolved core profile has no TEG conversion coefficients");
        var conversionModel=new TegFixtureEnergyBalance.ConversionModel(teg.efficiency(),teg.powerFactor());
          var counterOwners=new LinkedHashMap<String,TegCommittedCounterObservation.OwnerCounter>();
          var machinesByPos=new HashMap<BlockPos,AtmosMachineBlockEntity>();
           hosts.forEach(machine->machinesByPos.put(machine.getBlockPos(),machine));
           BlockPos hotPumpPosition=TegContinuousOperatingPlan.carrierPumpPositions(layout.fueled()).getFirst();
           AtmosMachineBlockEntity hotPump=machinesByPos.get(hotPumpPosition);
            if(hotPump==null||hotPump.resolvedProfile()==null||(hotPump.resolvedProfile().kind()!=AtmosMachineProfile.Kind.VOLUME_PUMP&&hotPump.resolvedProfile().kind()!=AtmosMachineProfile.Kind.PRESSURE_PUMP))
                throw new IllegalStateException("owned hot carrier pump missing or wrong resolved profile at "+hotPumpPosition);
            counterOwners.put("hotCarrierPump",counter(hotPump));
          int hotIndex=0;
          for(BlockPos position:layout.hotExchangers()) {
              AtmosMachineBlockEntity machine=machinesByPos.get(position);
              if(machine==null)throw new IllegalStateException("owned hot exchanger missing at "+position);
              counterOwners.put("hotHX"+(++hotIndex),counter(machine));
          }
          for(var machine:hosts)if(machine.resolvedProfile()!=null&&machine.resolvedProfile().kind()==AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER
                  &&!layout.hotExchangers().contains(machine.getBlockPos())&&!layout.radiatorPositions().contains(machine.getBlockPos()))
              counterOwners.put("coldHX",counter(machine));
          for(BlockPos position:layout.radiatorPositions()) {
              AtmosMachineBlockEntity machine=machinesByPos.get(position);
              if(machine==null)throw new IllegalStateException("owned space radiator missing at "+position);
              counterOwners.put("spaceRadiator:"+position.getX()+":"+position.getY()+":"+position.getZ(),counter(machine));
          }
           var injectorPos=layout.devicePosition(TegContinuousFuelPlan.DeviceRole.INJECTOR);
           var injector=hardware.additional().ventilation().stream().filter(v->v.getBlockPos().equals(injectorPos)).findFirst().orElseThrow();
           var wasteExtractionPos=layout.devicePosition(TegContinuousFuelPlan.DeviceRole.WASTE_PUMP);
           var waste=hardware.additional().ventilation().stream().filter(v->v.getBlockPos().equals(wasteExtractionPos)).findFirst().orElseThrow(()->new IllegalStateException("owned burner waste extraction pump missing"));
           if(level.getBlockEntity(wasteExtractionPos)!=waste)throw new IllegalStateException("owned burner waste extraction pump missing");
            counterOwners.put("burnerInjector",new TegCommittedCounterObservation.OwnerCounter(injectorPos,injector,injector.transferObservation().thermalJoules(),0,injector.transferObservation().speciesMoles()));
            counterOwners.put("burnerWasteExtraction",new TegCommittedCounterObservation.OwnerCounter(wasteExtractionPos,waste,waste.transferObservation().thermalJoules(),0,waste.transferObservation().speciesMoles()));
             return new StockSample(moles,burnerMoles,thermal,burnerThermal,fuelFeedTemperature,hot,cold,oxygen,plasma,minerHeat,exported,hotMoles,coldMoles,exportedHeat,
                  radiatorAmbient,radiatorSpace,ambient,space,hardware.core().tegRuntimeState().cumulativeGeneratedJoules(),conversionModel,
                  observed.machineOwners(),observed.gasOwners(),ownedNodeVersions,ownedNetworkCounters,new TegCommittedCounterObservation(counterOwners));
    }

    static String stockSummary(StockSample s){return "species="+s.inventoryMoles()+" thermal="+s.inventoryThermalJoules()+" hot="+s.hotLoopThermalJoules()+" burner="+s.burnerThermalJoules()+" feedK="+s.fuelFeedTemperatureKelvin()+" cold="+s.coldLoopThermalJoules()+" minerProductionO/P="+s.oxygenProducedMoles()+"/"+s.plasmaProducedMoles()+" minerHeat="+s.minerThermalJoules()+" exportSpecies="+s.exportedMoles()+" exportHeat="+s.exportedThermalJoules()+" radiatorAmbient/space="+s.radiatorAmbientHeatJoules()+"/"+s.radiatorSpaceHeatJoules()+" allAmbient/space="+s.allAmbientHeatJoules()+"/"+s.allSpaceHeatJoules()+" committedGen="+s.tegGeneratedJoules()+" ownerCounts="+s.machineOwners().size()+"/"+s.gasOwners().size();}

    private static TegCommittedCounterObservation.OwnerCounter counter(AtmosMachineBlockEntity machine) {
        return new TegCommittedCounterObservation.OwnerCounter(machine.getBlockPos(),machine,machine.committedThermalJoules(),machine.committedMovedMoles(),Map.of());
    }

    static void beginOperatingIgnition(GameTestHelper helper,Consumer<IgnitedFixture> afterIgnition) {
        beginOperatingIgnition(helper,(ServerLevel)helper.getLevel(),afterIgnition);
    }

    static void beginOperatingIgnition(GameTestHelper helper,ServerLevel level,Consumer<IgnitedFixture> afterIgnition) {
        beginOperatingIgnition(helper,level,afterIgnition,false);
    }

    static void beginAutomaticOperatingStartup(GameTestHelper helper,ServerLevel level,Consumer<IgnitedFixture> afterStartup) {
        beginOperatingIgnition(helper,level,afterStartup,true);
    }

    private static void beginOperatingIgnition(GameTestHelper helper,ServerLevel level,Consumer<IgnitedFixture> afterIgnition,boolean automatic) {
        BlockPos template=helper.absolutePos(BlockPos.ZERO),far=template.offset(48,8,48);
        var catalog=PrototypeRuntime.serverVentilationDevices();
        var vent=catalog.get(ModVentilationDevices.VENT);var scrubber=catalog.get(ModVentilationDevices.SCRUBBER);
        if(vent==null||scrubber==null)throw new GameTestAssertException("resolved vent receiver prototypes missing");
        int reach=Math.max(vent.extensionCableReceiver().localReachBlocks(),scrubber.extensionCableReceiver().localReachBlocks());
        var lease=FixtureChunkTickets.acquire(helper,level,new AABB(template.getX()-reach,template.getY()-reach,template.getZ()-reach,far.getX()+reach,far.getY()+reach,far.getZ()+reach));
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,lease::levelAwake,"continuous engine footprint entity-ticking and native dimension wake anchor",()->{
            BlockPos origin=null;
            try {
                origin=TegContinuousFixtureTestSupport.prepareOutdoorFootprint(level,template).origin();
                String built;
                if(automatic) {
                    var command=TegPublicCommandTestSupport.execute(level,"ms14 teg_fixture build "+origin.getX()+" "+origin.getY()+" "+origin.getZ());
                    require(command.returnCode()==1&&!command.messages().isEmpty()&&command.messages().getFirst().startsWith("Built continuous TEG hardware"),
                            "public permission-2 coordinate build command result/feedback: "+command);
                    built=command.messages().getFirst();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_PUBLIC_COMMAND marker=automatic-build origin={} returnCode={} feedback={}",origin,command.returnCode(),command.messages());
                } else {
                    built=TegContinuousFixtureService.build(level,origin);
                    require(built.startsWith("Built continuous TEG hardware"),"continuous build: "+built);
                }
                var registry=TegFixtureSavedData.existing(level).orElseThrow();
                var entry=registry.entry(origin).orElseThrow();
                require(registry.count()==1&&registry.count()<=TegFixtureSavedData.MAX_FIXTURES,"one scoped fixture owner within MAX8");
                require(entry.mode()==TegFixtureSavedData.Mode.CONTINUOUS&&!entry.initialized()&&entry.phase()==TegFixtureSavedData.ContinuousPhase.BUILT,
                        "public build persists same explicit origin as an uninitialized continuous BUILT entry: "+entry);
                java.util.UUID constructionToken=entry.token();
                var observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                var output=(PowerDeviceBlockEntity)level.getBlockEntity(observed.layout().fueled().electrical().devices().outputApc());
                  if(automatic) {
                      var command=TegPublicCommandTestSupport.execute(level,"ms14 teg_fixture start "+origin.getX()+" "+origin.getY()+" "+origin.getZ());
                      require(command.returnCode()==1&&command.messages().contains("TEG startup started."),"public permission-2 coordinate start command result/feedback: "+command);
                      var startedEntry=registry.entry(origin).orElseThrow();
                      require(startedEntry.token().equals(constructionToken)&&startedEntry.mode()==TegFixtureSavedData.Mode.CONTINUOUS,
                              "public start retains the exact build origin/token/mode");
                      require(registry.automaticStartup(origin).filter(a->a.status()==TegFixtureSavedData.AutomaticStartupStatus.WAITING_START).isPresent(),
                              "public start persists automatic startup admission at the same origin");
                      com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_PUBLIC_COMMAND marker=automatic-start origin={} token={} mode={} returnCode={} feedback={}",origin,constructionToken,startedEntry.mode(),command.returnCode(),command.messages());
                        pollAutomaticStartup(helper,level,origin,output,lease,constructionToken,helper.getTick()+3600,afterIgnition,java.util.EnumSet.noneOf(TegFixtureSavedData.ContinuousPhase.class),java.util.EnumSet.noneOf(TegFixtureSavedData.AutomaticStartupStatus.class));
                  } else awaitStartup(helper,level,origin,output,helper.getTick()+300,lease,afterIgnition);
            } catch(Throwable failure) { cleanupFailure(level,origin,lease,failure);logEngineFailure("setup",failure);helper.fail(TegContinuousFixtureTestSupport.boundedFailureMessage("continuous engine setup failed: "+failure)); }
        },template);
    }

    private static void pollAutomaticStartup(GameTestHelper helper,ServerLevel level,BlockPos origin,PowerDeviceBlockEntity output,
            FixtureChunkTickets.Lease lease,java.util.UUID expectedToken,long deadline,Consumer<IgnitedFixture> afterStartup,java.util.Set<TegFixtureSavedData.ContinuousPhase> phases,java.util.Set<TegFixtureSavedData.AutomaticStartupStatus> observedStatuses) {
        helper.runAfterDelay(1,()->{
            TegContinuousFixtureService.ObservedHardware observed=null;
            try {
                var saved=TegFixtureSavedData.existing(level).orElseThrow();
                var entry=saved.entry(origin).orElseThrow();
                require(entry.token().equals(expectedToken)&&entry.mode()==TegFixtureSavedData.Mode.CONTINUOUS,"automatic lifecycle retained original public-build origin/token/mode");
                observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                phases.add(entry.phase());
                var automatic=saved.automaticStartup(origin).orElseThrow();
                var radiatorPositions=observed.layout().radiatorPositions();
                if (automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD && observedStatuses.add(automatic.status())) {
                    var radiators=observed.placement().additional().atmosMachines().stream()
                            .filter(m->radiatorPositions.contains(m.getBlockPos())).toList();
                    if(radiators.size()!=radiatorPositions.size())throw new GameTestAssertException("cold prep radiator owners differ from typed array");
                    double receipts=radiators.stream().mapToDouble(m->m.ambientHeatReceiptJoules()+m.spaceHeatReceiptJoules()).sum();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("{}",String.format(java.util.Locale.ROOT,"TEG cold preparation admitted: loop temperature %.2f K; radiator heat receipt %.2f J",
                            TegContinuousFixtureService.automaticCarrierTemperature(level,observed),receipts));
                }
                if (automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.ACTIVE && entry.phase()==TegFixtureSavedData.ContinuousPhase.STARTUP && observedStatuses.add(automatic.status())) {
                    var radiators=observed.placement().additional().atmosMachines().stream()
                            .filter(m->radiatorPositions.contains(m.getBlockPos())).toList();
                    if(radiators.size()!=radiatorPositions.size())throw new GameTestAssertException("completed prep radiator owners differ from typed array");
                    double receipts=radiators.stream().mapToDouble(m->m.ambientHeatReceiptJoules()+m.spaceHeatReceiptJoules()).sum();
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("{}",String.format(java.util.Locale.ROOT,"TEG cold preparation complete: loop temperature %.2f K; radiator heat receipt %.2f J",
                            TegContinuousFixtureService.automaticCarrierTemperature(level,observed),receipts));
                }
                if(helper.getTick()%100==0) {
                    var frame=TegContinuousFixtureService.matchedPowerFrame(level,observed,Long.MIN_VALUE,entry.phase()!=TegFixtureSavedData.ContinuousPhase.HEATER_OFF);
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG automatic native phase snapshot helperTick={} worldTick={} status={} phase={} apc={} stock={}",
                             helper.getTick(),level.getGameTime(),automatic.status(),entry.phase(),frame.output(),stockSummary(sampleStock(level,observed)));
                    logBurn(helper.getTick(),observed,level);logCarrierThermal(helper.getTick(),observed,level);
                    if(automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.WAITING_START)
                        logAutomaticAdmissionDiagnostics(helper.getTick(),level,observed,lease);
                }
                if(automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.FAILED)
                    throw new GameTestAssertException("automatic controller failed: "+automatic.failureReason());
                if(entry.phase()==TegFixtureSavedData.ContinuousPhase.HEATER_OFF&&automatic.status()==TegFixtureSavedData.AutomaticStartupStatus.COMPLETE) {
                    require(phases.containsAll(java.util.EnumSet.of(TegFixtureSavedData.ContinuousPhase.STARTUP,TegFixtureSavedData.ContinuousPhase.IGNITION,
                            TegFixtureSavedData.ContinuousPhase.RUNNING,TegFixtureSavedData.ContinuousPhase.HEATER_OFF)),"automatic controller traversed all saved phases: "+phases);
                    require(output!=null&&entry.phaseStartedTick()>=0,"automatic startup phase persisted");
                    var beforeStatus=TegFixtureSavedData.existing(level).orElseThrow().entry(origin).orElseThrow();
                    var statusCommand=TegPublicCommandTestSupport.execute(level,"ms14 teg_fixture status "+origin.getX()+" "+origin.getY()+" "+origin.getZ());
                    require(statusCommand.returnCode()==1&&statusCommand.messages().size()==1,"public status succeeded with one captured feedback: "+statusCommand);
                    String statusBody=statusCommand.messages().getFirst();
                    require(statusBody.contains("CONTINUOUS phase=HEATER_OFF initialized=true"),"public status reports persisted HEATER_OFF initialized fixture: "+statusBody);
                    var afterStatus=TegFixtureSavedData.existing(level).orElseThrow().entry(origin).orElseThrow();
                    require(beforeStatus.equals(afterStatus)&&beforeStatus.token().equals(afterStatus.token()),"read-only public status preserves exact origin/token/persisted entry");
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_PUBLIC_COMMAND marker=heater-off-status origin={} token={} returnCode={} feedback={} readOnlyEntryUnchanged=true",origin,entry.token(),statusCommand.returnCode(),statusCommand.messages());
                    afterStartup.accept(new IgnitedFixture(helper,level,origin,output,lease,observed));return;
                }
                if(helper.getTick()>=deadline)throw new GameTestAssertException("automatic startup exceeded original test observation deadline; phase="+entry.phase()+" status="+automatic.status());
                  pollAutomaticStartup(helper,level,origin,output,lease,expectedToken,deadline,afterStartup,phases,observedStatuses);
            } catch(Throwable failure) {
                var saved=TegFixtureSavedData.existing(level).orElse(null);
                if(saved!=null)saved.automaticStartup(origin).ifPresent(a->saved.updateAutomaticStartup(origin,a.status(),TegFixtureSavedData.AutomaticStartupStatus.FAILED,"native automatic startup test failed"));
                try { if(observed!=null)TegContinuousFixtureTestSupport.quiesceOperatingHardware(observed); } catch(Throwable cleanup){failure.addSuppressed(cleanup);}
                try { lease.close(); } catch(Throwable cleanup){failure.addSuppressed(cleanup);}
                try {
                    com.juicyslew.moonstation14.MoonStation14.LOGGER.error("TEG automatic native consumer failure status={} phase={} operatingStatus={} powerSample={} measurements={}",
                            saved==null?"missing":saved.automaticStartup(origin).map(a->a.status()+":"+a.failureReason()).orElse("missing"),
                            saved==null?"missing":saved.entry(origin).map(e->e.phase()+"@"+e.phaseStartedTick()).orElse("missing"),
                            TegContinuousFixtureService.operatingStatus(level,origin),PowerRuntime.currentSampleTick(level),
                             observed==null?"unavailable":TegContinuousEngineGameTests.stockSummary(TegContinuousEngineGameTests.sampleStock(level,observed)));
                    if(observed!=null){TegContinuousEngineGameTests.logBurn(helper.getTick(),observed,level);TegContinuousEngineGameTests.logCarrierThermal(helper.getTick(),observed,level);}
                } catch(Throwable diagnostic){failure.addSuppressed(diagnostic);}
                logEngineFailure("automatic startup observation",failure);
                helper.fail(TegContinuousFixtureTestSupport.boundedFailureMessage("automatic TEG startup failed: "+failure.getMessage()));
            }
        });
    }

    /** Read-only graph admission evidence, emitted only on the existing WAITING_START cadence. */
    private static void logAutomaticAdmissionDiagnostics(long helperTick,ServerLevel level,
            TegContinuousFixtureService.ObservedHardware observed,FixtureChunkTickets.Lease lease){
        var layout=observed.layout();var electrical=layout.fueled().electrical();
        var positions=new java.util.LinkedHashSet<BlockPos>();
        positions.add(layout.fueled().core());
        positions.add(electrical.devices().startupApc());positions.add(electrical.devices().outputApc());
        positions.add(electrical.devices().substation());
        observed.placement().additional().atmosMachines().stream().limit(2).map(AtmosMachineBlockEntity::getBlockPos).forEach(positions::add);
        var footprints=new java.util.ArrayList<String>();
        for(BlockPos pos:positions.stream().limit(6).toList())
            footprints.add(pos+"="+PowerGraphService.diagnosticSnapshot(level,pos,4,CableTier.MV));
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error("TEG_AUTOMATIC_START admission-waiting helperTick={} worldTick={} lease={} ready={} generation={} global={} footprints={}",
                helperTick,level.getGameTime(),lease,PowerGraphService.topologyReady(level),PowerGraphService.topologyGeneration(level),
                PowerGraphService.diagnosticSnapshot(level),footprints);
    }

    private static void awaitStartup(GameTestHelper helper,ServerLevel level,BlockPos origin,
                                     PowerDeviceBlockEntity output,long deadline,FixtureChunkTickets.Lease lease,Consumer<IgnitedFixture> afterIgnition) {
        helper.runAfterDelay(1,()->{
            try {
                var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                var observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                if(!entry.initialized()) {
                    String pending=TegContinuousFixtureService.startupPowerFailure(level,observed);
                    if(pending==null) {
                        String status=TegContinuousFixtureService.start(level,origin);
                        entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                        pending=TegContinuousFixtureService.startupPowerFailure(level,observed);
                        if(!entry.initialized()&&pending==null)throw new GameTestAssertException("operating initialization rejected: "+status);
                    }
                    entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                    if(!entry.initialized()) {
                    if(helper.getTick()>=deadline)throw new GameTestAssertException("operating startup did not admit within 300 ticks: "+pending);
                         awaitStartup(helper,level,origin,output,deadline,lease,afterIgnition);return;
                    }
                }
                observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                var bootstrap=TegContinuousFixtureService.startupBootstrapSplit(observed);
                var auxiliary=(PowerDeviceBlockEntity)level.getBlockEntity(TegContinuousPowerPlan.equipment(observed.layout()).secondaryStartupApc());
                require(output!=null&&auxiliary!=null&&output.energyJoules()==bootstrap.operatingJoules()
                                &&auxiliary.energyJoules()==bootstrap.secondaryJoules(),
                        "both startup APCs immediately contain their resolved proportional shares");
                require(Math.abs(output.energyJoules()+auxiliary.energyJoules()-TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES)<=1e-6,
                        "startup stores sum to the original 1 MJ total");
                require(observed!=null&&observed.machineOwners().equals(entry.ownedMachines())
                        &&observed.gasOwners().equals(entry.ownedGas()),"owned hardware observation validates fixture ownership");
                var initial=sampleIgnition(level,observed,output);
                require(initial.burnerCo2()+initial.pipeCo2()+initial.exportedCo2()<=1e-6,"ignition starts with no CO2");
                logIgnition(0,initial,false,false,0);
                  pollIgnition(helper,level,new IgnitionRun(initial,origin,output,lease,helper.getTick()+2400,helper.getTick(),false,false,false,observed),afterIgnition);
            } catch(Throwable failure) { cleanupFailure(level,origin,lease,failure);logEngineFailure("startup proof",failure);helper.fail(TegContinuousFixtureTestSupport.boundedFailureMessage("continuous engine startup proof failed: "+failure)); }
        });
    }

    record IgnitionSample(double oxygenProduced,double plasmaProduced,double apcJoules,
                                  boolean paidHeaterWork,String heaterReceipts,double burnerOxygen,double burnerPlasma,
                                  double burnerCo2,double pipeCo2,double exportedCo2,
                                    double maximumBurnerKelvin,double meanBurnerKelvin,long powerSampleTick,long worldTick) { }

    record BurnSample(int cells,double minKelvin,double meanKelvin,double maxKelvin,
                      double minPressureKpa,double meanPressureKpa,double maxPressureKpa,
                      double oxygenMoles,double plasmaMoles,double co2Moles,
                      double minCellOxygen,double maxCellOxygen,double minCellPlasma,double maxCellPlasma,
                      int cellsWithPlasmaFire,double hypotheticalOnePassPlasmaMoles,double hypotheticalOnePassHeatJoules) { }

    record FeedlineSample(double pressureKpa,double temperatureKelvin,double oxygenMoles,
                          double plasmaMoles,double carbonDioxideMoles,double totalMoles,double physicalVolumeM3) { }

    static FeedlineSample sampleFeedline(TegContinuousFixtureService.ObservedHardware observed) {
        var mixer=observed.placement().additional().atmosMachines().stream()
                .filter(m->m.resolvedProfile()!=null&&m.resolvedProfile().kind()==AtmosMachineProfile.Kind.MIXER)
                .findFirst().orElseThrow(()->new IllegalStateException("owned mixer missing"));
        var outlet=mixer.gasPorts().stream().filter(p->p.id().equals("outlet")).findFirst()
                .orElseThrow(()->new IllegalStateException("resolved mixer outlet port missing"));
        var component=TegContinuousFixtureService.capturedComponentForPort(observed.topology(), observed.capture(),
                new PipeTopology.NodeKey(mixer.getBlockPos(),outlet.layer(),outlet.id()))
                .orElseThrow(()->new IllegalStateException("mixer outlet component missing from actual capture"));
        var gas=component.aggregate();
        double volume=gas.volumeCubicMeters();
        return new FeedlineSample(gas.pressureKpa(volume),gas.temperatureKelvin(),gas.moles(GasType.OXYGEN),
                gas.moles(GasType.PLASMA),gas.moles(GasType.CARBON_DIOXIDE),gas.totalMoles(),volume);
    }

    static BurnSample sampleBurn(ServerLevel level,TegContinuousFixtureService.ObservedHardware observed) {
        double minK=Double.POSITIVE_INFINITY,maxK=Double.NEGATIVE_INFINITY,sumK=0;
        double minPressure=Double.POSITIVE_INFINITY,maxPressure=Double.NEGATIVE_INFINITY,sumPressure=0;
        double oxygen=0,plasma=0,co2=0,minO=Double.POSITIVE_INFINITY,maxO=0,minP=Double.POSITIVE_INFINITY,maxP=0;
        int count=0,plasmaEvents=0;double hypotheticalPlasma=0,hypotheticalHeat=0;
        var catalog=PrototypeRuntime.serverGasReactions();
        for(BlockPos cell:observed.layout().fueled().roomInterior()) {
            GasMixture detached=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot();
            double t=detached.temperatureKelvin(),o=detached.moles(GasType.OXYGEN),p=detached.moles(GasType.PLASMA);
            double pressure=detached.pressureKpa(1);
            minK=Math.min(minK,t);maxK=Math.max(maxK,t);sumK+=t;
            minPressure=Math.min(minPressure,pressure);maxPressure=Math.max(maxPressure,pressure);sumPressure+=pressure;
            oxygen+=o;plasma+=p;co2+=detached.moles(GasType.CARBON_DIOXIDE);
            minO=Math.min(minO,o);maxO=Math.max(maxO,o);minP=Math.min(minP,p);maxP=Math.max(maxP,p);count++;
            GasReactionEvaluator.Result result=GasReactionEvaluator.evaluate(catalog,detached);
            for(var event:result.events())if(event.effect()==GasReactionData.EffectType.PLASMA_FIRE) {
                plasmaEvents++;
                hypotheticalPlasma+=Math.max(0,-event.speciesDelta().getOrDefault(GasType.PLASMA,0.0));
                hypotheticalHeat+=event.energyDeltaJoules();
            }
        }
        if(count!=27)throw new IllegalStateException("expected 27 burner finite cells, found "+count);
        return new BurnSample(count,minK,sumK/count,maxK,minPressure,sumPressure/count,maxPressure,oxygen,plasma,co2,minO,maxO,minP,maxP,
                plasmaEvents,hypotheticalPlasma,hypotheticalHeat);
    }

    private record IgnitionRun(IgnitionSample initial,BlockPos origin,PowerDeviceBlockEntity output,
                               FixtureChunkTickets.Lease lease,long deadline,long startTick,
                               boolean paidSeen,boolean reactantsSeen,boolean fuelAdmitted,TegContinuousFixtureService.ObservedHardware observed) { }

    private static void pollIgnition(GameTestHelper helper,ServerLevel level,IgnitionRun run,Consumer<IgnitedFixture> afterIgnition) {
        helper.runAfterDelay(1,()->{
            var currentSample=run.initial();var currentObserved=run.observed();
            try {
                var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(run.origin())).orElseThrow();
                currentObserved=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                currentSample=sampleIgnition(level,currentObserved,run.output());
                boolean paid=run.paidSeen()||currentSample.paidHeaterWork();
                boolean reactants=run.reactantsSeen()||(currentSample.burnerOxygen()>1e-6&&currentSample.burnerPlasma()>1e-6);
                long ticks=helper.getTick()-run.startTick();
                boolean admitted=run.fuelAdmitted();
                if(!admitted) {
                    if(currentSample.burnerOxygen()!=0||currentSample.burnerPlasma()!=0||currentSample.burnerCo2()!=0)
                        throw new GameTestAssertException("preheat chamber received fuel before operator admission");
                    String response=TegContinuousFixtureService.ignite(level,run.origin());
                    if(response.startsWith("Ignition admitted"))admitted=true;
                    else if(!response.startsWith("Ignition not ready"))throw new GameTestAssertException("production ignition operation refused: "+response);
                } else {
                    if(TegFixtureSavedData.existing(level).flatMap(d->d.entry(run.origin())).orElseThrow().phase()!=TegFixtureSavedData.ContinuousPhase.IGNITION)
                        throw new GameTestAssertException("production fixture phase left IGNITION before reaction proof");
                    var ignitionObserved=currentObserved;
                    var cold= ignitionObserved.placement().baseMachines().stream().filter(p->p.resolvedProfile()!=null&&p.resolvedProfile().kind()==AtmosMachineProfile.Kind.VOLUME_PUMP
                            &&TegContinuousOperatingPlan.pumpRole(ignitionObserved.layout().fueled(),p.getBlockPos())==TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP).findFirst().orElseThrow();
                     var ignitionPlan=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,cold.resolvedProfile(),cold.settingsSnapshot(),TegContinuousOperatingPlan.Stage.IGNITION);
                     var supplyPosition=ignitionObserved.layout().devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.INJECTOR).findFirst().orElseThrow().position();
                     var supply=ignitionObserved.placement().additional().ventilation().stream().filter(v->v.getBlockPos().equals(supplyPosition)).findFirst().orElseThrow();
                       if(!cold.settingsSnapshot().equals(ignitionPlan.finalValues())||!cold.settingsSnapshot().enabled().orElse(false)
                              ||com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePowerState.apcDemand(cold.resolvedProfile(),cold.settingsSnapshot().enabled().orElse(false))!=200
                              ||!supply.config().settings().configuredEnabled())throw new GameTestAssertException(
                             "IGNITION must keep cold circulation enabled while retaining its paid 200 W receiver: actual="+cold.settingsSnapshot()+", expected="+ignitionPlan.finalValues()
                                     +", requests="+ignitionPlan.requests()+", expectedInitialRevision="+ignitionPlan.expectedInitialRevision());
                      var runningPlan=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,cold.resolvedProfile(),cold.settingsSnapshot(),TegContinuousOperatingPlan.Stage.RUNNING);
                      if(!runningPlan.finalValues().enabled().orElse(false))throw new GameTestAssertException("RUNNING proposal must enable cold circulation");
                }
                double newCo2=currentSample.burnerCo2()+currentSample.pipeCo2()+currentSample.exportedCo2()
                        -run.initial().burnerCo2()-run.initial().pipeCo2()-run.initial().exportedCo2();
                 if(ticks%100==0) {
                      logIgnition(ticks,currentSample,paid,reactants,newCo2);
                      logBurn(ticks,currentObserved,level);
                      logCarrierThermal(ticks,currentObserved,level);
                      com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CONTINUOUS_STARTUP_STATUS elapsedTicks={} status={}",ticks,TegContinuousFixtureService.operatingStatus(level,run.origin()));
                 }
                if(admitted&&paid&&reactants&&currentSample.oxygenProduced()>run.initial().oxygenProduced()
                        &&currentSample.plasmaProduced()>run.initial().plasmaProduced()
                        &&currentSample.apcJoules()<1_000_000&&newCo2>1e-4&&currentSample.maximumBurnerKelvin()>293.16) {
                    logIgnition(ticks,currentSample,paid,reactants,newCo2);
                     afterIgnition.accept(new IgnitedFixture(helper,level,run.origin(),run.output(),run.lease(),currentObserved));return;
                }
                if(helper.getTick()>=run.deadline())throw new GameTestAssertException("ignition deadline; paid="+paid+" reactants="+reactants+" newCo2="+newCo2+" sample="+currentSample);
                  pollIgnition(helper,level,new IgnitionRun(run.initial(),run.origin(),run.output(),run.lease(),run.deadline(),run.startTick(),paid,reactants,admitted,currentObserved),afterIgnition);
            } catch(Throwable failure) {
                try { TegContinuousFixtureTestSupport.quiesceOperatingHardware(currentObserved); } catch(Throwable ignored) { }
                run.lease().close();logIgnition(helper.getTick()-run.startTick(),currentSample,run.paidSeen(),run.reactantsSeen(),Double.NaN);
                 try { logBurn(helper.getTick()-run.startTick(),currentObserved,level); } catch(Throwable ignored) { }
                  try { logCarrierThermal(helper.getTick()-run.startTick(),currentObserved,level); } catch(Throwable ignored) { }
                 try { com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CONTINUOUS_STARTUP_FAILURE_STATUS status={}",TegContinuousFixtureService.operatingStatus(level,run.origin())); } catch(Throwable ignored) { }
                logEngineFailure("ignition",failure);
                helper.fail(TegContinuousFixtureTestSupport.boundedFailureMessage("continuous ignition failed: "+failure));
            }
        });
    }

    private static void logIgnition(long ticks,IgnitionSample sample,boolean paid,boolean reactants,double newCo2) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CONTINUOUS_IGNITION phaseTicks={} worldTick={} privatePowerSampleTick={} paidSeen={} reactantsSeen={} newCo2={} sample={}",
                ticks,sample.worldTick(),sample.powerSampleTick(),paid,reactants,newCo2,sample);
    }

    private static void logEngineFailure(String phase, Throwable failure) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                "TEG continuous engine GameTest failed: phase={} reason={}", phase, failure.toString(), failure);
    }

    static void logBurn(long ticks,TegContinuousFixtureService.ObservedHardware observed,ServerLevel level) {
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "TEG_BURN_SAMPLE phaseTicks={} worldTick={} actualCommitted=NOT_REPORTED detachedEvaluation=HYPOTHETICAL_ONE_PASS snapshot={} actualMixerOutletFeedline={} (fuelPipeSnapshot; not actual flow rate or input-mixture assumption)",
                ticks,level.getGameTime(),sampleBurn(level,observed),sampleFeedline(observed));
        logMinerSources(ticks,observed,level);
    }

    /** Bounded, read-only carrier snapshots; the TEG runtime fields describe only its last committed event. */
    static void logCarrierThermal(long phaseTicks,TegContinuousFixtureService.ObservedHardware observed,ServerLevel level) {
        var core=observed.placement().core();
        var state=core.tegRuntimeState();
        var event=state.lastCommittedEvent();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                "TEG_CARRIER phaseTicks={} worldTick={} LAST_COMMITTED_TEG_EVENT tick={} status={} inputPaidJ={} generatedJ={} generatedW={} pressureDeltaA_kPa={} pressureDeltaB_kPa={} movedMolesA={} movedMolesB={} packetTA_K={} packetTB_K={} packetCpA_JperK={} packetCpB_JperK={} maxWorkJ={} effectiveEfficiency={} configuredEfficiency={} powerFactor={} intervalS={} eventPaid={} offerPublished={}",
                phaseTicks,level.getGameTime(),state.lastPaidSampleTick(),state.status(),state.lastInputPaidJoules(),state.lastGeneratedJoules(),state.lastGeneratedWatts(),
                state.circulatorA().pressureDeltaKpa(),state.circulatorB().pressureDeltaKpa(),state.circulatorA().transferredMoles(),state.circulatorB().transferredMoles(),
                event==null?Double.NaN:event.temperatureAKelvin(),event==null?Double.NaN:event.temperatureBKelvin(),
                event==null?Double.NaN:event.heatCapacityAJoulesPerKelvin(),event==null?Double.NaN:event.heatCapacityBJoulesPerKelvin(),
                event==null?Double.NaN:event.maximumWorkJoules(),event==null?Double.NaN:event.effectiveEfficiency(),
                event==null?Double.NaN:event.configuredEfficiency(),event==null?Double.NaN:event.powerFactor(),
                event==null?Double.NaN:event.intervalSeconds(),event!=null&&event.paidWorkCommitted(),event!=null&&event.offerPublished());
        for(int i=0;i<7;i++) {
            var component=observed.capture().componentFor(observed.topology().representatives().get(i)).orElseThrow();
            var mixture=component.aggregate();
             double volume=mixture.volumeCubicMeters();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "TEG_CARRIER_COMPONENT phaseTicks={} group={} loop={} nodes={} volumeM3={} temperatureK={} pressureKPa={} totalMoles={} heatCapacityJPerK={} thermalJ={}",
                    phaseTicks,i,i<3?"hot":"cold",component.nodes().size(),volume,
                    mixture.temperatureKelvin(),mixture.pressureKpa(volume),mixture.totalMoles(),mixture.heatCapacity(),mixture.thermalEnergy());
        }
        logWasteDischarge(level,observed,"cadence-"+phaseTicks);
        var assembly=TegAssembly.resolve(level,core).orElseThrow(()->new IllegalStateException("live TEG assembly unavailable for carrier diagnostics"));
        logCarrierPort(phaseTicks,"inletA",observed,assembly.inletA());
        logCarrierPort(phaseTicks,"outletA",observed,assembly.outletA());
        logCarrierPort(phaseTicks,"inletB",observed,assembly.inletB());
        logCarrierPort(phaseTicks,"outletB",observed,assembly.outletB());
        int hxIndex=0;
        for(var host:observed.placement().baseMachines()) {
            if(host.resolvedProfile()==null||host.resolvedProfile().kind()!=AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER)continue;
            String role="HX"+(++hxIndex);
            try {
                var gas=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,host.getBlockPos()).orElseThrow().snapshot();
                var work=host.deviceRuntimeState();
                var exterior=AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,host.getBlockPos()).orElse(null);
                String boundary=exterior==null?"FINITE_ROOM":exterior.kind().name();
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CARRIER_ROOM phaseTicks={} role={} temperatureK={} pressureKPa={} totalMoles={} heatCapacityJPerK={} thermalJ={} exchangerWorkStatus={} exchangerMovedMoles={} exchangerPipeDeltaJ={} ambientReceiptCumulativeJ={} spaceReceiptCumulativeJ={} boundary={}",phaseTicks,role,gas.temperatureKelvin(),gas.pressureKpa(1),gas.totalMoles(),gas.heatCapacity(),gas.thermalEnergy(),work.lastWorkStatus(),work.lastMoles(),work.lastThermalJoules(),host.ambientHeatReceiptJoules(),host.spaceHeatReceiptJoules(),boundary);
            } catch(RuntimeException unavailable) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CARRIER_ROOM phaseTicks={} role={} snapshot=UNAVAILABLE reason={}",phaseTicks,role,unavailable.toString());
            }
        }
        for(int i=0;i<observed.layout().hotExchangers().size();i++) {
            BlockPos body=observed.layout().hotExchangers().get(i);
            AtmosMachineBlockEntity host=java.util.stream.Stream.concat(observed.placement().baseMachines().stream(),observed.placement().additional().atmosMachines().stream())
                    .filter(machine->machine.getBlockPos().equals(body)).findFirst().orElseThrow(()->new IllegalStateException("planned hot contact host unavailable at "+body));
            try {
                var gas=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,body).orElseThrow().snapshot();
                var inlet=host.gasPorts().stream().filter(port->port.id().equals("inlet")).findFirst().orElseThrow();
                var outlet=host.gasPorts().stream().filter(port->port.id().equals("outlet")).findFirst().orElseThrow();
                var inComponent=TegContinuousFixtureService.capturedComponentForPort(observed.topology(),observed.capture(),inlet.key(body)).orElseThrow();
                var outComponent=TegContinuousFixtureService.capturedComponentForPort(observed.topology(),observed.capture(),outlet.key(body)).orElseThrow();
                var work=host.deviceRuntimeState();
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_HOT_CONTACT phaseTicks={} contact={} body={} burnerCellK={} inletComponent={} inletPortK={} outletComponent={} outletPortK={} committedWorkStatus={} committedMovedMoles={} committedPipeThermalJ={} committedThermalReceiptJ={} committedEvents={} ambientReceiptJ={} spaceReceiptJ={}",
                        phaseTicks,i+1,body,gas.temperatureKelvin(),observed.topology().components().indexOf(observed.topology().components().stream().filter(c->c.contains(inlet.key(body))).findFirst().orElseThrow()),inComponent.aggregate().temperatureKelvin(),observed.topology().components().indexOf(observed.topology().components().stream().filter(c->c.contains(outlet.key(body))).findFirst().orElseThrow()),outComponent.aggregate().temperatureKelvin(),work.lastWorkStatus(),work.lastMoles(),work.lastThermalJoules(),host.committedThermalJoules(),host.committedWorkEvents(),host.ambientHeatReceiptJoules(),host.spaceHeatReceiptJoules());
            } catch(RuntimeException unavailable) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_HOT_CONTACT phaseTicks={} contact={} body={} snapshot=UNAVAILABLE reason={}",phaseTicks,i+1,body,unavailable.toString());
            }
        }
        for(var host:observed.placement().baseMachines()) {
            if(host.resolvedProfile()==null||(host.resolvedProfile().kind()!=AtmosMachineProfile.Kind.VOLUME_PUMP&&host.resolvedProfile().kind()!=AtmosMachineProfile.Kind.PRESSURE_PUMP))continue;
            var profile=host.resolvedProfile();var settings=host.settingsSnapshot();
            var role=TegContinuousOperatingPlan.pumpRole(observed.layout().fueled(),host.getBlockPos());
            if(profile.kind()==AtmosMachineProfile.Kind.PRESSURE_PUMP)
                com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CARRIER_PUMP_SETTINGS phaseTicks={} role={} kind={} flowEnabled={} apcDemandW={} targetPressureKPa={} resolvedMaximumPressureKPa={} ports={} pumpSpeedup={} deviceIntervalSeconds={}",phaseTicks,role,profile.kind(),settings.enabled().orElse(false),host.apcPowerDemandWatts(),settings.targetPressureKpa().orElse(Double.NaN),profile.pump().maxPressureKpa().orElse(Double.NaN),host.gasPorts(),profile.simulation().pumpSpeedup(),profile.simulation().deviceIntervalSeconds());
            else com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CARRIER_PUMP_SETTINGS phaseTicks={} role={} kind={} flowEnabled={} apcDemandW={} configuredRateM3PerSecond={} pumpSpeedup={} deviceIntervalSeconds={}",phaseTicks,role,profile.kind(),settings.enabled().orElse(false),host.apcPowerDemandWatts(),settings.transferRateM3PerSecond().orElse(Double.NaN),profile.simulation().pumpSpeedup(),profile.simulation().deviceIntervalSeconds());
        }
        for(var host:observed.placement().additional().atmosMachines()) {
            if(host.resolvedProfile()==null||host.resolvedProfile().kind()!=AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER)continue;
            if(!observed.layout().radiatorPositions().contains(host.getBlockPos()))continue;
            var work=host.deviceRuntimeState();
            var finite=AtmosphereService.INSTANCE.finiteCellEndpointForImmediateTransfer(level,host.getBlockPos()).orElse(null);
            var boundary=AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,host.getBlockPos()).orElse(null);
            String boundaryKind=boundary==null?"UNAVAILABLE":boundary.kind().name();
            String environment=finite!=null?"FINITE_CELL":boundaryKind;
            double environmentMoles=finite==null?Double.NaN:finite.snapshot().totalMoles();
            double environmentKelvin=finite==null?(boundary==null?Double.NaN:boundary.ambient().temperatureKelvin()):finite.snapshot().temperatureKelvin();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "TEG_CARRIER_SPACE_RADIATOR phaseTicks={} position={} environment={} boundary={} finiteEnvironmentMoles={} environmentKelvin={} exchangerWorkStatus={} movedMoles={} pipeDeltaJ={} ambientReceiptCumulativeJ={} spaceReceiptCumulativeJ={}",
                    phaseTicks,host.getBlockPos(),environment,boundaryKind,environmentMoles,environmentKelvin,work.lastWorkStatus(),work.lastMoles(),work.lastThermalJoules(),host.ambientHeatReceiptJoules(),host.spaceHeatReceiptJoules());
        }
    }

    /** Read-only paired waste-path snapshot; distinguishes pump capture, current stock, and vent export. */
    static void logWasteDischarge(ServerLevel level,TegContinuousFixtureService.ObservedHardware observed,String label) {
        var layout=observed.layout();
        var vent=observed.placement().additional().ventilation().stream()
                .filter(v->v.getBlockPos().equals(layout.wasteVentBody())).findFirst().orElse(null);
        var pumpPos=layout.devicePosition(TegContinuousFuelPlan.DeviceRole.WASTE_PUMP);
        var pump=observed.placement().additional().ventilation().stream()
                .filter(v->v.getBlockPos().equals(pumpPos)).findFirst().orElse(null);
        var logger=com.juicyslew.moonstation14.MoonStation14.LOGGER;
        if(vent==null||pump==null) {
            logger.info("TEG_WASTE label={} ventPresent={} pumpPresent={}",label,vent!=null,pump!=null);return;
        }
        var settings=vent.config().settings();
        var meter=PowerRuntime.ventilationMeter(level,vent);
        var front=layout.wasteVentBody().relative(layout.wasteVentFacing());
        var boundary=AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,front).orElse(null);
        logger.info("TEG_WASTE label={} tick={} ventPos={} facing={} settingsEnabled={} layer={} targetKPa={} targetChangeKPaS={} externalBoundKPa={} rateM3s={} powerKnown={} deliveredW={} powerDiagnostic={} boundaryPresent={} boundaryKind={} boundaryValid={} boundaryPressureKPa={} boundaryMoles={} boundaryTemperatureK={}",
                label,level.getGameTime(),layout.wasteVentBody(),layout.wasteVentFacing(),settings.configuredEnabled(),settings.selectedLayer(),settings.targetPressureKPa(),settings.targetPressureChangeKPaPerSecond(),settings.externalPressureBoundKPa(),settings.volumeRateM3PerSecond(),meter.known(),meter.deliveredWatts(),PowerRuntime.ventilationMeterDiagnostic(level,vent),boundary!=null,boundary==null?null:boundary.kind(),boundary!=null&&boundary.validate(),boundary==null?Double.NaN:boundary.ambient().pressureKpa(1),boundary==null?Double.NaN:boundary.ambient().totalMoles(),boundary==null?Double.NaN:boundary.ambient().temperatureKelvin());
        logger.info("TEG_WASTE label={} ventExportSpecies={} ventExportHeat={} ventTransferSpecies={} ventTransferHeat={} pumpCaptureSpecies={} pumpCaptureHeat={}",
                label,vent.exportObservation().speciesMoles(),vent.exportObservation().thermalJoulesByBoundary(),vent.transferObservation().speciesMoles(),vent.transferObservation().thermalJoules(),pump.transferObservation().speciesMoles(),pump.transferObservation().thermalJoules());
        logger.info("TEG_WASTE_POWER label={} pumpPos={} pumpDiagnostic={}",label,pumpPos,PowerRuntime.ventilationMeterDiagnostic(level,pump));
        PipeGasTransfer.Plan hypotheticalReference=null,hypotheticalSecond=null;
        var backPosition=layout.wasteSegments().get(2).position();
         var backNode=observed.capture().components().values().stream().flatMap(c->c.nodes().stream())
                 .filter(n->n.key().position().equals(backPosition)&&n.key().layer()==layout.wasteSegments().get(2).layer()).findFirst().orElse(null);
         var backComponent=backNode==null?null:observed.capture().componentFor(backNode.key()).orElse(null);
         if(backNode!=null&&boundary!=null)try {
             var gas=backComponent.aggregate();double volume=gas.volumeCubicMeters();
             hypotheticalReference=VentilationGasFlow.planVentRelease(gas,boundary.ambient(),volume,1.0,
                     PowerRuntime.TICKS_PER_SOLVE/20.0,settings,vent.prototype().gasVentPump()).orElse(null);
             if(hypotheticalReference!=null)hypotheticalSecond=PipeGasTransfer.plan(gas,boundary.ambient(),volume,1.0,
                    hypotheticalReference.packet().totalMoles(),Math.min(settings.externalPressureBoundKPa(),settings.targetPressureKPa()),
                    java.util.Set.of(GasType.values())).orElse(null);
        } catch(RuntimeException ignored) { }
        logger.info("TEG_WASTE_HYPOTHETICAL label={} status=NOT_COMMITTED backNodePresent={} reference={} secondPlan={} withinReference={}",label,backNode!=null,hypotheticalReference==null?"EMPTY":hypotheticalReference.packet().gasMoles()+" Q="+hypotheticalReference.energyMoved(),hypotheticalSecond==null?"EMPTY":hypotheticalSecond.packet().gasMoles()+" Q="+hypotheticalSecond.energyMoved(),hypotheticalReference!=null&&hypotheticalSecond!=null&&wastePlanWithin(hypotheticalSecond,hypotheticalReference));
        for(var segment:layout.wasteSegments()) {
            var node=observed.capture().components().values().stream().flatMap(c->c.nodes().stream())
                    .filter(n->n.key().position().equals(segment.position())&&n.key().layer()==segment.layer()).findFirst().orElse(null);
            if(node==null) {logger.info("TEG_WASTE_NODE label={} pos={} snapshot=UNAVAILABLE",label,segment.position());continue;}
             var gas=observed.capture().inventory().nodeShare(node.key());
            logger.info("TEG_WASTE_NODE label={} pos={} key={} componentRoot={} identity={} version={} volumeM3={} moles={} temperatureK={} pressureKPa={}",label,segment.position(),node.key(),observed.topology().components().stream().filter(c->c.contains(node.key())).map(c->c.stream().min(java.util.Comparator.comparing(Object::toString)).orElse(null)).findFirst().orElse(null),node.incarnation(),node.entry().version(),node.volume(),gas.gasMoles(),gas.temperatureKelvin(),gas.pressureKpa(node.volume()));
        }
    }

    private static boolean wastePlanWithin(PipeGasTransfer.Plan actual,PipeGasTransfer.Plan reference) {
        for(GasType gas:GasType.values())if(actual.perGasMoved().getOrDefault(gas,0d)>reference.perGasMoved().getOrDefault(gas,0d))return false;
        return actual.packet().totalMoles()<=reference.packet().totalMoles()&&actual.energyMoved()<=reference.energyMoved();
    }

    /** Existing miner owner telemetry, emitted only at the bounded 100-tick diagnostic sample cadence. */
    static void logMinerSources(long phaseTicks,TegContinuousFixtureService.ObservedHardware observed,ServerLevel level) {
        for(var miner:observed.placement().additional().miners()) {
            var profile=miner.resolvedProfile();
            if(profile==null)continue;
            var room=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,miner.getBlockPos())
                    .map(endpoint->endpoint.snapshot()).orElse(null);
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info(
                    "TEG_MINER_SAMPLE phaseTicks={} gas={} producedMoles={} configuredRateMolPerSecond={} sourceTemperatureK={} maxExternalPressureKPa={} maxTotalMoles={} roomPressureKPa={} roomMoles={} roomTemperatureK={} identityPresent={}",
                    phaseTicks,profile.gas(),miner.producedMoles(),profile.spawnAmount(),profile.spawnTemperatureKelvin(),
                    profile.maxExternalPressureKpa(),profile.maxTotalMoles(),room==null?Double.NaN:room.pressureKpa(),
                    room==null?Double.NaN:room.totalMoles(),room==null?Double.NaN:room.temperatureKelvin(),miner.hasLivePlacementIdentity());
        }
    }

    private static void logCarrierPort(long phaseTicks,String role,TegContinuousFixtureService.ObservedHardware observed,PipeTopology.NodeKey key) {
        var component=TegContinuousFixtureService.capturedComponentForPort(observed.topology(),observed.capture(),key).orElseThrow();
        var gas=component.aggregate();double volume=gas.volumeCubicMeters();
        int group=observed.topology().representatives().indexOf(component.root());
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_CARRIER_STREAM phaseTicks={} role={} groupIndex={} volumeM3={} temperatureK={} pressureKPa={} totalMoles={} heatCapacityJPerK={} thermalJ={}",phaseTicks,role,group,volume,gas.temperatureKelvin(),gas.pressureKpa(volume),gas.totalMoles(),gas.heatCapacity(),gas.thermalEnergy());
    }

    static IgnitionSample sampleIgnition(ServerLevel level,
            TegContinuousFixtureService.ObservedHardware observed,PowerDeviceBlockEntity output) {
        double oxygenProduced=0,plasmaProduced=0;
        int oxygenMiners=0,plasmaMiners=0;
        for(var miner:observed.placement().additional().miners()) {
            GasMinerBlockEntity gasMiner=miner;
            if(level.getBlockEntity(miner.getBlockPos())!=miner)throw new IllegalStateException("owned gas miner missing at "+miner.getBlockPos());
            var profile=gasMiner.resolvedProfile();
            if(profile==null||!gasMiner.hasLivePlacementIdentity())
                throw new IllegalStateException("owned gas miner profile or live identity missing at "+miner);
            switch(profile.gas()) {
                case OXYGEN -> { oxygenProduced+=gasMiner.producedMoles(); oxygenMiners++; }
                case PLASMA -> { plasmaProduced+=gasMiner.producedMoles(); plasmaMiners++; }
                default -> { }
            }
        }
        if(oxygenMiners!=1||plasmaMiners!=1)throw new IllegalStateException("expected exactly one oxygen and plasma miner");

        var heaters=observed.placement().baseMachines().stream().filter(machine->level.getBlockEntity(machine.getBlockPos())==machine
                &&machine.resolvedProfile()!=null&&machine.resolvedProfile().kind()==AtmosMachineProfile.Kind.HEATER).toList();
        if(heaters.size()!=2)throw new IllegalStateException("expected both owned startup heaters, found "+heaters.size());
        String heaterReceipts=heaters.stream().map(heater->{
            var meter=PowerRuntime.apcPowerMeter(level,heater);
            return heater.getBlockPos()+"[paidJ="+heater.committedPaidJoules()+",thermalJ="+heater.committedThermalJoules()
                    +",grantW="+(meter.known()?meter.deliveredWatts():Double.NaN)+"]";
        }).collect(java.util.stream.Collectors.joining(";"));
        boolean paidHeaterWork=heaters.stream().allMatch(heater->{
            var meter=PowerRuntime.apcPowerMeter(level,heater);
            return heater.committedPaidJoules()>0&&heater.committedThermalJoules()>0&&meter.known()
                    &&heater.apcAllocatedWatts()>=heater.resolvedProfile().configuredReceiverLoadWatts()
                    &&meter.deliveredWatts()>=heater.resolvedProfile().configuredReceiverLoadWatts();
        });

        double burnerOxygen=0,burnerPlasma=0,burnerCo2=0,maximumBurnerKelvin=0,burnerKelvinSum=0;int burnerCells=0;
        for(var cell:observed.layout().fueled().roomInterior()) {
            GasMixture gas=AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,cell)
                    .orElseThrow().snapshot();
            burnerOxygen+=gas.moles(GasType.OXYGEN);
            burnerPlasma+=gas.moles(GasType.PLASMA);
            burnerCo2+=gas.moles(GasType.CARBON_DIOXIDE);
            maximumBurnerKelvin=Math.max(maximumBurnerKelvin,gas.temperatureKelvin());
            burnerKelvinSum+=gas.temperatureKelvin();burnerCells++;
        }
        if(burnerCells!=27)throw new IllegalStateException("expected 27 burner finite cells, found "+burnerCells);
        double pipeCo2=0;
        for(var component:observed.capture().components().values())
            pipeCo2+=component.aggregate().moles(GasType.CARBON_DIOXIDE);

        double exportedCo2=0;
        boolean foundVent=false;
        for(VentilationDeviceBlockEntity vent:observed.placement().additional().ventilation()) {
            if(!vent.getBlockPos().equals(observed.layout().wasteVentBody()))continue;
            if(level.getBlockEntity(vent.getBlockPos())!=vent)throw new IllegalStateException("owned waste vent missing");
            exportedCo2=vent.exportObservation().speciesMoles().getOrDefault(GasType.CARBON_DIOXIDE,0d);foundVent=true;
            break;
        }
        if(!foundVent)throw new IllegalStateException("owned waste vent missing");
        return new IgnitionSample(oxygenProduced,plasmaProduced,output.energyJoules(),paidHeaterWork,heaterReceipts,
                burnerOxygen,burnerPlasma,burnerCo2,pipeCo2,exportedCo2,maximumBurnerKelvin,burnerKelvinSum/burnerCells,
                com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.currentSampleTick(level),level.getGameTime());
    }

    private static void cleanupFailure(ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease,Throwable failure) {
        if(origin!=null)try {
            var entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElse(null);
            if(entry!=null)TegContinuousFixtureTestSupport.quiesceOperatingHardware(
                    TegContinuousFixtureService.observeOwnedHardware(level,entry));
        } catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
        try { lease.close(); } catch(Throwable cleanup) { failure.addSuppressed(cleanup); }
        if(failure.getSuppressed().length>0)com.juicyslew.moonstation14.MoonStation14.LOGGER.warn(
                "TEG continuous fixture cleanup failed: {}",failure.getSuppressed()[0].toString());
    }

    private static void require(boolean condition,String message) { if(!condition)throw new GameTestAssertException(message); }
}
