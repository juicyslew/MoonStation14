package com.juicyslew.moonstation14.ms14.player_body_control.client;

/** Replays the server's remaining flight steps independent of packet arrival cadence. */
final class ExternalFlightPredictionPolicy {
    private int remaining;

    void reset(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("flight ticks must be nonnegative");
        remaining = ticks;
    }

    boolean advanceFrame() {
        boolean flying = remaining > 1;
        if (remaining > 0) remaining--;
        return flying;
    }

    void clear() { remaining = 0; }
}
