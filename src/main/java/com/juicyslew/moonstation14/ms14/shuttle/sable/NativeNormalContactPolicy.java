package com.juicyslew.moonstation14.ms14.shuttle.sable;

import net.minecraft.world.phys.Vec3;

/** Prescribed-surface normal response; all velocity arguments are absolute m/s. */
public final class NativeNormalContactPolicy {
    private NativeNormalContactPolicy() { }

    /** Immutable world-space m/s prescription, captured from the pre-response velocity. */
    public record Target(Vec3 normal, Vec3 surfaceVelocity, double outgoingNormalSpeed) {
        public boolean valid() {
            return finite(normal) && finite(surfaceVelocity) && Double.isFinite(outgoingNormalSpeed)
                    && normal.length() >= 1.0e-9;
        }
    }

    /** Capture one absolute outgoing normal speed; invalid inputs produce an unusable target. */
    public static Target prescribe(Vec3 incoming, Vec3 surfaceVelocity, Vec3 normal, double restitution) {
        if (!finite(incoming) || !finite(surfaceVelocity) || !finite(normal) || !Double.isFinite(restitution)
                || normal.length() < 1.0e-9) return new Target(null, null, Double.NaN);
        Vec3 n = normal.normalize();
        double relativeNormal = incoming.subtract(surfaceVelocity).dot(n);
        double outgoing = relativeNormal >= 0 ? relativeNormal : -Math.max(0, restitution) * relativeNormal;
        return new Target(n, surfaceVelocity, surfaceVelocity.dot(n) + outgoing);
    }

    /** Replace only the prescribed normal on any post-motor absolute-velocity baseline. */
    public static Vec3 replaceNormal(Vec3 baseline, Target target) {
        if (!finite(baseline) || target == null || !target.valid()) return baseline;
        Vec3 n = target.normal().normalize();
        return baseline.add(n.scale(target.outgoingNormalSpeed() - baseline.dot(n)));
    }

    public static Vec3 replaceNormal(Vec3 incoming, Vec3 baseline, Vec3 surface, Vec3 normal, double restitution) {
        return replaceNormal(baseline, prescribe(incoming, surface, normal, restitution));
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
