package com.juicyslew.moonstation14.ms14.player_body_control.diagnostics;

/** Pure frame marker and success-only per-frame camera sample gate. */
public final class MovementTraceFrameSampler {
    public record Frame(long id, long startNanos, long intervalNanos, boolean intervalKnown) { }
    private long id;
    private long previousStart;
    private Frame current;
    private boolean sampled;

    public void reset() { id = 0; previousStart = 0; current = null; sampled = false; }

    public Frame onFrame(long startNanos) {
        long interval = previousStart == 0 ? 0 : Math.max(0, startNanos - previousStart);
        current = new Frame(++id, startNanos, interval, previousStart != 0);
        previousStart = startNanos;
        sampled = false;
        return current;
    }

    public Frame current() { return current; }
    public boolean canSampleOwnedBody() { return current != null && !sampled; }
    public void sampleRecorded(boolean success) { if (success && current != null) sampled = true; }
}
