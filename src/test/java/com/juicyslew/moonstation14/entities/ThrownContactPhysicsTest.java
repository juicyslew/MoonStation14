package com.juicyslew.moonstation14.entities;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrownContactPhysicsTest {
    @Test void glancingContactReflectsOnlyNormalComponentWithRestitution() {
        Vec3 result=ThrownContactPhysics.reflect(new Vec3(4,0,2),new Vec3(-1,0,0),.3);
        assertEquals(-1.2,result.x,1e-9);
        assertEquals(2,result.z,1e-9);
    }

    @Test void movingAwayDoesNotReverse() {
        Vec3 incoming=new Vec3(1,0,0);
        assertEquals(incoming,ThrownContactPhysics.reflect(incoming,new Vec3(1,0,0),.3));
    }

    @Test void sourceBaseSweepUsesItemHeightVerticallyAndWidthHorizontally() {
        AABB sweep=ThrownContactPhysics.sourceBaseSweep(new AABB(0,0,0,1,2,1),.65,.08);
        assertEquals(-.08,sweep.minY,1e-9);
        assertEquals(-.325,sweep.minX,1e-9);
        assertEquals(2,sweep.maxY,1e-9);
    }

    @Test void verticalBouncePositionsBaseOutsideCorrectFace() {
        Vec3 top=ThrownContactPhysics.outside(new Vec3(.5,2,.5),new Vec3(0,1,0),.65,.08);
        assertEquals(2.0001,top.y,1e-8);
        Vec3 bottom=ThrownContactPhysics.outside(new Vec3(.5,0,.5),new Vec3(0,-1,0),.65,.08);
        assertEquals(-.0801,bottom.y,1e-8);
    }
}
