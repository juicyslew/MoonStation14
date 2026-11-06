package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeIdleProbeEndpointSupportTest {
    private static final NativeActorYaw YAW = NativeActorYaw.fromSamples(List.of(
            new CurrentFrameCollisionScope.YawSample(1, 1, null, null, 0, null, 0, false)), true).orElseThrow();
    private static final AABB FLOOR = new AABB(-2, 0, -2, 2, 1, 2);

    @Test void acceptsUprightTopContact() {
        assertTrue(supports(new AABB(-.3, 1, -.3, .3, 2.8, .3), new Pose3d(), FLOOR));
    }

    @Test void acceptsTiltedTopContactWithUprightBody() {
        Pose3d pose = poseZ(Math.toRadians(42.5));
        AABB candidate = contactBox(pose, FLOOR.maxY);
        assertTrue(supports(candidate, pose, FLOOR));
    }

    @Test void rejectsCandidateBelowPlane() {
        assertFalse(supports(new AABB(-.3, .99, -.3, .3, 2.79, .3), new Pose3d(), FLOOR));
    }

    @Test void rejectsGapBeyondProbe() {
        assertFalse(supports(new AABB(-.3, 1.002, -.3, .3, 2.802, .3), new Pose3d(), FLOOR));
    }

    @Test void rejectsCandidateOutsideBlockFootprint() {
        assertFalse(supports(new AABB(2.1, 1, 2.1, 2.7, 2.8, 2.7), new Pose3d(), FLOOR));
    }

    @Test void rejectsUpsideDownCeilingPlane() {
        Pose3d upsideDown = poseZ(Math.PI);
        assertFalse(supports(new AABB(-.3, 1, -.3, .3, 2.8, .3), upsideDown, FLOOR));
    }

    @Test void rejectsInvalidGeometry() {
        assertFalse(supports(new AABB(0, 0, 0, Double.NaN, 1, 1), new Pose3d(), FLOOR));
        assertFalse(NativeIdleProbeEndpointSupport.geometricSupport(
                new AABB(-.3, 1, -.3, .3, 2.8, .3), YAW, new Pose3d(), List.of()));
    }

    private static boolean supports(AABB candidate, Pose3d pose, AABB floor) {
        return NativeIdleProbeEndpointSupport.geometricSupport(candidate, YAW, pose, List.of(floor));
    }

    private static Pose3d poseZ(double angle) {
        Pose3d pose = new Pose3d();
        pose.orientation().set(0, 0, Math.sin(angle / 2), Math.cos(angle / 2));
        return pose;
    }

    // Fixture-only projection computes the upright box's lowest point in pose-local Y,
    // then translates the box to exact contact; the assertion still exercises production logic.
    private static AABB contactBox(Pose3d pose, double top) {
        AABB box = new AABB(-.3, 0, -.3, .3, 1.8, .3);
        double lowest = Double.POSITIVE_INFINITY;
        Quaterniond inverse = new Quaterniond(pose.orientation()).conjugate();
        for (double x : new double[]{box.minX, box.maxX})
            for (double y : new double[]{box.minY, box.maxY})
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    var point = new org.joml.Vector3d(x, y, z);
                    inverse.transform(point);
                    lowest = Math.min(lowest, point.y);
                }
        double shift = (top - lowest) / pose.orientation().transform(new org.joml.Vector3d(0, 1, 0)).y;
        return box.move(0, shift, 0);
    }
}
