package com.juicyslew.moonstation14.ms14.player_body_control.server;

/** Pure sequence and one-frame-per-tick gate for untrusted ghost intents. */
final class GhostIntentGate<T> {
    private long lastSequence;
    private long acceptedTick = Long.MIN_VALUE;
    private T pending;
    private boolean exhausted;

    boolean offer(long sequence, long tick, T frame) {
        if (exhausted) return false;
        if (sequence == Long.MAX_VALUE) {
            exhausted = true;
            return false;
        }
        if (sequence <= lastSequence || tick == acceptedTick || pending != null)
            return false;
        lastSequence = sequence;
        acceptedTick = tick;
        pending = frame;
        return true;
    }

    T take() {
        T frame = pending;
        pending = null;
        return frame;
    }

    boolean exhausted() { return exhausted; }
}
