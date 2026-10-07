package com.juicyslew.moonstation14.ms14.doors.client;

import com.juicyslew.moonstation14.ms14.doors.AirlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import java.io.InputStream;

class AirlockFramePlannerTest {
    @Test void sourceFrameSelectionIsBoundedAndOrdered() {
        assertEquals(0, AirlockFramePlanner.frame(0));
        assertEquals(3, AirlockFramePlanner.frame(.5));
        assertEquals(5, AirlockFramePlanner.frame(1));
        assertEquals(0, AirlockFramePlanner.frame(-1));
        assertEquals(5, AirlockFramePlanner.frame(2));
    }

    @Test void stageTwoAccountsForResolvedFirstStageDurationAndIndependentStarts() {
        var atSecondStageStart = AirlockFramePlanner.plan(AirlockState.DoorPhase.OPENING_PASSABLE,
                0, .8, .4, .6, .3, true, false, false);
        var laterIndependentDoor = AirlockFramePlanner.plan(AirlockState.DoorPhase.OPENING_PASSABLE,
                .1, .8, .4, .6, .3, true, false, false);
        assertEquals("frames/opening_4", atSecondStageStart.base());
        assertEquals("frames/opening_5", laterIndependentDoor.base());
        assertNotEquals(atSecondStageStart.base(), laterIndependentDoor.base());
    }

    @Test void fullBaseRemainsVisibleAndPoweredGlowIsAnIndependentOverlay() {
        var visual = AirlockFramePlanner.plan(AirlockState.DoorPhase.CLOSING_PASSABLE,
                .05, .4, .2, .3, .1, true, false, true);
        assertTrue(visual.base().startsWith("frames/closing_"));
        assertTrue(visual.glow().startsWith("frames/closing_unlit_"));
        assertTrue(visual.glowVisible());
        assertFalse(visual.base().contains("unlit"));
        var bolted = AirlockFramePlanner.plan(AirlockState.DoorPhase.CLOSED,
                0, .4, .2, .3, .1, true, true, false);
        assertEquals("closed", bolted.base());
        assertFalse(bolted.glowVisible());
        assertTrue(bolted.boltOverlay());
        var off = AirlockFramePlanner.plan(AirlockState.DoorPhase.CLOSED,
                0, .4, .2, .3, .1, false, false, false);
        assertEquals("closed", off.base());
        assertEquals("closed_unlit", off.glow());
        assertFalse(off.glowVisible());
        var open = AirlockFramePlanner.plan(AirlockState.DoorPhase.OPEN,
                0, .4, .2, .3, .1, true, false, false);
        assertEquals("open", open.base());
        assertFalse(open.glowVisible());
    }

    @Test void overlaysRemainIndependent() {
        var visual = AirlockFramePlanner.plan(AirlockState.DoorPhase.CLOSING_PASSABLE,
                .05, .4, .2, .3, .1, false, true, true);
        assertTrue(visual.boltOverlay());
        assertTrue(visual.panelOverlay());
    }

    @Test void frontNormalFollowsAllFacingPosesUsedForTheDoorQuad() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            PoseStack pose = new PoseStack();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
            var normal = AirlockFramePlanner.frontNormal(pose.last());
            assertEquals(facing.getStepX(), Math.round(normal.x), facing.toString());
            assertEquals(0, Math.round(normal.y), facing.toString());
            assertEquals(facing.getStepZ(), Math.round(normal.z), facing.toString());
        }
    }

    @Test void rendererBoundsCoverTheRootAndEntireUpperHalf() {
        var bounds = AirlockBlockEntityRenderer.renderBounds(new BlockPos(7, 12, -3));
        assertEquals(7-AirlockGeometry.HALF_TRAVEL, bounds.minX);
        assertEquals(12, bounds.minY);
        assertEquals(-3-AirlockGeometry.HALF_TRAVEL, bounds.minZ);
        assertEquals(8+AirlockGeometry.HALF_TRAVEL, bounds.maxX);
        assertEquals(14, bounds.maxY);
        assertEquals(-2+AirlockGeometry.HALF_TRAVEL, bounds.maxZ);
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

    @Test void leavesMeetWhenClosedClearDoorwayWhenOpenAndStayInsideFacingBounds() {
        var closed=AirlockGeometry.leaves(0);
        assertEquals(closed.get(0).x1(),closed.get(1).x0(),1e-9);
        var open=AirlockGeometry.leaves(1);
        assertTrue(open.get(0).x1()<=.13);
        assertTrue(open.get(1).x0()>=.87);
        for(Direction facing:Direction.Plane.HORIZONTAL) for(var leaf:open) for(double x:new double[]{leaf.x0(),leaf.x1()}) for(double z:new double[]{leaf.z0(),leaf.z1()}) {
            double dx=x-.5,dz=z-.5;
            double wx=facing==Direction.NORTH?dx:facing==Direction.SOUTH?-dx:facing==Direction.EAST?dz:-dz;
            double wz=facing==Direction.NORTH?dz:facing==Direction.SOUTH?-dz:facing==Direction.EAST?-dx:dx;
            assertTrue(wx>=-.5-AirlockGeometry.HALF_TRAVEL-1e-9 && wx<=.5+AirlockGeometry.HALF_TRAVEL+1e-9);
            assertTrue(wz>=-.5-AirlockGeometry.HALF_TRAVEL-1e-9 && wz<=.5+AirlockGeometry.HALF_TRAVEL+1e-9);
        }
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

    @Test void staticCanvasUvRegionsCoverLeafAndFrameWithoutRepeatingWholeArt() {
        assertArrayEquals(new double[]{.13,.5,.13,.87},AirlockGeometry.leafUv(0));
        assertArrayEquals(new double[]{.5,.87,.13,.87},AirlockGeometry.leafUv(1));
        for(var leaf:AirlockGeometry.leaves(1)) assertEquals(.84,leaf.z1()-leaf.z0(),1e-9);
        assertEquals(.84,AirlockGeometry.FRONT-AirlockGeometry.BACK,1e-9);
        // Split domains tile the center-door band exactly once; motion changes positions, not assigned UVs.
        assertEquals(AirlockGeometry.leafUv(0)[1],AirlockGeometry.leafUv(1)[0]);
    }

    @Test void productionRenderPlanIncludesOpaqueBaseFrameAndBothLeafArtworkRegions() {
        assertEquals(java.util.List.of(AirlockBlockEntityRenderer.DrawLayer.NEUTRAL_GEOMETRY,
                        AirlockBlockEntityRenderer.DrawLayer.CLOSED_FRAME,
                        AirlockBlockEntityRenderer.DrawLayer.CLOSED_LEAVES),
                AirlockBlockEntityRenderer.drawPlan());
        assertArrayEquals(new double[]{.13,.5,.13,.87},AirlockGeometry.leafUv(0));
        assertArrayEquals(new double[]{.5,.87,.13,.87},AirlockGeometry.leafUv(1));
    }

    @Test void neutralBackingIsUniformSampledClosedMetalGrayAndHatchFacesAreOutside() throws Exception {
        try (InputStream in=getClass().getResourceAsStream("/assets/moonstation14/textures/block/airlock/neutral_metal.png")) {
            assertNotNull(in);
            var image=ImageIO.read(in);
            assertEquals(1,image.getWidth()); assertEquals(1,image.getHeight());
            assertEquals(0xff545454,image.getRGB(0,0));
        }
        var hatch=AirlockGeometry.hatch(true);
        double[] planes=AirlockGeometry.hatchPlanes();
        assertTrue(planes[0]>AirlockGeometry.FRONT+.002);
        assertTrue(planes[0]>.921+.002); // beyond frame, glow, and bolt artwork
        assertTrue(planes[1]<AirlockGeometry.BACK-.002);
        assertTrue(planes[1]<.078-.002); // beyond rear opaque leaf artwork
        assertEquals(.28,hatch.y1()-hatch.y0(),1e-9);
    }

    @Test void panelCropContainsExactNontransparentSourceBoundsAndIsUsedForBothFaces() throws Exception {
        try (InputStream in=getClass().getResourceAsStream("/assets/moonstation14/textures/block/airlock/panel_open.png")) {
            assertNotNull(in); var image=ImageIO.read(in);
            int minX=99,minY=99,maxX=-1,maxY=-1;
            for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
                if((image.getRGB(x,y)>>>24)!=0) { minX=Math.min(minX,x); minY=Math.min(minY,y); maxX=Math.max(maxX,x); maxY=Math.max(maxY,y); }
            assertEquals(23,minX); assertEquals(14,minY); assertEquals(27,maxX); assertEquals(25,maxY);
        }
        var crop=AirlockBlockEntityRenderer.panelUv();
        assertEquals(23f/32,crop.u0()); assertEquals(14f/32,crop.vAtTop());
        assertEquals(28f/32,crop.u1()); assertEquals(26f/32,crop.vAtBottom());
        // Renderer quad order maps first UV pair to y0 (bottom) and second to y1 (top).
        assertEquals(26, crop.vAtBottom()*32, 1e-6);
        assertEquals(14, crop.vAtTop()*32, 1e-6);
        for(int y=14;y<=25;y++) {
            boolean sampled = y >= crop.vAtTop()*32 && y < crop.vAtBottom()*32;
            assertTrue(sampled, "source row " + y + " must be included by the production crop");
        }
    }
}
