package com.juicyslew.moonstation14.ms14.player_body_control.server;

/** Pure sequence and one-frame-per-tick gate for untrusted ghost intents. */
final class GhostIntentGate<T> {
    private long lastSequence;
    private long acceptedTick = Long.MIN_VALUE;
    private T pending;
    private boolean exhausted;

    /**
     * Accepts increasing sequence numbers, not necessarily contiguous ones. A same-tick intent rejected by the
     * one-frame limit is permanently skipped; once a higher sequence is accepted it cannot be accepted later.
     * This lets acknowledgements settle all sequences through the last applied one without requiring recovery of
     * dropped or skipped packets.
     */
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

    void reset() {
        lastSequence = 0;
        acceptedTick = Long.MIN_VALUE;
        pending = null;
        exhausted = false;
    }
}
