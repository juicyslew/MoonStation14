package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import dev.ryanhcode.sable.companion.math.Pose3d;

/** Independent fixture-only model of Sable's per-block COM rebases and f32 native pose storage. */
final class BulkMassQuantizationOracle {
    private final Quaterniond rotation;
    private final Vector3d nativePosition;
    private Vector3d previousCom;
    private Vector3d logicalPosition;
    private int rebases;

    BulkMassQuantizationOracle(Vector3d nativePosition, Quaterniond rotation, Vector3d initialCom) {
        this.nativePosition = new Vector3d(nativePosition);
        this.logicalPosition = new Vector3d(nativePosition);
        this.rotation = new Quaterniond(rotation);
        this.previousCom = new Vector3d(initialCom);
    }

    void rebase(Vector3d newCom) {
        Vector3d delta = new Vector3d(newCom).sub(previousCom);
        rotation.transform(delta);
        logicalPosition = new Vector3d(nativePosition).add(delta);
        nativePosition.set(logicalPosition);
        nativePosition.set((float) nativePosition.x, (float) nativePosition.y, (float) nativePosition.z);
        previousCom = new Vector3d(newCom);
        rebases++;
    }

    Vector3d position() { return new Vector3d(nativePosition); }
    Vector3d logicalPosition() { return new Vector3d(logicalPosition); }
    Vector3d centerOfMass() { return new Vector3d(previousCom); }
    Quaterniond rotation() { return new Quaterniond(rotation); }
    Pose3d logicalPose() {
        return new Pose3d(logicalPosition(), rotation(), centerOfMass(), new Vector3d(1));
    }
    int rebases() { return rebases; }
}
