package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;

import java.util.OptionalInt;

/** Local cadence bridge for short status UPDATEs backed by a continuing reagent reservoir. */
public final class StatusDurationCadence {
    public static final int MINIMUM_TICKS = 21;

    private StatusDurationCadence() { }

    public static OptionalInt floor(OptionalInt duration, boolean ongoingSource, EffectCause cause,
                                    boolean hasReagentContext, boolean finite, int delayTicks,
                                    StatusEffectOperation operation) {
        if (duration == null || cause == null || operation == null) throw new NullPointerException();
        if (!duration.isPresent() || duration.getAsInt() <= 0 || duration.getAsInt() >= MINIMUM_TICKS
                || !ongoingSource || cause != EffectCause.METABOLISM || !hasReagentContext || !finite
                || delayTicks != 0 || operation != StatusEffectOperation.UPDATE) return duration;
        return OptionalInt.of(MINIMUM_TICKS);
    }
}
