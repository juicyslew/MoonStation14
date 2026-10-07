package com.juicyslew.moonstation14.ms14.disposal.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class DisposalFixtureTourPlanTest {
    @Test void longRideIsUniqueAxisContinuousAndEndsAtSafeRoomAir() {
        var cells=DisposalFixtureTourPlan.cells();
        assertEquals(259,cells.size());
        assertEquals(cells.size(),new HashSet<>(cells).size());
        for(int i=1;i<cells.size();i++) {
            BlockPos a=cells.get(i-1),b=cells.get(i);
            assertEquals(1,Math.abs(a.getX()-b.getX())+Math.abs(a.getY()-b.getY())+Math.abs(a.getZ()-b.getZ()),"continuous route at "+i);
        }
        assertEquals(new BlockPos(3,1,8),cells.getFirst());
        assertEquals(new BlockPos(6,1,9),cells.getLast());
        assertEquals(Direction.WEST,DisposalFixtureTourPlan.outputAt(cells.size()-1));
        assertEquals(new BlockPos(5,1,9),DisposalFixtureTourPlan.outlet());
        assertTrue(DisposalFixtureTourPlan.outlet().getX()>=2&&DisposalFixtureTourPlan.outlet().getX()<=7);
        assertTrue(DisposalFixtureTourPlan.outlet().getY()>=1&&DisposalFixtureTourPlan.outlet().getY()<=4);
        assertTrue(DisposalFixtureTourPlan.outlet().getZ()>=7&&DisposalFixtureTourPlan.outlet().getZ()<=10);
    }

    @Test void rideAndObstacleHaveSparseBoundedFootprintsAndKeepFloorReferenceClear() {
        var cells=DisposalFixtureTourPlan.cells();
        assertEquals(new BlockPos(30,5,28),DisposalFixtureTourPlan.maximum());
        assertTrue(cells.stream().allMatch(p->p.getY()>=DisposalFixtureTourPlan.floorY()));
        var obstacle=DisposalFixtureTourPlan.obstacleFootprint();
        assertEquals(72,obstacle.size());
        assertTrue(obstacle.contains(new BlockPos(20,0,8)));
        assertTrue(obstacle.contains(new BlockPos(25,3,10)));
        assertEquals(25.9,DisposalFixtureTourPlan.estimatedRideSeconds(.1),1e-9);
    }
}
