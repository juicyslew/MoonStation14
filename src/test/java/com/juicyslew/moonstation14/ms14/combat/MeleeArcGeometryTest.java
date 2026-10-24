package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeArcGeometryTest {
    @Test void closeTargetUsesTargetBearingAndDistance() {
        var aim = MeleeArcGeometry.capture(Vec3.ZERO, 2, new Vec3(0,0,1), new Vec3(1,20,0), 4);
        assertEquals(new Vec3(1,0,0), aim.direction());
        assertEquals(new Vec3(1,1,0), aim.attackPoint());
    }
    @Test void missingTargetUsesMaximumPointAndRangeClampsAtZero() {
        assertEquals(new Vec3(2.8,1,0), MeleeArcGeometry.capture(Vec3.ZERO,2,new Vec3(1,0,0),null,3).attackPoint());
        assertEquals(Vec3.ZERO, MeleeArcGeometry.capture(Vec3.ZERO,0,new Vec3(1,0,0),null,.1).attackPoint());
    }
    @Test void nonfiniteTargetDistanceFallsBackToMaximumAndCapturedVectorsAreFrozen() {
        var source = new Vec3(1,2,3);
        var aim = MeleeArcGeometry.capture(source,2,new Vec3(1,0,0),new Vec3(Double.NaN,0,0),3);
        source.add(100,100,100);
        assertEquals(new Vec3(1,2,3), aim.bodyPosition());
        assertEquals(new Vec3(3.8,3,3), aim.attackPoint());
    }
}
