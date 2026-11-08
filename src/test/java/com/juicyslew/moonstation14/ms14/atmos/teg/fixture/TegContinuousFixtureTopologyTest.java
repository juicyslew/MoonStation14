package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.machine.*;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.*;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TegContinuousFixtureTopologyTest {
    @Test void continuousAndFiniteLayoutsUseResolvedHotPressureAndColdVolumeCarrierPumps() {
        var core=new BlockPos(40,64,40);
        var finite=TegFueledFixturePlan.layout(core);
        var continuous=TegContinuousFuelPlan.layout(new BlockPos(18,62,22)).fueled();
        assertEquals(2,finite.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PUMP).count());
        assertEquals(0,finite.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PRESSURE_PUMP).count());
        assertEquals(core.offset(-8,0,-1),finite.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PUMP).findFirst().orElseThrow().position());
        assertEquals(1,continuous.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PUMP).count());
        assertEquals(1,continuous.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PRESSURE_PUMP).count());
        assertEquals(2,continuous.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).count());
        assertEquals(1,finite.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).count(),"legacy finite fixture remains one-heater");
        var pressure=catalog().get(ModAtmosMachines.id("gas_pressure_pump"));
        assertEquals(AtmosMachineProfile.Kind.PRESSURE_PUMP,pressure.kind());
        assertEquals(200d,pressure.power().watts());
        assertEquals(4500d,pressure.pump().maxPressureKpa().orElseThrow());
        assertEquals(2,pressure.ports().size());
    }
    @Test void continuousTypedPassiveCoverageIncludesBaseAndAdditionalContactsAndDistinctPumpKinds(){
        var layout=TegContinuousFuelPlan.layout(new BlockPos(40,64,40));
        var passive=new HashSet<BlockPos>();
        layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.EXCHANGER).map(TegFueledFixturePlan.Machine::position).forEach(passive::add);
        layout.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.PASSIVE_HEAT_EXCHANGER||d.role()==TegContinuousFuelPlan.DeviceRole.HOT_HEAT_EXCHANGER).map(TegContinuousFuelPlan.Device::position).forEach(passive::add);
        assertEquals(8,passive.size());
        assertEquals(5,layout.devicePositions(TegContinuousFuelPlan.DeviceRole.PASSIVE_HEAT_EXCHANGER).size());
        assertEquals(2,layout.devicePositions(TegContinuousFuelPlan.DeviceRole.HOT_HEAT_EXCHANGER).size());
        assertEquals(1,layout.devicePositions(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP).size());
          assertEquals(1,layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PUMP).count());
         assertEquals(1,layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PRESSURE_PUMP).count());
        var catalog=catalog();
        var volume=catalog.get(ModAtmosMachines.id("gas_volume_pump"));var pressure=catalog.get(ModAtmosMachines.id("gas_pressure_pump"));
        assertEquals(AtmosMachineProfile.Kind.VOLUME_PUMP,volume.kind());assertEquals(AtmosMachineProfile.PumpMode.VOLUME,volume.pump().mode());
        assertEquals(AtmosMachineProfile.Kind.PRESSURE_PUMP,pressure.kind());assertEquals(AtmosMachineProfile.PumpMode.PRESSURE,pressure.pump().mode());
        assertEquals(4500d,pressure.pump().maxPressureKpa().orElseThrow());
          assertEquals(1,layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PUMP).map(m->volume.kind()).filter(k->k==AtmosMachineProfile.Kind.VOLUME_PUMP).count());
         assertEquals(1,layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PRESSURE_PUMP).map(m->pressure.kind()).filter(k->k==AtmosMachineProfile.Kind.PRESSURE_PUMP).count());
        assertFalse(layout.fueled().machines().stream().map(TegFueledFixturePlan.Machine::position).toList().contains(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP)));
        assertEquals(13d/15d,TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION);
    }
    @Test void bothResolvedContinuousHeatersReceiveStartupAndCutoffProposalsWhileFiniteRosterStaysSingle(){
        var origin=new BlockPos(40,64,40);var continuous=TegContinuousFuelPlan.layout(origin);var catalog=catalog();
        var heaterProfile=catalog.get(ModAtmosMachines.id("gas_thermo_heater"));
        assertEquals(5000d,heaterProfile.configuredReceiverLoadWatts());
        var heaters=continuous.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList();
        assertEquals(2,heaters.size());
        for(var heater:heaters){
            assertTrue(continuous.fueled().hotComponents().stream().anyMatch(c->c.contains(heater.position().relative(heater.facing().getOpposite()))));
            var startup=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.IGNITION_HEATER,heaterProfile,AtmosMachineSettings.defaults(heaterProfile),TegContinuousOperatingPlan.Stage.STARTUP);
            var cutoff=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.IGNITION_HEATER,heaterProfile,startup.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF);
            assertEquals(java.util.Optional.of(true),startup.finalValues().enabled());
            assertEquals(java.util.Optional.of(false),cutoff.finalValues().enabled());
        }
        assertEquals(1,TegFueledFixturePlan.layout(continuous.core()).machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).count());
    }
    @Test void continuousPlacementUsesNineHighBoundsAndOnlyDeclaredWindowsAndSupportHosts(){
        var origin=new BlockPos(40,64,40);var layout=TegContinuousFuelPlan.layout(origin);
        assertTrue(TegFixturePlacement.insideFootprint(origin,origin.offset(47,8,47),TegContinuousFuelPlan.HEIGHT));
        assertFalse(TegFixturePlacement.insideFootprint(origin,origin.offset(47,8,47),8));
        assertFalse(TegFixturePlacement.insideFootprint(origin,origin.offset(48,8,47),TegContinuousFuelPlan.HEIGHT));
        assertFalse(TegFixturePlacement.insideFootprint(origin,origin.offset(47,9,47),TegContinuousFuelPlan.HEIGHT));
        var windows=TegFixturePlacement.continuousObservationWindows(layout);
        Set<BlockPos> removedColdWindows=new HashSet<>();
        for(int x=15;x<=17;x++)for(int y=0;y<=2;y++)removedColdWindows.add(layout.core().offset(x,y,-1));
        assertTrue(Collections.disjoint(windows,removedColdWindows),"removed cold chamber must not leave floating windows");
    }

    @Test void resolvesFifteenBodiesAndProducesTenDisjointComponents() {
        var profiles=catalog();var mixer=profiles.get(ModAtmosMachines.id("gas_mixer"));var rad=profiles.get(ModAtmosMachines.id("passive_heat_exchanger"));
        var layout=TegContinuousFuelPlan.layout(new BlockPos(40,64,40),mixer,rad);Map<BlockPos,List<PipeGasPortHost.Port>> ports=new LinkedHashMap<>();
         for(var m:layout.fueled().machines()){ports.put(m.position(),descriptors(profiles.get(ModAtmosMachines.id(switch(m.kind()){case CIRCULATOR->"teg_circulator";case PUMP->"gas_volume_pump";case PRESSURE_PUMP->"gas_pressure_pump";case EXCHANGER->"passive_heat_exchanger";case HEATER->"gas_thermo_heater";})),m.facing()));}
         ports.put(layout.origin().offset(6,2,4),descriptors(mixer,Direction.NORTH));for(var body:layout.radiatorPositions())ports.put(body,descriptors(rad,Direction.WEST));
         ports.put(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP),descriptors(profiles.get(ModAtmosMachines.id("gas_pressure_pump")),Direction.WEST));
         for(var device:layout.devices())if(device.role()==TegContinuousFuelPlan.DeviceRole.HOT_HEAT_EXCHANGER)ports.put(device.position(),descriptors(rad,device.facing()));
          var plan=TegContinuousFixtureTopology.plan(layout,ports);assertEquals(16,ports.size());assertEquals(10,plan.components().size());assertEquals(10,plan.representatives().size());
         double hotVolume=0,coldVolume=0;
         for(int i=0;i<plan.components().size();i++) {
             double volume=plan.components().get(i).stream().mapToDouble(node->ports.getOrDefault(node.position(),List.of()).stream()
                     .filter(port->port.id().equals(node.portId())).mapToDouble(PipeGasPortHost.Port::volume).findFirst()
                      .orElse(Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3)).sum();
             if(i<3)hotVolume+=volume;else if(i<7)coldVolume+=volume;
         }
          assertEquals(16.0d,hotVolume,1e-9,"resolved hot network includes both actual 200 L heater ports");
         assertEquals(19.8d,coldVolume,1e-9,"resolved cold carrier network node volume");
         assertEquals(27d,27*com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits.minecraftCellVolumeCubicMeters(),1e-9,
                 "27-cell burner fuel room physical volume");
           var heater=layout.core().offset(-11,0,-2);var heaterPort=ports.get(heater).stream().filter(p->p.id().equals("pipe")).findFirst().orElseThrow();var heaterKey=new PipeTopology.NodeKey(heater,PipeTopology.Layer.PRIMARY,"pipe");assertEquals(Direction.NORTH,layout.fueled().machines().stream().filter(m->m.position().equals(heater)).findFirst().orElseThrow().facing());assertTrue(heaterPort.directions().contains(Direction.SOUTH));assertTrue(plan.components().get(1).contains(heaterKey));assertFalse(plan.components().get(9).contains(heaterKey));
         var secondHeater=layout.core().offset(-9,0,-6);var secondHeaterPort=ports.get(secondHeater).stream().filter(p->p.id().equals("pipe")).findFirst().orElseThrow();var secondHeaterKey=new PipeTopology.NodeKey(secondHeater,PipeTopology.Layer.PRIMARY,"pipe");assertEquals(Direction.NORTH,layout.fueled().machines().stream().filter(m->m.position().equals(secondHeater)).findFirst().orElseThrow().facing().getOpposite());assertTrue(secondHeaterPort.directions().contains(Direction.NORTH));assertFalse(plan.components().get(1).contains(secondHeaterKey));assertTrue(plan.components().get(2).contains(secondHeaterKey));assertFalse(plan.components().get(9).contains(secondHeaterKey));
        var mixerPos=layout.origin().offset(6,2,4);var outlet=ports.get(mixerPos).stream().filter(p->p.id().equals("outlet")).findFirst().orElseThrow();
        var outletKey=new PipeTopology.NodeKey(mixerPos,outlet.layer(),outlet.id());
        assertTrue(plan.components().get(9).contains(outletKey));assertFalse(plan.representatives().contains(outletKey));
         var hotPump=layout.fueled().machines().get(1);var hotPumpOutlet=ports.get(hotPump.position()).stream().filter(p->p.id().equals("outlet")).findFirst().orElseThrow();
         var stableHotRepresentative=new PipeTopology.NodeKey(hotPump.position(),hotPumpOutlet.layer(),hotPumpOutlet.id());
         assertTrue(plan.components().get(1).contains(stableHotRepresentative));assertNotEquals(heaterKey,stableHotRepresentative);
         var representatives=new ArrayList<>(plan.representatives());representatives.set(1,stableHotRepresentative);
         var capturePlan=new TegFueledFixtureTopology.Plan(plan.components(),representatives);
         var components=new HashMap<PipeTopology.NodeKey,LoadedPipeGasWork.Component>();var portComponents=new HashMap<PipeTopology.NodeKey,PipeTopology.NodeKey>();
         for(int i=0;i<capturePlan.representatives().size();i++){var rep=capturePlan.representatives().get(i);var component=new LoadedPipeGasWork.Component(rep,List.of(),new GasMixture(Map.of(),300));components.put(rep,component);portComponents.put(rep,rep);}
         var capture=new LoadedPipeGasWork.Snapshot(new PipeGasInventory(),components,portComponents,false);
         assertSame(components.get(capturePlan.representatives().get(9)),TegContinuousFixtureService.capturedComponentForPort(capturePlan,capture,outletKey).orElseThrow());
         var heaterRep=capturePlan.representatives().get(1);
         assertNotEquals(heaterRep,heaterKey,"heater port is nonrepresentative within its hot-loop component");
         assertFalse(capture.portComponents().containsKey(heaterKey),"capture stores only representative port keys");
         assertTrue(capture.componentFor(heaterKey).isEmpty(),"direct nonrepresentative capture lookup is intentionally absent");
         assertSame(components.get(heaterRep),TegContinuousFixtureService.capturedComponentForPort(capturePlan,capture,heaterKey).orElseThrow(),
                 "heater gas resolves through its expected component representative");
         var unknown=new PipeTopology.NodeKey(layout.origin(),PipeTopology.Layer.PRIMARY,"unknown");
         assertThrows(IllegalStateException.class,()->TegContinuousFixtureService.capturedComponentForPort(capturePlan,capture,unknown));
         var aliasedComponents=new ArrayList<>(capturePlan.components());aliasedComponents.set(1,capturePlan.components().get(0));
         var aliasPlan=new TegFueledFixtureTopology.Plan(aliasedComponents,capturePlan.representatives());
         assertThrows(IllegalStateException.class,()->TegContinuousFixtureService.capturedComponentForPort(aliasPlan,capture,capturePlan.representatives().get(0)));
         var missingRepCapture=new LoadedPipeGasWork.Snapshot(new PipeGasInventory(),components,Map.of(),false);
         assertThrows(IllegalStateException.class,()->TegContinuousFixtureService.capturedComponentForPort(capturePlan,missingRepCapture,outletKey));
        for(int i=0;i<10;i++)assertTrue(plan.components().get(i).contains(plan.representatives().get(i)));
        var coldMachine=layout.fueled().machines().get(4);var coldPorts=new ArrayList<>(ports.get(coldMachine.position()));var coldPort=coldPorts.getFirst();coldPorts.set(0,new PipeGasPortHost.Port(coldPort.id(),PipeTopology.Layer.SECONDARY,coldPort.directions(),coldPort.volume(),coldPort.incarnation()));var wrongLayer=new LinkedHashMap<>(ports);wrongLayer.put(coldMachine.position(),coldPorts);assertThrows(IllegalArgumentException.class,()->TegContinuousFixtureTopology.plan(layout,wrongLayer));
         for(int i=0;i<3;i++)assertTrue(plan.components().get(i).stream().map(PipeTopology.NodeKey::position).collect(java.util.stream.Collectors.toSet()).containsAll(layout.fueled().hotComponents().get(i)));
         for(int y=1;y<=2;y++){assertTrue(plan.components().get(1).contains(new PipeTopology.NodeKey(layout.core().offset(-15,y,-1),PipeTopology.Layer.PRIMARY)));assertFalse(plan.components().get(2).contains(new PipeTopology.NodeKey(layout.core().offset(-15,y,-1),PipeTopology.Layer.PRIMARY)));assertTrue(plan.components().get(2).contains(new PipeTopology.NodeKey(layout.core().offset(-17,y,-1),PipeTopology.Layer.PRIMARY)));assertFalse(plan.components().get(1).contains(new PipeTopology.NodeKey(layout.core().offset(-17,y,-1),PipeTopology.Layer.PRIMARY)));}
         for(var body:layout.hotExchangers().subList(1,3)) {
             var exchangerPorts=ports.get(body);var inlet=exchangerPorts.stream().filter(p->p.id().equals("inlet")).findFirst().orElseThrow();var outletPort=exchangerPorts.stream().filter(p->p.id().equals("outlet")).findFirst().orElseThrow();
             assertEquals(PipeTopology.Layer.PRIMARY,inlet.layer());assertEquals(PipeTopology.Layer.PRIMARY,outletPort.layer());
             assertTrue(plan.components().get(1).contains(inlet.key(body)));assertTrue(plan.components().get(2).contains(outletPort.key(body)));
             assertEquals(rad.ports().stream().filter(p->p.name()==AtmosMachineProfile.PortName.INLET).findFirst().orElseThrow().volumeM3(),inlet.volume());
             assertEquals(rad.ports().stream().filter(p->p.name()==AtmosMachineProfile.PortName.OUTLET).findFirst().orElseThrow().volumeM3(),outletPort.volume());
         }
        for(var body:layout.radiatorPositions()){var in=new PipeTopology.NodeKey(body,PipeTopology.Layer.PRIMARY,"inlet");var out=new PipeTopology.NodeKey(body,PipeTopology.Layer.PRIMARY,"outlet");assertNotEquals(plan.components().stream().filter(c->c.contains(in)).findFirst(),plan.components().stream().filter(c->c.contains(out)).findFirst());}
        var missing=new LinkedHashMap<>(ports);missing.put(layout.origin().offset(6,2,4),List.of());assertThrows(IllegalArgumentException.class,()->TegContinuousFixtureTopology.plan(layout,missing));
        var multi=new LinkedHashMap<>(ports);var altered=new ArrayList<>(ports.values().iterator().next());var p=altered.getFirst();altered.set(0,new PipeGasPortHost.Port(p.id(),p.layer(),Set.of(Direction.NORTH,Direction.SOUTH),p.volume(),p.incarnation()));multi.put(ports.keySet().iterator().next(),altered);assertThrows(IllegalArgumentException.class,()->TegContinuousFixtureTopology.plan(layout,multi));
        Map<PipeTopology.NodeKey,Set<PipeTopology.NodeKey>> actual=new HashMap<>();for(int i=0;i<plan.components().size();i++)actual.put(plan.representatives().get(i),plan.components().get(i));assertTrue(TegFueledFixtureTopology.exact(plan,actual));var missingNode=new HashMap<>(actual);missingNode.remove(plan.representatives().getFirst());assertFalse(TegFueledFixtureTopology.exact(plan,missingNode));var aliased=new HashMap<>(actual);aliased.put(plan.representatives().getFirst(),plan.components().get(1));assertFalse(TegFueledFixtureTopology.exact(plan,aliased));
    }
     static List<PipeGasPortHost.Port> descriptors(AtmosMachineProfile profile,Direction facing){PipeTopology.Layer fallback=profile.pipeLayers().map(AtmosMachineProfile.PipeLayers::initialLayer).orElse(PipeTopology.Layer.PRIMARY);return profile.ports().stream().map(p->new PipeGasPortHost.Port(p.name().name().toLowerCase(Locale.ROOT),p.fixedLayer().orElse(fallback),Set.of(AtmosMachinePortDirections.worldDirection(p.direction(),facing)),p.volumeM3(),UUID.randomUUID())).toList();}
     private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<AtmosMachineProfile> catalog(){var manager=new PrototypeManager();manager.register(ModAtmosMachines.TYPE);var raw=new LinkedHashMap<ResourceLocation,JsonObject>();for(String id:List.of("atmos_machine_base","atmos_machine_base_powered","constructible_machine_base","gas_pipe_sans_layers","gas_pipe_base","gas_binary_base","gas_trinary_base","gas_thermo_base","teg_circulator","gas_volume_pump","gas_pressure_pump","gas_thermo_heater","gas_mixer","passive_heat_exchanger")){String path="/data/moonstation14/moonstation14/atmos_machine/"+id+".json";try(var in=TegContinuousFixtureTopologyTest.class.getResourceAsStream(path)){assertNotNull(in,path);raw.put(ModAtmosMachines.id(id),JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());}catch(IOException e){throw new RuntimeException(e);}}manager.reload(ModAtmosMachines.TYPE,raw);return manager.snapshot(ModAtmosMachines.TYPE);}
}


