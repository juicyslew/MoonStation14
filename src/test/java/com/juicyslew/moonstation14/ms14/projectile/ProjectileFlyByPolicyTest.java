package com.juicyslew.moonstation14.ms14.projectile;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectileFlyByPolicyTest {
    private static final AABB LISTENER = new AABB(-.3, 0, -.3, .3, 1.8, .3);

    @Test void probabilityBoundariesAndShooterAreRespected() {
        assertTrue(ProjectileFlyByCuePolicy.shouldCue(false, true, .1f, .099f));
        assertFalse(ProjectileFlyByCuePolicy.shouldCue(false, true, .1f, .1f));
        assertTrue(ProjectileFlyByCuePolicy.shouldCue(false, true, 1, .999f));
        assertFalse(ProjectileFlyByCuePolicy.shouldCue(false, true, 0, 0));
        assertFalse(ProjectileFlyByCuePolicy.shouldCue(true, true, 1, 0));
        assertFalse(ProjectileFlyByCuePolicy.shouldCue(false, false, 1, 0));
        assertFalse(ProjectileFlyByCuePolicy.shouldCue(false, true, true, 1, 0));
    }

    @Test void segmentSweepFindsThreeDimensionalFlyByAndEnterEdgesDoNotRepeat() {
        assertTrue(ProjectileFlyByGeometry.intersects(new Vec3(-4, .9, 0), new Vec3(4, .9, 0), LISTENER, 1.5));
        assertFalse(ProjectileFlyByGeometry.intersects(new Vec3(-4, 4, 0), new Vec3(4, 4, 0), LISTENER, 1.5));
        assertFalse(ProjectileFlyByGeometry.intersects(new Vec3(3, .9, 0), new Vec3(4, .9, 0), LISTENER, 1.5));
        // initial overlap is an entry; stationary continuation remains inside and cannot cue again
        assertTrue(ProjectileFlyByCuePolicy.shouldCue(false,
                ProjectileFlyByGeometry.intersects(Vec3.ZERO, Vec3.ZERO, LISTENER, 1.5), 1, 0));
        assertFalse(ProjectileFlyByCuePolicy.shouldCue(true,
                ProjectileFlyByGeometry.intersects(Vec3.ZERO, Vec3.ZERO, LISTENER, 1.5), 1, 0));
        // Leaving clears inside; a later approach is a new entry.
        assertFalse(ProjectileFlyByGeometry.intersects(new Vec3(4, .9, 0), new Vec3(5, .9, 0), LISTENER, 1.5));
        assertTrue(ProjectileFlyByCuePolicy.shouldCue(false,
                ProjectileFlyByGeometry.intersects(new Vec3(4, .9, 0), new Vec3(0, .9, 0), LISTENER, 1.5), 1, 0));
    }
}
