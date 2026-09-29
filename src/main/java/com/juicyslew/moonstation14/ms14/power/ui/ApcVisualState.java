package com.juicyslew.moonstation14.ms14.power.ui;

import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;

/** A conservative, ephemeral server solve signal; never a persisted battery or breaker state. */
public enum ApcVisualState {
    UNKNOWN, FULL, CHARGING, LACK;

    public static ApcVisualState fromSolve(boolean mvKnown, boolean apcKnown, double inputWatts,
                                           double outputWatts, double energyBefore, double energyAfter) {
        if (!mvKnown || !apcKnown || !Double.isFinite(inputWatts) || !Double.isFinite(outputWatts)
                || !Double.isFinite(energyBefore) || !Double.isFinite(energyAfter)
                || inputWatts < 0 || outputWatts < 0 || energyBefore < 0 || energyAfter < 0
                || energyBefore > PowerDeviceRules.APC_CAPACITY_JOULES
                || energyAfter > PowerDeviceRules.APC_CAPACITY_JOULES) return UNKNOWN;
        if (energyAfter > PowerDeviceRules.APC_CAPACITY_JOULES * .9) return FULL;
        if (inputWatts > outputWatts && energyAfter > energyBefore) return CHARGING;
        // Input is a measured MV allocation, not a claim inferred from a connected cable.
        if (inputWatts > 0 && outputWatts >= inputWatts && energyAfter < energyBefore) return LACK;
        return UNKNOWN;
    }

    /** ContainerData travels as a signed short; only these exact values are meaningful. */
    public static ApcVisualState decode(int value) {
        return switch (value) {
            case 1 -> FULL;
            case 2 -> CHARGING;
            case 3 -> LACK;
            default -> UNKNOWN;
        };
    }
}
