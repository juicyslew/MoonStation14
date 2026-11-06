package com.juicyslew.moonstation14.ms14.shuttle.sable;

/** Bounded experiment command schedule; driver indices are model/world observations, not SDK ticks. */
public final class RotationCauseDiagnosticPlan {
    public static final int TOTAL_TICKS = 1160;
    public static final int REQUEST_DURATION = 2400;
    public record Phase(String label, double verticalY, int yaw) { }
    public enum SetupStage { LIFT, HOVER, READY }
    private RotationCauseDiagnosticPlan() { }
    public static Phase phaseAt(int tick) {
        if (tick < 0 || tick >= TOTAL_TICKS) throw new IllegalArgumentException("outside diagnostic protocol: " + tick);
        if (tick < 360) return new Phase("spinup", 0, 4);
        if (tick < 460) return new Phase("steady", 0, 4);
        if (tick < 560) return new Phase("cross-positive", .5, 4);
        if (tick < 660) return new Phase("cross-neutral", 0, 4);
        if (tick < 760) return new Phase("cross-negative", -.5, 4);
        return new Phase("brake", 0, 0);
    }
    public static boolean retargetAt(int tick) {
        return tick == 0 || tick == 460 || tick == 560 || tick == 660 || tick == 760;
    }
    public static SetupStage setupStage(boolean heightReady, boolean hoverSettled) {
        if (!heightReady) return SetupStage.LIFT;
        return hoverSettled ? SetupStage.READY : SetupStage.HOVER;
    }
    public static boolean consecutiveWorldClock(long previous, long current) { return current == previous + 1; }
    public static boolean sixAppliedSteps(int previous, int current, boolean retarget) {
        return retarget || current - previous == 6;
    }
}
