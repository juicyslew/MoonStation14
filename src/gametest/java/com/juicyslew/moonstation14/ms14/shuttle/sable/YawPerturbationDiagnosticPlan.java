package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Quaterniondc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.UUID;

/** Test-only bounds and actual-driver premises for the native yaw feedback perturbation experiment. */
public final class YawPerturbationDiagnosticPlan {
    // Fixture budgets: 100 scene + 100 stationary + 600 lift + 200 hover + 480 settle + 1600 hold + 320 callback margin.
    public static final int REQUEST_DURATION = 2400;
    public static final int STEADY_TICKS_REQUIRED = 40;
    public static final int STEADY_WAIT_MAX_TICKS = 480;
    public static final int HOLD_TICKS = 1600;
    public static final int TEST_TIMEOUT_TICKS = 3400;
    public static final double TARGET_YAW = 4d;
    public static final double YAW_TOLERANCE = .01d;
    public static final double OFF_AXIS_TOLERANCE = .01d;
    public static final double LOCAL_ROLL_DELTA = .014d;
    public static final double GROSS_UP_TILT_DEGREES = 45d;
    private YawPerturbationDiagnosticPlan() { }

    public static int nextSteadyCount(int current, boolean qualifies) { return qualifies ? current + 1 : 0; }
    public static boolean stableYaw(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(y - TARGET_YAW) <= YAW_TOLERANCE
                && Math.abs(x) <= OFF_AXIS_TOLERANCE && Math.abs(z) <= OFF_AXIS_TOLERANCE;
    }
    public static boolean consecutiveWorldStep(long previousTick, long tick, int previousSubsteps, int substeps) {
        return tick == previousTick + 1 && substeps == previousSubsteps + 6;
    }
    public static Vector3d worldRollDelta(Quaterniondc bodyToWorld) {
        return normalizedOrientation(bodyToWorld).transform(new Vector3d(0, 0, LOCAL_ROLL_DELTA));
    }
    public static boolean achievedDelta(Vector3d before, Vector3d after, Vector3d requested) {
        if (before == null || after == null || requested == null) return false;
        Vector3d difference = new Vector3d(after).sub(before);
        return difference.distance(requested) <= 1e-5;
    }
    public static double upTiltDegrees(Quaterniondc orientation) {
        Vector3d up = worldUpUnit(orientation);
        double dot = Math.max(-1d, Math.min(1d, up.y));
        return Math.toDegrees(Math.acos(dot));
    }
    public static Vector3d worldUpUnit(Quaterniondc orientation) {
        return normalizedOrientation(orientation).transform(new Vector3d(0, 1, 0)).normalize();
    }
    public static boolean finiteQuaternion(Quaterniondc q) {
        try { normalizedOrientation(q); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private static Quaterniond normalizedOrientation(Quaterniondc q) {
        if (q == null) throw new IllegalArgumentException("missing quaternion");
        QuaternionAngularDistance.radians(q, new Quaterniond(0, 0, 0, 1));
        Quaterniond copy = new Quaterniond(q).normalize();
        if (!Double.isFinite(copy.x) || !Double.isFinite(copy.y) || !Double.isFinite(copy.z) || !Double.isFinite(copy.w))
            throw new IllegalArgumentException("invalid normalized quaternion");
        return copy;
    }
    public static final class FirstUnsafe {
        private boolean reported;
        public boolean first() { if (reported) return false; reported = true; return true; }
    }
    /** Book-safe GameTest assertion header; full witness data belongs only in the bounded logger. */
    public static String failureSummary(String phase, UUID frame, int sample, long worldTick, String details) {
        String detail = details == null ? "premise failed" : details.replace('\n', ' ').replace('\r', ' ');
        if (detail.length() > 96) detail = detail.substring(0, 96);
        String summary = "phase=" + phase + " frame=" + frame + " sample=" + sample + " worldTick=" + worldTick
                + " " + detail + "; see [yaw-roll-cause] phase=" + phase;
        if (summary.length() > 384) return summary.substring(0, 320) + "; see [yaw-roll-cause]";
        return summary;
    }
}
