package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TegContinuousCoolingPlanTest {
    @Test void translatedOriginsProduceBoundedImmutableParallelArray() {
        for(BlockPos core:List.of(new BlockPos(22,2,18),new BlockPos(-78,-30,-42))) {
            var p=TegContinuousCoolingPlan.layout(core);
            assertEquals(5,p.radiators().size());
            for(int i=0;i<5;i++) assertEquals(new TegContinuousCoolingPlan.Device(core.offset(16,5,1+2*i),Direction.WEST),p.radiators().get(i));
            assertEquals(new TegContinuousCoolingPlan.Device(core.offset(19,5,9),Direction.WEST),p.pressurePump());
            assertEquals(4,p.pipeComponents().size());
            assertEquals(Set.copyOf(p.pipeComponents().stream().flatMap(Collection::stream).toList()),p.exposedPipeHosts());
            var expectedReserved=Set.of(core.offset(16,5,1),core.offset(16,5,3),core.offset(16,5,5),core.offset(16,5,7),core.offset(16,5,9),core.offset(19,5,9));
            assertEquals(expectedReserved,p.reservedDevicePositions());
            for(var component:p.pipeComponents()) {
                assertEquals(component.size(),new HashSet<>(component).size()); assertTrue(connected(component));
                for(BlockPos pos:component) {
                    assertTrue(pos.getX()>=core.getX()-22&&pos.getX()<core.getX()+26);
                    assertTrue(pos.getY()>=core.getY()-2&&pos.getY()<core.getY()+7);
                    assertTrue(pos.getZ()>=core.getZ()-18&&pos.getZ()<core.getZ()+30);
                    assertFalse(p.reservedDevicePositions().contains(pos));
                }
            }
            for(int i=0;i<4;i++)for(int j=i+1;j<4;j++) {
                Set<BlockPos> a=Set.copyOf(p.pipeComponents().get(i));
                for(BlockPos pos:p.pipeComponents().get(j))for(Direction d:Direction.values())
                    assertFalse(a.contains(pos.relative(d)),"cross-component adjacent pipe hosts: "+pos+" / "+pos.relative(d));
            }
            assertEquals(core.offset(7,0,1),p.pipeComponents().get(0).getLast());
            assertEquals(core.offset(9,0,1),p.pipeComponents().get(1).getFirst());
            assertEquals(core.offset(15,5,9),p.pipeComponents().get(1).getLast());
            assertEquals(core.offset(17,5,1),p.pipeComponents().get(2).getFirst());
            assertEquals(core.offset(18,5,9),p.pipeComponents().get(2).getLast());
            assertEquals(core.offset(20,5,9),p.pipeComponents().get(3).getFirst());
            assertEquals(core.offset(-1,0,1),p.pipeComponents().get(3).getLast());
            assertTrue(p.pipeComponents().get(3).contains(core.offset(21,6,11)));
            assertFalse(p.pipeComponents().get(3).contains(core.offset(0,6,9)),"return must not cross above the outlet header at z9");
            assertTrue(p.pipeComponents().get(3).contains(core.offset(-3,6,11)));
            for(var radiator:p.radiators()) {
                assertTrue(p.pipeComponents().get(1).contains(radiator.position().west()));
                assertTrue(p.pipeComponents().get(2).contains(radiator.position().east()));
            }
            assertTrue(p.pipeComponents().get(2).contains(p.pressurePump().position().west()));
            assertTrue(p.pipeComponents().get(3).contains(p.pressurePump().position().east()));
            assertTrue(p.pipeComponents().get(0).contains(core.offset(7,0,1)));
            assertTrue(p.pipeComponents().get(1).contains(core.offset(9,0,1)));
            assertThrows(UnsupportedOperationException.class,()->p.radiators().clear());
            assertThrows(UnsupportedOperationException.class,()->p.pipeComponents().get(0).clear());
        }
    }
    private static boolean connected(List<BlockPos> nodes) {
        Set<BlockPos> allowed=Set.copyOf(nodes),seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(nodes.getFirst());seen.add(nodes.getFirst());
        while(!queue.isEmpty()){BlockPos p=queue.removeFirst();for(Direction d:Direction.values()){BlockPos n=p.relative(d);if(allowed.contains(n)&&seen.add(n))queue.addLast(n);}}
        return seen.size()==nodes.size();
    }
}
