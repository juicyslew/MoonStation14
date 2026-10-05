package com.juicyslew.moonstation14.ms14.atmos.piping.fixture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.core.*;
import com.juicyslew.moonstation14.ms14.power.topology.*;

class VentilationFixturePlanTest {
    @Test void boundedRoomsEnumerateAllAirCellsAndRemainSeparate(){
        var p=VentilationFixturePlan.standard();
        assertTrue(VentilationFixturePlan.WIDTH<=20 && VentilationFixturePlan.DEPTH<=12 && VentilationFixturePlan.HEIGHT<=5);
        assertEquals(18,p.sourceCells().size()); assertEquals(18,p.recipientCells().size());
        assertTrue(p.sourceCells().stream().noneMatch(p.recipientCells()::contains));
        assertTrue(p.blocks().stream().allMatch(c->c.x()>=0&&c.x()<20&&c.y()>=0&&c.y()<5&&c.z()>=0&&c.z()<12));
        assertEquals(2,p.scenarios().size());
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,p.testGas());
        assertEquals(2.0,p.testGasMolesPerCell());
        assertTrue(com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.hasOverlay(p.testGas()));
        assertTrue(com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.alphaByte(p.testGas(),p.testGasMolesPerCell(),1.0)>0);
    }
    @Test void planIsImmutableAndHasExplicitTypedPowerAndPipeGeometry(){
        var p=VentilationFixturePlan.standard();
        assertThrows(UnsupportedOperationException.class,()->p.blocks().clear());
        assertEquals(3,p.powerDevices().size()); assertEquals(11,p.pipes().size());
        assertTrue(p.powerDevices().stream().allMatch(d->!d.ports().isEmpty()));
    }

    @Test void sealedRoomsAndGlassFrontsAreExact(){
        var p=VentilationFixturePlan.standard(); var blocks=index(p);
        assertEquals(18,p.sourceCells().size()); assertEquals(18,p.recipientCells().size());
        for(var cell:p.sourceCells())assertEquals(VentilationFixturePlan.BlockRole.AIR,blocks.get(cell));
        for(var cell:p.recipientCells())assertEquals(VentilationFixturePlan.BlockRole.AIR,blocks.get(cell));
        for(int x:new int[]{1,2,3,9,10,11})for(int y=1;y<=2;y++)assertEquals(VentilationFixturePlan.BlockRole.GLASS,blocks.get(new BlockPos(x,y,6)));
        assertEquals(Direction.SOUTH,p.scrubberFacing()); assertEquals(Direction.SOUTH,p.ventFacing());
        assertEquals(Direction.NORTH,p.alarmFacing());
        assertEquals(VentilationFixturePlan.BlockRole.STEEL,blocks.get(new BlockPos(6,1,7)));
        assertTrue(flood(p.sourceCells().getFirst(),blocks).equals(Set.copyOf(p.sourceCells())));
        assertTrue(flood(p.recipientCells().getFirst(),blocks).equals(Set.copyOf(p.recipientCells())));
    }

    @Test void pipeAndCableNetworksAreFullyConnectedAndPortsMatch(){
        var p=VentilationFixturePlan.standard();
        Set<BlockPos> pipePos=new HashSet<>(); for(var n:p.pipes())assertTrue(pipePos.add(n.position()));
        assertEquals(11,pipePos.size()); assertEquals(pipePos,pipeReach(p.pipes(),p.pipes().getFirst()));
        assertEquals(2,p.pipes().stream().filter(VentilationFixturePlan.PipeNode::concealed).count());
        assertTrue(p.pipes().stream().filter(VentilationFixturePlan.PipeNode::concealed).allMatch(n->n.axis()==Direction.Axis.Z));
        for(var device:p.powerDevices())for(var port:device.ports())assertTrue(p.cables().stream().anyMatch(c->PowerTopology.deviceAdjacent(port,c)),device.kind()+" "+port);
        assertEquals(Set.of(com.juicyslew.moonstation14.ms14.power.cable.CableTier.HV,
                com.juicyslew.moonstation14.ms14.power.cable.CableTier.MV,
                com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC),
                p.cables().stream().map(CableFaceNode::tier).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3,nearestApcDistance(p,p.vent())); assertTrue(nearestApcDistance(p,p.vent())<=3);
        assertTrue(nearestApcDistance(p,p.alarm())<=3);
        assertTrue(p.cables().stream().allMatch(c->c.host().getX()>=0&&c.host().getX()<20&&c.host().getY()>=0&&c.host().getY()<5&&c.host().getZ()>=0&&c.host().getZ()<12));
    }
    private static int dist(BlockPos a,BlockPos b){return (int)Math.ceil(Math.sqrt(a.distSqr(b)));}
    private static int nearestApcDistance(VentilationFixturePlan p,BlockPos target){return p.cables().stream().filter(n->n.tier()==com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC).mapToInt(n->dist(target,n.host())).min().orElseThrow();}
    private static Map<BlockPos,VentilationFixturePlan.BlockRole> index(VentilationFixturePlan p){Map<BlockPos,VentilationFixturePlan.BlockRole>m=new HashMap<>();for(var c:p.blocks())m.put(c.pos(),c.role());return m;}
    private static Set<BlockPos> flood(BlockPos start,Map<BlockPos,VentilationFixturePlan.BlockRole> blocks){
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos>q=new ArrayDeque<>();seen.add(start);q.add(start);
        while(!q.isEmpty()){BlockPos a=q.remove();for(Direction d:Direction.values()){BlockPos b=a.relative(d);if(blocks.get(b)==VentilationFixturePlan.BlockRole.AIR&&seen.add(b))q.add(b);}}
        return seen;
    }
    private static Set<BlockPos> pipeReach(List<VentilationFixturePlan.PipeNode> nodes,VentilationFixturePlan.PipeNode start){
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<VentilationFixturePlan.PipeNode>q=new ArrayDeque<>();seen.add(start.position());q.add(start);
        while(!q.isEmpty()){var a=q.remove();for(var b:nodes){BlockPos delta=b.position().subtract(a.position());boolean adjacent=Math.abs(delta.getX())+Math.abs(delta.getY())+Math.abs(delta.getZ())==1;boolean run=a.axis()==b.axis()&&a.axis()==Direction.Axis.X&&delta.getY()==0&&delta.getZ()==0;boolean wallJoin=a.concealed()!=b.concealed()&&((a.concealed()?a:b).axis()==Direction.Axis.Z)&&delta.getY()==0&&delta.getX()==0;if((run||wallJoin)&&adjacent&&seen.add(b.position()))q.add(b);}}
        return seen;
    }
}
