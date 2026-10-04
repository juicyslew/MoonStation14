package com.juicyslew.moonstation14.ms14.loose_objects.pulling;

import net.minecraft.world.phys.Vec3;

/** Bounded velocity-level spring constraint used instead of teleporting a loose object to a hand. */
public final class PullConstraint {
    public static final double STIFFNESS = 0.0; // Bounded velocity correction; no native hard joint is available.
    public static final double MAX_IMPULSE = 0.18;
    public static final double MAX_SPEED = 0.45;

    private PullConstraint() { }

    /** Returns an impulse (not a destination); callers must apply it through the entity's physics path. */
    public static Vec3 impulse(Vec3 target, Vec3 current, Vec3 velocity, double maximumDistance) {
        if (!finite(target) || !finite(current) || !finite(velocity)
                || !Double.isFinite(maximumDistance) || maximumDistance <= 0) return Vec3.ZERO;
        Vec3 error = target.subtract(current);
        double distance = error.length();
        if (!Double.isFinite(distance) || distance <= maximumDistance || distance < 1.0e-6) return Vec3.ZERO;
        // Rope slack is exactly the captured radius. Beyond it, correct radial velocity only,
        // combining excess-distance correction with cancellation of outward relative motion.
        Vec3 inward = error.scale(1.0 / distance);
        double outwardSpeed = velocity.dot(inward);
        double desiredInwardSpeed = Math.min(MAX_SPEED, (distance - maximumDistance) * 0.22);
        double radialDelta = (desiredInwardSpeed - outwardSpeed) * 0.35;
        Vec3 delta = inward.scale(radialDelta);
        double magnitude = delta.length();
        return magnitude > MAX_IMPULSE ? delta.scale(MAX_IMPULSE / magnitude) : delta;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
