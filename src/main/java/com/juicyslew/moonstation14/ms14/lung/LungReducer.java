package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

/** Pure SS14 respirator saturation and organic oxygen-to-CO2 conversion approximation. */
public final class LungReducer {
    private LungReducer() {}

    public static double deplete(double saturation, double loss, double min) {
        if (!Double.isFinite(saturation) || !Double.isFinite(loss) || loss < 0 || !Double.isFinite(min)
                || saturation < min) throw new IllegalArgumentException("invalid saturation depletion");
        return Math.max(min, saturation - loss);
    }

    /** GasToReagent multiplies oxygen moles by 1144; human/animal Respiration Oxygenate has factor 1.
     * With no local lung reagent solution, model the entire inhaled O2 as available for conversion
     * on this inhale, converting the same mole count to CO2 as the upstream ModifyLungGas ratio.
     */
    public static Result uptake(GasMixture lung, double depleted, double multiplier, double maxSaturation) {
        if (lung == null || !Double.isFinite(depleted) || !Double.isFinite(multiplier)
                || multiplier <= 0 || !Double.isFinite(maxSaturation) || maxSaturation <= 0)
            throw new IllegalArgumentException("invalid oxygen uptake");
        double consumed = lung.moles(GasType.OXYGEN);
        double next = Math.min(maxSaturation, depleted + consumed * multiplier);
        if (!Double.isFinite(next)) throw new IllegalArgumentException("invalid oxygen saturation");
        GasMixture gas = consumed == 0 ? lung : lung.withGasDelta(GasType.OXYGEN, -consumed)
                .withGasDelta(GasType.CARBON_DIOXIDE, consumed, lung.temperatureKelvin());
        return new Result(gas, next, consumed);
    }

    public record Result(GasMixture exhaled, double saturation, double consumedOxygen) {}
}
