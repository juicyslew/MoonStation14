package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixtureSettlingTest {
    @Test void unstableHistoryCannotCountAsStableAdmission() {
        FixtureSettling settling=new FixtureSettling();
        Vec3 position=new Vec3(0,64,0);
        for(int i=0;i<39;i++) assertEquals(FixtureSettling.Result.WAIT,settling.observe(false,position));
        assertEquals(FixtureSettling.Result.ABORT,settling.observe(true,position));

        FixtureSettling reset=new FixtureSettling(); reset.seed(position);
        assertEquals(FixtureSettling.Result.WAIT,reset.observe(true,position));
        assertEquals(FixtureSettling.Result.WAIT,reset.observe(true,position));
        assertEquals(FixtureSettling.Result.WAIT,reset.observe(false,position));
        assertEquals(FixtureSettling.Result.WAIT,reset.observe(true,position));
    }

    @Test void requiresThreeStableObservationsAndAbortsAtDeadlineDuringMotion() {
        FixtureSettling ready=new FixtureSettling(); Vec3 p=new Vec3(0,64,0);
        ready.seed(p);
        assertEquals(FixtureSettling.Result.WAIT,ready.observe(true,p));
        assertEquals(FixtureSettling.Result.WAIT,ready.observe(true,p));
        assertEquals(FixtureSettling.Result.READY,ready.observe(true,p));
        FixtureSettling moving=new FixtureSettling();
        for(int i=0;i<39;i++) assertEquals(FixtureSettling.Result.WAIT,moving.observe(true,new Vec3(i*.1,64,0)));
        assertEquals(FixtureSettling.Result.ABORT,moving.observe(true,new Vec3(3.9,64,0)));
    }

    @Test void floorAndCandidateGeometryUseActualFeetAndHeading() {
        assertEquals(63,FixtureSettling.floorOrigin(new Vec3(0,64,0)).getY());
        assertEquals(63,FixtureSettling.floorOrigin(new Vec3(0,63.5,0)).getY());
        assertEquals(62,FixtureSettling.floorOrigin(new Vec3(-.2,62.5,-.2)).getY());
        float[] yaws={0,45,90,135,180,225,270,315};
        for(float yaw:yaws) {
            Vec3 forward=FixtureSettling.forward(yaw);
            Vec3 feet=new Vec3(-.2,64,10.2);
            var candidates=FixtureSettling.candidates(feet,yaw);
            assertTrue(candidates.size()<=18);
            assertFalse(candidates.isEmpty());
            for(var candidate:candidates) {
                Vec3 center=candidate.center();
                assertEquals(candidate.distance(),center.subtract(feet).dot(forward),1e-8);
                for(int i=0;i<100;i++) {
                    var cell=FixtureSettling.spawnCell(i);
                    int x=candidate.origin().getX()+cell.getX(),z=candidate.origin().getZ()+cell.getZ();
                    assertTrue(x>candidate.origin().getX()&&x<candidate.origin().getX()+15&&z>candidate.origin().getZ()&&z<candidate.origin().getZ()+15);
                    Vec3 animal=new Vec3(x+.5,feet.y,z+.5);
                    assertTrue(animal.subtract(feet).dot(forward)>0,"spawn behind body for yaw="+yaw);
                }
            }
        }
    }

    @Test void candidateSelectionCanChooseRaisedClearFootprintOrSafelyRejectAll() {
        Vec3 feet=new Vec3(0,-60,0);
        var candidates=FixtureSettling.candidates(feet,0);
        var raised=FixtureSettling.firstPassing(candidates,candidate -> candidate.origin().getY()>-61);
        assertNotNull(raised);
        assertEquals(-60,raised.origin().getY());
        assertNull(FixtureSettling.firstPassing(candidates,candidate -> false));
    }

    @Test void spawnCellsStayInteriorForCompactWidthsAndPreserveLegacySixteenGrid() {
        for(int i=0;i<100;i++) {
            var legacy=FixtureSettling.spawnCell(i,16);
            assertEquals(3+i%10,legacy.getX());
            assertEquals(3+i/10,legacy.getZ());
        }
        for(int width=6;width<=16;width++) for(int i=0;i<200;i++) {
            var cell=FixtureSettling.spawnCell(i,width);
            assertTrue(cell.getX()>0&&cell.getX()<width-1,"x width="+width+" i="+i);
            assertTrue(cell.getZ()>0&&cell.getZ()<width-1,"z width="+width+" i="+i);
        }
    }

    @Test void compactEightGridWrapsItsInteriorCells() {
        assertEquals(FixtureSettling.spawnCell(0,8),FixtureSettling.spawnCell(36,8));
    }

    @Test void showcaseInventoryIncludesEveryCustomHostAndLegacyMouseWithSafeUniqueCells() {
        var showcase=AnimalPopulationSpec.showcase();
        assertEquals(37,showcase.count());
        assertEquals(37,showcase.species().stream().distinct().count());
        assertTrue(showcase.species().contains("mouse"));
        var cells=new java.util.HashSet<net.minecraft.core.BlockPos>();
        for(int i=0;i<showcase.count();i++) {
            var cell=FixtureSettling.spawnCell(i,showcase.width(),showcase.cellSpacing());
            assertTrue(cells.add(cell),"duplicate showcase position "+cell);
            assertTrue(cell.getX()>0&&cell.getX()<showcase.width()-1);
            assertTrue(cell.getZ()>0&&cell.getZ()<showcase.width()-1);
        }
    }
}
