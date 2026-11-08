package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;

/** Shared stable orthonormal spread basis for 3D ranged directions. */
final class RangedDirection {
    private RangedDirection() { }

    static Vec3 applySpread(Vec3 direction, double angleRadians) {
        if (direction == null || !finite(direction) || direction.lengthSqr() < 1.0e-12
                || !Double.isFinite(angleRadians)) throw new IllegalArgumentException("invalid ranged direction");
        Vec3 forward = direction.normalize();
        Vec3 helper = Math.abs(forward.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = helper.cross(forward).normalize();
        return forward.scale(Math.cos(angleRadians))
                .add(right.scale(Math.sin(angleRadians))).normalize();
    }

    /** Deterministic equal-solid-angle disk-cone sample; angleRadians is the maximum half-angle. */
    static Vec3 coneSample(Vec3 direction, double angleRadians, int index, int count) {
        if (direction == null || !finite(direction) || direction.lengthSqr() < 1.0e-12
                || !Double.isFinite(angleRadians) || angleRadians < 0 || angleRadians > Math.PI / 2
                || count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("invalid cone sample");
        Vec3 forward = direction.normalize();
        if (index == 0 || angleRadians == 0) return forward;
        Vec3 helper = Math.abs(forward.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = helper.cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();
        double cosTheta = 1 - (1 - Math.cos(angleRadians)) * index / (count - 1d);
        double sinTheta = Math.sqrt(Math.max(0, 1 - cosTheta * cosTheta));
        double phi = index * Math.PI * (3 - Math.sqrt(5));
        return forward.scale(cosTheta).add(right.scale(sinTheta * Math.cos(phi)))
                .add(up.scale(sinTheta * Math.sin(phi))).normalize();
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
