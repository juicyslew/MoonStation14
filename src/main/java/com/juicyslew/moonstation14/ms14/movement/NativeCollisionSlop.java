package com.juicyslew.moonstation14.ms14.movement;

/** Bounded double-adapter for the stock native collision solver's f32-scale residuals. */
public final class NativeCollisionSlop {
    public static final double MAX_RESIDUAL = 1.0e-6;

    private NativeCollisionSlop() { }

    /** Normalize only near-zero/opposite-sign or near-endpoint residue; leave real collision/step results intact. */
    public static double acceptedAxis(double requested, double actual) {
        if (!Double.isFinite(requested) || !Double.isFinite(actual)) return actual;
        if (Math.abs(actual) <= MAX_RESIDUAL && (requested == 0d
                || Math.signum(requested) != Math.signum(actual))) return 0d;
        if (Math.abs(actual) > Math.abs(requested)
                && Math.abs(actual) - Math.abs(requested) <= MAX_RESIDUAL
                && Math.signum(actual) == Math.signum(requested)) return requested;
        return actual;
    }

    public static MovementVector accepted(MovementVector requested, MovementVector actual) {
        return new MovementVector(acceptedAxis(requested.x(), actual.x()),
                acceptedAxis(requested.y(), actual.y()), acceptedAxis(requested.z(), actual.z()));
    }
}
