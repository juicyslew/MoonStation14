package com.juicyslew.moonstation14.ms14.radiation;

/** Bounded one-second cadence clock. Lag catches up one interval per call; clock rollback resets without dosing. */
final class RadiationIntervalClock {
    static final int INTERVAL_TICKS = 20;
    private long processedAt;
    private boolean initialized;

    int advance(long now) {
        if (!initialized) { initialized = true; processedAt = now; return 0; }
        if (now < processedAt) { processedAt = now; return 0; }
        long pending = now - processedAt;
        if (pending < INTERVAL_TICKS) return 0;
        int elapsed = (int)Math.min(INTERVAL_TICKS, pending);
        processedAt += elapsed;
        return elapsed;
    }
}

