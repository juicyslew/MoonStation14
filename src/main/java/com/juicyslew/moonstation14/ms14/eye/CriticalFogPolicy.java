package com.juicyslew.moonstation14.ms14.eye;

/** Pure camera-fog projection for a Critical body; distance is bounded in camera space. */
public final class CriticalFogPolicy {
    private CriticalFogPolicy() { }

    public static float radius(double damage, double critical, double dead) {
        if (!Double.isFinite(damage) || !Double.isFinite(critical) || !Double.isFinite(dead)
                || dead <= critical || damage < critical) return Float.POSITIVE_INFINITY;
        double t = Math.max(0, Math.min(1, (damage - critical) / (dead - critical)));
        return (float) (16 - 14.5 * t);
    }

    /** Off-center camera adaptation: add its bounded body offset so the body remains visible. */
    public static float cameraFarPlane(float radius, double cameraBodyDistance) {
        if (!Float.isFinite(radius) || !Double.isFinite(cameraBodyDistance)) return Float.POSITIVE_INFINITY;
        double offset = Math.max(0, Math.min(12, cameraBodyDistance));
        return (float) Math.min(24, radius + offset);
    }

    /** Compose a valid near/far pair while preserving environmental fog that is already stricter. */
    public static FogPlanes combine(float existingNear, float existingFar, float criticalFar) {
        if (!Float.isFinite(existingNear) || !Float.isFinite(existingFar)
                || !Float.isFinite(criticalFar) || existingFar <= 0 || criticalFar <= 0) return null;
        if (criticalFar >= existingFar) return new FogPlanes(existingNear, existingFar);
        float far = Math.min(existingFar, criticalFar);
        float near = Math.min(existingNear, far * .75f);
        return new FogPlanes(near, far);
    }

    public record FogPlanes(float near, float far) { }
}
