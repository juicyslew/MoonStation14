package com.juicyslew.moonstation14.ms14.character;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AngularRecoveryLookTest {
    @Test void settledBasisPreservesRawLookAndQuarterTurnRotatesHeadLookIncludingPitch() {
        var settled = AngularRecoveryModel.departure(new AngularRecoveryModel.Quat(1, 0, 0, 0),
                new AngularRecoveryModel.Vec3(0, 0, 0));
        var settledLook = AngularRecoveryLook.worldLook(settled, 37, -22);
        var rawLook = AngularRecoveryLook.worldLook(null, 37, -22);
        assertEquals(rawLook.x(), settledLook.x(), 1e-10);
        assertEquals(rawLook.y(), settledLook.y(), 1e-10);
        assertEquals(rawLook.z(), settledLook.z(), 1e-10);

        var quarterTurn = AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(
                new AngularRecoveryModel.Vec3(0, 1, 0), Math.PI / 2), new AngularRecoveryModel.Vec3(0, 0, 0));
        var look = AngularRecoveryLook.worldLook(quarterTurn, 0, 30);
        assertEquals(Math.sqrt(3) / 2, look.x(), 1e-10);
        assertEquals(-0.5, look.y(), 1e-10);
        assertEquals(0, look.z(), 1e-10);

        var roll = AngularRecoveryModel.departure(AngularRecoveryModel.Quat.fromAxisAngle(
                new AngularRecoveryModel.Vec3(0, 0, 1), Math.PI / 2), new AngularRecoveryModel.Vec3(0, 0, 0));
        var rolledLook = AngularRecoveryLook.worldLook(roll, 0, 0);
        assertEquals(0, rolledLook.x(), 1e-10);
        assertEquals(0, rolledLook.y(), 1e-10);
        assertEquals(1, rolledLook.z(), 1e-10);
        var rolledUp = roll.orientation().rotate(new AngularRecoveryModel.Vec3(0, 1, 0));
        assertEquals(-1, rolledUp.x(), 1e-10);
        assertEquals(0, rolledUp.y(), 1e-10);
        assertEquals(0, rolledUp.z(), 1e-10);
    }

    @Test void appliesBasisToRawLookAndSharesItsWorldPlanarHeading() {
        var basis = AngularRecoveryModel.Quat.fromAxisAngle(new AngularRecoveryModel.Vec3(0, 1, 0), Math.PI / 2);
        var state = AngularRecoveryModel.departure(basis, new AngularRecoveryModel.Vec3(0, 0, 0));
        var look = AngularRecoveryLook.worldLook(state, 0, 0);
        assertEquals(1, look.x(), 1e-10);
        assertEquals(-90, AngularRecoveryLook.worldPlanarYaw(state, 0), 1e-5);
        assertEquals(31, AngularRecoveryLook.worldPlanarYaw(null, 31), 0); // no state preserves raw yaw
    }
}
