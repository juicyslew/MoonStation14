package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.juicyslew.moonstation14.ms14.doors.AirlockGeometry;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;

class AirlockFramePlannerTest {

    @Test void exportedAtlasAndPanelSelectionUsesOnlyExistingViewTruth() {
        assertEquals(new AirlockFramePlanner.Plan("unpowered", false), plan(AirlockState.DoorPhase.CLOSED, false, false, false, false, false));
        assertEquals(new AirlockFramePlanner.Plan("unpowered", true), plan(AirlockState.DoorPhase.DENYING, false, false, false, true, true));
        assertEquals(new AirlockFramePlanner.Plan("denied", false), plan(AirlockState.DoorPhase.DENYING, true, false, true, false, false));
        assertEquals(new AirlockFramePlanner.Plan("bolted", false), plan(AirlockState.DoorPhase.CLOSED, true, true, true, false, false));
        assertEquals(new AirlockFramePlanner.Plan("bolted", false), plan(AirlockState.DoorPhase.DENYING, true, true, true, false, false));
        assertEquals(new AirlockFramePlanner.Plan("open", false), plan(AirlockState.DoorPhase.OPEN, true, false, true, false, false));
        assertEquals(new AirlockFramePlanner.Plan("open", false), plan(AirlockState.DoorPhase.CLOSING, true, false, true, false, false));
        assertEquals(new AirlockFramePlanner.Plan("closed", false), plan(AirlockState.DoorPhase.CLOSED, true, false, true, false, false));
        assertEquals(new AirlockFramePlanner.Plan("open_panel", true), plan(AirlockState.DoorPhase.OPENING, true, false, true, true, true));
        assertEquals(new AirlockFramePlanner.Plan("bolted_panel", true), plan(AirlockState.DoorPhase.CLOSED, true, true, true, true, true));
        assertEquals(new AirlockFramePlanner.Plan("panel_open", true), plan(AirlockState.DoorPhase.CLOSED, true, false, true, true, true));
        assertEquals(new AirlockFramePlanner.Plan("denied", true), plan(AirlockState.DoorPhase.DENYING, true, false, true, true, true));
        assertEquals(new AirlockFramePlanner.Plan("open", false), plan(AirlockState.DoorPhase.OPEN, true, false, true, true, true));
        assertEquals(new AirlockFramePlanner.Plan("closed", false), plan(AirlockState.DoorPhase.CLOSED, true, false, true, true, false));
    }

    @Test void normalizedAtlasUvsRebaseAtSpriteBoundsAndMidpoint() {
        float low = .125f, high = .375f;
        assertEquals(low, AirlockBlockEntityRenderer.remapAtlasCoordinate(0, low, high), 1e-6);
        assertEquals((low + high) / 2, AirlockBlockEntityRenderer.remapAtlasCoordinate(.5f, low, high), 1e-6);
        assertEquals(high, AirlockBlockEntityRenderer.remapAtlasCoordinate(1, low, high), 1e-6);
    }

    @Test void onlyPoweredAuthoredUnshadedLampQuadsAreFullBright() {
        int darkness = 0;
        assertEquals(0x00F000F0, AirlockBlockEntityRenderer.quadLight(false, true, darkness));
        assertEquals(darkness, AirlockBlockEntityRenderer.quadLight(false, false, darkness));
        assertEquals(darkness, AirlockBlockEntityRenderer.quadLight(true, true, darkness));
    }

    @Test void allRuntimeComponentModelsAreRegisteredAsAdditionalModels() {
        var ids = AirlockBlockEntityRenderer.additionalModels().stream().map(Object::toString).toList();
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/frame")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/leaf_left")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/leaf_right")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/maintenance_cover")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("components/maintenance_wires")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("/denied")));
        assertTrue(ids.stream().anyMatch(id -> id.contains("/unpowered")));
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

    @Test void authoredLeavesTranslateOutwardAndRemainClippedToTheRootVolume() {
        var closed=AirlockGeometry.leaves(0);
        var open=AirlockGeometry.leaves(1);
        assertEquals(11,closed.size());
        assertEquals(-.125,open.get(0).x0()-closed.get(0).x0(),1e-9);
        assertEquals(AirlockGeometry.HALF_TRAVEL,open.get(5).x0()-closed.get(5).x0(),1e-9);
        for(var box:open) assertTrue(box.x0()>=0&&box.x1()<=1&&box.y0()>=0&&box.y1()<=2&&box.z0()>=0&&box.z1()<=1);
        for(Direction facing:Direction.Plane.HORIZONTAL) {
            var bounds=AirlockGeometry.worldBounds(new BlockPos(0,0,0),facing,.5,true);
            for(var box:bounds) assertTrue(box.minY>=0&&box.maxY<=2);
            var leaf=AirlockGeometry.model(AirlockGeometry.Part.LEAF_RIGHT).get(0);
            assertTrue(bounds.contains(worldBox(new BlockPos(0,0,0),facing,
                    new AirlockGeometry.Box(leaf.x0()+AirlockGeometry.HALF_TRAVEL*.5,leaf.y0(),leaf.z0(),
                            Math.min(1,leaf.x1()+AirlockGeometry.HALF_TRAVEL*.5),leaf.y1(),leaf.z1()))));
        }
        assertEquals(6,AirlockGeometry.model(AirlockGeometry.Part.FRAME).size());
        assertEquals(5,AirlockGeometry.model(AirlockGeometry.Part.LEAF_LEFT).size());
        assertEquals(6,AirlockGeometry.model(AirlockGeometry.Part.LEAF_RIGHT).size());
        assertEquals(1,AirlockGeometry.model(AirlockGeometry.Part.MAINTENANCE_COVER).size());
        assertEquals(1,AirlockGeometry.model(AirlockGeometry.Part.MAINTENANCE_WIRES).size());
        assertEquals(AirlockGeometry.model(AirlockGeometry.Part.FRAME).size()+closed.size()+1,
                AirlockGeometry.worldBounds(new BlockPos(0,0,0),Direction.NORTH,0,false).size());
        assertEquals(AirlockGeometry.model(AirlockGeometry.Part.FRAME).size()+closed.size()+1,
                AirlockGeometry.worldBounds(new BlockPos(0,0,0),Direction.NORTH,0,true).size());
        double halfTravel=AirlockGeometry.HALF_TRAVEL*.5;
        var cover=AirlockGeometry.model(AirlockGeometry.Part.MAINTENANCE_COVER).get(0);
        var wires=AirlockGeometry.model(AirlockGeometry.Part.MAINTENANCE_WIRES).get(0);
        var coverExpected=worldBox(new BlockPos(0,0,0),Direction.NORTH,new AirlockGeometry.Box(
                cover.x0()+halfTravel,cover.y0(),cover.z0(),cover.x1()+halfTravel,cover.y1(),cover.z1()));
        var wiresExpected=worldBox(new BlockPos(0,0,0),Direction.NORTH,new AirlockGeometry.Box(
                wires.x0()+halfTravel,wires.y0(),wires.z0(),wires.x1()+halfTravel,wires.y1(),wires.z1()));
        assertTrue(AirlockGeometry.worldBounds(new BlockPos(0,0,0),Direction.NORTH,.5,false).contains(coverExpected));
        assertTrue(AirlockGeometry.worldBounds(new BlockPos(0,0,0),Direction.NORTH,.5,true).contains(wiresExpected));
        assertNotEquals(coverExpected.minX,worldBox(new BlockPos(0,0,0),Direction.NORTH,new AirlockGeometry.Box(
                cover.x0()-halfTravel,cover.y0(),cover.z0(),cover.x1()-halfTravel,cover.y1(),cover.z1())).minX);
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
        assertEquals(.5,AirlockGeometry.openness(AirlockState.DoorPhase.OPENING_PASSABLE,0,.4,.2,.3,.1),1e-9);
        assertEquals(.5,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSING,.3,.4,.2,.3,.1),1e-9);
        assertEquals(.25,AirlockGeometry.openness(AirlockState.DoorPhase.CLOSING_PASSABLE,.05,.4,.2,.3,.1),1e-9);
        assertEquals(.375,AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.3,.4,.2,.3,.1),1e-9);
        assertNotEquals(AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.1,.4,.2,.3,.1),
                AirlockGeometry.openness(AirlockState.DoorPhase.OPENING,.2,.4,.2,.3,.1));
    }

    @Test void apertureHasNoCenteredLeafAtFullOpenAndGeometryHasAuthoredDepth() {
        for(var leaf:AirlockGeometry.leaves(1)) assertTrue(leaf.x1()<=.5||leaf.x0()>=.5);
        assertTrue(AirlockGeometry.model(AirlockGeometry.Part.LEAF_LEFT).stream().anyMatch(b->b.z1()-b.z0()<.5));
        assertEquals(6.5/16,AirlockGeometry.HALF_TRAVEL,1e-12);
    }

}
