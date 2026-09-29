package com.juicyslew.moonstation14.ms14.power.device;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ApcOverloadTest {
    private static final PowerDeviceRules.ApcProtectionConfig CONFIG = PowerDeviceRules.DEFAULT_APC_PROTECTION;

    @Test void defaultsAndInvalidConfigurationAreExplicit() {
        assertEquals(20_000.0, CONFIG.maxLoadWatts());
        assertEquals(3.0, CONFIG.tripTimeSeconds());
        assertThrows(IllegalArgumentException.class, () -> new PowerDeviceRules.ApcProtectionConfig(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new PowerDeviceRules.ApcProtectionConfig(20_000, Double.NaN));
    }

    @Test void thresholdMustBeStrictlyExceededAndTripTimeIsStrictlyGreaterThanThreeSeconds() {
        var state = PowerDeviceRules.ApcProtectionState.initial();
        for (long tick = 1; tick <= 80; tick++)
            state = observe(state, tick, tick, true, 20_000, true);
        assertEquals(0, state.overloadTicks());
        assertFalse(state.tripped());

        state = PowerDeviceRules.ApcProtectionState.initial();
        for (long tick = 1; tick <= 60; tick++)
            state = observe(state, tick, tick, true, 20_000.01, true);
        assertEquals(60, state.overloadTicks());
        assertFalse(state.tripped());
        state = observe(state, 61, 61, true, 20_000.01, true);
        assertTrue(state.tripped());
    }

    @Test void twentyTickSolveSamplesCanAdvanceOnlyFreshPerTickMeterAndDuplicateTicksDoNotCount() {
        var state = PowerDeviceRules.ApcProtectionState.initial();
        for (long tick = 1; tick <= 60; tick++) {
            long latestSolveTick = ((tick - 1) / PowerDeviceRules.APC_SOLVE_INTERVAL_TICKS)
                    * PowerDeviceRules.APC_SOLVE_INTERVAL_TICKS + 1;
            state = observe(state, tick, latestSolveTick, true, 25_000, true);
        }
        var duplicate = observe(state, 60, 41, true, 25_000, true);
        assertEquals(state, duplicate);
        assertFalse(state.tripped());
        state = observe(state, 61, 41, true, 25_000, true);
        assertTrue(state.tripped());
    }

    @Test void interruptionUnknownOpenStaleAndSkippedServerTicksResetContinuity() {
        var state = PowerDeviceRules.ApcProtectionState.initial();
        for (long tick = 1; tick <= 30; tick++) state = observe(state, tick, tick, true, 30_000, true);
        state = observe(state, 31, 31, true, 20_000, true);
        assertEquals(0, state.overloadTicks());
        for (long tick = 32; tick <= 50; tick++) state = observe(state, tick, tick, true, 30_000, true);
        state = observe(state, 51, 30, true, 30_000, true);
        assertEquals(0, state.overloadTicks(), "a solve sample older than the cadence expires");
        for (long tick = 52; tick <= 60; tick++) state = observe(state, tick, tick, true, 30_000, true);
        state = observe(state, 61, 61, false, 30_000, true);
        assertEquals(0, state.overloadTicks());
        for (long tick = 62; tick <= 70; tick++) state = observe(state, tick, tick, true, 30_000, true);
        state = observe(state, 71, 71, true, 30_000, false);
        assertEquals(0, state.overloadTicks());
        state = observe(state, 72, 72, true, 30_000, true);
        state = observe(state, 74, 74, true, 30_000, true);
        assertEquals(1, state.overloadTicks(), "unobserved server ticks are not credited");
    }

    @Test void tripLatchAndBreakerPersistButPendingTimerDoesNot() {
        CompoundTag saved = PowerDeviceRules.saveApc(42_000, false, true);
        var restored = PowerDeviceRules.loadApc(saved);
        assertEquals(42_000, restored.energyJoules());
        assertFalse(restored.breakerClosed());
        assertTrue(restored.tripLatched());

        CompoundTag inconsistent = PowerDeviceRules.saveApc(42_000, true, true);
        assertFalse(PowerDeviceRules.loadApc(inconsistent).breakerClosed());

        var pending = observe(PowerDeviceRules.ApcProtectionState.initial(), 1, 1, true, 30_000, true);
        assertEquals(1, pending.overloadTicks());
        assertEquals(PowerDeviceRules.ApcProtectionState.initial(), PowerDeviceRules.resetApcProtection(pending));
    }

    private static PowerDeviceRules.ApcProtectionState observe(PowerDeviceRules.ApcProtectionState state,
                                                                 long tick, long sampleTick, boolean known,
                                                                 double watts, boolean breakerClosed) {
        return PowerDeviceRules.observeApcOutput(state, tick, sampleTick, known, watts, breakerClosed, CONFIG);
    }
}
