package com.juicyslew.moonstation14.ms14.atmos.reaction;

/** Persistent local fire metadata. Volume is deliberately uncapped to the containing cell; volume and
 * temperature are stored at native float precision, without a gas MAX-temperature clamp.
 */
public record HotspotState(double volumeLiters, double temperatureKelvin,
                           boolean skippedFirstProcess, boolean bypass) {
    public HotspotState {
        if (!Double.isFinite(volumeLiters) || volumeLiters < 0
                || !Double.isFinite(temperatureKelvin) || temperatureKelvin < 0)
            throw new IllegalArgumentException("invalid hotspot state");
        volumeLiters = (double) (float) volumeLiters;
        temperatureKelvin = (double) (float) temperatureKelvin;
        if (!Double.isFinite(volumeLiters) || !Double.isFinite(temperatureKelvin))
            throw new IllegalArgumentException("hotspot state exceeds native float range");
    }
}

