package com.juicyslew.moonstation14.ms14.medical.syringe;

import com.juicyslew.moonstation14.ms14.medical.InjectorModeData;

/** Pure SS14 syringe operation timing calculations. */
public final class SyringeTiming {
    public enum TargetPosture { SELF, DOWNED, STANDING }

    private SyringeTiming() { }

    public static double seconds(InjectorModeData mode, double admittedUnits, boolean container, TargetPosture posture) {
        if (mode == null || posture == null || !Double.isFinite(admittedUnits) || admittedUnits <= 0)
            throw new IllegalArgumentException("A mode, posture, and positive admitted dose are required");
        if (container) return mode.kind() == InjectorModeData.Kind.DRAW ? mode.containerDrawTimeSeconds() : 0;
        double base = mode.mobTimeSeconds();
        double delay = Math.max(0, admittedUnits - mode.ignoreDelayForVolume()) * mode.additionalDelayPerUnitSeconds();
        double modifier = posture == TargetPosture.SELF ? mode.selfModifier()
                : posture == TargetPosture.DOWNED ? mode.downedModifier() : 1;
        double result = (base + delay) * modifier;
        if (!Double.isFinite(result) || result < 0) throw new IllegalArgumentException("Invalid syringe timing");
        return result;
    }

    public static int ticks(InjectorModeData mode, double admittedUnits, boolean container, TargetPosture posture) {
        double seconds = seconds(mode, admittedUnits, container, posture);
        double exactTicks = seconds * 20.0;
        double nearestTick = Math.rint(exactTicks);
        // Prototype numbers are floats; remove only their sub-microtick serialization noise.
        if (Math.abs(exactTicks - nearestTick) < 1.0e-5) exactTicks = nearestTick;
        return (int) Math.ceil(exactTicks);
    }
}

