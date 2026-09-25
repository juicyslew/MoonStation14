package com.juicyslew.moonstation14.ms14.movement.server;

/** Small, allocation-free-per-sample diagnostic accumulator for custom-owned movement. */
final class MovementSurfaceTrace {
    static final long SUMMARY_INTERVAL_TICKS = 100L;

    private long windowStartTick = Long.MIN_VALUE;
    private long samples;
    private long slidingNeutralTicks;
    private long slidingLubeTicks;
    private long projectedWishBlockedTicks;
    private long lubeToNeutralTransitions;
    private double minimumFactor = Double.POSITIVE_INFINITY;
    private double maximumFactor = Double.NEGATIVE_INFINITY;
    private double maximumHorizontalSpeed;
    private boolean previousLube;

    /** Record one custom-owned server tick; wish projection is diagnostic and pre-friction only. */
    Summary sample(long gameTick, boolean sliding, boolean onGround, double surfaceFactor,
                   double horizontalSpeed, double wishSpeed, double preFrictionWishProjection) {
        if (windowStartTick == Long.MIN_VALUE || gameTick < windowStartTick) {
            reset();
            windowStartTick = gameTick;
        }

        samples++;
        minimumFactor = Math.min(minimumFactor, surfaceFactor);
        maximumFactor = Math.max(maximumFactor, surfaceFactor);
        maximumHorizontalSpeed = Math.max(maximumHorizontalSpeed, horizontalSpeed);
        boolean lube = surfaceFactor < 1d;
        if (sliding && surfaceFactor == 1d) slidingNeutralTicks++;
        if (sliding && lube) slidingLubeTicks++;
        if (previousLube && surfaceFactor == 1d) lubeToNeutralTransitions++;
        previousLube = lube;
        if (onGround && wishSpeed > 0d && preFrictionWishProjection >= wishSpeed)
            projectedWishBlockedTicks++;

        if (gameTick - windowStartTick < SUMMARY_INTERVAL_TICKS) return null;
        Summary summary = new Summary(samples, minimumFactor, maximumFactor, slidingNeutralTicks,
                slidingLubeTicks, projectedWishBlockedTicks, lubeToNeutralTransitions, maximumHorizontalSpeed);
        reset();
        windowStartTick = gameTick;
        return summary;
    }

    void reset() {
        windowStartTick = Long.MIN_VALUE;
        samples = 0;
        slidingNeutralTicks = 0;
        slidingLubeTicks = 0;
        projectedWishBlockedTicks = 0;
        lubeToNeutralTransitions = 0;
        minimumFactor = Double.POSITIVE_INFINITY;
        maximumFactor = Double.NEGATIVE_INFINITY;
        maximumHorizontalSpeed = 0d;
        previousLube = false;
    }

    record Summary(long samples, double minimumFactor, double maximumFactor, long slidingNeutralTicks,
                   long slidingLubeTicks, long projectedWishBlockedTicks, long lubeToNeutralTransitions,
                   double maximumHorizontalSpeed) { }
}
