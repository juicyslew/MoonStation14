package com.juicyslew.moonstation14.ms14.player_body_control.client;

/** Session-local simulation-step clock. Network sequence numbers identify inputs, not time. */
public final class ClientOwnedSimulationClock {
    private long epoch;
    private long tick;
    private long lastSequence;
    private boolean anchored;

    public void reset() { epoch = tick = lastSequence = 0; anchored = false; }

    /** Establishes the next interval after the completed authoritative Sable step. */
    public void restore(long ownerEpoch, long completedStep, long acknowledgedSequence) {
        if (ownerEpoch <= 0 || completedStep < 0 || acknowledgedSequence < 0)
            throw new IllegalArgumentException("invalid authoritative simulation clock");
        epoch = ownerEpoch;
        tick = completedStep;
        lastSequence = acknowledgedSequence;
        anchored = true;
    }

    /** Advances exactly one physical interval for a newer intent; gaps do not change elapsed steps. */
    public Interval advance(long ownerEpoch, long sequence) {
        Interval interval = prepare(ownerEpoch, sequence);
        if (interval != null) commit(interval, ownerEpoch, sequence);
        return interval;
    }

    public Interval prepare(long ownerEpoch, long sequence) {
        return !anchored || ownerEpoch != epoch || sequence <= lastSequence || tick == Long.MAX_VALUE
                ? null : new Interval(tick, tick + 1);
    }

    public boolean commit(Interval interval, long ownerEpoch, long sequence) {
        if (!anchored || interval == null || ownerEpoch != epoch || sequence <= lastSequence
                || interval.from() != tick || interval.to() != tick + 1) return false;
        tick = interval.to(); lastSequence = sequence; return true;
    }

    public boolean anchored() { return anchored; }
    public long tick() { return tick; }
    public long lastSequence() { return lastSequence; }
    public record Interval(long from, long to) { }
}
