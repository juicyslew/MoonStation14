package com.juicyslew.moonstation14.ms14.singularity.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Stable world-up point-facing basis shared by singularity sprites and visibility probes. */
public record SingularityBillboardBasis(Vec3 right, Vec3 up, Vec3 normal) {
    private static final Vec3 WORLD_UP = new Vec3(0, 1, 0);

    public static SingularityBillboardBasis between(Vec3 source, Vec3 camera) {
        Vec3 delta = camera.subtract(source);
        Vec3 normal = delta.lengthSqr() > 1e-12 ? delta.normalize() : new Vec3(0, 0, 1);
        Vec3 reference = Math.abs(normal.y) > .9999 ? new Vec3(1, 0, 0) : WORLD_UP;
        Vec3 right = reference.cross(normal).normalize();
        Vec3 up = normal.cross(right).normalize();
        return new SingularityBillboardBasis(right, up, normal);
    }

    /** Rotation mapping sprite-local +X/+Y/+Z to this basis. */
    public Quaternionf rotation() {
        return new Quaternionf().setFromNormalized(new Matrix3f(
                (float) right.x, (float) right.y, (float) right.z,
                (float) up.x, (float) up.y, (float) up.z,
                (float) normal.x, (float) normal.y, (float) normal.z));
    }

    public Vector3f rightVector() { return new Vector3f((float) right.x, (float) right.y, (float) right.z); }
    public Vector3f upVector() { return new Vector3f((float) up.x, (float) up.y, (float) up.z); }
}
