package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameExamineGeometryTest {
    @Test void cameraRelativeOutlinePreservesGlobalStorageCornersAndUnitSize() {
        for (boolean rotated : new boolean[]{false, true}) for (double viewBase : new double[]{10.0, 5000.0}) {
            var pose = new dev.ryanhcode.sable.companion.math.Pose3d();
            pose.position().set(viewBase, 128.25, viewBase);
            pose.orientation().rotateY(rotated ? Math.PI / 2 : 0.0);
            pose.rotationPoint().set(20_481_032.375, 128.25, 20_481_032.625);
            var original = new AABB(20_481_032, 127, 20_481_032, 20_481_033, 128, 20_481_033);
            var camera = new net.minecraft.world.phys.Vec3(viewBase - 0.125, 128.5, viewBase - 0.25);
            var output = FrameExamineGeometry.cameraRelativeOutline(original, pose, camera);
            var nativeMatrix = pose.bakeIntoMatrix(new org.joml.Matrix4d());
            var centerFromCamera = nativeMatrix.transformPosition(new org.joml.Vector3d(
                    (original.minX + original.maxX) * .5, (original.minY + original.maxY) * .5,
                    (original.minZ + original.maxZ) * .5)).sub(camera.x, camera.y, camera.z);
            assertTrue(centerFromCamera.length() < 8.0, "fixture native pose must place geometry near camera");
            var eventBase = new org.joml.Matrix4d().translation(0.25, -0.5, 1.25).rotateY(0.17);
            var actualMatrix = new org.joml.Matrix4d(eventBase).mul(new org.joml.Matrix4d(output.matrix()));
            for (int corner = 0; corner < 8; corner++) {
                double x = (corner & 1) == 0 ? original.minX : original.maxX;
                double y = (corner & 2) == 0 ? original.minY : original.maxY;
                double z = (corner & 4) == 0 ? original.minZ : original.maxZ;
                var expected = nativeMatrix.transformPosition(new org.joml.Vector3d(x, y, z))
                        .sub(camera.x, camera.y, camera.z);
                eventBase.transformPosition(expected);
                var relative = new org.joml.Vector3d(
                        x - (original.minX + original.maxX) * .5,
                        y - (original.minY + original.maxY) * .5,
                        z - (original.minZ + original.maxZ) * .5);
                actualMatrix.transformPosition(relative);
                assertEquals(expected.x, relative.x, 1e-4);
                assertEquals(expected.y, relative.y, 1e-4);
                assertEquals(expected.z, relative.z, 1e-4);
            }
            assertEquals(1.0, output.localBounds().getXsize(), 1e-9);
            assertEquals(1.0, output.localBounds().getZsize(), 1e-9);
        }
        // The former global-float vertex path loses the unit edge at this storage origin.
        assertEquals((float)20_481_032, (float)20_481_033);
        assertTrue(Math.ulp((float)20_481_032) >= 2.0f);
    }

    @Test void boundsUseNativePoseRotationAndTranslation() {
        var matrix = new org.joml.Matrix4d().translation(10, 20, 30).rotateY(Math.PI / 2);
        var bounds = FrameBoundsTransform.transformedBounds(new AABB(1, 2, 3, 2, 4, 5), matrix);
        assertEquals(13, bounds.minX, 1e-9);
        assertEquals(15, bounds.maxX, 1e-9);
        assertEquals(22, bounds.minY, 1e-9);
        assertEquals(24, bounds.maxY, 1e-9);
        assertEquals(28, bounds.minZ, 1e-9);
        assertEquals(29, bounds.maxZ, 1e-9);
    }
}
