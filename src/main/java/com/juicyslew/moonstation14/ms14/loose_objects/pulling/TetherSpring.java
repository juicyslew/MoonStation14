package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import net.minecraft.world.phys.Vec3;

/** Deterministic tension-only 3D spring-damper; returns acceleration on the first endpoint. */
public final class TetherSpring {
    private TetherSpring() { }
    public static Vec3 acceleration(Vec3 first, Vec3 firstVelocity, Vec3 second, Vec3 secondVelocity,
                                    double slack, double stiffness, double progressive, double damping,
                                    double maximum) {
        if (!finite(first) || !finite(firstVelocity) || !finite(second) || !finite(secondVelocity)
                || !Double.isFinite(slack) || slack < 0 || !Double.isFinite(stiffness) || stiffness < 0
                || !Double.isFinite(progressive) || progressive < 0 || !Double.isFinite(damping) || damping < 0
                || !Double.isFinite(maximum) || maximum <= 0) return Vec3.ZERO;
        Vec3 delta = second.subtract(first);
        double distance = delta.length();
        double extension = distance - slack;
        if (!Double.isFinite(distance) || extension <= 0 || distance < 1e-9) return Vec3.ZERO;
        Vec3 direction = delta.scale(1 / distance);
        double outwardSpeed = secondVelocity.subtract(firstVelocity).dot(direction);
        double magnitude = stiffness * extension + progressive * extension * extension
                + damping * outwardSpeed;
        magnitude = Math.min(maximum, Math.max(0, magnitude));
        return direction.scale(magnitude);
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}

