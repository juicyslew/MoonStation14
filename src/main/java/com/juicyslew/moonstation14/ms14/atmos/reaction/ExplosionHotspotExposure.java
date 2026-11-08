package com.juicyslew.moonstation14.ms14.atmos.reaction;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.AtmosphereUnits;

/** Pure SS14-inspired hotspot exposure selection; this class never mutates gas or world state. */
public final class ExplosionHotspotExposure {
    private static final double HOTSPOT_THRESHOLD_KELVIN = (double) 373.15f;
    private static final double IGNITABLE_MOLES = (double) (float) AtmosphereUnits.referenceTileQuantity(0.5);
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
        exposureTemperatureKelvin = (double) (float) exposureTemperatureKelvin;
        if (gas == null || !Double.isFinite(exposureTemperatureKelvin) || exposureTemperatureKelvin <= 0
                || !Double.isFinite(intensity) || intensity <= 0
                || !Double.isFinite(currentHotspotTemperatureKelvin) || currentHotspotTemperatureKelvin < 0
                || !Double.isFinite(currentHotspotFraction) || currentHotspotFraction < 0
                || currentHotspotFraction > 1)
            return Decision.NONE;

        double oxidizer = gas.moles(GasType.OXYGEN);
        double fuel = gas.moles(GasType.TRITIUM) + gas.moles(GasType.PLASMA);
        if (!((double) (float) oxidizer > IGNITABLE_MOLES)
                || !((double) (float) fuel > IGNITABLE_MOLES))
            return Decision.NONE;

        if (currentHotspotTemperatureKelvin <= 0 && exposureTemperatureKelvin <= HOTSPOT_THRESHOLD_KELVIN)
            return Decision.NONE;

        double inputVolumeLiters = intensity * AtmosphereUnits.referenceTileQuantity(1.0);
        if (!Double.isFinite(inputVolumeLiters) || inputVolumeLiters <= 0) return Decision.NONE;
        double fraction = Math.min(1.0, inputVolumeLiters * SS14_EXPOSED_VOLUME_MULTIPLIER
                / LOCAL_CELL_VOLUME_LITERS);
        if (fraction <= 0) return Decision.NONE;
        return new Decision(true, Math.max(exposureTemperatureKelvin, currentHotspotTemperatureKelvin),
                Math.max(fraction, Math.min(1.0, currentHotspotFraction)), inputVolumeLiters);
    }

    public record Decision(boolean expose, double temperatureKelvin, double cellFraction, double exposedVolumeLiters) {
        /** Compatibility constructor for callers that only provide the rendered hotspot fraction. */
        @Deprecated
        public Decision(boolean expose, double temperatureKelvin, double cellFraction) {
            this(expose, temperatureKelvin, cellFraction, cellFraction * LOCAL_CELL_VOLUME_LITERS
                    / SS14_EXPOSED_VOLUME_MULTIPLIER);
        }
        private static final Decision NONE = new Decision(false, 0, 0, 0);
    }
}

