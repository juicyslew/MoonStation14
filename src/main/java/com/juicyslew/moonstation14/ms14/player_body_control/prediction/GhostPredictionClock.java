package com.juicyslew.moonstation14.ms14.player_body_control.prediction;

import com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads;

/** Monotonic-time prediction clock; independent of callback and packet counts. */
public final class GhostPredictionClock {
    public static final long NANOS_PER_TICK = 50_000_000L;
    private long anchorTick = -1, anchorNanos;
    public boolean anchor(long authoritativeTick, long nowNanos) {
        if (authoritativeTick < 0 || anchorTick >= 0 && authoritativeTick <= anchorTick) return false;
        anchorTick = authoritativeTick; anchorNanos = nowNanos; return true;
    }
    public long targetTick(long nowNanos, int leadTicks) {
        if (anchorTick < 0) return -1;
        long difference = nowNanos - anchorNanos;
        long elapsed = difference < 0 ? 0 : difference;
        long elapsedTicks = elapsed / NANOS_PER_TICK;
        long horizonCap = GhostControlPayloads.MAX_PENDING_INTENTS;
        long lead = Math.max(0, Math.min(horizonCap, leadTicks));
        long horizon = Math.min(horizonCap, elapsedTicks > horizonCap ? horizonCap : elapsedTicks + lead);
        return anchorTick > Long.MAX_VALUE - horizon ? Long.MAX_VALUE : anchorTick + horizon;
    }
    public void reset() { anchorTick = -1; anchorNanos = 0; }
    public long anchorTick() { return anchorTick; }
}
