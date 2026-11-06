package com.juicyslew.moonstation14.ms14.eye.client;

import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AngularRecoveryComfortCameraPolicyTest {
    private static AngularRecoveryModel.State state(double yaw, double tilt) {
        var q = AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0, 1, 0), yaw)
                .multiply(AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(1, 0, 0), tilt));
        return AngularRecoveryModel.departure(q, new AngularRecoveryModel.Vec3(0, 0, 0));
    }
    private static AngularRecoveryModel.Vec3 forward(float yaw, float pitch) {
        return AngularRecoveryLook.worldLook(null, yaw, pitch);
    }

    @Test void entersAtCurrentWorldHeading() {
        var policy = new AngularRecoveryComfortCameraPolicy();
        policy.start(state(Math.PI / 2, .3), 20, -15);
        var expected = AngularRecoveryLook.worldLook(state(Math.PI / 2, .3), 20, -15);
        var got = policy.worldAngles().orElseThrow();
        var actual = forward(got.yaw(), got.pitch());
        assertEquals(expected.x(), actual.x(), 1e-6); assertEquals(expected.y(), actual.y(), 1e-6);
        assertEquals(expected.z(), actual.z(), 1e-6);
    }

    @Test void mouseDeltaMovesViewButBodySpinDoesNot() {
        var policy = new AngularRecoveryComfortCameraPolicy();
        policy.start(state(0, 0), 170, 10);
        policy.update(-170, 10);
        assertEquals(-170, policy.worldAngles().orElseThrow().yaw(), 1e-5);
        assertEquals(10, policy.worldAngles().orElseThrow().pitch(), 1e-5);
    }

    @Test void finishInverseTransformsLookForCurrentTiltedBody() {
        var policy = new AngularRecoveryComfortCameraPolicy();
        policy.start(state(.4, .2), 30, -12);
        var stable = policy.worldAngles().orElseThrow();
        var current = state(-.8, .7);
        var raw = policy.finish(current).orElseThrow();
        var actual = AngularRecoveryLook.worldLook(current, raw.yaw(), raw.pitch());
        var expected = forward(stable.yaw(), stable.pitch());
        assertEquals(expected.x(), actual.x(), 1e-6); assertEquals(expected.y(), actual.y(), 1e-6);
        assertEquals(expected.z(), actual.z(), 1e-6);
        assertFalse(policy.active());
    }

    @Test void clearAndRestartDoesNotReuseOldLook() {
        var policy = new AngularRecoveryComfortCameraPolicy();
        policy.start(state(0, 0), 80, 25); policy.clear();
        assertTrue(policy.finish(null).isEmpty());
        policy.start(null, -40, -15);
        assertEquals(-40, policy.worldAngles().orElseThrow().yaw(), 1e-5);
        assertEquals(-15, policy.worldAngles().orElseThrow().pitch(), 1e-5);
    }

    @Test void wrapsYawDeltaAndClampsPitch() {
        var policy = new AngularRecoveryComfortCameraPolicy();
        policy.start(null, 179, 80);
        policy.update(-179, 100);
        assertEquals(-179, policy.worldAngles().orElseThrow().yaw(), 1e-5);
        assertEquals(90, policy.worldAngles().orElseThrow().pitch(), 1e-5);
    }
}
