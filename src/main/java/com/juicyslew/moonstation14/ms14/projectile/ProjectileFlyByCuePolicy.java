package com.juicyslew.moonstation14.ms14.projectile;

/** Pure enter-edge/probability policy used by the client manager and unit tests. */
public final class ProjectileFlyByCuePolicy {
    private ProjectileFlyByCuePolicy() { }

    public static boolean shouldCue(boolean wasInside, boolean intersectsSweep, float probability, float draw) {
        return shouldCue(wasInside, intersectsSweep, false, probability, draw);
    }

    public static boolean shouldCue(boolean wasInside, boolean intersectsSweep, boolean isShooter,
                                    float probability, float draw) {
        if (wasInside || !intersectsSweep || isShooter || !Float.isFinite(probability) || probability <= 0
                || probability > 1 || !Float.isFinite(draw) || draw < 0 || draw >= 1) return false;
        return draw < probability;
    }
}
