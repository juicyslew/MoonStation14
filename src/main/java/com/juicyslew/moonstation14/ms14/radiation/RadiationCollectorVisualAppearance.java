package com.juicyslew.moonstation14.ms14.radiation;

/** Pure visual derivation; pressure is local kPa, thresholds are source Pa / standard atm. */
public final class RadiationCollectorVisualAppearance {
    private RadiationCollectorVisualAppearance() { }

    public static int pressureBand(boolean tankPresent, double pressureKpa) {
        if (!tankPresent || !Double.isFinite(pressureKpa) || pressureKpa < .01) return 0;
        if (pressureKpa < 3.33 * 101.325) return 1;
        if (pressureKpa < 6.66 * 101.325) return 2;
        return 3;
    }
}
