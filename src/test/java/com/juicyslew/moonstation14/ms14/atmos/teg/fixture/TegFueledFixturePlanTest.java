package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Set;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.Direction;
import static org.junit.jupiter.api.Assertions.*;

class TegFueledFixturePlanTest {
    @Test void planReservesConcreteHotLoopAndReusesElectricalOwner() {
        var p=TegFueledFixturePlan.layout(new BlockPos(40,70,40));
        assertEquals(3,p.hotComponents().size());
        assertTrue(p.coldLoopGeometryComplete());
        assertEquals(7,p.machines().size());
        assertEquals(27,p.roomInterior().size());
        assertEquals(p.hotComponents().stream().flatMap(java.util.Collection::stream).distinct().count(),p.hotComponents().stream().flatMap(java.util.Collection::stream).count());
        assertTrue(java.util.Collections.disjoint(p.exposedPipePositions(),p.sealedCrossings()));
        assertTrue(p.wallShell().containsAll(p.sealedCrossings()));
        assertTrue(java.util.Collections.disjoint(p.exposedPipePositions(),p.wallShell()));
        var insideContinuation=p.core().offset(-15,0,-1);
        assertTrue(p.hotComponents().get(1).contains(insideContinuation));
        assertTrue(p.exposedPipePositions().contains(insideContinuation));
        assertTrue(p.roomInterior().contains(insideContinuation));
        assertTrue(p.hotComponents().get(2).contains(p.core().offset(-17,0,-1)));
        assertTrue(p.hotComponents().get(2).contains(p.core().offset(-18,0,-1)));
        assertEquals(3,p.coldComponents().size());
        assertEquals(27,p.coldRoomInterior().size());
        assertEquals(98,p.coldWallShell().size());
        assertEquals(Set.of(p.core().offset(1,0,1)),p.sharedCableHosts());
        assertTrue(p.coldSealedCrossings().contains(p.core().offset(1,0,1)));
        assertTrue(java.util.Collections.disjoint(p.coldExposedPipePositions(),p.coldSealedCrossings()));
        var coldCrossingsWithinRoomShell=new java.util.HashSet<>(p.coldSealedCrossings());coldCrossingsWithinRoomShell.removeAll(p.sharedCableHosts());
        assertTrue(p.coldWallShell().containsAll(coldCrossingsWithinRoomShell));
        assertTrue(p.coldComponents().get(1).contains(p.core().offset(15,0,1)));
        assertTrue(p.coldRoomInterior().contains(p.core().offset(15,0,1)));
        assertTrue(p.coldComponents().get(2).contains(p.core().offset(20,5,1)));
        assertEquals(293.15,p.inventory().nitrogenKelvin());
        var routes=new java.util.ArrayList<java.util.List<net.minecraft.core.BlockPos>>();routes.addAll(p.hotComponents());routes.addAll(p.coldComponents());
        for(int i=0;i<routes.size();i++)for(int j=i+1;j<routes.size();j++)for(var a:routes.get(i))for(var b:routes.get(j))
            assertTrue(a.distManhattan(b)>1,"separate pipe routes must not neighbor directly: "+a+" / "+b);
        assertEquals(p.sharedCableHosts(),intersection(p.allOwnedCells(),p.electrical().cableHosts()));
        assertEquals(7,p.pairings().size());
        assertTrue(TegFixturePlan.powerTopologyValid(p.electrical()));
        assertEquals(293.15,p.inventory().temperatureKelvin());
        assertEquals(.1,p.inventory().plasmaMoles());
        assertEquals(1,p.inventory().oxygenMoles());
    }
    @Test void observationFacesAreInsideFrontWallsAndDoNotReplacePipeCrossings(){
        var p=TegFueledFixturePlan.layout(new BlockPos(40,70,40));var windows=TegFueledFixturePlan.observationWindows(p.core());
        assertEquals(18,windows.size());
        var hotWindows=windows.stream().filter(pos->pos.getX()<=p.core().getX()).collect(java.util.stream.Collectors.toSet());
        var coldWindows=windows.stream().filter(pos->pos.getX()>p.core().getX()).collect(java.util.stream.Collectors.toSet());
        assertEquals(9,hotWindows.size());assertTrue(p.wallShell().containsAll(hotWindows));
        assertEquals(9,coldWindows.size());assertTrue(p.coldWallShell().containsAll(coldWindows));
        assertTrue(java.util.Collections.disjoint(windows,p.roomInterior()));
        assertTrue(java.util.Collections.disjoint(windows,p.coldRoomInterior()));
        assertTrue(java.util.Collections.disjoint(windows,p.sealedCrossings()));
        assertTrue(java.util.Collections.disjoint(windows,p.coldSealedCrossings()));
        assertTrue(windows.contains(p.core().offset(-16,1,-3)));
        assertTrue(windows.contains(p.core().offset(16,1,-1)));
    }
    @Test void labelsAreOutsideRoomsAndClearMachineryAndObservationGlass(){
        var p=TegFueledFixturePlan.layout(new BlockPos(40,70,40));var labels=p.presentationSigns().keySet();
        assertEquals(4,labels.size());assertTrue(p.allOwnedCells().containsAll(labels));
        assertTrue(java.util.Collections.disjoint(labels,p.machines().stream().map(TegFueledFixturePlan.Machine::position).toList()));
        assertTrue(java.util.Collections.disjoint(labels,TegFueledFixturePlan.observationWindows(p.core())));
        var labelPipeOverlap=new java.util.HashSet<>(labels);labelPipeOverlap.retainAll(p.exposedPipePositions());
        assertEquals(java.util.Set.of(p.core().offset(-16,0,-4)),labelPipeOverlap,
                "only the existing HOT BURN presentation label shares its reserved wall-face host with a pipe");
        assertTrue(java.util.Collections.disjoint(labels,p.roomInterior()));assertTrue(java.util.Collections.disjoint(labels,p.coldRoomInterior()));
        assertFalse(labels.contains(p.core().offset(-11,0,-2)));
    }
    @Test void defaultLayoutRetainsFiniteHeaterFootprintAndPower(){
        var core=new BlockPos(22,2,18);var p=TegFueledFixturePlan.layout(core);
        assertEquals(core.offset(-11,0,-2),p.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow().position());
        assertEquals(Direction.NORTH,p.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow().facing());
        assertEquals(TegFueledFixturePlan.signs(core),p.presentationSigns());
        assertTrue(p.startupPowerValid());assertDoesNotThrow(p::combinedCables);
    }
    @Test void relocatedHeaterUsesActualFacingLabelAndBranchAndRejectsFinitePower(){
        var core=new BlockPos(40,70,40);var heater=core.offset(-16,0,-10);var label=core.offset(-15,0,-9);
        var p=TegFueledFixturePlan.layoutWithHeater(core,heater,Direction.SOUTH,label);
        assertEquals(heater,p.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow().position());
        assertEquals(Direction.SOUTH,p.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).findFirst().orElseThrow().facing());
        assertTrue(p.pairings().stream().anyMatch(x->x.component().equals("heater-branch")&&x.second().equals(core.offset(-16,0,-11))));
        assertFalse(p.allOwnedCells().contains(core.offset(-11,0,-2)));assertFalse(p.allOwnedCells().contains(core.offset(-11,0,-3)));
         assertFalse(p.presentationSigns().containsKey(core.offset(-11,0,-3)));assertEquals(new TegFueledFixturePlan.PresentationLabel("STARTUP HEATER",Direction.SOUTH),p.presentationSigns().get(label));
        assertFalse(p.startupPowerValid());assertThrows(IllegalStateException.class,p::combinedCables);
        assertThrows(IllegalArgumentException.class,()->TegFueledFixturePlan.layoutWithHeater(core,core.offset(-17,0,-2),Direction.SOUTH,label));
        assertThrows(IllegalArgumentException.class,()->TegFueledFixturePlan.layoutWithHeater(core,heater,Direction.UP,label));
        assertThrows(IllegalArgumentException.class,()->TegFueledFixturePlan.layoutWithHeater(core,heater,Direction.SOUTH,heater));
    }
    @Test void startupApcFeedsAllRealReceiversWithoutBridgingOutputIsland(){
        var p=TegFueledFixturePlan.layout(new BlockPos(40,70,40));var cables=p.combinedCables();var d=p.electrical().devices();
        assertTrue(cables.size()<300,"cable route must be finite and bounded");assertEquals(cables.size(),cables.stream().distinct().count());
        var startup=new DevicePort(d.startupApc(),Direction.WEST,CableTier.APC);
        var targets=java.util.List.of(new DevicePort(p.core(),Direction.WEST,CableTier.APC),
                new DevicePort(p.core().offset(-8,0,-1),Direction.NORTH,CableTier.APC),
                new DevicePort(p.core().offset(-11,0,-2),Direction.WEST,CableTier.APC),
                new DevicePort(p.core().offset(8,0,1),Direction.SOUTH,CableTier.APC));
        for(var target:targets){assertTrue(cables.stream().anyMatch(c->PowerTopology.deviceAdjacent(target,c)),"real port contact: "+target);assertTrue(TegFixturePlan.connected(cables,startup,target));}
        assertTrue(TegFixturePlan.powerTopologyValid(new TegFixturePlan.Layout(p.electrical().core(),p.electrical().coreFacing(),p.electrical().lvReceiver(),p.electrical().hvOutput(),p.electrical().northCirculator(),p.electrical().northFacing(),p.electrical().southCirculator(),p.electrical().southFacing(),p.electrical().devices(),cables,cables.stream().map(c->c.host()).collect(java.util.stream.Collectors.toSet()))));
        assertTrue(p.startupPowerValid());
        assertTrue(cables.stream().anyMatch(c->PowerTopology.deviceAdjacent(new DevicePort(p.core().offset(-8,0,-1),Direction.NORTH,CableTier.APC),c)&&c.host().equals(p.core().offset(-8,0,-2))));
        assertTrue(cables.stream().anyMatch(c->PowerTopology.deviceAdjacent(new DevicePort(p.core().offset(-11,0,-2),Direction.WEST,CableTier.APC),c)&&c.host().equals(p.core().offset(-12,0,-2))));
        assertTrue(cables.stream().anyMatch(c->PowerTopology.deviceAdjacent(new DevicePort(p.core().offset(8,0,1),Direction.SOUTH,CableTier.APC),c)&&c.host().equals(p.core().offset(8,0,2))));
        assertTrue(p.electrical().cables().stream().anyMatch(c->c.tier()==CableTier.HV));
        assertFalse(TegFixturePlan.connected(cables,startup,new DevicePort(d.outputApc(),Direction.EAST,CableTier.APC)));
        assertFalse(TegFixturePlan.connected(cables,startup,new DevicePort(d.load(),Direction.NORTH,CableTier.APC)));
        var noHeater=cables.stream().filter(c->!c.host().equals(p.core().offset(-12,0,-2))).toList();assertFalse(TegFueledFixturePlan.startupPowerValid(p,noHeater));
    }
    @Test void finiteLayoutAddsOnlyMvFeedbackAndContinuousHeaterLayoutDoesNot(){
        var core=new BlockPos(40,70,40);var finite=TegFueledFixturePlan.layout(core);var cables=finite.combinedCables();
        var start=core.offset(-5,-1,0);var end=core.offset(0,-1,10);
        assertTrue(finite.startupMvFeedback());
        assertTrue(cables.contains(new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(start,Direction.UP,CableTier.MV)));
        assertTrue(cables.contains(new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(start,Direction.UP,CableTier.APC)));
        assertEquals(Set.of(CableTier.APC,CableTier.MV),cables.stream().filter(n->n.host().equals(start)&&n.face()==Direction.UP).map(com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode::tier).collect(java.util.stream.Collectors.toSet()));
        assertTrue(cables.contains(new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(end,Direction.UP,CableTier.MV)));
        assertTrue(TegFixturePlan.connected(cables,new DevicePort(finite.electrical().devices().substation(),Direction.WEST,CableTier.MV),new DevicePort(finite.electrical().devices().startupApc(),Direction.WEST,CableTier.MV)));
        assertFalse(TegFixturePlan.connected(cables,new DevicePort(finite.electrical().devices().startupApc(),Direction.WEST,CableTier.APC),new DevicePort(finite.electrical().devices().outputApc(),Direction.EAST,CableTier.APC)));
        var continuous=TegFueledFixturePlan.layoutWithHeater(core,core.offset(-11,0,-2),Direction.NORTH,core.offset(-11,0,-3));
        assertFalse(continuous.startupMvFeedback());assertTrue(continuous.startupPowerValid());assertFalse(continuous.combinedCables().stream().anyMatch(n->n.tier()==CableTier.MV&&!continuous.electrical().cables().contains(n)));
        var translated=TegFueledFixturePlan.layout(new BlockPos(-20,12,5)).combinedCables();
        assertTrue(translated.contains(new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(new BlockPos(-25,11,5),Direction.UP,CableTier.MV)));
    }
    @Test void independentLoopSeedAllocationUsesComponentVolumesAndCanonicalPortVolume(){
        var volumes=java.util.List.of(.1,1.7,4.2);var allocations=TegFueledFixturePlan.apportionFiniteMoles(43,volumes);
        assertEquals(3,allocations.size());assertEquals(43,allocations.stream().mapToDouble(Double::doubleValue).sum(),1e-12);
        double total=volumes.stream().mapToDouble(Double::doubleValue).sum();for(int i=0;i<volumes.size();i++)assertEquals(43*volumes.get(i)/total,allocations.get(i),1e-12);
        double floatBound=TegFueledFixturePlan.floatSeedRoundingBound(allocations);double stored=allocations.stream().mapToDouble(x->(double)(float)x.doubleValue()).sum();
        assertTrue(Math.abs(stored-43)<=floatBound,"independent float-stored component seeds retain one 43 mol loop inventory");
        var portSeed=new com.juicyslew.moonstation14.ms14.atmos.core.GasMixture(java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN,allocations.get(0)),293.15,.1);
        assertEquals((double)(float).1,portSeed.volumeCubicMeters());assertEquals((double)allocations.get(0).floatValue(),portSeed.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN));
        assertThrows(IllegalArgumentException.class,()->TegFueledFixturePlan.apportionFiniteMoles(43,java.util.List.of(1d,0d)));
    }
    private static Set<net.minecraft.core.BlockPos> intersection(Set<net.minecraft.core.BlockPos>a,Set<net.minecraft.core.BlockPos>b){Set<net.minecraft.core.BlockPos> r=new java.util.HashSet<>(a);r.retainAll(b);return r;}
}
