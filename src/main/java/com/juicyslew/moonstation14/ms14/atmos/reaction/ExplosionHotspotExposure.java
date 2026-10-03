package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

/** Pure SS14-inspired hotspot exposure selection; this class never mutates gas or world state. */
public final class ExplosionHotspotExposure {
    private static final double HOTSPOT_THRESHOLD_KELVIN = 373.15;
    private static final double SS14_EXPOSED_VOLUME_MULTIPLIER = 25.0;
    private static final double LOCAL_CELL_VOLUME_LITERS = 1000.0;

    private ExplosionHotspotExposure() { }

    /**
     * Selects an explosion's SS14-style hotspot exposure. The incoming temperature need not match
     * the ambient gas temperature: SS14 seeds the hotspot at the supplied exposure temperature.
     * An existing hotspot receives the soh=true boost without being weakened.
     */
    public static Decision decide(GasMixture gas, double exposureTemperatureKelvin, double intensity,
                                  double currentHotspotTemperatureKelvin, double currentHotspotFraction) {
        if (gas == null || !Double.isFinite(exposureTemperatureKelvin)
                || exposureTemperatureKelvin <= HOTSPOT_THRESHOLD_KELVIN
                || !Double.isFinite(intensity) || intensity <= 0
                || !Double.isFinite(currentHotspotTemperatureKelvin) || currentHotspotTemperatureKelvin < 0
                || !Double.isFinite(currentHotspotFraction) || currentHotspotFraction < 0
                || currentHotspotFraction > 1)
            return Decision.NONE;

        double oxidizer = gas.moles(GasType.OXYGEN);
        double fuel = gas.moles(GasType.TRITIUM) + gas.moles(GasType.PLASMA);
        if (!(oxidizer > 0) || !(fuel > 0))
            return Decision.NONE;

        double fraction = Math.min(1.0, intensity * SS14_EXPOSED_VOLUME_MULTIPLIER / LOCAL_CELL_VOLUME_LITERS);
        if (!Double.isFinite(fraction) || fraction <= 0) return Decision.NONE;
        return new Decision(true, Math.max(exposureTemperatureKelvin, currentHotspotTemperatureKelvin),
                Math.max(fraction, Math.min(1.0, currentHotspotFraction)));
    }

    public record Decision(boolean expose, double temperatureKelvin, double cellFraction) {
        private static final Decision NONE = new Decision(false, 0, 0);
    }
}
