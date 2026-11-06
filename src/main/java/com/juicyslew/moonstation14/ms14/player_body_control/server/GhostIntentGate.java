package com.juicyslew.moonstation14.ms14.player_body_control.server;

import java.util.ArrayDeque;
import java.util.Deque;

/** Pure monotonic-sequence gate for untrusted intents with legacy single-frame and bounded FIFO modes. */
public final class GhostIntentGate<T> {
    private long lastSequence;
    private long acceptedTick = Long.MIN_VALUE;
    private T pending;
    private final Deque<Queued<T>> queued = new ArrayDeque<>();
    private boolean exhausted;

    /** Legacy offer: accepts one pending frame and at most one accepted frame per tick. */
    public boolean offer(long sequence, long tick, T frame) {
        if (!sequenceAvailable(sequence)) return false;
        if (tick == acceptedTick || pending != null) return false;
        lastSequence = sequence;
        acceptedTick = tick;
        pending = frame;
        return true;
    }

    /** Accepts an ordered bounded command stream while retaining shared sequence/exhaustion checks. */
    public boolean offerQueued(long sequence, T frame, int capacity) {
        if (!sequenceAvailable(sequence)) return false;
        if (capacity <= 0) throw new IllegalArgumentException("queue capacity must be positive");
        if (queued.size() >= capacity) {
            exhausted = true;
            return false;
        }
        lastSequence = sequence;
        queued.addLast(new Queued<>(sequence, frame));
        return true;
    }

    public T takeQueued() {
        Queued<T> queuedFrame = queued.pollFirst();
        return queuedFrame == null ? null : queuedFrame.frame;
    }

    /** Discards queued real intents at an authority barrier and returns their highest sequence, if any. */
    public long discardQueuedThroughSequence() {
        Queued<T> last = queued.peekLast();
        long sequence = last == null ? -1 : last.sequence;
        queued.clear();
        return sequence;
    }

    public int queuedSize() { return queued.size(); }

    private boolean sequenceAvailable(long sequence) {
        if (exhausted) return false;
        if (sequence == Long.MAX_VALUE) {
            exhausted = true;
            return false;
        }
        return sequence > lastSequence;
    }

    public T take() {
        T frame = pending;
        pending = null;
        return frame;
    }

    public boolean exhausted() { return exhausted; }

    public void reset() {
        lastSequence = 0;
        acceptedTick = Long.MIN_VALUE;
        pending = null;
        queued.clear();
        exhausted = false;
    }

    private record Queued<T>(long sequence, T frame) { }
}
