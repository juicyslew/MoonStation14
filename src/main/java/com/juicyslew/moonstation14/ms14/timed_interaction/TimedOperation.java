package com.juicyslew.moonstation14.ms14.timed_interaction;

import java.util.Objects;

/** Immutable actor-owned operation. Ticks are monotonic server ticks. */
public record TimedOperation(long id, String kind, String sourceKey, long startTick, int durationTicks,
                             Phase phase, long terminalTick, boolean breakOnDamage, boolean breakOnMove) {
    public enum Phase { ACTIVE, COMPLETED, CANCELLED }
    public TimedOperation {
        if (id < 1 || startTick < 0 || durationTicks < 1) throw new IllegalArgumentException("invalid operation timing/id");
        Objects.requireNonNull(kind); Objects.requireNonNull(sourceKey); Objects.requireNonNull(phase);
        if (phase == Phase.ACTIVE ? terminalTick != -1 : terminalTick < startTick) throw new IllegalArgumentException("invalid terminal tick");
    }
    public long dueTick() { return Math.addExact(startTick, durationTicks); }
}
