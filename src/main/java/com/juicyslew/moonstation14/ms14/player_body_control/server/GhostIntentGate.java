package com.juicyslew.moonstation14.ms14.player_body_control.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded ordered admission of full control-state updates. Draining is one batch, not multiple physics steps. */
public final class GhostIntentGate<T> {
    public static final int CAPACITY = com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.MAX_PENDING_INTENTS;
    private long lastSequence;
    private long consumedTick = Long.MIN_VALUE;
    private final Deque<Queued<T>> pending = new ArrayDeque<>(CAPACITY);
    private long acknowledgedAppliedSequence;
    private long settledThroughSequence;
    private boolean exhausted;

    /** Compatibility form for callers without intended-tick metadata. */
    public boolean offer(long sequence, T frame) { return offer(sequence, 0, frame); }

    /** Accepts increasing sequence numbers and stores intended simulation time with each real frame. */
    public boolean offer(long sequence, long intendedSimulationTick, T frame) {
        if (intendedSimulationTick < 0) throw new IllegalArgumentException("intended simulation tick must be nonnegative");
        if (exhausted || sequence <= lastSequence || sequence == Long.MAX_VALUE) {
            if (sequence == Long.MAX_VALUE) exhausted = true;
            return false;
        }
        if (pending.size() >= CAPACITY) return false;
        lastSequence = sequence;
        pending.addLast(new Queued<>(sequence, intendedSimulationTick, frame));
        return true;
    }

    /** Admits using caller-provided capacity while retaining sequence and exhaustion checks. */
    public boolean offerQueued(long sequence, T frame, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("queue capacity must be positive");
        if (exhausted || sequence <= lastSequence || sequence == Long.MAX_VALUE) {
            if (sequence == Long.MAX_VALUE) exhausted = true;
            return false;
        }
        if (pending.size() >= Math.min(capacity, CAPACITY)) return false;
        lastSequence = sequence;
        pending.addLast(new Queued<>(sequence, 0, frame));
        return true;
    }

    /** Drains FIFO frames once per strictly increasing authoritative tick. */
    public List<T> takeBatch(long authoritativeGameTick) {
        if (authoritativeGameTick <= consumedTick) return List.of();
        consumedTick = authoritativeGameTick;
        if (pending.isEmpty()) return List.of();
        ArrayList<T> frames = new ArrayList<>(pending.size());
        Queued<T> item;
        while ((item = pending.pollFirst()) != null) {
            frames.add(item.frame);
        }
        return List.copyOf(frames);
    }

    public T takeQueued() {
        Queued<T> item = pending.pollFirst();
        if (item == null) return null;
        // Dequeue is admission only. The physical caller must explicitly settle it.
        return item.frame;
    }

    /** Records a sequence whose physical application completed (including a known partial move). */
    public void markApplied(long sequence) {
        if (sequence <= acknowledgedAppliedSequence) return;
        acknowledgedAppliedSequence = sequence;
        settledThroughSequence = Math.max(settledThroughSequence, sequence);
    }

    /** Permanently settles a rejected/skipped sequence without claiming physical application. */
    public void settleSkippedThroughSequence(long sequence) {
        if (sequence > settledThroughSequence) settledThroughSequence = sequence;
    }

    /** Permanently drops pending real frames; settlement advances, applied acknowledgement does not. */
    public long discardQueuedThroughSequence() {
        Queued<T> last = pending.peekLast();
        if (last == null) return -1;
        pending.clear();
        settledThroughSequence = Math.max(settledThroughSequence, last.sequence);
        return last.sequence;
    }

    public int queuedSize() { return pending.size(); }
    public long acknowledgedAppliedSequence() { return acknowledgedAppliedSequence; }
    public long settledThroughSequence() { return settledThroughSequence; }
    public boolean exhausted() { return exhausted; }

    public void reset() {
        lastSequence = 0;
        consumedTick = Long.MIN_VALUE;
        pending.clear();
        acknowledgedAppliedSequence = 0;
        settledThroughSequence = 0;
        exhausted = false;
    }

    private record Queued<T>(long sequence, long intendedSimulationTick, T frame) { }
}
