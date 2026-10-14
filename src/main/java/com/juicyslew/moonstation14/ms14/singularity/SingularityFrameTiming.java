package com.juicyslew.moonstation14.ms14.singularity;

/** Pure deterministic frame selection for an authored RSI delay sequence. */
public final class SingularityFrameTiming {
    private SingularityFrameTiming() { }

    /** Selects a frame at elapsed seconds; elapsed time may include the render partial tick. */
    public static int frameAt(double elapsedSeconds, double[] delaysSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0 || delaysSeconds == null || delaysSeconds.length == 0)
            throw new IllegalArgumentException("invalid animation time or delays");
        double cycle = 0;
        for (double delay : delaysSeconds) {
            if (!Double.isFinite(delay) || delay <= 0) throw new IllegalArgumentException("frame delays must be positive");
            cycle += delay;
        }
        double phase = elapsedSeconds % cycle;
        // Decimal authored delays (e.g. sixteen 0.1 s frames) can sum to 1.6000000000000003.
        // Normalize only representation-scale error at the cycle edge.
        if (cycle - phase <= 2 * Math.ulp(Math.max(elapsedSeconds, cycle))) phase = 0;
        double boundary = 0;
        double tolerance = 2 * Math.ulp(Math.max(elapsedSeconds, cycle));
        for (int i = 0; i < delaysSeconds.length; i++) {
            boundary += delaysSeconds[i];
            if (i == delaysSeconds.length - 1 || phase < boundary - tolerance) return i;
        }
        return 0;
    }
}
