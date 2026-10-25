package com.juicyslew.moonstation14.ms14.player_body_control.server;

/**
 * Bounded ordered admission of full control-state updates. A single authoritative-tick call drains the available
 * updates as one batch; the batch is not a list of movement steps. The owner folds state transitions and performs
 * at most its normal physics step for that world tick.
 */
public final class GhostIntentGate<T> {
    public static final int CAPACITY = com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.MAX_PENDING_INTENTS;
    private long lastSequence;
    private long consumedTick = Long.MIN_VALUE;
    private final java.util.ArrayDeque<T> pending = new java.util.ArrayDeque<>(CAPACITY);
    private boolean exhausted;

    /**
     * Accepts increasing sequence numbers, not necessarily contiguous ones. Overflow rejects without advancing
     * the accepted sequence; skipped sequence gaps can be settled by a later applied acknowledgement.
     */
    public boolean offer(long sequence, T frame) {
        if (exhausted) return false;
        if (sequence == Long.MAX_VALUE) {
            exhausted = true;
            return false;
        }
        if (sequence <= lastSequence || pending.size() >= CAPACITY)
            return false;
        lastSequence = sequence;
        pending.addLast(frame);
        return true;
    }

    public java.util.List<T> takeBatch(long authoritativeGameTick) {
        if (authoritativeGameTick <= consumedTick) return java.util.List.of();
        consumedTick = authoritativeGameTick;
        if (pending.isEmpty()) return java.util.List.of();
        java.util.ArrayList<T> batch = new java.util.ArrayList<>(pending.size());
        while (!pending.isEmpty()) batch.add(pending.removeFirst());
        return java.util.List.copyOf(batch);
    }

    public boolean exhausted() { return exhausted; }

    public void reset() {
        lastSequence = 0;
        consumedTick = Long.MIN_VALUE;
        pending.clear();
        exhausted = false;
    }
}
