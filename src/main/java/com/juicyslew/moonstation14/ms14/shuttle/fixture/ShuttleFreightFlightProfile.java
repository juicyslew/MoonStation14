package com.juicyslew.moonstation14.ms14.shuttle.fixture;

/** Deterministic, bounded low-thrust-feel diagnostic trajectory; deliberately not an SS14 thruster model. */
public final class ShuttleFreightFlightProfile {
    public static final double LINEAR_ACCELERATION_LIMIT = .35;
    public static final double ANGULAR_ACCELERATION_LIMIT = .02;
    public static final double LINEAR_JERK_LIMIT = .12;
    public static final double ANGULAR_JERK_LIMIT = .01;
    public static final ShuttleMotionMath.ControlLimits CONTROL_LIMITS = new ShuttleMotionMath.ControlLimits(
            LINEAR_ACCELERATION_LIMIT,ANGULAR_ACCELERATION_LIMIT,LINEAR_JERK_LIMIT,ANGULAR_JERK_LIMIT);
    private ShuttleFreightFlightProfile() { }

    /** World velocity (m/s) and world-Y yaw rate (rad/s), evaluated at elapsed native simulation time. */
    public static ShuttleMotionRequest target(double seconds, int durationTicks) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("invalid flight time");
        // Bounded harmonic sweeps: <15m horizontal, <2.1m vertical excursion at full 120s duration.
        double a = Math.PI * 2 / 42.0, b = Math.PI * 2 / 31.0, c = Math.PI * 2 / 23.0;
        double fade = smooth(seconds / 4.0) * smooth((durationTicks / 20.0 - seconds) / 4.0);
        double vx = fade * (.78 * Math.cos(a * seconds) + .30 * Math.cos(b * seconds + .8));
        double vz = fade * (.60 * Math.cos(a * seconds + 1.1) - .25 * Math.cos(c * seconds));
        double vy = fade * (.22 * Math.cos(c * seconds + .4) + .10 * Math.cos(b * seconds));
        double yaw = fade * (.061 * Math.cos(a * seconds + .3) + .019 * Math.cos(c * seconds));
        return new ShuttleMotionRequest(vx, vy, vz, yaw, durationTicks);
    }

    private static double smooth(double x) {
        double t = Math.max(0, Math.min(1, x));
        return t * t * t * (10 + t * (-15 + 6 * t));
    }
}
