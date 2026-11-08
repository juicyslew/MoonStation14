package com.juicyslew.moonstation14.ms14.standing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Shared nearest-point/reach rule for client nomination and authoritative target validation. */
public final class BodyInteractionRay {
    public record HeadLocalBasis(Vec3 forward, Vec3 right, Vec3 up) { }
    private BodyInteractionRay() { }

    public static Optional<Vec3> hit(AABB bounds, Vec3 start, Vec3 end, double reach, double blockerDistance) {
        if (bounds == null || start == null || end == null || reach < 0 || blockerDistance < 0) return Optional.empty();
        Optional<Vec3> point = bounds.contains(start) ? Optional.of(start) : bounds.clip(start, end);
        return point.filter(value -> start.distanceTo(value) + 1.0e-7 < reach
                && start.distanceTo(value) < blockerDistance);
    }

    /** Position a wide-strike origin at a normalized height in the body's bounds. */
    public static Vec3 bodyHeightOrigin(AABB bounds, double heightRatio) {
        if (bounds == null || !Double.isFinite(heightRatio) || heightRatio < 0 || heightRatio > 1
                || !Double.isFinite(bounds.minX) || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.maxY)
                || !Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxZ)) return null;
        return new Vec3((bounds.minX + bounds.maxX) * 0.5,
                bounds.minY + bounds.getYsize() * heightRatio, (bounds.minZ + bounds.maxZ) * 0.5);
    }

    /** Yaw-steered direction preserving the sampled look's vertical component. */
    public static Vec3 pitchedYawDirection(Vec3 look, double yaw) {
        if (look == null || !Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z)
                || look.lengthSqr() == 0 || !Double.isFinite(yaw)) return Vec3.ZERO;
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        return new Vec3(-Math.sin(yaw) * horizontal, look.y, Math.cos(yaw) * horizontal).normalize();
    }

    /** Stable head-local frame. Yaw is in degrees; fan angles are in radians. */
    public static HeadLocalBasis headLocalBasis(Vec3 look, double yaw) {
        if (look == null || !Double.isFinite(yaw) || !Double.isFinite(look.x) || !Double.isFinite(look.y)
                || !Double.isFinite(look.z) || look.lengthSqr() == 0) return null;
        Vec3 forward = look.normalize();
        Vec3 right = new Vec3(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw)));
        right = right.subtract(forward.scale(right.dot(forward)));
        if (right.lengthSqr() < 1.0e-12) {
            // At pathological inputs, choose a world axis least parallel to forward.
            Vec3 fallback = Math.abs(forward.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            right = fallback.subtract(forward.scale(fallback.dot(forward)));
        }
        right = right.normalize();
        Vec3 up = forward.cross(right).normalize();
        return new HeadLocalBasis(forward, right, up);
    }

    /** Yaw is in degrees; angle is in radians. */
    public static Vec3 headLocalFanDirection(Vec3 look, double yaw, double angle) {
        HeadLocalBasis basis = headLocalBasis(look, yaw);
        if (basis == null || !Double.isFinite(angle)) return Vec3.ZERO;
        return basis.forward().scale(Math.cos(angle)).add(basis.right().scale(Math.sin(angle))).normalize();
    }
}
