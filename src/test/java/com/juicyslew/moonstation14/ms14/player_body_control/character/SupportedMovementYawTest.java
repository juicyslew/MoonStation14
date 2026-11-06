package com.juicyslew.moonstation14.ms14.player_body_control.character;

import com.juicyslew.moonstation14.ms14.character.AngularRecoveryLook;
import com.juicyslew.moonstation14.ms14.character.AngularRecoveryModel;
import com.juicyslew.moonstation14.ms14.standing.WorldUpTwist;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SupportedMovementYawTest {
    private static final UUID FRAME = UUID.randomUUID();

    @Test
    void supportQuarterTurnRotatesWorldForwardWishWithMinecraftSign() {
        float turnedFrameYaw = WorldUpTwist.degrees(new Quaterniond().rotationY(Math.PI / 2));
        assertEquals(-90f, turnedFrameYaw, 1e-5f);
        var reference = SupportedMovementYaw.capture(FRAME, 0, 0);
        float movementYaw = SupportedMovementYaw.resolve(reference, 0, true, FRAME, turnedFrameYaw);
        assertEquals(-90f, movementYaw, 1e-5f);
        // GroundedHarnessMotor's forward basis is (-sin(yaw), cos(yaw)) in X/Z.
        assertEquals(1f, (float) -Math.sin(Math.toRadians(movementYaw)), 1e-6f);
        assertEquals(0f, (float) Math.cos(Math.toRadians(movementYaw)), 1e-6f);
    }

    @Test
    void heldReferenceKeepsDeckRelativeHeadingAndDoesNotChangeLookYaw() {
        var reference = SupportedMovementYaw.capture(FRAME, 0, 30);
        assertEquals(30f, SupportedMovementYaw.resolve(reference, 77, true, FRAME, 0), 1e-5f);
        assertEquals(-15f, SupportedMovementYaw.resolve(reference, 77, true, FRAME, -45), 1e-5f);
        assertEquals(-60f, SupportedMovementYaw.resolve(reference, 77, true, FRAME, -90), 1e-5f);
        float rawLookYaw = 37f;
        assertEquals(37f, rawLookYaw, 0f, "movement-frame resolution does not modify raw look yaw");
        // Strafe's planar basis stays perpendicular to the resolved forward basis.
        for (float yaw : new float[]{30, -15, -60}) {
            double forwardX = -Math.sin(Math.toRadians(yaw)), forwardZ = Math.cos(Math.toRadians(yaw));
            double strafeX = Math.cos(Math.toRadians(yaw)), strafeZ = Math.sin(Math.toRadians(yaw));
            assertEquals(0, forwardX * strafeX + forwardZ * strafeZ, 1e-12);
            assertEquals(1, Math.hypot(strafeX, strafeZ), 1e-12);
        }
    }

    @Test
    void airborneAndMissingOrMismatchedReferencesUseWorldYawFallback() {
        UUID other = UUID.randomUUID();
        var reference = SupportedMovementYaw.capture(FRAME, 0, 25);
        assertEquals(123f, SupportedMovementYaw.resolve(reference, 123, false, FRAME, -90));
        assertEquals(123f, SupportedMovementYaw.resolve(reference, 123, true, other, -90));
        assertEquals(123f, SupportedMovementYaw.resolve(null, 123, true, FRAME, -90));
    }

    @Test
    void inertialLookYawIsCapturedOnceAndFrameDeltaIsAppliedOnceAcrossWrap() {
        var orientation = AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0, 1, 0), .4);
        var state = new AngularRecoveryModel.State(orientation, new AngularRecoveryModel.Vec3(0, 0, 0), 0, false);
        float worldLookYaw = AngularRecoveryLook.worldPlanarYaw(state, 37);
        float initialFrameYaw = WorldUpTwist.degrees(new Quaterniond().rotationY(.8));
        var reference = SupportedMovementYaw.capture(FRAME, initialFrameYaw, worldLookYaw);
        assertEquals(worldLookYaw, SupportedMovementYaw.resolve(reference, 0, true, FRAME, initialFrameYaw), 1e-4f);
        float nextFrameYaw = WorldUpTwist.degrees(new Quaterniond().rotationY(.2));
        assertEquals(SupportedMovementYaw.wrap(worldLookYaw + nextFrameYaw - initialFrameYaw),
                SupportedMovementYaw.resolve(reference, 0, true, FRAME, nextFrameYaw), 1e-4f);
        var nearWrap = SupportedMovementYaw.capture(FRAME, 0, 179);
        assertEquals(-179f, SupportedMovementYaw.resolve(nearWrap, 0, true, FRAME, 2), 1e-5f);
    }
}
