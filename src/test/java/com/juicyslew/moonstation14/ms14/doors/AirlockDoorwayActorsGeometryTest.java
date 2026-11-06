package com.juicyslew.moonstation14.ms14.doors;

import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirlockDoorwayActorsGeometryTest {
    private static final AABB STORAGE_DOOR = new AABB(100, 20, -40, 101, 22, -39);

    @Test
    void projectsStorageVolumeToCurrentWorldOccupantNotStoragePhantom() {
        Pose3d pose = translated(1000, 80, 500);
        AABB body = new AABB(1100.2, 100.2, 460.2, 1100.8, 101.8, 460.8);
        assertTrue(AirlockDoorwayActors.intersects(STORAGE_DOOR, pose, body, 0));
        assertFalse(AirlockDoorwayActors.intersects(STORAGE_DOOR, pose,
                new AABB(100.2, 20.2, -39.8, 100.8, 21.8, -39.2), 0));
    }

    @Test
    void sdkObbRejectsRotatedBroadphaseCornerFalsePositive() {
        Pose3d pose = translated(0, 0, 0);
        pose.orientation().rotationY(Math.PI / 4);
        AABB doorway = new AABB(0, 0, 0, 1, 2, 1);
        AABB broadphase = com.juicyslew.moonstation14.ms14.examine.FrameBoundsTransform.transformedBounds(
                doorway, pose.bakeIntoMatrix(new org.joml.Matrix4d()));
        AABB broadphaseCorner = new AABB(1.32, .4, -.65, 1.38, 1.6, -.59);
        assertTrue(broadphase.intersects(broadphaseCorner));
        // Inverse yaw coordinates put even the nearest corner beyond the doorway's
        // x=1 face, while the enclosing transformed AABB still admits this candidate.
        assertTrue((broadphaseCorner.minX - broadphaseCorner.maxZ) / Math.sqrt(2) > 1);
        assertFalse(AirlockDoorwayActors.intersects(doorway, pose, broadphaseCorner, 0));
    }

    @Test
    void touchingMarginIsExplicitAndDoesNotChangeInputPoseOrBounds() {
        Pose3d pose = translated(20, 5, 30);
        double[] matrixBefore = matrix(pose);
        AABB doorway = new AABB(4, 10, 8, 5, 12, 9);
        AABB tangent = new AABB(25, 15.2, 38.2, 25.2, 16.8, 38.8);
        AABB doorwayBefore = doorway;
        AABB actorBefore = tangent;
        assertFalse(AirlockDoorwayActors.intersects(doorway, pose, tangent, 0));
        assertTrue(AirlockDoorwayActors.intersects(doorway, pose, tangent, 1.0E-5));
        assertSame(doorwayBefore, doorway);
        assertSame(actorBefore, tangent);
        assertArrayEquals(matrixBefore, matrix(pose));
    }

    private static Pose3d translated(double x, double y, double z) {
        Pose3d pose = new Pose3d();
        pose.position().set(x, y, z);
        pose.scale().set(1, 1, 1);
        return pose;
    }
    private static double[] matrix(Pose3d pose) {
        var m = pose.bakeIntoMatrix(new org.joml.Matrix4d());
        return new double[]{m.m00(),m.m01(),m.m02(),m.m03(),m.m10(),m.m11(),m.m12(),m.m13(),
                m.m20(),m.m21(),m.m22(),m.m23(),m.m30(),m.m31(),m.m32(),m.m33()};
    }
}
