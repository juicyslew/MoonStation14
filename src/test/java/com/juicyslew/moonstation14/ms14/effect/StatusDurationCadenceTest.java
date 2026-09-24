package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusDurationCadenceTest {
    @Test
    void floorsOnlyFiniteImmediateMetabolismUpdatesWithOngoingSource() {
        assertEquals(OptionalInt.of(21), floor(10, true, EffectCause.METABOLISM, true, true, 0,
                StatusEffectOperation.UPDATE));
        assertEquals(OptionalInt.of(40), floor(40, true, EffectCause.METABOLISM, true, true, 0,
                StatusEffectOperation.UPDATE));
        assertEquals(OptionalInt.of(10), floor(10, false, EffectCause.METABOLISM, true, true, 0,
                StatusEffectOperation.UPDATE));
        assertEquals(OptionalInt.of(10), floor(10, true, EffectCause.METABOLISM, true, true, 1,
                StatusEffectOperation.UPDATE));
        assertEquals(OptionalInt.empty(), StatusDurationCadence.floor(OptionalInt.empty(), true,
                EffectCause.METABOLISM, true, false, 0, StatusEffectOperation.UPDATE));
        assertEquals(OptionalInt.of(10), floor(10, true, EffectCause.METABOLISM, true, true, 0,
                StatusEffectOperation.REMOVE));
        assertEquals(OptionalInt.of(10), floor(10, true, EffectCause.MANUAL, false, true, 0,
                StatusEffectOperation.UPDATE));
    }

    @Test
    void preservesZeroAndScalingFalseAdmittedScale() {
        assertEquals(OptionalInt.of(10), floor(10, false, EffectCause.METABOLISM, true, true, 0,
                StatusEffectOperation.UPDATE));
        assertEquals(OptionalInt.of(0), floor(0, true, EffectCause.METABOLISM, true, true, 0,
                StatusEffectOperation.UPDATE));
    }

    private static OptionalInt floor(int ticks, boolean source, EffectCause cause, boolean context,
                                     boolean finite, int delay, StatusEffectOperation operation) {
        return StatusDurationCadence.floor(OptionalInt.of(ticks), source, cause, context, finite, delay, operation);
    }
}
