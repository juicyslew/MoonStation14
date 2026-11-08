package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.AirlockGeometry;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;

class AirlockFramePlannerTest {

    @Test void nativeStaticOverlaysFollowPoweredDoorAndBoltPresentation() {
        assertEquals("closed_unlit", plan(AirlockState.DoorPhase.CLOSED, true, false, true, false, false).headerOverlay());
        assertEquals("opening_unlit", plan(AirlockState.DoorPhase.OPENING, true, false, true, false, false).headerOverlay());
        assertNull(plan(AirlockState.DoorPhase.CLOSED, false, false, false, false, false).headerOverlay());
        assertFalse(plan(AirlockState.DoorPhase.CLOSED, false, false, false, false, false).headerLamps());
        assertTrue(plan(AirlockState.DoorPhase.CLOSED, true, false, true, false, false).headerLamps());
        assertEquals("bolted_unlit", plan(AirlockState.DoorPhase.CLOSED, true, true, true, false, false).headerOverlay());
        assertEquals("closed_unlit", plan(AirlockState.DoorPhase.CLOSED, true, true, false, false, false).headerOverlay());
        assertEquals("deny_unlit", plan(AirlockState.DoorPhase.DENYING, true, false, true, false, false).readerOverlay());
        assertNull(plan(AirlockState.DoorPhase.CLOSED, true, false, true, false, false).readerOverlay());
        assertEquals("opening_unlit", plan(AirlockState.DoorPhase.OPEN, true, false, true, false, false).headerOverlay());
        assertEquals("bolted_unlit", plan(AirlockState.DoorPhase.DENYING, true, true, true, false, false).headerOverlay());
        assertTrue(plan(AirlockState.DoorPhase.OPEN, true, false, false, true, true).maintenanceWires());
        assertFalse(plan(AirlockState.DoorPhase.OPENING, true, false, false, true, false).maintenanceWires());
    }

    @Test void normalizedAtlasUvsRebaseAtSpriteBoundsAndMidpoint() {
        float low = .125f, high = .375f;
        assertEquals(low, AirlockBlockEntityRenderer.remapAtlasCoordinate(0, low, high), 1e-6);
        assertEquals((low + high) / 2, AirlockBlockEntityRenderer.remapAtlasCoordinate(.5f, low, high), 1e-6);
        assertEquals(high, AirlockBlockEntityRenderer.remapAtlasCoordinate(1, low, high), 1e-6);
    }

    @Test void onlyPoweredAuthoredUnshadedOverlayQuadsAreFullBright() {
        int darkness = 0;
        assertEquals(0x00F000F0, AirlockBlockEntityRenderer.quadLight(false, true, darkness));
        assertEquals(darkness, AirlockBlockEntityRenderer.quadLight(false, false, darkness));
        assertEquals(darkness, AirlockBlockEntityRenderer.quadLight(true, true, darkness));
    }

    @Test void allRuntimeComponentModelsAreRegisteredAsAdditionalModels() {
        var ids = AirlockBlockEntityRenderer.additionalModels().stream().map(Object::toString).toList();
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/fixed")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/upper_leaf")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/lower_leaf")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/reader_cover")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/maintenance_wires")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("overlays/deny_unlit_00_header")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("overlays/bolted_unlit_00_header")));
    }

    private static AirlockFramePlanner.Plan plan(AirlockState.DoorPhase phase, boolean powered,
                                                  boolean boltsDown, boolean boltLightsVisible,
                                                  boolean panelVisible, boolean panelOpen) {
        return AirlockFramePlanner.plan(phase, powered, boltsDown, boltLightsVisible, panelVisible, panelOpen);
    }
    @Test void rendererBoundsCoverTheRootAndEntireUpperHalf() {
        var bounds = AirlockGeometry.renderBounds(new BlockPos(7, 12, -3));
        assertEquals(7, bounds.minX);
        assertEquals(12, bounds.minY);
        assertEquals(-3, bounds.minZ);
        assertEquals(8, bounds.maxX);
        assertEquals(14, bounds.maxY);
        assertEquals(-2, bounds.maxZ);
    }

    @Test void cuboidHasSixNondegenerateFacesWithUnitOrthogonalNormals() {
        var faces=new AirlockGeometry.Box(0,0,0,1,2,1).faces();
        assertEquals(6,faces.size());
        assertEquals(6,faces.stream().map(f -> f.nx()+","+f.ny()+","+f.nz()).distinct().count());
        for(var face:faces) {
            var a=face.a();var b=face.b();var c=face.c();
            double abx=b.x()-a.x(),aby=b.y()-a.y(),abz=b.z()-a.z();
            double acx=c.x()-a.x(),acy=c.y()-a.y(),acz=c.z()-a.z();
            double area=Math.sqrt(Math.pow(aby*acz-abz*acy,2)+Math.pow(abz*acx-abx*acz,2)+Math.pow(abx*acy-aby*acx,2));
            assertTrue(area>0);
            assertEquals(1,Math.sqrt(face.nx()*face.nx()+face.ny()*face.ny()+face.nz()*face.nz()),1e-6);
            assertEquals(0,face.nx()*abx+face.ny()*aby+face.nz()*abz,1e-6);
            assertEquals(0,face.nx()*acx+face.ny()*acy+face.nz()*acz,1e-6);
        }
    }

    @Test void importedVerticalLeavesStayClippedToTwoBlockRootForEveryFacing() {
        var closed=AirlockGeometry.leaves(0);
        var open=AirlockGeometry.leaves(1);
        assertFalse(closed.isEmpty());
        assertNotEquals(closed,open);
        for(var box:open) assertTrue(box.x0()>=0&&box.x1()<=1&&box.y0()>=0&&box.y1()<=2&&box.z0()>=0&&box.z1()<=1);
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var bounds=AirlockGeometry.worldBounds(new BlockPos(0,0,0),facing,.5,true);
            for(var box:bounds) assertTrue(box.minY>=0&&box.maxY<=2);
        }
        for(var part:AirlockGeometry.Part.values()) assertFalse(AirlockGeometry.model(part).isEmpty(),part.name());
        assertEquals(AirlockGeometry.UPPER_TRAVEL,AirlockGeometry.movementY(AirlockGeometry.Part.READER_HOUSING,1),1e-12);
        assertEquals(0,AirlockGeometry.movementY(AirlockGeometry.Part.READER_HOUSING,0),1e-12);
        assertEquals(AirlockGeometry.model(AirlockGeometry.Part.FIXED).size()
                        +AirlockGeometry.model(AirlockGeometry.Part.HEADER_LAMPS).size(),AirlockGeometry.frame().size());
        for(double q:new double[]{0,.25,.5,.75,1}) {
            for(var box:AirlockGeometry.leaves(q))
                assertTrue(box.x0()>=0&&box.x1()<=1&&box.y0()>=0&&box.y1()<=2&&box.z0()>=0&&box.z1()<=1);
            assertEquals(AirlockGeometry.movementY(AirlockGeometry.Part.UPPER_LEAF,q),
                    AirlockGeometry.movementY(AirlockGeometry.Part.READER_COVER,q));
            assertEquals(AirlockGeometry.movementY(AirlockGeometry.Part.UPPER_LEAF,q),
                    AirlockGeometry.movementY(AirlockGeometry.Part.MAINTENANCE_WIRES,q));
            assertEquals(AirlockGeometry.movementY(AirlockGeometry.Part.UPPER_LEAF,q),
                    AirlockGeometry.movementY(AirlockGeometry.Part.READER_HOUSING,q));
            for(Direction facing:Direction.Plane.HORIZONTAL)
                for(var bounds:AirlockGeometry.worldBounds(new BlockPos(3,4,5),facing,q,true)) {
                    assertTrue(bounds.minX>=3&&bounds.maxX<=4);
                    assertTrue(bounds.minY>=4&&bounds.maxY<=6);
                    assertTrue(bounds.minZ>=5&&bounds.maxZ<=6);
                }
        }
    }

    private static net.minecraft.world.phys.AABB worldBox(BlockPos root,Direction facing,AirlockGeometry.Box b) {
        double rx=facing.getCounterClockWise().getStepX(),rz=facing.getCounterClockWise().getStepZ();
        double fx=facing.getStepX(),fz=facing.getStepZ();
        double minX=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(double x:new double[]{b.x0(),b.x1()}) for(double z:new double[]{b.z0(),b.z1()}) {
            double wx=root.getX()+.5+rx*(x-.5)+fx*(z-.5),wz=root.getZ()+.5+rz*(x-.5)+fz*(z-.5);
            minX=Math.min(minX,wx);maxX=Math.max(maxX,wx);minZ=Math.min(minZ,wz);maxZ=Math.max(maxZ,wz);
        }
        return new net.minecraft.world.phys.AABB(minX,root.getY()+b.y0(),minZ,maxX,root.getY()+b.y1(),maxZ);
    }

    @Test void actualMotionUsesPhaseDurationsAndIndependentElapsedTime() {
        assertEquals(0,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSED,0,.4,.2,.3,.1),1e-9);
        assertEquals(1,AirlockGeometry.openness(AirlockState.DoorPhase.OPEN,0,.4,.2,.3,.1),1e-9);
        assertEquals(1,AirlockGeometry.openness(AirlockState.DoorPhase.OPENING_PASSABLE,0,.4,.2,.3,.1),1e-9);
        assertEquals(0,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSING_PASSABLE,.05,.4,.2,.3,.1),1e-9);
        assertEquals(1,AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.4,.4,.2,.3,.1),1e-9);
        assertEquals(0,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSING,.4,.4,.2,.3,.1),1e-9);
        assertEquals(.25,AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.1,.4,.2,.3,.1),1e-9);
        assertEquals(.5,AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.6,1.2,.2,.8,.1),1e-9);
        assertEquals(1,AirlockGeometry.openness(AirlockState.DoorPhase.OPENING_PASSABLE,99,1.2,.2,.8,.1),1e-9);
        assertEquals(.5,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSING,.4,1.2,.2,.8,.1),1e-9);
        assertEquals(0,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSING_PASSABLE,99,1.2,.2,.8,.1),1e-9);
        assertEquals(1,AirlockGeometry.openness(AirlockState.DoorPhase.OPEN,0,1.2,.2,.8,.1),1e-9);
        assertEquals(0,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSED,0,1.2,.2,.8,.1),1e-9);
        assertEquals(0,AirlockGeometry.openness(AirlockState.DoorPhase.DENYING,0,1.2,.2,.8,.1),1e-9);
        assertNotEquals(AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.1,.4,.2,.3,.1),
                AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.2,.4,.2,.3,.1));
    }

    @Test void apertureHasNoCenteredLeafAtFullOpenAndGeometryHasAuthoredDepth() {
        assertEquals(21.5/16,AirlockGeometry.UPPER_TRAVEL,1e-12);
        assertEquals(9.5/16,AirlockGeometry.LOWER_TRAVEL,1e-12);
    }

}
