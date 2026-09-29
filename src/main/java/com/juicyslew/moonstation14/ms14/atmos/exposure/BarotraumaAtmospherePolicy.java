package com.juicyslew.moonstation14.ms14.atmos.exposure;

/** Shared Atmospherics.cs hazard thresholds and multipliers; not species prototype data. */
public final class BarotraumaAtmospherePolicy {
    private BarotraumaAtmospherePolicy() { }
    public static final double LOW_HAZARD_KPA = 20;
    public static final double HIGH_HAZARD_KPA = 550;
    public static final double LOW_DAMAGE_MULTIPLIER = 4;
    public static final double HIGH_DAMAGE_COEFFICIENT = 4;
    public static final double HIGH_DAMAGE_SCALE_CAP = 4;
    public static final int CADENCE_TICKS = 20;
}
