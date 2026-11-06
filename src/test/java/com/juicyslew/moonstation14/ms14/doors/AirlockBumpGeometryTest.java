package com.juicyslew.moonstation14.ms14.doors;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirlockBumpGeometryTest {
    private static final AABB BODY = new AABB(0.2, 0, 0.2, 0.8, 1.8, 0.8);
    private static final AABB DOOR = new AABB(1, 0, 0, 2, 2, 1);

    @Test void contactRequiresMotionIntoDoorNotParallelOrAway() {
        assertEquals(2.0 / 3.0, AirlockBumpSystem.timeOfImpact(BODY, new Vec3(0.3, 0, 0), DOOR), 1.0E-9);
        assertEquals(-1.0, AirlockBumpSystem.timeOfImpact(BODY, new Vec3(0, 0, 0.4), DOOR));
        assertEquals(-1.0, AirlockBumpSystem.timeOfImpact(BODY, new Vec3(-0.4, 0, 0), DOOR));
    }

    @Test void supportingFloorDoesNotMaskGroundedHorizontalDoorContact() {
        // Gravity may be part of requested Entity.move; horizontal contact is tested at the
        // grounded actor bounds after vanilla's separate vertical collision resolution.
        assertTrue(AirlockBumpSystem.timeOfImpact(BODY, new Vec3(0.3, 0, 0), DOOR) >= 0);
        AABB floor = new AABB(-10, -1, -10, 10, 0, 10);
        assertEquals(-1.0, AirlockBumpSystem.timeOfImpact(BODY, new Vec3(0.3, 0, 0), floor));
    }

    @Test void foregroundWallWinsOverDoorBehindIt() {
        AABB wall = new AABB(0.9, 0, 0, 1.1, 2, 1);
        AABB fartherDoor = new AABB(1.5, 0, 0, 2.5, 2, 1);
        double wallTime = AirlockBumpSystem.timeOfImpact(BODY, new Vec3(1.5, 0, 0), wall);
        double doorTime = AirlockBumpSystem.timeOfImpact(BODY, new Vec3(1.5, 0, 0), fartherDoor);
        assertTrue(wallTime >= 0 && wallTime < doorTime);
    }

    @Test void endpointOrderingRejectsNearerOrdinaryWallAndUnknownButAllowsClearPath() {
        assertFalse(AirlockBumpSystem.worldEndpointOrderingAllows(.4, false));
        assertFalse(AirlockBumpSystem.worldEndpointOrderingAllows(Double.POSITIVE_INFINITY, true));
        assertTrue(AirlockBumpSystem.worldEndpointOrderingAllows(Double.POSITIVE_INFINITY, false));
        assertTrue(AirlockBumpSystem.worldEndpointOrderingAllows(1.0, false));
    }

    @Test void knownShapePlaneAggregationKeepsNearestWallAndUnknownEdgeWinsWhenNearer() {
        double doorTime=AirlockBumpSystem.timeOfImpact(BODY,new Vec3(2,0,0),
                new AABB(1.5,0,0,2.5,2,1));
        double wallTime=AirlockBumpSystem.timeOfImpact(BODY,new Vec3(2,0,0),
                new AABB(.9,0,0,1.1,2,1));
        var wallFirst=AirlockBumpSystem.earliestWorldPlane(java.util.List.of(
                AirlockBumpSystem.planeWall(wallTime),AirlockBumpSystem.planeWall(doorTime)));
        assertEquals(wallTime,wallFirst.firstT(),1.0E-9);
        assertTrue(wallFirst.doors().isEmpty());
        var unknownFirst=AirlockBumpSystem.earliestWorldPlane(java.util.List.of(
                AirlockBumpSystem.planeUnknown(wallTime),AirlockBumpSystem.planeWall(doorTime)));
        assertTrue(unknownFirst.unknown());
        assertEquals(wallTime,unknownFirst.firstT(),1.0E-9);
    }

    @Test void tiedUnknownPlaneFailsClosedIndependentOfHitOrderAndTolerance() {
        double first=.3, nearTie=first+0.5E-5, outsideTie=first+2.0E-5;
        for (var hits : java.util.List.of(
                java.util.List.of(AirlockBumpSystem.planeDoor(first,null,null),AirlockBumpSystem.planeUnknown(first)),
                java.util.List.of(AirlockBumpSystem.planeUnknown(first),AirlockBumpSystem.planeDoor(first,null,null)),
                java.util.List.of(AirlockBumpSystem.planeDoor(first,null,null),AirlockBumpSystem.planeUnknown(nearTie)))) {
            var plane=AirlockBumpSystem.earliestWorldPlane(hits);
            assertTrue(plane.unknown());
            assertFalse(AirlockBumpSystem.worldEndpointOrderingAllows(plane.firstT(),plane.unknown()));
        }
        var earlierKnown=AirlockBumpSystem.earliestWorldPlane(java.util.List.of(
                AirlockBumpSystem.planeUnknown(first),AirlockBumpSystem.planeWall(first-2.0E-5)));
        assertFalse(earlierKnown.unknown());
        assertEquals(first-2.0E-5,earlierKnown.firstT(),1.0E-9);
        var fartherUnknown=AirlockBumpSystem.earliestWorldPlane(java.util.List.of(
                AirlockBumpSystem.planeDoor(first,null,null),AirlockBumpSystem.planeUnknown(outsideTie)));
        assertFalse(fartherUnknown.unknown());
        assertEquals(first,fartherUnknown.firstT(),1.0E-9);
    }

    @Test void clientWorldOrderingMatchesClientBlockShapeFallbackForOpenAndPredictedViews() {
        var id=java.util.UUID.randomUUID();
        var predictedPassable=new AirlockBlockEntity.ClientView(id,4,AirlockState.DoorPhase.OPENING_PASSABLE,
                0,0,0,true,true,false,true,false,false);
        var blocked=new AirlockBlockEntity.ClientView(id,5,AirlockState.DoorPhase.CLOSED,
                0,0,0,false,true,false,true,false,false);
        assertFalse(AirlockBumpSystem.clientWorldAirlockSolid(true,null),"unknown OPEN state follows collision fallback");
        assertTrue(AirlockBumpSystem.clientWorldAirlockSolid(false,null),"unknown closed state remains opaque");
        assertFalse(AirlockBumpSystem.clientWorldAirlockSolid(false,predictedPassable),"effective passable view wins over closed bit");
        assertTrue(AirlockBumpSystem.clientWorldAirlockSolid(true,blocked),"known blocked view wins over open bit");
    }

    @Test void clientRootAndCapturedStateAdmissionRejectUnknownOrChangedEvidence() {
        var id=java.util.UUID.randomUUID();
        var view=new AirlockBlockEntity.ClientView(id,4,AirlockState.DoorPhase.CLOSED,0,0,0,
                false,true,false,true,false,false);
        assertTrue(AirlockBumpSystem.clientRootMatches(id,view));
        assertFalse(AirlockBumpSystem.clientRootMatches(null,view));
        assertFalse(AirlockBumpSystem.clientRootMatches(java.util.UUID.randomUUID(),view));
        assertFalse(AirlockBumpSystem.clientRootMatches(id,null));
        var closed=net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState();
        var changed=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        assertTrue(AirlockBumpSystem.sameCapturedBlockState(closed,closed));
        assertFalse(AirlockBumpSystem.sameCapturedBlockState(closed,changed));
        assertFalse(AirlockBumpSystem.sameCapturedBlockState(closed,null));
    }

    @Test void staticContactIncludesEachFaceButNotCornerVerticalSupportOrRealGap(){
        AABB doorway=new AABB(0,0,0,1,2,1);
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(-.6,0,.2,0,1.8,.8),doorway));
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(1,0,.2,1.6,1.8,.8),doorway));
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(.2,0,-.6,.8,1.8,0),doorway));
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(.2,0,1,.8,1.8,1.6),doorway));
        assertFalse(AirlockGeometry.touchesDoorway(new AABB(-.6,0,-.6,0,1.8,0),doorway));
        assertFalse(AirlockGeometry.touchesDoorway(new AABB(.2,2,.2,.8,3,.8),doorway));
        assertFalse(AirlockGeometry.touchesDoorway(new AABB(-.6,0,.2,-.01,1.8,.8),doorway));
    }
}
