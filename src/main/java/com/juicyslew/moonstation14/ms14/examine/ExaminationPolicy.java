package com.juicyslew.moonstation14.ms14.examine;

/** Shared SS14-derived eligibility math. World/LOS and resolved examiner data are supplied by the caller. */
public final class ExaminationPolicy {
    public static final double NORMAL_RANGE = 16.0;
    public static final double DETAILS_RANGE = 3.0;
    public static final double CRITICAL_RANGE = 1.3;
    public static final double DEAD_RANGE = 0.75;
    public static final double BLUR_MULTIPLIER = 2.5;

    private ExaminationPolicy() { }

    public record Examiner(boolean skipChecks, boolean checkInRangeUnOccluded) {
        public static final Examiner DEFAULT = new Examiner(false, true);
        public static final Examiner OBSERVER = new Examiner(true, true);
    }

    public enum State { ALIVE, CRITICAL, DEAD }

    public static double range(Examiner examiner, State state, boolean blind, double blurMagnitude) {
        if (examiner.skipChecks() || !examiner.checkInRangeUnOccluded()) return Double.POSITIVE_INFINITY;
        if (state == State.DEAD) return DEAD_RANGE;
        if (state == State.CRITICAL || blind) return CRITICAL_RANGE;
        if (Double.isFinite(blurMagnitude) && blurMagnitude > 0)
            return Math.clamp(NORMAL_RANGE - blurMagnitude * BLUR_MULTIPLIER, 2.0, NORMAL_RANGE);
        return NORMAL_RANGE;
    }

    public static boolean canExamine(Examiner examiner, State state, boolean blind, double blurMagnitude,
                                     double distance, boolean sameWorld, boolean lineOfSight, boolean attemptAllowed) {
        if (examiner.skipChecks()) return true;
        if (!attemptAllowed) return false;
        if (!examiner.checkInRangeUnOccluded()) return sameWorld;
        double range = range(examiner, state, blind, blurMagnitude);
        return sameWorld && distance >= 0 && distance <= range && lineOfSight;
    }

    public static boolean detailsAllowed(Examiner examiner, State state, boolean ghost,
                                         double distance, boolean sameWorld, boolean lineOfSight) {
        return ghost || (!examiner.skipChecks() && state == State.ALIVE && sameWorld
                && distance >= 0 && distance <= DETAILS_RANGE && lineOfSight);
    }
}
