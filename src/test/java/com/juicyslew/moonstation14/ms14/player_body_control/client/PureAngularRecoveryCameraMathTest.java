package com.juicyslew.moonstation14.ms14.player_body_control.client;

import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook;

import static org.junit.jupiter.api.Assertions.*;

class PureAngularRecoveryCameraMathTest {
    @Test void usesMinecraftCameraRotationConventionAndPreservesAllBasisVectors() {
        check(new Quaternionf(), 0, 0, 0);
        check(new Quaternionf(), 90, 0, 0);
        check(new Quaternionf(), 0, 0, 45);
        check(new Quaternionf().rotationXYZ(.4f, -.7f, .3f), 27, -18, 36);
    }

    @Test void preservesCameraOrientationAtVerticalLockAcrossYawWrap() {
        Quaternionf identity = new Quaternionf();
        Quaternionf yawOnly = axisAngle(0, 1, 0, .73f);
        Quaternionf tilted = axisAngle(1, 0, 0, .41f).mul(axisAngle(0, 1, 0, -.62f)).normalize();
        for (Quaternionf inertial : new Quaternionf[]{identity, yawOnly, tilted}) {
            for (float pitch : new float[]{90, -90, 89.999f, -89.999f, 89.99f, -89.99f,
                    89.99998f, -89.99998f, 89.99994f, -89.99994f, 89.9999f, -89.9999f}) {
                for (float yaw : new float[]{179.99f, 180, 180.01f, -180.01f, -180, -179.99f, 0, 90}) {
                    for (float roll : new float[]{0, 35, 90, 179}) check(inertial, yaw, pitch, roll);
                }
            }
        }
    }

    // Explicit canonical quaternion avoids depending on older JOML rotationX behavior.
    private static Quaternionf axisAngle(float x, float y, float z, float angle) {
        float half = angle * .5f, sine = (float) Math.sin(half);
        return new Quaternionf(x * sine, y * sine, z * sine, (float) Math.cos(half));
    }

    private static void check(Quaternionf inertial, float yaw, float pitch, float roll) {
        ProbeCamera probe = new ProbeCamera();
        probe.angles(yaw, pitch, roll);
        Quaternionf cameraBase = new Quaternionf().rotationYXZ((float)Math.toRadians(180-yaw),
                (float)Math.toRadians(-pitch), (float)Math.toRadians(-roll));
        assertTrue(Math.abs(cameraBase.dot(probe.rotation())) > .99999f,
                "verified base convention raw camera=" + probe.rotation() + " candidate=" + cameraBase);
        Quaternionf expected = new Quaternionf(inertial).mul(probe.rotation()).normalize();
        var result = PureAngularRecoveryCameraMath.compose(inertial, yaw, pitch, roll);
        assertTrue(Float.isFinite(result.yaw()) && Float.isFinite(result.pitch()) && Float.isFinite(result.roll()),
                "finite camera angles at yaw=" + yaw + " pitch=" + pitch + " roll=" + roll + " result=" + result);
        probe.angles(result.yaw(), result.pitch(), result.roll());
        Quaternionf actual = new Quaternionf(probe.rotation()).normalize();
        assertTrue(Math.abs(expected.dot(actual)) > .99999f, "full camera orientation must match: expected=" + expected + " actual=" + actual + " angles=" + result);
        var modelState = AngularRecoveryModel.departure(new AngularRecoveryModel.Quat(inertial.w, inertial.x, inertial.y, inertial.z),
                new AngularRecoveryModel.Vec3(0, 0, 0));
        var expectedLook = AngularRecoveryLook.worldLook(modelState, yaw, pitch);
        Vector3f cameraForward = actual.transform(new Vector3f(0, 0, -1));
        assertTrue(cameraForward.distance(new Vector3f((float) expectedLook.x(), (float) expectedLook.y(), (float) expectedLook.z())) < .001f,
                "camera forward must match physical world look");
        assertTrue(expected.transform(new Vector3f(0, 1, 0)).distance(actual.transform(new Vector3f(0, 1, 0))) < .001f);
    }

    private static final class ProbeCamera extends Camera {
        void angles(float yaw, float pitch, float roll) { super.setRotation(yaw, pitch, roll); }
    }
}
