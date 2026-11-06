package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BulkMassQuantizationOracleTest {
    @Test
    void sequentialRebasesAccumulateDeterministicFloatDrift() {
        var oracle = new BulkMassQuantizationOracle(new Vector3d(4000, 0, 0), new Quaterniond(), new Vector3d());
        for (int i = 1; i <= 384; i++) {
            Vector3d com = new Vector3d(i * .0001, 0, 0);
            oracle.rebase(com);
        }
        assertEquals(384, oracle.rebases());
        assertEquals(.0384, oracle.centerOfMass().x, 1e-12);
        assertEquals(4000, oracle.position().x);
        assertEquals(4000.0001, oracle.logicalPosition().x, 1e-10);
        assertEquals(3999.9617, oracle.logicalPose().transformPosition(new Vector3d()).x, 1e-9);
    }

    @Test
    void returningComToStartDoesNotUndoFloatQuantization() {
        var oracle = new BulkMassQuantizationOracle(new Vector3d(4000, 0, 0), new Quaterniond(), new Vector3d());
        oracle.rebase(new Vector3d(.0001, 0, 0));
        oracle.rebase(new Vector3d(.0002, 0, 0));
        oracle.rebase(new Vector3d(.0003, 0, 0));
        oracle.rebase(new Vector3d());
        assertEquals(3999.9997, oracle.logicalPosition().x, 1e-10);
        assertEquals(3999.999755859375, oracle.position().x);
        assertEquals(-.000244140625, oracle.position().x - 4000, 1e-12);
    }

    @Test
    void rotatesComDeltaBeforeApplyingAndQuantizingAtLargeWorldCoordinates() {
        var rotation = new Quaterniond().rotationY(Math.PI / 2);
        var oracle = new BulkMassQuantizationOracle(new Vector3d(8192, 32, -4096), rotation, new Vector3d(1, 2, 3));
        oracle.rebase(new Vector3d(1.0001, 2.0002, 3.0003));
        Vector3d delta = new Vector3d(.0001, .0002, .0003);
        rotation.transform(delta);
        assertEquals(8192 + delta.x, oracle.logicalPosition().x, 1e-10);
        assertEquals(32 + delta.y, oracle.logicalPosition().y, 1e-10);
        assertEquals(-4096 + delta.z, oracle.logicalPosition().z, 1e-10);
        assertEquals((double)(float)oracle.logicalPosition().x, oracle.position().x);
    }

    @Test
    void logicalPoseUsesUnroundedUploadTargetAndCurrentCenterOfMass() {
        var oracle = new BulkMassQuantizationOracle(new Vector3d(4000, 0, 0), new Quaterniond(), new Vector3d());
        oracle.rebase(new Vector3d(.0001, 0, 0));
        assertNotEquals(oracle.logicalPosition().x, oracle.position().x);
        Pose3d pose = oracle.logicalPose();
        assertEquals(oracle.logicalPosition(), pose.position());
        assertEquals(oracle.logicalPosition(), pose.transformPosition(new Vector3d(oracle.centerOfMass())));
    }

    @Test
    void constructorAndAccessorsDoNotExposeMutableState() {
        Vector3d pos = new Vector3d(1, 2, 3), com = new Vector3d(4, 5, 6);
        Quaterniond rot = new Quaterniond().rotationXYZ(.1, .2, .3);
        var oracle = new BulkMassQuantizationOracle(pos, rot, com);
        pos.zero(); com.zero(); rot.identity();
        oracle.position().zero(); oracle.logicalPosition().zero(); oracle.centerOfMass().zero(); oracle.rotation().identity();
        assertEquals(new Vector3d(1, 2, 3), oracle.position());
        assertEquals(new Vector3d(4, 5, 6), oracle.centerOfMass());
        assertNotEquals(new Quaterniond(), oracle.rotation());
    }

    @Test
    void heldWorldAnchorProjectsThroughLogicalPoseNotQuantizedEndpoint() {
        var rotation = new Quaterniond().rotationXYZ(.2, -.3, .4);
        var oracle = new BulkMassQuantizationOracle(new Vector3d(4000, 2, 3), rotation, new Vector3d(2048, 10, -34));
        oracle.rebase(new Vector3d(.0001, 0, 0));
        Vector3d heldWorld = new Vector3d(4003.125, 2.75, 3.5);
        Pose3d logicalPose = oracle.logicalPose();
        Vector3d projected = logicalPose.transformPositionInverse(new Vector3d(heldWorld));
        Vector3d reconstructed = logicalPose.transformPosition(new Vector3d(projected));
        assertEquals(heldWorld.x, reconstructed.x, 1e-8);
        assertEquals(heldWorld.y, reconstructed.y, 1e-8);
        assertEquals(heldWorld.z, reconstructed.z, 1e-8);
        Pose3d quantizedPose = new Pose3d(oracle.position(), oracle.rotation(), oracle.centerOfMass(), new Vector3d(1));
        assertTrue(heldWorld.distance(quantizedPose.transformPosition(new Vector3d(projected))) > 1e-8);
    }
}
