package com.juicyslew.moonstation14.ms14.mob.mouse.client;

/** Immediate presentation policy; authored clip definitions and physics are untouched. */
final class MouseLocomotionPresentation {
    private static final double MIN_HORIZONTAL_DELTA_SQUARED = 1.0E-6;

    private MouseLocomotionPresentation() {}

    static String clip(double dx, double dz, boolean dead, boolean critical) {
        if (dead || critical) return "down_hold";
        return dx * dx + dz * dz > MIN_HORIZONTAL_DELTA_SQUARED ? "run" : "idle_breathe";
    }

}
