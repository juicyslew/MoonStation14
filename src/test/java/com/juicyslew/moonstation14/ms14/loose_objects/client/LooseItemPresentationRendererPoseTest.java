package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LooseItemPresentationRendererPoseTest {
    private static LooseObjectProfile profile() {
        return new LooseObjectProfile(1, .5f, 0, 10, 1, "flat_sprite", "generic", false,
                0, 10, 1, 0, .5f, .1f,
                new LooseObjectProfile.VisualTuning(.6f, .8f, .13f, -.17f, 1, 0));
    }

    private static Vector3f transformed(PoseStack pose, Vector3f vector) {
        return pose.last().pose().transformPosition(vector);
    }

    private static Vector3f normalize(Vector3f vector) { return vector.normalize(); }

    @Test void cameraFacingViewPoseKeepsSpriteNormalAlignedAtVerticalCameraAngles() {
        var profile = profile();
        for (float pitch : new float[]{-90, 0, 90}) {
            PoseStack pose = new PoseStack();
            var camera = Axis.XP.rotationDegrees(pitch);
            pose.mulPose(camera);
            LooseItemRenderer.applyCameraFacingFlatPose(pose, profile, 1f);

            Vector3f normal = normalize(pose.last().normal().transform(new Vector3f(0, 0, 1)));
            Vector3f expected = normalize(camera.transform(new Vector3f(0, 0, 1)));
            assertEquals(1, normal.dot(expected), 1e-6, "camera pitch " + pitch);

            Vector3f origin = transformed(pose, new Vector3f(0, 0, 0));
            Vector3f expectedOffset = camera.transform(new Vector3f(.13f, -.17f, 0));
            assertEquals(expectedOffset.x, origin.x, 1e-6);
            assertEquals(expectedOffset.y, origin.y, 1e-6);
            assertEquals(expectedOffset.z, origin.z, 1e-6);
            assertEquals(.6f, transformed(pose, new Vector3f(1, 0, 0)).sub(origin).length(), 1e-6);
            assertEquals(.8f, transformed(pose, new Vector3f(0, 1, 0)).sub(origin).length(), 1e-6);
        }
    }

    @Test void floorPoseRetainsNinetyDegreeSpriteRotation() {
        PoseStack pose = new PoseStack();
        LooseItemRenderer.applyFlatPose(pose, profile());
        Vector3f normal = normalize(pose.last().normal().transform(new Vector3f(0, 0, 1)));
        assertEquals(0, normal.x, 1e-6);
        assertEquals(-1, normal.y, 1e-6);
        assertEquals(0, normal.z, 1e-6);
    }
}
