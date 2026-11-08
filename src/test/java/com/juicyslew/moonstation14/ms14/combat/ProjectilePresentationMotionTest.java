package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectilePresentationMotionTest {
    @Test void advancesAtTwentyTicksPerSecondAndUsesCorrectionAsNewOrigin() {
        Vec3 origin=new Vec3(1,2,3), velocity=new Vec3(40,0,0);
        Vec3 presented=ProjectilePresentationMotion.advance(origin,velocity);
        assertEquals(new Vec3(41,2,3),presented);
        Vec3 corrected=new Vec3(80,5,0);
        assertEquals(new Vec3(120,5,0),ProjectilePresentationMotion.advance(corrected,velocity));
    }
    @Test void zeroMotionIsStableAndNonFiniteInputsAreIgnored() {
        Vec3 p=new Vec3(2,3,4);
        assertEquals(p,ProjectilePresentationMotion.advance(p,Vec3.ZERO));
        assertEquals(p,ProjectilePresentationMotion.advance(p,new Vec3(Double.NaN,0,0)));
        assertEquals(Vec3.ZERO,ProjectilePresentationMotion.advance(new Vec3(Double.POSITIVE_INFINITY,0,0),Vec3.ZERO));
    }
    @Test void segmentDetectsFastCrossingAndDegeneratePoint() {
        assertEquals(0, ProjectilePresentationMotion.closestSegmentDistanceSquared(new Vec3(-10,0,0),new Vec3(10,0,0),Vec3.ZERO));
        assertEquals(4, ProjectilePresentationMotion.closestSegmentDistanceSquared(new Vec3(2,0,0),new Vec3(2,0,0),Vec3.ZERO));
        assertTrue(Double.isNaN(ProjectilePresentationMotion.closestSegmentDistanceSquared(new Vec3(Double.NaN,0,0),Vec3.ZERO,Vec3.ZERO)));
    }
}
