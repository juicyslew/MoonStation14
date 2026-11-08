package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePortDirections;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegThermodynamics;
import com.juicyslew.moonstation14.ms14.atmos.machine.thermal.PassiveHeatExchangePolicy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TegContinuousFuelPlanTest {
    @Test void radiatorStatusRequiresFiveUniformValidBoundaries() {
        assertEquals("SPACE",TegContinuousFixtureService.classifyRadiatorBoundaries(List.of("SPACE","SPACE","SPACE","SPACE","SPACE")));
        assertEquals("PLANETARY_AMBIENT",TegContinuousFixtureService.classifyRadiatorBoundaries(List.of("PLANETARY_AMBIENT","PLANETARY_AMBIENT","PLANETARY_AMBIENT","PLANETARY_AMBIENT","PLANETARY_AMBIENT")));
        assertEquals("UNAVAILABLE",TegContinuousFixtureService.classifyRadiatorBoundaries(List.of("SPACE","SPACE","PLANETARY_AMBIENT","SPACE","SPACE")));
        assertEquals("UNAVAILABLE",TegContinuousFixtureService.classifyRadiatorBoundaries(Arrays.asList("PLANETARY_AMBIENT","PLANETARY_AMBIENT",null,"PLANETARY_AMBIENT","PLANETARY_AMBIENT")));
        assertEquals("UNAVAILABLE",TegContinuousFixtureService.classifyRadiatorBoundaries(List.of("SPACE","SPACE","SPACE","SPACE")));
    }
    @Test void atmosphericRoleClassificationIncludesPressurePumpAndCountsNineAdditionalMachines() {
        assertTrue(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP.isAtmosMachine());
        assertFalse(TegContinuousFuelPlan.DeviceRole.GAS_MINER_OXYGEN.isAtmosMachine());
        var layout=TegContinuousFuelPlan.layout(BlockPos.ZERO);
        assertEquals(9,layout.devices().stream().filter(d->d.role().isAtmosMachine()).count());
    }
    @Test void runningRecipeHasKernelBudgetAndOrdinaryColdRejectionHeadroom() {
        // Measured native-loop packet inventory (3.04890728 hot / 1.26102018 cold mol),
        // not fuel-pressure-derived carrier inventory. Temperatures hypothesize a hotter
        // RUNNING burner environment; they are not a live-runtime claim.
        double hotMoles=3.04890728, coldMoles=1.26102018, hotK=1000, coldK=300, loadWatts=1525, dt=.6;
        double hotCp=hotMoles*GasType.NITROGEN.molarHeatCapacity();
        double coldCp=coldMoles*GasType.NITROGEN.molarHeatCapacity();
        var hot=new GasMixture(Map.of(GasType.NITROGEN,hotMoles),hotK,.1);
        var cold=new GasMixture(Map.of(GasType.NITROGEN,coldMoles),coldK,.1);
        double configuredEfficiency=.1;
        var result=TegThermodynamics.generate(hot,cold,configuredEfficiency,2);
        double watts=result.generatedEnergyJoules()/dt;
        double hotThermalWatts=result.hotThermalEnergyRemovedJoules()/dt;
        double coldThermalWatts=result.coldThermalEnergyAddedJoules()/dt;
        assertTrue(hotCp>0&&coldCp>0);
        assertTrue(watts>loadWatts*1.5,"kernel packet budget should exceed 1.525 kW with margin: "+watts);
        double hotTransferUlpWatts=(Math.ulp((float)hot.thermalEnergy())
                +Math.ulp((float)result.first().thermalEnergy()))/dt;
        double coldTransferUlpWatts=(Math.ulp((float)cold.thermalEnergy())
                +Math.ulp((float)result.second().thermalEnergy()))/dt;
        // TEG requested transfer is theoretical; committed mixture energy uses float-backed
        // temperatures. Bound only that representational quantization, not a power margin.
        assertTrue(Double.isFinite(hotThermalWatts)&&hotThermalWatts>0);
        assertTrue(Math.abs(result.transferJoules()/dt-hotThermalWatts)<=hotTransferUlpWatts,
                "actual hot-side transfer must match the requested kernel transfer within stored-energy ULPs");
        double modeledColdAdditionWatts=result.transferJoules()*(1-configuredEfficiency)/dt;
        assertTrue(Double.isFinite(coldThermalWatts)&&coldThermalWatts>0);
        assertTrue(Math.abs(modeledColdAdditionWatts-coldThermalWatts)<=coldTransferUlpWatts,
                "actual cold-side addition must match the requested kernel transfer within stored-energy ULPs");
        assertEquals(result.transferJoules()*2,result.generatedEnergyJoules(),1e-9,
                "kernel output retains its configured SS14 power-factor relation");

        // Resolve ordinary exchanger coefficients; compare finite one-cell ambient and
        // SPACE (null-environment radiation) boundaries without inventing a cold reservoir.
        var radiator=machineCatalog().get(ModAtmosMachines.id("passive_heat_exchanger"));
        var radiation=radiator.heatExchanger().radiationCoefficient();
        var convection=radiator.heatExchanger().convectionCoefficientJPerKPerS();
        var tileLoss=radiator.simulation().tileRadiationLossWatts();
        var space=PassiveHeatExchangePolicy.exchange(result.second(),null,dt,tileLoss,radiation,convection,false);
        assertTrue(space.radiatedEnergyJoules()/dt>=coldThermalWatts,
                "SPACE boundary must reject TEG cold-side heat at this assumed operating point");
        var air=GasMixture.breathableAir();
        var ambient=PassiveHeatExchangePolicy.exchange(result.second(),air,dt,tileLoss,radiation,convection,false);
        assertTrue(ambient.packetAfter().thermalEnergy()<result.second().thermalEnergy());
        assertTrue(ambient.environmentAfter().thermalEnergy()>air.thermalEnergy());
    }

    @Test void boundedRoutesAndMixerPortsUseExpectedFaces() {
        var profiles=mixerCatalog();var mixer=profiles.get(ModAtmosMachines.id("gas_mixer"));
        var p=TegContinuousFuelPlan.layout(new BlockPos(40,64,40),mixer);
        assertEquals(new BlockPos(62,66,58),p.core());
        var ports=p.mixerPorts(mixer);assertEquals(new BlockPos(46,66,44),ports.get(0).body());
        assertEquals(new BlockPos(46,66,43),ports.get(0).body().relative(Direction.NORTH));
        assertTrue(ports.stream().allMatch(port->port.facing()==Direction.NORTH));
        assertEquals(Direction.NORTH,ports.get(0).port());assertEquals(Direction.WEST,ports.get(1).port());assertEquals(Direction.SOUTH,ports.get(2).port());
        assertEquals(AtmosMachineProfile.PortName.INLET,ports.get(0).name());assertEquals(AtmosMachineProfile.PortName.FILTER,ports.get(1).name());assertEquals(AtmosMachineProfile.PortName.OUTLET,ports.get(2).name());
        assertEquals(java.util.List.of(AtmosMachineProfile.PortName.INLET,AtmosMachineProfile.PortName.FILTER,AtmosMachineProfile.PortName.OUTLET),mixer.ports().stream().map(AtmosMachineProfile.Port::name).toList());
        assertEquals(0.6,mixer.simulation().deviceIntervalSeconds());
        assertEquals(PipeTopology.Layer.PRIMARY,mixer.pipeLayers().orElseThrow().initialLayer());
        assertNull(mixer.power());
        for(var route:p.routes().values()) for(var pos:route) {
            assertTrue(pos.getX()>=40&&pos.getX()<88);
            assertTrue(pos.getY()>=64&&pos.getY()<72);
            assertTrue(pos.getZ()>=40&&pos.getZ()<88);
        }
        assertTrue(p.routes().get("oxygen").getLast().distManhattan(p.origin().offset(6,2,3))==0);
        assertTrue(p.routes().get("plasma").getLast().distManhattan(p.origin().offset(5,2,4))==0);
        assertTrue(p.routes().get("fuel").getFirst().distManhattan(p.origin().offset(6,2,5))==0);
        assertDoesNotThrow(()->TegContinuousFuelPlan.layout(new BlockPos(-50,-16,-80),mixer));
    }

    @Test void primaryWasteAndVentBranchesAttachToDeclaredHotComponents() {
        var p=TegContinuousFuelPlan.layout(new BlockPos(40,64,40));
        assertEquals(p.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.WASTE_PUMP).findFirst().orElseThrow().position(),
                p.devicePosition(TegContinuousFuelPlan.DeviceRole.WASTE_PUMP));
        assertNotEquals(p.wasteVentBody(),p.devicePosition(TegContinuousFuelPlan.DeviceRole.WASTE_PUMP));
        assertEquals(new BlockPos(58,68,57),p.wasteMountHost());
        var segments=p.wasteSegments();
        assertEquals(PipeTopology.Layer.PRIMARY,segments.get(0).layer());
        assertEquals(PipeTopology.Kind.STRAIGHT_SEALED_WALL,segments.get(0).kind());
        assertEquals(PipeTopology.Layer.PRIMARY,segments.get(1).layer());
        assertEquals(PipeTopology.Kind.STRAIGHT_SEALED_WALL,segments.get(1).kind());
        assertEquals(new BlockPos(47,66,59),segments.get(0).position());
        assertEquals(new BlockPos(45,66,59),segments.get(1).position());
        assertEquals(p.wasteMountHost(),segments.get(2).position());
        assertEquals(Set.of(Direction.NORTH,Direction.SOUTH),segments.get(1).ports());
        assertEquals(new BlockPos(58,68,58),p.wasteVentBody());
        assertEquals(Direction.SOUTH,p.wasteVentFacing());
        assertEquals(Map.of("oxygen",10,"plasma",18,"fuel",15,"waste",6,"vent",4),p.routes().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().size())));
        var waste=p.routes().get("waste");assertEquals(new BlockPos(45,66,58),waste.getLast());assertTrue(p.hotPipeComponents().get(2).containsAll(waste));assertTrue(p.hotPipeHosts().contains(waste.getLast()));assertFalse(p.hotPipeHosts().contains(segments.getFirst().position()));assertFalse(p.hotPipeHosts().contains(segments.get(1).position()));
        var vent=p.routes().get("vent");assertEquals(new BlockPos(58,67,57),vent.getFirst());assertEquals(new BlockPos(58,68,56),vent.get(2));assertEquals(p.wasteMountHost(),vent.getLast());assertTrue(p.hotPipeComponents().get(0).containsAll(vent));assertTrue(p.hotPipeHosts().contains(vent.get(2)));assertFalse(p.hotPipeHosts().contains(p.wasteMountHost()));assertTrue(vent.get(2).relative(Direction.SOUTH).equals(p.wasteMountHost()));
         var injector=p.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.INJECTOR).findFirst().orElseThrow();
           assertEquals(p.origin().offset(6,2,16),injector.position());
           assertEquals(p.origin().offset(6,2,17),injector.position().relative(injector.facing()));
         assertTrue(p.sealedCrossings().contains(injector.position().relative(injector.facing().getOpposite())));
        assertFalse(p.fueled().machines().stream().map(TegFueledFixturePlan.Machine::position).anyMatch(p.machines()::contains));
        assertEquals(PipeTopology.Kind.STRAIGHT_SEALED_WALL,segments.get(1).kind(),"back segment must seal the exterior vent mount");
        assertEquals(18,p.roomA().size()); // finite source-room cell geometry is unchanged
         assertEquals(8,p.wasteMountWall().size());
         assertFalse(p.wasteMountWall().contains(p.wasteMountHost()));
         assertTrue(p.wasteMountWall().contains(p.origin().offset(17,5,17)));
        assertFalse(p.wasteMountWall().contains(p.wasteVentBody()));
         assertTrue(p.wasteMountWall().contains(p.origin().offset(17,4,17))&&p.wasteMountWall().contains(p.origin().offset(19,6,17)));
        assertFalse(p.wasteMountWall().contains(p.wasteVentBody().relative(p.wasteVentFacing())));
    }

    @Test void continuousOnlyGeometryRestoresLongHotReturnAndLowerFuelEndpoint() {
        var origin=new BlockPos(40,64,40);var p=TegContinuousFuelPlan.layout(origin);var core=p.core();
        var hot=p.fueled().hotComponents().get(2);
         var expected=List.of(origin.offset(5,2,17),origin.offset(4,2,17),origin.offset(3,2,17),origin.offset(2,2,17),origin.offset(2,2,11),origin.offset(25,2,11),origin.offset(25,2,17));
        assertTrue(hot.containsAll(expected));
         assertTrue(hot.contains(origin.offset(2,2,17)));assertTrue(hot.contains(origin.offset(2,2,11)));
        assertTrue(p.fueled().sealedCrossings().contains(origin.offset(4,2,17)));
        assertTrue(p.fueled().sealedCrossings().contains(origin.offset(8,2,17)));
         assertEquals(origin.offset(6,2,5),p.routes().get("fuel").getFirst());
         assertTrue(p.routes().get("fuel").containsAll(List.of(origin.offset(6,2,7),origin.offset(6,4,7),origin.offset(6,4,13),origin.offset(6,2,13),origin.offset(6,2,15))));
        var injector=p.devicePosition(TegContinuousFuelPlan.DeviceRole.INJECTOR);
         assertEquals(origin.offset(6,2,16),injector);assertEquals(origin.offset(6,2,17),injector.relative(Direction.SOUTH));
        assertTrue(p.routes().get("fuel").contains(injector.relative(Direction.NORTH)));
         assertTrue(p.routes().get("fuel").contains(origin.offset(6,2,14)));
        assertTrue(Collections.disjoint(p.routes().get("fuel"),hot));
        assertEquals(27,p.fueled().roomInterior().size());
        var finite=TegFueledFixturePlan.layout(core);
         assertFalse(finite.hotComponents().get(2).contains(origin.offset(2,2,17)),"continuous-only long return does not extend the finite layout");
        assertEquals("BURN CHAMBER\nO2+PLASMA\nCO2+HEAT",p.presentationSigns().get(origin.offset(5,2,12)).text());
        assertFalse(p.presentationSigns().containsKey(origin.offset(6,2,14)));
        assertFalse(p.presentationSigns().containsKey(origin.offset(5,2,14)));
    }

    @Test void continuousConfinedSpacesHaveReadablePresentationLabels() {
        var p=TegContinuousFuelPlan.layout(new BlockPos(40,64,40));
        var labels=p.presentationSigns().values().stream().map(TegFueledFixturePlan.PresentationLabel::text).collect(java.util.stream.Collectors.toSet());
        assertEquals(18,p.roomA().size());assertEquals(18,p.roomB().size());
        assertFalse(labels.contains("HOT BURN"));
        assertTrue(labels.stream().anyMatch(text->text.contains("BURN CHAMBER")));
        assertTrue(labels.stream().anyMatch(text->text.contains("OXYGEN SOURCE")&&text.contains("O2 MINER")));
        assertTrue(labels.stream().anyMatch(text->text.contains("PLASMA SOURCE")&&text.contains("FUEL MINER")));
        assertTrue(labels.stream().anyMatch(text->text.contains("COOLANT ARRAY")&&text.contains("5 RADIATORS")));
        assertTrue(labels.contains("TEG"));
        assertTrue(labels.stream().anyMatch(text->text.contains("STARTUP HEATER")&&text.contains("PAID PREHEAT")));
        assertTrue(labels.stream().anyMatch(text->text.contains("CLOSED PLASMA")));
        assertFalse(labels.stream().anyMatch(text->text.contains("COOLANT CHAMBER")));
          assertFalse(p.presentationSigns().containsKey(p.origin().offset(5,2,14)));
         assertFalse(p.presentationSigns().containsKey(p.origin().offset(6,2,14)));
        assertTrue(p.presentationSigns().values().stream().anyMatch(s->s.text().contains("CO2+HEAT")));
        assertTrue(p.presentationSigns().values().stream().allMatch(s->s.text().split("\\n",-1).length<=4&&Arrays.stream(s.text().split("\\n",-1)).allMatch(line->line.length()<=15)));
        var glass=TegContinuousFuelPlan.sourceObservationWindows(new BlockPos(40,64,40));
        assertEquals(16,glass.size());assertFalse(glass.contains(p.origin().offset(2,2,2)));assertFalse(glass.contains(p.origin().offset(10,2,2)));
        assertTrue(glass.stream().noneMatch(p.roomA()::contains));assertTrue(glass.stream().noneMatch(p.roomB()::contains));
    }

    @Test void locatesBothHeatersOnCarrierLoopWithReachableControlsAndLabels() {
        var origin=new BlockPos(40,64,40);var p=TegContinuousFuelPlan.layout(origin);var heater=p.core().offset(-11,0,-2);
        assertEquals(24,p.fueled().machines().size()+p.devices().size()+1); // continuous array adds second startup heater, five radiators and pressure pump
        var heaterMachines=p.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList();assertEquals(2,heaterMachines.size());assertTrue(heaterMachines.stream().anyMatch(m->m.position().equals(heater)&&m.facing()==Direction.NORTH));assertTrue(heaterMachines.stream().anyMatch(m->m.position().equals(p.core().offset(-9,0,-6))&&m.facing()==Direction.SOUTH));
        assertEquals(2,p.fueled().pairings().stream().filter(pairing->pairing.component().equals("heater-branch")).count());
        var hotExchanger=p.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.EXCHANGER).findFirst().orElseThrow();
        assertEquals(p.core().offset(-16,0,-1),hotExchanger.position());
        assertTrue(p.fueled().roomInterior().contains(hotExchanger.position()),"continuous carrier reuses the ordinary finite-layout hot pickup");
        assertEquals(List.of(p.core().offset(-16,0,-1),p.core().offset(-16,1,-1),p.core().offset(-16,2,-1)),p.hotExchangers());
        assertEquals(List.of(p.core().offset(-15,1,-1),p.core().offset(-15,2,-1),p.core().offset(-17,1,-1),p.core().offset(-17,2,-1)),p.hotBranches());
        assertTrue(p.fueled().hotComponents().get(1).contains(p.core().offset(-15,0,-1)));
        assertTrue(p.fueled().hotComponents().get(2).contains(p.core().offset(-17,0,-1)));
        var hotPipes=p.hotPipeComponents();
        for(int y=1;y<=2;y++){assertTrue(hotPipes.get(1).contains(p.core().offset(-15,y,-1)));assertFalse(hotPipes.get(2).contains(p.core().offset(-15,y,-1)));assertTrue(hotPipes.get(2).contains(p.core().offset(-17,y,-1)));assertFalse(hotPipes.get(1).contains(p.core().offset(-17,y,-1)));}
        var masks=p.hotPipeConnections();
        assertTrue(masks.get(p.core().offset(-15,0,-1)).contains(Direction.UP));assertTrue(masks.get(p.core().offset(-15,1,-1)).containsAll(Set.of(Direction.DOWN,Direction.UP)));assertTrue(masks.get(p.core().offset(-15,2,-1)).contains(Direction.DOWN));
        assertTrue(masks.get(p.core().offset(-17,0,-1)).contains(Direction.UP));assertTrue(masks.get(p.core().offset(-17,1,-1)).containsAll(Set.of(Direction.DOWN,Direction.UP)));assertTrue(masks.get(p.core().offset(-17,2,-1)).contains(Direction.DOWN));
        assertEquals(27,p.fueled().roomInterior().size());
        assertTrue(p.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.HOT_HEAT_EXCHANGER).allMatch(d->d.facing()==Direction.EAST));
        assertTrue(p.fueled().hotComponents().get(1).contains(heater.relative(Direction.SOUTH)));
        var auxiliaryHeater=p.core().offset(-9,0,-6);assertTrue(p.fueled().hotComponents().get(2).contains(auxiliaryHeater.relative(Direction.NORTH)));
         assertEquals(new TegFueledFixturePlan.PresentationLabel("STARTUP HEATER\nPAID PREHEAT\nTARGET 590 K",Direction.NORTH),p.presentationSigns().get(p.core().offset(-12,0,-3)));
         assertEquals(new TegFueledFixturePlan.PresentationLabel("STARTUP HEATER\nPAID PREHEAT\nTARGET 590 K",Direction.SOUTH),p.presentationSigns().get(p.core().offset(-9,0,-8)));
         assertNotEquals(p.fueled().presentationSigns().get(p.core().offset(-12,0,-3)),p.presentationSigns().get(p.core().offset(-12,0,-3)),"continuous outer plan overrides the base heater label consumed by placement");
         assertNotEquals(p.fueled().presentationSigns().get(p.core().offset(-9,0,-8)),p.presentationSigns().get(p.core().offset(-9,0,-8)),"continuous outer plan overrides the auxiliary label consumed by placement");
        assertFalse(p.fueled().presentationSigns().containsKey(origin.offset(7,2,5)));assertTrue(p.fueled().machines().stream().anyMatch(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER&&m.position().equals(origin.offset(11,2,16))));
        var front=heater.relative(Direction.NORTH);assertFalse(p.fueled().allOwnedCells().contains(front));assertFalse(p.fueled().sharedCableHosts().contains(front));
    }

    @Test void paidCarrierPreheatBudgetKeepsFreshColdFuelAbovePlasmaIgnitionMargin() {
        var profiles=machineCatalog();
        var heaterProfile=profiles.get(ModAtmosMachines.id("gas_thermo_heater"));
        var origin=new BlockPos(40,64,40);
        var layout=TegContinuousFuelPlan.layout(origin,profiles.get(ModAtmosMachines.id("gas_mixer")),
                profiles.get(ModAtmosMachines.id("passive_heat_exchanger")));
        var actualPorts=new LinkedHashMap<BlockPos,List<com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost.Port>>();
        for(var machine:layout.fueled().machines()) {
            String id=switch(machine.kind()) {
                case CIRCULATOR -> "teg_circulator";
                case PUMP -> "gas_volume_pump";
                case PRESSURE_PUMP -> "gas_pressure_pump";
                case EXCHANGER -> "passive_heat_exchanger";
                case HEATER -> "gas_thermo_heater";
            };
            var profile=profiles.get(ModAtmosMachines.id(id));
            actualPorts.put(machine.position(),TegContinuousFixtureTopologyTest.descriptors(profile,machine.facing()));
        }
        for(var device:layout.devices()) {
            if(device.role()==TegContinuousFuelPlan.DeviceRole.MIXER
                    ||device.role()==TegContinuousFuelPlan.DeviceRole.PASSIVE_HEAT_EXCHANGER
                    ||device.role()==TegContinuousFuelPlan.DeviceRole.HOT_HEAT_EXCHANGER
                    ||device.role()==TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP) {
                var profile=profiles.get(ModAtmosMachines.id(device.role()==TegContinuousFuelPlan.DeviceRole.MIXER
                        ?"gas_mixer":device.role()==TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP?"gas_pressure_pump":"passive_heat_exchanger"));
                actualPorts.put(device.position(),TegContinuousFixtureTopologyTest.descriptors(profile,device.facing()));
            }
        }
        var topology=TegContinuousFixtureTopology.plan(layout,actualPorts);
         var heater=layout.fueled().machines().stream().filter(machine->machine.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow();
        var heaterPort=actualPorts.get(heater.position()).stream().filter(port->port.id().equals("pipe")).findFirst().orElseThrow();
        var heaterKey=heaterPort.key(heater.position());
        int heaterComponent=-1;
        for(int i=0;i<3;i++)if(topology.components().get(i).contains(heaterKey))heaterComponent=i;
         assertEquals(1,heaterComponent,"resolved heater port must be in actual hot-loop component 1");
         var secondHeater=layout.fueled().machines().stream().filter(machine->machine.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList().get(1);
         var secondHeaterPort=actualPorts.get(secondHeater.position()).stream().filter(port->port.id().equals("pipe")).findFirst().orElseThrow();
         var secondHeaterKey=secondHeaterPort.key(secondHeater.position());int secondHeaterComponent=-1;
         for(int i=0;i<3;i++)if(topology.components().get(i).contains(secondHeaterKey))secondHeaterComponent=i;
         assertEquals(2,secondHeaterComponent,"relocated heater port uses existing long hot-return component 2");
        var componentVolumes=new ArrayList<Double>();
        for(int i=0;i<3;i++) {
            double volume=0;
            for(var node:topology.components().get(i)) {
                var port=actualPorts.getOrDefault(node.position(),List.of()).stream()
                        .filter(candidate->candidate.id().equals(node.portId())).findFirst().orElse(null);
                volume+=port==null?com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3:port.volume();
            }
            componentVolumes.add(volume);
        }
        assertTrue(componentVolumes.stream().allMatch(v->v>0&&Double.isFinite(v)));
         assertEquals(16.0,componentVolumes.stream().mapToDouble(Double::doubleValue).sum(),1e-9,"moving the existing heater adds no gas node or volume");
        var hotMoles=TegFueledFixturePlan.apportionFiniteMoles(43,componentVolumes);
        double carrierN2Moles=hotMoles.stream().mapToDouble(Double::doubleValue).sum();
        assertTrue(hotMoles.get(heaterComponent)>0&&hotMoles.get(heaterComponent)<10,
                "actual heater connection owns only its small resolved pump-outlet share, not the full carrier loop");
        var coldCarrier=GasMixture.vacuum();var warmCarrier=GasMixture.vacuum();var warm550Carrier=GasMixture.vacuum();var heaterOnlyCarrier=GasMixture.vacuum();
        double preheatJ=0;
        for(int i=0;i<3;i++) {
            var cold=new GasMixture(Map.of(GasType.NITROGEN,hotMoles.get(i)),293.15,componentVolumes.get(i));
            var warm=new GasMixture(Map.of(GasType.NITROGEN,hotMoles.get(i)),TegContinuousFixtureService.STARTUP_CARRIER_MINIMUM_K,componentVolumes.get(i));
            var warm550=new GasMixture(Map.of(GasType.NITROGEN,hotMoles.get(i)),550,componentVolumes.get(i));
            double oneHotTemperature=i==heaterComponent?heaterProfile.thermostat().maximumTemperatureK():293.15;
            var heaterOnly=new GasMixture(Map.of(GasType.NITROGEN,hotMoles.get(i)),oneHotTemperature,componentVolumes.get(i));
            coldCarrier=coldCarrier.mergedWith(cold);warmCarrier=warmCarrier.mergedWith(warm);warm550Carrier=warm550Carrier.mergedWith(warm550);heaterOnlyCarrier=heaterOnlyCarrier.mergedWith(heaterOnly);
            preheatJ+=warm.heatCapacity()*(warm.temperatureKelvin()-293.15);
        }
        assertEquals(43,carrierN2Moles,1e-5,"resolved component volume allocation retains the 43 mol physical loop inventory");
        assertEquals(43*GasType.NITROGEN.molarHeatCapacity(),warmCarrier.heatCapacity(),2,
                "all three actual hot components contribute to carrier heat capacity");
        assertTrue(heaterProfile.thermostat().maximumTemperatureK()>=TegContinuousFixtureService.STARTUP_CARRIER_MINIMUM_K+heaterProfile.thermostat().toleranceK(),
                "resolved heater maximum supports the 590 K whole-loop target plus its tolerance");
        assertEquals(43*GasType.NITROGEN.molarHeatCapacity()*(TegContinuousFixtureService.STARTUP_CARRIER_MINIMUM_K-293.15),preheatJ,100,
                "whole-loop 43 mol carrier inventory uses actual gas Cp");
        double paidPreheatJ=preheatJ/heaterProfile.thermostat().coefficientOfPerformance();
        double heaterOnSeconds=paidPreheatJ/heaterProfile.thermostat().inputPowerWatts();
        double paidStartupPlusAuxJ=paidPreheatJ+(1525+200)*heaterOnSeconds;
        assertTrue(paidStartupPlusAuxJ<1_000_000,
                "whole-loop preheat plus existing 1.525 kW startup auxiliary load remains inside finite 1 MJ resource budget");

        double roomVolume=27*com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits.minecraftCellVolumeCubicMeters();
        double coldFuelMoles=TegContinuousOperatingPlan.IGNITION_BURNER_SUPPLY_TARGET_KPA*roomVolume*1000/(8.3144626*293.15);
        double oxygenFraction=TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION;
        var coldFuel=new GasMixture(Map.of(GasType.OXYGEN,coldFuelMoles*oxygenFraction,GasType.PLASMA,coldFuelMoles*(1d-oxygenFraction)),293.15,roomVolume);
        var admitted=warmCarrier.mergedWith(coldFuel);
        double ignitionPlusMargin=com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator.PLASMA_MINIMUM_TEMPERATURE_KELVIN+20;
        assertTrue(admitted.temperatureKelvin()>ignitionPlusMargin,"conservative fully cold planned-pressure fuel gulp remains above reaction threshold +20 K: "+admitted.temperatureKelvin());
        assertTrue(warm550Carrier.mergedWith(coldFuel).temperatureKelvin()<ignitionPlusMargin,
                "550 K whole-loop preheat passes the minimum quench budget, but is below the selected paid-heater characterization target");
        assertEquals(warmCarrier.moles(GasType.NITROGEN),admitted.moles(GasType.NITROGEN),1e-5);
        assertEquals(coldFuel.moles(GasType.OXYGEN),admitted.moles(GasType.OXYGEN),1e-5);
        assertEquals(coldFuel.moles(GasType.PLASMA),admitted.moles(GasType.PLASMA),1e-5);

        assertEquals(293.15,coldCarrier.temperatureKelvin(),.02);
        assertTrue(warmCarrier.temperatureKelvin()>=TegContinuousFixtureService.STARTUP_CARRIER_MINIMUM_K-0.1);
        assertTrue(warmCarrier.mergedWith(coldFuel).temperatureKelvin()>ignitionPlusMargin,
                "all three actual hot-loop components preheated to planned 590 K survive conservative cold gulp");
        assertTrue(heaterOnlyCarrier.mergedWith(coldFuel).temperatureKelvin()<ignitionPlusMargin,
                "even heating only the real small pump-outlet component to its resolved 593.15 K maximum is insufficient");
        var marginalCarrier=GasMixture.vacuum();
        for(int i=0;i<3;i++)marginalCarrier=marginalCarrier.mergedWith(new GasMixture(Map.of(GasType.NITROGEN,hotMoles.get(i)),450,componentVolumes.get(i)));
        assertTrue(marginalCarrier.mergedWith(coldFuel).temperatureKelvin()<com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator.PLASMA_MINIMUM_TEMPERATURE_KELVIN,
                "a 450 K whole-loop mean remains below plasma ignition temperature after the same cold gulp");
    }

    @Test void lowerFeedLoadingBudgetIsAnActualGasAndReactionDesignEstimate() {
        // Design estimate only: finite requested-feed inventory mixed with the specified whole
        // carrier packet. This is not a replacement for native-world mixing/flow validation.
        double roomVolume=27*com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits.minecraftCellVolumeCubicMeters();
        double gasConstant=8.3144626;
        double oldPressureKpa=5, newPressureKpa=TegContinuousOperatingPlan.IGNITION_BURNER_SUPPLY_TARGET_KPA;
        double oldMoles=oldPressureKpa*1000*roomVolume/(gasConstant*293.15);
        double newMoles=newPressureKpa*1000*roomVolume/(gasConstant*293.15);
        assertEquals(1d,newMoles/oldMoles,1e-12,"5 kPa supply retains the original finite cold-feed loading");
        double oxygenFraction=TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION;
        var oldFuel=new GasMixture(Map.of(GasType.OXYGEN,oldMoles*oxygenFraction,GasType.PLASMA,oldMoles*(1-oxygenFraction)),293.15,roomVolume);
        var newFuel=new GasMixture(Map.of(GasType.OXYGEN,newMoles*oxygenFraction,GasType.PLASMA,newMoles*(1-oxygenFraction)),293.15,roomVolume);
        var carrier=new GasMixture(Map.of(GasType.NITROGEN,43d),590,roomVolume);
        double oldFeedCp=oldFuel.heatCapacity(),newFeedCp=newFuel.heatCapacity();
        assertEquals(1d,newFeedCp/oldFeedCp,1e-6);
        var oldMix=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger.merge(List.of(carrier,oldFuel));
        var newMix=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger.merge(List.of(carrier,newFuel));
        assertEquals(oldMix.temperatureKelvin(),newMix.temperatureKelvin(),1e-12,"original 5 kPa feed pressure retains prior finite cold-feed loading");
        assertTrue(newMix.temperatureKelvin()>373.15,"5 kPa estimate remains above plasma ignition floor");
        var reactions=com.juicyslew.moonstation14.ms14.atmos.reaction.BundledGasReactionCatalog.load();
        var reaction=com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator.evaluate(reactions,newMix,
                effect->effect==com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData.EffectType.PLASMA_FIRE);
        assertTrue(-reaction.speciesDelta().getOrDefault(GasType.PLASMA,0d)>0,"hypothetical plasma fire is eligible and consumes plasma");
    }

    @Test void continuousExternalColdArrayPreservesFinitePlan() {
        var continuous=TegContinuousFuelPlan.layout(new BlockPos(40,64,40));
        var base=continuous.fueled();var components=continuous.coldPipeComponents();assertEquals(4,components.size());
        assertEquals(5,continuous.radiatorPositions().size());
        var external=TegContinuousCoolingPlan.layout(continuous.core());
        assertEquals(external.pipeComponents().stream().map(Set::copyOf).collect(java.util.stream.Collectors.toSet()),components.stream().map(Set::copyOf).collect(java.util.stream.Collectors.toSet()));
        assertEquals(4,base.coldComponents().size());
        for(var component:components)assertTrue(isConnected(component));
        assertTrue(components.stream().noneMatch(c->continuous.radiatorPositions().stream().anyMatch(c::contains)));
        var legacy=TegFueledFixturePlan.layout(continuous.core());
        assertEquals(98,legacy.coldWallShell().size(),"legacy finite coolant room shell remains intact");
        assertEquals(27,legacy.roomInterior().size(),"legacy finite exchanger room volume remains intact");
        assertNotEquals(legacy.hotComponents(),base.hotComponents(),"continuous-only return is compact while finite geometry remains unchanged");
         assertFalse(legacy.hotComponents().get(2).contains(continuous.origin().offset(2,2,17)),"continuous-only return does not extend finite geometry");
        Set<BlockPos> actual=new java.util.HashSet<>();components.forEach(actual::addAll);
        for(BlockPos pos:actual){assertTrue(pos.getX()>=continuous.origin().getX()&&pos.getX()<continuous.origin().getX()+TegContinuousFuelPlan.WIDTH);assertTrue(pos.getY()>=continuous.origin().getY()&&pos.getY()<continuous.origin().getY()+TegContinuousFuelPlan.HEIGHT);assertTrue(pos.getZ()>=continuous.origin().getZ()&&pos.getZ()<continuous.origin().getZ()+TegContinuousFuelPlan.DEPTH);}
    }

    @Test void resolvedRadiatorCompositionDefinesInlineColdLoopPorts() {
        var profiles=machineCatalog();var mixer=profiles.get(ModAtmosMachines.id("gas_mixer"));var radiator=profiles.get(ModAtmosMachines.id("passive_heat_exchanger"));
        var plan=TegContinuousFuelPlan.layout(new BlockPos(40,64,40),mixer,radiator);var ports=plan.radiatorPorts(radiator);
        assertEquals(10,ports.size());assertEquals(5,ports.stream().map(TegContinuousFuelPlan.Port::body).distinct().count());
        assertTrue(ports.stream().filter(p->p.name()==AtmosMachineProfile.PortName.INLET).allMatch(p->p.port()==Direction.WEST));
        assertTrue(ports.stream().filter(p->p.name()==AtmosMachineProfile.PortName.OUTLET).allMatch(p->p.port()==Direction.EAST));
        assertTrue(ports.stream().allMatch(p->p.facing()==Direction.WEST));
        var cold=plan.coldPipeComponents();assertEquals(4,cold.size());
        for(var p:ports)assertTrue(cold.stream().anyMatch(c->c.contains(p.body().relative(p.port()))));
        assertNull(radiator.power());
        assertThrows(IllegalArgumentException.class,()->plan.radiatorPorts(mixer));
    }

    @Test void profilePortDirectionTransformMatchesAllHorizontalFacings(){
        for(Direction facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            assertEquals(facing,AtmosMachinePortDirections.worldDirection(AtmosMachineProfile.PortDirection.NORTH,facing));
            assertEquals(facing.getOpposite(),AtmosMachinePortDirections.worldDirection(AtmosMachineProfile.PortDirection.SOUTH,facing));
        }
    }

    private static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<AtmosMachineProfile> mixerCatalog(){
        var manager=new PrototypeManager();manager.register(ModAtmosMachines.TYPE);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String id:new String[]{"atmos_machine_base","gas_pipe_sans_layers","gas_pipe_base","gas_trinary_base","gas_mixer"}){
            String path="/data/moonstation14/moonstation14/atmos_machine/"+id+".json";
            try(var in=TegContinuousFuelPlanTest.class.getResourceAsStream(path)){
                assertNotNull(in,path);raw.put(ModAtmosMachines.id(id),JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            }catch(java.io.IOException e){throw new RuntimeException(e);}
        }
        manager.reload(ModAtmosMachines.TYPE,raw);return manager.snapshot(ModAtmosMachines.TYPE);
    }
    public static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<AtmosMachineProfile> machineCatalog(){
        var manager=new PrototypeManager();manager.register(ModAtmosMachines.TYPE);var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String id:new String[]{"atmos_machine_base","atmos_machine_base_powered","constructible_machine_base","gas_pipe_sans_layers","gas_pipe_base","gas_binary_base","gas_trinary_base","gas_thermo_base","teg_center","teg_circulator","gas_volume_pump","gas_pressure_pump","gas_thermo_heater","gas_mixer","gas_valve","passive_heat_exchanger"}){String path="/data/moonstation14/moonstation14/atmos_machine/"+id+".json";try(var in=TegContinuousFuelPlanTest.class.getResourceAsStream(path)){assertNotNull(in,path);raw.put(ModAtmosMachines.id(id),JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());}catch(java.io.IOException e){throw new RuntimeException(e);}}
        manager.reload(ModAtmosMachines.TYPE,raw);return manager.snapshot(ModAtmosMachines.TYPE);
    }
    private static boolean isConnected(List<BlockPos> nodes){if(nodes.isEmpty())return false;Set<BlockPos> allowed=new HashSet<>(nodes),visited=new HashSet<>();ArrayDeque<BlockPos> pending=new ArrayDeque<>();pending.add(nodes.getFirst());visited.add(nodes.getFirst());while(!pending.isEmpty()){BlockPos current=pending.removeFirst();for(Direction direction:Direction.values()){BlockPos next=current.relative(direction);if(allowed.contains(next)&&visited.add(next))pending.addLast(next);}}return visited.size()==allowed.size();}
    private static Set<Set<BlockPos>> componentSets(List<List<BlockPos>> components){Set<Set<BlockPos>> result=new HashSet<>();components.forEach(c->result.add(Set.copyOf(c)));return result;}
}

