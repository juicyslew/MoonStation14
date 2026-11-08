package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.*;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerLiveLoop;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TegContinuousPowerPlanTest {
    @Test void startupReceiverRosterRejectsOmissionSubstitutionAndDuplicateIdentities(){
        var layout=TegContinuousFuelPlan.layout(new BlockPos(40,64,40));
        var roster=TegContinuousPowerPlan.startupReceiverPositions(layout).mainApc();
        var identities=TegContinuousPowerPlan.receiverIdentities(roster);
        assertTrue(TegContinuousPowerPlan.exactReceiverRoster(roster,identities));
        assertFalse(TegContinuousPowerPlan.exactReceiverRoster(roster,identities.subList(1,identities.size())),"omitted receiver is rejected");
        var substituted=new ArrayList<>(identities);var first=substituted.getFirst();
        substituted.set(0,new TegContinuousPowerPlan.ReceiverIdentity(first.position(),
                first.kind()==TegContinuousPowerPlan.StartupReceiverKind.TEG_CORE?TegContinuousPowerPlan.StartupReceiverKind.POWER_LAMP:TegContinuousPowerPlan.StartupReceiverKind.TEG_CORE));
        assertFalse(TegContinuousPowerPlan.exactReceiverRoster(roster,substituted),"receiver kind substitution is rejected");
        var duplicate=new ArrayList<>(identities);duplicate.add(identities.getFirst());
        assertFalse(TegContinuousPowerPlan.exactReceiverRoster(roster,duplicate),"duplicate receiver identity is rejected");
    }

    @Test void zeroGenerationStartupPaysBothSeparateApCIslandsWithinExistingDischargeCaps(){
        var split=TegContinuousPowerPlan.splitBootstrapByResolvedDemand(TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES,List.of(
                new TegContinuousPowerPlan.StartupReceiver(new BlockPos(1,2,3),TegContinuousPowerPlan.StartupCircuit.OPERATING,6_725),
                new TegContinuousPowerPlan.StartupReceiver(new BlockPos(4,5,6),TegContinuousPowerPlan.StartupCircuit.SECONDARY_HEATER,5_000)));
        assertEquals(573_560.77,split.operatingJoules(),.01);
        assertEquals(426_439.23,split.secondaryJoules(),.01);
        assertEquals(TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES,split.operatingJoules()+split.secondaryJoules(),1e-9);
        assertEquals(6_725,split.operatingDemandWatts(),0);assertEquals(5_000,split.secondaryDemandWatts(),0);
        assertEquals(split.operatingJoules()/split.operatingDemandWatts(),split.secondaryJoules()/split.secondaryDemandWatts(),1e-12);
        assertEquals(85.29,split.operatingJoules()/split.operatingDemandWatts(),.01,"zero-generation full-load capacity is an estimate, not an old-deadline promise");
        var result=PowerLiveLoop.solve(new PowerLiveLoop.Input(List.of(),List.of(),List.of(
                new PowerLiveLoop.Apc("main-startup",-1,false,1,true,true,split.operatingJoules()),
                new PowerLiveLoop.Apc("second-heater-startup",-1,false,2,true,true,split.secondaryJoules())),List.of(),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(
                new PowerLiveLoop.ApcConsumer("plant",1,true,1_725),
                new PowerLiveLoop.ApcConsumer("first-heater",1,true,5_000),
                new PowerLiveLoop.ApcConsumer("second-heater",2,true,5_000)),List.of(),20));
        assertEquals(0,result.apcInputWatts().getOrDefault("main-startup",0d),0);
        assertEquals(0,result.apcInputWatts().getOrDefault("second-heater-startup",0d),0);
        assertEquals(1_725,result.apcConsumerWatts().get("plant"),1e-6);
        assertEquals(5_000,result.apcConsumerWatts().get("first-heater"),1e-6);
        assertEquals(5_000,result.apcConsumerWatts().get("second-heater"),1e-6);
        assertEquals(6_725,result.apcOutputWatts().get("main-startup"),1e-6);
        assertEquals(5_000,result.apcOutputWatts().get("second-heater-startup"),1e-6);
        assertTrue(result.apcOutputWatts().get("main-startup")<10_000);
        assertTrue(result.apcOutputWatts().get("second-heater-startup")<10_000);
        assertEquals(split.operatingJoules()-6_725,result.batteryEnergyJoules().get("main-startup"),1e-6);
        assertEquals(split.secondaryJoules()-5_000,result.batteryEnergyJoules().get("second-heater-startup"),1e-6);
        assertEquals(TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES-11_725,result.batteryEnergyJoules().values().stream().mapToDouble(Double::doubleValue).sum(),1e-6);
    }
    @Test void proportionalBootstrapRejectsMissingCircuitDuplicateReceiverAndPerApcCapacityOverrun(){
        var main=new TegContinuousPowerPlan.StartupReceiver(new BlockPos(1,2,3),TegContinuousPowerPlan.StartupCircuit.OPERATING,6_725);
        var auxiliary=new TegContinuousPowerPlan.StartupReceiver(new BlockPos(4,5,6),TegContinuousPowerPlan.StartupCircuit.SECONDARY_HEATER,5_000);
        assertThrows(IllegalArgumentException.class,()->TegContinuousPowerPlan.splitBootstrapByResolvedDemand(1_000_000,List.of(main)));
        assertThrows(IllegalArgumentException.class,()->TegContinuousPowerPlan.splitBootstrapByResolvedDemand(1_000_000,List.of(main,
                new TegContinuousPowerPlan.StartupReceiver(main.position(),TegContinuousPowerPlan.StartupCircuit.SECONDARY_HEATER,5_000))));
        assertThrows(IllegalArgumentException.class,()->TegContinuousPowerPlan.splitBootstrapByResolvedDemand(2_000_000,List.of(main,auxiliary)));
    }

    @Test void operatingAndSecondStartupApCsRemainSeparateWithOnlyOriginalBootstrapTotal() {
        var origin=new BlockPos(40,64,40);var layout=TegContinuousFuelPlan.layout(origin);
        var cables=TegContinuousPowerPlan.cables(origin);var eq=TegContinuousPowerPlan.equipment(layout);
         var core=layout.core();
         assertTrue(layout.fueled().startupPowerValid(),"legacy/main startup APC island remains valid");
         var heater=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow().position();
         var heaters=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList();assertEquals(2,heaters.size());
         var plannedReceivers=TegContinuousPowerPlan.startupReceiverPositions(layout);
         Set<BlockPos> goldenMainReceivers=new HashSet<>();goldenMainReceivers.add(core);
         layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PUMP||m.kind()==TegFueledFixturePlan.MachineKind.PRESSURE_PUMP
                 ||m.position().equals(heaters.getFirst().position())).map(TegFueledFixturePlan.Machine::position).forEach(goldenMainReceivers::add);
         for(var role:List.of(TegContinuousFuelPlan.DeviceRole.SCRUBBER,TegContinuousFuelPlan.DeviceRole.INJECTOR,
                 TegContinuousFuelPlan.DeviceRole.WASTE_PUMP,TegContinuousFuelPlan.DeviceRole.VENT,TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP))
             goldenMainReceivers.addAll(layout.devicePositions(role));
         goldenMainReceivers.add(eq.lamp());
         assertEquals(goldenMainReceivers,plannedReceivers.mainApc().keySet(),"full typed main circuit membership derives from layout roles");
         assertEquals(Map.of(heaters.get(1).position(),TegContinuousPowerPlan.StartupReceiverKind.HEATER),plannedReceivers.secondaryApc());
         assertEquals(11,plannedReceivers.mainApc().size());
         assertEquals(TegContinuousPowerPlan.StartupReceiverKind.HEATER,plannedReceivers.secondaryApc().get(heaters.get(1).position()));
         assertFalse(plannedReceivers.mainApc().containsKey(heaters.get(1).position()),"auxiliary heater cannot silently enter the main provider roster");
         assertEquals(TegContinuousPowerPlan.StartupReceiverKind.HEATER,plannedReceivers.mainApc().get(heaters.getFirst().position()));
         assertTrue(plannedReceivers.mainApc().containsKey(eq.lamp()));
         assertEquals(core.offset(-9,1,-6),eq.secondaryStartupApc());
         assertTrue(layout.machines().contains(eq.secondaryStartupApc()),"auxiliary APC position is reserved by the fixture plan");
         assertEquals(1_000_000d,TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES,0);
         var auxiliaryPorts=PowerDeviceKind.APC.ports(eq.secondaryStartupApc(),eq.secondaryStartupApcFacing());
         var auxiliaryOutput=auxiliaryPorts.stream().filter(p->p.tier()==CableTier.APC).findFirst().orElseThrow();
         var auxiliaryMv=auxiliaryPorts.stream().filter(p->p.tier()==CableTier.MV).findFirst().orElseThrow();
         assertEquals(Direction.WEST,eq.secondaryStartupApcFacing());
         assertEquals(Direction.EAST,auxiliaryOutput.face());assertEquals(Direction.WEST,auxiliaryMv.face());
        DevicePort output=new DevicePort(eq.operatingApc(),Direction.EAST,CableTier.APC);
        assertTrue(TegFixturePlan.connected(cables,new DevicePort(core,Direction.EAST,CableTier.HV),new DevicePort(eq.substation(),Direction.EAST,CableTier.HV)));
        assertTrue(TegFixturePlan.connected(cables,new DevicePort(eq.substation(),Direction.WEST,CableTier.MV),new DevicePort(eq.operatingApc(),Direction.WEST,CableTier.MV)));
         var loads=List.of(new DevicePort(core,Direction.WEST,CableTier.APC),
                new DevicePort(core.offset(-8,0,-1),Direction.NORTH,CableTier.APC),
                 new DevicePort(heater,Direction.WEST,CableTier.APC),
                new DevicePort(core.offset(8,0,1),Direction.SOUTH,CableTier.APC),
                 new DevicePort(layout.devicePositions(TegContinuousFuelPlan.DeviceRole.SCRUBBER).getFirst(),Direction.DOWN,CableTier.APC),
                 new DevicePort(layout.devicePositions(TegContinuousFuelPlan.DeviceRole.SCRUBBER).get(1),Direction.DOWN,CableTier.APC),
                 new DevicePort(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.WASTE_PUMP),Direction.DOWN,CableTier.APC),
                  new DevicePort(layout.devicePositions(TegContinuousFuelPlan.DeviceRole.VENT).getFirst(),Direction.DOWN,CableTier.APC),
                   new DevicePort(eq.lamp(),Direction.NORTH,CableTier.APC),
                   new DevicePort(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP),Direction.SOUTH,CableTier.APC));
             assertEquals(10,loads.size(),"main APC retains the original direct receiver roster; injector uses extension");
             assertEquals(11,layout.devices().stream().filter(d->switch(d.role()){case SCRUBBER,WASTE_PUMP,VENT,INJECTOR,COLD_PRESSURE_PUMP->true;default->false;}).count()+5,"original eleven operating receivers remain declared");
             assertEquals(10,loads.stream().map(DevicePort::device).collect(java.util.stream.Collectors.toSet()).size());
             assertEquals(10,new HashSet<>(loads).size());
           assertFalse(loads.stream().anyMatch(load->load.device().equals(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.INJECTOR))));
        for(var hot:layout.hotExchangers().subList(1,3))assertFalse(loads.stream().anyMatch(load->load.device().equals(hot)));
        var exhaustBody=layout.wasteVentBody();
        var exhaustContact=new CableFaceNode(exhaustBody.below(),Direction.UP,CableTier.APC);
        assertTrue(cables.contains(exhaustContact),"exhaust body DOWN contact exists");
        var exhaustFloor=new CableFaceNode(exhaustBody.below().below().below(),Direction.UP,CableTier.APC);
         var exhaustTurn=new CableFaceNode(exhaustFloor.host().above().relative(Direction.EAST),Direction.WEST,CableTier.APC);
        assertTrue(PowerTopology.cableEdge(exhaustFloor,new CableFaceNode(exhaustFloor.host().relative(Direction.EAST),Direction.UP,CableTier.APC)).isPresent());
         assertTrue(PowerTopology.cableEdge(exhaustFloor,exhaustTurn).isPresent());
         assertTrue(PowerTopology.cableEdge(new CableFaceNode(exhaustFloor.host().above(3).relative(Direction.EAST),Direction.WEST,CableTier.APC),exhaustContact).isPresent());
         var front=layout.wasteVentBody().relative(Direction.SOUTH);
         assertTrue(cables.stream().noneMatch(n->n.host().equals(front)));
         assertTrue(cables.stream().noneMatch(n->n.host().getX()==front.getX()&&n.host().getZ()==front.getZ()&&n.host().getY()>=front.getY()));
         var addedExhaustHosts=Set.of(origin.offset(18,1,18),origin.offset(18,3,18),origin.offset(19,2,18),origin.offset(19,3,18),origin.offset(19,4,18));
         var protectedHosts=new HashSet<BlockPos>();
         protectedHosts.addAll(layout.hotPipeHosts()); protectedHosts.addAll(layout.coldExposedPipes()); protectedHosts.addAll(layout.machines());
         protectedHosts.addAll(layout.roomA()); protectedHosts.addAll(layout.roomB()); protectedHosts.addAll(layout.shellA()); protectedHosts.addAll(layout.shellB());
         protectedHosts.addAll(layout.presentationSigns().keySet()); protectedHosts.add(layout.fueled().electrical().devices().startupApc());
         protectedHosts.addAll(layout.outsideVent()); protectedHosts.add(layout.wasteMountHost()); protectedHosts.add(layout.wasteVentBody());
         assertTrue(Collections.disjoint(addedExhaustHosts,protectedHosts),"new exhaust cable hosts avoid protected pipe, machine, room, label, mount, vent, and startup APC hosts");
         for(var load:loads)assertTrue(TegFixturePlan.connected(cables,output,load),"unreachable load "+load);
         assertTrue(TegContinuousPowerPlan.receiversConnected(cables,output,loads));
          assertTrue(TegContinuousPowerPlan.secondaryStartupCircuitValid(layout,cables),()->{
              BlockPos auxiliary=eq.secondaryStartupApc();var secondHeater=heaters.get(1).position();
                var auxOut=auxiliaryOutput;
                var auxMv=auxiliaryMv;
               var heaterPort=new DevicePort(secondHeater,Direction.SOUTH.getCounterClockWise(),CableTier.APC);
              var operatingOut=new DevicePort(eq.operatingApc(),Direction.EAST,CableTier.APC);
              return "secondary APC circuit conditions: auxiliaryContact="+cables.stream().anyMatch(n->PowerTopology.deviceAdjacent(auxOut,n))
                      +", heaterContact="+cables.stream().anyMatch(n->PowerTopology.deviceAdjacent(heaterPort,n))
                      +", isolatedMvStub="+cables.stream().filter(n->n.tier()==CableTier.MV&&PowerTopology.deviceAdjacent(auxMv,n)).count()
                      +", auxToHeater="+TegFixturePlan.connected(cables,auxOut,heaterPort)
                      +", operatingToHeater="+TegFixturePlan.connected(cables,operatingOut,heaterPort)
                      +", operatingToAux="+TegFixturePlan.connected(cables,operatingOut,auxOut)
                      +", auxMvToSubstation="+TegFixturePlan.connected(cables,auxMv,new DevicePort(eq.substation(),Direction.WEST,CableTier.MV))
                      +", operatingToAuxPath="+topologyPath(cables,operatingOut,auxOut);
          });
          DevicePort secondaryOutput=auxiliaryOutput;
          DevicePort secondaryMv=auxiliaryMv;
          DevicePort secondHeater=new DevicePort(heaters.get(1).position(),Direction.SOUTH.getCounterClockWise(),CableTier.APC);
         assertTrue(TegFixturePlan.connected(cables,secondaryOutput,secondHeater));
         assertFalse(TegFixturePlan.connected(cables,output,secondHeater));
         assertFalse(TegFixturePlan.connected(cables,output,secondaryOutput));
          assertEquals(1,cables.stream().filter(n->n.tier()==CableTier.MV&&PowerTopology.deviceAdjacent(secondaryMv,n)).count(),"one isolated MV cable node makes input topology known without adding a source");
          var isolatedInputContact=TegContinuousPowerPlan.frontContact(secondaryMv);
          assertTrue(cables.contains(isolatedInputContact),"isolated MV stub uses the actual APC front-port contact, not an unsupported arbitrary face");
         assertFalse(TegFixturePlan.connected(cables,secondaryMv,new DevicePort(eq.substation(),Direction.WEST,CableTier.MV)),"auxiliary MV input cannot siphon TEG station output");
         assertTrue(cables.contains(exhaustContact));
         assertTrue(TegFixturePlan.connected(cables,output,new DevicePort(exhaustBody,Direction.DOWN,CableTier.APC)));
         assertTrue(cables.stream().anyMatch(n->n.host().equals(heater.relative(Direction.WEST))&&n.face()==Direction.EAST&&n.tier()==CableTier.APC));
            assertTrue(cables.stream().anyMatch(n->n.host().equals(heaters.get(1).position().relative(Direction.SOUTH.getCounterClockWise()))&&n.face()==Direction.SOUTH.getCounterClockWise().getOpposite()&&n.tier()==CableTier.APC));
         var pressurePump=layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP);
         assertTrue(cables.stream().anyMatch(n->n.host().equals(pressurePump.relative(Direction.SOUTH))&&n.face()==Direction.NORTH&&n.tier()==CableTier.APC));
          assertTrue(cables.stream().anyMatch(n->n.host().equals(pressurePump.relative(Direction.SOUTH).below())&&n.face()==Direction.NORTH&&n.tier()==CableTier.APC));
        var groundBus=cables.stream().filter(n->n.face()==Direction.UP&&n.tier()==CableTier.APC).map(CableFaceNode::host).collect(java.util.stream.Collectors.toSet());
          int bypass=heaters.get(1).position().getX()-origin.getX()-1;
           for(int x=heater.getX()-origin.getX()-1;x<=17;x++)if(x!=bypass&&x!=bypass+1)assertTrue(groundBus.contains(origin.offset(x,1,heater.getZ()-origin.getZ())));
           assertFalse(groundBus.contains(origin.offset(bypass,1,heater.getZ()-origin.getZ())));
           assertFalse(groundBus.contains(origin.offset(bypass+1,1,heater.getZ()-origin.getZ())));
           for(int x=bypass-1;x<=bypass+2;x++)assertTrue(groundBus.contains(origin.offset(x,1,heater.getZ()-origin.getZ()-1)));
         assertTrue(cables.stream().filter(n->n.tier()==CableTier.APC).allMatch(n->Math.abs(n.host().getX()-origin.getX())<=48&&Math.abs(n.host().getZ()-origin.getZ())<=48));
        assertFalse(cables.stream().anyMatch(n->n.host().equals(heater)&&n.face()==Direction.DOWN));
        assertFalse(List.of(eq.substation(),eq.operatingApc(),eq.lamp()).contains(layout.fueled().electrical().devices().startupApc()));
        assertEquals(1,cables.stream().filter(n->n.tier()==CableTier.APC && n.host().equals(eq.operatingApc().relative(Direction.EAST))).count());
        // Remove the endpoint contact serving the source-vent leaf: the output APC can no longer reach every load.
         var firstScrubber=layout.devicePositions(TegContinuousFuelPlan.DeviceRole.SCRUBBER).getFirst();
         var broken=new ArrayList<>(cables);broken.removeIf(n->n.host().equals(firstScrubber.below())&&n.face()==Direction.UP);
          assertFalse(TegContinuousPowerPlan.receiversConnected(broken,output,loads));
         var brokenExhaust=new ArrayList<>(cables);brokenExhaust.remove(exhaustContact);
         assertFalse(TegFixturePlan.connected(brokenExhaust,output,new DevicePort(exhaustBody,Direction.DOWN,CableTier.APC)));
        assertTrue(TegFixturePlan.connected(broken,new DevicePort(core,Direction.WEST,CableTier.APC),new DevicePort(core.offset(-8,0,-1),Direction.NORTH,CableTier.APC)));
         assertFalse(TegFixturePlan.connected(broken,output,new DevicePort(firstScrubber,Direction.DOWN,CableTier.APC)));
    }

    private static List<CableFaceNode> topologyPath(List<CableFaceNode> cables,DevicePort source,DevicePort target) {
        var previous=new HashMap<CableFaceNode,CableFaceNode>();var queue=new ArrayDeque<CableFaceNode>();var seen=new HashSet<CableFaceNode>();
        cables.stream().filter(node->PowerTopology.deviceAdjacent(source,node)).forEach(node->{seen.add(node);queue.add(node);});
        var targets=new HashSet<CableFaceNode>();cables.stream().filter(node->PowerTopology.deviceAdjacent(target,node)).forEach(targets::add);
        CableFaceNode found=null;
        while(!queue.isEmpty()) {
            var current=queue.remove();if(targets.contains(current)){found=current;break;}
            for(var candidate:cables)if(!seen.contains(candidate)&&PowerTopology.cableEdge(current,candidate).isPresent()){
                seen.add(candidate);previous.put(candidate,current);queue.add(candidate);
            }
        }
        if(found==null)return List.of();var path=new ArrayList<CableFaceNode>();
        for(var current=found;current!=null;current=previous.get(current))path.add(current);
        Collections.reverse(path);return List.copyOf(path);
    }
}
