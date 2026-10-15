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

    @Test void staticContactIncludesEachFaceButNotCornerVerticalSupportOrRealGap(){
        AABB doorway=new AABB(0,0,0,1,2,1);
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(-.6,0,.2,0,1.8,.8),doorway));
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(1,0,.2,1.6,1.8,.8),doorway));
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(.2,0,-.6,.8,1.8,0),doorway));
        assertTrue(AirlockGeometry.touchesDoorway(new AABB(.2,0,1,.8,1.8,1.6),doorway));
        AABB toleratedPositiveGap=new AABB(-.600005,0,.2,-.000005,1.8,.8);
        assertTrue(doorway.minX-toleratedPositiveGap.maxX>0);
        assertFalse(toleratedPositiveGap.intersects(doorway));
        assertTrue(AirlockGeometry.touchesDoorway(toleratedPositiveGap,doorway));
        assertFalse(AirlockGeometry.touchesDoorway(new AABB(-.6,0,-.6,0,1.8,0),doorway));
        assertFalse(AirlockGeometry.touchesDoorway(new AABB(.2,2,.2,.8,3,.8),doorway));
        assertFalse(AirlockGeometry.touchesDoorway(new AABB(-.6,0,.2,-.01,1.8,.8),doorway));
        assertFalse(AirlockGeometry.touchesDoorway(new AABB(-.60002,0,.2,-.00002,1.8,.8),doorway));
    }
}
