package com.juicyslew.moonstation14.ms14.medical;

import com.juicyslew.moonstation14.ms14.medical.syringe.SyringeTiming;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyringeTimingTest {
    private final InjectorModeData mode = new InjectorModeData(InjectorModeData.Kind.INJECT,
            List.of(5f, 10f, 15f), 5f, 5f, 0f, .1f, .5f, .5f);

    @Test void extraTimeUsesActuallyAdmittedDoseAndThenSelfModifier() {
        assertEquals(2.75, SyringeTiming.seconds(mode, 10, false, SyringeTiming.TargetPosture.SELF), 1e-7);
        assertEquals(55, SyringeTiming.ticks(mode, 10, false, SyringeTiming.TargetPosture.SELF));
        assertEquals(5.5, SyringeTiming.seconds(mode, 10, false, SyringeTiming.TargetPosture.STANDING), 1e-7);
    }

    @Test void selfModifierTakesPriorityOverDownedModifier() {
        assertEquals(2.5, SyringeTiming.seconds(mode, 5, false, SyringeTiming.TargetPosture.SELF), 1e-9);
        assertEquals(2.5, SyringeTiming.seconds(mode, 5, false, SyringeTiming.TargetPosture.DOWNED), 1e-9);
    }

    @Test void containerDrawUsesOnlyConfiguredContainerTime() {
        var draw = new InjectorModeData(InjectorModeData.Kind.DRAW, List.of(5f, 10f, 15f), 5f,
                5f, 1.25f, .1f, .5f, .5f);
        assertEquals(1.25, SyringeTiming.seconds(draw, 10, true, SyringeTiming.TargetPosture.STANDING), 1e-7);
    }

    @Test void containerInjectionIsImmediateAndDrawUsesOnlyConfiguredContainerTime() {
        for (double amount : new double[]{5, 10, 15}) {
            assertEquals(0, SyringeTiming.seconds(mode, amount, true, SyringeTiming.TargetPosture.STANDING));
            assertEquals(0, SyringeTiming.seconds(mode, amount, true, SyringeTiming.TargetPosture.SELF));
            assertEquals(0, SyringeTiming.seconds(mode, amount, true, SyringeTiming.TargetPosture.DOWNED));
        }
        var draw = new InjectorModeData(InjectorModeData.Kind.DRAW, List.of(5f, 10f, 15f), 5f,
                5f, 1.25f, .1f, .5f, .5f);
        for (double amount : new double[]{5, 10, 15})
            assertEquals(1.25, SyringeTiming.seconds(draw, amount, true, SyringeTiming.TargetPosture.DOWNED));
    }
}
